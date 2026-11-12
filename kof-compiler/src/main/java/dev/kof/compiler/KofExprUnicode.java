package dev.kof.compiler;

/**
 * Kof.expr fatia 3 (`D-KOF-EXPR`) — a TABELA DE CLASSES pinada (§10 do plano:
 * "definidas por uma tabela de categoria Unicode explícita embarcada com a
 * biblioteca, documentada e VERSIONADA, nunca pelo backend").
 *
 * <p><b>Unicode 16.0.0</b>, medido neste JDK: {@code U+1C89} (CYRILLIC CAPITAL
 * LETTER TJE) é LETRA só desde 16.0, {@code U+1E030} (MODIFIER LETTER
 * CYRILLIC) desde 15.0, {@code U+10D40} (GARAY DIGIT) só desde 16.0 — os três
 * são asserção de teste, não documentação. A versão é registrada em
 * {@code docs/backend-parity.md} com essa medição.
 *
 * <p>Uma fonte ÚNICA para os dois lados: este arquivo é a especificação, e o
 * fragmento de runtime ({@link #JVM_SOURCE}) a materializa como predicados sobre
 * o PONTO DE CÓDIGO — nunca dobrada no programa emitido (uma tabela de 677
 * intervalos por programa violaria o orçamento do artefato hello). O matcher
 * emitido lê o ponto de código (com recomposição de par surrogado) e pergunta à
 * função de runtime.
 *
 * <p>Semântica por classe (alinhada ao {@code java.lang.Character} de Unicode
 * 16 no JDK 25, que é a autoridade da versão pinada):
 * <ul>
 *   <li>{@code letter} — General Category L* ({@code Character.isLetter});</li>
 *   <li>{@code digit} — General Category Nd ({@code Character.isDigit});</li>
 *   <li>{@code whitespace} — {@code Character.isWhitespace} (o conjunto de
 *       separadores de espaço do Java, documentado);</li>
 *   <li>{@code word} — letra, dígito ou {@code _} (§3.1 do plano);</li>
 *   <li>{@code hex} — {@code [0-9A-Fa-f]}, ASCII por definição (§10);</li>
 *   <li>{@code any} — exatamente um valor escalar Unicode, sem tabela.</li>
 * </ul>
 */
public final class KofExprUnicode {

    private KofExprUnicode() {}

    /** A versão Unicode da tabela pinada — a asserção de teste usa este valor. */
    public static final String VERSION = "16.0.0";

    /** Nomes das classes que o lowering aceita (as demais são EXPR001). */
    public static final java.util.Set<String> CLASSES = java.util.Set.of(
            "letter", "digit", "whitespace", "word", "hex", "any");

    /** A função de runtime de uma classe (null para `any`, que não precisa de tabela). */
    public static String predicateOf(String kind) {
        return switch (kind) {
            case "letter" -> "kof_expr_is_letter";
            case "digit" -> "kof_expr_is_digit";
            case "whitespace" -> "kof_expr_is_space";
            case "word" -> "kof_expr_is_word";
            case "hex" -> "kof_expr_is_hex";
            default -> null;
        };
    }

    /**
     * O fragmento de runtime: predicados sobre o PONTO DE CÓDIGO (Int), escritos
     * para a classe gerada {@code dev.kof.runtime.KofRuntime}. É a MESMA
     * definição que o interpretador alcança por reflexão.
     */
    public static final String JVM_SOURCE = """

            // ── Kof.expr character classes (D-KOF-EXPR slice 3) — Unicode 16.0.0
            // As classes são perguntas sobre o PONTO DE CÓDIGO (o modelo de
            // sujeito do §10), nunca sobre a unidade UTF-16: um caractere fora
            // do BMP é UM valor escalar. A tabela é a do JDK (pinada em 16.0.0
            // por medição) e vive SÓ aqui — o programa emitido chama.
            public static boolean kof_expr_is_letter(int cp) {
                return Character.isLetter(cp);
            }

            public static boolean kof_expr_is_digit(int cp) {
                return Character.isDigit(cp);
            }

            public static boolean kof_expr_is_space(int cp) {
                return Character.isWhitespace(cp);
            }

            public static boolean kof_expr_is_word(int cp) {
                return Character.isLetterOrDigit(cp) || cp == '_';
            }

            public static boolean kof_expr_is_hex(int cp) {
                return (cp >= '0' && cp <= '9') || (cp >= 'a' && cp <= 'f') || (cp >= 'A' && cp <= 'F');
            }
            """;
}