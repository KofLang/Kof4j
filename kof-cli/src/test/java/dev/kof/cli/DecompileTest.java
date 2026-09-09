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
        assertTrue(kof.contains("Int m(Long arg0, Int arg1, Int arg2) = arg1 < arg2"),
                "wide long empurra slots dos ints:\n" + kof);
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