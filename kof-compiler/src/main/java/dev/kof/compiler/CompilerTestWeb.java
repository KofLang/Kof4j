package dev.kof.compiler;

import dev.kof.compiler.parser.Lexer;
import dev.kof.compiler.parser.Parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Pacote virtual {@code kof.test.web} (kof-testing-platform-plan §5, terceira
 * fatia; D-MAINT-BATCH-0610B/B). O helper de ciclo de vida de servidor HTTP
 * {@code withServer(app, port, body)} e escrito EM KOF
 * ({@code dev/kof/testweb.kf} no resource) e injetado FLAT no
 * {@code import kof.test.web} EXPLICITO — mesmo mecanismo de
 * {@code kof.test}/{@code kof.test.db}.
 *
 * <p>Host SEPARADO do {@code kof.test} DE PROPOSITO (precedente
 * {@link CompilerTestDb}): o helper depende de {@code kof.web}
 * ({@code app.listen}/{@code app.close}) e de {@code kof.net}
 * ({@code net.connect} na sonda de prontidao) e da primitiva {@code spawn}.
 * Como o {@code kof.test} e injetado FLAT inteiro, colocar {@code withServer}
 * la faria todo teste de unidade carregar a arvore web; o host isolado mantem
 * {@code kof.test} puro.
 *
 * <p>O namespace {@code kof.test.web} nao entra no ledger R1: o
 * {@code check_stdlib_boundary.sh} casa {@code "kof.<ns>"} por um unico
 * componente, entao a linha existente {@code test} cobre a superficie.
 */
final class CompilerTestWeb {

    static final String HOST_IMPORT = "kof.test.web";
    static final String FN = "withServer";

    private CompilerTestWeb() {}

    static CompilationUnitNode injectHostIfNeeded(CompilerDriver driver,
                                                  CompilationUnitNode unit,
                                                  DiagnosticCollector diagnostics) {
        boolean wantsHost = false;
        for (String imp : unit.imports()) {
            String base = imp.endsWith(".*") ? imp.substring(0, imp.length() - 2) : imp;
            if (HOST_IMPORT.equals(base)) { wantsHost = true; break; }
        }
        if (!wantsHost) return unit;
        // usuario definiu o proprio withServer: nao injeta (nome colidindo e sinal,
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
        String resource = "/dev/kof/testweb.kf";
        try (var in = CompilerDriver.class.getResourceAsStream(resource)) {
            if (in == null) {
                diagnostics.error("", 0, 0, 0,
                        "test web host resource " + resource + " missing", "PKG003");
                return null;
            }
            String hostSource = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            DiagnosticCollector silent = new DiagnosticCollector();
            Lexer lexer = new Lexer(hostSource, "testweb.kf", silent);
            Parser parser = new Parser(lexer.tokenize(), silent, "testweb.kf");
            CompilationUnitNode hostUnit = parser.parse();
            if (silent.hasErrors() || hostUnit == null) {
                for (Diagnostic d : silent.getDiagnostics()) diagnostics.report(d);
                diagnostics.error("", 0, 0, 0, "test web host did not parse", "PKG003");
                return null;
            }
            return hostUnit;
        } catch (IOException e) {
            diagnostics.error("", 0, 0, 0,
                    "test web host could not be loaded: " + e.getMessage(), "PKG003");
            return null;
        }
    }
}
