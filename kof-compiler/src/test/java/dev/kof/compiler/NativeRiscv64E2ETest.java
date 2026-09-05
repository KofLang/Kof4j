package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NATIVE002 — E2E riscv64 (qemu). O backend cross emite o {@code kof_main} em
 * asm (stack machine, mesma semântica do x86_64) + runtime em asm puro
 * (raw syscalls, sem C — binário estático, {@code riscv64-linux-gnu-as} +
 * {@code riscv64-linux-gnu-ld}); o binário roda em {@code qemu-riscv64}.
 *
 * Pula (assume) quando a toolchain cruzada ou o qemu não existem, como
 * {@code NativeE2ETest} faz quando o assembler nativo falta.
 */
class NativeRiscv64E2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static boolean has(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c).redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                if (p.waitFor() != 0 || out.isEmpty()) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private void assumeToolchain() {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
    }

    private String runRiscv64(Path tempDir, String source) throws IOException {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, source);
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(src, outDir, Target.NATIVE_RISCV64);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        Path binFile = outDir.resolve("Default/Main");
        assertTrue(Files.exists(binFile), "Binary should exist");
        ProcessBuilder pb = new ProcessBuilder("qemu-riscv64", binFile.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec;
        try {
            ec = p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while running riscv64 binary", e);
        }
        assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
        return output;
    }

    @Test
    void riscv64HelloWorld(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, "main() { println(\"Hello, Kof!\") }");
        assertEquals("Hello, Kof!", out);
    }

    @Test
    void riscv64ArithmeticAndLocal(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            main() {
                println("Hello")
                var x = 10
                println(x + 5)
            }
            """);
        assertEquals("Hello\n15", out);
    }

    @Test
    void riscv64IfElseComparisonsAndArithmetic(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            main() {
                var x = 10
                if (x > 5) {
                    println("greater")
                } else {
                    println("smaller")
                }
                var a = 7
                var b = 3
                println(a - b)
                println(a * b)
                if (a == b) { println("eq") } else { println("ne") }
            }
            """);
        assertEquals("greater\n4\n21\nne", out);
    }

    @Test
    void riscv64DivisionModuloNegative(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            main() {
                println(20 / 4)
                println(17 % 5)
                println(-7)
            }
            """);
        assertEquals("5\n2\n-7", out);
    }

    @Test
    void riscv64VirtualDispatch(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            class Animal {
                speak(): String = "animal"
            }
            class Dog extends Animal {
                speak(): String = "dog"
            }
            main() {
                var a = new Dog()
                println(a.speak())
                var b = new Animal()
                println(b.speak())
            }
            """);
        assertEquals("dog\nanimal", out);
    }

    @Test
    void riscv64FieldsAndMethods(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            class User {
                String name
                greet(): String = "oi " + name
            }
            main() {
                var u = new User()
                u.name = "Mel"
                println(u.greet())
            }
            """);
        assertEquals("oi Mel", out);
    }

    @Test
    void riscv64Arrays(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            main() {
                var arr = new Int[3]
                arr[0] = 10
                arr[1] = 20
                arr[2] = 30
                println(arr[0] + arr[1] + arr[2])
                println(arr.length)
            }
            """);
        assertEquals("60\n3", out);
    }

    @Test
    void riscv64List(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            main() {
                var l = listOf(1, 2, 3)
                l.add(4)
                println(l.size)
                println(l.get(2))
            }
            """);
        assertEquals("4\n3", out);
    }

    @Test
    void riscv64SwitchInt(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            main() {
                var x = 2
                switch (x) {
                    case 1: println("um")
                    case 2: println("dois")
                    default: println("outro")
                }
            }
            """);
        assertEquals("dois", out);
    }

    @Test
    void riscv64TryCatchThrow(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            main() {
                try {
                    throw "boom"
                } catch (String e) {
                    println("caught: " + e)
                }
                println("done")
            }
            """);
        assertEquals("caught: boom\ndone", out);
    }

    @Test
    void riscv64PatternMatching(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            main() {
                var x: Object = "hello"
                switch (x) {
                    case String s:
                        println("str:" + s)
                    default:
                        println("other")
                }
                var a: Object = "world"
                if (a instanceof String) {
                    println("is string")
                }
                var b: Object = "test" as String
                println(b)
            }
            """);
        assertEquals("str:hello\nis string\ntest", out);
    }

    @Test
    void riscv64SwitchExpression(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            record Point(Int x, Int y)
            main() {
                var op = "GET"
                var r = switch (op) {
                    case "GET" -> "buscar"
                    case "POST" -> "criar"
                    default -> "x"
                }
                println(r)
                var p = Point(3, 4)
                var s = switch (p) {
                    case Point(var x, var y) -> "pt:" + x + "," + y
                    default -> "other"
                }
                println(s)
            }
            """);
        assertEquals("buscar\npt:3,4", out);
    }

    @Test
    void riscv64JsonEncode(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            record Pessoa(String nome, Int idade)
            main() {
                var p = Pessoa("Ana", 30)
                println(json.encode(p))
                var q = Pessoa("a\\"b", 1)
                println(json.encode(q))
            }
            """);
        assertEquals("{\"nome\":\"Ana\",\"idade\":30}\n{\"nome\":\"a\\\"b\",\"idade\":1}", out);
    }

    @Test
    void riscv64JsonEncodeDecodeLists(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            main() {
                println(json.encode(listOf(1, 2, 3)))
                println(json.encode(listOf("a", "b")))
                var li = json.decode<List<Int>>("[1, 2, 3]")
                println(li.size())
                println(li.get(2))
                var ls = json.decode<List<String>>("[\\"x\\", \\"y\\"]")
                println(ls.get(1))
            }
            """);
        assertEquals("[1,2,3]\n[\"a\",\"b\"]\n3\n3\ny", out);
    }

    @Test
    void riscv64StringMethods(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            main() {
                var s = "Hello, Kof"
                println(s.length)
                println(s.substring(7))
                println(s.contains("Kof"))
                println(s.startsWith("He"))
                println(s.charAt(0))
            }
            """);
        assertEquals("10\nKof\ntrue\ntrue\n72", out);
    }

    @Test
    void riscv64Recursion(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            Int fib(Int n) {
                if (n < 2) { return n }
                return fib(n - 1) + fib(n - 2)
            }
            main() {
                println(fib(10))
            }
            """);
        assertEquals("55", out);
    }

    // NATIVE002-stdlib: http.get/post/status riscv64 (asm puro, syscalls
    // asm-generic). qemu user-mode executa sockets na pilha do host, então
    // 127.0.0.1 alcança o ServerSocket abaixo.
    @Test
    void riscv64HttpGetPostStatus(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        int port = startHttpServer();
        String out = runRiscv64(tempDir, """
            main() {
                println(http.get("http://127.0.0.1:%d/hello"))
                println(http.status("http://127.0.0.1:%d/hello"))
                println(http.post("http://127.0.0.1:%d/echo", "abc"))
            }
            """.formatted(port, port, port));
        assertEquals("Hello from Kof\n200\ngot:abc", out);
    }

    // NATIVE002-stdlib: spawn/await riscv64 (clone+futex, asm puro). qemu
    // user-mode roda threads de verdade; await sincroniza via futex no done.
    @Test
    void riscv64SpawnAwait(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            Int work(Int n) { return n * 2 }
            main() {
                val r = spawn work(21)
                println(await r)
            }
            """);
        assertEquals("42", out);
    }

    @Test
    void riscv64SpawnFireAndForgetJoins(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            main() {
                println("inicio")
                spawn { println("bg") }
                println("fim")
            }
            """);
        var lines = List.of(out.split("\n"));
        assertTrue(lines.contains("inicio"), "inicio primeiro: " + lines);
        assertTrue(lines.contains("bg"), "join implícito espera o worker: " + lines);
        assertTrue(lines.contains("fim"), "main não bloqueia no spawn: " + lines);
    }

    // NATIVE002-stdlib: métodos String riscv64 (trim/toUpper/toLowerCase/
    // replace char+String/lastIndexOf/equalsIgnoreCase/split) — antes
    // quebriam no link com undefined reference (R6: nunca silencioso).
    @Test
    void riscv64StringTrimCaseReplaceSplit(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            main() {
                println("  hi  ".trim().length)
                println("abc".toUpperCase())
                println("ABC".toLowerCase())
                println("a_b_c".replace('_', '-'))
                println("hello".replace("l", "L"))
                println("banana".lastIndexOf("na"))
                println("Hello".equalsIgnoreCase("hELLO"))
                var parts = "a,b,c".split(",")
                println(parts.length)
                println(parts[1])
            }
            """);
        assertEquals("2\nABC\nabc\na-b-c\nheLLo\n4\ntrue\n3\nb", out);
    }

    // NATIVE002-stdlib: time.now() real (clock_gettime=113) — antes era stub
    // `li a0,0` que quebrava o TTL do cache e time.now() silenciosamente (R6).
    @Test
    void riscv64TimeNow(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            main() {
                var t = time.now()
                println(t > 1000000000000)
            }
            """);
        assertEquals("true", out);
    }

    // NATIVE002-stdlib: cache real (set/get/ttl) + println(null) → "null".
    // O scan loop usava t2/t3 (caller-saved) através de kof_string_equals →
    // bounds quebrados → segfault. Agora usa ponteiro-fim salvo no frame.
    @Test
    void riscv64Cache(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            main() {
                cache.set("name", "Mel")
                println(cache.get("name"))
                println(cache.get("missing"))
                cache.set("t", "x", 1)
                println(cache.ttl("t") >= 0 && cache.ttl("t") <= 1)
            }
            """);
        assertEquals("Mel\nnull\ntrue", out);
    }

    // NATIVE002-stdlib: "42".toInt() — o loop derefava o VALOR do char
    // (lbu t0,24(s0) → 0x34) como endereço → SIGSEGV silencioso.
    @Test
    void riscv64StringToInt(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            main() {
                println("42".toInt())
                println("-7".toInt())
                println("0".toInt())
            }
            """);
        assertEquals("42\n-7\n0", out);
    }

    // NATIVE002-stdlib: Map/Set no cross (port linear-scan do x86_64) —
    // antes mapOf/setOf quebravam no link (undefined reference).
    @Test
    void riscv64MapSet(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            main() {
                var m = mapOf()
                m.put("a", 1)
                m.put("b", 2)
                println(m.get("a"))
                println(m.size())
                println(m.contains("b"))
                println(m.remove("a"))
                println(m.size())
                println(m.keys().size())
                var s = setOf("x", "x", "y")
                println(s.size())
                println(s.contains("y"))
                println(s.contains("z"))
                println(s.remove("x"))
                println(s.size())
            }
            """);
        assertEquals("1\n2\ntrue\n1\n1\n1\n2\ntrue\nfalse\ntrue\n1", out);
    }

    // NATIVE002-stdlib: higher-order (map/filter/reduce) no cross — closure
    // ABI igual mq (a0=fn, a1..=args, invoke via vtable[0]).
    @Test
    void riscv64HigherOrder(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            main() {
                var l = listOf(1, 2, 3)
                var d = l.map((x: Int) -> x * 2)
                println(d.get(2))
                var f = l.filter((x: Int) -> x > 1)
                println(f.size)
                println(l.reduce((a: Int, b: Int) -> a + b, 0))
            }
            """);
        assertEquals("6\n2\n6", out);
    }

    // NATIVE002-stdlib: json.decode<Int> escalar no cross (antes: undefined
    // reference a kof_json_decode_int — só o _int_list existia).
    @Test
    void riscv64JsonDecodeInt(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            main() {
                println(json.decode<Int>("42"))
                println(json.decode<Int>("-7"))
                println(json.decode<Int>("  99  "))
            }
            """);
        assertEquals("42\n-7\n99", out);
    }

    private static int startHttpServer() throws IOException {
        java.net.ServerSocket ss = new java.net.ServerSocket(0);
        int port = ss.getLocalPort();
        Thread t = new Thread(() -> {
            while (true) {
                try (java.net.Socket s = ss.accept()) {
                    java.io.BufferedReader in = new java.io.BufferedReader(
                            new java.io.InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                    String line = in.readLine();
                    if (line == null) continue;
                    String method = line.split(" ")[0];
                    int cl = 0;
                    String h;
                    while ((h = in.readLine()) != null && !h.isEmpty()) {
                        if (h.toLowerCase().startsWith("content-length:")) {
                            cl = Integer.parseInt(h.substring(15).trim());
                        }
                    }
                    String body = "Hello from Kof";
                    if (method.equals("POST")) {
                        char[] buf = new char[cl];
                        int off = 0;
                        while (off < cl) {
                            int r = in.read(buf, off, cl - off);
                            if (r < 0) break;
                            off += r;
                        }
                        body = "got:" + new String(buf, 0, off);
                    }
                    String resp = "HTTP/1.1 200 OK\r\nContent-Length: " + body.length()
                            + "\r\nConnection: close\r\n\r\n" + body;
                    s.getOutputStream().write(resp.getBytes(StandardCharsets.UTF_8));
                    s.getOutputStream().flush();
                } catch (IOException e) {
                    return;
                }
            }
        });
        t.setDaemon(true);
        t.start();
        return port;
    }
}
