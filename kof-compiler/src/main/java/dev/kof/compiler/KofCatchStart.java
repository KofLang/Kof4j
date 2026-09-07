package dev.kof.compiler;

import java.util.List;
import java.util.Map;
public record KofCatchStart(LabelId handlerLabel, String exceptionType, int localIndex) implements KofOperation {
}
