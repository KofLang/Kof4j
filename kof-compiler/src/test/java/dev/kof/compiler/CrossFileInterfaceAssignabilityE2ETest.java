package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §726: a class that `implements` an interface was NOT assignable to
 * the interface type when the class and the interface live in DIFFERENT files
 * (spurious {@code SEM021}).
 *
 * <p>Root: heritage names are stored inconsistently — a type declared in the
 * SAME file stays SIMPLE ("Greeter"), a type resolved via IMPORT is QUALIFIED
 * ("ifc.Greeter"), and {@code sa.getClass} resolves only by SIMPLE name. The
 * assignability BFS compared the entry against the target's SIMPLE name, so the
 * cross-file same-package case never matched → SEM021 false positive, blocking
 * every library that exposes an interface + implementations across files.
 *
 * <p>Fix: compare by the resolved symbol's FQN on both sides (normalising the
 * simple/qualified forms). Same-file keeps working; an UNRELATED pair still
 * rejects.
 */
class CrossFileInterfaceAssignabilityE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private Path write(Path root, String rel, String src) throws Exception {
        Path f = root.resolve(rel);
        Files.createDirectories(f.getParent());
        Files.writeString(f, src);
        return f;
    }

    @Test
    void crossFileInterfaceImplementationIsAssignable() throws Exception {
        Path root = tmp.resolve("crossfile");
        List<Path> sources = new ArrayList<>();
        sources.add(write(root, "ifc/Greeter.kf",
                "package ifc\ninterface Greeter {\n    String greet()\n}\n"));
        sources.add(write(root, "ifc/PtGreeter.kf",
                "package ifc\nclass PtGreeter() implements Greeter {\n    String greet() { return \"ola\" }\n}\n"));
        sources.add(write(root, "ifc/EnGreeter.kf",
                "package ifc\nclass EnGreeter() implements Greeter {\n    String greet() { return \"hi\" }\n}\n"));
        sources.add(write(root, "Main.kf",
                "import ifc.Greeter\nimport ifc.PtGreeter\nimport ifc.EnGreeter\n"
                + "main() {\n    var gs: List<Greeter> = listOf(PtGreeter(), EnGreeter())\n"
                + "    for (var g in gs) { println(g.greet()) }\n}\n"));
        Path out = root.resolve("out");
        CompilationResult r = driver.compileSources(sources, out, Target.JVM, root);
        assertTrue(r.success(), () -> "§726: cross-file interface assignment rejected — "
                + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                "-cp", out.toString(), "Default.Main").redirectErrorStream(true).start();
        String o = NativeRiscv64E2ETest.runBounded(p, "crossfile");
        assertEquals(0, p.exitValue(), () -> "run: " + o);
        assertEquals("ola\nhi", o.strip());
    }

    @Test
    void crossFileClassInheritanceIsAssignable() throws Exception {
        Path root = tmp.resolve("inherit");
        List<Path> sources = new ArrayList<>();
        sources.add(write(root, "app/Animal.kf",
                "package app\nclass Animal {\n    String name\n"
                + "    public constructor(String name) { this.name = name }\n"
                + "    String speak(): String = \"animal\"\n}\n"));
        sources.add(write(root, "app/Dog.kf",
                "package app\nclass Dog extends Animal {\n"
                + "    public constructor(String name) { super(name) }\n"
                + "    String speak(): String = \"dog\"\n}\n"));
        sources.add(write(root, "Main.kf",
                "import app.Animal\nimport app.Dog\n"
                + "main() {\n    var a: Animal = Dog(\"rex\")\n    println(a.speak())\n}\n"));
        Path out = root.resolve("out");
        CompilationResult r = driver.compileSources(sources, out, Target.JVM, root);
        assertTrue(r.success(), () -> "cross-file class inheritance rejected — "
                + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                "-cp", out.toString(), "Default.Main").redirectErrorStream(true).start();
        String o = NativeRiscv64E2ETest.runBounded(p, "inherit");
        assertEquals(0, p.exitValue(), () -> "run: " + o);
        assertEquals("dog", o.strip());
    }

    @Test
    void unrelatedTypesStillReject() throws Exception {
        Path root = tmp.resolve("unrelated");
        List<Path> sources = new ArrayList<>();
        sources.add(write(root, "Main.kf",
                "class A(Int x)\nclass B(Int y)\n"
                + "main() {\n    var a: A = B(1)\n    println(a.x)\n}\n"));
        CompilationResult r = driver.compileSources(sources, root.resolve("out"), Target.JVM, root);
        assertFalse(r.success(), "an unrelated pair must still reject");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM021".equals(d.code())),
                "expected SEM021: " + r.diagnostics().getDiagnostics());
    }
}
