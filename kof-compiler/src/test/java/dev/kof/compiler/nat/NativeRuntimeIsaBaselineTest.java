package dev.kof.compiler.nat;

import dev.kof.compiler.NativeRuntime;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #795: o asm do runtime x86-64 carregava UMA instrucao AVX/VEX — o
 * {@code vcvtsi2sd} em {@code kof_string_to_float} — enquanto o baseline que o
 * repo promete para x86-64 e SSE2 (AGENTS honest-cross-target: x86-64 baseline
 * = SSE2). Uma CPU pre-AVX (Core 2, ou qualquer x86-64 anterior a 2011) levanta
 * #UD nela -> SIGILL (exit 132) em todo parse numerico: {@code Int.parse}/
 * {@code Double.parse}, e os caminhos float de JSON/TOML/YAML. As maquinas AVX
 * das lanes de referencia executam a instrucao normalmente — por isso a suite
 * ficou verde la.
 *
 * <p>Este teste assegura o TEXTO EMITIDO direto: e deterministico e sem
 * toolchain (sem as/ld/qemu) e independente de a CPU que roda o teste ter AVX
 * (esta tem). RED-first: com o {@code vcvtsi2sd} presente o primeiro teste
 * falha nomeando o mnemônico e a linha.
 */
class NativeRuntimeIsaBaselineTest {

    /** Linha de instrucao: indentada, mnemônico primeiro. Diretivas ('.'), labels (coluna 0) e comentarios ('#') nao casam. */
    private static final Pattern INSTRUCTION = Pattern.compile("^(\\s+)([a-z][a-z0-9]*)\\b", Pattern.MULTILINE);

    /** Mnemônicos VEX/EVEX sao {@code v} + letra; nenhum mnemônico SSE comeca com 'v'. */
    private static final Pattern AVX_MNEMONIC = Pattern.compile("^v[a-z]");

    @Test
    void runtimeAssemblyCarriesNoAvxEncodedInstruction() {
        String asm = NativeRuntime.generateRuntimeAssembly();
        List<String> offenders = new ArrayList<>();
        Matcher m = INSTRUCTION.matcher(asm);
        while (m.find()) {
            if (AVX_MNEMONIC.matcher(m.group(2)).find()) {
                offenders.add(m.group(2) + " (linha " + lineOf(asm, m.start()) + ")");
            }
        }
        assertTrue(offenders.isEmpty(),
                "#795: instrucao AVX/VEX no asm do runtime x86-64 (baseline = SSE2;"
                        + " CPU pre-AVX levanta #UD -> SIGILL): " + offenders);
    }

    @Test
    void runtimeNumericConversionsStayOnTheSse2Forms() {
        String asm = NativeRuntime.generateRuntimeAssembly();
        assertTrue(asm.contains("cvtsi2sd %r13, %xmm0"),
                "#795: a conversao mantissa-int64 -> double tem de ser o `cvtsi2sd` SSE2 (forma de 64 bits)");
        assertTrue(asm.contains("cvtsd2ss %xmm0, %xmm0"),
                "kof_string_to_float: a conversao double -> float e o `cvtsd2ss` (SSE2)");
    }

    private static int lineOf(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}