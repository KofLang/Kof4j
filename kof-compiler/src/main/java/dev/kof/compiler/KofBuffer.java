package dev.kof.compiler;

import java.util.List;

/**
 * {@code kof.buffer} namespace + the nominal {@code Buffer(U8)} type
 * (D-R3-BUFFER / D6-3, maintainer 21/09/2026).
 *
 * <p>Incremental slice (R6-SCOPE): {@code buffer.alloc(Int) : Buffer(U8)} and
 * {@code Buffer.bytes() : Byte[]} on the JVM, JS, x86-64 ({@code Target.NATIVE},
 * #651 fatia A1) and the cross riscv64/aarch64 ({@code NativeRiscvAsmBuffer},
 * fatia B). The programmer never allocates or frees — the lifetime is
 * language-managed (D-R3-HANDLE-LIFETIME). The FFI out-buffer token {@code B}
 * binds on the same native targets (A2 x86, B cross); Android/Script and
 * riscv32/MCU stay honest gaps.
 */
public final class KofBuffer {
    private KofBuffer() {}

    static final Type BUFFER = new Type.ClassType("kof", "Buffer", List.of());
    private static final Type INT = Type.PrimitiveType.INT;
    private static final Type BYTES = new Type.ArrayType(Type.PrimitiveType.BYTE);

    static boolean isBufferNamespace(String name) { return "buffer".equals(name); }

    /** LSP catalogue — GUARD: StdCatalogTest locks this to the dispatch below. */
    static List<String> functions() { return List.of("alloc"); }

    public static boolean isBufferType(Type t) { return BUFFER.equals(t); }

    /** The element type accepted in `Buffer(<elem>)` — only U8 (Kof `Byte`) in v1. */
    static boolean isBufferElement(String t) {
        return "U8".equals(t) || "u8".equals(t) || "Byte".equals(t) || "byte".equals(t);
    }

    record BufferCall(String function, Type returnType, List<Type> parameterTypes) {}

    static BufferCall staticMethod(String namespace, String name, List<Type> argTypes) {
        if (!"buffer".equals(namespace)) return null;
        return switch (name) {
            case "alloc" -> argTypes.size() == 1
                    ? new BufferCall("kof_buffer_alloc", BUFFER, List.of(INT)) : null;
            default -> null;
        };
    }

    static BufferCall instanceMethod(Type receiver, String name, int argCount) {
        if (!isBufferType(receiver)) return null;
        return switch (name) {
            case "bytes" -> argCount == 0
                    ? new BufferCall("kof_buffer_bytes", BYTES, List.of(BUFFER)) : null;
            default -> null;
        };
    }

    static boolean supportedOn(Target target) {
        // JVM/JS landed 21/09; x86-64 native surface (alloc/bytes/println) landed in
        // #651 fatia A1; the cross riscv64/aarch64 surface + FFI B token landed in
        // fatia B (29/09, NativeRiscvAsmBuffer). riscv32/MCU stay honest FFI001.
        return target == Target.JVM || target == Target.JS
                || target == Target.NATIVE
                || target == Target.NATIVE_RISCV64 || target == Target.NATIVE_AARCH64;
    }

    static String gapCode(Target target) {
        // The Buffer namespace binds on JVM, JS and Native (x86-64 + cross
        // riscv64/aarch64). Android, Script, riscv32 and MCU still report FFI001;
        // the FFI `B` parameter binds wherever the buffer surface does.
        return "FFI001";
    }
}
