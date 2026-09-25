package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * §500 — lowering único dos argumentos de uma chamada estática resolvida pelo
 * classpath externo / reflexão JDK: aridade fixa segue o caminho coercitivo do
 * driver; VARARGS empacota os args finais num array do TIPO COMPONENTE do último
 * parâmetro (primitivos boxados) — a mesma receita do {@code StringFormatCallLowerer}
 * (#156/#216) generalizada para qualquer assinatura com {@code ACC_VARARGS} /
 * {@code isVarArgs} (`Arrays.asList(1,2)` → {@code Object[]}, `String.join`
 * → {@code CharSequence[]}). Sem este packing o emit fabricava descritor de
 * aridade fixa (`"".asList:(II)` / `String.join:(String,String,String)`) e o
 * artefato morria no load/run (R6/Q7).
 */
final class VarargsArrayPacker {

    private VarargsArrayPacker() {}

    /** Formais do {@code KofCall}: os próprios parameterDescriptors da
     *  assinatura REAL (inclui o array final quando varargs). */
    static List<Type> formalTypes(ExternalClasspath.MethodSignature sig) {
        List<Type> formal = new ArrayList<>();
        for (String d : sig.parameterDescriptors()) {
            formal.add(ExternalClasspath.typeFromDescriptor(d));
        }
        return formal;
    }

    /** Empilha os argumentos na ordem de chamada; retorna o localIdx. */
    static int lower(CompilerDriver driver, List<ExpressionNode> args,
                     ExternalClasspath.MethodSignature sig, List<KofOperation> ops,
                     String owner, int localIdx, List<IRLocalVariable> locals) {
        List<Type> formal = formalTypes(sig);
        if (!sig.isVarargs()) {
            return driver.emitArgumentsWithFormalTypes(args, formal, ops, owner, localIdx, locals);
        }
        int fixed = sig.parameterDescriptors().size() - 1;
        if (fixed > 0) {
            localIdx = driver.emitArgumentsWithFormalTypes(
                    args.subList(0, fixed), formal.subList(0, fixed),
                    ops, owner, localIdx, locals);
        }
        Type component = ExternalClasspath.typeFromDescriptor(
                sig.parameterDescriptors().get(sig.parameterDescriptors().size() - 1)
                        .substring(1));
        int extra = args.size() - fixed;
        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, extra));
        ops.add(new KofNewArray(component));
        for (int i = 0; i < extra; i++) {
            ops.add(new KofDup());
            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, i));
            ExpressionNode arg = args.get(fixed + i);
            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
            Type argType = ExpressionTyper.inferExprType(driver, arg, locals);
            if (argType instanceof Type.PrimitiveType) {
                TypeEmitter.boxPrimitive(ops, argType);
            }
            ops.add(new KofArrayStore(component));
        }
        return localIdx;
    }
}
