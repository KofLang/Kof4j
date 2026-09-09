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

    private static final java.util.Set<String> B64_FNS = java.util.Set.of(
            "kof_encoding_base64Encode", "kof_encoding_base64Decode");

    static EncodingCall staticMethod(String namespace, String name, List<Type> argTypes) {
        int argc = argTypes.size();
        return switch (name) {
            case "hexEncode", "base64Encode", "base64Decode" -> argc == 1
                    ? new EncodingCall("kof_encoding_" + name, STR, List.of(STR)) : null;
            case "hexDecode" -> argc == 1
                    ? new EncodingCall("kof_encoding_hexDecode", STR, List.of(STR)) : null;
            default -> null;
        };
    }

    /**
     * hex* é transformação pura de byte — presente em todos os targets.
     * ENC002 (padrão SECN000): base64 reusa kof_b64_*_internal do runtime
     * x86 (crypto lane); o riscv/aarch não tem esses símbolos (asm puro, sem
     * libc) — gate honesto em compile-time até o port, nunca link quebrado.
     */
    static boolean supportedOn(String function, Target target) {
        if (B64_FNS.contains(function)
                && (target == Target.NATIVE_RISCV64 || target == Target.NATIVE_AARCH64)) {
            return false;
        }
        return true;
    }

    static String gapCode(String function) {
        return B64_FNS.contains(function) ? "ENC002" : "ENC001";
    }
}
