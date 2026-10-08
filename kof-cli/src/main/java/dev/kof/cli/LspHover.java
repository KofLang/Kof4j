package dev.kof.cli;

import dev.kof.compiler.lang.LanguageProfile;
import dev.kof.compiler.lang.PortuKofMethodAliases;
import dev.kof.compiler.lang.SurfaceNames;

import java.util.List;
import java.util.Map;

/**
 * Conteúdo do hover do LSP (textual, mesma família de references/definition):
 * palavras-chave Kof, tipos primitivos, variáveis locais e DOMÍNIO da stdlib
 * (namespace — membros reais do StdCatalog; membro soló no contexto exato
 * {@code ns.}). Nenhum parser roda por request.
 *
 * <p>F7.2 (07/10) — RENDERIZACAO DE SUPERFICIE: o pedido pode vir de um
 * buffer {@code .ptkf}; o simbolo interno continua CANONICO e a etiqueta
 * exibida vem de {@link SurfaceNames} (fonte unica: vocabulario/stdlib/
 * metodos ja gateados). Kof = caminho historico byte-a-byte identico (zero
 * regressao). Identificador de usuario NUNCA passa por alias — o proprio
 * nome digitado e a identidade (PARTE 5 do contrato PortuKof).
 */
final class LspHover {

    private LspHover() {}

    static final List<String[]> KEYWORDS = List.of(
            new String[]{"var", "mutable variable"}, new String[]{"val", "immutable value"},
            new String[]{"spawn", "roda tarefa em virtual thread"},
            new String[]{"await", "aguarda Handle<T> e devolve T"},
            new String[]{"enum", "conjunto fechado de constantes"},
            new String[]{"record", "immutable structure with components"},
            new String[]{"class", "classe"}, new String[]{"interface", "contrato"},
            new String[]{"switch", "selection (exhaustive over enum → SEM031)"},
            new String[]{"listOf", "cria List<T>"}, new String[]{"mapOf", "cria Map<K,V>"},
            new String[]{"setOf", "cria Set<T>"},
            new String[]{"println", "prints a line to stdout"});

    static final List<String> BUILTIN_TYPES = List.of(
            "Int", "Long", "Bool", "String", "Float", "Double");

    /** markdown do hover para {@code word}, ou null se não tem. */
    static String hoverFor(String word, String text, int caret) {
        return hoverFor(LanguageProfile.KOF, word, text, caret);
    }

    /**
     * F7.2 — hover ciente do perfil. A palavra do buffer é CANONICALIZADA
     * pelo perfil (símbolo interno sempre o canônico) e a etiqueta exibida é
     * a GRAFIA DE SUPERFÍCIE via {@link SurfaceNames}. Símbolo de usuário
     * (não está em nenhum catálogo) mantém a grafia digitada — nunca alias
     * por coincidência textual (PARTE 5).
     */
    static String hoverFor(LanguageProfile p, String word, String text, int caret) {
        String canon = canonicalOf(p, word);
        for (String[] k : KEYWORDS) {
            if (k[0].equals(canon)) return "**" + surfaceWord(p, k[0]) + "** — " + k[1];
        }
        // Contexto de MEMBRO (palavra após um '.') tem precedência sobre o
        // builtin solto: `s.tamanho()` é o MÉTODO length/size (U3), NÃO o
        // builtin `len` (que também grafa `tamanho`). §8/§11 receiver-aware.
        if (p != LanguageProfile.KOF && precededByDot(text, caret)) {
            String member = hoverStd(p, word, text, caret);
            if (member != null) return member;
        }
        if (p == LanguageProfile.KOF) {
            if (BUILTIN_TYPES.contains(word)) return "**" + word + "** — tipo primitivo Kof";
        } else {
            String builtinCanon = SurfaceNames.canonicalNamespace(p, word);
            if (BUILTIN_TYPES.contains(word) || BUILTIN_TYPES.contains(builtinCanon)) {
                return "**" + SurfaceNames.keyword(p, lower(builtinCanon)) + "** — tipo primitivo Kof";
            }
            if (isBuiltinName(canon)) {
                return "**" + SurfaceNames.builtin(p, canon) + "** \u2014 builtin Kof: " + canon;
            }
        }
        for (String ln : text.split("\n")) {
            String t = ln.strip();
            if (t.startsWith("var ") || t.startsWith("val ")) {
                String rest = t.substring(4).strip();
                if (rest.startsWith(word)) {
                    int after = rest.indexOf(word) + word.length();
                    if (after < rest.length() && ":= \t".indexOf(rest.charAt(after)) >= 0) {
                        return "**" + word + "** — local variable\n```kf\n" + ln.strip() + "\n```";
                    }
                }
            }
        }
        return hoverStd(p, word, text, caret);
    }

    /** A palavra sob o cursor vem imediatamente após um '.' (contexto de membro)? */
    private static boolean precededByDot(String text, int caret) {
        int st = Math.max(0, Math.min(caret, text.length()));
        while (st > 0 && (Character.isLetterOrDigit(text.charAt(st - 1)) || text.charAt(st - 1) == '_')) st--;
        return st > 0 && text.charAt(st - 1) == '.';
    }

    /**
     * Cadeia canônica de uma palavra-fonte no perfil: contextual → símbolo
     * (builtin/namespace/`principal`) → identidade. É A pergunta ao perfil —
     * nunca regex, nunca tabela local (a ponte única é {@link SurfaceNames}
     * + os catálogos já gateados).
     */
    static String canonicalOf(LanguageProfile p, String word) {
        if (p == LanguageProfile.KOF) return word;
        String c = p.canonicalWord(word);
        if (!c.equals(word)) return c;
        String s = p.symbolAliases().get(word);
        return s != null ? s : word;
    }

    /** Grafia de superfície de um canônico: keyword (PAIRS) → builtin → identidade. */
    static String surfaceWord(LanguageProfile p, String canonical) {
        String k = SurfaceNames.keyword(p, canonical);
        if (!k.equals(canonical)) return k;
        String b = SurfaceNames.builtin(p, canonical);
        return b;
    }

    private static boolean isBuiltinName(String canon) {
        return PortuKofVocabularyBuiltins.contains(canon);
    }

    /** Domínio fechado dos builtins canônicos — fonte: catálogo do perfil. */
    private static final class PortuKofVocabularyBuiltins {
        static boolean contains(String canon) {
            return dev.kof.compiler.lang.PortuKofVocabulary.builtins().containsKey(canon);
        }
    }

    static String lower(String s) {
        return switch (s) {
            case "Int" -> "int";
            case "Long" -> "long";
            case "Bool" -> "bool";
            case "String" -> "string";
            case "Float" -> "float";
            case "Double" -> "double";
            default -> s;
        };
    }

    /**
     * 8.3 (linha do plano universal): hover POR DOMÍNIO — namespace da stdlib
     * e membro em contexto {@code ns.} exato, ambos vindos do StdCatalog (a
     * mesma fonte do completion; nenhum catálogo paralelo). Fora do contexto
     * {@code .} um membro solto NÃO vira hover — sem contexto não há como
     * saber se é o membro ou um usuário com o mesmo nome (nunca chute, R6).
     */
    private static String hoverStd(LanguageProfile p, String word, String text, int caret) {
        String nsWord = SurfaceNames.canonicalNamespace(p, word);
        if (dev.kof.compiler.StdCatalog.isNamespace(nsWord)) {
            var ms = dev.kof.compiler.StdCatalog.membersOf(nsWord);
            StringBuilder b = new StringBuilder("**" + SurfaceNames.namespace(p, nsWord)
                    + "** \u2014 namespace `kof." + nsWord
                    + "` (" + ms.size() + " members)");
            if (!ms.isEmpty()) {
                b.append("\n```\n");
                List<String> surf = SurfaceNames.memberNames(p, nsWord);
                for (int i = 0; i < Math.min(8, surf.size()); i++) b.append(surf.get(i)).append("\n");
                if (surf.size() > 8) b.append("\u2026\n");
                b.append("```");
            }
            return b.toString();
        }
        int st = Math.max(0, Math.min(caret, text.length()));
        while (st > 0 && (Character.isLetterOrDigit(text.charAt(st - 1)) || text.charAt(st - 1) == '_')) st--;
        if (st > 0 && text.charAt(st - 1) == '.') {
            int ns0 = st - 1;
            while (ns0 > 0 && (Character.isLetterOrDigit(text.charAt(ns0 - 1)) || text.charAt(ns0 - 1) == '_')) ns0--;
            String ns = SurfaceNames.canonicalNamespace(p, text.substring(ns0, st - 1));
            String member = SurfaceNames.canonicalMember(p, ns, word);
            if (dev.kof.compiler.StdCatalog.isNamespace(ns)
                    && dev.kof.compiler.StdCatalog.membersOf(ns).contains(member)) {
                var sig = dev.kof.compiler.StdCatalog.signaturesOf(ns, member);
                String label = "**" + SurfaceNames.member(p, ns, member) + "** \u2014 member of `kof." + ns + "`";
                if (sig.isEmpty()) return label;
                StringBuilder mb = new StringBuilder(label);
                mb.append("\n```\n");
                for (String sg : sig) mb.append(renderSignature(p, ns, member, sg)).append("\n");
                mb.append("```");
                return mb.toString();
            }
        }
        return hoverMemberMethod(p, word, text, caret);
    }

    /** Assinatura canônica com o NOME na superfície (parâmetros canônicos — §13). */
    static String renderSignature(LanguageProfile p, String ns, String canonMember, String form) {
        if (p == LanguageProfile.KOF) return form;
        int paren = form.indexOf('(');
        if (paren < 0) return form;
        String surface = SurfaceNames.member(p, ns, canonMember);
        String rest = form.substring(form.lastIndexOf('.', paren >= 0 ? paren : 0) + 1);
        return surface + rest.substring(paren >= 0 ? rest.indexOf('(') : 0);
    }

    /**
     * F7.2 (07/10) — hover de METODO receiver-aware (U3) no contexto exato
     * {@code receptor.metodo}: a tabela de categorias reais decide; um alias
     * cobre N canonicos (ex.: {@code tamanho} = length E size), entao sem o
     * tipo do receptor (que o LSP textual nao tem) o hover honesto e a lista
     * de identidades canonicas — NUNCA uma escolhida por chute (R6). Fora do
     * contexto {@code .} um nome solto nao ganha hover (pode ser simbolo do
     * usuario — PARTE 5).
     */
    private static String hoverMemberMethod(LanguageProfile p, String word, String text, int caret) {
        if (p == LanguageProfile.KOF) return null;
        int st = Math.max(0, Math.min(caret, text.length()));
        while (st > 0 && (Character.isLetterOrDigit(text.charAt(st - 1)) || text.charAt(st - 1) == '_')) st--;
        if (st == 0 || text.charAt(st - 1) != '.') return null;
        int r0 = st - 1;
        while (r0 > 0 && (Character.isLetterOrDigit(text.charAt(r0 - 1)) || text.charAt(r0 - 1) == '_')) r0--;
        String receiver = text.substring(r0, st - 1);
        String category = receiverCategory(p, text, receiver);
        if (category == null) return null;
        var hits = new java.util.LinkedHashSet<String>();
        for (Map<String, String> cat : PortuKofMethodAliases.aliasByCategory().values()) {
            for (Map.Entry<String, String> a : cat.entrySet()) {
                if (a.getKey().equals(word)) hits.add(a.getValue());
            }
        }
        if (hits.isEmpty()) return null;
        StringBuilder b = new StringBuilder("**" + word + "** \u2014 m\u00e9todo (receiver `" + category
                + "`): " + String.join(", ", hits));
        b.append("\n```\n");
        for (String h : hits) b.append(h).append("\n");
        b.append("```");
        return b.toString();
    }

    /**
     * Categoria U3 do receptor por varredura LOCAL conservadora (mesma
     * familia textual do tooling): literal de texto, construtor de colecao
     * conhecido ou alias de tipo na declaracao. Nada chutado: desconhecido
     * = null (sem hover). Namespace stdlib nao e receptor de metodo.
     */
    static String receiverCategory(LanguageProfile p, String text, String receiver) {
        if (receiver.isEmpty() || dev.kof.compiler.StdCatalog.isNamespace(
                SurfaceNames.canonicalNamespace(p, receiver))) return null;
        for (String ln : text.split("\n")) {
            String t = ln.strip();
            String[] tk = t.split(" +");
            if (tk.length < 3) continue;
            int eq = -1;
            for (int i = 0; i < tk.length; i++) if (tk[i].equals("=")) { eq = i; break; }
            if (eq < 1) continue;
            boolean kw = tk[0].equals("var") || tk[0].equals("val");
            String name = kw ? tk[1] : tk[0];
            String type = kw ? null : tk[0];
            if (eq + 1 >= tk.length || !name.equals(receiver)) continue;
            String init = tk[eq + 1];
            if (init.startsWith("\"")) return "STRING";
            if (init.startsWith("[")) return "ARRAY";
            if (init.startsWith("listaDe(") || init.startsWith("listOf(")) return "LIST";
            if (init.startsWith("mapaDe(") || init.startsWith("mapOf(")) return "MAP";
            if (init.startsWith("conjuntoDe(") || init.startsWith("setOf(")) return "SET";
            if (type != null) {
                String canon = SurfaceNames.canonicalOf(p, type);
                return switch (canon) {
                    case "string" -> "STRING";
                    case "int", "long", "double", "float", "bool", "char", "byte", "short" -> "PRIMITIVE";
                    default -> null;
                };
            }
            return null;
        }
        return null;
    }
}
