package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class UiA11yDefaultsReuseE2ETest extends ComponentCoreSupport {

    private static final String NODES = """
            const kofA11yNodes = (tag) => Object.values(window.__kofNodes)
                .filter((n) => String(n.tagName).toUpperCase() === tag);
            const kofA11ySvg = (n) => "hidden=" + (n.getAttribute("aria-hidden") === "true")
                + " role=" + n.getAttribute("role");
            """;

    @Test
    void iconIsDecorativeByDefault(@TempDir Path tempDir) throws IOException {
        String program = """
                main() {
                    var win = Window("App")
                    win.bind(Icon("star"))
                    win.bind(Icon("trash", 16))
                    win.show()
                    println("done")
                }
                """;
        String probe = NODES + """
                console.log(kofA11yNodes("SVG").map(kofA11ySvg).join(" | "));
                """;
        assertEquals("done\nhidden=true role=null | hidden=true role=null",
                runJsProbe(tempDir, "icondeco", program, probe),
                "an Icon is decorative by default: hidden from assistive technology");
    }

    @Test
    void canvasHasImageSemantics(@TempDir Path tempDir) throws IOException {
        String program = """
                main() {
                    var win = Window("App")
                    win.bind(Canvas(100, 50))
                    win.show()
                    println("done")
                }
                """;
        String probe = NODES + """
                const c = kofA11yNodes("CANVAS")[0];
                console.log("role=" + c.getAttribute("role") + " tabindex=" + c.getAttribute("tabindex"));
                """;
        assertEquals("done\nrole=img tabindex=null", runJsProbe(tempDir, "canvasimg", program, probe),
                "a Canvas is an image to assistive technology and stays non-interactive");
    }

    @Test
    void windowsEstablishASingleMainLandmark(@TempDir Path tempDir) throws IOException {
        String program = """
                main() {
                    var a = Window("A")
                    var b = Window("B")
                    a.bind(Label("one"))
                    b.bind(Label("two"))
                    a.show()
                    b.show()
                    println("done")
                }
                """;
        String probe = """
                const root = document.getElementById("kof-root");
                const wins = Object.values(window.__kofWindows);
                const mains = [root].concat(wins).filter((w) => w.getAttribute("role") === "main").length;
                console.log("windows=" + wins.length + " mainLandmarks=" + mains);
                """;
        assertEquals("done\nwindows=2 mainLandmarks=1",
                runJsProbe(tempDir, "mainlandmark", program, probe),
                "every Window shares one main landmark — never a duplicate per window");
    }

    @Test
    void iconSemanticsSurviveRootReuse(@TempDir Path tempDir) throws IOException {
        String program = """
                main() {
                    var app = Component(0)
                    var win = Window("App")
                    app.view((s: Int) -> { return Icon("star") })
                    win.bind(app)
                    win.show()
                    println("done")
                }
                """;
        String probe = NODES + """
                import { kofUiComponentStateSet } from './kof-runtime.mjs';
                const before = kofA11yNodes("SVG")[0];
                kofUiComponentStateSet(1, 7);
                const after = kofA11yNodes("SVG");
                console.log("same=" + (before === after[0]) + " count=" + after.length + " " + kofA11ySvg(after[0]));
                """;
        assertEquals("done\nsame=true count=1 hidden=true role=null",
                runJsProbe(tempDir, "iconreuse", program, probe),
                "root reuse (D-UI-DIFF B) keeps the decorative semantics on the surviving node");
    }

    @Test
    void iconSemanticsSurviveRebuildAndPrune(@TempDir Path tempDir) throws IOException {
        String program = """
                main() {
                    var app = Component(0)
                    var win = Window("App")
                    app.view((s: Int) -> {
                        if (s == 1) { return Label("L" + s) }
                        return Icon("star")
                    })
                    win.bind(app)
                    win.show()
                    println("done")
                }
                """;
        String probe = NODES + """
                import { kofUiComponentStateSet } from './kof-runtime.mjs';
                const first = kofA11yNodes("SVG")[0];
                kofUiComponentStateSet(1, 1);
                const asLabel = kofA11yNodes("SVG").length;
                kofUiComponentStateSet(1, 2);
                const again = kofA11yNodes("SVG");
                console.log("svgWhileLabel=" + asLabel + " fresh=" + (again[0] !== first)
                    + " count=" + again.length + " " + kofA11ySvg(again[0]));
                """;
        assertEquals("done\nsvgWhileLabel=0 fresh=true count=1 hidden=true role=null",
                runJsProbe(tempDir, "iconrebuild", program, probe),
                "rebuild+prune (§300) creates the Icon again with the same decorative semantics");
    }

    private static final String DEFAULTS_PROGRAM = """
            main() {
                var win = Window("App")
                win.bind(Icon("star"))
                win.bind(Canvas(10, 10))
                win.show()
                println("ok")
            }
            """;

    @Test
    void defaultsCompileAndRunOnJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("a11ydefaults.kf");
        Files.writeString(source, DEFAULTS_PROGRAM);
        runJvm(source, tempDir.resolve("jvm-a11ydefaults"), "ok");
    }

    @Test
    void defaultsCompileAndRunOnNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("a11ydefaults.kf");
        Files.writeString(source, DEFAULTS_PROGRAM);
        runNative(source, tempDir.resolve("native-a11ydefaults"), "ok");
    }
}
