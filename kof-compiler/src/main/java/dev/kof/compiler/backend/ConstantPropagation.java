package dev.kof.compiler.backend;

import dev.kof.compiler.KofCatchStart;
import dev.kof.compiler.KofConditionalJump;
import dev.kof.compiler.KofJump;
import dev.kof.compiler.KofLabel;
import dev.kof.compiler.KofLoadLiteral;
import dev.kof.compiler.KofLoadLocal;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.KofReturn;
import dev.kof.compiler.KofReturnVoid;
import dev.kof.compiler.KofStatementIf;
import dev.kof.compiler.KofStoreLocal;
import dev.kof.compiler.KofThrow;
import dev.kof.compiler.KofTryStart;
import dev.kof.compiler.SourcePosition;
import dev.kof.compiler.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Constant propagation over the linear op stream (Phase B of
 * {@code docs/assembly-optimization-plan.md} §9).
 *
 * <p>When a literal is stored to a local slot and the slot is later read with no
 * intervening write, the read is replaced by the literal. This is a
 * stack-effect-preserving rewrite (both a local load and a literal load push one
 * value), so the op stream stays balanced.
 *
 * <p><b>Safety (conservative by construction):</b> the slot→constant map is
 * cleared at every control boundary ({@link KofLabel}, {@link KofJump},
 * {@link KofConditionalJump}, {@link KofTryStart}, {@link KofCatchStart},
 * {@link KofReturn}, {@link KofReturnVoid}, {@link KofThrow}) — a value that
 * flows across a branch join is never assumed constant. A store to one slot
 * invalidates only that slot. A {@link KofStoreLocal} records a constant ONLY
 * when the immediately preceding op is a matching {@link KofLoadLiteral}; any
 * other producer (a call result, an arithmetic value) leaves the slot unknown.
 *
 * <p>This is additive: nothing in the production {@link Optimizer} calls it yet
 * (see {@link PassPipeline#standard()}), so emitted code is unchanged until a
 * caller opts in — the same discipline as Phase A.
 */
public final class ConstantPropagation implements IrPass {

    @Override
    public String name() {
        return "constant-propagation";
    }

    @Override
    public List<KofOperation> run(List<KofOperation> ops,
                                  Map<KofOperation, SourcePosition> positions) {
        Map<Integer, KofLoadLiteral> consts = new HashMap<>();
        List<KofOperation> out = new ArrayList<>(ops.size());
        KofOperation prev = null;
        for (KofOperation op : ops) {
            if (isControlBoundary(op)) {
                consts.clear();
                out.add(op);
                prev = op;
                continue;
            }
            if (op instanceof KofLoadLocal ll && consts.containsKey(ll.index())
                    && !isStoreReadBack(ll, prev)) {
                KofLoadLiteral c = consts.get(ll.index());
                if (c.type().equals(ll.type())) {
                    // Emit a DISTINCT literal instance per rewrite site. Sharing
                    // one instance across sites is unsafe: a later pass that
                    // removes ops by IDENTITY (dead-store elimination) would
                    // remove every occurrence when it removes one producer.
                    KofLoadLiteral fresh = copyOf(c);
                    SourcePosition p = positions.remove(op);
                    if (p != null) positions.put(fresh, p);
                    out.add(fresh);
                    prev = fresh;
                    continue;
                }
            }
            if (op instanceof KofStoreLocal sl) {
                if (prev instanceof KofLoadLiteral lit && lit.type().equals(sl.type())) {
                    consts.put(sl.index(), lit);
                } else {
                    consts.remove(sl.index());
                }
            }
            out.add(op);
            prev = op;
        }
        return out;
    }

    private static boolean isControlBoundary(KofOperation op) {
        return op instanceof KofLabel || op instanceof KofJump
                || op instanceof KofConditionalJump || op instanceof KofTryStart
                || op instanceof KofCatchStart || op instanceof KofReturn
                || op instanceof KofReturnVoid || op instanceof KofThrow
                || op instanceof KofStatementIf;
    }

    private static KofLoadLiteral copyOf(KofLoadLiteral lit) {
        return new KofLoadLiteral(lit.type(), lit.value());
    }

    /**
     * An immediate {@code store(slot); load(slot)} read-back is a
     * compiler-internal encoding the backends pattern-match on (the store/load
     * pairs of the {@code #switch}/{@code #recv} temporaries, see
     * {@code Optimizer.deadEffects}) — it is left verbatim, not folded. A
     * non-adjacent read of a constant slot still propagates.
     */
    private static boolean isStoreReadBack(KofLoadLocal load, KofOperation prev) {
        return prev instanceof KofStoreLocal store
                && store.index() == load.index()
                && store.type().equals(load.type());
    }
}
