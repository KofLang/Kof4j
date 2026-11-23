package dev.kof.compiler;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

/**
 * Um PNG sintético para os testes do Énois — a "capa" com um gradiente
 * conhecido, escrita byte a byte (sem `ImageIO`), para que o oráculo do teste
 * seja DERIVADO da fórmula e não de um arquivo do repositório.
 *
 * <p>O pixel (x, y) é {@code (x * 255 / w, y * 255 / h, blue)}.
 */
final class EnosisTestPng {

    private EnosisTestPng() {}

    /** Escreve um PNG `w`×`h` com o gradiente e devolve o caminho. */
    static Path gradient(Path dir, String name, int w, int h, int blue) throws Exception {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        for (int y = 0; y < h; y++) {
            raw.write(0);
            for (int x = 0; x < w; x++) {
                raw.write((x * 255) / w);
                raw.write((y * 255) / h);
                raw.write(blue);
            }
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        png.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'});
        // o IHDR: w (4 bytes BE), h (4 bytes BE), 8 / 2 / 0 / 0 / 0
        writeChunk(png, "IHDR", new byte[]{
                (byte) (w >>> 24), (byte) (w >>> 16), (byte) (w >>> 8), (byte) w,
                (byte) (h >>> 24), (byte) (h >>> 16), (byte) (h >>> 8), (byte) h,
                8, 2, 0, 0, 0});
        writeChunk(png, "IDAT", deflate(raw.toByteArray()));
        writeChunk(png, "IEND", new byte[0]);
        Path p = dir.resolve(name);
        Files.write(p, png.toByteArray());
        return p;
    }

    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data)
            throws Exception {
        byte[] t = type.getBytes(StandardCharsets.US_ASCII);
        out.write(new byte[]{(byte) (data.length >>> 24), (byte) (data.length >>> 16),
                (byte) (data.length >>> 8), (byte) data.length});
        out.write(t);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(data);
        long c = crc.getValue();
        out.write(new byte[]{(byte) (c >>> 24), (byte) (c >>> 16), (byte) (c >>> 8), (byte) c});
    }

    private static byte[] deflate(byte[] in) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (var z = new DeflaterOutputStream(bo)) {
            z.write(in);
        }
        return bo.toByteArray();
    }
}
