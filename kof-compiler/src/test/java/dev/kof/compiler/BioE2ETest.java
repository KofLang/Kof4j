package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Universal platform Stage 7.1 slice — the FASTA/FASTQ READERS
 * ({@code libs/bio/Bio.kf}): typed {@code FastaRecord(header, sequence)} /
 * {@code FastqRecord(header, sequence, quality)} parsed from text, plus the
 * classic {@code gcContent} computing face and honest refusals (BIO001 an
 * empty FASTA sequence, BIO002 a FASTQ quality-length mismatch). Pure Kof
 * over measured idioms — VCF (tabular) and BAM (binary FFI) stay recorded as
 * follow-ups in the plan.
 */
class BioE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "bio"; }

    @Override public List<String> libraryMarkers() { return List.of("Bio.kf"); }

    private static final String PROBE = """
            import bio.Bio

            main() {
                var fasta = ">seq1 human\\nATGC\\nGATT\\n>seq2 mouse\\nGGCC\\n"
                var recs = readFasta(fasta)
                println(recs.size())
                var r1 = recs.get(0)
                println(r1.header)
                println(r1.sequence)
                println(gcContent(r1.sequence))
                var fq = "@r1\\nATGC\\n+\\nIII!\\n"
                var fqs = readFastq(fq)
                println(fqs.size())
                println(fqs.get(0).sequence)
                println(fqs.get(0).quality)
                var broken = "@r1\\nATGC\\n+\\nIII\\n"
                try {
                    readFastq(broken)
                } catch (String e) {
                    println(e.contains("BIO002"))
                }
                var empty = ">s1\\n>s2\\nAA\\n"
                try {
                    readFasta(empty)
                } catch (String e) {
                    println(e.contains("BIO001"))
                }
            }
            """;

    // 2 FASTA records (seq1 = "ATGCGATT", gc = 3/8 = 0.375) ; 1 FASTQ record
    // with its quality line ; a quality-length mismatch refuses BIO002 ; a
    // header with no sequence refuses BIO001.
    private static final String EXPECTED = "2\nseq1 human\nATGCGATT\n0.375\n1\nATGC\nIII!\ntrue\ntrue";

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

    @Test
    void bioOnJvm() throws Exception {
        Path root = tmp.resolve("bio-jvm");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE);
        Path out = root.resolve("out");
        CompilationResult r = withLibrary(root, () -> driver.compile(root.resolve("Main.kf"), out, Target.JVM));
        assertTrue(r.success(), () -> "compile: " + r.diagnostics().getDiagnostics());
        var stdout = new ByteArrayOutputStream();
        var previousOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(stdout, true, StandardCharsets.UTF_8));
            try (var loader = new java.net.URLClassLoader(
                    new java.net.URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
                Class.forName("Default.Main", true, loader)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            }
        } finally {
            System.setOut(previousOut);
        }
        assertEquals(EXPECTED, stdout.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void bioOnScript() throws Exception {
        Path root = tmp.resolve("bio-script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE);
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script: " + result.stdout() + result.stderr());
        assertEquals(EXPECTED, result.stdout().strip());
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
