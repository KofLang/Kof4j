package dev.kof.compiler.nat;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Link dinâmico SOB DEMANDA (link-by-use) dos alvos cross riscv64/aarch64.
 *
 * <p>Diretriz da mantenedora (15/09): "liga dinamicamente". O x86_64 já liga
 * dinamicamente (`ld -dynamic-linker /lib64/ld-linux-x86-64.so.2 -lc`); o cross
 * nasceu ESTÁTICO (asm puro, sem libc) — a decisão do §2.3 do
 * `native-multiarch.md` sempre foi dinâmica, mas a implementação divergiu.
 *
 * <p>Modelo escolhido: <b>link-by-use</b>. O binário continua ESTÁTICO enquanto
 * o runtime (já PODADO) não referenciar nenhum símbolo de libc; no momento em
 * que alguma capacidade libc-dependente entra (double→string/FLT001 via
 * snprintf/strtod, kof.db via .so, etc.), o link passa a dinâmico com
 * `-dynamic-linker <loader> --sysroot <sysroot> -lc`. Isso preserva a
 * portabilidade dos binários atuais (nada muda sem um consumidor libc) e
 * destrava os gaps cross SOB DEMANDA.
 *
 * <p>Sysroot: a libc-cross não está em `/usr/<arch>-linux-gnu` neste host (foi
 * extraída em `/tmp/opencode/x`). Resolução em ordem:
 * <ol>
 *   <li>`KOF_CROSS_SYSROOT` (env) — explícito, vence sempre;</li>
 *   <li>instalação de sistema (`/usr/<arch>-linux-gnu/lib/<loader>` presente)
 *       — sem `--sysroot` (caminhos default do ld); é o caso do CI com
 *       `apt-get install libc6-<arch>-cross`;</li>
 *   <li>`/tmp/opencode/x` (o toolchain documentado deste host).</li>
 * </ol>
 * Sem nenhum sysroot → mantém ESTÁTICO + stderr (R6: nunca link quebrado
 * silencioso).
 */
public final class NativeCrossLink {

    private NativeCrossLink() {}

    /** Símbolos de libc cuja presença num `call` torna o link dinâmico. Lista
     *  CURADA (não heurística): cresce só quando um consumidor novo entra. */
    static final Set<String> LIBC_SYMBOLS = Set.of(
            "snprintf", "strtod", "printf", "malloc", "calloc", "realloc", "free",
            "pow", "sqrt", "fmod", "dlopen", "dlsym", "dlclose",
            "fopen", "fclose", "fwrite", "fread", "memcpy", "memset",
            "strlen", "strcmp", "strncmp", "open", "read", "write", "execvp");

   static final Set<String> LIBM_SYMBOLS = Set.of(
            "sin", "cos", "tan", "asin", "acos", "atan", "atan2",
            "sinh", "cosh", "tanh", "log", "log10", "exp",
            "floor", "ceil", "round", "hypot"
    );

    /** true se o texto asm (pós-poda) chama algum símbolo de libc. */
    static boolean needsLibc(String asmText) {
        for (String line : asmText.split("\n", -1)) {
            String t = line.strip();
            if (t.startsWith("#")) continue;
            int hash = t.indexOf('#');
            if (hash > 0) t = t.substring(0, hash).stripTrailing();
            if (t.startsWith("call ")) {
                String sym = t.substring(5).strip();
                if (LIBC_SYMBOLS.contains(sym)) return true;
            }
        }
        return false;
    }

    /** Símbolos da libsqlite3 (DB001). Prefixo (não lista curada): a API é
     *  grande e estável; qualquer `call sqlite3_*` implica o consumidor DB. */
    static final String SQLITE_PREFIX = "sqlite3_";

    /** true se o texto asm (pós-poda) chama algum símbolo da libsqlite3. */
    static boolean needsSqlite(String asmText) {
        for (String line : asmText.split("\n", -1)) {
            String t = line.strip();
            if (t.startsWith("#")) continue;
            int hash = t.indexOf('#');
            if (hash > 0) t = t.substring(0, hash).stripTrailing();
            if (t.startsWith("call ")) {
                String sym = t.substring(5).strip();
                if (sym.startsWith(SQLITE_PREFIX)) return true;
            }
        }
        return false;
    }

    /** row 10 (27/09, D-DECISION-BATCH-2709B #3): `call pow` (shim
     *  kof_math_pow, peça própria) torna o link cross dinâmico COM `-lm`
     *  POR USO — quem não chama pow não tem a peça, logo não liga libm. */
    static boolean needsLibm(String asmText) {
        for (String line : asmText.split("\n", -1)) {
            String t = line.strip();
            if (t.startsWith("#")) continue;
            int hash = t.indexOf('#');
            if (hash > 0) t = t.substring(0, hash).stripTrailing();
            String sym = t.substring(5).strip();
    return LIBM_SYMBOLS.contains(sym);
        }
        return false;
    }

    /** Caminho do `libsqlite3.so*` no sysroot resolvido para a arch, ou null
     *  se não houver libsqlite3-cross (CI instala só `libc6-*-cross`). */
    static String sqliteLibFor(String arch) {
        String sysroot = sysrootFor(arch);
        String name = sqliteLibFile(arch);
        if (sysroot == null || name == null) return null;
        String base = sysroot.isEmpty() ? "" : sysroot;
        return base + "/usr/" + arch + "-linux-gnu/lib/" + name;
    }

    /** true se dá para ligar `-lsqlite3` no alvo (sysroot com a lib). */
    public static boolean sqliteAvailable(String arch) {
        return sqliteLibFor(arch) != null;
    }

    /** Sysroot cross resolvido (null se não há libc-cross) — bridge de leitura
     *  p/ os E2E de outros pacotes (KofDbE2ETest). */
    public static String sysrootOrNull(String arch) {
        return sysrootFor(arch);
    }

    /** Arquivo `libsqlite3.so*` disponível no sysroot (dev symlink ou soname),
     *  ou null. Preferência pelo `.so` (o ld acha via `-lsqlite3`). */
    static String sqliteLibFile(String arch) {
        String sysroot = sysrootFor(arch);
        if (sysroot == null) return null;
        String base = sysroot.isEmpty() ? "" : sysroot;
        for (String name : new String[]{"libsqlite3.so", "libsqlite3.so.0"}) {
            File f = new File(base + "/usr/" + arch + "-linux-gnu/lib/" + name);
            if (f.exists()) return name;
        }
        return null;
    }

    /** Arg de link da libsqlite3: `-lsqlite3` quando há `libsqlite3.so`, senão
     *  `-l:libsqlite3.so.0` (o soname que `libsqlite3-0` instala no CI). */
    static String sqliteLinkArg(String arch) {
        String f = sqliteLibFile(arch);
        return (f != null && f.endsWith(".so.0")) ? "-l:libsqlite3.so.0" : "-lsqlite3";
    }

    static String loaderBase(String arch) {
        return switch (arch) {
            case "riscv64" -> "ld-linux-riscv64-lp64d.so.1";
            case "aarch64" -> "ld-linux-aarch64.so.1";
            default -> throw new IllegalArgumentException("arch cross: " + arch);
        };
    }

    /** Caminho do loader COMO VISTO PELO BINÁRIO EM TEMPO DE EXECUÇÃO (layout
     *  Debian: `/lib/<loader>`). É o arg de `-dynamic-linker`. */
    static String ldPathFor(String arch) {
        return "/lib/" + loaderBase(arch);
    }

    /** Prefixo para `QEMU_LD_PREFIX` ao rodar o binário dinâmico: o loader
     *  resolve-se em {@code <prefix>/lib/<loader>} (layout Debian). É a pasta
     *  {@code <arch>-linux-gnu} do sysroot, ou {@code null} se não há libc. */
    static String qemuPrefixFor(String arch) {
        String sysroot = sysrootFor(arch);
        if (sysroot == null) return null;
        return sysroot + "/usr/" + arch + "-linux-gnu";
    }
    /**
     * Sysroot a usar, ou {@code ""} para caminhos de sistema, ou {@code null}
     * se não há libc-cross. Ver a ordem no cabeçalho.
     */
    static String sysrootFor(String arch) {
        String env = System.getenv("KOF_CROSS_SYSROOT");
        if (env != null && !env.isBlank()) return env;
        String loader = loaderBase(arch);
        if (new File("/usr/" + arch + "-linux-gnu/lib/" + loader).exists()) return "";
        if (new File("/tmp/opencode/x/usr/" + arch + "-linux-gnu/lib/" + loader).exists()) return "/tmp/opencode/x";
        return null;
    }

    /** Monta os args do ld. Estático → só os args de hoje + gc-sections.
     *  Dinâmico → + `--dynamic-linker` + `--sysroot` (se houver) + `-lc`. */
    static String[] ldArgs(String ld, Path binFile, Path objFile, String arch,
                           boolean dynamic, String sysroot) {
        return ldArgs(ld, binFile, objFile, arch, dynamic, sysroot, false);
    }

    /** Igual, mas com {@code -lsqlite3} quando {@code sqlite} (DB001). O
     *  consumidor SQLite implica libc (a libsqlite3 depende da libc) — o
     *  chamador passa {@code dynamic=true} nesse caso. */
    static String[] ldArgs(String ld, Path binFile, Path objFile, String arch,
                           boolean dynamic, String sysroot, boolean sqlite) {
        return ldArgs(ld, binFile, objFile, arch, dynamic, sysroot, sqlite, java.util.List.of());
    }

    /** #431: arg de link p/ uma `library()` de extern no cross. Caminho
     *  absoluto NÃO é cross-arch (é host) — só o basename vale no sysroot:
     *  `libX.so[.N]` → `-l:libX.so.N` (igual x86), nome cru `X` → `-lX`.
     *  Exceção (fatia C4-x): um objeto {@code .o} já montado PARA A ARCH
     *  (ex.: fixture gerada pelo `kof-c-compiler` cross) é cross-arch e entra
     *  posicional, preservando o path — o mesmo padrão que o x86 já usa. */
    static String ffiLinkArg(String lib) {
        if (lib.endsWith(".o")) return lib;
        String base = lib.startsWith("/") ? lib.substring(lib.lastIndexOf('/') + 1) : lib;
        return base.contains(".so") ? "-l:" + base : "-l" + base;
    }

    /** Igual, mas com os `library()` dos externs (#431) apos `-lc`/sqlite —
     *  ordem de resolucao: objeto primeiro, libs depois. */
    static String[] ldArgs(String ld, Path binFile, Path objFile, String arch,
                           boolean dynamic, String sysroot, boolean sqlite,
                           java.util.Collection<String> ffiLibs) {
        return ldArgs(ld, binFile, objFile, arch, dynamic, sysroot, sqlite, ffiLibs, false);
    }

    /** row 10 (27/09): {@code libm} acrescenta {@code -lm} (POR USO, quando o
     *  texto podado contém {@code call pow}). O chamador já passa
     *  {@code dynamic=true}` nesse caso (pow ∈ LIBC_SYMBOLS). */
    static String[] ldArgs(String ld, Path binFile, Path objFile, String arch,
                           boolean dynamic, String sysroot, boolean sqlite,
                           java.util.Collection<String> ffiLibs, boolean libm) {
        List<String> a = new ArrayList<>();
        a.add(ld);
        if (arch.equals("riscv64")) a.add("--no-relax");
        a.add("--gc-sections");
        if (!dynamic) {
            a.add("-o");
            a.add(binFile.toString());
            a.add(objFile.toString());
            return a.toArray(new String[0]);
        }
        // `--allow-shlib-undefined`: a libc.so do sysroot referencia símbolos
        // GLIBC_PRIVATE do loader; sem isto o ld aborta (o runtime resolve-os).
        a.add("--allow-shlib-undefined");
        if (sysroot != null && !sysroot.isEmpty()) a.add("--sysroot=" + sysroot);
        a.add("-dynamic-linker");
        a.add(ldPathFor(arch));
        a.add("-o");
        a.add(binFile.toString());
        a.add(objFile.toString());
        a.add("-lc");
        if (sqlite) a.add(sqliteLinkArg(arch));
        if (libm) a.add("-lm");
        for (String lib : ffiLibs) a.add(ffiLinkArg(lib));
        return a.toArray(new String[0]);
    }
}
