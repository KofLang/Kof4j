package dev.kof.compiler;

import java.lang.reflect.Method;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                || internalName.startsWith("jdk/")
                || (internalName.startsWith("com/sun/") && exportedJdkApi(internalName));
    }

    private static final java.util.Map<String, Boolean> EXPORTED_COM_SUN = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * The supported {@code com.sun} APIs ({@code com.sun.management} in
     * jdk.management, {@code com.sun.net.httpserver} in jdk.httpserver) are
     * exported by a named {@code jdk.*} module; the internal {@code com.sun}
     * packages are not, and stay out.
     */
    /** An import of an exported {@code com.sun} JDK API: a class, or a package for a wildcard. */
    static boolean isExportedComSunImport(String dotted) {
        if (dotted == null || !dotted.startsWith("com.sun.")) return false;
        String name = dotted.endsWith(".*") ? dotted.substring(0, dotted.length() - 2) : dotted;
        if (exportedJdkApi(name.replace('.', '/'))) return true;
        return ModuleLayer.boot().modules().stream().anyMatch(m -> m.getName().startsWith("jdk.")
                && m.getPackages().contains(name) && m.isExported(name));
    }

    private static boolean exportedJdkApi(String internalName) {
        return EXPORTED_COM_SUN.computeIfAbsent(internalName, n -> {
            try {
                Class<?> c = Class.forName(n.replace('/', '.'), false, ClassLoader.getPlatformClassLoader());
                Module m = c.getModule();
                return m.isNamed() && m.getName().startsWith("jdk.") && m.isExported(c.getPackageName());
            } catch (Throwable t) {
                return false;
            }
        });
    }

    /**
     * §557: a classe JDK deste nome interno é uma INTERFACE? O dispatch de
     * chamada instance precisa da flag para emitir {@code invokeinterface}
     * (o JVM exige) em vez de {@code invokevirtual} — que morre com
     * {@code IncompatibleClassChangeError: Found interface ..., but class was
     * expected}. Espelha {@link #isJdkClass}: só nome interno de pacote JDK
     * entra; classe não carregável devolve false (nunca inventa interface).
     */
    public static boolean isJdkInterface(String internalName) {
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
    public static ExternalClasspath.MethodSignature resolveJdkMethodWithArgs(String ownerInternalName,
                                                                              String methodName,
                                                                              int argumentCount,
                                                                              List<Type> argumentTypes) {
        if (!isJdkClass(ownerInternalName)) return null;
        try {
            Class<?> cls = Class.forName(ownerInternalName.replace('/', '.'));
            return reflectResolve(cls, methodName, argumentCount, argumentTypes);
        } catch (Throwable t) {
            // Se a classe não puder ser carregada, retorna null
        }
        return null;
    }

    /**
     * #1093 fatia 2 — resolução POR REFLEXÃO sobre uma classe QUALQUER
     * (JDK ou do classpath externo carregado sem init). getMethods() caminha
     * a hierarquia INTEIRA (superclasses E interfaces) e os descritores vêm
     * da classe carregada — o método herdado de interface genérica
     * (`S3Builder.region` declarado como default em `Builder<B, T>`)
     * aparece aqui com o descritor APAGADO real, que o byte-parse de
     * findDeclared não vê (ele só caminha superclasses de CLASSE). O score
     * é o mesmo do caminho JDK (exato > atribuível > prim-box > Object;
     * bridge prefere não-bridge; varargs com penalidade).
     */
    public static ExternalClasspath.MethodSignature reflectResolve(Class<?> cls,
                                                                   String methodName,
                                                                   int argumentCount,
                                                                   List<Type> argumentTypes) {
        try {
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
                boolean isInterface = best.getDeclaringClass().isInterface();
                return new ExternalClasspath.MethodSignature(paramDescs, retDesc, isStatic,
                        isInterface, best.isVarArgs());
            }
        } catch (Throwable t) {
            // reflexão indisponível para esta classe — o caller cai no parse de bytes
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
    /** A CLASSE do owner: JDK primeiro, depois o classpath externo
     *  carregável (#1093 fatia 2, sem inicialização). */
    private static Class<?> classFor(String ownerInternalName, ExternalClasspath cp) {
        if (isJdkClass(ownerInternalName)) {
            try {
                return Class.forName(ownerInternalName.replace('/', '.'), false,
                        JdkReflectionResolver.class.getClassLoader());
            } catch (Throwable ignored) {
            }
        }
        return cp != null ? cp.loadClass(ownerInternalName) : null;
    }

    /**
     * #1093 fatia 2 — a parameterização da classe-alvo NA hierarquia do
     * receiver: `S3Builder implements Builder<S3Builder, S3>` → alvo
     * Builder → `[S3Builder, S3]`. Caminha superclasses + interfaces
     * genéricas substituindo as TypeVariables de cada nível pelos bindings
     * acumulados (um receiver genérico próprio resolve primeiro). Null
     * quando a parameterização é irresolvível.
     */
    private static List<Type> hierarchyArgsFor(Class<?> start, Class<?> target) {
        record Frame(Class<?> cls, Map<String, Type> bindings) {}
        java.util.ArrayDeque<Frame> work = new java.util.ArrayDeque<>();
        work.add(new Frame(start, Map.of()));
        int hops = 0;
        while (!work.isEmpty() && hops++ < 32) {
            Frame f = work.poll();
            java.util.List<java.lang.reflect.Type> supers = new ArrayList<>();
            if (f.cls.getGenericSuperclass() != null) supers.add(f.cls.getGenericSuperclass());
            supers.addAll(java.util.Arrays.asList(f.cls.getGenericInterfaces()));
            for (java.lang.reflect.Type st : supers) {
                if (!(st instanceof java.lang.reflect.ParameterizedType pt)) continue;
                if (!(pt.getRawType() instanceof Class<?> raw)) continue;
                Map<String, Type> nb = new HashMap<>(f.bindings);
                TypeVariable<?>[] rps = raw.getTypeParameters();
                java.lang.reflect.Type[] act = pt.getActualTypeArguments();
                boolean resolvable = true;
                List<Type> resolved = new ArrayList<>();
                for (int i = 0; i < rps.length && i < act.length; i++) {
                    Type t = null;
                    if (act[i] instanceof Class<?> c) {
                        t = ExternalClasspath.typeFromDescriptor(
                                org.objectweb.asm.Type.getDescriptor(c));
                    } else if (act[i] instanceof TypeVariable<?> tv) {
                        t = f.bindings.get(tv.getName());
                    }
                    if (t == null) { resolvable = false; break; }
                    nb.put(rps[i].getName(), t);
                    resolved.add(t);
                }
                if (!resolvable) continue;
                if (raw == target) return resolved;
                work.add(new Frame(raw, nb));
            }
        }
        return null;
    }

    public static Type genericReturnFor(String ownerInternalName, String methodName,
                                 int argumentCount, List<Type> receiverTypeArgs,
                                 ExternalClasspath cp) {
        try {
            Class<?> cls = classFor(ownerInternalName, cp);
            if (cls == null) return null;
            for (Method m : cls.getMethods()) {
                if (!m.getName().equals(methodName)) continue;
                if (m.getParameterCount() != argumentCount) continue;
                if (m.isBridge()) continue;
                java.lang.reflect.Type ret = m.getGenericReturnType();
                if (ret instanceof TypeVariable<?> tv) {
                    // fatia 1: retorno = a TypeVariable do dono — pareia com o
                    // type argument do receiver NO MESMO índice. #1093 fatia 2:
                    // o receiver SEM type args próprios (o builder concreto)
                    // resolve pela parameterização da classe DECLARANTE NA
                    // HIERARQUIA dele (`S3Builder implements Builder<S3Builder,
                    // S3>` → B:=S3Builder, T:=S3).
                    List<Type> effective = receiverTypeArgs;
                    if (effective == null || effective.isEmpty()
                            || cls.getTypeParameters().length != effective.size()) {
                        effective = hierarchyArgsFor(cls, m.getDeclaringClass());
                    }
                    if (effective == null || effective.isEmpty()) return null;
                    Class<?> decl = m.getDeclaringClass();
                    TypeVariable<?>[] declParams = decl.getTypeParameters();
                    for (int i = 0; i < declParams.length; i++) {
                        if (declParams[i].getName().equals(tv.getName())
                                && i < effective.size()) {
                            return effective.get(i);
                        }
                    }
                    return null;
                }
                if (ret instanceof java.lang.reflect.ParameterizedType pt) {
                    // #1093 fatia 1: retorno genérico CONCRETO do receiver
                    // (`Path.iterator()` declara `Iterator<Path>`) — a erasure
                    // cru devolvia Iterator sem args e o `next()` herdado
                    // (Iterator<E>.next → E) perdia o par. Args: Class faces
                    // viram tipos via descritor; TypeVariables são
                    // substituídas posicionalmente pelos type args do
                    // PRÓPRIO receiver (pareando com a classe DECLARANTE do
                    // método). QUALQUER arg irresolvível (wildcard, array
                    // genérico, type-var sem par no receiver) → null: o
                    // retorno fica CRU (mudança zero, nunca tipo inventado).
                    if (!(pt.getRawType() instanceof Class<?> raw)) return null;
                    List<Type> args = new ArrayList<>();
                    Class<?> decl = m.getDeclaringClass();
                    TypeVariable<?>[] declParams = decl.getTypeParameters();
                    for (java.lang.reflect.Type ta : pt.getActualTypeArguments()) {
                        if (ta instanceof Class<?> c) {
                            args.add(ExternalClasspath.typeFromDescriptor(
                                    org.objectweb.asm.Type.getDescriptor(c)));
                        } else if (ta instanceof TypeVariable<?> tv) {
                            Integer pos = null;
                            for (int i = 0; i < declParams.length; i++) {
                                if (declParams[i].getName().equals(tv.getName())) { pos = i; break; }
                            }
                            if (pos == null || receiverTypeArgs == null
                                    || pos >= receiverTypeArgs.size()) {
                                return null;
                            }
                            args.add(receiverTypeArgs.get(pos));
                        } else {
                            return null;
                        }
                    }
                    Type rawT = ExternalClasspath.typeFromDescriptor(
                            "L" + org.objectweb.asm.Type.getInternalName(raw) + ";");
                    if (args.isEmpty()) return rawT;
                    if (!(rawT instanceof Type.ClassType rct)) return null;
                    return new Type.ClassType(rct.packageName(), rct.name(), args);
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
                                             Type receiverType, ExternalClasspath cp) {
        if (argumentTypes == null
                || argumentTypes.size() != argumentCount
                || !(receiverType instanceof Type.ClassType rc)) {
            return null;
        }
        try {
            Class<?> cls = classFor(ownerInternalName, cp);
            if (cls == null) return null;
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
                // #1124 fatia 3: um PRIMITIVO é aceito como type argument — a
                // visão semântica Kof é Optional<Int> (os descritores apagam
                // de qualquer forma; o box/unbox é emitido no ponto de
                // chamada). Nullable, array e genérico profundo seguem
                // rejeitados (as faces box-cast e cast-aninhado são fatias
                // próprias).
                if (arg instanceof Type.PrimitiveType) {
                    return new Type.ClassType(rc.packageName(), rc.name(), List.of(arg));
                }
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
