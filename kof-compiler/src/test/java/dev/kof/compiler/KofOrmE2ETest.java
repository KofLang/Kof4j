package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * kof.orm — o ORM da própria linguagem: {@code entity} define o schema em
 * compile-time (o compilador conhece campos, tipos e constraints — nunca
 * reflection para descobrir schema) e {@code orm} fala SQL via kof.db.
 *
 * Fluxo: entity → record gerado + schema registrado; orm.create/save/find/
 * all/delete/count sobre JDBC (H2 nos testes).
 */
class KofOrmE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String ENTITY_SRC = """
            entity User {
                id: Long generated
                name: String
                email: String unique
                age: Int
            }
            """;

    private String runJvm(Path source, Path outDir, String expected) throws IOException {
        return runJvmWithExtra(source, outDir, null, expected);
    }

    private String runJvmWithExtra(Path source, Path outDir, String extraJar, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        String h2 = findH2Jar();
        String cp = outDir + java.io.File.pathSeparator + h2
                + (extraJar != null ? java.io.File.pathSeparator + extraJar : "");
        java.io.File stderrFile = java.io.File.createTempFile("kof-orm-stderr", ".txt");
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-Dfile.encoding=UTF-8",
                    "-Dstdout.encoding=UTF-8", "--enable-native-access=ALL-UNNAMED",
                    "-cp", cp, "Default.Main");
            // stderr separado: drivers (Mongo/SQLite) podem logar avisos de
            // inicialização no stderr — o stdout é o output do programa Kof;
            // em falha o stderr vai DENTRO da mensagem (Q3: red diagnosticável
            // sem re-rodar) e o arquivo temporário é sempre apagado (sem lixo).
            pb.redirectError(stderrFile);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            if (ec != 0) {
                String stderr = Files.exists(stderrFile.toPath())
                        ? new String(Files.readAllBytes(stderrFile.toPath()),
                                java.nio.charset.StandardCharsets.UTF_8).trim()
                        : "";
                assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'\n"
                        + "stderr: '" + stderr + "'");
            }
            assertEquals(expected, output, "Unexpected output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running JVM class", e);
        } finally {
            Files.deleteIfExists(stderrFile.toPath());
        }
    }

    /** #603: um Path entra em LITERAL de string Kof com `/` (SQLite/JDBC
     *  aceitam `/` no Windows); o path cru com `\` vira `\U`/`\j` (escapes
     *  colapsam pela regra lexical — o arquivo some e o SQLite dá CANTOPEN). */
    private static String kofPath(Path p) {
        return p.toString().replace('\\', '/');
    }

    /** #603: pular honesto se o Postgres NÃO aceitar as credenciais do teste
     *  (tcpOpen sozinho passa com Postgres alheio do dev e vira FATAL de
     *  senha — assumeTrue por credencial real). */
    private static void assumePostgresReady() {
        if (!tcpOpen("localhost", 5432)) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false,
                    "PostgreSQL not reachable (start it: docker run -d -p 5432:5432 -e POSTGRES_PASSWORD=kof postgres)");
        }
        try (java.sql.Connection c = java.sql.DriverManager.getConnection(
                "jdbc:postgresql://localhost:5432/kof_test?user=postgres&password=kof")) {
            try (java.sql.Statement s = c.createStatement()) {
                s.execute("select 1");
            }
        } catch (java.sql.SQLException e) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false,
                    "PostgreSQL reachable but test credentials rejected: " + e.getMessage());
        }
    }

    private static String findH2Jar() {
        return findDriverJar("h2", "H2");
    }

    private static String findDriverJar(String marker, String label) {
        String cp = System.getProperty("java.class.path");
        for (String entry : cp.split(java.io.File.pathSeparator)) {
            if (entry.contains(marker) && entry.endsWith(".jar")) return entry;
        }
        throw new IllegalStateException(label + " jar not found on test classpath");
    }

    @Test
    void createSaveFindAllDelete(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    var db = db.connect("jdbc:h2:mem:orm1;DB_CLOSE_DELAY=-1")
                    orm.create<User>(db)
                    var mel = orm.save(db, User(0, "Mel", "mel@kof.dev", 30))
                    println(mel.id)
                    var u = orm.find<User>(db, mel.id)
                    println(u.name)
                    var all = orm.all<User>(db)
                    println(all.size)
                    println(orm.count<User>(db))
                    orm.delete<User>(db, mel.id)
                    println(orm.count<User>(db))
                    db.close(db)
                }
                """);
        runJvm(source, tempDir.resolve("out"), "1\nMel\n1\n1\n0");
    }

    @Test
    void saveUpdatesExistingRow(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    var db = db.connect("jdbc:h2:mem:orm2;DB_CLOSE_DELAY=-1")
                    orm.create<User>(db)
                    var mel = orm.save(db, User(0, "Mel", "mel@kof.dev", 30))
                    mel = orm.save(db, User(mel.id, "Melissa", "mel@kof.dev", 31))
                    var u = orm.find<User>(db, mel.id)
                    println(u.name + " " + u.age)
                    db.close(db)
                }
                """);
        runJvm(source, tempDir.resolve("out"), "Melissa 31");
    }

    @Test
    void uniqueConstraintRejected(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    var db = db.connect("jdbc:h2:mem:orm3;DB_CLOSE_DELAY=-1")
                    orm.create<User>(db)
                    orm.save(db, User(0, "Mel", "same@kof.dev", 30))
                    try {
                        orm.save(db, User(0, "Kof", "same@kof.dev", 1))
                        println("no-error")
                    } catch (Throwable e) {
                        println("rejected")
                    }
                    db.close(db)
                }
                """);
        runJvm(source, tempDir.resolve("out"), "rejected");
    }

    @Test
    void entityWithoutGeneratedUsesFirstFieldAsPk(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                entity Product {
                    code: String unique
                    price: Double
                }
                main() {
                    var db = db.connect("jdbc:h2:mem:orm4;DB_CLOSE_DELAY=-1")
                    orm.create<Product>(db)
                    orm.save(db, Product("P1", 19.99))
                    var p = orm.find<Product>(db, "P1")
                    println(p.price)
                    db.close(db)
                }
                """);
        runJvm(source, tempDir.resolve("out"), "19.99");
    }

    @Test
    void whereFiltersByField(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + "\n"
                + "                main() {\n"
                + "                    var db = db.connect(\"jdbc:h2:mem:orm6;DB_CLOSE_DELAY=-1\")\n"
                + "                    orm.create<User>(db)\n"
                + "                    orm.save(db, User(0, \"Mel\", \"mel@kof.dev\", 30))\n"
                + "                    orm.save(db, User(0, \"Ana\", \"ana@kof.dev\", 25))\n"
                + "                    var adultos = orm.where<User>(db, \"age\", 30)\n"
                + "                    println(adultos.size)\n"
                + "                    println(adultos.get(0).name)\n"
                + "                    db.close(db)\n"
                + "                }\n"
                + "                ");
        runJvm(source, tempDir.resolve("out"), "1\nMel");
    }

    @Test
    void whereWithOperator(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + "\n"
                + "                main() {\n"
                + "                    var db = db.connect(\"jdbc:h2:mem:orm8;DB_CLOSE_DELAY=-1\")\n"
                + "                    orm.create<User>(db)\n"
                + "                    orm.save(db, User(0, \"Mel\", \"mel@kof.dev\", 30))\n"
                + "                    orm.save(db, User(0, \"Ana\", \"ana@kof.dev\", 25))\n"
                + "                    orm.save(db, User(0, \"Leo\", \"leo@kof.dev\", 40))\n"
                + "                    var adultos = orm.where<User>(db, \"age\", \">\", 25)\n"
                + "                    println(adultos.size)\n"
                + "                    var jovens = orm.where<User>(db, \"age\", \"<=\", 25)\n"
                + "                    println(jovens.size)\n"
                + "                    var ana = orm.where<User>(db, \"name\", \"LIKE\", \"A%\")\n"
                + "                    println(ana.size)\n"
                + "                    println(ana.get(0).name)\n"
                + "                    db.close(db)\n"
                + "                }\n"
                + "                ");
        runJvm(source, tempDir.resolve("out"), "2\n1\n1\nAna");
    }

    @Test
    void whereUnknownColumnIsCompileError(@TempDir Path tempDir) throws IOException {
        // P3-10: coluna que não existe na entidade → falha em compile-time (ORM003)
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + "\n"
                + "                main() {\n"
                + "                    var db = db.connect(\"jdbc:h2:mem:orm12;DB_CLOSE_DELAY=-1\")\n"
                + "                    var r = orm.where<User>(db, \"idade\", 30)\n"
                + "                }\n");
        CompilationResult r = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(r.success(), "coluna inexistente deve falhar na compilação");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "ORM003".equals(d.code())),
                "gap ORM003 esperado: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void queryDslFiltersOrdersAndLimits(@TempDir Path tempDir) throws IOException {
        // ORM001 (nível 3): Query DSL tipada — User.query(db) { where ...; orderBy ...; limit N }
        // O compilador baixa para db.query (SQL preparada em compile-time; valores como binds).
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + "\n"
                + "                main() {\n"
                + "                    var db = db.connect(\"jdbc:h2:mem:dsl1;DB_CLOSE_DELAY=-1\")\n"
                + "                    orm.create<User>(db)\n"
                + "                    orm.save(db, User(0, \"Mel\", \"mel@kof.dev\", 30))\n"
                + "                    orm.save(db, User(0, \"Ana\", \"ana@kof.dev\", 25))\n"
                + "                    orm.save(db, User(0, \"Leo\", \"leo@kof.dev\", 40))\n"
                + "                    var adultos = User.query(db) {\n"
                + "                        where age > 25\n"
                + "                        orderBy name asc\n"
                + "                    }\n"
                + "                    println(adultos.size)\n"
                + "                    println(adultos.get(0).name)\n"
                + "                    println(adultos.get(1).name)\n"
                + "                    var limitado = User.query(db) {\n"
                + "                        where age >= 25\n"
                + "                        limit 1\n"
                + "                    }\n"
                + "                    println(limitado.size)\n"
                + "                    db.close(db)\n"
                + "                }\n");
        runJvm(source, tempDir.resolve("out"), "2\nLeo\nMel\n1");
    }

    @Test
    void queryDslMultipleWhereAnds(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + "\n"
                + "                main() {\n"
                + "                    var db = db.connect(\"jdbc:h2:mem:dsl2;DB_CLOSE_DELAY=-1\")\n"
                + "                    orm.create<User>(db)\n"
                + "                    orm.save(db, User(0, \"Mel\", \"mel@kof.dev\", 30))\n"
                + "                    orm.save(db, User(0, \"Ana\", \"ana@kof.dev\", 25))\n"
                + "                    orm.save(db, User(0, \"Leo\", \"leo@kof.dev\", 40))\n"
                + "                    var faixa = User.query(db) {\n"
                + "                        where age >= 25\n"
                + "                        where age < 40\n"
                + "                        orderBy name asc\n"
                + "                    }\n"
                + "                    println(faixa.size)\n"
                + "                    println(faixa.get(0).name)\n"
                + "                    println(faixa.get(1).name)\n"
                + "                    db.close(db)\n"
                + "                }\n");
        // age >= 25 AND age < 40 → age 25 (Ana) + age 30 (Mel); idade 40 fora
        runJvm(source, tempDir.resolve("out"), "2\nAna\nMel");
    }

    @Test
    void queryDslUnknownColumnIsCompileError(@TempDir Path tempDir) throws IOException {
        // ORM003: coluna inexistente no where do DSL → falha em compile-time
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + "\n"
                + "                main() {\n"
                + "                    var db = db.connect(\"jdbc:h2:mem:dsl3;DB_CLOSE_DELAY=-1\")\n"
                + "                    var r = User.query(db) {\n"
                + "                        where idade > 10\n"
                + "                    }\n"
                + "                }\n");
        CompilationResult r = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(r.success(), "coluna inexistente no where do DSL deve falhar na compilação");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "ORM003".equals(d.code())),
                "gap ORM003 esperado: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void queryDslWhereMustBeComparisonIsCompileError(@TempDir Path tempDir) throws IOException {
        // ORM004: where sem comparação (só uma coluna) → falha em compile-time
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + "\n"
                + "                main() {\n"
                + "                    var db = db.connect(\"jdbc:h2:mem:dsl4;DB_CLOSE_DELAY=-1\")\n"
                + "                    var r = User.query(db) {\n"
                + "                        where age\n"
                + "                    }\n"
                + "                }\n");
        CompilationResult r = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(r.success(), "where sem comparação deve falhar na compilação");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "ORM004".equals(d.code())),
                "gap ORM004 esperado: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void whereDynamicColumnIsAllowed(@TempDir Path tempDir) throws IOException {
        // P3-10: coluna dinâmica (não-literal) não é validada em compile-time
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + "\n"
                + "                main() {\n"
                + "                    var db = db.connect(\"jdbc:h2:mem:orm13;DB_CLOSE_DELAY=-1\")\n"
                + "                    orm.create<User>(db)\n"
                + "                    orm.save(db, User(0, \"Mel\", \"mel@kof.dev\", 30))\n"
                + "                    var col = \"age\"\n"
                + "                    var r = orm.where<User>(db, col, 30)\n"
                + "                    println(r.size)\n"
                + "                    db.close(db)\n"
                + "                }\n");
        runJvm(source, tempDir.resolve("out"), "1");
    }

    @Test
    void saveAllBatch(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + "\n"
                + "                main() {\n"
                + "                    var db = db.connect(\"jdbc:h2:mem:orm9;DB_CLOSE_DELAY=-1\")\n"
                + "                    orm.create<User>(db)\n"
                + "                    var l = new List<User>()\n"
                + "                    l.add(User(0, \"Mel\", \"mel@kof.dev\", 30))\n"
                + "                    l.add(User(0, \"Ana\", \"ana@kof.dev\", 25))\n"
                + "                    orm.saveAll<User>(db, l)\n"
                + "                    println(orm.count<User>(db))\n"
                + "                    var mel = orm.where<User>(db, \"email\", \"mel@kof.dev\")\n"
                + "                    println(mel.size)\n"
                + "                    db.close(db)\n"
                + "                }\n"
                + "                ");
        runJvm(source, tempDir.resolve("out"), "2\n1");
    }

    @Test
    void pagePagination(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + "\n"
                + "                main() {\n"
                + "                    var db = db.connect(\"jdbc:h2:mem:orm10;DB_CLOSE_DELAY=-1\")\n"
                + "                    orm.create<User>(db)\n"
                + "                    var l = new List<User>()\n"
                + "                    l.add(User(0, \"A\", \"a@kof.dev\", 20))\n"
                + "                    l.add(User(0, \"B\", \"b@kof.dev\", 21))\n"
                + "                    l.add(User(0, \"C\", \"c@kof.dev\", 22))\n"
                + "                    orm.saveAll<User>(db, l)\n"
                + "                    var p1 = orm.page<User>(db, 2, 0)\n"
                + "                    println(p1.size)\n"
                + "                    var p2 = orm.page<User>(db, 2, 2)\n"
                + "                    println(p2.size)\n"
                + "                    println(p2.get(0).name)\n"
                + "                    db.close(db)\n"
                + "                }\n"
                + "                ");
        runJvm(source, tempDir.resolve("out"), "2\n1\nC");
    }

    @Test
    void countWhereAndDeleteAll(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + "\n"
                + "                main() {\n"
                + "                    var db = db.connect(\"jdbc:h2:mem:orm11;DB_CLOSE_DELAY=-1\")\n"
                + "                    orm.create<User>(db)\n"
                + "                    orm.save(db, User(0, \"Mel\", \"mel@kof.dev\", 30))\n"
                + "                    orm.save(db, User(0, \"Ana\", \"ana@kof.dev\", 25))\n"
                + "                    println(orm.count<User>(db, \"age\", 30))\n"
                + "                    println(orm.count<User>(db))\n"
                + "                    orm.deleteAll<User>(db)\n"
                + "                    println(orm.count<User>(db))\n"
                + "                    db.close(db)\n"
                + "                }\n"
                + "                ");
        runJvm(source, tempDir.resolve("out"), "1\n2\n0");
    }

    @Test
    void migrateAppliesOnce(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + "\n"
                + "                main() {\n"
                + "                    var db = db.connect(\"jdbc:h2:mem:orm7;DB_CLOSE_DELAY=-1\")\n"
                + "                    orm.create<User>(db)\n"
                + "                    orm.migrate(db, \"add_city\", \"ALTER TABLE \\\"user\\\" ADD COLUMN city VARCHAR(255)\")\n"
                + "                    orm.migrate(db, \"add_city\", \"ALTER TABLE \\\"user\\\" ADD COLUMN city VARCHAR(255)\")\n"
                + "                    orm.migrate(db, \"v2\", \"ALTER TABLE \\\"user\\\" ADD COLUMN country VARCHAR(255)\")\n"
                + "                    println(\"ok\")\n"
                + "                    db.close(db)\n"
                + "                }\n"
                + "                ");
        runJvm(source, tempDir.resolve("out"), "ok");
    }

    @Test
    void sqliteDialectViaJdbc(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    var db = db.connect("jdbc:sqlite:%s")
                    orm.create<User>(db)
                    var mel = orm.save(db, User(0, "Mel", "mel@kof.dev", 30))
                    var u = orm.find<User>(db, mel.id)
                    println(u.name)
                    println(orm.count<User>(db))
                    db.close(db)
                }
                """.formatted(kofPath(tempDir.resolve("orm.db"))));
        runJvmWithExtra(source, tempDir.resolve("out"), findDriverJar("sqlite-jdbc", "SQLite"),
                "Mel\n1");
    }

    @Test
    void mongoCrud(@TempDir Path tempDir) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(mongoAvailable(),
                "MongoDB not reachable on localhost:27017 (start it: docker run -d -p 27017:27017 mongo:7)");
        int port = 27017;
        {
            Path source = tempDir.resolve("Main.kf");
            Files.writeString(source, """
                entity User {
                    id: Long
                    name: String
                    email: String unique
                    age: Int
                }
                main() {
                    var db = db.connect("mongodb://localhost:%d/kof_test_%d")
                    orm.create<User>(db)
                    orm.save(db, User(1, "Mel", "mel@kof.dev", 30))
                    var u = orm.find<User>(db, 1)
                    println(u.name)
                    var adultos = orm.where<User>(db, "age", 30)
                    println(adultos.size)
                    println(orm.count<User>(db))
                    var l = new List<User>()
                    l.add(User(2, "Ana", "ana@kof.dev", 25))
                    l.add(User(3, "Leo", "leo@kof.dev", 40))
                    orm.saveAll<User>(db, l)
                    println(orm.count<User>(db))
                    var jovens = orm.where<User>(db, "age", "<=", 25)
                    println(jovens.size)
                    var pg = orm.page<User>(db, 2, 1)
                    println(pg.size)
                    var leo = orm.where<User>(db, "name", "LIKE", "L%%")
                    println(leo.size)
                    println(orm.count<User>(db, "age", 25))
                    orm.deleteAll<User>(db)
                    println(orm.count<User>(db))
                    db.close(db)
                }
                """.formatted(port, System.nanoTime()));
            runJvmWithExtra(source, tempDir.resolve("out"), mongoClasspath(), "Mel\n1\n1\n3\n1\n2\n1\n1\n0");
        }
    }

    private static boolean mongoAvailable() {
        try (java.net.Socket s = new java.net.Socket("localhost", 27017)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean tcpOpen(String host, int port) {
        try (java.net.Socket s = new java.net.Socket(host, port)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void mariadbCrud(@TempDir Path tempDir) throws Exception {
        int dbPort;
        try { dbPort = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "3306")); }
        catch (NumberFormatException e) { dbPort = 3306; }
        org.junit.jupiter.api.Assumptions.assumeTrue(tcpOpen("127.0.0.1", dbPort),
                "MariaDB not reachable on 127.0.0.1:" + dbPort + " (docker run -d -p 3306:3306 -e MARIADB_ROOT_PASSWORD=kof mariadb:11)");
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + "\n"
                + "                main() {\n"
                + "                    var db = db.connect(\"jdbc:mariadb://127.0.0.1:" + dbPort + "/kof_test_" + System.nanoTime() + "?user=root&password=kof&allowMultiQueries=true&createDatabaseIfNotExist=true\")\n"
                + "                    orm.create<User>(db)\n"
                + "                    orm.saveAll<User>(db, new List<User>())\n"
                + "                    orm.save(db, User(0, \"Mel\", \"mel@kof.dev\", 30))\n"
                + "                    orm.save(db, User(0, \"Ana\", \"ana@kof.dev\", 25))\n"
                + "                    var mel = orm.find<User>(db, 1)\n"
                + "                    println(mel.name)\n"
                + "                    println(orm.count<User>(db))\n"
                + "                    var velhos = orm.where<User>(db, \"age\", \">\", 26)\n"
                + "                    println(velhos.size)\n"
                + "                    orm.deleteAll<User>(db)\n"
                + "                    println(orm.count<User>(db))\n"
                + "                    db.close(db)\n"
                + "                }\n"
                + "                ");
        runJvmWithExtra(source, tempDir.resolve("out"), findDriverJar("mariadb", "MariaDB"), "Mel\n2\n1\n0");
    }

    @Test
    void deleteAllMysqlNativeMatchesJvm(@TempDir Path tempDir) throws Exception {
        // F2d1 (D-DB-GAPS DB-3): orm.deleteAll sobre o wire MySQL no Native
        // x86-64 — espelho do host (kof_orm_q backtick + kof_db_execute >= 0).
        // JVM dirige a MESMA semantica via JDBC; a prova e byte JVM==Native.
        int port;
        try { port = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306")); }
        catch (NumberFormatException e) { port = 13306; }
        org.junit.jupiter.api.Assumptions.assumeTrue(tcpOpen("127.0.0.1", port),
                "MySQL/MariaDB not reachable on 127.0.0.1:" + port);

        String body = """
                    db.execute(db, "drop table if exists `user`")
                    db.execute(db, "create table `user` (id int, name varchar(50), email varchar(80), age int)")
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 1, "Mel", "m@kof.dev", 30)
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 2, "Ana", "a@kof.dev", 25)
                    println(orm.deleteAll<User>(db))
                    println(orm.deleteAll<User>(db))
                    var rows = db.query(db, "select count(*) as c from `user`")
                    for (var r in rows) {
                        println(r)
                    }
                    db.close(db)
                }
                """;
        String expected = "true\ntrue\n{\"c\":0}";

        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"jdbc:mariadb://127.0.0.1:" + port
                   + "/test?user=root&password=kofpass&allowMultiQueries=true\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("mariadb", "MariaDB"), expected);

        Path nativeSource = tempDir.resolve("NativeMain.kf");
        Files.writeString(nativeSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"mysql://root:kofpass@127.0.0.1:" + port + "/test\")\n")
                + body);
        CompilationResult nativeResult = driver.compile(nativeSource, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), "Native deve compilar orm.deleteAll no mysql (F2d1): "
                + nativeResult.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String out = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = proc.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out, "paridade byte JVM==Native (deleteAll mysql; idempotente + count 0)");
    }

    @Test
    void countMysqlNativeMatchesJvm(@TempDir Path tempDir) throws Exception {
        // F2d2 (D-DB-GAPS DB-3): orm.count sobre o wire MySQL no Native x86-64
        // — COM_QUERY + leitura do resultset (kof_db_mysql_next/lenenc) com
        // parse bounded; host via JDBC; a prova e byte JVM==Native.
        int port;
        try { port = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306")); }
        catch (NumberFormatException e) { port = 13306; }
        org.junit.jupiter.api.Assumptions.assumeTrue(tcpOpen("127.0.0.1", port),
                "MySQL/MariaDB not reachable on 127.0.0.1:" + port);

        String body = """
                    db.execute(db, "drop table if exists `user`")
                    db.execute(db, "create table `user` (id int, name varchar(50), email varchar(80), age int)")
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 1, "Mel", "m@kof.dev", 30)
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 2, "Ana", "a@kof.dev", 25)
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 3, "Bia", "b@kof.dev", 41)
                    println(orm.count<User>(db))
                    db.execute(db, "delete from `user` where id = ?", 2)
                    println(orm.count<User>(db))
                    orm.deleteAll<User>(db)
                    println(orm.count<User>(db))
                    db.close(db)
                }
                """;
        String expected = "3\n2\n0";

        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"jdbc:mariadb://127.0.0.1:" + port
                   + "/test?user=root&password=kofpass&allowMultiQueries=true\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("mariadb", "MariaDB"), expected);

        Path nativeSource = tempDir.resolve("NativeMain.kf");
        Files.writeString(nativeSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"mysql://root:kofpass@127.0.0.1:" + port + "/test\")\n")
                + body);
        CompilationResult nativeResult = driver.compile(nativeSource, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), "Native deve compilar orm.count no mysql (F2d2): "
                + nativeResult.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String out = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = proc.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out, "paridade byte JVM==Native (count mysql; 3 -> 2 apos delete -> 0 apos deleteAll)");
    }

    @Test
    void deleteMysqlNativeMatchesJvm(@TempDir Path tempDir) throws Exception {
        // F2d3a (D-DB-GAPS DB-3): orm.delete sobre o wire MySQL no Native
        // x86-64 — DELETE FROM `t` WHERE `pk` = ? via kof_db_execute1
        // (prepared binario); host: execute1 >= 0 (miss tambem true).
        int port;
        try { port = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306")); }
        catch (NumberFormatException e) { port = 13306; }
        org.junit.jupiter.api.Assumptions.assumeTrue(tcpOpen("127.0.0.1", port),
                "MySQL/MariaDB not reachable on 127.0.0.1:" + port);

        String body = """
                    db.execute(db, "drop table if exists `user`")
                    db.execute(db, "create table `user` (id int, name varchar(50), email varchar(80), age int)")
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 1, "Mel", "m@kof.dev", 30)
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 2, "Ana", "a@kof.dev", 25)
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 3, "Bia", "b@kof.dev", 41)
                    println(orm.delete<User>(db, 2))
                    println(orm.count<User>(db))
                    println(orm.delete<User>(db, 999))
                    println(orm.count<User>(db))
                    db.close(db)
                }
                """;
        String expected = "true\n2\ntrue\n2";

        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"jdbc:mariadb://127.0.0.1:" + port
                   + "/test?user=root&password=kofpass&allowMultiQueries=true\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("mariadb", "MariaDB"), expected);

        Path nativeSource = tempDir.resolve("NativeMain.kf");
        Files.writeString(nativeSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"mysql://root:kofpass@127.0.0.1:" + port + "/test\")\n")
                + body);
        CompilationResult nativeResult = driver.compile(nativeSource, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), "Native deve compilar orm.delete no mysql (F2d3a): "
                + nativeResult.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String out = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = proc.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out, "paridade byte JVM==Native (delete mysql; hit true, miss true, count 2)");
    }

    @Test
    void findMysqlNativeMatchesJvm(@TempDir Path tempDir) throws Exception {
        // F2d3b (D-DB-GAPS DB-3): orm.find row-object sobre o wire MySQL no
        // Native x86-64 — SELECT * FROM `t` WHERE `pk` = ? via COM_QUERY,
        // colunas casadas por NOME e record construido por kof_orm_ctors;
        // miss -> null. Host via JDBC; a prova e byte JVM==Native.
        int port;
        try { port = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306")); }
        catch (NumberFormatException e) { port = 13306; }
        org.junit.jupiter.api.Assumptions.assumeTrue(tcpOpen("127.0.0.1", port),
                "MySQL/MariaDB not reachable on 127.0.0.1:" + port);

        String body = """
                    db.execute(db, "drop table if exists `user`")
                    db.execute(db, "create table `user` (id int, name varchar(50), email varchar(80), age int)")
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 1, "Mel", "m@kof.dev", 30)
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 2, "Ana", "a@kof.dev", 25)
                    var mel = orm.find<User>(db, 1)
                    println(mel.id)
                    println(mel.name)
                    println(mel.email)
                    println(mel.age)
                    var g = orm.find<User>(db, 999)
                    if (g == null) {
                        println("null")
                    } else {
                        println("hit")
                    }
                    var ana = orm.find<User>(db, 2)
                    println(ana.name + "/" + ana.age)
                    var klong: Long = 2
                    var ana2 = orm.find<User>(db, klong)
                    println(ana2.name + "/" + ana2.age)
                    var esk = orm.find<User>(db, "1")
                    println(esk.name)
                    db.close(db)
                }
                """;
        String expected = "1\nMel\nm@kof.dev\n30\nnull\nAna/25\nAna/25\nMel";

        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"jdbc:mariadb://127.0.0.1:" + port
                   + "/test?user=root&password=kofpass&allowMultiQueries=true\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("mariadb", "MariaDB"), expected);

        Path nativeSource = tempDir.resolve("NativeMain.kf");
        Files.writeString(nativeSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"mysql://root:kofpass@127.0.0.1:" + port + "/test\")\n")
                + body);
        CompilationResult nativeResult = driver.compile(nativeSource, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), "Native deve compilar orm.find no mysql (F2d3b): "
                + nativeResult.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String out = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = proc.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out, "paridade byte JVM==Native (find mysql; hit 4 campos, miss null, 2a linha)");
    }

    @Test
    void allMysqlNativeMatchesJvm(@TempDir Path tempDir) throws Exception {
        // F2d4a (D-DB-GAPS DB-3): orm.all row-object sobre o wire MySQL no
        // Native x86-64 — SELECT * FROM `t` via COM_QUERY, colunas casadas
        // por NOME, um record por linha acumulado em kof_list_new/add; lista
        // VAZIA (nunca null) quando nao ha linhas. Host via JDBC; a prova e
        // byte JVM==Native.
        int port;
        try { port = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306")); }
        catch (NumberFormatException e) { port = 13306; }
        org.junit.jupiter.api.Assumptions.assumeTrue(tcpOpen("127.0.0.1", port),
                "MySQL/MariaDB not reachable on 127.0.0.1:" + port);

        String body = """
                    db.execute(db, "drop table if exists `user`")
                    db.execute(db, "create table `user` (id int primary key, name varchar(50), email varchar(80), age int)")
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 1, "Mel", "m@kof.dev", 30)
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 2, "Ana", "a@kof.dev", 25)
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 3, "Leo", "l@kof.dev", 40)
                    var l = orm.all<User>(db)
                    println(l.size)
                    for (var u in l) {
                        println(u.name + "/" + u.age)
                    }
                    db.execute(db, "delete from `user` where id = ?", 2)
                    var l2 = orm.all<User>(db)
                    println(l2.size)
                    for (var u in l2) {
                        println(u.name)
                    }
                    orm.deleteAll<User>(db)
                    var l3 = orm.all<User>(db)
                    println(l3.size)
                    db.close(db)
                }
                """;
        String expected = "3\nMel/30\nAna/25\nLeo/40\n2\nMel\nLeo\n0";

        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"jdbc:mariadb://127.0.0.1:" + port
                   + "/test?user=root&password=kofpass&allowMultiQueries=true\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("mariadb", "MariaDB"), expected);

        Path nativeSource = tempDir.resolve("NativeMain.kf");
        Files.writeString(nativeSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"mysql://root:kofpass@127.0.0.1:" + port + "/test\")\n")
                + body);
        CompilationResult nativeResult = driver.compile(nativeSource, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), "Native deve compilar orm.all no mysql (F2d4a): "
                + nativeResult.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String out = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = proc.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out, "paridade byte JVM==Native (all mysql; 3 linhas, delete 1, lista vazia)");
    }

    @Test
    void whereMysqlNativeMatchesJvm(@TempDir Path tempDir) throws Exception {
        // F2d4b (D-DB-GAPS DB-3): orm.where/where_op row-object sobre o wire
        // MySQL no Native x86-64 — SELECT * FROM `t` WHERE `f` <op> ? via
        // COM_QUERY com o value como literal (.Lorm_key_lit) e a whitelist
        // do op do host (==->=, LIKE case-sensitive, resto throw
        // "ORM operator not allowed: <op>"); lista vazia se nada casar.
        // Host via JDBC; a prova e byte JVM==Native.
        int port;
        try { port = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306")); }
        catch (NumberFormatException e) { port = 13306; }
        org.junit.jupiter.api.Assumptions.assumeTrue(tcpOpen("127.0.0.1", port),
                "MySQL/MariaDB not reachable on 127.0.0.1:" + port);

        String body = """
                    db.execute(db, "drop table if exists `user`")
                    db.execute(db, "create table `user` (id int primary key, name varchar(50), email varchar(80), age int)")
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 1, "Mel", "m@kof.dev", 30)
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 2, "Ana", "a@kof.dev", 25)
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 3, "Bia", "b@kof.dev", 40)
                    var w1 = orm.where<User>(db, "age", 30)
                    println(w1.size)
                    for (var u in w1) { println(u.name + "/" + u.age) }
                    var w2 = orm.where<User>(db, "age", 99)
                    println(w2.size)
                    var w3 = orm.where<User>(db, "age", ">", 25)
                    println(w3.size)
                    for (var u in w3) { println(u.name) }
                    var w4 = orm.where<User>(db, "name", "LIKE", "A%")
                    println(w4.size)
                    for (var u in w4) { println(u.name) }
                    var w5 = orm.where<User>(db, "age", "==", 25)
                    println(w5.size)
                    var w6 = orm.where<User>(db, "name", "!=", "Mel")
                    println(w6.size)
                    for (var u in w6) { println(u.name) }
                    try {
                        orm.where<User>(db, "age", "DROP TABLE user", 1)
                        println("no-throw")
                    } catch (String e) {
                        println("throw:[" + e + "]")
                    }
                    var w7 = orm.where<User>(db, "age", 30)
                    println(w7.size)
                    db.close(db)
                }
                """;
        String expected = "1\nMel/30\n0\n2\nMel\nBia\n1\nAna\n1\n2\nAna\nBia\n"
                + "throw:[ORM operator not allowed: DROP TABLE user]\n1";

        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"jdbc:mariadb://127.0.0.1:" + port
                   + "/test?user=root&password=kofpass&allowMultiQueries=true\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("mariadb", "MariaDB"), expected);

        Path nativeSource = tempDir.resolve("NativeMain.kf");
        Files.writeString(nativeSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"mysql://root:kofpass@127.0.0.1:" + port + "/test\")\n")
                + body);
        CompilationResult nativeResult = driver.compile(nativeSource, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), "Native deve compilar orm.where no mysql (F2d4b): "
                + nativeResult.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String out = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = proc.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out, "paridade byte JVM==Native (where mysql; =/>/LIKE/==/!=, throw exato, vazio=0)");
    }

    @Test
    void pageMysqlNativeMatchesJvm(@TempDir Path tempDir) throws Exception {
        // F2d4c (D-DB-GAPS DB-3): orm.page row-object sobre o wire MySQL no
        // Native x86-64 — SELECT * FROM `t` LIMIT <lim> OFFSET <off> via
        // COM_QUERY, lim/off convertidos como o host (((Number)x).intValue()
        // — box Long incluso) e pagina vazia = lista VAZIA. Host via JDBC;
        // a prova e byte JVM==Native.
        int port;
        try { port = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306")); }
        catch (NumberFormatException e) { port = 13306; }
        org.junit.jupiter.api.Assumptions.assumeTrue(tcpOpen("127.0.0.1", port),
                "MySQL/MariaDB not reachable on 127.0.0.1:" + port);

        String body = """
                    db.execute(db, "drop table if exists `user`")
                    db.execute(db, "create table `user` (id int primary key, name varchar(50), email varchar(80), age int)")
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 1, "Mel", "m@kof.dev", 30)
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 2, "Ana", "a@kof.dev", 25)
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 3, "Bia", "b@kof.dev", 40)
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 4, "Leo", "l@kof.dev", 35)
                    var p1 = orm.page<User>(db, 2, 0)
                    println(p1.size)
                    for (var u in p1) { println(u.name) }
                    var p2 = orm.page<User>(db, 2, 2)
                    println(p2.size)
                    for (var u in p2) { println(u.name) }
                    var p3 = orm.page<User>(db, 0, 0)
                    println(p3.size)
                    var p4 = orm.page<User>(db, 10, 99)
                    println(p4.size)
                    var p5 = orm.page<User>(db, 3, 1)
                    println(p5.size)
                    for (var u in p5) { println(u.name + "/" + u.age) }
                    var lim: Long = 2
                    var off: Long = 0
                    var p6 = orm.page<User>(db, lim, off)
                    println(p6.size)
                    for (var u in p6) { println(u.name) }
                    db.close(db)
                }
                """;
        String expected = "2\nMel\nAna\n2\nBia\nLeo\n0\n0\n3\nAna/25\nBia/40\nLeo/35\n2\nMel\nAna";

        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"jdbc:mariadb://127.0.0.1:" + port
                   + "/test?user=root&password=kofpass&allowMultiQueries=true\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("mariadb", "MariaDB"), expected);

        Path nativeSource = tempDir.resolve("NativeMain.kf");
        Files.writeString(nativeSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"mysql://root:kofpass@127.0.0.1:" + port + "/test\")\n")
                + body);
        CompilationResult nativeResult = driver.compile(nativeSource, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), "Native deve compilar orm.page no mysql (F2d4c): "
                + nativeResult.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String out = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = proc.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out, "paridade byte JVM==Native (page mysql; 2 paginas, vazias, Long, parcial)");
    }

    @Test
    void saveAllMysqlNativeMatchesJvm(@TempDir Path tempDir) throws Exception {
        // F2d4d (D-DB-GAPS DB-3): orm.saveAll row-object sobre o wire MySQL no
        // Native x86-64 — loop por item espelhando as 3 saidas do host
        // (pk 0/null -> INSERT sem a PK, pk != 0 -> UPDATE, 0 linhas ->
        // INSERT de todas as colunas), COM_QUERY com os valores como literal
        // do typeCode; retorno do save descartado como no host. Prova byte
        // JVM==Native (host via JDBC).
        int port;
        try { port = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306")); }
        catch (NumberFormatException e) { port = 13306; }
        org.junit.jupiter.api.Assumptions.assumeTrue(tcpOpen("127.0.0.1", port),
                "MySQL/MariaDB not reachable on 127.0.0.1:" + port);

        String body = """
                    db.execute(db, "drop table if exists `user`")
                    db.execute(db, "create table `user` (id int primary key auto_increment, name varchar(50), email varchar(80), age int)")
                    var l1in = new List<User>()
                    l1in.add(User(0, "Mel", "m@kof.dev", 30))
                    l1in.add(User(0, "Ana", "a@kof.dev", 25))
                    var ok1 = orm.saveAll<User>(db, l1in)
                    println(ok1)
                    var l1 = orm.all<User>(db)
                    println(l1.size)
                    for (var u in l1) { println(u.id + "/" + u.name + "/" + u.age) }
                    var l2in = new List<User>()
                    l2in.add(User(1, "Mel", "mel@kof.dev", 31))
                    l2in.add(User(2, "Ana", "a@kof.dev", 25))
                    l2in.add(User(0, "Leo", "l@kof.dev", 40))
                    var ok2 = orm.saveAll<User>(db, l2in)
                    println(ok2)
                    var l2 = orm.all<User>(db)
                    println(l2.size)
                    for (var u in l2) { println(u.id + "/" + u.name + "/" + u.email + "/" + u.age) }
                    var l3in = new List<User>()
                    var ok3 = orm.saveAll<User>(db, l3in)
                    println(ok3)
                    orm.deleteAll<User>(db)
                    var l3 = orm.all<User>(db)
                    println(l3.size)
                    db.close(db)
                }
                """;
        String expected = "true\n2\n1/Mel/30\n2/Ana/25\ntrue\n3\n"
                + "1/Mel/mel@kof.dev/31\n2/Ana/a@kof.dev/25\n3/Leo/l@kof.dev/40\ntrue\n0";

        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"jdbc:mariadb://127.0.0.1:" + port
                   + "/test?user=root&password=kofpass&allowMultiQueries=true\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("mariadb", "MariaDB"), expected);

        Path nativeSource = tempDir.resolve("NativeMain.kf");
        Files.writeString(nativeSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"mysql://root:kofpass@127.0.0.1:" + port + "/test\")\n")
                + body);
        CompilationResult nativeResult = driver.compile(nativeSource, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), "Native deve compilar orm.saveAll no mysql (F2d4d): "
                + nativeResult.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String out = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = proc.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out, "paridade byte JVM==Native (saveAll mysql; pk gerada, upsert, lista vazia)");
    }

    @Test
    void saveMysqlNativeMatchesJvm(@TempDir Path tempDir) throws Exception {
        // F2d5 (D-DB-GAPS DB-3): orm.save row-object sobre o wire MySQL no
        // Native x86-64 — as 3 saidas do host (pk 0/null -> INSERT sem a PK
        // + SELECT LAST_INSERT_ID() + NOVA instancia com a pk patchada;
        // pk != 0 -> UPDATE hit devolve o MESMO ponteiro; UPDATE miss ->
        // INSERT de todas as colunas). Prova byte JVM==Native (host JDBC).
        int port;
        try { port = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306")); }
        catch (NumberFormatException e) { port = 13306; }
        org.junit.jupiter.api.Assumptions.assumeTrue(tcpOpen("127.0.0.1", port),
                "MySQL/MariaDB not reachable on 127.0.0.1:" + port);

        String body = """
                    db.execute(db, "drop table if exists `user`")
                    db.execute(db, "create table `user` (id int primary key auto_increment, name varchar(50), email varchar(80), age int)")
                    var u1 = orm.save(db, User(0, "Mel", "m@kof.dev", 30))
                    println(u1.id)
                    println(u1.name)
                    println(orm.count<User>(db))
                    var u2 = orm.save(db, User(1, "Mel", "mel@kof.dev", 31))
                    println(u2.id)
                    println(u2.name)
                    println(orm.count<User>(db))
                    var u3 = orm.save(db, User(0, "Ana", "a@kof.dev", 25))
                    println(u3.id)
                    println(orm.count<User>(db))
                    var u4 = orm.save(db, User(7, "Zoe", "z@kof.dev", 22))
                    println(u4.id)
                    println(orm.count<User>(db))
                    var l = orm.all<User>(db)
                    for (var x in l) { println(x.id + "/" + x.name + "/" + x.email + "/" + x.age) }
                    db.close(db)
                }
                """;
        String expected = "1\nMel\n1\n1\nMel\n1\n2\n2\n7\n3\n"
                + "1/Mel/mel@kof.dev/31\n2/Ana/a@kof.dev/25\n7/Zoe/z@kof.dev/22";

        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"jdbc:mariadb://127.0.0.1:" + port
                   + "/test?user=root&password=kofpass&allowMultiQueries=true\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("mariadb", "MariaDB"), expected);

        Path nativeSource = tempDir.resolve("NativeMain.kf");
        Files.writeString(nativeSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"mysql://root:kofpass@127.0.0.1:" + port + "/test\")\n")
                + body);
        CompilationResult nativeResult = driver.compile(nativeSource, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), "Native deve compilar orm.save no mysql (F2d5): "
                + nativeResult.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String out = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = proc.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out, "paridade byte JVM==Native (save mysql; pk gerada patchada, UPDATE hit, upsert)");
    }

    @Test
    void countWhereMysqlNativeMatchesJvm(@TempDir Path tempDir) throws Exception {
        // F2d6 (D-DB-GAPS DB-3): orm.count com filtro sobre o wire MySQL no
        // Native x86-64 — SELECT COUNT(*) FROM `t` WHERE `f` = <literal>
        // (backtick do host; value por box §284/KofString/null em literal).
        // Q3: string, int, ausente, injecao e negativo. Prova byte
        // JVM==Native (host via JDBC).
        int port;
        try { port = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306")); }
        catch (NumberFormatException e) { port = 13306; }
        org.junit.jupiter.api.Assumptions.assumeTrue(tcpOpen("127.0.0.1", port),
                "MySQL/MariaDB not reachable on 127.0.0.1:" + port);

        String body = """
                    db.execute(db, "drop table if exists `user`")
                    db.execute(db, "create table `user` (id int primary key, name varchar(50), email varchar(80), age int)")
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 1, "Mel", "m@kof.dev", 30)
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 2, "Ana", "a@kof.dev", 41)
                    println(orm.count<User>(db, "name", "Mel"))
                    println(orm.count<User>(db, "age", 30))
                    println(orm.count<User>(db, "email", "nope@x.io"))
                    println(orm.count<User>(db, "name", "x' OR 1=1 --"))
                    println(orm.count<User>(db, "age", -7))
                    println(orm.count<User>(db, "age", 41))
                    db.close(db)
                }
                """;
        String expected = "1\n1\n0\n0\n0\n1";

        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"jdbc:mariadb://127.0.0.1:" + port
                   + "/test?user=root&password=kofpass&allowMultiQueries=true\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("mariadb", "MariaDB"), expected);

        Path nativeSource = tempDir.resolve("NativeMain.kf");
        Files.writeString(nativeSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"mysql://root:kofpass@127.0.0.1:" + port + "/test\")\n")
                + body);
        CompilationResult nativeResult = driver.compile(nativeSource, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), "Native deve compilar orm.count filtrado no mysql (F2d6): "
                + nativeResult.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String out = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = proc.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out, "paridade byte JVM==Native (count_where mysql; string, int, miss, injecao, negativo)");
    }

    @Test
    void createMysqlNativeMatchesJvm(@TempDir Path tempDir) throws Exception {
        // F2d7 (D-DB-GAPS DB-3): orm.create sobre o wire MySQL no Native
        // x86-64 — dialeto backtick + tipos do host (INT/BIGINT/TINYINT(1)/
        // VARCHAR(255); generated = BIGINT AUTO_INCREMENT PRIMARY KEY).
        // Prova de dialeto: o save gera o pk (LAST_INSERT_ID) e o UNIQUE de
        // email aceita a 1a linha. Q3: create 2x (IF NOT EXISTS) e tipos.
        int port;
        try { port = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306")); }
        catch (NumberFormatException e) { port = 13306; }
        org.junit.jupiter.api.Assumptions.assumeTrue(tcpOpen("127.0.0.1", port),
                "MySQL/MariaDB not reachable on 127.0.0.1:" + port);

        String body = """
                    db.execute(db, "drop table if exists `user`")
                    println(orm.create<User>(db))
                    println(orm.create<User>(db))
                    var u = orm.save(db, User(0, "Mel", "m@kof.dev", 30))
                    println(u.id)
                    println(orm.count<User>(db, "name", "Mel"))
                    println(orm.count<User>(db, "email", "m@kof.dev"))
                    db.close(db)
                }
                """;
        String expected = "true\ntrue\n1\n1\n1";

        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"jdbc:mariadb://127.0.0.1:" + port
                   + "/test?user=root&password=kofpass&allowMultiQueries=true\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("mariadb", "MariaDB"), expected);

        Path nativeSource = tempDir.resolve("NativeMain.kf");
        Files.writeString(nativeSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"mysql://root:kofpass@127.0.0.1:" + port + "/test\")\n")
                + body);
        CompilationResult nativeResult = driver.compile(nativeSource, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), "Native deve compilar orm.create no mysql (F2d7): "
                + nativeResult.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String out = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = proc.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out, "paridade byte JVM==Native (create mysql; AUTO_INCREMENT + IF NOT EXISTS)");
    }

    @Test
    void migrateMysqlNativeMatchesJvm(@TempDir Path tempDir) throws Exception {
        // F2d7 (D-DB-GAPS DB-3): orm.migrate sobre o wire MySQL no Native
        // x86-64 — tabela kof_migrations com backtick, aplica uma unica vez
        // (2a chamada com SQL invalido prova que nao reexecuta), ALTER via
        // dialeto do host e INSERT do historico. Prova forte: a coluna
        // extra existe (insert de 3 colunas) mesmo depois de uma migracao
        // "drop column extra" que NAO pode rodar (ja aplicada).
        int port;
        try { port = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306")); }
        catch (NumberFormatException e) { port = 13306; }
        org.junit.jupiter.api.Assumptions.assumeTrue(tcpOpen("127.0.0.1", port),
                "MySQL/MariaDB not reachable on 127.0.0.1:" + port);

        String body = """
                    db.execute(db, "drop table if exists `kof_migrations`")
                    db.execute(db, "drop table if exists `widget`")
                    println(orm.migrate(db, "001-widget", "create table `widget` (id int primary key, label varchar(50))"))
                    println(orm.migrate(db, "001-widget", "this is not valid sql -- nao reexecuta"))
                    db.execute(db, "insert into `widget` values (1, 'a')")
                    println(db.query(db, "select count(*) as n from `widget`").get(0))
                    println(orm.migrate(db, "002-extra", "alter table `widget` add column extra int"))
                    println(orm.migrate(db, "002-extra", "alter table `widget` drop column extra"))
                    db.execute(db, "insert into `widget` values (2, 'b', 7)")
                    println(db.query(db, "select count(*) as n from `kof_migrations`").get(0))
                    db.close(db)
                }
                """;
        String expected = "true\ntrue\n{\"n\":1}\ntrue\ntrue\n{\"n\":2}";

        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"jdbc:mariadb://127.0.0.1:" + port
                   + "/test?user=root&password=kofpass&allowMultiQueries=true\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("mariadb", "MariaDB"), expected);

        Path nativeSource = tempDir.resolve("NativeMain.kf");
        Files.writeString(nativeSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"mysql://root:kofpass@127.0.0.1:" + port + "/test\")\n")
                + body);
        CompilationResult nativeResult = driver.compile(nativeSource, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), "Native deve compilar orm.migrate no mysql (F2d7): "
                + nativeResult.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String out = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = proc.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out, "paridade byte JVM==Native (migrate mysql; aplica uma vez, historico)");
    }

    @Test
    void postgresCrud(@TempDir Path tempDir) throws Exception {
        assumePostgresReady();
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + "\n"
                + "                main() {\n"
+ "                    var db = db.connect(\"jdbc:postgresql://localhost:5432/kof_test?user=postgres&password=kof\")\n"
+ "                    db.execute(db, \"DROP TABLE IF EXISTS \\\"user\\\"\")\n"
                + "                    orm.create<User>(db)\n"
                + "                    orm.saveAll<User>(db, new List<User>())\n"
                + "                    orm.save(db, User(0, \"Mel\", \"mel@kof.dev\", 30))\n"
                + "                    orm.save(db, User(0, \"Ana\", \"ana@kof.dev\", 25))\n"
                + "                    var mel = orm.find<User>(db, 1)\n"
                + "                    println(mel.name)\n"
                + "                    println(orm.count<User>(db))\n"
                + "                    var velhos = orm.where<User>(db, \"age\", \">\", 26)\n"
                + "                    println(velhos.size)\n"
                + "                    orm.deleteAll<User>(db)\n"
                + "                    println(orm.count<User>(db))\n"
                + "                    db.close(db)\n"
                + "                }\n"
                + "                ");
        runJvmWithExtra(source, tempDir.resolve("out"), findDriverJar("postgresql", "PostgreSQL"), "Mel\n2\n1\n0");
    }

    private static String mongoClasspath() {
        StringBuilder cp = new StringBuilder();
        String classpath = System.getProperty("java.class.path");
        for (String entry : classpath.split(java.io.File.pathSeparator)) {
            if ((entry.contains("mongodb") || entry.contains("bson") || entry.contains("slf4j"))
                    && entry.endsWith(".jar")) {
                if (cp.length() > 0) cp.append(java.io.File.pathSeparator);
                cp.append(entry);
            }
        }
        return cp.toString();
    }

    @Test
    void saveCompilesOnNativeAndJs(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    var db = db.connect("sqlite:%s")
                    orm.save(db, User(1, "Mel", "m@kof.dev", 30))
                }
                """.formatted(kofPath(tempDir.resolve("orm-test.db"))));
        // F2a (20/09): kof_orm_save REAL no Native x86-64 — compila limpo.
        CompilationResult nativeResult = driver.compile(source, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(),
                "Native should now compile orm.save (F2a): " + nativeResult.diagnostics().getDiagnostics());

        // ORM001 (18/09): JS suportado via KofJsOrmBridge — compila limpo.
        CompilationResult jsResult = driver.compile(source, tempDir.resolve("js-out"), Target.JS);
        assertTrue(jsResult.success(),
                "JS should now compile orm.* (ORM001 closed): " + jsResult.diagnostics().getDiagnostics());
    }

    // ── D-DB-GAPS F2a (20/09): kof_orm_save REAL no Native x86-64 ──

    @Test
    void findPreservesSavedBoolTrueRegression397(@TempDir Path tempDir) throws Exception {
        // §397: gravar==ler no Bool (JVM le INTEGER !=0 apos o fix do binder;
        // a asm de leitura do find (F2b) seguiu o oracle pre-fix e le "true"
        // so do texto -- este teste trava a PARIDADE CROSS com o host
        // corrigido: save(true) -> find().ok == true nos dois targets).
        String kf = """
            entity Flag {
                id: Long generated
                ok: Bool
            }
            main() {
                var db = db.connect("%s")
                db.execute(db, "create table if not exists flag (id INTEGER PRIMARY KEY AUTOINCREMENT, ok INTEGER)")
                var a = orm.save(db, Flag(0, true))
                var b = orm.save(db, Flag(0, false))
                var fa = orm.find<Flag>(db, a.id)
                if (fa != null) { println(fa.ok) }
                var fb = orm.find<Flag>(db, b.id)
                if (fb != null) { println(fb.ok) }
                var fm = orm.find<Flag>(db, 999L)
                if (fm == null) { println("miss=null") }
            }
            """;
        String expected = "true\nfalse\nmiss=null";
        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource,
                kf.formatted("jdbc:sqlite:" + kofPath(tempDir.resolve("jvm-flag397.db"))));
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("sqlite-jdbc", "SQLite"), expected);
        Path nativeSource = tempDir.resolve("NativeMain.kf");
        Files.writeString(nativeSource,
                kf.formatted("sqlite:" + kofPath(tempDir.resolve("nat-flag397.db"))));
        CompilationResult nr = driver.compile(nativeSource,
                tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nr.success(), "Native deve compilar orm.find (F2b): "
                + nr.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(
                tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process pr = pb.start();
        String out = new String(pr.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec = pr.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out,
                "§397 cross-target: save(true)->find().ok=true tambem no Native (INTEGER !=0)");
    }

    @Test
    void saveNativeEndToEndMatchesJvm(@TempDir Path tempDir) throws Exception {
        String body = """
                    db.execute(db, "create table if not exists user (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, email TEXT UNIQUE, age INTEGER)")
                    db.execute(db, "delete from user")
                    var u1 = orm.save(db, User(0, "O'Mel", "m@kof.dev", 30))
                    println(u1.id)
                    println(u1.name)
                    var u2 = orm.save(db, User(1, "Mel-2", "m@kof.dev", 30))
                    println(u2.id)
                    println(u2.name)
                    println(orm.count<User>(db))
                    var u3 = orm.save(db, User(9, "Ana", "a@kof.dev", 25))
                    println(u3.id)
                    println(orm.count<User>(db))
                    println(orm.count<User>(db, "age", 25))
                    var rows = db.query(db, "select id, name from user order by id")
                    for (var r in rows) {
                        println(r)
                    }
                }
                """;
        String expected = "1\nO'Mel\n1\nMel-2\n1\n9\n2\n1\n{\"id\":1,\"name\":\"Mel-2\"}\n{\"id\":9,\"name\":\"Ana\"}";
        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"jdbc:sqlite:" + kofPath(tempDir.resolve("jvmsave.db")) + "\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("sqlite-jdbc", "SQLite"), expected);
        Path nativeSource = tempDir.resolve("NativeMain.kf");
        Files.writeString(nativeSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"sqlite:" + kofPath(tempDir.resolve("nativesave.db")) + "\")\n")
                + body);
        CompilationResult nativeResult = driver.compile(nativeSource, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), "Native deve compilar orm.save (F2a): "
                + nativeResult.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(
                tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out, "paridade byte JVM==Native (save; 3 paths: INSERT-gen, UPDATE hit com VALOR ALTERADO writeback-verified, UPDATE miss -> INSERT-all)");
    }

    @Test
    void unknownEntityReportsOrm002(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    var db = db.connect("jdbc:h2:mem:orm5;DB_CLOSE_DELAY=-1")
                    orm.create<Ghost>(db)
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(result.success());
        assertTrue(result.diagnostics().getDiagnostics().toString().contains("ORM002"),
                "Should report ORM002: " + result.diagnostics().getDiagnostics());
    }

    // ── ORM001 (18/09): kof.orm no JS ── paridade byte-a-byte com o caminho
    // JVM acima, agora via KofJsOrmBridge (mesmo SQL) + bind de record no guest
    // (JsRuntimeOps __kof_decode_<T>). Cobrem as 3 formas de retorno: record
    // único (save/find), List<record> (all/where/where_op/page) e primitivo
    // (count/count_where/delete/delete_all/create/migrate/saveAll).

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

    @Test
    void jsCreateSaveFindAllDelete(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    var db = db.connect("jdbc:h2:mem:jsorm1;DB_CLOSE_DELAY=-1")
                    orm.create<User>(db)
                    var mel = orm.save(db, User(0, "Mel", "mel@kof.dev", 30))
                    println(mel.id)
                    var u = orm.find<User>(db, mel.id)
                    println(u.name)
                    var all = orm.all<User>(db)
                    println(all.size)
                    println(orm.count<User>(db))
                    orm.delete<User>(db, mel.id)
                    println(orm.count<User>(db))
                    db.close(db)
                }
                """);
        runJs(source, tempDir.resolve("out"), "1\nMel\n1\n1\n0");
    }

    @Test
    void jsSaveUpdatesExistingRow(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    var db = db.connect("jdbc:h2:mem:jsorm2;DB_CLOSE_DELAY=-1")
                    orm.create<User>(db)
                    var mel = orm.save(db, User(0, "Mel", "mel@kof.dev", 30))
                    mel = orm.save(db, User(mel.id, "Melissa", "mel@kof.dev", 31))
                    var u = orm.find<User>(db, mel.id)
                    println(u.name + " " + u.age)
                    db.close(db)
                }
                """);
        runJs(source, tempDir.resolve("out"), "Melissa 31");
    }

    @Test
    void jsUniqueConstraintRejected(@TempDir Path tempDir) throws IOException {
        // R6: a violacao de `unique` nao pode ser silenciosa. Na sessao anterior
        // este E2E estava BARRADO pelo ICE `catch(Throwable)` no JS (compilar
        // quebrava); com o fix do JsTryParser ele passa a ser cobertura real da
        // propagacao de erro da ponte KofJsOrmBridge -> catch no guest.
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    var db = db.connect("jdbc:h2:mem:jsorm3;DB_CLOSE_DELAY=-1")
                    orm.create<User>(db)
                    orm.save(db, User(0, "Mel", "same@kof.dev", 30))
                    try {
                        orm.save(db, User(0, "Kof", "same@kof.dev", 1))
                        println("no-error")
                    } catch (Throwable e) {
                        println("rejected")
                    }
                    db.close(db)
                }
                """);
        runJs(source, tempDir.resolve("out"), "rejected");
    }

    @Test
    void jsEntityWithoutGeneratedUsesFirstFieldAsPk(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                entity Product {
                    code: String unique
                    price: Double
                }
                main() {
                    var db = db.connect("jdbc:h2:mem:jsorm4;DB_CLOSE_DELAY=-1")
                    orm.create<Product>(db)
                    orm.save(db, Product("P1", 19.99))
                    var p = orm.find<Product>(db, "P1")
                    println(p.price)
                    db.close(db)
                }
                """);
        runJs(source, tempDir.resolve("out"), "19.99");
    }

    @Test
    void jsWhereFiltersByField(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    var db = db.connect("jdbc:h2:mem:jsorm6;DB_CLOSE_DELAY=-1")
                    orm.create<User>(db)
                    orm.save(db, User(0, "Mel", "mel@kof.dev", 30))
                    orm.save(db, User(0, "Ana", "ana@kof.dev", 25))
                    var adultos = orm.where<User>(db, "age", 30)
                    println(adultos.size)
                    println(adultos.get(0).name)
                    db.close(db)
                }
                """);
        runJs(source, tempDir.resolve("out"), "1\nMel");
    }

    @Test
    void jsWhereWithOperator(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    var db = db.connect("jdbc:h2:mem:jsorm8;DB_CLOSE_DELAY=-1")
                    orm.create<User>(db)
                    orm.save(db, User(0, "Mel", "mel@kof.dev", 30))
                    orm.save(db, User(0, "Ana", "ana@kof.dev", 25))
                    orm.save(db, User(0, "Leo", "leo@kof.dev", 40))
                    var adultos = orm.where<User>(db, "age", ">", 25)
                    println(adultos.size)
                    var jovens = orm.where<User>(db, "age", "<=", 25)
                    println(jovens.size)
                    var ana = orm.where<User>(db, "name", "LIKE", "A%")
                    println(ana.size)
                    println(ana.get(0).name)
                    db.close(db)
                }
                """);
        runJs(source, tempDir.resolve("out"), "2\n1\n1\nAna");
    }

    @Test
    void jsSaveAllBatch(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    var db = db.connect("jdbc:h2:mem:jsorm9;DB_CLOSE_DELAY=-1")
                    orm.create<User>(db)
                    var l = new List<User>()
                    l.add(User(0, "Mel", "mel@kof.dev", 30))
                    l.add(User(0, "Ana", "ana@kof.dev", 25))
                    orm.saveAll<User>(db, l)
                    println(orm.count<User>(db))
                    var mel = orm.where<User>(db, "email", "mel@kof.dev")
                    println(mel.size)
                    db.close(db)
                }
                """);
        runJs(source, tempDir.resolve("out"), "2\n1");
    }

    @Test
    void jsPagePagination(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    var db = db.connect("jdbc:h2:mem:jsorm10;DB_CLOSE_DELAY=-1")
                    orm.create<User>(db)
                    var l = new List<User>()
                    l.add(User(0, "A", "a@kof.dev", 20))
                    l.add(User(0, "B", "b@kof.dev", 21))
                    l.add(User(0, "C", "c@kof.dev", 22))
                    orm.saveAll<User>(db, l)
                    var p1 = orm.page<User>(db, 2, 0)
                    println(p1.size)
                    var p2 = orm.page<User>(db, 2, 2)
                    println(p2.size)
                    println(p2.get(0).name)
                    db.close(db)
                }
                """);
        runJs(source, tempDir.resolve("out"), "2\n1\nC");
    }

    @Test
    void jsCountWhereAndDeleteAll(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    var db = db.connect("jdbc:h2:mem:jsorm11;DB_CLOSE_DELAY=-1")
                    orm.create<User>(db)
                    orm.save(db, User(0, "Mel", "mel@kof.dev", 30))
                    orm.save(db, User(0, "Ana", "ana@kof.dev", 25))
                    println(orm.count<User>(db, "age", 30))
                    println(orm.count<User>(db))
                    orm.deleteAll<User>(db)
                    println(orm.count<User>(db))
                    db.close(db)
                }
                """);
        runJs(source, tempDir.resolve("out"), "1\n2\n0");
    }

    @Test
    void jsQueryDslFiltersOrdersAndLimits(@TempDir Path tempDir) throws IOException {
        // Query DSL tipada (nível 3) baixa para kof_db_queryN (mesmo caminho de
        // db.query<T>, DB002) — a supportedOn JS da ORM001 habilita o front-end
        // `User.query(db){ ... }` no mesmo passo. Byte-paridade com o JVM.
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    var db = db.connect("jdbc:h2:mem:jsorm12;DB_CLOSE_DELAY=-1")
                    orm.create<User>(db)
                    orm.save(db, User(0, "Mel", "mel@kof.dev", 30))
                    orm.save(db, User(0, "Ana", "ana@kof.dev", 25))
                    orm.save(db, User(0, "Leo", "leo@kof.dev", 40))
                    var adultos = User.query(db) {
                        where age > 25
                        orderBy name asc
                    }
                    println(adultos.size)
                    println(adultos.get(0).name)
                    println(adultos.get(1).name)
                    var limitado = User.query(db) {
                        where age >= 25
                        limit 1
                    }
                    println(limitado.size)
                    db.close(db)
                }
                """);
        runJs(source, tempDir.resolve("out"), "2\nLeo\nMel\n1");
    }

    // ── D-DB-GAPS F1a (20/09): kof_orm_delete_all REAL no Native x86-64 ──

    @Test
    void deleteAllNativeEndToEndMatchesJvm(@TempDir Path tempDir) throws Exception {
        String body = """
                    db.execute(db, "create table if not exists user (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, email TEXT UNIQUE, age INTEGER)")
                    db.execute(db, "delete from user")
                    db.execute(db, "insert into user (name, email, age) values ('Mel', 'm@kof.dev', 30)")
                    println(orm.deleteAll<User>(db))
                    var rows = db.query(db, "select count(*) as n from user")
                    for (var r in rows) {
                        println(r)
                    }
                }
                """;
        String expected = "true\n{\"n\":0}";
        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"jdbc:sqlite:" + kofPath(tempDir.resolve("jvma.db")) + "\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("sqlite-jdbc", "SQLite"), expected);
        Path nativeSource = tempDir.resolve("NativeMain.kf");
        Files.writeString(nativeSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"sqlite:" + kofPath(tempDir.resolve("nativea.db")) + "\")\n")
                + body);
        CompilationResult nativeResult = driver.compile(nativeSource, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), "Native deve compilar orm.deleteAll: "
                + nativeResult.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(
                tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out, "paridade byte JVM==Native (delete_all)");
    }

    @Test
    void deleteAllUnknownConnectionThrowsJvmMessageOnBothTargets(@TempDir Path tempDir)
            throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    try {
                        println(orm.deleteAll<User>("db2"))
                    } catch (String e) {
                        println(e)
                    }
                }
                """);
        String expected = "unknown db connection: db2";
        runJvm(source, tempDir.resolve("jvm-out"), expected);
        CompilationResult nativeResult = driver.compile(source, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), "Native deve compilar (usesOrm liga sqlite): "
                + nativeResult.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(
                tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out,
                "id invalido deve lancar a MESMA string do host (R6, paridade)");
    }

    @Test
    void countNativeEndToEndMatchesJvm(@TempDir Path tempDir) throws Exception {
        String body = """
                    db.execute(db, "create table if not exists user (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, email TEXT UNIQUE, age INTEGER)")
                    db.execute(db, "delete from user")
                    db.execute(db, "insert into user (name, email, age) values ('Mel', 'm@kof.dev', 30)")
                    db.execute(db, "insert into user (name, email, age) values ('Kof', 'k@kof.dev', 1)")
                    println(orm.count<User>(db))
                    println(orm.deleteAll<User>(db))
                    println(orm.count<User>(db))
                }
                """;
        String expected = "2\ntrue\n0";
        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"jdbc:sqlite:" + kofPath(tempDir.resolve("jvmb.db")) + "\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("sqlite-jdbc", "SQLite"), expected);
        Path nativeSource = tempDir.resolve("NativeMain.kf");
        Files.writeString(nativeSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"sqlite:" + kofPath(tempDir.resolve("nativeb.db")) + "\")\n")
                + body);
        CompilationResult nativeResult = driver.compile(nativeSource, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), "Native deve compilar orm.count: "
                + nativeResult.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(
                tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out, "paridade byte JVM==Native (count; 3 calls em sequencia testam a preservacao de registrantes da chamada)");
    }

    @Test
    void countUnknownConnectionThrowsJvmMessageOnNative(@TempDir Path tempDir)
            throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    try {
                        println(orm.count<User>("db2"))
                    } catch (String e) {
                        println(e)
                    }
                    println("after-throw")
                }
                """);
        String expected = "unknown db connection: db2\nafter-throw";
        runJvm(source, tempDir.resolve("jvm-out"), expected);
        CompilationResult nativeResult = driver.compile(source, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), "Native deve compilar: "
                + nativeResult.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(
                tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out, "throw + execucao continua (stack de excecao nativo)");
    }

    @Test
    void createNativeEndToEndMatchesJvm(@TempDir Path tempDir) throws Exception {
        String body = """
                    println(orm.create<User>(db))
                    println(orm.create<User>(db))
                    db.execute(db, "insert into user (name, email, age) values ('Mel', 'm@kof.dev', 30)")
                    println(orm.count<User>(db))
                    println(db.query(db, "select email from user").get(0))
                    println(db.query(db, "select sql from sqlite_master where type='table' and name='user'").get(0))
                }
                """;
        // o DDL gravado no sqlite_master e o TEXTO que cada engine enviou —
        // iguala-lo prova AUTOINCREMENT/UNIQUE/VARCHAR byte a byte (bug das
        // flags comidas pelo badtok, medido 20/09).
        String expected = "true\ntrue\n1\n{\"email\":\"m@kof.dev\"}\n{\"sql\":\"CREATE TABLE \\\"user\\\" (\\\"id\\\" INTEGER PRIMARY KEY AUTOINCREMENT, \\\"name\\\" VARCHAR(255), \\\"email\\\" VARCHAR(255) UNIQUE, \\\"age\\\" INTEGER)\"}";
        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"jdbc:sqlite:" + kofPath(tempDir.resolve("jvmb.db")) + "\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("sqlite-jdbc", "SQLite"), expected);
        Path nativeSource = tempDir.resolve("NativeMain.kf");
        Files.writeString(nativeSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"sqlite:" + kofPath(tempDir.resolve("nativeb.db")) + "\")\n")
                + body);
        CompilationResult nativeResult = driver.compile(nativeSource, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), "Native deve compilar orm.create: "
                + nativeResult.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(
                tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out, "paridade byte JVM==Native (DDL real: UNIQUE/varchar/pk"
                + " medidos pelo SELECT do email); CREATE IF NOT EXISTS duas vezes = true");
    }

    @Test
    void migrateNativeEndToEndMatchesJvm(@TempDir Path tempDir) throws Exception {
        String ddl = "create table if not exists user (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, email TEXT UNIQUE, age INTEGER)";
        String body = """
                    println(orm.migrate(db, "001-user", "DDL1"))
                    println(orm.migrate(db, "001-user", "DDL1"))
                    println(orm.migrate(db, "002-t2", "create table if not exists t2 (id INTEGER)"))
                    db.execute(db, "insert into user (name, email, age) values ('Mel', 'm@kof.dev', 30)")
                    println(db.query(db, "select count(*) as n from kof_migrations").get(0))
                    println(orm.count<User>(db))
                }
                """.replace("DDL1", ddl);
        String expected = "true\ntrue\ntrue\n{\"n\":2}\n1";
        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"jdbc:sqlite:" + kofPath(tempDir.resolve("jvmb.db")) + "\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("sqlite-jdbc", "SQLite"), expected);
        Path nativeSource = tempDir.resolve("NativeMain.kf");
        Files.writeString(nativeSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"sqlite:" + kofPath(tempDir.resolve("nativeb.db")) + "\")\n")
                + body);
        CompilationResult nativeResult = driver.compile(nativeSource, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), "Native deve compilar orm.migrate: "
                + nativeResult.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(
                tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out, "paridade byte JVM==Native (migrate idempotente; "
                + "applied_at nao sai no golden - valor de relogio nao e observavel pela API, como no host)");
    }

    @Test
    void migrateUnknownConnectionThrowsJvmMessageOnNative(@TempDir Path tempDir)
            throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    try {
                        println(orm.migrate("db2", "001", "create table t(x int)"))
                    } catch (String e) {
                        println(e)
                    }
                }
                """);
        runJvmWithExtra(source, tempDir.resolve("jvm-out"), null,
                "unknown db connection: db2");
        CompilationResult nativeResult = driver.compile(source, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), "Native deve compilar: "
                + nativeResult.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(
                tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals("unknown db connection: db2", out);
    }

    @Test
    void countWhereNativeEndToEndMatchesJvm(@TempDir Path tempDir) throws Exception {
        // F3a: count com UM bind — SQL identico ao host (FROM "t" WHERE "f"
        // = ?), valor via box de erasure §284. Q3: string, int, ausente,
        // injecao (o bind nunca concatena) e negativo (sign-extension do
        // movslq vs Integer do JDBC).
        String body = """
                    println(orm.create<User>(db))
                    db.execute(db, "insert into user (name, email, age) values ('Mel', 'm@kof.dev', 30)")
                    db.execute(db, "insert into user (name, email, age) values ('Ana', 'a@kof.dev', 41)")
                    println(orm.count<User>(db, "name", "Mel"))
                    println(orm.count<User>(db, "age", 30))
                    println(orm.count<User>(db, "email", "nope@x.io"))
                    println(orm.count<User>(db, "name", "x' OR 1=1 --"))
                    println(orm.count<User>(db, "age", -7))
                }
                """;
        String expected = "true\n1\n1\n0\n0\n0";
        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"jdbc:sqlite:" + kofPath(tempDir.resolve("jvmb.db")) + "\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("sqlite-jdbc", "SQLite"), expected);
        Path nativeSource = tempDir.resolve("NativeMain.kf");
        Files.writeString(nativeSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"sqlite:" + kofPath(tempDir.resolve("nativeb.db")) + "\")\n")
                + body);
        CompilationResult nativeResult = driver.compile(nativeSource, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), "Native deve compilar count com bind: "
                + nativeResult.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(
                tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out, "count_where Native deve igualar o JVM");
    }

    @Test
    void findNativeEndToEndMatchesJvm(@TempDir Path tempDir) throws Exception {
        String body = """
                    db.execute(db, "create table if not exists user (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, email TEXT UNIQUE, age INTEGER)")
                    db.execute(db, "delete from user")
                    var u1 = orm.save(db, User(0, "Mel", "m@kof.dev", 30))
                    var f1 = orm.find<User>(db, u1.id)
                    println(f1.id)
                    println(f1.name)
                    println(f1.age)
                    var g = orm.find<User>(db, 999)
                    if (g == null) {
                        println("null")
                    } else {
                        println("hit")
                    }
                    var f2 = orm.save(db, User(0, "Ana", "a@kof.dev", 25))
                    var f3 = orm.find<User>(db, f2.id)
                    println(f3.name + "/" + f3.age)
                    db.execute(db, "update user set name = 'Melissa' where id = " + u1.id)
                    var f4 = orm.find<User>(db, u1.id)
                    println(f4.name)
                    var rows = db.query(db, "select count(*) as n from user")
                    for (var r in rows) {
                        println(r)
                    }
                }
                """;
        String expected = "1\nMel\n30\nnull\nAna/25\nMelissa\n{\"n\":2}";
        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"jdbc:sqlite:" + kofPath(tempDir.resolve("jvmfind.db")) + "\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("sqlite-jdbc", "SQLite"), expected);
        Path nativeSource = tempDir.resolve("NativeMain.kf");
        Files.writeString(nativeSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"sqlite:" + kofPath(tempDir.resolve("nativefind.db")) + "\")\n")
                + body);
        CompilationResult nativeResult = driver.compile(nativeSource, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), "Native deve compilar orm.find (F2b): "
                + nativeResult.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(
                tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out, "paridade byte JVM==Native (find: hit, miss=null, 2a linha, update lido de volta)");
    }

    @Test
    void allNativeEndToEndMatchesJvm(@TempDir Path tempDir) throws Exception {
        // F2c: kof_orm_all no Native — 2 linhas por campo, lista vazia depois
        // do delete (host devolve List vazia, nunca null), oracle MEDIDO no
        // JVM (2/Mel/Ana/0/empty=0) antes de escrever a asm.
        String body = """
                    db.execute(db, "create table if not exists user (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, email TEXT UNIQUE, age INTEGER)")
                    db.execute(db, "delete from user")
                    orm.save(db, User(0, "Mel", "m@kof.dev", 30))
                    orm.save(db, User(0, "Ana", "a@kof.dev", 25))
                    var all = orm.all<User>(db)
                    println(all.size())
                    for (var u in all) {
                        println(u.name)
                    }
                    db.execute(db, "delete from user")
                    var all2 = orm.all<User>(db)
                    println(all2.size())
                    var none = orm.all<User>(db)
                    if (none.size() == 0) { println("empty=0") }
                }
                """;
        String expected = "2\nMel\nAna\n0\nempty=0";
        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"jdbc:sqlite:" + kofPath(tempDir.resolve("jvmall.db")) + "\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("sqlite-jdbc", "SQLite"), expected);
        Path nativeSource = tempDir.resolve("NativeMain.kf");
        Files.writeString(nativeSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"sqlite:" + kofPath(tempDir.resolve("nativeall.db")) + "\")\n")
                + body);
        CompilationResult nr = driver.compile(nativeSource,
                tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nr.success(), "Native deve compilar orm.all (F2c): "
                + nr.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(
                tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out,
                "paridade byte JVM==Native (all: 2 linhas, ordem de insercao, lista vazia=0)");
    }

    @Test
    void whereNativeEndToEndMatchesJvm(@TempDir Path tempDir) throws Exception {
        // F2c2: kof_orm_where/where_op no Native - oracle MEDIDO no JVM
        // (WhereJvm.kf): igualdade, vazio!=null, >, LIKE, ==, throw exato
        // "ORM operator not allowed: <op>" e chamada repetida sem leak.
        String body = """
                    db.execute(db, "create table if not exists user (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, email TEXT UNIQUE, age INTEGER)")
                    db.execute(db, "delete from user")
                    orm.save(db, User(0, "Mel", "m@kof.dev", 30))
                    orm.save(db, User(0, "Ana", "a@kof.dev", 25))
                    orm.save(db, User(0, "Bia", "b@kof.dev", 40))
                    var w1 = orm.where<User>(db, "age", 30)
                    println(w1.size())
                    for (var u in w1) { println(u.name) }
                    var w2 = orm.where<User>(db, "age", 99)
                    println(w2.size())
                    var w3 = orm.where<User>(db, "age", ">", 25)
                    println(w3.size())
                    for (var u in w3) { println(u.name) }
                    var w4 = orm.where<User>(db, "name", "LIKE", "A%")
                    println(w4.size())
                    var w5 = orm.where<User>(db, "age", "==", 25)
                    println(w5.size())
                    try {
                        orm.where<User>(db, "age", "DROP TABLE user", 1)
                        println("no-throw")
                    } catch (String e) {
                        println("throw:[" + e + "]")
                    }
                    var w6 = orm.where<User>(db, "age", 30)
                    println(w6.size())
                }
                """;
        String expected = "1\nMel\n0\n2\nMel\nBia\n1\n1\nthrow:[ORM operator not allowed: DROP TABLE user]\n1";
        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"jdbc:sqlite:" + kofPath(tempDir.resolve("jvmwhere.db")) + "\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("sqlite-jdbc", "SQLite"), expected);
        Path nativeSource = tempDir.resolve("NativeMain.kf");
        Files.writeString(nativeSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"sqlite:" + kofPath(tempDir.resolve("nativewhere.db")) + "\")\n")
                + body);
        CompilationResult nr = driver.compile(nativeSource,
                tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nr.success(), "Native deve compilar orm.where/where_op (F2c2): "
                + nr.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(
                tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out,
                "paridade byte JVM==Native (where: =/>/LIKE/==, throw exato, vazio=0)");
    }

    @Test
    void pageDeleteSaveAllNativeEndToEndMatchesJvm(@TempDir Path tempDir) throws Exception {
        // F2c3: kof_orm_page/kof_orm_delete/kof_orm_save_all no Native - oracle
        // MEDIDO no JVM (C3Jvm.kf): saveAll em batch, page com LIMIT/OFFSET
        // (bind 1/2), pagina vazio por offset, delete hit E miss (true sempre,
        // como execute1 >= 0 do host), leitura de volta por where/count.
        String body = """
                    db.execute(db, "create table if not exists user (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, email TEXT UNIQUE, age INTEGER)")
                    db.execute(db, "delete from user")
                    var batch = listOf(User(0, "Mel", "m@kof.dev", 30), User(0, "Ana", "a@kof.dev", 25))
                    println(orm.saveAll<User>(db, batch))
                    println(orm.count<User>(db))
                    var p1 = orm.page<User>(db, 1, 0)
                    println(p1.size())
                    for (var u in p1) { println(u.name) }
                    var p2 = orm.page<User>(db, 2, 1)
                    println(p2.size())
                    for (var u in p2) { println(u.name) }
                    var p3 = orm.page<User>(db, 0, 0)
                    println(p3.size())
                    var p4 = orm.page<User>(db, 10, 99)
                    println(p4.size())
                    var w = orm.where<User>(db, "name", "Mel")
                    println(orm.delete<User>(db, w.get(0).id))
                    println(orm.count<User>(db))
                    var w2 = orm.where<User>(db, "name", "Mel")
                    println(w2.size())
                    println(orm.delete<User>(db, 999))
                    println(orm.count<User>(db))
                    var all = orm.all<User>(db)
                    for (var u in all) { println(u.name) }
                }
                """;
        String expected = "true\n2\n1\nMel\n1\nAna\n0\n0\ntrue\n1\n0\ntrue\n1\nAna";
        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"jdbc:sqlite:" + kofPath(tempDir.resolve("jvmc3.db")) + "\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("sqlite-jdbc", "SQLite"), expected);
        Path nativeSource = tempDir.resolve("NativeMain.kf");
        Files.writeString(nativeSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"sqlite:" + kofPath(tempDir.resolve("nativec3.db")) + "\")\n")
                + body);
        CompilationResult nr = driver.compile(nativeSource,
                tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nr.success(), "Native deve compilar page/delete/saveAll (F2c3): "
                + nr.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(
                tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out,
                "paridade byte JVM==Native (F2c3: saveAll batch, page LIMIT/OFFSET, delete hit/miss=true)");
    }

    @Test
    void pageEdgesDeleteMissSaveAllEmptyNativeMatchesJvm(@TempDir Path tempDir) throws Exception {
        // F2c3 edges (Q3): saveAll de lista VAZIA (true, count 0), pagina com
        // offset para alem do fim (vazia), page(1,1) = segunda linha, delete
        // repetido do MESMO id: hit=true/count-1, miss=true/count intacto
        // (execute1 >= 0 do host). Oracle MEDIDO no JVM (C3bJvm.kf).
        String body = """
                    db.execute(db, "create table if not exists user (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, email TEXT UNIQUE, age INTEGER)")
                    db.execute(db, "delete from user")
                    println(orm.saveAll<User>(db, listOf()))
                    println(orm.count<User>(db))
                    println(orm.saveAll<User>(db, listOf(User(0, "Mel", "m@kof.dev", 30), User(0, "Ana", "a@kof.dev", 25))))
                    println(orm.count<User>(db))
                    var p = orm.page<User>(db, 5, 0)
                    println(p.size())
                    for (var u in p) { println(u.name) }
                    var pf = orm.page<User>(db, 5, 2)
                    println(pf.size())
                    var p1 = orm.page<User>(db, 1, 1)
                    println(p1.size())
                    println(p1.get(0).name)
                    var w = orm.where<User>(db, "name", "Mel")
                    println(orm.delete<User>(db, w.get(0).id))
                    println(orm.count<User>(db))
                    println(orm.delete<User>(db, w.get(0).id))
                    println(orm.count<User>(db))
                }
                """;
        String expected = "true\n0\ntrue\n2\n2\nMel\nAna\n0\n1\nAna\ntrue\n1\ntrue\n1";
        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"jdbc:sqlite:" + kofPath(tempDir.resolve("jvmc3b.db")) + "\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("sqlite-jdbc", "SQLite"), expected);
        Path nativeSource = tempDir.resolve("NativeMain.kf");
        Files.writeString(nativeSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"sqlite:" + kofPath(tempDir.resolve("nativec3b.db")) + "\")\n")
                + body);
        CompilationResult nr = driver.compile(nativeSource,
                tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nr.success(), "Native deve compilar edges F2c3: "
                + nr.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(
                tempDir.resolve("native-out/Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "Native exit code, output: '" + out + "'");
        assertEquals(expected, out,
                "paridade byte JVM==Native (F2c3 edges: batch vazio, offset alem, miss=true)");
    }

    @Test
    void rowObjectCrossFacesAllRealNoOrm001(@TempDir Path tempDir) throws IOException {
        // F2c3 FECHOU o row-object no x86-64 (os dois testes acima provam por
        // execucao) e as fatias cross A-F portaram TODAS as faces para
        // riscv64/aarch64 (delete_all/count/create/migrate/count_where/delete/
        // save/save_all/find/all/where/where_op/page). Nao ha mais ORM001 de
        // compile-time em ORM no cross; MySQL (runtime) segue recusado pelo
        // kof_orm_conn honesto (R6/R7).
        // As faces serem REAIS mudou o caminho: o compile agora LINK com
        // -lsqlite3 cross (antes parava no gate ORM001). O irmao da fatia A
        // carrega o trio de guards (§255); sem ele o teste hard-falha em host
        // sem libsqlite3-cross em vez de SKIP honesto.
        assumeTrue(isLinux(), "cross ORM E2E requires Linux + libsqlite3");
        assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 ausente — pulando");
        assumeTrue(dev.kof.compiler.nat.NativeCrossLink.sysrootOrNull("riscv64") != null,
                "sysroot cross riscv64 ausente — pulando");
        assumeTrue(dev.kof.compiler.nat.NativeCrossLink.sqliteAvailable("riscv64"),
                "libsqlite3 riscv64 ausente no sysroot — pulando");
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    var db = db.connect("sqlite:%s")
                    var p = orm.page<User>(db, 1, 0)
                    var ok = orm.delete<User>(db, 1)
                    println(orm.saveAll<User>(db, listOf(User(0, "Mel", "m@kof.dev", 30))))
                }
                """.formatted(kofPath(tempDir.resolve("f2c3-pin.db"))));
        CompilationResult r = driver.compile(source, tempDir.resolve("out"), Target.NATIVE_RISCV64);
        assertTrue(r.success(), "todas as faces do row-object sao REAIS no riscv64: "
                + r.diagnostics().getDiagnostics());
        assertFalse(r.diagnostics().getDiagnostics().toString().contains("ORM001"),
                "sem ORM001 residual: " + r.diagnostics().getDiagnostics());
    }

    /** DB-3/DB-1 cross slice A (22/09): {@code orm.deleteAll} + {@code orm.count}
     *  REAIS no riscv64/aarch64 sobre o SQLite do cross (peça RtB50, port de
     *  RuntimeOrm1) — byte-parity com o oráculo x86-64 (a referência do
     *  contrato, D-DB-GAPS) e as faces ainda não portadas seguem ORM001
     *  compile-time (pin de granularidade no mesmo teste). */
    @Test
    void crossNativeF1aDeleteAllCountMatchX86Oracle(@TempDir Path tempDir) throws IOException {
        assumeTrue(isLinux(), "cross ORM E2E requires Linux + libsqlite3");
        Path source = tempDir.resolve("OrmCross.kf");
        Files.writeString(source, """
            entity User {
                id: Long generated
                name: String
                age: Int
            }
            main() {
                var db = db.connect("sqlite:%s/kof.db")
                db.execute(db, "create table if not exists user(id integer primary key, name varchar, age int)")
                db.execute(db, "delete from user")
                db.execute(db, "insert into user(id, name, age) values (?, ?, ?)", 1, "Mel", 30)
                db.execute(db, "insert into user(id, name, age) values (?, ?, ?)", 2, "Ana", 25)
                db.execute(db, "insert into user(id, name, age) values (?, ?, ?)", 3, "Bia", 41)
                println(orm.count<User>(db))
                println(orm.deleteAll<User>(db))
                println(orm.count<User>(db))
                println(orm.deleteAll<User>(db))
                db.execute(db, "drop table user")
                println(orm.count<User>(db))
                println(orm.deleteAll<User>(db))
                db.close(db)
            }
            """.formatted(tempDir));
        Path x86out = tempDir.resolve("out-x86");
        CompilationResult xo = driver.compile(source, x86out, Target.NATIVE);
        assumeTrue(xo.success(), "x86-64 oracle should compile: " + xo.diagnostics().getDiagnostics());
        String oracle = runNativeBinary(x86out.resolve("Default/Main"), null);
        assertEquals("3\ntrue\n0\ntrue\n0\nfalse", oracle, "oráculo x86-64 (contrato D-DB-GAPS; drop -> count 0 / deleteAll false)");
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
            assertTrue(r.success(), t + " deveria compilar F1a real: " + r.diagnostics().getDiagnostics());
            String got = runNativeBinary(out.resolve("Default/Main"), "qemu-" + arch);
            assertEquals(oracle, got, t + " byte-parity com o oráculo x86-64");
        }
        Path srcGated = tempDir.resolve("OrmCrossGate.kf");
        Files.writeString(srcGated, """
            entity User {
                id: Long generated
                name: String
                age: Int
            }
            main() {
                var db = db.connect("sqlite:%s/gate.db")
                println(orm.page<User>(db, 1, 0))
            }
            """.formatted(tempDir));
        CompilationResult gated = driver.compile(srcGated, tempDir.resolve("out-gate"), Target.NATIVE_RISCV64);
        assertTrue(gated.success(), "page REAL no cross: nenhuma face ORM segue ORM001");
        assertFalse(gated.diagnostics().getDiagnostics().toString().contains("ORM001"),
                "sem ORM001 residual: " + gated.diagnostics().getDiagnostics());
    }

    /** S5.5 fatia 1 (24/09): {@code orm.count} sobre o wire mysql REAL no
     *  cross (peça RtB74: {@code kof_db_mysql_scalar_int}) — prova byte-parity
     *  com o oráculo x86-64 (que já tinha F2d2). Dialeto mysql: a peça B50
     *  cita com backtick (medido: {@code FROM "t"} = ERROR 1064 no MariaDB). */
    @Test
    void crossNativeMariadbCountMatchesX86Oracle(@TempDir Path tempDir) throws IOException {
        assumeTrue(isLinux(), "cross ORM E2E requires Linux + qemu");
        int port;
        try { port = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306")); }
        catch (NumberFormatException e) { port = 13306; }
        org.junit.jupiter.api.Assumptions.assumeTrue(tcpOpen("127.0.0.1", port),
                "MySQL/MariaDB not reachable on 127.0.0.1:" + port);
        String body = """
                    db.execute(db, "drop table if exists `user`")
                    db.execute(db, "create table `user` (id int, name varchar(50), age int)")
                    db.execute(db, "insert into `user` values (?, ?, ?)", 1, "Mel", 30)
                    db.execute(db, "insert into `user` values (?, ?, ?)", 2, "Ana", 25)
                    db.execute(db, "insert into `user` values (?, ?, ?)", 3, "Bia", 41)
                    println(orm.count<User>(db))
                    db.execute(db, "delete from `user` where id = ?", 2)
                    println(orm.count<User>(db))
                    db.close(db)
                }
                """;
        String expected = "3\n2";
        Path source = tempDir.resolve("OrmMysqlCross.kf");
        Files.writeString(source, """
            entity User {
                id: Long generated
                name: String
                age: Int
            }
            main() {
                var db = db.connect("mysql://root:kofpass@127.0.0.1:%d/test")
            %s
            """.formatted(port, body));
        Path x86out = tempDir.resolve("out-x86");
        CompilationResult xo = driver.compile(source, x86out, Target.NATIVE);
        assumeTrue(xo.success(), "x86-64 oracle should compile: " + xo.diagnostics().getDiagnostics());
        String oracle = runNativeBinary(x86out.resolve("Default/Main"), null);
        assertEquals(expected, oracle, "oráculo x86-64 (F2d2 count mysql; 3 -> 2)");
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
            assertTrue(r.success(), t + " deveria compilar orm.count mysql cross: "
                    + r.diagnostics().getDiagnostics());
            String got = runNativeBinary(out.resolve("Default/Main"), "qemu-" + arch);
            assertEquals(oracle, got, t + " byte-parity com o oráculo x86-64 (count mysql)");
        }
    }

    /** S5.5 fatia 2 (24/09): {@code orm.count_where} sobre o wire mysql no
     *  cross — os 3 oráculos juntos (JVM via JDBC, x86-64 nativo, riscv64/
     *  aarch64 sob qemu). Inclui o bind BOOL: o classificador x86 mysql
     *  checava o tag §284 errado (1 em vez de 3) e lançava ORM001 — o cross
     *  usa o mapa correto (RuntimeOrm3) e o teste trava a paridade dos 3. */
    @Test
    void crossNativeMariadbCountWhereMatchesOracles(@TempDir Path tempDir) throws Exception {
        assumeTrue(isLinux(), "cross ORM E2E requires Linux + qemu");
        int port;
        try { port = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306")); }
        catch (NumberFormatException e) { port = 13306; }
        org.junit.jupiter.api.Assumptions.assumeTrue(tcpOpen("127.0.0.1", port),
                "MySQL/MariaDB not reachable on 127.0.0.1:" + port);
        String body = """
                    db.execute(db, "drop table if exists `user`")
                    db.execute(db, "create table `user` (id int primary key, name varchar(50), email varchar(80), active int)")
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 1, "Mel", "m@kof.dev", 1)
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 2, "Ana", "a@kof.dev", 0)
                    println(orm.count<User>(db, "name", "Mel"))
                    println(orm.count<User>(db, "name", "x' OR 1=1 --"))
                    println(orm.count<User>(db, "email", "nope@x.io"))
                    println(orm.count<User>(db, "email", "m@kof.dev"))
                    println(orm.count<User>(db, "id", -7))
                    println(orm.count<User>(db, "id", 2))
                    println(orm.count<User>(db, "active", true))
                    println(orm.count<User>(db, "active", false))
                    db.close(db)
                }
                """;
        String expected = "1\n0\n0\n1\n0\n1\n1\n1";

        String entitySrc = """
            entity User {
                id: Long generated
                name: String
                email: String unique
                active: Bool
            }
            """;
        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, entitySrc + "main() {\n"
                + ("    var db = db.connect(\"jdbc:mariadb://127.0.0.1:" + port
                   + "/test?user=root&password=kofpass&allowMultiQueries=true\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("mariadb", "MariaDB"), expected);

        Path source = tempDir.resolve("OrmMysqlCross.kf");
        Files.writeString(source, entitySrc + "main() {\n"
                + ("    var db = db.connect(\"mysql://root:kofpass@127.0.0.1:" + port + "/test\")\n")
                + body);
        Path x86out = tempDir.resolve("out-x86");
        CompilationResult xo = driver.compile(source, x86out, Target.NATIVE);
        assumeTrue(xo.success(), "x86-64 oracle should compile: " + xo.diagnostics().getDiagnostics());
        String oracle = runNativeBinary(x86out.resolve("Default/Main"), null);
        assertEquals(expected, oracle, "oráculo x86-64 (count_where mysql; string/int/miss/neg/bool)");
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
            assertTrue(r.success(), t + " deveria compilar orm.count_where mysql cross: "
                    + r.diagnostics().getDiagnostics());
            String got = runNativeBinary(out.resolve("Default/Main"), "qemu-" + arch);
            assertEquals(oracle, got, t + " byte-parity com os oráculos (count_where mysql)");
        }
    }

    /** S5.5 fatia 3 (24/09): {@code orm.delete}/{@code orm.deleteAll} sobre o
     *  wire mysql no cross — 3 oráculos (JVM/JDBC, x86-64, riscv64/aarch64 sob
     *  qemu). O caminho de sucesso é o pin principal; o x86 usa o exec genérico
     *  (sem throw) e o cross o espelha (peça B75, `kof_db_mysql_execute`/B72). */
    @Test
    void crossNativeMariadbDeleteAndDeleteAllMatchesOracles(@TempDir Path tempDir) throws Exception {
        assumeTrue(isLinux(), "cross ORM E2E requires Linux + qemu");
        int port;
        try { port = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306")); }
        catch (NumberFormatException e) { port = 13306; }
        org.junit.jupiter.api.Assumptions.assumeTrue(tcpOpen("127.0.0.1", port),
                "MySQL/MariaDB not reachable on 127.0.0.1:" + port);
        String body = """
                    db.execute(db, "drop table if exists `user`")
                    db.execute(db, "create table `user` (id int primary key, name varchar(50), email varchar(80), age int)")
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 1, "Mel", "m@kof.dev", 30)
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 2, "Ana", "a@kof.dev", 25)
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 3, "Bia", "b@kof.dev", 41)
                    println(orm.count<User>(db))
                    println(orm.delete<User>(db, 2))
                    println(orm.count<User>(db))
                    println(orm.delete<User>(db, 99))
                    println(orm.count<User>(db))
                    println(orm.delete<User>(db, -7))
                    println(orm.count<User>(db))
                    println(orm.deleteAll<User>(db))
                    println(orm.deleteAll<User>(db))
                    println(orm.count<User>(db))
                    db.close(db)
                }
                """;
        String expected = "3\ntrue\n2\ntrue\n2\ntrue\n2\ntrue\ntrue\n0";
        String entitySrc = """
            entity User {
                id: Long generated
                name: String
                email: String unique
                age: Int
            }
            """;
        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, entitySrc + "main() {\n"
                + ("    var db = db.connect(\"jdbc:mariadb://127.0.0.1:" + port
                   + "/test?user=root&password=kofpass&allowMultiQueries=true\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("mariadb", "MariaDB"), expected);

        Path source = tempDir.resolve("OrmMysqlCross.kf");
        Files.writeString(source, entitySrc + "main() {\n"
                + ("    var db = db.connect(\"mysql://root:kofpass@127.0.0.1:" + port + "/test\")\n")
                + body);
        Path x86out = tempDir.resolve("out-x86");
        CompilationResult xo = driver.compile(source, x86out, Target.NATIVE);
        assumeTrue(xo.success(), "x86-64 oracle should compile: " + xo.diagnostics().getDiagnostics());
        String oracle = runNativeBinary(x86out.resolve("Default/Main"), null);
        assertEquals(expected, oracle, "oráculo x86-64 (delete/deleteAll mysql; hit/miss/neg/idempotente)");
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
            assertTrue(r.success(), t + " deveria compilar orm.delete/deleteAll mysql cross: "
                    + r.diagnostics().getDiagnostics());
            String got = runNativeBinary(out.resolve("Default/Main"), "qemu-" + arch);
            assertEquals(oracle, got, t + " byte-parity com os oráculos (delete/deleteAll mysql)");
        }
    }

    /** S5.5 fatia 3 (24/09): o ERR do servidor no `orm.deleteAll` mysql **NÃO**
     *  lança no Native — x86 e cross devolvem `affectedRows >= 0` (true). O
     *  host JVM lança (JDBC); essa divergência JVM↔Native é PRÉ-EXISTENTE e
     *  está catalogada (§493) — o teste trava a paridade Native↔cross, não com
     *  o JVM. */
    @Test
    void crossNativeMariadbDeleteErrorMatchesX86Oracle(@TempDir Path tempDir) throws IOException {
        assumeTrue(isLinux(), "cross ORM E2E requires Linux + qemu");
        int port;
        try { port = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306")); }
        catch (NumberFormatException e) { port = 13306; }
        org.junit.jupiter.api.Assumptions.assumeTrue(tcpOpen("127.0.0.1", port),
                "MySQL/MariaDB not reachable on 127.0.0.1:" + port);
        String body = """
                    db.execute(db, "drop table if exists `user`")
                    try {
                        println(orm.deleteAll<User>(db))
                    } catch (String e) {
                        println("threw")
                    }
                    db.close(db)
                }
                """;
        String expected = "true";
        Path source = tempDir.resolve("OrmMysqlErr.kf");
        Files.writeString(source, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"mysql://root:kofpass@127.0.0.1:" + port + "/test\")\n")
                + body);
        Path x86out = tempDir.resolve("out-x86");
        CompilationResult xo = driver.compile(source, x86out, Target.NATIVE);
        assumeTrue(xo.success(), "x86-64 oracle should compile: " + xo.diagnostics().getDiagnostics());
        String oracle = runNativeBinary(x86out.resolve("Default/Main"), null);
        assertEquals(expected, oracle, "oráculo x86-64 (deleteAll em tabela inexistente: true, sem throw — §493)");
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
            assertTrue(r.success(), t + " deveria compilar o erro mysql cross: "
                    + r.diagnostics().getDiagnostics());
            String got = runNativeBinary(out.resolve("Default/Main"), "qemu-" + arch);
            assertEquals(oracle, got, t + " byte-parity com o oráculo x86 (ERR não lança — §493)");
        }
    }

    /** S5.5 fatia 4b (24/09): {@code orm.save} sobre o wire mysql no cross —
     *  3 oráculos (JVM/JDBC, x86-64, riscv64/aarch64 sob qemu). As 3 saídas do
     *  host: INSERT sem a PK + {@code LAST_INSERT_ID} (nova instância), UPDATE
     *  hit (mesmo ponteiro) e UPDATE miss → upsert. Peça B77. */
    @Test
    void crossNativeMariadbSaveMatchesOracles(@TempDir Path tempDir) throws Exception {
        assumeTrue(isLinux(), "cross ORM E2E requires Linux + qemu");
        int port;
        try { port = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306")); }
        catch (NumberFormatException e) { port = 13306; }
        org.junit.jupiter.api.Assumptions.assumeTrue(tcpOpen("127.0.0.1", port),
                "MySQL/MariaDB not reachable on 127.0.0.1:" + port);
        String body = """
                    db.execute(db, "drop table if exists `user`")
                    db.execute(db, "create table `user` (id int primary key auto_increment, name varchar(50), email varchar(80), age int)")
                    var u1 = orm.save(db, User(0, "Mel", "m@kof.dev", 30))
                    println(u1.id)
                    println(orm.count<User>(db))
                    var u2 = orm.save(db, User(u1.id, "Mel2", "mel2@kof.dev", 31))
                    println(u2.id)
                    println(orm.count<User>(db))
                    println(db.query(db, "select name from `user` where id = " + u1.id).get(0))
                    var u3 = orm.save(db, User(7, "Zoe", "z@kof.dev", 22))
                    println(u3.id)
                    println(orm.count<User>(db))
                    var u4 = orm.save(db, User(0, "Ana", "a@kof.dev", 25))
                    println(u4.id)
                    println(orm.count<User>(db))
                    db.close(db)
                }
                """;
        String expected = "1\n1\n1\n1\n{\"name\":\"Mel2\"}\n7\n2\n8\n3";
        String entitySrc = """
            entity User {
                id: Long generated
                name: String
                email: String unique
                age: Int
            }
            """;
        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, entitySrc + "main() {\n"
                + ("    var db = db.connect(\"jdbc:mariadb://127.0.0.1:" + port
                   + "/test?user=root&password=kofpass&allowMultiQueries=true\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("mariadb", "MariaDB"), expected);

        Path source = tempDir.resolve("OrmMysqlCross.kf");
        Files.writeString(source, entitySrc + "main() {\n"
                + ("    var db = db.connect(\"mysql://root:kofpass@127.0.0.1:" + port + "/test\")\n")
                + body);
        Path x86out = tempDir.resolve("out-x86");
        CompilationResult xo = driver.compile(source, x86out, Target.NATIVE);
        assumeTrue(xo.success(), "x86-64 oracle should compile: " + xo.diagnostics().getDiagnostics());
        String oracle = runNativeBinary(x86out.resolve("Default/Main"), null);
        assertEquals(expected, oracle, "oráculo x86-64 (save mysql; INSERT gerado, UPDATE hit, upsert)");
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
            assertTrue(r.success(), t + " deveria compilar orm.save mysql cross: "
                    + r.diagnostics().getDiagnostics());
            String got = runNativeBinary(out.resolve("Default/Main"), "qemu-" + arch);
            assertEquals(oracle, got, t + " byte-parity com os oráculos (save mysql)");
        }
    }

    /** S5.5 fatia 4c (24/09): {@code orm.saveAll} sobre o wire mysql no cross —
     *  3 oráculos (JVM/JDBC, x86-64, riscv64/aarch64 sob qemu). O
     *  {@code kof_orm_save_all} (B56) só faz o loop e delega ao
     *  {@code kof_orm_save}, que agora ramifica para a peça B77 no type 2 —
     *  o lote INSERT (PKs geradas) e o lote UPDATE por pk, com lista vazia. */
    @Test
    void crossNativeMariadbSaveAllMatchesOracles(@TempDir Path tempDir) throws Exception {
        assumeTrue(isLinux(), "cross ORM E2E requires Linux + qemu");
        int port;
        try { port = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306")); }
        catch (NumberFormatException e) { port = 13306; }
        org.junit.jupiter.api.Assumptions.assumeTrue(tcpOpen("127.0.0.1", port),
                "MySQL/MariaDB not reachable on 127.0.0.1:" + port);
        String body = """
                    db.execute(db, "drop table if exists `user`")
                    db.execute(db, "create table `user` (id int primary key auto_increment, name varchar(50), email varchar(80), age int)")
                    var l1 = new List<User>()
                    l1.add(User(0, "Mel", "m@kof.dev", 30))
                    l1.add(User(0, "Ana", "a@kof.dev", 25))
                    println(orm.saveAll<User>(db, l1))
                    println(orm.count<User>(db))
                    println(db.query(db, "select name from `user` order by id").get(0))
                    println(db.query(db, "select name from `user` order by id").get(1))
                    var l2 = new List<User>()
                    l2.add(User(1, "Mel2", "m2@kof.dev", 31))
                    l2.add(User(2, "Ana2", "a2@kof.dev", 26))
                    println(orm.saveAll<User>(db, l2))
                    println(orm.count<User>(db))
                    println(db.query(db, "select name from `user` where id = 1").get(0))
                    var empty = new List<User>()
                    println(orm.saveAll<User>(db, empty))
                    println(orm.count<User>(db))
                    db.close(db)
                }
                """;
        String expected = "true\n2\n{\"name\":\"Mel\"}\n{\"name\":\"Ana\"}\n"
                + "true\n2\n{\"name\":\"Mel2\"}\ntrue\n2";
        String entitySrc = """
            entity User {
                id: Long generated
                name: String
                email: String unique
                age: Int
            }
            """;
        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, entitySrc + "main() {\n"
                + ("    var db = db.connect(\"jdbc:mariadb://127.0.0.1:" + port
                   + "/test?user=root&password=kofpass&allowMultiQueries=true\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("mariadb", "MariaDB"), expected);

        Path source = tempDir.resolve("OrmMysqlCross.kf");
        Files.writeString(source, entitySrc + "main() {\n"
                + ("    var db = db.connect(\"mysql://root:kofpass@127.0.0.1:" + port + "/test\")\n")
                + body);
        Path x86out = tempDir.resolve("out-x86");
        CompilationResult xo = driver.compile(source, x86out, Target.NATIVE);
        assumeTrue(xo.success(), "x86-64 oracle should compile: " + xo.diagnostics().getDiagnostics());
        String oracle = runNativeBinary(x86out.resolve("Default/Main"), null);
        assertEquals(expected, oracle, "oráculo x86-64 (saveAll mysql; lote INSERT, lote UPDATE, vazio)");
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
            assertTrue(r.success(), t + " deveria compilar orm.saveAll mysql cross: "
                    + r.diagnostics().getDiagnostics());
            String got = runNativeBinary(out.resolve("Default/Main"), "qemu-" + arch);
            assertEquals(oracle, got, t + " byte-parity com os oráculos (saveAll mysql)");
        }
    }

    /** S5.5 fatia 5 (24/09): {@code orm.find} sobre o wire mysql no cross — 3
     *  oráculos (JVM/JDBC, x86-64, riscv64/aarch64 sob qemu). SELECT por pk
     *  (key como literal), colunas casadas por NOME e record construído por
     *  {@code kof_orm_ctors}; miss → null. Peça B78. */
    @Test
    void crossNativeMariadbFindMatchesOracles(@TempDir Path tempDir) throws Exception {
        assumeTrue(isLinux(), "cross ORM E2E requires Linux + qemu");
        int port;
        try { port = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306")); }
        catch (NumberFormatException e) { port = 13306; }
        org.junit.jupiter.api.Assumptions.assumeTrue(tcpOpen("127.0.0.1", port),
                "MySQL/MariaDB not reachable on 127.0.0.1:" + port);
        String body = """
                    db.execute(db, "drop table if exists `user`")
                    db.execute(db, "create table `user` (id int, name varchar(50), email varchar(80), age int)")
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 1, "Mel", "m@kof.dev", 30)
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 2, "Ana", "a@kof.dev", 25)
                    var mel = orm.find<User>(db, 1)
                    println(mel.id)
                    println(mel.name)
                    println(mel.email)
                    println(mel.age)
                    var g = orm.find<User>(db, 999)
                    if (g == null) {
                        println("null")
                    } else {
                        println("hit")
                    }
                    var ana = orm.find<User>(db, 2)
                    println(ana.name + "/" + ana.age)
                    var klong: Long = 2
                    var ana2 = orm.find<User>(db, klong)
                    println(ana2.name + "/" + ana2.age)
                    var esk = orm.find<User>(db, "1")
                    println(esk.name)
                    db.close(db)
                }
                """;
        String expected = "1\nMel\nm@kof.dev\n30\nnull\nAna/25\nAna/25\nMel";
        String entitySrc = """
            entity User {
                id: Long generated
                name: String
                email: String unique
                age: Int
            }
            """;
        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, entitySrc + "main() {\n"
                + ("    var db = db.connect(\"jdbc:mariadb://127.0.0.1:" + port
                   + "/test?user=root&password=kofpass&allowMultiQueries=true\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("mariadb", "MariaDB"), expected);

        Path source = tempDir.resolve("OrmMysqlCross.kf");
        Files.writeString(source, entitySrc + "main() {\n"
                + ("    var db = db.connect(\"mysql://root:kofpass@127.0.0.1:" + port + "/test\")\n")
                + body);
        Path x86out = tempDir.resolve("out-x86");
        CompilationResult xo = driver.compile(source, x86out, Target.NATIVE);
        assumeTrue(xo.success(), "x86-64 oracle should compile: " + xo.diagnostics().getDiagnostics());
        String oracle = runNativeBinary(x86out.resolve("Default/Main"), null);
        assertEquals(expected, oracle, "oráculo x86-64 (find mysql; hit 4 campos, miss null, 2a linha, key String)");
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
            assertTrue(r.success(), t + " deveria compilar orm.find mysql cross: "
                    + r.diagnostics().getDiagnostics());
            String got = runNativeBinary(out.resolve("Default/Main"), "qemu-" + arch);
            assertEquals(oracle, got, t + " byte-parity com os oráculos (find mysql)");
        }
    }

    /** S5.5 fatia 5b (24/09): `orm.all` sobre o wire MySQL no cross (peça B79)
     *  — mesmo walk de pacotes do find; UMA resolução de ctors antes do loop,
     *  um record por linha em `kof_list_new/add`, lista VAZIA (nunca null).
     *  Prova byte JVM==x86-64==riscv64==aarch64. */
    @Test
    void crossNativeMariadbAllMatchesOracles(@TempDir Path tempDir) throws Exception {
        assumeTrue(isLinux(), "cross ORM E2E requires Linux + qemu");
        int port;
        try { port = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306")); }
        catch (NumberFormatException e) { port = 13306; }
        org.junit.jupiter.api.Assumptions.assumeTrue(tcpOpen("127.0.0.1", port),
                "MySQL/MariaDB not reachable on 127.0.0.1:" + port);
        String body = """
                    db.execute(db, "drop table if exists `user`")
                    db.execute(db, "create table `user` (id int primary key, name varchar(50), email varchar(80), age int)")
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 1, "Mel", "m@kof.dev", 30)
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 2, "Ana", "a@kof.dev", 25)
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 3, "Leo", "l@kof.dev", 40)
                    var l = orm.all<User>(db)
                    println(l.size)
                    for (var u in l) {
                        println(u.name + "/" + u.age)
                    }
                    db.execute(db, "delete from `user` where id = ?", 2)
                    var l2 = orm.all<User>(db)
                    println(l2.size)
                    for (var u in l2) {
                        println(u.name)
                    }
                    orm.deleteAll<User>(db)
                    var l3 = orm.all<User>(db)
                    println(l3.size)
                    db.close(db)
                }
                """;
        String expected = "3\nMel/30\nAna/25\nLeo/40\n2\nMel\nLeo\n0";
        String entitySrc = """
            entity User {
                id: Long generated
                name: String
                email: String unique
                age: Int
            }
            """;
        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, entitySrc + "main() {\n"
                + ("    var db = db.connect(\"jdbc:mariadb://127.0.0.1:" + port
                   + "/test?user=root&password=kofpass&allowMultiQueries=true\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("mariadb", "MariaDB"), expected);

        Path source = tempDir.resolve("OrmMysqlAllCross.kf");
        Files.writeString(source, entitySrc + "main() {\n"
                + ("    var db = db.connect(\"mysql://root:kofpass@127.0.0.1:" + port + "/test\")\n")
                + body);
        Path x86out = tempDir.resolve("out-x86");
        CompilationResult xo = driver.compile(source, x86out, Target.NATIVE);
        assumeTrue(xo.success(), "x86-64 oracle should compile: " + xo.diagnostics().getDiagnostics());
        String oracle = runNativeBinary(x86out.resolve("Default/Main"), null);
        assertEquals(expected, oracle, "oráculo x86-64 (all mysql; 3 linhas, 2 linhas, lista vazia)");
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
            assertTrue(r.success(), t + " deveria compilar orm.all mysql cross: "
                    + r.diagnostics().getDiagnostics());
            String got = runNativeBinary(out.resolve("Default/Main"), "qemu-" + arch);
            assertEquals(oracle, got, t + " byte-parity com os oráculos (all mysql)");
        }
    }

    /** S5.5 fatia 5c (24/09): `orm.where`/`where_op` sobre o wire MySQL no cross
     *  (peça B80, dispatch na B59) — `SELECT * FROM \`t\` WHERE \`f\` <op> ?`,
     *  value como literal, whitelist do op compartilhada com o sqlite
     *  (==→=, LIKE, resto throw), lista vazia se nada casar. Prova byte
     *  JVM==x86-64==riscv64==aarch64. */
    @Test
    void crossNativeMariadbWhereMatchesOracles(@TempDir Path tempDir) throws Exception {
        assumeTrue(isLinux(), "cross ORM E2E requires Linux + qemu");
        int port;
        try { port = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306")); }
        catch (NumberFormatException e) { port = 13306; }
        org.junit.jupiter.api.Assumptions.assumeTrue(tcpOpen("127.0.0.1", port),
                "MySQL/MariaDB not reachable on 127.0.0.1:" + port);
        String body = """
                    db.execute(db, "drop table if exists `user`")
                    db.execute(db, "create table `user` (id int primary key, name varchar(50), email varchar(80), age int)")
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 1, "Mel", "m@kof.dev", 30)
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 2, "Ana", "a@kof.dev", 25)
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 3, "Bia", "b@kof.dev", 40)
                    var w1 = orm.where<User>(db, "age", 30)
                    println(w1.size)
                    for (var u in w1) { println(u.name + "/" + u.age) }
                    var w2 = orm.where<User>(db, "age", 99)
                    println(w2.size)
                    var w3 = orm.where<User>(db, "age", ">", 25)
                    println(w3.size)
                    for (var u in w3) { println(u.name) }
                    var w4 = orm.where<User>(db, "name", "LIKE", "A%")
                    println(w4.size)
                    for (var u in w4) { println(u.name) }
                    var w5 = orm.where<User>(db, "age", "==", 25)
                    println(w5.size)
                    var w6 = orm.where<User>(db, "name", "!=", "Mel")
                    println(w6.size)
                    for (var u in w6) { println(u.name) }
                    try {
                        orm.where<User>(db, "age", "DROP TABLE user", 1)
                        println("no-throw")
                    } catch (String e) {
                        println("throw:[" + e + "]")
                    }
                    var w7 = orm.where<User>(db, "age", 30)
                    println(w7.size)
                    db.close(db)
                }
                """;
        String expected = "1\nMel/30\n0\n2\nMel\nBia\n1\nAna\n1\n2\nAna\nBia\n"
                + "throw:[ORM operator not allowed: DROP TABLE user]\n1";
        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"jdbc:mariadb://127.0.0.1:" + port
                   + "/test?user=root&password=kofpass&allowMultiQueries=true\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("mariadb", "MariaDB"), expected);

        Path source = tempDir.resolve("OrmMysqlWhereCross.kf");
        Files.writeString(source, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"mysql://root:kofpass@127.0.0.1:" + port + "/test\")\n")
                + body);
        Path x86out = tempDir.resolve("out-x86");
        CompilationResult xo = driver.compile(source, x86out, Target.NATIVE);
        assumeTrue(xo.success(), "x86-64 oracle should compile: " + xo.diagnostics().getDiagnostics());
        String oracle = runNativeBinary(x86out.resolve("Default/Main"), null);
        assertEquals(expected, oracle, "oráculo x86-64 (where mysql; =/>/LIKE/==/!=, throw, vazio)");
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
            assertTrue(r.success(), t + " deveria compilar orm.where mysql cross: "
                    + r.diagnostics().getDiagnostics());
            String got = runNativeBinary(out.resolve("Default/Main"), "qemu-" + arch);
            assertEquals(oracle, got, t + " byte-parity com os oráculos (where mysql)");
        }
    }

    /** S5.5 fatia 5d (24/09): `orm.page` sobre o wire MySQL no cross (peça B81,
     *  dispatch na B60) — `SELECT * FROM \`t\` LIMIT <lim> OFFSET <off>`,
     *  lim/off como o host (`((Number)x).intValue()`, box Long incluso),
     *  página vazia = lista vazia. Prova byte JVM==x86-64==riscv64==aarch64. */
    @Test
    void crossNativeMariadbPageMatchesOracles(@TempDir Path tempDir) throws Exception {
        assumeTrue(isLinux(), "cross ORM E2E requires Linux + qemu");
        int port;
        try { port = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306")); }
        catch (NumberFormatException e) { port = 13306; }
        org.junit.jupiter.api.Assumptions.assumeTrue(tcpOpen("127.0.0.1", port),
                "MySQL/MariaDB not reachable on 127.0.0.1:" + port);
        String body = """
                    db.execute(db, "drop table if exists `user`")
                    db.execute(db, "create table `user` (id int primary key, name varchar(50), email varchar(80), age int)")
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 1, "Mel", "m@kof.dev", 30)
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 2, "Ana", "a@kof.dev", 25)
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 3, "Bia", "b@kof.dev", 40)
                    db.execute(db, "insert into `user` values (?, ?, ?, ?)", 4, "Leo", "l@kof.dev", 35)
                    var p1 = orm.page<User>(db, 2, 0)
                    println(p1.size)
                    for (var u in p1) { println(u.name) }
                    var p2 = orm.page<User>(db, 2, 2)
                    println(p2.size)
                    for (var u in p2) { println(u.name) }
                    var p3 = orm.page<User>(db, 0, 0)
                    println(p3.size)
                    var p4 = orm.page<User>(db, 10, 99)
                    println(p4.size)
                    var p5 = orm.page<User>(db, 3, 1)
                    println(p5.size)
                    for (var u in p5) { println(u.name + "/" + u.age) }
                    var lim: Long = 2
                    var off: Long = 0
                    var p6 = orm.page<User>(db, lim, off)
                    println(p6.size)
                    for (var u in p6) { println(u.name) }
                    db.close(db)
                }
                """;
        String expected = "2\nMel\nAna\n2\nBia\nLeo\n0\n0\n3\nAna/25\nBia/40\nLeo/35\n2\nMel\nAna";
        Path jvmSource = tempDir.resolve("JvmMain.kf");
        Files.writeString(jvmSource, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"jdbc:mariadb://127.0.0.1:" + port
                   + "/test?user=root&password=kofpass&allowMultiQueries=true\")\n")
                + body);
        runJvmWithExtra(jvmSource, tempDir.resolve("jvm-out"),
                findDriverJar("mariadb", "MariaDB"), expected);

        Path source = tempDir.resolve("OrmMysqlPageCross.kf");
        Files.writeString(source, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"mysql://root:kofpass@127.0.0.1:" + port + "/test\")\n")
                + body);
        Path x86out = tempDir.resolve("out-x86");
        CompilationResult xo = driver.compile(source, x86out, Target.NATIVE);
        assumeTrue(xo.success(), "x86-64 oracle should compile: " + xo.diagnostics().getDiagnostics());
        String oracle = runNativeBinary(x86out.resolve("Default/Main"), null);
        assertEquals(expected, oracle, "oráculo x86-64 (page mysql; 2 páginas, vazias, Long, parcial)");
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
            assertTrue(r.success(), t + " deveria compilar orm.page mysql cross: "
                    + r.diagnostics().getDiagnostics());
            String got = runNativeBinary(out.resolve("Default/Main"), "qemu-" + arch);
            assertEquals(oracle, got, t + " byte-parity com os oráculos (page mysql)");
        }
    }

    /** S5.5 fatia 4b (24/09): o ERR do servidor no `orm.save` mysql **LANÇA**
     *  tanto no x86 quanto no cross (peça B77 + `kof_orm_mysql_exec`/B76) —
     *  diferente de delete/deleteAll (§493). Testa paridade Native↔cross
     *  (o host JVM também lança, mas com a mensagem do JDBC). */
    @Test
    void crossNativeMariadbSaveErrorMatchesX86Oracle(@TempDir Path tempDir) throws IOException {
        assumeTrue(isLinux(), "cross ORM E2E requires Linux + qemu");
        int port;
        try { port = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306")); }
        catch (NumberFormatException e) { port = 13306; }
        org.junit.jupiter.api.Assumptions.assumeTrue(tcpOpen("127.0.0.1", port),
                "MySQL/MariaDB not reachable on 127.0.0.1:" + port);
        String body = """
                    db.execute(db, "drop table if exists `user`")
                    try {
                        println(orm.save<User>(db, User(0, "X", "x@kof.dev", 1)))
                    } catch (String e) {
                        println("threw")
                    }
                    println("after-throw")
                    db.close(db)
                }
                """;
        String expected = "threw\nafter-throw";
        Path source = tempDir.resolve("OrmMysqlSaveErr.kf");
        Files.writeString(source, ENTITY_SRC + "main() {\n"
                + ("    var db = db.connect(\"mysql://root:kofpass@127.0.0.1:" + port + "/test\")\n")
                + body);
        Path x86out = tempDir.resolve("out-x86");
        CompilationResult xo = driver.compile(source, x86out, Target.NATIVE);
        assumeTrue(xo.success(), "x86-64 oracle should compile: " + xo.diagnostics().getDiagnostics());
        String oracle = runNativeBinary(x86out.resolve("Default/Main"), null);
        assertEquals(expected, oracle, "oráculo x86-64 (save em tabela inexistente: throw)");
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
            assertTrue(r.success(), t + " deveria compilar o erro save mysql cross: "
                    + r.diagnostics().getDiagnostics());
            String got = runNativeBinary(out.resolve("Default/Main"), "qemu-" + arch);
            assertEquals(oracle, got, t + " byte-parity com o oráculo x86 (ERR do save lança)");
        }
    }

    /** DB-3/DB-1 cross slice B (22/09): {@code orm.create} REAL no riscv64/
     *  aarch64 (peça RtB51, port de RuntimeOrm2) — parser de schema + DDL
     *  byte-idêntico ao x86-64 (o golden LÊ o sql gravado no sqlite_master:
     *  AUTOINCREMENT/UNIQUE/VARCHAR/tipos) + mensagem de id ruim. */
    @Test
    void crossNativeF1dCreateMatchesX86Oracle(@TempDir Path tempDir) throws IOException {
        assumeTrue(isLinux(), "cross ORM E2E requires Linux + libsqlite3");
        String userTemplate = """
            entity User {
                id: Long generated
                name: String
                email: String unique
                age: Int
            }
            main() {
                var db = db.connect("sqlite:%s/kof.db")
                println(orm.create<User>(db))
                println(orm.create<User>(db))
                db.execute(db, "insert into user (name, email, age) values ('Mel', 'm@kof.dev', 30)")
                println(orm.count<User>(db))
                println(db.query(db, "select email from user").get(0))
                println(db.query(db, "select sql from sqlite_master where type='table' and name='user'").get(0))
                db.close(db)
            }
            """;
        String golden = "true\ntrue\n1\n{\"email\":\"m@kof.dev\"}\n"
                + "{\"sql\":\"CREATE TABLE \\\"user\\\" (\\\"id\\\" INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "\\\"name\\\" VARCHAR(255), \\\"email\\\" VARCHAR(255) UNIQUE, \\\"age\\\" INTEGER)\"}";
        String oracle = runX86CreateOracle(tempDir, "user", userTemplate);
        assertEquals(golden, oracle, "oráculo x86-64 (DDL real lido do sqlite_master)");
        assertCrossCreateParity(tempDir, "user", userTemplate, oracle);

        String edgeTemplate = """
            entity Item {
                id: Long generated unique
                flag: Bool
                ratio: Float
                price: Double
                note: String
                qty: Int
                big: Long
            }
            entity Product {
                code: String unique
                price: Double
            }
            main() {
                var db = db.connect("sqlite:%s/edge.db")
                println(orm.create<Item>(db))
                println(orm.create<Product>(db))
                println(db.query(db, "select sql from sqlite_master where type='table' and name='item'").get(0))
                println(db.query(db, "select sql from sqlite_master where type='table' and name='product'").get(0))
                try {
                    println(orm.create<Item>("db2"))
                } catch (String e) {
                    println(e)
                }
                println("after-throw")
                db.close(db)
            }
            """;
        String edgeGolden = "true\ntrue\n"
                + "{\"sql\":\"CREATE TABLE \\\"item\\\" (\\\"id\\\" INTEGER PRIMARY KEY AUTOINCREMENT, \\\"flag\\\" BOOLEAN, "
                + "\\\"ratio\\\" REAL, \\\"price\\\" DOUBLE, \\\"note\\\" VARCHAR(255), \\\"qty\\\" INTEGER, \\\"big\\\" INTEGER)\"}\n"
                + "{\"sql\":\"CREATE TABLE \\\"product\\\" (\\\"code\\\" VARCHAR(255) UNIQUE, \\\"price\\\" DOUBLE)\"}\n"
                + "unknown db connection: db2\nafter-throw";
        String edgeOracle = runX86CreateOracle(tempDir, "edge", edgeTemplate);
        assertEquals(edgeGolden, edgeOracle,
                "oráculo x86-64 (edges: unique em generated, sem generated, tipos, id ruim)");
        assertCrossCreateParity(tempDir, "edge", edgeTemplate, edgeOracle);
    }

    /** DB-3/DB-1 cross slice C (22/09): {@code orm.migrate} REAL no riscv64/
     *  aarch64 (peça RtB52, port de RuntimeOrm1) — {@code kof_migrations},
     *  idempotência por nome (2ª chamada true sem re-rodar), SQL inválido →
     *  false (mesmo contrato do host JVM/x86: rc<0) sem registrar, e id ruim →
     *  throw. Byte-parity com o oráculo x86-64. */
    @Test
    void crossNativeF1cMigrateMatchesX86Oracle(@TempDir Path tempDir) throws IOException {
        assumeTrue(isLinux(), "cross ORM E2E requires Linux + libsqlite3");
        String template = """
            entity User {
                id: Long generated
                name: String
                email: String unique
                age: Int
            }
            main() {
                var db = db.connect("sqlite:%s/kof.db")
                println(orm.migrate(db, "001-user", "create table if not exists user (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, email TEXT UNIQUE, age INTEGER)"))
                println(orm.migrate(db, "001-user", "create table if not exists user (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, email TEXT UNIQUE, age INTEGER)"))
                println(orm.migrate(db, "002-t2", "create table if not exists t2 (id INTEGER)"))
                println(orm.migrate(db, "003-bad", "not a sql"))
                println(orm.migrate(db, "004-good", "create table if not exists t4 (x INTEGER)"))
                println(orm.migrate(db, "003-bad", "not a sql"))
                println(db.query(db, "select count(*) as n from kof_migrations").get(0))
                db.execute(db, "insert into user (name, email, age) values ('Mel', 'm@kof.dev', 30)")
                println(db.query(db, "select count(*) as n from kof_migrations").get(0))
                println(orm.count<User>(db))
                try {
                    println(orm.migrate("db2", "005", "create table t5(x int)"))
                } catch (String e) {
                    println(e)
                }
                println("after-throw")
                db.close(db)
            }
            """;
        String golden = "true\ntrue\ntrue\nfalse\ntrue\nfalse\n{\"n\":3}\n{\"n\":3}\n1\n"
                + "unknown db connection: db2\nafter-throw";
        String oracle = runX86CreateOracle(tempDir, "migrate", template);
        assertEquals(golden, oracle, "oráculo x86-64 (migrate: idempotente, inválido→false sem registrar, id ruim→throw)");
        assertCrossCreateParity(tempDir, "migrate", template, oracle);
    }

    /** DB-3/DB-1 cross slice D (22/09): {@code orm.count_where} REAL no
     *  riscv64/aarch64 (peça RtB53, port de RuntimeOrm3) — bind por tag de
     *  caixa §284 (Int/Long/Bool/Double/Float), KofString e null, SQL sem
     *  concatenação (injeção vira busca literal), miss/negativo → 0, valor
     *  de forma inválida → throw ORM001 (R6), id ruim → throw. Byte-parity
     *  com o oráculo x86-64 e com o host JVM na matriz toda.
     *
     *  <p>O caso Bool literal pina o §447: no Native o box de erasure do
     *  argumento ORM saía como {@code java.lang.Boolean.valueOf} → o
     *  dispatch nativo de {@code valueOf} (que converte para String no
     *  caminho de concat/print) devolvia TEXTO "true"/"false" e o bind
     *  comparava TEXT contra a coluna INTEGER — 0 sempre (o Int escapava
     *  pelo affinity numérico do SQLite; o Bool expôs). Fix no
     *  {@code ExpressionOrmCallLowerer}: face Bool emite {@code kof_box_bool} no
     *  Native (os demais primitivos seguem no valueOf/TEXTO — residuo §447).
     *
     *  <p>Face (b) diagnosticada: valor de forma inválida é throw ORM001 no
     *  Native (honesto) e 0 silencioso no host JVM (o golden JVM abaixo
     *  registra a divergência medida; a lane do host decide o R6 de lá). */
    @Test
    void crossNativeF3aCountWhereMatchesX86Oracle(@TempDir Path tempDir) throws IOException {
        assumeTrue(isLinux(), "cross ORM E2E requires Linux + libsqlite3");
        String template = """
            entity User {
                id: Long generated
                name: String
                email: String unique
                age: Int
            }
            entity Flagged {
                id: Long generated
                ok: Bool
                ratio: Float
                price: Double
                tag: Long
            }
            main() {
                var db = db.connect("sqlite:%s/kof.db")
                println(orm.create<User>(db))
                db.execute(db, "insert into user (name, email, age) values ('Mel', 'm@kof.dev', 30)")
                db.execute(db, "insert into user (name, email, age) values ('Ana', 'a@kof.dev', 41)")
                println(orm.count<User>(db, "name", "Mel"))
                println(orm.count<User>(db, "age", 30))
                println(orm.count<User>(db, "email", "nope@x.io"))
                println(orm.count<User>(db, "name", "x' OR 1=1 --"))
                println(orm.count<User>(db, "age", -7))
                println(orm.create<Flagged>(db))
                db.execute(db, "insert into flagged (ok, ratio, price, tag) values (1, 1.5, 2.25, 7)")
                println(orm.count<Flagged>(db, "ok", true))
                println(orm.count<Flagged>(db, "ok", 1))
                println(db.query(db, "select ok, typeof(ok) as t from flagged").get(0))
                db.execute(db, "insert into flagged (ok, ratio, price, tag) values ('true', 1.5, 2.25, 8)")
                db.execute(db, "insert into flagged (ok, ratio, price, tag) values (0, 1.5, 2.25, 9)")
                var b: Bool = true
                println(orm.count<Flagged>(db, "ok", true))
                println(orm.count<Flagged>(db, "ok", b))
                println(orm.count<Flagged>(db, "ok", false))
                println(orm.count<Flagged>(db, "price", 2.25))
                var r: Float = 1.5
                println(orm.count<Flagged>(db, "ratio", r))
                var l: Long = 7
                println(orm.count<Flagged>(db, "tag", l))
                try {
                    println(orm.count<User>(db, "age", listOf(1, 2)))
                } catch (String e) {
                    println(e)
                }
                println("after-throw")
                db.close(db)
            }
            """;
        String golden = "true\n1\n1\n0\n0\n0\ntrue\n1\n1\n{\"ok\":1,\"t\":\"integer\"}\n"
                + "1\n1\n1\n3\n3\n1\n"
                + "orm.count bind value: unsupported type on Native (ORM001)\nafter-throw";
        String oracle = runX86CreateOracle(tempDir, "countwhere", template);
        assertEquals(golden, oracle,
                "oráculo x86-64 (count_where: matriz de tags + §447 bool literal + ORM001)");
        assertCrossCreateParity(tempDir, "countwhere", template, oracle);
        // host JVM (b): bate com o oráculo na matriz toda; a UNICA linha que
        // diverge e o valor de forma invalida (0 silencioso la, ORM001 aqui).
        Path jvmDir = tempDir.resolve("countwhere-jvm");
        Files.createDirectories(jvmDir);
        Path jvmSource = jvmDir.resolve("Main.kf");
        Files.writeString(jvmSource, template.formatted(kofPath(jvmDir))
                .replace("sqlite:" + kofPath(jvmDir), "jdbc:sqlite:" + kofPath(jvmDir)));
        String goldenJvm = golden.replace(
                "orm.count bind value: unsupported type on Native (ORM001)", "0");
        runJvmWithExtra(jvmSource, jvmDir.resolve("out"),
                findDriverJar("sqlite-jdbc", "SQLite"), goldenJvm);
    }

    /** DB-3/DB-1 cross slice E (22/09): {@code orm.delete} REAL no riscv64/
     *  aarch64 (peça RtB54, port de RuntimeOrm9) — a PK resolvida pelo parser
     *  de schema portado de RuntimeOrmSchema (primeiro campo {@code generated},
     *  senão 0), SQL {@code DELETE FROM "t" WHERE "pk" = ?} (tabela e PK com
     *  aspas duplas, key pelo classificador box §284/KofString/null) e sempre
     *  {@code true} no SQLITE_DONE — inclusive no miss (host {@code execute1 >= 0}).
     *  Q3: PK Long grande (&gt; int32, prova do bind int64) e negativa, miss,
     *  re-delete, id ruim → throw + recuperação. Byte-parity com o oráculo
     *  x86-64. */
    @Test
    void crossNativeF2c3DeleteMatchesX86Oracle(@TempDir Path tempDir) throws IOException {
        assumeTrue(isLinux(), "cross ORM E2E requires Linux + libsqlite3");
        String template = """
            entity User {
                id: Long generated
                name: String
                email: String unique
                age: Int
            }
            main() {
                var db = db.connect("sqlite:%s/kof.db")
                println(orm.create<User>(db))
                db.execute(db, "insert into user (name, email, age) values ('Mel', 'm@kof.dev', 30)")
                db.execute(db, "insert into user (name, email, age) values ('Ana', 'a@kof.dev', 25)")
                db.execute(db, "insert into user (name, email, age) values ('Bia', 'b@kof.dev', 41)")
                db.execute(db, "insert into user (id, name, email, age) values (5000000000, 'Big', 'big@kof.dev', 7)")
                db.execute(db, "insert into user (id, name, email, age) values (-7, 'Neg', 'neg@kof.dev', 8)")
                println(orm.count<User>(db))
                println(orm.delete<User>(db, 2))
                println(orm.count<User>(db))
                println(orm.delete<User>(db, 999))
                println(orm.count<User>(db))
                var big: Long = 5000000000
                println(orm.delete<User>(db, big))
                var neg: Long = -7
                println(orm.delete<User>(db, neg))
                println(orm.count<User>(db))
                println(db.query(db, "select name from user order by id").get(0))
                println(orm.delete<User>(db, 1))
                println(orm.delete<User>(db, 3))
                println(orm.delete<User>(db, 3))
                println(orm.count<User>(db))
                try {
                    println(orm.delete<User>("db2", 1))
                } catch (String e) {
                    println(e)
                }
                println("after-throw")
                db.close(db)
            }
            """;
        String golden = "true\n5\ntrue\n4\ntrue\n4\ntrue\ntrue\n2\n{\"name\":\"Mel\"}\n"
                + "true\ntrue\ntrue\n0\nunknown db connection: db2\nafter-throw";
        String oracle = runX86CreateOracle(tempDir, "delete", template);
        assertEquals(golden, oracle,
                "oráculo x86-64 (delete: PK do schema, miss true, Long>int32, negativo, id ruim)");
        assertCrossCreateParity(tempDir, "delete", template, oracle);
    }

    /** DB-3/DB-1 cross slice E-parte-2a (22/09): {@code orm.save} REAL no
     *  riscv64/aarch64 (peça RtB55, port de {@code RuntimeOrm4}) — INSERT sem
     *  a pk + generated key (nova instância com a pk patchada), UPDATE hit
     *  (mesma instância) e upsert (UPDATE 0 linhas → INSERT de tudo). Pk pelo
     *  parser (primeiro {@code generated}); double/float truncado (fcvt.l.*
     *  rtz), String null e bool pelos caminhos do host. Byte-parity com o
     *  oráculo x86-64 + perna JVM. */
    @Test
    void crossNativeF2aSaveMatchesX86Oracle(@TempDir Path tempDir) throws IOException {
        assumeTrue(isLinux(), "cross ORM E2E requires Linux + libsqlite3");
        String template = """
            entity User {
                id: Long generated
                name: String
                email: String unique
                age: Int
            }
            main() {
                var db = db.connect("sqlite:%s/kof.db")
                println(orm.create<User>(db))
                var u = User(0, "Mel", "m@kof.dev", 30)
                var saved = orm.save<User>(db, u)
                println(saved.id)
                println(orm.count<User>(db))
                var u2 = User(saved.id, "Mel2", "m2@kof.dev", 31)
                var saved2 = orm.save<User>(db, u2)
                println(saved2.id)
                println(orm.count<User>(db))
                println(db.query(db, "select name from user where id = " + saved.id).get(0))
                var u3 = User(9, "Ana", "a@kof.dev", 25)
                println(orm.save<User>(db, u3).id)
                println(orm.count<User>(db))
                var u4 = User(0, "Bia", "b@kof.dev", 41)
                var s4 = orm.save<User>(db, u4)
                println(s4.id)
                println(orm.count<User>(db))
                try {
                    orm.save<User>("db2", User(0, "X", "x@kof.dev", 1))
                } catch (String e) {
                    println(e)
                }
                println("after-throw")
                db.close(db)
            }
            """;
        String golden = "true\n1\n1\n1\n1\n{\"name\":\"Mel2\"}\n9\n2\n10\n3\n"
                + "unknown db connection: db2\nafter-throw";
        String oracle = runX86CreateOracle(tempDir, "save", template);
        assertEquals(golden, oracle,
                "oráculo x86-64 (save: INSERT gerado, UPDATE hit, upsert pk=9, novo gerado, id ruim)");
        assertCrossCreateParity(tempDir, "save", template, oracle);
    }

    /** DB-3/DB-1 cross slice E-parte-2b (23/09): {@code orm.saveAll} REAL no
     *  riscv64/aarch64 (peça RtB56, port de {@code RuntimeOrm10}) — loop
     *  {@code kof_list_get}→{@code kof_orm_save}; lote de INSERTs, lote de
     *  UPDATEs por pk, lista vazia (true, sem tocar no banco) e id ruim →
     *  throw. Byte-parity com o oráculo x86-64 + perna JVM. */
    @Test
    void crossNativeF2aSaveAllMatchesX86Oracle(@TempDir Path tempDir) throws IOException {
        assumeTrue(isLinux(), "cross ORM E2E requires Linux + libsqlite3");
        String template = """
            entity User {
                id: Long generated
                name: String
                email: String unique
                age: Int
            }
            main() {
                var db = db.connect("sqlite:%s/kof.db")
                println(orm.create<User>(db))
                var l1 = new List<User>()
                l1.add(User(0, "Mel", "m@kof.dev", 30))
                l1.add(User(0, "Ana", "a@kof.dev", 25))
                println(orm.saveAll<User>(db, l1))
                println(orm.count<User>(db))
                println(db.query(db, "select name from user order by id").get(0))
                println(db.query(db, "select name from user order by id").get(1))
                var l2 = new List<User>()
                l2.add(User(1, "Mel2", "m2@kof.dev", 31))
                l2.add(User(2, "Ana2", "a2@kof.dev", 26))
                println(orm.saveAll<User>(db, l2))
                println(orm.count<User>(db))
                println(db.query(db, "select name from user where id = 1").get(0))
                var empty = new List<User>()
                println(orm.saveAll<User>(db, empty))
                println(orm.count<User>(db))
                try {
                    orm.saveAll<User>("db2", l1)
                } catch (String e) {
                    println(e)
                }
                println("after-throw")
                db.close(db)
            }
            """;
        String golden = "true\ntrue\n2\n{\"name\":\"Mel\"}\n{\"name\":\"Ana\"}\n"
                + "true\n2\n{\"name\":\"Mel2\"}\ntrue\n2\n"
                + "unknown db connection: db2\nafter-throw";
        String oracle = runX86CreateOracle(tempDir, "saveall", template);
        assertEquals(golden, oracle,
                "oráculo x86-64 (saveAll: lote INSERT, count, ordem, lote UPDATE, vazio true, id ruim)");
        assertCrossCreateParity(tempDir, "saveall", template, oracle);
    }

    /** DB-3/DB-1 cross slice E-parte-3 (23/09): {@code orm.find} REAL no
     *  riscv64/aarch64 (peça RtB57, port de {@code RuntimeOrm5}) — SELECT por
     *  PK, resolver {@code kof_orm_ctors} por-programa, leitura por NOME/
     *  typeCode (Int/Long/Bool/Double/Float/String, NULL→null, bool §397).
     *  Q3: hit 4 campos, miss→null, Long&gt;int32, negativo, key String (coerce
     *  TEXT↔pk INTEGER), tipos todos (Item), coluna ausente→throw ORM006
     *  "no column", id ruim→throw. Byte-parity com o oráculo x86-64. */
    @Test
    void crossNativeF2bFindMatchesX86Oracle(@TempDir Path tempDir) throws IOException {
        assumeTrue(isLinux(), "cross ORM E2E requires Linux + libsqlite3");
        String template = """
            entity User {
                id: Long generated
                name: String
                email: String unique
                age: Int
            }
            entity Item {
                id: Long generated
                flag: Bool
                ratio: Float
                price: Double
                note: String
                qty: Int
                big: Long
            }
            entity Ghost {
                id: Long generated
                name: String
            }
            main() {
                var db = db.connect("sqlite:%s/kof.db")
                println(orm.create<User>(db))
                println(orm.create<Item>(db))
                db.execute(db, "insert into user (name, email, age) values ('Mel', 'm@kof.dev', 30)")
                db.execute(db, "insert into user (name, email, age) values ('Ana', 'a@kof.dev', 25)")
                db.execute(db, "insert into user (id, name, email, age) values (5000000000, 'Big', 'big@kof.dev', 7)")
                db.execute(db, "insert into user (id, name, email, age) values (-7, 'Neg', 'neg@kof.dev', 8)")
                var mel = orm.find<User>(db, 1)
                println(mel.id)
                println(mel.name)
                println(mel.email)
                println(mel.age)
                println(mel)
                var g = orm.find<User>(db, 999)
                if (g == null) {
                    println("null")
                } else {
                    println("hit")
                }
                println(orm.find<User>(db, 2).name)
                println(orm.find<User>(db, "1").name)
                var bigKey: Long = 5000000000
                println(orm.find<User>(db, bigKey).name)
                var negKey: Long = -7
                println(orm.find<User>(db, negKey).name)
                db.execute(db, "insert into item (flag, ratio, price, note, qty, big) values (1, 1.5, 2.25, 'hello', 7, 5000000000)")
                db.execute(db, "insert into item (flag, ratio, price, note, qty, big) values (0, 0, 0, NULL, 0, -7)")
                var i1 = orm.find<Item>(db, 1)
                println(i1.flag)
                println(i1.ratio)
                println(i1.price)
                println(i1.note)
                println(i1.qty)
                println(i1.big)
                println(i1)
                var i2 = orm.find<Item>(db, 2)
                println(i2.flag)
                println(i2.note)
                db.execute(db, "create table ghost (id integer primary key, extra text)")
                db.execute(db, "insert into ghost (id, extra) values (1, 'x')")
                try {
                    println(orm.find<Ghost>(db, 1))
                } catch (String e) {
                    println(e)
                }
                println("after-nomatch")
                try {
                    println(orm.find<User>("db2", 1))
                } catch (String e) {
                    println(e)
                }
                println("after-throw")
                try {
                    println(orm.find<User>(db, true))
                } catch (String e) {
                    println(e)
                }
                println("after-bool")
                db.close(db)
            }
            """;
        String golden = "true\ntrue\n1\nMel\nm@kof.dev\n30\n"
                + "User[id=1, name=Mel, email=m@kof.dev, age=30]\nnull\nAna\nMel\nBig\nNeg\n"
                + "true\n1.5\n2.25\nhello\n7\n5000000000\n"
                + "Item[id=1, flag=true, ratio=1.5, price=2.25, note=hello, qty=7, big=5000000000]\n"
                + "false\nnull\nsqlite: no column \"name\"\nafter-nomatch\n"
                + "unknown db connection: db2\nafter-throw\n"
                + "orm.find bind value: unsupported type on Native (ORM001)\nafter-bool";
        String oracle = runX86CreateOracle(tempDir, "find", template);
        assertEquals(golden, oracle,
                "oráculo x86-64 (find: hit 4 campos, miss→null, Long>int32, negativo, key String, "
                        + "Item todos os tipos, coluna ausente→throw, id ruim)");
        assertCrossCreateParity(tempDir, "find", template, oracle);
    }

    @Test
    void crossNativeF2c1AllMatchesX86Oracle(@TempDir Path tempDir) throws IOException {
        assumeTrue(isLinux(), "cross ORM E2E requires Linux + libsqlite3");
        String template = """
            entity User {
                id: Long generated
                name: String
                email: String unique
                age: Int
            }
            entity Row {
                id: Long generated
                name: String
                n: Int
            }
            entity Item {
                id: Long generated
                flag: Bool
                ratio: Float
                price: Double
                note: String
                qty: Int
                big: Long
            }
            entity Ghost {
                id: Long generated
                name: String
            }
            main() {
                var db = db.connect("sqlite:%s/kof.db")
                println(orm.create<User>(db))
                println(orm.create<Row>(db))
                println(orm.create<Item>(db))
                println(orm.create<Ghost>(db))
                var empty = orm.all<User>(db)
                println(empty.size)
                db.execute(db, "insert into row (id, name, n) values (9, 'nine', 90)")
                db.execute(db, "insert into row (id, name, n) values (2, 'two', 20)")
                db.execute(db, "insert into row (id, name, n) values (5, 'five', 50)")
                var rows = orm.all<Row>(db)
                println(rows.size)
                for (var r in rows) {
                    println(r.id)
                    println(r.name)
                    println(r.n)
                }
                var ghosts = orm.all<Ghost>(db)
                println(ghosts.size)
                db.execute(db, "insert into user (name, email, age) values ('Mel', 'm@kof.dev', 30)")
                var users = orm.all<User>(db)
                println(users.size)
                for (var u in users) {
                    println(u.id)
                    println(u.name)
                    println(u.email)
                    println(u.age)
                }
                db.execute(db, "insert into item (flag, ratio, price, note, qty, big) values (1, 1.5, 2.25, 'hello', 7, 5000000000)")
                db.execute(db, "insert into item (flag, ratio, price, note, qty, big) values (0, 0, 0, NULL, 0, -7)")
                var items = orm.all<Item>(db)
                println(items.size)
                for (var it in items) {
                    println(it.id)
                    println(it.flag)
                    println(it.ratio)
                    println(it.price)
                    println(it.note)
                    println(it.qty)
                    println(it.big)
                }
                try {
                    var bad = orm.all<User>("db2")
                    println(bad.size)
                } catch (String e) {
                    println(e)
                }
                println("after-throw")
                db.close(db)
            }
            """;
        String golden = "true\ntrue\ntrue\ntrue\n0\n3\n"
                + "2\ntwo\n20\n5\nfive\n50\n9\nnine\n90\n"
                + "0\n1\n1\nMel\nm@kof.dev\n30\n2\n"
                + "1\ntrue\n1.5\n2.25\nhello\n7\n5000000000\n"
                + "2\nfalse\n0.0\n0.0\nnull\n0\n-7\n"
                + "unknown db connection: db2\nafter-throw";
        String oracle = runX86CreateOracle(tempDir, "all", template);
        assertEquals(golden, oracle,
                "oráculo x86-64 (all: lista vazia→List vazia, 3 linhas ordem rowid independente da "
                        + "inserção, multi-entidade, Item todos os tipos incl. zero/null, id ruim→throw)");
        assertCrossCreateParity(tempDir, "all", template, oracle);
    }

    @Test
    void crossNativeF2c2WhereMatchesX86Oracle(@TempDir Path tempDir) throws IOException {
        assumeTrue(isLinux(), "cross ORM E2E requires Linux + libsqlite3");
        String template = """
            entity User {
                id: Long generated
                name: String
                age: Int
                flag: Bool
                score: Double
            }
            entity Ghost {
                id: Long generated
                name: String
            }
            main() {
                var db = db.connect("sqlite:%s/kof.db")
                println(orm.create<User>(db))
                db.execute(db, "insert into user (name, age, flag, score) values ('Mel', 30, 1, 9.5)")
                db.execute(db, "insert into user (name, age, flag, score) values ('Ana', 25, 0, 3.25)")
                db.execute(db, "insert into user (name, age, flag, score) values ('Bia', 30, 1, 7.0)")
                var a = orm.where<User>(db, "age", 30)
                println(a.size)
                for (var u in a) {
                    println(u.id)
                    println(u.name)
                    println(u.flag)
                    println(u.score)
                }
                var b = orm.where<User>(db, "age", ">=", 26)
                println(b.size)
                var c = orm.where<User>(db, "name", "LIKE", "A%%")
                println(c.size)
                for (var u in c) {
                    println(u.name)
                }
                var d = orm.where<User>(db, "age", "!=", 30)
                println(d.size)
                var e = orm.where<User>(db, "age", 999)
                println(e.size)
                try {
                    var x = orm.where<User>(db, "age", "??", 1)
                    println(x.size)
                } catch (String ex) {
                    println(ex)
                }
                println("after-op")
                db.execute(db, "create table ghost (id integer primary key, extra text)")
                db.execute(db, "insert into ghost (id, extra) values (1, 'x')")
                try {
                    var y = orm.where<Ghost>(db, "id", 1)
                    println(y.size)
                } catch (String ex) {
                    println(ex)
                }
                println("after-nomatch")
                db.close(db)
            }
            """;
        String golden = "true\n2\n"
                + "1\nMel\ntrue\n9.5\n3\nBia\ntrue\n7.0\n"
                + "2\n1\nAna\n1\n"
                + "0\n"
                + "ORM operator not allowed: ??\nafter-op\n"
                + "sqlite: no column \"name\"\nafter-nomatch";
        String oracle = runX86CreateOracle(tempDir, "where", template);
        assertEquals(golden, oracle,
                "oráculo x86-64 (where: =, >=, LIKE, !=, vazio→List vazia, op inválido→throw, "
                        + "campo sem coluna→throw; Bool e Double lidos)");
        assertCrossCreateParity(tempDir, "where", template, oracle);
    }

    @Test
    void crossNativeF2c3PageMatchesX86Oracle(@TempDir Path tempDir) throws IOException {
        assumeTrue(isLinux(), "cross ORM E2E requires Linux + libsqlite3");
        String template = """
            entity User {
                id: Long generated
                name: String
                age: Int
                score: Double
            }
            entity Ghost {
                id: Long generated
                name: String
            }
            main() {
                var db = db.connect("sqlite:%s/kof.db")
                println(orm.create<User>(db))
                db.execute(db, "insert into user (name, age, score) values ('n1', 10, 1.5)")
                db.execute(db, "insert into user (name, age, score) values ('n2', 20, 2.5)")
                db.execute(db, "insert into user (name, age, score) values ('n3', 30, 3.5)")
                db.execute(db, "insert into user (name, age, score) values ('n4', 40, 4.5)")
                db.execute(db, "insert into user (name, age, score) values ('n5', 50, 5.5)")
                var a = orm.page<User>(db, 2, 0)
                println(a.size)
                for (var u in a) {
                    println(u.id)
                    println(u.name)
                }
                var b = orm.page<User>(db, 2, 2)
                println(b.size)
                for (var u in b) {
                    println(u.id)
                    println(u.name)
                }
                var c = orm.page<User>(db, 10, 0)
                println(c.size)
                var d = orm.page<User>(db, 2, 10)
                println(d.size)
                var e = orm.page<User>(db, 0, 0)
                println(e.size)
                var lim: Long = 3
                var off: Long = 1
                var f = orm.page<User>(db, lim, off)
                println(f.size)
                for (var u in f) {
                    println(u.id)
                    println(u.name)
                }
                var g = orm.page<User>(db, 2.9, 0.0)
                println(g.size)
                db.execute(db, "create table ghost (id integer primary key, extra text)")
                db.execute(db, "insert into ghost (id, extra) values (1, 'x')")
                try {
                    var y = orm.page<Ghost>(db, 1, 0)
                    println(y.size)
                } catch (String ex) {
                    println(ex)
                }
                println("after-nomatch")
                db.close(db)
            }
            """;
        String golden = "true\n2\n1\nn1\n2\nn2\n2\n3\nn3\n4\nn4\n5\n0\n0\n3\n"
                + "2\nn2\n3\nn3\n4\nn4\n2\n"
                + "sqlite: no column \"name\"\nafter-nomatch";
        String oracle = runX86CreateOracle(tempDir, "page", template);
        assertEquals(golden, oracle,
                "oráculo x86-64 (page: LIMIT/OFFSET, offset além, limit 0, Long→intValue, "
                        + "Double 2.9→2 truncado, campo sem coluna→throw)");
        assertCrossCreateParity(tempDir, "page", template, oracle);
    }

    /** x86-64: compila e roda o template numa pasta propria (banco limpo) e
     *  devolve o stdout — o oráculo do contrato D-DB-GAPS. */
    private String runX86CreateOracle(Path tempDir, String label, String template) throws IOException {
        Path out = tempDir.resolve("oracle-" + label);
        Files.createDirectories(out);
        Path source = out.resolve("Main.kf");
        Files.writeString(source, template.formatted(out));
        CompilationResult r = driver.compile(source, out, Target.NATIVE);
        assumeTrue(r.success(), "x86-64 oracle should compile: " + r.diagnostics().getDiagnostics());
        return runNativeBinary(out.resolve("Default/Main"), null);
    }

    /** riscv64/aarch64: mesma pasta-por-alvo (banco limpo) + byte-parity com o
     *  oráculo x86-64; sem toolchain/sysroot/sqlite = skip honesto. */
    private void assertCrossCreateParity(Path tempDir, String label, String template, String oracle)
            throws IOException {
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String arch = t.nativeArch();
            String as = arch.equals("riscv64") ? "riscv64-linux-gnu-as" : "aarch64-linux-gnu-as";
            String ld = arch.equals("riscv64") ? "riscv64-linux-gnu-ld" : "aarch64-linux-gnu-ld";
            assumeTrue(has(as, ld, "qemu-" + arch), "cross toolchain " + arch + " ausente — pulando");
            assumeTrue(dev.kof.compiler.nat.NativeCrossLink.sysrootOrNull(arch) != null,
                    "sysroot cross " + arch + " ausente — pulando");
            assumeTrue(dev.kof.compiler.nat.NativeCrossLink.sqliteAvailable(arch),
                    "libsqlite3 " + arch + " ausente no sysroot — pulando");
            Path out = tempDir.resolve(label + "-" + t);
            Files.createDirectories(out);
            Path source = out.resolve("Main.kf");
            Files.writeString(source, template.formatted(out));
            CompilationResult r = driver.compile(source, out, t);
            assertTrue(r.success(), t + " deveria compilar F1d real: " + r.diagnostics().getDiagnostics());
            String got = runNativeBinary(out.resolve("Default/Main"), "qemu-" + arch);
            assertEquals(oracle, got, t + " byte-parity com o oráculo x86-64 (" + label + ")");
        }
    }

    /** Executa o binário nativo (x86 direto; cross via qemu-<arch> com
     *  QEMU_LD_PREFIX do sysroot) e devolve o stdout com exit 0 exigido. */
    private static String runNativeBinary(Path bin, String qemu) throws IOException {
        ProcessBuilder pb = qemu == null
                ? new ProcessBuilder(bin.toString())
                : new ProcessBuilder(qemu, bin.toString());
        if (qemu != null) {
            String arch = qemu.endsWith("riscv64") ? "riscv64" : "aarch64";
            String prefix = qemuPrefix(arch);
            if (prefix != null) pb.environment().put("QEMU_LD_PREFIX", prefix);
        }
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec;
        try {
            ec = p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted running native binary " + bin, e);
        }
        assertEquals(0, ec, "exit code, output: '" + out + "'");
        return out;
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

    /** QEMU_LD_PREFIX p/ o loader dinâmico (espelho de KofDbE2ETest). */
    private static String qemuPrefix(String arch) {
        String env = System.getenv("KOF_CROSS_SYSROOT");
        String loader = arch.equals("riscv64") ? "ld-linux-riscv64-lp64d.so.1" : "ld-linux-aarch64.so.1";
        if (env != null && !env.isBlank()) {
            java.io.File f = new java.io.File(env + "/usr/" + arch + "-linux-gnu/lib/" + loader);
            if (f.exists()) return env + "/usr/" + arch + "-linux-gnu";
        }
        java.io.File sys = new java.io.File("/usr/" + arch + "-linux-gnu/lib/" + loader);
        return sys.exists() ? "/usr/" + arch + "-linux-gnu" : null;
    }

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase().contains("linux");
    }
}