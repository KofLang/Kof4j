[English](kofscript-embedding-guide.md) | [Português](kofscript-embedding-guide.pt_BR.md)

# Embedding Kof in a Java host

**Status:** `AVAILABLE` — `KofEmbedSession` (`kof-script`), the host-facing
contract of issue #842. Plan: [`docs/development/kofscript-embedding-plan.md`](development/kofscript-embedding-plan.md).

Use this when a Java application needs to run **player/user-authored Kof**
inside its own process: a game mod scripting surface, a plugin host, a rules
engine. The per-script JVM bytecode path is not the issue — the issue is
CONTROL, and this API is that control.

## The three guarantees

| Guarantee | What it means | Proof |
|---|---|---|
| **Bounded execution** | A script advances by a bounded number of IR operations per call and PARKS at an op boundary. `while (true) {}` can never block your thread. | `KofEmbedSessionTest#anInfiniteLoopNeverBlocksTheHost` |
| **Capability confinement** | A session is built with an explicit grant set (**empty by default**). A stdlib surface behind an ungranted capability refuses with `EMBED001`, naming the capability — never a silent no-op. | `KofEmbedSessionTest#anUngrantedCapabilityRefusesByName`, `#theGrantOpensTheSurface` |
| **Session bindings** | Host values registered per session are visible to that session's scripts only — two sessions never share state through the global path. | `KofEmbedSessionTest#sessionBindingsAreScopedAndIsolated` |

## Minimal host

```java
import dev.kof.compiler.KofCapability;
import dev.kof.script.KofEmbedSession;

try (KofEmbedSession session = KofEmbedSession.builder()
        .grant(KofCapability.FILESYSTEM)     // omit = no capability at all
        .bind("host", myHostApi)             // this session's values only
        .start(playerScript)) {

    KofEmbedSession.Status status;
    do {
        // Give the script 10_000 IR operations, then REGAIN CONTROL.
        status = session.run(10_000);
        // your loop keeps running here (render a frame, handle input, ...)
    } while (status == KofEmbedSession.Status.RUNNING);

    if (status == KofEmbedSession.Status.FAILED) {
        log.warn("script failed: {}", session.failure());
    } else {
        System.out.print(session.stdout());
    }
}
```

## Limits and non-goals

* **No new Kof syntax.** This is a host-facing runtime contract; scripts stay
  ordinary Kof.
* **No real-thread concurrency for scripts.** One script executes on one
  worker thread the session owns; the host drives it in slices.
* **No reflection escape.** A binding is a value you registered; the script
  cannot reach arbitrary Java classes through this API.
* **The granter picks the surface.** `KofCapability` is coarse today (one
  constant per stdlib area); a finer model is a later decision.
* **Not a sandbox against a malicious JVM.** Capabilities gate the KOF stdlib
  surfaces the interpreter consults; they are not a JVM security manager.
