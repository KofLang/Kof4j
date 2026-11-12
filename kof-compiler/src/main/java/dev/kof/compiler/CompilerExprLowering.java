package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Kof.expr fatia 1 (`D-KOF-EXPR`) — o lowering da DECLARAÇÃO de padrão para
 * uma CLASSE real com as duas operações da fatia:
 *
 * <pre>{@code
 * expr greeting { "hello" " " "world" }
 * }</pre>
 *
 * <p>vira a classe {@code greeting} com os estáticos {@code matches(String):
 * Bool} (casamento do SUJEITO INTEIRO) e {@code match(String): String?} (o
 * sujeito casado, ou null). Estratégia P (§8 do plano): o corpo emite o MESMO
 * IR que as chamadas String de usuário emitem — {@code startsWith(prefix,
 * from)} / {@code length()} INSTANCE e {@code kof_string_equals} FUNCTION —
 * zero op de backend novo, então JVM e Script executam a MESMA forma compilada
 * (o oráculo é byte-idêntico; JS/Native entram nas fatias 6/7 sobre o mesmo
 * IR).
 *
 * <p>O matcher da fatia rastreia POSIÇÃO: cada unidade literal avança {@code
 * pos} quando casa e salta para FALHA quando não casa; o final exige {@code
 * pos == length(s)} (sujeito inteiro). As âncoras {@code start}/{@code end}
 * não emitem nada aqui: a SEM113 já as prende à primeira/última posição e o
 * contrato de sujeito inteiro as satisfaz por construção — com quantificadores
 * (fatia 2) elas passam a ser emissão real, sem mudar a superfície.
 *
 * <p>Tudo ALÉM do conjunto da fatia recusa no compile com EXPR001 nomeando a
 * própria fatia do plano — nunca fallback silencioso (R6): um padrão que não
 * pode ser casado de verdade não vira classe que casa outra coisa.
 */
final class CompilerExprLowering {

    private CompilerExprLowering() {}

    /** @return a classe do padrão, ou null quando o corpo recusou (EXPR001 emitido). */
    static IRClass lower(CompilerDriver driver, ExprDeclarationNode ex, String packageName, int typeId) {
        String internalName = driver.toInternalName(packageName, ex.name());
        Type self = new Type.ClassType(packageName, ex.name(), List.of());
        int access = driver.computeAccess(ex.modifiers()) | AccessFlags.FINAL;

        IRMethod matches = matchesMethod(driver, self, ex);
        if (matches == null) return null;
        IRMethod match = matchMethod(driver, self, ex);
        if (match == null) return null;

        List<IRMethod> methods = new ArrayList<>();
        methods.add(matches);
        methods.add(match);
        return new IRClass(internalName, "java/lang/Object", List.of(), access,
                List.of(), methods, List.of(), null, typeId,
                CompilerAnnotations.lowerAnnotations(driver, ex.annotations()));
    }

    /**
     * {@code matches(String s): Bool} — caminha as unidades, depois exige
     * {@code pos == length(s)}.
     */
    private static IRMethod matchesMethod(CompilerDriver driver, Type self, ExprDeclarationNode ex) {
        LabelId fail = LabelId.create();
        LabelId pass = LabelId.create();
        List<KofOperation> ops = new ArrayList<>();
        ops.add(KofLoadLiteral.ofInt(0));
        ops.add(new KofStoreLocal(Type.PrimitiveType.INT, 1));
        if (!emitUnits(driver, ex, ex.body(), ops, fail, self)) return null;
        // sujeito inteiro: pos == length(s)
        ops.add(new KofLoadLocal(Type.PrimitiveType.INT, 1));
        ops.add(new KofLoadLocal(BuiltinTypes.STRING, 0));
        ops.add(new KofCall(BuiltinTypes.STRING, "length", List.of(),
                Type.PrimitiveType.INT, KofCallKind.INSTANCE));
        ops.add(new KofConditionalJump(KofComparison.EQ, pass, fail));
        ops.add(new KofLabel(pass));
        ops.add(KofLoadLiteral.ofBool(true));
        ops.add(new KofReturn(Type.PrimitiveType.BOOL));
        ops.add(new KofLabel(fail));
        ops.add(KofLoadLiteral.ofBool(false));
        ops.add(new KofReturn(Type.PrimitiveType.BOOL));
        return new IRMethod("matches", Type.PrimitiveType.BOOL, List.of(BuiltinTypes.STRING),
                AccessFlags.PUBLIC | AccessFlags.STATIC, List.of(),
                List.of(new IRBasicBlock(0, ops)),
                List.of(new IRLocalVariable(0, "s", BuiltinTypes.STRING),
                        new IRLocalVariable(1, "pos", Type.PrimitiveType.INT)));
    }

    /** {@code match(String s): String?} — o sujeito quando casa, null quando não. */
    private static IRMethod matchMethod(CompilerDriver driver, Type self, ExprDeclarationNode ex) {
        LabelId absent = LabelId.create();
        LabelId present = LabelId.create();
        List<KofOperation> ops = new ArrayList<>();
        ops.add(new KofLoadLocal(BuiltinTypes.STRING, 0));
        ops.add(new KofCall(self, "matches", List.of(BuiltinTypes.STRING),
                Type.PrimitiveType.BOOL, KofCallKind.STATIC));
        ops.add(KofLoadLiteral.ofInt(0));
        ops.add(new KofConditionalJump(KofComparison.EQ, absent, present));
        ops.add(new KofLabel(present));
        ops.add(new KofLoadLocal(BuiltinTypes.STRING, 0));
        ops.add(new KofReturn(BuiltinTypes.STRING));
        ops.add(new KofLabel(absent));
        ops.add(KofLoadLiteral.ofNull());
        ops.add(new KofReturn(BuiltinTypes.STRING));
        return new IRMethod("match", new Type.NullableType(BuiltinTypes.STRING),
                List.of(BuiltinTypes.STRING),
                AccessFlags.PUBLIC | AccessFlags.STATIC, List.of(),
                List.of(new IRBasicBlock(0, ops)),
                List.of(new IRLocalVariable(0, "s", BuiltinTypes.STRING)));
    }

    /**
     * Emite as unidades na ordem; @return false (sem emitir) quando alguma
     * unidade está fora do conjunto da fatia — EXPR001 com a fatia do plano.
     */
    private static boolean emitUnits(CompilerDriver driver, ExprDeclarationNode owner,
                                     ExprNode node, List<KofOperation> ops, LabelId fail, Type self) {
        if (node instanceof ExprSequenceNode seq) {
            for (ExprNode unit : seq.units()) {
                if (!emitUnits(driver, owner, unit, ops, fail, self)) return false;
            }
            return true;
        }
        if (node instanceof ExprAtomNode atom) {
            return emitAtom(driver, owner, atom, ops, fail);
        }
        if (node instanceof ExprQuantifierNode q) {
            return refuse(driver, q.position(), "'" + q.kind()
                    + "' (a quantifier) is not part of Kof.expr slice 1 (atoms + literal + "
                    + "sequence + match/matches): quantifiers enter with slice 2");
        }
        if (node instanceof ExprAlternationNode a) {
            return refuse(driver, a.position(), "'either' (alternation) is not part of Kof.expr "
                    + "slice 1 (atoms + literal + sequence + match/matches): 'either' enters with slice 4");
        }
        if (node instanceof ExprCaptureNode c) {
            return refuse(driver, c.position(), "capture('" + c.name()
                    + "') is not part of Kof.expr slice 1 (atoms + literal + sequence + "
                    + "match/matches): captures enter with slice 5");
        }
        if (node instanceof ExprReferenceNode r) {
            return refuse(driver, r.position(), "pattern reference '" + r.alias()
                    + "' is not part of Kof.expr slice 1 (atoms + literal + sequence + "
                    + "match/matches): composition references enter with a later slice");
        }
        return refuse(driver, owner.position(), "unsupported pattern element in slice 1");
    }

    private static boolean emitAtom(CompilerDriver driver, ExprDeclarationNode owner,
                                    ExprAtomNode atom, List<KofOperation> ops, LabelId fail) {
        switch (atom.kind()) {
            case "start", "end" -> {
                // SEM113 prende start à primeira e end à última posição, e o
                // contrato da fatia é o SUJEITO INTEIRO: start só vê pos==0 e
                // end é subsumido pela exigência final pos==length(s). Com
                // quantificadores (fatia 2) passam a emissão real.
                return true;
            }
            case "literal" -> {
                String lit = atom.values().isEmpty() ? "" : atom.values().get(0);
                int len = lit.length();
                LabelId next = LabelId.create();
                // s.startsWith(lit, pos): casa o trecho NA POSIÇÃO — sem cópia.
                ops.add(new KofLoadLocal(BuiltinTypes.STRING, 0));
                ops.add(KofLoadLiteral.ofString(lit));
                ops.add(new KofLoadLocal(Type.PrimitiveType.INT, 1));
                ops.add(new KofCall(BuiltinTypes.STRING, "startsWith",
                        List.of(BuiltinTypes.STRING, Type.PrimitiveType.INT),
                        Type.PrimitiveType.BOOL, KofCallKind.INSTANCE));
                ops.add(KofLoadLiteral.ofInt(0));
                ops.add(new KofConditionalJump(KofComparison.EQ, fail, next));
                ops.add(new KofLabel(next));
                // pos += len (o literal "" avança zero — a fatia testa isso).
                ops.add(new KofLoadLocal(Type.PrimitiveType.INT, 1));
                ops.add(KofLoadLiteral.ofInt(len));
                ops.add(new KofBinary(KofBinaryOp.ADD, Type.PrimitiveType.INT));
                ops.add(new KofStoreLocal(Type.PrimitiveType.INT, 1));
                return true;
            }
            default -> {
                return refuse(driver, atom.position(), "'" + atom.kind()
                        + "' (a character class) is not part of Kof.expr slice 1 (atoms + literal + "
                        + "sequence + match/matches): the character classes enter with slice 3");
            }
        }
    }

    private static boolean refuse(CompilerDriver driver, SourcePosition p, String message) {
        if (driver.currentDiagnostics != null) {
            driver.currentDiagnostics.error(p != null ? p.file() : "",
                    p != null ? p.line() : 0, p != null ? p.column() : 0,
                    p != null ? p.length() : 0, message + " (EXPR001)", "EXPR001");
        }
        return false;
    }
}
