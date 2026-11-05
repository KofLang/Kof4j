package dev.kof.cli;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Package manager MVP (TIER 1.4) — {@code kof deps}.
 *
 * <p>Gerencia o arquivo {@code kofdeps} (uma dependência Maven por linha,
 * {@code group:artifact:version}) e resolve para o cache local
 * {@code ~/.kof/deps}. A resolução é honesta: baixa o jar e o POM direto;
 * dependências transitivas ainda não são resolvidas (reportado, nunca
 * silencioso). O classpath resolvido é consumido por {@code kof build}
 * {@code --deps} e {@code kof run --deps}.</p>
 *
 * <p>Exemplo:</p>
 * <pre>{@code
 * kof deps init
 * kof deps add com.h2database:h2:2.2.224
 * kof deps list
 * kof deps resolve
 * kof run --deps main.kf
 * }</pre>
 */
final class Deps {

    private Deps() {}

    static final String DEPS_FILE = "kofdeps";
    static final String LOCK_FILE = "kofdeps.lock";
    private static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2/";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    static int run(String[] args) {
        if (args.length < 2 || "--help".equals(args[1]) || "-h".equals(args[1])) {
            System.out.println("usage: kof deps <init|add|remove|list|resolve>");
            System.out.println("  init [dir]                 create an empty kofdeps file");
            System.out.println("  add <g:a:v> [dir]          append a dependency (Maven g:a:v)");
            System.out.println("  remove <g:a:v> [dir]       remove a dependency");
            System.out.println("  list [dir]                 list declared dependencies");
            System.out.println("  resolve [dir]              download jars into ~/.kof/deps and print classpath");
            System.out.println();
            System.out.println("dependencies: one per line, group:artifact:version (Maven Central)");
            return 0;
        }
        try {
            return switch (args[1]) {
                case "init" -> init(args);
                case "add" -> add(args);
                case "remove" -> remove(args);
                case "list" -> list(args);
                case "resolve" -> resolve(args);
                default -> {
                    System.err.println("kof deps: unknown subcommand '" + args[1] + "'");
                    yield 1;
                }
            };
        } catch (IOException e) {
            System.err.println("kof deps: " + e.getMessage());
            return 1;
        }
    }

    private static Path depsFile(String[] args) {
        // args = [deps, subcomando, ...]. O diretório é:
        //   init/list/resolve: args[2] se existir, senão "."
        //   add/remove: a dependência é args[2]; o diretório é args[3] se
        //   existir, senão "."
        String dir;
        String sub = args[1];
        if ("add".equals(sub) || "remove".equals(sub)) {
            dir = args.length > 3 ? args[3] : ".";
        } else {
            dir = args.length > 2 ? args[2] : ".";
        }
        return Path.of(dir).resolve(DEPS_FILE);
    }

    private static int init(String[] args) throws IOException {
        Path file = depsFile(args);
        if (Files.exists(file)) {
            System.err.println("deps: " + file + " already exists");
            return 1;
        }
        Files.writeString(file, "");
        System.out.println("created " + file + " (empty) — add with: kof deps add <g:a:v>");
        return 0;
    }

    private static int add(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("usage: kof deps add <group:artifact:version> | <owner/repo[@version]> [dir]");
            return 1;
        }
        String dep = normalize(args[2]);
        String kof = dep == null ? DepsRegistry.normalize(args[2]) : null;
        if (kof != null) dep = kof;
        if (dep == null) {
            System.err.println("deps: expected format group:artifact:version (e.g. com.h2database:h2:2.2.224)");
            return 1;
        }
        Path file = depsFile(args);
        if (!Files.exists(file)) Files.writeString(file, "");
        List<String> lines = new ArrayList<>(Files.readAllLines(file));
        if (lines.contains(dep)) {
            System.out.println("already declared: " + dep);
            return 0;
        }
        lines.add(dep);
        Files.write(file, lines);
        System.out.println("added: " + dep);
        return 0;
    }

    private static int remove(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("usage: kof deps remove <group:artifact:version> [dir]");
            return 1;
        }
        Path file = depsFile(args);
        if (!Files.exists(file)) {
            System.err.println("deps: " + file + " does not exist");
            return 1;
        }
        List<String> lines = new ArrayList<>(Files.readAllLines(file));
        boolean removed = lines.remove(args[2]);
        Files.write(file, lines);
        if (removed) {
            System.out.println("removed: " + args[2]);
        } else {
            System.err.println("deps: '" + args[2] + "' not declared");
            return 1;
        }
        return 0;
    }

    private static int list(String[] args) throws IOException {
        Path file = depsFile(args);
        if (!Files.exists(file)) {
            System.err.println("deps: " + file + " does not exist (run: kof deps init)");
            return 1;
        }
        List<String> lines = Files.readAllLines(file);
        if (lines.isEmpty()) {
            System.out.println("(no dependency declared in " + file + ")");
        } else {
            for (String l : lines) {
                if (!l.isBlank()) System.out.println(l);
            }
        }
        return 0;
    }

    private static int resolve(String[] args) throws IOException {
        Path file = depsFile(args);
        if (!Files.exists(file)) {
            System.err.println("deps: " + file + " does not exist (run: kof deps init)");
            return 1;
        }
        List<String> lines = Files.readAllLines(file);
        List<String> missing = new ArrayList<>();
        boolean declaredAny = false;
        boolean pinned = false;
        java.util.Map<String, String> pins = new java.util.LinkedHashMap<>();
        for (String l : lines) {
            if (l.isBlank()) continue;
            declaredAny = true;
            try {
                if (DepsRegistry.isRegistrySpec(l)) {
                    String spec = DepsRegistry.normalize(l);
                    java.nio.file.Path j = DepsRegistry.fetch(spec);
                    if (DepsRegistry.versionOf(spec) == null) {
                        // latest = pin de versão concreto no kofdeps (lock-estável)
                        pinned = true;
                        pins.put(l, spec + "@" + j.getParent().getFileName());
                    }
                    continue;
                }
                String[] ga = l.split(":");
                if (ga.length != 3) {
                    missing.add(l + " (invalid format)");
                    continue;
                }
                download(ga[0], ga[1], ga[2]);
            } catch (Exception e) {
                missing.add(l + " → " + e.getMessage());
            }
        }
        if (!missing.isEmpty()) {
            System.err.println("deps: incomplete resolution:");
            for (String m : missing) System.err.println("  " + m);
            return 1;
        }
        if (pinned) {
            List<String> rewritten = new ArrayList<>();
            for (String l : lines) {
                String pin = pins.get(l.trim());
                rewritten.add(pin != null ? pin : l);
            }
            Files.write(file, rewritten);
            lines = rewritten;
        }
        // TIER 1.4 — transitivos via Maven (roadmap §"package manager":
        // "generate a temporary pom.xml and use Maven"; regra da plataforma:
        // resolução de grafo Maven JÁ EXISTE fora — R9, nunca reimplementar).
        // O fecho vai p/ kofdeps.lock; sem `mvn` no PATH o resolve fica
        // NOBRE mas HONESTO: sem lock, warning explícito (R6, nunca silencio).
        if (declaredAny) {
            if (mvnAvailable()) {
                try {
                    Files.write(file.getParent().resolve(LOCK_FILE), mvnClosure(file.getParent(), lines));
                } catch (Exception e) {
                    System.err.println("deps: transitive resolution failed (" + e.getMessage()
                            + "); classpath keeps ONLY the direct deps (no kofdeps.lock)");
                }
            } else {
                System.err.println("deps: `mvn` is not on PATH — TRANSITIVE dependencies"
                        + " unresolved (kofdeps.lock missing; install Maven to close the graph)");
            }
        }
        System.out.println(classpath(file.getParent()));
        return 0;
    }

    /** Normaliza "g:a:v" ou devolve null se o formato for inválido. */
    static String normalize(String dep) {
        String[] parts = dep.split(":");
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            return null;
        }
        return parts[0] + ":" + parts[1] + ":" + parts[2];
    }

    /** Seam de teste: permite apontar p/ um `mvn` fake (propriedade de sistema
     *  {@code kof.mvn}); produção usa o {@code mvn} do PATH. */
    static String mvnExecutable() { return System.getProperty("kof.mvn", "mvn"); }

    /** Gera o pom.xml temporâneo (roadmap §"package manager": "generate a
     *  temporary pom.xml and use Maven") a partir das linhas do kofdeps. Puro
     *  e determinístico — testável sem rede. */
    static String generatePomXml(List<String> depsLines) {
        StringBuilder sb = new StringBuilder();
        sb.append("<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n")
          .append("  <modelVersion>4.0.0</modelVersion>\n")
          .append("  <groupId>dev.kof.deps</groupId>\n")
          .append("  <artifactId>kofdeps-resolve</artifactId>\n")
          .append("  <version>1</version>\n")
          .append("  <packaging>pom</packaging>\n");
        List<String> gav = new ArrayList<>();
        for (String l : depsLines) {
            if (l == null || l.isBlank()) continue;
            String[] g = l.trim().split(":");
            if (g.length != 3) continue;
            gav.add("    <dependency><groupId>" + xmlEsc(g[0]) + "</groupId><artifactId>"
                    + xmlEsc(g[1]) + "</artifactId><version>" + xmlEsc(g[2]) + "</version></dependency>\n");
        }
        if (!gav.isEmpty()) {
            sb.append("  <dependencies>\n");
            for (String d : gav) sb.append(d);
            sb.append("  </dependencies>\n");
        }
        sb.append("</project>\n");
        return sb.toString();
    }

    private static String xmlEsc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /** `true` se houver um `mvn` executável no PATH (ou o seam de teste). */
    static boolean mvnAvailable() {
        try {
            return new ProcessBuilder(mvnExecutable(), "-v").redirectErrorStream(true)
                    .start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Fecha o grafo via Maven: pom temporâneo + `build-classpath` (resolve no
     *  `~/.m2` padrão — offline quando o fecho já está em cache, senão baixa),
     *  e COPIA só os jars do fecho para o cache do kofdeps (layout Maven
     *  idêntico, mas sem poluir `~/.kof/deps` com a árvore de plugins do Maven).
     *  Devolve os GAVs do fecho (matéria-prima do kofdeps.lock). Lança se o mvn
     *  falhar (chamador degrada com aviso honesto, R6). */
    static List<String> mvnClosure(Path depsDir, List<String> depsLines) throws IOException {
        Path tmp = Files.createTempDirectory("kofdeps-resolve");
        try {
            Path pom = tmp.resolve("pom.xml");
            Files.writeString(pom, generatePomXml(depsLines));
            Path cpFile = tmp.resolve("cp.txt");
            List<String> cmd = new ArrayList<>(List.of(
                    mvnExecutable(), "-B", "-q",
                    "org.apache.maven.plugins:maven-dependency-plugin:3.6.1:build-classpath",
                    "-Dmdep.outputFile=" + cpFile,
                    "-Dmdep.includeScope=runtime",
                    "-f", pom.toString()));
            Process p = new ProcessBuilder(cmd).directory(depsDir.toFile())
                    .redirectErrorStream(true).start();
            String log = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (p.waitFor() != 0 || !Files.exists(cpFile)) {
                throw new IOException("mvn " + (log.isBlank() ? "no output"
                        : log.substring(0, Math.min(log.length(), 400))));
            }
            String sep = System.getProperty("os.name", "").toLowerCase().contains("win") ? ";" : ":";
            String content = Files.readString(cpFile).trim();
            List<String> gavs = new ArrayList<>();
            if (!content.isEmpty()) for (String e : content.split(java.util.regex.Pattern.quote(sep))) {
                if (e.isBlank()) continue;
                String gav = copyToCache(Path.of(e));
                if (gav != null) gavs.add(gav);
            }
            return gavs;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("mvn interrompido", e);
        } finally {
            try (var w = Files.walk(tmp)) { w.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(x -> { try { Files.deleteIfExists(x); } catch (IOException ignored) {} }); }
            catch (IOException ignored) {}
        }
    }

    /** Copia um jar de `~/.m2` (layout group/artifact/version/file.jar) para o
     *  cache do kofdeps e devolve o GAV; null se o caminho não tiver o layout. */
    static String copyToCache(Path m2Jar) throws IOException {
        Path root = Path.of(System.getProperty("maven.repo.local",
                System.getProperty("user.home", ".") + "/.m2/repository"));
        Path rel;
        try {
            rel = root.toAbsolutePath().relativize(m2Jar.toAbsolutePath());
        } catch (IllegalArgumentException e) {
            return null;
        }
        int n = rel.getNameCount();
        if (n < 4) return null;
        if (rel.getName(0).toString().equals("..")) return null;   // fora do repositório
        String version = rel.getName(n - 2).toString();
        String artifact = rel.getName(n - 3).toString();
        StringBuilder g = new StringBuilder();
        for (int i = 0; i < n - 3; i++) g.append(i > 0 ? "." : "").append(rel.getName(i));
        Path dest = jarPath(g.toString(), artifact, version);
        if (!Files.exists(dest)) {
            Files.createDirectories(dest.getParent());
            Files.copy(m2Jar, dest, StandardCopyOption.REPLACE_EXISTING);
        }
        return g + ":" + artifact + ":" + version;
    }

    static Path cacheDir() {
        String seam = System.getProperty("kof.deps.home");
        if (seam != null && !seam.isBlank()) return Path.of(seam);
        String home = System.getProperty("user.home", ".");
        return Path.of(home, ".kof", "deps");
    }

    static Path jarPath(String group, String artifact, String version) {
        Path dir = cacheDir().resolve(group.replace('.', '/')).resolve(artifact).resolve(version);
        return dir.resolve(artifact + "-" + version + ".jar");
    }

    /** Seam de teste/air-gap: propriedade {@code kof.maven.central} aponta a
     *  base de download p/ outro repo (ex. `file:///srv/mirror/`); produção
     *  usa o Maven Central. */
    static String mavenBase() {
        String seam = System.getProperty("kof.maven.central");
        if (seam != null && !seam.isBlank()) return seam.endsWith("/") ? seam : seam + "/";
        return MAVEN_CENTRAL;
    }

    static void download(String group, String artifact, String version) throws IOException {
        Path jar = jarPath(group, artifact, version);
        if (Files.exists(jar)) return;
        Files.createDirectories(jar.getParent());
        String rel = group.replace('.', '/') + "/" + artifact + "/"
                + version + "/" + artifact + "-" + version + ".jar";
        String base = mavenBase();
        if (base.startsWith("file:")) {
            Path src;
            try {
                src = Path.of(URI.create(base + rel));
            } catch (Exception e) {
                throw new IOException("file repo inválido: " + base + rel);
            }
            try {
                Files.copy(src, jar, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                Files.deleteIfExists(jar);
                throw new IOException("file repo " + src + ": " + e.getMessage());
            }
            System.out.println("baixado " + group + ":" + artifact + ":" + version);
            return;
        }
        String url = base + rel;
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        try {
            HttpResponse<Path> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofFile(jar));
            if (resp.statusCode() != 200) {
                Files.deleteIfExists(jar);
                throw new IOException("HTTP " + resp.statusCode() + " for " + url);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrompido", e);
        }
        System.out.println("baixado " + group + ":" + artifact + ":" + version);
    }

    /** The resolved classpath as entries, the form {@code CompilerDriver.setExternalClasspath} takes. */
    static List<Path> classpathEntries() throws IOException {
        List<Path> entries = new ArrayList<>();
        for (String part : CmdBuild.splitClasspathEntries(classpath())) entries.add(Path.of(part));
        return entries;
    }

    /** Classpath resolvido (jars no cache), separado por ':' (ou ';' no Windows). */
    static String classpath() throws IOException {
        return classpath(Path.of("."));
    }

    static String classpath(Path projectDir) throws IOException {
        String sep = System.getProperty("os.name", "").toLowerCase().contains("win") ? ";" : ":";
        StringBuilder sb = new StringBuilder();
        Set<String> seen = new LinkedHashSet<>();
        // fonte de GAVs: lock (fecho transitivo) se existir, senão o próprio
        // kofdeps (só diretas — comportamento MVP, com warning do resolve).
        Path lock = projectDir.resolve(LOCK_FILE);
        if (!Files.exists(projectDir.resolve(DEPS_FILE))) return "";
        List<String> declared = Files.readAllLines(projectDir.resolve(DEPS_FILE));
        List<String> sources = new ArrayList<>();
        if (Files.exists(lock)) {
            sources.addAll(Files.readAllLines(lock));
            // linhas kof (owner/repo[@ver]) nunca vao p/ o lock Maven — sempre das diretas
            for (String l : declared) {
                String s = l == null ? "" : l.trim();
                if (!s.isEmpty() && DepsRegistry.isRegistrySpec(s)) sources.add(s);
            }
        } else {
            // MVP sem lock: diretas do kofdeps (maven GAVs + specs kof)
            sources.addAll(declared);
        }
        for (String l : sources) {
            if (l == null || l.isBlank()) continue;
            Path jar;
            if (DepsRegistry.isRegistrySpec(l.trim())) {
                String spec = DepsRegistry.normalize(l.trim());
                String ver = DepsRegistry.versionOf(spec);
                if (ver == null) continue;                  // latest sem resolve previo: honesto
                jar = DepsRegistry.jarPath(DepsRegistry.ownerOf(spec),
                        DepsRegistry.repoOf(spec), ver);
                // #566 (b): pacote com fontes e consumido como MODULO-FONTE (DepsSources.roots), nao
                // pelo jar — o jar no classpath duplicaria as classes que o compilador ja emite.
                if (DepsSources.hasSources(jar.getParent())) continue;
            } else {
                String[] ga = l.trim().split(":");
                if (ga.length != 3) continue;
                jar = jarPath(ga[0], ga[1], ga[2]);
            }
            if (!Files.exists(jar)) continue;               // direto: sem baixa no classpath
            if (seen.add(jar.toString())) {
                if (sb.length() > 0) sb.append(sep);
                sb.append(jar);
            }
        }
        return sb.toString();
    }
}