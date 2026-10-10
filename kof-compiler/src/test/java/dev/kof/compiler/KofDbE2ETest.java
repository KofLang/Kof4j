package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;


/**
 * Fase 5 — kof.db: JDBC por interoperabilidade JVM, API idiomática Kof
 * (db.connect/execute/query/query&lt;T&gt;/close + transaction { }).
 *
 * Os testes usam H2 em memória (dependência test-scope); o subprocesso roda
 * com o jar do H2 no classpath.
 */
class KofDbE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase().contains("linux");
    }

    private String runJvm(Path source, Path outDir, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        String h2 = findH2Jar();
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir + java.io.File.pathSeparator + h2, "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running JVM class", e);
        }
    }

    /** #603: um Path entra em LITERAL de string Kof com `/` (SQLite/JDBC
     *  aceitam `/` no Windows); o path cru com `\` vira `\U`/`\j` (escapes
     *  colapsam pela regra lexical — o arquivo some e o SQLite dá CANTOPEN). */
    private static String kofPath(Path p) {
        return p.toString().replace('\\', '/');
    }

    private static String findH2Jar() {
        String cp = System.getProperty("java.class.path");
        for (String entry : cp.split(java.io.File.pathSeparator)) {
            if (entry.contains("h2") && entry.endsWith(".jar")) return entry;
        }
        throw new IllegalStateException("H2 jar not found on test classpath");
    }

    // DB001 (16/09): o KofJS roda no host GraalJS (KofJsRunner), que vive na
    // MESMA JVM do teste e ve o h2/sqlite do java.class.path pelo DriverManager
    // (o proprio findH2Jar le esse classpath). O delegate `kof_platform.db*`
    // faz o roundtrip JDBC; a saida deve bater com o caminho JVM byte-a-byte.
    private String runJs(Path source, Path outDir, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "JS compilation should succeed: "
                + result.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        String output = out.toString(java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, ec, "JS exit code should be 0, output: '" + output + "'");
        assertEquals(expected, output, "Unexpected JS output");
        return output;
    }

    private String runNative(Path source, Path outDir, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Native compilation should succeed: " + result.diagnostics().getDiagnostics());
        Path binFile = outDir.resolve("Default/Main");
        assertTrue(Files.exists(binFile), "Binary should exist");
        try {
            ProcessBuilder pb = new ProcessBuilder(binFile.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Native exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected native output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running native binary", e);
        }
    }

    @Test
    void executeAndQueryRowsAsJson(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("jdbc:h2:mem:test1;DB_CLOSE_DELAY=-1")
                db.execute(db, "create table users(id int, name varchar(50))")
                db.execute(db, "insert into users values (?, ?)", 1, "Mel")
                db.execute(db, "insert into users values (?, ?)", 2, "Kof")
                var rows = db.query(db, "select * from users order by id")
                println(rows.size)
                println(rows.get(0))
                println(rows.get(1))
            }
            """);
        runJvm(source, tempDir.resolve("out"),
                "2\n{\"id\":1,\"name\":\"Mel\"}\n{\"id\":2,\"name\":\"Kof\"}");
    }

    // S2/db-parity (23/09): URL JDBC cujo driver NAO esta no classpath dava um
    // `SQLException: No suitable driver` cru. O connect deve NOMEAR o gap DB001
    // (R6), sem engolir falhas reais de conexao (teste irmão abaixo).
    @Test
    void jvmMissingJdbcDriverNamesGapNotSilent(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("jdbc:oracle:thin:@127.0.0.1:1521/XE")
                println("connected")
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "JVM compile should succeed: " + result.diagnostics().getDiagnostics());
        String output = runJvmExpectFailure(tempDir.resolve("out"), null);
        assertTrue(output.contains("DB001"), "Recusa deve NOMEAR o gap DB001 (R6), veio: " + output);
        assertTrue(output.contains("driver"), "Diagnostico deve citar o driver ausente: " + output);
        assertFalse(output.contains("No suitable driver"), "Nao vazar a mensagem crua do JDBC: " + output);
    }

    // Edge Q3 (falha real preservada): com o driver mariadb no classpath mas o
    // servidor fora, a mensagem original ("Connection refused"/timeout) deve
    // passar INTACTA — o rotulo DB001 e so para driver ausente, nunca engole
    // erro de conexao legitimo.
    @Test
    void jvmRealConnectionFailureIsNotRelabeledDb001(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("jdbc:mariadb://127.0.0.1:1/kof_none")
                println("connected")
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "JVM compile should succeed: " + result.diagnostics().getDiagnostics());
        String output = runJvmExpectFailure(tempDir.resolve("out"), findClasspathJar("mariadb"));
        assertFalse(output.contains("DB001"),
                "Falha real de conexao NAO pode virar DB001 (driver existe): " + output);
    }

    // S3/db-parity (23/09): `mongodb://` sem o driver mongo no classpath do
    // programa deve nomear DB001 (R6) em vez de um ClassNotFoundException cru.
    // (O classpath do teste TEM o driver; aqui rodamos com cp=outDir apenas.)
    @Test
    void jvmMongodbMissingDriverNamesGap(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("mongodb://localhost:27017/kof_gap")
                println("connected")
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "JVM compile should succeed: " + result.diagnostics().getDiagnostics());
        String output = runJvmExpectFailure(tempDir.resolve("out"), null);
        assertTrue(output.contains("DB001"), "Deve NOMEAR o gap DB001 (R6), veio: " + output);
        assertTrue(output.contains("mongodb"), "Diagnostico deve citar mongodb, veio: " + output);
        assertFalse(output.contains("ClassNotFoundException"),
                "Nao vazar ClassNotFoundException cru: " + output);
    }

    private String runJvmExpectFailure(Path outDir, String extraJar) throws IOException {        try {
            String cp = outDir.toString();
            if (extraJar != null) cp = cp + java.io.File.pathSeparator + extraJar;
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", cp, "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertNotEquals(0, ec, "URL sem driver / servidor fora nao pode 'conectar': " + output);
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running JVM class", e);
        }
    }

    /** Roda a JVM com um jar extra no classpath e afirma a saida exata. */
    private String runJvmWithExtra(Path source, Path outDir, String extraJar, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile should succeed: " + result.diagnostics().getDiagnostics());
        String cp = outDir.toString() + java.io.File.pathSeparator + extraJar;
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", cp, "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running JVM class", e);
        }
    }

    private static String findClasspathJar(String needle) {        String cp = System.getProperty("java.class.path");
        for (String entry : cp.split(java.io.File.pathSeparator)) {
            if (entry.toLowerCase().contains(needle) && entry.endsWith(".jar")) return entry;
        }
        throw new IllegalStateException(needle + " jar not found on test classpath");
    }

    @Test
    void typedQueryBindsRecord(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            record User(Int id, String name)

            main() {
                var db = db.connect("jdbc:h2:mem:test2;DB_CLOSE_DELAY=-1")
                db.execute(db, "create table users(id int, name varchar(50))")
                db.execute(db, "insert into users values (?, ?)", 7, "Ada")
                var users = db.query<User>(db, "select * from users where id = ?", 7)
                println(users.size)
                println(users.get(0).id)
                println(users.get(0).name)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "1\n7\nAda");
    }

    @Test
    void typedQueryAllRows(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            record User(Int id, String name)

            main() {
                var db = db.connect("jdbc:h2:mem:test3;DB_CLOSE_DELAY=-1")
                db.execute(db, "create table users(id int, name varchar(50))")
                db.execute(db, "insert into users values (1, 'A')")
                db.execute(db, "insert into users values (2, 'B')")
                var users = db.query<User>(db, "select * from users order by id")
                var total = 0
                for (var u in users) {
                    total = total + u.id
                }
                println(total)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "3");
    }

    @Test
    void typedQueryBindsIntColumnToBoolField(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            record Flag(Bool ok)

            main() {
                var db = db.connect("jdbc:h2:mem:s396;DB_CLOSE_DELAY=-1")
                db.execute(db, "create table t(ok int)")
                db.execute(db, "insert into t values (1)")
                db.execute(db, "insert into t values (0)")
                db.execute(db, "insert into t values (2)")
                var rows = db.query<Flag>(db, "select * from t")
                for (var r in rows) {
                    println(r.ok)
                }
            }
            """);
        runJvm(source, tempDir.resolve("out"), "true\nfalse\ntrue");
    }

    @Test
    void transactionCommits(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("jdbc:h2:mem:test4;DB_CLOSE_DELAY=-1")
                db.execute(db, "create table t(x int)")
                transaction {
                    db.execute(db, "insert into t values (1)")
                    db.execute(db, "insert into t values (2)")
                }
                var rows = db.query(db, "select count(*) as n from t")
                println(rows.get(0))
            }
            """);
        runJvm(source, tempDir.resolve("out"), "{\"n\":2}");
    }

    @Test
    void androidDbEmitsTheSameBytecodeAsJvm(@TempDir Path tempDir) throws IOException {
        // D-DB-GAPS DB-2 (20/09, §278): "android é JVM" nao e lema — e o
        // bytecode. ANDROID reusa o JvmBackend; o pin abaixo trava a
        // paridade por construcao (mesmo .class nos dois alvos) para kof.db
        // e kof.orm, os namespaces cuja over-gating o DB-2 levantou.
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                entity User {
                    id: Long generated
                    name: String
                }
                main() {
                    var db = db.connect("sqlite:/tmp/android-parity.db")
                    orm.create<User>(db)
                    println(orm.count<User>(db))
                }
                """);
        CompilationResult jvm = driver.compile(source, tempDir.resolve("jvm"), Target.JVM);
        assertTrue(jvm.success(), "JVM baseline: " + jvm.diagnostics().getDiagnostics());
        CompilationResult android = driver.compile(source, tempDir.resolve("android"), Target.ANDROID);
        assertTrue(android.success(), "android compila kof.db/kof.orm desde DB-2: "
                + android.diagnostics().getDiagnostics());
        byte[] a = Files.readAllBytes(tempDir.resolve("jvm/Default/Main.class"));
        byte[] b = Files.readAllBytes(tempDir.resolve("android/Default/Main.class"));
        assertArrayEquals(a, b, "Main.class ANDROID deve ser identico ao JVM (paridade por construcao)");
    }

    @Test
    void transactionRollsBackOnFailure(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("jdbc:h2:mem:test5;DB_CLOSE_DELAY=-1")
                db.execute(db, "create table t(x int)")
                try {
                    transaction {
                        db.execute(db, "insert into t values (1)")
                        throw "boom"
                    }
                } catch (String e) {
                    println("caught")
                }
                 var rows = db.query(db, "select count(*) as n from t")
                println(rows.get(0))
            }
            """);
        runJvm(source, tempDir.resolve("out"), "caught\n{\"n\":0}");
    }

    // GitHub #65 / bug 77 — aninhamento: um bloco transaction interno NESTA
    // mesma conexão NÃO comita (participa da transação externa). Antes o
    // commit interno confirmava as linhas da transação externa e o rollback
    // posterior não as desfazia ({"n":2} — garantia transacional quebrada).
    @Test
    void nestedTransactionDoesNotCommitOuterScope(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var connection = db.connect("jdbc:h2:mem:tx_probe;DB_CLOSE_DELAY=-1")
                db.execute(connection, "create table entries(id int)")
                try {
                    transaction {
                        db.execute(connection, "insert into entries values (1)")
                        transaction {
                            db.execute(connection, "insert into entries values (2)")
                        }
                        throw "abort outer transaction"
                    }
                } catch (String e) {
                    println("caught")
                }
                var rows = db.query(connection, "select count(*) as n from entries")
                println(rows.get(0))
            }
            """);
        runJvm(source, tempDir.resolve("out"), "caught\n{\"n\":0}");
    }

    // ── DB001 (16/09): os mesmos contratos no KofJS, via delegate
    //    `kof_platform.db*` no host GraalJS — saida byte-parity com JVM ──
    @Test
    void jsExecuteAndQueryRowsAsJson(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("jdbc:h2:mem:js1;DB_CLOSE_DELAY=-1")
                db.execute(db, "create table users(id int, name varchar(50))")
                db.execute(db, "insert into users values (?, ?)", 1, "Mel")
                db.execute(db, "insert into users values (?, ?)", 2, "Kof")
                var rows = db.query(db, "select * from users order by id")
                println(rows.size)
                println(rows.get(0))
                println(rows.get(1))
            }
            """);
        runJs(source, tempDir.resolve("out"),
                "2\n{\"id\":1,\"name\":\"Mel\"}\n{\"id\":2,\"name\":\"Kof\"}");
    }

    // S2/db-parity (23/09): mesma paridade no delegate JS — driver ausente no
    // host GraalJS tambem NOMEIA o gap DB001 em vez do SQLException cru.
    @Test
    void jsMissingJdbcDriverNamesGapNotSilent(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("jdbc:oracle:thin:@127.0.0.1:1521/XE")
                println("connected")
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JS);
        assertTrue(result.success(), "JS compilation should succeed: " + result.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(tempDir.resolve("out/Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        String output = out.toString(java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertNotEquals(0, ec, "URL sem driver nao pode 'conectar' no JS: " + output);
        assertTrue(output.contains("DB001"), "JS deve NOMEAR o gap DB001 (R6), veio: " + output);
        assertFalse(output.contains("No suitable driver"),
                "Nao vazar a mensagem crua do JDBC no JS: " + output);
    }

    // Edge Q3 no JS: falha real de conexao (driver presente, servidor fora)
    // passa intacta — o rotulo DB001 e so para driver ausente.
    @Test
    void jsRealConnectionFailureIsNotRelabeledDb001(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("jdbc:mariadb://127.0.0.1:1/kof_none")
                println("connected")
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JS);
        assertTrue(result.success(), "JS compilation should succeed: " + result.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(tempDir.resolve("out/Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        String output = out.toString(java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertNotEquals(0, ec, "Servidor fora nao pode 'conectar' no JS: " + output);
        assertFalse(output.contains("DB001"),
                "Falha real de conexao NAO pode virar DB001 no JS: " + output);
    }

    // S3/db-parity (23/09): `mongodb://` no JS nao e URL JDBC — a recusa deve
    // ser SCHEME-AWARE (DB001 nomeando mongodb), nunca a mensagem generica de
    // driver JDBC ausente (que seria enganosa para quem passou mongodb://).
    @Test
    void jsMongodbSchemeNamesGapNotSilent(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("mongodb://localhost:27017/kof_gap")
                println("connected")
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JS);
        assertTrue(result.success(), "JS compilation should succeed: " + result.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(tempDir.resolve("out/Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        String output = out.toString(java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertNotEquals(0, ec, "mongodb:// nao suportado no JS nao pode 'conectar': " + output);
        assertTrue(output.contains("DB001"), "JS deve NOMEAR o gap DB001 (R6), veio: " + output);
        assertTrue(output.contains("not supported on the JS target"),
                "Recusa deve ser SCHEME-AWARE (nao a mensagem JDBC generica), veio: " + output);
        assertFalse(output.contains("No suitable driver"),
                "Nao vazar a mensagem JDBC generica para mongodb://: " + output);
    }

    @Test
    void jsTransactionCommits(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("jdbc:h2:mem:js2;DB_CLOSE_DELAY=-1")
                db.execute(db, "create table t(x int)")
                transaction {
                    db.execute(db, "insert into t values (1)")
                    db.execute(db, "insert into t values (2)")
                }
                var rows = db.query(db, "select count(*) as n from t")
                println(rows.get(0))
            }
            """);
        runJs(source, tempDir.resolve("out"), "{\"n\":2}");
    }

    @Test
    void jsTransactionRollsBackOnFailure(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("jdbc:h2:mem:js3;DB_CLOSE_DELAY=-1")
                db.execute(db, "create table t(x int)")
                try {
                    transaction {
                        db.execute(db, "insert into t values (1)")
                        throw "boom"
                    }
                } catch (String e) {
                    println("caught")
                }
                var rows = db.query(db, "select count(*) as n from t")
                println(rows.get(0))
            }
            """);
        runJs(source, tempDir.resolve("out"), "caught\n{\"n\":0}");
    }

    @Test
    void jsNestedTransactionDoesNotCommitOuterScope(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var connection = db.connect("jdbc:h2:mem:js4;DB_CLOSE_DELAY=-1")
                db.execute(connection, "create table entries(id int)")
                try {
                    transaction {
                        db.execute(connection, "insert into entries values (1)")
                        transaction {
                            db.execute(connection, "insert into entries values (2)")
                        }
                        throw "abort outer transaction"
                    }
                } catch (String e) {
                    println("caught")
                }
                var rows = db.query(connection, "select count(*) as n from entries")
                println(rows.get(0))
            }
            """);
        runJs(source, tempDir.resolve("out"), "caught\n{\"n\":0}");
    }

    @Test
    void nativeTransactionCommits(@TempDir Path tempDir) throws IOException {
        assumeTrue(isLinux(), "Native transaction requires Linux + libsqlite3");
        Path source = tempDir.resolve("Native.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("sqlite:%s/tx.db")
                db.execute(db, "create table if not exists t(x int)")
                db.execute(db, "delete from t")
                transaction {
                    db.execute(db, "insert into t values (1)")
                    db.execute(db, "insert into t values (2)")
                }
                var rows = db.query(db, "select count(*) as n from t")
                println(rows.get(0))
            }
            """.formatted(kofPath(tempDir)));
        runNative(source, tempDir.resolve("out"), "{\"n\":2}");
    }

    @Test
    void nativeTransactionRollsBackOnFailure(@TempDir Path tempDir) throws IOException {
        assumeTrue(isLinux(), "Native transaction requires Linux + libsqlite3");
        Path source = tempDir.resolve("Native.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("sqlite:%s/txr.db")
                db.execute(db, "create table if not exists t(x int)")
                db.execute(db, "delete from t")
                try {
                    transaction {
                        db.execute(db, "insert into t values (1)")
                        throw "boom"
                    }
                } catch (String e) {
                    println("caught")
                }
                var rows = db.query(db, "select count(*) as n from t")
                println(rows.get(0))
            }
            """.formatted(kofPath(tempDir)));
        runNative(source, tempDir.resolve("out"), "caught\n{\"n\":0}");
    }

    // GitHub #65 / bug 77 espelhado no Native (§78): o bloco transaction
    // interno NESTA mesma conexão NÃO comita — participa da externa. Antes o
    // commit interno efetivava as linhas e o rollback do externo não as
    // desfazia ({"n":2}). Esperado: {"n":0} (paridade com o JVM).
    @Test
    void nativeNestedTransactionDoesNotCommitOuterScope(@TempDir Path tempDir) throws IOException {
        assumeTrue(isLinux(), "Native transaction requires Linux + libsqlite3");
        Path source = tempDir.resolve("Native.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("sqlite:%s/txnest.db")
                db.execute(db, "create table if not exists entries(id int)")
                db.execute(db, "delete from entries")
                try {
                    transaction {
                        db.execute(db, "insert into entries values (1)")
                        transaction {
                            db.execute(db, "insert into entries values (2)")
                        }
                        throw "abort outer transaction"
                    }
                } catch (String e) {
                    println("caught")
                }
                var rows = db.query(db, "select count(*) as n from entries")
                println(rows.get(0))
            }
            """.formatted(kofPath(tempDir)));
        runNative(source, tempDir.resolve("out"), "caught\n{\"n\":0}");
    }

    @Test
    void connectWithCredentials(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("jdbc:h2:mem:test6;DB_CLOSE_DELAY=-1", "sa", "")
                db.execute(db, "create table t(x int)")
                db.execute(db, "insert into t values (42)")
                var rows = db.query(db, "select x from t")
                println(rows.get(0))
                db.close(db)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "{\"x\":42}");
    }

    @Test
    void nativeSqliteRoundtrip(@TempDir Path tempDir) throws IOException {
        assumeTrue(isLinux(), "Native SQLite requires Linux + libsqlite3");
        Path source = tempDir.resolve("Native.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("sqlite:%s/kof.db")
                db.execute(db, "create table if not exists u(id int, name varchar)")
                db.execute(db, "insert into u values (?, ?)", 7, "Nativa")
                var rows = db.query(db, "select id, name from u where id = ?", 7)
                for (var r in rows) {
                    println(r)
                }
                db.close(db)
            }
            """.formatted(kofPath(tempDir)));
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Native compile should succeed: " + result.diagnostics().getDiagnostics());
        Path binFile = tempDir.resolve("out/Default/Main");
        assertTrue(Files.exists(binFile), "Binary should exist");
        try {
            ProcessBuilder pb = new ProcessBuilder(binFile.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals("{\"id\":7,\"name\":\"Nativa\"}", output, "Native SQLite query output");
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running native binary", e);
        }
    }

    @Test
    void nativeUnsupportedSchemeNamesGapNotSilent(@TempDir Path tempDir) throws IOException {
        assumeTrue(isLinux(), "Native exige Linux + as/ld");
        Path source = tempDir.resolve("Pin.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("jdbc:h2:mem:pin")
                println("connected")
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Native compile should succeed: " + result.diagnostics().getDiagnostics());
        Path binFile = tempDir.resolve("out/Default/Main");
        assertTrue(Files.exists(binFile), "Binary should exist");
        try {
            ProcessBuilder pb = new ProcessBuilder(binFile.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
            int ec = p.waitFor();
            assertNotEquals(0, ec, "Scheme fora do contrato nao pode 'conectar': " + output);
            assertTrue(output.contains("DB001"), "Recusa deve NOMEAR o gap DB001 (R6), veio: " + output);
            assertFalse(output.contains("unknown db connection"),
                "Recusa deve ocorrer no connect, nao tarde no ORM: " + output);
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running native binary", e);
        }
    }

    @Test
    void nativeMysqlWireProtocol(@TempDir Path tempDir) throws IOException {
        assumeTrue(isLinux(), "Native MySQL requires Linux");
        // WIP MySQL wire protocol (handshake + auth switch + COM_QUERY + resultset).
        // Requer um MySQL/MariaDB real (porta configurável via env KOF_MYSQL_PORT,
        // default 13306; credenciais root/kofpass, db test). Pula se indisponível.
        int port;
        try { port = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306")); }
        catch (NumberFormatException e) { port = 13306; }
        boolean up;
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress("127.0.0.1", port), 500);
            up = s.isConnected();
        } catch (Exception e) { up = false; }
        assumeTrue(up, "MySQL server not reachable on 127.0.0.1:" + port);
        Path source = tempDir.resolve("M.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("mysql://root:kofpass@127.0.0.1:%d/test")
                db.execute(db, "create table if not exists u(id int, name varchar(50))")
                db.execute(db, "delete from u")
                db.execute(db, "insert into u values (?, ?)", 7, "Nativa")
                var rows = db.query(db, "select id, name from u where id = ?", 7)
                for (var r in rows) {
                    println(r)
                }
                db.close(db)
            }
            """.formatted(port));
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Native compile should succeed: " + result.diagnostics().getDiagnostics());
        Path binFile = tempDir.resolve("out/Default/Main");
        try {
            ProcessBuilder pb = new ProcessBuilder(binFile.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals("{\"id\":7,\"name\":\"Nativa\"}", output, "Native MySQL query output");
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running native binary", e);
        }
    }

    @Test
    void nativeMariadbAliasWireProtocol(@TempDir Path tempDir) throws IOException {
        assumeTrue(isLinux(), "Native MySQL requires Linux");
        // S1/db-parity-plan: `mariadb://` é alias do wire `mysql://` (mesmo
        // handshake/auth/COM_QUERY/resultset, kof_db_type=2). Cobre as DUAS
        // formas de host: com userinfo (`user:pass@`) e sem (o caminho
        // `.Ldb_host_orig`, que computa o host a partir do comprimento do
        // scheme — o bug clássico do alias). Requer MariaDB real (KOF_MYSQL_PORT).
        int port;
        try { port = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306")); }
        catch (NumberFormatException e) { port = 13306; }
        boolean up;
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress("127.0.0.1", port), 500);
            up = s.isConnected();
        } catch (Exception e) { up = false; }
        assumeTrue(up, "MariaDB server not reachable on 127.0.0.1:" + port);
        Path source = tempDir.resolve("M.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("mariadb://root:kofpass@127.0.0.1:%d/test")
                db.execute(db, "create table if not exists m1(id int, name varchar(50))")
                db.execute(db, "delete from m1")
                db.execute(db, "insert into m1 values (?, ?)", 7, "Alias")
                var rows = db.query(db, "select id, name from m1 where id = ?", 7)
                for (var r in rows) { println(r) }
                db.close(db)
                var db2 = db.connect("mariadb://127.0.0.1:%d/test")
                var rows2 = db.query(db2, "select id, name from m1 where id = ?", 7)
                for (var r in rows2) { println(r) }
                db.close(db2)
            }
            """.formatted(port, port));
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Native compile should succeed: " + result.diagnostics().getDiagnostics());
        Path binFile = tempDir.resolve("out/Default/Main");
        try {
            ProcessBuilder pb = new ProcessBuilder(binFile.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals("{\"id\":7,\"name\":\"Alias\"}\n{\"id\":7,\"name\":\"Alias\"}", output,
                    "Native mariadb:// alias output (userinfo + host-only forms)");
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running native binary", e);
        }
    }

    // §488 (24/09): o caminho de texto MySQL do x86 (RuntimeDb5/Ldb_mysql_null)
    // anexava NULL como uma string VAZIA crua -> JSON invalido `{"n":,`; e uma
    // celula de STRING VAZIA caia no detector de digitos (len==0 => "todos
    // digitos") e saia crua tambem. Contrato JVM (kof_db_row_to_json): NULL ->
    // literal `null`; String -> kof_json_encode_string (vazia -> `""`). O mesmo
    // oraculo que a peca cross B70 ja cumpre (NativeRiscvDbWireTest
    // queryAllOracle). Prova: JVM medido (programa identico) e x86 nativo byte
    // a byte. RED pre-fix: o nativo x86 imprimia `{"id":1,"n":,"s":"ab"}`.
    @Test
    void nativeMysqlNullAndEmptyStringJson(@TempDir Path tempDir) throws IOException {
        assumeTrue(isLinux(), "Native MySQL requires Linux");
        int port;
        try { port = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306")); }
        catch (NumberFormatException e) { port = 13306; }
        boolean up;
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress("127.0.0.1", port), 500);
            up = s.isConnected();
        } catch (Exception e) { up = false; }
        assumeTrue(up, "MariaDB server not reachable on 127.0.0.1:" + port);
        String program = """
            main() {
                var db = db.connect("%s")
                db.execute(db, "drop table if exists n488")
                db.execute(db, "create table n488(id int, n int, s varchar(50))")
                db.execute(db, "insert into n488 values (1, null, 'ab')")
                db.execute(db, "insert into n488 values (2, 7, '')")
                var rows = db.query(db, "select id, n, s from n488 order by id")
                for (var r in rows) { println(r) }
                db.close(db)
            }
            """;
        String expected = "{\"id\":1,\"n\":null,\"s\":\"ab\"}\n{\"id\":2,\"n\":7,\"s\":\"\"}";

        // Oráculo JVM medido (mesmo programa; JDBC exige a URL `jdbc:`).
        Path jvm = tempDir.resolve("Oracle.kf");
        Files.writeString(jvm, program.formatted(
                "jdbc:mariadb://127.0.0.1:" + port + "/test?user=root&password=kofpass"));
        CompilationResult jr = driver.compile(jvm, tempDir.resolve("jvm"), Target.JVM);
        assertTrue(jr.success(), "JVM compile should succeed: " + jr.diagnostics().getDiagnostics());
        try {
            String jar = findClasspathJar("mariadb");
            ProcessBuilder pb = new ProcessBuilder("java", "-cp",
                    tempDir.resolve("jvm") + java.io.File.pathSeparator + jar, "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String jout = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "oráculo JVM exit code, output: '" + jout + "'");
            assertEquals(expected, jout, "oráculo JVM (§488: NULL -> null; string vazia -> \"\")");
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running JVM oracle", e);
        }

        Path source = tempDir.resolve("M.kf");
        Files.writeString(source, program.formatted("mysql://root:kofpass@127.0.0.1:" + port + "/test"));
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Native compile should succeed: " + result.diagnostics().getDiagnostics());
        Path binFile = tempDir.resolve("out/Default/Main");
        try {
            ProcessBuilder pb = new ProcessBuilder(binFile.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "x86 native exit code, output: '" + output + "'");
            assertEquals(expected, output, "x86 nativo deve bater o oráculo JVM byte a byte (§488)");
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running native binary", e);
        }
    }

    @Test
    void nativeMysqlPreparedBinary(@TempDir Path tempDir) throws IOException {
        assumeTrue(isLinux(), "Native MySQL requires Linux");
        int port;
        try { port = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306")); }
        catch (NumberFormatException e) { port = 13306; }
        boolean up;
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress("127.0.0.1", port), 500);
            up = s.isConnected();
        } catch (Exception e) { up = false; }
        assumeTrue(up, "MySQL server not reachable on 127.0.0.1:" + port);
        Path source = tempDir.resolve("M.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("mysql://root:kofpass@127.0.0.1:%d/test")
                db.execute(db, "drop table if exists u3")
                db.execute(db, "create table u3(id int, name varchar(120))")
                db.execute(db, "insert into u3 values (?, ?)", 5, "Joe 'Cool'")
                db.execute(db, "insert into u3 values (?, ?)", 6, "x\\\"; drop table u3; --")
                var rows = db.query(db, "select id, name from u3 order by id")
                for (var r in rows) { println(r) }
                var rows2 = db.query(db, "select id, name from u3 where name = ?", "Joe 'Cool'")
                for (var r in rows2) { println(r) }
                db.close(db)
            }
            """.formatted(port));
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Native compile should succeed: " + result.diagnostics().getDiagnostics());
        Path binFile = tempDir.resolve("out/Default/Main");
        try {
            ProcessBuilder pb = new ProcessBuilder(binFile.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals("{\"id\":5,\"name\":\"Joe 'Cool'\"}\n{\"id\":6,\"name\":\"x\\\"; drop table u3; --\"}\n"
                    + "{\"id\":5,\"name\":\"Joe 'Cool'\"}", output, "Native MySQL binary query output");
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running native binary", e);
        }
    }

    @Test
    void nativeSupportsSqliteAndJsCompilesTypedQuery(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("sqlite:/tmp/kof-db-test.db")
            }
            """);
        // Native: kof.db agora compila — SQLite via link direto de
        // libsqlite3 (sem JDBC driver); URLs não-sqlite falham em runtime.
        CompilationResult nativeResult = driver.compile(source, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(),
                nativeResult.diagnostics().getDiagnostics().toString());

        // DB002 (18/09): o gate compile-time CAIU — query tipado compila no JS
        // (bind no guest via __kof_decode_<T>, wire untyped).
        Path jsSource = tempDir.resolve("MainJs.kf");
        Files.writeString(jsSource, """
            record User(Int id, String name)

            main() {
                var db = db.connect("jdbc:sqlite:/tmp/x.db")
                var rows = db.query<User>(db, "select * from u")
                println(rows.size)
            }
            """);
        CompilationResult jsResult = driver.compile(jsSource, tempDir.resolve("js-out"), Target.JS);
        assertTrue(jsResult.success(), "DB002: typed query agora compila no JS: "
                + jsResult.diagnostics().getDiagnostics());
        assertFalse(jsResult.diagnostics().getDiagnostics().toString().contains("DB002"),
                jsResult.diagnostics().getDiagnostics().toString());
    }

    @Test
    void jsTypedQueryBindsRecord(@TempDir Path tempDir) throws IOException {
        // DB002 (18/09): paridade byte-a-byte com `typedQueryBindsRecord` da JVM
        // (o mesmo `__kof_decode_<T>` do json.decode faz o bind por linha).
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            record User(Int id, String name)

            main() {
                var db = db.connect("jdbc:h2:mem:jsd002a;DB_CLOSE_DELAY=-1")
                db.execute(db, "create table users(id int, name varchar(50))")
                db.execute(db, "insert into users values (?, ?)", 7, "Ada")
                var users = db.query<User>(db, "select * from users where id = ?", 7)
                println(users.size)
                println(users.get(0).id)
                println(users.get(0).name)
            }
            """);
        runJs(source, tempDir.resolve("out"), "1\n7\nAda");
    }

    @Test
    void jsTypedQueryAllRows(@TempDir Path tempDir) throws IOException {
        // DB002 (18/09): paridade com `typedQueryAllRows` da JVM (for-in sobre
        // List<record> + acesso a campo).
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            record User(Int id, String name)

            main() {
                var db = db.connect("jdbc:h2:mem:jsd002b;DB_CLOSE_DELAY=-1")
                db.execute(db, "create table users(id int, name varchar(50))")
                db.execute(db, "insert into users values (1, 'A')")
                db.execute(db, "insert into users values (2, 'B')")
                var users = db.query<User>(db, "select * from users order by id")
                var total = 0
                for (var u in users) {
                    total = total + u.id
                }
                println(total)
            }
            """);
        runJs(source, tempDir.resolve("out"), "3");
    }

    @Test
    void handleReuseAfterCloseDoesNotAliasLiveConnection(@TempDir Path tempDir) throws IOException {
        // issue #60 — `kof_db_register` gerava `"db" + (size() + 1)`: fechar
        // `a` e abrir `c` reutilizava o id de `b` (ainda aberta), sobrescrevia
        // o registro e o UPDATE via `b` escrevia no banco C — silencioso.
        // Fix: contador monotônico (nunca reutilizar handle de conexão ativa).
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = db.connect("jdbc:h2:mem:database_a;DB_CLOSE_DELAY=-1")
                var b = db.connect("jdbc:h2:mem:database_b;DB_CLOSE_DELAY=-1")
                db.execute(b, "create table marker(amount int)")
                db.execute(b, "insert into marker values (20)")
                db.close(a)
                var c = db.connect("jdbc:h2:mem:database_c;DB_CLOSE_DELAY=-1")
                db.execute(c, "create table marker(amount int)")
                db.execute(c, "insert into marker values (30)")
                println(b)
                println(c)
                db.execute(b, "update marker set amount = 99")
                var rows = db.query(c, "select amount as n from marker")
                println(rows.get(0))
            }
            """);
        runJvm(source, tempDir.resolve("jvm"), "db2\ndb3\n{\"n\":30}");
    }

    @Test
    void crossNativeSqliteRoundtrip(@TempDir Path tempDir) throws IOException {        // DB001 fechado no cross (15/09): o frontend compila db.* e o runtime
        // RtB46/RtB47 liga dinamicamente a libsqlite3 (link-by-use). Mesmo
        // programa do nativeSqliteRoundtrip, riscv64 + aarch64 sob qemu.
        Path source = tempDir.resolve("Cross.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("sqlite:%s/kof.db")
                db.execute(db, "create table if not exists u(id int, name varchar)")
                db.execute(db, "delete from u")
                db.execute(db, "insert into u values (?, ?)", 7, "Nativa")
                var rows = db.query(db, "select id, name from u where id = ?", 7)
                for (var r in rows) {
                    println(r)
                }
                db.close(db)
            }
            """.formatted(kofPath(tempDir)));
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String arch = t.nativeArch();
            String as = arch.equals("riscv64") ? "riscv64-linux-gnu-as" : "aarch64-linux-gnu-as";
            String ld = arch.equals("riscv64") ? "riscv64-linux-gnu-ld" : "aarch64-linux-gnu-ld";
            assumeTrue(has(as, ld, "qemu-" + arch), "cross toolchain " + arch + " ausente — pulando");
            assumeTrue(dev.kof.compiler.nat.NativeCrossLink.sysrootOrNull(arch) != null,
                    "sysroot cross " + arch + " ausente — pulando");
            assumeTrue(dev.kof.compiler.nat.NativeCrossLink.sqliteAvailable(arch),
                    "libsqlite3 " + arch + " ausente no sysroot — pulando");
            Path out = tempDir.resolve("out-" + t);
            CompilationResult r = driver.compile(source, out, t);
            assertTrue(r.success(), t + " deveria compilar db.*: " + r.diagnostics().getDiagnostics());
            Path binFile = out.resolve("Default/Main");
            assertTrue(Files.exists(binFile), "binário " + t + " deveria existir");
            ProcessBuilder pb = new ProcessBuilder("qemu-" + arch, binFile.toString());
            String prefix = qemuPrefix(arch);
            if (prefix != null) pb.environment().put("QEMU_LD_PREFIX", prefix);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec;
            try {
                ec = p.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted running " + t + " binary", e);
            }
            assertEquals(0, ec, t + " exit code, output: '" + output + "'");
            assertEquals("{\"id\":7,\"name\":\"Nativa\"}", output, t + " SQLite query output");
        }
    }

    // §477 (23/09): excecao NAO capturada no cross perdia a mensagem — o
    // `.Lthrow_panic` chamava `kof_panic` (que le .asciz) com um KofString:
    // strlen=0 -> so' um newline. Paridade x86: imprime a mensagem e sai 1.
    // Este teste roda os binarios riscv64/aarch64 de verdade (qemu) e pina a
    // mensagem no stdout/stderr — sem isso o erro era SILENCIOSO (R6).
    @Test
    void crossUncaughtThrowPrintsMessageAndExitsNonZero(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("Boom.kf");
        Files.writeString(source, "main() {\n    throw \"BOOM-MESSAGE\"\n}\n");
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String arch = t.nativeArch();
            String as = arch.equals("riscv64") ? "riscv64-linux-gnu-as" : "aarch64-linux-gnu-as";
            String ld = arch.equals("riscv64") ? "riscv64-linux-gnu-ld" : "aarch64-linux-gnu-ld";
            assumeTrue(has(as, ld, "qemu-" + arch), "cross toolchain " + arch + " ausente — pulando");
            assumeTrue(dev.kof.compiler.nat.NativeCrossLink.sysrootOrNull(arch) != null,
                    "sysroot cross " + arch + " ausente — pulando");
            Path out = tempDir.resolve("out-boom-" + t);
            CompilationResult r = driver.compile(source, out, t);
            assertTrue(r.success(), t + " deveria compilar throw: " + r.diagnostics().getDiagnostics());
            Path binFile = out.resolve("Default/Main");
            assertTrue(Files.exists(binFile), "binário " + t + " deveria existir");
            ProcessBuilder pb = new ProcessBuilder("qemu-" + arch, binFile.toString());
            String prefix = qemuPrefix(arch);
            if (prefix != null) pb.environment().put("QEMU_LD_PREFIX", prefix);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
            int ec;
            try {
                ec = p.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted running " + t + " binary", e);
            }
            assertNotEquals(0, ec, t + " excecao nao capturada deve sair != 0: " + output);
            assertTrue(output.contains("BOOM-MESSAGE"),
                    t + " excecao nao capturada deve IMPRIMIR a mensagem (R6), veio: [" + output + "]");
        }
    }

    // S5.4/honestidade (24/09): a recusa DB001 do cross agora vale para os
    // esquemas que REALMENTE nao existem lá (sqlite:/mysql:///mariadb:// ja
    // foram portados): um programa com postgres:// sai != 0 e imprime a
    // mensagem VERDADEIRA — nunca anuncia um esquema como suportado (R6/R7).
    @Test
    void crossNativeUnsupportedSchemeNamesTruthfulDb001(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("CrossRefuse.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("postgres://root@127.0.0.1:5432/x")
                println("connected")
            }
            """);
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String arch = t.nativeArch();
            String as = arch.equals("riscv64") ? "riscv64-linux-gnu-as" : "aarch64-linux-gnu-as";
            String ld = arch.equals("riscv64") ? "riscv64-linux-gnu-ld" : "aarch64-linux-gnu-ld";
            assumeTrue(has(as, ld, "qemu-" + arch), "cross toolchain " + arch + " ausente — pulando");
            assumeTrue(dev.kof.compiler.nat.NativeCrossLink.sysrootOrNull(arch) != null,
                    "sysroot cross " + arch + " ausente — pulando");
            assumeTrue(dev.kof.compiler.nat.NativeCrossLink.sqliteAvailable(arch),
                    "libsqlite3 " + arch + " ausente no sysroot — pulando");
            Path out = tempDir.resolve("out-refuse-" + t);
            CompilationResult r = driver.compile(source, out, t);
            assertTrue(r.success(), t + " deveria compilar (connect postgres://): " + r.diagnostics().getDiagnostics());
            Path binFile = out.resolve("Default/Main");
            assertTrue(Files.exists(binFile), "binário " + t + " deveria existir");
            ProcessBuilder pb = new ProcessBuilder("qemu-" + arch, binFile.toString());
            String prefix = qemuPrefix(arch);
            if (prefix != null) pb.environment().put("QEMU_LD_PREFIX", prefix);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
            int ec;
            try {
                ec = p.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted running " + t + " binary", e);
            }
            assertNotEquals(0, ec, t + " postgres:// no cross nao pode 'conectar': " + output);
            assertTrue(output.contains("DB001"), t + " recusa deve NOMEAR DB001, veio: " + output);
            assertTrue(output.contains("native cross: sqlite:, mysql://, mariadb://"),
                    t + " mensagem deve ser VERDADEIRA (esquemas realmente portados), veio: " + output);
            assertFalse(output.contains("(native: sqlite:, mysql://)"),
                    t + " mensagem nao pode ser a do x86, veio: " + output);
        }
    }

    // S5.4 fatia 2 (24/09): o espelho cross do `nativeMariadbAliasWireProtocol`
    // — o MESMO programa Kof (execute com binds + query com bind) agora roda de
    // verdade no riscv64/aarch64 contra o MariaDB real. Prova a cadeia inteira:
    // `db.connect` (B73) + dispatch type 2 no `kof_db_execute`/`kof_db_query`
    // (B47b) + substituição client-side B71 + B72/B70.
    @Test
    void crossNativeMariadbAliasWireProtocol(@TempDir Path tempDir) throws IOException {
        int port;
        try { port = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306")); }
        catch (NumberFormatException e) { port = 13306; }
        boolean up;
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress("127.0.0.1", port), 500);
            up = s.isConnected();
        } catch (Exception e) { up = false; }
        assumeTrue(up, "MariaDB server not reachable on 127.0.0.1:" + port);
        Path source = tempDir.resolve("CrossAlias.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("mariadb://root:kofpass@127.0.0.1:%d/test")
                db.execute(db, "create table if not exists kof_xalias(id int, name varchar(50))")
                db.execute(db, "delete from kof_xalias")
                db.execute(db, "insert into kof_xalias values (?, ?)", 7, "Alias")
                var rows = db.query(db, "select id, name from kof_xalias where id = ?", 7)
                for (var r in rows) { println(r) }
                db.close(db)
                var db2 = db.connect("mariadb://127.0.0.1:%d/test")
                var rows2 = db.query(db2, "select id, name from kof_xalias where id = ?", 7)
                for (var r in rows2) { println(r) }
                db.close(db2)
            }
            """.formatted(port, port));
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String arch = t.nativeArch();
            String as = arch.equals("riscv64") ? "riscv64-linux-gnu-as" : "aarch64-linux-gnu-as";
            String ld = arch.equals("riscv64") ? "riscv64-linux-gnu-ld" : "aarch64-linux-gnu-ld";
            assumeTrue(has(as, ld, "qemu-" + arch), "cross toolchain " + arch + " ausente — pulando");
            assumeTrue(dev.kof.compiler.nat.NativeCrossLink.sysrootOrNull(arch) != null,
                    "sysroot cross " + arch + " ausente — pulando");
            assumeTrue(dev.kof.compiler.nat.NativeCrossLink.sqliteAvailable(arch),
                    "libsqlite3 " + arch + " ausente no sysroot — pulando");
            Path out = tempDir.resolve("out-alias-" + t);
            CompilationResult r = driver.compile(source, out, t);
            assertTrue(r.success(), t + " deveria compilar (mariadb:// alias): " + r.diagnostics().getDiagnostics());
            Path binFile = out.resolve("Default/Main");
            assertTrue(Files.exists(binFile), "binário " + t + " deveria existir");
            ProcessBuilder pb = new ProcessBuilder("qemu-" + arch, binFile.toString());
            String prefix = qemuPrefix(arch);
            if (prefix != null) pb.environment().put("QEMU_LD_PREFIX", prefix);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec;
            try {
                ec = p.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted running " + t + " binary", e);
            }
            assertEquals(0, ec, t + " exit code, output: '" + output + "'");
            assertEquals("{\"id\":7,\"name\":\"Alias\"}\n{\"id\":7,\"name\":\"Alias\"}", output,
                    t + " mariadb:// alias (userinfo + host-only) no cross");
        }
    }

    // S5.4 fatia 2 (24/09): `transaction { }` no cross mysql. O BEGIN/COMMIT/
    // ROLLBACK da B47 chamam `kof_db_execute`, que agora despacha por tipo — a
    // transação real roda no wire (COM_QUERY "begin"/"commit"/"rollback").
    @Test
    void crossNativeMariadbTransactionCommits(@TempDir Path tempDir) throws IOException {
        int port = crossMariaPort();
        assertCrossMariaOutput(tempDir, """
            main() {
                var db = db.connect("mysql://root:kofpass@127.0.0.1:%d/test")
                db.execute(db, "drop table if exists kof_tx")
                db.execute(db, "create table kof_tx(x int)")
                transaction {
                    db.execute(db, "insert into kof_tx values (1)")
                    db.execute(db, "insert into kof_tx values (2)")
                }
                var rows = db.query(db, "select count(*) as n from kof_tx")
                for (var r in rows) { println(r) }
                db.close(db)
            }
            """.formatted(port), "{\"n\":2}");
    }

    @Test
    void crossNativeMariadbTransactionRollsBackOnFailure(@TempDir Path tempDir) throws IOException {
        int port = crossMariaPort();
        assertCrossMariaOutput(tempDir, """
            main() {
                var db = db.connect("mysql://root:kofpass@127.0.0.1:%d/test")
                db.execute(db, "drop table if exists kof_tx")
                db.execute(db, "create table kof_tx(x int)")
                db.execute(db, "insert into kof_tx values (1)")
                try {
                    transaction {
                        db.execute(db, "insert into kof_tx values (2)")
                        throw "abort"
                    }
                } catch (String e) {
                    println("caught:" + e)
                }
                var rows = db.query(db, "select count(*) as n from kof_tx")
                for (var r in rows) { println(r) }
                db.close(db)
            }
            """.formatted(port), "caught:abort\n{\"n\":1}");
    }

    /** Porta do MariaDB (assume viva) — espelho do guard dos E2E cross mysql. */
    private static int crossMariaPort() {
        int port;
        try { port = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306")); }
        catch (NumberFormatException e) { port = 13306; }
        boolean up;
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress("127.0.0.1", port), 500);
            up = s.isConnected();
        } catch (Exception e) { up = false; }
        assumeTrue(up, "MariaDB server not reachable on 127.0.0.1:" + port);
        return port;
    }

    /** Compila e roda no riscv64+aarch64 (qemu) exigindo o MariaDB real. */
    private void assertCrossMariaOutput(Path tempDir, String source, String expected) throws IOException {
        Path kf = tempDir.resolve("CrossTx.kf");
        Files.writeString(kf, source);
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String arch = t.nativeArch();
            String as = arch.equals("riscv64") ? "riscv64-linux-gnu-as" : "aarch64-linux-gnu-as";
            String ld = arch.equals("riscv64") ? "riscv64-linux-gnu-ld" : "aarch64-linux-gnu-ld";
            assumeTrue(has(as, ld, "qemu-" + arch), "cross toolchain " + arch + " ausente — pulando");
            assumeTrue(dev.kof.compiler.nat.NativeCrossLink.sysrootOrNull(arch) != null,
                    "sysroot cross " + arch + " ausente — pulando");
            assumeTrue(dev.kof.compiler.nat.NativeCrossLink.sqliteAvailable(arch),
                    "libsqlite3 " + arch + " ausente no sysroot — pulando");
            Path out = tempDir.resolve("out-tx-" + arch);
            CompilationResult r = driver.compile(kf, out, t);
            assertTrue(r.success(), t + " deveria compilar: " + r.diagnostics().getDiagnostics());
            Path binFile = out.resolve("Default/Main");
            ProcessBuilder pb = new ProcessBuilder("qemu-" + arch, binFile.toString());
            String prefix = qemuPrefix(arch);
            if (prefix != null) pb.environment().put("QEMU_LD_PREFIX", prefix);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec;
            try {
                ec = p.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted running " + t + " binary", e);
            }
            assertEquals(0, ec, t + " exit code, output: '" + output + "'");
            assertEquals(expected, output, t + " transação mysql cross diverge do esperado");
        }
    }

    /** has() do padrão dos testes cross (command -v). */
    private static boolean has(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c)
                        .redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
                if (p.waitFor() != 0 || out.isEmpty()) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    /** QEMU_LD_PREFIX p/ o loader dinâmico (espelho de NativeRiscv64E2ETest). */
    private static String qemuPrefix(String arch) {
        String env = System.getenv("KOF_CROSS_SYSROOT");
        String loader = arch.equals("riscv64") ? "ld-linux-riscv64-lp64d.so.1" : "ld-linux-aarch64.so.1";
        if (env != null && !env.isBlank()
                && Files.exists(Path.of(env, "usr", arch + "-linux-gnu", "lib", loader))) {
            return env + "/usr/" + arch + "-linux-gnu";
        }
        for (String root : new String[]{"/tmp/opencode/x", "/"}) {
            Path p = Path.of(root, "usr", arch + "-linux-gnu");
            if (Files.exists(p.resolve("lib").resolve(loader))) {
                return root.equals("/") ? p.toString() : root + "/usr/" + arch + "-linux-gnu";
            }
        }
        return null;
    }

    @Test
    void crossNativeSqliteNowCompiles(@TempDir Path tempDir) throws IOException {
        // DB001 fechado no cross (15/09): o gate caiu — db.* compila nos dois
        // alvos cross. §255 (16/09): compilar p/ cross INCLUI o link contra a
        // libsqlite3 do sysroot (link-by-use); sem ela o ld falha ALTO — que é
        // R6 correto p/ o USUÁRIO, mas um falso-vermelho p/ a SUÍTE numa máquina
        // sem o pacote multiarch. O guard é o mesmo par do irmão
        // crossNativeSqliteRoundtrip: pula honesto quando toolchain/sysroot/
        // sqlite ausentes; na máquina com eles roda como antes (Q0: sem o
        // guard, riscv64-ld "não foi possível localizar -lsqlite3" = red).
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("sqlite:/tmp/x.db")
            }
            """);
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String arch = t.nativeArch();
            String as = arch.equals("riscv64") ? "riscv64-linux-gnu-as" : "aarch64-linux-gnu-as";
            String ld = arch.equals("riscv64") ? "riscv64-linux-gnu-ld" : "aarch64-linux-gnu-ld";
            assumeTrue(has(as, ld, "qemu-" + arch), "cross toolchain " + arch + " ausente — pulando");
            assumeTrue(dev.kof.compiler.nat.NativeCrossLink.sysrootOrNull(arch) != null,
                    "sysroot cross " + arch + " ausente — pulando");
            assumeTrue(dev.kof.compiler.nat.NativeCrossLink.sqliteAvailable(arch),
                    "libsqlite3 " + arch + " ausente no sysroot — pulando");
            CompilationResult r = new CompilerDriver().compile(source, tempDir.resolve("cross-" + t), t);
            assertTrue(r.success(), t + " db.* agora compila (DB001 fechado): "
                    + r.diagnostics().getDiagnostics());
        }
    }

    @Test
    void jsDbConnectNowCompiles(@TempDir Path tempDir) throws IOException {
        // DB001 fechado no JS (16/09): o gate `KofDb.supportedOn` abriu —
        // connect nao-tipado compila (a ponte `kof_platform.db*` roda na mesma
        // JVM/classpath do caminho JVM). DB002 fechado no JS (18/09): o query
        // tipado tbem compila (bind no guest; veja jsTypedQueryBindsRecord).
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("jdbc:h2:mem:gate;DB_CLOSE_DELAY=-1")
            }
            """);
        CompilationResult r = driver.compile(source, tempDir.resolve("js-out"), Target.JS);
        assertTrue(r.success(), "connect deve compilar no JS apos DB001: "
                + r.diagnostics().getDiagnostics());
        assertFalse(r.diagnostics().getDiagnostics().toString().contains("DB001"),
                r.diagnostics().getDiagnostics().toString());
    }

    // §523 (27/09): `db.connect` contra servidor que REJEITA auth devolvia
    // handle morto e o query seguinte SIGSEGVa (ec=139) em vez de lancar o
    // ERR do handshake (lei JVM). A prova e HERMETICA: um fake server MySQL
    // no proprio teste (greeting 0x0A no formato exato do
    // `NativeRiscvDbWireTest#greetingHarness` + resposta ERR 1045) — roda em
    // todo host e na CI, sem fixture. RED pre-fix: sem throw, o programa
    // imprime NO-THROW ou morre no query (ec=139).
    private static byte[] fakeMysqlGreeting() throws IOException {
        java.io.ByteArrayOutputStream p = new java.io.ByteArrayOutputStream();
        p.write(0x0A);
        p.write("5.5.5-10.3.39-MariaDB".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        p.write(0);
        p.write(new byte[]{0x2A, 0, 0, 0});
        p.write("abcdefgh".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        p.write(0);
        p.write(new byte[]{0, 0});
        p.write(0x21);
        p.write(new byte[]{2, 0});
        p.write(new byte[]{0, 0});
        p.write(21);
        p.write(new byte[10]);
        p.write("ABCDEFGH1234".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        p.write(0);
        byte[] payload = p.toByteArray();
        java.io.ByteArrayOutputStream f = new java.io.ByteArrayOutputStream();
        f.write(payload.length & 0xFF);
        f.write((payload.length >> 8) & 0xFF);
        f.write((payload.length >> 16) & 0xFF);
        f.write(0);
        f.write(payload);
        return f.toByteArray();
    }

    private static byte[] mysqlErrPacket(String msg) throws IOException {
        byte[] m = msg.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        java.io.ByteArrayOutputStream p = new java.io.ByteArrayOutputStream();
        p.write(0xFF);
        p.write(0x15);
        p.write(0x09);
        p.write('#');
        p.write("28000".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        p.write(m);
        byte[] payload = p.toByteArray();
        java.io.ByteArrayOutputStream f = new java.io.ByteArrayOutputStream();
        f.write(payload.length & 0xFF);
        f.write((payload.length >> 8) & 0xFF);
        f.write((payload.length >> 16) & 0xFF);
        f.write(2);
        f.write(payload);
        return f.toByteArray();
    }

    /** Fake server: 1 conexao (greeting, le o handshake-response, devolve ERR),
     *  depois fecha. Devolve a porta + os bytes do request visto (prova que o
     *  cliente tentou o auth de verdade — o fix nao pode lancar antes de enviar). */
    private static final class FakeMysql {
        final int port;
        final java.util.concurrent.atomic.AtomicReference<byte[]> request = new java.util.concurrent.atomic.AtomicReference<>();
        FakeMysql(String errMsg) throws IOException {
            java.net.ServerSocket ss = new java.net.ServerSocket(0, 1,
                    java.net.InetAddress.getByName("127.0.0.1"));
            ss.setSoTimeout(30000);
            port = ss.getLocalPort();
            Thread th = new Thread(() -> {
                try (java.net.Socket s = ss.accept()) {
                    s.setSoTimeout(15000);
                    var out = s.getOutputStream();
                    var in = s.getInputStream();
                    out.write(fakeMysqlGreeting());
                    out.flush();
                    byte[] hdr = in.readNBytes(4);
                    int len = (hdr[0] & 0xFF) | ((hdr[1] & 0xFF) << 8) | ((hdr[2] & 0xFF) << 16);
                    byte[] body = in.readNBytes(len);
                    byte[] full = new byte[4 + body.length];
                    System.arraycopy(hdr, 0, full, 0, 4);
                    System.arraycopy(body, 0, full, 4, body.length);
                    request.set(full);
                    out.write(mysqlErrPacket(errMsg));
                    out.flush();
                    Thread.sleep(500);
                } catch (Exception ignored) {
                } finally {
                    try { ss.close(); } catch (Exception ignored) { }
                }
            });
            th.setDaemon(true);
            th.start();
        }
    }

    private static String authRejectProgram(int port) {
        return """
            main() {
                try {
                    var db = db.connect("mysql://root:wrong@127.0.0.1:%d/test")
                    var rows = db.query(db, "select 1")
                    println("NO-THROW")
                } catch (String e) {
                    println("caught:" + e)
                }
            }
            """.formatted(port);
    }

    /** Roda o binario nativo (direto no x86, sob qemu no cross) — [output, ec]. */
    private static String[] execBin(Path binFile, String arch) throws IOException {
        ProcessBuilder pb = arch == null
                ? new ProcessBuilder(binFile.toString())
                : new ProcessBuilder("qemu-" + arch, binFile.toString());
        if (arch != null) {
            String prefix = qemuPrefix(arch);
            if (prefix != null) pb.environment().put("QEMU_LD_PREFIX", prefix);
        }
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        try {
            return new String[]{output, String.valueOf(p.waitFor())};
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted running native binary", e);
        }
    }

    private static boolean tcpUp(int port) {
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress("127.0.0.1", port), 500);
            return s.isConnected();
        } catch (Exception e) { return false; }
    }

    @Test
    void nativeAuthRejectThrowsNamedMysqlError(@TempDir Path tempDir) throws Exception {
        assumeTrue(isLinux(), "Native MySQL requires Linux");
        String denied = "Access denied for user 'root'@'localhost' (using password: YES)";
        FakeMysql fake = new FakeMysql(denied);
        Path source = tempDir.resolve("M.kf");
        Files.writeString(source, authRejectProgram(fake.port));
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Native compile should succeed: " + result.diagnostics().getDiagnostics());
        String[] run = execBin(tempDir.resolve("out/Default/Main"), null);
        assertEquals("0", run[1], "Exit code should be 0, output: '" + run[0] + "'");
        assertEquals("caught:mysql: " + denied, run[0], "Auth reject must THROW the handshake ERR (§523)");
        assertTrue(fake.request.get() != null && fake.request.get().length > 4,
                "O cliente deve ter enviado o handshake-response antes do throw");
    }

    @Test
    void crossNativeAuthRejectThrowsNamedMysqlError(@TempDir Path tempDir) throws Exception {
        assumeTrue(isLinux(), "Native MySQL requires Linux");
        String denied = "Access denied for user 'root'@'localhost' (using password: YES)";
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String arch = t.nativeArch();
            String as = arch.equals("riscv64") ? "riscv64-linux-gnu-as" : "aarch64-linux-gnu-as";
            String ld = arch.equals("riscv64") ? "riscv64-linux-gnu-ld" : "aarch64-linux-gnu-ld";
            assumeTrue(has(as, ld, "qemu-" + arch), "cross toolchain " + arch + " ausente — pulando");
            assumeTrue(dev.kof.compiler.nat.NativeCrossLink.sysrootOrNull(arch) != null,
                    "sysroot cross " + arch + " ausente — pulando");
            // db.* cross linka sqlite por padrão; sem a lib cross o ld falha em
            // `-lsqlite3`. Mesmo guard dos demais testes cross desta classe
            // (sqliteAvailable) — condição ambiental, nunca falso-verde.
            assumeTrue(dev.kof.compiler.nat.NativeCrossLink.sqliteAvailable(arch),
                    "sqlite cross " + arch + " ausente — pulando");
            FakeMysql fake = new FakeMysql(denied);
            Path source = tempDir.resolve("M-" + arch + ".kf");
            Files.writeString(source, authRejectProgram(fake.port));
            Path out = tempDir.resolve("out-" + t);
            CompilationResult r = driver.compile(source, out, t);
            assertTrue(r.success(), t + " deveria compilar db.*: " + r.diagnostics().getDiagnostics());
            String[] run = execBin(out.resolve("Default/Main"), arch);
            assertEquals("0", run[1], t + " exit code, output: '" + run[0] + "'");
            assertEquals("caught:mysql: " + denied, run[0], t + " auth reject must THROW (§523)");
        }
    }

    @Test
    void nativeTcpRefusedThrowsConnectionLost(@TempDir Path tempDir) throws Exception {
        assumeTrue(isLinux(), "Native MySQL requires Linux");
        // Porta fechada de verdade (abre e fecha um ServerSocket) — sem fixture.
        int port;
        try (java.net.ServerSocket ss = new java.net.ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            port = ss.getLocalPort();
        }
        assumeTrue(!tcpUp(port), "A porta deveria estar fechada: " + port);
        Path source = tempDir.resolve("M.kf");
        Files.writeString(source, """
            main() {
                try {
                    var db = db.connect("mysql://root:x@127.0.0.1:%d/test")
                    println("NO-THROW")
                } catch (String e) {
                    println("caught:" + e)
                }
            }
            """.formatted(port));
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Native compile should succeed: " + result.diagnostics().getDiagnostics());
        String[] run = execBin(tempDir.resolve("out/Default/Main"), null);
        assertEquals("0", run[1], "Exit code should be 0, output: '" + run[0] + "'");
        assertEquals("caught:mysql: connection lost", run[0], "TCP recusado deve lancar (escopo §523 votado)");
    }

    @Test
    void authRejectMatchesJvmOnEnforcingServer(@TempDir Path tempDir) throws Exception {
        assumeTrue(isLinux(), "Native MySQL requires Linux");
        // Servidor que EXIGE auth (KOF_MYSQL_AUTH_PORT, default 13307 =
        // fixture user-land ~/.local/share/kof-mariadb/auth, root/kofpass).
        int port;
        try { port = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_AUTH_PORT", "13307")); }
        catch (NumberFormatException e) { port = 13307; }
        assumeTrue(tcpUp(port), "Auth-enforcing MariaDB not reachable on 127.0.0.1:" + port);
        String program = """
            main() {
                try {
                    var db = db.connect("mysql://root:wrong@127.0.0.1:%d/test")
                    println("NO-THROW")
                } catch (String e) {
                    println("caught:" + e)
                }
            }
            """.formatted(port);
        Path source = tempDir.resolve("M.kf");
        Files.writeString(source, program);
        // Lei JVM: o connect NAO entrega handle — o erro do servidor aborta
        // (excecao JDBC nao-capturavel por `catch (String e)`: assimetria
        // pre-existente do modelo JVM, igual em toda chamada de runtime;
        // muda-la seria regra 6. O observavel da lei: sem handle, com a
        // mensagem do servidor — nunca NO-THROW/nunca SIGSEGV).
        Path jvmSrc = tempDir.resolve("MJ.kf");
        Files.writeString(jvmSrc, """
            main() {
                var db = db.connect("jdbc:mariadb://127.0.0.1:%d/test?user=root&password=wrong")
                println("NO-THROW")
            }
            """.formatted(port));
        CompilationResult jvm = driver.compile(jvmSrc, tempDir.resolve("jvm"), Target.JVM);
        assertTrue(jvm.success(), "JVM compile should succeed: " + jvm.diagnostics().getDiagnostics());
        String jvmOut = runJvmExpectFailure(tempDir.resolve("jvm"), findClasspathJar("mariadb"));
        assertTrue(jvmOut.contains("Access denied"), "JVM deve abortar com Access denied, veio: " + jvmOut);
        assertFalse(jvmOut.contains("NO-THROW"), "JVM nao pode entregar handle morto: " + jvmOut);
        // Nativo x86: mesma lei, mensagem `mysql: ` do wire.
        CompilationResult nat = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(nat.success(), "Native compile should succeed: " + nat.diagnostics().getDiagnostics());
        String[] run = execBin(tempDir.resolve("out/Default/Main"), null);
        assertEquals("0", run[1], "Exit code should be 0, output: '" + run[0] + "'");
        assertTrue(run[0].startsWith("caught:mysql: Access denied"),
                "Nativo deve lancar como a JVM, veio: '" + run[0] + "'");
        // Happy path pelo servidor que exige auth (credencial certa) nao quebrou.
        Path happy = tempDir.resolve("H.kf");
        Files.writeString(happy, """
            main() {
                var db = db.connect("mysql://root:kofpass@127.0.0.1:%d/test")
                var rows = db.query(db, "select 1")
                for (var r in rows) { println(r) }
                db.close(db)
            }
            """.formatted(port));
        CompilationResult happyC = driver.compile(happy, tempDir.resolve("happy-out"), Target.NATIVE);
        assertTrue(happyC.success(), "Happy compile should succeed: " + happyC.diagnostics().getDiagnostics());
        String[] happyRun = execBin(tempDir.resolve("happy-out/Default/Main"), null);
        assertEquals("0", happyRun[1], "happy ec, output: '" + happyRun[0] + "'");
        assertEquals("{\"1\":1}", happyRun[0], "happy query output");
    }

    // D-DB-NORMALIZE (27/09, voted): bare `mysql://` works on JVM/JS as
    // `jdbc:mysql://` (userinfo → ?user=&password=). RED pre-fix: DB001
    // "no JDBC driver". Real MariaDB (KOF_MYSQL_PORT), driver mariadb.
    @Test
    void jvmBareMysqlNormalizesToJdbc(@TempDir Path tempDir) throws IOException {
        int port;
        try { port = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306")); }
        catch (NumberFormatException e) { port = 13306; }
        assumeTrue(tcpUp(port), "MariaDB not reachable on 127.0.0.1:" + port);
        Path source = tempDir.resolve("M.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("mysql://root:kofpass@127.0.0.1:%d/test")
                db.execute(db, "create table if not exists nrm(id int, name varchar(50))")
                db.execute(db, "delete from nrm")
                db.execute(db, "insert into nrm values (?, ?)", 7, "Nua")
                var rows = db.query(db, "select id, name from nrm where id = ?", 7)
                for (var r in rows) { println(r) }
                db.close(db)
                var db2 = db.connect("mysql://root:kofpass@127.0.0.1:%d/test?allowMultiQueries=true")
                var rows2 = db.query(db2, "select id, name from nrm where id = ?", 7)
                for (var r in rows2) { println(r) }
                db.close(db2)
            }
            """.formatted(port, port));
        runJvmWithExtra(source, tempDir.resolve("out"), findClasspathJar("mariadb"),
                "{\"id\":7,\"name\":\"Nua\"}\n{\"id\":7,\"name\":\"Nua\"}");
    }

    // D-DB-NORMALIZE: bare `sqlite:<path>` works on JVM as `jdbc:sqlite:<path>`.
    // Contains (not exact): the sqlite-jdbc driver prints JDK25 FFM
    // "restricted method" WARNINGs to stdout — environmental noise, not product.
    @Test
    void jvmBareSqliteNormalizesToJdbc(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("M.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("sqlite:%s/norm.db")
                db.execute(db, "create table if not exists u(id int, name varchar(50))")
                db.execute(db, "delete from u")
                db.execute(db, "insert into u values (?, ?)", 7, "Nua")
                var rows = db.query(db, "select id, name from u where id = ?", 7)
                for (var r in rows) { println(r) }
                db.close(db)
            }
            """.formatted(kofPath(tempDir)));
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "JVM compile should succeed: " + result.diagnostics().getDiagnostics());
        String cp = tempDir.resolve("out") + java.io.File.pathSeparator + findClasspathJar("sqlite-jdbc");
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", cp, "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertTrue(output.contains("{\"id\":7,\"name\":\"Nua\"}"),
                    "Bare sqlite: deve conectar e consultar, veio: '" + output + "'");
            assertFalse(output.contains("No suitable driver"),
                    "O scheme nu deve normalizar, nunca cair em driver ausente: '" + output + "'");
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running JVM class", e);
        }
    }

    // D-DB-NORMALIZE: bare `postgres://` normalizes to `jdbc:postgresql://`
    // (hermetic — closed port, no server): the scheme must be RECOGNIZED
    // (driver routed, real connection failure) — never "No suitable driver".
    @Test
    void jvmBarePostgresNormalizesWithoutServer(@TempDir Path tempDir) throws IOException {
        int port;
        try (java.net.ServerSocket ss = new java.net.ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            port = ss.getLocalPort();
        }
        assumeTrue(!tcpUp(port), "A porta deveria estar fechada: " + port);
        Path source = tempDir.resolve("M.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("postgres://root:pw@127.0.0.1:%d/x")
                println("connected")
            }
            """.formatted(port));
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "JVM compile should succeed: " + result.diagnostics().getDiagnostics());
        String output = runJvmExpectFailure(tempDir.resolve("out"), findClasspathJar("postgresql"));
        assertFalse(output.contains("No suitable driver"),
                "O scheme nu deve normalizar, nunca cair em driver ausente: " + output);
        assertFalse(output.contains("DB001"), "Falha real nao pode virar DB001: " + output);
    }

    // D-DB-NORMALIZE: `db.connect(url, user, pass)` with a bare url —
    // explicit creds stay authoritative (scheme swap only).
    @Test
    void jvmBareMysqlConnect2ExplicitCreds(@TempDir Path tempDir) throws IOException {
        int port;
        try { port = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306")); }
        catch (NumberFormatException e) { port = 13306; }
        assumeTrue(tcpUp(port), "MariaDB not reachable on 127.0.0.1:" + port);
        Path source = tempDir.resolve("M.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("mysql://127.0.0.1:%d/test", "root", "kofpass")
                var rows = db.query(db, "select id, name from nrm where id = ?", 7)
                for (var r in rows) { println(r) }
                db.close(db)
            }
            """.formatted(port));
        runJvmWithExtra(source, tempDir.resolve("out"), findClasspathJar("mariadb"),
                "{\"id\":7,\"name\":\"Nua\"}");
    }

    // D-DB-NORMALIZE (JS): the delegate IS the host JDBC — same bare url.
    @Test
    void jsBareMysqlNormalizesToJdbc(@TempDir Path tempDir) throws IOException {
        int port;
        try { port = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306")); }
        catch (NumberFormatException e) { port = 13306; }
        assumeTrue(tcpUp(port), "MariaDB not reachable on 127.0.0.1:" + port);
        Path source = tempDir.resolve("M.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("mysql://root:kofpass@127.0.0.1:%d/test")
                var rows = db.query(db, "select id, name from nrm where id = ?", 7)
                for (var r in rows) { println(r) }
                db.close(db)
            }
            """.formatted(port));
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JS);
        assertTrue(result.success(), "JS compilation should succeed: "
                + result.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(tempDir.resolve("out/Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        String output = out.toString(java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, ec, "JS exit code should be 0, output: '" + output + "'");
        assertEquals("{\"id\":7,\"name\":\"Nua\"}", output, "JS bare mysql:// output");
    }
}