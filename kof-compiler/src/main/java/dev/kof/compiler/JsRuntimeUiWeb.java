package dev.kof.compiler;

/** kof-runtime.mjs — web server (WEB001) + timers cooperativos. */
final class JsRuntimeUiWeb {
    private JsRuntimeUiWeb() {
    }

    static final String UI_WEB_RUNTIME = """
            // ── Web runtime (WEB001) — GraalJS HttpServer com handler invoke
            // Handler lambda tem metodo invoke(); usamos Value para interop.
            const kofWebApps = new Map();
            let kofWebPort = 8080;
            let kofWebServer = null;
            function kofWebHandleRequest(exchange) {
                const path = exchange.getRequestURI().getPath();
                const method = exchange.getRequestMethod();
                const handler = kofWebApps.get(method + ":" + path);
                if (!handler) {
                    exchange.sendResponseHeaders(404, 0);
                    exchange.getResponseBody().close();
                    return;
                }
                try {
                    const body = exchange.getRequestBodyBodyHandlers ? exchange.getRequestBodyBodyHandlers().fileDownload() : null;
                    const ctx = {
                        request: {
                            method: method,
                            path: path,
                            query: exchange.getRequestURI().getQuery(),
                            headers: exchange.getRequestHeaders()
                        },
                        body: body,
                        response: {
                            status: function(code, text) {
                                exchange.sendResponseHeaders(code, (text || "").length);
                                const os = exchange.getResponseBody();
                                os.write(text ? String.toBytes(text) : new Uint8Array(0));
                                os.close();
                            },
                            header: function(name, value) {
                                exchange.getResponseHeaders().set(name, value);
                            }
                        }
                    };
                    if (typeof handler.invoke === 'function') handler.invoke(ctx);
                    else if (typeof handler === 'function') handler(ctx);
                } catch(e) {
                    exchange.sendResponseHeaders(500, String.toBytes(String(e)));
                }
            }
            export function kofWebAppNew() {
                const app = {
                    handlers: new Map(),
                    _register: function(method, path, handler) {
                        this.handlers.set(method + ":" + path, handler);
                    }
                };
                const id = "app_" + kofWebApps.size;
                kofWebApps.set(id, app);
                return id;
            }
            export function kofWebRoute(appId, method, path, handler) {
                const app = kofWebApps.get(appId);
                if (!app) throw new Error("Invalid app handle: " + appId);
                app._register(method, path, handler);
                return 0;
            }
            export function kofWebListen(appId, port) {
                const app = kofWebApps.get(appId);
                if (!app) throw new Error("Invalid app handle: " + appId);
                kofWebPort = port | 0 || 8080;
                const HttpServer = Java.type('com.sun.net.httpserver.HttpServer');
                kofWebServer = HttpServer.create(Java.type('java.net.InetSocketAddress').create(0, kofWebPort), 0);
                for (const [key, handler] of app.handlers) {
                    const [method, path] = key.split(":");
                    kofWebServer.createContext(path, (exchange) => kofWebHandleRequest(exchange));
                }
                kofWebServer.setExecutor(null);
                kofWebServer.start();
                return 0;
            }

            export function kofEnumValueOf(values, name) {
                if (values != null && name != null) {
                    for (const v of values) {
                        if (v === name) return v;
                    }
                }
                return null;
            }

            export function kofNow() {
                return Date.now();
            }

            export function kofTimeNow() {
                return Date.now();
            }

            export function kofTimeSleep(ms) {
                const end = Date.now() + ms;
                // bombeia a fila cooperativa de timers durante o wait (GraalJS
                // single-thread: sem isso, time.interval nunca dispara)
                while (Date.now() < end) {
                    kofTimePump();
                }
                kofTimePump();
            }

            // ── Cooperative timers (TIME001 fechado): GraalJS não tem
            // event loop nativo nem setInterval, então os jobs vivem numa
            // fila bombeada por kofTimeSleep (que já bloqueia). Em browser/
            // Node, onde setInterval existe, os timers disparam assíncronos.
            const kofTimeJobs = new Map();
            const kofTimeSeq = { value: 0 };
            function kofTimeRunJob(fn) {
                if (typeof fn.invoke === 'function') fn.invoke();
                else if (typeof fn === 'function') fn();
            }
            export function kofTimeInterval(ms, fn) {
                if (typeof setInterval === 'function') {
                    return "n" + String(setInterval(() => kofTimeRunJob(fn), ms));
                }
                const id = "c" + (++kofTimeSeq.value);
                kofTimeJobs.set(id, { ms: ms, run: () => kofTimeRunJob(fn), next: Date.now() + ms });
                return id;
            }
            function kofTimePump() {
                const now = Date.now();
                for (const [id, job] of kofTimeJobs) {
                    if (now >= job.next) {
                        job.next = now + job.ms;
                        job.run();
                    }
                }
            }
            export function kofTimeCancel(id) {
                const key = String(id);
                if (key.charAt(0) === "n") {
                    if (typeof clearInterval === 'function') clearInterval(Number(key.substring(1)));
                    return;
                }
                kofTimeJobs.delete(key);
            }

            export function kofConfigGet(key) {
                return kofConfigLookup(key);
            }

            export function kofConfigEnv(key) {
                return kofConfigLookup(key);
            }

            export function kofConfigHas(key) {
                return kofConfigLookup(key) != null ? 1 : 0;
            }

            export function kofConfigRequired(key) {
                const v = kofConfigLookup(key);
                if (v == null) {
                    throw new Error("Kof config: missing required key '" + key + "'");
                }
                return v;
            }

            export function kofConfigStr(key, def) {
                const v = kofConfigLookup(key);
                return v != null ? v : def;
            }

            export function kofConfigInt(key, def) {
                const v = kofConfigLookup(key);
                if (v == null) return def | 0;
                const n = parseInt(v, 10);
                return isNaN(n) ? def | 0 : n | 0;
            }

            export function kofConfigLong(key, def) {
                const v = kofConfigLookup(key);
                if (v == null) return def;
                const n = parseInt(v, 10);
                return isNaN(n) ? def : n;
            }

            export function kofConfigBool(key, def) {
                const v = kofConfigLookup(key);
                if (v == null) return def ? 1 : 0;
                const s = String(v).toLowerCase();
                if (s === 'true' || s === '1' || s === 'yes') return 1;
                if (s === 'false' || s === '0' || s === 'no') return 0;
                return def ? 1 : 0;
            }

            // P2 (docs/stdlib-config.md §8.2): interpolação ${key} —
            // resolve referências entre chaves; ciclo/missing → literal.
            function kofConfigInterpolate(value) {
                if (!value || !value.includes('${')) return value;
                const seen = new Set();
                let current = value;
                for (let depth = 0; depth < 16; depth++) {
                    const start = current.indexOf('${');
                    if (start < 0) break;
                    const end = current.indexOf('}', start + 2);
                    if (end < 0) break;
                    const ref = current.slice(start + 2, end);
                    const resolved = kofConfigLookup(ref);
                    if (resolved == null || seen.has(ref)) return value;
                    seen.add(ref);
                    current = current.slice(0, start) + resolved + current.slice(end + 1);
                }
                return current;
            }

            function kofConfigLookup(key) {
                try {
                    if (typeof process !== 'undefined' && process.env) {
                        if (key in process.env) return process.env[key];
                        const kofKey = 'KOF_' + key.replace(/[^a-zA-Z0-9]/g, '_').toUpperCase();
                        if (kofKey in process.env) return process.env[kofKey];
                        const flat = key.replace(/\\./g, '_').toUpperCase();
                        if (flat in process.env) return process.env[flat];
                    }
                } catch (e) {}
                try {
                    if (typeof globalThis !== 'undefined' && globalThis.__kofConfig && key in globalThis.__kofConfig) {
                        return globalThis.__kofConfig[key];
                    }
                } catch (e) {}
                try {
                    // arquivo kof.config no diretório de trabalho (precedência 4)
                    if (!globalThis.__kofConfigFile) {
                        globalThis.__kofConfigFile = {};
                        if (typeof kof_platform !== 'undefined' && kof_platform.readFile) {
                            const text = kof_platform.readFile('kof.config');
                            if (text) {
                                for (const line of String(text).split('\\n')) {
                                    const t = line.trim();
                                    if (!t || t.startsWith('#')) continue;
                                    const eq = t.indexOf('=');
                                    if (eq <= 0) continue;
                                    globalThis.__kofConfigFile[t.slice(0, eq).trim()] = t.slice(eq + 1).trim();
                                }
                            }
                        }
                    }
                    if (key in globalThis.__kofConfigFile) return kofConfigInterpolate(globalThis.__kofConfigFile[key]);
                } catch (e) {}
                return null;
            }

            """;

}
