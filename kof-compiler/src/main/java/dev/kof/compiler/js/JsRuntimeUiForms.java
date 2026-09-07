package dev.kof.compiler.js;

/** kof-runtime.mjs — widgets de forms (input/textarea/select). */
public final class JsRuntimeUiForms {
    private JsRuntimeUiForms() {
    }

    static final String UI_FORMS_RUNTIME = """
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

            export function kofUiInputSetPlaceholder(input, placeholder) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[input]) {
                    window.__kofNodes[input].placeholder = placeholder;
                }
            }

            export function kofUiInputSetType(input, type) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[input]) {
                    window.__kofNodes[input].type = type;
                }
            }

            export function kofUiInputSetChecked(input, checked) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[input]) {
                    const node = window.__kofNodes[input];
                    node.checked = checked ? true : false;
                    // reflete no atributo (defaultChecked) para o DOM serializado
                    if (checked) node.setAttribute("checked", "");
                    else node.removeAttribute("checked");
                }
            }

            export function kofUiInputChecked(input) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[input]) {
                    return window.__kofNodes[input].checked ? 1 : 0;
                }
                return 0;
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

            export function kofUiTextareaNew(text) {
                const id = kofUiCreateNode("textarea", "kof-textarea");
                if (id < 0) {
                    return -1;
                }
                // textarea serializa o conteúdo como texto entre as tags
                // (o "default value") — .value programático não aparece no
                // outerHTML; define-se via textContent.
                const node = window.__kofNodes[id];
                node.value = text;
                node.textContent = text;
                return id;
            }

            export function kofUiTextareaSetText(ta, text) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[ta]) {
                    window.__kofNodes[ta].value = text;
                }
            }

            export function kofUiTextareaText(ta) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[ta]) {
                    return window.__kofNodes[ta].value;
                }
                return "";
            }

            export function kofUiTextareaSetPlaceholder(ta, placeholder) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[ta]) {
                    window.__kofNodes[ta].placeholder = placeholder;
                }
            }

            export function kofUiTextareaRemove(ta) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[ta]) {
                    const node = window.__kofNodes[ta];
                    if (node.parentNode) {
                        node.parentNode.removeChild(node);
                    }
                    delete window.__kofNodes[ta];
                }
            }

            export function kofUiSelectNew(options) {
                const id = kofUiCreateNode("select", "kof-select");
                if (id < 0) {
                    return -1;
                }
                const node = window.__kofNodes[id];
                if (options) {
                    for (const label of options) {
                        const opt = document.createElement("option");
                        opt.value = String(label);
                        opt.textContent = String(label);
                        node.appendChild(opt);
                    }
                }
                return id;
            }

            export function kofUiSelectSetOptions(sel, options) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[sel]) {
                    const node = window.__kofNodes[sel];
                    node.innerHTML = "";
                    if (options) {
                        for (const label of options) {
                            const opt = document.createElement("option");
                            opt.value = String(label);
                            opt.textContent = String(label);
                            node.appendChild(opt);
                        }
                    }
                }
            }

            export function kofUiSelectSetSelected(sel, index) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[sel]) {
                    const node = window.__kofNodes[sel];
                    node.selectedIndex = index;
                    // reflete no atributo `selected` das <option> — outerHTML
                    // (dump-dom) serializa atributos de conteúdo, não a
                    // propriedade IDL selectedIndex.
                    for (let i = 0; i < node.options.length; i++) {
                        if (i === index) {
                            node.options[i].setAttribute("selected", "");
                        } else {
                            node.options[i].removeAttribute("selected");
                        }
                    }
                }
            }

            export function kofUiSelectSelected(sel) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[sel]) {
                    return window.__kofNodes[sel].selectedIndex;
                }
                return 0;
            }

            export function kofUiSelectRemove(sel) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[sel]) {
                    const node = window.__kofNodes[sel];
                    if (node.parentNode) {
                        node.parentNode.removeChild(node);
                    }
                    delete window.__kofNodes[sel];
                }
            }
            """;
}
