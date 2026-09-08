package dev.kof.compiler;

import java.util.List;
import java.util.Map;
public record IRBasicBlock(int index, List<KofOperation> operations) {
}
