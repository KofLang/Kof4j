package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regressão P0 (R6 — nunca silencioso): resolução de método/campo que falha
 * em símbolo CONHECIDO (namespace builtin, classe do módulo, superclasse)
 * emite SEM025 antes do fallback UNKNOWN. UNKNOWN só existe para error
 * recovery — nunca declara método implícito.
 *
 * Bugs: #7 (namespaces builtin), #3 (campo em classe conhecida),
 * #6 (super.metodoInexistente), #8 (receiver conhecido não engole método).
 */
class SemanticResolutionTest {
    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(@TempDir Path tmp, String name, String src) throws IOException {
        Path source = tmp.resolve(name);
        Files.writeString(source, src);
        return driver.compile(source, tmp.resolve("out"), Target.JVM);
    }

    private void assertSem025(CompilationResult r, String snippet) {
        assertFalse(r.success(), "deve falhar: " + snippet);
        boolean found = r.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> "SEM025".equals(d.code()) && d.message().contains(snippet));
        assertTrue(found, "esperava SEM025 contendo '" + snippet + "', foi: "
                + r.diagnostics().getDiagnostics());
    }

    // ---- #7: namespace builtin + método inexistente → SEM025 (matriz) ----

    @Test
    void unknownMethodOnBuiltinNamespaces(@TempDir Path tmp) throws IOException {
        String[] namespaces = {"db", "log", "http", "mq", "time", "security",
                "orm", "cache", "gpu", "config", "observability", "validation",
                // Família KofStd (lane STDLIB) — R6: método inexistente em
                // qualquer namespace stdlib dá SEM025, nunca é descartado em
                // silêncio pelo lowerer (fonte única: typer e lowerer usam a
                // mesma tabela KofStd/Kof<Dom>.staticMethod).
                "strings", "random", "uuid", "encoding", "math", "net"};
        for (String ns : namespaces) {
            CompilationResult r = compile(tmp, ns + ".kf",
                    "main() { " + ns + ".metodoRuim() }");
            assertSem025(r, "on namespace '" + ns + "'");
        }
    }

    @Test
    void wrongArityOnStdlibMethod(@TempDir Path tmp) throws IOException {
        // R6 (complemento do anterior): aridade ERRADA em um nome que EXISTE
        // também é SEM025, não "typer passou e lowerer descartou". A tabela
        // de dispatch valida argc; se o nome não casa na aridade, o
        // staticMethod retorna null => SEM025 (prova a fonte única).
        String[][] cases = {
                {"time", "time.isWeekend(2026, 9)"},           // precisa 3
                {"random", "random.randomInt()"},              // precisa 1
                {"strings", "strings.capitalize()"},           // precisa 1
                {"validation", "validation.formatCpf(1, 2)"},  // precisa 1
                {"uuid", "uuid.isUuid()"},                     // precisa 1
                // Famílias de OUTRAS lanes (varredura R6 10/09 — aditivo,
                // prova persistida das sondas manuais db.connect()/http.get()/
                // cache.get()/mq.publish(): nome EXISTE, aridade não casa).
                {"db", "db.connect()"},                        // precisa ≥1
                {"http", "http.get()"},                        // precisa ≥1
                {"cache", "cache.get()"},                      // precisa 1+
                {"mq", "mq.publish()"},                        // precisa 2
                {"security", "security.hash()"},               // precisa 1
                {"orm", "orm.save()"},                         // precisa >=1
                {"config", "config.get()"},                    // precisa 1
                {"cache", "cache.put()"},                      // precisa 2
                {"log", "log.info()"},                         // precisa >=1
        };
        for (String[] c : cases) {
            CompilationResult r = compile(tmp, c[0] + ".kf", "main() { " + c[1] + " }");
            assertSem025(r, "on namespace '" + c[0] + "'");
        }
    }

    @Test
    void unknownMethodOnWebApp(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "W.kf",
                "main() { web.app().metodoRuim() }");
        assertSem025(r, "on namespace 'web.app'");
    }

    // ---- #6: super.metodoInexistente → SEM025 ----

    @Test
    void unknownMethodOnSuper(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "S.kf", """
                class Base {
                    Int ok() { return 1 }
                }
                class Sub extends Base {
                    Int bad() { return super.naoExiste() }
                }
                main() { println(Sub().bad()) }
                """);
        assertSem025(r, "in superclass 'Base'");
    }

    // ---- #3: campo inexistente em classe conhecida → SEM025 ----

    @Test
    void unknownFieldOnKnownClass(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "F.kf", """
                class P {
                    Int a
                }
                main() {
                    var p = P()
                    println(p.campoInexistente)
                }
                """);
        assertSem025(r, "Cannot resolve field 'campoInexistente' on type 'P'");
    }

    // ---- casos válidos NÃO podem diagnosticar (sem falso-positivo) ----

    @Test
    void validCallsStayGreen(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "V.kf", """
                class Base {
                    Int ok() { return 1 }
                }
                class Sub extends Base {
                    Int usa() { return super.ok() }
                }
                main() {
                    var s = Sub()
                    println(s.usa())
                    var l = listOf(1, 2, 3)
                    l.add(4)
                    println(l.size())
                    println(l.contains(2))
                    var m = mapOf("a", 1)
                    m.put("b", 2)
                    println(m.get("a"))
                    var st = setOf("x", "y")
                    println(st.contains("x"))
                    log.info("hello")
                    println(time.now())
                    println("ok")
                }
                """);
        assertTrue(r.success(), "casos válidos devem compilar: "
                + r.diagnostics().getDiagnostics());
    }

    // ---- SG-017 (SEM041): `new` de classe abstrata → erro ----

    @Test
    void abstractClassInstantiationFails(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "A.kf", """
                abstract class Shape {
                    Int area() { return 0 }
                }
                main() {
                    var s = Shape()
                    println(s)
                }
                """);
        assertFalse(r.success(), "deve falhar: new de abstract class");
        boolean found = r.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> "SEM041".equals(d.code())
                        && d.message().contains("abstract class 'Shape'"));
        assertTrue(found, "esperava SEM041, foi: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void abstractClassSubclassInstantiationStaysGreen(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "A.kf", """
                abstract class Shape {
                    Int area() { return 0 }
                }
                class Circle extends Shape {
                }
                main() {
                    var c = Circle()
                    println(c)
                }
                """);
        assertTrue(r.success(), "subclass concreta instanciável: "
                + r.diagnostics().getDiagnostics());
    }

    // ---- método inexistente em classe do módulo → SEM025 (já coberto
    //      pelo caminho ClassType; trava regressão do gate isKnownReceiver) ----

    @Test
    void unknownMethodOnKnownClass(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "M.kf", """
                class P {
                    Int a
                }
                main() {
                    var p = P()
                    p.naoExiste()
                }
                """);
        assertSem025(r, "on type 'P'");
    }

    // ---- #99 (R6): campo estático num TIPO PRIMITIVO (Int.MAX_VALUE) — fake
    // idiom, nunca existiu no Kof; antes passava sem diagnóstico e gerava lixo
    // nos 3 targets (JVM NoClassDefFoundError "?", Native SIGSEGV, Script null)
    // — e `var x = Int.MAX_VALUE` CRASHAVA o compilador (ASM visitMaxs). ----

    private void assertSem050(CompilationResult r, String snippet) {
        assertFalse(r.success(), "deve falhar: " + snippet);
        boolean found = r.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> "SEM050".equals(d.code()) && d.message().contains(snippet));
        assertTrue(found, "esperava SEM050 contendo '" + snippet + "', foi: "
                + r.diagnostics().getDiagnostics());
    }

    // §130 (spike OTP #83, 11/09): o laço de 4 passes do corpo de MÉTODO
    // (inference de return-type "bug 26") re-analisava cada corpo no MESMO
    // SymbolTable → do 2º pass em diante, todo `var` colidia (SEM024 falso)
    // quando UM método sem tipo declarado termina em `return <expr>` (ou chama
    // outro da classe que faz isso). Forma do spike: builder de cadeia com
    // overload `child(id, f)` delegando para `child(id, f, politica)`.
    @Test
    void redeclarationFalsePositiveEmMetodoDeClasse(@TempDir Path tmp) throws IOException {
        String src = """
                class Node {
                    Int value
                    Int rest
                    constructor(Int value, Int rest) { this.value = value; this.rest = rest }
                }
                class S {
                    Int total
                    constructor() { this.total = 0 }
                    add(Int v) {
                        return this.add2(Node(v, 0))
                    }
                    add2(Node n) {
                        var q = n.value
                        var r = n.rest
                        total = total + q + r
                        return total
                    }
                }
                main() {
                    var s = S()
                    s.add(3)
                    println(s.total == 3)
                }
                """;
        CompilationResult r = compile(tmp, "S110.kf", src);
        assertTrue(r.success(), "corpos de método re-analisados devem aceitar 'var' "
                + "repetido (escopo por análise, não por classe): "
                + r.diagnostics().getDiagnostics());
        // executa de verdade (o fix nao pode trocar SEM024 por bytecode quebrado)
        java.nio.file.Path out = tmp.resolve("out-run");
        CompilationResult r2 = driver.compile(tmp.resolve("S110.kf"), out, Target.JVM);
        assertTrue(r2.success(), "segunda compilacao p/ run: " + r2.diagnostics().getDiagnostics());
        try {
            Process p = new ProcessBuilder("java", "-cp", out.toString(), "Default.Main")
                    .redirectErrorStream(true).start();
            String os = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0, p.waitFor(), "run deve sair limpo: " + os);
            assertTrue(os.contains("true"), "inference de return-type encadeado deve "
                    + "produzir o valor certo: " + os);
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    // §130 borda: redeclaração GENUÍNA no mesmo corpo continua SEM024
    // (o fix só isola passes, nunca afrouxa o SC5).
    @Test
    void redeclaracaoMesmoCorpoAindaErro(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "S110b.kf", """
                main() {
                    var q = 1
                    var q = 2
                    println(q)
                }
                """);
        assertFalse(r.success(), "redeclaracao no mesmo escopo continua SEM024");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> "SEM024".equals(d.code()) && d.message().contains("'q'")),
                "esperava SEM024 de 'q', foi: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void staticFieldOnPrimitiveTypeRejected(@TempDir Path tmp) throws IOException {
        // as 3 formas: expressão solta, println, e assignment (o último era o
        // que CRASHAVA o compilador — agora é SEM050 limpo, não COMP002).
        String[] types = {"Int", "Long", "Double", "Float", "Char", "Byte", "Short", "Bool"};
        String[] fields = {"MAX_VALUE", "MIN_VALUE", "SIZE", "foo"};
        for (String t : types) {
            for (String f : fields) {
                assertSem050(compile(tmp, "e.kf", "main() { var x = " + t + "." + f + " }"),
                        "'" + t + "' é um tipo primitivo");
            }
        }
    }

    @Test
    void primitiveAsTypeAndLiteralStillCompile(@TempDir Path tmp) throws IOException {
        // o SEM050 não pode quebrar o que LEGITIMAMENTE usa um nome de tipo:
        // anotação (`x: Int`), cast (`as Int`), e acesso a campo em INSTÂNCIA
        // (String.length, "abc".length). Proibido regridir (regra 1).
        CompilationResult r = compile(tmp, "ok.kf", """
                main() {
                    var x: Int = 2147483647
                    var s = "abc"
                    println(s.length)
                    println("a😀b".length)
                    val big = 3000000000
                    println(x + big)
                }
                """);
        assertTrue(r.success(), "legítimo deve compilar: " + r.diagnostics().getDiagnostics());
    }

    // ---- #100 (R6, paridade absoluta): Char em método de String — o programa
    // era ACEITO e quebrava de um jeito DIFERENTE em cada target (JVM
    // VerifyError/IncompatibleClassChangeError, Native SIGSEGV/saída vazia,
    // Script false/vazio). REJEITAR em compile-time com o mesmo SEM051 em
    // todos os backends (lowering = frontend único dos 5 alvos). ----

    @Test
    void charArgOnStringMethodRejected(@TempDir Path tmp) throws IOException {
        String[] exprs = {
            "\"abc\".indexOf('c')", "\"abc\".lastIndexOf('b')", "\"abc\".contains('b')",
            "\"abc\".startsWith('a')", "\"abc\".endsWith('c')", "\"a,b\".split(',')",
            "\"abc\".concat('x')", "\"abc\".equalsIgnoreCase('a')",
            "\"abc\".compareTo('a')", "\"abc\".compareToIgnoreCase('a')",
            "\"abc\".equalsIgnoreCase(5)", "\"abc\".concat(5)" };
        for (String e : exprs) {
            CompilationResult r = compile(tmp, "e.kf", "main() { println(" + e + ") }");
            assertFalse(r.success(), "deve falhar: " + e);
            boolean found = r.diagnostics().getDiagnostics().stream()
                    .anyMatch(d -> "SEM051".equals(d.code()) && d.message().contains("como argumento"));
            assertTrue(found, "esperava SEM051 p/ '" + e + "', foi: "
                    + r.diagnostics().getDiagnostics());
        }
    }

    @Test
    void stringMethodsWithStringOrCharArgsStillCompile(@TempDir Path tmp) throws IOException {
        // não regridir (regra 1): literal String ok; replace(char,char) é o
        // overload LEGAL da registry; charAt/substring recebem numérico
        // (Char é Int em Kof — widening do usuário, não erro do compilador).
        CompilationResult r = compile(tmp, "ok.kf", """
                main() {
                    var s = "abc"
                    println(s.indexOf("c"))
                    println(s.replace('b', 'x'))
                    println(s.charAt(1))
                    println(s.substring(1))
                    println(s.contains("b"))
                    println(s.compareTo("a"))
                }
                """);
        assertTrue(r.success(), "legítimo deve compilar: " + r.diagnostics().getDiagnostics());
    }

    // ---- #96 (paridade absoluta JVM=JS=X86=ARM=RISC): funções da stdlib
    // `strings.*` chamadas como MÉTODO de String — o typer aceitava e cada
    // backend quebrava de um jeito (JVM NoSuchMethodError, Native link-fail,
    // JS roda o nativo do JS, Script roda por reflexão). Opção B: REJEITAR em
    // compile-time (SEM052) apontando para o idiom real do corpus. ----

    @Test
    void stringsFunctionsAsInstanceMethodsRejected(@TempDir Path tmp) throws IOException {
        String[] exprs = {
            "\"ab\".repeat(3)", "\"ab\".truncate(3)", "\"7\".padStart(5,\"-\")",
            "\"7\".padEnd(5,\"-\")", "\"7\".padLeft(3,\"0\")", "\"7\".padRight(3,\"0\")",
            "\"ab\".reverse()", "\"ab\".capitalize()", "\"abc\".count(\"a\")",
            "\"a\".isAlpha()", "\"a\".isNumeric()", "\"a_b\".toCamelCase()",
            "\"a\".escapeHtml()", "\"a\".slugify()" };
        for (String e : exprs) {
            CompilationResult r = compile(tmp, "e.kf", "main() { println(" + e + ") }");
            assertFalse(r.success(), "deve falhar: " + e);
            boolean found = r.diagnostics().getDiagnostics().stream()
                    .anyMatch(d -> "SEM052".equals(d.code()) && d.message().contains("strings."));
            assertTrue(found, "esperava SEM052 p/ '" + e + "', foi: "
                    + r.diagnostics().getDiagnostics());
        }
    }

    @Test
    void stringsFunctionsAndRealStringMethodsStillCompile(@TempDir Path tmp) throws IOException {
        // não regridir (regra 1): a forma função da stdlib e os métodos QUE
        // SÃO de String na registry (toUpperCase/trim/split/replace/substring).
        CompilationResult r = compile(tmp, "ok.kf", """
                main() {
                    println(strings.repeat("ab", 3))
                    println(strings.truncate("abcdef", 3))
                    println(strings.padLeft("7", 3, "0"))
                    println(strings.padRight("7", 3, "0"))
                    println(strings.reverse("ab"))
                    println(strings.capitalize("ab"))
                    println(strings.count("abc", "a"))
                    println(strings.isAlpha("a"))
                    var s = "ab"
                    println(s.toUpperCase())
                    println(s.trim())
                    println(s.replace("a", "b"))
                    println(s.substring(1))
                }
                """);
        assertTrue(r.success(), "legítimo deve compilar: " + r.diagnostics().getDiagnostics());
    }

    // ---- #98 (paridade absoluta JVM=JS=X86=ARM=RISC): `<`/`<=`/`>`/`>=` em
    // String era aceito e dava LIXO DIFERENTE em cada target (JVM tudo-false
    // via if_acmp, Native comparava PONTEIRO, Script lexicográfico). Opção B:
    // REJEITAR (SEM053) apontando p/ `compareTo` — igual nos 5 alvos. ----

    @Test
    void stringOrderingOperatorsRejected(@TempDir Path tmp) throws IOException {
        String[] exprs = {
            "\"abc\" < \"abd\"", "\"abc\" <= \"abd\"", "\"abc\" > \"abd\"",
            "\"abc\" >= \"abd\"", "\"abd\" < \"abc\"", "\"abc\" < 'b'" };
        for (String e : exprs) {
            CompilationResult r = compile(tmp, "e.kf", "main() { println(" + e + ") }");
            assertFalse(r.success(), "deve falhar: " + e);
            boolean found = r.diagnostics().getDiagnostics().stream()
                    .anyMatch(d -> "SEM053".equals(d.code()) && d.message().contains("compareTo"));
            assertTrue(found, "esperava SEM053 p/ '" + e + "', foi: "
                    + r.diagnostics().getDiagnostics());
        }
    }

    @Test
    void stringEqualityAndNumericOrderingStillCompile(@TempDir Path tmp) throws IOException {
        // não regridir: `==`/`!=` de String (conteúdo, congelado) e toda
        // comparação numérica (o guard é SÓ p/ String).
        CompilationResult r = compile(tmp, "ok.kf", """
                main() {
                    var a = "abc"
                    var b = "abd"
                    println(a == b)
                    println(a != b)
                    println(a == "abc")
                    println(3 < 5)
                    println(3L <= 5L)
                    println(2.5 > 1.5)
                    println(a.compareTo(b) < 0)
                    var n = 0
                    while (n < 10) { n = n + 1 }
                    println(n)
                }
                """);
        assertTrue(r.success(), "legítimo deve compilar: " + r.diagnostics().getDiagnostics());
    }

    // ---- subscript `[]`: só existe para ARRAY no corpus (learn/04:84,
    // control-flow.md:81). Em String/List/Map/Set era ACEITO e quebrava de um
    // jeito por target (JVM VerifyError aaload, Native/Script vazios). SEM054
    // rejeita nos 5 alvos (paridade absoluta) — escrita (l[0] = 9) inclusa. ----

    @Test
    void subscriptOnCollectionsRejected(@TempDir Path tmp) throws IOException {
        String[] exprs = {
            "var s = \"abc\"; println(s[0])",
            "var l = listOf(10, 20); println(l[1])",
            "var m = mapOf(\"a\", 1); println(m[\"a\"])",
            "var st = setOf(\"a\"); println(st[\"a\"])",
            "var l2 = listOf(1); l2[0] = 9" };
        for (String e : exprs) {
            CompilationResult r = compile(tmp, "e.kf", "main() { " + e + " }");
            assertFalse(r.success(), "deve falhar: " + e);
            boolean found = r.diagnostics().getDiagnostics().stream()
                    .anyMatch(d -> "SEM054".equals(d.code()) && d.message().contains("array"));
            assertTrue(found, "esperava SEM054 p/ '" + e + "', foi: "
                    + r.diagnostics().getDiagnostics());
        }
    }

    @Test
    void subscriptOnArraysStillCompiles(@TempDir Path tmp) throws IOException {
        // array de verdade (o único [] do corpus) não regride (regra 1).
        CompilationResult r = compile(tmp, "ok.kf", """
                main() {
                    var nums = new Int[3]
                    nums[0] = 5
                    println(nums[0])
                    var words = new String[2]
                    words[1] = "x"
                    println(words[1])
                    var grid = new Int[2][2]
                    grid[0][1] = 7
                    println(grid[0][1])
                }
                """);
        assertTrue(r.success(), "array deve compilar: " + r.diagnostics().getDiagnostics());
    }
}
