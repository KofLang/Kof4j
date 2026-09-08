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

            export function kofUiInputSetName(input, name) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[input]) {
                    window.__kofNodes[input].setAttribute("name", name);
                }
            }

            export function kofUiInputSetReadonly(input, readonly) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[input]) {
                    if (readonly) {
                        window.__kofNodes[input].setAttribute("readonly", "");
                    } else {
                        window.__kofNodes[input].removeAttribute("readonly");
                    }
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

            export function kofUiTextareaSetName(ta, name) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[ta]) {
                    window.__kofNodes[ta].setAttribute("name", name);
                }
            }

            export function kofUiTextareaSetReadonly(ta, readonly) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[ta]) {
                    if (readonly) {
                        window.__kofNodes[ta].setAttribute("readonly", "");
                    } else {
                        window.__kofNodes[ta].removeAttribute("readonly");
                    }
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

            function kofUiFillListItems(node, items) {
                node.innerHTML = "";
                if (items) {
                    for (const label of items) {
                        const li = document.createElement("li");
                        li.textContent = String(label);
                        node.appendChild(li);
                    }
                }
            }

            export function kofUiUlNew(items) {
                const id = kofUiCreateNode("ul", "kof-ul");
                if (id < 0) {
                    return -1;
                }
                kofUiFillListItems(window.__kofNodes[id], items);
                return id;
            }

            export function kofUiUlSetItems(ul, items) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[ul]) {
                    kofUiFillListItems(window.__kofNodes[ul], items);
                }
            }

            export function kofUiUlRemove(ul) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[ul]) {
                    const node = window.__kofNodes[ul];
                    if (node.parentNode) {
                        node.parentNode.removeChild(node);
                    }
                    delete window.__kofNodes[ul];
                }
            }

            export function kofUiOlNew(items) {
                const id = kofUiCreateNode("ol", "kof-ol");
                if (id < 0) {
                    return -1;
                }
                kofUiFillListItems(window.__kofNodes[id], items);
                return id;
            }

            export function kofUiOlSetItems(ol, items) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[ol]) {
                    kofUiFillListItems(window.__kofNodes[ol], items);
                }
            }

            export function kofUiOlRemove(ol) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[ol]) {
                    const node = window.__kofNodes[ol];
                    if (node.parentNode) {
                        node.parentNode.removeChild(node);
                    }
                    delete window.__kofNodes[ol];
                }
            }

            function kofUiFillTable(node, header, rows) {
                node.innerHTML = "";
                if (header) {
                    const thead = document.createElement("thead");
                    const tr = document.createElement("tr");
                    for (const h of header) {
                        const th = document.createElement("th");
                        th.textContent = String(h);
                        tr.appendChild(th);
                    }
                    thead.appendChild(tr);
                    node.appendChild(thead);
                }
                const tbody = document.createElement("tbody");
                if (rows) {
                    for (const row of rows) {
                        const tr = document.createElement("tr");
                        for (const cell of row) {
                            const td = document.createElement("td");
                            td.textContent = String(cell);
                            tr.appendChild(td);
                        }
                        tbody.appendChild(tr);
                    }
                }
                node.appendChild(tbody);
            }

            export function kofUiTableNew(header, rows) {
                const id = kofUiCreateNode("table", "kof-table");
                if (id < 0) {
                    return -1;
                }
                kofUiFillTable(window.__kofNodes[id], header, rows);
                return id;
            }

            export function kofUiTableSetRows(table, rows) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[table]) {
                    const node = window.__kofNodes[table];
                    const header = Array.from(node.querySelectorAll("th")).map(function (th) { return th.textContent; });
                    kofUiFillTable(node, header.length ? header : null, rows);
                }
            }

            export function kofUiTableRemove(table) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[table]) {
                    const node = window.__kofNodes[table];
                    if (node.parentNode) {
                        node.parentNode.removeChild(node);
                    }
                    delete window.__kofNodes[table];
                }
            }

            export function kofUiFieldsetNew(children) {
                const id = kofUiCreateNode("fieldset", "kof-fieldset");
                if (id < 0) {
                    return -1;
                }
                const node = window.__kofNodes[id];
                if (children) {
                    for (const childId of children) {
                        const child = window.__kofNodes[childId];
                        if (child) {
                            node.appendChild(child);
                        }
                    }
                }
                return id;
            }

            export function kofUiIframeNew(src) {
                const id = kofUiCreateNode("iframe", "kof-iframe");
                if (id < 0) {
                    return -1;
                }
                window.__kofNodes[id].src = src;
                return id;
            }

            export function kofUiIframeSetSrc(iframe, src) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[iframe]) {
                    window.__kofNodes[iframe].src = src;
                }
            }

            export function kofUiIframeRemove(iframe) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[iframe]) {
                    const node = window.__kofNodes[iframe];
                    if (node.parentNode) {
                        node.parentNode.removeChild(node);
                    }
                    delete window.__kofNodes[iframe];
                }
            }

            export function kofUiVideoNew(src) {
                const id = kofUiCreateNode("video", "kof-video");
                if (id < 0) {
                    return -1;
                }
                window.__kofNodes[id].src = src;
                return id;
            }

            export function kofUiVideoSetSrc(video, src) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[video]) {
                    window.__kofNodes[video].src = src;
                }
            }

            export function kofUiVideoSetControls(video, controls) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[video]) {
                    window.__kofNodes[video].controls = controls ? true : false;
                    if (controls) window.__kofNodes[video].setAttribute("controls", "");
                    else window.__kofNodes[video].removeAttribute("controls");
                }
            }

            export function kofUiVideoPlay(video) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[video]
                        && typeof window.__kofNodes[video].play === "function") {
                    window.__kofNodes[video].play();
                }
            }

            export function kofUiVideoPause(video) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[video]
                        && typeof window.__kofNodes[video].pause === "function") {
                    window.__kofNodes[video].pause();
                }
            }

            export function kofUiVideoRemove(video) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[video]) {
                    const node = window.__kofNodes[video];
                    if (node.parentNode) {
                        node.parentNode.removeChild(node);
                    }
                    delete window.__kofNodes[video];
                }
            }

            export function kofUiAudioNew(src) {
                const id = kofUiCreateNode("audio", "kof-audio");
                if (id < 0) {
                    return -1;
                }
                window.__kofNodes[id].src = src;
                return id;
            }

            export function kofUiAudioSetSrc(audio, src) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[audio]) {
                    window.__kofNodes[audio].src = src;
                }
            }

            export function kofUiAudioSetControls(audio, controls) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[audio]) {
                    window.__kofNodes[audio].controls = controls ? true : false;
                    if (controls) window.__kofNodes[audio].setAttribute("controls", "");
                    else window.__kofNodes[audio].removeAttribute("controls");
                }
            }

            export function kofUiAudioPlay(audio) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[audio]
                        && typeof window.__kofNodes[audio].play === "function") {
                    window.__kofNodes[audio].play();
                }
            }

            export function kofUiAudioPause(audio) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[audio]
                        && typeof window.__kofNodes[audio].pause === "function") {
                    window.__kofNodes[audio].pause();
                }
            }

            export function kofUiAudioRemove(audio) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[audio]) {
                    const node = window.__kofNodes[audio];
                    if (node.parentNode) {
                        node.parentNode.removeChild(node);
                    }
                    delete window.__kofNodes[audio];
                }
            }

            export function kofUiHrNew() {
                return kofUiCreateNode("hr", "kof-hr");
            }

            export function kofUiHrRemove(hr) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[hr]) {
                    const node = window.__kofNodes[hr];
                    if (node.parentNode) {
                        node.parentNode.removeChild(node);
                    }
                    delete window.__kofNodes[hr];
                }
            }
            """;
}
