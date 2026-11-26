package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M-2 (#1135, D-NATIVE-MACOS) — a família PROCESS (o spawn do
 * {@code kof_process_run}) pela costura.
 *
 * <p><b>O QUE MUDOU:</b> o {@code RuntimeProcessSpawn} abria os pipes, dava
 * {@code fork}, montava o {@code stdin}, dava {@code dup2}, marcava o
 * {@code FD_CLOEXEC}, executava ({@code execveat}) e esperava
 * ({@code wait4}) com syscalls CRUS — nada disso existe como syscall no
 * Darwin. Os 25 sítios agora chamam a costura, e cada face tem o seu corpo:
 * no Linux os syscalls de sempre (a semântica não muda); no Darwin a
 * libSystem ({@code pipe}, {@code fork}, {@code dup2}, {@code execve},
 * {@code wait4}, {@code kill}, {@code fcntl}) com a falha normalizada em
 * {@code -errno}.
 *
 * <p><b>AS DUAS DIFERENÇAS MEDIDAS:</b> o {@code pipe2} do Linux não existe
 * no Darwin — o corpo faz {@code pipe} e depois {@code fcntl(F_SETFD,
 * FD_CLOEXEC)} nos dois descritores quando o sítio pediu o
 * {@code O_CLOEXEC}; e o {@code execveat} também não existe — com o
 * {@code AT_FDCWD} ele é o mesmo {@code execve}.
 */
class NativeMacOSProcessSeamE2ETest {

    private static String runtime(String profile) {
        dev.kof.compiler.nat.NativeProfile previous =
                dev.kof.compiler.nat.NativeProfile.active;
        try {
            dev.kof.compiler.nat.NativeProfile.active = profile == null
                    ? previous
                    : dev.kof.compiler.nat.NativeProfile.of(profile);
            return dev.kof.compiler.NativeRuntime.generateRuntimeAssembly();
        } finally {
            dev.kof.compiler.nat.NativeProfile.active = previous;
        }
    }

    private static String body(String asm, String label) {
        String[] lines = asm.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        boolean inside = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (!inside) {
                if (trimmed.equals(label + ":")) inside = true;
                continue;
            }
            sb.append(line).append('\n');
            if (trimmed.equals("ret")) break;
        }
        return sb.toString();
    }

    /**
     * O SPAWN do runtime não faz mais syscall cru — em NENHUM perfil (a
     * migração é a mesma para os dois: os sítios chamam a costura).
     */
    @Test
    void theSpawnFamilyHasNoRawSyscall() {
        for (String profile : List.of("macos", "linux")) {
            String asm;
            try {
                asm = runtime(profile.equals("linux") ? null : profile);
            } catch (RuntimeException e) {
                continue;
            }
            String spawn = body(asm, "kof_process_spawn");
            assertFalse(spawn.contains("syscall"),
                    "no raw syscall left in the spawn family (" + profile + "):\n" + spawn);
            assertTrue(spawn.contains("call kof_plat_fork"),
                    "the fork goes through the seam (" + profile + "):\n" + spawn);
        }
    }

    /**
     * Os corpos DARWIN da família chamam a libSystem — e o {@code pipe2}/
     * {@code execveat} emulam o que o Darwin não tem.
     */
    @Test
    void theDarwinBodiesCallTheLibSystem() {
        String asm = runtime("macos");
        String pipe2 = body(asm, "kof_plat_pipe2");
        assertTrue(pipe2.contains("call pipe"),
                "the Darwin pipe2 starts with pipe:\n" + pipe2);
        assertTrue(pipe2.contains("call fcntl"),
                "and applies FD_CLOEXEC with fcntl:\n" + pipe2);
        assertFalse(pipe2.contains("syscall"),
                "no raw syscall in the Darwin pipe2:\n" + pipe2);

        String exec = body(asm, "kof_plat_exec");
        assertTrue(exec.contains("call execve"),
                "the Darwin exec is execve (no execveat):\n" + exec);
        assertTrue(exec.contains("call __error"),
                "the failure comes from the Darwin errno:\n" + exec);

        for (String fn : List.of("kof_plat_fork", "kof_plat_dup2", "kof_plat_wait",
                "kof_plat_kill")) {
            String b = body(asm, fn);
            assertFalse(b.contains("syscall"),
                    "no raw syscall in " + fn + " (Darwin):\n" + b);
        }
        assertTrue(body(asm, "kof_plat_wait").contains("call wait4"),
                "the wait is the libSystem's wait4");
        assertTrue(body(asm, "kof_plat_dup2").contains("call dup2"),
                "the dup2 is the libSystem's");
    }

    /**
     * O perfil do HOST mantém o contrato do syscall — os números de sempre.
     */
    @Test
    void theLinuxBodiesKeepTheSyscallNumbers() {
        String asm = runtime(null);
        assertTrue(body(asm, "kof_plat_fork").contains("movq $57, %rax"),
                "the Linux fork is the syscall 57");
        assertTrue(body(asm, "kof_plat_pipe2").contains("movq $293, %rax"),
                "the Linux pipe2 is the syscall 293");
        assertTrue(body(asm, "kof_plat_exec").contains("movq $436, %rax"),
                "the Linux exec is the syscall execveat 436");
        assertTrue(body(asm, "kof_plat_wait").contains("movq $61, %rax"),
                "the Linux wait is the syscall wait4 61");
    }
}
