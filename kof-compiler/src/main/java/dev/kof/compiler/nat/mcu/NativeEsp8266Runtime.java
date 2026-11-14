package dev.kof.compiler.nat.mcu;

/**
 * kof-esp8266 gate 3 (H1+H2): the runtime asm of the GPIO + delay HAL —
 * extracted from the emitter so it stays under the 500-line gate (the
 * `NativeMcuTimeRiscv32.runtimeAsm()` precedent). The ESP8266 register map:
 * GPIO_OUT 0x60000300, GPIO_OUT_W1TS 0x60000304, GPIO_OUT_W1TC 0x60000308,
 * GPIO_ENABLE 0x6000030C, GPIO_IN 0x60000318; the delay on the 80 MHz cycle
 * counter (the ccount special register 234), a PER-MILLISECOND loop (the L106
 * has no mul — measured).
 */
final class NativeEsp8266Runtime {

    private NativeEsp8266Runtime() {}

    static String all() {
        StringBuilder sb = new StringBuilder();
        sb.append("    .align 4\n");
        sb.append("kof_gpio_enable:\n");
        sb.append("    .literal_position\n");
        sb.append("    movi a4, 1\n");
        sb.append("    ssr a3\n");
        sb.append("    sll a4, a4\n");
        sb.append("    movi a5, 0x6000030C\n");
        sb.append("    l32i a6, a5, 0\n");
        sb.append("    or a6, a6, a4\n");
        sb.append("    s32i a6, a5, 0\n");
        sb.append("    movi a2, 0\n");
        sb.append("    ret\n\n");
        sb.append("    .align 4\n");
        sb.append("kof_gpio_write:\n");
        sb.append("    .literal_position\n");
        sb.append("    movi a5, 1\n");
        sb.append("    ssr a3\n");
        sb.append("    sll a5, a5\n");
        sb.append("    beqz a4, .Lesp_gpio_low\n");
        sb.append("    movi a6, 0x60000304\n");
        sb.append("    s32i a5, a6, 0\n");
        sb.append("    movi a2, 0\n");
        sb.append("    ret\n");
        sb.append(".Lesp_gpio_low:\n");
        sb.append("    movi a6, 0x60000308\n");
        sb.append("    s32i a5, a6, 0\n");
        sb.append("    movi a2, 0\n");
        sb.append("    ret\n\n");
        sb.append("    .align 4\n");
        sb.append("kof_gpio_read:\n");
        sb.append("    .literal_position\n");
        sb.append("    movi a5, 0x60000318\n");
        sb.append("    l32i a6, a5, 0\n");
        sb.append("    ssr a3\n");
        sb.append("    srl a2, a6\n");
        sb.append("    movi a5, 1\n");
        sb.append("    and a2, a2, a5\n");
        sb.append("    ret\n\n");
        sb.append("    .align 4\n");
        sb.append("kof_delay_ms:\n");
        sb.append("    .literal_position\n");
        sb.append("    movi a4, 80000\n");
        sb.append(".Lesp_ms:\n");
        sb.append("    rsr a5, 234\n");
        sb.append("    add a6, a5, a4\n");
        sb.append(".Lesp_tick:\n");
        sb.append("    rsr a5, 234\n");
        sb.append("    bltu a5, a6, .Lesp_tick\n");
        sb.append("    addi a3, a3, -1\n");
        sb.append("    bnez a3, .Lesp_ms\n");
        sb.append("    movi a2, 0\n");
        sb.append("    ret\n\n");
        return sb.toString();
    }
}
