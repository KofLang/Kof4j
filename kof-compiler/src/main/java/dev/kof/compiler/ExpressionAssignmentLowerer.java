package dev.kof.compiler;

import java.util.List;

/**
 * Lowering de AssignmentExpr (case do emitExpression).
 */
final class ExpressionAssignmentLowerer {

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
            localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
            ops.add(new KofPutStatic(cs.type(), fa.fieldName(), fld.type()));
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
    String faOp = ae.operator();
    if ("+=".equals(faOp) || "-=".equals(faOp) || "*=".equals(faOp)
            || "/=".equals(faOp) || "%=".equals(faOp)
            || "&=".equals(faOp) || "|=".equals(faOp) || "^=".equals(faOp)) {
        ops.add(new KofDup());
        ops.add(new KofLoadField(ExpressionTyper.inferExprType(driver, fa.receiver(), locals), fa.fieldName(),
                Type.UnknownType.UNKNOWN));
    }
    localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
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
    localIdx = ExpressionLowerer.emitExpression(driver, ae.value(), ops, owner, localIdx, locals);
    Type recvType = ExpressionTyper.inferExprType(driver, aa.receiver(), locals);
    Type elemType = Type.arrayElementType(recvType);
    // valor com primitivo ≠ slot (ex.: Int em Long[]) →
    // converter no IR (I2L/L2I), senão o emit gera aastore/
    // lastore com tipo errado e o verifier rejeita (o
    // frame crash COMP002 em new Long[] + a[i] = i*3)
    driver.emitPrimWidenNarrow(ops, ae.value(), elemType, locals);
    ops.add(new KofArrayStore(elemType));
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
            if (driver.erasesToReference(locals.get(i).type())
                    && TypeMetrics.isPrimitiveType(ExpressionTyper.inferExprType(driver, ae.value(), locals))) {
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
}