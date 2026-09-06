package dev.kof.compiler;

/** kof-runtime.mjs — Component Core + Link/Image/Icon. */
final class JsRuntimeUiComponents {
    private JsRuntimeUiComponents() {
    }

    static final String UI_COMPONENT_RUNTIME = """
            // ── Component Core (docs/ui/architecture.md) ─────────────
            // A UI is a tree of components. A Component node carries: identity,
            // state (Int), a view builder, lifecycle hooks, effects (auto-cleaned)
            // and events. Rendering is KofJS; the framework (not the widget)
            // owns the tree, the render schedule and the lifecycle.
            const kofUiComponents = new Map();
            let kofUiSeq = 0;
            let kofNodeSeq = 0;
            let kofUiFlushing = false;
            const kofUiDirty = [];
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
            function kofUiRemoveSubtree(rootId) {
                // remove the DOM subtree of a widget id and prune the registry
                if (!kofUiIsNode(rootId)) return;
                for (const n of kofUiSubtreeIds(rootId)) {
                    if (n.parentNode) n.parentNode.removeChild(n);
                    n._kofGone = true;
                    for (const key in window.__kofNodes) {
                        if (window.__kofNodes[key] === n) {
                            delete window.__kofNodes[key];
                            break;
                        }
                    }
                }
            }

            function kofUiRunFn(fn) {
                // a Kof lambda compiles to a class with an invoke() method;
                // plain functions pass through.
                return fn && typeof fn.invoke === "function" ? fn.invoke.bind(fn) : fn;
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

            function kofUiRender(c) {
                // rebuild the component's child subtree: run the view builder
                // with the current state, then swap the fresh DOM in place.
                // (handle diffing is a Phase-9 optimization)
                let rootId = 0;
                try {
                    const v = kofUiRunFn(c.view);
                    rootId = v ? v(c.state) : 0;
                } catch (e) {
                    rootId = 0;
                }
                if (c.el) {
                    const oldEl = window.__kofNodes && window.__kofNodes[c.root];
                    if (c.root !== rootId && oldEl
                            && oldEl.parentNode === c.el && oldEl.parentNode.removeChild) {
                        c.el.removeChild(oldEl);
                    }
                    const rootEl = window.__kofNodes && window.__kofNodes[rootId];
                    if (rootEl && rootEl.parentNode !== c.el) {
                        c.el.appendChild(rootEl);
                    }
                }
                c.root = rootId;
            }

            export function kofUiComponentNew(state) {
                const id = ++kofUiSeq;
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
                try {
                    result = f();
                } catch (e) {
                    result = null;
                }
                n.effects.push(result);
            }

            export function kofUiComponentMount(c) {
                const n = kofUiComponents.get(c);
                if (!n || n.mounted) return;
                n.mounted = true;
                // mount: (mount view) -> onMount() -> effects — deterministic
                if (n.view) kofUiRender(n);
                const om = kofUiRunFn(n.onMountFn);
                if (om) {
                    try { om(); } catch (e) {}
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
                    try { od(); } catch (e) {}
                }
                for (let i = n.effects.length - 1; i >= 0; i--) {
                    try {
                        const ef = n.effects[i];
                        if (typeof ef === "function") ef();
                    } catch (e) {}
                }
                n.effects.length = 0;
                n.effectFns.length = 0;
                n.disposed = true;
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
                node._kofHandlers = node._kofHandlers || {};
                const domType = KOF_UI_EV[type] || type;
                const arr = node._kofHandlers[domType];
                if (arr) arr.push(handler);
                else node._kofHandlers[domType] = [handler];
                if (typeof node.addEventListener === "function") {
                    node.addEventListener(domType, function (ev) {
                        const h = node._kofHandlers && node._kofHandlers[domType];
                        if (!h) return;
                        for (const fn of h) {
                            try {
                                if (typeof fn.invoke === "function") fn.invoke();
                                else fn();
                            } catch (e) {}
                        }
                    });
                }
            }

            export function kofUiNodesLive() {
                return kofUiComponents.size;
            }

            export function kofUiFlushUi() {
                kofUiFlushQueue();
            }

            export function kofUiEventType(type) {
                // kof.ui.Event identity: the event kind as registered.
                return type || "";
            }

            // ── Link ────────────────────────────────────────────
            export function kofUiLinkNew(text, url) {
                if (typeof document === "undefined") return -1;
                const a = document.createElement("a");
                a.textContent = text;
                a.href = url;
                a.target = "_blank";
                a.rel = "noopener";
                a.className = "kof-link";
                if (typeof window.__kofNodes === "undefined") window.__kofNodes = {};
                const id = ++kofNodeSeq;
                window.__kofNodes[id] = a;
                return id;
            }
            export function kofUiLinkSetText(link, text) {
                const n = window.__kofNodes && window.__kofNodes[link];
                if (n) n.textContent = text;
            }
            export function kofUiLinkText(link) {
                const n = window.__kofNodes && window.__kofNodes[link];
                return n ? n.textContent : "";
            }
            export function kofUiLinkSetUrl(link, url) {
                const n = window.__kofNodes && window.__kofNodes[link];
                if (n) {
                    n.href = url;
                    if (!n.target) { n.target = "_blank"; n.rel = "noopener"; }
                }
            }
            export function kofUiLinkUrl(link) {
                const n = window.__kofNodes && window.__kofNodes[link];
                return n ? n.href : "";
            }
            export function kofUiLinkRemove(link) {
                const n = window.__kofNodes && window.__kofNodes[link];
                if (n && n.parentNode) n.parentNode.removeChild(n);
            }

            // ── Image (preview) ────────────────────────────────
            export function kofUiImageNew(src) {
                if (typeof document === "undefined") return -1;
                const img = document.createElement("img");
                img.src = src;
                img.className = "kof-image";
                img.alt = "";
                if (typeof window.__kofNodes === "undefined") window.__kofNodes = {};
                const id = ++kofNodeSeq;
                window.__kofNodes[id] = img;
                return id;
            }
            export function kofUiImageSetSrc(image, src) {
                const n = window.__kofNodes && window.__kofNodes[image];
                if (n) n.src = src;
            }
            export function kofUiImageSrc(image) {
                const n = window.__kofNodes && window.__kofNodes[image];
                return n ? n.src : "";
            }
            export function kofUiImageRemove(image) {
                const n = window.__kofNodes && window.__kofNodes[image];
                if (n && n.parentNode) n.parentNode.removeChild(n);
            }

            // ── Icon (SVG paths embutidos) ─────────────────────
            const KOF_ICONS = {
              home: "M12 3l9 8h-3v9h-5v-6h-2v6H6v-9H3z",
              star: "M12 2l2.9 6.3 6.9.7-5.1 4.6 1.4 6.8L12 17l-6.1 3.4 1.4-6.8L2.2 9l6.9-.7z",
              heart: "M12 21s-8-5.3-8-11a4.6 4.6 0 018-3 4.6 4.6 0 018 3c0 5.7-8 11-8 11z",
              search: "M10 2a8 8 0 105.3 14l5.4 5.4 1.4-1.4-5.4-5.4A8 8 0 0010 2zm0 2a6 6 0 110 12 6 6 0 010-12z",
              settings: "M12 8a4 4 0 100 8 4 4 0 000-8zm9 4l-2.1-.6a7 7 0 00-.6-1.5l1.1-1.9-1.5-1.5-1.9 1.1a7 7 0 00-1.5-.6L14 3h-4l-.6 2.1a7 7 0 00-1.5.6L6 4.6 4.5 6.1l1.1 1.9a7 7 0 00-.6 1.5L3 10v4l2.1.6c.1.5.3 1 .6 1.5l-1.1 1.9 1.5 1.5 1.9-1.1c.5.3 1 .5 1.5.6L10 23h4l.6-2.1c.5-.1 1-.3 1.5-.6l1.9 1.1 1.5-1.5-1.1-1.9c.3-.5.5-1 .6-1.5L21 14z",
              user: "M12 12a5 5 0 100-10 5 5 0 000 10zm0 2c-4.4 0-8 2.2-8 5v3h16v-3c0-2.8-3.6-5-8-5z",
              menu: "M3 5h18v2H3zM3 11h18v2H3zM3 17h18v2H3z",
              close: "M6 5l13 13-1.4 1.4L4.6 6.4zM19 5L6 18l1.4 1.4L20.4 6.4z",
              check: "M9 16.2l-4.2-4.2L3.4 13.4 9 19 21 7l-1.4-1.4z",
              plus: "M11 5h2v6h6v2h-6v6h-2v-6H5v-2h6z",
              minus: "M5 11h14v2H5z",
              trash: "M6 7h12l-1 14H7zM9 4h6l1 2h4v2H4V6h4z",
              edit: "M4 20h4L20 8l-4-4L4 16zm2.8-3.4L16.6 6.8 17.2 7.4 7.4 17.2z",
              share: "M18 8a3 3 0 10-2.8-4H15L6 9.3a3 3 0 100 5.4l9 5.3h.2A3 3 0 1018 16a3 3 0 00-2 .8L7.3 11.6a3 3 0 000-1.2L15.9 5.3A3 3 0 0118 8z",
              download: "M12 16l-5-5h3V4h4v7h3zM5 18h14v2H5z",
              upload: "M12 3l5 5h-3v8h-4V8H7zM5 18h14v2H5z",
              mail: "M2 5h20v14H2zm2 2v.4l8 5 8-5V7l-8 5z",
              phone: "M6 2h4l2 5-2.5 1.5a12 12 0 006 6L17 12l5 2v4a2 2 0 01-2 2A17 17 0 014 4a2 2 0 012-2z",
              calendar: "M7 2h2v2h6V2h2v2h4v18H3V4h4zm12 8H5v10h14zM7 6H5v2h14V6h-2z",
              clock: "M12 2a10 10 0 100 20 10 10 0 000-20zm1 5h-2v6l5 3 1-1.7-4-2.3z",
              eye: "M12 5C6 5 2 12 2 12s4 7 10 7 10-7 10-7-4-7-10-7zm0 11a4 4 0 110-8 4 4 0 010 8z",
              lock: "M6 10V7a6 6 0 1112 0v3h2v12H4V10zm2 0h8V7a4 4 0 10-8 0z"
            };
            export function kofUiIconNew(name) { return kofUiIconNewSize(name, 24); }
            export function kofUiIconNewSize(name, size) {
                if (typeof document === "undefined") return -1;
                const d = KOF_ICONS[name] || KOF_ICONS["close"];
                const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
                svg.setAttribute("viewBox", "0 0 24 24");
                svg.setAttribute("width", size);
                svg.setAttribute("height", size);
                const p = document.createElementNS("http://www.w3.org/2000/svg", "path");
                p.setAttribute("d", d);
                p.setAttribute("fill", "currentColor");
                svg.appendChild(p);
                svg.dataset.kofIcon = name;
                if (typeof window.__kofNodes === "undefined") window.__kofNodes = {};
                const id = ++kofNodeSeq;
                window.__kofNodes[id] = svg;
                return id;
            }
            export function kofUiIconSetName(icon, name) {
                const n = window.__kofNodes && window.__kofNodes[icon];
                if (!n) return;
                const d = KOF_ICONS[name];
                if (d) n.querySelector("path").setAttribute("d", d);
                n.dataset.kofIcon = name;
            }
            export function kofUiIconName(icon) {
                const n = window.__kofNodes && window.__kofNodes[icon];
                return n ? (n.dataset.kofIcon || "") : "";
            }
            export function kofUiIconSetSize(icon, size) {
                const n = window.__kofNodes && window.__kofNodes[icon];
                if (n) { n.setAttribute("width", size); n.setAttribute("height", size); }
            }
            export function kofUiIconSize(icon) {
                const n = window.__kofNodes && window.__kofNodes[icon];
                return n ? parseInt(n.getAttribute("width"), 10) || 24 : 24;
            }
            export function kofUiIconRemove(icon) {
                const n = window.__kofNodes && window.__kofNodes[icon];
                if (n && n.parentNode) n.parentNode.removeChild(n);
            }

            """;

}
