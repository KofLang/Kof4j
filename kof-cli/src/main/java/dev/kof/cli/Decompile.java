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
 * idiomatic Kof source (docs/development/DECOMPILER.md, Fase E).
 *
 * This is a structural skeleton: class name, superclass, interfaces, fields
 * and method signatures are recovered exactly from the class file. Method
 * bodies are NOT recovered yet (Control Flow / Data Flow recovery are later
 * phases), so every body is emitted as an honest {@code throw} stub instead
 * of fabricating behavior (per LEGACY_MIGRATION §4: never invent silently).
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
        System.out.println("decompiled " + ok + " class(es) → " + out + (fail > 0 ? " (" + fail + " failed)" : ""));
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
    /**
     * Fase E (14/10): pré-pass das LAMBDAS — o sintético `lambda$X$0` tem
     * corpo no classfile; recupera (expression → statements) e arma
     * `(p0: T, ...) -> corpo` no mapa por ÍNDICE de CP.
     */
    static void prepassLambdas(ClassFileParser.ClassFile ir) {
        BytecodeConcat.LAMBDA_TEXT.clear();
        for (int i = 1; i < ir.constantPool.length; i++) {
            String e = ir.constantPool[i];
            if (e == null || !e.startsWith("LAMBDA:")) continue;
            String impl = e.substring("LAMBDA:".length());
            for (var m : ir.methods) {
                if (!m.name.equals(impl) || m.code == null) continue;
                var frame = new BytecodeFrame(m.descriptor, (m.accessFlags & 0x0008) != 0);
                String body = BytecodeDecoder.recoverExpression(m.code.bytecode,
                        ir.constantPool, frame);
                if (body == null) {
                    int[][] handlers = new int[m.code.exceptionHandlers == null
                            ? 0 : m.code.exceptionHandlers.size()][];
                    for (int hI = 0; hI < handlers.length; hI++) {
                        var h = m.code.exceptionHandlers.get(hI);
                        boolean fin = h.catchType == null || "INVALID".equals(h.catchType);
                        handlers[hI] = new int[]{h.startPc, h.endPc, h.handlerPc, fin ? 1 : 0};
                    }
                    var stmts = BytecodeStatements.recoverStatements(m.code.bytecode,
                            ir.constantPool, frame, handlers);
                    if (stmts != null) body = String.join("\n", stmts);
                }
                if (body == null) break;
                var pts = paramTypes(m.descriptor);
                var sbP = new StringBuilder("(");
                for (int k = 0; k < pts.size(); k++) {
                    if (k > 0) sbP.append(", ");
                    sbP.append("arg").append(k).append(": ")
                       .append(fieldKofType(pts.get(k)));
                }
                sbP.append(") -> ").append(body);
                BytecodeConcat.LAMBDA_TEXT.put(i, sbP.toString());
                break;
            }
        }
    }

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
    /** Slot carregado por uma instrução de LOAD direta, ou null. */
    private static Integer loadSlot(BytecodeReader.Insn in) {
        int op = in.opcode();
        if (op >= 0x1a && op <= 0x2d) return (op - 0x1a) % 4;      // xload_0..3
        if (op >= 0x15 && op <= 0x19) return in.operands().length > 0 ? in.operands()[0] : null;
        return null;
    }

    /** Descritores dos parâmetros (JVM type descriptors). */
    private static List<String> paramTypes(String descriptor) {
        List<String> out = new java.util.ArrayList<>();
        int i = 1;
        while (i < descriptor.length() && descriptor.charAt(i) != ')') {
            char c = descriptor.charAt(i);
            if (c == '[') {
                int j = i;
                while (descriptor.charAt(j) == '[') j++;
                if (descriptor.charAt(j) == 'L') j = descriptor.indexOf(';', j);
                out.add(descriptor.substring(i, j + 1));
                i = j + 1;
            } else if (c == 'L') {
                int j = descriptor.indexOf(';', i);
                out.add(descriptor.substring(i, j + 1));
                i = j + 1;
            } else {
                out.add(String.valueOf(c));
                i++;
            }
        }
        return out;
    }

    private static boolean isWideType(String desc) {
        return "J".equals(desc) || "D".equals(desc);
    }

    /**
     * Fase G (diamond, mantenedora 14/10): o shape `return A.super.m();`
     * compila para `aload_0; invokespecial InterfaceMethodref A.m; xreturn`.
     * O corpo do default é lido do CLASSFILE IRMÃO (o mesmo diretório) e
     * recuperado pelo MESMO pipeline — o call site emite o corpo inlinado.
     * Stub nunca é opção de design (Q7/AGENTS): se o corpo do default não
     * recuperar, o método segue não-recuperado pelo fluxo normal (o relatório
     * conta), nunca fabricado.
     */
    private static String tryInlineInterfaceSuperCall(ClassFileParser.MethodInfo m,
                                                      ClassFileParser.ClassFile self,
                                                      Path classFilePath,
                                                      BytecodeFrame curFrame) {
        if (m == null || m.code == null || classFilePath == null) return null;
        List<BytecodeReader.Insn> insns;
        try {
            insns = BytecodeReader.decode(m.code.bytecode);
        } catch (Exception e) {
            return null;
        }
        if (insns.size() < 3) return null;
        if (insns.get(0).opcode() != 0x2a) return null;            // aload_0 (receptor)
        int invokeAt = insns.size() - 2;
        if (insns.get(invokeAt).opcode() != 0xb7) return null;     // invokespecial
        int ret = insns.get(invokeAt + 1).opcode();
        if (ret < 0xac || ret > 0xb1) return null;                 // xreturn
        String[] ref = BytecodeCp.resolveMethodRef(self.constantPool, insns.get(invokeAt).operands()[0]);
        if (ref == null) return null;
        // o owner NÃO pode ser a própria classe nem a super (esses shapes têm
        // tratamento próprio); invokespecial não-init restante = interface
        if (ref[0].equals(self.thisClass) || ref[0].equals(self.superClass)) return null;
        int argc = BytecodeCp.argCount(ref[2]);
        if (insns.size() != argc + 3) return null;                 // aload_0 + N loads + invoke + return
        // os N loads entre o receptor e o invoke são os ARGUMENTOS — só a
        // forma de load direto de slot (local simples) é aceita
        int[] argSlots = new int[argc];
        for (int k = 0; k < argc; k++) {
            Integer slot = loadSlot(insns.get(1 + k));
            if (slot == null) return null;
            argSlots[k] = slot;
        }
        Path ownerFile = classFilePath.getParent() == null ? null
                : classFilePath.getParent().resolve(ref[0].replace('/', java.io.File.separatorChar) + ".class");
        if (ownerFile == null || !java.nio.file.Files.exists(ownerFile)) return null;
        try (java.io.InputStream in = java.nio.file.Files.newInputStream(ownerFile)) {
            ClassFileParser.ClassFile owner = ClassFileParser.parse(in);
            for (ClassFileParser.MethodInfo om : owner.methods) {
                if (!ref[1].equals(om.name) || !ref[2].equals(om.descriptor)) continue;
                if (om.code == null) return null;
                boolean omStatic = (om.accessFlags & 0x0008) != 0;
                BytecodeFrame of = new BytecodeFrame(om.descriptor, omStatic);
                // liga cada parâmetro do default à EXPRESSÃO do argumento no
                // call site (resolvido no frame do método CORRENTE)
                if (curFrame == null && argc > 0) return null;
                int slot = omStatic ? 0 : 1;
                List<String> ty = paramTypes(om.descriptor);
                for (int i = 0; i < argSlots.length && i < ty.size(); i++) {
                    of.bindSlot(slot, curFrame.name(argSlots[i]));
                    slot += isWideType(ty.get(i)) ? 2 : 1;
                }
                // o corpo do default é recuperado pelo MESMO pipeline
                return BytecodeDecoder.recoverExpression(om.code.bytecode, owner.constantPool, of);
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    /**
     * Fase E: o clinit em forma `[push, putstatic]*, return` dobra — cada
     * literal vai para o inicializador do campo. Qualquer instrução fora do
     * shape desiste do fold INTEIRO (nada parcial em silêncio).
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
        // Fase E (14/10): pré-pass das LAMBDAS — para cada CP entry
        // "LAMBDA:<impl>", recupera o corpo do sintético pelo MESMO pipeline
        // e arma o texto; os avaliadores consomem no invokedynamic.
        prepassLambdas(ir);
        StringBuilder sb = new StringBuilder();
        sb.append("// decompiled from ").append(classFile.getFileName()).append('\n');
        sb.append("// structural skeleton — simple method bodies recovered; others stubbed (Fase E)\n");
        sb.append("// confidence: class/fields/signatures = EXACT; recovered bodies = EXACT; stubs = UNKNOWN\n");
        if (pkg == null) pkg = packageOf(ir.thisClass);
        if (!pkg.isEmpty()) sb.append("package ").append(pkg).append('\n');
        int importPos = sb.length();   // imports entram aqui (pós-passe)
        sb.append('\n');

        String simpleName = simpleName(ir.thisClass);
        // Fase E (fila 13/09): Java record PURO → `record Nome implements I(T a, T b)`
        // (o frontend gera ctor/accessors/equals/hashCode/toString; a ordem do
        // header é name/typeParams/extends/implements/COMPONENTES — probe R7).
        // Os corpos dos 3 sintéticos NÃO existem no bytecode (invokedynamic
        // ObjectMethods) — no esqueleto `class X extends Record` cada um era um
        // stub silencioso (~215 records no corpus = a maior fonte única de
        // stubs). Qualquer desvio do shape (interface não-resolvida, método
        // extra, nome reservado, bound genérico) → null → esqueleto de hoje
        // (zero-drift por construção).
        // Fase E (14/10): Java enum PURO → `enum Nome { A, B, C }` (o Kof
        // tem enum nativo — medido). Qualquer método/field extra → o
        // esqueleto de hoje (zero-drift por construção).
        String enumForm = BytecodeRecords.pureEnum(ir);
        if (enumForm != null) {
            sb.append(enumForm).append("\n\n");
            if (scope != null) {
                var lines = new StringBuilder();
                for (String imp : scope.usedImports()) lines.append("import ").append(imp).append('\n');
                if (lines.length() > 0) sb.insert(importPos, lines.toString());
            }
            return sb.toString();
        }
        BytecodeRecords.Rec rec = BytecodeRecords.pureRecord(ir, scope);
        if (rec != null) {
            List<dev.kof.compiler.parser.ClassFileParser.FieldInfo> comps = rec.components();
            sb.append("record ").append(simpleName);
            var tps = BytecodeRecords.typeParams(ir.classSignature);
            if (!tps.isEmpty()) sb.append('<').append(String.join(", ", tps)).append('>');
            // #325: a forma Kof valida e `record Nome(comps) implements I` —
            // os COMPONENTES vao ANTES do implements (nao a ordem javac, que
            // era emitida aqui e dependia do furo do parser antigo).
            sb.append('(');
            for (int k = 0; k < comps.size(); k++) {
                if (k > 0) sb.append(", ");
                var f = comps.get(k);
                String ctype = f.signature != null
                        ? DecompileTypes.methodKofType(dev.kof.compiler.Type.describe(
                                dev.kof.compiler.Type.fromJvmSignature(f.signature)))
                        : fieldKofType(f.descriptor);
                recordSignatureUses(fieldTypeTree(f.descriptor, f.signature), scope);
                sb.append(ctype).append(' ').append(f.name);
            }
            sb.append(')');
            if (!rec.interfaces().isEmpty())
                sb.append(" implements ").append(String.join(", ", rec.interfaces()));
            sb.append("\n\n");
            if (scope != null) {
                var lines = new StringBuilder();
                for (String imp : scope.usedImports()) lines.append("import ").append(imp).append('\n');
                if (lines.length() > 0) sb.insert(importPos, lines.toString());
            }
            return sb.toString();
        }
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

        // Fase E (fatia static-init, mantenedora 14/10): o clinit em forma
        // literal-putstatic DOBRA nos inicializadores dos campos estáticos —
        // o MESMO padrão aprovado no tradutor; qualquer outra instrução → sem
        // fold (nada parcial em silêncio). Stub nunca é opção de design (Q7).
        java.util.Map<String, String> clinitInits = new java.util.LinkedHashMap<>();
        for (var mm : ir.methods) {
            if (!"<clinit>".equals(mm.name) || mm.code == null) continue;
            DecompileConstPool.foldClinitLiterals(mm.code.bytecode, ir.constantPool, clinitInits);
        }

        for (var f : ir.fields) {
            boolean isStaticField = (f.accessFlags & 0x0008) != 0;
            String ftype = f.signature != null
                    ? DecompileTypes.methodKofType(dev.kof.compiler.Type.describe(
                            dev.kof.compiler.Type.fromJvmSignature(f.signature)))
                    : fieldKofType(f.descriptor);
            recordSignatureUses(fieldTypeTree(f.descriptor, f.signature), scope);
            String init = "";
            if (isStaticField) {
                // o campo estático AGORA é emitido (antes: perda silenciosa —
                // o main referenciava `Cls.X` que não existia = Kof sem
                // compilar = falso OK); initializer: ConstantValue (constantes
                // `static final` NÃO passam pelo clinit, JVMS 4.7.2) ou o
                // clinit dobrado
                if (f.constantValue != null) {
                    String lit = DecompileConstPool.kofLiteral(f.constantValue, f.descriptor);
                    if (lit != null) init = " = " + lit;
                }
                if (init.isEmpty()) {
                    String lit = clinitInits.get(f.name);
                    if (lit != null) init = " = " + lit;
                }
            }
            sb.append("    ").append(isStaticField ? "static " : "").append(ftype).append(' ')
              .append(f.name).append(init).append("   // ").append(Confidence.EXACT.label()).append('\n');
        }

        for (var m : ir.methods) {
            sb.append('\n');
            // §7 degrau 4 (vale p/ ctor também — o `continue` abaixo pulava):
            // tipos de assinatura cross-package registrados p/ import.
            recordSignatureUses(m.returnType, scope);
            for (dev.kof.compiler.Type pt : m.parameterTypes) recordSignatureUses(pt, scope);
            if ("<clinit>".equals(m.name)) continue; // static initializer — skip
            if ("<init>".equals(m.name)) {
                // Fase E (14/10): o CONSTRUCTOR tem corpo — as atribuições
                // `this.x = x` são o ESTADO da classe; o `constructor(...)
                // { }` vazio deixava toda classe destruída com fields
                // mortos (medido no capstone Inventory: name/qty/price
                // nunca atribuídos, total()=0, find=null). O super()
                // implícito [aload_0, invokespecial Object.<init>] é
                // SKIPado no emitLinear (o Kof chama super implicitamente).
                sb.append("    constructor(")
                  .append(paramList(m.parameterTypeNames()))
                  .append(") {\n");
                if (m.code != null) {
                    BytecodeFrame cFrame = new BytecodeFrame(m.descriptor, false);
                    cFrame.treeScope = scope;
                    var cStmts = BytecodeStatements.recoverStatements(m.code.bytecode,
                            ir.constantPool, cFrame, new int[0][]);
                    if (cStmts != null) {
                        for (String cs : cStmts) sb.append("        ").append(cs).append('\n');
                    } else {
                        sb.append("        throw \"body not recovered\"\n");
                    }
                }
                sb.append("    }\n");
                continue;
            }
            String ret = DecompileTypes.methodKofType(m.returnTypeName());
            String params = paramList(m.parameterTypeNames());
            // bug 134: o modificador `static` era computado só p/ o frame e
            // NUNCA emitido → `S.staticMethod(x)` baixava como chamada de
            // instância → crash no 1º teste que EXECUTA saída decompilada.
            boolean isStatic = (m.accessFlags & 0x0008) != 0;
            String stat = isStatic ? "static " : "";
            // Fase G (super.m()): contexto para o `invokespecial` não-<init>
            BytecodeDecoder.currentSuperInternal = ir.superClass;
            String body = null;
            List<String> stmts = null;
            if (m.code != null) {
                BytecodeFrame frame = new BytecodeFrame(m.descriptor, isStatic);
                frame.treeScope = scope;
                boolean hasHandlers = m.code.exceptionHandlers != null && !m.code.exceptionHandlers.isEmpty();
                if (!hasHandlers) {
                    body = BytecodeDecoder.recoverExpression(m.code.bytecode, ir.constantPool, frame);
                }
                if (body == null) {
                    // Fase G (diamond, mantenedora 14/10): o refinamento de
                    // default `A.super.m()` — o corpo do default da interface é
                    // INLINADO (lido do classfile irmão e recuperado pelo MESMO
                    // pipeline); stub NUNCA é opção de design (Q7).
                    body = tryInlineInterfaceSuperCall(m, ir, classFile, frame);
                }
                if (body == null) {
                    // Fase C (13/10): o statements path roda para TODO corpo
                    // não-recuperado por expressão — antes o gate
                    // `exceptionHandlers != null` pulava métodos SEM try (o
                    // javac nem emite o attribute sem handlers), e tern/&&
                    // stubavam sem nunca chegar ao struct.
                    int[][] handlers = new int[m.code.exceptionHandlers == null ? 0 : m.code.exceptionHandlers.size()][];
                    for (int i = 0; i < handlers.length; i++) {
                        var h = m.code.exceptionHandlers.get(i);
                        boolean isFinally = h.catchType == null || "INVALID".equals(h.catchType);
                        handlers[i] = new int[]{h.startPc, h.endPc, h.handlerPc, isFinally ? 1 : 0};
                    }
                    stmts = BytecodeStatements.recoverStatements(m.code.bytecode, ir.constantPool, frame, handlers);
                }
            }
            if (body == null && stmts == null) {
                sb.append("    ").append(stat).append(ret).append(' ').append(m.name)
                  .append('(').append(params).append(") {\n");
                sb.append("        throw \"body not recovered\"   // ").append(Confidence.UNKNOWN.label()).append('\n');
                sb.append("    }\n");
            } else if (stmts != null) {
                sb.append("    ").append(stat).append(ret).append(' ').append(m.name).append('(').append(params).append(") {\n");
                for (String s : stmts) sb.append("        ").append(s).append('\n');
                sb.append("    }\n");
            } else if (body.isEmpty()) {
                sb.append("    ").append(stat).append(ret).append(' ').append(m.name).append('(').append(params).append(") {\n    }\n");
            } else {
                sb.append("    ").append(stat).append(ret).append(' ').append(m.name).append('(').append(params)
                  .append(") = ").append(body).append('\n');
            }
        }

        sb.append("}\n");
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
            sb.append(DecompileTypes.methodKofType(t)).append(" arg").append(i);
            i++;
        }
        return sb.toString();
    }

    static String simpleName(String internalName) {
        if (internalName == null || internalName.isEmpty()) return "Object";
        int slash = internalName.lastIndexOf('/');
        return slash >= 0 ? internalName.substring(slash + 1) : internalName;
    }

    /** Maps a raw JVM field descriptor (e.g. "I", "[I", "Ljava/lang/String;") to a Kof type name. */
    static String fieldKofType(String jvmDesc) { return DecompileTypes.fieldKofType(jvmDesc); }
    private static String optionValue(String[] args, String opt) {
        for (int i = 0; i < args.length - 1; i++) {
            if (opt.equals(args[i])) return args[i + 1];
        }
        return null;
    }
}