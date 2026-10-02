package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * kof.workflow 2.1.2 MVP golden (universal plan Stage 2, row 2.1; plan
 * docs/development/workflow-plan.md §5 2.1.2 — Q2 minimal surface: job, dag,
 * after, run, Report; plus 2.1.3 face 1 (Q3 additive retry: flow.retry/retryFixed/
exponential/Report.retries). checkpoint/deadLetter/schedule stay in the bundle).
 *
 * The host is pure Kof (`/dev/kof/workflow-host.kf`) injected flat by
 * `CompilerWorkflow` on `import kof.workflow` (DD-OTP-01 option A, same
 * mechanism as `kof.supervisor` — hence `job(...)`/`dag(...)`, not the
 * `workflow.` prefix the §2 sketch drew; the sign-off note says so). Nothing
 * here crosses a runtime boundary — no threads, no process — so the SAME
 * source executes byte-identical on JVM and JS, and compiles on Script and
 * Native (compile-pinned below; the honest PROC001/CRON001/ORM001 gaps of §4
 * live in the job BODIES the user writes, not in this layer).
 */
class WorkflowE2ETest extends WorkflowPrograms {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

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
        } catch (java.lang.reflect.InvocationTargetException e) {
            return new Run(false, "THROW: " + e.getCause());
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

    private static String diags(CompilationResult r) {
        StringBuilder sb = new StringBuilder();
        r.diagnostics().getDiagnostics().forEach(d -> sb.append(d.message()).append("\n"));
        return sb.toString();
    }

    /** JVM+JS byte-parity (rule 5) + exact stdout golden. */
    private void assertJvmJsParity(String source, String... expected) throws Exception {
        Files.writeString(tmp.resolve("W.kf"), source);
        Run jvm = runJvm(tmp.resolve("W.kf"), tmp.resolve("o-jvm"));
        assertTrue(jvm.ok(), () -> "JVM failed: " + jvm.output());
        Run js = runJs(tmp.resolve("W.kf"), tmp.resolve("o-js"));
        assertTrue(js.ok(), () -> "JS failed: " + js.output());
        assertEquals(jvm.output(), js.output(), "kof.workflow MVP: JVM/JS diverge");
        for (String e : expected) {
            assertTrue(jvm.output().contains(e), () -> "expected '" + e + "' in: " + jvm.output());
        }
    }

    /** The MVP headline: linear dependency chain runs in topological order
     *  regardless of input order, Report.summary/`allOk` agree on both faces. */
    @Test
    void linearDagRunsInOrderReportsOk() throws Exception {
        assertJvmJsParity("""
            import kof.workflow
            main() {
                var build = job("build", () -> true)
                var image = job("image", () -> true).after(build)
                var rep = dag(listOf(image, build)).run()
                println(rep.summary())
                println(rep.allOk())
            }
            """, "ok=build,image failed= skipped=", "true");
    }

    /** Failure cascades: failed job poisons its transitive dependents,
     *  independent branches still run (diamond with a side branch). */
    @Test
    void failureSkipsTransitiveDependentsOnly() throws Exception {
        assertJvmJsParity("""
            import kof.workflow
            main() {
                var build = job("build", () -> false)
                var image = job("image", () -> true).after(build)
                var ship = job("ship", () -> true).after(image)
                var lint = job("lint", () -> true)
                var rep = dag(listOf(build, image, ship, lint)).run()
                println(rep.summary())
                println(rep.allOk())
            }
            """, "ok=lint failed=build skipped=image,ship", "false");
    }

    /** A throwing body is caught, recorded as failed with the reason, and
     *  cascades like a false (errors face). NOTE: the body keeps an
     *  unreachable-looking `return false` because a block lambda whose only
     *  exit is `throw` types as Void ("expected 'function' but got
     *  'function'") — typer limitation, cataloged with the unit. */
    @Test
    void throwingBodyRecordedWithReason() throws Exception {
        assertJvmJsParity("""
            import kof.workflow
            main() {
                var boom = job("boom", () -> { if (true) { throw "kaboom" } return false })
                var after = job("after", () -> true).after(boom)
                var rep = dag(listOf(boom, after)).run()
                println(rep.summary())
                println(rep.errors.get(0))
            }
            """, "ok= failed=boom skipped=after", "boom: kaboom");
    }

    /** Cycle is rejected at RUN time with an actionable message (§2 invariant
     *  — never a silent hang), propagating as a plain Kof string throw. */
    @Test
    void cycleRejectedAtRunTimeWithMessage() throws Exception {
        Files.writeString(tmp.resolve("C.kf"), """
            import kof.workflow
            main() {
                var a = job("a", () -> true)
                var b = job("b", () -> true).after(a)
                var c = job("c", () -> true).after(b)
                a.after(c)
                dag(listOf(a, b, c)).run()
            }
            """);
        Run jvm = runJvm(tmp.resolve("C.kf"), tmp.resolve("c-jvm"));
        assertTrue(jvm.output().contains("ciclo detectado entre: a,b,c"),
                () -> "JVM cycle message wrong: " + jvm.output());
        Run js = runJs(tmp.resolve("C.kf"), tmp.resolve("c-js"));
        assertTrue(js.output().contains("ciclo detectado entre: a,b,c"),
                () -> "JS cycle message wrong: " + js.output());
    }

    /** Construction-time guards: dup name / null body / empty dag — clear
     *  errors, not UB. */
    @Test
    void dagGuardsThrowAtConstruction() throws Exception {
        assertJvmJsParity("""
            import kof.workflow
            main() {
                var x = job("x", () -> true)
                var y = job("y", () -> true)
                try { dag(listOf(x, y, x)).run() } catch (String e) { println(e) }
                try { job("nulo", null) } catch (String e) { println(e) }
                try { dag(listOf()) } catch (String e) { println(e) }
                try { x.after(x) } catch (String e) { println(e) }
            }
            """, "nome de job duplicado 'x'", "corpo nulo", "dag() vazia",
                "dependência de si mesmo");
    }

    /** Real bodies: a workflow whose jobs do measurable work — closures
     *  accumulate, chains compose — proving the layer is composition, not a
     *  toy (string building through dependent jobs, Report verifies order). */
    @Test
    void realBodiesComposeThroughDeps() throws Exception {
        assertJvmJsParity("""
            import kof.workflow
            main() {
                var acc = listOf("start")
                var build = job("build", () -> { acc.add("built"); return true })
                var image = job("image", () -> { acc.add("imaged"); return true }).after(build)
                var rep = dag(listOf(image, build)).run()
                println(rep.summary())
                println(acc.get(2))
            }
            """, "ok=build,image failed= skipped=", "imaged");
    }

    /** 2.1.3 face 1: ADDITIVE retry (Q3) — `flow.retry(job, 2,
     *  exponential(1, 2))` recovers a flaky closure-counter on the 3rd
     *  attempt; `retryFixed(job, 1)` exhausts a thrower and keeps the last
     *  reason in errors; Report.retries records actual attempt counts. */
    @Test
    void retryFacesBothOutcomes() throws Exception {
        assertJvmJsParity(SRC_RETRY_FACES_BOTH_OUTCOMES, "ok=flaky failed=boom skipped=", "flaky: tentativas=3",
                "boom: tentativas=2", "boom: sempre");
    }

    /** 2.1.3 face 2 (Q4): deadLetter IN-MEMORY — `Report.dead` coleta
     *  "nome: motivo" para TODO job que esgotou retry (throw e false),
     *  sem depender de sink; jobs bem-sucedidos nunca entram. */
    @Test
    void deadLetterInMemoryFaceCollectsDeadJobs() throws Exception {
        assertJvmJsParity(SRC_DEAD_LETTER_IN_MEMORY_FACE_COLLECTS_DEAD_JOBS, "ok=ok failed=boom,falsey skipped=", "boom: estourou", "falsey: false", "2");
    }

    /** 2.1.3 face 2 (Q4): deadLetter DURÁVEL — sink `(nome, motivo) -> Bool`
     *  do USUÁRIO recebe cada falha final (persistência é código dele, ex.
     *  kof.orm); recusa (false) falha ALTO com o nome do job (R6). */
    @Test
    void deadLetterDurableSinkReceivesFailuresAndRefusalIsLoud() throws Exception {
        assertJvmJsParity(SRC_DEAD_LETTER_DURABLE_SINK_RECEIVES_FAILURES_AND_REFUSAL_IS_LOUD, "boom: persiste-me", "boom/persiste-me", "1");
        assertJvmJsParity(SRC_DEAD_LETTER_DURABLE_SINK_RECEIVES_FAILURES_AND_REFUSAL_IS_LOUD_2, "workflow: deadLetter sink recusou 'boom'");
    }

    /** 2.1.3 face 3: `flow.schedule(expr, dag)` DELEGA ao scheduler.at
     *  (duração idiomática ou cron — D-SCHED-DURATION); cada disparo roda a
     *  dag e devolve allOk(). Fire-count real com "20ms" nos 2 alvos. */
    @Test
    void scheduleDelegatesToSchedulerAtAndFires() throws Exception {
        assertJvmJsParity("""
            import kof.workflow
            main() {
                var runs = 0
                var a = job("a", () -> { runs = runs + 1; return true })
                var id = schedule(dag(listOf(a)), "20ms")
                time.sleep(100)
                scheduler.cancel(id)
                println(id != "")
                println(runs >= 2)
            }
            """, "true", "true");
    }

    /** Native: o gate CRON001 do scheduler.at é estático — referenciá-lo no
     *  host derrubaria a compilação INTEIRA. A fatia schedule entra no
     *  Native como STUB que falha ALTO em runtime citando CRON001 (R6);
     *  prova: o host + schedule compilam no Native (a delegação não está lá). */
    @Test
    void scheduleOnNativeCompilesViaStubHostStillPortable() throws Exception {
        Files.writeString(tmp.resolve("S.kf"), """
            import kof.workflow
            main() {
                var id = schedule(dag(listOf(job("a", () -> true))), "*/5 * * * *")
                println(id)
            }
            """);
        CompilationResult nativeRes = driver.compile(tmp.resolve("S.kf"), tmp.resolve("s-native"), Target.NATIVE);
        assertTrue(nativeRes.success(), () -> "Native deve compilar o host + stub schedule: " + diags(nativeRes));
    }

    /** 2.1.3 face 4: `checkpoint(d, dbConn, dagName)` — store REUSA kof.db
     *  (H2 mem) via a fatia orm; restored jobs re-enter as succeeded WITHOUT
     *  re-running their bodies (counter proves the skip). JVM-only golden:
     *  the store is H2; JS orm bridge parity is that lane's surface. */
    @Test
    void checkpointRestoresCompletedJobsAcrossRuns() throws Exception {
        Files.writeString(tmp.resolve("C.kf"), SRC_CHECKPOINT_RESTORES_COMPLETED_JOBS_ACROSS_RUNS);
        CompilationResult result = driver.compile(tmp.resolve("C.kf"), tmp.resolve("c-jvm"), Target.JVM);
        assertTrue(result.success(), () -> "JVM compile: " + diags(result));
        String h2 = null;
        for (String entry : System.getProperty("java.class.path").split(java.io.File.pathSeparator)) {
            if (entry.contains("h2") && entry.endsWith(".jar")) { h2 = entry; break; }
        }
        if (h2 == null) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "h2 jar ausente no classpath");
        }
        ProcessBuilder pb = new ProcessBuilder(TestJdk.javaBin(),
                "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                "-cp", tmp.resolve("c-jvm").toString() + java.io.File.pathSeparator + h2,
                "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int ec = p.waitFor();
        assertEquals(0, ec, "exit: " + output);
        assertEquals("""
            ok=a,b failed= skipped=
            2
            ok=a,b failed= skipped=
            2""".trim(), output.trim(), "checkpoint: 2a run executa, 2a restaura sem re-executar");
    }

    /** Native: a fatia checkpoint entra como STUB (ORM001 em runtime) — o
     *  host + stub compilam (a referência a kof.orm não está lá). */
    @Test
    void checkpointOnNativeCompilesViaStub() throws Exception {
        Files.writeString(tmp.resolve("CK.kf"), """
            import kof.workflow
            main() {
                var d = dag(listOf(job("a", () -> true)))
                checkpoint(d, "jdbc:h2:mem:x", "dag1")
                d.run()
            }
            """);
        CompilationResult nativeRes = driver.compile(tmp.resolve("CK.kf"), tmp.resolve("c-native"), Target.NATIVE);
        assertTrue(nativeRes.success(), () -> "Native deve compilar o host + stub checkpoint: " + diags(nativeRes));
    }

    /** Rule-5 source portability: the same injected host compiles on Native
     *  (no runtime boundary in this layer — bodies decide). Script shares the
     *  exact same merge/injection pipeline (CompilerPipeline.interpret), so
     *  compilation is proven there by construction. */
    @Test
    void hostCompilesOnNative() throws Exception {
        Files.writeString(tmp.resolve("P.kf"), """
            import kof.workflow
            main() {
                var rep = dag(listOf(job("a", () -> true))).run()
                println(rep.summary())
            }
            """);
        CompilationResult nativeRes = driver.compile(tmp.resolve("P.kf"), tmp.resolve("p-native"), Target.NATIVE);
        assertTrue(nativeRes.success(), () -> "Native must compile the host: " + diags(nativeRes));
    }

    /** D-WORKFLOW-RUN slice 1: introspection — `order()` gives the
     *  topological order without running a body, `runJob(name)` runs only the
     *  named job plus its transitive deps, and both surfaces are byte-parity
     *  JVM/JS. The acc list proves which bodies actually ran. */
    @Test
    void orderAndRunJobIntrospectWithoutRunningEverything() throws Exception {
        assertJvmJsParity(SRC_ORDER_AND_RUN_JOB_INTROSPECT_WITHOUT_RUNNING_EVERYTHING, "a,b,c", "0", "ok=a,b failed= skipped=", "a,b");
    }

    /** `runJob` on an unknown name is loud (R6), never a silent empty run. */
    @Test
    void runJobUnknownNameIsLoud() throws Exception {
        assertJvmJsParity("""
            import kof.workflow
            main() {
                var d = dag(listOf(job("a", () -> true)))
                try { d.runJob("ghost") } catch (String e) { println(e) }
                try { d.depsOf("ghost") } catch (String e) { println(e) }
            }
            """, "workflow: job 'ghost' não existe na dag");
    }

    /** `order()` rejects a cycle with the same actionable message as `run()`. */
    @Test
    void orderRejectsCycleWithSameMessage() throws Exception {
        assertJvmJsParity("""
            import kof.workflow
            main() {
                var a = job("a", () -> true)
                var b = job("b", () -> true).after(a)
                a.after(b)
                try { dag(listOf(a, b)).order() } catch (String e) { println(e) }
            }
            """, "ciclo detectado entre: a,b");
    }

    /** 2.1.3 face 5 (supervision — plano §3/§5: o workflow DELEGA o restart
     *  ao kof.supervisor): happy path chain+independente roda sob o one_for_one
     *  e o Report sai na ordem de declaração — SEM o usuário importar
     *  kof.supervisor (o CompilerWorkflow injeta o host flat, dedup por marca
     *  KofSupWrap). */
    @Test
    void supervisedHappyPathRunsDagUnderOneForOne() throws Exception {
        assertJvmJsParity("""
            import kof.workflow
            main() {
                var a = job("a", () -> true)
                var b = job("b", () -> true).after(a)
                var c = job("c", () -> true)
                var rep = runSupervised(dag(listOf(b, a, c)), "s1", 2)
                println(rep.summary())
                println(rep.allOk())
            }
            """, "ok=b,a,c failed= skipped=", "true");
    }

    /** one_for_one de verdade: SÓ o filho que falha reinicia (laço vigiar por
     *  filho do núcleo OTP). `flaky` tropeça 2x e vence na 3ª visita; `vizinho`
     *  roda UMA vez e nunca é tocado — os dois contadores provam os dois lados
     *  da palavra "one". Report.retries expõe o custo em tentativas. */
    @Test
    void supervisedOneForOneRestartsOnlyTheFailedChild() throws Exception {
        assertJvmJsParity("""
            import kof.workflow
            main() {
                var f = 0
                var v = 0
                var flaky = job("flaky", () -> { f = f + 1; if (f <= 2) { throw "tropeco-" + f } return true })
                var vizinho = job("vizinho", () -> { v = v + 1; return true })
                var rep = runSupervised(dag(listOf(flaky, vizinho)), "s2", 3)
                println(rep.summary())
                println(rep.retries.get(0))
                println("f=" + f + " v=" + v)
            }
            """, "ok=flaky,vizinho failed= skipped=", "flaky: tentativas=3", "f=3 v=1");
    }

    /** A política de reinício é do supervisor: com max=1 o job que sempre
     *  falha encerra na 2ª visita (limite excedido → drop via escalate),
     *  vira failed/dead com o motivo cru, o dependente pula (skip transitivo
     *  no status podre) e o independente fecha — nada trava nem reinicia
     *  para sempre (R6). */
    @Test
    void supervisedLimitExceededDropsAndSkipsDependents() throws Exception {
        assertJvmJsParity(SRC_SUPERVISED_LIMIT_EXCEEDED_DROPS_AND_SKIPS_DEPENDENTS, "ok=okjob failed=boom skipped=depois", "boom: sempre", "boom: sempre",
                "boom: tentativas=2", "false");
    }

    /** R6 da face, três recusas ALTAS: retry() na mesma dag (duas políticas
     *  de reinício = uma só manda), maxReinicios < 1 (restart ilimitado
     *  silencioso = storm de threads — a lição medida do host que caiu hoje)
     *  e supervisor sem nome. */
    @Test
    void supervisedGuardsFailLoud() throws Exception {
        assertJvmJsParity("""
            import kof.workflow
            main() {
                var x = job("x", () -> true)
                var flow = dag(listOf(x))
                flow.retry(x, 2, (n: Int) -> 0)
                try { runSupervised(flow, "s", 1) } catch (String e) { println(e) }
                try { runSupervised(dag(listOf(x)), "s", 0) } catch (String e) { println(e) }
                try { runSupervised(dag(listOf(x)), "", 1) } catch (String e) { println(e) }
            }
            """, "é política do supervisor", "maxReinicios < 1", "sem nome de supervisor");
    }

    /** Native: o núcleo supervisor roda nos 4 alvos (§129 portado, OTP001
     *  removido 19/09) — a face NÃO precisa de stub; prova: o host + o
     *  supervisor + a fatia compilam no Native. */
    @Test
    void supervisedCompilesOnNative() throws Exception {
        Files.writeString(tmp.resolve("U.kf"), """
            import kof.workflow
            main() {
                var a = job("a", () -> true)
                var rep = runSupervised(dag(listOf(a)), "s", 2)
                println(rep.summary())
            }
            """);
        CompilationResult nativeRes = driver.compile(tmp.resolve("U.kf"), tmp.resolve("u-native"), Target.NATIVE);
        assertTrue(nativeRes.success(), () -> "Native deve compilar host + supervisor + fatia: " + diags(nativeRes));
    }

    /** Import duplo: `kof.supervisor` injeta o host ANTES (pipeline 438→440);
     *  o `import kof.workflow` não pode DUBLAR Supervisor/KofWorker — a dedup
     *  pela marca KofSupWrap decide e o programa roda igual. */
    @Test
    void importedSupervisorDoesNotDoubleHost() throws Exception {
        assertJvmJsParity("""
            import kof.supervisor
            import kof.workflow
            main() {
                var rep = runSupervised(dag(listOf(job("a", () -> true))), "s", 2)
                println(rep.summary())
            }
            """, "ok=a failed= skipped=");
    }

    /** §353: corpo de lambda `() -> File(...).exists()` (io direto, sem
     *  workaround de binding `Bool ok`) era rejeitado com SEM014
     *  ("expected 'function' but got 'function'") — o typer SEMANAL não
     *  conhecia kof.io, só o do emit. Pre-red: compilação falha nos 2 alvos;
     *  pós-fix: JVM == JS e o dag enxerga o disco de verdade. */
    @Test
    void lambdaBodyWithIoBoolCompilesAndRuns() throws Exception {
        String src = """
            import kof.workflow
            import kof.io
            main() {
                val f = File("__BASE__/s353.txt")
                println(f.writeText("x"))
                var probe = job("probe", () -> File("__BASE__/s353.txt").exists())
                var gone = job("gone", () -> File("__BASE__/missing-s353.txt").exists())
                var rep = dag(listOf(probe, gone)).run()
                println(rep.summary())
                println(rep.allOk())
            }
            """.replace("__BASE__", tmp.toString());
        assertJvmJsParity(src, "true", "ok=probe failed=gone skipped=", "false");
    }
}
