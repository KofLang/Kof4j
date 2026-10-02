package dev.kof.c;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Slice 1 do plano de cross ({@code docs/development/kof-c-cross.md}): o
 * subconjunto C atual passa a emitir TAMBÉM riscv64/aarch64. O oráculo é o
 * próprio alvo x86_64 do host (mesmo programa, mesma saída) e a concordância
 * byte-a-byte entre as duas archs sob qemu — REGRA 5, medição real, nunca
 * memória. Ferramenta ausente → {@code assumeTrue} (skip honesto, sem
 * falso-verde).
 */
class KofCCrossCompilerTest extends KofCSupport {

    /** Programa que cobre o subconjunto inteiro: globais, atribuição, laço,
     *  desvio, endereço/deref e binárias (aritmética + comparação). */
    private static final String FULL_SUBSET = """
            int x;
            int y;
            int p;
            void main() {
              x = 42;
              print_arg = x;
              print();
              y = 0;
              while(y < 5) { y = y + 1; }
              print_arg = y;
              print();
              x = 10;
              y = 0;
              if(x > 5) { y = 1; }
              print_arg = y;
              print();
              x = 99;
              p = &x;
              *(int*)p = 42;
              print_arg = x;
              print();
              x = 10;
              y = 3;
              print_arg = x + y;
              print();
            }
            """;

    private static final String FULL_GOLDEN = "42\n5\n1\n42\n13";

    private static final String COMPARISONS = """
            int a;
            int b;
            void main() {
              a = 7;
              b = 3;
              print_arg = a == b;
              print();
              print_arg = a != b;
              print();
              print_arg = a < b;
              print();
              print_arg = a > b;
              print();
              print_arg = a <= b;
              print();
              print_arg = a >= b;
              print();
              print_arg = a << b;
              print();
              print_arg = a >> b;
              print();
            }
            """;

    private static final String COMPARISONS_GOLDEN = "0\n1\n0\n1\n0\n1\n56\n0";



    /** Compila e roda no alvo (sob qemu quando cross), devolvendo o stdout. */

    @Test
    void riscv64FullSubsetMatchesGolden(@TempDir Path tmp) throws Exception {
        requireTools(KofCTarget.RISCV64);
        assertEquals(FULL_GOLDEN, run(KofCTarget.RISCV64, tmp, FULL_SUBSET));
    }

    @Test
    void aarch64FullSubsetMatchesGolden(@TempDir Path tmp) throws Exception {
        requireTools(KofCTarget.AARCH64);
        assertEquals(FULL_GOLDEN, run(KofCTarget.AARCH64, tmp, FULL_SUBSET));
    }

    @Test
    void bothCrossTargetsAgreeWithTheX86Oracle(@TempDir Path tmp) throws Exception {
        requireTools(KofCTarget.RISCV64);
        requireTools(KofCTarget.AARCH64);
        String oracle = run(KofCTarget.X86_64, tmp.resolve("x86"), FULL_SUBSET);
        String riscv = run(KofCTarget.RISCV64, tmp.resolve("rv"), FULL_SUBSET);
        String aarch = run(KofCTarget.AARCH64, tmp.resolve("arm"), FULL_SUBSET);
        assertEquals(oracle, riscv, "riscv64 deve concordar com o oráculo x86_64");
        assertEquals(oracle, aarch, "aarch64 deve concordar com o oráculo x86_64");
    }

    @Test
    void zeroPrintsZeroOnBothCrossTargets(@TempDir Path tmp) throws Exception {
        String src = "int x;\nvoid main() { x = 0; print_arg = x; print(); }\n";
        requireTools(KofCTarget.RISCV64);
        requireTools(KofCTarget.AARCH64);
        assertEquals("0", run(KofCTarget.RISCV64, tmp.resolve("rv"), src));
        assertEquals("0", run(KofCTarget.AARCH64, tmp.resolve("arm"), src));
    }

    @Test
    void comparisonsAndShiftsAgreeOnBothCrossTargets(@TempDir Path tmp) throws Exception {
        requireTools(KofCTarget.RISCV64);
        requireTools(KofCTarget.AARCH64);
        String oracle = run(KofCTarget.X86_64, tmp.resolve("x86"), COMPARISONS);
        assertEquals(COMPARISONS_GOLDEN, oracle, "oráculo x86_64");
        assertEquals(COMPARISONS_GOLDEN, run(KofCTarget.RISCV64, tmp.resolve("rv"), COMPARISONS));
        assertEquals(COMPARISONS_GOLDEN, run(KofCTarget.AARCH64, tmp.resolve("arm"), COMPARISONS));
    }

    @Test
    void userDeclaredPrintArgIsNotDuplicated(@TempDir Path tmp) throws Exception {
        // print_arg declarado pelo usuário: o helper NÃO deve redeclará-lo
        // (símbolo duplicado quebraria o ld). Cobre hasGlobal().
        String src = "int print_arg;\nint x;\nvoid main() { x = 7; print_arg = x; print(); }\n";
        requireTools(KofCTarget.RISCV64);
        requireTools(KofCTarget.AARCH64);
        assertEquals("7", run(KofCTarget.X86_64, tmp.resolve("x86"), src));
        assertEquals("7", run(KofCTarget.RISCV64, tmp.resolve("rv"), src));
        assertEquals("7", run(KofCTarget.AARCH64, tmp.resolve("arm"), src));
    }

    @Test
    void targetParsesTheDocumentedAliases() {
        assertEquals(KofCTarget.X86_64, KofCTarget.parse("x86_64"));
        assertEquals(KofCTarget.X86_64, KofCTarget.parse("amd64"));
        assertEquals(KofCTarget.X86_64, KofCTarget.parse("native"));
        assertEquals(KofCTarget.RISCV64, KofCTarget.parse("riscv"));
        assertEquals(KofCTarget.AARCH64, KofCTarget.parse("arm64"));
        assertThrows(IllegalArgumentException.class, () -> KofCTarget.parse("sparc"));
    }
}
