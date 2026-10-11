package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class UiAnnounceE2ETest extends ComponentCoreSupport {

    private static final String HELPERS = """
            const liveNodes = () => Object.values(window.__kofNodes)
                .filter((n) => n.getAttribute && n.getAttribute("aria-live") !== null);
            const spans = () => Object.values(window.__kofNodes)
                .filter((n) => String(n.tagName).toUpperCase() === "SPAN");
            """;

    @Test
    void announceMarksAPoliteLiveRegionAndFalseRemovesIt(@TempDir Path tempDir) throws IOException {
        String program = """
                main() {
                    var win = Window("App")
                    var status = Label("")
                    status.setAnnounce(true)
                    var quiet = Label("")
                    quiet.setAnnounce(true)
                    quiet.setAnnounce(false)
                    var plain = Label("")
                    win.bind(status)
                    win.bind(quiet)
                    win.bind(plain)
                    status.text = "Saved"
                    win.show()
                    println("done")
                }
                """;
        String probe = HELPERS + """
                console.log(spans().map((n) => String(n.getAttribute("aria-live"))).join(",")
                    + " text=" + spans()[0].textContent);
                """;
        assertEquals("done\npolite,null,null text=Saved",
                runJsProbe(tempDir, "announce", program, probe),
                "setAnnounce(true) marks a polite live region, false removes it, the default is off");
    }

    @Test
    void rootReuseKeepsOneLiveRegion(@TempDir Path tempDir) throws IOException {
        String program = """
                main() {
                    var app = Component(0)
                    var win = Window("App")
                    app.view((s: Int) -> {
                        var l = Label("items: " + s)
                        l.setAnnounce(true)
                        return l
                    })
                    win.bind(app)
                    win.show()
                    println("done")
                }
                """;
        String probe = HELPERS + """
                import { kofUiComponentStateSet } from './kof-runtime.mjs';
                const region = liveNodes()[0];
                kofUiComponentStateSet(1, 1);
                kofUiComponentStateSet(1, 2);
                const now = liveNodes();
                console.log("regions=" + now.length + " same=" + (now[0] === region)
                    + " live=" + now[0].getAttribute("aria-live") + " text=" + now[0].textContent);
                """;
        assertEquals("done\nregions=1 same=true live=polite text=items: 2",
                runJsProbe(tempDir, "announcereuse", program, probe),
                "state updates change the text of the SAME live region — never a duplicate region");
    }

    @Test
    void rootReuseDropsTheRegionWhenTheRenderStopsAnnouncing(@TempDir Path tempDir) throws IOException {
        String program = """
                main() {
                    var app = Component(0)
                    var win = Window("App")
                    app.view((s: Int) -> {
                        var l = Label("" + s)
                        if (s == 0) { l.setAnnounce(true) }
                        return l
                    })
                    win.bind(app)
                    win.show()
                    println("done")
                }
                """;
        String probe = HELPERS + """
                import { kofUiComponentStateSet } from './kof-runtime.mjs';
                const before = liveNodes().length;
                kofUiComponentStateSet(1, 1);
                console.log("before=" + before + " after=" + liveNodes().length);
                """;
        assertEquals("done\nbefore=1 after=0",
                runJsProbe(tempDir, "announcedrop", program, probe),
                "a reused node does not keep announcing once the render stops asking for it");
    }

    @Test
    void rebuildAndPruneCreatesTheAnnouncedRegion(@TempDir Path tempDir) throws IOException {
        String program = """
                main() {
                    var app = Component(0)
                    var win = Window("App")
                    app.view((s: Int) -> {
                        if (s == 0) { return Input("") }
                        var l = Label("ready")
                        l.setAnnounce(true)
                        return l
                    })
                    win.bind(app)
                    win.show()
                    println("done")
                }
                """;
        String probe = HELPERS + """
                import { kofUiComponentStateSet } from './kof-runtime.mjs';
                kofUiComponentStateSet(1, 1);
                const live = liveNodes();
                console.log("regions=" + live.length + " tag=" + String(live[0].tagName).toUpperCase());
                """;
        assertEquals("done\nregions=1 tag=SPAN",
                runJsProbe(tempDir, "announcerebuild", program, probe),
                "rebuild+prune creates exactly one announced region");
    }

    private static final String ANNOUNCE_PROGRAM = """
            main() {
                var win = Window("App")
                var status = Label("")
                status.setAnnounce(true)
                status.setAnnounce(false)
                win.bind(status)
                win.show()
                println("ok")
            }
            """;

    @Test
    void announceCompilesAndRunsOnJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("anjvm.kf");
        Files.writeString(source, ANNOUNCE_PROGRAM);
        runJvm(source, tempDir.resolve("jvm-announce"), "ok");
    }

    @Test
    void announceCompilesAndRunsOnNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("annative.kf");
        Files.writeString(source, ANNOUNCE_PROGRAM);
        runNative(source, tempDir.resolve("native-announce"), "ok");
    }
}
