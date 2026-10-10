package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * kof.ui layout primitives (docs/ui/architecture.md §2.8): the CSS-first
 * structural containers render as DOM on KofJS while JVM/Native execute the
 * same program as no-op handles. Split out of {@code ComponentCoreE2ETest}
 * (test-architecture Phase 3) so the component-core suite stays under the
 * 500-line gate.
 */
class UiLayoutRenderE2ETest extends ComponentCoreSupport {

    @Test
    void layoutPrimitivesRenderCssContainers(@TempDir Path tempDir) throws IOException {
        // Fase 4 (docs/ui/architecture.md §2.8): Box/Stack/Wrap/Grid/Spacer/
        // Center/Align are CSS-first containers; JVM/Native run them as no-ops.
        String program = """
            main() {
                var l1 = Label("a")
                var l2 = Label("b")
                var box = Box(listOf(l1, l2))
                var win = Window("App")
                win.bind(box)
                win.show()
            }
            """;
        Path layoutSrc = tempDir.resolve("layout.kf");
        Files.writeString(layoutSrc, program);
        runJvm(layoutSrc, tempDir.resolve("jvm-layout"), "");
        runNative(layoutSrc, tempDir.resolve("native-layout"), "");
        Path jsSource = tempDir.resolve("layout-js.kf");
        Files.writeString(jsSource, program);
        CompilationResult js = driver.compile(jsSource, tempDir.resolve("js-layout"), Target.JS);
        assertTrue(js.success(), "JS compilation should succeed: " + js.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        String html = dev.kof.runtime.KofJsRunner.runCaptureHtml(
                tempDir.resolve("js-layout").resolve("Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        assertNotNull(html, "The window should serialize to HTML");
        assertTrue(html.contains("kof-box"), "Box must render as a CSS container: " + html);
        assertTrue(html.contains(">a</span>") && html.contains(">b</span>"),
                "Box must contain its children: " + html);
    }

    @Test
    void scrollRendersScrollableContainer(@TempDir Path tempDir) throws IOException {
        // #702 (docs/ui/architecture.md §2.8): Scroll(children) — a bounded
        // scrollable container; CSS-first (overflow:auto) on KofJS, no-op on
        // JVM/Native, 1-arg List like Box.
        String program = """
            main() {
                var l1 = Label("a")
                var l2 = Label("b")
                var scroll = Scroll(listOf(l1, l2))
                var win = Window("App")
                win.bind(scroll)
                win.show()
            }
            """;
        Path layoutSrc = tempDir.resolve("scroll.kf");
        Files.writeString(layoutSrc, program);
        runJvm(layoutSrc, tempDir.resolve("jvm-scroll"), "");
        runNative(layoutSrc, tempDir.resolve("native-scroll"), "");
        Path jsSource = tempDir.resolve("scroll-js.kf");
        Files.writeString(jsSource, program);
        CompilationResult js = driver.compile(jsSource, tempDir.resolve("js-scroll"), Target.JS);
        assertTrue(js.success(), "JS compilation should succeed: " + js.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        String html = dev.kof.runtime.KofJsRunner.runCaptureHtml(
                tempDir.resolve("js-scroll").resolve("Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        assertNotNull(html, "The window should serialize to HTML");
        assertTrue(html.contains("kof-scroll"), "Scroll must render as a CSS container: " + html);
        assertTrue(html.contains(">a</span>") && html.contains(">b</span>"),
                "Scroll must contain its children: " + html);
    }
}
