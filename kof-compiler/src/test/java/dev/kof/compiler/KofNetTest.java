package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** STDLIB S8 — kof.net (6 campos escalares de URI v1 + fachada query*). */
class KofNetTest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            String ufields(String s) {
                return net.scheme(s) + "|" + net.host(s) + "|" + net.port(s) + "|" + net.path(s) + "|" + net.query(s) + "|" + net.fragment(s)
            }
            main() {
                println(ufields("http://example.com/a/b?x=1#frag"))
                println(ufields("https://user:pw@host.io:8443/p?q#f"))
                println(ufields("ftp://files.example.org"))
                println(ufields("/just/a/path?query=1"))
                println(ufields("mailto:someone@x.com"))
                println(ufields("http://h/p#frag?notquery"))
                println(ufields("http://h?onlyquery"))
                println(ufields("HTTP://UPPER.example/Path"))
                println(ufields("noscheme-nocolon"))
                println(ufields("http://:80/x"))
                println(ufields("http://a@b@c:1/p"))
                println(ufields("file:///etc/hosts"))
                println(ufields(""))
                println(ufields("1http://h/x"))
                println(ufields("http://h:8080"))
                println(net.queryEncode("a b&c=1"))
                println(net.queryDecode("a%20b%26c%3D1"))
            }
            """;

    private static final String EXPECTED = String.join("\n",
            "http|example.com||/a/b|x=1|frag",
            "https|host.io|8443|/p|q|f",
            "ftp|files.example.org||||",
            "|||/just/a/path|query=1|",
            "mailto|||someone@x.com||",
            "http|h||/p||frag?notquery",
            "http|h|||onlyquery|",
            "HTTP|UPPER.example||/Path||",
            "|||noscheme-nocolon||",
            "http||80|/x||",
            "http|c|1|/p||",
            "file|||/etc/hosts||",
            "|||||",
            "|||1http://h/x||",
            "http|h|8080|||",
            "a%20b%26c%3D1",
            "a b&c=1");

    @Test
    void netOnJvm(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("Main.kf");
        Files.writeString(file, SRC);
        Path out = tmp.resolve("jvm");
        CompilationResult r = driver.compile(file, out, Target.JVM);
        assertTrue(r.success(), "JVM compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                "-cp", out.toString(), "Default.Main").redirectErrorStream(true).start();
        String o = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor());
        assertEquals(EXPECTED, o);
    }

    @Test
    void netOnJs(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("Main.kf");
        Files.writeString(file, SRC);
        Path out = tmp.resolve("js");
        CompilationResult r = driver.compile(file, out, Target.JS);
        assertTrue(r.success(), "JS compile: " + r.diagnostics().getDiagnostics());
        Path entry;
        try (var s = Files.walk(out)) {
            entry = s.filter(q -> q.getFileName().toString().equals("Default.mjs")).findFirst().orElseThrow();
        }
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(entry, buf,
                java.io.InputStream.nullInputStream(), new java.io.ByteArrayOutputStream());
        assertEquals(0, ec, "JS exit " + ec + " out: " + buf);
        assertEquals(EXPECTED, buf.toString(java.nio.charset.StandardCharsets.UTF_8).trim());
    }

    @Test
    void netOnNativeX86(@TempDir Path tmp) throws Exception {
        // S8-B: x86 portado (RuntimeUri) — mesmos 17 vetores do oracle.
        Path file = tmp.resolve("Main.kf");
        Files.writeString(file, SRC);
        Path out = tmp.resolve("nat");
        CompilationResult r = driver.compile(file, out, Target.NATIVE);
        assertTrue(r.success(), "Native compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(out.resolve("Default/Main").toString())
                .redirectErrorStream(true).start();
        String o = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor());
        assertEquals(EXPECTED, o);
    }

    @Test
    void netOnCrossArch(@TempDir Path tmp) throws Exception {
        // NET001 FECHADO (09/09): net.* portado p/ riscv B24 + aarch translator —
        // mesmos 17 vetores do oracle, byte-idênticos sob qemu.
        assumeToolchain("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64");
        runQemuE(tmp, Target.NATIVE_RISCV64, "qemu-riscv64", SRC, EXPECTED);
        assumeToolchain("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64");
        runQemuE(tmp, Target.NATIVE_AARCH64, "qemu-aarch64", SRC, EXPECTED);
    }

    private static void assumeToolchain(String... tools) {
        for (String c : tools) {
            try {
                Process p = new ProcessBuilder(c, "--version").redirectErrorStream(true).start();
                String o = new String(p.getInputStream().readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8).trim();
                if (p.waitFor() != 0 || o.isEmpty()) {
                    org.junit.jupiter.api.Assumptions.assumeTrue(false, "toolchain ausente: " + c);
                }
            } catch (Exception e) {
                org.junit.jupiter.api.Assumptions.assumeTrue(false, "toolchain ausente: " + c);
            }
        }
    }

    private void runQemuE(Path tempDir, Target target, String qemu, String source,
                          String expected) throws Exception {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path out = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult r = driver.compile(file, out, target);
        assertTrue(r.success(), target + " compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(qemu, out.resolve("Default/Main").toString())
                .redirectErrorStream(true).start();
        String o = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor());
        assertEquals(expected, o, target + " output");
    }
}
