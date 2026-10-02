package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.kof.compiler.nat.NativeProfile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * B-3 (PLAN-BAREMETAL-BOOT) — perfil BIOS (x86_64, boot path legado): o
 * compilador emite um setor de boot de 512 bytes cujo entry {@code _start} roda
 * em modo real 16-bit, imprime via teletype do BIOS ({@code int 0x10,
 * ah=0x0E}) e espelha no COM1 (0x3F8, a captura headless), e para. Aceitação do
 * plano: {@code qemu-system-x86_64 -drive format=raw,file=kof.img} dá boot e o
 * hello aparece; a sabotagem (quebrar a assinatura {@code 0xAA55}) faz o qemu
 * recusar o disco ("not a bootable disk") e nada é impresso.
 *
 * <p>Fatia B-3a: só o BOOT PATH (nada de payload Kof — a carga dos próximos
 * setores é a B-3b). A imagem é flat: o setor começa em 0x7C00 e fecha com a
 * assinatura em 0x1FE.
 */
class BiosBootE2ETest extends BiosBootSupport {

    private static final String HELLO = """
            main() {
                println("KO-BIOS PAYLOAD")
            }
            """;

    private static final String MARKER = "KO-BIOS OK";

    private static final String BAD_MARKER = "KO-BIOS LOAD BAD";

    private static final String LM_MARKER = "KO-BIOS LM64 OK";


    @Test
    void biosArtifactIsBootableMbr(@TempDir Path tempDir) throws IOException {
        assumeTrue(hasTool("as", "--version") && hasTool("ld", "--version")
                && hasTool("objcopy", "--version"), "toolchain binutils ausente");
        Path img = build(tempDir, HELLO);
        byte[] b = Files.readAllBytes(img);
        assertTrue(b.length >= 512, "imagem BIOS deveria ter >= 512 bytes, tem " + b.length);
        assertEquals((byte) 0x55, b[510], "assinatura de boot: byte 0x1FE deve ser 0x55");
        assertEquals((byte) 0xAA, b[511], "assinatura de boot: byte 0x1FF deve ser 0xAA");
    }

    @Test
    void biosMbrPrintsUnderQemuAndSabotagedMagicRefuses(@TempDir Path tempDir) throws Exception {
        Path qemu = findQemu();
        assumeTrue(qemu != null, "qemu-system-x86_64 ausente");
        assumeTrue(hasTool("as", "--version") && hasTool("ld", "--version")
                && hasTool("objcopy", "--version"), "toolchain binutils ausente");
        Path img = build(tempDir, HELLO);

        // (a) boot POSITIVO — o setor imprime o marcador no COM1.
        Path ser = tempDir.resolve("ser.log");
        Process p = new ProcessBuilder(qemuCmd(qemu, img, ser)).redirectErrorStream(true).start();
        String text = "";
        try {
            long deadline = System.currentTimeMillis() + 60_000;
            while (System.currentTimeMillis() < deadline) {
                if (Files.exists(ser)) {
                    text = serialText(ser);
                    if (text.contains(MARKER)) break;
                }
                Thread.sleep(500);
            }
            assertTrue(text.contains(MARKER),
                    "BIOS nao imprimiu '" + MARKER + "' no serial. Log: "
                            + text.substring(Math.max(0, text.length() - 400)));
        } finally {
            p.destroyForcibly();
        }

        // (b) SABOTAGEM — sem a assinatura 0xAA55 o firmware recusa o disco e
        // NADA é impresso (o nível é real, não decorativo).
        byte[] corrupt = Files.readAllBytes(img);
        corrupt[510] = 0;
        corrupt[511] = 0;
        Path bad = tempDir.resolve("bad.img");
        Files.write(bad, corrupt);
        Path ser2 = tempDir.resolve("ser2.log");
        Process p2 = new ProcessBuilder(qemuCmd(qemu, bad, ser2)).redirectErrorStream(true).start();
        try {
            Thread.sleep(5_000);
        } finally {
            p2.destroyForcibly();
        }
        String text2 = Files.exists(ser2) ? serialText(ser2) : "";
        assertFalse(text2.contains(MARKER),
                "assinatura quebrada NAO pode bootar, mas imprimiu: " + text2);
    }

    @Test
    void biosLoadsPayloadSectorAndNamesCorruptedPayload(@TempDir Path tempDir) throws Exception {
        Path qemu = findQemu();
        assumeTrue(qemu != null, "qemu-system-x86_64 ausente");
        assumeTrue(hasTool("as", "--version") && hasTool("ld", "--version")
                && hasTool("objcopy", "--version"), "toolchain binutils ausente");
        Path img = build(tempDir, HELLO);

        // (a) carga POSITIVA — o setor lê o payload (LBA 1) e valida a magia.
        Path ser = tempDir.resolve("ser.log");
        Process p = new ProcessBuilder(qemuCmd(qemu, img, ser)).redirectErrorStream(true).start();
        String text = "";
        try {
            long deadline = System.currentTimeMillis() + 60_000;
            while (System.currentTimeMillis() < deadline) {
                if (Files.exists(ser)) {
                    text = serialText(ser);
                    if (text.contains(MARKER)) break;
                }
                Thread.sleep(500);
            }
        } finally {
            p.destroyForcibly();
        }
        assertTrue(text.contains(MARKER),
                "payload (LBA 1) nao carregou/magic nao bateu. Log: " + text);

        // (b) payload CORROMPIDO — a magia KOFPAYLD do header deixa de bater e
        // o stage2 imprime a falha NOMEADA no serial (nunca um hang silencioso).
        byte[] corrupt = Files.readAllBytes(img);
        int hdr = findMagic(corrupt);
        assertTrue(hdr >= 0, "header KOFPAYLD ausente na imagem flat");
        corrupt[hdr] = (byte) 'X';
        Path bad = tempDir.resolve("badpay.img");
        Files.write(bad, corrupt);
        Path ser2 = tempDir.resolve("ser2.log");
        Process p2 = new ProcessBuilder(qemuCmd(qemu, bad, ser2)).redirectErrorStream(true).start();
        try {
            long deadline = System.currentTimeMillis() + 60_000;
            while (System.currentTimeMillis() < deadline) {
                if (Files.exists(ser2) && serialText(ser2).contains(BAD_MARKER)) break;
                Thread.sleep(500);
            }
        } finally {
            p2.destroyForcibly();
        }
        String text2 = Files.exists(ser2) ? serialText(ser2) : "";
        assertTrue(text2.contains(BAD_MARKER),
                "payload corrompido deveria imprimir '" + BAD_MARKER + "'. Log: " + text2);
        assertFalse(text2.contains(MARKER),
                "payload corrompido NAO pode reportar sucesso: " + text2);
    }

    /** B-3b-3: o PAYLOAD KOF REAL roda bare — o boot carrega os N setores do
     * programa (header KOFPAYLD), entra em long mode, copia o staging para a
     * base 0x100000 e salta para {@code kof_payload_entry}; o println do main
     * sai no COM1 via a costura kof_plat_write (corpo BIOS). Prova: o TEXTO DO
     * PROGRAMA ("KO-BIOS PAYLOAD") aparece no serial DEPOIS dos marcadores do
     * boot. */
    @Test
    void biosRunsKofMainBare(@TempDir Path tempDir) throws Exception {
        Path qemu = findQemu();
        assumeTrue(qemu != null, "qemu-system-x86_64 ausente");
        assumeTrue(hasTool("as", "--version") && hasTool("ld", "--version")
                && hasTool("objcopy", "--version"), "toolchain binutils ausente");
        Path img = build(tempDir, HELLO);

        Path ser = tempDir.resolve("ser.log");
        Process p = new ProcessBuilder(qemuCmd(qemu, img, ser)).redirectErrorStream(true).start();
        String text = "";
        try {
            long deadline = System.currentTimeMillis() + 120_000;
            while (System.currentTimeMillis() < deadline) {
                if (Files.exists(ser)) {
                    text = serialText(ser);
                    if (text.contains("KO-BIOS PAYLOAD")) break;
                }
                Thread.sleep(500);
            }
        } finally {
            p.destroyForcibly();
        }
        assertTrue(text.contains(MARKER), "boot deveria imprimir '" + MARKER + "': " + text);
        assertTrue(text.contains(LM_MARKER), "boot deveria imprimir '" + LM_MARKER + "': " + text);
        assertTrue(text.contains("KO-BIOS PAYLOAD"),
                "o main Kof nao rodou bare (println ausente no serial). Log: "
                        + text.substring(Math.max(0, text.length() - 400)));
    }

    @Test
    void biosEntersLongModeAndRuns64BitCode(@TempDir Path tempDir) throws Exception {
        Path qemu = findQemu();
        assumeTrue(qemu != null, "qemu-system-x86_64 ausente");
        assumeTrue(hasTool("as", "--version") && hasTool("ld", "--version")
                && hasTool("objcopy", "--version"), "toolchain binutils ausente");
        Path img = build(tempDir, HELLO);

        Path ser = tempDir.resolve("ser.log");
        Process p = new ProcessBuilder(qemuCmd(qemu, img, ser)).redirectErrorStream(true).start();
        String text = "";
        try {
            long deadline = System.currentTimeMillis() + 90_000;
            while (System.currentTimeMillis() < deadline) {
                if (Files.exists(ser)) {
                    text = serialText(ser);
                    if (text.contains(LM_MARKER)) break;
                }
                Thread.sleep(500);
            }
        } finally {
            p.destroyForcibly();
        }
        assertTrue(text.contains(MARKER),
                "carga 16-bit nao rodou antes do long mode. Log: " + text);
        assertTrue(text.contains(LM_MARKER),
                "codigo 64-bit nao rodou (long mode nao entrou). Log: "
                        + text.substring(Math.max(0, text.length() - 400)));
    }

    /** B-5 (D-BAREMETAL-BODIES): o relógio de parede do BIOS vem do RTC CMOS —
     *  {@code time.now()} roda bare e devolve um epoch MODERNO (> 2020), não
     *  zero/lixo. Prova a leitura do RTC + a conversão data→epoch (BCD, 12h/24h,
     *  século) pela costura {@code kof_plat_time}; mono/sleep seguem recusas
     *  nomeadas. */
    @Test
    void biosTimeNowRunsBare(@TempDir Path tempDir) throws Exception {
        Path qemu = findQemu();
        assumeTrue(qemu != null, "qemu-system-x86_64 ausente");
        assumeTrue(hasTool("as", "--version") && hasTool("ld", "--version")
                && hasTool("objcopy", "--version"), "toolchain binutils ausente");
        Path img = build(tempDir, """
                main() {
                    println(time.now() > 1600000000000)
                }
                """);

        Path ser = tempDir.resolve("ser.log");
        Process p = new ProcessBuilder(qemuCmd(qemu, img, ser)).redirectErrorStream(true).start();
        String text = "";
        try {
            long deadline = System.currentTimeMillis() + 120_000;
            while (System.currentTimeMillis() < deadline) {
                if (Files.exists(ser)) {
                    text = serialText(ser);
                    if (text.contains("true") || text.contains("false")) break;
                }
                Thread.sleep(500);
            }
        } finally {
            p.destroyForcibly();
        }
        assertTrue(text.contains(MARKER), "boot nao imprimiu '" + MARKER + "': " + text);
        assertTrue(text.contains("true"),
                "time.now() no BIOS nao leu um epoch moderno do RTC. Log: "
                        + text.substring(Math.max(0, text.length() - 400)));
        assertFalse(text.contains("false"),
                "time.now() retornou epoch invalido/antigo no BIOS (RTC mal lido). Log: "
                        + text.substring(Math.max(0, text.length() - 400)));
    }

    /** B-5 (D-BAREMETAL-BODIES): capacidade ainda SEM corpo no BIOS
     *  ({@code http.get} → {@code kof_plat_net_*}) recusa de forma NOMEADA
     *  (R6) com diagnóstico **ASCII legível** no COM1 — nunca stub silencioso
     *  nem a forma UTF-16 do UEFI. O programa para na recusa. */
    @Test
    void biosUnsupportedCapabilityPrintsReadableRefusal(@TempDir Path tempDir) throws Exception {
        Path qemu = findQemu();
        assumeTrue(qemu != null, "qemu-system-x86_64 ausente");
        assumeTrue(hasTool("as", "--version") && hasTool("ld", "--version")
                && hasTool("objcopy", "--version"), "toolchain binutils ausente");
        Path img = build(tempDir, """
                main() {
                    println("BEFORE")
                    println(http.get("http://example.com"))
                    println("AFTER")
                }
                """);

        Path ser = tempDir.resolve("ser.log");
        Process p = new ProcessBuilder(qemuCmd(qemu, img, ser)).redirectErrorStream(true).start();
        String text = "";
        try {
            long deadline = System.currentTimeMillis() + 120_000;
            while (System.currentTimeMillis() < deadline) {
                if (Files.exists(ser)) {
                    text = serialText(ser);
                    if (text.contains("kof_plat_net")) break;
                }
                Thread.sleep(500);
            }
        } finally {
            p.destroyForcibly();
        }
        assertTrue(text.contains("BEFORE"),
                "o programa nao comecou a rodar antes da recusa. Log: " + text);
        assertTrue(text.contains("KOF BIOS") && text.contains("kof_plat_net"),
                "recusa nomeada ilegivel no COM1 (esperado ASCII 'KOF BIOS ... kof_plat_net'). Log: "
                        + text.substring(Math.max(0, text.length() - 400)));
        assertFalse(text.contains("AFTER"),
                "a recusa deveria PARAR o programa (nada apos http.get). Log: " + text);
    }

    /** B-5 (D-BAREMETAL-BODIES): {@code kof_plat_time_mono} no BIOS é REAL
     *  (TSC calibrado pelo PIT) — provado pelo span: dormir 60 ms entre
     *  {@code spanStart}/{@code spanEnd} produz {@code durationMicros >= 10000}
     *  no JSON (se o mono fosse stub/recusa, não haveria duração ou seria ~0). */
    @Test
    void biosMonoSpanDuration(@TempDir Path tempDir) throws Exception {
        Path qemu = findQemu();
        assumeTrue(qemu != null, "qemu-system-x86_64 ausente");
        assumeTrue(hasTool("as", "--version") && hasTool("ld", "--version")
                && hasTool("objcopy", "--version"), "toolchain binutils ausente");
        Path img = build(tempDir, """
                main() {
                    var h = observability.spanStart("op")
                    time.sleep(60)
                    var j = observability.spanEnd(h)
                    println(j)
                }
                """);

        Path ser = tempDir.resolve("ser.log");
        Process p = new ProcessBuilder(qemuCmd(qemu, img, ser)).redirectErrorStream(true).start();
        String text = "";
        try {
            long deadline = System.currentTimeMillis() + 120_000;
            while (System.currentTimeMillis() < deadline) {
                if (Files.exists(ser)) {
                    text = serialText(ser);
                    if (text.contains("durationMicros")) break;
                }
                Thread.sleep(500);
            }
        } finally {
            p.destroyForcibly();
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"durationMicros\"\\s*:\\s*(\\d+)").matcher(text);
        assertTrue(m.find(),
                "spanEnd nao trouxe durationMicros no BIOS. Log: "
                        + text.substring(Math.max(0, text.length() - 400)));
        long us = Long.parseLong(m.group(1));
        assertTrue(us >= 10000,
                "mono do BIOS nao mediu o sleep de 60 ms (durationMicros=" + us
                        + ", esperado >= 10000). Log: "
                        + text.substring(Math.max(0, text.length() - 400)));
    }

    /** B-5 (D-BAREMETAL-BODIES): {@code random.*} no BIOS tem corpo REAL
     *  (RDRAND/TSC + xorshift64) — não a recusa. Provado por: o programa chega
     *  ao fim ({@code AFTER}) e duas amostras de 10^9 não colidem (a chance de
     *  falso-vermelho é 10^-9). */
    @Test
    void biosRandomRunsBare(@TempDir Path tempDir) throws Exception {
        Path qemu = findQemu();
        assumeTrue(qemu != null, "qemu-system-x86_64 ausente");
        assumeTrue(hasTool("as", "--version") && hasTool("ld", "--version")
                && hasTool("objcopy", "--version"), "toolchain binutils ausente");
        Path img = build(tempDir, """
                main() {
                    var a = random.randomInt(1000000000)
                    var b = random.randomInt(1000000000)
                    println(a != b)
                    println("AFTER")
                }
                """);

        Path ser = tempDir.resolve("ser.log");
        Process p = new ProcessBuilder(qemuCmd(qemu, img, ser)).redirectErrorStream(true).start();
        String text = "";
        try {
            long deadline = System.currentTimeMillis() + 120_000;
            while (System.currentTimeMillis() < deadline) {
                if (Files.exists(ser)) {
                    text = serialText(ser);
                    if (text.contains("AFTER")) break;
                }
                Thread.sleep(500);
            }
        } finally {
            p.destroyForcibly();
        }
        assertTrue(text.contains(MARKER), "boot nao imprimiu '" + MARKER + "': " + text);
        assertTrue(text.contains("AFTER"),
                "random.* no BIOS nao chegou ao fim (ainda recusa?). Log: "
                        + text.substring(Math.max(0, text.length() - 400)));
        assertTrue(text.contains("true"),
                "random.randomInt(10^9) no BIOS deu valores colidentes/sem entropia. Log: "
                        + text.substring(Math.max(0, text.length() - 400)));
    }

    /** B-5 (D-BAREMETAL-BODIES): {@code time.sleep} no BIOS é REAL (PIT canal 0),
     *  não a recusa — provado pela parede: dormir 1.1 s tem de avançar o RTC em
     *  >= 1 s (se o sleep retornasse na hora, o delta seria 0 e sairia `false`). */
    @Test
    void biosSleepAdvancesWallClock(@TempDir Path tempDir) throws Exception {
        Path qemu = findQemu();
        assumeTrue(qemu != null, "qemu-system-x86_64 ausente");
        assumeTrue(hasTool("as", "--version") && hasTool("ld", "--version")
                && hasTool("objcopy", "--version"), "toolchain binutils ausente");
        Path img = build(tempDir, """
                main() {
                    var before = time.now()
                    time.sleep(1100)
                    var after = time.now()
                    println(after - before >= 1000)
                }
                """);

        Path ser = tempDir.resolve("ser.log");
        Process p = new ProcessBuilder(qemuCmd(qemu, img, ser)).redirectErrorStream(true).start();
        String text = "";
        try {
            long deadline = System.currentTimeMillis() + 120_000;
            while (System.currentTimeMillis() < deadline) {
                if (Files.exists(ser)) {
                    text = serialText(ser);
                    if (text.contains("true") || text.contains("false")) break;
                }
                Thread.sleep(500);
            }
        } finally {
            p.destroyForcibly();
        }
        assertTrue(text.contains(MARKER), "boot nao imprimiu '" + MARKER + "': " + text);
        assertTrue(text.contains("true"),
                "time.sleep(1100) no BIOS nao avancou o RTC em >= 1 s (PIT nao dormiu). Log: "
                        + text.substring(Math.max(0, text.length() - 400)));
        assertFalse(text.contains("false"),
                "time.sleep retornou cedo demais no BIOS. Log: "
                        + text.substring(Math.max(0, text.length() - 400)));
    }
}
