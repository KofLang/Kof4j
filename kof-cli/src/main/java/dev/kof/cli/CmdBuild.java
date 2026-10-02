package dev.kof.cli;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.Diagnostic;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.KofProjectConfig;
import dev.kof.compiler.Target;
import dev.kof.compiler.TargetMatrix;
import dev.kof.compiler.backend.AndroidProjectWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * kof build — compila um diretório (Go-like: todos os .kf do diretório
 * formam UM módulo) para jvm|native|js|android, com pipeline APK
 * standalone quando --apk. Extraído de Main (REFACTOR-500 Fase 8).
 */
final class CmdBuild {

    private CmdBuild() {
    }

    private static final String USAGE = "usage: kof build <source-dir|file.kf> [--target jvm|native|js|native.risc|native.arm|android] [--profile host|freestanding] [--backend <t>] [--frontend <t>] [--output <dir>] [--release] [--apk] [--aab] [--fat] [--print-sizes] [--classpath <jars>] [--keystore <ks> [--storepass <p>] [--keypass <p>] [--alias <a>]] [--min-sdk <n>] [--target-sdk <n>]";

    static void run(String[] args) {
        if (args.length >= 2
                && ("--help".equals(args[1]) || "-h".equals(args[1]) || "--version".equals(args[1]))) {
            System.out.println(USAGE);
            return;
        }
        // #708: uma source root declarada em [sources] app permite `kof build`
        // sem argumento posicional (projetos com kof.toml). Se o arg imediato
        // já é uma flag, ou não há raiz declarada, o modo é decidido abaixo.
        boolean flagMode = args.length < 2 || args[1].startsWith("-");
        Path src = null;
        KofProjectConfig cfg = KofProjectConfig.empty();
        Path projectRoot = null;
        int argStart = 2;
        boolean declaredRoot = false;
        if (flagMode) {
            // Manifesto descoberto a partir do diretório de trabalho.
            projectRoot = KofCliSupport.projectRootOf(Path.of("."));
            cfg = KofCliSupport.configOf(projectRoot);
            // Sem [sources] app a raiz fica ausente e o erro é decidido DEPOIS
            // de parsear as flags — assim um typo de flag (`--bogus`) é
            // reportado como flag desconhecida (R6), não como "sem raiz".
            src = KofProjectConfig.resolveSourceRoot(projectRoot, cfg.sourceApp(), null);
            argStart = 1;
            declaredRoot = src != null;
        } else {
            src = Path.of(args[1]);
        }
        Target target = Target.JVM;
        Target frontendTarget = null;
        boolean targetFlagged = false;
        String backendFlag = null;
        String frontendFlag = null;
        Path out = Path.of("build/classes");
        boolean outFlagged = false;
        boolean release = false;
        boolean apk = false;
        boolean aab = false;
        boolean fat = false;
        String classpath = null;
        String keystore = null;
        String storepass = null;
        String keypass = null;
        String keyalias = null;
        String minSdkArg = null;
        String targetSdkArg = null;
        String profileArg = null;
        boolean useDeps = false;
        boolean printSizes = false;
        for (int i = argStart; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--target=")) {
                target = KofCliSupport.parseTarget(arg.substring("--target=".length()));
                targetFlagged = true;
            } else if (arg.startsWith("--output=")) {
                out = Path.of(arg.substring("--output=".length()));
                outFlagged = true;
            } else if (arg.equals("--target") && i + 1 < args.length) {
                target = KofCliSupport.parseTarget(args[i + 1]);
                targetFlagged = true;
                i++;
            } else if (arg.startsWith("--backend=")) {
                backendFlag = arg.substring("--backend=".length());
            } else if (arg.equals("--backend") && i + 1 < args.length) {
                backendFlag = args[++i];
            } else if (arg.startsWith("--frontend=")) {
                frontendFlag = arg.substring("--frontend=".length());
            } else if (arg.equals("--frontend") && i + 1 < args.length) {
                frontendFlag = args[++i];
            } else if (arg.equals("--output") && i + 1 < args.length) {
                out = Path.of(args[i + 1]);
                outFlagged = true;
                i++;
            } else if (arg.equals("--release")) {
                release = true;
            } else if (arg.equals("--apk")) {
                apk = true;
            } else if (arg.equals("--aab")) {
                aab = true;
            } else if (arg.equals("--fat")) {
                fat = true;
            } else if (arg.equals("--deps")) {
                useDeps = true;
            } else if (arg.equals("--print-sizes")) {
                printSizes = true;
            } else if (arg.startsWith("--classpath=")) {
                classpath = arg.substring("--classpath=".length());
            } else if (arg.equals("--classpath") && i + 1 < args.length) {
                classpath = args[++i];
            } else if (arg.startsWith("--keystore=")) {
                keystore = arg.substring("--keystore=".length());
            } else if (arg.equals("--keystore") && i + 1 < args.length) {
                keystore = args[++i];
            } else if (arg.startsWith("--storepass=")) {
                storepass = arg.substring("--storepass=".length());
            } else if (arg.equals("--storepass") && i + 1 < args.length) {
                storepass = args[++i];
            } else if (arg.startsWith("--keypass=")) {
                keypass = arg.substring("--keypass=".length());
            } else if (arg.equals("--keypass") && i + 1 < args.length) {
                keypass = args[++i];
            } else if (arg.startsWith("--alias=")) {
                keyalias = arg.substring("--alias=".length());
            } else if (arg.equals("--alias") && i + 1 < args.length) {
                keyalias = args[++i];
            } else if (arg.startsWith("--min-sdk=")) {
                minSdkArg = arg.substring("--min-sdk=".length());
            } else if (arg.equals("--min-sdk") && i + 1 < args.length) {
                minSdkArg = args[++i];
            } else if (arg.startsWith("--target-sdk=")) {
                targetSdkArg = arg.substring("--target-sdk=".length());
            } else if (arg.equals("--target-sdk") && i + 1 < args.length) {
                targetSdkArg = args[++i];
            } else if (arg.startsWith("--profile=")) {
                profileArg = arg.substring("--profile=".length());
            } else if (arg.equals("--profile") && i + 1 < args.length) {
                profileArg = args[++i];
            } else if (arg.equals("--help") || arg.equals("-h")) {
                System.out.println(USAGE);
                return;
            } else if (arg.startsWith("-")) {
                // R6: an unknown flag (or a known flag missing its value) must
                // never be silently ignored — the user/CI would believe it took
                // effect. Same rule already applied by check/fmt/inspect/init.
                System.err.println("build: unknown or incomplete flag: " + arg
                        + " (see 'kof build --help')");
                System.exit(1);
                return;
            } else {
                System.err.println("build: unexpected argument: " + arg
                        + " (see 'kof build --help')");
                System.exit(1);
                return;
            }
        }
        if (src == null) {
            // R6: nenhuma raiz declarada e nenhum posicional — não compilar um
            // projeto inexistente; dizer o que fazer (decidido após as flags
            // para um typo de flag ser reportado como tal).
            System.err.println("build: no source root given and no [sources] app"
                    + " declared in kof.toml (see 'kof build --help')");
            System.exit(1);
            return;
        }
        if (!Files.exists(src)) {
            // R6: a nonexistent source dir exited 0 (same silent-success class);
            // check/test/run already refuse with "not found".
            System.err.println("not found: " + src);
            System.exit(1);
            return;
        }
        // Convenience (Go-like: the directory is the module): a single source
        // file resolves to its containing directory. Documented as
        // `kof build app.kf`, it previously exited 0 with "no .kf/.kof files
        // found" — a silent no-op (R6).
        if (Files.isRegularFile(src)) {
            Path parent = src.toAbsolutePath().normalize().getParent();
            if (parent == null) {
                System.err.println("build: cannot resolve the module directory of " + src);
                System.exit(1);
                return;
            }
            src = parent;
            declaredRoot = false;   // a source root declarada é sempre um diretório
        }
        // F2-parte-4 (plataforma): --backend/--frontend sobrepõem o kof.toml.
        // --target (contrato legado, congelado) tem precedência; sem ele, o
        // backend da flag/kof.toml dirige a compilação. A combinação backend×
        // frontend é validada pela TargetMatrix ANTES de compilar (R6: erro
        // honesto, nunca fallback silencioso).
        if (!targetFlagged) {
            try {
                KofCliSupport.Targets sel = KofCliSupport.selectTargets(backendFlag, frontendFlag, src);
                if (sel.backend() != null) target = sel.backend();
                frontendTarget = sel.frontend();
            } catch (IllegalArgumentException e) {
                System.err.println("build: " + e.getMessage());
                System.exit(1);
                return;
            }
        }
        // D-APP I3 (Q6): --fat só faz sentido no JVM (native/js já saem como
        // artefato único). Honesto e cedo (R6), antes de compilar.
        if (fat && target != Target.JVM) {
            System.err.println("build: --fat only applies to --target jvm ("
                    + TargetMatrix.name(target) + " already produces a single artifact)");
            System.exit(1);
            return;
        }
        // android-only signing/artifact flags on a non-android target: never
        // accepted and silently ignored (R6) — the user/CI would believe the
        // APK/signing happened. Same class as --fat/--aab.
        if (target != Target.ANDROID && (apk || keystore != null
                || storepass != null || keypass != null || keyalias != null)) {
            System.err.println("build: --apk/--keystore/--storepass/--keypass/--alias "
                    + "only apply to --target android (target: "
                    + TargetMatrix.name(target) + ")");
            System.exit(1);
            return;
        }
        // signing flags only act inside the standalone --apk pipeline; without
        // --apk they would be silently dropped (R6).
        if (!apk && (keystore != null || storepass != null
                || keypass != null || keyalias != null)) {
            System.err.println("build: --keystore/--storepass/--keypass/--alias "
                    + "only apply together with --apk");
            System.exit(1);
            return;
        }
        // B-1: --profile host|freestanding (BuildProfileFlag; R6: só native).
        CompilerDriver driver = new CompilerDriver();
        String profileErr = BuildProfileFlag.apply(driver, "build", target, profileArg);
        if (profileErr != null) {
            System.err.println(profileErr);
            System.exit(1);
            return;
        }
        if (release) driver.setDebugInfoEnabled(false);
        // kof-android Fase 4: minSdk/targetSdk por flag explícita (nunca
        // arquivo mágico). Honesto e cedo (R6): as flags só valem para o
        // alvo android e min não pode exceder target.
        int androidMin = AndroidProjectWriter.DEFAULT_MIN_SDK;
        int androidTarget = AndroidProjectWriter.DEFAULT_TARGET_SDK;
        if (minSdkArg != null || targetSdkArg != null) {
            if (target != Target.ANDROID) {
                System.err.println("build: --min-sdk/--target-sdk only apply to --target android");
                System.exit(1);
                return;
            }
            androidMin = parseSdk(minSdkArg, "--min-sdk", AndroidProjectWriter.DEFAULT_MIN_SDK);
            androidTarget = parseSdk(targetSdkArg, "--target-sdk", AndroidProjectWriter.DEFAULT_TARGET_SDK);
            if (androidMin > androidTarget) {
                System.err.println("build: --min-sdk (" + androidMin
                        + ") cannot be greater than --target-sdk (" + androidTarget + ")");
                System.exit(1);
                return;
            }
            driver.setAndroidSdk(androidMin, androidTarget);
        }
        // dependências externas (android.jar etc.) geridas pelo Kof via
        // ExternalClasspath — separadas pelo separador de classpath da plataforma
        // (#441: no Windows o ':' faz parte da unidade `C:\`, splitar por ':'
        // quebrava entradas em fragmentos como "C" → CP002 falso).
        List<Path> externalEntries = new ArrayList<>();
        for (String part : splitClasspathEntries(classpath)) {
            externalEntries.add(Path.of(part));
        }
        if (!externalEntries.isEmpty()) driver.setExternalClasspath(externalEntries);
        // kofdeps: dependências Maven resolvidas no cache ~/.kof/deps
        if (useDeps) {
            try {
                driver.setDependencySourceRoots(DepsSources.roots(Path.of(".")));   // #566 (b)
                String depsCp = Deps.classpath();
                if (!depsCp.isBlank()) {
                    externalEntries = new ArrayList<>();
                    for (String part : depsCp.split(java.util.regex.Pattern.quote(
                            System.getProperty("os.name", "").toLowerCase().contains("win") ? ";" : ":"))) {
                        if (!part.isBlank()) externalEntries.add(Path.of(part));
                    }
                    driver.setExternalClasspath(externalEntries);
                }
            } catch (IOException e) {
                System.err.println("build: failed to read kofdeps: " + e.getMessage());
                return;
            }
        }
        // F3 (plataforma): full-stack = raiz com src/web/ (frontend) — aditivo.
        // Backend → build/backend, frontend → build/frontend (respeitando
        // --output quando presente). Sem web/ com .kf = monólito de hoje
        // (detectLayout devolve a própria src → comportamento inalterado).
        KofCliSupport.Layout layout = KofCliSupport.detectLayout(src);
        Path backendDir = layout.backendDir();
        String app001 = KofCliSupport.app001(target, layout.fullStack());
        if (app001 != null) { System.err.println("build: " + app001); System.exit(1); return; }
        // #708: a raiz declarada ([sources] app) é a base dos pacotes e a
        // coleta é recursiva (subdiretório = pacote); o modo posicional
        // histórico segue "um diretório = um pacote" (não-recursivo).
        List<Path> files = declaredRoot
                ? KofCliSupport.collectRecursive(backendDir)
                : KofCliSupport.collect(backendDir);
        if (files.isEmpty()) {
            // R6 (#708): a directory with no Kof source used to print this and
            // exit 0 — a silent no-op that looked like a successful build. The
            // positional discovery is non-recursive (one directory = one
            // package), so a tree like src/main/kof/exemplo/ yields nothing
            // here; fail explicitly instead, and point at the expected layout.
            System.err.println("build: no .kf/.kof files found in " + backendDir
                    + (declaredRoot
                            ? " ([sources] app from kof.toml; discovery is recursive)"
                            : " (discovery is one directory = one package; run from the"
                                    + " directory that holds the sources)"));
            System.exit(1);
            return;
        }
        if (declaredRoot) files.sort(java.util.Comparator.comparing(Path::toString));
        else files.sort(java.util.Comparator.comparing(p -> p.getFileName().toString()));
        // D-DB-ZERODRIVER (a): mesmos drivers auto no build JVM (cp de
        // compilação + embed no --fat via externalEntries, que o
        // buildFatJar empacota). Native/JS não embarcam driver (R7).
        if (target == Target.JVM) {
            try {
                String autoDbCp = DbDrivers.provision(Path.of("."), files);
                if (!autoDbCp.isBlank()) {
                    for (String part : autoDbCp.split(java.util.regex.Pattern.quote(
                            System.getProperty("os.name", "").toLowerCase().contains("win") ? ";" : ":"))) {
                        if (!part.isBlank()) externalEntries.add(Path.of(part));
                    }
                    driver.setExternalClasspath(externalEntries);
                }
            } catch (IOException e) {
                System.err.println("build: cannot provision db driver: " + e.getMessage());
                return;
            }
        }
        Path backendOut = out;
        if (layout.fullStack()) {
            Path buildRoot = outFlagged ? out : Path.of("build");
            backendOut = buildRoot.resolve("backend");
        }
        // convenção Go-like: TODOS os .kf do diretório formam UM módulo
        // (raiz = diretório do backend; imports de pacotes resolvem daí)
        CompilationResult module = driver.compileSources(files, backendOut, target,
                backendDir.toAbsolutePath().normalize());
        for (Diagnostic d : module.diagnostics().getDiagnostics()) System.out.println(d.format());
        if (!module.success()) System.exit(1);
        if (printSizes) printSizes(target, backendOut);
        // D-APP I3 (Q6): fat jar opcional — classes do app + runtime +
        // dependências num único .jar executável (java -jar). Default (sem a
        // flag) permanece classpath explícito, como hoje.
        if (fat) {
            try {
                Path jar = buildFatJar(backendOut, externalEntries);
                System.out.println("fat jar → " + jar);
            } catch (IOException e) {
                System.err.println("build: failed to generate fat jar: " + e.getMessage());
                System.exit(1);
                return;
            }
        }
        if (layout.fullStack()) {
            if (frontendTarget == null) frontendTarget = Target.JS;
            KofCliSupport.buildFrontend(driver, layout, frontendTarget, outFlagged ? out : Path.of("build"));
            System.out.println("backend (" + TargetMatrix.name(target) + ") → " + backendOut);
        }
        // target android + --apk: pipeline direto (sem Maven) usando o SDK
        // (full-stack: as classes do backend saíram em backendOut)
        boolean apkOk = true;
        if (target == Target.ANDROID && apk) {
            apkOk = ApkToolchain.runApkPipeline(backendOut, androidMin, androidTarget,
                    keystore, storepass, keypass, keyalias);
        }
        // --aab (App Bundle p/ Play): ainda NÃO produzido — precisa do
        // bundletool (fora do build-tools). Honesto e explícito (R6): nunca
        // ignorar a flag em silêncio e devolver um APK como se fosse AAB.
        if (aab) {
            if (target != Target.ANDROID) {
                System.err.println("build: --aab only applies to --target android");
                System.exit(1);
                return;
            }
            System.err.println("build: --aab: App Bundle (AAB) is not generated yet —"
                    + " requires bundletool (outside build-tools). The project was generated;"
                    + " use --apk or bundletool manually"
                    + " (docs/targets/KOFANDROID.md)");
            System.exit(1);
        }
        // --apk pedido mas o SDK nao permitiu gerar o artefato: a flag nao pode
        // "passar" em silencio (exit 0 sem APK) — o script do usuario checaria $?
        // e acreditaria em sucesso (R6). O projeto foi gerado; o exit e honesto.
        if (!apkOk) {
            System.exit(1);
        }
    }

    /** Lê um valor de SDK (`--min-sdk`/`--target-sdk`); default = `dflt`. */
    private static int parseSdk(String value, String flag, int dflt) {
        if (value == null) return dflt;
        int n;
        try {
            n = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            n = -1;
        }
        if (n < 1 || n > 99) {
            System.err.println("build: " + flag + " invalid: '" + value
                    + "' (expected integer 1..99)");
            System.exit(1);
        }
        return n;
    }

    /**
     * D-APP I3: empacota as classes compiladas + o runtime {@code dev.kof.runtime}
     * (que vive dentro de {@code classesDir}) + as dependências externas num
     * único {@code kof-app.jar} com {@code Main-Class} no manifesto. Entradas
     * do app têm precedência sobre as de dependências (first-wins) e arquivos
     * de assinatura de jars deps são descartados (não fazem sentido num fat
     * jar). Retorna o caminho do jar gerado.
     *
     * <p>#565: o output nunca é input. O jar é montado num staging FORA de
     * {@code classesDir} (senão o {@code Files.walk} o lia ainda incompleto e o
     * embutia truncado), o path final exato é excluído da varredura (um
     * {@code kof-app.jar} de build anterior também não vira input) e só é
     * substituído depois de fechado; em falha o staging é apagado e o jar
     * anterior fica intacto.</p>
     */
    static Path buildFatJar(Path classesDir, List<Path> deps) throws IOException {
        String mainClass = KofCliSupport.findMainClass(classesDir);
        if (mainClass == null) throw new IOException("no main class found em " + classesDir);
        Path jar = classesDir.resolve("kof-app.jar");
        Path finalJar = jar.toAbsolutePath().normalize();
        Path stagingDir = classesDir.toAbsolutePath().normalize().getParent();
        if (stagingDir == null) throw new IOException("cannot stage fat jar outside " + classesDir);
        Path tempJar = Files.createTempFile(stagingDir, ".kof-app-", ".jar");
        java.util.jar.Manifest manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().put(java.util.jar.Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(java.util.jar.Attributes.Name.MAIN_CLASS, mainClass);
        java.util.Set<String> seen = new java.util.HashSet<>();
        boolean moved = false;
        try {
            try (java.util.jar.JarOutputStream jos =
                         new java.util.jar.JarOutputStream(Files.newOutputStream(tempJar), manifest)) {
                addClassesToJar(jos, classesDir, classesDir, seen, finalJar);
                for (Path dep : deps) {
                    if (Files.isDirectory(dep)) {
                        addClassesToJar(jos, dep, dep, seen, finalJar);
                    } else if (dep.toString().endsWith(".jar") && Files.isRegularFile(dep)) {
                        addJarEntriesToJar(jos, dep, seen);
                    }
                }
            }
            Files.move(tempJar, finalJar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            moved = true;
        } finally {
            if (!moved) Files.deleteIfExists(tempJar);
        }
        return jar;
    }

    private static void addClassesToJar(java.util.jar.JarOutputStream jos, Path root, Path dir,
                                        java.util.Set<String> seen, Path excludedOutput)
            throws IOException {
        try (var s = Files.walk(dir)) {
            for (Path p : s.filter(Files::isRegularFile).sorted().toList()) {
                if (p.toAbsolutePath().normalize().equals(excludedOutput)) continue;
                String name = root.relativize(p).toString().replace(java.io.File.separatorChar, '/');
                if (skipJarEntry(name) || !seen.add(name)) continue;
                java.util.jar.JarEntry e = new java.util.jar.JarEntry(name);
                e.setTime(Files.getLastModifiedTime(p).toMillis());
                jos.putNextEntry(e);
                Files.copy(p, jos);
                jos.closeEntry();
            }
        }
    }

    private static void addJarEntriesToJar(java.util.jar.JarOutputStream jos, Path dep,
                                           java.util.Set<String> seen) throws IOException {
        try (var zip = new java.util.zip.ZipFile(dep.toFile())) {
            for (var entries = zip.entries(); entries.hasMoreElements(); ) {
                var entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (skipJarEntry(name) || !seen.add(name)) continue;
                java.util.jar.JarEntry e = new java.util.jar.JarEntry(name);
                e.setTime(entry.getTime());
                jos.putNextEntry(e);
                try (var in = zip.getInputStream(entry)) {
                    in.transferTo(jos);
                }
                jos.closeEntry();
            }
        }
    }

    private static boolean skipJarEntry(String name) {
        if (name.equals("META-INF/MANIFEST.MF") || name.startsWith("META-INF/versions/")) return true;
        if (!name.startsWith("META-INF/")) return false;
        return name.endsWith(".SF") || name.endsWith(".RSA") || name.endsWith(".DSA")
                || name.endsWith(".EC");
    }

    /** Issue #97 / T0: imprime o tamanho medido do artefato (bytes por seção
     *  + contagem de símbolos `kof_*` definidos) em JSON. Parser ELF64 puro
     *  (sem `nm`/`readelf` — host-dependentes); JS soma os .mjs. Aditivo: só
     *  roda com --print-sizes, nunca muda o build de hoje. */
    private static void printSizes(Target target, Path out) {
        try {
            if (target.isNative()) {
                Path bin = out.resolve("Default/Main");
                if (!Files.exists(bin)) {
                    try (var s = Files.walk(out)) {
                        bin = s.filter(p -> p.getFileName().toString().equals("Main")
                                && Files.isExecutable(p)).findFirst().orElse(null);
                    }
                }
                if (bin == null || !Files.exists(bin)) { System.err.println("print-sizes: native binary not found in " + out); return; }
                System.out.println("# " + target + " " + bin);
                System.out.println(dev.kof.compiler.ArtifactSize.toJson(
                        dev.kof.compiler.ArtifactSize.elf(bin)));
            } else if (target == Target.JS) {
                System.out.println("{\"jsBytes\":" + dev.kof.compiler.ArtifactSize.jsBytes(out) + "}");
            } else {
                System.out.println("# print-sizes: " + target + " is lazy/on-demand — no single artifact (separate JVM classes)");
            }
        } catch (IOException e) {
            System.err.println("print-sizes: " + e.getMessage());
        }
    }

    /**
     * #441: divide um {@code --classpath} em entradas. Usa o separador real da
     * plataforma ({@link java.io.File#pathSeparatorChar}): {@code ';'} no
     * Windows (nunca parte um {@code C:\...} no dois-pontos), {@code ':'} fora
     * dele. No Unix mantém a tolerância histórica a {@code ';'} também (zero
     * regressão para quem já passava os dois). Visível p/ teste com separador
     * injetado (o host do teste é Linux; a semântica Windows é a que se prova).
     */
    static java.util.List<String> splitClasspathEntries(String classpath) {
        return splitClasspathEntries(classpath, java.io.File.pathSeparatorChar);
    }

    static java.util.List<String> splitClasspathEntries(String classpath, char separator) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (classpath == null || classpath.isBlank()) return out;
        for (String part : classpath.split(separator == ':' ? "[:;]" : String.valueOf(separator))) {
            if (!part.isBlank()) out.add(part);
        }
        return out;
    }

}
