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

    // ---- bug 99: String method com formal String/CharSequence recebe
    //      Int/Char → SEM025 (R6). O registry resolve por ARIDADE, então o
    //      formal String "aceita" o Int/Char no caminho e cada backend
    //      divergia: JVM VerifyError, Native SIGSEGV, JS -1 silencioso,
    //      interpretador CCE. Kof não tem tipo char ('x' É Int) — rejeitar
    //      apontando p/ o idiom, nunca o "compila e quebra". ----

    @Test
    void stringMethodRefusoesCharEmFormalString(@TempDir Path tmp) throws IOException {
        // indexOf/contains/lastIndexOf/startsWith/endsWith com char literal
        assertSem025(compile(tmp, "I.kf", "main() {\n var s = \"abc\"\n println(s.indexOf('c'))\n}"),
                "indexOf' expects a String");
        assertSem025(compile(tmp, "C.kf", "main() {\n var s = \"abc\"\n println(s.contains('b'))\n}"),
                "contains' expects a String");
        assertSem025(compile(tmp, "L.kf", "main() {\n var s = \"abc\"\n println(s.lastIndexOf('c'))\n}"),
                "lastIndexOf' expects a String");
        assertSem025(compile(tmp, "S.kf", "main() {\n var s = \"abc\"\n println(s.startsWith('a'))\n}"),
                "startsWith' expects a String");
        assertSem025(compile(tmp, "E.kf", "main() {\n var s = \"abc\"\n println(s.endsWith('c'))\n}"),
                "endsWith' expects a String");
        // Int (não literal) no formal String também rejeita — o tipo importa,
        // não a forma da literal.
        assertSem025(compile(tmp, "N.kf", "main() {\n var s = \"abc\"\n var n = 42\n println(s.indexOf(n))\n}"),
                "indexOf' expects a String");
    }

    // Formais corretos continuam aceitos (zero regressão): String em
    // indexOf/contains/startsWith, E replace(char,char) que é intencional.
    @Test
    void stringMethodAceitaStringEReplaceChar(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "M.kf", """
                main() {
                    var s = "aXbXc"
                    println(s.indexOf("X"))
                    println(s.contains("b"))
                    println(s.lastIndexOf("c"))
                    println(s.startsWith("a"))
                    println(s.endsWith("c"))
                    println(s.replace('X', "-"))
                    println(s.replace("X", "-"))
                }
                """);
        assertTrue(r.success(), "formais String + replace(char,char) devem compilar: "
                + r.diagnostics().getDiagnostics());
    }

    // §110 (spike OTP #83, 11/09): o laço de 4 passes do corpo de MÉTODO
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

    // §110 borda: redeclaração GENUÍNA no mesmo corpo continua SEM024
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
}
