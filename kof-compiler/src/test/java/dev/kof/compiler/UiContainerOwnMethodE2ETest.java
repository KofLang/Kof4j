package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * issue #991 — a method of the OWNING class called inside
 * {@code Column(listOf(heading("ok")))} stopped resolving after the #827
 * change: the {@code Column}/{@code Row} branch of {@code BuiltinUiCallTyper}
 * returned the container type WITHOUT inferring its argument, so the inner
 * {@code heading(...)} call never resolved against the owner's members and
 * the lowering emitted it against the WRONG owner — JVM
 * {@code NoSuchMethodError: Default.Main.heading}, JS
 * {@code ReferenceError: heading is not defined}. The workaround is an
 * explicit {@code this.heading(...)}; the same form outside Column, or the
 * list stored in a variable first, never broke.
 *
 * <p>Fix: the Column/Row branch infers its argument like every other
 * container (View/Style/Button always did).
 *
 * <p>RED-first: the JVM face was RED pre-fix (NoSuchMethodError at run).
 */
class UiContainerOwnMethodE2ETest extends UiSupport {

    private static final String PROGRAM = """
        import kof.ui.*

        class Guide {
            Column root
            public constructor() {
                root = Column(listOf(heading("ok")))
            }
            private Label heading(String text) { return Label(text) }
        }

        main() {
            var guide = Guide()
            println("ok")
        }
        """;

    @Test
    void ownMethodInsideColumnResolvesOnJvmAndJs(@TempDir Path tempDir) throws IOException {
        both(tempDir, "own-method-column", PROGRAM, "ok");
    }

    @Test
    void ownMethodInsideRowResolvesOnJvm(@TempDir Path tempDir) throws IOException {
        String row = PROGRAM.replace("Column(listOf(heading(\"ok\")))",
                "Row(listOf(heading(\"ok\")))")
                .replace("Column root", "Row root");
        both(tempDir, "own-method-row", row, "ok");
    }

    @Test
    void ownMethodOutsideColumnStillResolves(@TempDir Path tempDir) throws IOException {
        // control from the issue: the call outside Column never broke
        String outside = """
            import kof.ui.*

            class Guide {
                public Label heading(String text) { return Label(text) }
            }

            main() {
                var guide = Guide()
                println(guide.heading("ok").text())
                println("done")
            }
            """;
        Path dir = tempDir.resolve("outside");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), outside);
        CompilationResult r = new CompilerDriver()
                .compile(dir.resolve("Main.kf"), dir.resolve("out"), Target.JVM);
        assertTrue(r.success(), "got: " + r.diagnostics().getDiagnostics());
        // the exact Label.text() surface varies; the compile itself is the pin
    }
}
