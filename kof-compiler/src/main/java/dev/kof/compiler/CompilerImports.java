package dev.kof.compiler;

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
final class CompilerImports {

    private CompilerImports() {}

    static CompilationUnitNode expandKofImports(CompilationUnitNode unit,
                                            Path moduleRoot,
                                            DiagnosticCollector currentDiagnostics,
                                            java.util.Map<AstNode, String> declarationPackages) {
        java.util.Set<String> visitedDirs = new java.util.HashSet<>();
        List<AstNode> decls = new ArrayList<>(unit.declarations());
        List<String> imports = new ArrayList<>(unit.imports());
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>(imports);
        int rounds = 0;
        while (!queue.isEmpty() && rounds++ < 256) {
            String imp = queue.poll();
            if (imp.endsWith(".*")) {
                imp = imp.substring(0, imp.length() - 2);
            }
            Path pkgDir = moduleRoot != null
                    ? moduleRoot.resolve(imp.replace('.', '/'))
                    : Path.of(imp.replace('.', '/'));
            // Try directory import first (import a.b -> whole package a/b)
            if (Files.isDirectory(pkgDir)) {
                String dirKey = pkgDir.toAbsolutePath().normalize().toString();
                if (!visitedDirs.add(dirKey)) continue;
                try (var stream = Files.walk(pkgDir, 1)) {
                    for (Path kf : stream.filter(p -> p.toString().endsWith(".kf"))
                            .sorted(java.util.Comparator.comparing(p -> p.getFileName().toString()))
                            .toList()) {
                        String code = Files.readString(kf);
                        String fileName = kf.getFileName().toString();
                        DiagnosticCollector silent = new DiagnosticCollector();
                        Parser parser = new Parser(new Lexer(code, fileName, silent).tokenize(),
                                silent, fileName);
                        CompilationUnitNode libUnit = parser.parse();
                        if (silent.hasErrors()) {
                            for (Diagnostic d : silent.getDiagnostics()) currentDiagnostics.report(d);
                            continue;
                        }
                        String expectedPkg = dirKey.equals(moduleRoot.toAbsolutePath().normalize().toString())
                                ? "" : imp;
                        if (!libUnit.packageName().isEmpty()
                                && !libUnit.packageName().equals(expectedPkg)
                                && currentDiagnostics != null) {
                            SourcePosition p0 = libUnit.position();
                            currentDiagnostics.error(kf.toString(), 0, 0, 0,
                                    "package '" + libUnit.packageName()
                                            + "' não corresponde ao diretório do import ('"
                                            + expectedPkg + "')",
                                    "PKG004");
                            continue;
                        }
                        for (String libImp : libUnit.imports()) {
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
                if (Files.isRegularFile(kfFile)) {
                    String pkgKey = kfFile.toAbsolutePath().normalize().toString();
                    if (!visitedDirs.add(pkgKey)) continue;
                    try {
                        String code = Files.readString(kfFile);
                        String fileName = kfFile.getFileName().toString();
                        DiagnosticCollector silent = new DiagnosticCollector();
                        Parser parser = new Parser(new Lexer(code, fileName, silent).tokenize(), silent, fileName);
                        CompilationUnitNode libUnit = parser.parse();
                        if (silent.hasErrors()) {
                            for (Diagnostic d : silent.getDiagnostics()) currentDiagnostics.report(d);
                            continue;
                        }
                        if (!libUnit.packageName().isEmpty()
                                && !libUnit.packageName().equals(pkgPart)
                                && currentDiagnostics != null) {
                            currentDiagnostics.error(kfFile.toString(), 0, 0, 0,
                                    "package '" + libUnit.packageName() + "' não corresponde ao diretório do import ('" + pkgPart + "')",
                                    "PKG004");
                            continue;
                        }
                        for (String libImp : libUnit.imports()) {
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
            // import externo (android.* etc.) — ignora
            continue;
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
                    currentDiagnostics.error("", 0, 0, 0,
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

    static String declarationName(AstNode d) {
        if (d instanceof TypeDeclarationNode t) return t.name();
        if (d instanceof FunctionDeclarationNode f) return f.name();
        return null;
    }
}