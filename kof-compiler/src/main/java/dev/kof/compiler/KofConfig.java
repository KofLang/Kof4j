package dev.kof.compiler;

import java.util.List;


/**
 * Compile-time dispatch table for the Kof-native configuration module
 * ({@code kof.config}).
 *
 * <p>The Kof surface is typed and idiomatic:
 *
 * <pre>{@code
 * var port = config.int("server.port", 8080)
 * var url  = config.str("database.url", "jdbc:h2:mem")
 * var debug = config.bool("app.debug", false)
 * var home = config.env("HOME")
 * if (config.has("database.url")) { ... }
 * }</pre>
 *
 * <p>Internally every call maps to a static {@code kof_config_*} function of
 * the generated {@code dev.kof.runtime.KofRuntime} class (JVM target).
 * Sources, in precedence order: explicit file ({@code KOF_CONFIG} env var),
 * environment variable {@code KOF_<KEY>}, profile file
 * ({@code kof.<KOF_PROFILE>.config} or {@code kof.config} in the working
 * directory). Real implementations exist on JVM, Native x86_64 (own asm),
 * riscv64/aarch64 cross (own asm, D-FULL-PARITY-050 row 9, 26/09) and JS
 * ({@code kof_platform}).
 */
public final class KofConfig {

    private KofConfig() {}

    static final Type CONFIG = new Type.ClassType("kof.config", "Config", List.of());

    private static final Type STR = BuiltinTypes.STRING;
    private static final Type INT = Type.PrimitiveType.INT;
    private static final Type LONG = Type.PrimitiveType.LONG;
    private static final Type BOOL = Type.PrimitiveType.BOOL;

    static boolean isConfigNamespace(String name) {
        return "config".equals(name);
    }

    /**
     * kof.config: real on all targets. riscv64/aarch64 gained their own lookup
     * runtime (environ + file/env/profile + interpolation) on 26/09, closing
     * the {@code CONF001} gap (D-FULL-PARITY-050 row 9).
     */
    static boolean supportedOn(Target target) {
        return true;
    }

    record ConfigCall(String function, Type returnType, List<Type> parameterTypes) {}

/** X10 fatia 3: nomes aceitos pelo dispatch real (catálogo p/ LSP).
     *  GUARDA: StdCatalogTest exige == case-literals da fonte abaixo. */
    static List<String> functions() { return List.of("get", "env", "has", "str", "int", "long", "bool", "required"); }

    /** {@code config.<method>(...) } — validates arity and maps to the runtime function. */
    static ConfigCall staticCall(String name, List<Type> argTypes) {
        return switch (name) {
            case "get" -> argTypes.size() == 1
                    ? new ConfigCall("kof_config_get", STR, List.of(STR))
                    : null;
            case "env" -> argTypes.size() == 1
                    ? new ConfigCall("kof_config_env", STR, List.of(STR))
                    : null;
            case "has" -> argTypes.size() == 1
                    ? new ConfigCall("kof_config_has", BOOL, List.of(STR))
                    : null;
            case "str" -> argTypes.size() == 2
                    ? new ConfigCall("kof_config_str", STR, List.of(STR, STR))
                    : null;
            case "int" -> argTypes.size() == 2
                    ? new ConfigCall("kof_config_int", INT, List.of(STR, INT))
                    : null;
            case "long" -> argTypes.size() == 2
                    ? new ConfigCall("kof_config_long", LONG, List.of(STR, LONG))
                    : null;
            case "bool" -> argTypes.size() == 2
                    ? new ConfigCall("kof_config_bool", BOOL, List.of(STR, BOOL))
                    : null;
            // P1 (docs/stdlib/stdlib-config.md §8.2): required — falha no startup se
            // ausente (nenhum null silencioso em deploy).
            case "required" -> argTypes.size() == 1
                    ? new ConfigCall("kof_config_required", STR, List.of(STR))
                    : null;
            default -> null;
        };
    }
}