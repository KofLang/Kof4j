package dev.kof.compiler;

/**
 * Guarda de isencao do SEM011 (§354, gate ≤500): "quando um identificador
 * a puro nome pode ficar Unknown SEM relatorio de 'Undefined variable or
 * type'" — `this`/`super`, namespaces kof.*, UI/media, tipos builtin,
 * operandos de type-ref (bug 127/#336/§336), classes do modulo e classes
 * externas (§134). MOVIMENTO 1:1 puro de {@code SemExpressionTyper} —
 * zero mudanca de comportamento; curto-circuito e ordem preservados.
 */
final class SemUndefinedVarGuard {

    private SemUndefinedVarGuard() {}

    static boolean reportsUndefined(SemanticAnalyzer sa, String name) {
        return sa.diagnostics() != null
                && !"this".equals(name) && !"super".equals(name)
                && !"json".equals(name) && !"process".equals(name) && !"shell".equals(name)
                && !"ssh".equals(name)
                && !CompilerInterop.isInteropNamespace(name)
                && !KofWeb.isWebNamespace(name)
                && !KofConfig.isConfigNamespace(name)
                && !KofCache.isCacheNamespace(name)
                && !KofGpu.isGpuNamespace(name)
                && !KofDb.isDbNamespace(name)
                && !KofOrm.isOrmNamespace(name)
                && !KofLog.isLogNamespace(name)
                && !KofSecurity.isSecurityNamespace(name)
                && !KofValidation.isValidationNamespace(name)
                && !KofStd.isStdNamespace(name)
                && !KofObservability.isObservabilityNamespace(name)
                && !KofHttp.isHttpNamespace(name)
                && !KofMq.isMqNamespace(name)
                && !KofTime.isTimeNamespace(name)
                && !KofScheduler.isSchedulerNamespace(name)
                && !KofTetris.isTetrisNamespace(name)
                && !KofMedia.isStaticNamespace(name)
                && !KofUi.isPalette(name) && !KofUi.isConstructor(name)
                && !KofUiTokens.isTokenNamespace(name)
                && !KofUi.isRouterNamespace(name)
                && !"Theme".equals(name)
                && !MemberResolver.isBuiltinTypeName(name)
                // bug 127/#336: operando de TIPO de `as`/`instanceof`
                // vira IdentifierExpr com o type-ref completo —
                // funcão (`() -> Int`), genericos (`List<Int>`),
                // array (`Int[]`), nullable (`Int?`). Nunca e
                // variavel/tipo declarado; toType cuida da resolucao.
                && !looksLikeTypeRefName(name)
                && !sa.allClasses().containsKey(name)
                // §134: nome de classe EXTERNA (Button.inflate,
                // Greeter.hello) — o lowering (ExpressionMethodCall
                // Lowerer) resolve via ExternalClasspath; sem este
                // passe a análise semântica marcava SEM011 e a
                // chamada estática com receiver identificador nunca
                // chegava ao lowering (só `new X()` e instância
                // funcionavam).
                && !isExternalImportedClass(sa, name)
                // §500 slice B: `Integer.MAX_VALUE` sem import (o caminho de
                // método já aceita o nome do JDK desde o §499); o campo é
                // tipado/emitido pelo bloco estático do typer/lowerer.
                && !StaticClassReceiver.isJdkFieldOwnerName(name);
    }

    /**
     * §496: um identificador é um NAMESPACE builtin (math, strings, time, db,
     * orm, log, cache, config, security, gpu, …). Diferente de {@link
     * #reportsUndefined} (que também isenta tipos/paletas/tokens/interop),
     * aqui interessa SÓ o namespace puro — a face FIELD de um namespace não
     * existe (a superfície é função), então um campo desconhecido nele precisa
     * virar diagnóstico (SEM102) em vez de `getfield "?".campo`
     * (NoClassDefFoundError no load, compilado limpo). Interop/router/tokens
     * ficam de fora: qualificam nomes/constantes e têm caminho próprio.
     */
    static boolean isBuiltinNamespace(String name) {
        return "json".equals(name) || "process".equals(name)
                || "shell".equals(name) || "ssh".equals(name)
                || KofWeb.isWebNamespace(name)
                || KofConfig.isConfigNamespace(name)
                || KofCache.isCacheNamespace(name)
                || KofGpu.isGpuNamespace(name)
                || KofDb.isDbNamespace(name)
                || KofOrm.isOrmNamespace(name)
                || KofLog.isLogNamespace(name)
                || KofSecurity.isSecurityNamespace(name)
                || KofValidation.isValidationNamespace(name)
                || KofStd.isStdNamespace(name)
                || KofObservability.isObservabilityNamespace(name)
                || KofHttp.isHttpNamespace(name)
                || KofMq.isMqNamespace(name)
                || KofTime.isTimeNamespace(name)
                || KofScheduler.isSchedulerNamespace(name)
                || KofTetris.isTetrisNamespace(name)
                || KofMedia.isStaticNamespace(name)
                || KofMath.isMathNamespace(name)
                || KofStrings.isStringsNamespace(name)
                || KofBuffer.isBufferNamespace(name)
                || KofEncoding.isEncodingNamespace(name)
                || KofNet.isNetNamespace(name)
                || KofRandom.isRandomNamespace(name)
                || KofRng.isRngNamespace(name)
                || KofUuid.isUuidNamespace(name);
    }

    /**
     * §134: o nome simples, via imports da unit, aponta para um tipo
     * nos entries do ExternalClasspath (--classpath/--deps)? Usado para não
     * marcar SEM011 no receiver de chamada estática externa (Greeter.hello),
     * que o lowering resolve via knows()/resolveMethod().
     */
    private static boolean isExternalImportedClass(SemanticAnalyzer sa, String name) {
        if (sa.externalTypes() == null || sa.unit() == null) return false;
        Type t = MemberResolver.qualifyViaImports(sa.unit(), name, sa.externalTypes());
        return t instanceof Type.ClassType ct && !ct.packageName().isEmpty()
                && sa.externalTypes().knows(ct.internalName());
    }

    /**
     * §336 (#459): um "identificador" com `&lt;...&gt;`, `[]` ou `?` so pode ter
     * nascido do type-ref de `as`/`instanceof` (o lexer nao mistura esses
     * caracteres em nomes) — Type.of/toType cuidam da resolucao. Espelha a
     * isencao do tipo-funcao (bug 127) e o conservadorismo de
     * MemberResolver.isUnresolvedSimpleType.
     */
    static boolean looksLikeTypeRefName(String name) {
        if (name == null || name.isEmpty()) return false;
        // bug 127: tipo-funcao `() -> Int` / `(Int) -> Bool`
        if (name.startsWith("(") && name.contains(" -> ")) return true;
        return (name.indexOf('<') >= 0 && name.endsWith(">"))
                || name.endsWith("[]") || name.endsWith("?");
    }
}
