package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** End-to-end coverage for the pure-Kof PDF library in {@code libs/pdf}. */
class PdfLibraryE2ETest extends KofmdRunSupport implements LibraryInstallSupport {

    @Test
    void createsPdfWithAccentsAndUserDefinedGrid() throws Exception {
        Path pdf = tmp.resolve("relatorio.pdf");
        runKof("""
            import pdf.PdfDocument
            import pdf.style.PdfColor
            import pdf.style.GridStyle
            import pdf.style.TextAlign
            import pdf.style.TextStyle

            main() {
                var document = PdfDocument()
                document.title("Relatório de usuários € “Kof” —", 40, 802)
                    .fontColor(PdfColor("#FF0000"))
                document.text("São Paulo", 40, 760)
                var pessoas = listOf(
                    Pessoa("João", 28, "São Paulo", "Professor")
                )
                document.grid(4)
                    .widths(listOf(140, 50, 170, 155))
                    .style(GridStyle()
                        .rowHeight(30)
                        .headerStyle(TextStyle().fontSize(10).align(TextAlign.center)))
                    .header(listOf("Nome", "Idade", "Cidade", "Profissão"))
                    .rows(pessoas.map((pessoa: Pessoa) -> listOf(
                        pessoa.nome,
                        pessoa.idade.toString(),
                        pessoa.cidade,
                        pessoa.profissao
                    )))
                document.save("%s")
            }

            class Pessoa {
                String nome
                Int idade
                String cidade
                String profissao

                constructor(String nome, Int idade, String cidade, String profissao) {
                    this.nome = nome
                    this.idade = idade
                    this.cidade = cidade
                    this.profissao = profissao
                }
            }
            """.formatted(pdf.toString().replace('\\', '/')));

        assertTrue(Files.isRegularFile(pdf), "save() must create the requested PDF");
        String content = Files.readString(pdf, StandardCharsets.ISO_8859_1);
        assertTrue(content.startsWith("%PDF-1.4\n"));
        assertTrue(content.endsWith("%%EOF\n"));
        assertTrue(content.contains("/Encoding /WinAnsiEncoding"));
        assertTrue(content.contains("Relat\\363rio de usu\\341rios"));
        assertTrue(content.contains("\\200 \\223Kof\\224 \\227"));
        assertTrue(content.contains("S\\343o Paulo"));
        assertTrue(content.contains("Profiss\\343o"));
        assertTrue(content.contains("Jo\\343o"));
        assertTrue(content.contains("1 0 0 rg\nBT /F1 20 Tf 40 802 Td"));
        assertTrue(content.contains("BT /F1 10 Tf 98 717 Td (Nome)"));

        // Four columns and custom widths are reflected in the generated layout.
        assertTrue(content.contains("40 702 140 30 re"));
        assertTrue(content.contains("180 702 50 30 re"));
        assertTrue(content.contains("230 702 170 30 re"));
        assertTrue(content.contains("400 702 155 30 re"));

        int startXref = content.lastIndexOf("startxref\n");
        int offsetStart = startXref + "startxref\n".length();
        int offsetEnd = content.indexOf('\n', offsetStart);
        int declaredXrefOffset = Integer.parseInt(content.substring(offsetStart, offsetEnd));
        assertEquals(content.indexOf("xref\n"), declaredXrefOffset,
                "startxref must point to the xref table");
    }

    @Test
    void createsSingleColumnGrid() throws Exception {
        Path pdf = tmp.resolve("uma-coluna.pdf");
        runKof("""
            import pdf.PdfDocument

            main() {
                var document = PdfDocument()
                document.grid(1)
                    .header(listOf("Nome"))
                    .row(listOf("Davi"))
                document.save("%s")
            }
            """.formatted(pdf.toString().replace('\\', '/')));

        String content = Files.readString(pdf, StandardCharsets.ISO_8859_1);
        assertTrue(content.contains("40 778 515 24 re"));
        assertTrue(content.contains("40 754 515 24 re"));
        assertTrue(content.contains("(Nome) Tj"));
        assertTrue(content.contains("(Davi) Tj"));
    }

    @Test
    void rejectsInvalidGridAndStyleConfigurations() throws Exception {
        runKof("""
            import pdf.PdfDocument
            import pdf.style.GridStyle
            import pdf.style.PdfColor
            import pdf.style.TextStyle

            main() {
                var rejected = 0

                try { PdfDocument().grid(0) }
                catch (String error) { rejected = rejected + 1 }

                try { PdfDocument().grid(2).header(listOf("Nome")) }
                catch (String error) { rejected = rejected + 1 }

                try { PdfDocument().grid(2).widths(listOf(100)) }
                catch (String error) { rejected = rejected + 1 }

                try { PdfDocument().grid(2).widths(listOf(100, -1)) }
                catch (String error) { rejected = rejected + 1 }

                try { PdfColor("XYZ") }
                catch (String error) { rejected = rejected + 1 }

                try {
                    var tooWide = PdfDocument()
                    tooWide.grid(2).widths(listOf(400, 200))
                    tooWide.save("ignorado-largura.pdf")
                } catch (String error) { rejected = rejected + 1 }

                try {
                    var tooShort = PdfDocument()
                    tooShort.grid(1)
                        .style(GridStyle()
                            .rowHeight(10)
                            .headerStyle(TextStyle().fontSize(10)))
                        .header(listOf("Nome"))
                    tooShort.save("ignorado-altura.pdf")
                } catch (String error) { rejected = rejected + 1 }

                if (rejected != 7) {
                    throw "Nem todas as configurações inválidas foram rejeitadas"
                }
            }
            """);
    }

    @Test
    void createsPdfOnScript() throws Exception {
        Path root = tmp.resolve("script");
        Path pdf = root.resolve("script.pdf");
        Path source = prepareParityProbe(root, pdf);

        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(source), root, new String[0]));

        assertEquals(0, result.exitCode(), "SCRIPT output: " + result.stdout());
        assertValidPdf(pdf, "SCRIPT");
    }

    @Test
    void createsPdfOnJs() throws Exception {
        Path root = tmp.resolve("js");
        Path pdf = root.resolve("js.pdf");
        Path source = prepareParityProbe(root, pdf);
        Path out = root.resolve("out");

        CompilationResult result = withLibrary(root,
                () -> driver.compile(source, out, Target.JS));
        assertTrue(result.success(), () -> "JS compile: "
                + result.diagnostics().getDiagnostics());
        try (var stdout = new java.io.ByteArrayOutputStream();
             var stderr = new java.io.ByteArrayOutputStream()) {
            int exitCode = dev.kof.runtime.KofJsRunner.run(
                    out.resolve("Default.mjs"), stdout,
                    java.io.InputStream.nullInputStream(), stderr);
            assertEquals(0, exitCode, "JS stderr: " + stderr);
        }
        assertValidPdf(pdf, "JS");
    }

    @Test
    void createsPdfOnNativeX86() throws Exception {
        assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path root = tmp.resolve("native");
        Path pdf = root.resolve("native.pdf");
        Path source = prepareParityProbe(root, pdf);
        Path out = root.resolve("out");

        CompilationResult result = withLibrary(root,
                () -> driver.compile(source, out, Target.NATIVE));
        assertTrue(result.success(), () -> "Native compile: "
                + result.diagnostics().getDiagnostics());
        Path binary = out.resolve("Default/Main");
        assertTrue(Files.isRegularFile(binary), "Native binary must exist");
        Process process = new ProcessBuilder(binary.toString())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), "Native output: " + output);
        assertValidPdf(pdf, "Native");
    }

    private Path prepareParityProbe(Path root, Path pdf) throws Exception {
        Files.createDirectories(root);
        copyLibrary(root.resolve("kof-install/lib/kof-libs"));
        Path source = root.resolve("Main.kf");
        Files.writeString(source, """
            import pdf.PdfDocument
            import pdf.style.GridStyle
            import pdf.style.PdfColor
            import pdf.style.TextAlign
            import pdf.style.TextStyle

            main() {
                var document = PdfDocument()
                document.title("Relatório de usuários € “Kof” —", 40, 802)
                    .width(515)
                    .align(TextAlign.center)
                    .fontColor(PdfColor("#FF0000"))
                document.text("São Paulo", 40, 760)
                    .width(515)
                    .style(TextStyle()
                        .fontSize(12)
                        .fontColor(PdfColor(37, 99, 235))
                        .align(TextAlign.right))
                document.grid(4)
                    .widths(listOf(140, 50, 170, 155))
                    .style(GridStyle()
                        .rowHeight(30)
                        .padding(5)
                        .borderWidth(1)
                        .headerBackground(PdfColor("#E5E7EB"))
                        .cellBackground(PdfColor("#FFFFFF"))
                        .borderColor(PdfColor("#374151"))
                        .headerStyle(TextStyle().fontSize(10).align(TextAlign.center))
                        .cellStyle(TextStyle().fontSize(10)))
                    .header(listOf("Nome", "Idade", "Cidade", "Profissão"))
                    .row(listOf("João", "28", "São Paulo", "Professor"))
                    .rows(listOf(listOf("Ana", "22", "Belo Horizonte", "Designer")))
                document.save("%s")
            }
            """.formatted(pdf.toString().replace('\\', '/')));
        return source;
    }

    private static void assertValidPdf(Path pdf, String target) throws Exception {
        assertTrue(Files.isRegularFile(pdf), target + " must create the requested PDF");
        String content = Files.readString(pdf, StandardCharsets.ISO_8859_1);
        assertTrue(content.startsWith("%PDF-1.4\n"), target + " PDF header");
        assertTrue(content.endsWith("%%EOF\n"), target + " PDF trailer");
        assertTrue(content.contains("/Encoding /WinAnsiEncoding"), target + " PDF encoding");
        assertTrue(content.contains("Relat\\363rio de usu\\341rios"), target + " accents");
        assertTrue(content.contains("\\200 \\223Kof\\224 \\227"), target + " WinAnsi symbols");
        assertTrue(content.contains("Profiss\\343o"), target + " grid header");
        assertTrue(content.contains("Jo\\343o"), target + " grid row");
        assertTrue(content.contains("40 702 140 30 re"), target + " first custom column");
        assertTrue(content.contains("400 702 155 30 re"), target + " last custom column");
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


    @Override
    protected String outPrefix() {
        return "pdf-out-";
    }



    @Override
    public String libraryName() {
        return "pdf";
    }

    @Override
    public java.util.List<String> libraryMarkers() {
        return java.util.List.of("PdfDocument.kf");
    }

}
