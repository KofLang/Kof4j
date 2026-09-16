package dev.kof.compiler.jvm;
import dev.kof.compiler.BuiltinTypes;
import dev.kof.compiler.KofMedia;
import dev.kof.compiler.KofUi;
import dev.kof.compiler.Type;

import java.util.List;
import java.util.Map;

public final class JvmTypeMapper {

    private JvmTypeMapper() {
    }

    static String toDescriptor(Type type) {
        return switch (type) {
            case Type.PrimitiveType p -> primitiveDescriptor(p);
            case Type.ClassType c when KofUi.isUiType(c) || KofMedia.isHandleType(c) -> "I";
            case Type.ClassType c -> classDescriptor(c);
            case Type.ArrayType a -> "[" + toDescriptor(a.componentType());
            case Type.TypeVariable _ -> "Ljava/lang/Object;";
            case Type.WildcardType _ -> "Ljava/lang/Object;";
            case Type.FunctionType ft -> ft.className() != null
                    ? "L" + ft.className() + ";" : "Ljava/lang/Object;";
            case Type.UnknownType _ -> "Ljava/lang/Object;";
            // D-NULL-INTENT/N1 (mantenedora 15/09, e04f10ff): Nullable(primitivo)
            // precisa carregar null de verdade — apagar para o descritor primitivo
            // (opção A/§125, revogada) tornava `null` inrepresentável no slot/retorno.
            // O boxed é o único jeito de um valor de tipo primitivo carregar uma
            // referência null na JVM (JVMS §2.3/§4.3.2: slot primitivo não é
            // referência). Nullable(referência) já é o próprio inner (inalterado).
            case Type.NullableType n -> n.inner() instanceof Type.PrimitiveType pt
                    ? "L" + boxedInternalName(pt.name()) + ";"
                    : toDescriptor(n.inner());
            default -> "Ljava/lang/Object;";
        };
    }

    static String primitiveDescriptor(Type.PrimitiveType p) {
        return switch (p.name()) {
            case "void", "Void" -> "V";
            case "boolean", "bool", "Bool", "Boolean" -> "Z";
            case "byte", "Byte" -> "B";
            case "short", "Short" -> "S";
            case "char", "Char" -> "C";
            case "int", "Int" -> "I";
            case "long", "Long" -> "J";
            case "float", "Float" -> "F";
            case "double", "Double" -> "D";
            default -> "Ljava/lang/Object;";
        };
    }

    static String classDescriptor(Type.ClassType c) {
        String internalName = c.internalName();
        if ("java.lang".equals(c.packageName()) && "String".equals(c.name())) {
            return "Ljava/lang/String;";
        }
        if ("kof".equals(c.packageName()) && "List".equals(c.name())) {
            return "Ljava/util/ArrayList;";
        }
        if ("kof".equals(c.packageName()) && "Set".equals(c.name())) {
            return "Ljava/util/HashSet;";
        }
        if ("kof".equals(c.packageName()) && "Map".equals(c.name())) {
            return "Ljava/util/HashMap;";
        }
        if ("kof.concurrent".equals(c.packageName()) && "Channel".equals(c.name())) {
            return "Ljava/util/concurrent/LinkedBlockingQueue;";
        }
        // Handle<T> apaga para CompletableFuture (o runtime de spawn é
        // exatamente um) — sem isto, `Handle<Int>` como parâmetro de método
        // gerava descriptor LHandle; (classe inexistente) → ClassNotFoundException
        // / VerifyError (GitHub #31).
        if ("kof.concurrent".equals(c.packageName()) && "Handle".equals(c.name())) {
            return "Ljava/util/concurrent/CompletableFuture;";
        }
        // enum: D-ENUM207 — o valor é uma INSTÂNCIA de enum (classe real
        // emitida por CompilerEnumLowering), não a String do nome. Descriptor
        // próprio L<Dir>; (antes era apagado p/ Ljava/lang/String;).
        return "L" + internalName + ";";
    }

    /**
     * Assinatura genérica (atributo Signature do class file) de um tipo —
     * preserva os type-arguments que o descriptor apaga. Emitida em campos e
     * record components para que `Field.getGenericType()`/
     * `RecordComponent.getGenericType()` devolva `List<Addr>` (não `List`
     * cru) — é o que o decoder JSON usa p/ bindar elementos de coleção de
     * records (GitHub #34 / bug 58). Retorna null quando o tipo não tem
     * type-args (assinatura == descriptor, redundante).
     */
    static String toGenericSignature(Type type) {
        // §128/§187-Native: NullableType (`List<T>?`) NÃO tinha assinatura —
        // o Signature do record component/field ficava ausente e
        // `RecordComponent.getGenericType()` devolvia `List` cru → o decoder
        // JSON não bindava os elementos (`LinkedHashMap` cru →
        // ClassCastException). A nullability foge ao modelo de generics do
        // Java (não há `?` de null): a assinatura é a do inner.
        // (rebase: mesmo fato do df10e71b `type = n.inner()` — uma redação.)
        if (type instanceof Type.NullableType n) return toGenericSignature(n.inner());
        if (type instanceof Type.ClassType c) {
            // tipos apagados p/ int (UI/handle de media): sem assinatura
            // genérica — o descriptor não é L...; e a erasure divergiria
            if (KofUi.isUiType(c) || KofMedia.isHandleType(c)) return null;
            String base = classDescriptor(c);
            if (c.typeArguments().isEmpty()) return null;
            StringBuilder sb = new StringBuilder(base.substring(0, base.length() - 1));
            sb.append('<');
            for (Type arg : c.typeArguments()) {
                sb.append(signatureTypeArg(arg));
            }
            sb.append('>');
            return sb.append(';').toString();
        }
        if (type instanceof Type.ArrayType a) {
            String s = toGenericSignature(a.componentType());
            return s == null ? null : "[" + s;
        }
        return null;
    }

    // GitHub #62 / bug 72: type-arg de assinatura genérica NUNCA pode ser
    // descriptor primitivo (`D`, `I`, ...) — `List<Double>` emitia `Lkof/...<
    // D>;` e `Field.getGenericType()` falhava com GenericSignatureFormatError
    // ("Remaining input: D>"). Em posição de type-arg, primitivo vira o
    // boxed (Ljava/lang/Double;) e nullable vira o inner.
    private static String signatureTypeArg(Type type) {
        if (type instanceof Type.NullableType n) type = n.inner();
        if (type instanceof Type.PrimitiveType) {
            return "L" + boxedInternalName(((Type.PrimitiveType) type).name()) + ";";
        }
        String s = toGenericSignature(type);
        return s != null ? s : toDescriptor(type);
    }

    private static String boxedInternalName(String primitiveName) {
        return switch (primitiveName) {
            case "boolean", "bool", "Bool", "Boolean" -> "java/lang/Boolean";
            case "byte", "Byte" -> "java/lang/Byte";
            case "short", "Short" -> "java/lang/Short";
            case "char", "Char" -> "java/lang/Character";
            case "int", "Int" -> "java/lang/Integer";
            case "long", "Long" -> "java/lang/Long";
            case "float", "Float" -> "java/lang/Float";
            case "double", "Double" -> "java/lang/Double";
            default -> "java/lang/Object";
        };
    }

    static String toMethodDescriptor(Type returnType, List<Type> parameterTypes) {
        StringBuilder sb = new StringBuilder("(");
        for (Type pt : parameterTypes) sb.append(toDescriptor(pt));
        sb.append(")").append(toDescriptor(returnType));
        return sb.toString();
    }

    static String toConstructorDescriptor(List<Type> parameterTypes) {
        StringBuilder sb = new StringBuilder("(");
        for (Type pt : parameterTypes) sb.append(toDescriptor(pt));
        sb.append(")V");
        return sb.toString();
    }

    public static String toInternalName(String packageName, String simpleName) {
        if (simpleName.contains("/")) return simpleName;
        if (simpleName.contains(".")) return simpleName.replace('.', '/');
        if ("kof".equals(packageName) && "List".equals(simpleName)) return "java/util/ArrayList";
        if ("kof".equals(packageName) && "Set".equals(simpleName)) return "java/util/HashSet";
        if ("kof".equals(packageName) && "Map".equals(simpleName)) return "java/util/HashMap";
        if ("kof.concurrent".equals(packageName) && "Channel".equals(simpleName)) return "java/util/concurrent/LinkedBlockingQueue";
        if ("kof.concurrent".equals(packageName) && "Handle".equals(simpleName)) return "java/util/concurrent/CompletableFuture";
        if (packageName.isEmpty()) return simpleName;
        return packageName.replace('.', '/') + "/" + simpleName;
    }

    static Type fromTypeName(String typeName) {
        return Type.of(typeName);
    }

    /**
     * UIW050: handle de UI/mídia APAGA para int no bytecode (ver
     * {@link #toDescriptor}). O emit de comparações/hash de record e a
     * decisão primitive-vs-referência precisam disto: tratar um handle como
     * referência gerava Objects.equals/if_acmp sobre int → VerifyError.
     */
    static boolean isHandleErasedToInt(Type type) {
        if (type instanceof Type.NullableType nt) return isHandleErasedToInt(nt.inner());
        return type instanceof Type.ClassType ct
                && (KofUi.isUiType(ct) || KofMedia.isHandleType(ct));
    }

    static boolean isPrimitive(String descriptor) {
        return "I".equals(descriptor) || "J".equals(descriptor) || "F".equals(descriptor) ||
               "D".equals(descriptor) || "Z".equals(descriptor) || "B".equals(descriptor) ||
               "S".equals(descriptor) || "C".equals(descriptor);
    }

    static boolean isDoubleWidth(String descriptor) {
        return "J".equals(descriptor) || "D".equals(descriptor);
    }

    static final Map<String, String> BUILTIN_TYPES = Map.of(
        "System", "java/lang/System",
        "String", "java/lang/String",
        "PrintStream", "java/io/PrintStream",
        "Integer", "java/lang/Integer",
        "Long", "java/lang/Long",
        "Float", "java/lang/Float",
        "Double", "java/lang/Double",
        "Boolean", "java/lang/Boolean"
    );
}
