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
 * `kof.financial` slice 3 — the DOUBLE-ENTRY ledger (`libs/financial/Ledger.kf`).
 * Exact minor-unit money: currency-safe arithmetic (a mismatch is an ERROR,
 * never a silent conversion), overflow-checked, integer-only rounding (6
 * modes, halfEven among them), exact rational scaling. Pure Kof over existing
 * primitives (record + Long + String + enum) — no new syntax, no core change.
 */
class FinancialLedgerE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "financial"; }

    @Override public List<String> libraryMarkers() { return List.of("Money.kf", "Ledger.kf", "Recon.kf", "Compliance.kf", "Jurisdiction.kf"); }

    private static final String PROBE = """
            import financial.Money
            import financial.Ledger
import financial.Recon
import financial.Compliance
import financial.Jurisdiction

            main() {
                var cash = Account("cash-1", "store-1", "BRL", "asset")
                var rev = Account("rev-1", "store-1", "BRL", "revenue")
                var entries = listOf(
                    Entry("cash-1", Direction.debit, 1000L, "BRL"),
                    Entry("rev-1", Direction.credit, 1000L, "BRL"))
                var s0 = LedgerStore(listOf())
                var s1 = post(s0, entries, "sale-1", "j-1", 1000L)
                println(s1.journals.size())
                println(isBalanced(s1.journals.get(0)))
                println(postedBalance("cash-1", s1))
                println(postedBalance("rev-1", s1))
                var bal = Balance("cash-1", postedBalance("cash-1", s1), 0L)
                println(availableBalance(bal))
                var held = applyHold(bal, Hold("cash-1", 300L, "BRL", 2000L))
                println(availableBalance(held))
                var bad = listOf(
                    Entry("cash-1", Direction.debit, 100L, "BRL"),
                    Entry("rev-1", Direction.credit, 90L, "BRL"))
                try {
                    post(s1, bad, "sale-2", "j-2", 2000L)
                    println("no-throw")
                } catch (String e) {
                    println(e)
                }
                var e2 = listOf(
                    Entry("cash-1", Direction.debit, 995L, "BRL"),
                    Entry("rev-1", Direction.credit, 995L, "BRL"))
                var s2store = post(s1, e2, "sale-3", "j-2", 1800L)
                var stmt = listOf(
                    StatementLine("s-1", 1000L, "BRL", 1500L, "dep 1000"),
                    StatementLine("s-2", 990L, "BRL", 1600L, "dep 990 (9 off)"),
                    StatementLine("s-3", 500L, "USD", 1700L, "usd line"))
                var ms = reconcile(s2store.journals, stmt, 10)
                println(ms.size)
                println(ms.get(0).kind)
                println(ms.get(1).kind)
                println(ms.get(2).kind)
                var br = JurisdictionProfile("BR", 500000L, listOf("customer", "merchant", "fee"))
                var c1 = checkTxAmount(br, Money(1000L, "BRL"))
                var c2 = checkTxAmount(br, Money(900000L, "BRL"))
                var c3 = checkAccountKind(br, "customer")
                var c4 = checkAccountKind(br, "casino")
                println(c1.passed)
                println(c2.passed)
                println(c3.passed)
                println(c4.passed)
                var checks = listOf(c1, c2, c3, c4)
                println(allPassed(checks))
                println(failures(checks).size)
                println(failures(checks).get(0).detail)
                var jt = defaultJurisdictions()
                var brRow = jurisdictionOf(jt, "BR")
                if (brRow != null) {
                    println(brRow.currency)
                    println(brRow.maxMinorPerTx)
                    println(toProfile(brRow).code)
                }
                println(currencyFor(jt, "US"))
                try {
                    currencyFor(jt, "XX")
                    println("no-throw")
                } catch (String e) {
                    println(e)
                }
            }
            """;

    // 150+250=400 -> R$ 4.00 ; 250-150=100 -> R$ 1.00 ; negate 150 -> R$ -1.50 ;
    // roundTo(155, 1, halfEven) -> 160 -> R$ 1.60 ; scale(150, 3/2) = 225 -> R$ 2.25 ;
    // JPY has 0 minor units: Money(0,"JPY") -> "JPY 0" ; roundTo(12345, 0) -> "JPY 12345"
    private static final String EXPECTED = "1\ntrue\n1000\n-1000\n1000\n700\nunbalanced journal (FIN004)\n3\nexact\ntolerance\nunmatched\ntrue\nfalse\ntrue\nfalse\nfalse\n2\n900000 exceeds the per-tx cap of 500000 (BR)\nBRL\n500000\nBR\nUSD\nunknown jurisdiction: XX (FIN008)";

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
    void ledgerCoreOnJvm() throws Exception {
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
    void ledgerCoreOnScript() throws Exception {
        Path root = tmp.resolve("money-script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE);
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script: " + result.stdout() + result.stderr());
        assertEquals(EXPECTED, result.stdout().strip());
    }

    @Test
    void ledgerCoreOnJs() throws Exception {
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
    void ledgerCoreOnNativeX86() throws Exception {
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
