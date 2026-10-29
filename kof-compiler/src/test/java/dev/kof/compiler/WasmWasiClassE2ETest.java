package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Fase E (28/10, #776 — split test-hygiene): as faces CLASS-1 do WASI — as
 * CLASSES mutáveis (o plano §12): o ctor como func com o receiver (o this =
 * o ÚLTIMO param do ctor — a mecânica do alloc: os args se empilham antes e
 * o get do scratch clsIdx empilha o receiver por cima; os MÉTODOS = o
 * receiver PRIMEIRO, a ordem do IR), o ZERO-FILL dos campos (o bump não é
 * zerado — a paridade 0/null do JVM), o store de campo (a op nova
 * KofStoreField + o scratch clsIdx DEDICADO — o objIdx = o scratch do alloc
 * do ctor: reusá-lo corrompia o objeto em construção) e o Call DIRETO (sem
 * herança/despacho — a escolha de implementação do §12). Os helpers são os
 * da classe irmã, CHAMADA QUALIFICADA (o ratchet bane nomes duplicados).
 */
class WasmWasiClassE2ETest {

    @Test
    void classFieldsMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // CLASS-1 fatia A (28/10, #776): os campos — o ctor (o corpo = os
        // stores), as leituras/escritas (o store pela largura: o i64/o i32/
        // o f64), a aritmética entre campos e o zero-fill (o campo não
        // atribuído no ctor = 0/null como o JVM).
        var wasmtime = WasmWasiListE2ETest.host("wasmtime");
        var wasmTools = WasmWasiListE2ETest.host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                class P {
                    Int a
                    Int b

                    constructor() {
                        this.a = 5
                        this.b = 7
                    }
                }
                main(String[] args) {
                    var p = P()
                    println(p.a)
                    println(p.b)
                    p.a = 11
                    println(p.a)
                    p.b = p.a + 2
                    println(p.b)
                    var r = Rec()
                    println(r.label)
                    println(r.count)
                    println(r.v)
                    r.v = 2.5
                    println(r.v)
                    r.label = "ok"
                    println(r.label)
                }
                class Rec {
                    String label
                    Int count
                    Double v

                    constructor() {
                        this.count = 3
                    }
                }
                """);
        WasmWasiListE2ETest.assertListFaceMatchesJvm(dir);
    }

    @Test
    void classMethodsMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // CLASS-1 fatia B (28/10, #776): os MÉTODOS de instância — o Call
        // DIRETO (sem herança/despacho: a escolha de implementação do §12,
        // o comportamento observável = o contrato), o receiver = o param 0
        // (a ordem do IR), os params escalares, o retorno Int/String (o
        // results do primitive ignorava a ClassType e a func saía void: o
        // call site esperava o handle — medido), e as instâncias
        // independentes (o estado por handle).
        var wasmtime = WasmWasiListE2ETest.host("wasmtime");
        var wasmTools = WasmWasiListE2ETest.host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                class Counter {
                    Int n

                    constructor() {
                        this.n = 0
                    }

                    inc(): Int {
                        this.n = this.n + 1
                        return this.n
                    }

                    add(x: Int): Int {
                        this.n = this.n + x
                        return this.n
                    }
                }
                class User {
                    String name
                    Double h
                    Int age

                    constructor(nm: String, hh: Double, a: Int) {
                        this.name = nm
                        this.h = hh
                        this.age = a
                    }

                    greet(): String {
                        return "Hi " + this.name
                    }
                }
                class Point {
                    Int x
                    Int y

                    constructor() {
                        this.x = 1
                        this.y = 2
                    }

                    sum(): Int {
                        return this.x + this.y
                    }
                }
                main(String[] args) {
                    var c = Counter()
                    println(c.inc())
                    println(c.inc())
                    println(c.add(10))
                    println(c.n)
                    var d = Counter()
                    println(d.inc())
                    println(c.n)
                    println(d.n)
                    var u = User("Mel", 1.5, 26)
                    println(u.greet())
                    println(u.h)
                    println(u.age)
                    u.age = u.age + 1
                    println(u.age)
                    var p = Point()
                    println(p.sum())
                    println(p.x)
                }
                """);
        WasmWasiListE2ETest.assertListFaceMatchesJvm(dir);
    }

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
}
