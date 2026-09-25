package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering do namespace shell (shell.*) no emitExpression.
 *
 * Sugar over kof.process: `run` lowers onto the existing kof_process_run
 * binding (JVM+JS real, Native gated PROC001 — same honest gap as process.run);
 * `pipeline` needs live pipes: real on JVM and on the JS host
 * (KofJsProcessBridge chain + pump threads, 20/09), so Native alone hits
 * PROC001 at compile time exactly like the spawn gate does (never a raw call
 * that would ReferenceError — the §235 lesson).
 * `cmd` is an argv builder, `ok` is pure field/compare IR on the Result.
 */
public final class ExpressionShellCallLowerer {

    private ExpressionShellCallLowerer() {}

    static int lower(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                     String owner, int localIdx, List<IRLocalVariable> locals) {
        List<Type> argTypes = new ArrayList<>();
        for (ExpressionNode arg : mc.arguments()) {
            argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
        }
        KofShell.ShellCall call = KofShell.staticCall(mc.methodName(), argTypes);
        if (call == null) {
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(posFile(mc), posLine(mc), posCol(mc), 0,
                        "Cannot resolve method '" + mc.methodName()
                                + "' on 'shell' (valid: " + String.join(", ", KofShell.functions()) + ")",
                        "SEM025");
            }
            return localIdx;
        }
        // D-FULL-PARITY-050 row 2 CLOSED on native: run/cmd/ok/runWith/
        // pipeline emit for real on the x86-64 native target and on
        // riscv64/aarch64 (runWith cross = NativeRiscvAsmShell, argv-first;
        // inherited cwd/env byte-parity, a non-empty cwd/env is an honest
        // Result failure; runWith x86-64 = kof_shell_runwith: argv split +
        // chdir + additive setenv in the child hook; pipeline x86-64 =
        // kof_shell_pipeline: kernel pipe-chaining, last-stage capture —
        // JVM-parity goldens; pipeline cross = NativeRiscvAsmPipeline).
        // Every face landed; no shell call is gated on the supported native
        // targets anymore. When a new target (MCU/riscv32) needs per-face
        // gates, they come back here — refusals name the face that landed (R6).
        switch (mc.methodName()) {
            case "run" -> {
                localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0),
                        ops, owner, localIdx, locals);
                if (mc.arguments().size() == 2) {
                    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(1),
                            ops, owner, localIdx, locals);
                } else {
                    ops.add(new KofCall(KofProcess.STRING_LIST, "kof_list_new", List.of(),
                            KofProcess.STRING_LIST, KofCallKind.FUNCTION));
                }
                ops.add(new KofCall(KofProcess.RESULT, "kof_process_run",
                        List.of(BuiltinTypes.STRING, KofProcess.STRING_LIST),
                        KofProcess.RESULT, KofCallKind.FUNCTION));
            }
            case "cmd" -> {
                localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0),
                        ops, owner, localIdx, locals);
                localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(1),
                        ops, owner, localIdx, locals);
                ops.add(new KofCall(KofShell.STRING_LIST, "kof_shell_argv",
                        List.of(BuiltinTypes.STRING, KofProcess.STRING_LIST),
                        KofShell.STRING_LIST, KofCallKind.FUNCTION));
            }
            case "pipeline" -> {
                localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0),
                        ops, owner, localIdx, locals);
                ops.add(new KofCall(KofProcess.RESULT, "kof_shell_pipeline",
                        List.of(KofShell.STRING_LIST_LIST), KofProcess.RESULT,
                        KofCallKind.FUNCTION));
            }
            case "runWith" -> {
                for (int i = 0; i < 3; i++) {
                    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(i),
                            ops, owner, localIdx, locals);
                }
                ops.add(new KofCall(KofProcess.RESULT, "kof_shell_runwith",
                        List.of(KofProcess.STRING_LIST, BuiltinTypes.STRING, KofShell.MAP_SS),
                        KofProcess.RESULT, KofCallKind.FUNCTION));
            }
            default -> {
                // "ok": shell.ok(result) == result.exitCode == 0 — pure IR, no binding
                localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0),
                        ops, owner, localIdx, locals);
                ops.add(new KofLoadField(KofProcess.RESULT, "exitCode",
                        Type.PrimitiveType.INT));
                ops.add(KofLoadLiteral.ofInt(0));
                ops.add(new KofBinary(KofBinaryOp.EQ, Type.PrimitiveType.INT));
            }
        }
        return localIdx;
    }

    private static String posFile(MethodCallExpr mc) {
        return mc.position() != null ? mc.position().file() : "";
    }

    private static int posLine(MethodCallExpr mc) {
        return mc.position() != null ? mc.position().line() : 0;
    }

    private static int posCol(MethodCallExpr mc) {
        return mc.position() != null ? mc.position().column() : 0;
    }
}
