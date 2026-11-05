package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §779 — {@code class C implements I} (and {@code record R(...) implements I},
 * {@code interface J extends I}) where {@code I} is a Kof interface declared
 * in a package emitted the bare simple name in the class file
 * ({@code implements PIface}), so the program died with
 * {@code NoClassDefFoundError: PIface} at first use, from the same package or
 * from another one. The interface list now resolves registry-first, like the
 * superclass already did.
 */
class PackageInterfaceImplementsE2ETest {

    @TempDir Path tmp;

    @Test
    void implementsAPackageInterfaceFromTheSameAndAnotherPackage() throws Exception {
        Path src = tmp.resolve("src");
        Path lib = src.resolve("kernel/p");
        Path app = src.resolve("app");
        Files.createDirectories(lib);
        Files.createDirectories(app);
        Files.writeString(lib.resolve("P.kf"), """
            package kernel.p

            interface PIface {
                String f()
            }

            interface Named extends PIface {
                String name()
            }

            class SameImpl implements PIface {
                String f() { return "same" }
            }

            record Tag(String v) implements PIface {
                String f() { return "tag " + v }
            }

            String call(PIface p) { return p.f() }
            """);
        Files.writeString(app.resolve("Main.kf"), """
            package app

            import kernel.p.*

            class OtherImpl implements Named {
                String f() { return "other" }
                String name() { return "n" }
            }

            main() {
                println(call(SameImpl()))
                println(call(Tag("x")))
                val o = OtherImpl()
                println(call(o) + " " + o.name())
            }
            """);
        Path out = tmp.resolve("out");
        CompilationResult r = new CompilerDriver().compileSources(
                List.of(lib.resolve("P.kf"), app.resolve("Main.kf")), out, Target.JVM, src);
        assertTrue(r.success(), "must compile: " + r.diagnostics().getDiagnostics());
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true));
        try (URLClassLoader cl = new URLClassLoader(new URL[]{out.toUri().toURL()},
                getClass().getClassLoader())) {
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(old);
        }
        assertEquals("same\ntag x\nother n\n", buf.toString());
    }
}
