package dev.kof.compiler.backend;

import dev.kof.compiler.IRBasicBlock;
import dev.kof.compiler.IRLocalVariable;
import dev.kof.compiler.IRMethod;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Per-method register-placement driver (Phase D of
 * {@code docs/development/assembly-optimization-plan.md}; decision
 * {@code D-CODEGEN-REWRITE}, maintainer 13/10).
 *
 * <p>Chains the Phase C analyses and the Phase D mapping for one method: build
 * the linear op stream, run {@link LivenessAnalysis} → {@link InterferenceGraph}
 * → {@link RegisterAllocator} (with {@link SpillCost}) → {@link RegisterAssignment},
 * yielding the physical register (or memory) each local slot is placed in. It is
 * the single entry point the codegen rewrite will call.
 *
 * <p>ADDITIVE — it computes a placement and returns it; it does not touch the
 * method or any emission. The same additive discipline as the Phase A–C analyses.
 */
public final class RegisterPlanner {

    /** The placement for one method: the assignment plus the allocator's result. */
    public record Placement(RegisterAssignment.Assignment assignment,
                            RegisterAllocator.Allocation allocation,
                            int registerCount) {

        /** The physical register for a slot, or {@code null} if it is in memory. */
        public String register(int slot) {
            return assignment.register(slot);
        }

        /** True when the slot is placed in a physical register. */
        public boolean isRegister(int slot) {
            return assignment.isRegister(slot);
        }

        /**
         * The callee-saved registers this method's placement USES, ascending — the
         * registers the prologue must save and the epilogue restore (a callee-saved
         * register the method never touches needs no save). rbx/r12–r15 are the
         * INTEGER callee-saved file; SysV has no callee-saved SSE register.
         */
        public java.util.List<String> calleeSavedUsed() {
            java.util.TreeSet<String> used = new java.util.TreeSet<>();
            for (String reg : assignment.regOf().values()) {
                if (X86_64RegisterFile.isCalleeSaved(X86_64RegisterFile.RegClass.INTEGER, reg)
                        || X86_64RegisterFile.isCalleeSaved(X86_64RegisterFile.RegClass.SSE, reg)) {
                    used.add(reg);
                }
            }
            return List.copyOf(used);
        }
    }

    private RegisterPlanner() {
    }

    /**
     * Plans register placement for a method, using {@code registerCount} integer
     * and the same number of SSE registers (a simple, deterministic budget).
     */
    public static Placement plan(IRMethod method, int registerCount) {
        List<KofOperation> ops = flatten(method);
        LivenessAnalysis live = LivenessAnalysis.ofOps(ops);
        InterferenceGraph graph = InterferenceGraph.of(ops, live);
        Map<Integer, Type> slotTypes = slotTypes(method);
        // #866 (5ª face): um slot que NUNCA é LIDO não precisa de registrador — e
        // atribuir um a ele era o defeito: um slot morto não interfere com
        // ninguém no grafo, então o alocador lhe dá a cor mais baixa e o PRÓLOGO
        // copia o parâmetro para esse registrador COMPARTILHADO. Medido no
        // disassembly: `Int g(10 params) { return a + j }` (só `a` e `j` são
        // usados) carregava NOVE parâmetros em `%rbx`, um sobrescrevendo o outro —
        // `rbx` acabava com `i = 9` e o retorno virava `9 + 10 = 19` em vez de
        // `1 + 10 = 11`. Filtrar os slots lidos deixa os não-lidos no FRAME (o
        // prólogo sempre guarda os params nos seus slots) e só coloca em
        // registrador quem é de fato lido (colisão impossível: valores vivos
        // interferem).
        slotTypes.keySet().retainAll(readSlots(ops));
        // PARAM slots are defined by the ABI (they arrive in registers), never by
        // a KofStoreLocal, so the def-driven graph omits them — leaving the register
        // model inert for the common `f(a, b) { ... }` shape. Add them as nodes with
        // their entry-live interference so they are coloured (and never share a
        // register with another value live at the same time).
        graph = graph.withParams(live, live.blockCount(), slotTypes.keySet());
        SpillCost cost = SpillCost.ofOps(ops);
        RegisterAllocator.Allocation alloc = RegisterAllocator.allocate(graph, registerCount, cost);
        RegisterAssignment.Assignment assignment = RegisterAssignment.of(slotTypes, alloc);
        return new Placement(assignment, alloc, registerCount);
    }

    /** Slots READ anywhere in the method ({@code KofLoadLocal}). */
    private static java.util.Set<Integer> readSlots(List<KofOperation> ops) {
        java.util.Set<Integer> read = new java.util.TreeSet<>();
        for (KofOperation op : ops) {
            if (op instanceof dev.kof.compiler.KofLoadLocal ll) read.add(ll.index());
        }
        return read;
    }

    /** The declared type of each local slot of the method. */
    private static Map<Integer, Type> slotTypes(IRMethod method) {
        Map<Integer, Type> types = new TreeMap<>();
        for (IRLocalVariable lv : method.localVariables()) {
            types.put(lv.index(), lv.type());
        }
        return types;
    }

    private static List<KofOperation> flatten(IRMethod method) {
        List<KofOperation> ops = new ArrayList<>();
        for (IRBasicBlock b : method.basicBlocks()) {
            ops.addAll(b.operations());
        }
        return ops;
    }

    /**
     * Whether a method is ELIGIBLE for the register-mapped local model (Phase D
     * switch-over).
     *
     * <p><b>Methods that CALL are NOT eligible (issue #841/#839 regression gate,
     * measured 14/10).</b> With the feature on, a method that calls another
     * function mis-coloured a live local/param: `Av1CflGeometryE2ETest
     * #av1CflRegionOnNativeX86` read a param as `1` where it should be `4` (and
     * a hash constant `8192` where it should vary) — the same defect behind the
     * `RasterDecode`/AV1 native faces of #839. Disabling the whole feature
     * (`-Dkof.native.register.locals=false`) makes those tests pass; restricting
     * eligibility to call-FREE methods makes them pass too. A method with 9
     * register-worthy locals and NO call is correct, so the defect is specific to
     * the call path (the interference/colouring does not protect a value live
     * across a call). The precise fix — model the call-clobber in the
     * interference graph — is the D-CODEGEN-REWRITE lane's follow-up; until then
     * this conservative gate keeps the idiom CORRECT (frame locals, the
     * pre-feature behaviour) at the cost of the optimisation on calling methods.
     *
     * <p>Exception methods are eligible too: the emission stores a caught
     * exception to the local's register when it is register-placed
     * ({@code KofCatchStart} is placement-aware). References and double-width
     * locals still stay in the frame via {@link RegisterAssignment#placeable}, so
     * only int/bool locals are register-placed.
     */
    public static boolean eligibleForRegisterLocals(IRMethod method) {
        for (var b : method.basicBlocks()) {
            for (var op : b.operations()) {
                if (op instanceof dev.kof.compiler.KofCall) return false;
            }
        }
        return true;
    }
}
