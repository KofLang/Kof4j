package dev.kof.compiler;

/** kof-runtime.mjs — suporte de UI (mq/stores/router). */
final class JsRuntimeUiSupport {
    private JsRuntimeUiSupport() {
    }

    static final String UI_SUPPORT_RUNTIME = """
            export const kofMqSubs = new Map();
            export const kofMqQueues = new Map();
            export let kofMqSeq = 0;
            export function kofMqPublish(topic, msg) {
                const subs = kofMqSubs.get(topic);
                if (subs) {
                    for (const fn of [...subs]) {
                        try {
                            if (typeof fn.invoke === 'function') fn.invoke(msg);
                            else if (typeof fn === 'function') fn(msg);
                        } catch (e) {}
                    }
                }
            }
            export function kofMqSubscribe(topic, fn) {
                if (!kofMqSubs.has(topic)) kofMqSubs.set(topic, []);
                kofMqSubs.get(topic).push(fn);
            }
            export function kofMqUnsubscribe(topic, fn) {
                const subs = kofMqSubs.get(topic);
                if (!subs) return;
                const idx = subs.indexOf(fn);
                if (idx >= 0) subs.splice(idx, 1);
            }
            export function kofMqQueue() {
                const id = 'q-' + (++kofMqSeq);
                kofMqQueues.set(id, []);
                return id;
            }
            export function kofMqPush(queue, msg) {
                const q = kofMqQueues.get(queue);
                if (q) q.push(msg);
            }
            export function kofMqPop(queue) {
                const q = kofMqQueues.get(queue);
                if (!q || q.length === 0) return null;
                return q.shift();
            }
            export function kofMqQueueSize(queue) {
                const q = kofMqQueues.get(queue);
                return q ? q.length : 0;
            }

            export const kofCacheData = new Map();
            export const kofCacheExpiry = new Map();
            export function kofCacheGet(key) {
                const exp = kofCacheExpiry.get(key);
                if (exp != null && exp !== 0 && Date.now() > exp) {
                    kofCacheData.delete(key);
                    kofCacheExpiry.delete(key);
                    return null;
                }
                return kofCacheData.get(key) ?? null;
            }
            export function kofCacheSet(key, value) {
                kofCacheData.set(key, value);
                kofCacheExpiry.delete(key);
            }
            export function kofCacheSetTtl(key, value, ttl) {
                kofCacheData.set(key, value);
                if (ttl > 0) {
                    kofCacheExpiry.set(key, Date.now() + ttl * 1000);
                } else {
                    kofCacheExpiry.delete(key);
                }
            }
            export function kofCacheTtl(key) {
                const exp = kofCacheExpiry.get(key);
                if (exp == null || exp === 0) return -1;
                const remaining = exp - Date.now();
                if (remaining <= 0) {
                    kofCacheData.delete(key);
                    kofCacheExpiry.delete(key);
                    return -1;
                }
                return Math.floor(remaining / 1000);
            }
            export function kofCacheDelete(key) {
                kofCacheData.delete(key);
                kofCacheExpiry.delete(key);
            }
            export function kofCacheClear() {
                kofCacheData.clear();
                kofCacheExpiry.clear();
            }

            """;

}
