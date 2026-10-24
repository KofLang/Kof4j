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
    void bottomTestedLoopWithFusedHeaderAndBreakRecover(@TempDir Path dir) throws Exception {
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

        // Fase E (24/10): face atualizada quando o sep virou TRADUÇÃO — o
        // do-while com o primeiro statement do corpo fundido ao header
        // (cond nula) recupera via testSplit + detecção gated: o prefixo
        // `DoBr.OUT = arg0` sai DENTRO do corpo (o marcador do `do {` é
        // gravado antes da emissão do prefixo). O brk (break no ramo)
        // já recuperava da rodada anterior. Nenhum stub; nenhum while
        // inventado com a condição do if interno (R6).
        assertFalse(kof.contains("body not recovered"), "nenhum stub:\n" + kof);
        assertTrue(kof.contains("do {"), "o wrap `do {`:\n" + kof);
        assertTrue(kof.contains("DoBr.OUT = arg0"), "o prefixo fundido dentro do corpo:\n" + kof);
        assertTrue(kof.contains("if (arg0 > 100) {"), "o if interno do sep:\n" + kof);
        assertTrue(kof.contains("if (arg0 == 3) {\n        break"), "o break no ramo do brk:\n" + kof);
        assertTrue(kof.contains("} while (arg0 > 0)"), "as conds no FIM:\n" + kof);
        // zero-drift executado contra o oráculo Java
        String kofRun = kof + "\nmain() {\n"
                + "    println(DoBr.sep(5))\n    println(DoBr.sep(0))\n    println(DoBr.sep(3))\n    println(DoBr.sep(200))\n"
                + "    println(DoBr.brk(5))\n    println(DoBr.brk(3))\n    println(DoBr.brk(0))\n}\n";
        CompilationResult result = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(result.success(), "compila zero drift:\n" + kofRun + "\n" + result.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("2\n0\n3\n2\n3\n3\n-1", output, "zero-drift: sep + brk como no Java");
    }

    @Test
    void coinBoxLoopDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (24/10): o domínio MÁQUINA DE VENDAS — a integração das
        // fatias do do-while desta roda num domínio realista: o
        // insertCoins tem o PRIMEIRO statement do corpo fundido ao header
        // (v1 += 25; v2 += 1 antes do if) MAIS o break no ramo (a
        // integração do header fundido + break), e o refund tem o break
        // como primeiro statement do corpo (o header puro). Zero código
        // novo — tudo sai das fatias já aterrissadas.
        Path javaFile = dir.resolve("CoinBox.java");
        Files.writeString(javaFile, """
                public class CoinBox {
                    static int insertCoins(int target) {
                        int credit = 0;
                        int coins = 0;
                        do {
                            credit = credit + 25;
                            coins = coins + 1;
                            if (credit >= target) break;
                        } while (coins < 10);
                        return coins;
                    }
                    static int refund(int price, int paid) {
                        int change = paid - price;
                        int coins = 0;
                        do {
                            if (change < 10) break;
                            change = change - 10;
                            coins = coins + 1;
                        } while (change >= 0);
                        return coins;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("CoinBox.class"));
        assertFalse(kof.contains("body not recovered"), "sem stubs:\n" + kof);
        assertTrue(kof.contains("do {"), "os wraps:\n" + kof);
        assertTrue(kof.contains("if (v1 >= arg0) {\n        break"), "o break do insertCoins:\n" + kof);
        assertTrue(kof.contains("if (v2 < 10) {\n        break"), "o break do refund:\n" + kof);
        assertTrue(kof.contains("} while (v2 < 10)") && kof.contains("} while (v2 >= 0)"),
                "as conds no FIM:\n" + kof);
        // zero-drift executado (oráculo Java: 4/10/1/5/0/0)
        String kofRun = kof + "\nmain() {\n    var c = CoinBox()\n"
                + "    println(c.insertCoins(100))\n    println(c.insertCoins(500))\n    println(c.insertCoins(0))\n"
                + "    println(c.refund(50, 100))\n    println(c.refund(50, 55))\n    println(c.refund(50, 50))\n}\n";
        CompilationResult result = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(result.success(), "compila zero drift:\n" + kofRun + "\n" + result.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("4\n10\n1\n5\n0\n0", output, "zero-drift: a máquina de vendas como no Java");
    }

    @Test
    void digitsDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (24/10): o domínio DÍGITOS — o guard if SEM else que
        // converge no PRÓPRIO header do do-while em auto-loop (o
        // `if (n < 0) n = -n;` antes do loop): os preds não-self do join
        // são {branch, fim-do-then} e o pred extra é o join (a aresta de
        // retorno do auto-loop) — o pureIfThen agora aceita. Mais: o
        // `long` com multiplicação no corpo do do-while (scale, wide
        // arithmetic) e o early-return com while (countDigits).
        Path javaFile = dir.resolve("Digits.java");
        Files.writeString(javaFile, """
                public class Digits {
                    static int digitSum(int n) {
                        int sum = 0;
                        if (n < 0) n = -n;
                        do {
                            sum = sum + n % 10;
                            n = n / 10;
                        } while (n > 0);
                        return sum;
                    }
                    static int reverse(int n) {
                        int r = 0;
                        do {
                            r = r * 10 + n % 10;
                            n = n / 10;
                        } while (n > 0);
                        return r;
                    }
                    static long scale(long n, long factor) {
                        long result = n;
                        int steps = 0;
                        do {
                            result = result * factor;
                            steps = steps + 1;
                        } while (steps < 3);
                        return result;
                    }
                    static int countDigits(int n) {
                        if (n == 0) return 1;
                        int c = 0;
                        while (n > 0) { c++; n = n / 10; }
                        return c;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Digits.class"));
        assertFalse(kof.contains("body not recovered"), "sem stubs:\n" + kof);
        assertTrue(kof.contains("if (arg0 < 0) {"), "o guard antes do loop:\n" + kof);
        assertTrue(kof.contains("} while (arg0 > 0)"), "as conds no FIM:\n" + kof);
        assertTrue(kof.contains("Long scale"), "o long no corpo do do-while:\n" + kof);
        // zero-drift executado (oráculo Java: 15/11/4321/1/5/3000). As
        // chamadas são QUALIFICADAS pela CLASSE por causa de dois defeitos
        // do compilador medidos nesta rodada e registrados para a lane do
        // compilador (não são desta lane): (1) a chamada estática via
        // INSTÂNCIA com argumento que precisa de transformação emite o
        // RECEIVER como base do argumento — `p.twice(3)` com parâmetro
        // Long sai `aload_1; i2l`, `p.twice(-47)` sai `aload_1; ineg`
        // (bytecode inválido, VerifyError no load mascarado pela mensagem
        // de JavaFX no launcher); (2) a mesma chamada long via instância
        // em PRIMEIRO lugar no main derruba o COMPUTE_FRAMES
        // (NegativeArraySizeException no visitMaxs, ordem-dependente).
        // A forma qualificada pela classe emite limpo (medido: P3/DR3).
        String kofRun = kof + "\nmain() {\n"
                + "    println(Digits.digitSum(591))\n    println(Digits.digitSum(-47))\n"
                + "    println(Digits.reverse(1234))\n"
                + "    println(Digits.countDigits(0))\n    println(Digits.countDigits(90210))\n"
                + "    println(Digits.scale(3, 10))\n}\n";
        CompilationResult result = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(result.success(), "compila zero drift:\n" + kofRun + "\n" + result.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("15\n11\n4321\n1\n5\n3000", output, "zero-drift: os dígitos como no Java");
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

    @Test
    void fusedHeadTernaryArgRecovers(@TempDir Path dir) throws Exception {
        // Fase E (16/10): o HEAD FUNDIDO do for-each com o TERNÁRIO-COMO-ARG
        // — o blkSeq corta o prefixo da binding (o elemento: it.next +
        // checkcast + astore) e o teste fica no mesmo bloco; o corpo é
        // `v1.add(if (v3.length > 10) v3.substring(0, 10) else v3)` — o
        // if-EXPRESSÃO como o argumento do add (o join = [call 1-arg, pop]).
        // O slice: (i) a binding podada (o for-each já declara o elemento no
        // cabeçalho — registra o slot sem re-emitir) + (ii) o split com
        // allowLoad + (iii) a emissão recv.call(if (cond) t else e) com o
        // skip do call/pop no join. Zero-drift executado: 4/alexandre/bia/
        // guilherme/ca — 5/5 idênticos ao Java.
        Path javaFile = dir.resolve("TernHead.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class TernHead {
                    static List<String> heads(List<String> names) {
                        List<String> out = new java.util.ArrayList<>();
                        for (String n : names) {
                            out.add(n.length() > 10 ? n.substring(0, 10) : n);
                        }
                        return out;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("TernHead.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    var ws = listOf("alexandre", "bia", "guilherme", "ca")
                    println(TernHead.heads(ws).size())
                    for (var h in TernHead.heads(ws)) { println(h) }
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
        // oracle Java: 4 | alexandre | bia | guilherme | ca
        assertEquals("4\nalexandre\nbia\nguilherme\nca", output,
                "zero-drift: o ternário-como-arg no head fundido idêntico ao Java");
    }

    @Test
    void orOfReceiverThreadedCallsRecovers(@TempDir Path dir) throws Exception {
        // Fase E (16/10): o || de DUAS calls receiver-threaded — cada termo
        // = [dload, invokestatic, ldc2_w, dcmp, ifXX] (o dcmpl/dcmpg de
        // sinal com o const Long/Double do CP) e a orientação MISTO dentro
        // da cadeia (o 1º salta-VERDADEIRO, o 2º salta-FALSO). O slice: o
        // PAR-CMP GERAL no blockConditionTaken (a direção do JUMP) + o
        // espelho no blockCondition (a queda) + o fallback no walk do ||
        // quando o testExpr fechado recusa. Zero-drift executado:
        // true/true/false/true/1/2/3/4/60.0 — 9/9 idênticos ao Java.
        Path javaFile = dir.resolve("Trig.java");
        Files.writeString(javaFile, """
                public class Trig {
                    static double normalize(double deg) {
                        double d = deg % 360;
                        if (d < 0) { d = d + 360; }
                        return d;
                    }
                    static int quadrant(double deg) {
                        double n = normalize(deg);
                        if (n < 90) { return 1; }
                        if (n < 180) { return 2; }
                        if (n < 270) { return 3; }
                        return 4;
                    }
                    static boolean isRightAngle(double deg) {
                        return normalize(deg) == 90 || normalize(deg) == 270;
                    }
                    static double complement(double deg) {
                        return 90 - normalize(deg);
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Trig.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Trig.isRightAngle(90.0))
                    println(Trig.isRightAngle(270.0))
                    println(Trig.isRightAngle(45.0))
                    println(Trig.isRightAngle(-90.0))
                    println(Trig.quadrant(45.0))
                    println(Trig.quadrant(135.0))
                    println(Trig.quadrant(225.0))
                    println(Trig.quadrant(315.0))
                    println(Trig.complement(30.0))
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
        // oracle Java: true | true | false | true | 1 | 2 | 3 | 4 | 60.0
        assertEquals("true\ntrue\nfalse\ntrue\n1\n2\n3\n4\n60.0", output,
                "zero-drift: o || de calls receiver-threaded idêntico ao Java");
    }

    @Test
    void mathDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (16/10): o dom\u00ednio MATEM\u00c1TICO — 4 shapes sem c\u00f3digo
        // novo: o clamp com os DOIS ifs limiares e o return do pr\u00f3prio
        // param, a DIST\u00c2NCIA com os locais intermediários + o Math.sqrt +
        // o dmul/dadd (dx * dx + dy * dy), o sign com o -1 literal e o
        // power com o ACUMULADOR Long no for (r = r * base). Zero-drift
        // executado: 5/0/10/5.0/1/-1/0/1024/243 — 9/9 id\u00eanticos ao Java
        // — a 20\u00aa valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Math2.java");
        Files.writeString(javaFile, """
                public class Math2 {
                    static int clamp(int v, int lo, int hi) {
                        if (v < lo) { return lo; }
                        if (v > hi) { return hi; }
                        return v;
                    }
                    static double distance(double x1, double y1, double x2, double y2) {
                        double dx = x2 - x1;
                        double dy = y2 - y1;
                        return Math.sqrt(dx * dx + dy * dy);
                    }
                    static int sign(int v) {
                        if (v > 0) { return 1; }
                        if (v < 0) { return -1; }
                        return 0;
                    }
                    static long power(int base, int exp) {
                        long r = 1;
                        for (int i = 0; i < exp; i++) {
                            r = r * base;
                        }
                        return r;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Math2.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Math2.clamp(5, 0, 10))
                    println(Math2.clamp(-3, 0, 10))
                    println(Math2.clamp(15, 0, 10))
                    println(Math2.distance(0.0, 0.0, 3.0, 4.0))
                    println(Math2.sign(7))
                    println(Math2.sign(-7))
                    println(Math2.sign(0))
                    println(Math2.power(2, 10))
                    println(Math2.power(3, 5))
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
        // oracle Java: 5 | 0 | 10 | 5.0 | 1 | -1 | 0 | 1024 | 243
        assertEquals("5\n0\n10\n5.0\n1\n-1\n0\n1024\n243", output,
                "zero-drift: clamp/distance/sign/power id\u00eanticos ao Java");
    }

    @Test
    void rentalDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (16/10): o dom\u00ednio ALUGUEL — 4 shapes sem c\u00f3digo novo: o
        // custo com o local intermediário + o REBATE condicional (cost:
        // `base = base * 0.9` dentro do if), as TR\u00caS faixas de idade
        // (category), o ACUMULADOR de km no for-each (totalKm) e o
        // available com o contains + a NEGAÇÃO (o `!` na expressão do
        // return). Zero-drift executado: 300.0/900.0/young/standard/
        // senior/405/true/false — 8/8 id\u00eanticos ao Java — a 21\u00aa
        // valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Rental.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Rental {
                    static double cost(int days, double dailyRate) {
                        double base = days * dailyRate;
                        if (days > 7) { base = base * 0.9; }
                        return base;
                    }
                    static String category(int age) {
                        if (age < 25) { return "young"; }
                        if (age > 70) { return "senior"; }
                        return "standard";
                    }
                    static int totalKm(List<Integer> dailyKm) {
                        int total = 0;
                        for (int km : dailyKm) {
                            total += km;
                        }
                        return total;
                    }
                    static boolean available(String model, List<String> rented) {
                        return !rented.contains(model);
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Rental.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Rental.cost(3, 100.0))
                    println(Rental.cost(10, 100.0))
                    println(Rental.category(21))
                    println(Rental.category(35))
                    println(Rental.category(75))
                    var kms = listOf(120, 85, 200)
                    println(Rental.totalKm(kms))
                    var rented = listOf("sedan", "suv")
                    println(Rental.available("hatch", rented))
                    println(Rental.available("suv", rented))
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
        // oracle Java: 300.0 | 900.0 | young | standard | senior | 405 | true | false
        assertEquals("300.0\n900.0\nyoung\nstandard\nsenior\n405\ntrue\nfalse", output,
                "zero-drift: cost/category/totalKm/available id\u00eanticos ao Java");
    }

    @Test
    void pollDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (16/10): o dom\u00ednio ELEI\u00c7\u00c3O — 3 shapes sem c\u00f3digo novo:
        // os DOIS contadores com o ELSE-IF na cadeia (winner: `if (v.equals(
        // "A")) a++; else if (v.equals("B")) b++;`), o contador de BRANCOS
        // com o && NEGADO (`!v.equals("A") && !v.equals("B")`) e o
        // UNANIME com o early-false invertido (unanimous). Zero-drift
        // executado: A/1/false/true — 4/4 id\u00eanticos ao Java — a 22\u00aa
        // valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Poll.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Poll {
                    static String winner(List<String> votes) {
                        int a = 0, b = 0;
                        for (String v : votes) {
                            if (v.equals("A")) { a++; }
                            else if (v.equals("B")) { b++; }
                        }
                        if (a > b) { return "A"; }
                        if (b > a) { return "B"; }
                        return "tie";
                    }
                    static int blankCount(List<String> votes) {
                        int n = 0;
                        for (String v : votes) {
                            if (!v.equals("A") && !v.equals("B")) { n++; }
                        }
                        return n;
                    }
                    static boolean unanimous(List<String> votes, String cand) {
                        for (String v : votes) {
                            if (!v.equals(cand)) { return false; }
                        }
                        return true;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Poll.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    var vs = listOf("A", "B", "A", "A", "C")
                    println(Poll.winner(vs))
                    println(Poll.blankCount(vs))
                    println(Poll.unanimous(vs, "A"))
                    println(Poll.unanimous(listOf("A", "A"), "A"))
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
        // oracle Java: A | 1 | false | true
        assertEquals("A\n1\nfalse\ntrue", output,
                "zero-drift: winner/blankCount/unanimous id\u00eanticos ao Java");
    }

    @Test
    void bankDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (16/10): o dom\u00ednio BANCO — o for-each sobre elementos
        // DOUBLE (o unboxing doubleValue → dstore) fatia LANÇADA: o scan da
        // binding do for-each cobre os stores WIDE (lstore/fstore/dstore
        // genéricos + _n) e o evalInto os modela (a sombra do slot wide
        // nunca é lida). Os shapes: o SALDO acumulando no for-each
        // (balance), o OVERDRAWN com o early-return DENTRO do loop (o teste
        // do saldo corrente), as 4 faixas de fee (feeTier) e a formatação
        // com o int-div/módulo + concat (formatCents). Zero-drift
        // executado: 82.5/true/false/0/2/3/$12.34 — 7/7 id\u00eanticos ao
        // Java — a 23\u00aa valida\u00e7\u00e3o.
        Path javaFile = dir.resolve("Bank.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Bank {
                    static double balance(List<Double> txs) {
                        double bal = 0;
                        for (double t : txs) {
                            bal += t;
                        }
                        return bal;
                    }
                    static boolean overdrawn(List<Double> txs, double limit) {
                        double bal = 0;
                        for (double t : txs) {
                            bal += t;
                            if (bal < limit) { return true; }
                        }
                        return false;
                    }
                    static int feeTier(int ops) {
                        if (ops < 10) { return 0; }
                        if (ops < 50) { return 1; }
                        if (ops < 200) { return 2; }
                        return 3;
                    }
                    static String formatCents(int cents) {
                        return "$" + cents / 100 + "." + cents % 100;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Bank.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    var txs = listOf(100.0, -30.0, 12.5)
                    println(Bank.balance(txs))
                    println(Bank.overdrawn(listOf(50.0, -60.0, 5.0), -5.0))
                    println(Bank.overdrawn(listOf(50.0, -40.0), -5.0))
                    println(Bank.feeTier(5))
                    println(Bank.feeTier(100))
                    println(Bank.feeTier(500))
                    println(Bank.formatCents(1234))
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
        // oracle Java: 82.5 | true | false | 0 | 2 | 3 | $12.34
        assertEquals("82.5\ntrue\nfalse\n0\n2\n3\n$12.34", output,
                "zero-drift: balance/overdrawn/feeTier/formatCents id\u00eanticos ao Java");
    }

    @Test
    void orInLoopSharedContinueJoinRecovers(@TempDir Path dir) throws Exception {
        // Fase E (16/10): o || dentro de loop com o CONTINUE-JOIN
        // COMPARTILHADO fatia LANÇADA — o alvo do falso real do || = a
        // CONTINUAÇÃO do loop (os preds = os testes da cadeia + a QUEDA do
        // then; o discriminador = o pred da queda) → if-SEM-ELSE e o join
        // (o iinc compartilhado) anda DEPOIS, fora do if; o else não
        // re-emite. Os shapes: as INICIAIS com o || de `i == 0 || charAt(
        // i-1) == ' '` (o isub no índice + o char contra const), o palíndromo
        // com o while de 2 ponteiros e o early-false, o repeat com o
        // acumulador de concat no for e o join de 3 com o sep. Zero-drift
        // executado: a-b-c/ababab/true/false/amt/g — 6/6 idênticos ao Java.
        Path javaFile = dir.resolve("Text.java");
        Files.writeString(javaFile, """
                public class Text {
                    static String join(String sep, String a, String b, String c) {
                        return a + sep + b + sep + c;
                    }
                    static String repeat(String s, int n) {
                        String r = "";
                        for (int i = 0; i < n; i++) {
                            r = r + s;
                        }
                        return r;
                    }
                    static boolean isPalindrome(String s) {
                        int i = 0;
                        int j = s.length() - 1;
                        while (i < j) {
                            if (s.charAt(i) != s.charAt(j)) { return false; }
                            i++;
                            j--;
                        }
                        return true;
                    }
                    static String initials(String name) {
                        String out = "";
                        for (int i = 0; i < name.length(); i++) {
                            if (i == 0 || name.charAt(i - 1) == ' ') {
                                out = out + name.charAt(i);
                            }
                        }
                        return out;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Text.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Text.join("-", "a", "b", "c"))
                    println(Text.repeat("ab", 3))
                    println(Text.isPalindrome("arara"))
                    println(Text.isPalindrome("java"))
                    println(Text.initials("alan mathison turing"))
                    println(Text.initials("guido"))
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
        // oracle Java: a-b-c | ababab | true | false | amt | g
        assertEquals("a-b-c\nababab\ntrue\nfalse\namt\ng", output,
                "zero-drift: join/repeat/isPalindrome/initials id\u00eanticos ao Java");
    }

    @Test
    void gradebookDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (16/10): o dom\u00ednio DIÁRIO — 4 shapes sem código novo: a
        // MÉDIA com o ternário de guarda (vazio → 0.0) + o cast (double)
        // sum/size, a LETRA com as CINCO faixas encadeadas (A/B/C/D/F), o
        // contador de abaixo-do-piso e o allPassed com o early-false.
        // Zero-drift executado: 79.0/0.0/A/B/C/D/F/1/true/true — 10/10
        // id\u00eanticos ao Java — a 23\u00aa valida\u00e7\u00e3o.
        Path javaFile = dir.resolve("Grade2.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Grade2 {
                    static double average(List<Integer> grades) {
                        int sum = 0;
                        for (int g : grades) {
                            sum += g;
                        }
                        return grades.isEmpty() ? 0 : (double) sum / grades.size();
                    }
                    static String letter(double avg) {
                        if (avg >= 90) { return "A"; }
                        if (avg >= 80) { return "B"; }
                        if (avg >= 70) { return "C"; }
                        if (avg >= 60) { return "D"; }
                        return "F";
                    }
                    static int belowCount(List<Integer> grades, int floor) {
                        int n = 0;
                        for (int g : grades) {
                            if (g < floor) { n++; }
                        }
                        return n;
                    }
                    static boolean allPassed(List<Integer> grades) {
                        for (int g : grades) {
                            if (g < 60) { return false; }
                        }
                        return true;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Grade2.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    var gs = listOf(95, 88, 72, 61)
                    println(Grade2.average(gs))
                    println(Grade2.average(listOf()))
                    println(Grade2.letter(92.0))
                    println(Grade2.letter(85.0))
                    println(Grade2.letter(75.0))
                    println(Grade2.letter(65.0))
                    println(Grade2.letter(40.0))
                    println(Grade2.belowCount(gs, 70))
                    println(Grade2.allPassed(gs))
                    println(Grade2.allPassed(listOf(75, 90)))
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
        // oracle Java: 79.0 | 0.0 | A | B | C | D | F | 1 | true | true
        assertEquals("79.0\n0.0\nA\nB\nC\nD\nF\n1\ntrue\ntrue", output,
                "zero-drift: average/letter/belowCount/allPassed id\u00eanticos ao Java");
    }

    @Test
    void tripDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (16/10): o dom\u00ednio VIAGEM — 4 shapes sem c\u00f3digo novo: o
        // custo com a cadeia de ddiv/dmul (fuelCost), a ETA com o local
        // intermediário + TR\u00caS faixas, o WHILE com a SUBTRA\u00c7\u00c3O
        // decrementante (stops: `left = left - range` com o contador) e o
        // needsPassport com o contains + a NEGA\u00c7\u00c3O. Zero-drift
        // executado: 137.5/less-than-an-hour/same-day/overnight/3/0/true/
        // false — 8/8 id\u00eanticos ao Java — a 24\u00aa valida\u00e7\u00e3o
        // zero-c\u00f3digo.
        Path javaFile = dir.resolve("Trip.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Trip {
                    static double fuelCost(double km, double kmPerLiter, double pricePerLiter) {
                        return km / kmPerLiter * pricePerLiter;
                    }
                    static String eta(double km, double kmPerHour) {
                        double hours = km / kmPerHour;
                        if (hours < 1) { return "less than an hour"; }
                        if (hours < 5) { return "same day"; }
                        return "overnight";
                    }
                    static int stops(double km, double range) {
                        int n = 0;
                        double left = km;
                        while (left > range) {
                            left = left - range;
                            n++;
                        }
                        return n;
                    }
                    static boolean needsPassport(String country, List<String> domestic) {
                        return !domestic.contains(country);
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Trip.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Trip.fuelCost(300.0, 12.0, 5.5))
                    println(Trip.eta(40.0, 60.0))
                    println(Trip.eta(200.0, 60.0))
                    println(Trip.eta(600.0, 60.0))
                    println(Trip.stops(350.0, 100.0))
                    println(Trip.stops(90.0, 100.0))
                    var domestic = listOf("BR", "UY")
                    println(Trip.needsPassport("AR", domestic))
                    println(Trip.needsPassport("BR", domestic))
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
        // oracle Java: 137.5 | less than an hour | same day | overnight | 3 | 0 | true | false
        assertEquals("137.5\nless than an hour\nsame day\novernight\n3\n0\ntrue\nfalse", output,
                "zero-drift: fuelCost/eta/stops/needsPassport id\u00eanticos ao Java");
    }

    @Test
    void chatDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (16/10): o dom\u00ednio CHAT — 4 shapes sem c\u00f3digo novo: o
        // unread com o contains + a NEGA\u00c7\u00c3O dentro do for-each duplo
        // (o seen contra o all), o PREVIEW com o corte + o sufixo "..."
        // (o if-simples + a concat no return), o mentions com o EARLY-TRUE
        // (o contains dentro do loop) e o charCount com o ACUMULADOR Long
        // (o ladd no for-each de Strings). Zero-drift executado: 2/ola
        // mund.../curto/true/false/10 — 6/6 id\u00eanticos ao Java — a 25\u00aa
        // valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Chat.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Chat {
                    static int unread(List<String> seen, List<String> all) {
                        int n = 0;
                        for (String m : all) {
                            if (!seen.contains(m)) { n++; }
                        }
                        return n;
                    }
                    static String preview(String msg, int max) {
                        if (msg.length() <= max) { return msg; }
                        return msg.substring(0, max) + "...";
                    }
                    static boolean mentions(String msg, List<String> names) {
                        for (String name : names) {
                            if (msg.contains(name)) { return true; }
                        }
                        return false;
                    }
                    static long charCount(List<String> msgs) {
                        long total = 0;
                        for (String m : msgs) {
                            total += m.length();
                        }
                        return total;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Chat.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    var seen = listOf("oi", "tchau")
                    var all = listOf("oi", "tchau", "e ai", "bom dia")
                    println(Chat.unread(seen, all))
                    println(Chat.preview("ola mundo tudo bem", 8))
                    println(Chat.preview("curto", 8))
                    println(Chat.mentions("oi joao", listOf("maria", "joao")))
                    println(Chat.mentions("oi ze", listOf("maria", "joao")))
                    var msgs = listOf("abc", "de", "fghij")
                    println(Chat.charCount(msgs))
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
        // oracle Java: 2 | ola mund... | curto | true | false | 10
        assertEquals("2\nola mund...\ncurto\ntrue\nfalse\n10", output,
                "zero-drift: unread/preview/mentions/charCount id\u00eanticos ao Java");
    }

    @Test
    void matrix3DomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (16/10): o dom\u00ednio MATRIZ-3 — 4 shapes sem c\u00f3digo novo: a
        // DIAGONAL com o `m[i][i]` no for contado, o rowSums com o LOOP
        // ANINHADO completo + o array de sa\u00edda preenchido por \u00edndice
        // (`out[i] = r`), o isSquare com o early-false do comprimento e o
        // MAX com o best inicializado + o aninhado. Zero-drift executado:
        // 16/2/5/9/true/9 — 6/6 id\u00eanticos ao Java — a 26\u00aa valida\u00e7\u00e3o
        // zero-c\u00f3digo.
        Path javaFile = dir.resolve("Matrix3.java");
        Files.writeString(javaFile, """
                public class Matrix3 {
                    static int diagonalSum(int[][] m) {
                        int sum = 0;
                        for (int i = 0; i < m.length; i++) {
                            sum += m[i][i];
                        }
                        return sum;
                    }
                    static int[] rowSums(int[][] m) {
                        int[] out = new int[m.length];
                        for (int i = 0; i < m.length; i++) {
                            int r = 0;
                            for (int j = 0; j < m[i].length; j++) {
                                r += m[i][j];
                            }
                            out[i] = r;
                        }
                        return out;
                    }
                    static boolean isSquare(int[][] m) {
                        for (int i = 0; i < m.length; i++) {
                            if (m[i].length != m.length) { return false; }
                        }
                        return true;
                    }
                    static int max(int[][] m) {
                        int best = m[0][0];
                        for (int i = 0; i < m.length; i++) {
                            for (int j = 0; j < m[i].length; j++) {
                                if (m[i][j] > best) { best = m[i][j]; }
                            }
                        }
                        return best;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Matrix3.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    var m = new Int[3][0]
                    var r0 = new Int[3]
                    r0[0] = 2; r0[1] = 0; r0[2] = 0
                    var r1 = new Int[3]
                    r1[0] = 0; r1[1] = 5; r1[2] = 0
                    var r2 = new Int[3]
                    r2[0] = 0; r2[1] = 0; r2[2] = 9
                    m[0] = r0; m[1] = r1; m[2] = r2
                    println(Matrix3.diagonalSum(m))
                    var rs = Matrix3.rowSums(m)
                    for (var r in rs) { println(r) }
                    println(Matrix3.isSquare(m))
                    println(Matrix3.max(m))
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
        // oracle Java: 16 | 2 | 5 | 9 | true | 9
        assertEquals("16\n2\n5\n9\ntrue\n9", output,
                "zero-drift: diagonalSum/rowSums/isSquare/max id\u00eanticos ao Java");
    }

    @Test
    void payrollDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (16/10): o dom\u00ednio FOLHA — 4 shapes sem c\u00f3digo novo: o
        // l\u00edquido com a álgebra de Double (netSalary: `gross * (1 -
        // taxRate)` + o benefício), as QUATRO faixas salariais (bracket), o
        // bônus com o ACUMULADOR Double no for-each (a fatia dos stores
        // wide por trás) e o deservesRaise com o && de comparações MISTAS
        // (o `years >= 2` inteiro contra o `score >= 4.0` double).
        // Zero-drift executado: 4300.0/exempt/low/mid/high/350.0/true/
        // false/false — 9/9 id\u00eanticos ao Java — a 27\u00aa valida\u00e7\u00e3o
        // zero-c\u00f3digo.
        Path javaFile = dir.resolve("Payroll.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Payroll {
                    static double netSalary(double gross, double taxRate, double benefits) {
                        double taxed = gross * (1 - taxRate);
                        return taxed + benefits;
                    }
                    static String bracket(double salary) {
                        if (salary < 2000) { return "exempt"; }
                        if (salary < 5000) { return "low"; }
                        if (salary < 10000) { return "mid"; }
                        return "high";
                    }
                    static double bonus(List<Double> sales, double rate) {
                        double total = 0;
                        for (double s : sales) {
                            total += s;
                        }
                        return total * rate;
                    }
                    static boolean deservesRaise(int years, double score) {
                        return years >= 2 && score >= 4.0;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Payroll.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Payroll.netSalary(5000.0, 0.2, 300.0))
                    println(Payroll.bracket(1500.0))
                    println(Payroll.bracket(3500.0))
                    println(Payroll.bracket(8000.0))
                    println(Payroll.bracket(12000.0))
                    var sales = listOf(1000.0, 2000.0, 500.0)
                    println(Payroll.bonus(sales, 0.1))
                    println(Payroll.deservesRaise(3, 4.5))
                    println(Payroll.deservesRaise(1, 4.5))
                    println(Payroll.deservesRaise(3, 3.5))
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
        // oracle Java: 4300.0 | exempt | low | mid | high | 350.0 | true | false | false
        assertEquals("4300.0\nexempt\nlow\nmid\nhigh\n350.0\ntrue\nfalse\nfalse", output,
                "zero-drift: netSalary/bracket/bonus/deservesRaise id\u00eanticos ao Java");
    }

    @Test
    void clockDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (16/10): o dom\u00ednio REL\u00d3GIO — 4 shapes sem c\u00f3digo novo:
        // a conversão com a ARITMÉTICA ENCADEADA (toSeconds: `h * 3600 +
        // m * 60 + s`), o período com as QUATRO faixas (dawn/morning/
        // afternoon/night), o addMinutes com o wrap do módulo (`total / 60
        // % 24` — o encadeamento de div+rem) e o isBusinessDay com o &&
        // TRIPLO (a NEGAÇÃO + as duas comparações). Zero-drift executado:
        // 3723/dawn/morning/afternoon/night/0/10/true/false/false — 10/10
        // id\u00eanticos ao Java — a 28\u00aa valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Time.java");
        Files.writeString(javaFile, """
                public class Time {
                    static int toSeconds(int h, int m, int s) {
                        return h * 3600 + m * 60 + s;
                    }
                    static String period(int hour) {
                        if (hour < 6) { return "dawn"; }
                        if (hour < 12) { return "morning"; }
                        if (hour < 18) { return "afternoon"; }
                        return "night";
                    }
                    static int addMinutes(int h, int m, int add) {
                        int total = h * 60 + m + add;
                        return total / 60 % 24;
                    }
                    static boolean isBusinessDay(int day, boolean holiday) {
                        return !holiday && day >= 2 && day <= 6;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Time.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Time.toSeconds(1, 2, 3))
                    println(Time.period(4))
                    println(Time.period(9))
                    println(Time.period(15))
                    println(Time.period(23))
                    println(Time.addMinutes(23, 50, 20))
                    println(Time.addMinutes(10, 10, 5))
                    println(Time.isBusinessDay(3, false))
                    println(Time.isBusinessDay(1, false))
                    println(Time.isBusinessDay(3, true))
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
        // oracle Java: 3723 | dawn | morning | afternoon | night | 0 | 10 | true | false | false
        assertEquals("3723\ndawn\nmorning\nafternoon\nnight\n0\n10\ntrue\nfalse\nfalse", output,
                "zero-drift: toSeconds/period/addMinutes/isBusinessDay id\u00eanticos ao Java");
    }

    @Test
    void votingDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (16/10): o dom\u00ednio VOTA\u00c7\u00c3O — 4 shapes sem c\u00f3digo novo:
        // o contador de votos com o equals no for-each, o canVote com o
        // gate do early-false + o return do boolean param, o runoff com as
        // DUAS calls ANTES do if (o reuso dos resultados como locais) e o
        // turnout com a PROMO\u00c7\u00c3O int→double no multiplicador (`votes *
        // 100.0 / eligible`). Zero-drift executado: 3/0/false/true/false/
        // ana/68.0 — 7/7 id\u00eanticos ao Java — a 29\u00aa valida\u00e7\u00e3o
        // zero-c\u00f3digo.
        Path javaFile = dir.resolve("Voting.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Voting {
                    static int countVotes(List<String> ballots, String cand) {
                        int n = 0;
                        for (String b : ballots) {
                            if (b.equals(cand)) { n++; }
                        }
                        return n;
                    }
                    static boolean canVote(int age, boolean registered) {
                        if (age < 18) { return false; }
                        return registered;
                    }
                    static String runoff(String a, String b, List<String> ballots) {
                        int va = countVotes(ballots, a);
                        int vb = countVotes(ballots, b);
                        if (va > vb) { return a; }
                        if (vb > va) { return b; }
                        return a;
                    }
                    static double turnout(int votes, int eligible) {
                        return votes * 100.0 / eligible;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Voting.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    var bs = listOf("ana", "bia", "ana", "ana", "bia")
                    println(Voting.countVotes(bs, "ana"))
                    println(Voting.countVotes(bs, "caio"))
                    println(Voting.canVote(17, true))
                    println(Voting.canVote(25, true))
                    println(Voting.canVote(25, false))
                    println(Voting.runoff("ana", "bia", bs))
                    println(Voting.turnout(340, 500))
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
        // oracle Java: 3 | 0 | false | true | false | ana | 68.0
        assertEquals("3\n0\nfalse\ntrue\nfalse\nana\n68.0", output,
                "zero-drift: countVotes/canVote/runoff/turnout id\u00eanticos ao Java");
    }

    @Test
    void weatherDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (16/10): o dom\u00ednio CLIMA — 4 shapes sem c\u00f3digo novo: a
        // média com o ACUMULADOR Double + o ternário de guarda (vazio →
        // 0.0), o alerta com as DUAS faixas opostas (freezing/hot/normal),
        // o hotDays com o contador de threshold e o willRain com a
        // comparação direta no return. Zero-drift executado: 21.9/0.0/
        // freezing/normal/hot/2/true/false — 8/8 id\u00eanticos ao Java —
        // a 30\u00aa valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Weather.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Weather {
                    static double averageTemp(List<Double> temps) {
                        double sum = 0;
                        for (double t : temps) {
                            sum += t;
                        }
                        return temps.isEmpty() ? 0 : sum / temps.size();
                    }
                    static String alert(double temp) {
                        if (temp < 0) { return "freezing"; }
                        if (temp > 35) { return "hot"; }
                        return "normal";
                    }
                    static int hotDays(List<Double> temps, double threshold) {
                        int n = 0;
                        for (double t : temps) {
                            if (t > threshold) { n++; }
                        }
                        return n;
                    }
                    static boolean willRain(int humidity) {
                        return humidity > 80;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Weather.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    var ts = listOf(22.0, 31.5, -2.0, 40.0, 18.0)
                    println(Weather.averageTemp(ts))
                    println(Weather.averageTemp(listOf()))
                    println(Weather.alert(-5.0))
                    println(Weather.alert(22.0))
                    println(Weather.alert(38.0))
                    println(Weather.hotDays(ts, 30.0))
                    println(Weather.willRain(85))
                    println(Weather.willRain(40))
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
        // oracle Java: 21.9 | 0.0 | freezing | normal | hot | 2 | true | false
        assertEquals("21.9\n0.0\nfreezing\nnormal\nhot\n2\ntrue\nfalse", output,
                "zero-drift: averageTemp/alert/hotDays/willRain id\u00eanticos ao Java");
    }

    @Test
    void lockDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (16/10): o dom\u00ednio COFE — 4 shapes sem c\u00f3digo novo: o
        // tryOpen com o gate do boolean + as TR\u00caS sa\u00eddas, o code com a
        // combinação posicional (`a * 100 + b * 10 + c`), o matches com o
        // WHILE de DOIS decrementos paralelos (os d\u00edgitos por div/10 +
        // o early-false no %10) e o attemptsLeft com a subtração direta.
        // Zero-drift executado: open/locked/waiting/472/true/false/false/4
        // — 8/8 id\u00eanticos ao Java — a 31\u00aa valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Lock.java");
        Files.writeString(javaFile, """
                public class Lock {
                    static String tryOpen(int attempts, int max, boolean keyFound) {
                        if (keyFound) { return "open"; }
                        if (attempts >= max) { return "locked"; }
                        return "waiting";
                    }
                    static int code(int a, int b, int c) {
                        return a * 100 + b * 10 + c;
                    }
                    static boolean matches(int guess, int secret) {
                        int digits = 0;
                        int g = guess;
                        int s = secret;
                        while (g > 0 && s > 0) {
                            if (g % 10 != s % 10) { return false; }
                            g = g / 10;
                            s = s / 10;
                            digits++;
                        }
                        return digits == 3;
                    }
                    static int attemptsLeft(int used, int max) {
                        return max - used;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Lock.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Lock.tryOpen(2, 3, true))
                    println(Lock.tryOpen(3, 3, false))
                    println(Lock.tryOpen(1, 3, false))
                    println(Lock.code(4, 7, 2))
                    println(Lock.matches(123, 123))
                    println(Lock.matches(125, 123))
                    println(Lock.matches(12, 123))
                    println(Lock.attemptsLeft(1, 5))
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
        // oracle Java: open | locked | waiting | 472 | true | false | false | 4
        assertEquals("open\nlocked\nwaiting\n472\ntrue\nfalse\nfalse\n4", output,
                "zero-drift: tryOpen/code/matches/attemptsLeft id\u00eanticos ao Java");
    }

    @Test
    void recipeDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (16/10): o dom\u00ednio RECEITA — 4 shapes sem c\u00f3digo novo: o
        // tempo com a soma direta, o spiceLevel com as faixas ==/<=
        // (mild/medium/hot/inferno), o vegetarian com o || de equals DENTRO
        // do early-false (`ing.equals("chicken") || ing.equals("beef")` —
        // a cadeia || com o return antecipado) e o scale com a regra de
        // três (`amount * target / servings`). Zero-drift executado: 55/
        // mild/medium/hot/inferno/true/false/300.0 — 8/8 id\u00eanticos ao
        // Java — a 32\u00aa valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Recipe.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Recipe {
                    static int totalTime(int prep, int cook) {
                        return prep + cook;
                    }
                    static String spiceLevel(int peppers) {
                        if (peppers == 0) { return "mild"; }
                        if (peppers <= 2) { return "medium"; }
                        if (peppers <= 5) { return "hot"; }
                        return "inferno";
                    }
                    static boolean vegetarian(List<String> ingredients) {
                        for (String ing : ingredients) {
                            if (ing.equals("chicken") || ing.equals("beef")) { return false; }
                        }
                        return true;
                    }
                    static double scale(double amount, int servings, int target) {
                        return amount * target / servings;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Recipe.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Recipe.totalTime(15, 40))
                    println(Recipe.spiceLevel(0))
                    println(Recipe.spiceLevel(2))
                    println(Recipe.spiceLevel(4))
                    println(Recipe.spiceLevel(9))
                    var ings = listOf("rice", "beans", "salad")
                    var meat = listOf("rice", "chicken")
                    println(Recipe.vegetarian(ings))
                    println(Recipe.vegetarian(meat))
                    println(Recipe.scale(200.0, 4, 6))
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
        // oracle Java: 55 | mild | medium | hot | inferno | true | false | 300.0
        assertEquals("55\nmild\nmedium\nhot\ninferno\ntrue\nfalse\n300.0", output,
                "zero-drift: totalTime/spiceLevel/vegetarian/scale id\u00eanticos ao Java");
    }

    @Test
    void logDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (16/10): o dom\u00ednio LOG — 4 shapes sem c\u00f3digo novo: o
        // errorCount com o startsWith no for-each, o hasFatal com o
        // EARLY-TRUE, o firstLine com o guard do isEmpty (o <empty> no
        // vazio) e o join com o acumulador de concat + o \u005c\u005cn
        // literal (o newline final PRESERVADO byte a byte). Zero-drift
        // executado: 2/false/true/INFO ok/<empty>/a\u005cnb\u005cn (len 4) —
        // id\u00eanticos ao Java — a 33\u00aa valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Log.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Log {
                    static int errorCount(List<String> lines) {
                        int n = 0;
                        for (String line : lines) {
                            if (line.startsWith("ERROR")) { n++; }
                        }
                        return n;
                    }
                    static boolean hasFatal(List<String> lines) {
                        for (String line : lines) {
                            if (line.startsWith("FATAL")) { return true; }
                        }
                        return false;
                    }
                    static String firstLine(List<String> lines) {
                        if (lines.isEmpty()) { return "<empty>"; }
                        return lines.get(0);
                    }
                    static String join(List<String> lines) {
                        String out = "";
                        for (String line : lines) {
                            out = out + line + "\\n";
                        }
                        return out;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Log.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    var lines = listOf("INFO ok", "ERROR falhou", "INFO de novo", "ERROR outra vez")
                    println(Log.errorCount(lines))
                    println(Log.hasFatal(lines))
                    println(Log.hasFatal(listOf("INFO a", "FATAL x")))
                    println(Log.firstLine(lines))
                    println(Log.firstLine(listOf()))
                    var j = Log.join(listOf("a", "b"))
                    println(j.length())
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
        // oracle Java: 2 | false | true | INFO ok | <empty> | 4 (o join =
        // "a\nb\n", o newline final preservado)
        assertEquals("2\nfalse\ntrue\nINFO ok\n<empty>\n4", output,
                "zero-drift: errorCount/hasFatal/firstLine/join id\u00eanticos ao Java");
    }

    @Test
    void fitnessDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (16/10): o dom\u00ednio FITNESS — 4 shapes sem c\u00f3digo novo: o
        // IMC com o denominador parentesado (bmi: `weight / (h * h)`), as
        // QUATRO faixas de categoria (under/normal/over/obese), as
        // calorias com o CAST d2i no return (`(int) (minutes * rate)`) e
        // o hitGoal com a comparação direta. Zero-drift executado:
        // 22.857142857142858/under/normal/over/obese/225/true/false — 8/8
        // id\u00eanticos ao Java — a 34\u00aa valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Fit.java");
        Files.writeString(javaFile, """
                public class Fit {
                    static double bmi(double weightKg, double heightM) {
                        return weightKg / (heightM * heightM);
                    }
                    static String category(double bmi) {
                        if (bmi < 18.5) { return "under"; }
                        if (bmi < 25) { return "normal"; }
                        if (bmi < 30) { return "over"; }
                        return "obese";
                    }
                    static int caloriesBurned(int minutes, double rate) {
                        return (int) (minutes * rate);
                    }
                    static boolean hitGoal(int steps, int goal) {
                        return steps >= goal;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Fit.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Fit.bmi(70.0, 1.75))
                    println(Fit.category(17.0))
                    println(Fit.category(22.0))
                    println(Fit.category(27.0))
                    println(Fit.category(33.0))
                    println(Fit.caloriesBurned(30, 7.5))
                    println(Fit.hitGoal(11000, 10000))
                    println(Fit.hitGoal(9000, 10000))
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
        // oracle Java: 22.857142857142858 | under | normal | over | obese | 225 | true | false
        assertEquals("22.857142857142858\nunder\nnormal\nover\nobese\n225\ntrue\nfalse", output,
                "zero-drift: bmi/category/caloriesBurned/hitGoal id\u00eanticos ao Java");
    }

    @Test
    void orderDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (16/10): o dom\u00ednio PEDIDO — 4 shapes sem c\u00f3digo novo: o
        // total com o acumulador + o desconto algébrico (`sum * (1 -
        // discount)`), o status com as faixas ==/> (empty/bulk/standard),
        // o freeShipping com o gate do early-true + o return do boolean
        // param e o itemCount com o equals contra um PARAM (o sku
        // dinâmico). Zero-drift executado: 90.0/empty/bulk/standard/true/
        // true/false/2/0 — 9/9 id\u00eanticos ao Java — a 35\u00aa valida\u00e7\u00e3o
        // zero-c\u00f3digo.
        Path javaFile = dir.resolve("Order.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Order {
                    static double total(List<Double> prices, double discount) {
                        double sum = 0;
                        for (double p : prices) {
                            sum += p;
                        }
                        return sum * (1 - discount);
                    }
                    static String status(int items) {
                        if (items == 0) { return "empty"; }
                        if (items > 20) { return "bulk"; }
                        return "standard";
                    }
                    static boolean freeShipping(double total, boolean member) {
                        if (total >= 100) { return true; }
                        return member;
                    }
                    static int itemCount(List<String> skus, String sku) {
                        int n = 0;
                        for (String s : skus) {
                            if (s.equals(sku)) { n++; }
                        }
                        return n;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Order.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    var prices = listOf(50.0, 30.0, 20.0)
                    println(Order.total(prices, 0.1))
                    println(Order.status(0))
                    println(Order.status(25))
                    println(Order.status(3))
                    println(Order.freeShipping(150.0, false))
                    println(Order.freeShipping(50.0, true))
                    println(Order.freeShipping(50.0, false))
                    var skus = listOf("a", "b", "a", "c")
                    println(Order.itemCount(skus, "a"))
                    println(Order.itemCount(skus, "z"))
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
        // oracle Java: 90.0 | empty | bulk | standard | true | true | false | 2 | 0
        assertEquals("90.0\nempty\nbulk\nstandard\ntrue\ntrue\nfalse\n2\n0", output,
                "zero-drift: total/status/freeShipping/itemCount id\u00eanticos ao Java");
    }

    @Test
    void gameDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (16/10): o dom\u00ednio JOGO — 4 shapes sem c\u00f3digo novo: o
        // cr\u00edtico com o == no return, o damage com o CAST d2i
        // BRANCH-DEPENDENTE (os DOIS returns com casts, o cr\u00edtico
        // multiplicando x2), o rank com as QUATRO faixas e o canLevel com
        // a comparação contra a PRODUTORIA (`xp >= level * 100` — a
        // multiplicação no lado direito). Zero-drift executado: true/
        // false/30/15/legend/master/novice/beginner/true/false — 10/10
        // id\u00eanticos ao Java — a 36\u00aa valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Game.java");
        Files.writeString(javaFile, """
                public class Game {
                    static boolean isCritical(int roll) {
                        return roll == 20;
                    }
                    static int damage(int base, double multiplier, boolean crit) {
                        if (crit) { return (int) (base * multiplier * 2); }
                        return (int) (base * multiplier);
                    }
                    static String rank(int score) {
                        if (score >= 1000) { return "legend"; }
                        if (score >= 500) { return "master"; }
                        if (score >= 100) { return "novice"; }
                        return "beginner";
                    }
                    static boolean canLevel(int xp, int level) {
                        return xp >= level * 100;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Game.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Game.isCritical(20))
                    println(Game.isCritical(18))
                    println(Game.damage(10, 1.5, true))
                    println(Game.damage(10, 1.5, false))
                    println(Game.rank(1200))
                    println(Game.rank(700))
                    println(Game.rank(150))
                    println(Game.rank(50))
                    println(Game.canLevel(350, 3))
                    println(Game.canLevel(250, 3))
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
        // oracle Java: true | false | 30 | 15 | legend | master | novice | beginner | true | false
        assertEquals("true\nfalse\n30\n15\nlegend\nmaster\nnovice\nbeginner\ntrue\nfalse", output,
                "zero-drift: isCritical/damage/rank/canLevel id\u00eanticos ao Java");
    }

    @Test
    void docDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (16/10): o dom\u00ednio DOC — 4 shapes sem c\u00f3digo novo: o
        // wordCount com a MÁQUINA DE ESTADOS de char (o boolean inWord + o
        // if/else-if dentro do loop de charAt), o firstWord com o while de
        // CONDIÇÃO COMPOSTA + o substring, o isUpper com as CALLS
        // ENCADEADAS no return (equals(toUpperCase())) e o countChar com o
        // char PARAM na comparação. Zero-drift executado: 4/1/ola/so/true/
        // false/3/0 — 8/8 id\u00eanticos ao Java — a 37\u00aa valida\u00e7\u00e3o
        // zero-c\u00f3digo.
        Path javaFile = dir.resolve("Doc.java");
        Files.writeString(javaFile, """
                public class Doc {
                    static int wordCount(String text) {
                        int n = 0;
                        boolean inWord = false;
                        for (int i = 0; i < text.length(); i++) {
                            char c = text.charAt(i);
                            if (c == ' ') {
                                inWord = false;
                            } else if (!inWord) {
                                n++;
                                inWord = true;
                            }
                        }
                        return n;
                    }
                    static String firstWord(String text) {
                        int end = 0;
                        while (end < text.length() && text.charAt(end) != ' ') {
                            end++;
                        }
                        return text.substring(0, end);
                    }
                    static boolean isUpper(String tag) {
                        return tag.equals(tag.toUpperCase());
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
        String kof = Decompile.decompile(dir.resolve("Doc.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Doc.wordCount("um dois tres quatro"))
                    println(Doc.wordCount("unico"))
                    println(Doc.firstWord("ola mundo"))
                    println(Doc.firstWord("so"))
                    println(Doc.isUpper("ABC"))
                    println(Doc.isUpper("AbC"))
                    println(Doc.countChar("banana", 'a'))
                    println(Doc.countChar("teste", 'z'))
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
        // oracle Java: 4 | 1 | ola | so | true | false | 3 | 0
        assertEquals("4\n1\nola\nso\ntrue\nfalse\n3\n0", output,
                "zero-drift: wordCount/firstWord/isUpper/countChar id\u00eanticos ao Java");
    }

    @Test
    void parallelListsDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (16/10): o dom\u00ednio LISTAS-PARALELAS — 4 shapes sem
        // c\u00f3digo novo: o subtotal com o for INDEXADO sobre DUAS listas
        // (`qtys.get(i) * prices.get(i)` — o get duplo no mesmo \u00edndice),
        // o cheapest com o tracking do melhor \u00cdNDICE (`prices.get(i) <
        // prices.get(best)` — o get cruzado) + a concat no return, o
        // inStock com a comparação direta e o shipping com o zero literal
        // no early-return. Zero-drift executado: 57.0/item 2/true/false/
        // 0.0/15.5 — 6/6 id\u00eanticos ao Java — a 38\u00aa valida\u00e7\u00e3o
        // zero-c\u00f3digo.
        Path javaFile = dir.resolve("Cart2.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Cart2 {
                    static double subtotal(List<Integer> qtys, List<Double> prices) {
                        double total = 0;
                        for (int i = 0; i < qtys.size(); i++) {
                            total += qtys.get(i) * prices.get(i);
                        }
                        return total;
                    }
                    static String cheapest(List<Double> prices) {
                        int best = 0;
                        for (int i = 1; i < prices.size(); i++) {
                            if (prices.get(i) < prices.get(best)) { best = i; }
                        }
                        return "item " + best;
                    }
                    static boolean inStock(int qty) {
                        return qty > 0;
                    }
                    static double shipping(double total) {
                        if (total >= 200) { return 0; }
                        return 15.5;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Cart2.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    var qtys = listOf(2, 1, 3)
                    var prices = listOf(10.0, 25.0, 4.0)
                    println(Cart2.subtotal(qtys, prices))
                    println(Cart2.cheapest(prices))
                    println(Cart2.inStock(5))
                    println(Cart2.inStock(0))
                    println(Cart2.shipping(250.0))
                    println(Cart2.shipping(100.0))
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
        // oracle Java: 57.0 | item 2 | true | false | 0.0 | 15.5
        assertEquals("57.0\nitem 2\ntrue\nfalse\n0.0\n15.5", output,
                "zero-drift: subtotal/cheapest/inStock/shipping id\u00eanticos ao Java");
    }

    @Test
    void creditDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (16/10): o dom\u00ednio CR\u00c9DITO + a fatia dos CONSTS WIDE —
        // o loadValue n\u00e3o cobria o lconst/fconst/dconst (0x09-0x0f): o
        // `while (left > 0)` com Double compila o header como [dload,
        // dconst_0, dcmpl, ifle] e o par-case recusava = cond-null (medido
        // no Bank2.monthsToPay). Os shapes: a taxa dobrada no negativo, o
        // riskLevel com a raz\u00e3o local + as faixas de double, o canLoan com
        // o && TRIPLO misto (boolean >= int >=) e o monthsToPay com o
        // while de subtra\u00e7\u00e3o. Zero-drift executado: -60.0/95.0/high/
        // medium/low/true/false/false/4/3 — 10/10 id\u00eanticos ao Java — a
        // 39\u00aa valida\u00e7\u00e3o.
        Path javaFile = dir.resolve("Bank2.java");
        Files.writeString(javaFile, """
                public class Bank2 {
                    static double applyFee(double balance, double fee) {
                        if (balance < 0) { return balance - fee * 2; }
                        return balance - fee;
                    }
                    static String riskLevel(double debt, double income) {
                        double ratio = debt / income;
                        if (ratio > 0.5) { return "high"; }
                        if (ratio > 0.2) { return "medium"; }
                        return "low";
                    }
                    static boolean canLoan(double income, int years, boolean employed) {
                        return employed && income >= 2000 && years >= 1;
                    }
                    static int monthsToPay(double debt, double payment) {
                        int m = 0;
                        double left = debt;
                        while (left > 0) {
                            left -= payment;
                            m++;
                        }
                        return m;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Bank2.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Bank2.applyFee(-50.0, 5.0))
                    println(Bank2.applyFee(100.0, 5.0))
                    println(Bank2.riskLevel(6000.0, 10000.0))
                    println(Bank2.riskLevel(2500.0, 10000.0))
                    println(Bank2.riskLevel(1000.0, 10000.0))
                    println(Bank2.canLoan(3000.0, 2, true))
                    println(Bank2.canLoan(3000.0, 2, false))
                    println(Bank2.canLoan(1000.0, 2, true))
                    println(Bank2.monthsToPay(100.0, 30.0))
                    println(Bank2.monthsToPay(90.0, 30.0))
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
        // oracle Java: -60.0 | 95.0 | high | medium | low | true | false | false | 4 | 3
        assertEquals("-60.0\n95.0\nhigh\nmedium\nlow\ntrue\nfalse\nfalse\n4\n3", output,
                "zero-drift: applyFee/riskLevel/canLoan/monthsToPay id\u00eanticos ao Java");
    }

    @Test
    void pizzaDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (16/10): o dom\u00ednio PIZZA — 4 shapes sem c\u00f3digo novo: o
        // preço com o ACUMULADOR RE-ATRIBUÍDO no for-each (`base = base +
        // 2.5` — a forma explícita, não o +=), o sizeName com as faixas ==
        // (small/medium/large), o isVegetarian com o && de NEGAÇÕES no
        // return e o slicesFor com a multiplicação direta. Zero-drift
        // executado: 17.5/5.0/small/medium/large/true/false/12 — 8/8
        // id\u00eanticos ao Java — a 40\u00aa valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Pizza.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Pizza {
                    static double price(int size, List<String> toppings) {
                        double base = size * 5.0;
                        for (String t : toppings) {
                            base = base + 2.5;
                        }
                        return base;
                    }
                    static String sizeName(int size) {
                        if (size == 1) { return "small"; }
                        if (size == 2) { return "medium"; }
                        return "large";
                    }
                    static boolean isVegetarian(List<String> toppings) {
                        return !toppings.contains("pepperoni") && !toppings.contains("ham");
                    }
                    static int slicesFor(int people) {
                        return people * 3;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Pizza.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    var tops = listOf("queijo", "milho", "catupiry")
                    println(Pizza.price(2, tops))
                    println(Pizza.price(1, listOf()))
                    println(Pizza.sizeName(1))
                    println(Pizza.sizeName(2))
                    println(Pizza.sizeName(3))
                    println(Pizza.isVegetarian(tops))
                    println(Pizza.isVegetarian(listOf("queijo", "ham")))
                    println(Pizza.slicesFor(4))
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
        // oracle Java: 17.5 | 5.0 | small | medium | large | true | false | 12
        assertEquals("17.5\n5.0\nsmall\nmedium\nlarge\ntrue\nfalse\n12", output,
                "zero-drift: price/sizeName/isVegetarian/slicesFor id\u00eanticos ao Java");
    }

    @Test
    void textProcessDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (16/10): o dom\u00ednio TEXTO-PROC — 4 shapes sem c\u00f3digo novo: o
        // longest com a compara\u00e7\u00e3o CALL-vs-CALL (`w.length() >
        // best.length()` — os DOIS lados sendo chamadas), o totalLength com
        // o contador de lengths no for-each, o allShort com o early-false
        // de threshold e o initials-v2 com a MÁQUINA DE ESTADOS invertida
        // (o newWord setado no espaço e consumido no else-if). Zero-drift
        // executado: jardim/13/true/false/amt — 5/5 id\u00eanticos ao Java —
        // a 41\u00aa valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Stream.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Stream {
                    static String longest(List<String> words) {
                        String best = "";
                        for (String w : words) {
                            if (w.length() > best.length()) { best = w; }
                        }
                        return best;
                    }
                    static int totalLength(List<String> words) {
                        int n = 0;
                        for (String w : words) {
                            n += w.length();
                        }
                        return n;
                    }
                    static boolean allShort(List<String> words, int max) {
                        for (String w : words) {
                            if (w.length() > max) { return false; }
                        }
                        return true;
                    }
                    static String initials(String phrase) {
                        String out = "";
                        boolean newWord = true;
                        for (int i = 0; i < phrase.length(); i++) {
                            char c = phrase.charAt(i);
                            if (c == ' ') {
                                newWord = true;
                            } else if (newWord) {
                                out = out + c;
                                newWord = false;
                            }
                        }
                        return out;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Stream.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    var ws = listOf("casa", "jardim", "sol")
                    println(Stream.longest(ws))
                    println(Stream.totalLength(ws))
                    println(Stream.allShort(ws, 7))
                    println(Stream.allShort(ws, 4))
                    println(Stream.initials("alan mathison turing"))
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
        // oracle Java: jardim | 13 | true | false | amt
        assertEquals("jardim\n13\ntrue\nfalse\namt", output,
                "zero-drift: longest/totalLength/allShort/initials id\u00eanticos ao Java");
    }

    @Test
    void investDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (16/10): o dom\u00ednio INVESTIMENTO — 4 shapes sem c\u00f3digo
        // novo: o juros compostos com o for de re-atribuição
        // (`balance = balance * (1 + rate)`), o profitable com a comparação
        // direta, o yearsToDouble com o WHILE contra a PRODUTÓRIA
        // (`balance < principal * 2` — o threshold calculado no header) e
        // o fee com as duas taxas por braço. Zero-drift executado:
        // 1331.0/true/false/8/5.0/10.0 — 6/6 id\u00eanticos ao Java — a
        // 42\u00aa valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Invest.java");
        Files.writeString(javaFile, """
                public class Invest {
                    static double compoundInterest(double principal, double rate, int years) {
                        double balance = principal;
                        for (int i = 0; i < years; i++) {
                            balance = balance * (1 + rate);
                        }
                        return balance;
                    }
                    static boolean profitable(double initial, double current) {
                        return current > initial;
                    }
                    static int yearsToDouble(double principal, double rate) {
                        int years = 0;
                        double balance = principal;
                        while (balance < principal * 2) {
                            balance = balance * (1 + rate);
                            years++;
                        }
                        return years;
                    }
                    static double fee(double amount, boolean premium) {
                        if (premium) { return amount * 0.005; }
                        return amount * 0.01;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Invest.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Invest.compoundInterest(1000.0, 0.1, 3))
                    println(Invest.profitable(1000.0, 1200.0))
                    println(Invest.profitable(1000.0, 900.0))
                    println(Invest.yearsToDouble(1000.0, 0.1))
                    println(Invest.fee(1000.0, true))
                    println(Invest.fee(1000.0, false))
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
        // oracle Java: 1331.0 | true | false | 8 | 5.0 | 10.0
        assertEquals("1331.0\ntrue\nfalse\n8\n5.0\n10.0", output,
                "zero-drift: compoundInterest/profitable/yearsToDouble/fee id\u00eanticos ao Java");
    }

    @Test
    void socialDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (16/10): o dom\u00ednio SOCIAL — 4 shapes sem c\u00f3digo novo: o
        // follows com o contains direto, o handle com a concat + o
        // toLowerCase no return, o isNew com o equals NEGADO e o
        // likesToLevel com as QUATRO faixas decrescentes (3/2/1/0).
        // Zero-drift executado: true/false/@maria/true/false/3/2/1/0 —
        // 9/9 id\u00eanticos ao Java — a 43\u00aa valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Social.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Social {
                    static boolean follows(List<String> myFollows, String user) {
                        return myFollows.contains(user);
                    }
                    static String handle(String name) {
                        return "@" + name.toLowerCase();
                    }
                    static boolean isNew(String post, String lastSeen) {
                        return !post.equals(lastSeen);
                    }
                    static int likesToLevel(int likes) {
                        if (likes >= 1000) { return 3; }
                        if (likes >= 100) { return 2; }
                        if (likes >= 10) { return 1; }
                        return 0;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Social.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    var follows = listOf("ana", "bia")
                    println(Social.follows(follows, "ana"))
                    println(Social.follows(follows, "caio"))
                    println(Social.handle("Maria"))
                    println(Social.isNew("post2", "post1"))
                    println(Social.isNew("post1", "post1"))
                    println(Social.likesToLevel(1500))
                    println(Social.likesToLevel(250))
                    println(Social.likesToLevel(15))
                    println(Social.likesToLevel(3))
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
        // oracle Java: true | false | @maria | true | false | 3 | 2 | 1 | 0
        assertEquals("true\nfalse\n@maria\ntrue\nfalse\n3\n2\n1\n0", output,
                "zero-drift: follows/handle/isNew/likesToLevel id\u00eanticos ao Java");
    }

    @Test
    void parkingDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (16/10): o dom\u00ednio ESTACIONAMENTO — 4 shapes sem c\u00f3digo
        // novo: a tarifa com a DIVISÃO INT→DOUBLE no local (`minutes /
        // 60.0` + o excedente multiplicado), o isFull com a comparação
        // direta, o zone com o CHAR PARAM nas faixas (== 'A' / == 'B') e o
        // spotsLeft com a subtra\u00e7\u00e3o dupla encadeada. Zero-drift
        // executado: 5.0/8.0/true/false/premium/standard/economy/30 — 8/8
        // id\u00eanticos ao Java — a 44\u00aa valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Parking.java");
        Files.writeString(javaFile, """
                public class Parking {
                    static double fee(int minutes) {
                        double hours = minutes / 60.0;
                        if (hours <= 1) { return 5.0; }
                        return 5.0 + (hours - 1) * 3.0;
                    }
                    static boolean isFull(int occupied, int total) {
                        return occupied >= total;
                    }
                    static String zone(char code) {
                        if (code == 'A') { return "premium"; }
                        if (code == 'B') { return "standard"; }
                        return "economy";
                    }
                    static int spotsLeft(int total, int occupied, int reserved) {
                        return total - occupied - reserved;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Parking.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Parking.fee(30))
                    println(Parking.fee(120))
                    println(Parking.isFull(50, 50))
                    println(Parking.isFull(49, 50))
                    println(Parking.zone('A'))
                    println(Parking.zone('B'))
                    println(Parking.zone('C'))
                    println(Parking.spotsLeft(100, 60, 10))
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
        // oracle Java: 5.0 | 8.0 | true | false | premium | standard | economy | 30
        assertEquals("5.0\n8.0\ntrue\nfalse\npremium\nstandard\neconomy\n30", output,
                "zero-drift: fee/isFull/zone/spotsLeft id\u00eanticos ao Java");
    }

    @Test
    void metricDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o dom\u00ednio M\u00c9TRICAS — 4 shapes sem c\u00f3digo novo:
        // a convers\u00e3o km→milhas com a constante longa, a FAHRENHEIT com a
        // f\u00f3rmula mista int-double (`c * 9 / 5 + 32` — o 9/5 INTEIRO
        // aplicado após a promoção), o sizeLabel com as QUATRO faixas e o
        // isRoomTemp com o && de FAIXA (`>= 20 && <= 26`). Zero-drift
        // executado: 6.21371/212.0/32.0/S/M/L/XL/true/false — 9/9
        // id\u00eanticos ao Java — a 45\u00aa valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Metric.java");
        Files.writeString(javaFile, """
                public class Metric {
                    static double kmToMiles(double km) {
                        return km * 0.621371;
                    }
                    static double celsiusToFahrenheit(double c) {
                        return c * 9 / 5 + 32;
                    }
                    static String sizeLabel(int cm) {
                        if (cm < 100) { return "S"; }
                        if (cm < 150) { return "M"; }
                        if (cm < 180) { return "L"; }
                        return "XL";
                    }
                    static boolean isRoomTemp(double c) {
                        return c >= 20 && c <= 26;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Metric.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Metric.kmToMiles(10.0))
                    println(Metric.celsiusToFahrenheit(100.0))
                    println(Metric.celsiusToFahrenheit(0.0))
                    println(Metric.sizeLabel(80))
                    println(Metric.sizeLabel(120))
                    println(Metric.sizeLabel(165))
                    println(Metric.sizeLabel(190))
                    println(Metric.isRoomTemp(23.0))
                    println(Metric.isRoomTemp(30.0))
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
        // oracle Java: 6.21371 | 212.0 | 32.0 | S | M | L | XL | true | false
        assertEquals("6.21371\n212.0\n32.0\nS\nM\nL\nXL\ntrue\nfalse", output,
                "zero-drift: kmToMiles/celsiusToFahrenheit/sizeLabel/isRoomTemp id\u00eanticos ao Java");
    }

    @Test
    void menuDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o dom\u00ednio MENU — 4 shapes sem c\u00f3digo novo: o
        // total com o acumulador Double no for-each (a fatia dos stores
        // wide), o spicyLabel com as faixas ==/<= (no-spice/mild/spicy/
        // fire), o hasVegan com o EARLY-TRUE do equals e o perPerson com a
        // divis\u00e3o double/int. Zero-drift executado: 56.0/no spice/mild/
        // spicy/fire/false/true/30.0 — 8/8 id\u00eanticos ao Java — a
        // 46\u00aa valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Menu.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Menu {
                    static double totalPrice(List<Double> items) {
                        double sum = 0;
                        for (double item : items) {
                            sum += item;
                        }
                        return sum;
                    }
                    static String spicyLabel(int level) {
                        if (level == 0) { return "no spice"; }
                        if (level <= 3) { return "mild"; }
                        if (level <= 6) { return "spicy"; }
                        return "fire";
                    }
                    static boolean hasVegan(List<String> tags) {
                        for (String tag : tags) {
                            if (tag.equals("vegan")) { return true; }
                        }
                        return false;
                    }
                    static double perPerson(double total, int people) {
                        return total / people;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Menu.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    var items = listOf(25.5, 18.0, 12.5)
                    println(Menu.totalPrice(items))
                    println(Menu.spicyLabel(0))
                    println(Menu.spicyLabel(2))
                    println(Menu.spicyLabel(5))
                    println(Menu.spicyLabel(9))
                    var tags = listOf("veg", "gluten-free")
                    var vegan = listOf("veg", "vegan")
                    println(Menu.hasVegan(tags))
                    println(Menu.hasVegan(vegan))
                    println(Menu.perPerson(120.0, 4))
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
        // oracle Java: 56.0 | no spice | mild | spicy | fire | false | true | 30.0
        assertEquals("56.0\nno spice\nmild\nspicy\nfire\nfalse\ntrue\n30.0", output,
                "zero-drift: totalPrice/spicyLabel/hasVegan/perPerson id\u00eanticos ao Java");
    }

    @Test
    void bikeDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o dom\u00ednio BICICLETA — 4 shapes sem c\u00f3digo novo:
        // o gear-inches com o CAST i2d NO MEIO (`chainring / (double) cog *
        // wheelDiameter` — o cast mudando o tipo da divis\u00e3o), o
        // terrainType com as TR\u00caS faixas de gradient, as calorias com o
        // CAST d2i no return (`(int) (minutes * 8 * intensity)` — a
        // promo\u00e7\u00e3o int→double na cadeia) e o needsService com a
        // compara\u00e7\u00e3o direta. Zero-drift executado: 108.0/flat/rolling/
        // climbing/360/true/false — 7/7 id\u00eanticos ao Java — a 47\u00aa
        // valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Bike.java");
        Files.writeString(javaFile, """
                public class Bike {
                    static double gearInches(int chainring, int cog, double wheelDiameter) {
                        return chainring / (double) cog * wheelDiameter;
                    }
                    static String terrainType(double gradient) {
                        if (gradient < 2) { return "flat"; }
                        if (gradient < 6) { return "rolling"; }
                        return "climbing";
                    }
                    static int calories(int minutes, double intensity) {
                        return (int) (minutes * 8 * intensity);
                    }
                    static boolean needsService(double kmSince, double limit) {
                        return kmSince >= limit;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Bike.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Bike.gearInches(52, 13, 27.0))
                    println(Bike.terrainType(1.0))
                    println(Bike.terrainType(4.0))
                    println(Bike.terrainType(8.0))
                    println(Bike.calories(30, 1.5))
                    println(Bike.needsService(500.0, 400.0))
                    println(Bike.needsService(300.0, 400.0))
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
        // oracle Java: 108.0 | flat | rolling | climbing | 360 | true | false
        assertEquals("108.0\nflat\nrolling\nclimbing\n360\ntrue\nfalse", output,
                "zero-drift: gearInches/terrainType/calories/needsService id\u00eanticos ao Java");
    }

    @Test
    void courseDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o dom\u00ednio CURSO — 4 shapes sem c\u00f3digo novo: a
        // média com o acumulador int + o ternário de guarda + o CAST
        // (`(double) total / size`), o grade com as QUATRO faixas, o
        // approved com a compara\u00e7\u00e3o direta e o failedCount com o
        // contador de abaixo-do-piso. Zero-drift executado: 76.25/0.0/A/
        // B/C/F/true/false/1 — 9/9 id\u00eanticos ao Java — a 48\u00aa
        // valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Course.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Course {
                    static double average(List<Integer> scores) {
                        int total = 0;
                        for (int s : scores) {
                            total += s;
                        }
                        return scores.isEmpty() ? 0 : (double) total / scores.size();
                    }
                    static String grade(double score) {
                        if (score >= 90) { return "A"; }
                        if (score >= 75) { return "B"; }
                        if (score >= 60) { return "C"; }
                        return "F";
                    }
                    static boolean approved(double average) {
                        return average >= 60;
                    }
                    static int failedCount(List<Integer> scores) {
                        int n = 0;
                        for (int s : scores) {
                            if (s < 60) { n++; }
                        }
                        return n;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Course.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    var scores = listOf(85, 92, 58, 70)
                    println(Course.average(scores))
                    println(Course.average(listOf()))
                    println(Course.grade(95.0))
                    println(Course.grade(80.0))
                    println(Course.grade(65.0))
                    println(Course.grade(40.0))
                    println(Course.approved(72.0))
                    println(Course.approved(55.0))
                    println(Course.failedCount(scores))
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
        // oracle Java: 76.25 | 0.0 | A | B | C | F | true | false | 1
        assertEquals("76.25\n0.0\nA\nB\nC\nF\ntrue\nfalse\n1", output,
                "zero-drift: average/grade/approved/failedCount id\u00eanticos ao Java");
    }

    @Test
    void shapeDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o dom\u00ednio GEOMETRIA — 4 shapes sem c\u00f3digo novo:
        // o retângulo com o produto direto, o círculo com a CONSTANTE na
        // FRENTE (`3.14159 * r * r` — a constante primeiro no encadeamento),
        // o kind com as faixas == (triangle/quadrilateral/pentagon/polygon)
        // e o isSquare com o == entre os DOIS params. Zero-drift executado:
        // 20.0/12.56636/triangle/quadrilateral/pentagon/polygon/true/false
        // — 8/8 id\u00eanticos ao Java — a 49\u00aa valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Shape.java");
        Files.writeString(javaFile, """
                public class Shape {
                    static double rectArea(double w, double h) {
                        return w * h;
                    }
                    static double circleArea(double r) {
                        return 3.14159 * r * r;
                    }
                    static String kind(int sides) {
                        if (sides == 3) { return "triangle"; }
                        if (sides == 4) { return "quadrilateral"; }
                        if (sides == 5) { return "pentagon"; }
                        return "polygon";
                    }
                    static boolean isSquare(double w, double h) {
                        return w == h;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Shape.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Shape.rectArea(4.0, 5.0))
                    println(Shape.circleArea(2.0))
                    println(Shape.kind(3))
                    println(Shape.kind(4))
                    println(Shape.kind(5))
                    println(Shape.kind(7))
                    println(Shape.isSquare(3.0, 3.0))
                    println(Shape.isSquare(3.0, 4.0))
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
        // oracle Java: 20.0 | 12.56636 | triangle | quadrilateral | pentagon | polygon | true | false
        assertEquals("20.0\n12.56636\ntriangle\nquadrilateral\npentagon\npolygon\ntrue\nfalse", output,
                "zero-drift: rectArea/circleArea/kind/isSquare id\u00eanticos ao Java");
    }

    @Test
    void emailDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o dom\u00ednio EMAIL + a fatia do String.repeat — o
        // mapeamento java-instance → stdlib-call: o `s.repeat(n)` do JDK
        // vira `strings.repeat(s, n)` (o Kof NÃO tem String.repeat —
        // SEM052 — mas a stdlib TEM strings.repeat/kof_strings_repeat; o
        // precedente keySet→keys). Os shapes: o isValid com o && TRIPLO de
        // calls encadeadas (o indexOf reusado), o domain com o indexOf +
        // substring no local, o mask com o REPEAT MAPEADO + a concat e o
        // sameDomain com a chamada local reusada duas vezes. Zero-drift
        // executado: true/false/false/test.com/***@test.com/true/false —
        // 7/7 id\u00eanticos ao Java — a 50\u00aa valida\u00e7\u00e3o.
        Path javaFile = dir.resolve("Email.java");
        Files.writeString(javaFile, """
                public class Email {
                    static boolean isValid(String email) {
                        return email.contains("@") && email.indexOf("@") > 0 && email.indexOf("@") < email.length() - 1;
                    }
                    static String domain(String email) {
                        int at = email.indexOf("@");
                        return email.substring(at + 1);
                    }
                    static String mask(String email) {
                        int at = email.indexOf("@");
                        return "*".repeat(at) + email.substring(at);
                    }
                    static boolean sameDomain(String a, String b) {
                        return domain(a).equals(domain(b));
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Email.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        assertTrue(kof.contains("strings.repeat"), "o repeat mapeado:\n" + kof);
        String kofRun = kof + """
                main() {
                    println(Email.isValid("ana@test.com"))
                    println(Email.isValid("ana@"))
                    println(Email.isValid("@test.com"))
                    println(Email.domain("ana@test.com"))
                    println(Email.mask("ana@test.com"))
                    println(Email.sameDomain("a@x.com", "b@x.com"))
                    println(Email.sameDomain("a@x.com", "b@y.com"))
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
        // oracle Java: true | false | false | test.com | ***@test.com | true | false
        assertEquals("true\nfalse\nfalse\ntest.com\n***@test.com\ntrue\nfalse", output,
                "zero-drift: isValid/domain/mask/sameDomain id\u00eanticos ao Java");
    }

    @Test
    void noteDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o dom\u00ednio NOTA — 4 shapes sem c\u00f3digo novo: o
        // pad com o repeat MAPEADO dentro da aritm\u00e9tica (`word + " "
        // .repeat(width - word.length())` — o repeat com a EXPRESS\u00c3O
        // calculada no arg), o isBlank com a cadeia trim().isEmpty() no
        // return, o capitalize com o substring(0,1).toUpperCase() + a
        // concat e o guard do isEmpty. Zero-drift executado: 6/ola+5
        // espa\u00e7os/comprida.../true/false/Ola/Ola — 7/7 id\u00eanticos ao
        // Java — a 51\u00aa valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Note.java");
        Files.writeString(javaFile, """
                public class Note {
                    static int wordLen(String word) {
                        return word.length();
                    }
                    static String pad(String word, int width) {
                        if (word.length() >= width) { return word; }
                        return word + " ".repeat(width - word.length());
                    }
                    static boolean isBlank(String line) {
                        return line.trim().isEmpty();
                    }
                    static String capitalize(String word) {
                        if (word.isEmpty()) { return word; }
                        return word.substring(0, 1).toUpperCase() + word.substring(1);
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Note.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Note.wordLen("banana"))
                    println(Note.pad("ola", 8) + "|")
                    println(Note.pad("comprida o suficiente", 8) + "|")
                    println(Note.isBlank("   "))
                    println(Note.isBlank(" x "))
                    println(Note.capitalize("ola"))
                    println(Note.capitalize("").length())
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
        // oracle Java: 6 | ola     | | comprida o suficiente | | true | false | Ola | 0
        // (o capitalize("") imprime 0 — o length do vazio; o trim do harness
        // comeria a linha vazia no println direto)
        assertEquals("6\nola     |\ncomprida o suficiente|\ntrue\nfalse\nOla\n0", output,
                "zero-drift: wordLen/pad/isBlank/capitalize id\u00eanticos ao Java");
    }

    @Test
    void timerDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o dom\u00ednio TIMER — 4 shapes sem c\u00f3digo novo: o
        // formatDuration com a concat MISTA int+literal (m + "m" + s +
        // "s"), o isExpired com a compara\u00e7\u00e3o de Longs no return, o
        // addSeconds com o encadeamento div/rem (%3600/60) e o countdown
        // com o for DESCENDENTE (`i = n; i > 0; i--`) + o acumulador de
        // concat + o sufixo "go". Zero-drift executado: 2m5s/1m0s/true/
        // false/2/3 2 1 go — 6/6 id\u00eanticos ao Java — a 52\u00aa
        // valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Timer2.java");
        Files.writeString(javaFile, """
                public class Timer2 {
                    static String formatDuration(int seconds) {
                        int m = seconds / 60;
                        int s = seconds % 60;
                        return m + "m" + s + "s";
                    }
                    static boolean isExpired(long deadline, long now) {
                        return now > deadline;
                    }
                    static int addSeconds(int h, int m, int s) {
                        int total = s + m * 60 + h * 3600;
                        return total % 3600 / 60;
                    }
                    static String countdown(int n) {
                        String out = "";
                        for (int i = n; i > 0; i--) {
                            out = out + i + " ";
                        }
                        return out + "go";
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Timer2.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Timer2.formatDuration(125))
                    println(Timer2.formatDuration(60))
                    println(Timer2.isExpired(100, 150))
                    println(Timer2.isExpired(100, 50))
                    println(Timer2.addSeconds(1, 2, 3))
                    println(Timer2.countdown(3))
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
        // oracle Java: 2m5s | 1m0s | true | false | 2 | 3 2 1 go
        assertEquals("2m5s\n1m0s\ntrue\nfalse\n2\n3 2 1 go", output,
                "zero-drift: formatDuration/isExpired/addSeconds/countdown id\u00eanticos ao Java");
    }

    @Test
    void doorDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o dom\u00ednio PORTA — 4 shapes sem c\u00f3digo novo: o
        // canEnter com o || de booleans no return, o doorState com as duas
        // faixas booleanas, o openCount com o contador no for-each de
        // Bool[] (o array PRIMITIVO de booleans iterado) e o anyOpen com o
        // EARLY-TRUE. Zero-drift executado: true/true/false/open/locked/
        // closed/2/true/0/false — 10/10 id\u00eanticos ao Java — a 53\u00aa
        // valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Door.java");
        Files.writeString(javaFile, """
                public class Door {
                    static boolean canEnter(boolean hasKey, boolean isStaff) {
                        return hasKey || isStaff;
                    }
                    static String doorState(boolean open, boolean locked) {
                        if (open) { return "open"; }
                        if (locked) { return "locked"; }
                        return "closed";
                    }
                    static int openCount(boolean[] states) {
                        int n = 0;
                        for (boolean s : states) {
                            if (s) { n++; }
                        }
                        return n;
                    }
                    static boolean anyOpen(boolean[] states) {
                        for (boolean s : states) {
                            if (s) { return true; }
                        }
                        return false;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Door.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Door.canEnter(true, false))
                    println(Door.canEnter(false, true))
                    println(Door.canEnter(false, false))
                    println(Door.doorState(true, true))
                    println(Door.doorState(false, true))
                    println(Door.doorState(false, false))
                    var st = new Bool[3]
                    st[0] = true; st[1] = false; st[2] = true
                    println(Door.openCount(st))
                    println(Door.anyOpen(st))
                    var st2 = new Bool[2]
                    st2[0] = false; st2[1] = false
                    println(Door.openCount(st2))
                    println(Door.anyOpen(st2))
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
        // oracle Java: true | true | false | open | locked | closed | 2 | true | 0 | false
        assertEquals("true\ntrue\nfalse\nopen\nlocked\nclosed\n2\ntrue\n0\nfalse", output,
                "zero-drift: canEnter/doorState/openCount/anyOpen id\u00eanticos ao Java");
    }

    @Test
    void farmDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o dom\u00ednio FAZENDA — 4 shapes sem c\u00f3digo novo: o
        // harvestValue com o unbox×double no for-each (`c * pricePerCrate`
        // — a promo\u00e7\u00e3o int→double no acumulador), as esta\u00e7\u00f5es com o
        // || de COMPARA\u00c7\u00d5ES nas faixas (`m <= 2 || m == 12` — o wrap do
        // ano) e as TR\u00caS faixas encadeadas, o needsIrrigation com o && de
        // compara\u00e7\u00e3o+nega\u00e7\u00e3o e o totalTrees com o acumulador int.
        // Zero-drift executado: 200.0/summer/summer/autumn/winter/spring/
        // true/false/false/16 — 10/10 id\u00eanticos ao Java — a 54\u00aa
        // valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Farm.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Farm {
                    static double harvestValue(List<Integer> crates, double pricePerCrate) {
                        double total = 0;
                        for (int c : crates) {
                            total += c * pricePerCrate;
                        }
                        return total;
                    }
                    static String season(int month) {
                        if (month <= 2 || month == 12) { return "summer"; }
                        if (month <= 5) { return "autumn"; }
                        if (month <= 8) { return "winter"; }
                        return "spring";
                    }
                    static boolean needsIrrigation(double soilMoisture, boolean forecastRain) {
                        return soilMoisture < 30 && !forecastRain;
                    }
                    static int totalTrees(List<Integer> perRow) {
                        int sum = 0;
                        for (int t : perRow) {
                            sum += t;
                        }
                        return sum;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Farm.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    var crates = listOf(5, 3, 8)
                    println(Farm.harvestValue(crates, 12.5))
                    println(Farm.season(1))
                    println(Farm.season(12))
                    println(Farm.season(4))
                    println(Farm.season(7))
                    println(Farm.season(10))
                    println(Farm.needsIrrigation(25.0, false))
                    println(Farm.needsIrrigation(25.0, true))
                    println(Farm.needsIrrigation(40.0, false))
                    println(Farm.totalTrees(crates))
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
        // oracle Java: 200.0 | summer | summer | autumn | winter | spring | true | false | false | 16
        assertEquals("200.0\nsummer\nsummer\nautumn\nwinter\nspring\ntrue\nfalse\nfalse\n16", output,
                "zero-drift: harvestValue/season/needsIrrigation/totalTrees id\u00eanticos ao Java");
    }

    @Test
    void cafeDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o dom\u00ednio CAF\u00c9 — 4 shapes sem c\u00f3digo novo: o
        // price com o TERN\u00c1RIO de call-condition no init (`size.equals(
        // "large") ? 8.0 : 5.0`) + o RE-ATRIBUTO depois do if, o
        // loyaltyPoints com o cast d2i no return, o isMorning com o && de
        // faixa e o tip com a regra de três (`bill * percent / 100`).
        // Zero-drift executado: 10.0/8.0/7.0/5.0/8/true/false/5.0 — 8/8
        // id\u00eanticos ao Java — a 55\u00aa valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Cafe.java");
        Files.writeString(javaFile, """
                public class Cafe {
                    static double price(String size, boolean extraShot) {
                        double base = size.equals("large") ? 8.0 : 5.0;
                        if (extraShot) { base = base + 2.0; }
                        return base;
                    }
                    static int loyaltyPoints(double amount) {
                        return (int) (amount / 10);
                    }
                    static boolean isMorning(int hour) {
                        return hour >= 6 && hour < 12;
                    }
                    static double tip(double bill, int percent) {
                        return bill * percent / 100;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Cafe.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Cafe.price("large", true))
                    println(Cafe.price("large", false))
                    println(Cafe.price("small", true))
                    println(Cafe.price("small", false))
                    println(Cafe.loyaltyPoints(85.0))
                    println(Cafe.isMorning(8))
                    println(Cafe.isMorning(14))
                    println(Cafe.tip(50.0, 10))
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
        // oracle Java: 10.0 | 8.0 | 7.0 | 5.0 | 8 | true | false | 5.0
        assertEquals("10.0\n8.0\n7.0\n5.0\n8\ntrue\nfalse\n5.0", output,
                "zero-drift: price/loyaltyPoints/isMorning/tip id\u00eanticos ao Java");
    }

    @Test
    void gymDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o dom\u00ednio ACADEMIA — 4 shapes sem c\u00f3digo novo: o
        // bmi com o get POR PARAM (`weights.get(idx)` — o índice
        // dinâmico) + o denominador calculado, o plan com as faixas ==,
        // o isPeak com o && de faixa e o totalVisits com o acumulador.
        // Zero-drift executado: 22.857142857142858/27.755102040816325/
        // basic/pro/custom/true/false/36 — 8/8 id\u00eanticos ao Java — a
        // 56\u00aa valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Gym.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Gym {
                    static double bmi(List<Double> weights, int idx) {
                        return weights.get(idx) / (1.75 * 1.75);
                    }
                    static String plan(int days) {
                        if (days == 3) { return "basic"; }
                        if (days == 5) { return "pro"; }
                        return "custom";
                    }
                    static boolean isPeak(int hour) {
                        return hour >= 17 && hour <= 20;
                    }
                    static int totalVisits(List<Integer> monthly) {
                        int sum = 0;
                        for (int v : monthly) {
                            sum += v;
                        }
                        return sum;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Gym.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    var weights = listOf(70.0, 85.0)
                    println(Gym.bmi(weights, 0))
                    println(Gym.bmi(weights, 1))
                    println(Gym.plan(3))
                    println(Gym.plan(5))
                    println(Gym.plan(2))
                    println(Gym.isPeak(18))
                    println(Gym.isPeak(10))
                    var visits = listOf(12, 15, 9)
                    println(Gym.totalVisits(visits))
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
        // oracle Java: 22.857142857142858 | 27.755102040816325 | basic | pro | custom | true | false | 36
        assertEquals("22.857142857142858\n27.755102040816325\nbasic\npro\ncustom\ntrue\nfalse\n36", output,
                "zero-drift: bmi/plan/isPeak/totalVisits id\u00eanticos ao Java");
    }

    @Test
    void vaultDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o dom\u00ednio COFRE-2 — 4 shapes sem c\u00f3digo novo: o
        // unlocked com o == entre os DOIS params, o status com o boolean
        // param nas TR\u00caS sa\u00eddas (open/seized/locked), o remaining com o
        // zero literal no early-return e o alarmLevel com a divis\u00e3o
        // int/double no return. Zero-drift executado: true/false/open/
        // seized/locked/2/0/0.3 — 8/8 id\u00eanticos ao Java — a 57\u00aa
        // valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Vault.java");
        Files.writeString(javaFile, """
                public class Vault {
                    static boolean unlocked(int code, int expected) {
                        return code == expected;
                    }
                    static String status(int attempts, int max, boolean unlocked) {
                        if (unlocked) { return "open"; }
                        if (attempts >= max) { return "seized"; }
                        return "locked";
                    }
                    static int remaining(int attempts, int max) {
                        if (attempts >= max) { return 0; }
                        return max - attempts;
                    }
                    static double alarmLevel(int failedRecently, double threshold) {
                        return failedRecently / threshold;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Vault.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Vault.unlocked(1234, 1234))
                    println(Vault.unlocked(999, 1234))
                    println(Vault.status(2, 5, true))
                    println(Vault.status(5, 5, false))
                    println(Vault.status(2, 5, false))
                    println(Vault.remaining(3, 5))
                    println(Vault.remaining(5, 5))
                    println(Vault.alarmLevel(3, 10.0))
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
        // oracle Java: true | false | open | seized | locked | 2 | 0 | 0.3
        assertEquals("true\nfalse\nopen\nseized\nlocked\n2\n0\n0.3", output,
                "zero-drift: unlocked/status/remaining/alarmLevel id\u00eanticos ao Java");
    }

    @Test
    void libraryDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o dom\u00ednio BIBLIOTECA — 4 shapes sem c\u00f3digo novo:
        // o findTitle com o EARLY-RETURN DENTRO do for INDEXADO (o get +
        // equals + o return do próprio elemento), a multa com o zero no
        // early-return (o days <= 0 cobrindo 0 e negativo), o canBorrow
        // com a comparação direta e o countByGenre com o equals contra um
        // PARAM. Zero-drift executado: o cortico/not found/2.5/0.0/0.0/
        // true/false/2 — 8/8 id\u00eanticos ao Java — a 58\u00aa valida\u00e7\u00e3o
        // zero-c\u00f3digo.
        Path javaFile = dir.resolve("Library.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Library {
                    static String findTitle(List<String> titles, String isbn) {
                        for (int i = 0; i < titles.size(); i++) {
                            if (titles.get(i).equals(isbn)) {
                                return titles.get(i);
                            }
                        }
                        return "not found";
                    }
                    static double lateFee(int days, double rate) {
                        if (days <= 0) { return 0; }
                        return days * rate;
                    }
                    static boolean canBorrow(int active, int limit) {
                        return active < limit;
                    }
                    static int countByGenre(List<String> genres, String genre) {
                        int n = 0;
                        for (String g : genres) {
                            if (g.equals(genre)) { n++; }
                        }
                        return n;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Library.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    var titles = listOf("dom casmurro", "o cortico", "iracema")
                    println(Library.findTitle(titles, "o cortico"))
                    println(Library.findTitle(titles, "ausente"))
                    println(Library.lateFee(5, 0.5))
                    println(Library.lateFee(0, 0.5))
                    println(Library.lateFee(-2, 0.5))
                    println(Library.canBorrow(2, 5))
                    println(Library.canBorrow(5, 5))
                    var genres = listOf("romance", "ficcao", "romance")
                    println(Library.countByGenre(genres, "romance"))
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
        // oracle Java: o cortico | not found | 2.5 | 0.0 | 0.0 | true | false | 2
        assertEquals("o cortico\nnot found\n2.5\n0.0\n0.0\ntrue\nfalse\n2", output,
                "zero-drift: findTitle/lateFee/canBorrow/countByGenre id\u00eanticos ao Java");
    }

    @Test
    void planetDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o dom\u00ednio PLANETA — 4 shapes sem c\u00f3digo novo: a
        // gravidade marciana com a constante decimal, o sizeClass com as
        // TR\u00caS faixas (dwarf/terrestrial/giant), o hasMoon com a
        // compara\u00e7\u00e3o direta e a escapeVelocity com a NOTA\u00c7\u00c3O
        // CIENT\u00cdFICA no ldc2_w (`6.674e-11`) + o Math.sqrt + a cadeia
        // mista (`2 * const * mass / radius`). Zero-drift executado:
        // 30.4/dwarf/terrestrial/giant/false/true/11185.72649237147 — 7/7
        // id\u00eanticos ao Java — a 59\u00aa valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Planet.java");
        Files.writeString(javaFile, """
                public class Planet {
                    static double weightOnMars(double earthKg) {
                        return earthKg * 0.38;
                    }
                    static String sizeClass(double radiusKm) {
                        if (radiusKm < 2500) { return "dwarf"; }
                        if (radiusKm < 12000) { return "terrestrial"; }
                        return "giant";
                    }
                    static boolean hasMoon(int moons) {
                        return moons > 0;
                    }
                    static double escapeVelocity(double mass, double radius) {
                        return Math.sqrt(2 * 6.674e-11 * mass / radius);
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Planet.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Planet.weightOnMars(80.0))
                    println(Planet.sizeClass(2000.0))
                    println(Planet.sizeClass(6371.0))
                    println(Planet.sizeClass(69911.0))
                    println(Planet.hasMoon(0))
                    println(Planet.hasMoon(2))
                    println(Planet.escapeVelocity(5.972e24, 6.371e6))
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
        // oracle Java: 30.4 | dwarf | terrestrial | giant | false | true | 11185.72649237147
        assertEquals("30.4\ndwarf\nterrestrial\ngiant\nfalse\ntrue\n11185.72649237147", output,
                "zero-drift: weightOnMars/sizeClass/hasMoon/escapeVelocity id\u00eanticos ao Java");
    }

    @Test
    void vendingDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o dom\u00ednio MÁQUINA — 4 shapes sem c\u00f3digo novo: o
        // charge com o get POR PARAM (o índice do item), o display com o
        // early-string + a concat no return (`"credit: " + credit`), o
        // canBuy com a compara\u00e7\u00e3o direta e o change com o early-return
        // do pr\u00f3prio param. Zero-drift executado: 2.0/insert coins/
        // credit: 2.5/true/false/1.5/2.0 — 7/7 id\u00eanticos ao Java — a
        // 60\u00aa valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Vending.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Vending {
                    static double charge(List<Double> prices, int item) {
                        return prices.get(item);
                    }
                    static String display(double credit) {
                        if (credit <= 0) { return "insert coins"; }
                        return "credit: " + credit;
                    }
                    static boolean canBuy(double credit, double price) {
                        return credit >= price;
                    }
                    static double change(double credit, double price) {
                        if (credit < price) { return credit; }
                        return credit - price;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Vending.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    var prices = listOf(3.5, 2.0, 4.75)
                    println(Vending.charge(prices, 1))
                    println(Vending.display(0.0))
                    println(Vending.display(2.5))
                    println(Vending.canBuy(5.0, 3.5))
                    println(Vending.canBuy(2.0, 3.5))
                    println(Vending.change(5.0, 3.5))
                    println(Vending.change(2.0, 3.5))
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
        // oracle Java: 2.0 | insert coins | credit: 2.5 | true | false | 1.5 | 2.0
        assertEquals("2.0\ninsert coins\ncredit: 2.5\ntrue\nfalse\n1.5\n2.0", output,
                "zero-drift: charge/display/canBuy/change id\u00eanticos ao Java");
    }

    @Test
    void stock2DomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o dom\u00ednio CARTEIRA — 4 shapes sem c\u00f3digo novo: o
        // portfolioValue com o for indexado sobre DUAS listas (o get duplo
        // × a promo\u00e7\u00e3o), o performance com as TR\u00caS faixas de sinal
        // (gain/loss/flat), o profitLots com a subtra\u00e7\u00e3o direta e o
        // worthSelling com a aritm\u00e9tica no && impl\u00edcito do return
        // (`current - fee > purchase` — a subtra\u00e7\u00e3o no lado esquerdo da
        // compara\u00e7\u00e3o). Zero-drift executado: 2350.0/gain/loss/flat/150/
        // true/false — 7/7 id\u00eanticos ao Java — a 61\u00aa valida\u00e7\u00e3o
        // zero-c\u00f3digo.
        Path javaFile = dir.resolve("Stock2.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Stock2 {
                    static double portfolioValue(List<Integer> shares, List<Double> prices) {
                        double total = 0;
                        for (int i = 0; i < shares.size(); i++) {
                            total += shares.get(i) * prices.get(i);
                        }
                        return total;
                    }
                    static String performance(double change) {
                        if (change > 0) { return "gain"; }
                        if (change < 0) { return "loss"; }
                        return "flat";
                    }
                    static int profitLots(int bought, int sold) {
                        return sold - bought;
                    }
                    static boolean worthSelling(double current, double purchase, double fee) {
                        return current - fee > purchase;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Stock2.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    var shares = listOf(100, 50, 200)
                    var prices = listOf(10.5, 20.0, 1.5)
                    println(Stock2.portfolioValue(shares, prices))
                    println(Stock2.performance(0.5))
                    println(Stock2.performance(-0.5))
                    println(Stock2.performance(0.0))
                    println(Stock2.profitLots(300, 450))
                    println(Stock2.worthSelling(110.0, 100.0, 5.0))
                    println(Stock2.worthSelling(103.0, 100.0, 5.0))
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
        // oracle Java: 2350.0 | gain | loss | flat | 150 | true | false
        assertEquals("2350.0\ngain\nloss\nflat\n150\ntrue\nfalse", output,
                "zero-drift: portfolioValue/performance/profitLots/worthSelling id\u00eanticos ao Java");
    }

    @Test
    void kitchenDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o dom\u00ednio COZINHA — 4 shapes sem c\u00f3digo novo: o
        // costPerServing com a divis\u00e3o double/int, o doneness com as TR\u00caS
        // faixas de temperatura (rare/medium/well done — o label com
        // ESPA\u00c7O), o hasIngredient com o EARLY-TRUE do equals contra param
        // e o servingsFor com a soma direta. Zero-drift executado: 15.0/
        // rare/medium/well done/true/false/12 — 7/7 id\u00eanticos ao Java —
        // a 62\u00aa valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Recipe2.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Recipe2 {
                    static double costPerServing(double totalCost, int servings) {
                        return totalCost / servings;
                    }
                    static String doneness(double internalTemp) {
                        if (internalTemp < 52) { return "rare"; }
                        if (internalTemp < 63) { return "medium"; }
                        return "well done";
                    }
                    static boolean hasIngredient(List<String> ingredients, String wanted) {
                        for (String ing : ingredients) {
                            if (ing.equals(wanted)) { return true; }
                        }
                        return false;
                    }
                    static int servingsFor(int guests, int extra) {
                        return guests + extra;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Recipe2.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Recipe2.costPerServing(75.0, 5))
                    println(Recipe2.doneness(48.0))
                    println(Recipe2.doneness(58.0))
                    println(Recipe2.doneness(70.0))
                    var ings = listOf("farinha", "ovos", "leite")
                    println(Recipe2.hasIngredient(ings, "ovos"))
                    println(Recipe2.hasIngredient(ings, "fermento"))
                    println(Recipe2.servingsFor(10, 2))
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
        // oracle Java: 15.0 | rare | medium | well done | true | false | 12
        assertEquals("15.0\nrare\nmedium\nwell done\ntrue\nfalse\n12", output,
                "zero-drift: costPerServing/doneness/hasIngredient/servingsFor id\u00eanticos ao Java");
    }

    @Test
    void textSearchDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (24/10): o domínio BUSCA EM TEXTO — 4 shapes sem código
        // novo: o countWord com o loop indexOf-com-início (o while do
        // `indexOf(word, pos + word.length)` — a busca com ponto de
        // partida), o countChar com o charAt no while e parâmetro Char,
        // o firstLine com o early-return no indexOf STRING ("\\n" — a
        // sobrecarga INT/char do indexOf é SEM051/bug 100, sem
        // equivalente Kof: registrada na rodada, o texto usa a forma
        // String) e o truncate com o ternário + concat de substring.
        // Zero-drift executado (oráculo Java: 2/2/0/6/INFO ok/INFO ok/
        // ER.../short). Chamadas QUALIFICADAS pela classe (os defeitos do
        // compilador da rodada anterior).
        Path javaFile = dir.resolve("TextSearch.java");
        Files.writeString(javaFile, """
                public class TextSearch {
                    static int countWord(String text, String word) {
                        int count = 0;
                        int pos = text.indexOf(word);
                        while (pos != -1) {
                            count++;
                            pos = text.indexOf(word, pos + word.length());
                        }
                        return count;
                    }
                    static int countChar(String text, char c) {
                        int count = 0;
                        for (int i = 0; i < text.length(); i++) {
                            if (text.charAt(i) == c) count++;
                        }
                        return count;
                    }
                    static String firstLine(String text) {
                        int end = text.indexOf("\\n");
                        if (end == -1) return text;
                        return text.substring(0, end);
                    }
                    static String truncate(String s, int max) {
                        if (s.length() <= max) return s;
                        return s.substring(0, max) + "...";
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("TextSearch.class"));
        assertFalse(kof.contains("body not recovered"), "sem stubs:\n" + kof);
        assertTrue(kof.contains("indexOf(arg1, (v3 + arg1.length))"), "o indexOf com início:\n" + kof);
        assertTrue(kof.contains("charAt(v3) == arg1"), "o charAt no while:\n" + kof);
        assertTrue(kof.contains("indexOf(\"\\n\")"), "o indexOf String no firstLine:\n" + kof);
        assertTrue(kof.contains("= if (arg0.length <= arg1) arg0 else"), "o ternário do truncate:\n" + kof);
        // zero-drift executado (oráculo Java: 2/2/0/6/INFO ok/INFO ok/ER.../short)
        String log = "INFO ok\\nERROR disk full\\nINFO retry\\nERROR timeout";
        String kofRun = kof + "\nmain() {\n"
                + "    var log = \"" + log + "\"\n"
                + "    println(TextSearch.countWord(log, \"ERROR\"))\n"
                + "    println(TextSearch.countWord(log, \"INFO\"))\n"
                + "    println(TextSearch.countWord(log, \"WARN\"))\n"
                + "    println(TextSearch.countChar(log, 'R'))\n"
                + "    println(TextSearch.firstLine(log))\n"
                + "    println(TextSearch.truncate(log, 10))\n"
                + "    println(TextSearch.truncate(\"short\", 10))\n}\n";
        CompilationResult result = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(result.success(), "compila zero drift:\n" + kofRun + "\n" + result.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("2\n2\n0\n6\nINFO ok\nINFO ok\nER...\nshort", output,
                "zero-drift: a busca em texto como no Java");
    }

    @Test
    void dateMathDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (24/10): o domínio ARITMÉTICA DE DATAS — zero código
        // novo: o isLeap com a precedência MISTA ((a && b) || c) que
        // recupera como cadeia de condicionais (if a true else if c
        // true else false — semântica idêntica ao ||), o daysInMonth
        // com a cadeia || de QUATRO comparações que recupera como
        // encadeamento != aninhado (if != 4 if != 6 if != 9 if == 11
        // 30 else 31 else 30 else 30 else 30) e o ternário dentro do
        // early-return, o dayOfYear com o for que CHAMA o método
        // (daysInMonth no acumulador) e o season com a guarda || que
        // recupera aplicando De Morgan (if > 2 && != 12). Os booleans
        // comparam contra 0 (isLeap(arg0) != 0). Zero-drift executado
        // (oráculo Java: true/false/true/false/29/28/30/61/365/winter/
        // winter/summer). Chamadas qualificadas pela classe.
        Path javaFile = dir.resolve("Calendar2.java");
        Files.writeString(javaFile, """
                public class Calendar2 {
                    static boolean isLeap(int year) {
                        return (year % 4 == 0 && year % 100 != 0) || year % 400 == 0;
                    }
                    static int daysInMonth(int year, int month) {
                        if (month == 2) return isLeap(year) ? 29 : 28;
                        if (month == 4 || month == 6 || month == 9 || month == 11) return 30;
                        return 31;
                    }
                    static int dayOfYear(int year, int month, int day) {
                        int total = day;
                        for (int m = 1; m < month; m++) {
                            total = total + daysInMonth(year, m);
                        }
                        return total;
                    }
                    static String season(int month) {
                        if (month <= 2 || month == 12) return "winter";
                        if (month <= 5) return "spring";
                        if (month <= 8) return "summer";
                        return "autumn";
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Calendar2.class"));
        assertFalse(kof.contains("body not recovered"), "sem stubs:\n" + kof);
        assertTrue(kof.contains("if ((arg0 % 4) == 0 && (arg0 % 100) != 0) true else if ((arg0 % 400) == 0) true else false"),
                "o || misto como cadeia:\n" + kof);
        assertTrue(kof.contains("if (arg1 == 11) 30 else 31 else 30 else 30 else 30"),
                "a cadeia || de 4 como != aninhado:\n" + kof);
        assertTrue(kof.contains("v3 = (v3 + Calendar2.daysInMonth(arg0, v4))"),
                "o for que chama o método:\n" + kof);
        assertTrue(kof.contains("if (arg0 > 2 && arg0 != 12)"), "a guarda || com De Morgan:\n" + kof);
        // zero-drift executado (oráculo Java: true/false/true/false/29/28/30/61/365/winter/winter/summer)
        String kofRun = kof + "\nmain() {\n"
                + "    println(Calendar2.isLeap(2024))\n    println(Calendar2.isLeap(1900))\n"
                + "    println(Calendar2.isLeap(2000))\n    println(Calendar2.isLeap(2023))\n"
                + "    println(Calendar2.daysInMonth(2024, 2))\n    println(Calendar2.daysInMonth(2023, 2))\n"
                + "    println(Calendar2.daysInMonth(2024, 11))\n"
                + "    println(Calendar2.dayOfYear(2024, 3, 1))\n    println(Calendar2.dayOfYear(2023, 12, 31))\n"
                + "    println(Calendar2.season(1))\n    println(Calendar2.season(12))\n    println(Calendar2.season(7))\n}\n";
        CompilationResult result = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(result.success(), "compila zero drift:\n" + kofRun + "\n" + result.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("true\nfalse\ntrue\nfalse\n29\n28\n30\n61\n365\nwinter\nwinter\nsummer", output,
                "zero-drift: a aritmética de datas como no Java");
    }

    @Test
    void everydayClassicDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (24/10): o domínio CLÁSSICO DO DIA-A-DIA (fizzbuzz +
        // loja + banco) — zero código novo: o fizzCount com o else-if
        // de contagem (o && misto e o || no meio do encadeamento),
        // o label com o ternário ANINHADO de strings (15/3/5), o
        // total da loja com a multiplicação e o desconto condicional
        // (sum - sum*pct/100), e o withdraw do banco com o early-return
        // -1 no guard. Zero-drift executado (oráculo Java REAL: 8/5/1/FizzBuzz/Fizz/Buzz/none/8100/9000/9000/-1/500). Chamadas
        // qualificadas pela classe.
        Path javaFile = dir.resolve("Classic.java");
        Files.writeString(javaFile, """
                public class Classic {
                    static int fizzCount(int n) {
                        int count = 0;
                        for (int i = 1; i <= n; i++) {
                            if (i % 3 == 0 && i % 5 == 0) count = count + 2;
                            else if (i % 3 == 0 || i % 5 == 0) count = count + 1;
                        }
                        return count;
                    }
                    static String label(int n) {
                        return n % 15 == 0 ? "FizzBuzz" : (n % 3 == 0 ? "Fizz" : (n % 5 == 0 ? "Buzz" : "none"));
                    }
                    static int total(int price, int qty, int discountPct) {
                        int sum = price * qty;
                        if (discountPct > 0) {
                            sum = sum - sum * discountPct / 100;
                        }
                        return sum;
                    }
                    static int withdraw(int balance, int amount) {
                        if (amount > balance) return -1;
                        return balance - amount;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Classic.class"));
        assertFalse(kof.contains("body not recovered"), "sem stubs:\n" + kof);
        assertTrue(kof.contains("if ((v2 % 3) == 0 && (v2 % 5) == 0)"),
                "o && misto do fizzCount:\n" + kof);
        assertTrue(kof.contains("if ((arg0 % 15) == 0) \"FizzBuzz\" else"),
                "o ternário aninhado do label:\n" + kof);
        assertTrue(kof.contains("v3 = (v3 - ((v3 * arg2) / 100))"),
                "o desconto condicional:\n" + kof);
        assertTrue(kof.contains("if (arg1 > arg0)"), "o guard do withdraw:\n" + kof);
        // zero-drift executado (oráculo Java REAL: 8/5/1/FizzBuzz/Fizz/Buzz/none/8100/9000/9000/-1/500)
        String kofRun = kof + "\nmain() {\n"
                + "    println(Classic.fizzCount(15))\n    println(Classic.fizzCount(10))\n    println(Classic.fizzCount(4))\n"
                + "    println(Classic.label(15))\n    println(Classic.label(9))\n    println(Classic.label(10))\n    println(Classic.label(7))\n"
                + "    println(Classic.total(900, 10, 10))\n    println(Classic.total(1000, 9, 0))\n"
                + "    println(Classic.total(1000, 10, 10))\n"
                + "    println(Classic.withdraw(100, 500))\n    println(Classic.withdraw(1000, 500))\n}\n";
        CompilationResult result = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(result.success(), "compila zero drift:\n" + kofRun + "\n" + result.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("8\n5\n1\nFizzBuzz\nFizz\nBuzz\nnone\n8100\n9000\n9000\n-1\n500", output,
                "zero-drift: os clássicos do dia-a-dia como no Java");
    }

    @Test
    void primesDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (24/10): o domínio PRIMOS — zero código novo: o isPrime
        // com a condição do loop ARITMÉTICA ((v1 * v1) <= arg0 — o imul
        // no teste do while) e o early-return false dentro do loop, o
        // countPrimes com o for que chama o método (isPrime(v2) != 0 —
        // booleanos comparam contra 0), o nextPrime com a condição
        // NEGADA (!isPrime → == 0) e o largestPrimeBelow com a guarda
        // composta (v1 >= 2 && isPrime(v1) == 0). Zero-drift executado
        // (oráculo Java REAL: true/false/true/8/15/11/17/7/1). Chamadas
        // qualificadas pela classe (os defeitos do compilador).
        Path javaFile = dir.resolve("Primes.java");
        Files.writeString(javaFile, """
                public class Primes {
                    static boolean isPrime(int n) {
                        if (n < 2) return false;
                        int d = 2;
                        while (d * d <= n) {
                            if (n % d == 0) return false;
                            d = d + 1;
                        }
                        return true;
                    }
                    static int countPrimes(int limit) {
                        int count = 0;
                        for (int n = 2; n <= limit; n++) {
                            if (isPrime(n)) count = count + 1;
                        }
                        return count;
                    }
                    static int nextPrime(int n) {
                        int candidate = n + 1;
                        while (!isPrime(candidate)) {
                            candidate = candidate + 1;
                        }
                        return candidate;
                    }
                    static int largestPrimeBelow(int limit) {
                        int n = limit;
                        while (n >= 2 && !isPrime(n)) {
                            n = n - 1;
                        }
                        return n;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Primes.class"));
        assertFalse(kof.contains("body not recovered"), "sem stubs:\n" + kof);
        assertTrue(kof.contains("while ((v1 * v1) <= arg0)"), "a condição aritmética do while:\n" + kof);
        assertTrue(kof.contains("Primes.isPrime(v2) != 0"), "a chamada booleana no for:\n" + kof);
        assertTrue(kof.contains("while (Primes.isPrime(v1) == 0)"), "a condição negada:\n" + kof);
        assertTrue(kof.contains("while (v1 >= 2 && Primes.isPrime(v1) == 0)"), "a guarda composta:\n" + kof);
        // zero-drift executado (oráculo Java REAL: true/false/true/8/15/11/17/7/1)
        String kofRun = kof + "\nmain() {\n"
                + "    println(Primes.isPrime(2))\n    println(Primes.isPrime(15))\n    println(Primes.isPrime(97))\n"
                + "    println(Primes.countPrimes(20))\n    println(Primes.countPrimes(50))\n"
                + "    println(Primes.nextPrime(10))\n    println(Primes.nextPrime(13))\n"
                + "    println(Primes.largestPrimeBelow(10))\n    println(Primes.largestPrimeBelow(1))\n}\n";
        CompilationResult result = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(result.success(), "compila zero drift:\n" + kofRun + "\n" + result.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("true\nfalse\ntrue\n8\n15\n11\n17\n7\n1", output,
                "zero-drift: os primos como no Java");
    }

    @Test
    void gridDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (24/10): o domínio GRADE — zero código novo: os loops
        // ANINHADOS recuperam como whiles empilhados (o sumTable com o
        // produto i*j no acumulador, o fillDiagonal com a igualdade
        // i==j), e o countPairs traz o BREAK dentro do loop INTERNO
        // (o break sai do while interno, não do externo — o while
        // externo continua) mais o else do if (os incrementos no
        // caminho normal). Zero-drift executado (oráculo Java REAL:
        // 36/225/6/10/1/4/0). Chamadas qualificadas pela classe.
        Path javaFile = dir.resolve("Grid.java");
        Files.writeString(javaFile, """
                public class Grid {
                    static int sumTable(int n) {
                        int total = 0;
                        for (int i = 1; i <= n; i++) {
                            for (int j = 1; j <= n; j++) {
                                total = total + i * j;
                            }
                        }
                        return total;
                    }
                    static int countPairs(int n, int limit) {
                        int count = 0;
                        for (int i = 0; i < n; i++) {
                            for (int j = i + 1; j < n; j++) {
                                if (i + j > limit) break;
                                count++;
                            }
                        }
                        return count;
                    }
                    static int fillDiagonal(int n) {
                        int cells = 0;
                        for (int i = 0; i < n; i++) {
                            for (int j = 0; j < n; j++) {
                                if (i == j) cells++;
                            }
                        }
                        return cells;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Grid.class"));
        assertFalse(kof.contains("body not recovered"), "sem stubs:\n" + kof);
        assertTrue(kof.contains("while (v2 <= arg0)"), "o while externo:\n" + kof);
        assertTrue(kof.contains("v1 = (v1 + (v2 * v3))"), "o produto no acumulador:\n" + kof);
        assertTrue(kof.contains("var v4 = (v3 + 1)"), "o j = i + 1 do countPairs:\n" + kof);
        assertTrue(kof.contains("break"), "o break no loop interno:\n" + kof);
        assertTrue(kof.contains("if (v2 == v3)"), "a igualdade da diagonal:\n" + kof);
        // zero-drift executado (oráculo Java REAL: 36/225/6/10/1/4/0)
        String kofRun = kof + "\nmain() {\n"
                + "    println(Grid.sumTable(3))\n    println(Grid.sumTable(5))\n"
                + "    println(Grid.countPairs(5, 4))\n    println(Grid.countPairs(5, 100))\n    println(Grid.countPairs(4, 1))\n"
                + "    println(Grid.fillDiagonal(4))\n    println(Grid.fillDiagonal(0))\n}\n";
        CompilationResult result = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(result.success(), "compila zero drift:\n" + kofRun + "\n" + result.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("36\n225\n6\n10\n1\n4\n0", output,
                "zero-drift: os loops aninhados como no Java");
    }

    @Test
    void switchDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (24/10): o domínio MENU — zero código novo: o switch de
        // RETORNO por case (o action), o switch de CHAR (os cases saem
        // como os códigos 65/66/67 — o Char do Kof é o mesmo Int) e o
        // switch que ATRIBUI uma variável declarada antes dele (o
        // dayName com `var v1` compartilhado entre os cases e os
        // breaks; o default no fim sem break). Zero-drift executado
        // (oráculo Java REAL: open/save/close/unknown/4/3/2/0/Mon/Wed/
        // ???). Chamadas qualificadas pela classe.
        Path javaFile = dir.resolve("SwitchMenu.java");
        Files.writeString(javaFile, """
                public class SwitchMenu {
                    static String action(int option) {
                        switch (option) {
                            case 1: return "open";
                            case 2: return "save";
                            case 3: return "close";
                            default: return "unknown";
                        }
                    }
                    static int points(char grade) {
                        switch (grade) {
                            case 'A': return 4;
                            case 'B': return 3;
                            case 'C': return 2;
                            default: return 0;
                        }
                    }
                    static String dayName(int d) {
                        String name;
                        switch (d) {
                            case 1: name = "Mon"; break;
                            case 2: name = "Tue"; break;
                            case 3: name = "Wed"; break;
                            default: name = "???"; break;
                        }
                        return name;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("SwitchMenu.class"));
        assertFalse(kof.contains("body not recovered"), "sem stubs:\n" + kof);
        assertTrue(kof.contains("switch (arg0)"), "o switch nativo:\n" + kof);
        assertTrue(kof.contains("case 65: return 4"), "o switch de char com códigos:\n" + kof);
        assertTrue(kof.contains("var v1"), "a variável compartilhada do dayName:\n" + kof);
        assertTrue(kof.contains("v1 = \"Mon\""), "a atribuição por case:\n" + kof);
        // zero-drift executado (oráculo Java REAL: open/save/close/unknown/4/3/2/0/Mon/Wed/???)
        String kofRun = kof + "\nmain() {\n"
                + "    println(SwitchMenu.action(1))\n    println(SwitchMenu.action(2))\n    println(SwitchMenu.action(3))\n    println(SwitchMenu.action(9))\n"
                + "    println(SwitchMenu.points('A'))\n    println(SwitchMenu.points('B'))\n    println(SwitchMenu.points('C'))\n    println(SwitchMenu.points('F'))\n"
                + "    println(SwitchMenu.dayName(1))\n    println(SwitchMenu.dayName(3))\n    println(SwitchMenu.dayName(7))\n}\n";
        CompilationResult result = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(result.success(), "compila zero drift:\n" + kofRun + "\n" + result.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("open\nsave\nclose\nunknown\n4\n3\n2\n0\nMon\nWed\n???", output,
                "zero-drift: os switches como no Java");
    }

    @Test
    void trigDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (24/10): o mapeamento DOUBLE do math COMPLETO — o
        // sin/cos/tan/asin/acos/atan/toRadians/toDegrees existiam no
        // namespace do Kof (KofMath) mas o decompiler só mapeava o sqrt
        // (o (D)D) e o pow (o (DD)D); agora a família inteira mapeia 1:1
        // com o java.lang.Math, com a MESMA reescrita do sufixo
        // `.toDouble()` → `as Double` do sqrt (o I2D do javac). O atan2
        // acompanha o pow no (DD)D. O abs NÃO entra (o abs do Kof é
        // Int-only, SEM025 — teto honesto). Zero-drift executado
        // (oráculo Java REAL: 5.0/2.0/0.6435011087932844/90.0/
        // 1.5707963267948966/5.0/3/0).
        Path javaFile = dir.resolve("Trig.java");
        Files.writeString(javaFile, """
                public class Trig {
                    static double circleX(double angle, double radius) {
                        return Math.cos(angle) * radius;
                    }
                    static double circleY(double angle, double radius) {
                        return Math.sin(angle) * radius;
                    }
                    static double slopeAngle(double rise, double run) {
                        return Math.atan2(rise, run);
                    }
                    static double toDeg(double rad) {
                        return Math.toDegrees(rad);
                    }
                    static double toRad(double deg) {
                        return Math.toRadians(deg);
                    }
                    static double hypotenuse(double a, double b) {
                        return Math.sqrt(a * a + b * b);
                    }
                    static int clamp(int v, int lo, int hi) {
                        if (v < lo) return lo;
                        if (v > hi) return hi;
                        return v;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Trig.class"));
        assertFalse(kof.contains("body not recovered"), "sem stubs:\n" + kof);
        assertTrue(kof.contains("math.cos(arg0) * arg1"), "o cos mapeado:\n" + kof);
        assertTrue(kof.contains("math.sin(arg0) * arg1"), "o sin mapeado:\n" + kof);
        assertTrue(kof.contains("math.atan2(arg0, arg1)"), "o atan2 mapeado:\n" + kof);
        assertTrue(kof.contains("math.toDegrees(arg0)") && kof.contains("math.toRadians(arg0)"),
                "as conversões de ângulo:\n" + kof);
        assertTrue(kof.contains("math.sqrt(((arg0 * arg0) + (arg1 * arg1)))"), "o sqrt:\n" + kof);
        // zero-drift executado (oráculo Java REAL: 5.0/2.0/0.6435011087932844/90.0/1.5707963267948966/5.0/3/0)
        String kofRun = kof + "\nmain() {\n"
                + "    println(Trig.circleX(0.0, 5.0))\n    println(Trig.circleY(1.5707963267948966, 2.0))\n"
                + "    println(Trig.slopeAngle(3.0, 4.0))\n    println(Trig.toDeg(1.5707963267948966))\n    println(Trig.toRad(90.0))\n"
                + "    println(Trig.hypotenuse(3.0, 4.0))\n    println(Trig.clamp(5, 0, 3))\n    println(Trig.clamp(-2, 0, 3))\n}\n";
        CompilationResult result = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(result.success(), "compila zero drift:\n" + kofRun + "\n" + result.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("5.0\n2.0\n0.6435011087932844\n90.0\n1.5707963267948966\n5.0\n3\n0", output,
                "zero-drift: a trigonometria como no Java");
    }

    @Test
    void contactsDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (24/10): o domínio CONTATOS — o null-handling realista —
        // zero código novo: as comparações de null saem das ifnull/
        // ifnonnull (o `== null` e o `!= null` do invCond), o `!isEmpty()`
        // vira `isEmpty() == 0` (booleans comparam contra 0), o `||` do
        // guard inverte para `&&` De Morgan no ramo oposto, e o `.length`
        // sai sem parênteses (a propriedade do Kof). O Java não tem
        // strings não-nulas, então o zero-drift EXERCE os casos null de
        // verdade (o decompiler emite `String` e o Kof aceita null nos
        // parâmetros — medido). Zero-drift executado (oráculo Java REAL:
        // Ada Lovelace/Byron/Byron/5/0/0/true/false/false/anon/bob).
        // Chamadas qualificadas pela classe.
        Path javaFile = dir.resolve("Contacts.java");
        Files.writeString(javaFile, """
                public class Contacts {
                    static String displayName(String first, String last) {
                        if (first == null || first.isEmpty()) {
                            return last;
                        }
                        return first + " " + last;
                    }
                    static int lengthOrZero(String s) {
                        if (s == null) return 0;
                        return s.length();
                    }
                    static boolean hasContent(String s) {
                        return s != null && !s.isEmpty();
                    }
                    static String orDefault(String s, String fallback) {
                        if (s == null || s.isEmpty()) return fallback;
                        return s;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Contacts.class"));
        assertFalse(kof.contains("body not recovered"), "sem stubs:\n" + kof);
        assertTrue(kof.contains("arg0 != null && arg0.isEmpty() == 0"),
                "o guard De Morgan com null e isEmpty:\n" + kof);
        assertTrue(kof.contains("if (arg0 == null) 0 else arg0.length"),
                "o == null com o length sem parênteses:\n" + kof);
        assertTrue(kof.contains("if (arg0 != null && arg0.isEmpty() == 0) true else false"),
                "o hasContent como expressão:\n" + kof);
        // zero-drift executado (oráculo Java REAL: Ada Lovelace/Byron/Byron/5/0/0/true/false/false/anon/bob)
        String kofRun = kof + "\nmain() {\n"
                + "    println(Contacts.displayName(\"Ada\", \"Lovelace\"))\n"
                + "    println(Contacts.displayName(null, \"Byron\"))\n"
                + "    println(Contacts.displayName(\"\", \"Byron\"))\n"
                + "    println(Contacts.lengthOrZero(\"hello\"))\n    println(Contacts.lengthOrZero(null))\n    println(Contacts.lengthOrZero(\"\"))\n"
                + "    println(Contacts.hasContent(\"x\"))\n    println(Contacts.hasContent(null))\n    println(Contacts.hasContent(\"\"))\n"
                + "    println(Contacts.orDefault(null, \"anon\"))\n    println(Contacts.orDefault(\"bob\", \"anon\"))\n}\n";
        CompilationResult result = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(result.success(), "compila zero drift:\n" + kofRun + "\n" + result.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("Ada Lovelace\nByron\nByron\n5\n0\n0\ntrue\nfalse\nfalse\nanon\nbob", output,
                "zero-drift: o null-handling como no Java");
    }

    @Test
    void overloadsDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (24/10): o domínio SOBRECARGAS — três métodos com o mesmo
        // nome e assinaturas diferentes (o caso realista mais comum: a
        // área de um quadrado/retângulo/círculo). Cada método é
        // descompilado independentemente e o compilador Kof aceita a
        // sobrecarga (zero-drift: o dispatch acontece pelo tipo e pela
        // aridade dos argumentos). O String-switch do mesmo lote segue o
        // stub honesto (a compilacão hashCode + equals do javac não é
        // recuperada — sem código errado).
        Path javaFile = dir.resolve("Shapes.java");
        Files.writeString(javaFile, """
                public class Shapes {
                    static int area(int side) {
                        return side * side;
                    }
                    static int area(int width, int height) {
                        return width * height;
                    }
                    static double area(double radius) {
                        return 3.14159 * radius * radius;
                    }
                    static String describe(int side) {
                        return "square " + side;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Shapes.class"));
        assertFalse(kof.contains("body not recovered"), "sem stubs:\n" + kof);
        assertTrue(kof.contains("static Int area(Int arg0) = (arg0 * arg0)"), "o overload 1-arg:\n" + kof);
        assertTrue(kof.contains("static Int area(Int arg0, Int arg1) = (arg0 * arg1)"), "o overload 2-arg:\n" + kof);
        assertTrue(kof.contains("static Double area(Double arg0) = ((3.14159 * arg0) * arg0)"), "o overload Double:\n" + kof);
        assertTrue(kof.contains("static String describe(Int arg0) = \"square \" + arg0"), "o concat:\n" + kof);
        // zero-drift executado (oráculo Java REAL: 25/24/12.56636/square 7)
        String kofRun = kof + "\nmain() {\n"
                + "    println(Shapes.area(5))\n"
                + "    println(Shapes.area(4, 6))\n"
                + "    println(Shapes.area(2.0))\n"
                + "    println(Shapes.describe(7))\n}\n";
        CompilationResult result = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(result.success(), "compila zero drift:\n" + kofRun + "\n" + result.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("25\n24\n12.56636\nsquare 7", output,
                "zero-drift: as sobrecargas como no Java");
    }

    @Test
    void accountClassDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (24/10): o domínio CONTA-CLASSE — o ESTADO DE INSTÂNCIA
        // completo — zero código novo: os campos de instância (`Int
        // balance`, `String owner`), o CONSTRUTOR com parâmetros e as
        // atribuições `this.campo = ...`, os métodos de instância lendo
        // e escrevendo `this.campo` (o getfield/putfield), o early-return
        // no withdraw e o getter como expressão (`Int getBalance() =
        // this.balance`). A ponta decisiva: o main CRIA a instância e
        // chama os métodos — zero-drift executado (oráculo Java REAL:
        // 150/true/120/false/120/Ada: 120).
        Path javaFile = dir.resolve("BankAccount.java");
        Files.writeString(javaFile, """
                public class BankAccount {
                    private int balance;
                    private String owner;

                    BankAccount(String owner, int initial) {
                        this.owner = owner;
                        this.balance = initial;
                    }
                    void deposit(int amount) {
                        balance = balance + amount;
                    }
                    boolean withdraw(int amount) {
                        if (amount > balance) return false;
                        balance = balance - amount;
                        return true;
                    }
                    int getBalance() {
                        return balance;
                    }
                    String describe() {
                        return owner + ": " + balance;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("BankAccount.class"));
        assertFalse(kof.contains("body not recovered"), "sem stubs:\n" + kof);
        assertTrue(kof.contains("constructor(String arg0, Int arg1)"), "o construtor com parâmetros:\n" + kof);
        assertTrue(kof.contains("this.owner = arg0") && kof.contains("this.balance = arg1"),
                "as atribuições do construtor:\n" + kof);
        assertTrue(kof.contains("this.balance = (this.balance + arg0)"), "a escrita de instância:\n" + kof);
        assertTrue(kof.contains("Int getBalance() = this.balance"), "o getter como expressão:\n" + kof);
        assertTrue(kof.contains("String describe() = this.owner + \": \" + this.balance"), "a concat de instância:\n" + kof);
        // zero-drift executado (oráculo Java REAL: 150/true/120/false/120/Ada: 120)
        String kofRun = kof + "\nmain() {\n"
                + "    var acc = BankAccount(\"Ada\", 100)\n"
                + "    acc.deposit(50)\n"
                + "    println(acc.getBalance())\n"
                + "    println(acc.withdraw(30))\n"
                + "    println(acc.getBalance())\n"
                + "    println(acc.withdraw(500))\n"
                + "    println(acc.getBalance())\n"
                + "    println(acc.describe())\n}\n";
        CompilationResult result = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(result.success(), "compila zero drift:\n" + kofRun + "\n" + result.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("150\ntrue\n120\nfalse\n120\nAda: 120", output,
                "zero-drift: o estado de instância como no Java");
    }

    @Test
    void textsDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (24/10): o domínio TEXTOS — zero código novo: as
        // chamadas encadeadas no receptor (substring(0, 1).toUpperCase()
        // dentro de concatenação), o loop de dois ponteiros com charAt
        // (o isPalindrome, com o `v2 = v2 + -1` do j--), as comparações
        // de CHAR contra os códigos inteiros (o countVowels: v3 == 97 ||
        // ... — o Char do Kof é o mesmo Int), o length como propriedade
        // no limite do loop e o slugify com trim/toLowerCase/replace
        // encadeados. Zero-drift executado (oráculo Java REAL:
        // A.L./true/false/false/3/0/hello-world/(vazio)).
        Path javaFile = dir.resolve("Texts.java");
        Files.writeString(javaFile, """
                public class Texts {
                    static String initials(String first, String last) {
                        String f = first.substring(0, 1).toUpperCase();
                        String l = last.substring(0, 1).toUpperCase();
                        return f + "." + l + ".";
                    }
                    static boolean isPalindrome(String s) {
                        int i = 0;
                        int j = s.length() - 1;
                        while (i < j) {
                            if (s.charAt(i) != s.charAt(j)) return false;
                            i++;
                            j--;
                        }
                        return true;
                    }
                    static int countVowels(String s) {
                        int count = 0;
                        for (int i = 0; i < s.length(); i++) {
                            char c = s.charAt(i);
                            if (c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u') {
                                count++;
                            }
                        }
                        return count;
                    }
                    static String slugify(String s) {
                        String t = s.trim();
                        if (t.isEmpty()) return "";
                        return t.toLowerCase().replace(" ", "-");
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Texts.class"));
        assertFalse(kof.contains("body not recovered"), "sem stubs:\n" + kof);
        assertTrue(kof.contains("arg0.substring(0, 1).toUpperCase() + \".\""), "a cadeia encadeada:\n" + kof);
        assertTrue(kof.contains("arg0.charAt(v1) != arg0.charAt(v2)"), "o loop de dois ponteiros:\n" + kof);
        assertTrue(kof.contains("v3 == 97 || v3 == 101"), "os códigos de char:\n" + kof);
        assertTrue(kof.contains("v1.toLowerCase().replace(\" \", \"-\")"), "o slugify encadeado:\n" + kof);
        // zero-drift executado (oráculo Java REAL: A.L./true/false/false/3/0/hello-world/(vazio))
        String kofRun = kof + "\nmain() {\n"
                + "    println(Texts.initials(\"ada\", \"lovelace\"))\n"
                + "    println(Texts.isPalindrome(\"radar\"))\n    println(Texts.isPalindrome(\"hello\"))\n    println(Texts.isPalindrome(\"ab\"))\n"
                + "    println(Texts.countVowels(\"hello world\"))\n    println(Texts.countVowels(\"xyz\"))\n"
                + "    println(Texts.slugify(\"  Hello World  \"))\n    println(Texts.slugify(\"   \"))\n}\n";
        CompilationResult result = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(result.success(), "compila zero drift:\n" + kofRun + "\n" + result.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("A.L.\ntrue\nfalse\nfalse\n3\n0\nhello-world", output,
                "zero-drift: os textos como no Java");
    }

    @Test
    void switchExpressionDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (24/10): a EXPRESSÃO SWITCH (Java 14+) — o arrow form —
        // zero código novo: o javac compila como um tableswitch cujos
        // braços carregam o valor e saltam ao join comum, e o decompiler
        // recupera a forma de seta (`return switch (arg0) { case 1 ->
        // "Mon" case 2 -> "Tue" ... }`) que o compilador Kof ACEITA. O
        // segundo método é a versão char (os cases saem como códigos
        // 65/66). Zero-drift executado (oráculo Java REAL:
        // Mon/Wed/???/4/3/0).
        Path javaFile = dir.resolve("Days.java");
        Files.writeString(javaFile, """
                public class Days {
                    static String dayName(int d) {
                        return switch (d) {
                            case 1 -> "Mon";
                            case 2 -> "Tue";
                            case 3 -> "Wed";
                            default -> "???";
                        };
                    }
                    static int points(char grade) {
                        return switch (grade) {
                            case 'A' -> 4;
                            case 'B' -> 3;
                            default -> 0;
                        };
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Days.class"));
        assertFalse(kof.contains("body not recovered"), "sem stubs:\n" + kof);
        assertTrue(kof.contains("return switch (arg0) { case 1 -> \"Mon\" case 2 -> \"Tue\" case 3 -> \"Wed\" default -> \"???\" }"),
                "a expressão switch de seta:\n" + kof);
        assertTrue(kof.contains("case 65 -> 4 case 66 -> 3 default -> 0 }"),
                "a versão char com códigos:\n" + kof);
        // zero-drift executado (oráculo Java REAL: Mon/Wed/???/4/3/0)
        String kofRun = kof + "\nmain() {\n"
                + "    println(Days.dayName(1))\n    println(Days.dayName(3))\n    println(Days.dayName(9))\n"
                + "    println(Days.points('A'))\n    println(Days.points('B'))\n    println(Days.points('F'))\n}\n";
        CompilationResult result = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(result.success(), "compila zero drift:\n" + kofRun + "\n" + result.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("Mon\nWed\n???\n4\n3\n0", output,
                "zero-drift: a expressão switch como no Java");
    }

    @Test
    void guardedDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (24/10): o domínio GUARDA — o try/catch realista — zero
        // código novo: o catch (Exception e) do Java vira catch (String e)
        // (o idioma do Kof), o Integer.parseInt vira toInt() (o idioma de
        // conversão), e a estrutura try/return/catch/return é preservada.
        // O ponto decisivo: as EXCEÇÕES DO RUNTIME DO KOF são capturadas
        // pelo catch (String e) exatamente como o Java captura as suas —
        // o divide-por-zero, a falha de parse e o substring fora da
        // faixa. Zero-drift executado (oráculo Java REAL: 5/-1/42/0/
        // hello worl/(vazio)).
        Path javaFile = dir.resolve("Guarded.java");
        Files.writeString(javaFile, """
                public class Guarded {
                    static int safeDivide(int a, int b) {
                        try {
                            return a / b;
                        } catch (Exception e) {
                            return -1;
                        }
                    }
                    static int safeParse(String s) {
                        try {
                            return Integer.parseInt(s);
                        } catch (Exception e) {
                            return 0;
                        }
                    }
                    static String safeAccess(String s) {
                        try {
                            return s.substring(0, 10);
                        } catch (Exception e) {
                            return "";
                        }
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Guarded.class"));
        assertFalse(kof.contains("body not recovered"), "sem stubs:\n" + kof);
        assertTrue(kof.contains("try {\n        return (arg0 / arg1)\n        } catch (String e) {\n        return -1"),
                "o try/catch de divisão:\n" + kof);
        assertTrue(kof.contains("arg0.toInt()"), "a conversão parseInt→toInt:\n" + kof);
        assertTrue(kof.contains("return arg0.substring(0, 10)"), "o substring guardado:\n" + kof);
        // zero-drift executado (oráculo Java REAL: 5/-1/42/0/hello worl/(vazio))
        String kofRun = kof + "\nmain() {\n"
                + "    println(Guarded.safeDivide(10, 2))\n    println(Guarded.safeDivide(10, 0))\n"
                + "    println(Guarded.safeParse(\"42\"))\n    println(Guarded.safeParse(\"abc\"))\n"
                + "    println(Guarded.safeAccess(\"hello world\"))\n    println(Guarded.safeAccess(\"short\"))\n}\n";
        CompilationResult result = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(result.success(), "compila zero drift:\n" + kofRun + "\n" + result.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("5\n-1\n42\n0\nhello worl", output,
                "zero-drift: o try/catch como no Java");
    }

    @Test
    void vec2DomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (24/10): o domínio VETOR-2D — a INSTANCIAÇÃO dentro de
        // métodos — zero código novo: a fábrica estática que devolve um
        // novo objeto (new Point(0, 0) → Point(0, 0), a sintaxe de
        // construtor do Kof), o método de instância que instancia
        // misturando campos do this e do outro (this.x + arg0.x), o
        // getter de expressão e a distância ao quadrado. Probed no mesmo
        // lote: o construtor DELEGADO (this(0, 0)) segue o stub honesto —
        // o registro fica nesta entrada. Zero-drift executado (oráculo
        // Java REAL: 0,0/0/2,3/13).
        Path javaFile = dir.resolve("Vec2.java");
        Files.writeString(javaFile, """
                public class Vec2 {
                    int x;
                    int y;

                    Vec2(int x, int y) {
                        this.x = x;
                        this.y = y;
                    }
                    static Vec2 origin() {
                        return new Vec2(0, 0);
                    }
                    Vec2 plus(Vec2 other) {
                        return new Vec2(this.x + other.x, this.y + other.y);
                    }
                    int distanceSquared() {
                        return this.x * this.x + this.y * this.y;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Vec2.class"));
        assertFalse(kof.contains("body not recovered"), "sem stubs:\n" + kof);
        assertTrue(kof.contains("static Vec2 origin() = Vec2(0, 0)"),
                "a fábrica estática com o construtor do Kof:\n" + kof);
        assertTrue(kof.contains("Vec2 plus(Vec2 arg0) = Vec2((this.x + arg0.x), (this.y + arg0.y))"),
                "a instanciação misturando this e outro:\n" + kof);
        assertTrue(kof.contains("Int distanceSquared() = ((this.x * this.x) + (this.y * this.y))"),
                "a distância ao quadrado:\n" + kof);
        // zero-drift executado (oráculo Java REAL: 0,0/0/2,3/13)
        String kofRun = kof + "\nmain() {\n"
                + "    var p1 = Vec2(2, 3)\n"
                + "    var o = Vec2.origin()\n"
                + "    println(o.x + \",\" + o.y)\n"
                + "    println(o.distanceSquared())\n"
                + "    var p4 = p1.plus(o)\n"
                + "    println(p4.x + \",\" + p4.y)\n"
                + "    println(p4.distanceSquared())\n}\n";
        CompilationResult result = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(result.success(), "compila zero drift:\n" + kofRun + "\n" + result.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("0,0\n0\n2,3\n13", output,
                "zero-drift: a instanciação como no Java");
    }

    @Test
    void commuteDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o dom\u00ednio DESLOCAMENTO — 4 shapes sem c\u00f3digo
        // novo: o costPerDay com a divis\u00e3o double/int, o mode com as
        // QUATRO faixas (walk/bike/bus/train), o arrivesEarly com a
        // ARITMÉTICA na compara\u00e7\u00e3o (`h * 60 + m < limit` — a convers\u00e3o
        // para minutos no && impl\u00edcito) e o weeklyCost com a
        // multiplica\u00e7\u00e3o direta. Zero-drift executado: 10.0/walk/bike/bus/
        // train/true/false/110 — 8/8 id\u00eanticos ao Java — a 63\u00aa
        // valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Commute.java");
        Files.writeString(javaFile, """
                public class Commute {
                    static double costPerDay(double monthlyPass, int workdays) {
                        return monthlyPass / workdays;
                    }
                    static String mode(int distanceKm) {
                        if (distanceKm < 2) { return "walk"; }
                        if (distanceKm < 8) { return "bike"; }
                        if (distanceKm < 30) { return "bus"; }
                        return "train";
                    }
                    static boolean arrivesEarly(int departHour, int departMin, int limitMin) {
                        return departHour * 60 + departMin < limitMin;
                    }
                    static int weeklyCost(int daily) {
                        return daily * 5;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Commute.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Commute.costPerDay(200.0, 20))
                    println(Commute.mode(1))
                    println(Commute.mode(5))
                    println(Commute.mode(20))
                    println(Commute.mode(45))
                    println(Commute.arrivesEarly(7, 30, 500))
                    println(Commute.arrivesEarly(8, 30, 500))
                    println(Commute.weeklyCost(22))
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
        // oracle Java: 10.0 | walk | bike | bus | train | true | false | 110
        assertEquals("10.0\nwalk\nbike\nbus\ntrain\ntrue\nfalse\n110", output,
                "zero-drift: costPerDay/mode/arrivesEarly/weeklyCost id\u00eanticos ao Java");
    }

    @Test
    void hotelDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o dom\u00ednio HOTEL — 4 shapes sem c\u00f3digo novo: o
        // cost com o produto + o ADENDO condicional (`total = total +
        // nights * 25.0` — a aritm\u00e9tica dupla no branch), o roomClass
        // com as faixas == (single/double/suite), o hasVacancy com a
        // compara\u00e7\u00e3o direta e o cancellationFee com o zero literal.
        // Zero-drift executado: 525.0/450.0/single/double/suite/true/false/
        // 0.0/50.0 — 9/9 id\u00eanticos ao Java — a 64\u00aa valida\u00e7\u00e3o
        // zero-c\u00f3digo.
        Path javaFile = dir.resolve("Hotel.java");
        Files.writeString(javaFile, """
                public class Hotel {
                    static double cost(int nights, double nightly, boolean breakfast) {
                        double total = nights * nightly;
                        if (breakfast) { total = total + nights * 25.0; }
                        return total;
                    }
                    static String roomClass(int guests) {
                        if (guests == 1) { return "single"; }
                        if (guests == 2) { return "double"; }
                        return "suite";
                    }
                    static boolean hasVacancy(int booked, int total) {
                        return booked < total;
                    }
                    static double cancellationFee(int daysBefore) {
                        if (daysBefore >= 7) { return 0; }
                        return 50.0;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Hotel.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Hotel.cost(3, 150.0, true))
                    println(Hotel.cost(3, 150.0, false))
                    println(Hotel.roomClass(1))
                    println(Hotel.roomClass(2))
                    println(Hotel.roomClass(4))
                    println(Hotel.hasVacancy(10, 20))
                    println(Hotel.hasVacancy(20, 20))
                    println(Hotel.cancellationFee(10))
                    println(Hotel.cancellationFee(2))
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
        // oracle Java: 525.0 | 450.0 | single | double | suite | true | false | 0.0 | 50.0
        assertEquals("525.0\n450.0\nsingle\ndouble\nsuite\ntrue\nfalse\n0.0\n50.0", output,
                "zero-drift: cost/roomClass/hasVacancy/cancellationFee id\u00eanticos ao Java");
    }

    @Test
    void tradeDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o dom\u00ednio COM\u00c9RCIO — 4 shapes sem c\u00f3digo novo:
        // o sellValue com o produto int×double, o verdict com as TR\u00caS
        // faixas de sinal (gain/even/loss — o == 0 no meio), o worthIt com
        // o PRODUTO no lado direito (`expected > cost * 1.1` — a
        // multiplicação como threshold) e o breakEven com o DIVISOR
        // PARENTESADO (`fixed / (price - unit)`). Zero-drift executado:
        // 1250.0/gain/even/loss/true/false/40 — 7/7 id\u00eanticos ao Java —
        // a 65\u00aa valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Trade.java");
        Files.writeString(javaFile, """
                public class Trade {
                    static double sellValue(int quantity, double unitPrice) {
                        return quantity * unitPrice;
                    }
                    static String verdict(double profit) {
                        if (profit > 0) { return "gain"; }
                        if (profit == 0) { return "even"; }
                        return "loss";
                    }
                    static boolean worthIt(double expected, double cost) {
                        return expected > cost * 1.1;
                    }
                    static int breakEven(int fixed, int unit, int price) {
                        return fixed / (price - unit);
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Trade.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Trade.sellValue(100, 12.5))
                    println(Trade.verdict(100.0))
                    println(Trade.verdict(0.0))
                    println(Trade.verdict(-50.0))
                    println(Trade.worthIt(120.0, 100.0))
                    println(Trade.worthIt(105.0, 100.0))
                    println(Trade.breakEven(1000, 20, 45))
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
        // oracle Java: 1250.0 | gain | even | loss | true | false | 40
        assertEquals("1250.0\ngain\neven\nloss\ntrue\nfalse\n40", output,
                "zero-drift: sellValue/verdict/worthIt/breakEven id\u00eanticos ao Java");
    }

    @Test
    void keychainDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o dom\u00ednio CHAVEIRO — 4 shapes sem c\u00f3digo novo: o
        // fits com o equals entre os DOIS params, o ringLabel com as faixas
        // <= (empty/small/medium/large), o sparesNeeded com o ZERO no
        // early-return e o brassTag com o ENDSWITH no return. Zero-drift
        // executado: true/false/empty/small/medium/large/2/0/true/false —
        // 10/10 id\u00eanticos ao Java — a 66\u00aa valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Keychain.java");
        Files.writeString(javaFile, """
                public class Keychain {
                    static boolean fits(String key, String lock) {
                        return key.equals(lock);
                    }
                    static String ringLabel(int keys) {
                        if (keys <= 1) { return "empty"; }
                        if (keys <= 5) { return "small"; }
                        if (keys <= 10) { return "medium"; }
                        return "large";
                    }
                    static int sparesNeeded(int total, int have) {
                        if (total <= have) { return 0; }
                        return total - have;
                    }
                    static boolean brassTag(String key) {
                        return key.endsWith("-brass");
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Keychain.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Keychain.fits("casa", "casa"))
                    println(Keychain.fits("casa", "carro"))
                    println(Keychain.ringLabel(0))
                    println(Keychain.ringLabel(3))
                    println(Keychain.ringLabel(8))
                    println(Keychain.ringLabel(15))
                    println(Keychain.sparesNeeded(5, 3))
                    println(Keychain.sparesNeeded(3, 5))
                    println(Keychain.brassTag("portao-brass"))
                    println(Keychain.brassTag("portao-steel"))
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
        // oracle Java: true | false | empty | small | medium | large | 2 | 0 | true | false
        assertEquals("true\nfalse\nempty\nsmall\nmedium\nlarge\n2\n0\ntrue\nfalse", output,
                "zero-drift: fits/ringLabel/sparesNeeded/brassTag id\u00eanticos ao Java");
    }

    @Test
    void elevatorDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o dom\u00ednio ELEVADOR — 4 shapes sem c\u00f3digo novo: a
        // dire\u00e7\u00e3o com as TR\u00caS faixas de sinal (up/down/here), o
        // overloaded com a compara\u00e7\u00e3o direta, o floorsToTravel com a
        // subtra\u00e7\u00e3o e o stopCount com o contador != PARAM no for-each
        // (`floor != currentFloor` — o != contra param, o primeiro do
        // corpus). Zero-drift executado: up/down/here/true/false/6/2/4 —
        // 8/8 id\u00eanticos ao Java — a 67\u00aa valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Elevator.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Elevator {
                    static String direction(int current, int target) {
                        if (target > current) { return "up"; }
                        if (target < current) { return "down"; }
                        return "here";
                    }
                    static boolean overloaded(int people, int capacity) {
                        return people > capacity;
                    }
                    static int floorsToTravel(int from, int to) {
                        return to - from;
                    }
                    static int stopCount(List<Integer> stops, int currentFloor) {
                        int n = 0;
                        for (int floor : stops) {
                            if (floor != currentFloor) { n++; }
                        }
                        return n;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Elevator.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Elevator.direction(3, 7))
                    println(Elevator.direction(7, 3))
                    println(Elevator.direction(5, 5))
                    println(Elevator.overloaded(11, 10))
                    println(Elevator.overloaded(10, 10))
                    println(Elevator.floorsToTravel(3, 9))
                    var stops = listOf(2, 5, 2, 7)
                    println(Elevator.stopCount(stops, 2))
                    println(Elevator.stopCount(stops, 9))
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
        // oracle Java: up | down | here | true | false | 6 | 2 | 4
        assertEquals("up\ndown\nhere\ntrue\nfalse\n6\n2\n4", output,
                "zero-drift: direction/overloaded/floorsToTravel/stopCount id\u00eanticos ao Java");
    }

    @Test
    void wardrobeDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o dom\u00ednio GUARDA-ROUPA — 4 shapes sem c\u00f3digo
        // novo: o sizeTag com as faixas < (P/M/G/GG), o fitsColor com o
        // equals entre os DOIS params, o hangersNeeded com a soma direta e
        // o isFormal com o STARTSWITH de 1 char no return. Zero-drift
        // executado: P/M/G/GG/true/false/15/true/false — 9/9 id\u00eanticos
        // ao Java — a 68\u00aa valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Wardrobe.java");
        Files.writeString(javaFile, """
                public class Wardrobe {
                    static String sizeTag(int waist) {
                        if (waist < 30) { return "P"; }
                        if (waist < 36) { return "M"; }
                        if (waist < 42) { return "G"; }
                        return "GG";
                    }
                    static boolean fitsColor(String item, String outfit) {
                        return item.equals(outfit);
                    }
                    static int hangersNeeded(int items, int spare) {
                        return items + spare;
                    }
                    static boolean isFormal(String tag) {
                        return tag.startsWith("f");
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Wardrobe.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Wardrobe.sizeTag(28))
                    println(Wardrobe.sizeTag(34))
                    println(Wardrobe.sizeTag(40))
                    println(Wardrobe.sizeTag(44))
                    println(Wardrobe.fitsColor("azul", "azul"))
                    println(Wardrobe.fitsColor("azul", "preto"))
                    println(Wardrobe.hangersNeeded(10, 5))
                    println(Wardrobe.isFormal("formal"))
                    println(Wardrobe.isFormal("casual"))
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
        // oracle Java: P | M | G | GG | true | false | 15 | true | false
        assertEquals("P\nM\nG\nGG\ntrue\nfalse\n15\ntrue\nfalse", output,
                "zero-drift: sizeTag/fitsColor/hangersNeeded/isFormal id\u00eanticos ao Java");
    }

    @Test
    void gasDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o dom\u00ednio G\u00c1S — 4 shapes sem c\u00f3digo novo: a
        // conta com o produto int×double, o usageLevel com as faixas < (low/
        // normal/high), o leakDetected com a compara\u00e7\u00e3o direta e o
        // daysLeft com o CAST d2i no return (`(int) (m3 / dailyUse)` — a
        // divis\u00e3o truncada: 50/4.5 = 11). Zero-drift executado: 75.0/low/
        // normal/high/true/false/11 — 7/7 id\u00eanticos ao Java — a
        // 69\u00aa valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Gas.java");
        Files.writeString(javaFile, """
                public class Gas {
                    static double bill(int m3, double perM3) {
                        return m3 * perM3;
                    }
                    static String usageLevel(int m3) {
                        if (m3 < 10) { return "low"; }
                        if (m3 < 25) { return "normal"; }
                        return "high";
                    }
                    static boolean leakDetected(double ppm, double limit) {
                        return ppm > limit;
                    }
                    static int daysLeft(int m3, double dailyUse) {
                        return (int) (m3 / dailyUse);
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Gas.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Gas.bill(30, 2.5))
                    println(Gas.usageLevel(5))
                    println(Gas.usageLevel(15))
                    println(Gas.usageLevel(40))
                    println(Gas.leakDetected(150.0, 100.0))
                    println(Gas.leakDetected(50.0, 100.0))
                    println(Gas.daysLeft(50, 4.5))
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
        // oracle Java: 75.0 | low | normal | high | true | false | 11
        assertEquals("75.0\nlow\nnormal\nhigh\ntrue\nfalse\n11", output,
                "zero-drift: bill/usageLevel/leakDetected/daysLeft id\u00eanticos ao Java");
    }

    @Test
    void backpackDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o dom\u00ednio MOCHILA — 4 shapes sem c\u00f3digo novo: o
        // weight com o acumulador Double, o canCarry com o PRODUTO no lado
        // direito (`load <= strength * 2` — o i2d na promoção), o
        // packPriority com os booleanos nas faixas (medical/hydration/food)
        // e o pockets com o contador > 0. Zero-drift executado: 7.0/true/
        // false/medical/hydration/food/2 — 7/7 id\u00eanticos ao Java — a
        // 70\u00aa valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Backpack.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Backpack {
                    static double weight(List<Double> items) {
                        double total = 0;
                        for (double w : items) {
                            total += w;
                        }
                        return total;
                    }
                    static boolean canCarry(double load, int strength) {
                        return load <= strength * 2;
                    }
                    static String packPriority(boolean firstAid, boolean water) {
                        if (firstAid) { return "medical"; }
                        if (water) { return "hydration"; }
                        return "food";
                    }
                    static int pockets(List<Integer> sizes) {
                        int n = 0;
                        for (int s : sizes) {
                            if (s > 0) { n++; }
                        }
                        return n;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Backpack.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    var items = listOf(2.5, 1.5, 3.0)
                    println(Backpack.weight(items))
                    println(Backpack.canCarry(15.0, 10))
                    println(Backpack.canCarry(25.0, 10))
                    println(Backpack.packPriority(true, false))
                    println(Backpack.packPriority(false, true))
                    println(Backpack.packPriority(false, false))
                    var sizes = listOf(2, 0, 3, 0)
                    println(Backpack.pockets(sizes))
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
        // oracle Java: 7.0 | true | false | medical | hydration | food | 2
        assertEquals("7.0\ntrue\nfalse\nmedical\nhydration\nfood\n2", output,
                "zero-drift: weight/canCarry/packPriority/pockets id\u00eanticos ao Java");
    }

    @Test
    void fishingDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o dom\u00ednio PESCA — 4 shapes sem c\u00f3digo novo: o
        // weightKg com a constante decimal (`length * 0.15`), o sizeClass
        // com as faixas < (small/medium/trophy), o legal com a comparação
        // contra o min PARAM e o pointsPerFish com o CAST d2i no return
        // (`(int) (kg * 10)` — 3.5×10 = 35). Zero-drift executado: 6.0/
        // small/medium/trophy/true/false/35 — 7/7 id\u00eanticos ao Java —
        // a 71\u00aa valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Fishing.java");
        Files.writeString(javaFile, """
                public class Fishing {
                    static double weightKg(int lengthCm) {
                        return lengthCm * 0.15;
                    }
                    static String sizeClass(double kg) {
                        if (kg < 1) { return "small"; }
                        if (kg < 5) { return "medium"; }
                        return "trophy";
                    }
                    static boolean legal(double kg, double minKg) {
                        return kg >= minKg;
                    }
                    static int pointsPerFish(double kg) {
                        return (int) (kg * 10);
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Fishing.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Fishing.weightKg(40))
                    println(Fishing.sizeClass(0.5))
                    println(Fishing.sizeClass(3.0))
                    println(Fishing.sizeClass(8.0))
                    println(Fishing.legal(2.0, 1.5))
                    println(Fishing.legal(1.0, 1.5))
                    println(Fishing.pointsPerFish(3.5))
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
        // oracle Java: 6.0 | small | medium | trophy | true | false | 35
        assertEquals("6.0\nsmall\nmedium\ntrophy\ntrue\nfalse\n35", output,
                "zero-drift: weightKg/sizeClass/legal/pointsPerFish id\u00eanticos ao Java");
    }

    @Test
    void gardenDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o dom\u00ednio JARDIM — 4 shapes sem c\u00f3digo novo: o
        // waterNeeded com o produto int×double, o growthStage com as
        // QUATRO faixas (seed/sprout/growing/mature), o needsWater com a
        // comparação direta e o weeklyWater com o PRODUTO TRIPLO
        // (`plants * perPlant * dryDays` — a cadeia de 3 multiplicadores
        // com a promoção int→double no meio). Zero-drift executado: 5.0/
        // seed/sprout/growing/mature/true/false/35.0 — 8/8 id\u00eanticos
        // ao Java — a 72\u00aa valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Garden.java");
        Files.writeString(javaFile, """
                public class Garden {
                    static double waterNeeded(int plants, double perPlant) {
                        return plants * perPlant;
                    }
                    static String growthStage(int days) {
                        if (days < 7) { return "seed"; }
                        if (days < 21) { return "sprout"; }
                        if (days < 60) { return "growing"; }
                        return "mature";
                    }
                    static boolean needsWater(double moisture) {
                        return moisture < 40;
                    }
                    static double weeklyWater(int plants, double perPlant, int dryDays) {
                        return plants * perPlant * dryDays;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Garden.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Garden.waterNeeded(10, 0.5))
                    println(Garden.growthStage(3))
                    println(Garden.growthStage(14))
                    println(Garden.growthStage(45))
                    println(Garden.growthStage(90))
                    println(Garden.needsWater(35.0))
                    println(Garden.needsWater(50.0))
                    println(Garden.weeklyWater(10, 0.5, 7))
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
        // oracle Java: 5.0 | seed | sprout | growing | mature | true | false | 35.0
        assertEquals("5.0\nseed\nsprout\ngrowing\nmature\ntrue\nfalse\n35.0", output,
                "zero-drift: waterNeeded/growthStage/needsWater/weeklyWater id\u00eanticos ao Java");
    }

    @Test
    void chessDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o dom\u00ednio XADREZ — 4 shapes sem c\u00f3digo novo: o
        // pieceName com as faixas == (pawn/knight/bishop/other), o
        // isValidMove com o != entre os DOIS params, o materialScore com a
        // SOMA DE PRODUTOS (`pawns * 1 + knights * 3` — os pesos
        // multiplicados) e o inCheck com o IDENTITY RETURN do boolean
        // param. Zero-drift executado: pawn/knight/bishop/other/true/
        // false/14/true/false — 9/9 id\u00eanticos ao Java — a 73\u00aa
        // valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Chess.java");
        Files.writeString(javaFile, """
                public class Chess {
                    static String pieceName(int type) {
                        if (type == 1) { return "pawn"; }
                        if (type == 2) { return "knight"; }
                        if (type == 3) { return "bishop"; }
                        return "other";
                    }
                    static boolean isValidMove(int from, int to) {
                        return from != to;
                    }
                    static int materialScore(int pawns, int knights) {
                        return pawns * 1 + knights * 3;
                    }
                    static boolean inCheck(boolean kingAttacked) {
                        return kingAttacked;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Chess.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Chess.pieceName(1))
                    println(Chess.pieceName(2))
                    println(Chess.pieceName(3))
                    println(Chess.pieceName(5))
                    println(Chess.isValidMove(3, 5))
                    println(Chess.isValidMove(4, 4))
                    println(Chess.materialScore(8, 2))
                    println(Chess.inCheck(true))
                    println(Chess.inCheck(false))
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
        // oracle Java: pawn | knight | bishop | other | true | false | 14 | true | false
        assertEquals("pawn\nknight\nbishop\nother\ntrue\nfalse\n14\ntrue\nfalse", output,
                "zero-drift: pieceName/isValidMove/materialScore/inCheck id\u00eanticos ao Java");
    }

    @Test
    void solarDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o dom\u00ednio SOLAR — 4 shapes sem c\u00f3digo novo: o
        // dailyKwh com a CADEIA TRIPLO (`panels * 0.4 * hours` — a
        // promoção no meio), o condition com as faixas < (cloudy/partial/
        // full), o batteryOk com a comparação direta e o savings com o
        // produto double×double. Zero-drift executado: 20.0/cloudy/
        // partial/full/true/false/17.0 — 7/7 id\u00eanticos ao Java — a
        // 74\u00aa valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Solar.java");
        Files.writeString(javaFile, """
                public class Solar {
                    static double dailyKwh(double panels, double hours) {
                        return panels * 0.4 * hours;
                    }
                    static String condition(double kwh) {
                        if (kwh < 2) { return "cloudy"; }
                        if (kwh < 8) { return "partial"; }
                        return "full";
                    }
                    static boolean batteryOk(double charge) {
                        return charge > 20;
                    }
                    static double savings(double kwh, double rate) {
                        return kwh * rate;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Solar.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Solar.dailyKwh(10, 5.0))
                    println(Solar.condition(1.0))
                    println(Solar.condition(5.0))
                    println(Solar.condition(15.0))
                    println(Solar.batteryOk(25.0))
                    println(Solar.batteryOk(15.0))
                    println(Solar.savings(20.0, 0.85))
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
        // oracle Java: 20.0 | cloudy | partial | full | true | false | 17.0
        assertEquals("20.0\ncloudy\npartial\nfull\ntrue\nfalse\n17.0", output,
                "zero-drift: dailyKwh/condition/batteryOk/savings id\u00eanticos ao Java");
    }

    @Test
    void batteryDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o dom\u00ednio BATERIA — 4 shapes sem c\u00f3digo novo: o
        // percentRemaining com a FÓRMULA DE PERCENTUAL (`(capacity - used)
        // * 100 / capacity` — o parêntese no numerador + a divisão
        // inteira), o state com as faixas < (low/ok/full), o needsCharge
        // com a comparação direta e o hoursLeft com a divisão int/double.
        // Zero-drift executado: 70/low/ok/full/true/false/10.0 — 7/7
        // id\u00eanticos ao Java — a 75\u00aa valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Battery.java");
        Files.writeString(javaFile, """
                public class Battery {
                    static int percentRemaining(int used, int capacity) {
                        return (capacity - used) * 100 / capacity;
                    }
                    static String state(double pct) {
                        if (pct < 20) { return "low"; }
                        if (pct < 80) { return "ok"; }
                        return "full";
                    }
                    static boolean needsCharge(double pct) {
                        return pct < 15;
                    }
                    static double hoursLeft(int remainingPct, double drainPerHour) {
                        return remainingPct / drainPerHour;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Battery.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Battery.percentRemaining(30, 100))
                    println(Battery.state(10.0))
                    println(Battery.state(50.0))
                    println(Battery.state(90.0))
                    println(Battery.needsCharge(10.0))
                    println(Battery.needsCharge(50.0))
                    println(Battery.hoursLeft(50, 5.0))
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
        // oracle Java: 70 | low | ok | full | true | false | 10.0
        assertEquals("70\nlow\nok\nfull\ntrue\nfalse\n10.0", output,
                "zero-drift: percentRemaining/state/needsCharge/hoursLeft id\u00eanticos ao Java");
    }

    @Test
    void phoneDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o dom\u00ednio TELEFONE — 4 shapes sem c\u00f3digo novo: o
        // dialKey com o CHAR ARITMÉTICO (`digit - '0'` — o char menos o
        // literal de char), o isValidNumber com o && de COMPARAÇÃO+CALL
        // (`length() == 11 && startsWith("55")`), o format com a concat de
        // 4 termos com o parêntese no literal e o firstDigit com o charAt
        // no return. Zero-drift executado: 7/0/false/false/false/(11)
        // 999998888/5 — 7/7 id\u00eanticos ao Java — a 76\u00aa valida\u00e7\u00e3o
        // zero-c\u00f3digo (o == 11 rejeita o 12 dígitos NOS DOIS — o drift
        // zero honesto).
        Path javaFile = dir.resolve("Phone.java");
        Files.writeString(javaFile, """
                public class Phone {
                    static int dialKey(char digit) {
                        return digit - '0';
                    }
                    static boolean isValidNumber(String number) {
                        return number.length() == 11 && number.startsWith("55");
                    }
                    static String format(String areaCode, String local) {
                        return "(" + areaCode + ") " + local;
                    }
                    static char firstDigit(String number) {
                        return number.charAt(0);
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Phone.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Phone.dialKey('7'))
                    println(Phone.dialKey('0'))
                    println(Phone.isValidNumber("551199998888"))
                    println(Phone.isValidNumber("1199998888"))
                    println(Phone.isValidNumber("331199998888"))
                    println(Phone.format("11", "999998888"))
                    println(Phone.firstDigit("5511"))
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
        // oracle Java: 7 | 0 | false | false | false | (11) 999998888 | 5
        assertEquals("7\n0\nfalse\nfalse\nfalse\n(11) 999998888\n5", output,
                "zero-drift: dialKey/isValidNumber/format/firstDigit id\u00eanticos ao Java");
    }

    @Test
    void movieDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o dom\u00ednio CINEMA — 4 shapes sem c\u00f3digo novo: o
        // ticketPrice com as faixas idade+boolean (10/14/20), o rating
        // com as faixas >= (great/good/bad), o isBlockbuster com a
        // compara\u00e7\u00e3o direta e o runtimeInMinutes com a convers\u00e3o
        // (`hours * 60 + minutes` — o 135 exato). Zero-drift executado:
        // 10.0/14.0/20.0/great/good/bad/true/false/135 — 9/9 id\u00eanticos
        // ao Java — a 77\u00aa valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Movie.java");
        Files.writeString(javaFile, """
                public class Movie {
                    static double ticketPrice(int age, boolean student) {
                        if (age < 12) { return 10.0; }
                        if (student) { return 14.0; }
                        return 20.0;
                    }
                    static String rating(double score) {
                        if (score >= 8) { return "great"; }
                        if (score >= 6) { return "good"; }
                        return "bad";
                    }
                    static boolean isBlockbuster(double revenueM) {
                        return revenueM >= 100;
                    }
                    static int runtimeInMinutes(int hours, int minutes) {
                        return hours * 60 + minutes;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Movie.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Movie.ticketPrice(8, false))
                    println(Movie.ticketPrice(20, true))
                    println(Movie.ticketPrice(20, false))
                    println(Movie.rating(9.0))
                    println(Movie.rating(7.0))
                    println(Movie.rating(4.0))
                    println(Movie.isBlockbuster(150.0))
                    println(Movie.isBlockbuster(50.0))
                    println(Movie.runtimeInMinutes(2, 15))
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
        // oracle Java: 10.0 | 14.0 | 20.0 | great | good | bad | true | false | 135
        assertEquals("10.0\n14.0\n20.0\ngreat\ngood\nbad\ntrue\nfalse\n135", output,
                "zero-drift: ticketPrice/rating/isBlockbuster/runtimeInMinutes id\u00eanticos ao Java");
    }

    @Test
    void goalDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o dom\u00eddio META — 4 shapes sem c\u00f3digo novo: o
        // achieved com a comparação direta, o progressLabel com as QUATRO
        // faixas (done/halfway/started/not started — o label ESPAÇADO), o
        // daysToGoal com a divisão inteira e o completionPct com a
        // PROMOÇÃO EXPLÍCITA (`done * 100.0 / total` — o 75.0 exato).
        // Zero-drift executado: true/false/done/halfway/started/not
        // started/10/75.0 — 8/8 id\u00eanticos ao Java — a 78\u00aa
        // valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Goal.java");
        Files.writeString(javaFile, """
                public class Goal {
                    static boolean achieved(int current, int target) {
                        return current >= target;
                    }
                    static String progressLabel(double pct) {
                        if (pct >= 100) { return "done"; }
                        if (pct >= 50) { return "halfway"; }
                        if (pct > 0) { return "started"; }
                        return "not started";
                    }
                    static int daysToGoal(int remaining, int perDay) {
                        return remaining / perDay;
                    }
                    static double completionPct(int done, int total) {
                        return done * 100.0 / total;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Goal.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Goal.achieved(15, 10))
                    println(Goal.achieved(5, 10))
                    println(Goal.progressLabel(100.0))
                    println(Goal.progressLabel(75.0))
                    println(Goal.progressLabel(25.0))
                    println(Goal.progressLabel(0.0))
                    println(Goal.daysToGoal(50, 5))
                    println(Goal.completionPct(30, 40))
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
        // oracle Java: true | false | done | halfway | started | not started | 10 | 75.0
        assertEquals("true\nfalse\ndone\nhalfway\nstarted\nnot started\n10\n75.0", output,
                "zero-drift: achieved/progressLabel/daysToGoal/completionPct id\u00eanticos ao Java");
    }

    @Test
    void recipe3DomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o dom\u00eddio RECEITA-3 — 4 shapes sem c\u00f3digo novo:
        // o pricePerServing com o DIVISOR PARENTESADO de soma (`(ingredients
        // + overhead) / servings`), o spice com as faixas ==/<= (none/mild/
        // medium/hot), o isVegetarian com o && de DUAS NEGAÇÕES de params
        // booleanos e o totalMinutes com a soma tripla. Zero-drift
        // executado: 10.0/none/mild/medium/hot/true/false/false/40 — 9/9
        // id\u00eanticos ao Java — a 79\u00aa valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Recipe3.java");
        Files.writeString(javaFile, """
                public class Recipe3 {
                    static double pricePerServing(double ingredients, double overhead, int servings) {
                        return (ingredients + overhead) / servings;
                    }
                    static String spice(int peppers) {
                        if (peppers == 0) { return "none"; }
                        if (peppers <= 2) { return "mild"; }
                        if (peppers <= 5) { return "medium"; }
                        return "hot";
                    }
                    static boolean isVegetarian(boolean hasMeat, boolean hasFish) {
                        return !hasMeat && !hasFish;
                    }
                    static int totalMinutes(int prep, int cook, int rest) {
                        return prep + cook + rest;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Recipe3.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Recipe3.pricePerServing(40.0, 10.0, 5))
                    println(Recipe3.spice(0))
                    println(Recipe3.spice(2))
                    println(Recipe3.spice(4))
                    println(Recipe3.spice(8))
                    println(Recipe3.isVegetarian(false, false))
                    println(Recipe3.isVegetarian(true, false))
                    println(Recipe3.isVegetarian(false, true))
                    println(Recipe3.totalMinutes(10, 25, 5))
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
        // oracle Java: 10.0 | none | mild | medium | hot | true | false | false | 40
        assertEquals("10.0\nnone\nmild\nmedium\nhot\ntrue\nfalse\nfalse\n40", output,
                "zero-drift: pricePerServing/spice/isVegetarian/totalMinutes id\u00eanticos ao Java");
    }

    @Test
    void beachDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o dom\u00eddio PRAIA — 4 shapes sem c\u00f3digo novo: o
        // sunscreenDoses com o produto double×double, o uvIndexLabel com
        // as faixas <= (low/moderate/high/very high — o label espaçado), o
        // safeToSwim com o && de COMPARAÇÃO+BOOLEAN (`waves <= 3 &&
        // lifeguard` — a comparação && o param nu) e o maxPeople com a
        // divisão inteira. Zero-drift executado: 75.0/low/moderate/high/
        // very high/true/false/false/50 — 9/9 id\u00eanticos ao Java — a
        // 80\u00aa valida\u00e7\u00e3o zero-c\u00f3digo.
        Path javaFile = dir.resolve("Beach.java");
        Files.writeString(javaFile, """
                public class Beach {
                    static double sunscreenDoses(double areaM2, double perM2) {
                        return areaM2 * perM2;
                    }
                    static String uvIndexLabel(int uv) {
                        if (uv <= 2) { return "low"; }
                        if (uv <= 5) { return "moderate"; }
                        if (uv <= 7) { return "high"; }
                        return "very high";
                    }
                    static boolean safeToSwim(int waves, boolean lifeguard) {
                        return waves <= 3 && lifeguard;
                    }
                    static int maxPeople(int areaM2, int perPerson) {
                        return areaM2 / perPerson;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Beach.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Beach.sunscreenDoses(2.5, 30.0))
                    println(Beach.uvIndexLabel(1))
                    println(Beach.uvIndexLabel(4))
                    println(Beach.uvIndexLabel(6))
                    println(Beach.uvIndexLabel(10))
                    println(Beach.safeToSwim(2, true))
                    println(Beach.safeToSwim(5, true))
                    println(Beach.safeToSwim(2, false))
                    println(Beach.maxPeople(200, 4))
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
        // oracle Java: 75.0 | low | moderate | high | very high | true | false | false | 50
        assertEquals("75.0\nlow\nmoderate\nhigh\nvery high\ntrue\nfalse\nfalse\n50", output,
                "zero-drift: sunscreenDoses/uvIndexLabel/safeToSwim/maxPeople id\u00eanticos ao Java");
    }

    @Test
    void trainDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o dom\u00eddio TREM — 4 shapes sem c\u00f3digo novo: o
        // ticketPrice com o produto double×double, o seatClass com as
        // faixas < (short/regional/long), o onTime com a comparação de
        // limite e o totalStops com o acumulador int. Zero-drift
        // executado: 87.5/short/regional/long/true/false/6 — 7/7
        // idênticos ao Java — a 81ª validação zero-código.
        Path javaFile = dir.resolve("Train.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Train {
                    static double ticketPrice(double km, double perKm) {
                        return km * perKm;
                    }
                    static String seatClass(double km) {
                        if (km < 100) { return "short"; }
                        if (km < 500) { return "regional"; }
                        return "long";
                    }
                    static boolean onTime(int minutesLate) {
                        return minutesLate <= 5;
                    }
                    static int totalStops(List<Integer> perStation) {
                        int sum = 0;
                        for (int s : perStation) {
                            sum += s;
                        }
                        return sum;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Train.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Train.ticketPrice(250.0, 0.35))
                    println(Train.seatClass(80.0))
                    println(Train.seatClass(300.0))
                    println(Train.seatClass(700.0))
                    println(Train.onTime(3))
                    println(Train.onTime(8))
                    var stops = listOf(2, 3, 1)
                    println(Train.totalStops(stops))
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
        // oracle Java: 87.5 | short | regional | long | true | false | 6
        assertEquals("87.5\nshort\nregional\nlong\ntrue\nfalse\n6", output,
                "zero-drift: ticketPrice/seatClass/onTime/totalStops idênticos ao Java");
    }

    @Test
    void laundryDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o domínio LAVANDERIA — 4 shapes sem código novo: o
        // costPerLoad com o DIVISOR PARENTESADO de soma, o cycleFor com as
        // faixas de equals (gentle/normal/heavy), o isFull com a
        // comparação de limite e o timeFor com o SWITCH-EXPRESSION de
        // String (gentle/normal/default). Zero-drift executado: 5.0/gentle/
        // normal/heavy/true/false/30/45/60 — 9/9 idênticos ao Java — a
        // 82ª validação zero-código.
        Path javaFile = dir.resolve("Laundry.java");
        Files.writeString(javaFile, """
                public class Laundry {
                    static double costPerLoad(double detergent, double electricity, int loads) {
                        return (detergent + electricity) / loads;
                    }
                    static String cycleFor(String fabric) {
                        if (fabric.equals("delicate")) { return "gentle"; }
                        if (fabric.equals("cotton")) { return "normal"; }
                        return "heavy";
                    }
                    static boolean isFull(int items, int capacity) {
                        return items >= capacity;
                    }
                    static int timeFor(String cycle) {
                        return switch (cycle) {
                            case "gentle" -> 30;
                            case "normal" -> 45;
                            default -> 60;
                        };
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Laundry.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Laundry.costPerLoad(10.0, 15.0, 5))
                    println(Laundry.cycleFor("delicate"))
                    println(Laundry.cycleFor("cotton"))
                    println(Laundry.cycleFor("jeans"))
                    println(Laundry.isFull(10, 10))
                    println(Laundry.isFull(5, 10))
                    println(Laundry.timeFor("gentle"))
                    println(Laundry.timeFor("normal"))
                    println(Laundry.timeFor("heavy"))
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
        // oracle Java: 5.0 | gentle | normal | heavy | true | false | 30 | 45 | 60
        assertEquals("5.0\ngentle\nnormal\nheavy\ntrue\nfalse\n30\n45\n60", output,
                "zero-drift: costPerLoad/cycleFor/isFull/timeFor idênticos ao Java");
    }

    @Test
    void pizza2DomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o domínio PIZZA-2 — 4 shapes sem código novo: o
        // pricePerSlice com a divisão double/int, o sizeFor com as faixas
        // <= (small/medium/large), o isVegetarian com o && de negação+positivo
        // (!pepperoni && mushrooms) e o totalSlices com o produto. Zero-drift
        // executado: 7.5/small/medium/large/true/false/false/24 — 8/8
        // idênticos ao Java — a 83ª validação zero-código.
        Path javaFile = dir.resolve("Pizza2.java");
        Files.writeString(javaFile, """
                public class Pizza2 {
                    static double pricePerSlice(double total, int slices) {
                        return total / slices;
                    }
                    static String sizeFor(int diameter) {
                        if (diameter <= 20) { return "small"; }
                        if (diameter <= 30) { return "medium"; }
                        return "large";
                    }
                    static boolean isVegetarian(boolean pepperoni, boolean mushrooms) {
                        return !pepperoni && mushrooms;
                    }
                    static int totalSlices(int pizzas, int perPizza) {
                        return pizzas * perPizza;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Pizza2.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Pizza2.pricePerSlice(60.0, 8))
                    println(Pizza2.sizeFor(18))
                    println(Pizza2.sizeFor(25))
                    println(Pizza2.sizeFor(35))
                    println(Pizza2.isVegetarian(false, true))
                    println(Pizza2.isVegetarian(true, true))
                    println(Pizza2.isVegetarian(false, false))
                    println(Pizza2.totalSlices(3, 8))
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
        // oracle Java: 7.5 | small | medium | large | true | false | false | 24
        assertEquals("7.5\nsmall\nmedium\nlarge\ntrue\nfalse\nfalse\n24", output,
                "zero-drift: pricePerSlice/sizeFor/isVegetarian/totalSlices idênticos ao Java");
    }

    @Test
    void swimmingDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o domínio NATAÇÃO — 4 shapes sem código novo: o
        // speed com a divisão double/double (o 1.6666666666666667 de 17
        // dígitos batendo), o level com as faixas <= (competitive/fit/
        // casual), o isOlympicPool com o == de CONSTANTE DOUBLE (50.0) e o
        // lapsFor com o CAST d2i sobre a cadeia (`(int) (km * 1000 /
        // poolLength)`). Zero-drift executado: 1.6666666666666667/
        // competitive/fit/casual/true/false/20 — 7/7 idênticos ao Java — a
        // 84ª validação zero-código.
        Path javaFile = dir.resolve("Swimming.java");
        Files.writeString(javaFile, """
                public class Swimming {
                    static double speed(double meters, double seconds) {
                        return meters / seconds;
                    }
                    static String level(int minutesPerKm) {
                        if (minutesPerKm <= 15) { return "competitive"; }
                        if (minutesPerKm <= 25) { return "fit"; }
                        return "casual";
                    }
                    static boolean isOlympicPool(double meters) {
                        return meters == 50;
                    }
                    static int lapsFor(double km, double poolLength) {
                        return (int) (km * 1000 / poolLength);
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Swimming.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Swimming.speed(100.0, 60.0))
                    println(Swimming.level(12))
                    println(Swimming.level(20))
                    println(Swimming.level(30))
                    println(Swimming.isOlympicPool(50.0))
                    println(Swimming.isOlympicPool(25.0))
                    println(Swimming.lapsFor(1.0, 50.0))
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
        // oracle Java: 1.6666666666666667 | competitive | fit | casual | true | false | 20
        assertEquals("1.6666666666666667\ncompetitive\nfit\ncasual\ntrue\nfalse\n20", output,
                "zero-drift: speed/level/isOlympicPool/lapsFor idênticos ao Java");
    }

    @Test
    void snookerDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o domínio SNOOKER — 4 shapes sem código novo: o
        // breakValue com o produto int×double, o frameState com as faixas
        // ==/<= (colors only/endgame/open — o label com espaço), o
        // isCentury com a comparação direta e o pointsToWin com a FÓRMULA
        // DE PRECEDÊNCIA MISTA (bestOf / 2 + 1 - framesPlayed — a divisão
        // antes da soma e da subtração). Zero-drift executado: 7.5/colors
        // only/endgame/open/true/false/3 — 7/7 idênticos ao Java — a 85ª
        // validação zero-código.
        Path javaFile = dir.resolve("Snooker.java");
        Files.writeString(javaFile, """
                public class Snooker {
                    static double breakValue(int balls, double perBall) {
                        return balls * perBall;
                    }
                    static String frameState(int redsLeft) {
                        if (redsLeft == 0) { return "colors only"; }
                        if (redsLeft <= 3) { return "endgame"; }
                        return "open";
                    }
                    static boolean isCentury(int breakScore) {
                        return breakScore >= 100;
                    }
                    static int pointsToWin(int framesPlayed, int bestOf) {
                        return bestOf / 2 + 1 - framesPlayed;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Snooker.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Snooker.breakValue(5, 1.5))
                    println(Snooker.frameState(0))
                    println(Snooker.frameState(2))
                    println(Snooker.frameState(7))
                    println(Snooker.isCentury(120))
                    println(Snooker.isCentury(80))
                    println(Snooker.pointsToWin(2, 9))
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
        // oracle Java: 7.5 | colors only | endgame | open | true | false | 3
        assertEquals("7.5\ncolors only\nendgame\nopen\ntrue\nfalse\n3", output,
                "zero-drift: breakValue/frameState/isCentury/pointsToWin idênticos ao Java");
    }

    @Test
    void flowControlDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o domínio CONTROLE-DE-FLUXO — 4 shapes sem código
        // novo: o findFirst com o early-return no for INDEXADO, o
        // sumUntilNegative com o BREAK no for-each, o skipZeros com o
        // CONTINUE no for-each (o contador pulando os zeros) e o
        // allPositive com o early-false. Os dois primeiros exercitam as
        // instruções de fluxo que mais arriscam código ERRADO compilável —
        // o zero-drift prova a semântica (10 = 4+6 para antes do -1; 3 =
        // os não-zeros). Executado: 2/-1/10/5/3/true/false — 7/7. A 86ª
        // validação zero-código.
        Path javaFile = dir.resolve("Queue2.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Queue2 {
                    static int findFirst(List<Integer> values, int target) {
                        for (int i = 0; i < values.size(); i++) {
                            if (values.get(i) == target) { return i; }
                        }
                        return -1;
                    }
                    static int sumUntilNegative(List<Integer> values) {
                        int sum = 0;
                        for (int v : values) {
                            if (v < 0) { break; }
                            sum += v;
                        }
                        return sum;
                    }
                    static int skipZeros(List<Integer> values) {
                        int count = 0;
                        for (int v : values) {
                            if (v == 0) { continue; }
                            count++;
                        }
                        return count;
                    }
                    static boolean allPositive(List<Integer> values) {
                        for (int v : values) {
                            if (v <= 0) { return false; }
                        }
                        return true;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Queue2.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    var vals = listOf(5, 8, 3, 8)
                    println(Queue2.findFirst(vals, 3))
                    println(Queue2.findFirst(vals, 9))
                    var mixed = listOf(4, 6, -1, 9)
                    println(Queue2.sumUntilNegative(mixed))
                    println(Queue2.sumUntilNegative(listOf(2, 3)))
                    var zeros = listOf(1, 0, 2, 0, 3)
                    println(Queue2.skipZeros(zeros))
                    println(Queue2.allPositive(vals))
                    println(Queue2.allPositive(listOf(1, -2)))
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
        // oracle Java: 2 | -1 | 10 | 5 | 3 | true | false
        assertEquals("2\n-1\n10\n5\n3\ntrue\nfalse", output,
                "zero-drift: findFirst/sumUntilNegative/skipZeros/allPositive — o break/continue com a semântica idêntica ao Java");
    }

    @Test
    void weather2DomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o domínio CLIMA-2 — 4 shapes sem código novo: o
        // windChill com a fórmula de sensação térmica, o jacket com as
        // faixas < (heavy/light/none), o isStorm com o && duplo de
        // comparações e o daysAbove com o loop sobre Double[] (o ARRAY
        // PRIMITIVO de doubles iterado no for-each — o contador de
        // acima-do-limite). Zero-drift executado: 4.0/heavy/light/none/
        // true/false/false/1 — 8/8 idênticos ao Java — a 87ª validação
        // zero-código.
        Path javaFile = dir.resolve("Weather2.java");
        Files.writeString(javaFile, """
                public class Weather2 {
                    static double windChill(double tempC, double windKmh) {
                        return tempC - windKmh * 0.05;
                    }
                    static String jacket(double tempC) {
                        if (tempC < 10) { return "heavy"; }
                        if (tempC < 20) { return "light"; }
                        return "none";
                    }
                    static boolean isStorm(double rainMm, double windKmh) {
                        return rainMm > 20 && windKmh > 50;
                    }
                    static int daysAbove(double[] temps, double threshold) {
                        int count = 0;
                        for (double t : temps) {
                            if (t > threshold) { count++; }
                        }
                        return count;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Weather2.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Weather2.windChill(5.0, 20.0))
                    println(Weather2.jacket(5.0))
                    println(Weather2.jacket(15.0))
                    println(Weather2.jacket(25.0))
                    println(Weather2.isStorm(25.0, 60.0))
                    println(Weather2.isStorm(25.0, 40.0))
                    println(Weather2.isStorm(10.0, 60.0))
                    var ts = new Double[3]
                    ts[0] = 25.0; ts[1] = 31.0; ts[2] = 28.0
                    println(Weather2.daysAbove(ts, 30.0))
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
        // oracle Java: 4.0 | heavy | light | none | true | false | false | 1
        assertEquals("4.0\nheavy\nlight\nnone\ntrue\nfalse\nfalse\n1", output,
                "zero-drift: windChill/jacket/isStorm/daysAbove idênticos ao Java");
    }

    @Test
    void textAnalysisDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (18/10): o domínio ANÁLISE-DE-TEXTO — 4 shapes sem código
        // novo: o countOccurrences com o loop de charAt, o truncate com o
        // corte + o sufixo, o startsWithVowel com a CADEIA DE 5 COMPARAÇÕES
        // DE CHAR ligadas por || (o teste de vogal — o maior encadeamento ||
        // de comparações no corpus) e o averageWordLength com o loop sobre
        // String[] + o GUARDA do vazio (0.0 na lista vazia). Zero-drift
        // executado: 3/ola m.../curto/true/false/4.0/0.0 — 7/7 idênticos ao
        // Java — a 88ª validação zero-código.
        Path javaFile = dir.resolve("Library2.java");
        Files.writeString(javaFile, """
                public class Library2 {
                    static int countOccurrences(String text, char target) {
                        int count = 0;
                        for (int i = 0; i < text.length(); i++) {
                            if (text.charAt(i) == target) { count++; }
                        }
                        return count;
                    }
                    static String truncate(String text, int maxLen) {
                        if (text.length() <= maxLen) { return text; }
                        return text.substring(0, maxLen) + "...";
                    }
                    static boolean startsWithVowel(String word) {
                        char first = word.charAt(0);
                        return first == 'a' || first == 'e' || first == 'i' || first == 'o' || first == 'u';
                    }
                    static double averageWordLength(String[] words) {
                        if (words.length == 0) { return 0; }
                        int total = 0;
                        for (String w : words) {
                            total += w.length();
                        }
                        return total / words.length;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Library2.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Library2.countOccurrences("banana", 'a'))
                    println(Library2.truncate("ola mundo", 5))
                    println(Library2.truncate("curto", 10))
                    println(Library2.startsWithVowel("abacate"))
                    println(Library2.startsWithVowel("pera"))
                    var ws = new String[3]
                    ws[0] = "ola"; ws[1] = "mundo"; ws[2] = "grande"
                    println(Library2.averageWordLength(ws))
                    var empty = new String[0]
                    println(Library2.averageWordLength(empty))
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
        // oracle Java: 3 | ola m... | curto | true | false | 4.0 | 0.0
        assertEquals("3\nola m...\ncurto\ntrue\nfalse\n4.0\n0.0", output,
                "zero-drift: countOccurrences/truncate/startsWithVowel/averageWordLength idênticos ao Java");
    }

    @Test
    void fitnessDomainRecovers2(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o domínio ACADEMIA-2 — 4 shapes sem código novo:
        // o bmi com o denominador ao quadrado (o double de 17 dígitos
        // batendo), o bmiCategory com as 4 faixas (underweight/normal/
        // overweight/obese), o caloriesBurned com a FÓRMULA MET DE 5
        // FATORES (`(int) (minutes * met * 3.5 * weightKg / 200)` — a
        // cadeia de multiplicação com a divisão e o cast no fim, o 294
        // exato) e o needsRest com o >= simples. Zero-drift executado:
        // 22.857142857142858/underweight/normal/overweight/obese/294/true/
        // false — 8/8 idênticos ao Java — a 89ª validação zero-código.
        Path javaFile = dir.resolve("Gym2.java");
        Files.writeString(javaFile, """
                public class Gym2 {
                    static double bmi(double weightKg, double heightM) {
                        return weightKg / (heightM * heightM);
                    }
                    static String bmiCategory(double bmi) {
                        if (bmi < 18.5) { return "underweight"; }
                        if (bmi < 25) { return "normal"; }
                        if (bmi < 30) { return "overweight"; }
                        return "obese";
                    }
                    static int caloriesBurned(int minutes, double met, double weightKg) {
                        return (int) (minutes * met * 3.5 * weightKg / 200);
                    }
                    static boolean needsRest(int consecutiveDays) {
                        return consecutiveDays >= 5;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Gym2.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Gym2.bmi(70.0, 1.75))
                    println(Gym2.bmiCategory(17.0))
                    println(Gym2.bmiCategory(22.0))
                    println(Gym2.bmiCategory(27.0))
                    println(Gym2.bmiCategory(35.0))
                    println(Gym2.caloriesBurned(30, 8.0, 70.0))
                    println(Gym2.needsRest(5))
                    println(Gym2.needsRest(3))
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
        // oracle Java: 22.857142857142858 | underweight | normal | overweight | obese | 294 | true | false
        assertEquals("22.857142857142858\nunderweight\nnormal\noverweight\nobese\n294\ntrue\nfalse", output,
                "zero-drift: bmi/bmiCategory/caloriesBurned/needsRest idênticos ao Java");
    }

    @Test
    void bank3DomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o domínio BANCO-3 — 4 shapes sem código novo: o
        // applyInterest com o juros condicional (o parêntese `1 + rate` no
        // multiplicador + o retorno do saldo inalterado no else), o
        // accountType com as quatro faixas (overdrawn/basic/standard/
        // premium), o canWithdraw com a CONDIÇÃO DUPLA REVERSA (`amount <=
        // balance && amount > 0` — o limite antes do positivo) e o fee com
        // o 2% condicional e o 0 promovido para double. Zero-drift
        // executado: 1050.0/-500.0/overdrawn/basic/standard/premium/true/
        // false/false/20.0/0.0 — 11/11 idênticos ao Java — a 90ª validação
        // zero-código.
        Path javaFile = dir.resolve("Bank3.java");
        Files.writeString(javaFile, """
                public class Bank3 {
                    static double applyInterest(double balance, double rate) {
                        if (balance > 0) { return balance * (1 + rate); }
                        return balance;
                    }
                    static String accountType(double balance) {
                        if (balance < 0) { return "overdrawn"; }
                        if (balance < 1000) { return "basic"; }
                        if (balance < 10000) { return "standard"; }
                        return "premium";
                    }
                    static boolean canWithdraw(double balance, double amount) {
                        return amount <= balance && amount > 0;
                    }
                    static double fee(double amount, int transactions) {
                        if (transactions > 10) { return amount * 0.02; }
                        return 0;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Bank3.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Bank3.applyInterest(1000.0, 0.05))
                    println(Bank3.applyInterest(-500.0, 0.05))
                    println(Bank3.accountType(-100.0))
                    println(Bank3.accountType(500.0))
                    println(Bank3.accountType(5000.0))
                    println(Bank3.accountType(50000.0))
                    println(Bank3.canWithdraw(1000.0, 500.0))
                    println(Bank3.canWithdraw(1000.0, 1500.0))
                    println(Bank3.canWithdraw(1000.0, -50.0))
                    println(Bank3.fee(1000.0, 15))
                    println(Bank3.fee(1000.0, 5))
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
        // oracle Java: 1050.0 | -500.0 | overdrawn | basic | standard | premium | true | false | false | 20.0 | 0.0
        assertEquals("1050.0\n-500.0\noverdrawn\nbasic\nstandard\npremium\ntrue\nfalse\nfalse\n20.0\n0.0", output,
                "zero-drift: applyInterest/accountType/canWithdraw/fee idênticos ao Java");
    }

    @Test
    void cameraDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o domínio CÂMERA — 4 shapes sem código novo: o
        // megapixels com a divisão por literal double (`width * height /
        // 1000000.0` — a promoção int×int para double na divisão, o
        // 12.0 e o 2.0736 exatos), o quality com as faixas < (low/medium/
        // high), o needsFlash com a comparação simples e o maxZoom com a
        // multiplicação int×int. Zero-drift executado: 12.0/2.0736/low/
        // medium/high/true/false/12 — 8/8 idênticos ao Java — a 91ª
        // validação zero-código.
        Path javaFile = dir.resolve("Camera.java");
        Files.writeString(javaFile, """
                public class Camera {
                    static double megapixels(int width, int height) {
                        return width * height / 1000000.0;
                    }
                    static String quality(int pixels) {
                        if (pixels < 2) { return "low"; }
                        if (pixels < 8) { return "medium"; }
                        return "high";
                    }
                    static boolean needsFlash(double lightLevel) {
                        return lightLevel < 50;
                    }
                    static int maxZoom(int opticalZoom, int digitalZoom) {
                        return opticalZoom * digitalZoom;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Camera.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Camera.megapixels(4000, 3000))
                    println(Camera.megapixels(1920, 1080))
                    println(Camera.quality(1))
                    println(Camera.quality(5))
                    println(Camera.quality(12))
                    println(Camera.needsFlash(30.0))
                    println(Camera.needsFlash(80.0))
                    println(Camera.maxZoom(3, 4))
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
        // oracle Java: 12.0 | 2.0736 | low | medium | high | true | false | 12
        assertEquals("12.0\n2.0736\nlow\nmedium\nhigh\ntrue\nfalse\n12", output,
                "zero-drift: megapixels/quality/needsFlash/maxZoom idênticos ao Java");
    }

    @Test
    void podcastDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o domínio PODCAST — 4 shapes sem código novo: o
        // totalDuration com o acumulador no loop e a divisão FORA dele
        // (`total / 60.0` — o 1.8333333333333333 de 17 dígitos batendo), o
        // popularityLabel com as faixas < (niche/growing/popular), o
        // isNewEpisode com a comparação de prazo e o averageMinutes com o
        // acumulador + o GUARDA do vazio (0 na lista vazia, o 27 exato).
        // Zero-drift executado: 1.8333333333333333/niche/growing/popular/
        // true/false/27/0 — 8/8 idênticos ao Java — a 92ª validação
        // zero-código.
        Path javaFile = dir.resolve("Podcast.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Podcast {
                    static double totalDuration(List<Integer> episodeMinutes) {
                        int total = 0;
                        for (int m : episodeMinutes) {
                            total += m;
                        }
                        return total / 60.0;
                    }
                    static String popularityLabel(int listens) {
                        if (listens < 100) { return "niche"; }
                        if (listens < 10000) { return "growing"; }
                        return "popular";
                    }
                    static boolean isNewEpisode(int daysSinceRelease) {
                        return daysSinceRelease <= 7;
                    }
                    static int averageMinutes(List<Integer> episodes) {
                        if (episodes.isEmpty()) { return 0; }
                        int total = 0;
                        for (int m : episodes) {
                            total += m;
                        }
                        return total / episodes.size();
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Podcast.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    var eps = listOf(25, 30, 35, 20)
                    println(Podcast.totalDuration(eps))
                    println(Podcast.popularityLabel(50))
                    println(Podcast.popularityLabel(5000))
                    println(Podcast.popularityLabel(50000))
                    println(Podcast.isNewEpisode(3))
                    println(Podcast.isNewEpisode(10))
                    println(Podcast.averageMinutes(eps))
                    println(Podcast.averageMinutes(listOf()))
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
        // oracle Java: 1.8333333333333333 | niche | growing | popular | true | false | 27 | 0
        assertEquals("1.8333333333333333\nniche\ngrowing\npopular\ntrue\nfalse\n27\n0", output,
                "zero-drift: totalDuration/popularityLabel/isNewEpisode/averageMinutes idênticos ao Java");
    }

    @Test
    void recipe4DomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o domínio RECEITA-4 — 4 shapes sem código novo: o
        // caloriesPerServing com a divisão inteira, o heatLevel com as
        // faixas < (simmer/medium heat/high heat — os labels espaçados), o
        // isVegan com o && TRIPLO de negações de params booleanos (!meat &&
        // !dairy && !eggs — o maior && de negações do corpus) e o
        // pricePerKg com a DIVISÃO ANINHADA no divisor (`total /
        // (weightGrams / 1000)` — a conversão g→kg dentro do divisor, o
        // 30.0 exato). Zero-drift executado: 300/simmer/medium heat/high
        // heat/true/false/false/false/30.0 — 9/9 idênticos ao Java — a
        // 93ª validação zero-código.
        Path javaFile = dir.resolve("Recipe4.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Recipe4 {
                    static int caloriesPerServing(int totalCalories, int servings) {
                        return totalCalories / servings;
                    }
                    static String heatLevel(double celsius) {
                        if (celsius < 100) { return "simmer"; }
                        if (celsius < 150) { return "medium heat"; }
                        return "high heat";
                    }
                    static boolean isVegan(boolean hasMeat, boolean hasDairy, boolean hasEggs) {
                        return !hasMeat && !hasDairy && !hasEggs;
                    }
                    static double pricePerKg(double total, double weightGrams) {
                        return total / (weightGrams / 1000);
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Recipe4.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Recipe4.caloriesPerServing(1200, 4))
                    println(Recipe4.heatLevel(80.0))
                    println(Recipe4.heatLevel(120.0))
                    println(Recipe4.heatLevel(200.0))
                    println(Recipe4.isVegan(false, false, false))
                    println(Recipe4.isVegan(true, false, false))
                    println(Recipe4.isVegan(false, true, false))
                    println(Recipe4.isVegan(false, false, true))
                    println(Recipe4.pricePerKg(15.0, 500.0))
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
        // oracle Java: 300 | simmer | medium heat | high heat | true | false | false | false | 30.0
        assertEquals("300\nsimmer\nmedium heat\nhigh heat\ntrue\nfalse\nfalse\nfalse\n30.0", output,
                "zero-drift: caloriesPerServing/heatLevel/isVegan/pricePerKg idênticos ao Java");
    }

    @Test
    void savingsDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o domínio POUPANÇA — 4 shapes sem código novo: o
        // monthlyInterest com a cadeia triple (`balance * annualRate / 12`
        // — o 6.0 exato), o goalStatus com o LIMIAR RELATIVO (`saved >=
        // target / 2` — a divisão DENTRO da comparação), o afterFees com a
        // subtração do percentual e o monthsToGoal com o CAST sobre a
        // expressão parentesada (`(int) ((target - saved) / monthly)` — o
        // 8 exato). Zero-drift executado: 6.0/reached/halfway/in progress/
        // 980.0/8 — 6/6 idênticos ao Java — a 94ª validação zero-código.
        Path javaFile = dir.resolve("Savings.java");
        Files.writeString(javaFile, """
                public class Savings {
                    static double monthlyInterest(double balance, double annualRate) {
                        return balance * annualRate / 12;
                    }
                    static String goalStatus(double saved, double target) {
                        if (saved >= target) { return "reached"; }
                        if (saved >= target / 2) { return "halfway"; }
                        return "in progress";
                    }
                    static double afterFees(double amount, double feePercent) {
                        return amount - amount * feePercent;
                    }
                    static int monthsToGoal(double saved, double target, double monthly) {
                        return (int) ((target - saved) / monthly);
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Savings.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Savings.monthlyInterest(1200.0, 0.06))
                    println(Savings.goalStatus(1000.0, 1000.0))
                    println(Savings.goalStatus(600.0, 1000.0))
                    println(Savings.goalStatus(100.0, 1000.0))
                    println(Savings.afterFees(1000.0, 0.02))
                    println(Savings.monthsToGoal(200.0, 1000.0, 100.0))
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
        // oracle Java: 6.0 | reached | halfway | in progress | 980.0 | 8
        assertEquals("6.0\nreached\nhalfway\nin progress\n980.0\n8", output,
                "zero-drift: monthlyInterest/goalStatus/afterFees/monthsToGoal idênticos ao Java");
    }

    @Test
    void garden2DomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o domínio JARDIM-2 — 4 shapes sem código novo: o
        // plantsFor com o cast sobre a divisão (`(int) (area / perPlant)` —
        // o 24 exato), o wateringSchedule com a DISJUNÇÃO DE IGUALDADES
        // (`day == 1 || day == 4` — o padrão de agendamento), o
        // needsFertilizer com a DISJUNÇÃO DE FAIXA DUPLO-LADO (`soilPH < 6.0
        // || soilPH > 7.5` — o teste do fora-do-intervalo, o complemento do
        // && de intervalo) e o growthRate com a subtração no numerador
        // (`(current - start) / days` — o 4.0 exato). Zero-drift executado:
        // 24/water/water/rest/true/false/true/4.0 — 8/8 idênticos ao Java
        // — a 95ª validação zero-código.
        Path javaFile = dir.resolve("Garden2.java");
        Files.writeString(javaFile, """
                public class Garden2 {
                    static int plantsFor(double area, double perPlant) {
                        return (int) (area / perPlant);
                    }
                    static String wateringSchedule(int dayOfWeek) {
                        if (dayOfWeek == 1 || dayOfWeek == 4) { return "water"; }
                        return "rest";
                    }
                    static boolean needsFertilizer(double soilPH) {
                        return soilPH < 6.0 || soilPH > 7.5;
                    }
                    static double growthRate(double currentHeight, double startHeight, int days) {
                        return (currentHeight - startHeight) / days;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Garden2.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Garden2.plantsFor(12.0, 0.5))
                    println(Garden2.wateringSchedule(1))
                    println(Garden2.wateringSchedule(4))
                    println(Garden2.wateringSchedule(2))
                    println(Garden2.needsFertilizer(5.5))
                    println(Garden2.needsFertilizer(7.0))
                    println(Garden2.needsFertilizer(8.0))
                    println(Garden2.growthRate(30.0, 10.0, 5))
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
        // oracle Java: 24 | water | water | rest | true | false | true | 4.0
        assertEquals("24\nwater\nwater\nrest\ntrue\nfalse\ntrue\n4.0", output,
                "zero-drift: plantsFor/wateringSchedule/needsFertilizer/growthRate idênticos ao Java");
    }

    @Test
    void shippingDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (17/10): o domínio FRETE — 4 shapes sem código novo: o cost
        // com o produto peso×tarifa, o zone com as faixas de CHAR (N/S/E/W
        // — a tabela de regiões), o isExpress com a comparação de prazo e
        // o total com a soma simples. Zero-drift executado: 25.0/north/
        // south/east/west/true/false/125.0 — 8/8 idênticos ao Java — a
        // 95ª validação zero-código.
        Path javaFile = dir.resolve("Shipping.java");
        Files.writeString(javaFile, """
                public class Shipping {
                    static double cost(double weightKg, double ratePerKg) {
                        return weightKg * ratePerKg;
                    }
                    static String zone(char region) {
                        if (region == 'N') { return "north"; }
                        if (region == 'S') { return "south"; }
                        if (region == 'E') { return "east"; }
                        return "west";
                    }
                    static boolean isExpress(int days) {
                        return days <= 2;
                    }
                    static double total(double subtotal, double shippingCost) {
                        return subtotal + shippingCost;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Shipping.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Shipping.cost(10.0, 2.5))
                    println(Shipping.zone('N'))
                    println(Shipping.zone('S'))
                    println(Shipping.zone('E'))
                    println(Shipping.zone('W'))
                    println(Shipping.isExpress(1))
                    println(Shipping.isExpress(5))
                    println(Shipping.total(100.0, 25.0))
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
        // oracle Java: 25.0 | north | south | east | west | true | false | 125.0
        assertEquals("25.0\nnorth\nsouth\neast\nwest\ntrue\nfalse\n125.0", output,
                "zero-drift: cost/zone/isExpress/total idênticos ao Java");
    }

    @Test
    void screenDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (18/10): o domínio TELA + a fatia do MATH.SQRT com
        // expressão Int — o javac emite I2D quando o arg do Math.sqrt é
        // uma expressão Int (ppi: Math.sqrt(w*w + h*h)), o decompiler
        // convertia para `.toDouble()` mas a resolução do namespace do
        // math NÃO reconhece o tipo do resultado do método (SEM025) — o
        // idioma que compila é o cast `as Double` (medido). Os shapes: o
        // ppi com o SQRT + a divisão pela diagonal (o 102.460798608479 de
        // 15 dígitos batendo), o sizeCategory com as faixas (low/standard/
        // retina), o isWide com a comparação de dimensões e o totalPixels
        // com o produto (2073600). Zero-drift executado: 102.460798608479/
        // low/standard/retina/true/false/2073600 — 7/7 idênticos ao Java —
        // a 96ª validação.
        Path javaFile = dir.resolve("Screen.java");
        Files.writeString(javaFile, """
                public class Screen {
                    static double pixelsPerInch(int width, int height, double diagonalInches) {
                        double diagonalPixels = Math.sqrt(width * width + height * height);
                        return diagonalPixels / diagonalInches;
                    }
                    static String sizeCategory(double ppi) {
                        if (ppi < 150) { return "low"; }
                        if (ppi < 300) { return "standard"; }
                        return "retina";
                    }
                    static boolean isWide(int width, int height) {
                        return width > height;
                    }
                    static int totalPixels(int width, int height) {
                        return width * height;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Screen.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        assertTrue(kof.contains(" as Double"), "o sqrt com o cast:\n" + kof);
        String kofRun = kof + """
                main() {
                    println(Screen.pixelsPerInch(1920, 1080, 21.5))
                    println(Screen.sizeCategory(100.0))
                    println(Screen.sizeCategory(200.0))
                    println(Screen.sizeCategory(400.0))
                    println(Screen.isWide(1920, 1080))
                    println(Screen.isWide(1080, 1920))
                    println(Screen.totalPixels(1920, 1080))
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
        // oracle Java: 102.460798608479 | low | standard | retina | true | false | 2073600
        assertEquals("102.460798608479\nlow\nstandard\nretina\ntrue\nfalse\n2073600", output,
                "zero-drift: pixelsPerInch/sizeCategory/isWide/totalPixels idênticos ao Java");
    }

    @Test
    void pharmacyDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (22/10): o domínio FARMÁCIA — o % MODULO como forma nova do
        // sweep (primeiro uso nos domínios) + a promoção de int para Double
        // na aritmética mista via o cast `as Double` (o I2D do javac, o mesmo
        // mecanismo do sqrt da rodada SCREEN), a cadeia if/else de 4 faixas,
        // o || de boolean-param com comparação e o local mutável
        // reatribuído dentro do if. Zero-drift executado: 100.0/drops/syrup/
        // tablet/capsule/true/true/false/4/5 — 10/10 idênticos ao Java.
        Path javaFile = dir.resolve("Pharmacy.java");
        Files.writeString(javaFile, """
                public class Pharmacy {
                    static double pediatricDose(int weightKg, double adultDose) {
                        return adultDose * weightKg / 70.0;
                    }
                    static String dosageForm(int age) {
                        if (age <= 2) { return "drops"; }
                        if (age <= 12) { return "syrup"; }
                        if (age <= 65) { return "tablet"; }
                        return "capsule";
                    }
                    static boolean needsPrescription(boolean controlled, int refillsLeft) {
                        return controlled || refillsLeft <= 0;
                    }
                    static int pillsForDays(int pillsPerDay, int totalPills) {
                        int days = totalPills / pillsPerDay;
                        if (totalPills % pillsPerDay > 0) { days = days + 1; }
                        return days;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Pharmacy.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Pharmacy.pediatricDose(14, 500.0))
                    println(Pharmacy.dosageForm(1))
                    println(Pharmacy.dosageForm(8))
                    println(Pharmacy.dosageForm(40))
                    println(Pharmacy.dosageForm(70))
                    println(Pharmacy.needsPrescription(true, 3))
                    println(Pharmacy.needsPrescription(false, 0))
                    println(Pharmacy.needsPrescription(false, 5))
                    println(Pharmacy.pillsForDays(3, 10))
                    println(Pharmacy.pillsForDays(2, 10))
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
        // oracle Java: 100.0 | drops | syrup | tablet | capsule | true | true | false | 4 | 5
        assertEquals("100.0\ndrops\nsyrup\ntablet\ncapsule\ntrue\ntrue\nfalse\n4\n5", output,
                "zero-drift: pediatricDose/dosageForm/needsPrescription/pillsForDays idênticos ao Java");
    }

    @Test
    void cipherDomainRecovers(@TempDir Path dir) throws Exception {
        // Fase E (22/10): o domínio CIFRA — a fatia do NARROWING 0x91-0x93
        // (i2b/i2c/i2s) na linha linear + a declaração COM ESCOPO DE RAMO.
        // O shiftChar do Cipher tinha `int pos = c - 'a'; return (char)
        // ('a' + (pos + shift) % 26);` em dois ramos de um else-if — dois
        // defeitos: (1) o i2c do ramo era `emitLinear-null:0x92` (a linha
        // linear recusava o narrowing inteiro porque o comentário
        // descrevia o código PRE-§695; o §695/#471 deu ao Kof
        // `as Byte`/`as Short`/`as Char` a semântica fiel — medido:
        // 300 as Byte=44, 256 as Byte=0, 70000 as Short=4464, os cinco
        // idênticos ao Java); (2) o slot 2 era armazenado nos dois ramos
        // (dois `int pos` com escopos separados no Java, um slot no
        // bytecode) — o var nascia no 1º ramo e o 2º use saía bare
        // (SEM011); o snapshot/restore do declared no ramo genérico dá a
        // cada ramo a sua declaração (o Kof aceita o mesmo nome em blocos
        // irmãos — medido no Shadow.kf). O collatzSteps (do-while com if
        // no corpo) segue o stub honesto (o loop-body-separate — a fatia
        // do do-while multi-bloco é a próxima). Zero-drift executado:
        // k/K/khoor zruog/IFMMP/true/false — 6/6 idênticos ao Java.
        Path javaFile = dir.resolve("Cipher.java");
        Files.writeString(javaFile, """
                public class Cipher {
                    static char shiftChar(char c, int shift) {
                        if (c >= 'a' && c <= 'z') {
                            int pos = c - 'a';
                            return (char) ('a' + (pos + shift) % 26);
                        }
                        if (c >= 'A' && c <= 'Z') {
                            int pos = c - 'A';
                            return (char) ('A' + (pos + shift) % 26);
                        }
                        return c;
                    }
                    static String encode(String text, int shift) {
                        String result = "";
                        for (int i = 0; i < text.length(); i++) {
                            result = result + shiftChar(text.charAt(i), shift);
                        }
                        return result;
                    }
                    static boolean isPalindrome(String word) {
                        int left = 0;
                        int right = word.length() - 1;
                        while (left < right) {
                            if (word.charAt(left) != word.charAt(right)) return false;
                            left++;
                            right--;
                        }
                        return true;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Cipher.class"));
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + """
                main() {
                    println(Cipher.shiftChar('h', 3))
                    println(Cipher.shiftChar('H', 3))
                    println(Cipher.encode("hello world", 3))
                    println(Cipher.encode("HELLO", 1))
                    println(Cipher.isPalindrome("level"))
                    println(Cipher.isPalindrome("hello"))
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
        // oracle Java: k | K | khoor zruog | IFMMP | true | false
        assertEquals("k\nK\nkhoor zruog\nIFMMP\ntrue\nfalse", output,
                "zero-drift: shiftChar/encode/isPalindrome idênticos ao Java");
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
    void doWhileBodyRecovers(@TempDir Path dir) throws Exception {
        // Fase E (22/10): o do-while com corpo MULTI-BLOCO — o teste do
        // loop mora no bloco final (back edge de CONDICIONAL ao header),
        // não no header; o header é o if INTERNO do corpo. Antes: o while
        // top-tested tomava a condição do if interno como condição do
        // loop (errado) e o fechamento caía no loop-body-separate (stub).
        // Agora: `do { if...else... } while (cond)` zero-drift, incluindo
        // a cauda do contador no próprio bloco do teste (steps).
        Path javaFile = dir.resolve("Lw.java");
        Files.writeString(javaFile, """
                public class Lw {
                    static int collatz(int n) {
                        do {
                            if (n % 2 == 0) n = n / 2;
                            else n = 3 * n + 1;
                        } while (n != 1);
                        return n;
                    }
                    static int parityWalk(int n) {
                        do {
                            if (n % 2 == 0) n = n / 2;
                            else n = n - 1;
                        } while (n > 0);
                        return n;
                    }
                    static int countDown(int n) {
                        do {
                            if (n > 0) n = n - 1;
                        } while (n > 0);
                        return n;
                    }
                    static int decUntil(int n) {
                        do {
                            n = n - 1;
                        } while (n > 0);
                        return n;
                    }
                    static int steps(int n) {
                        int count = 0;
                        do {
                            if (n % 2 == 0) n = n / 2;
                            else n = n - 1;
                            count++;
                        } while (n > 0);
                        return count;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Lw.class"));
        assertFalse(kof.contains("body not recovered"),
                "do-while com corpo multi-bloco deve recuperar (nao stub):\n" + kof);
        assertTrue(kof.contains("do {"), "o wrap `do {`:\n" + kof);
        assertTrue(kof.contains("} while (arg0 != 1)"), "a cond do collatz no FIM:\n" + kof);
        assertTrue(kof.contains("} while (arg0 > 0)"), "as conds do while no FIM:\n" + kof);
        assertTrue(kof.contains("v1 = v1 + 1"), "a cauda do contador no corpo:\n" + kof);
        // zero-drift executado (oráculo Java: 1/1/0/0/0/0/0/5/1/5)
        String kofRun = kof + "\nmain() {\n    var lw = Lw()\n"
                + "    println(lw.collatz(6))\n    println(lw.collatz(1))\n"
                + "    println(lw.parityWalk(10))\n    println(lw.parityWalk(7))\n"
                + "    println(lw.countDown(5))\n    println(lw.countDown(0))\n"
                + "    println(lw.decUntil(3))\n"
                + "    println(lw.steps(10))\n    println(lw.steps(0))\n    println(lw.steps(7))\n}\n";
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
        assertEquals("1\n1\n0\n0\n0\n0\n0\n5\n1\n5", output,
                "zero-drift: os do-whiles como no Java");
    }

    @Test
    void doWhileContinueRecovers(@TempDir Path dir) throws Exception {
        // Fase E (24/10): o CONTINUE dentro do corpo multi-blocos do
        // do-while — o braço verdadeiro do if salta direto ao bloco do
        // teste (o continue é um goto ao teste), então os DOIS braços
        // alcançam o bloco do teste e a detecção do bottom-tested
        // dispara; o braço do continue sai como o THEN VAZIO do
        // if/else invertido (`if (c) { } else { corpo }`) — fiel ao
        // Java sem código novo. O BREAK (braço que sai ao pós-loop)
        // NÃO dispara a detecção (o braço não alcança o teste) — segue
        // o stub honesto `loop-body-separate` (medido; a fatia do
        // caminho genérico vem depois).
        Path javaFile = dir.resolve("Scan.java");
        Files.writeString(javaFile, """
                public class Scan {
                    static int countNonZero(int n) {
                        int seen = 0;
                        do {
                            if (n == 0) continue;
                            seen = seen + 1;
                            n = n - 1;
                        } while (n > 0);
                        return seen;
                    }
                    static int sumNonZero(int n) {
                        int sum = 0;
                        do {
                            if (n == 0) continue;
                            sum = sum + n;
                            n = n - 1;
                        } while (n > 0);
                        return sum;
                    }
                    static int tallyPairs(int n) {
                        int pairs = 0;
                        int left = n;
                        do {
                            if (left < 2) continue;
                            pairs = pairs + 1;
                            left = left - 2;
                        } while (left > 1);
                        return pairs;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Scan.class"));
        assertFalse(kof.contains("body not recovered"),
                "o continue no corpo do do-while deve recuperar (nao stub):\n" + kof);
        assertTrue(kof.contains("do {"), "o wrap `do {`:\n" + kof);
        assertTrue(kof.contains("} while (arg0 > 0)"), "as conds no FIM:\n" + kof);
        assertTrue(kof.contains("if (arg0 == 0) {\n        } else {")
                || kof.contains("if (v2 < 2) {\n        } else {"),
                "o braço vazio do continue:\n" + kof);
        // zero-drift executado (oráculo Java: 4/0/1/10/0/2/2/0/0)
        String kofRun = kof + "\nmain() {\n    var s = Scan()\n"
                + "    println(s.countNonZero(4))\n    println(s.countNonZero(0))\n    println(s.countNonZero(1))\n"
                + "    println(s.sumNonZero(4))\n    println(s.sumNonZero(0))\n"
                + "    println(s.tallyPairs(5))\n    println(s.tallyPairs(4))\n"
                + "    println(s.tallyPairs(0))\n    println(s.tallyPairs(1))\n}\n";
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
        assertEquals("4\n0\n1\n10\n0\n2\n2\n0\n0", output,
                "zero-drift: os do-whiles com continue como no Java");
    }

    @Test
    void doWhileBreakRecovers(@TempDir Path dir) throws Exception {
        // Fase E (24/10): o BREAK dentro do corpo multi-blocos do
        // do-while — o braço do break NÃO alcança o bloco do teste, então
        // a detecção aceita agora os dois formatos de saída: (a) o bloco
        // `goto saída` (o break como o THEN — firstOddStop, dropTo), (b)
        // o `else break` do javac que ramifica DIRETO ao bloco de saída
        // sem goto (clampSmall — B@0 succ[13,5] com 13 = o retorno); o
        // caminho genérico para os braços no bloco do teste (o pendente
        // carrega o início do teste) e emite `break` no ramo que É a
        // saída, continuando no teste depois do if/else para o wrap.
        // O braço return (succ vazio) NÃO entra — segue sem detecção.
        Path javaFile = dir.resolve("Halt.java");
        Files.writeString(javaFile, """
                public class Halt {
                    static int firstOddStop(int n) {
                        do {
                            if (n % 2 != 0) break;
                            n = n - 2;
                        } while (n > 0);
                        return n;
                    }
                    static int clampSmall(int n) {
                        do {
                            if (n > 3) n = n - 3;
                            else break;
                        } while (n > 0);
                        return n;
                    }
                    static int dropTo(int n, int stop) {
                        do {
                            if (n <= stop) {
                                n = stop;
                                break;
                            }
                            n = n - 1;
                        } while (n > stop);
                        return n;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Halt.class"));
        assertFalse(kof.contains("body not recovered"),
                "o break no corpo do do-while deve recuperar (nao stub):\n" + kof);
        assertTrue(kof.contains("do {"), "o wrap `do {`:\n" + kof);
        assertTrue(kof.contains("} while (arg0 > 0)"), "as conds no FIM:\n" + kof);
        assertTrue(kof.contains("break"), "o break emitido:\n" + kof);
        // zero-drift executado (oráculo Java: 0/7/-2/1/2/2/3/3/0)
        String kofRun = kof + "\nmain() {\n    var h = Halt()\n"
                + "    println(h.firstOddStop(10))\n    println(h.firstOddStop(7))\n    println(h.firstOddStop(0))\n"
                + "    println(h.clampSmall(10))\n    println(h.clampSmall(2))\n    println(h.clampSmall(5))\n"
                + "    println(h.dropTo(10, 3))\n    println(h.dropTo(2, 3))\n    println(h.dropTo(10, 0))\n}\n";
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
        assertEquals("0\n7\n-2\n1\n2\n2\n3\n3\n0", output,
                "zero-drift: os do-whiles com break como no Java");
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

        // Fase E (22/10): os TRÊS narrowings recuperam — o §695/#471 deu ao
        // Kof `as Byte`/`as Short`/`as Char` a semântica FIEL do Java (o
        // JVM emite i2b/i2s/i2c reais; x86 e riscv idem). O comentário
        // antigo de "i2c é o único fiel" descrevia o código PRE-§695.
        // Medido: 300 as Byte=44, 256 as Byte=0, -1 as Byte=-1,
        // 70000 as Short=4464, 70000 as Byte=112 — idênticos ao Java.
        assertTrue(kof.contains("Char toChar(Int arg0) = ((arg0 + 65) as Char)"),
                "i2c deve recuperar as Char:\n" + kof);
        assertTrue(kof.contains("Byte toByte(Int arg0) = (arg0 as Byte)"),
                "i2b deve recuperar as Byte (fiel desde o §695):\n" + kof);
        assertTrue(kof.contains("Short toShort(Int arg0) = (arg0 as Short)"),
                "i2s deve recuperar as Short (fiel desde o §695):\n" + kof);

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