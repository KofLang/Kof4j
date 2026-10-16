package dev.kof.compiler.backend;

import dev.kof.compiler.KofBinary;
import dev.kof.compiler.KofBinaryOp;
import dev.kof.compiler.KofLoadLiteral;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.SourcePosition;
import dev.kof.compiler.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Strength reduction over the linear op stream (Phase B/§12 of
 * {@code docs/development/assembly-optimization-plan.md}).
 *
 * <p>Replaces a multiplication by a power of two with a left shift:
 * {@code x * 2^n} becomes {@code x << n}. On the stack IR the pattern is
 * {@code LoadLiteral(2^n); Binary(MUL)} — the literal's value and the binary are
 * rewritten to {@code LoadLiteral(n); Binary(SHL)}, keeping the op stream
 * balanced (same shape, same operand count).
 *
 * <p><b>Only where it is exactly equivalent:</b> integer and long types only
 * (two's complement — {@code x * 2^n == x << n} for every {@code x}, including
 * negatives); never floating point ({@code x * 2.0} is not a shift), never
 * {@code n == 0} (a shift by 0 is a no-op but the multiply is already identity
 * and handled elsewhere), and only a positive power of two. Division is
 * deliberately NOT reduced: {@code x / 2} rounds toward zero while {@code x >> 1}
 * rounds toward negative infinity, so they differ for negative {@code x}.
 *
 * <p>Additive: nothing in the production {@link Optimizer} calls it yet (see
 * {@link PassPipeline#standard()}); emitted code is unchanged until a caller opts
 * in.
 */
public final class StrengthReduction implements IrPass {

    @Override
    public String name() {
        return "strength-reduction";
    }

    @Override
    public List<KofOperation> run(List<KofOperation> ops,
                                  Map<KofOperation, SourcePosition> positions) {
        List<KofOperation> out = new ArrayList<>(ops.size());
        KofOperation prev = null;
        for (KofOperation op : ops) {
            if (op instanceof KofBinary b && b.op() == KofBinaryOp.MUL
                    && isShiftable(b.operandType()) && prev instanceof KofLoadLiteral lit) {
                int shift = powerOfTwoShift(lit);
                if (shift > 0) {
                    // The shift amount is ALWAYS an int on the stack, even for a
                    // long value: JVM `lshl`/`ishl` take (long,int)/(int,int), so
                    // a long shift literal would be a VerifyError (see
                    // ExpressionBinaryLowerer §167). The operand type of the
                    // resulting SHL stays the value's type.
                    KofLoadLiteral replacement = KofLoadLiteral.ofInt(shift);
                    // The literal's position is the multiplier's; keep it.
                    SourcePosition p = positions.remove(prev);
                    out.remove(out.size() - 1);
                    if (p != null) positions.put(replacement, p);
                    out.add(replacement);
                    out.add(new KofBinary(KofBinaryOp.SHL, b.operandType()));
                    prev = null;
                    continue;
                }
            }
            out.add(op);
            prev = op;
        }
        return out;
    }

    private static boolean isShiftable(Type t) {
        return t.equals(Type.PrimitiveType.INT) || t.equals(Type.PrimitiveType.LONG);
    }

    /** {@code n} when the literal is a positive power of two, else {@code -1}. */
    private static int powerOfTwoShift(KofLoadLiteral lit) {
        long v;
        if (lit.value() instanceof Integer i) v = i;
        else if (lit.value() instanceof Long l) v = l;
        else return -1;
        if (v <= 1) return -1;                 // 1 is identity; <= 0 is not a power of two
        if ((v & (v - 1)) != 0) return -1;     // not a power of two
        return Long.numberOfTrailingZeros(v);
    }
}
