package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for the pure-Kof streaming XML pull reader in
 * {@code libs/file} (kof.file slice 2.4, {@code D-KOF-FILE-GO}). Chunk size 4
 * forces cross-chunk tag reassembly; the golden exercises elements, attributes,
 * empty elements, predefined and numeric entities, CDATA (not decoded),
 * comments, a DOCTYPE with an internal subset, and the mismatch/EOF errors. No
 * compiler change. JVM, Native x86-64 + riscv64 (qemu) and Script run the real
 * golden; JS inherits the {@code IOJS001} compile-time gap.
 */
class XmlReaderE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String XML =
            "<?xml version=\"1.0\"?>\n"
            + "<library>\n"
            + "  <book id=\"1\" lang=\"en\"><title>Kof &amp; XML</title><author>M&#101;l</author></book>\n"
            + "  <book id=\"2\"><title><![CDATA[<raw> & text]]></title><!-- note --><empty/><code>&#72;&#x69;</code></book>\n"
            + "</library>\n";
    private static final String XML_GOLDEN = String.join("\n",
            "start:library",
            "start:book id=1",
            "start:title",
            "text:Kof & XML",
            "end:title",
            "start:author",
            "text:Mel",
            "end:author",
            "end:book",
            "start:book id=2",
            "start:title",
            "text:<raw> & text",
            "end:title",
            "empty:empty",
            "start:code",
            "text:Hi",
            "end:code",
            "end:book",
            "end:library");
    private static final String WITH_DOCTYPE =
            "<!DOCTYPE library [ <!ENTITY x \"y\"> ]>\n<library><a/></library>\n";
    private static final String DOCTYPE_GOLDEN = "start:library\nempty:a\nend:library";

    private static final String NS_XML =
            "<root xmlns=\"urn:def\" xmlns:p=\"urn:p\">\n"
            + "  <child p:attr=\"1\" plain=\"2\"/>\n"
            + "  <p:item xml:lang=\"en\">hi</p:item>\n"
            + "</root>\n";
    private static final String NS_GOLDEN = String.join("\n",
            "start:root@urn:def",
            "empty:child@urn:def p:attr=urn:p plain=",
            "start:item@urn:p xml:lang=http://www.w3.org/XML/1998/namespace",
            "text:hi",
            "end:item@urn:p",
            "end:root@urn:def");

    @Test
    void parsesEventsOnJvm() throws Exception {
        Path src = tmp.resolve("doc.xml");
        Files.writeString(src, XML, StandardCharsets.UTF_8);
        assertEquals(XML_GOLDEN, runJvm(parseProbe(src)));
    }

    @Test
    void skipsDoctypeWithInternalSubset() throws Exception {
        Path src = tmp.resolve("doctype.xml");
        Files.writeString(src, WITH_DOCTYPE, StandardCharsets.UTF_8);
        assertEquals(DOCTYPE_GOLDEN, runJvm(parseProbe(src)));
    }

    @Test
    void mismatchedEndTagThrows() throws Exception {
        Path src = tmp.resolve("bad.xml");
        Files.writeString(src, "<a><b></a>", StandardCharsets.UTF_8);
        assertEquals("XML: end tag </a> does not match <b>", runJvm(errorProbe(src)));
    }

    @Test
    void unclosedElementAtEofThrows() throws Exception {
        Path src = tmp.resolve("unclosed.xml");
        Files.writeString(src, "<a><b>", StandardCharsets.UTF_8);
        assertEquals("XML: unexpected end of document (unclosed element <b>)", runJvm(errorProbe(src)));
    }

    @Test
    void resolvesNamespacesOnJvm() throws Exception {
        Path src = tmp.resolve("ns.xml");
        Files.writeString(src, NS_XML, StandardCharsets.UTF_8);
        assertEquals(NS_GOLDEN, runJvm(namespaceProbe(src)));
    }

    @Test
    void resolvesNamespacesOnScript() throws Exception {
        Path root = tmp.resolve("script-ns");
        Files.createDirectories(root);
        Path src = root.resolve("ns.xml");
        Files.writeString(src, NS_XML, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("Main.kf"), namespaceProbe(src));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(NS_GOLDEN, result.stdout().strip());
    }

    @Test
    void resolvesNamespacesOnNativeX86() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path src = tmp.resolve("ns-x86.xml");
        Files.writeString(src, NS_XML, StandardCharsets.UTF_8);
        assertEquals(NS_GOLDEN, runNativeX86(namespaceProbe(src)));
    }

    @Test
    void resolvesNamespacesOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        Path src = tmp.resolve("ns-riscv.xml");
        Files.writeString(src, NS_XML, StandardCharsets.UTF_8);
        assertEquals(NS_GOLDEN, runCrossCode("riscv64", Target.NATIVE_RISCV64, namespaceProbe(src)));
    }

    @Test
    void unboundPrefixThrows() throws Exception {
        Path src = tmp.resolve("unbound.xml");
        Files.writeString(src, "<a><q:b/></a>", StandardCharsets.UTF_8);
        assertEquals("XML: unbound namespace prefix 'q'", runJvm(errorProbe(src)));
    }

    @Test
    void namespaceScopeIsRestoredAfterElement() throws Exception {
        Path src = tmp.resolve("scope.xml");
        Files.writeString(src, "<r><a xmlns:p=\"u\"><p:b/></a><p:c/></r>", StandardCharsets.UTF_8);
        assertEquals("XML: unbound namespace prefix 'p'", runJvm(errorProbe(src)));
    }

    @Test
    void parsesOnScript() throws Exception {
        Path root = tmp.resolve("script-xml");
        Files.createDirectories(root);
        Path src = root.resolve("doc.xml");
        Files.writeString(src, XML, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("Main.kf"), parseProbe(src));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(XML_GOLDEN, result.stdout().strip());
    }

    @Test
    void parsesOnNativeX86() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path src = tmp.resolve("doc-x86.xml");
        Files.writeString(src, XML, StandardCharsets.UTF_8);
        assertEquals(XML_GOLDEN, runNativeX86(parseProbe(src)));
    }

    @Test
    void parsesOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        Path src = tmp.resolve("doc-riscv.xml");
        Files.writeString(src, XML, StandardCharsets.UTF_8);
        assertEquals(XML_GOLDEN, runCrossCode("riscv64", Target.NATIVE_RISCV64, parseProbe(src)));
    }

    @Test
    void readRangeGapOnJsAppliesToXml() throws Exception {
        Path root = tmp.resolve("js-xml");
        Files.createDirectories(root);
        Path src = root.resolve("doc.xml");
        Files.writeString(src, XML, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("Main.kf"), parseProbe(src));
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root,
                () -> compile(root.resolve("Main.kf"), out, Target.JS));
        assertTrue(!result.success(), "JS must refuse the missing kof.io readRange binding (R6)");
        String diag = result.diagnostics().getDiagnostics().toString();
        assertTrue(diag.contains("IOJS001"),
                () -> "expected the explicit IOJS001 gap diagnostic, got: " + diag);
    }

    private static String parseProbe(Path src) {
        return """
            import file.Xml

            String attrOr(Map<String, String> m, String k, String dflt) {
                var v = m.get(k)
                if (v == null) {
                    return dflt
                }
                return v
            }

            main() {
                var reader = XmlReader("%s", 4)
                var ev = reader.next()
                while (ev != null) {
                    if (ev != null) {
                        if (ev.kind == "text") {
                            println("text:" + ev.text)
                        } else if (ev.kind == "start") {
                            var line = "start:" + ev.name
                            if (ev.attributes.containsKey("id")) {
                                line = line + " id=" + attrOr(ev.attributes, "id", "")
                            }
                            println(line)
                        } else if (ev.kind == "empty") {
                            println("empty:" + ev.name)
                        } else {
                            println("end:" + ev.name)
                        }
                    }
                    ev = reader.next()
                }
            }
            """.formatted(path(src));
    }

    private static String namespaceProbe(Path src) {
        return """
            import file.Xml

            String nsOf(Map<String, String> m, String k) {
                var v = m.get(k)
                if (v == null) {
                    return "<absent>"
                }
                return v
            }

            main() {
                var reader = XmlReader("%s", 4)
                var ev = reader.next()
                while (ev != null) {
                    if (ev.kind == "text") {
                        println("text:" + ev.text)
                    } else if (setOf("start", "empty").contains(ev.kind)) {
                        var line = ev.kind + ":" + ev.localName + "@" + ev.namespaceUri
                        if (ev.attributes.containsKey("p:attr")) {
                            line = line + " p:attr=" + nsOf(ev.attributeNamespaces, "p:attr")
                        }
                        if (ev.attributes.containsKey("plain")) {
                            line = line + " plain=" + nsOf(ev.attributeNamespaces, "plain")
                        }
                        if (ev.attributes.containsKey("xml:lang")) {
                            line = line + " xml:lang=" + nsOf(ev.attributeNamespaces, "xml:lang")
                        }
                        println(line)
                    } else {
                        println("end:" + ev.localName + "@" + ev.namespaceUri)
                    }
                    ev = reader.next()
                }
            }
            """.formatted(path(src));
    }

    private static String errorProbe(Path src) {
        return """
            import file.Xml

            main() {
                try {
                    var reader = XmlReader("%s", 4)
                    var ev = reader.next()
                    while (ev != null) {
                        ev = reader.next()
                    }
                    println("no error")
                } catch (String e) {
                    println(e)
                }
            }
            """.formatted(path(src));
    }

    private static String path(Path p) {
        return p.toString().replace('\\', '/');
    }

    private String runJvm(String code) throws Exception {
        Path root = tmp.resolve("jvm-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Path source = root.resolve("Main.kf");
        Files.writeString(source, code);
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root, () -> compile(source, out, Target.JVM));
        assertTrue(result.success(), () -> "compile: " + result.diagnostics().getDiagnostics());

        var stdout = new java.io.ByteArrayOutputStream();
        var previousOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(stdout, true, StandardCharsets.UTF_8));
            try (var loader = new URLClassLoader(
                    new java.net.URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
                Class.forName("Default.Main", true, loader)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            }
        } finally {
            System.setOut(previousOut);
        }
        return stdout.toString(StandardCharsets.UTF_8).strip();
    }

    private String runNativeX86(String code) throws Exception {
        Path root = tmp.resolve("native-x86-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        withLibrary(root, () -> {
            CompilationResult result = compile(root.resolve("Main.kf"), out, Target.NATIVE);
            assertTrue(result.success(),
                    () -> "native compile: " + result.diagnostics().getDiagnostics());
            return null;
        });
        return runBinary(out.resolve("Default/Main"));
    }

    private String runCrossCode(String arch, Target target, String code) throws Exception {
        Path root = tmp.resolve("cross-" + arch + "-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        withLibrary(root, () -> {
            CompilationResult result = compile(root.resolve("Main.kf"), out, target);
            assertTrue(result.success(),
                    () -> arch + " compile: " + result.diagnostics().getDiagnostics());
            return null;
        });
        Path binary = out.resolve("Default/Main");
        assertTrue(Files.isRegularFile(binary), arch + " binary must exist");
        ProcessBuilder pb = NativeRiscv64E2ETest.qemu(arch, binary);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, process.waitFor(), arch + " output: " + output);
        return output;
    }

    private static String runBinary(Path binary) throws Exception {
        Process process = new ProcessBuilder(binary.toString())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, process.waitFor(), "native output: " + output);
        return output;
    }

    private CompilationResult compile(Path source, Path out, Target target) {
        return driver.compile(source, out, target);
    }

    private static boolean has(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c)
                        .redirectErrorStream(true).start();
                if (p.waitFor() != 0) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private <T> T withLibrary(Path root, CheckedSupplier<T> action) throws Exception {
        copyLibrary(root.resolve("kof-install/lib/kof-libs"));
        String previous = System.getProperty("kof.install.dir");
        System.setProperty("kof.install.dir", root.resolve("kof-install").toString());
        try {
            return action.get();
        } finally {
            if (previous == null) System.clearProperty("kof.install.dir");
            else System.setProperty("kof.install.dir", previous);
        }
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    private static void copyLibrary(Path destinationRoot) throws Exception {
        Path sourceRoot = findLibraryRoot();
        try (var files = Files.walk(sourceRoot)) {
            for (Path source : files.filter(Files::isRegularFile).toList()) {
                Path destination = destinationRoot.resolve("file")
                        .resolve(sourceRoot.relativize(source));
                Files.createDirectories(destination.getParent());
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static Path findLibraryRoot() {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        Path fromRepository = workingDirectory.resolve("libs/file");
        if (Files.isRegularFile(fromRepository.resolve("Xml.kf"))) return fromRepository;

        Path fromModule = workingDirectory.resolve("../libs/file").normalize();
        if (Files.isRegularFile(fromModule.resolve("Xml.kf"))) return fromModule;

        throw new IllegalStateException("libs/file not found from " + workingDirectory);
    }
}
