package dev.kof.compiler;

/**
 * Programas Kof do teste de gap-codes ({@code DomainGapCodesTest}), hoisted de inline para
 * constantes. Vive fora da classe de teste para mantê-la abaixo do limite de 500
 * linhas de teste (Fase 3 da arquitetura de testes, {@code D-TEST-ARCHITECTURE-GO}).
 */
abstract class DomainGapPrograms {

    static final String SRC_PROCESS_RUN_ON_NATIVE_COMPILES = """
            main() {
                val r = process.run("echo", "hi")
                println(r.stdout)
            }
            """;

    static final String SRC_PROCESS_SPAWN_ON_NATIVE_COMPILES = """
            main() {
                val h = process.spawn("echo", "hi")
                println(if (h.alive()) "alive" else "dead")
            }
            """;

    static final String SRC_PROCESS_SPAWN_ON_CROSS_HAS_NO_GAP = """
            main() {
                val h = process.spawn("echo", "hi")
                println(if (h.alive()) "alive" else "dead")
            }
            """;

    static final String SRC_PROCESS_SPAWN_ON_JS_HAS_NO_GAP = """
            main() {
                val h = process.spawn("echo", "hi")
                println(if (h.alive()) "alive" else "dead")
            }
            """;

    static final String SRC_PROCESS_SPAWN_ON_JVM_HAS_NO_GAP = """
            main() {
                val h = process.spawn("echo", "hi")
                println(if (h.alive()) "alive" else "dead")
            }
            """;

    static final String SRC_IO_COPY_ON_CROSS_HAS_NO_GAP = """
                main() {
                    val f = File("/tmp/kof-io-probe")
                    println(f.copyTo("/tmp/kof-io-probe2"))
                }
                """;

    static final String SRC_IO_STAT_ON_CROSS_HAS_NO_GAP = """
                main() {
                    val f = File("/tmp/kof-io-probe")
                    println(f.exists())
                    println(f.isFile())
                    println(f.isDirectory())
                    println(f.writeText("x"))
                    println(f.readText())
                    println(f.appendText("y"))
                }
                """;

    static final String SRC_IO_READ_RANGE_ON_JS_IS_IOJS001 = """
            main() {
                val f = File("/tmp/kof-io-probe")
                println(f.readRange(0, 4).length)
            }
            """;

    static final String SRC_IO_AND_WEB_T1_ON_X86_AND_JS_HAVE_NO_GAP = """
            main() {
                val f = File("/tmp/kof-io-probe")
                println(f.exists())
                val app = web.app()
                app.listen(8080)
            }
            """;

    static final String SRC_STRING_INCOMPLETE_ON_JVM_HAS_NO_GAP = """
            main() {
                println("abc".matches("a.*"))
                println("a1b".replaceAll("b", "x"))
                println("ab".compareToIgnoreCase("AB"))
            }
            """;
}
