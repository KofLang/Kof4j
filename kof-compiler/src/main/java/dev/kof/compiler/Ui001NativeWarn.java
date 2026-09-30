package dev.kof.compiler;

/**
 * UI001 (R6, issue #683): kof.ui on the Native targets is a documented
 * no-op — but it MUST NOT be a silent no-op.
 *
 * <p>Mirrors UI002 (the Script interpreter warns once on stderr via
 * {@code KofInterpreter.warnUi002}): at compile time, when the target is
 * native and the lowered IR contains a {@code kof_ui_*} call, report
 * exactly one WARNING diagnostic pointing at {@code --target=js}.
 *
 * <p>Additive by construction: {@code success()} only counts ERRORs, so no
 * previously-green build turns red; the binary is unchanged.
 */
final class Ui001NativeWarn {

    private Ui001NativeWarn() {}

    /**
     * Reports the UI001 warning once when {@code target} is native and
     * {@code ir} contains a {@code kof_ui_*} call. Returns the first
     * {@code kof_ui_*} function found, or null when no warning applies.
     */
    static String warnOnce(IRModule ir, Target target, DiagnosticCollector diagnostics) {
        if (ir == null || diagnostics == null || target == null || !target.isNative()) return null;
        String first = firstUiCall(ir);
        if (first == null) return null;
        diagnostics.warning("", 0, 0, 0,
                "kof.ui does not render on the native target (first call: " + first + "); "
                        + "kof.ui is KofJS — run with --target=js for real UI",
                "UI001");
        return first;
    }

    /** First {@code kof_ui_*} callee in the module, in IR order — null when absent. */
    static String firstUiCall(IRModule ir) {
        for (IRClass cls : ir.classes()) {
            for (IRMethod m : cls.methods()) {
                for (IRBasicBlock b : m.basicBlocks()) {
                    for (KofOperation op : b.operations()) {
                        if (op instanceof KofCall call && call.methodName().startsWith("kof_ui_")) {
                            return call.methodName();
                        }
                    }
                }
            }
        }
        return null;
    }
}
