package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D2-A (D-POLL-19 19/09): a face --publish do {@code kof deploy} — a recusa
 * honesta sem token (R6), a release criada + tar.gz subido (fake server,
 * zero rede), a tag existente reusada no 422, o endpoint inacessível falhando
 * honestamente após o pacote local, e o parser do --publish (owner/repo, URL,
 * URL .git, git@, lixo). Vive fora do {@code CmdDeployTest} para mantê-lo
 * abaixo do limite de 500 linhas de teste (Fase 3 da arquitetura de testes,
 * {@code D-TEST-ARCHITECTURE-GO}); os helpers vêm do {@code CmdDeploySupport}.
 */
class CmdDeployPublishTest extends CmdDeploySupport {

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

}
