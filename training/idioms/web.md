[English](web.md) | [Português](web.pt_BR.md)

# Idioms — Web (kof.web)

**Status:** available (JVM) · **Introduced:** 0.2.6-beta · **Updated:** 0.3.0-beta

## What it is

`web.app()` creates the application; each `app.get/post/put/patch/delete(path) { … }`
registers a route. The **handler's return is the response contract**:

- `return "texto"` → `200 OK` with the body (`String`).
- `return null` → `404 Not Found` (documented absence, not an error).
- `status(code)` / `headerSet(...)` before the return → headers + code.

## GOOD — handler with presence/absence

```kof
main() {
    var app = web.app()
    app.get("/tasks/:id") {
        var id = param("id").toInt()
        if (id >= 1) {
            return "task " + id
        }
        return null    // → 404
    }
    app.delete("/tasks/:id") {
        return "deleted:" + param("id")
    }
    app.listen(8080)
}
```

The idiomatic form `if (cond) { return valor } return null` works in any
order of branches (bug 53, GitHub #28 — fixed 07/09: the handler type is now
inferred from ALL the body's returns, not just the top).

## When to use

- REST/HTTP route with the `kof.web` runtime (JVM).
- Resource absence → `return null` (404), not `throw`.

## When NOT to use

- A real handler error → `throw "mensagem"` (the runtime turns it into a 500 with the
  diagnostic, R6).
- A non-200/404 response (e.g. 301, 401) → `status(code)` + return.

## GOOD — declarative rejection bodies (`app.security`)

```kof
main() {
    var app = web.app()
    var r = mapOf()
    r.put("unauthorized", "{\"error\":\"sign in first\"}")
    val rObj: Object = r
    val o: Map<String, Object> = mapOf()
    val h: Object = "authorization"
    o.put("sessionHeader", h)
    o.put("responses", rObj)
    app.security(o)
    app.get("/me") { return "ok" }
    app.listen(8080)
}
```

`responses` (`Map`) replaces the pipeline's built-in JSON for the synthetic
`401`/`403`/`429` (keys `unauthorized`/`forbidden`/`tooManyRequests`). Omitted
keys keep the built-in bodies, so it is additive.

## BAD — re-checking the path inside every handler

```kof
app.get("/admin/users") {
    if (path() == "/admin/users" || path().startsWith("/admin")) {
        if (!auth.hasRole("admin")) { return status(403, "nope") }
    }
    return users()
}
app.get("/admin/logs") {
    if (path().startsWith("/admin")) {          // repeated in every handler…
        if (!auth.hasRole("admin")) { return status(403, "nope") }
    }
    return logs()
}
```

## GOOD — declare the resource policy once (`app.policy`)

```kof
app.security(mapOf("rateLimit", "200/60"))
app.policy("/admin", mapOf("roles", "admin"))   // every route under /admin
app.get("/admin/users") { return users() }      // inherits the policy
app.get("/admin/logs") { return logs() }        // inherits the policy
```

`app.policy(prefix, opts)` scopes the same `app.security` opts to a path prefix.
Scalars (e.g. `rateLimit`, `auth`) are deepest-wins; lists (`publicPaths`,
`roles`) accumulate. The handler never re-checks what the policy declares.

## GOOD — read pagination from the request (`pageRequest`)

```kof
import kof.web

app.get("/users") {
    try {
        val p = pageRequest(20, 100)   // ?page/limit/offset; default 20, cap 100
        return json.encode(orm.window<User>(db, p.limit(), p.offset()))
    } catch (String e) {
        return status(400, e)          // named PAGINATION: error → 400
    }
}
```

`pageRequest` returns a core `PageRequest(page, limit, offset)` — no HTTP type
leaks out. `page` is 1-based, `limit` is clamped to the cap, `?offset=` wins.
Native has no web context, so this is a `WEB001` gap there.

## Notes

- `app.listen` accepts ONLY Int (`app.listen(8080)` — #102.2 13/09: a String
  turned into a VerifyError at runtime; now it is SEM025 in `kof check`).

- `app.delete(path) { … }` is a route (HTTP verb), not `File.delete()` —
  the collided name was bug 54 (GitHub #29), fixed 07/09 (arity guard in
  `KofIo`).
- Middlewares (`app.use { … }`) follow the SAME contract: `return null`
  proceeds to the handler; `return "corpo"` responds and ends (short-circuit).
