package dev.kof.compiler;

import dev.kof.compiler.js.JsRuntimeTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D-COMPLETE-FIRST item 4 (maintainer 26/09): kof.ui deterministic release —
 * the leak locks. A store (or subscription) created during a component's
 * lifecycle belongs to that component and dies at unmount; app-scope stores
 * and AppState stay ownerless/manual by design; the trio
 * uiNodesLive()/storesLive()/subscriptionsLive() must return to 0 after
 * mount/unmount cycles. JVM/Native keep the documented UI=KofJS parity faces
 * (probe = honest no-op accounting); Script inherits the UI002 route.
 */
class UiLeakLockE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase().contains("linux");
    }

    private String runJvm(Path source, Path outDir, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compilation should succeed: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected JVM output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running JVM class", e);
        }
    }

    private String runNative(Path source, Path outDir, String expected) throws IOException {
        Assumptions.assumeTrue(isLinux(), "Native target runs on Linux");
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Native compilation should succeed: " + result.diagnostics().getDiagnostics());
        Path binFile = outDir.resolve("Default/Main");
        assertTrue(Files.exists(binFile), "Binary should exist");
        try {
            ProcessBuilder pb = new ProcessBuilder(binFile.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected Native output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running native binary", e);
        }
    }

    private String runJs(Path tempDir, String name, String program) throws IOException {
        Path source = tempDir.resolve(name + "-js.kf");
        Files.writeString(source, program);
        CompilationResult js = driver.compile(source, tempDir.resolve("js-" + name), Target.JS);
        assertTrue(js.success(), "JS compilation should succeed: " + js.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int code = dev.kof.runtime.KofJsRunner.run(
                tempDir.resolve("js-" + name).resolve("Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        assertEquals(0, code, "JS run should succeed: " + out);
        return out.toString().trim();
    }

    private String runJsProbe(Path tempDir, String name, String program,
                              String probeJs) throws IOException {
        Path source = tempDir.resolve(name + "-js.kf");
        Files.writeString(source, program);
        CompilationResult js = driver.compile(source, tempDir.resolve("js-" + name), Target.JS);
        assertTrue(js.success(), "JS compilation should succeed: " + js.diagnostics().getDiagnostics());
        Path module = tempDir.resolve("js-" + name).resolve("Default.mjs");
        Files.writeString(module, Files.readString(module) + "\n" + probeJs + "\n");
        JsRuntimeTestSupport.includeImportsOf(module.getParent(), probeJs);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int code = dev.kof.runtime.KofJsRunner.run(module, out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        assertEquals(0, code, "JS probe run should succeed: " + out);
        return out.toString().trim();
    }

    @Test
    void subscriptionsLiveProbeFaces(@TempDir Path tempDir) throws IOException {
        // App-scope store + two subscribers: the JVM/Native faces are the
        // documented UI=KofJS no-ops (JVM subscribe discards — no subscription
        // can exist there; Native is pure zero). The JS face counts real
        // subscribers.
        String program = """
            main() {
                var store = Store(1)
                store.subscribe((v: Int) -> {})
                store.subscribe((v: Int) -> {})
                println(subscriptionsLive())
                println(storesLive())
            }
            """;
        Path src = tempDir.resolve("probe-faces.kf");
        Files.writeString(src, program);
        runJvm(src, tempDir.resolve("jvm-probe-faces"), "0\n1");
        runNative(src, tempDir.resolve("native-probe-faces"), "0\n0");
        assertEquals("2\n1", runJs(tempDir, "probe-faces", program),
                "the JS probe counts real live subscriptions");
    }

    @Test
    void componentOwnedStoreDiesWithComponent(@TempDir Path tempDir) throws IOException {
        // The item-4 core: a Store CREATED inside the component's lifecycle
        // (the view render) is owned by it. After the component leaves the
        // tree, the store and the subscription it carries must be gone —
        // storesLive() and subscriptionsLive() both back to 0.
        String program = """
            main() {
                var app = Component(0)
                app.view((s: Int) -> {
                    var inner = Store(7)
                    inner.subscribe((v: Int) -> println("in=" + v))
                    return Label("x")
                })
                var win = Window("App")
                win.bind(app)
                println(storesLive())
                println(subscriptionsLive())
            }
            """;
        String probe = """
            import { kofUiComponentRemove } from './kof-runtime.mjs';
            kofUiComponentRemove(1);
            // storesLive/subscriptionsLive already imported by the compiled main
            console.log("after=" + kofUiStoresLive() + "," + kofUiSubscriptionsLive());
            """;
        String out = runJsProbe(tempDir, "owned-store", program, probe);
        assertEquals("in=7\n1\n1\nafter=0,0", out,
                "component-owned store + subscription must die at unmount (leak locks to 0)");
    }

    @Test
    void appScopedStoreSurvivesUnmountControl(@TempDir Path tempDir) throws IOException {
        // Boundary control: a store created OUTSIDE any component lifecycle
        // is ownerless and manual by design — removing the component must
        // not touch the store nor its subscription.
        String program = """
            main() {
                var store = Store(1)
                store.subscribe((v: Int) -> println("app=" + v))
                var app = Component(0)
                app.view((s: Int) -> { return Label("x") })
                var win = Window("A")
                win.bind(app)
            }
            """;
        String probe = """
            import { kofUiComponentRemove, kofUiStoreSet, kofUiStoresLive, kofUiSubscriptionsLive } from './kof-runtime.mjs';
            kofUiComponentRemove(1);
            console.log("locks=" + kofUiStoresLive() + "," + kofUiSubscriptionsLive());
            kofUiStoreSet(1, 2);
            """;
        String out = runJsProbe(tempDir, "appscope", program, probe);
        assertEquals("app=1\nlocks=1,1\napp=2", out,
                "app-scope store + subscription survive component removal (manual semantics)");
    }

    @Test
    void appStateIsNeverAttributedToItsCreator(@TempDir Path tempDir) throws IOException {
        // The singleton exemption: AppState created INSIDE a component view is
        // still app-scoped by definition — unmounting the creator must not
        // destroy the root store.
        String program = """
            main() {
                var app = Component(0)
                app.view((s: Int) -> {
                    AppState(5)
                    return Label("x")
                })
                var win = Window("App")
                win.bind(app)
                println(storesLive())
            }
            """;
        String probe = """
            import { kofUiComponentRemove } from './kof-runtime.mjs';
            kofUiComponentRemove(1);
            // storesLive already imported by the compiled main
            console.log("after=" + kofUiStoresLive());
            """;
        String out = runJsProbe(tempDir, "appstate-exempt", program, probe);
        assertEquals("1\nafter=1", out,
                "AppState is never attributed to a component (app by design)");
    }

    @Test
    void manualUnsubscribeMovesTheProbe(@TempDir Path tempDir) throws IOException {
        // Probe precision (not just "it returns something"): every manual
        // unsubscribe decrements subscriptionsLive() exactly.
        String program = """
            main() {
                var store = Store(1)
                var a = (v: Int) -> {}
                var b = (v: Int) -> {}
                store.subscribe(a)
                store.subscribe(b)
                println(subscriptionsLive())
                store.unsubscribe(a)
                println(subscriptionsLive())
                store.unsubscribe(b)
                store.unsubscribe(b)
                println(subscriptionsLive())
            }
            """;
        Path src = tempDir.resolve("unsub-count.kf");
        Files.writeString(src, program);
        runJvm(src, tempDir.resolve("jvm-unsub-count"), "0\n0\n0");
        runNative(src, tempDir.resolve("native-unsub-count"), "0\n0\n0");
        assertEquals("2\n1\n0", runJs(tempDir, "unsub-count", program),
                "subscriptionsLive tracks every (manual) subscription exactly");
    }

    @Test
    void stressMountUnmountCyclesLeaveNoStoreOrSubscription(@TempDir Path tempDir) throws IOException {
        // The leak locks of item 4, same scale as the existing nodes stress:
        // 10.000 mount/unmount cycles where each component CREATES a store and
        // SUBSCRIBES inside its lifecycle. The trio must return to 0 — proof
        // the deterministic release holds under churn, on every target.
        String program = """
            main() {
                var win = Window("App")
                var i = 0
                while (i < 10000) {
                    var app = Component(i)
                    app.view((s: Int) -> {
                        var inner = Store(s)
                        inner.subscribe((v: Int) -> {})
                        return Label("n=" + s)
                    })
                    win.bind(app)
                    app.remove()
                    i = i + 1
                }
                println(uiNodesLive())
                println(storesLive())
                println(subscriptionsLive())
            }
            """;
        Path src = tempDir.resolve("stress-locks.kf");
        Files.writeString(src, program);
        runJvm(src, tempDir.resolve("jvm-stress-locks"), "0\n0\n0");
        runNative(src, tempDir.resolve("native-stress-locks"), "0\n0\n0");
        assertEquals("0\n0\n0", runJs(tempDir, "stress-locks", program),
                "10k cycles must leave no node, no store and no subscription alive");
    }
}
