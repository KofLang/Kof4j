package dev.kof.compiler.js;

/**
 * kof-runtime.mjs — kof.uuid (STDLIB S3b-ext). isUuid: shape RFC 4122.
 * Fragmento separado (JsRuntimeUiStdlib 492/500 — gate ≤500).
 */
public final class JsRuntimeUiUuid {
    private JsRuntimeUiUuid() {
    }

    static final String UI_UUID_RUNTIME = """
            // v7 (RFC 9562, S3b.2): b0..b5 = unix-ts-ms 48 bits big-endian
            // (Date.now() = mesma fonte do time.now; Number exato ate 2^53);
            // b6 = 0111|rand; b8 = 10x|rand (mask, idem v4). Mesma regra
            // byte-level do JVM — paridade byte-a-byte do formato.
            export function kofUuidV7() {
                const hex = kof_platform.randomBytesHex(16);   // 32 chars
                const c = [...hex];
                const t = Math.floor(Date.now());
                for (let i = 0; i < 6; i++) {
                    const b = Math.floor(t / Math.pow(2, 8*(5-i))) % 256;
                    c[i*2] = "0123456789abcdef"[b >> 4];
                    c[i*2+1] = "0123456789abcdef"[b & 15];
                }
                c[12] = "7";                                    // version
                c[16] = "89ab"[parseInt(c[16], 16) >> 2];       // variant 10xx
                return c.slice(0,8).join("") + "-" + c.slice(8,12).join("") + "-"
                     + c.slice(12,16).join("") + "-" + c.slice(16,20).join("") + "-"
                     + c.slice(20,32).join("");
            }

            // isUuid (S3b-ext): shape RFC 4122 — 36 chars: 32 hex + hífens
            // fixos em 8/13/18/23. null => false. Não checa versão/variante.
            export function kofUuidIsUuid(s) {
                if (s === null || s.length !== 36) return 0;
                const H = c => (c >= 48 && c <= 57) || (c >= 97 && c <= 102) || (c >= 65 && c <= 70);
                for (let i = 0; i < 36; i++) {
                    const c = s.charCodeAt(i);
                    if (i === 8 || i === 13 || i === 18 || i === 23) {
                        if (c !== 45) return 0;   // '-'
                    } else if (!H(c)) {
                        return 0;
                    }
                }
                return 1;
            }
        """;
}
