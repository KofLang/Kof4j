package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;


class JsonE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path source, Path outDir, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running JVM class", e);
        }
    }

    private String runNative(Path source, Path outDir, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        Path binFile = outDir.resolve("Default/Main");
        assertTrue(Files.exists(binFile), "Binary should exist");
        try {
            ProcessBuilder pb = new ProcessBuilder(binFile.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running native binary", e);
        }
    }



    @Test
    void jvmEncodePrimitives(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                println(json.encode(42))
                println(json.encode(true))
                println(json.encode("hi \\"there\\""))
                println(json.encode(9000000000))
            }
            """);
        runJvm(source, tempDir.resolve("out"), "42\ntrue\n\"hi \\\"there\\\"\"\n9000000000");
    }

    @Test
    void jvmEncodeDecodeLists(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                println(json.encode(listOf(1, 2, 3)))
                println(json.encode(listOf("a", "b")))
                var dl = json.decode<List<Int>>("[1, 2, 3]")
                println(dl.size())
                println(dl.get(0) + dl.get(2))
                var dl2 = json.decode<List<String>>("[\\"x\\", \\"y\\"]")
                println(dl2.get(1))
            }
            """);
        runJvm(source, tempDir.resolve("out"), "[1,2,3]\n[\"a\",\"b\"]\n3\n4\ny");
    }

    @Test
    void jvmEncodeArray(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var arr = new Int[3]
                arr[0] = 7
                arr[1] = 8
                arr[2] = 9
                println(json.encode(arr))
            }
            """);
        runJvm(source, tempDir.resolve("out"), "[7,8,9]");
    }

    @Test
    void jvmDecodeScalars(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                println(json.decode<Int>("77"))
                println(json.decode<Bool>("true"))
                println(json.decode<String>("\\"hello\\""))
                println(json.decode<Long>("9000000000"))
            }
            """);
        runJvm(source, tempDir.resolve("out"), "77\ntrue\nhello\n9000000000");
    }

    @Test
    void jvmDecodeBoolFalseAndWhitespace(@TempDir Path tempDir) throws IOException {
        // REGRESSÃO (bug 30): decode<Bool>("false") dava true e "  true"
        // dava false no Native — o caller passava o length em %r8d (a helper
        // lê %rdx) e comparava do offset 0 (ignorava o pos após ws).
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                println(json.decode<Bool>("false"))
                println(json.decode<Bool>("true"))
                println(json.decode<Bool>("  true"))
                println(json.decode<Bool>("  false"))
            }
            """);
        runJvm(source, tempDir.resolve("out"), "false\ntrue\ntrue\nfalse");
        runNative(source, tempDir.resolve("out-native"), "false\ntrue\ntrue\nfalse");
    }

    @Test
    void jvmEncodeDecodeObject(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class User {
                String name
                Int age
            }
            main() {
                var u = new User()
                u.name = "Mel"
                u.age = 30
                println(json.encode(u))
                var du = json.decode<User>("{\\"name\\": \\"Ana\\", \\"age\\": 25}")
                println(du.name)
                println(du.age)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "{\"name\":\"Mel\",\"age\":30}\nAna\n25");
    }

    @Test
    void jvmEncodeDecodeRecord(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            record Point(Int x, Int y)
            main() {
                var p = new Point(3, 4)
                println(json.encode(p))
                var dp = json.decode<Point>("{\\"x\\": 10, \\"y\\": 20}")
                println(dp.x)
                println(dp.y)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "{\"x\":3,\"y\":4}\n10\n20");
    }



    @Test
    void nativeEncodePrimitives(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                println(json.encode(42))
                println(json.encode(true))
                println(json.encode("hi"))
            }
            """);
        runNative(source, tempDir.resolve("out"), "42\ntrue\n\"hi\"");
    }

    @Test
    void nativeEncodeDecodeLists(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                println(json.encode(listOf(1, 2, 3)))
                println(json.encode(listOf("a", "b")))
                var dl = json.decode<List<Int>>("[1, 2, 3]")
                println(dl.size())
                println(dl.get(0) + dl.get(2))
                var dl2 = json.decode<List<String>>("[\\"x\\", \\"y\\"]")
                println(dl2.get(1))
            }
            """);
        runNative(source, tempDir.resolve("out"), "[1,2,3]\n[\"a\",\"b\"]\n3\n4\ny");
    }

    @Test
    void nativeEncodeArray(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var arr = new Int[3]
                arr[0] = 7
                arr[1] = 8
                arr[2] = 9
                println(json.encode(arr))
            }
            """);
        runNative(source, tempDir.resolve("out"), "[7,8,9]");
    }

@Test
    void nativeDecodeScalars(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                println(json.decode<Int>("77"))
                println(json.decode<Bool>("true"))
                println(json.decode<String>("\\"hello\\""))
            }
            """);
        runNative(source, tempDir.resolve("out"), "77\ntrue\nhello");
    }

    @Test
    void nativeLongAndListDecode(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                println(json.encode(9000000000))
                println(json.decode<Long>("9000000000"))
                var dl = json.decode<List<Long>>("[1, 2, 3]")
                println(dl.get(0) + dl.get(2))
            }
            """);
        runNative(source, tempDir.resolve("out"), "9000000000\n9000000000\n4");
    }



    @Test
    void floatSupportedOnJvmAndNative(@TempDir Path tempDir) throws IOException {
        // JSN001 fechado: encode de Float funciona nos 3 targets
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                println(json.encode(1.5))
            }
            """);
        CompilationResult jvm = driver.compile(source, tempDir.resolve("jvm-out"), Target.JVM);
        assertTrue(jvm.success(), "Float encode should work on JVM");
        CompilationResult nativeResult = driver.compile(source, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), "Float encode should work on Native (JSN001): "
                + nativeResult.diagnostics().getDiagnostics());
    }

    @Test
    void nativeObjectEncodeJs002(@TempDir Path tempDir) throws IOException {
        // JSN002 implementado: encode de objeto no Native compõe JSON em compile-time
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class User {
                String name
            }
            main() {
                var u = new User()
                println(json.encode(u))
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Native object encode (JSN002) should compile: "
                + result.diagnostics().getDiagnostics());
    }

    @Test
    void decodeArrayWorksOnJvmAndNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = json.decode<Int[]>("[1, 2]")
                println(a.length)
            }
            """);
        CompilationResult jvm = driver.compile(source, tempDir.resolve("jvm-out"), Target.JVM);
        assertTrue(jvm.success(), "Array decode should work on JVM");
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", tempDir.resolve("jvm-out").toString(),
                    "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String jvmOut = new String(p.getInputStream().readAllBytes()).trim();
            assertEquals(0, p.waitFor(), jvmOut);
            assertEquals("2", jvmOut);
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
        // JSN003 fechado: o Native decodifica Int[] de verdade
        CompilationResult nativeResult = driver.compile(source, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), nativeResult.diagnostics().getDiagnostics().toString());
        Path bin = tempDir.resolve("native-out/Default/Main");
        assertTrue(Files.exists(bin));
        try {
            ProcessBuilder pb = new ProcessBuilder(bin.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String nativeOut = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).trim();
            assertEquals(0, p.waitFor(), nativeOut);
            assertEquals("2", nativeOut, "native output");
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }
}