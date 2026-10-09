package dev.kof.compiler.nat;

import dev.kof.compiler.NativeToolchainAssumptions;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * D-MEMORY-CLEAR (O-03/`MEM003`) — PROVA DE RUNTIME do contrato no alvo x86-64
 * (host Linux): {@code clear()} anula CADA slot antes de encolher. Mesmo
 * harness do {@link NativeRiscvMemClearTest}, com o runtime x86 podado pela
 * PRODUCAO ({@link RuntimeSlices#keepForProgramText}). Lista via add real; map
 * com pares key/val plantados; OR dos slots lidos da memoria apos o clear.
 */
class NativeX86MemClearTest implements NativeToolchainAssumptions {

    private static boolean has(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c)
                        .redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                if (p.waitFor() != 0 || out.isEmpty()) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private static final String HARNESS_CLEAR = """
            .section .data
            .globl kof_heap_root_start
            kof_heap_root_start:
                .quad 0
            .globl kof_tostring_table
            kof_tostring_table:
                .quad 0
            .globl kof_super_table
            kof_super_table:
                .quad 0
            .globl kof_equals_table
            kof_equals_table:
                .quad 0
            .globl kof_hashcode_table
            kof_hashcode_table:
                .quad 0
            .Lnewline: .asciz "\\n"
            .Lkof_str_true: .asciz "true"
            .Lkof_str_false: .asciz "false"
            .section .text
            .globl _start
            _start:
                andq $-16, %rsp

                # ---- list: 3 sentinelas via kof_list_add ----
                call kof_list_new
                movq %rax, %r12
                movq %r12, %rdi
                movq $0x1111, %rsi
                call kof_list_add
                movq %r12, %rdi
                movq $0x2222, %rsi
                call kof_list_add
                movq %r12, %rdi
                movq $0x3333, %rsi
                call kof_list_add
                movq 24(%r12), %rdx
                movq 0(%rdx), %rax
                orq 8(%rdx), %rax
                orq 16(%rdx), %rax
                movq %rax, %r13
                movq %r12, %rdi
                call kof_list_clear
                movq 24(%r12), %rdx
                movq 0(%rdx), %rax
                orq 8(%rdx), %rax
                orq 16(%rdx), %rax
                movq %rax, %r14
                testq %r13, %r13
                jz .Lfail
                testq %r14, %r14
                jnz .Lfail
                cmpl $0, 16(%r12)
                jnz .Lfail

                # ---- map: 3 pares key/val plantados nos slots ----
                call kof_map_new
                movq %rax, %r15
                movl $3, 16(%r15)
                movq 24(%r15), %rsi
                movq 32(%r15), %rdi
                movq $0x4444, %rax
                movq %rax, 0(%rsi)
                movq $0x5555, %rax
                movq %rax, 8(%rsi)
                movq $0x6666, %rax
                movq %rax, 16(%rsi)
                movq $0x7777, %rax
                movq %rax, 0(%rdi)
                movq $0x8888, %rax
                movq %rax, 8(%rdi)
                movq $0x9999, %rax
                movq %rax, 16(%rdi)
                movq %r15, %rdi
                call kof_map_clear
                cmpl $0, 16(%r15)
                jnz .Lfail
                movq 24(%r15), %rsi
                movq 32(%r15), %rdi
                movq 0(%rsi), %rax
                orq 8(%rsi), %rax
                orq 16(%rsi), %rax
                orq 0(%rdi), %rax
                orq 8(%rdi), %rax
                orq 16(%rdi), %rax
                testq %rax, %rax
                jnz .Lfail

                xorq %rdi, %rdi
                movq $60, %rax
                syscall
            .Lfail:
                movq $1, %rdi
                movq $60, %rax
                syscall
            """;

    private String runCapture(String... cmd) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        try {
            int ec = p.waitFor();
            assertEquals(0, ec, "comando falhou (" + ec + "): " + String.join(" ", cmd) + "\n" + out);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        return out;
    }

    private String build(Path tempDir, String name) throws IOException {
        String runtime = RuntimeSlices.renderSubset(RuntimeSlices.keepForProgramText(HARNESS_CLEAR));
        Path asm = tempDir.resolve(name + ".s");
        Files.writeString(asm, HARNESS_CLEAR + "\n" + runtime);
        Path obj = tempDir.resolve(name + ".o");
        Path bin = tempDir.resolve(name);
        runCapture("as", "--64", "-o", obj.toString(), asm.toString());
        runCapture("ld", "-o", bin.toString(), obj.toString(),
                "-dynamic-linker", "/lib64/ld-linux-x86-64.so.2", "-lc", "-l:libpthread.so.0");
        bin.toFile().setExecutable(true);
        return runCapture(bin.toString());
    }

    @Test
    void clearNullsEverySlotX86(@TempDir Path tempDir) throws IOException {
        assumeNativeX86_64();
        assertEquals("", build(tempDir, "memclearx86"));
    }
}
