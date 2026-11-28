package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M-6 (#1135, D-NATIVE-MACOS) — a face {@code kof_io_file_copy_to} pela
 * costura.
 *
 * <p><b>O DEFEITO:</b> a cópia de arquivo usava syscalls crus — o
 * {@code openat} (257), o {@code read} (0), o {@code write} (1), o
 * {@code fchmod} (91), o {@code unlinkat} (280) e o {@code close} (3). No
 * Darwin não há syscall: a face é da costura.
 *
 * <p><b>AS DIFERENÇAS MEDIDAS:</b> o Darwin não tem {@code openat} nem
 * {@code unlinkat} — o corpo usa {@code open} (com o {@code AT_FDCWD}) e
 * escolhe entre {@code unlink} e {@code rmdir} pela flag
 * {@code AT_REMOVEDIR}; o {@code mkdirat}/{@code symlinkat}/
 * {@code readlinkat}/{@code renameat} também não existem lá — são
 * {@code mkdir}/{@code symlink}/{@code readlink}/{@code rename}.
 */
class NativeIoCopySeamE2ETest {

    /** Um programa que COPIA um arquivo (a fatia precisa ser emitida). */
    private static final String COPY_PROGRAM = """
            main() {
                var f = File("/tmp/kof-io-copy-seam-source.txt")
                f.writeText("conteudo")
                var g = File("/tmp/kof-io-copy-seam-target.txt")
                println(g.copyTo("/tmp/kof-io-copy-seam-source.txt"))
                println(g.readText())
                var d = Directory("/tmp/kof-io-seam-dir")
                println(d.create())
                println(d.delete())
                println(f.readBytes().length)
                println(f.readRange(0, 3).length)
            }
            """;

    private static String assembly(Path tmp) throws IOException {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, COPY_PROGRAM);
        String priorKeep = System.getProperty("KOF_KEEP_ASM");
        System.setProperty("KOF_KEEP_ASM", "1");
        try {
            CompilerDriver driver = new CompilerDriver();
            CompilationResult host = driver.compile(src, tmp.resolve("out"), Target.NATIVE);
            assertTrue(host.success(), "the native compile succeeds: "
                    + host.diagnostics().getDiagnostics());
            return Files.readString(tmp.resolve("out/Default/Main.s"));
        } finally {
            if (priorKeep == null) {
                System.clearProperty("KOF_KEEP_ASM");
            } else {
                System.setProperty("KOF_KEEP_ASM", priorKeep);
            }
        }
    }

    /**
     * A cópia de arquivo não faz mais syscall cru — e usa as faces novas da
     * costura ({@code openat}, {@code fchmod}, {@code unlinkat}).
     */
    @Test
    void theFileCopyGoesThroughTheSeam(@TempDir Path tmp) throws IOException {
        String asm = assembly(tmp);
        int at = asm.indexOf("kof_io_file_copy_to:");
        assertTrue(at > 0, "the copy function is in the text");
        String body = asm.substring(at, Math.min(asm.length(), at + 4000));
        for (String line : body.split("\n", -1)) {
            assertFalse(line.trim().equals("syscall"),
                    "no raw syscall left in the file copy:\n" + body);
        }
        assertTrue(body.contains("call kof_plat_openat"),
                "the open goes through the seam:\n" + body);
        assertTrue(body.contains("call kof_plat_read"),
                "the read goes through the seam:\n" + body);
        assertTrue(body.contains("call kof_plat_write"),
                "the write goes through the seam:\n" + body);
        assertTrue(body.contains("call kof_plat_close"),
                "the close goes through the seam:\n" + body);
        assertTrue(body.contains("call kof_plat_fchmod"),
                "the mode goes through the seam:\n" + body);
    }

    /**
     * A face de LEITURA por faixa tambem: o `openat` (257), o `fstat` (5) e o
     * `pread64` (17) passam pela costura — e o `fstat` do Darwin recebe a
     * MESMA normalizacao de layout do `kof_plat_stat` (o `struct stat` do
     * Darwin tem outro layout).
     */
    @Test
    void theReadFacesGoThroughTheSeam(@TempDir Path tmp) throws IOException {
        String asm = assembly(tmp);
        for (String fn : java.util.List.of("kof_io_read_bytes", "kof_io_read_range")) {
            int at = asm.indexOf(fn + ":");
            assertTrue(at > 0, "the function is in the text: " + fn);
            String body = asm.substring(at, Math.min(asm.length(), at + 3000));
            boolean raw = false;
            for (String line : body.split("\n", -1)) {
                if (line.trim().equals("syscall")) raw = true;
            }
            assertFalse(raw, "no raw syscall left in " + fn + ":\n" + body);
        }
        assertTrue(asm.contains("call kof_plat_openat"),
                "the open goes through the seam");
        assertTrue(asm.contains("call kof_plat_fstat_fd"),
                "the fstat goes through the seam");
        assertTrue(asm.contains("call kof_plat_pread"),
                "the pread goes through the seam");
    }

    /**
     * A face de CRIAR/REMOVER diretorio tambem: o `mkdir` (83), o
     * `rmdir` (84) e o `unlink` (87) passam pela costura.
     */
    @Test
    void theDirectoryFacesGoThroughTheSeam(@TempDir Path tmp) throws IOException {
        String asm = assembly(tmp);
        for (String fn : java.util.List.of("kof_io_dir_create", "kof_io_dir_create_dirs",
                "kof_io_dir_delete")) {
            int at = asm.indexOf(fn + ":");
            assertTrue(at > 0, "the function is in the text: " + fn);
            String body = asm.substring(at, Math.min(asm.length(), at + 3000));
            boolean raw = false;
            for (String line : body.split("\n", -1)) {
                if (line.trim().equals("syscall")) raw = true;
            }
            assertFalse(raw, "no raw syscall left in " + fn + ":\n" + body);
        }
        assertTrue(asm.contains("call kof_plat_mkdir"),
                "the mkdir goes through the seam");
        assertTrue(asm.contains("call kof_plat_rmdir"),
                "the rmdir goes through the seam");
    }
}
