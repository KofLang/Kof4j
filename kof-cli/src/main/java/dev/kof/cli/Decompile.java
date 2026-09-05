package dev.kof.cli;

import dev.kof.compiler.ClassFileParser;
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
            System.err.println("usage: kof decompile <file.class> [--output <file.kf>]");
            return 1;
        }
        Path classFile = Path.of(args[0]);
        String outArg = optionValue(args, "--output");
        Path outFile = outArg != null ? Path.of(outArg) : null;

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

    static String decompile(Path classFile) throws IOException {
        var ir = ClassFileParser.parse(Files.newInputStream(classFile));
        StringBuilder sb = new StringBuilder();
        sb.append("// decompiled from ").append(classFile.getFileName()).append('\n');
        sb.append("// structural skeleton — simple method bodies recovered; others stubbed (Fase E)\n");
        sb.append("// confidence: class/fields/signatures = EXACT; recovered bodies = EXACT; stubs = UNKNOWN\n\n");

        String simpleName = simpleName(ir.thisClass);
        sb.append("class ").append(simpleName);
        if (ir.superClass != null && !ir.superClass.equals("java/lang/Object")) {
            sb.append(" extends ").append(simpleName(ir.superClass));
        }
        if (ir.interfaces.length > 0) {
            sb.append(" implements ");
            List<String> ifaces = new ArrayList<>();
            for (String i : ir.interfaces) ifaces.add(simpleName(i));
            sb.append(String.join(", ", ifaces));
        }
        sb.append(" {\n");

        for (var f : ir.fields) {
            if ((f.accessFlags & 0x0008) != 0) continue; // skip static
            sb.append("    ").append(fieldKofType(f.descriptor)).append(' ')
              .append(f.name).append("   // ").append(Confidence.EXACT.label()).append('\n');
        }

        for (var m : ir.methods) {
            sb.append('\n');
            if ("<clinit>".equals(m.name)) continue; // static initializer — skip
            if ("<init>".equals(m.name)) {
                sb.append("    constructor(")
                  .append(paramList(m.parameterTypeNames()))
                  .append(") {\n    }\n");
                continue;
            }
            String ret = methodKofType(m.returnTypeName());
            String params = paramList(m.parameterTypeNames());
            String body = null;
            List<String> stmts = null;
            if (m.code != null) {
                boolean isStatic = (m.accessFlags & 0x0008) != 0;
                int pcount = m.parameterTypeNames().size();
                body = BytecodeDecoder.recoverExpression(m.code.bytecode, ir.constantPool, pcount, isStatic);
                if (body == null) {
                    stmts = BytecodeDecoder.recoverStatements(m.code.bytecode, ir.constantPool, pcount, isStatic);
                }
            }
            if (body == null && stmts == null) {
                sb.append("    ").append(ret).append(' ').append(m.name)
                  .append('(').append(params).append(") {\n");
                sb.append("        throw \"body not recovered\"   // ").append(Confidence.UNKNOWN.label()).append('\n');
                sb.append("    }\n");
            } else if (stmts != null) {
                sb.append("    ").append(ret).append(' ').append(m.name).append('(').append(params).append(") {\n");
                for (String s : stmts) sb.append("        ").append(s).append('\n');
                sb.append("    }\n");
            } else if (body.isEmpty()) {
                sb.append("    ").append(ret).append(' ').append(m.name).append('(').append(params).append(") {\n    }\n");
            } else {
                sb.append("    ").append(ret).append(' ').append(m.name).append('(').append(params)
                  .append(") = ").append(body).append('\n');
            }
        }

        sb.append("}\n");
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

    static String simpleName(String internalName) {
        if (internalName == null || internalName.isEmpty()) return "Object";
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