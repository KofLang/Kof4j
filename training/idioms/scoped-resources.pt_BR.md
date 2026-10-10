[English](scoped-resources.md) | [Português](scoped-resources.pt_BR.md)

# Scoped resources (`using`)

Libere um recurso ao sair do escopo — intenção sobre mecanismo
(`D-SCOPED-RESOURCES-GO` fatia 1; sem ownership).

```kof
main() {
    using (conn = db.connect("jdbc:h2:mem:t"), db.close(conn)) {
        store(conn, record)
    } // db.close(conn) roda mesmo se `store` lançar
}
```

Regras:

- O **closer é explícito**: escreva o idioma de close que o tipo realmente tem
  (`db.close(conn)`, `conn.close()`, `sse.close()`). Sem closer, sem programa
  (erro de parse, nunca leak silencioso).
- O vínculo vive só dentro do bloco; o closer roda em **ambos** os caminhos
  (sucesso/exceção).
- Múltiplos recursos: aninhe `using` (o interno fecha primeiro).

```kof
using (a = openA(), closeA(a)) {
    using (b = openB(), b.close()) {
        work(a, b)
    }
}
```
