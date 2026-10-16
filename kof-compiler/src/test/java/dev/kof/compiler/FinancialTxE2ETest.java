package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * `kof.financial` slice 4 — the TRANSACTION lifecycle state machine (`libs/financial/Tx.kf`).
 * Exact minor-unit money: currency-safe arithmetic (a mismatch is an ERROR,
 * never a silent conversion), overflow-checked, integer-only rounding (6
 * modes, halfEven among them), exact rational scaling. Pure Kof over existing
 * primitives (record + Long + String + enum) — no new syntax, no core change.
 */
class FinancialTxE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "financial"; }

    @Override public List<String> libraryMarkers() { return List.of("Money.kf", "Tx.kf"); }

    private static final String PROBE = """
            import financial.Money
            import financial.Tx

            main() {
                var t = Transaction("tx-1", "idem-1", Money(1000L, "BRL"), TxState.Created, 1000L, 1000L, "")
                var auth = authorize(t, "prov-1", 1100L)
                println(auth.state)
                var cap = capture(auth, Money(700L, "BRL"), "prov-1", 1200L)
                println(cap.state)
                println(cap.amount.minor)
                var st = settle(cap, 1300L)
                println(st.state)
                var rf = refund(st, Money(300L, "BRL"), 1400L)
                println(rf.state)
                println(rf.amount.minor)
                try {
                    authorize(auth, "prov-1", 1500L)
                    println("no-throw")
                } catch (String e) {
                    println(e)
                }
                try {
                    capture(auth, Money(2000L, "BRL"), "prov-1", 1600L)
                    println("no-throw")
                } catch (String e) {
                    println(e)
                }
            }
            """;

    // 150+250=400 -> R$ 4.00 ; 250-150=100 -> R$ 1.00 ; negate 150 -> R$ -1.50 ;
    // roundTo(155, 1, halfEven) -> 160 -> R$ 1.60 ; scale(150, 3/2) = 225 -> R$ 2.25 ;
    // JPY has 0 minor units: Money(0,"JPY") -> "JPY 0" ; roundTo(12345, 0) -> "JPY 12345"
    private static final String EXPECTED = "Authorized\nCaptured\n700\nSettled\nRefunded\n300\ninvalid transition: Authorized -> Authorized (FIN005)\nover-capture: 2000 > 1000 (FIN006)";

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
    void txLifecycleOnJvm() throws Exception {
        Path root = tmp.resolve("money-jvm");
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
    void txLifecycleOnScript() throws Exception {
        Path root = tmp.resolve("money-script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE);
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script: " + result.stdout() + result.stderr());
        assertEquals(EXPECTED, result.stdout().strip());
    }

    @Test
    void txLifecycleOnJs() throws Exception {
        Path root = tmp.resolve("money-js");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE);
        Path out = root.resolve("out");
        CompilationResult r = withLibrary(root, () -> driver.compile(root.resolve("Main.kf"), out, Target.JS));
        assertTrue(r.success(), () -> "JS compile: " + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(TestJdk.onPath("node"), out.resolve("Default.mjs").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String got = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, p.waitFor(), "JS exit: " + got);
        assertEquals(EXPECTED, got);
    }

    @Test
    void txLifecycleOnNativeX86() throws Exception {
        Assumptions.assumeTrue(dev.kof.compiler.nat.NativeToolchainGate.present(), "native toolchain absent");
        Path root = tmp.resolve("money-native");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE);
        Path out = root.resolve("out");
        CompilationResult r = withLibrary(root, () -> driver.compile(root.resolve("Main.kf"), out, Target.NATIVE));
        assertTrue(r.success(), () -> "native compile: " + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(out.resolve("Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String got = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, p.waitFor(), "native exit: " + got);
        assertEquals(EXPECTED, got);
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
