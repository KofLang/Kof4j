package dev.kof.cli;

import dev.kof.compiler.parser.ClassFileParser;
import dev.kof.compiler.Confidence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * `kof decompile` — structural decompilation of a JVM {@code .class} into
 * idiomatic Kof source (docs/future/DECOMPILER.md, Fase E).
 *
 * This is a structural skeleton: class name, superclass, interfaces, fields
 * and method signatures are recovered exactly from the class file. Method
 * bodies are NOT recovered yet (Control Flow / Data Flow recovery are later
 * phases), so every body is emitted as an honest {@code throw} stub instead
 * of fabricating behavior (per LEGACY_IR: never invent silently).
 */
public final class Decompile {

    private Decompile() {
    }

    public static int run(String[] args) {
        if (args.length > 0 && "decompile".equals(args[0])) {
            args = java.util.Arrays.copyOfRange(args, 1, args.length);
        }
        if (args.length == 0) {
            System.err.println("usage: kof decompile <file.class|dir> [--output <file.kf|dir>]");
            return 1;
        }
        Path classFile = Path.of(args[0]);
        String outArg = optionValue(args, "--output");
        Path outFile = outArg != null ? Path.of(outArg) : null;

        if (Files.isDirectory(classFile)) {
            return decompileTree(classFile, outFile);
        }
        if (!Files.isRegularFile(classFile)) {
            System.err.println("file not found: " + classFile);
            return 1;
        }
        if (!classFile.toString().endsWith(".class")) {
            System.err.println("kof decompile expects a .class file");
            return 1;
        }

        try {
            String kofSource = decompile(classFile);
            if (outFile != null) {
                Files.writeString(outFile, kofSource);
                System.out.println("decompiled to " + outFile);
            } else {
                System.out.print(kofSource);
            }
            return 0;
        } catch (Exception e) {
            System.err.println("kof decompile: " + e.getMessage());
            return 1;
        }
    }

    /**
     * Modo multi-classe (DECOMPILER §7, degrau 1): varre um diretório de
     * {@code .class}, decompila cada um p/ um {@code .kf} na ÁRVORE espelhada
     * em {@code out} (com {@code package} derivado do caminho interno). O
     * frontend Kof resolve tipos de MESMO pacote entre arquivos sem import
     * (probe PKG004/SEM025) — então referências de domínio cross-file (as 4
     * drifts restantes no corpus) deixam de ser "Undefined type". Cada arquivo
     * é independente; um que falha não derruba os demais (R6: nunca silencioso
     * — reporta no stderr e segue).
     */
    static int decompileTree(Path root, Path out) {
        if (out == null) {
            System.err.println("kof decompile <dir> exige --output <dir>");
            return 1;
        }
        int ok = 0, fail = 0;
        try (var walk = Files.walk(root)) {
            var classes = walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".class")).sorted().toList();
            // §7 degrau 2, passe 1: índice internalName → pacote de TODA a
            // árvore (parse uma vez, reuso no passe 2 — sem re-parse).
            var parsed = new java.util.ArrayList<ParsedClass>();
            for (Path c : classes) {
                try {
                    var ir = ClassFileParser.parse(Files.newInputStream(c));
                    parsed.add(new ParsedClass(c, ir));
                } catch (Throwable t) {
                    fail++;
                    System.err.println("kof decompile: " + c + ": " + t);
                }
            }
            var pkgOf = new java.util.TreeMap<String, String>();
            for (ParsedClass p : parsed) pkgOf.put(p.ir.thisClass, packageOf(p.ir.thisClass));
            // passe 2: decompila com o escopo (instanceof/cast/new de domínio
            // resolvem no mesmo pacote sem import; cross-package com import;
            // resto segue stub honesto).
            for (ParsedClass p : parsed) {
                try {
                    String pkg = packageOf(p.ir.thisClass);
                    var scope = new TreeScope(pkgOf, pkg);
                    String src = decompile(p.file, pkg, scope);
                    Path dest = out;
                    if (!pkg.isEmpty()) dest = dest.resolve(pkg.replace('.', '/'));
                    dest = dest.resolve(simpleName(p.ir.thisClass) + ".kf");
                    Files.createDirectories(dest.getParent());
                    Files.writeString(dest, src);
                    ok++;
                } catch (Throwable t) {
                    fail++;
                    System.err.println("kof decompile: " + p.file + ": " + t);
                }
            }
        } catch (IOException e) {
            System.err.println("kof decompile: " + e.getMessage());
            return 1;
        }
        System.out.println("decompiled " + ok + " class(es) → " + out + (fail > 0 ? " (" + fail + " falharam)" : ""));
        return fail > 0 ? 1 : 0;
    }

    /** .class parseado + seu path (passe 1 do índice, sem re-parse no passe 2). */
    private record ParsedClass(Path file, dev.kof.compiler.parser.ClassFileParser.ClassFile ir) {}

    /** Pacote Kof (pontos) derivado do internal name; "" p/ default package. */
    static String packageOf(String internalName) {
        if (internalName == null) return "";
        int slash = internalName.lastIndexOf('/');
        return slash < 0 ? "" : internalName.substring(0, slash).replace('/', '.');
    }

    static String decompile(Path classFile) throws IOException {
        return decompile(classFile, null, null);
    }

    static String decompile(Path classFile, String pkg) throws IOException {
        return decompile(classFile, pkg, null);
    }

    /**
     * §7 degrau 4: registra usos cross-package de tipos de ASSINATURA
     * (param/return/field) p/ import. A emissão continua com nomes simples
     * (byte-idêntica); só o registro muda. Generics destrinchados
     * (`List<p.B>` registra `p/B`); `Outer$Inner`/malformados caem nas
     * guardas do `TreeScope.resolve` (recusa → como antes).
     */
    static void recordSignatureUses(dev.kof.compiler.Type t, TreeScope scope) {
        if (scope == null || t == null) return;
        if (t instanceof dev.kof.compiler.Type.ClassType ct) {
            String internal = ct.packageName().isEmpty() ? ct.name()
                    : ct.packageName().replace('.', '/') + "/" + ct.name();
            scope.resolve(internal);
            for (dev.kof.compiler.Type a : ct.typeArguments()) recordSignatureUses(a, scope);
        } else if (t instanceof dev.kof.compiler.Type.ArrayType at) {
            recordSignatureUses(at.componentType(), scope);
        } else if (t instanceof dev.kof.compiler.Type.NullableType nt) {
            recordSignatureUses(nt.inner(), scope);
        }
    }

    /**
     * Árvore de tipos de um field (signature genérica preferida, como na
     * emissão; descriptor cru como fallback). Exceções de parse → null
     * (a emissão quebra primeiro no mesmo input — nunca mascarar).
     */
    static dev.kof.compiler.Type fieldTypeTree(String descriptor, String signature) {
        try {
            if (signature != null) return dev.kof.compiler.Type.fromJvmSignature(signature);
            if (descriptor != null && !descriptor.isEmpty()) {
                return dev.kof.compiler.Type.parseJvmDescriptorAt(descriptor, 0).type();
            }
        } catch (RuntimeException ignored) {
            // parse quebrado: a emissão já falhou/recusou acima; sem registro
        }
        return null;
    }
    /**
     * Nome p/ extends/implements: resolve no escopo (registrando import
     * cross-package) ou cai no simples (comportamento anterior).
     * Emissão nunca é null — no pior caso, igual a antes.
     */
    static String resolveSuperName(String internal, TreeScope scope) {
        if (scope != null) {
            String r = scope.resolve(internal);
            if (r != null) return r;
        }
        return simpleName(internal);
    }

    static String decompile(Path classFile, String pkg,
                            TreeScope scope) throws IOException {
        var ir = ClassFileParser.parse(Files.newInputStream(classFile));
        StringBuilder sb = new StringBuilder();
        sb.append("// decompiled from ").append(classFile.getFileName()).append('\n');
        sb.append("// structural skeleton — simple method bodies recovered; others stubbed (Fase E)\n");
        sb.append("// confidence: class/fields/signatures = EXACT; recovered bodies = EXACT; stubs = UNKNOWN\n");
        if (pkg == null) pkg = packageOf(ir.thisClass);
        if (!pkg.isEmpty()) sb.append("package ").append(pkg).append('\n');
        int importPos = sb.length();   // imports entram aqui (pós-passe)
        sb.append('\n');

        String simpleName = simpleName(ir.thisClass);
        sb.append("class ").append(simpleName);
        if (ir.superClass != null && !ir.superClass.equals("java/lang/Object")) {
            sb.append(" extends ").append(resolveSuperName(ir.superClass, scope));
        }
        if (ir.interfaces.length > 0) {
            sb.append(" implements ");
            List<String> ifaces = new ArrayList<>();
            for (String i : ir.interfaces) ifaces.add(resolveSuperName(i, scope));
            sb.append(String.join(", ", ifaces));
        }
        sb.append(" {\n");

        for (var f : ir.fields) {
            if ((f.accessFlags & 0x0008) != 0) continue; // skip static
            String ftype = f.signature != null
                    ? methodKofType(dev.kof.compiler.Type.describe(
                            dev.kof.compiler.Type.fromJvmSignature(f.signature)))
                    : fieldKofType(f.descriptor);
            recordSignatureUses(fieldTypeTree(f.descriptor, f.signature), scope);
            sb.append("    ").append(ftype).append(' ')
              .append(f.name).append("   // ").append(Confidence.EXACT.label()).append('\n');
        }

        for (var m : ir.methods) {
            sb.append('\n');
            // §7 degrau 4 (vale p/ ctor também — o `continue` abaixo pulava):
            // tipos de assinatura cross-package registrados p/ import.
            recordSignatureUses(m.returnType, scope);
            for (dev.kof.compiler.Type pt : m.parameterTypes) recordSignatureUses(pt, scope);
            if ("<clinit>".equals(m.name)) continue; // static initializer — skip
            if ("<init>".equals(m.name)) {
                sb.append("    constructor(")
                  .append(paramList(m.parameterTypeNames()))
                  .append(") {\n    }\n");
                continue;
            }
            String ret = methodKofType(m.returnTypeName());
            String params = paramList(m.parameterTypeNames());
            // §137: `static` precisa sobreviver à ida-e-volta — sem o prefixo,
            // o `S.grade(1)` (invokestatic) emitido noutro corpo chamaria um
            // método de INSTÂNCIA e o round-trip não reproduz o programa.
            String mods = (m.accessFlags & 0x0008) != 0 ? "static " : "";
            String body = null;
            List<String> stmts = null;
            if (m.code != null) {
                boolean isStatic = (m.accessFlags & 0x0008) != 0;
                BytecodeFrame frame = new BytecodeFrame(m.descriptor, isStatic);
                frame.treeScope = scope;
                boolean hasHandlers = m.code.exceptionHandlers != null && !m.code.exceptionHandlers.isEmpty();
                if (!hasHandlers) {
                    body = BytecodeDecoder.recoverExpression(m.code.bytecode, ir.constantPool, frame);
                }
                if (body == null) {
                    int[][] handlers = new int[m.code.exceptionHandlers.size()][];
                    for (int i = 0; i < handlers.length; i++) {
                        var h = m.code.exceptionHandlers.get(i);
                        boolean isFinally = h.catchType == null || "INVALID".equals(h.catchType);
                        handlers[i] = new int[]{h.startPc, h.endPc, h.handlerPc, isFinally ? 1 : 0};
                    }
                    stmts = BytecodeStatements.recoverStatements(m.code.bytecode, ir.constantPool, frame, handlers);
                }
            }
            if (body == null && stmts == null) {
                sb.append("    ").append(mods).append(ret).append(' ').append(m.name)
                  .append('(').append(params).append(") {\n");
                sb.append("        throw \"body not recovered\"   // ").append(Confidence.UNKNOWN.label()).append('\n');
                sb.append("    }\n");
            } else if (stmts != null) {
                sb.append("    ").append(mods).append(ret).append(' ').append(m.name).append('(').append(params).append(") {\n");
                for (String s : stmts) sb.append("        ").append(s).append('\n');
                sb.append("    }\n");
            } else if (body.isEmpty()) {
                sb.append("    ").append(mods).append(ret).append(' ').append(m.name).append('(').append(params).append(") {\n    }\n");
            } else {
                sb.append("    ").append(mods).append(ret).append(' ').append(m.name).append('(').append(params)
                  .append(") = ").append(body).append('\n');
            }
        }

        sb.append("}\n");
        // §137: `public static void main(String[])` (ou `main()`) precisa virar
        // um `main()` TOP-LEVEL — o executável da Kof é `Default.Main` gerado a
        // partir do main top-level, nunca de um método de classe. Em modo 1-
        // arquivo (scope==null) emito um forwarder `main() { S.main(...) }`; no
        // modo tree N classes podem ter main → cada arquivo já é compilado
        // junto e o forwarder duplicaria `main()` (SEM047), então não emito.
        if (scope == null) {
            String fwd = topLevelMainForwarder(ir, simpleName);
            if (fwd != null) sb.append(fwd);
        }
        // §7 degrau 3: imports usados (só modo tree; escopo null = sem imports
        // = bytes idênticos ao anterior). Inserção no ponto marcado p/ ficarem
        // entre package e classe (`package p\nimport q.B\n\nclass ...`).
        if (scope != null) {
            var lines = new StringBuilder();
            for (String imp : scope.usedImports()) lines.append("import ").append(imp).append('\n');
            if (lines.length() > 0) sb.insert(importPos, lines.toString());
        }
        return sb.toString();
    }

    private static String paramList(List<String> paramTypeNames) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (String t : paramTypeNames) {
            if (i > 0) sb.append(", ");
            sb.append(methodKofType(t)).append(" arg").append(i);
            i++;
        }
        return sb.toString();
    }

    /**
     * §137: forwarder `main()` top-level chamando o `main(String[])`/`main()`
     * estático da classe (se houver). null = nenhum candidato.
     */
    private static String topLevelMainForwarder(ClassFileParser.ClassFile ir, String simpleName) {
        boolean has = false;
        for (var m : ir.methods) {
            if (!"main".equals(m.name) || (m.accessFlags & 0x0008) == 0) continue;
            if ("([Ljava/lang/String;)V".equals(m.descriptor) || "()V".equals(m.descriptor)) {
                has = true;
                break;
            }
        }
        return has ? "\nmain() {\n    " + simpleName + ".main(new String[0])\n}\n" : null;
    }

    static String simpleName(String internalName) {        if (internalName == null || internalName.isEmpty()) return "Object";
        int slash = internalName.lastIndexOf('/');
        return slash >= 0 ? internalName.substring(slash + 1) : internalName;
    }

    /** Maps a raw JVM field descriptor (e.g. "I", "[I", "Ljava/lang/String;") to a Kof type name. */
    static String fieldKofType(String jvmDesc) {
        return capitalizePrimitive(descriptorToName(jvmDesc));
    }

    /** Maps a {@code Type.describe()} name (e.g. "int", "String", "int[]") to a Kof type name. */
    static String methodKofType(String typeName) {
        return capitalizePrimitive(typeName);
    }

    private static String capitalizePrimitive(String name) {
        if (name == null || name.isEmpty()) return "Object";
        String arraySuffix = "";
        String base = name;
        while (base.endsWith("[]")) {
            arraySuffix = "[]" + arraySuffix;
            base = base.substring(0, base.length() - 2);
        }
        String capitalized = switch (base) {
            case "int" -> "Int";
            case "long" -> "Long";
            case "float" -> "Float";
            case "double" -> "Double";
            case "bool", "boolean" -> "Bool";
            case "char" -> "Char";
            case "byte" -> "Byte";
            case "short" -> "Short";
            case "void" -> "void";
            default -> base;
        };
        return capitalized + arraySuffix;
    }

    private static String descriptorToName(String desc) {
        if (desc == null || desc.isEmpty()) return "";
        char c = desc.charAt(0);
        if (c == 'L') {
            int end = desc.indexOf(';');
            String cls = end >= 0 ? desc.substring(1, end) : desc.substring(1);
            return simpleName(cls);
        }
        if (c == '[') {
            return descriptorToName(desc.substring(1)) + "[]";
        }
        return switch (c) {
            case 'B' -> "byte";
            case 'C' -> "char";
            case 'D' -> "double";
            case 'F' -> "float";
            case 'I' -> "int";
            case 'J' -> "long";
            case 'S' -> "short";
            case 'V' -> "void";
            case 'Z' -> "bool";
            default -> desc;
        };
    }

    private static String optionValue(String[] args, String opt) {
        for (int i = 0; i < args.length - 1; i++) {
            if (opt.equals(args[i])) return args[i + 1];
        }
        return null;
    }
}