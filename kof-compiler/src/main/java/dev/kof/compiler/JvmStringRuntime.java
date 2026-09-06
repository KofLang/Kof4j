package dev.kof.compiler;

/**
 * Runtime do String → numérico (OBS-010) — gerado no KofRuntime junto com o JvmRuntime.
 * Separado num arquivo próprio porque o constant pool do
 * javac limita cada string a 65535 bytes.
 * REFACTOR-500 Fase 8: o source foi dividido em fragmentos (classes
 * Jvm*Part) no mesmo pacote; a concatenacao preserva byte-a-byte.
 */
final class JvmStringRuntime {

    private JvmStringRuntime() {}

    static String source() {
        return JvmStringCoreRuntime.source() + JvmStringSecurityRuntime.source() + JvmStringValidationRuntime.source() + JvmStringObsRuntime.source() + JvmStringMiscRuntime.source();
    }
}
