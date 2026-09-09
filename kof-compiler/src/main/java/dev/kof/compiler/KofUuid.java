package dev.kof.compiler;

import java.util.List;

/**
 * Compile-time dispatch for {@code kof.uuid} (STDLIB S3b, plan-stdlib-
 * expansion).
 *
 * S3b-wedge: {@code uuid.v4()} — 16 bytes aleatórios formatados como
 * {@code 8-4-4-4-12} com version=4 e variant=10 (RFC 4122).
 * Fonte de entropia por target: x86 kof_sec_random_hex (getrandom),
 * JS kof_platform.randomBytesHex, JVM SecureRandom.
 * SECN000 (padrão crypto lane): riscv64/aarch64 (asm puro, sem libc) não
 * tem primitiva de random — gate honesto em compile-time. v7/ulid = S3b.2
 * (exigem clock + mais bytes aleatórios).
 */
public final class KofUuid {

    private KofUuid() {}

    private static final Type STR = BuiltinTypes.STRING;

    static final List<String> NAMESPACES = List.of("uuid");

    static boolean isUuidNamespace(String name) {
        return NAMESPACES.contains(name);
    }

    record UuidCall(String function, Type returnType, List<Type> parameterTypes) {}

    static UuidCall staticMethod(String namespace, String name, List<Type> argTypes) {
        return switch (name) {
            case "v4" -> argTypes.isEmpty()
                    ? new UuidCall("kof_uuid_v4", STR, List.of()) : null;
            default -> null;
        };
    }

    /**
     * uuid.v4 depende de entropia (getrandom/cryptorandom) — ausente no
     * runtime cross-arch (asm puro, sem libc), igual a toda a lane crypto.
     */
    static boolean supportedOn(String function, Target target) {
        return target != Target.NATIVE_RISCV64 && target != Target.NATIVE_AARCH64;
    }

    static String gapCode(String function) {
        return "SECN000";
    }
}
