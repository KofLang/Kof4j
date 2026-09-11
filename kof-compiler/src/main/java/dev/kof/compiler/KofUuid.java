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
    private static final Type BOOL = Type.PrimitiveType.BOOL;

    static final List<String> NAMESPACES = List.of("uuid");

    static boolean isUuidNamespace(String name) {
        return NAMESPACES.contains(name);
    }

    record UuidCall(String function, Type returnType, List<Type> parameterTypes) {}

    static UuidCall staticMethod(String namespace, String name, List<Type> argTypes) {
        return switch (name) {
            // S3b-ext: isUuid — shape RFC 4122 (8-4-4-4-12 hex, hífens em
            // 8/13/18/23). Não valida versão/variante (qualquer v1..v5
            // canônico é true) — validação de entropia é do v4() (SECN000).
            case "isUuid" -> argTypes.size() == 1
                    ? new UuidCall("kof_uuid_isUuid", BOOL, List.of(STR)) : null;
            case "v4" -> argTypes.isEmpty()
                    ? new UuidCall("kof_uuid_v4", STR, List.of()) : null;
            // S3b.2 (main, RFC 9562): v7 time-ordered — 48 bits unix_ts_ms +
            // rand; JVM/Script/JS/x86; riscv/aarch = UUID002 (fatia pendente
            // — sem cross-assembler/qemu na lane, prova impossível).
            case "v7" -> argTypes.isEmpty()
                    ? new UuidCall("kof_uuid_v7", STR, List.of()) : null;
            default -> null;
        };
    }

    /**
     * SECN000 FECHADO (09/09): getrandom(2) via ecall (syscall 278, probe
     * riscv64+aarch64) no runtime riscv B25 / aarch translator. R11: só a
     * primitiva do SO, sem cripto caseira; null se o syscall falhar (mesmo
     * contrato do x86 kof_sec_random_hex). supportedOn volta se outro gap.
     * UUID001 FECHADO (merge beta→main 10/09): isUuid portado p/ riscv64
     * (fatia B25 — byte-scan de forma, lição travada: upper-bound das
     * bandas hex é EXCLUSIVO, 58/71/103) + aarch64 via tradutor; prova
     * KofUuidTest.isUuidCrossArch (assert sob qemu — bug 59 no println).
     * UUID002 (10/09, main): v7 (RFC 9562 — unix_ts_ms 48 bits + rand) tem
     * JVM/Script/JS/x86; riscv64/aarch64 = fatia B pendente (sem cross-
     * assembler/qemu na lane — regra: nunca asm sem montar/rodar).
     */
    static boolean supportedOn(String function, Target target) {
        if ("kof_uuid_v7".equals(function)
                && (target == Target.NATIVE_RISCV64 || target == Target.NATIVE_AARCH64)) {
            return false;
        }
        return true;
    }

    static String gapCode(String function) {
        if ("kof_uuid_v7".equals(function)) return "UUID002";
        return "SECN000";
    }
}
