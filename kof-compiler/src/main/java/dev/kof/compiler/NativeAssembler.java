package dev.kof.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * FASE 3 (REFACTOR-500): invocação do as/ld nativo (x86_64 host).
 * Extraído verbatim de NativeBackend (assemble/runCommand/ToolchainMissing);
 * os flags de link (db/mysql/concurrency) viram parâmetros.
 */
final class NativeAssembler {

    private NativeAssembler() {}

    /** Toolchain ausente (binário não encontrado) — gracioso: mantém asm,
     *  assumeToolchain() pula o teste. NÃO confundir com falha de as/ld. */
    static final class ToolchainMissing extends IOException {
        ToolchainMissing(String m) { super(m); }
    }

    static void assemble(Path asmFile, Path binFile, boolean usesDb, boolean usesMysql,
                   boolean usesConcurrency) throws IOException {
        Path objFile = asmFile.resolveSibling(asmFile.getFileName() + ".o");
        System.err.println("NativeBackend: assembling " + asmFile);
        try {
            runCommand(new String[]{"as", "-o", objFile.toString(), asmFile.toString()}, "as");
        } catch (IOException e) {
            System.err.println("NativeBackend: as failed: " + e.getMessage());
            throw e;
        }
        // Native always needs dynamic linker + libc now (printf for float, db optionally)
        // to keep single codegen path; plain integer programs still work via ld+ld.so.
        boolean needsDynamic = true;
        String os = System.getProperty("os.name", "").toLowerCase();
        if (needsDynamic && os.contains("linux")) {
            java.util.List<String> cmdL = new java.util.ArrayList<>(java.util.Arrays.asList(
                    "ld", "-o", binFile.toString(), objFile.toString(),
                    "-dynamic-linker", "/lib64/ld-linux-x86-64.so.2", "-lc"));
            if (usesDb) {
                cmdL.add(usesMysql ? "-l:libsqlite3.so.0" : "-l:libsqlite3.so.0");
                if (usesMysql) cmdL.add("-l:libmariadb.so.3");
            }
            if (usesConcurrency) cmdL.add("-l:libpthread.so.0");
            runCommand(cmdL.toArray(new String[0]), "ld");
        } else {
            if (usesDb) {
                String os2 = System.getProperty("os.name", "").toLowerCase();
                if (os2.contains("linux")) {
                    String[] extra = usesMysql
                            ? new String[]{"-l:libsqlite3.so.0", "-l:libmariadb.so.3"}
                            : new String[]{"-l:libsqlite3.so.0"};
                    String[] cmd = new String[7 + extra.length];
                    cmd[0] = "ld"; cmd[1] = "-o"; cmd[2] = binFile.toString(); cmd[3] = objFile.toString();
                    cmd[4] = "-dynamic-linker"; cmd[5] = "/lib64/ld-linux-x86-64.so.2"; cmd[6] = "-lc";
                    System.arraycopy(extra, 0, cmd, 7, extra.length);
                    runCommand(cmd, "ld");
                } else {
                    runCommand(new String[]{"ld", "-o", binFile.toString(), objFile.toString()}, "ld");
                }
            } else {
                runCommand(new String[]{"ld", "-o", binFile.toString(), objFile.toString()}, "ld");
            }
        }
        Files.deleteIfExists(objFile);
        if (System.getenv("KOF_KEEP_ASM") == null) Files.deleteIfExists(asmFile);
        binFile.toFile().setExecutable(true);
    }

    static void runCommand(String[] cmd, String name) throws IOException {
        Process p;
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            p = pb.start();
        } catch (IOException e) {
            throw new ToolchainMissing(name + " not available: " + e.getMessage());
        }
        try {
            String output = new String(p.getInputStream().readAllBytes());
            p.waitFor();
            if (p.exitValue() != 0) {
                throw new IOException(name + " failed (exit " + p.exitValue() + "): " + output);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(name + " interrupted");
        }
    }
}
