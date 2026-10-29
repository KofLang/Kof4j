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
}
