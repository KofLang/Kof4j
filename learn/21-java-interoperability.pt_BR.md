[English](21-java-interoperability.md) | [Português](21-java-interoperability.pt_BR.md)

# 21 — Java Interoperability

> **Status: parcial — bytecode JVM compatível; chamada Java direta funciona
> para o que está no classpath (verificado 02/09; corrigido 01/10, §558)**
>
> O compilador gera bytecode JVM padrão (V21). **Antes de assumir que uma API
> Java funciona, compile e rode.** Verificado em 02/09: `java.util` collections
> ✅; `java.time`/`java.util.stream` ❌ (tipos não resolvem sem classpath
> externo); `java.io.FileWriter.write` ❌ (resolução de overload errada →
> `NoSuchMethodError`).
>
> **Re-medido 01/10 (jar do tip fresco, sondas do KofShare):** tipos do JDK
> resolvem via `import` **SEM** classpath externo — `import java.time.LocalDate;` +
> `LocalDate.now()` é ✅ (o ❌ de 02/09 era a face sem-import/receiver-qualificado).
> O nome qualificado NÃO é um receiver genérico: `java.X.Y.call(...)` cru é
> SEM011; ✅ posições são `import` + nome simples, `var x = java.X.Y.staticCall(...)`
> (inicializador) e `new java.X.Y(...)`. Streams não são caminho de interop —
> `jl.stream()` é honestamente `SEM025` (`List` do Kof é a coleção da própria
> linguagem; `new ArrayList<T>()` é tipado como `List`). Jars de terceiros chegam via
> `kof deps` + `--deps` (BouncyCastle `import`/ctor/cast/chamada de instância medidos
> verdes 01/10). Ver `known-bugs` §558 (EN+PT).

## A premissa

Kof gera bytecode JVM padrão — V21, com exception table real e virtual
threads. Bibliotecas Java podem funcionar, mas **o caminho idiomático é a
stdlib Kof** (`listOf`/`mapOf`/`kof.io`/`json.*`).

## Usando Java Collections (verificado ✅)

```kf
import java.util.ArrayList;
import java.util.HashMap;

main() {
    var lista = new ArrayList<String>()
    lista.add("Kof")
    lista.add("legal")
    println(lista.size())    // 2
    println(lista.get(0))    // Kof

    var mapa = new HashMap<String, Integer>()
    mapa.put("kof", 1)
}
```

## O idiomático: use as collections do Kof

Para o caso comum, `List<T>`/`Map<K,V>` da linguagem já resolvem — sem
`import java.util.*`:

```kf
var lista = listOf("Kof", "legal")
println(lista.size)
var mapa = mapOf("kof", 1)
```

## Transformação de dados — use `map/filter`, não Java Streams

```kf
// ✅ Kof idiomático — sem Stream, sem Collectors
var numeros = listOf(1, 2, 3, 4, 5)
var pares = numeros.filter((n: Int) -> n % 2 == 0)
println(pares.size)          // 2

// ❌ Java Streams NÃO compila sem classpath externo:
//   var pares = numeros.stream().filter(...).collect(Collectors.toList())
```

## Arquivos — use `kof.io`

```kf
// ✅ kof.io idiomático
File("/tmp/x.txt").writeText("olá")
println(File("/tmp/x.txt").readText())

// ⚠️ java.io.FileWriter.write(String) → NoSuchMethodError (02/09, não usar)
```

## O que requer classpath externo (parcial)

Tipos fora de `java.lang`/`java.util` (ex.: `java.time.*`, JDBC, Spring)
precisam do classpath externo configurado (`setExternalClasspath` /
`--classpath`) e ainda não têm paridade completa:

```kf
// Requer classpath externo + pode não resolver overloads
var hoje = LocalDate.now()          // ❌ SEM011 sem classpath
var conn = DriverManager.getConnection(url, user, pass)   // ❌ idem
```

## Reflexão na fronteira — `interop.schema` (X6)

Quando os dados vêm de fora (schemas Arrow/Parquet/ML), você normalmente precisa
da **estrutura** do record (nomes + tipos dos campos) para ligar colunas a
campos. O Kof expõe isso como um **intrínseco de compile-time**, só na fronteira
de interop — sem reflexão em runtime, sem mapper escrito à mão:

```kf
import kof.interop

record Order(String id, Double amount, Long qty)

main() {
    for (var f in interop.schema(Order)) {
        println(f.name() + ":" + f.type())   // id:String, amount:Double, qty:Long
    }
}
```

`interop.schema(R)` devolve uma `List<Field>` imutável, onde `Field` é um
`record Field(String name, String type)` fornecido pelo compilador, na ordem de
declaração. Como a dobra acontece no frontend, a saída é idêntica em
JVM/Native/Script/JS. Uso inválido é diagnosticado (`INTEROP002` membro
desconhecido; `INTEROP001` aridade errada / valor / classe / enum) — nunca
silencioso.

## Regras de interoperabilidade

1. **Tipos Kof → Java**: mapeados diretamente (`Int` → `int`, `String` → `String`)
2. **Generics**: funcionam entre as linguagens (collections ✅)
3. **Annotations**: chegam ao bytecode corretamente (ver cap. 20)
4. **Antes de usar API Java**: compile e rode — o suporte é parcial e a
   resolução de overloads ainda tem falhas (02/09)

## Reflexão na fronteira — `interop.schema(R)` (X6)

Quando dados externos precisam se ligar a um `record` Kof (um schema
Arrow/Parquet/ML), você não escreve um mapper à mão. O compilador já conhece a
estrutura do record: `interop.schema(R)` dá uma visão somente-leitura dela em
compile-time — zero reflexão em runtime, logo a mesma saída em todo target.
Ativado explicitamente por `import kof.interop`.

```kf
import kof.interop

record Order(String id, Double amount, Long qty)

main() {
    for (var f in interop.schema(Order)) {
        println(f.name() + ":" + f.type())   // id:String, amount:Double, qty:Long
    }
}
```

`interop.schema(R)` resolve para uma `List<Field>` imutável, onde `Field` é o
`record Field(String name, String type)` fornecido pelo compilador, na ordem de
declaração. Uso inválido é diagnóstico, nunca silêncio (`INTEROP002` para membro
desconhecido, `INTEROP001` para aridade errada ou argumento que não é record).

## Motores — `KofPy` e `KofR` (a linguagem e detalhe; a face e o contrato)

O `kof.interop` traz motores prontos para o interpretador que voce escolher
(X2, `D-COMPLETE-FIRST` — medido 26–27/09). Voce declara a FONTE; a plataforma
monta o RPC, o JSON e as falhas nomeadas:

```kof
import kof.interop
var py = KofPy("def greet(name):\n    return 'oi ' + name")
println(py.callString("greet", listOf("mel")))        // oi mel

var r = KofR("greet <- function(name) paste0('oi ', name)")
println(r.callString("greet", listOf("mel")))         // oi mel — mesma face, segundo motor
```

A chamada NUNCA pendura na sua mao: o deadline corre no FILHO, na propria
linguagem do motor, e cada parada e uma string NOMEADA (excecoes sao Strings
— a mesma regra de sempre no Kof):

```kof
py.timeout(2000)                       // default 30000 ms; 0 = sem limite (declarado)
try {
    println(py.callInt("loop", listOf()))
} catch (String e) {
    println(e)   // INTEROP007: loop exceeded the 2000ms deadline and was stopped by the engine itself
}
```

O `cancel()` (de uma task `spawn`, por exemplo) para a chamada viva —
`INTEROP008` nos dois motores: o python se autopará e reporta `KOFCANCEL`; o R
morre no SIGINT e o pai NOMEIA a morte (resposta que chegou primeiro vence).
Records cruzam com o JSON da propria plataforma: `callJson` + `json.decode<T>`.
Nomes no lugar do chute: `INTEROP004` interpretador ausente/morto,
`INTEROP005` o alvo nao tem runtime de processo provado (cross §514, ANDROID,
MCU — recusa em compile-time que mantem a face), `INTEROP006` erro remoto com
o traceback carregado. Os motores sao `experimental` ate os encoders cross
pousarem — JVM/x86/JS/Script certificados na CI
(`InteropPyE2ETest`/`InteropRE2ETest`/`InteropTimeoutE2ETest`).
Idioma completo: `training/idioms/interop.pt_BR.md` §(e).

## Próximo passo

[JVM →](22-jvm.md)