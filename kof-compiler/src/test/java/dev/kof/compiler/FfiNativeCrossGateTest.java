package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #431/§61 — gate de binding do `extern` por alvo NATIVO, SEM toolchain.
 *
 * <p>A regressão real (21/09): as fatias de struct-por-valor (3.7/3.8b) reabriram
 * o gate NATIVO com um `if (driver.target != Target.NATIVE) return false;` de
 * topo, que fecha o caminho ESCALAR do riscv64/aarch64 (shim LP64/AAPCS64 do
 * #431 fatia 2). O sintoma só aparecia onde há qemu + toolchain cross — os hosts
 * sem toolchain PULAM o {@link FfiNativeCrossE2ETest} ({@code assumeTrue}) e a
 * regressão passou silenciosa. Este teste fecha essa cegueira: exercita
 * {@link CompilerFfiBinding#isExternBound} direto, que é pré-codegen e independe
 * do assembler/linker.
 *
 * <p>Contrato (R6 + fatias R3): escalar binda em TODO alvo nativo; struct por
 * valor no x86-64 (register path + sret); no cross (3.7 fatia 3) o RETURN com
 * campos INTEGER (≤ 16 B) binda — o caso positivo é provado com toolchain em
 * {@link FfiNativeCrossE2ETest} (`div()` da libc). Um nome de record que o
 * driver não resolve (sem classe) não tem layout → FFI001 honesto em todo alvo.
 */
class FfiNativeCrossGateTest {

    private static CompilerDriver driver(Target t) {
        CompilerDriver d = new CompilerDriver();
        d.target = t;
        return d;
    }

    private static ExternalFunctionNode scalarExtern() {
        List<FormalParameterNode> ps = new ArrayList<>();
        ps.add(new FormalParameterNode(null, List.of(), "Int", "x"));
        return new ExternalFunctionNode(null, "libc.so.6", "Int", "abs", ps, null);
    }

    private static ExternalFunctionNode structReturnExtern() {
        return new ExternalFunctionNode(null, "libc.so.6", "Point", "make", List.of(), null);
    }

    @Test
    void scalarExternBindsOnEveryNativeTarget() {
        for (Target t : List.of(Target.NATIVE, Target.NATIVE_RISCV64, Target.NATIVE_AARCH64)) {
            assertTrue(CompilerFfiBinding.isExternBound(driver(t), scalarExtern()),
                    "extern escalar (Int→Int + library()) deve bindar no " + t
                            + " — #431 fatia 2 abriu o shim cross e as fatias de struct não podem re-fechá-lo");
        }
    }

    @Test
    void scalarExternWithoutLibraryStaysGap() {
        ExternalFunctionNode noLib = new ExternalFunctionNode(null, null, "Int", "abs", List.of(), null);
        assertFalse(CompilerFfiBinding.isExternBound(driver(Target.NATIVE_RISCV64), noLib),
                "extern sem library() não tem o que linkar → FFI001 (R6)");
    }

    @Test
    void unresolvableStructReturnNameStaysGapOnEveryTarget() {
        // Sem registro da classe no driver não há layout de struct → nada a
        // bindar; nunca silencioso (R6). O caminho POSITIVO (record declarado,
        // campos INTEGER) é provado com toolchain em FfiNativeCrossE2ETest.
        for (Target t : List.of(Target.NATIVE, Target.NATIVE_RISCV64, Target.NATIVE_AARCH64)) {
            assertFalse(CompilerFfiBinding.isExternBound(driver(t), structReturnExtern()),
                    "retorno de tipo record não resolvido é FFI001 em " + t);
        }
    }
}
