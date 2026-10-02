package dev.kof.compiler;

import dev.kof.compiler.parser.Lexer;
import dev.kof.compiler.parser.Parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Pacote virtual {@code kof.pagination} (D-PAGINATION, 28/09/2026). O valor
 * {@code Window<T>} e as funcoes {@code window(...)} sao escritos EM KOF
 * ({@code dev/kof/pagination.kf} no resource) e injetados FLAT no
 * {@code import kof.pagination} EXPLICITO — mesmo mecanismo de
 * {@code kof.supervisor}/{@code kof.workflow}/{@code kof.interop}. Nenhum
 * runtime por backend: o corpo Kof reaproveita {@code List.slice} (slice P1,
 * ja nos 4 alvos) e apenas constroi o valor de metadados (plano
 * {@code docs/development/pagination-plan.md} §7.2). O core ignora SQL e HTTP;
 * {@code orm.window} e {@code pageRequest} sao faces de plataforma de fatias
 * posteriores (P3/P5), nunca fundacao da linguagem.
 */
final class CompilerPagination {

    static final String HOST_IMPORT = "kof.pagination";
    static final String TYPE = "Window";
    static final String FN = "window";

    private CompilerPagination() {}

    static CompilationUnitNode injectHostIfNeeded(CompilerDriver driver,
                                                  CompilationUnitNode unit,
                                                  DiagnosticCollector diagnostics) {
        boolean wantsHost = false;
        for (String imp : unit.imports()) {
            String base = imp.endsWith(".*") ? imp.substring(0, imp.length() - 2) : imp;
            if (HOST_IMPORT.equals(base)) { wantsHost = true; break; }
        }
        if (!wantsHost) return unit;
        // usuario definiu o proprio Window/window: nao injeta (nome colidindo e
        // sinal, nao silencio — mesmo criterio do supervisor/workflow/interop).
        boolean collision = unit.declarations().stream().anyMatch(d ->
                (d instanceof TypeDeclarationNode t && TYPE.equals(t.name()))
                        || (d instanceof FunctionDeclarationNode f && FN.equals(f.name())));
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
        String resource = "/dev/kof/pagination.kf";
        try (var in = CompilerDriver.class.getResourceAsStream(resource)) {
            if (in == null) {
                diagnostics.error("", 0, 0, 0,
                        "pagination host resource " + resource + " missing", "PKG003");
                return null;
            }
            String hostSource = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            DiagnosticCollector silent = new DiagnosticCollector();
            Lexer lexer = new Lexer(hostSource, "pagination.kf", silent);
            Parser parser = new Parser(lexer.tokenize(), silent, "pagination.kf");
            CompilationUnitNode hostUnit = parser.parse();
            if (silent.hasErrors() || hostUnit == null) {
                for (Diagnostic d : silent.getDiagnostics()) diagnostics.report(d);
                diagnostics.error("", 0, 0, 0, "pagination host did not parse", "PKG003");
                return null;
            }
            return hostUnit;
        } catch (IOException e) {
            diagnostics.error("", 0, 0, 0,
                    "pagination host could not be loaded: " + e.getMessage(), "PKG003");
            return null;
        }
    }
}
