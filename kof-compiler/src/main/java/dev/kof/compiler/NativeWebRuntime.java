package dev.kof.compiler;

/**
 * kof.web server para o target Nativo (WEB002).
 *
 * T1 (feito): accept loop + resposta "hello" 200.
 * T2 (aqui): parse METHOD+PATH na request line e match contra rotas
 *            registradas; handler só é invocado no T3.
 * T3 (próximo): dispatch do lambda com trampolim (objeto→vtable[0]→invoke).
 *
 * Módulo separado (≤500 linhas — AGENTS.md 03/09). Fragmentos
 * (REFACTOR-500 Fase 8): NativeWebCore (dados + primitivas),
 * NativeWebListen (listen + handle_client) e NativeWebResponses (helpers de
 * resposta). A concatenação abaixo preserva o assembly injetado byte-a-byte.
 */
final class NativeWebRuntime {

    private NativeWebRuntime() {}

    static void emitWebFunctions(StringBuilder sb) {
        sb.append(NativeWebCore.source());
        sb.append(NativeWebListen.source());
        sb.append(NativeWebResponses.source());
    }
}
