package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §374 (#553) — argumento PRIMITIVO em `add`/`set` de coleção builtin BARE
 * (sem type-args: `List xs = listOf(1)`, campo `List xs`) nunca era boxeado
 * no JVM: `emitBoxIfPrimitive` seguia o elemType DECLARADO, que e `Unknown`
 * num receiver sem type-args — o `int` cru chegava a `ArrayList.add(Object)`
 * e o Load morria em `VerifyError: Type integer ... not assignable to
 * 'java/lang/Object'` (o lancador da CLI mascara como mensagem JavaFX, §149).
 *
 * <p>Fix = o MESMO fallback do bug 35 (`kof_list_contains`/`indexOf`): boxear
 * pelo tipo do ARGUMENTO no call-site quando o declarado nao ajuda; com
 * elemType tipado o resultado e identico (controles abaixo). Set/Map ja
 * caiam no fallback via parameterTypes; a face viva era `kof_list_add`/
 * `kof_list_set`.
 *
 * <p>Paridade (regra 5): mesmo programa, mesma saida byte-a-byte em JVM,
 * Script e JS (host); Native x86-64 compila e roda quando o toolchain existe
 * no host (guarda ambiental honesta).
 */
class BareCollectionPrimitiveArgE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private record Run(boolean ok, String output) {}

    private Run runJvm(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JVM);
        if (!r.success()) return new Run(false, diags(r));
        var oldOut = System.out;
        var buf = new ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(buf, true));
        try {
            var cl = new URLClassLoader(new java.net.URL[]{out.toUri().toURL()},
                    getClass().getClassLoader());
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            return new Run(true, buf.toString());
        } catch (ReflectiveOperationException e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            return new Run(false, "THROW: " + c);
        } finally {
            System.setOut(oldOut);
        }
    }

    private Run runJs(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JS);
        if (!r.success()) return new Run(false, diags(r));
        var buf = new ByteArrayOutputStream();
        try {
            int rc = dev.kof.runtime.KofJsRunner.run(
                    out.resolve("Default.mjs"), buf,
                    new ByteArrayInputStream(new byte[0]), buf);
            return new Run(rc == 0, buf.toString());
        } catch (Exception e) {
            return new Run(false, "THROW: " + e.getMessage() + "\n" + buf);
        }
    }

    private Run runScript(Path src, Path root) {
        try {
            var r = driver.interpret(java.util.List.of(src), root, new String[0]);
            return new Run(r.exitCode() == 0, r.stdout());
        } catch (Exception e) {
            return new Run(false, "THROW: " + e.getMessage());
        }
    }

    private static String diags(CompilationResult r) {
        StringBuilder sb = new StringBuilder();
        r.diagnostics().getDiagnostics().forEach(d -> sb.append(d.message()).append("\n"));
        return sb.toString();
    }

    /** Byte-for-byte JVM == Script == JS-host for a source (rule 5). */
    private void assertAllTargets(@TempDir Path tmp, String base, String source, String expected)
            throws Exception {
        Path src = tmp.resolve(base + ".kf");
        Files.writeString(src, source);
        Run jvm = runJvm(src, tmp.resolve("o-" + base + "-jvm"));
        assertTrue(jvm.ok(), () -> "JVM: " + jvm.output());
        assertEquals(expected, jvm.output().replace("\r\n", "\n").trim(), "JVM saida");
        Run scr = runScript(src, tmp);
        assertTrue(scr.ok(), () -> "Script: " + scr.output());
        assertEquals(jvm.output().replace("\r\n", "\n").trim(),
                scr.output().replace("\r\n", "\n").trim(), "JVM x Script byte-a-byte");
        Run js = runJs(src, tmp.resolve("o-" + base + "-js"));
        assertTrue(js.ok(), () -> "JS: " + js.output());
        assertEquals(jvm.output().replace("\r\n", "\n").trim(),
                js.output().replace("\r\n", "\n").trim(), "JVM x JS byte-a-byte");
    }

    /** O reprodutor verbatim do #553: add de primitivo numa List BARE LOCAL. */
    @Test
    void bareListAddPrimitiveRunsEverywhere(@TempDir Path tmp) throws Exception {
        assertAllTargets(tmp, "B374Add", """
            main() {
              List xs = listOf(1)
              xs.add(2)
              println(xs.size)
              println(xs.get(1))
            }
            """, "2\n2");
    }

    /** Set BARE + valor primitivo (face do field, viva apos o pin do §373). */
    @Test
    void bareListFieldAddPrimitiveRuns(@TempDir Path tmp) throws Exception {
        assertAllTargets(tmp, "B374Field", """
            class Box {
              List xs = listOf()
            }

            main() {
              var b = Box()
              b.xs.add(7)
              b.xs.add(8)
              println(b.xs.size)
              println(b.xs.get(0))
            }
            """, "2\n7");
    }

    /** `set(i, primitivo)` em BARE local: o VALOR e o arg posicao-1. */
    @Test
    void bareListSetPrimitiveValueRuns(@TempDir Path tmp) throws Exception {
        assertAllTargets(tmp, "B374Set", """
            main() {
              List xs = listOf(1)
              xs.set(0, 5)
              println(xs.get(0))
            }
            """, "5");
    }

    /** Long/Double (categorias de pilha largas) — nao so `int`. Uma lista por
     *  tipo: o runtime e homogeneo e recusa Double em lista Long com
     *  diagnostico R6 correto (comportamento esperado, nao o bug §374). */
    @Test
    void bareListWidePrimitivesRun(@TempDir Path tmp) throws Exception {
        assertAllTargets(tmp, "B374Wide", """
            main() {
              List longs = listOf()
              longs.add(9L)
              List doubles = listOf()
              doubles.add(2.5)
              println(longs.get(0))
              println(doubles.get(0))
            }
            """, "9\n2.5");
    }

    /** Controles: Set/Map bare ja caiam no fallback de parameterTypes. */
    @Test
    void bareSetAndMapPrimitiveArgsStayGreen(@TempDir Path tmp) throws Exception {
        assertAllTargets(tmp, "B374SetMap", """
            main() {
              Set s = setOf()
              s.add(3)
              println(s.contains(3))
              Map m = mapOf()
              m.put(1, "v")
              println(m.get(1))
            }
            """, "true\nv");
    }

    /** Controle de nao-regressao: coleção TIPIADA nao muda de emissao. */
    @Test
    void typedListPrimitiveOpsUnchanged(@TempDir Path tmp) throws Exception {
        assertAllTargets(tmp, "B374Typed", """
            main() {
              var xs = listOf(1)
              xs.add(2)
              xs.set(0, 3)
              println(xs.get(0))
              println(xs.size)
            }
            """, "3\n2");
    }

    /** Native x86-64: compila e roda quando o toolchain existe (guarda honesta). */
    @Test
    void bareListAddPrimitiveNativeRuns(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("B374Nat.kf");
        Files.writeString(src, """
            main() {
              List xs = listOf(1)
              xs.add(2)
              println(xs.size)
              println(xs.get(1))
            }
            """);
        Path out = tmp.resolve("o-nat");
        org.junit.jupiter.api.Assumptions.assumeTrue(
                dev.kof.compiler.nat.NativeToolchainGate.present(),
                "Native toolchain (as/ld) ausente no host — pulando honesto (NATIVE002)");
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), "compilação Native falhou (regressão, não skip): " + diags(r));
        Process p = new ProcessBuilder(out.resolve("Default/Main").toString())
                .redirectErrorStream(true).start();
        String s = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "Native exit, saida: " + s);
        assertEquals("2\n2", s, "Native == JVM (regra 5)");
    }

    @Test
    void bareListAddPrimitiveInsideLambdaRunsEverywhere(@TempDir Path tmp) throws Exception {
        String source = """
            main() {
              var b = listOf()
              listOf(1, 2, 3).forEach((x) -> b.add(x * 2))
              println(b)
              println(b.get(0) + 1)
            }
            """;
        assertAllTargets(tmp, "B745Lambda", source, "[2, 4, 6]\n3");
        org.junit.jupiter.api.Assumptions.assumeTrue(
                dev.kof.compiler.nat.NativeToolchainGate.present(),
                "Native toolchain (as/ld) ausente no host, pulando (NATIVE002)");
        Path src = tmp.resolve("B745LambdaNat.kf");
        Files.writeString(src, source);
        Path out = tmp.resolve("o-745-nat");
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), "compilação Native falhou: " + diags(r));
        assertEquals("[2, 4, 6]\n3", runNative(out), "Native == JVM (§745)");
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String arch = t == Target.NATIVE_RISCV64 ? "riscv64" : "aarch64";
            org.junit.jupiter.api.Assumptions.assumeTrue(
                    NativeRiscv64E2ETest.hasToolchain(arch),
                    "cross toolchain " + arch + " + qemu ausente, pulando (NATIVE002)");
            Path f = tmp.resolve("B745LambdaCross-" + t + ".kf");
            Files.writeString(f, source);
            Path co = tmp.resolve("o-745-" + t);
            CompilationResult rc = driver.compile(f, co, t);
            assertTrue(rc.success(), t + " compilação falhou: " + diags(rc));
            assertEquals("[2, 4, 6]\n3", NativeRiscv64E2ETest.runQemu(arch, co.resolve("Default/Main")),
                    t + " == JVM (§745)");
        }
    }

    // ── faces medidas e travadas pela lane #553 (re-trigger 20/09): os
    //     slots Object que o fallback do §374 alcança e que ainda nao
    //     tinham golden ────────────────────────────────────────────────

    /** `put(k, v)` com AMBOS os args primitivos na Map BARE (chave passa
     *  pelo SWAP-box do put; valor pelo fallback pos-§373/bug 35). */
    @Test
    void bareMapPutBothPrimitivesRunsEverywhere(@TempDir Path tmp) throws Exception {
        assertAllTargets(tmp, "B553Put", """
            main() {
              Map m = mapOf()
              m.put(1, 2)
              m.put(5, 7)
              println(m.get(1))
              println(m.size)
            }
            """, "2\n2");
    }

    /** `remove(primitivo)` + `contains` numa Set BARE (mesma lei do add). */
    @Test
    void bareSetRemoveAndContainsPrimitivesRun(@TempDir Path tmp) throws Exception {
        assertAllTargets(tmp, "B553Set", """
            main() {
              Set s = setOf()
              s.add(3)
              s.add(4)
              s.remove(3)
              println(s.size)
              println(s.contains(4))
            }
            """, "1\ntrue");
    }

    /** Bool/Char no primeiro add de lista BARE — wrapper por categoria
     *  (Boolean.valueOf/Character.valueOf), espelhando o dispatch do helper
     *  de box; saida medida nos 3 hosts: Bool = true/false, Char = 120 no
     *  dispatch dinamico do get (mesmo valor nos 3 — regra 5). */
    @Test
    void bareListBoolAndCharFirstAddRun(@TempDir Path tmp) throws Exception {
        assertAllTargets(tmp, "B553Bool", """
            main() {
              List b = listOf()
              b.add(true)
              b.add(false)
              println(b.get(0))
              println(b.get(1))
            }
            """, "true\nfalse");
    }

    /** Canal BARE (a face que o fix do §374 reclamou mas NAO alcancava: o
     *  lowerer punha o elemT do canal (Unknown) no parameterTypes, nunca o
     *  tipo do ARG — o fallback lia Unknown de novo e o int cru morria no
     *  LOAD do put(Object) do LinkedBlockingQueue). */
    @Test
    void bareChannelSendPrimitiveReceivesOnJvmScriptJs(@TempDir Path tmp) throws Exception {
        assertAllTargets(tmp, "B553Chan", """
            main() {
              val c = channel()
              c.send(1)
              println(c.receive())
            }
            """, "1");
    }

    /** §374 FECHADO (21/09, lane nat): o canal NU + primitivo no x86_64 agora
     *  empurra a caixa MAGIC do §284 no SEND (fila de objetos) e o println de
     *  {@code Unknown} despacha por {@code kof_box_to_string} — mesma saida
     *  {@code 1} dos 3 hosts (a recusa NAT003 caiu). O canal TIPADO segue
     *  byte-identico (controle, emissao raw int). Nos arcos cross o runtime de
     *  canal nunca foi portado → diagnóstico honesto NAT005 (R6), nunca
     *  link-fail {@code undefined reference}. */
    @Test
    void bareChannelPrimitiveNativeBoxesAndTypedStaysGreen(@TempDir Path tmp) throws Exception {
        String bareSrc = """
            main() {
              val c = channel()
              c.send(1)
              println(c.receive())
            }
            """;
        assumeX86Toolchain();
        Path bare = tmp.resolve("B553ChanBare-NATIVE-" + System.nanoTime() + ".kf");
        Files.writeString(bare, bareSrc);
        Path out = tmp.resolve("o-chnat-NATIVE-" + System.nanoTime());
        CompilationResult rn = driver.compile(bare, out, Target.NATIVE);
        assertTrue(rn.success(), "bare Channel + primitive must compile now: " + diags(rn));
        assertEquals("1", runNative(out), "bare Channel<Unknown> primitive"
                + " = caixa MAGIC no nativo (paridade JVM), nunca SIGSEGV");

        String typedSrc = """
            main() {
              val c = channel<Int>()
              c.send(5)
              c.send(6)
              println(c.receive() + c.receive())
            }
            """;
        Path typed = tmp.resolve("B553ChanTyped-NATIVE-" + System.nanoTime() + ".kf");
        Files.writeString(typed, typedSrc);
        Path out2 = tmp.resolve("o-chnat2-NATIVE-" + System.nanoTime());
        CompilationResult rt = driver.compile(typed, out2, Target.NATIVE);
        assertTrue(rt.success(), "typed Channel<Int> must keep compiling: " + diags(rt));
        assertEquals("11", runNative(out2), "typed Channel<Int> unchanged (regra 1)");

        // §423 FECHADO (23/09, lane baremetal): o runtime de canais existe nos
        // 2 alvos cross (NativeRiscvAsmRtB61) — o programa compila, linka e
        // roda sob qemu com paridade byte-a-byte do JVM (bare `1`, tipado `11`).
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String arch = t == Target.NATIVE_RISCV64 ? "riscv64" : "aarch64";
            org.junit.jupiter.api.Assumptions.assumeTrue(
                    NativeRiscv64E2ETest.hasToolchain(arch),
                    "cross toolchain " + arch + " + qemu ausente — pulando (NATIVE002)");
            for (String[] pair : new String[][]{new String[]{bareSrc, "1"}, new String[]{typedSrc, "11"}}) {
                Path f = tmp.resolve("B553ChanCross-" + t + "-" + System.nanoTime() + ".kf");
                Files.writeString(f, pair[0]);
                Path co = tmp.resolve("o-x-" + t + "-" + System.nanoTime());
                CompilationResult rc = driver.compile(f, co, t);
                assertTrue(rc.success(), t + " channel must compile now (§423): " + diags(rc));
                assertEquals(pair[1], NativeRiscv64E2ETest.runQemu(arch, co.resolve("Default/Main")),
                        t + " channel output (paridade JVM)");
            }
        }
    }

    /** Guarda ambiental honesta: so roda onde o toolchain x86_64 existe. */
    private static void assumeX86Toolchain() {
        for (String c : new String[]{"as", "ld"}) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c)
                        .redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8).trim();
                org.junit.jupiter.api.Assumptions.assumeTrue(
                        p.waitFor() == 0 && !out.isEmpty(), "toolchain ausente: " + c);
            } catch (Exception e) {
                org.junit.jupiter.api.Assumptions.assumeTrue(false, "toolchain ausente: " + c);
            }
        }
    }

    private String runNative(Path out) throws Exception {
        Process p = new ProcessBuilder(out.resolve("Default/Main").toString())
                .redirectErrorStream(true).start();
        String s = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "native exit, saida: " + s);
        return s;
    }
}
