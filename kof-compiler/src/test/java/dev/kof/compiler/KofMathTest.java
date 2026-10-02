package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * STDLIB S1 — kof.math (Int-only) nos 3 targets compiláveis (JVM/Native/JS).
 * O interpretador (Target.SCRIPT) herda via reflexão no KofRuntime gerado
 * (mesmo source de JVMStringMathRuntime) — a matriz cobre a paridade 4-target.
 */
class KofMathTest extends KofMathSupport {

    @Test
    void mathJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
            main() {
                println(math.clamp(15, 0, 10))
                println(math.clamp(-3, 0, 10))
                println(math.abs(-7))
                println(math.sign(-4))
                println(math.min(3, 8))
                println(math.max(3, 8))
                println(math.isEven(4))
                println(math.isOdd(4))
                println(math.isPositive(1))
                println(math.isNegative(0))
                println(math.isZero(0))
            }
            """, "10\n0\n7\n-1\n3\n8\ntrue\nfalse\ntrue\nfalse\ntrue");
    }

    @Test
    void mathNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
            main() {
                assert(math.clamp(15, 0, 10) == 10)
                assert(math.clamp(-3, 0, 10) == 0)
                assert(math.abs(-7) == 7)
                assert(math.sign(-4) == -1)
                assert(math.min(3, 8) == 3)
                assert(math.max(3, 8) == 8)
                assert(math.isEven(4))
                assert(!math.isEven(3))
                assert(math.isOdd(3))
                assert(math.isPositive(1))
                assert(!math.isNegative(0))
                assert(math.isZero(0))
                println("ok")
            }
            """, "ok");
    }

    @Test
    void mathJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
            main() {
                println(math.clamp(15, 0, 10))
                println(math.clamp(-3, 0, 10))
                println(math.abs(-7))
                println(math.sign(-4))
                println(math.min(3, 8))
                println(math.max(3, 8))
                println(math.isEven(4))
                println(math.isOdd(4))
                println(math.isPositive(1))
                println(math.isNegative(0))
                println(math.isZero(0))
            }
            """, "10\n0\n7\n-1\n3\n8\ntrue\nfalse\ntrue\nfalse\ntrue");
    }

    // S1b: sqrt = PRIMEIRO Double em kof.math. Comparações Bool (nunca
    // println de double cru — bug 44 no Native). NaN em <0 = paridade
    // Math.sqrt (IEEE, medido nos 4 targets: NaN==NaN é false).
    private static final String SQRT_SRC = """
        main() {
            println(math.sqrt(9.0) == 3.0)
            println(math.sqrt(2.0) == 1.4142135623730951)
            println(math.sqrt(0.25) == 0.5)
            println(math.sqrt(1e30) == 1000000000000000.0)
            println(math.sqrt(1e300) == 1e150)
            println(math.sqrt(0.0) == 0.0)
            println(math.sqrt(-1.0) == -1.0)
            println(math.sqrt(-1.0) != math.sqrt(-1.0))
        }
        """;

    private static final String SQRT_OUT =
            "true\ntrue\ntrue\ntrue\ntrue\ntrue\nfalse\ntrue";

    @Test
    void sqrtJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, SQRT_SRC, SQRT_OUT);
    }

    @Test
    void sqrtNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, SQRT_SRC, SQRT_OUT);
    }

    @Test
    void sqrtJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, SQRT_SRC, SQRT_OUT);
    }

    @Test
    void sqrtCrossArch(@TempDir Path tmp) throws Exception {
        // MATH001 FECHADO 11/09: kof_math_sqrt na fatia riscv B32
        // (fsqrt.d) + aarch (fsqrtd no tradutor). Golden BYTE-IDÊNTICO
        // aos 3 targets (SQRT_OUT), executado sob qemu.
        forCrossArch(tmp, SQRT_SRC, SQRT_OUT);
    }

    // S1b.1: escalares Double puros (lerp/percentage/isInteger/isDecimal) —
    // paridade byte-idêntica JVM/Script/JS/x86 (harness SSE2 isolado 18/18
    // antes desta classe). Golden travado no oracle JVM medido (P.java/O.java
    // da sessão), nunca de memória: 2.675-style não entra (roundTo fica no
    // degrau seguinte). NaN via percentage(0,0) — IEEE, != != em todos.
    private static final String DBL_SRC = """
        main() {
            println(math.lerp(0.0, 10.0, 0.5) == 5.0)
            println(math.lerp(0.0, 10.0, 0.25) == 2.5)
            println(math.lerp(-4.0, 4.0, 0.75) == 2.0)
            println(math.lerp(2.0, 8.0, 1.5) == 11.0)
            println(math.percentage(3.0, 4.0) == 75.0)
            println(math.percentage(1.0, 3.0) == 33.33333333333333)
            println(math.percentage(-2.0, 8.0) == -25.0)
            println(math.percentage(0.0, 5.0) == 0.0)
            println(math.percentage(0.0, 0.0) != math.percentage(0.0, 0.0))
            println(math.isInteger(4.0))
            println(math.isInteger(4.5) == false)
            println(math.isInteger(-3.0))
            println(math.isInteger(0.0))
            println(math.isInteger(1e20))
            println(math.isDecimal(4.5))
            println(math.isDecimal(4.0) == false)
            println(math.isInteger(1.0 / 0.0) == false)
            println(math.isDecimal(1.0 / 0.0))
        }
        """;

    private static final String DBL_OUT = String.join("\n",
            "true", "true", "true", "true", "true", "true", "true", "true", "true",
            "true", "true", "true", "true", "true", "true", "true", "true", "true");

    @Test
    void doubleOpsJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, DBL_SRC, DBL_OUT);
    }

    @Test
    void doubleOpsNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, DBL_SRC, DBL_OUT);
    }

    @Test
    void doubleOpsJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, DBL_SRC, DBL_OUT);
    }

    @Test
    void doubleOpsCrossArch(@TempDir Path tmp) throws Exception {
        // MATH001 FECHADO 11/09 (lerp/percentage/isInteger/isDecimal — B32).
        forCrossArch(tmp, DBL_SRC, DBL_OUT);
    }

    // S1b.2: pow(base,exp) — primeiro caso libm no native (pow@PLT + -lm)
    // e Math.pow/** nos outros. Golden = oracle JVM medido, comparações Bool
    // (bug 44: nunca println de double cru no Native). Cobre finitos exatos,
    // exp negativo, exp fracionário (raiz) e a borda IEEE pow(0.0,0.0)==1.0.
    private static final String POW_SRC = """
        main() {
            println(math.pow(2.0, 10.0) == 1024.0)
            println(math.pow(9.0, 0.5) == 3.0)
            println(math.pow(2.0, -1.0) == 0.5)
            println(math.pow(10.0, 2.0) == 100.0)
            println(math.pow(2.0, 0.0) == 1.0)
            println(math.pow(0.0, 0.0) == 1.0)
            println(math.pow(2.0, 0.5) == math.sqrt(2.0))
            println(math.pow(3.0, 3.0) == 27.0)
            println(math.pow(-2.0, 3.0) == -8.0)
            println(math.pow(10.0, -2.0) == 0.01)
            println(math.pow(1.0, 0.0) == 1.0)
            println(math.pow(-1.0, 0.5) != math.pow(-1.0, 0.5))
            println(math.pow(2.0, 1024.0) == math.pow(2.0, 1024.0))
            println(math.pow(-8.0, 0.3333333333333333) != math.pow(-8.0, 0.3333333333333333))
        }
        """;

    private static final String POW_OUT = String.join("\n",
            "true", "true", "true", "true", "true", "true", "true", "true", "true",
            "true", "true", "true", "true", "true");

    // S13a (plan-stdlib-expansion §2, P0): math.parseInt/parseLong/parseDouble
    // = fachada de namespace sobre as runtime fns EXISTENTES kof_string_to_*
    // (zero runtime novo nos 4 alvos; regra 2). Contrato = JDK com trim (idem
    // `.toInt()`): inválido/overflow LANÇA. Golden byte-idêntico JVM/Script/JS/
    // x86 (Double via == Bool — bug 44: nunca println de double cru no Native;
    // Long 2^63-1 prova 64-bit real pós-§81/BigInt no JS). Erro esperado via
    // try/catch (Q3: borda) + bordas Int (MIN ok, MAX+1 lança) e Long.
    private static final String PARSE_SRC = """
        main() {
            println(math.parseInt("42"))
            println(math.parseInt(" -7 "))
            println(math.parseInt("+13"))
            println(math.parseInt("0"))
            println(math.parseInt("-2147483648"))
            println(math.parseLong("9007199254740993"))
            println(math.parseLong("-9223372036854775807"))
            println(math.parseDouble("2.5") == 2.5)
            println(math.parseDouble("  -0.25 ") == -0.25)
            println(math.parseDouble("1e2") == 100.0)
            try { println(math.parseInt("abc")); println("S1") } catch (String e) { println("T1") }
            try { println(math.parseInt("12a34")); println("S2") } catch (String e) { println("T2") }
            try { println(math.parseInt("2147483648")); println("S3") } catch (String e) { println("T3") }
            try { println(math.parseInt("")); println("S4") } catch (String e) { println("T4") }
            try { println(math.parseLong("9223372036854775808")); println("S5") } catch (String e) { println("T5") }
        }
        """;

    private static final String PARSE_OUT = String.join("\n",
            "42", "-7", "13", "0", "-2147483648",
            "9007199254740993", "-9223372036854775807",
            "true", "true", "true",
            "T1", "T2", "T3", "T4", "T5");

    @Test
    void parseJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, PARSE_SRC, PARSE_OUT);
    }

    @Test
    void parseNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, PARSE_SRC, PARSE_OUT);
    }

    @Test
    void parseJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, PARSE_SRC, PARSE_OUT);
    }

    @Test
    void parseCrossArch(@TempDir Path tmp) throws Exception {
        // kof_string_to_int/long = B30 (bug 79, contrato JDK); kof_string_to_double
        // = B31 (bug 82). Golden BYTE-IDÊNTICO sob qemu-riscv64 + qemu-aarch64.
        forCrossArch(tmp, PARSE_SRC, PARSE_OUT);
    }

    @Test
    void parseTypeGuardRefused(@TempDir Path tmp) throws Exception {
        // Q3 (erro esperado) + SEM025 (R6): Int NÃO alarga para String em
        // silêncio — math.parseInt(42) é erro de COMPILAÇÃO (guia no check,
        // não exceção em runtime; por isso o golden acima não tem T6).
        Path file = tmp.resolve("Guard-" + System.nanoTime() + ".kf");
        Files.writeString(file, "main() { println(math.parseInt(42)) }");
        Path outDir = tmp.resolve("guard-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertFalse(result.success(), "math.parseInt(42) deve ser rejeitado no typer (SEM025)");
    }

    // S13b (plan-stdlib-expansion §2, P0): math.parseInt/parseLong/
    // parseDouble**OrDefault** — briefing §43 ("falha de parse = OrNull/
    // OrDefault"). Mesmo contrato do parse (JDK + trim); falha DEVOLVE o
    // default (nunca lança) — backends: JVM try/catch (JvmStringCoreRuntime),
    // JS wrapper (JsRuntimeUiStdlib), x86 wrapper c/ handler no exc_chain
    // (RuntimeStringParseOrDefault), riscv B41 (aarch herda via tradutor).
    // Literal Int default em parseLong prova o widening I2L do KofStd
    // (crash COMPUTE_FRAMES sem ele — mesma unidade). Linhas `""`/"   " do
    // Double INCLUÍDAS pós-§175 (vazio lançava 0.0 no Native — paridade do
    // parse base fechada; antes estavam fora do golden). Int/Long vazios
    // devolvem o default (o parse base sempre lançou neles).
    private static final String PARSEORD_SRC = """
        main() {
            println(math.parseIntOrDefault("42", 0))
            println(math.parseIntOrDefault("abc", -1))
            println(math.parseIntOrDefault("", 7))
            println(math.parseIntOrDefault("  15  ", 0))
            println(math.parseIntOrDefault("99999999999999", 3))
            println(math.parseLongOrDefault("9007199254740993", 0))
            println(math.parseLongOrDefault("x", -5))
            println(math.parseLongOrDefault("9223372036854775808", 8))
            println(math.parseDoubleOrDefault("2.5", 0.0) == 2.5)
            println(math.parseDoubleOrDefault("nope", -0.5) == -0.5)
            println(math.parseDoubleOrDefault("1e2", 0.0) == 100.0)
            println(math.parseDoubleOrDefault("", 1.5) == 1.5)
            println(math.parseDoubleOrDefault("   ", -0.25) == -0.25)
        }
        """;

    private static final String PARSEORD_OUT = String.join("\n",
            "42", "-1", "7", "15", "3",
            "9007199254740993", "-5", "8",
            "true", "true", "true", "true", "true");

    @Test
    void parseOrDefaultJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, PARSEORD_SRC, PARSEORD_OUT);
    }

    @Test
    void parseOrDefaultNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, PARSEORD_SRC, PARSEORD_OUT);
    }

    @Test
    void parseOrDefaultJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, PARSEORD_SRC, PARSEORD_OUT);
    }

    @Test
    void parseOrDefaultCrossArch(@TempDir Path tmp) throws Exception {
        // riscv = B41 (wrapper c/ handler no exc_chain sobre B30/B31); aarch
        // herda linha-a-linha no tradutor. Golden BYTE-IDÊNTICO sob qemu.
        forCrossArch(tmp, PARSEORD_SRC, PARSEORD_OUT);
    }

    @Test
    void powJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, POW_SRC, POW_OUT);
    }

    @Test
    void powNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, POW_SRC, POW_OUT);
    }

    @Test
    void powJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, POW_SRC, POW_OUT);
    }

    @Test
    void powCrossArch(@TempDir Path tmp) throws Exception {
        // row 10 (27/09, D-DECISION-BATCH-2709B #3): riscv/aarch LIGAM libm POR
        // USO — shim kof_math_pow (peça própria) chama pow@PLT; `-lm` entra por
        // NativeCrossLink.needsLibm quando o texto podado contém `call pow`.
        // Golden byte-idêntico ao JVM (POW_OUT, só Bool) sob qemu. Substitui a
        // antiga recusa MATH001 (contrato revisto pela mantenedora).
        forCrossArch(tmp, POW_SRC, POW_OUT);
    }

    // S1b.3 (DECISIONS §3): math.roundTo(value: Double, decimals: Int) ->
    // Double. Half-away-from-zero por escala decimal determinística, SEM libm
    // (p=10^|d| por multiplicação repetida → byte-idêntico 5 alvos). Golden
    // só Bool (bug 44: nunca println de double cru no Native). Contrato
    // ARITMÉTICO (não decimal-string): 2.675 → 2.68 (o double 2.675*100
    // arredonda a 267.5, half-away → 268), 1.005 → 1.0 (100.4999…). decimals
    // negativo arredonda p/ dezenas/centenas; guard de overflow (1e307,5 →
    // devolve v) e de |d| extremo (1e10,-308 → 0.0).
    private static final String ROUND_SRC = """
        main() {
            println(math.roundTo(2.5, 0) == 3.0)
            println(math.roundTo(-2.5, 0) == -3.0)
            println(math.roundTo(2.4, 0) == 2.0)
            println(math.roundTo(2.6, 0) == 3.0)
            println(math.roundTo(-2.4, 0) == -2.0)
            println(math.roundTo(0.49999999999999994, 0) == 0.0)
            println(math.roundTo(1.5, 0) == 2.0)
            println(math.roundTo(-1.5, 0) == -2.0)
            println(math.roundTo(3.14159, 2) == 3.14)
            println(math.roundTo(2.675, 2) == 2.68)
            println(math.roundTo(1.005, 2) == 1.0)
            println(math.roundTo(1234.0, -2) == 1200.0)
            println(math.roundTo(1250.0, -2) == 1300.0)
            println(math.roundTo(-1250.0, -2) == -1300.0)
            println(math.roundTo(2.5, -1) == 0.0)
            println(math.roundTo(0.0, 5) == 0.0)
            println(math.roundTo(1.0, 0) == 1.0)
            println(math.roundTo(1e307, 5) == 1e307)
            println(math.roundTo(1e10, -308) == 0.0)
            println(math.roundTo(1.0 / 0.0, 2) == 1.0 / 0.0)
            println(math.roundTo(0.0 / 0.0, 2) != math.roundTo(0.0 / 0.0, 2))
            println(math.roundTo(-0.4, 0) == 0.0)
        }
        """;

    private static final String ROUND_OUT = String.join("\n",
            "true", "true", "true", "true", "true", "true", "true", "true",
            "true", "true", "true", "true", "true", "true", "true", "true",
            "true", "true", "true", "true", "true", "true");

    @Test
    void roundToJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, ROUND_SRC, ROUND_OUT);
    }

    @Test
    void roundToNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, ROUND_SRC, ROUND_OUT);
    }

    @Test
    void roundToJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, ROUND_SRC, ROUND_OUT);
    }

    @Test
    void roundToCrossArch(@TempDir Path tmp) throws Exception {
        // riscv = fatia B32 (fmul.d/fdiv.d/fcvt.l.d/fcvt.d.l/feq/flt inline);
        // aarch herda via tradutor. Golden BYTE-IDÊNTICO sob qemu.
        forCrossArch(tmp, ROUND_SRC, ROUND_OUT);
    }

    @Test
    void roundToTypeGuardRefused(@TempDir Path tmp) throws Exception {
        // SEM025 (R6): decimals é Int — Double NÃO alarga em silêncio.
        Path file = tmp.resolve("Guard-" + System.nanoTime() + ".kf");
        Files.writeString(file, "main() { println(math.roundTo(2.0, 2.0)) }");
        Path outDir = tmp.resolve("guard-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertFalse(result.success(), "math.roundTo(2.0, 2.0) deve ser rejeitado no typer (SEM025)");
    }

    private static final String TRIG_SRC = """
        main() {
            println(math.sin(0.0) == 0.0)
            println(math.cos(0.0) == 1.0)
            println(math.tan(0.0) == 0.0)
            println(math.asin(0.0) == 0.0)
            println(math.acos(1.0) == 0.0)
            println(math.atan(0.0) == 0.0)
            println(math.atan2(0.0, 1.0) == 0.0)
            println(math.toRadians(180.0) == math.pi())
            println(math.toDegrees(math.pi()) == 180.0)
            println(math.pi() > 3.14 && math.pi() < 3.15)
            println(math.e() > 2.71 && math.e() < 2.72)
            println(math.tau() == 2.0 * math.pi())
        }
        """;

    private static final String TRIG_OUT =
            "true\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue";

    @Test
    void trigJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, TRIG_SRC, TRIG_OUT);
    }

    @Test
    void trigJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, TRIG_SRC, TRIG_OUT);
    }

}
