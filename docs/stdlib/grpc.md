[English](grpc.md) | [Português](grpc.pt_BR.md)

# kof.grpc — gRPC in pure Kof

**Status:** available (unary calls, JVM target; the codec/frame/HPACK faces run
on every target) · **Introduced:** 0.6.7 · **Library:** `libs/grpc/` ·
**Interop:** proven against the reference gRPC stack (grpcio 1.85.1)

## What it is

gRPC without `io.grpc`, without protobuf-java, without a `.proto` codegen
toolchain: the wire protocol implemented in PURE KOF over the sockets
`kof.net` already ships. A Kof program can serve and call gRPC the same way it
does HTTP — part of the ecosystem, zero dependencies.

The library is five files, each one protocol layer:

| File | Layer | What it does |
|---|---|---|
| `Proto.kf` | protobuf wire format | varint / tag / length-delimited encode + decode; UTF-8 |
| `Frame.kf` | gRPC message frame | the 5-byte prefix (`compressed-flag`, big-endian length) + the status code space |
| `Hpack.kf` | HPACK | the minimal encoder (literal, no Huffman, no indexing) + the literal decoder |
| `H2.kf` | HTTP/2 framing | the preface, SETTINGS, HEADERS/DATA frames, the 9-byte header |
| `Client.kf` | the unary client | one call = preface → SETTINGS → HEADERS → DATA → trailers |

## A unary call

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

## Status codes

The spec's code space (`Status` enum in `Frame.kf`): `OK`=0 through
`UNAUTHENTICATED`=16. A call answers `CallResult(status, message, payload)` —
the status is the server's `grpc-status` trailer; `payload` is empty unless
the status is OK.

## Refusals (never a silent fallback)

| Code | What it refuses |
|---|---|
| `GRPC001` | an unsupported protobuf wire type in decode |
| `GRPC002` | a compressed gRPC message (the compressed-flag set) — no compression algorithm here |
| `GRPC003` | a truncated frame (fewer bytes than the prefix declares) |
| `GRPC004` | an HPACK Huffman-coded string or an unsupported representation — the 257-entry table is not carried |
| `GRPC005` | no `grpc-status` within the read budget (flow-control window not implemented) |
| `GRPC007` | the server RESET the stream (the HTTP/2 error code is named) |
| `GRPC008` | the response carried no `grpc-status` trailer |

## Honest limits

* **Unary only.** Server/client streaming, bidirectional — not implemented
  (they need multiplexing and flow control; `GRPC005`/`GRPC006` name it).
* **JVM target for the transport.** `kof.net`'s socket faces bind JVM (+Script
  via the interpreter) and Native; JS keeps its existing `NET001` gap.
* **The codec/frame/HPACK faces** (`Proto.kf`, `Frame.kf`, `Hpack.kf`) are pure
  and run on EVERY target — only the dialing needs sockets.
* **No TLS.** plaintext h2c to the peer; a TLS transport is a later slice.
* **No `.proto` codegen.** The schema is a Kof value (the field list); the
  builders you write are ordinary Kof functions over `Proto.kf`'s writer.

## Proof

The interop is proven against the REAL gRPC stack: `GrpcInteropE2ETest` starts
a reference grpcio 1.85.1 server and drives it with a Kof-compiled program —
`status=0`, the server's answer decoded (`value=9`), end to end. The codec's
reference vectors came from the real `protoc`; the HPACK block is the exact
byte sequence the reference peer accepted (the FIRST attempt — independent
HPACK encodes concatenated — was REJECTED by the peer with
`Missing :method header`; the single-pass encode in `Hpack.hpackEncode` is the
fix that block records).
