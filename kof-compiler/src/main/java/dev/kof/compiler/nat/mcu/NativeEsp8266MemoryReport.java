package dev.kof.compiler.nat.mcu;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * kof-esp8266 gate 2 (14/11): the MEMORY/STACK REPORT — the budget line that
 * makes the gate-2 exit criterion a MEASURED number, never an assumption.
 *
 * <p>The ESP8266 budget (the plan's §2/§5 + the datasheet): ~80 KB usable DRAM
 * (0x3ffe8000..0x40000000 minus the system reserves), 32 KB IRAM for code,
 * 1 MB flash-mapped irom. `xtensa-esp-elf-size` reads the linked ELF; absent
 * → a NAMED skip (the toolchain guard already named itself).
 */
final class NativeEsp8266MemoryReport {

    private NativeEsp8266MemoryReport() {}

    /** The ESP8266 memory budget (bytes). */
    static final long DRAM_BUDGET = 80L * 1024;   // ~80 KB usable SRAM
    static final long IRAM_BUDGET = 32L * 1024;   // 32 KB IRAM (code)

    record MemoryLine(long data, long bss, long text) {}

    static void print(Path elf, long heapBytes) {
        String home = NativeEsp8266.discoverHome();
        Path size = Path.of(home, "bin/xtensa-esp-elf-size");
        if (!Files.isExecutable(size)) {
            System.err.println("NativeEsp8266: memory report SKIPPED (named) —"
                    + " xtensa-esp-elf-size not provisioned");
            return;
        }
        try {
            MemoryLine m = readSizes(size, elf);
            if (m == null) {
                System.err.println("NativeEsp8266: memory report SKIPPED (named) —"
                        + " the size table was unreadable");
                return;
            }
            long dramUsed = m.data + m.bss + heapBytes;
            System.err.printf(
                    "NativeEsp8266: memory — dram %d/%d B (%.0f%%), iram/text %d/%d B (%.0f%%),"
                            + " heap budget %d B — %s%n",
                    dramUsed, DRAM_BUDGET, 100.0 * dramUsed / DRAM_BUDGET,
                    m.text, IRAM_BUDGET, 100.0 * m.text / IRAM_BUDGET,
                    heapBytes,
                    (dramUsed <= DRAM_BUDGET && m.text <= IRAM_BUDGET)
                            ? "WITHIN the target constraints" : "EXCEEDS the target constraints");
        } catch (IOException | IllegalStateException e) {
            System.err.println("NativeEsp8266: memory report SKIPPED (named): " + e.getMessage());
        }
    }

    /** Runs `xtensa-esp-elf-size`; returns the text/data/bss sums, or null when
     *  the table is unreadable. */
    private static MemoryLine readSizes(Path size, Path elf) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(size.toString(), elf.toString())
                .redirectErrorStream(true);
        Process p = pb.start();
        String out;
        try {
            out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new IllegalStateException("NATIVE002: esp8266-size timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("esp8266-size interrupted", e);
        }
        // the Berkeley table: text data bss dec hex filename
        var lines = out.lines().filter(l -> l.matches(".*\\d+.*\\d+.*")).toList();
        if (lines.isEmpty()) return null;
        var cols = lines.get(lines.size() - 1).trim().split("\\s+");
        try {
            return new MemoryLine(Long.parseLong(cols[0]), Long.parseLong(cols[1]),
                    Long.parseLong(cols[2]));
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return null;
        }
    }
}
