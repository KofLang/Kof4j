package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class UiAccessibleNameE2ETest extends ComponentCoreSupport {

    private static final String HELPERS = """
            const anNodes = (tag) => Object.values(window.__kofNodes)
                .filter((n) => String(n.tagName).toUpperCase() === tag);
            const anSem = (n) => "label=" + n.getAttribute("aria-label") + " role=" + n.getAttribute("role")
                + " hidden=" + (n.getAttribute("aria-hidden") === "true");
            """;

    @Test
    void everyWidgetKindTakesAnAccessibleName(@TempDir Path tempDir) throws IOException {
        String program = """
                main() {
                    var win = Window("App")
                    var email = Input("")
                    email.setAccessibleName("Email address")
                    var status = Label("ok")
                    status.setAccessibleName("Status")
                    var save = Button("S", () -> println("s"))
                    save.setAccessibleName("Save")
                    var chart = Canvas(100, 50)
                    chart.setAccessibleName("Sales per month")
                    var docs = Link("docs", "https://example.org")
                    docs.setAccessibleName("Documentation")
                    win.bind(email)
                    win.bind(status)
                    win.bind(save)
                    win.bind(chart)
                    win.bind(docs)
                    win.show()
                    println("done")
                }
                """;
        String probe = HELPERS + """
                console.log(["INPUT", "SPAN", "BUTTON", "CANVAS", "A"]
                    .map((t) => t + ":" + anNodes(t)[0].getAttribute("aria-label")).join(" | "));
                console.log(anSem(anNodes("CANVAS")[0]));
                """;
        assertEquals("done\nINPUT:Email address | SPAN:Status | BUTTON:Save | CANVAS:Sales per month | A:Documentation\n"
                        + "label=Sales per month role=img hidden=false",
                runJsProbe(tempDir, "anwidgets", program, probe),
                "setAccessibleName names any DOM widget and the Canvas image");
    }

    @Test
    void namedIconIsAnImageAndEmptyNameMakesItDecorativeAgain(@TempDir Path tempDir) throws IOException {
        String program = """
                main() {
                    var win = Window("App")
                    var fav = Icon("star")
                    fav.setAccessibleName("Favorite")
                    var gone = Icon("trash")
                    gone.setAccessibleName("Delete")
                    gone.setAccessibleName("")
                    win.bind(fav)
                    win.bind(gone)
                    win.show()
                    println("done")
                }
                """;
        String probe = HELPERS + """
                const svgs = anNodes("SVG");
                console.log(anSem(svgs[0]));
                console.log(anSem(svgs[1]));
                """;
        assertEquals("done\nlabel=Favorite role=img hidden=false\n"
                        + "label=null role=null hidden=true",
                runJsProbe(tempDir, "anicon", program, probe),
                "a named Icon is announced as an image; an empty name returns it to decorative");
    }

    @Test
    void clickableIconKeepsButtonRoleWithItsName(@TempDir Path tempDir) throws IOException {
        String program = """
                main() {
                    var win = Window("App")
                    var a = Icon("trash")
                    a.setAccessibleName("Delete")
                    a.on("click", (e: Event) -> { println("a") })
                    var b = Icon("edit")
                    b.on("click", (e: Event) -> { println("b") })
                    b.setAccessibleName("Edit")
                    win.bind(a)
                    win.bind(b)
                    win.show()
                    println("done")
                }
                """;
        String probe = HELPERS + """
                console.log(anNodes("SVG").map(anSem).join(" | "));
                """;
        assertEquals("done\nlabel=Delete role=button hidden=false | label=Edit role=button hidden=false",
                runJsProbe(tempDir, "anclick", program, probe),
                "naming before or after on(click) gives a named button, never an image");
    }

    @Test
    void reuseKeepsTheNamedImageWhenTheIconStopsBeingClickable(@TempDir Path tempDir) throws IOException {
        String program = """
                main() {
                    var app = Component(0)
                    var win = Window("App")
                    app.view((s: Int) -> {
                        var ic = Icon("trash")
                        ic.setAccessibleName("Delete")
                        if (s == 0) { ic.on("click", (e: Event) -> { println("x") }) }
                        return ic
                    })
                    win.bind(app)
                    win.show()
                    println("done")
                }
                """;
        String probe = HELPERS + """
                import { kofUiComponentStateSet } from './kof-runtime.mjs';
                const svg = anNodes("SVG")[0];
                const first = anSem(svg);
                kofUiComponentStateSet(1, 1);
                const after = anNodes("SVG")[0];
                console.log(first + " -> same=" + (after === svg) + " " + anSem(after)
                    + " tabindex=" + after.getAttribute("tabindex"));
                """;
        assertEquals("done\nlabel=Delete role=button hidden=false -> same=true label=Delete role=img hidden=false tabindex=null",
                runJsProbe(tempDir, "anreuseclick", program, probe),
                "dropping the click handler on reuse leaves a named image, not a stale button");
    }

    @Test
    void nameFollowsStateOnRootReuse(@TempDir Path tempDir) throws IOException {
        String program = """
                main() {
                    var app = Component(0)
                    var win = Window("App")
                    app.view((s: Int) -> {
                        var l = Label("" + s)
                        if (s < 5) { l.setAccessibleName("Unread " + s) }
                        return l
                    })
                    win.bind(app)
                    win.show()
                    println("done")
                }
                """;
        String probe = HELPERS + """
                import { kofUiComponentStateSet } from './kof-runtime.mjs';
                const span = anNodes("SPAN")[0];
                kofUiComponentStateSet(1, 3);
                const mid = anNodes("SPAN")[0];
                const midLabel = mid.getAttribute("aria-label");
                kofUiComponentStateSet(1, 9);
                const last = anNodes("SPAN")[0];
                console.log("same=" + (span === mid && mid === last) + " mid=" + midLabel
                    + " last=" + last.getAttribute("aria-label"));
                """;
        assertEquals("done\nsame=true mid=Unread 3 last=null",
                runJsProbe(tempDir, "anreuse", program, probe),
                "root reuse updates the name and drops it when the render no longer sets one");
    }

    @Test
    void nameIsAppliedAfterRebuildAndPrune(@TempDir Path tempDir) throws IOException {
        String program = """
                main() {
                    var app = Component(0)
                    var win = Window("App")
                    app.view((s: Int) -> {
                        if (s == 0) { return Label("loading") }
                        var field = Input("")
                        field.setAccessibleName("Search")
                        return field
                    })
                    win.bind(app)
                    win.show()
                    println("done")
                }
                """;
        String probe = HELPERS + """
                import { kofUiComponentStateSet } from './kof-runtime.mjs';
                kofUiComponentStateSet(1, 1);
                console.log("spans=" + anNodes("SPAN").length + " input=" + anNodes("INPUT")[0].getAttribute("aria-label"));
                """;
        assertEquals("done\nspans=0 input=Search",
                runJsProbe(tempDir, "anrebuild", program, probe),
                "rebuild+prune creates the named widget with its accessible name");
    }

    private static final String NAME_PROGRAM = """
            main() {
                var win = Window("App")
                var ic = Icon("star")
                ic.setAccessibleName("Favorite")
                var c = Canvas(10, 10)
                c.setAccessibleName("Chart")
                var i = Input("")
                i.setAccessibleName("Name")
                win.bind(ic)
                win.bind(c)
                win.bind(i)
                win.show()
                println("ok")
            }
            """;

    @Test
    void accessibleNameCompilesAndRunsOnJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("anjvm.kf");
        Files.writeString(source, NAME_PROGRAM);
        runJvm(source, tempDir.resolve("jvm-an"), "ok");
    }

    @Test
    void accessibleNameCompilesAndRunsOnNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("annative.kf");
        Files.writeString(source, NAME_PROGRAM);
        runNative(source, tempDir.resolve("native-an"), "ok");
    }
}
