package dev.kof.compiler;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

/**
 * Shared readiness probe for the web E2E fixtures: the server is started as a
 * child process (or an in-test runner thread), so the test must wait until it
 * binds the port. This is the single home of that bounded poll — test classes
 * call {@link #awaitListening(Process, int)} / {@link #awaitPort(int, int, long)}
 * instead of duplicating the loop (and its {@code Thread.sleep}) each time.
 *
 * <p>Pure test infrastructure: the compiler is never touched.
 */
final class TestServerFixture {
    private TestServerFixture() {
    }

    /** Default JVM child-process probe: 40 attempts, 100 ms apart (≈4 s). */
    static void awaitListening(Process serverProcess, int port) throws IOException {
        awaitListening(serverProcess, port, 40, 100);
    }

    /**
     * Child-process probe with explicit budget. Fails fast if the process dies,
     * reading its stdout for the diagnostic, and kills it on timeout so a stuck
     * server never leaks past the test.
     */
    static void awaitListening(Process serverProcess, int port, int attempts, long intervalMillis)
            throws IOException {
        await(serverProcess, port, attempts, intervalMillis, () -> awaitPort(port, 1, 0));
    }

    /**
     * Child-process readiness with a custom probe (e.g. a TLS handshake before
     * the port can serve). Fails fast on death and kills the process on timeout,
     * like {@link #awaitListening}. An {@link IOException} from the probe means
     * "not ready yet" and is retried; any other exception aborts immediately, so
     * an {@code AssertionError} raised inside the probe still fails the test.
     */
    static void await(Process serverProcess, int port, int attempts, long intervalMillis,
            Callable<Boolean> ready) throws IOException {
        for (int attempt = 0; attempt < attempts; attempt++) {
            if (!serverProcess.isAlive()) {
                String out = new String(serverProcess.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                        .replace("\r\n", "\n").trim();
                throw new IOException("server exited early: " + out);
            }
            try {
                if (Boolean.TRUE.equals(ready.call())) {
                    return;
                }
            } catch (IOException e) {
                // not ready yet; retry
            } catch (Exception e) {
                throw new IOException("readiness probe failed: " + e, e);
            }
            sleepQuietly(intervalMillis);
        }
        serverProcess.destroyForcibly();
        throw new IOException("server did not start listening on port " + port);
    }

    /**
     * TCP-only probe for servers whose lifetime is not a Java child process
     * (in-test runner threads, native binaries). Returns true as soon as the port
     * accepts a connection, false if the budget expires; it never throws for a
     * refused connection.
     */
    static boolean awaitPort(int port, int attempts, long intervalMillis) {
        for (int attempt = 0; attempt < attempts; attempt++) {
            try (Socket probe = new Socket()) {
                probe.connect(new InetSocketAddress("127.0.0.1", port), 200);
                return true;
            } catch (IOException e) {
                sleepQuietly(intervalMillis);
            }
        }
        return false;
    }

    /**
     * Bounded poll for an arbitrary condition (stat counters, response codes).
     * The condition may throw (e.g. a probe request); a throwing attempt is
     * treated as "not ready yet" and retried. Returns true as soon as it holds,
     * false when the budget expires; never throws for a failing condition.
     */
    static boolean awaitTrue(int attempts, long intervalMillis, Callable<Boolean> condition) {
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                if (Boolean.TRUE.equals(condition.call())) {
                    return true;
                }
            } catch (Exception e) {
                // not ready yet; retry
            }
            sleepQuietly(intervalMillis);
        }
        return false;
    }

    /**
     * Reads ONE HTTP/1.1 response the way a client of a persistent connection
     * must (§773): the head up to the blank line, then exactly Content-Length
     * body bytes; without Content-Length (SSE, close-delimited) it reads to EOF.
     */
    static String readResponse(InputStream in) throws IOException {
        return new String(readResponseBytes(in), StandardCharsets.UTF_8);
    }

    /** {@link #readResponse} as raw bytes (head + body), for binary bodies. */
    static byte[] readResponseBytes(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream raw = new java.io.ByteArrayOutputStream();
        int matched = 0;
        while (matched < 4) {
            int b = in.read();
            if (b == -1) {
                return raw.toByteArray();
            }
            raw.write(b);
            matched = b == (matched % 2 == 0 ? '\r' : '\n') ? matched + 1 : (b == '\r' ? 1 : 0);
        }
        int length = -1;
        for (String line : raw.toString(StandardCharsets.ISO_8859_1).split("\r\n")) {
            if (line.toLowerCase().startsWith("content-length:")) {
                length = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
            }
        }
        raw.write(length >= 0 ? in.readNBytes(length) : in.readAllBytes());
        return raw.toByteArray();
    }

    /** One request on a fresh connection, its response read with {@link #readResponse}. */
    static String exchange(int port, String rawRequest, int timeoutMillis) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(timeoutMillis);
            socket.getOutputStream().write(rawRequest.getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            return readResponse(socket.getInputStream());
        }
    }

    private static void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
