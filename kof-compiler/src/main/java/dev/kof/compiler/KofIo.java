package dev.kof.compiler;

import java.util.List;


public final class KofIo {

    private KofIo() {}

    static final Type FILE = new Type.ClassType("kof.io", "File", List.of());
    static final Type PATH = new Type.ClassType("kof.io", "Path", List.of());
    static final Type DIRECTORY = new Type.ClassType("kof.io", "Directory", List.of());

    static boolean isFile(Type t) { return FILE.equals(t); }
    static boolean isPath(Type t) { return PATH.equals(t); }
    static boolean isDirectory(Type t) { return DIRECTORY.equals(t); }

    static boolean isIoType(Type t) {
        return isFile(t) || isPath(t) || isDirectory(t);
    }

    static boolean isConstructor(String name) {
        return "File".equals(name) || "Path".equals(name) || "Directory".equals(name);
    }

    static Type constructorType(String name) {
        return switch (name) {
            case "File" -> FILE;
            case "Path" -> PATH;
            case "Directory" -> DIRECTORY;
            default -> Type.UnknownType.UNKNOWN;
        };
    }


    record IoCall(String function, Type returnType, List<Type> parameterTypes) {}

    private static final Type STR = BuiltinTypes.STRING;
    private static final Type STR_NULL = new Type.NullableType(STR);
    private static final Type INT = Type.PrimitiveType.INT;
    private static final Type LONG = Type.PrimitiveType.LONG;
    private static final Type BOOL = Type.PrimitiveType.BOOL;
    private static final Type INT_ARRAY = new Type.ArrayType(INT);
    private static final Type STRING_LIST = new Type.ClassType("kof", "List", List.of(STR));


    static IoCall instanceMethod(Type receiver, String name, int argCount) {
        return switch (name) {
            case "exists" -> argCount == 0 ? new IoCall("kof_io_file_exists", BOOL, List.of()) : null;
            case "isFile" -> argCount == 0 ? new IoCall("kof_io_file_is_file", BOOL, List.of()) : null;
            case "isDirectory" -> argCount == 0 ? new IoCall("kof_io_file_is_dir", BOOL, List.of()) : null;
            case "readText" -> argCount == 0 ? new IoCall("kof_io_read_text", STR_NULL, List.of()) : null;
            case "writeText" -> argCount == 1 ? new IoCall("kof_io_write_text", BOOL, List.of(STR)) : null;
            case "appendText" -> argCount == 1 ? new IoCall("kof_io_append_text", BOOL, List.of(STR)) : null;
            case "readBytes" -> argCount == 0 ? new IoCall("kof_io_read_bytes", INT_ARRAY, List.of()) : null;
            // leitura com offset p/ arquivos grandes (GGUF de LLM): sem carregar o arquivo inteiro
            case "readRange" -> argCount == 2
                    ? new IoCall("kof_io_read_range", INT_ARRAY, List.of(LONG, LONG)) : null;
            case "writeBytes" -> argCount == 1 ? new IoCall("kof_io_write_bytes", BOOL, List.of(INT_ARRAY)) : null;
            case "appendBytes" -> argCount == 1 ? new IoCall("kof_io_append_bytes", BOOL, List.of(INT_ARRAY)) : null;
            // delete de instância é 0-args (File.delete()/Directory.delete()).
            // Sem a guarda de aridade, `app.delete("/x") {…}` (2 args) colidia
            // com este case em hasReturnValueInner (receiver UNKNOWN) → KofPop
            // extra sobre kof_web_route (void) → underflow no Frame.merge
            // (COMP002, GitHub #29 / bug 54).
            case "delete" -> argCount == 0
                    ? (isDirectory(receiver)
                            ? new IoCall("kof_io_dir_delete", BOOL, List.of())  // recursivo
                            : new IoCall("kof_io_delete", BOOL, List.of()))
                    : null;
            case "size" -> argCount == 0 ? new IoCall("kof_io_file_size", LONG, List.of()) : null;
            case "name" -> argCount == 0 ? new IoCall("kof_io_file_name", STR, List.of()) : null;
            case "resolve" -> argCount == 1 ? new IoCall("kof_io_path_resolve", PATH, List.of(STR)) : null;
            case "parent" -> argCount == 0 ? new IoCall("kof_io_path_parent", PATH, List.of()) : null;
            case "fileName" -> argCount == 0 ? new IoCall("kof_io_path_file_name", STR, List.of()) : null;
            case "extension" -> argCount == 0 ? new IoCall("kof_io_path_extension", STR, List.of()) : null;
            case "normalize" -> argCount == 0 ? new IoCall("kof_io_path_normalize", PATH, List.of()) : null;
            case "isAbsolute" -> argCount == 0 ? new IoCall("kof_io_path_is_absolute", BOOL, List.of()) : null;
            case "toAbsolute" -> argCount == 0 ? new IoCall("kof_io_path_to_absolute", PATH, List.of()) : null;
            case "create" -> argCount == 0 ? new IoCall("kof_io_dir_create", BOOL, List.of()) : null;
            case "createDirectories" -> argCount == 0 ? new IoCall("kof_io_dir_create_dirs", BOOL, List.of()) : null;
            case "list" -> argCount == 0 ? new IoCall("kof_io_dir_list", STRING_LIST, List.of()) : null;
            default -> null;
        };
    }


    static IoCall staticMethod(String className, String name, int argCount) {
        if ("File".equals(className)) {
            return switch (name) {
                case "exists" -> argCount == 1 ? new IoCall("kof_io_file_exists", BOOL, List.of(STR)) : null;
                case "isFile" -> argCount == 1 ? new IoCall("kof_io_file_is_file", BOOL, List.of(STR)) : null;
                case "isDirectory" -> argCount == 1 ? new IoCall("kof_io_file_is_dir", BOOL, List.of(STR)) : null;
                case "readText" -> argCount == 1 ? new IoCall("kof_io_read_text", STR_NULL, List.of(STR)) : null;
                case "writeText" -> argCount == 2 ? new IoCall("kof_io_write_text", BOOL, List.of(STR, STR)) : null;
                case "appendText" -> argCount == 2 ? new IoCall("kof_io_append_text", BOOL, List.of(STR, STR)) : null;
                case "readBytes" -> argCount == 1 ? new IoCall("kof_io_read_bytes", INT_ARRAY, List.of(STR)) : null;
                case "readRange" -> argCount == 3
                        ? new IoCall("kof_io_read_range_path", INT_ARRAY, List.of(STR, LONG, INT)) : null;
                case "writeBytes" -> argCount == 2 ? new IoCall("kof_io_write_bytes", BOOL, List.of(STR, INT_ARRAY)) : null;
                case "delete" -> argCount == 1 ? new IoCall("kof_io_delete", BOOL, List.of(STR)) : null;
                case "size" -> argCount == 1 ? new IoCall("kof_io_file_size", LONG, List.of(STR)) : null;
                case "name" -> argCount == 1 ? new IoCall("kof_io_file_name", STR, List.of(STR)) : null;
                default -> null;
            };
        }
        if ("Path".equals(className)) {
            return switch (name) {
                case "resolve" -> argCount == 2 ? new IoCall("kof_io_path_resolve", STR, List.of(STR, STR)) : null;
                case "parent" -> argCount == 1 ? new IoCall("kof_io_path_parent", STR, List.of(STR)) : null;
                case "fileName" -> argCount == 1 ? new IoCall("kof_io_path_file_name", STR, List.of(STR)) : null;
                case "extension" -> argCount == 1 ? new IoCall("kof_io_path_extension", STR, List.of(STR)) : null;
                case "normalize" -> argCount == 1 ? new IoCall("kof_io_path_normalize", STR, List.of(STR)) : null;
                case "isAbsolute" -> argCount == 1 ? new IoCall("kof_io_path_is_absolute", BOOL, List.of(STR)) : null;
                case "toAbsolute" -> argCount == 1 ? new IoCall("kof_io_path_to_absolute", STR, List.of(STR)) : null;
                case "exists" -> argCount == 1 ? new IoCall("kof_io_file_exists", BOOL, List.of(STR)) : null;
                default -> null;
            };
        }
        if ("Directory".equals(className)) {
            return switch (name) {
                case "exists" -> argCount == 1 ? new IoCall("kof_io_file_exists", BOOL, List.of(STR)) : null;
                case "create" -> argCount == 1 ? new IoCall("kof_io_dir_create", BOOL, List.of(STR)) : null;
                case "createDirectories" -> argCount == 1 ? new IoCall("kof_io_dir_create_dirs", BOOL, List.of(STR)) : null;
                case "delete" -> argCount == 1 ? new IoCall("kof_io_dir_delete", BOOL, List.of(STR)) : null;
                case "list" -> argCount == 1 ? new IoCall("kof_io_dir_list", STRING_LIST, List.of(STR)) : null;
                default -> null;
            };
        }
        return null;
    }


    static boolean isIdentityMethod(String name) {
        return "path".equals(name);
    }
}