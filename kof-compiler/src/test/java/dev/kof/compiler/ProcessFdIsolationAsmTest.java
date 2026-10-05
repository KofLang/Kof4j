package dev.kof.compiler;

import dev.kof.compiler.nat.RiscvSlices;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Structural backstop for all native execvp sites covered by #762. */
class ProcessFdIsolationAsmTest {

    private static int count(String text, String needle) {
        int n = 0;
        int at = 0;
        while ((at = text.indexOf(needle, at)) >= 0) {
            n++;
            at += needle.length();
        }
        return n;
    }

    @Test
    void x86AllExecPathsUseDescriptorIsolation() {
        String asm = NativeRuntime.generateRuntimeAssembly();
        assertEquals(3, count(asm, "call kof_process_child_fd_isolation"),
                "process.run, process.spawn and shell.pipeline must all isolate FDs");
        assertTrue(asm.contains("movl $436, %eax"), "close_range fast path");
        assertTrue(asm.contains("movl $302, %eax"), "prlimit64 fallback");
        assertTrue(asm.contains("movl $72, %eax"), "fcntl fallback");
    }

    @Test
    void crossAllExecPathsUseDescriptorIsolation() {
        String asm = RiscvSlices.renderRuntime();
        assertEquals(3, count(asm, "call kof_process_child_fd_isolation"),
                "cross run/spawn/pipeline must all isolate FDs");
        assertTrue(asm.contains("li   a7, 436"), "close_range fast path");
        assertTrue(asm.contains("li   a7, 261"), "prlimit64 fallback");
        assertTrue(asm.contains("li   a7, 25"), "fcntl fallback");
    }
}
