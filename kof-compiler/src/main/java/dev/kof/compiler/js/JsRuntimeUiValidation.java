package dev.kof.compiler.js;

/**
 * kof-runtime.mjs — kof.validation (documentos BR). Extraído do padrão dos
 * JsRuntimeUi* (gate ≤500). S12: formatCpf/formatCep (pontuação BR; no-op
 * quando o nº de dígitos não confere — face leniente, paridade com isCpf).
 */
public final class JsRuntimeUiValidation {
    private JsRuntimeUiValidation() {
    }

    static final String UI_VALIDATION_RUNTIME = """

            // ── kof.validation (STDLIB S12) — pontuação BR ─────────────
            // 11 dígitos => DDD.DDD.DDD-DD; 8 => DDDDD-DDDD; senão original
            // (null => null; nunca lança — face leniente da lane).
            export function kofValidationFormatCpf(s) {
                if (s == null) return null;
                const d = kofBrDigits(s);
                if (d.length !== 11) return s;
                const g = n => d.slice(n, n + 3).join('');
                return g(0) + '.' + g(3) + '.' + g(6) + '-' + d[9] + d[10];
            }
            export function kofValidationFormatCep(s) {
                if (s == null) return null;
                const d = kofBrDigits(s);
                if (d.length !== 8) return s;
                return d.slice(0, 5).join('') + '-' + d.slice(5).join('');
            }
        """;
}
