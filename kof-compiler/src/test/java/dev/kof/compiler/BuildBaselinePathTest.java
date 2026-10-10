package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate do D-BASELINE (release 25). O PATH do fork do surefire é montado à mão
 * no {@code pom.xml} raiz e precisa do JDK que compila NA FRENTE: sem isso um
 * host com outro JDK no PATH carrega KofRuntime class-file 69 com JVM 65
 * (UnsupportedClassVersionError em massa).
 *
 * <p>O separador não pode ser literal. {@code ":"} é Unix e {@code ";"} é
 * Windows, e o fork recebe a string como está — no Windows cada {@code ":"}
 * torna o primeiro item um diretório inválido, o bin do JDK deixa de ir na
 * frente e o primeiro item de PATH do chamador desaparece. O pom precisa das
 * propriedades do Maven ({@code ${path.separator}} / {@code ${file.separator}}),
 * que resolvem certo nos dois sistemas. Este teste roda em qualquer host e
 * falha no pom não-portável, que é onde o defeito aparece (issue #787).
 */
class BuildBaselinePathTest {

    /**
     * Sobe até o pom AGREGADOR (o único com {@code <modules>}): o D-BASELINE
     * vive no {@code pluginManagement} do raiz, não nos poms dos módulos — um
     * {@code repoRoot()} que parasse no primeiro {@code pom.xml} acharia o do
     * {@code kof-compiler} e passaria vazio.
     */
    private static Path repoRoot() throws IOException {
        Path p = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && p != null; i++, p = p.getParent()) {
            Path pom = p.resolve("pom.xml");
            if (Files.exists(pom) && Files.readString(pom).contains("<modules>")) return p;
        }
        throw new IllegalStateException("pom agregador (com <modules>) não achado a partir de " + p);
    }

    @Test
    void surefirePathUsesPortableSeparators() throws IOException {
        String pom = Files.readString(repoRoot().resolve("pom.xml"));
        Matcher m = Pattern.compile("<PATH>(.*?)</PATH>").matcher(pom);
        assertTrue(m.find(), "o pom raiz precisa declarar o <PATH> do D-BASELINE");
        String path = m.group(1);
        assertTrue(path.contains("${path.separator}"),
                "o <PATH> do surefire precisa juntar com ${path.separator} (a \":\" literal só vale no Unix): " + path);
        assertTrue(path.contains("${file.separator}"),
                "o <PATH> do surefire precisa montar o bin do JDK com ${file.separator} (a barra literal só vale no Unix): " + path);
        assertFalse(path.matches(".*\\$\\{java\\.home\\}/bin.*"),
                "o <PATH> não pode usar a barra literal em ${java.home}/bin: " + path);
    }
}
