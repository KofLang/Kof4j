package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §770 — a call into an overloaded external method picked the FIRST declared
 * overload with the same arity, ignoring the argument types:
 * {@code FluentConfiguration.configuration(HashMap)} dispatched to
 * {@code configuration(Configuration)} and died with
 * {@code IncompatibleClassChangeError}. Selection now follows JLS 15.12.2
 * (subtyping, then boxing, then varargs; the most specific wins) for jar and
 * JDK owners, and a tie between known types is SEM057 at compile time.
 *
 * <p>Goldens are what javac selects for the same calls.
 */
class ExternalOverloadResolutionE2ETest {

    private static final Map<String, String> SOURCES = Map.of(
            "Cfg", "package ext; public interface Cfg {}",
            "Conf", """
                package ext;
                public class Conf {
                    public String last = "";
                    public Conf configuration(Cfg c) { last = "cfg"; return this; }
                    public Conf configuration(java.util.Map<String, String> m) { last = "map" + m.size(); return this; }
                    public String pick(Object o) { return "object"; }
                    public String pick(CharSequence s) { return "charseq"; }
                    public String pick(String s) { return "string"; }
                    public String num(Integer x) { return "Integer"; }
                    public String num(long x) { return "long"; }
                    public String amb(Object a, String b) { return "os"; }
                    public String amb(String a, Object b) { return "so"; }
                    public static String stat(Cfg c) { return "static-cfg"; }
                    public static String stat(java.util.Map<String, String> m) { return "static-map"; }
                }
                """,
            "Base", "package ext; public class Base { public String over(Object o) { return \"base-object\"; } }",
            "Derived", """
                package ext;
                public class Derived extends Base { public String over(String s) { return "derived-string"; } }
                """);

    @TempDir Path tmp;

    private Path buildJar() throws Exception {
        Path src = tmp.resolve("src/ext");
        Files.createDirectories(src);
        Path classes = tmp.resolve("cls");
        Files.createDirectories(classes);
        String[] args = new String[4 + SOURCES.size()];
        args[0] = "--release";
        args[1] = "21";
        args[2] = "-d";
        args[3] = classes.toString();
        int i = 4;
        for (var e : SOURCES.entrySet()) {
            Path f = src.resolve(e.getKey() + ".java");
            Files.writeString(f, e.getValue());
            args[i++] = f.toString();
        }
        JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
        assertNotNull(jc, "the JDK JavaCompiler is required (surefire must run on a JDK)");
        assertEquals(0, jc.run(null, null, null, args));
        Path jar = tmp.resolve("ext-overloads.jar");
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (Path c : Files.walk(classes).filter(f -> f.toString().endsWith(".class")).toList()) {
                z.putNextEntry(new ZipEntry(classes.relativize(c).toString().replace('\\', '/')));
                z.write(Files.readAllBytes(c));
            }
        }
        return jar;
    }

    private CompilationResult compile(Path jar, String kf) throws Exception {
        Path source = tmp.resolve("Main.kf");
        Files.writeString(source, kf);
        CompilerDriver d = new CompilerDriver();
        d.setExternalClasspath(List.of(jar));
        return d.compile(source, tmp.resolve("out"), Target.JVM);
    }

    private String compileAndRun(String kf) throws Exception {
        Path jar = buildJar();
        CompilationResult r = compile(jar, kf);
        assertTrue(r.success(), "must compile: " + r.diagnostics().getDiagnostics());
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true));
        try (URLClassLoader cl = new URLClassLoader(new URL[]{tmp.resolve("out").toUri().toURL(),
                jar.toUri().toURL()}, getClass().getClassLoader())) {
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(old);
        }
        return buf.toString();
    }

    @Test
    void mapArgumentSelectsTheMapOverloadNotTheInterfaceOne() throws Exception {
        String out = compileAndRun("""
            import ext.Conf
            import java.util.HashMap
            main() {
                val m = HashMap<String, String>()
                m.put("k", "v")
                println(Conf().configuration(m).last)
                println(Conf().configuration(mapOf("a", "b")).last)
                println(Conf.stat(m))
            }
            """);
        assertEquals("map1\nmap1\nstatic-map\n", out);
    }

    @Test
    void mostSpecificApplicableOverloadWinsByPhase() throws Exception {
        String out = compileAndRun("""
            import ext.Conf
            main() {
                val c = Conf()
                println(c.pick("s"))
                println(c.pick(1))
                println(c.num(1))
            }
            """);
        assertEquals("string\nobject\nlong\n", out);
    }

    @Test
    void overloadsInheritedFromTheSuperclassAreCandidates() throws Exception {
        String out = compileAndRun("""
            import ext.Derived
            main() {
                val d = Derived()
                println(d.over("s"))
                println(d.over(1))
            }
            """);
        assertEquals("derived-string\nbase-object\n", out);
    }

    @Test
    void jdkInterfaceStaticOverloadIsCalledThroughTheInterface() throws Exception {
        String out = compileAndRun("""
            import java.nio.file.Path
            main() {
                println(Path.of("x").getFileName())
                println(Path.of("a", "b").getNameCount())
            }
            """);
        assertEquals("x\n2\n", out);
    }

    /** JDK overloads that the arity pick got wrong: round(float) over
     *  round(double) (an int where a long was concatenated: VerifyError),
     *  valueOf(String) over valueOf(LocalDateTime), now(Clock) over now(ZoneId),
     *  and an Int literal that must widen into plusSeconds(long). */
    @Test
    void jdkTimeOverloadsSelectByArgumentType() throws Exception {
        String out = compileAndRun("""
            import java.sql.Timestamp
            import java.time.LocalDateTime
            import java.time.LocalDate
            import java.time.ZoneOffset
            import java.time.ZonedDateTime
            import java.lang.Math
            main() {
                println("r=" + Math.round(2.6))
                val ldt = LocalDateTime.of(2026, 1, 2, 3, 4)
                println(Timestamp.valueOf(ldt).toString())
                println(LocalDate.now(ZoneOffset.UTC).getYear() > 2000)
                println(ZonedDateTime.of(ldt, ZoneOffset.UTC).plusSeconds(1).getSecond())
            }
            """);
        assertEquals("r=3\n2026-01-02 03:04:00.0\ntrue\n1\n", out);
    }

    /** Math.min/max on Int operands bind (int, int), not the first overload in
     *  declaration order: the result was stored into an Int field or local
     *  (VerifyError at class load, enois G374), and a long min cast to Int in
     *  a method with many branches crashed COMPUTE_FRAMES (COMP002, G377). */
    @Test
    void mathMinMaxSelectTheOverloadOfTheOperands() throws Exception {
        String out = compileAndRun("""
            import java.lang.Math
            class Window {
                Int days = 0
                Window(Int d) { this.days = Math.max(1, Math.min(d, 365)) }
            }
            Int score(Int a, Long x, Bool f1, Bool f2, Bool f3) {
                var risk = 0
                if (a > 3) { risk = risk + 10 }
                if (f1) { risk = risk + 7 }
                if (f2) { risk = risk + 3 }
                if (f3) { risk = risk + 11 }
                if (x > 0L) { risk = risk + (Math.min(45L, x * 15L) as Int) }
                return risk
            }
            main() {
                println(Window(500).days + " " + Window(-3).days)
                val a: Int = Math.min(4, 9)
                println(a + 1)
                println(score(4, 2L, true, false, true) + " " + score(0, 9L, false, false, false))
            }
            """);
        assertEquals("365 1\n5\n58 45\n", out);
    }

    @Test
    void tieBetweenKnownArgumentTypesIsSem057() throws Exception {
        CompilationResult r = compile(buildJar(), """
            import ext.Conf
            main() {
                println(Conf().amb("a", "b"))
            }
            """);
        assertFalse(r.success(), "an ambiguous call must not compile");
        assertTrue(r.diagnostics().getDiagnostics().stream().anyMatch(d -> "SEM057".equals(d.code())),
                "expected SEM057, was: " + r.diagnostics().getDiagnostics());
    }
}
