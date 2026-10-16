package dev.kof.compiler.backend;

import dev.kof.compiler.KofConditionalJump;
import dev.kof.compiler.KofJump;
import dev.kof.compiler.KofLabel;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.SourcePosition;
import dev.kof.compiler.LabelId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Jump threading over the linear op stream (Phase B/§3 O2 of
 * {@code docs/development/assembly-optimization-plan.md}: "jump threading").
 *
 * <p>A TRAMPOLINE is a block that is exactly {@code Label(L); Jump(M)} — reaching
 * {@code L} immediately jumps to {@code M}. Threading retargets every jump whose
 * target is a trampoline {@code L} (and which is not itself the trampoline's own
 * jump) straight to {@code M}, removing an intermediate hop. Chains are resolved
 * to the final target.
 *
 * <p><b>Safety:</b> the rewrite only changes a jump's destination from an alias
 * to the value that alias always resolves to — the reached block is identical,
 * so semantics are unchanged. A trampoline is only a candidate when its block is
 * EXACTLY the two ops (no other op runs when it is entered); self-referential
 * chains ({@code L -> L}) are left alone (a guard stops the walk).
 *
 * <p>Additive: nothing in the production {@link Optimizer} calls it yet (see
 * {@link PassPipeline#standard()}); emitted code is unchanged until a caller opts
 * in.
 */
public final class JumpThreading implements IrPass {

    @Override
    public String name() {
        return "jump-threading";
    }

    @Override
    public List<KofOperation> run(List<KofOperation> ops,
                                  Map<KofOperation, SourcePosition> positions) {
        // 1) trampolines: a label whose IMMEDIATELY following op is a Jump — reaching
        //    that label always jumps to the jump's target.
        Map<LabelId, LabelId> tramp = new HashMap<>();
        for (int i = 0; i + 1 < ops.size(); i++) {
            if (ops.get(i) instanceof KofLabel kl && ops.get(i + 1) instanceof KofJump j) {
                tramp.putIfAbsent(kl.label(), j.target());
            }
        }
        if (tramp.isEmpty()) return ops;

        // 2) resolve chains to the final non-trampoline target (cycle-guarded).
        Map<LabelId, LabelId> resolved = new HashMap<>();
        for (LabelId start : tramp.keySet()) {
            LabelId cur = start;
            java.util.Set<LabelId> seen = new java.util.HashSet<>();
            seen.add(cur);
            while (true) {
                LabelId next = tramp.get(cur);
                if (next == null || next.equals(cur) || !seen.add(next)) break;
                cur = next;
            }
            resolved.put(start, cur);
        }

        // 3) retarget every jump (and conditional jump) to the resolved target.
        //    NOTE: this pass is additive and NOT wired into the production
        //    Optimizer — the JS backend reconstructs if/while/switch structure
        //    from the exact jump targets, so retargeting them (measured: both
        //    KofJsE2ETest.execBreakContinue and KofSwitchExprE2ETest.nestedJs)
        //    breaks it. Kept for the JVM/native backends once the JS
        //    reconstruction is made robust to retargeted jumps.
        List<KofOperation> out = new ArrayList<>(ops.size());
        for (KofOperation op : ops) {
            if (op instanceof KofJump j) {
                LabelId t = resolved.get(j.target());
                if (t != null && !t.equals(j.target())) {
                    out.add(new KofJump(t));
                    continue;
                }
            } else if (op instanceof KofConditionalJump cj) {
                LabelId t = resolved.getOrDefault(cj.trueLabel(), cj.trueLabel());
                LabelId f = resolved.getOrDefault(cj.falseLabel(), cj.falseLabel());
                if (!t.equals(cj.trueLabel()) || !f.equals(cj.falseLabel())) {
                    out.add(new KofConditionalJump(cj.comparison(), cj.operandType(), t, f));
                    continue;
                }
            }
            out.add(op);
        }
        return out;
    }
}
