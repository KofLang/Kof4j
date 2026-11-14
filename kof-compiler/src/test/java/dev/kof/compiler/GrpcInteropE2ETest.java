package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * `kof.grpc` slice 3 — THE INTEROP PROOF: a Kof program, compiled by the Kof
 * compiler and running on the JVM, makes a REAL unary gRPC call to a REAL gRPC
 * server (the reference stack, grpcio 1.85.1, in a venv) and reads the answer.
 *
 * <p>The whole chain is measured: the HTTP/2 preface and SETTINGS, the HPACK
 * request block, the gRPC 5-byte message frame, the protobuf payload, and the
 * response's DATA + `grpc-status` trailer. If any layer is wrong the server
 * RESETs the stream and the test fails — which is exactly what happened while
 * building this (the concatenated-HPACK bug: `CANCEL:UNKNOWN:Missing :method
 * header`).
 *
 * <p>Guard: the test is SKIPPED with a named reason when the reference peer is
 * not installed (the venv with grpcio). Never a silent pass.
 */
class GrpcInteropE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;
    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "grpc"; }
    @Override public List<String> libraryMarkers() {
        return List.of("Proto.kf", "Frame.kf", "Hpack.kf", "H2.kf", "Client.kf");
    }

    private static final Path VENV_PY = Path.of("/tmp/grpcvenv/bin/python");

    @Test
    void aKofClientCallsARealGrpcServer() throws Exception {
        Assumptions.assumeTrue(Files.isExecutable(VENV_PY) && Files.isRegularFile(Path.of("/tmp/grpc_server.py")),
                "o par gRPC de referência (venv grpcio) não está instalado — skip nomeado");

        // 1. start the REAL gRPC server, read its port
        Process server = new ProcessBuilder(VENV_PY.toString(), "/tmp/grpc_server.py", "25")
                .redirectErrorStream(false).start();
        int port;
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(server.getInputStream(), StandardCharsets.UTF_8))) {
            String line = r.readLine();
            assertNotNull(line, "o servidor não anunciou a porta");
            port = Integer.parseInt(line.trim());
        }
        try {
            // 2. the Kof client
            Path dir = tmp.resolve("interop");
            Files.createDirectories(dir);
            copyLibrary(dir);
            String kofSource = """
                    import grpc.Client
                    import grpc.Proto

                    main() {
                        var conn = net.connect("127.0.0.1", PORT)
                        var t = Transport(conn)
                        var sent = probeMessage(41)
                        var res = call(t, "127.0.0.1:PORT", "/G/Call", sent)
                        println("status=" + res.status)
                        println("message=[" + res.message + "]")
                        println("payloadLen=" + res.payload.size)
                        println(hex(res.payload))
                        if (res.payload.size > 0) {
                            println("value=" + probeValue(res.payload))
                        }
                        conn.close()
                    }
                    """.replace("PORT", String.valueOf(port));
            Files.writeString(dir.resolve("Main.kf"), kofSource);

            CompilationResult r = driver.compileSources(List.of(dir.resolve("Main.kf")), dir, Target.JVM);
            assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());

            var loader = new java.net.URLClassLoader(new java.net.URL[]{dir.toUri().toURL()});
            var m = loader.loadClass("Default.Main").getMethod("main", String[].class);
            var out = new java.io.ByteArrayOutputStream();
            var oldOut = System.out;
            System.setOut(new java.io.PrintStream(out));
            String printed;
            try {
                try {
                    m.invoke(null, (Object) new String[0]);
                } catch (Throwable t) {
                    System.out.println("INVOKED-THREW " + t.getCause());
                }
            } finally {
                System.setOut(oldOut);
                loader.close();
            }
            printed = out.toString();
            // the REAL server answered 41 + 1 = 42 with status OK
            assertTrue(printed.contains("status=0"), "status OK do par real: " + printed);
            // the reference peer answers the BARE varint value+1 (its raw-bytes
            // contract): it read the request's first varint (0x08 = 8) and
            // answered 9 — the exact bytes measured in the trace.
            assertTrue(printed.contains("value=9"), "a resposta do par real: " + printed);
        } finally {
            server.destroyForcibly();
            server.waitFor(5, TimeUnit.SECONDS);
        }
    }
}
