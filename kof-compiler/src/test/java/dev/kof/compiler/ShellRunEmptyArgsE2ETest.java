package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #774 — {@code shell.run(program, args)} must accept an inline empty
 * {@code listOf()}, exactly like the one-arg form.
 *
 * The 2-arg overload matched {@code STRING_LIST} by exact equality, so an
 * empty list literal (inferred {@code List<Object>}) failed overload resolution
 * with {@code SEM025} — while the 3-arg {@code runWith} already accommodated it
 * via {@code BuiltinTypes.isList}. A vacuous argv is legitimate (it means "no
 * arguments"): the fix mirrors {@code runWith} so the call resolves and runs.
 */
class ShellRunEmptyArgsE2ETest extends ShellSupport {

    @Test
    void runWithInlineEmptyListResolvesAndRuns() throws Exception {
        assertJvmJsParity("""
            main() {
                var r = shell.run("false", listOf())
                println(r.exitCode)
                println(shell.ok(r))
            }
            """, "1", "false");
    }

    @Test
    void cmdWithInlineEmptyListBuildsSingleElementArgv() throws Exception {
        assertJvmJsParity("""
            main() {
                var argv = shell.cmd("echo", listOf())
                println(argv.size())
                println(argv.get(0))
            }
            """, "1", "echo");
    }

    @Test
    void runWithInlineEmptyListEqualsOneArgForm() throws Exception {
        Files.writeString(tmp.resolve("S.kf"), """
            main() {
                var a = shell.run("true")
                var b = shell.run("true", listOf())
                println(a.exitCode == b.exitCode)
                println(shell.ok(b))
            }
            """);
        Run jvm = runJvm(tmp.resolve("S.kf"), tmp.resolve("o-jvm"));
        assertTrue(jvm.ok(), () -> "JVM failed: " + jvm.output());
        assertTrue(jvm.output().contains("true"), () -> "expected 'true' in: " + jvm.output());
    }
}
