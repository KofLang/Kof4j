package dev.kof.cli;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.Diagnostic;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Target;
import dev.kof.compiler.TargetMatrix;
import dev.kof.compiler.backend.AndroidProjectWriter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * kof build — compila um diretório (Go-like: todos os .kf do diretório
 * formam UM módulo) para jvm|native|js|android, com pipeline APK
 * standalone quando --apk. Extraído de Main (REFACTOR-500 Fase 8).
 */
final class CmdBuild {

    private CmdBuild() {
    }

    private static final String USAGE = "usage: kof build <source-dir|file.kf> [--target jvm|native|js|native.risc|native.arm|android] [--backend <t>] [--frontend <t>] [--output <dir>] [--release] [--apk] [--aab] [--fat] [--print-sizes] [--classpath <jars>] [--keystore <ks> [--storepass <p>] [--keypass <p>] [--alias <a>]] [--min-sdk <n>] [--target-sdk <n>]";

    static void run(String[] args) {
        if (args.length < 2) { System.err.println(USAGE); return; }
        if ("--help".equals(args[1]) || "-h".equals(args[1]) || "--version".equals(args[1])) {
            System.out.println(USAGE);
            return;
        }
        if (args[1].startsWith("-")) {
            // R6: a flag in the source position was treated as a directory name
            // and exited 0 with "no .kf/.kof files found" — a typo'd flag looked
            // like an empty project.
            System.err.println("build: unknown flag: " + args[1]
                    + " (see 'kof build --help')");
            System.exit(1);
            return;
        }
        Path src = Path.of(args[1]);
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
        boolean useDeps = false;
        boolean printSizes = false;
        for (int i = 2; i < args.length; i++) {
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
        CompilerDriver driver = new CompilerDriver();
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
        // ExternalClasspath — separadas por ':' ou ';'
        List<Path> externalEntries = new ArrayList<>();
        if (classpath != null && !classpath.isBlank()) {
            for (String part : classpath.split(Pattern.quote(File.pathSeparator))) {
                if (!part.isBlank()) externalEntries.add(Path.of(part));
            }
            driver.setExternalClasspath(externalEntries);
        }
        // kofdeps: dependências Maven resolvidas no cache ~/.kof/deps
        if (useDeps) {
            try {
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
        List<Path> files = KofCliSupport.collect(backendDir);
        if (files.isEmpty()) { System.out.println("no .kf/.kof files found"); return; }
        files.sort(java.util.Comparator.comparing(p -> p.getFileName().toString()));
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
            apkOk = runApkPipeline(backendOut, androidMin, androidTarget,
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
     */
    static Path buildFatJar(Path classesDir, List<Path> deps) throws IOException {
        String mainClass = KofCliSupport.findMainClass(classesDir);
        if (mainClass == null) throw new IOException("no main class found em " + classesDir);
        Path jar = classesDir.resolve("kof-app.jar");
        java.util.jar.Manifest manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().put(java.util.jar.Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(java.util.jar.Attributes.Name.MAIN_CLASS, mainClass);
        java.util.Set<String> seen = new java.util.HashSet<>();
        try (java.util.jar.JarOutputStream jos =
                     new java.util.jar.JarOutputStream(Files.newOutputStream(jar), manifest)) {
            addClassesToJar(jos, classesDir, classesDir, seen);
            for (Path dep : deps) {
                if (Files.isDirectory(dep)) {
                    addClassesToJar(jos, dep, dep, seen);
                } else if (dep.toString().endsWith(".jar") && Files.isRegularFile(dep)) {
                    addJarEntriesToJar(jos, dep, seen);
                }
            }
        }
        return jar;
    }

    private static void addClassesToJar(java.util.jar.JarOutputStream jos, Path root, Path dir,
                                        java.util.Set<String> seen) throws IOException {
        try (var s = Files.walk(dir)) {
            for (Path p : s.filter(Files::isRegularFile).sorted().toList()) {
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
     * Pipeline APK standalone (#6/#7): chama os binários oficiais do SDK
     * direto — d8 → aapt2 → zip → zipalign → apksigner. Sem --keystore,
     * gera debug keystore local na primeira vez; com --keystore, assina
     * com o keystore do usuário (release signing parametrizável).
     */
    private static boolean runApkPipeline(Path projDir, int minSdk, int targetSdk,
                                          String keystore, String storepass,
                                          String keypass, String keyalias) {
        String androidHome = System.getenv("ANDROID_HOME");
        if (androidHome == null || androidHome.isBlank()) {
            System.err.println("--apk: ANDROID_HOME not set; generate the project and use 'mvn verify'");
            return false;
        }
        Path bt = Path.of(androidHome, "build-tools", "34.0.0");
        Path platformJar = Path.of(androidHome, "platforms", "android-" + targetSdk, "android.jar");
        if (!Files.isExecutable(bt.resolve("aapt2"))) {
            System.err.println("--apk: build-tools 34.0.0 not found in " + bt);
            return false;
        }
        Path build = projDir.resolve("target");
        Path apkDir = build.resolve("apk");
        boolean userKs = keystore != null && !keystore.isBlank();
        try {
            Files.createDirectories(apkDir);
            // debug keystore local (só quando o usuário não passou --keystore)
            Path ks = userKs ? Path.of(keystore) : build.resolve("debug.keystore");
            if (!userKs && !Files.exists(ks)) {
                run(List.of("keytool", "-genkeypair", "-keystore", ks.toString(),
                        "-alias", "androiddebugkey", "-storepass", "android",
                        "-keypass", "android", "-keyalg", "RSA", "-validity", "9999",
                        "-dname", "CN=Kof Debug,O=Kof,C=BR"), projDir);
            }
            run(List.of(bt.resolve("aapt2").toString(), "compile", "--dir",
                    projDir.resolve("src/main/res").toString(),
                    "-o", apkDir.resolve("res.zip").toString()), projDir);
            run(List.of(bt.resolve("aapt2").toString(), "link",
                    "-o", apkDir.resolve("base.apk").toString(),
                    "-I", platformJar.toString(),
                    "--manifest", projDir.resolve("src/main/AndroidManifest.xml").toString(),
                    "-A", projDir.resolve("src/main/assets").toString(),
                    "-R", apkDir.resolve("res.zip").toString()), projDir);
            run(List.of(bt.resolve("d8").toString(), "--release",
                    "--lib", platformJar.toString(), "--min-api", Integer.toString(minSdk),
                    "--output", apkDir.toString(),
                    projDir.resolve("libs/kof-app.jar").toString()), projDir);
            run(List.of("jar", "uf", apkDir.resolve("base.apk").toString(),
                    "-C", apkDir.toString(), "classes.dex"), projDir);
            run(List.of(bt.resolve("zipalign").toString(), "-f", "4",
                    apkDir.resolve("base.apk").toString(),
                    apkDir.resolve("aligned.apk").toString()), projDir);
            String sp = userKs && storepass != null ? storepass : "android";
            String kp = userKs && keypass != null ? keypass : sp;
            List<String> sign = new ArrayList<>(List.of(
                    bt.resolve("apksigner").toString(), "sign",
                    "--ks", ks.toString(), "--ks-pass", "pass:" + sp,
                    "--key-pass", "pass:" + kp));
            if (userKs && keyalias != null && !keyalias.isBlank()) {
                sign.add("--ks-key-alias");
                sign.add(keyalias);
            }
            sign.add("--out");
            sign.add(build.resolve("kof-app.apk").toString());
            sign.add(apkDir.resolve("aligned.apk").toString());
            run(sign, projDir);
            System.out.println("APK built: " + build.resolve("kof-app.apk"));
            return true;
        } catch (Exception e) {
            System.err.println("APK pipeline failed: " + e.getMessage());
            return false;
        }
    }

    private static void run(List<String> cmd, Path cwd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(cwd.toFile()).inheritIO();
        Process proc = pb.start();
        int code = proc.waitFor();
        if (code != 0) throw new IOException("exit " + code + ": " + cmd.get(0));
    }
}
