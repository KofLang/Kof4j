package dev.kof.compiler.nat;

import dev.kof.compiler.KofArrayLength;
import dev.kof.compiler.KofArrayLoad;
import dev.kof.compiler.KofArrayStore;
import dev.kof.compiler.KofBinary;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofCatchStart;
import dev.kof.compiler.KofCheckCast;
import dev.kof.compiler.KofConditionalJump;
import dev.kof.compiler.KofContinueLabel;
import dev.kof.compiler.KofDup;
import dev.kof.compiler.KofDup2;
import dev.kof.compiler.KofDupX1;
import dev.kof.compiler.KofDupX2;
import dev.kof.compiler.KofExcUnlink;
import dev.kof.compiler.KofFunctionAddress;
import dev.kof.compiler.KofGetStatic;
import dev.kof.compiler.KofInstanceOf;
import dev.kof.compiler.KofJump;
import dev.kof.compiler.KofLabel;
import dev.kof.compiler.KofLoadField;
import dev.kof.compiler.KofLoadLiteral;
import dev.kof.compiler.KofLoadLocal;
import dev.kof.compiler.KofNewArray;
import dev.kof.compiler.KofNewMultiArray;
import dev.kof.compiler.KofNewObject;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.KofPop;
import dev.kof.compiler.KofPop2;
import dev.kof.compiler.KofPutStatic;
import dev.kof.compiler.KofReturn;
import dev.kof.compiler.KofReturnVoid;
import dev.kof.compiler.KofStatementIf;
import dev.kof.compiler.KofStoreField;
import dev.kof.compiler.KofStoreLocal;
import dev.kof.compiler.KofThrow;
import dev.kof.compiler.KofTryEnd;
import dev.kof.compiler.KofTryStart;
import dev.kof.compiler.KofUnary;

import java.util.Set;

/**
 * Phase D of {@code docs/development/assembly-optimization-plan.md} §6/§735:
 * "each backend must explicitly declare which operations it supports."
 *
 * <p>An EXPLICIT, machine-readable declaration of the IR operations the native
 * x86-64 emitter lowers. It is the CONTRACT: the emitter's {@code switch} must
 * handle exactly this set (verified by {@code NativeOpSupportTest}, which also
 * checks completeness against every {@link KofOperation} in the compiler so a NEW
 * op cannot be added without a decision here). The declaration makes an
 * unsupported op a compile-time contract surface rather than a runtime
 * {@code UnsupportedOperationException} discovered in the field.
 *
 * <p>Pure data — it changes no emission.
 */
final class NativeOpSupport {

    private NativeOpSupport() {
    }

    /** The IR operations the native x86-64 backend lowers, explicitly. */
    static final Set<Class<?>> X86_64 = Set.of(
            KofArrayLength.class, KofArrayLoad.class, KofArrayStore.class,
            KofBinary.class, KofCall.class, KofCatchStart.class, KofCheckCast.class,
            KofConditionalJump.class, KofContinueLabel.class,
            KofDup.class, KofDup2.class, KofDupX1.class, KofDupX2.class,
            KofExcUnlink.class, KofFunctionAddress.class, KofGetStatic.class,
            KofInstanceOf.class, KofJump.class, KofLabel.class,
            KofLoadField.class, KofLoadLiteral.class, KofLoadLocal.class,
            KofNewArray.class, KofNewMultiArray.class, KofNewObject.class,
            KofPop.class, KofPop2.class, KofPutStatic.class,
            KofReturn.class, KofReturnVoid.class, KofStatementIf.class,
            KofStoreField.class, KofStoreLocal.class, KofThrow.class,
            KofTryEnd.class, KofTryStart.class, KofUnary.class);

    /** True when the x86-64 backend declares support for {@code op}'s type. */
    static boolean supports(Class<?> opType) {
        return X86_64.contains(opType);
    }

    /**
     * The IR operations the RISC-V 64 (and, via translation, AArch64) CROSS
     * backend lowers, explicitly. It is {@link #X86_64} MINUS
     * {@link KofFunctionAddress} — the cross lowering never materializes a bare
     * function address (higher-order calls go through {@link KofCall}); verified
     * by {@code NativeOpSupportTest} (the cross emitter's cases must equal this
     * set) and by the higher-order E2E tests passing on both cross targets.
     */
    static final Set<Class<?>> RISCV64_CROSS = X86_64.stream()
            .filter(c -> c != KofFunctionAddress.class)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    /** True when the cross (riscv64/aarch64) backend declares support for the op. */
    static boolean supportsCross(Class<?> opType) {
        return RISCV64_CROSS.contains(opType);
    }
}
