[English](05-funcoes.md) | [Português](05-funcoes.pt_BR.md)

# 05 — Functions

> **PortuKof — every output below was measured by running the program.**

A function is a named machine: inputs go in, a result comes out.

## The Kof pattern: type before name

```ptkf
Int dobrar(Int n) {
    retorna n * 2
}

principal() {
    escrevaln(dobrar(21))
}
```

Measured output: `42`

- `Int` → return type
- `dobrar` → function name
- `(Int n)` → parameter
- `retorna` → return statement

> **Notice:** in Kof there is **no** `fun`, `func` or `def`. Type comes before
> name, just like `main()` / `principal()`.

## Short form: expression body with `=`

```ptkf
Int triplicar(Int n) = n * 3

principal() {
    escrevaln(triplicar(7))
}
```

Measured output: `21`

## No-return functions (`void`)

```ptkf
void saudar(texto nome) {
    diga("Oi, " + nome + "!")
}
```

`void` means "no value returned — side effect only". Measured output:

```text
Oi, Ana!
Oi, Beto!
```
