package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class UiKeyboardOperabilityE2ETest extends ComponentCoreSupport {

    private static final String HELPERS = """
            const kbNodes = (tag) => Object.values(window.__kofNodes)
                .filter((n) => String(n.tagName).toUpperCase() === tag);
            const kbPress = (n, key, target) => {
                for (const fn of ((n._handlers && n._handlers.keydown) || []).slice()) {
                    fn({ key: key, target: target || n, preventDefault() {}, stopPropagation() {} });
                }
            };
            const kbSem = (n) => "role=" + n.getAttribute("role") + " tabindex=" + n.getAttribute("tabindex")
                + " hidden=" + (n.getAttribute("aria-hidden") === "true");
            """;

    @Test
    void clickableIconBecomesKeyboardOperable(@TempDir Path tempDir) throws IOException {
        String program = """
                main() {
                    var win = Window("App")
                    var del = Icon("trash")
                    del.on("click", (e: Event) -> { println("removed") })
                    win.bind(del)
                    win.show()
                    println("done")
                }
                """;
        String probe = HELPERS + """
                const svg = kbNodes("SVG")[0];
                console.log(kbSem(svg));
                kbPress(svg, "Enter");
                kbPress(svg, " ");
                kbPress(svg, "a");
                console.log("keys-done");
                """;
        assertEquals("done\nrole=button tabindex=0 hidden=false\nremoved\nremoved\nkeys-done",
                runJsProbe(tempDir, "kbicon", program, probe),
                "a clickable Icon is a focusable button: Enter and Space activate it, other keys do not");
    }

    @Test
    void clickableLabelAndViewAreKeyboardOperable(@TempDir Path tempDir) throws IOException {
        String program = """
                main() {
                    var win = Window("App")
                    var l = Label("open")
                    l.on("click", (e: Event) -> { println("label") })
                    var v = View(Style("padding: 4"))
                    v.on("click", (e: Event) -> { println("view") })
                    win.bind(l)
                    win.bind(v)
                    win.show()
                    println("done")
                }
                """;
        String probe = HELPERS + """
                const span = kbNodes("SPAN")[0];
                const div = kbNodes("DIV").find((n) => n.getAttribute("role") === "button");
                console.log(kbSem(span) + " | " + kbSem(div));
                kbPress(span, "Enter");
                kbPress(div, " ");
                """;
        assertEquals("done\nrole=button tabindex=0 hidden=false | role=button tabindex=0 hidden=false\nlabel\nview",
                runJsProbe(tempDir, "kblabelview", program, probe),
                "any non-native widget with a click handler is reachable and operable by keyboard");
    }

    @Test
    void nativeButtonKeepsItsOwnSemantics(@TempDir Path tempDir) throws IOException {
        String program = """
                main() {
                    var win = Window("App")
                    var b = Button("Save", () -> println("saved"))
                    b.on("click", (e: Event) -> { println("clicked") })
                    win.bind(b)
                    win.show()
                    println("done")
                }
                """;
        String probe = HELPERS + """
                const b = kbNodes("BUTTON")[0];
                const keys = ((b._handlers && b._handlers.keydown) || []).length;
                console.log(kbSem(b) + " keyListeners=" + keys);
                """;
        assertEquals("done\nrole=null tabindex=null hidden=false keyListeners=0",
                runJsProbe(tempDir, "kbbutton", program, probe),
                "a native button already is accessible: no role, no tabindex, no extra key handling");
    }

    @Test
    void widgetsWithoutClickStayNonInteractive(@TempDir Path tempDir) throws IOException {
        String program = """
                main() {
                    var win = Window("App")
                    var ic = Icon("star")
                    ic.on("keydown", (e: Event) -> { println("key") })
                    win.bind(ic)
                    win.bind(Label("plain"))
                    win.show()
                    println("done")
                }
                """;
        String probe = HELPERS + """
                console.log(kbSem(kbNodes("SVG")[0]) + " | " + kbSem(kbNodes("SPAN")[0]));
                """;
        assertEquals("done\nrole=null tabindex=null hidden=true | role=null tabindex=null hidden=false",
                runJsProbe(tempDir, "kbplain", program, probe),
                "accessibility defaults never turn a decorative or passive widget into a control");
    }

    @Test
    void keyFromADescendantDoesNotActivateTheContainer(@TempDir Path tempDir) throws IOException {
        String program = """
                main() {
                    var win = Window("App")
                    var field = Input("")
                    var row = Row(listOf(field))
                    row.on("click", (e: Event) -> { println("row") })
                    win.bind(row)
                    win.show()
                    println("done")
                }
                """;
        String probe = HELPERS + """
                const row = kbNodes("DIV").find((n) => n.getAttribute("role") === "button");
                const input = kbNodes("INPUT")[0];
                kbPress(row, "Enter", input);
                console.log("after-input-enter");
                kbPress(row, "Enter");
                """;
        assertEquals("done\nafter-input-enter\nrow",
                runJsProbe(tempDir, "kbdescendant", program, probe),
                "Enter typed inside a child field must not trigger the clickable container");
    }

    @Test
    void clickableComponentIsKeyboardOperable(@TempDir Path tempDir) throws IOException {
        String program = """
                main() {
                    var app = Component(0)
                    var win = Window("App")
                    app.view((s: Int) -> { return Label("card") })
                    app.on("click", (e: Event) -> { println("card") })
                    win.bind(app)
                    win.show()
                    println("done")
                }
                """;
        String probe = HELPERS + """
                const host = kbNodes("DIV").concat(Object.values(window.__kofWindows))
                    .flatMap((n) => [n].concat(n.children || []))
                    .find((n) => n.getAttribute && n.getAttribute("role") === "button");
                console.log(kbSem(host));
                kbPress(host, "Enter");
                """;
        assertEquals("done\nrole=button tabindex=0 hidden=false\ncard",
                runJsProbe(tempDir, "kbcomponent", program, probe),
                "Component.on(\"click\") makes the component host keyboard-operable");
    }

    @Test
    void rootReuseKeepsOneKeyboardActivation(@TempDir Path tempDir) throws IOException {
        String program = """
                main() {
                    var app = Component(0)
                    var win = Window("App")
                    app.view((s: Int) -> {
                        var ic = Icon("trash")
                        ic.on("click", (e: Event) -> { println("hit" + s) })
                        return ic
                    })
                    win.bind(app)
                    win.show()
                    println("done")
                }
                """;
        String probe = HELPERS + """
                import { kofUiComponentStateSet } from './kof-runtime.mjs';
                const before = kbNodes("SVG")[0];
                kofUiComponentStateSet(1, 1);
                kofUiComponentStateSet(1, 2);
                const svgs = kbNodes("SVG");
                console.log("same=" + (svgs[0] === before) + " count=" + svgs.length + " " + kbSem(svgs[0]));
                kbPress(svgs[0], "Enter");
                """;
        assertEquals("done\nsame=true count=1 role=button tabindex=0 hidden=false\nhit2",
                runJsProbe(tempDir, "kbreuse", program, probe),
                "root reuse keeps the button semantics and exactly one activation per key press");
    }

    @Test
    void rootReuseDropsButtonSemanticsWhenNoLongerClickable(@TempDir Path tempDir) throws IOException {
        String program = """
                main() {
                    var app = Component(0)
                    var win = Window("App")
                    app.view((s: Int) -> {
                        var ic = Icon("star")
                        if (s == 0) { ic.on("click", (e: Event) -> { println("fav") }) }
                        return ic
                    })
                    win.bind(app)
                    win.show()
                    println("done")
                }
                """;
        String probe = HELPERS + """
                import { kofUiComponentStateSet } from './kof-runtime.mjs';
                const svg = kbNodes("SVG")[0];
                const first = kbSem(svg);
                kofUiComponentStateSet(1, 1);
                const after = kbNodes("SVG")[0];
                console.log(first + " -> same=" + (after === svg) + " " + kbSem(after)
                   );
                kbPress(after, "Enter");
                console.log("quiet");
                """;
        assertEquals("done\nrole=button tabindex=0 hidden=false -> same=true role=null tabindex=null hidden=true\nquiet",
                runJsProbe(tempDir, "kbreusedrop", program, probe),
                "a reused node that stops being clickable returns to decorative semantics");
    }

    @Test
    void rebuildAndPruneAppliesKeyboardSemantics(@TempDir Path tempDir) throws IOException {
        String program = """
                main() {
                    var app = Component(0)
                    var win = Window("App")
                    app.view((s: Int) -> {
                        if (s == 0) { return Label("loading") }
                        var ic = Icon("trash")
                        ic.on("click", (e: Event) -> { println("ready") })
                        return ic
                    })
                    win.bind(app)
                    win.show()
                    println("done")
                }
                """;
        String probe = HELPERS + """
                import { kofUiComponentStateSet } from './kof-runtime.mjs';
                kofUiComponentStateSet(1, 1);
                const svg = kbNodes("SVG")[0];
                console.log("spans=" + kbNodes("SPAN").length + " " + kbSem(svg));
                kbPress(svg, " ");
                """;
        assertEquals("done\nspans=0 role=button tabindex=0 hidden=false\nready",
                runJsProbe(tempDir, "kbrebuild", program, probe),
                "rebuild+prune creates the clickable Icon with keyboard semantics");
    }

    private static final String CLICK_PROGRAM = """
            main() {
                var win = Window("App")
                var ic = Icon("trash")
                ic.on("click", (e: Event) -> { println("x") })
                var row = Row(listOf(Label("a")))
                row.on("click", (e: Event) -> { println("y") })
                win.bind(ic)
                win.bind(row)
                win.show()
                println("ok")
            }
            """;

    @Test
    void clickableWidgetsCompileAndRunOnJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("kbjvm.kf");
        Files.writeString(source, CLICK_PROGRAM);
        runJvm(source, tempDir.resolve("jvm-kb"), "ok");
    }

    @Test
    void clickableWidgetsCompileAndRunOnNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("kbnative.kf");
        Files.writeString(source, CLICK_PROGRAM);
        runNative(source, tempDir.resolve("native-kb"), "ok");
    }
}
