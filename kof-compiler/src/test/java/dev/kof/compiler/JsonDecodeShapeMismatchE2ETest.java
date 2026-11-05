package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §819 — {@code json.decode<Map<String, Object>>} of a JSON array returned an
 * EMPTY map, and a JSON object decoded as {@code List<T>} an empty list, with
 * no diagnostic: the caller saw "no data" where the input had the wrong
 * shape. A JSON value of the wrong shape for the requested container is now
 * the runtime error JSN006, catchable as a Kof String, with the same text on
 * JVM, Script and JS.
 */
class JsonDecodeShapeMismatchE2ETest {

    private static final String SRC = """
            record Item(String name)
            record Box(List<Item> items)

            String attempt(String what, () -> String f) {
                try {
                    return what + " ok " + f()
                } catch (String e) {
                    return what + " " + e
                }
            }

            main() {
                println(attempt("map<-array", () -> "" + json.decode<Map<String, Object>>("[1, 2]").size()))
                println(attempt("map<-number", () -> "" + json.decode<Map<String, Object>>("7").size()))
                println(attempt("map<Item><-array", () -> "" + json.decode<Map<String, Item>>("[]").size()))
                println(attempt("list<-object", () -> "" + json.decode<List<Object>>("{\\"a\\": 1}").size()))
                println(attempt("list<Item><-object", () -> "" + json.decode<List<Item>>("{}").size()))
                println(attempt("list<String><-null", () -> "" + json.decode<List<String>>("null").size()))
                println(attempt("field<-object", () -> "" + json.decode<Box>("{\\"items\\": {}}").items.size()))
                println(attempt("map<-object", () -> "" + json.decode<Map<String, Object>>("{\\"a\\": 1}").size()))
                println(attempt("list<-array", () -> "" + json.decode<List<Item>>("[{\\"name\\": \\"x\\"}]").size()))
                println(attempt("map<-empty", () -> "" + json.decode<Map<String, Object>>("{}").size()))
            }
            """;

    private static final String GOLDEN = """
            map<-array JSN006: json.decode expected an object, found an array
            map<-number JSN006: json.decode expected an object, found a number
            map<Item><-array JSN006: json.decode expected an object, found an array
            list<-object JSN006: json.decode expected an array, found an object
            list<Item><-object JSN006: json.decode expected an array, found an object
            list<String><-null JSN006: json.decode expected an array, found null
            field<-object JSN006: json.decode expected an array, found an object
            map<-object ok 1
            list<-array ok 1
            map<-empty ok 0
            """;

    @TempDir Path tmp;

    @Test
    void wrongShapeIsJsn006OnJvmScriptAndJs() throws Exception {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, SRC);
        CompilerDriver driver = new CompilerDriver();

        CompilationResult jvm = driver.compile(src, tmp.resolve("jvm"), Target.JVM);
        assertTrue(jvm.success(), "JVM compile: " + jvm.diagnostics().getDiagnostics());
        assertEquals(GOLDEN, run(System.getProperty("java.home") + "/bin/java", "-cp",
                tmp.resolve("jvm").toString(), "Default.Main"), "JVM");

        KofInterpreter.Result script = driver.interpret(List.of(src), tmp, new String[0]);
        assertEquals(GOLDEN, script.stdout(), "Script, stderr: " + script.stderr());

        CompilationResult js = driver.compile(src, tmp.resolve("js"), Target.JS);
        assertTrue(js.success(), "JS compile: " + js.diagnostics().getDiagnostics());
        assertEquals(GOLDEN, run("node", tmp.resolve("js/Default.mjs").toString()), "JS");
    }

    private static String run(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        p.waitFor();
        return out;
    }
}
