package dev.kof.compiler.jvm;
import dev.kof.compiler.BuiltinTypes;
import dev.kof.compiler.KofMedia;
import dev.kof.compiler.KofUi;
import dev.kof.compiler.Type;
import dev.kof.compiler.TypeMetrics;

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
            // §355 (rio da erasure): a variável APAGA para o seu BOUND
            // (javac: `<T extends Animal>` → `LAnimal;`), sem bound → Object.
            // O TypeVariable agora carrega o bound (Type.java §355); o caso
            // antigo (Object puro) continua para os unbounded.
            case Type.TypeVariable tv -> tv.bound() != null
                    ? toDescriptor(tv.bound()) : "Ljava/lang/Object;";
            case Type.WildcardType _ -> "Ljava/lang/Object;";
            case Type.FunctionType ft -> ft.className() != null
                    ? "L" + ft.className() + ";" : "Ljava/lang/Object;";
            case Type.UnknownType _ -> "Ljava/lang/Object;";
            // D-NULL-INTENT (supersede §125 opção A, DECISIONS.md 15/09):
            // Nullable(primitivo) precisa de representação de REFERÊNCIA de
            // verdade — um descriptor primitivo (`I`/`J`/...) não tem onde
            // guardar `null`. O inner NÃO-primitivo (String?/record?/etc.)
            // já é referência por natureza; segue apagando para o próprio
            // inner (comportamento anterior, intocado).
            case Type.NullableType n -> n.inner() instanceof Type.PrimitiveType pt
                    && !Type.isVoid(pt) && TypeMetrics.boxedTypeFor(pt) instanceof Type.ClassType boxed
                    ? classDescriptor(boxed)
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
        // #634: Kof `Map<K,V>` apaga para a INTERFACE `java.util.Map` (não
        // `HashMap`): toda operação de mapa já é `INVOKEINTERFACE
        // java/util/Map` (JvmOpMap) e os decoders JSON já devolvem
        // `Ljava/util/Map;` — o descritor concreto em parâmetro/retorno/campo
        // fazia o verifier rejeitar a passagem de um `Map` do decode para uma
        // função declarada `Map<K,V>` (VerifyError escondido atrás da
        // mensagem de JavaFX do launcher). `mapOf()` continua `new HashMap` —
        // atribuível à interface.
        if ("kof".equals(c.packageName()) && "Map".equals(c.name())) {
            return "Ljava/util/Map;";
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
        // process.Result apaga para KofRuntime$ProcessResult (o binding host de
        // kof_process_run é exatamente um) — sem isto, `Result` vindo de uma
        // lambda (checkcast/invoke descriptor) gerava classe inexistente →
        // ClassNotFoundException / NoClassDefFoundError (mesma forma do #31).
        if ("kof.process".equals(c.packageName()) && "Result".equals(c.name())) {
            return "Ldev/kof/runtime/KofRuntime$ProcessResult;";
        }
        // D-R3-BUFFER: Buffer(U8) apaga para KofRuntime$Buffer (o runtime real do
        // out-buffer) — sem isto, `Buffer` virava a classe inexistente `kof/Buffer`
        // → ClassNotFoundException/NoClassDefFoundError (mesma forma do #31).
        if ("kof".equals(c.packageName()) && "Buffer".equals(c.name())) {
            return "Ldev/kof/runtime/KofRuntime$Buffer;";
        }
        // D-SECRETS face 1: o tipo Kof `Secret` apaga para KofRuntime$Secret
        // (wrapper com toString redigido); sem isto virava a classe inexistente
        // `kof/Secret` → ClassNotFoundException.
        if ("kof".equals(c.packageName()) && "Secret".equals(c.name())) {
            return "Ldev/kof/runtime/KofRuntime$Secret;";
        }
        // D-SECRETS P3: `KeyHandle` apaga para KofRuntime$KeyHandle (mesmo
        // padrao do Secret).
        if ("kof".equals(c.packageName()) && "KeyHandle".equals(c.name())) {
            return "Ldev/kof/runtime/KofRuntime$KeyHandle;";
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
    public static String toGenericSignature(Type type) {
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
        if ("kof".equals(packageName) && "Map".equals(simpleName)) return "java/util/Map";
        if ("kof.concurrent".equals(packageName) && "Channel".equals(simpleName)) return "java/util/concurrent/LinkedBlockingQueue";
        if ("kof.concurrent".equals(packageName) && "Handle".equals(simpleName)) return "java/util/concurrent/CompletableFuture";
        if ("kof.process".equals(packageName) && "Result".equals(simpleName)) return "dev/kof/runtime/KofRuntime$ProcessResult";
        // Same erasure family as `classDescriptor`: these nominal runtime types
        // must map in OWNER position (checkcast/anewarray/getfield) too, or a
        // value crossing a generic container emits the non-existent class
        // (`kof/Buffer`, `kof/Secret`, `kof/KeyHandle`) → NoClassDefFoundError.
        if ("kof".equals(packageName) && "Buffer".equals(simpleName)) return "dev/kof/runtime/KofRuntime$Buffer";
        if ("kof".equals(packageName) && "Secret".equals(simpleName)) return "dev/kof/runtime/KofRuntime$Secret";
        if ("kof".equals(packageName) && "KeyHandle".equals(simpleName)) return "dev/kof/runtime/KofRuntime$KeyHandle";
        if (packageName.isEmpty()) return simpleName;
        return packageName.replace('.', '/') + "/" + simpleName;
    }

    /**
     * §355 (rio da erasure) — NOME INTERNO de erasure de um tipo em posição
     * de OWNER (getfield/putfield/invoke/checkcast/anewarray). A família de
     * bugs #399/#363/#368/#375 é sempre a mesma forma: um TypeVariable (ou
     * qualquer não-ClassType) chegava cru no emit e saía como owner `""`
     * (ClassFormatError: Illegal class name), `"?"` (NoClassDefFoundError: ?)
     * ou o LITERAL `T` (NoClassDefFoundError: T). A erasure JVM manda:
     * variável de tipo → bound (`T: Animal` → Animal), senão Object.
     */
    public static String erasureInternalName(Type t) {
        return switch (t) {
            case Type.ClassType ct -> toInternalName(ct.packageName(), ct.name());
            case Type.TypeVariable tv -> tv.bound() != null
                    ? erasureInternalName(tv.bound()) : "java/lang/Object";
            case Type.WildcardType wt -> wt.bound() != null
                    ? erasureInternalName(wt.bound()) : "java/lang/Object";
            case Type.NullableType nt -> erasureInternalName(nt.inner());
            case Type.FunctionType ft -> ft.className() != null
                    ? ft.className() : "java/lang/Object";
            default -> null;
        };
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
