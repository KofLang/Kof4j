package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * kof-esp8266 gate 3 (H1+H2): the GPIO + delay HAL — the emitted asm carries
 * the ESP8266 register constants (the datasheet map: GPIO_OUT/W1TS/W1TC/ENABLE/IN
 * at 0x60000300..0x60000318, the delay on the 80 MHz cycle counter) and the ELF
 * links. The proof is the ARTIFACT (the register writes visible in the asm + the
 * link); RUNNING on hardware is the named gate-5 boundary.
 */
class NativeEsp8266GpioE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private static String tcRoot() {
        String env = System.getenv("KOF_ESP_HOME");
        if (env != null && !env.isBlank()) return env;
        for (Path p : List.of(
                Path.of(System.getProperty("user.home"), ".local/share/kof-esp8266"),
                Path.of("/tmp/xtensa162"))) {
            if (Files.isExecutable(p.resolve("bin/xtensa-esp-elf-as"))) return p.toString();
        }
        return "/tmp/xtensa162";
    }

    private static boolean hasToolchain() {
        String root = tcRoot();
        return Files.isExecutable(Path.of(root, "bin/xtensa-esp-elf-as"))
                && Files.isRegularFile(Path.of(root, "lib/xtensa_esp8266.so"));
    }

    @Override public String libraryName() { return "esp"; }
    @Override public List<String> libraryMarkers() { return List.of("Gpio.kf"); }

    @Test
    void theGpioProgramEmitsTheRegisterWrites(@TempDir Path dir) throws Exception {
        Path dir2 = dir.resolve("g");
        Files.createDirectories(dir2);
        copyLibrary(dir2);
        Files.writeString(dir2.resolve("Main.kf"), """
                import esp.Gpio

                main() {
                    gpioEnable(2)
                    gpioWrite(2, 1)
                    delay(500)
                    var v = gpioRead(2)
                    println(v)
                }
                """);
        CompilationResult r = new CompilerDriver()
                .compileSources(List.of(dir2.resolve("Main.kf")), dir2, Target.NATIVE_ESP8266);
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
        Path asm = null;
        try (var s2 = Files.walk(dir2.resolve("out"))) {
            for (Path f : s2.filter(x -> x.toString().endsWith(".s")).toList()) {
                asm = f; break;
            }
        }
        if (asm == null) fail("o asm não foi gerado");
        String text = Files.readString(asm);
        assertTrue(text.contains("0x6000030C"), "GPIO_ENABLE no asm");
        assertTrue(text.contains("0x60000304"), "GPIO_OUT_W1TS no asm");
        assertTrue(text.contains("0x60000308"), "GPIO_OUT_W1TC no asm");
        assertTrue(text.contains("0x60000318"), "GPIO_IN no asm");
        assertTrue(text.contains("kof_gpio_write"), "a rotina HAL");
        assertTrue(text.contains("80000"), "o delay no ciclo de 80 MHz");

        Assumptions.assumeTrue(hasToolchain(),
                "o toolchain xtensa não está provisionado — o asm é mantido");
        Path elf = null;
        try (var s2 = Files.walk(dir2.resolve("out"))) {
            for (Path f : s2.filter(x -> x.toString().equals(dir2.resolve("out").toString())
                    || x.getFileName().toString().equals("Main")).toList()) {
                if (Files.isRegularFile(f) && !f.toString().endsWith(".s")
                        && !f.toString().endsWith(".o") && !f.toString().endsWith(".ld")) {
                    elf = f; break;
                }
            }
        }
        if (elf == null) fail("o ELF não foi linkado");
        var pb = new ProcessBuilder(tcRoot() + "/bin/xtensa-esp-elf-objdump", "-f", elf.toString());
        String out = new String(pb.start().getInputStream().readAllBytes());
        assertTrue(out.contains("elf32-xtensa-le"), "little-endian (a ordem real do ESP8266): " + out);
    }
}
