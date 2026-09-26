package dev.kof.compiler;

/**
 * §500 slice B — o receiver de um FIELD ESTÁTICO acessado pelo NOME da
 * classe ({@code Integer.MAX_VALUE}, {@code Double.NaN}, {@code TimeUnit.SECONDS}
 * importado). Espelha o {@code jdkStaticOwner} do §499 (chamadas estáticas)
 * MAS só com os nomes reais de classe do JDK: os apelidos do Kof para
 * primitivos ({@code Int}/{@code Bool}/{@code string}) continuam SEM050 no
 * caminho de valor e {@code Int.MAX_VALUE} continua fake-idiom — o idioma da
 * constante é o mapeado ({@code Integer.MAX_VALUE}) ou um {@code val} próprio.
 */
final class StaticClassReceiver {

    private StaticClassReceiver() {}

    static String jdkFieldOwner(String name) {
        return switch (name) {
            case "String" -> "java/lang/String";
            case "Integer" -> "java/lang/Integer";
            case "Long" -> "java/lang/Long";
            case "Float" -> "java/lang/Float";
            case "Double" -> "java/lang/Double";
            case "Boolean" -> "java/lang/Boolean";
            case "Character" -> "java/lang/Character";
            case "Byte" -> "java/lang/Byte";
            case "Short" -> "java/lang/Short";
            case "Object" -> "java/lang/Object";
            default -> null;
        };
    }

    static boolean isJdkFieldOwnerName(String name) {
        return jdkFieldOwner(name) != null;
    }

    /**
     * FACE SEMÂNTICA (typer): null = o receiver NÃO é nome de classe externa
     * (face não aplicável — segue o fluxo normal); senão o tipo do campo
     * público-estático, ou UNKNOWN depois de emitir o SEM025 honesto (R6 —
     * existência validada aqui, nunca `getfield "?"` morto adiante).
     */
    static Type semInfer(SemanticAnalyzer sa, FieldAccessExpr fa, Type recvType) {
        if (sa == null || !(recvType instanceof Type.UnknownType)
                || !(fa.receiver() instanceof IdentifierExpr id)) {
            return null;
        }
        if (!(classTypeOf(sa, id.name()) instanceof Type.ClassType qc)
                || !sa.isExternal(qc)) {
            return null;
        }
        String desc = sa.externalTypes() == null ? null
                : sa.externalTypes().resolveStaticFieldType(qc.internalName(), fa.fieldName());
        if (desc != null) {
            return ExternalClasspath.typeFromDescriptor(desc);
        }
        if (sa.diagnostics() != null) {
            sa.diagnostics().error(fa,
                    "Cannot resolve static field '" + fa.fieldName()
                            + "' on type '" + id.name() + "'",
                    "SEM025");
        }
        return Type.UnknownType.UNKNOWN;
    }

    /**
     * FACE DE LOWERING: tipo do campo estático externo acessado pelo NOME da
     * classe (sem shadow de local), ou null quando a face não se aplica/não
     * resolve (sem diagnóstico — o SEM025 já gateou a existência). Usada pelo
     * `ExpressionTyper` (dispatch do println dá box ao primitivo) e pelo
     * emit do `KofGetStatic`.
     */
    static Type emitType(CompilerDriver driver, FieldAccessExpr fa,
                         java.util.List<IRLocalVariable> locals) {
        if (driver == null || driver.semanticAnalyzer == null
                || !(fa.receiver() instanceof IdentifierExpr id)
                || driver.isLocalVarName(id.name(), locals)) {
            return null;
        }
        if (!(classTypeOf(driver.semanticAnalyzer, id.name()) instanceof Type.ClassType qc)
                || !driver.externalClasspath.knows(qc.internalName())) {
            return null;
        }
        String desc = driver.externalClasspath
                .resolveStaticFieldType(qc.internalName(), fa.fieldName());
        return desc == null ? null : ExternalClasspath.typeFromDescriptor(desc);
    }

    /**
     * Emit: `getstatic` REAL (owner+descritor do reflection/classpath).
     * FACE JVM-BACKED (§510): JS e Native NÃO têm o JVM por trás da classe —
     * emitir o op lá seria o `ReferenceError: java_lang_Integer` silencioso
     * medido no probe. Nesses alvos o compile falha com o código nomeado
     * INTEROP003 (R6: nunca divergência silenciosa; rule 5: paridade ou
     * diagnóstico). JVM/SCRIPT/ANDROID (ART) resolvem real.
     */
    static boolean emitStatic(CompilerDriver driver, FieldAccessExpr fa,
                              java.util.List<IRLocalVariable> locals,
                              java.util.List<KofOperation> ops) {
        Type t = emitType(driver, fa, locals);
        if (t == null) return false;
        if (driver.target == Target.JS || driver.target.isNative()) {
            if (driver.currentDiagnostics != null) {
                SourcePosition pos = fa.position();
                driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                        pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                        "static field '" + ((IdentifierExpr) fa.receiver()).name() + "."
                                + fa.fieldName() + "' of an external class requires a "
                                + "JVM-backed target (JVM/Script/Android); not available on "
                                + driver.target + " (INTEROP003)",
                        "INTEROP003");
            }
            return true;
        }
        Type.ClassType owner = (Type.ClassType) classTypeOf(
                driver.semanticAnalyzer, ((IdentifierExpr) fa.receiver()).name());
        ops.add(new KofGetStatic(owner, fa.fieldName(), t));
        return true;
    }

    /** ClassType do receiver-NAME (mapa JDK ou imports da unit); null se não. */
    static Type classTypeOf(SemanticAnalyzer sa, String name) {
        if (sa == null || name == null || name.isEmpty()) return null;
        String jdk = jdkFieldOwner(name);
        if (jdk != null) {
            return CompilerTypes.ownerTypeFromInternal(jdk, sa);
        }
        if (sa.unit() != null && sa.externalTypes() != null) {
            Type q = MemberResolver.qualifyViaImports(sa.unit(), name, sa.externalTypes());
            if (q == null && name.contains(".")) {
                q = MemberResolver.qualifiedType(Type.of(name));
            }
            return q instanceof Type.ClassType ct ? ct : null;
        }
        return null;
    }
}
