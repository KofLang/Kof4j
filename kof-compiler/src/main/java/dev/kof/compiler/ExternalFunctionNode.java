package dev.kof.compiler;

import java.util.List;

/**
 * FFI (TIER 2.1.1/2.1.2): declaração {@code extern name(params): ReturnType;}
 * — formaliza a assinatura de uma função externa em compile-time. Não há
 * corpo: o binding real é lowering/runtime por target (TIER 2.1.4+); enquanto
 * não existe, o lowering emite o gap honesto FFI001 em vez de gerar bytecode
 * quebrado.
 */
public record ExternalFunctionNode(SourcePosition position, String library, String returnType,
                                   String name, List<FormalParameterNode> parameters,
                                   String ownership) implements AstNode {
    /** null = the inline extern form (no foreign module block) — the array
     *  params are BY-VALUE copy-in (the D6-2/3.8b contract, the C cannot
     *  mutate the caller's array). {@code "borrowed"} = the module declared
     *  the borrow: the callee MAY write the array during the call and the
     *  write flows back (the copy-out fatia, the #1156 face). */
    public ExternalFunctionNode {
        if (ownership != null && ownership.isBlank()) ownership = null;
    }
}
