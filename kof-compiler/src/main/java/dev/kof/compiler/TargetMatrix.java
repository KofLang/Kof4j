package dev.kof.compiler;

import java.util.List;

/**
 * Fase 2 (plataforma): matriz de targets e capacidades — ÚNICA fonte da
 * topologia backend×frontend. Proibido espalhar {@code if target == X} pelo
 * compilador/CLI (restrição 20 do plano). Os gates de CAPACIDADE por API
 * (supportedOn/gapCode nos Kof*.java: DB001, WEB001, ...) continuam a fonte
 * do que CADA API suporta em CADA alvo; esta classe só decide quem pode ser
 * backend e quem pode ser frontend.
 *
 * <pre>
 *   BACKEND:  JVM · NATIVE(+arch) · SCRIPT · (ANDROID = app inteira)
 *   FRONTEND: JS (KofJS) · SCRIPT (KofScript)
 *   WASM:     planejado Fase 6 — rejeitado com gap honesto, nunca silencioso
 * </pre>
 */
public final class TargetMatrix {

    private TargetMatrix() {}

    /** Targets que podem servir de backend. */
    public static boolean isBackend(Target t) {
        return t == Target.JVM || t.isNative() || t == Target.SCRIPT;
    }

    /** Targets que podem servir de frontend. */
    public static boolean isFrontend(Target t) {
        return t == Target.JS || t == Target.SCRIPT;
    }

    /** ANDROID empacota o app inteiro (não é um lado da matriz). */
    public static boolean isWholeApp(Target t) {
        return t == Target.ANDROID;
    }

    /**
     * Valida uma combinação backend×frontend (null = não especificado).
     * Retorna null se válida; senão mensagem legível para a CLI reportar
     * ANTES de compilar.
     */
    public static String validate(Target backend, Target frontend) {
        if (backend != null && !isBackend(backend)) {
            return "target '" + name(backend) + "' não pode ser backend"
                    + " (backend: jvm, native, script)";
        }
        if (frontend != null && !isFrontend(frontend)) {
            return "target '" + name(frontend) + "' não pode ser frontend"
                    + " (frontend: kofjs, script)";
        }
        return null;
    }

    /** Nome canônico do alvo (o que vai no kof.toml / CLI). */
    public static String name(Target t) {
        return switch (t) {
            case JVM -> "jvm";
            case NATIVE -> "native";
            case NATIVE_RISCV64 -> "native.riscv64";
            case NATIVE_AARCH64 -> "native.aarch64";
            case JS -> "kofjs";
            case ANDROID -> "android";
            case SCRIPT -> "script";
        };
    }

    /**
     * Gap code honesto para um frontend pedido que ainda não existe como
     * target (ex.: "wasm"/"kofwebasm" → WASM001). null se é frontend real.
     */
    public static String frontendGapFor(String requested) {
        if (requested == null) return null;
        String r = requested.toLowerCase();
        if (r.equals("wasm") || r.equals("kofwasm") || r.equals("kofwebasm")
                || r.equals("kofwebassembly") || r.equals("webassembly")) {
            return "WASM001";
        }
        return null;
    }

    /**
     * Resolve o valor de {@code --backend/--frontend}/kof.toml para um
     * Target. Retorna null se desconhecido; {@code outError} (opcional)
     * recebe a mensagem honesta — inclui WASM (planejado, não existe).
     */
    public static Target parse(String value, List<String> outError) {
        if (value == null) return null;
        String gap = frontendGapFor(value);
        if (gap != null) {
            if (outError != null) outError.add(
                    "target '" + value + "' (KofWebAssembly) ainda não existe — planejado"
                            + " na Fase 6 do plano de plataforma (docs/development/future/PLATFORM-PLAN.md) ["
                            + gap + "]");
            return null;
        }
        return switch (value.trim().toLowerCase()) {
            case "jvm" -> Target.JVM;
            case "native" -> Target.NATIVE;
            case "native.risc", "native.riscv64", "native.riscv" -> Target.NATIVE_RISCV64;
            case "native.arm", "native.aarch64", "native.aarch" -> Target.NATIVE_AARCH64;
            case "js", "kofjs" -> Target.JS;
            case "android" -> Target.ANDROID;
            case "script", "kofscript" -> Target.SCRIPT;
            default -> {
                if (outError != null) outError.add("unknown target: " + value);
                yield null;
            }
        };
    }

    /** Combinações backend×frontend suportadas (para docs/info/CLI). */
    public static List<String> supportedCombinations() {
        return List.of(
                "jvm+kofjs", "jvm+script",
                "native+kofjs", "native+script",
                "native.riscv64+kofjs", "native.riscv64+script",
                "native.aarch64+kofjs", "native.aarch64+script",
                "script+kofjs", "script+script");
    }
}
