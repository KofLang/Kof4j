package dev.kof.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;

/**
 * Relays what the `kof debug` JVM debuggee prints to the adapter's own stderr
 * (the DAP protocol owns stdout). §541: the bytes are forwarded exactly as
 * read — never re-decoded, never trimmed — so the debuggee is launched to
 * write in the charset of the stream its bytes end up in.
 */
final class DebuggeeOutput {

    private DebuggeeOutput() {
    }

    /** JVM flags that make the debuggee write stdout and stderr in {@code target}'s charset. */
    static List<String> encodingFlags(PrintStream target) {
        String charset = target.charset().name();
        return List.of("-Dstdout.encoding=" + charset, "-Dstderr.encoding=" + charset);
    }

    /** Starts the daemon thread that copies {@code from} into {@code to} until end of stream. */
    static Thread relay(InputStream from, PrintStream to) {
        Thread sink = new Thread(() -> copy(from, to), "debuggee-sink");
        sink.setDaemon(true);
        sink.start();
        return sink;
    }

    static void copy(InputStream from, PrintStream to) {
        byte[] buf = new byte[1024];
        try {
            int n;
            while ((n = from.read(buf)) != -1) {
                to.write(buf, 0, n);
                to.flush();
            }
        } catch (IOException closed) {
            // The session destroyed the debuggee and its pipe is gone: nothing left to relay.
        }
    }
}
