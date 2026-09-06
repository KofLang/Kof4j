package dev.kof.compiler;

/** kof-runtime.mjs — eventos de UI (dispatch/bubbling). */
final class JsRuntimeUiEvents {
    private JsRuntimeUiEvents() {
    }

    static final String UI_EVENT_RUNTIME = """
            // Fase 5 (docs/ui/architecture.md §2.5): target -> bubbles up the
            // component tree (child -> parent). The handler receives a Kof
            // Event with type + stopPropagation support.
            function kofUiDispatchEvent(targetId, domType, ev) {
                const kofEv = {
                    stopped: false,
                    // Kof accesses event kind as e.type() (a method call)
                    type() { return domType; },
                    stopPropagation() { this.stopped = true; },
                    // raw DOM event passthrough (null in the host mock)
                    raw: ev || null
                };
                let current = targetId;
                while (current != null) {
                    const n = kofUiComponents.get(current);
                    if (!n) break;
                    const h = n.el && n.el._kofHandlers && n.el._kofHandlers[domType];
                    if (h) {
                        for (const fn of h) {
                            try {
                                const f = kofUiRunFn(fn);
                                if (f) f(kofEv);
                            } catch (e) {}
                        }
                    }
                    if (kofEv.stopped) break;
                    current = n.parent ? n.parent.id : null;
                }
            }

            /** Test/entry hook: fires an event at a component (bubbles up). */
            export function kofUiEmit(c, type) {
                kofUiDispatchEvent(c, KOF_UI_EV[type] || type, null);
            }

            export function kofUiEventStop(ev) {
                if (ev && typeof ev.stopPropagation === "function") ev.stopPropagation();
            }

            // ── Store: shared observable state (docs/ui/architecture.md §2.6)
            // One Store, many component subscribers. set() notifies every
            // subscriber; a component that re-renders on its own state stays
            // with minimal invalidation — the Store only carries the value.
            const kofUiStores = new Map();
            let kofUiStoreSeq = 0;

            export function kofUiStoreNew(initial) {
                const id = ++kofUiStoreSeq;
                kofUiStores.set(id, { value: initial, subs: [] });
                return id;
            }

            export function kofUiStoreGet(s) {
                const st = kofUiStores.get(s);
                return st ? st.value : 0;
            }

            export function kofUiStoreSet(s, value) {
                const st = kofUiStores.get(s);
                if (!st) return;
                st.value = value;
                // notify every subscriber synchronously (ordering: subscription)
                for (const f of st.subs.slice()) {
                    try { f(value); } catch (e) {}
                }
            }

            export function kofUiStoreSubscribe(s, fn) {
                const st = kofUiStores.get(s);
                if (!st) return;
                const f = kofUiRunFn(fn);
                if (!f) return;
                st.subs.push(f);
                // the subscriber receives the current value immediately
                try { f(st.value); } catch (e) {}
            }

            export function kofUiStoreUnsubscribe(s, fn) {
                const st = kofUiStores.get(s);
                if (!st) return;
                const i = st.subs.indexOf(fn);
                if (i >= 0) st.subs.splice(i, 1);
            }

            export function kofUiStoresLive() {
                return kofUiStores.size;
            }

            // ── Fase 7: Navegação (docs/ui/architecture.md §2.9) ──────
            // Route = nome + builder(componente raiz). Navegar troca o
            // componente raiz da janela: unmount do antigo (lifecycle
            // completo) + mount do novo. back/forward = histórico em stack.
            const kofUiRouterState = {
                routes: {},          // name -> root component id
                current: null,       // nome da rota ativa
                param: null,         // params da rota ativa
                history: [],         // stack para back()
                forwardStack: [],    // stack para forward()
            };

            export function kofUiRouteRegister(name, rootComponent) {
                kofUiRouterState.routes[name] = rootComponent;
            }

            function kofUiRouterHost() {
                // primeiro window montado (o app de janela única usa o id 1)
                return typeof window !== "undefined" && window.__kofWindows
                    ? window.__kofWindows[1] : null;
            }

            function kofUiRouterShow(name, param, pushHistory) {
                const root = kofUiRouterState.routes[name];
                if (root === undefined || root === null) return false;
                const prev = kofUiRouterState.current;
                // desmonta qualquer rota montada que não seja o destino
                // (cobre o caso do bind inicial, que monta sem registrar current)
                for (const key of Object.keys(kofUiRouterState.routes)) {
                    if (key === name) continue;
                    const rc = kofUiRouterState.routes[key];
                    const rn = kofUiComponents.get(rc);
                    if (rn && rn.mounted) {
                        kofUiComponentUnmount(rc);
                        const rel = kofUiComponents.get(rc);
                        if (rel && rel.el && rel.el.parentNode) {
                            rel.el.parentNode.removeChild(rel.el);
                        }
                    }
                }
                if (pushHistory && prev !== null && prev !== name) {
                    kofUiRouterState.forwardStack.length = 0;
                    kofUiRouterState.history.push({ name: prev, param: kofUiRouterState.param });
                }
                kofUiRouterState.current = name;
                kofUiRouterState.param = param;
                const comp = kofUiComponents.get(root);
                if (comp && kofUiRouterHost()) {
                    if (comp.el && !comp.el.parentNode) {
                        kofUiRouterHost().appendChild(comp.el);
                    }
                    kofUiComponentMount(root);
                }
                return true;
            }

            function host() { return kofUiRouterHost(); }

            export function kofUiRouterGo1(name) {
                return kofUiRouterShow(name, null, true);
            }

            export function kofUiRouterGo2(name, param) {
                return kofUiRouterShow(name, param, true);
            }

            export function kofUiRouterReplace1(name) {
                return kofUiRouterNavigate(name, null);
            }

            export function kofUiRouterReplace2(name, param) {
                return kofUiRouterNavigate(name, param);
            }

            export function kofUiRouterBack() {
                if (kofUiRouterState.history.length === 0) return 0;
                const entry = kofUiRouterState.history.pop();
                if (kofUiRouterState.current !== null) {
                    kofUiRouterState.forwardStack.push(
                            { name: kofUiRouterState.current, param: kofUiRouterState.param });
                }
                const ok = kofUiRouterNavigate(entry.name, entry.param);
                return ok ? 1 : 0;
            }

            // troca sem mexer nos stacks (usada por back/forward)
            function kofUiRouterNavigate(name, param) {
                const root = kofUiRouterState.routes[name];
                if (root === undefined || root === null) return false;
                const prev = kofUiRouterState.current;
                if (prev !== null && prev !== name) {
                    const prevComp = kofUiRouterState.routes[prev];
                    if (prevComp !== undefined) {
                        kofUiComponentUnmount(prevComp);
                        const prevEl = kofUiComponents.get(prevComp);
                        if (prevEl && prevEl.el && prevEl.el.parentNode) {
                            prevEl.el.parentNode.removeChild(prevEl.el);
                        }
                    }
                }
                kofUiRouterState.current = name;
                kofUiRouterState.param = param;
                const comp = kofUiComponents.get(root);
                if (comp && kofUiRouterHost()) {
                    if (comp.el && !comp.el.parentNode) kofUiRouterHost().appendChild(comp.el);
                    kofUiComponentMount(root);
                }
                return true;
            }

            export function kofUiRouterForward() {
                if (kofUiRouterState.forwardStack.length === 0) return 0;
                const entry = kofUiRouterState.forwardStack.pop();
                if (kofUiRouterState.current !== null) {
                    kofUiRouterState.history.push(
                            { name: kofUiRouterState.current, param: kofUiRouterState.param });
                }
                const ok = kofUiRouterNavigate(entry.name, entry.param);
                return ok ? 1 : 0;
            }

            export function kofUiRouterParam() {
                return kofUiRouterState.param == null ? "" : String(kofUiRouterState.param);
            }

            export function kofUiRouterCurrent() {
                return kofUiRouterState.current == null ? "" : kofUiRouterState.current;
            }

            export function kofUiRouterDepth() {
                return kofUiRouterState.history.length;
            }
            """;

}
