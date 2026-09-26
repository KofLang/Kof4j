package dev.kof.compiler.js;

/** kof-runtime.mjs — Component Core + Link/Image/Icon. */
public final class JsRuntimeUiComponents {
    private JsRuntimeUiComponents() {
    }

    static  String UI_COMPONENT_RUNTIME = """
            // ── Component Core (docs/ui/architecture.md) ─────────────
            // A UI is a tree of components. A Component node carries: identity,
            // state (Int), a view builder, lifecycle hooks, effects (auto-cleaned)
            // and events. Rendering is KofJS; the framework (not the widget)
            // owns the tree, the render schedule and the lifecycle.
            const kofUiComponents = new Map();
            let kofNodeSeq = 0;
            let kofUiFlushing = false;
            const kofUiDirty = [];
            // D-UI-AUTOUNSUB (A): the component whose lifecycle is executing
            // right now (view render / onMount / effect). Store.subscribe
            // consults it to bind the subscription to the component.
            let kofUiCurrentComponent = null;
            const KOF_UI_EV = {
                click: "click", dblclick: "dblclick", mousedown: "mousedown",
                mouseup: "mouseup", mousemove: "mousemove", mouseenter: "mouseenter",
                mouseleave: "mouseleave", wheel: "wheel", keydown: "keydown",
                keyup: "keyup", focus: "focus", blur: "blur", input: "input", change: "change"
            };

            function kofUiIsNode(id) {
                return window.__kofNodes && Object.prototype.hasOwnProperty.call(window.__kofNodes, id);
            }
            function kofUiParentOf(id) {
                const n = window.__kofNodes && window.__kofNodes[id];
                return n && n.parentNode ? n : null;
            }
            function kofUiSubtreeIds(rootId) {
                // all nodes reachable from rootId (BFS over the DOM tree)
                const out = [];
                const q = [window.__kofNodes[rootId]];
                while (q.length > 0) {
                    const n = q.shift();
                    if (!n || n._kofGone) continue;
                    out.push(n);
                    const kids = Array.from(n.children || []);
                    for (const k of kids) q.push(k);
                }
                return out;
            }
            function kofUiDetachDom(id) {
                const n = window.__kofNodes && window.__kofNodes[id];
                if (n && n.parentNode && n.parentNode.removeChild) {
                    n.parentNode.removeChild(n);
                }
            }
            function kofUiPruneNode(n) {
                // §300 prune applied to a DOM node (used by root-kind reuse where
                // the root handle must survive). Every registry key that maps to
                // the node dies with it — D-UI-DIFF (B) aliases the fresh root
                // handle onto the old node, so a node can have TWO keys and the
                // old single-key `break` would leak the alias forever.
                if (!n) return;
                const q = [n];
                while (q.length > 0) {
                    const x = q.shift();
                    if (!x || x._kofGone) continue;
                    for (const k of Array.from(x.children || [])) q.push(k);
                    if (x.parentNode) x.parentNode.removeChild(x);
                    x._kofGone = true;
                    for (const key in window.__kofNodes) {
                        if (window.__kofNodes[key] === x) {
                            delete window.__kofNodes[key];
                            // §300: the action table is a SECOND global keyed by
                            // the same handle — without this the discarded
                            // Button action (and its closure) stays reachable
                            // forever. __kofFormSubmits is a third (Form).
                            if (window.__kofActions) delete window.__kofActions[key];
                            if (window.__kofFormSubmits) delete window.__kofFormSubmits[key];
                        }
                    }
                }
            }
            function kofUiRemoveSubtree(rootId) {
                // remove the DOM subtree of a widget id and prune the registry
                if (!kofUiIsNode(rootId)) return;
                kofUiPruneNode(window.__kofNodes[rootId]);
            }

            function kofUiRunFn(fn) {
                // a Kof lambda compiles to a class with an invoke() method;
                // plain functions pass through.
                return fn && typeof fn.invoke === "function" ? fn.invoke.bind(fn) : fn;
            }

            function kofUiOnDom(node, type, fn) {
                // single funnel for widget-node DOM listeners so D-UI-DIFF (B)
                // reuse can MOVE them from the discarded node to the survivor
                // (removeEventListener needs the exact reference). Component
                // wrapper (.on) listeners live on the component div, never on a
                // reused widget root, and stay outside this funnel.
                if (!node || typeof node.addEventListener !== "function") return;
                node._kofDomListeners = node._kofDomListeners || [];
                node._kofDomListeners.push({ t: type, f: fn });
                node.addEventListener(type, fn);
            }

            function kofUiCopyValueProps(oldEl, newEl) {
                // (B) contract, "value-bearing properties": attributes (style
                // strings, class, widget props), the CSSOM object the runtime
                // writes (n.style.X = ...), text on a leaf, and the direct
                // properties the widget families set. User input wins over an
                // absent declaration: `value`/`checked` are copied ONLY when the
                // fresh node carries a non-default value.
                const names = oldEl.getAttributeNames
                    ? Array.from(new Set(Object.keys(newEl._attrs || {}).concat(newEl.getAttributeNames())))
                    : Object.keys(newEl._attrs || {});
                for (const a of names) {
                    if (a === "value" || a === "checked") continue;
                    const v = newEl.getAttribute(a);
                    if (v !== null) oldEl.setAttribute(a, v);
                }
                for (const k in oldEl.style) delete oldEl.style[k];
                for (const k in (newEl.style || {})) oldEl.style[k] = newEl.style[k];
                for (const k in (newEl.dataset || {})) oldEl.dataset[k] = newEl.dataset[k];
                const kids = newEl.children || [];
                if (kids.length === 0 && typeof newEl.textContent === "string") {
                    oldEl.textContent = newEl.textContent;
                }
                if (typeof newEl.value === "string" && newEl.value !== "") oldEl.value = newEl.value;
                if (newEl.checked) oldEl.checked = true;
            }

            function kofUiRehomeWidgetHandlers(oldEl, oldId, newEl) {
                // (B) detail: handlers registered via kofUiWidgetOn close over
                // the handle of the node they were set on — the fresh node dies,
                // so their dispatch is RE-REGISTERED on the surviving handle
                // instead of moving the closure (which would dispatch through a
                // dead id). Wrapper fns carry a marker and are skipped when the
                // raw DOM listeners move over in kofUiReuseRoot.
                const hm = newEl._kofHandlers || {};
                for (const domType in hm) {
                    for (const h of hm[domType]) {
                        kofUiWidgetOnHandle(oldId, domType, h);
                    }
                }
            }

            function kofUiReuseRoot(oldEl, newEl, oldId, newId) {
                // D-UI-DIFF (B): same root kind across renders — the OLD node
                // (and its focus/caret/scroll) survives; the fresh node's
                // properties and children migrate onto it and it is discarded.
                // The OLD handle keeps its identity and stays the ONLY
                // __kofNodes key for the node (alias-free: the §300 registry
                // bound holds), so the per-handle dispatch tables are re-homed
                // from newId to oldId.
                for (const kid of Array.from(oldEl.children || [])) kofUiPruneNode(kid);
                for (const l of oldEl._kofDomListeners || []) {
                    if (oldEl.removeEventListener) oldEl.removeEventListener(l.t, l.f);
                }
                oldEl._kofDomListeners = [];
                oldEl._kofHandlers = {};
                for (const k of oldEl._kofStaleForms || []) {
                    if (window.__kofFormSubmits) delete window.__kofFormSubmits[k];
                }
                oldEl._kofStaleForms = [];
                kofUiCopyValueProps(oldEl, newEl);
                for (const kid of Array.from(newEl.children || [])) oldEl.appendChild(kid);
                kofUiRehomeWidgetHandlers(oldEl, oldId, newEl);
                if (window.__kofActions && window.__kofActions[newId] !== undefined) {
                    window.__kofActions[oldId] = window.__kofActions[newId];
                    delete window.__kofActions[newId];
                }
                if (window.__kofFormSubmits && window.__kofFormSubmits[newId] !== undefined) {
                    window.__kofFormSubmits[oldId] = window.__kofFormSubmits[newId];
                    oldEl._kofStaleForms.push(newId);
                }
                for (const l of newEl._kofDomListeners || []) {
                    if (l.f && l.f.__kofWidgetWrapper) continue;
                    kofUiOnDom(oldEl, l.t, l.f);
                }
                newEl._kofGone = true;
                newEl._kofHandlers = undefined;
                newEl._kofDomListeners = undefined;
                if (newEl.parentNode) newEl.parentNode.removeChild(newEl);
                delete window.__kofNodes[newId];
            }

            function kofUiScheduleFlush() {
                if (kofUiFlushing) {
                    // already rendering — batch the rest
                    if (typeof Promise !== "undefined" && Promise.resolve) {
                        Promise.resolve().then(() => kofUiFlushQueue());
                    }
                    return;
                }
                kofUiFlushQueue();
            }

            function kofUiFlushQueue() {
                if (kofUiFlushing) return;
                kofUiFlushing = true;
                try {
                    while (kofUiDirty.length > 0) {
                        const id = kofUiDirty.shift();
                        const c = kofUiComponents.get(id);
                        if (c && c.mounted && c.view) {
                            kofUiRender(c);
                        }
                    }
                } finally {
                    kofUiFlushing = false;
                }
            }

            function kofUiReportError(where, e) {
                // §266-filha: os catches deste módulo ENGOLIAM o throw do
                // código do usuário — view que crashava = UI vazia SEM NENHUMA
                // mensagem no console do browser (a lição do §266: o loop estava
                // errado, mas QUALQUER outro crash de view continuava silencioso
                // p/ UI e barulhento só headless). Mantém a resiliência (um
                // component quebrado não derruba os irmãos do flush) mas TORNA
                // O ERRO VISÍVEL: console.error com contexto + o throw original
                // (stack quando houver), espelhando o stderr do JVM/Script.
                const detail = e && (e.stack || e.message) ? (e.stack || e.message) : String(e);
                try {
                    (console.error || console.log)("[kof] " + where + ": " + detail);
                } catch (ignored) {}
            }

            function kofUiRender(c) {
                // rebuild the component's child subtree: run the view builder
                // with the current state, then swap the fresh DOM in place.
                // (handle diffing is a Phase-9 optimization)
                let rootId = 0;
                const prevCtx = kofUiCurrentComponent;
                kofUiCurrentComponent = c;
                try {
                    const v = kofUiRunFn(c.view);
                    rootId = v ? v(c.state) : 0;
                } catch (e) {
                    kofUiReportError("view render threw for component " + (c && c.name), e);
                    rootId = 0;
                } finally {
                    kofUiCurrentComponent = prevCtx;
                }
                if (c.el) {
                    const oldEl = window.__kofNodes && window.__kofNodes[c.root];
                    const rootEl = window.__kofNodes && window.__kofNodes[rootId];
                    if (oldEl && rootEl && oldEl !== rootEl && c.root !== rootId
                        && oldEl.tagName === rootEl.tagName) {
                        // D-UI-DIFF (B): stable root kind → the old DOM node
                        // survives (focus/caret/scroll preserved); the old root
                        // handle keeps its identity. Different kind (or error
                        // render) → rebuild + prune exactly as §300.
                        kofUiReuseRoot(oldEl, rootEl, c.root, rootId);
                        rootId = c.root;
                    } else {
                        if (c.root !== rootId) {
                            // §300: the previous render's subtree must be pruned from
                            // BOTH the DOM and __kofNodes (detaching only the root
                            // leaked every old node — unbounded, silent).
                            kofUiRemoveSubtree(c.root);
                        }
                        if (rootEl && rootEl.parentNode !== c.el) {
                            c.el.appendChild(rootEl);
                        }
                    }
                }
                c.root = rootId;
            }

            export function kofUiComponentNew(state) {
                // §261: componentes e nós DOM compartilham UMA sequencia de handles
                // (kofNodeSeq). Antes o component tinha contador proprio (kofUiSeq),
                // entao window.bind(id) achava um node com o MESMO id de um component
                // (busca component-first) e montava o objeto errado — o widget real
                // ficava orfao no __kofNodes (medido no Chrome 16/09, ReconfigButton
                // nunca aparecia quando um Slider era montado antes dele).
                const id = ++kofNodeSeq;
                const c = {
                    id: id, name: "c" + id, state: state,
                    view: null, mounted: false, disposed: false,
                    el: null, root: null, onMountFn: null, onDisposeFn: null,
                    effects: [], effectFns: [],
                    parent: null, children: []
                };
                kofUiComponents.set(id, c);
                if (typeof document !== "undefined") {
                    const wrap = document.createElement("div");
                    wrap.className = "kof-component";
                    c.el = wrap;
                }
                return id;
            }

            export function kofUiComponentStateGet(c) {
                const n = kofUiComponents.get(c);
                return n ? n.state : 0;
            }

            export function kofUiComponentStateSet(c, value) {
                const n = kofUiComponents.get(c);
                if (!n) return;
                n.state = value;
                // state change is the invalidation point: mark ONLY this
                // component dirty and schedule a batched re-render.
                if (n.mounted && !kofUiDirty.includes(c)) {
                    kofUiDirty.push(c);
                }
                kofUiScheduleFlush();
            }

            export function kofUiComponentView(c, builder) {
                const n = kofUiComponents.get(c);
                if (!n) return;
                n.view = builder;
                if (n.mounted) {
                    if (!kofUiDirty.includes(c)) kofUiDirty.push(c);
                    kofUiScheduleFlush();
                }
            }

            export function kofUiComponentOnMount(c, fn) {
                const n = kofUiComponents.get(c);
                if (n) n.onMountFn = fn;
            }

            export function kofUiComponentOnDispose(c, fn) {
                const n = kofUiComponents.get(c);
                if (n) n.onDisposeFn = fn;
            }

            export function kofUiComponentEffect(c, fn) {
                // effects run on mount (or immediately when the component is
                // already mounted) and their cleanup runs on unmount, in
                // reverse registration order — no manual leak management.
                const n = kofUiComponents.get(c);
                if (!n) return;
                const f = kofUiRunFn(fn);
                if (!f) return;
                n.effectFns.push(f);
                if (n.mounted) kofUiRunEffect(n, f);
            }

            function kofUiRunEffect(n, f) {
                let result;
                const prevCtx = kofUiCurrentComponent;
                kofUiCurrentComponent = n;
                try {
                    result = f();
                } catch (e) {
                    kofUiReportError("effect threw", e);
                    result = null;
                } finally {
                    kofUiCurrentComponent = prevCtx;
                }
                n.effects.push(result);
            }

            function kofUiDropAutoStores(n) {
                // D-COMPLETE-FIRST item 4: a store created inside the
                // component's lifecycle dies with it — deleting the entry
                // frees the value AND every subscription it still carried
                // (subscriptionsLive() drops with it). Deterministic release
                // at unmount, not a GC hope. App-scope stores and AppState
                // never land in _autoStores (ownerless, manual by design).
                const ids = n._autoStores;
                if (!ids || ids.length === 0) return;
                for (const id of ids) kofUiStores.delete(id);
                n._autoStores = [];
            }

            function kofUiDropAutoSubs(n) {
                // (A): subscriptions made in this component's lifecycle die with
                // it. Manual (outside-component) subscriptions are untouched —
                // they never landed in _autoSubs. Removal is idempotent: an
                // entry already unsubscribed by hand is simply not found.
                const subs = n._autoSubs;
                if (!subs || subs.length === 0) return;
                for (const rec of subs) {
                    const st = kofUiStores.get(rec.store);
                    if (!st) continue;
                    for (let i = 0; i < st.subs.length; i++) {
                        if (st.subs[i].raw === rec.raw) { st.subs.splice(i, 1); break; }
                    }
                }
                n._autoSubs = [];
            }

            export function kofUiComponentMount(c) {
                const n = kofUiComponents.get(c);
                if (!n || n.mounted) return;
                n.mounted = true;
                // mount: (mount view) -> onMount() -> effects — deterministic
                if (n.view) kofUiRender(n);
                const om = kofUiRunFn(n.onMountFn);
                if (om) {
                    const prevCtx = kofUiCurrentComponent;
                    kofUiCurrentComponent = n;
                    try { om(); } catch (e) { kofUiReportError("onMount threw", e); }
                    finally { kofUiCurrentComponent = prevCtx; }
                }
                for (const f of n.effectFns) kofUiRunEffect(n, f);
            }

            export function kofUiComponentUnmount(c) {
                const n = kofUiComponents.get(c);
                if (!n || !n.mounted) return;
                n.mounted = false;
                // unmount cascades top-down: children first (they lose their
                // host), then this node's hooks. Detach once at the root.
                for (const child of n.children.slice()) {
                    const cc = kofUiComponents.get(child);
                    if (cc && cc.mounted) kofUiComponentUnmount(child);
                }
                // unmount: onDispose() -> effects() in REVERSE
                const od = kofUiRunFn(n.onDisposeFn);
                if (od) {
                    try { od(); } catch (e) { kofUiReportError("onDispose threw", e); }
                }
                for (let i = n.effects.length - 1; i >= 0; i--) {
                    try {
                        const ef = n.effects[i];
                        if (typeof ef === "function") ef();
                    } catch (e) { kofUiReportError("effect cleanup threw", e); }
                }
                n.effects.length = 0;
                n.effectFns.length = 0;
                n.disposed = true;
                kofUiDropAutoSubs(n);
                kofUiDropAutoStores(n);
            }

            export function kofUiComponentBind(c, child) {
                // compose: attach a child widget or component under this one.
                const n = kofUiComponents.get(c);
                if (!n || !n.el) return;
                // a child component mounts on bind (lifecycle is automatic)
                const childComp = kofUiComponents.get(child);
                if (childComp) {
                    childComp.parent = n;
                    if (!n.children.includes(child)) n.children.push(child);
                    if (childComp.el) n.el.appendChild(childComp.el);
                    kofUiComponentMount(child);
                    return;
                }
                const childEl = window.__kofNodes && window.__kofNodes[child];
                if (childEl) n.el.appendChild(childEl);
            }

            export function kofUiComponentRemove(c) {
                const n = kofUiComponents.get(c);
                if (!n) return;
                // detach from the parent's child list (tree is the source of truth)
                if (n.parent) {
                    const i = n.parent.children.indexOf(c);
                    if (i >= 0) n.parent.children.splice(i, 1);
                    n.parent = null;
                }
                if (n.mounted) {
                    // unmount the subtree, freeing every component in it
                    kofUiRemoveSubtreeComponents(c);
                } else {
                    n.disposed = true;
                    kofUiDropAutoSubs(n);
                    kofUiDropAutoStores(n);
                    kofUiDetachDom(c);
                    kofUiComponents.delete(c);
                }
            }

            function kofUiRemoveSubtreeComponents(c) {
                const n = kofUiComponents.get(c);
                if (!n) return;
                for (const child of n.children.slice()) {
                    kofUiRemoveSubtreeComponents(child);
                }
                if (n.mounted) {
                    // unmount runs hooks + cleanup; skip the recursive
                    // children walk (already freed above)
                    n.children.length = 0;
                    kofUiComponentUnmount(c);
                }
                kofUiComponents.delete(c);
            }

            export function kofUiComponentOn(c, type, handler) {
                // centralised event dispatch on the component root element.
                const n = kofUiComponents.get(c);
                if (!n || !n.el || !type || !handler) return;
                const domType = KOF_UI_EV[type] || type;
                n.el._kofHandlers = n.el._kofHandlers || {};
                const arr = n.el._kofHandlers[domType];
                if (arr) arr.push(handler);
                else n.el._kofHandlers[domType] = [handler];
                if (typeof n.el.addEventListener === "function") {
                    n.el.addEventListener(domType, function (ev) {
                        kofUiDispatchEvent(c, domType, ev);
                    });
                }
            }


            // centralised event dispatch: one registry, deterministic cleanup
            export function kofUiWidgetOn(id, type, handler) {
                if (!type || !handler) return;
                const node = window.__kofNodes && window.__kofNodes[id];
                if (!node) return;
                kofUiWidgetOnHandle(id, KOF_UI_EV[type] || type, handler);
            }

            function kofUiWidgetOnHandle(id, domType, handler) {
                // domType already mapped through KOF_UI_EV; the wrapper carries
                // a marker so (B) reuse re-registers instead of moving it.
                const node = window.__kofNodes && window.__kofNodes[id];
                if (!node) return;
                node._kofHandlers = node._kofHandlers || {};
                const arr = node._kofHandlers[domType];
                if (arr) arr.push(handler);
                else node._kofHandlers[domType] = [handler];
                const wrapper = function (ev) {
                    kofUiDispatchWidgetEvent(id, domType, ev);
                };
                wrapper.__kofWidgetWrapper = true;
                kofUiOnDom(node, domType, wrapper);
            }

            export function kofUiNodesLive() {
                return kofUiComponents.size;
            }

            export function kofUiFlushUi() {
                kofUiFlushQueue();
            }

            export function kofUiEventType(ev) {
                // kof.ui.Event identity: o IR baixa e.type() para
                // kof_ui_event_type(ev); o evento é o objeto de kofUiMakeEvent.
                // Aceita também a string de tipo (uso histórico).
                if (ev && typeof ev.type === "function") return ev.type();
                return ev || "";
            }

            """;

}
