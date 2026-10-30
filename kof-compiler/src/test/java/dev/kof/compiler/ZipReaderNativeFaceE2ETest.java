package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * #852 — the NATIVE face of the streaming reader: the same library, run as a
 * binary on x86-64 (local) and riscv64/aarch64 (under qemu). The JVM/Script
 * suites prove the behaviour; this proves the claim "it runs where `readRange`
 * runs" instead of leaving it a comment in the header: a STORED and a DEFLATE
 * entry streamed in chunks (length + hash against the host's own bytes), a
 * lying declared size (the limit stops the decoder) and a CRC-32 mismatch.
 */
class ZipReaderNativeFaceE2ETest {

    private final ArchiveKofRunner kof = new ArchiveKofRunner();

    private static byte[] words(int bytes, long seed) {
        String[] vocabulary = {"alfa", "beta", "gama", "delta", "kof", "zip", "inflate", "stream", "chunk", "janela"};
        Random random = new Random(seed);
        StringBuilder sb = new StringBuilder();
        while (sb.length() < bytes) {
            sb.append(vocabulary[random.nextInt(vocabulary.length)]).append(' ');
        }
        return sb.substring(0, bytes).getBytes(StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @EnumSource(value = Target.class, names = {"NATIVE", "NATIVE_RISCV64", "NATIVE_AARCH64"})
    void streamsAndRefusesOnNative(Target target, @TempDir Path dir) throws Exception {
        if (target != Target.NATIVE) {
            Assumptions.assumeTrue(NativeRiscv64E2ETest.hasToolchain(target.nativeArch()),
                    target.nativeArch() + " toolchain/QEMU required");
        }
        byte[] stored = "hello stored".getBytes(StandardCharsets.UTF_8);
        byte[] text = words(3000, 7);
        byte[] hello = "hello deflate".getBytes(StandardCharsets.UTF_8);
        Path zip = dir.resolve("lote.zip");
        Files.write(zip, new ZipFixtureBuilder()
                .add("alfa.txt", 0, stored, stored)
                .add("dyn.txt", 8, ZipFixtureBuilder.rawDeflate(text), text)
                .addDeclaring("liar.txt", 8, ZipFixtureBuilder.rawDeflate(text), ArchiveFixtureSupport.crc32(text), 100)
                .addDeclaring("crc.txt", 8, ZipFixtureBuilder.rawDeflate(hello), 12345, hello.length)
                .build());

        Files.writeString(dir.resolve("Main.kf"), ArchiveKofRunner.DRAIN + """

                main() {
                    var z = ZipReader("%s")
                    println("entries=" + z.entries.size())
                    println(drainEntry(z, "alfa.txt", 5))
                    println(drainEntry(z, "dyn.txt", 1000))
                    try {
                        println(drainEntry(z, "liar.txt", 4096))
                    } catch (String m) {
                        println("liar.txt: " + m)
                    }
                    try {
                        println(drainEntry(z, "crc.txt", 4096))
                    } catch (String m) {
                        println("crc.txt: " + m)
                    }
                }
                """.formatted(zip.toAbsolutePath()));

        String expected = String.join("\n",
                "entries=4",
                "alfa.txt chunk=5 len=12 hash=" + ArchiveFixtureSupport.hash(stored) + " chunks=3 verified=true",
                "dyn.txt chunk=1000 len=3000 hash=" + ArchiveFixtureSupport.hash(text) + " chunks=3 verified=true",
                "liar.txt: archive: entry 'liar.txt' inflates past its declared size of 100 bytes — refused",
                "crc.txt: archive: CRC-32 mismatch for 'crc.txt' (archive 12345, computed "
                        + (int) ArchiveFixtureSupport.crc32(hello) + ")");
        assertEquals(expected, kof.nativeRun(target, dir), target + ": stream, limit and CRC on the binary");
    }
}
