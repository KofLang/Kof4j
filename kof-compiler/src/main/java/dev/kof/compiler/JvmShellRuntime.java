package dev.kof.compiler;

/**
 * kof.shell (TIER 6) — runtime JVM. Executa um comando através de `sh -c`
 * (pipes/glob/redireção) reusando `kof_process_run`; devolve o stdout.
 */
final class JvmShellRuntime {

    private JvmShellRuntime() {}

    static String source() {
        return """
                public static String kof_shell_run(String cmd) {
                    try {
                        ProcessResult r = kof_process_run("/bin/sh", java.util.List.of("-c", cmd));
                        return r.stdout;
                    } catch (Exception e) {
                        return "";
                    }
                }

""";
    }
}