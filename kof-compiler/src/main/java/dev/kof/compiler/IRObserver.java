package dev.kof.compiler;

import java.util.List;

/**
 * Callback for tooling that wants the IR statistics of a compilation
 * (kof inspect, docs/architecture/performance.md §34). Invoked after lowering with the
 * module statistics before and after optimization.
 */
@FunctionalInterface
public interface IRObserver {
    void observed(IRStatistics stats);
}