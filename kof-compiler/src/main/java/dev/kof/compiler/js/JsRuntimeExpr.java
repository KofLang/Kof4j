package dev.kof.compiler.js;

import dev.kof.compiler.KofExprUnicode;

/**
 * Kof.expr fatia 7 (`D-KOF-EXPR`) — o fragmento de RUNTIME JS das classes de
 * caractere: os predicados como funções top-level do kof-runtime.mjs, sobre o
 * PONTO DE CÓDIGO (o mesmo modelo de sujeito do §10 do plano).
 *
 * <p>O JS não tem {@code Character.isLetter} — a TABELA é DADO: os intervalos
 * pinados do Unicode 16.0.0 (medidos neste JDK e commitados em
 * {@link KofExprUnicode#RANGES_LETTER}/{@code DIGIT}/{@code SPACE}) são
 * embutidos AQUI pela fonte única, com busca binária sobre os pares hex. A JVM
 * continua no {@code Character}, que MEDIU esta mesma versão — a asserção de
 * teste trava as duas na mesma tabela
 * ({@code KofExprClassesE2ETest#thePinnedUnicodeLetterIsALetterOnJsToo}).
 *
 * <p>Os predicados vêm ANTES das tabelas no texto: o seletor de slices extrai
 * unidades por declaração de topo, e as tabelas precisam ser unidades
 * FORNECEDORAS puxadas por necessidade (se fossem o preâmbulo, entrariam em
 * TODO artefato e o orçamento do hello inflaria ~7 KB).
 */
final class JsRuntimeExpr {

    private JsRuntimeExpr() {}

    private static String jsTable(String name, String ranges) {
        // "41-5a,aa,b5" → const NAME = "41-5a,aa,b5"; (o par hex viaja CRU — a
        // busca binária converte com parseInt(…,16) na leitura)
        return "const " + name + " = \"" + ranges + "\";\n";
    }

    private static String fn(String body) {
        // o corpo JÁ começa com "function": o seletor declara providers pelos
        // nomes declarados — um prefixo dobrado ("function function …")
        // viraria um provider chamado "function" e derrubaria blocos inteiros
        // no fallback always (o nome "function" aparece entre aspas em
        // vários fragmentos).
        return body.strip() + "\n";
    }

    static final String EXPR_RUNTIME = "\n"
            // ── os predicados PRIMEIRO (unidades fornecedoras); o comentário é
            // o preâmbulo "always" do bloco e não emite nada
            + "// ── Kof.expr character classes (D-KOF-EXPR slice 7): the pinned\n"
            + "// Unicode 16.0.0 ranges as DATA (GraalJS has no Character.isLetter);\n"
            + "// binary search over the hex pairs, the same table the JVM face's\n"
            + "// Character asserts (KofExprClassesE2ETest pins both on U+1C89).\n"
            + fn("""
                    function kofExprInRange(ranges, cp) {
                        let lo = 0, hi = (ranges.length / 2) | 0;
                        while (lo < hi) {
                            const mid = (lo + hi) >> 1;
                            const start = parseInt(ranges[mid * 2], 16);
                            const end = parseInt(ranges[mid * 2 + 1], 16);
                            if (cp < start) hi = mid;
                            else if (cp > end) lo = mid + 1;
                            else return true;
                        }
                        return false;
                    }
                    """)
            + fn("""
                    export function kofExprIsLetter(cp) {
                        return kofExprInRange(KOF_EXPR_LETTER, cp);
                    }
                    """)
            + fn("""
                    export function kofExprIsDigit(cp) {
                        return kofExprInRange(KOF_EXPR_DIGIT, cp);
                    }
                    """)
            + fn("""
                    export function kofExprIsSpace(cp) {
                        return kofExprInRange(KOF_EXPR_SPACE, cp);
                    }
                    """)
            + fn("""
                    export function kofExprIsWord(cp) {
                        return kofExprIsLetter(cp) || kofExprIsDigit(cp) || cp === 0x5f;
                    }
                    """)
            + fn("""
                    export function kofExprIsHex(cp) {
                        return (cp >= 0x30 && cp <= 0x39) || (cp >= 0x41 && cp <= 0x46)
                                || (cp >= 0x61 && cp <= 0x66);
                    }
                    """)
            // ── as tabelas DEPOIS: unidades fornecedoras de KOF_EXPR_*, puxadas
            // só quando um predicado é selecionado
            + jsTable("KOF_EXPR_LETTER", KofExprUnicode.RANGES_LETTER)
            + jsTable("KOF_EXPR_DIGIT", KofExprUnicode.RANGES_DIGIT)
            + jsTable("KOF_EXPR_SPACE", KofExprUnicode.RANGES_SPACE);
}