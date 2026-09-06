package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Emissão de instâncias UI (emitUiInstance), cores packed e SAM adapters.
 */
final class CompilerUiEmitter {

    private CompilerUiEmitter() {}

    static int emitSamAdapter(CompilerDriver driver, LambdaExpr le, Type.ClassType iface, ExternalClasspath.Sam sam,
                               List<KofOperation> ops, String owner, int localIdx,
                               List<IRLocalVariable> locals) {
        List<IRLocalVariable> captures = driver.collectCaptures(le, locals);
        if (CompilerLambdaClass.lambdaUsesSuper(le) && driver.currentLoweringOwner != null) {
            Type outerType = CompilerTypes.ownerTypeFromInternal(driver.currentLoweringOwner, driver.semanticAnalyzer);
            List<IRLocalVariable> eff = new ArrayList<>();
            eff.add(new IRLocalVariable(0, "$outer", outerType));
            eff.addAll(captures);
            captures = eff;
        }
        String className = driver.samAdapterNames.computeIfAbsent(le,
                k -> "Sam" + iface.name().replace('.', '_') + "_" + (++driver.lambdaCounter));
        if (CompilerLambdaClass.lambdaUsesSuper(le) && driver.currentLoweringOwner != null) {
            driver.lambdaEnclosingOwner.put(className, driver.currentLoweringOwner);
        }

        List<Type> samParamTypes = new ArrayList<>();
        for (String d : sam.signature().parameterDescriptors()) {
            samParamTypes.add(ExternalClasspath.typeFromDescriptor(d));
        }
        Type samReturn = ExternalClasspath.typeFromDescriptor(sam.signature().returnDescriptor());

        if (le.parameters().size() != samParamTypes.size() && driver.currentDiagnostics != null) {
            SourcePosition p = le.position();
            driver.currentDiagnostics.error(p != null ? p.file() : "",
                    p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                    "SAM mismatch: lambda has " + le.parameters().size()
                            + " parameter(s) but " + iface.name() + "." + sam.methodName()
                            + " needs " + samParamTypes.size(),
                    "SAM001");
        }

        if (!driver.samAdapterNames.containsValue(className) || !CompilerTypeSupport.syntheticExists(driver, className)) {
            driver.buildSyntheticAdapter(className, iface.internalName(), sam.methodName(),
                    samParamTypes, samReturn, le, captures);
        }

        Type adapterType = new Type.ClassType("", className, List.of());
        List<Type> captureTypes = new ArrayList<>();
        for (IRLocalVariable cap : captures) captureTypes.add(cap.type());
        ops.add(new KofNewObject(adapterType, captureTypes));
        ops.add(new KofDup());
        for (IRLocalVariable cap : captures) {
            ops.add(new KofLoadLocal(cap.type(), cap.index()));
        }
        ops.add(new KofCall(adapterType, "<init>", captureTypes,
                Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
        return localIdx;
    }

    static int emitFieldIncrement(CompilerDriver driver, Type ownerType, String fieldName, Type fieldType,
                                   boolean prefix, KofBinaryOp op,
                                   List<KofOperation> ops, int localIdx,
                                   List<IRLocalVariable> locals) {
        int recvTmp = localIdx++;
        int valTmp = localIdx++;
        int newTmp = localIdx++;
        locals.add(new IRLocalVariable(recvTmp, "#recv", ownerType));
        locals.add(new IRLocalVariable(valTmp, "#inc", fieldType));
        locals.add(new IRLocalVariable(newTmp, "#new", fieldType));
        ops.add(new KofStoreLocal(ownerType, recvTmp));
        ops.add(new KofLoadLocal(ownerType, recvTmp));
        ops.add(new KofLoadField(ownerType, fieldName, fieldType));
        ops.add(new KofStoreLocal(fieldType, valTmp));
        ops.add(new KofLoadLocal(fieldType, valTmp));
        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 1));
        ops.add(new KofBinary(op, fieldType));
        ops.add(new KofStoreLocal(fieldType, newTmp));
        ops.add(new KofLoadLocal(ownerType, recvTmp));
        ops.add(new KofLoadLocal(fieldType, newTmp));
        ops.add(new KofStoreField(ownerType, fieldName, fieldType));
        ops.add(new KofLoadLocal(fieldType, prefix ? newTmp : valTmp));
        return localIdx;
    }

    static int emitPackedColor(CompilerDriver driver, List<ExpressionNode> args, List<KofOperation> ops,
                               String owner, int localIdx, List<IRLocalVariable> locals) {
        for (int i = 0; i < args.size(); i++) {
            localIdx = ExpressionLowerer.emitExpression(driver, args.get(i), ops, owner, localIdx, locals);
            if (i == 0) {
                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 24));
                ops.add(new KofBinary(KofBinaryOp.SHL, Type.PrimitiveType.INT));
            } else if (i == 1) {
                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 16));
                ops.add(new KofBinary(KofBinaryOp.SHL, Type.PrimitiveType.INT));
            } else if (i == 2) {
                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 8));
                ops.add(new KofBinary(KofBinaryOp.SHL, Type.PrimitiveType.INT));
            }
            if (i < 3 && i > 0) {
                ops.add(new KofBinary(KofBinaryOp.OR, Type.PrimitiveType.INT));
            }
            if (i == 3) {
                ops.add(new KofBinary(KofBinaryOp.OR, Type.PrimitiveType.INT));
            }
        }
        if (args.size() == 3) {
            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0xFF));
            ops.add(new KofBinary(KofBinaryOp.OR, Type.PrimitiveType.INT));
        }
        return localIdx;
    }

    static int emitUiInstance(CompilerDriver driver, Type recvType, MethodCallExpr mc, List<KofOperation> ops,
                                String owner, int localIdx, List<IRLocalVariable> locals) {
        if (KofUi.isComponent(recvType) || KofUi.isStore(recvType)) {
            KofUi.UiCall cc = KofUi.instanceMethod(recvType, mc.methodName(), mc.arguments().size());
            if (cc != null) {
                for (ExpressionNode arg : mc.arguments()) {
                    localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                }
                List<Type> ccParams = new ArrayList<>();
                ccParams.add(Type.PrimitiveType.INT);
                ccParams.addAll(cc.parameterTypes());
                ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                        cc.function(), ccParams, cc.returnType(), KofCallKind.FUNCTION));
                return localIdx;
            }
            return localIdx;
        }
        if (KofUi.isWindow(recvType) || KofUi.isLabel(recvType) || KofUi.isButton(recvType)
                || KofUi.isInput(recvType) || KofUi.isView(recvType)
                || KofUi.isLink(recvType) || KofUi.isImage(recvType) || KofUi.isIcon(recvType)
                || KofUi.isCanvas(recvType)) {
            KofUi.UiCall uiCall = KofUi.instanceMethod(recvType, mc.methodName(), mc.arguments().size());
            if (uiCall != null) {
                for (ExpressionNode arg : mc.arguments()) {
                    localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                }
                List<Type> uiParams = new ArrayList<>();
                uiParams.add(Type.PrimitiveType.INT);
                uiParams.addAll(uiCall.parameterTypes());
                ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                        uiCall.function(), uiParams, uiCall.returnType(), KofCallKind.FUNCTION));
                return localIdx;
            }
            return localIdx;
        }
        if (KofUi.isColor(recvType)) {
            switch (mc.methodName()) {
                case "red" -> {
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 24));
                    ops.add(new KofBinary(KofBinaryOp.USHR, Type.PrimitiveType.INT));
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0xFF));
                    ops.add(new KofBinary(KofBinaryOp.AND, Type.PrimitiveType.INT));
                    return localIdx;
                }
                case "green" -> {
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 16));
                    ops.add(new KofBinary(KofBinaryOp.USHR, Type.PrimitiveType.INT));
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0xFF));
                    ops.add(new KofBinary(KofBinaryOp.AND, Type.PrimitiveType.INT));
                    return localIdx;
                }
                case "blue" -> {
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 8));
                    ops.add(new KofBinary(KofBinaryOp.USHR, Type.PrimitiveType.INT));
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0xFF));
                    ops.add(new KofBinary(KofBinaryOp.AND, Type.PrimitiveType.INT));
                    return localIdx;
                }
                case "alpha" -> {
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0xFF));
                    ops.add(new KofBinary(KofBinaryOp.AND, Type.PrimitiveType.INT));
                    return localIdx;
                }
                case "isOpaque" -> {
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0xFF));
                    ops.add(new KofBinary(KofBinaryOp.AND, Type.PrimitiveType.INT));
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0xFF));
                    ops.add(new KofBinary(KofBinaryOp.EQ, Type.PrimitiveType.INT));
                    return localIdx;
                }
                case "withAlpha" -> {
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0xFFFFFF00));
                    ops.add(new KofBinary(KofBinaryOp.AND, Type.PrimitiveType.INT));
                    for (ExpressionNode arg : mc.arguments()) {
                        localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                    }
                    ops.add(new KofBinary(KofBinaryOp.OR, Type.PrimitiveType.INT));
                    return localIdx;
                }
                case "toCss" -> {
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            "kof_ui_color_to_css", List.of(Type.PrimitiveType.INT),
                            BuiltinTypes.STRING, KofCallKind.FUNCTION));
                    return localIdx;
                }
                default -> {
                    return localIdx;
                }
            }
        }
        if (KofUi.isTheme(recvType)) {
            switch (mc.methodName()) {
                case "background", "surface", "primary", "secondary", "text", "error" -> {
                    int tagTmp = localIdx++;
                    locals.add(new IRLocalVariable(tagTmp, "#theme", Type.PrimitiveType.INT));
                    ops.add(new KofStoreLocal(Type.PrimitiveType.INT, tagTmp));
                    ops.add(new KofLoadLocal(Type.PrimitiveType.INT, tagTmp));
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 1));
                    LabelId darkLabel = LabelId.create();
                    LabelId lightLabel = LabelId.create();
                    LabelId endLabel = LabelId.create();
                    ops.add(new KofConditionalJump(KofComparison.NE, lightLabel, darkLabel));
                    ops.add(new KofLabel(lightLabel));
                    Integer light = KofUi.themeColor(mc.methodName(), 0);
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, light));
                    ops.add(new KofJump(endLabel));
                    ops.add(new KofLabel(darkLabel));
                    Integer dark = KofUi.themeColor(mc.methodName(), 1);
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, dark));
                    ops.add(new KofLabel(endLabel));
                    return localIdx;
                }
                case "isDark" -> {
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 1));
                    ops.add(new KofBinary(KofBinaryOp.EQ, Type.PrimitiveType.INT));
                    return localIdx;
                }
                default -> {
                    return localIdx;
                }
            }
        }
        return localIdx;
    }
}