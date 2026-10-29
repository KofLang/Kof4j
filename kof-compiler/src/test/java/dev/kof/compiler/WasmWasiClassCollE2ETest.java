package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Fase E (29/10, #776 — split test-hygiene): as faces CLASS-2 de COLEÇÕES
 * com elementos de classe — List<Animal> (o slot do handle i32 do String,
 * a lei da largura do resultado do remove), os valores de classe no Map
 * (o CRU do String + o local nullable-CLASS + o typeName/unwrapNullable)
 * e Set<Animal> (a rota DIRECT-I64 = a identidade = a paridade do
 * default equals). Os helpers são os da classe irmã, CHAMADA QUALIFICADA
 * (o ratchet bane nomes duplicados).
 */
class WasmWasiClassCollE2ETest {

    @Test
    void listOfClassesMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        // CLASS-2g fatia A (29/10, #776): List<Animal> — os ELEMENTOS de
        // classe nas fatias de lista (o MESMO slot do handle i32 do String,
        // o extend no slot i64): o add/get/set/remove-statement/clear, o
        // despacho sobre o elemento (a rota ARMADA — o hook vê o retorno
        // ClassType do list get), o acesso de campo do elemento. A LEI DA
        // LARGURA DO RESULTADO do remove (o slot devolve i64 — o ref = o
        // wrap i32 + o rastro do elem; o bool = o wrap; o double = o
        // reinterpret). O remove COM O RESULTADO USADO = o buraco do
        // ORÁCULO JVM: o return Object (erasure) SEM o CHECKCAST = o
        // VerifyError disfarçado da mensagem FX = a lacuna do compilador
        // core (JVM backend), NÃO desta lane — registrada, a sonda usa as
        // formas do diferencial válido.
        var wasmtime = WasmWasiListE2ETest.host("wasmtime");
        var wasmTools = WasmWasiListE2ETest.host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                class Animal {
                    String name

                    constructor(nm: String) {
                        this.name = nm
                    }

                    speak(): String {
                        return "animal " + this.name
                    }
                }
                class Dog extends Animal {
                    constructor(nm: String) {
                        super(nm)
                    }

                    speak(): String {
                        return "dog " + this.name
                    }
                }
                class Cat extends Animal {
                    constructor(nm: String) {
                        super(nm)
                    }

                    speak(): String {
                        return "cat " + this.name
                    }
                }
                main(String[] args) {
                    var l = listOf(Dog("Rex"), Cat("Mimi"))
                    println(l.size())
                    println(l.get(0).speak())
                    println(l.get(1).speak())
                    l.set(1, Dog("Bob"))
                    println(l.get(1).speak())
                    println(l.get(0).name)
                    l.remove(0)
                    println(l.size())
                    l.clear()
                    println(l.size())
                }
                """);
        WasmWasiListE2ETest.assertListFaceMatchesJvm(dir);
    }

    @Test
    void mapClassValuesMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // CLASS-2h fatia A (29/10, #776): os VALORES de classe no Map — o
        // get/put = os MESMOS runtimes CRU do String (o handle i32 no slot,
        // ZERO runtime novo), o local nullable-CLASS = o handle i32 (o
        // null = o handle 0, a lei do zero-fill), o rastro do get = o NOME
        // da classe (a lei do typeName: o NullableType com o inner classe =
        // o nome — o rastro do load = o lixo do toString, medido) e o walk
        // do despacho = o unwrapNullable (o local route não via o
        // NullableType = a recusa "produtor fora do subset", medido). A
        // sonda usa Map<String, Dog> com a SUBCLASSE Puppy (os genéricos do
        // Kof = INVARIANTES, SEM021: Map<String, Dog> ≠ Map<String,
        // Animal>; o Cat no Map<String, Dog> = o SEM gap registrado).
        var wasmtime = WasmWasiListE2ETest.host("wasmtime");
        var wasmTools = WasmWasiListE2ETest.host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                class Animal {
                    String name

                    constructor(nm: String) {
                        this.name = nm
                    }

                    speak(): String {
                        return "animal " + this.name
                    }
                }
                class Dog extends Animal {
                    constructor(nm: String) {
                        super(nm)
                    }

                    speak(): String {
                        return "dog " + this.name
                    }
                }
                class Puppy extends Dog {
                    constructor(nm: String) {
                        super(nm)
                    }

                    speak(): String {
                        return "puppy " + this.name
                    }
                }
                main(String[] args) {
                    var m = mapOf("a", Dog("Rex"), "b", Puppy("Bit"))
                    println(m.size())
                    var v = m.get("a")
                    if (v != null) {
                        println(v.speak())
                        println(v.name)
                    }
                    var w = m.get("b")
                    if (w != null) {
                        println(w.speak())
                    }
                    m.put("c", Dog("Bob"))
                    var y = m.get("c")
                    if (y != null) {
                        println(y.speak())
                    }
                    println(m.size())
                }
                """);
        WasmWasiListE2ETest.assertListFaceMatchesJvm(dir);
    }

    @Test
    void setOfClassesMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        // CLASS-2i (29/10, #776): Set<Animal> — os ELEMENTOS de classe nas
        // fatias de set = a rota DIRECT-I64 (o handle i32 estendido no slot
        // i64 — a MESMA disciplina das classes da lista/mapa; os MESMOS
        // runtimes setAdd/setContains/setRemove, ZERO código novo). A
        // SEMÂNTICA = a IDENTIDADE (o contains/remove = o i64.eq dos
        // handles) = a paridade do default-equals do JVM (o contrato do
        // Set por tipo: o String = o content-eq via o strEq, a classe = a
        // identidade — contains(Dog("X")) = false medido).
        var wasmtime = WasmWasiListE2ETest.host("wasmtime");
        var wasmTools = WasmWasiListE2ETest.host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                class Dog {
                    String name

                    constructor(nm: String) {
                        this.name = nm
                    }

                    speak(): String {
                        return "dog " + this.name
                    }
                }
                class Puppy extends Dog {
                    constructor(nm: String) {
                        super(nm)
                    }

                    speak(): String {
                        return "puppy " + this.name
                    }
                }
                main(String[] args) {
                    var d = Dog("Rex")
                    var p = Puppy("Bit")
                    var s = setOf(d, p)
                    println(s.size())
                    println(s.contains(d))
                    println(s.contains(p))
                    println(s.contains(Dog("X")))
                    println(s.contains(Dog("Rex")))
                    s.remove(p)
                    println(s.size())
                }
                """);
        WasmWasiListE2ETest.assertListFaceMatchesJvm(dir);
    }
}
