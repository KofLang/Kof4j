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
 * SECN000 FECHADO (09/09): riscv64/aarch64 usam getrandom(2) via ecall
 * (syscall 278) na fatia riscv B25 + aarch translator (R11 — só a primitiva
 * do SO). v7/ulid = S3b.2 (exigem clock + mais bytes aleatórios).
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
            case "v7" -> argTypes.isEmpty()
                    ? new UuidCall("kof_uuid_v7", STR, List.of()) : null;
            case "isUuid" -> argTypes.size() == 1
                    ? new UuidCall("kof_uuid_isUuid", Type.PrimitiveType.BOOL, List.of(STR)) : null;
            default -> null;
        };
    }

    /**
     * SECN000 FECHADO (09/09): getrandom(2) via ecall (syscall 278, probe
     * riscv64+aarch64) no runtime riscv B25 / aarch translator. R11: só a
     * primitiva do SO, sem cripto caseira; null se o syscall falhar (mesmo
     * contrato do x86 kof_sec_random_hex). supportedOn volta se outro gap.
     * UUID001 (10/09): isUuid (predicado de forma, byte-scan puro) tem
     * JVM/Script/JS/x86; riscv64/aarch64 = fatia B própria pendente (mesma
     * condição de parada de S7c-1: sem cross-assembler/qemu no ambiente da
     * lane — spec x86 pronta; NÃO escrever asm sem montar/rodar).
     * UUID002 (10/09): v7 (RFC 9562 time-ordered ms timestamp + random bits)
     * tem JVM/Script/JS/x86; riscv64/aarch64 = fatia B pendente de prova.
     */
    static boolean supportedOn(String function, Target target) {
        if (("kof_uuid_isUuid".equals(function) || "kof_uuid_v7".equals(function))
                && (target == Target.NATIVE_RISCV64 || target == Target.NATIVE_AARCH64)) {
            return false;
        }
        return true;
    }

    static String gapCode(String function) {
        if ("kof_uuid_v7".equals(function)) return "UUID002";
        return "kof_uuid_isUuid".equals(function) ? "UUID001" : "SECN000";
    }
}
