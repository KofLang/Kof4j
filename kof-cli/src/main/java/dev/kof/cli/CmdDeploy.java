package dev.kof.cli;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.KofVersion;
import dev.kof.compiler.Target;
import dev.kof.compiler.TargetMatrix;
import dev.kof.compiler.backend.AndroidProjectWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * X9 fatia 1 (IMPLEMENTATION-UNIVERSAL-PLATFORM.md, fila X; VISION §9) —
 * {@code kof deploy}: empacota uma release JVM a partir do pipeline do build.
 *
 * <p>O que a fatia faz (target JVM): compila o módulo, empacota o fat jar
 * (D-APP.5, reusa {@link CmdBuild#buildFatJar}), e monta {@code deploy/} com
 * o artefato + {@code RELEASE.md} (metadados legíveis: target, versão do
 * compilador, timestamp UTC, main class) + {@code SHA256SUMS} (integridade) +
 * {@code .tar.gz} (artefato de distribuição único).
 *
 * <p>Fatia 2 (18/09): faces NATIVE x86_64 (binário ELF, mode 0755 no tar) e
 * JS ({@code Default.mjs}) empacotadas com a MESMA estrutura de release.
 * Fatia 3 (18/09): face ANDROID — APK assinado via pipeline --apk do build
 * (ANDROID_HOME/build-tools obrigatórios; sem SDK, recusa honesta).
 *
 * <p>D2-A (D-POLL-19, 19/09): {@code --publish} SUBLIGE a release ao host
 * oficial GitHub Releases ({@link DeployPublish}) — sem token, falha honesta
 * (R6) depois do pacote local pronto; nunca exit 0 sem artefato. Cross
 * riscv64/aarch64 empacotam desde a fatia 6 (X9, 20/09) — toolchain ausente =
 * falha honesta nomeando a ferramenta (R6), nao recusa preventiva.
 */
final class CmdDeploy {

    private static final String USAGE = "usage: kof deploy <source-dir|file.kf> --target <t>[,<t>...|all]"
            + " [--output <dir>] [--name <n>] [--version <v>] [--publish <registry>]\n"
            + "  targets: jvm|native|js|android (all = jvm,native,js); --publish publica"
            + " virgula faz o multi-target 8.4 (mesma fonte, uma release por alvo"
            + " + .deploy-manifest.json); --publish [<owner/repo>] sobe os tar.gz"
            + " ao GitHub Releases (D2-A; GH_TOKEN necessário)";

    private CmdDeploy() {
    }

    static void run(String[] args) {
        if (args.length < 2) { System.err.println(USAGE); System.exit(1); return; }
        if (args[1].equals("--help") || args[1].equals("-h")) {
            System.out.println(USAGE);
            return;
        }
        if (args[1].startsWith("-")) {
            System.err.println("deploy: unknown flag: " + args[1] + " (see 'kof deploy --help')");
            System.exit(1);
            return;
        }
        Path src = Path.of(args[1]);
        if (!Files.exists(src)) {
            System.err.println("not found: " + src);
            System.exit(1);
            return;
        }
        if (Files.isRegularFile(src)) {
            Path parent = src.toAbsolutePath().normalize().getParent();
            if (parent == null) {
                System.err.println("deploy: cannot resolve the module directory of " + src);
                System.exit(1);
                return;
            }
            src = parent;
        }
        java.util.LinkedHashSet<Target> targets = new java.util.LinkedHashSet<>();
        Path out = Path.of("build");
        String name = null;
        String version = "0.0.0";
        String publish = null;
        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--target=")) {
                addTargets(targets, arg.substring("--target=".length()));
            } else if (arg.equals("--target") && i + 1 < args.length) {
                addTargets(targets, args[++i]);
            } else if (arg.startsWith("--output=")) {
                out = Path.of(arg.substring("--output=".length()));
            } else if (arg.equals("--output") && i + 1 < args.length) {
                out = Path.of(args[++i]);
            } else if (arg.startsWith("--name=")) {
                name = arg.substring("--name=".length());
            } else if (arg.equals("--name") && i + 1 < args.length) {
                name = args[++i];
            } else if (arg.startsWith("--version=")) {
                version = arg.substring("--version=".length());
            } else if (arg.equals("--version") && i + 1 < args.length) {
                version = args[++i];
            } else if (arg.startsWith("--publish=")) {
                publish = arg.substring("--publish=".length());
            } else if (arg.equals("--publish") && i + 1 < args.length) {
                publish = args[++i];
            } else if (arg.equals("--publish")) {
                publish = ""; // flag sem valor: mesmo gap honesto
            } else if (arg.equals("--help") || arg.equals("-h")) {
                System.out.println(USAGE);
                return;
            } else if (arg.startsWith("-")) {
                System.err.println("deploy: unknown or incomplete flag: " + arg
                        + " (accepts: --target --output --name --version --publish)");
                System.exit(1);
                return;
            } else {
                System.err.println("deploy: unexpected argument: " + arg
                        + " (see 'kof deploy --help')");
                System.exit(1);
                return;
            }
        }
        if (targets.isEmpty()) targets.add(Target.JVM);
        // X9 fatia 6 (20/09): o cross (riscv64/aarch64) EMPACOTA como o NATIVE x86 —
        // mesmo pipeline do driver (as/ld via NativeArchEmitter; toolchain ausente =
        // ToolchainMissing nomeando a ferramenta no diagnostico, R6 — nao mais a
        // recusa generalizada DEP001, que recusava sem nem tentar). Faces empacotaveis:
        // JVM (fat jar), NATIVE x86_64 + cross (ELF 0755), JS (.mjs), ANDROID (APK).
        // --publish (D2-A, D-POLL-19 19/09): publica a release empacotada no
        // host oficial GitHub Releases. Sem token/endpoint = falha honesta
        // (R6) DEPOIS do pacote local existir — nunca "meia publicação" falsa.
        if (name == null) name = src.toAbsolutePath().normalize().getFileName().toString();
        String safeName = name.replaceAll("[^A-Za-z0-9._-]", "-");
        if (safeName.isBlank() || safeName.equals("-")) {
            System.err.println("deploy: --name resolved to an unusable artifact id: '" + name + "'");
            System.exit(1);
            return;
        }
        if (!version.matches("[A-Za-z0-9._+-]+")) {
            System.err.println("deploy: --version must match [A-Za-z0-9._+-]+: '" + version + "'");
            System.exit(1);
            return;
        }
        if (targets.size() > 1) {
            deployMulti(src, out, safeName, version, new ArrayList<>(targets), publish);
            return;
        }
        try {
            Release r = deploy(src, out, safeName, version, targets.iterator().next(), "");
            System.out.println("deploy → " + r.releaseDir());
            System.out.println("artifact → " + r.tgz()
                    + (r.sha256() == null ? " (library: sources only)"
                            : " (sha256 " + r.sha256().substring(0, 12) + "…)"));
            if (publish != null) {
                DeployPublish.publishAll(List.of(r), null, publish, safeName, version);
            }
        } catch (IOException e) {
            System.err.println("deploy: failed: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void addTargets(java.util.LinkedHashSet<Target> targets, String spec) {
        for (String tok : spec.split(",")) {
            String t = tok.trim();
            if (t.isEmpty()) continue;
            if (t.equals("all")) { // 8.4: as três faces nucleares da plataforma
                targets.add(Target.JVM);
                targets.add(Target.NATIVE);
                targets.add(Target.JS);
                continue;
            }
            targets.add(KofCliSupport.parseTarget(t));
        }
    }

    /** Resultado de uma face num multi-deploy (8.4). */
    record Release(String target, String status, Path releaseDir, Path tgz,
                           String artifact, String sha256, String error) {
    }

    /**
     * X9 fatia 4 / linha 8.4 (IMPLEMENTATION-UNIVERSAL-PLATFORM.md): multi-target
     * da MESMA fonte (“same source → JVM/Native/JS”). Cada alvo roda o pipeline
     * completo de release no seu subdiretório; alvo que falha (ferramenta ausente,
     * compilação, toolchain ausente) não derruba os demais — o resumo e o
     * {@code .deploy-manifest.json} registram SUCCESS/FAIL com a razão honesta
     * (R6/R7) e o exit é 1 se houve falha.
     */
    private static void deployMulti(Path src, Path out, String name, String version,
                                    List<Target> targets, String publish) {
        List<Release> results = new ArrayList<>();
        boolean anyFail = false;
        for (Target t : targets) {
            String tn = TargetMatrix.name(t);
            try {
                Release r = deploy(src, out, name, version, t, "-" + tn);
                results.add(r);
                System.out.println("  " + tn + " → " + r.releaseDir());
            } catch (IOException e) {
                results.add(new Release(tn, "FAIL", null, null, null, null, e.getMessage()));
                anyFail = true;
            }
        }
        String m = DeployPublish.manifestJson(results);
        Path manifest = out.resolve("deploy").resolve(name + "-" + version + ".deploy-manifest.json");
        try {
            Files.createDirectories(manifest.getParent());
            Files.writeString(manifest, m, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("deploy: failed to write manifest: " + e.getMessage());
            System.exit(1);
            return;
        }
        for (Release r : results) {
            System.out.println("  " + r.target() + " " + r.status()
                    + (r.error() != null ? ": " + r.error() : ""));
        }
        System.out.println("multi-deploy → " + manifest);
        if (publish != null) {
            List<Release> ok = new ArrayList<>();
            for (Release r : results) if ("SUCCESS".equals(r.status())) ok.add(r);
            if (!ok.isEmpty()) {
                try {
                    DeployPublish.publishAll(ok, manifest, publish, name, version);
                } catch (IOException e) {
                    System.err.println("deploy: failed: " + e.getMessage());
                    System.exit(1);
                }
            }
        }
        if (anyFail) System.exit(1);
    }

    private static Release deploy(Path src, Path out, String name, String version,
                                  Target target, String dirSuffix) throws IOException {
        // 1) compila o módulo (mesma convenção Go-like do build)
        KofCliSupport.Layout layout = KofCliSupport.detectLayout(src);
        Path backendDir = layout.backendDir();
        String app001 = KofCliSupport.app001(target, layout.fullStack());
        if (app001 != null) throw new IOException(app001);
        List<Path> files = KofCliSupport.collect(backendDir);
        // #566 (b): a release carrega as FONTES (todo o modulo, recursivo); um modulo sem fontes no
        // topo (so arvore de pacotes) e uma BIBLIOTECA — valida compilando e publica so as fontes.
        List<Path> tree = DeploySources.collectTree(backendDir, out, layout.fullStack() ? "web" : null);
        boolean library = files.isEmpty();
        if (library) files = new ArrayList<>(tree);
        if (files.isEmpty()) throw new IOException("no .kf/.kof files found in " + backendDir);
        files.sort(java.util.Comparator.comparing(p -> p.getFileName().toString()));
        Path classes = out.resolve("deploy-classes" + dirSuffix);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult module = driver.compileSources(files, classes, target,
                backendDir.toAbsolutePath().normalize());
        for (var d : module.diagnostics().getDiagnostics()) System.out.println(d.format());
        if (!module.success()) throw new IOException("compilation failed for target " + TargetMatrix.name(target));

        // 2) artefato único da face: fat jar (JVM, D-APP.5), ELF (native),
        // Default.mjs (JS)
        Path built;
        String ext;
        int tarMode;
        if (library) {
            built = null;
            ext = "";
            tarMode = 0644;
        } else switch (target) {
            case JVM -> {
                built = CmdBuild.buildFatJar(classes, List.of());
                ext = ".jar";
                tarMode = 0644;
            }
            case NATIVE, NATIVE_RISCV64, NATIVE_AARCH64 -> {
                // x86_64 nativo e o cross (riscv64/aarch64) caem no MESMO ponto de
                // saida do driver (Default/Main); so muda a ferramenta do emissor
                // (KOF_CROSS_PREFIX pode prefixa-la p/ teste/ambiente).
                built = classes.resolve("Default").resolve("Main");
                if (!Files.isRegularFile(built)) {
                    throw new IOException("native binary not found: " + built);
                }
                ext = "";
                tarMode = 0755;
            }
            case JS -> {
                built = findJsEntry(classes);
                ext = ".mjs";
                tarMode = 0644;
            }
            case ANDROID -> {
                // pipeline --apk do build (gera o projeto Android dentro de
                // classes/ e assina com debug.keystore ou --keystore do build).
                boolean ok = ApkToolchain.runApkPipeline(classes,
                        AndroidProjectWriter.DEFAULT_MIN_SDK,
                        AndroidProjectWriter.DEFAULT_TARGET_SDK, null, null, null, null);
                if (!ok) {
                    throw new IOException("android APK pipeline failed (DEP001"
                            + " conditions: ANDROID_HOME/build-tools required)");
                }
                built = classes.resolve("target").resolve("kof-app.apk");
                if (!Files.isRegularFile(built)) {
                    throw new IOException("apk not found: " + built);
                }
                ext = ".apk";
                tarMode = 0644;
            }
            case WASM -> throw new IOException(
                    "target 'wasm' emits the scalar subset (unit 15.2) but has no deployable"
                            + " host/archive yet (WASM001; wasm-wasi-plan.md TIER 15 units"
                            + " 15.3+, issue #776)");
            case WASI -> throw new IOException(
                    "target 'wasi' has no emitting backend yet (WASM001; wasm-wasi-plan.md"
                            + " TIER 15 unit 15.3, issue #776)");
            default -> throw new IOException("unreachable: " + target);
        }

        // 3) diretório da release
        Path releaseDir = out.resolve("deploy").resolve(name + "-" + version + dirSuffix);
        Files.createDirectories(releaseDir);
        String artifact = name + "-" + version + ext;
        Path jarDst = releaseDir.resolve(artifact);
        if (!library) Files.copy(built, jarDst, StandardCopyOption.REPLACE_EXISTING);
        List<Path> staged = DeploySources.stage(backendDir, tree, releaseDir);
        // §298: a release JS precisa ser AUTOCONTIDA — o entry importa módulos
        // relativos do build (./kof-runtime.mjs, ./kof-runtime-io.mjs, ...), que
        // vivem AO LADO dele; copiar só o entry deixava o "node <x>.mjs" do
        // RELEASE.md morrendo em ERR_MODULE_NOT_FOUND. Closure de imports.
        List<Path> jsDeps = new ArrayList<>();
        if (target == Target.JS && !library) {
            for (Path dep : jsImportClosure(built)) {
                Path depSrc = built.getParent().resolve(dep.toString());
                Path dst = releaseDir.resolve(dep.toString());
                Files.copy(depSrc, dst, StandardCopyOption.REPLACE_EXISTING);
                jsDeps.add(dep);
            }
        }

        // 4) RELEASE.md (metadados legíveis) + SHA256SUMS (integridade)
        String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC));
        String runCmd;
        if (target == Target.JVM) {
            runCmd = "java -jar " + artifact;
        } else if (target == Target.NATIVE || target == Target.NATIVE_RISCV64
                || target == Target.NATIVE_AARCH64) {
            runCmd = "./" + artifact; // cross roda no alvo (ou qemu -L sysroot)
        } else if (target == Target.JS) {
            runCmd = "node " + artifact;
        } else {
            runCmd = "adb install " + artifact;
        }
        String mainLine = target == Target.JVM && !library
                ? "- main class: " + KofCliSupport.findMainClass(classes) + "\n" : "";
        String artifactLine = library
                ? "- kind: library (source module, no runnable artifact)\n"
                : "- artifact: " + artifact + "\n";
        Files.writeString(releaseDir.resolve("RELEASE.md"),
                "# Release " + name + " " + version + "\n\n"
                        + artifactLine
                        + "- target: " + TargetMatrix.name(target) + "\n"
                        + mainLine
                        + "- sources: " + staged.size() + " file(s) under src/ (consumed as a source module)\n"
                        + "- compiler: " + KofVersion.version() + "\n"
                        + "- built at (UTC): " + timestamp + "\n"
                        + (library ? "" : "- run: " + runCmd + "\n"),
                StandardCharsets.UTF_8);
        String sha256 = library ? null : sha256Hex(jarDst);
        StringBuilder sums = new StringBuilder(library ? "" : sha256 + "  " + artifact + "\n");
        for (Path dep : jsDeps) {
            sums.append(sha256Hex(releaseDir.resolve(dep))).append("  ").append(dep).append("\n");
        }
        for (Path s : staged) {   // cada fonte coberta: o consumidor recusa a que nao conferir
            sums.append(sha256Hex(releaseDir.resolve(s))).append("  ")
                    .append(s.toString().replace('\\', '/')).append("\n");
        }
        Files.writeString(releaseDir.resolve("SHA256SUMS"), sums.toString(), StandardCharsets.UTF_8);

        // 5) tar.gz do conjunto (artefato de distribuição único)
        List<Path> releaseFiles = new ArrayList<>();
        if (!library) releaseFiles.add(jarDst.getFileName());
        releaseFiles.addAll(jsDeps);
        releaseFiles.addAll(staged);
        releaseFiles.add(Path.of("RELEASE.md"));
        releaseFiles.add(Path.of("SHA256SUMS"));
        Path tgz = out.resolve("deploy").resolve(name + "-" + version + dirSuffix + ".tar.gz");
        writeTarGz(tgz, releaseDir, releaseFiles, tarMode);
        return new Release(TargetMatrix.name(target), "SUCCESS", releaseDir, tgz,
                library ? null : artifact, sha256, null);
    }

    /**
     * §298: closure (transitiva, sem ciclos) dos imports relativos de um
     * módulo ES gerado pelo backend JS — os {@code ./x.mjs} que precisam
     * acompanhar o entry para a release rodar fora do diretório de build.
     */
    static List<Path> jsImportClosure(Path entry) throws IOException {
        List<Path> out = new ArrayList<>();
        java.util.Set<Path> seen = new java.util.LinkedHashSet<>();
        seen.add(entry.toAbsolutePath().normalize());
        java.util.Deque<Path> queue = new java.util.ArrayDeque<>();
        queue.add(entry);
        while (!queue.isEmpty()) {
            Path file = queue.poll();
            for (Path dep : directJsImports(file)) {
                Path key = dep.toAbsolutePath().normalize();
                if (seen.add(key)) {
                    out.add(dep.getFileName());
                    queue.add(dep);
                }
            }
        }
        return out;
    }

    private static List<Path> directJsImports(Path file) throws IOException {
        List<Path> deps = new ArrayList<>();
        for (String line : Files.readAllLines(file)) {
            String t = line.trim();
            if (!t.startsWith("import ") || !t.contains(" from './")) continue;
            int a = t.indexOf("from './") + "from '".length();
            int b = t.indexOf("'", a);
            if (b < 0) continue;
            Path dep = file.getParent().resolve(t.substring(a, b));
            if (Files.isRegularFile(dep)) deps.add(dep);
        }
        return deps;
    }

    private static Path findJsEntry(Path dir) throws IOException {
        Path direct = dir.resolve("Default.mjs");
        if (Files.isRegularFile(direct)) return direct;
        try (var s = Files.walk(dir)) {
            var opt = s.filter(p -> p.getFileName().toString().equals("Default.mjs")).findFirst();
            if (opt.isPresent()) return opt.get();
        }
        throw new IOException("no Default.mjs found in " + dir);
    }

    static String sha256Hex(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (var in = Files.newInputStream(file)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 unavailable", e);
        }
    }

    /** Tar ustar mínimo (arquivos regulares) + gzip — sem dependência externa. */
    static void writeTarGz(Path tgz, Path root, List<Path> relativeFiles,
                           int artifactMode) throws IOException {
        try (var fos = Files.newOutputStream(tgz);
             var gzos = new java.util.zip.GZIPOutputStream(fos)) {
            for (Path rel : relativeFiles) {
                Path abs = root.resolve(rel);
                byte[] data = Files.readAllBytes(abs);
                // 1º entry = artefato da face (JVM 0644, native 0755, JS 0644);
                // RELEASE.md/SHA256SUMS sempre 0644.
                int mode = rel.equals(relativeFiles.get(0)) ? artifactMode : 0644;
                byte[] header = new byte[512];
                byte[] nameBytes = (rel.toString().replace('\\', '/') + "\0")
                        .getBytes(StandardCharsets.UTF_8);
                if (nameBytes.length > 100) {
                    throw new IOException("tar entry name too long: " + rel);
                }
                System.arraycopy(nameBytes, 0, header, 0, nameBytes.length);
                writeOctal(header, 100, mode, 8);   // mode
                writeOctal(header, 108, 0, 8);      // uid
                writeOctal(header, 116, 0, 8);      // gid
                writeOctal(header, 124, data.length, 12); // size
                writeOctal(header, 136, Instant.now().getEpochSecond(), 12); // mtime
                header[156] = '0';                  // typeflag: regular file
                System.arraycopy("ustar\000".getBytes(StandardCharsets.US_ASCII), 0, header, 257, 6);
                System.arraycopy("00".getBytes(StandardCharsets.US_ASCII), 0, header, 263, 2);
                int checksum = 0;
                header[148] = 0x20; header[149] = 0x20; header[150] = 0x20;
                header[151] = 0x20; header[152] = 0x20; header[153] = 0x20;
                header[154] = 0x20; header[155] = 0x20; // campo a espaços p/ cálculo
                for (byte b : header) checksum += b & 0xff;
                writeOctal(header, 148, checksum, 8);
                gzos.write(header);
                gzos.write(data);
                int pad = (512 - (data.length % 512)) % 512;
                for (int i = 0; i < pad; i++) gzos.write(0);
            }
            byte[] eof = new byte[1024]; // dois blocos zero encerram o tar
            gzos.write(eof);
            gzos.finish();
        }
    }

    private static void writeOctal(byte[] header, int off, long value, int len) {
        String oct = String.format("%0" + (len - 1) + "o\0", value);
        System.arraycopy(oct.getBytes(StandardCharsets.US_ASCII), 0, header, off, len);
    }
}
