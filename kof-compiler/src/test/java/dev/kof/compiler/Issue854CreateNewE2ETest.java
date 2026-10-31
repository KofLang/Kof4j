package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #854 — {@code File(p).createNew(): Bool}. Exclusive file creation
 * (CREATE_NEW equivalent) that closes the TOCTOU race between exists() and
 * write()/writeBytes(): it fails if the path already exists OR is a symlink,
 * even a dangling one — openat(O_CREAT|O_EXCL) on Linux never follows the
 * last path component when it already exists as a link, a POSIX kernel
 * guarantee, not a JDK/host detail. One golden program, oracle = JVM, parity
 * proven on JS (embedded GraalJS), Native x86-64, and cross riscv64/aarch64
 * (qemu).
 */
class Issue854CreateNewE2ETest extends NativeIoJvmOracleSupport {

    // createNew(new)=true, exists(new)=true,
    // createNew(existing file)=false, readText(existing)=original (untouched),
    // createNew(existing dir)=false,
    // createNew(dangling symlink)=false, exists(dangling symlink)=false
    //   (the target was NOT created through the link — the security proof).
    private static final String EXPECTED = "true\ntrue\nfalse\noriginal\nfalse\nfalse\nfalse";

    private static String program(Path base) {
        return """
                main() {
                    println(File("%1$s/new.txt").createNew())
                    println(File("%1$s/new.txt").exists())
                    File("%1$s/exist.txt").writeText("original")
                    println(File("%1$s/exist.txt").createNew())
                    println(File("%1$s/exist.txt").readText())
                    Directory("%1$s/adir").create()
                    println(File("%1$s/adir").createNew())
                    println(File("%1$s/link").createNew())
                    println(File("%1$s/link").exists())
                }
                """.formatted(base.toString());
    }

    /** Pre-cria o diretorio base e o symlink PENDENTE (fora do programa Kof —
     *  nao ha criacao de symlink no stdlib do Kof; o setup e so o ataque que
     *  a issue descreve: um link plantado no lugar antes da gravacao). */
    private static Path setUpBase(Path tempDir, String tag) throws IOException {
        Path base = tempDir.resolve(tag);
        Files.createDirectories(base);
        Files.createSymbolicLink(base.resolve("link"), base.resolve("ghost-target"));
        return base;
    }

    @Override
    protected Path jvmOracleSource(Path tempDir) throws IOException {
        Path base = setUpBase(tempDir, "jvm");
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, program(base));
        return src;
    }

    @Override
    protected String jvmOracleExpected() {
        return EXPECTED;
    }

    @Test
    void riscv64CreateNewMatchesJvmOracle(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        Path baseJvm = setUpBase(tempDir, "jvm-oracle");
        Path srcJvm = tempDir.resolve("MainJ.kf");
        Files.writeString(srcJvm, program(baseJvm));
        String oracle = runJvm(driver, srcJvm, tempDir.resolve("jvm-out"));
        assertEquals(EXPECTED, oracle, "JVM oracle golden");
        Path base = setUpBase(tempDir, "riscv");
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, program(base));
        assertEquals(oracle, runCross(driver, src, tempDir.resolve("out-riscv"), "qemu-riscv64",
                Target.NATIVE_RISCV64), "riscv64 != JVM oracle (oracle=" + oracle + ")");
    }

    @Test
    void aarch64CreateNewMatchesJvmOracle(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        Path baseJvm = setUpBase(tempDir, "jvm-oracle2");
        Path srcJvm = tempDir.resolve("MainJ2.kf");
        Files.writeString(srcJvm, program(baseJvm));
        String oracle = runJvm(driver, srcJvm, tempDir.resolve("jvm-out2"));
        assertEquals(EXPECTED, oracle, "JVM oracle golden");
        Path base = setUpBase(tempDir, "aarch");
        Path src = tempDir.resolve("Main2.kf");
        Files.writeString(src, program(base));
        assertEquals(oracle, runCross(driver, src, tempDir.resolve("out-aarch"), "qemu-aarch64",
                Target.NATIVE_AARCH64), "aarch64 != JVM oracle (oracle=" + oracle + ")");
    }

    @Test
    void nativeX86CreateNewMatchesJvmOracle(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain x86-64 ausente");
        Path base = setUpBase(tempDir, "nat");
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, program(base));
        assertEquals(EXPECTED, runNative(src, tempDir.resolve("out-nat")), "x86-64 != golden");
    }

    @Test
    void jsCreateNewMatchesJvmOracle(@TempDir Path tempDir) throws IOException {
        Path base = setUpBase(tempDir, "js");
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, program(base));
        Path outJs = tempDir.resolve("out-js");
        CompilationResult r = driver.compile(src, outJs, Target.JS);
        assertTrue(r.success(), "JS compile: " + r.diagnostics().getDiagnostics());
        Path jsFile = outJs.resolve("Default.mjs");
        assertTrue(Files.isRegularFile(jsFile), "generated JS module must exist");
        var stdout = new java.io.ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(jsFile, stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        String js = stdout.toString(StandardCharsets.UTF_8).strip();
        assertEquals(0, exit, "js output: " + js);
        assertEquals(EXPECTED, js, "JS must match the golden (rule 5)");
    }
}
