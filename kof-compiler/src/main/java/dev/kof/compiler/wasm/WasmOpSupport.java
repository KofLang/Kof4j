package dev.kof.compiler.wasm;

import dev.kof.compiler.KofArrayLength;
import dev.kof.compiler.KofArrayLoad;
import dev.kof.compiler.KofBinary;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofCheckCast;
import dev.kof.compiler.KofConditionalJump;
import dev.kof.compiler.KofContinueLabel;
import dev.kof.compiler.KofDup;
import dev.kof.compiler.KofGetStatic;
import dev.kof.compiler.KofJump;
import dev.kof.compiler.KofLabel;
import dev.kof.compiler.KofLoadField;
import dev.kof.compiler.KofLoadLiteral;
import dev.kof.compiler.KofLoadLocal;
import dev.kof.compiler.KofNewObject;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.KofPop;
import dev.kof.compiler.KofReturnVoid;
import dev.kof.compiler.KofStoreLocal;
import dev.kof.compiler.KofUnary;

import java.util.Set;

/**
 * Phase D of {@code docs/assembly-optimization-plan.md}: "each
 * backend must explicitly declare which operations it supports."
 *
 * <p>The native backends declare theirs in {@code NativeOpSupport}; this is the
 * WASM backend's declaration, as explicit DATA rather than something to infer
 * from the lowerer's instanceof chain. Every op NOT in this set is refused with
 * {@link WasmUnsupportedException} (a diagnosed WASM002), which is the same
 * never-silent contract the native emitters follow.
 *
 * <p>The set is the WASI/WASM 15.3d increment's surface: scalars, locals,
 * control flow, calls, string/record handles and the record/array access paths
 * that landed with the bump-heap lowering. Collections beyond these handles and
 * instance-method lowering are still future slices, which is exactly why the
 * declaration matters — a new op must be a DELIBERATE addition here.
 */
final class WasmOpSupport {

    private WasmOpSupport() {
    }

    /** The IR operations this backend lowers. */
    static final Set<Class<?>> SUPPORTED = Set.of(
            KofArrayLength.class,
            KofArrayLoad.class,
            KofBinary.class,
            KofCall.class,
            KofCheckCast.class,
            KofConditionalJump.class,
            KofContinueLabel.class,
            KofDup.class,
            KofGetStatic.class,
            KofJump.class,
            KofLabel.class,
            KofLoadField.class,
            KofLoadLiteral.class,
            KofLoadLocal.class,
            KofNewObject.class,
            KofPop.class,
            KofReturnVoid.class,
            KofStoreLocal.class,
            KofUnary.class);

    /** True when this backend declares support for {@code opType}. */
    static boolean supports(Class<?> opType) {
        return SUPPORTED.contains(opType);
    }

    /** The declared count, for the drift guard and for diagnostics. */
    static int size() {
        return SUPPORTED.size();
    }

    /** True when the op is a {@link KofOperation} subtype this backend declares. */
    static boolean isDeclared(KofOperation op) {
        return SUPPORTED.contains(op.getClass());
    }

    /**
     * The diagnosed refusal for an op this method did not handle. Splitting the
     * two cases keeps a DRIFT loud: an op the set claims to support but the
     * lowerer never handles is a declared capability that does not exist, while
     * an op absent from the set is an honest, expected refusal.
     */
    static RuntimeException refusal(KofOperation op, String funcName) {
        if (isDeclared(op)) {
            return new WasmUnsupportedException("WASM002 DRIFT: '" + op.getClass().getSimpleName()
                    + "' esta declarada em WasmOpSupport mas o lowerer nao a trata, em '"
                    + funcName + "'");
        }
        return new WasmUnsupportedException("construcao fora do subset escalar 15.2 (WASM002): '"
                + op.getClass().getSimpleName() + "' em '" + funcName
                + "' — docs/wasm-wasi-plan.md (#776)");
    }
}
