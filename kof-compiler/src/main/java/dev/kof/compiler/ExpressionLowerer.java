package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering de expressões (emitExpression) do CompilerDriver. Recebe o driver como host.
 */
public final class ExpressionLowerer {

    private ExpressionLowerer() {}

    static int emitExpression(CompilerDriver driver, ExpressionNode expr, List<KofOperation> ops, String owner, int localIdx,
                               List<IRLocalVariable> locals) {
        return switch (expr) {
            case LiteralExpr lit -> {
                switch (lit.kind()) {
                    case ConcreteLiteralKind.INT -> ops.add(KofLoadLiteral.ofInt(driver.parseIntLiteral(lit.value())));
                    case ConcreteLiteralKind.LONG -> ops.add(KofLoadLiteral.ofLong(driver.parseLongLiteral(lit.value())));
                    case ConcreteLiteralKind.FLOAT ->
                        ops.add(KofLoadLiteral.ofFloat(driver.parseFloatLiteral(lit.value())));
                    case ConcreteLiteralKind.DOUBLE ->
                        ops.add(KofLoadLiteral.ofDouble(driver.parseDoubleLiteral(lit.value())));
                    case ConcreteLiteralKind.STRING -> ops.add(KofLoadLiteral.ofString(lit.value()));
                    case ConcreteLiteralKind.BOOLEAN -> ops.add(KofLoadLiteral.ofBool(Boolean.parseBoolean(lit.value())));
                    case ConcreteLiteralKind.CHAR -> ops.add(KofLoadLiteral.ofInt(lit.value().charAt(0)));
                    case ConcreteLiteralKind.NULL -> ops.add(KofLoadLiteral.ofNull());
                    default -> throw new IllegalStateException("literal kind: " + lit.kind());
                }
                yield localIdx;
            }
            case IdentifierExpr ie -> {
                if (driver.loweringMain && "args".equals(ie.name())
                        && driver.findLocalVar(ie.name(), locals) == null) {
                    // #397: the implicit main-args intercept applies ONLY when
                    // the user did not declare `args` in main (a declared local
                    // wins — same §179 declared-beats-builtin-alias precedent as
                    // the field-vs-namespace guard #403; the SEM pass already
                    // resolves locals first, and the divergence here loaded
                    // slot 0 String[] over the user's local → silent garbage).
                    if (driver.mainArgsListField) {
                        // args: List<String> — the converted list lives in
                        // slot 1 (set by the main prologue)
                        ops.add(new KofLoadLocal(KofProcess.STRING_LIST, 1));
                    } else if (driver.target == Target.JVM) {
                        ops.add(new KofLoadLocal(new Type.ArrayType(BuiltinTypes.STRING), 0));
                    } else {
                        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
                        ops.add(new KofNewArray(BuiltinTypes.STRING));
                    }
                    yield localIdx;
                }
                // constante de enum não-qualificada → instância de enum real
                if (driver.currentUnit != null && driver.findLocalVar(ie.name(), locals) == null
                        && (driver.semanticAnalyzer == null || !driver.semanticAnalyzer.allClasses().containsKey(ie.name()))) {
                    for (AstNode d0 : driver.currentUnit.declarations()) {
                        if (d0 instanceof EnumDeclarationNode en0
                                && en0.constants().contains(ie.name())) {
                            Type enumT = CompilerTypes.enumTypeOf(en0.name(), driver.semanticAnalyzer); // #445
                            ops.add(new KofGetStatic(enumT, ie.name(), enumT));
                            yield localIdx;
                        }
                    }
                }
                for (int i = locals.size() - 1; i >= 0; i--) {
                    if (locals.get(i).name().equals(ie.name())) {
                        IRLocalVariable lv = locals.get(i);
                        if (driver.boxFactory.isBoxType(lv.type())) {
                            ops.add(new KofLoadLocal(lv.type(), lv.index()));
                            ops.add(new KofLoadField(lv.type(), "value",
                                    driver.boxFactory.boxValueType(lv.type())));
                        } else {
                            ops.add(new KofLoadLocal(lv.type(), lv.index()));
                        }
                        yield localIdx;
                    }
                }
                if (!owner.isEmpty() && driver.semanticAnalyzer != null) {
                    String className = owner.substring(owner.lastIndexOf('/') + 1);
                    SymbolTable.ClassSymbol cs = driver.semanticAnalyzer.getClass(className);
                    if (cs == null) {
                        for (var entry : driver.semanticAnalyzer.allClasses().entrySet()) {
                            if (entry.getValue().internalName().equals(owner)) { cs = entry.getValue(); break; }
                        }
                    }
                    if (cs != null) {
                        SymbolTable.Symbol fieldSym = HierarchyResolver.resolveFieldInHierarchy(cs.name(), ie.name(), driver.semanticAnalyzer);
                        if (fieldSym instanceof SymbolTable.FieldSymbol fs) {
                            // campo ESTÁTICO acessado por nome simples (ex.:
                            // `count` dentro de bump()): GETSTATIC — sem this.
                            // Emitir LoadLocal(0)+LoadField quebrava método
                            // estático (aload_0 sem this → VerifyError no
                            // compilado, recv null no interpretador).
                            if ((fs.accessFlags() & AccessFlags.STATIC) != 0) {
                                ops.add(new KofGetStatic(cs.type(), ie.name(), fs.type()));
                            } else {
                                ops.add(new KofLoadLocal(cs.type(), 0));
                                ops.add(new KofLoadField(cs.type(), ie.name(), fs.type()));
                            }
                            yield localIdx;
                        } else if (fieldSym instanceof SymbolTable.MethodSymbol ms
                                && ms.parameterTypes().isEmpty()) {
                            // Record/class-with-primary-constructor: the
                            // accessor method (kind()) shares the component
                            // field name (kind); a bare identifier refers to
                            // the field, not the accessor call.
                            ops.add(new KofLoadLocal(cs.type(), 0));
                            ops.add(new KofLoadField(cs.type(), ie.name(), ms.returnType()));
                            yield localIdx;
                        }
                    }
                }
                // Nome de TIPO builtin (String/Int/Long/…) como receiver de
                // método estático: não existe valor para empilhar — o KofCall
                // STATIC abaixo não consome receiver. Empilhar algo aqui
                // (o fallback aload_0 de antes) desalinha a pilha do call
                // (frame crash / VerifyError).
                if (driver.isBuiltinStaticReceiver(ie.name(), locals)) {
                    yield localIdx;
                }
                // Fallback (ex.: receiver `super` — slot 0 é o `this`):
                // empilhar com o tipo REAL da classe quando slot 0 é
                // referência de classe; UNKNOWN só onde não há `this`. O
                // store spill do lowerField recebe o valor já tipado.
                ops.add(new KofLoadLocal(
                        !locals.isEmpty() && locals.get(0).type() instanceof Type.ClassType
                                ? locals.get(0).type() : Type.UnknownType.UNKNOWN, 0));
                yield localIdx;
            }
            case BinaryExpr bin -> {
                yield ExpressionBinaryLowerer.lower(driver, bin, ops, owner, localIdx, locals);
            }
            case UnaryExpr ue -> {
                Type operandType = ExpressionTyper.inferExprType(driver, ue.operand(), locals);
                if ("++".equals(ue.operator()) || "--".equals(ue.operator())) {
                    localIdx = driver.emitIncrement(ue, operandType, ops, owner, localIdx, locals);
                    yield localIdx;
                }
                // D-TROOL: `!` de `Troolean` — Kleene (!U = U). O NOT cru do
                // operando boxed era VerifyError (JVM) / "not an int" (Script).
                if ("!".equals(ue.operator()) && CompilerComparisons.isNullableBool(operandType)) {
                    yield CompilerComparisons.lowerTrooleanNot(driver, ue, ops, owner, localIdx, locals);
                }
                localIdx = ExpressionLowerer.emitExpression(driver, ue.operand(), ops, owner, localIdx, locals);
                if ("-".equals(ue.operator())) {
                    ops.add(new KofUnary(KofUnaryOp.NEG, operandType));
                } else if ("!".equals(ue.operator())) {
                    ops.add(new KofUnary(KofUnaryOp.NOT, operandType));
                }
                yield localIdx;
            }
            case MethodCallExpr mc -> {
                yield ExpressionMethodCallLowerer.lower(driver, mc, ops, owner, localIdx, locals);
            }
            case AssignmentExpr ae -> {
                yield ExpressionAssignmentLowerer.lower(driver, ae, ops, owner, localIdx, locals);
            }
            case NewExpr ne -> {
                Type type = CompilerTypes.toType(ne.typeName(), driver.currentUnit,
                        driver.externalClasspath);
                Type collBuiltin = CompilerTypes.builtinCollectionType(ne.typeName(), driver.currentUnit, driver.semanticAnalyzer);
                if (collBuiltin == BuiltinTypes.LIST) {
                    type = BuiltinTypes.LIST;
                }
                if (!ne.typeArguments().isEmpty() && type instanceof Type.ClassType cts) {
                    type = new Type.ClassType(cts.packageName(), cts.name(),
                            ne.typeArguments().stream().map(n -> CompilerTypes.toType(n, driver.currentUnit)).toList());
                }
                if (BuiltinTypes.isList(type)) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : ne.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                    ops.add(new KofCall(BuiltinTypes.LIST, "kof_list_new", argTypes, BuiltinTypes.LIST, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                // #139/#150 — `new Set<T>()`/`new Map<K,V>()` são COLEÇÕES
                // (não classes JVM reais): baixam p/ kof_set_new/kof_map_new,
                // como setOf/mapOf. Sem isto caíam em KofNewObject com o nome
                // Kof (`kof/Set`/`kof/Map`) → NoClassDefFound/ClassFormatError.
                if (BuiltinTypes.isSet(type)) {
                    ops.add(new KofCall(type, "kof_set_new", List.of(), type, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (BuiltinTypes.isMap(type)) {
                    ops.add(new KofCall(type, "kof_map_new", List.of(), type, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                List<Type> argTypes = new ArrayList<>();
                for (ExpressionNode arg : ne.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                SymbolTable.ConstructorSymbol resolvedCtor = driver.semanticAnalyzer != null
                        ? driver.semanticAnalyzer.getResolvedConstructor(ne) : null;
                if (resolvedCtor == null && type instanceof Type.ClassType ct
                        && driver.semanticAnalyzer != null) {
                    // fallback: resolver por assignability quando o registro
                    // por identidade falhou (ex.: node recriado no desugar)
                    SymbolTable.ClassSymbol cs = driver.semanticAnalyzer.getClass(ct.name());
                    if (cs != null) {
                        SymbolTable.Symbol anyInit = cs.members().resolve("<init>");
                        if (anyInit instanceof SymbolTable.ConstructorSet set) {
                            for (SymbolTable.ConstructorSymbol c : set.constructors()) {
                                if (c.parameterTypes().size() == argTypes.size()) {
                                    boolean compatible = true;
                                    for (int ai = 0; ai < argTypes.size(); ai++) {
                                        if (!driver.ctorCompatible(c.parameterTypes().get(ai), argTypes.get(ai))) {
                                            compatible = false;
                                            break;
                                        }
                                    }
                                    if (compatible) { resolvedCtor = c; break; }
                                }
                            }
                        }
                    }
                }
                if (resolvedCtor == null && type instanceof Type.ClassType ct
                        && driver.semanticAnalyzer != null) {
                    SymbolTable.ClassSymbol cs2 = driver.semanticAnalyzer.getClass(ct.name());
                    if (cs2 != null) {
                        SymbolTable.Symbol anyInit2 = cs2.members().resolve("<init>");
                        if (anyInit2 instanceof SymbolTable.ConstructorSet set2) {
                            for (SymbolTable.ConstructorSymbol c : set2.constructors()) {
                                if (c.parameterTypes().size() == argTypes.size()) {
                                    resolvedCtor = c;
                                    break;
                                }
                            }
                        }
                    }
                }
                ops.add(new KofNewObject(type, argTypes));
                ops.add(new KofDup());
                List<Type> ctorParamTypes;
                if (resolvedCtor != null
                        && resolvedCtor.parameterTypes().size() == ne.arguments().size()) {
                    ctorParamTypes = resolvedCtor.parameterTypes();
                } else if (type instanceof Type.ClassType ct && !ct.packageName().isEmpty()
                        && driver.externalClasspath.knows(ct.internalName())) {
                    // construtor de classe externa: descritor exato do classpath
                    ExternalClasspath.MethodSignature extCtor =
                            driver.externalClasspath.resolveConstructor(ct.internalName(), ne.arguments().size());
                    if (extCtor != null) {
                        List<Type> formal = new ArrayList<>();
                        for (String d : extCtor.parameterDescriptors()) {
                            formal.add(ExternalClasspath.typeFromDescriptor(d));
                        }
                        ctorParamTypes = formal;
                    } else {
                        ctorParamTypes = argTypes;
                    }
                } else {
                    ctorParamTypes = argTypes;
                }
                localIdx = driver.emitArgumentsWithFormalTypes(ne.arguments(), ctorParamTypes, ops, owner, localIdx, locals);
                ops.add(new KofCall(type, "<init>", ctorParamTypes, Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
                yield localIdx;
            }
            case NewArrayExpr na -> {
                Type elemType = CompilerTypes.toType(na.elementType(), driver.currentUnit);
                localIdx = ExpressionLowerer.emitExpression(driver, na.size(), ops, owner, localIdx, locals);
                if (na.moreDims().isEmpty()) {
                    ops.add(new KofNewArray(elemType));
                } else {
                    // multidimensional: empilha as dimensões restantes e cria
                    // o array n-dimensional (bug 71 — antes só a 1ª dim era
                    // criada e o `[b]` virava index inválido → VerifyError)
                    for (ExpressionNode dim : na.moreDims()) {
                        localIdx = ExpressionLowerer.emitExpression(driver, dim, ops, owner, localIdx, locals);
                    }
                    ops.add(new KofNewMultiArray(elemType, na.moreDims().size() + 1));
                }
                yield localIdx;
            }
            case ArrayAccessExpr aa -> {
                localIdx = ExpressionLowerer.emitExpression(driver, aa.receiver(), ops, owner, localIdx, locals);
                 localIdx = ExpressionLowerer.emitExpression(driver, aa.index(), ops, owner, localIdx, locals);
                 Type recvType = ExpressionTyper.inferExprType(driver, aa.receiver(), locals);
                 // #152/#149: `list[i]` sobre List/Map NÃO é array — emitir
                 // KofArrayLoad gerava AALOAD sobre java/util/ArrayList →
                 // VerifyError (o receptor é referência, não array). Baixa p/
                 // as mesmas funções de runtime do `.get()`
                 // (CollectionCallLowerer: kof_list_get/kof_map_get, INSTANCE).
                 if (BuiltinTypes.isList(recvType) || BuiltinTypes.isMap(recvType)) {
                     String fn = BuiltinTypes.isList(recvType) ? "kof_list_get" : "kof_map_get";
                     // elemType real (idem CollectionCallLowerer) — o retorno
                     // Unknown fazia o desugar de println tratar Int como
                     // Object cru (VerifyError: integer na pilha do println).
                     Type elem = BuiltinTypes.isList(recvType)
                             ? driver.listElementType(recvType)
                             : (recvType instanceof Type.ClassType ct && ct.typeArguments().size() > 1
                                 ? ct.typeArguments().get(1) : Type.UnknownType.UNKNOWN);
                     // #150: o param do get é o ÍNDICE (List → Int) ou a CHAVE
                     // (Map → tipo da chave). Hardcodar INT boxava a chave String
                     // como Integer → VerifyError (`Integer.valueOf(String)`).
                     Type indexOrKey = BuiltinTypes.isList(recvType)
                             ? Type.PrimitiveType.INT
                             : BuiltinTypes.mapKey(recvType);
                     ops.add(new KofCall(recvType, fn,
                             List.of(indexOrKey), elem,
                             KofCallKind.INSTANCE));
                     yield localIdx;
                 }
                 // Set[i] não existe (R6): sem op de indexação — erro honesto.
                 if (BuiltinTypes.isSet(recvType)) {
                     if (driver.currentDiagnostics != null) {
                         var p = aa.position();
                         driver.currentDiagnostics.error(p != null ? p.file() : "",
                                 p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                                 "Set does not support indexing [i]; use contains(x) for "
                                         + "membership or keys() to iterate",
                                 "SEM025");
                     }
                     yield localIdx;
                 }
                 Type elemType = Type.arrayElementType(recvType);
                 ops.add(new KofArrayLoad(elemType));
                 yield localIdx;
            }
            case FieldAccessExpr fa -> ExpressionFieldAccessLowerer.emit(driver, fa, ops, owner,
                    localIdx, locals);
            case IfExpr ie -> {
                LabelId thenLabel = LabelId.create();
                LabelId elseLabel = LabelId.create();
                LabelId endLabel = LabelId.create();
                if (ie.condition() instanceof BinaryExpr bin && driver.isComparisonShortcut(bin, locals)) {
                    localIdx = driver.emitComparisonShortcut(bin, ops, owner, localIdx, locals);
                    ops.add(new KofConditionalJump(driver.mapComparison(bin.operator()), driver.comparisonOperandType(bin, locals), thenLabel, elseLabel));
                } else {
                    localIdx = CompilerComparisons.emitTruthinessJump(driver, ie.condition(),
                            ops, owner, localIdx, locals, thenLabel, elseLabel);
                }
                ops.add(new KofLabel(thenLabel));
                localIdx = ExpressionLowerer.emitExpression(driver, ie.thenExpr(), ops, owner, localIdx, locals);
                // #57/§70: ramos com tipos distintos — cada ramo primitivo é
                // boxeado p/ SEU boxed aqui dentro (join só de referências);
                // os callers pulam o pós-box (mesmo predicado, sem canal).
                List<Type> bts = ie.elseExpr() != null
                        ? ExpressionTyper.ifBranchTypes(driver, ie, locals) : List.of();
                boolean differ = !bts.isEmpty() && ExpressionTyper.branchTypesDiffer(bts);
                if (differ) ExpressionTyper.boxPrimitiveBranch(driver, ops, bts.get(0));
                ops.add(new KofJump(endLabel));
                ops.add(new KofLabel(elseLabel));
                localIdx = ExpressionLowerer.emitExpression(driver, ie.elseExpr(), ops, owner, localIdx, locals);
                if (differ) ExpressionTyper.boxPrimitiveBranch(driver, ops, bts.get(1));
                ops.add(new KofLabel(endLabel));
                yield localIdx;
            }
            case SwitchExpr se -> {
                localIdx = SwitchExprLowerer.emitSwitchExpr(driver, se, ops, owner, localIdx, locals);
                yield localIdx;
            }
            case LambdaExpr le -> {
                Type.FunctionType ft = (Type.FunctionType) ExpressionTyper.inferExprType(driver, le, locals);
                List<IRLocalVariable> captures = driver.collectCaptures(le, locals);
                String lambdaClass = driver.lambdaClass(le, ft, captures);
                List<IRLocalVariable> effective = driver.lambdaEffectiveCaptures.get(le);
                if (effective != null) captures = effective;
                if (ft.className() == null) {
                    ft = new Type.FunctionType(ft.parameterTypes(), ft.returnType(), lambdaClass);
                }
                Type lambdaType = new Type.ClassType("", lambdaClass, List.of());
                List<Type> captureTypes = new ArrayList<>();
                for (IRLocalVariable cap : captures) captureTypes.add(cap.type());
                ops.add(new KofNewObject(lambdaType, captureTypes));
                ops.add(new KofDup());
                for (IRLocalVariable cap : captures) {
                    CompilerCaptures.pushCapture(driver, ops, cap);
                }
                ops.add(new KofCall(lambdaType, "<init>", captureTypes,
                        Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
                yield localIdx;
            }
            case QueryDslExpr q -> {
                yield driver.lowerQueryDsl(q, ops, owner, localIdx, locals);
            }
            default -> localIdx;
        };
    }
}