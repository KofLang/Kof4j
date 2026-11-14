[English](grpc.md) | [Português](grpc.pt_BR.md)

# kof.grpc — gRPC em Kof puro

**Status:** disponível (chamadas unárias, alvo JVM; as faces de codec/frame/HPACK rodam
em todo alvo) · **Introduced:** 0.6.7 · **Library:** `libs/grpc/` ·
**Interop:** provado contra o stack gRPC de referência (grpcio 1.85.1)

## O que é

gRPC sem `io.grpc`, sem protobuf-java, sem toolchain de codegen `.proto`: o
protocolo wire implementado em KOF PURO sobre os sockets que o `kof.net` já entrega. Um programa Kof serve e chama gRPC do mesmo jeito que faz HTTP — parte
do ecossistema, zero dependências.

A biblioteca são cinco arquivos, cada um uma camada do protocolo:

| Arquivo | Camada | O que faz |
|---|---|---|
| `Proto.kf` | wire format protobuf | varint / tag / length-delimited encode + decode; UTF-8 |
| `Frame.kf` | frame de mensagem gRPC | o prefixo de 5 bytes (`compressed-flag`, tamanho big-endian) + o espaço de códigos de status |
| `Hpack.kf` | HPACK | o encoder mínimo (literal, sem Huffman, sem indexação) + o decoder literal |
| `H2.kf` | framing HTTP/2 | o preface, SETTINGS, os frames HEADERS/DATA, o header de 9 bytes |
| `Client.kf` | o cliente unário | uma chamada = preface → SETTINGS → HEADERS → DATA → trailers |

## Uma chamada unária

```kof
import grpc.Client
import grpc.Proto

main() {
    var conn = net.connect("127.0.0.1", 50051)
    var t = Transport(conn)
    var sent = probeMessage(41)                 // your protobuf message (Proto.kf builders)
    var res = call(t, "127.0.0.1:50051", "/mypkg.MyService/MyMethod", sent)
    if (res.status == 0) {
        println("answered " + probeValue(res.payload))
    } else {
        println("grpc error " + res.status + ": " + res.message)
    }
    conn.close()
}
```

## Códigos de status

O espaço de códigos do spec (o enum `Status` em `Frame.kf`): `OK`=0 até
`UNAUTHENTICATED`=16. Uma chamada responde `CallResult(status, message, payload)` —
o status é o trailer `grpc-status` do servidor; `payload` é vazio a menos que
o status seja OK.

## Recusas (nunca um fallback silencioso)

| Código | O que recusa |
|---|---|
| `GRPC001` | um wire type protobuf não suportado no decode |
| `GRPC002` | uma mensagem gRPC comprimida (o compressed-flag ligado) — nenhum algoritmo de compressão aqui |
| `GRPC003` | um frame truncado (menos bytes do que o prefixo declara) |
| `GRPC004` | uma string HPACK com Huffman ou uma representação não suportada — a tabela de 257 entradas não é carregada |
| `GRPC005` | nenhum `grpc-status` dentro do orçamento de leituras (a janela de flow-control não é implementada) |
| `GRPC007` | o servidor RESETOU o stream (o código de erro HTTP/2 é nomeado) |
| `GRPC008` | a resposta não carregou o trailer `grpc-status` |

## Limites honestos

* **Só unário.** Streaming servidor/cliente, bidirecional — não implementado
  (exige multiplexação e flow control; `GRPC005`/`GRPC006` o nomeiam).
* **Alvo JVM para o transporte.** As faces de socket do `kof.net` bindam JVM (+Script
  via o interpretador) e Native; o JS mantém o gap `NET001` existente.
* **As faces de codec/frame/HPACK** (`Proto.kf`, `Frame.kf`, `Hpack.kf`) são puras
  e rodam em TODO alvo — só o discar precisa de sockets.
* **Sem TLS.** h2c em texto claro para o par; um transporte TLS é uma fatia posterior.
* **Sem codegen `.proto`.** O schema é um valor Kof (a lista de campos); os
  builders que você escreve são funções Kof comuns sobre o writer do `Proto.kf`.

## Prova

A interop é provada contra o stack gRPC REAL: `GrpcInteropE2ETest` inicia um
servidor grpcio 1.85.1 de referência e o dirige com um programa compilado pela
Kof — `status=0`, a resposta do servidor decodificada (`value=9`), ponta a ponta.
Os vetores de referência do codec vieram do `protoc` real; o bloco HPACK é a
sequência exata de bytes que o par de referência aceitou (a PRIMEIRA tentativa —
encodes HPACK independentes concatenados — foi REJEITADA pelo par com
`Missing :method header`; o encode em uma passada em `Hpack.hpackEncode` é a
correção que o bloco registra).
