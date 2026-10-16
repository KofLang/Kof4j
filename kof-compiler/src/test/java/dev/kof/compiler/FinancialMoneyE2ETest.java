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
 * `kof.financial` slice 1 — the MONEY core (`libs/financial/Money.kf`).
 * Exact minor-unit money: currency-safe arithmetic (a mismatch is an ERROR,
 * never a silent conversion), overflow-checked, integer-only rounding (6
 * modes, halfEven among them), exact rational scaling. Pure Kof over existing
 * primitives (record + Long + String + enum) — no new syntax, no core change.
 */
class FinancialMoneyE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "financial"; }

    @Override public List<String> libraryMarkers() { return List.of("Money.kf", "Fees.kf", "Events.kf", "Idempotency.kf"); }

    private static final String PROBE = """
            import financial.Money
import financial.Fees
import financial.Events
import financial.Idempotency
import financial.Fx

            main() {
                var a = Money(150L, "BRL")
                var b = Money(250L, "BRL")
                println(format(add(a, b)))
                println(format(sub(b, a)))
                println(format(negate(a)))
                println(format(roundTo(Money(155L, "BRL"), 1, Rounding.halfEven)))
                println(format(scale(a, 3L, 2L, Rounding.halfUp)))
                println(format(Money(0L, "JPY")))
                println(format(roundTo(Money(12345L, "JPY"), 0, Rounding.halfEven)))
                try {
                    println(add(Money(1L, "BRL"), Money(1L, "USD")))
                } catch (String e) {
                    println(e)
                }
                var rules = listOf(
                    FeeRule("fixed-1", "fixed", 50L, 0L, "BRL"),
                    FeeRule("pct-1", "percentage", 0L, 250L, "BRL"))
                var fees = applyFees(Money(1000L, "BRL"), rules, Rounding.halfUp)
                println(fees.get(0).amount.minor)
                println(fees.get(1).amount.minor)
                println(format(totalWithFees(Money(1000L, "BRL"), fees)))
                println(format(netOfFees(Money(1000L, "BRL"), fees)))
                var tiered = listOf(FeeRule("t-1", "tiered", 0L, 0L, "BRL"))
                try {
                    applyFees(Money(1000L, "BRL"), tiered, Rounding.halfUp)
                    println("no-throw")
                } catch (String e) {
                    println(e)
                }
                var box = Outbox(listOf())
                var o1 = publish(box, Event("e-1", "payment.created", "tx-1", "{}", 1000L, "c-1"))
                var o2 = publish(o1, Event("e-2", "payment.captured", "tx-1", "{}", 1100L, "c-1"))
                println(pending(o2).size)
                var o3 = markDispatched(o2, "e-1")
                println(pending(o3).size)
                println(pending(o3).get(0).id)
                var s = IdempotencyStore(listOf())
                var s1 = remember(s, IdempotencyRecord("k-1", "hash-a", "{}", 1000L))
                println(isReplay(s1, "k-1", "hash-a"))
                try {
                    remember(s1, IdempotencyRecord("k-1", "hash-b", "{}", 1100L))
                    println("no-throw")
                } catch (String e) {
                    println(e)
                }
                var usd = convert(Money(200L, "BRL"), Rate("BRL", "USD", 1L, 5L, 1000L, "test"), Rounding.halfUp)
                println(format(usd))
                var brl = convert(usd, invert(Rate("BRL", "USD", 1L, 5L, 1000L, "test")), Rounding.halfUp)
                println(format(brl))
                var eur = convert(Money(100L, "BRL"), compose(Rate("BRL", "USD", 1L, 5L, 1000L, "t1"), Rate("USD", "EUR", 3L, 2L, 900L, "t2")), Rounding.halfUp)
                println(format(eur))
            }
            """;

    // 150+250=400 -> R$ 4.00 ; 250-150=100 -> R$ 1.00 ; negate 150 -> R$ -1.50 ;
    // roundTo(155, 1, halfEven) -> 160 -> R$ 1.60 ; scale(150, 3/2) = 225 -> R$ 2.25 ;
    // JPY has 0 minor units: Money(0,"JPY") -> "JPY 0" ; roundTo(12345, 0) -> "JPY 12345"
    private static final String EXPECTED = "R$ 4.00\nR$ 1.00\nR$ -1.50\nR$ 1.60\nR$ 2.25\nJPY 0\nJPY 12345\ncurrency mismatch: BRL + USD (FIN001)\n50\n25\nR$ 10.75\nR$ 9.25\nunsupported fee kind: tiered (FIN007)\n2\n1\ne-2\ntrue\nidempotency conflict: key k-1 reused with a different request (FIN005)\n$ 0.40\nR$ 2.00\nEUR 0.30";

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
    void moneyCoreOnJvm() throws Exception {
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
    void moneyCoreOnScript() throws Exception {
        Path root = tmp.resolve("money-script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE);
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script: " + result.stdout() + result.stderr());
        assertEquals(EXPECTED, result.stdout().strip());
    }

    @Test
    void moneyCoreOnJs() throws Exception {
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
    void moneyCoreOnNativeX86() throws Exception {
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
