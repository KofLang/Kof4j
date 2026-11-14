package dev.kof.compiler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * #1139 — the shared guard for a FACE THAT NEEDS A SYSTEM LIBRARY.
 *
 * <p>The FFI faces (BLAS over {@code libopenblas.so.0}, SVG over
 * {@code librsvg-2.so.2}, pcap over {@code libpcap.so.1}) bind a real system
 * library. A runner (or a dev host) without that library cannot execute the
 * face, and the face used to report that as a REAL TEST FAILURE — the same
 * red for a broken program and for a missing library. That is what made every
 * pull request look broken.
 *
 * <p>This helper answers the question the runner actually asks: can the
 * dynamic loader resolve the soname? The answer is a NAMED environmental skip
 * ({@code assumeTrue}) with the soname in the message, never a silent pass and
 * never a failure dressed up as a regression. The repository's existing
 * pattern is the same one ({@code InteropRE2ETest} skips when
 * {@code Rscript}/{@code jsonlite} are absent; {@code BufferPeekE2ETest}
 * skips when the host libc is absent) — these FFI faces simply did not have
 * it.
 *
 * <p>The check is deliberately shallow but honest: the loader is asked to
 * resolve the soname, the way the JVM's FFM and the dynamic linker will ask
 * at run time. A library that exists but is broken still fails the face — this
 * guard removes the ENVIRONMENTAL reds, it does not weaken the assertion.
 */
final class NativeLibraryAvailability {

    private NativeLibraryAvailability() {}

    /** The soname directories the loader searches on the supported hosts. */
    private static final String[] SEARCH_DIRS = {
            "/usr/lib", "/usr/lib64", "/usr/lib/x86_64-linux-gnu",
            "/lib", "/lib64", "/lib/x86_64-linux-gnu",
            "/usr/local/lib", "/usr/local/lib64",
            "/usr/lib/aarch64-linux-gnu", "/usr/lib/riscv64-linux-gnu",
            "/opt/homebrew/lib", "/usr/local/opt/lib",
    };

    /**
     * True when the dynamic loader can resolve {@code soname} on this host.
     *
     * @param soname the library soname (e.g. {@code libopenblas.so.0})
     */
    static boolean isAvailable(String soname) {
        // 1) the loader's own cache — the authoritative answer for the JVM FFM
        //    path and for the dynamic linker.
        if (loaderResolves(soname)) return true;
        // 2) the filesystem — covers hosts without ldconfig and the vendored
        //    library trees (a Kof FFI face may point at a vendor dir).
        for (String dir : SEARCH_DIRS) {
            if (Files.isRegularFile(Path.of(dir, soname))) return true;
        }
        return false;
    }

    /** The loader's own answer, for a soname resolved through ld.so.cache. */
    static boolean loaderResolves(String soname) {
        try {
            ProcessBuilder pb = new ProcessBuilder("/sbin/ldconfig", "-p");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes());
            p.waitFor();
            return p.exitValue() == 0 && out.contains(soname);
        } catch (Exception e) {
            // ldconfig ausente (host nao-Linux ou sem permissao) — a busca no
            // filesystem cobre o caso; aqui e so um "nao sei".
            return false;
        }
    }

    /** The sonames the loader could NOT resolve, in the order given. */
    static List<String> missing(String... sonames) {
        List<String> missing = new ArrayList<>();
        for (String soname : sonames) {
            if (!isAvailable(soname)) missing.add(soname);
        }
        return missing;
    }

    /**
     * The named skip message for the missing libraries.
     *
     * @param face    the face name (e.g. {@code LinalgBlasFloatE2ETest})
     * @param missing the sonames the loader could not resolve
     */
    static String skipMessage(String face, List<String> missing) {
        return face + ": biblioteca do sistema ausente no runner ("
                + String.join(", ", missing)
                + ") — gate de AMBIENTE, nao regressao do Kof";
    }

    /**
     * The guard a face calls before it runs: skip, NAMED, when the system
     * library is absent; otherwise proceed and let the face assert.
     *
     * <p>It lives HERE, and not as a per-class method, because the test
     * hygiene gate forbids a duplicated method name across test classes (the
     * same {@code dupname} ratchet the §424 JS guard hit).
     *
     * @param face    the face name (e.g. {@code LinalgBlasFloatE2ETest})
     * @param sonames the libraries the face binds
     */
    static void assumePresent(String face, String... sonames) {
        List<String> missing = missing(sonames);
        org.junit.jupiter.api.Assumptions.assumeTrue(missing.isEmpty(),
                skipMessage(face, missing));
    }
}
