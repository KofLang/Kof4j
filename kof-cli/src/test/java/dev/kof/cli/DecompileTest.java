package dev.kof.cli;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Target;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * kof decompile — structural skeleton .class → .kf (docs/future/DECOMPILER.md).
 * Round-trips a javac-compiled class into Kof source that itself compiles.
 */
class DecompileTest {

    @Test
    void decompileProducesCompilableKof(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Calc.java");
        Files.writeString(javaFile, """
                public class Calc {
                    int total;
                    public int add(int a, int b) { return a + b; }
                    public String greet(String name) { return "hi " + name; }
                }
                """);
        Path classFile = dir.resolve("Calc.class");
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(classFile);

        assertTrue(kof.contains("class Calc"), "should emit class name:\n" + kof);
        assertTrue(kof.contains("Int total"), "should emit field with type:\n" + kof);
        assertTrue(kof.contains("Int add"), "should emit add method:\n" + kof);
        assertTrue(kof.contains("String greet"), "should emit greet method:\n" + kof);
        assertTrue(kof.contains("throw \"body not recovered\""), "bodies must be honest stubs:\n" + kof);
        assertTrue(kof.contains("// unknown"), "bodies must be marked UNKNOWN:\n" + kof);
        assertTrue(kof.contains("// exact"), "fields must be marked EXACT:\n" + kof);

        Path out = dir.resolve("Calc.kf");
        Files.writeString(out, kof);

        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled Kof must compile:\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void decompileGenericSignaturesAreExact(@TempDir Path dir) throws Exception {
        // Fase D (Type Recovery): genéricos só existem no atributo Signature
        // (o descriptor apaga por erasure). O esqueleto deve sair EXACT.
        Path javaFile = dir.resolve("Bag.java");
        Files.writeString(javaFile, """
                import java.util.List;
                import java.util.Map;
                public class Bag {
                    List<String> items;
                    public List<String> get(Map<String, Integer> counts, String key) { return items; }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Bag.class"));
        assertTrue(kof.contains("List<String> items"), "field genérico EXACT:\n" + kof);
        assertTrue(kof.contains("List<String> get"), "retorno genérico EXACT:\n" + kof);
        assertTrue(kof.contains("Map<String, Integer> arg0"), "param genérico EXACT:\n" + kof);
    }

    @Test
    void decompileStaticFieldSkipped(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Const.java");
        Files.writeString(javaFile, """
                public class Const {
                    public static final int MAX = 100;
                    public String label = "x";
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Const.class"));
        assertFalse(kof.contains("MAX"), "static field should be skipped:\n" + kof);
        assertTrue(kof.contains("String label"), "instance field should be kept:\n" + kof);
    }

    @Test
    void recoversSimpleArithmeticBody(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Arith.java");
        Files.writeString(javaFile, """
                public class Arith {
                    public static int add(int a, int b) { return a + b; }
                    public int twice(int x) { return x * 2; }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Arith.class"));

        assertTrue(kof.contains("add(Int arg0, Int arg1) = ("), "add must be recovered as expression body:\n" + kof);
        assertTrue(kof.contains("+"), "must contain arithmetic:\n" + kof);
        assertTrue(kof.contains("twice(Int arg0) = ("), "twice must be recovered (instance, arg shifted by this):\n" + kof);
        assertFalse(kof.contains("throw \"body not recovered\""), "no stub expected for recovered bodies:\n" + kof);

        Path out = dir.resolve("Arith.kf");
        Files.writeString(out, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled must compile:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void recoversComparisonBodies(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Cmp.java");
        Files.writeString(javaFile, """
                public class Cmp {
                    public static boolean isPos(int x) { return x > 0; }
                    public static boolean eq(int a, int b) { return a == b; }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Cmp.class"));

        assertTrue(kof.contains("isPos(Int arg0) = arg0 > 0"), "isPos deve virar comparação:\n" + kof);
        assertTrue(kof.contains("eq(Int arg0, Int arg1) = arg0 == arg1"), "eq deve virar comparação:\n" + kof);
        assertFalse(kof.contains("throw \"body not recovered\""), "não deve haver stub:\n" + kof);

        Path out = dir.resolve("Cmp.kf");
        Files.writeString(out, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void recoversIfElseReturn(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Max.java");
        Files.writeString(javaFile, """
                public class Max {
                    public static int max(int a, int b) { if (a > b) return a; return b; }
                    public static int abs(int x) { if (x >= 0) return x; return -x; }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Max.class"));

        assertTrue(kof.contains("max(Int arg0, Int arg1) = if (arg0 > arg1) arg0 else arg1"),
                "max deve virar if-expression:\n" + kof);
        assertTrue(kof.contains("abs(Int arg0) = if (arg0 >= 0) arg0 else -arg0"),
                "abs deve virar if-expression com negação:\n" + kof);

        Path out = dir.resolve("Max.kf");
        Files.writeString(out, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void recoversWhileLoop(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Loop.java");
        Files.writeString(javaFile, """
                public class Loop {
                    public static int downto(int n) { int i = n; while (i > 0) { i = i - 1; } return i; }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Loop.class"));

        assertTrue(kof.contains("while (v1 > 0)"), "deve ter while:\n" + kof);
        assertTrue(kof.contains("var v1 = arg0"), "deve ter var inicial:\n" + kof);
        assertTrue(kof.contains("return v1"), "deve retornar v1:\n" + kof);
        assertFalse(kof.contains("throw \"body not recovered\""), "não deve ter stub:\n" + kof);

        Path out = dir.resolve("Loop.kf");
        Files.writeString(out, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void bottomTestedLoopRecoversAsDoWhile(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("DoLoop.java");
        Files.writeString(javaFile, """
                public class DoLoop {
                    public static int dec(int i) { do { i = i - 1; } while (i > 0); return i; }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("DoLoop.class"));

        // Bottom-tested loop (do-while): corpo + teste embaixo — o recovery
        // distingue por back-edge self/para-trás no bloco cond e emite
        // `do { } while (c)` (NUNCA um while de corpo vazio — "never invent
        // silently"). Kof tem do-while nativo (training/idioms/control-flow).
        assertTrue(kof.contains("do {"), "deve ter do-while:\n" + kof);
        assertTrue(kof.contains("} while (arg0 > 0)"), "teste embaixo, sem inversão:\n" + kof);
        assertFalse(kof.contains("throw \"body not recovered\""), "não deve ter stub:\n" + kof);
        assertFalse(kof.contains("while (arg0 <= 0)"), "não deve inverter p/ while vazio:\n" + kof);

        Path out = dir.resolve("DoLoop.kf");
        Files.writeString(out, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void recoversNestedWhileLoops(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Nest.java");
        Files.writeString(javaFile, """
                public class Nest {
                    public static int grid(int n) {
                        int s = 0; int i = 0;
                        while (i < n) { int j = 0; while (j < n) { s = s + i * j; j = j + 1; } i = i + 1; }
                        return s;
                    }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Nest.class"));

        // while dentro de while (top-tested, back-edge de bloco posterior p/
        // o header externo): o struct() recursa pelo corpo do loop externo
        // incluindo o header interno — recovery já lida, sem código errado.
        assertTrue(kof.contains("while (v2 < arg0)"), "loop externo:\n" + kof);
        assertTrue(kof.contains("while (v3 < arg0)"), "loop interno:\n" + kof);
        assertFalse(kof.contains("throw \"body not recovered\""), "não deve ter stub:\n" + kof);

        Path out = dir.resolve("Nest.kf");
        Files.writeString(out, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void bottomTestedLoopWithBranchInsideStaysHonestStub(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("DoBr.java");
        Files.writeString(javaFile, """
                public class DoBr {
                    static int OUT = 0;
                    public static int sep(int i) {
                        do { OUT = i; if (i > 100) { OUT = i * 2; } i = i - 3; } while (i > 0);
                        return OUT;
                    }
                    public static int brk(int i) {
                        do { if (i == 3) break; i = i - 1; } while (i > 0);
                        return i;
                    }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("DoBr.class"));

        // do-while com corpo ramificado (diamond/break): o teste fica em bloco
        // SEPARADO do corpo (back-edge p/ bloco anterior) — recuperar isso
        // exige análise de merge estruturado (o corpo com if-join sairia com o
        // ponto de junção emitido dentro de um ramo; break escaparia p/ o pós-
        // loop dentro do corpo). Enquanto isso: stub UNKNOWN honesto — NUNCA
        // while de corpo vazio (código errado, anti-R6).
        assertTrue(kof.contains("throw \"body not recovered\""), "deve degradar p/ stub:\n" + kof);
        assertFalse(kof.contains("do {"), "não deve emitir do-while errado:\n" + kof);
        assertFalse(kof.contains("while ("), "não deve inventar while:\n" + kof);
    }

    @Test
    void diamondJoinShapesStayHonestStub(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Join.java");
        Files.writeString(javaFile, """
                public class Join {
                    public static int contFor(int n) { int s = 0; for (int i = 0; i < n; i++) { if (i % 2 == 0) continue; s += i; } return s; }
                    public static int shortc(int a, int b) { if (a > 0 && b > a) return 1; return 0; }
                    public static int tern(int a) { return a > 0 ? a : -a; }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Join.class"));

        // REGRESSÃO R6 travada: continue (join no incremento), && (curto-
        // circuito com braços que caem no mesmo bloco) e `?:` têm PONTO DE
        // JUNÇÃO compartilhado entre braços. O struct() antigo re-emitia o
        // bloco já emitido (só checava isLoopHeader), gerando código ERRADO
        // mas COMPILÁVEL: o `for+continue` virava um while que PERDIA o
        // incremento no caminho normal; `&&` sugava o return final p/ dentro
        // do else. Agora: re-entrar em bloco que NÃO é o header do loop aberto
        // → recusar → stub UNKNOWN honesto (R6: nunca código errado).
        assertTrue(kof.contains("throw \"body not recovered\""), "continue/&&/?: devem degradar:\n" + kof);
        assertFalse(kof.contains("while (v2 <"), "não deve emitir for errado (sem incremento):\n" + kof);
    }

    @Test
    void floatConstantsDegradeNotDrift(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Fl.java");
        Files.writeString(javaFile, """
                public class Fl {
                    public static float f() { return 3.5f; }
                    public static double d() { return 2.25; }
                    public static int i() { return 42; }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Fl.class"));

        // CP tag-4 (Float) agora decodifica o VALOR (3.5, não os bits crus) —
        // mas Kof não tem literal float inline: emitir "3.5" num método Float
        // drifta p/ Double (SEM010). ldc recusa literais float → stub honesto,
        // igual a Double/Long (ldc2_w). Int constante segue recuperando.
        assertTrue(kof.contains("Int i() = 42"), "int constante deve recuperar:\n" + kof);
        assertFalse(kof.contains("= 3.5"), "float não pode virar literal Double:\n" + kof);
        assertTrue(kof.contains("Float f()"), "assinatura Float exata:\n" + kof);
        assertTrue(kof.contains("throw \"body not recovered\""), "f/d degradam p/ stub:\n" + kof);
    }

    @Test
    void recoversLongDoubleConstBodies(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("LC.java");
        Files.writeString(javaFile, """
                public class LC {
                    public static long zl() { return 0L; }
                    public static long one() { return 1L; }
                    public static double zero() { return 0.0; }
                    public static double uno() { return 1.0; }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("LC.class"));

        // lconst_0/1 (0x09/0x0a) e dconst_0/1 (0x0e/0x0f) carregam o TIPO no
        // opcode — emitir "0L"/"1L"/"0.0"/"1.0" não pode driftar (Kof tem os
        // literais; e o verificador JVM garante const tipo == retorno). Lição
        // aplicada do bug 62 (float ldc ficou recusado — sem literal em Kof).
        assertTrue(kof.contains("= 0L"), "long const deve recuperar com sufixo L:\n" + kof);
        assertTrue(kof.contains("= 1L"), "lconst_1:\n" + kof);
        assertTrue(kof.contains("= 0.0"), "dconst_0:\n" + kof);
        assertTrue(kof.contains("= 1.0"), "dconst_1:\n" + kof);
        assertFalse(kof.contains("body not recovered"), "nenhum deve degradar:\n" + kof);

        Path out = dir.resolve("LC.kf");
        Files.writeString(out, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void recoversMethodCall(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Call.java");
        Files.writeString(javaFile, """
                public class Call {
                    public int add(int a, int b) { return a + b; }
                    public int add4(int x) { return add(x, x); }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Call.class"));

        assertTrue(kof.contains("add4(Int arg0) = this.add(arg0, arg0)"),
                "add4 deve virar chamada de método:\n" + kof);

        Path out = dir.resolve("Call.kf");
        Files.writeString(out, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void recoversFieldAccess(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Box.java");
        Files.writeString(javaFile, """
                public class Box {
                    int value;
                    public int getValue() { return value; }
                    public void setValue(int v) { value = v; }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Box.class"));

        assertTrue(kof.contains("getValue() = this.value"), "getfield deve virar this.value:\n" + kof);
        assertTrue(kof.contains("this.value = arg0"), "putfield deve virar this.value = arg0:\n" + kof);

        Path out = dir.resolve("Box.kf");
        Files.writeString(out, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void recoversObjectCreation(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Node.java");
        Files.writeString(javaFile, """
                public class Node {
                    public Node make() { return new Node(); }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Node.class"));

        assertTrue(kof.contains("make() = Node()"), "new deve virar Node():\n" + kof);

        Path out = dir.resolve("Node.kf");
        Files.writeString(out, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void recoversTryCatch(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Safe.java");
        Files.writeString(javaFile, """
                public class Safe {
                    public static int div(int a, int b) {
                        try { return a / b; }
                        catch (ArithmeticException e) { return 0; }
                    }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Safe.class"));

        assertTrue(kof.contains("try {"), "deve ter try:\n" + kof);
        assertTrue(kof.contains("return (arg0 / arg1)"), "try deve retornar divisão:\n" + kof);
        assertTrue(kof.contains("} catch (String e) {"), "deve ter catch String:\n" + kof);
        assertTrue(kof.contains("return 0"), "catch deve retornar 0:\n" + kof);
        assertFalse(kof.contains("throw \"body not recovered\""), "não deve ter stub:\n" + kof);

        Path out = dir.resolve("Safe.kf");
        Files.writeString(out, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void recoversSwitch(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Sw.java");
        Files.writeString(javaFile, """
                public class Sw {
                    public static int sign(int n) {
                        switch (n) {
                            case 0: return 5;
                            case 1: return 7;
                            default: return 9;
                        }
                    }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Sw.class"));

        assertTrue(kof.contains("switch (arg0)"), "deve ter switch:\n" + kof);
        assertTrue(kof.contains("case 0: return 5"), "case 0:\n" + kof);
        assertTrue(kof.contains("case 1: return 7"), "case 1:\n" + kof);
        assertTrue(kof.contains("default: return 9"), "default:\n" + kof);
        assertFalse(kof.contains("throw \"body not recovered\""), "não deve ter stub:\n" + kof);

        Path out = dir.resolve("Sw.kf");
        Files.writeString(out, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void recoversFinally(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Ctr.java");
        Files.writeString(javaFile, """
                public class Ctr {
                    int calls;
                    public int work(int n) {
                        try { return n * 2; }
                        finally { calls = calls + 1; }
                    }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Ctr.class"));

        assertTrue(kof.contains("try {"), "deve ter try:\n" + kof);
        assertTrue(kof.contains("return (arg0 * 2)"), "try deve retornar expressão:\n" + kof);
        assertTrue(kof.contains("} finally {"), "deve ter finally:\n" + kof);
        assertTrue(kof.contains("this.calls = (this.calls + 1)"), "finally deve incrementar campo:\n" + kof);
        assertFalse(kof.contains("throw \"body not recovered\""), "não deve ter stub:\n" + kof);

        Path out = dir.resolve("Ctr.kf");
        Files.writeString(out, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void mapsStdlib(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Std.java");
        Files.writeString(javaFile, """
                public class Std {
                    public static void hello() { System.out.println("hi"); }
                    public static int size(String s) { return s.length(); }
                    public static boolean same(String a, String b) { return a.equals(b); }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Std.class"));

        assertTrue(kof.contains("println(\"hi\")"), "System.out.println deve virar println:\n" + kof);
        assertFalse(kof.contains("System.out"), "não deve manter System.out:\n" + kof);
        assertTrue(kof.contains("arg0.length"), "String.length() deve virar .length:\n" + kof);
        assertTrue(kof.contains("arg0 == arg1"), "equals deve virar ==:\n" + kof);

        Path out = dir.resolve("Std.kf");
        Files.writeString(out, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void mapsCollectionSize(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Col.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Col {
                    public static int count(List<String> xs) { return xs.size(); }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Col.class"));

        assertTrue(kof.contains("arg0.size"), "size() vira propriedade .size:\n" + kof);
        assertFalse(kof.contains("arg0.size()"), "não deve manter size():\n" + kof);
    }

    @Test
    void mapsParseAndClock(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Parse.java");
        Files.writeString(javaFile, """
                public class Parse {
                    public static int toN(String s) { return Integer.parseInt(s); }
                    public static long when() { return System.currentTimeMillis(); }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Parse.class"));

        assertTrue(kof.contains("arg0.toInt()"), "Integer.parseInt vira .toInt():\\n" + kof);
        assertTrue(kof.contains("now()"), "currentTimeMillis vira now():\\n" + kof);
    }

    private void runJavac(Path javaFile, Path dir) throws IOException, InterruptedException {
        String javaHome = System.getProperty("java.home");
        Path javac = Path.of(javaHome, "bin", "javac");
        ProcessBuilder pb = new ProcessBuilder(javac.toString(), "-d", dir.toString(), javaFile.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        int rc = p.waitFor();
        if (rc != 0) {
            throw new IOException("javac failed: " + new String(p.getInputStream().readAllBytes()));
        }
    }
}