package dev.kof.compiler;

import java.util.List;
import java.util.Map;
public record KofBinary(KofBinaryOp op, Type operandType) implements KofOperation {
}
