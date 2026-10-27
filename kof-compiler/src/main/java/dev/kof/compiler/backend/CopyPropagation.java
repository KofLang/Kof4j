package dev.kof.compiler.backend;

import dev.kof.compiler.KofCatchStart;
import dev.kof.compiler.KofConditionalJump;
import dev.kof.compiler.KofJump;
import dev.kof.compiler.KofLabel;
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
 * Copy propagation over the linear op stream (Phase B of
 * {@code docs/assembly-optimization-plan.md} §11).
 *
 * <p>When a local is copied from another local — {@code LoadLocal(x); StoreLocal(y)}
 * with matching types — later reads of {@code y} are redirected to read {@code x}
 * instead. Both a {@code LoadLocal(y)} and a {@code LoadLocal(x)} push one value
 * of the same type, so the op stream stays balanced; the store to {@code y} is
 * kept (so {@code y} is still written) and semantics are unchanged.
 *
 * <p><b>Safety (conservative by construction):</b> the copy map is cleared at
 * every control boundary ({@link KofLabel}, {@link KofJump},
 * {@link KofConditionalJump}, {@link KofTryStart}, {@link KofCatchStart},
 * {@link KofReturn}, {@link KofReturnVoid}, {@link KofThrow}) — a copy that
 * crosses a join is never assumed. A store to a slot invalidates that slot AND
 * any slot whose recorded source is that slot (a copy of a re-written local is
 * stale). Chains are resolved to the root source.
 *
 * <p>Additive: nothing in the production {@link Optimizer} calls it yet (see
 * {@link PassPipeline#standard()}); emitted code is unchanged until a caller
 * opts in.
 */
public final class CopyPropagation implements IrPass {

    @Override
    public String name() {
        return "copy-propagation";
    }

    @Override
    public List<KofOperation> run(List<KofOperation> ops,
                                  Map<KofOperation, SourcePosition> positions) {
        // slot -> the slot it is a copy of (resolved to a root source).
        Map<Integer, Integer> copyOf = new HashMap<>();
        // slot -> type, to guard a redirect against a type mismatch.
        Map<Integer, Type> typeOf = new HashMap<>();
        List<KofOperation> out = new ArrayList<>(ops.size());
        KofOperation prev = null;
        for (KofOperation op : ops) {
            if (isControlBoundary(op)) {
                copyOf.clear();
                typeOf.clear();
                out.add(op);
                prev = op;
                continue;
            }
            if (op instanceof KofLoadLocal ll && copyOf.containsKey(ll.index())
                    && !isStoreReadBack(ll, prev)) {
                int src = copyOf.get(ll.index());
                if (typeOf.get(src) != null && typeOf.get(src).equals(ll.type())) {
                    KofLoadLocal redirected = new KofLoadLocal(ll.type(), src);
                    SourcePosition p = positions.remove(op);
                    if (p != null) positions.put(redirected, p);
                    out.add(redirected);
                    prev = redirected;
                    continue;
                }
            }
            if (op instanceof KofStoreLocal sl) {
                // a store FIRST invalidates the slot itself and any slot copied FROM it.
                copyOf.remove(sl.index());
                typeOf.remove(sl.index());
                copyOf.entrySet().removeIf(e -> e.getValue() == sl.index());
                // then, if the producer is a bare load of another slot, record the new copy.
                if (prev instanceof KofLoadLocal src && src.type().equals(sl.type())) {
                    int root = copyOf.getOrDefault(src.index(), src.index());
                    copyOf.put(sl.index(), root);
                    typeOf.put(sl.index(), sl.type());
                    typeOf.putIfAbsent(root, src.type());
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

    /**
     * An immediate {@code store(slot); load(slot)} read-back is a
     * compiler-internal encoding the backends pattern-match on (the store/load
     * pairs of the {@code #switch}/{@code #recv} temporaries, see
     * {@code Optimizer.deadEffects}) — it is left verbatim, not redirected. A
     * non-adjacent read of a copied slot still redirects.
     */
    private static boolean isStoreReadBack(KofLoadLocal load, KofOperation prev) {
        return prev instanceof KofStoreLocal store
                && store.index() == load.index()
                && store.type().equals(load.type());
    }
}
