[English](net.md) | [Português](net.pt_BR.md)

# Idiomas — Rede (kof.net)

**Status:** disponível (JVM · Native x86-64 · Script · JS/host) · **Introduzido:** 0.5.0-beta (D-KOF-NET) · **Atualizado:** 02/10

## O que é

Um namespace para sockets: conexões TCP e datagramas UDP, verbos
bloqueantes, `spawn` como única concorrência, payload `Byte[]` nas duas
mãos. O mesmo `net` mantém os acessores de URI (`net.scheme/host/port/
path/query/fragment`, `queryEncode/queryDecode`) — são funções puras de
String, sem socket envolvido.

```
net.listen(port: Int) -> Listener      net.connect(host: String, port: Int) -> Conn
listener.accept() -> Conn              // bloqueia
conn.send(data: Byte[]) -> Int         // bytes escritos
conn.receive(maxBytes: Int) -> Byte[]  // bloqueia até uma leitura; array vazio no EOF
conn.close() ; listener.close()

net.bind(port: Int) -> Endpoint
endpoint.sendTo("host:port", data: Byte[]) -> Int
endpoint.receive(maxBytes: Int) -> Byte[]   // um datagrama
endpoint.peer() -> String                    // origem da última leitura, "" antes
endpoint.close()
```

## TCP — eco, um enquadramento por vez

```kof
main() {
    var l = net.listen(18081)
    var worker = spawn {
        var s = l.accept()
        var got = s.receive(4096)
        s.send(got)
        s.close()
    }
    var c = net.connect("127.0.0.1", 18081)
    c.send("ping".getBytes())
    var back = c.receive(4096)
    println(back.size)
    c.close()
    await worker
    l.close()
}
```

`receive` retorna ASSIM QUE há dados (semântica de stream) — não é uma
chamada "preencher N bytes". O enquadramento (prefixos de tamanho, tabelas
de chunks) vive no código Kof, nunca no transporte.

## UDP — um datagrama, origem real via `peer()`

```kof
main() {
    var server = net.bind(18082)
    var client = net.bind(18083)
    client.sendTo("127.0.0.1:18082", "hi".getBytes())
    var got = server.receive(4096)
    println(got.size)
    println(server.peer())      // "127.0.0.1:18083"
    client.close()
    server.close()
}
```

Datagramas têm teto: acima de `65507` bytes (payload UDP IPv4 máximo) o
envio recusa com `NET003` — NÃO existe fragmentação transparente. Endereço
malformado recusa com `NET004`; tipo de handle errado, `NET005`.

## Regras

- O payload é `Byte[]` nas duas mãos; `"texto".getBytes()` constrói um a
  partir de String, e `new Byte[n]` com `b[i] = v` constrói a partir de
  bytes crus (o valor envolve para o byte com sinal exatamente como no JVM).
- Uma chamada bloqueante = uma linha de trabalho: servidores vivem atrás de
  `spawn`, e o `await worker` vem antes do `close` do listener.
- O v1 é unicast IPv4 dotted-quad apenas — sem broadcast/multicast, sem
  nomes de host além do que a plataforma resolve.
- Handles (`Listener`/`Conn`/`Endpoint`) são opacos: nunca imprima nem
  compare, apenas chame os métodos deles.
- Eles são TIPOS DECLARADOS: passe-os em assinaturas tipadas
  (`Int pump(Conn c)`, `Conn open(String host, Int port)`) — os verbos
  ligam pelo tipo de handle, então um parâmetro se comporta exatamente
  como o `var` que o produziu. Para locais, mantenha `var`.

```
Int pumpIn(Conn c) {              // helper tipado — sem inline via closure
    var b = c.receive(16)
    c.send(b)
    c.close()
    return b.length
}
```

## Diagnósticos

| Código | Significado |
|---|---|
| `NET002` | verbo de socket em alvo sem runtime (Native riscv64/aarch64 — porta pendente) |
| `NET003` | datagrama acima do teto de 65507 bytes |
| `NET004` | endereço `"host:port"` malformado |
| `NET005` | tipo de handle errado (ex.: `close` fora de um handle) |

## Verificado por

`NetTcpE2ETest` (JVM/Native x86-64), `NetJsE2ETest`, `NetScriptE2ETest`,
`NetSurfaceE2ETest`,
`KofInterpreterParityTest#byteShortArrayStore`; contrato congelado em `docs/development/DECISIONS.md` (`D-KOF-NET`).
