package dev.kof.compiler.lang;

import java.util.List;
import java.util.Map;

/**
 * D-PORTUKOF F6 (07/10) — catálogo de diagnósticos localizados. PRINCÍPIO: um
 * único sistema de diagnósticos; o CÓDIGO canônico (SEM025, LEX005, PARSE011…)
 * nunca muda e é a única chave de localização. A mensagem humana em inglês é
 * canônica (teste, LSP, `--json`, tooling); esta classe só produz a forma PT-BR
 * para a superfície `.ptkf`, renderizando o template do CÓDIGO com os
 * ARGUMENTOS ESTRUTURADOS capturados na emissão. Nunca há tradução por regex ou
 * `message.replace(...)`.
 *
 * Placeholders: `{0}`, `{1}`… na MESMA ordem dos argumentos estruturados. Um
 * template sem placeholder é estático. A paridade de placeholders EN↔PT é
 * travada pelo gerador/gate. Artefato pensado para ser gerado/validado contra
 * os pontos de emissão reais (medir → catalogar → localizar → gate).
 */
public final class PortuKofDiagnostics {

    private PortuKofDiagnostics() {}

    /** código canônico -> template PT-BR (índices {0}..{n} = args estruturados). */
    private static final Map<String, String> PT = build();

    public static Map<String, String> ptTemplates() {
        return PT;
    }

    /**
     * Renderiza a mensagem humana PT para `code` se a superfície do arquivo é
     * `.ptkf` e existe template; caso contrário devolve `null` (o chamador usa a
     * mensagem canônica em inglês). A decisão de superfície é a EXTENSÃO do
     * arquivo via {@link LanguageProfile}, nunca o conteúdo da mensagem.
     */
    public static String localize(String code, String file, List<Object> args) {
        if (code == null || code.isEmpty()) return null;
        if (LanguageProfile.forFileName(file) != LanguageProfile.PORTUKOF) return null;
        String tpl = PT.get(code);
        if (tpl == null) return null;
        return render(tpl, args);
    }

    /** Substituição posicional `{i}` -> args[i]; sem regex, sem tocar conteúdo. */
    static String render(String template, List<Object> args) {
        if (args == null || args.isEmpty()) return template;
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < template.length()) {
            char c = template.charAt(i);
            if (c == '{') {
                int end = template.indexOf('}', i + 1);
                if (end > i) {
                    String idx = template.substring(i + 1, end);
                    if (idx.chars().allMatch(Character::isDigit)) {
                        int a = Integer.parseInt(idx);
                        out.append(a < args.size() ? String.valueOf(args.get(a)) : "");
                        i = end + 1;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static Map<String, String> build() {
        var m = new java.util.LinkedHashMap<String, String>();
        // ---- LEX (léxico) ----
        m.put("LEX001", "comentário de bloco não terminado");
        m.put("LEX002", "literal de string não terminada");
        m.put("LEX003", "literal de caractere vazio");
        m.put("LEX004", "literal de caractere não terminado");
        m.put("LEX005", "caractere inesperado: '{0}'");
        m.put("LEX006", "escape unicode incompleto (esperado \\uXXXX)");
        m.put("LEX007", "escape unicode inválido: \\u{0}");
        m.put("LEX008", "Kof não tem literal de string triplamente aspas (raw/multilinha): "
                + "use \"...\" com \\n (também sem interpolação — concatenate com +)");
        return java.util.Collections.unmodifiableMap(m);
    }
}
