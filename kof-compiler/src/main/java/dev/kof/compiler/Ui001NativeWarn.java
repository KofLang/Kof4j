package dev.kof.compiler;

/**
 * UI001 (R6 residual of {@code KOFUI-AUDIT}): {@code kof.ui} on Native is a
 * documented no-op, but must not stay silent at compile time. When the
 * lowered IR contains any {@code kof_ui_*} call and the target is Native,
 * emit exactly one WARNING pointing at {@code --target=js} — mirror of
 * Script's UI002 ({@link KofInterpreter#warnUi002}), additive so
 * {@link DiagnosticCollector#hasErrors()} / {@link CompilationResult#success()}
 * stay green.
 *
 * <p>Scan is post-lowering (IR {@link KofCall} names), never a backend
 * {@code if feature == ui} branch (AGENTS rule 12).
 */
final class Ui001NativeWarn {

    private Ui001NativeWarn() {
    }

    static void reportIfNeeded(IRModule module, DiagnosticCollector diagnostics) {
        if (module == null || diagnostics == null) {
            return;
        }
        String first = firstKofUiCall(module);
        if (first == null) {
            return;
        }
        diagnostics.warning("", 0, 0, 0,
                "kof.ui is a no-op on the Native target (first call: " + first
                        + "); use --target=js for real UI",
                "UI001");
    }

    /** First {@code kof_ui_*} method name in IR order, or null. */
    static String firstKofUiCall(IRModule module) {
        if (module == null || module.classes() == null) {
            return null;
        }
        for (IRClass cls : module.classes()) {
            if (cls == null || cls.methods() == null) {
                continue;
            }
            for (IRMethod method : cls.methods()) {
                if (method == null || method.basicBlocks() == null) {
                    continue;
                }
                for (IRBasicBlock block : method.basicBlocks()) {
                    if (block == null || block.operations() == null) {
                        continue;
                    }
                    for (KofOperation op : block.operations()) {
                        if (op instanceof KofCall call) {
                            String name = call.methodName();
                            if (name != null && name.startsWith("kof_ui_")) {
                                return name;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }
}
