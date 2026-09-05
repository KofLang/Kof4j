package dev.kof.compiler;

import java.util.List;

/**
 * M32.2: namespace gpu.* — FFI Vulkan compute.
 * gpu.dispatchMatmul(a, b, c, m, n, k) → int (0 = ok, != 0 = fallback CPU)
 * gpu.available() → bool (device compute inicializado)
 * Os arrays são Int[] (ponto fixo de milésimos, coerente com o runtime Kof).
 */
final class KofGpu {
    private KofGpu() {}
    static final Type GPU = new Type.ClassType("kof.gpu", "Gpu", List.of());
    private static final Type INT = Type.PrimitiveType.INT;
    private static final Type LONG = Type.PrimitiveType.LONG;
    private static final Type BOOL = Type.PrimitiveType.BOOL;
    private static final Type STR = BuiltinTypes.STRING;
    private static final Type VOID = Type.PrimitiveType.VOID;

    static boolean isGpuNamespace(String name) { return "gpu".equals(name); }

    static boolean isGpuMethod(String name) {
        return switch (name) {
            case "available", "dispatchMatmul", "dispatchMatmul64", "failReason",
                 "mvSetShape", "mvLoadW", "mvMatvec", "mvPutW", "mvRun", "mvPut32", "mvRun32", "mvPutSp", "mvRunSp" -> true;
            default -> false;
        };
    }

    record GpuCall(String function, Type returnType, List<Type> parameterTypes) {}

    static boolean supportedOn(Target target) {
        // JVM: FFM real. Nativos: stubs asm (available=false, dispatch=1).
        // JS: sem suporte (GPU001).
        return target.isNative() || target == Target.JVM;
    }

    static GpuCall staticCall(String name, List<Type> argTypes) {
        return switch (name) {
            case "available" -> argTypes.isEmpty() ? new GpuCall("kof_vk_available", BOOL, List.of()) : null;
            case "failReason" -> argTypes.isEmpty() ? new GpuCall("kof_vk_fail_reason", STR, List.of()) : null;
            case "dispatchMatmul" -> argTypes.size() == 6
                    ? new GpuCall("kof_vk_dispatch", INT,
                        List.of(new Type.ArrayType(INT), new Type.ArrayType(INT), new Type.ArrayType(INT),
                                INT, INT, INT))
                    : null;
            // M36: acumulador int64 p/ ponto fixo NANO (produto 9.3e18).
            case "dispatchMatmul64" -> argTypes.size() == 6
                    ? new GpuCall("kof_vk_dispatch64", INT,
                        List.of(new Type.ArrayType(LONG), new Type.ArrayType(LONG), new Type.ArrayType(LONG),
                                INT, INT, INT))
                    : null;
            // M36 FASE C: matvec residente — W fica no buffer mapeado (GPU lê
            // via PCIe sem copia por dispatch; 30x nos shapes koflama)
            case "mvSetShape" -> argTypes.size() == 2
                    ? new GpuCall("kof_mv64_set_shape", INT, List.of(INT, INT))
                    : null;
            case "mvLoadW" -> argTypes.size() == 3
                    ? new GpuCall("kof_mv64_load_w", INT,
                        List.of(new Type.ArrayType(LONG), INT, INT))
                    : null;
            case "mvMatvec" -> argTypes.size() == 4
                    ? new GpuCall("kof_mv64_matvec", INT,
                        List.of(new Type.ArrayType(LONG), new Type.ArrayType(LONG), INT, INT))
                    : null;
            case "mvPutW" -> argTypes.size() == 4
                    ? new GpuCall("kof_mv64_wput", INT,
                        List.of(INT, new Type.ArrayType(LONG), INT, INT))
                    : null;
            case "mvRun" -> argTypes.size() == 6
                    ? new GpuCall("kof_mv64_wrun", INT,
                        List.of(INT, new Type.ArrayType(LONG), new Type.ArrayType(LONG), INT, INT, LONG))
                    : null;
            case "mvPut32" -> argTypes.size() == 4
                    ? new GpuCall("kof_mv64_wput32", INT,
                        List.of(INT, new Type.ArrayType(INT), INT, INT))
                    : null;
            case "mvRun32" -> argTypes.size() == 6
                    ? new GpuCall("kof_mv64_wrun32", INT,
                        List.of(INT, new Type.ArrayType(LONG), new Type.ArrayType(LONG), INT, INT, LONG))
                    : null;
            case "mvPutSp" -> argTypes.size() == 5
                    ? new GpuCall("kof_mv64_wputsp", INT,
                        List.of(INT, new Type.ArrayType(INT), new Type.ArrayType(INT), INT, INT))
                    : null;
            case "mvRunSp" -> argTypes.size() == 6
                    ? new GpuCall("kof_mv64_wrunsp", INT,
                        List.of(INT, new Type.ArrayType(LONG), new Type.ArrayType(LONG), INT, INT, LONG))
                    : null;
            default -> null;
        };
    }
}
