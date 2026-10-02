package dev.kof.compiler;

/**
 * Programas Kof do E2E de componentes ({@code ComponentCoreE2ETest}), hoisted de inline para
 * constantes. Vive fora da classe de teste (Fase 3 da arquitetura de testes,
 * {@code D-TEST-ARCHITECTURE-GO}) e do harness para que ambos fiquem abaixo do
 * limite de 500 linhas; os testes e o nome da classe seguem no {@code ComponentCoreE2ETest}.
 */
abstract class ComponentCorePrograms {

    static final String SRC_COMPONENT_SUBSCRIPTION_DIES_WITH_COMPONENT = """
            main() {
                var store = Store(10)
                var app = Component(0)
                app.view((s: Int) -> {
                    store.subscribe((v: Int) -> println("sub=" + v))
                    return Label("x")
                })
                var win = Window("App")
                win.bind(app)
                win.show()
                println("shown")
            }
            """;

    static final String SRC_APP_SCOPED_SUBSCRIPTION_STAYS_MANUAL = """
            main() {
                var store = Store(1)
                store.subscribe((v: Int) -> println("app=" + v))
                var app = Component(0)
                app.view((s: Int) -> { return Label("x") })
                var win = Window("A")
                win.bind(app)
                win.show()
                println("shown")
            }
            """;

    static final String SRC_STABLE_ROOT_KIND_REUSES_NODE_AND_HANDLE = """
            main() {
                var app = Component(0)
                var win = Window("App")
                app.view((s: Int) -> { return Label("v=" + s) })
                win.bind(app)
                win.show()
                println("done")
            }
            """;

    static final String SRC_STABLE_ROOT_KIND_REUSES_NODE_AND_HANDLE_2 = """
            import { kofUiComponentStateSet } from './kof-runtime.mjs';
            const before = Object.keys(window.__kofNodes).join(",");
            const el = Object.values(window.__kofNodes)[0];
            const text0 = el.textContent;
            kofUiComponentStateSet(1, 7);
            const after = Object.keys(window.__kofNodes).join(",");
            const el2 = Object.values(window.__kofNodes)[0];
            console.log("keysBefore=" + before + " keysAfter=" + after
                + " sameNode=" + (el === el2) + " text0=" + text0 + " text=" + el2.textContent);
            """;

    static final String SRC_BUTTON_ROOT_ACTION_SURVIVES_REUSE_WITHOUT_DOUBLING = """
            main() {
                var app = Component(0)
                var win = Window("App")
                app.view((s: Int) -> { return Button("b" + s, () -> println("fired=" + s)) })
                win.bind(app)
                app.stateSet(4)
                win.show()
                println("done")
            }
            """;

    static final String SRC_KIND_CHANGE_STILL_REBUILDS_AND_PRUNES = """
            main() {
                var app = Component(0)
                var win = Window("App")
                app.view((s: Int) -> {
                    if (s < 5) { return Label("L" + s) }
                    return Button("B" + s, () -> println("late"))
                })
                win.bind(app)
                app.stateSet(1)
                win.show()
                println("done")
            }
            """;

    static final String SRC_STATE_ROUND_TRIP = """
            main() {
                var app = Component(0)
                app.state = 41
                app.state = app.state + 1
                println(app.state)
                app.remove()
                println(uiNodesLive())
            }
            """;

    static final String SRC_LIFECYCLE_ORDER = """
            main() {
                var app = Component(0)
                app.onMount(() -> println("mounted"))
                app.onDispose(() -> println("disposed"))
                var win = Window("App")
                win.bind(app)
                app.view((s: Int) -> {
                    var l = Label("v=" + s)
                    win.bind(l)
                    return l
                })
                app.remove()
                println(uiNodesLive())
            }
            """;

    static final String SRC_EFFECT_RUNS_ON_MOUNT_AND_CLEANS_UP_ON_UNMOUNT = """
            main() {
                var app = Component(0)
                app.effect(() -> println("effect-up"))
                app.onDispose(() -> println("disposed"))
                var win = Window("App")
                win.bind(app)
                app.remove()
                println(uiNodesLive())
            }
            """;

    static final String SRC_VIEW_RECEIVES_STATE_AND_RENDERS = """
            main() {
                var app = Component(7)
                var win = Window("App")
                win.bind(app)
                app.view((s: Int) -> {
                    var l = Label("v=" + s)
                    win.bind(l)
                    return l
                })
                app.state = 8
                win.show()
            }
            """;

    static final String SRC_COMPOSITION_BIND_MOUNTS_CHILD_COMPONENT = """
            main() {
                var parent = Component(0)
                var child = Component(5)
                child.onMount(() -> println("child-mounted"))
                var win = Window("App")
                win.bind(parent)
                parent.bind(child)
                child.remove()
                println(uiNodesLive())
            }
            """;

    static final String SRC_BATCHING_MULTIPLE_STATE_WRITES_SINGLE_RENDER = """
            main() {
                var app = Component(0)
                var win = Window("App")
                win.bind(app)
                app.view((s: Int) -> {
                    var l = Label("v=" + s)
                    win.bind(l)
                    return l
                })
                app.state = 1
                app.state = 2
                app.state = 3
                win.show()
            }
            """;

    static final String SRC_RERENDER_PRUNES_PREVIOUS_SUBTREE_FROM_REGISTRY = """
            main() {
                var app = Component(0)
                var win = Window("App")
                app.view((s: Int) -> { return Label("v=" + s) })
                win.bind(app)
                app.stateSet(1)
                app.stateSet(2)
                app.stateSet(3)
                app.stateSet(4)
                app.stateSet(5)
                win.show()
                println("done")
            }
            """;

    static final String SRC_RERENDER_RELEASES_DISCARDED_BUTTON_ACTIONS = """
            main() {
                var app = Component(0)
                var win = Window("App")
                app.view((s: Int) -> {
                    return Button("b" + s, () -> println("clicked"))
                })
                win.bind(app)
                app.stateSet(1)
                app.stateSet(2)
                win.show()
                println("done")
            }
            """;

    static final String SRC_UNMOUNT_CASCADES_TO_CHILDREN = """
            main() {
                var parent = Component(0)
                var child = Component(1)
                child.onDispose(() -> println("child-disposed"))
                var win = Window("App")
                win.bind(parent)
                parent.bind(child)
                parent.remove()
                println(uiNodesLive())
            }
            """;

    static final String SRC_STRESS_TEN_THOUSAND_MOUNT_UNMOUNT_CYCLES = """
            main() {
                var win = Window("App")
                var i = 0
                while (i < 10000) {
                    var app = Component(i)
                    app.onMount(() -> {})
                    app.onDispose(() -> {})
                    app.effect(() -> {})
                    win.bind(app)
                    app.view((s: Int) -> {
                        var l = Label("n=" + s)
                        win.bind(l)
                        return l
                    })
                    app.remove()
                    i = i + 1
                }
                println(uiNodesLive())
            }
            """;

    static final String SRC_EVENTS_BUBBLE_UP_THE_COMPONENT_TREE = """
            main() {
                var parent = Component(0)
                var child = Component(1)
                var log = ""
                parent.on("ping", (e: Event) -> { log = log + "P:" + e.type() + "," })
                child.on("ping", (e: Event) -> { log = log + "C," })
                var win = Window("App")
                win.bind(parent)
                parent.bind(child)
                emit(child, "ping")
                emit(parent, "ping")
                println(log)
            }
            """;

    static final String SRC_STOP_PROPAGATION_BLOCKS_BUBBLING = """
            main() {
                var parent = Component(0)
                var child = Component(1)
                var log = ""
                parent.on("ping", (e: Event) -> { log = log + "P," })
                child.on("ping", (e: Event) -> { log = log + "C,"; e.stopPropagation() })
                var win = Window("App")
                win.bind(parent)
                parent.bind(child)
                emit(child, "ping")
                println(log)
            }
            """;

    static final String SRC_STORE_SHARES_STATE_ACROSS_COMPONENTS = """
            main() {
                var store = Store(10)
                var log = ""
                store.subscribe((v: Int) -> { log = log + "s=" + v + "," })
                println(store.get())
                store.set(20)
                println(log)
                println(store.get())
                println(storesLive())
            }
            """;

    static final String SRC_STORE_DRIVES_TWO_COMPONENTS_INDEPENDENTLY = """
            main() {
                var store = Store(1)
                var a = Component(0)
                var b = Component(0)
                a.on("tick", (e: Event) -> {})
                store.subscribe((v: Int) -> { a.state = v })
                store.subscribe((v: Int) -> { b.state = v * 2 })
                var win = Window("App")
                win.bind(a)
                a.bind(b)
                store.set(5)
                println(a.state)
                println(b.state)
                println(storesLive())
            }
            """;

    static final String SRC_STORE_UNSUBSCRIBE_STOPS_DELIVERY = """
            main() {
                var store = Store(1)
                var log = ""
                var h = (v: Int) -> { log = log + "n=" + v + "," }
                store.subscribe(h)
                store.set(2)
                store.unsubscribe(h)
                store.set(3)
                store.unsubscribe(h)
                println(log)
            }
            """;

    static final String SRC_APP_STATE_IS_CREATE_OR_GET_SINGLETON = """
            main() {
                var a1 = AppState(10)
                var a2 = AppState(999)
                println(a1.get())
                println(a2.get())
                var log = ""
                a1.subscribe((v: Int) -> { log = log + "x=" + v + "," })
                a2.set(42)
                println(log)
                println(storesLive())
            }
            """;

    static final String SRC_APP_STATE_DRIVES_COMPONENTS_WITHOUT_PROP_DRILLING = """
            main() {
                AppState(0)
                var win = Window("App")
                var a = Component(0)
                var b = Component(0)
                AppState(0).subscribe((v: Int) -> { a.state = v })
                AppState(0).subscribe((v: Int) -> { b.state = v * 2 })
                win.bind(a)
                a.bind(b)
                AppState(0).set(7)
                println(a.state)
                println(b.state)
                println(storesLive())
            }
            """;

    static final String SRC_NAN_RELATIONAL_IS_IEEE_ON_ALL_TARGETS = """
            Double nan(Double zero) {
                return zero / zero
            }
            Float nanf(Float zero) {
                return zero / zero
            }
            main() {
                var n = nan(0.0)
                println(n < 1.0)
                println(n <= 1.0)
                println(n > 1.0)
                println(n >= 1.0)
                println(n == 1.0)
                println(n != 1.0)
                println(n == n)
                println(n != n)
                println(1.0 < n)
                println(1.0 <= n)
                println(1.0 > n)
                println(1.0 >= n)
                var f = nanf(0.0f)
                println(f < 1.0f)
                println(f <= 1.0f)
                println(f > 1.0f)
                println(f >= 1.0f)
                println(f == 1.0f)
                println(f != 1.0f)
                if (n < 1.0) { println(1) } else { println(0) }
                if (n <= 1.0) { println(1) } else { println(0) }
                if (n > 1.0) { println(1) } else { println(0) }
                if (n >= 1.0) { println(1) } else { println(0) }
                if (n == n) { println(1) } else { println(0) }
                if (n != n) { println(1) } else { println(0) }
                if (1.0 < n) { println(1) } else { println(0) }
                if (1.0 > n) { println(1) } else { println(0) }
            }
            """;

    static final String SRC_NAN_RELATIONAL_IS_IEEE_ON_ALL_TARGETS_2 = """
            false
            false
            false
            false
            false
            true
            false
            true
            false
            false
            false
            false
            false
            false
            false
            false
            false
            true
            0
            0
            0
            0
            0
            1
            0
            0""";

    static final String SRC_USER_CLASS_SHADOWS_BUILTIN_UI_TYPE_NAME = """
            class Label {
                String value
                Label(String value) { this.value = value }
                String get() { return this.value }
            }
            main() {
                Label l = Label("meu")
                println(l.get())
            }
            """;

    static final String SRC_THROWING_VIEW_IS_REPORTED_NOT_SILENTLY_SWALLOWED = """
            main() {
                var win = Window("App")
                var bad = Component(0)
                win.bind(bad)
                bad.view((s: Int) -> {
                    var junk = listOf(1, 2).get(9)
                    return Label("unreachable " + junk)
                })
                bad.mount()
                var ok = Component(0)
                win.bind(ok)
                ok.view((s: Int) -> { return Label("sibling-ok") })
                ok.mount()
                println("main-done")
            }
            """;

    static final String SRC_THROWING_ON_MOUNT_IS_REPORTED_NOT_SILENTLY_SWALLOWED = """
            main() {
                var app = Component(0)
                app.onMount(() -> {
                    var junk = listOf(1, 2).get(9)
                    println("unreachable " + junk)
                })
                var win = Window("App")
                win.bind(app)
                app.mount()
                println("main-done")
            }
            """;
}
