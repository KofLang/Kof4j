package dev.kof.compiler;

import dev.kof.compiler.jvm.JvmTypeMapper;

import java.util.List;

/**
 * StructuralAssignability — a família de atribuição ESTRUTURAL do
 * TypeChecker (type-pairs sem hierarquia nominal), extraída no MESMO commit
 * que a face de boxing JLS 5.3 da #1089 (o arquivo cruzou 588→620 no gate
 * ≤600): primitivos/nullability/arrays/FunctionType/wrapper+Number — o
 * caminho nominal (analyzer-aware) permanece no TypeChecker.isAssignable(sa,…)
 * e delega para cá quando não há candidato a referência.
 */
final class StructuralAssignability {

    private StructuralAssignability() {}

    static boolean isAssignable(Type from, Type to) {
        if (from == null || to == null) return true;
        if (Type.isUnknown(from) || Type.isUnknown(to)) return true;
        if (from instanceof Type.TypeVariable || to instanceof Type.TypeVariable) return true;
        if (from instanceof Type.NullableType fn) {
            if (to instanceof Type.NullableType tn) return isAssignable(fn.inner(), tn.inner());
            // #1089: Nullable(primitivo) chega BOXADO no runtime (D-NULL-INTENT)
            // — passar onde o wrapper do inner (ou Number/Object) é esperado é
            // a MESMA conversão do primitivo cru, e null é valor legal no Java.
            // A emissão não re-boxa (o valor já é a referência).
            if (to instanceof Type.ClassType ct && "java.lang".equals(ct.packageName())
                    && fn.inner() instanceof Type.PrimitiveType fp) {
                Type boxed = TypeMetrics.boxedTypeFor(fp);
                if (boxed instanceof Type.ClassType bc && bc.name().equals(ct.name())) return true;
                if ("Object".equals(ct.name())) return true;
                if ("Number".equals(ct.name())) {
                    String n = Type.canonicalPrimitiveName(fp.name());
                    if (!("bool".equals(n) || "char".equals(n))) return true;
                }
            }
            return false;
        }
        if (to instanceof Type.NullableType tn) {
            // from nunca é NullableType aqui: o branch acima sempre retorna
            // nesse caso (CodeQL contradictory-type-checks — ramo morto removido).
            return isAssignable(from, tn.inner());
        }
        // §372 (lane docs): o gate de store de campo (§368) expôs que isAssignable nao
        // desembolava ArrayType — o MESMO TypeMetrics.isPrimitiveType/§270
        // padrao. `T[] v = <T[]>` com os dois lados vistos por vias de
        // representacao diferentes (ClassType[T] do campo apagado vs
        // TypeVariable[T] do escopo generico) caia em equals()=false e o
        // gate rejeitava ouro verde do §270 (GenericFieldArrayEraseE2ETest).
        // Recurso ao componente preserva o §270 (nome do parametrico decide;
        // int[]→T[] continua caindo no SEM098 de runtime do writer, que e o
        // contrato antigo do golden) e mantem as recusas reais (String[]→
        // Char[] etc.) porque a recursao usa a MESMA regra no componente.
        if (from instanceof Type.ArrayType fa && to instanceof Type.ArrayType ta) {
            return isAssignable(fa.componentType(), ta.componentType());
        }
        if (from.equals(to)) return true;
        // §288 (#396): função declarada com type-param do escopo (`(T) -> T`
        // em `class Box<T>`) apaga para Object nos componentes — `(Int) -> Int`
        // CONFORMA-se a ela (o invoke da interface sintética recebe/retorna
        // Object; o call site faz a conversão). Comparação por componente,
        // invariante: aridade e cada par param/retorno seguem a MESMA regra
        // (o TypeVariable no topo já aceita qualquer coisa; `(Int)->Int` vs
        // `(String)->String` continua rejeitado, como antes).
        if (from instanceof Type.FunctionType ff && to instanceof Type.FunctionType tf) {
            if (ff.parameterTypes().size() != tf.parameterTypes().size()) return false;
            for (int i = 0; i < ff.parameterTypes().size(); i++) {
                if (!isAssignable(ff.parameterTypes().get(i), tf.parameterTypes().get(i))) return false;
            }
            return isAssignable(ff.returnType(), tf.returnType());
        }
        if (from instanceof Type.PrimitiveType fp && to instanceof Type.PrimitiveType tp) {
            if ("bool".equals(Type.canonicalPrimitiveName(fp.name())) || "bool".equals(Type.canonicalPrimitiveName(tp.name()))) {
                return "bool".equals(Type.canonicalPrimitiveName(fp.name())) && "bool".equals(Type.canonicalPrimitiveName(tp.name()));
            }
            // double → float: o lowering emite D2F; sem isso literais
            // decimais (1000.0) não atribuem a campos Float
            if ("double".equals(fp.name()) && "float".equals(tp.name())) return true;
            int fw = primitiveWidth(fp);
            int tw = primitiveWidth(tp);
            return fw <= tw;
        }
        if (from instanceof Type.FunctionType && to instanceof Type.ClassType) {
            // lambda → interface funcional externa (SAM conversion): a
            // compatibilidade real (aridade/tipos) é validada na emissão
            return true;
        }
        if (from instanceof Type.PrimitiveType && to instanceof Type.ClassType ct
                && "Object".equals(ct.name()) && "java.lang".equals(ct.packageName())) {
            // bug 15: primitivo → Object (auto-boxing no emit) — `Object n = 42`
            return true;
        }
        if (from instanceof Type.PrimitiveType fp && to instanceof Type.ClassType ct
                && "java.lang".equals(ct.packageName())) {
            // #1089: JLS 5.3 (method invocation conversion) — boxing seguido de
            // widening reference na fronteira JDK: Bool→Boolean, Int→Integer,
            // Long→Long, Double→Double, Float→Float, Char→Character,
            // Byte→Byte, Short→Short; e Int/Long/Float/Double/Short/Byte→
            // Number (Character e Boolean NÃO são Number). O emit boxa o
            // primitivo cru no ponto de chamada (emitErasureBox) e o Nullable
            // já chega boxado (D-NULL-INTENT — nunca re-boxa).
            Type boxed = TypeMetrics.boxedTypeFor(fp);
            if (boxed instanceof Type.ClassType bc && bc.name().equals(ct.name())) {
                return true;
            }
            if ("Number".equals(ct.name())) {
                String n = Type.canonicalPrimitiveName(fp.name());
                return !("bool".equals(n) || "char".equals(n));
            }
        }
        if (from instanceof Type.PrimitiveType fp
                && "double".equals(fp.name())
                && to instanceof Type.PrimitiveType tp
                && "float".equals(tp.name())) {
            // double → float: o lowering emite D2F; sem isso literais
            // decimais (1000.0) não atribuem a campos Float
            return true;
        }
        if (to instanceof Type.ClassType tc) {
            if (from instanceof Type.ClassType fc) {
                // D-MAINT-BATCH-0510/TY1 (#753 residual): o fallback
                // "todo ClassType atribui a todo ClassType" aceitava
                // `process.spawn(...)` (um handle `java.lang.Long`) numa
                // função declarada `Handle` (que apaga para
                // `CompletableFuture`) — `kof check` limpo e a classe morria
                // no load com `VerifyError: Bad return type`. Os builtins com
                // ERASÃO JVM conhecida são comparados pelo runtime REAL: o
                // descritor de `JvmTypeMapper.classDescriptor` é carregado e
                // a hierarquia do JDK decide. Só RECUSA quando as duas classes
                // carregam e não há relação; qualquer lado sintético
                // (kof.ui.*, kof.io.*, ...) ou externo não-carregável
                // permanece conservador (nunca quebrar interop legítima, R6).
                Boolean runtime = runtimeClassRelation(fc, tc);
                if (runtime != null) return runtime;
            }
            return from instanceof Type.ClassType;
        }
        return false;
    }

    /**
     * D-MAINT-BATCH-0510/TY1 (#753 residual): relação real de duas classes
     * builtin pelo seu descritor de ERASÃO JVM (`JvmTypeMapper.classDescriptor`
     * — o mesmo chokepoint que resolve `kof.List`→`ArrayList`,
     * `kof.concurrent.Handle`→`CompletableFuture`, `kof.Secret`→
     * `KofRuntime$Secret`). Devolve {@code null} quando a decisão não é
     * segura: qualquer descritor não-carregável (tipo sintético/fantasma),
     * primitivo/array ou erro de link. Assim a recusa do TY1 nunca inventa
     * hierarquia para os tipos que o compilador apenas emite por convenção.
     */
    private static Boolean runtimeClassRelation(Type.ClassType from, Type.ClassType to) {
        Class<?> f = loadRuntimeClass(from);
        Class<?> t = loadRuntimeClass(to);
        if (f != null && t != null) return t.isAssignableFrom(f);
        // §597 residual (D-MAINT-BATCH-0510/TY1): os builtins que apagam para
        // classes ANINHADAS de `KofRuntime` (ProcessResult/Buffer/Secret/
        // KeyHandle/InteropError) NÃO estão no classpath do compilador — o
        // `KofRuntime` é gerado por-output e compilado com `javac` só na
        // emissão. `Class.forName` devolve null e a relação ficava
        // conservadora: `Result r = "x"` e `Long -> Result` compilavam limpo e
        // morriam no load com `NoClassDefFoundError`. Resolvemos a superclasse
        // real da classe gerada pelo registro canônico
        // (`JvmTypeMapper.generatedRuntimeSuperInternalName`); as cinco classes
        // só estendem Object/RuntimeException e não implementam interface, então
        // a cadeia de superclasse decide a hierarquia por completo.
        String fi = erasureInternalName(from);
        String ti = erasureInternalName(to);
        String fSuper = JvmTypeMapper.generatedRuntimeSuperInternalName(fi);
        String tSuper = JvmTypeMapper.generatedRuntimeSuperInternalName(ti);
        if (fSuper == null && tSuper == null) return null;
        if (fi != null && fi.equals(ti)) return true;
        // `from -> to`: `to` gerado só pode ser atribuído de si mesmo (acima);
        // nenhuma outra classe gerada o estende. `from` gerado conforma-se a
        // `to` quando a superclasse real de `from` conforma.
        if (tSuper != null) return false;
        Class<?> fromSuper = loadInternalName(fSuper);
        if (fromSuper == null) return null;
        if (t != null) return t.isAssignableFrom(fromSuper);
        return null;
    }

    /** Erasure internal name (`a/b/C`) de um ClassType, ou null. */
    private static String erasureInternalName(Type.ClassType ct) {
        try {
            return JvmTypeMapper.toInternalName(ct.packageName(), ct.name());
        } catch (Throwable t) {
            return null;
        }
    }

    private static Class<?> loadRuntimeClass(Type.ClassType ct) {
        String descriptor;
        try {
            descriptor = JvmTypeMapper.classDescriptor(ct);
        } catch (Throwable t) {
            return null;
        }
        if (descriptor == null || descriptor.length() < 3
                || descriptor.charAt(0) != 'L' || descriptor.charAt(descriptor.length() - 1) != ';') {
            return null;
        }
        return loadInternalName(descriptor.substring(1, descriptor.length() - 1));
    }

    private static Class<?> loadInternalName(String internal) {
        if (internal == null) return null;
        try {
            return Class.forName(internal.replace('/', '.'), false,
                    TypeChecker.class.getClassLoader());
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * SG-009: subtipagem NOMINAL — `A a = <não-relacionado>` é erro
     * compile-time (antes só o checkcast do emit salvava, em runtime).
     * Caminha superClass/interfaces via BFS (MemberResolver usa o mesmo
     * padrão). Conservador (true) quando a hierarquia é desconhecida:
     * tipo externo (imports Android/JDK), builtin (String/List/Map — o
     * BuiltinTypes resolve), ou classe não declarada no módulo — restringir
     * esses quebraria interop legítima (regra 6: nunca quebrar o que funciona).
     */
    /** Nome RAW de um ClassType (pkg.Simple), ignorando type-args. §270. */
    static boolean isBuiltinClassType(Type.ClassType ct) {
        String pkg = ct.packageName();
        return "kof".equals(pkg) || "java".equals(pkg)
                || "java.lang".equals(pkg) || "java.util".equals(pkg)
                || "kof.concurrent".equals(pkg) || "dev.kof".equals(pkg)
                || pkg.startsWith("java.") || pkg.startsWith("dev.kof.")
                || pkg.startsWith("kof.");
    }

    static int primitiveWidth(Type.PrimitiveType pt) {
        return switch (pt.name()) {
            case "bool", "Bool" -> 0;
            case "char", "Char" -> 1;
            case "int", "Int", "byte", "short" -> 2;
            case "long", "Long" -> 3;
            case "float", "Float" -> 4;
            case "double", "Double" -> 5;
            default -> 2;
        };
    }
}
