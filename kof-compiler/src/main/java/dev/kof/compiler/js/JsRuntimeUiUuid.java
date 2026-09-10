package dev.kof.compiler.js;

/**
 * Runtime JS do kof.uuid (STDLIB S3b/S3b.2).
 * v4 (RFC 4122), v7 (RFC 9562 time-ordered ms timestamp) e isUuid.
 * Extraído de JsRuntimeUiStdlib para garantir estrita observância ao gate ≤500 linhas.
 */
public final class JsRuntimeUiUuid {

    private JsRuntimeUiUuid() {}

    static final String UUID_RUNTIME = """

            // ── kof.uuid (STDLIB S3b / S3b.2) ──────────────────────────────
            // v4: kof_platform.randomBytesHex (16 B) -> RFC 4122
            export function kofUuidV4() {
                const hex = kof_platform.randomBytesHex(16);   // 32 chars
                const c = [...hex];
                c[12] = "4";                                    // version
                c[16] = "89ab"[parseInt(c[16], 16) >> 2];       // variant 10xx
                return c.slice(0,8).join("") + "-" + c.slice(8,12).join("") + "-"
                     + c.slice(12,16).join("") + "-" + c.slice(16,20).join("") + "-"
                     + c.slice(20,32).join("");
            }
            // v7: RFC 9562 time-ordered (48 bits unix_ts_ms + 74 bits aleatórios)
            export function kofUuidV7() {
                const ts = Date.now();
                const tsHex = ts.toString(16).padStart(12, "0");
                const randHex = kof_platform.randomBytesHex(10);
                const c = [...tsHex, ...randHex];
                c[12] = "7";                                    // version 7
                c[16] = "89ab"[parseInt(c[16], 16) >> 2];       // variant 10xx
                return c.slice(0,8).join("") + "-" + c.slice(8,12).join("") + "-"
                     + c.slice(12,16).join("") + "-" + c.slice(16,20).join("") + "-"
                     + c.slice(20,32).join("");
            }
            // isUuid: forma 8-4-4-4-12; traços em 8/13/18/23; hex (min ou
            // maiúsculo). Version/variant NÃO verificadas (mesma regra JVM/x86).
            export function kofUuidIsUuid(v) {
                if (v == null || v.length !== 36) return false;
                for (let i = 0; i < 36; i++) {
                    const c = v.charCodeAt(i);
                    if (i === 8 || i === 13 || i === 18 || i === 23) {
                        if (c !== 45) return false;
                    } else if (!((c >= 48 && c <= 57) || (c >= 97 && c <= 102) || (c >= 65 && c <= 70))) {
                        return false;
                    }
                }
                return true;
            }
            """;
}
