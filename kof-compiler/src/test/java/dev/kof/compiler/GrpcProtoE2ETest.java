package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * `kof.grpc` slice 1 — the protobuf WIRE FORMAT in pure Kof (`libs/grpc/Proto.kf`).
 *
 * <p>The reference vectors below were produced by the REAL protobuf
 * implementation (protoc / the python `google.protobuf` runtime — grpcio
 * 1.85.1), so the codec is proven byte-for-byte against the reference, not
 * against itself. The schema: `Probe { int32 i = 1; string s = 2; bool b = 3;
 * double d = 4; }` (proto3).
 *
 * <p>protobuf wire format: each field = tag varint + payload; tag =
 * (number << 3) | wire_type; proto3 drops DEFAULT values (0/false/"") — so an
 * unset field writes nothing.
 */
class GrpcProtoE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "grpc"; }
    @Override public List<String> libraryMarkers() { return List.of("Proto.kf"); }

    /** Compiles + runs a probe against the installed lib on JVM + Script. */
    private String run(String body, Target target) throws Exception {
        Path dir = tmp.resolve("g" + Math.abs(body.hashCode()) + target);
        Files.createDirectories(dir);
        copyLibrary(dir);
        Files.writeString(dir.resolve("Main.kf"), "import grpc.Proto\n\nmain() {\n" + body + "\n}\n");
        if (target == Target.SCRIPT) {
            // SCRIPT emits no artifacts: it is interpreted (the same frontend)
            var res = driver.interpret(List.of(dir.resolve("Main.kf")), dir, new String[0]);
            assertEquals(0, res.exitCode(), "script exit: " + res.stderr());
            return res.stdout();
        }
        CompilationResult r = driver.compileSources(List.of(dir.resolve("Main.kf")), dir, target);
        if (!r.success()) {
            fail("compile failed: " + r.diagnostics().getDiagnostics());
        }
        var loader = new java.net.URLClassLoader(new java.net.URL[]{dir.toUri().toURL()});
        var main = loader.loadClass("Default.Main");
        var m = main.getMethod("main", String[].class);
        var out = new java.io.ByteArrayOutputStream();
        var oldOut = System.out;
        System.setOut(new java.io.PrintStream(out));
        try { m.invoke(null, (Object) new String[0]); } finally { System.setOut(oldOut); loader.close(); }
        return out.toString();
    }

    @Test
    void varintEdgeValuesMatchTheReferenceBytes() throws Exception {
        String body = """
                var w = newWriter()
                w = writeVarintField(w, 1, 1L)
                println(hex(bytes(w)))
                var w2 = newWriter()
                w2 = writeVarintField(w2, 1, 150L)
                println(hex(bytes(w2)))
                var w3 = newWriter()
                w3 = writeVarintField(w3, 1, 300L)
                println(hex(bytes(w3)))""";
        String expected = "0801\n089601\n08ac02\n";
        assertEquals(expected, run(body, Target.JVM));
        assertEquals(expected, run(body, Target.SCRIPT));
    }

    @Test
    void stringsMatchTheReferenceUtf8Bytes() throws Exception {
        String body = """
                var w = newWriter()
                w = writeStringField(w, 2, "kof")
                println(hex(bytes(w)))
                var w2 = newWriter()
                w2 = writeStringField(w2, 2, "héllo")
                println(hex(bytes(w2)))""";
        String expected = "12036b6f66\n120668c3a96c6c6f\n";
        assertEquals(expected, run(body, Target.JVM));
        assertEquals(expected, run(body, Target.SCRIPT));
    }

    @Test
    void theCompositeMessageMatchesTheReference() throws Exception {
        String body = """
                var w = newWriter()
                w = writeVarintField(w, 1, 7L)
                w = writeStringField(w, 2, "grpc")
                w = writeVarintField(w, 3, 1L)
                println(hex(bytes(w)))""";
        assertEquals("08071204677270631801\n", run(body, Target.JVM));
        assertEquals("08071204677270631801\n", run(body, Target.SCRIPT));
    }

    @Test
    void decodeRoundTripsTheReferenceBytes() throws Exception {
        String body = """
                var raw = parseHex("12036b6f66")
                var t = readTag(Reader(raw, 0))
                println(t.number)
                println(t.wireType)
                println(readString(t.reader).value)""";
        assertEquals("2\n2\nkof\n", run(body, Target.JVM));
    }
}
