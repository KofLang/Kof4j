package dev.kof.compiler;

import java.nio.file.Files;
import java.nio.file.Path;

/** Fixtures + probe for AVIF slice 2f (tile_group_obu header prefix, AV1 5.11.1).
 *  All group fixtures ride the tile4 frame shape: redSeq128() (128x128, sbCols=8,
 *  sbRows=8) + frameReduced uniform 2x2 (tileBits=2, TileSizeBytes=1). */
final class AvifGroupSupport {

    private AvifGroupSupport() {}

    /** frameReduced prefix bits for a type-6 inline payload (same tile_info
     *  bits as tile4; returns raw bytes WITHOUT the OBU wrapper). */
    private static byte[] tile4PrefixBytes() {
        byte[] obu = AvifFrameSupport.frameReduced(1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 6, 1, 1, 1, 2, 2, 0);
        // obuType prepends head+size: payload starts after them
        int p = 2;
        byte[] out = new byte[obu.length - p];
        System.arraycopy(obu, p, out, 0, out.length);
        return out;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    /** group header bytes: optional flag/range, byte align, le(sizeBytes=1)
     *  per explicit size, then payload filler bytes. Payload bytes are
     *  DISTINCT per tile (an increasing seed) so a wrong payload offset is
     *  caught by the per-tile hash, not masked by uniform filler. */
    private static byte[] groupBytes(int flag, int tgS, int tgE, int[] explicitSizes, int filler) {
        AvifMetadataSupport.Bits w = new AvifMetadataSupport.Bits();
        w.bits(flag, 1);
        if (flag == 1) {
            w.bits(tgS, 2);
            w.bits(tgE, 2);
        }
        while ((w.n & 7) != 0) {
            w.bits(0, 1);
        }
        int seed = 0;
        for (int s : explicitSizes) {
            w.bits(s - 1, 8);
            for (int i = 0; i < s; i++) {
                w.bits(seed++ & 255, 8);
            }
        }
        for (int i = 0; i < filler; i++) {
            w.bits(seed++ & 255, 8);
        }
        return w.bytes();
    }

    static Path groupFixtures(Path dir) throws Exception {
        Files.createDirectories(dir);
        // single-tile: 32x32 red frame (1x1 tiles, numTiles==1 -> NO range
        // flag, NO size table: the one tile takes the whole payload)
        Files.write(dir.resolve("tg-one.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        AvifMetadataSupport.configObu(0, 2, false, false, true),
                        AvifFrameSupport.frameReduced(1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 3, 1, 0, 0, 1, 1, 0),
                        AvifSeqSupport.obuType(4, new byte[]{0x11, 0x22})))));
        // split: OBU_FRAME_HEADER (3) + two sibling OBU_TILE_GROUP (4):
        // g1 [0..1] size 5 + 3 filler; g2 [2..3] size 4 + 2 filler
        byte[] hdr3 = AvifFrameSupport.frameReduced(1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 3, 1, 1, 1, 2, 2, 0);
        Files.write(dir.resolve("tg-split.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        AvifFrameSupport.redSeq128(),
                        hdr3,
                        AvifSeqSupport.obuType(4, groupBytes(1, 0, 1, new int[]{5}, 3)),
                        AvifSeqSupport.obuType(4, groupBytes(1, 2, 3, new int[]{4}, 2))))));
        // inline: OBU_FRAME (6) with the SAME header bytes and ONE inline
        // group after the header byte_alignment (full range -> flag=0):
        // sizes ,5,4,3 + 1 filler -> last=1 total=13
        Files.write(dir.resolve("tg-inline.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        AvifFrameSupport.redSeq128(),
                        AvifSeqSupport.obuType(6, concat(tile4PrefixBytes(),
                                groupBytes(0, 0, 0, new int[]{5, 4, 3}, 1)))))));
        return dir;
    }

    static Path groupErrorFixtures(Path dir) throws Exception {
        Files.createDirectories(dir);
        byte[] hdr3 = AvifFrameSupport.frameReduced(1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 3, 1, 1, 1, 2, 2, 0);
        // full range with the flag set
        Files.write(dir.resolve("full.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(), AvifFrameSupport.redSeq128(), hdr3,
                        AvifSeqSupport.obuType(4, groupBytes(1, 0, 3, new int[]{1, 1, 1}, 1))))));
        // out-of-order start
        Files.write(dir.resolve("order.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(), AvifFrameSupport.redSeq128(), hdr3,
                        AvifSeqSupport.obuType(4, groupBytes(1, 2, 3, new int[]{1}, 1))))));
        // inverted range
        Files.write(dir.resolve("range.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(), AvifFrameSupport.redSeq128(), hdr3,
                        AvifSeqSupport.obuType(4, groupBytes(1, 3, 1, new int[]{1}, 1))))));
        // last group ends at 1 of 4
        Files.write(dir.resolve("incomplete.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(), AvifFrameSupport.redSeq128(), hdr3,
                        AvifSeqSupport.obuType(4, groupBytes(1, 0, 1, new int[]{1}, 1))))));
        // truncated: flag=1 tg[0..2], byte align, le size field claims 200
        // but only 1 payload byte follows (manual bits: groupBytes would
        // emit 200 real filler bytes instead of a truncated claim)
        Files.write(dir.resolve("trunc.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(), AvifFrameSupport.redSeq128(), hdr3,
                        AvifSeqSupport.obuType(4, new byte[]{(byte) 0x90, (byte) 0xC7, 0x07})))));
        // frame header with no group behind it
        Files.write(dir.resolve("nogroup.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(), AvifFrameSupport.redSeq128(), hdr3))));
        // group before any frame header
        Files.write(dir.resolve("before.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(), AvifFrameSupport.redSeq128(),
                        AvifSeqSupport.obuType(4, groupBytes(1, 0, 1, new int[]{1}, 1))))));
        return dir;
    }

    static String groupProbe(Path dir) {
        String base = dir.toString().replace('\\', '/');
        return """
            import image.AvifGroup

            String sizes(List<Int> xs) {
                var s = ""
                for (var x in xs) {
                    s = s + "," + x
                }
                return s
            }

            String gfacts(AvifTileGroup g) {
                return g.tgStart + ".." + g.tgEnd + " n=" + g.tileCount
                    + " sizes=" + sizes(g.tileSizes) + " last=" + g.lastTileSize
                    + " total=" + g.totalBytes
            }

            String pay(Int[] p) {
                var s = 0
                for (var x in p) {
                    s = (s * 31 + x) %% 100003
                }
                return "len=" + p.length + " h=" + s
            }

            main() {
                var base = "%s"
                var one = readAvifTileGroups(base + "/tg-one.avif")
                for (var g in one) {
                    println("one " + gfacts(g))
                }
                var sp = readAvifTileGroups(base + "/tg-split.avif")
                for (var g in sp) {
                    println("split " + gfacts(g))
                }
                var inl = readAvifTileGroups(base + "/tg-inline.avif")
                for (var g in inl) {
                    println("inline " + gfacts(g))
                }
                var pays = readAvifTilePayloads(base + "/tg-inline.avif")
                for (var p in pays) {
                    println("tile " + pay(p))
                }
                var spays = readAvifTilePayloads(base + "/tg-split.avif")
                for (var p in spays) {
                    println("stile " + pay(p))
                }
            }
            """.formatted(base);
    }

    static String groupErrorProbe(Path dir) {
        String base = dir.toString().replace('\\', '/');
        return """
            import image.Avif
            import image.AvifFrame
            import image.AvifGroup
            import image.AvifItems

            main() {
                var base = "%s"
                try {
                    readAvifTileGroups(base + "/full.avif")
                } catch (String e) {
                    println(e)
                }
                try {
                    readAvifTileGroups(base + "/order.avif")
                } catch (String e) {
                    println(e)
                }
                try {
                    readAvifTileGroups(base + "/range.avif")
                } catch (String e) {
                    println(e)
                }
                try {
                    readAvifTileGroups(base + "/incomplete.avif")
                } catch (String e) {
                    println(e)
                }
                try {
                    readAvifTileGroups(base + "/trunc.avif")
                } catch (String e) {
                    println(e)
                }
                try {
                    readAvifTileGroups(base + "/nogroup.avif")
                } catch (String e) {
                    println(e)
                }
                try {
                    readAvifTileGroups(base + "/before.avif")
                } catch (String e) {
                    println(e)
                }
            }
            """.formatted(base);
    }
}
