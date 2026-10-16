package dev.kof.compiler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * §713 — inferência de type-arguments de uma chamada de construtor sem args
 * explícitos (`Box(n)`). Sem isto o receptor ficava com o `ClassType` de args
 * VAZIOS, o `T` do campo/método não resolvia e o tipo efetivo permanecia o
 * `Object` APAGADO — no JVM isso é `VerifyError: Bad type on operand stack`
 * (`Object` não é atribuível a `int`) e nos alvos nativos `ClassCastException`.
 *
 * <p>O unificador liga os NOMES dos type-params declarados da classe contra os
 * tipos DECLARADOS dos parâmetros do construtor e os tipos INFERIDOS dos
 * argumentos. É conservador: um type-param que não casa (aridade diferente,
 * tipo primitivo vs variável, argumento `Unknown`) NÃO é ligado — o chamador
 * então mantém o tipo cru (comportamento atual, zero-regressão).
 */
final class GenericTypeArgInference {

    private GenericTypeArgInference() {}

    /**
     * Type-arguments inferidos para {@code new C(args)}, na ordem dos
     * type-params de {@code C}, ou {@code null} quando nada pôde ser inferido
     * (o chamador mantém os args vazios).
     *
     * @param typeParamNames nomes dos type-params da classe (ordem)
     * @param paramTypes     tipos DECLARADOS dos parâmetros do construtor
     * @param argTypes       tipos inferidos dos argumentos
     */
    static List<Type> infer(List<String> typeParamNames, List<Type> paramTypes,
                            List<Type> argTypes) {
        if (typeParamNames == null || typeParamNames.isEmpty()) return null;
        if (paramTypes == null || argTypes == null || paramTypes.size() != argTypes.size()) return null;
        Map<String, Type> bindings = new LinkedHashMap<>();
        for (int i = 0; i < paramTypes.size(); i++) {
            unify(paramTypes.get(i), argTypes.get(i), typeParamNames, bindings);
        }
        if (bindings.isEmpty()) return null;
        List<Type> out = new ArrayList<>(typeParamNames.size());
        for (String tp : typeParamNames) {
            Type b = bindings.get(tp);
            // Um type-param não ligado vira Object (erasure), NUNCA Unknown —
            // Unknown degradaria o receptor inteiro e perderia os acessos já
            // resolvidos dos outros type-params.
            out.add(b != null ? b : new Type.ClassType("java.lang", "Object", List.of()));
        }
        return out;
    }

    /**
     * Liga os type-params de {@code declared} contra {@code actual}, de forma
     * RECURSIVA (cobre `List<T>` contra `List<Int>`). O primeiro binding vence
     * (não unifica ocorrências repetidas — conservador e determinístico).
     */
    private static void unify(Type declared, Type actual, List<String> typeParamNames,
                              Map<String, Type> bindings) {
        if (declared == null || actual == null) return;
        if (declared instanceof Type.TypeVariable tv) {
            if (isTypeParam(tv.name(), typeParamNames) && !bindings.containsKey(tv.name())
                    && isInferable(actual)) {
                bindings.put(tv.name(), actual);
            }
            return;
        }
        if (declared instanceof Type.ClassType dc && actual instanceof Type.ClassType ac
                && dc.name().equals(ac.name())
                && dc.typeArguments().size() == ac.typeArguments().size()) {
            for (int i = 0; i < dc.typeArguments().size(); i++) {
                unify(dc.typeArguments().get(i), ac.typeArguments().get(i), typeParamNames, bindings);
            }
            return;
        }
        if (declared instanceof Type.ArrayType da && actual instanceof Type.ArrayType aa) {
            unify(da.componentType(), aa.componentType(), typeParamNames, bindings);
            return;
        }
        if (declared instanceof Type.NullableType dn && actual instanceof Type.NullableType an) {
            unify(dn.inner(), an.inner(), typeParamNames, bindings);
        }
    }

    private static boolean isTypeParam(String name, List<String> typeParamNames) {
        return typeParamNames.contains(name);
    }

    /**
     * Um tipo que pode virar um type-argument: descarta `Unknown` (não
     * informativo) e variáveis de tipo NÃO ligadas (que viriam de outro
     * escopo genérico).
     */
    private static boolean isInferable(Type t) {
        if (t == null) return false;
        if (t instanceof Type.UnknownType) return false;
        if (t instanceof Type.TypeVariable) return false;
        return true;
    }
}
