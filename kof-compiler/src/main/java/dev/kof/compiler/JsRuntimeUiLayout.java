package dev.kof.compiler;

/** kof-runtime.mjs — primitivas de layout + coleções/canais. */
final class JsRuntimeUiLayout {
    private JsRuntimeUiLayout() {
    }

    static final String UI_LAYOUT_RUNTIME = """
            // ── Layout primitives (docs/ui/architecture.md §2.8) ──────
            // CSS-first: the framework never computes pixel positions; each
            // primitive maps to a flex/grid CSS pattern.
            function kofUiLayoutContainerNew(tag, className, ids, extraStyle) {
                const id = kofUiCreateNode(tag, className);
                if (id < 0) {
                    return -1;
                }
                const node = window.__kofNodes[id];
                if (extraStyle) {
                    for (const k in extraStyle) node.style[k] = extraStyle[k];
                }
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

            export function kofUiBoxNew(ids) {
                return kofUiLayoutContainerNew("div", "kof-box kof-view", ids);
            }

            export function kofUiStackNew(ids) {
                // overlapping children (z-stack): all children in the same cell
                return kofUiLayoutContainerNew("div", "kof-stack", ids,
                        { display: "grid" });
            }

            export function kofUiWrapNew(ids) {
                return kofUiLayoutContainerNew("div", "kof-wrap", ids,
                        { display: "flex", flexDirection: "row", flexWrap: "wrap" });
            }

            export function kofUiGridNew(cols, ids) {
                return kofUiLayoutContainerNew("div", "kof-grid", ids,
                        { display: "grid",
                          gridTemplateColumns: "repeat(" + (cols > 0 ? cols : 1) + ", 1fr)" });
            }

            export function kofUiSpacerNew(size) {
                return kofUiLayoutContainerNew("div", "kof-spacer", null,
                        { flex: size > 0 ? String(size) : "1" });
            }

            export function kofUiCenterNew(ids) {
                return kofUiLayoutContainerNew("div", "kof-center", ids,
                        { display: "flex", alignItems: "center", justifyContent: "center" });
            }

            export function kofUiAlignNew(horizontal, vertical, ids) {
                // horizontal/vertical: 0=start, 1=center, 2=end
                const justify = horizontal === 1 ? "center" : horizontal === 2 ? "flex-end" : "flex-start";
                const align = vertical === 1 ? "center" : vertical === 2 ? "flex-end" : "flex-start";
                return kofUiLayoutContainerNew("div", "kof-align", ids,
                        { display: "flex", justifyContent: justify, alignItems: align });
            }

            export function kofUiViewRemove(view) {
                if (typeof document !== "undefined" && window.__kofNodes && window.__kofNodes[view]) {
                    const node = window.__kofNodes[view];
                    if (node.parentNode) {
                        node.parentNode.removeChild(node);
                    }
                    delete window.__kofNodes[view];
                }
            }

            export function kofUiColorToCss(color) {
                const r = (color >>> 24) & 0xFF;
                const g = (color >>> 16) & 0xFF;
                const b = (color >>> 8) & 0xFF;
                const a = color & 0xFF;
                if (a === 255) {
                    return "rgb(" + r + ", " + g + ", " + b + ")";
                }
                return "rgba(" + r + ", " + g + ", " + b + ", " + a + ")";
            }

            export function kofListNew() {
                return [];
            }

            export function kofChannelNew() {
                return { items: [], resolvers: [] };
            }

            export function kofChannelSend(chan, value) {
                if (chan.resolvers.length > 0) {
                    chan.resolvers.shift()(value);
                } else {
                    chan.items.push(value);
                }
            }

            export function kofChannelReceive(chan) {
                if (chan.items.length > 0) {
                    return Promise.resolve(chan.items.shift());
                }
                return new Promise(resolve => chan.resolvers.push(resolve));
            }

            export function kofListAdd(list, value) {
                list.push(value);
            }

            export function kofListGet(list, index) {
                if (index < 0 || index >= list.length) {
                    throw new Error("Index out of bounds: " + index + " (size " + list.length + ")");
                }
                return list[index];
            }

            export function kofListSet(list, index, value) {
                if (index < 0 || index >= list.length) {
                    throw new Error("Index out of bounds: " + index + " (size " + list.length + ")");
                }
                list[index] = value;
            }

            export function kofListSize(list) {
                return list.length;
            }

            export function kofListContains(list, value) {
                return list.includes(value) ? 1 : 0;
            }

            export function kofListIsEmpty(list) {
                return list.length === 0 ? 1 : 0;
            }

            export function kofListRemove(list, index) {
                if (index < 0 || index >= list.length) {
                    throw new Error("Index out of bounds: " + index + " (size " + list.length + ")");
                }
                return list.splice(index, 1)[0];
            }

            export function kofListClear(list) {
                list.length = 0;
            }

            export function kofMapNew() {
                return new Map();
            }

            export function kofMapPut(map, key, value) {
                const prev = map.get(key);
                map.set(key, value);
                return prev === undefined ? null : prev;
            }

            export function kofMapGet(map, key) {
                const v = map.get(key);
                return v === undefined ? null : v;
            }

            export function kofMapRemove(map, key) {
                const v = map.get(key);
                map.delete(key);
                return v === undefined ? null : v;
            }

            export function kofMapContains(map, key) {
                return map.has(key) ? 1 : 0;
            }

            export function kofMapSize(map) {
                return map.size;
            }

            export function kofMapClear(map) {
                map.clear();
            }

            export function kofMapIsEmpty(map) {
                return map.size === 0 ? 1 : 0;
            }

            export function kofMapKeys(map) {
                return Array.from(map.keys());
            }

            export function kofMapValues(map) {
                return Array.from(map.values());
            }

            export function kofSetNew() {
                return new Set();
            }

            export function kofSetAdd(set, value) {
                const had = set.has(value);
                set.add(value);
                return had ? 0 : 1;
            }

            export function kofSetContains(set, value) {
                return set.has(value) ? 1 : 0;
            }

            export function kofSetRemove(set, value) {
                return set.delete(value) ? 1 : 0;
            }

            export function kofSetSize(set) {
                return set.size;
            }

            export function kofSetClear(set) {
                set.clear();
            }

            export function kofSetIsEmpty(set) {
                return set.size === 0 ? 1 : 0;
            }

            export function kofListMap(list, fn) {
                return list.map(x => (typeof fn.invoke === 'function' ? fn.invoke(x) : fn(x)));
            }

            export function kofListFilter(list, fn) {
                return list.filter(x => !!(typeof fn.invoke === 'function' ? fn.invoke(x) : fn(x)));
            }

            export function kofListReduce(list, initial, fn) {
                return list.reduce((acc, x) => (typeof fn.invoke === 'function' ? fn.invoke(acc, x) : fn(acc, x)), initial);
            }

            let kofActiveTasks = 0;
            globalThis.kofActiveTasks = kofActiveTasks;

            export function kofSpawn(task) {
                kofActiveTasks++;
                globalThis.kofActiveTasks = kofActiveTasks;
                Promise.resolve().then(() => {
                    return (task && typeof task.invoke === "function") ? task.invoke() : task;
                }).catch(err => {
                    const msg = (err && err.message !== undefined) ? err.message : String(err);
                    (console.error || console.log)("spawn task failed: " + msg);
                }).finally(() => {
                    kofActiveTasks--;
                    globalThis.kofActiveTasks = kofActiveTasks;
                });
            }

            export function kofSpawnResult(task) {
                kofActiveTasks++;
                globalThis.kofActiveTasks = kofActiveTasks;
                const handle = { done: false, value: undefined, error: undefined, cancelled: false };
                const promise = Promise.resolve().then(() => {
                    return (task && typeof task.invoke === "function") ? task.invoke() : task;
                }).then(value => {
                    handle.done = true;
                    handle.value = value;
                    return value;
                }).catch(err => {
                    handle.done = true;
                    handle.error = err;
                    throw err;
                }).finally(() => {
                    kofActiveTasks--;
                    globalThis.kofActiveTasks = kofActiveTasks;
                });
                handle.promise = promise;
                promise.catch(() => {});
                return handle;
            }

            export function kofPoll(handle) {
                return handle && handle.done && !handle.error ? handle.value : null;
            }

            export function kofDone(handle) {
                return (handle && handle.done) ? 1 : 0;
            }

            export function kofCancel(handle) {
                if (!handle) return 0;
                const wasDone = handle.done;
                if (!wasDone) handle.cancelled = true;
                return wasDone ? 0 : 1;
            }

            export function kofCancelled() {
                // Sem thread-local em JS embutido: não há "task atual" para
                // consultar — cancelamento cooperativo via handle.cancelled.
                return 0;
            }

            export function kofSelectAny(handles) {
                return Promise.race((handles || []).map(h =>
                    (h && h.promise !== undefined) ? h.promise : Promise.resolve(h)));
            }

            export function kofAwait(handle) {
                if (handle != null && handle.promise !== undefined) {
                    return handle.promise;
                }
                return handle;
            }

            export async function kofAwaitTimeout(handle, timeoutMs) {
                const deadline = Date.now() + timeoutMs;
                while (Date.now() < deadline) {
                    if (handle && handle.done) {
                        if (handle.error) {
                            const e = handle.error;
                            throw (e && e.message !== undefined) ? e.message : String(e);
                        }
                        return handle.value;
                    }
                    await Promise.resolve();
                }
                throw "await timeout after " + timeoutMs + "ms";
            }

            export function kofWebStub() {
                // JS stub for kof.web/db — keeps KofJS compilable; real impl is JVM/Native
                return 0;
            }

            let kofHttpTimeoutSec = 10;
            let kofHttpRetries = 0;
            let kofHttpCircuitTrips = 0;
            let kofHttpCircuitFailures = 0;
            let kofHttpCircuitOpenUntil = 0;
            const KOF_HTTP_CIRCUIT_WINDOW_MS = 30000;
            export function kofHttpTimeoutSet(sec) { kofHttpTimeoutSec = sec; }
            export function kofHttpRetrySet(n) { kofHttpRetries = Math.max(0, n | 0); }
            export function kofHttpCircuitSet(trips) {
                kofHttpCircuitTrips = Math.max(0, trips | 0);
                if (kofHttpCircuitTrips <= 0) { kofHttpCircuitFailures = 0; kofHttpCircuitOpenUntil = 0; }
            }
            function kofHttpCircuitOpen() {
                if (kofHttpCircuitOpenUntil === 0) return false;
                if (Date.now() >= kofHttpCircuitOpenUntil) { kofHttpCircuitOpenUntil = 0; return false; }
                return true;
            }
            function kofHttpCircuitRecordFailure() {
                if (kofHttpCircuitTrips <= 0) return;
                if (++kofHttpCircuitFailures >= kofHttpCircuitTrips) {
                    kofHttpCircuitOpenUntil = Date.now() + KOF_HTTP_CIRCUIT_WINDOW_MS;
                }
            }
            function kofHttpCircuitRecordSuccess() {
                kofHttpCircuitFailures = 0;
                kofHttpCircuitOpenUntil = 0;
            }
            export function kofHttpGet(url) { return kofHttpRequest(url, "GET", null, null); }
            export function kofHttpGetHeaders(url, headers) { return kofHttpRequest(url, "GET", headers, null); }
            export function kofHttpDelete(url) { return kofHttpRequest(url, "DELETE", null, null); }
            export function kofHttpDeleteHeaders(url, headers) { return kofHttpRequest(url, "DELETE", headers, null); }
            export function kofHttpOptions(url) { return kofHttpRequest(url, "OPTIONS", null, null); }
            export function kofHttpOptionsHeaders(url, headers) { return kofHttpRequest(url, "OPTIONS", headers, null); }
            export function kofHttpPost(url, body) { return kofHttpRequest(url, "POST", null, body); }
            export function kofHttpPostHeaders(url, body, headers) { return kofHttpRequest(url, "POST", headers, body); }
            export function kofHttpPut(url, body) { return kofHttpRequest(url, "PUT", null, body); }
            export function kofHttpPutHeaders(url, body, headers) { return kofHttpRequest(url, "PUT", headers, body); }
            export function kofHttpPatch(url, body) { return kofHttpRequest(url, "PATCH", null, body); }
            export function kofHttpPatchHeaders(url, body, headers) { return kofHttpRequest(url, "PATCH", headers, body); }
            export function kofHttpStatus(url) {
                try {
                    const HttpClient = Java.type('java.net.http.HttpClient');
                    const HttpRequest = Java.type('java.net.http.HttpRequest');
                    const URI = Java.type('java.net.URI');
                    const Duration = Java.type('java.time.Duration');
                    let client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(kofHttpTimeoutSec)).build();
                    let builder = HttpRequest.newBuilder().uri(URI.create(url)).method("HEAD", HttpRequest.BodyPublishers.noBody()).timeout(Duration.ofSeconds(kofHttpTimeoutSec));
                    let req = builder.build();
                    let resp = client.send(req, Java.type('java.net.http.HttpResponse$BodyHandlers').discarding());
                    return resp.statusCode();
                } catch(e) { return 0; }
            }
            function kofHttpRequest(url, method, headers, body) {
                if (kofHttpCircuitOpen()) {
                    throw new Error("kof.http circuit open (fail fast): " + url);
                }
                let lastErr = null;
                const attempts = kofHttpRetries + 1;
                for (let attempt = 0; attempt < attempts; attempt++) {
                    try {
                        // Prefer Java HttpClient via GraalJS interop (synchronous, works in KofJsRunner)
                        if (typeof Java !== 'undefined' && Java.type) {
                            const HttpClient = Java.type('java.net.http.HttpClient');
                            const HttpRequest = Java.type('java.net.http.HttpRequest');
                            const URI = Java.type('java.net.URI');
                            const Duration = Java.type('java.time.Duration');
                            let client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(kofHttpTimeoutSec)).build();
                            let builder = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(kofHttpTimeoutSec));
                            if (headers) {
                                let lines = headers.split("\\n");
                                for (let line of lines) {
                                    let idx = line.indexOf(":");
                                    if (idx > 0) builder.header(line.substring(0, idx).trim(), line.substring(idx+1).trim());
                                }
                            }
                            let publisher = body != null ? HttpRequest.BodyPublishers.ofString(body) : HttpRequest.BodyPublishers.noBody();
                            builder.method(method, publisher);
                            let req = builder.build();
                            let resp = client.send(req, Java.type('java.net.http.HttpResponse$BodyHandlers').ofString());
                            if (resp.statusCode() >= 500) {
                                lastErr = new Error("HTTP " + resp.statusCode() + " from " + url);
                                kofHttpCircuitRecordFailure();
                                continue;
                            }
                            kofHttpCircuitRecordSuccess();
                            return resp.body() != null ? resp.body() : "";
                        }
                        // Fallback to fetch if Java interop not available (Node/Browser)
                        if (typeof fetch !== 'undefined') {
                            // synchronous fallback not possible - use deasync via Atomics if available
                            // For MVP, do blocking via fetch sync is not supported; return empty
                            kofHttpCircuitRecordSuccess();
                            return "";
                        }
                        kofHttpCircuitRecordSuccess();
                        return "";
                    } catch(e) {
                        lastErr = e;
                        kofHttpCircuitRecordFailure();
                    }
                }
                if (lastErr == null) lastErr = new Error("request failed: " + url);
                throw lastErr;
            }

            export function kofSchedulerEvery(ms, fn) {
                // delega ao kofTimeInterval: fila cooperativa bombeada por
                // kofTimeSleep no GraalJS (sem setInterval), nativa no browser
                return kofTimeInterval(ms, fn);
            }
            export function kofSchedulerAt(cron, fn) {
                return kofSchedulerEvery(60000, fn);
            }
            export function kofSchedulerCancel(id) { kofTimeCancel(id); }

            """;

}
