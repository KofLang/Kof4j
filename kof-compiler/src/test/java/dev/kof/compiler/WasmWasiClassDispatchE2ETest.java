package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Fase E (29/10, #776 — split test-hygiene): as faces CLASS-2 do WASI — o
 * DESPACHO VIRTUAL e a família de args/provenância (o fatia A-E): o block
 * type multi-valor por typeidx, a provenância da tag pelo walk (o
 * LoadLocal/o alloc/o campo/o retorno), os tees GATED pelo lookahead, a
 * tag ARMADA no produtor, o segundo scratch de tag para o despacho
 * aninhado, os calls de super-método e as interações null (o fix do
 * popsOf do load de campo). Os helpers são os da classe irmã, CHAMADA
 * QUALIFICADA (o ratchet bane nomes duplicados).
 */
class WasmWasiClassDispatchE2ETest {

    @Test
    void polymorphicDispatchMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        // CLASS-2 (28/10, #776): o DESPACHO VIRTUAL — a TAG de classe no
        // offset 0 (o primeiro 4B do header, os campos começam em 16) e a
        // cadeia if MULTI-VALOR por typeidx (os params = [receiver, args...]
        // — a lei do if: os valores ABAIXO do label sobrevivem ao end, o
        // braço só os consome como params declarados). A provenança do
        // receiver = o LoadLocal (o local.get relê o valor vivo) ou o
        // alloc/init (o scratch clsIdx). A reatribuição troca a TAG — o
        // JVM despacha Dog e Cat sobre o MESMO local tipado Animal.
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
                        return "animal"
                    }

                    feed(times: Int): String {
                        return "animal ate " + times
                    }
                }
                class Dog extends Animal {
                    constructor(nm: String) {
                        super(nm)
                    }

                    speak(): String {
                        return "dog"
                    }

                    feed(times: Int): String {
                        return "dog ate " + times
                    }
                }
                class Cat extends Animal {
                    constructor(nm: String) {
                        super(nm)
                    }

                    speak(): String {
                        return "cat"
                    }

                    feed(times: Int): String {
                        return "cat ate " + times
                    }
                }
                main(String[] args) {
                    var a: Animal = Dog("Rex")
                    println(a.speak())
                    println(a.feed(3))
                    println(Dog("Bob").speak())
                    a = Cat("Mimi")
                    println(a.speak())
                    println(a.feed(7))
                }
                """);
        WasmWasiListE2ETest.assertListFaceMatchesJvm(dir);
    }

    @Test
    void classFieldAndCallResultReceiversMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // CLASS-2b (29/10, #776): os CAMPOS de classe (o handle i32 no slot
        // de 8B — o zero-fill = 0 = null), o RECEIVER DE CAMPO no despacho
        // (o tee da leitura no scratch clsIdx — a provenança) e o RECEIVER
        // DE RETORNO DE CHAMADA (a função top-level de assinatura de
        // classe entra no fecho; o tee do resultado no clsIdx; a func NÃO
        // sai void — o defeito da 56 corrigido só para o String, medido).
        // O arg de CLASSE na chamada de despacho = o handle i32 nos block
        // params (a simulação de provenância lê o local-fonte VIVO — à
        // prova do clobber dos tees dos args).
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
                        return "animal"
                    }

                    meet(other: Animal): String {
                        return this.name + " meets " + other.name
                    }
                }
                class Dog extends Animal {
                    constructor(nm: String) {
                        super(nm)
                    }

                    speak(): String {
                        return "dog"
                    }

                    meet(other: Animal): String {
                        return "dog " + this.name + " meets " + other.name
                    }
                }
                class Cat extends Animal {
                    constructor(nm: String) {
                        super(nm)
                    }

                    speak(): String {
                        return "cat"
                    }

                    meet(other: Animal): String {
                        return "cat " + this.name + " meets " + other.name
                    }
                }
                class Owner {
                    Animal pet

                    constructor(a: Animal) {
                        this.pet = a
                    }

                    voice(): String {
                        return this.pet.speak()
                    }
                }
                pick(): Animal {
                    return Cat("Mimi")
                }
                main(String[] args) {
                    var o = Owner(Dog("Rex"))
                    println(o.pet.speak())
                    o.pet = Cat("Mimi")
                    println(o.pet.speak())
                    println(o.voice())
                    var b: Animal = pick()
                    println(b.speak())
                    println(pick().speak())
                    var a: Animal = Dog("Rex")
                    println(a.meet(b))
                }
                """);
        WasmWasiListE2ETest.assertListFaceMatchesJvm(dir);
    }

    @Test
    void classArgsOnScratchReceiversMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // CLASS-2c fatia A (29/10, #776): os ARGS de classe LOCAIS nos
        // receivers de scratch — o LoadLocal de classe NÃO faz mais tee no
        // clsIdx (o save era peso morto e os tees dos args clobberavam o
        // save do receiver entre o produtor e o call, medido em P6: o
        // despacho sobre pick()/o campo/o alloc com o arg local recusava);
        // o scan do intervalo aperta para os PRODUTORES-DE-TEE. A LEI DO
        // SUPER (o Q0 pegado pela sonda): o call super(args) no corpo do
        // ctor lia o receiver do scratch clsIdx — que só o tee do
        // LoadLocal(this) inicializava; sem o tee, o super gravava os
        // campos no handle NULL (os nomes vazios, medido). O site do super
        // agora lê o THIS do ctor (o param do slot 0 — o Ctx carrega o
        // fname QUALIFICADO, a checagem do site dispara).
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
                        return "animal"
                    }

                    meet(other: Animal): String {
                        return this.name + " meets " + other.name
                    }
                }
                class Dog extends Animal {
                    constructor(nm: String) {
                        super(nm)
                    }

                    speak(): String {
                        return "dog"
                    }

                    meet(other: Animal): String {
                        return "dog " + this.name + " meets " + other.name
                    }
                }
                class Cat extends Animal {
                    constructor(nm: String) {
                        super(nm)
                    }

                    speak(): String {
                        return "cat"
                    }

                    meet(other: Animal): String {
                        return "cat " + this.name + " meets " + other.name
                    }
                }
                class Owner {
                    Animal pet

                    constructor() {
                    }
                }
                pick(): Animal {
                    return Cat("Mimi")
                }
                main(String[] args) {
                    var b: Animal = Dog("Rex")
                    println(pick().meet(b))
                    println(Dog("Rex").meet(b))
                    var o = Owner()
                    o.pet = pick()
                    println(o.pet.meet(b))
                }
                """);
        WasmWasiListE2ETest.assertListFaceMatchesJvm(dir);
    }
}
