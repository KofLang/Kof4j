package dev.kof.compiler.js;
import dev.kof.compiler.KofJump;
import dev.kof.compiler.KofLabel;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.KofReturnVoid;

import java.util.ArrayList;
import java.util.List;

/**
 * JsIfThrowElse — parse do else quando o then termina em saída incondicional
 * (§147, 12/09, #101).
 *
 * <p>O IR de if/else é linear (sem Jump/Label de end): then-throw cai direto
 * no Label(false), else linear, epílogo linear. O parse normal do else, com
 * endLabels vazio, engolia o epílogo pós-if para dentro do else — com um
 * `return` fantasma vindo do KofReturnVoid final do método (o epílogo "sumia":
 * dead code; while→hang no caso do reporter). Este parser consome o else até
 * o primeiro KofJump/KofLabel não-loop, EXCETO o trailing KofReturnVoid do
 * corpo do método (retorno implícito — epílogo real, nunca statement do else).
 *
 * <p>Extraído de JsControlFlowParser (§140, ratchet ≤500: o arquivo estourou
 * 492→564 com o fix; a responsabilidade "else-pós-throw" é separável).
 */
public final class JsIfThrowElse {

    private JsIfThrowElse() {}

    /** O último statement do then sai incondicionalmente (sem Jump de end)? */
    static boolean thenEndsUnconditional(List<JsIr.JsStatement> thenBranch) {
        if (thenBranch.isEmpty()) return false;
        JsIr.JsStatement last = thenBranch.get(thenBranch.size() - 1);
        return last instanceof JsIr.JsThrow || last instanceof JsIr.JsReturn
                || last instanceof JsIr.JsBreak || last instanceof JsIr.JsContinue;
    }

    static List<JsIr.JsStatement> parseElse(JsControlFlowParser flow, MethodCtx ctx, int[] pos) {
        List<JsIr.JsStatement> out = new ArrayList<>();
        while (pos[0] < ctx.ops.size()) {
            KofOperation op = ctx.ops.get(pos[0]);
            if (op instanceof dev.kof.compiler.KofContinueLabel) {
                // §266: fronteira corpo/update do loop — mesma regra de
                // parseStatements. O else de um `if (…){ continue }` (§147,
                // then-incondicional) engolia o marcador e caía em
                // parseStatement (COMP002). NÃO consumir: o dono (parseLoop)
                // casa o marcador + Label(continue).
                return out;
            }
            if (op instanceof KofLabel kl) {
                if (ctx.isLoopLabel(kl.label())) return out;
                // O endLabel de um try ENVOLVENTE não é o fim do else: consumi-lo
                // deixa o KofCatchStart solto no statement level (COMP002). Deixa
                // o dono (parseStatements do try) casar o endLabel.
                if (ctx.isTryEndLabel(kl.label())) return out;
                // §147 + assert: um label de INÍCIO de loop (lookahead) NÃO é o
                // end do else. No if-throw sem else (assert), o corpo seguinte
                // pertence ao else (o then sempre lança) — parar no label do
                // `while` seguinte deixava o `var` do loop fora do escopo
                // (ReferenceError). Parseia o loop DENTRO do else e continua.
                if (JsLabelParser.isLoopStart(ctx, pos, kl.label())) {
                    out.add(flow.parseLoop(ctx, pos, kl.label()));
                    continue;
                }
                // §380: o falseLabel de um `if` ENVOLVENTE ativo não é o fim
                // do else interno — consumi-lo rouba a fronteira do dono e
                // absorve o epílogo externo no then (path não-throw virava
                // undefined). Devolve sem consumir; o parseStatements do
                // envolvente casa o label (endLabels dele).
                if (ctx.isEnclosingIfFalse(kl.label())) return out;
                pos[0]++;
                return out;
            }
            if (op instanceof KofJump) {
                pos[0]++;
                return out;
            }
            if (op instanceof dev.kof.compiler.KofExcUnlink) {
                // §549/§551: pop de handler do caminho normal (ou de um desvio
                // que atravessa a região try) — o JS reconstrói a estrutura
                // try/catch/finally; nada a emitir.
                pos[0]++;
                continue;
            }
            if (op instanceof KofReturnVoid && pos[0] == ctx.ops.size() - 1) {
                return out;
            }
            out.addAll(flow.parseStatement(ctx, pos));
        }
        return out;
    }
}
