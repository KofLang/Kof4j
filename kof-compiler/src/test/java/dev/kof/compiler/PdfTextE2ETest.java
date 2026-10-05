package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end coverage for {@code pdfText} (issue #629): the pure-Kof PDF text
 * reader in {@code libs/pdf/PdfText.kf}. Fixtures are assembled byte-by-byte so
 * the shapes that matter are explicit — uncompressed and FlateDecode content,
 * WinAnsi accents, {@code TJ} arrays, UTF-16 hex strings, multipage documents,
 * form XObjects, resources inherited from the page tree and PDF 1.5 object
 * streams — plus the explicit rejection contract for encrypted, invalid and
 * missing files.
 */
class PdfTextE2ETest extends KofmdRunSupport implements LibraryInstallSupport {

    @Test
    void readsTextFromEveryDocumentShape() throws Exception {
        Path root = tmp.resolve("shapes");
        Files.createDirectories(root);

        Map<String, Path> pdfs = new LinkedHashMap<>();
        pdfs.put("plain", write(root, "plain.pdf", document("1.4",
                pageWithContent(stream("BT /F1 24 Tf 72 720 Td (Hello PDF) Tj ET")))));
        pdfs.put("flate", write(root, "flate.pdf", document("1.4",
                pageWithContent(flateStream("BT /F1 24 Tf 72 720 Td (Hello PDF) Tj ET")))));
        pdfs.put("accents", write(root, "accents.pdf", document("1.4",
                pageWithContent(stream("BT /F1 24 Tf 72 720 Td (Caf\\351 S\\343o Paulo) Tj ET")))));
        pdfs.put("tj", write(root, "tj.pdf", document("1.4",
                pageWithContent(stream("BT /F1 24 Tf 72 720 Td [(Hel) -30 (lo)] TJ ET")))));
        pdfs.put("utf16", write(root, "utf16.pdf", document("1.4",
                pageWithContent(stream("BT /F1 24 Tf 72 720 Td <FEFF00480065006C006C006F> Tj ET")))));
        pdfs.put("multipage", write(root, "multipage.pdf", multipageDocument()));
        pdfs.put("form", write(root, "form.pdf", formDocument(false)));
        pdfs.put("inherited", write(root, "inherit.pdf", formDocument(true)));
        pdfs.put("objstm", write(root, "objstm.pdf", objectStreamDocument()));

        Map<String, Path> outputs = new LinkedHashMap<>();
        for (String name : pdfs.keySet()) {
            outputs.put(name, root.resolve(name + ".txt"));
        }
        runKof(renderProgram(pdfs, outputs));

        assertEquals("TEXT:Hello PDF", read(outputs.get("plain")));
        assertEquals("TEXT:Hello PDF", read(outputs.get("flate")));
        assertEquals("TEXT:Café São Paulo", read(outputs.get("accents")));
        assertEquals("TEXT:Hello", read(outputs.get("tj")));
        assertEquals("TEXT:Hello", read(outputs.get("utf16")));
        assertEquals("TEXT:Page one\nPage two", read(outputs.get("multipage")));
        assertEquals("TEXT:Form text", read(outputs.get("form")));
        assertEquals("TEXT:Form text", read(outputs.get("inherited")));
        assertEquals("TEXT:From object stream", read(outputs.get("objstm")));
    }

    @Test
    void rejectsUnreadableDocumentsExplicitly() throws Exception {
        Path root = tmp.resolve("reject");
        Files.createDirectories(root);

        Path notext = write(root, "notext.pdf", document("1.4", pageWithContent(
                stream("BT /F1 24 Tf 72 720 Td ( ) Tj ET"))));
        Path encrypt = write(root, "encrypt.pdf", document("1.4", pageWithContent(
                stream("BT /F1 24 Tf 72 720 Td (Secret) Tj ET")), "/Encrypt 9 0 R"));
        Path notpdf = write(root, "notpdf.pdf", "this is not a pdf".getBytes(StandardCharsets.US_ASCII));
        Path missing = root.resolve("missing.pdf");

        Path output = root.resolve("results.txt");
        Map<String, Path> pdfs = new LinkedHashMap<>();
        pdfs.put("notext", notext);
        pdfs.put("encrypt", encrypt);
        pdfs.put("notpdf", notpdf);
        pdfs.put("missing", missing);

        StringBuilder probe = new StringBuilder(renderFunction());
        probe.append("main() {\n");
        probe.append("    var results = \"\"\n");
        List<Path> ordered = List.copyOf(pdfs.values());
        for (int i = 0; i < ordered.size(); i++) {
            if (i > 0) {
                probe.append("    results = results + \"\\n\"\n");
            }
            probe.append("    results = results + render(\"").append(slash(ordered.get(i))).append("\")\n");
        }
        probe.append("    File(\"").append(slash(output)).append("\").writeText(results)\n");
        probe.append("}\n");
        runKof(probe.toString());

        String[] lines = read(output).split("\n", -1);
        assertEquals(4, lines.length);
        assertEquals("NULL", lines[0]);
        assertEquals("ERROR:PDF: encrypted documents are not supported", lines[1]);
        assertEquals("ERROR:PDF: not a PDF document", lines[2]);
        assertEquals("ERROR:PDF: cannot read file", lines[3]);
    }

    @Test
    void readsBackWhatTheWriterProduced() throws Exception {
        Path root = tmp.resolve("roundtrip");
        Files.createDirectories(root);
        Path pdf = root.resolve("roundtrip.pdf");
        runKof("""
            import pdf.PdfDocument

            main() {
                var document = PdfDocument()
                document.text("Relatório São Paulo", 40, 760)
                document.save("%s")
            }
            """.formatted(slash(pdf)));

        Path output = root.resolve("roundtrip.txt");
        runKof("""
            import pdf.PdfText

            main() {
                File("%s").writeText(pdfText("%s"))
            }
            """.formatted(slash(output), slash(pdf)));
        assertEquals("Relatório São Paulo", read(output));
    }

    @Test
    void readsPdfOnScript() throws Exception {
        Path root = tmp.resolve("script");
        Path pdf = write(root, "script.pdf", document("1.4",
                pageWithContent(flateStream("BT /F1 24 Tf 72 720 Td (Script PDF) Tj ET"))));
        Path output = root.resolve("script.txt");
        Path source = readerProgram(root, pdf, output);

        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(source), root, new String[0]));

        assertEquals(0, result.exitCode(), "SCRIPT output: " + result.stdout());
        assertEquals("Script PDF", read(output));
    }

    /**
     * JS face is blocked by an unrelated pre-existing compiler bug, catalogued as
     * {@code known-bugs §601}: {@code ExpressionBinaryLowerer} leaves the result
     * type of a numeric comparison as the numeric common type, so a following
     * {@code &&}/{@code ||} sees a non-Bool left operand and the JS backend emits
     * bitwise {@code &}/{@code |} (no short-circuit) — the reader's
     * {@code i < end && b[i] != ...} guards then read out of bounds. The compiler
     * frontend is another lane's territory; this test pins the gap (a tripwire:
     * it must be replaced by the contract assertion once §601 is fixed) instead of
     * asserting a false green. JVM/Script/Native faces are covered above.
     */
    @Test
    void readsOnJsBlockedByKnownBug601() throws Exception {
        Path root = tmp.resolve("js");
        Path pdf = write(root, "js.pdf", document("1.4",
                pageWithContent(flateStream("BT /F1 24 Tf 72 720 Td (JS PDF) Tj ET"))));
        Path output = root.resolve("js.txt");
        Path source = readerProgram(root, pdf, output);
        Path out = root.resolve("out");

        CompilationResult result = withLibrary(root, () -> driver.compile(source, out, Target.JS));
        assertTrue(result.success(), () -> "JS compile: " + result.diagnostics().getDiagnostics());
        try (var stdout = new java.io.ByteArrayOutputStream();
             var stderr = new java.io.ByteArrayOutputStream()) {
            int exitCode = dev.kof.runtime.KofJsRunner.run(
                    out.resolve("Default.mjs"), stdout,
                    java.io.InputStream.nullInputStream(), stderr);
            assertTrue(exitCode != 0,
                    "known-bugs §601 fixed? the JS reader ran green — replace this gap "
                            + "test with the contract assertion (exit 0 + \"JS PDF\")");
        }
        assertTrue(Files.notExists(output),
                "known-bugs §601: the bitwise `&` guard reads out of bounds before writing");
    }

    @Test
    void readsPdfOnNativeX86() throws Exception {
        assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path root = tmp.resolve("native");
        Path pdf = write(root, "native.pdf", document("1.4",
                pageWithContent(flateStream("BT /F1 24 Tf 72 720 Td (Native PDF) Tj ET"))));
        Path output = root.resolve("native.txt");
        Path source = readerProgram(root, pdf, output);
        Path out = root.resolve("out");

        CompilationResult result = withLibrary(root, () -> driver.compile(source, out, Target.NATIVE));
        assertTrue(result.success(), () -> "Native compile: " + result.diagnostics().getDiagnostics());
        Path binary = out.resolve("Default/Main");
        assertTrue(Files.isRegularFile(binary), "Native binary must exist");
        Process process = new ProcessBuilder(binary.toString()).redirectErrorStream(true).start();
        String processOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), "Native output: " + processOutput);
        assertEquals("Native PDF", read(output));
    }

    // --- probe programs ---------------------------------------------------

    private static String renderFunction() {
        return """
            import pdf.PdfText

            String render(String path) {
                try {
                    var t = pdfText(path)
                    if (t == null) {
                        return "NULL"
                    }
                    return "TEXT:" + t
                } catch (String e) {
                    return "ERROR:" + e
                }
            }

            """;
    }

    private static String renderProgram(Map<String, Path> pdfs, Map<String, Path> outputs) {
        StringBuilder probe = new StringBuilder(renderFunction());
        probe.append("main() {\n");
        for (String name : pdfs.keySet()) {
            probe.append("    File(\"").append(slash(outputs.get(name))).append("\").writeText(")
                    .append("render(\"").append(slash(pdfs.get(name))).append("\"))\n");
        }
        probe.append("}\n");
        return probe.toString();
    }

    private Path readerProgram(Path root, Path pdf, Path output) throws Exception {
        Files.createDirectories(root);
        copyLibrary(root.resolve("kof-install/lib/kof-libs"));
        Path source = root.resolve("Main.kf");
        Files.writeString(source, """
            import pdf.PdfText

            main() {
                File("%s").writeText(pdfText("%s"))
            }
            """.formatted(slash(output), slash(pdf)));
        return source;
    }

    private <T> T withLibrary(Path root, CheckedSupplier<T> action) throws Exception {
        String previousInstallDir = System.getProperty("kof.install.dir");
        System.setProperty("kof.install.dir", root.resolve("kof-install").toString());
        try {
            return action.get();
        } finally {
            if (previousInstallDir == null) System.clearProperty("kof.install.dir");
            else System.setProperty("kof.install.dir", previousInstallDir);
        }
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    // --- fixture assembly -------------------------------------------------

    /** Catalog + pages tree + the supplied page/content objects + trailer. */
    private static byte[] document(String version, byte[] pageAndContent, String... extraObjects) {
        PdfBuilder builder = new PdfBuilder(version);
        builder.obj(1, "<< /Type /Catalog /Pages 2 0 R >>");
        builder.obj(2, "<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        builder.raw(pageAndContent);
        for (String extra : extraObjects) {
            builder.obj(9, extra);
        }
        builder.trailer("<< /Root 1 0 R >>");
        return builder.done();
    }

    /** Page dictionary (3), its content stream (4) and the font (8). */
    private static byte[] pageWithContent(byte[] contentObject) {
        return concat(
                object(3, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                        + "/Resources << /Font << /F1 8 0 R >> >> /Contents 4 0 R >>"),
                object(4, contentObject),
                object(8, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica "
                        + "/Encoding /WinAnsiEncoding >>"));
    }

    private static byte[] multipageDocument() {
        PdfBuilder builder = new PdfBuilder("1.4");
        builder.obj(1, "<< /Type /Catalog /Pages 2 0 R >>");
        builder.obj(2, "<< /Type /Pages /Kids [3 0 R 5 0 R] /Count 2 >>");
        builder.raw(object(3, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                + "/Resources << /Font << /F1 8 0 R >> >> /Contents 4 0 R >>"));
        builder.raw(object(4, stream("BT /F1 24 Tf 72 720 Td (Page one) Tj ET")));
        builder.raw(object(5, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                + "/Resources << /Font << /F1 8 0 R >> >> /Contents 6 0 R >>"));
        builder.raw(object(6, stream("BT /F1 24 Tf 72 720 Td (Page two) Tj ET")));
        builder.raw(object(8, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica "
                + "/Encoding /WinAnsiEncoding >>"));
        builder.trailer("<< /Root 1 0 R >>");
        return builder.done();
    }

    /**
     * Page (3) draws form XObject 7. When {@code inherited} the `/Resources`
     * entry lives on the parent `/Pages` node instead of the page dictionary.
     */
    private static byte[] formDocument(boolean inherited) {
        PdfBuilder builder = new PdfBuilder("1.4");
        builder.obj(1, "<< /Type /Catalog /Pages 2 0 R >>");
        if (inherited) {
            builder.obj(2, "<< /Type /Pages /Kids [3 0 R] /Count 1 /Resources 5 0 R >>");
        } else {
            builder.obj(2, "<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        }
        String page = inherited
                ? "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R >>"
                : "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                        + "/Resources 5 0 R /Contents 4 0 R >>";
        builder.raw(object(3, page));
        builder.raw(object(4, stream("BT /F1 24 Tf 72 720 Td /Fm Do ET")));
        builder.raw(object(5, "<< /XObject << /Fm 7 0 R >> >>"));
        byte[] formBody = "BT /F1 24 Tf (Form text) Tj ET".getBytes(StandardCharsets.ISO_8859_1);
        builder.raw(object(7, concat(
                ("<< /Type /XObject /Subtype /Form /BBox [0 0 100 100] /Length "
                        + formBody.length + " >>\nstream\n").getBytes(StandardCharsets.ISO_8859_1),
                formBody, "\nendstream".getBytes(StandardCharsets.ISO_8859_1))));
        builder.trailer("<< /Root 1 0 R >>");
        return builder.done();
    }

    /** PDF 1.5: the page dictionary (5) lives inside a FlateDecode object stream (3). */
    private static byte[] objectStreamDocument() {
        byte[] pageDict = "<< /Type /Page /Parent 2 0 R /Contents 4 0 R /Resources << >> >>"
                .getBytes(StandardCharsets.ISO_8859_1);
        byte[] header = "5 0 ".getBytes(StandardCharsets.ISO_8859_1);
        int first = header.length;
        byte[] compressed = deflate(concat(header, pageDict));

        PdfBuilder builder = new PdfBuilder("1.5");
        builder.obj(1, "<< /Type /Catalog /Pages 2 0 R >>");
        builder.obj(2, "<< /Type /Pages /Kids [5 0 R] /Count 1 >>");
        builder.raw(object(3, concat(
                ("<< /Type /ObjStm /N 1 /First " + first + " /Filter /FlateDecode /Length "
                        + compressed.length + " >>\nstream\n").getBytes(StandardCharsets.ISO_8859_1),
                compressed, "\nendstream".getBytes(StandardCharsets.ISO_8859_1))));
        builder.raw(object(4, stream("BT /F1 24 Tf 72 720 Td (From object stream) Tj ET")));
        builder.trailer("<< /Root 1 0 R >>");
        return builder.done();
    }

    private static byte[] stream(String content) {
        byte[] payload = content.getBytes(StandardCharsets.ISO_8859_1);
        return concat("<< /Length ".getBytes(StandardCharsets.ISO_8859_1),
                String.valueOf(payload.length).getBytes(StandardCharsets.ISO_8859_1),
                " >>\nstream\n".getBytes(StandardCharsets.ISO_8859_1), payload,
                "\nendstream".getBytes(StandardCharsets.ISO_8859_1));
    }

    private static byte[] flateStream(String content) {
        byte[] payload = deflate(content.getBytes(StandardCharsets.ISO_8859_1));
        return concat("<< /Filter /FlateDecode /Length ".getBytes(StandardCharsets.ISO_8859_1),
                String.valueOf(payload.length).getBytes(StandardCharsets.ISO_8859_1),
                " >>\nstream\n".getBytes(StandardCharsets.ISO_8859_1), payload,
                "\nendstream".getBytes(StandardCharsets.ISO_8859_1));
    }

    private static byte[] deflate(byte[] input) {
        Deflater deflater = new Deflater();
        deflater.setInput(input);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        return out.toByteArray();
    }

    private static byte[] object(int num, String body) {
        return object(num, body.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static byte[] object(int num, byte[] body) {
        return concat(String.valueOf(num).getBytes(StandardCharsets.ISO_8859_1),
                " 0 obj\n".getBytes(StandardCharsets.ISO_8859_1), body,
                "\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    private Path write(Path root, String name, byte[] content) throws Exception {
        Files.createDirectories(root);
        Path file = root.resolve(name);
        Files.write(file, content);
        return file;
    }

    private static String slash(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /** Minimal sequential PDF assembler: objects are written in insertion order. */
    private static final class PdfBuilder {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        PdfBuilder(String version) {
            write("%PDF-" + version + "\n");
        }

        void obj(int num, String body) {
            raw(object(num, body));
        }

        void raw(byte[] bytes) {
            out.writeBytes(bytes);
        }

        void trailer(String trailer) {
            write(trailer + "\ntrailer\n");
        }

        byte[] done() {
            write("%%EOF\n");
            return out.toByteArray();
        }

        private void write(String text) {
            out.writeBytes(text.getBytes(StandardCharsets.ISO_8859_1));
        }
    }

    // --- harness wiring ---------------------------------------------------

    @Override
    protected String outPrefix() {
        return "pdftext-out-";
    }

    @Override
    public String libraryName() {
        return "pdf";
    }

    @Override
    public List<String> libraryMarkers() {
        return List.of("PdfDocument.kf");
    }

    @Override
    public List<String> libraryNames() {
        return List.of("pdf", "image");
    }
}
