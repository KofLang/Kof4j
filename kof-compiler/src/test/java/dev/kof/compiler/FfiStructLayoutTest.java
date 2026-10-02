package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 3.7 fatia 1: classificação/bindability do struct nativo x86-64, sem toolchain C
 * (o E2E com shim real é {@code FfiStructE2ETest}). Trava o caminho de
 * REGISTRADORES (SysV) e os gaps honestos: MEMORY (> 16 B), SSE com mais de um
 * campo (empacotamento não emitido) e estouro de registradores.
 */
class FfiStructLayoutTest {

    private static Type struct(char... chars) {
        return FfiStructLayout.structTypeOfChars(new String(chars));
    }

    @Test
    void registerPathStructsAreBindable() {
        assertTrue(FfiStructLayout.x86Bindable(List.of(struct('i', 'i'))),
                "Point(Int,Int): 1 eightbyte INTEGER");
        assertTrue(FfiStructLayout.x86Bindable(List.of(struct('i', 'f'))),
                "Int+Float no MESMO eightbyte → INTEGER (int domina)");
        assertTrue(FfiStructLayout.x86Bindable(List.of(struct('j', 'd'))),
                "Time(Long,Double): INTEGER + SSE");
        assertTrue(FfiStructLayout.x86Bindable(List.of(
                        Type.PrimitiveType.INT, struct('i', 'i'), Type.PrimitiveType.DOUBLE)),
                "escalar + struct + escalar na ordem formal");
    }

    @Test
    void memoryPathAndExhaustionStayUnbound() {
        assertFalse(FfiStructLayout.x86Bindable(List.of(struct('i', 'i', 'i', 'i', 'i'))),
                "Big3(5×Int) = 20 B → SysV MEMORY (FFI001 honesto)");
        assertFalse(FfiStructLayout.x86Bindable(List.of(struct('f', 'f'))),
                "dois Float no MESMO eightbyte SSE não é emitido (FFI001 honesto)");
        assertFalse(FfiStructLayout.x86Bindable(List.of(
                        Type.PrimitiveType.INT, Type.PrimitiveType.INT, Type.PrimitiveType.INT,
                        Type.PrimitiveType.INT, Type.PrimitiveType.INT, Type.PrimitiveType.INT,
                        struct('i', 'i'))),
                "6 int regs já consumidos → struct iria à memória (FFI001 honesto)");
    }

    @Test
    void sretReturnAndReservedIntRegister() {
        assertTrue(FfiStructLayout.x86SretReturn(struct('i', 'i', 'i', 'i', 'i')),
                "5×Int = 20 B → SysV MEMORY → sret (D6-4)");
        assertFalse(FfiStructLayout.x86SretReturn(struct('j', 'd')),
                "Time = 16 B → register path, não sret");
        // o ponteiro escondido do sret ocupa rdi: um struct param que caberia
        // sem o sret passa a não caber (deslocamento de 1 registrador INTEGER).
        List<Type> fiveInts = List.of(Type.PrimitiveType.INT, Type.PrimitiveType.INT,
                Type.PrimitiveType.INT, Type.PrimitiveType.INT, Type.PrimitiveType.INT);
        List<Type> withStruct = new java.util.ArrayList<>(fiveInts);
        withStruct.add(struct('i', 'i'));
        assertTrue(FfiStructLayout.x86Bindable(withStruct, 0),
                "5 ints + Point: o struct cai em r9 (ordinal 5) sem sret");
        assertFalse(FfiStructLayout.x86Bindable(withStruct, 1),
                "com o rdi reservado pelo sret, o struct iria à memória → não-bindável");
    }

    @Test
    void crossIntReturnIsBindableOnlyForIntegerRegisterPath() {
        // 3.7 fatia 3 (primeiro corte): só struct de campos INTEGER, ≤ 16 B.
        assertTrue(FfiStructLayout.crossIntRegisterOnly(Target.NATIVE_RISCV64, struct('i', 'i')),
                "div_t(Int,Int): riscv ≤2 campos → 1 doubleword INTEGER (a0)");
        assertTrue(FfiStructLayout.crossIntRegisterOnly(Target.NATIVE_AARCH64, struct('i', 'i')),
                "div_t(Int,Int): aarch ≤16 B → 1 eightword INTEGER (x0)");
        assertTrue(FfiStructLayout.crossIntRegisterOnly(Target.NATIVE_RISCV64,
                        struct('i', 'i', 'i')),
                "{Int,Int,Int}: riscv 3+ campos → 2 doublewords INTEGER");
        assertTrue(FfiStructLayout.crossIntRegisterOnly(Target.NATIVE_AARCH64, struct('j', 'j')),
                "{Long,Long}: aarch 2 eightwords INTEGER");
        // gaps honestos (R6): float na riscv vira classe SSE; HFA no aarch;
        // byref > 16 B nas duas.
        assertFalse(FfiStructLayout.crossIntRegisterOnly(Target.NATIVE_RISCV64, struct('d', 'i')),
                "{Double,Int}: riscv ≤2 campos → SSE+INTEGER (float não emitido na fatia 3)");
        assertFalse(FfiStructLayout.crossIntRegisterOnly(Target.NATIVE_AARCH64, struct('f', 'f')),
                "HFA (2×Float) no aarch vai em SIMD (não emitido na fatia 3)");
        assertFalse(FfiStructLayout.crossIntRegisterOnly(Target.NATIVE_AARCH64,
                        struct('i', 'i', 'i', 'i', 'i')),
                "5×Int = 20 B → BYREF no cross (FFI001 honesto)");
        assertFalse(FfiStructLayout.crossIntRegisterOnly(Target.NATIVE_RISCV64,
                        struct('i', 'i', 'i', 'i', 'i')),
                "5×Int = 20 B → BYREF no riscv (FFI001 honesto)");
    }

    @Test
    void crossMemoryReturnIsSretOnlyForLargeStructs() {
        // D-MEM-FFI-CROSS-FULL face 3: > 16 B → BYREF/MEMORY → sret nas duas archs.
        assertTrue(FfiStructLayout.crossMemoryReturn(Target.NATIVE_RISCV64,
                        struct('j', 'j', 'j')),
                "{Long,Long,Long} = 24 B → BYREF no riscv64 (sret em a0)");
        assertTrue(FfiStructLayout.crossMemoryReturn(Target.NATIVE_AARCH64,
                        struct('j', 'j', 'j')),
                "{Long,Long,Long} = 24 B → BYREF no aarch64 (sret em x8)");
        assertTrue(FfiStructLayout.crossMemoryReturn(Target.NATIVE_RISCV64,
                        struct('i', 'i', 'i', 'i', 'i')),
                "5×Int = 20 B → BYREF no riscv64");
        // ≤ 16 B continua register path (não é sret).
        assertFalse(FfiStructLayout.crossMemoryReturn(Target.NATIVE_RISCV64, struct('i', 'i')),
                "div_t = 8 B → registrador, não sret");
        assertFalse(FfiStructLayout.crossMemoryReturn(Target.NATIVE_AARCH64, struct('j', 'j')),
                "{Long,Long} = 16 B → registrador, não sret");
        // O sret do riscv consome 1 registrador INTEGER (a0): um struct param que
        // cabia sem o sret deixa de caber.
        List<Type> sevenInts = List.of(Type.PrimitiveType.INT, Type.PrimitiveType.INT,
                Type.PrimitiveType.INT, Type.PrimitiveType.INT, Type.PrimitiveType.INT,
                Type.PrimitiveType.INT, Type.PrimitiveType.INT);
        List<Type> plusPair = new java.util.ArrayList<>(sevenInts);
        plusPair.add(struct('i', 'i'));
        assertTrue(FfiStructLayout.crossBindable(plusPair, 0),
                "7 ints + Point: o struct cai no 8º registrador sem sret");
        assertFalse(FfiStructLayout.crossBindable(plusPair, 1),
                "com o ponteiro sret reservando 1 INTEGER, o struct iria à memória → não-bindável");
    }

    @Test
    void crossByMemoryStructParamCountsAsOnePointer() {
        // D-MEM-FFI-CROSS-FULL face 3 estendida: struct > 16 B como PARÂMETRO
        // viaja como UM ponteiro INTEGER (BYREF, medido riscv64+aarch64).
        Type big = struct('j', 'j', 'j');
        assertTrue(FfiStructLayout.crossByMemory(Target.NATIVE_RISCV64, big),
                "{Long,Long,Long} = 24 B → BYREF no riscv64");
        assertTrue(FfiStructLayout.crossByMemory(Target.NATIVE_AARCH64, big),
                "{Long,Long,Long} = 24 B → BYREF no aarch64");
        assertTrue(FfiStructLayout.crossBindable(List.of(big, Type.PrimitiveType.LONG)),
                "um struct > 16 B + um Long cabem (2 ordinais INTEGER)");
        assertFalse(FfiStructLayout.crossBindable(List.of(
                        Type.PrimitiveType.INT, Type.PrimitiveType.INT, Type.PrimitiveType.INT,
                        Type.PrimitiveType.INT, Type.PrimitiveType.INT, Type.PrimitiveType.INT,
                        Type.PrimitiveType.INT, Type.PrimitiveType.INT, big)),
                "8 ints já consomem os registradores → o ponteiro do struct derrama → não-bindável");
    }

    @Test
    void abiForMapsTargets() {
        assertEquals(AbiLayout.Abi.SYSV_X86_64, FfiStructLayout.abiFor(Target.NATIVE));
        assertEquals(AbiLayout.Abi.RISCV64, FfiStructLayout.abiFor(Target.NATIVE_RISCV64));
        assertEquals(AbiLayout.Abi.AAPCS64, FfiStructLayout.abiFor(Target.NATIVE_AARCH64));
    }
}
