[Português](net.pt_BR.md)

# Idioms — Network (kof.net)

**Status:** available (JVM · Native x86-64 · Script · JS/host) · **Introduced:** 0.5.0-beta (D-KOF-NET) · **Updated:** 02/10

## What it is

One namespace for sockets: TCP connections and UDP datagrams, blocking
verbs, `spawn` as the only concurrency, `Byte[]` payload in both
directions. The same `net` namespace also keeps the URI accessors
(`net.scheme/host/port/path/query/fragment`, `queryEncode/queryDecode`) —
they are pure string functions, no sockets involved.

```
net.listen(port: Int) -> Listener      net.connect(host: String, port: Int) -> Conn
listener.accept() -> Conn              // blocks
conn.send(data: Byte[]) -> Int         // bytes written
conn.receive(maxBytes: Int) -> Byte[]  // blocks up to one read; empty array at EOF
conn.close() ; listener.close()

net.bind(port: Int) -> Endpoint
endpoint.sendTo("host:port", data: Byte[]) -> Int
endpoint.receive(maxBytes: Int) -> Byte[]   // one datagram
endpoint.peer() -> String                    // source of the last receive, "" before
endpoint.close()
```

## TCP — echo, one line of framing at a time

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

`receive` returns AS SOON AS data is available (stream semantics) — it is
not a "fill N bytes" call. Framing (length prefixes, chunk tables) lives
in Kof code, never in the transport.

## UDP — one datagram, real source via `peer()`

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

Datagrams are bounded: over `65507` bytes (IPv4 UDP payload max) the send
refuses with `NET003` — there is NO transparent fragmentation. A malformed
address refuses with `NET004`; a wrong handle kind with `NET005`.

## Rules

- Payload is `Byte[]` both ways; `"text".getBytes()` builds one from a
  String, and `new Byte[n]` with `b[i] = v` builds one from raw bytes
  (values wrap to the signed byte exactly like the JVM).
- One blocking call = one thread of work: put servers behind `spawn` and
  `await` the worker before `close` of the listener.
- v1 is unicast IPv4 dotted-quad only — no broadcast/multicast, no
  hostnames beyond what the platform resolves.
- Handles (`Listener`/`Conn`/`Endpoint`) are opaque: never print or
  compare them, only call their methods.
- They are DECLARED TYPES: pass them with typed signatures
  (`Int pump(Conn c)`, `Conn open(String host, Int port)`) — the verbs
  bind by the handle kind, so a parameter behaves exactly like the `var`
  that produced it. Keep `var` for locals.

```
Int pumpIn(Conn c) {              // typed helper — no closure-inlining
    var b = c.receive(16)
    c.send(b)
    c.close()
    return b.length
}
```

## Diagnostics

| Code | Meaning |
|---|---|
| `NET002` | socket verb on a target with no runtime yet (Native riscv64/aarch64 — port pending) |
| `NET003` | datagram over the 65507-byte bound |
| `NET004` | malformed `"host:port"` address |
| `NET005` | wrong handle kind (e.g. `close` on a non-handle) |

## Verified by

`NetTcpE2ETest` (JVM/Native x86-64), `NetJsE2ETest`, `NetScriptE2ETest`,
`NetSurfaceE2ETest`, `KofInterpreterParityTest#byteShortArrayStore`;
contract frozen in `docs/development/DECISIONS.md` (`D-KOF-NET`).
