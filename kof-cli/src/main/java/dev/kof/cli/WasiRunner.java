package dev.kof.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The WASI host runner of {@code kof run --target wasi} (TIER 15, unit 15.3
 * + the phase-4 capabilities — issue #776).
 *
 * <p>WHY THIS EXISTS: a WASI module is a COMMAND module (it exports
 * {@code _start}), so a WASI host can run it directly — the same shape the
 * native target has (an ELF that runs). Until now the CLI refused
 * {@code --target wasi} honestly and pointed at {@code kof build --target
 * wasi} plus a manual runtime invocation; the user had to know the host's
 * command line. This runner closes that loop — the same program runs with
 * {@code kof run --target wasi} on any machine that has a WASI host.
 *
 * <p>THE HOSTS (in precedence order): {@code wasmtime} (the reference host —
 * {@code KOF_WASM_HOME}/bin or the PATH, the convention of
 * {@code scripts/provision-wasmtime.sh}) and {@code node} (whose
 * {@code node:wasi} is a real preview1 host — the same oracle the
 * {@code WasmWasiNodeHostTest} uses). With NEITHER present the runner refuses
 * honestly and names the missing host — never a silent fallback (R6).
 *
 * <p>THE PREOPEN: the WASI filesystem faces live under the preopened fd 3, so
 * the runner preopens the CURRENT WORKING DIRECTORY — the desktop app's
 * sandbox root is where the user launched it. MEASURED: without a preopen the
 * {@code kof.io} faces fail SILENTLY (the file is never created and
 * {@code readText} returns null); with the cwd preopened the file is really
 * created and read back.
 */
final class WasiRunner {

    private WasiRunner() {
    }

    /** The wasmtime binary, or null. {@code KOF_WASM_HOME} wins over the PATH
     *  (the provisioned prefix of {@code scripts/provision-wasmtime.sh} — whose
     *  DEFAULT prefix is {@code $HOME/.local/share/kof-wasm}, the same
     *  default the provisioning script uses, so a provisioned host is found
     *  without exporting anything). */
    static Path wasmtime() {
        String seam = System.getProperty("kof.wasi.host");
        if (seam != null) {
            // seam de teste: o valor e' o NOME do host procurado; um valor
            // vazio desliga a busca (o caminho da recusa honesta)
            if (seam.isBlank()) return null;
            Path p = Path.of(seam);
            if (Files.isExecutable(p)) return p;
            return onPath(seam);
        }
        String home = System.getenv("KOF_WASM_HOME");
        if (home != null && !home.isBlank()) {
            Path p = Path.of(home, "bin", "wasmtime");
            if (Files.isExecutable(p)) return p;
        } else {
            String userHome = System.getProperty("user.home");
            if (userHome != null && !userHome.isBlank()) {
                Path p = Path.of(userHome, ".local", "share", "kof-wasm", "bin", "wasmtime");
                if (Files.isExecutable(p)) return p;
            }
        }
        return onPath("wasmtime");
    }

    /** The node binary, or null. {@code KOF_NODE_HOME} wins over the PATH. */
    static Path node() {
        String seam = System.getProperty("kof.wasi.host");
        if (seam != null) {
            // o seam aponta para UM host (o wasmtime); o node e' o segundo
            // oraculo e so e procurado quando o seam nao desliga a busca
            if (seam.isBlank()) return null;
            return null;
        }
        String home = System.getenv("KOF_NODE_HOME");
        if (home != null && !home.isBlank()) {
            Path p = Path.of(home, "node");
            if (Files.isExecutable(p)) return p;
        }
        return onPath("node");
    }

    /** `true` se algum host WASI pode rodar o modulo. */
    static boolean hostAvailable() {
        return wasmtime() != null || node() != null;
    }

    /** O nome do host disponivel (para a mensagem honesta), ou null. */
    static String hostName() {
        if (wasmtime() != null) return "wasmtime";
        if (node() != null) return "node";
        return null;
    }

    private static Path onPath(String name) {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) return null;
        for (String dir : path.split(java.io.File.pathSeparator)) {
            if (dir.isBlank()) continue;
            Path p = Path.of(dir, name);
            if (Files.isExecutable(p)) return p;
        }
        return null;
    }

    /**
     * Runs the WASI module under the available host, with the cwd preopened
     * and the program's arguments forwarded. The child's exit code becomes the
     * CLI's (the same discipline the native target uses).
     *
     * <p>THE ARGV LAW (measured against the JVM oracle): the Kof program sees
     * the SAME arguments it would see on the JVM — {@code args[0]} is the
     * FIRST argument the user typed, NOT the program name. The wasmtime
     * convention already provides that (the module path becomes argv[0]), so
     * the runner forwards the user's arguments verbatim; the node host is
     * configured the same way (the WASI config's {@code args} array starts at
     * the user's first argument).
     *
     * @param wasm    the emitted {@code Main.wasm}
     * @param programArgs the arguments the Kof program sees (args[0] = the
     *                    user's first argument, as the JVM's
     *                    {@code main(String[])})
     * @param tempDir the compile temp dir (cleaned by the caller)
     */
    static void run(Path wasm, List<String> programArgs, Path tempDir) throws IOException {
        Path host = wasmtime();
        if (host != null) {
            runUnderWasmtime(host, wasm, programArgs, tempDir);
            return;
        }
        Path node = node();
        if (node != null) {
            runUnderNode(node, wasm, programArgs, tempDir);
            return;
        }
        throw new HostAbsent();
    }

    /** Nenhum host WASI neste host — o chamador recusa honestamente (R6). */
    static final class HostAbsent extends IOException {
        HostAbsent() {
            super("no WASI host found");
        }
    }

    /**
     * {@code wasmtime run --dir <cwd> <module> <args...>} — the preopen is
     * the cwd (the app's sandbox root). The program's own args follow the
     * module path (wasmtime's convention: the module path IS argv[0]).
     */
    private static void runUnderWasmtime(Path host, Path wasm, List<String> programArgs,
                                         Path tempDir) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add(host.toString());
        cmd.add("run");
        cmd.add("--dir");
        cmd.add(".");
        cmd.add(wasm.toString());
        for (String a : programArgs) cmd.add(a);
        KofCliSupport.executeProcess(cmd, tempDir);
    }

    /**
     * {@code node <script> <module> <args...>} — the script drives
     * {@code node:wasi} (a real preview1 host) with the cwd preopened as
     * {@code '.'}; the program's args are forwarded through the WASI config.
     */
    private static void runUnderNode(Path node, Path wasm, List<String> programArgs,
                                     Path tempDir) throws IOException {
        Path script = Path.of(System.getProperty("java.io.tmpdir"),
                "kof-wasi-run-" + ProcessHandle.current().pid() + ".mjs");
        StringBuilder sb = new StringBuilder();
        sb.append("import fs from 'node:fs';\n");
        sb.append("import path from 'node:path';\n");
        sb.append("import { WASI } from 'node:wasi';\n");
        sb.append("const modulePath = process.argv[2];\n");
        sb.append("const args = process.argv.slice(3);\n");
        sb.append("const wasi = new WASI({\n");
        sb.append("  version: 'preview1',\n");
        sb.append("  args: ['program', ...args],\n");
        sb.append("  env: process.env,\n");
        sb.append("  preopens: { '.': path.resolve('.') },\n");
        sb.append("});\n");        sb.append("const mod = await WebAssembly.compile(fs.readFileSync(modulePath));\n");
        sb.append("const inst = await WebAssembly.instantiate(mod, { wasi_snapshot_preview1: wasi.wasiImport });\n");
        sb.append("wasi.start(inst);\n");
        Files.writeString(script, sb.toString(), StandardCharsets.UTF_8);
        List<String> cmd = new ArrayList<>();
        cmd.add(node.toString());
        // #1193: o node emite um `ExperimentalWarning: WASI is an experimental
        // feature` no stderr; o CLI roda com stderr FUNDIDO no stdout
        // (redirectErrorStream no caminho do `kof run`), entao o aviso do HOST
        // vazava para a saida do PROGRAMA e quebrava a paridade argv com o
        // oraculo JVM (medido no CI da 0.8.1, run 45089343792: 1F em
        // CmdRunWasiE2ETest; o host de dev tem wasmtime e nao pegava). O
        // diagnostico do host nao pertence ao stdout do programa: `--no-warnings`
        // suprime SO os avisos do node (medido: suprime o ExperimentalWarning,
        // sem efeito na saida do modulo).
        cmd.add("--no-warnings");
        cmd.add(script.toString());
        cmd.add(wasm.toString());
        for (String a : programArgs) cmd.add(a);
        try {
            KofCliSupport.executeProcess(cmd, tempDir);
        } finally {
            try {
                Files.deleteIfExists(script);
            } catch (IOException ignored) {
                // o script e' temporario; a falha de limpeza nao muda o resultado
            }
        }
    }
}
