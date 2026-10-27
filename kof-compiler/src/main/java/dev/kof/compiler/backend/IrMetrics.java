package dev.kof.compiler.backend;

import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofConditionalJump;
import dev.kof.compiler.KofJump;
import dev.kof.compiler.KofLoadField;
import dev.kof.compiler.KofLoadLocal;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.KofReturn;
import dev.kof.compiler.KofReturnVoid;
import dev.kof.compiler.KofStoreField;
import dev.kof.compiler.KofStoreLocal;
import dev.kof.compiler.KofThrow;

import java.util.List;

/**
 * Instruction metrics over the IR (Phase C/§18 of
 * {@code docs/assembly-optimization-plan.md}).
 *
 * <p>§18 lists the counters the backend should track: instruction count, load
 * count, store count, branch count, call count, spill count, register usage.
 * This class computes them from the op stream (a pure read — nothing is
 * emitted), so an optimization's effect can be MEASURED (the Phase B reduction,
 * the Phase C spill/register numbers) rather than asserted.
 *
 * <p>{@code spillCount} and {@code registersUsed} come from the Phase C
 * {@link RegisterAllocator.Allocation}; the rest are counted directly. All
 * counters are exact over the op stream — no sampling, no estimation.
 */
public record IrMetrics(int instructions, int loads, int stores, int branches,
                        int calls, int returns, int spillCount, int registersUsed) {

    /** Counts the op stream; spill/register figures default to 0. */
    public static IrMetrics of(List<KofOperation> ops) {
        return of(ops, null);
    }

    /** Counts the op stream, adding the allocation's spill/register figures. */
    public static IrMetrics of(List<KofOperation> ops, RegisterAllocator.Allocation alloc) {
        int loads = 0, stores = 0, branches = 0, calls = 0, returns = 0;
        for (KofOperation op : ops) {
            if (op instanceof KofLoadLocal || op instanceof KofLoadField) {
                loads++;
            } else if (op instanceof KofStoreLocal || op instanceof KofStoreField) {
                stores++;
            } else if (op instanceof KofJump || op instanceof KofConditionalJump) {
                branches++;
            } else if (op instanceof KofCall) {
                calls++;
            } else if (op instanceof KofReturn || op instanceof KofReturnVoid
                    || op instanceof KofThrow) {
                returns++;
            }
        }
        int spills = alloc == null ? 0 : alloc.spills().size();
        int registers = alloc == null ? 0 : alloc.colorsUsed();
        return new IrMetrics(ops.size(), loads, stores, branches, calls, returns,
                spills, registers);
    }

    /** A one-line summary for reports/logs. */
    public String summary() {
        return "instructions=" + instructions + " loads=" + loads + " stores=" + stores
                + " branches=" + branches + " calls=" + calls + " returns=" + returns
                + " spills=" + spillCount + " registers=" + registersUsed;
    }
}
