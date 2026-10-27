package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class JsReservedFunctionNameE2ETest {

    private static final String SRC = """
            class C {
                Int with = 3
                public constructor() {}
                debugger(Int with): Int = with + this.with
                eval(): Int = 10
            }

            eval(Int x): Int = x + 1
            eval(String s): String = s + "!"
            typeof(Int x): Int = x * 2
            delete(Int x): Int = x - 1
            of(Int x): Int = x * 3
            arguments(Int x): Int = x * 4
            yield(Int x): Int = x * 5

            main() {
                var debugger = 1
                println(debugger + C().debugger(2))
                println(C().eval())
                println(eval(1))
                println(eval("a"))
                println(typeof(eval(4)))
                println(delete(1))
                println(of(1))
                println(arguments(1))
                println(yield(1))
                var f = (n: Int) -> eval(n)
                println(f(9))
            }
            """;

    private static final String OUT = "6\n10\n2\na!\n10\n0\n3\n4\n5\n10";

    @TempDir
    Path tmp;

    @Test
    void reservedJsNamesAsKofFunctionsRunOnJs() throws Exception {
        Path file = tmp.resolve("js").resolve("Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, SRC);
        Path out = tmp.resolve("js-out");
        CompilationResult r = new CompilerDriver().compile(file, out, Target.JS);
        assertTrue(r.success(), String.valueOf(r.diagnostics().getDiagnostics()));
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), buf,
                new java.io.ByteArrayInputStream(new byte[0]), buf);
        assertEquals(0, exit, buf.toString(StandardCharsets.UTF_8));
        assertEquals(OUT, buf.toString(StandardCharsets.UTF_8).strip());
    }
}
