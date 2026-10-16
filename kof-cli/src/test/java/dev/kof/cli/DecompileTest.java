package dev.kof.cli;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Target;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * kof decompile — structural skeleton .class → .kf (docs/development/DECOMPILER.md).
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
                    public static float noLit() { return 1.5f; }
                }
                """);
        Path classFile = dir.resolve("Calc.class");
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(classFile);

        assertTrue(kof.contains("class Calc"), "should emit class name:\n" + kof);
        assertTrue(kof.contains("Int total"), "should emit field with type:\n" + kof);
        assertTrue(kof.contains("Int add"), "should emit add method:\n" + kof);
        assertTrue(kof.contains("String greet"), "should emit greet method:\n" + kof);
        // noLit (float ldc, sem literal float em Kof) degrada p/ stub honesto
        // enquanto add/greet recuperam — o smoke valida os dois juntos.
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
        // 14/10: os wrappers mapeiam p/ o primitivo Kof (Integer→Int) —
        // o `Integer` cru era indefinido no Kof e o decompilado NÃO compilava
        assertTrue(kof.contains("Map<String, Int> arg0"), "param genérico EXACT (Int):\n" + kof);
        assertFalse(kof.contains("Integer"), "wrapper indefinido não sai:\n" + kof);
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
    void comparisonReturnRespectsBoolVsIntReturnType(@TempDir Path dir) throws Exception {
        // §236 (14/09): o shape cmp/iconst_1/goto/iconst_0/ireturn e AMBIGUO na
        // raiz — `return x > 0` (Bool) e `return a < b ? 1 : 0` (Int) temo MESMO
        // bytecode. O fold antigo sempre devolvia o Bool cru: num corpo Int a
        // saida decompilada virava Bool e o typer rejeitava (SEM010 —
        // nao-compilavel, anti-R6). O fold agora PORTA pelo retType: Z → cru,
        // I → if-expression de inteiros. Prova: as DUAS formas no MESMO arquivo
        // + RECOMPILACAO (string so nao basta — foi o pino errado que escondeu
        // o bug por um dia).
        Path javaFile = dir.resolve("Cmp2.java");
        Files.writeString(javaFile, """
                public class Cmp2 {
                    public static boolean gt(int x) { return x > 0; }
                    public static int oneIfLt(int a, int b) { return a < b ? 1 : 0; }
                    public static int oneIfNe(int x) { return x != 0 ? 1 : 0; }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Cmp2.class"));

        assertTrue(kof.contains("Bool gt(Int arg0) = arg0 > 0"),
                "corpo Z preserva o Bool cru (byte-identico ao que ja passava):\n" + kof);
        assertTrue(kof.contains("Int oneIfLt(Int arg0, Int arg1) = if (arg0 < arg1) 1 else 0"),
                "corpo I dobra ternario como if-expression (nao Bool cru):\n" + kof);
        assertTrue(kof.contains("Int oneIfNe(Int arg0) = if (arg0 != 0) 1 else 0"),
                "face unaria (ifne) tambem porta p/ Int:\n" + kof);
        assertFalse(kof.contains("= arg0 != 0\n"), "nunca emitir `= <bool>` num corpo Int:\n" + kof);

        Path out = dir.resolve("Cmp2.kf");
        Files.writeString(out, kof);
        CompilationResult result = new CompilerDriver().compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "as 3 formas decompiladas devem compilar:\n" + kof
                + "\n" + result.diagnostics().getDiagnostics());
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
    void shortcircuitAndTernaryRecoveredZeroDrift(@TempDir Path dir) throws Exception {
        // Fase C (13/10): `&&` (combinação de condições — a cadeia de
        // branches do bytecode É o curto-circuito) e o ternário
        // (if-expression Kof) RECUPERAM — a face antiga os travava como stub
        // com um oráculo errado (corrigido na rodada do contFor).
        Path javaFile = dir.resolve("Join.java");
        Files.writeString(javaFile, """
                public class Join {
                    public static int shortc(int a, int b) { if (a > 0 && b > a) return 1; return 0; }
                    public static int tern(int a) { return a > 0 ? 1 : -1; }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Join.class"));
        assertFalse(kof.contains("body not recovered"),
                "&& e ?: recuperam (nao stub):\n" + kof);
        assertTrue(kof.contains("arg0 > 0 && arg1 > arg0"), kof);
        // o ternário agora recupera pela via de EXPRESSÃO (armExpr do
        // ifElseReturn, 13/10) — corpo `= if (..)`; a forma `return if (..)`
        // (statement) é equivalente. O substring abaixo cobre as duas.
        assertTrue(kof.contains("if (arg0 > 0) 1 else -1"), kof);
        // zero-drift executado contra o oráculo Java
        String kofRun = kof + "\nmain() {\n    var sc = Join()\n    println(Join.shortc(2, 5))\n    println(Join.shortc(2, 1))\n    println(Join.tern(4))\n    println(Join.tern(-4))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("1\n0\n1\n-1", output, "zero-drift: igual ao Java");
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
    void recoversLdc2LongDoubleConstants(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("L2.java");
        Files.writeString(javaFile, """
                public class L2 {
                    public static long big() { return 9999999999L; }
                    public static double frac() { return 2.25; }
                    public static double expo() { return 1.0E-5; }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("L2.class"));

        // ldc2_w (0x14) só aponta p/ CP Long/Double — classificação por FORMA
        // (lição bug 62): dígitos → sufixo L; '.'/E → Double literal (Kof
        // aceita "1.0E-5"); NaN/Infinity recusados (sem literal em Kof).
        assertTrue(kof.contains("= 9999999999L"), "long ldc2:\n" + kof);
        assertTrue(kof.contains("= 2.25"), "double ldc2:\n" + kof);
        assertTrue(kof.contains("= 1.0E-5"), "double expo ldc2:\n" + kof);

        Path out = dir.resolve("L2.kf");
        Files.writeString(out, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void multiArgConcatKeepsArgumentOrder(@TempDir Path dir) throws Exception {
        // REGRESSÃO (13/10): a concat com 2+ placeholders saía com a ORDEM
        // INVERTIDA ("cba" p/ `a + b + c`) — o caller re-invertia a pilha e
        // o BytecodeConcat.apply re-invertia de novo (double-reverse); a face
        // antiga só tinha 1 placeholder, então nunca pegou. Zero-drift
        // executado: o Kof decompilado RODA e dá o mesmo do Java.
        Path javaFile = dir.resolve("Mc.java");
        Files.writeString(javaFile, """
                public class Mc {
                    static String three(String a, String b, String c) { return a + b + c; }
                    static String four(String a, String b, String c, String d) { return a + "-" + b + c + d; }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Mc.class"));
        assertTrue(kof.contains("arg0 + arg1 + arg2"),
                "concat de 3 args mant\u00e9m a ordem:\n" + kof);
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + "\nmain() {\n    println(Mc.three(\"a\", \"b\", \"c\"))\n    println(Mc.four(\"w\", \"x\", \"y\", \"z\"))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("abc\nw-xyz", output, "zero-drift: ordem da concat correta");
    }

    @Test
    void recoversStringConcatInvokedynamic(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Cat.java");
        Files.writeString(javaFile, """
                public class Cat {
                    public static String greet(String n) { return "v=" + n + 42; }
                    public static String simple(String a) { return a + "x"; }
                    public static String mid(int i) { return "i" + i + "j"; }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Cat.class"));

        // String + em Java moderno (9+) é invokedynamic makeConcatWithConstants
        // — a forma de corpo String MAIS comum. Sem recovery de receita, TODO
        // corpo com concat caía em stub. O parser agora lê BootstrapMethods e
        // o decoder aplica a receita (\u0001 = placeholder) → `a + "x" + b`.
        assertTrue(kof.contains("greet(String arg0) = \"v=\" + arg0 + \"42\""),
                "concat 'v=' + n + 42:\n" + kof);
        assertTrue(kof.contains("simple(String arg0) = arg0 + \"x\""),
                "concat 'a' + \"x\":\n" + kof);
        assertTrue(kof.contains("\"i\" + arg0 + \"j\""),
                "concat no meio:\n" + kof);
        assertFalse(kof.contains("body not recovered"), "nenhum deve degradar:\n" + kof);

        Path out = dir.resolve("Cat.kf");
        Files.writeString(out, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void recoversLdcWAndAconstNull() {
        // Corpus-real coverage (histograma de opcodes do kof-compiler): ldc_w
        // (0x13, índice u2 p/ CP>255) era o opcode NÃO-tratado mais frequente
        // (~3k ocorrências) e aconst_null (0x01) ~800. ldc_w é o MESMO ldc já
        // tratado (BytecodeReader já lê o índice de 2 bytes) — teste manual:
        // bytecode `ldc_w #300; areturn` com cp[300]="#301", cp[301]="oi".
        String[] cp = new String[302];
        cp[300] = "#301";
        cp[301] = "oi";
        byte[] code = {0x13, 0x01, 0x2C, (byte) 0xb0};            // ldc_w 300; areturn
        String e = BytecodeDecoder.recoverExpression(code, cp,
                new BytecodeFrame("()Ljava/lang/String;", true));
        assertEquals("\"oi\"", e, "ldc_w recupera igual ao ldc");

        byte[] nul = {0x01, (byte) 0xb0};                          // aconst_null; areturn
        assertEquals("null", BytecodeDecoder.recoverExpression(nul, cp,
                new BytecodeFrame("()Ljava/lang/String;", true)), "aconst_null → null");
    }

    @Test
    void decompileTreeEmitsPackageAndResolvesCrossFileReference(@TempDir Path dir) throws Exception {
        // §7 multi-classe degrau 1: `kof decompile <dir>` espelha a árvore com
        // `package`, e o frontend resolve tipo de MESMO pacote entre arquivos
        // SEM import (probe PKG004) → referência cross-file deixa de ser
        // "Undefined variable or type" (as 4 drifts restantes no corpus).
        Path src = dir.resolve("classes");
        Path pkg = src.resolve("p");
        Files.createDirectories(pkg);
        Path b = pkg.resolve("B.java");
        Files.writeString(b, """
                package p;
                public class B { public int v; public B(int v) { this.v = v; } }
                """);
        Path c = pkg.resolve("C.java");
        Files.writeString(c, """
                package p;
                public class C {
                    public static int use(B b) { return b.v; }
                }
                """);
        runJavac(List.of(b, c), pkg);
        Path out = dir.resolve("gen");
        Decompile.decompileTree(src, out);
        String bSrc = Files.readString(out.resolve("p/B.kf"));
        String cSrc = Files.readString(out.resolve("p/C.kf"));
        assertTrue(bSrc.contains("\npackage p\n"), "decompileTree deve emitir package:\n" + bSrc);
        assertTrue(cSrc.contains("use(B"), "C referencia B por nome simples:\n" + cSrc);
        // prova de fogo: o PAR compilado junto (mesmo pacote) resolve B em C —
        // sem o modo multi-arquivo, C.kf sozinho daria SEM011.
        CompilationResult r = new CompilerDriver().compileSources(List.of(
                out.resolve("p/B.kf"), out.resolve("p/C.kf")), dir.resolve("o"), Target.JVM, out);
        assertTrue(r.success(), "cross-file mesmo pacote deve compilar:\n" + cSrc + "\n" + r.diagnostics().getDiagnostics());
    }

    @Test
    void decompileTreeResolvesSamePackageInstanceofAndCast(@TempDir Path dir) throws Exception {
        // §7 degrau 2: com o índice da árvore, instanceof/checkcast de classe
        // de DOMÍNIO do MESMO pacote recuperam (antes: stub honesto); o par
        // decompilado junto compila (zero drift). Fora do índice → stub.
        Path src = dir.resolve("classes");
        Path pkg = src.resolve("p");
        Files.createDirectories(pkg);
        Path b = pkg.resolve("B.java");
        Files.writeString(b, """
                package p;
                public class B { public int v; public B(int v) { this.v = v; } }
                """);
        Path c = pkg.resolve("C.java");
        Files.writeString(c, """
                package p;
                public class C {
                    public static boolean isB(Object o) { boolean x = o instanceof B; return x; }
                    public static int getV(Object o) { B bb = (B) o; int v = bb.v; return v; }
                }
                """);
        runJavac(java.util.List.of(b, c), pkg);
        Path out = dir.resolve("gen");
        assertEquals(0, Decompile.decompileTree(src, out));
        String cSrc = Files.readString(out.resolve("p/C.kf"));
        assertTrue(cSrc.contains("arg0 instanceof B"),
                "instanceof de domínio same-package deve recuperar:\n" + cSrc);
        assertTrue(cSrc.contains("(arg0 as B)"),
                "checkcast de domínio same-package deve recuperar:\n" + cSrc);
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                out.resolve("p/B.kf"), out.resolve("p/C.kf")), dir.resolve("o"), Target.JVM, out);
        assertTrue(r.success(), "par cross-file deve compilar (zero drift):\n" + cSrc + "\n" + r.diagnostics().getDiagnostics());
    }

    @Test
    void decompileTreeStillStubsOutOfTreeDomainTypes(@TempDir Path dir) throws Exception {
        // Recusa R6 preservada: classe de domínio que NÃO está na árvore
        // (outro pacote, sem índice) continua stub honesto — nunca inventa.
        Path src = dir.resolve("classes");
        Path pkg = src.resolve("p");
        Files.createDirectories(pkg);
        Path b = pkg.resolve("B.java");
        Files.writeString(b, """
                package p;
                public class B { public int v; }
                """);
        Path q = src.resolve("q");
        Files.createDirectories(q);
        Path c = q.resolve("C.java");
        Files.writeString(c, """
                package q;
                import p.B;
                public class C {
                    public static boolean isB(Object o) { boolean x = o instanceof B; return x; }
                }
                """);
        runJavac(java.util.List.of(b), pkg);
        runJavac(java.util.List.of(b, c), src);
        Path out = dir.resolve("gen");
        // decompila SÓ q (B fora da árvore) → instanceof p.B não resolve
        Path onlyQ = dir.resolve("onlyQ");
        Files.createDirectories(onlyQ.resolve("q"));
        Files.copy(src.resolve("q/C.class"), onlyQ.resolve("q/C.class"));
        assertEquals(0, Decompile.decompileTree(onlyQ, out));
        String cSrc = Files.readString(out.resolve("q/C.kf"));
        assertTrue(cSrc.contains("body not recovered"),
                "instanceof cross-package fora do índice fica stub:\n" + cSrc);
    }

    @Test
    void decompileTreeEmitsImportsForCrossPackageDomainRefs(@TempDir Path dir) throws Exception {
        // §7 degrau 3: referência de domínio cross-package (extends,
        // instanceof, new) sai com `import` e o par compila junto
        // (antes: nome simples sem import → SEM011).
        Path src = dir.resolve("classes");
        Path p = src.resolve("p");
        Path q = src.resolve("q");
        Files.createDirectories(p);
        Files.createDirectories(q);
        Path b = p.resolve("B.java");
        Files.writeString(b, """
                package p;
                public class B { public int v; public B(int v) { this.v = v; } }
                """);
        Path c = q.resolve("C.java");
        Files.writeString(c, """
                package q;
                import p.B;
                public class C extends B {
                    public C(int v) { super(v); }
                    public static boolean isB(Object o) { boolean x = o instanceof B; return x; }
                    public static B make() { return new B(3); }
                }
                """);
        runJavac(java.util.List.of(b, c), src);
        Path out = dir.resolve("gen");
        assertEquals(0, Decompile.decompileTree(src, out));
        String cSrc = Files.readString(out.resolve("q/C.kf"));
        assertTrue(cSrc.contains("import p.B"),
                "uso cross-package deve gerar import:\n" + cSrc);
        assertTrue(cSrc.contains("arg0 instanceof B") && cSrc.contains("= B(3)"),
                "extends/instanceof/new devem recuperar:\n" + cSrc);
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                out.resolve("p/B.kf"), out.resolve("q/C.kf")), dir.resolve("o"), Target.JVM, out);
        assertTrue(r.success(), "árvore com import deve compilar (zero drift):\n" + cSrc + "\n" + r.diagnostics().getDiagnostics());
    }

    @Test
    void superCallRecoveredNotStubbed(@TempDir Path dir) throws Exception {
        // Fase G (diamond super.m(), mantenedora 14/10): `invokespecial` de
        // superclasse emitia stub ("body not recovered"); `super.m()` É
        // expressável em Kof (medido rodando) — o corpo recupera o call.
        Path src = dir.resolve("classes");
        Path b = dir.resolve("B.java");
        Files.writeString(b, """
                public class B { public int base() { return 10; } }
                """);
        Path c = dir.resolve("C.java");
        Files.writeString(c, """
                public class C extends B {
                    public int viaSuper() { return super.base(); }
                }
                """);
        runJavac(java.util.List.of(b, c), src);
        Path out = dir.resolve("gen");
        assertEquals(0, Decompile.decompileTree(src, out));
        String cSrc = Files.readString(out.resolve("C.kf"));
        assertTrue(cSrc.contains("return super.base()"),
                "super.m() deve ser RECUPERADO (nao stub):\n" + cSrc);
        assertFalse(cSrc.contains("body not recovered"),
                "nenhum stub no arquivo:\n" + cSrc);
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                out.resolve("B.kf"), out.resolve("C.kf")), dir.resolve("o"), Target.JVM, out);
        assertTrue(r.success(), "arvore com super.m() deve compilar (zero drift):\n" + cSrc
                + "\n" + r.diagnostics().getDiagnostics());
    }

    @Test
    void interfaceSuperDefaultInlinedNotStubbed(@TempDir Path dir) throws Exception {
        // Fase G (diamond, mantenedora 14/10): `return A.super.m();` — o
        // corpo do default da interface é lido do classfile irmão e INLINADO
        // no call site (mesmo pipeline); stub nunca é opção (Q7).
        Path src = dir.resolve("classes");
        Path a = dir.resolve("A.java");
        Files.writeString(a, """
                public interface A {
                    default int m() { return 1; }
                    default int twice(int x) { return x * 2; }
                }
                """);
        Path c = dir.resolve("C.java");
        Files.writeString(c, """
                public class C implements A {
                    public int viaInterface() { return A.super.m(); }
                    public int viaArgs(int x) { return A.super.twice(x); }
                }
                """);
        runJavac(java.util.List.of(a, c), src);
        Path out = dir.resolve("gen");
        assertEquals(0, Decompile.decompileTree(src, out));
        String cSrc = Files.readString(out.resolve("C.kf"));
        assertTrue(cSrc.contains("viaInterface() = 1"),
                "o default deve ser INLINADO (valor do corpo de A.m()):\n" + cSrc);
        assertTrue(cSrc.contains("viaArgs(Int arg0) = (arg0 * 2)"),
                "o default COM argumento deve ser INLINADO ligando o arg:\n" + cSrc);
        assertFalse(cSrc.contains("body not recovered"),
                "nenhum stub no arquivo:\n" + cSrc);
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                out.resolve("A.kf"), out.resolve("C.kf")), dir.resolve("o"), Target.JVM, out);
        assertTrue(r.success(), "arvore com o default inlinado deve compilar (zero drift):\n" + cSrc
                + "\n" + r.diagnostics().getDiagnostics());
    }

    @Test
    void callConditionsRecoverNotStubbed(@TempDir Path dir) throws Exception {
        // Fase C (13/10): condições com CHAMADA (`s.isEmpty()`) e com
        // aritmética (`a + 1 > b`) recuperam; o `isEmpty` saiu das
        // propriedades da stdlib (medido: `.isEmpty` compilava e quebrava em
        // runtime com NoSuchField — a chamada é fiel e `Bool != 0` funciona).
        Path javaFile = dir.resolve("Cd.java");
        Files.writeString(javaFile, """
                public class Cd {
                    int check(String s) {
                        if (s.isEmpty()) { return 1; }
                        return 0;
                    }
                    int gt(int a, int b) {
                        if (a + 1 > b) { return 2; }
                        return 3;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Cd.class"));
        assertFalse(kof.contains("body not recovered"),
                "condicoes com chamada/aritmetica recuperam:\n" + kof);
        assertTrue(kof.contains("arg0.isEmpty() != 0"), kof);
        assertTrue(kof.contains("(arg0 + 1) > arg1"), kof);
        // zero-drift executado
        String kofRun = kof + "\nmain() {\n    var cd = Cd()\n    println(cd.check(\"\"))\n    println(cd.check(\"x\"))\n    println(cd.gt(2, 1))\n    println(cd.gt(1, 2))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("1\n0\n2\n3", output, "zero-drift executado");
    }

    @Test
    void staticFieldsAndClinitFoldNotSilentlyLost(@TempDir Path dir) throws Exception {
        // Fase E (fatia static-init, mantenedora 14/10): o clinit em forma
        // literal-putstatic dobra nos inicializadores — antes os campos
        // estáticos NEM eram emitidos (o main referenciava `St.V` que não
        // existia = Kof sem compilar, falso OK medido).
        Path src = dir.resolve("classes");
        Path s = dir.resolve("St.java");
        Files.writeString(s, """
                public class St {
                    static int V;
                    static String S;
                    static { V = 42; S = "hi"; }
                    public static void main(String[] args) {
                        System.out.println(V);
                        System.out.println(S);
                    }
                }
                """);
        runJavac(java.util.List.of(s), src);
        Path out = dir.resolve("gen");
        assertEquals(0, Decompile.decompileTree(src, out));
        String stSrc = Files.readString(out.resolve("St.kf"));
        assertTrue(stSrc.contains("static Int V = 42"), stSrc);
        assertTrue(stSrc.contains("static String S = \"hi\""), stSrc);
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                out.resolve("St.kf")), dir.resolve("o"), Target.JVM, out);
        assertTrue(r.success(), "a arvore com os campos dobrados deve compilar:\n" + stSrc
                + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "St");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("42\nhi", output, "os valores dobrados devem RODAR");
    }


    @Test
    void decompileStaticFieldEmittedWithFoldedInit(@TempDir Path dir) throws Exception {
        // Fase E (fatia static-init, mantenedora 14/10): o campo estático era
        // PULADO (perda silenciosa — o main referenciava `Const.MAX` que não
        // existia = falso OK); agora é EMITIDO e o clinit literal dobra.
        Path javaFile = dir.resolve("Const.java");
        Files.writeString(javaFile, """
                public class Const {
                    public static final int MAX = 100;
                    public String label = "x";
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Const.class"));
        assertTrue(kof.contains("static Int MAX = 100"),
                "static field emitido com o clinit dobrado:\n" + kof);
        assertTrue(kof.contains("String label"), "instance field should be kept:\n" + kof);
    }


    @Test
    void primitiveArrayBuildRecoveredNotStubbed(@TempDir Path dir) throws Exception {
        // Fase E: `newarray` (0xbc) faltava — arrays primitivos (`int[]`)
        // stubavam o metodo inteiro. Agora `new int[n]` + xastore recuperam.
        Path javaFile = dir.resolve("Arr.java");
        Files.writeString(javaFile, """
                public class Arr {
                    int[] build(int n) {
                        int[] a = new int[n];
                        for (int i = 0; i < n; i++) { a[i] = i; }
                        return a;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Arr.class"));
        assertFalse(kof.contains("body not recovered"),
                "arrays primitivos devem recuperar (nao stub):\n" + kof);
        assertTrue(kof.contains("new Int[arg0]"), "newarray → new Int[n]:\n" + kof);
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kof)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "o Kof dos arrays deve compilar (zero drift):\n" + kof
                + "\n" + r.diagnostics().getDiagnostics());
    }

    @Test
    void castConditionRecoversNotStubbed(@TempDir Path dir) throws Exception {
        // Fase C: checkcast no testExpr — `if ((Integer) o > 3)` recupera
        // como `(arg0 as Int) > 3` (o intValue e UNBOX → identidade).
        Path javaFile = dir.resolve("Cn.java");
        Files.writeString(javaFile, """
                public class Cn {
                    int v;
                    int viaCast(Object o) {
                        if ((Integer) o > 3) { return 1; }
                        return 0;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Cn.class"));
        assertFalse(kof.contains("body not recovered"),
                "cast na condicao recupera:\n" + kof);
        assertTrue(kof.contains("(arg0 as Int) > 3"), kof);
        // zero-drift executado: build(5) -> 1, cast 2 -> 0
        String kofRun = kof + "\nmain() {\n    var cn = Cn()\n    var o1: Object = 5\n    var o2: Object = 2\n    println(cn.viaCast(o1))\n    println(cn.viaCast(o2))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("1\n0", output, "zero-drift executado");
    }

    @Test
    void throwExceptionConstructionRecoversNotStubbed(@TempDir Path dir) throws Exception {
        // Fase E (o idioma errors.md no caminho de statements): a construção
        // de exceção É a String do message — `throw new RuntimeException(msg)`
        // recupera como `throw msg` e o zero-drift é executado (o catch pega
        // a mensagem igual ao Java).
        Path javaFile = dir.resolve("Th.java");
        Files.writeString(javaFile, """
                public class Th {
                    void kaboom(String msg) { throw new RuntimeException(msg); }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Th.class"));
        assertFalse(kof.contains("body not recovered"),
                "o throw com construção recupera:\n" + kof);
        assertTrue(kof.contains("throw arg0"), kof);
        // zero-drift executado: o catch pega a mensagem como no Java
        String kofRun = kof + "\nmain() {\n    var th = Th()\n    try {\n        th.kaboom(\"boom-42\")\n        println(\"no-throw\")\n    } catch (String e) {\n        println(\"caught: \" + e)\n    }\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("caught: boom-42", output, "zero-drift: a mensagem lançada e pega");
    }

    @Test
    void andCombinationNoCrashOnSucc1Then(@TempDir Path dir) throws Exception {
        // Fase C (regressão 13/10): o loop de combinação && chamava
        // succ.get(1) sem exigir succ.size() == 2 — um then-de-teste com
        // succ ÚNICO (braços que caem no mesmo join) crashava IOOBE
        // (medido no TranslateStatements REAL). Sem crash + rc=0.
        Path real = Path.of("target/classes/dev/kof/cli/TranslateStatements.class");
        if (!Files.exists(real)) {
            real = Path.of("kof-cli/target/classes/dev/kof/cli/TranslateStatements.class");
        }
        final Path realF = real;
        assertDoesNotThrow(() -> Decompile.decompile(realF),
                "o decompile não pode CRASHAR (degrada para stub, nunca lança)");
    }

    @Test
    void abstractMethodEmitsSignatureNotStub(@TempDir Path dir) throws Exception {
        // Fase E: método ABSTRATO não tem corpo NO BYTECODE — o throw-stub
        // era mentira (não há informação perdida); emite a assinatura nua.
        Path javaFile = dir.resolve("Sh.java");
        Files.writeString(javaFile, """
                public interface Sh {
                    String shape(int kind);
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Sh.class"));
        // O Kof NÃO tem métodos abstratos (a interface decompilada vira
        // class): o corpo do abstrato é o throw-stub INÓCUO (nunca
        // alcançável numa interface) — catalogado, nunca código real
        // inventado; a assinatura fica EXACT.
        assertTrue(kof.contains("shape"), kof);
        assertTrue(kof.contains("body not recovered"),
                "o abstrato degrada para o stub inócuo catalogado:\n" + kof);
    }

    @Test
    void valuePropagatingIfElseRecovers(@TempDir Path dir) throws Exception {
        // Fase C (13/10): os braços do if/else deixando 1 valor na pilha e o
        // join começando com a store → if-expression propagada
        // (`var v2 = if (cond) thenVal else eVal`; o astore do join é
        // consumido pela atribuição — o skip-first).
        Path javaFile = dir.resolve("Vp.java");
        Files.writeString(javaFile, """
                public class Vp {
                    String pick(boolean big) {
                        String r;
                        if (big) { r = "B"; } else { r = "S"; }
                        return r;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Vp.class"));
        assertFalse(kof.contains("body not recovered"),
                "o if/else com valor propagado recupera:\n" + kof);
        assertTrue(kof.contains("if ("), kof);
        String kofRun = kof + "\nmain() {\n    var vp = Vp()\n    println(vp.pick(true))\n    println(vp.pick(false))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("B\nS", output, "zero-drift executado");
    }

    @Test
    void jdkCollectionConstructionMapsToStdlib(@TempDir Path dir) throws Exception {
        // Fase E (13/10): mapeamento JDK→stdlib de COLEÇÕES (o mesmo do
        // tradutor): `new ArrayList()` → `listOf()` etc. Sem o mapa, o
        // `new ArrayList()` no corpo recusava (stub) — destravou o
        // parseRecord e muitos métodos.
        Path javaFile = dir.resolve("Col.java");
        Files.writeString(javaFile, """
                import java.util.ArrayList;
                public class Col {
                    static ArrayList<String> mk() {
                        ArrayList<String> xs = new ArrayList<String>();
                        xs.add("a");
                        xs.add("b");
                        return xs;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Col.class"));
        assertFalse(kof.contains("body not recovered"),
                "new ArrayList() mapeia para listOf() (n\u00e3o stub):\n" + kof);
        assertTrue(kof.contains("listOf()"), kof);
        // zero-drift executado
        String kofRun = kof + "\nmain() {\n    var xs = Col.mk()\n    println(xs.size)\n    println(xs.get(0))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("2\na", output, "zero-drift executado");
    }

    @Test
    void breakInLoopRecoversNotStubbed(@TempDir Path dir) throws Exception {
        // Fase C (13/10): `break` é um goto para a SAÍDA do loop aberto —
        // um bloco de goto único para ela emite `break` (o corpo antes já
        // emitido). Antes: stub do método inteiro.
        Path javaFile = dir.resolve("Bk.java");
        Files.writeString(javaFile, """
                public class Bk {
                    int brk(int n) {
                        int s = 0;
                        for (int i = 0; i < n; i++) { if (i == 3) break; s += i; }
                        return s;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Bk.class"));
        assertFalse(kof.contains("body not recovered"),
                "o break recupera:\n" + kof);
        assertTrue(kof.contains("break"), kof);
        // zero-drift executado
        String kofRun = kof + "\nmain() {\n    var bk = Bk()\n    println(bk.brk(0))\n    println(bk.brk(3))\n    println(bk.brk(6))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("0\n3\n3", output, "zero-drift executado");
    }

    @Test
    void orCombinationAndBoolCoercionRecover(@TempDir Path dir) throws Exception {
        // Fase C (13/10): o `||` (orientação ESPELHADA do &&) recupera +
        // coerção Bool (braços 1/0 num método `Z` → true/false; antes o
        // `&&` emitia `return if (..) 1 else 0` num Bool = SEM010).
        Path javaFile = dir.resolve("Or.java");
        Files.writeString(javaFile, """
                public class Or {
                    boolean or2(int a, int b) { return a > 0 || b > 0; }
                    boolean and2(int a, int b) { return a > 0 && b > 0; }
                    int orInIf(int a, int b) { if (a > 0 || b > 0) { return 1; } return 0; }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Or.class"));
        assertFalse(kof.contains("body not recovered"),
                "|| e && recuperam:\n" + kof);
        assertTrue(kof.contains("true") || kof.contains("false"),
                "braços Bool viram true/false:\n" + kof);
        // zero-drift executado
        String kofRun = kof + "\nmain() {\n    var o = Or()\n    println(o.or2(1, 0))\n    println(o.or2(0, 0))\n    println(o.and2(1, 1))\n    println(o.and2(1, 0))\n    println(o.orInIf(0, 5))\n    println(o.orInIf(0, 0))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("true\nfalse\ntrue\nfalse\n1\n0", output, "zero-drift executado");
    }

    @Test
    void shiftOperatorsRecoverNotStubbed(@TempDir Path dir) throws Exception {
        // Fase E (13/10): os opcodes de SHIFT (0x78-0x7d) faltavam no emitLinear
        // (e no avaliador) — qualquer corpo com `<<`/`>>`/`>>>` stubava. A
        // semântica por-target é do COMPILADOR (corrigida no §698).
        Path javaFile = dir.resolve("Sh.java");
        Files.writeString(javaFile, """
                public class Sh {
                    static int shl(int a, int b) { return a << b; }
                    static int shr(int a, int b) { return a >> b; }
                    static int ushr(int a, int b) { return a >>> b; }
                    static long lshl(long a, int b) { return a << b; }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Sh.class"));
        assertFalse(kof.contains("body not recovered"),
                "os shifts recuperam:\n" + kof);
        assertTrue(kof.contains("<<") && kof.contains(">>") && kof.contains(">>>"), kof);
        // zero-drift executado (contagens VARIÁVEIS — literais são const-folded)
        String kofRun = kof + "\nmain() {\n    var sh = Sh()\n    println(Sh.shl(1, 4))\n    println(Sh.shr(-16, 2))\n    println(Sh.ushr(-5, 1))\n    println(Sh.lshl(1L, 40))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: 1<<4=16, -16>>2=-4, -5>>>1=2147483645, 1L<<40=1099511627776
        assertEquals("16\n-4\n2147483645\n1099511627776", output, "zero-drift dos shifts");
    }

    @Test
    void orChain3TermsRecoversNotStubbed(@TempDir Path dir) throws Exception {
        // Fase C (13/10): a cadeia `||` de 3+ termos (orChain) — os testes
        // NAO-finais saltam para o VERDADEIRO (takenCond) e o ULTIMO cai no
        // verdadeiro (invCond), diferente do `||` de 2 termos (assimetria
        // medida). Antes: stub do método inteiro.
        Path javaFile = dir.resolve("Oc.java");
        Files.writeString(javaFile, """
                public class Oc {
                    boolean or3(int a, int b, int c) { return a > 0 || b > 0 || c > 0; }
                    boolean or4(int a, int b, int c, int d) { return a > 0 || b > 0 || c > 0 || d > 0; }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Oc.class"));
        assertFalse(kof.contains("body not recovered"),
                "a cadeia || de 3/4 termos recupera:\n" + kof);
        assertTrue(kof.contains("arg0 > 0 || arg1 > 0 || arg2 > 0"), kof);
        String kofRun = kof + "\nmain() {\n    var oc = Oc()\n    println(oc.or3(0, 0, 1))\n    println(oc.or3(0, 0, 0))\n    println(oc.or4(0, 0, 0, 5))\n    println(oc.or4(0, 0, 0, 0))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("true\nfalse\ntrue\nfalse", output, "zero-drift executado");
    }

    @Test
    void stringValueOfMapsToConcat(@TempDir Path dir) throws Exception {
        // Fase E (13/10): `String.valueOf(x)` é MUITO comum — o decompilador
        // mapeia para a concat de string vazia (o idioma Kof; medido: "" + x
        // dá o mesmo p/ Int/Long/Double/Bool/Char). Antes: stub.
        Path javaFile = dir.resolve("Sv.java");
        Files.writeString(javaFile, """
                public class Sv {
                    static String ofInt(int i) { return String.valueOf(i); }
                    static String ofLong(long l) { return String.valueOf(l); }
                    static String ofBool(boolean b) { return String.valueOf(b); }
                    static String embed(int i) { return "n=" + String.valueOf(i) + "!"; }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Sv.class"));
        assertFalse(kof.contains("body not recovered"),
                "String.valueOf recupera:\n" + kof);
        assertTrue(kof.contains("\"\" + arg0"), kof);
        String kofRun = kof + "\nmain() {\n    println(Sv.ofInt(42))\n    println(Sv.ofLong(7L))\n    println(Sv.ofBool(true))\n    println(Sv.embed(5))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("42\n7\ntrue\nn=5!", output, "zero-drift: String.valueOf idêntico ao Java");
    }

    @Test
    void primitiveConversionsRecoverNotStubbed(@TempDir Path dir) throws Exception {
        // Fase E (13/10): as CONVERSÕES primitivas (0x85-0x90) faltavam no
        // emitLinear/avaliador — qualquer corpo com `(long) x`/`(double) x`/
        // `(int) l` stubava. Mapeadas p/ .toLong()/.toDouble()/.toInt()/
        // .toFloat() (os únicos conversores de primitivo do Kof, medido).
        // 0x91-0x93 (i2b/i2c/i2s) NÃO têm equivalente — seguem recusados.
        Path javaFile = dir.resolve("Cv.java");
        Files.writeString(javaFile, """
                public class Cv {
                    static long widen(int i) { return (long) i; }
                    static double iToD(int i) { return (double) i; }
                    static int lToI(long l) { return (int) l; }
                    static double lToD(long l) { return (double) l; }
                    static int dToI(double d) { return (int) d; }
                    static long dToL(double d) { return (long) d; }
                    static double mix(int a, long b) {
                        long x = (long) a;
                        double y = (double) b;
                        return x + y;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Cv.class"));
        assertFalse(kof.contains("body not recovered"),
                "as conversoes recuperam:\n" + kof);
        // o caminho de EXPRESS\u00c3O emite `as` (correto, medido); o de
        // STATEMENTS emite `.toX()` — aceita qualquer um
        assertTrue(kof.contains("as Long") || kof.contains(".toLong()"), kof);
        assertTrue(kof.contains("as Double") || kof.contains(".toDouble()"), kof);
        String kofRun = kof + "\nmain() {\n    println(Cv.widen(42))\n    println(Cv.iToD(7))\n    println(Cv.lToI(9L))\n    println(Cv.lToD(3L))\n    println(Cv.dToI(2.9))\n    println(Cv.dToL(5.7))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("42\n7.0\n9\n3.0\n2\n5", output, "zero-drift das conversoes");
    }

    @Test
    void multiAssignRecoversNotStubbed(@TempDir Path dir) throws Exception {
        // Fase E (13/10): o `x = y = 5` compila p/ iconst_5, dup, istore,
        // istore — o dup de um valor PURO (sem chamada) é seguro (o texto
        // aparece duas vezes mas avalia igual). Antes: stub do método.
        Path javaFile = dir.resolve("Ma.java");
        Files.writeString(javaFile, """
                public class Ma {
                    static int multiAssign() { int x, y; x = y = 5; return x + y; }
                    static int multiAssign2(int a) { int x, y; x = y = a * 2; return x - y; }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Ma.class"));
        assertFalse(kof.contains("body not recovered"),
                "o multi-assign recupera:\n" + kof);
        String kofRun = kof + "\nmain() {\n    println(Ma.multiAssign())\n    println(Ma.multiAssign2(6))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("10\n0", output, "zero-drift executado");
    }

    @Test
    void nestedTernaryRecoversAndNoWrongCode(@TempDir Path dir) throws Exception {
        // Fase E (13/10) BUG CRÍTICO CORRIGIDO: `a < b ? -1 : (a > b ? 1 : 0)`
        // era decompilado como `if (a > b) 1 else 0` — DESCARTAVA o braço -1
        // = CÓDIGO ERRADO compilável (anti-R6). Causa: comparisonReturn lia
        // os 2 primeiros loads do MÉTODO (não os do cmp) e ignorava branch
        // anterior, aceitando o TAIL de um ternário aninhado como flat. Fix:
        // o cmp tem que ser o INÍCIO do fluxo + armExpr RECURSIVO no
        // ifElseReturn (braço que é ele mesmo um teste). Zero-drift executado.
        Path javaFile = dir.resolve("Nt.java");
        Files.writeString(javaFile, """
                public class Nt {
                    static int cmpChain(int a, int b) { return a < b ? -1 : (a > b ? 1 : 0); }
                    static int nest(int a) { return a > 0 ? (a > 10 ? 2 : 1) : 0; }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Nt.class"));
        assertFalse(kof.contains("body not recovered"),
                "o tern\u00e1rio aninhado recupera:\n" + kof);
        assertTrue(kof.contains("arg0 < arg1"), "o bra\u00e7o -1 n\u00e3o pode ser descartado:\n" + kof);
        assertTrue(kof.contains("-1"), kof);
        String kofRun = kof + "\nmain() {\n    println(Nt.cmpChain(3, 5))\n    println(Nt.cmpChain(5, 3))\n    println(Nt.cmpChain(4, 4))\n    println(Nt.nest(5))\n    println(Nt.nest(15))\n    println(Nt.nest(-3))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: cmpChain -1/1/0; nest(5)=1 nest(15)=2 nest(-3)=0
        assertEquals("-1\n1\n0\n1\n2\n0", output, "zero-drift: o bra\u00e7o -1 preservado");
    }

    @Test
    void mixedAndOrPrecedenceRecovers(@TempDir Path dir) throws Exception {
        // Fase E (13/10): a PRECEDENCIA MISTA `a > 0 && b > 0 || c > 0` — o
        // grupo && tem o falso convergindo no RHS do || (que E um teste). O
        // ifElseReturn agora trata a SEGUNDA orientacao do teste interno do
        // && (fall-through == o falso externo; takenCond) e devolve o shape
        // `||` puro ao struct walker (que o emite idiomatico). Antes: stub.
        Path javaFile = dir.resolve("Mx.java");
        Files.writeString(javaFile, """
                public class Mx {
                    boolean mixed(int a, int b, int c) { return a > 0 && b > 0 || c > 0; }
                    boolean mixed2(int a, int b, int c) { return a > 10 && b > 10 || c == 7; }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Mx.class"));
        assertFalse(kof.contains("body not recovered"),
                "a precedencia mista recupera:\n" + kof);
        assertTrue(kof.contains("arg0 > 0 && arg1 > 0"), kof);
        String kofRun = kof + "\nmain() {\n    var m = Mx()\n    println(m.mixed(1, 1, 0))\n    println(m.mixed(1, 0, 1))\n    println(m.mixed(0, 1, 0))\n    println(m.mixed(0, 0, 1))\n    println(m.mixed(0, 0, 0))\n    println(m.mixed2(11, 11, 0))\n    println(m.mixed2(11, 0, 7))\n    println(m.mixed2(0, 0, 0))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: 5 casos do mixed + 3 do mixed2
        assertEquals("true\ntrue\nfalse\ntrue\nfalse\ntrue\ntrue\nfalse", output,
                "zero-drift: precedencia mista identica ao Java");
    }

    @Test
    void collectionFactoriesRecoverZeroDrift(@TempDir Path dir) throws Exception {
        // Fase E (14/10): as FABRICAS de colecao imutavel (List.of/Map.of/
        // Set.of — os idiomas modernos) mapeiam p/ listOf/mapOf/setOf com os
        // args (o javac usa os OVERLOADS de aridade fixa p/ ate 10 args —
        // sem array no bytecode). E os WRAPPERS nas assinaturas genericas
        // mapeiam p/ o primitivo Kof (Integer→Int) — antes o decompilado
        // nem COMPILAVA (Integer indefinido). Zero-drift executado.
        Path javaFile = dir.resolve("Cf.java");
        Files.writeString(javaFile, """
                import java.util.List;
                import java.util.Map;
                import java.util.Set;
                public class Cf {
                    static List<String> two() { return List.of("a", "b"); }
                    static List<Integer> nums() { return List.of(1, 2, 3); }
                    static Map<String, Integer> pairs() { return Map.of("x", 1, "y", 2); }
                    static Set<String> dup() { return Set.of("p", "q"); }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Cf.class"));
        assertFalse(kof.contains("body not recovered"),
                "as fabricas recuperam:\n" + kof);
        assertTrue(kof.contains("listOf(\"a\", \"b\")"), kof);
        assertTrue(kof.contains("mapOf(\"x\", "), kof);
        assertFalse(kof.contains("Integer"), "wrapper indefinido nao sai:\n" + kof);
        String kofRun = kof + "\nmain() {\n    println(Cf.two())\n    println(Cf.nums())\n    println(Cf.pairs())\n    println(Cf.dup())\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: [a, b] [1, 2, 3] {x=1, y=2} [p, q]
        assertEquals("[a, b]\n[1, 2, 3]\n{x=1, y=2}\n[p, q]", output,
                "zero-drift: fabricas identicas ao Java");
    }

    @Test
    void trueVarargsJoinAndAsListRecover(@TempDir Path dir) throws Exception {
        // Fase E (14/10): os VARARGS VERDADEIROS — `String.join(sep, a, b)` /
        // `Arrays.asList(a, b)` compilam para iconst_N, anewarray, (dup,
        // iconst_i, push, aastore)*N, invokestatic. O varargsCall reconstr\u00f3i
        // os valores (join → concat com o sep ENTRE; asList → listOf) e
        // recusa qualquer desvio (R6). Antes: stub (emitLinear-null:0xbd).
        Path javaFile = dir.resolve("Tv.java");
        Files.writeString(javaFile, """
                import java.util.List;
                import java.util.Arrays;
                public class Tv {
                    static String j(String sep) { return String.join(sep, "a", "b"); }
                    static String j3(String sep, String x) { return String.join(sep, x, "m", "n"); }
                    static List<String> al() { return Arrays.asList("p", "q"); }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Tv.class"));
        assertFalse(kof.contains("body not recovered"),
                "os varargs verdadeiros recuperam:\n" + kof);
        assertTrue(kof.contains("\"a\" + arg0 + \"b\""), kof);
        assertTrue(kof.contains("listOf(\"p\", \"q\")"), kof);
        String kofRun = kof + "\nmain() {\n    println(Tv.j(\"-\"))\n    println(Tv.j3(\",\", \"z\"))\n    println(Tv.al())\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: a-b | z,m,n | [p, q]
        assertEquals("a-b\nz,m,n\n[p, q]", output, "zero-drift: varargs id\u00eanticos ao Java");
    }

    @Test
    void bitwiseOperatorsRecoverZeroDrift(@TempDir Path dir) throws Exception {
        // Fase E (14/10): os operadores BITWISE (& | ^) faltavam no emitLinear
        // E no avaliador (movido p/ BytecodeEval pelo refactor) — qualquer
        // corpo com bits stubava. O Kof tem os tres (medido: 6&3=2, 6|3=7,
        // 6^3=5). Zero-drift executado contra o oráculo Java REAL.
        Path javaFile = dir.resolve("Bw.java");
        Files.writeString(javaFile, """
                public class Bw {
                    static int bits(int a, int b) { return (a & b) | (a ^ b); }
                    static int xor(int a, int b) { return a ^ b; }
                    static boolean even(int a) { return (a & 1) == 0; }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Bw.class"));
        assertFalse(kof.contains("body not recovered"),
                "os bitwise recuperam:\n" + kof);
        assertTrue(kof.contains("&") && kof.contains("|") && kof.contains("^"), kof);
        String kofRun = kof + "\nmain() {\n    println(Bw.bits(6, 3))\n    println(Bw.bits(12, 10))\n    println(Bw.xor(170, 85))\n    println(Bw.even(4))\n    println(Bw.even(5))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java REAL: (6&3)|(6^3)=7; (12&10)|(12^10)=14; 170^85=255; even
        assertEquals("7\n14\n255\ntrue\nfalse", output, "zero-drift: bitwise id\u00eanticos ao Java");
    }

    @Test
    void loopWithCompoundConditionRecovers(@TempDir Path dir) throws Exception {
        // Fase E (14/10): o `while (i < n && s < 100)` — o CFG divide os DOIS
        // testes em blocos separados (o fall-through de um cond é líder) e o
        // teste interno salta para a MESMA saída. A combinação `&&` já cobria
        // o shape, mas o guard `!loop0` pulava-a para headers de loop — o
        // método inteiro stubava. A combinação não altera then/exit (o corpo
        // já é o fall-through do teste interno). Zero-drift executado.
        Path javaFile = dir.resolve("Wc.java");
        Files.writeString(javaFile, """
                public class Wc {
                    static int run(int n) {
                        int s = 0; int i = 1;
                        while (i < n && s < 100) { s += i; i *= 2; }
                        return s;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Wc.class"));
        assertFalse(kof.contains("body not recovered"),
                "o loop com condi\u00e7\u00e3o composta recupera:\n" + kof);
        assertTrue(kof.contains("while ("), kof);
        assertTrue(kof.contains("&&"), kof);
        String kofRun = kof + "\nmain() {\n    println(Wc.run(5))\n    println(Wc.run(1000))\n    println(Wc.run(0))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java REAL: 7 127 0
        assertEquals("7\n127\n0", output, "zero-drift: loop composto id\u00eantico ao Java");
    }

    @Test
    void switchStatementWithPrologueRecovers(@TempDir Path dir) throws Exception {
        // Fase E (14/10): o `int r = 0; switch (x) { case 1: r = 10; ... }` —
        // o PRÓLOGO com store fazia o linearExpr (avaliador de EXPRESSÃO,
        // sem store) recusar e o switch inteiro stubar. Agora o prólogo vira
        // statements (`var r = 0`) ANTES do switch, o hoist pula o slot já
        // declarado (o `var r` sem init fazia o compilador Kof boxar as
        // atribuições → VerifyError no ireturn; medido). Zero-drift.
        Path javaFile = dir.resolve("Sw.java");
        Files.writeString(javaFile, """
                public class Sw {
                    int fall(int x) { int r = 0; switch (x) { case 1: r = 10; break; case 2: r = 20; break; default: r = -1; } return r; }
                    static int noPrologue(int x) { switch (x) { case 1: return 10; case 2: return 20; default: return -1; } }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Sw.class"));
        assertFalse(kof.contains("body not recovered"),
                "o switch recupera (com e sem pr\u00f3logo):\n" + kof);
        assertTrue(kof.contains("switch (arg0)"), kof);
        String kofRun = kof + "\nmain() {\n    var sw = Sw()\n    println(sw.fall(1))\n    println(sw.fall(2))\n    println(sw.fall(9))\n    println(Sw.noPrologue(1))\n    println(Sw.noPrologue(9))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: 10 20 -1 10 -1
        assertEquals("10\n20\n-1\n10\n-1", output, "zero-drift: switch id\u00eantico ao Java");
    }

    @Test
    void switchExpressionRecovers(@TempDir Path dir) throws Exception {
        // Fase E (14/10): o SWITCH-EXPRESSION (`return switch (x) { case 1 ->
        // 10; ... }`) — os bra\u00e7os deixam EXATAMENTE 1 valor na pilha e o fim
        // comum \u00e9 um \u00fanico return → a forma seta do Kof. Qualquer bra\u00e7o com
        // statement → cai na forma statement (sem perda). Zero-drift executado.
        Path javaFile = dir.resolve("Se.java");
        Files.writeString(javaFile, """
                public class Se {
                    static int num(int x) { return switch (x) { case 1 -> 10; case 2 -> 20; default -> -1; }; }
                    static String label(int x) { return switch (x) { case 1 -> "A"; case 2 -> "B"; default -> "?"; }; }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Se.class"));
        assertFalse(kof.contains("body not recovered"),
                "o switch-expression recupera:\n" + kof);
        assertTrue(kof.contains("switch (arg0)"), kof);
        String kofRun = kof + "\nmain() {\n    println(Se.num(1))\n    println(Se.num(2))\n    println(Se.num(9))\n    println(Se.label(1))\n    println(Se.label(9))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: 10 20 -1 A ?
        assertEquals("10\n20\n-1\nA\n?", output, "zero-drift: switch-expression id\u00eantico ao Java");
    }

    @Test
    void stringSwitchRecovers(@TempDir Path dir) throws Exception {
        // Fase E (14/10): o switch de STRING — o javac desdobra em hashCode
        // → lookupswitch + cadeias equals atribuindo um índice + um SEGUNDO
        // switch sobre o índice. O stringSwitchExpr reconstr\u00f3i os casos
        // literais e emite a forma seta Kof (que aceita String — medido).
        // Três armadilhas medidas: o join \u00e9 [iload IDX, switch] (o switch
        // vem DEPOIS do load), o fim comum \u00e9 o RETURN (o dflt \u00e9 o INÍCIO
        // do bra\u00e7o default) e o \u00daLTIMO caso de hash cai no join sem goto.
        Path javaFile = dir.resolve("Ss.java");
        Files.writeString(javaFile, """
                public class Ss {
                    static String pick(String s) { return switch (s) { case "a" -> "A"; case "b" -> "B"; default -> "?"; }; }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Ss.class"));
        assertFalse(kof.contains("body not recovered"),
                "o switch de String recupera:\n" + kof);
        assertTrue(kof.contains("case \"a\""), kof);
        String kofRun = kof + "\nmain() {\n    println(Ss.pick(\"a\"))\n    println(Ss.pick(\"b\"))\n    println(Ss.pick(\"z\"))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: A B ?
        assertEquals("A\nB\n?", output, "zero-drift: switch de String id\u00eantico ao Java");
    }

    @Test
    void bindingInstanceofTernaryRecovers(@TempDir Path dir) throws Exception {
        // Fase E (14/10): o BINDING instanceof (`o instanceof String s ? ...`)
        // compila p/ instanceof+ifeq no cond e checkcast+astore+aload no
        // braço. O avaliador ganhou instanceof (0xc1) + memória de locais da
        // fatia (o binding é self-contained); os extratores de CONDIÇÃO
        // recusam stores (o fusion-path as emite — sem isso o prólogo
        // sumia e o corpo referenciava v1 antes da declaração, SEM011).
        // Zero-drift executado. (O `==` de REFERÊNCIA Java segue stub
        // honesto: o == do Kof é CONTEÚDO — semântica congelada.)
        Path javaFile = dir.resolve("Bi.java");
        Files.writeString(javaFile, """
                public class Bi {
                    static String describe(Object o) { return o instanceof String s ? "str:" + s : "other"; }
                    static boolean isStr(Object o) { return o instanceof String; }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Bi.class"));
        assertFalse(kof.contains("body not recovered"),
                "o binding instanceof recupera:\n" + kof);
        assertTrue(kof.contains("arg0 instanceof String"), kof);
        assertFalse(kof.contains("!= 0) \""), "o instanceof n\u00e3o carrega ru\u00eddo:\n" + kof);
        String kofRun = kof + "\nmain() {\n    println(Bi.describe(\"x\"))\n    println(Bi.describe(42))\n    println(Bi.isStr(\"s\"))\n    println(Bi.isStr(7))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: str:x | other | true | false
        assertEquals("str:x\nother\ntrue\nfalse", output, "zero-drift: binding instanceof id\u00eantico ao Java");
    }

    @Test
    void longLoopAndGcdRecovers(@TempDir Path dir) throws Exception {
        // Fase E (14/10): dois achados. (1) A comparação LONG ([x, y, lcmp,
        // ifXX]) — a condição real tem DOIS operandos e o inv da queda é o
        // espelho do ifXX. (2) CRÍTICO: o `assign` REDECLARAVA PARÂMETROS
        // (`var arg1 = ...` criava local de BLOCO; o parâmetro nunca
        // atualizava — o gcd decompilado rodava PARA SEMPRE, R6). Slot
        // nomeado nunca redeclara. Zero-drift executado.
        Path javaFile = dir.resolve("Lg.java");
        Files.writeString(javaFile, """
                public class Lg {
                    static long factorial(long n) { long r = 1; for (long i = 2; i <= n; i++) { r *= i; } return r; }
                    static int gcd(int a, int b) { while (b != 0) { int t = b; b = a % b; a = t; } return a; }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Lg.class"));
        assertFalse(kof.contains("body not recovered"),
                "os dois recuperam:\n" + kof);
        assertFalse(kof.contains("var arg0") || kof.contains("var arg1"),
                "par\u00e2metro nunca redeclara:\n" + kof);
        String kofRun = kof + "\nmain() {\n    println(Lg.factorial(5L))\n    println(Lg.factorial(10L))\n    println(Lg.gcd(48, 18))\n    println(Lg.gcd(7, 13))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: 120 | 3628800 | 6 | 1
        assertEquals("120\n3628800\n6\n1", output, "zero-drift: long loop + gcd id\u00eanticos ao Java");
    }

    @Test
    void twoDimArrayRecovers(@TempDir Path dir) throws Exception {
        // Fase E (14/10): o MULTIANEWARRAY (0xc5) — `new int[n][2]` — faltava
        // no statementOp E o reader n\u00e3o populava os operands (o \u00edndice CP +
        // o byte de dims). Emite `new Int[d1][2]` (medido: compila e roda com
        // indexa\u00e7\u00e3o dupla). Zero-drift executado.
        Path javaFile = dir.resolve("Td.java");
        Files.writeString(javaFile, """
                public class Td {
                    static int[][] make(int n) { int[][] m = new int[n][2]; for (int i = 0; i < n; i++) { m[i][0] = i; } return m; }
                    static int read(int[][] m) { return m[0][1]; }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Td.class"));
        assertFalse(kof.contains("body not recovered"),
                "o array 2D recupera:\n" + kof);
        assertTrue(kof.contains("new Int[") && kof.contains("][2]"), kof);
        String kofRun = kof + "\nmain() {\n    var m = Td.make(3)\n    println(Td.read(m))\n    println(m[2][0])\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: read2d=0 (m[0][1] zera) | m[2][0]=2
        assertEquals("0\n2", output, "zero-drift: array 2D id\u00eantico ao Java");
    }

    @Test
    void mathDoubleAndHonestGates(@TempDir Path dir) throws Exception {
        // Fase E (14/10): Math.sqrt/pow EXISTEM no math do Kof (medido
        // 3.0/8.0) e agora mapeiam; floor/round/Character.* N\u00c3O existem
        // (SEM025) → tetos honestos. E o verbatim de JDK n\u00e3o-compil\u00e1vel
        // foi trancado: lastIndexOf(int)/indexOf(int) (o do Kof \u00e9
        // String-param, SEM051) e strip (o compilador Kof digere-o como
        // Object → VerifyError) RECUSAM em vez de emitir lixo.
        Path javaFile = dir.resolve("Md.java");
        Files.writeString(javaFile, """
                public class Md {
                    static double root(double a) { return Math.sqrt(a); }
                    static double pow(double a, double b) { return Math.pow(a, b); }
                    static String cut(String s, int i) { return s.substring(i); }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Md.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        assertTrue(kof.contains("math.sqrt(") && kof.contains("math.pow("), kof);
        String kofRun = kof + "\nmain() {\n    println(Md.root(9.0))\n    println(Md.pow(2.0, 10.0))\n    println(Md.cut(\"hello\", 1))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: 3.0 | 1024.0 | ello
        assertEquals("3.0\n1024.0\nello", output, "zero-drift: sqrt/pow id\u00eanticos ao Java");
    }

    @Test
    void arrayLoadInConditionRecovers(@TempDir Path dir) throws Exception {
        // Fase E (14/10): a CARGA DE ARRAY na CONDI\u00c7\u00c3O (`if (xs[i] > m)`
        // dentro de um loop — o maxOf realista) — o xaload (0x2e-0x35) faltava
        // no avaliador de condi\u00e7\u00f5es. A classe Stack inteira (construtor,
        // push/pop/peek com fields) j\u00e1 recuperava. Zero-drift executado.
        // (O countWords — if/else-if com incremento no corpo do loop — segue
        // stub: recovery de join estruturado, trabalho futuro documentado.)
        Path javaFile = dir.resolve("Ao.java");
        Files.writeString(javaFile, """
                public class Ao {
                    static int maxOf(int[] xs) {
                        int m = xs[0];
                        for (int i = 1; i < xs.length; i++) { if (xs[i] > m) { m = xs[i]; } }
                        return m;
                    }
                    static String repeat(String s, int n) {
                        String r = "";
                        for (int i = 0; i < n; i++) { r = r + s; }
                        return r;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Ao.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        assertTrue(kof.contains("while ("), kof);
        String kofRun = kof + "\nmain() {\n    var xs = new Int[4]\n    xs[0] = 3\n    xs[1] = 17\n    xs[2] = 5\n    xs[3] = 9\n    println(Ao.maxOf(xs))\n    println(Ao.repeat(\"ab\", 3))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: 17 | ababab
        assertEquals("17\nababab", output, "zero-drift: maxOf/repeat id\u00eanticos ao Java");
    }

    @Test
    void pureEnumRecoversAsEnum(@TempDir Path dir) throws Exception {
        // Fase E (14/10): o ENUM PURO — `enum Color { RED, GREEN, BLUE }` —
        // decompilava como classe com 3 stubs (os sint\u00e9ticos values/valueOf/
        // $values). O pureEnum (espelho do pureRecord) reconhece o shape
        // estrito (super Enum, as constantes static do pr\u00f3prio tipo na
        // ordem do source, EXATAMENTE os sint\u00e9ticos) e emite a forma enum
        // do Kof (medida: compila e itera na ordem). M\u00e9todo extra → o
        // esqueleto (conservador, zero-drift por constru\u00e7\u00e3o).
        Path javaFile = dir.resolve("Pri.java");
        Files.writeString(javaFile, """
                public enum Pri {
                    RED, GREEN, BLUE
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Pri.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        assertTrue(kof.contains("enum Pri { RED, GREEN, BLUE }"), kof);
        String kofRun = kof + "\nmain() {\n    for (var c in Pri.values()) { println(c) }\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: as constantes na ordem do source
        assertEquals("RED\nGREEN\nBLUE", output, "zero-drift: enum id\u00eantico ao Java");
    }

    @Test
    void elseIfChainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (14/10): a CADEIA else-if — `if (a>0) r=1; else if (b>0)
        // r=2;` e `.. else { r=3; }` — stubava (o join externo tem 3 preds
        // e o pureIfElse exige 2; medido). O braco else E um teste cujo
        // falso == o join externo (else-if) ou cujo verdadeiro E falso
        // convergem num join interno (else-if-else). Zero-drift executado.
        Path javaFile = dir.resolve("Ei.java");
        Files.writeString(javaFile, """
                public class Ei {
                    static int chain(int a, int b) { int r = 0; if (a > 0) { r = 1; } else if (b > 0) { r = 2; } return r; }
                    static int full(int a, int b) { int r = 0; if (a > 0) { r = 1; } else if (b > 0) { r = 2; } else { r = 3; } return r; }
                    static int inLoop(int n) { int s = 0; for (int i = 0; i < n; i++) { if (i % 2 == 0) { s += i; } else if (i % 3 == 0) { s += 1; } } return s; }
                    static int deep(int a, int b, int c) { int r = 0; if (a > 0) { r = 1; } else if (b > 0) { r = 2; } else if (c > 0) { r = 3; } return r; }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Ei.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + "\nmain() {\n    println(Ei.chain(1, 1))\n    println(Ei.chain(0, 1))\n    println(Ei.chain(0, 0))\n    println(Ei.full(1, 1))\n    println(Ei.full(0, 1))\n    println(Ei.full(0, 0))\n    println(Ei.inLoop(10))\n    println(Ei.deep(1, 1, 1))\n    println(Ei.deep(0, 0, 1))\n    println(Ei.deep(0, 0, 0))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: 1 2 0 | 1 2 3 | 22 | deep 1 3 0
        assertEquals("1\n2\n0\n1\n2\n3\n22\n1\n3\n0", output, "zero-drift: cadeia else-if id\u00eantica ao Java");
    }

    @Test
    void forEachAndStringBuilderRecover(@TempDir Path dir) throws Exception {
        // Fase E (14/10): os dois idioms JDK-heavy. (1) O for-each DESAGUADO
        // (`var it = xs.iterator(); while (it.hasNext()) { var x =
        // (it.next() as T); ... }`) → o nativo `for (var x in xs)` (o
        // iterator não existe — SEM025; o binding unboxa via intValue). A
        // fonte com chamadas sem equivalente (entrySet) recusa (R6).
        // (2) O StringBuilder (`new StringBuilder(); sb.append(x);
        // sb.toString()`) → concat (`var sb = ""; sb = sb + x; sb`) —
        // SEM015. Zero-drift executado.
        Path javaFile = dir.resolve("Fe.java");
        Files.writeString(javaFile, """
                public class Fe {
                    static int sumList(java.util.List<Integer> xs) { int s = 0; for (int x : xs) { s += x; } return s; }
                    static boolean anyNeg(java.util.List<Integer> xs) { for (int x : xs) { if (x < 0) { return true; } } return false; }
                    static String repeatChar(char c, int n) { StringBuilder sb = new StringBuilder(); for (int i = 0; i < n; i++) { sb.append(c); } return sb.toString(); }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Fe.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        assertFalse(kof.contains("iterator"), "sem iterator no output:\n" + kof);
        assertFalse(kof.contains("StringBuilder"), "sem StringBuilder no output:\n" + kof);
        assertTrue(kof.contains("for (var"), kof);
        String kofRun = kof + "\nmain() {\n    var xs = listOf(1, -2, 3)\n    println(Fe.sumList(xs))\n    println(Fe.anyNeg(xs))\n    println(Fe.anyNeg(listOf(1, 2)))\n    println(Fe.repeatChar('x', 5))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: 2 | true | false | xxxxx
        assertEquals("2\ntrue\nfalse\nxxxxx", output, "zero-drift: for-each + StringBuilder id\u00eanticos ao Java");
    }

    @Test
    void lambdaRecovers(@TempDir Path dir) throws Exception {
        // Fase E (14/10): o LAMBDA — o javac compila para invokedynamic +
        // Lambdafactory + o sint\u00e9tico `lambda$X$0` (o corpo no classfile).
        // O parser exp\u00f5e `LAMBDA:<impl>` (a ordem importa: o implMethod
        // resolve ANTES da receita String — o MethodType do lambda produzia
        // a receita falsa e escondia o LAMBDA); o pr\u00e9-pass recupera o
        // corpo pelo MESMO pipeline e arma `(p0: T) -> corpo`; os avaliadores
        // empurram no invokedynamic. As interfaces funcionais
        // (Predicate/Function/Supplier) mapeiam p/ tipos fun\u00e7\u00e3o Kof
        // (medido: `(Int) -> Bool` como param compila e roda).
        Path javaFile = dir.resolve("Lm.java");
        Files.writeString(javaFile, """
                import java.util.List;
                import java.util.function.Predicate;
                public class Lm {
                    static boolean anyMatch(List<Integer> xs, Predicate<Integer> p) {
                        for (int x : xs) { if (p.test(x)) { return true; } } return false;
                    }
                    static int useAny(List<Integer> xs) { return anyMatch(xs, x -> x < 0) ? 1 : 0; }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Lm.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        assertTrue(kof.contains("-> if (arg0 < 0) true else false"), kof);
        assertFalse(kof.contains("Predicate<"), "o tipo fun\u00e7\u00e3o Kof, n\u00e3o o JDK:\n" + kof);
        String kofRun = kof + "\nmain() {\n    var xs = listOf(1, -2, 3)\n    println(Lm.useAny(xs))\n    println(Lm.useAny(listOf(2, 3)))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: o lambda pega o -2 (1) e n\u00e3o pega (0)
        assertEquals("1\n0", output, "zero-drift: lambda id\u00eantico ao Java");
    }

    @Test
    void capstoneClassRecoversZeroDrift(@TempDir Path dir) throws Exception {
        // VALIDA\u00c7\u00c3O CAPSTONE (14/10): uma classe realista (fields +
        // ctor + m\u00e9todos de inst\u00e2ncia + est\u00e1ticos + for-each +
        // concat + String.equals) decompila → compila → RODA id\u00eantica ao
        // Java. Tr\u00eas bugs cr\u00edticos pegos no caminho: (1) o ctor sa\u00eda
        // VAZIO (o corpo nunca recuperava — toda classe destruída ficava com
        // fields mortos); (2) o literal `null` (BANIDO pela sem\u00e2ntica
        // congelada, SEM048) emitia c\u00f3digo n\u00e3o-compil\u00e1vel — recusa
        // honesta; (3) o super(args) expl\u00edcito do ctor.
        Path javaFile = dir.resolve("Inv.java");
        Files.writeString(javaFile, """
                import java.util.List;
                import java.util.ArrayList;
                public class Inv {
                    private String name;
                    private int qty;
                    public Inv(String name, int qty) { this.name = name; this.qty = qty; }
                    public boolean take(int n) { if (n > qty) { return false; } qty -= n; return true; }
                    public static Inv find(List<Inv> invs, String name) {
                        for (Inv inv : invs) { if (inv.name.equals(name)) { return inv; } } return null;
                    }
                    public static String label(Inv inv) { return inv == null ? "none" : inv.name; }
                    public static String repeat(String s, int n) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < n; i++) { sb.append(s); } return sb.toString();
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Inv.class"));
        // o `find` (return null) STUBA honestamente: o literal null \u00e9
        // BANIDO pela sem\u00e2ntica congelada (SEM048, mantenedora 09/09 —
        // "total ban, not even to T?"); o label usa `== null` (narrowing, OK)
        assertTrue(kof.contains("this.name = arg0"), "o ctor tem corpo:\n" + kof);
        // o `== null` (narrowing) \u00c9 permitido; a ATRIBUI\u00c7\u00c3O/retorno
        // do literal n\u00e3o (SEM048) — tira os `== null` antes de checar
        assertFalse(kof.replace("== null", "").contains("= null"),
                "o null literal n\u00e3o emite (o == null do narrowing fica):\n" + kof);
        String kofRun = kof + "\nmain() {\n    var invs = listOf(Inv(\"a\", 5), Inv(\"b\", 0))\n    var a = invs.get(0)\n    println(Inv.label(a))\n    a.take(2)\n    println(Inv.label(a))\n    println(Inv.label(invs.get(1)))\n    println(Inv.repeat(\"ab\", 3))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: a | a | b | ababab
        assertEquals("a\na\nb\nababab", output, "zero-drift: capstone id\u00eantico ao Java");
    }

    @Test
    void orRangeCheckWithThrowRecovers(@TempDir Path dir) throws Exception {
        // VALIDA\u00c7\u00c3O (14/10): o range-check `if (p < 1 || p > 65535)
        // throw` — TR\u00caS bugs raiz pegos: (1) o athrow (0xbf) OPACO tinha
        // length=4 no skipper — o bloco do throw esticava e ENGOLIA o
        // `return` seguinte (o CFG ficava com blocos sobrepostos);
        // (2) o `||` com o PR\u00d3LOGO fundido no teste: o 1º termo do walk
        // usa o SUFIXO do split (o prólogo já emitido); (3) o `ldc` grande
        // (65535, fora do bipush) cai no mini-avaliador quando o loadValue
        // falha. Zero-drift executado.
        Path javaFile = dir.resolve("Vk.java");
        Files.writeString(javaFile, """
                public class Vk {
                    static int parsePort(String s) {
                        int p = Integer.parseInt(s);
                        if (p < 1 || p > 65535) { throw new RuntimeException("range:" + p); }
                        return p;
                    }
                    static boolean inRange(int v, int lo, int hi) { return v >= lo && v <= hi; }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Vk.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        assertTrue(kof.contains("||"), kof);
        String kofRun = kof + "\nmain() {\n    println(Vk.parsePort(\"8080\"))\n    println(Vk.inRange(5, 1, 10))\n    println(Vk.inRange(0, 1, 10))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: 8080 | true | false
        assertEquals("8080\ntrue\nfalse", output, "zero-drift: range-check id\u00eantico ao Java");
    }

    @Test
    void mixedAndOrTreeAndParamHoistGuard(@TempDir Path dir) throws Exception {
        // Fase E (14/10): (1) a \u00c1RVORE de condi\u00e7\u00e3o — o `||` cujo 2º
        // termo \u00e9 uma CADEIA && (`v > 100 || (v > 50 && v < 75)`) — o
        // condTree recursa com limite; (2) o HOIST (\u00a7238) sombreava o
        // PAR\u00c2METRO com o default (`var n = 0`) — o collatz rodava para
        // sempre; o hoist pula os slots nomeados. Zero-drift executado.
        Path javaFile = dir.resolve("Mt.java");
        Files.writeString(javaFile, """
                public class Mt {
                    static boolean band(int v) { return v > 100 || (v > 50 && v < 75); }
                    static int collatz(int n) { int steps = 0; while (n != 1) { if (n % 2 == 0) { n = n / 2; } else { n = 3 * n + 1; } steps++; } return steps; }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Mt.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        assertFalse(kof.contains("var arg0 ="), "o par\u00e2metro n\u00e3o sombreia:\n" + kof);
        String kofRun = kof + "\nmain() {\n    println(Mt.band(60))\n    println(Mt.band(200))\n    println(Mt.band(10))\n    println(Mt.collatz(6))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: true | true | false | 8
        assertEquals("true\ntrue\nfalse\n8", output, "zero-drift: \u00e1rvore + collatz id\u00eanticos ao Java");
    }

    @Test
    void stringSwitchStatementRecovers(@TempDir Path dir) throws Exception {
        // Fase E (14/10): o switch de STRING em forma STATEMENT (`r = 0;
        // switch (s) { case "a": r = 1; break; ... } return r;`) — o desugar
        // hashCode/equals com o PRÓLOGO REAL antes (`r = 0`), os braços
        // STATEMENT ([push, istore, goto]) e o commonEnd = o ALVO DOS GOTOS
        // (NÃO o offset do return — medido: 100 vs 101). Zero-drift.
        Path javaFile = dir.resolve("Ssw.java");
        Files.writeString(javaFile, """
                public class Ssw {
                    static int pick(String s) {
                        int r = 0;
                        switch (s) {
                            case "a": r = 1; break;
                            case "b": r = 2; break;
                            default: r = -1;
                        }
                        return r;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Ssw.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        assertTrue(kof.contains("case \"a\""), kof);
        String kofRun = kof + "\nmain() {\n    println(Ssw.pick(\"a\"))\n    println(Ssw.pick(\"b\"))\n    println(Ssw.pick(\"z\"))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: 1 | 2 | -1
        assertEquals("1\n2\n-1", output, "zero-drift: string-switch statement id\u00eantico ao Java");
    }

    @Test
    void classicAlgorithmsRecoveZeroDrift(@TempDir Path dir) throws Exception {
        // VALIDA\u00c7\u00c3O (14/10): os ALGORITMOS CL\u00c1SSICOS (fib recursivo,
        // gcd, soma de d\u00edgitos, contagem de vogais com chamada de
        // m\u00e9todo na condi\u00e7\u00e3o, pad por concat em loop, mid3 com
        // Math.max/min aninhados, tern\u00e1rio Bool→String) — 0 stubs e as 9
        // linhas de sa\u00edda id\u00eanticas ao Java.
        Path javaFile = dir.resolve("Alg.java");
        Files.writeString(javaFile, """
                public class Alg {
                    static int fib(int n) { if (n < 2) { return n; } return fib(n - 1) + fib(n - 2); }
                    static int gcd(int a, int b) { if (b == 0) { return a; } return gcd(b, a % b); }
                    static int sumDigits(int n) { int s = 0; while (n > 0) { s += n % 10; n = n / 10; } return s; }
                    static boolean isVowel(char c) { return c == 'a' || c == 'e' || c == 'i'; }
                    static int countVowels(String s) { int n = 0; for (int i = 0; i < s.length(); i++) { if (isVowel(s.charAt(i))) { n++; } } return n; }
                    static String pad(String s, int w) { while (s.length() < w) { s = s + " "; } return s; }
                    static String boolStr(boolean b) { return b ? "yes" : "no"; }
                    static int mid3(int a, int b, int c) { return a + b + c - Math.max(a, Math.max(b, c)) - Math.min(a, Math.min(b, c)); }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Alg.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + "\nmain() {\n    println(Alg.fib(10))\n    println(Alg.gcd(48, 18))\n    println(Alg.sumDigits(987))\n    println(Alg.countVowels(\"kotlin like\"))\n    println(Alg.pad(\"ab\", 5) + \"|\" )\n    println(Alg.boolStr(true))\n    println(Alg.mid3(5, 1, 9))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: 55 | 6 | 24 | 4 | ab   | | yes | 5
        assertEquals("55\n6\n24\n3\nab   |\nyes\n5", output, "zero-drift: algoritmos cl\u00e1ssicos id\u00eanticos ao Java");
    }

    @Test
    void staticInitAndBoxingClassRecovers(@TempDir Path dir) throws Exception {
        // VALIDA\u00c7\u00c3O (14/10): o CLINIT FOLD (`static { counter = 10;
        // prefix = "n"; }` vira os inicializadores dos fields EXACT), o
        // boxing/unboxing de wrappers (Integer.valueOf/x.intValue = \u00ad
        // identidade), a cadeia else-if de 3 n\u00edveis como EXPRESS\u00c3O e
        // os loops duplos — 0 stubs e as 7 linhas id\u00eanticas ao Java.
        Path javaFile = dir.resolve("St.java");
        Files.writeString(javaFile, """
                public class St {
                    static int counter;
                    static String prefix;
                    static { counter = 10; prefix = "n"; }
                    static Integer boxed(int v) { return v; }
                    static int unboxed(Integer v) { return v + 1; }
                    static String grade2(int s) {
                        if (s >= 9) { return "A"; } else if (s >= 7) { return "B"; }
                        else if (s >= 5) { return "C"; } else { return "F"; }
                    }
                    static int doubleLoop(int a, int b) {
                        int t = 0; while (a > 0) { while (b > 0) { t += 2; b--; } a--; } return t;
                    }
                    static int countTo(int limit) {
                        int n = 0; for (int i = 0; i < limit; i++) { for (int j = 0; j < i; j++) { n++; } } return n;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("St.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        assertTrue(kof.contains("static Int counter = 10"), kof);
        String kofRun = kof + "\nmain() {\n    println(St.counter)\n    println(St.prefix + \":\" + St.boxed(7))\n    println(St.unboxed(41))\n    println(St.grade2(8))\n    println(St.doubleLoop(3, 2))\n    println(St.countTo(4))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: 10 | n:7 | 42 | B | 4 | 6
        assertEquals("10\nn:7\n42\nB\n4\n6", output, "zero-drift: static-init + boxing id\u00eanticos ao Java");
    }

    @Test
    void domainObjectCollectionsRecover(@TempDir Path dir) throws Exception {
        // VALIDA\u00c7\u00c3O (14/10): COLE\u00c7\u00d5ES de OBJETOS de dom\u00ednio (o
        // padr\u00e3o repositório: List<Prod> + filtros/agregações) — o
        // checkcast do dom\u00ednio no modo 1-arquivo recusava (o treeScope
        // era null) → o nome SIMPLES do n\u00e3o-JDK \u00e9 fiel: o .kf irmão
        // decompilado declara a classe. Zero-drift executado.
        Path prodFile = dir.resolve("Pr.java");
        Files.writeString(prodFile, """
                public class Pr {
                    String id; int stock;
                    Pr(String id, int stock) { this.id = id; this.stock = stock; }
                    int stock() { return stock; }
                    String id() { return id; }
                }
                """);
        Path repoFile = dir.resolve("Rp.java");
        Files.writeString(repoFile, """
                import java.util.List;
                import java.util.ArrayList;
                public class Rp {
                    static List<Pr> lowStock(List<Pr> ps, int min) {
                        List<Pr> out = new ArrayList<>();
                        for (Pr p : ps) { if (p.stock() < min) { out.add(p); } } return out;
                    }
                    static Pr maxStock(List<Pr> ps) {
                        Pr best = ps.get(0);
                        for (Pr p : ps) { if (p.stock() > best.stock()) { best = p; } } return best;
                    }
                    static int totalUnits(List<Pr> ps) {
                        int t = 0; for (Pr p : ps) { t += p.stock(); } return t;
                    }
                    static boolean allAvailable(List<Pr> ps) {
                        for (Pr p : ps) { if (p.stock() <= 0) { return false; } } return true;
                    }
                }
                """);
        // os DOIS no mesmo javac (o Rp referencia o Pr)
        runJavac(java.util.List.of(prodFile, repoFile), dir);
        String prodKof = Decompile.decompile(dir.resolve("Pr.class"));
        String repoKof = Decompile.decompile(dir.resolve("Rp.class"));
        assertFalse(prodKof.contains("body not recovered"), prodKof);
        assertFalse(repoKof.contains("body not recovered"), repoKof);
        assertTrue(repoKof.contains("as Pr"), "o cast do dom\u00ednio:\n" + repoKof);
        // o kofRun \u00c9 S\u00d3 o main (as classes j\u00e1 est\u00e3o nos pr\u00f3prios arquivos)
        String kofRun = "\nmain() {\n    var ps = listOf(Pr(\"a\", 5), Pr(\"b\", 0), Pr(\"c\", 12))\n    println(Rp.totalUnits(ps))\n    println(Rp.maxStock(ps).id())\n    println(Rp.lowStock(ps, 3).size())\n    println(Rp.allAvailable(ps))\n}\n";
        // writeKof usa nome FIXO (Arr.kf) — os DOIS arquivos com nomes próprios
        Path prodK = dir.resolve("ProdK.kf");
        Path repoK = dir.resolve("RepoK.kf");
        Files.writeString(prodK, prodKof);
        Files.writeString(repoK, repoKof + kofRun);   // o main vai no Repo
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                prodK, repoK), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: 17 | c | 1 | false
        assertEquals("17\nc\n1\nfalse", output, "zero-drift: cole\u00e7\u00f5es de objetos id\u00eanticas ao Java");
    }

    @Test
    void orderServiceClassRecovers(@TempDir Path dir) throws Exception {
        // VALIDA\u00c7\u00c3O (14/10): a classe de SERVI\u00c7O (status por faixas,
        // avg com cast double + empty-guard, join com \u00edndice manual,
        // startsWith com substring(0,1) + .equals ||, findIndex com .get(i)
        // e lastOf com size()-1) — 0 stubs NA PRIMEIRA TENTATIVA e as 7
        // linhas id\u00eanticas ao Java (o front maduro valida de primeira).
        Path javaFile = dir.resolve("Or.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Or {
                    static String status(List<Integer> items) {
                        if (items.isEmpty()) { return "empty"; }
                        int total = 0;
                        for (int i : items) { total += i; }
                        if (total > 100) { return "big"; }
                        if (total > 10) { return "mid"; }
                        return "small";
                    }
                    static double avg(List<Integer> xs) {
                        if (xs.isEmpty()) { return 0.0; }
                        int s = 0;
                        for (int x : xs) { s += x; }
                        return (double) s / xs.size();
                    }
                    static String joinNames(List<String> names) {
                        String r = ""; int i = 0;
                        for (String n : names) { if (i > 0) { r = r + ","; } r = r + n; i++; }
                        return r;
                    }
                    static boolean startsWithVowel(List<String> words) {
                        for (String w : words) {
                            String f = w.substring(0, 1);
                            if (f.equals("a") || f.equals("e") || f.equals("i")) { return true; }
                        }
                        return false;
                    }
                    static int findIndex(List<String> xs, String target) {
                        for (int i = 0; i < xs.size(); i++) { if (xs.get(i).equals(target)) { return i; } }
                        return -1;
                    }
                    static String lastOf(List<String> xs) { return xs.get(xs.size() - 1); }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Or.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + "\nmain() {\n    var items = listOf(5, 10, 90)\n    println(Or.status(listOf()))\n    println(Or.status(items))\n    println(Or.avg(listOf(10, 20, 30)))\n    println(Or.joinNames(listOf(\"a\", \"b\", \"c\")))\n    println(Or.startsWithVowel(listOf(\"x\", \"apple\")))\n    println(Or.findIndex(listOf(\"p\", \"q\"), \"q\"))\n    println(Or.lastOf(listOf(\"u\", \"v\")))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: empty | big | 20.0 | a,b,c | true | 1 | v
        assertEquals("empty\nbig\n20.0\na,b,c\ntrue\n1\nv", output, "zero-drift: a classe de servi\u00e7o id\u00eantica ao Java");
    }

    @Test
    void freshArrayLiteralRecovers(@TempDir Path dir) throws Exception {
        // Fase E (14/10): o ARRAY LITERAL fresh (`new int[2]` + dup +
        // iastore\u00d7N + return — o shape do minMax) — o dup do array vira
        // um TEMP (`var arrT1 = new Int[2]` + as stores no var): o texto
        // `new Int[2][0] = v` n\u00e3o parseia. Zero-drift executado.
        Path javaFile = dir.resolve("Fz.java");
        Files.writeString(javaFile, """
                public class Fz {
                    static int[] minMax(int[] xs) {
                        int mn = xs[0]; int mx = xs[0];
                        for (int x : xs) { if (x < mn) { mn = x; } if (x > mx) { mx = x; } }
                        return new int[] { mn, mx };
                    }
                    static boolean allBelow(int[] xs, int cap) {
                        for (int x : xs) { if (x >= cap) { return false; } } return true;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Fz.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        assertTrue(kof.contains("arrT"), "o temp do array literal:\n" + kof);
        assertFalse(kof.contains("new Int[2][0]"), "sem store no texto do new:\n" + kof);
        String kofRun = kof + "\nmain() {\n    var xs = new Int[3]\n    xs[0] = 4\n    xs[1] = -2\n    xs[2] = 9\n    println(Fz.allBelow(xs, 10))\n    var mm = Fz.minMax(xs)\n    println(mm[0])\n    println(mm[1])\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: true | -2 | 9
        assertEquals("true\n-2\n9", output, "zero-drift: array literal id\u00eantico ao Java");
    }

    @Test
    void ternaryInArgumentRecovers(@TempDir Path dir) throws Exception {
        // Fase E (14/10): o TERN\u00c1RIO-EM-ARGUMENTO (`"$" + cents/100 + "." +
        // (cents%100 < 10 ? "0" : "") + cents%100` — branches no MEIO da
        // express\u00e3o linear). O machineRun avalia os 2 braços standalone e
        // salta p/ o join (pcJump); o resultado \u00e9 PARENTESADO (o else
        // sem o parens engolia o concat seguinte e PERDIA o `+ M` no braço
        // verdadeiro). Zero-drift executado: 405/50/12000 = $4.05/$0.50/$120.00.
        Path javaFile = dir.resolve("Mo.java");
        Files.writeString(javaFile, """
                public class Mo {
                    static String money(int cents) {
                        return "$" + cents / 100 + "." + (cents % 100 < 10 ? "0" : "") + cents % 100;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Mo.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        assertTrue(kof.contains("(if ("), "o ternário parentesado:\n" + kof);
        String kofRun = kof + "\nmain() {\n    println(Mo.money(405))\n    println(Mo.money(50))\n    println(Mo.money(12000))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: $4.05 | $0.50 | $120.00
        assertEquals("$4.05\n$0.50\n$120.00", output, "zero-drift: money id\u00eantico ao Java");
    }

    @Test
    void signComparisonWithPrologueRecovers(@TempDir Path dir) throws Exception {
        // Fase E (14/10): a COMPARA\u00c7\u00c3O-POR-SINAL COM PR\u00d3LOGO
        // (lcmp 0x94 / dcmpl 0x97 / dcmpg 0x98): [prologue, op2, op1, cmp,
        // ifXX, iconst_1, goto J, iconst_0, ireturn]. O scan k desce do n-7
        // at\u00e9 a fatia que deixa EXATAMENTE 2 valores na pilha (medido:
        // k = n-11 — [dload d, dload r, dload r, dmul] antes do dcmpg); o
        // esp\u00e9lhete \u00e9 o FALL-THROUGH-TRUE (a forma limpa
        // `v10 <= (r*r)`, n\u00e3o a invertida). Zero-drift executado:
        // dist(0,0,3,4)=5.0, quadrantes I..IV+axis, poly(2.5)=6.0,
        // inCircle true/false — 9/9 id\u00eanticos ao Java.
        Path javaFile = dir.resolve("Geo.java");
        Files.writeString(javaFile, """
                public class Geo {
                    static double dist(double x1, double y1, double x2, double y2) {
                        double dx = x2 - x1;
                        double dy = y2 - y1;
                        return Math.sqrt(dx * dx + dy * dy);
                    }
                    static String quad(int x, int y) {
                        if (x > 0 && y > 0) { return "I"; }
                        if (x < 0 && y > 0) { return "II"; }
                        if (x < 0 && y < 0) { return "III"; }
                        if (x > 0 && y < 0) { return "IV"; }
                        return "axis";
                    }
                    static boolean inCircle(double px, double py, double cx, double cy, double r) {
                        double d = (px - cx) * (px - cx) + (py - cy) * (py - cy);
                        return d <= r * r;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Geo.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        assertTrue(kof.contains("return v10 <= (arg4 * arg4)") || kof.contains("<="),
                "a compara\u00e7\u00e3o por sinal:\n" + kof);
        String kofRun = kof + "\nmain() {\n    println(Geo.dist(0, 0, 3, 4))\n    println(Geo.quad(1, 2))\n"
                + "    println(Geo.quad(-1, 2))\n    println(Geo.quad(-1, -2))\n    println(Geo.quad(1, -2))\n"
                + "    println(Geo.quad(0, 5))\n    println(Geo.inCircle(1, 1, 0, 0, 2))\n"
                + "    println(Geo.inCircle(5, 5, 0, 0, 2))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: 5.0 | I | II | III | IV | axis | true | false
        assertEquals("5.0\nI\nII\nIII\nIV\naxis\ntrue\nfalse", output,
                "zero-drift: dist/quadrantes/inCircle id\u00eanticos ao Java");
    }

    @Test
    void multiInsnElseReturnJoinRecovers(@TempDir Path dir) throws Exception {
        // Fase E (14/10): o RETURN-JOIN com o ELSE MULTI-insn — o Fase C
        // exigia o else de UMA insn; o avg real (`xs.isEmpty() ? 0 :
        // (double) sum / xs.size()`) tem o else = [iload, i2d, aload, size,
        // i2d, ddiv] (6 insns) caindo no bloco do dreturn. O stackValue
        // decide (null = recusa, R6). Zero-drift executado: tier
        // gold/silver/none, avg(listOf(2,4,6))=4.0, avg(listOf())=0.0 — 5/5
        // id\u00eanticos ao Java.
        Path javaFile = dir.resolve("Cu.java");
        Files.writeString(javaFile, """
                public class Cu {
                    static String tier(int points) {
                        String t = "none";
                        if (points >= 1000) { t = "gold"; }
                        else if (points >= 500) { t = "silver"; }
                        return t;
                    }
                    static double avg(java.util.List<Integer> xs) {
                        int sum = 0;
                        for (int x : xs) { sum += x; }
                        return xs.isEmpty() ? 0 : (double) sum / xs.size();
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Cu.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        assertTrue(kof.contains("return if ("), "o return-ternário:\n" + kof);
        String kofRun = kof + "\nmain() {\n    println(Cu.tier(1200))\n    println(Cu.tier(600))\n"
                + "    println(Cu.tier(10))\n    println(Cu.avg(listOf(2, 4, 6)))\n"
                + "    println(Cu.avg(listOf()))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: gold | silver | none | 4.0 | 0.0
        assertEquals("gold\nsilver\nnone\n4.0\n0.0", output,
                "zero-drift: tier/avg id\u00eanticos ao Java");
    }

    @Test
    void arrayLoopsWithEarlyReturnRecovers(@TempDir Path dir) throws Exception {
        // Fase E (14/10): o dom\u00ednio ARRAY — 4 shapes travados SEM c\u00f3digo
        // novo (a 3\u00aa valida\u00e7\u00e3o consecutiva out-of-the-box): o loop
        // indexado somando (total), o loop de PREENCHIMENTO de array
        // (doubled: newarray + astore no \u00edndice), o for-each com return
        // antecipado (hasNegative), o find com return dentro do if do loop
        // (find). Zero-drift executado: 100/20/80/false/true/2/-1 — 7/7
        // id\u00eanticos ao Java.
        Path javaFile = dir.resolve("Inv.java");
        Files.writeString(javaFile, """
                public class Inv {
                    static int total(int[] prices) {
                        int t = 0;
                        for (int i = 0; i < prices.length; i++) { t += prices[i]; }
                        return t;
                    }
                    static int[] doubled(int[] xs) {
                        int[] out = new int[xs.length];
                        for (int i = 0; i < xs.length; i++) { out[i] = xs[i] * 2; }
                        return out;
                    }
                    static boolean hasNegative(int[] xs) {
                        for (int x : xs) { if (x < 0) { return true; } }
                        return false;
                    }
                    static int find(int[] xs, int target) {
                        for (int i = 0; i < xs.length; i++) {
                            if (xs[i] == target) { return i; }
                        }
                        return -1;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Inv.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        // Fase E (14/10): os loops INDEXADOS de array saem como WHILE (o
        // idioma while-era-um-for do bytecode); o for-in do Kof é o shape
        // ITERATOR (List), não o arraylength
        assertTrue(kof.contains("while (") && kof.contains("["), "os loops de array:\n" + kof);
        String kofRun = kof + """
                main() {
                    var ps = new Int[4]
                    ps[0] = 10
                    ps[1] = 20
                    ps[2] = 30
                    ps[3] = 40
                    println(Inv.total(ps))
                    var db = Inv.doubled(ps)
                    println(db[0])
                    println(db[3])
                    println(Inv.hasNegative(ps))
                    var neg = new Int[3]
                    neg[0] = 5
                    neg[1] = -1
                    neg[2] = 7
                    println(Inv.hasNegative(neg))
                    println(Inv.find(ps, 30))
                    println(Inv.find(ps, 99))
                }
                """;
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: 100 | 20 | 80 | false | true | 2 | -1
        assertEquals("100\n20\n80\nfalse\ntrue\n2\n-1", output,
                "zero-drift: os loops de array id\u00eanticos ao Java");
    }

    @Test
    void stringSearchLoopsRecovers(@TempDir Path dir) throws Exception {
        // Fase E (14/10): o dom\u00ednio STRING — 4 shapes travados SEM c\u00f3digo
        // novo (a 4\u00aa valida\u00e7\u00e3o consecutiva out-of-the-box): o concat
        // multi-parte com repeti\u00e7\u00e3o do par\u00e2metro (tag), o while-pad
        // (pad), o loop de busca com o indexOf de DOIS ARGUMENTOS (count —
        // a forma (String, Int); o teto \u00e9 o overload por CHAR) e o
        // two-pointer com charAt (isPalindrome). Zero-drift executado:
        // <b>hi</b>/ab   |/3/2/true/false — 6/6 id\u00eanticos ao Java.
        Path javaFile = dir.resolve("Lib.java");
        Files.writeString(javaFile, """
                public class Lib {
                    static String tag(String name, String value) {
                        return "<" + name + ">" + value + "</" + name + ">";
                    }
                    static String pad(String s, int width) {
                        String r = s;
                        while (r.length() < width) { r = r + " "; }
                        return r;
                    }
                    static int count(String hay, String needle) {
                        int n = 0;
                        int i = hay.indexOf(needle);
                        while (i >= 0) {
                            n++;
                            i = hay.indexOf(needle, i + 1);
                        }
                        return n;
                    }
                    static boolean isPalindrome(String s) {
                        int lo = 0;
                        int hi = s.length() - 1;
                        while (lo < hi) {
                            if (s.charAt(lo) != s.charAt(hi)) { return false; }
                            lo++;
                            hi--;
                        }
                        return true;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Lib.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        assertTrue(kof.contains("while (") && kof.contains("indexOf"), "os loops de string:\n" + kof);
        String kofRun = kof + """
                main() {
                    println(Lib.tag("b", "hi"))
                    println(Lib.pad("ab", 5) + "|")
                    println(Lib.count("aXbXcX", "X"))
                    println(Lib.count("aaa", "aa"))
                    println(Lib.isPalindrome("arara"))
                    println(Lib.isPalindrome("java"))
                }
                """;
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: <b>hi</b> | ab   | | 3 | 2 | true | false
        assertEquals("<b>hi</b>\nab   |\n3\n2\ntrue\nfalse", output,
                "zero-drift: os loops de string id\u00eanticos ao Java");
    }

    @Test
    void collectionAccumulatorsRecovers(@TempDir Path dir) throws Exception {
        // Fase E (14/10): os ACUMULADORES de coleção — 3 shapes: o tally
        // (containsKey/put/get com o round-trip de boxing do javac: `((m.get
        // (w) as Int) + 1).toInt()`), os for-each ANINHADOS sobre
        // List<List<String>> e a iteração de keySet (mapeada p/ keys do Kof —
        // medido SEM025: o verbatim keySet() não resolve). A ORDEM do Map é
        // não-especificada NOS DOIS lados — a face \u00e9 order-free (size +
        // contains, nunca a concat direta). Zero-drift executado:
        // 3/2/1/6/2/true/true == Java.
        Path javaFile = dir.resolve("Board.java");
        Files.writeString(javaFile, """
                import java.util.List;
                import java.util.Map;
                import java.util.ArrayList;
                import java.util.HashMap;
                public class Board {
                    static Map<String, Integer> tally(List<String> words) {
                        Map<String, Integer> m = new HashMap<>();
                        for (String w : words) {
                            if (m.containsKey(w)) { m.put(w, m.get(w) + 1); }
                            else { m.put(w, 1); }
                        }
                        return m;
                    }
                    static int totalLengths(List<List<String>> groups) {
                        int t = 0;
                        for (List<String> g : groups) {
                            for (String s : g) { t += s.length(); }
                        }
                        return t;
                    }
                    static List<String> names(Map<Integer, String> m) {
                        List<String> out = new ArrayList<>();
                        for (Integer k : m.keySet()) {
                            out.add(m.get(k));
                        }
                        return out;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Board.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        assertTrue(kof.contains("keys()") && kof.contains("for (var "), "o tally/keys:\n" + kof);
        String kofRun = kof + """
                main() {
                    var m = Board.tally(listOf("a", "b", "a", "c", "b", "a"))
                    println(m.get("a"))
                    println(m.get("b"))
                    println(m.get("c"))
                    var gs = listOf(listOf("ab", "cde"), listOf("f"))
                    println(Board.totalLengths(gs))
                    var nm = Board.names(mapOf(1, "x", 2, "y"))
                    println(nm.size())
                    println(nm.contains("x"))
                    println(nm.contains("y"))
                }
                """;
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: 3 | 2 | 1 | 6 | 2 | true | true (order-free p/ o Map)
        assertEquals("3\n2\n1\n6\n2\ntrue\ntrue", output,
                "zero-drift: tally/totalLengths/names id\u00eanticos ao Java");
    }

    @Test
    void matrix2dDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (15/10): o dom\u00ednio MATRIZ 2D — 4 shapes travados SEM
        // c\u00f3digo novo (a 5\u00aa valida\u00e7\u00e3o consecutiva
        // out-of-the-box): o multianewarray quadrado + a diagonal
        // (identity), o somat\u00f3rio com o loop ANINHADO indexado
        // (sumAll: m[i].length no interno), o preenchimento de linhas com
        // acumulador interno (rowSums) e a transposta com os \u00edndices
        // TROCADOS (t[j][i] = m[i][j]). Zero-drift executado:
        // 3/36/6/30/1/0 — 7/7 id\u00eanticos ao Java. Sintaxe do harness
        // medida: o array ragged do Kof \u00e9 `new Int[n][0]` (o
        // `new Int[n][]` \u00e9 PARSE041).
        Path javaFile = dir.resolve("Matrix.java");
        Files.writeString(javaFile, """
                public class Matrix {
                    static int[][] identity(int n) {
                        int[][] m = new int[n][n];
                        for (int i = 0; i < n; i++) { m[i][i] = 1; }
                        return m;
                    }
                    static int sumAll(int[][] m) {
                        int s = 0;
                        for (int i = 0; i < m.length; i++) {
                            for (int j = 0; j < m[i].length; j++) { s += m[i][j]; }
                        }
                        return s;
                    }
                    static int[] rowSums(int[][] m) {
                        int[] out = new int[m.length];
                        for (int i = 0; i < m.length; i++) {
                            int r = 0;
                            for (int j = 0; j < m[i].length; j++) { r += m[i][j]; }
                            out[i] = r;
                        }
                        return out;
                    }
                    static int[][] transpose(int[][] m) {
                        int[][] t = new int[m[0].length][m.length];
                        for (int i = 0; i < m.length; i++) {
                            for (int j = 0; j < m[i].length; j++) { t[j][i] = m[i][j]; }
                        }
                        return t;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Matrix.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        assertTrue(kof.contains("Int[][]") && kof.contains("while ("), "a matriz:\n" + kof);
        String kofRun = kof + """
                main() {
                    var id = Matrix.identity(3)
                    println(Matrix.sumAll(id))
                    var g = new Int[2][0]
                    var g0 = new Int[3]
                    g0[0] = 1
                    g0[1] = 2
                    g0[2] = 3
                    var g1 = new Int[2]
                    g1[0] = 10
                    g1[1] = 20
                    g[0] = g0
                    g[1] = g1
                    println(Matrix.sumAll(g))
                    var rs = Matrix.rowSums(g)
                    println(rs[0])
                    println(rs[1])
                    var t = Matrix.transpose(g)
                    println(t[0][0])
                    println(t[2][1])
                }
                """;
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: 3 | 36 | 6 | 30 | 1 | 0
        assertEquals("3\n36\n6\n30\n1\n0", output,
                "zero-drift: a matriz 2D id\u00eantica ao Java");
    }

    @Test
    void signCompareFusedPrologueRecovers(@TempDir Path dir) throws Exception {
        // Fase E (15/10): a compara\u00e7\u00e3o-por-sinal com PR\u00d3LOGO FUNDIDO
        // no MESMO bloco do teste (`if (sub > 200.0) { sub = sub * 0.9 }`).
        // Tr\u00eas pe\u00e7as: (1) o loadValue ganhou o overload cp-aware — o
        // `ldc2_w` (0x14, const Double do CP) resolve na extra\u00e7\u00e3o de
        // condi\u00e7\u00f5es; (2) o pair-case do blockCondition ganhou a guarda
        // PUREZA (s\u00f3 o bloco exato [op2, op1, cmp, ifXX]) — sem ela o cond
        // sa\u00eda do rabo de um bloco fundido e o PR\u00d3LOGO era engolido (cond
        // referenciando local n\u00e3o-declarada = c\u00f3digo errado, bug latente
        // da fam\u00edlia lcmp tamb\u00e9m); (3) o testSplit ganhou a fam\u00edlia de
        // sinal no sufixo com o espelho 2-op (o inv do caller \u00e9 o
        // valor-teste `> 0` — medido: sa\u00eda `v3 > 0 200.0`). Zero-drift
        // executado: 100.0/270.0/1200/200/receipt/true/false — 7/7 id\u00eanticos
        // ao Java; o receipt (concat de args MISTOS String/Int/Double com
        // invokedynamic) recupera de gra\u00e7a.
        Path javaFile = dir.resolve("Store.java");
        Files.writeString(javaFile, """
                public class Store {
                    static double withTax(double price, int qty) {
                        double sub = price * qty;
                        if (sub > 200) { sub = sub * 0.9; }
                        return sub;
                    }
                    static int loyalty(int cents) {
                        int pts = cents / 100;
                        if (pts > 500) { return pts * 2; }
                        return pts;
                    }
                    static String receipt(String item, int qty, double price) {
                        return item + " x" + qty + " = " + withTax(price, qty);
                    }
                    static boolean freeShip(int cents) { return cents >= 9900; }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Store.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        assertTrue(kof.contains("if (v3 > 200.0)") && kof.contains("var v3 = (arg0 * arg1.toDouble())"),
                "o prólogo fundido + o cond ldc2:\n" + kof);
        String kofRun = kof + """
                main() {
                    println(Store.withTax(50.0, 2))
                    println(Store.withTax(150.0, 2))
                    println(Store.loyalty(60000))
                    println(Store.loyalty(20000))
                    println(Store.receipt(" mug", 2, 150.0))
                    println(Store.freeShip(9900))
                    println(Store.freeShip(5000))
                }
                """;
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: 100.0 | 270.0 | 1200 | 200 | mug x2 = 270.0 | true | false
        assertEquals("100.0\n270.0\n1200\n200\n mug x2 = 270.0\ntrue\nfalse", output,
                "zero-drift: withTax/loyalty/receipt/freeShip id\u00eanticos ao Java");
    }

    @Test
    void ternaryArmsSeeOuterLocalsRecovers(@TempDir Path dir) throws Exception {
        // Fase E (15/10): os BRA\u00c7OS do tern\u00e1rio avaliam com o mapa de
        // locals do run EXTERNO (o overload machineRun(.., seed)) — sem o
        // seed o `g` do astore_1 virava o `v1` FANTASMA no bra\u00e7o else
        // (`"pass: " + v1` = SEM011; o bra\u00e7o standalone n\u00e3o v\u00ea o
        // binding do pr\u00f3logo). Medido no verdict: `return g.equals("F") ?
        // "fail" : "pass: " + g` → `(if (Grade.letter(arg0) == "F") "fail"
        // else "pass: " + Grade.letter(arg0))` — o local PURE + usado 3x
        // inline (o store eliminado por inlining \u00e9 equivalente). A classe
        // grade completa: a cadeia else-if 4 n\u00edveis (letter), o guard
        // vazio + for-each + cast/div (average — o 0-length early return), o
        // tern\u00e1rio com o local externo (verdict) e o max-loop indexado
        // (best). Zero-drift executado: A/B/C/D/F/8.5/0.0/fail/pass: B/10 —
        // 10/10 id\u00eanticos ao Java.
        Path javaFile = dir.resolve("Grade.java");
        Files.writeString(javaFile, """
                public class Grade {
                    static String letter(int score) {
                        if (score >= 90) { return "A"; }
                        if (score >= 80) { return "B"; }
                        if (score >= 70) { return "C"; }
                        if (score >= 60) { return "D"; }
                        return "F";
                    }
                    static double average(int[] scores) {
                        if (scores.length == 0) { return 0.0; }
                        int sum = 0;
                        for (int s : scores) { sum += s; }
                        return (double) sum / scores.length;
                    }
                    static String verdict(int score) {
                        String g = letter(score);
                        return g.equals("F") ? "fail" : "pass: " + g;
                    }
                    static int best(int[] scores) {
                        int b = scores[0];
                        for (int i = 1; i < scores.length; i++) {
                            if (scores[i] > b) { b = scores[i]; }
                        }
                        return b;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Grade.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        assertFalse(kof.contains("+ v1"), "o fantasma v1:\n" + kof);
        assertTrue(kof.contains("\"pass: \" + Grade.letter(arg0)"), "o else-arm com o local externo:\n" + kof);
        String kofRun = kof + """
                main() {
                    println(Grade.letter(95))
                    println(Grade.letter(85))
                    println(Grade.letter(72))
                    println(Grade.letter(60))
                    println(Grade.letter(10))
                    var xs = new Int[4]
                    xs[0] = 8
                    xs[1] = 7
                    xs[2] = 9
                    xs[3] = 10
                    println(Grade.average(xs))
                    println(Grade.average(new Int[0]))
                    println(Grade.verdict(55))
                    println(Grade.verdict(88))
                    println(Grade.best(xs))
                }
                """;
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: A | B | C | D | F | 8.5 | 0.0 | fail | pass: B | 10
        assertEquals("A\nB\nC\nD\nF\n8.5\n0.0\nfail\npass: B\n10", output,
                "zero-drift: letter/average/verdict/best id\u00eanticos ao Java");
    }

    @Test
    void chainJoinSiblingIfsRecovers(@TempDir Path dir) throws Exception {
        // Fase E (15/10): o JOIN do && dentro de LOOP — os falsos de TODOS
        // os testes da cadeia convergem no mesmo alvo (preds > 2 POR
        // DESENHO); o pureIfThen exigia exatamente 2 preds e recusava → o
        // bra\u00e7o then andava pelo IF-IRM\u00c3O (que aninhava dentro do then,
        // com o incremento do loop dentro do if!) e o m\u00e9todo inteiro
        // stubava. O relax: os preds extras permitidos s\u00e3o EXATAMENTE os
        // testes coletados no walk do &&; qualquer outro pred segue
        // recusando (R6). Medido no strong: dois `if (c >= X && c <= Y)
        // { flag = true }` IRM\u00c3OS dentro do while + o return-if com &&
        // no fim. ORACLE LESSON re-aplicada: os primeiros inputs nunca
        // chegavam ao true (7 chars < 8) — a face usa 8+ de verdade.
        // Zero-drift executado: true/false/true/false/5/0/****ey/*** — 8/8.
        Path javaFile = dir.resolve("Pass.java");
        Files.writeString(javaFile, """
                public class Pass {
                    static boolean strong(String pwd) {
                        if (pwd.length() < 8) { return false; }
                        boolean hasDigit = false;
                        boolean hasUpper = false;
                        for (int i = 0; i < pwd.length(); i++) {
                            char c = pwd.charAt(i);
                            if (c >= '0' && c <= '9') { hasDigit = true; }
                            if (c >= 'A' && c <= 'Z') { hasUpper = true; }
                        }
                        return hasDigit && hasUpper;
                    }
                    static int bits(boolean a, boolean b, boolean c) {
                        int n = 0;
                        if (a) { n += 1; }
                        if (b) { n += 2; }
                        if (c) { n += 4; }
                        return n;
                    }
                    static String mask(String s) {
                        if (s.length() <= 4) { return "***"; }
                        return "****" + s.substring(s.length() - 2);
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Pass.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        assertTrue(kof.contains("&&"), "a cadeia &&:\n" + kof);
        String kofRun = kof + """
                main() {
                    println(Pass.strong("Abcdefg1"))
                    println(Pass.strong("abcdefg1"))
                    println(Pass.strong("ABCDEFG1"))
                    println(Pass.strong("a1"))
                    println(Pass.bits(true, false, true))
                    println(Pass.bits(false, false, false))
                    println(Pass.mask("secretkey"))
                    println(Pass.mask("abc"))
                }
                """;
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: true | false | true | false | 5 | 0 | ****ey | ***
        assertEquals("true\nfalse\ntrue\nfalse\n5\n0\n****ey\n***", output,
                "zero-drift: strong/bits/mask id\u00eanticos ao Java");
    }

    @Test
    void cartDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (15/10): o dom\u00ednio CARRINHO — 4 shapes travados SEM
        // c\u00f3digo novo: o CONTINUE dentro do for-each somando com filtro
        // (total), o loop INDEXADO com .get() e return antecipado
        // (firstNegative), o concat acumulador com o sufixo de size
        // (describe) e o continue-com-acumulador duplo + o tern\u00e1rio do
        // avg (avgNonZero). Zero-drift executado: 60/0/2/-1/a;b; (2)/empty/
        // 4.0/0.0 — 8/8 id\u00eanticos ao Java.
        Path javaFile = dir.resolve("Cart.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Cart {
                    static int total(List<Integer> prices) {
                        int t = 0;
                        for (int p : prices) {
                            if (p < 0) { continue; }
                            t += p;
                        }
                        return t;
                    }
                    static int firstNegative(List<Integer> xs) {
                        for (int i = 0; i < xs.size(); i++) {
                            if (xs.get(i) < 0) { return i; }
                        }
                        return -1;
                    }
                    static String describe(List<String> items) {
                        if (items.isEmpty()) { return "empty"; }
                        String s = "";
                        for (String it : items) {
                            s = s + it + ";";
                        }
                        return s + " (" + items.size() + ")";
                    }
                    static double avgNonZero(List<Integer> xs) {
                        int sum = 0;
                        int n = 0;
                        for (int x : xs) {
                            if (x == 0) { continue; }
                            sum += x;
                            n++;
                        }
                        return n == 0 ? 0 : (double) sum / n;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Cart.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Cart.total(listOf(10, -5, 20, 30)))
                    println(Cart.total(listOf()))
                    println(Cart.firstNegative(listOf(3, 7, -1, 9)))
                    println(Cart.firstNegative(listOf(3, 7)))
                    println(Cart.describe(listOf("a", "b")))
                    println(Cart.describe(listOf()))
                    println(Cart.avgNonZero(listOf(2, 0, 4, 6)))
                    println(Cart.avgNonZero(listOf(0, 0)))
                }
                """;
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: 60 | 0 | 2 | -1 | a;b; (2) | empty | 4.0 | 0.0
        assertEquals("60\n0\n2\n-1\na;b; (2)\nempty\n4.0\n0.0", output,
                "zero-drift: total/firstNegative/describe/avgNonZero id\u00eanticos ao Java");
    }

    @Test
    void uninitializedLocalChainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (15/10): o local SEM INIT na cadeia else-if — `String s;`
        // do Java NÃO gera store no prólogo (o `String g = "";` do Conv
        // gerava, por isso o level passava e o size stubava): o primeiro
        // `var` nascia DENTRO do 1º braço e os demais atribuíam slot
        // não-declarado = SEM011. O hoist PROFUNDO (scan por RANGE de
        // offsets do then e do else-subtree — os stores aninhados que o
        // hoist raso não vê) sobe a declaração default ANTES do if. O
        // default de REFERÊNCIA só existe honestamente quando TODOS os
        // valores armazenados são LITERAIS String (o `""` é o tipo real
        // medido; SEM048 bane o null). Zero-drift executado: overlaps
        // true/false, clamp 5/0/10, size tiny:5/small:50/medium:500/large:
        // 5000, mid 3.5 — 10/10 idênticos ao Java.
        Path javaFile = dir.resolve("Range.java");
        Files.writeString(javaFile, """
                public class Range {
                    static boolean overlaps(int a1, int a2, int b1, int b2) {
                        return a1 <= b2 && b1 <= a2;
                    }
                    static int clamp(int v, int lo, int hi) {
                        if (v < lo) { return lo; }
                        if (v > hi) { return hi; }
                        return v;
                    }
                    static String size(int n) {
                        String s;
                        if (n < 10) { s = "tiny"; }
                        else if (n < 100) { s = "small"; }
                        else if (n < 1000) { s = "medium"; }
                        else { s = "large"; }
                        return s + ":" + n;
                    }
                    static double mid(int lo, int hi) { return (lo + hi) / 2.0; }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Range.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        assertTrue(kof.contains("var v1 = \"\""), "o hoist profundo:\n" + kof);
        String kofRun = kof + """
                main() {
                    println(Range.overlaps(1, 5, 4, 8))
                    println(Range.overlaps(1, 5, 6, 8))
                    println(Range.clamp(5, 0, 10))
                    println(Range.clamp(-3, 0, 10))
                    println(Range.clamp(99, 0, 10))
                    println(Range.size(5))
                    println(Range.size(50))
                    println(Range.size(500))
                    println(Range.size(5000))
                    println(Range.mid(3, 4))
                }
                """;
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: true | false | 5 | 0 | 10 | tiny:5 | small:50 | medium:500 | large:5000 | 3.5
        assertEquals("true\nfalse\n5\n0\n10\ntiny:5\nsmall:50\nmedium:500\nlarge:5000\n3.5", output,
                "zero-drift: overlaps/clamp/size/mid id\u00eanticos ao Java");
    }

    @Test
    void scaleDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (15/10): o dom\u00ednio CONVERS\u00d3ES/escala — 5 shapes sem
        // c\u00f3digo novo: as convers\u00f5es lineares de temperatura (kelvin/
        // fahrenheit — a aritm\u00e9tica Double com constantes), o \u00cdNDICE
        // com matem\u00e1tica intermedi\u00e1ria (bmi: h/100, m*m, w/(m*m)), os
        // limiares encadeados de Double (label — 3 ifs de retorno) e a
        // cadeia else-if com local acumulador de Double (discount). Zero-
        // drift executado: 298.15/212.0/22.857142857142858/under/normal/
        // over/obese/108.0/57.0/30.0 — 10/10 id\u00eanticos ao Java.
        Path javaFile = dir.resolve("Scale.java");
        Files.writeString(javaFile, """
                public class Scale {
                    static double kelvin(double c) { return c + 273.15; }
                    static double fahrenheit(double c) { return c * 9 / 5 + 32; }
                    static double bmi(double w, double h) {
                        double m = h / 100;
                        return w / (m * m);
                    }
                    static String label(double bmi) {
                        if (bmi < 18.5) { return "under"; }
                        if (bmi < 25) { return "normal"; }
                        if (bmi < 30) { return "over"; }
                        return "obese";
                    }
                    static double discount(double amount) {
                        double d = 0;
                        if (amount >= 100) { d = amount * 0.1; }
                        else if (amount >= 50) { d = amount * 0.05; }
                        return amount - d;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Scale.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Scale.kelvin(25.0))
                    println(Scale.fahrenheit(100.0))
                    println(Scale.bmi(70.0, 175.0))
                    println(Scale.label(17.0))
                    println(Scale.label(22.0))
                    println(Scale.label(27.0))
                    println(Scale.label(35.0))
                    println(Scale.discount(120.0))
                    println(Scale.discount(60.0))
                    println(Scale.discount(30.0))
                }
                """;
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: 298.15 | 212.0 | 22.857142857142858 | under | normal |
        // over | obese | 108.0 | 57.0 | 30.0
        assertEquals("298.15\n212.0\n22.857142857142858\nunder\nnormal\nover\nobese\n108.0\n57.0\n30.0", output,
                "zero-drift: kelvin/fahrenheit/bmi/label/discount id\u00eanticos ao Java");
    }

    @Test
    void statsDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (15/10): o dom\u00ednio ESTAT\u00cdSTICA sobre arrays TIPADOS —
        // 5 shapes sem c\u00f3digo novo: o max-loop cl\u00e1ssico (max), o
        // arg-min com compara\u00e7\u00e3o INDIRETA `xs[i] < xs[mi]` (minIndex —
        // Double[]), o all-positive com return antecipado (allPositive), o
        // SOMAT\u00d3RIO Long[] (sum — o Long de 3B n\u00e3o vira Int) e a
        // contagem com CHAR comparado `s.charAt(i) == target` (countChar —
        // o param sai tipado `Char` no Kof e o literal 'a' compila).
        // Zero-drift executado: 9/1/true/false/6000000000/3 — 6/6
        // id\u00eanticos ao Java.
        Path javaFile = dir.resolve("Stats.java");
        Files.writeString(javaFile, """
                public class Stats {
                    static int max(int[] xs) {
                        int m = xs[0];
                        for (int i = 1; i < xs.length; i++) {
                            if (xs[i] > m) { m = xs[i]; }
                        }
                        return m;
                    }
                    static int minIndex(double[] xs) {
                        int mi = 0;
                        for (int i = 1; i < xs.length; i++) {
                            if (xs[i] < xs[mi]) { mi = i; }
                        }
                        return mi;
                    }
                    static boolean allPositive(int[] xs) {
                        for (int x : xs) {
                            if (x <= 0) { return false; }
                        }
                        return true;
                    }
                    static long sum(long[] xs) {
                        long s = 0;
                        for (long x : xs) { s += x; }
                        return s;
                    }
                    static int countChar(String s, char target) {
                        int n = 0;
                        for (int i = 0; i < s.length(); i++) {
                            if (s.charAt(i) == target) { n++; }
                        }
                        return n;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Stats.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        assertTrue(kof.contains("Char arg1"), "o param Char:\n" + kof);
        String kofRun = kof + """
                main() {
                    var xs = new Int[4]
                    xs[0] = 3
                    xs[1] = 9
                    xs[2] = 4
                    xs[3] = 9
                    println(Stats.max(xs))
                    var ds = new Double[3]
                    ds[0] = 2.5
                    ds[1] = 0.5
                    ds[2] = 1.5
                    println(Stats.minIndex(ds))
                    println(Stats.allPositive(xs))
                    var neg = new Int[2]
                    neg[0] = 1
                    neg[1] = -1
                    println(Stats.allPositive(neg))
                    var ls = new Long[3]
                    ls[0] = 1000000000
                    ls[1] = 2000000000
                    ls[2] = 3000000000
                    println(Stats.sum(ls))
                    println(Stats.countChar("banana", 'a'))
                }
                """;
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: 9 | 1 | true | false | 6000000000 | 3
        assertEquals("9\n1\ntrue\nfalse\n6000000000\n3", output,
                "zero-drift: max/minIndex/allPositive/sum/countChar id\u00eanticos ao Java");
    }

    @Test
    void doubleForEachBothLoopsRecovers(@TempDir Path dir) throws Exception {
        // Fase E (15/10): a FATIA NOMEADA pousada — o M\u00c9TODO com DOIS
        // loops for-each. O javac REUTILIZA o slot do iterator (o escopo do
        // 1\u00ba loop morre), ent\u00e3o o decl do 2\u00ba sai SEM `var`
        // (`v3 = arg1.iterator()`) — a forma sem-var \u00e9 aceita na
        // \u00daLTIMA linha de out (emitida pelo bloco linear imediatamente
        // anterior; a busca backward CORROMPIA via re-entr\u00e2ncia do struct
        // — FSCAN-provado — e foi revertida; o `out.remove(last)` \u00e9
        // seguro por constru\u00e7\u00e3o). Zero-drift executado: recipients
        // dedupe 3/true, domain kof.dev/"", isValid true/false/false,
        // quotaBytes 6000 — 8/8 id\u00eanticos ao Java.
        Path javaFile = dir.resolve("Mail.java");
        Files.writeString(javaFile, """
                import java.util.List;
                import java.util.ArrayList;
                public class Mail {
                    static List<String> recipients(List<String> to, List<String> cc) {
                        List<String> all = new ArrayList<>();
                        for (String r : to) { all.add(r); }
                        for (String r : cc) { if (!all.contains(r)) { all.add(r); } }
                        return all;
                    }
                    static String domain(String email) {
                        int at = email.indexOf("@");
                        if (at < 0) { return ""; }
                        return email.substring(at + 1);
                    }
                    static boolean isValid(String email) {
                        int at = email.indexOf("@");
                        if (at <= 0) { return false; }
                        return email.indexOf("@", at + 1) < 0 && email.contains(".");
                    }
                    static int quotaBytes(List<Integer> sizes) {
                        int total = 0;
                        for (int s : sizes) {
                            if (total + s > 25000000) { break; }
                            total += s;
                        }
                        return total;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Mail.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        assertTrue(kof.contains("for (var v4 in arg1)"), "o 2\u00ba for-each:\n" + kof);
        String kofRun = kof + """
                main() {
                    var all = Mail.recipients(listOf("a@x", "b@x"), listOf("b@x", "c@x"))
                    println(all.size())
                    println(all.contains("c@x"))
                    println(Mail.domain("user@kof.dev"))
                    println(Mail.domain("noat"))
                    println(Mail.isValid("user@kof.dev"))
                    println(Mail.isValid("a@b@c"))
                    println(Mail.isValid("noat"))
                    println(Mail.quotaBytes(listOf(1000, 2000, 3000)))
                }
                """;
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: 3 | true | kof.dev | (vazio) | true | false | false | 6000
        assertEquals("3\ntrue\nkof.dev\n\ntrue\nfalse\nfalse\n6000", output,
                "zero-drift: recipients/domain/isValid/quotaBytes id\u00eanticos ao Java");
    }

    @Test
    void ternaryStoreTerminalBlockRecovers(@TempDir Path dir) throws Exception {
        // Fase E (15/10): o TERNC\u00c1RIO armazenado em local + o bloco
        // TERMINAL do m\u00e9todo (`String sign = b >= 0 ? "+" : "-";
        // return who + ": " + sign + Math.abs(b)`). Duas pe\u00e7as: (1) o
        // if-expression da Fase C emite o store simbolicamente e marca
        // skipFirst no join; (2) o caminho do BLOCO TERMINAL (succ vazio)
        // usava insnsWithin DIRETO, bypassando o blkSeq — o astore do join
        // era re-emitido com a pilha VAZIA (emitLinear-null). O fix = 1
        // linha: blkSeq no terminal. O math.abs(I)I mapeia math.abs (o
        // guard de descriptor). Zero-drift executado: 115/true/false/
        // ana: +115/bob: -11/2 — 6/6 id\u00eanticos ao Java.
        Path javaFile = dir.resolve("Ledger.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Ledger {
                    static int balance(List<Integer> entries) {
                        int b = 0;
                        for (int e : entries) { b += e; }
                        return b;
                    }
                    static boolean overLimit(List<Integer> entries, int limit) {
                        return balance(entries) > limit;
                    }
                    static String report(String who, List<Integer> entries) {
                        int b = balance(entries);
                        String sign = b >= 0 ? "+" : "-";
                        return who + ": " + sign + Math.abs(b);
                    }
                    static List<Integer> positives(List<Integer> xs) {
                        List<Integer> out = new java.util.ArrayList<>();
                        for (int x : xs) {
                            if (x > 0) { out.add(x); }
                        }
                        return out;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Ledger.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        assertTrue(kof.contains("math.abs(v2)"), "o abs mapeado:\n" + kof);
        String kofRun = kof + """
                main() {
                    var es = listOf(100, -30, 45)
                    println(Ledger.balance(es))
                    println(Ledger.overLimit(es, 100))
                    println(Ledger.overLimit(es, 200))
                    println(Ledger.report("ana", es))
                    println(Ledger.report("bob", listOf(-5, -6)))
                    var ps = Ledger.positives(es)
                    println(ps.size())
                }
                """;
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: 115 | true | false | ana: +115 | bob: -11 | 2
        assertEquals("115\ntrue\nfalse\nana: +115\nbob: -11\n2", output,
                "zero-drift: balance/overLimit/report/positives id\u00eanticos ao Java");
    }

    @Test
    void authDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (15/10): o dom\u00ednio AUTH — 4 shapes sem c\u00f3digo novo: a
        // procura em DUAS listas com indexOf/get (check — o indexOf de
        // String + o get pelo \u00edndice + o equals de conte\u00fado), a
        // cadeia de pap\u00e9is com equals/startsWith (role), a extra\u00e7\u00e3o de
        // nomes com indexOf/substring dentro de for-each (names) e o
        // anyMatch com startsWith e return antecipado. Zero-drift
        // executado: true/false/false/root/service/user/2/ana,bob,/true/
        // false — 11/11 id\u00eanticos ao Java.
        Path javaFile = dir.resolve("Auth.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Auth {
                    static boolean check(String user, String pass, List<String> users, List<String> passes) {
                        int ui = users.indexOf(user);
                        if (ui < 0) { return false; }
                        return passes.get(ui).equals(pass);
                    }
                    static String role(String user) {
                        if (user.equals("admin")) { return "root"; }
                        if (user.startsWith("svc_")) { return "service"; }
                        return "user";
                    }
                    static List<String> names(List<String> emails) {
                        List<String> out = new java.util.ArrayList<>();
                        for (String e : emails) {
                            int at = e.indexOf("@");
                            out.add(e.substring(0, at));
                        }
                        return out;
                    }
                    static boolean anyMatch(List<String> xs, String prefix) {
                        for (String x : xs) {
                            if (x.startsWith(prefix)) { return true; }
                        }
                        return false;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Auth.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    var us = listOf("admin", "ana", "svc_backup")
                    var ps = listOf("root123", "ana123", "svc456")
                    println(Auth.check("ana", "ana123", us, ps))
                    println(Auth.check("ana", "errado", us, ps))
                    println(Auth.check("ghost", "x", us, ps))
                    println(Auth.role("admin"))
                    println(Auth.role("svc_backup"))
                    println(Auth.role("ana"))
                    var ns = Auth.names(listOf("ana@x", "bob@y"))
                    println(ns.size())
                    var j = ""
                    for (var n in ns) { j = j + n + "," }
                    println(j)
                    println(Auth.anyMatch(us, "svc_"))
                    println(Auth.anyMatch(us, "zz"))
                }
                """;
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: true | false | false | root | service | user | 2 |
        // ana,bob, | true | false
        assertEquals("true\nfalse\nfalse\nroot\nservice\nuser\n2\nana,bob,\ntrue\nfalse", output,
                "zero-drift: check/role/names/anyMatch id\u00eanticos ao Java");
    }

    @Test
    void recursionEmptyConcatTypeRecovers(@TempDir Path dir) throws Exception {
        // Fase E (15/10): a RECURSÃO clássica + o concat do literal VAZIO —
        // `"" + n` produz a receita de UM placeholder SEM literal (o javac
        // nem põe o literal vazio na receita), e a dobragem para `n` mudava
        // o TIPO (Int no lugar de String → SEM010 no return, medido no
        // Trib.binary). O apply reemite `"" + x` — fiel (o valor é
        // idêntico e o tipo String preservado). 5 métodos recursivos: fib
        // (dupla chamada), fact (Long), gcd (módulo trocado), power,
        // binary (a String recursiva). Zero-drift executado:
        // 55/3628800/6/81/1010 — 5/5 idênticos ao Java.
        Path javaFile = dir.resolve("Trib.java");
        Files.writeString(javaFile, """
                public class Trib {
                    static int fib(int n) {
                        if (n <= 1) { return n; }
                        return fib(n - 1) + fib(n - 2);
                    }
                    static long fact(int n) {
                        if (n <= 1) { return 1L; }
                        return n * fact(n - 1);
                    }
                    static int gcd(int a, int b) {
                        if (b == 0) { return a; }
                        return gcd(b, a % b);
                    }
                    static int power(int base, int exp) {
                        if (exp == 0) { return 1; }
                        return base * power(base, exp - 1);
                    }
                    static String binary(int n) {
                        if (n <= 1) { return "" + n; }
                        return binary(n / 2) + (n % 2);
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Trib.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        assertTrue(kof.contains("\"\" + arg0"), "o concat do literal vazio:\n" + kof);
        String kofRun = kof + """
                main() {
                    println(Trib.fib(10))
                    println(Trib.fact(10))
                    println(Trib.gcd(48, 18))
                    println(Trib.power(3, 4))
                    println(Trib.binary(10))
                }
                """;
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: 55 | 3628800 | 6 | 81 | 1010
        assertEquals("55\n3628800\n6\n81\n1010", output,
                "zero-drift: fib/fact/gcd/power/binary id\u00eanticos ao Java");
    }

    @Test
    void nestedTernaryStoreRecovers(@TempDir Path dir) throws Exception {
        // Fase E (15/10): a FATIA NOMEADA pousada — a CADEIA-TERN\u00c1RIO
        // armazenada em local com cauda de concat (`String g = pct >= 90 ?
        // "A" : pct >= 80 ? "B" : "C"; return g + "(" + (int) pct + ")"`).
        // Duas corre\u00e7\u00f5es: (1) o bothReach do if-expression-store
        // exigia o else DIRETO no join — o else \u00e9 o TESTE INTERNO que
        // converge indireto (via 21/26) → o armReachesJoin; (2) o bra\u00e7o
        // TESTE vira if-expression via o armExpr (o fallback da rodada
        // anterior). O guard de REGI\u00c3O (os preds do join todos dentro
        // de [b.start, tail)) autoriza o join de 3 preds. O cast d2i emite
        // `toInt()`. O ciclo stub-vira-c\u00f3digo-real fechado na rodada
        // seguinte ao R6. Zero-drift executado: 45/A(95)/B(85)/C(70)/30/
        // true/false/false — 8/8 id\u00eanticos ao Java.
        Path javaFile = dir.resolve("Warehouse.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Warehouse {
                    static int pick(List<Integer> stock, List<Integer> order) {
                        int picked = 0;
                        for (int i = 0; i < order.size() && picked < 100; i++) {
                            int want = order.get(i);
                            if (stock.get(i) >= want) { picked += want; }
                        }
                        return picked;
                    }
                    static String grade(double pct) {
                        String g = pct >= 90 ? "A" : pct >= 80 ? "B" : "C";
                        return g + "(" + (int) pct + ")";
                    }
                    static int sumDigits(int n) {
                        int s = 0;
                        while (n > 0) { s += n % 10; n /= 10; }
                        return s;
                    }
                    static boolean fits(int used, int free) {
                        int left = free - used;
                        return left >= 0 && left <= 512;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Warehouse.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        assertTrue(kof.contains("var v2 = if (arg0 >= 90.0) \"A\" else if (arg0 >= 80.0) \"B\" else \"C\""),
                "a cadeia armazenada:\n" + kof);
        assertTrue(kof.contains("toInt()"), "o cast d2i:\n" + kof);
        String kofRun = kof + """
                main() {
                    var stock = listOf(10, 20, 30)
                    var order = listOf(5, 15, 25)
                    println(Warehouse.pick(stock, order))
                    println(Warehouse.grade(95.0))
                    println(Warehouse.grade(85.5))
                    println(Warehouse.grade(70.0))
                    println(Warehouse.sumDigits(9876))
                    println(Warehouse.fits(100, 500))
                    println(Warehouse.fits(500, 100))
                    println(Warehouse.fits(1000, 100))
                }
                """;
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: 45 | A(95) | B(85) | C(70) | 30 | true | false | false
        assertEquals("45\nA(95)\nB(85)\nC(70)\n30\ntrue\nfalse\nfalse", output,
                "zero-drift: pick/grade/sumDigits/fits id\u00eanticos ao Java");
    }

    @Test
    void reportDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (15/10): o dom\u00ednio RELAT\u00d3RIO — 4 shapes sem c\u00f3digo
        // novo: a call cross-m\u00e9todo no total (total/headline), a
        // CADEIA-TERN\u00c1RIO armazenada com BRA\u00c7OS DE CALL VALUE
        // (headline: `t > 100 ? "high" : t > 50 ? "mid" : "low"` — a fatia
        // do grade GENERALIZA: o guard de regi\u00e3o + o armReachesJoin + o
        // armExpr comp\u00f5em de gra\u00e7a), o `||` com a call no 1\u00ba
        // termo (needsReview) e a cadeia com toUpperCase (badge).
        // Zero-drift executado: 130/T=130 [high]/false/true/GOLD:950/
        // SILVER:600/BRONZE:100 — 7/7 id\u00eanticos ao Java.
        Path javaFile = dir.resolve("Report.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Report {
                    static int total(List<Integer> xs) {
                        int t = 0;
                        for (int x : xs) { t += x; }
                        return t;
                    }
                    static String headline(List<Integer> xs) {
                        int t = total(xs);
                        String trend = t > 100 ? "high" : t > 50 ? "mid" : "low";
                        return "T=" + t + " [" + trend + "]";
                    }
                    static boolean needsReview(List<Integer> xs) {
                        return total(xs) == 0 || xs.isEmpty();
                    }
                    static String badge(int score) {
                        String b = score > 900 ? "gold" : score > 500 ? "silver" : "bronze";
                        return b.toUpperCase() + ":" + score;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Report.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    var xs = listOf(40, 60, 30)
                    println(Report.total(xs))
                    println(Report.headline(xs))
                    println(Report.needsReview(xs))
                    println(Report.needsReview(listOf()))
                    println(Report.badge(950))
                    println(Report.badge(600))
                    println(Report.badge(100))
                }
                """;
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: 130 | T=130 [high] | false | true | GOLD:950 |
        // SILVER:600 | BRONZE:100
        assertEquals("130\nT=130 [high]\nfalse\ntrue\nGOLD:950\nSILVER:600\nBRONZE:100", output,
                "zero-drift: total/headline/needsReview/badge id\u00eanticos ao Java");
    }

    @Test
    void nonLinearTryCatchRecovers(@TempDir Path dir) throws Exception {
        // Fase E (15/10): a FATIA pousada — o try/catch NÃO-LINEAR
        // (o if + o throw dentro do try, o valor armazenado, o catch
        // reatribuindo, o tail depois). O HANDLER-AWARE: o main-flow struct
        // sobre o MÉTODO INTEIRO (o bloco do handler fica sem preds no CFG e
        // nunca é andado; os braços convergem no join REAL) + o transform
        // (o out termina [.., "return X", "}"] — o return SAI do try e vira
        // o tail; o } do else fica DENTRO). O hoist `var v1 = ""` + os
        // guards: o valor do catch = ldc-String, a store do MESMO slot nos
        // DOIS lados. O ciclo stub-vira-código-real fechado na rodada
        // seguinte ao R6. Zero-drift executado: 42/7/-1/0/ok:5!/bad! — 6/6
        // idênticos ao Java; o withFinally (o finally com valor) segue o
        // stub honesto (a próxima fatia).
        Path javaFile = dir.resolve("Res.java");
        Files.writeString(javaFile, """
                public class Res {
                    static int parse(String s) {
                        try {
                            return Integer.parseInt(s.trim());
                        } catch (NumberFormatException e) {
                            return -1;
                        }
                    }
                    static String describe(int code) {
                        String base;
                        try {
                            if (code < 0) { throw new IllegalArgumentException("neg"); }
                            base = "ok:" + code;
                        } catch (IllegalArgumentException e) {
                            base = "bad";
                        }
                        return base + "!";
                    }
                    static int withFinally(boolean fail) {
                        int v = 0;
                        try {
                            v = 10;
                            if (fail) { throw new IllegalStateException("x"); }
                            v = 20;
                        } finally {
                            v = v + 1;
                        }
                        return v;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Res.class"));
        assertTrue(kof.contains("var v1 = \"\"") && kof.contains("} catch (String e) {"),
                "o try/catch não-linear:\n" + kof);
        assertTrue(kof.contains("throw \"neg\""), "o throw dentro do try:\n" + kof);
        assertTrue(kof.contains("} finally {") && kof.contains("v1 = (v1 + 1)"),
                "o finally com valor:\n" + kof);
        assertFalse(kof.contains("body not recovered"),
                "a classe INTEIRA recupera (o finally com valor incluso):\n" + kof);
        String kofRun = kof + """
                main() {
                    println(Res.parse("42"))
                    println(Res.parse(" 7 "))
                    println(Res.parse("abc"))
                    println(Res.parse("0"))
                    println(Res.describe(5))
                    println(Res.describe(-3))
                    println(Res.withFinally(false))
                }
                """;
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: 42 | 7 | -1 | 0 | ok:5! | bad!
        assertEquals("42\n7\n-1\n0\nok:5!\nbad!\n21", output,
                "zero-drift: parse/describe/withFinally id\u00eanticos ao Java");
    }

    @Test
    void inv2DomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (15/10): o domínio INVENTÁRIO-2 — 4 shapes sem código novo:
        // o filtro com && de startsWith/endsWith no for-each (countTags), o
        // switch de Int com 3 cases + default e RETURNS nos braços (pick —
        // o switch STATEMENT de return), o clamp de Double com os DOIS ifs
        // de reatribuição (clamp01) e o anyOver com startsWith-like no
        // limiar. Zero-drift executado: 2/ok/missing/error/?/0.5/0.0/1.0/
        // true/false — 10/10 idênticos ao Java.
        Path javaFile = dir.resolve("Inv2.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Inv2 {
                    static int countTags(List<String> rows) {
                        int n = 0;
                        for (String r : rows) {
                            if (r.startsWith("<") && r.endsWith(">")) { n++; }
                        }
                        return n;
                    }
                    static String pick(int code) {
                        switch (code) {
                            case 200: return "ok";
                            case 404: return "missing";
                            case 500: return "error";
                            default: return "?";
                        }
                    }
                    static double clamp01(double v) {
                        double r = v;
                        if (r < 0) { r = 0; }
                        if (r > 1) { r = 1; }
                        return r;
                    }
                    static boolean anyOver(List<Integer> xs, int lim) {
                        for (int x : xs) {
                            if (x > lim) { return true; }
                        }
                        return false;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Inv2.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    var rows = listOf("<a>", "text", "<b>")
                    println(Inv2.countTags(rows))
                    println(Inv2.pick(200))
                    println(Inv2.pick(404))
                    println(Inv2.pick(500))
                    println(Inv2.pick(302))
                    println(Inv2.clamp01(0.5))
                    println(Inv2.clamp01(-2.0))
                    println(Inv2.clamp01(3.0))
                    var xs = listOf(1, 5, 9)
                    println(Inv2.anyOver(xs, 7))
                    println(Inv2.anyOver(xs, 20))
                }
                """;
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: 2 | ok | missing | error | ? | 0.5 | 0.0 | 1.0 | true | false
        assertEquals("2\nok\nmissing\nerror\n?\n0.5\n0.0\n1.0\ntrue\nfalse", output,
                "zero-drift: countTags/pick/clamp01/anyOver id\u00eanticos ao Java");
    }

    @Test
    void sessDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (15/10): o domínio SESSÃO (Long/keys) — 5 shapes: o expired
        // (a compara\u00e7\u00e3o Long >=), o token (o concat armazenado com
        // a call hashCode em cima — o BUG de PRECED\u00caNCIA fechado: o
        // inline do local sem par\u00eanteses anexava o hashCode ao
        // \u00daLTIMO ARG (`arg1.hashCode()` onde o Java computa o hashCode
        // do CONCAT INTEIRO — pego pelo zero-drift: o token divergia);
        // o store agora guarda PARENTEZADO) + o Integer.toString(I) mapeado
        // p/ `"" + x` (o idioma do String.valueOf; o Kof Int TEM toString);
        // o remaining (o Long menos + o cast toInt), o isValidFormat (o
        // length == 8 && !contains) e o purge (o \u00edndice paralelo com
        // o get nas DUAS listas). Zero-drift executado: false/-1072647258/
        // 3/0/true/false/1 — 7/7 id\u00eanticos ao Java (o token EXATO).
        Path javaFile = dir.resolve("Sess.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Sess {
                    static boolean expired(long now, long until) { return now >= until; }
                    static String token(String user, long now) {
                        String raw = user + ":" + now;
                        return Integer.toString(raw.hashCode());
                    }
                    static int remaining(long now, long until) {
                        if (expired(now, until)) { return 0; }
                        long d = until - now;
                        return (int) (d / 1000);
                    }
                    static boolean isValidFormat(String tok) {
                        return tok.length() == 8 && !tok.contains(" ");
                    }
                    static List<String> purge(List<String> users, long now, List<Long> untils) {
                        List<String> keep = new java.util.ArrayList<>();
                        for (int i = 0; i < users.size(); i++) {
                            if (!expired(now, untils.get(i))) { keep.add(users.get(i)); }
                        }
                        return keep;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Sess.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        assertTrue(kof.contains("(arg0 + \":\" + arg1).hashCode()"),
                "o concat parentesado + o hashCode:\n" + kof);
        String kofRun = kof + """
                main() {
                    var us = listOf("ana", "bob")
                    var un = listOf(1000L, 5000L)
                    println(Sess.expired(2000L, 5000L))
                    println(Sess.token("ana", 7777L))
                    println(Sess.remaining(2000L, 5000L))
                    println(Sess.remaining(6000L, 5000L))
                    println(Sess.isValidFormat("abcd1234"))
                    println(Sess.isValidFormat("abc 1234"))
                    var k = Sess.purge(us, 2000L, un)
                    println(k.size())
                }
                """;
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: false | -1072647258 | 3 | 0 | true | false | 1
        assertEquals("false\n-1072647258\n3\n0\ntrue\nfalse\n1", output,
                "zero-drift: expired/token/remaining/isValidFormat/purge id\u00eanticos ao Java");
    }

    @Test
    void cfgDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (15/10): o dom\u00ednio CFG — 4 shapes sem c\u00f3digo novo: o
        // max de TR\u00caS vars com os DOIS ifs de reatribui\u00e7\u00e3o
        // encadeada (max — o padr\u00e3o running-max), o sign com os TR\u00caS
        // returns antecipados (sign), o absDiff com a negação condicional
        // (absDiff: `if (d < 0) return -d`) e a paridade com o MOD
        // comparado (sameParity: `(a % 2) == (b % 2)`). Zero-drift
        // executado: 9/9/9/+/-/0/7/7/true/false — 10/10 id\u00eanticos ao
        // Java.
        Path javaFile = dir.resolve("Cfg.java");
        Files.writeString(javaFile, """
                public class Cfg {
                    static int max(int a, int b, int c) {
                        int m = a;
                        if (b > m) { m = b; }
                        if (c > m) { m = c; }
                        return m;
                    }
                    static String sign(int n) {
                        if (n > 0) { return "+"; }
                        if (n < 0) { return "-"; }
                        return "0";
                    }
                    static int absDiff(int a, int b) {
                        int d = a - b;
                        if (d < 0) { return -d; }
                        return d;
                    }
                    static boolean sameParity(int a, int b) {
                        return (a % 2) == (b % 2);
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Cfg.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Cfg.max(3, 9, 5))
                    println(Cfg.max(9, 3, 5))
                    println(Cfg.max(3, 5, 9))
                    println(Cfg.sign(42))
                    println(Cfg.sign(-7))
                    println(Cfg.sign(0))
                    println(Cfg.absDiff(10, 3))
                    println(Cfg.absDiff(3, 10))
                    println(Cfg.sameParity(4, 8))
                    println(Cfg.sameParity(4, 7))
                }
                """;
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: 9 | 9 | 9 | + | - | 0 | 7 | 7 | true | false
        assertEquals("9\n9\n9\n+\n-\n0\n7\n7\ntrue\nfalse", output,
                "zero-drift: max/sign/absDiff/sameParity id\u00eanticos ao Java");
    }

    @Test
    void fleetDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (15/10): o domínio FROTA — 4 shapes sem código novo: o
        // somatório simples (totalKm), a busca do MÍNIMO por listas
        // PARALELAS com o get(i) no índice (cheapest — o arg-min em DUAS
        // listas), o check com a negação do intervalo (needsService: `since
        // > 10000 || since < 0`) e o avg com o guard vazio + a call
        // cross-método (avg — a 14\u00aa validação zero-código). Zero-drift
        // executado: 67000/car2/false/true/22333.333333333332/0.0 — 6/6
        // id\u00eanticos ao Java.
        Path javaFile = dir.resolve("Fleet.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Fleet {
                    static int totalKm(List<Integer> kms) {
                        int t = 0;
                        for (int k : kms) { t += k; }
                        return t;
                    }
                    static String cheapest(List<String> names, List<Integer> prices) {
                        int best = 0;
                        for (int i = 1; i < prices.size(); i++) {
                            if (prices.get(i) < prices.get(best)) { best = i; }
                        }
                        return names.get(best);
                    }
                    static boolean needsService(int km, int lastService) {
                        int since = km - lastService;
                        return since > 10000 || since < 0;
                    }
                    static double avg(List<Integer> kms) {
                        if (kms.isEmpty()) { return 0.0; }
                        return (double) totalKm(kms) / kms.size();
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Fleet.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    var km = listOf(12000, 30000, 25000)
                    var nm = listOf("car1", "car2", "car3")
                    println(Fleet.totalKm(km))
                    println(Fleet.cheapest(nm, listOf(50000, 30000, 40000)))
                    println(Fleet.needsService(12000, 5000))
                    println(Fleet.needsService(12000, 20000))
                    println(Fleet.avg(km))
                    println(Fleet.avg(listOf()))
                }
                """;
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: 67000 | car2 | false | true | 22333.333333333332 | 0.0
        assertEquals("67000\ncar2\nfalse\ntrue\n22333.333333333332\n0.0", output,
                "zero-drift: totalKm/cheapest/needsService/avg id\u00eanticos ao Java");
    }

    @Test
    void grade2DomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (16/10): o domínio NOTAS-2 — 4 shapes sem código novo: a
        // média PONDERADA com as constantes Double (weighted: a*0.6 + b*0.4
        // — a 15\u00aa valida\u00e7\u00e3o zero-c\u00f3digo), a cadeia de
        // conceitos com os TR\u00caS ifs de retorno (concept), o boolean
        // composto chamando outro m\u00e9todo (approved: `weighted(a, b)
        // >= 5`) e o ARREDONDAMENTO para dezenas com o cast Int e a
        // reatribui\u00e7\u00e3o (round10: `(int)(v/10) * 10`). Zero-drift
        // executado: 7.2/A/B/C/F/true/false/70 — 8/8 id\u00eanticos ao Java.
        Path javaFile = dir.resolve("Grade2.java");
        Files.writeString(javaFile, """
                public class Grade2 {
                    static double weighted(double a, double b) {
                        return a * 0.6 + b * 0.4;
                    }
                    static String concept(double avg) {
                        if (avg >= 9) { return "A"; }
                        if (avg >= 7) { return "B"; }
                        if (avg >= 5) { return "C"; }
                        return "F";
                    }
                    static boolean approved(double a, double b) {
                        return weighted(a, b) >= 5;
                    }
                    static int round10(double v) {
                        int r = (int) (v / 10);
                        return r * 10;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Grade2.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Grade2.weighted(8.0, 6.0))
                    println(Grade2.concept(9.5))
                    println(Grade2.concept(7.2))
                    println(Grade2.concept(5.8))
                    println(Grade2.concept(4.0))
                    println(Grade2.approved(8.0, 6.0))
                    println(Grade2.approved(4.0, 5.0))
                    println(Grade2.round10(75.0))
                }
                """;
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: 7.2 | A | B | C | F | true | false | 70
        assertEquals("7.2\nA\nB\nC\nF\ntrue\nfalse\n70", output,
                "zero-drift: weighted/concept/approved/round10 id\u00eanticos ao Java");
    }

    @Test
    void medDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (16/10): o dom\u00ednio M\u00c9DICO — 4 shapes sem c\u00f3digo
        // novo: o \u00cdNDICE com a matem\u00e1tica intermedi\u00e1ria (imc:
        // peso / (altura * altura) — a 16\u00aa valida\u00e7\u00e3o
        // zero-c\u00f3digo), as faixas com os limiares encadeados de Double
        // (faixa), a dose com o CAST Int de Double (`(int)(peso * 2)`) e o
        // al\u00e9rgico com o contains de substring (alergico). Zero-drift
        // executado: 22.857142857142858/abaixo/normal/sobrepeso/obeso/131/
        // true/false — 8/8 id\u00eanticos ao Java.
        Path javaFile = dir.resolve("Med.java");
        Files.writeString(javaFile, """
                public class Med {
                    static double imc(double peso, double altura) {
                        return peso / (altura * altura);
                    }
                    static String faixa(double imc) {
                        if (imc < 18.5) { return "abaixo"; }
                        if (imc < 25) { return "normal"; }
                        if (imc < 30) { return "sobrepeso"; }
                        return "obeso";
                    }
                    static int doseMg(double peso) {
                        return (int) (peso * 2);
                    }
                    static boolean alergico(String subst, String lista) {
                        return lista.contains(subst);
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Med.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Med.imc(70.0, 1.75))
                    println(Med.faixa(17.0))
                    println(Med.faixa(22.0))
                    println(Med.faixa(27.0))
                    println(Med.faixa(32.0))
                    println(Med.doseMg(65.5))
                    println(Med.alergico("penicilina", "aspirina,penicilina,ibuprofeno"))
                    println(Med.alergico("codeina", "aspirina,penicilina"))
                }
                """;
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: 22.857142857142858 | abaixo | normal | sobrepeso | obeso | 131 | true | false
        assertEquals("22.857142857142858\nabaixo\nnormal\nsobrepeso\nobeso\n131\ntrue\nfalse", output,
                "zero-drift: imc/faixa/doseMg/alergico id\u00eanticos ao Java");
    }

    @Test
    void calendarDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (16/10): o dom\u00ednio CALEND\u00c1RIO — 4 shapes sem
        // c\u00f3digo novo: o ANO BISSEXTO com o &&/|| MISTO e o m\u00f3dulo
        // (isLeap: `(y % 4 == 0 && y % 100 != 0) || y % 400 == 0`), o
        // diasNoM\u00eas com os ifs encadeados e o OR de igualdades
        // (daysInMonth: `m == 4 || m == 6 || m == 9 || m == 11`), o
        // dayType com o m\u00f3dulo no || (dayType: `d % 7 == 0 || d % 7 ==
        // 6`) e o nextMonth com o wrap do m\u00f3dulo (`m % 12 + 1`).
        // Zero-drift executado: true/false/true/false/29/28/30/31/weekend/
        // weekday/1/4 — 12/12 id\u00eanticos ao Java.
        Path javaFile = dir.resolve("Sched2.java");
        Files.writeString(javaFile, """
                public class Sched2 {
                    static boolean isLeap(int year) {
                        return (year % 4 == 0 && year % 100 != 0) || year % 400 == 0;
                    }
                    static int daysInMonth(int month, int year) {
                        if (month == 2 && isLeap(year)) { return 29; }
                        if (month == 2) { return 28; }
                        if (month == 4 || month == 6 || month == 9 || month == 11) { return 30; }
                        return 31;
                    }
                    static String dayType(int day) {
                        if (day % 7 == 0 || day % 7 == 6) { return "weekend"; }
                        return "weekday";
                    }
                    static int nextMonth(int month) {
                        return month % 12 + 1;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Sched2.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Sched2.isLeap(2024))
                    println(Sched2.isLeap(2023))
                    println(Sched2.isLeap(2000))
                    println(Sched2.isLeap(1900))
                    println(Sched2.daysInMonth(2, 2024))
                    println(Sched2.daysInMonth(2, 2023))
                    println(Sched2.daysInMonth(4, 2024))
                    println(Sched2.daysInMonth(7, 2024))
                    println(Sched2.dayType(7))
                    println(Sched2.dayType(8))
                    println(Sched2.nextMonth(12))
                    println(Sched2.nextMonth(3))
                }
                """;
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: true | false | true | false | 29 | 28 | 30 | 31 |
        // weekend | weekday | 1 | 4
        assertEquals("true\nfalse\ntrue\nfalse\n29\n28\n30\n31\nweekend\nweekday\n1\n4", output,
                "zero-drift: isLeap/daysInMonth/dayType/nextMonth id\u00eanticos ao Java");
    }

    @Test
    void queueDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (16/10): o dom\u00ednio FILA — 4 shapes sem c\u00f3digo novo: o
        // contador com o threshold no for-each (serve: `t > start`),
        // os TR\u00caS returns com os limiares encadeados de Int (status),
        // a multiplicação direta de params (waitMinutes) e a diferença com
        // o threshold no `||` implícito do return (skip: `myTicket - now >
        // 3`). Zero-drift executado: 3/done/soon/waiting/60/true/false —
        // 7/7 id\u00eanticos ao Java — a 18\u00aa valida\u00e7\u00e3o
        // zero-c\u00f3digo.
        Path javaFile = dir.resolve("Queue.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Queue {
                    static int serve(List<Integer> tickets, int start) {
                        int served = 0;
                        for (int t : tickets) {
                            if (t > start) { served++; }
                        }
                        return served;
                    }
                    static String status(int position) {
                        if (position <= 0) { return "done"; }
                        if (position < 5) { return "soon"; }
                        return "waiting";
                    }
                    static int waitMinutes(int position, int perClient) {
                        return position * perClient;
                    }
                    static boolean skip(int myTicket, int now) {
                        return myTicket - now > 3;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Queue.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    var ts = listOf(3, 7, 12, 5)
                    println(Queue.serve(ts, 4))
                    println(Queue.status(0))
                    println(Queue.status(3))
                    println(Queue.status(9))
                    println(Queue.waitMinutes(4, 15))
                    println(Queue.skip(20, 5))
                    println(Queue.skip(6, 5))
                }
                """;
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: 3 | done | soon | waiting | 60 | true | false
        assertEquals("3\ndone\nsoon\nwaiting\n60\ntrue\nfalse", output,
                "zero-drift: serve/status/waitMinutes/skip id\u00eanticos ao Java");
    }

    @Test
    void stockDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (16/10): o dom\u00ednio ESTOQUE — 4 shapes sem c\u00f3digo novo:
        // o contador de BELOW-THRESHOLD no for-each (lowStock: `q < min`), os
        // TR\u00caS returns com o ==/comparação (level: out/low/ok), o
        // ACUMULADOR de déficit com o alvo menos o atual (restock: `added +=
        // target - q`) e o ALL-POSITIVE invertido (hasAll: o return false
        // antecipado + true). Zero-drift executado: 2/out/low/ok/27/true/
        // false — 7/7 id\u00eanticos ao Java — a 19\u00aa valida\u00e7\u00e3o
        // zero-c\u00f3digo.
        Path javaFile = dir.resolve("Stock.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Stock {
                    static int lowStock(List<Integer> qtys, int min) {
                        int n = 0;
                        for (int q : qtys) {
                            if (q < min) { n++; }
                        }
                        return n;
                    }
                    static String level(int qty) {
                        if (qty == 0) { return "out"; }
                        if (qty < 10) { return "low"; }
                        return "ok";
                    }
                    static int restock(List<Integer> qtys, int min, int target) {
                        int added = 0;
                        for (int q : qtys) {
                            if (q < min) { added += target - q; }
                        }
                        return added;
                    }
                    static boolean hasAll(List<Integer> qtys, int min) {
                        for (int q : qtys) {
                            if (q < min) { return false; }
                        }
                        return true;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Stock.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    var qs = listOf(20, 5, 40, 8)
                    println(Stock.lowStock(qs, 10))
                    println(Stock.level(0))
                    println(Stock.level(7))
                    println(Stock.level(50))
                    println(Stock.restock(qs, 10, 20))
                    println(Stock.hasAll(qs, 5))
                    println(Stock.hasAll(qs, 10))
                }
                """;
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: 2 | out | low | ok | 27 | true | false
        assertEquals("2\nout\nlow\nok\n27\ntrue\nfalse", output,
                "zero-drift: lowStock/level/restock/hasAll id\u00eanticos ao Java");
    }

    private Path writeKof(Path dir, String kof) throws Exception {
        Path f = dir.resolve("Arr.kf");
        Files.writeString(f, kof);
        return f;
    }


    @Test
    void stringCastRecoversNotStubbed(@TempDir Path dir) throws Exception {
        // Fase E (fatia cast-String, 14/10): checkcast com alvo String era
        // recusado ("redundante") — medido que `as String` é FIEL (o cast
        // errado lança a CCE): os métodos com cast recuperam agora.
        Path javaFile = dir.resolve("Ca.java");
        Files.writeString(javaFile, """
                public class Ca {
                    String cast(Object o) { return (String) o; }
                    int len(Object o) { String s = (String) o; return s.length(); }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Ca.class"));
        assertFalse(kof.contains("body not recovered"),
                "cast String deve recuperar (nao stub):\n" + kof);
        assertTrue(kof.contains("as String"), "checkcast → as String:\n" + kof);
        // zero-drift executado
        String kofRun = kof + "\nmain() {\n    var ca = Ca()\n    println(ca.cast(\"ok\"))\n    println(ca.len(\"abcd\"))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("ok\n4", output, "zero-drift: cast + len como no Java");
    }



    @Test
    void decompileTreeRefusesAmbiguousSimpleNames(@TempDir Path dir) throws Exception {
        // Recusa R6: simples duplicado em 2+ pacotes do índice nunca ganha
        // import (ambíguo) → stub honesto, mesmo com o pacote atual tendo um.
        Path src = dir.resolve("classes");
        for (String pkg : new String[]{"p", "q", "r"}) Files.createDirectories(src.resolve(pkg));
        Path dp = src.resolve("p/Dup.java");
        Files.writeString(dp, "package p;\npublic class Dup { public int v; }\n");
        Path dq = src.resolve("q/Dup.java");
        Files.writeString(dq, "package q;\npublic class Dup { public int w; }\n");
        Path c = src.resolve("r/C.java");
        Files.writeString(c, """
                package r;
                import p.Dup;
                public class C {
                    public static boolean isD(Object o) { boolean x = o instanceof Dup; return x; }
                }
                """);
        runJavac(java.util.List.of(dp, dq, c), src);
        Path out = dir.resolve("gen");
        assertEquals(0, Decompile.decompileTree(src, out));
        String cSrc = Files.readString(out.resolve("r/C.kf"));
        assertTrue(cSrc.contains("body not recovered") && !cSrc.contains("instanceof Dup"),
                "simples ambíguo (p.Dup + q.Dup) fica stub, sem import:\n" + cSrc);
        assertTrue(!cSrc.contains("import p.Dup"),
                "nenhum import ambíguo deve ser emitido:\n" + cSrc);
    }

    @Test
    void decompileTreeEmitsImportsForSignatureTypes(@TempDir Path dir) throws Exception {
        // §7 degrau 4: tipos de ASSINATURA (field, ctor-param, param, return)
        // cross-package registram import (emissão de nomes inalterada).
        Path src = dir.resolve("classes");
        Path p = src.resolve("p");
        Path q = src.resolve("q");
        Files.createDirectories(p);
        Files.createDirectories(q);
        Path b = p.resolve("B.java");
        Files.writeString(b, """
                package p;
                public class B { public int v; public B(int v) { this.v = v; } }
                """);
        Path c = q.resolve("C.java");
        Files.writeString(c, """
                package q;
                import p.B;
                public class C {
                    public B held;
                    public C(B b) { this.held = b; }
                    public static int take(B b) { return b.v; }
                    public static B make() { return new B(3); }
                }
                """);
        runJavac(java.util.List.of(b, c), src);
        Path out = dir.resolve("gen");
        assertEquals(0, Decompile.decompileTree(src, out));
        String cSrc = Files.readString(out.resolve("q/C.kf"));
        assertTrue(cSrc.contains("import p.B"),
                "tipos de assinatura cross-package devem gerar import:\n" + cSrc);
        assertTrue(cSrc.contains("B held") && cSrc.contains("take(B arg0")
                && cSrc.contains("B make()"),
                "field/param/return devem sair com o simples:\n" + cSrc);
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                out.resolve("p/B.kf"), out.resolve("q/C.kf")), dir.resolve("o"), Target.JVM, out);
        assertTrue(r.success(), "árvore com import deve compilar (zero drift):\n" + cSrc + "\n" + r.diagnostics().getDiagnostics());
    }

    @Test
    void recoversNewInStatementBody(@TempDir Path dir) throws Exception {
        // Fase E: `new` em corpo multi-statement (`var n = new N(x); ...`)
        // não existia no emitLinear (só no path linear) → stub certo.
        // Mirror 0xbb/0x59/0xb7; JDK recusa (R6); cross-package registra import.
        Path src = dir.resolve("classes");
        Path p = src.resolve("p");
        Path q = src.resolve("q");
        Files.createDirectories(p);
        Files.createDirectories(q);
        Path n = p.resolve("N.java");
        Files.writeString(n, """
                package p;
                public class N {
                    public int v;
                    public N(int v) { this.v = v; }
                    public static int build(int x) {
                        N n = new N(x);
                        int v = n.v;
                        return v;
                    }
                }
                """);
        Path m = q.resolve("M.java");
        Files.writeString(m, """
                package q;
                import p.N;
                public class M {
                    public static int build(int x) {
                        N n = new N(x);
                        int v = n.v;
                        return v;
                    }
                }
                """);
        runJavac(java.util.List.of(n, m), src);
        Path out = dir.resolve("gen");
        assertEquals(0, Decompile.decompileTree(src, out));
        String nSrc = Files.readString(out.resolve("p/N.kf"));
        assertTrue(nSrc.contains("var v1 = N(arg0)") || nSrc.contains("= N(arg0)"),
                "`new` same-package em statement deve recuperar:\n" + nSrc);
        String mSrc = Files.readString(out.resolve("q/M.kf"));
        assertTrue(mSrc.contains("import p.N"),
                "`new` cross-package deve gerar import:\n" + mSrc);
        assertTrue(mSrc.contains("N(arg0)"),
                "`new` cross-package em statement deve recuperar:\n" + mSrc);
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                out.resolve("p/N.kf"), out.resolve("q/M.kf")), dir.resolve("o"), Target.JVM, out);
        assertTrue(r.success(), "árvore com new cross-package deve compilar:\n" + mSrc + "\n" + r.diagnostics().getDiagnostics());
    }

    @Test
    void recoversArrayCreateAndAccess(@TempDir Path dir) throws Exception {
        // Fase E arrays: `anewarray` + `aaload`/`aastore`/`arraylength`.
        // Nenhum existia nos decoders → stub certo. Elemento ESTRITO
        // (String/Object/domínio; wrappers como Integer[] recusam — `new
        // Int[n]` é int[], semântica distinta). `arr[i]`/`arr[i] = v`/
        // `arr.length` são os idioms Kof (training + probes JVM/script).
        Path src = dir.resolve("classes");
        Path p = src.resolve("p");
        Files.createDirectories(p);
        Path a = p.resolve("A.java");
        Files.writeString(a, """
                package p;
                public class A {
                    public static String first(int n) {
                        String[] arr = new String[n];
                        arr[0] = "hi";
                        String s = arr[0];
                        return s;
                    }
                    public static int len(String[] a) {
                        return a.length;
                    }
                }
                """);
        runJavac(java.util.List.of(a), src);
        String kof = Decompile.decompile(src.resolve("p/A.class"));
        assertTrue(kof.contains("new String[arg0]"),
                "anewarray deve recuperar `new String[n]`:\n" + kof);
        assertTrue(kof.contains("v1[0] = \"hi\"") && kof.contains("v1[0]"),
                "aastore/aaload devem recuperar `a[i] = v` / `a[i]`:\n" + kof);
        assertTrue(kof.contains("arg0.length"),
                "arraylength deve recuperar `a.length`:\n" + kof);
        Path out = dir.resolve("gen");
        Files.createDirectories(out.resolve("p"));
        Files.writeString(out.resolve("p/A.kf"), kof);
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                out.resolve("p/A.kf")), dir.resolve("o"), Target.JVM, out);
        assertTrue(r.success(), "array decompilado deve compilar (zero drift):\n" + kof + "\n" + r.diagnostics().getDiagnostics());
    }

    @Test
    void recoversStatementSwitchAndRunsIt(@TempDir Path dir) throws Exception {
        // Fase C: switch-statement (cases com side-effect + break). Prova
        // FORTE: não basta compilar — executa os 3 caminhos (braço errado
        // silencioso é a pior falha). `break` sai do switch (probe) e o
        // epílogo vai após `}` (dentro executaria o próximo braço).
        Path src = dir.resolve("classes");
        Files.createDirectories(src);
        Path s = src.resolve("S.java");
        Files.writeString(s, """
                public class S {
                    public static String grade(int v) {
                        String r;
                        switch (v) {
                            case 1: r = "one"; break;
                            case 2: r = "two"; break;
                            default: r = "other"; break;
                        }
                        return r;
                    }
                    public static void main(String[] a) {
                        System.out.println(grade(1));
                        System.out.println(grade(2));
                        System.out.println(grade(9));
                    }
                }
                """);
        runJavac(java.util.List.of(s), src);
        String kof = Decompile.decompile(src.resolve("S.class"));
        assertTrue(kof.contains("switch (arg0) {"), "switch deve recuperar:\n" + kof);
        assertTrue(kof.contains("case 1:") && kof.contains("break"),
                "cases com break explícito:\n" + kof);
        Path out = dir.resolve("gen");
        Files.createDirectories(out);
        Path kf = out.resolve("S.kf");
        Files.writeString(kf, kof);
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(kf),
                dir.resolve("o"), Target.JVM, out);
        assertTrue(r.success(), "switch decompilado deve compilar:\n" + kof + "\n" + r.diagnostics().getDiagnostics());
        // Entry = a classe decompilada (main estático dentro de `class S`),
        // não `Default.Main` (que só existe p/ programa Kof de main top-level).
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", dir.resolve("o").toString(), "S");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String o = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "run: " + o);
        assertEquals("one\ntwo\nother", o, "os 3 caminhos devem executar certo:\n" + kof);
    }

    @Test
    void recoversIfThenJoinAndRunsIt(@TempDir Path dir) throws Exception {
        // Fase C (join de if-sem-else): `if (x > 5) { r = r + x }` seguida de
        // sequela — o braço then cai no join (preds {b, then}). O walker atual
        // PARA o braço no join e o struct recusava re-entrar (linha 206),
        // stubando o método INTEIRO. Prova FORTE: executa os 2 caminhos
        // (oracle JVM medido: g(6)=107, g(1)=101) — sem o else o fluxo do
        // braço falso é justamente o que um join errado quebraria.
        Path src = dir.resolve("classes");
        Files.createDirectories(src);
        Path s = src.resolve("G.java");
        Files.writeString(s, """
                public class G {
                    public static int g(int x) {
                        int r = 100;
                        if (x > 5) { r = r + x; }
                        r = r + 1;
                        return r;
                    }
                    public static void main(String[] a) {
                        System.out.println(g(6));
                        System.out.println(g(1));
                    }
                }
                """);
        runJavac(java.util.List.of(s), src);
        String kof = Decompile.decompile(src.resolve("G.class"));
        assertTrue(kof.contains("if (arg0 > 5) {"), "if-sem-else deve recuperar:\n" + kof);
        assertFalse(kof.contains("} else {"), "sem else (join estruturado):\n" + kof);
        Path out = dir.resolve("gen");
        Files.createDirectories(out);
        Path kf = out.resolve("G.kf");
        Files.writeString(kf, kof);
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(kf),
                dir.resolve("o"), Target.JVM, out);
        assertTrue(r.success(), "if-sem-else decompilado deve compilar:\n" + kof + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", dir.resolve("o").toString(), "G");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String o = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "run: " + o);
        assertEquals("107\n101", o, "os 2 caminhos do join devem executar certo:\n" + kof);
    }

    @Test
    void hoistsEscapingLocalOutOfIfElseBranchesAndRunsIt(@TempDir Path dir) throws Exception {
        // §238 (Fase C degrau 2c): um local cuja PRIMEIRA escrita fica dentro
        // de um ramo do if-else e e lida DEPOIS do join saia com `var` escopado
        // dentro do if -> o pos-join referenciava um nome nao-declarado e a
        // saida NAO recompilava (SEM000, anti-R6). O hoist icar a declaracao
        // (default por tipo da store: Int->0) ANTES do if. Prova FORTE:
        // recompila E executa os 3 caminhos (oracle JVM medido 10 21 12).
        Path src = dir.resolve("classes");
        Files.createDirectories(src);
        Path s = src.resolve("Hl.java");
        Files.writeString(s, """
                public class Hl {
                    public static int both(int n) {
                        int m = n % 2; int s;
                        if (m == 0) { s = 10; } else { s = 20; }
                        return s + n;
                    }
                }
                """);
        runJavac(java.util.List.of(s), src);
        String kof = Decompile.decompile(src.resolve("Hl.class"));

        assertTrue(kof.contains("var v2 = 0") && kof.contains("if (v1 == 0) {"),
                "declaracao do local escapante deve ser içada p/ antes do if:\n" + kof);
        assertTrue(kof.indexOf("var v2 = 0") < kof.indexOf("if (v1 == 0)"),
                "o var do local vem ANTES do if, nao dentro do ramo:\n" + kof);
        assertFalse(kof.contains("throw \"body not recovered\""), "nao deve stubar:\n" + kof);

        Path out = dir.resolve("gen");
        Files.createDirectories(out);
        Path kf = out.resolve("Hl.kf");
        Files.writeString(kf, kof);
        Path mainKf = out.resolve("Main.kf");
        Files.writeString(mainKf, "main() {\n    println(Hl.both(0))\n    println(Hl.both(1))"
                + "\n    println(Hl.both(2))\n}\n");
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(kf, mainKf),
                dir.resolve("o"), Target.JVM, out);
        assertTrue(r.success(), "içado decompilado deve COMPILAR (era o bug):\n" + kof
                + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String o = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "run: " + o);
        assertEquals("10\n21\n12", o, "os 3 caminhos do join içado:\n" + kof);
    }

    @Test
    void sipushConstantInTestIsRecoveredAndRuns(@TempDir Path dir) throws Exception {
        // §238-face (sipush no loadValue): `if (a == 30000)` usa sipush (const
        // fora do range de bipush). O loadValue nao tratava 0x11 e o teste
        // computado ficava null = stub, APEMBAR que o machineRun ja dobrava
        // (divergencia de passada). Seguranca: so icar o local escapante com o
        // hoist §238 no lugar (sem ele viraria saida nao-compilavel). Prova:
        // decompila `if (arg0 == 30000)` COM o var içado + recompila + roda os
        // 3 caminhos (oracle JVM medido 1/2/1).
        Path src = dir.resolve("classes");
        Files.createDirectories(src);
        Path s = src.resolve("Sp.java");
        Files.writeString(s, """
                public class Sp {
                    public static int big(int a) {
                        int r; if (a == 30000) { r = 1; } else { r = 2; } return r;
                    }
                }
                """);
        runJavac(java.util.List.of(s), src);
        String kof = Decompile.decompile(src.resolve("Sp.class"));

        assertTrue(kof.contains("if (arg0 == 30000)"), "sipush deve dobrar no teste:\n" + kof);
        assertFalse(kof.contains("throw \"body not recovered\""), "nao deve stubar (era o gap):\n" + kof);

        Path out = dir.resolve("gen");
        Files.createDirectories(out);
        Path kf = out.resolve("Sp.kf");
        Files.writeString(kf, kof);
        Path mainKf = out.resolve("Main.kf");
        Files.writeString(mainKf, "main() {\n    println(Sp.big(30000))\n    println(Sp.big(1))\n"
                + "    println(Sp.big(-30000))\n}\n");
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(kf, mainKf),
                dir.resolve("o"), Target.JVM, out);
        assertTrue(r.success(), "sipush içado deve COMPILAR:\n" + kof + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String o = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "run: " + o);
        assertEquals("1\n2\n2", o, "3 caminhos do teste sipush:\n" + kof);
    }

    @Test
    void refLocalEscapingRecoversValuePropagated(@TempDir Path dir) throws Exception {
        // §238 (13/10, evoluído): o local de REFERÊNCIA escrito nos 2 ramos e
        // lido depois AGORA RECUPERA — o valor propagado declara o `var` UMA
        // vez FORA do if/else (`var v2 = if (cond) "a" else "b"`), exatamente
        // o que a face antiga exigia (sem `var` escopado no ramo = o
        // não-compilável do bug §238). Antes: stub honesto (sem default
        // seguro a içar). Zero-drift executado.
        Path src = dir.resolve("classes");
        Files.createDirectories(src);
        Path s = src.resolve("Hr.java");
        Files.writeString(s, """
                public class Hr {
                    public static String ref(int n) {
                        int m = n % 2; String s;
                        if (m == 0) { s = "a"; } else { s = "b"; }
                        return s;
                    }
                }
                """);
        runJavac(java.util.List.of(s), src);
        String kof = Decompile.decompile(src.resolve("Hr.class"));
        assertFalse(kof.contains("body not recovered"),
                "o local de referencia escapante recupera (valor propagado):\n" + kof);
        assertTrue(kof.contains("if ("), kof);
        // o `var` é declarado FORA do ramo (o contrato do §238)
        assertTrue(kof.indexOf("var ") < kof.indexOf("if ("),
                "o var do local escapante fica FORA do if/else:\n" + kof);
        String kofRun = kof + "\nmain() {\n    println(Hr.ref(0))\n    println(Hr.ref(1))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("a\nb", output, "zero-drift executado");
    }
    @Test
    void recoversNullNarrowAndRunsIt(@TempDir Path dir) throws Exception {
        // Fase C: ifnull/ifnonnull (0xc6/0xc7) — narrowing CANONICO da
        // linguagem (idiom Null safety; ROI medido: 308 testes sobre load
        // puro no corpus). len vira if-expression ternaria (linear path),
        // nul vira if-sem-else com join (degrau 1). Prova por EXECUCAO dos
        // 4 caminhos (oracle JVM medido 3 0 5 9).
        Path src = dir.resolve("classes");
        Files.createDirectories(src);
        Path s = src.resolve("Nl.java");
        Files.writeString(s, """
                public class Nl {
                    public static int len(String x) { if (x != null) { return x.length(); } return 0; }
                    public static int nul(String x) { int r = 5; if (x == null) { r = 9; } return r; }
                    public static void main(String[] a) { }
                }
                """);
        runJavac(java.util.List.of(s), src);
        String kof = Decompile.decompile(src.resolve("Nl.class"));
        assertTrue(kof.contains("if (arg0 != null)"), "ifnonnull vira `!= null`:\n" + kof);
        assertTrue(kof.contains("if (arg0 == null) {"), "ifnull vira `== null`:\n" + kof);
        assertFalse(kof.contains("body not recovered"), "ambos recuperados:\n" + kof);
        Path out = dir.resolve("gen");
        Files.createDirectories(out);
        Path kf = out.resolve("Nl.kf");
        Files.writeString(kf, kof);
        Path mainKf = out.resolve("Main.kf");
        Files.writeString(mainKf, "main() {\n    println(Nl.len(\"abc\"))\n    println(Nl.len(null))\n    println(Nl.nul(\"x\"))\n    println(Nl.nul(null))\n}\n");
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(kf, mainKf),
                dir.resolve("o"), Target.JVM, out);
        assertTrue(r.success(), "narrowing decompilado deve compilar:\n" + kof + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String o = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "run: " + o);
        assertEquals("3\n0\n5\n9", o, "os 4 caminhos do narrow:\n" + kof);
    }

    @Test
    void recoversContinueAsEmptyThenJoinAndRunsIt(@TempDir Path dir) throws Exception {
        // Fase C degrau 2a: `for` com `continue` vira while + if de condicao
        // INVERTIDA com then VAZIO (o continue pula o corpo; o incremento
        // fica na sequela do join — preservado nos DOIS caminhos). Medido
        // por execucao 13/09: contFor(0..6) = 0 0 1 3 3 7 12 == oracle JVM.
        // (A variante `i % 2 == 0` fica em stub honesto — blockCondition nao
        // recupera test-expr com calculo; diamondJoinShapesStayHonestStub.)
        Path src = dir.resolve("classes");
        Files.createDirectories(src);
        Path s = src.resolve("Jn.java");
        Files.writeString(s, """
                public class Jn {
                    public static int contFor(int n) {
                        int s = 0;
                        for (int i = 0; i < n; i++) { if (i == 3) continue; s = s + i; }
                        return s;
                    }
                    public static void main(String... args) { }
                }
                """.replace("String... args", "String[] a"));
        runJavac(java.util.List.of(s), src);
        String kof = Decompile.decompile(src.resolve("Jn.class"));
        assertTrue(kof.contains("while (") , "for vira while:\n" + kof);
        assertFalse(kof.contains("throw \"body not recovered\""), "corpo recuperado:\n" + kof);
        Path out = dir.resolve("gen");
        Files.createDirectories(out);
        Path kf = out.resolve("Jn.kf");
        Files.writeString(kf, kof);
        Path mainKf = out.resolve("Main.kf");
        StringBuilder calls = new StringBuilder("main() {\n");
        for (int n = 0; n < 7; n++) calls.append("    println(Jn.contFor(").append(n).append("))\n");
        calls.append("}\n");
        Files.writeString(mainKf, calls.toString());
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(kf, mainKf),
                dir.resolve("o"), Target.JVM, out);
        assertTrue(r.success(), "decompilado deve compilar:\n" + kof + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String o = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "run: " + o);
        assertEquals("0\n0\n1\n3\n3\n7\n12", o, "continue==then vazio, incremento no join:\n" + kof);
    }

    @Test
    void recoversIfElseWithTailJoinAndRunsIt(@TempDir Path dir) throws Exception {
        // Fase C degrau 2a: if-else LINEAR com sequela pos-if (join P com
        // preds exatos {then,else}). Hoje o naive path anda o then p/ dentro
        // do join e a recusa 214 stuba o metodo INTEIRO (E.class stubou
        // honesto na medicao 13/09 — nunca codigo errado). Prova FORTE:
        // executa os 2 caminhos (oracle JVM medido 13/14) — o caminho do
        // else e justamente o que um join duplicado/perdido quebraria.
        Path src = dir.resolve("classes");
        Files.createDirectories(src);
        Path s = src.resolve("E.java");
        Files.writeString(s, """
                public class E {
                    public static int e(int a) {
                        int r = 1;
                        if (a > 0) { r = r + 2; } else { r = r + 3; }
                        return r + 10;
                    }
                    public static void main(String[] a) { }
                }
                """.replace("String[]", "String[]"));
        runJavac(java.util.List.of(s), src);
        String kof = Decompile.decompile(src.resolve("E.class"));
        assertTrue(kof.contains("} else {"), "if-else deve recuperar:\n" + kof);
        Path out = dir.resolve("gen");
        Files.createDirectories(out);
        Path kf = out.resolve("E.kf");
        Files.writeString(kf, kof);
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(kf),
                dir.resolve("o"), Target.JVM, out);
        assertTrue(r.success(), "if-else decompilado deve compilar:\n" + kof + "\n" + r.diagnostics().getDiagnostics());
        Path mainKf = out.resolve("Main.kf");
        Files.writeString(mainKf, "main() {\n    println(E.e(1))\n    println(E.e(-1))\n}\n");
        CompilationResult r2 = new CompilerDriver().compileSources(java.util.List.of(kf, mainKf),
                dir.resolve("o2"), Target.JVM, out);
        assertTrue(r2.success(), "programa deve compilar:\n" + r2.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", dir.resolve("o2").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String o = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "run: " + o);
        assertEquals("13\n14", o, "os 2 caminhos do join if-else:\n" + kof);
    }

    @Test
    void recoversIfThenChainAndRunsIt(@TempDir Path dir) throws Exception {
        // Fase C degrau 1 (Q3 idempotencia/irmas): DOIS if-sem-else seguidos.
        // Prova que a borda de um naoo vaza p/ o irmao (stops consumido
        // localmente): os 4 caminhos executam (oracle JVM medido 6/4/5/3).
        Path src = dir.resolve("classes");
        Files.createDirectories(src);
        Path s = src.resolve("C.java");
        Files.writeString(s, """
                public class C {
                    public static int c(int a, int b) {
                        int x = 0;
                        if (a > 0) { x = x + 1; }
                        if (b > 0) { x = x + 2; }
                        x = x + 3;
                        return x;
                    }
                    public static void main(String[] a) {
                        System.out.println(c(1, 1));
                        System.out.println(c(1, 0));
                        System.out.println(c(0, 1));
                        System.out.println(c(0, 0));
                    }
                }
                """);
        runJavac(java.util.List.of(s), src);
        String kof = Decompile.decompile(src.resolve("C.class"));
        assertTrue(kof.contains("if (arg0 > 0) {") && kof.contains("if (arg1 > 0) {"),
                "os dois ifs devem recuperar:\n" + kof);
        assertFalse(kof.contains("} else {"), "nenhum com else (joins puros):\n" + kof);
        Path out = dir.resolve("gen");
        Files.createDirectories(out);
        Path kf = out.resolve("C.kf");
        Files.writeString(kf, kof);
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(kf),
                dir.resolve("o"), Target.JVM, out);
        assertTrue(r.success(), "corrente decompilada deve compilar:\n" + kof + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", dir.resolve("o").toString(), "C");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String o = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "run: " + o);
        assertEquals("6\n4\n5\n3", o, "os 4 caminhos da corrente:\n" + kof);
    }

    @Test
    void nestedIfWithoutElseStaysHonestStub(@TempDir Path dir) throws Exception {
        // Fase C (Q4): o if-sem-else NAO-puro (then com sequela propria +
        // join externo) stuba HONESTO. Medido 13/09: a variante ingenua
        // (borda de stop tambem no braco do else) produzia CODIGO ERRADO
        // COMPILAVEL — a sequela do pos-if era sugada p/ dentro do else e o
        // caminho falso pulava statements. Recusar > errar (R6/portao Q0).
        Path src = dir.resolve("classes");
        Files.createDirectories(src);
        Path s = src.resolve("N2.java");
        Files.writeString(s, """
                public class N2 {
                    public static int n(int a, int b) {
                        int x = 0;
                        if (a > 0) {
                            if (b > 0) { x = x + 1; }
                            x = x + 2;
                        }
                        x = x + 3;
                        return x;
                    }
                }
                """);
        runJavac(java.util.List.of(s), src);
        String kof = Decompile.decompile(src.resolve("N2.class"));
        assertTrue(kof.contains("throw \"body not recovered\""),
                "if-aninhado sem else deve stubar honesto (nao virar codigo errado):\n" + kof);
    }

    @Test
    void switchFallthroughStaysHonestStub(@TempDir Path dir) throws Exception {
        // Kof não tem fallthrough: case sem `break` caindo no próximo braço
        // NÃO tem forma válida → stub honesto (nunca código errado).
        Path src = dir.resolve("classes");
        Files.createDirectories(src);
        Path s = src.resolve("F.java");
        Files.writeString(s, """
                public class F {
                    public static String grade(int v) {
                        String r;
                        switch (v) {
                            case 1: r = "one";
                            case 2: r = "two"; break;
                            default: r = "other"; break;
                        }
                        return r;
                    }
                }
                """);
        runJavac(java.util.List.of(s), src);
        String kof = Decompile.decompile(src.resolve("F.class"));
        assertTrue(kof.contains("body not recovered"),
                "fallthrough (case 1 sem break) deve ficar stub:\n" + kof);
        assertTrue(!kof.contains("switch ("), "nenhum switch parcial:\n" + kof);
    }

    @Test
    void escapesStringConstantsInDecompiledSource(@TempDir Path dir) throws Exception {
        // R6 (prova de drift 09/09): o ldc emitia a string do CP CRUA — `\b`,
        // newline real e `"` estouravam o lexer do .kf (LEX002/LEX004). Agora
        // escape canônico (BytecodeConcat); round-trip: decompilar → compilar.
        Path javaFile = dir.resolve("E.java");
        Files.writeString(javaFile, """
                public class E {
                    public static String s1() { return "a\\\\b"; }
                    public static String s2() { return "q\\"t"; }
                    public static String s3() { return "nl\\nend"; }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("E.class"));
        assertTrue(kof.contains("\"a\\\\b\""), "barra dupla escapada:\n" + kof);
        assertTrue(kof.contains("\"q\\\"t\""), "aspas escapadas:\n" + kof);
        assertTrue(kof.contains("\"nl\\nend\""), "newline escapado:\n" + kof);
        Path out = dir.resolve("E.kf");
        Files.writeString(out, kof);
        CompilationResult result = new CompilerDriver().compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void whitelistInstanceofRecoversAndCompiles(@TempDir Path dir) throws Exception {
        // Fase E (09/09): `x instanceof String` é idiomático Kof e o nome
        // resolve SEM import — seguro recuperar. Tipo de domínio fica stub
        // (prova de drift: `instanceof DiagnosticCollector` gerava .kf que não
        // compila; whitelist = String/primitivos/Object).
        Path javaFile = dir.resolve("Is.java");
        Files.writeString(javaFile, """
                public class Is {
                    public static boolean isStr(Object o) {
                        boolean b = o instanceof String;
                        return b;
                    }
                    public static boolean isCs(Object o) {
                        boolean b = o instanceof CharSequence;
                        return b;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Is.class"));
        assertTrue(kof.contains("arg0 instanceof String"),
                "instanceof String (whitelist) deve recuperar:\n" + kof);
        assertTrue(kof.contains("isCs(Object arg0) {\n        throw \"body not recovered\""),
                "instanceof CharSequence (domínio/JDK não-whitelist) fica stub:\n" + kof);
        Path out = dir.resolve("Is.kf");
        Files.writeString(out, kof);
        CompilationResult result = new CompilerDriver().compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void whitelistCastRecoversPrimitiveTargetsOnly(@TempDir Path dir) throws Exception {
        // checkcast p/ primitivo vira `(x as Int)` (útil p/ downcast de Object);
        // p/ String/Object é recusado (cast trivial/ambíguo na verificação).
        Path javaFile = dir.resolve("Cs.java");
        Files.writeString(javaFile, """
                public class Cs {
                    public static int take(Object o) {
                        Integer i = (Integer) o;
                        int v = i;
                        return v;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Cs.class"));
        assertTrue(kof.contains("as Int"), "checkcast Integer → `as Int`:\n" + kof);
        assertFalse(kof.contains("intValue"), "#362: unbox do wrapper = identidade — nunca emitir `x.intValue()` (SEM074):\n" + kof);
        Path out = dir.resolve("Cs.kf");
        Files.writeString(out, kof);
        CompilationResult result = new CompilerDriver().compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void invokedynamicIsFiveBytes() {
        // REGRESSÃO PEGO POR TRUNCATION-MARKER: length(0xba) era 7 —
        // INVÁLIDO (JVMS 4.9.3: opcode + u2 index + 2 zero-bytes = 5). Com 7,
        // o pc saltava a instrução SEGUINTE (o `areturn`): o teste do concat
        // passava por ACIDENTE (caía no fallback "fim sem return → topo").
        // Bytes reais de javac: `lload_0; invokedynamic #7; areturn`
        // → offsets 0,1..5,6. Com len=5, o 0xb0 em offset 6 É alcançado.
        String[] cp = new String[9];
        cp[7] = "CONCAT:v=\u000142";
        byte[] code = {0x1e, (byte) 0xba, 0x00, 0x07, 0x00, 0x00, (byte) 0xb0};
        String e = BytecodeDecoder.recoverExpression(code, cp,
                new BytecodeFrame("(J)Ljava/lang/String;", true));
        // o long vira toString no concat do javac; com o índice correto o
        // decoder enxerga o areturn e recupera o valor (não o fallback):
        assertEquals("\"v=\" + arg0 + \"42\"", e, "invokedynamic len=5: areturn alcançável:\n" + e);
    }

    @Test
    void truncatedLastInstructionBecomesHonestStub() {
        // BytecodeReader nunca lança em código truncado: marcador → decoder
        // default → null → stub honesto (ferramenta sobre .class real).
        String[] cp = new String[0];
        byte[] trunc = {0x1a, 0x13, 0x01};   // iload_0; ldc_w com índice incompleto
        assertNull(BytecodeDecoder.recoverExpression(trunc, cp,
                new BytecodeFrame("()I", true)), "truncado → null (stub), nunca exceção");
    }

    @Test
    void wideIincConsumesSixBytes() {
        // skipVariable comparava op == 0x84 — IMPOSSÍVEL (o op é 0xc4; 0x84 é
        // o SUB-opcode lido depois). Consequência: `wide iinc` (6 bytes) era
        // pulado como 3 e TODO opcode seguinte driftava (ldc/invokes errados
        // ou truncamento fantasma). wide normal = 4; wide iinc = 6.
        byte[] code = {(byte) 0xc4, (byte) 0x84, 0x00, 0x65, 0x00, 0x03, (byte) 0xb1};
        var insns = BytecodeReader.decode(code);
        // wide é OPAQUE (1 insn, skipVariable consome os 6) + return = 2
        assertEquals(2, insns.size());
        assertEquals(6, insns.get(1).offset(), "return deve começar em 6 (não 3+1!)");
        // wide SEM iinc (iinc = 4 bytes)
        var w = BytecodeReader.decode(new byte[]{(byte) 0xc4, 0x15, 0x01, 0x23, (byte) 0xac});
        assertEquals(2, w.size());
        assertEquals(4, w.get(1).offset());
    }

    @Test
    void wideParamsMapToCorrectSlots(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("V.java");
        Files.writeString(javaFile, """
                public class V {
                    public static int m(long x, int a, int b) { return a < b ? 1 : 0; }
                    public static long twoLongs(long a, long b) { return a; }
                    public static double sq(double x) { double y = x; return y; }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("V.class"));

        // R6 (lição bug 62): Long/Double ocupam DOIS slots (JVMS 2.6.1). Com
        // mapeamento de 1-slot, `iload_2` (segundo int, slot 2) virava nome de
        // parâmetro ERRADO (arg2) / local inexistente (v3) → decompilado que
        // NÃO compila (SEM011). BytecodeFrame resolve pelo descriptor.
        // §236 (14/09): corpo Int com shape cmp/iconst_1/goto/iconst_0/ireturn
        // (ternario `a < b ? 1 : 0` do javac) NAo pode dobrar p/ Bool cru
        // (SEM010, nao-compilavel) — o pino antigo guardava a saida errada que o
        // compilador tolerava; agora = if-expression de inteiros, e o teste
        // compila a saida (a linha 1230 sempre tentou — era o pino que estava
        // errado, nao o fold).
        assertTrue(kof.contains("Int m(Long arg0, Int arg1, Int arg2) = if (arg1 < arg2) 1 else 0"),
                "wide long empurra slots + ternario int (nao Bool cru):\n" + kof);
        assertFalse(kof.contains(" v3") && kof.contains("Int m("),
                "slot 2 não é mais arg0-shift:\n" + kof);
        // lload_0 devolve arg0 (não v0): corpo de 2 slots wide
        assertTrue(kof.contains("Long twoLongs(Long arg0, Long arg1) = arg0"),
                "lload_0 = arg0:\n" + kof);
        // dstore_2 (0x49): slot = (op-0x3f)%4 = 2 (o bug antigo dava slot 9→v10
        // inconsistente com dload_1); y local no slot 2 (arg0 wide=0,1)
        assertTrue(kof.contains("Double sq(Double arg0)") && kof.contains("var v2 = arg0")
                        && kof.contains("return v2"),
                "dstore_2 → slot 2:\n" + kof);

        Path out = dir.resolve("V.kf");
        Files.writeString(out, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void recoversLongDoubleArithmeticAndCasts(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Num.java");
        Files.writeString(javaFile, """
                public class Num {
                    public static long ladd(long a, long b) { return a + b; }
                    public static long ldiv(long a, long b) { return a / b; }
                    public static double dmul(double a, double b) { return a * b; }
                    public static long i2l(int a) { return a; }
                    public static int l2i(long a) { return (int) a; }
                    public static int d2i(double a) { return (int) a; }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Num.class"));

        // Unit B (Fase C/E): ladd/ldiv/dmul e casts têm opcode que é a FONTE do
        // tipo (verificador JVM garante); a pilha valor+tipo (BytecodeTypes.TStack)
        // só emite quando os operandos/retorno batem (lição bug 62: opcode
        // "linear" ainda pode driftar). / long trunc, casts truncam p/ Int.
        assertTrue(kof.contains("Long ladd(Long arg0, Long arg1) = (arg0 + arg1)"),
                "ladd:\n" + kof);
        assertTrue(kof.contains("Long ldiv(Long arg0, Long arg1) = (arg0 / arg1)"),
                "ldiv:\n" + kof);
        assertTrue(kof.contains("Double dmul(Double arg0, Double arg1) = (arg0 * arg1)"),
                "dmul:\n" + kof);
        assertTrue(kof.contains("Long i2l(Int arg0) = (arg0 as Long)"), "i2l:\n" + kof);
        assertTrue(kof.contains("Int l2i(Long arg0) = (arg0 as Int)"), "l2i:\n" + kof);
        assertTrue(kof.contains("Int d2i(Double arg0) = (arg0 as Int)"), "d2i:\n" + kof);
        assertFalse(kof.contains("body not recovered"), "nenhum deve degradar:\n" + kof);

        Path out = dir.resolve("Num.kf");
        Files.writeString(out, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void mixedTypeAndLoopShapesRecoverOrDegradeHonest(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("S2.java");
        Files.writeString(javaFile, """
                public class S2 {
                    public static long mixed(long a, int b) { return a + b; }
                    public static int nestCast(long a, long b) { return (int) (a + b); }
                    public static String longStr(long a) { return "v" + a; }
                    public static int callRet(int a) { return a + Math.abs(a); }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("S2.class"));

        // R6 sweep pós-Unit B: widening implícito de Java (long + int) é
        // i2l+ladd no bytecode — recupera como `(arg0 + (arg1 as Long))`
        // (cast explícito, tipo bate p/ o ladd). Cast aninhado e concat de
        // long (invokedynamic) preservam o tipo. Não drifta (lição 62).
        assertTrue(kof.contains("Long mixed(Long arg0, Int arg1) = (arg0 + (arg1 as Long))"),
                "widening long+int:\n" + kof);
        assertTrue(kof.contains("Int nestCast(Long arg0, Long arg1) = ((arg0 + arg1) as Int)"),
                "cast aninhado:\n" + kof);
        assertTrue(kof.contains("String longStr(Long arg0) = \"v\" + arg0"),
                "concat de long (invokedynamic):\n" + kof);
        assertTrue(kof.contains("(arg0 + math.abs(arg0))"),
                "Math.abs (I)I → math.abs — sem owner JDK (R6: SEM011):\n" + kof);

        Path out = dir.resolve("S2.kf");
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
    void recoversCharCastButStaysHonestOnByteShort(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Cast.java");
        Files.writeString(javaFile, """
                public class Cast {
                    public static char toChar(int a) { return (char) (a + 65); }
                    public static byte toByte(int a) { return (byte) a; }
                    public static short toShort(int a) { return (short) a; }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Cast.class"));

        // i2c → `as Char`: ÚNICO narrowing de Kof fiel ao Java (o codegen do
        // Kof emite i2c real p/ `x as Char`, wrap 16 bits igual (char)).
        assertTrue(kof.contains("Char toChar(Int arg0) = ((arg0 + 65) as Char)"),
                "i2c deve recuperar as Char:\n" + kof);
        // i2b/i2s NÃO têm equivalente fiel: Kof `as Byte`/`as Short` são no-op
        // (256 as Byte = 256 ≠ (byte) 256 = 0). Recusa → stub honesto (R6).
        assertTrue(kof.contains("Byte toByte(Int arg0) {\n        throw \"body not recovered\""),
                "i2b deve ficar stub (sem cast fiel em Kof):\n" + kof);
        assertTrue(kof.contains("Short toShort(Int arg0) {\n        throw \"body not recovered\""),
                "i2s deve ficar stub (sem cast fiel em Kof):\n" + kof);

        Path out = dir.resolve("Cast.kf");
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

    @Test
    void recoversPureJavaRecordAsKofRecord(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Point.java");
        Files.writeString(javaFile, """
                public record Point(int x, int y) { }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Point.class"));
        assertTrue(kof.contains("record Point(Int x, Int y)"), "record puro → record Kof:\n" + kof);
        assertFalse(kof.contains("extends Record"), "sem esqueleto class+extends:\n" + kof);
        assertFalse(kof.contains("body not recovered"), "sem stub sintético:\n" + kof);
        Path out = dir.resolve("Point.kf");
        Files.writeString(out, kof);
        CompilationResult result = new CompilerDriver().compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void recoversPureRecordWithGenericsAndObjects(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Bag2.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public record Bag2(List<String> items, String label) { }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Bag2.class"));
        assertTrue(kof.contains("record Bag2(List<String> items, String label)"),
                "componentes genéricos EXACT:\n" + kof);
        assertFalse(kof.contains("body not recovered"), "sem stub:\n" + kof);
        Path out = dir.resolve("Bag2.kf");
        Files.writeString(out, kof);
        CompilationResult result = new CompilerDriver().compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void extraMethodInRecordKeepsHonestSkeleton(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Twice.java");
        Files.writeString(javaFile, """
                public record Twice(int x) {
                    public int twice() { return x * 2; }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Twice.class"));
        // método extra (não-acessor) → não-puro → esqueleto de hoje (zero drift)
        assertTrue(kof.contains("class Twice extends Record"), "extra → skeleton atual:\n" + kof);
        assertTrue(kof.contains("Int twice() = (this.x * 2)"), "método extra ainda recuperado como body:\n" + kof);
    }

    @Test
    void reservedComponentNameKeepsHonestSkeleton(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("ValR.java");
        Files.writeString(javaFile, """
                public record ValR(int val) { }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("ValR.class"));
        // `val` é reservada no frontend (PARSE015) → emitir record DRIFTARIA;
        // deve permanecer no skeleton atual (honesto, compila).
        assertTrue(kof.contains("class ValR extends Record"), "nome reservado → skeleton atual:\n" + kof);
    }

    @Test
    void recordWithInterfaceKeepsHonestSkeleton(@TempDir Path dir) throws Exception {
        Path iface = dir.resolve("Named.java");
        Files.writeString(iface, "public interface Named { String name(); }\n");
        Path javaFile = dir.resolve("NamedR.java");
        Files.writeString(javaFile, """
                public record NamedR(String name) implements Named { }
                """);
        runJavac(java.util.List.of(iface, javaFile), dir);
        String kof = Decompile.decompile(dir.resolve("NamedR.class"));
        // `record X implements Y` → PARSE007 no frontend (probe 13/09) → skeleton atual
        assertTrue(kof.contains("class NamedR extends Record"), "implements → skeleton atual:\n" + kof);
    }

    @Test
    void recoversGenericRecordWithExactTypeParams(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Gp.java");
        Files.writeString(javaFile, """
                public record Gp<T>(T value, int tag) { }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Gp.class"));
        assertTrue(kof.contains("record Gp<T>(T value, Int tag)"),
                "type-param EXATO na forma Kof:\n" + kof);
        assertFalse(kof.contains("body not recovered"), "sem stub:\n" + kof);
        Path out = dir.resolve("Gp.kf");
        Files.writeString(out, kof);
        CompilationResult result = new CompilerDriver().compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void genericBoundRecordKeepsHonestSkeleton(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Bnd.java");
        Files.writeString(javaFile, """
                public record Bnd<T extends Comparable<T>>(T value) { }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Bnd.class"));
        // bound genérico (`<T:Ljava/lang/Comparable<...>;>`) não é a forma que
        // emitimos: RECUSAR (skeleton atual, compila) — nunca `record Bnd<T>` errado.
        assertTrue(kof.contains("class Bnd extends Record"), "bound não-suportado → skeleton atual:\n" + kof);
    }

    @Test
    void recoversRecordImplementingSamePackageInterface(@TempDir Path dir) throws Exception {
        Path root = dir.resolve("classes");
        Files.createDirectories(root);
        Path iface = root.resolve("Named.java");
        Files.writeString(iface, "public interface Named { String name(); }\n");
        Path javaFile = root.resolve("NamedR.java");
        Files.writeString(javaFile, "public record NamedR(String name) implements Named { }\n");
        runJavac(java.util.List.of(iface, javaFile), root);
        Path out = dir.resolve("gen");
        Decompile.decompileTree(root, out);
        String kof = Files.readString(out.resolve("NamedR.kf"));
        assertTrue(kof.contains("record NamedR(String name) implements Named"),
                "interface top do MESMO pacote resolve (#325: forma Kof canonical = comps ANTES do implementsntes):\n" + kof);
        assertFalse(kof.contains("body not recovered"), "sem stub:\n" + kof);
        CompilationResult r = new CompilerDriver().compileSources(
                List.of(out.resolve("NamedR.kf").toAbsolutePath().normalize(),
                        out.resolve("Named.kf").toAbsolutePath().normalize()),
                dir.resolve("kout"), Target.JVM, out);
        assertTrue(r.success(), "record implements irmão deve compilar junto:\n" + kof + "\n" + r.diagnostics().getDiagnostics());
    }

    @Test
    void recoversRecordWithInterfaceFromTreeScope(@TempDir Path dir) throws Exception {
        Path root = dir.resolve("classes");
        Path p1 = root.resolve("a");
        Path p2 = root.resolve("b");
        Files.createDirectories(p1);
        Files.createDirectories(p2);
        Path iface = p1.resolve("Iface.java");
        Files.writeString(iface, "package a; public interface Iface { String name(); }\n");
        Path rec = p2.resolve("CrossR.java");
        Files.writeString(rec, "package b;\nimport a.Iface;\npublic record CrossR(String name) implements Iface { }\n");
        runJavac(java.util.List.of(iface, rec), root);
        Path out = dir.resolve("gen");
        Decompile.decompileTree(root, out);
        String kof = Files.readString(out.resolve("b/CrossR.kf"));
        assertTrue(kof.contains("record CrossR(String name) implements Iface"),
                "cross-package com import (#325 forma canonical):\n" + kof);
        assertTrue(kof.contains("import a.Iface"), "import emitido:\n" + kof);
        assertFalse(kof.contains("body not recovered"), "sem stub:\n" + kof);
        CompilationResult r = new CompilerDriver().compileSources(
                List.of(out.resolve("b/CrossR.kf").toAbsolutePath().normalize(),
                        out.resolve("a/Iface.kf").toAbsolutePath().normalize()),
                dir.resolve("out"), Target.JVM, out);
        assertTrue(r.success(), "árvore cross-pkg compila:\n" + kof + "\n" + r.diagnostics().getDiagnostics());
    }

    @Test
    void recordWithOutOfTreeInterfaceKeepsHonestSkeleton(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("SerR.java");
        Files.writeString(javaFile, """
                import java.io.Serializable;
                public record SerR(int x) implements Serializable { }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("SerR.class"));
        // JDK fora da árvore → `implements Serializable` é SEM015/PKG006 = drift
        // → skeleton atual (compila hoje; Serializable era resolveSuperName fallback)
        assertTrue(kof.contains("class SerR extends Record"), "fora-da-árvore → skeleton:\n" + kof);
    }

    @Test
    void recoversRecordImplementingSamePackageInnerInterface(@TempDir Path dir) throws Exception {
        Path root = dir.resolve("classes");
        Files.createDirectories(root);
        Path outer = root.resolve("Box.java");
        Files.writeString(outer, """
                public class Box {
                    public interface Expr { }
                }
                """);
        Path rec = root.resolve("NumR.java");
        Files.writeString(rec, "public record NumR(int v) implements Box.Expr { }\n");
        runJavac(java.util.List.of(outer, rec), root);
        Path out = dir.resolve("gen");
        Decompile.decompileTree(root, out);
        String numr = Files.readString(out.resolve("NumR.kf"));
        assertTrue(numr.contains("record NumR(Int v) implements Box$Expr"),
                "interna do MESMO pacote: frontend aceita nome com `$` como top (#325 forma canonical):\n" + numr);
        assertFalse(numr.contains("body not recovered"), "sem stub:\n" + numr);
        CompilationResult r = new CompilerDriver().compileSources(
                List.of(out.resolve("NumR.kf").toAbsolutePath().normalize(),
                        out.resolve("Box.kf").toAbsolutePath().normalize(),
                        out.resolve("Box$Expr.kf").toAbsolutePath().normalize()),
                dir.resolve("kout"), Target.JVM, out);
        assertTrue(r.success(), "record implements interna compila junto:\n"
                + numr + "\n" + Files.readString(out.resolve("Box.kf")) + "\n"
                + Files.readString(out.resolve("Box$Expr.kf")) + "\n"
                + r.diagnostics().getDiagnostics());
    }

    @Test
    void recordWithCrossPackageInnerInterfaceKeepsHonestSkeleton(@TempDir Path dir) throws Exception {
        Path root = dir.resolve("classes");
        Files.createDirectories(root.resolve("a"));
        Path iface = root.resolve("a/Box.java");
        Files.writeString(iface, """
                package a;
                public class Box {
                    public interface Expr { }
                }
                """);
        Path rec = root.resolve("CrossI.java");
        Files.writeString(rec, """
                import a.Box;
                public record CrossI(int v) implements Box.Expr { }
                """);
        runJavac(java.util.List.of(iface, rec), root);
        Path out = dir.resolve("gen");
        Decompile.decompileTree(root, out);
        String kof = Files.readString(out.resolve("CrossI.kf"));
        // interna CROSS-pacote: `import a.Box$Expr` NÃO PROVADO (SEM015?) →
        // NÃO vira record. O `implements Box$Expr` que aparece vem do
        // fallback de skeleton PRÉ-EXISTENTE (não mudei o fallback); o gate
        // desta unidade é record-vs-skeleton.
        assertTrue(kof.contains("class CrossI extends Record"),
                "interna cross-pacote continua honesta (skeleton):\n" + kof);
        assertFalse(kof.contains("record CrossI"), "não virou record (cross-pkg interna):\n" + kof);
    }

    private void runJavac(Path javaFile, Path dir) throws IOException, InterruptedException {
        runJavac(java.util.List.of(javaFile), dir);
    }

    private void runJavac(java.util.List<Path> javaFiles, Path dir) throws IOException, InterruptedException {
        String javaHome = System.getProperty("java.home");
        Path javac = Path.of(javaHome, "bin", "javac");
        var cmd = new java.util.ArrayList<String>();
        cmd.add(javac.toString()); cmd.add("-d"); cmd.add(dir.toString());
        for (Path f : javaFiles) cmd.add(f.toString());
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        int rc = p.waitFor();
        if (rc != 0) {
            throw new IOException("javac failed: " + new String(p.getInputStream().readAllBytes()));
        }
    }
}