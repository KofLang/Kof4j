package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #934 — o marshaling `String`→`char*` de um `extern` NÃO depende da ordem em
 * que o arquivo da declaração é mesclado no módulo.
 *
 * <p>Repro do issue (3 arquivos): `a_probe.kf` chama `access`, `b_ffi.kf`
 * declara o `extern`, `main.kf` usa os dois. Pré-fix, o call-site do arquivo
 * que mescla ANTES da decl perdia o gate do dispatcher
 * (`ExpressionMethodCallLowerer` só entra no caminho FFI se
 * `driver.externSignatures.containsKey` — e a tabela era preenchida no laço de
 * lowerToIR, um arquivo por vez): no JVM o call-site caía no bare-call e
 * emitia `invokestatic Default/Main.access:(Ljava/lang/String;I)Object`
 * fantasma (VerifyError no load, @6 ireturn sobre Object); no Native x86 o
 * handle de String CRU ia no rdi do `call access` (a libc lia o HEADER do
 * objeto como path → ENOENT silencioso, `-1` em vez de `0`). O mesmo vale num
 * ÚNICO arquivo com o call-site antes da decl (a mesma raiz, sem segundo
 * arquivo).
 *
 * <p>Oráculo é o JVM (`--enable-native-access` para o FFM do `kof_ffi`); as
 * faces x86_64 executam o binário nativo (as/ld). `access("/tmp", 0)` = 0 é
 * determinístico em Linux (o diretório existe e o modo 0 = F_OK).
 */
class MultiFileExternOrderE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String PROBE = """
            class Probe {
                Int check() {
                    return access("/tmp", 0)
                }
            }
            """;

    private static final String FFI = """
            extern "libc.so.6" access(String path, Int mode): Int
            """;

    private static final String MAIN = """
            main() {
                println("from main.kf:  " + access("/tmp", 0))
                println("from a_probe:  " + Probe().check())
            }
            """;

    private static final String GOLDEN = "from main.kf:  0\nfrom a_probe:  0";

    private static final String SINGLE_FILE_LATE_EXTERN = """
            main() {
                println("late decl:  " + access("/tmp", 0))
            }

            extern "libc.so.6" access(String path, Int mode): Int
            """;

    private static final String SINGLE_FILE_GOLDEN = "late decl:  0";

    /** O módulo de 3 arquivos do issue, na ordem alfabética de merge. */
    private List<Path> writeRepro(Path root) throws Exception {
        List<Path> sources = new ArrayList<>();
        sources.add(write(root, "a_probe.kf", PROBE));
        sources.add(write(root, "b_ffi.kf", FFI));
        sources.add(write(root, "main.kf", MAIN));
        return sources;
    }

    private Path write(Path root, String name, String src) throws Exception {
        Path f = root.resolve(name);
        Files.createDirectories(f.getParent());
        Files.writeString(f, src);
        return f;
    }

    private List<Path> writeSingle(Path root) throws Exception {
        List<Path> sources = new ArrayList<>();
        sources.add(write(root, "Main.kf", SINGLE_FILE_LATE_EXTERN));
        return sources;
    }

    private Path compile(Path root, List<Path> sources, Target target) throws Exception {
        Path out = root.resolve("out");
        CompilationResult r = driver.compileSources(sources, out, target, root);
        assertTrue(r.success(), () -> target + " compile: " + r.diagnostics().getDiagnostics());
        return out;
    }

    @Test
    void externDeclInALaterFileMarshalsTheStringOnTheJvmOracle(@TempDir Path tmp2) throws Exception {
        Path out = compile(tmp2, writeRepro(tmp2), Target.JVM);
        assertEquals(GOLDEN, runJvm(out),
                "#934 JVM: o call-site no arquivo que mescla ANTES da decl extern "
                        + "emite o MESMO caminho FFI do que mescla depois");
    }

    @Test
    void externDeclInALaterFileMarshalsTheStringOnNativeX86(@TempDir Path tmp2) throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        Path out = compile(tmp2, writeRepro(tmp2), Target.NATIVE);
        assertEquals(GOLDEN, runNative(out.resolve("Default").resolve("Main")),
                "#934 x86_64: o handle de String cru no rdi era ENOENT silencioso "
                        + "(a marshal `leaq 24(%rdi)` é obrigatória nos DOIS call-sites)");
    }

    @Test
    void singleFileLateExternDeclMarshalsTheStringOnTheJvmOracle(@TempDir Path tmp2) throws Exception {
        Path out = compile(tmp2, writeSingle(tmp2), Target.JVM);
        assertEquals(SINGLE_FILE_GOLDEN, runJvm(out),
                "#934 (mesma raiz, um arquivo): call-site antes da decl no MESMO "
                        + "arquivo também baixa pelo caminho FFI");
    }

    @Test
    void singleFileLateExternDeclMarshalsTheStringOnNativeX86(@TempDir Path tmp2) throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        Path out = compile(tmp2, writeSingle(tmp2), Target.NATIVE);
        assertEquals(SINGLE_FILE_GOLDEN, runNative(out.resolve("Default").resolve("Main")),
                "#934 (mesma raiz, um arquivo): o caminho FFI não depende da ordem");
    }

    private String runJvm(Path out) throws Exception {
        Process p = new ProcessBuilder("java", "--enable-native-access=ALL-UNNAMED",
                "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                "-cp", out.toString(), "Default.Main").redirectErrorStream(true).start();
        String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertTrue(p.waitFor(120, TimeUnit.SECONDS), "jvm oracle não terminou em 120s");
        assertEquals(0, p.exitValue(), "jvm oracle exit: " + o);
        return o;
    }

    private String runNative(Path bin) throws Exception {
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertTrue(p.waitFor(120, TimeUnit.SECONDS), "native não terminou em 120s");
        assertEquals(0, p.exitValue(), "native exit: " + o);
        return o;
    }

    private static boolean has(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c)
                        .redirectErrorStream(true).start();
                if (p.waitFor() != 0) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }
}
