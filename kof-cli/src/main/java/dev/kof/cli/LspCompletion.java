package dev.kof.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * F7.2 (D-PORTUKOF): construção dos itens de completion do LSP, ciente do
 * perfil de superfície (Kof vs PortuKof). Extraído de {@link LspServer}
 * (REFACTOR-500: a classe cruzou 600 linhas). A fonte é sempre a tabela única
 * ({@code SurfaceNames}/{@code PortuKofVocabulary}) — nunca uma lista paralela.
 */
final class LspCompletion {

    private LspCompletion() {}

    /** Itens de completion para a posição {@code off} no buffer. */
    static List<Object> items(dev.kof.compiler.lang.LanguageProfile profile, String text, int off) {
        boolean member = off > 0 && text.charAt(off - 1) == '.';
        List<Object> items = new ArrayList<>();
        String detail = profile == dev.kof.compiler.lang.LanguageProfile.KOF ? "Kof" : "PortuKof";
        java.util.function.BiConsumer<String, String> add = (label, kind) -> {
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("label", label);
            it.put("kind", kind);
            it.put("detail", detail);
            items.add(it);
        };
        java.util.Set<String> seen = new java.util.HashSet<>();
        if (!member) {
            for (String[] k : LspHover.KEYWORDS) {
                String label = LspHover.surfaceWord(profile, k[0]);
                if (seen.add(label)) add.accept(label, "Keyword");
            }
            for (String ty : LspHover.BUILTIN_TYPES) {
                String label = dev.kof.compiler.lang.SurfaceNames.keyword(
                        profile, LspHover.lower(ty));
                if (seen.add(label)) add.accept(label, "Type");
            }
            if (profile != dev.kof.compiler.lang.LanguageProfile.KOF) {
                // F7.2: o domínio FECHADO da superfície PT — cada builtin do
                // catálogo e a entrada do programa. Mesma fonte dos testes
                // U1/U2 (PortuKofVocabulary); nada de lista paralela.
                for (String canon : dev.kof.compiler.lang.PortuKofVocabulary.builtins().keySet()) {
                    String label = dev.kof.compiler.lang.SurfaceNames.builtin(profile, canon);
                    if (seen.add(label)) add.accept(label, "Keyword");
                }
                // D-PORTUKOF-SUGAR (08/10): `diga`/`diz` — mesmos símbolos,
                // grafias extras da tabela oficial (nunca lista paralela).
                for (String label : dev.kof.compiler.lang.PortuKofVocabulary.builtinsSugar().values()) {
                    if (seen.add(label)) add.accept(label, "Keyword");
                }
                String entry = dev.kof.compiler.lang.SurfaceNames.symbol(profile, "main");
                if (seen.add(entry)) add.accept(entry, "Keyword");
            }
        }
        for (String ln : text.split("\n")) {
            String t = ln.strip();
            if ((t.startsWith("var ") || t.startsWith("val ")) && t.contains("=")) {
                String rest = t.substring(4).strip();
                String name = rest.split("[\\s:=]")[0];
                if (!name.isEmpty() && seen.add(name)) add.accept(name, "Variable");
            }
        }
        // X10 fatia 1: completion domain-aware — membros reais do typer
        // (StdCatalog) quando o prefixo antes do '.' é um namespace stdlib.
        // F7.2: a palavra antes do '.' é CANONICALIZADA pelo perfil (o
        // símbolo interno é sempre o canônico; a etiqueta é a superfície).
        if (member) {
            String ns = namespaceBefore(profile, text, off - 1);
            if (ns != null) {
                for (String fn : dev.kof.compiler.StdCatalog.membersOf(ns)) {
                    Map<String, Object> it = new LinkedHashMap<>();
                    it.put("label", dev.kof.compiler.lang.SurfaceNames.member(profile, ns, fn));
                    it.put("kind", "Function");
                    it.put("detail", "kof." + ns);
                    items.add(it);
                }
            } else {
                // F7.2 §8: receiver de variável local → métodos da categoria
                // U3 (mesma tabela do splicer; receiver determina a semântica
                // — aliases são exibidos, o canônico fica no detail).
                String recv = LspServer.identifierBefore(text, off - 1);
                String category = LspHover.receiverCategory(profile, text, recv);
                if (category != null) {
                    java.util.Map<String, java.util.List<String>> byName =
                            dev.kof.compiler.lang.SurfaceNames.methodSurfaceNames(profile, category);
                    java.util.Set<String> emitted = new java.util.HashSet<>();
                    for (var e : byName.entrySet()) {
                        for (String surface : e.getValue()) {
                            if (!emitted.add(surface)) continue;
                            Map<String, Object> it = new LinkedHashMap<>();
                            it.put("label", surface);
                            it.put("kind", "Method");
                            it.put("detail", e.getKey());
                            items.add(it);
                        }
                    }
                }
            }
        }
        return items;
    }

    /** Identificador antes da posição do '.', se for namespace stdlib (X10). */
    private static String namespaceBefore(dev.kof.compiler.lang.LanguageProfile p,
                                          String text, int dotIndex) {
        String w = LspServer.identifierBefore(text, dotIndex);
        if (w.isEmpty()) return null;
        String canon = dev.kof.compiler.lang.SurfaceNames.canonicalNamespace(p, w);
        return dev.kof.compiler.StdCatalog.isNamespace(canon) ? canon : null;
    }
}
