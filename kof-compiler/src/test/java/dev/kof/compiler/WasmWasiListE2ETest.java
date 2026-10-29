package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Fase E (27/10, #776): as faces LIST do WASI (as colecoes da unidade 15.3).
 * O contrato e o PRINCIPIO: um frontend nunca tem comportamento diferente
 * entre backends — cada face compara o stdout do wasmtime com o ORACULO JVM
 * da MESMA fonte e valida o modulo no wasm-tools oficial. O ratchet de
 * higiene (≤500) forcou a saida das faces de lista do WasmWasiFlowE2ETest
 * (mesmo precedente do split do WasmWasiE2ETest).
 */
class WasmWasiListE2ETest {

    static String jvmBin() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    static Path host(String tool) {
        String home = System.getenv().getOrDefault("KOF_WASM_HOME",
                System.getProperty("user.home") + "/.local/share/kof-wasm");
        Path p = Path.of(home, "bin", tool);
        return Files.isExecutable(p) ? p : null;
    }

    static void assertListFaceMatchesJvm(Path dir) throws Exception {
        var wasmtime = host("wasmtime");
        var wasmTools = host("wasm-tools");
        var driver = new CompilerDriver();
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-jvm"), Target.JVM);
        assertTrue(jvm.success(), "jvm compile: " + jvm.diagnostics().getDiagnostics());
        var oracle = new ProcessBuilder(List.of(jvmBin(), "-cp",
                Path.of(dir.toString(), "out-jvm").toString(), "Default.Main"))
                .redirectErrorStream(true).start();
        String expected = new String(oracle.getInputStream().readAllBytes());
        assertTrue(oracle.waitFor(120, TimeUnit.SECONDS) && oracle.exitValue() == 0,
                "JVM oracle: " + expected);
        var wasi = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-wasi"), Target.WASI);
        assertTrue(wasi.success(), "wasi compile: " + wasi.diagnostics().getDiagnostics());
        Path wasm = Path.of(dir.toString(), "out-wasi", "Default", "Main.wasm");
        var v = new ProcessBuilder(wasmTools.toString(), "validate", wasm.toString())
                .redirectErrorStream(true).start();
        String vout = new String(v.getInputStream().readAllBytes());
        assertTrue(v.waitFor(60, TimeUnit.SECONDS) && v.exitValue() == 0,
                "module must pass the official validator: " + vout);
        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run", wasm.toString()))
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS), "host within 60s");
        assertEquals(0, proc.exitValue(), "clean WASI exit: " + out);
        assertEquals(expected.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                out.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                "the frontend must behave identically on the JVM and on WASI");
    }


    @Test
    void listOfBasicsMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // Fase E (27/10, #776): LIST fatia 1 — List<Int> no WASI: o desugar
        // listOf (kof_list_new + N× add) + size/get no bump heap. O handle e'
        // uma CELULA INDIRETA ([dataPtr][len][cap]) para o add que cresce
        // (dobra, copia, reescreve a celula) NUNCA invalidar o handle do
        // chamador — o contrato observavel do ArrayList da JVM. A face
        // forc a DOIS crecimentos (11 elems: cap 4->8->16 — o primeiro
        // corte desta fatia pegou o heapAlloc gravando o DELTA no global e
        // a copia sobreposta borrava o bloco: 1,1,1,1 medido), a iteracao
        // com o indice em variavel e o add de valor computado.
        var wasmtime = host("wasmtime");
        var wasmTools = host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var xs = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
                    println(xs.size())
                    var i = 0
                    while (i < xs.size()) {
                        println(xs.get(i))
                        i = i + 1
                    }
                    var ys = listOf(100)
                    ys.add(200)
                    ys.add(300)
                    println(ys.get(0) + ys.get(1) + ys.get(2))
                    var z = 40
                    ys.add(z * 2)
                    println(ys.get(3))
                }
                """);
        assertListFaceMatchesJvm(dir);
    }

    @Test
    void listMutatorsMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // Fase E (27/10, #776): LIST fatia 3 — os MUTADORES: set (store com
        // trap de fronteira — o Kof set e' void, o velho NAO volta), remove
        // (por indice, desloca para a esquerda, DEVOLVE o elemento — o
        // KofPop do desugar o consome) e clear (len=0). set/remove tem SEM
        // fantasma na pilha (diferente das consultas da fatia 2 — medido no
        // WAT: o drop aqui consumia o proprio valor). Os elems String viajam
        // como na fatia 2 (o handle zero-extendido no slot i64).
        var wasmtime = host("wasmtime");
        var wasmTools = host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var xs = listOf(10, 20, 30)
                    xs.set(1, 99)
                    println(xs.get(1))
                    xs.remove(0)
                    println(xs.size())
                    println(xs.get(0))
                    xs.clear()
                    println(xs.size())
                    println(xs.isEmpty())
                    var ss = listOf("a", "b", "c")
                    ss.set(1, "z")
                    println(ss.get(1))
                    ss.remove(0)
                    println(ss.get(0))
                    println(ss.size())
                }
                """);
        assertListFaceMatchesJvm(dir);
    }

    @Test
    void listStringQueriesMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // Fase E (27/10, #776): LIST fatia 4 — as consultas sobre
        // List<String> com IGUALDADE DE CONTEUDO (o kof.strEq existente, o
        // mesmo do fold de `==` de record — nenhum comparador novo). O slot
        // guarda o HANDLE (i64 zero-extendido): volta i32 (wrap) antes do
        // strEq; o phantom das consultas (o receiver tipado como param 0 —
        // para strings o desugar materializa `int 1`, medido no IR) continua
        // sendo dropado. A face tambem sela o achado da rodada: a insercao
        // posicional NAO existe na linguagem (SEM072 nos dois backends).
        var wasmtime = host("wasmtime");
        var wasmTools = host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var ss = listOf("a", "b", "c")
                    println(ss.contains("b"))
                    println(ss.contains("x"))
                    println(ss.indexOf("c"))
                    println(ss.lastIndexOf("a"))
                    println(ss.lastIndexOf("x"))
                    var t = listOf("hello", "world")
                    println(t.contains("world"))
                    println(t.indexOf("hello"))
                }
                """);
        assertListFaceMatchesJvm(dir);
    }

    @Test
    void listDoubleElemsMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // Fase E (27/10, #776): LIST fatia 5 — List<Double>: o f64 anda nos
        // slots i64 como BITS (add/set reinterpretam 0xbd; get volta 0xbf) —
        // ZERO runtime novo; as consultas comparam BITS = exatamente
        // Double.equals (NaN=NaN por bits; 0.0 vs -0.0 distintos). A ordem
        // nas consultas: o drop do fantasma vem ANTES do reinterpret (a
        // ordem antiga reinterpretava o proprio phantom — o validador
        // rejeitou). O descarte do remove de Int usa KofPop2 (o pop WIDE) —
        // baixado como o mesmo drop.
        var wasmtime = host("wasmtime");
        var wasmTools = host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var ds = listOf(1.5, 2.5, 3.5)
                    ds.add(4.5)
                    ds.set(0, 9.75)
                    println(ds.size())
                    println(ds.get(0))
                    println(ds.contains(2.5))
                    println(ds.contains(9.9))
                    println(ds.indexOf(3.5))
                    ds.remove(1)
                    println(ds.get(1))
                    println(ds.size())
                    var e = 5.5
                    ds.add(e * 2.0)
                    println(ds.get(ds.size() - 1))
                }
                """);
        assertListFaceMatchesJvm(dir);
    }

    @Test
    void listRecordElemsMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // LIST-REC (29/10, #776): os ELEMENTOS de RECORD nas fatias de
        // LISTA — as rotas de handle (new/add/set/get/remove-índice: o
        // MESMO slot do handle i32, o rastro "record:Point"/"Point") e as
        // BUSCAS por CONTEÚDO (kof.listContainsRec/<Rec> etc. — o MESMO
        // corpo das variantes Str com o compare = o fold COMPARTILHADO do
        // SET-REC `kof.recEq.<Rec>`; o probe anda HANDLE CRU i32 — SEM
        // extend). As faces: size/get/contains/indexOf/lastIndexOf, o add,
        // o set + o println do ELEMENTO (o toString do record), o remove
        // por índice (a LEI DA LARGURA do resultado via isRefElem), o fold
        // de campo String/Double/Bool/Char e o record ANINHADO.
        var wasmtime = host("wasmtime");
        var wasmTools = host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                record Point(Int x, Int y)
                record Book(String title, Double price, Bool inPrint, Char grade)
                record Inner(Int a)
                record Outer(Inner in)
                main(String[] args) {
                    var l = listOf(Point(1, 2), Point(3, 4))
                    println(l.size())
                    println(l.get(0).x)
                    println(l.contains(Point(1, 2)))
                    println(l.indexOf(Point(3, 4)))
                    println(l.lastIndexOf(Point(1, 2)))
                    l.add(Point(5, 6))
                    println(l.size())
                    l.set(0, Point(9, 9))
                    println(l.get(0))
                    println(l.remove(1))
                    println(l.size())
                    var b = listOf(Book("Dune", 9.9, true, 'A'), Book("Dune", 9.9, true, 'A'), Book("LotR", 12.5, false, 'B'))
                    println(b.size())
                    println(b.contains(Book("Dune", 9.9, true, 'A')))
                    println(b.lastIndexOf(Book("Dune", 9.9, true, 'A')))
                    var o = listOf(Outer(Inner(1)), Outer(Inner(2)))
                    println(o.size())
                    println(o.contains(Outer(Inner(2))))
                    println(o.indexOf(Outer(Inner(1))))
                }
                """);
        assertListFaceMatchesJvm(dir);
    }

    @Test
    void listScalarElemsMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // Fase E (27/10, #776): LIST fatia 6 — os elems escalares restantes:
        // Long (i64 nativo, slots diretos), Char (i64 nativo no WASI — o
        // rastro "char"; o print/strChar recebem i64) e Bool (i32 — estende
        // no slot e volta com wrap; as consultas estendem o valor antes do
        // bit-scan). O rastro do ARG de um literal Long e' "long" (medido) —
        // o check de largura aceita as tres trilhas i64-wide.
        var wasmtime = host("wasmtime");
        var wasmTools = host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var ls = listOf(1L, 2L)
                    ls.add(3000000000L)
                    ls.set(0, 7L)
                    println(ls.size())
                    println(ls.get(0))
                    println(ls.contains(3000000000L))
                    println(ls.indexOf(7L))
                    ls.remove(1)
                    println(ls.get(1))
                    var bs = listOf(true, false, true)
                    bs.set(1, true)
                    println(bs.get(1))
                    println(bs.contains(false))
                    bs.remove(0)
                    println(bs.get(0))
                    var cs = listOf('a', 'b')
                    cs.set(0, 'c')
                    println(cs.get(0))
                    println(cs.contains(99))
                }
                """);
        assertListFaceMatchesJvm(dir);
    }

    @Test
    void listNestedMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // Fase E (27/10, #776): LIST fatia 7 — listas ANINHADAS
        // (List<List<X>>): o handle da lista interna anda no slot i64
        // EXATAMENTE como o handle de String (extend no add/set, wrap no
        // get) e o rastro do get e' "list" — os encadeamentos (.size()/
        // .get() sobre o resultado) sao a maquinaria da LIST-1. Pego
        // rodando: o rastro do load de um LOCAL tipado List e' "List"
        // (capital — o typeName da classe), o check era case-sensitive.
        // contains/indexOf sobre aninhadas RECUSAM (a igualdade profunda
        // de listas e' uma face propria).
        var wasmtime = host("wasmtime");
        var wasmTools = host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var nested = listOf(listOf(1, 2), listOf(3, 4))
                    println(nested.size())
                    println(nested.get(0).size())
                    println(nested.get(1).get(0))
                    var row = listOf(5, 6)
                    var grid = listOf(row, listOf(7, 8))
                    grid.add(listOf(9, 10))
                    grid.set(0, listOf(1, 1))
                    println(grid.size())
                    println(grid.get(0).get(1))
                    println(grid.get(2).get(0))
                    var s = 0
                    var i = 0
                    while (i < grid.size()) {
                        s = s + grid.get(i).get(0)
                        i = i + 1
                    }
                    println(s)
                }
                """);
        assertListFaceMatchesJvm(dir);
    }

    @Test
    void mapBasicsMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // Fase E (27/10, #776): MAP fatia 1 — Map<String, Int> no WASI: o
        // put SOBRESCREVE pela chave (igualdade de CONTEUDO via o kof.strEq
        // existente) e DEVOLVE o valor anterior como caixa (o contrato do
        // HashMap.put — a forma void da cadeia mapOf dropa no despacho, a
        // forma declaracao deixa o KofPop consumir); o get de chave ausente
        // devolve o handle 0 e o boxToStr renderiza "null" (a paridade
        // String.valueOf(null)). Pego rodando: o END do bloco externo do
        // put (4 aberturas, 3 fechamentos) e a lei do br do match (o bloco
        // de fora, depth 3).
        var wasmtime = host("wasmtime");
        var wasmTools = host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var m = mapOf("a", 1, "b", 2)
                    m.put("a", 99)
                    println(m.get("a"))
                    println(m.get("zz"))
                    println(m.size())
                    m.put("c", 3)
                    println(m.size())
                    println(m.get("c"))
                    var k = "b"
                    println(m.get(k))
                }
                """);
        assertListFaceMatchesJvm(dir);
    }

    @Test
    void mapStringMembersMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // Fase E (27/10, #776): MAP fatia 2 — os membros de STRING:
        // Map.containsKey/remove (o remove devolve a CAIXA do valor removido
        // — 0=null, o KofPop da forma declaracao consome). A METADE SET
        // deste teste original saiu para o WasmWasiSetE2ETest (o ratchet do
        // test-hygiene ≤500 forcou o split da rodada 53).
        var wasmtime = host("wasmtime");
        var wasmTools = host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var m = mapOf("a", 1)
                    println(m.containsKey("a"))
                    println(m.containsKey("zz"))
                    m.remove("a")
                    println(m.size())
                    println(m.containsKey("a"))
                }
                """);
        assertListFaceMatchesJvm(dir);
    }

    @Test
    void listOfNonIntElementIsHonestRefusal(@TempDir Path dir) throws Exception {
        // Fase E (27/10, #776): a fatia so' admite elems Int — um add de
        // String recusa pelo nome (WASM002, sem artefato — Q7: nunca lista
        // silenciosa errada).
        var wasmtime = host("wasmtime");
        assumeTrue(wasmtime != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var xs = listOf(1)
                    var s = "x"
                    xs.add(s)
                }
                """);
        var wasi = new CompilerDriver().compile(dir.resolve("Main.kf"),
                dir.resolve("out-wasi"), dev.kof.compiler.Target.WASI);
        assertFalse(wasi.success(), "a fatia deve recusar elems nao-Int");
        assertTrue(wasi.diagnostics().getDiagnostics().toString().contains("List.add"),
                "a recusa nomeia o metodo: " + wasi.diagnostics().getDiagnostics());
    }

    @Test
    void listQueriesAndStringElemsMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // Fase E (27/10, #776): LIST fatia 2 — contains/indexOf/lastIndexOf/
        // isEmpty (as consultas Int) + List<String> elems (o handle i32 viaja
        // zero-extendido no slot i64 e volta wrap no get, pelo type argument
        // do OWNER). Os desugares das consultas trazem um 0 FANTASMA no topo
        // (o param 0 = o receiver tipado, medido no IR) — o despacho o dropa.
        // O scan de busca e' DOIS blocos: as saidas de fronteira caem no -1
        // (dentro do bloco externo) e o br do MATCH mira o bloco externo
        // (preserva o indice — um bloco so' fazia o match cair no -1).
        var wasmtime = host("wasmtime");
        var wasmTools = host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var xs = listOf(1, 2, 3)
                    println(xs.contains(2))
                    println(xs.contains(9))
                    println(xs.indexOf(2))
                    println(xs.indexOf(9))
                    println(xs.lastIndexOf(3))
                    println(xs.lastIndexOf(9))
                    println(xs.isEmpty())
                    var ys = listOf(1)
                    println(ys.isEmpty())
                    var ss = listOf("a", "b", "c")
                    println(ss.size())
                    println(ss.get(1))
                    ss.add("d")
                    println(ss.get(3))
                    var i = 0
                    while (i < ss.size()) {
                        println(ss.get(i))
                        i = i + 1
                    }
                }
                """);
        assertListFaceMatchesJvm(dir);
    }

    @Test
    void mapKeysValuesMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // Fase E (27/10, #776): MAP fatia 3 — a ITERACAO: m.keys()/
        // m.values() devolvem a LISTA nova (a celula 24B provada, cap=len,
        // SEM grow — o tamanho final e' conhecido; o mapa vazio da
        // heapAlloc(0), um bump valido). O rastro "list" entrega o
        // resultado a toda a maquinaria da LISTA (size/get/contains/
        // for-in). A ORDEM nao e' o contrato (o HashMap nao a expoe — a
        // celula WASI e' de insercao): as assercoes sao livres de ordem +
        // o get de par UNICO. Pego rodando: a lista de locals do extract
        // tinha len/cell TROCADOS (slot2=I32/slot3=I64 — o validator
        // apontava o call com "expected i32, found i64"; o bisect
        // byte-a-byte do binario fixou o mapa).
        var wasmtime = host("wasmtime");
        var wasmTools = host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var m = mapOf("a", 1, "b", 2, "c", 3)
                    var ks = m.keys()
                    println(ks.size())
                    println(ks.contains("b"))
                    var vs = m.values()
                    println(vs.size())
                    println(vs.contains(3))
                    var m2 = mapOf("k", 42)
                    println(m2.keys().get(0))
                    println(m2.values().get(0))
                    var e = mapOf()
                    println(e.keys().size())
                    println(e.values().size())
                }
                """);
        assertListFaceMatchesJvm(dir);
    }
}
