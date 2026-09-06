package dev.kof.compiler;

import java.util.List;

/**
 * Análise de statements/corpos, extraída do SemanticAnalyzer
 * (REFACTOR-500 fase 6). Mantém a ordem exata dos diagnósticos SEM0xx.
 */
final class StatementAnalyzer {

    private StatementAnalyzer() {}

    static void analyzeBody(SemanticAnalyzer sa, List<StatementNode> body, SymbolTable scope, Type returnType) {
        for (StatementNode stmt : body) {
            analyzeStatement(sa, stmt, scope, returnType);
        }
    }

    /**
     * Assignment como STATEMENT (`a = b`, `i = i + 1` no update do for):
     * infere alvo/valor e valida assignability (SEM012) SEM emitir o SEM027
     * (que é reservado para assignment usado como VALOR — bug 12).
     */
    static Type analyzeAssignmentStatement(SemanticAnalyzer sa, AssignmentExpr ae, SymbolTable scope) {
        Type valueType = SemExpressionTyper.inferType(sa, ae.value(), scope);
        Type targetType = Type.UnknownType.UNKNOWN;
        if (ae.target() instanceof IdentifierExpr ie) {
            SymbolTable.Symbol sym = scope.resolve(ie.name());
            if (sym != null) {
                targetType = sym.type();
                if (sa.diagnostics() != null && !Type.isUnknown(targetType)
                        && !Type.isUnknown(valueType)
                        && !TypeChecker.isAssignable(valueType, targetType)) {
                    sa.diagnostics().error("", 0, 0, 0,
                            "Type mismatch: cannot assign " + valueType + " to " + targetType,
                            "SEM012");
                }
            } else {
                targetType = SemExpressionTyper.inferType(sa, ae.target(), scope);
            }
        } else if (ae.target() != null) {
            targetType = SemExpressionTyper.inferType(sa, ae.target(), scope);
        }
        return targetType;
    }

    static void analyzeStatement(SemanticAnalyzer sa, StatementNode stmt, SymbolTable scope, Type returnType) {
        switch (stmt) {
            case BlockStmt block -> {
                SymbolTable blockScope = scope.enterScope();
                for (StatementNode s : block.statements()) analyzeStatement(sa, s, blockScope, returnType);
            }
            case TryStmt tryStmt -> {
                // corpos de try/catch/finally eram ignorados pela análise
                // semântica (ex.: `throw 42` dentro de try passava e gerava
                // bytecode inválido). Analisa os três blocos.
                SymbolTable tryScope = scope.enterScope();
                for (StatementNode s : tryStmt.tryBody()) analyzeStatement(sa, s, tryScope, returnType);
                for (CatchClause cc : tryStmt.catchClauses()) {
                    SymbolTable catchScope = scope.enterScope();
                    if (cc.exceptionName() != null) {
                        Type excType = "String".equals(cc.exceptionType()) ? BuiltinTypes.STRING
                                : Type.of(cc.exceptionType());
                        catchScope.define(new SymbolTable.LocalVariableSymbol(cc.exceptionName(), excType, 0));
                    }
                    for (StatementNode s : cc.body()) analyzeStatement(sa, s, catchScope, returnType);
                }
                if (tryStmt.finallyBody() != null) {
                    SymbolTable finScope = scope.enterScope();
                    for (StatementNode s : tryStmt.finallyBody()) analyzeStatement(sa, s, finScope, returnType);
                }
            }
            case VarDeclStmt vds -> {
                Type varType;
                if (vds.type() != null && !vds.type().isEmpty() && !"var".equals(vds.type())) {
                    Type viaImports = MemberResolver.qualifyViaImports(sa.unit(), vds.type());
                    varType = viaImports != null ? viaImports : Type.of(vds.type());
                } else if (vds.initializer() != null) {
                    varType = SemExpressionTyper.inferType(sa, vds.initializer(), scope);
                } else {
                    varType = Type.UnknownType.UNKNOWN;
                }
                // SC5: redeclaração no MESMO escopo é erro
                if (scope.hasLocal(vds.name()) && sa.diagnostics() != null) {
                    sa.diagnostics().error("", 0, 0, 0,
                            "variable '" + vds.name() + "' is already defined in this scope",
                            "SEM024");
                }
                if (vds.initializer() != null) SemExpressionTyper.inferType(sa, vds.initializer(), scope);
                // SC2: tipo explícito ≠ tipo do inicializador
                if (sa.diagnostics() != null && vds.initializer() != null
                        && !varType.equals(Type.UnknownType.UNKNOWN)) {
                    Type initType = SemExpressionTyper.inferType(sa, vds.initializer(), scope);
                    if (!initType.equals(Type.UnknownType.UNKNOWN)
                            && !TypeChecker.isAssignable(initType, varType)
                            && !(initType instanceof Type.FunctionType)
                            && !(varType instanceof Type.FunctionType)) {
                        sa.diagnostics().error("", 0, 0, 0,
                                "type mismatch: cannot assign " + initType
                                        + " to '" + vds.name() + ": " + varType + "'",
                                "SEM021");
                    }
                }
                scope.define(new SymbolTable.LocalVariableSymbol(vds.name(), varType, 0));
            }
            case ReturnStmt ret -> {
                if (ret.value() != null) {
                    Type valueType = SemExpressionTyper.inferType(sa, ret.value(), scope);
                    sa.expressionTypes().put(ret.value(), valueType);
                    if (sa.diagnostics() != null && !Type.isUnknown(returnType) && !Type.isVoid(returnType)
                            && !Type.isUnknown(valueType) && !TypeChecker.isAssignable(valueType, returnType)) {
                        sa.diagnostics().error("", 0, 0, 0,
                                "Return type mismatch: expected " + returnType + " but got " + valueType, "SEM010");
                    }
                }
            }
            case BreakStmt ignored -> {}
            case ContinueStmt ignored -> {}
            case IfStmt ifStmt -> {
                Type condType = SemExpressionTyper.inferType(sa, ifStmt.condition(), scope);
                // Nullability narrowing: if (x != null) { x: T } where x: T?
                SymbolTable ifScope = scope.enterScope();
                if (ifStmt.condition() instanceof BinaryExpr be && "!=".equals(be.operator())
                        && be.left() instanceof IdentifierExpr ie
                        && be.right() instanceof LiteralExpr le && le.kind() == ConcreteLiteralKind.NULL) {
                    SymbolTable.Symbol sym = scope.resolve(ie.name());
                    if (sym != null && sym.type() instanceof Type.NullableType nt) {
                        ifScope.define(new SymbolTable.LocalVariableSymbol(ie.name(), nt.inner(), 0));
                    }
                } else if (ifStmt.condition() instanceof BinaryExpr be2 && "!=".equals(be2.operator())
                        && be2.right() instanceof IdentifierExpr ie2
                        && be2.left() instanceof LiteralExpr le2 && le2.kind() == ConcreteLiteralKind.NULL) {
                    SymbolTable.Symbol sym2 = scope.resolve(ie2.name());
                    if (sym2 != null && sym2.type() instanceof Type.NullableType nt2) {
                        ifScope.define(new SymbolTable.LocalVariableSymbol(ie2.name(), nt2.inner(), 0));
                    }
                }
                analyzeStatement(sa, ifStmt.thenBranch(), ifScope, returnType);
                if (ifStmt.elseBranch() != null) analyzeStatement(sa, ifStmt.elseBranch(), scope, returnType);
            }
            case WhileStmt ws -> {
                SemExpressionTyper.inferType(sa, ws.condition(), scope);
                SymbolTable whileScope = scope.enterScope();
                analyzeStatement(sa, ws.body(), whileScope, returnType);
            }
            case DoWhileStmt dws -> {
                SymbolTable doScope = scope.enterScope();
                analyzeStatement(sa, dws.body(), doScope, returnType);
                SemExpressionTyper.inferType(sa, dws.condition(), doScope);
            }
            case ForStmt fs -> {
                SymbolTable forScope = scope.enterScope();
                if (fs.init() != null) analyzeStatement(sa, fs.init(), forScope, returnType);
                if (fs.condition() != null) SemExpressionTyper.inferType(sa, fs.condition(), forScope);
                analyzeStatement(sa, fs.body(), forScope, returnType);
                if (fs.update() != null) {
                    // `i = i + 1` no update é statement, não valor
                    if (fs.update() instanceof AssignmentExpr ae) {
                        SemExpressionTyper.inferType(sa, ae.value(), forScope);
                        if (ae.target() != null) SemExpressionTyper.inferType(sa, ae.target(), forScope);
                    } else {
                        SemExpressionTyper.inferType(sa, fs.update(), forScope);
                    }
                }
            }
            case ForInStmt fis -> {
                SymbolTable forScope = scope.enterScope();
                Type collType = SemExpressionTyper.inferType(sa, fis.collection(), forScope);
                Type elemType = Type.UnknownType.UNKNOWN;
                if (collType instanceof Type.ClassType ct && "List".equals(ct.name()) && !ct.typeArguments().isEmpty()) {
                    elemType = ct.typeArguments().get(0);
                } else if (collType instanceof Type.ArrayType at) {
                    elemType = at.componentType();
                }
                forScope.define(new SymbolTable.LocalVariableSymbol(fis.varName(), elemType, 0));
                analyzeStatement(sa, fis.body(), forScope, returnType);
            }
            case SwitchStmt ss -> {
                SemExpressionTyper.inferType(sa, ss.expression(), scope);
                SymbolTable switchScope = scope.enterScope();
                for (SwitchCase sc : ss.cases()) {
                    if (sc.value() instanceof PatternExpr pe) {
                        Type patType = MemberResolver.resolveType(sa, pe.typeName(), scope);
                        if (patType == null) patType = Type.UnknownType.UNKNOWN;
                        SymbolTable caseScope = switchScope.enterScope();
                        if (pe.varName() != null) {
                            caseScope.define(new SymbolTable.LocalVariableSymbol(pe.varName(), patType, 0));
                        }
                        if (!pe.fieldVars().isEmpty()) {
                            String simple = patType instanceof Type.ClassType ct ? ct.name() : pe.typeName();
                            SymbolTable.ClassSymbol cls = sa.getClass(simple);
                            if (cls == null) {
                                // Try via scope resolve
                                Type t2 = MemberResolver.resolveType(sa, pe.typeName(), scope);
                                if (t2 instanceof Type.ClassType ct2) cls = sa.getClass(ct2.name());
                            }
                            java.util.List<String> fieldNames = pe.fieldVars();
                            for (int i = 0; i < fieldNames.size(); i++) {
                                String fv = fieldNames.get(i);
                                Type fieldType = Type.UnknownType.UNKNOWN;
                                if (cls != null) {
                                    // Try to find field by index or name
                                    var members = cls.members();
                                    // For records, fields are in order; try to get by index
                                    java.util.List<SymbolTable.Symbol> fields = new java.util.ArrayList<>();
                                    for (var e : members.localSymbols().values()) {
                                        if (e instanceof SymbolTable.FieldSymbol) fields.add(e);
                                    }
                                    // If fieldVars size matches record field count, use positional
                                    if (fields.size() == fieldNames.size() && i < fields.size()) {
                                        fieldType = fields.get(i).type();
                                    } else {
                                        SymbolTable.Symbol sym = members.resolve(fv);
                                        if (sym != null) fieldType = sym.type();
                                        else {
                                            // Try by field name from record declaration
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
                                caseScope.define(new SymbolTable.LocalVariableSymbol(fv, fieldType, 0));
                            }
                        }
                        analyzeBody(sa, sc.body(), caseScope, returnType);
                    } else {
                        SemExpressionTyper.inferType(sa, sc.value(), scope);
                        SymbolTable caseScope = switchScope.enterScope();
                        analyzeBody(sa, sc.body(), caseScope, returnType);
                    }
                }
                if (!ss.defaultBody().isEmpty()) {
                    SymbolTable defaultScope = switchScope.enterScope();
                    analyzeBody(sa, ss.defaultBody(), defaultScope, returnType);
                }
            }
            case ExpressionStmt es -> {
                if (es.expression() != null) {
                    // `a = b` como STATEMENT é legítimo: check de assignability
                    // (SEM012) sem o SEM027 (que é só para uso como VALOR —
                    // bug 12). Mesmo helper usado pelo update do for.
                    if (es.expression() instanceof AssignmentExpr ae) {
                        sa.expressionTypes().put(es.expression(),
                                analyzeAssignmentStatement(sa, ae, scope));
                    } else {
                        Type exprType = SemExpressionTyper.inferType(sa, es.expression(), scope);
                        sa.expressionTypes().put(es.expression(), exprType);
                    }
                }
            }
            case ThrowStmt ts -> {
                if (ts.expression() != null) {
                    Type t = SemExpressionTyper.inferType(sa, ts.expression(), scope);
                    // bug 1: `throw <não-String>` gerava bytecode inválido no
                    // JVM (wrap em RuntimeException assumindo String). Exceções
                    // são Strings em Kof — rejeita com diagnóstico limpo.
                    if (sa.diagnostics() != null && t != null && !Type.isUnknown(t)
                            && !BuiltinTypes.isString(t)) {
                        sa.diagnostics().error("", 0, 0, 0,
                                "throw exige uma String (exceções são Strings em Kof),"
                                        + " recebeu " + Type.canonicalPrimitiveName(
                                        t instanceof Type.ClassType ct ? ct.name() : t.toString()),
                                "SEM026");
                    }
                }
            }
            case SpawnStmt ss -> {
                if (ss.expression() != null) SemExpressionTyper.inferType(sa, ss.expression(), scope);
            }
            case AssertStmt asrt -> {
                if (asrt.condition() != null) SemExpressionTyper.inferType(sa, asrt.condition(), scope);
            }
            default -> {}
        }
    }
}
