package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering do dispatch ORM estático (KofOrm): save/find/all/where/page/migrate
 * e a validação tipada dos campos de entidade.
 */
final class ExpressionOrmCallLowerer {

    private ExpressionOrmCallLowerer() {}

    static int lower(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                    String owner, int localIdx, List<IRLocalVariable> locals) {
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
        if (!KofOrm.supportedOn(driver.target)) {
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
            if (ai > 0 && TypeMetrics.isPrimitiveType(ExpressionTyper.inferExprType(driver, arg, locals))) {
                TypeEmitter.boxPrimitive(ops, ExpressionTyper.inferExprType(driver, arg, locals));
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
}