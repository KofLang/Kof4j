package dev.kof.compiler;

import dev.kof.compiler.parser.Lexer;
import dev.kof.compiler.parser.Parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Pacote virtual {@code kof.test.db} (kof-testing-platform-plan §5, segunda
 * fatia; D-MAINT-BATCH-0610B/B). O helper de ciclo de vida de conexao de banco
 * {@code withDb(url, body)} e escrito EM KOF ({@code dev/kof/testdb.kf} no
 * resource) e injetado FLAT no {@code import kof.test.db} EXPLICITO — mesmo
 * mecanismo de {@code kof.test}/{@code kof.web}/{@code kof.pagination}.
 *
 * <p>Host SEPARADO do {@code kof.test} DE PROPOSITO (precedente
 * {@link CompilerWeb} vs {@code kof.pagination}): o helper chama
 * {@code db.connect}/{@code db.close}, e o alvo Native liga libsqlite3 POR USO
 * — {@link dev.kof.compiler.nat.NativeCrossLink#needsSqlite} varre o asm podado
 * por {@code call sqlite3_*}. Como o {@code kof.test} e injetado FLAT inteiro,
 * um helper de banco morando la faria TODO programa que importa
 * {@code kof.test} (mesmo sem banco) tentar {@code -lsqlite3} no cross
 * (medido: {@code riscv64-linux-gnu-ld: nao foi possivel localizar -lsqlite3}).
 * Aqui o gap so aparece em quem importa {@code kof.test.db} e chama
 * {@code withDb}.
 *
 * <p>O namespace {@code kof.test.db} nao entra no ledger R1: o
 * {@code check_stdlib_boundary.sh} casa {@code "kof.<ns>"} por um unico
 * componente, entao a linha existente {@code test} cobre a superficie.
 */
final class CompilerTestDb {

    static final String HOST_IMPORT = "kof.test.db";
    static final String FN = "withDb";

    private CompilerTestDb() {}

    static CompilationUnitNode injectHostIfNeeded(CompilerDriver driver,
                                                  CompilationUnitNode unit,
                                                  DiagnosticCollector diagnostics) {
        boolean wantsHost = false;
        for (String imp : unit.imports()) {
            String base = imp.endsWith(".*") ? imp.substring(0, imp.length() - 2) : imp;
            if (HOST_IMPORT.equals(base)) { wantsHost = true; break; }
        }
        if (!wantsHost) return unit;
        // usuario definiu o proprio withDb: nao injeta (nome colidindo e sinal,
        // nao silencio — mesmo criterio dos outros hosts).
        boolean collision = unit.declarations().stream().anyMatch(d ->
                d instanceof FunctionDeclarationNode f && FN.equals(f.name()));
        if (collision) return unit;
        CompilationUnitNode hostUnit = parseHostResource(diagnostics);
        if (hostUnit == null) return null;
        List<String> imports = new ArrayList<>();
        for (String imp : unit.imports()) {
            String base = imp.endsWith(".*") ? imp.substring(0, imp.length() - 2) : imp;
            if (HOST_IMPORT.equals(base)) continue; // virtual — resolvido aqui
            if (!imports.contains(imp)) imports.add(imp);
        }
        List<AstNode> decls = new ArrayList<>(unit.declarations());
        for (AstNode d : hostUnit.declarations()) {
            driver.declarationPackages.put(d, "");
            decls.add(d);
        }
        return new CompilationUnitNode(unit.position(), unit.packageName(), imports, decls);
    }

    private static CompilationUnitNode parseHostResource(DiagnosticCollector diagnostics) {
        String resource = "/dev/kof/testdb.kf";
        try (var in = CompilerDriver.class.getResourceAsStream(resource)) {
            if (in == null) {
                diagnostics.error("", 0, 0, 0,
                        "test db host resource " + resource + " missing", "PKG003");
                return null;
            }
            String hostSource = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            DiagnosticCollector silent = new DiagnosticCollector();
            Lexer lexer = new Lexer(hostSource, "testdb.kf", silent);
            Parser parser = new Parser(lexer.tokenize(), silent, "testdb.kf");
            CompilationUnitNode hostUnit = parser.parse();
            if (silent.hasErrors() || hostUnit == null) {
                for (Diagnostic d : silent.getDiagnostics()) diagnostics.report(d);
                diagnostics.error("", 0, 0, 0, "test db host did not parse", "PKG003");
                return null;
            }
            return hostUnit;
        } catch (IOException e) {
            diagnostics.error("", 0, 0, 0,
                    "test db host could not be loaded: " + e.getMessage(), "PKG003");
            return null;
        }
    }
}
