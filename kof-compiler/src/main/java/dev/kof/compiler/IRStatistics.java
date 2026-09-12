package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * IR statistics for tooling (kof inspect, docs/architecture/performance.md §34).
 *
 * Reports per-method operation counts before and after optimization so
 * tools can show what the optimizer eliminated.
 */
public final class IRStatistics {

    public record MethodStat(String className, String methodName, long opsBefore, long opsAfter) {
        public long removed() {
            return opsBefore - opsAfter;
        }

        public long reductionPct() {
            return opsBefore > 0 ? (100 * removed()) / opsBefore : 0;
        }
    }

    private final long classes;
    private final long opsBefore;
    private final long opsAfter;
    private final List<MethodStat> methods;

    private IRStatistics(long classes, long opsBefore, long opsAfter, List<MethodStat> methods) {
        this.classes = classes;
        this.opsBefore = opsBefore;
        this.opsAfter = opsAfter;
        this.methods = methods;
    }

    public static IRStatistics of(IRModule before, IRModule after) {
        List<MethodStat> stats = new ArrayList<>();
        for (IRClass cls : after.classes()) {
            for (IRMethod m : cls.methods()) {
                long beforeOps = methodOps(cls.name(), m.name(), before);
                long afterOps = methodOps(cls.name(), m.name(), after);
                stats.add(new MethodStat(cls.name(), m.name(), beforeOps, afterOps));
            }
        }
        return new IRStatistics(after.classes().size(), totalOps(before), totalOps(after), stats);
    }

    public long classes() {
        return classes;
    }

    public long opsBefore() {
        return opsBefore;
    }

    public long opsAfter() {
        return opsAfter;
    }

    public long opsRemoved() {
        return opsBefore - opsAfter;
    }

    public long reductionPct() {
        return opsBefore > 0 ? (100 * opsRemoved()) / opsBefore : 0;
    }

    public List<MethodStat> methods() {
        return methods;
    }

    public static long totalOps(IRModule module) {
        long total = 0;
        for (IRClass cls : module.classes()) {
            for (IRMethod m : cls.methods()) {
                total += methodOps(cls.name(), m.name(), module);
            }
        }
        return total;
    }

    private static long methodOps(String className, String methodName, IRModule module) {
        for (IRClass cls : module.classes()) {
            if (!cls.name().equals(className)) continue;
            for (IRMethod m : cls.methods()) {
                if (!m.name().equals(methodName)) continue;
                long count = 0;
                for (IRBasicBlock bb : m.basicBlocks()) {
                    for (KofOperation op : bb.operations()) {
                        if (!(op instanceof KofLabel)) count++;
                    }
                }
                return count;
            }
        }
        return 0;
    }
}