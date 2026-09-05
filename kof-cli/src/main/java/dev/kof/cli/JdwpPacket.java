package dev.kof.cli;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Frame de pacote JDWP (wire) — serialização/leitura big-endian.
 * Extraído de JdwpClient (REFACTOR-500 Fase 8) — SRP: só codec de pacote.
 */
final class JdwpPacket {
private static final class Packet {
    int id;
    int errorCode;
    private final List<Byte> bytes = new ArrayList<>();
    private byte[] data;
    private int pos;

    Packet() {
    }

    Packet(int id, int errorCode, byte[] data) {
        this.id = id;
        this.errorCode = errorCode;
        this.data = data;
        this.pos = 0;
    }

    boolean eventData;

    byte[] toByteArray() {
        byte[] arr = new byte[bytes.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = bytes.get(i);
        }
        return arr;
    }

    void writeByte(int b) {
        bytes.add((byte) b);
    }

    void writeInt(int v) {
        writeByte(v >>> 24);
        writeByte(v >>> 16);
        writeByte(v >>> 8);
        writeByte(v);
    }

    void writeLong(long v) {
        writeInt((int) (v >>> 32));
        writeInt((int) v);
    }

    void writeString(String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        writeInt(b.length);
        for (byte x : b) {
            bytes.add(x);
        }
    }

    void writeReference(long ref) {
        writeLong(ref);
    }

    int readByte() {
        return data[pos++] & 0xFF;
    }

    int readShort() {
        return (readByte() << 8) | readByte();
    }

    int readInt() {
        return (readByte() << 24) | (readByte() << 16) | (readByte() << 8) | readByte();
    }

    long readLong() {
        return ((long) readInt() << 32) | (readInt() & 0xFFFFFFFFL);
    }

    long readReference() {
        return readLong();
    }

    String readString() {
        int len = readInt();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append((char) readByte());
        }
        return sb.toString();
    }

    void skipRemaining() {
        pos = data.length;
    }
}
}
