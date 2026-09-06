package dev.kof.compiler;

import java.util.ArrayList;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Suporte ORM/Query DSL: super-bridges, validação de campos, SQL em compile-time.
 */
final class CompilerOrmSupport {

    private CompilerOrmSupport() {}

    static IRModule applySuperBridges(CompilerDriver driver, IRModule module) {
        if (driver.pendingSuperBridges.isEmpty()) return module;
        List<IRClass> classes = new ArrayList<>();
        for (IRClass clazz : module.classes()) {
            List<IRMethod> bridges = driver.pendingSuperBridges.get(clazz.name());
            if (bridges == null) {
                classes.add(clazz);
                continue;
            }
            List<IRMethod> methods = new ArrayList<>(clazz.methods());
            methods.addAll(bridges);
            classes.add(new IRClass(clazz.name(), clazz.superName(), clazz.interfaces(),
                    clazz.accessFlags(), clazz.fields(), methods,
                    clazz.innerClasses(), clazz.signature(), clazz.typeId(),
                    clazz.annotations()));
        }
        driver.pendingSuperBridges.clear();
        return new IRModule(module.name(), classes, module.imports(), module.sourceName());
    }

    /** Pacote derivado do DIRETÓRIO do arquivo relativo à raiz do módulo. */

    /**
     * P3-10: em {@code orm.where<T>(db, "col", v)}, {@code orm.where_op<T>(db,
     * "col", op, v)} e {@code orm.count<T>(db, "col", v)} a coluna é o 2º arg.
     * Se for um literal de string, ele tem que nomear um campo da entidade —
     * caso contrário falha em compile-time (ORM003), sem esperar o SQL falhar
     * em runtime. Colunas dinâmicas (arg não-literal) seguem liberadas.
     */
    static void validateOrmField(CompilerDriver driver, MethodCallExpr mc,
                                 String entityName,
                                  List<EntityFieldNode> fields) {
        String m = mc.methodName();
        boolean isWhere = "where".equals(m) || "where_op".equals(m);
        boolean isCountWhere = "count".equals(m) && mc.arguments().size() == 3;
        if (!isWhere && !isCountWhere) return;
        ExpressionNode fieldArg = mc.arguments().get(1);
        if (!(fieldArg instanceof LiteralExpr lit) || lit.kind() != ConcreteLiteralKind.STRING) return;
        String col = lit.value();
        for (EntityFieldNode f : fields) {
            if (f.name().equals(col)) return;
        }
        if (driver.currentDiagnostics != null) {
            SourcePosition p = mc.position();
            driver.currentDiagnostics.error(p != null ? p.file() : "",
                    p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                    "orm." + m + ": unknown column '" + col + "' in entity '"
                            + entityName + "'",
                    "ORM003");
        }
    }

    /**
     * Query DSL tipada (ORM001): baixa {@code Entity.query(db) { where ...;
     * orderBy ...; limit N }} para {@code kof_db_queryN(db, sql, binds...,
     * className)} — o mesmo caminho de {@code db.query<T>}. A SQL é montada em
     * compile-time a partir do schema da entidade (validação de coluna à la
     * ORM003); os valores de {@code where} são binds preparados ({@code ?}).
     */
    static int lowerQueryDsl(CompilerDriver driver, QueryDslExpr q,
                              List<KofOperation> ops, String owner,
                              int localIdx, List<IRLocalVariable> locals) {
        if (!KofDb.supportedOn(driver.target)) {
            if (driver.currentDiagnostics != null) {
                SourcePosition p = q.position();
                driver.currentDiagnostics.error(p != null ? p.file() : "",
                        p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                        q.entityType() + ".query: not available on the " + driver.target
                                + " driver.target yet (" + KofDb.gapCode() + ")",
                        KofDb.gapCode());
            }
            return localIdx;
        }
        String entity = q.entityType();
        List<EntityFieldNode> fields = driver.entitySchemas.get(entity);
        // identificadores sempre quotados (ANSI "ident") — nomes de entidade/
        // coluna podem ser palavras reservadas do SQL (ex.: user)
        String table = '"' + KofOrm.tableName(entity) + '"';

        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(table);
        List<ExpressionNode> binds = new ArrayList<>();
        boolean firstWhere = true;
        for (ExpressionNode w : q.whereClauses()) {
            if (!(w instanceof BinaryExpr be)) {
                if (driver.currentDiagnostics != null) {
                    SourcePosition p = q.position();
                    driver.currentDiagnostics.error(p != null ? p.file() : "",
                            p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                            "query: where clause must be a comparison (a > b)", "ORM004");
                }
                return localIdx;
            }
            if (!(be.left() instanceof IdentifierExpr col)) {
                if (driver.currentDiagnostics != null) {
                    SourcePosition p = q.position();
                    driver.currentDiagnostics.error(p != null ? p.file() : "",
                            p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                            "query: where field must be a column name", "ORM004");
                }
                return localIdx;
            }
            if (fields == null) {
                if (driver.currentDiagnostics != null) {
                    SourcePosition p = q.position();
                    driver.currentDiagnostics.error(p != null ? p.file() : "",
                            p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                            "query: unknown entity '" + entity + "' (ORM002)", "ORM002");
                }
                return localIdx;
            }
            boolean valid = fields.stream().anyMatch(f -> f.name().equals(col.name()));
            if (!valid) {
                if (driver.currentDiagnostics != null) {
                    SourcePosition p = q.position();
                    driver.currentDiagnostics.error(p != null ? p.file() : "",
                            p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                            "query: unknown column '" + col.name() + "' in entity '"
                                    + entity + "' (ORM003)", "ORM003");
                }
                return localIdx;
            }
            String op = CompilerOrmSupport.sqlOp(be.operator());
            if (op == null) {
                if (driver.currentDiagnostics != null) {
                    SourcePosition p = q.position();
                    driver.currentDiagnostics.error(p != null ? p.file() : "",
                            p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                            "query: unsupported operator '" + be.operator() + "' (use =, ==, !=, <, <=, >, >=)",
                            "ORM004");
                }
                return localIdx;
            }
            sql.append(firstWhere ? " WHERE " : " AND ")
                    .append('"').append(col.name()).append('"')
                    .append(' ').append(op).append(" ?");
            firstWhere = false;
            binds.add(be.right());
        }
        if (!q.orderByFields().isEmpty()) {
            for (int i = 0; i < q.orderByFields().size(); i++) {
                ExpressionNode f = q.orderByFields().get(i);
                if (!(f instanceof IdentifierExpr idf)) {
                    if (driver.currentDiagnostics != null) {
                        SourcePosition p = q.position();
                        driver.currentDiagnostics.error(p != null ? p.file() : "",
                                p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                                "query: orderBy field must be a column name", "ORM004");
                    }
                    return localIdx;
                }
                sql.append(i == 0 ? " ORDER BY " : ", ")
                        .append('"').append(idf.name()).append('"')
                        .append(" ").append(q.orderByDirs().get(i).toUpperCase());
            }
        }
        // limit: literal inline; não-literal vira bind
        if (q.limit() != null) {
            if (q.limit() instanceof LiteralExpr le && le.kind() == ConcreteLiteralKind.INT) {
                sql.append(" LIMIT ").append(le.value());
            } else {
                sql.append(" LIMIT ?");
                binds.add(q.limit());
            }
        }

        int nBinds = binds.size();
        if (nBinds > KofDb.MAX_BIND) {
            if (driver.currentDiagnostics != null) {
                SourcePosition p = q.position();
                driver.currentDiagnostics.error(p != null ? p.file() : "",
                        p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                        "query: at most " + KofDb.MAX_BIND + " binds (where + limit)", "ORM004");
            }
            return localIdx;
        }
        String fn = "kof_db_query" + nBinds;
        // 1) db id
        localIdx = ExpressionLowerer.emitExpression(driver, q.dbArg(), ops, owner, localIdx, locals);
        // 2) sql
        ops.add(new KofLoadLiteral(BuiltinTypes.STRING, sql.toString()));
        // 3) binds (primitivos boxed — o runtime espera Object)
        for (ExpressionNode b : binds) {
            Type bt = ExpressionTyper.inferExprType(driver, b, locals);
            localIdx = ExpressionLowerer.emitExpression(driver, b, ops, owner, localIdx, locals);
            if (TypeMetrics.isPrimitiveType(bt)) TypeEmitter.boxPrimitive(ops, bt);
        }
        // 4) className
        ops.add(new KofLoadLiteral(BuiltinTypes.STRING, CompilerTypes.classNameFor(entity)));
        // 5) a chamada
        List<Type> params = new ArrayList<>();
        params.add(BuiltinTypes.STRING); // id
        params.add(BuiltinTypes.STRING); // sql
        for (int i = 0; i < nBinds; i++) params.add(Type.UnknownType.UNKNOWN);
        params.add(BuiltinTypes.STRING); // className
        Type retType = new Type.ClassType("kof", "List", List.of(CompilerTypes.toType(entity, driver.currentUnit)));
        ops.add(new KofCall(new Type.ClassType("kof.db", "Db", List.of()),
                fn, params, retType, KofCallKind.FUNCTION));
        return localIdx;
    }

    /** Operador Kof → operador SQL ({@code ==} → {@code =}); null se não suportado. */
    static String sqlOp(String op) {
        return switch (op) {
            case "=", "==", "!=" -> op.equals("==") ? "=" : op;
            case "<", "<=", ">", ">=" -> op;
            default -> null;
        };
    }

    static String declPackage(CompilerDriver driver, AstNode decl, String fallback) {
        String pkg = driver.declarationPackages.get(decl);
        return pkg != null ? pkg : fallback;
    }

}