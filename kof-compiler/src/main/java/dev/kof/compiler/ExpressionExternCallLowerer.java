package dev.kof.compiler;

import java.util.List;

/**
 * Lowering da face extern/FFI de MethodCallExpr (higiene de gate check_500 —
 * extraída do dispatcher {@code ExpressionMethodCallLowerer}; o guard de
 * `externSignatures` fica no dispatcher). Devolve o localIdx quando emite, ou
 * {@code -1} quando o binding extern não está ativo — o dispatcher continua a
 * cadeia (mesma convenção do {@code ExpressionStaticCallLowerer}).
 */
final class ExpressionExternCallLowerer {

    private ExpressionExternCallLowerer() {}

    /** Diagnóstico de gap (XXX00x) — posição da chamada, mensagem e código prontos. */
    private static void gapError(CompilerDriver driver, MethodCallExpr mc, String msg, String code) {
        if (driver.currentDiagnostics == null) return;
        SourcePosition p = mc.position();
        driver.currentDiagnostics.error(p != null ? p.file() : "",
                p != null ? p.line() : 0, p != null ? p.column() : 0, 0, msg, code);
    }

    static int lower(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                        String owner, int localIdx, List<IRLocalVariable> locals) {
        // #763: mesmo-nome = overloads; a assinatura vem do typer (1 resolução, sem 2º oracle).
    ExternalFunctionNode ext = driver.semanticAnalyzer != null
            ? driver.semanticAnalyzer.getExternChoice(mc) : null;
    if (ext == null) {
        List<ExternalFunctionNode> cands = driver.externSignatures.get(mc.methodName());
        if (cands.size() == 1) {
            ext = cands.get(0);
        } else {
            gapError(driver, mc, "extern '" + mc.methodName() + "' has " + cands.size()
                    + " overloads; this call did not resolve one (FFI001)", "FFI001");
            return localIdx;
        }
    }
    if (CompilerFfiBinding.isExternBound(driver, ext)) {
        // #431/§61 (Native): ABI escalar DIRETA — os args ficam crus na pilha de
        // operandos (mesma convenção push dos calls internos) e o backend emite o
        // marshaling SysV + `call sym@PLT` (precedente: consumidor SQLite/DB001).
        // O KofCall carrega owner=kof.ffi + methodName "lib::simbolo" como
        // metadado — nenhum lowering de usuário gera "::" num nome de método.
        if (driver.target.isNative()) {
            // #431 fatia 2: o mesmo KofCall "lib::simbolo" serve as 3 archs —
            // o backend escolhe o shim (x86 SysV / riscv LP64 / aarch AAPCS64
            // via tradução do texto riscv).
            List<Type> ffiParams = new java.util.ArrayList<>();
            for (var p : ext.parameters()) {
                Type scalar = FfiSignature.paramType(p.type());
                if (scalar != null) {
                    ffiParams.add(scalar);
                } else {
                    // 3.7: `record` por valor — o Type carrega os campos p/ o
                    // backend montar os registradores sem precisar do driver.
                    String fc = FfiSignature.structFieldChars(p.type(), driver);
                    if (fc != null) {
                        ffiParams.add(FfiStructLayout.structTypeOfChars(fc));
                    } else {
                        // D6-2/3.7: array escalar `T[]`→`ptr` (marker kof.ffi/array).
                        Character ae = FfiSignature.arrayElemChar(p.type());
                        if (ae != null) {
                            // A fatia COPY-OUT: borrowed = o marker "arrayout"
                            // (o payload direto); o default = "array" (by-value).
                            if ("borrowed".equals(ext.ownership())) {
                                ffiParams.add(FfiStructLayout.arrayOutPtrType(ae));
                            } else {
                                ffiParams.add(FfiStructLayout.arrayPtrType(ae));
                            }
                        } else if (FfiSignature.isStringArray(p.type())) {
                            // D-MEM-FFI-CROSS-FULL face 2: `String[]`→`char**`
                            // (marker arrayPtrType('S')).
                            ffiParams.add(FfiStructLayout.arrayPtrType('S'));
                        } else if (FfiSignature.isBufferParam(p.type())) {
                            // D6-3/D-R3-BUFFER (fatia A2): Buffer(U8) INOUT — o
                            // backend x86-64 passa o payload (obj+24) direto.
                            ffiParams.add(FfiStructLayout.bufferPtrType());
                        } else {
                            // M1 callbacks on Native x86-64: cb-ptr marker (ou null).
                            ffiParams.add(FfiStructLayout.cbMarkerOrNull(p.type()));
                        }
                    }
                }
            }
            for (int i = 0; i < mc.arguments().size(); i++) {
                ExpressionNode arg = mc.arguments().get(i);
                localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                // #549/§370: o marshaling SysV lê os bits pela classe do SLOT — o
                // argumento chega já convertido ao tipo declarado (regra comum).
                ExternArgumentCoercion.coerce(driver, arg, i < ffiParams.size() ? ffiParams.get(i) : null, ops, locals);
            }
            // 3.7 fatia 2: retorno `record` por valor — o KofCall carrega o
            // ClassType REAL do record (resolução de `.campo()` a jusante e o
            // backend materializa o objeto a partir do registrador/scratch).
            Type ffiRet = FfiSignature.returnType(ext.returnType());
            if (FfiSignature.returnChar(ext.returnType()) == null) {
                Type st = FfiSignature.structReturnType(ext.returnType(), driver);
                if (st != null) ffiRet = st;
            }
            ops.add(new KofCall(new Type.ClassType("kof", "ffi", List.of()),
                    ext.library() + "::" + ext.name(), ffiParams,
                    ffiRet, KofCallKind.FUNCTION));
            return localIdx;
        }
        // FFI (R3, generalizado): kof_ffi(lib, nome, sig, Object[] args).
        Type object = new Type.ClassType("java.lang", "Object", List.of());
        Type objectArray = new Type.ArrayType(object);
        ops.add(new KofLoadLiteral(BuiltinTypes.STRING,
                ext.library() != null ? ext.library() : ""));
        ops.add(new KofLoadLiteral(BuiltinTypes.STRING, ext.name()));
        ops.add(new KofLoadLiteral(BuiltinTypes.STRING,
                FfiSignature.signature(driver, ext)));
        int n = mc.arguments().size();
        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, n));
        ops.add(new KofNewArray(object));
        for (int i = 0; i < n; i++) {
            ops.add(new KofDup());
            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, i));
            ExpressionNode arg = mc.arguments().get(i);
            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
            Type argType = ExpressionTyper.inferExprType(driver, arg, locals);
            // #549/§370: `Double→Float`/`Int→Float`… viram a conversão real ANTES do
            // box — o `kof_ffi` faz cast pelo wrapper do slot (Float), nunca reinterpreta.
            Type slot = i < ext.parameters().size() ? FfiSignature.paramType(ext.parameters().get(i).type()) : null;
            if (ExternArgumentCoercion.coerce(driver, arg, slot, ops, locals)) argType = slot;
            if (argType instanceof Type.PrimitiveType) {
                TypeEmitter.boxPrimitive(ops, argType);
            }
            ops.add(new KofArrayStore(object));
        }
        String ffiHelper = FfiSignature.isVoidFFI(ext.returnType())
                ? "kof_ffi_void" : "kof_ffi";
        ops.add(new KofCall(new Type.ClassType("kof", "ffi", List.of()), ffiHelper,
                List.of(BuiltinTypes.STRING, BuiltinTypes.STRING, BuiltinTypes.STRING, objectArray),
                FfiSignature.callReturnType(driver, ext), KofCallKind.FUNCTION));
        return localIdx;
    }
    // isExternBound == false: NÃO tratado — o dispatcher continua a cadeia
    // (scheduler-method/sleep/bare-call), exatamente como o corpo original caía.
    return -1;
    }
}
