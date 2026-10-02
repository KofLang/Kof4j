package dev.kof.compiler;

import dev.kof.compiler.parser.Lexer;
import dev.kof.compiler.parser.Parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Pacote virtual {@code kof.web} (D-PAGINATION P5, 29/09/2026). O helper HTTP
 * {@code pageRequest(...)} e o record CORE {@code PageRequest} sao escritos EM
 * KOF ({@code dev/kof/web.kf} no resource) e injetados FLAT no
 * {@code import kof.web} EXPLICITO — mesmo mecanismo de
 * {@code kof.pagination}/{@code kof.supervisor}/{@code kof.workflow}/
 * {@code kof.interop}.
 *
 * <p>Host SEPARADO do {@code kof.pagination} de proposito: {@code pageRequest}
 * le o contexto de request ({@code query(...)}), que NAO existe no Native
 * (WEB001). Injetar no {@code kof.pagination} faria todo programa Native que
 * importa paginacao pagar o gap mesmo sem chamar o helper (regressao); aqui o
 * gap so aparece em quem importa {@code kof.web} e chama {@code pageRequest}.
 * O namespace {@code web} ja esta no ledger R1 ({@code scripts/stdlib_boundary.txt}).
 */
final class CompilerWeb {

    static final String HOST_IMPORT = "kof.web";
    static final String TYPE = "PageRequest";
    static final String FN = "pageRequest";

    private CompilerWeb() {}

    static CompilationUnitNode injectHostIfNeeded(CompilerDriver driver,
                                                  CompilationUnitNode unit,
                                                  DiagnosticCollector diagnostics) {
        boolean wantsHost = false;
        for (String imp : unit.imports()) {
            String base = imp.endsWith(".*") ? imp.substring(0, imp.length() - 2) : imp;
            if (HOST_IMPORT.equals(base)) { wantsHost = true; break; }
        }
        if (!wantsHost) return unit;
        // usuario definiu o proprio PageRequest/pageRequest: nao injeta (nome
        // colidindo e sinal, nao silencio — mesmo criterio dos outros hosts).
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
        String resource = "/dev/kof/web.kf";
        try (var in = CompilerDriver.class.getResourceAsStream(resource)) {
            if (in == null) {
                diagnostics.error("", 0, 0, 0,
                        "web host resource " + resource + " missing", "PKG003");
                return null;
            }
            String hostSource = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            DiagnosticCollector silent = new DiagnosticCollector();
            Lexer lexer = new Lexer(hostSource, "web.kf", silent);
            Parser parser = new Parser(lexer.tokenize(), silent, "web.kf");
            CompilationUnitNode hostUnit = parser.parse();
            if (silent.hasErrors() || hostUnit == null) {
                for (Diagnostic d : silent.getDiagnostics()) diagnostics.report(d);
                diagnostics.error("", 0, 0, 0, "web host did not parse", "PKG003");
                return null;
            }
            return hostUnit;
        } catch (IOException e) {
            diagnostics.error("", 0, 0, 0,
                    "web host could not be loaded: " + e.getMessage(), "PKG003");
            return null;
        }
    }
}
