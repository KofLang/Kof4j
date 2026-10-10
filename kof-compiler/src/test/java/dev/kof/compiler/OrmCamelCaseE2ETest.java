package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §564 (JVM ORM read path): the write path preserves camelCase column names,
 * but {@code kof_db_query_n} built every row map with
 * {@code md.getColumnLabel(i).toLowerCase()}, so {@code myLabel} became
 * {@code mylabel} and {@code kof_json_bind} looked the component name up in
 * the lowercased map — String fields silently bound NULL, primitives died
 * with the {@code ValueConversions} NPE of §565. RED-first battery: the
 * camelCase round-trip fails at the tip, the lower_snake_case shape (the
 * kof-publisher workaround) must stay green.
 */
class OrmCamelCaseE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private Path write(Path root, String rel, String src) throws Exception {
        Path f = root.resolve(rel);
        Files.createDirectories(f.getParent() == null ? root : f.getParent());
        Files.writeString(f, src);
        return f;
    }

    private String runJvm(Path root, Path source, String tag, String expected) throws Exception {
        Path outDir = root.resolve("out-" + tag);
        CompilationResult r = driver.compileSources(java.util.List.of(source), outDir, Target.JVM, root);
        assertTrue(r.success(), tag + " compile failed: " + r.diagnostics().getDiagnostics());
        var oldOut = System.out;
        var buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true));
        try (URLClassLoader cl = new URLClassLoader(
                new java.net.URL[]{outDir.toUri().toURL()}, getClass().getClassLoader())) {
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new AssertionError(tag + " JVM threw: " + e.getCause(), e.getCause());
        } finally {
            System.setOut(oldOut);
        }
        String out = buf.toString().replace("\r\n", "\n").trim();
        assertEquals(expected, out, tag + " JVM output");
        return out;
    }

    @Test
    void camelCaseEntityFieldsRoundTripJvm(@TempDir Path root) throws Exception {
        Path source = write(root, "Main.kf", """
                import kof.db
                import kof.orm

                entity Thing {
                    id: String unique
                    myLabel: String
                    myCount: Int
                }

                main() {
                    var db = db.connect("jdbc:h2:mem:ormcamel;DB_CLOSE_DELAY=-1")
                    orm.create<Thing>(db)
                    orm.save(db, Thing("1", "hello", 7))
                    var f = orm.find<Thing>(db, "1")
                    println(f.myLabel)
                    println(f.myCount)
                    db.close(db)
                }
                """);
        runJvm(root, source, "camel", "hello\n7");
    }

    @Test
    void snakeCaseEntityFieldsStayGreenJvm(@TempDir Path root) throws Exception {
        Path source = write(root, "Main.kf", """
                import kof.db
                import kof.orm

                entity Item {
                    id: String unique
                    created_at: String
                    recurrence_id: Int
                }

                main() {
                    var db = db.connect("jdbc:h2:mem:ormsnake;DB_CLOSE_DELAY=-1")
                    orm.create<Item>(db)
                    orm.save(db, Item("1", "now", 42))
                    var f = orm.find<Item>(db, "1")
                    println(f.created_at)
                    println(f.recurrence_id)
                    db.close(db)
                }
                """);
        runJvm(root, source, "snake", "now\n42");
    }
}
