package dev.kof.compiler;

/**
 * Fragmento do source do KofRuntime gerado (REFACTOR-500 Fase 8).
 * kof.media Mic + web serving (serveDir/estaticos/Range) - parte 2/2 de JvmMediaRuntime. Concatenacao preserva byte-a-byte.
 */
final class JvmMediaWebRuntime {

    private JvmMediaWebRuntime() {}

    static String source() {
        return """
                // ── Mic ───────────────────────────────────────────────

                public static java.util.ArrayList<String> kof_media_mic_list() {
                    java.util.ArrayList<String> out = new java.util.ArrayList<>();
                    for (javax.sound.sampled.Mixer.Info m :
                            javax.sound.sampled.AudioSystem.getMixerInfo()) {
                        out.add(m.getName());
                    }
                    return out;
                }

                private static javax.sound.sampled.AudioFormat kof_media_mic_format() {
                    return new javax.sound.sampled.AudioFormat(
                            javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
                            16000, 16, 1, 2, 16000.0f, true);
                }

                /** Captura `seconds` do microfone padrão → Audio (PCM 16k mono). */
                public static int kof_media_mic_record(int seconds) {
                    // Qualquer falha de hardware de áudio (linha não suportada,
                    // LineUnavailable, IllegalArgumentException/IllegalState do
                    // ALSA dummy, UnsatisfiedLinkError sem lib nativa) é o gap
                    // honesto MEDIA003 — nunca um 500 sem marcador.
                    try {
                        return kof_media_mic_record_impl(seconds);
                    } catch (RuntimeException e) {
                        if (e.getMessage() != null && e.getMessage().contains("MEDIA003")) throw e;
                        throw new RuntimeException("microfone indisponível (MEDIA003): " + e.getMessage(), e);
                    } catch (Throwable e) {
                        throw new RuntimeException("microfone indisponível (MEDIA003): " + e, e);
                    }
                }

                private static int kof_media_mic_record_impl(int seconds) {
                    javax.sound.sampled.AudioFormat fmt = kof_media_mic_format();
                    javax.sound.sampled.DataLine.Info info =
                            new javax.sound.sampled.TargetDataLine.Info(
                                    javax.sound.sampled.TargetDataLine.class, fmt);
                    try {
                        if (!javax.sound.sampled.AudioSystem.isLineSupported(info)) {
                            throw new RuntimeException(
                                    "sem microfone disponível neste ambiente (MEDIA003)");
                        }
                    } catch (RuntimeException e) {
                        if (e.getMessage() != null && e.getMessage().contains("sem microfone")) throw e;
                        throw new RuntimeException(
                                "sem microfone disponível neste ambiente (MEDIA003)", e);
                    }
                    int total = 16000 * Math.max(1, seconds) * 2;
                    byte[] buf = new byte[total];
                    int read = 0;
                    try (var line = (javax.sound.sampled.TargetDataLine)
                            javax.sound.sampled.AudioSystem.getLine(info)) {
                        line.open(fmt);
                        line.start();
                        while (read < buf.length) {
                            int n = line.read(buf, read, Math.min(2048, buf.length - read));
                            if (n <= 0) break;
                            read += n;
                        }
                        line.stop();
                    } catch (javax.sound.sampled.LineUnavailableException e) {
                        throw new RuntimeException(
                                "microfone indisponível (MEDIA003): " + e.getMessage(), e);
                    }
                    byte[] pcm = new byte[read];
                    System.arraycopy(buf, 0, pcm, 0, read);
                    return kof_media_audio_store(new KofAudioData(pcm, 16000, 1));
                }

                // ── web: serving de arquivos (serveDir) ──────────────

                public static void kof_web_serve_dir(String appId, String prefix, String dir) {
                    String p = prefix.startsWith("/") ? prefix : "/" + prefix;
                    while (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
                    java.nio.file.Path d = kof_media_resolve(dir);
                    kof_web_app(appId).staticDirs.add(
                            new WebApp.StaticDir(p, d.toAbsolutePath().normalize()));
                }

                /** app.health(path) — rota built-in que responde com o estado
                 *  de saúde do app em JSON (status, readiness, liveness). */
                public static void kof_web_health(String appId, String path) {
                    String p = path.startsWith("/") ? path : "/" + path;
                    while (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
                    kof_web_app(appId).healthPaths.add(p);
                }

                private static String kof_web_mime(String fileName) {
                    String n = fileName == null ? "" : fileName.toLowerCase();
                    int dot = n.lastIndexOf('.');
                    if (dot < 0) return "application/octet-stream";
                    switch (n.substring(dot + 1)) {
                        case "html": case "htm": return "text/html; charset=utf-8";
                        case "css": return "text/css; charset=utf-8";
                        case "js": case "mjs": return "text/javascript; charset=utf-8";
                        case "json": return "application/json; charset=utf-8";
                        case "txt": case "md": case "log": return "text/plain; charset=utf-8";
                        case "xml": case "yaml": case "yml": return "application/xml; charset=utf-8";
                        case "svg": return "image/svg+xml";
                        case "png": return "image/png";
                        case "jpg": case "jpeg": return "image/jpeg";
                        case "gif": return "image/gif";
                        case "webp": return "image/webp";
                        case "ico": return "image/x-icon";
                        case "bmp": return "image/bmp";
                        case "avif": return "image/avif";
                        case "wav": return "audio/wav";
                        case "mp3": return "audio/mpeg";
                        case "ogg": case "oga": return "audio/ogg";
                        case "flac": return "audio/flac";
                        case "m4a": case "m4b": return "audio/mp4";
                        case "mp4": case "m4v": return "video/mp4";
                        case "mov": return "video/quicktime";
                        case "webm": return "video/webm";
                        case "mkv": return "video/x-matroska";
                        case "avi": return "video/x-msvideo";
                        case "mpeg": case "mpg": return "video/mpeg";
                        case "ts": return "video/mp2t";
                        case "3gp": return "video/3gpp";
                        case "pdf": return "application/pdf";
                        case "woff": return "font/woff";
                        case "woff2": return "font/woff2";
                        case "ttf": return "font/ttf";
                        case "otf": return "font/otf";
                        case "wasm": return "application/wasm";
                        default: return "application/octet-stream";
                    }
                }

                /** Retorna o arquivo estático para /prefix/... ou null (404).
                 *  Protegido contra path traversal (normaliza e confina). */
                // ── estáticos (serveDir) — com Range p/ vídeo ─────────
                // Match por request em ThreadLocal (evita 3x a resolução de
                // caminho): kof_web_static_match → id; _meta(id) → "mime|total";
                // _read(id, start, end) → bytes do intervalo (inclusivo).

                private static final ThreadLocal<java.nio.file.Path> KOF_WEB_STATIC_MATCHED =
                        new ThreadLocal<>();

                public static int kof_web_static_match(WebApp app, String path) {
                    for (WebApp.StaticDir sd : app.staticDirs) {
                        String rel;
                        if (path.equals(sd.prefix)) {
                            rel = "index.html";
                        } else if (path.startsWith(sd.prefix + "/")) {
                            rel = path.substring(sd.prefix.length() + 1);
                        } else {
                            continue;
                        }
                        java.nio.file.Path f = sd.dir.resolve(rel).normalize();
                        if (!f.startsWith(sd.dir)) continue;          // traversal
                        if (!java.nio.file.Files.isRegularFile(f)) continue;
                        KOF_WEB_STATIC_MATCHED.set(f);
                        return 0;
                    }
                    KOF_WEB_STATIC_MATCHED.remove();
                    return -1;
                }

                public static String kof_web_static_meta() {
                    java.nio.file.Path f = KOF_WEB_STATIC_MATCHED.get();
                    if (f == null) return null;
                    try {
                        long total = java.nio.file.Files.size(f);
                        return kof_web_mime(f.getFileName().toString()) + "|" + total;
                    } catch (java.io.IOException e) {
                        return null;
                    }
                }

                /** Lê [start, end] inclusivo (end clamped no total). */
                public static byte[] kof_web_static_read(long start, long end) {
                    java.nio.file.Path f = KOF_WEB_STATIC_MATCHED.get();
                    if (f == null) return null;
                    try (var ch = java.nio.channels.FileChannel.open(f, java.nio.file.StandardOpenOption.READ)) {
                        long total = ch.size();
                        if (end >= total) end = total - 1;
                        int len = (int) (end - start + 1);
                        byte[] out = new byte[len];
                        int read = 0;
                        ch.position(start);
                        while (read < len) {
                            int n = ch.read(java.nio.ByteBuffer.wrap(out, read, len - read));
                            if (n < 0) break;
                            read += n;
                        }
                        if (read < len) {
                            byte[] trimmed = new byte[read];
                            System.arraycopy(out, 0, trimmed, 0, read);
                            return trimmed;
                        }
                        return out;
                    } catch (java.io.IOException e) {
                        return null;
                    }
                }

                public static void kof_web_static_done() {
                    KOF_WEB_STATIC_MATCHED.remove();
                }
                """;
    }
}
