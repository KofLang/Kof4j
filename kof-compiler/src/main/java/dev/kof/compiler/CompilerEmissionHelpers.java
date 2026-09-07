package dev.kof.compiler;

import java.util.List;

/**
 * Emissão de conversões de primitivos (widen/narrow/box/unbox) e helpers.
 */
public final class CompilerEmissionHelpers {

    private CompilerEmissionHelpers() {}

    static boolean needsErasureBoxing(CompilerDriver driver) {
        return driver.target == Target.JVM;
    }

    static boolean isJvmTarget(CompilerDriver driver) {
        return driver.target == Target.JVM;
    }

    static void emitWideningIfNeeded(CompilerDriver driver, List<KofOperation> ops, Type from, Type to) {
        if (from.equals(to)) return;
        String fn = TypeMetrics.primitiveName(from);
        String tn = TypeMetrics.primitiveName(to);
        // slot declarado primitivo + valor de tipo APAGADO (Unknown/Object/
        // TypeVariable): o valor real chega boxed (ex.: `await` sobre um
        // handle que perdeu o Handle<Int> ao passar por um parâmetro Object —
        // kof_await devolve Object). Sem o unbox aqui, o return emitia
        // ireturn sobre referência → VerifyError (GitHub #31).
        if (!tn.isEmpty() && !TypeMetrics.isPrimitiveType(from)
                && (from instanceof Type.UnknownType
                    || from instanceof Type.TypeVariable
                    || (from instanceof Type.ClassType ct && "java.lang".equals(ct.packageName())
                        && "Object".equals(ct.name())))) {
            emitErasureUnbox(driver, ops, to);
            return;
        }
        KofUnaryOp conv = switch (tn) {
            case "long", "Long" -> switch (fn) {
                case "int", "Int", "char", "Char", "short", "Short", "byte", "Byte" -> KofUnaryOp.I2L;
                default -> null;
            };
            case "float", "Float" -> switch (fn) {
                case "int", "Int", "char", "Char", "short", "Short", "byte", "Byte" -> KofUnaryOp.I2F;
                case "long", "Long" -> KofUnaryOp.L2F;
                case "double", "Double" -> KofUnaryOp.D2F;
                default -> null;
            };
            case "double", "Double" -> switch (fn) {
                case "int", "Int", "char", "Char", "short", "Short", "byte", "Byte" -> KofUnaryOp.I2D;
                case "long", "Long" -> KofUnaryOp.L2D;
                case "float", "Float" -> KofUnaryOp.F2D;
                default -> null;
            };
            default -> null;
        };
        if (conv != null) {
            ops.add(new KofUnary(conv, from));
        }
    }

    static void emitPrimNarrow(CompilerDriver driver, List<KofOperation> ops, Type from, Type to) {
        if (from.equals(to)) return;
        String fn = TypeMetrics.primitiveName(from);
        String tn = TypeMetrics.primitiveName(to);
        KofUnaryOp conv = switch (tn) {
            case "int", "Int" -> switch (fn) {
                case "long", "Long" -> KofUnaryOp.L2I;
                case "float", "Float" -> KofUnaryOp.F2I;
                case "double", "Double" -> KofUnaryOp.D2I;
                default -> null;
            };
            case "long", "Long" -> switch (fn) {
                case "float", "Float" -> KofUnaryOp.F2L;
                case "double", "Double" -> KofUnaryOp.D2L;
                default -> null;
            };
            default -> null;
        };
        if (conv != null) {
            ops.add(new KofUnary(conv, from));
        }
    }

    static boolean isZeroLiteral(LiteralExpr lit) {
        if (lit.value() == null) return false;
        String v = lit.value().trim();
        boolean zero = "0".equals(v) || "-0".equals(v)
                || "0.0".equals(v) || "-0.0".equals(v) || "0.00".equals(v);
        return switch (lit.kind()) {
            case ConcreteLiteralKind.INT, ConcreteLiteralKind.LONG -> zero;
            case ConcreteLiteralKind.FLOAT, ConcreteLiteralKind.DOUBLE -> zero;
            default -> false;
        };
    }

    static void emitErasureBox(CompilerDriver driver, List<KofOperation> ops, Type primitive) {
        if (!driver.needsErasureBoxing()) return;
        Type boxed = TypeMetrics.boxedTypeFor(primitive);
        Type boxParam = primitive instanceof Type.PrimitiveType pt
                && ("char".equals(pt.name()) || "Char".equals(pt.name())) ? Type.PrimitiveType.INT : primitive;
        ops.add(new KofCall(boxed, "kof_box", List.of(boxParam), boxed, KofCallKind.FUNCTION));
    }

    static void emitErasureUnbox(CompilerDriver driver, List<KofOperation> ops, Type primitive) {
        if (!driver.needsErasureBoxing()) return;
        Type boxed = TypeMetrics.boxedTypeFor(primitive);
        ops.add(new KofCall(primitive, "kof_unbox", List.of(boxed), primitive, KofCallKind.FUNCTION));
    }
}