package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M-6 (#1135, D-NATIVE-MACOS) — a família ENVIRON da costura.
 *
 * <p><b>O DEFEITO (medido):</b> o {@code kof_env_getc} lia o ambiente
 * ATRAVÉS DO {@code /proc/self/environ} — o procfs do Linux. No Darwin esse
 * arquivo não existe: a face do {@code config.env(...)} nunca acharia a
 * variável. E o corpo trazia a leitura do procfs junto (syscalls crus), o que
 * a costura existe para evitar.
 *
 * <p>A correção é a família {@code kof_plat_env_get}: no Linux o mesmo scan do
 * procfs (agora dentro da costura, com {@code kof_plat_open/read/close}), no
 * Darwin o {@code getenv} da libSystem — o MESMO contrato
 * ({@code rdi = nome C string}, {@code rax = KofString* | 0}).
 */
class NativeMacOSEnvSeamE2ETest {

    private static final String PROBE = """
            main() {
                println(config.env("KOF_MACOS_ENV_PROBE"))
                println(secrets.get("HOME"))
                log.info("probe")
            }
            """;

    private static String assembly(Path tmp, String profile) throws IOException {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, PROBE);
        String priorKeep = System.getProperty("KOF_KEEP_ASM");
        System.setProperty("KOF_KEEP_ASM", "1");
        dev.kof.compiler.nat.NativeProfile previous =
                dev.kof.compiler.nat.NativeProfile.active;
        try {
            CompilerDriver driver = new CompilerDriver();
            driver.setNativeProfile(profile == null
                    ? dev.kof.compiler.nat.NativeProfile.active
                    : dev.kof.compiler.nat.NativeProfile.of(profile));
            driver.compile(src, tmp.resolve("out-" + (profile == null ? "host" : profile)),
                    Target.NATIVE);
            return Files.readString(tmp.resolve("out-" + (profile == null ? "host" : profile)
                    + "/Default/Main.s"));
        } finally {
            dev.kof.compiler.nat.NativeProfile.active = previous;
            if (priorKeep == null) {
                System.clearProperty("KOF_KEEP_ASM");
            } else {
                System.setProperty("KOF_KEEP_ASM", priorKeep);
            }
        }
    }

    private static String body(String asm, String label) {
        String[] lines = asm.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        boolean inside = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.equals(label + ":")) {
                inside = true;
                continue;
            }
            if (!inside) continue;
            if (trimmed.startsWith(".section")) break;
            if (trimmed.endsWith(":") && !trimmed.contains(" ")
                    && !trimmed.startsWith(".")) break;
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    /**
     * O corpo até o PRÓXIMO rótulo (inclusive local): o suficiente para um
     * delegador de uma linha, sem puxar o resto do arquivo.
     */
    private static String untilNextLabel(String asm, String label) {
        String[] lines = asm.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        boolean inside = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.equals(label + ":")) {
                inside = true;
                continue;
            }
            if (!inside) continue;
            if (trimmed.endsWith(":") && !trimmed.contains(" ") && !trimmed.startsWith(".")) break;
            if (trimmed.endsWith(":") && trimmed.startsWith(".")) break;
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    /**
     * O DARWIN busca no ambiente pela libSystem — o procfs do Linux não existe
     * lá.
     */
    @Test
    void theDarwinEnvBodyAsksTheLibSystem(@TempDir Path tmp) throws IOException {
        String asm = assembly(tmp, "macos");
        String env = body(asm, "kof_plat_env_get");
        assertTrue(env.contains("call getenv"),
                "the Darwin env lookup is the libSystem's getenv:\n" + env);
        assertTrue(env.contains("call kof_string_from_literal"),
                "the value becomes a real KofString:\n" + env);
        assertFalse(env.contains("syscall"),
                "no raw syscall in the Darwin env body:\n" + env);
        assertFalse(env.contains("/proc/self/environ"),
                "the Linux procfs path does not leak into the Darwin env body:\n" + env);
    }

    /**
     * O {@code open} do Darwin é VARIÁDICO: o AL tem de dizer quantos
     * registradores XMM foram usados, e a falha é -1 com o errno num slot
     * (a costura devolve -errno, como o kernel).
     */
    @Test
    void theDarwinOpenIsVariadicAndNormalizesTheErrno(@TempDir Path tmp) throws IOException {
        String open = body(assembly(tmp, "macos"), "kof_plat_open");
        assertTrue(open.contains("call open"),
                "the Darwin open is the libSystem's:\n" + open);
        assertTrue(open.contains("xorl %eax, %eax"),
                "open is variadic: AL must hold the XMM count:\n" + open);
        assertTrue(open.contains("call __error"),
                "the Darwin errno comes from int *__error(void):\n" + open);
        assertTrue(open.contains("negl %eax"),
                "the failure is normalized to -errno:\n" + open);
    }

    /**
     * O sítio que consome a face (o {@code kof_env_getc}) só DELEGA — nada de
     * syscall e nada de procfs.
     */
    @Test
    void theEnvLookupSiteOnlyDelegates(@TempDir Path tmp) throws IOException {
        String wrapper = untilNextLabel(assembly(tmp, "macos"), "kof_env_getc");
        assertTrue(wrapper.contains("jmp kof_plat_env_get"),
                "the lookup site delegates to the seam:\n" + wrapper);
        assertFalse(wrapper.contains("syscall"),
                "no raw syscall left at the lookup site:\n" + wrapper);
    }

    /**
     * O perfil do HOST (Linux) mantem o contrato: o scan do procfs, agora
     * DENTRO da costura.
     */
    @Test
    void theLinuxEnvBodyReadsTheProcfsThroughTheSeam(@TempDir Path tmp) throws IOException {
        String asm = assembly(tmp, null);
        String env = body(asm, "kof_plat_env_get");
        assertTrue(env.contains("call kof_plat_open"),
                "the Linux env body opens through the seam:\n" + env);
        assertTrue(env.contains("call kof_plat_read"),
                "the Linux env body reads through the seam:\n" + env);
        assertTrue(env.contains("call kof_plat_close"),
                "the Linux env body closes through the seam:\n" + env);
        assertTrue(asm.contains("/proc/self/environ"),
                "the Linux env body reads the procfs");
    }

    /**
     * Os dois sítios IRMÃOS do environ também passaram pela costura — o
     * `.Llog_parse_level` (o nível do log) e o `kof_sec_secret_get` (a
     * variável de ambiente com segredo). Antes eles abriam o
     * {@code /proc/self/environ} eles mesmos.
     */
    @Test
    void theSiblingEnvSitesGoThroughTheSeam(@TempDir Path tmp) throws IOException {
        String asm = assembly(tmp, "macos");
        assertFalse(asm.contains("/proc/self/environ"),
                "no Linux procfs path survives in the macOS profile");

        String log = body(asm, ".Llog_parse_level");
        assertTrue(log.contains("call kof_plat_env_get"),
                "the log level comes from the seam:\n" + log);
        assertFalse(log.contains("syscall"),
                "no raw syscall at the log level site:\n" + log);

        String secret = body(asm, "kof_sec_secret_get");
        assertTrue(secret.contains("call kof_plat_env_get"),
                "the secret lookup comes from the seam:\n" + secret);
        assertFalse(secret.contains("syscall"),
                "no raw syscall at the secret site:\n" + secret);
    }

    /**
     * O perfil do HOST (Linux) mantem o contrato: os dois sítios também
     * delegam.
     */
    @Test
    void theLinuxSiblingEnvSitesGoThroughTheSeam(@TempDir Path tmp) throws IOException {
        String asm = assembly(tmp, null);
        String log = body(asm, ".Llog_parse_level");
        assertTrue(log.contains("call kof_plat_env_get"),
                "the log level comes from the seam:\n" + log);
        String secret = body(asm, "kof_sec_secret_get");
        assertTrue(secret.contains("call kof_plat_env_get"),
                "the secret lookup comes from the seam:\n" + secret);
    }

    /**
     * O ASSEMBLER REAL aceita a família inteira no perfil macOS.
     */
    @Test
    void theRealMachOAssemblerAcceptsTheEnvFamily(@TempDir Path tmp) throws Exception {
        Path zig = machoToolchain();
        org.junit.jupiter.api.Assumptions.assumeTrue(zig != null,
                "the Mach-O toolchain is absent (scripts/provision-macho-toolchain.sh)");
        String macho = dev.kof.compiler.nat.NativeMachO.translate(assembly(tmp, "macos"));
        StringBuilder withoutElfOnly = new StringBuilder();
        for (String line : macho.split("\n", -1)) {
            String t = line.trim();
            if (t.equals("syscall") || t.contains("%fs:") || t.contains("@tpoff")) continue;
            withoutElfOnly.append(line).append('\n');
        }
        Path asmFile = tmp.resolve("Main.macho.s");
        Files.writeString(asmFile, withoutElfOnly.toString());
        Path obj = tmp.resolve("Main.macho.o");
        ProcessBuilder pb = new ProcessBuilder(
                zig.toString(), "cc", "-target", "x86_64-macos-none",
                "-c", asmFile.toString(), "-o", obj.toString());
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes());
        int rc = proc.waitFor();
        assertEquals(0, rc,
                "the Mach-O assembler must accept the env family:\n" + output);
    }

    private static Path machoToolchain() {
        String home = System.getenv("KOF_MACHO_HOME");
        List<Path> candidates = new ArrayList<>();
        if (home != null && !home.isBlank()) {
            candidates.add(Path.of(home, "bin", "zig"));
        }
        candidates.add(Path.of(System.getProperty("user.home", ""),
                ".local", "share", "kof-macho", "bin", "zig"));
        for (Path p : candidates) {
            if (Files.isExecutable(p)) return p;
        }
        return null;
    }
}
