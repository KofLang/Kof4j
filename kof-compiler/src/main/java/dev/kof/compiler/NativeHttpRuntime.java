package dev.kof.compiler;

/**
 * HTTP/1.1 client for the Native target (HTTP002).
 *
 * Plain HTTP only — https:// throws at runtime (TLS in asm stays a separate
 * gap). DNS: dotted IPv4 {@code a.b.c.d} is parsed inline; any other host
 * falls back to 127.0.0.1 (same convention as the MySQL wire runtime).
 *
 * Response framing: {@code Connection: close} + read-until-EOF. The body is
 * everything after the {@code \r\n\r\n} header terminator. The status code
 * digit-parse honours any "HTTP/x.y NNN" first line.
 *
 * Own module per the ≤500-lines rule (03/09): NativeRuntime must not grow.
 * Fragmentos (REFACTOR-500 Fase 8): NativeHttpPrimitives (data + buffer
 * helpers), NativeHttpParseUrl (parse de URL) e NativeHttpCore (request core
 * + wrappers). A concatenação abaixo preserva o assembly injetado byte-a-byte.
 */
final class NativeHttpRuntime {

    private NativeHttpRuntime() {}

    static void emitHttpFunctions(StringBuilder sb) {

        sb.append(NativeHttpPrimitives.source());
        sb.append(NativeHttpParseUrl.source());
        sb.append(NativeHttpCore.source());

    }
}
