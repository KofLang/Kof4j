package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §5 — integration harness: the database-connection lifecycle helper
 * {@code withDb(url, body)}, written in Kof (no new syntax/primitive,
 * D-KOF-FIRST item 12) and authorized by D-MAINT-BATCH-0610B/B
 * ("temp dir / server / db lifecycle with try/finally cleanup; Kof library,
 * injected flat on import kof.test").
 *
 * <p>The helper lives in a SEPARATE virtual host {@code kof.test.db} (not in
 * {@code kof.test}) — the {@code CompilerWeb}-vs-{@code kof.pagination}
 * precedent. The reason is measured: {@code withDb} calls
 * {@code db.connect}/{@code db.close}, and the cross native links libsqlite3 by
 * use ({@code NativeCrossLink.needsSqlite} scans the pruned asm for
 * {@code call sqlite3_*}); since {@code kof.test} is flat-injected whole, a db
 * helper living there would make EVERY {@code import kof.test} program link
 * {@code -lsqlite3} on the cross (observed:
 * {@code riscv64-linux-gnu-ld: cannot find -lsqlite3}). The separate opt-in
 * import means only a program that imports {@code kof.test.db} pays the gap.
 *
 * <p>{@code withDb} opens {@code db.connect(url)}, runs the body and closes
 * the connection in a {@code finally} — both the success and the throw path.
 * The close is the symmetric pair of the open; the helper exists so an
 * integration test never leaves a database connection behind.
 *
 * <p><b>Observable of the close.</b> Kof has no {@code isClosed}, so the
 * cleanup is proven indirectly: an H2 in-memory database lives only while at
 * least one connection is open, so reconnecting after the helper must find the
 * database empty (the connection was closed); leaking the connection would
 * keep the table. The same program proves the throw path (the table is gone
 * after the body throws, i.e. the finally ran). The native sqlite file does
 * not expose a comparable close observable, so its legs pin the body + the
 * throw-path continuation with the data persisted.
 *
 * <p>Parity: JVM + JS + Native x86-64. The cross legs are not added because
 * the native sqlite link is host-dependent and the close has no cross
 * observable; the helper itself compiles on all targets (it is flat-injected
 * with the rest of {@code kof.test}).
 */
class DbLifecycleE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static String jvmProgram() {
        return """
                import kof.test.db

                main() {
                    withDb("jdbc:h2:mem:lifecycle", (h: String) -> {
                        db.execute(h, "create table t(id int)")
                        db.execute(h, "insert into t values (?)", 7)
                        println("inside=" + db.query(h, "select count(*) as n from t").get(0))
                    })
                    println("after=" + tableCount("jdbc:h2:mem:lifecycle"))

                    var threw = false
                    try {
                        withDb("jdbc:h2:mem:lifecycle-throw", (h: String) -> {
                            db.execute(h, "create table t(id int)")
                            throw "boom"
                        })
                    } catch (Exception e) {
                        threw = true
                    }
                    println("threw=" + threw)
                    println("afterThrow=" + tableCount("jdbc:h2:mem:lifecycle-throw"))
                }

                String tableCount(String url) {
                    var c = db.connect(url)
                    var rows = db.query(c, "select count(*) as n from information_schema.tables where table_name='T'")
                    db.close(c)
                    return rows.get(0)
                }
                """;
    }

    // The JVM/JS oracle: the body sees its own write; the H2 in-memory database
    // is EMPTY after the helper (the connection was closed) on both the success
    // and the throw path.
    private static final String GOLDEN = """
            inside={"n":1}
            after={"n":0}
            threw=true
            afterThrow={"n":0}""";

    private static String nativeProgram(Path dbFile) {
        String p = dbFile.toString().replace('\\', '/');
        return """
                import kof.test.db

                main() {
                    withDb("sqlite:%s", (h: String) -> {
                        db.execute(h, "create table t(id int)")
                        db.execute(h, "insert into t values (?)", 7)
                        println("inside=" + db.query(h, "select count(*) as n from t").get(0))
                    })

                    var threw = false
                    try {
                        withDb("sqlite:%s", (h: String) -> {
                            throw "boom"
                        })
                    } catch (Exception e) {
                        threw = true
                    }
                    println("threw=" + threw)
                    println("after=" + db.query(db.connect("sqlite:%s"), "select count(*) as n from t"))
                }
                """.formatted(p, p, p);
    }

    private record Run(int exitCode, String output) {}

    private Run runJvm(Path source, Path outDir) throws IOException, InterruptedException {
        CompilationResult r = driver.compileForTests(source, outDir, Target.JVM);
        assertTrue(r.success(), "JVM compile: " + r.diagnostics().getDiagnostics());
        String h2 = "";
        for (String entry : System.getProperty("java.class.path").split(java.io.File.pathSeparator)) {
            if (entry.contains("h2") && entry.endsWith(".jar")) h2 = entry;
        }
        assertTrue(!h2.isEmpty(), "H2 jar must be on the test classpath");
        Process p = new ProcessBuilder(TestJdk.javaBin(), "-cp",
                outDir + java.io.File.pathSeparator + h2, "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        return new Run(p.waitFor(), out);
    }

    private Run runJs(Path source, Path outDir) throws IOException {
        CompilationResult r = driver.compile(source, outDir, Target.JS);
        assertTrue(r.success(), "JS compile: " + r.diagnostics().getDiagnostics());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                java.io.InputStream.nullInputStream(), java.io.OutputStream.nullOutputStream(),
                false, new String[0]);
        return new Run(ec, out.toString(java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim());
    }

    private Run runNative(Path source, Path outDir) throws IOException, InterruptedException {
        CompilationResult r = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(r.success(), "native compile: " + r.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binary should exist");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        return new Run(p.waitFor(), out);
    }

    @Test
    void dbLifecycleOnJvm(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("D.kf");
        Files.writeString(src, jvmProgram());
        Run r = runJvm(src, tempDir.resolve("out"));
        assertEquals(0, r.exitCode(), () -> "output: " + r.output());
        assertEquals(GOLDEN, r.output(), "JVM oracle for the db lifecycle");
    }

    @Test
    void dbLifecycleMatchesJvmOnJs(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("D.kf");
        Files.writeString(src, jvmProgram());
        Run jvm = runJvm(src, tempDir.resolve("out-jvm"));
        Run js = runJs(src, tempDir.resolve("out-js"));
        assertEquals(jvm.exitCode(), js.exitCode(), () -> "js: " + js.output());
        assertEquals(jvm.output(), js.output(), "JS must match the JVM oracle (rule 5)");
    }

    @Test
    void dbLifecycleOnNativeX86(@TempDir Path tempDir) throws Exception {
        Assumptions.assumeTrue(isLinux(), "Native sqlite requires Linux + libsqlite3");
        Path dbFile = tempDir.resolve("lifecycle.db");
        Path src = tempDir.resolve("D.kf");
        Files.writeString(src, nativeProgram(dbFile));
        Run r = runNative(src, tempDir.resolve("out"));
        assertEquals(0, r.exitCode(), () -> "output: " + r.output());
        assertEquals("""
                inside={"n":1}
                threw=true
                after=[{"n":1}]""", r.output(), "native body + throw-path continuation");
    }

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("linux");
    }
}
