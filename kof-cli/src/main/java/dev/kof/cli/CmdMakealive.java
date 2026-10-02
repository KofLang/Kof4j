package dev.kof.cli;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Diagnostic;
import dev.kof.compiler.Target;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * `kof makealive &lt;plan|apply|destroy&gt; &lt;file.kf&gt;` — tooling da linha 3.8
 * (plano Kof Makealive; contrato D-MAKEALIVE-CLI, decisao do maintainer
 * 20/09 via "propor + implementar"). A regra 6 de 2.6 vale igual: o runner
 * nao inventa gramatica nova — o arquivo e Kof puro (`import kof.makealive`,
 * `design(): Infrastructure`, `provider(): Provider`, SEM `main()`), o CLI
 * sintetiza um main que fala com o host injetado e comunica pelo protocolo de
 * linha marcada `@@KOF_MAKEALIVE@@ `; a saida humana/JSON e formatada AQUI.
 *
 * <p>Estado: h2 file via `--state PATH` (default `&lt;file&gt;.makealive`),
 * persistido pelas faces `mkLoadState`/`mkSaveState`/`mkMaxGen` (geracao
 * SEMPRE max+1 — update = nova geracao, precedente SEM038). `plan` nunca
 * escreve. JVM+JS com paridade de bytes (R7); script/native = follow-up
 * honesto (Native carrega o stub ORM001 nas faces de estado).
 */
final class CmdMakealive {

    private static final String MARK = "@@KOF_MAKEALIVE@@ ";

    private static final String USAGE =
            "usage: kof makealive <plan|apply|destroy> <file.kf> [--state PATH] [--json] [--target jvm|js]\n"
            + "  a design file imports kof.makealive and defines design(): Infrastructure and\n"
            + "  provider(): Provider (no main() -- the tool synthesizes the runner's main)\n"
            + "  plan      show the pure diff desired-vs-saved (never writes)\n"
            + "  apply     create/update what plan says; save a new generation on success\n"
            + "  destroy   remove every saved resource (reverse order); state becomes empty\n"
            + "  --state PATH   h2 state file (default: <file>.makealive)\n"
            + "  --json         machine-readable output";

    private CmdMakealive() {
    }

    static int run(String[] args) {
        if (args.length < 2) { System.err.println(USAGE); return 1; }
        if (args[1].equals("--help") || args[1].equals("-h")) { System.out.println(USAGE); return 0; }
        String op = args[1];
        if (!op.equals("plan") && !op.equals("apply") && !op.equals("destroy")) {
            System.err.println("makealive: unknown subcommand: " + op + " (expected plan|apply|destroy)");
            return 1;
        }
        if (args.length < 3) { System.err.println(USAGE); return 1; }
        Path file = Path.of(args[2]);
        boolean json = false;
        String state = null;
        Target target = Target.JVM;
        for (int i = 3; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--json")) {
                json = true;
            } else if (a.equals("--state") && i + 1 < args.length) {
                state = args[++i];
            } else if (a.startsWith("--state=")) {
                state = a.substring("--state=".length());
            } else if (a.equals("--target") && i + 1 < args.length) {
                target = KofCliSupport.parseTarget(args[++i]);
            } else if (a.startsWith("--target=")) {
                target = KofCliSupport.parseTarget(a.substring("--target=".length()));
            } else {
                System.err.println("makealive: unknown or incomplete flag: " + a);
                return 1;
            }
        }
        if (target != Target.JVM && target != Target.JS) {
            System.err.println("makealive: target not supported yet (R7: JVM first; script/native are follow-up)");
            return 1;
        }
        if (!Files.exists(file)) { System.err.println("not found: " + file); return 1; }

        String source;
        try {
            source = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("makealive: cannot read " + file + ": " + e.getMessage());
            return 1;
        }
        // R6: falha cedo com a razao, nunca compila "por acaso".
        if (!Pattern.compile("(?m)^\\s*import\\s+kof\\.makealive\\b").matcher(source).find()) {
            System.err.println("makealive: " + file + " does not import kof.makealive"
                    + " (a design file imports it and defines design() + provider())");
            return 1;
        }
        if (!Pattern.compile("\\bdesign\\s*\\(").matcher(source).find()) {
            System.err.println("makealive: " + file + " does not define design()"
                    + " (the runner synthesizes main(); the file must define"
                    + " design(): Infrastructure and provider(): Provider and must not define main())");
            return 1;
        }
        if (!Pattern.compile("\\bprovider\\s*\\(").matcher(source).find()) {
            System.err.println("makealive: " + file + " does not define provider()"
                    + " (apply/destroy need a Provider; plan does not -- but the contract"
                    + " keeps one file for all three ops)");
            return 1;
        }

        String statePath = (state != null ? Path.of(state) : file.toAbsolutePath().resolveSibling(
                file.getFileName().toString().replaceAll("\\.kf$", "") + ".makealive"))
                .toAbsolutePath().normalize().toString().replace('\\', '/');
        if (!statePath.startsWith("jdbc:")) {
            statePath = "jdbc:h2:" + statePath;
        }

        Path temp;
        try {
            temp = Files.createTempDirectory("kof-makealive-");
        } catch (IOException e) {
            System.err.println("makealive: failed to create temp dir: " + e.getMessage());
            return 1;
        }
        Path out = temp.resolve("classes");
        Path siblingDir = file.toAbsolutePath().normalize().getParent();
        try {
            Path entry = temp.resolve(file.getFileName().toString());
            Files.writeString(entry, source + "\n" + generatedMain(op, statePath), StandardCharsets.UTF_8);
            if (siblingDir != null) {
                for (Path sib : KofCliSupport.collectShallow(siblingDir)) {
                    Path abs = sib.toAbsolutePath().normalize();
                    if (abs.equals(file.toAbsolutePath().normalize())) continue;
                    Files.copy(abs, temp.resolve(abs.getFileName().toString()),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            System.err.println("makealive: failed to stage the module: " + e.getMessage());
            KofCliSupport.cleanup(temp);
            return 1;
        }

        List<Path> sources = KofCliSupport.collect(temp);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compileSources(sources, out, target, temp);
        for (Diagnostic d : result.diagnostics().getDiagnostics()) System.err.println(d.format());
        if (!result.success()) { KofCliSupport.cleanup(temp); return 1; }

        String output;
        if (target == Target.JS) {
            // mesmo contrato do caminho JVM: a linha marcada decide; o stdout
            // do guest vai ao cap, o stderr em tee (terminal + cap) espelha o
            // pipe mesclado do subprocesso.
            java.io.ByteArrayOutputStream cap = new java.io.ByteArrayOutputStream();
            try {
                dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), cap,
                        new java.io.ByteArrayInputStream(new byte[0]),
                        KofCliSupport.tee(System.err, cap),
                        false, new String[0]);
            } catch (IOException e) {
                System.err.println("makealive: failed to execute: " + e.getMessage());
                KofCliSupport.cleanup(temp);
                return 1;
            }
            output = cap.toString(java.nio.charset.StandardCharsets.UTF_8);
            KofCliSupport.cleanup(temp);
        } else {
            String className = KofCliSupport.findMainClass(out);
            if (className == null) {
                System.err.println("makealive: no main class produced");
                KofCliSupport.cleanup(temp);
                return 1;
            }
            try {
                // o contrato do host `db` é "quem traz o driver JDBC é o
                // runner" (KofJsDbBridge; h2 = test-scope no repo) — o
                // subprocess herda o classpath do CLI, senão `mkLoadState`
                // não acha org.h2.Driver nem no teste.
                List<String> jvmCmd = new java.util.ArrayList<>();
                jvmCmd.add(KofCliSupport.javaExecutable());
                jvmCmd.addAll(KofStdio.capturedJvmFlags());
                jvmCmd.addAll(List.of("-cp",
                        out + java.io.File.pathSeparator + System.getProperty("java.class.path"),
                        className));
                ProcessBuilder pb = new ProcessBuilder(jvmCmd);
                pb.directory(siblingDir != null ? siblingDir.toFile() : temp.toFile());
                pb.redirectErrorStream(true);
                Process p = pb.start();
                output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                p.waitFor();
            } catch (IOException | InterruptedException e) {
                System.err.println("makealive: failed to execute: " + e.getMessage());
                KofCliSupport.cleanup(temp);
                return 1;
            } finally {
                KofCliSupport.cleanup(temp);
            }
        }

        String jsonLine = null;
        for (String line : output.split("\r?\n")) {
            if (line.startsWith(MARK)) { jsonLine = line.substring(MARK.length()); }
        }
        if (jsonLine == null) {
            // sem a linha marcada: o throw do host (provider recusou, guarda
            // de argumento) subiu cru -- a saida do processo E o diagnostico.
            System.err.print(output);
            return 1;
        }
        Map<String, Object> obj;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = (Map<String, Object>) Json.parse(jsonLine);
            obj = parsed;
        } catch (RuntimeException e) {
            System.err.println("makealive: invalid runner output: " + jsonLine);
            return 1;
        }
        printResult(op, obj, json, jsonLine);
        return KofCliSupport.truthy(obj.get("allOk")) ? 0 : 1;
    }

    private static void printResult(String op, Map<String, Object> obj, boolean json, String jsonLine) {
        if (json) { System.out.println(jsonLine); return; }
        String creates = join(obj.get("creates"));
        String updates = join(obj.get("updates"));
        String deletes = join(obj.get("deletes"));
        if (op.equals("plan")) {
            System.out.println("plan creates=" + creates + " updates=" + updates + " deletes=" + deletes);
            return;
        }
        String gen = String.valueOf(obj.getOrDefault("gen", "-"));
        if (op.equals("apply")) {
            System.out.println("apply created=" + join(obj.get("created"))
                    + " updated=" + join(obj.get("updated"))
                    + " deleted=" + join(obj.get("deleted")) + " gen=" + gen);
            return;
        }
        System.out.println("destroy deleted=" + deletes + " gen=" + gen);
    }

    private static String join(Object v) {
        if (!(v instanceof List<?> xs)) { return ""; }
        StringBuilder sb = new StringBuilder();
        for (Object x : xs) {
            if (sb.length() > 0) sb.append(',');
            sb.append(x);
        }
        return sb.toString();
    }

    /** main() sintetizado (texto Kof) — a unica convencao nova do runner. */
    static String generatedMain(String op, String conn) {
        String head = "main() {\n"
                + "    var d = design()\n"
                + "    var conn = \"" + conn + "\"\n"
                + "    var cur = mkLoadState(conn, d.nome)\n";
        if (op.equals("plan")) {
            return head
                    + "    var pl = plan(d, cur)\n"
                    + "    println(\"@@KOF_MAKEALIVE@@ \" + \"{\\\"op\\\":\\\"plan\\\",\\\"creates\\\":\"\n"
                    + "            + json.encode(pl.creates) + \",\\\"updates\\\":\"\n"
                    + "            + json.encode(pl.updates) + \",\\\"deletes\\\":\"\n"
                    + "            + json.encode(pl.deletes) + \",\\\"allOk\\\":true}\")\n"
                    + "    process.exit(0)\n"
                    + "}\n";
        }
        if (op.equals("apply")) {
            return head
                    + "    var p = provider()\n"
                    + "    var rep = apply(d, cur, p)\n"
                    + "    var g = mkMaxGen(conn, d.nome) + 1\n"
                    + "    var saved = mkSaveState(conn, d.nome, g, rep.state)\n"
                    + "    println(\"@@KOF_MAKEALIVE@@ \" + \"{\\\"op\\\":\\\"apply\\\",\\\"created\\\":\"\n"
                    + "            + json.encode(rep.created) + \",\\\"updated\\\":\"\n"
                    + "            + json.encode(rep.updated) + \",\\\"deleted\\\":\"\n"
                    + "            + json.encode(rep.deleted) + \",\\\"gen\\\":\"\n"
                    + "            + g + \",\\\"allOk\\\":\" + json.encode(saved) + \"}\")\n"
                    + "    process.exit(0)\n"
                    + "}\n";
        }
        return head
                + "    var p = provider()\n"
                + "    var rep = destroy(d, cur, p)\n"
                + "    var g = mkMaxGen(conn, d.nome) + 1\n"
                + "    var saved = mkSaveState(conn, d.nome, g, rep.state)\n"
                + "    println(\"@@KOF_MAKEALIVE@@ \" + \"{\\\"op\\\":\\\"destroy\\\",\\\"deletes\\\":\"\n"
                + "            + json.encode(rep.deleted) + \",\\\"gen\\\":\"\n"
                + "            + g + \",\\\"allOk\\\":\" + json.encode(saved) + \"}\")\n"
                + "    process.exit(0)\n"
                + "}\n";
    }
}
