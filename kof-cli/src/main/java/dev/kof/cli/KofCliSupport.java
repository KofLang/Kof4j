package dev.kof.cli;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Diagnostic;
import dev.kof.compiler.KofProjectConfig;
import dev.kof.compiler.ProjectLocator;
import dev.kof.compiler.Target;
import dev.kof.compiler.TargetMatrix;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    /** Bool JS é numérico (contrato §382); JVM emite true/false. Mesma decisão. */
    static boolean truthy(Object v) {
        if (v instanceof Boolean b) { return b; }
        if (v instanceof Number n) { return n.longValue() != 0; }
        return false;
    }

    static java.io.OutputStream tee(java.io.OutputStream a, java.io.OutputStream b) {
        return new java.io.OutputStream() {
            @Override public void write(int n) throws java.io.IOException { a.write(n); b.write(n); }
            @Override public void write(byte[] buf, int off, int len) throws java.io.IOException {
                a.write(buf, off, len); b.write(buf, off, len);
            }
            @Override public void flush() throws java.io.IOException { a.flush(); b.flush(); }
            @Override public void close() { /* dono real fecha; o tee nao fecha */ }
        };
    }

    KofCliSupport() {
    }

    static Process servedProcess;

    static void executeProcess(List<String> command, Path tempDir) {
        executeProcess(command, tempDir, Map.of());
    }

    /**
     * F3 (plataforma): variante com variáveis de ambiente extras para o
     * processo filho — usada pelo serve full-stack para passar ao backend o
     * caminho do bundle (KOF_WEB_OUT) e dos estáticos (KOF_STATIC_OUT), que
     * o app consome via {@code config.env(...)} + {@code app.serveDir}.
     */
    static void executeProcess(List<String> command, Path tempDir, Map<String, String> extraEnv) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.inheritIO();
            pb.environment().putAll(extraEnv);
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

    /**
     * Diagnóstico para um {@code --target} que não resolve (flag legado).
     * {@code wasm} e cia. são alvos CONHECIDOS que ainda não existem →
     * mesma mensagem do caminho {@code --backend/--frontend} (WASM001,
     * Fase 6), nunca "unknown" genérico (R6). Vazio se o valor é válido.
     */
    static java.util.List<String> unknownTargetMessages(String value) {
        Target t = switch (value) {
            case "jvm", "native", "native.risc", "native.riscv64", "native.riscv",
                 "native.arm", "native.aarch64", "native.aarch", "js", "kofjs",
                 "android", "script", "kofscript" -> Target.JVM;
            default -> null;
        };
        if (t != null) return List.of();
        if (dev.kof.compiler.TargetMatrix.frontendGapFor(value) != null) {
            java.util.List<String> errs = new java.util.ArrayList<>();
            dev.kof.compiler.TargetMatrix.parse(value, errs);
            return errs;
        }
        return List.of("unknown target: " + value);
    }

    static Target parseTarget(String value) {
        Target t = switch (value) {
            case "jvm" -> Target.JVM;
            case "native" -> Target.NATIVE;
            case "native.risc", "native.riscv64", "native.riscv" -> Target.NATIVE_RISCV64;
            case "native.arm", "native.aarch64", "native.aarch" -> Target.NATIVE_AARCH64;
            case "js", "kofjs" -> Target.JS;
            case "android" -> Target.ANDROID;
            case "script", "kofscript" -> Target.SCRIPT;
            default -> {
                for (String e : unknownTargetMessages(value)) System.err.println(e);
                System.exit(1);
                yield Target.JVM; // inalcançável (exit acima)
            }
        };
        return t;
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

    /**
     * F3 (plataforma, APPLICATION_MODEL I2.6): full-stack com backend não-JVM
     * → APP001 honesto (R6: nunca silenciar o frontend). O bundle é montado
     * pelo backend via app.serveDir — só JVM hoje (Native/JS = WEB005 gap).
     * Retorna null se ok; mensagem legível com o código senão.
     */
    public static String app001(Target backend, boolean fullStack) {
        if (!fullStack || backend == null || backend == Target.JVM) return null;
        return "backend '" + TargetMatrix.name(backend) + "' with [frontend]/web/ does not"
                + " run full-stack yet (the bundle is assembled by the JVM backend via app.serveDir;"
                + " Native/JS = WEB005) [APP001]";
    }

    /**
     * F3 (plataforma): compila o componente frontend (web/) para o bundle
     * estático e copia os estáticos (static/) ao lado. Frontend KofJS gera
     * .mjs + index.html em {@code buildRoot/frontend}; estáticos em
     * {@code buildRoot/static}. Usado por build (artefatos) e serve
     * (tempDir) full-stack. Falha de compilação aborta (System.exit 1) —
     * nunca silencioso.
     */
    static void buildFrontend(CompilerDriver driver, Layout layout,
                              Target frontendTarget, Path buildRoot) {
        Path frontendOut = buildRoot.resolve("frontend");
        List<Path> frontendFiles = collect(layout.frontendDir());
        frontendFiles.sort(java.util.Comparator.comparing(p -> p.getFileName().toString()));
        // PKG006 (#71): se o frontend vive num projeto com kof.toml, a raiz do
        // projeto manda — imports cross-directory tipo `import src.Shared`
        // resolvem a partir dela, mesma regra que CmdRun aplica ao módulo de
        // entrada. Sem kof.toml, mantém o comportamento anterior.
        Path frontendRoot = driver.resolveModuleRoot(frontendFiles);
        if (frontendRoot == null) frontendRoot = layout.frontendDir().toAbsolutePath().normalize();
        CompilationResult fe = driver.compileSources(frontendFiles, frontendOut, frontendTarget,
                frontendRoot);
        for (Diagnostic d : fe.diagnostics().getDiagnostics()) System.out.println(d.format());
        if (!fe.success()) System.exit(1);
        if (layout.staticDir() != null) {
            try {
                int n = copyTree(layout.staticDir(), buildRoot.resolve("static"));
                System.out.println(n +  " static file(s) → " + buildRoot.resolve("static"));
            } catch (java.io.IOException e) {
                System.err.println("build: failed to copy static files: " + e.getMessage());
                System.exit(1);
            }
        }
        System.out.println("frontend (" + TargetMatrix.name(frontendTarget) + ") → " + frontendOut);
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

    /** Irmãos .kf/.kof do MESMO diretório (não-recursivo) — inclusão no módulo do run.
     *  GitHub #67: .kof é extensão oficial (editor/kof.tmLanguage.json declara as duas). */
    static List<Path> collectShallow(Path dir) {
        List<Path> files = new ArrayList<>();
        try (var s = Files.list(dir)) {
            s.filter(KofCliSupport::isKofSource).forEach(files::add);
        } catch (IOException e) { System.err.println("error: " + e.getMessage()); }
        files.sort(java.util.Comparator.comparing(Path::toString));
        return files;
    }

    /** O path é um arquivo-fonte Kof (.kf ou .kof)? Único filtro da descoberta. */
    static boolean isKofSource(Path p) {
        String n = p.toString().toLowerCase();
        return n.endsWith(".kf") || n.endsWith(".kof");
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
        try (var s = Files.list(dir)) { s.filter(KofCliSupport::isKofSource).forEach(files::add); }
        catch (IOException e) { System.err.println("error: " + e.getMessage()); }
        files.sort(java.util.Comparator.comparing(Path::toString));
        return files;
    }

    /**
     * #708: descoberta recursiva de uma source root declarada
     * ({@code [sources] app/test} do kof.toml). Cada subdiretório é um
     * diretório-pacote — a raiz dada é a base dos pacotes; portanto a coleta
     * desce (diferente de {@link #collect}, em que um diretório = um pacote).
     * Ordenada por caminho para saída determinística.
     */
    static List<Path> collectRecursive(Path root) {
        List<Path> files = new ArrayList<>();
        try (var s = Files.walk(root)) {
            s.filter(KofCliSupport::isKofSource).forEach(files::add);
        } catch (IOException e) { System.err.println("error: " + e.getMessage()); }
        files.sort(java.util.Comparator.comparing(Path::toString));
        return files;
    }

    /** #708: raiz do projeto (kof.toml em um ancestral), ou null. */
    static Path projectRootOf(Path start) {
        return ProjectLocator.locate(start);
    }

    /** #708: manifesto do projeto, ou vazio quando não há kof.toml. */
    static KofProjectConfig configOf(Path projectRoot) {
        return projectRoot != null ? KofProjectConfig.load(projectRoot) : KofProjectConfig.empty();
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
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
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
