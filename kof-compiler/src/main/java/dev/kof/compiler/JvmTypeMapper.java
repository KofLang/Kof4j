package dev.kof.compiler;

import java.util.List;
import java.util.Map;

final class JvmTypeMapper {

    private JvmTypeMapper() {
    }

    static String toDescriptor(Type type) {
        return switch (type) {
            case Type.PrimitiveType p -> primitiveDescriptor(p);
            case Type.ClassType c when KofUi.isUiType(c) || KofMedia.isHandleType(c) -> "I";
            case Type.ClassType c -> classDescriptor(c);
            case Type.ArrayType a -> "[" + toDescriptor(a.componentType());
            case Type.TypeVariable tv -> "Ljava/lang/Object;";
            case Type.WildcardType wt -> "Ljava/lang/Object;";
            case Type.FunctionType ft -> ft.className() != null
                    ? "L" + ft.className() + ";" : "Ljava/lang/Object;";
            case Type.UnknownType ut -> "Ljava/lang/Object;";
            case Type.NullableType n -> toDescriptor(n.inner());
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
        // enum: o valor em runtime é o nome (String)
        if (c.packageName().isEmpty() && BuiltinTypes.isEnumName(c.name())) {
            return "Ljava/lang/String;";
        }
        return "L" + internalName + ";";
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

    static String toInternalName(String packageName, String simpleName) {
        if (simpleName.contains("/")) return simpleName;
        if (simpleName.contains(".")) return simpleName.replace('.', '/');
        if ("kof".equals(packageName) && "List".equals(simpleName)) return "java/util/ArrayList";
        if ("kof".equals(packageName) && "Set".equals(simpleName)) return "java/util/HashSet";
        if ("kof".equals(packageName) && "Map".equals(simpleName)) return "java/util/HashMap";
        if ("kof.concurrent".equals(packageName) && "Channel".equals(simpleName)) return "java/util/concurrent/LinkedBlockingQueue";
        if (packageName.isEmpty() && BuiltinTypes.isEnumName(simpleName)) return "java/lang/String";
        if (packageName.isEmpty()) return simpleName;
        return packageName.replace('.', '/') + "/" + simpleName;
    }

    static Type fromTypeName(String typeName) {
        return Type.of(typeName);
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
