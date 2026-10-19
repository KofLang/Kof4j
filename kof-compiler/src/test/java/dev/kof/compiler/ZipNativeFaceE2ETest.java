package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A face NATIVA da biblioteca de arquivos (#852): listar, ler e EXTRAIR um ZIP
 * rodando como binário — x86-64 nativo local e riscv64/aarch64 sob qemu.
 *
 * <p>Por que este teste existe: as fatias 1–3 do `libs/archive` foram provadas em
 * JVM + Script, e a livraria se apresenta como "vale onde as primitivas de
 * `kof.io` valem". Isto é o que prova a afirmação nos alvos nativos — a mesma
 * classe de prova que os outros pacotes oficiais têm (`FileLibraryE2ETest`,
 * `IniReaderE2ETest`), e o único lugar onde `isSymlink()` (JVM+Native, lacuna
 * `IOJS001` no JS) entra no caminho da extração.
 */
class ZipNativeFaceE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private static final String STORED_TEXT = "hello stored";
    private static final String NESTED_TEXT = "aninhado";

    @ParameterizedTest
    @EnumSource(value = Target.class, names = {"NATIVE", "NATIVE_RISCV64", "NATIVE_AARCH64"})
    void listReadAndExtractRunOnNative(Target target, @TempDir Path dir) throws Exception {
        if (target.isNative() && target != Target.NATIVE) {
            Assumptions.assumeTrue(NativeRiscv64E2ETest.hasToolchain(target.nativeArch()),
                    target.nativeArch() + " toolchain/QEMU required");
        }
        Path zip = dir.resolve("lote.zip");
        try (OutputStream raw = Files.newOutputStream(zip); ZipOutputStream out = new ZipOutputStream(raw)) {
            byte[] payload = STORED_TEXT.getBytes(StandardCharsets.UTF_8);
            ZipEntry stored = new ZipEntry("alfa.txt");
            stored.setMethod(ZipEntry.STORED);
            stored.setSize(payload.length);
            stored.setCompressedSize(payload.length);
            stored.setCrc(ArchiveFixtureSupport.crc32(payload));
            out.putNextEntry(stored);
            out.write(payload);
            out.closeEntry();
            out.putNextEntry(new ZipEntry("dir/nota.txt"));       // default = DEFLATED
            out.write(NESTED_TEXT.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }

        Path outDir = dir.resolve("extraido");
        Files.writeString(dir.resolve("Main.kf"), """
                import archive.Zip

                main() {
                    var z = Zip("%s")
                    println("entradas=" + z.entries.size())
                    println("stored=" + z.readText(z.entries.get(0), 4096))
                    println("escritos=" + z.extractTo("%s", 4096))
                    println("lido=" + File("%s/dir/nota.txt").readText())
                }
                """.formatted(zip.toAbsolutePath(), outDir.toAbsolutePath(), outDir.toAbsolutePath()));

        String output = runOnNative(target, dir);
        assertEquals("entradas=2\nstored=" + STORED_TEXT + "\nescritos=2\nlido=" + NESTED_TEXT, output,
                target + ": listar, ler e EXTRAIR no binário nativo");
        assertEquals(NESTED_TEXT, Files.readString(outDir.resolve("dir/nota.txt")),
                "o arquivo extraído tem de estar no destino (o teste roda no host, o binário escreveu)");
        assertTrue(Files.isRegularFile(outDir.resolve("alfa.txt")), "as DUAS entradas extraídas");
    }

    private String runOnNative(Target target, Path dir) throws Exception {
        Path out = dir.resolve("out");
        return withLibrary(dir, () -> {
            CompilationResult r = new CompilerDriver().compile(dir.resolve("Main.kf"), out, target);
            assertTrue(r.success(), target + " compile: " + r.diagnostics().getDiagnostics());
            Path binary = out.resolve("Default/Main");
            assertTrue(Files.isRegularFile(binary), target + ": binário tem de existir");
            ProcessBuilder pb = target == Target.NATIVE
                    ? new ProcessBuilder(binary.toString())
                    : NativeRiscv64E2ETest.qemu(target.nativeArch(), binary);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").strip();
            assertEquals(0, p.waitFor(), target + " output: " + o);
            return o;
        });
    }

    private <T> T withLibrary(Path root, CheckedSupplier<T> action) throws Exception {
        copyLibrary(root.resolve("kof-install/lib/kof-libs"));
        String previous = System.getProperty("kof.install.dir");
        System.setProperty("kof.install.dir", root.resolve("kof-install").toString());
        try {
            return action.get();
        } finally {
            if (previous == null) System.clearProperty("kof.install.dir");
            else System.setProperty("kof.install.dir", previous);
        }
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    @Override
    public String libraryName() {
        return "archive";
    }

    @Override
    public List<String> libraryNames() {
        return List.of("archive", "image");
    }

    @Override
    public List<String> libraryMarkers() {
        return List.of("Zip.kf");
    }
}
