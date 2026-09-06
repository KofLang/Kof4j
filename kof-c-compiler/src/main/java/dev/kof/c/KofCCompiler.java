package dev.kof.c;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * KofCcompiler — native-only C subset compiler.
 * Input: .c file with subset grammar.
 * Output: ELF64 executable via GAS + LD.
 * No JVM target.
 */
public final class KofCCompiler {

    public record CompileResult(boolean success, String diagnostics, Path binary) {}

    public static CompileResult compile(Path cFile, Path outDir) throws IOException {
        String src = Files.readString(cFile);
        var lexer = new KofCLexer(src);
        List<KofCToken> toks = lexer.lex();
        var parser = new KofCParser(toks);
        var prog = parser.parseProgram();

        // basic validation: need main
        boolean hasMain = prog.funcs().stream().anyMatch(f -> f.name().equals("main"));
        if (!hasMain) {
            return new CompileResult(false, "missing main() function", null);
        }

        var emitter = new KofCEmitter(prog);
        String asm = emitter.emit();

        Files.createDirectories(outDir);
        Path sFile = outDir.resolve("kofc.s");
        Files.writeString(sFile, asm);

        Path oFile = outDir.resolve("kofc.o");
        Path bin = outDir.resolve(cFile.getFileName().toString().replaceFirst("\\.c$", ""));
        if (bin.toString().endsWith(".c")) bin = outDir.resolve("a.out");
        // also ensure we produce file without extension for execution
        if (!bin.getFileName().toString().contains(".")) {
            // keep as is
        } else {
            bin = outDir.resolve("kofc_bin");
        }

        // as
        ProcessBuilder pbAs = new ProcessBuilder("as", "--64", "-o", oFile.toString(), sFile.toString());
        pbAs.redirectErrorStream(true);
        Process pAs = pbAs.start();
        String asOut = new String(pAs.getInputStream().readAllBytes());
        try { pAs.waitFor(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        if (pAs.exitValue() != 0) {
            return new CompileResult(false, "as failed: " + asOut + "\n" + asm, null);
        }

        // ld - use gcc for easier linking with runtime? Use ld directly for bare.
        // Use ld -o bin -e _start oFile
        ProcessBuilder pbLd = new ProcessBuilder("ld", "-o", bin.toString(), "-e", "_start", oFile.toString());
        pbLd.redirectErrorStream(true);
        Process pLd = pbLd.start();
        String ldOut = new String(pLd.getInputStream().readAllBytes());
        try { pLd.waitFor(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        if (pLd.exitValue() != 0) {
            // fallback to gcc
            ProcessBuilder pbGcc = new ProcessBuilder("gcc", "-nostdlib", "-o", bin.toString(), oFile.toString());
            pbGcc.redirectErrorStream(true);
            Process pGcc = pbGcc.start();
            String gccOut = new String(pGcc.getInputStream().readAllBytes());
            try { pGcc.waitFor(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            if (pGcc.exitValue() != 0) {
                return new CompileResult(false, "ld failed: " + ldOut + "\ngcc failed: " + gccOut + "\n" + asm, null);
            }
        }
        // chmod +x
        bin.toFile().setExecutable(true);
        return new CompileResult(true, "", bin);
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: KofCCompiler <file.c> [-o outDir]");
            System.exit(1);
        }
        Path cFile = Path.of(args[0]);
        Path outDir = args.length > 1 ? Path.of(args[1]) : Files.createTempDirectory("kofc-out");
        var res = compile(cFile, outDir);
        if (!res.success()) {
            System.err.println(res.diagnostics());
            System.exit(1);
        }
        System.out.println("built " + res.binary());
    }
}
