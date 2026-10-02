package dev.kof.cli;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.Diagnostic;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.KofHttpServer;
import dev.kof.compiler.KofProjectConfig;
import dev.kof.compiler.ReflectiveHandler;
import dev.kof.compiler.Target;
import dev.kof.compiler.TargetMatrix;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * kof serve — compila o módulo do arquivo e sobe o servidor: apps
 * Kof-native (web.app() + app.listen()) rodam main() em processo filho;
 * apps legacy handle(...) rodam no KofHttpServer in-process. Extraído
 * de Main (REFACTOR-500 Fase 8).
 */
final class CmdServe {

    private CmdServe() {
    }

    static void run(String[] args) {
        if (args.length >= 2 && ("--help".equals(args[1]) || "-h".equals(args[1]) || "--version".equals(args[1]))) {
            System.out.println("usage: kof serve <file.kf> [--port <port>] [--host <host>] [--backend <t>] [--frontend <t>]");
            return;
        }
        if (args.length < 2) {
            System.err.println("usage: kof serve <file.kf> [--port <port>] [--host <host>] [--backend <t>] [--frontend <t>]");
            return;
        }
        Path file = Path.of(args[1]);
        if (!Files.exists(file)) { System.err.println("file not found: " + file); System.exit(1); return; }

        int port = 8080;
        boolean portFlag = false;
        String host = "0.0.0.0";
        String backendFlag = null;
        String frontendFlag = null;
        for (int i = 2; i < args.length; i++) {
            if (args[i].startsWith("--port=")) {
                Integer p = parsePortOption(args[i].substring("--port=".length()));
                if (p == null) System.exit(1);
                port = p;
                portFlag = true;
            } else if (args[i].equals("--port") && i + 1 < args.length) {
                Integer p = parsePortOption(args[i + 1]);
                if (p == null) System.exit(1);
                port = p;
                portFlag = true;
                i++;
            } else if (args[i].startsWith("--host=")) {
                host = args[i].substring("--host=".length());
            } else if (args[i].equals("--host") && i + 1 < args.length) {
                host = args[i + 1];
                i++;
            } else if (args[i].startsWith("--backend=")) {
                backendFlag = args[i].substring("--backend=".length());
            } else if (args[i].equals("--backend") && i + 1 < args.length) {
                backendFlag = args[++i];
            } else if (args[i].startsWith("--frontend=")) {
                frontendFlag = args[i].substring("--frontend=".length());
            } else if (args[i].equals("--frontend") && i + 1 < args.length) {
                frontendFlag = args[++i];
            } else if (args[i].startsWith("-")) {
                // R6: `serve` has no program-arg pass-through; an unknown flag
                // (or one missing its value) must not be silently ignored.
                System.err.println("serve: unknown or incomplete flag: " + args[i]
                        + " (accepts: --port <n> --host <h> --backend <t> --frontend <t>)");
                System.exit(1);
                return;
            } else {
                System.err.println("serve: unexpected argument: " + args[i]
                        + " (accepts: --port <n> --host <h> --backend <t> --frontend <t>)");
                System.exit(1);
                return;
            }
        }

        Path tempDir;
        try { tempDir = Files.createTempDirectory("kof-serve-"); }
        catch (IOException e) { System.err.println("failed to create temp dir: " + e.getMessage()); System.exit(1); return; }

        CompilerDriver driver = new CompilerDriver();
        // módulo = diretório do arquivo de entrada (irmãos .kf incluídos)
        java.util.List<Path> serveSources = new ArrayList<>();
        serveSources.add(file.toAbsolutePath().normalize());
        Path serveDir = file.toAbsolutePath().normalize().getParent();
        if (serveDir != null) {
            for (Path sib : KofCliSupport.collect(serveDir)) {
                Path abs = sib.toAbsolutePath().normalize();
                if (!abs.equals(serveSources.get(0)) && !serveSources.contains(abs)) serveSources.add(abs);
            }
        }
        // F2-parte-4 (plataforma): --backend/--frontend sobrepõem o kof.toml.
        // serve hoje só executa backend JVM (in-process KofHttpServer); backend
        // não-JVM resolvido vira erro honesto (R6), nunca fallback silencioso.
        Target serveTarget = Target.JVM;
        if (backendFlag != null || frontendFlag != null) {
            Path serveRoot = driver.resolveModuleRoot(serveSources);
            if (serveRoot == null) serveRoot = serveDir;
            try {
                KofCliSupport.Targets sel = KofCliSupport.selectTargets(backendFlag, frontendFlag, serveRoot);
                if (sel.backend() != null && sel.backend() != Target.JVM) {
                    System.err.println("serve: backend '" + TargetMatrix.name(sel.backend())
                            + "' is not runnable via kof serve yet (jvm only); "
                            + "use 'kof run --target " + TargetMatrix.name(sel.backend()) + "'");
                    KofCliSupport.cleanup(tempDir);
                    System.exit(1);
                    return;
                }
                serveTarget = sel.backend() != null ? sel.backend() : Target.JVM;
            } catch (IllegalArgumentException e) {
                System.err.println("serve: " + e.getMessage());
                KofCliSupport.cleanup(tempDir);
                System.exit(1);
                return;
            }
        }
        CompilationResult result = driver.compileSources(serveSources, tempDir, serveTarget);
        for (Diagnostic d : result.diagnostics().getDiagnostics()) System.err.println(d.format());
        if (!result.success()) { KofCliSupport.cleanup(tempDir); System.exit(1); return; }

        // F3 (plataforma, APPLICATION_MODEL I2/P2): full-stack — o APP é dono
        // das rotas. A CLI só compila o frontend (bundle KofJS) + estáticos e
        // passa os caminhos ao backend via env (KOF_WEB_OUT/KOF_STATIC_OUT),
        // que o app consome com config.env(...) + app.serveDir. Aditivo: sem
        // web/ com .kf = backend puro de hoje.
        Path webOut = null;
        Path staticOut = null;
        if (serveTarget == Target.JVM) {
            KofCliSupport.Layout layout = KofCliSupport.detectLayout(serveDir);
            if (layout.fullStack()) {
                KofCliSupport.buildFrontend(driver, layout, Target.JS, tempDir);
                webOut = tempDir.resolve("frontend");
                staticOut = layout.staticDir() != null ? tempDir.resolve("static") : null;
            }
        }

        String className = KofCliSupport.findMainClass(tempDir);
        if (System.getProperty("kof.trace") != null) {
            System.err.println("LAUNCH className=" + className + " dir=" + tempDir);
        }
        if (className == null) {
            System.err.println("no main class found");
            KofCliSupport.cleanup(tempDir);
            System.exit(1);
            return;
        }

        URLClassLoader handlerLoader;
        try {
            handlerLoader = new URLClassLoader(
                    new java.net.URL[]{tempDir.toUri().toURL()}, Main.class.getClassLoader());
        } catch (java.net.MalformedURLException e) {
            System.err.println("failed to load compiled classes: " + e.getMessage());
            KofCliSupport.cleanup(tempDir);
            System.exit(1);
            return;
        }

        try {
            Class<?> handlerClass = Class.forName(className, true, handlerLoader);
            boolean hasMain = false;
            try {
                handlerClass.getMethod("main", String[].class);
                hasMain = true;
            } catch (NoSuchMethodException ignored) {
            }
            if (hasMain) {
                // Kof-native web app (web.app() + app.listen()): the program
                // runs its own server. Legacy handle(...) apps have no main.
                handlerLoader.close();
                // R6 (#35.3): o app é dono da porta via app.listen(port).
                // --port/--host NÃO controlam este modo — avisar em vez de
                // imprimir um banner mentiroso com a porta da CLI.
                System.out.println("compiling " + file + " ...");
                if (portFlag) {
                    System.out.println("note: kof-native app defines its own port via "
                            + "app.listen(port); --port " + port + " is ignored here.");
                }
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    System.out.println("\nkof serve shutting down...");
                    if (KofCliSupport.servedProcess != null && KofCliSupport.servedProcess.isAlive()) {
                        KofCliSupport.servedProcess.destroy();
                    }
                    KofCliSupport.cleanup(tempDir);
                }));
                // F3 full-stack: passa ao backend o caminho do bundle/estáticos
                // (o app consome via config.env("KOF_WEB_OUT") + serveDir).
                Map<String, String> appEnv = new java.util.HashMap<>();
                if (webOut != null) appEnv.put("KOF_WEB_OUT", webOut.toString());
                if (staticOut != null) appEnv.put("KOF_STATIC_OUT", staticOut.toString());
                // #598 (D-APP): o manifesto é lido pela CLI. Se kof.toml declara
                // [server] port e nenhum KOF_SERVER_PORT veio do ambiente, a CLI
                // repassa o valor ao app — `config.int("server.port", ...)` lê
                // KOF_SERVER_PORT por convenção. Sem manifesto/valor, comportamento
                // anterior; env explícito do usuário tem precedência.
                Path manifestRoot = driver.resolveModuleRoot(serveSources);
                if (manifestRoot == null) manifestRoot = serveDir;
                Integer manifestPort = manifestRoot != null
                        ? KofProjectConfig.load(manifestRoot).serverPort() : null;
                if (manifestPort != null && System.getenv("KOF_SERVER_PORT") == null) {
                    appEnv.put("KOF_SERVER_PORT", String.valueOf(manifestPort));
                    System.out.println("kof serve: using [server] port = " + manifestPort
                            + " from kof.toml");
                }
                List<String> serveCmd = new java.util.ArrayList<>();
                serveCmd.add(KofCliSupport.javaExecutable());
                serveCmd.addAll(KofStdio.inheritedJvmFlags());
                serveCmd.addAll(List.of("-Dkof.root=" + file.toAbsolutePath().normalize().getParent(),
                        "-cp", tempDir.toString(), className));
                KofCliSupport.executeProcess(serveCmd, tempDir, appEnv);
                return;
            }
            dev.kof.compiler.KofHttpServer server = new dev.kof.compiler.KofHttpServer(
                    dev.kof.compiler.ReflectiveHandler.forClass(handlerClass));
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nkof serve shutting down...");
                server.close();
                try { handlerLoader.close(); } catch (IOException ignored) {}
                KofCliSupport.cleanup(tempDir);
            }));

            System.out.println("compiling " + file + " ...");
            System.out.println("kof serve listening on http://" + host + ":" + port);
            System.out.println("listening for connections...");
            server.serve(host, port);
        } catch (ClassNotFoundException e) {
            System.err.println("handler class not found: " + e.getMessage());
            KofCliSupport.cleanup(tempDir);
            System.exit(1);
        } catch (IOException e) {
            System.err.println("server error: " + e.getMessage());
            KofCliSupport.cleanup(tempDir);
            System.exit(1);
        }
    }

    /**
     * `--port`: int com diagnostico limpo em vez de NumberFormatException
     * crua (CodeQL uncaught-number-format-exception, R6 — mesmo padrao do
     * `parseIntOption` do `kof bench`). Null = valor invalido.
     */
    static Integer parsePortOption(String value) {
        try {
            int p = Integer.parseInt(value.trim());
            if (p < 0 || p > 65535) {
                System.err.println("kof serve: valor invalido para --port: '" + value
                        + "' (esperado 0-65535)");
                return null;
            }
            return p;
        } catch (NumberFormatException e) {
            System.err.println("kof serve: valor invalido para --port: '" + value + "'");
            return null;
        }
    }
}
