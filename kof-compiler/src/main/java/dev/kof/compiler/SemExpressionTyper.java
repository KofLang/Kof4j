package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Inferência de tipos de expressões, extraída do SemanticAnalyzer
 * (REFACTOR-500 fase 6). Sem estado próprio — recebe o analyzer (estado
 * compartilhado) por parâmetro.
 */
final class SemExpressionTyper {

    private SemExpressionTyper() {}

    static Type inferType(SemanticAnalyzer sa, ExpressionNode expr, SymbolTable scope) {
        Type cached = sa.expressionTypes().get(expr);
        if (cached != null && !Type.isUnknown(cached)) return cached;
        Type result = inferTypeInternal(sa, expr, scope);
        sa.expressionTypes().put(expr, result);
        return result;
    }

    static boolean isLocalName(SymbolTable scope, String name) {
        if (scope == null) return false;
        return scope.resolve(name) != null;
    }

    /**
     * Define no escopo do case as variáveis de um pattern:
     * {@code case T v} → {@code v:T}; {@code case T(var x, var y)} → campos por
     * índice (record) ou por nome. Espelha a lógica do {@code SwitchStmt}.
     */
    private static void bindPatternVars(SemanticAnalyzer sa, PatternExpr pe, SymbolTable scope) {
        Type patType = MemberResolver.resolveType(sa, pe.typeName(), scope);
        if (patType == null) patType = Type.UnknownType.UNKNOWN;
        if (pe.varName() != null) {
            scope.define(new SymbolTable.LocalVariableSymbol(pe.varName(), patType, 0));
            return;
        }
        if (pe.fieldVars().isEmpty()) return;
        String simple = patType instanceof Type.ClassType ct ? ct.name() : pe.typeName();
        SymbolTable.ClassSymbol cls = sa.getClass(simple);
        java.util.List<String> fieldNames = pe.fieldVars();
        for (int i = 0; i < fieldNames.size(); i++) {
            String fv = fieldNames.get(i);
            Type fieldType = Type.UnknownType.UNKNOWN;
            if (cls != null) {
                var members = cls.members();
                java.util.List<SymbolTable.Symbol> fields = new java.util.ArrayList<>();
                for (var e : members.localSymbols().values()) {
                    if (e instanceof SymbolTable.FieldSymbol) fields.add(e);
                }
                if (fields.size() == fieldNames.size() && i < fields.size()) {
                    fieldType = fields.get(i).type();
                } else {
                    SymbolTable.Symbol sym = members.resolve(fv);
                    if (sym != null) fieldType = sym.type();
                    else {
                        for (AstNode d : sa.unit().declarations()) {
                            if (d instanceof RecordDeclarationNode rec && rec.name().equals(simple)) {
                                if (i < rec.components().size()) {
                                    fieldType = MemberResolver.resolveType(sa, rec.components().get(i).type(), scope);
                                }
                                break;
                            }
                        }
                    }
                }
            }
            scope.define(new SymbolTable.LocalVariableSymbol(fv, fieldType, 0));
        }
    }

    private static Type inferTypeInternal(SemanticAnalyzer sa, ExpressionNode expr, SymbolTable scope) {
        return switch (expr) {
            case PatternExpr pe -> {
                Type t = MemberResolver.resolveType(sa, pe.typeName(), scope);
                yield t != null ? t : Type.UnknownType.UNKNOWN;
            }
            case QueryDslExpr q -> {
                // Query DSL tipada: Entity.query(db) { ... } -> List<Entity>.
                // where/orderBy referenciam COLUNAS do schema (não variáveis
                // em escopo) — validadas no lowering; não inferir aqui (senão
                // SEM011 "undefined variable" nas colunas). dbArg e limit são
                // expressões reais.
                inferType(sa, q.dbArg(), scope);
                if (q.limit() != null) inferType(sa, q.limit(), scope);
                Type elem = MemberResolver.resolveType(sa, q.entityType(), scope);
                yield new Type.ClassType("kof", "List",
                        List.of(elem != null ? elem : Type.UnknownType.UNKNOWN));
            }
            case LiteralExpr lit -> TypeChecker.inferLiteralType(lit);
            case IdentifierExpr ie -> {
                SymbolTable.Symbol sym = scope.resolve(ie.name());
                if (sym != null) yield sym.type();
                if ("args".equals(ie.name()) && "main".equals(sa.currentFunctionName())) {
                    yield new Type.ArrayType(BuiltinTypes.STRING);
                }
                // constante de enum não-qualificada (rótulos de case, etc.):
                // Red → Color quando algum enum declara Red
                if (sa.unit() != null && !sa.allClasses().containsKey(ie.name())) {
                    for (AstNode d0 : sa.unit().declarations()) {
                        if (d0 instanceof EnumDeclarationNode en0
                                && en0.constants().contains(ie.name())) {
                            yield new Type.ClassType("", en0.name(), List.of());
                        }
                    }
                }
                if (sa.currentClassName() != null && !sa.currentClassName().isEmpty()) {
                    SymbolTable.Symbol fieldSym = MemberResolver.resolveInHierarchy(sa, sa.currentClassName(), ie.name());
                    if (fieldSym != null) {
                        sa.expressionTypes().put(ie, fieldSym.type());
                        yield fieldSym.type();
                    }
                }
                if (sa.diagnostics() != null && !"this".equals(ie.name()) && !"super".equals(ie.name())
                        && !"json".equals(ie.name()) && !"process".equals(ie.name())
                        && !KofWeb.isWebNamespace(ie.name())
                        && !KofConfig.isConfigNamespace(ie.name())
                        && !KofCache.isCacheNamespace(ie.name())
                        && !KofGpu.isGpuNamespace(ie.name())
                        && !KofDb.isDbNamespace(ie.name())
                        && !KofOrm.isOrmNamespace(ie.name())
                        && !KofLog.isLogNamespace(ie.name())
                        && !KofSecurity.isSecurityNamespace(ie.name())
                        && !KofValidation.isValidationNamespace(ie.name())
                        && !KofObservability.isObservabilityNamespace(ie.name())
                        && !KofHttp.isHttpNamespace(ie.name())
                        && !KofMq.isMqNamespace(ie.name())
                        && !KofTime.isTimeNamespace(ie.name())
                        && !KofScheduler.isSchedulerNamespace(ie.name())
                        && !KofWorkflow.isWorkflowNamespace(ie.name())
                        && !KofShell.isShellNamespace(ie.name())
                        && !KofTetris.isTetrisNamespace(ie.name())
                        && !KofMedia.isStaticNamespace(ie.name())
                        && !KofUi.isPalette(ie.name()) && !KofUi.isConstructor(ie.name())
                        && !KofUi.isRouterNamespace(ie.name())
                        && !"Theme".equals(ie.name())
                        && !MemberResolver.isBuiltinTypeName(ie.name())
                        && !sa.allClasses().containsKey(ie.name())) {
                    sa.diagnostics().error("", 0, 0, 0,
                            "Undefined variable or type: '" + ie.name() + "'", "SEM011");
                }
                yield Type.UnknownType.UNKNOWN;
            }
            case AssignmentExpr ae -> {
                // bug 12: assignment como VALOR de expressão (`var c = a = b`)
                // gerava bytecode inválido no JVM. Kof não tem assignment como
                // expressão — rejeita com diagnóstico limpo (statements passam
                // pelo ExpressionStmt, que não chega aqui).
                if (sa.diagnostics() != null) {
                    sa.diagnostics().error("", 0, 0, 0,
                            "atribuição é um statement, não uma expressão (use '=' em linha própria)",
                            "SEM027");
                }
                Type valueType = inferType(sa, ae.value(), scope);
                Type targetType = Type.UnknownType.UNKNOWN;
                if (ae.target() instanceof IdentifierExpr ie) {
                    SymbolTable.Symbol sym = scope.resolve(ie.name());
                    // SC1: atribuição a variável nunca declarada
                    if (sym == null && sa.diagnostics() != null) {
                        boolean hasField = false;
                        if (sa.currentClassName() != null) {
                            hasField = MemberResolver.resolveInHierarchy(sa, sa.currentClassName(), ie.name()) != null;
                        }
                        if (!hasField
                                && !"json".equals(ie.name()) && !"process".equals(ie.name())
                                && !KofWeb.isWebNamespace(ie.name())
                                && !KofConfig.isConfigNamespace(ie.name())
                                && !KofCache.isCacheNamespace(ie.name())
                                && !KofGpu.isGpuNamespace(ie.name())
                                && !KofDb.isDbNamespace(ie.name())
                                && !KofOrm.isOrmNamespace(ie.name())
                                && !KofLog.isLogNamespace(ie.name())
                                && !KofSecurity.isSecurityNamespace(ie.name())
                                && !KofValidation.isValidationNamespace(ie.name())
                                && !KofObservability.isObservabilityNamespace(ie.name())
                                && !KofHttp.isHttpNamespace(ie.name())
                                && !KofMq.isMqNamespace(ie.name())
                                && !KofTime.isTimeNamespace(ie.name())
                                && !KofScheduler.isSchedulerNamespace(ie.name())
                                && !sa.allClasses().containsKey(ie.name())) {
                            sa.diagnostics().error("", 0, 0, 0,
                                    "undefined variable: '" + ie.name() + "'", "SEM020");
                        }
                    }
                    if (sym != null) {
                        targetType = sym.type();
                        if (sa.diagnostics() != null && !Type.isUnknown(targetType) && !Type.isUnknown(valueType)
                                && !TypeChecker.isAssignable(valueType, targetType)) {
                            sa.diagnostics().error("", 0, 0, 0,
                                    "Type mismatch: cannot assign " + valueType + " to " + targetType, "SEM012");
                        }
                    }
                } else if (ae.target() instanceof FieldAccessExpr fa) {
                    targetType = inferType(sa, fa, scope);
                }
                yield targetType;
            }
            case BinaryExpr bin -> {
                // Left-associative chains (huge string concatenations in
                // generated UIs, editors) are iterated instead of recursed:
                // deep chains would overflow the compiler's own stack.
                java.util.List<BinaryExpr> chain = new ArrayList<>();
                ExpressionNode cursor = bin;
                while (cursor instanceof BinaryExpr be) {
                    chain.add(be);
                    cursor = be.left();
                }
                Type accType = inferType(sa, cursor, scope);
                for (int ci = chain.size() - 1; ci >= 0; ci--) {
                    BinaryExpr be = chain.get(ci);
                    Type rightType = inferType(sa, be.right(), scope);
                    accType = TypeChecker.inferBinaryResultType(sa.diagnostics(), be.operator(), accType, rightType);
                }
                yield accType;
            }
            case UnaryExpr ue -> {
                Type operandType = inferType(sa, ue.operand(), scope);
                if ("!".equals(ue.operator())) yield Type.PrimitiveType.BOOL;
                yield operandType;
            }
            case MethodCallExpr mc -> SemMethodCallTyper.infer(sa, mc, scope);
            case NewExpr ne -> {
                SymbolTable.ClassSymbol cs = sa.getClass(ne.typeName());
                if (cs != null) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : ne.arguments()) {
                        argTypes.add(inferType(sa, arg, scope));
                    }
                    SymbolTable.ConstructorSymbol ctor3 =
                            SymbolTable.constructorFor(cs.members(), ne.arguments().size());
                    if (ctor3 != null) {
                        sa.resolvedConstructors().put(ne, ctor3);
                    } else if (sa.diagnostics() != null) {
                        SymbolTable.Symbol anyInit = cs.members().resolve("<init>");
                        if (anyInit instanceof SymbolTable.ConstructorSymbol c) {
                            sa.diagnostics().error("", 0, 0, 0,
                                    "no constructor of '" + ne.typeName() + "' with "
                                            + ne.arguments().size() + " argument(s) (expected "
                                            + c.parameterTypes().size() + ")",
                                    "SEM023");
                        } else if (anyInit instanceof SymbolTable.ConstructorSet set
                                && !set.constructors().isEmpty()) {
                            sa.diagnostics().error("", 0, 0, 0,
                                    "no constructor of '" + ne.typeName() + "' with "
                                            + ne.arguments().size() + " argument(s)",
                                    "SEM023");
                        }
                    }
                    yield new Type.ClassType(cs.packageName(), cs.name(), List.of());
                }
                // classe EXTERNA (android.webkit.WebView etc.): qualifica pelo
                // import e registra o construtor do classpath — sem isso a
                // variável fica Unknown e toda a cadeia de chamadas seguinte
                // perde o tipo
                String qname = ne.typeName();
                if (!qname.contains(".")) {
                    Type viaImport = MemberResolver.qualifyViaImports(sa.unit(), qname);
                    if (viaImport != null) qname = viaImport instanceof Type.ClassType qt
                            ? qt.packageName() + "." + qt.name() : qname;
                }
                if (qname.contains(".") && sa.externalTypes() != null) {
                    String internal = qname.replace('.', '/');
                    if (sa.externalTypes().knows(internal)) {
                        ExternalClasspath.MethodSignature sig =
                                sa.externalTypes().resolveConstructor(internal, ne.arguments().size());
                        if (sig != null) {
                            List<Type> params = new ArrayList<>();
                            for (String d : sig.parameterDescriptors()) {
                                params.add(ExternalClasspath.typeFromDescriptor(d));
                            }
                            sa.resolvedConstructors().put(ne, new SymbolTable.ConstructorSymbol(
                                    internal.substring(internal.lastIndexOf('/') + 1), params, 1));
                        }
                        int lastDot = qname.lastIndexOf('.');
                        yield new Type.ClassType(qname.substring(0, lastDot),
                                qname.substring(lastDot + 1), List.of());
                    }
                }
                yield Type.UnknownType.UNKNOWN;
            }
            case FieldAccessExpr fa -> {
                if (fa.receiver() instanceof IdentifierExpr pId && KofUi.isPalette(pId.name())
                        && KofUi.paletteColor(fa.fieldName()) != null) {
                    yield KofUi.COLOR;
                }
                Type recvType = inferType(sa, fa.receiver(), scope);
                if (KofUi.isComponent(recvType) && "state".equals(fa.fieldName())) {
                    yield Type.PrimitiveType.INT;
                }
                if (KofProcess.isResult(recvType) && KofProcess.isField(fa.fieldName())) {
                    yield KofProcess.fieldType(fa.fieldName());
                }
                if (recvType instanceof Type.ArrayType at && "length".equals(fa.fieldName())) {
                    yield Type.PrimitiveType.INT;
                }
                if (Type.isString(recvType) && "length".equals(fa.fieldName())) {
                    yield Type.PrimitiveType.INT;
                }
                if (Type.isString(recvType) && ("name".equals(fa.fieldName()) || "path".equals(fa.fieldName()))) {
                    yield BuiltinTypes.STRING;
                }
                if (recvType instanceof Type.ClassType ct) {
                    SymbolTable.Symbol field = MemberResolver.resolveInHierarchy(sa, ct.name(), fa.fieldName());
                    if (field != null) yield field.type();
                    if (sa.isExternal(ct)) {
                        String desc = sa.externalTypes().resolveFieldType(ct.internalName(), fa.fieldName());
                        if (desc != null) {
                            yield ExternalClasspath.typeFromDescriptor(desc);
                        }
                    }
                }
                yield Type.UnknownType.UNKNOWN;
            }
            case NewArrayExpr na -> {
                Type elemType = Type.of(na.elementType());
                inferType(sa, na.size(), scope);
                yield new Type.ArrayType(elemType);
            }
            case ArrayAccessExpr aa -> {
                Type recvType = inferType(sa, aa.receiver(), scope);
                inferType(sa, aa.index(), scope);
                if (recvType instanceof Type.ArrayType at) {
                    yield at.componentType();
                }
                yield Type.UnknownType.UNKNOWN;
            }
            case LambdaExpr le -> {
                SymbolTable lambdaScope = scope.enterScope();
                List<Type> paramTypes = new ArrayList<>();
                int idx = 0;
                for (FormalParameterNode p : le.parameters()) {
                    Type paramType = MemberResolver.resolveType(sa, p.type(), scope);
                    paramTypes.add(paramType);
                    lambdaScope.define(new SymbolTable.ParameterSymbol(p.name(), paramType, idx));
                    idx++;
                }
                StatementAnalyzer.analyzeBody(sa, le.body(), lambdaScope, Type.UnknownType.UNKNOWN);
                Type returnType = Type.UnknownType.UNKNOWN;
                for (StatementNode s : le.body()) {
                    if (s instanceof ReturnStmt rs && rs.value() != null) {
                        returnType = inferType(sa, rs.value(), lambdaScope);
                        break;
                    }
                    if (s instanceof BlockStmt b) {
                        for (StatementNode inner : b.statements()) {
                            if (inner instanceof ReturnStmt rs2 && rs2.value() != null) {
                                returnType = inferType(sa, rs2.value(), lambdaScope);
                                break;
                            }
                        }
                    }
                }
                yield new Type.FunctionType(paramTypes, returnType);
            }
            case IfExpr ie -> {
                Type thenType = inferType(sa, ie.thenExpr(), scope);
                Type elseType = inferType(sa, ie.elseExpr(), scope);
                if (thenType.equals(elseType)) yield thenType;
                if (thenType instanceof Type.PrimitiveType && elseType instanceof Type.PrimitiveType) {
                    yield thenType;
                }
                yield thenType;
            }
            case SwitchExpr se -> {
                Type subjectType = inferType(sa, se.expression(), scope);
                Type result = Type.UnknownType.UNKNOWN;
                int armCount = 0;
                for (SwitchExprCase sc : se.cases()) {
                    SymbolTable caseScope = scope.enterScope();
                    if (sc.value() instanceof PatternExpr pe) {
                        bindPatternVars(sa, pe, caseScope);
                    } else {
                        inferType(sa, sc.value(), scope);
                    }
                    Type t = inferType(sa, sc.body(), caseScope);
                    if (armCount == 0) result = t;
                    armCount++;
                }
                if (se.defaultValue() != null) {
                    SymbolTable defaultScope = scope.enterScope();
                    inferType(sa, se.defaultValue(), defaultScope);
                } else {
                    // exaustividade: sem default, switch sobre enum precisa cobrir
                    // todas as constantes (mesma regra do statement, SEM031).
                    if (subjectType instanceof Type.ClassType sct && sct.packageName().isEmpty()
                            && sa.unit() != null) {
                        java.util.Set<String> covered = new java.util.HashSet<>();
                        for (SwitchExprCase sc : se.cases()) {
                            String cn = MemberResolver.enumConstantOfExpr(sa.unit(), sc.value());
                            if (cn != null) covered.add(cn);
                        }
                        List<String> constants = MemberResolver.enumConstantsOf(sa.unit(), sct.name());
                        List<String> missing = constants.stream().filter(c -> !covered.contains(c)).toList();
                        if (!missing.isEmpty()) {
                            sa.reportError(se, "switch expressão sobre '" + sct.name()
                                    + "' não cobre: " + String.join(", ", missing)
                                    + " (adicione default ou os casos faltantes)", "SEM032");
                        }
                    } else {
                        sa.reportError(se, "switch expressão exige 'default' (ou exaustividade de enum)", "SEM032");
                    }
                }
                yield result;
            }
            default -> Type.UnknownType.UNKNOWN;
        };
    }
}
