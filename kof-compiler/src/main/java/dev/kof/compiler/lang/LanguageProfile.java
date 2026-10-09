package dev.kof.compiler.lang;

import dev.kof.compiler.TokenType;

import java.util.List;
import java.util.Map;

/**
 * D-PORTUKOF (07/10) — perfil de superfície linguística do Kof.
 *
 * <p>Um perfil NUNCA muda semântica: troca apenas a SUPERFÍCIE na entrada do
 * pipeline — tabela de keywords do `Lexer` (com valor de token canônico) e o
 * vocabulário do parser via {@link #matchesWord}. O AST produzido é sempre o
 * AST canônico do Kof; IR, type system, runtime e backends não conhecem
 * perfis (`D-PORTUKOF` contrato 2 — uma semântica, N superfícies).
 *
 * <p>Superfícies futuras (PARTE 22): novo perfil + vocabulário + aliases +
 * docs + testes — nenhum ponto abaixo do parse muda.
 */
public record LanguageProfile(String id, String locale, List<String> extensions,
                              Map<String, TokenType> keywords,
                              Map<String, String> contextualWords,
                              Map<String, String> lexicalCanon,
                              Map<String, String> symbolAliases) {

    /** Perfil canônico do Kof — extensão oficial (.kf/.kof), vocabulário inglês. */
    public static final LanguageProfile KOF = new LanguageProfile(
            "kof", "en", List.of(".kf", ".kof"),
            KofKeywords.MAP, Map.of(), Map.of(), Map.of());

    /** PortuKof — superfície pt-BR do MESMO Kof (`D-PORTUKOF`). */
    public static final LanguageProfile PORTUKOF = new LanguageProfile(
            "ptkf", "pt-BR", List.of(".ptkf"),
            PortuKofVocabulary.keywords(),
            PortuKofVocabulary.contextualPortugueseToCanon(),
            PortuKofVocabulary.lexicalCanon(),
            PortuKofVocabulary.symbolAliases());

    /**
     * Autodescoberta determinística (PARTE 9): só a extensão decide; NUNCA
     * heurística de conteúdo. Extensão desconhecida ⇒ perfil canônico (Kof),
     * o comportamento histórico do pipeline.
     */
    public static LanguageProfile forFileName(String fileName) {
        if (fileName == null) return KOF;
        String n = fileName.toLowerCase();
        if (n.endsWith(PORTUKOF.extensions().get(0))) return PORTUKOF;
        for (String ext : KOF.extensions()) {
            if (n.endsWith(ext)) return KOF;
        }
        return KOF;
    }

    /** Palavra canônica de um token IDENTIFIER do perfil (PortuKof → inglês; Kof → si). */
    public String canonicalWord(String word) {
        String c = contextualWords.get(word);
        return c != null ? c : word;
    }

    /** O identificador-fonte `word` ocupa a posição da palavra canônica `canonical`? */
    public boolean matchesWord(String word, String canonical) {
        return word.equals(canonical) || canonical.equals(contextualWords.get(word));
    }

    /** Alias canônico de um símbolo (builtin/namespace); ausente ⇒ o próprio nome. */
    public String canonicalSymbol(String name) {
        String c = symbolAliases.get(name);
        return c != null ? c : name;
    }
}
