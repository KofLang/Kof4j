package dev.kof.compiler;

import java.io.File;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assumptions;

/**
 * Harness único para os guards honestos de toolchain nativa (R6) da família
 * `assumeToolchain` da suíte (test-architecture Phase 5). Antes cada classe de
 * teste declarava a sua própria cópia do helper — 26 declarações com o mesmo
 * nome, todas fazendo a mesma checagem "o binário existe?" com variações de
 * mensagem. Aqui a checagem vive uma única vez; cada teste só nomeia o guard
 * que precisa ({@link #assumeNativeRiscv64()}, {@link #assumeNativeAarch64()},
 * ...).
 *
 * <p>O guard é prefix-aware (§591): quando `KOF_CROSS_PREFIX` aponta para um
 * cross rootless, `<prefix>/<tool>` vence; senão o `command -v` no PATH. Um
 * runtime ausente nunca vira PASS silencioso — {@code assumeTrue} faz o JUnit
 * pular o teste com a mensagem.
 */
public interface NativeToolchainAssumptions {

    String[] RISCV64_TOOLS = {"riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"};
    String[] AARCH64_TOOLS = {"aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"};
    String[] X86_64_TOOLS = {"as", "ld"};
    String[] MCU_RISCV_TOOLS = {"riscv64-linux-gnu-as"};
    String[] MCU_ARM_TOOLS = {"arm-none-eabi-as"};

    /** `true` se todos os comandos existem (prefix-aware §591, senão PATH). */
    static boolean hasTool(String... cmds) {
        for (String c : cmds) {
            String prefix = System.getenv("KOF_CROSS_PREFIX");
            if (prefix != null && !prefix.isBlank() && new File(prefix, c).canExecute()) {
                continue;
            }
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c)
                        .redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                if (p.waitFor() != 0 || out.isEmpty()) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    /** Pula com uma mensagem nomeada quando a toolchain declarada falta. */
    default void assumeToolchain(String... tools) {
        for (String c : tools) {
            Assumptions.assumeTrue(hasTool(c), "toolchain ausente: " + c);
        }
    }

    /** riscv64-linux-gnu-as/-ld + qemu-riscv64 (NATIVE002). */
    default void assumeNativeRiscv64() {
        Assumptions.assumeTrue(hasTool(RISCV64_TOOLS),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
    }

    /** riscv64 + qemu + o sysroot libc cross (KOF_CROSS_SYSROOT / /tmp/opencode/x). */
    default void assumeNativeRiscv64WithSysroot() {
        assumeNativeRiscv64();
        Assumptions.assumeTrue(crossSysrootPresent("riscv64"),
                "libc cross ausente (KOF_CROSS_SYSROOT / /tmp/opencode/x) — pulando");
    }

    /** aarch64-linux-gnu-as/-ld + qemu-aarch64 (NATIVE002). */
    default void assumeNativeAarch64() {
        Assumptions.assumeTrue(hasTool(AARCH64_TOOLS),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
    }

    /** binutils do host x86-64 (`as`, `ld`). */
    default void assumeNativeX86_64() {
        Assumptions.assumeTrue(hasTool(X86_64_TOOLS),
                "binutils x86-64 ausente — pulando");
    }

    /** assembler do MCU riscv64 (`riscv64-linux-gnu-as`). */
    default void assumeMcuRiscvAsm() {
        Assumptions.assumeTrue(hasTool(MCU_RISCV_TOOLS),
                "binutils riscv64 ausente (riscv64-linux-gnu-as)");
    }

    /** assembler do MCU arm (`arm-none-eabi-as`). */
    default void assumeMcuArmAsm() {
        Assumptions.assumeTrue(hasTool(MCU_ARM_TOOLS),
                "binutils arm-none-eabi ausente");
    }

    /** Espelha `NativeCrossLink.sysrootFor` (package-private) sem tocá-lo:
     *  `KOF_CROSS_SYSROOT`, o loader do sistema, ou o staging `/tmp/opencode/x`. */
    static boolean crossSysrootPresent(String arch) {
        String env = System.getenv("KOF_CROSS_SYSROOT");
        if (env != null && !env.isBlank()) return true;
        String loader = switch (arch) {
            case "riscv64" -> "ld-linux-riscv64-lp64d.so.1";
            case "aarch64" -> "ld-linux-aarch64.so.1";
            default -> throw new IllegalArgumentException("arch cross: " + arch);
        };
        return new File("/usr/" + arch + "-linux-gnu/lib/" + loader).exists()
                || new File("/tmp/opencode/x/usr/" + arch + "-linux-gnu/lib/" + loader).exists();
    }
}
