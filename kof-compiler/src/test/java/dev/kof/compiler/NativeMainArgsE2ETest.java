package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * §1187 — os argumentos de `main(String[] args)` no NATIVO x86-64.
 *
 * <p>O entry point passava um array VAZIO ("N3: passa array vazio — evita
 * segfault ao tratar argc como ponteiro"), entao `args.length` era 0 e todo
 * `args[0]` batia "array index out of bounds" enquanto o JVM respondia certo
 * (medido na fatia do app de exemplo, que lia `--frames N`).
 *
 * <p>O contrato do Kof: `args` carrega so' os argumentos DO USUARIO — o JVM
 * entrega `alpha beta` para `Main alpha beta` e o WASI derruba o argv[0] pelo
 * mesmo motivo. O entry do nativo le o vetor da pilha do `_start`
 * (`[rsp] = argc`, `[rsp+8] = argv[0..)`) e DESCARTA o argv[0].
 */
class NativeMainArgsE2ETest {

    private static final String PROBE = """
            main(String[] args) {
                println("n=" + args.length)
                var i = 0
                while (i < args.length) {
                    println("a" + i + "=" + args[i])
                    i = i + 1
                }
            }
            """;

    private static String run(Path dir, String[] command) throws Exception {
        var pb = new ProcessBuilder(command).redirectErrorStream(true);
        var p = pb.start();
        String out = new String(p.getInputStream().readAllBytes()).strip();
        assertTrue(p.waitFor(120, TimeUnit.SECONDS), "process finishes");
        assertEquals(0, p.exitValue(), "clean exit: " + out);
        return out;
    }

    @Test
    void theArgsMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        assumeTrue(NativeToolchainAssumptions.hasTool("as", "ld"), "native toolchain absent");
        Files.writeString(dir.resolve("Main.kf"), PROBE, StandardCharsets.UTF_8);
        var driver = new CompilerDriver();
        var nat = driver.compile(dir.resolve("Main.kf"), dir.resolve("n"), Target.NATIVE);
        assertTrue(nat.success(), "native compile: " + nat.diagnostics().getDiagnostics());
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("j"), Target.JVM);
        assertTrue(jvm.success(), "jvm compile: " + jvm.diagnostics().getDiagnostics());

        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String oracle = run(dir, new String[]{java, "-cp", dir.resolve("j").toString(),
                "Default.Main", "alpha", "beta"});
        String nativeOut = run(dir, new String[]{dir.resolve("n/Default/Main").toString(),
                "alpha", "beta"});
        assertEquals(oracle, nativeOut, "os dois alvos entregam os MESMOS argumentos");
        assertEquals("n=2\na0=alpha\na1=beta", nativeOut, "o argv[0] do SO nao entra em args");
    }

    @Test
    void theCrossTargetsCarryTheSameArguments(@TempDir Path dir) throws Exception {
        // §1187 no CROSS: o entry do riscv/aarch64 chamava `main` SEM array —
        // um segundo entry, fora do x86. Medido: n=0 no riscv e SEGFAULT no
        // aarch64; a correcao espelha o bloco do x86 no dialeto riscv (o
        // aarch64 herda pela traducao linha-a-linha).
        assumeTrue(NativeToolchainAssumptions.hasTool("-linux-gnu-as"), "cross toolchain absent");
        Files.writeString(dir.resolve("Main.kf"), PROBE, StandardCharsets.UTF_8);
        var home = System.getProperty("user.home");
        var cross = Path.of(home, ".local/share/kof-cross");
        assumeTrue(Files.isDirectory(cross), "kof-cross sysroot absent");
        for (String [] pair : new String[][]{{"aarch64", "NATIVE_AARCH64"}, {"riscv64", "NATIVE_RISCV64"}}) {
            String arch = pair[0];
            var as = "/usr/bin/" + arch + "-linux-gnu-as";
            var qemu = "/usr/bin/qemu-" + arch;
            assumeTrue(new File(as).exists() && new File(qemu).exists(),
                    "cross " + arch + " toolchain absent");
            var r = new CompilerDriver().compile(dir.resolve("Main.kf"),
                    dir.resolve("x-" + arch), Target.valueOf(pair[1]));
            assertTrue(r.success(), arch + " compile: " + r.diagnostics().getDiagnostics());
            var pb = new ProcessBuilder(qemu, "-L", cross.resolve("usr/" + arch + "-linux-gnu").toString(),
                    "-E", "LD_LIBRARY_PATH=" + cross.resolve("usr/" + arch + "-linux-gnu/lib"),
                    dir.resolve("x-" + arch + "/Default/Main").toString(), "alpha", "beta")
                    .redirectErrorStream(true);
            var p = pb.start();
            String out = new String(p.getInputStream().readAllBytes()).strip();
            assertTrue(p.waitFor(120, TimeUnit.SECONDS), arch + " finishes");
            assertEquals(0, p.exitValue(), arch + " clean exit: " + out);
            assertEquals("n=2\na0=alpha\na1=beta", out, arch + " entrega os mesmos argumentos");
        }
    }

    @Test
    void noArgumentsIsAnEmptyArray(@TempDir Path dir) throws Exception {
        assumeTrue(NativeToolchainAssumptions.hasTool("as", "ld"), "native toolchain absent");
        Files.writeString(dir.resolve("Main.kf"), PROBE, StandardCharsets.UTF_8);
        var r = new CompilerDriver().compile(dir.resolve("Main.kf"), dir.resolve("n"), Target.NATIVE);
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
        String out = run(dir, new String[]{dir.resolve("n/Default/Main").toString()});
        assertEquals("n=0", out, "sem argumentos = array vazio, nao o argv[0]");
    }

    @Test
    void freestandingArgsMatchJvmOracle(@TempDir Path dir) throws Exception {
        assumeTrue(NativeToolchainAssumptions.hasTool("as", "ld"), "native toolchain absent");
        Files.writeString(dir.resolve("Main.kf"), PROBE, StandardCharsets.UTF_8);
        var driver = new CompilerDriver();
        var fs = driver.compile(dir.resolve("Main.kf"), dir.resolve("fs"), Target.NATIVE,
                dev.kof.compiler.nat.NativeProfile.FREESTANDING);
        assertTrue(fs.success(), "freestanding compile: " + fs.diagnostics().getDiagnostics());
        String withArgs = run(dir, new String[]{dir.resolve("fs/Default/Main").toString(), "alpha", "beta"});
        assertEquals("n=2\na0=alpha\na1=beta", withArgs,
                "§1187/#1186: o perfil freestanding le o vetor do kernel ANTES do swap de rsp");
        String noArgs = run(dir, new String[]{dir.resolve("fs/Default/Main").toString()});
        assertEquals("n=0", noArgs, "sem argumentos no freestanding = array vazio (sem OOM)");
    }
}
