package dev.kof.compiler.lang;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * D-PORTUKOF F7.2 (07/10) — RENDERIZADOR DE SUPERFÍCIE do tooling.
 *
 * <p>Ponte única {@code CANONICAL → SURFACE}: recebe o símbolo canônico (o
 * único que o parser/semântica conhecem) e devolve a grafia humana do perfil.
 * Não existe segunda tabela: cada método delega às tabelas já gateadas
 * ({@link PortuKofVocabulary}, {@link PortuKofStdlibMembers},
 * {@link PortuKofMethodAliases}). Kof = identidade; PortuKof = alias oficial;
 * símbolo sem alias devolve o próprio canônico.
 *
 * <p>Proibido por contrato (`D-PORTUKOF` regra-ouro): regex e replacement
 * textual no tooling — o tooling SEMPRE pergunta ao perfil, nunca inventa.
 * Identificadores de usuário NUNCA passam por aqui: o canônico deles É a
 * grafia digitada (PARTE 5).
 */
public final class SurfaceNames {

    private SurfaceNames() {}

    /**
     * Nome de superfície de uma keyword/tipo/contextual canônico (class →
     * classe, in → em, constructor → construtor). A ponte cobre as DUAS
     * famílias do vocabulário — keywords léxicas (`PortuKofVocabulary.pairs()`)
     * e palavras contextuais (a MESMA tabela `CONTEXTUAL` que alimenta
     * `contextualWords` do perfil e o `wordIs` do parser; PARTE 27) — porque
     * o AST-printer precisa reimprimir as duas. Sem segunda lista. NUNCA
     * resolve builtins: um slot estrutural pode colidir com um nome de
     * usuário (`print` é IDENTIFIER válido), e mistraduzir nome é a
     * regra-ouro absoluta (PARTE 5).
     */
    public static String keyword(LanguageProfile p, String canonical) {
        if (p == LanguageProfile.KOF) return canonical;
        for (String[] pair : PortuKofVocabulary.pairs()) {
            if (pair[0].equals(canonical)) return pair[1];
        }
        String c = PortuKofVocabulary.contextualCanonToPortuguese().get(canonical);
        return c != null ? c : canonical;
    }

    /** Nome de superfície de um builtin de chamada nua (print → escreva). */
    public static String builtin(LanguageProfile p, String canonical) {
        if (p == LanguageProfile.KOF) return canonical;
        String pt = PortuKofVocabulary.builtins().get(canonical);
        return pt != null ? pt : canonical;
    }

    /** Nome de superfície de um namespace (math → matematica). */
    public static String namespace(LanguageProfile p, String canonical) {
        if (p == LanguageProfile.KOF) return canonical;
        String pt = PortuKofVocabulary.namespaces().get(canonical);
        return pt != null ? pt : canonical;
    }

    /** Namespace canônico a partir da grafia de superfície (identidade p/ Kof). */
    public static String canonicalNamespace(LanguageProfile p, String surface) {
        if (p == LanguageProfile.KOF) return surface;
        String c = PortuKofVocabulary.symbolAliases().get(surface);
        return c != null ? c : surface;
    }

    /**
     * Grafia de superfície para um SÍMBOLO do catálogo fechado (main →
     * principal): varredura inversa da MESMA tabela de aliases do perfil —
     * sem segunda lista. Sem alias oficial devolve o canônico.
     */
    public static String symbol(LanguageProfile p, String canonical) {
        if (p == LanguageProfile.KOF) return canonical;
        for (Map.Entry<String, String> e : p.symbolAliases().entrySet()) {
            if (e.getValue().equals(canonical)) return e.getKey();
        }
        return canonical;
    }

    /** Alias de superfície de um membro de namespace (sqrt → raizQuadrada). */
    public static String member(LanguageProfile p, String canonicalNs, String canonicalMember) {
        if (p == LanguageProfile.KOF) return canonicalMember;
        String[][] rows = PortuKofStdlibMembers.table().get(canonicalNs);
        if (rows != null) {
            for (String[] row : rows) {
                if (row[0].equals(canonicalMember)) return row[1];
            }
        }
        return canonicalMember;
    }

    /** Membro canônico a partir da grafia de superfície (identidade p/ Kof). */
    public static String canonicalMember(LanguageProfile p, String canonicalNs, String surfaceMember) {
        if (p == LanguageProfile.KOF) return surfaceMember;
        String c = PortuKofVocabulary.memberAliases(canonicalNs).get(surfaceMember);
        return c != null ? c : surfaceMember;
    }

    /**
     * Membros de um namespace canônico na grafia do perfil — fonte única: a
     * ordem de {@link PortuKofStdlibMembers#table()} (espelho do catálogo em
     * execução), nunca uma cópia manual.
     */
    public static List<String> memberNames(LanguageProfile p, String canonicalNs) {
        String[][] rows = PortuKofStdlibMembers.table().get(canonicalNs);
        if (rows == null) return List.of();
        List<String> out = new ArrayList<>(rows.length);
        for (String[] row : rows) out.add(p == LanguageProfile.KOF ? row[0] : row[1]);
        return out;
    }

    /**
     * Nome canônico → lista de aliases de superfície de uma categoria
     * receiver-aware (U3). Um canônico pode ter N aliases (length →
     * comprimento/tamanho); a identidade canônica continua única.
     */
    public static Map<String, List<String>> methodSurfaceNames(
            LanguageProfile p, String category) {
        Map<String, List<String>> byName = new LinkedHashMap<>();
        if (p == LanguageProfile.KOF) {
            for (String c : PortuKofMethodAliases.canonicalsByCategory()
                    .getOrDefault(category, List.of())) {
                byName.put(c, List.of(c));
            }
            return byName;
        }
        for (String canonical : PortuKofMethodAliases.canonicalsByCategory()
                .getOrDefault(category, List.of())) {
            List<String> aliases = new ArrayList<>();
            for (Map<String, String> cat : PortuKofMethodAliases.aliasByCategory().values()) {
                for (Map.Entry<String, String> a : cat.entrySet()) {
                    if (a.getValue().equals(canonical) && !aliases.contains(a.getKey())) {
                        aliases.add(a.getKey());
                    }
                }
            }
            if (aliases.isEmpty()) aliases.add(canonical);
            byName.put(canonical, aliases);
        }
        return byName;
    }

    /** Alias de superfície de um método canônico (primeiro alias ≠ canônico). */
    public static String method(LanguageProfile p, String canonical) {
        if (p == LanguageProfile.KOF) return canonical;
        for (Map<String, String> cat : PortuKofMethodAliases.aliasByCategory().values()) {
            for (Map.Entry<String, String> a : cat.entrySet()) {
                if (a.getValue().equals(canonical) && !a.getKey().equals(canonical)) {
                    return a.getKey();
                }
            }
        }
        return canonical;
    }

    /**
     * Todas as grafias de superfície de um canônico de método (references):
     * aliases PT + o próprio canônico — a identidade buscada continua a
     * canônica; a busca é a UNIÃO das grafias, nunca um chute textual.
     */
    public static List<String> methodSpellings(LanguageProfile p, String canonical) {
        List<String> out = new ArrayList<>();
        if (p != LanguageProfile.KOF) {
            for (Map<String, String> cat : PortuKofMethodAliases.aliasByCategory().values()) {
                for (Map.Entry<String, String> a : cat.entrySet()) {
                    if (a.getValue().equals(canonical) && !out.contains(a.getKey())) {
                        out.add(a.getKey());
                    }
                }
            }
        }
        out.add(canonical);
        return out;
    }

    /**
     * Cadeia canônica de uma palavra-fonte do buffer: contextual → símbolo
     * (builtin/namespace/`principal`) → keyword léxica → identidade. É a
     * pergunta AO PERFIL — a única direção superfície→canônica do tooling.
     */
    public static String canonicalOf(LanguageProfile p, String word) {
        if (p == LanguageProfile.KOF) return word;
        String c = p.canonicalWord(word);
        if (!c.equals(word)) return c;
        c = p.symbolAliases().get(word);
        if (c != null) return c;
        c = p.lexicalCanon().get(word);
        return c != null ? c : word;
    }
}
