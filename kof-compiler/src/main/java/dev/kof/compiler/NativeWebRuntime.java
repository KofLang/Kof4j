package dev.kof.compiler;

/**
 * kof.web server para o target Nativo (WEB002).
 *
 * Orquestrador: concatena os fragmentos (REFACTOR-500 Fase 8 + merge
 * planning-future ← beta-0.3.0) na ordem original, preservando o assembly
 * injetado. Fragmentos: NativeWebCore (dados + primitivas + app_new/body/
 * route), NativeWebListen (listen + handle_client + write_body_response),
 * NativeWebRequestContext (method/path/query/header/param + match por
 * segmentos), NativeWebResponseContext (status/headerSet/status_text) e
 * NativeWebResponses (helpers de resposta).
 */
final class NativeWebRuntime {

    private NativeWebRuntime() {}

    static void emitWebFunctions(StringBuilder sb) {
        sb.append(NativeWebCore.source());
        sb.append(NativeWebListen.source());
        sb.append(NativeWebRequestContext.source());
        sb.append(NativeWebParamMatch.source());
        sb.append(NativeWebResponseContext.source());
        sb.append(NativeWebResponses.source());
    }
}