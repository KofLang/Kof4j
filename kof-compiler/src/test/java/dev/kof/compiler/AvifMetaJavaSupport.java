package dev.kof.compiler;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Second independent AVIF metadata-OBU reader (plain Java, written from
 *  AV1 5.8.1-5.8.4 + the metadata_type symbol table): the agreement oracle
 *  for slice 2g. Emits the SAME fact strings and the SAME `IMAGE:` refusal
 *  messages as the Kof face. */
final class AvifMetaJavaSupport {

    private AvifMetaJavaSupport() {}

    static List<String> javaMetaFacts(Path file) throws Exception {
        byte[] item = AvifItemsSupport.readItemJava(file, 1);
        List<String> out = new ArrayList<>();
        int pos = 0, n = item.length;
        while (pos < n) {
            int head = item[pos] & 255;
            if ((head >> 7) != 0) throw new AssertionError("IMAGE: avif item obu forbidden bits");
            int type = (head >> 3) & 15;
            int ext = (head >> 2) & 1;
            if ((head & 1) != 0) throw new AssertionError("IMAGE: avif item obu reserved bit set");
            if (((head >> 1) & 1) != 1) throw new AssertionError("IMAGE: avif item obu missing size field");
            int p = pos + 1 + ext;
            int size = 0;
            boolean sized = false;
            for (int i = 0; i < 8; i++) {
                int x = item[p++] & 255;
                size = (size << 7) | (x & 127);
                if ((x & 128) == 0) {
                    sized = true;
                    break;
                }
            }
            if (!sized) throw new AssertionError("IMAGE: avif obu size too long");
            int limit = p + size;
            if (limit > n) throw new AssertionError("IMAGE: avif obu truncated");
            if (type == 5) {
                out.add(metaFacts(item, p, limit));
            }
            pos = limit;
        }
        return out;
    }

    private static String metaFacts(byte[] b, int p, int limit) {
        int mtype = 0;
        boolean coded = false;
        for (int i = 0; i < 8; i++) {
            int x = b[p++] & 255;
            mtype = (mtype << 7) | (x & 127);
            if ((x & 128) == 0) {
                coded = true;
                break;
            }
        }
        if (!coded) throw new AssertionError("IMAGE: avif metadata type too long");
        int payloadBytes = limit - p;
        String name = "reserved";
        if (mtype == 0) name = "reserved";
        else if (mtype == 1) name = "hdrCll";
        else if (mtype == 2) name = "hdrMdcv";
        else if (mtype == 3) name = "scalability";
        else if (mtype == 4) name = "itutT35";
        else if (mtype == 5) name = "timecode";
        else if (mtype <= 31) name = "private";
        StringBuilder s = new StringBuilder(mtype + " " + name + " pb=" + payloadBytes);
        if (mtype == 4) {
            if (p + 1 > limit) throw new AssertionError("IMAGE: truncated avif metadata obu");
            int country = b[p++] & 255;
            boolean extended = false;
            if (country == 255) {
                if (p + 1 > limit) throw new AssertionError("IMAGE: truncated avif metadata obu");
                extended = true;
                country = (country << 8) | (b[p++] & 255);
            }
            s.append(" cc=").append(country).append(" ext=").append(extended ? 1 : 0)
             .append(" t35=").append(limit - p);
        } else if (mtype == 5) {
            // metadata_timecode() per AV1 5.8.7 + the 6.7.7 semantics
            int bits = p * 8;
            int limitBits = limit * 8;
            if (bits + 23 > limitBits) throw new AssertionError("IMAGE: truncated avif metadata obu");
            int countingType = readBits(b, bits, 5); bits += 5;
            int fullTs = readBits(b, bits, 1); bits += 1;
            int discontinuity = readBits(b, bits, 1); bits += 1;
            int cntDropped = readBits(b, bits, 1); bits += 1;
            int nFrames = readBits(b, bits, 9); bits += 9;
            int seconds = -1, minutes = -1, hours = -1;
            if (fullTs == 1) {
                if (bits + 17 > limitBits) throw new AssertionError("IMAGE: truncated avif metadata obu");
                seconds = readBits(b, bits, 6); bits += 6;
                minutes = readBits(b, bits, 6); bits += 6;
                hours = readBits(b, bits, 5); bits += 5;
            } else {
                if (bits + 1 > limitBits) throw new AssertionError("IMAGE: truncated avif metadata obu");
                int secondsFlag = readBits(b, bits, 1); bits += 1;
                if (secondsFlag == 1) {
                    if (bits + 7 > limitBits) throw new AssertionError("IMAGE: truncated avif metadata obu");
                    seconds = readBits(b, bits, 6); bits += 6;
                    int minutesFlag = readBits(b, bits, 1); bits += 1;
                    if (minutesFlag == 1) {
                        if (bits + 7 > limitBits) throw new AssertionError("IMAGE: truncated avif metadata obu");
                        minutes = readBits(b, bits, 6); bits += 6;
                        int hoursFlag = readBits(b, bits, 1); bits += 1;
                        if (hoursFlag == 1) {
                            if (bits + 5 > limitBits) throw new AssertionError("IMAGE: truncated avif metadata obu");
                            hours = readBits(b, bits, 5); bits += 5;
                        }
                    }
                }
            }
            if (bits + 5 > limitBits) throw new AssertionError("IMAGE: truncated avif metadata obu");
            int offsetLength = readBits(b, bits, 5); bits += 5;
            int offset = 0;
            if (offsetLength > 0) {
                if (bits + offsetLength > limitBits) throw new AssertionError("IMAGE: truncated avif metadata obu");
                offset = readBits(b, bits, offsetLength); bits += offsetLength;
            }
            s.append(" tc=,").append(countingType).append(",").append(fullTs)
             .append(",").append(discontinuity).append(",").append(cntDropped)
             .append(",").append(nFrames).append(",").append(seconds)
             .append(",").append(minutes).append(",").append(hours)
             .append(",").append(offset);
        } else if (mtype == 1) {
            if (p + 4 > limit) throw new AssertionError("IMAGE: truncated avif metadata obu");
            int cll = ((b[p] & 255) << 8) | (b[p + 1] & 255);
            int fall = ((b[p + 2] & 255) << 8) | (b[p + 3] & 255);
            s.append(" cll=").append(cll).append(" fall=").append(fall);
        } else if (mtype == 2) {
            if (p + 24 > limit) throw new AssertionError("IMAGE: truncated avif metadata obu");
            s.append(" mdcv=");
            for (int i = 0; i < 10; i++) {
                s.append(",");
                if (i < 8) {
                    s.append(((b[p] & 255) << 8) | (b[p + 1] & 255));
                    p += 2;
                } else {
                    s.append(((long) (b[p] & 255) << 24) | ((b[p + 1] & 255) << 16)
                           | ((b[p + 2] & 255) << 8) | (b[p + 3] & 255));
                    p += 4;
                }
            }
        }
        return s.toString();
    }

    private static int readBits(byte[] b, int bitPos, int n) {
        int v = 0;
        for (int i = 0; i < n; i++) {
            int p = bitPos + i;
            v = (v << 1) | (((b[p >> 3] & 255) >> (7 - (p & 7))) & 1);
        }
        return v;
    }

    static String javaMetaFactsError(Path file) {
        try {
            javaMetaFacts(file);
            return "OK";
        } catch (AssertionError e) {
            return e.getMessage();
        } catch (Exception e) {
            return "IO:" + e;
        }
    }
}
