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
                "orm", "cache", "gpu", "config", "observability", "validation"};
        for (String ns : namespaces) {
            CompilationResult r = compile(tmp, ns + ".kf",
                    "main() { " + ns + ".metodoRuim() }");
            assertSem025(r, "on namespace '" + ns + "'");
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
}
