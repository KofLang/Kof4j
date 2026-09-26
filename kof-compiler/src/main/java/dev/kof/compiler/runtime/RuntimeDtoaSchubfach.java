package dev.kof.compiler.runtime;

import java.math.BigInteger;

/**
 * B-1c (PLAN-BAREMETAL-BOOT §B-1c): constant tables of the JDK
 * {@code DoubleToDecimal} (Schubfach) algorithm, needed by the libc-free dtoa.
 *
 * <p>The JDK renders {@code Double} with Schubfach (Giulietti,
 * "The Schubfach way to render doubles"), whose output is NOT the
 * mathematically shortest ({@code Double.toString(0x1)} = {@code 4.9E-324}
 * although {@code 5e-324} round-trips). To match the oracle byte-for-byte the
 * runtime must reproduce the algorithm exactly, so the constants below are
 * GENERATED from the same closed forms the JDK {@code MathUtils} uses, not
 * transcribed by hand.
 *
 * <p>{@code g} (the {@code K_MIN..K_MAX} split table): for each {@code k},
 * {@code 10^-k = beta 2^r} with {@code 2^125 <= beta < 2^126}; {@code g =
 * floor(beta) + 1}, split into {@code g1 = g >> 63} and {@code g0 = g &
 * (2^63-1)}. Validated: the full 1234-value table hashes exactly to the JDK
 * {@code MathUtils.g} array (see {@code SchubfachTableGeneratorTest}).
 */
public final class RuntimeDtoaSchubfach {

    /** JDK {@code DoubleToDecimal.K_MIN}/{@code K_MAX} (and {@code MathUtils}). */
    public static final int K_MIN = -324;
    public static final int K_MAX = 292;

    /** C_2 = floor(log2(10) * 2^Q_2) (JDK {@code MathUtils}). */
    private static final int Q_2 = 38;
    private static final long C_2 = 913_124_641_741L;

    /** The first powers of 10, 10^0..10^17 (JDK {@code MathUtils.pow10}). */
    private static final long[] POW10 = {
        1L, 10L, 100L, 1_000L, 10_000L, 100_000L, 1_000_000L, 10_000_000L,
        100_000_000L, 1_000_000_000L, 10_000_000_000L, 100_000_000_000L,
        1_000_000_000_000L, 10_000_000_000_000L, 100_000_000_000_000L,
        1_000_000_000_000_000L, 10_000_000_000_000_000L, 100_000_000_000_000_000L,
    };

    private RuntimeDtoaSchubfach() {}

    static int flog2pow10(int e) {
        return (int) ((e * C_2) >> Q_2);
    }

    /** {@code floor(10^-k / 2^(flog2pow10(-k)-125)) + 1}, as the JDK. */
    static long[] g1g0(int k) {
        int e = -k;
        int s = 125 - flog2pow10(e);
        BigInteger num;
        BigInteger den;
        if (e >= 0) {
            num = BigInteger.TEN.pow(e);
            den = BigInteger.ONE;
        } else {
            num = BigInteger.ONE;
            den = BigInteger.TEN.pow(-e);
        }
        if (s >= 0) {
            num = num.shiftLeft(s);
        } else {
            den = den.shiftLeft(-s);
        }
        BigInteger g = num.divide(den).add(BigInteger.ONE);
        long g1 = g.shiftRight(63).longValue();
        long g0 = g.and(BigInteger.ONE.shiftLeft(63).subtract(BigInteger.ONE)).longValue();
        return new long[]{g1, g0};
    }

    /** The interleaved {@code g1,g0} table for {@code k} in {@code K_MIN..K_MAX}. */
    public static long[] gTable() {
        long[] t = new long[(K_MAX - K_MIN + 1) * 2];
        for (int k = K_MIN; k <= K_MAX; k++) {
            long[] p = g1g0(k);
            t[(k - K_MIN) * 2] = p[0];
            t[(k - K_MIN) * 2 + 1] = p[1];
        }
        return t;
    }

    /** Emits {@code .Lschub_g} and {@code .Lschub_pow10} into {@code .rodata}. */
    public static void emitTables(StringBuilder sb) {
        sb.append("            .section .rodata\n");
        sb.append("            .align 8\n");
        sb.append(".Lschub_g:\n");
        long[] g = gTable();
        // §508: o guard i+1 < length e trivialmente equivalente ao i < length —
        // gTable() tem comprimento PAR por construcao ((K_MAX-K_MIN+1)*2); o
        // assert em SchubfachTableGeneratorTest fixa a invariante.
        for (int i = 0; i + 1 < g.length; i += 2) {
            sb.append("            .quad 0x").append(Long.toHexString(g[i]))
                    .append(", 0x").append(Long.toHexString(g[i + 1])).append('\n');
        }
        sb.append(".Lschub_pow10:\n");
        for (int i = 0; i < POW10.length; i++) {
            sb.append("            .quad ").append(POW10[i]).append('\n');
        }
    }

    /**
     * Emits the libc-free Schubfach dtoa: {@code kof_double_to_string(xmm0) ->
     * rax: String*}, reproducing the JDK {@code DoubleToDecimal} algorithm
     * (tables, {@code rop}, {@code toDecimal}, {@code toChars}) byte-for-byte.
     * Fixes §448 (Native {@code 5.0E-324} vs JVM {@code 4.9E-324}).
     */
    public static void emitCore(StringBuilder sb) {
        sb.append("            .section .rodata\n");
        sb.append(".Lschub_inf: .asciz \"Infinity\"\n");
        sb.append(".Lschub_ninf: .asciz \"-Infinity\"\n");
        sb.append(".Lschub_nan: .asciz \"NaN\"\n");
        sb.append(".Lschub_zero: .asciz \"0.0\"\n");
        sb.append(".Lschub_nzero: .asciz \"-0.0\"\n");
        emitTables(sb);
        RuntimeDtoaSchubfachCore.emit(sb);
        RuntimeDtoaSchubfachDouble.emit(sb);
        RuntimeDtoaSchubfachFloat.emit(sb);
    }
}