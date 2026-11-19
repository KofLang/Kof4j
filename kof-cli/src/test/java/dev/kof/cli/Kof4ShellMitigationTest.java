package dev.kof.cli;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * kof4shell (CVE-2026-114938) — the mitigation: EVERY child JVM the CLI
 * launches carries {@code -XX:-UseBASE64Intrinsics}. The CVE is a heap
 * overflow in HotSpot's AVX-512 Base64 decode intrinsic (Intel Ice Lake+ /
 * AMD Zen 4+, JDK 17.0.6+/21/25, padded inputs) turning into unauthenticated
 * RCE (CVSS 9.2); the generated KofRuntime uses {@code java.util.Base64} in
 * 87 call sites (crypto/web/tls/acme/config), several decoding network
 * input. The flag rides both {@link KofStdio} flag helpers (the single
 * chokepoint for run/serve/test/workflow/makealive/bench/profile/compare)
 * plus the two launch paths that bypass them (the JS-driver re-exec and the
 * debug session). {@code KOF_B64_INTRINSICS=1} opts out for a fixed JDK.
 *
 * <p>RED-first: before the fix both helpers returned only the encoding
 * flags — the asserted flag was absent and the tests failed.</p>
 */
class Kof4ShellMitigationTest {

    @Test
    void inheritedFlagsCarryTheDisabledIntrinsic() {
        assertTrue(KofStdio.inheritedJvmFlags().contains(KofStdio.BASE64_INTRINSIC_FLAG),
                "the inherited flags (kof run/kof serve child) must disable the"
                        + " Base64 intrinsic: " + KofStdio.inheritedJvmFlags());
    }

    @Test
    void capturedFlagsCarryTheDisabledIntrinsic() {
        assertTrue(KofStdio.capturedJvmFlags().contains(KofStdio.BASE64_INTRINSIC_FLAG),
                "the captured flags (kof test/compare/bench child) must disable the"
                        + " Base64 intrinsic: " + KofStdio.capturedJvmFlags());
    }

    @Test
    void theEncodingFlagsSurviveNextToTheMitigation() {
        List<String> inherited = KofStdio.inheritedJvmFlags();
        assertTrue(inherited.stream().anyMatch(f -> f.startsWith("-Dstdout.encoding=")),
                "the stdout encoding rule stays");
        assertTrue(inherited.stream().anyMatch(f -> f.startsWith("-Dstderr.encoding=")),
                "the stderr encoding rule stays");
        assertEquals(List.of("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                        KofStdio.BASE64_INTRINSIC_FLAG),
                KofStdio.capturedJvmFlags(),
                "the captured list keeps its shape with the flag appended");
    }

    @Test
    void optOutEnvVarReenablesTheIntrinsic() {
        // direct read of the same gate the launch sites use: the env var is
        // read live, so the opt-out is an operator action, not a build-time
        // constant (verified by the two launch-site tests above on a default
        // environment).
        String v = System.getenv("KOF_B64_INTRINSICS");
        boolean expectDisabled = !"1".equals(v);
        assertEquals(expectDisabled, KofStdio.base64IntrinsicDisabledByDefault(),
                "the gate mirrors the env var (KOF_B64_INTRINSICS=" + v + ")");
    }

    // ── #1173 (19/11, maintainer decision): the VERSION GATE ────────────────
    // O default desarma o intrínseco SÓ abaixo do build corrigido da linha;
    // em build corrigido (ou linha posterior) o Kof não paga o custo.

    @Test
    void fixedBuildsAreRecognisedPerLine() {
        assertTrue(KofStdio.jdkHasBase64Fix("17.0.21.1"), "17.0.21.1 é o build corrigido da linha 17");
        assertTrue(KofStdio.jdkHasBase64Fix("17.0.22"), "acima do corrigido");
        assertTrue(KofStdio.jdkHasBase64Fix("21.0.13.1"));
        assertTrue(KofStdio.jdkHasBase64Fix("25.0.5.1"));
        assertTrue(KofStdio.jdkHasBase64Fix("25.0.6+9"), "sufixo +build é ignorado");
        assertTrue(KofStdio.jdkHasBase64Fix("27.0.1.1"));
        assertTrue(KofStdio.jdkHasBase64Fix("28.0.1"), "linha acima da tabela é presumida corrigida");
    }

    @Test
    void vulnerableBuildsAreDetected() {
        assertFalse(KofStdio.jdkHasBase64Fix("17.0.21"), "17.0.21 < 17.0.21.1");
        assertFalse(KofStdio.jdkHasBase64Fix("21.0.13"), "21.0.13 < 21.0.13.1");
        assertFalse(KofStdio.jdkHasBase64Fix("25.0.4.1"), "o JDK DESTE host ainda é vulnerável");
        assertFalse(KofStdio.jdkHasBase64Fix("17.0.6"), "o build onde o CVE foi introduzido");
        assertFalse(KofStdio.jdkHasBase64Fix("11.0.20"), "linha fora da tabela e abaixo: conservador");
        assertFalse(KofStdio.jdkHasBase64Fix("1.8.0_402"), "java.version legado");
        assertFalse(KofStdio.jdkHasBase64Fix(""), "vazio → conservador");
        assertFalse(KofStdio.jdkHasBase64Fix(null), "nulo → conservador");
        assertFalse(KofStdio.jdkHasBase64Fix("garbage"), "parse falho → conservador");
    }

    @Test
    void runningJdkOnThisTreeStaysProtected() {
        // O host de referência (Zen 2, JDK 25.0.4.1) é VULNERÁVEL: a flag tem
        // de estar presente nos dois helpers (o gate NÃO pode relaxar aqui).
        String v = System.getProperty("java.version", "");
        if (KofStdio.jdkHasBase64Fix(v)) return;   // num JDK corrigido o gate relaxa por desenho
        assertTrue(KofStdio.inheritedJvmFlags().contains(KofStdio.BASE64_INTRINSIC_FLAG), v);
        assertTrue(KofStdio.capturedJvmFlags().contains(KofStdio.BASE64_INTRINSIC_FLAG), v);
    }
}
