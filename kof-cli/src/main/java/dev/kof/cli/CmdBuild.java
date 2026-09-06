package dev.kof.cli;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.Diagnostic;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Target;

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

    static void run(String[] args) {
        if (args.length < 2) { System.err.println("usage: kof build <source-dir> [--target jvm|native|js|android] [--output <dir>] [--release] [--apk] [--classpath <jars>] [--keystore <ks> [--storepass <p>] [--keypass <p>] [--alias <a>]]");
        if ("--help".equals(args[1]) || "-h".equals(args[1]) || "--version".equals(args[1])) {
            System.out.println("usage: kof build <source-dir> [--target jvm|native|js|android] [--output <dir>] [--release] [--apk] [--classpath <jars>] [--keystore <ks> [--storepass <p>] [--keypass <p>] [--alias <a>]]");
            return;
        } return; }
        Path src = Path.of(args[1]);
        Target target = Target.JVM;
        Path out = Path.of("build/classes");
        boolean release = false;
        boolean apk = false;
        String classpath = null;
        String keystore = null;
        String storepass = null;
        String keypass = null;
        String keyalias = null;
        boolean useDeps = false;
        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--target=")) {
                target = KofCliSupport.parseTarget(arg.substring("--target=".length()));
            } else if (arg.startsWith("--output=")) {
                out = Path.of(arg.substring("--output=".length()));
            } else if (arg.equals("--target") && i + 1 < args.length) {
                target = KofCliSupport.parseTarget(args[i + 1]);
                i++;
            } else if (arg.equals("--output") && i + 1 < args.length) {
                out = Path.of(args[i + 1]);
                i++;
            } else if (arg.equals("--release")) {
                release = true;
            } else if (arg.equals("--apk")) {
                apk = true;
            } else if (arg.equals("--deps")) {
                useDeps = true;
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
            }
        }
        CompilerDriver driver = new CompilerDriver();
        if (release) driver.setDebugInfoEnabled(false);
        // dependências externas (android.jar etc.) geridas pelo Kof via
        // ExternalClasspath — separadas por ':' ou ';'
        if (classpath != null && !classpath.isBlank()) {
            List<Path> entries = new ArrayList<>();
            for (String part : classpath.split("[:;]")) {
                if (!part.isBlank()) entries.add(Path.of(part));
            }
            driver.setExternalClasspath(entries);
        }
        // kofdeps: dependências Maven resolvidas no cache ~/.kof/deps
        if (useDeps) {
            try {
                String depsCp = Deps.classpath();
                if (!depsCp.isBlank()) {
                    List<Path> entries = new ArrayList<>();
                    for (String part : depsCp.split(java.util.regex.Pattern.quote(
                            System.getProperty("os.name", "").toLowerCase().contains("win") ? ";" : ":"))) {
                        if (!part.isBlank()) entries.add(Path.of(part));
                    }
                    driver.setExternalClasspath(entries);
                }
            } catch (IOException e) {
                System.err.println("build: falha ao ler kofdeps: " + e.getMessage());
                return;
            }
        }
        List<Path> files = KofCliSupport.collect(src);
        if (files.isEmpty()) { System.out.println("no .kf files found"); return; }
        files.sort(java.util.Comparator.comparing(p -> p.getFileName().toString()));
        // convenção Go-like: TODOS os .kf do diretório formam UM módulo
        // (raiz = diretório passado ao build; imports de pacotes resolvem daí)
        CompilationResult module = driver.compileSources(files, out, target,
                src.toAbsolutePath().normalize());
        for (Diagnostic d : module.diagnostics().getDiagnostics()) System.out.println(d.format());
        if (!module.success()) System.exit(1);
        // target android + --apk: pipeline direto (sem Maven) usando o SDK
        if (target == Target.ANDROID && apk) {
            runApkPipeline(out, keystore, storepass, keypass, keyalias);
        }
    }

    /**
     * Pipeline APK standalone (#6/#7): chama os binários oficiais do SDK
     * direto — d8 → aapt2 → zip → zipalign → apksigner. Sem --keystore,
     * gera debug keystore local na primeira vez; com --keystore, assina
     * com o keystore do usuário (release signing parametrizável).
     */
    private static void runApkPipeline(Path projDir, String keystore, String storepass,
                                       String keypass, String keyalias) {
        String androidHome = System.getenv("ANDROID_HOME");
        if (androidHome == null || androidHome.isBlank()) {
            System.err.println("--apk: ANDROID_HOME não definido; gere o projeto e use 'mvn verify'");
            return;
        }
        Path bt = Path.of(androidHome, "build-tools", "34.0.0");
        Path platformJar = Path.of(androidHome, "platforms", "android-34", "android.jar");
        if (!Files.isExecutable(bt.resolve("aapt2"))) {
            System.err.println("--apk: build-tools 34.0.0 não encontrado em " + bt);
            return;
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
                    "--lib", platformJar.toString(), "--min-api", "24",
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
            System.out.println("APK gerado: " + build.resolve("kof-app.apk"));
        } catch (Exception e) {
            System.err.println("pipeline apk falhou: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void run(List<String> cmd, Path cwd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(cwd.toFile()).inheritIO();
        Process proc = pb.start();
        int code = proc.waitFor();
        if (code != 0) throw new IOException("exit " + code + ": " + cmd.get(0));
    }
}
