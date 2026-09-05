package dev.kof.compiler;

/**
 * Runtime de kof.media — imagens (javax.imageio), áudio/WAV e microfone
 * (javax.sound.sampled). Gerado no KofRuntime junto com JvmRuntime;
 * separado em arquivo próprio pelo mesmo motivo do JvmWebRuntime (limite
 * de 65535 bytes por string constant pool).
 *
 * Filosofia: a linguagem NÃO transporta imagem/áudio como String gigante
 * (nem base64 literal no fonte, nem data-URI colado à mão). O app trata o
 * ARQUIVO: abre, manipula, salva. Web server entrega o arquivo do disco
 * com content-type correto (serveDir). O data-URI só existe como opção
 * explícita (img.dataUri()) para o caso em que o destino só aceita URI.
 */
final class JvmMediaRuntime {

    private JvmMediaRuntime() {}

    static String source() {
        return """
                // ── kof.media — imagens, áudio, microfone ─────────────

                public static final class KofAudioData {
                    final byte[] pcm;          // PCM_SIGNED 16-bit little-endian
                    final int sampleRate;      // Hz
                    final int channels;
                    KofAudioData(byte[] pcm, int sampleRate, int channels) {
                        this.pcm = pcm;
                        this.sampleRate = sampleRate;
                        this.channels = channels;
                    }
                    int durationMs() {
                        if (sampleRate <= 0 || channels <= 0) return 0;
                        long frames = pcm.length / (2L * channels);
                        return (int) (frames * 1000L / sampleRate);
                    }
                }

                public static final class KofImageFile {
                    final java.awt.image.BufferedImage image;
                    final String format;
                    final String path;
                    KofImageFile(java.awt.image.BufferedImage image, String format, String path) {
                        this.image = image;
                        this.format = format;
                        this.path = path;
                    }
                }

                public static final class KofVideoFile {
                    final byte[] data;
                    final String format;
                    final String path;
                    final int durationMs;
                    KofVideoFile(byte[] data, String format, String path, int durationMs) {
                        this.data = data;
                        this.format = format;
                        this.path = path;
                        this.durationMs = durationMs;
                    }
                }

                private static final java.util.concurrent.ConcurrentHashMap<Integer, KofImageFile> KOF_MEDIA_IMAGES =
                        new java.util.concurrent.ConcurrentHashMap<>();
                private static final java.util.concurrent.ConcurrentHashMap<Integer, KofAudioData> KOF_MEDIA_AUDIO =
                        new java.util.concurrent.ConcurrentHashMap<>();
                private static final java.util.concurrent.ConcurrentHashMap<Integer, KofVideoFile> KOF_MEDIA_VIDEOS =
                        new java.util.concurrent.ConcurrentHashMap<>();
                private static final java.util.concurrent.atomic.AtomicInteger KOF_MEDIA_SEQ =
                        new java.util.concurrent.atomic.AtomicInteger();

                /** Raiz do projeto (definida pelo CLI: -Dkof.root) — caminhos
                 *  relativos do app resolvem contra ela, não contra o CWD. */
                public static String kof_media_root() {
                    String root = System.getProperty("kof.root");
                    return root == null || root.isBlank()
                            ? System.getProperty("user.dir", ".") : root;
                }

                private static java.nio.file.Path kof_media_resolve(String p) {
                    java.nio.file.Path path = java.nio.file.Path.of(p);
                    if (path.isAbsolute()) return path.toAbsolutePath().normalize();
                    return java.nio.file.Path.of(kof_media_root())
                            .toAbsolutePath().normalize().resolve(p).normalize();
                }

                private static String kof_media_format(String fileName) {
                    String n = fileName == null ? "" : fileName.toLowerCase();
                    int dot = n.lastIndexOf('.');
                    if (dot < 0) return "png";
                    String ext = n.substring(dot + 1);
                    if (ext.equals("jpg") || ext.equals("jpeg")) return "jpeg";
                    if (ext.equals("gif")) return "gif";
                    if (ext.equals("bmp")) return "bmp";
                    if (ext.equals("webp")) return "webp";
                    return "png";
                }

                // ── Bitmap ────────────────────────────────────────────

                public static int kof_media_image_open(String path) {
                    try {
                        java.nio.file.Path p = kof_media_resolve(path);
                        if (!java.nio.file.Files.isRegularFile(p)) {
                            throw new RuntimeException("arquivo não encontrado: " + path);
                        }
                        java.awt.image.BufferedImage img =
                                javax.imageio.ImageIO.read(p.toFile());
                        if (img == null) {
                            throw new RuntimeException(
                                    "formato de imagem não suportado: " + path);
                        }
                        int id = KOF_MEDIA_SEQ.incrementAndGet();
                        KOF_MEDIA_IMAGES.put(id, new KofImageFile(img,
                                kof_media_format(p.getFileName().toString()), path));
                        return id;
                    } catch (java.io.IOException e) {
                        throw new RuntimeException("Image.open falhou: " + e.getMessage(), e);
                    }
                }

                private static KofImageFile kof_media_image(int id) {
                    KofImageFile f = KOF_MEDIA_IMAGES.get(id);
                    if (f == null) throw new IllegalStateException("imagem inválida: " + id);
                    return f;
                }

                public static int kof_media_image_width(int id) {
                    return kof_media_image(id).image.getWidth();
                }

                public static int kof_media_image_height(int id) {
                    return kof_media_image(id).image.getHeight();
                }

                public static String kof_media_image_format(int id) {
                    return kof_media_image(id).format;
                }

                public static int kof_media_image_save(int id, String path) {
                    return kof_media_image_save_fmt(id, path,
                            kof_media_format(java.nio.file.Path.of(path).getFileName().toString()));
                }

                public static int kof_media_image_save_fmt(int id, String path, String fmt) {
                    try {
                        java.nio.file.Path p = kof_media_resolve(path);
                        if (p.getParent() != null) {
                            java.nio.file.Files.createDirectories(p.getParent());
                        }
                        boolean ok = javax.imageio.ImageIO.write(
                                kof_media_image(id).image, fmt, p.toFile());
                        if (!ok) {
                            throw new RuntimeException(
                                    "sem writer para o formato '" + fmt + "'");
                        }
                        return 1;
                    } catch (java.io.IOException e) {
                        throw new RuntimeException("Image.save falhou: " + e.getMessage(), e);
                    }
                }

                /** data-URI gerado EM RUNTIME a partir do arquivo (não é um
                 *  literal no fonte) — para destinos que só aceitam URI. */
                public static String kof_media_image_data_uri(int id) {
                    try {
                        KofImageFile f = kof_media_image(id);
                        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                        if (!javax.imageio.ImageIO.write(f.image, f.format, bos)) {
                            throw new RuntimeException(
                                    "sem writer para o formato '" + f.format + "'");
                        }
                        return "data:image/" + f.format + ";base64,"
                                + java.util.Base64.getEncoder().encodeToString(bos.toByteArray());
                    } catch (java.io.IOException e) {
                        throw new RuntimeException("Image.dataUri falhou: " + e.getMessage(), e);
                    }
                }

                public static int[] kof_media_image_bytes(int id) {
                    return kof_media_image_bytes_fmt(id, kof_media_image(id).format);
                }

                public static int[] kof_media_image_bytes_fmt(int id, String fmt) {
                    try {
                        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                        if (!javax.imageio.ImageIO.write(kof_media_image(id).image, fmt, bos)) {
                            throw new RuntimeException(
                                    "sem writer para o formato '" + fmt + "'");
                        }
                        byte[] b = bos.toByteArray();
                        int[] out = new int[b.length];
                        for (int i = 0; i < b.length; i++) out[i] = b[i];
                        return out;
                    } catch (java.io.IOException e) {
                        throw new RuntimeException("Image.bytes falhou: " + e.getMessage(), e);
                    }
                }

                public static void kof_media_image_close(int id) {
                    KOF_MEDIA_IMAGES.remove(id);
                }

                // ── Video ──────────────────────────────────────────
                // Vídeo = arquivo de mídia: o app NÃO decodiza frames (gap
                // honesto — sem lib externa no JVM). A API expõe metadados
                // do container (formato, tamanho, duração) + bytes para
                // servir/streamar (serveDir + Range requests).

                public static int kof_media_video_open(String path) {
                    try {
                        java.nio.file.Path p = kof_media_resolve(path);
                        if (!java.nio.file.Files.isRegularFile(p)) {
                            throw new RuntimeException("arquivo não encontrado: " + path);
                        }
                        byte[] data = java.nio.file.Files.readAllBytes(p);
                        int id = KOF_MEDIA_SEQ.incrementAndGet();
                        KOF_MEDIA_VIDEOS.put(id, new KofVideoFile(data,
                                kof_media_video_format(p.getFileName().toString()),
                                path, kof_media_mp4_duration_ms(data)));
                        return id;
                    } catch (java.io.IOException e) {
                        throw new RuntimeException("Video.open falhou: " + e.getMessage(), e);
                    }
                }

                private static String kof_media_video_format(String fileName) {
                    String n = fileName == null ? "" : fileName.toLowerCase();
                    int dot = n.lastIndexOf('.');
                    if (dot < 0) return "mp4";
                    String ext = n.substring(dot + 1);
                    if (ext.equals("mpeg") || ext.equals("mpg")) return "mpeg";
                    return ext;
                }

                private static KofVideoFile kof_media_video(int id) {
                    KofVideoFile v = KOF_MEDIA_VIDEOS.get(id);
                    if (v == null) throw new IllegalStateException("vídeo inválido: " + id);
                    return v;
                }

                public static String kof_media_video_path(int id) {
                    return kof_media_video(id).path;
                }

                public static int kof_media_video_size(int id) {
                    return kof_media_video(id).data.length;
                }

                public static String kof_media_video_format(int id) {
                    return kof_media_video(id).format;
                }

                /** Duração em ms — MP4/MOV lida do box 'mvhd' (ISO BMFF);
                 *  outros containers (MKV/WebM/AVI) → 0 (desconhecido). */
                public static int kof_media_video_duration_ms(int id) {
                    return kof_media_video(id).durationMs;
                }

                /** Duração de MP4/MOV: varre os boxes até 'mvhd' e lê
                 *  duration/timescale (v0 32-bit e v1 64-bit). */
                static int kof_media_mp4_duration_ms(byte[] b) {
                    long size = b.length;
                    long pos = 0;
                    while (pos + 8 <= size) {
                        long boxSize = kof_media_be32(b, (int) pos);
                        if (boxSize < 8) break;
                        String type = new String(b, (int) pos + 4, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
                        if ("moov".equals(type)) {
                            return kof_media_mp4_mvhd_duration(b, (int) pos + 8, (int) (pos + boxSize));
                        }
                        pos += boxSize;
                    }
                    return 0;
                }

                private static int kof_media_mp4_mvhd_duration(byte[] b, int from, int to) {
                    int pos = from;
                    while (pos + 8 <= to) {
                        long boxSize = kof_media_be32(b, pos);
                        if (boxSize < 8) break;
                        String type = new String(b, pos + 4, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
                        if ("mvhd".equals(type)) {
                            int version = b[pos + 8] & 0xFF;
                            if (version == 1) {
                                // size,type(8) version(8) flags(9..12)
                                // creation(12..20) mod(20..28) timescale(28..32)
                                // duration(32..40)
                                if (pos + 40 > to) break;
                                long duration = ((long) (b[pos + 32] & 0xFF) << 56)
                                        | ((long) (b[pos + 33] & 0xFF) << 48)
                                        | ((long) (b[pos + 34] & 0xFF) << 40)
                                        | ((long) (b[pos + 35] & 0xFF) << 32)
                                        | ((long) kof_media_be32(b, pos + 36));
                                int timescale = kof_media_be32(b, pos + 28);
                                return timescale > 0 ? (int) Math.min(Integer.MAX_VALUE,
                                        duration * 1000L / timescale) : 0;
                            }
                            // v0: size,type(8) version(8) flags(9..12)
                            //   creation(12..16) mod(16..20) timescale(20..24)
                            //   duration(24..28)
                            if (pos + 28 > to) break;
                            long duration = kof_media_be32(b, pos + 24);
                            int timescale = kof_media_be32(b, pos + 20);
                            return timescale > 0 ? (int) Math.min(Integer.MAX_VALUE,
                                    duration * 1000L / timescale) : 0;
                        }
                        pos += (int) boxSize;
                    }
                    return 0;
                }

                private static int kof_media_be32(byte[] b, int off) {
                    return ((b[off] & 0xFF) << 24) | ((b[off + 1] & 0xFF) << 16)
                            | ((b[off + 2] & 0xFF) << 8) | (b[off + 3] & 0xFF);
                }

                public static int[] kof_media_video_bytes(int id) {
                    byte[] d = kof_media_video(id).data;
                    int[] out = new int[d.length];
                    for (int i = 0; i < d.length; i++) out[i] = d[i];
                    return out;
                }

                public static void kof_media_video_close(int id) {
                    KOF_MEDIA_VIDEOS.remove(id);
                }

                // ── Audio (WAV) ───────────────────────────────────────

                public static int kof_media_audio_open_wav(String path) {
                    try {
                        java.nio.file.Path p = kof_media_resolve(path);
                        return kof_media_audio_store(kof_media_read_wav(p));
                    } catch (java.io.IOException e) {
                        throw new RuntimeException("Audio.openWav falhou: " + e.getMessage(), e);
                    }
                }

                public static int kof_media_audio_sample_rate(int id) {
                    return kof_media_audio(id).sampleRate;
                }

                public static int kof_media_audio_duration_ms(int id) {
                    return kof_media_audio(id).durationMs();
                }

                public static int kof_media_audio_save_wav(int id, String path) {
                    try {
                        java.nio.file.Path p = kof_media_resolve(path);
                        if (p.getParent() != null) {
                            java.nio.file.Files.createDirectories(p.getParent());
                        }
                        java.nio.file.Files.write(p, kof_media_write_wav(kof_media_audio(id)));
                        return 1;
                    } catch (java.io.IOException e) {
                        throw new RuntimeException("Audio.saveWav falhou: " + e.getMessage(), e);
                    }
                }

                public static int[] kof_media_audio_pcm_bytes(int id) {
                    byte[] pcm = kof_media_audio(id).pcm;
                    int[] out = new int[pcm.length];
                    for (int i = 0; i < pcm.length; i++) out[i] = pcm[i];
                    return out;
                }

                /** Constrói um Audio a partir de PCM 16-bit (teste/síntese). */
                public static int kof_media_audio_from_pcm_bytes(int[] pcm, int sampleRate, int channels) {
                    byte[] b = new byte[pcm.length];
                    for (int i = 0; i < pcm.length; i++) b[i] = (byte) pcm[i];
                    return kof_media_audio_store(new KofAudioData(b, sampleRate, channels));
                }

                private static int kof_media_audio_store(KofAudioData a) {
                    int id = KOF_MEDIA_SEQ.incrementAndGet();
                    KOF_MEDIA_AUDIO.put(id, a);
                    return id;
                }

                private static KofAudioData kof_media_audio(int id) {
                    KofAudioData a = KOF_MEDIA_AUDIO.get(id);
                    if (a == null) throw new IllegalStateException("áudio inválido: " + id);
                    return a;
                }

                private static int kof_media_le16(byte[] b, int off) {
                    return (b[off] & 0xFF) | (b[off + 1] & 0xFF) << 8;
                }

                private static int kof_media_le32(byte[] b, int off) {
                    return (b[off] & 0xFF) | (b[off + 1] & 0xFF) << 8
                            | (b[off + 2] & 0xFF) << 16 | (b[off + 3] & 0xFF) << 24;
                }

                /** Lê WAV RIFF — só PCM 16-bit (o resto é gap honesto). */
                private static KofAudioData kof_media_read_wav(java.nio.file.Path p)
                        throws java.io.IOException {
                    byte[] all = java.nio.file.Files.readAllBytes(p);
                    if (all.length < 12
                            || all[0] != 'R' || all[1] != 'I' || all[2] != 'F' || all[3] != 'F'
                            || all[8] != 'W' || all[9] != 'A' || all[10] != 'V' || all[11] != 'E') {
                        throw new RuntimeException("não é um WAV RIFF: " + p);
                    }
                    int channels = 0, sampleRate = 0, bits = 0;
                    byte[] data = null;
                    int pos = 12;
                    while (pos + 8 <= all.length) {
                        String cid = new String(all, pos, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
                        int size = kof_media_le32(all, pos + 4);
                        int body = pos + 8;
                        if (body + size > all.length) break;
                        if ("fmt ".equals(cid)) {
                            int audioFormat = kof_media_le16(all, body);
                            channels = kof_media_le16(all, body + 2);
                            sampleRate = kof_media_le32(all, body + 4);
                            bits = kof_media_le16(all, body + 14);
                            if (audioFormat != 1) {
                                throw new RuntimeException(
                                        "WAV não suportado: codec " + audioFormat + " (precisa de PCM 1)");
                            }
                        } else if ("data".equals(cid)) {
                            data = new byte[size];
                            System.arraycopy(all, body, data, 0, size);
                        }
                        pos = body + size + (size & 1);
                    }
                    if (data == null || sampleRate <= 0 || bits != 16) {
                        throw new RuntimeException(
                                "WAV não suportado: precisa de PCM 16-bit (bits=" + bits + ")");
                    }
                    return new KofAudioData(data, sampleRate, channels);
                }

                private static byte[] kof_media_write_wav(KofAudioData a) {
                    byte[] pcm = a.pcm;
                    int ch = a.channels, rate = a.sampleRate;
                    int byteRate = rate * ch * 2;
                    int blockAlign = ch * 2;
                    byte[] out = new byte[44 + pcm.length];
                    out[0] = 'R'; out[1] = 'I'; out[2] = 'F'; out[3] = 'F';
                    kof_media_put_le32(out, 4, 36 + pcm.length);
                    out[8] = 'W'; out[9] = 'A'; out[10] = 'V'; out[11] = 'E';
                    out[12] = 'f'; out[13] = 'm'; out[14] = 't'; out[15] = ' ';
                    kof_media_put_le32(out, 16, 16);
                    kof_media_put_le16(out, 20, 1);
                    kof_media_put_le16(out, 22, ch);
                    kof_media_put_le32(out, 24, rate);
                    kof_media_put_le32(out, 28, byteRate);
                    kof_media_put_le16(out, 32, blockAlign);
                    kof_media_put_le16(out, 34, 16);
                    out[36] = 'd'; out[37] = 'a'; out[38] = 't'; out[39] = 'a';
                    kof_media_put_le32(out, 40, pcm.length);
                    System.arraycopy(pcm, 0, out, 44, pcm.length);
                    return out;
                }

                private static void kof_media_put_le16(byte[] b, int off, int v) {
                    b[off] = (byte) (v & 0xFF);
                    b[off + 1] = (byte) ((v >> 8) & 0xFF);
                }

                private static void kof_media_put_le32(byte[] b, int off, int v) {
                    b[off] = (byte) (v & 0xFF);
                    b[off + 1] = (byte) ((v >> 8) & 0xFF);
                    b[off + 2] = (byte) ((v >> 16) & 0xFF);
                    b[off + 3] = (byte) ((v >> 24) & 0xFF);
                }

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
