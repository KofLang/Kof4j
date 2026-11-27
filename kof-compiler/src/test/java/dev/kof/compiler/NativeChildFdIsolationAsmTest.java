package dev.kof.compiler;

import dev.kof.compiler.nat.RiscvSlices;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        // Seam refactor: each exec site calls kof_plat_exec (close_range) first;
        // the syscalls live once in RuntimePlatProc, not inline per site.
        assertEquals(count(asm, "call execvp"), count(asm, "call kof_plat_exec"),
                "x86-64: every execvp (process.run, process.spawn, shell.pipeline) needs kof_plat_exec (close_range) first");
        assertEquals(3, count(asm, "call kof_plat_exec"), "x86-64: run + spawn + pipeline");
        assertTrue(asm.contains("movq $436, %rax"),
                "x86-64: kof_plat_exec must issue close_range (syscall 436)");
        assertTrue(asm.contains("movq $72, %rax"),
                "x86-64: kof_plat_cloexec must issue the fcntl(F_SETFD) fallback (syscall 72) for old kernels");
    }

    @Test
    void crossEveryExecvpSiteIsolatesDescriptors() {
        String asm = RiscvSlices.renderRuntime();
        assertEquals(count(asm, "call execvp"), count(asm, "li a7, 436"),
                "riscv64/aarch64: every execvp needs close_range first");
        assertEquals(3, count(asm, "li a7, 436"), "cross: run + spawn + pipeline");
    }
}
