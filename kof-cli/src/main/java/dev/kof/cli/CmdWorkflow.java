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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * {@code kof workflow} — runner de pipelines (D-WORKFLOW-RUN, Stage 2 rows
 * 2.5/2.6). O pipeline é CÓDIGO Kof; o runner é tooling (VISION §4.3).
 *
 * <p>Um arquivo de pipeline importa {@code kof.workflow} e define
 * {@code pipeline(): KofWfDag} — sem {@code main()}. O runner copia o módulo
 * para um diretório temporário, ACRESCENTA um {@code main()} sintetizado
 * (mesmo frontend, sem parser paralelo), compila e executa; o main emite uma
 * linha JSON marcada e o runner a formata. {@code list} mostra a DAG,
 * {@code run} executa (com {@code --job} para um subgrafo e {@code --dry-run}
 * para a ordem topológica), {@code --json} devolve a forma de máquina.
 *
 * <p>JVM+JS (R7): {@code --target js} roda o mesmo protocolo de linha marcada
 * honesto quando um corpo precisar de primitiva indisponível.
 */
final class CmdWorkflow {

    private static final String MARK = "@@KOF_WORKFLOW@@ ";

    private static final String USAGE =
            "usage: kof workflow <list|run> <file.kf> [--json] [--job <name>] [--dry-run] [--target jvm|js]\n"
            + "  a pipeline file imports kof.workflow and defines pipeline(): KofWfDag (no main())\n"
            + "  list        show the jobs and their dependencies\n"
            + "  run         execute the dag (exit 0 iff every job succeeded)\n"
            + "  --job <n>   run only job <n> and its transitive dependencies\n"
            + "  --dry-run   print the topological order without running any body\n"
            + "  --json      machine-readable output";

    private CmdWorkflow() {
    }

    static int run(String[] args) {
        if (args.length < 2) { System.err.println(USAGE); return 1; }
        if (args[1].equals("--help") || args[1].equals("-h")) { System.out.println(USAGE); return 0; }
        String sub = args[1];
        if (!sub.equals("list") && !sub.equals("run")) {
            System.err.println("workflow: unknown subcommand: " + sub + " (expected list|run)");
            return 1;
        }
        if (args.length < 3) { System.err.println(USAGE); return 1; }
        Path file = Path.of(args[2]);
        boolean json = false;
        boolean dryRun = false;
        String job = null;
        Target target = Target.JVM;
        for (int i = 3; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--json")) {
                json = true;
            } else if (a.equals("--dry-run")) {
                dryRun = true;
            } else if (a.equals("--job") && i + 1 < args.length) {
                job = args[++i];
            } else if (a.startsWith("--job=")) {
                job = a.substring("--job=".length());
            } else if (a.equals("--target") && i + 1 < args.length) {
                target = KofCliSupport.parseTarget(args[++i]);
            } else if (a.startsWith("--target=")) {
                target = KofCliSupport.parseTarget(a.substring("--target=".length()));
            } else {
                System.err.println("workflow: unknown or incomplete flag: " + a);
                return 1;
            }
        }
        if (sub.equals("list") && (job != null || dryRun)) {
            System.err.println("workflow list: --job/--dry-run only apply to 'run'");
            return 1;
        }
        if (target != Target.JVM && target != Target.JS) {
            System.err.println("workflow: target not supported yet (R7: JVM first; script/native are follow-up slices)");
            return 1;
        }
        if (!Files.exists(file)) { System.err.println("not found: " + file); return 1; }

        String source;
        try {
            source = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("workflow: cannot read " + file + ": " + e.getMessage());
            return 1;
        }
        // R6: um arquivo que não é pipeline falha cedo com a razão, nunca
        // compila "por acaso" e roda vazio.
        if (!Pattern.compile("(?m)^\\s*import\\s+kof\\.workflow\\b").matcher(source).find()) {
            System.err.println("workflow: " + file + " does not import kof.workflow"
                    + " (a pipeline file imports it and defines pipeline(): KofWfDag)");
            return 1;
        }
        if (!Pattern.compile("\\bpipeline\\s*\\(").matcher(source).find()) {
            System.err.println("workflow: " + file + " does not define pipeline()"
                    + " (the runner synthesizes main(); the file must define"
                    + " pipeline(): KofWfDag and must not define main())");
            return 1;
        }

        Path temp;
        try {
            temp = Files.createTempDirectory("kof-workflow-");
        } catch (IOException e) {
            System.err.println("workflow: failed to create temp dir: " + e.getMessage());
            return 1;
        }
        Path out = temp.resolve("classes");
        Path siblingDir = file.toAbsolutePath().normalize().getParent();
        try {
            Path entry = temp.resolve(file.getFileName().toString());
            Files.writeString(entry, source + "\n" + generatedMain(sub, job, dryRun), StandardCharsets.UTF_8);
            if (siblingDir != null) {
                for (Path sib : KofCliSupport.collectShallow(siblingDir)) {
                    Path abs = sib.toAbsolutePath().normalize();
                    if (abs.equals(file.toAbsolutePath().normalize())) continue;
                    Files.copy(abs, temp.resolve(abs.getFileName().toString()),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            System.err.println("workflow: failed to stage the module: " + e.getMessage());
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
            // mesmo contrato do caminho JVM: tudo que o guest escrever (stdout
            // + stderr confluentes) vira `output`; a linha marcada decide. O
            // cwd do processo do CLI vale p/ caminhos relativos (paridade com
            // CmdRun --target js; o subprocess JVM usa o dir do arquivo).
            java.io.ByteArrayOutputStream cap = new java.io.ByteArrayOutputStream();
            try {
                dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), cap,
                        new java.io.ByteArrayInputStream(new byte[0]),
                        KofCliSupport.tee(System.err, cap),
                        false, new String[0]);
            } catch (IOException e) {
                System.err.println("workflow: failed to execute: " + e.getMessage());
                KofCliSupport.cleanup(temp);
                return 1;
            }
            output = cap.toString(java.nio.charset.StandardCharsets.UTF_8);
            KofCliSupport.cleanup(temp);
        } else {
        String className = KofCliSupport.findMainClass(out);
        if (className == null) {
            System.err.println("workflow: no main class produced");
            KofCliSupport.cleanup(temp);
            return 1;
        }
        try {
            List<String> jvmCmd = new ArrayList<>();
            jvmCmd.add(KofCliSupport.javaExecutable());
            jvmCmd.addAll(KofStdio.capturedJvmFlags());
            jvmCmd.addAll(List.of("-cp", out.toString(), className));
            ProcessBuilder pb = new ProcessBuilder(jvmCmd);
            // o pipeline roda no diretório do arquivo: caminhos relativos do
            // pipeline (ex. build/) resolvem no projeto, não no staging.
            pb.directory(siblingDir != null ? siblingDir.toFile() : temp.toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor();
        } catch (IOException | InterruptedException e) {
            System.err.println("workflow: failed to execute: " + e.getMessage());
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
            // sem a linha marcada: a falha subiu como throw (ex. ciclo) — a
            // saída crua do processo É o diagnóstico.
            System.err.print(output);
            return 1;
        }
        Map<String, Object> obj;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = (Map<String, Object>) Json.parse(jsonLine);
            obj = parsed;
        } catch (RuntimeException e) {
            System.err.println("workflow: invalid runner output: " + jsonLine);
            return 1;
        }
        if (sub.equals("list")) {
            printList(obj, json, jsonLine);
            return 0;
        }
        if (dryRun) {
            printOrder(obj, json, jsonLine);
            return 0;
        }
        printReport(obj, json, jsonLine);
        return KofCliSupport.truthy(obj.get("allOk")) ? 0 : 1;
    }

    /** main() sintetizado (texto Kof) — a única convenção nova do runner. */
    private static String generatedMain(String sub, String job, boolean dryRun) {
        if (sub.equals("list")) {
            return """
                main() {
                    var __wf = pipeline()
                    List<String> __names = listOf()
                    List<String> __pairs = listOf()
                    var __i = 0
                    while (__i < __wf.jobs.size) {
                        var __j = __wf.jobs.get(__i)
                        __names.add(__j.nome)
                        var __joined = ""
                        var __k = 0
                        while (__k < __j.deps.size) {
                            if (__k > 0) { __joined = __joined + "\\u001f" }
                            __joined = __joined + __j.deps.get(__k).nome
                            __k = __k + 1
                        }
                        __pairs.add(__j.nome + "\\u001f" + __joined)
                        __i = __i + 1
                    }
                    println("@@KOF_WORKFLOW@@ " + "{\\"jobs\\":" + json.encode(__names)
                            + ",\\"deps\\":" + json.encode(__pairs) + "}")
                    process.exit(0)
                }
                """;
        }
        if (dryRun) {
            return """
                main() {
                    var __wf = pipeline()
                    println("@@KOF_WORKFLOW@@ " + "{\\"order\\":" + json.encode(__wf.order()) + "}")
                    process.exit(0)
                }
                """;
        }
        String call = job == null
                ? "__wf.run()"
                : "__wf.runJob(" + kofLiteral(job) + ")";
        return """
            main() {
                var __wf = pipeline()
                var __rep = %s
                println("@@KOF_WORKFLOW@@ " + "{\\"summary\\":" + json.encode(__rep.summary())
                        + ",\\"succeeded\\":" + json.encode(__rep.succeeded)
                        + ",\\"failed\\":" + json.encode(__rep.failed)
                        + ",\\"skipped\\":" + json.encode(__rep.skipped)
                        + ",\\"errors\\":" + json.encode(__rep.errors)
                        + ",\\"retries\\":" + json.encode(__rep.retries)
                        + ",\\"dead\\":" + json.encode(__rep.dead)
                        + ",\\"allOk\\":" + json.encode(__rep.allOk()) + "}")
                if (!__rep.allOk()) { process.exit(1) }
                process.exit(0)
            }
            """.formatted(call);
    }

    private static String kofLiteral(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    @SuppressWarnings("unchecked")
    private static void printList(Map<String, Object> obj, boolean json, String raw) {
        if (json) { System.out.println(raw); return; }
        List<String> jobs = (List<String>) obj.get("jobs");
        List<String> pairs = (List<String>) obj.get("deps");
        java.util.Map<String, String> deps = new java.util.LinkedHashMap<>();
        for (String pair : pairs) {
            int at = pair.indexOf('\u001f');
            if (at < 0) deps.put(pair, "");
            else deps.put(pair.substring(0, at), pair.substring(at + 1));
        }
        System.out.println("jobs: " + jobs.size());
        for (String name : jobs) {
            String d = deps.getOrDefault(name, "");
            if (d.isEmpty()) System.out.println("  " + name);
            else System.out.println("  " + name + " (after: " + d.replace("\u001f", ", ") + ")");
        }
    }

    @SuppressWarnings("unchecked")
    private static void printOrder(Map<String, Object> obj, boolean json, String raw) {
        if (json) { System.out.println(raw); return; }
        List<String> order = (List<String>) obj.get("order");
        for (int i = 0; i < order.size(); i++) {
            System.out.println((i + 1) + ". " + order.get(i));
        }
    }

    @SuppressWarnings("unchecked")
    private static void printReport(Map<String, Object> obj, boolean json, String raw) {
        if (json) { System.out.println(raw); return; }
        System.out.println(String.valueOf(obj.get("summary")));
        printGroup("errors", (List<String>) obj.get("errors"));
        printGroup("dead", (List<String>) obj.get("dead"));
    }

    private static void printGroup(String label, List<String> items) {
        if (items == null || items.isEmpty()) return;
        for (String item : items) System.out.println(label + ": " + item);
    }
}
