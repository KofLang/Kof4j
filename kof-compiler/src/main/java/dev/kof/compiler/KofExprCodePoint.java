package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Kof.expr fatias 2+3 (`D-KOF-EXPR`) — a LEITURA do sujeito como VALORES
 * ESCALARES Unicode (§10 do plano: o modelo de sujeito é o de string do Kof, e
 * "caractere" é um valor escalar, nunca uma unidade UTF-16).
 *
 * <p>A leitura e o avanço concordam por construção: {@link #emitReadCodePoint}
 * empilha o escalar em {@code pos} e {@link #emitAdvanceCodePoint} avança pelo
 * TAMANHO EM UNIDADES desse escalar (2 para um par surrogado, 1 caso
 * contrário). É por isso que um caractere fora do BMP é UM {@code any} e nunca
 * se parte em duas metades.
 *
 * <p>Fora do texto ({@code pos >= length}) a leitura empilha {@code -1}, que
 * nenhuma classe e nenhum literal casa — a leitura não "passa do fim" em
 * silêncio. Extraído de {@code CompilerExprLowering} pelo ratchet de 500 linhas;
 * o comportamento é o mesmo, instrução a instrução.
 */
final class KofExprCodePoint {

    /** 0 = s, 1 = pos (os mesmos slots de todo estado sintético do matcher). */
    private static final int SLOT_S = 0;
    private static final int SLOT_POS = 1;
    private static final Type STR = BuiltinTypes.STRING;
    private static final Type INT = Type.PrimitiveType.INT;
    private static final Type BOOL = Type.PrimitiveType.BOOL;

    private KofExprCodePoint() {}

    /**
     * Empilha o PONTO DE CÓDIGO em {@code pos}. {@code s.codePointAt(pos)}
     * (§827) devolve exatamente o escalar na JVM e no interpretador.
     */
    static void emitRead(List<KofOperation> ops, int tempSlot) {
        LabelId inRange = LabelId.create();
        LabelId outOfRange = LabelId.create();
        LabelId join = LabelId.create();
        ops.add(new KofLoadLocal(INT, SLOT_POS));
        ops.add(new KofLoadLocal(STR, SLOT_S));
        ops.add(new KofCall(STR, "length", List.of(), INT, KofCallKind.INSTANCE));
        ops.add(new KofConditionalJump(KofComparison.LT, INT, inRange, outOfRange));
        ops.add(new KofLabel(inRange));
        ops.add(new KofLoadLocal(STR, SLOT_S));
        ops.add(new KofLoadLocal(INT, SLOT_POS));
        ops.add(new KofCall(STR, "codePointAt", List.of(INT), INT, KofCallKind.INSTANCE));
        ops.add(new KofJump(join));
        ops.add(new KofLabel(outOfRange));
        ops.add(KofLoadLiteral.ofInt(-1));
        ops.add(new KofLabel(join));
    }

    /**
     * Avança {@code pos} pelo tamanho em unidades do escalar guardado em
     * {@code tempSlot}: 2 quando o escalar está acima de 0xFFFF (par surrogado),
     * 1 caso contrário.
     */
    static void emitAdvance(List<KofOperation> ops, int tempSlot) {
        LabelId wide = LabelId.create();
        LabelId narrow = LabelId.create();
        LabelId join = LabelId.create();
        ops.add(new KofLoadLocal(INT, SLOT_POS));
        ops.add(new KofLoadLocal(INT, tempSlot));
        ops.add(KofLoadLiteral.ofInt(0xFFFF));
        ops.add(new KofConditionalJump(KofComparison.GT, INT, wide, narrow));
        ops.add(new KofLabel(wide));
        ops.add(KofLoadLiteral.ofInt(2));
        ops.add(new KofJump(join));
        ops.add(new KofLabel(narrow));
        ops.add(KofLoadLiteral.ofInt(1));
        ops.add(new KofLabel(join));
        ops.add(new KofBinary(KofBinaryOp.ADD, INT));
        ops.add(new KofStoreLocal(INT, SLOT_POS));
    }
}
