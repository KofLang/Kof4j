package dev.kof.compiler;

import java.util.List;


public final class BuiltinTypes {

    private BuiltinTypes() {}


    public static final Type STRING = new Type.ClassType("java.lang", "String", List.of());


    public static final Type STRING_ARRAY = new Type.ArrayType(STRING);


    public static boolean isString(Type type) {
        if (type instanceof Type.ClassType ct) {
            return "java.lang".equals(ct.packageName()) && "String".equals(ct.name());
        }
        return false;
    }


    public static boolean isObject(Type type) {
        if (type instanceof Type.ClassType ct) {
            return "java.lang".equals(ct.packageName()) && "Object".equals(ct.name());
        }
        return false;
    }


    public static boolean isReferenceType(Type type) {
        return type instanceof Type.ClassType || type instanceof Type.ArrayType;
    }


    public static final Type LIST = new Type.ClassType("kof", "List", List.of());


    public static boolean isList(Type type) {
        if (type instanceof Type.ClassType ct) {
            return "kof".equals(ct.packageName()) && "List".equals(ct.name());
        }
        return false;
    }


    public static final Type MAP = new Type.ClassType("kof", "Map", List.of());


    public static boolean isMap(Type type) {
        if (type instanceof Type.ClassType ct) {
            return "kof".equals(ct.packageName()) && "Map".equals(ct.name());
        }
        return false;
    }

    public static Type mapKey(Type type) {
        if (type instanceof Type.ClassType ct && "Map".equals(ct.name())
                && ct.typeArguments().size() == 2) {
            return ct.typeArguments().get(0);
        }
        return Type.UnknownType.UNKNOWN;
    }

    /** §107: elemento de List/Set (typeArgs = [elem]); Unknown se ausente. */
    public static Type listElement(Type type) {
        if (type instanceof Type.ClassType ct && "List".equals(ct.name())
                && !ct.typeArguments().isEmpty()) {
            return ct.typeArguments().get(0);
        }
        return Type.UnknownType.UNKNOWN;
    }

    public static Type setElement(Type type) {
        if (type instanceof Type.ClassType ct && "Set".equals(ct.name())
                && !ct.typeArguments().isEmpty()) {
            return ct.typeArguments().get(0);
        }
        return Type.UnknownType.UNKNOWN;
    }

    public static Type mapValue(Type type) {
        if (type instanceof Type.ClassType ct && "Map".equals(ct.name())
                && ct.typeArguments().size() == 2) {
            return ct.typeArguments().get(1);
        }
        return Type.UnknownType.UNKNOWN;
    }


    public static final Type SET = new Type.ClassType("kof", "Set", List.of());


    /**
     * Canais tipados (concorrência): {@code channel<T>()}. O tipo carrega o
     * elemento T; em runtime o canal é opaco (JVM: Long do registry de filas;
     * Native: pointer p/ a struct; JS: objeto com array). Métodos: {@code send(v)},
     * {@code receive()} -> T.
     */
    public static final Type CHANNEL = new Type.ClassType("kof.concurrent", "Channel", List.of());

    /**
     * #753: {@code Handle} sem type-args (a forma nua que {@code baseTypeName}
     * já reconhece no guard de tipo não resolvido). Sem o pin, a forma nua
     * caía num {@code ClassType("", "Handle")} e o JVM emitia {@code LHandle;}
     * (classe inexistente) → {@code NoClassDefFoundError: Handle} no load; a
     * forma parametrizada {@code Handle<T>} já era mapeada em {@code Type.of}.
     */
    public static final Type HANDLE = new Type.ClassType("kof.concurrent", "Handle", List.of());

    public static boolean isChannel(Type type) {
        if (type instanceof Type.ClassType ct) {
            return "kof.concurrent".equals(ct.packageName()) && "Channel".equals(ct.name());
        }
        return false;
    }

    public static Type channelElement(Type type) {
        if (type instanceof Type.ClassType ct && "Channel".equals(ct.name())
                && !ct.typeArguments().isEmpty()) {
            return ct.typeArguments().get(0);
        }
        return Type.UnknownType.UNKNOWN;
    }


    /**
     * Enums declarados na compilação corrente: em runtime o valor do enum É
     * o nome (String) — mapeado para java/lang/String em todos os backends.
     */
    private static final java.util.Set<String> ENUM_NAMES =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void resetEnums() {
        ENUM_NAMES.clear();
    }

    public static void registerEnum(String name) {
        if (name != null) ENUM_NAMES.add(name);
    }

    public static boolean isEnumName(String name) {
        return name != null && ENUM_NAMES.contains(name);
    }

    public static boolean isEnumType(Type type) {
        // #445: exige só o NOME registrado — o pacote real chega no tipo via
        // ClassSymbol/declPackage; a era "enum = pkg-vazio" morreu no D-ENUM207
        // (valor é INSTÂNCIA da classe emitida, não a String do nome).
        return type instanceof Type.ClassType ct && isEnumName(ct.name());
    }


    public static boolean isSet(Type type) {
        if (type instanceof Type.ClassType ct) {
            return "kof".equals(ct.packageName()) && "Set".equals(ct.name());
        }
        return false;
    }


    /**
     * §373 (issue #443, "§297 do corpo da issue"): tipo builtin para nome
     * NÚ (sem type-args) de coleção/concorrência em posição DECLARADA — campo,
     * parâmetro, retorno, var. O pin do {@code toType} (#139/#150/#214/§243)
     * cobria só o caminho IR do {@code lowerField}; o caminho do SÍMBOLO
     * ({@code SymbolTableBuilder.defineClassMembers →
     * MemberResolver.resolveType → Type.of → qualifyDeep passo 2b}) nunca o
     * via, e o tipo do FieldSymbol ficava {@code ClassType("", "List")} —
     * divergindo do IRField ({@code kof/List}), o que produzia Fieldrefs com
     * descritor fantasma ({@code Field Box.items:LList;}, receiver
     * {@code List.size}) e {@code ClassNotFoundException: List} no LOAD da
     * classe (a resolução de Fieldref carrega o tipo do descritor ANTES de
     * comparar nome). Espelha o MESMO conjunto de pins do {@code toType}
     * (inclui o apelido {@code LinkedList}); o shadowing do usuário (§243,
     * DECISIONS §4) é preservado pelo guard do caller (qualifyDeep 2b:
     * {@code !unitDeclaresType && sa.getClass == null}). Retorna null caso
     * contrário.
     */
    public static Type declaredCollectionType(String name) {
        if ("LinkedList".equals(name)) return LIST;
        String base = baseTypeName(name);
        if (base == null) return null;
        return switch (base) {
            case "List" -> LIST;
            case "Set" -> SET;
            case "Map" -> MAP;
            case "Channel" -> CHANNEL;
            case "Handle" -> HANDLE;
            default -> null;
        };
    }


    /**
     * §249: nome simples (sem type-args) de um tipo builtin de coleção/
     * concorrência — `List`, `Map`, `Set`, `Channel`, `Handle` e os apelidos
     * `ArrayList`/`HashMap`/`HashSet`. O {@code Type.of} só mapeia esses nomes
     * na forma parametrizada (`List<Int>`); a forma nua (`List xs = listOf(...)`)
     * também é válida e não pode cair no guard de tipo não resolvido.
     * Retorna {@code null} se não for builtin.
     */
    public static String baseTypeName(String name) {
        return switch (name) {
            case "List", "ArrayList" -> "List";
            case "Map", "HashMap" -> "Map";
            case "Set", "HashSet" -> "Set";
            case "Channel" -> "Channel";
            case "Handle" -> "Handle";
            default -> null;
        };
    }
}
