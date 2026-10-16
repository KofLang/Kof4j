package dev.kof.compiler.backend;

import dev.kof.compiler.KofCatchStart;
import dev.kof.compiler.KofConditionalJump;
import dev.kof.compiler.KofFunctionAddress;
import dev.kof.compiler.KofJump;
import dev.kof.compiler.KofLabel;
import dev.kof.compiler.KofLoadLiteral;
import dev.kof.compiler.KofLoadLocal;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.KofReturn;
import dev.kof.compiler.KofReturnVoid;
import dev.kof.compiler.KofStoreLocal;
import dev.kof.compiler.KofThrow;
import dev.kof.compiler.KofTryStart;
import dev.kof.compiler.SourcePosition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dead store elimination over the linear op stream (Phase B of
 * {@code docs/development/assembly-optimization-plan.md} §10).
 *
 * <p>A store is DEAD when the slot it writes is overwritten by a later store
 * before any read of that slot — the first value is never observed. When such a
 * store's producer is a pure, zero-input, one-output op (a {@link KofLoadLiteral},
 * a {@link KofLoadLocal}, or a {@link KofFunctionAddress}), the producer AND the
 * store are marked dead together, keeping the op stream balanced.
 *
 * <p><b>Safety (conservative by construction):</b>
 * <ul>
 *   <li>only a store whose IMMEDIATELY preceding op is such a pure producer is a
 *       candidate — a store fed by an opaque producer (a call, an arithmetic
 *       chain) is left alone, because removing its producer would be unsafe;</li>
 *   <li>any read of the slot (a {@link KofLoadLocal}) between the two stores
 *       keeps the first store alive;</li>
 *   <li>the live-store map is cleared at every control boundary
 *       ({@link KofLabel}, {@link KofJump}, {@link KofConditionalJump},
 *       {@link KofTryStart}, {@link KofCatchStart}, {@link KofReturn},
 *       {@link KofReturnVoid}, {@link KofThrow}) — a store that might be read on
 *       another path is never removed;</li>
 *   <li>a store to a DIFFERENT slot does not invalidate this one.</li>
 * </ul>
 *
 * <p>Removal is a mark-and-sweep over op identity (not index arithmetic), so a
 * removal never invalidates another store's recorded position.
 *
 * <p>Additive: nothing in the production {@link Optimizer} calls it yet (see
 * {@link PassPipeline#standard()}); emitted code is unchanged until a caller
 * opts in.
 */
public final class DeadCodeElimination implements IrPass {

    /** A live store: the slot it writes plus its producer and store ops. */
    private record LiveStore(KofOperation producer, KofOperation store) {
    }

    @Override
    public String name() {
        return "dead-store-elimination";
    }

    @Override
    public List<KofOperation> run(List<KofOperation> ops,
                                  Map<KofOperation, SourcePosition> positions) {
        Map<Integer, LiveStore> live = new HashMap<>();
        Set<KofOperation> dead = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        KofOperation prev = null;
        for (KofOperation op : ops) {
            if (isControlBoundary(op)) {
                live.clear();
                prev = op;
                continue;
            }
            if (op instanceof KofLoadLocal ll) {
                live.remove(ll.index());       // a read keeps the pending store alive
                prev = op;
                continue;
            }
            if (op instanceof KofStoreLocal sl) {
                LiveStore p = live.get(sl.index());
                if (p != null && p.producer() != null) {
                    // the previous store's value was never read -> both ops are dead.
                    // A store is ONLY removable together with its producer: removing
                    // a store whose value came from an opaque producer (a call, a
                    // dup, an arithmetic chain) would leave that pushed value on the
                    // stack, unbalancing the op stream (ASM frame crash).
                    dead.add(p.producer());
                    dead.add(p.store());
                }
                boolean pureProducer = isPureProducer(prev);
                live.put(sl.index(), new LiveStore(pureProducer ? prev : null, op));
                prev = op;
                continue;
            }
            prev = op;
        }
        List<KofOperation> out = new ArrayList<>(ops.size());
        for (KofOperation op : ops) {
            if (dead.contains(op)) {
                positions.remove(op);
                continue;
            }
            out.add(op);
        }
        return out;
    }

    private static boolean isPureProducer(KofOperation op) {
        return op instanceof KofLoadLiteral || op instanceof KofLoadLocal
                || op instanceof KofFunctionAddress;
    }

    private static boolean isControlBoundary(KofOperation op) {
        return op instanceof KofLabel || op instanceof KofJump
                || op instanceof KofConditionalJump || op instanceof KofTryStart
                || op instanceof KofCatchStart || op instanceof KofReturn
                || op instanceof KofReturnVoid || op instanceof KofThrow;
    }
}
