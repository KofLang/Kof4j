package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Inferência de tipo para chamadas ESTÁTICAS sobre namespaces builtin
 * (db, log, orm, process, config, cache, gpu, http, mq, time, security,
 * validation, std, observability, tetris, media, web) — extraído verbatim
 * do MemberCallTyper (§140 split-8, regra ≤500).
 *
 * Convenção (idêntica ao MethodCallNamespaces do split-6): devolve o tipo
 * quando o if do namespace casa (mesmo UNKNOWN — era terminal) e {@code
 * null} quando nenhum casa, para o caller seguir a cadeia.
 */
final class MemberCallNamespaces {

    private MemberCallNamespaces() {}

    static Type inferStatic(SemanticAnalyzer sa, MethodCallExpr mc, SymbolTable scope, Type recvType) {
        // #126: o caminho semântico não conhecia o namespace `json` — só o
        // lowering JVM (MethodCallNamespaces) o despacha. Resultado: aridade
        // errada (`json.encode(x, 4)`, `json.encode()`) passava no check e o
        // lowering (guard `arguments().size() == 1`) não emitia nada → bytecode
        // com operand stack underflow (VerifyError em runtime). O fix valida só
        // ARIDADE: caso VÁLIDO devolve null (continua a cadeia EXATAMENTE como
        // antes — nenhum if casava; dar tipo concreto p/ decode mudaria o
        // narrowing a jusante e quebraria programas válidos — regressão).
        if (mc.receiver() instanceof IdentifierExpr rid && "json".equals(rid.name())
                && !SemExpressionTyper.isLocalName(scope, rid.name())) {
            boolean valid = ("encode".equals(mc.methodName()) && mc.arguments().size() == 1)
                    || ("decode".equals(mc.methodName()) && mc.arguments().size() == 1
                        && !mc.typeArguments().isEmpty());
            if (!valid && sa.diagnostics() != null) {
                sa.diagnostics().error(mc,
                        "Cannot resolve method '" + mc.methodName() + "' on namespace 'json' — "
                                + ("decode".equals(mc.methodName())
                                        ? "use json.decode<T>(s) (1 arg + type argument)"
                                        : "use json.encode(x) (1 arg)"),
                        "SEM025");
                return Type.UnknownType.UNKNOWN;
            }
            return null;
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !SemExpressionTyper.isLocalName(scope, rid.name()) && KofDb.isDbNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            boolean typed = KofDb.isQuery(mc.methodName()) && !mc.typeArguments().isEmpty();
            KofDb.DbCall dbCall = KofDb.staticCall(mc.methodName(), argTypes, typed);
            if (dbCall != null) {
                if (typed && !mc.typeArguments().isEmpty()) {
                    return new Type.ClassType("kof", "List",
                            List.of(MemberResolver.resolveType(sa, mc.typeArguments().get(0), scope)));
                }
                return dbCall.returnType();
            }
            return unknown(sa, "db", mc.methodName());
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !SemExpressionTyper.isLocalName(scope, rid.name()) && KofLog.isLogNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            KofLog.LogCall logCall = KofLog.staticCall(mc.methodName(), argTypes);
            if (logCall != null) return logCall.returnType();
            return unknown(sa, "log", mc.methodName());
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !SemExpressionTyper.isLocalName(scope, rid.name()) && KofOrm.isOrmNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            boolean typed = !mc.typeArguments().isEmpty();
            String entityName = typed ? mc.typeArguments().get(0) : null;
            // P4 (D-PAGINATION-P4-LOWERING): `orm.window<T>` devolve Window<T>,
            // nao List<T> — a face e dessugada no ORM lowerer sobre
            // orm.page/orm.count + o helper Kof window(...). Exige o host
            // kof.pagination importado (Window existe); sem ele nao resolve.
            if ("window".equals(mc.methodName()) && typed) {
                Type ent = MemberResolver.resolveType(sa, mc.typeArguments().get(0), scope);
                Type win = KofOrm.windowType(sa, ent);
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
                                List.of(MemberResolver.resolveType(sa, mc.typeArguments().get(0), scope)));
                    }
                    if ("find".equals(mc.methodName())) {
                        return MemberResolver.resolveType(sa, mc.typeArguments().get(0), scope);
                    }
                }
                return ormCall.returnType();
            }
            return unknown(sa, "orm", mc.methodName());
        }
        if (mc.receiver() instanceof IdentifierExpr rid && "process".equals(rid.name())
                && !SemExpressionTyper.isLocalName(scope, rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            KofProcess.ProcessCall procCall = KofProcess.entryCall(mc.methodName(), argTypes);
            if (procCall != null) return procCall.returnType();
            KofProcess.ProcessCall exitCall = KofProcess.exitCall(argTypes);
            if (exitCall != null) return exitCall.returnType();
            if (sa.diagnostics() != null) {
                sa.diagnostics().error(mc,
                        "Cannot resolve method '" + mc.methodName() + "' on 'process' (valid: run, spawn, exit)",
                        "SEM025");
            }
            return Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() instanceof IdentifierExpr rid && "shell".equals(rid.name())
                && !SemExpressionTyper.isLocalName(scope, rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            KofShell.ShellCall shellCall = KofShell.staticCall(mc.methodName(), argTypes);
            if (shellCall != null) return shellCall.returnType();
            if (sa.diagnostics() != null) {
                sa.diagnostics().error(mc,
                        "Cannot resolve method '" + mc.methodName() + "' on 'shell' (valid: "
                                + String.join(", ", KofShell.functions()) + ")",
                        "SEM025");
            }
            return Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() instanceof IdentifierExpr rid && "ssh".equals(rid.name())
                && !SemExpressionTyper.isLocalName(scope, rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            KofSsh.SshCall sshCall = KofSsh.staticCall(mc.methodName(), argTypes);
            if (sshCall != null) return sshCall.returnType();
            if (sa.diagnostics() != null) {
                sa.diagnostics().error(mc,
                        "Cannot resolve method '" + mc.methodName() + "' on 'ssh' (valid: "
                                + String.join(", ", KofSsh.functions()) + ")",
                        "SEM025");
            }
            return Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !SemExpressionTyper.isLocalName(scope, rid.name()) && KofConfig.isConfigNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            KofConfig.ConfigCall cfgCall = KofConfig.staticCall(mc.methodName(), argTypes);
            if (cfgCall != null) return cfgCall.returnType();
            return unknown(sa, "config", mc.methodName());
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !SemExpressionTyper.isLocalName(scope, rid.name()) && KofCache.isCacheNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            KofCache.CacheCall cacheCall = KofCache.staticCall(mc.methodName(), argTypes);
            if (cacheCall != null) return cacheCall.returnType();
            return unknown(sa, "cache", mc.methodName());
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !SemExpressionTyper.isLocalName(scope, rid.name()) && KofGpu.isGpuNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            KofGpu.GpuCall gpuCall = KofGpu.staticCall(mc.methodName(), argTypes);
            if (gpuCall != null) return gpuCall.returnType();
            return unknown(sa, "gpu", mc.methodName());
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !SemExpressionTyper.isLocalName(scope, rid.name()) && KofHttp.isHttpNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            KofHttp.HttpCall httpCall = KofHttp.staticCall(mc.methodName(), argTypes);
            if (httpCall != null) return httpCall.returnType();
            return unknown(sa, "http", mc.methodName());
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !SemExpressionTyper.isLocalName(scope, rid.name()) && KofMq.isMqNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            KofMq.MqCall mqCall = KofMq.staticCall(mc.methodName(), argTypes);
            if (mqCall != null) return mqCall.returnType();
            return unknown(sa, "mq", mc.methodName());
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !SemExpressionTyper.isLocalName(scope, rid.name()) && KofTime.isTimeNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            KofTime.TimeCall timeCall = KofTime.staticCall(mc.methodName(), argTypes);
            if (timeCall != null) return timeCall.returnType();
            return unknown(sa, "time", mc.methodName());
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !SemExpressionTyper.isLocalName(scope, rid.name()) && KofSecurity.isSecurityNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            KofSecurity.SecCall secCall = KofSecurity.staticMethod(rid.name(), mc.methodName(), argTypes);
            if (secCall != null) return secCall.returnType();
            return unknown(sa, rid.name(), mc.methodName());
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !SemExpressionTyper.isLocalName(scope, rid.name()) && KofValidation.isValidationNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            KofValidation.ValidationCall vCall = KofValidation.staticMethod(rid.name(), mc.methodName(), argTypes);
            if (vCall != null) return vCall.returnType();
            return unknown(sa, rid.name(), mc.methodName());
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !SemExpressionTyper.isLocalName(scope, rid.name()) && KofStd.isStdNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            KofStd.StdCall sCall = KofStd.staticMethod(rid.name(), mc.methodName(), argTypes);
            if (sCall != null) return sCall.returnType();
            return unknown(sa, rid.name(), mc.methodName());
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !SemExpressionTyper.isLocalName(scope, rid.name()) && KofObservability.isObservabilityNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            KofObservability.ObservabilityCall oCall = KofObservability.staticMethod(rid.name(), mc.methodName(), argTypes);
            if (oCall != null) return oCall.returnType();
            return unknown(sa, rid.name(), mc.methodName());
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !SemExpressionTyper.isLocalName(scope, rid.name()) && KofTetris.isTetrisNamespace(rid.name())) {
            KofTetris.TetrisCall tetrisCall = KofTetris.staticMethod(rid.name(), mc.methodName(),
                    mc.arguments().size());
            if (tetrisCall != null) {
                for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
                return tetrisCall.returnType();
            }
            return unknown(sa, rid.name(), mc.methodName());
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !SemExpressionTyper.isLocalName(scope, rid.name()) && KofImage.isImageNamespace(rid.name())) {
            KofImage.ImageCall imageCall = KofImage.staticMethod(rid.name(), mc.methodName(),
                    mc.arguments().size());
            if (imageCall != null) {
                for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
                return imageCall.returnType();
            }
            return unknown(sa, rid.name(), mc.methodName());
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !SemExpressionTyper.isLocalName(scope, rid.name()) && KofMedia.isStaticNamespace(rid.name())) {
            KofMedia.MediaCall mediaCall = KofMedia.staticCall(rid.name(), mc.methodName(),
                    mc.arguments().size());
            if (mediaCall != null) {
                for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
                return mediaCall.returnType();
            }
            return unknown(sa, rid.name(), mc.methodName());
        }
        // §494: o namespace `scheduler` não passava pelo gate central de
        // namespaces — `scheduler.bogus()` inferia UNKNOWN em silêncio e o
        // lowering (ExpressionSchedulerCallLowerer, schedCall == null)
        // devolvia sem emitir nada → operand stack underflow (VerifyError no
        // load), o mesmo R6/Q7 do #126 (json) e do §490. Agora é SEM025,
        // igual a math/strings/db/log/orm.
        if (mc.receiver() instanceof IdentifierExpr rid && !SemExpressionTyper.isLocalName(scope, rid.name())
                && KofScheduler.isSchedulerNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            KofScheduler.SchedulerCall sc = KofScheduler.staticCall(mc.methodName(), argTypes);
            if (sc != null) return sc.returnType();
            return unknown(sa, "scheduler", mc.methodName());
        }
        // §498: o namespace `web` só expõe `web.app()`. Um método desconhecido
        // (`web.bogus()`) caía em webInstance com recvType UNKNOWN, que devolve
        // null — sem diagnóstico — e o lowering não emitia nada (no-op silencioso;
        // como expressão, operand stack underflow → VerifyError no load). Mesma
        // família R6/Q7 do §495/#126.
        if (mc.receiver() instanceof IdentifierExpr rid && !SemExpressionTyper.isLocalName(scope, rid.name())
                && KofWeb.isWebNamespace(rid.name())) {
            if ("app".equals(mc.methodName()) && mc.arguments().isEmpty()) {
                return KofWeb.APP;
            }
            return unknown(sa, "web", mc.methodName());
        }
        // D-KOF-NET (fatia 1): membros de handle `net` (Listener/Conn/Endpoint).
        // Sem braço próprio caíam em webInstance → null silencioso (a família
        // R6 do §498). Handle é o 1º argumento na rota de membros (padrão web/db).
        if (KofNet.isNetHandleType(recvType)) {
            java.util.List<Type> netArgTypes = new java.util.ArrayList<>();
            netArgTypes.add(recvType);
            for (ExpressionNode arg : mc.arguments()) {
                netArgTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            }
            KofNet.NetCall netCall = KofNet.instanceMethod(mc.methodName(), netArgTypes);
            if (netCall != null) return netCall.returnType();
            return unknown(sa, "net", mc.methodName());
        }
        return webInstance(sa, mc, scope, recvType);
    }

    /** Instâncias web (KofWeb.app / SseConnection) e media image/audio. */
    private static Type webInstance(SemanticAnalyzer sa, MethodCallExpr mc, SymbolTable scope, Type recvType) {
        if (KofWeb.isAppType(recvType)) {
            if ("sse".equals(mc.methodName()) && mc.arguments().size() == 2
                    && mc.arguments().get(1) instanceof LambdaExpr le
                    && le.parameters().isEmpty()) {
                mc.arguments().set(1, new LambdaExpr(le.position(),
                        List.of(new FormalParameterNode(le.position(), List.of(),
                                SemMethodCallTyper.SSE_CONNECTION_TYPE, "sse")), le.body()));
            }
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            KofWeb.WebCall webCall = KofWeb.instanceMethod(mc.methodName(), argTypes);
            if (webCall != null) return webCall.returnType();
            return unknown(sa, "web.app", mc.methodName());
        }
        if (KofWeb.isSseConnectionType(recvType)) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            KofWeb.WebCall sseCall = KofWeb.sseConnectionMethod(mc.methodName(), argTypes);
            if (sseCall != null) return sseCall.returnType();
            return unknown(sa, "sse", mc.methodName());
        }
        return null;
    }

    private static Type unknown(SemanticAnalyzer sa, String ns, String method) {
        if (sa.diagnostics() != null) {
            sa.diagnostics().error("", 0, 0, 0,
                    "Cannot resolve method '" + method + "' on namespace '" + ns + "'",
                    "SEM025");
        }
        return Type.UnknownType.UNKNOWN;
    }
}
