package dev.kof.cli;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Target;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * KofDebugJvmSession — sessao DAP do alvo JVM; com X7-5 ela tambem ANEXA a um
 * Kof JVM ja vivo (`kof debug --attach <porta>` contra um debuggee com
 * server=y,suspend=n): nada compila, nada lanca, breakpoint resolve na classe
 * carregada (ClassesBySignature) e o disconnect NUNCA mata o processo do
 * usuario. Extraida de KofDebug pelo gate 500 (regra 7: nome pela
 * responsabilidade).
 */
final class KofDebugJvmSession {

    private final Path sourceFile;
    private final CompilerDriver driver = new CompilerDriver();
    private final List<Integer> pendingBreakpoints = new ArrayList<>();
    private final List<JdwpClient.FullFrame> lastFrames = new ArrayList<>();
    private final Integer attachPort;
    private boolean attached;
    private Process jvmProcess;
    private JdwpClient jdwp;
    private Path classesDir;
    private int nextSeq = 1;
    private OutputStream out;
    private volatile long stoppedThread = -1;
    private volatile int stoppedLine = -1;
    private volatile String stopReason = "breakpoint";

    KofDebugJvmSession(Path sourceFile, Integer attachPort) {
        this.sourceFile = sourceFile;
        this.attachPort = attachPort;
    }

    void run() throws Exception {
        out = System.out;
        InputStream in = System.in;
        // §438: SIGTERM (editor que fecha / host que derruba) nao passa pelo EOF
        // do stdin. Sem o hook, o debuggee JVM (`-agentlib:jdwp=...,suspend=y`)
        // e o diretorio temporario `kof-debug-*` ficavam orfaos, segurando o
        // espaco ate esgotar o tmpfs e matar suites seguintes (mesmo hook do
        // KofDebugNativeDap).
        Runtime.getRuntime().addShutdownHook(new Thread(this::cleanupOnExit, "kof-dap-cleanup"));
        if (attachPort != null) {
            // X7-5 (doc §2 "attach — future"): anexa a um Kof JVM JA VIVO
            // (`java -agentlib:jdwp=...,server=y,suspend=n ...` + `kof debug --attach <porta>`).
            // Nada compila, nada lanca, e o processo do USUARIO nunca e morto no disconnect.
            attached = true;
            jdwp = new JdwpClient("127.0.0.1", attachPort);
            jdwp.connect();
            jdwp.setClassPrepareRequest("Default.Main", this::onJdwpEvent);
        }
        while (true) {
            int contentLength = -1;
            while (true) {
                String line = KofDebug.readLine(in);
                if (line == null) return;
                if (line.isBlank()) break;
                if (line.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring("content-length:".length()).trim());
                }
            }
            if (contentLength < 0) continue;
            byte[] body = in.readNBytes(contentLength);
            if (body.length < contentLength) return;
            Object parsed = Json.parse(new String(body, StandardCharsets.UTF_8));
            if (!(parsed instanceof Map<?, ?> msg)) continue;
            Map<String, Object> m = (Map<String, Object>) msg;
            if (!"request".equals(String.valueOf(m.get("type")))) continue;
            Object seq = m.get("seq");
            String command = String.valueOf(m.get("command"));
            Map<String, Object> args = m.get("arguments") instanceof Map<?, ?> a
                    ? (Map<String, Object>) a : Map.of();
            handleRequest(seq, command, args);
        }
    }

    private void handleRequest(Object seq, String command, Map<String, Object> args) throws Exception {
        switch (command) {
            case "initialize" -> {
                Map<String, Object> caps = new LinkedHashMap<>();
                caps.put("supportsConfigurationDoneRequest", true);
                caps.put("supportsTerminateRequest", true);
                caps.put("supportsEvaluateForHovers", true);
                respond(seq, command, caps);
            }
            case "launch" -> {
                String program = args.get("program") == null ? sourceFile.toString()
                        : args.get("program").toString();
                launch(Path.of(program));
                respond(seq, command, Map.of());
            }
            case "setBreakpoints" -> {
                List<Object> bps = args.get("breakpoints") instanceof List<?> l
                        ? new ArrayList<>(l) : List.of();
                List<Object> result = new ArrayList<>();
                pendingBreakpoints.clear();
                for (Object bp : bps) {
                    if (bp instanceof Map<?, ?> bpm && bpm.get("line") instanceof Number n) {
                        int line = n.intValue();
                        pendingBreakpoints.add(line);
                        Map<String, Object> brk = new LinkedHashMap<>();
                        brk.put("line", line);
                        boolean ok = false;
                        if (attached && jdwp != null) {
                            // classe ja carregada no alvo vivo: resolve agora (ClassesBySignature)
                            try {
                                jdwp.setLineBreakpoint("Default.Main", line);
                                ok = true;
                            } catch (IOException late) {
                                ok = false;
                            }
                        }
                        brk.put("verified", ok);
                        result.add(brk);
                    }
                }
                respond(seq, command, Map.of("breakpoints", result));
            }
            case "configurationDone" -> {
                if (jdwp != null && !attached) jdwp.resume();
                respond(seq, command, Map.of());
            }
            case "attach" -> fail2(seq, command, attached
                    ? "already attached (CLI --attach)"
                    : "use `kof debug --attach <porta>` — the Kof attach surface is CLI-side (X7-5)");
            case "continue" -> {
                // limpar ANTES do resume: o evento do proximo breakpoint chega noutra
                // thread e seta o id novo; limpar depois sobrescreveria esse id com -1
                // (corrida medida — stackTrace seguinte virava FrameCount(-1)=error 20).
                stoppedThread = -1;
                if (jdwp != null) jdwp.resume();
                respond(seq, command, Map.of("allThreadsContinued", true));
            }
            case "next" -> step(seq, command, 1);
            case "stepIn" -> step(seq, command, 0);
            case "stepOut" -> step(seq, command, 2);
            case "pause" -> {
                if (jdwp == null) {
                    fail2(seq, command, "not launched");
                    return;
                }
                long requested = args.get("threadId") instanceof Number n ? n.longValue() : -1;
                try {
                    if (requested >= 0) {
                        jdwp.suspendThread(requested);
                        stoppedThread = requested;
                    } else {
                        // Suspend the user threads, NEVER the JDWP agent's own threads
                        // (see JdwpEvents.suspendUserThreads for the measured reason).
                        stoppedThread = jdwp.suspendUserThreads();
                    }
                } catch (IOException e) {
                    fail2(seq, command, "pause failed: " + e.getMessage());
                    return;
                }
                stopReason = "pause";
                respond(seq, command, Map.of());
                notifyStopped();
            }
            case "setExceptionBreakpoints" -> {
                if (jdwp == null) {
                    fail2(seq, command, "not launched");
                    return;
                }
                List<?> filters = args.get("filters") instanceof List<?> l ? l : List.of();
                boolean caught = false;
                boolean uncaught = false;
                for (Object f : filters) {
                    if ("caught".equals(f)) caught = true;
                    else if ("uncaught".equals(f)) uncaught = true;
                }
                if (filters.isEmpty()) {
                    // no filter = the DAP "all exceptions" default
                    caught = true;
                    uncaught = true;
                }
                jdwp.setExceptionRequest(caught, uncaught);
                List<Object> result = new ArrayList<>();
                for (Object f : filters) {
                    result.add(Map.of("verified", true, "id", String.valueOf(f)));
                }
                respond(seq, command, Map.of("breakpoints", result));
            }
            case "threads" -> {
                List<Object> threads = new ArrayList<>();
                if (jdwp != null) {
                    for (long tid : jdwp.allThreads()) {
                        Map<String, Object> t = new LinkedHashMap<>();
                        t.put("id", tid);
                        t.put("name", "kof-thread-" + tid);
                        threads.add(t);
                    }
                }
                respond(seq, command, Map.of("threads", threads));
            }
            case "stackTrace" -> {
                List<Object> frames = new ArrayList<>();
                if (jdwp != null) {
                    long threadId = args.get("threadId") instanceof Number n
                            ? n.longValue() : stoppedThread;
                    int idx = 0;
                    lastFrames.clear();
                    try {
                        for (JdwpClient.FullFrame f : jdwp.framesFull(threadId, 50)) {
                            Map<String, Object> frame = new LinkedHashMap<>();
                            frame.put("id", idx);
                            frame.put("name", f.methodName());
                            Map<String, Object> src = new LinkedHashMap<>();
                            src.put("path", sourceFile.toAbsolutePath().toString());
                            src.put("line", f.line());
                            frame.put("source", src);
                            frame.put("line", f.line());
                            frame.put("column", 1);
                            frames.add(frame);
                            lastFrames.add(f);
                            idx++;
                        }
                    } catch (IOException e) {
                        // erro transitorio do JDWP (ex.: thread morta) = recusa honesta
                        // desta requisicao; a sessao do editor NAO pode morrer por isso.
                        fail2(seq, command, "JDWP: " + e.getMessage());
                        return;
                    }
                }
                respond(seq, command, Map.of("stackFrames", frames, "totalFrames", frames.size()));
            }
            case "scopes" -> {
                List<Object> scopes = new ArrayList<>();
                if (args.get("frameId") instanceof Number n) {
                    Map<String, Object> scope = new LinkedHashMap<>();
                    scope.put("name", "Local");
                    scope.put("variablesReference", n.intValue() + 1);
                    scope.put("expensive", false);
                    scopes.add(scope);
                }
                respond(seq, command, Map.of("scopes", scopes));
            }
            case "variables" -> {
                List<Object> vars = new ArrayList<>();
                if (args.get("variablesReference") instanceof Number n) {
                    int ref = n.intValue() - 1;
                    if (jdwp != null && ref >= 0 && ref < lastFrames.size()) {
                        try {
                            for (Object[] local : jdwp.locals(lastFrames.get(ref))) {
                                Map<String, Object> v = new LinkedHashMap<>();
                                v.put("name", local[0]);
                                v.put("value", formatValue(jdwp, (String) local[1], local[2]));
                                v.put("type", sigType((String) local[1]));
                                v.put("variablesReference", 0);
                                vars.add(v);
                            }
                        } catch (IOException e) {
                            System.err.println("kof debug: variables: " + e.getMessage());
                        }
                    }
                }
                respond(seq, command, Map.of("variables", vars));
            }
            case "evaluate" -> {
                // JDWP has no expression evaluator: resolve a local variable NAME
                // of the given frame (the DAP hover case). Anything else is an
                // honest refusal — never an invented value (R6).
                if (!(args.get("expression") instanceof String expr) || expr.isBlank()) {
                    fail2(seq, command, "missing expression");
                    return;
                }
                if (jdwp == null) {
                    fail2(seq, command, "not stopped — cannot evaluate");
                    return;
                }
                if (lastFrames.isEmpty() && stoppedThread >= 0) {
                    // the client may evaluate before asking for stackTrace
                    try {
                        lastFrames.addAll(jdwp.framesFull(stoppedThread, 50));
                    } catch (IOException e) {
                        fail2(seq, command, "JDWP: " + e.getMessage());
                        return;
                    }
                }
                int frameId = args.get("frameId") instanceof Number n ? n.intValue() : 0;
                if (frameId < 0 || frameId >= lastFrames.size()) {
                    fail2(seq, command, "no frame — stop at a breakpoint first");
                    return;
                }
                if (!expr.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
                    fail2(seq, command, "JVM evaluate resolves a local variable name only"
                            + " (JDWP has no expression evaluator)");
                    return;
                }
                try {
                    for (Object[] local : jdwp.locals(lastFrames.get(frameId))) {
                        if (expr.equals(local[0])) {
                            respond(seq, command, Map.of(
                                    "result", formatValue(jdwp, (String) local[1], local[2]),
                                    "type", sigType((String) local[1]),
                                    "variablesReference", 0));
                            return;
                        }
                    }
                } catch (IOException e) {
                    fail2(seq, command, "JDWP: " + e.getMessage());
                    return;
                }
                fail2(seq, command, "no local named '" + expr + "' in this frame");
            }
            case "disconnect", "terminate" -> {
                if (!attached && jdwp != null) jdwp.dispose();
                if (!attached && jvmProcess != null) jvmProcess.destroy();
                cleanup();
                respond(seq, command, Map.of());
                out.flush();
                System.exit(0);
            }
            // §428: request nao implementada responde erro HONESTO (nunca
            // success:true + corpo vazio = fachada silenciosa, Q7).
            default -> fail2(seq, command, "unsupported request: " + command);
        }
    }

    private void onJdwpEvent(int kind, long threadId, long typeId) {
        try {
            if (kind == 8) {
                for (Integer line : pendingBreakpoints) {
                    jdwp.setLineBreakpoint(typeId, line);
                }
                jdwp.resume();
            } else if (kind == 2 || kind == 1 || kind == 4) {
                // 2 = Breakpoint, 1 = SingleStep (a step landed), 4 = Exception
                stoppedThread = threadId;
                stopReason = kind == 1 ? "step" : kind == 4 ? "exception" : "breakpoint";
                for (JdwpClient.FrameInfo f : jdwp.frames(threadId, 1)) {
                    stoppedLine = f.line();
                }
                notifyStopped();
            }
        } catch (IOException e) {
            System.err.println("kof debug: " + e.getMessage());
        }
    }

    /**
     * DAP next/stepIn/stepOut: set a line SingleStep for the stopped thread and
     * resume; the resulting SingleStep event arrives as a `stopped` with
     * reason "step". {@code depth}: 1 = over, 0 = into, 2 = out (JDWP).
     */
    private void step(Object seq, String command, int depth) throws IOException {
        if (jdwp == null || stoppedThread < 0) {
            fail2(seq, command, "not stopped — cannot step");
            return;
        }
        jdwp.setStepRequest(stoppedThread, depth);
        // limpar ANTES do resume (mesma corrida do `continue`): o SingleStep chega
        // noutra thread e seta stoppedThread; limpar depois o clobberava com -1.
        stoppedThread = -1;
        jdwp.resume();
        respond(seq, command, Map.of());
    }

    private void launch(Path file) throws Exception {
        classesDir = Files.createTempDirectory("kof-debug-");
        CompilationResult result = driver.compile(file, classesDir, Target.JVM);
        if (!result.success()) {
            throw new IOException("compilation failed");
        }
        int port;
        try (ServerSocket ss = new ServerSocket(0)) {
            port = ss.getLocalPort();
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(KofDebug.javaExecutable());
        cmd.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=" + port);
        // #679 / D-STDOUT-ENCODING: the debuggee writes to a pipe, so force its
        // streams to UTF-8 and relay them byte-exactly (below), instead of
        // inheriting the console/native encoding and corrupting accents.
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-Dstdout.encoding=UTF-8");
        cmd.add("-Dstderr.encoding=UTF-8");
        cmd.add("-cp");
        cmd.add(classesDir.toString());
        cmd.add("Default.Main");
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        jvmProcess = pb.start();
        Thread sink = new Thread(() -> {
            try (var reader = new java.io.InputStreamReader(jvmProcess.getInputStream(), StandardCharsets.UTF_8)) {
                char[] cbuf = new char[1024];
                int n;
                while ((n = reader.read(cbuf)) != -1) {
                    System.err.print(new String(cbuf, 0, n));
                }
                System.err.flush();
            } catch (IOException ignored) {
            }
        }, "debuggee-sink");
        sink.setDaemon(true);
        sink.start();

        jdwp = new JdwpClient("127.0.0.1", port);
        jdwp.connect();
        jdwp.setClassPrepareRequest("Default.Main", this::onJdwpEvent);
    }

    private static String sigType(String signature) {
        return switch (signature.charAt(0)) {
            case 'I', 'S', 'B' -> "Int";
            case 'J' -> "Long";
            case 'Z' -> "Bool";
            case 'C' -> "Char";
            case 'F' -> "Float";
            case 'D' -> "Double";
            default -> signature.startsWith("Ljava/lang/String;") ? "String" : "Object";
        };
    }

    static String formatValue(JdwpClient jdwp, String signature, Object value) throws IOException {
        if (value == null) {
            return "null";
        }
        char kind = signature.charAt(0);
        if (kind == 'L' || kind == '[') {
            long ref = ((Long) value).longValue();
            if (ref == 0) {
                return "null";
            }
            if ("Ljava/lang/String;".equals(signature)) {
                try {
                    return (char) 34 + jdwp.stringValue(ref) + (char) 34;
                } catch (IOException notString) {
                    return "<String@" + ref + ">";
                }
            }
            return "<Object@" + ref + ">";
        }
        if (kind == 'Z') {
            return Boolean.toString((Boolean) value);
        }
        if (kind == 'C') {
            return "'" + (char) ((Integer) value).intValue() + "'";
        }
        if (kind == 'F') {
            return Float.toString(Float.intBitsToFloat((Integer) value));
        }
        if (kind == 'D') {
            return Double.toString(Double.longBitsToDouble((Long) value));
        }
        return String.valueOf(value);
    }

    private void notifyStopped() throws IOException {
        Map<String, Object> evt = new LinkedHashMap<>();
        evt.put("seq", nextSeq++);
        evt.put("type", "event");
        evt.put("event", "stopped");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", stopReason);
        body.put("threadId", stoppedThread);
        body.put("allThreadsStopped", true);
        evt.put("body", body);
        KofDebug.writeMessage(out, Json.stringify(evt));
    }

    private void fail2(Object seq, String command, String message) throws IOException {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("seq", nextSeq++);
        response.put("type", "response");
        response.put("request_seq", seq);
        response.put("success", false);
        response.put("message", message);
        response.put("command", command);
        KofDebug.writeMessage(out, Json.stringify(response));
    }

    private void respond(Object seq, String command, Object body) throws IOException {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("seq", nextSeq++);
        response.put("type", "response");
        response.put("request_seq", seq);
        response.put("success", true);
        response.put("command", command);
        response.put("body", body);
        KofDebug.writeMessage(out, Json.stringify(response));
    }

    /**
     * §438: encerra o debuggee lançado por este processo e remove o diretório
     * temporário. Chamado pelo shutdown hook (SIGTERM/System.exit); nunca toca
     * um alvo ATTACH (processo do usuário).
     */
    private void cleanupOnExit() {
        if (!attached && jvmProcess != null && jvmProcess.isAlive()) {
            jvmProcess.destroyForcibly();
        }
        cleanup();
    }

    private void cleanup() {
        if (classesDir != null) {
            try (var s = Files.walk(classesDir)) {
                s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                    }
                });
            } catch (IOException ignored) {
            }
        }
    }
}
