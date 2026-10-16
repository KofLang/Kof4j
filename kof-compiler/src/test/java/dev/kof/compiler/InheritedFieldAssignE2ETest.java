package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BUG "Atribuição de campo herdado vira Object" — a escrita de um campo
 * declarado na SUPERCLASSE vertia o valor num temporário `Object`
 * (`Object var11 = w; this.width = var11;` descompilado) e o PUTFIELD saía
 * com owner `?`/descritor `Ljava/lang/Object;` → NoClassDefFoundError: "?"
 * em runtime. Causa raiz (família única — "identidade da superclasse não
 * canônica"):
 *
 *  1. `super` como receiver NÃO era tipado em lugar nenhum:
 *     ExpressionTyper/SemExpressionTyper deixavam UNKNOWN, o lowerField não
 *     resolvia o campo e o spill `KofStoreLocal(UNKNOWN,...)` era o temp
 *     Object. Fonte única agora: HierarchyResolver.superTypeOf (espelha o
 *     special-case que só o READ tinha).
 *  2. BFS de membros (MemberResolver) não normalizava o nome de super
 *     ARMAZENADO — import explícito qualifica ("foo.bar.Shape"), wildcard
 *     deixa simples, genéricos grudam ("Box<T>") — e o registro é por nome
 *     simples. A cadeia quebrava em silêncio → SEM025/SEM011 falsos em
 *     herança cross-package → fieldType UNKNOWN de novo.
 *  3. Emissão do super_class usava o nome cru ("Shape") quando o arquivo da
 *     superclasse morava em package → NoClassDefFoundError: Shape.
 *     Registry-first (§308): canonicalSuperInternal.
 */
class InheritedFieldAssignE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    // ---------- helpers ----------

    private Path compileJvm(Path root, List<Path> sources, String tag) {
        Path outDir = root.resolve("out-" + tag);
        CompilationResult result = driver.compileSources(sources, outDir, Target.JVM, root);
        assertTrue(result.success(), tag + " JVM compile failed: " + result.diagnostics().getDiagnostics());
        return outDir;
    }

    private String runJvm(Path outDir, String tag, String expected) throws Exception {
        Path javaHome = Path.of(System.getProperty("java.home"));
        Process p = new ProcessBuilder(javaHome.resolve("bin").resolve("java").toString(),
                "-cp", outDir.toAbsolutePath().toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        if (!p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
            p.destroyForcibly();
            fail(tag + " JVM run hung (>30s)");
        }
        assertEquals(0, p.exitValue(), tag + " JVM exit code, output: " + output);
        assertEquals(expected, output, tag + " JVM output");
        return output;
    }

    private String javap(Path outDir, String binaryName) throws Exception {
        Path javaHome = Path.of(System.getProperty("java.home"));
        Process p = new ProcessBuilder(javaHome.resolve("bin").resolve("javap").toString(),
                "-c", "-p", "-l", "-cp", outDir.toAbsolutePath().toString(), binaryName)
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        if (!p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
            p.destroyForcibly();
            fail("javap hung (>30s)");
        }
        assertEquals(0, p.exitValue(), "javap exit: " + out);
        return out;
    }

    /**
     * O contrato do bug: TODO store do campo `width` resolve no dono
     * declarado com o descritor PRIMITIVO do campo (F/J/I) — nunca
     * `Ljava/lang/Object` (o temporário Object do relatório).
     */
    private void assertNoObjectFieldStore(String javapOut, String tag) {
        for (String line : javapOut.split("\n")) {
            String l = line.trim();
            if (l.startsWith("putfield") || l.startsWith("getfield")) {
                assertFalse(l.contains("Ljava/lang/Object") && l.contains("width"),
                        tag + ": campo herdado store/load com descritor Object: [" + l
                                + "] — o valor foi vertido num temporário Object");
            }
        }
        assertTrue(javapOut.contains("width:F") || javapOut.contains("width:J") || javapOut.contains("width:I"),
                tag + ": javap não mostra o PUTFIELD/GETFIELD tipado do campo herdado:\n" + javapOut);
    }

    private Path write(Path root, String rel, String src) throws Exception {
        Path f = root.resolve(rel);
        Files.createDirectories(f.getParent() == null ? root : f.getParent());
        Files.writeString(f, src);
        return f;
    }

    // ---------- 1. super.field = v (o sintoma exato do relatório) ----------

    @Test
    void superFieldWriteKeepsConcreteType(@TempDir Path tmp) throws Exception {
        Path main = write(tmp, "Main.kf", """
                class Shape {
                    Float width
                }
                class Rect extends Shape {
                    setWidth(Float w) {
                        super.width = w
                        println(super.width)
                    }
                }
                main() {
                    Rect().setWidth(3.5f)
                }
                """);
        Path out = compileJvm(tmp, List.of(main), "superw");
        runJvm(out, "superw", "3.5");
        String j = javap(out, "Rect");
        assertNoObjectFieldStore(j, "superw");
        assertTrue(j.contains("Field Shape.width:F"),
                "superw: esperado putfield/getfield em Shape.width:F —\n" + j);
        // O sintoma original do relatório (`Object var11 = w`) foi corrigido:
        // o valor vertia como FLOAT (fstore num temp). O re-baseline (13/10,
        // D-CODEGEN-REWRITE/value-propagation) remove o TEMP redundante — o
        // valor agora flui direto do parâmetro (`fload_1`) para o putfield,
        // byte-comportamento idêntico (run imprime 3.5; putfield/getfield
        // tipados em Shape.width:F; sem Object store). O contrato é o
        // COMPORTAMENTO, não a forma do temp.
        assertTrue(j.contains("fload_1") || j.contains("fload"),
                "superw: o valor deve fluir como FLOAT até o putfield —\n" + j);
    }

    @Test
    void superFieldCompoundWrite(@TempDir Path tmp) throws Exception {
        Path main = write(tmp, "Main.kf", """
                class Shape {
                    Int width
                }
                class Rect extends Shape {
                    go(Int w) {
                        super.width = w
                        super.width += 8
                        println(super.width)
                    }
                }
                main() {
                    Rect().go(34)
                }
                """);
        Path out = compileJvm(tmp, List.of(main), "superadd");
        runJvm(out, "superadd", "42");
        assertNoObjectFieldStore(javap(out, "Rect"), "superadd");
    }

    // ---------- 2. `this.width` / bare no mesmo arquivo (forma citada) ----------

    @Test
    void thisFieldWriteOfSuperclassFieldKeepsConcreteType(@TempDir Path tmp) throws Exception {
        Path main = write(tmp, "Main.kf", """
                class Shape {
                    Float width
                }
                class Rect extends Shape {
                    setWidth(Float w) {
                        this.width = w
                        println(this.width)
                    }
                }
                main() {
                    Rect().setWidth(3.5f)
                }
                """);
        Path out = compileJvm(tmp, List.of(main), "thisw");
        runJvm(out, "thisw", "3.5");
        assertNoObjectFieldStore(javap(out, "Rect"), "thisw");
    }

    // ---------- 3. super cross-package com import EXPLÍCITO (super armazenado
    //              pontuado "foo.bar.Shape" — o BFS cru quebrava → SEM025 falso
    //              e fieldType UNKNOWN) ----------

    @Test
    void explicitImportCrossPackageSuperFieldWrite(@TempDir Path tmp) throws Exception {
        Path shape = write(tmp, "foo/bar/Shape.kf", """
                package foo.bar

                class Shape {
                    Float width
                }
                """);
        Path main = write(tmp, "Main.kf", """
                import foo.bar.Shape

                class Rect extends Shape {
                    go() {
                        var w = 3.5f
                        this.width = w
                        println(this.width)
                    }
                }

                main() {
                    Rect().go()
                }
                """);
        Path out = compileJvm(tmp, List.of(main, shape), "xpkg");
        runJvm(out, "xpkg", "3.5");
        String j = javap(out, "Rect");
        assertNoObjectFieldStore(j, "xpkg");
        // O PUTFIELD é emitido no tipo do RECEIVER (Rect) — javap omite o
        // owner quando é a própria classe (`Field width:F`). A resolução JVM
        // sobe até o dono declarado (foo/bar Shape) — o que o bug quebrava
        // era o descritor (Object) e o owner `?`, nunca o nome do declarador.
        assertTrue(j.contains("width:F"),
                "xpkg: PUTFIELD deve sair tipado F —\n" + j);
    }

    // ---------- 4. super cross-package com WILDCARD (super armazenado SIMPLES
    //              "Shape" — a emissão crua do super_class dava
    //              NoClassDefFoundError: Shape no load) ----------

    @Test
    void wildcardImportCrossPackageSuperEmitsCanonicalSuper(@TempDir Path tmp) throws Exception {
        Path shape = write(tmp, "foo/bar/Shape.kf", """
                package foo.bar

                class Shape {
                    Float width
                }
                """);
        Path main = write(tmp, "Main.kf", """
                import foo.bar.*

                class Rect extends Shape {
                    go() {
                        var w = 3.5f
                        this.width = w
                        println(this.width)
                    }
                }

                main() {
                    Rect().go()
                }
                """);
        Path out = compileJvm(tmp, List.of(main, shape), "xpkgw");
        runJvm(out, "xpkgw", "3.5");
        String j = javap(out, "Rect");
        assertNoObjectFieldStore(j, "xpkgw");
        assertTrue(j.contains("extends foo.bar.Shape"),
                "xpkgw: super_class deve ser o nome canônico foo/bar/Shape —\n" + j);
    }

    // ---------- 5. campo herdado por nome NUDEIRO com super pontuado
    //              (SEM011 falso antes da normalização do BFS) ----------

    @Test
    void bareInheritedFieldWriteWithDottedSuper(@TempDir Path tmp) throws Exception {
        Path shape = write(tmp, "foo/bar/Shape.kf", """
                package foo.bar

                class Shape {
                    Float width
                }
                """);
        Path main = write(tmp, "Main.kf", """
                import foo.bar.Shape

                class Rect extends Shape {
                    go() {
                        var w = 3.5f
                        width = w
                        println(width)
                    }
                }

                main() {
                    Rect().go()
                }
                """);
        Path out = compileJvm(tmp, List.of(main, shape), "bare");
        runJvm(out, "bare", "3.5");
        assertNoObjectFieldStore(javap(out, "Rect"), "bare");
    }

    // ---------- 6. duas níveis: `super.x` mira o pai; campo do AVÔ resolve
    //              pelo BFS a partir do pai ----------

    @Test
    void twoLevelSuperFieldWriteInheritedFromGrandparent(@TempDir Path tmp) throws Exception {
        Path main = write(tmp, "Main.kf", """
                class Base {
                    Long v
                }
                class Mid extends Base {
                }
                class Rect extends Mid {
                    bump(Long x) {
                        super.v = x
                        println(super.v)
                        println(this.v)
                    }
                }
                main() {
                    Rect().bump(42)
                }
                """);
        Path out = compileJvm(tmp, List.of(main), "twolevel");
        runJvm(out, "twolevel", "42\n42");
        String j = javap(out, "Rect");
        assertFalse(j.contains("v:Ljava/lang/Object"), "twolevel: campo Long herdado vertido a Object —\n" + j);
        assertTrue(j.contains("Field Mid.v:J"), "twolevel: putfield tipado J no receiver Mid (resolve no dono Base) —\n" + j);
    }

    @Test
    void staticSuperFieldWriteGoesPutStatic(@TempDir Path tmp) throws Exception {
        Path main = write(tmp, "Main.kf", """
                class Base {
                    static Int count
                }
                class Rect extends Base {
                    bump(Int v) {
                        super.count = v
                        println(count)
                    }
                }
                main() {
                    Rect().bump(42)
                }
                """);
        Path out = compileJvm(tmp, List.of(main), "staticsuper");
        runJvm(out, "staticsuper", "42");
        String j = javap(out, "Rect");
        assertTrue(j.contains("putstatic") && j.contains("Base.count:I"),
                "staticsuper: campo ESTÁTICO da super via `super.` deve ser putstatic tipado —\n" + j);
        assertFalse(j.contains("putfield") && j.contains("count"),
                "staticsuper: putfield num campo estático = bytecode inválido —\n" + j);
    }

    // ---------- 7. paridade cross-target do `super.width` (Q3) ----------

    @Test
    void superFieldWriteParityScriptTarget(@TempDir Path tmp) throws Exception {
        Path main = write(tmp, "Main.kf", """
                class Shape {
                    Float width
                }
                class Rect extends Shape {
                    setWidth(Float w) {
                        super.width = w
                        println(super.width)
                    }
                }
                main() {
                    Rect().setWidth(3.5f)
                }
                """);
        KofInterpreter.Result r = driver.interpret(List.of(main), tmp, new String[0]);
        assertEquals(0, r.exitCode(), "superw SCRIPT exit, output: " + r.stdout());
        assertEquals("3.5", r.stdout().trim().replace("\r\n", "\n"), "superw SCRIPT output");
    }
}
