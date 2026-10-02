package dev.kof.compiler;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Hand-built image fixtures and the Kof probe for the {@code kof.vision}
 * golden ({@link VisionAnalysisE2ETest}); extracted from
 * {@link RasterDecodeFixtures} to keep that class under the oversized
 * threshold. Paths are resolved through {@link RasterDecodeFixtures#path}.
 */
final class VisionFixtures {

    private VisionFixtures() {
    }

    static Path visionFixtures(Path dir) throws Exception {
        Files.createDirectories(dir);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("P5\n4 4\n255\n".getBytes(StandardCharsets.US_ASCII));
        for (int i = 0; i < 10; i++) out.write(30);
        for (int i = 0; i < 6; i++) out.write(220);
        Files.write(dir.resolve("bimodal.pgm"), out.toByteArray());
        Files.write(dir.resolve("ramp.pgm"), pnmGray(5, 5, new int[]{
                0, 0, 0, 0, 0,
                0, 10, 10, 10, 10,
                0, 10, 50, 10, 10,
                0, 10, 10, 10, 10,
                0, 0, 0, 0, 0}));
        Files.write(dir.resolve("edge.pgm"), pnmGray(5, 5, new int[]{
                0, 0, 0, 0, 0,
                0, 0, 0, 0, 0,
                0, 0, 255, 0, 0,
                0, 0, 0, 0, 0,
                0, 0, 0, 0, 0}));
        Files.write(dir.resolve("blobs.pgm"), pnmGray(6, 4, new int[]{
                255, 255, 0, 0, 0, 0,
                255, 0, 0, 255, 255, 0,
                0, 0, 0, 255, 255, 0,
                0, 0, 0, 0, 0, 0}));
        Files.write(dir.resolve("speck.pgm"), pnmGray(5, 5, new int[]{
                0, 0, 0, 0, 0,
                0, 0, 0, 0, 0,
                0, 0, 255, 0, 0,
                0, 0, 0, 0, 0,
                0, 0, 0, 0, 0}));
        Files.write(dir.resolve("lowcontrast.pgm"), pnmGray(4, 4, new int[]{
                60, 60, 60, 60,
                60, 60, 60, 60,
                60, 60, 60, 60,
                200, 200, 200, 200}));
        Files.write(dir.resolve("six.pgm"), pnmGray(8, 8, new int[]{
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 51, 51, 51, 51, 51, 51,
                51, 51, 51, 51, 102, 102, 102, 102,
                102, 102, 102, 102, 102, 102, 153, 153,
                153, 153, 153, 153, 153, 153, 153, 153,
                204, 204, 204, 204, 204, 204, 204, 204,
                204, 204, 255, 255, 255, 255, 255, 255,
                255, 255, 255, 255, 255, 255, 255, 255}));
        return dir;
    }

    static byte[] pnmGray(int w, int h, int[] samples) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(("P5\n" + w + " " + h + "\n255\n").getBytes(StandardCharsets.US_ASCII));
        for (int s : samples) out.write(s & 0xFF);
        return out.toByteArray();
    }

    static String visionProbe(Path dir) {
        String base = path(dir);
        return """
            import image.Raster
            import vision.Components
            import vision.Edges
            import vision.Histogram
            import vision.Morphology
            import vision.Regions
            import vision.Vision

            main() {
                var r = decodeRaster("%s/bimodal.pgm")
                var h = histogram(r)
                println("P5:" + r.width + "x" + r.height + " ch=" + r.channels + " px=" + (r.width * r.height))
                println("hist=" + h[220] + "," + h[30])
                println("norm=" + (normalizedHistogram(r)[220] * 1000).toInt())
                var bw = otsuBinarize(r)
                var t = sat(otsuLevel(r))
                println("otsu=" + t + " bw=" + bw.samples[0] + "," + bw.samples[6] + "," + bw.samples[15])
                var ramp = decodeRaster("%s/ramp.pgm")
                var e = sobelMagnitude(ramp)
                println("sobelRamp=" + e.format + ":" + e.width + "x" + e.height + " ch=" + e.channels)
                println("sobelRamp-mid=" + e.samples[12] + " edge=" + e.samples[6])
                var dot = decodeRaster("%s/edge.pgm")
                var d = sobelMagnitude(dot)
                println("sobelDot=" + d.samples[12] + " border=" + d.samples[0] + "," + d.samples[24])
                var blobs = decodeRaster("%s/blobs.pgm")
                var lab = componentLabels(blobs)
                println("comp=" + componentCount(lab) + " a=" + lab[0] + " b=" + lab[9] + " bg=" + lab[7])
                var speck = decodeRaster("%s/speck.pgm")
                println("erode=" + erode(speck).samples[12] + " dilate=" + dilate(speck).samples[12] + "," + dilate(speck).samples[6])
                println("open=" + openRaster(speck).samples[12] + " close=" + closeRaster(speck).samples[12])
                var lowc = decodeRaster("%s/lowcontrast.pgm")
                var lowLut = equalizationLut(lowc)
                var lowEq = equalizeRaster(lowc)
                println("eqLow=" + lowLut[60] + "," + lowLut[200] + " out=" + lowEq.samples[0] + "," + lowEq.samples[15])
                var six = decodeRaster("%s/six.pgm")
                var sixLut = equalizationLut(six)
                println("eqSix=" + sixLut[51] + "," + sixLut[102] + "," + sixLut[153] + "," + sixLut[204])
                var boxes = componentBoxes(lab, blobs.width)
                var areas = componentAreas(lab)
                println("areas=" + areas[1] + "," + areas[2])
                println("box1=" + boxes.get(1).minX + "," + boxes.get(1).minY + "," + boxes.get(1).maxX + "," + boxes.get(1).maxY)
                println("box2=" + boxes.get(2).minX + "," + boxes.get(2).minY + "," + boxes.get(2).maxX + "," + boxes.get(2).maxY)
                var comps = labelComponents(lab, blobs.width)
                var c1 = comps.get(0)
                println("regions=" + comps.size() + " r1=" + c1.label + "@" + c1.box.minX + "," + c1.box.minY + " a" + c1.area)
            }

            String sat(Int v) {
                if (v < 0) { return "neg" }
                if (v > 255) { return "over" }
                return v.toString()
            }
            """.formatted(base, base, base, base, base, base, base);
    }

    private static String path(Path p) {
        return RasterDecodeFixtures.path(p);
    }
}
