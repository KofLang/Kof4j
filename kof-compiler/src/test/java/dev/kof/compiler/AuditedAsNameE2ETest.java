package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code audited} is the entity-history modifier ({@code audited entity}), not
 * one of the documented keywords (lexical-structure.md §1.1), yet the lexer
 * reserved it everywhere: {@code Int audited(action: String)} was
 * {@code PARSE010 Expected function name}. It is now a modifier only right
 * before {@code entity}, like the contextual {@code sealed}.
 */
class AuditedAsNameE2ETest {

    @TempDir Path tmp;

    @Test
    void auditedIsAnOrdinaryNameOutsideAnEntityDeclaration() throws Exception {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, """
            Int audited(action: String) {
                return action.length()
            }
            class Log {
                Bool audited = true
            }
            main() {
                val audited = audited("four")
                println(audited)
                println(Log().audited)
            }
            """);
        Path out = tmp.resolve("out");
        CompilationResult r = new CompilerDriver().compile(src, out, Target.JVM);
        assertTrue(r.success(), "must compile: " + r.diagnostics().getDiagnostics());
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true));
        try (URLClassLoader cl = new URLClassLoader(new URL[]{out.toUri().toURL()},
                getClass().getClassLoader())) {
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(old);
        }
        assertEquals("4\ntrue\n", buf.toString());
    }
}
