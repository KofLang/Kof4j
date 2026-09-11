package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Conformance Matrix (Fase 9 do plano de plataforma —
 * docs/development/conformance-matrix.md): cada caso trava a MESMA saída
 * esperada nos 4 targets — JVM (bytecode), Native (x86_64), Script
 * (interpretador de IR) e KofJS (GraalJS).
 *
 * Regra: a saída esperada é a do COMPORTAMENTO DOCUMENTADO (corpus +
 * backend-parity.md), não "o que o JVM imprime". Célula PARTIAL da matriz
 * = target excluído da asserção + bug registrado em known-bugs.md (ref no
 * comentário). Casos determinísticos apenas — concorrência/tempo têm
 * suítes próprias (SpawnE2ETest, KofTimeE2ETest).
 */
class ConformanceMatrixTest {

    private record TargetResult(int exit, String out) {}

    private static String norm(String s) {
        return s == null ? "" : s.replace("\r\n", "\n").trim();
    }

    // Driver fresco por compilação: isolamento defensivo (cada caso é um
    // processo independente no CLI real). O vazamento de estado entre
    // compilações (bug 51) foi corrigido em CompilerDriverState
    // .resetForCompilation, mas manter um driver por caso continua sendo a
    // prática correta para testes de paridade.
    private CompilerDriver freshDriver() {
        return new CompilerDriver();
    }

    private TargetResult runJvm(Path source, Path outDir) throws IOException {
        CompilationResult r = freshDriver().compile(source, outDir, Target.JVM);
        assertTrue(r.success(), "JVM compile: " + r.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int ec = p.waitFor();
            return new TargetResult(ec, norm(out));
        } catch (InterruptedException e) {
            throw new IOException("interrupted", e);
        }
    }

    private TargetResult runScript(Path source, Path dir) {
        try {
            KofInterpreter.Result r = freshDriver().interpret(List.of(source), dir, new String[0]);
            return new TargetResult(r.exitCode(), norm(r.stdout()));
        } catch (KofInterpretException e) {
            return new TargetResult(-1, "FRONTEND-ERR");
        }
    }

    private TargetResult runNative(Path source, Path outDir) throws IOException {
        CompilationResult r = freshDriver().compile(source, outDir, Target.NATIVE);
        assertTrue(r.success(), "Native compile: " + r.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binário deve existir");
        try {
            ProcessBuilder pb = new ProcessBuilder(bin.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int ec = p.waitFor();
            return new TargetResult(ec, norm(out));
        } catch (InterruptedException e) {
            throw new IOException("interrupted", e);
        }
    }

    private TargetResult runJs(Path source, Path outDir) throws IOException {
        CompilationResult r = freshDriver().compile(source, outDir, Target.JS);
        assertTrue(r.success(), "JS compile: " + r.diagnostics().getDiagnostics());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                (InputStream) new ByteArrayInputStream(new byte[0]), out);
        return new TargetResult(ec, norm(out.toString()));
    }

    /**
     * Trava o MESMO output nos 4 targets. partial = targets com bug
     * registrado (célula PARTIAL da matriz) — excluídos da asserção,
     * cada um com o ref do bug.
     */
    private void matrix(String name, String program, String expected,
                        Set<String> partial, Path tempDir) throws IOException {
        Path dir = tempDir.resolve(name);
        Files.createDirectories(dir);
        Path source = dir.resolve("Main.kf");
        Files.writeString(source, program);
        var problemas = new StringBuilder();
        if (!partial.contains("jvm")) {
            TargetResult t = runJvm(source, dir.resolve("jvm"));
            if (t.exit() != 0 || !expected.equals(t.out()))
                problemas.append(" JVM(exit=").append(t.exit()).append(")=<").append(t.out()).append(">");
        }
        if (!partial.contains("native")) {
            TargetResult t = runNative(source, dir.resolve("nat"));
            if (t.exit() != 0 || !expected.equals(t.out()))
                problemas.append(" NATIVE(exit=").append(t.exit()).append(")=<").append(t.out()).append(">");
        }
        if (!partial.contains("script")) {
            TargetResult t = runScript(source, dir);
            if (t.exit() != 0 || !expected.equals(t.out()))
                problemas.append(" SCRIPT(exit=").append(t.exit()).append(")=<").append(t.out()).append(">");
        }
        if (!partial.contains("js")) {
            TargetResult t = runJs(source, dir.resolve("js"));
            if (t.exit() != 0 || !expected.equals(t.out()))
                problemas.append(" JS(exit=").append(t.exit()).append(")=<").append(t.out()).append(">");
        }
        assertEquals("", problemas.toString().trim(),
                "[" + name + "] esperado <" + expected + "> — divergências:");
    }

    // ===== Lote 1 — linguagem core (docs/development/conformance-matrix.md) =====

    @Test
    void conformanceCoreArithmetic(@TempDir Path tempDir) throws IOException {
        matrix("arith", """
                main() {
                    var a = 2147483647
                    println(a + 1)
                    println(-7 % 3)
                    println(7 % -3)
                }
                """, "-2147483648\n-1\n1", Set.of(), tempDir);
        matrix("longdiv", """
                main() {
                    var a = 10000000000L
                    println(a / 3L)
                    println(a % 7L)
                }
                """, "3333333333\n4", Set.of(), tempDir);
        matrix("cast", """
                main() {
                    var d = 9.9
                    println(d as Int)
                    var l = 70000L
                    println(l as Int)
                    println(66 as Char)
                }
                """, "9\n70000\n66", Set.of(), tempDir);
        // bug 44 CORRIGIDO 10/09 (x86_64): kof_print_double/float via snprintf
        // %.16g + append '.0' p/ inteiro-válido + write via syscall (sem
        // printf/reordenação) — Native desbloqueado. KofJS mantém a exclusão:
        // doc "parece bug mas é esperado" (JS String(5.0) = "5").
        matrix("floatprint", """
                main() {
                    println(1.0 / 3.0)
                    println(2.5 * 2.0)
                    println(7.0 / 2.0)
                }
                """, "0.3333333333333333\n5.0\n3.5", Set.of("js"), tempDir);
        // bug 44 (residual, x86_64, paridade regra 5): o glibc %.16g escreve
        // 'inf'/'-inf'/'nan' mas o contrato é JDK Double.toString →
        // 'Infinity'/'-Infinity'/'NaN' (o que JVM/Script imprimem). O println
        // boxa via kof_double_to_string (RuntimeStringConv); o print sem box via
        // kof_print_double (RuntimePrintNum). As 2 faces + float + concat
        // String.valueOf. JS mantém a exclusão (idêntica ao floatprint:
        // String(5.0) = "5" no JS, "5.0" no JVM — a divergência é o '.0', não
        // o spelling de inf/nan, que o JS já casa).
        matrix("infinityprint", """
                main() {
                    println(1.0 / 0.0)
                    println(-1.0 / 0.0)
                    println(0.0 / 0.0)
                    println(1e38f * 1e38f)
                    print(1.0 / 0.0)
                    print(" ")
                    print(0.0 / 0.0)
                    println("")
                    println("v=" + (0.0 / 0.0))
                }
                """, "Infinity\n-Infinity\nNaN\nInfinity\nInfinity NaN\nv=NaN",
                Set.of("js"), tempDir);
        // bug 100 (paridade absoluta): `String.equals(não-String)` é `false` em
        // todo target — o JVM sempre deu false (Objects.equals), mas o Native
        // CRASHAVA (SIGSEGV/vazio) ao ler o Int-boxado como ponteiro-String.
        // Agora é constant-fold no lowering (mesmo `false` nos 5). O == de
        // String-vs-String (conteúdo) segue pelo runtime em todos.
        // §102 (paridade absoluta): o índice inicial de indexOf/lastIndexOf/
        // startsWith era IGNORADO no Native (helper de aridade 1 só). JDK 21
        // é o oracle (clampagens: from<0, from>total, vazia, corte de par).
        matrix("searchfrom", """
                main() {
                    println("aXb".indexOf("X",2))
                    println("abc".indexOf("",5))
                    println("aXa".lastIndexOf("a",-1))
                    println("aXa".lastIndexOf("a",9))
                    println("aXb".startsWith("X",1))
                    println("abc".startsWith("",4))
                }
                """, "-1\n3\n-1\n2\ntrue\nfalse", Set.of(), tempDir);
        matrix("equalsfold", """
                main() {
                    val s = "abc"
                    println(s.equals("abc"))
                    println(s.equals("abd"))
                    println(s.equals(5))
                    println(s.equals('x'))
                }
                """, "true\nfalse\nfalse\nfalse", Set.of(), tempDir);
        // §104 (paridade absoluta): record DENTRO de coleção usa equals/
        // hashCode/toString por CONTEÚDO (oracle = JVM, registro real gera os
        // 3). Script era identidade (KofObj sem override → §104a CORRIGIDO
        // 11/09); Native LINK_FAIL em Thing.equals (Object.equals herdado sem
        // slot na vtable → §104b ABERTO, célula excluída); JS usa identidade
        // (Map/HashSet nativos + sem wrapper → §104c ABERTO, excluído).
        matrix("objmethods", """
                record Point(Int x, Int y)
                main() {
                    val p1 = Point(1, 2)
                    val p2 = Point(1, 2)
                    println(listOf(p1).contains(p2))
                    println(setOf(p1).contains(p2))
                    println(mapOf(p1, 7).get(p2))
                    println(listOf(p1))
                }
                """, "true\ntrue\n7\n[Point[x=1, y=2]]", Set.of("native", "js"), tempDir);
        matrix("boollogic", """
                main() {
                    println(true && false)
                    println(true || false)
                    println(!true)
                    println((1 < 2) == (3 > 2))
                }
                """, "false\ntrue\nfalse\ntrue", Set.of(), tempDir);
        matrix("bitwise", """
                main() {
                    println(6 & 3)
                    println(6 | 3)
                    println(6 ^ 3)
                    println(1 << 4)
                    println(256 >> 2)
                }
                """, "2\n7\n5\n16\n64", Set.of(), tempDir);
        // STDLIB S1 — kof.math (Int-only) paridade total nos 4 targets.
        // §93: os dois últimos casos comparam `== true`/`== false` no
        // CAMINHO DE VALOR (o print sozinho coercia 1/0 e mascarava o bug).
        matrix("stdmath", """
                main() {
                    println(math.clamp(15, 0, 10))
                    println(math.clamp(-3, 0, 10))
                    println(math.abs(-7))
                    println(math.sign(-4))
                    println(math.min(3, 8))
                    println(math.max(3, 8))
                    println(math.isEven(4))
                    println(math.isOdd(4))
                    println(math.isZero(0))
                    println(math.isEven(4) == true)
                    println(math.isEven(4) == false)
                }
                """, "10\n0\n7\n-1\n3\n8\ntrue\nfalse\ntrue\ntrue\nfalse", Set.of(), tempDir);
        // STDLIB S1b — kof.math.sqrt (PRIMEIRO Double da namespace). Compara-
        // ções Bool (nunca print de double cru — bug 44 no Native). riscv/aarch
        // = B32 `fsqrt.d` (MATH001 fechado 11/09 — a cobertura cross com
        // golden byte-idêntico mora em KofMathTest.sqrtCrossArch/
        // doubleOpsCrossArch sob qemu; esta matriz roda os 4 targets não-cross).
        // PARTIAL script = bug 94 (numEq usa Double.compare → NaN==NaN true,
        // divergindo dos 3 compilados que seguem IEEE NaN!=NaN).
        matrix("stdsqrt", """
                main() {
                    println(math.sqrt(9.0) == 3.0)
                    println(math.sqrt(2.0) == 1.4142135623730951)
                    println(math.sqrt(0.25) == 0.5)
                    println(math.sqrt(0.0) == 0.0)
                    println(math.sqrt(-1.0) == -1.0)
                    println(math.sqrt(-1.0) != math.sqrt(-1.0))
                }
                 """, "true\ntrue\ntrue\ntrue\nfalse\ntrue", Set.of("script"), tempDir);
        // STDLIB S1b.1 — kof.math escalares Double (lerp/percentage/
        // isInteger/isDecimal). Subset determinístico travado nos 4 targets
        // (NaN excluído — bug 94 no interpretador; provado só nos compilados
        // em KofMathTest.doubleOps*). Bool == false no script casa (S12b).
        matrix("stdmathdouble", """
                main() {
                    println(math.lerp(0.0, 10.0, 0.5) == 5.0)
                    println(math.lerp(0.0, 10.0, 0.25) == 2.5)
                    println(math.lerp(-4.0, 4.0, 0.75) == 2.0)
                    println(math.lerp(2.0, 8.0, 1.5) == 11.0)
                    println(math.percentage(3.0, 4.0) == 75.0)
                    println(math.percentage(1.0, 3.0) == 33.33333333333333)
                    println(math.percentage(-2.0, 8.0) == -25.0)
                    println(math.percentage(0.0, 5.0) == 0.0)
                    println(math.isInteger(4.0))
                    println(math.isInteger(4.5) == false)
                    println(math.isInteger(-3.0))
                    println(math.isInteger(0.0))
                    println(math.isInteger(1e20))
                    println(math.isDecimal(4.5))
                    println(math.isDecimal(4.0) == false)
                }
                """, "true\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue", Set.of(), tempDir);
        // STDLIB S2a — kof.strings predicados paridade total nos 4 targets.
        matrix("stdstrings", """
                main() {
                    println(strings.isAlpha("Hello"))
                    println(strings.isAlpha("Hello World"))
                    println(strings.isAlpha(""))
                    println(strings.isNumeric("12345"))
                    println(strings.isNumeric("12.34"))
                    println(strings.isNumeric(""))
                    println(strings.isAlphaNumeric("abc123"))
                    println(strings.isAlphaNumeric("abc-123"))
                    println(strings.isAscii("ola"))
                    println(strings.isAscii("ola !123"))
                    println(strings.isUpperCase("HELLO"))
                    println(strings.isUpperCase("Hello"))
                    println(strings.isLowerCase("abc-123"))
                    println(strings.isLowerCase("Abc"))
                    println(strings.count("aabaabaa", "ab"))
                    println(strings.count("aaa", "aa"))
                    println(strings.isAlpha("Hello") == true)
                }
                """, "true\nfalse\nfalse\ntrue\nfalse\nfalse\ntrue\nfalse\ntrue\ntrue\ntrue\nfalse\ntrue\nfalse\n2\n1\ntrue", Set.of(), tempDir);
        // STDLIB S2b — kof.strings conversores (alocam String). ASCII-only:
        // é onde JVM/Native/JS concordam byte a byte. capitalize é ASCII
        // (mesma regra nos 4); reverse é byte-reverso no Native e UTF-16
        // nos outros — em ASCII as três convenções coincidem. Gap UTF-8 do
        // reverse nativo = NAT-STR01 (plan-stdlib-expansion §5).
        matrix("stdstrings2b", """
                main() {
                    var a = strings.capitalize("hello world")
                    var b = strings.capitalize("1abc")
                    var c = strings.reverse("abc123")
                    var d = strings.reverse("kayak")
                    var e = strings.repeat("ab", 3)
                    var f = strings.truncate("hello world", 5)
                    var g = strings.truncate("abc", 10)
                    var h = strings.padLeft("7", 3, "0")
                    var i = strings.padRight("ab", 5, "-")
                    println(a + "|" + b + "|" + c + "|" + d + "|" + e + "|" + f + "|" + g + "|" + h + "|" + i)
                }
                """, "Hello world|1abc|321cba|kayak|ababab|hello|abc|007|ab---", Set.of(), tempDir);
        // STDLIB S2b.4 — kof.strings conversores de palavra (split+join, ASCII).
        // A matriz roda native=x86 (tem asm); o port riscv/aarch é STRN001-gated
        // (KofStringsTest.wordConvertersGatedOnCrossArch). Em ASCII os 4 targets
        // (jvm/native/script/js) concordam byte a byte.
        matrix("stdstrings2b4", """
                main() {
                    var a = strings.toSnakeCase("HTTPServer")
                    var b = strings.toSnakeCase("XMLParser")
                    var c = strings.toCamelCase("hello_world")
                    var d = strings.toPascalCase("hello world")
                    var e = strings.toKebabCase("helloWorld")
                    var f = strings.slugify("Hello, World!! 42")
                    println(a + "|" + b + "|" + c + "|" + d + "|" + e + "|" + f)
                }
                """, "http_server|xml_parser|helloWorld|HelloWorld|hello-world|hello-world-42", Set.of(), tempDir);
        // STDLIB S4 — kof.encoding hex (UTF-8 por bytes; paridade byte-idêntica
        // nos 4: getBytes/TextEncoder/asm UTF-8 puro).
        matrix("stdenc", """
                main() {
                    var a = encoding.hexEncode("Hi")
                    var b = encoding.hexDecode("4869")
                    var c = encoding.hexEncode("café")
                    var d = encoding.hexDecode(c)
                    var e = encoding.base64Encode("Man")
                    var f = encoding.base64Decode("Y2Fmw6k=")
                    var u = encoding.urlEncode("a b")
                    var w = encoding.urlDecode("caf%C3%A9")
                    var x = encoding.base64UrlEncode("fb&O->f")
                    var y = encoding.base64UrlDecode("ZmImTy0-Zg")
                    var mark = if (encoding.hexEncode("") == "") "E" else "N"
                    println(a + "|" + b + "|" + c + "|" + d + "|" + e + "|" + f + "|" + u + "|" + w + "|" + x + "|" + y + "|" + mark)
                }
                """, "4869|Hi|636166c3a9|café|TWFu|café|a%20b|café|ZmImTy0-Zg|fb&O->f|E", Set.of(), tempDir);
        matrix("stdvalidation", """
                main() {
                    println(validation.isCpf("529.982.247-25"))
                    println(validation.isCpf("111.111.111-11"))
                    println(validation.isCnpj("11.222.333/0001-81"))
                    println(validation.isCnpj("11.222.333/0001-82"))
                    println(validation.isCep("01310-100"))
                    println(validation.isCep("0131010"))
                    println(validation.isPis("123.4567.890-0"))
                    println(validation.isPis("12345678901"))
                    println(validation.isCpf("529.982.247-25") == true)
                    println(validation.isCpf("111.111.111-11") == false)
                }
                """, "true\nfalse\ntrue\nfalse\ntrue\nfalse\ntrue\nfalse\ntrue\ntrue", Set.of(), tempDir);
        matrix("stdvalidationnet", """
                main() {
                    println(validation.isIpv4("192.168.0.1"))
                    println(validation.isIpv4("256.1.1.1"))
                    println(validation.isIpv4("01.2.3.4"))
                    println(validation.isMac("00:1A:2B:3C:4D:5E"))
                    println(validation.isMac("GG:1A:2B:3C:4D:5E"))
                    println(validation.isPort(443))
                    println(validation.isPort(65536))
                }
                """, "true\nfalse\nfalse\ntrue\nfalse\ntrue\nfalse", Set.of(), tempDir);
        matrix("stdluhn", """
                main() {
                    println(validation.isCreditCard("4111111111111111"))
                    println(validation.isCreditCard("4532 0151 1283 0366"))
                    println(validation.isCreditCard("378282246310005"))
                    println(validation.isCreditCard("4111111111111112"))
                    println(validation.isCreditCard("45"))
                    println(validation.isCreditCard("1234567890123456789"))
                }
                """, "true\ntrue\ntrue\nfalse\nfalse\nfalse", Set.of(), tempDir);
        matrix("stdipv6", """
                main() {
                    println(validation.isIpv6("::1"))
                    println(validation.isIpv6("fe80::1"))
                    println(validation.isIpv6("a:b:c:d:e:f:1:2"))
                    println(validation.isIpv6("1::2::3"))
                    println(validation.isIpv6("12345::"))
                    println(validation.isIpv6("::ffff:192.168.0.1"))
                }
                """, "true\ntrue\ntrue\nfalse\nfalse\nfalse", Set.of(), tempDir);
        matrix("stddomain", """
                main() {
                    println(validation.isDomain("example.com"))
                    println(validation.isDomain("xn--mnchen-3ya.de"))
                    println(validation.isDomain("localhost"))
                    println(validation.isDomain("example..com"))
                    println(validation.isDomain("ex_ample.com"))
                    println(validation.isDomain("x.x"))
                }
                """, "true\ntrue\nfalse\nfalse\nfalse\nfalse", Set.of(), tempDir);
        matrix("stdescape", """
                main() {
                    println(strings.escapeHtml("a<b>&\\"'c"))
                    println(strings.escapeHtml("Café & ç"))
                    println(strings.escapeHtml("&amp;lt;"))
                    println(strings.escapeHtml("<a href=\\"u\\">y</a>"))
                }
                """, "a&lt;b&gt;&amp;&quot;&#39;c\nCafé &amp; ç\n&amp;amp;lt;\n&lt;a href=&quot;u&quot;&gt;y&lt;/a&gt;", Set.of(), tempDir);
        matrix("stdws", """
                main() {
                    println(strings.removeWhitespace("  a\\tb\\nc  ") + "|" + strings.removeWhitespace("Café é"))
                    println(strings.normalizeWhitespace("  a   b  ") + "|" + strings.normalizeWhitespace("a\\t\\n b"))
                    println(strings.normalizeWhitespace("   ") + "|[" + strings.removeWhitespace("") + "]")
                }
                """, "abc|Caféé\na b|a b\n|[]", Set.of(), tempDir);
        matrix("stdnet", """
                main() {
                    val s = "https://user:pw@host.io:8443/p?q#f"
                    println(net.scheme(s) + "|" + net.host(s) + "|" + net.port(s) + "|" + net.path(s) + "|" + net.query(s) + "|" + net.fragment(s))
                    println(net.path("/only/path") + "|" + net.query("http://h?onlyquery"))
                    println(net.queryEncode("a b&c=1"))
                    println(net.queryDecode("a%20b%26c%3D1"))
                }
                """, "https|host.io|8443|/p|q|f\n/only/path|onlyquery\na%20b%26c%3D1\na b&c=1", Set.of(), tempDir);
        // STDLIB S3 — kof.uuid.isUuid (predicado de forma 8-4-4-4-12; hex min
        // ou maiúsculo; version/variant NAO verificadas — so forma canonica).
        // Deterministica => matriz nos 4 targets (riscv/aarch = UUID001 gate,
        // nao alvo da matriz; ultima linha: v4() gerada no proprio target).
        matrix("stduuidform", """
                main() {
                    println(uuid.isUuid("550e8400-e29b-41d4-a716-446655440000"))
                    println(uuid.isUuid("550E8400-E29B-41D4-A716-446655440000"))
                    println(uuid.isUuid("550e8400e29b41d4a716446655440000"))
                    println(uuid.isUuid("550e8400xe29b-41d4-a716-446655440000"))
                    println(uuid.isUuid("550e8400-e29b-41d4-a716-44665544000g"))
                    println(uuid.isUuid(""))
                    println(uuid.isUuid(uuid.v4()))
                }
                """, "true\ntrue\nfalse\nfalse\nfalse\nfalse\ntrue", Set.of(), tempDir);
        matrix("stdunescape", """
                main() {
                    println(strings.unescapeHtml("a&amp;b"))
                    println(strings.unescapeHtml("&lt;x&gt;"))
                    println(strings.unescapeHtml("caf&#233;"))
                    println(strings.unescapeHtml("&#9731;"))
                    println(strings.unescapeHtml("&&amp;"))
                    println(strings.unescapeHtml("&notreal;"))
                }
                """, "a&b\n<x>\ncafé\n\u2603\n&&\n&notreal;", Set.of(), tempDir);
        matrix("stdtime", """
                main() {
                    println(time.isLeapYear(2000))
                    println(time.isLeapYear(1900))
                    println(time.isLeapYear(2024))
                    println(time.isLeapYear(-4))
                    println(time.daysInMonth(2024, 2))
                    println(time.daysInMonth(2023, 2))
                    println(time.daysInMonth(2024, 4))
                    println(time.daysInMonth(2024, 13))
                    println(time.dayOfWeek(1970, 1, 1))
                    println(time.dayOfWeek(2026, 9, 9))
                    println(time.dayOfWeek(2024, 2, 30))
                    println(time.daysBetween(2024, 1, 1, 2024, 3, 1))
                    println(time.daysBetween(2024, 3, 1, 2024, 1, 1))
                    println(time.daysBetween(2023, 2, 29, 2023, 3, 1))
                }
                """, "true\nfalse\ntrue\nfalse\n29\n28\n30\n0\n4\n3\n0\n60\n-60\n0", Set.of(), tempDir);
                // STDLIB S7a/S7b — addDays/diffDays em data ISO (String).
                // JVM+Script (java.time) + JS (algoritmo civil, sem Date);
                // Native = TIME002 (gate honesto no compile-time; o erro é
                // provado em KofTimeE2ETest).
                matrix("stdtime2", """
                main() {
                    println(time.addDays("2024-02-28", 1))
                    println(time.addDays("2023-02-28", 1))
                    println(time.addDays("2024-12-31", 1))
                    println(time.addDays("2024-01-01", -1))
                    println(time.addDays("2024-02-30", 1))
                    println(time.addDays("garbage", 1))
                    println(time.diffDays("2024-01-01", "2024-03-01"))
                    println(time.diffDays("2024-03-01", "2024-01-01"))
                    println(time.diffDays("x", "y"))
                    println(time.addDays("0999-12-31", 1))
                    println(time.addDays("0001-01-01", -1))
                    println(time.addDays("1700-02-28", 1))
                }
                """, "2024-02-29\n2023-03-01\n2025-01-01\n2023-12-31\n\n\n60\n-60\n0\n1000-01-01\n\n1700-03-01", Set.of(), tempDir);
    }

    @Test
    void conformanceCoreStrings(@TempDir Path tempDir) throws IOException {
        // bug 43 CORRIGIDO (x86_64, 10/09): kof_string_char_at agora conta
        // code units UTF-16 (café.charAt(3)=233) — verificado no teste abaixo.
        // Residual: substring ainda é byte-based (separado, §43 nota).
        matrix("unicode", """
                main() {
                    var s = "café"
                    println(s.length)
                    println(s.charAt(3))
                    println(s + "!")
                }
                """, "4\n233\ncafé!", Set.of(), tempDir);
        matrix("unicode-astral", """
                main() {
                    var e = "a😀b"
                    println(e.length)
                    println(e.charAt(1))
                    println(e.charAt(2))
                    println(e.charAt(3))
                }
                """, "4\n55357\n56832\n98", Set.of(), tempDir);
        // bug 43 (substring face, 10/09) — code units UTF-16, paridade 4
        // targets. SÓ fronteiras bem-formadas (corte de par astral ao meio
        // exige storage WTF-8 — sub-residual §43, não entra na matriz).
        matrix("unicode-substring", """
                main() {
                    var s = "café"
                    println(s.substring(1))
                    println(s.substring(3))
                    var e = "a😀b"
                    println(e.substring(1, 3))
                    println(e.substring(0, 3).length)
                    println(e.substring(3))
                }
                """, "afé\né\n😀\n3\nb", Set.of(), tempDir);
        // bug 43 (indexOf/lastIndexOf face, 10/09) — índice em code units
        // UTF-16 nos 4 targets (needle vazio / ausente / astral).
        matrix("unicode-indexof", """
                main() {
                    var e = "a😀b😀c"
                    println(e.indexOf("c"))
                    println(e.indexOf("z"))
                    println(e.lastIndexOf("😀"))
                    println("café".indexOf("é"))
                }
                """, "6\n-1\n4\n3", Set.of(), tempDir);
        matrix("strops", """
                main() {
                    var s = "a,b,,c"
                    println(s.split(",").length)
                    println("Hello World".toLowerCase())
                    println("  x  ".trim() + "|")
                }
                """, "4\nhello world\nx|", Set.of(), tempDir);
        matrix("concat", """
                main() {
                    println("n=" + 42)
                    println(1 + 2 + "x")
                    println("x" + 1 + 2)
                }
                """, "n=42\n3x\nx12", Set.of(), tempDir);
    }

    @Test
    void conformanceCoreCollections(@TempDir Path tempDir) throws IOException {
        matrix("map", """
                main() {
                    var m = mapOf("a", 1)
                    m.put("b", 2)
                    println(m.get("a"))
                    println(m.size)
                }
                """, "1\n2", Set.of(), tempDir);
        matrix("emptylist", """
                main() {
                    var l = listOf()
                    println(l.isEmpty())
                    println(l.size)
                    println(l.contains(1))
                }
                """, "true\n0\nfalse", Set.of(), tempDir);
        matrix("setdedup", """
                main() {
                    var s = setOf(1, 2, 2, 3, 3, 3)
                    println(s.size)
                    println(s.contains(2))
                    println(s.contains(9))
                }
                """, "3\ntrue\nfalse", Set.of(), tempDir);
        matrix("listops", """
                main() {
                    var l = listOf(1, 2, 3)
                    l.add(4)
                    l.set(0, 99)
                    println(l.get(0))
                    println(l.size)
                    println(l.remove(1))
                    println(l.size)
                }
                """, "99\n4\n2\n3", Set.of(), tempDir);
        matrix("mapiter", """
                main() {
                    var m = mapOf("x", 1)
                    m.put("y", 2)
                    m.put("z", 3)
                    var ks = m.keys()
                    var sum = 0
                    for (var k in ks) {
                        sum = sum + m.get(k)
                    }
                    println(sum)
                }
                """, "6", Set.of(), tempDir);
    }

    @Test
    void conformanceCoreNull(@TempDir Path tempDir) throws IOException {
        // SEM048: null não é fabricável (literal banido); T? vem de API.
        matrix("nulleq", """
                main() {
                    var a = mapOf("x", 1).get("y")
                    var b = mapOf("x", 1).get("z")
                    println(a == b)
                    println(a != b)
                }
                """, "true\nfalse", Set.of(), tempDir);
        matrix("nulleqshortcut", """
                main() {
                    var a = mapOf("x", 1).get("y")
                    var b = mapOf("x", 1).get("z")
                    if (a == b) { println("iguais") } else { println("dif") }
                    if (a != b) { println("ne") } else { println("nao-ne") }
                }
                """, "iguais\nnao-ne", Set.of(), tempDir);
    }

    @Test
    void conformanceCoreControl(@TempDir Path tempDir) throws IOException {
        matrix("nestedif", """
                main() {
                    var x = 5
                    var r = if (x > 0) if (x > 10) "big" else "small" else "neg"
                    println(r)
                }
                """, "small", Set.of(), tempDir);
        // issue #57 — if-expr com branches heterogêneos (Int vs String) como
        // argumento direto: o typer devolve o thenType e o box pós-join
        // aplicava Integer.valueOf ao ramo String → VerifyError. Fix: ramos
        // primitivos boxeados in-branch + skip do pós-box (só codegen; o
        // check continua aprovando). JS excluído: underflow pré-existente
        // no backend KofJS p/ if heterogêneo (known-bugs §69, provado com
        // Bug 69 CORRIGIDO: paridade JVM+Native+Script+JS.
        matrix("ifexpr-heterogeneous-direct", """
                main() {
                    var s = ""
                    println(if (s == "") 1 else "s")
                }
                """, "1", Set.of(), tempDir);
        // mesma classe da #57 p/ switch-expression heterogêneo.
        matrix("switchexpr-heterogeneous-direct", """
                main() {
                    var s = ""
                    println(switch (s) {
                        case "" -> 1
                        default -> "s"
                    })
                }
                """, "1", Set.of(), tempDir);
        // §70 — heterogêneo primitivo-vs-primitivo de slots distintos
        // (Int 1-word vs Long 2-word): o join quebrava o COMPUTE_FRAMES
        // (crash AIOOBE) em vez de VerifyError. Fix: cada ramo boxeado
        // p/ SEU boxed (sem widening: `2L` imprime `2`, paridade script).
        matrix("ifexpr-intlong-direct", """
                main() {
                    var s = ""
                    println(if (s == "") 1 else 2L)
                }
                """, "1", Set.of(), tempDir);
        matrix("ifexpr-longdouble-direct", """
                main() {
                    var s = ""
                    println(if (s == "") 2L else 2.5)
                }
                """, "2", Set.of(), tempDir);
        matrix("ifexpr-intnull-direct", """
                main() {
                    var s = ""
                    println(if (s == "") 1 else null)
                }
                """, "1", Set.of(), tempDir);
        matrix("switchexpr", """
                main() {
                    var v = 3
                    var d = switch (v) {
                        case 1 -> "one"
                        case 2 -> "two"
                        case 3 -> "three"
                        default -> "other"
                    }
                    println(d)
                }
                """, "three", Set.of(), tempDir);
        matrix("breakcont", """
                main() {
                    var sum = 0
                    for (var i in listOf(1,2,3,4,5)) {
                        if (i == 2) { continue }
                        if (i == 4) { break }
                        sum = sum + i
                    }
                    println(sum)
                }
                """, "4", Set.of(), tempDir);
        matrix("recursion", """
                Int fact(Int n) {
                    if (n <= 1) { return 1 }
                    return n * fact(n - 1)
                }
                main() {
                    println(fact(10))
                }
                """, "3628800", Set.of(), tempDir);
    }

    @Test
    void conformanceCoreFunctions(@TempDir Path tempDir) throws IOException {
        matrix("lambdachain", """
                main() {
                    var l = listOf(1,2,3,4)
                    var r = l.filter((x: Int) -> x > 1).map((x: Int) -> x * 10).reduce((a: Int, b: Int) -> a + b, 0)
                    println(r)
                }
                """, "90", Set.of(), tempDir);
        matrix("lambdacapture", """
                main() {
                    var n = 0
                    var inc = () -> { n = n + 1 }
                    inc()
                    inc()
                    inc()
                    println(n)
                }
                """, "3", Set.of(), tempDir);
        matrix("array2d", """
                main() {
                    var a = new Int[3]
                    a[0] = 10
                    a[1] = 20
                    a[2] = 30
                    println(a[0] + a[1] + a[2])
                    println(a.length)
                }
                """, "60\n3", Set.of(), tempDir);
    }

    @Test
    void conformanceCoreRecordsAndStatics(@TempDir Path tempDir) throws IOException {
        matrix("record", """
                record P(Int x, Int y)
                main() {
                    var a = P(1,2)
                    var b = P(1,2)
                    println(a == b)
                    println(a)
                    println(a.x())
                }
                """, "true\nP[x=1, y=2]\n1", Set.of(), tempDir);
        // Metade JS CORRIGIDA 07/09 (verificado 08/09: JS roda 'true').
        // Metade Native CORRIGIDA (buildRecordHashCodeMethod em CompilerRecordSupport).
        matrix("recordhash", """
                record P(Int x, Int y)
                main() {
                    var a = P(1,2)
                    var b = P(1,2)
                    println(a.hashCode() == b.hashCode())
                }
                """, "true", Set.of(), tempDir);
        // PARTIAL: bug 41 (Native stub vazio KofGetStatic/KofPutStatic → lixo).
        matrix("staticfield", """
                class Counter {
                    static Int count = 0
                    static Int bump() {
                        count = count + 1
                        return count
                    }
                }
                main() {
                    println(Counter.bump())
                    println(Counter.bump())
                    println(Counter.count)
                }
                """, "1\n2\n2", Set.of(), tempDir);
        matrix("staticpluseq", """
                class Counter2 {
                    static Int count = 0
                    static Int bump() {
                        count += 2
                        return count
                    }
                }
                main() {
                    println(Counter2.bump())
                    println(Counter2.bump())
                    println(Counter2.count)
                }
                """, "2\n4\n4", Set.of(), tempDir);
    }

    // ===== Lote 2 — erros/null/JSON =====

    @Test
    void conformanceErrors(@TempDir Path tempDir) throws IOException {
        matrix("trycatch", """
                main() {
                    try {
                        throw "not found: x"
                    } catch (String e) {
                        println("caught:" + e)
                    }
                    println("after")
                }
                """, "caught:not found: x\nafter", Set.of(), tempDir);
        matrix("trycatchfin", """
                main() {
                    try {
                        println("in")
                    } catch (String e) {
                        println("c:" + e)
                    } finally {
                        println("fin")
                    }
                    println("after")
                }
                """, "in\nfin\nafter", Set.of(), tempDir);
        matrix("throwprop", """
                Int boom() {
                    throw "kaboom"
                }
                main() {
                    try {
                        boom()
                    } catch (String e) {
                        println("got:" + e)
                    }
                }
                """, "got:kaboom", Set.of(), tempDir);
        // FIXED (bug 49, 07/09): KofJS não compilava try aninhado
        // (COMP002 "try expected KofTryEnd") — JsControlFlowParser
        // consome o label de saída (done) do try no caso sem-finally.
        matrix("nestedtry", """
                main() {
                    try {
                        try {
                            throw "inner"
                        } catch (String e) {
                            println("caught-inner:" + e)
                        }
                    } catch (String e) {
                        println("caught-outer")
                    }
                    println("end")
                }
                """, "caught-inner:inner\nend", Set.of(), tempDir);
        // bug 52 (re-throw em catch) — CORRIGIDO como efeito colateral do fix
        // do bug 45 (c727fee, finally-c/return no try): o parser JS passou a
        // tratar o corpo de catch que termina em KofThrow pela região externa.
        // 4 targets agora concordam (JVM/Native/Script/JS).
        matrix("catchrethrow", """
                main() {
                    try {
                        try {
                            throw "x"
                        } catch (String e) {
                            throw "re:" + e
                        }
                    } catch (String e) {
                        println("outer:" + e)
                    }
                    println("end")
                }
                """, "outer:re:x\nend", Set.of(), tempDir);
    }

    @Test
    void conformanceNullSafety(@TempDir Path tempDir) throws IOException {
        matrix("nullnarrow", """
                String find(String k) {
                    if (k == "a") { return "A" }
                    return null
                }
                main() {
                    var v = find("a")
                    if (v != null) {
                        println("val=" + v.length)
                    }
                    var w = find("z")
                    if (w != null) {
                        println("never")
                    } else {
                        println("null-ok")
                    }
                }
                """, "val=1\nnull-ok", Set.of(), tempDir);
    }

    @Test
    void conformanceJson(@TempDir Path tempDir) throws IOException {
        matrix("jsonenc-int", """
                main() {
                    println(json.encode(42))
                    println(json.encode("oi"))
                    println(json.encode(true))
                }
                """, "42\n\"oi\"\ntrue", Set.of(), tempDir);
        matrix("jsonenc-list", """
                main() {
                    println(json.encode(listOf(1, 2, 3)))
                }
                """, "[1,2,3]", Set.of(), tempDir);
        matrix("jsonenc-record", """
                record P(Int x, Int y)
                main() {
                    println(json.encode(P(1, 2)))
                }
                """, "{\"x\":1,\"y\":2}", Set.of(), tempDir);
        matrix("jsondec-int", """
                main() {
                    println(json.decode<Int>("7"))
                    println(json.decode<String>("\\"oi\\""))
                    println(json.decode<Bool>("true"))
                }
                """, "7\noi\ntrue", Set.of(), tempDir);
        matrix("jsondec-list", """
                main() {
                    var l = json.decode<List<Int>>("[1, 2, 3]")
                    println(l.size())
                    println(l.get(1))
                }
                """, "3\n2", Set.of(), tempDir);
        // DONE após fix 07/09 (decode<Record> no interpretador —
        // KofInterpreterRuntime.decodeKofValue espelha encodeKof; antes:
        // interpretador exit 1 stderr "Point").
        matrix("jsondec-record", """
                record P(Int x, Int y)
                main() {
                    var p = json.decode<P>("{\\"x\\":1,\\"y\\":2}")
                    println(p.x())
                    println(p.y())
                }
                """, "1\n2", Set.of(), tempDir);
        // PARTIAL: bug 48 (metade Native) — Native não compila
        // (JsonDispatch sem ramo lista-de-classe). JVM/Script/JS dão 2/2;
        // a metade Script foi corrigida em KofInterpreterRuntime
        // (kof_json_decode_object_list → mapeia itens p/ KofObj).
        matrix("jsondec-recordlist", """
                record P(Int x)
                main() {
                    var l = json.decode<List<P>>("[{\\"x\\":1},{\\"x\\":2}]")
                    println(l.size())
                    println(l.get(1).x)
                }
                """, "2\n2", Set.of("native"), tempDir);
    }

    // ===== Lote 3 — concorrência DETERMINÍSTICA (ordem garantida por await/
    // FIFO; o order de fire-and-forget é NÃO-determinístico por design e fica
    // em KofConcurrency2Test com asserções frouxas) =====

    @Test
    void conformanceConcurrencyDeterministic(@TempDir Path tempDir) throws IOException {
        // ordem garantida: await bloqueia main até a task terminar.
        matrix("spawnawait-fn", """
                Int calc(Int x) {
                    return x * 2
                }
                main() {
                    var h = spawn calc(21)
                    var v = await h
                    println(v)
                }
                """, "42", Set.of(), tempDir);
        // dois handles: cada await devolve o SEU resultado (ordem dos awaits).
        matrix("spawnawait-two", """
                Int calc(Int x) {
                    return x + 1
                }
                main() {
                    var h1 = spawn calc(1)
                    var h2 = spawn calc(10)
                    println(await h1)
                    println(await h2)
                }
                """, "2\n11", Set.of(), tempDir);
        // spawn-EXPR com LAMBDA LITERAL que retorna valor (bug 46): a ordem é
        // garantida (await bloqueia). O typer de `spawn { return ... }` devolvia
        // Handle<FunctionType> e o await vazava FunctionType -> println String ->
        // SIGSEGV no Native; interp/JVM/JS davam 42. Fix 09/09: desembrulhar o
        // returnType. Travado nos 4 targets (antes excluía native).
        matrix("spawnexpr-return", """
                main() {
                    var n = 21
                    var h = spawn { return n * 2 }
                    println(await h)
                }
                """, "42", Set.of(), tempDir);
        // canal na MESMA thread: FIFO, ordem garantida, sem spawn.
        matrix("channel-samethread", """
                main() {
                    val c = channel<Int>()
                    c.send(5)
                    c.send(6)
                    var s = 0
                    var i = 0
                    while (i < 2) {
                        s = s + c.receive()
                        i++
                    }
                    println("s=" + s)
                    val cs = channel<String>()
                    cs.send("a")
                    cs.send("b")
                    println(cs.receive() + cs.receive())
                }
                """, "s=11\nab", Set.of(), tempDir);
        // canal + spawn (send na task): 4 targets dão 42 (bug 50 corrigido
        // 09/09 — usleep clobberava %rsi=&lock no caminho de fila vazia).
        matrix("channel-spawn", """
                main() {
                    val c = channel<Int>()
                    spawn {
                        c.send(42)
                    }
                    val v = c.receive()
                    println(v)
                }
                """, "42", Set.of(), tempDir);
        matrix("channel-spawn-two", """
                main() {
                    val c = channel<Int>()
                    spawn {
                        c.send(1)
                        c.send(2)
                    }
                    println(c.receive())
                    println(c.receive())
                }
                """, "1\n2", Set.of(), tempDir);
    }
}
