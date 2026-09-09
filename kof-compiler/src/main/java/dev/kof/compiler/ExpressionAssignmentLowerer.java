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
                boolean compound = "+=".equals(sop) || "-=".equals(sop) || "*=".equals(sop)
                        || "/=".equals(sop) || "%=".equals(sop) || "&=".equals(sop)
                        || "|=".equals(sop) || "^=".equals(sop);
                if (compound) {
                    ops.add(new KofGetStatic(ownerType, ie.name(), fsStatic.type()));
                }
                localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
                if (compound) {
                    KofBinaryOp binOp = switch (sop) {
                        case "+=" -> KofBinaryOp.ADD;
                        case "-=" -> KofBinaryOp.SUB;
                        case "*=" -> KofBinaryOp.MUL;
                        case "/=" -> KofBinaryOp.DIV;
                        case "%=" -> KofBinaryOp.MOD;
                        case "&=" -> KofBinaryOp.AND;
                        case "|=" -> KofBinaryOp.OR;
                        case "^=" -> KofBinaryOp.XOR;
                        default -> KofBinaryOp.ADD;
                    };
                    ops.add(new KofBinary(binOp, fsStatic.type()));
                }
                ops.add(new KofPutStatic(ownerType, ie.name(), fsStatic.type()));
                return localIdx;
            }
            ops.add(new KofLoadLocal(ownerType, 0));
            String op = ae.operator();
            if ("+=".equals(op) || "-=".equals(op) || "*=".equals(op)
                    || "/=".equals(op) || "%=".equals(op)
                    || "&=".equals(op) || "|=".equals(op) || "^=".equals(op)) {
                // compound em CAMPO de instância: o getfield consome o `this`
                // e o putfield precisa dele de novo — duplica antes (bug 40:
                // stack underflow no putfield, `n += 1` em método de instância).
                ops.add(new KofDup());
                ops.add(new KofLoadField(ownerType, ie.name(), fieldSym.type()));
            }
            localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
            if ("+=".equals(op) || "-=".equals(op) || "*=".equals(op)
                    || "/=".equals(op) || "%=".equals(op)
                    || "&=".equals(op) || "|=".equals(op) || "^=".equals(op)) {
                KofBinaryOp binOp = switch (op) {
                    case "+=" -> KofBinaryOp.ADD;
                    case "-=" -> KofBinaryOp.SUB;
                    case "*=" -> KofBinaryOp.MUL;
                    case "/=" -> KofBinaryOp.DIV;
                    case "%=" -> KofBinaryOp.MOD;
                    case "&=" -> KofBinaryOp.AND;
                    case "|=" -> KofBinaryOp.OR;
                    case "^=" -> KofBinaryOp.XOR;
                    default -> KofBinaryOp.ADD;
                };
                ops.add(new KofBinary(binOp, fieldSym.type()));
            }
            ops.add(new KofStoreField(ownerType, ie.name(), fieldSym.type()));
            return localIdx;
        }
    }
}
if (ae.target() instanceof FieldAccessExpr fa) {
    if (fa.receiver() instanceof IdentifierExpr rid && driver.semanticAnalyzer != null
            && driver.semanticAnalyzer.getClass(rid.name()) != null) {
        // Static field store: Class.field = value.
        SymbolTable.ClassSymbol cs = driver.semanticAnalyzer.getClass(rid.name());
        SymbolTable.Symbol fs = HierarchyResolver.resolveFieldInHierarchy(cs.name(), fa.fieldName(), driver.semanticAnalyzer);
        if (fs instanceof SymbolTable.FieldSymbol fld) {
            String sfaOp = ae.operator();
            boolean sfaCompound = "+=".equals(sfaOp) || "-=".equals(sfaOp) || "*=".equals(sfaOp)
                    || "/=".equals(sfaOp) || "%=".equals(sfaOp) || "&=".equals(sfaOp)
                    || "|=".equals(sfaOp) || "^=".equals(sfaOp);
            // compound em campo ESTÁTICO qualificado (`Counter.total += 5`,
            // GitHub #64): getstatic antes do emit — sem receiver na pilha
            // (estático não consome this), a ordem simples do caminho por
            // nome simples basta.
            if (sfaCompound) {
                ops.add(new KofGetStatic(cs.type(), fa.fieldName(), fld.type()));
            }
            Type sfaValueType = ExpressionTyper.inferExprType(driver, ae.value(), locals);
            boolean sfaConcat = sfaCompound && "+=".equals(sfaOp)
                    && (Type.isString(fld.type()) || Type.isString(sfaValueType));
            if (sfaConcat) {
                if (!Type.isString(fld.type()) && TypeMetrics.isPrimitiveType(fld.type())) {
                    TypeEmitter.boxPrimitive(ops, fld.type());
                }
                if (!Type.isString(fld.type())) {
                    ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                            List.of(driver.target.isNative() && !Type.isString(fld.type())
                                    && !(fld.type() instanceof Type.PrimitiveType)
                                    ? fld.type() : Type.UnknownType.UNKNOWN),
                            BuiltinTypes.STRING, KofCallKind.STATIC));
                }
            }
            localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
            if (sfaConcat) {
                if (!Type.isString(sfaValueType) && TypeMetrics.isPrimitiveType(sfaValueType)) {
                    TypeEmitter.boxPrimitive(ops, sfaValueType);
                }
                if (!Type.isString(sfaValueType)) {
                    ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                            List.of(driver.target.isNative() && !Type.isString(sfaValueType)
                                    && !(sfaValueType instanceof Type.PrimitiveType)
                                    ? sfaValueType : Type.UnknownType.UNKNOWN),
                            BuiltinTypes.STRING, KofCallKind.STATIC));
                }
                ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                        List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                        BuiltinTypes.STRING, KofCallKind.FUNCTION));
            } else if (sfaCompound) {
                // RHS primitivo ≠ campo (ex.: Double *= int): widening p/ o
                // tipo do campo — o KofBinary usa fld.type() p/ o opcode e o
                // literal int na pilha de um DMUL daria frame inválido.
                if (TypeMetrics.isPrimitiveType(sfaValueType)
                        && TypeMetrics.isPrimitiveType(fld.type())) {
                    driver.emitWideningIfNeeded(ops, sfaValueType, fld.type());
                }
                ops.add(new KofBinary(compoundBinaryOp(sfaOp), fld.type()));
            }
            ops.add(new KofPutStatic(cs.type(), fa.fieldName(), sfaConcat ? BuiltinTypes.STRING : fld.type()));
            return localIdx;
        }
    }
    Type faRecvType = ExpressionTyper.inferExprType(driver, fa.receiver(), locals);
    if (KofUi.isWindow(faRecvType) && "title".equals(fa.fieldName())) {
        localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
        localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                "kof_ui_window_set_title", List.of(Type.PrimitiveType.INT, BuiltinTypes.STRING),
                Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
        return localIdx;
    }
    if (KofUi.isLabel(faRecvType) && "text".equals(fa.fieldName())) {
        localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
        localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                "kof_ui_label_set_text", List.of(Type.PrimitiveType.INT, BuiltinTypes.STRING),
                Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
        return localIdx;
    }
    if (KofUi.isLabel(faRecvType) && "fontSize".equals(fa.fieldName())) {
        localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
        localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                "kof_ui_label_set_font_size", List.of(Type.PrimitiveType.INT, Type.PrimitiveType.INT),
                Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
        return localIdx;
    }
    if (KofUi.isLabel(faRecvType) && "bold".equals(fa.fieldName())) {
        localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
        localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                "kof_ui_label_set_bold", List.of(Type.PrimitiveType.INT, Type.PrimitiveType.BOOL),
                Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
        return localIdx;
    }
    if (KofUi.isLabel(faRecvType) && "color".equals(fa.fieldName())) {
        localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
        localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                "kof_ui_label_set_color", List.of(Type.PrimitiveType.INT, Type.PrimitiveType.INT),
                Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
        return localIdx;
    }
    if (KofUi.isWindow(faRecvType) && "theme".equals(fa.fieldName())) {
        localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
        localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                "kof_ui_window_set_theme", List.of(Type.PrimitiveType.INT, Type.PrimitiveType.INT),
                Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
        return localIdx;
    }
    if (KofUi.isButton(faRecvType) && "text".equals(fa.fieldName())) {
        localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
        localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                "kof_ui_button_set_text", List.of(Type.PrimitiveType.INT, BuiltinTypes.STRING),
                Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
        return localIdx;
    }
    if (KofUi.isInput(faRecvType) && "text".equals(fa.fieldName())) {
        localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
        localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                "kof_ui_input_set_text", List.of(Type.PrimitiveType.INT, BuiltinTypes.STRING),
                Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
        return localIdx;
    }
    if (KofUi.isComponent(faRecvType) && "state".equals(fa.fieldName())) {
        localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
        localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                "kof_ui_component_state_set", List.of(Type.PrimitiveType.INT, Type.PrimitiveType.INT),
                Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
        return localIdx;
    }
    localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
    Type recvType = ExpressionTyper.inferExprType(driver, fa.receiver(), locals);
    Type fieldType = Type.UnknownType.UNKNOWN;
    if (recvType instanceof Type.ClassType ct) {
        SymbolTable.Symbol fs = HierarchyResolver.resolveFieldInHierarchy(ct.name(), fa.fieldName(), driver.semanticAnalyzer);
        if (fs != null) fieldType = fs.type();
        else if (!ct.packageName().isEmpty() && driver.externalClasspath != null
                && driver.externalClasspath.knows(ct.internalName())) {
            String desc = driver.externalClasspath.resolveFieldType(
                    ct.internalName(), fa.fieldName());
            if (desc != null) fieldType = ExternalClasspath.typeFromDescriptor(desc);
        }
    }
    String faOp = ae.operator();
    if ("+=".equals(faOp) || "-=".equals(faOp) || "*=".equals(faOp)
            || "/=".equals(faOp) || "%=".equals(faOp)
            || "&=".equals(faOp) || "|=".equals(faOp) || "^=".equals(faOp)) {
        // compound em CAMPO (via variável, ex.: b.n -= 2): o getfield
        // consome o receiver e o putfield precisa dele de novo — duplica
        // (bug 40: putfield com stack underflow). O tipo do campo REAL
        // (não Unknown) evita getfield de Object + aritmética inválida.
        ops.add(new KofDup());
        ops.add(new KofLoadField(recvType, fa.fieldName(), fieldType));
    }
    localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
    if ("+=".equals(faOp) || "-=".equals(faOp) || "*=".equals(faOp)
            || "/=".equals(faOp) || "%=".equals(faOp)
            || "&=".equals(faOp) || "|=".equals(faOp) || "^=".equals(faOp)) {
        KofBinaryOp binOp = switch (faOp) {
            case "+=" -> KofBinaryOp.ADD;
            case "-=" -> KofBinaryOp.SUB;
            case "*=" -> KofBinaryOp.MUL;
            case "/=" -> KofBinaryOp.DIV;
            case "%=" -> KofBinaryOp.MOD;
            case "&=" -> KofBinaryOp.AND;
            case "|=" -> KofBinaryOp.OR;
            case "^=" -> KofBinaryOp.XOR;
            default -> KofBinaryOp.ADD;
        };
        ops.add(new KofBinary(binOp, fieldType));
    }
    ops.add(new KofStoreField(recvType, fa.fieldName(), fieldType));
    return localIdx;
}
if (ae.target() instanceof ArrayAccessExpr aa) {
    localIdx = ExpressionLowerer.emitExpression(driver, aa.receiver(), ops, owner, localIdx, locals);
    localIdx = ExpressionLowerer.emitExpression(driver, aa.index(), ops, owner, localIdx, locals);
    Type aaRecvType = ExpressionTyper.inferExprType(driver, aa.receiver(), locals);
    Type aaElemType = Type.arrayElementType(aaRecvType);
    String aaOp = ae.operator();
    boolean aaCompound = "+=".equals(aaOp) || "-=".equals(aaOp) || "*=".equals(aaOp)
            || "/=".equals(aaOp) || "%=".equals(aaOp) || "&=".equals(aaOp)
            || "|=".equals(aaOp) || "^=".equals(aaOp);
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
    if (aaStringConcat && !Type.isString(aaElemType) && TypeMetrics.isPrimitiveType(aaElemType)) {
        TypeEmitter.boxPrimitive(ops, aaElemType);
    }
    if (aaStringConcat && !Type.isString(aaElemType)) {
        ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                List.of(driver.target.isNative() && !Type.isString(aaElemType)
                        && !(aaElemType instanceof Type.PrimitiveType)
                        ? aaElemType : Type.UnknownType.UNKNOWN),
                BuiltinTypes.STRING, KofCallKind.STATIC));
    }
    localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
    if (aaStringConcat) {
        if (!Type.isString(aaValueType) && TypeMetrics.isPrimitiveType(aaValueType)) {
            TypeEmitter.boxPrimitive(ops, aaValueType);
        }
        if (!Type.isString(aaValueType)) {
            ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                    List.of(driver.target.isNative() && !Type.isString(aaValueType)
                            && !(aaValueType instanceof Type.PrimitiveType)
                            ? aaValueType : Type.UnknownType.UNKNOWN),
                    BuiltinTypes.STRING, KofCallKind.STATIC));
        }
        ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                BuiltinTypes.STRING, KofCallKind.FUNCTION));
    } else if (aaCompound) {
        // RHS primitivo ≠ elemento (ex.: Int[] += int ok, Long[] += int
        // precisa widening) — o KofBinary usa aaElemType p/ o opcode.
        if (TypeMetrics.isPrimitiveType(aaValueType)
                && TypeMetrics.isPrimitiveType(aaElemType)) {
            driver.emitWideningIfNeeded(ops, aaValueType, aaElemType);
        }
        ops.add(new KofBinary(compoundBinaryOp(aaOp), aaElemType));
    }
    if (!aaStringConcat && !aaCompound) {
        // valor com primitivo ≠ slot (ex.: Int em Long[]) →
        // converter no IR (I2L/L2I), senão o emit gera aastore/
        // lastore com tipo errado e o verifier rejeita (o
        // frame crash COMP002 em new Long[] + a[i] = i*3).
        // NO compound NÃO aplicar: o resultado do KofBinary já é o tipo do
        // elemento (o widening do RHS foi feito antes) — a conversão extra
        // consumiria o topo errado (I2L sobre long → VerifyError).
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
            if ("+=".equals(op) && BuiltinTypes.isString(valType)) {
                ops.add(new KofLoadLocal(boxLv.type(), boxLv.index()));
                ops.add(new KofLoadField(boxLv.type(), "value", valType));
                localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
                ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                        List.of(Type.UnknownType.UNKNOWN), BuiltinTypes.STRING,
                        KofCallKind.STATIC));
                ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                        List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                        BuiltinTypes.STRING, KofCallKind.FUNCTION));
                ops.add(new KofStoreField(boxLv.type(), "value", valType));
            } else if ("+=".equals(op) || "-=".equals(op) || "*=".equals(op)
                    || "/=".equals(op) || "%=".equals(op)
                    || "&=".equals(op) || "|=".equals(op) || "^=".equals(op)) {
                ops.add(new KofLoadLocal(boxLv.type(), boxLv.index()));
                ops.add(new KofLoadField(boxLv.type(), "value", valType));
                localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
                driver.emitWideningIfNeeded(ops, ExpressionTyper.inferExprType(driver, ae.value(), locals), valType);
                KofBinaryOp binOp = switch (op) {
                    case "+=" -> KofBinaryOp.ADD;
                    case "-=" -> KofBinaryOp.SUB;
                    case "*=" -> KofBinaryOp.MUL;
                    case "/=" -> KofBinaryOp.DIV;
                    case "%=" -> KofBinaryOp.MOD;
                    case "&=" -> KofBinaryOp.AND;
                    case "|=" -> KofBinaryOp.OR;
                    case "^=" -> KofBinaryOp.XOR;
                    default -> KofBinaryOp.ADD;
                };
                ops.add(new KofBinary(binOp, valType));
                driver.emitWideningIfNeeded(ops, valType, valType);
                ops.add(new KofStoreField(boxLv.type(), "value", valType));
            } else {
                ops.add(new KofLoadLocal(boxLv.type(), boxLv.index()));
                localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
                driver.emitWideningIfNeeded(ops, ExpressionTyper.inferExprType(driver, ae.value(), locals), valType);
                ops.add(new KofStoreField(boxLv.type(), "value", valType));
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
            ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                    List.of(Type.UnknownType.UNKNOWN), BuiltinTypes.STRING,
                    KofCallKind.STATIC));
            ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                    List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                    BuiltinTypes.STRING, KofCallKind.FUNCTION));
            ops.add(new KofStoreLocal(targetLocal.type(), targetLocal.index()));
            return localIdx;
        } else if ("+=".equals(op) || "-=".equals(op) || "*=".equals(op)
                || "/=".equals(op) || "%=".equals(op)
                || "&=".equals(op) || "|=".equals(op) || "^=".equals(op)) {
            ops.add(new KofLoadLocal(targetLocal.type(), targetLocal.index()));
            localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
            KofBinaryOp binOp = switch (op) {
                case "+=" -> KofBinaryOp.ADD;
                case "-=" -> KofBinaryOp.SUB;
                case "*=" -> KofBinaryOp.MUL;
                case "/=" -> KofBinaryOp.DIV;
                case "%=" -> KofBinaryOp.MOD;
                case "&=" -> KofBinaryOp.AND;
                case "|=" -> KofBinaryOp.OR;
                case "^=" -> KofBinaryOp.XOR;
                default -> KofBinaryOp.ADD;
            };
            ops.add(new KofBinary(binOp, targetLocal.type()));
            driver.emitWideningIfNeeded(ops, ExpressionTyper.inferExprType(driver, ae.value(), locals), targetLocal.type());
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
            driver.emitWideningIfNeeded(ops, ExpressionTyper.inferExprType(driver, ae.value(), locals), locals.get(i).type());
            // bug 15: `Object o; o = 7` — box primitivo p/ referência
            // (#57: IfExpr/switch heterogêneo já boxeou in-branch → pular)
            if (driver.erasesToReference(locals.get(i).type())
                    && TypeMetrics.isPrimitiveType(ExpressionTyper.inferExprType(driver, ae.value(), locals))
                    && !ExpressionTyper.boxesOwnBranches(driver, ae.value(), locals)) {
                driver.emitErasureBox(ops, ExpressionTyper.inferExprType(driver, ae.value(), locals));
            }
            ops.add(new KofStoreLocal(locals.get(i).type(), locals.get(i).index()));
            return localIdx;
        }
    }
}
ops.add(new KofStoreLocal(Type.UnknownType.UNKNOWN, localIdx));
return localIdx;
    }

    private static KofBinaryOp compoundBinaryOp(String op) {
        return switch (op) {
            case "+=" -> KofBinaryOp.ADD;
            case "-=" -> KofBinaryOp.SUB;
            case "*=" -> KofBinaryOp.MUL;
            case "/=" -> KofBinaryOp.DIV;
            case "%=" -> KofBinaryOp.MOD;
            case "&=" -> KofBinaryOp.AND;
            case "|=" -> KofBinaryOp.OR;
            case "^=" -> KofBinaryOp.XOR;
            default -> KofBinaryOp.ADD;
        };
    }
}