package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §727 — a record/entity declared in a LIB file is fully usable by
 * the typed ORM lowering (`orm.create`/`orm.save`/`orm.all`) on the JVM.
 *
 * <p>ROOT (fixed here): {@code CompilerUnitLowering.lowerToIR} registered the
 * entity schemas ({@code driver.entitySchemas}) only when its linear loop
 * REACHED the entity declaration. A lib file merges into the unit AFTER the
 * main file, so `main()`'s {@code orm.*} calls lowered BEFORE the lib
 * entity's schema was registered — ORM002 "unknown entity" on all three
 * faces, while the plain-ctor face (no registry lookup) worked. The fix is a
 * PRE-PASS (the same order-independence argument as the {@code extern}
 * signature table above it): all entity schemas register BEFORE any body
 * lowers; the main-loop put remains (idempotent, same key/value).
 *
 * <p>RED-first: pre-fix, this test failed with ORM002 on all three orm calls
 * (measured live on the tip: ORM002 × 3).
 */
class LibRecordOrmE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String LIB = """
            package entities

            entity User {
                id: Long generated
                name: String
            }
            """;

    private static final String MAIN = """
            import entities.User

            main() {
                var db = db.connect("jdbc:h2:mem:ormlib727;DB_CLOSE_DELAY=-1")
                orm.create<User>(db)
                var mel = orm.save(db, User(1, "mel"))
                var all = orm.all<User>(db)
                println("count=" + all.size + " name=" + all.get(0).name)
            }
            """;

    private String runJvm() throws Exception {
        Path libDir = tmp.resolve("entities");
        Files.createDirectories(libDir);
        Files.writeString(libDir.resolve("User.kf"), LIB, StandardCharsets.UTF_8);
        Path main = tmp.resolve("Main.kf");
        Files.writeString(main, MAIN, StandardCharsets.UTF_8);
        Path out = tmp.resolve("out");
        CompilationResult r = driver.compile(main, out, Target.JVM);
        assertTrue(r.success(), () -> "compile: " + r.diagnostics().getDiagnostics());
        String h2 = findH2Jar();
        Process p = new ProcessBuilder("java", "-Dfile.encoding=UTF-8",
                "-Dstdout.encoding=UTF-8",
                "-cp", out + File.pathSeparator + h2, "Default.Main")
                .redirectErrorStream(true).start();
        String outTxt = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, p.waitFor(), outTxt);
        return outTxt;
    }

    private static String findH2Jar() throws Exception {
        var walk = Files.walk(Path.of(System.getProperty("user.home"), ".m2", "repository"));
        var hit = walk.filter(p -> p.getFileName().toString().matches("h2-.*\\.jar"))
                .findFirst();
        walk.close();
        assertTrue(hit.isPresent(), "the h2 jar is required (mvn resolves it)");
        return hit.get().toString();
    }

    @Test
    void ormFacesWorkWithALibEntity() throws Exception {
        assertEquals("count=1 name=mel", runJvm(),
                "§727: orm.create/save/all on a LIB-file entity must work — the"
                        + " schema registration must not depend on the declaration order");
    }
}
