package dev.kof.compiler;

/**
 * Guard honesto do record {@code Result} INTEIRO no alvo Native
 * (D-FULL-PARITY-050 linha 1, follow-up da fatia A {@code process.run}).
 *
 * <p>No Native o {@code Result} é um objeto opaco do {@code RuntimeProcess}
 * (campos lidos pelos accessors {@code kof_process_result_stdout/stderr/
 * exitcode}); o alvo NÃO gera o {@code toString} de conteúdo que o JVM/JS
 * têm (§367). Medido no tip que pousou a fatia A: {@code println(r)} compilava
 * LIMPO e o binário morria com SIGSEGV (exit 139) sem imprimir nada — o
 * caminho de valueOf lia o header de um objeto que não tem vtable/typeId.
 * Compilar limpo e crashar em runtime é o pior caso de R6/Q7; este guard
 * transforma em diagnóstico {@code PROC001} no compile-time. A superfície
 * suportada no Native é o acesso aos campos.
 */
final class ProcessResultPrintGuard {

    private ProcessResultPrintGuard() {}

    /**
     * Emite {@code PROC001} e devolve {@code true} quando o valor é o record
     * do {@code process.run} no alvo Native; caso contrário devolve
     * {@code false} (o chamador segue o caminho normal).
     */
    static boolean refuseWholeResult(CompilerDriver driver, Type type,
                                     String file, int line, int column) {
        boolean mcu = driver.target == Target.NATIVE_RISCV32
                || driver.target == Target.NATIVE_MCU_ARM;
        if (!mcu || !KofProcess.isResult(type)) return false;
        if (driver.currentDiagnostics != null) {
            driver.currentDiagnostics.error(file, line, column, 0,
                    "printing/concatenating a whole process result is not supported on the"
                            + " freestanding MCU target — access .stdout/.stderr/.exitCode"
                            + " (JVM/JS and the host native targets have content toString)",
                    "PROC001");
        }
        return true;
    }
}
