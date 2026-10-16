package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** `kof legacy` (KofBOL fatia 4): copybook → the generated Kof contract. */
class CmdLegacyTest {

    @Test
    void generatesTheContract(@TempDir Path tmp) throws Exception {
        Path cpy = tmp.resolve("CUSTOMER.cpy");
        Files.writeString(cpy, """
                   01  CUSTOMER-RECORD.
                       05  CUST-NAME        PIC X(10).
                       05  CUST-BALANCE     PIC S9(5)V99.
                       05  CUST-QTY         PIC 9(3).
                """, StandardCharsets.UTF_8);
        ByteArrayOutputStream outB = new ByteArrayOutputStream();
        ByteArrayOutputStream errB = new ByteArrayOutputStream();
        int rc = CmdLegacy.run(new String[]{"legacy", cpy.toString(), "customer"},
                new PrintStream(outB, true, StandardCharsets.UTF_8),
                new PrintStream(errB, true, StandardCharsets.UTF_8));
        assertEquals(0, rc, () -> "stderr: " + errB);
        String out = outB.toString(StandardCharsets.UTF_8).strip();
        assertEquals("var customer = Layout(listOf(textOf(\"CUST-NAME\", 10), "
                + "decimalOf(\"CUST-BALANCE\", 7, 2), digitsOf(\"CUST-QTY\", 3)))", out);
    }

    @Test
    void refusesUnsupportedClausesByName(@TempDir Path tmp) throws Exception {
        Path cpy = tmp.resolve("BAD.cpy");
        // OCCURS + REDEFINES + 88 are SUPPORTED since slice 5 — the COMP-3
        // USAGE clause is the honest refusal (the manual packComp3 codec exists;
        // the copybook USAGE attribute does not map yet).
        Files.writeString(cpy, "       05  D PIC 9(3) COMP-3.\n", StandardCharsets.UTF_8);
        ByteArrayOutputStream outB = new ByteArrayOutputStream();
        ByteArrayOutputStream errB = new ByteArrayOutputStream();
        int rc = CmdLegacy.run(new String[]{"legacy", cpy.toString()},
                new PrintStream(outB, true, StandardCharsets.UTF_8),
                new PrintStream(errB, true, StandardCharsets.UTF_8));
        assertEquals(1, rc);
        assertTrue(errB.toString(StandardCharsets.UTF_8).contains("LEG014"),
                () -> "stderr: " + errB);
    }

    @Test
    void usageWhenNoArgs() {
        ByteArrayOutputStream errB = new ByteArrayOutputStream();
        int rc = CmdLegacy.run(new String[]{"legacy"},
                new PrintStream(ByteArrayOutputStream.nullOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(errB, true, StandardCharsets.UTF_8));
        assertEquals(2, rc);
        assertTrue(errB.toString(StandardCharsets.UTF_8).contains("usage:"));
    }
}
