package dev.kof.compiler;

/**
 * Dispatch de funções JSON do runtime nativo (encode/decode) por tipo Kof.
 * Puro — não usa estado de compilação.
 */
final class JsonDispatch {

    private JsonDispatch() {}

    static int listTag(Type elemType) {
        if (BuiltinTypes.isString(elemType)) return 1;
        if (elemType instanceof Type.PrimitiveType pt && "bool".equals(pt.name())) return 2;
        return 0;
    }

    static String encodeFunction(Type type) {
        if (type instanceof Type.PrimitiveType pt) {
            return switch (pt.name()) {
                case "int", "char", "byte", "short" -> "kof_json_encode_int";
                case "long" -> "kof_json_encode_long";
                case "bool" -> "kof_json_encode_bool";
                case "float" -> "kof_json_encode_float";
                case "double" -> "kof_json_encode_double";
                default -> "kof_json_encode_int";
            };
        }
        if (BuiltinTypes.isString(type)) return "kof_json_encode_string";
        if (BuiltinTypes.isList(type)) return "kof_json_encode_list";
        if (type instanceof Type.ArrayType) return "kof_json_encode_array";
        return "kof_json_encode";
    }

    static String decodeFunction(Type type, Type listElementType) {
        if (type instanceof Type.PrimitiveType pt) {
            return switch (pt.name()) {
                case "int", "char", "byte", "short" -> "kof_json_decode_int";
                case "long" -> "kof_json_decode_long";
                case "bool" -> "kof_json_decode_bool";
                case "float" -> "kof_json_decode_float";
                case "double" -> "kof_json_decode_double";
                default -> "kof_json_decode_int";
            };
        }
        if (type instanceof Type.ArrayType at) {
            if (at.componentType() instanceof Type.PrimitiveType ap) {
                return switch (ap.name()) {
                    case "int", "char", "byte", "short" -> "kof_json_decode_int_array";
                    case "bool" -> "kof_json_decode_bool_array";
                    case "long" -> "kof_json_decode_long_array";
                    case "double", "float" -> "kof_json_decode_double_array";
                    default -> "kof_json_decode_int_array";
                };
            }
            if (BuiltinTypes.isString(at.componentType())) return "kof_json_decode_string_array";
        }
        if (BuiltinTypes.isString(type)) return "kof_json_decode_string";
        if (BuiltinTypes.isList(type)) {
            if (listElementType instanceof Type.PrimitiveType ep && "int".equals(ep.name())) {
                return "kof_json_decode_int_list";
            }
            if (BuiltinTypes.isString(listElementType)) return "kof_json_decode_string_list";
            return "kof_json_decode_list";
        }
        if (type instanceof Type.ClassType ct) return "kof_json_decode_" + sanitize(ct.name());
        return "kof_json_decode_string";
    }

    static String sanitize(String name) {
        return name.replace(".", "_").replace("/", "_").replace("-", "_");
    }
}