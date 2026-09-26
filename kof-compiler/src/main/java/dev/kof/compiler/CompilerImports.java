package dev.kof.compiler;
import dev.kof.compiler.parser.Lexer;
import dev.kof.compiler.parser.Parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Expansão de imports de pacotes Kof (código Kof em outras pastas):
 * resolução transitiva relativa à raiz do módulo. Puro — recebe o
 * estado (moduleRoot/currentDiagnostics/declarationPackages) por parâmetro.
 */
public final class CompilerImports {

    private CompilerImports() {}

    static CompilationUnitNode expandKofImports(CompilationUnitNode unit,
                                            Path moduleRoot,
                                            DiagnosticCollector currentDiagnostics,
                                            java.util.Map<AstNode, String> declarationPackages) {
        return expandKofImports(unit, moduleRoot, currentDiagnostics, declarationPackages, null);
    }

    static CompilationUnitNode expandKofImports(CompilationUnitNode unit,
                                            Path moduleRoot,
                                            DiagnosticCollector currentDiagnostics,
                                            java.util.Map<AstNode, String> declarationPackages,
                                            ExternalClasspath externalClasspath) {
        return expandKofImports(unit, moduleRoot, currentDiagnostics, declarationPackages,
                externalClasspath, List.of());
    }

    static CompilationUnitNode expandKofImports(CompilationUnitNode unit,
                                            Path moduleRoot,
                                            DiagnosticCollector currentDiagnostics,
                                            java.util.Map<AstNode, String> declarationPackages,
                                            ExternalClasspath externalClasspath,
                                            List<Path> dependencySourceRoots) {
        return expandKofImports(unit, moduleRoot, currentDiagnostics, declarationPackages,
                externalClasspath, dependencySourceRoots, List.of());
    }

    static CompilationUnitNode expandKofImports(CompilationUnitNode unit,
                                            Path moduleRoot,
                                            DiagnosticCollector currentDiagnostics,
                                            java.util.Map<AstNode, String> declarationPackages,
                                            ExternalClasspath externalClasspath,
                                            List<Path> dependencySourceRoots,
                                            List<Path> compiledSources) {
        java.util.Set<String> visitedDirs = new java.util.HashSet<>();
        // #628 (root cause): a file already compiled in `sources` must NEVER be
        // re-parsed through an import — the import copy loses the package
        // (a class constant `Rotulo` instead of `dominio/Rotulo` -> the JVM
        // aborts with NoClassDefFoundError at load) and re-registers top-level
        // functions (SEM047/SEM057). Seed the per-path dedup with them.
        for (Path compiled : compiledSources) {
            visitedDirs.add(compiled.toAbsolutePath().normalize().toString());
        }
        // Raízes de biblioteca depois do módulo local: stdlib oficial PRIMEIRO, depois as fontes
        // de dependências (#566 opção b) — uma dependência nunca sombreia a stdlib.
        List<Path> libraryRoots = new ArrayList<>();
        Path officialLibraryRoot = officialLibraryRoot();
        if (officialLibraryRoot != null) libraryRoots.add(officialLibraryRoot);
        if (dependencySourceRoots != null) {
            for (Path r : dependencySourceRoots) {
                Path root = r.toAbsolutePath().normalize();
                if (Files.isDirectory(root) && !libraryRoots.contains(root)) libraryRoots.add(root);
            }
        }
        // Fase 1 (PKG007): grafo import → imports do arquivo (fechado após
        // a expansão; ciclos detectados globalmente ao fim do loop).
        java.util.Map<String, java.util.Set<String>> graph = new java.util.HashMap<>();
        List<AstNode> decls = new ArrayList<>(unit.declarations());
        List<String> imports = new ArrayList<>(unit.imports());
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>(imports);
        int rounds = 0;
        while (!queue.isEmpty() && rounds++ < 256) {
            String imp = queue.poll();
            boolean wildcard = imp.endsWith(".*");
            if (wildcard) {
                imp = imp.substring(0, imp.length() - 2);
            }
            Path resolutionRoot = moduleRoot;
            Path pkgDir = moduleRoot != null
                    ? moduleRoot.resolve(imp.replace('.', '/'))
                    : Path.of(imp.replace('.', '/'));
            if (!Files.isDirectory(pkgDir)) {
                for (Path libRoot : libraryRoots) {
                    Path libPackage = libRoot.resolve(imp.replace('.', '/'));
                    if (Files.isDirectory(libPackage)) {
                        pkgDir = libPackage;
                        resolutionRoot = libRoot;
                        break;
                    }
                }
            }
            // Try directory import first (import a.b -> whole package a/b)
            if (Files.isDirectory(pkgDir)) {
                String dirKey = pkgDir.toAbsolutePath().normalize().toString();
                if (!visitedDirs.add(dirKey)) {
                    continue;
                }
                graph.computeIfAbsent(imp, k -> new java.util.HashSet<>());
                try (var stream = Files.walk(pkgDir, 1)) {
                    for (Path kf : stream.filter(p -> p.toString().endsWith(".kf"))
                            .sorted(java.util.Comparator.comparing(p -> p.getFileName().toString()))
                            .toList()) {
                        if (!visitedDirs.add(kf.toAbsolutePath().normalize().toString())) {
                            continue; // #628: file already compiled or already imported
                        }
                        String code = Files.readString(kf);
                        String fileName = kf.getFileName().toString();
                        DiagnosticCollector silent = new DiagnosticCollector();
                        Parser parser = new Parser(new Lexer(code, fileName, silent).tokenize(),
                                silent, fileName);
                        CompilationUnitNode libUnit = parser.parse();
                        if (silent.hasErrors()) {
                            // §CodeQL deref-null: same null-tolerance of the
                            // PKG003/PKG004 branches below (lines 74/95/126).
                            if (currentDiagnostics != null) {
                                for (Diagnostic d : silent.getDiagnostics()) currentDiagnostics.report(d);
                            }
                            continue;
                        }
                        String expectedPkg = (resolutionRoot != null
                                && resolutionRoot.equals(moduleRoot)
                                && dirKey.equals(resolutionRoot.toAbsolutePath().normalize().toString()))
                                ? "" : imp;
                        if (!libUnit.packageName().isEmpty()
                                && !libUnit.packageName().equals(expectedPkg)
                                && currentDiagnostics != null) {
                            currentDiagnostics.error(kf.toString(), 0, 0, 0,
                                    "package '" + libUnit.packageName()
                                            + "' does not match the import directory ('"
                                            + expectedPkg + "')",
                                    "PKG004");
                            continue;
                        }
                        for (String libImp : libUnit.imports()) {
                            String impKey = libImp.endsWith(".*")
                                    ? libImp.substring(0, libImp.length() - 2) : libImp;
                            graph.computeIfAbsent(imp, k -> new java.util.HashSet<>()).add(impKey);
                            if (!imports.contains(libImp)) { imports.add(libImp); queue.add(libImp); }
                        }
                        for (AstNode d : libUnit.declarations()) {
                            declarationPackages.put(d, libUnit.packageName());
                            decls.add(d);
                        }
                    }
                } catch (IOException e) {
                    if (currentDiagnostics != null) {
                        currentDiagnostics.error("", 0, 0, 0,
                                "import '" + imp + "' could not be read: " + e.getMessage(), "PKG003");
                    }
                }
                continue;
            }
            // File import (import a.b.C -> single file a/b/C.kf)
            int lastDot = imp.lastIndexOf('.');
            if (lastDot > 0) {
                String pkgPart = imp.substring(0, lastDot);
                String filePart = imp.substring(lastDot + 1);
                Path pkgPath = moduleRoot != null ? moduleRoot.resolve(pkgPart.replace('.', '/')) : Path.of(pkgPart.replace('.', '/'));
                Path kfFile = pkgPath.resolve(filePart + ".kf");
                if (!Files.isRegularFile(kfFile)) {
                    for (Path libRoot : libraryRoots) {
                        Path candidate = libRoot.resolve(pkgPart.replace('.', '/'))
                                .resolve(filePart + ".kf");
                        if (Files.isRegularFile(candidate)) { kfFile = candidate; break; }
                    }
                }
                if (Files.isRegularFile(kfFile)) {
                    String pkgKey = kfFile.toAbsolutePath().normalize().toString();
                    if (!visitedDirs.add(pkgKey)) {
                        continue;
                    }
                    try {
                        String code = Files.readString(kfFile);
                        String fileName = kfFile.getFileName().toString();
                        DiagnosticCollector silent = new DiagnosticCollector();
                        Parser parser = new Parser(new Lexer(code, fileName, silent).tokenize(), silent, fileName);
                        CompilationUnitNode libUnit = parser.parse();
                        if (silent.hasErrors()) {
                            if (currentDiagnostics != null) {
                                for (Diagnostic d : silent.getDiagnostics()) currentDiagnostics.report(d);
                            }
                            continue;
                        }
                        if (!libUnit.packageName().isEmpty()
                                && !libUnit.packageName().equals(pkgPart)
                                && currentDiagnostics != null) {
                            currentDiagnostics.error(kfFile.toString(), 0, 0, 0,
                                    "package '" + libUnit.packageName() + "' does not match the import directory ('" + pkgPart + "')",
                                    "PKG004");
                            continue;
                        }
                        for (String libImp : libUnit.imports()) {
                            String impKey = libImp.endsWith(".*")
                                    ? libImp.substring(0, libImp.length() - 2) : libImp;
                            graph.computeIfAbsent(imp, k -> new java.util.HashSet<>()).add(impKey);
                            if (!imports.contains(libImp)) { imports.add(libImp); queue.add(libImp); }
                        }
                        for (AstNode d : libUnit.declarations()) {
                            declarationPackages.put(d, libUnit.packageName());
                            decls.add(d);
                        }
                    } catch (IOException e) {
                        if (currentDiagnostics != null) {
                            currentDiagnostics.error("", 0, 0, 0,
                                    "import '" + imp + "' could not be read: " + e.getMessage(), "PKG003");
                        }
                    }
                    continue;
                }
            }
            // Fase 1 (plataforma): import que não é externo (std/interop)
            // e não resolveu nem em diretório nem em arquivo → PKG006
            // (antes era silencioso e o erro só aparecia como SEM011).
            // §134: EXTERNO de verdade inclui o que está nos entries do
            // ExternalClasspath (--classpath/--deps: qualquer pacote — gson,
            // postgres, lib interna). A whitelist de prefixos abaixo só cobre
            // os namespaces estáticos; sem consultar os jars carregados, um
            // import legítimo de dependência virava PKG006.
            if (!isExternalImport(imp)
                    && (externalClasspath == null
                        || !externalClasspath.knowsImport(imp, wildcard))
                    && currentDiagnostics != null) {
                currentDiagnostics.error("", 0, 0, 0,
                        "import '" + imp + "' not found in the module"
                                + " (expected " + imp.replace('.', '/') + "/ or "
                                + imp.replace('.', '/') + ".kf under the root"
                                + " or the official libraries)",
                        "PKG006");
            }
            // import externo (android.* etc.) — ignora
            continue;
        }
        // Fase 1 (PKG007): AUTO-IMPORT direto é diagnóstico (arquivo que
        // importa a si mesmo). IMPORT MÚTUO entre pacotes (a.X ↔ b.Y) é
        // LEGÍTIMO no modelo de unidade mergeada — provado por
        // PackagesE2ETest.moduleRootDerivedFromCommonAncestor (congelado);
        // ciclos gerais NÃO são erro aqui.
        if (currentDiagnostics != null) {
            for (var e : graph.entrySet()) {
                if (e.getValue().contains(e.getKey())) {
                    currentDiagnostics.error("", 0, 0, 0,
                            "import auto-referente: '" + e.getKey()
                                    + "' importa a si mesmo",
                            "PKG007");
                }
            }
        }
        java.util.Map<String, String> seen = new java.util.HashMap<>();
        java.util.Map<String, String> seenFile = new java.util.HashMap<>();
        for (AstNode d : decls) {
            String n = declarationName(d);
            if (n == null) continue;
            String pkg = declarationPackages.getOrDefault(d, unit.packageName());
            String prev = seen.get(n);
            String file = d.position() != null ? d.position().file() : "";
            if (prev != null && prev.equals(pkg)
                    && !java.util.Objects.equals(seenFile.get(n), file)) {
                // Mesmo nome simples no MESMO pacote vindo de ARQUIVOS
                // diferentes é colisão real. A mesma declaração re-adicionada
                // via import transitivo (fonte explícita + import) não é.
                if (currentDiagnostics != null) {
                    currentDiagnostics.error(d,
                            "duplicate type name '" + n + "' in package '" + pkg + "'",
                            "PKG005");
                }
            }
            seen.putIfAbsent(n, pkg);
            seenFile.putIfAbsent(n, file);
        }
        return new CompilationUnitNode(unit.position(), unit.packageName(),
                imports, decls);
    }

    private static Path officialLibraryRoot() {
        String installDir = System.getProperty("kof.install.dir", "").trim();
        if (installDir.isEmpty()) return null;

        Path root = Path.of(installDir).resolve("lib").resolve("kof-libs")
                .toAbsolutePath().normalize();
        return Files.isDirectory(root) ? root : null;
    }

    static String declarationName(AstNode d) {
        if (d instanceof TypeDeclarationNode t) return t.name();
        if (d instanceof FunctionDeclarationNode f) return f.name();
        return null;
    }

    /**
     * Fase 1 (PKG007): true se {@code from} alcança {@code to} no grafo de
     * imports (DFS com limite). Usado para detectar A→B→A quando um import
     * já visitado volta à fila.
     */
    static boolean reaches(java.util.Map<String, java.util.Set<String>> graph,
                           String from, String to) {
        return reachesDfs(graph, from, to, new java.util.HashSet<>(), 0);
    }

    private static boolean reachesDfs(java.util.Map<String, java.util.Set<String>> graph,
                                      String current, String to,
                                      java.util.Set<String> seen, int depth) {
        if (depth > 256 || seen.contains(current)) return false;
        seen.add(current);
        java.util.Set<String> next = graph.get(current);
        if (next == null) return false;
        if (next.contains(to)) return true;
        for (String n : next) {
            if (reachesDfs(graph, n, to, seen, depth + 1)) return true;
        }
        return false;
    }

    /**
     * Fase 1 (PKG006): namespaces que NÃO se resolvem no filesystem e são
     * legítimos — stdlib (kof.*), interop (android.*, java.*, jakarta.*,
     * androidx.*), wildcard de pacote externo (java.awt.*). Qualquer outra
     * coisa que não resolva em arquivo/dir sob a raiz é erro de import.
     */
    static boolean isExternalImport(String imp) {
        if (imp.startsWith("kof.")) return true;
        return imp.startsWith("android.") || imp.startsWith("androidx.")
                || imp.startsWith("java.") || imp.startsWith("jakarta.")
                || imp.startsWith("javax.") || imp.startsWith("kotlin.")
                || imp.startsWith("scala.");
    }
}
