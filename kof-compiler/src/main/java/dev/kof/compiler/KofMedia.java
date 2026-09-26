package dev.kof.compiler;

import java.util.List;

/**
 * Tabela de dispatch do kof.media — imagens, áudio e microfone.
 *
 * <p>Superfície em Kof:
 *
 * <pre>{@code
 * var img = Image.open("logo.png")     // kof.media.ImageData (handle)
 * img.width()
 * img.save("logo.jpg")
 * var a = Audio.openWav("nota.wav")
 * a.saveWav("copy.wav")
 * var mic = Mic.record(3)              // 3s do microfone padrão
 * mic.saveWav("gravacao.wav")
 * }</pre>
 *
 * <p>Imagens/áudio são manipulados como ARQUIVO ou handle binário interno —
 * a linguagem não faz o app colar base64/HTML/CSS em String literal. O
 * target JVM usa {@code javax.imageio} e {@code javax.sound.sampled}.
 * Os tipos kof.media.* existem só em compile-time; em runtime são Ints.
 */
public final class KofMedia {

    private KofMedia() {}

    static final Type IMAGE_DATA = new Type.ClassType("kof.media", "ImageData", List.of());
    static final Type AUDIO = new Type.ClassType("kof.media", "Audio", List.of());
    static final Type VIDEO = new Type.ClassType("kof.media", "Video", List.of());

    private static final Type STR = BuiltinTypes.STRING;
    private static final Type INT = Type.PrimitiveType.INT;
    private static final Type VOID = Type.PrimitiveType.VOID;
    private static final Type INT_ARRAY = new Type.ArrayType(INT);

    record MediaCall(String function, Type returnType, List<Type> parameterTypes) {}

    /** X10 fatia 3: membros estáticos por namespace (Image/Audio/Video/Mic).
     *  GUARDA: StdCatalogTest exige == switches aninhados do staticCall. */
    static java.util.Map<String, List<String>> functions() {
        return java.util.Map.of(
                "Image", List.of("open"),
                "Audio", List.of("openWav"),
                "Video", List.of("open"),
                "Mic", List.of("record", "list"));
    }

    /** Chamadas estáticas (sem receiver): {@code Image.open}, {@code Audio.openWav},
     *  {@code Mic.record}. O nome da classe-namespace vem primeiro. */
    static MediaCall staticCall(String namespace, String name, int argCount) {
        return switch (namespace) {
            case "Image" -> switch (name) {
                case "open" -> argCount == 1
                        ? new MediaCall("kof_media_image_open", IMAGE_DATA, List.of(STR)) : null;
                default -> null;
            };
            case "Audio" -> switch (name) {
                case "openWav" -> argCount == 1
                        ? new MediaCall("kof_media_audio_open_wav", AUDIO, List.of(STR)) : null;
                default -> null;
            };
            case "Video" -> switch (name) {
                case "open" -> argCount == 1
                        ? new MediaCall("kof_media_video_open", VIDEO, List.of(STR)) : null;
                default -> null;
            };
            case "Mic" -> switch (name) {
                case "record" -> argCount == 1
                        ? new MediaCall("kof_media_mic_record", AUDIO, List.of(INT)) : null;
                case "list" -> argCount == 0
                        ? new MediaCall("kof_media_mic_list",
                                new Type.ClassType("kof", "List", List.of(STR)), List.of()) : null;
                default -> null;
            };
            default -> null;
        };
    }

    static boolean isStaticNamespace(String name) {
        return "Image".equals(name) || "Audio".equals(name) || "Mic".equals(name)
                || "Video".equals(name);
    }

    static boolean isImageData(Type t) { return IMAGE_DATA.equals(t); }
    static boolean isAudio(Type t) { return AUDIO.equals(t); }
    static boolean isVideo(Type t) { return VIDEO.equals(t); }

    /** Handles de mídia são Int em runtime (mesmo modelo dos handles kof.ui)
     *  — o backend JVM os mapeia para o descritor "I". */
    static public boolean isHandleType(Type t) {
        return isImageData(t) || isAudio(t) || isVideo(t);
    }

    /** Métodos em receiver {@code kof.media.ImageData}. */
    static MediaCall imageDataMethod(String name, int argCount) {
        return switch (name) {
            case "width" -> argCount == 0 ? new MediaCall("kof_media_image_width", INT, List.of()) : null;
            case "height" -> argCount == 0 ? new MediaCall("kof_media_image_height", INT, List.of()) : null;
            case "format" -> argCount == 0 ? new MediaCall("kof_media_image_format", STR, List.of()) : null;
            case "save" -> argCount == 1
                    ? new MediaCall("kof_media_image_save", INT, List.of(INT, STR)) : null;
            case "saveAs" -> argCount == 2
                    ? new MediaCall("kof_media_image_save_fmt", INT, List.of(INT, STR, STR)) : null;
            case "dataUri" -> argCount == 0
                    ? new MediaCall("kof_media_image_data_uri", STR, List.of(INT)) : null;
            case "bytes" -> argCount == 0
                    ? new MediaCall("kof_media_image_bytes", INT_ARRAY, List.of(INT)) : null;
            case "bytesAs" -> argCount == 1
                    ? new MediaCall("kof_media_image_bytes_fmt", INT_ARRAY, List.of(INT, STR)) : null;
            case "close" -> argCount == 0 ? new MediaCall("kof_media_image_close", VOID, List.of(INT)) : null;
            default -> null;
        };
    }

    /** Métodos em receiver {@code kof.media.Audio}. */
    static MediaCall audioMethod(String name, int argCount) {
        return switch (name) {
            case "sampleRate" -> argCount == 0
                    ? new MediaCall("kof_media_audio_sample_rate", INT, List.of()) : null;
            case "durationMs" -> argCount == 0
                    ? new MediaCall("kof_media_audio_duration_ms", INT, List.of()) : null;
            case "saveWav" -> argCount == 1
                    ? new MediaCall("kof_media_audio_save_wav", INT, List.of(INT, STR)) : null;
            case "pcmBytes" -> argCount == 0
                    ? new MediaCall("kof_media_audio_pcm_bytes", INT_ARRAY, List.of(INT)) : null;
            default -> null;
        };
    }

    /** Métodos em receiver {@code kof.media.Video}. Vídeo é tratado como
     *  ARQUIVO de mídia: o app não decodiza frames (gap honesto — sem lib
     *  externa no JVM); a API expõe metadados do container + bytes para
     *  servir/streamar. */
    static MediaCall videoMethod(String name, int argCount) {
        return switch (name) {
            case "path" -> argCount == 0 ? new MediaCall("kof_media_video_path", STR, List.of()) : null;
            case "size" -> argCount == 0 ? new MediaCall("kof_media_video_size", INT, List.of()) : null;
            case "format" -> argCount == 0 ? new MediaCall("kof_media_video_format", STR, List.of()) : null;
            case "durationMs" -> argCount == 0
                    ? new MediaCall("kof_media_video_duration_ms", INT, List.of()) : null;
            case "bytes" -> argCount == 0
                    ? new MediaCall("kof_media_video_bytes", INT_ARRAY, List.of()) : null;
            case "close" -> argCount == 0 ? new MediaCall("kof_media_video_close", VOID, List.of()) : null;
            default -> null;
        };
    }

    /** Método em qualquer handle de mídia (ImageData/Audio/Video). */
    static MediaCall handleMethod(Type receiver, String name, int argCount) {
        if (isImageData(receiver)) return imageDataMethod(name, argCount);
        if (isAudio(receiver)) return audioMethod(name, argCount);
        if (isVideo(receiver)) return videoMethod(name, argCount);
        return null;
    }

    /**
     * D-FULL-PARITY-050 linha 4 — a UNICA fonte de verdade de "que face de
     * media tem runtime em que target". Os dois gates de lowerer
     * ({@code ExpressionUiMediaCallLowerer} estatico,
     * {@code ExpressionBuiltinInstanceCalls} face de handle) delegam aqui;
     * a fatia 2 (cross riscv64/aarch64) abre por ENTRADA DE TABELA, nao por
     * reescrita de gate. JS nunca abre sem decisao de engine (regra 6).
     */
    static boolean mediaFaceReady(Target target, String function) {
        if (function == null) return false;
        if (function.startsWith("kof_media_video_")) {
            // FATIA 2A (26/09): o Video cross (riscv64; aarch64 herda pelo
            // tradutor) pousou em NativeRiscvAsmMedia/Mp4 — E2E byte a byte
            // vs oraculo JVM (MediaCrossE2ETest).
            return target == Target.NATIVE || target == Target.NATIVE_RISCV64
                    || target == Target.NATIVE_AARCH64;
        }
        return function.startsWith("kof_media_audio_") && target == Target.NATIVE;
    }

    static String gapCode(String function) {
        // `app.serveDir` NÃO passa por aqui — é método de instância do app
        // (KofWeb.instanceMethod → kof_web_serve_dir) e o gap WEB005 é
        // emitido por KofWeb.gapCode. O `appServeDir` estático que existia
        // aqui era código morto e mantinha um SEGUNDO mapeamento (a origem
        // da divergência doc×código: as docs prometiam WEB005, o gate web
        // emitia WEB001 — corrigido 17/09).
        return switch (function) {
            case "kof_media_mic_record" -> "MEDIA003";
            default -> "MEDIA001";
        };
    }
}
