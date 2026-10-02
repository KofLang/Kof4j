package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering do dispatch ORM estático (KofOrm): save/find/all/where/page/migrate
 * e a validação tipada dos campos de entidade.
 */
public final class ExpressionOrmCallLowerer {

    private ExpressionOrmCallLowerer() {}

    static int lower(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                    String owner, int localIdx, List<IRLocalVariable> locals) {
    if ("window".equals(mc.methodName())) {
        return lowerOrmWindow(driver, mc, ops, owner, localIdx, locals);
    }
    IdentifierExpr rid = (IdentifierExpr) mc.receiver();
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    boolean typed = !mc.typeArguments().isEmpty();
    String entityName = typed ? mc.typeArguments().get(0) : null;
    if (entityName == null && "save".equals(mc.methodName()) && !argTypes.isEmpty()) {
        Type objType = argTypes.get(argTypes.size() - 1);
        if (objType instanceof Type.ClassType ct) entityName = ct.name();
    }
    KofOrm.OrmCall ormCall = KofOrm.staticCall(mc.methodName(), argTypes, typed, entityName);
    if (ormCall != null) {
        if (!KofOrm.fnSupportedOn(driver.target, ormCall.function())) {
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                        mc.position() != null ? mc.position().line() : 0,
                        mc.position() != null ? mc.position().column() : 0,
                        0,
                        rid.name() + "." + mc.methodName()
                                + ": not available on the " + driver.target
                                + " driver.target yet (" + KofOrm.gapCode() + ")",
                        KofOrm.gapCode());
            }
            return localIdx;
        }
        List<EntityFieldNode> fields = entityName == null ? null : driver.entitySchemas.get(entityName);
        boolean needsEntity = !"migrate".equals(mc.methodName());
        if (needsEntity && fields == null) {
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                        mc.position() != null ? mc.position().line() : 0,
                        mc.position() != null ? mc.position().column() : 0,
                        0,
                        "orm." + mc.methodName() + ": unknown entity '"
                                + (entityName == null ? "?" : entityName) + "' (ORM002)",
                        "ORM002");
            }
            return localIdx;
        }
        // P3-10: validação tipada do campo em where/count/where_op —
        // a coluna tem que ser um campo real da entidade (ORM003)
        driver.validateOrmField(mc, entityName, fields);
        // args do usuário: (db[, obj|id]) — primitivos são
        // boxed (o runtime espera Object para obj/id)
        for (int ai = 0; ai < mc.arguments().size(); ai++) {
            ExpressionNode arg = mc.arguments().get(ai);
            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
            Type at = ExpressionTyper.inferExprType(driver, arg, locals);
            if (ai > 0 && TypeMetrics.isPrimitiveType(at)) {
                // §447 face Bool: no Native o box de erasure e kof_box_bool
                // (§284) — o valueOf do wrapper cai no dispatch nativo de
                // String.valueOf e o bind virava TEXTO "true"/"false"
                // (divergia do host JVM que liga Boolean/1). Fix cirurgico no
                // Bool: os demais primitivos seguem no valueOf (TEXTO) porque
                // as fatias mysql x86 leem o key como texto/atoi (residuo
                // catalogado no §447 — box-all exige auditar
                // RuntimeOrmMysqlDelete/Find/CountWhere/Page).
                boolean nativeTarget = switch (driver.target) {
                    case NATIVE, NATIVE_RISCV64, NATIVE_AARCH64 -> true;
                    default -> false;
                };
                boolean boolArg = at instanceof Type.PrimitiveType pt
                        && "bool".equals(Type.canonicalPrimitiveName(pt.name()));
                if (nativeTarget && boolArg) {
                    CompilerEmissionHelpers.emitErasureBox(driver, ops, at);
                } else {
                    TypeEmitter.boxPrimitive(ops, at);
                }
            }
        }
        // literais do schema (conhecidos em compile-time):
        // table, schema, [className]
        boolean isMigrate = "migrate".equals(mc.methodName());
        String table = entityName == null ? "" : KofOrm.tableName(entityName);
        String schema = entityName == null ? "" : KofOrm.schemaString(fields);
        boolean needsClassName = "find".equals(mc.methodName())
                || "all".equals(mc.methodName())
                || "where".equals(mc.methodName())
                || "page".equals(mc.methodName());
        List<Type> params = new ArrayList<>(ormCall.parameterTypes());
        if (!isMigrate) {
            ops.add(new KofLoadLiteral(BuiltinTypes.STRING, table));
            ops.add(new KofLoadLiteral(BuiltinTypes.STRING, schema));
            params.add(BuiltinTypes.STRING); // table
            params.add(BuiltinTypes.STRING); // schema
        }
        if (needsClassName) {
            ops.add(new KofLoadLiteral(BuiltinTypes.STRING, CompilerTypes.classNameFor(entityName)));
            params.add(BuiltinTypes.STRING); // className
        }
        Type retType = ormCall.returnType();
        if ("save".equals(mc.methodName()) && !argTypes.isEmpty()) {
            retType = argTypes.get(argTypes.size() - 1);
        } else if (typed) {
            if ("all".equals(mc.methodName()) || "page".equals(mc.methodName())
                    || "where".equals(mc.methodName())) {
                retType = new Type.ClassType("kof", "List",
                        List.of(CompilerTypes.toType(mc.typeArguments().get(0), driver.currentUnit)));
            } else if ("find".equals(mc.methodName())) {
                retType = CompilerTypes.toType(mc.typeArguments().get(0), driver.currentUnit);
            }
        }
        ops.add(new KofCall(new Type.ClassType("kof.orm", "Orm", List.of()),
                ormCall.function(), params, retType, KofCallKind.FUNCTION));
    }
    return localIdx;
    }

    /**
     * P4 (D-PAGINATION-P4-LOWERING): {@code orm.window<T>(db, limit,
     * offset[, true])} dessuga no ORM lowerer para o helper Kof {@code
     * windowPage(...)} injetado ({@code kof.pagination}) sobre {@code orm.page}/
     * {@code orm.count} ({@code windowPage} NAO re-fatiar: a pagina ja vem
     * LIMIT/OFFSET do SQL). Nenhum runtime por alvo constroi o record {@code
     * Window<T>} (ele e por-programa, sem reflection). Os argumentos do usuario
     * sao avaliados UMA vez (temps {@code $kw*}); a forma de 4 args so roda
     * {@code orm.count} no ramo {@code withTotal} (opt-in COUNT(*), lazy — a
     * forma de 3 args nunca conta). Semantica identica a face em memoria (P2):
     * {@code hasPrevious = offset > 0}; {@code hasNext} otimista
     * ({@code page.size == limit}) sem total, exato com ele.
     */
    private static int lowerOrmWindow(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                                      String owner, int localIdx, List<IRLocalVariable> locals) {
        SourcePosition pos = mc.position();
        boolean typed = !mc.typeArguments().isEmpty();
        String entityName = typed ? mc.typeArguments().get(0) : null;
        int nArgs = mc.arguments().size();
        if (!typed || entityName == null || nArgs < 3 || nArgs > 4) {
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                        pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                        "orm.window<T>(db, limit, offset[, true]) requires a type argument and 3 or 4 arguments",
                        "ORM002");
            }
            return localIdx;
        }
        if (driver.entitySchemas.get(entityName) == null) {
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                        pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                        "orm.window: unknown entity '" + entityName + "' (ORM002)", "ORM002");
            }
            return localIdx;
        }
        if (!KofOrm.fnSupportedOn(driver.target, "kof_orm_page")) {
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                        pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                        "orm.window: not available on the " + driver.target
                                + " driver.target yet (" + KofOrm.gapCode() + ")",
                        KofOrm.gapCode());
            }
            return localIdx;
        }
        // Avalia cada argumento UMA vez num temp ($kw*): o page consome
        // db/limit/offset e o helper precisa de limit/offset de novo.
        Type dbType = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
        localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
        int dbIdx = localIdx++;
        locals.add(new IRLocalVariable(dbIdx, "$kwdb" + dbIdx, dbType));
        ops.add(new KofStoreLocal(dbType, dbIdx));

        Type limitType = ExpressionTyper.inferExprType(driver, mc.arguments().get(1), locals);
        localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(1), ops, owner, localIdx, locals);
        int limitIdx = localIdx++;
        locals.add(new IRLocalVariable(limitIdx, "$kwlim" + limitIdx, limitType));
        ops.add(new KofStoreLocal(limitType, limitIdx));

        Type offsetType = ExpressionTyper.inferExprType(driver, mc.arguments().get(2), locals);
        localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(2), ops, owner, localIdx, locals);
        int offsetIdx = localIdx++;
        locals.add(new IRLocalVariable(offsetIdx, "$kwoff" + offsetIdx, offsetType));
        ops.add(new KofStoreLocal(offsetType, offsetIdx));

        int flagIdx = -1;
        if (nArgs == 4) {
            Type flagType = ExpressionTyper.inferExprType(driver, mc.arguments().get(3), locals);
            localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(3), ops, owner, localIdx, locals);
            flagIdx = localIdx++;
            locals.add(new IRLocalVariable(flagIdx, "$kwflag" + flagIdx, flagType));
            ops.add(new KofStoreLocal(flagType, flagIdx));
        }

        Type entityType = CompilerTypes.toType(mc.typeArguments().get(0), driver.currentUnit);
        Type listType = new Type.ClassType("kof", "List", List.of(entityType));
        IdentifierExpr ormRecv = new IdentifierExpr(pos, "orm");
        // orm.page<T>(db, windowBounds(limit, offset), offset) -> List<T>;
        // guarda num temp (avaliado UMA vez, mesmo quando os dois ramos do
        // total o consultam). O windowBounds no arg do limit valida ANTES do
        // SQL: um limit/offset negativo vira o erro nomeado Kof, nao erro de
        // driver.
        ExpressionNode validatedLimit = new MethodCallExpr(pos, null, "windowBounds", List.of(),
                List.of(new IdentifierExpr(pos, "$kwlim" + limitIdx),
                        new IdentifierExpr(pos, "$kwoff" + offsetIdx)));
        MethodCallExpr pageCall = new MethodCallExpr(pos, ormRecv, "page", mc.typeArguments(),
                List.of(new IdentifierExpr(pos, "$kwdb" + dbIdx),
                        validatedLimit,
                        new IdentifierExpr(pos, "$kwoff" + offsetIdx)));
        localIdx = ExpressionLowerer.emitExpression(driver, pageCall, ops, owner, localIdx, locals);
        int pageIdx = localIdx++;
        locals.add(new IRLocalVariable(pageIdx, "$kwpage" + pageIdx, listType));
        ops.add(new KofStoreLocal(listType, pageIdx));
        ExpressionNode pageId = new IdentifierExpr(pos, "$kwpage" + pageIdx);

        ExpressionNode result;
        if (nArgs == 3) {
            result = new MethodCallExpr(pos, null, "windowPage", List.of(),
                    List.of(pageId,
                            new IdentifierExpr(pos, "$kwlim" + limitIdx),
                            new IdentifierExpr(pos, "$kwoff" + offsetIdx)));
        } else {
            MethodCallExpr withTotal = new MethodCallExpr(pos, null, "windowPage", List.of(),
                    List.of(pageId,
                            new IdentifierExpr(pos, "$kwlim" + limitIdx),
                            new IdentifierExpr(pos, "$kwoff" + offsetIdx),
                            new MethodCallExpr(pos, ormRecv, "count", mc.typeArguments(),
                                    List.of(new IdentifierExpr(pos, "$kwdb" + dbIdx)))));
            MethodCallExpr without = new MethodCallExpr(pos, null, "windowPage", List.of(),
                    List.of(pageId,
                            new IdentifierExpr(pos, "$kwlim" + limitIdx),
                            new IdentifierExpr(pos, "$kwoff" + offsetIdx)));
            result = new IfExpr(pos, new IdentifierExpr(pos, "$kwflag" + flagIdx), withTotal, without);
        }
        return ExpressionLowerer.emitExpression(driver, result, ops, owner, localIdx, locals);
    }
}