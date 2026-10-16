package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #852 (fatia 2) — leitura de gzip (RFC 1952) em Kof puro (`libs/archive/Gzip.kf`),
 * pela MESMA regra de composição da fatia 1: o DEFLATE vai para o
 * `libs/image/Inflate.kf` que já existia e o CRC-32/ISIZE do trailer saem do
 * `zipCrc32` escrito para o ZIP.
 *
 * <p>O oráculo é o `java.util.zip.GZIPOutputStream` do host. A face MULTI-MEMBRO
 * é a que importa aqui: o trailer NÃO pode ser assumido como os últimos 8 bytes
 * do arquivo (isso só vale para um membro único) — ele é localizado onde o fluxo
 * DEFLATE de fato TERMINOU.
 */
class GzipLibraryE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String LOG = "linha de log ".repeat(20);   // 260 bytes
    private static final String FIRST = "primeiro membro ";         // 16
    private static final String SECOND = "segundo membro";          // 14

    // --- oráculo do host -----------------------------------------------------

    private static Path buildGzFixture(Path dir, String name, String text) throws Exception {
        Path gz = dir.resolve(name);
        try (OutputStream raw = Files.newOutputStream(gz); GZIPOutputStream out = new GZIPOutputStream(raw)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return gz;
    }

    /** Dois MEMBROS concatenados no mesmo arquivo (RFC 1952 §2.2). */
    private static Path buildMultiMember(Path dir, String name, String first, String second) throws Exception {
        Path gz = dir.resolve(name);
        try (OutputStream raw = Files.newOutputStream(gz); GZIPOutputStream out = new GZIPOutputStream(raw)) {
            out.write(first.getBytes(StandardCharsets.UTF_8));
        }
        try (OutputStream raw = Files.newOutputStream(gz, StandardOpenOption.APPEND);
             GZIPOutputStream out = new GZIPOutputStream(raw)) {
            out.write(second.getBytes(StandardCharsets.UTF_8));
        }
        return gz;
    }

    // --- programa Kof --------------------------------------------------------

    private static String simpleProbe(Path gz, int maxBytes) {
        return """
                import archive.Gzip

                main() {
                    println("bytes=" + gzipRead("%s", %d).length)
                    println("texto=" + gzipReadText("%s", %d))
                }
                """.formatted(gz.toAbsolutePath(), maxBytes, gz.toAbsolutePath(), maxBytes);
    }

    private static final String SINGLE_FACE = """
            bytes=260
            texto=%s
            """.formatted(LOG).strip();

    private static String refusalProbe(Path gz, int maxBytes) {
        return """
                import archive.Gzip

                main() {
                    try {
                        var bytes = gzipRead("%s", %d)
                        println("LEU " + bytes.length)
                    } catch (String m) {
                        println("recusado: " + m)
                    }
                }
                """.formatted(gz.toAbsolutePath(), maxBytes);
    }

    @Test
    void readsASingleMemberOnJvm() throws Exception {
        Path root = tmp.resolve("jvm");
        Files.createDirectories(root);
        Path gz = buildGzFixture(root, "log.gz", LOG);
        Files.writeString(root.resolve("Main.kf"), simpleProbe(gz, 4096));
        assertEquals(SINGLE_FACE, runGzOnJvm(root), "JVM: gzip de um membro (oraculo GZIPOutputStream)");
    }

    @Test
    void readsASingleMemberOnScript() throws Exception {
        Path root = tmp.resolve("script");
        Files.createDirectories(root);
        Path gz = buildGzFixture(root, "log.gz", LOG);
        Files.writeString(root.resolve("Main.kf"), simpleProbe(gz, 4096));
        assertEquals(SINGLE_FACE, runGzOnScript(root), "Script: a MESMA face");
    }

    /**
     * A face que separa uma implementação correta de uma que "quase funciona":
     * dois membros concatenados. Achar o trailer do PRIMEIRO pelo fim do arquivo
     * daria lixo aqui, e o teste tem de pegar isso.
     */
    @Test
    void readsConcatenatedMembersOnJvmAndScript() throws Exception {
        Path root = tmp.resolve("multi");
        Files.createDirectories(root);
        Path gz = buildMultiMember(root, "dois.gz", FIRST, SECOND);
        Files.writeString(root.resolve("Main.kf"), simpleProbe(gz, 4096));
        String expected = "bytes=30\ntexto=" + FIRST + SECOND;
        assertEquals(expected, runGzOnJvm(root), "multi-membro (JVM)");
        assertEquals(expected, runGzOnScript(root), "multi-membro (Script)");
    }

    /** O teto é do CHAMADOR e vale para o arquivo inteiro, não por membro. */
    @Test
    void byteCeilingRefusesOnJvmAndScript() throws Exception {
        Path root = tmp.resolve("cap");
        Files.createDirectories(root);
        Path gz = buildGzFixture(root, "log.gz", LOG);
        Files.writeString(root.resolve("Main.kf"), refusalProbe(gz, 10));
        String expected = "recusado: archive: gzip member 0 does not inflate inside the limit of 10 bytes — refused";
        assertEquals(expected, runGzOnJvm(root), "teto (JVM)");
        assertEquals(expected, runGzOnScript(root), "teto (Script)");

        Path multi = buildMultiMember(root, "dois.gz", FIRST, SECOND);
        Files.writeString(root.resolve("Main.kf"), refusalProbe(multi, 20));
        String expectedMulti = "recusado: archive: gzip member 1 does not inflate inside the limit of 20 bytes — refused";
        assertEquals(expectedMulti, runGzOnJvm(root), "teto ATRAVESSADO o primeiro membro (JVM)");
        assertEquals(expectedMulti, runGzOnScript(root), "teto ATRAVESSADO o primeiro membro (Script)");
    }

    /** CRC-32 e ISIZE do trailer são conferidos de verdade (membro único: último CRC/ISIZE). */
    @Test
    void refusesCorruptedCrcAndWrongIsize() throws Exception {
        Path root = tmp.resolve("trailer");
        Files.createDirectories(root);
        Path gz = buildGzFixture(root, "log.gz", LOG);

        byte[] badCrc = ArchiveFixtureSupport.read(gz);
        badCrc[badCrc.length - 8] = (byte) (badCrc[badCrc.length - 8] ^ 0xFF);
        Path crcPath = root.resolve("badcrc.gz");
        Files.write(crcPath, badCrc);

        byte[] badIsize = ArchiveFixtureSupport.read(gz);
        ArchiveFixtureSupport.putU32(badIsize, badIsize.length - 4, 999);
        Path isizePath = root.resolve("badisize.gz");
        Files.write(isizePath, badIsize);

        Files.writeString(root.resolve("Main.kf"), refusalProbe(crcPath, 4096));
        String crc = "recusado: archive: gzip CRC-32 mismatch on member 0";
        assertEquals(crc, runGzOnJvm(root), "CRC (JVM)");
        assertEquals(crc, runGzOnScript(root), "CRC (Script)");

        Files.writeString(root.resolve("Main.kf"), refusalProbe(isizePath, 4096));
        String isize = "recusado: archive: gzip ISIZE says 999 but member 0 inflates to 260";
        assertEquals(isize, runGzOnJvm(root), "ISIZE (JVM)");
        assertEquals(isize, runGzOnScript(root), "ISIZE (Script)");
    }

    /** Truncado, não-gzip e flags reservadas: diagnóstico explícito, nunca silêncio. */
    @Test
    void refusesTruncatedForeignAndReservedFlagInputs() throws Exception {
        Path root = tmp.resolve("formas");
        Files.createDirectories(root);
        Path gz = buildGzFixture(root, "log.gz", LOG);
        byte[] raw = ArchiveFixtureSupport.read(gz);

        Path truncated = root.resolve("trunc.gz");
        Files.write(truncated, java.util.Arrays.copyOf(raw, raw.length - 5));

        Path notGzip = root.resolve("notgz.gz");
        Files.writeString(notGzip, "isto nao e gzip");

        byte[] reserved = raw.clone();
        reserved[3] = (byte) (reserved[3] | 0xE0);
        Path reservedPath = root.resolve("resv.gz");
        Files.write(reservedPath, reserved);

        for (boolean jvm : new boolean[] {true, false}) {
            Files.writeString(root.resolve("Main.kf"), refusalProbe(truncated, 4096));
            assertEquals("recusado: archive: truncated gzip trailer",
                    jvm ? runGzOnJvm(root) : runGzOnScript(root), "truncado");

            Files.writeString(root.resolve("Main.kf"), refusalProbe(notGzip, 4096));
            assertEquals("recusado: archive: not a gzip stream (bad magic at 0)",
                    jvm ? runGzOnJvm(root) : runGzOnScript(root), "não-gzip");

            Files.writeString(root.resolve("Main.kf"), refusalProbe(reservedPath, 4096));
            assertEquals("recusado: archive: gzip reserved flag bits set — refused",
                    jvm ? runGzOnJvm(root) : runGzOnScript(root), "flags reservadas");
        }
    }

    /** Estrutura: o teto é MEMÓRIA — o buffer é alocado no teto do chamador, nunca no declarado. */
    @Test
    void libraryFileIsComposedNotRewritten() throws Exception {
        String source = Files.readString(findLibraryRoot().resolve("Gzip.kf"));
        assertTrue(source.contains("import image.Inflate"),
                "o DEFLATE tem de vir do image.Inflate (composição), não de um codec novo");
        assertTrue(source.contains("Inflate(payload, budget)"),
                "o inflate roda com o orçamento do CHAMADOR, nunca com um tamanho do arquivo");
        assertTrue(source.contains("zipCrc32") && source.contains("zipU32"),
                "o CRC-32 e a leitura little-endian vêm do slice 1 (Zip.kf)");
    }

    // --- execução ------------------------------------------------------------

    private String runGzOnJvm(Path root) throws Exception {
        Path outDir = root.resolve("out");
        return withLibrary(root, () -> {
            CompilationResult r = driver.compileSources(List.of(root.resolve("Main.kf")), outDir, Target.JVM, root);
            assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            assertEquals(0, p.waitFor(), "JVM run: " + o);
            return o;
        });
    }

    private String runGzOnScript(Path root) throws Exception {
        KofInterpreter.Result r = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, r.exitCode(), "Script exit, stderr=" + r.stderr());
        return r.stdout().strip();
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

    /** `Gzip.kf` composes `image.Inflate` and reuses `Zip.kf`, so all three trees install. */
    @Override
    public List<String> libraryNames() {
        return List.of("archive", "image");
    }

    @Override
    public List<String> libraryMarkers() {
        return List.of("Gzip.kf");
    }
}
