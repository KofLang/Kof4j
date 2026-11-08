package dev.kof.compiler;

import java.lang.reflect.Method;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolução de assinaturas de métodos do JDK via reflexão em tempo de compilação.
 *
 * <p>Resolve issues #237 (§234: String.join com interface/concreto), #231 (§224:
 * StringBuilder.append com retorno da classe concreta), etc.
 */
public final class JdkReflectionResolver {

    private JdkReflectionResolver() {}

    /** Verifica se o nome interno pertence a um pacote padrão do JDK. */
    public static boolean isJdkClass(String internalName) {
        if (internalName == null) return false;
        return internalName.startsWith("java/")
                || internalName.startsWith("javax/")
                || internalName.startsWith("jdk/");
    }

    /**
     * §557: a classe JDK deste nome interno é uma INTERFACE? O dispatch de
     * chamada instance precisa da flag para emitir {@code invokeinterface}
     * (o JVM exige) em vez de {@code invokevirtual} — que morre com
     * {@code IncompatibleClassChangeError: Found interface ..., but class was
     * expected}. Espelha {@link #isJdkClass}: só nome interno de pacote JDK
     * entra; classe não carregável devolve false (nunca inventa interface).
     */
    static boolean isJdkInterface(String internalName) {
        if (!isJdkClass(internalName)) return false;
        try {
            return Class.forName(internalName.replace('/', '.')).isInterface();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Resolve a assinatura real do método no JDK usando reflexão.
     */
    public static ExternalClasspath.MethodSignature resolveJdkMethod(String ownerInternalName,
                                                              String methodName,
                                                              int argumentCount) {
        return resolveJdkMethodWithArgs(ownerInternalName, methodName, argumentCount, null);
    }

    /**
     * Resolve o método no JDK considerando aridade e, quando fornecidos, os tipos dos argumentos.
     */
    static ExternalClasspath.MethodSignature resolveJdkMethodWithArgs(String ownerInternalName,
                                                                      String methodName,
                                                                      int argumentCount,
                                                                      List<Type> argumentTypes) {
        if (!isJdkClass(ownerInternalName)) return null;
        try {
            Class<?> cls = Class.forName(ownerInternalName.replace('/', '.'));
            Method best = null;
            int bestScore = -1;

            for (Method m : cls.getMethods()) {
                if (!m.getName().equals(methodName)) continue;
                // §500: aridade fixa OU varargs com N >= fixo. O candidato
                // exato ainda vence (penalidade -5 nos varargs).
                int pc = m.getParameterCount();
                boolean varargs = m.isVarArgs();
                if (pc != argumentCount && !(varargs && argumentCount >= pc - 1)) continue;

                int score = 0;
                if (!m.isBridge()) {
                    score += 100;
                }
                if (varargs) {
                    score -= 5;
                }

                if (argumentTypes != null && argumentTypes.size() == argumentCount) {
                    Class<?>[] paramTypes = m.getParameterTypes();
                    int fixed = varargs ? pc - 1 : pc;
                    Class<?> varComp = varargs
                            ? paramTypes[pc - 1].getComponentType() : null;
                    boolean compatible = true;
                    for (int i = 0; i < argumentCount; i++) {
                        Class<?> p = i < fixed ? paramTypes[i] : varComp;
                        Type argT = argumentTypes.get(i);
                        Class<?> a = toJavaClass(argT);
                        if (a != null) {
                            if (p.equals(a)) {
                                score += 20;
                            } else if (p.isAssignableFrom(a)) {
                                score += 10;
                            } else if (isCompatiblePrimitiveBox(p, a)) {
                                score += 8;
                            } else if (p == Object.class) {
                                // #1124 fatia 2: o param apagado de `of(T)` é
                                // Object — primitivo NUNCA passava
                                // (Object.isAssignableFrom(int.class) é false,
                                // primitivo não é Class) e a fábrica nem
                                // resolvia. Object aceita qualquer argumento
                                // (referência direta, primitivo via boxing
                                // JLS) com o MENOR score: uma sobrecarga
                                // específica continua vencendo.
                                score += 2;
                            } else {
                                compatible = false;
                                break;
                            }
                        } else {
                            score += 1;
                        }
                    }
                    if (!compatible) continue;
                }

                if (score > bestScore) {
                    bestScore = score;
                    best = m;
                }
            }

            if (best != null) {
                List<String> paramDescs = new ArrayList<>();
                for (Class<?> p : best.getParameterTypes()) {
                    paramDescs.add(org.objectweb.asm.Type.getDescriptor(p));
                }
                String retDesc = org.objectweb.asm.Type.getDescriptor(best.getReturnType());
                boolean isStatic = Modifier.isStatic(best.getModifiers());
                boolean isInterface = cls.isInterface();
                return new ExternalClasspath.MethodSignature(paramDescs, retDesc, isStatic,
                        isInterface, best.isVarArgs());
            }
        } catch (Throwable t) {
            // Se a classe não puder ser carregada, retorna null
        }
        return null;
    }

    /**
     * §500 slice B: descritor de um campo PUBLIC STATIC do JDK via reflexão
     * (`Integer.MAX_VALUE` → "I", `TimeUnit.SECONDS` → "Ljava/util/concurrent/
     * TimeUnit;"). Só `getFields()` (públicos) + `Modifier.isStatic` — campo de
     * instância pelo NOME da classe não existe como acesso, e a recusa é o
     * diagnóstico (SEM025 no typer), nunca o `getfield "?"` de antes.
     */
    static String resolveStaticJdkFieldType(String ownerInternalName, String fieldName) {
        if (!isJdkClass(ownerInternalName)) return null;
        try {
            Class<?> cls = Class.forName(ownerInternalName.replace('/', '.'));
            for (java.lang.reflect.Field f : cls.getFields()) {
                if (f.getName().equals(fieldName) && Modifier.isStatic(f.getModifiers())) {
                    return org.objectweb.asm.Type.getDescriptor(f.getType());
                }
            }
        } catch (Throwable t) {
            return null;
        }
        return null;
    }

    /**
     * §499: existe método público do JDK com este nome e aridade, ciente de
     * varargs (`String.format(String, Object...)` casa com 1..N argumentos)?
     * É a pergunta que o gate de método estático desconhecido em nome de tipo
     * builtin precisa — `resolveJdkMethod` exige `getParameterCount() == argc`
     * e por isso não vê varargs.
     */
    /**
     * #1032 — o tipo de retorno REAL de um método JDK, para a decisão de
     * statement-level POP. TRUE = devolve valor (um POP); FALSE = void (nenhum
     * POP); null = não resolve (o chamador cai no fallback semântico). Por
     * ARIDADE apenas — os conversores de ArrayType/Unknown perdiam matches.
     */
    static Boolean statementReturn(String ownerInternalName, String methodName, int argumentCount) {
        ExternalClasspath.MethodSignature sig =
                resolveJdkMethod(ownerInternalName, methodName, argumentCount);
        if (sig == null) return null;
        return !sig.returnDescriptor().startsWith("V");
    }

    /**
     * #1032 — a variante usada pelo gate de statement: lê o tipo do receiver
     * DIRETO do slot IR (o tipo semântico é Unknown para um `new` externo não
     * resolvido) e devolve o retorno real do JDK, ou null quando o receiver
     * não é java.* ou o método não resolve.
     */
    static Boolean statementReturnFor(IdentifierExpr rid, CompilerDriver driver,
                                      List<IRLocalVariable> locals, MethodCallExpr mc) {
        IRLocalVariable lv = driver.findLocalVar(rid.name(), locals);
        if (lv == null || !(lv.type() instanceof Type.ClassType rct)
                || !rct.packageName().startsWith("java.")) {
            return null;
        }
        return statementReturn(rct.packageName().replace('.', '/') + "/" + rct.name(),
                mc.methodName(), mc.arguments().size());
    }

    /**
     * #1124 — o retorno GENÉRICO de um método JDK, substituído pelos type
     * arguments do receiver. {@code get()} em {@code Optional<String>} é
     * {@code String} (o argumento), não {@code Object} (a erasure).
     *
     * <p>Pareamento POSICIONAL: a TypeVariable do retorno é procurada pelos
     * parâmetros de tipo da classe DECLARANTE; o índice casa com o type
     * argument do receiver {@code ClassType} no mesmo índice. Devolve null
     * quando o retorno não é uma TypeVariable de nível topo, o receiver não
     * carrega type arguments, ou a aridade de tipo não casa — o chamador
     * mantém o tipo apagado (mudança zero para faces não genéricas). Fadiga
     * de escopo: ParameterizedType aninhado (ex. {@code Stream<T>}) e
     * substituição dirigida por argumento (fábricas {@code of(T)}) ficam
     * para as fatias seguintes — nunca inventar tipo sem pareamento provado.
     */
    public static Type genericReturnFor(String ownerInternalName, String methodName,
                                 int argumentCount, List<Type> receiverTypeArgs) {
        if (!isJdkClass(ownerInternalName) || receiverTypeArgs == null
                || receiverTypeArgs.isEmpty()) {
            return null;
        }
        try {
            Class<?> cls = Class.forName(ownerInternalName.replace('/', '.'));
            TypeVariable<?>[] ownerParams = cls.getTypeParameters();
            if (ownerParams.length != receiverTypeArgs.size()) return null;
            for (Method m : cls.getMethods()) {
                if (!m.getName().equals(methodName)) continue;
                if (m.getParameterCount() != argumentCount) continue;
                if (m.isBridge()) continue;
                java.lang.reflect.Type ret = m.getGenericReturnType();
                if (!(ret instanceof TypeVariable<?> tv)) return null;
                Class<?> decl = m.getDeclaringClass();
                TypeVariable<?>[] declParams = decl.getTypeParameters();
                for (int i = 0; i < declParams.length; i++) {
                    if (declParams[i].getName().equals(tv.getName())
                            && i < receiverTypeArgs.size()) {
                        return receiverTypeArgs.get(i);
                    }
                }
                return null;
            }
        } catch (Throwable t) {
            return null;
        }
        return null;
    }

    /**
     * #1124 fatia 2 — retorno de FÁBRICA estática genérica substituído pelo
     * ARGUMENTO: {@code Optional.of("hi")} é {@code Optional<String>}.
     *
     * <p>Forma reconhecida (tudo ou nada — nunca inventar tipo): o retorno
     * genérico é um ParameterizedType cujo raw é a própria classe dona, com
     * EXATAMENTE UMA TypeVariable entre os argumentos de tipo; essa
     * TypeVariable aparece como tipo de nível topo de EXATAMENTE UM parâmetro;
     * e o argumento nessa posição é um ClassType de REFERÊNCIA com type
     * arguments VAZIOS (String, classes simples — primitivo, Nullable, array e
     * genérico profundo mantêm o retorno CRU: o box/desbox e o cast aninhado
     * são faces próprias, não desta fatia). Qualquer outra forma devolve
     * null — o chamador fica com a erasure (mudança zero).
     */
    public static Type genericReturnFromArgs(String ownerInternalName, String methodName,
                                             int argumentCount, List<Type> argumentTypes,
                                             Type receiverType) {
        if (!isJdkClass(ownerInternalName) || argumentTypes == null
                || argumentTypes.size() != argumentCount
                || !(receiverType instanceof Type.ClassType rc)) {
            return null;
        }
        try {
            Class<?> cls = Class.forName(ownerInternalName.replace('/', '.'));
            for (Method m : cls.getMethods()) {
                if (!m.getName().equals(methodName)) continue;
                if (m.getParameterCount() != argumentCount) continue;
                if (m.isBridge()) continue;
                java.lang.reflect.Type ret = m.getGenericReturnType();
                if (!(ret instanceof java.lang.reflect.ParameterizedType pt)
                        || pt.getRawType() != cls) {
                    return null;
                }
                java.lang.reflect.Type[] typeArgs = pt.getActualTypeArguments();
                int tvPos = -1;
                for (int i = 0; i < typeArgs.length; i++) {
                    if (typeArgs[i] instanceof TypeVariable<?> tv) {
                        if (tvPos >= 0) return null;
                        tvPos = i;
                    } else if (!(typeArgs[i] instanceof Class<?>)) {
                        return null;
                    }
                }
                if (tvPos < 0) return null;
                String tvName = ((TypeVariable<?>) typeArgs[tvPos]).getName();
                int paramPos = -1;
                java.lang.reflect.Type[] genParams = m.getGenericParameterTypes();
                for (int i = 0; i < genParams.length; i++) {
                    if (genParams[i] instanceof TypeVariable<?> tv
                            && tv.getName().equals(tvName)) {
                        if (paramPos >= 0) return null;
                        paramPos = i;
                    }
                }
                if (paramPos < 0) return null;
                Type arg = argumentTypes.get(paramPos);
                if (!(arg instanceof Type.ClassType act) || !act.typeArguments().isEmpty()) {
                    return null;
                }
                return new Type.ClassType(rc.packageName(), rc.name(), List.of(arg));
            }
        } catch (Throwable t) {
            return null;
        }
        return null;
    }

    static boolean hasJdkMethod(String ownerInternalName, String methodName, int argumentCount) {
        if (!isJdkClass(ownerInternalName)) return false;
        try {
            Class<?> cls = Class.forName(ownerInternalName.replace('/', '.'));
            for (Method m : cls.getMethods()) {
                if (!m.getName().equals(methodName) || !Modifier.isStatic(m.getModifiers())) continue;
                int pc = m.getParameterCount();
                if (pc == argumentCount) return true;
                if (m.isVarArgs() && argumentCount >= pc - 1) return true;
            }
        } catch (Throwable t) {
            return false;
        }
        return false;
    }

    /**
     * §393 (#568): construtor PUBLICO do JDK com a aridade dada via reflexao
     * (`getConstructors` = so publicos; classe abstrata nao entrega
     * construtor). Retorno sempre "V" — construtor nunca tem valor.
     */
    static ExternalClasspath.MethodSignature resolvePublicJdkConstructor(String ownerInternalName,
                                                                         int argumentCount) {
        if (!isJdkClass(ownerInternalName)) return null;
        try {
            Class<?> cls = Class.forName(ownerInternalName.replace('/', '.'));
            for (java.lang.reflect.Constructor<?> c : cls.getConstructors()) {
                if (c.getParameterCount() != argumentCount) continue;
                List<String> paramDescs = new ArrayList<>();
                for (Class<?> p : c.getParameterTypes()) {
                    paramDescs.add(org.objectweb.asm.Type.getDescriptor(p));
                }
                return new ExternalClasspath.MethodSignature(paramDescs, "V", false,
                        cls.isInterface(), c.isVarArgs());
            }
        } catch (Throwable t) {
            // Classe nao carregavel: sem construtor (mesma politica do resolveJdkMethod)
        }
        return null;
    }

    private static Class<?> toJavaClass(Type type) {
        if (type == null) return null;
        if (type instanceof Type.PrimitiveType pt) {
            return switch (Type.canonicalPrimitiveName(pt.name())) {
                case "int" -> int.class;
                case "long" -> long.class;
                case "bool", "boolean" -> boolean.class;
                case "double" -> double.class;
                case "float" -> float.class;
                case "char" -> char.class;
                case "byte" -> byte.class;
                case "short" -> short.class;
                case "void" -> void.class;
                default -> null;
            };
        }
        if (type instanceof Type.ClassType ct) {
            if ("kof".equals(ct.packageName()) || ct.packageName().isEmpty()) {
                return switch (ct.name()) {
                    case "String" -> String.class;
                    case "List" -> java.util.ArrayList.class;
                    case "Map" -> java.util.HashMap.class;
                    case "Set" -> java.util.HashSet.class;
                    case "Int", "Integer" -> Integer.class;
                    case "Long" -> Long.class;
                    case "Bool", "Boolean" -> Boolean.class;
                    case "Double" -> Double.class;
                    case "Float" -> Float.class;
                    default -> null;
                };
            }
            try {
                String className = ct.packageName() + "." + ct.name();
                return Class.forName(className);
            } catch (Throwable e) {
                return null;
            }
        }
        return null;
    }

    private static boolean isCompatiblePrimitiveBox(Class<?> param, Class<?> arg) {
        if (param.isPrimitive()) {
            return (param == int.class && arg == Integer.class)
                    || (param == long.class && arg == Long.class)
                    || (param == boolean.class && arg == Boolean.class)
                    || (param == double.class && arg == Double.class)
                    || (param == float.class && arg == Float.class)
                    || (param == char.class && arg == Character.class)
                    || (param == byte.class && arg == Byte.class)
                    || (param == short.class && arg == Short.class);
        }
        if (arg.isPrimitive()) {
            return (arg == int.class && param == Integer.class)
                    || (arg == long.class && param == Long.class)
                    || (arg == boolean.class && param == Boolean.class)
                    || (arg == double.class && param == Double.class)
                    || (arg == float.class && param == Float.class)
                    || (arg == char.class && param == Character.class)
                    || (arg == byte.class && param == Byte.class)
                    || (arg == short.class && param == Short.class);
        }
        return false;
    }
}
