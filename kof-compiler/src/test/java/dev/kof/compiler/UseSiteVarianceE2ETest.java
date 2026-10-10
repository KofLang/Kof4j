package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * X5.4 (`D-X5-SURFACE`/`D-TYPE-VARIANCE`, 21/09) — projeção no sítio de USO:
 * `List<out Animal>` (covariante) e `List<in Animal>` (contravariante) num
 * type-argument. A projeção governa a atribuibilidade em USO, espelhando a
 * variância declaration-site; é apagada na emissão (reusa `Type.WildcardType`,
 * que os 4 alvos já apagam) — bytes idênticos.
 *
 * Red-first: antes desta fatia `List<out Animal>` gerava o type-arg textual
 * `outAnimal` (tokens concatenados sem espaço) → tipo inválido.
 */
class UseSiteVarianceE2ETest extends MultiSourceRunSupport {


    private static final String ANIMALS = """
            class Animal {
                String name
                public constructor(String name) { this.name = name }
            }

            class Dog extends Animal {
                public constructor(String name) { super(name) }
            }
            """;

    private String runJvm(Path root, List<Path> sources, String expected) throws Exception {
        Path outDir = root.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compileSources(sources, outDir, Target.JVM, root);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                "-cp", outDir.toString(), "Default.Main").redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "JVM exit code, output: " + output);
        assertEquals(expected, output, "JVM output");
        return output;
    }

    
    private static Path write(Path dir, String name, String body) throws Exception {
        Path f = dir.resolve(name);
        Files.writeString(f, body);
        return f;
    }

    @Test
    void covariantUseSiteAcceptsSubtypeList(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", ANIMALS + """
                // Retorno declarado List<out Animal> recebendo List<Dog>:
                // caminho isAssignable(sa) — só passa pela projeção `out`.
                List<out Animal> up(List<Dog> xs) { return xs }

                main() {
                    var dogs = listOf(Dog("rex"))
                    println(up(dogs).size)
                }
                """);
        runJvm(tmp, List.of(f), "1");
        runScript(tmp, List.of(f), "1");
    }

    @Test
    void contravariantUseSiteAcceptsSupertypeList(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", ANIMALS + """
                // Retorno declarado List<in Dog> recebendo List<Animal>:
                // caminho isAssignable(sa) — só passa pela projeção `in`.
                List<in Dog> down(List<Animal> xs) { return xs }

                main() {
                    var animals = listOf(Animal("a"))
                    println(down(animals).size)
                }
                """);
        runJvm(tmp, List.of(f), "1");
    }

    @Test
    void invariantListStillRejectsSubtypeList(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", ANIMALS + """
                // Sem projeção = invariante: List<Dog> NÃO sobe p/ List<Animal>.
                List<Animal> up(List<Dog> xs) { return xs }

                main() { println(up(listOf(Dog("rex"))).size) }
                """);
        CompilationResult result = driver.compileSources(List.of(f), tmp.resolve("out"), Target.JVM, tmp);
        assertFalse(result.success(), "List<Dog> → List<Animal> (invariante) deve falhar");
    }

    @Test
    void projectionCompilesOnAllTargets(@TempDir Path tmp) throws Exception {
        Path f = write(tmp, "Solo.kf", ANIMALS + """
                List<out Animal> up(List<Dog> xs) { return xs }

                main() {
                    var dogs = listOf(Dog("rex"))
                    println(up(dogs).size)
                }
                """);
        for (Target t : new Target[]{Target.JVM, Target.JS, Target.NATIVE}) {
            CompilationResult r = driver.compileSources(List.of(f), tmp.resolve("o-" + t), t, tmp);
            assertTrue(r.success(), t + " compile failed: " + r.diagnostics().getDiagnostics());
        }
        runJvm(tmp, List.of(f), "1");
    }

    @Test
    void outStillUsableAsTypeArgumentName(@TempDir Path tmp) throws Exception {
        // `<out>` sozinho não é projeção (falta o tipo) — segue nome de tipo.
        Path f = write(tmp, "Solo.kf", """
                main() {
                    var out = 3
                    println(out + 1)
                }
                """);
        runJvm(tmp, List.of(f), "4");
    }
}
