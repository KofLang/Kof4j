package dev.kof.compiler;

import java.util.List;

/**
 * #386 (fatia 2) — extras do containsValue no lowering compartilhado: o box
 * do argumento no nativo (família Int/Long do §284 e, no slot de valor
 * Object, TODO primitivo — §352) e o tag de comparação por valor
 * ({@link CollectionMethodGates#valueCmpTag}). Vive fora do
 * CollectionCallLowerer pelo gate de 500 linhas e pela responsabilidade
 * própria; o backend JVM faz POP do tag (java.util usa equals), Script/JS
 * ignoram, nativo usa no scan — Object usa o tag 6 DINÂMICO (o runtime
 * classifica com kof_value_kind).
 */
final class CollectionValueOps {

    private CollectionValueOps() {}

    /**
     * Emite [box(arg0)?][tag] antes da call; o caller adiciona o
     * {@code INT} do tag a argTypes.
     */
    static void emitContainsValueExtras(CompilerDriver driver, MethodCallExpr mc,
            List<KofOperation> ops, List<IRLocalVariable> locals,
            List<Type> argTypes, Type valueType) {
        Type arg0 = argTypes.isEmpty() ? null : argTypes.get(0);
        // #386 — containsValue: a sonda nativa exige caixa nos dois lados
        // quando a família é Int/Long (slot boxed × arg cru = deref de
        // inteiro como ponteiro); §352: no slot Object TODO primitivo é
        // caixa (o scan dinâmico compara box×box, como o equals do JVM).
        if (!argTypes.isEmpty() && driver.target.isNative() && driver.needsErasureBoxing()
                && (CollectionLoweringSupport.mapBoxablePrim(arg0)
                        || CollectionLoweringSupport.referenceSlotPrim(valueType, arg0))
                && !ExpressionTyper.boxesOwnBranches(driver, mc.arguments().get(0), locals)) {
            CompilerEmissionHelpers.emitErasureBox(driver, ops, arg0);
        }
        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT,
                CollectionMethodGates.valueCmpTag(valueType, arg0)));
    }
}
