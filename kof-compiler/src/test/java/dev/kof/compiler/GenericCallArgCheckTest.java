package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #688 — argumentos de CHAMADA (função/método/construtor) usavam o
 * `isAssignable` estrutural de 2 args, que aceita quaisquer dois ClassType e
 * ignora os type-args. Resultado: `tentaEscrever(Caixa<Cachorro>)` num
 * parâmetro `Caixa<Animal>` (ou `List<Dog>`→`List<Animal>`) compilava e
 * corrompia em runtime. Agora o caminho é o nominal (mesmo de declarações):
 * hierarquia + args de genérico (invariante por padrão, `out`/`in` por
 * variância declaration-site).
 */
class GenericCallArgCheckTest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String ANIMALS = """
            interface Animal { String nome() }
            class Cachorro implements Animal { String nome() { return "cachorro" } }
            class Gato implements Animal { String nome() { return "gato" } }
            class Caixa<T> { T valor }
            class Outra<T> { T valor }
            """;

    private CompilationResult compile(Path dir, String body) throws Exception {
        Path f = dir.resolve("M.kf");
        Files.writeString(f, body);
        return driver.compileSources(List.of(f), dir.resolve("out-" + System.nanoTime()), Target.JVM, dir);
    }

    private static boolean has(CompilationResult r, String code) {
        return r.diagnostics().getDiagnostics().stream().anyMatch(d -> code.equals(d.code()));
    }

    @Test
    void plainInvariantArgRejected(@TempDir Path t) throws Exception {
        // O núcleo do #688: genérico SEM projeção também era aceito.
        CompilationResult r = compile(t, ANIMALS + """
                void tentaEscrever(Caixa<Animal> c) { c.valor = new Gato() }
                main() {
                    var c = new Caixa<Cachorro>()
                    tentaEscrever(c)
                }
                """);
        assertFalse(r.success(), "Caixa<Cachorro> em Caixa<Animal> deve ser rejeitado (invariante)");
        assertTrue(has(r, "SEM014"), "SEM014 esperado, veio: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void builtinListArgRejected(@TempDir Path t) throws Exception {
        CompilationResult r = compile(t, ANIMALS + """
                void guardar(List<Animal> xs) { }
                main() {
                    var xs = listOf(new Cachorro())
                    guardar(xs)
                }
                """);
        assertFalse(r.success(), "List<Cachorro> em List<Animal> deve ser rejeitado (invariante)");
        assertTrue(has(r, "SEM014"), "SEM014 esperado, veio: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void differentRawRejected(@TempDir Path t) throws Exception {
        CompilationResult r = compile(t, ANIMALS + """
                void usar(Outra<Animal> o) { }
                main() {
                    var c = new Caixa<Cachorro>()
                    usar(c)
                }
                """);
        assertFalse(r.success(), "Caixa<...> em Outra<...> deve ser rejeitado (raw nominal)");
        assertTrue(has(r, "SEM014"), "SEM014 esperado, veio: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void methodCallArgRejected(@TempDir Path t) throws Exception {
        CompilationResult r = compile(t, ANIMALS + """
                class Servico {
                    void tentaEscrever(Caixa<Animal> c) { c.valor = new Gato() }
                }
                main() {
                    var s = new Servico()
                    var c = new Caixa<Cachorro>()
                    s.tentaEscrever(c)
                }
                """);
        assertFalse(r.success(), "method call com Caixa<Cachorro> em Caixa<Animal> deve falhar");
        assertTrue(has(r, "SEM014"), "SEM014 esperado, veio: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void sameGenericArgCompiles(@TempDir Path t) throws Exception {
        CompilationResult r = compile(t, ANIMALS + """
                void ler(Caixa<Cachorro> c) { println(c.valor.nome()) }
                main() {
                    var c = new Caixa<Cachorro>()
                    c.valor = new Cachorro()
                    ler(c)
                }
                """);
        assertTrue(r.success(), "mesmo arg deve compilar: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void upcastInCallArgStillCompiles(@TempDir Path t) throws Exception {
        // O subtipo DIRETO (não-generico) continua atribuível ao super.
        CompilationResult r = compile(t, ANIMALS + """
                void usar(Animal a) { println(a.nome()) }
                main() {
                    usar(new Cachorro())
                }
                """);
        assertTrue(r.success(), "Cachorro→Animal deve compilar: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void outProjectionReadCovarianceRemains(@TempDir Path t) throws Exception {
        // `out` no sítio de uso é covariância de LEITURA documentada
        // (D-TYPE-VARIANCE); o parâmetro projetado segue aceitando o arg mais
        // estreito. A escrita-atráves-de-out é uma lacuna separada (o
        // compilador não impõe read-only) — não regredimos a covariância.
        CompilationResult r = compile(t, ANIMALS + """
                void ler(Caixa<out Animal> c) { println(c.valor.nome()) }
                main() {
                    var c = new Caixa<Cachorro>()
                    c.valor = new Cachorro()
                    ler(c)
                }
                """);
        assertTrue(r.success(), "Caixa<out Animal> aceita Caixa<Cachorro>: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void rawParamStaysPermissive(@TempDir Path t) throws Exception {
        // Raw (sem args) e tipo-param permanecem permissivos (erasure/inferência).
        CompilationResult r = compile(t, ANIMALS + """
                void usar(Caixa c) { }
                main() {
                    usar(new Caixa<Cachorro>())
                }
                """);
        assertTrue(r.success(), "raw deve seguir permissivo: " + r.diagnostics().getDiagnostics());
    }
}
