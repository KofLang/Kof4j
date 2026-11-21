package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** #1183: {@code kof.encoding} bound EVERY one-argument call by ARITY alone
 *  ({@code KofEncoding.staticMethod} checked {@code argc == 1} and never the
 *  argument type), so {@code encoding.base64Encode(123)} passed {@code check}
 *  and emitted {@code invokestatic …(Ljava/lang/String;)} with an {@code int}
 *  on the stack — one source, three behaviours, no diagnostic:
 *  JVM {@code VerifyError: Bad type on operand stack}, JS silently {@code ""},
 *  Native SIGSEGV.
 *
 *  <p>The guards mirror {@code kof.math}'s §658 fix and {@code kof.strings}''
 *  §657: a non-String argument is refused at the binding layer with SEM025
 *  (R6 — no silent widening), and the String faces keep binding. */
class KofEncodingTypeGuardTest {

    @TempDir
    Path tmp;

    private CompilationResult compile(String tag, String expr) throws Exception {
        Path file = tmp.resolve(tag + "/Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "main() {\n    println(" + expr + ")\n}\n");
        return new CompilerDriver().compile(file, tmp.resolve("out-" + tag), Target.JVM);
    }

    private String diagOf(String tag, String expr) throws Exception {
        return String.join("\n", compile(tag, expr).diagnostics().getDiagnostics().stream()
                .map(Object::toString).toList());
    }

    @Test
    void nonStringArgumentsAreRefusedWithSem025() throws Exception {
        // the issue's exact repro
        assertTrue(diagOf("b64int", "encoding.base64Encode(123)").contains("SEM025"),
                "encoding.base64Encode(Int) must be refused at the binding layer");
        assertTrue(diagOf("hexint", "encoding.hexEncode(123)").contains("SEM025"),
                "encoding.hexEncode(Int) must be refused");
        assertTrue(diagOf("hexdecint", "encoding.hexDecode(123)").contains("SEM025"),
                "encoding.hexDecode(Int) must be refused");
        assertTrue(diagOf("urlint", "encoding.urlEncode(123)").contains("SEM025"),
                "encoding.urlEncode(Int) must be refused");
        assertTrue(diagOf("b64urldbl", "encoding.base64UrlDecode(2.5)").contains("SEM025"),
                "encoding.base64UrlDecode(Double) must be refused");
        assertTrue(diagOf("b64bool", "encoding.base64Decode(true)").contains("SEM025"),
                "encoding.base64Decode(Bool) must be refused");
    }

    @Test
    void stringFacesStillBindCleanly() throws Exception {
        assertTrue(compile("ok-hex", "encoding.hexEncode(\"Hi\")").success(),
                "encoding.hexEncode(String) must keep binding");
        assertTrue(compile("ok-b64", "encoding.base64Encode(\"Hi\")").success(),
                "encoding.base64Encode(String) must keep binding");
        assertTrue(compile("ok-hexdec", "encoding.hexDecode(\"4869\")").success(),
                "encoding.hexDecode(String) must keep binding");
        assertTrue(compile("ok-url", "encoding.urlEncode(\"a b\")").success(),
                "encoding.urlEncode(String) must keep binding");
    }

    /** The nullable face is plain (the neighbours' unwrap runs in the typer),
     *  so a narrowed {@code String?} keeps binding — the guard must not refuse
     *  it. */
    @Test
    void narrowedNullableStringStillBinds() throws Exception {
        Path file = tmp.resolve("narrow/Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
            main() {
                var s: String? = "Hi"
                if (s != null) {
                    println(encoding.hexEncode(s))
                }
            }
            """);
        CompilationResult r = new CompilerDriver().compile(file, tmp.resolve("out-narrow"), Target.JVM);
        assertTrue(r.success(), "got: " + r.diagnostics().getDiagnostics());
    }
}
