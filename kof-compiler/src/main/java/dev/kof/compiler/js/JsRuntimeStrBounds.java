package dev.kof.compiler.js;

/** §730: `String.substring`/`charAt` FORA de faixa no backend JS.
 *
 *  <p>O JS delegava ao `String.prototype` do HOST, que por definição CLAMPA
 *  (`"abc".substring(9)` → `""`) e TROCA os argumentos de `substring`
 *  (`"abc".substring(2, 1)` → `"b"`), além de `charCodeAt` fora de faixa
 *  devolver `NaN` (→ string vazia). O JVM/Script LANÇAM a mensagem nomeada
 *  (`Range [..) out of bounds for length N` / `Index i out of bounds for
 *  length N`) e um `try`/`catch (String e)` do Kof a recupera — esse é o
 *  contrato (a variante SILENCIOSA é a pior classe, regra 5).
 *
 *  <p>Estes helpers fazem o range check e LANÇAM a MESMA string do JVM. O
 *  IR do JS não tem nó de EXPRESSÃO condicional, então a guarda vive num
 *  helper e num bloco PRÓPRIO (seeded só quando a face é emitida) — a mesma
 *  forma de {@link JsRuntimeDivZero} e {@link JsRuntimeCastFail}.
 */
public final class JsRuntimeStrBounds {

    private JsRuntimeStrBounds() {
    }

    static String STR_BOUNDS_RUNTIME = """
            // §730: substring/charAt fora de faixa — o host CLAMPA e troca os
            // args do substring; o JVM/Script lançam a mensagem nomeada, que o
            // `catch (String e)` do Kof recupera. Mesma string do JVM.
            export function kofStrCharAt(s, i) {
                if (i < 0 || i >= s.length) throw "Index " + i + " out of bounds for length " + s.length;
                return s.charCodeAt(i);
            }
            export function kofStrSubstring(s, start, end) {
                if (end === undefined) end = s.length;
                if (start < 0 || start > end || end > s.length) throw "Range [" + start + ", " + end + ") out of bounds for length " + s.length;
                return s.substring(start, end);
            }
            // §738: `replace` com agulha VAZIA — a baixa `split(from).join(to)`
            // não gera elemento de BORDA (o `split("")` do JS não tem as pontas),
            // e o JVM/Script inserem `to` em TODA posição, inclusive nas pontas
            // (`"aXbXc".replace("", "-")` = `-a-X-b-X-c-`; `"".replace("", "-")`
            // = `-`). O ramo não-vazio é byte-idêntico à baixa anterior.
            // §739: o `String.prototype.trim` do host remove o conjunto Unicode
            // WhiteSpace+LineTerminator (NBSP, U+2000...) e NAO remove os
            // controles C0; o `String.trim()` do JVM tira TODO char <= ' '.
            export function kofStrTrim(s) {
                var a = 0, b = s.length;
                while (a < b && s.charCodeAt(a) <= 32) a++;
                while (b > a && s.charCodeAt(b - 1) <= 32) b--;
                return s.substring(a, b);
            }
            export function kofStrReplace(s, from, to) {
                if (from.length !== 0) return s.split(from).join(to);
                if (s.length === 0) return to;
                return to + s.split("").join(to) + to;
            }

            """;
}
