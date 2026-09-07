package dev.kof.compiler;

import java.util.List;
public record AnnotationNode(SourcePosition position, String name,
                      List<AnnotationPair> pairs) implements AstNode {

    /** Valor único na forma curta @Name("x"): par com chave null. */
    boolean singleValue() {
        return pairs.size() == 1 && pairs.get(0).key() == null;
    }
}
