package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §833 — #1103 made a wrong-shaped top-level {@code json.decode} a JSN006; the
 * same mismatch one level down, in a record field, still decoded silently or
 * failed with a host error: a {@code List} field given {@code {}} was an empty
 * list on the JVM and {@code undefined} on JS, a nested record given an array
 * was {@code undefined} on JS and crashed the interpreter. Every field shape
 * mismatch is now the same JSN006 on JVM, Script and JS.
 */
class JsonFieldShapeMismatchE2ETest {

    private static final String SRC = """
            record Item(String name)
            record Box(List<Item> items)
            record Tags(List<String> tags)
            record Bag(Map<String, Int> counts)
            record Holder(Item item)
            String attempt(String what, () -> String f) {
                try {
                    return what + " ok " + f()
                } catch (String e) {
                    return what + " " + e
                }
            }
            main() {
                println(attempt("listRecField<-object", () -> "" + json.decode<Box>("{\\"items\\": {}}").items.size()))
                println(attempt("listStrField<-object", () -> "" + json.decode<Tags>("{\\"tags\\": {\\"a\\": 1}}").tags.size()))
                println(attempt("listStrField<-string", () -> "" + json.decode<Tags>("{\\"tags\\": \\"x\\"}").tags.size()))
                println(attempt("mapField<-array", () -> "" + json.decode<Bag>("{\\"counts\\": [1]}").counts.size()))
                println(attempt("recField<-array", () -> "" + json.decode<Holder>("{\\"item\\": [1]}").item.name))
                println(attempt("record<-array", () -> "" + json.decode<Item>("[1]").name))
                println(attempt("listField<-null", () -> "" + (json.decode<Tags>("{\\"tags\\": null}").tags == null)))
                println(attempt("listField ok", () -> "" + json.decode<Box>("{\\"items\\": [{\\"name\\": \\"a\\"}]}").items.size()))
            }
            """;

    private static final String GOLDEN = """
            listRecField<-object JSN006: json.decode expected an array but the payload is an object
            listStrField<-object JSN006: json.decode expected an array but the payload is an object
            listStrField<-string JSN006: json.decode expected an array but the payload is a string
            mapField<-array JSN006: json.decode expected an object but the payload is an array
            recField<-array JSN006: json.decode expected an object but the payload is an array
            record<-array JSN006: json.decode expected an object but the payload is an array
            listField<-null ok true
            listField ok ok 1
            """.trim();

    @Test
    void aWrongShapedFieldIsJsn006OnJvmScriptAndJs(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, SRC);

        Path jvmOut = tmp.resolve("jvm");
        CompilationResult jvm = new CompilerDriver().compile(src, jvmOut, Target.JVM);
        assertTrue(jvm.success(), () -> "JVM compile: " + jvm.diagnostics().getDiagnostics());
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try (URLClassLoader cl = new URLClassLoader(new URL[]{jvmOut.toUri().toURL()}, getClass().getClassLoader())) {
            Class.forName("Default.Main", true, cl).getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(old);
        }
        assertEquals(GOLDEN, buf.toString(StandardCharsets.UTF_8).trim(), "JVM");

        KofInterpreter.Result script = new CompilerDriver().interpret(List.of(src), tmp, new String[0]);
        assertEquals(GOLDEN, script.stdout().trim(), () -> "Script, stderr: " + script.stderr());

        Path jsOut = tmp.resolve("js");
        CompilationResult js = new CompilerDriver().compile(src, jsOut, Target.JS);
        assertTrue(js.success(), () -> "JS compile: " + js.diagnostics().getDiagnostics());
        ByteArrayOutputStream jsBuf = new ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(jsOut.resolve("Default.mjs"), jsBuf,
                new java.io.ByteArrayInputStream(new byte[0]), jsBuf);
        assertEquals(0, exit, () -> "JS: " + jsBuf);
        assertEquals(GOLDEN, jsBuf.toString(StandardCharsets.UTF_8).trim(), "JS");
    }
}
