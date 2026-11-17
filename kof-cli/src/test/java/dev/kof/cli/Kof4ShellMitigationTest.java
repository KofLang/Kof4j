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
}
