# Anti-pattern — Call void de builtin em statement antes de merge (histórico)

## Name

Frame crash COMP002 com `list.add(...)` como statement seguido de `while` —
e por que NÃO é mais um problema.

## Problem (0.2.6-beta ≤ 31/08)

```kof
f(List<Box> cache, Int n): Int {
    cache.add(Box(7))     // call void builtin como statement
    var i = 0
    var tot = 0
    while (i < n) {       // merge de frames aqui
        var ent = cache.get(i)
        tot = tot + ent.a
        i = i + 1
    }
    return tot
}
```

Emitia bytecode com stack underflow (o `kof_list_add` já descartava o
boolean `add` retorna, e o `KofPop` do IR popava de novo) → o ASM quebrava
no merge de frames do loop com `Index -1 out of bounds for length 0`
(frame crash COMP002) **só quando havia um merge (while/if) depois**.

## Sintoma típico

- `kof check` ok em código linear (sem loop/if depois do call).
- Crash interno no compilador quando o call void builtin precede um
  ponto de merge (while/for/if).
- Mensagem: `frame crash em Default.Main.f: Index -1 out of bounds ... [COMP002]`.

## Resolução (01/09)

`JvmBackend.emitOperation` do `kof_list_add` não emite mais o `POP` extra:
o `ArrayList.add` empilha `boolean` e o `KofPop` do IR (gerado na conversão
de statement) o descarta — exatamente 1 push + 1 pop.

## Preferred (hoje)

```kof
cache.add(item)    // ✅ escreva normal; o bug está corrigido
```

Se um erro COMP002 "Index -1/-3" reaparecer em nova feature de call void
builtin, reproduza com o par call-statement + while e conserte o emit
(push/pop balanceado por operação), nunca contorne no código Kof.

## Why

Um bug de emit (push/pop desbalanceado) só explode em presença de merge
porque o verificador de frames do ASM (`COMPUTE_FRAMES`) unifica estados
de pilha nos labels; em código linear o desbalanceamento pode passar batido.

## Related

- `runtime-workarounds.md` §9 — correções 01/09
- `common-mistakes.md`
