package dev.kof.cli;

import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Harness do registry PULL (`DepsRegistryTest`) — o fake server com o SHAPE REAL
 * da API de Releases do GitHub (#564), o pacote D2-A e o runner de subprocesso
 * CLI. Vivem fora da classe de teste para mantê-la abaixo do limite de 500
 * linhas (Fase 3 da arquitetura de testes, {@code D-TEST-ARCHITECTURE-GO}); os
 * testes e o nome da classe seguem em {@link DepsRegistryTest} — zero drift de
 * citação (o harness é herdado, então os {@code import static
 * dev.kof.cli.DepsRegistryTest.*} das classes vizinhas continuam resolvendo).
 */
abstract class DepsRegistrySupport {

    static CliResult runWithEnv(Path workDir, Map<String, String> env, String... cliArgs)
            throws Exception {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("-Duser.home=" + env.get("HOMEOF"));
        cmd.add("dev.kof.cli.Main");
        cmd.addAll(java.util.List.of(cliArgs));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir.toFile()).redirectErrorStream(true);
        pb.environment().put("GH_TOKEN", "");        // hermetico: nunca herda/vaza token real
        pb.environment().put("GITHUB_TOKEN", "");
        env.forEach(pb.environment()::put);
        pb.environment().remove("HOMEOF");
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor(180, java.util.concurrent.TimeUnit.SECONDS);
        return new CliResult(p.exitValue(), out);
    }

    record CliResult(int exit, String out) {}

    /** Release D2-A real: jar valido + RELEASE.md + SHA256SUMS + tar.gz do proprio writer. */
    static byte[] buildPackage(Path dir, String repo, String version,
                                       boolean withJar, boolean goodSums, boolean withSums)
            throws Exception {
        Files.createDirectories(dir);
        Path jar = dir.resolve(repo + "-" + version + ".jar");
        if (withJar) {
            try (JarOutputStream jo = new JarOutputStream(Files.newOutputStream(jar))) {
                jo.putNextEntry(new JarEntry("Hello.txt"));
                jo.write("hello-kof".getBytes(StandardCharsets.UTF_8));
                jo.closeEntry();
            }
        } else {
            Files.writeString(dir.resolve("README.txt"), "sem jar");
        }
        Files.writeString(dir.resolve("RELEASE.md"), "# " + repo + " " + version + "\n");
        if (withSums) {
            String sha = (goodSums && withJar)
                    ? CmdDeploy.sha256Hex(jar)
                    : "0".repeat(64);
            Files.writeString(dir.resolve("SHA256SUMS"),
                    sha + "  " + jar.getFileName() + "\n");
        }
        Path tgz = dir.resolve(repo + "-" + version + ".tar.gz");
        List<Path> files = new java.util.ArrayList<>();
        files.add(withJar ? jar.getFileName() : Path.of("README.txt"));
        files.add(Path.of("RELEASE.md"));
        if (withSums) files.add(Path.of("SHA256SUMS"));
        CmdDeploy.writeTarGz(tgz, dir, files, 0644);
        return Files.readAllBytes(tgz);
    }

    // ---- fake registry com o SHAPE REAL da API de Releases do GitHub (#564) ----
    // O fixture antigo era um asset plano {"name","download_url"} — formato que a
    // API real nunca devolve; por isso o pull ficou verde só contra o mock. O real
    // tem `uploader{...}` aninhado (com `url` proprio) ANTES de `browser_download_url`,
    // sem `download_url`, e o binario se baixa pelo `url` do asset (API) com
    // `Accept: application/octet-stream`.

    /** Asset servido pelo fake: id na API + nome publicado. */
    record FakeAsset(long id, String name) {}

    /** Ordem dos campos do asset: a do GitHub, ou invertida (o parser nao pode depender dela). */
    enum Order { GITHUB, REVERSED }

    /** Requisicoes vistas por cada fake (headers relevantes) — prova de contrato HTTP. */
    private static final Map<HttpServer, List<String>> SEEN =
            new java.util.concurrent.ConcurrentHashMap<>();

    static List<String> seen(HttpServer s) { return SEEN.get(s); }

    private static String hdr(com.sun.net.httpserver.HttpExchange ex, String name) {
        String v = ex.getRequestHeaders().getFirst(name);
        return v == null ? "-" : v;
    }

    /** Release no formato real (chaves e aninhamento medidos na API oficial). */
    private static String githubReleaseJson(String addr, String repo, String version,
                                            List<FakeAsset> assets, Order order) {
        String user = "{\"login\":\"octocat\",\"id\":1,\"node_id\":\"MDQ6VXNlcjE=\","
                + "\"url\":\"https://api.github.com/users/octocat\",\"type\":\"User\"}";
        StringBuilder as = new StringBuilder();
        for (FakeAsset a : assets) {
            java.util.LinkedHashMap<String, String> f = new java.util.LinkedHashMap<>();
            f.put("url", "\"" + addr + "/repos/acme/" + repo + "/releases/assets/" + a.id() + "\"");
            f.put("id", String.valueOf(a.id()));
            f.put("node_id", "\"RA_kwDOx\"");
            f.put("name", "\"" + a.name() + "\"");
            // delimitadores e aspas DENTRO de string (T3): scanner artesanal se desalinha
            f.put("label", "\"texto com } e \\\"aspas\\\" e ] dentro\"");
            f.put("uploader", user);
            f.put("content_type", "\"application/octet-stream\"");
            f.put("state", "\"uploaded\"");
            f.put("size", "1920");
            f.put("digest", "\"sha256:" + "0".repeat(64) + "\"");
            f.put("browser_download_url", "\"" + addr + "/browser/" + a.name() + "\"");
            List<String> keys = new java.util.ArrayList<>(f.keySet());
            if (order == Order.REVERSED) java.util.Collections.reverse(keys);
            StringBuilder one = new StringBuilder("{");
            for (String k : keys) {
                if (one.length() > 1) one.append(',');
                one.append('"').append(k).append("\":").append(f.get(k));
            }
            if (as.length() > 0) as.append(',');
            as.append(one).append('}');
        }
        // `author` (objeto aninhado) vem ANTES de `tag_name`, como na API real
        return "{\"url\":\"" + addr + "/repos/acme/" + repo + "/releases/1\",\"id\":1,"
                + "\"author\":" + user + ",\"tag_name\":\"" + repo + "-" + version + "\","
                + "\"name\":\"" + repo + " " + version + "\",\"draft\":false,\"assets\":["
                + as + "]}";
    }

    static HttpServer serveFakeRegistry(Path dir, String repo, String version,
                                                byte[] tgz, boolean found) throws Exception {
        return serveFakeRegistry(repo, version, tgz, found,
                List.of(new FakeAsset(123, repo + "-" + version + ".tar.gz")),
                Order.GITHUB, false, false);
    }

    static HttpServer serveFakeRegistry(String repo, String version, byte[] tgz,
                                                boolean found, List<FakeAsset> assets,
                                                Order order, boolean viaRedirect,
                                                boolean malformedRelease) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        String addr = "http://127.0.0.1:" + port;
        String cdn = "http://localhost:" + port;   // OUTRO host: o token nao pode segui-lo
        List<String> log = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        SEEN.put(server, log);
        String json = malformedRelease ? "{\"tag_name\":"
                : githubReleaseJson(addr, repo, version, assets, order);
        String meta = "/repos/acme/" + repo + "/releases/";
        server.createContext("/", ex -> {
            String p = ex.getRequestURI().getPath();
            String common = "accept=" + hdr(ex, "Accept") + "|auth=" + hdr(ex, "Authorization")
                    + "|ua=" + hdr(ex, "User-Agent") + "|ver=" + hdr(ex, "X-GitHub-Api-Version");
            try {
                if (found && (p.startsWith(meta + "tags/") || p.startsWith(meta + "latest"))) {
                    log.add("META|" + common);
                    byte[] b = json.getBytes(StandardCharsets.UTF_8);
                    ex.sendResponseHeaders(200, b.length);
                    try (OutputStream os = ex.getResponseBody()) { os.write(b); }
                } else if (found && p.startsWith(meta + "assets/")) {
                    String id = p.substring((meta + "assets/").length());
                    log.add("ASSET|" + id + "|" + common);
                    if (!"application/octet-stream".equals(ex.getRequestHeaders().getFirst("Accept"))) {
                        // sem octet-stream a API real devolve o JSON do asset, nao o binario
                        byte[] b = ("{\"id\":" + id + "}").getBytes(StandardCharsets.UTF_8);
                        ex.sendResponseHeaders(200, b.length);
                        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
                    } else if (viaRedirect) {
                        ex.getResponseHeaders().add("Location", cdn + "/cdn/" + id);
                        ex.sendResponseHeaders(302, -1);
                    } else {
                        ex.sendResponseHeaders(200, tgz.length);
                        try (OutputStream os = ex.getResponseBody()) { os.write(tgz); }
                    }
                } else if (found && p.startsWith("/cdn/")) {
                    log.add("CDN|" + p.substring(5) + "|" + common);
                    ex.sendResponseHeaders(200, tgz.length);
                    try (OutputStream os = ex.getResponseBody()) { os.write(tgz); }
                } else {
                    log.add("OTHER|" + p);   // inclui /browser/...: o cliente de API nao o usa
                    ex.sendResponseHeaders(404, -1);
                }
            } catch (Exception e) {
                ex.sendResponseHeaders(500, -1);
            } finally {
                ex.close();
            }
        });
        server.start();
        return server;
    }

    static Map<String, String> envOf(HttpServer server, Path fakeHome) {
        return Map.of(
                "KOF_REGISTRY_API", "http://127.0.0.1:" + server.getAddress().getPort(),
                "HOMEOF", fakeHome.toString());
    }

    /** Uma resolve limpa (add + resolve) contra o fake; devolve o CliResult do resolve. */
    static CliResult pull(Path tmp, HttpServer server, String spec,
                                  Map<String, String> extraEnv) throws Exception {
        Path proj = tmp.resolve("proj");
        Files.createDirectories(proj);
        java.util.HashMap<String, String> env = new java.util.HashMap<>(
                envOf(server, tmp.resolve("home")));
        env.putAll(extraEnv);
        CliResult a = runWithEnv(proj, env, "deps", "add", spec);
        assertEquals(0, a.exit(), "add:\n" + a.out());
        return runWithEnv(proj, env, "deps", "resolve");
    }

    static boolean jarInstalled(Path tmp, String version) {
        return Files.exists(tmp.resolve("home/.kof/deps/kof/acme/hello/" + version
                + "/hello-" + version + ".jar"));
    }
}
