package dev.kof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * `kof makealive <plan|apply|destroy>` (3.8, D-MAKEALIVE-CLI): o runner sintetiza
 * main() sobre `design()` + `provider()` e fala pelo protocolo de linha marcada;
 * estado h2 via --state com geracao max+1; paridade de bytes JVM==JS; recusas
 * honestas (R6/R7). RED-first na slice: os asserts de saida foram medidos no
 * caminho JVM e exigidos IGUAIS no JS.
 */
class CmdMakealiveTest {

    private static Process startCli(Path workDir, String... cliArgs) throws IOException {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.add("--enable-native-access=ALL-UNNAMED");
        cmd.add("--sun-misc-unsafe-memory-access=allow");
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("dev.kof.cli.Main");
        cmd.addAll(java.util.Arrays.asList(cliArgs));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);
        return pb.start();
    }

    private record CliResult(int exit, String out) {}

    private static CliResult run(Path workDir, String... cliArgs) throws Exception {
        Process p = startCli(workDir, cliArgs);
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(300, TimeUnit.SECONDS), "CLI travou");
        return new CliResult(p.exitValue(), out);
    }

    private static final String DESIGN = """
            import kof.makealive

            class World {
                List<String> rn = listOf()
                List<String> rk = listOf()
                List<String> rv = listOf()
            }

            Map<String, String> mkWorldRead(World w, Resource r) {
                var m = mapOf()
                var i = 0
                while (i < w.rn.size) {
                    if (w.rn.get(i) == r.name()) { m.put(w.rk.get(i), w.rv.get(i)) }
                    i = i + 1
                }
                return m
            }

            Bool mkWorldSet(World w, Resource r, Map<String, String> props) {
                w.rn.add(r.name())
                w.rk.add("size")
                w.rv.add(props.get("size"))
                return true
            }

            Infrastructure design() {
                var d = Infrastructure("prod")
                d.resource("app", "a1")
                d.resource("app", "a2")
                d.prop("a1", "size", "3")
                d.requires("a2", "a1")
                return d
            }

            Provider provider() {
                var w = World()
                return Provider(
                    (r: Resource) -> mkWorldRead(w, r),
                    (r: Resource, props: Map<String, String>) -> mkWorldSet(w, r, props),
                    (r: Resource) -> true
                )
            }
            """;

    private static final String DESIGN_RECALCITRANT = """
            import kof.makealive

            Infrastructure design() {
                var d = Infrastructure("teimoso")
                d.resource("app", "a1")
                return d
            }

            Provider provider() {
                return Provider(
                    (r: Resource) -> mapOf(),
                    (r: Resource, props: Map<String, String>) -> false,
                    (r: Resource) -> true
                )
            }
            """;

    private Path writeDesign(Path dir, String name, String src) throws IOException {
        Path f = dir.resolve(name);
        Files.writeString(f, src, StandardCharsets.UTF_8);
        return f;
    }

    @Test
    void planOnFreshStateListsCreatesInTopoOrder(@TempDir Path dir) throws Exception {
        Path f = writeDesign(dir, "d.kf", DESIGN);
        CliResult r = run(dir, "makealive", "plan", f.toString(), "--state", dir.resolve("st1").toString());
        assertEquals(0, r.exit(), r.out() + "\nCP=" + System.getProperty("java.class.path").substring(0, 140));
        assertTrue(r.out().contains("plan creates=a1,a2 updates= deletes="), r.out());
    }

    @Test
    void applyPersistsGenerationAndPlanBecomesEmpty(@TempDir Path dir) throws Exception {
        Path f = writeDesign(dir, "d.kf", DESIGN);
        String st = dir.resolve("st2").toString();
        CliResult ap = run(dir, "makealive", "apply", f.toString(), "--state", st);
        assertEquals(0, ap.exit(), ap.out());
        assertTrue(ap.out().contains("apply created=a1,a2 updated= deleted= gen=0"), ap.out());
        CliResult pl = run(dir, "makealive", "plan", f.toString(), "--state", st);
        assertEquals(0, pl.exit(), pl.out());
        assertTrue(pl.out().contains("plan creates= updates= deletes="), pl.out());
    }

    @Test
    void destroyRemovesAllReverseTopoAndStateRebirthsOnNextPlan(@TempDir Path dir) throws Exception {
        Path f = writeDesign(dir, "d.kf", DESIGN);
        String st = dir.resolve("st3").toString();
        CliResult ap = run(dir, "makealive", "apply", f.toString(), "--state", st);
        assertEquals(0, ap.exit(), ap.out());
        CliResult de = run(dir, "makealive", "destroy", f.toString(), "--state", st);
        assertEquals(0, de.exit(), de.out());
        assertTrue(de.out().contains("destroy deleted=a2,a1 gen=1"), de.out());
        CliResult pl = run(dir, "makealive", "plan", f.toString(), "--state", st);
        assertTrue(pl.out().contains("plan creates=a1,a2"), pl.out());
    }

    @Test
    void jsonFlagFacesMarkedLineShape(@TempDir Path dir) throws Exception {
        Path f = writeDesign(dir, "d.kf", DESIGN);
        CliResult r = run(dir, "makealive", "apply", f.toString(),
                "--state", dir.resolve("st4").toString(), "--json");
        assertEquals(0, r.exit(), r.out());
        assertTrue(r.out().contains("\"op\":\"apply\""), r.out());
        assertTrue(r.out().contains("\"created\":[\"a1\",\"a2\"]"), r.out());
        assertTrue(r.out().contains("\"gen\":0"), r.out());
        assertTrue(r.out().contains("\"allOk\":true"), r.out());
    }

    @Test
    void jsTargetFacesJvmBytes(@TempDir Path dir) throws Exception {
        Path f = writeDesign(dir, "d.kf", DESIGN);
        CliResult jvm = run(dir, "makealive", "apply", f.toString(), "--state", dir.resolve("stJ").toString());
        assertEquals(0, jvm.exit(), jvm.out());
        CliResult js = run(dir, "makealive", "apply", f.toString(), "--target", "js",
                "--state", dir.resolve("stJ2").toString());
        assertEquals(0, js.exit(), js.out());
        assertEquals(jvm.out(), js.out(), "paridade de bytes JVM==JS");
        CliResult jvmP = run(dir, "makealive", "plan", f.toString(), "--state", dir.resolve("stJ").toString());
        CliResult jsP = run(dir, "makealive", "plan", f.toString(), "--target", "js",
                "--state", dir.resolve("stJ2").toString());
        assertEquals(0, jsP.exit(), jsP.out());
        assertEquals(jvmP.out(), jsP.out(), "paridade de bytes JVM==JS (plan pos-apply)");
    }

    @Test
    void providerRefusalPassesTheRawDiagnosoThrough(@TempDir Path dir) throws Exception {
        Path f = writeDesign(dir, "bad.kf", DESIGN_RECALCITRANT);
        CliResult r = run(dir, "makealive", "apply", f.toString(), "--state", dir.resolve("st5").toString());
        assertEquals(1, r.exit(), r.out());
        assertTrue(r.out().contains("provider recusou set em 'a1'"), r.out());
    }

    @Test
    void refusalsAreHonest(@TempDir Path dir) throws Exception {
        Path f = writeDesign(dir, "d.kf", DESIGN);
        CliResult sub = run(dir, "makealive", "provision", f.toString());
        assertEquals(1, sub.exit(), sub.out());
        assertTrue(sub.out().contains("unknown subcommand"), sub.out());

        Path plain = writeDesign(dir, "plain.kf", "main() { println(1) }\n");
        CliResult imp = run(dir, "makealive", "plan", plain.toString());
        assertEquals(1, imp.exit(), imp.out());
        assertTrue(imp.out().contains("does not import kof.makealive"), imp.out());

        CliResult noDesign = run(dir, "makealive", "plan", f.toString().replace("d.kf", "plain.kf"));
        assertTrue(noDesign.exit() == 1, "mesmo arquivo sem import segue recusado");

        Path noProv = writeDesign(dir, "np.kf", DESIGN.replace("Provider provider()", "Provider unusedProvider()"));
        CliResult npr = run(dir, "makealive", "apply", noProv.toString());
        assertEquals(1, npr.exit(), npr.out());
        assertTrue(npr.out().contains("does not define provider()"), npr.out());

        CliResult tgt = run(dir, "makealive", "plan", f.toString(), "--target", "native");
        assertEquals(1, tgt.exit(), tgt.out());
        assertTrue(tgt.out().contains("is not supported"), tgt.out());
    }

    @Test
    void anUnsupportedTargetNamesItselfAndTheRealSupportSet(@TempDir Path dir) throws Exception {
        // Before the fix the message said "script/native are follow-up" while the
        // guard actually allowed jvm|js — it named targets that are REFUSED today.
        Path f = writeDesign(dir, "t.kf", DESIGN);
        for (String target : java.util.List.of("wasm", "script", "native")) {
            CliResult r = run(dir, "makealive", "plan", f.toString(), "--target", target);
            assertEquals(1, r.exit(), target + " must be refused: " + r.out());
            assertTrue(r.out().contains("--target " + target + " is not supported"),
                    target + " names the rejected target: " + r.out());
            assertTrue(r.out().contains("supported today: jvm|js"),
                    target + " states the real support set: " + r.out());
        }
    }
}
