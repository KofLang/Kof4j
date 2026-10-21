package dev.kof.compiler.backend;

import dev.kof.compiler.IRMethod;
import dev.kof.compiler.Type;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Virtual-register numbering over a method's local slots (Phase A of
 * {@code docs/assembly-optimization-plan.md} §21: "define virtual
 * registers").
 *
 * <p>The IR addresses locals by {@code (type, index)} slot pairs (a slot may be
 * reused with different types, e.g. an {@code int} and a {@code long}). A
 * virtual register is the pair identity: the same slot with the same type maps
 * to the SAME vreg; a slot reused with a different type maps to a DIFFERENT
 * vreg. Numbering is deterministic (first-seen order over parameters then
 * declared locals), which later passes (liveness, allocation) rely on.
 *
 * <p>This is a pure read of the method — it allocates nothing in the emitted
 * code and changes no behavior. It is the numbering the Phase C register
 * allocator will consume.
 */
public final class VirtualRegisters {

    private final Map<String, Integer> vregOf = new LinkedHashMap<>();

    private VirtualRegisters() {
    }

    /** Assigns vreg ids for a method: parameters first, then declared locals. */
    public static VirtualRegisters of(IRMethod method) {
        VirtualRegisters vr = new VirtualRegisters();
        int next = 0;
        for (int i = 0; i < method.parameterTypes().size(); i++) {
            next = vr.assign(i, method.parameterTypes().get(i), next);
        }
        for (var local : method.localVariables()) {
            next = vr.assign(local.index(), local.type(), next);
        }
        return vr;
    }

    private int assign(int slot, Type type, int next) {
        String key = key(slot, type);
        if (!vregOf.containsKey(key)) {
            vregOf.put(key, next++);
        }
        return next;
    }

    /** The vreg id for a {@code (slot, type)} pair, or {@code -1} if unseen. */
    public int vregOf(int slot, Type type) {
        return vregOf.getOrDefault(key(slot, type), -1);
    }

    /** The number of distinct virtual registers. */
    public int count() {
        return vregOf.size();
    }

    private static String key(int slot, Type type) {
        return slot + ":" + type;
    }
}
