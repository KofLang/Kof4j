package dev.kof.compiler.js;

/**
 * Runtime JS dos métodos de coleção novos (#382/#386): indexOf/lastIndexOf/
 * addAll/subList/sort sobre List (Array) e containsValue/putIfAbsent sobre
 * Map (Map com chave canônica kofMapKeyIdx, §104c). Bloco próprio (gate 500
 * no JsRuntimeUiLayout) registrado em JsRuntimeSlices; a poda por
 * alcançabilidade só puxa as unidades usadas.
 */
public final class JsRuntimeCollections {

    private JsRuntimeCollections() {}

    static String COLLECTIONS_RUNTIME = """
            export function kofListIndexOf(list, value) {
                for (let i = 0; i < list.length; i++) {
                    if (kofValEq(list[i], value)) return i;
                }
                return -1;
            }

            export function kofListLastIndexOf(list, value) {
                for (let i = list.length - 1; i >= 0; i--) {
                    if (kofValEq(list[i], value)) return i;
                }
                return -1;
            }

            export function kofListAddAll(list, other) {
                if (!other || other.length === 0) return 0;
                for (let i = 0; i < other.length; i++) list.push(other[i]);
                return 1;
            }

            export function kofListSubList(list, from, to) {
                if (from < 0 || to > list.length || from > to) {
                    throw new Error("Index out of bounds: " + from + " (size " + list.length + ")");
                }
                return list.slice(from, to);
            }

            export function kofListTake(list, n) {
                if (n < 0) throw new Error("PAGINATION: count must be >= 0");
                return list.slice(0, n < list.length ? n : list.length);
            }

            export function kofListDrop(list, n) {
                if (n < 0) throw new Error("PAGINATION: count must be >= 0");
                return list.slice(n < list.length ? n : list.length, list.length);
            }

            export function kofListSlice(list, offset, limit) {
                if (offset < 0 || limit < 0) throw new Error("PAGINATION: limit/offset must be >= 0");
                const start = offset < list.length ? offset : list.length;
                const remaining = list.length - start;
                return list.slice(start, start + (limit < remaining ? limit : remaining));
            }

            // D-MULTIPARADIGMA-PHASE1A — eager short-circuit quantifiers.
            // Truthiness mirrors the JVM rule exactly (true or number 1 —
            // notably NOT 1n: JVM rejects Long via Integer.equals, so JS
            // rejects BigInt too); lambdas throw through (short-circuit).
            function kofQuantCall(fn, x) {
                return (typeof fn.invoke === 'function' ? fn.invoke(x) : fn(x));
            }

            // D-MULTIPARADIGMA-PHASE1A slice 1g — two-argument version for
            // the sorted comparator (SAM object or plain closure, same rule).
            function kofQuantCall2(fn, a, b) {
                return (typeof fn.invoke === 'function' ? fn.invoke(a, b) : fn(a, b));
            }

            function kofQuantTrue(v) {
                return v === true || v === 1;
            }

            export function kofListAny(list, fn) {
                for (let i = 0; i < list.length; i++) {
                    if (kofQuantTrue(kofQuantCall(fn, list[i]))) return true;
                }
                return false;
            }

            export function kofListAll(list, fn) {
                for (let i = 0; i < list.length; i++) {
                    if (!kofQuantTrue(kofQuantCall(fn, list[i]))) return false;
                }
                return true;
            }

            export function kofListNone(list, fn) {
                for (let i = 0; i < list.length; i++) {
                    if (kofQuantTrue(kofQuantCall(fn, list[i]))) return false;
                }
                return true;
            }

            // D-MULTIPARADIGMA-PHASE1A slice 1b — find returns the match or
            // null (like kofMapGet on missing); count(pred) counts matches.
            export function kofListFind(list, fn) {
                for (let i = 0; i < list.length; i++) {
                    if (kofQuantTrue(kofQuantCall(fn, list[i]))) return list[i];
                }
                return null;
            }

            export function kofListCountPred(list, fn) {
                let n = 0;
                for (let i = 0; i < list.length; i++) {
                    if (kofQuantTrue(kofQuantCall(fn, list[i]))) n++;
                }
                return n;
            }

            // D-MULTIPARADIGMA-PHASE1A slice 1c — forEach runs for effect.
            export function kofListForeach(list, fn) {
                for (let i = 0; i < list.length; i++) {
                    kofQuantCall(fn, list[i]);
                }
            }

            // D-MULTIPARADIGMA-PHASE1A slice 1d — flatMap concatenates each
            // element's List in order (native one-level flatten).
            export function kofListFlatmap(list, fn) {
                return list.flatMap(x => kofQuantCall(fn, x));
            }

            // D-MULTIPARADIGMA-PHASE1A slice 1e — distinct dedups by kofValEq
            // (same rule as contains on this target); the tag arg is
            // Native-only and ignored here.
            export function kofListDistinct(list, tag) {
                const out = [];
                for (let i = 0; i < list.length; i++) {
                    let found = false;
                    for (let j = 0; j < out.length; j++) {
                        if (kofValEq(out[j], list[i])) { found = true; break; }
                    }
                    if (!found) out.push(list[i]);
                }
                return out;
            }

            function kofNaturalCmp(a, b) {
                if (typeof a === "number" && typeof b === "number") {
                    return a < b ? -1 : a > b ? 1 : 0;
                }
                if (typeof a === "bigint" && typeof b === "bigint") {
                    return a < b ? -1 : a > b ? 1 : 0;
                }
                if (typeof a === "string" && typeof b === "string") {
                    return a < b ? -1 : a > b ? 1 : 0;
                }
                if (typeof a === "boolean" && typeof b === "boolean") {
                    return (a === b) ? 0 : (a ? 1 : -1);
                }
                return 0;
            }

            export function kofListSort(list) {
                list.sort(kofNaturalCmp);
            }

            export function kofListSorted(list, tag) {
                const out = list.slice();
                for (let i = 1; i < out.length; i++) {
                    const key = out[i];
                    let j = i - 1;
                    while (j >= 0 && kofNaturalCmp(out[j], key) > 0) {
                        out[j + 1] = out[j];
                        j--;
                    }
                    out[j + 1] = key;
                }
                return out;
            }

            export function kofListSortedCmp(list, cmp) {
                const out = list.slice();
                for (let i = 1; i < out.length; i++) {
                    const key = out[i];
                    let j = i - 1;
                    while (j >= 0 && kofQuantCall2(cmp, out[j], key) > 0) {
                        out[j + 1] = out[j];
                        j--;
                    }
                    out[j + 1] = key;
                }
                return out;
            }

            // #685 — enum sort(): in-place insertion via the comparator
            // (a.compareTo(b) synthesized by the compiler).
            export function kofListSortCmp(list, cmp) {
                for (let i = 1; i < list.length; i++) {
                    const key = list[i];
                    let j = i - 1;
                    while (j >= 0 && kofQuantCall2(cmp, list[j], key) > 0) {
                        list[j + 1] = list[j];
                        j--;
                    }
                    list[j + 1] = key;
                }
            }

            export function kofListGroupBy(list, fn, tag) {
                const out = new Map();
                for (const o of list) {
                    const key = kofQuantCall(fn, o);
                    let bucket = out.get(key);
                    if (bucket === undefined) {
                        bucket = [];
                        out.set(key, bucket);
                    }
                    bucket.push(o);
                }
                return out;
            }

            export function kofMapContainsValue(map, value) {
                for (const v of map.values()) {
                    if (kofValEq(v, value)) return 1;
                }
                return 0;
            }

            export function kofMapPutIfAbsent(map, key, value) {
                const k = kofMapKeyIdx(map, key);
                if (k !== undefined) {
                    const prev = map.get(k);
                    return prev === undefined ? null : prev;
                }
                map.set(key, value);
                return null;
            }
            """;
}
