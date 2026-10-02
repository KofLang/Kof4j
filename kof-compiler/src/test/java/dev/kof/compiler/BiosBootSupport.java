package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kof.compiler.nat.NativeProfile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Suporte do E2E do perfil BIOS ({@code BiosBootE2ETest}): localização de qemu,
 * montagem da linha de comando, leitura do serial e o helper de build. Vive fora
 * da classe de teste para mantê-la abaixo do limite de 500 linhas de teste
 * (Fase 3 da arquitetura de testes, {@code D-TEST-ARCHITECTURE-GO}); os testes e
 * o nome da classe seguem no {@code BiosBootE2ETest} — zero drift de citação.
 */
abstract class BiosBootSupport {

    protected static boolean hasTool(String tool, String... args) {
        String[] cmd = new String[args.length + 1];
        cmd[0] = tool;
        System.arraycopy(args, 0, cmd, 1, args.length);
        try {
            Process p = new ProcessBuilder(cmd).start();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    protected static Path ovmfPrefix() {
        String env = System.getenv("KOF_OVMF_HOME");
        if (env != null && Files.isRegularFile(Path.of(env, "usr/bin/qemu-system-x86_64"))) {
            return Path.of(env);
        }
        Path home = Path.of(System.getProperty("user.home"), ".local/share/kof-ovmf");
        if (Files.isRegularFile(home.resolve("usr/bin/qemu-system-x86_64"))) {
            return home;
        }
        return null;
    }

    protected static Path findQemu() {
        if (hasTool("qemu-system-x86_64", "--version")) return Path.of("qemu-system-x86_64");
        Path p = ovmfPrefix();
        return p == null ? null : p.resolve("usr/bin/qemu-system-x86_64");
    }

    protected Path build(Path tempDir, String program) throws IOException {
        CompilerDriver driver = new CompilerDriver();
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, program);
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE, NativeProfile.BIOS);
        assertTrue(result.success(),
                "compile BIOS inesperado: " + result.diagnostics().getDiagnostics());
        return outDir.resolve("Default/Main");
    }

    protected static java.util.List<String> qemuCmd(Path qemu, Path img, Path ser) {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        Path prefix = ovmfPrefix();
        boolean prefixQemu = prefix != null && qemu.startsWith(prefix);
        if (prefixQemu) {
            cmd.add(prefix.resolve("usr/lib/x86_64-linux-gnu/ld-linux-x86-64.so.2").toString());
            cmd.add("--library-path");
            cmd.add(prefix.resolve("usr/lib/x86_64-linux-gnu").toString());
        }
        cmd.add(qemu.toString());
        if (prefix != null) {
            // B-3: o firmware do boot legado é o SeaBIOS (debian: share/seabios),
            // NÃO o OVMF (share/qemu só traz os dtbs/roms). Sem o -L certo o qemu
            // morre em "could not load PC BIOS 'bios-256k.bin'" e nada boota.
            Path seabios = prefix.resolve("usr/share/seabios");
            Path datadir = Files.isDirectory(seabios) ? seabios : prefix.resolve("usr/share/qemu");
            cmd.addAll(java.util.List.of("-L", datadir.toString()));
        }
        cmd.addAll(java.util.List.of(
                "-machine", "pc", "-m", "128",
                "-display", "none", "-net", "none", "-no-reboot",
                "-serial", "file:" + ser,
                "-drive", "format=raw,file=" + img + ",if=ide"));
        return cmd;
    }

    protected String serialText(Path log) throws IOException {
        return Files.readString(log, StandardCharsets.ISO_8859_1).replace("\0", "");
    }

    /** B-3b-3: localiza a magia do header do payload ({@code KOFPAYLD}) na imagem flat. */
    protected static int findMagic(byte[] img) {
        for (int i = 0; i + 8 <= img.length; i++) {
            if (img[i] == 'K' && img[i + 1] == 'O' && img[i + 2] == 'F' && img[i + 3] == 'P'
                    && img[i + 4] == 'A' && img[i + 5] == 'Y' && img[i + 6] == 'L' && img[i + 7] == 'D') {
                return i;
            }
        }
        return -1;
    }
}
