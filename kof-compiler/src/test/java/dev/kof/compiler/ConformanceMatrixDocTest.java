package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate de CI da Fase 9 (plano de plataforma): "CI compara matriz × testes
 * reais". A matriz {@code docs/development/conformance-matrix.md} declara,
 * por caso, quais targets são DONE e quais são PARTIAL (bug registrado). O
 * {@link ConformanceMatrixTest} é a PROVA — o {@code Set.of(...)} de cada
 * {@code matrix(...)} lista os targets EXCLUÍDOS da asserção. Este teste
 * trava que os dois concordem: uma célula PARTIAL na doc sem exclusão no
 * teste (ou vice-versa) falha aqui — a doc não pode divergir do que o teste
 * realmente valida (R6: nunca silencioso).
 */
class ConformanceMatrixDocTest {

    // coluna da matriz → nome do target no Set.of do teste
    private static final String[] COLS = { "jvm", "native", "script", "js" };

    private static Path repoRoot() {
        Path p = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && p != null; i++, p = p.getParent()) {
            if (Files.exists(p.resolve("docs/development/conformance-matrix.md"))) return p;
        }
        throw new IllegalStateException("conformance-matrix.md não achado a partir de " + p);
    }

    /** caso → targets NÃO-DONE declarados na matriz. */
    private static Map<String, Set<String>> matrixPartials() throws IOException {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        for (String line : Files.readAllLines(repoRoot().resolve("docs/development/conformance-matrix.md"))) {
            String l = line.trim();
            if (!l.startsWith("|")) continue;
            // GFM: \| é pipe literal dentro de célula — só quebra em pipe não escapado
            String[] c = l.split("(?<!\\\\)\\|");
            // | feature | saída | JVM | Native | Script | KofJS | caso |
            if (c.length < 8) continue;
            String caso = c[7].trim().replace("`", "");
            if (!caso.matches("[a-z][a-z0-9-]*")) continue; // cabeçalho/nota/separador
            Set<String> partial = new LinkedHashSet<>();
            for (int i = 0; i < 4; i++) {
                String cell = c[3 + i].trim();
                if (cell.contains("PARTIAL")) partial.add(COLS[i]);
                else if (!cell.contains("DONE")) {
                    throw new IllegalStateException("célula nem DONE nem PARTIAL: " + caso + " col " + COLS[i] + " = '" + cell + "'");
                }
            }
            out.put(caso, partial);
        }
        return out;
    }

    /** caso → targets EXCLUÍDOS (Set.of) no ConformanceMatrixTest. */
    private static Map<String, Set<String>> testExcludes() throws IOException {
        String src = Files.readString(repoRoot().resolve(
                "kof-compiler/src/test/java/dev/kof/compiler/ConformanceMatrixTest.java"));
        Map<String, Set<String>> out = new LinkedHashMap<>();
        Matcher m = Pattern.compile("matrix\\(\"([a-z0-9-]+)\".*?Set\\.of\\(([^)]*)\\),\\s*tempDir\\)",
                Pattern.DOTALL).matcher(src);
        while (m.find()) {
            Set<String> ex = new LinkedHashSet<>();
            Matcher s = Pattern.compile("\"(jvm|native|script|js)\"").matcher(m.group(2));
            while (s.find()) ex.add(s.group(1));
            out.put(m.group(1), ex);
        }
        return out;
    }

    @Test
    void matrixDocMatchesTestExclusions() throws IOException {
        Map<String, Set<String>> doc = matrixPartials();
        Map<String, Set<String>> test = testExcludes();
        assertTrue(doc.size() >= 40, "matriz deve ter dezenas de casos, achou " + doc.size());
        assertEquals(test.keySet(), doc.keySet(),
                "casos na matriz ≠ casos no teste (toda célula precisa de prova e vice-versa)");
        for (Map.Entry<String, Set<String>> e : test.entrySet()) {
            assertEquals(e.getValue(), doc.get(e.getKey()),
                    "caso '" + e.getKey() + "': exclusões do teste ≠ células PARTIAL da matriz");
        }
    }
}
