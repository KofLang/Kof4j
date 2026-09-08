package dev.kof.compiler;

import java.util.List;
import java.util.Map;
public record KofTryStart(LabelId startLabel, LabelId endLabel, LabelId handlerLabel,
                   String exceptionType, int excLocalIndex) implements KofOperation {
}
