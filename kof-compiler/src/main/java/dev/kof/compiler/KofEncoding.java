package dev.kof.compiler;

import java.util.List;

/**
 * Compile-time dispatch for {@code kof.encoding} (STDLIB S4, plan-stdlib-
 * expansion). Transformações puras de byte — paridade byte-idêntica nos 4
 * targets (JVM / Native x86 / Native riscv-aarch / JS / interpretador).
 *
 * S4-wedge: {@code encoding.hexEncode(s)} / {@code encoding.hexDecode(s)}.
 * Semântica travada na matriz {@code stdenc}:
 *  - hexEncode: cada byte -> 2 dígito hex minúsculo; null=>null, ""=>"".
 *  - hexDecode: pares de dígitos -> byte (a-f/A-F/0-9); char inválido -> 0;
 *    comprimento ímpar: último dígito é o nibble alto (baixo=0); null=>null.
 * base64/URL = S4.2 (próximo degrau).
 */
public final class KofEncoding {

    private KofEncoding() {}

    private static final Type STR = BuiltinTypes.STRING;

    static final List<String> NAMESPACES = List.of("encoding");

    static boolean isEncodingNamespace(String name) {
        return NAMESPACES.contains(name);
    }

    record EncodingCall(String function, Type returnType, List<Type> parameterTypes) {}


    /** X10 fatia 1: nomes aceitos pelo staticMethod (catálogo p/ LSP).
     *  GUARDA: StdCatalogTest exige == case literals do switch(name) abaixo. */
    // 19/09 LSP-A fatia 3: base64/url viviam no case de família do staticMethod
    // (ligado por vírgula, invisível ao lock de 1º literal) — 2->6.
    static List<String> functions() {
        return List.of("hexEncode", "hexDecode", "base64Encode",
                "base64Decode", "base64UrlEncode", "base64UrlDecode",
                "urlEncode", "urlDecode");
    }
    // #1183: as guardas de tipo dos params (espelho KofMath/KofStrings). A
    // ligação era só-por-arity, então encoding.base64Encode(123) passava o
    // check e emitia invokestatic com um int na pilha — VerifyError no JVM,
    // "" silencioso no JS, SIGSEGV no Native. Número não alarga em silêncio
    // (SEM025, R6).
    private static boolean isStr(Type t) {
        return STR.equals(t) || "String".equals(t.toString());
    }

    static EncodingCall staticMethod(String namespace, String name, List<Type> argTypes) {
        int argc = argTypes.size();
        return switch (name) {
            case "hexEncode", "base64Encode", "base64Decode",
                    "base64UrlEncode", "base64UrlDecode",
                    "urlEncode", "urlDecode" -> argc == 1 && isStr(argTypes.get(0))
                    ? new EncodingCall("kof_encoding_" + name, STR, List.of(STR)) : null;
            case "hexDecode" -> argc == 1 && isStr(argTypes.get(0))
                    ? new EncodingCall("kof_encoding_hexDecode", STR, List.of(STR)) : null;
            default -> null;
        };
    }

    // ENC002 FECHADO (09/09): base64/base64Url portados p/ riscv B23 +
    // aarch64 translator (mesma spec tolerante do x86/JVM/JS). supportedOn
    // volta quando outro gap nascer.
    static boolean supportedOn(@SuppressWarnings("unused") String function,
            @SuppressWarnings("unused") Target target) {
        return true;
    }

    static String gapCode(String function) {
        return "ENC001";
    }
}
