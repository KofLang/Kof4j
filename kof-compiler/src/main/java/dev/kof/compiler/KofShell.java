package dev.kof.compiler;

import java.util.List;

/**
 * TIER 6 — `kof.shell`: execução de comando através de shell (`sh -c`),
 * com pipes/glob/redireção — sobre `kof.process` (nunca reimplementar).
 * JVM-first (SHL001 nos demais targets até o runtime fechar).
 */
final class KofShell {
    private KofShell() {}

    static final Type SHELL = new Type.ClassType("kof.shell", "Shell", List.of());
    private static final Type STR = BuiltinTypes.STRING;

    static boolean isShellNamespace(String name) {
        return "shell".equals(name);
    }

    static boolean isShellMethod(String name) {
        return "run".equals(name);
    }

    record ShellCall(String function, Type returnType, List<Type> parameterTypes) {}

    static boolean supportedOn(Target target) {
        return target == Target.JVM || target == Target.ANDROID;
    }

    static ShellCall staticCall(String name, List<Type> argTypes) {
        return switch (name) {
            case "run" -> argTypes.size() == 1
                    ? new ShellCall("kof_shell_run", STR, List.of(STR)) : null;
            default -> null;
        };
    }
}