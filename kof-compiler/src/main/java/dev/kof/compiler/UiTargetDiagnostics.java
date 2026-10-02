package dev.kof.compiler;

import java.util.List;

/**
 * UI001 (R6): {@code kof.ui} não renderiza no alvo Native — era um no-op
 * silencioso. Este passe espelha o UI002 (Script) no plano de compilação:
 * quando o alvo é Native e a IR contém uma chamada {@code kof.ui}, emite UM
 * WARNING aditivo (não quebra o build — {@code success()} só olha erros) que
 * aponta para {@code --target=js}. O corpus {@code kof.ui} é KofJS.
 */
final class UiTargetDiagnostics {

    private UiTargetDiagnostics() {}

    /** True se a IR contém qualquer chamada do pacote {@code kof.ui}. */
    static boolean usesUi(IRModule module) {
        if (module == null) {
            return false;
        }
        for (IRClass cls : module.classes()) {
            for (IRMethod method : cls.methods()) {
                for (IRBasicBlock block : method.basicBlocks()) {
                    for (KofOperation op : block.operations()) {
                        if (op instanceof KofCall call && isUiCall(call)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean isUiCall(KofCall call) {
        return call.ownerType() instanceof Type.ClassType owner
                && "kof.ui".equals(owner.packageName());
    }

    /**
     * Emite o WARNING UI001 uma única vez quando o alvo é Native e a IR usa
     * {@code kof.ui}. Aditivo: nunca lança erro (R6 — diagnosticado, não
     * silencioso).
     */
    static void warnIfNative(CompilerDriver driver, IRModule module, DiagnosticCollector diagnostics) {
        if (diagnostics == null || driver == null || !driver.target.isNative() || !usesUi(module)) {
            return;
        }
        String file = driver.currentSourceName == null ? "" : driver.currentSourceName;
        diagnostics.warning(file, 0, 0, 0,
                "kof.ui does not render on the Native target (UI001) — no-op; "
                        + "run with --target=js for real UI",
                "UI001");
    }
}
