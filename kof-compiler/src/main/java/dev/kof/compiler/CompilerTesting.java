package dev.kof.compiler;

import dev.kof.compiler.parser.Lexer;
import dev.kof.compiler.parser.Parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Pacote virtual {@code kof.test} (D-TESTING-PLATFORM, 28/09/2026;
 * design em {@code D-FUTURE-BATCH-2809B}). Os helpers de asserção
 * ({@code assertTrue}/{@code assertFalse}/{@code assertEqualInt}/
 * {@code assertEqualString}/{@code assertNotEqualInt}/{@code assertNotEqualString}/
 * {@code assertEqualBool}/{@code assertEqualLong}/{@code assertNotEqualLong}/
 * {@code assertEqualDouble}/{@code assertNotEqualDouble}/{@code assertEqualFloat}/
 * {@code assertNotEqualFloat}/{@code assertNull}/{@code assertNotNull}/{@code assertThrows}/
 * {@code fail}) são escritos EM KOF
 * ({@code dev/kof/test.kf} no resource) e injetados FLAT no
 * {@code import kof.test} EXPLÍCITO — mesmo mecanismo de
 * {@code kof.pagination}/{@code kof.pairs}. Aditivo à superfície
 * {@code test}/{@code assert} existente: nenhuma sintaxe nova, nenhum runtime
 * por backend (só {@code throw} de String, já tratado pelos 4 alvos).
 * Playwright/Cypress e o runner de browser ficam para fatias posteriores.
 */
final class CompilerTesting {

    static final String HOST_IMPORT = "kof.test";
    static final String FN = "assertTrue";

    private CompilerTesting() {}

    static CompilationUnitNode injectHostIfNeeded(CompilerDriver driver,
                                                  CompilationUnitNode unit,
                                                  DiagnosticCollector diagnostics) {
        boolean wantsHost = false;
        for (String imp : unit.imports()) {
            String base = imp.endsWith(".*") ? imp.substring(0, imp.length() - 2) : imp;
            if (HOST_IMPORT.equals(base)) { wantsHost = true; break; }
        }
        if (!wantsHost) return unit;
        // usuário definiu o próprio assertTrue: não injeta (nome colidindo é
        // sinal, não silêncio — mesmo critério do supervisor/workflow/interop/pagination).
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
        String resource = "/dev/kof/test.kf";
        try (var in = CompilerDriver.class.getResourceAsStream(resource)) {
            if (in == null) {
                diagnostics.error("", 0, 0, 0,
                        "test host resource " + resource + " missing", "PKG003");
                return null;
            }
            String hostSource = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            DiagnosticCollector silent = new DiagnosticCollector();
            Lexer lexer = new Lexer(hostSource, "test.kf", silent);
            Parser parser = new Parser(lexer.tokenize(), silent, "test.kf");
            CompilationUnitNode hostUnit = parser.parse();
            if (silent.hasErrors() || hostUnit == null) {
                for (Diagnostic d : silent.getDiagnostics()) diagnostics.report(d);
                diagnostics.error("", 0, 0, 0, "test host did not parse", "PKG003");
                return null;
            }
            return hostUnit;
        } catch (IOException e) {
            diagnostics.error("", 0, 0, 0,
                    "test host could not be loaded: " + e.getMessage(), "PKG003");
            return null;
        }
    }
}
