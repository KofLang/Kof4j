package dev.kof.compiler;

/**
 * Fragmento do source do KofRuntime gerado (REFACTOR-500 Fase 8).
 * kof.media (Bitmap/Video/Audio-WAV) - parte 1/2 de JvmMediaRuntime. Concatenacao preserva byte-a-byte.
 */
final class JvmMediaCoreRuntime {

    private JvmMediaCoreRuntime() {}

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

                """;
    }
}
