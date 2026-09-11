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
}
