package dev.kof.compiler;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolução de assinaturas de métodos do JDK via reflexão em tempo de compilação.
 *
 * <p>Resolve issues #237 (§234: String.join com interface/concreto), #231 (§224:
 * StringBuilder.append com retorno da classe concreta), etc.
 */
final class JdkReflectionResolver {

    private JdkReflectionResolver() {}

    /** Verifica se o nome interno pertence a um pacote padrão do JDK. */
    static boolean isJdkClass(String internalName) {
        if (internalName == null) return false;
        return internalName.startsWith("java/")
                || internalName.startsWith("javax/")
                || internalName.startsWith("jdk/");
    }

    /**
     * Resolve a assinatura real do método no JDK usando reflexão.
     */
    static ExternalClasspath.MethodSignature resolveJdkMethod(String ownerInternalName,
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
    static boolean hasJdkMethod(String ownerInternalName, String methodName, int argumentCount) {
        if (!isJdkClass(ownerInternalName)) return false;
        try {
            Class<?> cls = Class.forName(ownerInternalName.replace('/', '.'));
            for (Method m : cls.getMethods()) {
                if (!m.getName().equals(methodName)) continue;
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
