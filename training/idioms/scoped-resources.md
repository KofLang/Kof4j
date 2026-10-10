[English](scoped-resources.md) | [Português](scoped-resources.pt_BR.md)

# Scoped resources (`using`)

Release a resource when leaving the scope — intention over mechanism
(`D-SCOPED-RESOURCES-GO` slice 1; no ownership).

```kof
main() {
    using (conn = db.connect("jdbc:h2:mem:t"), db.close(conn)) {
        store(conn, record)
    } // db.close(conn) runs even if `store` throws
}
```

Rules:

- The **closer is explicit**: write whatever close idiom the type really has
  (`db.close(conn)`, `conn.close()`, `sse.close()`). No closer, no program
  (parse error, never a silent leak).
- The binding lives only inside the block; the closer runs on **both** paths
  (success/exception).
- Multiple resources: nest `using` (inner closes first).

```kof
using (a = openA(), closeA(a)) {
    using (b = openB(), b.close()) {
        work(a, b)
    }
}
```
