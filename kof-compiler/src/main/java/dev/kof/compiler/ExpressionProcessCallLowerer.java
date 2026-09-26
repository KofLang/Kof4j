package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering do namespace process (process.*) no emitExpression.
 */
public final class ExpressionProcessCallLowerer {

    private ExpressionProcessCallLowerer() {}

    static int lower(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                      String owner, int localIdx, List<IRLocalVariable> locals) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    KofProcess.ProcessCall procCall = KofProcess.entryCall(mc.methodName(), argTypes);
    if (procCall != null && "kof_process_spawn".equals(procCall.function())) {
        // D-FULL-PARITY-050 row 1, slices B + D: spawn + handle ops emit for
        // real on the Native x86-64 host (RuntimeProcessSpawn, 26/09) AND on
        // the riscv64/aarch64 cross (NativeRiscvAsmProcessSpawn, slice D,
        // 26/09). Only the MCU (riscv32/cortex-m) keeps the honest PROC001 gap
        // — the process model there is bare-metal (R6, never a silent fallback).
        boolean spawnMcu = driver.target == Target.NATIVE_RISCV32
                || driver.target == Target.NATIVE_MCU_ARM;
        if (spawnMcu) {
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                        mc.position() != null ? mc.position().line() : 0,
                        mc.position() != null ? mc.position().column() : 0,
                        0,
                        "process.spawn: interactive stdin/stdout is supported on the JVM, JS and Native x86-64/riscv64/aarch64 targets; this target is an honest PROC001 gap",
                        "PROC001");
            }
            return localIdx;
        }
        // F10: process.spawn(program, args...) → monta List<String>
        // e chama kof_process_spawn (stdin/stdout vivos)
        localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
        Type listType = KofProcess.STRING_LIST;
        ops.add(new KofCall(listType, "kof_list_new", List.of(), listType, KofCallKind.FUNCTION));
        for (int i = 1; i < mc.arguments().size(); i++) {
            ops.add(new KofDup());
            localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(i), ops, owner, localIdx, locals);
            ops.add(new KofCall(listType, "kof_list_add",
                    List.of(BuiltinTypes.STRING), Type.PrimitiveType.VOID,
                    KofCallKind.INSTANCE));
        }
        ops.add(new KofCall(KofProcess.HANDLE, "kof_process_spawn",
                List.of(BuiltinTypes.STRING, KofProcess.STRING_LIST),
                KofProcess.HANDLE, KofCallKind.FUNCTION));
        return localIdx;
    }
    if (procCall != null) {
        // D-FULL-PARITY-050 row 1: run emits for real on x86-64 (RuntimeProcess)
        // AND on the riscv64/aarch64 cross (NativeRiscvAsmProcess, slice C).
        // spawn (slice B) is handled above: x86-64 real, cross/MCU PROC001.
        boolean cross = driver.target == Target.NATIVE_RISCV64
                || driver.target == Target.NATIVE_AARCH64;
        boolean isRun = "kof_process_run".equals(procCall.function());
        if (driver.target.isNative() && ((cross && !isRun)
                || "kof_process_spawn".equals(procCall.function()))) {
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                        mc.position() != null ? mc.position().line() : 0,
                        mc.position() != null ? mc.position().column() : 0,
                        0,
                        (cross ? "process.spawn on the riscv64/aarch64 native targets: "
                                : "process.spawn: interactive stdin/stdout is supported on")
                                + (cross ? "not landed yet (PROC001); process.run IS landed (slice C)"
                                : " the JVM and JS targets; Native is an honest PROC001 gap (slice B)"),
                        "PROC001");
            }
            return localIdx;
        }
        // process.run(program, args...) →
        // kof_process_run(program, List<String>) — TODOS os targets
        // (Native x86-64: RuntimeProcess, linha 1 do ledger D-FULL-PARITY-050)
        localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
        Type listType = KofProcess.STRING_LIST;
        ops.add(new KofCall(listType, "kof_list_new", List.of(), listType, KofCallKind.FUNCTION));
        for (int i = 1; i < mc.arguments().size(); i++) {
            ops.add(new KofDup());
            localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(i), ops, owner, localIdx, locals);
            ops.add(new KofCall(listType, "kof_list_add",
                    List.of(BuiltinTypes.STRING), Type.PrimitiveType.VOID,
                    KofCallKind.INSTANCE));
        }
        ops.add(new KofCall(KofProcess.RESULT, "kof_process_run",
                List.of(BuiltinTypes.STRING, KofProcess.STRING_LIST),
                KofProcess.RESULT, KofCallKind.FUNCTION));
    } else {
        // process.exit(code) — todos os targets
        KofProcess.ProcessCall exitCall = KofProcess.exitCall(argTypes);
        if (exitCall != null) {
            localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
            ops.add(new KofCall(new Type.ClassType("kof.process", "Process", List.of()),
                    exitCall.function(), exitCall.parameterTypes(), exitCall.returnType(),
                    KofCallKind.FUNCTION));
        } else if (driver.currentDiagnostics != null) {
            driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                    mc.position() != null ? mc.position().line() : 0,
                    mc.position() != null ? mc.position().column() : 0, 0,
                    "Cannot resolve method '" + mc.methodName() + "' on 'process' (valid: run, spawn, exit)",
                    "SEM025");
        }
    }
        return localIdx;
    }
}