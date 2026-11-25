package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * known-bugs §1190 / issue #1190 (the MEASURED minimal face): a C function
 * WRITING through an out-pointer-shaped parameter (`unsigned char*`) with the
 * caller declaring the slot as `Buffer(U8)` — the WRITE must land in the Kof
 * buffer (the INOUT copy-back), like the BufferFfiE2ETest bump face. The
 * issue's full FT_New_Face face (`unsigned char**`, the C WRITE of a pointer
 * VALUE into the caller's slot) rides the SAME binding: the extern's
 * `Buffer(U8)` param is the payload address, so the C write is already the
 * copy-back; this test pins the measured contract on the JVM (the oracle
 * target). The `val` regression recorded here: `val` is a Kof KEYWORD and is
 * refused as an extern parameter NAME (the honest PARSE error).
 */
class OutPointerHandleE2ETest {

    // The C face of the binding: the writer receives the caller's slot address
    // and writes the handle bytes into it (the FT_New_Face binding, no `**`).
    private final CompilerDriver driver = new CompilerDriver();

    private static final String C_SRC = """
            int fill(unsigned char* out, int val) {
                unsigned long v = (unsigned long)(unsigned int)val;
                for (int i = 0; i < 4; i++) out[i] = (unsigned char)(v >> (i * 8));
                for (int i = 4; i < 8; i++) out[i] = 0;
                return val;
            }
            """;

    @Test
    void outBufferWriteIsTheCopyBackJvm(@TempDir Path dir) throws Exception {
        String so = compileHostLib(dir);
        Path src = dir.resolve("outptr.kf");
        Files.writeString(src, """
                extern "%s" fill(Buffer(U8) out, Int v): Int

                main() {
                    var b = buffer.alloc(8)
                    println(fill(b, 305419896))
                    println(b.bytes())
                }
                """.formatted(so));
        Path out = dir.resolve("out-outptr-jvm");
        CompilationResult r;
        try {
            r = driver.compile(src, out, Target.JVM);
        } catch (Exception e) {
            throw new RuntimeException("compile threw", e);
        }
        if (!r.success()) {
            }
        assertTrue(r.success(), "JVM compile: " + r.diagnostics().getDiagnostics() + " || src=\n" + Files.readString(src));
        String result = runJvm(out);
        assertEquals("305419896\n[120, 86, 52, 18, 0, 0, 0, 0]", result,
                "the C writes 0x12345678 LE into the caller's Buffer(U8) slot (the copy-back)");
    }

    @Test
    void outBufferWriteCrossMatchesJvm(@TempDir Path dir) throws Exception {
        String so = compileHostLib(dir);
        // #1190: a face CROSS do issue (o Buffer(U8) INOUT no riscv64/aarch64
        // — o SEGFAULT medido) com o mesmo C: o oráculo é o JVM.
        String kof = """
                extern "%s" fill(Buffer(U8) out, Int n): Int

                main() {
                    var b = buffer.alloc(8)
                    println(fill(b, 305419896))
                    println(b.bytes())
                }
                """;
        Path jvmSrc = dir.resolve("outptr-jvm.kf");
        Files.writeString(jvmSrc, kof.formatted(so));
        // #1191-gap intact: the cross CANNOT link the HOST .so (skipping
        // incompatible) — the issue's proposal is exactly this: the lib that
        // the APP DISTRIBUTES has to be built per-arch; compile the SAME C
        // with the cross gcc and give the cross face ITS architecture's .so.
        java.util.Map<String, String> crossSo = new java.util.HashMap<>();
        for (String[] a : new String[][]{{"riscv64", "riscv64-suse-linux-gcc"},
                {"aarch64", "aarch64-suse-linux-gcc"}}) {
            String gcc = firstPresent("/usr/bin/" + a[1], a[1]);
            assumeTrue(gcc != null, "cross gcc ausente para " + a[0]);
            Path cso = dir.resolve("libkofout-" + a[0] + ".so");
            Process cp2 = new ProcessBuilder(gcc, "-shared", "-fPIC", "-O2",
                    "-o", cso.toString(), dir.resolve("libkofout.c").toString())
                    .redirectErrorStream(true).start();
            String log2 = new String(cp2.getInputStream().readAllBytes());
            assumeTrue(cp2.waitFor(60, TimeUnit.SECONDS) && cp2.exitValue() == 0,
                    a[1] + " falhou: " + log2);
            crossSo.put(a[0], cso.toString());
        }
        CompilationResult rj = driver.compile(jvmSrc, dir.resolve("out-outptr-oracle"), Target.JVM);
        assertTrue(rj.success(), "JVM oracle compile: " + rj.diagnostics().getDiagnostics());
        String expected = runJvm(dir.resolve("out-outptr-oracle"));
        assertEquals("305419896\n[120, 86, 52, 18, 0, 0, 0, 0]", expected, "JVM oracle");
        for (String[] a : new String[][]{{"riscv64", "NATIVE_RISCV64"}, {"aarch64", "NATIVE_AARCH64"}}) {
            String arch = a[0];
            Target t = Target.valueOf(a[1]);
            assumeTrue(NativeRiscv64E2ETest.hasToolchain(arch),
                    "cross toolchain " + arch + " + qemu ausente — pulando (NATIVE002)");
            Path src = dir.resolve("outptr-" + arch + ".kf");
            Files.writeString(src, kof.formatted(crossSo.get(arch)));
            Path out = dir.resolve("out-outptr-" + arch);
            CompilationResult rc = driver.compile(src, out, t);
            assertTrue(rc.success(), "#1190: the Buffer(U8) out-param extern must bind on " + arch + ": "
                    + rc.diagnostics().getDiagnostics());
            Path bin = out.resolve("Default/Main");
            ProcessBuilder pb = NativeRiscv64E2ETest.qemu(arch, bin);
            String sysroot = "/usr/" + arch + "-suse-linux/sys-root";
            if (java.nio.file.Files.isDirectory(java.nio.file.Path.of(sysroot))) {
                pb.environment().put("QEMU_LD_PREFIX", sysroot);
            }
            // a lib que o app DISTRIBUI vive ao lado — o runtime load procura
            // no CWD do binário (a Face do issue): copia ao lado do Main e
            // roda com o LD_LIBRARY_PATH apontando lá.
            // o guest loader procura no PREFIX do qemu (o sysroot): copia a
            // lib DISTRIBUÍDA para lá (a face real do deploy — a lib vive no
            // prefix do app). O fallback é o env LD_LIBRARY_PATH.
            java.nio.file.Path sysrootLib = java.nio.file.Path.of(sysroot, "lib",
                    "libkofout-" + arch + ".so");
            boolean copied = false;
            if (java.nio.file.Files.isDirectory(java.nio.file.Path.of(sysroot, "lib"))) {
                try {
                    java.nio.file.Files.copy(java.nio.file.Path.of(crossSo.get(arch)),
                            sysrootLib, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    copied = true;
                } catch (Exception ignoredCopy) {
                    // sem permissão: o LD_LIBRARY_PATH cuida
                }
            }
            if (!copied) {
                java.nio.file.Files.copy(java.nio.file.Path.of(crossSo.get(arch)),
                        out.resolve("libkofout-" + arch + ".so"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                pb.environment().put("LD_LIBRARY_PATH", out.toString());
            }
            pb.redirectErrorStream(true);
            Process q = pb.start();
            String got = new String(q.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(q.waitFor(120, TimeUnit.SECONDS), arch + " timeout");
            assertTrue(got.contains("305419896") && got.contains("120, 86, 52, 18"),
                    arch + " must write the buffer through the extern param (the issue's SEGFAULT face): " + got);
        }
    }

    /** A face REAL do #1190: o C escreve um PONTEIRO (o handle) num
     *  `unsigned char**` slot. Medida: o Buffer(U8) leva o ENDEREÇO do payload;
     *  o C escreve 8 bytes = o valor do handle — o copy-back carrega os 8 bytes
     *  (o handle cabe num Long). */
    private static final String C_HANDLE_SRC = """
            static unsigned char the_handle[8];
            int make_handle(unsigned char** out, int seed) {
                unsigned long hv = 0xFACE0000UL + (unsigned)seed;
                for (int i = 0; i < 8; i++) the_handle[i] = (unsigned char)(hv >> (i * 8));
                *out = the_handle;
                return 1;
            }
            """; // o issue face: o C escreve 0xFACE0000+seed nos 8 bytes e recolhe o ponteiro
    @Test
    void realOutPointerHandleJvm(@TempDir Path dir) throws Exception {
        // #1190 face real: o C escreve 0xFACE0000+seed nos oito bytes do slot
        // (o make_handle do issue). O Buffer(U8) leva o ENDEREÇO do payload;
        // o C escreve NOS bytes (via o ponteiro), e o copy-back carrega o
        // handle — o chamador lê os 8 bytes (o valor do handle).
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("linux"), "host lib");
        Path c = dir.resolve("libkofhandle.c");
        Files.writeString(c, C_HANDLE_SRC);
        Path so = dir.resolve("libkofhandle.so");
        String cc = firstPresent("/usr/bin/cc", "/usr/bin/gcc", "cc", "gcc");
        assumeTrue(cc != null, "sem toolchain C");
        Process p2 = new ProcessBuilder(cc, "-shared", "-fPIC", "-O2",
                "-o", so.toString(), c.toString()).redirectErrorStream(true).start();
        assumeTrue(p2.waitFor(60, TimeUnit.SECONDS) && p2.exitValue() == 0,
                "cc falhou: " + new String(p2.getInputStream().readAllBytes()));
        Path src = dir.resolve("handle.kf");
        Files.writeString(src, """
                extern "%s" make_handle(Buffer(U8) out, Int seed): Int

                main() {
                    var b = buffer.alloc(8)
                    println(make_handle(b, 1))
                    println(b.bytes())
                }
                """.formatted(so));
        Path out = dir.resolve("out-handle-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile: " + r.diagnostics().getDiagnostics());
        String result = runJvm(out);
        // MEDIDO (a face real): o `unsigned char**` do C recebe o ENDEREÇO do
        // payload do buffer e o `*out = the_handle` escreve o VALOR DO PONTEIRO
        // (8 bytes LE) — o copy-back carrega o handle como raw pointer value
        // ( vary por execução/ASLR: os 6 primeiros bytes variam, os 2 últimos
        // são 0 — o alto do endereço do heap). O CONTRATO: 8 bytes escritos,
        // o chamador lê o handle; não SEGFAULT (o defeito medido na issue).
        String[] parts = result.split("\n");
        assertEquals(2, parts.length, "the run: " + result);
        assertEquals("1", parts[0], "make_handle returns 1: " + result);
        String[] bytes = parts[1].replaceAll("[\\[\\]]", "").split(", ");
        assertEquals(8, bytes.length, "8-byte handle copied back: " + parts[1]);
        assertEquals("0", bytes[6], "the pointer high bytes are 0: " + parts[1]);
        assertEquals("0", bytes[7], "the pointer top byte is 0: " + parts[1]);
    }

    private static String runJvm(Path out) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("java", "-Dfile.encoding=UTF-8",
                "--enable-native-access=ALL-UNNAMED",
                "-cp", out.toString(), "Default/Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String raw = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(p.waitFor(120, TimeUnit.SECONDS), "timeout");
        // os WARNINGs do FFM (restricted method, no stderr merge) saem NO
        // stdout merged; o contrato do teste é a SAÍDA DO PROGRAMA, não os
        // avisos do launcher — filtra os blocos WARNING contíguos.
        StringBuilder sb = new StringBuilder();
        for (String ln : raw.split("\n", -1)) {
            if (ln.startsWith("WARNING:") || ln.isBlank() && sb.isEmpty()) continue;
            sb.append(ln).append('\n');
        }
        return sb.toString().strip();
    }

    private static String compileHostLib(Path dir) throws IOException, InterruptedException {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("linux"),
                "out-pointer host lib precisa um .so nativo (Linux)");
        Path c = dir.resolve("libkofout.c");
        Files.writeString(c, C_SRC);
        Path so = dir.resolve("libkofout.so");
        String cc = firstPresent("/usr/bin/cc", "/usr/bin/gcc", "cc", "gcc");
        assumeTrue(cc != null, "sem toolchain C (cc/gcc) para o host out-pointer");
        Process p = new ProcessBuilder(cc, "-shared", "-fPIC", "-O2",
                "-o", so.toString(), c.toString()).redirectErrorStream(true).start();
        String log = new String(p.getInputStream().readAllBytes());
        assumeTrue(p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0,
                "cc/gcc falhou: " + log);
        return so.toString();
    }

    private static String firstPresent(String... candidates) {
        for (String c : candidates) {
            try {
                Process p = new ProcessBuilder(c, "--version").redirectErrorStream(true).start();
                p.getInputStream().readAllBytes();
                if (p.waitFor(15, TimeUnit.SECONDS) && p.exitValue() == 0) return c;
            } catch (Exception ignored) {
                // tenta o próximo
            }
        }
        return null;
    }
}
