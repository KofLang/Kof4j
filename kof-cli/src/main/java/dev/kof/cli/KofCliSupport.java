package dev.kof.cli;

import dev.kof.compiler.KofProjectConfig;
import dev.kof.compiler.Target;
import dev.kof.compiler.TargetMatrix;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Encanamento compartilhado dos subcomandos da CLI (dev.kof.cli):
 * descoberta de fontes, classe main, execução de processo, temp dirs e
 * target parsing. Extraído de Main (REFACTOR-500 Fase 8) — SRP: Main
 * vira só o dispatcher; cada subcomando fica em sua própria classe
 * (CmdBuild/CmdRun/CmdTest/CmdScript/CmdServe).
 *
 * <p>{@code servedProcess} é o estado pré-existente do Main (processo
 * servido, usado no shutdown hook de run/serve) — movido, não criado.</p>
 */
final class KofCliSupport {

    KofCliSupport() {
    }

    static Process servedProcess;

    static void executeProcess(List<String> command, Path tempDir) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.inheritIO();
            Process p = pb.start();
            servedProcess = p;
            int exitCode = p.waitFor();
            if (tempDir != null) cleanup(tempDir);
            System.exit(exitCode);
        } catch (IOException e) {
            System.err.println("failed to execute: " + e.getMessage());
            if (tempDir != null) cleanup(tempDir);
            System.exit(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (tempDir != null) cleanup(tempDir);
            System.exit(1);
        }
    }

    static Target parseTarget(String value) {
        return switch (value) {
            case "jvm" -> Target.JVM;
            case "native" -> Target.NATIVE;
            case "native.risc", "native.riscv64", "native.riscv" -> Target.NATIVE_RISCV64;
            case "native.arm", "native.aarch64", "native.aarch" -> Target.NATIVE_AARCH64;
            case "js", "kofjs" -> Target.JS;
            case "android" -> Target.ANDROID;
            case "script", "kofscript" -> Target.SCRIPT;
            default -> {
                System.err.println("unknown target: " + value);
                System.exit(1);
                yield Target.JVM;
            }
        };
    }

    /**
     * F3 (plataforma): layout de aplicação full-stack (APPLICATION_MODEL P6 —
     * componentes por convenção de diretório): {@code src/} = backend,
     * {@code src/web/} = frontend (módulo Kof compilado p/ KofJS),
     * {@code src/static/} = estáticos crus. Aditivo: sem {@code web/} com .kf,
     * é o monólito de hoje (zero regressão).
     */
    public static record Layout(Path backendDir, Path frontendDir, Path staticDir) {
        public boolean fullStack() { return frontendDir != null; }
    }

    /** Detecta o layout a partir do diretório passado ao build/run (ou da raiz). */
    public static Layout detectLayout(Path dir) {
        Path src = dir;
        if (!Files.isDirectory(dir.resolve("web")) && Files.isDirectory(dir.resolve("src"))) {
            src = dir.resolve("src");
        }
        Path web = src.resolve("web");
        if (Files.isDirectory(web) && !collectShallow(web).isEmpty()) {
            Path st = src.resolve("static");
            return new Layout(src, web, Files.isDirectory(st) ? st : null);
        }
        return new Layout(dir, null, null);
    }

    /** Copia uma árvore de arquivos (estáticos) preservando a estrutura relativa. */
    static int copyTree(Path from, Path to) throws IOException {
        List<Path> files;
        try (var s = Files.walk(from)) {
            files = s.filter(Files::isRegularFile).toList();
        }
        for (Path f : files) {
            Path dst = to.resolve(from.relativize(f).toString());
            Files.createDirectories(dst.getParent());
            Files.copy(f, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return files.size();
    }

    /** Irmãos .kf do MESMO diretório (não-recursivo) — inclusão no módulo do run. */
    static List<Path> collectShallow(Path dir) {
        List<Path> files = new ArrayList<>();
        try (var s = Files.list(dir)) {
            s.filter(p -> p.toString().endsWith(".kf")).forEach(files::add);
        } catch (IOException e) { System.err.println("error: " + e.getMessage()); }
        files.sort(java.util.Comparator.comparing(Path::toString));
        return files;
    }

    /**
     * F2-parte-4 (plataforma): seleciona backend×frontend para build/run/serve.
     * Prioridade: flag {@code --backend/--frontend} > {@code [backend]/[frontend]}
     * do kof.toml > default (null). Valida a combinação via
     * {@link TargetMatrix#validate} ANTES de compilar (R6: erro honesto).
     * Retorna um record ou lança {@link IllegalArgumentException} com a mensagem
     * legível — o caller imprime e sai com 1.
     */
    public static record Targets(Target backend, Target frontend) {}

    public static Targets selectTargets(String backendFlag, String frontendFlag, Path projectRoot) {
        KofProjectConfig cfg = projectRoot != null ? KofProjectConfig.load(projectRoot) : KofProjectConfig.empty();
        List<String> errors = new ArrayList<>();
        for (String w : cfg.warnings()) System.err.println("kof.toml: " + w);
        String backendRaw = backendFlag != null ? backendFlag : cfg.backendTarget();
        String frontendRaw = frontendFlag != null ? frontendFlag : cfg.frontendTarget();
        Target backend = TargetMatrix.parse(backendRaw, errors);
        Target frontend = TargetMatrix.parse(frontendRaw, errors);
        if (!errors.isEmpty()) {
            for (String e : errors) System.err.println("error: " + e);
            throw new IllegalArgumentException(String.join("; ", errors));
        }
        String invalid = TargetMatrix.validate(backend, frontend);
        if (invalid != null) throw new IllegalArgumentException(invalid);
        return new Targets(backend, frontend);
    }

    static List<Path> collect(Path dir) {
        // convenção Go-like: um diretório = UM pacote → não-recursivo
        // (subdirs como tests/ são pacotes independentes)
        List<Path> files = new ArrayList<>();
        try (var s = Files.list(dir)) { s.filter(p -> p.toString().endsWith(".kf")).forEach(files::add); }
        catch (IOException e) { System.err.println("error: " + e.getMessage()); }
        files.sort(java.util.Comparator.comparing(Path::toString));
        return files;
    }

    static void cleanup(Path dir) {
        try (var s = Files.walk(dir)) {
            s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }

    static String javaExecutable() {
        String install = System.getProperty("kof.install.dir", "");
        if (!install.isEmpty()) {
            String exe = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
            Path jdk = Path.of(install, "jdk", "bin", exe);
            if (Files.isExecutable(jdk)) return jdk.toString();
        }
        return "java";
    }

    static String findMainClass(Path dir) {
        try (var s = Files.walk(dir)) {
            List<String> candidates = s.filter(p -> p.toString().endsWith(".class"))
                    .map(p -> dir.relativize(p).toString()
                            .replace(".class", "")
                            .replace("/", ".")
                            .replace("\\", "."))
                    .toList();
            for (String c : candidates) {
                if (c.endsWith(".Main") || c.equals("Main")) return c;
            }
            return candidates.isEmpty() ? null : candidates.get(candidates.size() - 1);
        } catch (IOException e) {
            return null;
        }
    }

    static String findJsEntry(Path dir) {
        Path defaultEntry = dir.resolve("Default.mjs");
        if (Files.exists(defaultEntry)) return defaultEntry.toString();
        try (var s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".mjs"))
                    .filter(p -> !p.toString().contains("kof-runtime"))
                    .findFirst()
                    .map(p -> p.toString())
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }
}
