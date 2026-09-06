package dev.kof.cli;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.Diagnostic;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.KofHttpServer;
import dev.kof.compiler.ReflectiveHandler;
import dev.kof.compiler.Target;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
        if (args.length < 2) { System.err.println("usage: kof serve <file.kf> [--port <port>] [--host <host>]");
        if ("--help".equals(args[1]) || "-h".equals(args[1]) || "--version".equals(args[1])) {
            System.out.println("usage: kof serve <file.kf> [--port <port>] [--host <host>]");
            return;
        } return; }
        Path file = Path.of(args[1]);
        if (!Files.exists(file)) { System.err.println("file not found: " + file); System.exit(1); return; }

        int port = 8080;
        String host = "0.0.0.0";
        for (int i = 2; i < args.length; i++) {
            if (args[i].equals("--port") && i + 1 < args.length) {
                port = Integer.parseInt(args[i + 1]);
                i++;
            } else if (args[i].equals("--host") && i + 1 < args.length) {
                host = args[i + 1];
                i++;
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
        CompilationResult result = driver.compileSources(serveSources, tempDir, Target.JVM);
        for (Diagnostic d : result.diagnostics().getDiagnostics()) System.err.println(d.format());
        if (!result.success()) { KofCliSupport.cleanup(tempDir); System.exit(1); return; }

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

        System.out.println("kof serve starting on " + host + ":" + port);
        System.out.println("compiling " + file + " ...");
        System.out.println("server ready at http://" + host + ":" + port);

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
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    System.out.println("\nkof serve shutting down...");
                    if (KofCliSupport.servedProcess != null && KofCliSupport.servedProcess.isAlive()) {
                        KofCliSupport.servedProcess.destroy();
                    }
                    KofCliSupport.cleanup(tempDir);
                }));
                KofCliSupport.executeProcess(List.of(KofCliSupport.javaExecutable(),
                        "-Dkof.root=" + file.toAbsolutePath().normalize().getParent(),
                        "-cp", tempDir.toString(), className), tempDir);
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
}
