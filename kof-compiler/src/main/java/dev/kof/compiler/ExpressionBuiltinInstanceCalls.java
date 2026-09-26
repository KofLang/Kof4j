package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Chamadas de instância em receivers BUILTIN por domínio (enum name / kof.web /
 * kof.media / kof.io) — extraído verbatim do ExpressionInstanceCallLowerer
 * (§140 split-5, regra ≤500). Cada bloco termina em return localIdx: quem casa,
 * consome a chamada inteira; o caller decide se continua (Io devolve localIdx
 * quando nenhum método casa — o fluxo do caller segue).
 */
final class ExpressionBuiltinInstanceCalls {

    private ExpressionBuiltinInstanceCalls() {}

    /**
     * D-FULL-PARITY-050 row 13: faces de kof.io JA portadas para o cross
     * riscv64/aarch64 (fatia {@code NativeRiscvAsmIoStat}). As demais seguem
     * com gate honesto NAT006 (§427) — nunca um link break silencioso (R6).
     */
    private static final Set<String> CROSS_IO_READY = Set.of(
            "kof_io_file_exists", "kof_io_file_is_file", "kof_io_file_is_dir",
            "kof_io_read_text", "kof_io_write_text", "kof_io_append_text",
            "kof_io_delete", "kof_io_dir_create", "kof_io_dir_create_dirs",
            "kof_io_file_size", "kof_io_read_bytes", "kof_io_write_bytes",
            "kof_io_append_bytes", "kof_io_dir_list", "kof_io_read_range",
            "kof_io_read_range_path", "kof_io_file_name", "kof_io_path_file_name",
            "kof_io_path_parent", "kof_io_path_extension", "kof_io_path_is_absolute",
            "kof_io_path_resolve", "kof_io_path_normalize", "kof_io_path_to_absolute", "kof_io_dir_delete", "kof_io_file_modified_time", "kof_io_file_is_symlink", "kof_io_file_move_to", "kof_io_file_copy_to");

    /** Diagnóstico de gap honesto (R6) numa chamada kof.web. */
    private static void webGap(CompilerDriver driver, MethodCallExpr mc, String msg, String code) {
        if (driver.currentDiagnostics == null) return;
        SourcePosition p = mc.position();
        driver.currentDiagnostics.error(p != null ? p.file() : "",
                p != null ? p.line() : 0, p != null ? p.column() : 0, 0, msg, code);
    }

    /**
     * Lowering de métodos em instância de enum (name, toString, ordinal, compareTo).
     * Retorna >= 0 se o método foi consumido, ou -1 se não é método de enum tratado aqui.
     *
     * <p>D-ENUM207 (#207): o valor agora é uma INSTÂNCIA de enum real com os
     * métodos {@code name()}/{@code ordinal()}/{@code toString()}/{@code
     * compareTo()} emitidos por {@link CompilerEnumLowering}. O receiver já
     * está na pilha — basta o INVOKEVIRTUAL; o caminho antigo (lista de
     * Strings + {@code kof_enum_ordinal}) empilhava tipo errado.
     */
    static int lowerEnum(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                         String owner, int localIdx, List<IRLocalVariable> locals, Type recvType) {
        if (!CompilerTypes.isEnumType(recvType, driver.currentUnit)) return -1;
        String mn = mc.methodName();
        if (("name".equals(mn) || "toString".equals(mn)) && mc.arguments().isEmpty()) {
            ops.add(new KofCall(recvType, mn, List.of(), BuiltinTypes.STRING, KofCallKind.INSTANCE));
            return localIdx;
        }
        if ("ordinal".equals(mn) && mc.arguments().isEmpty()) {
            ops.add(new KofCall(recvType, "ordinal", List.of(),
                    Type.PrimitiveType.INT, KofCallKind.INSTANCE));
            return localIdx;
        }
        if ("compareTo".equals(mn) && mc.arguments().size() == 1) {
            localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
            ops.add(new KofCall(recvType, "compareTo", List.of(recvType),
                    Type.PrimitiveType.INT, KofCallKind.INSTANCE));
            return localIdx;
        }
        return -1;
    }

    private static void emitEnumValuesList(List<KofOperation> ops, Type enumT, Type listT, List<String> consts) {
        ops.add(new KofCall(listT, "kof_list_new", List.of(), listT, KofCallKind.FUNCTION));
        for (String c : consts) {
            ops.add(new KofDup());
            ops.add(new KofGetStatic(enumT, c, enumT));
            ops.add(new KofCall(listT, "kof_list_add", List.of(enumT), Type.PrimitiveType.VOID, KofCallKind.INSTANCE));
        }
    }

    static int lowerWeb(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                        String owner, int localIdx, List<IRLocalVariable> locals, Type recvType) {
        List<Type> webArgTypes = new ArrayList<>();
        for (ExpressionNode arg : mc.arguments()) webArgTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
        KofWeb.WebCall webCall = KofWeb.instanceMethod(mc.methodName(), webArgTypes);
        if (webCall != null) {
            // AND002: no Android o servidor embutido não tem realização
            // (app móvel não escuta porta) — diagnóstico honesto em compile
            // time (R6), nunca código de servidor que não roda.
            if (driver.target == Target.ANDROID) {
                webGap(driver, mc,
                        "web: embedded server not available on Android — a mobile app "
                                + "does not listen on a port; use interop (AND002)",
                        "AND002");
                return localIdx;
            }
            boolean crossNative = driver.target == Target.NATIVE_RISCV64
                    || driver.target == Target.NATIVE_AARCH64;
            boolean webT1Fn = webCall.function().equals("kof_web_listen")
                    || webCall.function().equals("kof_web_route");
            // §427: the native HTTP server runtime (NativeWebRuntime —
            // kof_web_listen/route) is emitted only on the x86_64 path; the
            // riscv64/aarch64 runtime has no such symbols (loud `ld`
            // undefined-reference). Honest compile-time refusal (NAT007),
            // never a link break (R6).
            if (crossNative && webT1Fn) {
                webGap(driver, mc,
                        "web T1 (listen/route): not available on the riscv64/aarch64"
                                + " native targets yet (NAT007) — the native HTTP server"
                                + " runtime is x86_64-only; use --target native",
                        "NAT007");
                return localIdx;
            }
            boolean nativeWebT1 = driver.target == Target.NATIVE && webT1Fn;
            // WEB001-T1 JS (13/09): routes HTTP + listen liberados no JS — o
            // runtime JsRuntimeUiWeb emite kofWebAppNew/Route/Listen (server
            // GraalJS HttpServer real); ws/TLS seguem WEB004/002. SSE ✅ 16/09
            // handler-scoped (push pós-return do handler = WEB003 residual —
            // o pump JS é single-thread).
            boolean jsWebT1 = driver.target == Target.JS
                    && (webCall.function().equals("kof_web_listen")
                        || webCall.function().equals("kof_web_route")
                        || webCall.function().equals("kof_web_sse_route")
                        || webCall.function().equals("kof_web_app_new"));
            if (driver.target != Target.JVM && driver.target != Target.ANDROID
                    && !nativeWebT1 && !jsWebT1) {
                String webCode = KofWeb.gapCode(webCall.function());
                String webMsg = switch (webCode) {
                    case "WEB002" -> "web TLS: not available on the " + driver.target
                            + " driver.target yet (WEB002)";
                    case "WEB003" -> "web SSE: not available on the " + driver.target
                            + " driver.target yet (WEB003)";
                    case "WEB004" -> "web WebSocket: not available on the " + driver.target
                            + " driver.target yet (WEB004)";
                    case "WEB005" -> "web serveDir: not available on the " + driver.target
                            + " driver.target yet (WEB005)";
                    case "WEB006" -> "web security middleware: not available on the "
                            + driver.target + " driver.target yet (WEB006)";
                    default -> "web: not available on the " + driver.target
                            + " driver.target yet (WEB001)";
                };
                if (driver.currentDiagnostics != null) {
                    driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                            mc.position() != null ? mc.position().line() : 0,
                            mc.position() != null ? mc.position().column() : 0,
                            0, webMsg, webCode);
                }
                return localIdx;
            }
            List<Type> webParams = new ArrayList<>();
            webParams.add(BuiltinTypes.STRING);
            if (KofWeb.isRouteMethod(mc.methodName()) && !"ws".equals(mc.methodName())) {
                ops.add(new KofLoadLiteral(BuiltinTypes.STRING, mc.methodName().toUpperCase()));
                webParams.add(BuiltinTypes.STRING);
            }
            for (ExpressionNode arg : mc.arguments()) {
                webParams.add(ExpressionTyper.inferExprType(driver, arg, locals));
                localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
            }
            ops.add(new KofCall(KofWeb.APP, webCall.function(), webParams,
                    webCall.returnType(), KofCallKind.FUNCTION));
        }
        return localIdx;
    }

    static int lowerMedia(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                          String owner, int localIdx, List<IRLocalVariable> locals, Type recvType) {
        KofMedia.MediaCall mediaCall =
                KofMedia.handleMethod(recvType, mc.methodName(), mc.arguments().size());
        if (mediaCall != null) {
            // §509-era/linha-4 parity: as faces de handle seguiam sem gate de
            // target (buraco latente — só não era alcançado porque o OPEN
            // estático era MEDIA001 nos demais alvos). Agora espelham o gate
            // do ExpressionUiMediaCallLowerer via a tabela KofMedia.mediaFaceReady
            // (JVM/ANDROID sim; x86-64 Video/Audio; demais = gap honesto).
            if (driver.target != Target.JVM && driver.target != Target.ANDROID
                    && !KofMedia.mediaFaceReady(driver.target, mediaCall.function())) {
                String code = KofMedia.gapCode(mediaCall.function());
                if (driver.currentDiagnostics != null) {
                    driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                            mc.position() != null ? mc.position().line() : 0,
                            mc.position() != null ? mc.position().column() : 0,
                            0,
                            mc.methodName() + ": not available on the "
                                    + driver.target + " driver.target yet (" + code + ")",
                            code);
                }
                return localIdx;
            }
            List<Type> mediaParams = new ArrayList<>();
            mediaParams.add(Type.PrimitiveType.INT);      // handle (receiver)
            for (ExpressionNode arg : mc.arguments()) {
                mediaParams.add(ExpressionTyper.inferExprType(driver, arg, locals));
                localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
            }
            ops.add(new KofCall(new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
                    mediaCall.function(), mediaParams,
                    mediaCall.returnType(), KofCallKind.FUNCTION));
        }
        return localIdx;
    }

    static int lowerBuffer(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                           String owner, int localIdx, List<IRLocalVariable> locals, Type recvType) {
        KofBuffer.BufferCall bufferCall =
                KofBuffer.instanceMethod(recvType, mc.methodName(), mc.arguments().size());
        if (bufferCall != null) {
            List<Type> params = new ArrayList<>();
            params.add(recvType); // receiver (kof.Buffer) first — JvmTypeMapper maps it
            for (ExpressionNode arg : mc.arguments()) {
                params.add(ExpressionTyper.inferExprType(driver, arg, locals));
                localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
            }
            ops.add(new KofCall(new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
                    bufferCall.function(), params,
                    bufferCall.returnType(), KofCallKind.FUNCTION));
        }
        return localIdx;
    }

    static int lowerSecret(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                           String owner, int localIdx, List<IRLocalVariable> locals, Type recvType) {
        KofSecurity.SecCall secretCall =
                KofSecurity.instanceMethod(recvType, mc.methodName(), mc.arguments().size());
        if (secretCall != null) {
            List<Type> params = new ArrayList<>();
            params.add(recvType); // receiver (kof.Secret) first — JvmTypeMapper maps it
            for (ExpressionNode arg : mc.arguments()) {
                params.add(ExpressionTyper.inferExprType(driver, arg, locals));
                localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
            }
            ops.add(new KofCall(new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
                    secretCall.function(), params,
                    secretCall.returnType(), KofCallKind.FUNCTION));
        }
        return localIdx;
    }

    static int lowerIo(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                       String owner, int localIdx, List<IRLocalVariable> locals, Type recvType) {
        if (KofIo.isIdentityMethod(mc.methodName())) {
            return localIdx;
        }
        KofIo.IoCall ioCall = KofIo.instanceMethod(recvType, mc.methodName(), mc.arguments().size());
        if (ioCall != null) {
            // §427: the kof.io File/Path/Directory runtime is not ported to the
            // riscv64/aarch64 cross — only the kof_io_strlen/make_string
            // internals exist there (sqlite/JSON). Emitting the call is a loud
            // `ld` undefined-reference, so refuse honestly at compile time
            // (NAT006), never a link break (R6). D-FULL-PARITY-050 row 13:
            // exists()/isFile()/isDirectory() ARE ported (NativeRiscvAsmIoStat)
            // — those emit; every other face keeps the honest NAT006.
            boolean crossIo = driver.target == Target.NATIVE_RISCV64
                    || driver.target == Target.NATIVE_AARCH64;
            if (crossIo && !CROSS_IO_READY.contains(ioCall.function())) {
                if (driver.currentDiagnostics != null) {
                    SourcePosition ioPos = mc.position();
                    driver.currentDiagnostics.error(
                            ioPos != null ? ioPos.file() : "",
                            ioPos != null ? ioPos.line() : 0,
                            ioPos != null ? ioPos.column() : 0, 0,
                            "kof.io: '" + mc.methodName() + "' is not available on the"
                                    + " riscv64/aarch64 native targets yet (NAT006) — the"
                                    + " read/write/dir faces are x86_64-only (exists/isFile/"
                                    + "isDirectory are ported); use --target native or the"
                                    + " JVM/JS/Script drivers",
                            "NAT006");
                }
                return localIdx;
            }
            // §388-A: parâmetro primitivo-array (writeBytes/appendBytes: Int[])
            // recebido um List era aceito em silêncio e morria no runtime (JVM
            // VerifyError na reflection do Run, Script rc=1 mudo, JS mismatch
            // silencioso) — R6 exige diagnóstico no compile time (família
            // SEM098, mas SEM gate de alvo: nenhum target rodava de verdade, a
            // regra 2 do freeze não protege o que já quebrava).
            List<Type> ioFormals = ioCall.parameterTypes();
            if (driver.currentDiagnostics != null) {
                for (int i = 0; i < mc.arguments().size() && i < ioFormals.size(); i++) {
                    if (!(ioFormals.get(i) instanceof Type.ArrayType)) continue;
                    Type ioActual = ExpressionTyper.inferExprType(driver, mc.arguments().get(i), locals);
                    if (ioActual instanceof Type.ClassType ioCt
                            && ("List".equals(ioCt.name()) || "ArrayList".equals(ioCt.name()))) {
                        SourcePosition ioPos = mc.position();
                        driver.currentDiagnostics.error(
                                ioPos != null ? ioPos.file() : "",
                                ioPos != null ? ioPos.line() : 0,
                                ioPos != null ? ioPos.column() : 0, 0,
                                "cannot pass a List to '" + mc.methodName() + "': the kof.io bytes"
                                        + " faces take an Int[] primitive array (training/language/"
                                        + "io.md contract) — a List is not an array on any target"
                                        + " (VerifyError on the JVM, crash under the interpreter,"
                                        + " silent mismatch in KofJS). Fill a primitive array:"
                                        + " val a = new Int[n] with a[i] = v, then f." + mc.methodName()
                                        + "(a)",
                                "SEM099");
                        return localIdx;
                    }
                }
            }
            // receiver File/Path/Directory é apagado pra String
            // path em runtime (empilhado acima); os METHOD args
            // alinham com ioCall.parameterTypes() — a conversão
            // formal (int literal → long slot no readRange)
            // evita o frame bug I/J no visitMaxs
            localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), ioCall.parameterTypes(),
                    ops, owner, localIdx, locals);
            List<Type> ioParams = new ArrayList<>();
            ioParams.add(BuiltinTypes.STRING);
            ioParams.addAll(ioCall.parameterTypes());
            ops.add(new KofCall(new Type.ClassType("kof.io", "Io", List.of()),
                    ioCall.function(), ioParams, ioCall.returnType(), KofCallKind.FUNCTION));
            return localIdx;
        }
        return localIdx;
    }
}
