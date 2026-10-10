package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §566 — uma {@code entity} declarada em um pacote NOMEADO tem que persistir
 * (JVM). O lowerer ORM passava o NOME SIMPLES do type-argument para o runtime
 * ({@code Class.forName("Erec")}) em vez do nome BINÁRIO ({@code app.Erec}) —
 * compilava limpo, {@code app/Erec.class} era emitido, e a primeira leitura
 * morria {@code NoClassDefFoundError: Erec}.
 *
 * Espelha o caminho de resolução de pacote de enums/records (§308/D-ENUM207):
 * o mesmo {@code CompilerTypes.toType}/{@code qualifyDeep} que já qualifica o
 * tipo de retorno do {@code orm.find<T>} tem que qualificar o {@code className}
 * entregue ao runtime. Sem regressão para o root package (nome simples continua
 * {@code Erec}).
 */
class OrmEntityPackageE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private Path write(Path root, String rel, String src) throws Exception {
        Path f = root.resolve(rel);
        Files.createDirectories(f.getParent() == null ? root : f.getParent());
        Files.writeString(f, src);
        return f;
    }

    /**
     * Invoca por reflexão (não pelo launcher `java -cp ... Default.Main`): o
     * launcher mascara qualquer falha de load/link da classe principal atrás
     * de "os componentes de runtime do JavaFX não foram encontrados" (§556,
     * regra do AGENTS.md). A reflexão expõe a causa raiz — é o
     * {@code NoClassDefFoundError: Erec} do §566.
     */
    private String runJvm(Path root, Path source, String tag, String expected) throws Exception {
        Path outDir = root.resolve("out-" + tag);
        CompilationResult r = driver.compileSources(java.util.List.of(source), outDir, Target.JVM, root);
        assertTrue(r.success(), tag + " compile failed: " + r.diagnostics().getDiagnostics());
        var oldOut = System.out;
        var buf = new java.io.ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(buf, true));
        try (java.net.URLClassLoader cl = new java.net.URLClassLoader(
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
    void entityInNamedPackagePersistsJvm(@TempDir Path root) throws Exception {
        Path source = write(root, "app/Main.kf", """
                package app

                import kof.db
                import kof.orm

                entity Erec {
                    id: String unique
                    label: String
                    count: Int
                }

                main() {
                    var db = db.connect("jdbc:h2:mem:ormpkg;DB_CLOSE_DELAY=-1")
                    orm.create<Erec>(db)
                    orm.save(db, Erec("1", "hello", 7))
                    var all = orm.all<Erec>(db)
                    println(all.size)
                    var f = orm.find<Erec>(db, "1")
                    println(f.label)
                    println(f.count)
                    db.close(db)
                }
                """);
        runJvm(root, source, "named-pkg", "1\nhello\n7");
        assertTrue(Files.exists(root.resolve("out-named-pkg").resolve("app/Erec.class")),
                "app/Erec.class deve ser emitido no diretório do pacote");
    }

    @Test
    void entityInRootPackageStillPersistsJvm(@TempDir Path root) throws Exception {
        Path source = write(root, "Main.kf", """
                import kof.db
                import kof.orm

                entity Erec {
                    id: String unique
                    label: String
                    count: Int
                }

                main() {
                    var db = db.connect("jdbc:h2:mem:ormroot;DB_CLOSE_DELAY=-1")
                    orm.create<Erec>(db)
                    orm.save(db, Erec("1", "root", 3))
                    var f = orm.find<Erec>(db, "1")
                    println(f.label)
                    println(f.count)
                    db.close(db)
                }
                """);
        runJvm(root, source, "root-pkg", "root\n3");
    }
}
