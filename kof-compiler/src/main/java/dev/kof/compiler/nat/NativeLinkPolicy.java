package dev.kof.compiler.nat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import dev.kof.compiler.KofLoadLiteral;
import dev.kof.compiler.KofOperation;

/**
 * Política de link nativo: o gate NATIVE003 do perfil freestanding (B-1) e a
 * detecção de wire MySQL por URL literal (link-by-use). Extraída de
 * NativeBackend pelo gate ≤500 (a classe cruzou 600 com o perfil UEFI, B-2);
 * responsabilidade própria: o que pode linkar, com quais libs, por quê.
 */
final class NativeLinkPolicy {

    private NativeLinkPolicy() {}

    /** Delega o assemble/link ao NativeAssembler (script/objcopy por perfil). */
    static void assemble(NativeBackend backend, Path asmFile, Path binFile) throws IOException {
        // B-1: no perfil freestanding (x86_64) o link é estático e sem libc —
        // qualquer capacidade que precise de libc é RECUSADA com diagnóstico
        // (nunca um link que falha feio nem um binário que resolve em runtime).
        if (backend.freestanding && backend.target == dev.kof.compiler.Target.NATIVE
                && (backend.usesDb || backend.usesOrm || backend.usesMysql || backend.usesConcurrency
                        || backend.usesPow || backend.usesProcess || !backend.ffiLibs.isEmpty())) {
            throw new IOException("NATIVE003: perfil freestanding nao suporta libc (db/mysql/concurrency/pow/ffi/process) "
                    + "neste alvo; use o perfil host ou remova a dependencia");
        }
        // M-1 (#1135, D-NATIVE-MACOS): as faces ELF-only que o macOS ainda
        // NÃO tem. A medição é sobre o TEXTO EMITIDO (o mesmo texto que o
        // tradutor vai converter) — o perfil recusa com o diagnóstico
        // NOMEADO (R6) em vez de gerar um binário que falha em runtime.
        //   - o TLS ELF local-exec (`%fs:X@tpoff`) → a etapa M-4 (TLV ou
        //     pthread key);
        //   - o syscall CRU do Linux (o ABI Linux) → a etapa M-2 (a costura
        //     `kof_plat_*` sobre a libSystem).
        if (NativeProfile.active.isMacos()) {
            String asm = Files.readString(asmFile);
            List<String> tls = NativeMachO.elfTlsSites(asm);
            List<String> syscalls = NativeMachO.elfSyscallSites(asm);
            if (!tls.isEmpty() || !syscalls.isEmpty()) {
                StringBuilder msg = new StringBuilder("NATIVE005: o perfil macOS ainda nao tem ");
                if (!tls.isEmpty()) {
                    msg.append("a face de TLS (o ELF local-exec `%fs:...@tpoff` — a etapa M-4 do plano #1135: "
                            + "TLV ou pthread key); ");
                }
                if (!syscalls.isEmpty()) {
                    msg.append("a face de syscall (o ABI Linux cru — a etapa M-2 do plano #1135: "
                            + "a costura `kof_plat_*` sobre a libSystem; o allocator/mmap e a etapa M-3 "
                            + "e a concorrencia e a etapa M-5); ");
                }
                msg.append("o programa emite ").append(tls.size()).append(" site(s) de TLS e ")
                   .append(syscalls.size()).append(" syscall(s) crus");
                if (!syscalls.isEmpty()) {
                    msg.append(" (nos sites: ")
                       .append(String.join(", ", NativeMachO.elfSyscallOwners(asm))).append(")");
                }
                msg.append(" — o perfil macOS nao emite um binario "
                   + "que falha em runtime (R6); a migracao e a M-2 (a costura) e a M-4 (o TLS)");
                throw new IOException(msg.toString());
            }
        }
        // M-1 (#1135, D-NATIVE-MACOS): o perfil macOS exige o TOOLCHAIN macOS
        // (o assembler Mach-O e o linker da libSystem). Numa frota Linux o
        // `as`/`ld` do GNU NÃO falam Mach-O — o link daria um erro confuso
        // (ou um binário GNU). A recusa é HONESTA e nomeada (R6): o perfil
        // macOS é emitido e medido (o texto + a linha de link), mas o LINK
        // exige o runner macOS (a etapa M-7).
        if (NativeProfile.active.isMacos()
                && !System.getProperty("os.name", "").toLowerCase().contains("mac")) {
            throw new IOException("NATIVE004: o perfil macOS exige o toolchain macOS "
                    + "(o assembler Mach-O + o linker da libSystem); este host nao o tem "
                    + "— a etapa M-7 (o runner macos-14) e o que prova o link; "
                    + "use o perfil host neste host");
        }
        // R2 fatia 1 (20/09): -lm AGORA é by-use como sqlite/mariadb/pthread —
        // o shim `call pow` do monolito virou WEAK (RuntimeMath `.weak pow`),
        // então linkar sem libm fecha; usaPow só quando a fonte chama
        // kof_math_pow (scan acima — único caminho ao shim). A história do
        // 7f174a6f (arg morto, link incondicional) mora aqui.
        NativeAssembler.assemble(asmFile, binFile, backend.usesDb || backend.usesOrm,
                backend.usesConcurrency, backend.ffiLibs, backend.usesPow, backend.freestanding);
    }

    /** Detecta o protocolo do URL de conexão quando é um literal em
     *  compile-time (intenção conhecida pelo compilador). Desde §523 (27/09)
     *  o wire mysql é auto-contido e NÃO exige lib no link (S0 recusa NOMEADA
     *  no runtime para o resto); o flag restante serve ao gate freestanding
     *  (sem sockets sem libc). URLs dinâmicos seguem conservadores (true). */
    static boolean connectsToMysql(int callIndex, List<KofOperation> ops) {
        for (int j = callIndex - 1; j >= 0 && j >= callIndex - 8; j--) {
            if (ops.get(j) instanceof KofLoadLiteral lit && lit.value() instanceof String url) {
                String u = url.toLowerCase();
                // §523: sem link-by-use de libmariadb — o wire nunca chamou a
                // lib (zero simbolo, medido via readelf); sem a flag, a prova
                // hermética linka em host sem a lib (CI).
                return u.startsWith("mysql://") || u.startsWith("mariadb://")
                        || u.startsWith("jdbc:mysql://");
            }
        }
        return true;
    }
}
