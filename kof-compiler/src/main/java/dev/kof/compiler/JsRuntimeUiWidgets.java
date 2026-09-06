package dev.kof.compiler;

/** kof-runtime.mjs — widgets de UI (font/label/button/input). */
final class JsRuntimeUiWidgets {
    private JsRuntimeUiWidgets() {
    }

    static final String UI_WIDGET_RUNTIME = """
            // ── Font ───────────────────────────────────────────
            let __kofFontSeq = 0;
            export function kofUiFontNew(family, size) {
                window.__kofFonts = window.__kofFonts || {};
                const id = ++__kofFontSeq;
                window.__kofFonts[id] = { family, size, bold: false };
                return id;
            }
            export function kofUiFontNewBold(family, size, bold) {
                window.__kofFonts = window.__kofFonts || {};
                const id = ++__kofFontSeq;
                window.__kofFonts[id] = { family, size, bold: !!bold };
                return id;
            }
            export function kofUiWidgetSetFont(widget, fontId) {
                const n = window.__kofNodes && window.__kofNodes[widget];
                const f = window.__kofFonts && window.__kofFonts[fontId];
                if (n && f) {
                    n.style.fontFamily = '"' + f.family + '", system-ui, sans-serif';
                    n.style.fontSize = f.size + "px";
                    n.style.fontWeight = f.bold ? "700" : "400";
                    n.dataset.kofFont = String(fontId);
                }
            }
            export function kofUiWidgetFont(widget) {
                const n = window.__kofNodes && window.__kofNodes[widget];
                return n && n.dataset.kofFont ? parseInt(n.dataset.kofFont, 10) : -1;
            }

            export function kofUiLabelNew(text) {
                if (typeof document === "undefined") {
                    return -1;
                }
                const span = document.createElement("span");
                span.textContent = text;
                span.className = "kof-label";
                if (typeof window.__kofNodes === "undefined") {
                    window.__kofNodes = {};
                }
                const id = ++kofNodeSeq;
                window.__kofNodes[id] = span;
                return id;
            }

            export function kofUiLabelSetText(label, text) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[label]) {
                    window.__kofNodes[label].textContent = text;
                }
            }

            export function kofUiLabelText(label) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[label]) {
                    return window.__kofNodes[label].textContent;
                }
                return "";
            }

            export function kofUiLabelSetFontSize(label, size) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[label]) {
                    window.__kofNodes[label].style.fontSize = size + "px";
                }
            }

            export function kofUiLabelFontSize(label) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[label]) {
                    const fs = window.__kofNodes[label].style.fontSize;
                    if (typeof fs === "string" && fs.endsWith("px")) {
                        const v = parseInt(fs, 10);
                        if (!isNaN(v)) return v;
                    }
                }
                return 0;
            }

            export function kofUiLabelSetBold(label, bold) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[label]) {
                    window.__kofNodes[label].style.fontWeight = bold ? "bold" : "normal";
                }
            }

            export function kofUiLabelBold(label) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[label]) {
                    return window.__kofNodes[label].style.fontWeight === "bold" ? 1 : 0;
                }
                return 0;
            }

            export function kofUiLabelSetColor(label, color) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[label]) {
                    window.__kofNodes[label].style.color = kofUiColorToCss(color);
                }
            }

            export function kofUiLabelColor(label) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[label]) {
                    const css = window.__kofNodes[label].style.color;
                    const m = typeof css === "string" ? css.match(/rgba?\\((\\d+),\\s*(\\d+),\\s*(\\d+)/) : null;
                    if (m) {
                        return ((parseInt(m[1], 10) << 24) | (parseInt(m[2], 10) << 16)
                                | (parseInt(m[3], 10) << 8) | 0xFF) >>> 0;
                    }
                }
                return 0;
            }

            export function kofUiLabelRemove(label) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[label]) {
                    const node = window.__kofNodes[label];
                    if (node.parentNode) {
                        node.parentNode.removeChild(node);
                    }
                    delete window.__kofNodes[label];
                }
            }

            function kofUiCreateNode(tag, className) {
                if (typeof document === "undefined") {
                    return -1;
                }
                const el = document.createElement(tag);
                el.className = className;
                if (typeof window.__kofNodes === "undefined") {
                    window.__kofNodes = {};
                }
                const id = ++kofNodeSeq;
                window.__kofNodes[id] = el;
                return id;
            }

            function kofUiSetAction(id, action) {
                if (!action || typeof document === "undefined") {
                    return;
                }
                window.__kofActions = window.__kofActions || {};
                window.__kofActions[id] = action;
                const node = window.__kofNodes[id];
                if (node && typeof node.addEventListener === "function") {
                    node.addEventListener("click", function () {
                        action.invoke();
                    });
                }
            }

            export function kofUiButtonNew(text) {
                const id = kofUiCreateNode("button", "kof-button");
                if (id < 0) {
                    return -1;
                }
                window.__kofNodes[id].textContent = text;
                return id;
            }

            export function kofUiButtonNewAction(text, action) {
                const id = kofUiCreateNode("button", "kof-button");
                if (id < 0) {
                    return -1;
                }
                window.__kofNodes[id].textContent = text;
                kofUiSetAction(id, action);
                return id;
            }

            export function kofUiButtonSetText(button, text) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[button]) {
                    window.__kofNodes[button].textContent = text;
                }
            }

            export function kofUiButtonText(button) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[button]) {
                    return window.__kofNodes[button].textContent;
                }
                return "";
            }

            export function kofUiButtonRemove(button) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[button]) {
                    const node = window.__kofNodes[button];
                    if (node.parentNode) {
                        node.parentNode.removeChild(node);
                    }
                    delete window.__kofNodes[button];
                    if (window.__kofActions) {
                        delete window.__kofActions[button];
                    }
                }
            }

            export function kofUiInputNew(text) {
                const id = kofUiCreateNode("input", "kof-input");
                if (id < 0) {
                    return -1;
                }
                const node = window.__kofNodes[id];
                node.type = "text";
                node.value = text;
                return id;
            }

            export function kofUiInputSetText(input, text) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[input]) {
                    window.__kofNodes[input].value = text;
                }
            }

            export function kofUiInputText(input) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[input]) {
                    return window.__kofNodes[input].value;
                }
                return "";
            }

            export function kofUiInputRemove(input) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[input]) {
                    const node = window.__kofNodes[input];
                    if (node.parentNode) {
                        node.parentNode.removeChild(node);
                    }
                    delete window.__kofNodes[input];
                }
            }

            export function kofUiColumnNew(ids) {
                const id = kofUiCreateNode("div", "kof-column");
                if (id < 0) {
                    return -1;
                }
                const node = window.__kofNodes[id];
                if (ids) {
                    for (const childId of ids) {
                        const child = window.__kofNodes[childId];
                        if (child) {
                            node.appendChild(child);
                        }
                    }
                }
                return id;
            }

            export function kofUiRowNew(ids) {
                const id = kofUiCreateNode("div", "kof-row");
                if (id < 0) {
                    return -1;
                }
                const node = window.__kofNodes[id];
                if (ids) {
                    for (const childId of ids) {
                        const child = window.__kofNodes[childId];
                        if (child) {
                            node.appendChild(child);
                        }
                    }
                }
                return id;
            }

            export function kofUiStyleNew(background, foreground, padding, radius) {
                if (typeof document === "undefined") {
                    return -1;
                }
                window.__kofStyles = window.__kofStyles || {};
                const id = Object.keys(window.__kofStyles).length + 1;
                window.__kofStyles[id] = { background: background, foreground: foreground,
                        padding: padding, radius: radius };
                return id;
            }

            export function kofUiViewNew(style) {
                const id = kofUiCreateNode("div", "kof-view");
                if (id < 0) {
                    return -1;
                }
                const s = window.__kofStyles && window.__kofStyles[style];
                const node = window.__kofNodes[id];
                if (s) {
                    const css = node.style;
                    if (s.background !== 0) {
                        css.backgroundColor = kofUiColorToCss(s.background);
                    }
                    if (s.foreground !== 0) {
                        css.color = kofUiColorToCss(s.foreground);
                    }
                    if (s.padding > 0) {
                        css.padding = s.padding + "px";
                    }
                    if (s.radius > 0) {
                        css.borderRadius = s.radius + "px";
                    }
                }
                return id;
            }

            export function kofUiViewBind(view, child) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[view]
                        && window.__kofNodes[child]) {
                    window.__kofNodes[view].appendChild(window.__kofNodes[child]);
                }
            }

            // ── Canvas 2D ──────────────────────────────────────
            export function kofUiCanvasNew(w, h) {
                if (typeof document === "undefined") return -1;
                const canvas = document.createElement("canvas");
                canvas.width = w;
                canvas.height = h;
                canvas.className = "kof-canvas";
                canvas.style.display = "block";
                const ctx = canvas.getContext("2d");
                if (typeof window.__kofNodes === "undefined") window.__kofNodes = {};
                const id = ++kofNodeSeq;
                window.__kofNodes[id] = canvas;
                window.__kofCanvasCtx = window.__kofCanvasCtx || {};
                window.__kofCanvasCtx[id] = ctx;
                // renderiza standalone: anexa ao root (w.bind(c) re-parenteia
                // para a janela — appendChild remove do pai anterior)
                const root = document.getElementById("kof-root");
                if (root) root.appendChild(canvas);
                return id;
            }
            export function kofUiCanvasBeginPath(id) {
                const ctx = window.__kofCanvasCtx && window.__kofCanvasCtx[id];
                if (ctx) ctx.beginPath();
            }
            export function kofUiCanvasClosePath(id) {
                const ctx = window.__kofCanvasCtx && window.__kofCanvasCtx[id];
                if (ctx) ctx.closePath();
            }
            export function kofUiCanvasMoveTo(id, x, y) {
                const ctx = window.__kofCanvasCtx && window.__kofCanvasCtx[id];
                if (ctx) ctx.moveTo(x, y);
            }
            export function kofUiCanvasLineTo(id, x, y) {
                const ctx = window.__kofCanvasCtx && window.__kofCanvasCtx[id];
                if (ctx) ctx.lineTo(x, y);
            }
            export function kofUiCanvasArc(id, x, y, r, startAngle, endAngle) {
                const ctx = window.__kofCanvasCtx && window.__kofCanvasCtx[id];
                if (ctx) ctx.arc(x, y, r, startAngle, endAngle);
            }
            export function kofUiCanvasFill(id) {
                const ctx = window.__kofCanvasCtx && window.__kofCanvasCtx[id];
                if (ctx) ctx.fill();
                kofUiSerializeHtml();
            }
            export function kofUiCanvasStroke(id) {
                const ctx = window.__kofCanvasCtx && window.__kofCanvasCtx[id];
                if (ctx) ctx.stroke();
                kofUiSerializeHtml();
            }
            export function kofUiCanvasSetFill(id, color) {
                const ctx = window.__kofCanvasCtx && window.__kofCanvasCtx[id];
                if (ctx) ctx.fillStyle = kofUiColorToCss(color);
            }
            export function kofUiCanvasSetStroke(id, color) {
                const ctx = window.__kofCanvasCtx && window.__kofCanvasCtx[id];
                if (ctx) ctx.strokeStyle = kofUiColorToCss(color);
            }
            export function kofUiCanvasSetLineWidth(id, w) {
                const ctx = window.__kofCanvasCtx && window.__kofCanvasCtx[id];
                if (ctx) ctx.lineWidth = w;
            }
            export function kofUiCanvasClearRect(id, x, y, w, h) {
                const ctx = window.__kofCanvasCtx && window.__kofCanvasCtx[id];
                if (ctx) ctx.clearRect(x, y, w, h);
            }
            export function kofUiCanvasRemove(id) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[id]) {
                    const node = window.__kofNodes[id];
                    if (node.parentNode) node.parentNode.removeChild(node);
                    delete window.__kofNodes[id];
                    if (window.__kofCanvasCtx) delete window.__kofCanvasCtx[id];
                }
            }

            """;

}
