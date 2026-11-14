package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #1139 — the CI harness bug, part 3: the FFI faces that bind a SYSTEM
 * LIBRARY had no environmental guard at all, so a runner without that library
 * reported a REAL TEST FAILURE — the same red for a broken program and for a
 * missing library. That is what made every pull request look broken.
 *
 * <p>This test pins the guard in two directions:
 * <ul>
 *   <li>the HELPER resolves a present library and reports an absent one as
 *       missing (never a silent pass);</li>
 *   <li>every FFI face that names a system library CALLS the guard before it
 *       runs (so a future edit cannot drop it again — the same blind-spot
 *       shape as the §424 ratchet).</li>
 * </ul>
 */
class Issue1139FfiLibraryGuardTest {

    /** The FFI faces that bind a system library, and the soname each needs. */
    private static final List<String[]> FFI_FACES = List.of(
            new String[]{"LinalgBlasFloatE2ETest", "libopenblas.so.0"},
            new String[]{"LinalgBlasLevel1E2ETest", "libopenblas.so.0"},
            new String[]{"LinalgDdotE2ETest", "libopenblas.so.0"},
            new String[]{"SvgRenderE2ETest", "librsvg-2.so.2"},
            new String[]{"PcapVersionE2ETest", "libpcap.so.1"});

    private static Path testSource(String className) {
        Path candidate = Path.of("kof-compiler/src/test/java/dev/kof/compiler", className + ".java");
        if (Files.isRegularFile(candidate)) return candidate;
        return Path.of("..").resolve(candidate).normalize();
    }

    @Test
    void theHelperTellsPresentFromAbsent() {
        // A soname that cannot exist is always reported as missing — the
        // helper never claims a library it did not find.
        List<String> missing = NativeLibraryAvailability.missing("libdefinitely-not-here.so.99");
        assertEquals(List.of("libdefinitely-not-here.so.99"), missing,
                "uma soname inexistente tem de ser reportada como ausente");

        // And the skip message names the face and the library (a NAMED skip,
        // never a silent pass).
        String message = NativeLibraryAvailability.skipMessage("SomeFace", missing);
        assertTrue(message.contains("SomeFace"), "o skip nomeia a face: " + message);
        assertTrue(message.contains("libdefinitely-not-here.so.99"),
                "o skip nomeia a biblioteca: " + message);
        assertTrue(message.contains("AMBIENTE"),
                "o skip distingue ambiente de regressao: " + message);
    }

    @Test
    void aPresentLibraryIsNotReportedMissing() {
        // The helper must find a library the loader really has — otherwise it
        // would skip every FFI face and hide a real regression.
        List<String> missing = NativeLibraryAvailability.missing("libc.so.6", "libm.so.6");
        assertTrue(missing.isEmpty(),
                "libc/libm do host deveriam ser resolvidas (o helper nao pode pular"
                        + " tudo e esconder regressao): " + missing);
    }

    @Test
    void everyFfiFaceCallsTheEnvironmentalGuard() throws java.io.IOException {
        for (String[] face : FFI_FACES) {
            String className = face[0];
            String soname = face[1];
            Path source = testSource(className);
            assertTrue(Files.isRegularFile(source),
                    "fonte de teste nao encontrada: " + source);
            String text = Files.readString(source);
            assertTrue(text.contains(soname),
                    className + " deve nomear a biblioteca do sistema (" + soname + ")");
            assertTrue(text.contains("NativeLibraryAvailability.assumePresent("),
                    className + " deve chamar a guarda ambiental antes de rodar a face"
                            + " (sem ela, um runner sem " + soname + " reporta FALHA DE TESTE"
                            + " em vez de um skip nomeado)");
            assertTrue(text.contains("NativeLibraryAvailability.assumePresent("),
                    className + " deve usar o helper compartilhado de disponibilidade");
        }
    }

    @Test
    void theFacesThatBindLibrariesAreTheOnesGuarded() throws java.io.IOException {
        // A face FFI sem guarda e o furo; esta lista e a cobertura medida.
        for (String[] face : FFI_FACES) {
            assertFalse(NativeLibraryAvailability.missing(face[1]).isEmpty()
                            && !Files.readString(testSource(face[0])).contains("NativeLibraryAvailability.assumePresent("),
                    "furo: " + face[0] + " liga " + face[1] + " e nao tem guarda ambiental");
        }
    }
}
