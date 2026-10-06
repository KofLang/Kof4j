package dev.kof.compiler;

import java.util.List;


/**
 * kof.shell — idiomatic shell over kof.process (universal plan Stage 2, row 2.2).
 *
 * Pure lowering: `run` reuses kof_process_run verbatim, `cmd` builds the argv
 * list, `ok` is a field/compare on kof.process's Result, and only `pipeline`
 * needs a new runtime binding (JVM; JS/Native hit the inherited PROC001
 * live-pipe gap at compile time — see future/shell-plan.md §4).
 *
 *   var r = shell.run("git", ["status", "--short"])
 *   if (shell.ok(r)) println(r.stdout)
 *   var out = shell.pipeline([["ls", "-1"], ["wc", "-l"]]).stdout
 *
 * The Result type IS kof.process's Result (one shape, never a fork).
 */
public final class KofShell {

    private KofShell() {}

    /** Namespace id, literal for the R1 boundary ledger (scripts/stdlib_boundary.txt). */
    static final String NAMESPACE = "kof.shell";

    /** argv builder result: [program] + args — always a List, never a string. */
    static final Type STRING_LIST = KofProcess.STRING_LIST;
    static final Type STRING_LIST_LIST =
            new Type.ClassType("kof", "List", List.of(STRING_LIST));
    static final Type MAP_SS =
            new Type.ClassType("kof", "Map", List.of(BuiltinTypes.STRING, BuiltinTypes.STRING));

    record ShellCall(String function, Type returnType, List<Type> parameterTypes) {
    }

    /** Names accepted by dispatch (catalog for LSP; mirrors KofProcess.functions()). */
    static List<String> functions() { return List.of("cmd", "run", "runWith", "pipeline", "ok"); }

    static ShellCall staticCall(String methodName, List<Type> argTypes) {
        switch (methodName) {
            case "cmd" -> {
                if (argTypes.size() != 2) return null;
                if (!BuiltinTypes.isString(argTypes.get(0))) return null;
                // isList (not equals STRING_LIST): um listOf() vazio inferido
                // List<Object> é argv legítimo — mesma acomodação do runWith.
                if (!BuiltinTypes.isList(argTypes.get(1))) return null;
                return new ShellCall("kof_shell_argv", STRING_LIST,
                        List.of(BuiltinTypes.STRING, STRING_LIST));
            }
            case "run" -> {
                // run(program) or run(program, args) — lowers onto kof_process_run
                if (argTypes.size() == 1 && BuiltinTypes.isString(argTypes.get(0))) {
                    return new ShellCall("kof_process_run", KofProcess.RESULT,
                            List.of(BuiltinTypes.STRING, STRING_LIST));
                }
                // isList (not equals STRING_LIST): um listOf() vazio inferido
                // List<Object> é argv legítimo (sem argumentos), não SEM025 —
                // mesma acomodação do runWith.
                if (argTypes.size() == 2 && BuiltinTypes.isString(argTypes.get(0))
                        && BuiltinTypes.isList(argTypes.get(1))) {
                    return new ShellCall("kof_process_run", KofProcess.RESULT,
                            List.of(BuiltinTypes.STRING, STRING_LIST));
                }
                return null;
            }
            case "runWith" -> {
                // runWith(argv, cwd, env) — 2.2.3: cwd ""/missing dir = honest
                // Result exit -1; env is ADDITIVE (child inherits the parent and
                // the map's keys override) — never a silent environment wipe.
                if (argTypes.size() != 3) return null;
                // isList (not equals STRING_LIST): um listOf() vazio inferido
                // List<Object> é argv legitimo — a vacuidade falha ALTO no
                // runtime com "empty argv", não com SEM025.
                if (!BuiltinTypes.isList(argTypes.get(0))) return null;
                if (!BuiltinTypes.isString(argTypes.get(1))) return null;
                if (!BuiltinTypes.isMap(argTypes.get(2))) return null;
                return new ShellCall("kof_shell_runwith", KofProcess.RESULT,
                        List.of(STRING_LIST, BuiltinTypes.STRING, MAP_SS));
            }
            case "pipeline" -> {
                if (argTypes.size() != 1) return null;
                if (!STRING_LIST_LIST.equals(argTypes.get(0))) return null;
                return new ShellCall("kof_shell_pipeline", KofProcess.RESULT,
                        List.of(STRING_LIST_LIST));
            }
            case "ok" -> {
                if (argTypes.size() != 1) return null;
                if (!KofProcess.isResult(argTypes.get(0))) return null;
                // pure IR (field/compare), not a runtime function
                return new ShellCall("ok", Type.PrimitiveType.BOOL,
                        List.of(KofProcess.RESULT));
            }
            default -> {
                return null;
            }
        }
    }
}
