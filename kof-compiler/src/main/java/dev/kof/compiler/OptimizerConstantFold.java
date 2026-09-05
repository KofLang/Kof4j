package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Constant-folding pass of the IR optimizer (docs/performance.md §8, §12, §14).
 *
 * Constant folding of arithmetic/comparisons/unary ops, identity elements
 * (x+0, x*1, ...), and conditional jumps to direct jumps, with position
 * bookkeeping for folded ops.
 *
 * Extraído de Optimizer (REFACTOR-500 FASE 8): transformação de IR pura,
 * sem alteração de semântica (gate: OptimizerTest + suíte).
 */
final class OptimizerConstantFold {

    private OptimizerConstantFold() {}
    static List<KofOperation> constantFold(List<KofOperation> ops,
                                                   Map<KofOperation, SourcePosition> positions) {
        List<KofOperation> out = new ArrayList<>(ops.size());
        for (int i = 0; i < ops.size(); i++) {
            KofOperation op = ops.get(i);
            if (op instanceof KofConditionalJump cj) {
                if (i >= 1) {
                    KofOperation folded = foldConditionalJump(ops, i, cj, positions);
                    if (folded != null) {
                        replaceLastTwo(out, folded, positions);
                        continue;
                    }
                }
                if (cj.trueLabel().equals(cj.falseLabel())) {
                    KofJump j = new KofJump(cj.trueLabel());
                    transferPosition(op, j, positions);
                    out.set(out.size() - 1, j);
                    continue;
                }
                out.add(op);
                continue;
            }
            if (op instanceof KofBinary kb && i >= 1
                    && ops.get(i - 1) instanceof KofLoadLiteral lb) {
                if (i >= 2 && ops.get(i - 2) instanceof KofLoadLiteral la) {
                    KofLoadLiteral folded = foldBinary(la, lb, kb);
                    if (folded != null) {
                        SourcePosition p = positions.remove(la);
                        positions.remove(lb);
                        positions.remove(op);
                        if (p != null) positions.put(folded, p);
                        out.set(out.size() - 2, folded);
                        out.remove(out.size() - 1);
                        continue;
                    }
                }
                KofLoadLiteral identity = foldIdentity(lb, kb);
                if (identity == FOLD_REMOVE) {
                    positions.remove(lb);
                    positions.remove(op);
                    out.remove(out.size() - 1);
                    continue;
                }
                if (identity != null) {
                    SourcePosition p = positions.remove(lb);
                    positions.remove(op);
                    if (p != null) positions.put(identity, p);
                    out.set(out.size() - 1, identity);
                    continue;
                }
                out.add(op);
                continue;
            }
            if (op instanceof KofUnary ku && i >= 1
                    && ops.get(i - 1) instanceof KofLoadLiteral la) {
                KofLoadLiteral folded = foldUnary(la, ku);
                if (folded != null) {
                    SourcePosition p = positions.remove(la);
                    positions.remove(op);
                    if (p != null) positions.put(folded, p);
                    out.set(out.size() - 1, folded);
                    continue;
                }
            }
            out.add(op);
        }
        return out;
    }

    /** Sentinel returned by foldIdentity when the binary is a no-op. */
    private static final KofLoadLiteral FOLD_REMOVE = new KofLoadLiteral(Type.PrimitiveType.INT, 0);

    /**
     * Identity elements for int/long arithmetic: x+0, x-0, x*1, x/1, x|0,
     * x^0, x<<0 are all equal to x (no exceptions possible for these ops).
     * The result replaces the literal slot, keeping stack depth unchanged.
     */
    private static KofLoadLiteral foldIdentity(KofLoadLiteral lb, KofBinary kb) {
        if (!(kb.operandType() instanceof Type.PrimitiveType pt)) return null;
        String name = pt.name();
        if (!("int".equals(name) || "long".equals(name))) return null;
        if (!(lb.value() instanceof Number n)) return null;
        boolean isZero = n.longValue() == 0;
        boolean isOne = n.longValue() == 1;
        switch (kb.op()) {
            case ADD, SUB, OR, XOR, SHL -> { if (isZero) return FOLD_REMOVE; }
            case MUL, DIV -> { if (isOne) return FOLD_REMOVE; }
            // x % 1 is NOT folded here: replacing [x, 1, MOD] with a literal
            // 0 would leave x's value on the stack (stack-depth change);
            // the two-literal case is handled by foldBinary.
            default -> { }
        }
        return null;
    }

    private static KofLoadLiteral foldBinary(KofLoadLiteral la, KofLoadLiteral lb, KofBinary kb) {
        if (!(la.value() instanceof Number) || !(lb.value() instanceof Number)) {
            return foldNullComparison(la, lb, kb);
        }
        if (!(kb.operandType() instanceof Type.PrimitiveType pt)) return null;
        String name = pt.name();
        boolean isInt = "int".equals(name);
        boolean isLong = "long".equals(name);
        boolean isBool = "bool".equals(name);
        if (!isInt && !isLong && !isBool && !"float".equals(name) && !"double".equals(name)) return null;
        Number a = (Number) la.value();
        Number b = (Number) lb.value();
        if (isInt) {
            int av = a.intValue();
            int bv = b.intValue();
            return switch (kb.op()) {
                case ADD -> KofLoadLiteral.ofInt(av + bv);
                case SUB -> KofLoadLiteral.ofInt(av - bv);
                case MUL -> KofLoadLiteral.ofInt(av * bv);
                case DIV -> bv == 0 ? null : KofLoadLiteral.ofInt(av / bv);
                case MOD -> bv == 0 ? null : KofLoadLiteral.ofInt(av % bv);
                case AND -> KofLoadLiteral.ofInt(av & bv);
                case OR -> KofLoadLiteral.ofInt(av | bv);
                case XOR -> KofLoadLiteral.ofInt(av ^ bv);
                case SHL -> KofLoadLiteral.ofInt(av << (bv & 31));
                case SHR -> KofLoadLiteral.ofInt(av >> (bv & 31));
                case USHR -> KofLoadLiteral.ofInt(av >>> (bv & 31));
                case EQ -> KofLoadLiteral.ofBool(av == bv);
                case NE -> KofLoadLiteral.ofBool(av != bv);
                case LT -> KofLoadLiteral.ofBool(av < bv);
                case LE -> KofLoadLiteral.ofBool(av <= bv);
                case GT -> KofLoadLiteral.ofBool(av > bv);
                case GE -> KofLoadLiteral.ofBool(av >= bv);
            };
        }
        if (isLong) {
            long av = a.longValue();
            long bv = b.longValue();
            return switch (kb.op()) {
                case ADD -> KofLoadLiteral.ofLong(av + bv);
                case SUB -> KofLoadLiteral.ofLong(av - bv);
                case MUL -> KofLoadLiteral.ofLong(av * bv);
                case DIV -> bv == 0 ? null : KofLoadLiteral.ofLong(av / bv);
                case MOD -> bv == 0 ? null : KofLoadLiteral.ofLong(av % bv);
                case AND -> KofLoadLiteral.ofLong(av & bv);
                case OR -> KofLoadLiteral.ofLong(av | bv);
                case XOR -> KofLoadLiteral.ofLong(av ^ bv);
                case SHL -> KofLoadLiteral.ofLong(av << (bv & 63));
                case SHR -> KofLoadLiteral.ofLong(av >> (bv & 63));
                case USHR -> KofLoadLiteral.ofLong(av >>> (bv & 63));
                case EQ -> KofLoadLiteral.ofBool(av == bv);
                case NE -> KofLoadLiteral.ofBool(av != bv);
                case LT -> KofLoadLiteral.ofBool(av < bv);
                case LE -> KofLoadLiteral.ofBool(av <= bv);
                case GT -> KofLoadLiteral.ofBool(av > bv);
                case GE -> KofLoadLiteral.ofBool(av >= bv);
            };
        }
        if ("float".equals(name)) {
            float av = a.floatValue();
            float bv = b.floatValue();
            return switch (kb.op()) {
                case ADD -> KofLoadLiteral.ofFloat(av + bv);
                case SUB -> KofLoadLiteral.ofFloat(av - bv);
                case MUL -> KofLoadLiteral.ofFloat(av * bv);
                case DIV -> KofLoadLiteral.ofFloat(av / bv);
                case MOD -> KofLoadLiteral.ofFloat(av % bv);
                case EQ -> KofLoadLiteral.ofBool(av == bv);
                case NE -> KofLoadLiteral.ofBool(av != bv);
                case LT -> KofLoadLiteral.ofBool(av < bv);
                case LE -> KofLoadLiteral.ofBool(av <= bv);
                case GT -> KofLoadLiteral.ofBool(av > bv);
                case GE -> KofLoadLiteral.ofBool(av >= bv);
                default -> null;
            };
        }
        if ("double".equals(name)) {
            double av = a.doubleValue();
            double bv = b.doubleValue();
            return switch (kb.op()) {
                case ADD -> KofLoadLiteral.ofDouble(av + bv);
                case SUB -> KofLoadLiteral.ofDouble(av - bv);
                case MUL -> KofLoadLiteral.ofDouble(av * bv);
                case DIV -> KofLoadLiteral.ofDouble(av / bv);
                case MOD -> KofLoadLiteral.ofDouble(av % bv);
                case EQ -> KofLoadLiteral.ofBool(av == bv);
                case NE -> KofLoadLiteral.ofBool(av != bv);
                case LT -> KofLoadLiteral.ofBool(av < bv);
                case LE -> KofLoadLiteral.ofBool(av <= bv);
                case GT -> KofLoadLiteral.ofBool(av > bv);
                case GE -> KofLoadLiteral.ofBool(av >= bv);
                default -> null;
            };
        }
        // bool values are stored as int 0/1
        int av = a.intValue();
        int bv = b.intValue();
        return switch (kb.op()) {
            case AND -> KofLoadLiteral.ofBool((av & bv) != 0);
            case OR -> KofLoadLiteral.ofBool((av | bv) != 0);
            case XOR -> KofLoadLiteral.ofBool((av ^ bv) != 0);
            case EQ -> KofLoadLiteral.ofBool(av == bv);
            case NE -> KofLoadLiteral.ofBool(av != bv);
            default -> null;
        };
    }

    /** Reference equality between literal values (only null/null is known statically). */
    private static KofLoadLiteral foldNullComparison(KofLoadLiteral la, KofLoadLiteral lb, KofBinary kb) {
        if (kb.op() != KofBinaryOp.EQ && kb.op() != KofBinaryOp.NE) return null;
        boolean aNull = la.value() == null;
        boolean bNull = lb.value() == null;
        if (aNull && bNull) return KofLoadLiteral.ofBool(kb.op() == KofBinaryOp.EQ);
        if (aNull || bNull) return KofLoadLiteral.ofBool(kb.op() == KofBinaryOp.NE);
        return null;
    }

    private static KofLoadLiteral foldUnary(KofLoadLiteral la, KofUnary ku) {
        if (!(la.value() instanceof Number v)) return null;
        return switch (ku.op()) {
            case NEG -> {
                if (v instanceof Integer i) yield KofLoadLiteral.ofInt(-i);
                if (v instanceof Long l) yield KofLoadLiteral.ofLong(-l);
                if (v instanceof Float f) yield KofLoadLiteral.ofFloat(-f);
                if (v instanceof Double d) yield KofLoadLiteral.ofDouble(-d);
                yield null;
            }
            case NOT -> v instanceof Integer i ? KofLoadLiteral.ofInt(i == 0 ? 1 : 0) : null;
            case I2L -> v instanceof Integer i ? KofLoadLiteral.ofLong(i.longValue()) : null;
            case I2C -> v instanceof Integer i ? KofLoadLiteral.ofInt(i & 0xFFFF) : null;
            case L2I -> v instanceof Long l ? KofLoadLiteral.ofInt(l.intValue()) : null;
            case I2F -> v instanceof Integer i ? KofLoadLiteral.ofFloat(i.floatValue()) : null;
            case I2D -> v instanceof Integer i ? KofLoadLiteral.ofDouble(i.doubleValue()) : null;
            case L2F -> v instanceof Long l ? KofLoadLiteral.ofFloat(l.floatValue()) : null;
            case L2D -> v instanceof Long l ? KofLoadLiteral.ofDouble(l.doubleValue()) : null;
            case F2D -> v instanceof Float f ? KofLoadLiteral.ofDouble(f.doubleValue()) : null;
            case D2F -> v instanceof Double d ? KofLoadLiteral.ofFloat(d.floatValue()) : null;
            case D2I -> v instanceof Double d ? KofLoadLiteral.ofInt((int) d.doubleValue()) : null;
            case F2I -> v instanceof Float f ? KofLoadLiteral.ofInt((int) f.floatValue()) : null;
            case D2L -> v instanceof Double d ? KofLoadLiteral.ofLong((long) d.doubleValue()) : null;
            case F2L -> v instanceof Float f ? KofLoadLiteral.ofLong((long) f.floatValue()) : null;
        };
    }

    /**
     * `LoadLiteral(a) LoadLiteral(b) KofConditionalJump(cmp,t,f)` folds to a
     * direct jump when both operands are literals. A single literal must NOT
     * be treated as a known condition: `[expr, 0, NE]` is a runtime
     * comparison against zero, not a statically known result.
     */
    private static KofOperation foldConditionalJump(List<KofOperation> ops, int i,
                                                    KofConditionalJump cj,
                                                    Map<KofOperation, SourcePosition> positions) {
        if (i < 2) return null;
        KofOperation a = ops.get(i - 2);
        KofOperation b = ops.get(i - 1);
        if (!(a instanceof KofLoadLiteral la) || !(b instanceof KofLoadLiteral lb)) return null;
        if (!(la.value() instanceof Number na) || !(lb.value() instanceof Number nb)) {
            if (la.value() == null && lb.value() == null) {
                return new KofJump(cj.comparison() == KofComparison.EQ
                        ? cj.trueLabel() : cj.falseLabel());
            }
            if (la.value() == null || lb.value() == null) {
                return new KofJump(cj.comparison() == KofComparison.NE
                        ? cj.trueLabel() : cj.falseLabel());
            }
            return null;
        }
        if (!(la.type() instanceof Type.PrimitiveType pa)
                || !(lb.type() instanceof Type.PrimitiveType pb)) return null;
        String fam = integerFamily(pa.name(), pb.name());
        if (fam == null) return null;
        boolean result = evalComparison(la, lb, cj.comparison(), fam);
        return new KofJump(result ? cj.trueLabel() : cj.falseLabel());
    }

    private static boolean evalComparison(KofLoadLiteral la, KofLoadLiteral lb,
                                          KofComparison cmp, String name) {
        Number a = (Number) la.value();
        Number b = (Number) lb.value();
        return switch (name) {
            case "int", "bool" -> {
                int av = a.intValue();
                int bv = b.intValue();
                yield compare(cmp, av, bv);
            }
            case "long" -> {
                long av = a.longValue();
                long bv = b.longValue();
                yield compare(cmp, av, bv);
            }
            case "float" -> compare(cmp, a.floatValue(), b.floatValue());
            case "double" -> compare(cmp, a.doubleValue(), b.doubleValue());
            default -> false;
        };
    }

    private static String integerFamily(String a, String b) {
        boolean aInt = "int".equals(a) || "bool".equals(a) || "char".equals(a)
                || "byte".equals(a) || "short".equals(a);
        boolean bInt = "int".equals(b) || "bool".equals(b) || "char".equals(b)
                || "byte".equals(b) || "short".equals(b);
        if (aInt && bInt) return "int";
        return null;
    }

    private static boolean compare(KofComparison cmp, int a, int b) {
        return switch (cmp) {
            case EQ -> a == b;
            case NE -> a != b;
            case LT -> a < b;
            case LE -> a <= b;
            case GT -> a > b;
            case GE -> a >= b;
        };
    }

    private static boolean compare(KofComparison cmp, long a, long b) {
        return switch (cmp) {
            case EQ -> a == b;
            case NE -> a != b;
            case LT -> a < b;
            case LE -> a <= b;
            case GT -> a > b;
            case GE -> a >= b;
        };
    }

    private static boolean compare(KofComparison cmp, float a, float b) {
        return switch (cmp) {
            case EQ -> a == b;
            case NE -> a != b;
            case LT -> a < b;
            case LE -> a <= b;
            case GT -> a > b;
            case GE -> a >= b;
        };
    }

    private static boolean compare(KofComparison cmp, double a, double b) {
        return switch (cmp) {
            case EQ -> a == b;
            case NE -> a != b;
            case LT -> a < b;
            case LE -> a <= b;
            case GT -> a > b;
            case GE -> a >= b;
        };
    }

    private static void replaceLastTwo(List<KofOperation> out, KofOperation replacement,
                                       Map<KofOperation, SourcePosition> positions) {
        int size = out.size();
        if (size == 1) {
            SourcePosition p = positions.remove(out.get(0));
            if (p != null) positions.put(replacement, p);
            out.set(0, replacement);
            return;
        }
        KofOperation a = out.get(size - 2);
        KofOperation b = out.get(size - 1);
        SourcePosition p = positions.remove(a);
        positions.remove(b);
        if (p != null) positions.put(replacement, p);
        out.set(size - 2, replacement);
        out.remove(size - 1);
    }

    private static void transferPosition(KofOperation from, KofOperation to,
                                         Map<KofOperation, SourcePosition> positions) {
        SourcePosition p = positions.remove(from);
        if (p != null) positions.put(to, p);
    }
}
