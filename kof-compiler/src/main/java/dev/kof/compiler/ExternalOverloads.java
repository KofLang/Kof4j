package dev.kof.compiler;

import dev.kof.compiler.jvm.JvmTypeMapper;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * §770 — overload selection for a call into an external class (jar entries
 * read with ASM, JDK classes read by reflection), following JLS 15.12.2:
 * phase 1 (subtyping and primitive widening), phase 2 (plus boxing and
 * unboxing), phase 3 (variable arity), then the most specific applicable
 * method. Several maximally specific methods are an ambiguity the caller
 * reports as SEM057 when every argument type is known; nothing applicable, or
 * a tie that an argument of unknown type could break, leaves the caller's
 * legacy arity-based pick, whose argument checks report a mismatch.
 *
 * <p>An argument whose Kof type has no JVM counterpart (UNKNOWN, a type
 * variable, a class absent from the classpath) is treated as possibly
 * compatible: the selector never rejects what it cannot prove inapplicable.
 * A lambda is applicable to an interface parameter.
 */
final class ExternalOverloads {

    record Result(ExternalClasspath.MethodSignature selected, int ambiguous) {
        static final Result NONE = new Result(null, 0);
    }

    private static final String UNKNOWN = "?";
    private static final String LAMBDA = "->";
    private static final String OBJECT = "Ljava/lang/Object;";

    private final Map<String, byte[]> classBytes;
    private boolean uncertain;

    private ExternalOverloads(Map<String, byte[]> classBytes) {
        this.classBytes = classBytes;
    }

    static Result select(Map<String, byte[]> classBytes, String owner, String name, List<Type> argTypes) {
        if (owner == null) return Result.NONE;
        ExternalOverloads o = new ExternalOverloads(classBytes);
        List<ExternalClasspath.MethodSignature> cands = classBytes.containsKey(owner)
                ? o.jarCandidates(owner, name)
                : JdkReflectionResolver.isJdkClass(owner) ? jdkCandidates(owner, name) : List.of();
        List<String> args = new ArrayList<>();
        for (Type t : argTypes) args.add(argDescriptor(t));
        for (int phase = 1; phase <= 3; phase++) {
            List<ExternalClasspath.MethodSignature> applicable = new ArrayList<>();
            for (ExternalClasspath.MethodSignature c : cands) {
                if (o.applicable(c, args, phase)) applicable.add(c);
            }
            if (applicable.isEmpty()) continue;
            Result r = o.mostSpecific(applicable, args.size());
            boolean provable = !o.uncertain && !args.contains(UNKNOWN) && !args.contains(LAMBDA);
            return r.selected() != null || provable ? r : Result.NONE;
        }
        return Result.NONE;
    }

    /** SEM057 when the call is ambiguous; true when it was reported. */
    static boolean reportAmbiguity(SemanticAnalyzer sa, MethodCallExpr mc, String owner, List<Type> argTypes) {
        Result r = sa.externalTypes().resolveOverload(owner, mc.methodName(), argTypes);
        if (r.ambiguous() < 2) return false;
        if (sa.diagnostics() != null) {
            sa.diagnostics().error(mc, "call to '" + mc.methodName() + "' is ambiguous between "
                    + r.ambiguous() + " overloads — add a cast to pick one", "SEM057");
        }
        return true;
    }

    private List<ExternalClasspath.MethodSignature> jarCandidates(String owner, String name) {
        Map<List<String>, ExternalClasspath.MethodSignature> found = new LinkedHashMap<>();
        ArrayDeque<String> queue = new ArrayDeque<>(List.of(owner));
        Set<String> seen = new HashSet<>();
        // §814: the type arguments each supertype receives on the way up
        // (`S3Builder extends Builder<S3Builder, S3>`), bound to its own
        // type parameters when it is visited.
        Map<String, List<String>> actuals = new java.util.HashMap<>();
        while (!queue.isEmpty()) {
            String cls = queue.poll();
            byte[] bytes = classBytes.get(cls);
            if (!seen.add(cls) || bytes == null) continue;
            ClassReader reader = new ClassReader(bytes);
            boolean iface = (reader.getAccess() & Opcodes.ACC_INTERFACE) != 0;
            Map<String, String> bindings = new java.util.HashMap<>();
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public void visit(int version, int access, String n, String classSig, String sup, String[] ifs) {
                    List<String> formals = GenericSignatures.formalNames(classSig);
                    List<String> args = actuals.getOrDefault(cls, List.of());
                    for (int i = 0; i < formals.size() && i < args.size(); i++) bindings.put(formals.get(i), args.get(i));
                    for (Map.Entry<String, List<String>> st : GenericSignatures.supertypes(classSig, bindings)) {
                        actuals.putIfAbsent(st.getKey(), st.getValue());
                    }
                }

                @Override
                public MethodVisitor visitMethod(int access, String n, String desc, String sig, String[] ex) {
                    if (n.equals(name) && (access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_BRIDGE
                            | Opcodes.ACC_SYNTHETIC)) == 0) {
                        ExternalClasspath.MethodSignature s = signature(desc,
                                (access & Opcodes.ACC_STATIC) != 0, iface,
                                (access & Opcodes.ACC_VARARGS) != 0);
                        String generic = GenericSignatures.returnDescriptor(sig, bindings);
                        if (generic != null && !generic.equals(s.returnDescriptor())) {
                            s = new ExternalClasspath.MethodSignature(s.parameterDescriptors(), s.returnDescriptor(),
                                    s.isStatic(), s.ownerIsInterface(), s.isVarargs(), generic);
                        }
                        found.putIfAbsent(s.parameterDescriptors(), s);
                    }
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
            if (reader.getSuperName() != null) queue.add(reader.getSuperName());
            queue.addAll(List.of(reader.getInterfaces()));
        }
        return new ArrayList<>(found.values());
    }

    private static List<ExternalClasspath.MethodSignature> jdkCandidates(String owner, String name) {
        Map<List<String>, ExternalClasspath.MethodSignature> found = new LinkedHashMap<>();
        Class<?> cls = jdkClass(owner);
        if (cls == null) return List.of();
        for (Method m : cls.getMethods()) {
            if (!m.getName().equals(name) || m.isBridge() || m.isSynthetic()) continue;
            ExternalClasspath.MethodSignature s = signature(org.objectweb.asm.Type.getMethodDescriptor(m),
                    Modifier.isStatic(m.getModifiers()), cls.isInterface(), m.isVarArgs());
            found.putIfAbsent(s.parameterDescriptors(), s);
        }
        return new ArrayList<>(found.values());
    }

    private static ExternalClasspath.MethodSignature signature(String desc, boolean isStatic,
                                                               boolean iface, boolean varargs) {
        org.objectweb.asm.Type mt = org.objectweb.asm.Type.getMethodType(desc);
        List<String> params = new ArrayList<>();
        for (org.objectweb.asm.Type t : mt.getArgumentTypes()) params.add(t.getDescriptor());
        return new ExternalClasspath.MethodSignature(params, mt.getReturnType().getDescriptor(),
                isStatic, iface, varargs);
    }

    private boolean applicable(ExternalClasspath.MethodSignature c, List<String> args, int phase) {
        int pc = c.parameterDescriptors().size();
        if (phase < 3 ? pc != args.size() : !c.isVarargs() || args.size() < pc - 1) return false;
        for (int i = 0; i < args.size(); i++) {
            if (!fits(args.get(i), param(c, i, phase == 3), phase >= 2)) return false;
        }
        return true;
    }

    private static String param(ExternalClasspath.MethodSignature c, int i, boolean expandVarargs) {
        List<String> ps = c.parameterDescriptors();
        return expandVarargs && i >= ps.size() - 1 ? ps.get(ps.size() - 1).substring(1) : ps.get(i);
    }

    private boolean fits(String arg, String param, boolean loose) {
        if (arg.equals(UNKNOWN)) return true;
        if (arg.equals(LAMBDA)) return isReference(param) && isInterface(param);
        if (isReference(arg) == isReference(param)) return subtype(arg, param);
        if (!loose) return false;
        if (!isReference(arg)) return subtype(box(arg), param);
        String unboxed = unbox(arg);
        return unboxed != null && subtype(unboxed, param);
    }

    private Result mostSpecific(List<ExternalClasspath.MethodSignature> applicable, int n) {
        List<ExternalClasspath.MethodSignature> maximal = new ArrayList<>();
        for (ExternalClasspath.MethodSignature m : applicable) {
            boolean best = true;
            for (ExternalClasspath.MethodSignature o : applicable) {
                if (m != o && !moreSpecific(m, o, n)) { best = false; break; }
            }
            if (best) maximal.add(m);
        }
        return maximal.size() == 1 ? new Result(maximal.get(0), 0) : new Result(null, applicable.size());
    }

    private boolean moreSpecific(ExternalClasspath.MethodSignature m1, ExternalClasspath.MethodSignature m2, int n) {
        boolean varargs = m1.parameterDescriptors().size() != n || m2.parameterDescriptors().size() != n
                || (m1.isVarargs() && m2.isVarargs());
        int k = Math.max(n, Math.max(m1.parameterDescriptors().size(), m2.parameterDescriptors().size()));
        for (int i = 0; i < k; i++) {
            String a = varargs ? param(m1, i, true) : m1.parameterDescriptors().get(i);
            String b = varargs ? param(m2, i, true) : m2.parameterDescriptors().get(i);
            if (isReference(a) != isReference(b) || !subtype(a, b)) return false;
        }
        return true;
    }

    private boolean subtype(String s, String t) {
        if (s.equals(t)) return true;
        if (!isReference(s)) return !isReference(t) && widens(s.charAt(0), t.charAt(0));
        if (t.equals(OBJECT)) return true;
        if (s.startsWith("[")) {
            if (t.startsWith("[")) {
                String sc = s.substring(1);
                String tc = t.substring(1);
                return isReference(sc) && isReference(tc) ? subtype(sc, tc) : sc.equals(tc);
            }
            return t.equals("Ljava/lang/Cloneable;") || t.equals("Ljava/io/Serializable;");
        }
        if (t.startsWith("[")) return false;
        String target = t.substring(1, t.length() - 1);
        ArrayDeque<String> queue = new ArrayDeque<>(List.of(s.substring(1, s.length() - 1)));
        Set<String> seen = new HashSet<>();
        while (!queue.isEmpty()) {
            String cls = queue.poll();
            if (cls.equals(target)) return true;
            if (!seen.add(cls)) continue;
            List<String> sup = supertypes(cls);
            if (sup == null) {
                uncertain = true;
                return true;
            }
            queue.addAll(sup);
        }
        return false;
    }

    /** Direct supertypes, or null when the class is on no classpath (hierarchy unknown). */
    private List<String> supertypes(String internal) {
        byte[] bytes = classBytes.get(internal);
        if (bytes != null) {
            ClassReader r = new ClassReader(bytes);
            List<String> out = new ArrayList<>(List.of(r.getInterfaces()));
            if (r.getSuperName() != null) out.add(r.getSuperName());
            return out;
        }
        Class<?> c = JdkReflectionResolver.isJdkClass(internal) ? jdkClass(internal) : null;
        if (c == null) return null;
        List<String> out = new ArrayList<>();
        for (Class<?> i : c.getInterfaces()) out.add(org.objectweb.asm.Type.getInternalName(i));
        if (c.getSuperclass() != null) out.add(org.objectweb.asm.Type.getInternalName(c.getSuperclass()));
        return out;
    }

    private boolean isInterface(String desc) {
        if (!desc.startsWith("L")) return false;
        String internal = desc.substring(1, desc.length() - 1);
        byte[] bytes = classBytes.get(internal);
        if (bytes != null) return (new ClassReader(bytes).getAccess() & Opcodes.ACC_INTERFACE) != 0;
        Class<?> c = jdkClass(internal);
        return c == null || c.isInterface();
    }

    private static Class<?> jdkClass(String internal) {
        try {
            return Class.forName(internal.replace('/', '.'), false, ExternalOverloads.class.getClassLoader());
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean isReference(String desc) {
        return desc.startsWith("L") || desc.startsWith("[");
    }

    /** JLS 5.1.2 widening primitive conversion (identity excluded). */
    private static boolean widens(char s, char t) {
        String to = switch (s) {
            case 'B' -> "SIJFD";
            case 'S', 'C' -> "IJFD";
            case 'I' -> "JFD";
            case 'J' -> "FD";
            case 'F' -> "D";
            default -> "";
        };
        return to.indexOf(t) >= 0;
    }

    private static String box(String prim) {
        return switch (prim) {
            case "Z" -> "Ljava/lang/Boolean;";
            case "B" -> "Ljava/lang/Byte;";
            case "S" -> "Ljava/lang/Short;";
            case "C" -> "Ljava/lang/Character;";
            case "I" -> "Ljava/lang/Integer;";
            case "J" -> "Ljava/lang/Long;";
            case "F" -> "Ljava/lang/Float;";
            default -> "Ljava/lang/Double;";
        };
    }

    private static String unbox(String ref) {
        for (String p : List.of("Z", "B", "S", "C", "I", "J", "F", "D")) {
            if (box(p).equals(ref)) return p;
        }
        return null;
    }

    static String argDescriptor(Type t) {
        return switch (t) {
            case Type.PrimitiveType p -> switch (Type.canonicalPrimitiveName(p.name())) {
                case "int" -> "I";
                case "long" -> "J";
                case "double" -> "D";
                case "float" -> "F";
                case "boolean", "bool" -> "Z";
                case "byte" -> "B";
                case "char" -> "C";
                case "short" -> "S";
                default -> UNKNOWN;
            };
            case Type.NullableType n when n.inner() instanceof Type.PrimitiveType ->
                    unknownOr(argDescriptor(n.inner()), ExternalOverloads::box);
            case Type.NullableType n -> argDescriptor(n.inner());
            case Type.ArrayType a -> unknownOr(argDescriptor(a.componentType()), c -> "[" + c);
            case Type.FunctionType _ -> LAMBDA;
            case Type.ClassType c -> JvmTypeMapper.classDescriptor(c);
            case null, default -> UNKNOWN;
        };
    }

    private static String unknownOr(String d, java.util.function.UnaryOperator<String> f) {
        return d.equals(UNKNOWN) || d.equals(LAMBDA) ? UNKNOWN : f.apply(d);
    }
}
