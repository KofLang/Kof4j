package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Only {@code java.*}, {@code javax.*} and {@code jdk.*} counted as JDK
 * classes, so the supported {@code com.sun} APIs that named {@code jdk.*}
 * modules export ({@code com.sun.management.OperatingSystemMXBean},
 * {@code com.sun.net.httpserver.HttpServer}) were PKG006 on import. They now
 * resolve; an internal {@code com.sun} package (not exported) stays PKG006.
 */
class JdkComSunApiImportE2ETest {

    @TempDir Path tmp;

    private CompilationResult compile(String name, String kf) throws Exception {
        Path src = tmp.resolve(name + ".kf");
        Files.writeString(src, kf);
        return new CompilerDriver().compile(src, tmp.resolve(name), Target.JVM);
    }

    @Test
    void exportedComSunApisResolve() throws Exception {
        CompilationResult r = compile("Main", """
            import com.sun.management.OperatingSystemMXBean
            import com.sun.net.httpserver.HttpServer
            import java.lang.management.ManagementFactory
            import java.net.InetSocketAddress
            main() {
                val os = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean
                println(os.getAvailableProcessors() > 0)
                val s = HttpServer.create(new InetSocketAddress(0), 0)
                println(s.getAddress().getPort() > 0)
                s.stop(0)
            }
            """);
        assertTrue(r.success(), "must compile: " + r.diagnostics().getDiagnostics());
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true));
        try (URLClassLoader cl = new URLClassLoader(new URL[]{tmp.resolve("Main").toUri().toURL()},
                getClass().getClassLoader())) {
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(old);
        }
        assertEquals("true\ntrue\n", buf.toString());
    }

    @Test
    void anInternalComSunPackageStaysRefused() throws Exception {
        CompilationResult r = compile("Internal", """
            import com.sun.org.apache.xerces.internal.util.XMLChar
            main() { println("x") }
            """);
        assertFalse(r.success(), "a non-exported JDK package must not resolve");
        assertTrue(r.diagnostics().getDiagnostics().stream().anyMatch(d -> "PKG006".equals(d.code())),
                "expected PKG006, was: " + r.diagnostics().getDiagnostics());
    }
}
