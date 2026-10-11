package dev.kof.compiler;

import dev.kof.compiler.parser.Lexer;
import dev.kof.compiler.parser.Parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Pacote virtual {@code kof.test.browser} (kof-testing-platform-plan §6, a
 * fatia da API de browser no nível Kof; o dono atual lane
 * security/connectors). Escrito EM KOF ({@code dev/kof/testbrowser.kf} no
 * resource) e injetado FLAT no {@code import kof.test.browser} EXPLICITO —
 * mesmo mecanismo de {@code kof.test}/{@code kof.test.db}/
 * {@code kof.test.web} (precedente {@link CompilerTestWeb}).
 *
 * <p>Host SEPARADO de proposito: os helpers dependem da primitiva
 * {@code spawn} (o Chrome CLI {@code --headless} — a semente
 * {@code KofJsBrowserE2ETest}) e do {@code time.sleep}; o host isolado mantem
 * {@code kof.test} puro.
 *
 * <p>O namespace {@code kof.test.browser} nao entra no ledger R1: o
 * {@code check_stdlib_boundary.sh} casa {@code "kof.<ns>"} por um unico
 * componente, entao a linha existente {@code test} cobre a superficie.
 */
final class CompilerTestBrowser {

    static final String HOST_IMPORT = "kof.test.browser";
    static final String FN = "browserContent";

    private CompilerTestBrowser() {}

    static CompilationUnitNode injectHostIfNeeded(CompilerDriver driver,
                                                  CompilationUnitNode unit,
                                                  DiagnosticCollector diagnostics) {
        boolean wantsHost = false;
        for (String imp : unit.imports()) {
            String base = imp.endsWith(".*") ? imp.substring(0, imp.length() - 2) : imp;
            if (HOST_IMPORT.equals(base)) { wantsHost = true; break; }
        }
        if (!wantsHost) return unit;
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
        String resource = "/dev/kof/testbrowser.kf";
        try (var in = CompilerDriver.class.getResourceAsStream(resource)) {
            if (in == null) {
                diagnostics.error("", 0, 0, 0,
                        "test browser host resource " + resource + " missing", "PKG003");
                return null;
            }
            String hostSource = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            DiagnosticCollector silent = new DiagnosticCollector();
            Lexer lexer = new Lexer(hostSource, "testbrowser.kf", silent);
            Parser parser = new Parser(lexer.tokenize(), silent, "testbrowser.kf");
            CompilationUnitNode hostUnit = parser.parse();
            if (silent.hasErrors() || hostUnit == null) {
                for (Diagnostic d : silent.getDiagnostics()) diagnostics.report(d);
                diagnostics.error("", 0, 0, 0, "test browser host did not parse", "PKG003");
                return null;
            }
            return hostUnit;
        } catch (IOException e) {
            diagnostics.error("", 0, 0, 0,
                    "test browser host could not be loaded: " + e.getMessage(), "PKG003");
            return null;
        }
    }
}
