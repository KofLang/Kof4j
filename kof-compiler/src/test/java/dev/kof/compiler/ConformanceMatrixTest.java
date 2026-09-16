package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Conformance Matrix (Fase 9 do plano de plataforma —
 * docs/bugs-and-gaps/conformance-matrix.md): cada caso trava a MESMA saída
 * esperada nos 4 targets — JVM (bytecode), Native (x86_64), Script
 * (interpretador de IR) e KofJS (GraalJS).
 *
 * Regra: a saída esperada é a do COMPORTAMENTO DOCUMENTADO (corpus +
 * backend-parity.md), não "o que o JVM imprime". Célula PARTIAL da matriz
 * = target excluído da asserção + bug registrado em known-bugs.md (ref no
 * comentário). Casos determinísticos apenas — concorrência/tempo têm
 * suítes próprias (SpawnE2ETest, KofTimeE2ETest).
 */
class ConformanceMatrixTest {

    private record TargetResult(int exit, String out) {}

    private static String norm(String s) {
        return s == null ? "" : s.replace("\r\n", "\n").trim();
    }

    // Driver fresco por compilação: isolamento defensivo (cada caso é um
    // processo independente no CLI real). O vazamento de estado entre
    // compilações (bug 51) foi corrigido em CompilerDriverState
    // .resetForCompilation, mas manter um driver por caso continua sendo a
    // prática correta para testes de paridade.
    private CompilerDriver freshDriver() {
        return new CompilerDriver();
    }

    private TargetResult runJvm(Path source, Path outDir) throws IOException {
        CompilationResult r = freshDriver().compile(source, outDir, Target.JVM);
        assertTrue(r.success(), "JVM compile: " + r.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder(System.getProperty("java.home") + "/bin/java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int ec = p.waitFor();
            return new TargetResult(ec, norm(out));
        } catch (InterruptedException e) {
            throw new IOException("interrupted", e);
        }
    }

    private TargetResult runScript(Path source, Path dir) {
        try {
            KofInterpreter.Result r = freshDriver().interpret(List.of(source), dir, new String[0]);
            return new TargetResult(r.exitCode(), norm(r.stdout()));
        } catch (KofInterpretException e) {
            return new TargetResult(-1, "FRONTEND-ERR");
        }
    }

    private TargetResult runNative(Path source, Path outDir) throws IOException {
        CompilationResult r = freshDriver().compile(source, outDir, Target.NATIVE);
        assertTrue(r.success(), "Native compile: " + r.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binário deve existir");
        try {
            ProcessBuilder pb = new ProcessBuilder(bin.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int ec = p.waitFor();
            return new TargetResult(ec, norm(out));
        } catch (InterruptedException e) {
            throw new IOException("interrupted", e);
        }
    }

    private TargetResult runJs(Path source, Path outDir) throws IOException {
        CompilationResult r = freshDriver().compile(source, outDir, Target.JS);
        assertTrue(r.success(), "JS compile: " + r.diagnostics().getDiagnostics());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                (InputStream) new ByteArrayInputStream(new byte[0]), out);
        return new TargetResult(ec, norm(out.toString()));
    }

    /**
     * Trava o MESMO output nos 4 targets. partial = targets com bug
     * registrado (célula PARTIAL da matriz) — excluídos da asserção,
     * cada um com o ref do bug.
     */
    private void matrix(String name, String program, String expected,
                        Set<String> partial, Path tempDir) throws IOException {
        Path dir = tempDir.resolve(name);
        Files.createDirectories(dir);
        Path source = dir.resolve("Main.kf");
        Files.writeString(source, program);
        var problemas = new StringBuilder();
        if (!partial.contains("jvm")) {
            TargetResult t = runJvm(source, dir.resolve("jvm"));
            if (t.exit() != 0 || !expected.equals(t.out()))
                problemas.append(" JVM(exit=").append(t.exit()).append(")=<").append(t.out()).append(">");
        }
        if (!partial.contains("native")) {
            TargetResult t = runNative(source, dir.resolve("nat"));
            if (t.exit() != 0 || !expected.equals(t.out()))
                problemas.append(" NATIVE(exit=").append(t.exit()).append(")=<").append(t.out()).append(">");
        }
        if (!partial.contains("script")) {
            TargetResult t = runScript(source, dir);
            if (t.exit() != 0 || !expected.equals(t.out()))
                problemas.append(" SCRIPT(exit=").append(t.exit()).append(")=<").append(t.out()).append(">");
        }
        if (!partial.contains("js")) {
            TargetResult t = runJs(source, dir.resolve("js"));
            if (t.exit() != 0 || !expected.equals(t.out()))
                problemas.append(" JS(exit=").append(t.exit()).append(")=<").append(t.out()).append(">");
        }
        assertEquals("", problemas.toString().trim(),
                "[" + name + "] esperado <" + expected + "> — divergências:");
    }

    // ===== Lote 1 — linguagem core (docs/bugs-and-gaps/conformance-matrix.md) =====

    @Test
    void conformanceCoreArithmetic(@TempDir Path tempDir) throws IOException {
        matrix("arith", """
                main() {
                    var a = 2147483647
                    println(a + 1)
                    println(-7 % 3)
                    println(7 % -3)
                }
                """, "-2147483648\n-1\n1", Set.of(), tempDir);
        matrix("longdiv", """
                main() {
                    var a = 10000000000L
                    println(a / 3L)
                    println(a % 7L)
                }
                """, "3333333333\n4", Set.of(), tempDir);
        // §146 (12/09, #101): Double % com operando VARIÁVEL devolvia o
        // dividendo no Native x86 (MOD caía no `default` do bloco Double,
        // que reempurra xmm0; só o fold de literais acertava). fmod em SSE2
        // (kof_double_mod, sem libm): q=trunc(a/b), resto=a-q*b; NaN/Inf/0
        // espelham o JVM (bug 101 congela só os RELACIONAIS com NaN).
        // JS excluído: String(1.0)="1" no JS vs "1.0" no JVM (bug 44,
        // floatprint/negzero — o resto 1.0 imprime sem o ".0").
        matrix("doublemod", """
                main() {
                    var a = 7.5
                    var b = 2.0
                    println(a % b)
                    println(7.5 % 2.0)
                    println(10.0 % 3.0)
                    println(0.5 % 1.0)
                    println(-7.5 % 2.0)
                    println(7.5 % -2.0)
                    var z = 0.0
                    println(7.5 % z)
                    var inf = 1.0 / z
                    println(inf % 2.0)
                    var nan = z / z
                    println(nan % 2.0)
                }
                """, "1.5\n1.5\n1.0\n0.5\n-1.5\n1.5\nNaN\nNaN\nNaN", Set.of("js"), tempDir);
        matrix("cast", """
                main() {
                    var d = 9.9
                    println(d as Int)
                    var l = 70000L
                    println(l as Int)
                    println(66 as Char)
                }
                """, "9\n70000\n66", Set.of(), tempDir);
        // §110 (paridade absoluta, JVM literal-emitter): -0.0 em JVM virava
        // +0.0 — `emitLoadDouble`/`emitLoadFloat` testavam `value == 0.0`,
        // e IEEE casa -0.0 == 0.0 → DCONST_0 colapsava o sinal (literal
        // `-0.0`, fold de `-1.0 * 0.0` e negação de resultado de fold).
        // Native/Script nunca colapsaram (guard por raw bits). `==` de
        // signed zero continua true (congelado §94) — a célula imprime os
        // spellings, não troca o contrato de comparação.
        matrix("negzero", """
                main() {
                    println(0.0)
                    println(-0.0)
                    val z = 0.0
                    println(-z)
                    val a = -1.0
                    val b = 0.0
                    println(a * b)
                    println(-1.0 * 0.0)
                    println(0.0 == -0.0)
                }
                """, "0.0\n-0.0\n-0.0\n-0.0\n-0.0\ntrue", Set.of("js"), tempDir);
        // bug 44 CORRIGIDO 10/09 (x86_64): kof_print_double/float via snprintf
        // %.16g + append '.0' p/ inteiro-válido + write via syscall (sem
        // printf/reordenação) — Native desbloqueado. KofJS mantém a exclusão:
        // doc "parece bug mas é esperado" (JS String(5.0) = "5").
        matrix("floatprint", """
                main() {
                    println(1.0 / 3.0)
                    println(2.5 * 2.0)
                    println(7.0 / 2.0)
                }
                """, "0.3333333333333333\n5.0\n3.5", Set.of("js"), tempDir);
        // bug 44 (residual, x86_64, paridade regra 5): o glibc %.16g escreve
        // 'inf'/'-inf'/'nan' mas o contrato é JDK Double.toString →
        // 'Infinity'/'-Infinity'/'NaN' (o que JVM/Script imprimem). O println
        // boxa via kof_double_to_string (RuntimeStringConv); o print sem box via
        // kof_print_double (RuntimePrintNum). As 2 faces + float + concat
        // String.valueOf. JS mantém a exclusão (idêntica ao floatprint:
        // String(5.0) = "5" no JS, "5.0" no JVM — a divergência é o '.0', não
        // o spelling de inf/nan, que o JS já casa).
        matrix("infinityprint", """
                main() {
                    println(1.0 / 0.0)
                    println(-1.0 / 0.0)
                    println(0.0 / 0.0)
                    println(1e38f * 1e38f)
                    print(1.0 / 0.0)
                    print(" ")
                    print(0.0 / 0.0)
                    println("")
                    println("v=" + (0.0 / 0.0))
                }
                """, "Infinity\n-Infinity\nNaN\nInfinity\nInfinity NaN\nv=NaN",
                Set.of("js"), tempDir);
        // §180 ✅ CORRIGIDO 14/09 (DECISIONS §6): o contrato é JDK
        // Double.toString/Float.toString — shortest-round-trip
        // (0.1+0.2 = 0.30000000000000004), notação científica (|x|>=1e7 ou
        // <1e-3, spelling 1.0E7/1.0E-5) e Float com repr própria
        // (1.0f/3.0f = 0.33333334, não a expansão double 0.3333333432674408).
        // O x86_64 agora usa `kof_dtoa` (RuntimeDtoa: loop `%.*e`+strtod p/ o
        // shortest + reformat p/ o limiar/estilo do Java); a exclusão do Native
        // CAIU. JS segue excluído (Number.toString não emite '.0' nem notação
        // científica no mesmo limiar — §44).
        matrix("doubleprint", """
                main() {
                    println(0.1 + 0.2)
                    println(1e7)
                    println(1e-5)
                    println(100.0 / 3.0)
                    println(1.0f / 3.0f)
                    println(1.0e20f)
                    println(math.pow(-1.0, 0.5))
                    println(1e-3)
                    println(1e-4)
                    println(3.4028235e38f)
                    println(-0.0)
                }
                """, "0.30000000000000004\n1.0E7\n1.0E-5\n33.333333333333336\n0.33333334\n1.0E20\nNaN"
                + "\n0.001\n1.0E-4\n3.4028235E38\n-0.0",
                Set.of("js"), tempDir);
        // bug 100 (paridade absoluta): `String.equals(não-String)` é `false` em
        // todo target — o JVM sempre deu false (Objects.equals), mas o Native
        // CRASHAVA (SIGSEGV/vazio) ao ler o Int-boxado como ponteiro-String.
        // Agora é constant-fold no lowering (mesmo `false` nos 5). O == de
        // String-vs-String (conteúdo) segue pelo runtime em todos.
        // §102 (paridade absoluta): o índice inicial de indexOf/lastIndexOf/
        // startsWith era IGNORADO no Native (helper de aridade 1 só). JDK 21
        // é o oracle (clampagens: from<0, from>total, vazia, corte de par).
        matrix("searchfrom", """
                main() {
                    println("aXb".indexOf("X",2))
                    println("abc".indexOf("",5))
                    println("aXa".lastIndexOf("a",-1))
                    println("aXa".lastIndexOf("a",9))
                    println("aXb".startsWith("X",1))
                    println("abc".startsWith("",4))
                }
                """, "-1\n3\n-1\n2\ntrue\nfalse", Set.of(), tempDir);
        matrix("equalsfold", """
                main() {
                    val s = "abc"
                    println(s.equals("abc"))
                    println(s.equals("abd"))
                    println(s.equals(5))
                    println(s.equals('x'))
                }
                """, "true\nfalse\nfalse\nfalse", Set.of(), tempDir);
        // §104 (paridade absoluta): record DENTRO de coleção usa equals/
        // hashCode/toString por CONTEÚDO (oracle = JVM, registro real gera os
        // 3). Script era identidade (KofObj sem override → §104a CORRIGIDO
        // 11/09); Native LINK_FAIL em Thing.equals (Object.equals herdado sem
        // slot na vtable → §104b ABERTO, célula excluída); JS usa identidade
        // (Map/HashSet nativos + sem wrapper → §104c ABERTO, excluído).
        matrix("objmethods", """
                record Point(Int x, Int y)
                main() {
                    val p1 = Point(1, 2)
                    val p2 = Point(1, 2)
                    println(listOf(p1).contains(p2))
                    println(setOf(p1).contains(p2))
                    println(mapOf(p1, 7).get(p2))
                    println(listOf(p1))
                }
                """, "true\ntrue\n7\n[Point[x=1, y=2]]", Set.of("native"), tempDir);
        // §107-JS (paridade absoluta): `println(coleção)` no JS dava
        // "1,2" (Array.toString sem colchetes) / "[object Map]" / "[object
        // Set]" — sem o formato do contêiner JVM ([1, 2] / {k=1}). kofFormat
        // (JsRuntimeCore) espelha ArrayList/HashMap/HashSet.toString. Roteado
        // por tipo no valueOf (JsCallEmitter) — só coleção, não toca escalar
        // (bug 44). Bool-em-lista fica fora daqui: §107 (Script [1,0]).
        // §107 (Native, CORRIGIDO 12/09 p/ escalares): o println(<coleção>)
        // nativo imprimia LIXO de ponteiro; agora kof_{list,set,map}_to_string
        // (x86 f3b3821c + cross B39) reproduzem o formato JVM p/ elementos
        // escalares. A célula CONTINUA com native excluído porque os golden
        // aqui exigem o que os nativos ainda NÃO têm: (a) record-em-lista →
        // os nativos dão `?` (recusa honesta, cara do §104b-ii — face dos
        // records, lane alheia), (b) `listOf(1.5, 2.25)` → o cross levanta
        // FLT001 em compilação (recusa honesta, R6). Escalares int/string/
        // bool/long/char aninhado=`?`/Map-single estão provados por exec
        // em NativeE2ETest#execCollectionPrintMatchesJvmGolden +
        // Native{Riscv64,Aarch64}E2ETest#nativeCollectionPrintMatchesJvmGolden
        // (golden = oracle JVM medido, byte-idêntico nos 3 targets nativos).
        matrix("collprint", """
                record Point(Int x, Int y)
                main() {
                    println(listOf(1, 2))
                    println(listOf("a", "b"))
                    println(listOf(1.5, 2.25))
                    println(mapOf("k", 1))
                    println(setOf(1))
                    println(listOf(Point(1,2), Point(3,4)))
                    println(listOf(listOf(1), listOf(2)))
                }
                """, "[1, 2]\n[a, b]\n[1.5, 2.25]\n{k=1}\n[1]\n[Point[x=1, y=2], Point[x=3, y=4]]\n[[1], [2]]",
                Set.of("native"), tempDir);

        // §109 (paridade absoluta + JVM CRASH): mapOf(k, <primitivo>) e o
        // GUARD do kof_map_get (Nullable(V) primitivo) chamavam
        // unboxMethodName com o tipo PRIMITIVO interno — só o ramo ClassType
        // era tratado, então Bool caía em `intValue` → `Boolean.intValue()Z`
        // → NoSuchMethodError em runtime no JVM. Fix trata o ramo primitivo
        // (mesma tabela de boxedClassNameFor). Cobre Int/Long/Double/Bool/Char
        // pelo mesmo caminho de guard.
        // §104b-ii FACE char (✅ 11/09, esta célula sem exclusões): JVM
        // imprimia 97 só p/ `Int`; char caía em `Integer.charValue()C`
        // inexistente (char é GUARDADO como Integer, a caixa nunca é
        // Character) — unbox agora é `intValue`/`()I` coerente com a caixa.
        // Native SIGSEGVava/imprimia o caractere ("a") em println(char-em-
        // coleção): `ExpressionPrintLowerer` mapeava char→Int p/ valueOf só
        // com CHAR cru (Nullable(CHAR) vazava p/ o ramo char_to_string do
        // backend) e o cast `x as Char` pinava Unknown no mapOf (o cache do
        // SemanticAnalyzer não tinha o repair do ExpressionTyper). 4/4.
        // JS: `d*2`→`5` vs `5.0` (String(5.0)="5") é o floatprint §44; a
        // célula usa predicado (`d > 1.0`) p/ exercitar o storage Double sem
        // colidir com ele.
        matrix("mapgetprim", """
                main() {
                    val b = mapOf("t", true).get("t")
                    println(b)
                    println(b == true)
                    val n = mapOf("i", 7).get("i")
                    println(n + 1)
                    val g = mapOf("l", 9000000000L).get("l")
                    println(g + 1)
                    val d = mapOf("d", 2.5).get("d")
                    println(d > 1.0)
                    val c = mapOf("c", 'a' as Char).get("c")
                    println(c)
                    val miss = mapOf("x", true).get("nope")
                    println(miss)
                }
                """, "true\ntrue\n8\n9000000001\ntrue\n97\nfalse", Set.of(), tempDir);

        // D-NULL-INTENT/N1 (mantenedora 15/09, e04f10ff): §125 opção A
        // REVOGADA — Nullable(primitivo) agora é boxed nos 3 targets
        // implementados (JVM/Script/JS) e carrega null de verdade: println de
        // função Nullable(primitivo) que RETORNA null imprime "null" (mesmo
        // precedente de Nullable(REF), §124), `== null`/`!= null` responde de
        // verdade, e o valor NUNCA se confunde com 0/false (`five()+1`→`6`,
        // `en(7)`→`7`). `mapOf(...).get("zz") == null`→`false` continua
        // INALTERADO (map-miss, SG-008/bug-87, congelado — fora do escopo do
        // N1, distinguido por FORMA de chamada, não por tipo). Native (N2)
        // ainda não implementa o boxed — excluído até lá (fecha #259/#266).
        matrix("nullableprint", """
                Int? ni() { return null }
                Bool? nb() { return null }
                Long? nl() { return null }
                Double? nd() { return null }
                Int? five() { return 5 }
                Int? en(Int x) = if (x > 0) x else null
                Bool? bn(Int x) = if (x > 0) true else null
                main() {
                    println(ni())
                    println(nb())
                    println(nl())
                    println(five() + 1)
                    println(ni() == null)
                    println(nl() == null)
                    println(nd() == null)
                    println(mapOf("a", 1).get("zz") == null)
                    val a = ni()
                    println(a == null)
                    println(en(7))
                    println(en(-7))
                    Int? v = if (false) 9 else null
                    println(v)
                    println(bn(-1))
                    println("a" + ni())
                    println(ni() + "b")
                }
                 """, "null\nnull\nnull\n6\ntrue\ntrue\ntrue\nfalse\ntrue\n7\nnull\nnull\nnull\nanull\nnullb",
                Set.of("native"), tempDir);

        // §143 (B1, 12/09): widening numérico ABENÇOADO pelo §126 ("Int em
        // Long passa") em escrita de coleção PINADA dava VerifyError/CCE no
        // JVM (o box do store era pelo tipo pinado sobre arg cru width-1) —
        // a conversão do §121 (array-store) nunca chegou nas coleções. Fix:
        // coerceStoreWiden no lower (emitWideningIfNeeded só promove; rejeição
        // por SEM056 do §126 intocada). Narrowing NÃO está aqui (família B1b,
        // §144 aberto).
        matrix("collwiden", """
                main() {
                    var l = listOf(1L, 2L)
                    l.add(3)
                    println(l.get(2))
                    l.set(0, 4)
                    println(l.get(0))
                    println(l.size)
                }
                """, "3\n4\n3", Set.of(), tempDir);
        // Map: o put com valor widening (Int em Map<_,Long>) crashava o JVM
        // igual. O Native também crashava — §142 CORRIGIDO 12/09: o POP2 nativo
        // (descarte do prev Long do put, expression-statement) fazia addq $16
        // sobre 1 qword empilhado e pisava o local `m` (SIGSEGV). Agora 4/4.
        matrix("mapwiden", """
                main() {
                    var m = mapOf("a", 1L)
                    m.put("b", 2)
                    println(m.get("b"))
                    println(m.get("a"))
                }
                """, "2\n1", Set.of(), tempDir);
        // §142 (12/09): descarte de expressão Long/Double (POP2) no nativo
        // desbalanceava a pilha — `m.put(...)` (prev Long) como statement, e
        // `d == null`/`x == null` (fold que descarta o primitivo). 4/4.
        matrix("longdiscard", """
                main() {
                    var m = mapOf("a", 1L)
                    m.put("b", 2L)
                    println(m.size)
                    var d = 2.5
                    println(d == null)
                    var x = 1L
                    println(x == null)
                }
                """, "2\nfalse\nfalse", Set.of(), tempDir);

        // §112 (paridade absoluta, 3 superfícies novas achadas no sweep de
        // coleções): (a) JVM **VerifyError** em `println(m.put(k,v))` com V
        // primitivo — HashMap.put devolve Object (prev), e o typer declara o
        // retorno V; o Object entrando em uso primitivo quebrava o verifier.
        // (b) JVM **NullPointerException** em `println(m.remove(k))` de chave
        // AUSENTE — remove devolve null e o unbox cru de primitivo estourava.
        // (c) interpretador (Script) `s.add(1)` de um set que JÁ CONTÉM 1
        // devolvia true (o código fazia add() e depois contains() — sempre
        // true) vs JVM false. (d) interpretador os mesmos NPE/VerifyError de
        // (a)/(b). Fix: emitPrevValueUnbox (guard null→default, espelhando o
        // guard do kof_map_get) no JvmOpCollections kof_map_put/kof_map_remove
        // + prevOrDefault no interpretador + s.add corrigido. **Bug extra no
        // mesmo caminho:** o x86 `kof_map_remove` na rota de MISS fazia
        // 3 popq para 5 pushq (desequilíbrio de pilha → `ret` para lixo →
        // **SIGSEGV** em `m.remove(chave-ausente)`) — 5 pops simétricos.
        matrix("mapmutret", """
                main() {
                    var s = setOf(1, 2)
                    println(s.add(1))
                    println(s.add(5))
                    println(s.size)
                    println(s.remove(1))
                    println(s.remove(42))
                    var m = mapOf("a", 1)
                    println(m.put("a", 2))
                    println(m.get("a"))
                    println(m.remove("a"))
                    println(m.remove("zz"))
                    println(m.size)
                }
                """, "false\ntrue\n3\ntrue\nfalse\n1\n2\n2\n0\n0", Set.of(), tempDir);
        // §104b-i (Native): `Thing.equals(...)` em classe NÂO-record dava
        // LINK_FAIL (Object.equals herdado sem símbolo no bare-metal).
        // Síntese de equals de identidade → oracle JVM (false entre
        // instâncias novas, true por referência).
        matrix("classequals", """
                class Thing {
                    Int v
                    public constructor(Int v) { this.v = v }
                }
                main() {
                    val t1 = Thing(5)
                    val t2 = Thing(5)
                    val r = t1
                    println(t1 == t2)
                    println(r == t1)
                    println(t1.equals(t2))
                    println(listOf(t1).contains(t1))
                }
                """, "false\ntrue\nfalse\ntrue", Set.of(), tempDir);
        matrix("boollogic", """
                main() {
                    println(true && false)
                    println(true || false)
                    println(!true)
                    println((1 < 2) == (3 > 2))
                }
                """, "false\ntrue\nfalse\ntrue", Set.of(), tempDir);
        matrix("bitwise", """
                main() {
                    println(6 & 3)
                    println(6 | 3)
                    println(6 ^ 3)
                    println(1 << 4)
                    println(256 >> 2)
                    var l = 5L
                    println(l & 3)
                    println(l | 3)
                    println(l ^ 3)
                    var i = 5
                    println(i & l)
                    var neg = -1
                    var big = 4294967295L
                    println(neg & big)
                    println(neg | big)
                    println(neg ^ big)
                    println(l << 2L)
                    println(l << 70)
                    println(l << 70L)
                    println(l >> 65L)
                    var one = 1
                    println(one << 40L)
                    println(one >> 40L)
                    println(one >>> 40L)
                    var n = -1L
                    println(n >>> 1)
                    println(n >>> 64L)
                    println(n >>> 65L)
                    var max = 9223372036854775807L
                    println(max + 1L)
                    println(max * 2L)
                    var min = -9223372036854775807L - 1L
                    println(-min)
                    var w = 5000000000L
                    var t = w as Int
                    println(t)
                    println(t + 1)
                    println((l as Int) & 3)
                }
                """, """
                2
                7
                5
                16
                64
                1
                7
                6
                5
                4294967295
                -1
                -4294967296
                20
                320
                320
                2
                256
                0
                0
                9223372036854775807
                -1
                9223372036854775807
                -9223372036854775808
                -2
                -9223372036854775808
                705032704
                705032705
                1
                """.trim(), Set.of(), tempDir);
        // §168 — `++`/`--`/compound em tipos largos + elemento de array.
        // O JVM emitia VerifyError (literal INT 1 em binário de 2 slots, DUP de
        // 1 slot em long/double, arraystore sem [array,index]); agora os 4
        // targets concordam (golden = oracle JVM). Bordas: prefixo/pós-fixo,
        // `--` negativo, float/double/long, estouro de Long em `++` e elemento
        // de array Int e Long.
        matrix("increment", """
                main() {
                    var c = 1L
                    c++
                    println(c)
                    ++c
                    println(c)
                    c--
                    println(c)
                    var d = 1.5
                    d++
                    println(d)
                    ++d
                    println(d)
                    d--
                    println(d)
                    var f = 1.5f
                    f++
                    println(f)
                    var i = 5
                    i++
                    println(i)
                    var l = 100L
                    l /= 3
                    println(l)
                    l += 2L
                    println(l)
                    d /= 2.0
                    println(d)
                    var max = 9223372036854775807L
                    max++
                    println(max)
                    var a = new Long[2]
                    a[0] = 7L
                    a[0]++
                    println(a[0])
                    println(++a[0])
                    a[1] = 40L
                    a[1]--
                    println(a[1])
                    var b = new Int[2]
                    b[0] = 7
                    b[0]++
                    println(b[0])
                    println(b[0]--)
                    println(b[0])
                }
                """, """
                2
                3
                2
                2.5
                3.5
                2.5
                2.5
                6
                33
                35
                1.25
                -9223372036854775808
                8
                9
                39
                8
                8
                7
                """.trim(), Set.of(), tempDir);
        // §172 — compound shift `<<=`/`>>=`/`>>>=` (parser reconhece; o
        // lowering narrowa o RHS largo p/ int — `g=1L; g <<= 40L` sem o L2I
        // emitia `lshl` (long,long) → VerifyError no JVM). 4 targets.
        matrix("compound-shift", """
                main() {
                    var a = 6; a <<= 2; println(a)
                    var b = 6; b >>= 1; println(b)
                    var c = -8; c >>>= 1; println(c)
                    var d = 6; d &= 3; println(d)
                    var e = 6; e |= 8; println(e)
                    var f = 6; f ^= 1; println(f)
                    var g = 1L; g <<= 40L; println(g)
                }
                """, """
                24
                3
                2147483644
                2
                14
                7
                1099511627776
                """.trim(), Set.of(), tempDir);
        // STDLIB S1 — kof.math (Int-only) paridade total nos 4 targets.
        // §93: os dois últimos casos comparam `== true`/`== false` no
        // CAMINHO DE VALOR (o print sozinho coercia 1/0 e mascarava o bug).
        matrix("stdmath", """
                main() {
                    println(math.clamp(15, 0, 10))
                    println(math.clamp(-3, 0, 10))
                    println(math.abs(-7))
                    println(math.sign(-4))
                    println(math.min(3, 8))
                    println(math.max(3, 8))
                    println(math.isEven(4))
                    println(math.isOdd(4))
                    println(math.isZero(0))
                    println(math.isEven(4) == true)
                    println(math.isEven(4) == false)
                }
                """, "10\n0\n7\n-1\n3\n8\ntrue\nfalse\ntrue\ntrue\nfalse", Set.of(), tempDir);
        // STDLIB S1b — kof.math.sqrt (PRIMEIRO Double da namespace). Compara-
        // ções Bool (nunca print de double cru — bug 44 no Native). riscv/aarch
        // = B32 `fsqrt.d` (MATH001 fechado 11/09 — a cobertura cross com
        // golden byte-idêntico mora em KofMathTest.sqrtCrossArch/
        // doubleOpsCrossArch sob qemu; esta matriz roda os 4 targets não-cross).
        // §94 FECHADO 13/09: o interpretador usava Double.compare (NaN==NaN
        // true, +0.0==-0.0 false) — agora IEEE, paridade 4/4 sem exclusão.
        matrix("stdsqrt", """
                main() {
                    println(math.sqrt(9.0) == 3.0)
                    println(math.sqrt(2.0) == 1.4142135623730951)
                    println(math.sqrt(0.25) == 0.5)
                    println(math.sqrt(0.0) == 0.0)
                    println(math.sqrt(-1.0) == -1.0)
                    println(math.sqrt(-1.0) != math.sqrt(-1.0))
                    println(0.0 == -0.0)
                }
                 """, "true\ntrue\ntrue\ntrue\nfalse\ntrue\ntrue", Set.of(), tempDir);
        // STDLIB S1b.1 — kof.math escalares Double (lerp/percentage/
        // isInteger/isDecimal). Subset determinístico travado nos 4 targets
        // (NaN incluído após §94; provado nos compilados em KofMathTest.doubleOps*).
        // Bool == false no script casa (S12b).
        matrix("stdmathdouble", """
                main() {
                    println(math.lerp(0.0, 10.0, 0.5) == 5.0)
                    println(math.lerp(0.0, 10.0, 0.25) == 2.5)
                    println(math.lerp(-4.0, 4.0, 0.75) == 2.0)
                    println(math.lerp(2.0, 8.0, 1.5) == 11.0)
                    println(math.percentage(3.0, 4.0) == 75.0)
                    println(math.percentage(1.0, 3.0) == 33.33333333333333)
                    println(math.percentage(-2.0, 8.0) == -25.0)
                    println(math.percentage(0.0, 5.0) == 0.0)
                    println(math.isInteger(4.0))
                    println(math.isInteger(4.5) == false)
                    println(math.isInteger(-3.0))
                    println(math.isInteger(0.0))
                    println(math.isInteger(1e20))
                    println(math.isDecimal(4.5))
                    println(math.isDecimal(4.0) == false)
                }
                """, "true\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue", Set.of(), tempDir);
        // STDLIB S1b.2 — kof.math.pow (decisão 7a): primeiro caso libm no
        // native x86 (`pow@PLT` + `-lm`); JVM/JS Math.pow; riscv/aarch =
        // MATH001 (link estático sem libc — gap diagnosticado, fora das 4
        // colunas). Subset determinístico travado nos 4 targets.
        matrix("stdmathpow", """
                main() {
                    println(math.pow(2.0, 10.0) == 1024.0)
                    println(math.pow(9.0, 0.5) == 3.0)
                    println(math.pow(2.0, -1.0) == 0.5)
                    println(math.pow(2.0, 0.0) == 1.0)
                    println(math.pow(0.0, 0.0) == 1.0)
                    println(math.pow(2.0, 0.5) == math.sqrt(2.0))
                    println(math.pow(3.0, 3.0) == 27.0)
                    println(math.pow(-2.0, 3.0) == -8.0)
                    println(math.pow(10.0, -2.0) == 0.01)
                    println(math.pow(-1.0, 0.5) != math.pow(-1.0, 0.5))
                }
                 """, "true\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue", Set.of(), tempDir);
        // STDLIB S1b.3 (DECISIONS §3) — kof.math.roundTo(value: Double,
        // decimals: Int) -> Double. Half-away-from-zero por escala decimal
        // determinística, SEM libm (p=10^|d| por multiplicação repetida →
        // byte-idêntico 5 alvos). Bool via == (bug 44: nunca println de
        // double cru no Native). decimals negativo arredonda p/ dezenas.
        // Contrato ARITMÉTICO: 2.675 → 2.68. Cross-arch mora em
        // KofMathTest.roundToCrossArch sob qemu.
        matrix("stdmathround", """
                main() {
                    println(math.roundTo(2.5, 0) == 3.0)
                    println(math.roundTo(-2.5, 0) == -3.0)
                    println(math.roundTo(2.4, 0) == 2.0)
                    println(math.roundTo(0.49999999999999994, 0) == 0.0)
                    println(math.roundTo(3.14159, 2) == 3.14)
                    println(math.roundTo(2.675, 2) == 2.68)
                    println(math.roundTo(1234.0, -2) == 1200.0)
                    println(math.roundTo(-1250.0, -2) == -1300.0)
                    println(math.roundTo(1.0, 0) == 1.0)
                    println(math.roundTo(0.0, 5) == 0.0)
                }
                """, "true\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue", Set.of(), tempDir);
        // STDLIB S13a — math.parseInt/parseLong/parseDouble (fachada sobre as
        // runtime fns kof_string_to_* EXISTENTES nos 4 backends; regra 2 —
        // zero runtime novo). Contrato JDK com trim (idem `.toInt()`):
        // inválido/overflow LANÇA (try/catch). Double via == Bool (bug 44).
        // Long 9007199254740993 > 2^53 prova Long real pós-§81 (BigInt JS).
        // Cross-arch (riscv B30/B31 + aarch) com golden byte-idêntico mora em
        // KofMathTest.parseCrossArch sob qemu.
        matrix("stdmathparse", """
                main() {
                    println(math.parseInt("42"))
                    println(math.parseInt(" -7 "))
                    println(math.parseInt("+13"))
                    println(math.parseInt("0"))
                    println(math.parseInt("-2147483648"))
                    println(math.parseLong("9007199254740993"))
                    println(math.parseLong("-9223372036854775807"))
                    println(math.parseDouble("2.5") == 2.5)
                    println(math.parseDouble("  -0.25 ") == -0.25)
                    println(math.parseDouble("1e2") == 100.0)
                    try { println(math.parseInt("abc")); println("S1") } catch (String e) { println("T1") }
                    try { println(math.parseInt("12a34")); println("S2") } catch (String e) { println("T2") }
                    try { println(math.parseInt("2147483648")); println("S3") } catch (String e) { println("T3") }
                    try { println(math.parseInt("")); println("S4") } catch (String e) { println("T4") }
                    try { println(math.parseLong("9223372036854775808")); println("S5") } catch (String e) { println("T5") }
                }
                """, "42\n-7\n13\n0\n-2147483648\n9007199254740993\n-9223372036854775807\ntrue\ntrue\ntrue\nT1\nT2\nT3\nT4\nT5", Set.of(), tempDir);
        // STDLIB S13b — math.parseInt/parseLong/parseDouble**OrDefault** (§43:
        // falha de parse DEVOLVE o default, nunca lança). Backends: JVM
        // try/catch, JS wrapper, x86 wrapper c/ handler no exc_chain, riscv
        // B41 (aarch tradutor). Literal Int em param Long prova o widening
        // I2L do KofStd (crash COMPUTE_FRAMES sem ele). Linhas ""/"   " do
        // Double INCLUÍDAS pós-§175 (vazio lançava 0.0 no Native — paridade
        // fechada); Int/Long "" DEVOLVEM o default (lançam no parse base).
        matrix("stdmathparseord", """
                main() {
                    println(math.parseIntOrDefault("42", 0))
                    println(math.parseIntOrDefault("abc", -1))
                    println(math.parseIntOrDefault("", 7))
                    println(math.parseIntOrDefault("  15  ", 0))
                    println(math.parseIntOrDefault("99999999999999", 3))
                    println(math.parseLongOrDefault("9007199254740993", 0))
                    println(math.parseLongOrDefault("x", -5))
                    println(math.parseLongOrDefault("9223372036854775808", 8))
                    println(math.parseDoubleOrDefault("2.5", 0.0) == 2.5)
                    println(math.parseDoubleOrDefault("nope", -0.5) == -0.5)
                    println(math.parseDoubleOrDefault("1e2", 0.0) == 100.0)
                }
                """, "42\n-1\n7\n15\n3\n9007199254740993\n-5\n8\ntrue\ntrue\ntrue", Set.of(), tempDir);
        // STDLIB S2a — kof.strings predicados paridade total nos 4 targets.
        matrix("stdstrings", """
                main() {
                    println(strings.isAlpha("Hello"))
                    println(strings.isAlpha("Hello World"))
                    println(strings.isAlpha(""))
                    println(strings.isNumeric("12345"))
                    println(strings.isNumeric("12.34"))
                    println(strings.isNumeric(""))
                    println(strings.isAlphaNumeric("abc123"))
                    println(strings.isAlphaNumeric("abc-123"))
                    println(strings.isAscii("ola"))
                    println(strings.isAscii("ola !123"))
                    println(strings.isUpperCase("HELLO"))
                    println(strings.isUpperCase("Hello"))
                    println(strings.isLowerCase("abc-123"))
                    println(strings.isLowerCase("Abc"))
                    println(strings.count("aabaabaa", "ab"))
                    println(strings.count("aaa", "aa"))
                    println(strings.isAlpha("Hello") == true)
                }
                """, "true\nfalse\nfalse\ntrue\nfalse\nfalse\ntrue\nfalse\ntrue\ntrue\ntrue\nfalse\ntrue\nfalse\n2\n1\ntrue", Set.of(), tempDir);
        // STDLIB S2b — kof.strings conversores (alocam String). ASCII-only:
        // é onde JVM/Native/JS concordam byte a byte. capitalize é ASCII
        // (mesma regra nos 4); reverse é byte-reverso no Native e UTF-16
        // nos outros — em ASCII as três convenções coincidem. Gap UTF-8 do
        // reverse nativo = NAT-STR01 (plan-stdlib-expansion §5).
        matrix("stdstrings2b", """
                main() {
                    var a = strings.capitalize("hello world")
                    var b = strings.capitalize("1abc")
                    var c = strings.reverse("abc123")
                    var d = strings.reverse("kayak")
                    var e = strings.repeat("ab", 3)
                    var f = strings.truncate("hello world", 5)
                    var g = strings.truncate("abc", 10)
                    var h = strings.padLeft("7", 3, "0")
                    var i = strings.padRight("ab", 5, "-")
                    println(a + "|" + b + "|" + c + "|" + d + "|" + e + "|" + f + "|" + g + "|" + h + "|" + i)
                }
                """, "Hello world|1abc|321cba|kayak|ababab|hello|abc|007|ab---", Set.of(), tempDir);
        // STDLIB S2b.4 — kof.strings conversores de palavra (split+join, ASCII).
        // A matriz roda native=x86 (tem asm); o port riscv/aarch é STRN001-gated
        // (KofStringsTest.wordConvertersGatedOnCrossArch). Em ASCII os 4 targets
        // (jvm/native/script/js) concordam byte a byte.
        matrix("stdstrings2b4", """
                main() {
                    var a = strings.toSnakeCase("HTTPServer")
                    var b = strings.toSnakeCase("XMLParser")
                    var c = strings.toCamelCase("hello_world")
                    var d = strings.toPascalCase("hello world")
                    var e = strings.toKebabCase("helloWorld")
                    var f = strings.slugify("Hello, World!! 42")
                    println(a + "|" + b + "|" + c + "|" + d + "|" + e + "|" + f)
                }
                """, "http_server|xml_parser|helloWorld|HelloWorld|hello-world|hello-world-42", Set.of(), tempDir);
        // STDLIB S4 — kof.encoding hex (UTF-8 por bytes; paridade byte-idêntica
        // nos 4: getBytes/TextEncoder/asm UTF-8 puro).
        matrix("stdenc", """
                main() {
                    var a = encoding.hexEncode("Hi")
                    var b = encoding.hexDecode("4869")
                    var c = encoding.hexEncode("café")
                    var d = encoding.hexDecode(c)
                    var e = encoding.base64Encode("Man")
                    var f = encoding.base64Decode("Y2Fmw6k=")
                    var u = encoding.urlEncode("a b")
                    var w = encoding.urlDecode("caf%C3%A9")
                    var x = encoding.base64UrlEncode("fb&O->f")
                    var y = encoding.base64UrlDecode("ZmImTy0-Zg")
                    var mark = if (encoding.hexEncode("") == "") "E" else "N"
                    println(a + "|" + b + "|" + c + "|" + d + "|" + e + "|" + f + "|" + u + "|" + w + "|" + x + "|" + y + "|" + mark)
                }
                """, "4869|Hi|636166c3a9|café|TWFu|café|a%20b|café|ZmImTy0-Zg|fb&O->f|E", Set.of(), tempDir);
        matrix("stdvalidation", """
                main() {
                    println(validation.isCpf("529.982.247-25"))
                    println(validation.isCpf("111.111.111-11"))
                    println(validation.isCnpj("11.222.333/0001-81"))
                    println(validation.isCnpj("11.222.333/0001-82"))
                    println(validation.isCep("01310-100"))
                    println(validation.isCep("0131010"))
                    println(validation.isPis("123.4567.890-0"))
                    println(validation.isPis("12345678901"))
                    // S12c: NIS — MESMO checksum mod-11 do PIS (reuso 1:1).
                    println(validation.isNis("12056412278"))
                    println(validation.isNis("120.5641.227-8"))
                    println(validation.isNis("12056412279"))
                    println(validation.isNis("12345678901"))
                    println(validation.isNis(""))
                    println(validation.isCpf("529.982.247-25") == true)
                    println(validation.isCpf("111.111.111-11") == false)
                }
                """, "true\nfalse\ntrue\nfalse\ntrue\nfalse\ntrue\nfalse\ntrue\ntrue\nfalse\nfalse\nfalse\ntrue\ntrue", Set.of(), tempDir);
        matrix("stdvalidationnet", """
                main() {
                    println(validation.isIpv4("192.168.0.1"))
                    println(validation.isIpv4("256.1.1.1"))
                    println(validation.isIpv4("01.2.3.4"))
                    println(validation.isMac("00:1A:2B:3C:4D:5E"))
                    println(validation.isMac("GG:1A:2B:3C:4D:5E"))
                    println(validation.isPort(443))
                    println(validation.isPort(65536))
                }
                """, "true\nfalse\nfalse\ntrue\nfalse\ntrue\nfalse", Set.of(), tempDir);
        matrix("stdluhn", """
                main() {
                    println(validation.isCreditCard("4111111111111111"))
                    println(validation.isCreditCard("4532 0151 1283 0366"))
                    println(validation.isCreditCard("378282246310005"))
                    println(validation.isCreditCard("4111111111111112"))
                    println(validation.isCreditCard("45"))
                    println(validation.isCreditCard("1234567890123456789"))
                }
                """, "true\ntrue\ntrue\nfalse\nfalse\nfalse", Set.of(), tempDir);
        matrix("stdipv6", """
                main() {
                    println(validation.isIpv6("::1"))
                    println(validation.isIpv6("fe80::1"))
                    println(validation.isIpv6("a:b:c:d:e:f:1:2"))
                    println(validation.isIpv6("1::2::3"))
                    println(validation.isIpv6("12345::"))
                    println(validation.isIpv6("::ffff:192.168.0.1"))
                }
                """, "true\ntrue\ntrue\nfalse\nfalse\nfalse", Set.of(), tempDir);
        matrix("stddomain", """
                main() {
                    println(validation.isDomain("example.com"))
                    println(validation.isDomain("xn--mnchen-3ya.de"))
                    println(validation.isDomain("localhost"))
                    println(validation.isDomain("example..com"))
                    println(validation.isDomain("ex_ample.com"))
                    println(validation.isDomain("x.x"))
                }
                """, "true\ntrue\nfalse\nfalse\nfalse\nfalse", Set.of(), tempDir);
        matrix("stdescape", """
                main() {
                    println(strings.escapeHtml("a<b>&\\"'c"))
                    println(strings.escapeHtml("Café & ç"))
                    println(strings.escapeHtml("&amp;lt;"))
                    println(strings.escapeHtml("<a href=\\"u\\">y</a>"))
                }
                """, "a&lt;b&gt;&amp;&quot;&#39;c\nCafé &amp; ç\n&amp;amp;lt;\n&lt;a href=&quot;u&quot;&gt;y&lt;/a&gt;", Set.of(), tempDir);
        matrix("stdws", """
                main() {
                    println(strings.removeWhitespace("  a\\tb\\nc  ") + "|" + strings.removeWhitespace("Café é"))
                    println(strings.normalizeWhitespace("  a   b  ") + "|" + strings.normalizeWhitespace("a\\t\\n b"))
                    println(strings.normalizeWhitespace("   ") + "|[" + strings.removeWhitespace("") + "]")
                }
                """, "abc|Caféé\na b|a b\n|[]", Set.of(), tempDir);
        matrix("stdnet", """
                main() {
                    val s = "https://user:pw@host.io:8443/p?q#f"
                    println(net.scheme(s) + "|" + net.host(s) + "|" + net.port(s) + "|" + net.path(s) + "|" + net.query(s) + "|" + net.fragment(s))
                    println(net.path("/only/path") + "|" + net.query("http://h?onlyquery"))
                    println(net.queryEncode("a b&c=1"))
                    println(net.queryDecode("a%20b%26c%3D1"))
                }
                """, "https|host.io|8443|/p|q|f\n/only/path|onlyquery\na%20b%26c%3D1\na b&c=1", Set.of(), tempDir);
        // STDLIB S3 — kof.uuid.isUuid (predicado de forma 8-4-4-4-12; hex min
        // ou maiúsculo; version/variant NAO verificadas — so forma canonica).
        // Deterministica => matriz nos 4 targets (riscv/aarch = UUID001 gate,
        // nao alvo da matriz; ultima linha: v4() gerada no proprio target).
        matrix("stduuidform", """
                main() {
                    println(uuid.isUuid("550e8400-e29b-41d4-a716-446655440000"))
                    println(uuid.isUuid("550E8400-E29B-41D4-A716-446655440000"))
                    println(uuid.isUuid("550e8400e29b41d4a716446655440000"))
                    println(uuid.isUuid("550e8400xe29b-41d4-a716-446655440000"))
                    println(uuid.isUuid("550e8400-e29b-41d4-a716-44665544000g"))
                    println(uuid.isUuid(""))
                    println(uuid.isUuid(uuid.v4()))
                }
                """, "true\ntrue\nfalse\nfalse\nfalse\nfalse\ntrue", Set.of(), tempDir);
        matrix("stdunescape", """
                main() {
                    println(strings.unescapeHtml("a&amp;b"))
                    println(strings.unescapeHtml("&lt;x&gt;"))
                    println(strings.unescapeHtml("caf&#233;"))
                    println(strings.unescapeHtml("&#9731;"))
                    println(strings.unescapeHtml("&&amp;"))
                    println(strings.unescapeHtml("&notreal;"))
                }
                """, "a&b\n<x>\ncafé\n\u2603\n&&\n&notreal;", Set.of(), tempDir);
        matrix("stdtime", """
                main() {
                    println(time.isLeapYear(2000))
                    println(time.isLeapYear(1900))
                    println(time.isLeapYear(2024))
                    println(time.isLeapYear(-4))
                    println(time.daysInMonth(2024, 2))
                    println(time.daysInMonth(2023, 2))
                    println(time.daysInMonth(2024, 4))
                    println(time.daysInMonth(2024, 13))
                    println(time.dayOfWeek(1970, 1, 1))
                    println(time.dayOfWeek(2026, 9, 9))
                    println(time.dayOfWeek(2024, 2, 30))
                    println(time.daysBetween(2024, 1, 1, 2024, 3, 1))
                    println(time.daysBetween(2024, 3, 1, 2024, 1, 1))
                    println(time.daysBetween(2023, 2, 29, 2023, 3, 1))
                }
                """, "true\nfalse\ntrue\nfalse\n29\n28\n30\n0\n4\n3\n0\n60\n-60\n0", Set.of(), tempDir);
                // STDLIB S7a/S7b — addDays/diffDays em data ISO (String).
                // JVM+Script (java.time) + JS (algoritmo civil, sem Date);
                // Native = TIME002 (gate honesto no compile-time; o erro é
                // provado em KofTimeE2ETest).
                matrix("stdtime2", """
                main() {
                    println(time.addDays("2024-02-28", 1))
                    println(time.addDays("2023-02-28", 1))
                    println(time.addDays("2024-12-31", 1))
                    println(time.addDays("2024-01-01", -1))
                    println(time.addDays("2024-02-30", 1))
                    println(time.addDays("garbage", 1))
                    println(time.diffDays("2024-01-01", "2024-03-01"))
                    println(time.diffDays("2024-03-01", "2024-01-01"))
                    println(time.diffDays("x", "y"))
                    println(time.addDays("0999-12-31", 1))
                    println(time.addDays("0001-01-01", -1))
                    println(time.addDays("1700-02-28", 1))
                }
                """, "2024-02-29\n2023-03-01\n2025-01-01\n2023-12-31\n\n\n60\n-60\n0\n1000-01-01\n\n1700-03-01", Set.of(), tempDir);
        // STDLIB S7e (D-STDLIB ratificado 13/09): todayIso/formatDateIso/
        // isToday — UTC-only (D1), invalidade => "" (D4), isToday = igualdade
        // com a data UTC de now() (D5). todayIso validado por formato
        // (len 10, parts 4/2/2) — nunca por valor literal (dia vira).
        // Asserções isToday determinísticas: só o `false` (independe do
        // relógio). 5 alvos: JVM/java.time + JS civil + x86 RuntimeTimeIso +
        // riscv/aarch B33 estendida; cross qemu no KofTimeE2ETest S7e.
        matrix("stdtime3", """
                main() {
                    var today = time.todayIso()
                    println(today.length)
                    println(time.formatDateIso(2026, 9, 13))
                    println(time.formatDateIso(2024, 2, 29))
                    println(time.formatDateIso(2023, 2, 29))
                    println(time.formatDateIso(0, 1, 1))
                    println(time.formatDateIso(10000, 1, 1))
                    println(time.formatDateIso(2026, 13, 1))
                    println(time.formatDateIso(2026, 4, 31))
                    println(time.isToday(2026, 9, 12))
                    println(time.isToday(2026, 2, 30))
                    var parts = today.split("-")
                    println(parts.size)
                    println(parts[0].length)
                    println(parts[1].length)
                    println(parts[2].length)
                }
                """, "10\n2026-09-13\n2024-02-29\n\n\n\n\n\nfalse\nfalse\n3\n4\n2\n2", Set.of(), tempDir);
        // STDLIB S7f (D3 ratificado 13/09): hoursBetween — floor simétrico
        // (truncado a zero, consistente daysBetween); datas inválidas/hora
        // fora de 0..23 => 0; sem float (FLT001). 5 alvos (JVM/JS/SCRIPT +
        // x86 emit genérico 7+ args FIXADO + riscv B33-ext; aarch tradutor).
        // Golden determinístico: diferencas ±, virada de dia, bissexto,
        // ano 9999->1 (overflow de janela => 0).
        matrix("stdtime4", """
                main() {
                    println(time.hoursBetween(2026, 1, 1, 10, 2026, 1, 2, 12))
                    println(time.hoursBetween(2026, 1, 2, 12, 2026, 1, 1, 10))
                    println(time.hoursBetween(2026, 1, 1, 0, 2026, 1, 1, 23))
                    println(time.hoursBetween(2026, 1, 1, 23, 2026, 1, 2, 0))
                    println(time.hoursBetween(2026, 1, 1, 5, 2026, 1, 1, 5))
                    println(time.hoursBetween(2026, 2, 30, 5, 2026, 3, 1, 5))
                    println(time.hoursBetween(2026, 1, 1, 24, 2026, 1, 2, 5))
                    println(time.hoursBetween(2024, 2, 29, 1, 2024, 3, 1, 1))
                    println(time.hoursBetween(9999, 12, 31, 0, 1, 1, 0, 23))
                    println(time.hoursBetween(2026, 1, 1, 10, 2027, 1, 1, 10))
                }
                """, "26\n-26\n23\n1\n0\n0\n0\n24\n0\n8760", Set.of(), tempDir);
        // STDLIB S7g (D4): parseDateIso — "YYYY-MM-DD" estrito -> serial
        // daysFromEpoch; inválido => 0. MESMO serial de hoursBetween/
        // daysBetween (recomposição s-e = 20709 fecha com stdtime2).
        matrix("stdtime5", """
                main() {
                    println(time.parseDateIso("1970-01-01"))
                    println(time.parseDateIso("2026-09-13"))
                    println(time.parseDateIso("2024-02-29"))
                    println(time.parseDateIso("0001-01-01"))
                    println(time.parseDateIso("9999-12-31"))
                    println(time.parseDateIso("2023-02-29"))
                    println(time.parseDateIso("2026-13-01"))
                    println(time.parseDateIso("garbage"))
                    println(time.parseDateIso("2026-9-13"))
                    var s = time.parseDateIso("2026-09-13")
                    var e = time.parseDateIso("1970-01-01")
                    println(s - e)
                }
                """, "0\n20709\n19782\n-719162\n2932896\n0\n0\n0\n0\n20709", Set.of(), tempDir);
        // §182 ✅ CORRIGIDO 13/09 (lane development .18): parse ISO ESTRITO
        // em TODOS os alvos (Native era a referência; JVM/Script trocaram
        // Integer.parseInt por checagem dígito a dígito; JS kofTimeParseIso
        // reusa o helper estrito do parseDateIso — inconsistência interna
        // do JS eliminada). Golden = consenso estrito, 4 targets SEM
        // exclusão (antes: jvm/script/js excluídos por serem lenientes).
        matrix("parseisostrict", """
                main() {
                    println(time.parseDateIso("+999-01-01"))
                    println(time.parseDateIso("2026-+1-01"))
                    println(time.parseDateIso("2026-01-+1"))
                    println(time.parseDateIso("2026-01-01"))
                    println(time.addDays("+999-01-01", 1))
                    println(time.diffDays("+999-01-01", "1000-01-01"))
                }
                """, "0\n0\n0\n20454\n\n0", Set.of(), tempDir);
        // STDLIB S7h (D1): tzOffsetSeconds — fuso do HOST como getter
        // explícito; paridade JVM×JS (mesmo host, MESMO oracle ZoneId);
        // NÃO-determinístico entre hosts => o valor vem do oracle JVM
        // (medição real); native RECUSA (gap honesto TIME003 — R6).
        int tzNow = java.time.ZoneId.systemDefault().getRules()
                .getOffset(java.time.Instant.now()).getTotalSeconds();
        matrix("stdtime6", """
                main() {
                    var tz = time.tzOffsetSeconds()
                    println(tz % 60)
                    println(tz >= -43200 && tz <= 50400)
                    println(tz)
                }
                """, "0\ntrue\n" + tzNow, Set.of("native"), tempDir);
        // §181 ✅ CORRIGIDO 13/09 (lane development .18): cast Double/Float as
        // Int/Long SATURANTE (JLS 5.1.3 — NaN => 0, > MAX => MAX, < MIN =>
        // MIN) em TODOS os alvos. Antes: x86 cvttsd2si cru = "integer
        // indefinite" (NaN => INT_MIN); JS Math.trunc sem clamp (3e9/NaN/
        // Infinity passavam; NaN as Long LANÇAVA RangeError). Riscv/aarch
        // espelham (feq NaN-check + clamp; aarch via tradutor fcvtzs).
        // Golden do oracle JVM (medição real).
        matrix("castrange", """
                main() {
                    var d = 3.0e9
                    println(d as Int)
                    var n = 0.0 / 0.0
                    println(n as Int)
                    println(n as Int == 0)
                    var inf = 1.0 / 0.0
                    println(inf as Int)
                    println((-inf) as Int)
                    println((1.0e19) as Long)
                    println(n as Long == 0)
                    println((100.7) as Int)
                    println((-100.7) as Int)
                    var f = 3.0e9f
                    println(f as Int)
                    println((-1.5) as Long)
                }
                """, "2147483647\n0\ntrue\n2147483647\n-2147483648\n9223372036854775807\ntrue\n100\n-100\n2147483647\n-1", Set.of(), tempDir);
        // §89 (decisão 3a, 13/09): conversão numérica em receiver PRIMITIVO
        // (`n.toDouble()`/`toInt()`/`toLong()`/`toFloat()`) = alias do cast
        // `as`. Antes: JVM ClassFormatError (owner ""), Native undefined
        // reference, Script `Integer.toDouble/0`, JS TypeError — quebrava nos
        // 4. O fix tinha prova só em JVM+JS (`runBoth`); esta célula trava os
        // 4 targets (incl. Native) e o warning SEM090 (não-erro).
        matrix("numconv", """
                main() {
                    var n = 5
                    println(n.toDouble() == 5.0)
                    var d = 3.7
                    println(d.toInt())
                    println((-2.5).toInt())
                    println(n.toLong())
                    var f = 2.5
                    println(f.toFloat())
                }
                """, "true\n3\n-2\n5\n2.5", Set.of(), tempDir);
    }

    @Test
    void conformanceCoreStrings(@TempDir Path tempDir) throws IOException {
        // bug 43 CORRIGIDO (x86_64, 10/09): kof_string_char_at agora conta
        // code units UTF-16 (café.charAt(3)=233) — verificado no teste abaixo.
        // Residual: substring ainda é byte-based (separado, §43 nota).
        matrix("unicode", """
                main() {
                    var s = "café"
                    println(s.length)
                    println(s.charAt(3))
                    println(s + "!")
                }
                """, "4\n233\ncafé!", Set.of(), tempDir);
        matrix("unicode-astral", """
                main() {
                    var e = "a😀b"
                    println(e.length)
                    println(e.charAt(1))
                    println(e.charAt(2))
                    println(e.charAt(3))
                }
                """, "4\n55357\n56832\n98", Set.of(), tempDir);
        // bug 43 (substring face, 10/09) — code units UTF-16, paridade 4
        // targets. SÓ fronteiras bem-formadas (corte de par astral ao meio
        // exige storage WTF-8 — sub-residual §43, não entra na matriz).
        matrix("unicode-substring", """
                main() {
                    var s = "café"
                    println(s.substring(1))
                    println(s.substring(3))
                    var e = "a😀b"
                    println(e.substring(1, 3))
                    println(e.substring(0, 3).length)
                    println(e.substring(3))
                }
                """, "afé\né\n😀\n3\nb", Set.of(), tempDir);
        // bug 43 (indexOf/lastIndexOf face, 10/09) — índice em code units
        // UTF-16 nos 4 targets (needle vazio / ausente / astral).
        matrix("unicode-indexof", """
                main() {
                    var e = "a😀b😀c"
                    println(e.indexOf("c"))
                    println(e.indexOf("z"))
                    println(e.lastIndexOf("😀"))
                    println("café".indexOf("é"))
                }
                """, "6\n-1\n4\n3", Set.of(), tempDir);
        matrix("strops", """
                main() {
                    var s = "a,b,,c"
                    println(s.split(",").length)
                    println("Hello World".toLowerCase())
                    println("  x  ".trim() + "|")
                }
                """, "4\nhello world\nx|", Set.of(), tempDir);
        // §111 (paridade absoluta): `split` não removía vazios TRAILING no
        // Native nem no JS. O contrato é o Java: "a,"→["a"], ","→[], "a,b,"→
        // ["a","b"], EXCETO input ""→[""] (size 1). Native (RuntimeStringEdit
        // .Lkof_split_done) e JS (helper kofSplit) ganham o trim; JVM/Script
        // (java.lang.String.split) já eram oracle. Também trava o §111 do
        // substring: sentinela "até o fim" do 1-arg passou de 0→-1 (end=0 é
        // valor legítimo do 2-arg — "hello".substring(0,0) era "hello").
        matrix("strsplit", """
                main() {
                    println("a,".split(",").length)
                    println(",".split(",").length)
                    println("a,b,".split(",").length)
                    println("".split(",").length)
                    println("a,b,c".split(",").length)
                    println("hello".substring(0, 0).length)
                    println("hello".substring(2))
                    println("hello".substring(5).length)
                }
                """, "1\n0\n2\n1\n3\n0\nllo\n0", Set.of(), tempDir);
        // §145 (12/09, #101): `isEmpty` não estava no registro — JVM
        // `()Object`, Native link-fail, JS TypeError. 4 casos do reporter:
        // receiver inferido (trim), aninhado, declarado e `!` em if.
        matrix("strisempty", """
                main() {
                    var s = "abc"
                    var t = s.trim()
                    println(t.isEmpty())
                    println(s.trim().isEmpty())
                    println("".isEmpty())
                    if (!t.isEmpty()) { println("ok") }
                }
                """, "false\nfalse\ntrue\nok", Set.of(), tempDir);
        matrix("concat", """
                main() {
                    println("n=" + 42)
                    println(1 + 2 + "x")
                    println("x" + 1 + 2)
                }
                """, "n=42\n3x\nx12", Set.of(), tempDir);
    }

    @Test
    void conformanceCoreCollections(@TempDir Path tempDir) throws IOException {
        matrix("map", """
                main() {
                    var m = mapOf("a", 1)
                    m.put("b", 2)
                    println(m.get("a"))
                    println(m.size)
                }
                """, "1\n2", Set.of(), tempDir);
        matrix("mapint", """
                main() {
                    var m = mapOf(1, "um")
                    m.put(2, "dois")
                    println(m.get(1))
                    println(m.get(2))
                    println(m.size)
                    println(m.remove(1))
                    println(m.get(1))
                }
                """, "um\ndois\n2\num\nnull", Set.of(), tempDir);
        matrix("wrongkey", """
                main() {
                    var m = mapOf(1, "a")
                    println(m.get("x"))
                    var s = setOf("a", "b")
                    println(s.contains(5))
                    var l = listOf("a", "b")
                    println(l.contains(5))
                    var n = mapOf("a", 1)
                    println(n.get(5))
                }
                """, "null\nfalse\nfalse\n0", Set.of(), tempDir);
        matrix("emptylist", """
                main() {
                    var l = listOf()
                    println(l.isEmpty())
                    println(l.size)
                    println(l.contains(1))
                }
                """, "true\n0\nfalse", Set.of(), tempDir);
        matrix("setdedup", """
                main() {
                    var s = setOf(1, 2, 2, 3, 3, 3)
                    println(s.size)
                    println(s.contains(2))
                    println(s.contains(9))
                    println(s.remove(2))
                    println(s.contains(2))
                    println(s.remove(2))
                    println(s.size)
                    var t = setOf("a", "b", "c")
                    println(t.remove("a"))
                    println(t.contains("b"))
                    println(t.contains("a"))
                }
                """, "3\ntrue\nfalse\ntrue\nfalse\nfalse\n2\ntrue\ntrue\nfalse", Set.of(), tempDir);
        matrix("listops", """
                main() {
                    var l = listOf(1, 2, 3)
                    l.add(4)
                    l.set(0, 99)
                    println(l.get(0))
                    println(l.size)
                    println(l.remove(1))
                    println(l.size)
                }
                """, "99\n4\n2\n3", Set.of(), tempDir);
        matrix("mapiter", """
                main() {
                    var m = mapOf("x", 1)
                    m.put("y", 2)
                    m.put("z", 3)
                    var ks = m.keys()
                    var sum = 0
                    for (var k in ks) {
                        sum = sum + m.get(k)
                    }
                    println(sum)
                }
                """, "6", Set.of(), tempDir);
    }

    @Test
    void conformanceCoreNull(@TempDir Path tempDir) throws IOException {
        // SEM048: null não é fabricável (literal banido); T? vem de API.
        matrix("nulleq", """
                main() {
                    var a = mapOf("x", 1).get("y")
                    var b = mapOf("x", 1).get("z")
                    println(a == b)
                    println(a != b)
                }
                """, "true\nfalse", Set.of(), tempDir);
        matrix("nulleqshortcut", """
                main() {
                    var a = mapOf("x", 1).get("y")
                    var b = mapOf("x", 1).get("z")
                    if (a == b) { println("iguais") } else { println("dif") }
                    if (a != b) { println("ne") } else { println("nao-ne") }
                }
                """, "iguais\nnao-ne", Set.of(), tempDir);
    }

    @Test
    void conformanceCoreControl(@TempDir Path tempDir) throws IOException {
        matrix("nestedif", """
                main() {
                    var x = 5
                    var r = if (x > 0) if (x > 10) "big" else "small" else "neg"
                    println(r)
                }
                """, "small", Set.of(), tempDir);
        // issue #57 — if-expr com branches heterogêneos (Int vs String) como
        // argumento direto: o typer devolve o thenType e o box pós-join
        // aplicava Integer.valueOf ao ramo String → VerifyError. Fix: ramos
        // primitivos boxeados in-branch + skip do pós-box (só codegen; o
        // check continua aprovando). JS excluído: underflow pré-existente
        // no backend KofJS p/ if heterogêneo (known-bugs §69, provado com
        // Bug 69 CORRIGIDO: paridade JVM+Native+Script+JS.
        matrix("ifexpr-heterogeneous-direct", """
                main() {
                    var s = ""
                    println(if (s == "") 1 else "s")
                }
                """, "1", Set.of(), tempDir);
        // §205 (ramo FALSO): o print direto pelo outro lado do if — no
        // código antigo (SIGSEGV) ambos os lados crashavam; a prova precisa
        // cobrir os dois dispatchs (valueOf(int) E println(string)).
        matrix("ifexpr-heterogeneous-direct-else", """
                main() {
                    var s = "x"
                    println(if (s == "") 1 else "s")
                }
                """, "s", Set.of(), tempDir);
        // mesma classe da #57 p/ switch-expression heterogêneo.
        matrix("switchexpr-heterogeneous-direct", """
                main() {
                    var s = ""
                    println(switch (s) {
                        case "" -> 1
                        default -> "s"
                    })
                }
                """, "1", Set.of(), tempDir);
        // §205: lado FALSO do switch heterogêneo (default) + multi-arms — a
        // chain de comparação precisa cair no próximo braço. No código antigo,
        // crashava em todos os lados.
        matrix("switchexpr-heterogeneous-direct-else", """
                main() {
                    var s = "x"
                    println(switch (s) {
                        case "" -> 1
                        default -> "s"
                    })
                }
                """, "s", Set.of(), tempDir);
        matrix("switchexpr-heterogeneous-multi", """
                main() {
                    var v = 5
                    println(switch (v) {
                        case 1 -> "a"
                        case 5 -> 2
                        default -> "d"
                    })
                }
                """, "2", Set.of(), tempDir);
        matrix("switchexpr-heterogeneous-multi-default", """
                main() {
                    var v = 9
                    println(switch (v) {
                        case 1 -> "a"
                        case 5 -> 2
                        default -> "d"
                    })
                }
                """, "d", Set.of(), tempDir);
        // §70 — heterogêneo primitivo-vs-primitivo de slots distintos
        // (Int 1-word vs Long 2-word): o join quebrava o COMPUTE_FRAMES
        // (crash AIOOBE) em vez de VerifyError. Fix: cada ramo boxeado
        // p/ SEU boxed (sem widening: `2L` imprime `2`, paridade script).
        matrix("ifexpr-intlong-direct", """
                main() {
                    var s = ""
                    println(if (s == "") 1 else 2L)
                }
                """, "1", Set.of(), tempDir);
        matrix("ifexpr-longdouble-direct", """
                main() {
                    var s = ""
                    println(if (s == "") 2L else 2.5)
                }
                """, "2", Set.of(), tempDir);
        matrix("ifexpr-intnull-direct", """
                main() {
                    var s = ""
                    println(if (s == "") 1 else null)
                }
                """, "1", Set.of(), tempDir);
        matrix("switchexpr", """
                main() {
                    var v = 3
                    var d = switch (v) {
                        case 1 -> "one"
                        case 2 -> "two"
                        case 3 -> "three"
                        default -> "other"
                    }
                    println(d)
                }
                """, "three", Set.of(), tempDir);
        matrix("breakcont", """
                main() {
                    var sum = 0
                    for (var i in listOf(1,2,3,4,5)) {
                        if (i == 2) { continue }
                        if (i == 4) { break }
                        sum = sum + i
                    }
                    println(sum)
                }
                """, "4", Set.of(), tempDir);
        matrix("recursion", """
                Int fact(Int n) {
                    if (n <= 1) { return 1 }
                    return n * fact(n - 1)
                }
                main() {
                    println(fact(10))
                }
                """, "3628800", Set.of(), tempDir);
    }

    @Test
    void conformanceCoreFunctions(@TempDir Path tempDir) throws IOException {
        matrix("lambdachain", """
                main() {
                    var l = listOf(1,2,3,4)
                    var r = l.filter((x: Int) -> x > 1).map((x: Int) -> x * 10).reduce((a: Int, b: Int) -> a + b, 0)
                    println(r)
                }
                """, "90", Set.of(), tempDir);
        matrix("lambdacapture", """
                main() {
                    var n = 0
                    var inc = () -> { n = n + 1 }
                    inc()
                    inc()
                    inc()
                    println(n)
                }
                """, "3", Set.of(), tempDir);
        // §113: `array2d` era enganoso — só exercitava 1-D (new Int[3]).
        // Agora é multidimensional DE VERDADE: a célula que o bug-59 nunca
        // deixou de fora porque o Native NUNCA alocou nada (KofNewMultiArray
        // caía no default -> {} → SIGSEGV). Oracle: zero-fill + lengths +
        // store/load em todas as células, 3-D incluso (stride de ponteiro
        // interno 8; folha 0). 4/4 targets sem exclusão.
        matrix("array2d", """
                main() {
                    var a = new Int[3]
                    a[0] = 10
                    a[1] = 20
                    a[2] = 30
                    println(a[0] + a[1] + a[2])
                    println(a.length)
                    var m = new Int[2][3]
                    println(m.length)
                    println(m[1].length)
                    println(m[0][2])
                    m[1][2] = 7
                    println(m[1][2])
                    var c = new Int[2][2][2]
                    println(c.length)
                    println(c[0][1].length)
                    c[1][0][1] = 9
                    println(c[1][0][1])
                    println(c[0][0][0])
                }
                """, "60\n3\n2\n3\n0\n7\n2\n2\n9\n0", Set.of(), tempDir);
        // §121: store de Int em slot Long (crashava o COMPUTE_FRAMES do JVM —
        // o bloco de conversão do ExpressionAssignmentLowerer era um if {} que
        // sÓ comentava a promessa). widened 1-D e 2-D; oracle do interpretador.
        matrix("arrlongstore", """
                main() {
                    var e = new Long[4]
                    e[1] = 9
                    println(e[1])
                    var c = new Long[2][2]
                    c[1][0] = 3
                    println(c[1][0])
                    println(e[0])
                }
                """, "9\n3\n0", Set.of(), tempDir);
        // §184 (13/09): store em `Byte[]`/`Short[]` com valor FORA de faixa —
        // JVM/Native/Script truncam (BASTORE/SASTORE, 8/16 bits com sinal). O
        // JS gravava o valor cru (kofArraySet não conhecia o tipo do
        // elemento) — §184 fix na RAIZ: o emitter passa o kind (byte/short)
        // p/ kofArraySet, que agora aplica o mesmo estreitamento (i2b/i2s).
        // Cell cobre os 4 targets (era `Set.of("js")`, Q5 false-green).
        matrix("narrowarr", """
                main() {
                    var b = new Byte[1]
                    b[0] = 130
                    println(b[0])
                    var s = new Short[1]
                    s[0] = 70000
                    println(s[0])
                }
                """, "-126\n4464", Set.of(), tempDir);
        // §185 (13/09): store em elemento de `Char[]`/`Bool[]` — o
        // interpretador (Script) LANÇA "argument type mismatch" no caminho
        // vivo `KofInterpreter:306` (`coerceFor` devolve Integer; `Array.set`
        // de `char[]`/`boolean[]` exige Character/Boolean). JVM/Native/JS
        // imprimem o code unit/bool. PARTIAL script.
        matrix("chararr", """
                main() {
                    var c = new Char[2]
                    c[0] = 'A'
                    c[1] = 66 as Char
                    println(c[0])
                    println(c[1])
                    var b = new Bool[2]
                    b[0] = true
                    b[1] = false
                    println(b[0])
                    println(b[1])
                }
                """, "65\n66\ntrue\nfalse", Set.of("script"), tempDir);
        // §186 (13/09): inicializador de campo `static` NÃO-literal. O
        // front-end só levava `LiteralExpr` direto ao `initialValue`; `-1`
        // (unário) e `2 + 3` (binário dobrado) ficavam de fora e, no JVM,
        // viravam `this.x = ...` no construtor (PUTFIELD em campo estático →
        // IncompatibleClassChangeError) — liam 0/undefined. Fix: dobrar
        // expressões constantes em `lowerField` + não injetar estáticos no
        // construtor. Cobre os 4 targets.
        matrix("staticinit", """
                class H {
                    static Int neg = -1
                    static Int fold = 2 + 3
                    static Long wide = -7L
                    static Double frac = -1.5
                    static String cat = "a" + "b"
                    static Bool yes = !false
                    static Int lit = 7
                }
                main() {
                    println(H.neg)
                    println(H.fold)
                    println(H.wide)
                    println(H.frac)
                    println(H.cat)
                    println(H.yes)
                    println(H.lit)
                }
                """, "-1\n5\n-7\n-1.5\nab\ntrue\n7", Set.of(), tempDir);
        // §187 (13/09): `Char[]` NÃO estreitava a 16 bits no Native (o
        // `elementTypeSize` mapeia char→4 e o `kof_array_set` fazia `movl`
        // cru) nem no JS (Array puro, §184); o cast escalar `as Char` está
        // certo nos 4. JVM `CASTORE`/`CALOAD` trunca/zero-estende. **Face
        // Native CORRIGIDA 13/09** (máscara 16-bit no store x86 `movzwl` e
        // riscv/aarch `slli 48`/`srli 48` — stride 4 mantido, load `movslq`
        // segue correto). A 2-D trava o `kof_multi_alloc`; o `Short[]`
        // negativo é o controle de SINAL (prova que a máscara não virou
        // zero-extend genérico). §184/§187 fix na RAIZ: o JS também estreita
        // Char[] (kind=3 → `& 0xFFFF`) — só o `script` segue PARTIAL (§185,
        // crash do interpretador no store de Char[]).
        matrix("charnarrow", """
                main() {
                    var c = new Char[2]
                    c[0] = 70000
                    c[1] = -1
                    println(c[0])
                    println(c[1])
                    var d = new Char[2][2]
                    d[0][0] = 70000
                    d[1][1] = -1
                    println(d[0][0])
                    println(d[1][1])
                    var s = new Short[1]
                    s[0] = -1
                    println(s[0])
                }
                """, "4464\n65535\n4464\n65535\n-1", Set.of("script"), tempDir);
        // §131 (decisão 10a, 13/09): sobrecarga de MÉTODO de classe por
        // assinatura (aridade/tipos). Antes: SEM013 no JVM (último def
        // sobrescrevia) e colisão de símbolo no Native. Prova só JVM+JS
        // (`runBoth`) — esta célula trava os 4 targets.
        matrix("methodoverload", """
                class Calc {
                    Int twice(Int x) { return this.twice(x, x) }
                    Int twice(Int x, Int y) { return x + y }
                }
                main() {
                    var c = Calc()
                    println(c.twice(21))
                    println(c.twice(3, 4))
                }
                """, "42\n7", Set.of(), tempDir);
        // §131-residual (13/09): overload de MESMA aridade e TIPOS diferentes
        // (`twice(Int)`/`twice(String)`) — o call site nativo resolvia a
        // vtable só pela ARIDADE e caía no 1º slot; passar String p/ um
        // parâmetro Int dava SIGSEGV. JVM/Script/JS sempre corretos (descritor/
        // SAM). Fix: resolver o slot pelo NOME + TIPOS do call site.
        matrix("methodoverloadtype", """
                class Calc {
                    Int twice(Int x) { return x + x }
                    String twice(String s) { return s + s }
                }
                main() {
                    var c = Calc()
                    println(c.twice(21))
                    println(c.twice("ab"))
                }
                """, "42\nabab", Set.of(), tempDir);
        // §163 (13/09): parâmetro largo (`Long`/`Double`) NÃO-primeiro no
        // interpretador (Script) — a IR dá 2 slots a largos, mas `invokeKof`
        // copiava `args` compacto → 2º largo lido como `null` (NPE). JVM/
        // Native/JS sempre corretos. Fix: posicionar cada arg no slot real.
        // Só `Long` (JS imprime `Double` sem o ".0" — bug 44, alheio).
        matrix("wideparams", """
                Long g(Long a, Long b) { return a + b }
                Long mixed(Int a, Long b, Long c) { return b + c }
                Long unread(Long a, Long b) { return a }
                class Box {
                    Long v
                    public constructor(Long v) { this.v = v }
                    Long add(Long x) { return this.v + x }
                }
                main() {
                    println(g(10000000000L, 2L))
                    println(mixed(9, 10000000000L, 7L))
                    println(unread(10000000000L, 5L))
                    var b = Box(10000000000L)
                    println(b.add(5L))
                }
                """, "10000000002\n10000000007\n10000000000\n10000000005", Set.of(), tempDir);
        // §155 (13/09): tipo-função como ARGUMENTO GENÉRICO declarado
        // (`List<(Int) -> Int>`) — o parser montava a string de tipo sem
        // espaços (`"(Int)->Int"`), e `Type.of` só reconhece `"(Int) -> Int"`
        // → ClassType com nome vazio → ClassFormatError no JVM e lixo nos
        // outros 3. Fix no parser (parseFunctionTypeRef preserva espaços);
        // a prova automatizada era só JVM+Native. Esta célula trava 4 targets.
        matrix("fntypegeneric", """
                main() {
                    List<(Int) -> Int> l = listOf((x: Int) -> x + 1)
                    println(l.get(0)(5))
                }
                """, "6", Set.of(), tempDir);
        // §156 (13/09): `List` HETEROGÊNEO de lambdas com a MESMA assinatura
        // → ClassCastException no JVM (`Lambda1` não é `Lambda0`); os outros 3
        // já imprimiam certo. Fix na inferência do elemento (unifica a SAM).
        // A prova automatizada era só JVM+Native; esta célula trava 4 targets.
        matrix("lambdalisthet", """
                main() {
                    var l = listOf((x: Int) -> x + 1, (x: Int) -> x * 2)
                    println(l.get(1)(5))
                    println(l.get(0)(5))
                }
                """, "10\n6", Set.of(), tempDir);
        // §157 (13/09): `mapOf()` vazio + 1º `put` de valor Long (pin-alinha)
        // — o emit lia o valueType antes do pin (retType Unknown → sem unbox,
        // 1 Object) mas o descarte da statement via o local depois do pin
        // (Long → POP2) → underflow de frame/VerifyError no JVM. Fix no
        // CollectionCallLowerer (alinhar key/value ao tipo pinado). O doc
        // dizia "célula nova na matriz", mas ela não existia — esta é ela.
        matrix("mapputlong", """
                main() {
                    var m = mapOf()
                    var v = 9000000001L
                    m.put("k", v)
                    println(m.get("k"))
                    println(m.size)
                }
                """, "9000000001\n1", Set.of(), tempDir);
        // §127-JVM (13/09): `x as () -> Int` — o RHS de `as` era parseado
        // como LambdaExpr (não type-ref) → checkcast com alvo "?" →
        // VerifyError no JVM. Fix no parser+lowerer+typer. A prova
        // automatizada era só JVM+Native (`LambdaE2ETest.castToFunctionType`);
        // Script/JS eram sonda manual. Esta célula trava 4 targets.
        matrix("castfn", """
                main() {
                    var l = listOf(() -> 5)
                    var g = l.get(0) as () -> Int
                    println(g() == 5)
                }
                """, "true", Set.of(), tempDir);
    }

    @Test
    void conformanceCoreRecordsAndStatics(@TempDir Path tempDir) throws IOException {
        matrix("record", """
                record P(Int x, Int y)
                main() {
                    var a = P(1,2)
                    var b = P(1,2)
                    println(a == b)
                    println(a)
                    println(a.x())
                }
                """, "true\nP[x=1, y=2]\n1", Set.of(), tempDir);
        // Metade JS CORRIGIDA 07/09 (verificado 08/09: JS roda 'true').
        // Metade Native CORRIGIDA (buildRecordHashCodeMethod em CompilerRecordSupport).
        matrix("recordhash", """
                record P(Int x, Int y)
                main() {
                    var a = P(1,2)
                    var b = P(1,2)
                    println(a.hashCode() == b.hashCode())
                }
                """, "true", Set.of(), tempDir);
        // §114 (face String): `==` de record com campo String era PONTEIRO no
        // Native (S("ab")==S("ab") → false vs JVM/JS/Script true). Agora o
        // campo String compara por CONTEÚDO via kof_string_equals (null-safe).
        // Campo de RECORD aninhado / hash de referência / record-em-coleção
        // ficam no §104b-ii (equals/hashCode genérico por vtable — unidade
        // própria; a célula objmethods mantém native excluído).
        matrix("recordstrfield", """
                record S(String t)
                record T(Int n, String s)
                main() {
                    println(S("ab") == S("ab"))
                    println(S("ab") == S("cd"))
                    println(T(1, "x") == T(1, "x"))
                    println(T(2, "x") == T(1, "x"))
                }
                """, "true\nfalse\ntrue\nfalse", Set.of(), tempDir);
        // PARTIAL: bug 41 (Native stub vazio KofGetStatic/KofPutStatic → lixo).
        matrix("staticfield", """
                class Counter {
                    static Int count = 0
                    static Int bump() {
                        count = count + 1
                        return count
                    }
                }
                main() {
                    println(Counter.bump())
                    println(Counter.bump())
                    println(Counter.count)
                }
                """, "1\n2\n2", Set.of(), tempDir);
        matrix("staticpluseq", """
                class Counter2 {
                    static Int count = 0
                    static Int bump() {
                        count += 2
                        return count
                    }
                }
                main() {
                    println(Counter2.bump())
                    println(Counter2.bump())
                    println(Counter2.count)
                }
                """, "2\n4\n4", Set.of(), tempDir);
    }

    // ===== Lote 2 — erros/null/JSON =====

    @Test
    void conformanceErrors(@TempDir Path tempDir) throws IOException {
        matrix("trycatch", """
                main() {
                    try {
                        throw "not found: x"
                    } catch (String e) {
                        println("caught:" + e)
                    }
                    println("after")
                }
                """, "caught:not found: x\nafter", Set.of(), tempDir);
        matrix("trycatchfin", """
                main() {
                    try {
                        println("in")
                    } catch (String e) {
                        println("c:" + e)
                    } finally {
                        println("fin")
                    }
                    println("after")
                }
                """, "in\nfin\nafter", Set.of(), tempDir);
        matrix("throwprop", """
                Int boom() {
                    throw "kaboom"
                }
                main() {
                    try {
                        boom()
                    } catch (String e) {
                        println("got:" + e)
                    }
                }
                """, "got:kaboom", Set.of(), tempDir);
        // FIXED (bug 49, 07/09): KofJS não compilava try aninhado
        // (COMP002 "try expected KofTryEnd") — JsControlFlowParser
        // consome o label de saída (done) do try no caso sem-finally.
        matrix("nestedtry", """
                main() {
                    try {
                        try {
                            throw "inner"
                        } catch (String e) {
                            println("caught-inner:" + e)
                        }
                    } catch (String e) {
                        println("caught-outer")
                    }
                    println("end")
                }
                """, "caught-inner:inner\nend", Set.of(), tempDir);
        // bug 52 (re-throw em catch) — CORRIGIDO como efeito colateral do fix
        // do bug 45 (c727fee, finally-c/return no try): o parser JS passou a
        // tratar o corpo de catch que termina em KofThrow pela região externa.
        // 4 targets agora concordam (JVM/Native/Script/JS).
        matrix("catchrethrow", """
                main() {
                    try {
                        try {
                            throw "x"
                        } catch (String e) {
                            throw "re:" + e
                        }
                    } catch (String e) {
                        println("outer:" + e)
                    }
                    println("end")
                }
                """, "outer:re:x\nend", Set.of(), tempDir);
        // §147 (12/09, #101): if/else com then terminando em throw — o
        // parser JS engolia o epílogo pós-if para dentro do else (com um
        // `return` fantasma): o ramo else nunca caía no epílogo (dead
        // code; while→hang no caso do reporter). O IR é linear (sem
        // Jump/Label de end); o fix pára o else antes do trailing-return
        // do método. Prova nos dois ramos (throw tomado e não-tomado).
        matrix("ifthrowelse", """
                main() {
                    var n = 2
                    if (n == 1) {
                        throw "boom"
                    } else {
                        println("else")
                    }
                    println("after")
                }
                """, "else\nafter", Set.of(), tempDir);
    }

    @Test
    void conformanceNullSafety(@TempDir Path tempDir) throws IOException {
        matrix("nullnarrow", """
                String find(String k) {
                    if (k == "a") { return "A" }
                    return null
                }
                main() {
                    var v = find("a")
                    if (v != null) {
                        println("val=" + v.length)
                    }
                    var w = find("z")
                    if (w != null) {
                        println("never")
                    } else {
                        println("null-ok")
                    }
                }
                """, "val=1\nnull-ok", Set.of(), tempDir);
    }

    @Test
    void conformanceJson(@TempDir Path tempDir) throws IOException {
        matrix("jsonenc-int", """
                main() {
                    println(json.encode(42))
                    println(json.encode("oi"))
                    println(json.encode(true))
                }
                """, "42\n\"oi\"\ntrue", Set.of(), tempDir);
        matrix("jsonenc-list", """
                main() {
                    println(json.encode(listOf(1, 2, 3)))
                }
                """, "[1,2,3]", Set.of(), tempDir);
        matrix("jsonenc-record", """
                record P(Int x, Int y)
                main() {
                    println(json.encode(P(1, 2)))
                }
                """, "{\"x\":1,\"y\":2}", Set.of(), tempDir);
        // §106 (decisão 2b): json.encode(Map) = objeto JSON com chaves SORTED
        // (determinismo). A ordem de inserção é invertida de propósito
        // (b depois a) p/ provar que a saída é ordenada, não insertion-order.
        // Q3: valor string (escape) + mapa vazio.
        matrix("jsonenc-map", """
                import kof.json.*
                main() {
                    var m = mapOf("b", 2)
                    m.put("a", 1)
                    println(json.encode(m))
                    var s = mapOf("z", "last")
                    s.put("a", "first")
                    println(json.encode(s))
                    println(json.encode(mapOf()))
                }
                """, "{\"a\":1,\"b\":2}\n{\"a\":\"first\",\"z\":\"last\"}\n{}", Set.of(), tempDir);
        matrix("jsondec-int", """
                main() {
                    println(json.decode<Int>("7"))
                    println(json.decode<String>("\\"oi\\""))
                    println(json.decode<Bool>("true"))
                }
                """, "7\noi\ntrue", Set.of(), tempDir);
        matrix("jsondec-list", """
                main() {
                    var l = json.decode<List<Int>>("[1, 2, 3]")
                    println(l.size())
                    println(l.get(1))
                }
                """, "3\n2", Set.of(), tempDir);
        // DONE após fix 07/09 (decode<Record> no interpretador —
        // KofInterpreterRuntime.decodeKofValue espelha encodeKof; antes:
        // interpretador exit 1 stderr "Point").
        matrix("jsondec-record", """
                record P(Int x, Int y)
                main() {
                    var p = json.decode<P>("{\\"x\\":1,\\"y\\":2}")
                    println(p.x())
                    println(p.y())
                }
                """, "1\n2", Set.of(), tempDir);
        // PARTIAL: bug 48 (metade Native) — Native não compila
        // (JsonDispatch sem ramo lista-de-classe). JVM/Script/JS dão 2/2;
        // a metade Script foi corrigida em KofInterpreterRuntime
        // (kof_json_decode_object_list → mapeia itens p/ KofObj).
        matrix("jsondec-recordlist", """
                record P(Int x)
                main() {
                    var l = json.decode<List<P>>("[{\\"x\\":1},{\\"x\\":2}]")
                    println(l.size())
                    println(l.get(1).x)
                }
                """, "2\n2", Set.of("native"), tempDir);
        // §103.1 (#103): decode<Map<String,Record>> — o caso do report
        // (arquivo de config keyed). Antes: kof_json_decode_Map inexistente
        // (NoSuchMethodError no JVM). Agora objeto_list-estilo por valor.
        // Native: JSN004 (gap honesto — runtime nativo sem decoder de mapa).
        matrix("jsondec-map", """
                record CardText(String name, String upright)
                main() {
                    var m = json.decode<Map<String, CardText>>("{\\"0\\":{\\"name\\":\\"Fool\\",\\"upright\\":\\"fresh\\"},\\"1\\":{\\"name\\":\\"Magician\\",\\"upright\\":\\"focus\\"}}")
                    println(m.size)
                    var c = m.get("1")
                    if (c != null) { println(c.name) }
                }
                """, "2\nMagician", Set.of("native"), tempDir);
        // §103.1 (#103): decode<Map<String,String>> — valor escalar (String),
        // caminho kof_json_decode_map (não object_map).
        matrix("jsondec-mapscalar", """
                main() {
                    var m = json.decode<Map<String, String>>("{\\"a\\":\\"x\\",\\"b\\":\\"y\\"}")
                    println(m.size)
                    println(m.get("b"))
                }
                """, "2\ny", Set.of("native"), tempDir);
    }

    // ===== Lote 3 — concorrência DETERMINÍSTICA (ordem garantida por await/
    // FIFO; o order de fire-and-forget é NÃO-determinístico por design e fica
    // em KofConcurrency2Test com asserções frouxas) =====

    @Test
    void conformanceConcurrencyDeterministic(@TempDir Path tempDir) throws IOException {
        // ordem garantida: await bloqueia main até a task terminar.
        matrix("spawnawait-fn", """
                Int calc(Int x) {
                    return x * 2
                }
                main() {
                    var h = spawn calc(21)
                    var v = await h
                    println(v)
                }
                """, "42", Set.of(), tempDir);
        // dois handles: cada await devolve o SEU resultado (ordem dos awaits).
        matrix("spawnawait-two", """
                Int calc(Int x) {
                    return x + 1
                }
                main() {
                    var h1 = spawn calc(1)
                    var h2 = spawn calc(10)
                    println(await h1)
                    println(await h2)
                }
                """, "2\n11", Set.of(), tempDir);
        // spawn-EXPR com LAMBDA LITERAL que retorna valor (bug 46): a ordem é
        // garantida (await bloqueia). O typer de `spawn { return ... }` devolvia
        // Handle<FunctionType> e o await vazava FunctionType -> println String ->
        // SIGSEGV no Native; interp/JVM/JS davam 42. Fix 09/09: desembrulhar o
        // returnType. Travado nos 4 targets (antes excluía native).
        matrix("spawnexpr-return", """
                main() {
                    var n = 21
                    var h = spawn { return n * 2 }
                    println(await h)
                }
                """, "42", Set.of(), tempDir);
        // canal na MESMA thread: FIFO, ordem garantida, sem spawn.
        matrix("channel-samethread", """
                main() {
                    val c = channel<Int>()
                    c.send(5)
                    c.send(6)
                    var s = 0
                    var i = 0
                    while (i < 2) {
                        s = s + c.receive()
                        i++
                    }
                    println("s=" + s)
                    val cs = channel<String>()
                    cs.send("a")
                    cs.send("b")
                    println(cs.receive() + cs.receive())
                }
                """, "s=11\nab", Set.of(), tempDir);
        // canal + spawn (send na task): 4 targets dão 42 (bug 50 corrigido
        // 09/09 — usleep clobberava %rsi=&lock no caminho de fila vazia).
        matrix("channel-spawn", """
                main() {
                    val c = channel<Int>()
                    spawn {
                        c.send(42)
                    }
                    val v = c.receive()
                    println(v)
                }
                """, "42", Set.of(), tempDir);
        matrix("channel-spawn-two", """
                main() {
                    val c = channel<Int>()
                    spawn {
                        c.send(1)
                        c.send(2)
                    }
                    println(c.receive())
                    println(c.receive())
                }
                """, "1\n2", Set.of(), tempDir);
    }
}
