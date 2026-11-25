package dev.kof.compiler.nat;

import java.util.ArrayList;
import java.util.List;

/**
 * M-1 (#1135, D-NATIVE-MACOS) — as faces ELF-ONLY que o texto emitido ainda
 * carrega: o TLS local-exec ({@code %fs:X@tpoff}) e o syscall cru (o
 * {@code syscall} do x86-64, o {@code svc #0} do aarch64 e o {@code ecall} do
 * riscv64).
 *
 * <p>É o que a RECUSA nomeada (NATIVE005) mede: o perfil macOS não traduz o
 * texto às cegas — ele diz QUAIS famílias ainda falam o ABI Linux. A
 * detecção é separada da TRADUÇÃO porque são duas responsabilidades: uma
 * traduz, a outra mede.
 */
public final class NativeMachOFaces {

    private NativeMachOFaces() {}

    /**
     * Os SÍTIOS de TLS ELF local-exec ({@code %fs:X@tpoff}) do texto.
     *
     * <p>É a face que o Mach-O não tem: a migração é a etapa M-4 (TLV ou
     * pthread key). O perfil macOS recusa o programa com o diagnóstico
     * nomeado (R6) — o texto NÃO é traduzido às cegas.
     */
    public static List<String> elfTlsSites(String asm) {
        return elfSites(asm, "%fs:", "@tpoff");
    }

    /**
     * Os SÍTIOS de SYSCALL CRU (o ABI Linux) do texto — a face que o macOS
     * troca pela costura {@code kof_plat_*} sobre a libSystem (a etapa M-2
     * do plano).
     *
     * <p>O detector cobre AS TRÊS faces do runtime nativo: o {@code syscall}
     * do x86-64, o {@code svc #0} do aarch64 e o {@code ecall} do riscv64.
     * Antes ele só via o x86-64 — um texto aarch64/riscv passava pela recusa
     * sem ser medido (a recusa ficava CEGA justamente nas faces onde o
     * syscall é outra instrução).
     */
    public static List<String> elfSyscallSites(String asm) {
        List<String> sites = new ArrayList<>();
        for (String[] site : elfSyscallScan(asm)) {
            sites.add(site[1]);
        }
        return sites;
    }

    /**
     * Os DONOS dos sítios de syscall (o rótulo que os contém) — a recusa
     * NOMEIA a família que ainda fala o ABI Linux (a costura
     * {@code kof_plat_*}, o allocator, a concorrência), em vez de dizer só
     * "N syscall(s)".
     */
    public static List<String> elfSyscallOwners(String asm) {
        List<String> owners = new ArrayList<>();
        for (String[] site : elfSyscallScan(asm)) {
            if (!owners.contains(site[0])) owners.add(site[0]);
        }
        return owners;
    }

    /** Os sítios de syscall cru com o rótulo que os contém. */
    private static List<String[]> elfSyscallScan(String asm) {
        List<String[]> sites = new ArrayList<>();
        String owner = "?";
        for (String line : asm.split("\n", -1)) {
            String code = line;
            int commentAt = NativeMachO.commentStart(code);
            if (commentAt >= 0) code = code.substring(0, commentAt);
            String trimmed = code.trim();
            if (trimmed.endsWith(":") && !trimmed.contains(" ")) {
                owner = trimmed.substring(0, trimmed.length() - 1);
                continue;
            }
            if (isRawSyscall(trimmed)) {
                sites.add(new String[]{owner, trimmed});
            }
        }
        return sites;
    }

    /** O mnemônico de syscall CRU de cada face do runtime nativo. */
    private static boolean isRawSyscall(String trimmed) {
        if (trimmed.equals("syscall") || trimmed.startsWith("syscall ")) return true;  // x86-64
        if (trimmed.equals("ecall") || trimmed.startsWith("ecall ")) return true;      // riscv64
        if (trimmed.equals("svc") || trimmed.startsWith("svc ")) return true;          // aarch64
        return false;
    }

    /** As linhas do texto que contêm QUALQUER dos marcadores dados. */
    private static List<String> elfSites(String asm, String... markers) {
        List<String> sites = new ArrayList<>();
        String[] lines = asm.split("\n", -1);
        for (String line : lines) {
            String code = line;
            int commentAt = NativeMachO.commentStart(line);
            if (commentAt >= 0) code = line.substring(0, commentAt);
            for (String marker : markers) {
                if (code.contains(marker)) {
                    sites.add(line.trim());
                    break;
                }
            }
        }
        return sites;
    }
}
