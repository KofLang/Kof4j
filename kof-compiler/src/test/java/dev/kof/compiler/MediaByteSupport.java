package dev.kof.compiler;

/**
 * Helpers de montagem de bytes de container (MP4/WAV) compartilhados pelos E2E
 * de mídia cross e native ({@code MediaCrossE2ETest}/{@code MediaNativeE2ETest}).
 * Vive fora das classes de teste para deduplicar e para mantê-las enxutas (Fase
 * 4/harness da arquitetura de testes, {@code D-TEST-ARCHITECTURE-GO}); os testes
 * e os nomes das classes seguem nos dois arquivos — zero drift de citação.
 */
abstract class MediaByteSupport {

    protected static int be32(byte[] b, int off, long v) {
        b[off] = (byte) (v >> 24); b[off + 1] = (byte) (v >> 16);
        b[off + 2] = (byte) (v >> 8); b[off + 3] = (byte) v;
        return off + 4;
    }

    protected static void type4(byte[] b, int off, String t) {
        for (int i = 0; i < 4; i++) b[off + i] = (byte) t.charAt(i);
    }

    protected static byte[] clipMp4() {
        byte[] b = new byte[20 + 32 + 108];
        be32(b, 0, 20); type4(b, 4, "ftyp"); type4(b, 8, "isom");
        be32(b, 20, 1); type4(b, 24, "free"); be32(b, 28, 0); be32(b, 32, 32);
        b[36] = (byte) 0xB8; b[37] = (byte) 0xFE; b[38] = 0x01; b[39] = (byte) 0x80;
        be32(b, 52, 108); type4(b, 56, "moov");
        be32(b, 60, 100); type4(b, 64, "mvhd");
        b[68] = 0;
        be32(b, 80, 1000);
        be32(b, 84, 3000);
        return b;
    }

    protected static byte[] clipZeroSizeMp4() {
        byte[] b = new byte[16 + 108];
        be32(b, 0, 0); type4(b, 4, "free");
        be32(b, 16, 108); type4(b, 20, "moov");
        be32(b, 24, 100); type4(b, 28, "mvhd");
        be32(b, 44, 1000); be32(b, 48, 3000);
        return b;
    }

    protected static byte[] wav(int format, int channels, int rate, int bits, byte[] data) {
        byte[] b = new byte[44 + data.length];
        type4(b, 0, "RIFF"); le32(b, 4, 36 + data.length); type4(b, 8, "WAVE");
        type4(b, 12, "fmt "); le32(b, 16, 16);
        le16(b, 20, format); le16(b, 22, channels); le32(b, 24, rate);
        le32(b, 28, rate * channels * 2); le16(b, 32, channels * 2); le16(b, 34, bits);
        type4(b, 36, "data"); le32(b, 40, data.length);
        System.arraycopy(data, 0, b, 44, data.length);
        return b;
    }

    protected static void le16(byte[] b, int off, int v) {
        b[off] = (byte) v; b[off + 1] = (byte) (v >> 8);
    }

    protected static void le32(byte[] b, int off, int v) {
        le16(b, off, v & 0xFFFF); le16(b, off + 2, v >> 16);
    }
}
