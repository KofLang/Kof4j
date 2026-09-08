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
                                   String name, List<FormalParameterNode> parameters) implements AstNode {
}
