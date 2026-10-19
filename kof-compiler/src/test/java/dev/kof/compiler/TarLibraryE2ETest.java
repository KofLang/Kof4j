package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #852 (fatia 3) — leitura de TAR (POSIX ustar + a extensão GNU de nome longo)
 * em Kof puro (`libs/archive/Tar.kf`), pela mesma regra de composição das fatias
 * 1 e 2.
 *
 * <p>O oráculo é DUPLO: o fixture é construído byte a byte em Java (independente
 * do leitor Kof e sem dependência externa) e, quando o `tar` do host existe, o
 * MESMO fixture é conferido por ele (`tar -tf`) — o formato é fixo, então as duas
 * implementações têm de concordar. É o que o TAR acrescenta ao par ZIP/GZIP:
 * estrutura sobre compressão, e a composição `tarGzEntries` (gzip → tar) é
 * provada aqui.
 */
class TarLibraryE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String ALFA = "conteudo do alfa\n";          // 17 bytes
    private static final String BETA = "xyz";                          // 3

    // --- fixture: escritor ustar independente (byte a byte) ------------------

    /** Bloco de 512 bytes de um header ustar, com o checksum calculado pela
     *  definição (o próprio campo entrando como espaços). */
    private static byte[] tarHeader(String name, char type, long size, String linkName) {
        byte[] h = new byte[512];
        putStr(h, 0, name, 100);
        putOctal(h, 100, 8, 0644);          // mode
        putOctal(h, 108, 8, 0);             // uid
        putOctal(h, 116, 8, 0);             // gid
        putOctal(h, 124, 12, size);         // size
        putOctal(h, 136, 12, 1700000000L);  // mtime
        for (int i = 148; i < 156; i++) h[i] = ' ';   // chksum lido como espaços
        h[156] = (byte) type;
        if (linkName != null) putStr(h, 157, linkName, 100);
        putStr(h, 257, "ustar", 6);
        h[263] = '0';
        h[264] = '0';
        long sum = 0;
        for (byte b : h) sum += (b & 255);
        putOctal(h, 148, 7, sum);
        h[155] = ' ';
        return h;
    }

    private static void putStr(byte[] to, int at, String s, int len) {
        byte[] raw = s.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(raw, 0, to, at, Math.min(raw.length, len));
    }

    private static void putOctal(byte[] to, int at, int len, long value) {
        String oct = Long.toOctalString(value);
        StringBuilder sb = new StringBuilder();
        for (int i = oct.length(); i < len - 1; i++) sb.append('0');
        sb.append(oct);
        putStr(to, at, sb.toString(), len - 1);
        to[at + len - 1] = 0;
    }

    private static byte[] tarBlock(byte[] data) {
        int pad = (512 - (data.length % 512)) % 512;
        byte[] out = new byte[data.length + pad];
        System.arraycopy(data, 0, out, 0, data.length);
        return out;
    }

    /** ustar com um diretório, dois arquivos e um link — na ordem escrita. */
    private static byte[] buildTar() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tarHeader("dir/", '5', 0, null));
        out.write(tarHeader("dir/alfa.txt", '0', ALFA.length(), null));
        out.write(tarBlock(ALFA.getBytes(StandardCharsets.UTF_8)));
        out.write(tarHeader("dir/beta.txt", '0', BETA.length(), null));
        out.write(tarBlock(BETA.getBytes(StandardCharsets.UTF_8)));
        out.write(tarHeader("dir/link.txt", '2', 0, "alfa.txt"));
        out.write(new byte[1024]);          // fim: dois blocos zero
        return out.toByteArray();
    }

    /** GNU long name: o marcador 'L' carrega o nome, a entrada seguinte o usa. */
    private static byte[] buildLongNameTar(String longName) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] nameBytes = (longName + "\0").getBytes(StandardCharsets.UTF_8);
        out.write(tarHeader("././@LongLink", 'L', nameBytes.length, null));
        out.write(tarBlock(nameBytes));
        out.write(tarHeader(longName, '0', BETA.length(), null));
        out.write(tarBlock(BETA.getBytes(StandardCharsets.UTF_8)));
        out.write(new byte[1024]);
        return out.toByteArray();
    }

    private static byte[] gzip(byte[] raw) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (OutputStream gz = new GZIPOutputStream(out)) {
            gz.write(raw);
        }
        return out.toByteArray();
    }

    // --- programa Kof --------------------------------------------------------

    private static String listingProbe(Path tar) {
        return """
                import archive.Tar

                main() {
                    var es = tarEntries("%s")
                    for (var e in es) {
                        println(e.name + "|" + e.typeByte + "|" + e.size)
                    }
                    var f = es.get(1)
                    println("alfa=" + tarReadEntry("%s", f, 4096).length)
                }
                """.formatted(tar.toAbsolutePath(), tar.toAbsolutePath());
    }

    private static String targzProbe(Path targz) {
        return """
                import archive.Tar

                main() {
                    var es = tarGzEntries("%s", 1048576)
                    println("n=" + es.size() + " primeiro=" + es.get(1).name)
                    var f = es.get(1)
                    println("alfa=" + tarReadEntryFrom(gzipRead("%s", 1048576), f, 4096).length)
                }
                """.formatted(targz.toAbsolutePath(), targz.toAbsolutePath());
    }

    private static String refusalProbe(Path tar, int which, int maxBytes) {
        return """
                import archive.Tar

                main() {
                    try {
                        var es = tarEntries("%s")
                        var e = es.get(%d)
                        println("LEU " + tarReadEntry("%s", e, %d).length)
                    } catch (String m) {
                        println("recusado: " + m)
                    }
                }
                """.formatted(tar.toAbsolutePath(), which, tar.toAbsolutePath(), maxBytes);
    }

    private static String listingOnlyProbe(Path tar) {
        return """
                import archive.Tar

                main() {
                    try {
                        var es = tarEntries("%s")
                        println("listou " + es.size())
                    } catch (String m) {
                        println("recusado: " + m)
                    }
                }
                """.formatted(tar.toAbsolutePath());
    }

    private static final String LISTING = """
            dir/|53|0
            dir/alfa.txt|48|17
            dir/beta.txt|48|3
            dir/link.txt|50|0
            alfa=17
            """.strip();

    // --- provas --------------------------------------------------------------

    @Test
    void listsAndReadsUstarOnJvm() throws Exception {
        Path root = tmp.resolve("ustar");
        Files.createDirectories(root);
        Path tar = root.resolve("lote.tar");
        Files.write(tar, buildTar());
        Files.writeString(root.resolve("Main.kf"), listingProbe(tar));
        assertEquals(LISTING, runTarOnJvm(root), "JVM: listagem ustar + leitura de arquivo");
    }

    @Test
    void listsAndReadsUstarOnScript() throws Exception {
        Path root = tmp.resolve("ustar-script");
        Files.createDirectories(root);
        Path tar = root.resolve("lote.tar");
        Files.write(tar, buildTar());
        Files.writeString(root.resolve("Main.kf"), listingProbe(tar));
        assertEquals(LISTING, runTarOnScript(root), "Script: a MESMA face");
    }

    /** O fixture Java conferido pelo `tar` do host — o formato é fixo, então o
     *  escritor do teste e o leitor Kof têm de concordar com a referência. */
    @Test
    void hostTarAgreesWithTheFixture() throws Exception {
        Path tar = tmp.resolve("lote.tar");
        Files.write(tar, buildTar());
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/usr/bin/tar")) || Files.isExecutable(Path.of("/bin/tar")),
                "host tar required");
        ProcessBuilder pb = new ProcessBuilder("tar", "-tf", tar.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.waitFor(), "tar -tf: " + out);
        List<String> names = new ArrayList<>();
        for (String line : out.split("\n")) {
            if (!line.isBlank()) names.add(line.strip());
        }
        assertEquals(List.of("dir/", "dir/alfa.txt", "dir/beta.txt", "dir/link.txt"), names,
                "o tar do host le exatamente as entradas que o leitor Kof lista");
    }

    /** A composição da família: `.tar.gz` = a fatia GZIP + esta fatia. */
    @Test
    void tarGzIsTheGzipSlicePlusThisOne() throws Exception {
        Path root = tmp.resolve("targz");
        Files.createDirectories(root);
        Path targz = root.resolve("lote.tar.gz");
        Files.write(targz, gzip(buildTar()));
        Files.writeString(root.resolve("Main.kf"), targzProbe(targz));
        String expected = "n=4 primeiro=dir/alfa.txt\nalfa=17";
        assertEquals(expected, runTarOnJvm(root), "tar.gz (JVM)");
        assertEquals(expected, runTarOnScript(root), "tar.gz (Script)");
    }

    /** GNU long name: o marcador 'L' é SEGUIDO e não aparece como entrada. */
    @Test
    void followsGnuLongNames() throws Exception {
        Path root = tmp.resolve("longo");
        Files.createDirectories(root);
        String longName = "n".repeat(120) + ".txt";
        Path tar = root.resolve("longo.tar");
        Files.write(tar, buildLongNameTar(longName));
        Files.writeString(root.resolve("Main.kf"), listingOnlyProbe(tar));
        assertEquals("listou 1", runTarOnJvm(root), "nome longo (JVM)");

        Files.writeString(root.resolve("Main.kf"), """
                import archive.Tar

                main() {
                    var es = tarEntries("%s")
                    println(es.get(0).name.length())
                }
                """.formatted(tar.toAbsolutePath()));
        assertEquals(String.valueOf(longName.length()), runTarOnJvm(root), "o nome REAL tem 124 chars");
    }

    /** Checksum de header, pax, truncado e as recusas de tipo — tudo explícito. */
    @Test
    void refusesCorruptionPaxTruncationAndNonFiles() throws Exception {
        Path root = tmp.resolve("recusas");
        Files.createDirectories(root);
        byte[] good = buildTar();
        Path tar = root.resolve("lote.tar");
        Files.write(tar, good);

        byte[] badSum = good.clone();
        badSum[148] = badSum[148] == '7' ? (byte) '6' : (byte) '7';
        Path badSumPath = root.resolve("badsum.tar");
        Files.write(badSumPath, badSum);

        Path pax = root.resolve("pax.tar");
        ByteArrayOutputStream paxOut = new ByteArrayOutputStream();
        paxOut.write(tarHeader("dir/", 'x', 8, null));
        paxOut.write(tarBlock("28 mtime=1\0".getBytes(StandardCharsets.UTF_8)));
        paxOut.write(tarHeader("dir/alfa.txt", '0', ALFA.length(), null));
        paxOut.write(tarBlock(ALFA.getBytes(StandardCharsets.UTF_8)));
        paxOut.write(new byte[1024]);
        Files.write(pax, paxOut.toByteArray());

        byte[] truncated = tarHeader("dir/grande.bin", '0', 4096, null);
        Path truncatedPath = root.resolve("trunc.tar");
        Files.write(truncatedPath, truncated);

        for (boolean jvm : new boolean[] {true, false}) {
            Files.writeString(root.resolve("Main.kf"), listingOnlyProbe(badSumPath));
            assertEquals("recusado: archive: tar header checksum mismatch at 0",
                    jvm ? runTarOnJvm(root) : runTarOnScript(root), "checksum");

            Files.writeString(root.resolve("Main.kf"), listingOnlyProbe(pax));
            assertEquals("recusado: archive: pax extended tar header ('x') is not supported — refused",
                    jvm ? runTarOnJvm(root) : runTarOnScript(root), "pax");

            Files.writeString(root.resolve("Main.kf"), listingOnlyProbe(truncatedPath));
            assertEquals("recusado: archive: tar entry 'dir/grande.bin' declares 4096 bytes past the end of the archive",
                    jvm ? runTarOnJvm(root) : runTarOnScript(root), "truncado");

            Files.writeString(root.resolve("Main.kf"), refusalProbe(tar, 0, 4096));
            assertEquals("recusado: archive: 'dir/' is a directory, not a file",
                    jvm ? runTarOnJvm(root) : runTarOnScript(root), "diretório");

            Files.writeString(root.resolve("Main.kf"), refusalProbe(tar, 3, 4096));
            assertEquals("recusado: archive: 'dir/link.txt' is a link to 'alfa.txt' — not a file",
                    jvm ? runTarOnJvm(root) : runTarOnScript(root), "link simbólico");

            Files.writeString(root.resolve("Main.kf"), refusalProbe(tar, 1, 4));
            assertEquals("recusado: archive: entry 'dir/alfa.txt' holds 17 bytes, over the limit of 4",
                    jvm ? runTarOnJvm(root) : runTarOnScript(root), "teto de bytes");
        }
    }

    // --- execução ------------------------------------------------------------

    private String runTarOnJvm(Path root) throws Exception {
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

    private String runTarOnScript(Path root) throws Exception {
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

    /** `Tar.kf` composes `Gzip.kf` (and through it `image.Inflate`), so all trees install. */
    @Override
    public List<String> libraryNames() {
        return List.of("archive", "image");
    }

    @Override
    public List<String> libraryMarkers() {
        return List.of("Tar.kf");
    }
}
