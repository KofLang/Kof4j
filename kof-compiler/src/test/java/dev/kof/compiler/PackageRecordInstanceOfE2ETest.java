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
 * §776 — {@code v instanceof R} and {@code v as R} for a record {@code R}
 * declared in the current package ({@code package kernel.db}) emitted the
 * unqualified class name ({@code instanceof DbNullBind}), so the program died
 * with {@code NoClassDefFoundError: DbNullBind}. The lowering now qualifies
 * the target through the analyzer-aware {@code CompilerTypes.toType}, like
 * every other type reference of the module.
 */
class PackageRecordInstanceOfE2ETest {

    @TempDir Path tmp;

    @Test
    void instanceofAndCastToAPackageRecordUseTheQualifiedName() throws Exception {
        Path dir = tmp.resolve("src/kernel/db");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), """
            package kernel.db

            record DbNullBind(Int sqlType)

            String kind(Object v) {
                if (v instanceof DbNullBind) {
                    val n = v as DbNullBind
                    return "null-bind " + n.sqlType
                }
                return "value"
            }

            main() {
                println(kind(DbNullBind(4)))
                println(kind("x"))
            }
            """);
        Path out = tmp.resolve("out");
        CompilationResult r = new CompilerDriver().compileSources(
                List.of(dir.resolve("Main.kf")), out, Target.JVM, tmp.resolve("src"));
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
        assertEquals("null-bind 4\nvalue\n", buf.toString());
    }

    @Test
    void aDefaultPackageFileCastsToAClassImportedByWildcard() throws Exception {
        // enois G379: a test file (default package) with `import moderation.*`
        // cast to a moderation class and died with NoClassDefFoundError.
        Path src = tmp.resolve("src");
        Path pkg = src.resolve("moderation");
        Files.createDirectories(pkg);
        Files.writeString(pkg.resolve("Images.kf"), """
            package moderation

            class ModerationImages {
                String kind() { return "base" }
            }
            class ModerationMediaImages extends ModerationImages {
                String extra() { return "extra" }
            }
            """);
        Files.writeString(src.resolve("Main.kf"), """
            import moderation.*

            ModerationImages pick() { return ModerationMediaImages() }

            main() {
                val b = pick()
                val m = b as ModerationMediaImages
                println(m.extra())
                println(b instanceof ModerationMediaImages)
            }
            """);
        Path out = tmp.resolve("out2");
        CompilationResult r = new CompilerDriver().compileSources(
                List.of(pkg.resolve("Images.kf"), src.resolve("Main.kf")), out, Target.JVM, src);
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
        assertEquals("extra\ntrue\n", buf.toString());
    }
}
