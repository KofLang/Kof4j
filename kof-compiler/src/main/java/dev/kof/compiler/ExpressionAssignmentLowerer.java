package dev.kof.compiler;

import java.util.List;

/**
 * Lowering de AssignmentExpr (case do emitExpression).
 */
public final class ExpressionAssignmentLowerer {

    private ExpressionAssignmentLowerer() {}

    static int lower(CompilerDriver driver, AssignmentExpr ae, List<KofOperation> ops,
                        String owner, int localIdx, List<IRLocalVariable> locals) {
if (ae.target() instanceof IdentifierExpr ie && !owner.isEmpty()) {
    boolean isLocal = false;
    for (int i = locals.size() - 1; i >= 0; i--) {
        if (locals.get(i).name().equals(ie.name())) { isLocal = true; break; }
    }
    if (!isLocal) {
        String className = owner.substring(owner.lastIndexOf('/') + 1);
        SymbolTable.Symbol fieldSym = driver.semanticAnalyzer != null
                ? HierarchyResolver.resolveFieldInHierarchy(className, ie.name(), driver.semanticAnalyzer) : null;
        if (fieldSym != null
                && (fieldSym instanceof SymbolTable.FieldSymbol
                || (fieldSym instanceof SymbolTable.MethodSymbol ms
                        && ms.parameterTypes().isEmpty()))) {
            Type ownerType = CompilerTypes.ownerTypeFromInternal(owner, driver.semanticAnalyzer);
            // campo ESTÁTICO por nome simples (ex.: `count = count + 1` em
            // bump()): GETSTATIC/PUTSTATIC — sem this (LoadLocal(0) quebraria
            // método estático: aload_0 sem receiver).
            if (fieldSym instanceof SymbolTable.FieldSymbol fsStatic
                    && (fsStatic.accessFlags() & AccessFlags.STATIC) != 0) {
                String sop = ae.operator();
                boolean compound = isCompoundOp(sop);
                if (compound) {
                    ops.add(new KofGetStatic(ownerType, ie.name(), fsStatic.type()));
                }
                localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
                if (compound && "+=".equals(sop) && Type.isString(fsStatic.type())) {
                    ExpressionBinaryLowerer.emitOperandToString(driver, ops,
                            ExpressionTyper.inferExprType(driver, ae.value(), locals));
                    ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                            List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                            BuiltinTypes.STRING, KofCallKind.FUNCTION));
                } else if (compound) {
                    emitCompoundRhsConv(driver, ops, sop, fsStatic.type(),
                            ExpressionTyper.inferExprType(driver, ae.value(), locals));
                    ops.add(new KofBinary(compoundBinaryOp(sop), fsStatic.type()));
                    CompilerEmissionHelpers.emitStoreNarrow(driver, ops, Type.PrimitiveType.INT, fsStatic.type());
                }
                ops.add(new KofPutStatic(ownerType, ie.name(), fsStatic.type()));
                return localIdx;
            }
            ops.add(new KofLoadLocal(ownerType, 0));
            String op = ae.operator();
            if (isCompoundOp(op)) {
                // compound em CAMPO de instância: o getfield consome o `this`
                // e o putfield precisa dele de novo — duplica antes (bug 40:
                // stack underflow no putfield, `n += 1` em método de instância).
                ops.add(new KofDup());
                ops.add(new KofLoadField(ownerType, ie.name(), fieldSym.type()));
            }
            boolean compoundAsgn = isCompoundOp(op);
            // #194 — `s += t` em campo String de INSTÂNCIA: o caminho de
            // campo de instância não tinha o tratamento de concatenação que
            // o de campo estático já tinha (linhas ~98-126), então caía em
            // KofBinary(ADD, String) → `iadd` → VerifyError. Espelha o
            // caminho estático: box do primitivo + valueOf + kof_string_concat.
            Type instValType = ExpressionTyper.inferExprType(driver, ae.value(), locals);
            boolean instConcat = compoundAsgn && "+=".equals(op)
                    && (Type.isString(fieldSym.type()) || Type.isString(instValType));
            if (instConcat) {
                ExpressionBinaryLowerer.emitOperandToString(driver, ops, fieldSym.type());
            }
            localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
            // §103.2 (#103): widening do valor p/ o tipo do CAMPO — Int→Long
            // putfield sem I2L → VerifyError (this.value = n, campo Long,
            // param Int). O caminho estático (~158) e o de array-store já
            // faziam; o de campo de instância não fazia NEM simples NEM
            // composto (RHS Int num LADD também quebra o frame).
            if (instConcat) {
                ExpressionBinaryLowerer.emitOperandToString(driver, ops, instValType);
                ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                        List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                        BuiltinTypes.STRING, KofCallKind.FUNCTION));
            } else if (TypeMetrics.isPrimitiveType(fieldSym.type()) && compoundAsgn) {
                emitCompoundRhsConv(driver, ops, op, fieldSym.type(), instValType);
            } else if ("=".equals(op)) {
                if (TypeMetrics.isPrimitiveType(instValType)
                        && TypeMetrics.isPrimitiveType(fieldSym.type())) {
                    // #845: RHS `Int?` estreitado abre a caixa antes do widening
                    driver.emitWideningIfNeeded(ops, CompilerEmissionHelpers.emitNarrowedNullableUnbox(
                            driver, ops, instValType, fieldSym.type()), fieldSym.type());
                } else if (driver.erasesToReference(fieldSym.type())
                        && TypeMetrics.isPrimitiveType(instValType)
                        && !ExpressionTyper.boxesOwnBranches(driver, ae.value(), locals)) {
                    // Issue #181: campo de instância declarado Object recebendo primitivo (this.item = 99)
                    driver.emitErasureBox(ops, instValType);
                }
            }
            if (compoundAsgn && !instConcat) {
                ops.add(new KofBinary(compoundBinaryOp(op), fieldSym.type()));
                CompilerEmissionHelpers.emitStoreNarrow(driver, ops, Type.PrimitiveType.INT, fieldSym.type());
            }
            ops.add(new KofStoreField(ownerType, ie.name(),
                    instConcat ? BuiltinTypes.STRING : fieldSym.type()));
            return localIdx;
        }
    }
}
if (ae.target() instanceof FieldAccessExpr fa) {
    return ExpressionFieldAssignLowerer.lowerField(driver, ae, fa, ops, owner, localIdx, locals);
}
if (ae.target() instanceof ArrayAccessExpr aa) {
    localIdx = ExpressionLowerer.emitExpression(driver, aa.receiver(), ops, owner, localIdx, locals);
    localIdx = ExpressionLowerer.emitExpression(driver, aa.index(), ops, owner, localIdx, locals);
    // #845: índice `Int?` estreitado — o iastore/aastore exige int
    CompilerEmissionHelpers.emitNarrowedNullableUnbox(driver, ops,
            ExpressionTyper.inferExprType(driver, aa.index(), locals), Type.PrimitiveType.INT);
    Type aaRecvType = ExpressionTyper.inferExprType(driver, aa.receiver(), locals);
    Type aaElemType = Type.arrayElementType(aaRecvType);
    String aaOp = ae.operator();
    boolean aaCompound = isCompoundOp(aaOp);
    if (aaCompound) {
        // compound em ELEMENTO de array (`values[0] += 5`, GitHub #64): o
        // stack do aaload é [receiver, index] — duplica os 2 e carrega o
        // valor atual; o store consome um par + o resultado.
        ops.add(new KofDup2());
        ops.add(new KofArrayLoad(aaElemType));
    }
    Type aaValueType = ExpressionTyper.inferExprType(driver, ae.value(), locals);
    boolean aaStringConcat = aaCompound && "+=".equals(aaOp)
            && (Type.isString(aaElemType) || Type.isString(aaValueType));
    if (aaStringConcat) {
        ExpressionBinaryLowerer.emitOperandToString(driver, ops, aaElemType);
    }
    localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
    if (aaStringConcat) {
        ExpressionBinaryLowerer.emitOperandToString(driver, ops, aaValueType);
        ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                BuiltinTypes.STRING, KofCallKind.FUNCTION));
    } else if (aaCompound) {
        // RHS primitivo ≠ elemento (ex.: Int[] += int ok, Long[] += int
        // precisa widening) — o KofBinary usa aaElemType p/ o opcode. Shift
        // (`<<=`) exige contagem int (L2I) — regra do §167.
        emitCompoundRhsConv(driver, ops, aaOp, aaElemType, aaValueType);
        ops.add(new KofBinary(compoundBinaryOp(aaOp), aaElemType));
    }
    if (!aaStringConcat && !aaCompound) {
        // §121: valor primitivo ≠ slot (ex.: Int em Long[]) → converter no IR
        // (I2L/L2I) ANTES do store, senão o emit gera lastore/aastore com tipo
        // errado e o verifier rejeita (frame crash COMP002 em
        // `new Long[4]; c[1] = 9`). O bloco existia como comentário-vazio
        // (prometia a conversão, nunca a emitiu) — mesma linha que o caminho
        // compound logo acima já aplica. NO compound NÃO duplicar: lá o
        // widening do RHS já foi feito antes do KofBinary.
        if (TypeMetrics.isPrimitiveType(aaValueType)
                && TypeMetrics.isPrimitiveType(aaElemType)) {
            driver.emitWideningIfNeeded(ops, CompilerEmissionHelpers.emitNarrowedNullableUnbox(
                    driver, ops, aaValueType, aaElemType), aaElemType); // #845
        }
    }
    ops.add(new KofArrayStore(aaStringConcat ? BuiltinTypes.STRING : aaElemType));
    return localIdx;
}
if (ae.target() instanceof IdentifierExpr ieBox) {
    for (int i = locals.size() - 1; i >= 0; i--) {
        if (locals.get(i).name().equals(ieBox.name()) && driver.boxFactory.isBoxType(locals.get(i).type())) {
            IRLocalVariable boxLv = locals.get(i);
            String op = ae.operator();
            Type valType = driver.boxFactory.boxValueType(boxLv.type());
            // #192 — compound (`+=`/`-=`/`*=`) e concat em variável capturada:
            // o `getfield value` consome a referência do box e o `putfield`
            // precisa dela de novo — duplicar antes (o caminho de campo de
            // instância já fazia; aqui faltava → VerifyError "Operand stack
            // underflow" no putfield do invoke() da lambda).
            if ("+=".equals(op) && BuiltinTypes.isString(valType)) {
                ops.add(new KofLoadLocal(boxLv.type(), boxLv.index()));
                ops.add(new KofDup());
                ops.add(new KofLoadField(boxLv.type(), "value", valType));
                localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
                ExpressionBinaryLowerer.emitOperandToString(driver, ops, ExpressionTyper.inferExprType(driver, ae.value(), locals));
                ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                        List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                        BuiltinTypes.STRING, KofCallKind.FUNCTION));
                ops.add(new KofStoreField(boxLv.type(), "value", valType));
            } else if (isCompoundOp(op)) {
                ops.add(new KofLoadLocal(boxLv.type(), boxLv.index()));
                ops.add(new KofDup());
                ops.add(new KofLoadField(boxLv.type(), "value", valType));
                localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
                emitCompoundRhsConv(driver, ops, op, valType,
                        ExpressionTyper.inferExprType(driver, ae.value(), locals));
                ops.add(new KofBinary(compoundBinaryOp(op), valType));
                ops.add(new KofStoreField(boxLv.type(), "value", valType));
            } else {
                // §253 face B: o receiver-box NUNCA fica na pilha de máquina
                // durante a avaliação do RHS — um push ímpar cruzando os calls
                // do RHS faz todo call entrar com rsp≡8 (mod 16) e o SSE da
                // libc (movaps) SIGSEGVa no callee (glibc é vítima; a pilha é
                // o bug). Ordem: avalia o RHS primeiro, derrama o valor num
                // slot de frame, e só então empilha [box, value] quando já não
                // existe nenhum call pendente. Load do slot é puro → ordem de
                // efeitos idêntica nos 4 backends (retro-compatível, regra 2).
                localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
                driver.emitWideningIfNeeded(ops, CompilerEmissionHelpers.emitNarrowedNullableUnbox(driver, ops,
                        ExpressionTyper.inferExprType(driver, ae.value(), locals), valType), valType); // #845
                ops.add(new KofStoreLocal(valType, localIdx));
                locals.add(new IRLocalVariable(localIdx, "$boxval" + localIdx, valType));
                ops.add(new KofLoadLocal(boxLv.type(), boxLv.index()));
                ops.add(new KofLoadLocal(valType, localIdx));
                ops.add(new KofStoreField(boxLv.type(), "value", valType));
                return localIdx + (TypeMetrics.isDoubleWidth(valType) ? 2 : 1);
            }
            return localIdx;
        }
    }
}
// composto sobre local: LHS empurrado ANTES do RHS (a ordem do
// binário é lhs op rhs). O caminho antigo empurrava o RHS na
// linha compartilhada e o LHS depois → `a -= 2` virava `2 - 10`
// (bugs 2 e 3: resultado errado + stack extra no concat de s+=).
if (ae.target() instanceof IdentifierExpr cie) {
    IRLocalVariable targetLocal = null;
    for (int i = locals.size() - 1; i >= 0; i--) {
        if (locals.get(i).name().equals(cie.name())) { targetLocal = locals.get(i); break; }
    }
    if (targetLocal != null) {
        String op = ae.operator();
        if ("+=".equals(op) && BuiltinTypes.isString(targetLocal.type())) {
            ops.add(new KofLoadLocal(targetLocal.type(), targetLocal.index()));
            ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                    List.of(Type.UnknownType.UNKNOWN), BuiltinTypes.STRING,
                    KofCallKind.STATIC));
            localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
            ExpressionBinaryLowerer.emitOperandToString(driver, ops, ExpressionTyper.inferExprType(driver, ae.value(), locals));
            ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                    List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                    BuiltinTypes.STRING, KofCallKind.FUNCTION));
            ops.add(new KofStoreLocal(targetLocal.type(), targetLocal.index()));
            return localIdx;
        } else if (isCompoundOp(op)) {
            // §295(b): `g += 1` sobre slot Nullable(primitivo) — referência
            // física no JVM (Commit B) e IADD direto sobre ela é VerifyError.
            // Aritmética no INNER (unbox → binário → box); emitErasureUnbox/
            // Box se auto-gamam (só JVM) e só casam primitivo CRÚ — um RHS
            // já Nullable passa o binário no wrapper como antes (nunca re-box
            // de referência: lesson §294-2a). Slot null no unbox → crash
            // honesto (NPE), nunca default silencioso (R6).
            Type cSlot = targetLocal.type();
            Type cInner = TypeMetrics.isNullablePrimitive(cSlot)
                    && ExpressionTyper.inferExprType(driver, ae.value(), locals)
                            instanceof Type.PrimitiveType
                    ? ((Type.NullableType) cSlot).inner() : null;
            ops.add(new KofLoadLocal(cSlot, targetLocal.index()));
            if (cInner != null) {
                driver.emitErasureUnbox(ops, cInner);
                localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
                emitCompoundRhsConv(driver, ops, op, cInner,
                        ExpressionTyper.inferExprType(driver, ae.value(), locals));
                ops.add(new KofBinary(compoundBinaryOp(op), cInner));
                driver.emitErasureBox(ops, cInner);
                ops.add(new KofStoreLocal(cSlot, targetLocal.index()));
                return localIdx;
            }
            localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
            // conversão ANTES do binário (shift: contagem int via L2I).
            emitCompoundRhsConv(driver, ops, op, targetLocal.type(),
                    ExpressionTyper.inferExprType(driver, ae.value(), locals));
            ops.add(new KofBinary(compoundBinaryOp(op), targetLocal.type()));
            // §696: o composto num slot Byte/Short estreita (JLS 15.26.2 — a
            // atribuição composta inclui o cast implícito). A aritmética
            // promove Byte/Short a Int, então o valor na pilha é Int.
            CompilerEmissionHelpers.emitStoreNarrow(driver, ops, Type.PrimitiveType.INT, targetLocal.type());
            ops.add(new KofStoreLocal(targetLocal.type(), targetLocal.index()));
            return localIdx;
        }
    }
}
// atribuição simples: empurra o RHS e guarda no slot do local
localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
if (ae.target() instanceof IdentifierExpr sie) {
    for (int i = locals.size() - 1; i >= 0; i--) {
        if (locals.get(i).name().equals(sie.name())) {
            // #845: `j = i` (i: Int? estreitado) num local primitivo
            Type rhsT = CompilerEmissionHelpers.emitNarrowedNullableUnbox(driver, ops,
                    ExpressionTyper.inferExprType(driver, ae.value(), locals), locals.get(i).type());
            driver.emitWideningIfNeeded(ops, rhsT, locals.get(i).type());
            // §696: estreita o store num slot Byte/Short
            CompilerEmissionHelpers.emitStoreNarrow(driver, ops, rhsT, locals.get(i).type());
            // bug 15: `Object o; o = 7` — box primitivo p/ referência
            // (#57: IfExpr/switch heterogêneo já boxeou in-branch → pular)
            // §295(b): espelho do gate cru do return/VarDecl — slot
            // Nullable(primitivo) é referência física (Commit B) e o gate de
            // cima (erasesToReference, false p/ NullableType) não o conhecia:
            // `g = 9` caía `bipush 9; astore` (VerifyError). Só primitivo CRÚ
            // boxa; RHS já Nullable passa como referência (nunca re-box).
            if (driver.erasesToReference(locals.get(i).type())
                    && TypeMetrics.isPrimitiveType(ExpressionTyper.inferExprType(driver, ae.value(), locals))
                    && !ExpressionTyper.boxesOwnBranches(driver, ae.value(), locals)) {
                driver.emitErasureBox(ops, ExpressionTyper.inferExprType(driver, ae.value(), locals));
            } else if (TypeMetrics.isNullablePrimitive(locals.get(i).type())
                    && ExpressionTyper.inferExprType(driver, ae.value(), locals) instanceof Type.PrimitiveType spt
                    && !Type.isVoid(spt)
                    && !ExpressionTyper.boxesOwnBranches(driver, ae.value(), locals)) {
                // §295(b): `Int? v = 5; v = 9` — slot boxed (Commit B) recebe
                // primitivo CRU → boxa (gate espelha return/VarDecl; RHS já
                // Nullable é referência física e passa sem re-box).
                // #259: boxa com o inner DECLARADO do slot — o widening da
                // linha acima já converteu a pilha p/ o destino, e boxar pelo
                // tipo de ORIGEM empilhava um tipo e chamava o boxer de outro
                // (`Float? x = 2.5; x = 3.5` empilhava float e chamava
                // Double.valueOf → bits de float lidos como double:
                // 5.3360734E-315 medido nos 3 targets nativos).
                Type slotInner = locals.get(i).type() instanceof Type.NullableType ntl
                        && ntl.inner() instanceof Type.PrimitiveType ? ntl.inner() : spt;
                driver.emitErasureBox(ops, slotInner);
            }
            ops.add(new KofStoreLocal(locals.get(i).type(), locals.get(i).index()));
            return localIdx;
        }
    }
}
ops.add(new KofStoreLocal(Type.UnknownType.UNKNOWN, localIdx));
return localIdx;
    }

    static KofBinaryOp compoundBinaryOp(String op) {
        return switch (op) {
            case "+=" -> KofBinaryOp.ADD;
            case "-=" -> KofBinaryOp.SUB;
            case "*=" -> KofBinaryOp.MUL;
            case "/=" -> KofBinaryOp.DIV;
            case "%=" -> KofBinaryOp.MOD;
            case "&=" -> KofBinaryOp.AND;
            case "|=" -> KofBinaryOp.OR;
            case "^=" -> KofBinaryOp.XOR;
            case "<<=" -> KofBinaryOp.SHL;
            case ">>=" -> KofBinaryOp.SHR;
            case ">>>=" -> KofBinaryOp.USHR;
            default -> KofBinaryOp.ADD;
        };
    }

    /**
     * Operador de atribuição composta reconhecido pelo lowering. O parser
     * aceita `<<=`, `>>=`, `>>>=` (Lexer/ExpressionParser), mas o lowerer não
     * os tratava como compostos → caíam no ramo de atribuição SIMPLES e só o
     * RHS era gravado (`x = 6; x <<= 2` virava `x = 2`, não 24) — bug de
     * correção silencioso nos 4 targets (achado 13/09 na caça Q4 do
     * translator, que emite `<<=`).
     */
    static boolean isCompoundOp(String op) {
        return switch (op) {
            case "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=",
                 "<<=", ">>=", ">>>=" -> true;
            default -> false;
        };
    }

    /** Operador de atribuição composta de shift (`<<=`, `>>=`, `>>>=`). */
    static boolean isShiftAssignOp(String op) {
        return "<<=".equals(op) || ">>=".equals(op) || ">>>=".equals(op);
    }

    /**
     * Conversão do RHS já empilhado para o composto. No shift o JVM usa
     * `lshl`/`ishl` com contagem SEMPRE int (`(long,int)`/`(int,int)`) e o
     * resultado tem o tipo PROMOVIDO do operando esquerdo (JLS 15.19) — o RHS
     * long precisa de L2I, nunca de widening p/ o tipo do alvo (§167 no
     * caminho binário; aqui no composto). Nos demais compostos, widening do
     * RHS p/ o tipo do alvo (ex.: campo Long `+=` Int).
     */
    static void emitCompoundRhsConv(CompilerDriver driver, List<KofOperation> ops,
                                    String op, Type targetType, Type valueType) {
        // #845: RHS `Int?` estreitado (`total += i`) abre a caixa antes do binário
        valueType = CompilerEmissionHelpers.emitNarrowedNullableUnbox(driver, ops, valueType, targetType);
        if (isShiftAssignOp(op)) {
            driver.emitPrimNarrow(ops, valueType, Type.PrimitiveType.INT);
        } else if (TypeMetrics.isPrimitiveType(valueType)
                && TypeMetrics.isPrimitiveType(targetType)) {
            driver.emitWideningIfNeeded(ops, valueType, targetType);
        }
    }
}