package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Fase E (29/10, #776 — split test-hygiene): as faces CLASS-2 de
 * ARGS/PROVENÂNCIA — os calls de SUPER-MÉTODO (o call direto puro — o
 * receiver já está na pilha), o DESPACHO ANINHADO como arg (a lei do
 * arming pelo walk + o segundo scratch de tag + o resultType de classe)
 * e as interações null (o fix do popsOf do load de campo). Os helpers
 * são os da classe irmã, CHAMADA QUALIFICADA (o ratchet bane nomes
 * duplicados).
 */
class WasmWasiClassArgsE2ETest {

    @Test
    void superMethodCallsMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // CLASS-2c fatia B (29/10, #776): os CALLS de SUPER-MÉTODO
        // (`super.speak()`, `super.meet(other)`) = o call DIRETO (a
        // semântica do JVM: a implementação do super, NUNCA o despacho
        // virtual). O receiver JÁ ESTÁ na pilha (o desugar empilha o
        // LoadLocal(this) PRIMEIRO — a ordem padrão dos métodos; o get
        // duplicado trocava os operandos do call, medido em P7). O fecho
        // resolve o SUPER pelo dono QUALIFICADO (o lookup sem qualificação
        // não tem os métodos de classe — "chamada 'speak' nao resolvida").
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

                    meet(other: Animal): String {
                        return this.name + " meets " + other.name
                    }
                }
                class Dog extends Animal {
                    constructor(nm: String) {
                        super(nm)
                    }

                    speak(): String {
                        return "dog > " + super.speak()
                    }

                    meet(other: Animal): String {
                        return super.meet(other) + " (woof)"
                    }
                }
                class Cat extends Animal {
                    constructor(nm: String) {
                        super(nm)
                    }

                    speak(): String {
                        return "cat > " + super.speak()
                    }

                    meet(other: Animal): String {
                        return super.meet(other) + " (meow)"
                    }
                }
                main(String[] args) {
                    var a: Animal = Dog("Rex")
                    println(a.speak())
                    var b: Animal = Cat("Mimi")
                    println(a.meet(b))
                    println(b.speak())
                    println(b.meet(a))
                }
                """);
        WasmWasiListE2ETest.assertListFaceMatchesJvm(dir);
    }

    @Test
    void classArgsOnArmedReceiversMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // CLASS-2d fatia A (29/10, #776): os OPS de classe ANINHADOS como
        // ARGS — o tee dos produtores = GATED pelo lookahead (só o receiver
        // do próximo despacho do bloco faz tee; os args NÃO escrevem o
        // scratch clsIdx) e a TAG = ARMADA no produtor ANTES da avaliação
        // dos args (os receivers de alloc/call não são re-legíveis); o
        // despacho armado lê a tag pronta (o scan do intervalo = os calls
        // de despacho apenas — um despacho aninhado como arg continua a
        // recusa honesta: o load de tag dele sobrescreve o tagIdx
        // compartilhado, fatia CLASS-2e registrada).
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

                    meet(other: Animal): String {
                        return this.name + " meets " + other.name
                    }
                }
                class Dog extends Animal {
                    constructor(nm: String) {
                        super(nm)
                    }

                    speak(): String {
                        return "dog > " + super.speak()
                    }

                    meet(other: Animal): String {
                        return super.meet(other) + " (woof)"
                    }
                }
                class Cat extends Animal {
                    constructor(nm: String) {
                        super(nm)
                    }

                    speak(): String {
                        return "cat > " + super.speak()
                    }

                    meet(other: Animal): String {
                        return super.meet(other) + " (meow)"
                    }
                }
                class Holder {
                    Animal a

                    constructor(x: Animal) {
                        this.a = x
                    }
                }
                pick(): Animal {
                    return Cat("Mimi")
                }
                give(): Animal {
                    return Dog("Bob")
                }
                main(String[] args) {
                    var b: Animal = Dog("Rex")
                    println(pick().meet(Cat("Mimi")))
                    println(pick().meet(give()))
                    var h = Holder(b)
                    println(pick().meet(h.a))
                    println(b.meet(pick()))
                }
                """);
        WasmWasiListE2ETest.assertListFaceMatchesJvm(dir);
    }

    @Test
    void nestedDispatchAsArgMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        // CLASS-2e (29/10, #776): o DESPACHO ANINHADO como arg — A LEI DO
        // ARMING (o hook arma o PRIMEIRO despacho cujo walk backward
        // resolve para ESTE produtor — o rascunho checava só o primeiro
        // despacho, que podia ser o ARG: pick().meet(b.speak()) armava o
        // speak e o meet lia o clsIdx OBSOLETO = o despacho errado = o
        // trap, medido) + O SEGUNDO SCRATCH DE TAG (o despacho aninhado
        // como arg carrega a tag no tagIdx2 e a cadeia lê o tagIdx2 — a
        // tag armada do externo no tagIdx sobrevive; a profundidade
        // limita a um nível — CLASS-2f = dois níveis = a recusa honesta)
        // + O RETORNO DE CLASSE do despacho = o handle i32 (o resultType
        // default 0x40 fazia a cadeia do mate(): Animal sair void —
        // "values remaining on stack", medido). A sonda String→Animal
        // ORIGINAL = o programa Kof INVÁLIDO que o compilador aceita (o
        // oráculo JVM = o VerifyError disfarçado da mensagem FX; a lacuna
        // semântica pré-existente = registrada, não desta lane).
        var wasmtime = WasmWasiListE2ETest.host("wasmtime");
        var wasmTools = WasmWasiListE2ETest.host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                class Animal {
                    String name

                    constructor(nm: String) {
                        this.name = nm
                    }

                    mate(other: Animal): Animal {
                        return other
                    }

                    meet(other: Animal): String {
                        return this.name + " meets " + other.name
                    }
                }
                class Dog extends Animal {
                    constructor(nm: String) {
                        super(nm)
                    }

                    mate(other: Animal): Animal {
                        return this
                    }

                    meet(other: Animal): String {
                        return "dog-" + this.name + " meets " + other.name
                    }
                }
                class Cat extends Animal {
                    constructor(nm: String) {
                        super(nm)
                    }

                    mate(other: Animal): Animal {
                        return other
                    }

                    meet(other: Animal): String {
                        return "cat-" + this.name + " meets " + other.name
                    }
                }
                pick(): Animal {
                    return Cat("Mimi")
                }
                main(String[] args) {
                    var b: Animal = Dog("Rex")
                    var c: Animal = Cat("Mimi")
                    println(pick().meet(b.mate(c)))
                    println(b.meet(pick().mate(c)))
                    println(pick().mate(c).meet(b))
                }
                """);
        WasmWasiListE2ETest.assertListFaceMatchesJvm(dir);
    }

    @Test
    void classNullInteractionsMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // CLASS-2f (29/10, #776): as interações null/Object nos valores de
        // classe — as comparações ==/!= contra o null literal (o i32.eq/ne
        // nos handles, o ramo ClassType do binário), o narrowing do campo
        // (`if (o.pet != null)`) e O FIX DO MODELO DA SIMULAÇÃO: o popsOf
        // tratava o LOAD-DE-CAMPO como push puro mas a emissão = o
        // net-zero (o pop 1 + o push 1) — o walk do despacho devolvia o
        // SUB-RECEIVER do arg (o owner) e a tag = a tag do OWNER = o
        // despacho errado ("cat Rex meets Rex", medido em P10).
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

                    meet(other: Animal): String {
                        return this.name + " meets " + other.name
                    }
                }
                class Dog extends Animal {
                    constructor(nm: String) {
                        super(nm)
                    }

                    speak(): String {
                        return "dog " + this.name
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
                        return "cat " + this.name
                    }

                    meet(other: Animal): String {
                        return "cat " + this.name + " meets " + other.name
                    }
                }
                class Owner {
                    Animal pet

                    constructor(x: Animal) {
                        this.pet = x
                    }
                }
                main(String[] args) {
                    var a: Animal = Dog("Rex")
                    println(a == null)
                    println(a != null)
                    var o = Owner(a)
                    println(o.pet == null)
                    println(o.pet != null)
                    if (a != null) {
                        println(a.speak())
                    }
                    if (o.pet != null) {
                        println(o.pet.speak())
                    }
                    println(a.meet(o.pet))
                }
                """);
        WasmWasiListE2ETest.assertListFaceMatchesJvm(dir);
    }

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
}
