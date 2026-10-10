package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Inferência de tipo para chamadas ESTÁTICAS sobre namespaces builtin
 * (enum values/valueOf, json, web, db, http, mq, time, scheduler, log, orm,
 * process, config, tetris, io-ctor) — extraído verbatim do MethodCallTyper
 * (§140 split-6, regra ≤500).
 *
 * Convenção: devolve o tipo quando o if EXTERNO casa (mesmo que o resultado
 * seja UNKNOWN — era terminal no original) e {@code null} quando nenhum if
 * casa (o caller continua a cadeia). Os dois "fall through" originais
 * (scheduler sem receiver, io-ctor estático) preservam o comportamento:
 * sem match devolvem null para o caller seguir.
 */
final class MethodCallNamespaces {

    private MethodCallNamespaces() {}

    static Type inferStatic(CompilerDriver driver, MethodCallExpr mc, List<IRLocalVariable> locals) {
        if (mc.receiver() instanceof IdentifierExpr rid && CompilerTypes.isEnumName(rid.name(), driver.currentUnit)
                && driver.findLocalVar(rid.name(), locals) == null) {
            java.util.List<String> consts = CompilerTypes.enumConstantsOf(rid.name(), driver.currentUnit);
            Type enumT = CompilerTypes.enumTypeOf(rid.name(), driver.semanticAnalyzer); // #445
            // D-ENUM207: valores agora são INSTÂNCIAS de enum — values() é
            // List<Enum>, valueOf devolve Enum (não List<String>).
            if ("values".equals(mc.methodName()) && mc.arguments().isEmpty()) {
                return new Type.ClassType("kof", "List", List.of(enumT));
            }
            if ("valueOf".equals(mc.methodName()) && mc.arguments().size() == 1) {
                return enumT;
            }
            // constante via sintaxe de método? Color.Red() — não suportado
            if (consts.contains(mc.methodName())) return enumT;
            return Type.UnknownType.UNKNOWN;
        }
        // X6 (D-INTEROP-REFLECT): `interop.schema(R)` é intrínseco de
        // compile-time — o tipo é List<Field> (host kof.interop), dobrado no
        // lowerer sem reflexão em runtime.
        if (CompilerInterop.isSchemaCall(mc) && CompilerInterop.hostPresent(driver)) {
            return CompilerInterop.schemaType();
        }
        if (mc.receiver() instanceof IdentifierExpr rid && "json".equals(rid.name())) {
            if ("encode".equals(mc.methodName())) return BuiltinTypes.STRING;
            if ("decode".equals(mc.methodName()) && !mc.typeArguments().isEmpty()) {
                return CompilerTypes.toType(mc.typeArguments().get(0), driver.currentUnit);
            }
            return Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() instanceof IdentifierExpr rid && KofWeb.isWebNamespace(rid.name())) {
            if ("app".equals(mc.methodName()) && mc.arguments().isEmpty()) {
                return KofWeb.APP;
            }
            return Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() instanceof IdentifierExpr rid && KofDb.isDbNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
            boolean typed = KofDb.isQuery(mc.methodName()) && !mc.typeArguments().isEmpty();
            KofDb.DbCall dbCall = KofDb.staticCall(mc.methodName(), argTypes, typed);
            if (dbCall != null) {
                if (typed && !mc.typeArguments().isEmpty()) {
                    return new Type.ClassType("kof", "List",
                            List.of(CompilerTypes.toType(mc.typeArguments().get(0), driver.currentUnit)));
                }
                return dbCall.returnType();
            }
            return Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() instanceof IdentifierExpr rid && KofHttp.isHttpNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
            KofHttp.HttpCall httpCall = KofHttp.staticCall(mc.methodName(), argTypes);
            if (httpCall != null) return httpCall.returnType();
            return Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() instanceof IdentifierExpr rid && KofMq.isMqNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
            KofMq.MqCall mqCall = KofMq.staticCall(mc.methodName(), argTypes);
            if (mqCall != null) return mqCall.returnType();
            return Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() instanceof IdentifierExpr rid && KofTime.isTimeNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
            KofTime.TimeCall timeCall = KofTime.staticCall(mc.methodName(), argTypes);
            if (timeCall != null) return timeCall.returnType();
            return Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() instanceof IdentifierExpr rid && KofScheduler.isSchedulerNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
            KofScheduler.SchedulerCall sc = KofScheduler.staticCall(mc.methodName(), argTypes);
            if (sc != null) return sc.returnType();
            return Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() == null && KofScheduler.isSchedulerMethod(mc.methodName())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
            KofScheduler.SchedulerCall sc = KofScheduler.staticCall(mc.methodName(), argTypes);
            if (sc != null) return sc.returnType();
            // fall through (original): sem match, o caller segue
            return null;
        }
        // #108 (opção 1, 13/09): `sleep(ms)` sem receiver resolve como
        // `time.sleep(ms)` — espelha o `now()` sem receiver que já existe
        // (MethodCallTyper + BuiltinCallTyper + ExpressionStaticCallLowerer).
        // Só vale sem receiver E sem local `sleep` (não sombreia usuário).
        if (mc.receiver() == null && "sleep".equals(mc.methodName())
                && driver.findLocalVar("sleep", locals) == null) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
            KofTime.TimeCall timeCall = KofTime.staticCall("sleep", argTypes);
            if (timeCall != null) return timeCall.returnType();
            return Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() instanceof IdentifierExpr rid && KofLog.isLogNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
            KofLog.LogCall logCall = KofLog.staticCall(mc.methodName(), argTypes);
            if (logCall != null) return logCall.returnType();
            return Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() instanceof IdentifierExpr rid && KofOrm.isOrmNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
            boolean typed = !mc.typeArguments().isEmpty();
            String entityName = typed ? mc.typeArguments().get(0) : null;
            // P4 (D-PAGINATION-P4-LOWERING): `orm.window<T>` devolve Window<T>
            // (dessugado no ORM lowerer); exige o host kof.pagination importado.
            if ("window".equals(mc.methodName()) && typed) {
                Type win = KofOrm.windowType(driver.semanticAnalyzer,
                        CompilerTypes.toType(mc.typeArguments().get(0), driver.currentUnit));
                if (win != null) return win;
            }
            KofOrm.OrmCall ormCall = KofOrm.staticCall(mc.methodName(), argTypes, typed, entityName);
            if (ormCall != null) {
                if ("save".equals(mc.methodName()) && !argTypes.isEmpty()) {
                    return argTypes.get(argTypes.size() - 1);
                }
                if (typed && !mc.typeArguments().isEmpty()) {
                    if ("all".equals(mc.methodName()) || "where".equals(mc.methodName())
                            || "page".equals(mc.methodName())) {
                        return new Type.ClassType("kof", "List",
                                List.of(CompilerTypes.toType(mc.typeArguments().get(0), driver.currentUnit)));
                    }
                    if ("find".equals(mc.methodName())) {
                        return CompilerTypes.toType(mc.typeArguments().get(0), driver.currentUnit);
                    }
                }
                return ormCall.returnType();
            }
            return Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() instanceof IdentifierExpr rid && "process".equals(rid.name())
                && driver.findLocalVar(rid.name(), locals) == null) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
            KofProcess.ProcessCall procCall = KofProcess.entryCall(mc.methodName(), argTypes);
            if (procCall != null) return procCall.returnType();
            return Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() instanceof IdentifierExpr rid && "shell".equals(rid.name())
                && driver.findLocalVar(rid.name(), locals) == null) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
            KofShell.ShellCall shellCall = KofShell.staticCall(mc.methodName(), argTypes);
            if (shellCall != null) return shellCall.returnType();
            return Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() instanceof IdentifierExpr rid && "ssh".equals(rid.name())
                && driver.findLocalVar(rid.name(), locals) == null) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
            KofSsh.SshCall sshCall = KofSsh.staticCall(mc.methodName(), argTypes);
            if (sshCall != null) return sshCall.returnType();
            return Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() instanceof IdentifierExpr rid && KofConfig.isConfigNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
            KofConfig.ConfigCall cfgCall = KofConfig.staticCall(mc.methodName(), argTypes);
            if (cfgCall != null) return cfgCall.returnType();
            return Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() instanceof IdentifierExpr rid && KofTetris.isTetrisNamespace(rid.name())) {
            KofTetris.TetrisCall tetrisCall = KofTetris.staticMethod(rid.name(), mc.methodName(),
                    mc.arguments().size());
            if (tetrisCall != null) return tetrisCall.returnType();
            return Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() instanceof IdentifierExpr rid && KofImage.isImageNamespace(rid.name())) {
            KofImage.ImageCall imageCall = KofImage.staticMethod(rid.name(), mc.methodName(),
                    mc.arguments().size());
            if (imageCall != null) return imageCall.returnType();
            return Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() instanceof IdentifierExpr rid2 && KofIo.isConstructor(rid2.name())) {
            KofIo.IoCall ioCall = KofIo.staticMethod(rid2.name(), mc.methodName(), mc.arguments().size());
            if (ioCall != null) return ioCall.returnType();
            // fall through (original): sem match, o caller segue
            return null;
        }
        return null;
    }
}
