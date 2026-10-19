package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #852 — `Zip.validate()`: a **validação estrutural** que a issue pede no escopo
 * ("consistência entre cabeçalho local e diretório central, sobreposição de dados
 * entre entradas"). São dois vetores que a LEITURA sozinha não pega, porque
 * nenhum deles quebra o formato — quebram a CONFIANÇA entre as duas cópias dos
 * dados que um ZIP carrega.
 *
 * <p>Os fixtures são cirurgias de bytes (um `ZipOutputStream` do JDK não produz
 * essas mentiras), com o cuidado de cada caso: o `data descriptor` (flag bit 3)
 * é a mentira LEGÍTIMA da especificação e o teste exige que ela NÃO seja recusada
 * — um validador que recusa o legítimo é tão inútil quanto um que aceita o falso.
 */
class ZipValidateE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final byte[] PAYLOAD = "alfa".getBytes(StandardCharsets.UTF_8);

    /** ZIP mínimo de uma entrada STORED (`a.txt`), para servir de base à cirurgia. */
    private static byte[] baseZip() {
        byte[] nm = "a.txt".getBytes(StandardCharsets.UTF_8);
        byte[] local = new byte[30 + nm.length];
        ArchiveFixtureSupport.putU32(local, 0, ArchiveFixtureSupport.LOCAL);
        ArchiveFixtureSupport.putU16(local, 4, 20);
        ArchiveFixtureSupport.putU32(local, 14, ArchiveFixtureSupport.crc32(PAYLOAD));
        ArchiveFixtureSupport.putU32(local, 18, PAYLOAD.length);
        ArchiveFixtureSupport.putU32(local, 22, PAYLOAD.length);
        ArchiveFixtureSupport.putU16(local, 26, nm.length);
        System.arraycopy(nm, 0, local, 30, nm.length);

        byte[] cd = new byte[46 + nm.length];
        ArchiveFixtureSupport.putU32(cd, 0, ArchiveFixtureSupport.CENTRAL);
        ArchiveFixtureSupport.putU16(cd, 4, 20);
        ArchiveFixtureSupport.putU16(cd, 6, 20);
        ArchiveFixtureSupport.putU32(cd, 16, ArchiveFixtureSupport.crc32(PAYLOAD));
        ArchiveFixtureSupport.putU32(cd, 20, PAYLOAD.length);
        ArchiveFixtureSupport.putU32(cd, 24, PAYLOAD.length);
        ArchiveFixtureSupport.putU16(cd, 28, nm.length);
        ArchiveFixtureSupport.putU32(cd, 42, 0);                 // o header local está em 0
        System.arraycopy(nm, 0, cd, 46, nm.length);

        int cdOffset = local.length + PAYLOAD.length;
        byte[] eocd = new byte[22];
        ArchiveFixtureSupport.putU32(eocd, 0, ArchiveFixtureSupport.EOCD);
        ArchiveFixtureSupport.putU16(eocd, 8, 1);
        ArchiveFixtureSupport.putU16(eocd, 10, 1);
        ArchiveFixtureSupport.putU32(eocd, 12, cd.length);
        ArchiveFixtureSupport.putU32(eocd, 16, cdOffset);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(local);
        out.writeBytes(PAYLOAD);
        out.writeBytes(cd);
        out.writeBytes(eocd);
        return out.toByteArray();
    }

    /** O MESMO registo do diretório central duplicado (mesmo nome, mesmo header
     *  local) → duas entradas com a MESMA faixa de dados. */
    private static byte[] withDuplicatedCentralEntry(byte[] zip) {
        int cdOffset = (int) readU32(zip, zip.length - 22 + 16);
        int cdSize = (int) readU32(zip, zip.length - 22 + 12);
        byte[] rec = java.util.Arrays.copyOfRange(zip, cdOffset, cdOffset + cdSize);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(zip, 0, cdOffset);
        out.writeBytes(rec);
        out.write(zip, cdOffset, zip.length - cdOffset);
        byte[] merged = out.toByteArray();
        int eocd = merged.length - 22;
        ArchiveFixtureSupport.putU16(merged, eocd + 10, 2);
        ArchiveFixtureSupport.putU32(merged, eocd + 12, cdSize * 2);
        return merged;
    }

    private static long readU32(byte[] b, int at) {
        return (b[at] & 255L) | ((b[at + 1] & 255L) << 8) | ((b[at + 2] & 255L) << 16) | ((b[at + 3] & 255L) << 24);
    }

    private static String probe(Path dir) {
        return """
                import archive.Zip

                void tentaExtrair(String path) {
                    try {
                        var z = Zip(path)
                        println("ok=" + z.validate())
                    } catch (String m) {
                        println("recusado: " + m)
                    }
                }

                main() {
                    tentaExtrair("%s/ok.zip")
                    tentaExtrair("%s/nome.zip")
                    tentaExtrair("%s/tam.zip")
                    tentaExtrair("%s/crc.zip")
                    tentaExtrair("%s/overlap.zip")
                    tentaExtrair("%s/desc.zip")
                    try {
                        var z = Zip("%s/nome.zip")
                        println("extraiu=" + z.extractTo("%s/saida", 4096))
                    } catch (String m) {
                        println("extractTo recusou: " + m)
                    }
                }
                """.formatted(dir, dir, dir, dir, dir, dir, dir, dir);
    }

    @Test
    void detectsTheStructuralLiesAndToleratesTheLegitDescriptor(@TempDir Path dir) throws Exception {
        byte[] ok = baseZip();
        Files.write(dir.resolve("ok.zip"), ok);

        byte[] name = ok.clone();                       // nome diferente no header local
        name[30] = 'X';
        Files.write(dir.resolve("nome.zip"), name);

        byte[] size = ok.clone();                       // tamanho diferente no header local
        ArchiveFixtureSupport.putU32(size, 22, 999);
        Files.write(dir.resolve("tam.zip"), size);

        byte[] crc = ok.clone();                        // CRC diferente no header local
        ArchiveFixtureSupport.putU32(crc, 14, 12345);
        Files.write(dir.resolve("crc.zip"), crc);

        Files.write(dir.resolve("overlap.zip"), withDuplicatedCentralEntry(ok));

        byte[] desc = ok.clone();                       // data descriptor LEGÍTIMO (bit 3)
        ArchiveFixtureSupport.putU16(desc, 6, 8);
        ArchiveFixtureSupport.putU32(desc, 14, 0);
        ArchiveFixtureSupport.putU32(desc, 18, 0);
        ArchiveFixtureSupport.putU32(desc, 22, 0);
        int cd = ArchiveFixtureSupport.find(desc, ArchiveFixtureSupport.CENTRAL, 0);
        ArchiveFixtureSupport.putU16(desc, cd + 8, 8);
        Files.write(dir.resolve("desc.zip"), desc);

        Files.writeString(dir.resolve("Main.kf"), probe(dir));

        String expected = """
                ok=1
                recusado: archive: entry 'a.txt' is named 'X.txt' in the local header — inconsistent with the central directory
                recusado: archive: entry 'a.txt' sizes differ between the local header (4/999) and the central directory (4/4)
                recusado: archive: entry 'a.txt' CRC-32 differs between the local header (12345) and the central directory (%s)
                recusado: archive: entries overlap in the file — the data at 35 is inside another entry's range (refused)
                ok=1
                extractTo recusou: archive: entry 'a.txt' is named 'X.txt' in the local header — inconsistent with the central directory
                """.formatted((int) ArchiveFixtureSupport.crc32(PAYLOAD)).strip();
        assertEquals(expected, runOnJvm(dir), "validação estrutural (JVM)");
        assertEquals(expected, runOnScript(dir), "validação estrutural (Script)");
        assertTrue(Files.notExists(dir.resolve("saida/a.txt")),
                "o extractTo recusa ANTES de escrever: nada pode aparecer no destino");
    }

    // --- execução ------------------------------------------------------------

    private String runOnJvm(Path root) throws Exception {
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

    private String runOnScript(Path root) throws Exception {
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

    @Override
    public List<String> libraryNames() {
        return List.of("archive", "image");
    }

    @Override
    public List<String> libraryMarkers() {
        return List.of("Zip.kf");
    }
}
