package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #1139 — the UEFI/PE32+ artifact build is BROKEN BY CONSTRUCTION, and the CI
 * harness bug it causes is NOT environmental.
 *
 * <p>The native UEFI profile links an ELF with {@code ld} and converts it to a
 * PE32+ EFI application with {@code objcopy}. The conversion used
 * {@code --target pei-x86-64}. {@code --target} (alias {@code -F}) sets BOTH the
 * input and the output format, so objcopy tries to READ the ELF as if it were a
 * PE and aborts with "file format not recognized". {@code --output-target}
 * (alias {@code -O}) names only the OUTPUT format: the ELF is read, the PE32+
 * is written.
 *
 * <p>This test pins the FLAG, not the toolchain: it asks the objcopy that is
 * on the host whether it understands a format-only-output switch, and then
 * asserts that the native assembler uses the output-only spelling. A machine
 * whose objcopy predates {@code --output-target} is an ENVIRONMENTAL skip (the
 * toolchain is the platform's, not the code's); a machine that DOES understand
 * it and finds the assembler using the input+output spelling is a real
 * regression — that is the RED-first case.
 *
 * <p>Why it matters: this single flag took down 11 CI faces (7 in
 * {@code NativeUefiE2ETest}, 4 in {@code RingPrivilegeE2ETest}) and made every
 * pull request look broken, which is exactly what #1139 tracked.
 */
class Issue1139ObjcopyOutputTargetTest {

    /** The ELF->PE conversion must name the OUTPUT format only. */
    private static final String UEFI_OBJCOPY_OUTPUT_FLAG = "--output-target";

    /** The spelling that sets input AND output — the bug. */
    private static final String UEFI_OBJCOPY_BUGGY_FLAG = "\"--target\"";

    private static Path nativeAssemblerSource() {
        Path candidate = Path.of("kof-compiler/src/main/java/dev/kof/compiler/nat/NativeAssembler.java");
        if (Files.isRegularFile(candidate)) return candidate;
        return Path.of("..").resolve(candidate).normalize();
    }

    /** True when the host objcopy understands the output-only format switch. */
    private static boolean objcopySupportsOutputTarget() {
        try {
            ProcessBuilder pb = new ProcessBuilder("objcopy", "--output-target", "pei-x86-64", "--help");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.getInputStream().readAllBytes();
            p.waitFor();
            // --help exits 0 on a toolchain that knows the option; a toolchain
            // that does not know it prints usage and exits non-zero.
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void uefiObjcopyNamesTheOutputFormatOnly(@TempDir Path tempDir) throws IOException {
        Path source = nativeAssemblerSource();
        Assumptions.assumeTrue(Files.isRegularFile(source),
                "NativeAssembler.java nao encontrado a partir de " + Path.of("").toAbsolutePath());
        Assumptions.assumeTrue(objcopySupportsOutputTarget(),
                "objcopy do host nao conhece --output-target — toolchain ambiental, nao regressao");

        List<String> lines = Files.readAllLines(source);
        List<String> offenders = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            // The UEFI conversion is the only objcopy call that names a PE
            // format; the BIOS one uses the "binary" output target already.
            if (!line.contains(UEFI_OBJCOPY_BUGGY_FLAG)) continue;
            if (!isUefiObjcopyCall(lines, i)) continue;
            offenders.add((i + 1) + ": " + line.trim());
        }
        assertTrue(offenders.isEmpty(),
                "o objcopy do perfil UEFI usa " + UEFI_OBJCOPY_BUGGY_FLAG
                        + " (define entrada E saida — o objcopy tenta ler o ELF como PE e falha com"
                        + " \"file format not recognized\"); use " + UEFI_OBJCOPY_OUTPUT_FLAG
                        + " (so a saida). Ocorrencia(s): " + offenders);
    }

    /** True when the line at {@code index} belongs to the UEFI PE conversion. */
    private static boolean isUefiObjcopyCall(List<String> lines, int index) {
        // The UEFI block is the one that names "pei-x86-64"; the BIOS block
        // names "binary" and is already correct.
        int start = Math.max(0, index - 6);
        int end = Math.min(lines.size(), index + 6);
        for (int i = start; i < end; i++) {
            if (lines.get(i).contains("pei-x86-64")) return true;
        }
        return false;
    }

    @Test
    void theUefiConversionStillNamesThePeFormat(@TempDir Path tempDir) throws IOException {
        Path source = nativeAssemblerSource();
        Assumptions.assumeTrue(Files.isRegularFile(source),
                "NativeAssembler.java nao encontrado a partir de " + Path.of("").toAbsolutePath());
        String text = Files.readString(source);
        assertTrue(text.contains("pei-x86-64"),
                "o perfil UEFI deve continuar nomeando o formato PE (pei-x86-64)");
        assertTrue(text.contains(UEFI_OBJCOPY_OUTPUT_FLAG),
                "o perfil UEFI deve usar " + UEFI_OBJCOPY_OUTPUT_FLAG + " para o formato de saida");
    }
}
