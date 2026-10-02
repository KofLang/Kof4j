package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * kof.ui Component Core (docs/ui/architecture.md §2.2): state, view builder,
 * lifecycle (mount/unmount), effects with automatic cleanup and the leak
 * probe. Rendering happens in the KofJS target (DOM); JVM and Native execute
 * the same program with no-op handles — the program must still compile and
 * run identically.
 */
class ComponentCoreE2ETest extends ComponentCoreSupport {

    @Test
    void componentSubscriptionDiesWithComponent(@TempDir Path tempDir) throws IOException {
        // D-UI-AUTOUNSUB (A): a subscribe executed during the component's own
        // lifecycle (here: the view render) is bound to it; removing the
        // component must stop delivery WITHOUT any manual unsubscribe.
        String program = SRC_COMPONENT_SUBSCRIPTION_DIES_WITH_COMPONENT;
        String probe = """
            import { kofUiComponentRemove, kofUiStoreSet } from './kof-runtime.mjs';
            kofUiStoreSet(1, 20);
            kofUiComponentRemove(1);
            kofUiStoreSet(1, 30);
            console.log("after");
            """;
        String out = runJsProbe(tempDir, "autosub", program, probe);
        assertEquals("sub=10\nshown\nsub=20\nafter",
                out, "the component subscription must die with the component (no sub=30)");
    }

    @Test
    void appScopedSubscriptionStaysManual(@TempDir Path tempDir) throws IOException {
        // (A) boundary: outside a component lifecycle the subscription is NOT
        // bound to anyone — removing every component must not touch it.
        String program = SRC_APP_SCOPED_SUBSCRIPTION_STAYS_MANUAL;
        String probe = """
            import { kofUiComponentRemove, kofUiStoreSet } from './kof-runtime.mjs';
            kofUiComponentRemove(1);
            kofUiStoreSet(1, 2);
            console.log("after");
            """;
        String out = runJsProbe(tempDir, "manualsub", program, probe);
        assertEquals("app=1\nshown\napp=2\nafter",
                out, "an app-scope subscription must survive component removal (manual semantics)");
    }

    @Test
    void stableRootKindReusesNodeAndHandle(@TempDir Path tempDir) throws IOException {
        // D-UI-DIFF (B) core claim: when the view keeps the same root kind,
        // the OLD DOM node and the OLD handle survive a state write (the
        // fresh node is discarded) — identity continuity, not just no-leak.
        String program = SRC_STABLE_ROOT_KIND_REUSES_NODE_AND_HANDLE;
        String probe = SRC_STABLE_ROOT_KIND_REUSES_NODE_AND_HANDLE_2;
        String out = runJsProbe(tempDir, "rootreuse", program, probe);
        assertEquals("done\nkeysBefore=2 keysAfter=2 sameNode=true text0=v=0 text=v=7",
                out, "stable root kind must keep the same node and the same handle");
    }

    @Test
    void buttonRootActionSurvivesReuseWithoutDoubling(@TempDir Path tempDir) throws IOException {
        // (B) risk case: the root has a DOM listener (Button action). Reuse
        // must MOVE it (remove the stale, register the fresh exactly once),
        // re-home the action table key onto the surviving handle, and keep
        // clicks firing with one listener per render.
        String program = SRC_BUTTON_ROOT_ACTION_SURVIVES_REUSE_WITHOUT_DOUBLING;
        String probe = """
            const el = Object.values(window.__kofNodes)[0];
            el.click();
            kofUiComponentStateSet(1, 5);
            const el2 = Object.values(window.__kofNodes)[0];
            el2.click();
            console.log("same=" + (el === el2) + " listeners=" + el2._kofDomListeners.length
                + " actions=" + Object.keys(window.__kofActions || {}).length);
            """;
        String out = runJsProbe(tempDir, "buttonreuse", program, probe);
        assertEquals("done\nfired=4\nfired=5\nsame=true listeners=1 actions=1",
                out, "reused root must move (not stack) its click listener and re-home the action key");
    }

    @Test
    void kindChangeStillRebuildsAndPrunes(@TempDir Path tempDir) throws IOException {
        // (B) boundary: different root kind → the §300 path stays untouched
        // (rebuild + prune), the reuse branch must NOT alias two different
        // widgets onto one node.
        String program = SRC_KIND_CHANGE_STILL_REBUILDS_AND_PRUNES;
        String probe = """
            const el = Object.values(window.__kofNodes)[0];
            const tag0 = el.tagName;
            kofUiComponentStateSet(1, 9);
            const el2 = Object.values(window.__kofNodes)[0];
            console.log("tag0=" + tag0 + " tag=" + el2.tagName + " changed=" + (el !== el2)
                + " nodes=" + Object.keys(window.__kofNodes).length);
            """;
        String out = runJsProbe(tempDir, "kindchange", program, probe);
        assertEquals("done\ntag0=SPAN tag=BUTTON changed=true nodes=1",
                out, "a different root kind must still rebuild+prune (no aliasing across kinds)");
    }

    @Test
    void stateRoundTrip(@TempDir Path tempDir) throws IOException {
        String program = SRC_STATE_ROUND_TRIP;
        // JVM/Native: state getter is a no-op (0) and remove frees the
        // handle, so the live probe reports 0.
        both(tempDir, "state", program, "0\n0");
        assertEquals("42\n0", runJs(tempDir, "state", program),
                "state round-trip + leak probe on the JS target");
    }

    @Test
    void lifecycleOrder(@TempDir Path tempDir) throws IOException {
        String program = SRC_LIFECYCLE_ORDER;
        both(tempDir, "lifecycle", program, "0");
        assertEquals("mounted\ndisposed\n0", runJs(tempDir, "lifecycle", program),
                "mount runs onMount, remove runs onDispose and frees the component");
    }

    @Test
    void effectRunsOnMountAndCleansUpOnUnmount(@TempDir Path tempDir) throws IOException {
        String program = SRC_EFFECT_RUNS_ON_MOUNT_AND_CLEANS_UP_ON_UNMOUNT;
        both(tempDir, "effect", program, "0");
        assertEquals("effect-up\ndisposed\n0", runJs(tempDir, "effect", program),
                "effect runs once on mount; unmount frees the component");
    }

    @Test
    void viewReceivesStateAndRenders(@TempDir Path tempDir) throws IOException {
        String program = SRC_VIEW_RECEIVES_STATE_AND_RENDERS;
        Path source = tempDir.resolve("render.kf");
        Files.writeString(source, program);
        // JVM/Native: no-op handles — compiles and runs empty
        runJvm(source, tempDir.resolve("jvm-render"), "");
        runNative(source, tempDir.resolve("native-render"), "");
        // JS: the rendered page must show the state-driven label
        Path jsSource = tempDir.resolve("render-js.kf");
        Files.writeString(jsSource, program);
        CompilationResult js = driver.compile(jsSource, tempDir.resolve("js-render"), Target.JS);
        assertTrue(js.success(), "JS compilation should succeed: " + js.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        String html = dev.kof.runtime.KofJsRunner.runCaptureHtml(
                tempDir.resolve("js-render").resolve("Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        assertNotNull(html, "The window should serialize to HTML");
        assertTrue(html.contains("v=8"), "Re-rendered label must reflect the new state: " + html);
        assertFalse(html.contains("v=7"), "The stale view must not remain in the tree");
    }

    @Test
    void compositionBindMountsChildComponent(@TempDir Path tempDir) throws IOException {
        String program = SRC_COMPOSITION_BIND_MOUNTS_CHILD_COMPONENT;
        // JVM tracks handles in a live set (2 created, 1 removed → 1 alive);
        // Native handles are pure no-ops — the probe always reports 0.
        Path composeSrc = tempDir.resolve("compose.kf");
        Files.writeString(composeSrc, program);
        runJvm(composeSrc, tempDir.resolve("jvm-compose"), "1");
        runNative(composeSrc, tempDir.resolve("native-compose"), "0");
        // JS: bind mounted the child ("child-mounted"); remove frees only
        // the child — the parent is still alive.
        assertEquals("child-mounted\n1", runJs(tempDir, "compose", program),
                "bind mounts the child component; remove frees only the child");
    }

    @Test
    void onRegistersCentralizedHandler(@TempDir Path tempDir) throws IOException {
        // The KofJS host mock has no real DOM event dispatch; the probe
        // asserts the handler was registered on the component root element
        // (the centralised dispatch table used by kofUiComponentOn).
        String program = """
            main() {
                var app = Component(0)
                app.on("click", () -> println("clicked"))
                var win = Window("App")
                win.bind(app)
            }
            """;
        String probe = """
            import { kofUiNodesLive } from './kof-runtime.mjs';
            // find the component wrapper (only element with class kof-component)
            const root = document.getElementById("kof-root");
            const wrap = root.children[0].children[0];
            const handlers = wrap._kofHandlers && wrap._kofHandlers["click"];
            console.log("handlers=" + (handlers ? handlers.length : 0));
            console.log("live=" + kofUiNodesLive());
            """;
        assertEquals("handlers=1\nlive=1", runJsProbe(tempDir, "on", program, probe),
                "on() registers the handler in the component's centralised table");
    }

    @Test
    void batchingMultipleStateWritesSingleRender(@TempDir Path tempDir) throws IOException {
        // Multiple state writes in the same tick coalesce: the dirty queue
        // holds one entry per component, so the view runs once with the
        // FINAL state.
        String program = SRC_BATCHING_MULTIPLE_STATE_WRITES_SINGLE_RENDER;
        Path batchSrc = tempDir.resolve("batch.kf");
        Files.writeString(batchSrc, program);
        runJvm(batchSrc, tempDir.resolve("jvm-batch"), "");
        runNative(batchSrc, tempDir.resolve("native-batch"), "");
        Path jsSource = tempDir.resolve("batch-js.kf");
        Files.writeString(jsSource, program);
        CompilationResult js = driver.compile(jsSource, tempDir.resolve("js-batch"), Target.JS);
        assertTrue(js.success(), "JS compilation should succeed: " + js.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        String html = dev.kof.runtime.KofJsRunner.runCaptureHtml(
                tempDir.resolve("js-batch").resolve("Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        assertNotNull(html, "The window should serialize to HTML");
        assertTrue(html.contains("v=3"), "Final state must win after batching: " + html);
        assertFalse(html.contains("v=1"), "Intermediate states must not leak into the tree");
    }

    @Test
    void rerenderPrunesPreviousSubtreeFromRegistry(@TempDir Path tempDir) throws IOException {
        // §300 (found during the Phase 9 survey, docs/ui/architecture.md):
        // kofUiRender detached only the previous ROOT element from the DOM.
        // The view builder creates a fresh widget (and handle) on every
        // render, so every state change leaked the whole previous subtree
        // into __kofNodes (unbounded growth, silent — R6). Measured pre-fix
        // in the embedded host: 1 node after mount, 6 after 5 re-renders.
        String program = SRC_RERENDER_PRUNES_PREVIOUS_SUBTREE_FROM_REGISTRY;
        String probe = """
            console.log("nodes=" + Object.keys(window.__kofNodes).length);
            """;
        assertEquals("done\nnodes=1",
                runJsProbe(tempDir, "rerenderprune", program, probe),
                "re-render must prune the previous subtree from __kofNodes "
                + "(only the current view root may remain)");
    }

    @Test
    void rerenderReleasesDiscardedButtonActions(@TempDir Path tempDir) throws IOException {
        // §300 second face: the action table (window.__kofActions) is keyed by
        // the same handle; a discarded Button with an action kept its closure
        // reachable forever. kofUiRemoveSubtree now deletes the entry for
        // every pruned node (kofUiButtonRemove already did it on the
        // single-widget path).
        String program = SRC_RERENDER_RELEASES_DISCARDED_BUTTON_ACTIONS;
        String probe = """
            console.log("actions=" + Object.keys(window.__kofActions || {}).length);
            """;
        assertEquals("done\nactions=1",
                runJsProbe(tempDir, "rerenderactions", program, probe),
                "actions of discarded widgets must be released with the subtree");
    }

    @Test
    void unmountCascadesToChildren(@TempDir Path tempDir) throws IOException {
        String program = SRC_UNMOUNT_CASCADES_TO_CHILDREN;
        Path cascadeSrc = tempDir.resolve("cascade.kf");
        Files.writeString(cascadeSrc, program);
        runJvm(cascadeSrc, tempDir.resolve("jvm-cascade"), "0");
        runNative(cascadeSrc, tempDir.resolve("native-cascade"), "0");
        // parent.remove() unmounts the subtree: child's onDispose runs and
        // BOTH components are freed.
        assertEquals("child-disposed\n0", runJs(tempDir, "cascade", program),
                "unmount cascades top-down; the whole subtree is freed");
    }

    @Test
    void stressTenThousandMountUnmountCycles(@TempDir Path tempDir) throws IOException {
        // docs/ui/architecture.md §2.2: stress 10.000 ciclos mount/unmount
        // sem vazamento — uiNodesLive() must return to 0.
        String program = SRC_STRESS_TEN_THOUSAND_MOUNT_UNMOUNT_CYCLES;
        Path stressSrc = tempDir.resolve("stress.kf");
        Files.writeString(stressSrc, program);
        runJvm(stressSrc, tempDir.resolve("jvm-stress"), "0");
        runNative(stressSrc, tempDir.resolve("native-stress"), "0");
        assertEquals("0", runJs(tempDir, "stress", program),
                "10k mount/unmount cycles must leave no component alive (no leak)");
    }

    @Test
    void eventsBubbleUpTheComponentTree(@TempDir Path tempDir) throws IOException {
        // Fase 5 (docs/ui/architecture.md §2.5): emit(child) -> child handler
        // -> bubbles to parent. emit(parent) reaches only the parent.
        String program = SRC_EVENTS_BUBBLE_UP_THE_COMPONENT_TREE;
        Path evSrc = tempDir.resolve("evbubble.kf");
        Files.writeString(evSrc, program);
        runJvm(evSrc, tempDir.resolve("jvm-evbubble"), "");
        runNative(evSrc, tempDir.resolve("native-evbubble"), "");
        assertEquals("C,P:ping,P:ping,", runJs(tempDir, "evbubble", program),
                "child event bubbles to parent; parent event stays local");
    }

    @Test
    void stopPropagationBlocksBubbling(@TempDir Path tempDir) throws IOException {
        String program = SRC_STOP_PROPAGATION_BLOCKS_BUBBLING;
        Path evSrc = tempDir.resolve("evstop.kf");
        Files.writeString(evSrc, program);
        runJvm(evSrc, tempDir.resolve("jvm-evstop"), "");
        runNative(evSrc, tempDir.resolve("native-evstop"), "");
        assertEquals("C,", runJs(tempDir, "evstop", program),
                "stopPropagation must stop the event from reaching the parent");
    }

    @Test
    void storeSharesStateAcrossComponents(@TempDir Path tempDir) throws IOException {
        // Fase 8 (docs/ui/architecture.md §2.6): Store is shared observable
        // state. Subscribers receive the current value on subscribe and every
        // change on set(). storesLive() is the leak probe.
        String program = SRC_STORE_SHARES_STATE_ACROSS_COMPONENTS;
        Path storeSrc = tempDir.resolve("store.kf");
        Files.writeString(storeSrc, program);
        // JVM no-ops: get()=0, log vazio, get()=0, storesLive=1 (live set);
        // Native é no-op puro (storesLive=0).
        runJvm(storeSrc, tempDir.resolve("jvm-store"), "0\n\n0\n1");
        runNative(storeSrc, tempDir.resolve("native-store"), "0\n\n0\n0");
        assertEquals("10\ns=10,s=20,\n20\n1", runJs(tempDir, "store", program),
                "Store notifies subscribers on set; get returns the value; no store leak");
    }

    @Test
    void storeDrivesTwoComponentsIndependently(@TempDir Path tempDir) throws IOException {
        // two components subscribed to one store; the set() updates both via
        // their local state (minimal invalidation is preserved per component)
        String program = SRC_STORE_DRIVES_TWO_COMPONENTS_INDEPENDENTLY;
        Path storeSrc = tempDir.resolve("store2.kf");
        Files.writeString(storeSrc, program);
        // JVM: state getters sempre 0, storesLive=1; Native: storesLive=0.
        runJvm(storeSrc, tempDir.resolve("jvm-store2"), "0\n0\n1");
        runNative(storeSrc, tempDir.resolve("native-store2"), "0\n0\n0");
        // JS: each subscriber sees the value on subscribe (1 and 2) and on
        // set (5 and 10); the getters read the final local states.
        assertEquals("5\n10\n1", runJs(tempDir, "store2", program),
                "both components must be driven by the store");
    }

    @Test
    void storeUnsubscribeStopsDelivery(@TempDir Path tempDir) throws IOException {
        // §301: JS unsubscribe was a SILENT NO-OP — subscribe stored the
        // wrapper (fn.invoke.bind) but unsubscribe searched the RAW handle,
        // so indexOf never matched and the subscriber kept being notified.
        // Same identity contract as mq's unsubscribeStopsDelivery (JS now;
        // JVM/Native keep their documented Store no-ops).
        String program = SRC_STORE_UNSUBSCRIBE_STOPS_DELIVERY;
        Path src = tempDir.resolve("store-unsub.kf");
        Files.writeString(src, program);
        // JVM/Native: subscribe/set are no-ops — log stays empty.
        runJvm(src, tempDir.resolve("jvm-store-unsub"), "");
        runNative(src, tempDir.resolve("native-store-unsub"), "");
        // JS: current value on subscribe (n=1) + set(2); after unsubscribe
        // set(3) must NOT fire; the second unsubscribe is a no-op.
        assertEquals("n=1,n=2,", runJs(tempDir, "store-unsub", program),
                "unsubscribe must stop delivery (§301)");
    }

    @Test
    void appStateIsCreateOrGetSingleton(@TempDir Path tempDir) throws IOException {
        // Fase 8 (docs/ui/architecture.md §2.6): AppState(initial) is the
        // application-scoped root store — create-or-get singleton over the
        // Store machinery; the second `initial` is ignored. Methods are
        // exactly the Store's (get/set/subscribe/unsubscribe).
        String program = SRC_APP_STATE_IS_CREATE_OR_GET_SINGLETON;
        Path src = tempDir.resolve("appstate.kf");
        Files.writeString(src, program);
        // JVM: Store no-ops (get()=0, no notify) but the slot counts once.
        runJvm(src, tempDir.resolve("jvm-appstate"), "0\n0\n\n1");
        // Native: pure no-op — storesLive()=0.
        runNative(src, tempDir.resolve("native-appstate"), "0\n0\n\n0");
        // JS: ONE shared store — second call returns the same handle (10,
        // not 999); subscriber sees 10 on subscribe and 42 via the other handle.
        assertEquals("10\n10\nx=10,x=42,\n1", runJs(tempDir, "appstate", program),
                "AppState must be ONE shared store regardless of the call site");
    }

    @Test
    void appStateDrivesComponentsWithoutPropDrilling(@TempDir Path tempDir) throws IOException {
        // The app-state idiom: each component reads AppState itself — the
        // store handle is never passed around.
        String program = SRC_APP_STATE_DRIVES_COMPONENTS_WITHOUT_PROP_DRILLING;
        Path src = tempDir.resolve("appstate-shared.kf");
        Files.writeString(src, program);
        runJvm(src, tempDir.resolve("jvm-appstate-shared"), "0\n0\n1");
        runNative(src, tempDir.resolve("native-appstate-shared"), "0\n0\n0");
        assertEquals("7\n14\n1", runJs(tempDir, "appstate-shared", program),
                "each component reaches the same app state");
    }

    @Test
    void declaredUiAndMediaTypesCompileAndRun(@TempDir Path tempDir) throws IOException {
        // §179 (D-BACKEND-SEMANTICS #4): tipo kof.ui/kof.media DECLARADO
        // (var/param/campo/retorno) — antes o descritor JVM saía `LLabel;`
        // enquanto o valor do handle é int → VerifyError no load. Agora o
        // builtin é resolvido quando nada mais resolve o nome.
        String program = """
            Label makeLabel() {
                Label l = Label("x")
                return l
            }
            main() {
                Label l = makeLabel()
                println("ok")
            }
            """;
        Path source = tempDir.resolve("declui.kf");
        Files.writeString(source, program);
        runJvm(source, tempDir.resolve("jvm-declui"), "ok");
        runNative(source, tempDir.resolve("native-declui"), "ok");
        assertEquals("ok", runJs(tempDir, "declui", program),
                "declared kof.ui type must run on JS too");
    }

    @Test
    void nanRelationalIsIeeeOnAllTargets(@TempDir Path tempDir) throws IOException {
        // §101 (D-BACKEND-SEMANTICS #1): todo relacional com NaN é false e
        // `!=` é true (IEEE 754 / JLS 15.20.1). Cobre o caminho de VALOR e o
        // de SALTO (if/else) nos 3 alvos — x86 usava `setb`/`jb` sem o guard
        // de unordered (CF=1 no NaN) e dava `true`.
        String program = SRC_NAN_RELATIONAL_IS_IEEE_ON_ALL_TARGETS;
        String expected = SRC_NAN_RELATIONAL_IS_IEEE_ON_ALL_TARGETS_2;
        both(tempDir, "nanrel", program, expected);
        assertEquals(expected, runJs(tempDir, "nanrel", program),
                "NaN relational must be IEEE on JS too");
    }

    @Test
    void userClassShadowsBuiltinUiTypeName(@TempDir Path tempDir) throws IOException {
        // §179: o shadowing do usuário é preservado — uma classe de módulo
        // chamada `Label` vence o builtin kof.ui.Label.
        String program = SRC_USER_CLASS_SHADOWS_BUILTIN_UI_TYPE_NAME;
        both(tempDir, "shadow", program, "meu");
    }

    @Test
    void throwingViewIsReportedNotSilentlySwallowed(@TempDir Path tempDir) throws IOException {
        // §266-filha: kofUiRender/view/effect/onMount/onDispose catches ENGOLIAM
        // o throw do usuário → UI vazia SEM NENHUM erro no console do browser
        // (a lição do §266: só o loop estava errado; QUALQUER outro crash de
        // view continuava silencioso). Agora o catch mantém a resiliência (o
        // component quebrado não derruba os irmãos do flush) mas TORNA O ERRO
        // VISÍVEL (console.error com contexto + stack). JS-only: views só
        // executam no runner headless do JS (JVM/Native desktop não renderizam
        // sem janela, UiE2ETest prova por via própria). O harness runJs passa
        // o MESMO buffer p/ out E err, então console.error é capturável.
        String program = SRC_THROWING_VIEW_IS_REPORTED_NOT_SILENTLY_SWALLOWED;
        String out = runJs(tempDir, "throwview", program);
        assertTrue(out.contains("[kof] view render threw"),
                "o throw da view deve ser REPORTADO, não engolido — output: " + out);
        assertTrue(out.contains("Index out of bounds") || out.contains("out of bounds"),
                "a mensagem do throw original deve aparecer — output: " + out);
        assertTrue(out.contains("main-done"),
                "main continua (resiliência: um component quebrado não derruba o flush) — output: " + out);
    }

    @Test
    void throwingOnMountIsReportedNotSilentlySwallowed(@TempDir Path tempDir) throws IOException {
        // §266-filha: mesmo furo no caminho do onMount (try { om(); } catch {}
        // engolia). Prova que o helper cobre o callback do usuário no ciclo de
        // vida, não só o da view.
        String program = SRC_THROWING_ON_MOUNT_IS_REPORTED_NOT_SILENTLY_SWALLOWED;
        String out = runJs(tempDir, "throwmount", program);
        assertTrue(out.contains("[kof] onMount threw"),
                "o throw do onMount deve ser reportado — output: " + out);
        assertTrue(out.contains("main-done"),
                "o mount não deve derrubar o resto — output: " + out);
    }
}
