package dev.kof.compiler.backend;

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

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Effect and local def/use analysis over the IR (Phase A of
 * {@code docs/assembly-optimization-plan.md} §21: "define use
 * analysis; define effect analysis").
 *
 * <p>The IR is a linear, stack-based op stream with local slots. Later passes
 * (dead-code elimination, copy propagation, CSE) need two facts per op:
 * <ul>
 *   <li>its {@link Effect} — whether it is pure, reads/writes state, performs a
 *       call, or is a control-flow/structural marker;</li>
 *   <li>which local slots it {@code def}s (stores) and {@code use}s (loads).</li>
 * </ul>
 *
 * <p>This class is a pure read of the IR — it changes nothing about emission.
 * The classification is conservative by design: anything whose side effects
 * cannot be proven absent is {@link Effect#CALL} (a barrier), never silently
 * treated as pure.
 */
public final class EffectAnalysis {

    /** Coarse effect class of an op, from the optimizer's point of view. */
    public enum Effect {
        /** No observable effect; safe to eliminate/reorder (literals, arithmetic). */
        PURE,
        /** Reads state (local/field/static/array load) but writes nothing. */
        READ,
        /** Writes state (local/field/static/array store). */
        WRITE,
        /** Opaque side effects (call, allocation, throw); a reorder/elim barrier. */
        CALL,
        /** Control-flow terminator (jump/branch/return). */
        CONTROL,
        /** Structural marker (label, try/catch boundary); no value effect. */
        STRUCTURAL
    }

    /** Per-block summary: the slots defined/used and the effects present. */
    public record BlockSummary(Set<Integer> defs, Set<Integer> uses,
                               boolean hasCall, boolean hasWrite, boolean hasControl) {
    }

    private EffectAnalysis() {
    }

    /** The effect class of a single op. */
    public static Effect effectOf(KofOperation op) {
        if (op instanceof KofJump || op instanceof KofConditionalJump
                || op instanceof KofReturn || op instanceof KofReturnVoid) {
            return Effect.CONTROL;
        }
        if (op instanceof KofThrow) return Effect.CALL;          // unwinds; a barrier
        if (op instanceof KofCall) return Effect.CALL;
        if (op instanceof KofNewObject || op instanceof KofNewArray
                || op instanceof KofNewMultiArray) {
            return Effect.CALL;                                  // allocation
        }
        if (op instanceof KofStoreLocal || op instanceof KofStoreField
                || op instanceof KofPutStatic || op instanceof KofArrayStore
                || op instanceof KofExcUnlink) {
            return Effect.WRITE;
        }
        if (op instanceof KofLoadLocal || op instanceof KofLoadField
                || op instanceof KofGetStatic || op instanceof KofArrayLoad) {
            return Effect.READ;
        }
        if (op instanceof KofLabel || op instanceof KofContinueLabel
                || op instanceof KofTryStart || op instanceof KofTryEnd
                || op instanceof KofCatchStart || op instanceof KofStatementIf) {
            return Effect.STRUCTURAL;
        }
        if (op instanceof KofLoadLiteral || op instanceof KofBinary
                || op instanceof KofUnary || op instanceof KofDup
                || op instanceof KofDup2 || op instanceof KofDupX1
                || op instanceof KofDupX2 || op instanceof KofPop
                || op instanceof KofPop2 || op instanceof KofCheckCast
                || op instanceof KofInstanceOf || op instanceof KofArrayLength
                || op instanceof KofFunctionAddress) {
            return Effect.PURE;
        }
        return Effect.CALL;   // unknown op: conservative barrier, never "pure"
    }

    /**
     * Summarises a block: {@code uses} are the slots read before being written
     * in the block (upward-exposed uses), {@code defs} the slots written.
     */
    public static BlockSummary summarize(ControlFlowGraph.Block block) {
        Set<Integer> defs = new LinkedHashSet<>();
        Set<Integer> uses = new LinkedHashSet<>();
        boolean hasCall = false, hasWrite = false, hasControl = false;
        for (KofOperation op : block.operations()) {
            Effect e = effectOf(op);
            switch (e) {
                case CALL -> hasCall = true;
                case WRITE -> hasWrite = true;
                case CONTROL -> hasControl = true;
                default -> { }
            }
            if (op instanceof KofLoadLocal ll) {
                if (!defs.contains(ll.index())) uses.add(ll.index());
            } else if (op instanceof KofStoreLocal sl) {
                defs.add(sl.index());
            } else if (op instanceof KofCatchStart cs) {
                defs.add(cs.localIndex());
            }
        }
        return new BlockSummary(defs, uses, hasCall, hasWrite, hasControl);
    }
}
