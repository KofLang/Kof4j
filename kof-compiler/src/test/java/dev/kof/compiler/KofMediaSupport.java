package dev.kof.compiler;

import java.nio.charset.StandardCharsets;

/**
 * Fixtures de bytes de container para o E2E do {@code kof.media}
 * ({@code KofMediaE2ETest}): builders puros de WAV e de boxes ISO-BMFF (MP4).
 * Vivem fora da classe de teste para mantê-la abaixo do limite de 500 linhas de
 * teste (Fase 3 da arquitetura de testes, {@code D-TEST-ARCHITECTURE-GO}); os
 * testes e o nome da classe seguem no {@code KofMediaE2ETest} — zero drift de
 * citação.
 */
abstract class KofMediaSupport {

    protected static byte[] makeWav(int sampleRate, int channels, int framesOfSine) {
        byte[] pcm = new byte[framesOfSine * channels * 2];
        for (int i = 0; i < framesOfSine; i++) {
            int sample = (int) (Math.sin(i / 200.0) * 10000);
            for (int ch = 0; ch < channels; ch++) {
                int off = (i * channels + ch) * 2;
                pcm[off] = (byte) (sample & 0xFF);
                pcm[off + 1] = (byte) ((sample >> 8) & 0xFF);
            }
        }
        byte[] out = new byte[44 + pcm.length];
        out[0] = 'R'; out[1] = 'I'; out[2] = 'F'; out[3] = 'F';
        int dataSize = pcm.length;
        out[4] = (byte) (36 + dataSize); out[5] = (byte) ((36 + dataSize) >> 8);
        out[6] = (byte) ((36 + dataSize) >> 16); out[7] = (byte) ((36 + dataSize) >> 24);
        out[8] = 'W'; out[9] = 'A'; out[10] = 'V'; out[11] = 'E';
        out[12] = 'f'; out[13] = 'm'; out[14] = 't'; out[15] = ' ';
        out[16] = 16;
        out[20] = 1; out[21] = 0;                       // PCM (0x0001 little-endian)
        out[22] = (byte) channels;
        out[24] = (byte) (sampleRate & 0xFF); out[25] = (byte) ((sampleRate >> 8) & 0xFF);
        out[26] = (byte) ((sampleRate >> 16) & 0xFF); out[27] = (byte) ((sampleRate >> 24) & 0xFF);
        int byteRate = sampleRate * channels * 2;
        out[28] = (byte) (byteRate & 0xFF); out[29] = (byte) ((byteRate >> 8) & 0xFF);
        out[30] = (byte) ((byteRate >> 16) & 0xFF); out[31] = (byte) ((byteRate >> 24) & 0xFF);
        out[32] = (byte) (channels * 2);
        out[34] = 16;
        out[36] = 'd'; out[37] = 'a'; out[38] = 't'; out[39] = 'a';
        out[40] = (byte) (dataSize & 0xFF); out[41] = (byte) ((dataSize >> 8) & 0xFF);
        out[42] = (byte) ((dataSize >> 16) & 0xFF); out[43] = (byte) ((dataSize >> 24) & 0xFF);
        System.arraycopy(pcm, 0, out, 44, pcm.length);
        return out;
    }

    protected static byte[] mp4Box(String type, byte[] payload) {
        byte[] out = new byte[8 + payload.length];
        out[0] = (byte) (out.length >>> 24);
        out[1] = (byte) (out.length >>> 16);
        out[2] = (byte) (out.length >>> 8);
        out[3] = (byte) out.length;
        System.arraycopy(type.getBytes(StandardCharsets.ISO_8859_1), 0, out, 4, 4);
        System.arraycopy(payload, 0, out, 8, payload.length);
        return out;
    }

    /** MP4 mínimo com moov/mvhd v0: timescale=1000, duration=3000 → 3000ms. */
    protected static byte[] makeMp4() {
        byte[] mvhdPayload = new byte[100];
        mvhdPayload[0] = 0;                              // version 0
        // timescale at payload[12..15] = 1000 (0x03E8)
        mvhdPayload[12] = 0; mvhdPayload[13] = 0;
        mvhdPayload[14] = (byte) 0x03; mvhdPayload[15] = (byte) 0xE8;
        // duration at payload[16..19] = 3000 (0x0BB8)
        mvhdPayload[16] = 0; mvhdPayload[17] = 0;
        mvhdPayload[18] = (byte) 0x0B; mvhdPayload[19] = (byte) 0xB8;
        byte[] mvhd = mp4Box("mvhd", mvhdPayload);
        byte[] moov = mp4Box("moov", mvhd);
        byte[] ftypPayload = "isom".getBytes(StandardCharsets.ISO_8859_1);
        byte[] head = mp4Box("ftyp", ftypPayload);
        byte[] out = new byte[head.length + moov.length];
        System.arraycopy(head, 0, out, 0, head.length);
        System.arraycopy(moov, 0, out, head.length, moov.length);
        return out;
    }

    /** Box ISO-BMFF na forma de tamanho ESTENDIDO (size==1, ISO/IEC
     *  14496-12 §4.2 — comum em 'free'/'wide'/'mdat' de arquivo grande):
     *  size32=1, depois 8 bytes de largesize (big-endian) com o tamanho
     *  real. #623: antes do fix, QUALQUER box nessa forma antes de 'moov'
     *  desalinhava o scanner e durationMs() voltava 0 silenciosamente. */
    protected static byte[] mp4Box64(String type, byte[] payload) {
        long total = 16L + payload.length;
        byte[] out = new byte[(int) total];
        out[0] = 0; out[1] = 0; out[2] = 0; out[3] = 1; // size32 == 1 (extended)
        System.arraycopy(type.getBytes(StandardCharsets.ISO_8859_1), 0, out, 4, 4);
        for (int i = 0; i < 8; i++) {
            out[8 + i] = (byte) (total >>> (8 * (7 - i)));
        }
        System.arraycopy(payload, 0, out, 16, payload.length);
        return out;
    }

    /** Igual a {@link #makeMp4()}, mas com um box 'free' de tamanho
     *  ESTENDIDO (size==1) entre 'ftyp' e 'moov' — #623. */
    protected static byte[] makeMp4WithExtendedSizeBoxBeforeMoov() {
        byte[] mvhdPayload = new byte[100];
        mvhdPayload[0] = 0;
        mvhdPayload[14] = (byte) 0x03; mvhdPayload[15] = (byte) 0xE8; // timescale 1000
        mvhdPayload[18] = (byte) 0x0B; mvhdPayload[19] = (byte) 0xB8; // duration 3000
        byte[] mvhd = mp4Box("mvhd", mvhdPayload);
        byte[] moov = mp4Box("moov", mvhd);
        byte[] head = mp4Box("ftyp", "isom".getBytes(StandardCharsets.ISO_8859_1));
        byte[] free = mp4Box64("free", new byte[8]);
        byte[] out = new byte[head.length + free.length + moov.length];
        System.arraycopy(head, 0, out, 0, head.length);
        System.arraycopy(free, 0, out, head.length, free.length);
        System.arraycopy(moov, 0, out, head.length + free.length, moov.length);
        return out;
    }
}
