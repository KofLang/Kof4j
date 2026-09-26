package dev.kof.compiler;

import java.util.List;

/**
 * Lowering do acesso a campo (FieldAccessExpr) extraido VERBATIM do
 * ExpressionLowerer (gate &lt;=500; rule 3 = estrutura, nao comportamento):
 * UI palette/components, enums, estaticos externos (face §500 slice B via
 * StaticClassReceiver), reflection JVM e unboxing de erasure.
 */
final class ExpressionFieldAccessLowerer {

    private ExpressionFieldAccessLowerer() {}

    static int emit(CompilerDriver driver, FieldAccessExpr fa, List<KofOperation> ops, String owner,
                    int localIdx, List<IRLocalVariable> locals) {
        if (fa.receiver() instanceof IdentifierExpr pId && KofUi.isPalette(pId.name())) {
            Integer color = KofUi.paletteColor(fa.fieldName());
            if (color != null) {
                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, color));
                return localIdx;
            }
        }
        if (fa.receiver() instanceof IdentifierExpr tid && KofUiTokens.isTokenNamespace(tid.name())) {
            Integer tok = KofUiTokens.tokenValue(tid.name(), fa.fieldName());
            if (tok != null) {
                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, tok));
                return localIdx;
            }
            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
            return localIdx;
        }
        if (fa.receiver() instanceof IdentifierExpr sid2 && "super".equals(sid2.name())
                && !owner.isEmpty() && driver.semanticAnalyzer != null) {
            // super.campo: GETFIELD com owner na superclasse
            String superInternal = HierarchyResolver.findSuperClass(owner, driver.semanticAnalyzer);
            if (superInternal == null) superInternal = "java/lang/Object";
            superInternal = superInternal.replace('.', '/');
            Type superType = CompilerTypes.ownerTypeFromInternal(superInternal, driver.semanticAnalyzer);
            String superSimple = superInternal.substring(superInternal.lastIndexOf('/') + 1);
            SymbolTable.Symbol fieldSym = driver.semanticAnalyzer.resolveInHierarchy(superSimple, fa.fieldName());
            Type fieldType = fieldSym != null ? fieldSym.type() : ExpressionTyper.inferExprType(driver, fa, locals);
            ops.add(new KofLoadLocal(CompilerTypes.ownerTypeFromInternal(owner, driver.semanticAnalyzer), 0));
            ops.add(new KofLoadField(superType, fa.fieldName(), fieldType));
            return localIdx;
        }
        // §500 slice B: `KofGetStatic` real do campo estático externo
        // pelo nome da classe (antes: getfield de owner vazio → "?").
        if (StaticClassReceiver.emitStatic(driver, fa, locals, ops)) {
            return localIdx;
        }
        {
            // campo de classe EXTERNA: owner e tipo vêm do classpath
            Type extRecv = ExpressionTyper.inferExprType(driver, fa.receiver(), locals);
            if (extRecv instanceof Type.ClassType ect && !ect.packageName().isEmpty()
                    && driver.externalClasspath.knows(ect.internalName())) {
                String desc = driver.externalClasspath.resolveFieldType(ect.internalName(), fa.fieldName());
                if (desc != null) {
                    localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
                    ops.add(new KofLoadField(ect, fa.fieldName(),
                            ExternalClasspath.typeFromDescriptor(desc)));
                    return localIdx;
                }
            }
        }
        Type faType = ExpressionTyper.inferExprType(driver, fa.receiver(), locals);
        if (KofProcess.isResult(faType) && KofProcess.isField(fa.fieldName())) {
            localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
            if (driver.target.isNative()) {
                // Native: Result é objeto opaco do RuntimeProcess — os
                // campos baixam para os accessors (o KofLoadField não
                // tem layout de classe builtin no backend asm).
                Type ft = KofProcess.fieldType(fa.fieldName());
                ops.add(new KofCall(ft, "kof_process_result_" + fa.fieldName(),
                        List.of(KofProcess.RESULT), ft, KofCallKind.FUNCTION));
            } else {
                ops.add(new KofLoadField(KofProcess.RESULT, fa.fieldName(),
                        KofProcess.fieldType(fa.fieldName())));
            }
            return localIdx;
        }
        if (KofUi.isWindow(faType) && "title".equals(fa.fieldName())) {
            localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
            ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                    "kof_ui_window_title", List.of(Type.PrimitiveType.INT),
                    BuiltinTypes.STRING, KofCallKind.FUNCTION));
            return localIdx;
        }
        if (KofUi.isLabel(faType) && "text".equals(fa.fieldName())) {
            localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
            ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                    "kof_ui_label_text", List.of(Type.PrimitiveType.INT),
                    BuiltinTypes.STRING, KofCallKind.FUNCTION));
            return localIdx;
        }
        if (KofUi.isLabel(faType) && "fontSize".equals(fa.fieldName())) {
            localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
            ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                    "kof_ui_label_font_size", List.of(Type.PrimitiveType.INT),
                    Type.PrimitiveType.INT, KofCallKind.FUNCTION));
            return localIdx;
        }
        if (KofUi.isLabel(faType) && "bold".equals(fa.fieldName())) {
            localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
            ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                    "kof_ui_label_bold", List.of(Type.PrimitiveType.INT),
                    Type.PrimitiveType.BOOL, KofCallKind.FUNCTION));
            return localIdx;
        }
        if (KofUi.isLabel(faType) && "color".equals(fa.fieldName())) {
            localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
            ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                    "kof_ui_label_color", List.of(Type.PrimitiveType.INT),
                    Type.PrimitiveType.INT, KofCallKind.FUNCTION));
            return localIdx;
        }
        if (KofUi.isButton(faType) && "text".equals(fa.fieldName())) {
            localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
            ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                    "kof_ui_button_text", List.of(Type.PrimitiveType.INT),
                    BuiltinTypes.STRING, KofCallKind.FUNCTION));
            return localIdx;
        }
        if (KofUi.isInput(faType) && "text".equals(fa.fieldName())) {
            localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
            ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                    "kof_ui_input_text", List.of(Type.PrimitiveType.INT),
                    BuiltinTypes.STRING, KofCallKind.FUNCTION));
            return localIdx;
        }
        if (KofUi.isComponent(faType) && "state".equals(fa.fieldName())) {
            localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
            ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                    "kof_ui_component_state_get", List.of(Type.PrimitiveType.INT),
                    Type.PrimitiveType.INT, KofCallKind.FUNCTION));
            return localIdx;
        }
        Type recvType = ExpressionTyper.inferExprType(driver, fa.receiver(), locals);
        // #375/§355: acesso a membro em type-variable com bound resolve
        // no BOUND (getfield Animal.name, dono real) — espelha o typer.
        if (recvType instanceof Type.TypeVariable tvb && tvb.bound() != null
                && tvb.bound() instanceof Type.ClassType) {
            recvType = tvb.bound();
        }
        // narrowing de null-safety (`if (x != null) { x.length }`): o tipo do
        // receptor é o inner — antes emitia `getfield "?".length` para String?
        // (owner "?" inválido → erro de launcher/verificação no JVM).
        if (recvType instanceof Type.NullableType nt) recvType = nt.inner();
        if (BuiltinTypes.isList(recvType) && ("size".equals(fa.fieldName()) || "length".equals(fa.fieldName()))) {
            localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
            ops.add(new KofCall(recvType, "kof_list_size", List.of(), Type.PrimitiveType.INT, KofCallKind.INSTANCE));
            return localIdx;
        }
        // Map/Set `.size` propriedade (bug 14): antes caía no field-access
        // genérico → getfield HashMap.size → NoSuchFieldError em runtime.
        if (BuiltinTypes.isMap(recvType) && ("size".equals(fa.fieldName()) || "length".equals(fa.fieldName()))) {
            localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
            ops.add(new KofCall(recvType, "kof_map_size", List.of(), Type.PrimitiveType.INT, KofCallKind.INSTANCE));
            return localIdx;
        }
        if (BuiltinTypes.isSet(recvType) && ("size".equals(fa.fieldName()) || "length".equals(fa.fieldName()))) {
            localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
            ops.add(new KofCall(recvType, "kof_set_size", List.of(), Type.PrimitiveType.INT, KofCallKind.INSTANCE));
            return localIdx;
        }
        if (Type.isString(recvType) && ("name".equals(fa.fieldName()) || "path".equals(fa.fieldName()))) {
            localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
            return localIdx;
        }
        // enum constant access: Color.Red — instância de enum real
        if (recvType instanceof Type.ClassType ct
                && CompilerTypes.isEnumName(ct.name(), driver.currentUnit)) { // #445: pkg real aceito
            if (!CompilerTypes.enumConstantsOf(ct.name(), driver.currentUnit).contains(fa.fieldName())) {
                if (driver.currentDiagnostics != null) {
                    driver.currentDiagnostics.error(fa.position() != null ? fa.position().file() : "",
                            fa.position() != null ? fa.position().line() : 0,
                            fa.position() != null ? fa.position().column() : 0, 0,
                            "enum '" + ct.name() + "' has no constant '" + fa.fieldName() + "'",
                            "SEM030");
                }
                return localIdx;
            }
            ops.add(new KofGetStatic(recvType, fa.fieldName(), recvType));
            return localIdx;
        }
        // static field access: Class.field — no receiver on the stack
        if (recvType instanceof Type.ClassType ct
                && CompilerTypes.isEnumName(ct.name(), driver.currentUnit) && CompilerTypes.enumConstantsOf(ct.name(), driver.currentUnit).contains(fa.fieldName())) { // #445
            ops.add(new KofGetStatic(recvType, fa.fieldName(), recvType));
            return localIdx;
        }
        if (recvType instanceof Type.ClassType ct && driver.semanticAnalyzer != null) {
            SymbolTable.Symbol staticSym = HierarchyResolver.resolveFieldInHierarchy(ct.name(), fa.fieldName(), driver.semanticAnalyzer);
            if (staticSym instanceof SymbolTable.FieldSymbol fs
                    && (fs.accessFlags() & AccessFlags.STATIC) != 0) {
                ops.add(new KofGetStatic(recvType, fa.fieldName(), fs.type()));
                return localIdx;
            }
        }
        localIdx = ExpressionLowerer.emitExpression(driver, fa.receiver(), ops, owner, localIdx, locals);
        if (recvType instanceof Type.ArrayType && ("length".equals(fa.fieldName())
                || "size".equals(fa.fieldName()) || "count".equals(fa.fieldName()))) {
            // array.size/.length/.count → arraylength (property, sem
            // parênteses); sem isto vira getfield com owner "" no
            // constant pool → ClassFormatError (GitHub #30, mesma
            // causa do .get(i) no ExpressionInstanceCallLowerer).
            ops.add(new KofArrayLength());
        } else if (Type.isString(recvType) && "length".equals(fa.fieldName())) {
            ops.add(new KofLoadField(recvType, fa.fieldName(), Type.PrimitiveType.INT));
        } else {
            Type fieldType = Type.UnknownType.UNKNOWN;
            SymbolTable.Symbol accessor = null;
            if (recvType instanceof Type.ClassType ct && driver.semanticAnalyzer != null) {
                accessor = HierarchyResolver.resolveFieldInHierarchy(ct.name(), fa.fieldName(), driver.semanticAnalyzer);
                if (accessor != null) fieldType = accessor.type();
            }
            if (accessor instanceof SymbolTable.MethodSymbol ms && ms.parameterTypes().isEmpty()) {
                ops.add(new KofCall(recvType, fa.fieldName(), List.of(), ms.returnType(), KofCallKind.INSTANCE));
            } else {
                ops.add(new KofLoadField(recvType, fa.fieldName(), fieldType));
                // §245/#268: o descritor do campo genérico é apagado
                // (`T wrapped` → `Ljava/lang/Object;`), mas o tipo
                // EFETIVO vem do type-argument do receiver
                // (`Wrapper<Point>` → `Point`, `Wrapper<Int>` → `Int`).
                // O valor sai como Object: referência → checkcast;
                // primitivo → unbox. Sem o ajuste o próximo acesso
                // recebia Object na pilha → VerifyError.
                if (fieldType instanceof Type.TypeVariable && recvType instanceof Type.ClassType) {
                    Type eff = CompilerTypes.substituteTypeVariableIn(fieldType, recvType, driver.currentUnit);
                    Type ref = eff instanceof Type.NullableType nt2 ? nt2.inner() : eff;
                    if (TypeMetrics.isPrimitiveType(ref)) {
                        driver.emitErasureUnbox(ops, ref);
                    } else if (ref instanceof Type.ClassType rct && !"Object".equals(rct.name())) {
                        ops.add(new KofCheckCast(ref));
                    }
                }
            }
        }
        return localIdx;
    
    }
}
