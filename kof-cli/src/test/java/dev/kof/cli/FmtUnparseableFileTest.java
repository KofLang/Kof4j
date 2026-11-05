package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §818 — {@code kof fmt src -w} on a tree where one file fails to parse wrote
 * the lossy token-fallback output over it ({@code String? name} became
 * {@code String?name}, {@code json.decode<T>(x)} became
 * {@code json.decode < T > (x)}), so a one-line syntax slip turned into a file
 * of new errors. A file that does not parse is now reported and left byte for
 * byte; the files that parse are still formatted and the exit code is 1.
 */
class FmtUnparseableFileTest {

    private static final String BROKEN = """
            class Repo {
                String? name
                Map<String, Object> load(String raw) {
                    return json.decode<Map<String, Object>>(raw)
                }
                void oops( {
            }
            """;

    @Test
    void anUnparseableFileIsReportedAndLeftUntouched(@TempDir Path dir) throws Exception {
        Path broken = dir.resolve("Broken.kf");
        Files.writeString(broken, BROKEN);
        Path good = dir.resolve("Good.kf");
        String goodIn = "main(){println( \"hi\" )}\n";
        Files.writeString(good, goodIn);

        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream realErr = System.err;
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        int ec;
        try {
            ec = Fmt.run(new String[] { "fmt", dir.toString(), "-w" });
        } finally {
            System.setErr(realErr);
        }
        String stderr = err.toString(StandardCharsets.UTF_8);
        assertEquals(1, ec, "an unparseable file must fail the run: " + stderr);
        assertEquals(BROKEN, Files.readString(broken), "the broken file must not be rewritten");
        assertTrue(stderr.contains("Broken.kf") && stderr.contains("does not parse"), stderr);
        String goodOut = Files.readString(good);
        assertNotEquals(goodIn, goodOut, "the parseable file is still formatted");
        assertTrue(goodOut.contains("println(\"hi\")"), goodOut);
    }
}
