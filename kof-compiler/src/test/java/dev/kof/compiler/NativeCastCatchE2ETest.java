package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class NativeCastCatchE2ETest {

    private static final String SRC = """
            class Animal {
                public constructor() {}
            }

            class Dog extends Animal {
                public constructor() {
                    super()
                }
            }

            main() {
                Object n = 42
                try {
                    String s = n as String
                    println("sem erro " + s)
                } catch (Exception e) {
                    println("string: " + e)
                }
                Animal a = Animal()
                try {
                    Dog d = a as Dog
                    println("sem erro dog")
                } catch (Exception e) {
                    println("dog pego")
                }
                Animal ok = Dog()
                Dog d2 = ok as Dog
                println(d2 instanceof Dog)
                println("fim")
            }
            """;

    @TempDir
    Path tmp;

    @Test
    void failingCastIsCatchableOnNative() throws Exception {
        Assumptions.assumeTrue(hasToolchain(), "native toolchain absent");
        Path file = tmp.resolve("x86").resolve("Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, SRC);
        Path out = tmp.resolve("x86-out");
        CompilationResult r = new CompilerDriver().compile(file, out, Target.NATIVE);
        assertTrue(r.success(), String.valueOf(r.diagnostics().getDiagnostics()));
        Process p = new ProcessBuilder(out.resolve("Default/Main").toString()).redirectErrorStream(true).start();
        String o = NativeRiscv64E2ETest.runBounded(p, "x86");
        assertEquals(0, p.exitValue(), o);
        assertEquals("string: type cast failed\ndog pego\ntrue\nfim", o.strip());
    }

    private static boolean hasToolchain() {
        try {
            return new ProcessBuilder("sh", "-c", "command -v as && command -v ld").start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
