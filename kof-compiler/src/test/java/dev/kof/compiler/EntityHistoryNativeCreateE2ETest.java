package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** entity-history-plan Phase 5 slice 5a (D-ENTITY-HISTORY; the maintainer's
 *  13/10 "ponta a ponta" order): `orm.createHistory<User>(db)` on the NATIVE
 *  x86-64 backend — the {@code kof_orm_create_history} asm emits the fixedschema {@code "user_history"} DDL (mirroring the JVM host). The proof:
 *  the table EXISTS with the 8 columns (read back via raw SQL on the SAME
 *  program). MySQL connections keep the honest runtime ORM001 (the native
 *  ORM's SQLite-only contract); the cross targets keep the compile-time
 *  ORM001 (unchanged). */
class EntityHistoryNativeCreateE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void nativeCreateHistoryMakesTheTable() throws Exception {
        assumeTrue(dev.kof.compiler.nat.NativeToolchainGate.present(),
                "x86-64 toolchain (as/ld) ausente — pulando");
        String source = """
                import kof.orm.history
                audited entity User {
                    id: Long generated
                    name: String
                }
                main() {
                    var db = db.connect("sqlite:%s/hist.db")
                    println(orm.createHistory<User>(db))
                    var cols = db.query(db, "select count(*) as c from pragma_table_info('user_history')")
                    for (var r in cols) {
                        println(r)
                    }
                    var n = db.query(db, "select count(*) as c from sqlite_master where name = 'user_history' and type = 'table'")
                    for (var r in n) {
                        println(r)
                    }
                    db.close(db)
                }
                """.formatted(tmp);
        Path out = tmp.resolve("out-native");
        CompilationResult r = driver.compile(
                writeFile("Main.kf", source), out, Target.NATIVE);
        assertTrue(r.success(), () -> "native compile: " + r.diagnostics().getDiagnostics());
        String got = runNative(out.resolve("Default/Main"));
        assertEquals("true\n{\"c\":8}\n{\"c\":1}", got,
                "a tabela user_history existe com as 8 colunas (DDL Fase 5)");
    }

    @Test
    void nativeDeleteRecordedRecordsTheRow() throws Exception {
        assumeTrue(dev.kof.compiler.nat.NativeToolchainGate.present(),
                "x86-64 toolchain (as/ld) ausente — pulando");
        java.nio.file.Path dbgDir = Path.of("/tmp/histdbg-run");
        java.nio.file.Files.createDirectories(dbgDir);
        java.nio.file.Files.deleteIfExists(dbgDir.resolve("hist5b.db"));
        String source = """
                import kof.orm.history
                audited entity User {
                    id: Long generated
                    name: String
                }
                main() {
                    var db = db.connect("sqlite:%s/hist5b.db")
                    orm.create<User>(db)
                    orm.createHistory<User>(db)
                    db.execute(db, "insert into user(id, name) values (7, 'Mel')")
                    println(orm.delete<User>(db, 7))
                    var rows = db.query(db, "select revision, operation from user_history order by revision")
                    for (var r in rows) {
                        println(r)
                    }
                    db.close(db)
                }
                """.formatted(dbgDir);
        Path out = tmp.resolve("out-native5b");
        CompilationResult r = driver.compile(
                writeFile("Main5b.kf", source), out, Target.NATIVE);
        assertTrue(r.success(), () -> "native compile: " + r.diagnostics().getDiagnostics());
        String got = runNative(out.resolve("Default/Main"));
        assertEquals("true\n{\"revision\":1,\"operation\":\"DELETE\"}",
                got, "a revisão DELETE gravada pelo nativo (o snapshot é a chave; "
                   + "o frame alinhado 88 = o conserto do segv)");
    }

    @Test
    void nativeHistoryQueryReturnsTheRevisions() throws Exception {
        assumeTrue(dev.kof.compiler.nat.NativeToolchainGate.present(),
                "x86-64 toolchain (as/ld) ausente — pulando");
        java.nio.file.Path dbgDir = Path.of("/tmp/histdbg-run");
        Files.createDirectories(dbgDir);
        Files.deleteIfExists(dbgDir.resolve("hist5d.db"));
        String source = """
                import kof.orm.history
                audited entity User {
                    id: Long generated
                    name: String
                }
                main() {
                    var db = db.connect("sqlite:%s/hist5d.db")
                    orm.create<User>(db)
                    orm.createHistory<User>(db)
                    db.execute(db, "insert into user_history(revision, entity_pk, operation, at, payload, actor, actor_kind, correlation_id) values (1, '7', 'INSERT', 100, '{}', NULL, 'system', NULL)")
                    db.execute(db, "insert into user_history(revision, entity_pk, operation, at, payload, actor, actor_kind, correlation_id) values (2, '7', 'UPDATE', 200, '{}', NULL, 'system', NULL)")
                    var rs = User.history(db, 7)
                    println(rs.size)
                    println(rs.get(0).operation)
                    println(rs.get(1).operation)
                    db.close(db)
                }
                """.formatted(dbgDir);
        Path out = tmp.resolve("out-native5d");
        CompilationResult r = driver.compile(
                writeFile("Main5d.kf", source), out, Target.NATIVE);
        assertTrue(r.success(), () -> "native compile: " + r.diagnostics().getDiagnostics());
        assertEquals("2\nINSERT\nUPDATE", runNative(out.resolve("Default/Main")),
                "a consulta ascendente no nativo (a Fase 5 fatia 5d-1)");
    }

    @Test
    void nativeHistoryPageAndOneVariants() throws Exception {
        assumeTrue(dev.kof.compiler.nat.NativeToolchainGate.present(),
                "x86-64 toolchain (as/ld) ausente — pulando");
        java.nio.file.Path dbgDir = Path.of("/tmp/histdbg-run");
        Files.createDirectories(dbgDir);
        Files.deleteIfExists(dbgDir.resolve("hist5e.db"));
        String source = """
                import kof.orm.history
                audited entity User {
                    id: Long generated
                    name: String
                }
                main() {
                    var db = db.connect("sqlite:%s/hist5e.db")
                    orm.create<User>(db)
                    orm.createHistory<User>(db)
                    db.execute(db, "insert into user_history(revision, entity_pk, operation, at, payload, actor, actor_kind, correlation_id) values (1, '7', 'INSERT', 100, '{}', NULL, 'system', NULL)")
                    db.execute(db, "insert into user_history(revision, entity_pk, operation, at, payload, actor, actor_kind, correlation_id) values (2, '7', 'UPDATE', 200, '{}', NULL, 'system', NULL)")
                    var pg = User.history(db, 7, 0, 1)
                    println(pg.size)
                    println(pg.get(0).operation)
                    var one = User.history(db, 7, 2)
                    if (one != null) {
                        println(one.operation)
                    }
                    var miss = User.history(db, 7, 9)
                    println(miss == null)
                    db.close(db)
                }
                """.formatted(dbgDir);
        Path out = tmp.resolve("out-native5e");
        CompilationResult r = driver.compile(
                writeFile("Main5e.kf", source), out, Target.NATIVE);
        assertTrue(r.success(), () -> "native compile: " + r.diagnostics().getDiagnostics());
        // CI: runner limpo — /tmp/histdbg-last/ não existe; criar antes do
        // copy (o dir local existia por sessões anteriores, mascarando isto).
        Files.createDirectories(Path.of("/tmp/histdbg-last"));
        Files.copy(out.resolve("Default/Main"), Path.of("/tmp/histdbg-last/h5e-main"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        assertEquals("1\nINSERT\nUPDATE\ntrue", runNative(out.resolve("Default/Main")),
                "as variantes page/one no nativo (a Fase 5 fatia 5d-2)");
    }

    @Test
    void crossTargetsKeepTheHonestOrm001() throws Exception {
        String source = """
                import kof.orm.history
                audited entity User {
                    id: Long generated
                    name: String
                }
                main() {
                    var db = db.connect("sqlite:x.db")
                    println(orm.createHistory<User>(db))
                    db.close(db)
                }
                """;
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            CompilationResult r = driver.compile(writeFile("Cross.kf", source),
                    tmp.resolve("out-" + t.name().toLowerCase(java.util.Locale.ROOT)), t);
            assertFalse(r.success(), t + " mantém a recusa honesta");
            assertTrue(r.diagnostics().getDiagnostics().stream()
                            .anyMatch(d -> String.valueOf(d).contains("ORM001")),
                    t + " recusa com ORM001: " + r.diagnostics().getDiagnostics());
        }
    }

    private Path writeFile(String name, String content) throws Exception {
        Path f = tmp.resolve(name);
        Files.createDirectories(f.getParent());
        Files.writeString(f, content);
        return f;
    }

    private static String runNative(Path binary) throws Exception {
        // o preserv p/ o debug: o binário + o .s em /tmp/histdbg-last/
        try {
            Path dbg = Path.of("/tmp/histdbg-last");
            Files.createDirectories(dbg);
            Files.copy(binary, dbg.resolve("Main"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Path parent = binary.getParent();
            if (parent != null) {
                for (Path f : Files.list(parent).toList()) {
                    Files.copy(f, dbg.resolve(f.getFileName().toString()),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (Exception e) { }
        java.util.List<String> cmd = "1".equals(System.getenv("KOF_GDB"))
                ? java.util.List.of("gdb", "-batch", "-ex", "run",
                        "-ex", "info registers rdi rsi rdx r12 rbx rsp rip",
                        "-ex", "x/6i $rip-24", "--args", binary.toString())
                : java.util.List.of(binary.toString());
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        p.waitFor();
        System.out.println("NATIVE-RUN: " + out);
        return out;
    }
}
