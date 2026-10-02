package dev.kof.compiler;

/**
 * Programas Kof do E2E de {@code kof.workflow} ({@code WorkflowE2ETest}), hoisted de inline
 * para constantes. Vive fora da classe de teste para mantê-la abaixo do limite
 * de 500 linhas de teste (Fase 3 da arquitetura de testes,
 * {@code D-TEST-ARCHITECTURE-GO}).
 */
abstract class WorkflowPrograms {

    static final String SRC_RETRY_FACES_BOTH_OUTCOMES = """
            import kof.workflow
            main() {
                var tries = 0
                var flaky = job("flaky", () -> { tries = tries + 1; return tries >= 3 })
                var boom = job("boom", () -> { if (true) { throw "sempre" } return false })
                var flow = dag(listOf(flaky, boom))
                flow.retry(flaky, 2, exponential(1, 2))
                flow.retryFixed(boom, 1)
                var rep = flow.run()
                println(rep.summary())
                println(rep.retries.get(0))
                println(rep.retries.get(1))
                println(rep.errors.get(0))
            }
            """;

    static final String SRC_DEAD_LETTER_IN_MEMORY_FACE_COLLECTS_DEAD_JOBS = """
            import kof.workflow
            main() {
                var boom = job("boom", () -> { if (true) { throw "estourou" } return false })
                var falsey = job("falsey", () -> false)
                var ok = job("ok", () -> true)
                var rep = dag(listOf(boom, falsey, ok)).run()
                println(rep.summary())
                println(rep.dead.get(0))
                println(rep.dead.get(1))
                println(rep.dead.size)
            }
            """;

    static final String SRC_DEAD_LETTER_DURABLE_SINK_RECEIVES_FAILURES_AND_REFUSAL_IS_LOUD = """
            import kof.workflow
            main() {
                var log = listOf()
                var boom = job("boom", () -> { if (true) { throw "persiste-me" } return false })
                var flow = dag(listOf(boom))
                flow.deadLetter(boom, (n: String, m: String) -> { log.add(n + "/" + m); return true })
                var rep = flow.run()
                println(rep.dead.get(0))
                println(log.get(0))
                println(log.size)
            }
            """;

    static final String SRC_DEAD_LETTER_DURABLE_SINK_RECEIVES_FAILURES_AND_REFUSAL_IS_LOUD_2 = """
            import kof.workflow
            main() {
                var boom = job("boom", () -> { if (true) { throw "x" } return false })
                var flow = dag(listOf(boom))
                flow.deadLetter(boom, (n: String, m: String) -> false)
                try {
                    flow.run()
                    println("no-throw")
                } catch (String e) {
                    println(e)
                }
            }
            """;

    static final String SRC_CHECKPOINT_RESTORES_COMPLETED_JOBS_ACROSS_RUNS = """
            import kof.workflow
            main() {
                var runs = 0
                var a = job("a", () -> { runs = runs + 1; return true })
                var b = job("b", () -> { runs = runs + 1; return true }).after(a)
                var d = dag(listOf(b, a))
                checkpoint(d, "jdbc:h2:mem:wfck1;DB_CLOSE_DELAY=-1", "pipelinha")
                var rep1 = d.run()
                println(rep1.summary())
                println(runs)
                var rep2 = d.run()
                println(rep2.summary())
                println(runs)
            }
            """;

    static final String SRC_ORDER_AND_RUN_JOB_INTROSPECT_WITHOUT_RUNNING_EVERYTHING = """
            import kof.workflow
            main() {
                var acc = listOf()
                var a = job("a", () -> { acc.add("a"); return true })
                var b = job("b", () -> { acc.add("b"); return true }).after(a)
                var c = job("c", () -> { acc.add("c"); return true }).after(b)
                var d = dag(listOf(c, b, a))
                println(kofWfJoin(d.order(), ","))
                println(acc.size)
                var rep = d.runJob("b")
                println(rep.summary())
                println(kofWfJoin(acc, ","))
            }
            """;

    static final String SRC_SUPERVISED_LIMIT_EXCEEDED_DROPS_AND_SKIPS_DEPENDENTS = """
            import kof.workflow
            main() {
                var boom = job("boom", () -> { if (true) { throw "sempre" } return false })
                var depois = job("depois", () -> true).after(boom)
                var okjob = job("okjob", () -> true)
                var rep = runSupervised(dag(listOf(boom, depois, okjob)), "s3", 1)
                println(rep.summary())
                println(rep.errors.get(0))
                println(rep.dead.get(0))
                println(rep.retries.get(0))
                println(rep.allOk())
            }
            """;
}
