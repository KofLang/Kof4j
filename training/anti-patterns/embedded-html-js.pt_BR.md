[Português](embedded-html-js.pt_BR.md) | [English](embedded-html-js.md)

# Anti-pattern — HTML / CSS / JavaScript embutidos

## Nome

Colar HTML, CSS ou JavaScript dentro de código Kof (inclusive como payloads de string ou text block).

## Problema

Um programa Kof expressa intenção com **primitivas e idiomas Kof apenas**. Markup,
estilo e script de uma stack estrangeira nunca podem ser importados para `.kf` —
nem como tags, nem como classes/estilos CSS, nem como payloads de string/text
block que montam UI, ligam comportamento ou injetam script.

Isso quebra a superfície da linguagem (`AGENTS.md` regra 11), a separação de
domínio (regra 3) e a honestidade cross-target (regra 5): a mesma fonte Kof deve
significar o mesmo em todo alvo, e o backend de cada alvo renderiza a **intenção**
declarada. Kof não é markup disfarçado (`docs/philosophy.md`).

Autoridade: `DECISIONS.md` §`D-KOF-IS-KOF` (diretiva da mantenedora 29/09/2026 —
"KOF É KOF").

## Exemplo ruim

```kof
main() {
    var page = "<div class=\"card\" onclick=\"go()\">" + title + "</div>"
    app.get("/") { return page }
}
```

## Por que é errado

- importa sintaxe estrangeira (`<div>`, `class=`, `onclick=`) para a fonte Kof;
- o comportamento (`onclick="go()"`) vive em JavaScript, fora do contrato do Kof;
- depende de um browser/DOM que os outros alvos não têm — divergência silenciosa;
- o payload é invisível para o compilador: sem diagnóstico, sem checagem cross-target.

## Exemplo bom

```kof
main() {
    var app = web.app()
    app.get("/") { return page.card(title) }
}
```

Declare a intenção com primitivas Kof (`kof.web` / `kof.ui`); o backend de cada
alvo renderiza. Se o que você precisa não existe, o conserto é uma abstração Kof
(library-first, `D-KOF-FIRST`) ou uma decisão da mantenedora — nunca sintaxe
estrangeira. Interop real passa pelo caminho sancionado de FFI/pacotes oficiais,
nunca markup/script embutido.

## Regra

Kof é Kof. Nada de HTML, CSS ou JavaScript dentro de código Kof.
