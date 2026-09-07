package dev.kof.compiler;


public final class NativeTypeMapper {

    private NativeTypeMapper() {
    }

    static int typeSize(Type type) {
        return switch (type) {
            case Type.PrimitiveType pt -> switch (pt.name()) {
                case "long", "Long" -> 8;
                case "double", "Double" -> 8;
                case "float", "Float" -> 4;
                case "int", "Int", "boolean", "bool", "Bool", "byte", "Byte", "short", "Short", "char", "Char" -> 4;
                case "void", "Void" -> 0;
                default -> 8;
            };
            case Type.ClassType ct -> 8;
            case Type.ArrayType at -> 8;
            default -> 8;
        };
    }

    static boolean isDoubleWidth(Type type) {
        if (type instanceof Type.PrimitiveType pt) {
            return "long".equals(pt.name()) || "Long".equals(pt.name()) ||
                   "double".equals(pt.name()) || "Double".equals(pt.name());
        }
        return false;
    }

    static boolean isIntegerType(Type type) {
        if (type instanceof Type.PrimitiveType pt) {
            return switch (pt.name()) {
                case "int", "Int", "boolean", "bool", "Bool", "byte", "Byte", "short", "Short", "char", "Char" -> true;
                default -> false;
            };
        }
        return false;
    }

    static String accessorSuffix(Type type) {
        return switch (type) {
            case Type.PrimitiveType pt -> switch (pt.name()) {
                case "int", "Int", "boolean", "bool", "Bool", "byte", "Byte", "short", "Short", "char", "Char" -> "l";
                case "long", "Long" -> "q";
                case "float", "Float" -> "ss";
                case "double", "Double" -> "sd";
                default -> "q";
            };
            default -> "q";
        };
    }

    static String accessorRetReg(Type type) {
        return switch (type) {
            case Type.PrimitiveType pt -> switch (pt.name()) {
                case "int", "Int", "boolean", "bool", "Bool", "byte", "Byte", "short", "Short", "char", "Char" -> "%eax";
                case "long", "Long" -> "%rax";
                case "float", "Float", "double", "Double" -> "%xmm0";
                default -> "%rax";
            };
            default -> "%rax";
        };
    }

    static String storeSuffix(Type type) {
        return switch (type) {
            case Type.PrimitiveType pt -> switch (pt.name()) {
                case "int", "Int", "boolean", "bool", "Bool", "byte", "Byte", "short", "Short", "char", "Char" -> "l";
                case "long", "Long" -> "q";
                case "float", "Float" -> "ss";
                case "double", "Double" -> "sd";
                default -> "q";
            };
            default -> "q";
        };
    }

    static String loadSuffix(Type type) {
        return switch (type) {
            case Type.PrimitiveType pt -> switch (pt.name()) {
                case "int", "Int", "boolean", "bool", "Bool", "byte", "Byte", "short", "Short", "char", "Char" -> "l";
                case "long", "Long" -> "q";
                case "float", "Float" -> "ss";
                case "double", "Double" -> "sd";
                default -> "q";
            };
            default -> "q";
        };
    }

    static String constructorSrcReg(int idx, Type type) {
        if (type instanceof Type.PrimitiveType pt && ("float".equals(pt.name()) || "Float".equals(pt.name()) || "double".equals(pt.name()) || "Double".equals(pt.name()))) {
            return switch (idx) {
                case 1 -> "%xmm0";
                case 2 -> "%xmm1";
                default -> "%xmm0";
            };
        }
        return switch (idx) {
            case 1 -> "%esi";
            case 2 -> "%edx";
            case 3 -> "%ecx";
            case 4 -> "%r8d";
            case 5 -> "%r9d";
            default -> "%eax";
        };
    }

    static String argRegName(int idx) {
        return switch (idx) {
            case 1 -> "rsi";
            case 2 -> "rdx";
            case 3 -> "rcx";
            case 4 -> "r8";
            case 5 -> "r9";
            default -> "rax";
        };
    }

    static String argRegNameD(int idx) {
        return switch (idx) {
            case 1 -> "esi";
            case 2 -> "edx";
            case 3 -> "ecx";
            case 4 -> "r8d";
            case 5 -> "r9d";
            default -> "eax";
        };
    }
}
