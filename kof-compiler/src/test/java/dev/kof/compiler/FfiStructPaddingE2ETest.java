package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §14/§15 + §762: the emitted FFI offsets for a struct whose C layout has
 * ALIGNMENT PADDING. Kept in its own class so {@code FfiStructE2ETest} stays
 * clear of the 500-line gate.
 */
class FfiStructPaddingE2ETest extends FfiStructSupport {

@Test
    void structParamBoolPaddingNative(@TempDir Path dir) throws Exception {
        // §14/§15: the emitted offsets for a struct with ALIGNMENT PADDING. A
        // 1-byte _Bool forces 3 bytes of padding before the int, so C reads the
        // int at offset 4 (not 1) and the struct is 8 B. Every other fixture in
        // this file is built from 4/8-byte fields, which never exercises that
        // path — a wrong offset here would silently read the wrong bits.
        // Golden = the same source on the JVM (FFM) + the C function's own
        // arithmetic, so a mismatch cannot be absorbed by a matching mistake.
        String so = compileHostLib(dir);
        String kof = """
                record PB(Bool a, Int b)
                record PBB(Bool a, Bool b, Bool c, Int d)

                extern "%s" pbsum(PB p): Int
                extern "%s" pbsum3(PBB p): Int

                main() {
                    println(pbsum(PB(true, 7)))
                    println(pbsum3(PBB(true, false, true, 5)))
                }
                """.formatted(so, so);
        // PB(true,7)  -> 1*100 + 7          = 107
        // PBB(1,0,1,5)-> 1 + 0 + 100 + 5000 = 5101
        String expected = "107\n5101";

        // NATIVE is the target that can express this: measured correct here.
        String nat = runNative(dir, "padbool", kof);
        assertEquals(expected, nat, "NATIVE x86-64: bool-padding struct offsets");

        // §762 FIXED (20/10): the JVM/FFM derived layout now inserts INTERIOR
        // padding (a 1-byte field before a wider one), so this shape crosses by
        // value on the JVM too and must match the native result exactly.
        Path jvmSrc = dir.resolve("padbool-jvm.kf");
        Files.writeString(jvmSrc, kof);
        Path jvmOut = dir.resolve("out-padbool-jvm");
        CompilationResult rj = driver.compile(jvmSrc, jvmOut, Target.JVM);
        assertTrue(rj.success(), "JVM compile: " + rj.diagnostics().getDiagnostics());
        String jvm = runJvm(jvmOut);
        assertEquals(expected, jvm, "JVM golden (bool-padding struct param) — §762");
        assertEquals(jvm, nat, "JVM<->Native parity (bool-padding struct, §762)");
    }
}
