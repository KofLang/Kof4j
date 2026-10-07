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
        return t == Target.JVM || t.isNative() || t == Target.SCRIPT || t == Target.WASM;
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
            return "target '" + name(backend) + "' cannot be a backend"
                    + backendGapHint(backend);
        }
        if (frontend != null && !isFrontend(frontend)) {
            if (frontend == Target.WASM || frontend == Target.WASI) {
                return "target '" + name(frontend) + "' cannot be a frontend yet"
                        + " — no host/runtime (WASM001; the wasm backend emits only the"
                        + " scalar subset — unit 15.2): see"
                        + " docs/development/wasm-wasi-plan.md (TIER 15, units 15.3+,"
                        + " issue #776); frontend: kofjs, script";
            }
            return "target '" + name(frontend) + "' cannot be a frontend"
                    + " (frontend: kofjs, script)";
        }
        return null;
    }

    /**
     * 15.1 (#776) colocou wasm/wasi na topologia; 15.2 deu ao wasm um backend
     * de subset escalar. WASI ainda recusa — a recusa nomeia o gap (WASM001),
     * o plano e a unidade que implementa, em vez da lista generica de backends
     * (R6: nunca "unknown" para alvo real).
     */
    private static String backendGapHint(Target t) {
        if (t == Target.WASI) {
            return " yet — it has no emitting backend (WASM001): the plan is"
                    + " docs/development/wasm-wasi-plan.md (TIER 15, unit 15.3,"
                    + " issue #776); backend: jvm, native, script, wasm";
        }
        return " (backend: jvm, native, script, wasm)";
    }

    /** Nome canônico do alvo (o que vai no kof.toml / CLI). */
    public static String name(Target t) {
        return switch (t) {
            case JVM -> "jvm";
            case NATIVE -> "native";
            case NATIVE_RISCV64 -> "native.riscv64";
            case NATIVE_AARCH64 -> "native.aarch64";
            case NATIVE_RISCV32 -> "native.riscv32";
            case NATIVE_MCU_ARM -> "native.cortex-m";
            case JS -> "kofjs";
            case ANDROID -> "android";
            case SCRIPT -> "script";
            case WASM -> "wasm";
            case WASI -> "wasi";
        };
    }

    /**
     * Gap code honesto para um frontend pedido que ainda não existe como
     * target (ex.: "wasm"/"kofwebasm" → WASM001). null se é frontend real.
     */
    public static String frontendGapFor(String requested) {
        if (requested == null) return null;
        String r = requested.toLowerCase();
        // 15.1 (07/10, #776): "wasm"/"wasi" sao targets REAIS da topologia
        // (parse abaixo) — o gap deles e de EMISsAO (WASM001 via validate/
        // compile), nao de existencia. Os ALIAS/solecismos longos (incl. os
        // cobertos pela lane .30:9092 em 233724b40) seguem gap de string.
        if (r.equals("kofwasm") || r.equals("kofwebasm")
                || r.equals("kofwebassembly") || r.equals("webassembly")
                || r.equals("wasm32") || r.equals("wasm32-wasi")
                || r.equals("wasi-preview1") || r.equals("wasip1")
                || r.equals("kofwasi")) {
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
                    "target '" + value + "' (KofWebAssembly/WASI) does not exist yet — the"
                            + " wasm/wasi frontend is promoted and is a 0.6.0 cut gate"
                            + " (docs/development/wasm-wasi-plan.md, issue #776,"
                            + " D-WEB-WASI-DEFAULT-0710, docs/development/DECISIONS.md) ["
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
            case "wasm" -> Target.WASM;
            case "wasi" -> Target.WASI;
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
