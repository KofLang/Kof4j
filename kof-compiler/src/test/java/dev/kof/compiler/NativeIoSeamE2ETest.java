package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M-6 (#1135, D-NATIVE-MACOS) — a io do RUNTIME pela costura: o
 * {@code read}/{@code write}/{@code close} do runtime nativo passam a falar
 * com a costura {@code kof_plat_*} (e não com o syscall Linux cru).
 *
 * <p><b>POR QUE ISTO É A LINHA M-6:</b> a costura é o ÚNICO ponto onde o
 * runtime fala com o SO. O runtime usava o syscall Linux direto na io (o
 * gate 2 media isso como "fora-da-costura"), então o perfil macOS não
 * teria como falar a libSystem — os bytes lidos/escritos iam pelo ABI Linux
 * num SO Darwin.
 *
 * <p><b>A PROVA É ESTRUTURAL (o texto emitido):</b> no texto do programa
 * real, a io passa a chamar a costura — e o syscall cru da io some. O
 * {@code fstat} (o syscall 5) fica CRU de propósito: o {@code struct stat}
 * do Darwin tem um layout diferente do Linux, então ele é a família "stat"
 * da costura (a linha M-6 seguinte) — não é um read disfarçado.
 */
class NativeIoSeamE2ETest {

    /** Um programa que usa a io de verdade (o read/write/close). */
    private static final String IO_PROGRAM = """
            main() {
                var f = File("/tmp/kof-io-seam-probe.bin")
                var b = new Int[4]
                println(f.writeBytes(b))
                println(f.readBytes().length)
            }
            """;

    /** O texto emitido no alvo nativo (perfil host). */
    private static String nativeAssembly(Path tmp) throws IOException {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, IO_PROGRAM);
        String priorKeep = System.getProperty("KOF_KEEP_ASM");
        System.setProperty("KOF_KEEP_ASM", "1");
        try {
            CompilerDriver driver = new CompilerDriver();
            CompilationResult host = driver.compile(src, tmp.resolve("out"), Target.NATIVE);
            assertTrue(host.success(), "the native compile succeeds: "
                    + host.diagnostics().getDiagnostics());
            return Files.readString(tmp.resolve("out/Default/Main.s"));
        } finally {
            if (priorKeep == null) {
                System.clearProperty("KOF_KEEP_ASM");
            } else {
                System.setProperty("KOF_KEEP_ASM", priorKeep);
            }
        }
    }

    /**
     * A IO PASSA PELA COSTURA: o texto emitido tem as chamadas da costura e
     * NÃO tem mais o syscall Linux cru da io (o read = 0, o write = 1 e o
     * close = 3).
     */
    @Test
    void theIoGoesThroughTheSeam(@TempDir Path tmp) throws IOException {
        String asm = nativeAssembly(tmp);
        assertTrue(asm.contains("call kof_plat_read"),
                "the io reads through the seam: " + count(asm, "call kof_plat_read"));
        assertTrue(asm.contains("call kof_plat_write"),
                "the io writes through the seam: " + count(asm, "call kof_plat_write"));
        assertTrue(asm.contains("call kof_plat_close"),
                "the io closes through the seam: " + count(asm, "call kof_plat_close"));
        // o syscall Linux cru da io sumiu dos CORPOS da io (o fstat fica — o
        // struct stat do Darwin tem outro layout: a família stat é a linha M-6)
        for (String fn : List.of("kof_io_read_bytes", "kof_io_write_bytes",
                "kof_read_line", "kof_read_file", "kof_write_file")) {
            String body = block(asm, fn);
            assertTrue(!body.isEmpty(), "the io function is in the text: " + fn);
            assertTrue(!body.contains("movq $0, %rax\n                syscall")
                            && !body.contains("movq $1, %rax\n                syscall")
                            && !body.contains("movq $3, %rax\n                syscall"),
                    "no raw read/write/close syscall left in " + fn + ": " + body);
        }
    }

    /** O corpo de uma função (do rótulo até o próximo rótulo global). */
    private static String block(String asm, String label) {
        String[] lines = asm.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        boolean inside = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.equals(label + ":")) {
                inside = true;
                continue;
            }
            if (inside && trimmed.endsWith(":") && !trimmed.contains(" ")
                    && !trimmed.startsWith(".")) break;
            if (inside) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    /** Quantas vezes o texto contém o fragmento. */
    private static int count(String text, String needle) {
        int n = 0;
        int at = text.indexOf(needle);
        while (at >= 0) {
            n++;
            at = text.indexOf(needle, at + needle.length());
        }
        return n;
    }

    /**
     * A MESMA IO no perfil macOS: a costura fala a libSystem (o read/write/
     * close da libc) — o runtime não fala o ABI Linux.
     */
    @Test
    void theMacosProfileReadsThroughTheLibSystem(@TempDir Path tmp) throws IOException {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, IO_PROGRAM);
        String priorKeep = System.getProperty("KOF_KEEP_ASM");
        System.setProperty("KOF_KEEP_ASM", "1");
        dev.kof.compiler.nat.NativeProfile previous =
                dev.kof.compiler.nat.NativeProfile.active;
        try {
            CompilerDriver driver = new CompilerDriver();
            driver.setNativeProfile(dev.kof.compiler.nat.NativeProfile.of("macos"));
            driver.compile(src, tmp.resolve("outmac"), Target.NATIVE);
            String asm = Files.readString(tmp.resolve("outmac/Default/Main.s"));
            assertTrue(asm.contains("call kof_plat_read"),
                    "the io reads through the seam on macOS too");
            // o read/write/close não falam mais o ABI Linux — o fstat fica
            // (o struct stat do Darwin tem outro layout: a família stat é a
            // linha M-6 seguinte)
            String body = block(asm, "kof_io_read_bytes");
            assertTrue(!body.contains("movq $0, %rax\n                syscall"),
                    "the macOS io does not speak the Linux read ABI: " + body);
        } finally {
            dev.kof.compiler.nat.NativeProfile.active = previous;
            if (priorKeep == null) {
                System.clearProperty("KOF_KEEP_ASM");
            } else {
                System.setProperty("KOF_KEEP_ASM", priorKeep);
            }
        }
    }
}
