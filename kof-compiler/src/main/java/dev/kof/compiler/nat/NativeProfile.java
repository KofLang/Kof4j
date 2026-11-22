package dev.kof.compiler.nat;

/**
 * B-1 (PLAN-BAREMETAL-BOOT): perfil de link do alvo nativo.
 *
 * <p>{@link #HOST} é o padrão: Linux + libc, ligação dinâmica
 * ({@code -dynamic-linker … -lc}) — comportamento atual. {@link #FREESTANDING}
 * liga estático e sem libc ({@code ld -o bin obj}): o programa fala com o SO
 * só pela costura {@code kof_plat_*} (B-0). Capacidades que dependem de libc
 * (DB/MySQL/concurrency/pow/FFI) são recusadas com diagnóstico — nunca caem
 * silenciosamente.
 */
public enum NativeProfile {
    HOST,
    FREESTANDING,
    /** B-2 (PLAN-BAREMETAL-BOOT): aplicação UEFI PE32+ — link estático sem
     * libc (herda o perfil FREESTANDING) + entry {@code _start} MS x64
     * (RCX=ImageHandle, RDX=SystemTable), saída via
     * {@code SystemTable->ConOut->OutputString} e
     * {@code BootServices->Exit}; o ld é seguido de
     * {@code objcopy --target=pei-x86-64 --subsystem=10}. */
    UEFI,
    /** B-6.1 (PLAN-BAREMETAL-BOOT): UEFI + anéis de privilégio — herda o boot
     *  path {@link #UEFI} e instala a GDT/IDT/TSS PRÓPRIOS do Kof (code/data
     *  ring0 e ring1), recarregando {@code CS} para o seletor do Kof. A
     *  superfície Kof para mirar ring1 (B-6.2) é decisão rule 6; esta fatia é só
     *  a maquinaria. Não altera a saída do perfil {@link #UEFI}. */
    UEFI_RING,
    /** B-3 (PLAN-BAREMETAL-BOOT): boot path LEGACY BIOS — um setor de boot de
     *  512 bytes (magia {@code 0xAA55} em 0x1FE) em modo real 16-bit, entrada
     *  {@code _start} que imprime via teletype do BIOS ({@code int 0x10,
     *  ah=0x0E}) + COM1 e para. Herda o link estático sem libc do
     *  {@link #FREESTANDING}; carregar o payload Kof (B-3b) é a fatia seguinte. */
    BIOS,
    /** M-1 (#1135, D-NATIVE-MACOS 17/11): o alvo Native no macOS — Mach-O +
     *  libSystem.
     *
     *  <p>O perfil troca o FORMATO DO OBJETO (as diretivas GNU — {@code
     *  .type}/{@code .size}/{@code .section .text} — viram {@code __TEXT,__text}
     *  e afim, e os globais ganham o prefixo {@code _} do Mach-O) e a LINHA DE
     *  LINK ({@code ld -lSystem -syslibroot <sdk> -arch <arch>}), nunca a
     *  SEMÂNTICA: o código emitido é o mesmo, a costura {@code kof_plat_*} é a
     *  mesma, e o entry é o {@code _main} do Mach-O.
     *
     *  <p><b>A FACE (medida, 22/11):</b> o perfil NÃO carrega a arquitetura —
     *  a arquitetura segue o EMISSOR, e o emissor do alvo Native é o x86-64
     *  (o face Apple silicon exigiria o emissor cross aarch64 + a sua prova
     *  Mach-O — a próxima linha do plano). A linha de link por isso nomeia
     *  {@code -arch x86_64}: um {@code -arch arm64} sobre um objeto x86-64 é
     *  um link que falha no runner (o M-1 mediu o texto real: ele é x86-64).
     *  No Apple silicon o binário roda sob o Rosetta 2.
     *
     *  <p> nesta frota Linux o toolchain macOS NÃO existe — a prova da fatia
     *  é ESTRUTURAL (o emissor e a linha de link medidos), até o runner macOS
     *  (M-7) existir. */
    MACOS;

    /** M-1 (#1135): o perfil macOS (Mach-O + libSystem). */
    public boolean isMacos() { return this == MACOS; }

    /** B-3: o boot path legado (setor de boot) — sem costura EFI nem PE32+. */
    public boolean isBios() { return this == BIOS; }

    /** Perfil da compilação CORRENTE — a costura ({@code RuntimePlat}) e o
     *  link ({@code NativeAssembler}) são estáticos e sem parâmetro; o
     *  {@code NativeBackend} grava aqui no início de cada compilação. */
    public static NativeProfile active = HOST;

    /** B-6.1: o boot path UEFI — {@link #UEFI} e {@link #UEFI_RING} compartilham
     *  a costura EFI e o PE32+ (o {@code uefi-ring} só acrescenta as tabelas de
     *  descritor próprias). Mantém um único ponto de verdade para os corpos
     *  {@code kof_plat_*} e o link. */
    public boolean isUefi() { return this == UEFI || this == UEFI_RING; }

    public static boolean activeIsUefi() { return active.isUefi(); }

    /** B-3b-3: o perfil BIOS troca os corpos da costura kof_plat_* (COM1/hlt). */
    public static boolean activeIsBios() { return active.isBios(); }

    /** Aceita {@code host}/{@code freestanding}/{@code uefi}/{@code uefi-ring};
     *  o resto é erro do chamador. */
    public static NativeProfile of(String value) {
        return switch (value == null ? "" : value.trim().toLowerCase()) {
            case "", "host" -> HOST;
            case "freestanding", "bare" -> FREESTANDING;
            case "uefi", "efi" -> UEFI;
            case "uefi-ring", "ring", "rings" -> UEFI_RING;
            case "bios", "mbr" -> BIOS;
            // M-1 (#1135): o perfil macOS (Mach-O + libSystem). A ARCHITECTURA
            // não vem do perfil — vem do emissor (o x86-64 do alvo Native); a
            // face Apple silicon é a próxima linha do plano (#1135).
            case "macos", "darwin" -> MACOS;
            default -> throw new IllegalArgumentException("unknown native profile: " + value);
        };
    }
}
