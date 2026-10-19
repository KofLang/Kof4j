package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.nio.charset.Charset;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #852 (fatia 1) — a biblioteca de ZIP importável (`libs/archive/Zip.kf`,
 * `package archive`), em Kof puro sobre `kof.io` e COMPONDO o
 * `libs/image/Inflate.kf` que já existia (o DEFLATE é reaproveitado, não
 * reescrito).
 *
 * <p>O oráculo é o `java.util.zip` do host: o fixture é escrito com um
 * `ZipOutputStream` (uma entrada STORED e uma DEFLATED) e o Kof lê
 * nomes/tamanhos/conteúdo — JVM e Script. As faces de RECUSA (ZIP64,
 * criptografia, método fora de {0,8}, tamanho declarado mentiroso, CRC
 * corrompido) são asserções do TEXTO do diagnóstico, e o teto de bytes é
 * exercitado nos dois lados.
 */
class ZipLibraryE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    /** 220 bytes ASCII de conteúdo fácil de conferir olho nu. */
    private static final String BIG_TEXT = "deflate me ".repeat(20);
    private static final String STORED_TEXT = "hello stored";

    // --- oráculo do host -----------------------------------------------------

    /** ZIP com uma entrada STORED (`alfa.txt`) e uma DEFLATED (`dir/normal.txt`). */
    private static Path buildFixture(Path dir, String name) throws Exception {
        Path zip = dir.resolve(name);
        try (OutputStream raw = Files.newOutputStream(zip); ZipOutputStream out = new ZipOutputStream(raw)) {
            byte[] stored = STORED_TEXT.getBytes(StandardCharsets.UTF_8);
            ZipEntry e1 = new ZipEntry("alfa.txt");
            e1.setMethod(ZipEntry.STORED);
            e1.setSize(stored.length);
            e1.setCompressedSize(stored.length);
            e1.setCrc(ArchiveFixtureSupport.crc32(stored));
            out.putNextEntry(e1);
            out.write(stored);
            out.closeEntry();
            out.putNextEntry(new ZipEntry("dir/normal.txt"));   // default = DEFLATED
            out.write(BIG_TEXT.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return zip;
    }

    /** ZIP mínimo com entradas STORED de mesmo conteúdo e os nomes dados — escrito
     *  byte a byte porque um `ZipOutputStream` do JDK NORMALIZA/recusa os nomes
     *  maliciosos, e é exatamente deles que o confinamento precisa. */
    private static byte[] zipWithNames(List<String> names, byte[] data) {
        java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
        List<Integer> offsets = new java.util.ArrayList<>();
        for (String name : names) {
            offsets.add(body.size());
            byte[] nm = name.getBytes(StandardCharsets.UTF_8);
            byte[] local = new byte[30 + nm.length];
            ArchiveFixtureSupport.putU32(local, 0, ArchiveFixtureSupport.LOCAL);
            ArchiveFixtureSupport.putU16(local, 4, 20);
            ArchiveFixtureSupport.putU16(local, 6, 0);
            ArchiveFixtureSupport.putU16(local, 8, 0);                 // método 0 = STORED
            ArchiveFixtureSupport.putU32(local, 14, ArchiveFixtureSupport.crc32(data));
            ArchiveFixtureSupport.putU32(local, 18, data.length);
            ArchiveFixtureSupport.putU32(local, 22, data.length);
            ArchiveFixtureSupport.putU16(local, 26, nm.length);
            System.arraycopy(nm, 0, local, 30, nm.length);
            body.write(local, 0, local.length);
            body.write(data, 0, data.length);
        }
        int cdOffset = body.size();
        for (int i = 0; i < names.size(); i++) {
            byte[] nm = names.get(i).getBytes(StandardCharsets.UTF_8);
            byte[] cd = new byte[46 + nm.length];
            ArchiveFixtureSupport.putU32(cd, 0, ArchiveFixtureSupport.CENTRAL);
            ArchiveFixtureSupport.putU16(cd, 4, 20);
            ArchiveFixtureSupport.putU16(cd, 6, 20);
            ArchiveFixtureSupport.putU16(cd, 10, 0);
            ArchiveFixtureSupport.putU32(cd, 16, ArchiveFixtureSupport.crc32(data));
            ArchiveFixtureSupport.putU32(cd, 20, data.length);
            ArchiveFixtureSupport.putU32(cd, 24, data.length);
            ArchiveFixtureSupport.putU16(cd, 28, nm.length);
            ArchiveFixtureSupport.putU32(cd, 42, offsets.get(i));
            System.arraycopy(nm, 0, cd, 46, nm.length);
            body.write(cd, 0, cd.length);
        }
        int cdSize = body.size() - cdOffset;
        byte[] eocd = new byte[22];
        ArchiveFixtureSupport.putU32(eocd, 0, ArchiveFixtureSupport.EOCD);
        ArchiveFixtureSupport.putU16(eocd, 8, names.size());
        ArchiveFixtureSupport.putU16(eocd, 10, names.size());
        ArchiveFixtureSupport.putU32(eocd, 12, cdSize);
        ArchiveFixtureSupport.putU32(eocd, 16, cdOffset);
        body.write(eocd, 0, eocd.length);
        return body.toByteArray();
    }

    /**
     * `extractTo` — o CONFINAMENTO que a issue pede: recusa `..`, absoluto,
     * unidade Windows, separador `\` e NUNCA sobrescreve. O ZIP é construído
     * byte a byte porque o `ZipOutputStream` do JDK normaliza/recusa esses nomes.
     */
    @Test
    void extractToConfinesNamesAndNeverOverwrites(@TempDir Path dir) throws Exception {
        byte[] payload = "malicioso".getBytes(StandardCharsets.UTF_8);
        Path clean = dir.resolve("clean.zip");
        Files.write(clean, zipWithNames(List.of("ok.txt", "sub/dir/nota.txt"), payload));
        Path traversal = dir.resolve("trav.zip");
        Files.write(traversal, zipWithNames(List.of("../escape.txt"), payload));
        Path absolute = dir.resolve("abs.zip");
        Files.write(absolute, zipWithNames(List.of("/abs.txt"), payload));
        Path drive = dir.resolve("drive.zip");
        Files.write(drive, zipWithNames(List.of("C:/win.txt"), payload));
        Path backslash = dir.resolve("bs.zip");
        Files.write(backslash, zipWithNames(List.of("dir\\evil.txt"), payload));

        Path out = dir.resolve("out");
        Files.createDirectories(out);
        Files.writeString(out.resolve("ok.txt"), "preservado");

        Files.writeString(dir.resolve("Main.kf"), """
                import archive.Zip

                void face(String path, String dest) {
                    try {
                        var z = Zip(path)
                        println("ESCREVEU " + z.extractTo(dest, 4096))
                    } catch (String m) {
                        println("recusado: " + m)
                    }
                }

                main() {
                    face("%s", "%s")
                    face("%s", "%s")
                    face("%s", "%s")
                    face("%s", "%s")
                    face("%s", "%s")
                }
                """.formatted(
                        traversal.toAbsolutePath(), out.toAbsolutePath(),
                        absolute.toAbsolutePath(), out.toAbsolutePath(),
                        drive.toAbsolutePath(), out.toAbsolutePath(),
                        backslash.toAbsolutePath(), out.toAbsolutePath(),
                        clean.toAbsolutePath(), out.toAbsolutePath()));

        String expected = "recusado: archive: refusing entry '../escape.txt' — it escapes the destination\n"
                + "recusado: archive: refusing entry '/abs.txt' — it escapes the destination\n"
                + "recusado: archive: refusing entry 'C:/win.txt' — it escapes the destination\n"
                + "recusado: archive: refusing entry 'dir\\evil.txt' — it escapes the destination\n"
                + "recusado: archive: refusing to overwrite 'ok.txt' — it already exists";
        assertEquals(expected, runOnJvm(dir), "recusas de confinamento (JVM)");
        assertEquals(expected, runOnScript(dir), "recusas de confinamento (Script)");
        assertEquals("preservado", Files.readString(out.resolve("ok.txt")), "o pré-existente NÃO foi tocado");
        assertFalse(Files.exists(dir.resolve("escape.txt")), "nada pode escapar do destino");
        assertFalse(Files.exists(Path.of("/abs.txt")), "nada pode ser escrito no absoluto");
    }

    // --- programa Kof --------------------------------------------------------

    private static String listingProbe(Path zip) {
        return """
                import archive.Zip

                main() {
                    var z = Zip("%s")
                    for (var e in z.entries) {
                        println(e.name + "|" + e.method + "|" + e.compressedSize + "|" + e.uncompressedSize)
                    }
                    var a = z.find("alfa.txt")
                    if (a != null) {
                        println("stored=" + z.readText(a, 4096))
                    }
                    var n = z.find("dir/normal.txt")
                    if (n != null) {
                        println("deflate=" + n.uncompressedSize + ":" + z.readText(n, 4096).length)
                    }
                    println("ausente=" + (z.find("nao-existe.kf") == null))
                }
                """.formatted(zip.toAbsolutePath());
    }

    private static String refusalProbe(Path zip, String entry, int maxBytes) {
        return """
                import archive.Zip

                main() {
                    try {
                        var z = Zip("%s")
                        var e = z.find("%s")
                        if (e == null) {
                            println("SEM ENTRADA")
                        } else {
                            println("LEU " + z.readEntry(e, %d).length)
                        }
                    } catch (String m) {
                        println("recusado: " + m)
                    }
                }
                """.formatted(zip.toAbsolutePath(), entry, maxBytes);
    }

    private static final String LISTING = """
            alfa.txt|0|12|12
            dir/normal.txt|8|16|220
            stored=hello stored
            deflate=220:220
            ausente=true
            """.strip();

    @Test
    void listsAndReadsBothEntriesOnJvm() throws Exception {
        Path root = tmp.resolve("jvm");
        Files.createDirectories(root);
        Path zip = buildFixture(root, "lote.zip");
        Files.writeString(root.resolve("Main.kf"), listingProbe(zip));
        assertEquals(LISTING, runOnJvm(root), "JVM: listagem + STORED + DEFLATED (oraculo java.util.zip)");
    }

    @Test
    void listsAndReadsBothEntriesOnScript() throws Exception {
        Path root = tmp.resolve("script");
        Files.createDirectories(root);
        Path zip = buildFixture(root, "lote.zip");
        Files.writeString(root.resolve("Main.kf"), listingProbe(zip));
        assertEquals(LISTING, runOnScript(root), "Script: a MESMA face");
    }

    /** O teto de bytes é do CHAMADOR e vale para STORED e para DEFLATED. */
    @Test
    void byteCeilingRefusesBothMethodsOnJvmAndScript() throws Exception {
        Path root = tmp.resolve("cap");
        Files.createDirectories(root);
        Path zip = buildFixture(root, "lote.zip");

        Files.writeString(root.resolve("Main.kf"), refusalProbe(zip, "alfa.txt", 4));
        String stored = "recusado: archive: entry 'alfa.txt' holds 12 bytes, over the limit of 4";
        assertEquals(stored, runOnJvm(root), "STORED sob o teto (JVM)");
        assertEquals(stored, runOnScript(root), "STORED sob o teto (Script)");

        Files.writeString(root.resolve("Main.kf"), refusalProbe(zip, "dir/normal.txt", 10));
        String deflated = "recusado: archive: entry 'dir/normal.txt' declares 220 bytes, over the limit of 10";
        assertEquals(deflated, runOnJvm(root), "DEFLATED sob o teto (JVM)");
        assertEquals(deflated, runOnScript(root), "DEFLATED sob o teto (Script)");
    }

    /** ZIP64, criptografia e método fora de {0,8}: diagnóstico EXPLÍCITO, nunca silêncio. */
    @Test
    void refusesZip64EncryptionAndForeignMethods() throws Exception {
        Path root = tmp.resolve("refusals");
        Files.createDirectories(root);
        Path zip = buildFixture(root, "lote.zip");

        // Both patches target the FIRST entry (`alfa.txt`): the central directory
        // holds one record per entry, so `find(CENTRAL, 0)` is `alfa.txt` and
        // `rfind(CENTRAL)` is the LAST one (`dir/normal.txt`) — patching by hand
        // is exactly where a wrong offset reads as a false green.
        int firstCentral = ArchiveFixtureSupport.find(ArchiveFixtureSupport.read(zip), ArchiveFixtureSupport.CENTRAL, 0);

        byte[] method9 = ArchiveFixtureSupport.read(zip);
        ArchiveFixtureSupport.putU16(method9, ArchiveFixtureSupport.find(method9, ArchiveFixtureSupport.LOCAL, 0) + 8, 9);   // local header do alfa.txt
        ArchiveFixtureSupport.putU16(method9, firstCentral + 10, 9);             // diretório central do alfa.txt
        Path method9Path = root.resolve("method9.zip");
        Files.write(method9Path, method9);

        byte[] encrypted = ArchiveFixtureSupport.read(zip);
        ArchiveFixtureSupport.putU16(encrypted, firstCentral + 8, 1);
        Path encryptedPath = root.resolve("enc.zip");
        Files.write(encryptedPath, encrypted);

        byte[] zip64 = ArchiveFixtureSupport.read(zip);
        ArchiveFixtureSupport.putU16(zip64, ArchiveFixtureSupport.rfind(zip64, ArchiveFixtureSupport.EOCD) + 10, 0xFFFF);
        Path zip64Path = root.resolve("z64.zip");
        Files.write(zip64Path, zip64);

        for (boolean jvm : new boolean[] {true, false}) {
            Files.writeString(root.resolve("Main.kf"), refusalProbe(zip64Path, "alfa.txt", 4096));
            String z64 = "recusado: archive: ZIP64 sentinel in the end of central directory — ZIP64 is not supported";
            assertEquals(z64, jvm ? runOnJvm(root) : runOnScript(root), "ZIP64");

            Files.writeString(root.resolve("Main.kf"), refusalProbe(encryptedPath, "alfa.txt", 4096));
            String enc = "recusado: archive: entry 'alfa.txt' is encrypted (flag bit 0) — not supported";
            assertEquals(enc, jvm ? runOnJvm(root) : runOnScript(root), "criptografia");

            Files.writeString(root.resolve("Main.kf"), refusalProbe(method9Path, "alfa.txt", 4096));
            String m9 = "recusado: archive: entry 'alfa.txt' uses compression method 9 "
                    + "(only 0 = stored and 8 = deflate are supported)";
            assertEquals(m9, jvm ? runOnJvm(root) : runOnScript(root), "método 9");
        }
    }

    /**
     * A DEFESA DE BOMBA na face mais dura: o diretório central DECLARA 10 bytes e
     * o fluxo infla 220. O teto do chamador (4096) não barra nada aqui — quem barra
     * é o `got != declared`, e a recusa é explícita em vez de truncada em silêncio.
     */
    @Test
    void refusesAnArchiveThatLiesAboutItsDeclaredSize() throws Exception {
        Path root = tmp.resolve("lying");
        Files.createDirectories(root);
        byte[] lying = ArchiveFixtureSupport.read(buildFixture(root, "lote.zip"));
        // A mentira vai na entrada DEFLATED (`dir/normal.txt`, o ÚLTIMO registro do
        // diretório central): ela infla 220 bytes e passa a declarar 10. O teto do
        // chamador (4096) não barra nada — quem barra é o `got != declared`.
        ArchiveFixtureSupport.putU32(lying, ArchiveFixtureSupport.rfind(lying, ArchiveFixtureSupport.CENTRAL) + 24, 10);
        Path lyingPath = root.resolve("lying.zip");
        Files.write(lyingPath, lying);

        String expected = "recusado: archive: entry 'dir/normal.txt' did not inflate to its declared 10 bytes — refused";
        Files.writeString(root.resolve("Main.kf"), refusalProbe(lyingPath, "dir/normal.txt", 4096));
        assertEquals(expected, runOnJvm(root), "mentira sobre o tamanho (JVM)");
        assertEquals(expected, runOnScript(root), "mentira sobre o tamanho (Script) — o texto é o mesmo");
    }

    /** CRC-32 conferido de verdade: um byte trocado no payload tem de ser recusado. */
    @Test
    void refusesACorruptedPayloadOnCrc32() throws Exception {
        Path root = tmp.resolve("crc");
        Files.createDirectories(root);
        byte[] corrupted = ArchiveFixtureSupport.read(buildFixture(root, "lote.zip"));
        int payloadAt = ArchiveFixtureSupport.find(corrupted, ArchiveFixtureSupport.LOCAL, 0) + 30 + "alfa.txt".length();
        corrupted[payloadAt] = (byte) 'X';
        Path corruptedPath = root.resolve("bad.zip");
        Files.write(corruptedPath, corrupted);

        Files.writeString(root.resolve("Main.kf"), refusalProbe(corruptedPath, "alfa.txt", 4096));
        String out = runOnJvm(root);
        assertTrue(out.startsWith("recusado: archive: CRC-32 mismatch for 'alfa.txt'"),
                "CRC tem de pegar o byte trocado, veio: " + out);
    }

    /**
     * Nomes seguem a REGRA DO FORMATO (APPNOTE 6.3.x): o bit 11 (EFS) decide.
     * Com ele LIMPO o nome é CP437 — o caso dos arquivos legados, que antes saíam
     * com caracteres de substituição — e com ele SET o nome é UTF-8. O oráculo do
     * CP437 é o charset IBM437 do PRÓPRIO JDK (implementação independente da minha
     * tabela), e o de UTF-8 é o próprio `ZipOutputStream` padrão.
     */
    @Test
    void decodesLegacyCp437NamesAndKeepsTheUtf8FlagRule(@TempDir Path dir) throws Exception {
        String accented = "caf\u00e9.txt";                       // "café.txt"

        Path legacy = dir.resolve("legacy.zip");
        try (OutputStream raw = Files.newOutputStream(legacy);
             ZipOutputStream out = new ZipOutputStream(raw, Charset.forName("IBM437"))) {
            byte[] data = "legado".getBytes(StandardCharsets.UTF_8);
            ZipEntry e = new ZipEntry(accented);
            e.setMethod(ZipEntry.STORED);
            e.setSize(data.length);
            e.setCompressedSize(data.length);
            e.setCrc(ArchiveFixtureSupport.crc32(data));
            out.putNextEntry(e);
            out.write(data);
            out.closeEntry();
        }

        Path modern = dir.resolve("modern.zip");
        try (OutputStream raw = Files.newOutputStream(modern); ZipOutputStream out = new ZipOutputStream(raw)) {
            out.putNextEntry(new ZipEntry(accented));             // default = UTF-8
            out.write("moderno".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }

        Path main = dir.resolve("Main.kf");
        Files.writeString(main, """
                import archive.Zip

                main() {
                    var l = Zip("%s")
                    var m = Zip("%s")
                    println("legacy=" + l.entries.get(0).name + "|efs=" + ((l.entries.get(0).flags & 2048) != 0))
                    println("modern=" + m.entries.get(0).name + "|efs=" + ((m.entries.get(0).flags & 2048) != 0))
                }
                """.formatted(legacy.toAbsolutePath(), modern.toAbsolutePath()));

        String expected = "legacy=" + accented + "|efs=false\nmodern=" + accented + "|efs=true";
        assertEquals(expected, runOnJvm(dir), "CP437 legado + UTF-8 sinalizado (JVM)");
        assertEquals(expected, runOnScript(dir), "CP437 legado + UTF-8 sinalizado (Script)");
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

    /** `Zip.kf` composes `image.Inflate`, so BOTH trees are installed. */
    @Override
    public List<String> libraryNames() {
        return List.of("archive", "image");
    }

    @Override
    public List<String> libraryMarkers() {
        return List.of("Zip.kf");
    }
}
