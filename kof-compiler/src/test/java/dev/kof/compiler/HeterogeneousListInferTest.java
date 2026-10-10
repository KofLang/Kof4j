package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #360 — `listOf()` with heterogeneous related subtypes inferred the
 * element type from the FIRST argument and emitted `checkcast FirstType`
 * for every `get()`: `listOf(new Dog(), new Cat())` printed `woof` then
 * died on `ClassCastException: Cat cannot be cast to Dog`. The same root
 * ran through the set side (`setOf`). Uncollected related types now widen
 * to their common ancestor (interface OR superclass — the old
 * commonSupertype only walked superclasses); when there is no named common
 * ancestor the pre-fix first-wins stays (SEM056 already rejects truly
 * heterogeneous literals, and nothing that works today changes).
 */
class HeterogeneousListInferTest extends JvmRunSupport {

    private static final String ANIMALS = """
            interface Animal { String sound() }
            class Dog implements Animal { String sound() { return "woof" } }
            class Cat implements Animal { String sound() { return "meow" } }
            main() {
              var animals = listOf(new Dog(), new Cat())
              println(animals.get(0).sound())
              println(animals.get(1).sound())
            }
            """;

    private CompilationResult compile(Path tempDir, String name, String program, Target t) throws Exception {
        Path source = tempDir.resolve(name + ".kf");
        Files.writeString(source, program);
        return new CompilerDriver().compile(source, tempDir.resolve("out-" + name + t), t);
    }

    
    @Test
    void heterogeneousListWidensToCommonInterface(@TempDir Path tempDir) throws Exception {
        // #360 verbatim — pre-fix ran `woof` then ClassCastException; the
        // proof EXECUTES the two-line oracle `woof\nmeow`.
        CompilationResult r = compile(tempDir, "V", ANIMALS, Target.JVM);
        assertTrue(r.success(), "#360 verbatim must compile: " + r.diagnostics().getDiagnostics());
        assertRuns(tempDir.resolve("out-VJVM"), "woof\nmeow", "#360 verbatim");
    }

    @Test
    void commonSuperclassAndSetFace(@TempDir Path tempDir) throws Exception {
        // faces: the ancestor can be a SUPERCLASS (not only an interface),
        // and `setOf` shares the root. Goldens measured on the CLI oracle.
        CompilationResult r = compile(tempDir, "S", """
                class Pet { String name = "p" }
                class A extends Pet { }
                class B extends Pet { }
                interface Flyer { String fly() }
                class Bird implements Flyer { String fly() { return "fly" } }
                class Plane implements Flyer { String fly() { return "zoom" } }
                main() {
                  var ab = listOf(new A(), new B())
                  println(ab.get(0).name)
                  println(ab.get(1).name)
                  val b = new Bird()
                  var s = setOf(b, new Plane())
                  println(s.contains(b))
                }
                """, Target.JVM);
        assertTrue(r.success(), "superclass+set faces must compile: " + r.diagnostics().getDiagnostics());
        assertRuns(tempDir.resolve("out-SJVM"), "p\np\ntrue", "#360 superclass+set");
    }

    @Test
    void homogeneousAndPrimitiveInferenceUnchanged(@TempDir Path tempDir) throws Exception {
        // r1 controls: all-same-type list keeps the CONCRETE type (`.a()`
        // only exists on A), primitive list keeps numeric inference.
        CompilationResult r = compile(tempDir, "C", """
                class Pet { String name = "p" }
                class A extends Pet { String a() { return "A" } }
                main() {
                  var dogs = listOf(new A(), new A())
                  println(dogs.get(0).a())
                  var ints = listOf(1, 2)
                  println(ints.get(0) + 1)
                }
                """, Target.JVM);
        assertTrue(r.success(), "controls must compile: " + r.diagnostics().getDiagnostics());
        assertRuns(tempDir.resolve("out-CJVM"), "A\n2", "#360 controls");
    }

    @Test
    void recordSubtypesWidenToCommonInterface(@TempDir Path tempDir) throws Exception {
        // #596 — records (unlike classes) carry the structural `extends Record`
        // as their stored superclass; the BFS queued it BEFORE the record's own
        // interfaces, so the common ancestor resolved to the unqualified
        // `Record` and every `get()` emitted `checkcast Record` ->
        // NoClassDefFoundError: Record at class load. Must widen to `Shape`.
        CompilationResult r = compile(tempDir, "R", """
                interface Shape
                record Circle(Int r) implements Shape
                record Square(Int s) implements Shape
                main() {
                  var shapes = listOf(Circle(1), Square(2))
                  for (var sh in shapes) {
                    println(sh)
                  }
                }
                """, Target.JVM);
        assertTrue(r.success(), "#596 records must compile: " + r.diagnostics().getDiagnostics());
        assertRuns(tempDir.resolve("out-RJVM"), "Circle[r=1]\nSquare[s=2]", "#596 records -> interface");
    }

    @Test
    void heterogeneousListCompilesOnNativeAndJs(@TempDir Path tempDir) throws Exception {
        // rule 5: the inference is shared IR — CLI measured `woof/meow` on
        // the fixed build; gate compilation for the artifact targets.
        for (Target t : new Target[]{Target.NATIVE, Target.JS}) {
            CompilationResult r = compile(tempDir, "P", ANIMALS, t);
            assertTrue(r.success(), t + " must compile: " + r.diagnostics().getDiagnostics());
        }
    }

    @Test
    void recordSubtypesCompileOnNativeAndJs(@TempDir Path tempDir) throws Exception {
        // #596 cross-target: the widening fix is shared IR; gate that records
        // implementing a shared interface also compile on the artifact targets.
        for (Target t : new Target[]{Target.NATIVE, Target.JS}) {
            CompilationResult r = compile(tempDir, "PR", """
                    interface Shape
                    record Circle(Int r) implements Shape
                    record Square(Int s) implements Shape
                    main() {
                      var shapes = listOf(Circle(1), Square(2))
                      println(shapes.size())
                    }
                    """, t);
            assertTrue(r.success(), t + " records must compile: " + r.diagnostics().getDiagnostics());
        }
    }
}
