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
                FieldAccessExpr outerFa = CompilerCaptures.outerField(driver, ie, locals);
                if (outerFa != null) {
                    yield emitExpression(driver, outerFa, ops, owner, localIdx, locals);
                }
                if (!owner.isEmpty() && driver.semanticAnalyzer != null) {
                    String className = owner.substring(owner.lastIndexOf('/') + 1);
                    // #639 face 2 (D-DECISION-BATCH-2709B): o `owner` é o nome
                    // INTERNO ("p1/Item") — resolve o pacote primeiro pelo índice
                    // FQN (o `getClass(className)` simples colide e devolvia o
                    // last-write, emitindo getfield do owner ERRADO → VerifyError).
                    SymbolTable.ClassSymbol cs = driver.semanticAnalyzer
                            .findQualifiedClass(owner.replace('/', '.'));
                    if (cs == null) cs = driver.semanticAnalyzer.getClass(className);
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
                                // §725: o campo pode estar DECLARADO numa
                                // interface (ou superclasse) — o dono emitido
                                // tem de ser a classe DECLARANTE, senão Script/
                                // JS/nativo procuram o estático na classe ATUAL
                                // e não o acham (o JVM resolve pela hierarquia /
                                // inlina a constante). `cs.type()` (a classe
                                // atual) só quando o campo é dela.
                                Type staticOwner = cs.type();
                                if (fs.ownerClass() != null && !fs.ownerClass().equals(cs.name())) {
                                    SymbolTable.ClassSymbol decl = driver.semanticAnalyzer.getClass(fs.ownerClass());
                                    if (decl != null) staticOwner = decl.type();
                                }
                                ops.add(new KofGetStatic(staticOwner, ie.name(), fs.type()));
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
                // §582: a bare ctor name of a PACKAGED record inside an IMPORTED
                // module resolves here against the merged MAIN unit (the caller),
                // which does not declare the type — the owner reached the emitter
                // BARE and the JVM died at LOAD (NoClassDefFoundError: <Name>),
                // while Script died interpreting the same bare owner. Requalify
                // through the §571/§573 analyzer chokepoint (idempotent elsewhere).
                Type type = ExpressionTyper.qualifyBareModuleType(driver,
                        CompilerTypes.toType(ne.typeName(), driver.currentUnit,
                                driver.externalClasspath));
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
                    SymbolTable.ClassSymbol cs = driver.semanticAnalyzer.getClass(ct.name());
                    if (cs != null) {
                        resolvedCtor = SymbolTable.constructorFor(cs.members(), argTypes.size(), argTypes);
                    }
                }
                if (resolvedCtor != null && !resolvedCtor.acceptsArgumentCount(argTypes.size())) {
                    resolvedCtor = null;
                }
                // #760: `new <imported-external-class>(...)` is a JVM-backed
                // face; JS/Native must refuse with INTEROP003 instead of leaking
                // the java_* ctor (Native ld undefined reference / JS
                // ReferenceError). Wrapper owners keep their shims.
                if (type instanceof Type.ClassType nct && !nct.packageName().isEmpty()
                        && driver.externalClasspath.knows(nct.internalName())
                        && JvmInteropTargetGap.refuses(driver.target)
                        && !JvmInteropTargetGap.isShimmedOwner(nct.internalName())) {
                    JvmInteropTargetGap.refuse(driver, ne.position(),
                            "constructor of external class '"
                                    + nct.internalName().replace('/', '.') + "'");
                    yield localIdx;
                }
                ops.add(new KofNewObject(type, argTypes));
                ops.add(new KofDup());
                List<Type> ctorParamTypes;
                if (resolvedCtor != null) {
                    ctorParamTypes = resolvedCtor.effectiveParameterTypes(ne.arguments().size());
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
                 // #882: o receptor ESTREITADO (`if (l != null)`) segue `Nullable(...)` no
                 // IR (o estreitamento é semântico) — a classificação e a extração do
                 // elemento olham o tipo SEM a anulabilidade; senão um `List<T>?` cai no
                 // caminho de ARRAY (`aaload`/`arraylength` sobre ArrayList → VerifyError).
                 Type recvBase = Type.withoutNullable(recvType);
                 // #845: índice `Int?` estreitado de array/List (a chave de Map é referência)
                 if (!BuiltinTypes.isMap(recvBase)) {
                     CompilerEmissionHelpers.emitNarrowedNullableUnbox(driver, ops,
                             ExpressionTyper.inferExprType(driver, aa.index(), locals), Type.PrimitiveType.INT);
                 }
                 // #152/#149: `list[i]` sobre List/Map NÃO é array — emitir
                 // KofArrayLoad gerava AALOAD sobre java/util/ArrayList →
                 // VerifyError (o receptor é referência, não array). Baixa p/
                 // as mesmas funções de runtime do `.get()`
                 // (CollectionCallLowerer: kof_list_get/kof_map_get, INSTANCE).
                 if (BuiltinTypes.isList(recvBase) || BuiltinTypes.isMap(recvBase)) {
                     String fn = BuiltinTypes.isList(recvBase) ? "kof_list_get" : "kof_map_get";
                     // elemType real (idem CollectionCallLowerer) — o retorno
                     // Unknown fazia o desugar de println tratar Int como
                     // Object cru (VerifyError: integer na pilha do println).
                     Type elem = BuiltinTypes.isList(recvBase)
                             ? driver.listElementType(recvBase)
                             : (recvBase instanceof Type.ClassType ct && ct.typeArguments().size() > 1
                                 ? ct.typeArguments().get(1) : Type.UnknownType.UNKNOWN);
                     // #150: o param do get é o ÍNDICE (List → Int) ou a CHAVE
                     // (Map → tipo da chave). Hardcodar INT boxava a chave String
                     // como Integer → VerifyError (`Integer.valueOf(String)`).
                     Type indexOrKey = BuiltinTypes.isList(recvBase)
                             ? Type.PrimitiveType.INT
                             : BuiltinTypes.mapKey(recvBase);
                     ops.add(new KofCall(recvBase, fn,
                             List.of(indexOrKey), elem,
                             KofCallKind.INSTANCE));
                     yield localIdx;
                 }
                 // Set[i] não existe (R6): sem op de indexação — erro honesto.
                 if (BuiltinTypes.isSet(recvBase)) {
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
                // #870: a unificação usa o tipo EFETIVO do ramo (com o narrowing da
                // condição), mas quem decide box/unbox é a REPRESENTAÇÃO FÍSICA: um
                // `Int?` já está boxeado (D-NULL-INTENT) e só ABRE quando o join
                // unifica no primitivo; um primitivo cru só é BOXEADO quando o join
                // unifica em referências (ramos heterogêneos) — #57/§70.
                List<Type> bts = ie.elseExpr() != null
                        ? ExpressionBranchJoin.ifBranchTypes(driver, ie, locals) : List.of();
                boolean differ = !bts.isEmpty() && ExpressionBranchJoin.branchTypesDiffer(bts);
                Type thenPhys = ExpressionTyper.inferExprType(driver, ie.thenExpr(), locals);
                Type elsePhys = ExpressionTyper.inferExprType(driver, ie.elseExpr(), locals);
                // Fase E (26/10, #776): o JOIN do if-expression vai para um
                // TEMPORARIO quando os dois ramos convergem no MESMO tipo — o
                // valor nao pode atravessar a fronteira de bloco (a maquina de
                // estados do WASI nao transporta a pilha entre estados: o
                // frontend tem de comportar IGUAL em todos os backends). Mesma
                // maquina de temporario unico do troolean e das cadeias logicas.
                Type joinT = null;
                // O alvo JS dobra o shape de if-expression por PARSER proprio
                // (stores entre os arcos quebram a deteccao — mesmo achado do
                // troolean 19/09); la o caminho antigo de pilha fica.
                if (driver.target != Target.JS) {
                    if (!differ && !bts.isEmpty() && bts.get(0).equals(bts.get(1))) {
                        joinT = bts.get(0);
                    } else if (!differ && bts.isEmpty() && elsePhys != null && thenPhys.equals(elsePhys)) {
                        joinT = thenPhys;
                    }
                }
                // Fase E (27/10, #776): o join HETEROGENEO no WASI — o LUB
                // unifica em Object com os ramos primitivos BOXEADOS (contrato
                // §284, terceiro backend; o bytecode JVM mede Integer.valueOf
                // + String.valueOf). Sem o temporario o valor cru/boxeado
                // atravessa a fronteira de bloco e a maquina de estados recusa
                // (refuseLiveStack) — a familia INTEIRA (`if (c) 1 else "x"`)
                // estava fechada no WASI. O temp do LUB engata a MESMA maquina
                // de stores/loads do join homogeneo (logo abaixo).
                if (joinT == null && differ && driver.target == Target.WASI) {
                    Type lub = ExpressionTyper.inferExprType(driver, ie, locals);
                    if (lub instanceof Type.ClassType) {
                        joinT = lub;
                    }
                }
                int tmpIdx = -1;
                if (joinT != null) {
                    tmpIdx = Math.max(localIdx, CompilerComparisons.nextFreeLocalIndex(locals));
                    locals.add(new IRLocalVariable(tmpIdx, "$kif" + tmpIdx, joinT));
                    localIdx = tmpIdx + 1;
                }
                localIdx = ExpressionLowerer.emitExpression(driver, ie.thenExpr(), ops, owner, localIdx, locals);
                if (differ) {
                    ExpressionTyper.boxPrimitiveBranch(driver, ops, thenPhys);
                    emitWasiBox(driver, ops, thenPhys);
                } else if (!bts.isEmpty()) {
                    CompilerEmissionHelpers.emitNarrowedNullableUnbox(driver, ops, thenPhys, bts.get(0));
                }
                if (joinT != null) ops.add(new KofStoreLocal(joinT, tmpIdx));
                ops.add(new KofJump(endLabel));
                ops.add(new KofLabel(elseLabel));
                localIdx = ExpressionLowerer.emitExpression(driver, ie.elseExpr(), ops, owner, localIdx, locals);
                if (differ) {
                    ExpressionTyper.boxPrimitiveBranch(driver, ops, elsePhys);
                    emitWasiBox(driver, ops, elsePhys);
                } else if (!bts.isEmpty()) {
                    CompilerEmissionHelpers.emitNarrowedNullableUnbox(driver, ops, elsePhys, bts.get(1));
                }
                if (joinT != null) ops.add(new KofStoreLocal(joinT, tmpIdx));
                ops.add(new KofLabel(endLabel));
                if (joinT != null) ops.add(new KofLoadLocal(joinT, tmpIdx));
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
                CompilerLambdaClass.propagateCapturePins(driver, lambdaClass, captures, locals);
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

    /**
     * Fase E (27/10, #776): a CAIXA WASI do ramo heterogeneo — o MESMO
     * contrato da nativa §284 ({@code kof_box_*}, 24B [MAGIC][tag][valor]).
     * O JVM/native boxeiam via {@code emitErasureBox} (gate needsErasureBoxing);
     * o WASI NAO boxeia globalmente (o subset escalar nao tem caixa nos
     * consumidores legados) — so o join heterogeneo do if-expression boxea,
     * aqui, explicitamente. Ramo referencia (String/record): sem box — o
     * handle passa cru e o MAGIC e o que distingue no despacho.
     */
    private static void emitWasiBox(CompilerDriver driver, List<KofOperation> ops, Type branchT) {
        if (driver.target != Target.WASI) return;
        if (!(branchT instanceof Type.PrimitiveType pt) || TypeMetrics.isNullablePrimitive(branchT)) return;
        String name = switch (pt.name().toLowerCase()) {
            case "int", "char", "short", "byte" -> "kof_box_int";
            case "long" -> "kof_box_long";
            case "bool", "boolean" -> "kof_box_bool";
            case "double", "float" -> "kof_box_double";
            default -> null;
        };
        if (name == null) return;
        Type boxed = TypeMetrics.boxedTypeFor(branchT);
        // O Char boxa como INT no contrato §284 (mesma largura i64 na pilha)
        ops.add(new KofCall(boxed, name,
                List.of(Type.PrimitiveType.INT), boxed, KofCallKind.FUNCTION));
    }
}