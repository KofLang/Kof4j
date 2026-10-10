package dev.kof.compiler;

import dev.kof.compiler.nat.RiscvSlices;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * #762 structural backstop (runs on every host, no Linux/qemu needed): every
 * native {@code execvp} site must mark the inherited descriptors close-on-exec
 * first. {@link NativeChildFdIsolationE2ETest} proves the behaviour on Linux;
 * this guard fails if a new exec path is added (or one is dropped) without it.
 */
class NativeChildFdIsolationAsmTest {

    private static int count(String text, String needle) {
        int n = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) n++;
        return n;
    }

    @Test
    void x86EveryExecvpSiteIsolatesDescriptors() {
        String asm = NativeRuntime.generateRuntimeAssembly();
        assertEquals(count(asm, "call execvp"), count(asm, "movl $436, %eax"),
                "x86-64: every execvp (process.run, process.spawn, shell.pipeline) needs close_range first");
        assertEquals(3, count(asm, "movl $436, %eax"), "x86-64: run + spawn + pipeline");
        assertEquals(count(asm, "movl $436, %eax"), count(asm, "movl $72, %eax"),
                "x86-64: each close_range has the fcntl(F_SETFD) fallback for old kernels");
    }

    @Test
    void crossEveryExecvpSiteIsolatesDescriptors() {
        String asm = RiscvSlices.renderRuntime();
        assertEquals(count(asm, "call execvp"), count(asm, "li a7, 436"),
                "riscv64/aarch64: every execvp needs close_range first");
        assertEquals(3, count(asm, "li a7, 436"), "cross: run + spawn + pipeline");
    }
}
