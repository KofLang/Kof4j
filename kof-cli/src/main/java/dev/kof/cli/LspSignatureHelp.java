package dev.kof.cli;

import dev.kof.compiler.StdCatalog;
import dev.kof.compiler.lang.LanguageProfile;
import dev.kof.compiler.lang.SurfaceNames;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LSP-A (plano universal 8.3, 19/09): {@code textDocument/signatureHelp}
 * consumindo a tabela {@code SIGNATURES} do StdCatalog — a mesma fonte unica
 * do hover, travada em {@code StdCatalogSignaturesTest}. Membro sem tabela
 * nao ganha chute (R6): o servidor responde null e o cliente nao mostra nada.
 *
 * <p>F7.2 (07/10): a ASSINATURA continua a canônica (mesma tabela, mesmos
 * parametros — sem traduçao de nomes de parametro sem catalogo), mas o NOME
 * exibido e a grafia de superficie do perfil. Em Kof o resultado e
 * byte-a-byte o historico.
 */
public final class LspSignatureHelp {

    private LspSignatureHelp() { }

    public static Map<String, Object> helpFor(String text, int offset) {
        return helpFor(LanguageProfile.KOF, text, offset);
    }

    /**
     * Resultado no formato LSP (signatures/activeSignature/activeParameter)
     * ou {@code null} quando o cursor nao esta dentro de uma chamada de
     * membro stdlib com tabela.
     */
    public static Map<String, Object> helpFor(LanguageProfile p, String text, int offset) {
        if (text == null || text.isEmpty() || offset <= 0 || offset > text.length()) {
            return null;
        }
        int start = 0;
        for (int i = Math.min(offset, text.length()) - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == ';' || c == '{' || c == '}') {
                start = i + 1;
                break;
            }
        }
        List<Integer> opens = new ArrayList<>();
        boolean inStr = false;
        for (int i = start; i < offset; i++) {
            char c = text.charAt(i);
            if (inStr) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inStr = false;
                }
                continue;
            }
            if (c == '"') {
                inStr = true;
            } else if (c == '(') {
                opens.add(i);
            } else if (c == ')' && !opens.isEmpty()) {
                opens.remove(opens.size() - 1);
            }
        }
        if (opens.isEmpty()) {
            return null;
        }
        int open = opens.get(opens.size() - 1);
        String[] nsMember = resolveMember(p, text, open);
        if (nsMember == null) {
            return null;
        }
        List<String> forms = StdCatalog.signaturesOf(nsMember[0], nsMember[1]);
        if (forms.isEmpty()) {
            return null;
        }
        int active = 0;
        int depth = 0;
        boolean str = false;
        for (int i = open + 1; i < offset; i++) {
            char c = text.charAt(i);
            if (str) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    str = false;
                }
                continue;
            }
            if (c == '"') {
                str = true;
            } else if (c == '(' || c == '[') {
                depth++;
            } else if (c == ')' || c == ']') {
                depth--;
            } else if (c == ',' && depth == 0) {
                active++;
            }
        }
        List<Map<String, Object>> sigs = new ArrayList<>();
        int widest = 0;
        String surfaceMember = SurfaceNames.member(p, nsMember[0], nsMember[1]);
        for (String form : forms) {
            sigs.add(toSignature(renderForm(p, surfaceMember, form)));
            widest = Math.max(widest, paramLabels(form).size());
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("signatures", sigs);
        res.put("activeSignature", 0L);
        res.put("activeParameter", (long) (widest == 0 ? 0 : Math.min(active, widest - 1)));
        return res;
    }

    /** `ns.member(` no contexto exato do catalogo; membro solto so quando
     *  unico namespace da tabela o contem (ambiguo => null, nunca chute).
     *  F7.2: o identificador-fonte e CANONICALIZADO pelo perfil — a chave da
     *  tabela e SEMPRE o simbolo canônico (a superficie so aparece no label). */
    private static String[] resolveMember(LanguageProfile p, String text, int open) {
        int j = open;
        while (j > 0 && (Character.isLetterOrDigit(text.charAt(j - 1)) || text.charAt(j - 1) == '_')) {
            j--;
        }
        String surfaceMember = text.substring(j, open);
        if (surfaceMember.isEmpty()) {
            return null;
        }
        if (j > 0 && text.charAt(j - 1) == '.') {
            int nsi = j - 1;
            while (nsi > 0 && (Character.isLetterOrDigit(text.charAt(nsi - 1)) || text.charAt(nsi - 1) == '_')) {
                nsi--;
            }
            String ns = SurfaceNames.canonicalNamespace(p, text.substring(nsi, j - 1));
            String member = SurfaceNames.canonicalMember(p, ns, surfaceMember);
            return StdCatalog.isNamespace(ns) ? new String[] { ns, member } : null;
        }
        String member = surfaceMember;
        if (p != LanguageProfile.KOF) {
            // builtin de chamada nua (`escreva(` → `print(`) — dominio fechado
            // do catalogo; sem alias oficial o nome permanece (simbolo de usuario).
            member = p.symbolAliases().getOrDefault(surfaceMember, surfaceMember);
        }
        String found = null;
        int hits = 0;
        for (String ns : StdCatalog.namespaces()) {
            if (!StdCatalog.signaturesOf(ns, member).isEmpty()) {
                found = ns;
                hits++;
            }
        }
        return hits == 1 ? new String[] { found, member } : null;
    }

    /**
     * F7.2: so o NOME da frente vira superficie; a lista de parametros e o
     * tipo de retorno continuam CANONICOS (sem catalogo de nome de parametro
     * nao se traduz nada — §13). Idempotente e byte-a-byte igual em Kof.
     */
    private static String renderForm(LanguageProfile p, String surfaceMember, String form) {
        if (p == LanguageProfile.KOF) return form;
        int paren = form.indexOf('(');
        if (paren < 0) return form;
        return surfaceMember + form.substring(paren);
    }

    private static Map<String, Object> toSignature(String form) {
        List<Map<String, Object>> params = new ArrayList<>();
        for (String p : paramLabels(form)) {
            params.add(Map.of("label", p));
        }
        Map<String, Object> sig = new LinkedHashMap<>();
        sig.put("label", form);
        sig.put("parameters", params);
        return sig;
    }

    private static List<String> paramLabels(String form) {
        int a = form.indexOf('(');
        int b = form.lastIndexOf(')');
        List<String> out = new ArrayList<>();
        if (a < 0 || b <= a) {
            return out;
        }
        String inner = form.substring(a + 1, b);
        int depth = 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '(' || c == '[') {
                depth++;
            } else if (c == ')' || c == ']') {
                depth--;
            }
            if (c == ',' && depth == 0) {
                out.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        if (!inner.isBlank()) {
            out.add(sb.toString().trim());
        }
        return out;
    }
}
