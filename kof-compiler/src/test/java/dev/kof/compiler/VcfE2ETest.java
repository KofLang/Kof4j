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
 * Universal platform Stage 7.1 follow-up — the VCF READER
 * ({@code libs/bio/Vcf.kf}): typed {@code VcfRecord(chrom, pos, id, ref,
 * alt, qual)} parsed from the 8-column rows, `##` meta lines and the
 * `#CHROM` header skipped, a missing QUAL (`.`) is {@code Double?} null,
 * fewer than 8 columns refuses BIO003. Pure Kof — the tab comes from the
 * char-code idiom (no `\t` escape assumed).
 */
class VcfE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "bio"; }

    @Override public List<String> libraryMarkers() { return List.of("Bio.kf", "Vcf.kf"); }

    private static final String PROBE = """
            import bio.Bio
            import bio.Vcf

            main() {
                var tab = "\t"
                var vcf = "##fileformat=VCFv4.2\\n##INFO=<ID=DP>\\n#CHROM POS ID REF ALT QUAL FILTER INFO\\n"
                    + "chr1" + tab + "100" + tab + "rs1" + tab + "A" + tab + "G" + tab + "29.5" + tab + "PASS" + tab + "DP=5\\n"
                    + "chr2" + tab + "200" + tab + "." + tab + "T" + tab + "C" + tab + "." + tab + "PASS" + tab + ".\\n"
                var recs = readVcf(vcf)
                println(recs.size())
                println(recs.get(0).chrom)
                println(recs.get(0).pos)
                println(recs.get(0).qual)
                var r2 = recs.get(1)
                println(r2.qual == null)
                var bad = "##fileformat=VCFv4.2\\n" + "chr1" + tab + "100" + tab + "rs1" + tab + "A" + tab + "G" + tab + "29.5\\n"
                try {
                    readVcf(bad)
                } catch (String e) {
                    println(e.contains("BIO003"))
                }
            }
            """;

    // 2 data rows survive the meta/header skipping; the first carries its
    // chrom/pos/qual; the second's QUAL "." is null (never a fabricated 0);
    // a 6-column row refuses BIO003.
    private static final String EXPECTED = "2\nchr1\n100\n29.5\ntrue\ntrue";

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
    void vcfOnJvm() throws Exception {
        Path root = tmp.resolve("vcf-jvm");
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
    void vcfOnScript() throws Exception {
        Path root = tmp.resolve("vcf-script");
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
