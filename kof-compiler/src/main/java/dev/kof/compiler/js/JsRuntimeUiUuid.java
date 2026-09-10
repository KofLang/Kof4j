package dev.kof.compiler.js;

/**
 * kof-runtime.mjs — kof.uuid (STDLIB S3b-ext). isUuid: shape RFC 4122.
 * Fragmento separado (JsRuntimeUiStdlib 492/500 — gate ≤500).
 */
public final class JsRuntimeUiUuid {
    private JsRuntimeUiUuid() {
    }

    static final String UI_UUID_RUNTIME = """

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
