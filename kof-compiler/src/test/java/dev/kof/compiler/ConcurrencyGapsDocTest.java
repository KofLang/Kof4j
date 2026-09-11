package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guard da tabela "Gaps por target" de {@code learn/18-concurrency.md}, no
 * mesmo espírito do {@link ConformanceMatrixDocTest} (que trava
 * {@code conformance-matrix.md} contra o {@code ConformanceMatrixTest}).
 *
 * <p>Motivo de existir: essa tabela apodreceu em silêncio. Durante meses
 * declarou que {@code poll}/{@code done}/{@code cancel}/{@code cancelled}/
 * {@code selectAny} reportavam {@code CONC001} no Native, quando os quatro
 * já funcionavam no x86_64 desde 31/08 — porque nada comparava a doc com o
 * código. O {@code conformance-matrix.md} não apodreceu pelo motivo oposto:
 * tem guard. Este teste dá o mesmo guard a esta tabela.
 *
 * <p>Duas asserções, uma por tipo de célula:
 * <ul>
 *   <li><b>{@code ✅}/{@code ⚠️}</b> — a célula precisa citar ao menos um
 *       método de teste {@code Classe.metodo}, e esse método precisa existir
 *       de fato em {@code src/test/java}. Marcar suportado sem prova falha.</li>
 *   <li><b>{@code ❌}</b> (só a coluna riscv64/aarch64) — o símbolo do
 *       runtime correspondente não pode ser emitido por nenhum emissor
 *       cross ({@code nat/NativeRiscv*.java}, {@code nat/NativeAarch64*.java}).
 *       Se alguém portar o construto e esquecer da doc, falha aqui.</li>
 * </ul>
 *
 * <p>R6: a doc nunca pode divergir em silêncio do que o código faz.
 */
class ConcurrencyGapsDocTest {

    private static final String DOC = "learn/18-concurrency.md";

    /** Construto declarado na 1ª coluna → símbolos do runtime que o implementam. */
    private static final Map<String, List<String>> SYMBOLS = Map.of(
            "spawn stmt", List.of("kof_spawn"),
            "val r = spawn expr", List.of("kof_spawn_result"),
            "await r", List.of("kof_await"),
            "poll / done", List.of("kof_poll", "kof_done"),
            "cancel / cancelled", List.of("kof_cancel"),
            "selectAny", List.of("kof_select_any"),
            "awaitTimeout", List.of("kof_await_timeout"));

    /** Índice da coluna riscv64/aarch64 nas células de dados (0 = JVM). */
    private static final int COL_CROSS = 2;

    private static final Pattern TEST_REF =
            Pattern.compile("`([A-Z][A-Za-z0-9]*Test)\\.([a-zA-Z][A-Za-z0-9_]*)`");

    private static Path repoRoot() {
        Path p = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && p != null; i++, p = p.getParent()) {
            if (Files.exists(p.resolve(DOC))) return p;
        }
        throw new IllegalStateException(DOC + " não achado a partir de " + System.getProperty("user.dir"));
    }

    /** Linhas de dados da tabela de gaps: construto → 4 células (JVM, x86, cross, JS). */
    private static Map<String, String[]> gapRows() throws IOException {
        Map<String, String[]> rows = new LinkedHashMap<>();
        boolean inTable = false;
        for (String line : Files.readAllLines(repoRoot().resolve(DOC))) {
            String l = line.trim();
            if (l.startsWith("| Construto |")) { inTable = true; continue; }
            if (inTable && !l.startsWith("|")) break;   // fim da tabela
            if (!inTable || l.startsWith("|--")) continue;
            String[] c = l.split("(?<!\\\\)\\|");
            // ["", construto, jvm, x86, cross, js]
            if (c.length < 6) continue;
            String construto = c[1].trim().replace("`", "");
            rows.put(construto, new String[] { c[2].trim(), c[3].trim(), c[4].trim(), c[5].trim() });
        }
        return rows;
    }

    private static final String[] TEST_ROOTS = {
            "kof-compiler/src/test/java", "kof-cli/src/test/java",
            "kof-script/src/test/java", "kof-c-compiler/src/test/java" };

    /** Todo método de teste declarado nos módulos, como "Classe.metodo". */
    private static Set<String> declaredTestMethods() throws IOException {
        Set<String> out = new LinkedHashSet<>();
        Path root = repoRoot();
        Pattern decl = Pattern.compile("\\bvoid\\s+([a-zA-Z][A-Za-z0-9_]*)\\s*\\(");
        for (String rel : TEST_ROOTS) {
            Path dir = root.resolve(rel);
            if (!Files.isDirectory(dir)) continue;   // módulo ausente: não é erro
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path f : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    String cls = f.getFileName().toString().replace(".java", "");
                    Matcher m = decl.matcher(Files.readString(f));
                    while (m.find()) out.add(cls + "." + m.group(1));
                }
            }
        }
        return out;
    }

    /** Fontes dos emissores riscv64/aarch64 concatenadas. */
    private static String crossEmitterSources() throws IOException {
        Path nat = repoRoot().resolve("kof-compiler/src/main/java/dev/kof/compiler/nat");
        StringBuilder sb = new StringBuilder();
        try (Stream<Path> files = Files.list(nat)) {
            for (Path f : files.filter(f -> {
                String n = f.getFileName().toString();
                return n.endsWith(".java") && (n.startsWith("NativeRiscv") || n.startsWith("NativeAarch64"));
            }).toList()) {
                sb.append(Files.readString(f)).append('\n');
            }
        }
        return sb.toString();
    }

    @Test
    void tabelaDeGapsDeclaraTodosOsConstrutosConhecidos() throws IOException {
        assertEquals(SYMBOLS.keySet(), gapRows().keySet(),
                "a tabela de gaps de " + DOC + " precisa listar exatamente os construtos "
                        + "mapeados neste teste — adicionar construto na doc exige adicioná-lo em SYMBOLS");
    }

    @Test
    void celulaSuportadaCitaTesteQueExiste() throws IOException {
        Set<String> declarados = declaredTestMethods();
        List<String> problemas = new ArrayList<>();

        for (Map.Entry<String, String[]> row : gapRows().entrySet()) {
            String[] cells = row.getValue();
            for (int col = 0; col < cells.length; col++) {
                String cell = cells[col];
                if (cell.contains("❌")) continue;

                Matcher m = TEST_REF.matcher(cell);
                List<String> refs = new ArrayList<>();
                while (m.find()) refs.add(m.group(1) + "." + m.group(2));

                if (refs.isEmpty()) {
                    problemas.add(row.getKey() + " [col " + col + "]: célula suportada sem citar teste — " + cell);
                    continue;
                }
                for (String ref : refs) {
                    if (!declarados.contains(ref)) {
                        problemas.add(row.getKey() + " [col " + col + "]: cita `" + ref + "`, que não existe em src/test/java");
                    }
                }
            }
        }
        assertTrue(problemas.isEmpty(), "células de suporte sem prova em " + DOC + ":\n  " + String.join("\n  ", problemas));
    }

    @Test
    void celulaAusenteNoCrossNaoTemSimboloEmitido() throws IOException {
        String src = crossEmitterSources();
        List<String> problemas = new ArrayList<>();

        for (Map.Entry<String, String[]> row : gapRows().entrySet()) {
            if (!row.getValue()[COL_CROSS].contains("❌")) continue;
            for (String sym : SYMBOLS.get(row.getKey())) {
                if (src.contains(sym)) {
                    problemas.add(row.getKey() + ": a doc diz ausente em riscv64/aarch64, mas `" + sym
                            + "` aparece nos emissores cross — a doc está desatualizada, atualize a célula");
                }
            }
        }
        assertTrue(problemas.isEmpty(), "doc divergente do emissor cross:\n  " + String.join("\n  ", problemas));
    }
}
