[English](embedded-html-js.md) | [Português](embedded-html-js.pt_BR.md)

# Anti-pattern — Embedded HTML / CSS / JavaScript

## Name

Pasting HTML, CSS or JavaScript inside Kof source (including as string or text-block payloads).

## Problem

A Kof program expresses intent with **Kof primitives and idioms only**. Markup,
styling and scripting from a foreign stack must never be imported into `.kf` —
not as tags, not as CSS classes/inline styles, and not as string/text-block
payloads that build a UI, wire behavior or inject script.

It breaks the language surface (`AGENTS.md` rule 11), the domain separation
(rule 3) and cross-target honesty (rule 5): the same Kof source must mean the
same thing on every target, and each target's backend renders the declared
**intent**. Kof is not markup in disguise (`docs/philosophy.md`).

Authority: `DECISIONS.md` §`D-KOF-IS-KOF` (maintainer directive 29/09/2026 —
"KOF É KOF").

## Bad example

```kof
main() {
    var page = "<div class=\"card\" onclick=\"go()\">" + title + "</div>"
    app.get("/") { return page }
}
```

## Why it is wrong

- imports foreign syntax (`<div>`, `class=`, `onclick=`) into Kof source;
- behavior (`onclick="go()"`) lives in JavaScript, outside Kof's contract;
- depends on a browser/DOM the other targets do not have — silent divergence;
- the payload is invisible to the compiler: no diagnostic, no cross-target check.

## Good example

```kof
main() {
    var app = web.app()
    app.get("/") { return page.card(title) }
}
```

Declare the intent with Kof primitives (`kof.web` / `kof.ui`); each target's
backend renders it. If what you need does not exist, the fix is a Kof
abstraction (library-first, `D-KOF-FIRST`) or a maintainer decision — never
foreign syntax. Real interop goes through the sanctioned FFI/official-package
path, never embedded markup/script.

## Rule

Kof is Kof. No HTML, CSS or JavaScript inside Kof code.
