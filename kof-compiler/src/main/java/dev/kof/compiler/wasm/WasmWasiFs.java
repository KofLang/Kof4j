package dev.kof.compiler.wasm;

import dev.kof.compiler.IRBasicBlock;
import dev.kof.compiler.IRMethod;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofOperation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Runtime WASI-preview1 do {@code kof.io} (fase 4 do plano #776): as faces
 * de ARQUIVO do host — {@code exists}/{@code isFile}/{@code isDirectory}/
 * {@code readText}/{@code writeText}.
 *
 * <p>SEMANTICA WASI (medida no host, não inventada): o preview1 resolve o
 * path RELATIVO a um diretorio pre-aberto (o fd 3 — a convenção do preview1:
 * 0/1/2 = stdin/stdout/stderr, 3+ = preopens) e RECUSA o path absoluto
 * (errno 63 = EPERM — medido no wasmtime 49.0.2). Por isso o runtime tira o
 * "/" inicial: o path do Kof resolve dentro da sandbox que o host concedeu
 * (o preopen = a raiz da sandbox).
 *
 * <p>CONTRATO (o mesmo da matriz do runtime JVM/nativo, medida no bytecode
 * gerado: {@code kof_io_file_exists(String)->int},
 * {@code kof_io_read_text(String)->String} — null quando o arquivo não existe
 * — e {@code kof_io_write_text(String,String)->int}):
 * <ul>
 *   <li>{@code exists}/{@code isFile}/{@code isDirectory} -> Bool;</li>
 *   <li>{@code readText} -> String? (o 0 = null — o handle i32 do String,
 *       igual ao {@code map.get} que devolve null);</li>
 *   <li>{@code writeText} -> Bool (1 = gravou, 0 = recusou).</li>
 * </ul>
 *
 * <p>O path e o conteudo chegam como HANDLE de String (o layout KofString =
 * {@code [len i32][bytes]} no bump heap) — os bytes do path sao passados
 * direto ao {@code path_open} (o preview1 leva ptr+len, sem NUL), e o
 * conteudo gravado sai dos bytes do handle.
 */
final class WasmWasiFs {

    private WasmWasiFs() {
    }

    /** os nomes de call do Kof (o fecho do emit os passa como intrinsics). */
    static final Set<String> NAMES = Set.of(
            "kof_io_file_exists", "kof_io_file_is_file", "kof_io_file_is_dir",
            "kof_io_read_text", "kof_io_write_text");

    /** as faces de arquivo que o programa usa (o import so entra se usada). */
    static Set<String> usedFaces(List<IRMethod> methods) {
        Set<String> used = new LinkedHashSet<>();
        for (IRMethod m : methods) {
            for (IRBasicBlock bb : m.basicBlocks()) {
                for (KofOperation op : bb.operations()) {
                    if (op instanceof KofCall kc && NAMES.contains(kc.methodName())) {
                        used.add(kc.methodName());
                    }
                }
            }
        }
        return used;
    }

    /**
     * A face de DISPATCH: o nome do call do Kof -> o nome do runtime. O
     * nome do runtime começa com "kof." — a lei do GC-2 (as funcs de runtime
     * alocam pelo {@code heapAllocNG}, que NUNCA coleta).
     */
    static String runtimeName(String kofName) {
        return switch (kofName) {
            case "kof_io_file_exists" -> "kof.io.exists";
            case "kof_io_file_is_file" -> "kof.io.isFile";
            case "kof_io_file_is_dir" -> "kof.io.isDir";
            case "kof_io_read_text" -> "kof.io.readText";
            case "kof_io_write_text" -> "kof.io.writeText";
            default -> throw new IllegalArgumentException("face de io desconhecida: " + kofName);
        };
    }

    /** o rastro do RESULTADO (o "shape" que o resto do emit usa). */
    static String resultShape(String kofName) {
        return "kof_io_read_text".equals(kofName) ? "nullablestring" : "bool";
    }

    // ── os imports do host (preview1) ──────────────────────────────────────

    /** {@code path_open(fd, dirflags, path, path_len, oflags, rights_base,
     *  rights_inheriting, fdflags, opened_fd)} -> errno. */
    static WasmImport pathOpen() {
        return WasmImport.wasi("path_open",
                List.of(0x7f, 0x7f, 0x7f, 0x7f, 0x7f, 0x7e, 0x7e, 0x7f, 0x7f),
                List.of(0x7f));
    }

    /** {@code path_filestat_get(fd, flags, path, path_len, buf)} -> errno. */
    static WasmImport pathFilestatGet() {
        return WasmImport.wasi("path_filestat_get",
                List.of(0x7f, 0x7f, 0x7f, 0x7f, 0x7f), List.of(0x7f));
    }

    /** {@code fd_read(fd, iovs, iovs_len, nread)} -> errno. */
    static WasmImport fdRead() {
        return WasmImport.wasi("fd_read",
                List.of(0x7f, 0x7f, 0x7f, 0x7f), List.of(0x7f));
    }

    /** {@code fd_write(fd, iovs, iovs_len, nwritten)} -> errno. */
    static WasmImport fdWrite() {
        return WasmImport.wasi("fd_write",
                List.of(0x7f, 0x7f, 0x7f, 0x7f), List.of(0x7f));
    }

    /** {@code fd_close(fd)} -> errno. */
    static WasmImport fdClose() {
        return WasmImport.wasi("fd_close", List.of(0x7f), List.of(0x7f));
    }

    // ── os rights do preview1 (as mascaras MEDIDAS no host) ────────────────

    /** A face de LEITURA (medida no wasmtime, errno 0): FD_READ|FD_SEEK|
     *  FD_TELL|FD_FILESTAT_GET = 0x200026. */
    private static final long RIGHTS_READ = 2097190L;

    /** A face de ESCRITA (medida: o arquivo nasce com o conteudo): a de
     *  leitura + FD_WRITE + PATH_CREATE_FILE + PATH_OPEN = 0x202C06. */
    private static final long RIGHTS_WRITE =
            RIGHTS_READ | (1L << 6) | (1L << 10) | (1L << 13);

    /** O_CREAT|O_TRUNC — o preview1 nao tem O_APPEND como flag de path_open. */
    private static final int OFLAGS_CREATE_TRUNC = 1 | 8;

    /** O preopen: o preview1 abre os preopens a partir do fd 3. */
    private static final int PREOPEN_FD = 3;

    // ── o runtime ──────────────────────────────────────────────────────────

    /** {@code kof.io.exists(path)} -> 1|0. */
    static WasmFunc exists() {
        return statFace("kof.io.exists", 0);
    }

    /** {@code kof.io.isFile(path)} -> 1|0 (o filetype 4 = REGULAR_FILE). */
    static WasmFunc isFile() {
        return statFace("kof.io.isFile", 4);
    }

    /** {@code kof.io.isDirectory(path)} -> 1|0 (o filetype 3 = DIRECTORY). */
    static WasmFunc isDirectory() {
        return statFace("kof.io.isDir", 3);
    }

    /**
     * A face de STAT: o errno do {@code path_filestat_get} + (quando o
     * filetype importa) o byte do filetype no offset 16 do {@code filestat}
     * (medido: dev@0, ino@8, filetype@16, nlink@24, size@32).
     *
     * <p>Locals: 0=path(handle), 1=errno, 2=ptr, 3=len, 4=res.
     */
    private static WasmFunc statFace(String name, int filetype) {
        List<WasmInstr> b = new ArrayList<>();
        pathPrologue(b, 2, 3);
        // path_filestat_get(preopen, 0, ptr, len, statbuf) -> errno
        b.add(new WasmInstr.Const(0, PREOPEN_FD));
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "ptr"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "len"));
        b.add(new WasmInstr.Const(0, WasmStdoutRuntime.SCRATCH_FS_STAT));
        b.add(new WasmInstr.Call("path_filestat_get"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 1, "errno"));
        // o errno == 0 ?
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "errno"));
        b.add(new WasmInstr.Simple(0x45, "i32.eqz"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        if (filetype > 0) {
            b.add(new WasmInstr.Const(0, WasmStdoutRuntime.SCRATCH_FS_STAT + 16));
            b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD8U, 0));
            b.add(new WasmInstr.Const(0, filetype));
            b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        } else {
            b.add(new WasmInstr.Const(0, 1));
        }
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 4, "res"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x40));
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 4, "res"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "res"));
        return new WasmFunc(name, List.of(0x7f), List.of(0x7f),
                List.of(0x7f, 0x7f, 0x7f, 0x7f), b);
    }

    /**
     * {@code kof.io.readText(path) -> handle|0}.
     *
     * <p>Locals: 0=path, 1=errno, 2=ptr, 3=len, 4=size, 5=h, 6=res.
     */
    static WasmFunc readText() {
        List<WasmInstr> b = new ArrayList<>();
        pathPrologue(b, 2, 3);
        // path_filestat_get(preopen, 0, ptr, len, statbuf) -> errno
        b.add(new WasmInstr.Const(0, PREOPEN_FD));
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "ptr"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "len"));
        b.add(new WasmInstr.Const(0, WasmStdoutRuntime.SCRATCH_FS_STAT));
        b.add(new WasmInstr.Call("path_filestat_get"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 1, "errno"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "errno"));
        b.add(new WasmInstr.Simple(0x45, "i32.eqz"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        // path_open(preopen, 0, ptr, len, oflags=0, rights, inheriting, fdflags, &fd)
        b.add(new WasmInstr.Const(0, PREOPEN_FD));
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "ptr"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "len"));
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Const(1, RIGHTS_READ));
        b.add(new WasmInstr.Const(1, 0L));
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Const(0, WasmStdoutRuntime.SCRATCH_FS_FD));
        b.add(new WasmInstr.Call("path_open"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 1, "errno"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "errno"));
        b.add(new WasmInstr.Simple(0x45, "i32.eqz"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        // o size = i64 no offset 32 do filestat -> i32
        b.add(new WasmInstr.Const(0, WasmStdoutRuntime.SCRATCH_FS_STAT + 32));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 4, "size"));
        // o handle = heapAlloc(size+5) ([len][bytes][\n])
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "size"));
        b.add(new WasmInstr.Const(0, 5));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Call("kof.heapAlloc"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 5, "h"));
        // o iovec = {buf: h+4, len: size}
        b.add(new WasmInstr.Const(0, WasmStdoutRuntime.SCRATCH_FS_IOV));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 5, "h"));
        b.add(new WasmInstr.Const(0, 4));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));
        b.add(new WasmInstr.Const(0, WasmStdoutRuntime.SCRATCH_FS_IOV + 4));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "size"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));
        // fd_read(fd, iov, 1, &nread) -> errno
        b.add(new WasmInstr.Const(0, WasmStdoutRuntime.SCRATCH_FS_FD));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        b.add(new WasmInstr.Const(0, WasmStdoutRuntime.SCRATCH_FS_IOV));
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Const(0, WasmStdoutRuntime.SCRATCH_FS_NREAD));
        b.add(new WasmInstr.Call("fd_read"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 1, "errno"));
        // [h] = nread (o len real dos bytes lidos)
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 5, "h"));
        b.add(new WasmInstr.Const(0, WasmStdoutRuntime.SCRATCH_FS_NREAD));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));
        // fecha o fd
        b.add(new WasmInstr.Const(0, WasmStdoutRuntime.SCRATCH_FS_FD));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        b.add(new WasmInstr.Call("fd_close"));
        b.add(new WasmInstr.Simple(0x1a, "drop"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 5, "h"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 6, "res"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x40));
        b.add(new WasmInstr.Const(0, 0));                        // null
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 6, "res"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x40));
        b.add(new WasmInstr.Const(0, 0));                        // null
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 6, "res"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 6, "res"));
        return new WasmFunc("kof.io.readText", List.of(0x7f), List.of(0x7f),
                List.of(0x7f, 0x7f, 0x7f, 0x7f, 0x7f, 0x7f), b);
    }

    /**
     * {@code kof.io.writeText(path, content) -> 1|0}.
     *
     * <p>Locals: 0=path, 1=content, 2=errno, 3=ptr, 4=len, 5=clen, 6=res.
     */
    static WasmFunc writeText() {
        List<WasmInstr> b = new ArrayList<>();
        pathPrologue(b, 3, 4);
        // o conteudo: os bytes do handle (o len no [content])
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "content"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 5, "clen"));
        // path_open(preopen, 0, ptr, len, CREAT|TRUNC, rights, inheriting, fdflags, &fd)
        b.add(new WasmInstr.Const(0, PREOPEN_FD));
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "ptr"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "len"));
        b.add(new WasmInstr.Const(0, OFLAGS_CREATE_TRUNC));
        b.add(new WasmInstr.Const(1, RIGHTS_WRITE));
        b.add(new WasmInstr.Const(1, 0L));
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Const(0, WasmStdoutRuntime.SCRATCH_FS_FD));
        b.add(new WasmInstr.Call("path_open"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "errno"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "errno"));
        b.add(new WasmInstr.Simple(0x45, "i32.eqz"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        // o iovec = {buf: content+4, len: clen}
        b.add(new WasmInstr.Const(0, WasmStdoutRuntime.SCRATCH_FS_IOV));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "content"));
        b.add(new WasmInstr.Const(0, 4));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));
        b.add(new WasmInstr.Const(0, WasmStdoutRuntime.SCRATCH_FS_IOV + 4));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 5, "clen"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));
        // fd_write(fd, iov, 1, &nwritten) -> errno
        b.add(new WasmInstr.Const(0, WasmStdoutRuntime.SCRATCH_FS_FD));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        b.add(new WasmInstr.Const(0, WasmStdoutRuntime.SCRATCH_FS_IOV));
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Const(0, WasmStdoutRuntime.SCRATCH_FS_NREAD));
        b.add(new WasmInstr.Call("fd_write"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "errno"));
        // fecha o fd
        b.add(new WasmInstr.Const(0, WasmStdoutRuntime.SCRATCH_FS_FD));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        b.add(new WasmInstr.Call("fd_close"));
        b.add(new WasmInstr.Simple(0x1a, "drop"));
        // o errno do write == 0 ?
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "errno"));
        b.add(new WasmInstr.Simple(0x45, "i32.eqz"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 6, "res"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x40));
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 6, "res"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 6, "res"));
        return new WasmFunc("kof.io.writeText", List.of(0x7f, 0x7f), List.of(0x7f),
                List.of(0x7f, 0x7f, 0x7f, 0x7f, 0x7f), b);
    }

    /**
     * O PROLOG do path: o handle (local 0) -> (ptr, len) nos locals dados,
     * tirando o "/" inicial (o preview1 recusa o path absoluto — medido:
     * errno 63 = EPERM; o "/" inicial = a raiz da sandbox).
     */
    private static void pathPrologue(List<WasmInstr> b, int ptrIdx, int lenIdx) {
        // ptr = path + 4 (o [len i32] do KofString fica no handle)
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "path"));
        b.add(new WasmInstr.Const(0, 4));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, ptrIdx, "ptr"));
        // len = [path]
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "path"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, lenIdx, "len"));
        // se o primeiro byte e' '/', pula (o "/" inicial = a raiz da sandbox)
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, ptrIdx, "ptr"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD8U, 0));
        b.add(new WasmInstr.Const(0, '/'));
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, ptrIdx, "ptr"));
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, ptrIdx, "ptr"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, lenIdx, "len"));
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, lenIdx, "len"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
    }
}
