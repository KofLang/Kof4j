package dev.kof.compiler;

import dev.kof.compiler.lang.PortuKofMethodAliases;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-PORTUKOF unidade 3 (07/10) — paridade COMPLETA de metodos/campos de
 * superficie tipada: cada nome canonico dos dispatchers reais tem um alias
 * pt-BR e o alias resolve pelo TIPO REAL do receiver (mesmo output no alvo
 * script, sem runtime paralelo). Prova central: `tamanho` = `length` em
 * String/Array e `size` em colecoes no MESMO programa — a ambiguidade que
 * qualquer alias AST-only teria.
 */
class PortuKofMethodsE2ETest {

    private static String run(String src, String fileName) throws Exception {
        return runAt(src, fileName, Files.createTempDirectory("ptkf-methods-"));
    }

    private static String runAt(String src, String fileName, Path root) throws Exception {
        Path f = root.resolve(fileName);
        Files.writeString(f, src);
        KofInterpreter.Result r = new CompilerDriver().interpret(List.of(f), root, new String[0]);
        assertEquals(0, r.exitCode(), "saida esperada; stderr=[" + r.stderr() + "] src=[" + src + "]");
        return r.stdout();
    }

    private static String pt(String body) {
        return "principal() {\n" + body + "}\n";
    }

    private static String kf(String body) {
        return "main() {\n" + body + "}\n";
    }

    @Test
    void tamanhoResolvesByReceiverTypeInTheSameProgram() throws Exception {
        String kof = kf("    var s = \"ola mundo\"\n"
                + "    var l = listOf(1, 2, 3)\n"
                + "    var m = mapOf(\"a\", 1)\n"
                + "    var st = setOf(4, 5)\n"
                + "    println(s.length())\n"
                + "    println(l.size())\n"
                + "    println(m.size())\n"
                + "    println(st.size())\n");
        String ptkf = pt("    var s = \"ola mundo\"\n"
                + "    var l = listOf(1, 2, 3)\n"
                + "    var m = mapOf(\"a\", 1)\n"
                + "    var st = setOf(4, 5)\n"
                + "    println(s.tamanho())\n"
                + "    println(l.tamanho())\n"
                + "    println(m.tamanho())\n"
                + "    println(st.tamanho())\n");
        assertEquals(run(kof, "Main.kf"), run(ptkf, "Main.ptkf"),
                "tamanho = length(String) e size(colecoes) no mesmo programa");
        assertEquals("9\n3\n1\n2\n", run(ptkf, "Main.ptkf"), "valores reais");
    }

    @Test
    void stringMethodAliasesProduceSameOutput() throws Exception {
        String kof = kf("    var s = \"Kof lang\"\n"
                + "    println(s.contains(\"lang\"))\n"
                + "    println(s.startsWith(\"Kof\"))\n"
                + "    println(s.indexOf(\"lang\"))\n"
                + "    println(s.toUpperCase())\n"
                + "    println(s.replace(\"Kof\", \"PortuKof\"))\n"
                + "    println(s.split(\" \").length)\n"
                + "    println(\"42\".toInt() + 1)\n"
                + "    println(s.substring(4))\n"
                + "    println(s.isEmpty())\n");
        String ptkf = pt("    var s = \"Kof lang\"\n"
                + "    println(s.contem(\"lang\"))\n"
                + "    println(s.comecaCom(\"Kof\"))\n"
                + "    println(s.indiceDe(\"lang\"))\n"
                + "    println(s.paraMaiusculas())\n"
                + "    println(s.substituir(\"Kof\", \"PortuKof\"))\n"
                + "    println(s.dividir(\" \").comprimento)\n"
                + "    println(\"42\".paraInt() + 1)\n"
                + "    println(s.subcadena(4))\n"
                + "    println(s.estaVazio())\n");
        assertEquals(run(kof, "Main.kf"), run(ptkf, "Main.ptkf"),
                "metodo String: alias == canonico em saida");
    }

    @Test
    void collectionFunctionAliasesProduceSameOutput() throws Exception {
        String kof = kf("    var l = listOf(1, 2, 3, 4)\n"
                + "    println(l.filter((x: Int) -> x > 2))\n"
                + "    println(l.map((x: Int) -> x * 2))\n"
                + "    println(l.reduce((a: Int, b: Int) -> a + b, 0))\n"
                + "    println(l.any((x: Int) -> x > 3))\n"
                + "    println(l.find((x: Int) -> x > 2))\n"
                + "    var m = mapOf(\"k\", 9)\n"
                + "    println(m.get(\"k\"))\n"
                + "    println(m.keys())\n");
        String ptkf = pt("    var l = listOf(1, 2, 3, 4)\n"
                + "    println(l.filtrar((x: Int) -> x > 2))\n"
                + "    println(l.mapear((x: Int) -> x * 2))\n"
                + "    println(l.reduzir((a: Int, b: Int) -> a + b, 0))\n"
                + "    println(l.algum((x: Int) -> x > 3))\n"
                + "    println(l.achar((x: Int) -> x > 2))\n"
                + "    var m = mapOf(\"k\", 9)\n"
                + "    println(m.obter(\"k\"))\n"
                + "    println(m.chaves())\n");
        assertEquals(run(kof, "Main.kf"), run(ptkf, "Main.ptkf"),
                "funcoes funcionais de colecao: alias == canonico em saida");
    }

    @Test
    void primitiveEnumAndFieldAliasesProduceSameOutput() throws Exception {
        String kof = kf("    var n = 42\n"
                + "    println(n.toString())\n"
                + "    println(Cor.Vermelho.name())\n"
                + "    println(Cor.Vermelho.ordinal())\n");
        String ptkf = pt("    var n = 42\n"
                + "    println(n.paraTexto())\n"
                + "    println(Cor.Vermelho.nome())\n"
                + "    println(Cor.Vermelho.ordem())\n");
        String enums = "enum Cor {\n    Vermelho, Azul\n}\n";
        String enumsPt = "enumeracao Cor {\n    Vermelho, Azul\n}\n";
        assertEquals(run(enums + kof, "Main.kf"), run(enumsPt + ptkf, "Main.ptkf"),
                "primitivo/enum/field: alias == canonico em saida");
    }

    @Test
    void userClassMethodsAreNeverRewritten() throws Exception {
        // PARTE 5: `contem` PROPRIO do usuario permanece `contem` — o splice so
        // conhece tipos da superficie; a classe do usuario e categoria null.
        String kof = "class Caixa {\n"
                + "    Bool contem(Int n) {\n"
                + "        return n > 0\n"
                + "    }\n"
                + "}\n"
                + kf("    var c = new Caixa()\n"
                + "    println(c.contem(5))\n"
                + "    println(c.contem(0))\n");
        String ptkf = "classe Caixa {\n"
                + "    Bool contem(Int n) {\n"
                + "        retorna n > 0\n"
                + "    }\n"
                + "}\n"
                + pt("    var c = novo Caixa()\n"
                + "    println(c.contem(5))\n"
                + "    println(c.contem(0))\n");
        assertEquals(run(kof, "Main.kf"), run(ptkf, "Main.ptkf"),
                "metodo de usuario `contem` NAO vira `contains`");
    }

    @Test
    void ioMethodAliasesResolveToCanonicalSymbols() throws Exception {
        // Face File/Path: `existe`/`lerTexto`/`escreverTexto` -> exists/readText/
        // writeText. Caminho ABSOLUTO dentro do tmpdir — nunca polui o repo.
        Path root = Files.createTempDirectory("ptkf-io-");
        String target = root.resolve("alvo.txt").toString().replace("\\", "/");
        String kof = kf("    var f = File(\"" + target + "\")\n"
                + "    f.writeText(\"oi\")\n"
                + "    println(f.exists())\n"
                + "    println(f.readText())\n");
        String ptkf = pt("    var f = File(\"" + target + "\")\n"
                + "    f.escreverTexto(\"oi\")\n"
                + "    println(f.existe())\n"
                + "    println(f.lerTexto())\n");
        String outK = runAt(kof, "Main.kf", root);
        String outP = runAt(ptkf, "Main.ptkf", root);
        assertEquals(outK, outP, "IO: alias == canonico em saida");
        assertTrue(outK.contains("oi"), "gravou/leu de verdade: " + outK);
    }

    @Test
    void theGeneratedMethodTableIsTotalBijectiveAndAscii() {
        Map<String, Map<String, String>> alias = PortuKofMethodAliases.aliasByCategory();
        Map<String, List<String>> canon = PortuKofMethodAliases.canonicalsByCategory();
        assertFalse(alias.isEmpty(), "tabela gerada dos dispatchers reais — nunca vazia");
        for (String cat : canon.keySet()) {
            Map<String, String> a = alias.get(cat);
            List<String> c = canon.get(cat);
            assertFalse(c.isEmpty(), "categoria vazia: " + cat);
            assertEquals(new HashSet<>(c).size(), c.size(), "canonicos unicos: " + cat);
            for (Map.Entry<String, String> e : a.entrySet()) {
                assertTrue(c.contains(e.getValue()), "alias orfao " + cat + ":" + e.getKey());
                assertTrue(e.getKey().matches("[a-z][A-Za-z0-9]*"),
                        "alias sem acento/ASCII: " + cat + ":" + e.getKey());
                assertEquals(e.getValue(), PortuKofMethodAliases.canonicalize(cat, e.getKey()),
                        "alias resolve ao canonico em " + cat);
            }
            for (String cn : c) {
                assertTrue(a.containsValue(cn), "sem alias p/ " + cat + "." + cn);
            }
        }
        assertTrue(canon.get("STRING").contains("contains"), "STRING.contains");
        assertTrue(canon.get("LIST").contains("filter"), "LIST.filter");
        assertTrue(canon.get("MAP").contains("keys"), "MAP.keys");
        assertTrue(canon.get("IO").contains("readText"), "IO.readText");
        assertTrue(canon.get("ENUM").contains("ordinal"), "ENUM.ordinal");
        assertTrue(canon.get("PRIMITIVE").contains("toString"), "PRIMITIVE.toString");
        // regra de ouro do splice receiver-aware: tamanho -> length em String,
        // tamanho -> size em colecoes (ambiguidade impossivel em alias AST-only)
        assertEquals("length", PortuKofMethodAliases.canonicalize("STRING", "tamanho"),
                "String: tamanho = length (kof_string_length existe)");
        assertEquals("size", PortuKofMethodAliases.canonicalize("LIST", "tamanho"));
        assertEquals("size", PortuKofMethodAliases.canonicalize("MAP", "tamanho"));
        assertEquals("size", PortuKofMethodAliases.canonicalize("SET", "tamanho"));
    }

    @Test
    void canonicalNamesPassThroughUntouched() {
        for (Map.Entry<String, List<String>> e
                : PortuKofMethodAliases.canonicalsByCategory().entrySet()) {
            for (String c : e.getValue()) {
                assertEquals(c, PortuKofMethodAliases.canonicalize(e.getKey(), c),
                        "nome canonico devolve a si mesmo (identidade) em " + e.getKey());
            }
        }
    }
}
