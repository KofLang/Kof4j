package dev.kof.compiler;

import dev.kof.compiler.parser.Lexer;
import dev.kof.compiler.parser.Parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Par nominal {@code Pair<A,B>} + helper {@code zipPairs} escritos EM KOF
 * ({@code dev/kof/pairs.kf} no resource), injetados FLAT em TODO programa
 * (D-MULTIPARADIGMA-ZIP, slice 1i, library-first): o lowering reescreve
 * {@code xs.zip(ys)} em {@code zipPairs(xs, ys)} — nenhum runtime por
 * backend, nenhum ABI novo, nenhuma sintaxe nova.
 *
 * <p>Diferente do {@code kof.pagination} (virtual, exige import explícito),
 * a injeção aqui é incondicional: {@code zip} é método de {@code List} como
 * os irmãos (map/filter/distinct...), então precisa funcionar sem import.
 * O custo é uma declaração a mais por programa (sem uso, código morto
 * podado pelo tree-shaking); colisão com definição do usuário desliga a
 * injeção (mesmo critério do supervisor/workflow/interop/pagination).</p>
 */
final class CompilerPairs {

    static final String TYPE = "Pair";
    static final String FN = "zipPairs";

    private CompilerPairs() {}

    static CompilationUnitNode injectHostIfNeeded(CompilerDriver driver,
                                                  CompilationUnitNode unit,
                                                  DiagnosticCollector diagnostics) {
        // usuario definiu o proprio Pair/zipPairs: nao injeta (nome colidindo
        // e sinal, nao silencio — mesmo criterio do supervisor/workflow/
        // interop/pagination). Nesse caso xs.zip segue para SEM025.
        boolean collision = unit.declarations().stream().anyMatch(d ->
                (d instanceof TypeDeclarationNode t && TYPE.equals(t.name()))
                        || (d instanceof FunctionDeclarationNode f && FN.equals(f.name())));
        if (collision) return unit;
        CompilationUnitNode hostUnit = parseHostResource(diagnostics);
        if (hostUnit == null) return null;
        List<AstNode> decls = new ArrayList<>(unit.declarations());
        for (AstNode d : hostUnit.declarations()) {
            driver.declarationPackages.put(d, "");
            decls.add(d);
        }
        return new CompilationUnitNode(unit.position(), unit.packageName(), unit.imports(), decls);
    }

    private static CompilationUnitNode parseHostResource(DiagnosticCollector diagnostics) {
        String resource = "/dev/kof/pairs.kf";
        try (var in = CompilerDriver.class.getResourceAsStream(resource)) {
            if (in == null) {
                diagnostics.error("", 0, 0, 0,
                        "pairs host resource " + resource + " missing", "PKG003");
                return null;
            }
            String hostSource = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            DiagnosticCollector silent = new DiagnosticCollector();
            Lexer lexer = new Lexer(hostSource, "pairs.kf", silent);
            Parser parser = new Parser(lexer.tokenize(), silent, "pairs.kf");
            CompilationUnitNode hostUnit = parser.parse();
            if (silent.hasErrors() || hostUnit == null) {
                for (Diagnostic d : silent.getDiagnostics()) diagnostics.report(d);
                diagnostics.error("", 0, 0, 0, "pairs host did not parse", "PKG003");
                return null;
            }
            return hostUnit;
        } catch (IOException e) {
            diagnostics.error("", 0, 0, 0,
                    "pairs host could not be loaded: " + e.getMessage(), "PKG003");
            return null;
        }
    }
}
