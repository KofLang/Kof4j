package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * X9 fatia 1 ({@code kof deploy --target jvm}): a release empacotada é REAL —
 * o jar roda ({@code java -jar}), o SHA256SUMS confere com o artefato, o
 * RELEASE.md carrega os metadados e o .tar.gz é um tar ustar+gzip legível.
 * Recusas honestas (R6/R7): target não-JVM e --publish (registry = decisão
 * D2) saem com exit 1 + DEP001; flag desconhecida nunca é ignorada.
 */
class CmdDeployTest extends CmdDeploySupport {






    @Test
    void jvmReleaseIsPackagedAndConsistent(@TempDir Path dir) throws Exception {
        Path src = writeApp(dir, "main() { println(\"deploy ok\") }\n");
        CliResult r = run(dir, "deploy", src.toString(), "--target", "jvm",
                "--output", "dist", "--name", "servico", "--version", "1.2.3");
        assertEquals(0, r.exit(), "deploy exit, output:\n" + r.out());

        Path releaseDir = dir.resolve("dist/deploy/servico-1.2.3");
        assertTrue(Files.isDirectory(releaseDir), r.out());
        Path jar = releaseDir.resolve("servico-1.2.3.jar");
        assertTrue(Files.isRegularFile(jar), "jar ausente:\n" + r.out());

        // #565: o artefato distribuído não embute cópia truncada de si mesmo
        try (var zip = new java.util.zip.ZipFile(jar.toFile())) {
            assertNull(zip.getEntry("kof-app.jar"),
                    "deploy JVM não pode publicar cópia truncada do próprio fat jar");
            assertNotNull(zip.getEntry("Default/Main.class"), "classes do app no jar publicado");
        }

        // prova real: o artefato empacotado RODA
        Process run = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-jar", jar.toString())
                .directory(dir.toFile()).redirectErrorStream(true).start();
        String runOut = new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(run.waitFor(60, TimeUnit.SECONDS), "java -jar timeout:\n" + runOut);
        assertEquals(0, run.exitValue(), "java -jar exit:\n" + runOut);
        assertEquals("deploy ok", runOut.trim(), "saída do app empacotado");

        // checksum confere com o artefato
        String sums = Files.readString(releaseDir.resolve("SHA256SUMS"), StandardCharsets.UTF_8);
        String expected = CmdDeploy.sha256Hex(jar) + "  servico-1.2.3.jar";
        assertEquals(expected, sums.lines().findFirst().orElse(""), "1a linha do SHA256SUMS = o artefato (as fontes vem depois, #566)");

        // RELEASE.md com os metadados
        String release = Files.readString(releaseDir.resolve("RELEASE.md"), StandardCharsets.UTF_8);
        assertTrue(release.contains("servico-1.2.3.jar"), release);
        assertTrue(release.contains("jvm"), release);
        assertTrue(release.contains("Default.Main"), release);
        assertTrue(release.contains("java -jar servico-1.2.3.jar"), release);

        // tar.gz: ustar legível, primeiro entry = jar, depois as FONTES (#566, opção b: contrato do
        // tar mudou de propósito — era 3 entries), RELEASE.md e SHA256SUMS por último.
        Path tgz = dir.resolve("dist/deploy/servico-1.2.3.tar.gz");
        assertTrue(Files.size(tgz) > 512, "tar vazio");
        try (GZIPInputStream in = new GZIPInputStream(Files.newInputStream(tgz))) {
            TarEntry e1 = tarEntry(in);
            assertEquals("servico-1.2.3.jar", e1.name(), "1º entry");
            skipTarPayload(in, e1.size());
            TarEntry srcEntry = tarEntry(in);
            assertEquals("src/Main.kf", srcEntry.name(), "2º entry = a fonte do módulo");
            skipTarPayload(in, srcEntry.size());
            TarEntry rel = tarEntry(in);
            assertEquals("RELEASE.md", rel.name(), "3º entry");
            skipTarPayload(in, rel.size());
            TarEntry sumsEntry = tarEntry(in);
            assertEquals("SHA256SUMS", sumsEntry.name(), "4º entry");
            skipTarPayload(in, sumsEntry.size());
            byte[] eof = new byte[512];
            assertEquals(512, in.readNBytes(eof, 0, 512));
            assertTrue(isZeroBlock(eof), "bloco final do tar deve ser zero");
        }
    }

    // nonJvmTargetIsHonestGap REMOVIDO (18/09): caso obsoleto apos a face
    // ANDROID da X9 fatia 3 + §299 — "--target android" nao e mais um gap
    // universal: com SDK empacota APK (androidWithSdkPackagesApk, verificado
    // no CI ubuntu), sem SDK recusa honesto (androidWithoutSdkIsHonestFailure).
    // Assertar DEP001 com ANDROID_HOME do runner = conflito com o e2e.

    /**
     * D2-A (D-POLL-19 19/09): --publish ganhou face real (GitHub Releases) —
     * sem token continua FALHA honesta R6, agora com a razão certa no lugar
     * da antiga recusa DEP001/D2.
     */
    @Test
    void publishWithoutTokenFailsHonestly(@TempDir Path dir) throws Exception {
        Path src = writeApp(dir, "main() { println(\"x\") }\n");
        ProcessBuilder pb = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), "dev.kof.cli.Main",
                "deploy", src.toString(), "--target", "jvm", "--output", "dist",
                "--name", "app", "--version", "1.0.0", "--publish", "o/r");
        pb.environment().remove("GH_TOKEN");
        pb.environment().remove("GITHUB_TOKEN");
        pb.directory(dir.toFile()).redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor(120, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(1, p.exitValue(), "sem token nao pode haver publish falso: " + out);
        assertTrue(out.contains("GH_TOKEN"), "razao honesta citando o token: " + out);
        // o pacote local continua a unidade entregue (deploy rodou ANTES da recusa)
        java.nio.file.Files.walk(dir.resolve("dist")).map(java.nio.file.Path::toString)
                .filter(x -> x.endsWith(".tar.gz")).findFirst().orElseThrow(
                        () -> new AssertionError("tar.gz local deve existir mesmo com publish recusado"));
    }

    /** D2-A: release criada + tar.gz subido no endpoint (fake server, zero rede). */
    @Test
    void publishCreatesReleaseAndUploadsArtifact(@TempDir Path dir) throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        StringBuilder seen = new StringBuilder();
        java.util.concurrent.atomic.AtomicReference<byte[]> uploaded = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<String> uploadQ = new java.util.concurrent.atomic.AtomicReference<>();
        server.createContext("/", ex -> {
            String path = ex.getRequestURI().getPath();
            String auth = String.valueOf(ex.getRequestHeaders().getFirst("Authorization"));
            if (path.equals("/repos/o/r/releases")) {
                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                seen.append(body);
                seen.append(" AUTH=").append(auth);
                byte[] resp = releaseJson(serverAddr(server), 7, "/up/7", "/repos/o/r/releases/7/assets").getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(201, resp.length);
                ex.getResponseBody().write(resp);
                ex.close();
            } else if (path.equals("/up/7")) {
                uploadQ.set(ex.getRequestURI().getRawQuery());
                uploaded.set(ex.getRequestBody().readAllBytes());
                ex.sendResponseHeaders(201, -1);
                ex.close();
            } else {
                ex.sendResponseHeaders(404, -1);
                ex.close();
            }
        });
        server.start();
        try {
            Path src = writeApp(dir, "main() { println(\"pub ok\") }\n");
            CliResult r = runWithEnv(dir, java.util.Map.of(
                    "GH_TOKEN", "sekret", "KOF_PUBLISH_API", serverAddr(server)),
                    "deploy", src.toString(), "--target", "jvm", "--output", "dist",
                    "--name", "servo", "--version", "9.9.9", "--publish", "o/r");
            assertEquals(0, r.exit(), "publish feliz:\n" + r.out());
            assertTrue(r.out().contains("published jvm"), "linha de publish: " + r.out());
            assertTrue(seen.toString().contains("\"tag_name\":\"servo-9.9.9\""),
                    "release com a tag do nome-versao: " + seen);
            assertTrue(seen.toString().contains("Bearer sekret"), "token levado: " + seen);
            assertTrue(uploadQ.get().contains("name=servo-9.9.9.tar.gz"),
                    "asset = o tar.gz empacotado: " + uploadQ);
            Path tgz = dir.resolve("dist").resolve("deploy").resolve("servo-9.9.9.tar.gz");
            assertArrayEquals(java.nio.file.Files.readAllBytes(tgz), uploaded.get(),
                    "bytes do asset = bytes do tar.gz local");
        } finally {
            server.stop(0);
        }
    }

    /** D2-A: tag ja existe (422) = reusa a release da tag, nunca duplicata. */
    @Test
    void publishReusesExistingTagOn422(@TempDir Path dir) throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        StringBuilder paths = new StringBuilder();
        server.createContext("/", ex -> {
            String path = ex.getRequestURI().getPath();
            paths.append(path).append(' ');
            byte[] resp;
            int code;
            if (path.equals("/repos/o/r/releases")) {
                resp = "{\"message\":\"Validation Failed\"}".getBytes(StandardCharsets.UTF_8);
                code = 422;
            } else if (path.equals("/repos/o/r/releases/tags/servo-1.0")) {
                resp = releaseJson(serverAddr(server), 8, "/up/8", null).getBytes(StandardCharsets.UTF_8);
                code = 200;
            } else {
                ex.sendResponseHeaders(201, -1);
                ex.close();
                return;
            }
            ex.sendResponseHeaders(code, resp.length);
            ex.getResponseBody().write(resp);
            ex.close();
        });
        server.start();
        try {
            Path src = writeApp(dir, "main() { println(\"x\") }\n");
            CliResult r = runWithEnv(dir, java.util.Map.of(
                    "GH_TOKEN", "t", "KOF_PUBLISH_API", serverAddr(server)),
                    "deploy", src.toString(), "--target", "jvm", "--output", "d",
                    "--name", "servo", "--version", "1.0", "--publish", "o/r");
            assertEquals(0, r.exit(), "422 deve reusar, nao falhar:\n" + r.out());
            assertTrue(paths.toString().contains("/repos/o/r/releases/tags/servo-1.0"),
                    "GET da tag apos 422: " + paths);
        } finally {
            server.stop(0);
        }
    }

    /** D2-A: endpoint inacessivel = falha honesta DEPOIS do pacote local (R6). */
    @Test
    void publishUnreachableEndpointFailsHonestly(@TempDir Path dir) throws Exception {
        Path src = writeApp(dir, "main() { println(\"x\") }\n");
        CliResult r = runWithEnv(dir, java.util.Map.of(
                "GH_TOKEN", "t", "KOF_PUBLISH_API", "http://127.0.0.1:9"),
                "deploy", src.toString(), "--target", "jvm", "--output", "d",
                "--name", "app", "--version", "1.0", "--publish", "o/r");
        assertEquals(1, r.exit(), "sem endpoint nao ha publish feliz: " + r.out());
        assertTrue(r.out().contains("deploy:"), "razao prefixada: " + r.out());
        assertTrue(java.nio.file.Files.exists(dir.resolve("d").resolve("deploy")
                .resolve("app-1.0")), "pacote local permanece: " + r.out());
    }

    /** D2-A: parser do --publish (owner/repo, URL, URL .git, git@, lixo). */
    @Test
    void publishRepoSpecParsing() throws Exception {
        assertEquals("o/r", dev.kof.cli.DeployPublish.parseRepo("o/r"));
        assertEquals("o/r", dev.kof.cli.DeployPublish.parseRepo("https://github.com/o/r"));
        assertEquals("o/r", dev.kof.cli.DeployPublish.parseRepo("https://github.com/o/r.git"));
        assertEquals("o/r", dev.kof.cli.DeployPublish.parseRepo("git@github.com:o/r.git"));
        assertNull(dev.kof.cli.DeployPublish.parseRepo(""), "vazio = inferir do git");
        assertThrows(java.io.IOException.class,
                () -> dev.kof.cli.DeployPublish.parseRepo("http:// x"), "lixo recusa");
    }

    /** JSON de release no formato do GitHub (upload_url com template {?name,label}). */



    /** X9 fatia 2: face NATIVE — o ELF empacotado RODA e sai mode 0755 no tar. */
    @Test
    void nativeFaceRunsAndMarksExecutable(@TempDir Path dir) throws Exception {
        Path src = writeApp(dir, "main() { println(\"native ok\") }\n");
        CliResult r = run(dir, "deploy", src.toString(), "--target", "native",
                "--output", "dist", "--name", "edge", "--version", "2.0.0");
        assertEquals(0, r.exit(), "deploy native, saída:\n" + r.out());

        Path bin = dir.resolve("dist/deploy/edge-2.0.0/edge-2.0.0");
        assertTrue(Files.isRegularFile(bin), "binário ausente:\n" + r.out());

        // prova real: o artefato empacotado RODA
        Process run = new ProcessBuilder(bin.toString())
                .directory(dir.toFile()).redirectErrorStream(true).start();
        String runOut = new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(run.waitFor(60, TimeUnit.SECONDS), "timeout:\n" + runOut);
        assertEquals(0, run.exitValue(), "exit:\n" + runOut);
        assertEquals("native ok", runOut.trim(), "saída do binário empacotado");

        // checksum confere
        String sums = Files.readString(dir.resolve("dist/deploy/edge-2.0.0/SHA256SUMS"),
                StandardCharsets.UTF_8);
        assertEquals(CmdDeploy.sha256Hex(bin) + "  edge-2.0.0", sums.lines().findFirst().orElse(""),
                "SHA256SUMS diverge do ELF");

        // tar: artefato com mode 0755 no header
        try (GZIPInputStream in = new GZIPInputStream(
                Files.newInputStream(dir.resolve("dist/deploy/edge-2.0.0.tar.gz")))) {
            byte[] header = new byte[512];
            assertEquals(512, in.readNBytes(header, 0, 512));
            int mode = tarMode(header);
            assertTrue((mode & 0111) != 0, "artefato native deve ser executável: "
                    + Integer.toOctalString(mode));
        }
    }

    /** X9 fatia 2: face JS — o Default.mjs entra no pacote com checksum. */
    @Test
    void jsFacePackagesEntry(@TempDir Path dir) throws Exception {
        Path src = writeApp(dir, "main() { println(\"js ok\") }\n");
        CliResult r = run(dir, "deploy", src.toString(), "--target", "js",
                "--output", "dist", "--name", "webapp", "--version", "0.9.0");
        assertEquals(0, r.exit(), "deploy js, saída:\n" + r.out());

        Path mjs = dir.resolve("dist/deploy/webapp-0.9.0/webapp-0.9.0.mjs");
        assertTrue(Files.isRegularFile(mjs), ".mjs ausente:\n" + r.out());
        String sums = Files.readString(dir.resolve("dist/deploy/webapp-0.9.0/SHA256SUMS"),
                StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertTrue(
                sums.startsWith(CmdDeploy.sha256Hex(mjs) + "  webapp-0.9.0.mjs"),
                "linha do entry em SHA256SUMS:\n" + sums);
        // §298: a release tem de ser AUTOCONTIDA — o entry importa o runtime
        // relativo; sem os modulos irmaos o "node webapp-0.9.0.mjs" do
        // RELEASE.md morre em ERR_MODULE_NOT_FOUND (bug medido no tip).
        org.junit.jupiter.api.Assumptions.assumeTrue(hasNode(),
                "node nao disponivel no host (skip honesto, nao verde falso)");
        assertTrue(sums.contains("  kof-runtime.mjs"),
                "runtime deve acompanhar o entry (SHA256SUMS):\n" + sums);
        org.junit.jupiter.api.Assertions.assertTrue(
                Files.isRegularFile(dir.resolve("dist/deploy/webapp-0.9.0/kof-runtime.mjs")),
                "kof-runtime.mjs ausente na release");
        String out = runNode(dir.resolve("dist/deploy/webapp-0.9.0"), "webapp-0.9.0.mjs");
        assertTrue(out.contains("js ok"), "node na release empacotada:\n" + out);
        String release = Files.readString(dir.resolve("dist/deploy/webapp-0.9.0/RELEASE.md"),
                StandardCharsets.UTF_8);
        assertTrue(release.contains("node webapp-0.9.0.mjs"), release);
        assertTrue(release.contains("js"), release);
    }

    /** X9 fatia 6: sem toolchain cross o deploy FALHA honesto nomeando a
     *  ferramenta (R6) — nao e mais recusa preventiva DEP001: ele TENTOU. */
    @Test
    void crossDeployWithoutToolchainFailsHonestly(@TempDir Path dir) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                !hasCrossToolchain(), "host com toolchain cross real: face provada e o empacotamento");
        Path src = writeApp(dir, "main() { println(\"x\") }\n");
        for (String t : new String[]{"native.riscv64", "native.aarch64"}) {
            CliResult r = run(dir, "deploy", src.toString(), "--target", t);
            assertEquals(1, r.exit(), t + " sem toolchain deve falhar: " + r.out());
            assertTrue(r.out().contains("binary not found"), t + " esperava falha honesta: " + r.out());
            assertTrue(r.out().contains(t.contains("risc") ? "riscv64" : "aarch64"),
                    t + " deve nomear a ferramenta: " + r.out());
        }
    }


    /** X9 fatia 6 (padrao house X7-3/X7-4): stubs de as/ld via KOF_CROSS_PREFIX
     *  provam no host o pipeline INTEIRO do cross-release (argv do alvo certo,
     *  ELF 0755, RELEASE.md, SHA256SUMS, tar.gz). Toolchain real = CI. */
    @Test
    void crossReleasePackagesWithStubToolchain(@TempDir Path dir) throws Exception {
        Path stubs = dir.resolve("crossbin");
        Files.createDirectories(stubs);
        for (String tool : new String[]{"riscv64-linux-gnu-as", "riscv64-linux-gnu-ld",
                "aarch64-linux-gnu-as", "aarch64-linux-gnu-ld"}) {
            Path sh = stubs.resolve(tool);
            Files.writeString(sh, "#!/bin/sh\nprev=\"\"\nfor a in \"$@\"; do\n"
                    + "  if [ \"$prev\" = \"-o\" ]; then printf 'STUBKOFELF' > \"$a\"; exit 0; fi\n"
                    + "  prev=\"$a\"\ndone\nexit 0\n");
            sh.toFile().setExecutable(true);
        }
        Path src = writeApp(dir, "main() { println(\"x\") }\n");
        for (String t : new String[]{"native.riscv64", "native.aarch64"}) {
            CliResult r = runEnv(dir, java.util.Map.of("KOF_CROSS_PREFIX", stubs.toString()),
                    "deploy", src.toString(), "--target", t,
                    "--output", "dist-" + t, "--name", "xapp", "--version", "9.9.9");
            assertEquals(0, r.exit(), t + " deploy com stub deve empacotar: " + r.out());
            Path rel = dir.resolve("dist-" + t + "/deploy/xapp-9.9.9");
            Path bin = rel.resolve("xapp-9.9.9");
            assertTrue(Files.isRegularFile(bin), t + " artefato ELF ausente:\n" + r.out());
            assertTrue(Files.isExecutable(bin), t + " artefato deve ser executavel (0755 no tar)");
            String sums = Files.readString(rel.resolve("SHA256SUMS"));
            assertEquals(CmdDeploy.sha256Hex(bin) + "  xapp-9.9.9", sums.lines().findFirst().orElse(""), t + " checksum");
            String release = Files.readString(rel.resolve("RELEASE.md"));
            assertTrue(release.contains("./xapp-9.9.9"), t + " run hint deve ser ./binario: " + release);
            assertTrue(Files.isRegularFile(dir.resolve("dist-" + t + "/deploy/xapp-9.9.9.tar.gz")),
                    t + " tar.gz ausente");
        }
    }

    /** 8.4 fatia 4: multi-target da MESMA fonte — 3 releases + manifest SUCCESS. */
    @Test
    void multiTargetFromOneSource(@TempDir Path dir) throws Exception {
        Path src = writeApp(dir, "main() { println(\"multi ok\") }\n");
        CliResult r = run(dir, "deploy", src.toString(), "--target", "all",
                "--output", "dist", "--name", "uni", "--version", "0.1.0");
        assertEquals(0, r.exit(), "multi-deploy exit:\n" + r.out());
        Path dep = dir.resolve("dist").resolve("deploy");
        String manifest = Files.readString(dep.resolve("uni-0.1.0.deploy-manifest.json"));
        for (String t : new String[]{"jvm", "native", "kofjs"}) {
            assertTrue(Files.isDirectory(dep.resolve("uni-0.1.0-" + t)),
                    "release por alvo faltando: " + t + " — out:\n" + r.out());
            assertTrue(Files.isRegularFile(dep.resolve("uni-0.1.0-" + t + ".tar.gz")),
                    "tar.gz por alvo faltando: " + t);
            assertTrue(manifest.contains("\"target\": \"" + t + "\", \"status\": \"SUCCESS\""),
                    t + " deve constar SUCCESS no manifest:\n" + manifest);
        }
        assertTrue(r.out().contains("multi-deploy"), "deve anunciar o manifest:\n" + r.out());
    }

    /** 8.4: alvo que falha não derruba os outros — exit 1 + FAIL com razão honesta. */
    @Test
    void multiTargetPartialFailureIsHonest(@TempDir Path dir) throws Exception {
        Path src = writeApp(dir, "main() { println(\"x\") }\n");
        // X9 fatia 6: o cross TENTOU (não é mais recusa preventiva). Falha forcada
        // de forma DETERMINISTICA em qualquer host (inclusive CI com toolchain real):
        // KOF_CROSS_PREFIX apontando para pasta sem ferramentas.
        CliResult r = runEnv(dir, java.util.Map.of("KOF_CROSS_PREFIX", dir.resolve("emptybin").toString()),
                "deploy", src.toString(), "--target", "jvm,native.risc",
                "--output", "dist", "--name", "par", "--version", "1.0");
        assertEquals(1, r.exit(), "com falha o exit é 1:\n" + r.out());
        assertTrue(Files.isDirectory(dir.resolve("dist").resolve("deploy").resolve("par-1.0-jvm")),
                "jvm não pode ser contaminado pela falha do risc:\n" + r.out());
        String manifest = Files.readString(dir.resolve("dist").resolve("deploy")
                .resolve("par-1.0.deploy-manifest.json"));
        assertTrue(manifest.contains("\"target\": \"jvm\", \"status\": \"SUCCESS\""), manifest);
        assertTrue(manifest.contains("\"error\"") && manifest.contains("binary not found"),
                "risc deve entrar FAIL com a causa real (toolchain), nunca DEP001 preventivo:\n" + manifest);
    }

    /** 8.4: lista com repetição deduplica vira single — layout legado sem sufixo preservado. */
    @Test
    void duplicateListDedupesToLegacyLayout(@TempDir Path dir) throws Exception {
        Path src = writeApp(dir, "main() { println(\"legado\") }\n");
        CliResult r = run(dir, "deploy", src.toString(), "--target", "jvm,jvm",
                "--output", "dist", "--name", "one", "--version", "2.0");
        assertEquals(0, r.exit(), "jvm,jvm dedupeia para um deploy:\n" + r.out());
        assertTrue(Files.isDirectory(dir.resolve("dist").resolve("deploy").resolve("one-2.0")),
                "um alvo usa o nome histórico sem sufixo:\n" + r.out());
        assertFalse(Files.exists(dir.resolve("dist").resolve("deploy")
                .resolve("one-2.0.deploy-manifest.json")), "single não gera manifest de multi");
        assertFalse(r.out().contains("multi-deploy"));
    }

    /** X9 fatia 3: face ANDROID — sem SDK, recusa honesta (não fake-success). */
    @Test
    void androidWithoutSdkIsHonestFailure(@TempDir Path dir) throws Exception {
        Path src = writeApp(dir, "main() { println(\"x\") }\n");
        ProcessBuilder pb = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), "dev.kof.cli.Main",
                "deploy", src.toString(), "--target", "android",
                "--output", "dist", "--name", "app", "--version", "1.0.0");
        pb.environment().remove("ANDROID_HOME");
        pb.directory(dir.toFile()).redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertNotEquals(0, p.waitFor(120, TimeUnit.SECONDS),
                "sem ANDROID_HOME o deploy não pode fingir sucesso (R6):\n" + out);
        assertTrue(out.contains("ANDROID_HOME") || out.contains("APK pipeline failed"),
                "mensagem deve apontar a causa:\n" + out);
    }

    /** X9 fatia 3: COM SDK válido, o APK empacotado existe + checksum confere.
     *  Guard honesto: sem build-tools 34.0.0 o bloco dá skip (ambiente). */
    @Test
    void androidWithSdkPackagesApk(@TempDir Path dir) throws Exception {
        String androidHome = System.getenv("ANDROID_HOME");
        Assumptions.assumeTrue(androidHome != null && !androidHome.isBlank(),
                "ANDROID_HOME ausente — face android do deploy é validada no host com SDK");
        Path bt = ApkToolchain.pickBuildTools(Path.of(androidHome));
        Assumptions.assumeTrue(bt != null && ApkToolchain.buildToolsSupportsJava21(bt),
                "android e2e exige build-tools >= 35 (d8 le class major 65); pin 34 falhava no dex"
                        + " — skip honesto, a recusa com diagnostico e coberta por CmdBuildApkToolchainTest");
        Path src = writeApp(dir, "main() { println(\"x\") }\n");
        CliResult r = run(dir, "deploy", src.toString(), "--target", "android",
                "--output", "dist", "--name", "app", "--version", "1.0.0");
        assertEquals(0, r.exit(), "deploy android, saída:\n" + r.out());
        Path apk = dir.resolve("dist/deploy/app-1.0.0/app-1.0.0.apk");
        assertTrue(Files.isRegularFile(apk), "APK ausente:\n" + r.out());
        String sums = Files.readString(dir.resolve("dist/deploy/app-1.0.0/SHA256SUMS"),
                StandardCharsets.UTF_8);
        assertEquals(CmdDeploy.sha256Hex(apk) + "  app-1.0.0.apk", sums.lines().findFirst().orElse(""));
    }

    @Test
    void unknownFlagNeverSilent(@TempDir Path dir) throws Exception {
        Path src = writeApp(dir, "main() { println(\"x\") }\n");
        CliResult r = run(dir, "deploy", src.toString(), "--target", "jvm", "--fat");
        assertEquals(1, r.exit(), "flag estranha deve recusar (R6): " + r.out());
        assertTrue(r.out().contains("unknown or incomplete flag"), r.out());
    }

    // ── §298 node helpers ──



    // ── tar helpers ──


    /** Lê o header (nome ustar + size octal); o chamador decide pular o payload. */

    /** Pula payload + padding (512-aligned) do entry cujo header já foi consumido. */


}
