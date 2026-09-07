package dev.kof.compiler;

import java.util.List;


/**
 * kof.process — multiplatform process abstraction.
 *
 * The same Kof code runs on every backend; each runtime implements the
 * ProcessBuilder/fork/execve/child_process mechanics behind one API:
 *
 *   var result = process.run("git", "status")
 *   println(result.stdout)
 *   println(result.exitCode)
 */
public final class KofProcess {

    private KofProcess() {}

    static final Type RESULT = new Type.ClassType("kof.process", "Result", List.of());
    static final Type STRING_LIST = new Type.ClassType("kof", "List", List.of(BuiltinTypes.STRING));

    static boolean isResult(Type t) {
        return RESULT.equals(t);
    }

    /** Fields exposed by the process result object. */
    static Type fieldType(String name) {
        return switch (name) {
            case "stdout", "stderr" -> BuiltinTypes.STRING;
            case "exitCode" -> Type.PrimitiveType.INT;
            default -> Type.UnknownType.UNKNOWN;
        };
    }

    static boolean isField(String name) {
        return "stdout".equals(name) || "stderr".equals(name) || "exitCode".equals(name);
    }

    record ProcessCall(String function, Type returnType, List<Type> parameterTypes) {
    }

    /** process.run(program, args...) — the only entry point for now. */
    static ProcessCall runCall(List<Type> argTypes) {
        // [String, String, ...] or [String] — at least the program
        if (argTypes.isEmpty()) return null;
        Type head = argTypes.get(0);
        if (!BuiltinTypes.isString(head)) return null;
        for (int i = 1; i < argTypes.size(); i++) {
            if (!BuiltinTypes.isString(argTypes.get(i))) return null;
        }
        return new ProcessCall("kof_process_run", RESULT, argTypes);
    }

    /**
     * process.exit(code) — termina o programa com o código dado em todos
     * os targets (JVM: System.exit; Native: syscall exit; JS: sentinel
     * capturado pelo runner). Primitivo de scripting e do harness de testes.
     */
    static ProcessCall exitCall(List<Type> argTypes) {
        if (argTypes.size() != 1) return null;
        Type t = argTypes.get(0);
        if (!(t instanceof Type.PrimitiveType pt)) return null;
        if (!"int".equals(Type.canonicalPrimitiveName(pt.name()))) return null;
        return new ProcessCall("kof_process_exit", Type.PrimitiveType.VOID,
                List.of(Type.PrimitiveType.INT));
    }

    // ── process.spawn — processo com stdin/stdout vivos (F10) ──────
    //
    //   var h = process.spawn("sh", "-c", "read x; echo got=$x")
    //   h.write("oi")           // escreve no stdin (+\n)
    //   var line = h.readLine() // uma linha do stdout (bloqueia)
    //   var code = h.exitCode() // -1=erro, MIN=ainda vivo
    //   h.kill()                // destrói o processo
    //   h.alive()               // Bool

    // o handle é opaco na IR; em runtime é um Long boxed (registry do
    // KofRuntime) — java.lang.Long no descriptor evita checkcast quebrado
    static final Type HANDLE = new Type.ClassType("java.lang", "Long", List.of());

    static boolean isHandle(Type t) {
        return HANDLE.equals(t);
    }

    /** process.spawn(program, args...) — stdin/stdout vivos (F10). */
    static ProcessCall spawnCall(List<Type> argTypes) {
        if (argTypes.isEmpty()) return null;
        if (!BuiltinTypes.isString(argTypes.get(0))) return null;
        for (int i = 1; i < argTypes.size(); i++) {
            if (!BuiltinTypes.isString(argTypes.get(i))) return null;
        }
        return new ProcessCall("kof_process_spawn", HANDLE, argTypes);
    }

    /** process.run vs process.spawn — o nome do método decide (ambos
     *  aceitam (String, String...); sem o nome o runCall engolia o spawn). */
    static ProcessCall entryCall(String methodName, List<Type> argTypes) {
        if ("spawn".equals(methodName)) return spawnCall(argTypes);
        return runCall(argTypes);
    }

    /** Métodos de instância sobre o handle de spawn. */
    static ProcessCall handleMethod(String name, List<Type> argTypes) {
        switch (name) {
            case "write" -> {
                if (argTypes.size() != 1 || !BuiltinTypes.isString(argTypes.get(0))) return null;
                return new ProcessCall("kof_spawn_write", Type.PrimitiveType.VOID,
                        List.of(HANDLE, BuiltinTypes.STRING));
            }
            case "readLine" -> {
                if (!argTypes.isEmpty()) return null;
                return new ProcessCall("kof_spawn_read_line", BuiltinTypes.STRING, List.of(HANDLE));
            }
            case "exitCode" -> {
                if (!argTypes.isEmpty()) return null;
                return new ProcessCall("kof_spawn_exit_code", Type.PrimitiveType.INT, List.of(HANDLE));
            }
            case "kill" -> {
                if (!argTypes.isEmpty()) return null;
                return new ProcessCall("kof_spawn_kill", Type.PrimitiveType.VOID, List.of(HANDLE));
            }
            case "alive" -> {
                if (!argTypes.isEmpty()) return null;
                return new ProcessCall("kof_spawn_alive", Type.PrimitiveType.BOOL, List.of(HANDLE));
            }
            default -> {
                return null;
            }
        }
    }
}