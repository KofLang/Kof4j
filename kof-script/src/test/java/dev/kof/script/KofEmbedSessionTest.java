package dev.kof.script;

import dev.kof.compiler.KofCapability;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * kofscript-embedding (issue #842, plan `docs/development/kofscript-embedding-plan.md`):
 * the three host requirements MEASURED — bounded execution (an infinite loop can
 * never block the host), capability confinement (EMBED001 by name) and session
 * bindings (per-session, not the global map).
 */
class KofEmbedSessionTest {

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void anInfiniteLoopNeverBlocksTheHost() throws Exception {
        // RED sem o stepping: este teste PENDURA até o @Timeout.
        try (KofEmbedSession s = KofEmbedSession.builder().start("""
                main() {
                    var i = 0
                    while (true) {
                        i = i + 1
                    }
                }
                """)) {
            KofEmbedSession.Status first = s.run(2000);
            assertEquals(KofEmbedSession.Status.RUNNING, first,
                    "um laço infinito devolve RUNNING (o host recupera o controle)");
            // e retoma: mais um passo continua RUNNING, nunca DONE
            assertEquals(KofEmbedSession.Status.RUNNING, s.run(2000),
                    "o script é retomável");
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void aBoundedScriptReachesDoneWithItsOutput() throws Exception {
        try (KofEmbedSession s = KofEmbedSession.builder().start("""
                main() {
                    println("kof-embed-ok")
                }
                """)) {
            assertEquals(KofEmbedSession.Status.DONE, s.runToCompletion());
            assertTrue(s.stdout().contains("kof-embed-ok"),
                    "o stdout do script chega ao host: " + s.stdout());
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void anUngrantedCapabilityRefusesByName() throws Exception {
        try (KofEmbedSession s = KofEmbedSession.builder().start("""
                main() {
                    println(File("/etc/hostname").readText())
                }
                """)) {
            assertTrue(s.grants().isEmpty(), "o default é NENHUMA capacidade");
            assertEquals(KofEmbedSession.Status.FAILED, s.runToCompletion());
            assertNotNull(s.failure());
            assertTrue(s.failure().contains("EMBED001"),
                    "a recusa é NOMEADA: " + s.failure());
            assertTrue(s.failure().contains("FILESYSTEM"),
                    "a capacidade negada é nomeada: " + s.failure());
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void theGrantOpensTheSurface() throws Exception {
        try (KofEmbedSession s = KofEmbedSession.builder()
                .grant(KofCapability.FILESYSTEM)
                .start("""
                        main() {
                            println(File("/etc/hostname").exists())
                        }
                        """)) {
            assertEquals(KofEmbedSession.Status.DONE, s.runToCompletion(),
                    "com a capacidade concedida o script completa: " + s.failure());
            assertTrue(s.stdout().contains("true"), "o valor real: " + s.stdout());
        }
    }

    @Test
    void sessionBindingsAreScopedAndIsolated() throws Exception {
        KofEmbedSession a = KofEmbedSession.builder().bind("host", "A").build();
        KofEmbedSession b = KofEmbedSession.builder().bind("host", "B").build();
        assertEquals("A", a.bindings().get("host"));
        assertEquals("B", b.bindings().get("host"));
        assertNotEquals(a.bindings(), b.bindings(),
                "duas sessões NÃO compartilham os bindings");
        a.close();
        b.close();
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void theScriptSeesTheHostApi() throws Exception {
        try (KofEmbedSession s = KofEmbedSession.builder()
                .bindApi("greet", "String", args -> "ola " + args[0])
                .start("""
                        main() {
                            println(hostcall_greet("kof"))
                        }
                        """)) {
            assertEquals(KofEmbedSession.Status.DONE, s.runToCompletion(),
                    "o script chama a API do host: " + s.failure());
            assertTrue(s.stdout().contains("ola kof"),
                    "o retorno do host chega ao script: " + s.stdout());
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void anUnregisteredHostApiIsRefusedByName() throws Exception {
        try (KofEmbedSession s = KofEmbedSession.builder().start("""
                main() {
                    println(hostcall_greet("kof"))
                }
                """)) {
            assertEquals(KofEmbedSession.Status.FAILED, s.runToCompletion());
            assertNotNull(s.failure());
            assertTrue(s.failure().contains("EMBED003"),
                    "a recusa é NOMEADA: " + s.failure());
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void aScriptFailureIsReportedNotThrown() throws Exception {
        try (KofEmbedSession s = KofEmbedSession.builder().start("""
                main() {
                    throw "falha-do-script"
                }
                """)) {
            assertEquals(KofEmbedSession.Status.FAILED, s.runToCompletion());
            assertTrue(s.failure().contains("falha-do-script"),
                    "a falha chega ao host como texto: " + s.failure());
        }
    }
}
