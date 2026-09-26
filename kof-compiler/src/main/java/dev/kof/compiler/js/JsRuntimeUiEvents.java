package dev.kof.compiler.js;

/** kof-runtime.mjs — eventos de UI (dispatch/bubbling). */
public final class JsRuntimeUiEvents {
    private JsRuntimeUiEvents() {
    }

    static  String UI_EVENT_RUNTIME = """
            // Fase 5 (docs/ui/architecture.md §2.5): target -> bubbles up the
            // component tree (child -> parent). The handler receives a Kof
            // Event with type + stopPropagation support.
            function kofUiMakeEvent(domType, ev) {
                const raw = ev || null;
                return {
                    stopped: false,
                    // Kof accesses event kind as e.type() (a method call)
                    type() { return domType; },
                    stopPropagation() { this.stopped = true; },
                    // UI006: key/value/x/y do DOM event real (null/vazio no
                    // host mock e no emit sintético).
                    key() {
                        return raw && typeof raw.key === "string" ? raw.key : "";
                    },
                    value() {
                        if (!raw) return "";
                        const t = raw.target;
                        return t && typeof t.value === "string" ? t.value : "";
                    },
                    x() { return raw && typeof raw.clientX === "number" ? raw.clientX : 0; },
                    y() { return raw && typeof raw.clientY === "number" ? raw.clientY : 0; },
                    // UI006 residual: alvo do evento como id do nó (fallback
                    // tagName minúscula quando sem setId; "" fora do browser).
                    target() {
                        const t = raw && raw.target;
                        if (!t) return "";
                        return t.id ? String(t.id)
                                : (t.tagName ? t.tagName.toLowerCase() : "");
                    },
                    relatedTarget() {
                        const t = raw && raw.relatedTarget;
                        if (!t) return "";
                        return t.id ? String(t.id)
                                : (t.tagName ? t.tagName.toLowerCase() : "");
                    },
                    // raw DOM event passthrough (null in the host mock)
                    raw: raw
                };
            }

            // UI006: dispatch de widget DOM (fora da árvore de Component) —
            // o handler recebe o kofEv construído do evento DOM real.
            function kofUiDispatchWidgetEvent(id, domType, ev) {
                const node = window.__kofNodes && window.__kofNodes[id];
                if (!node) return;
                const h = node._kofHandlers && node._kofHandlers[domType];
                if (!h) return;
                const kofEv = kofUiMakeEvent(domType, ev);
                for (const fn of h) {
                    try {
                        if (typeof fn.invoke === "function") fn.invoke(kofEv);
                        else fn(kofEv);
                    } catch (e) {}
                }
            }

            function kofUiDispatchEvent(targetId, domType, ev) {
                const kofEv = kofUiMakeEvent(domType, ev);
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

            // UIW050: acessores de `e: Event` como funções de runtime. O IR
            // baixa `e.value()/e.key()/e.x()/e.y()/e.type()/e.target()/
            // e.relatedTarget()` para `kof_ui_event_*` (receiver = evento) em
            // todos os alvos; aqui o evento é o objeto de kofUiMakeEvent.
            export function kofUiEventValue(ev) {
                if (!ev) return "";
                return typeof ev.value === "function" ? ev.value() : "";
            }

            export function kofUiEventKey(ev) {
                if (!ev) return "";
                return typeof ev.key === "function" ? ev.key() : "";
            }

            export function kofUiEventX(ev) {
                if (!ev) return 0;
                return typeof ev.x === "function" ? ev.x() : 0;
            }

            export function kofUiEventY(ev) {
                if (!ev) return 0;
                return typeof ev.y === "function" ? ev.y() : 0;
            }

            export function kofUiEventTarget(ev) {
                if (!ev) return "";
                return typeof ev.target === "function" ? String(ev.target()) : "";
            }

            export function kofUiEventRelatedTarget(ev) {
                if (!ev) return "";
                return typeof ev.relatedTarget === "function" ? String(ev.relatedTarget()) : "";
            }

            // ── Store: shared observable state (docs/ui/architecture.md §2.6)
            // One Store, many component subscribers. set() notifies every
            // subscriber; a component that re-renders on its own state stays
            // with minimal invalidation — the Store only carries the value.
            const kofUiStores = new Map();
            let kofUiStoreSeq = 0;

            export function kofUiStoreNew(initial, ownerless) {
                const id = ++kofUiStoreSeq;
                kofUiStores.set(id, { value: initial, subs: [] });
                // D-COMPLETE-FIRST item 4: a store CREATED during a component's
                // lifecycle belongs to it (same context rule as the
                // D-UI-AUTOUNSUB subscriptions) and dies deterministically at
                // unmount. Created outside a component (or via kofUiAppState,
                // app by definition) it stays ownerless and manual by design.
                if (!ownerless && kofUiCurrentComponent) {
                    const comp = kofUiCurrentComponent;
                    (comp._autoStores = comp._autoStores || []).push(id);
                }
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
                for (const sub of st.subs.slice()) {
                    try { sub.f(value); } catch (e) {}
                }
            }

            export function kofUiStoreSubscribe(s, fn) {
                const st = kofUiStores.get(s);
                if (!st) return;
                const f = kofUiRunFn(fn);
                if (!f) return;
                // §301: keep the RAW handle as the unsubscribe key — the
                // wrapper (fn.invoke.bind) is a new object every call, so
                // indexOf(fn) on wrappers could never match.
                st.subs.push({ raw: fn, f: f });
                if (kofUiCurrentComponent) {
                    // D-UI-AUTOUNSUB (A): made inside a component's lifecycle →
                    // bound to that component, dropped when it leaves the tree.
                    const comp = kofUiCurrentComponent;
                    (comp._autoSubs = comp._autoSubs || []).push({ store: s, raw: fn });
                }
                // the subscriber receives the current value immediately
                try { f(st.value); } catch (e) {}
            }

            export function kofUiStoreUnsubscribe(s, fn) {
                const st = kofUiStores.get(s);
                if (!st) return;
                // remove exactly one matching subscription (first), like a
                // listener list; a fn never subscribed is a silent no-op.
                for (let i = 0; i < st.subs.length; i++) {
                    if (st.subs[i].raw === fn) { st.subs.splice(i, 1); return; }
                }
            }

            export function kofUiStoresLive() {
                return kofUiStores.size;
            }

            export function kofUiSubscriptionsLive() {
                // Leak lock probe (D-COMPLETE-FIRST item 4): every subscriber
                // registered on a live store. Manual unsubscribe, auto
                // unsubscribe and component store death all flow through here
                // — the lock is this number hitting 0.
                let n = 0;
                for (const st of kofUiStores.values()) n += st.subs.length;
                return n;
            }

            // Fase 8 §2.6 / D-UI-APPSTATE: application-scoped root store —
            // create-or-get singleton over the Store machinery (one slot per
            // process; the `initial` of later calls is ignored by design).
            let kofUiAppStateId = null;
            export function kofUiAppState(initial) {
                if (kofUiAppStateId === null) kofUiAppStateId = kofUiStoreNew(initial, true);
                return kofUiAppStateId;
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
