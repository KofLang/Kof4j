package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §734 (JS backend): a pure-Kof function whose body is
 * {@code if (A) { x } else if (B) { throw } } followed by more statements
 * returned {@code undefined} on the JS target (JVM/Script/native x86 correct).
 *
 * <p>Root: the IR has a SHARED end label — the enclosing if's then exits with
 * {@code Jump(end)} and a nested {@code if (B) { throw }} (its then ends
 * unconditionally, so it takes the {@code JsIfThrowElse} path) consumed that
 * {@code Label(end)} as its OWN end. The enclosing else then ran to the method
 * epilogue and absorbed the trailing {@code return}, so on the {@code A} path
 * the else was skipped and the function fell off the end → {@code undefined}.
 * Fix: track the enclosing if's end label(s) (the then's exit jump targets) and
 * forbid a nested if from consuming them (the {@code §380} guard is the
 * analogous rule for the false label).
 */
class JsIfElseIfThrowE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            Int comp3Width(Int digits) {
                var nibbles = digits + 1
                if (nibbles % 2 == 1) {
                    nibbles = nibbles + 1
                }
                return nibbles / 2
            }
            String unpackComp3(List<Int> bytes, Int digits, Int scale) {
                var total = comp3Width(digits) * 2
                var nibs: List<Int> = listOf()
                var i = 0
                while (i < bytes.size) {
                    var b = bytes.get(i)
                    nibs.add(b / 16)
                    nibs.add(b % 16)
                    i = i + 1
                }
                var sign = nibs.get(total - 1)
                var negative = false
                if (sign == 11 || sign == 13) {
                    negative = true
                } else if (sign != 10 && sign != 12 && sign != 14 && sign != 15) {
                    throw "bad sign nibble: " + sign
                }
                var all = ""
                var j = total - 1 - digits
                while (j < total - 1) {
                    var d = nibs.get(j)
                    all = all + ((d + 48) as Char)
                    j = j + 1
                }
                var out = all
                if (scale > 0) {
                    out = all.substring(0, all.length - scale) + "." + all.substring(all.length - scale, all.length)
                }
                if (negative) {
                    out = "-" + out
                }
                return out
            }
            String label(Int s) {
                var neg = false
                if (s == 11) {
                    neg = true
                } else if (s == 10) {
                    throw "bad"
                }
                return "label" + neg
            }
            Int chain(Int s) {
                var r = 0
                if (s == 1) {
                    r = 10
                } else if (s == 2) {
                    throw "bad2"
                } else {
                    r = 30
                }
                r = r + 1
                return r
            }
            Int retChain(Int s) {
                var r = 0
                if (s == 1) {
                    r = 10
                } else if (s == 2) {
                    return 20
                }
                r = r + 1
                return r
            }
            Int nestedThen(Int s) {
                var r = 0
                if (s == 1) {
                    if (s != 99) {
                        r = 5
                    }
                    r = r + 2
                } else if (s == 2) {
                    throw "x"
                }
                return r + 1
            }
            Int loopSeq(Int s) {
                var r = 0
                while (r < s) {
                    if (r == 1) {
                        break
                    } else if (r == 2) {
                        throw "y"
                    }
                    r = r + 1
                }
                return r
            }
            Int loopTrail(Int n) {
                var r = 0
                while (r < 5) {
                    if (r == n) {
                        r = r + 10
                        if (r > 12) { return r }
                    } else if (r == 4) {
                        break
                    }
                    r = r + 1
                }
                return r
            }
            Int chain4(Int s) {
                var r = 0
                if (s == 1) { r = 1 }
                else if (s == 2) { r = 2 }
                else if (s == 3) { return 30 }
                else if (s == 4) { throw "e4" }
                else { r = 9 }
                return r + 100
            }
            main() {
                println(unpackComp3(listOf(1, 35, 77), 4, 2))
                println(unpackComp3(listOf(4, 35, 77), 4, 2))
                println(label(11))
                println(label(5))
                try { println(label(10)) } catch (String e) { println("caught") }
                println(chain(1))
                println(chain(3))
                try { println(chain(2)) } catch (String e) { println("chain2caught") }
                println(retChain(1))
                println(retChain(2))
                println(retChain(5))
                println(nestedThen(1))
                println(nestedThen(5))
                println(loopSeq(0))
                println(loopSeq(2))
                println(loopTrail(0))
                println(loopTrail(9))
                println(chain4(1))
                println(chain4(2))
                println(chain4(3))
                println(chain4(7))
                try { println(chain4(4)) } catch (String e) { println("e4c") }
            }
            """;

    private static final String GOLDEN = String.join("\n",
            "-12.34", "-42.34", "labeltrue", "labelfalse", "caught",
            "11", "31", "chain2caught",
            "11", "20", "1", "8", "1", "0", "1",
            "11", "4",
            "101", "102", "30", "109", "e4c");

    @Test
    void jvmIsTheContractForJsIfElseIfThrow() throws Exception {
        assertEquals(GOLDEN, runJvm(), "o oráculo JVM mudou — revise o caso");
    }

    @Test
    void scriptMatchesTheJvmOnJsIfElseIfThrow() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = driver.interpret(
                java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(GOLDEN, r.stdout().strip());
    }

    @Test
    void jsMatchesTheJvmOnJsIfElseIfThrow() throws Exception {
        Path dir = tmp.resolve("js");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JS);
        assertTrue(r.success(), () -> "JS compile: " + r.diagnostics().getDiagnostics());
        var so = new java.io.ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), so,
                new java.io.ByteArrayInputStream(new byte[0]), so);
        assertEquals(0, exit, () -> "js saída: " + so);
        assertEquals(GOLDEN, so.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void nativeX86MatchesTheJvmOnJsIfElseIfThrow() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        Path dir = tmp.resolve("x86");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.NATIVE);
        assertTrue(r.success(), () -> "x86 compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(out.resolve("Default/Main").toString()).redirectErrorStream(true).start();
        String o = NativeRiscv64E2ETest.runBounded(p, "x86");
        assertEquals(0, p.exitValue(), () -> "x86 saída: " + o);
        assertEquals(GOLDEN, o);
    }

    private String runJvm() throws Exception {
        Path dir = tmp.resolve("jvm");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JVM);
        assertTrue(r.success(), () -> "JVM compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                "-cp", out.toString(), "Default.Main").redirectErrorStream(true).start();
        String o = NativeRiscv64E2ETest.runBounded(p, "jvm");
        assertEquals(0, p.exitValue(), () -> "oráculo JVM: " + o);
        return o;
    }

    private static boolean has(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c)
                        .redirectErrorStream(true).start();
                if (p.waitFor() != 0) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }
}