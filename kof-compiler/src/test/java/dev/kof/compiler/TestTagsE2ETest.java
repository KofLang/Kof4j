package dev.kof.compiler;

import dev.kof.compiler.Target;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * X8 fatia 3 ({@code D-COMPLETE-FIRST}, 26/09): tags no primitivo
 * {@code test "nome", "tag"... { }}, filtro {@code kof.test.tag} decidido em
 * compile-time (mesmo catálogo p/ todos os alvos — rule 5 por construção) e
 * fixtures {@code setup}/{@code teardown} como funções comuns (setup que falha
 * = SKIP nomeado; teardown por {@code finally} inclusive em teste que falha).
 *
 * <p>Goldens MEDIDOS no harness (nenhum valor de memória): forma antiga sem
 * tags mantém a saída byte-idêntica (rule 2 — aditivo puro).</p>
 */
class TestTagsE2ETest {

    @TempDir Path tmp;

    private record Run(boolean success, String diags, String output) {}

    private void clearTag() {
        System.clearProperty("kof.test.tag");
    }

    private Run compileAndRunJvm(String code) throws Exception {
        return compileAndRunJvm(code, null);
    }

    private Run compileAndRunJvm(String code, String filterTag) throws Exception {
        clearTag();
        if (filterTag != null) System.setProperty("kof.test.tag", filterTag);
        try {
            Files.writeString(tmp.resolve("T" + System.nanoTime() + ".kf"), code);
            Path src = Files.list(tmp).filter(p -> p.toString().endsWith(".kf"))
                    .findFirst().orElseThrow();
            Path out = Files.createTempDirectory(tmp, "o");
            CompilerDriver driver = new CompilerDriver();
            CompilationResult r = driver.compileForTests(src, out, Target.JVM);
            StringBuilder diags = new StringBuilder();
            r.diagnostics().getDiagnostics().forEach(d -> diags.append(d.code()).append(' ')
                    .append(d.message()).append('\n'));
            if (!r.success()) return new Run(false, diags.toString(), "");
            // harness roda em SUBPROCESSO (padrão StructuredTestE2ETest): o
            // process.exit(1) de teste que falha não pode matar o surefire.
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", out.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String output = new String(proc.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n");
            int ec = proc.waitFor();
            return new Run(ec == 0, diags.toString() + (ec == 0 ? "" : "exit=" + ec + "\n"), output);
        } finally {
            clearTag();
        }
    }

    private static final String TAGGED = """
            main() { println("user main should NOT run") }
            test "soma simples", "smoke" {
                assert(2 + 2 == 4)
            }
            test "strings" {
                assert("kof" == "kof")
            }
            """;

    @Test
    void unfilteredRunsAllAndKeepsLegacyOutputShape() throws Exception {
        Run r = compileAndRunJvm(TAGGED);
        assertTrue(r.success(), "must compile: " + r.diags());
        assertFalse(r.output().contains("user main"), "harness still replaces main");
        assertEquals("PASS soma simples\nPASS strings\n────────\n0 failed of 2 tests",
                r.output().trim(), "sem filtro = contrato antigo, byte a byte");
    }

    @Test
    void filterKeepsOnlyTaggedWithHeaderLine() throws Exception {
        Run r = compileAndRunJvm(TAGGED, "smoke");
        assertTrue(r.success(), "must compile: " + r.diags());
        assertEquals("kof test: tag 'smoke' (1 of 2)\nPASS soma simples\n────────\n"
                + "0 failed of 1 tests", r.output().trim(),
                "filtro em compile-time: header + catálogo reduzido");
    }

    @Test
    void zeroKeptRefusesHonestlyAndPasses() throws Exception {
        Run r = compileAndRunJvm(TAGGED, "ui");
        assertTrue(r.success(), "must compile: " + r.diags());
        assertEquals("kof test: tag 'ui' (0 of 2)\nno tests with tag 'ui' (of 2)",
                r.output().trim(), "R6: nada silencioso quando a tag não casa");
    }

    @Test
    void emptyTagIsRejectedAtParseTime() throws Exception {
        Run r = compileAndRunJvm("""
                test "a", "" {
                    assert(true)
                }
                """);
        assertFalse(r.success(), "empty tag must not compile");
        assertTrue(r.diags().contains("PARSE010") && r.diags().contains("must not be empty"),
                "expected PARSE010 naming the empty tag, was: " + r.diags());
    }

    @Test
    void trailingCommaWithoutStringIsRejected() throws Exception {
        Run r = compileAndRunJvm("""
                test "a", {
                    assert(true)
                }
                """);
        assertFalse(r.success(), "dangling comma must not compile");
        assertTrue(r.diags().contains("PARSE010"),
                "expected PARSE010 tag-after-comma, was: " + r.diags());
    }

    @Test
    void setupFailureSkipsItsTestNamed() throws Exception {
        Run r = compileAndRunJvm("""
            void setup() {
                throw "db down"
            }
            test "precisa de db" {
                assert(1 == 1)
            }
            """);
        assertTrue(r.success(), "must compile: " + r.diags());
        assertEquals("SKIP precisa de db: setup failed: db down\n────────\n0 failed of 1 tests",
                r.output().trim(), "setup que falha = SKIP nomeado, não FAIL");
    }

    @Test
    void teardownRunsEvenWhenTestFails() throws Exception {
        Run r = compileAndRunJvm("""
            void teardown() {
                println("teardown-executed")
            }
            test "quebra" {
                assert(1 == 2, "de propósito")
            }
            """);
        assertTrue(!r.diags().contains("exit=2"), "must COMPILE (exit=2 means load error): "
                + r.diags());
                assertTrue(r.output().contains("FAIL quebra: de propósito"),
                "failing test reports FAIL: " + r.output());
        assertTrue(r.output().contains("teardown-executed"),
                "teardown must run via finally even on failure: " + r.output());
        assertTrue(r.diags().contains("exit=1"), "failing suite must exit 1: " + r.diags());
    }

    @Test
    void setupOkThenTeardownRunsAfter() throws Exception {
        Run r = compileAndRunJvm("""
            void setup() {
                println("up")
            }
            void teardown() {
                println("down")
            }
            test "vida" {
                assert(true)
            }
            """);
        assertTrue(r.success(), "must compile: " + r.diags());
        assertEquals("up\nPASS vida\ndown\n────────\n0 failed of 1 tests", r.output().trim(),
                "ordem setup -> teste -> teardown");
    }

    @Test
    void beforeAllRunsOnceBeforeAnyTest() throws Exception {
        Run r = compileAndRunJvm("""
            void beforeAll() {
                println("before-all")
            }
            test "a" {
                println("test-a")
            }
            test "b" {
                println("test-b")
            }
            """);
        assertTrue(r.success(), "must compile: " + r.diags());
        assertEquals("before-all\ntest-a\nPASS a\ntest-b\nPASS b\n────────\n0 failed of 2 tests",
                r.output().trim(), "beforeAll runs once before the first test");
    }

    @Test
    void afterAllRunsOnceAfterAllTests() throws Exception {
        Run r = compileAndRunJvm("""
            void afterAll() {
                println("after-all")
            }
            test "a" {
                println("test-a")
            }
            test "b" {
                println("test-b")
            }
            """);
        assertTrue(r.success(), "must compile: " + r.diags());
        assertEquals("test-a\nPASS a\ntest-b\nPASS b\nafter-all\n────────\n0 failed of 2 tests",
                r.output().trim(), "afterAll runs once after the last test");
    }

    @Test
    void beforeEachAliasRunsBeforeEveryTest() throws Exception {
        Run r = compileAndRunJvm("""
            void beforeEach() {
                println("before-each")
            }
            test "a" {
                println("test-a")
            }
            test "b" {
                println("test-b")
            }
            """);
        assertTrue(r.success(), "must compile: " + r.diags());
        assertEquals("before-each\ntest-a\nPASS a\nbefore-each\ntest-b\nPASS b\n────────\n0 failed of 2 tests",
                r.output().trim(), "beforeEach is the §4.2 alias of setup (runs per test)");
    }

    @Test
    void afterEachAliasRunsAfterEveryTest() throws Exception {
        Run r = compileAndRunJvm("""
            void afterEach() {
                println("after-each")
            }
            test "a" {
                println("test-a")
            }
            test "b" {
                println("test-b")
            }
            """);
        assertTrue(r.success(), "must compile: " + r.diags());
        assertEquals("test-a\nPASS a\nafter-each\ntest-b\nPASS b\nafter-each\n────────\n0 failed of 2 tests",
                r.output().trim(), "afterEach is the §4.2 alias of teardown (runs per test)");
    }

    @Test
    void formatterKeepsTagsIdempotently() throws Exception {
        String src = "test \"a\", \"smoke\", \"ui\" {\n    assert(true)\n}\n";
        String once = KofFormatter.format(src, "T.kf");
        String twice = KofFormatter.format(once, "T.kf");
        assertTrue(once.contains("test \"a\", \"smoke\", \"ui\" {"),
                "fmt must preserve tags, was: " + once);
        assertEquals(once, twice, "fmt idempotent with tags");
    }

    @Test
    void jsTargetHonorsTheSameCompiledFilter() throws Exception {
        clearTag();
        System.setProperty("kof.test.tag", "smoke");
        try {
            Path src = tmp.resolve("Js" + System.nanoTime() + ".kf");
            Files.writeString(src, TAGGED);
            Path out = Files.createTempDirectory(tmp, "ojs");
            CompilerDriver driver = new CompilerDriver();
            CompilationResult r = driver.compileForTests(src, out, Target.JS);
            assertTrue(r.success(), "JS must compile: " + r.diagnostics().getDiagnostics());
            var buf = new ByteArrayOutputStream();
            int ec = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), buf,
                    new ByteArrayInputStream(new byte[0]), buf);
            assertEquals(0, ec, "js exit");
            assertEquals("kof test: tag 'smoke' (1 of 2)\nPASS soma simples\n────────\n"
                    + "0 failed of 1 tests", buf.toString().trim(),
                    "JS executa o MESMO catálogo filtrado do JVM (rule 5)");
        } finally {
            clearTag();
        }
    }

    @Test
    void beforeAllFailureSkipsTestsAndExitsNonZero() throws Exception {
        Run r = compileAndRunJvm("""
            void beforeAll() {
                throw "db connection failed"
            }
            test "a" {
                println("should-not-run-a")
            }
            test "b" {
                println("should-not-run-b")
            }
            """);
        assertFalse(r.success(), "must fail when beforeAll throws: " + r.diags());
        assertTrue(r.diags().contains("exit=1"), "must exit with code 1");
        assertFalse(r.output().contains("should-not-run-a"), "test a must NOT run when beforeAll fails");
        assertFalse(r.output().contains("should-not-run-b"), "test b must NOT run when beforeAll fails");
        assertTrue(r.output().contains("beforeAll failed: db connection failed"), "must report beforeAll failure");
        assertTrue(r.output().contains("SKIP a: beforeAll failed"), "test a must be marked SKIP");
        assertTrue(r.output().contains("SKIP b: beforeAll failed"), "test b must be marked SKIP");
        assertTrue(r.output().contains("1 failed of 2 tests"), "must report 1 failed of 2 tests");
        assertFalse(r.output().contains("0 failed of 2 tests"), "must NOT report 0 failed");
    }

    @Test
    void afterAllFailureRecordsFailureAndPrintsSummary() throws Exception {
        Run r = compileAndRunJvm("""
            void afterAll() {
                throw "cleanup failed"
            }
            test "a" {
                println("test-a")
            }
            """);
        assertFalse(r.success(), "must fail when afterAll throws: " + r.diags());
        assertTrue(r.diags().contains("exit=1"), "must exit with code 1");
        assertTrue(r.output().contains("test-a"), "test a must run before afterAll");
        assertTrue(r.output().contains("PASS a"), "test a passes");
        assertTrue(r.output().contains("afterAll failed: cleanup failed"), "must report afterAll failure");
        assertTrue(r.output().contains("1 failed of 1 tests"), "must count afterAll as failed");
    }
}
