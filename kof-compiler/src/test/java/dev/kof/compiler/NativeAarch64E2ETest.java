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
 * NATIVE002 — E2E aarch64 (qemu). O backend cross emite o {@code kof_main} em
 * asm (stack machine, mesma semântica do x86_64) + runtime em asm puro
 * (raw syscalls, sem C — binário estático, {@code aarch64-linux-gnu-as} +
 * {@code aarch64-linux-gnu-ld}); o binário roda em {@code qemu-aarch64}.
 *
 * Pula (assume) quando a toolchain cruzada ou o qemu não existem, como
 * {@code NativeE2ETest} faz quando o assembler nativo falta.
 */
class NativeAarch64E2ETest {

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
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
    }

    private String runAarch64(Path tempDir, String source) throws IOException {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, source);
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(src, outDir, Target.NATIVE_AARCH64);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        Path binFile = outDir.resolve("Default/Main");
        assertTrue(Files.exists(binFile), "Binary should exist");
        ProcessBuilder pb = new ProcessBuilder("qemu-aarch64", binFile.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec;
        try {
            ec = p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while running aarch64 binary", e);
        }
        assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
        return output;
    }

    // NATIVE002-stdlib: http herdado do riscv64 via translateRiscvToAarch64.
    @Test
    void aarch64HttpGetPostStatus(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        int port = startHttpServer();
        String out = runAarch64(tempDir, """
            main() {
                println(http.get("http://127.0.0.1:%d/hello"))
                println(http.status("http://127.0.0.1:%d/hello"))
                println(http.post("http://127.0.0.1:%d/echo", "abc"))
            }
            """.formatted(port, port, port));
        assertEquals("Hello from Kof\n200\ngot:abc", out);
    }

    // NATIVE002-stdlib: spawn/await herdado do riscv64 (clone+futex).
    @Test
    void aarch64SpawnAwait(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runAarch64(tempDir, """
            Int work(Int n) { return n * 2 }
            main() {
                val r = spawn work(21)
                println(await r)
            }
            """);
        assertEquals("42", out);
    }

    @Test
    void aarch64SpawnFireAndForgetJoins(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runAarch64(tempDir, """
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

    // NATIVE002-stdlib: métodos String aarch64 (herdado via translateRiscvToAarch64).
    @Test
    void aarch64StringTrimCaseReplaceSplit(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runAarch64(tempDir, """
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

    // NATIVE002-stdlib: time.now() herdado do riscv64 (clock_gettime=113).
    @Test
    void aarch64TimeNow(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runAarch64(tempDir, """
            main() {
                var t = time.now()
                println(t > 1000000000000)
            }
            """);
        assertEquals("true", out);
    }

    // NATIVE002-stdlib: cache real (set/get/ttl) + println(null) → "null".
    @Test
    void aarch64Cache(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runAarch64(tempDir, """
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

    // NATIVE002-stdlib: "42".toInt() herdado do riscv64 (deref do valor → SIGSEGV).
    @Test
    void aarch64StringToInt(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runAarch64(tempDir, """
            main() {
                println("42".toInt())
                println("-7".toInt())
                println("0".toInt())
                try { println("abc".toInt()); println("S1") } catch (String e) { println("T1") }
                try { println("12a34".toInt()); println("S2") } catch (String e) { println("T2") }
                println(" -42 ".toInt())
                println("+7".toInt())
                println("-2147483648".toInt())
                try { println("2147483648".toInt()); println("S3") } catch (String e) { println("T3") }
                try { println("999999999999".toInt()); println("S4") } catch (String e) { println("T4") }
                println("1234567890".toLong())
                println("-9223372036854775807".toLong())
                try { println("9223372036854775808".toLong()); println("S5") } catch (String e) { println("T5") }
                var v = "-9223372036854775808".toLong()
                println(v < 0)
                println(("0".toLong()) == 0)
                println(v)
            }
            """);
        assertEquals("42\n-7\n0\nT1\nT2\n-42\n7\n-2147483648\nT3\nT4\n1234567890\n-9223372036854775807\nT5\ntrue\ntrue\n-9223372036854775808", out);
    }

    // NATIVE002-stdlib: Map/Set herdado do riscv64 (port linear-scan).
    @Test
    void aarch64MapSet(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runAarch64(tempDir, """
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

    // NATIVE002-stdlib: higher-order herdado do riscv64 (closure ABI igual mq).
    @Test
    void aarch64HigherOrder(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runAarch64(tempDir, """
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

    // NATIVE002-stdlib: json.decode<Int> escalar herdado do riscv64.
    @Test
    void aarch64JsonDecodeInt(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runAarch64(tempDir, """
            main() {
                println(json.decode<Int>("42"))
                println(json.decode<Int>("-7"))
                println(json.decode<Int>("  99  "))
                println(json.decode<Bool>("false"))
                println(json.decode<Bool>("  true"))
                println(json.decode<String>("\\"oi\\""))
                println(json.decode<Long>("-123"))
            }
            """);
        assertEquals("42\n-7\n99\nfalse\ntrue\noi\n-123", out);
    }

    // NATIVE002-stdlib: metrics() "# TYPE" herdado do riscv64 (tradutor
    // quote-aware — antes quebrava no .asciz "# TYPE ").
    @Test
    void aarch64MetricsParity(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runAarch64(tempDir, """
            main() {
                observability.counter("req")
                observability.increment("req", 3)
                observability.gauge("temp", 42)
                println(observability.metrics())
            }
            """);
        assertEquals("# TYPE req counter\nreq 4\n# TYPE temp gauge\ntemp 42", out);
    }

    // NATIVE002-stdlib: time.sleep real herdado do riscv64 (nanosleep 101).
    @Test
    void aarch64TimeSleep(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runAarch64(tempDir, """
            main() {
                var t0 = time.now()
                time.sleep(300)
                println(time.now() - t0 >= 280)
            }
            """);
        assertEquals("true", out);
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

    @Test
    void aarch64HelloWorld(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runAarch64(tempDir, "main() { println(\"Hello, Kof!\") }");
        assertEquals("Hello, Kof!", out);
    }

    @Test
    void aarch64ArithmeticAndLocal(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runAarch64(tempDir, """
            main() {
                println("Hello")
                var x = 10
                println(x + 5)
            }
            """);
        assertEquals("Hello\n15", out);
    }

    @Test
    void aarch64IfElseComparisonsAndArithmetic(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runAarch64(tempDir, """
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
    void aarch64DivisionModuloNegative(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runAarch64(tempDir, """
            main() {
                println(20 / 4)
                println(17 % 5)
                println(-7)
            }
            """);
        assertEquals("5\n2\n-7", out);
    }

    @Test
    void aarch64VirtualDispatch(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runAarch64(tempDir, """
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
    void aarch64FieldsAndMethods(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runAarch64(tempDir, """
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
    void aarch64Arrays(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runAarch64(tempDir, """
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
    void aarch64List(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runAarch64(tempDir, """
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
    void aarch64SwitchInt(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runAarch64(tempDir, """
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
    void aarch64TryCatchThrow(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runAarch64(tempDir, """
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
    void aarch64PatternMatching(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runAarch64(tempDir, """
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
    void aarch64SwitchExpression(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runAarch64(tempDir, """
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
    void aarch64JsonEncode(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runAarch64(tempDir, """
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
    void aarch64JsonEncodeDecodeLists(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runAarch64(tempDir, """
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
    void aarch64StringMethods(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runAarch64(tempDir, """
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
    void aarch64Recursion(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runAarch64(tempDir, """
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
    // NATIVE002-stdlib (residual R6, 10/09): paridade cross do CORE S1/S2/S4/S3b
    // (math/strings/encoding/uuid) — antes SO os 3 targets da ConformanceMatrix
    // (JVM/x86/JS); riscv/aarch tinham fatias (B7/B8 math, encoding, uuid.v4) sem
    // CI de execucao. 18 vetores golden JVM-medidos 10/09 — divergencia silente
    // (tipo do bug 88) fica travada nos 2 qemu.
    @Test
    void aarch64StdlibCore(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runAarch64(tempDir, """
main() {
    println(math.clamp(15, 1, 10))
    println(math.clamp(-5, 1, 10))
    println(math.sign(-7))
    println(math.sign(0))
    println(math.abs(-9))
    println(math.isEven(4))
    println(math.isOdd(4))
    println(math.min(3, 8))
    println(math.max(3, 8))
    println(strings.isAlpha("abc"))
    println(strings.isAlpha("a1"))
    println(strings.isNumeric("12"))
    println(strings.count("ababa", "ba"))
    var h = encoding.hexEncode("Hi")
    println(h)
    println(encoding.hexDecode(h))
    println(encoding.base64Encode("Hi"))
    println(encoding.base64Decode(encoding.base64Encode("Hi")))
    var u = uuid.v4()
    println(uuid.isUuid(u))
    println(uuid.isUuid("nope"))
}
            """);
        assertEquals("10\n1\n-1\n0\n9\ntrue\nfalse\n3\n8\ntrue\nfalse\ntrue\n2\n4869\nHi\nSGk=\nHi\ntrue\nfalse", out);
    }




    // NATIVE002-stdlib (residual R6, 10/09, parte 2): validacao BR/rede +
    // Luhn + IPv6/domain + escape/unescape/whitespace + net + time nos 2
    // qemu — fatias B12/B15/B16/B17/B20/B21 rodavam sem CI de execucao;
    // divergência silente cross (classe do bug 88) agora travada. Golden
    // medido no JVM — idêntico x86/riscv/aarch 10/09 (26 vetores).
    @Test
    void aarch64StdlibValidationNetTime(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runAarch64(tempDir, """
main() {
    println(validation.isCpf("529.982.247-25"))
    println(validation.isCpf("111.111.111-11"))
    println(validation.isCnpj("11.222.333/0001-81"))
    println(validation.isCep("0131010"))
    println(validation.isPis("123.4567.890-0"))
    println(validation.isIpv4("192.168.0.1"))
    println(validation.isIpv4("256.1.1.1"))
    println(validation.isMac("00:1A:2B:3C:4D:5E"))
    println(validation.isPort(443))
    println(validation.isPort(65536))
    println(validation.isCreditCard("4111111111111111"))
    println(validation.isCreditCard("4111111111111112"))
    println(validation.isIpv6("::1"))
    println(validation.isIpv6("1::2::3"))
    println(validation.isDomain("example.com"))
    println(validation.isDomain("-bad.com"))
    println(strings.escapeHtml("a<b>&\\"'c"))
    println(strings.unescapeHtml("caf&#233;"))
    println(strings.removeWhitespace("  a\\tb  ") + "|" + strings.normalizeWhitespace("  a   b  "))
    var u = "https://user:pw@host.io:8443/p?q#f"
    println(net.scheme(u) + "|" + net.host(u) + "|" + net.port(u) + "|" + net.path(u))
    println(net.queryEncode("a b&c=1"))
    println(time.isLeapYear(2000))
    println(time.isLeapYear(1900))
    println(time.daysInMonth(2024, 2))
    println(time.dayOfWeek(1970, 1, 1))
    println(time.daysBetween(2024, 1, 1, 2024, 3, 1))
}
            """);
        assertEquals("true\nfalse\ntrue\nfalse\ntrue\ntrue\nfalse\ntrue\ntrue\nfalse\ntrue\nfalse\ntrue\nfalse\ntrue\nfalse\na&lt;b&gt;&amp;&quot;&#39;c\ncafé\nab|a b\nhttps|host.io|8443|/p\na%20b%26c%3D1\ntrue\nfalse\n29\n4\n60", out);
    }

    @Test
    void nativeStringLengthAndCharAtUtf16(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        // bug 43 cross (faces 1/3): length/charAt em code units UTF-16
        // (tradução riscv→aarch da B33) — MESMO golden do JVM medido.
        String out = runAarch64(tempDir, """
                main() {
                    println("caf\\u00e9".length)
                    println("caf\\u00e9".charAt(3))
                    println("a\\u00e9\\u00e8".length)
                    println("a\\u00e9\\u00e8".charAt(1))
                    println("a\\u00e9\\u00e8".charAt(2))
                    println("a\\u20ac".length)
                    println("a\\u20ac".charAt(1))
                    println("a\\uD83D\\uDE00b".length)
                    println("a\\uD83D\\uDE00b".charAt(1))
                    println("a\\uD83D\\uDE00b".charAt(2))
                    println("a\\uD83D\\uDE00b".charAt(3))
                }
                """);
        assertEquals("4\n233\n3\n233\n232\n2\n8364\n4\n55357\n56832\n98", out);
    }

    @Test
    void nativeStringSubstringIndexOfUtf16(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        // bug 43 cross (estágio 2): substring/indexOf/lastIndexOf em code
        // units UTF-16 (tradução riscv→aarch da B34) — MESMO golden do JVM.
        String out = runAarch64(tempDir, """
                main() {
                    println("caf\\u00e9".substring(0, 3).length)
                    println("\\u00e9abc".indexOf("a"))
                    println("\\u00e9abc".indexOf("bc"))
                    println("caf\\u00e9".indexOf(""))
                    println("\\u00e9x\\u00e9".lastIndexOf("\\u00e9"))
                    println("caf\\u00e9x".indexOf("x"))
                    println("ab\\u00e9cd".substring(1, 3) + "|")
                    println("ab\\u00e9cd".substring(3) + "|")
                    println("a\\uD83D\\uDE00b".substring(1, 3) + "|")
                    println("a\\uD83D\\uDE00b".substring(3))
                    println("a\\uD83D\\uDE00b".indexOf("b"))
                    println("a\\uD83D\\uDE00b".lastIndexOf(""))
                    println("\\u20acx\\u20ac".lastIndexOf("\\u20ac"))
                    println("caf\\u00e9".substring(2, 4) + "|")
                }
                """);
        assertEquals("3\n1\n2\n0\n2\n4\nbé|\ncd|\n😀|\nb\n3\n4\n2\nfé|", out);
    }

    @Test
    void nativeStringCompareToAndHashCodeUtf16(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        // bug 97 cross: compareTo/hashCode em code units UTF-16 (tradução
        // riscv→aarch da fatia B32) — MESMOS 11 vetores golden do x86.
        String out = runAarch64(tempDir, """
                main() {
                    println("ab".compareTo("aX"))
                    println("a\\u00e9".compareTo("a"))
                    println("abc".compareTo("abd"))
                    println("ab".compareTo("abc"))
                    println("\\uD83D\\uDE00".compareTo("a"))
                    println("a\\uD83D\\uDE00".compareTo("a\\uFFFD"))
                    println("a\\uFFFD".compareTo("a\\uD83D\\uDE00"))
                    println("abc".hashCode())
                    println("a\\u00e9".hashCode())
                    println("\\uD83D\\uDE00".hashCode())
                    println("".hashCode())
                }
                """);
        assertEquals("10\n1\n-1\n-1\n55260\n-10176\n10176\n96354\n3240\n1772899\n0", out);
    }
}
