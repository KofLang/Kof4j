package dev.kof.compiler.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/**
 * B-1c — prova que o gerador das tabelas do Schubfach reproduz EXATAMENTE a
 * tabela {@code MathUtils.g} do JDK 25 (Schubfach): o SHA-256 dos 1234 valores
 * (g1,g0 intercalados, big-endian) do gerador tem de bater com o do array
 * canônico. Sem isso o dtoa libc-free não casa com o oráculo.
 *
 * <p>O hash canônico foi medido lendo {@code
 * jdk.internal.math.MathUtils.g} do {@code src.zip} do JDK 25.0.3.
 */
class SchubfachTableGeneratorTest {

    /** SHA-256 do array {@code g} do MathUtils do JDK (1234 longs, BE). */
    private static final String JDK_G_SHA256 =
            "04f45e7b800acc71417790dc89dd27872beef4f9800715de5e2103891acfbd05";

    private static String sha256(long[] xs) throws Exception {
        ByteBuffer bb = ByteBuffer.allocate(xs.length * 8);
        for (long x : xs) {
            bb.putLong(x);
        }
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bb.array()));
    }

    @Test
    void generatedGTableMatchesJdkMathUtils() throws Exception {
        long[] g = RuntimeDtoaSchubfach.gTable();
        assertEquals(2 * (RuntimeDtoaSchubfach.K_MAX - RuntimeDtoaSchubfach.K_MIN + 1),
                g.length, "tamanho da tabela g");
        assertEquals(JDK_G_SHA256, sha256(g),
                "tabela g gerada != MathUtils.g do JDK (Schubfach divergente)");
    }

    @Test
    void knownEntriesAndHelpers() {
        assertEquals(1076, RuntimeDtoaSchubfach.flog2pow10(324));
        assertEquals(0x4F0CEDC95A718DD4L, RuntimeDtoaSchubfach.g1g0(-324)[0]);
        assertEquals(0x5B01E8B09AA0D1B5L, RuntimeDtoaSchubfach.g1g0(-324)[1]);
        assertEquals(0x7E7B160EF71C1621L, RuntimeDtoaSchubfach.g1g0(-323)[0]);
        assertEquals(0x50F29D7A37C00E29L, RuntimeDtoaSchubfach.g1g0(-321)[0]);
    }

    @Test
    void emittedTablesAreWellFormed() {
        StringBuilder sb = new StringBuilder();
        RuntimeDtoaSchubfach.emitTables(sb);
        String s = sb.toString();
        assertTrue(s.contains(".Lschub_g:"), "rotulo da tabela g");
        assertTrue(s.contains(".Lschub_pow10:"), "rotulo da tabela pow10");
        int gPairs = RuntimeDtoaSchubfach.K_MAX - RuntimeDtoaSchubfach.K_MIN + 1;
        assertEquals(gPairs + 18, s.split("\\.quad", -1).length - 1,
                "617 linhas g + 18 pow10 = 635 linhas .quad");
        // §508: comprimento PAR por construcao — a invariante que torna o guard
        // i+1 < g.length do emitTables trivialmente equivalente ao antigo.
        assertEquals(0, RuntimeDtoaSchubfach.gTable().length % 2,
                "tabela g com comprimento par (invariante do guard §508)");
    }
}
