package dev.kof.runtime;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * KofJsRunner — executes the JavaScript emitted by the KofJS backend inside
 * the Kof process itself, using the embedded GraalJS engine.
 *
 * The KofJS target has no dependency on Node.js or any external JavaScript
 * runtime: the generated .mjs modules are standard ES2022+ ECMAScript modules,
 * and `kof run --target=js` (and the E2E suite) run them here, in-process.
 *
 * Platform operations (filesystem, stdout, stdin) are exposed to the module
 * through the `kof_platform` global, implemented in Java. The generated module
 * only talks to the platform-neutral kof-runtime.mjs; the platform layer
 * (kof-runtime-io.mjs) delegates to kof_platform.
 */
public final class KofJsRunner {

    private KofJsRunner() {}

    /**
     * Executes an ESM module file and returns the process exit code
     * (0 on success, 1 on runtime error).
     */
    public static int run(Path moduleFile) throws IOException {
        return run(moduleFile, System.out, System.in, System.err);
    }

    public static int run(Path moduleFile, OutputStream out, InputStream in,
                          OutputStream err) throws IOException {
        return run(moduleFile, out, in, err, false);
    }

    /**
     * Executes an ESM module. When {@code openWindow} is true and the program
     * created a kof.ui window (kofUiFlush was triggered), the serialized page
     * is written next to the module and opened in the system webview (the
     * platform default browser).
     */
    public static int run(Path moduleFile, OutputStream out, InputStream in,
                          OutputStream err, boolean openWindow) throws IOException {
        return run(moduleFile, out, in, err, openWindow, new String[0]);
    }

    public static int run(Path moduleFile, OutputStream out, InputStream in,
                          OutputStream err, boolean openWindow, String[] programArgs) throws IOException {
        // o contexto NÃO pode fechar antes da extração do sentinel de
        // process.exit (o guest object só é legível com o contexto vivo)
        Context context = Context.newBuilder("js")
                .allowIO(true)
                .allowAllAccess(true)
                .option("engine.WarnInterpreterOnly", "false")
                .out(out)
                .err(err)
                .in(in)
                .build();
        try {
            exposePlatform(context, out, in, programArgs);
            Source source = Source.newBuilder("js", moduleFile.toFile())
                    .mimeType("application/javascript+module")
                    .build();
            context.eval(source);
            drainActiveTasks(context);
            if (openWindow) {
                Value uiRoot = context.getBindings("js").getMember("kof__uiRootHtml");
                if (uiRoot != null && uiRoot.isString()) {
                    String html = uiRoot.asString();
                    if (html != null && !html.isEmpty()) {
                        KofJsWebview.openInWebview(moduleFile, html);
                    }
                }
            }
            return 0;
        } catch (org.graalvm.polyglot.PolyglotException pe) {
            // process.exit(code): o guest lança { __kof_exit__: code }
            if (pe.isGuestException()) {
                try {
                    Value guest = pe.getGuestObject();
                    if (guest != null && guest.hasMembers()) {
                        Value exit = guest.getMember("__kof_exit__");
                        if (exit != null && exit.isNumber()) {
                            return exit.asInt();
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            try {
                String message = pe.getMessage();
                if (message != null && !message.isBlank()) {
                    err.write((message + "\n").getBytes(StandardCharsets.UTF_8));
                    err.flush();
                }
            } catch (IOException ignored) {
            }
            return 1;
        } catch (Exception e) {
            try {
                String message = e.getMessage();
                if (message != null && !message.isBlank()) {
                    err.write((message + "\n").getBytes(StandardCharsets.UTF_8));
                    err.flush();
                }
            } catch (IOException ignored) {
            }
            return 1;
        } finally {
            context.close();
        }
    }

    /**
     * Executes the module and returns the serialized kof.ui window HTML
     * (or null when the program created no window). Used by tests.
     */
    public static String runCaptureHtml(Path moduleFile, OutputStream out, InputStream in,
                                        OutputStream err) throws IOException {
        try (Context context = Context.newBuilder("js")
                .allowIO(true)
                .allowAllAccess(true)
                .option("engine.WarnInterpreterOnly", "false")
                .out(out)
                .err(err)
                .in(in)
                .build()) {
            exposePlatform(context, out, in);
            Source source = Source.newBuilder("js", moduleFile.toFile())
                    .mimeType("application/javascript+module")
                    .build();
            context.eval(source);
            drainActiveTasks(context);
            Value html = context.getBindings("js").getMember("kof__uiRootHtml");
            return html.isString() && !html.asString().isEmpty() ? html.asString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Bombeia microtasks até não haver spawn tasks ativas (kofActiveTasks).
     * GraalJS pode não drenar a fila após um único eval; sem isso spawn/async
     * terminam antes do programa sair.
     */
    private static void drainActiveTasks(Context context) {
        Value active = context.getBindings("js").getMember("kofActiveTasks");
        while (active != null && active.isNumber() && active.asInt() > 0) {
            context.eval(Source.newBuilder("js", "void 0;", "kof-pump.js").buildLiteral());
            active = context.getBindings("js").getMember("kofActiveTasks");
        }
    }

    /**
     * Exposes the kof_platform object: IO and console primitives implemented
     * in Java. The generated JavaScript never reaches for Node/browser APIs.
     */
    private static void exposePlatform(Context context, OutputStream out, InputStream in) {
        exposePlatform(context, out, in, new String[0]);
    }

    private static void exposePlatform(Context context, OutputStream out, InputStream in,
                                       String[] programArgs) {
        Value bindings = context.getBindings("js");
        java.util.Map<String, Object> platform = new java.util.LinkedHashMap<>();
        platform.put("print", (ProxyExecutable) args -> {
            for (Value arg : args) {
                try {
                    out.write(String.valueOf(arg.isNull() ? "null" : arg).getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    return 0;
                }
            }
            return 0;
        });
        platform.put("processRun", (ProxyExecutable) args -> {
            try {
                String program = args[0].asString();
                java.util.List<String> cmd = new java.util.ArrayList<>();
                cmd.add(program);
                if (args.length > 1 && !args[1].isNull() && args[1].hasArrayElements()) {
                    long n = args[1].getArraySize();
                    for (int i = 0; i < n; i++) {
                        Value v = args[1].getArrayElement(i);
                        cmd.add(v.isString() ? v.asString() : String.valueOf(v));
                    }
                }
                Process p = new ProcessBuilder(cmd).redirectErrorStream(false).start();
                String outText = new String(p.getInputStream().readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8);
                String errText = new String(p.getErrorStream().readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8);
                int code = p.waitFor();
                java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
                result.put("stdout", outText);
                result.put("stderr", errText);
                result.put("exitCode", code);
                return result;
            } catch (Exception e) {
                java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
                result.put("stdout", "");
                result.put("stderr", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                result.put("exitCode", -1);
                return result;
            }
        });
        platform.put("args", (ProxyExecutable) args -> java.util.Arrays.asList(programArgs));
        platform.put("readLine", (ProxyExecutable) args -> readLine(in));
        platform.put("readFile", (ProxyExecutable) args -> {
            try {
                return Files.readString(Path.of(args[0].asString()));
            } catch (IOException e) {
                return null;
            }
        });
        platform.put("writeFile", (ProxyExecutable) args -> {
            try {
                Files.writeString(Path.of(args[0].asString()), args[1].asString());
                return 0;
            } catch (IOException e) {
                return -1;
            }
        });
        platform.put("fileExists", (ProxyExecutable) args -> Files.exists(Path.of(args[0].asString())) ? 1 : 0);
        platform.put("fileIsFile", (ProxyExecutable) args -> Files.isRegularFile(Path.of(args[0].asString())) ? 1 : 0);
        platform.put("fileIsDir", (ProxyExecutable) args -> Files.isDirectory(Path.of(args[0].asString())) ? 1 : 0);
        platform.put("readText", (ProxyExecutable) args -> readFileText(args));
        platform.put("writeText", (ProxyExecutable) args -> writeFileText(args, false));
        platform.put("appendText", (ProxyExecutable) args -> writeFileText(args, true));
        platform.put("readBytes", (ProxyExecutable) args -> readBytes(args));
        platform.put("writeBytes", (ProxyExecutable) args -> writeBytes(args, false));
        platform.put("appendBytes", (ProxyExecutable) args -> writeBytes(args, true));
        platform.put("delete", (ProxyExecutable) args -> {
            try {
                Files.deleteIfExists(Path.of(args[0].asString()));
                return 0;
            } catch (IOException e) {
                return -1;
            }
        });
        platform.put("fileSize", (ProxyExecutable) args -> {
            try {
                return Files.size(Path.of(args[0].asString()));
            } catch (IOException e) {
                return -1L;
            }
        });
        platform.put("fileName", (ProxyExecutable) args -> {
            Path p = Path.of(args[0].asString());
            Path name = p.getFileName();
            return name == null ? args[0].asString() : name.toString();
        });
        platform.put("pathParent", (ProxyExecutable) args -> {
            Path parent = Path.of(args[0].asString()).getParent();
            return parent == null ? null : parent.toString();
        });
        platform.put("pathFileName", (ProxyExecutable) args -> {
            Path name = Path.of(args[0].asString()).getFileName();
            return name == null ? args[0].asString() : name.toString();
        });
        platform.put("pathExtension", (ProxyExecutable) args -> {
            String name = Path.of(args[0].asString()).getFileName().toString();
            int dot = name.lastIndexOf('.');
            return dot <= 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1);
        });
        platform.put("pathNormalize", (ProxyExecutable) args -> Path.of(args[0].asString()).normalize().toString());
        platform.put("pathResolve", (ProxyExecutable) args -> Path.of(args[0].asString()).resolve(args[1].asString()).toString());
        platform.put("pathIsAbsolute", (ProxyExecutable) args -> Path.of(args[0].asString()).isAbsolute() ? 1 : 0);
        platform.put("pathToAbsolute", (ProxyExecutable) args -> Path.of(args[0].asString()).toAbsolutePath().toString());
        platform.put("dirCreate", (ProxyExecutable) args -> dirCreate(args, false));
        platform.put("dirCreateDirs", (ProxyExecutable) args -> dirCreate(args, true));
        platform.put("dirDelete", (ProxyExecutable) args -> {
            try {
                Files.deleteIfExists(Path.of(args[0].asString()));
                return 0;
            } catch (IOException e) {
                return -1;
            }
        });
        platform.put("dirList", (ProxyExecutable) args -> dirList(args));
        // kof.security platform primitives (docs/security.md §5)
        platform.put("getenv", (ProxyExecutable) args ->
                System.getenv(args[0].asString()));
        platform.put("randomBytesHex", (ProxyExecutable) args -> {
            int n = args[0].asInt();
            byte[] buf = new byte[Math.max(0, Math.min(n, 4096))];
            new java.security.SecureRandom().nextBytes(buf);
            StringBuilder sb = new StringBuilder(buf.length * 2);
            for (byte b : buf) sb.append(String.format("%02x", b));
            return sb.toString();
        });
        platform.put("randomInt", (ProxyExecutable) args -> {
            int bound = args[0].asInt();
            return bound <= 0 ? 0 : new java.security.SecureRandom().nextInt(bound);
        });
        platform.put("pbkdf2Hex", (ProxyExecutable) args -> {
            String password = args[0].asString();
            String saltHex = args[1].asString();
            int iterations = args[2].asInt();
            byte[] salt = new byte[saltHex.length() / 2];
            for (int i = 0; i < salt.length; i++) {
                salt[i] = (byte) Integer.parseInt(saltHex.substring(i * 2, i * 2 + 2), 16);
            }
            try {
                byte[] dk = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                        .generateSecret(new javax.crypto.spec.PBEKeySpec(
                                password.toCharArray(), salt, iterations, 256))
                        .getEncoded();
                StringBuilder sb = new StringBuilder(dk.length * 2);
                for (byte b : dk) sb.append(String.format("%02x", b));
                return sb.toString();
            } catch (Exception e) {
                return null;
            }
        });
        bindings.putMember("kof_platform", ProxyObject.fromMap(platform));
    }

    private static String readLine(InputStream in) {
        StringBuilder sb = new StringBuilder();
        try {
            int c;
            while ((c = in.read()) != -1) {
                if (c == '\n') return sb.toString();
                sb.append((char) c);
            }
        } catch (IOException ignored) {
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static Object readFileText(Value[] args) {
        try {
            return Files.readString(Path.of(args[0].asString()));
        } catch (IOException e) {
            return null;
        }
    }

    private static Object writeFileText(Value[] args, boolean append) {
        try {
            if (append) {
                Files.writeString(Path.of(args[0].asString()), args[1].asString(),
                        StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND);
            } else {
                Files.writeString(Path.of(args[0].asString()), args[1].asString());
            }
            return 0;
        } catch (IOException e) {
            return -1;
        }
    }

    private static Object readBytes(Value[] args) {
        try {
            byte[] bytes = Files.readAllBytes(Path.of(args[0].asString()));
            int[] out = new int[bytes.length];
            for (int i = 0; i < bytes.length; i++) out[i] = bytes[i] & 0xFF;
            return out;
        } catch (IOException e) {
            return null;
        }
    }

    private static Object writeBytes(Value[] args, boolean append) {
        try {
            byte[] bytes = new byte[(int) args[1].getArraySize()];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) (args[1].getArrayElement(i).asInt() & 0xFF);
            }
            Path p = Path.of(args[0].asString());
            if (append) {
                Files.write(p, bytes, java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND);
            } else {
                Files.write(p, bytes);
            }
            return 0;
        } catch (IOException e) {
            return -1;
        }
    }

    private static Object dirCreate(Value[] args, boolean recursive) {
        try {
            if (recursive) {
                Files.createDirectories(Path.of(args[0].asString()));
            } else {
                Files.createDirectory(Path.of(args[0].asString()));
            }
            return 0;
        } catch (IOException e) {
            return -1;
        }
    }

    private static Object dirList(Value[] args) {
        try (var stream = Files.list(Path.of(args[0].asString()))) {
            List<String> names = new ArrayList<>();
            stream.map(p -> p.toString()).sorted().forEach(names::add);
            return names.toArray(new String[0]);
        } catch (IOException e) {
            return null;
        }
    }
}