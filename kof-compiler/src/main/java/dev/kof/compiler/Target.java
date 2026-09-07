package dev.kof.compiler;

public enum Target {
    JVM,
    NATIVE,
    NATIVE_RISCV64,
    NATIVE_AARCH64,
    JS,
    ANDROID,
    /**
     * KofScript — coringa de execução (fase 2 do plano de plataforma).
     * Não emite artefatos: o programa é interpretado na IR compartilhada
     * ({@code CompilerPipeline.interpret}). {@code compile}/{@code build}
     * com este target falham com COMP003 (nunca fallback silencioso).
     */
    SCRIPT;

    public boolean isNative() {
        return this == NATIVE || this == NATIVE_RISCV64 || this == NATIVE_AARCH64;
    }

    public boolean isScript() {
        return this == SCRIPT;
    }

    public String nativeArch() {
        return switch (this) {
            case NATIVE -> "x86_64";
            case NATIVE_RISCV64 -> "riscv64";
            case NATIVE_AARCH64 -> "aarch64";
            default -> "unknown";
        };
    }
}
