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
    private static final Type VOID = Type.PrimitiveType.VOID;
    private static final Type BYTES = new Type.ArrayType(Type.PrimitiveType.BYTE);

    static boolean isBufferNamespace(String name) { return "buffer".equals(name); }

    /** LSP catalogue — GUARD: StdCatalogTest locks this to the dispatch below. */
    static List<String> functions() { return List.of("alloc", "peek8", "peek32", "peek64",
            "poke8", "poke32", "poke64"); }

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
            // Graphics slice 3.4b inc1 (decision F row, ORDERED 08/10): the peek
            // primitive — read memory at a raw address OR at a Buffer payload
            // offset (the B decision form: overload by arity/types). The raw
            // form reads the opaque C structs (AVFrame.data[0]/width); the
            // Buffer form reads the out-pointer a C wrote into the payload
            // (AVFormatContext** via avformat_open_input). Bounds: negative
            // offset or offset+n beyond the cap → honest trap (never a silent
            // fallback); the raw form has no bounds (the address is the caller's).
            case "peek8" -> peekCall("kof_buffer_peek8", "kof_buffer_peek8_buf",
                    INT, argTypes);
            case "peek32" -> peekCall("kof_buffer_peek32", "kof_buffer_peek32_buf",
                    INT, argTypes);
            case "peek64" -> peekCall("kof_buffer_peek64", "kof_buffer_peek64_buf",
                    Type.PrimitiveType.LONG, argTypes);
            case "poke8" -> pokeCall("kof_buffer_poke8", "kof_buffer_poke8_buf",
                    Type.PrimitiveType.INT, argTypes);
            case "poke32" -> pokeCall("kof_buffer_poke32", "kof_buffer_poke32_buf",
                    Type.PrimitiveType.INT, argTypes);
            case "poke64" -> pokeCall("kof_buffer_poke64", "kof_buffer_poke64_buf",
                    Type.PrimitiveType.LONG, argTypes);
            default -> null;
        };
    }

    private static BufferCall peekCall(String rawFn, String bufFn, Type ret, List<Type> argTypes) {
        if (argTypes.size() == 1 && Type.PrimitiveType.LONG.equals(argTypes.get(0))) {
            return new BufferCall(rawFn, ret, List.of(Type.PrimitiveType.LONG));
        }
        if (argTypes.size() == 2 && BUFFER.equals(argTypes.get(0))
                && Type.PrimitiveType.INT.equals(argTypes.get(1))) {
            return new BufferCall(bufFn, ret, List.of(BUFFER, Type.PrimitiveType.INT));
        }
        return null;
    }

    private static BufferCall pokeCall(String rawFn, String bufFn, Type valueTy, List<Type> argTypes) {
        // The write counterpart of peek (3.4c): (addr, value) raw or (buffer, off, value).
        // A Long value slot accepts Int too — the ordinary Kof conversion
        // (#549/§370) widens at the call site, the same as sqrt(9).
        if (argTypes.size() == 2 && Type.PrimitiveType.LONG.equals(argTypes.get(0))
                && valueTy.equals(argTypes.get(1))) {
            return new BufferCall(rawFn, VOID, List.of(Type.PrimitiveType.LONG, valueTy));
        }
        if (argTypes.size() == 3 && BUFFER.equals(argTypes.get(0))
                && Type.PrimitiveType.INT.equals(argTypes.get(1)) && valueTy.equals(argTypes.get(2))) {
            return new BufferCall(bufFn, VOID, List.of(BUFFER, Type.PrimitiveType.INT, valueTy));
        }
        if (argTypes.size() == 2 && Type.PrimitiveType.LONG.equals(argTypes.get(0))
                && valueTy.equals(Type.PrimitiveType.LONG)
                && Type.PrimitiveType.INT.equals(argTypes.get(1))) {
            return new BufferCall(rawFn, VOID, List.of(Type.PrimitiveType.LONG, Type.PrimitiveType.LONG));
        }
        if (argTypes.size() == 3 && BUFFER.equals(argTypes.get(0))
                && Type.PrimitiveType.INT.equals(argTypes.get(1)) && valueTy.equals(Type.PrimitiveType.LONG)
                && Type.PrimitiveType.INT.equals(argTypes.get(2))) {
            return new BufferCall(bufFn, VOID, List.of(BUFFER, Type.PrimitiveType.INT, Type.PrimitiveType.LONG));
        }
        return null;
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

    /** Per-function supportedOn: `peek*` is a raw-memory read — the JS host has
     *  no address space (the JS Buffer is emulated), so peek stays an honest gap
     *  there; Android/Script gap for the whole namespace (existing rule). */
    static boolean supportedOn(String function, Target target) {
        if (function.startsWith("peek") || function.startsWith("poke")) {
            return target == Target.JVM
                    || target == Target.NATIVE
                    || target == Target.NATIVE_RISCV64 || target == Target.NATIVE_AARCH64;
        }
        return supportedOn(target);
    }

    static String gapCode(Target target) {
        // The Buffer namespace binds on JVM, JS and Native (x86-64 + cross
        // riscv64/aarch64). Android, Script, riscv32 and MCU still report FFI001;
        // the FFI `B` parameter binds wherever the buffer surface does.
        return "FFI001";
    }
}
