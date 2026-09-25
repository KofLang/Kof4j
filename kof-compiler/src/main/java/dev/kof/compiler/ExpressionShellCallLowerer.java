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
        if (driver.target.isNative()) {
            boolean cross = driver.target == Target.NATIVE_RISCV64
                    || driver.target == Target.NATIVE_AARCH64;
            // D-FULL-PARITY-050 row 2: run/cmd/ok emit for real on the x86-64
            // native target and, since row 1 slice C landed process.run on the
            // cross, also on riscv64/aarch64. Slice B: runWith landed on the
            // cross (NativeRiscvAsmShell, argv-first; inherited cwd/env are
            // byte-parity, a non-empty cwd/env is an honest Result failure) —
            // x86-64 runWith and pipeline everywhere are still slice B. Every
            // refusal names the face that landed, never a raw call that ends in
            // an ld error (R6).
            boolean pipeline = mc.methodName().equals("pipeline");
            boolean runWith = mc.methodName().equals("runWith");
            if (pipeline || (runWith && !cross)) {
                if (driver.currentDiagnostics != null) {
                    driver.currentDiagnostics.error(posFile(mc), posLine(mc), posCol(mc), 0,
                            "shell." + mc.methodName() + ": " + (pipeline
                                    ? "chained pipes are slice B"
                                    : "runWith on the x86-64 native target is slice B")
                                    + " (JVM and JS support the full shell surface; Native x86-64"
                                    + " and riscv64/aarch64 landed run/cmd/ok; the cross landed runWith)",
                            "PROC001");
                }
                return localIdx;
            }
        }
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
