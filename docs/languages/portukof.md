[English](portukof.md) | [Português](portukof.pt_BR.md)

# PortuKof (`.ptkf`) — Portuguese Surface for Kof

last: F7.3 profile-aware AST formatter landed 08/10 (`.ptkf` formats FROM the AST through the single `SurfaceNames` bridge — no transpile, no token-fallback-as-primary, round-trip AST-equal, idempotent, Kof byte-identical; `PortuKofFormatterProfileE2ETest` 18/18 + CLI/LSP `PortuKofToolingE2ETest` 8/8) | F7.2 surface rendering + `.ptkf` imports landed 07/10 (completion/hover/signature/symbols render PT through the single `lang/SurfaceNames` bridge over canonical symbols — no regex, no textual replace, no second engine; `CompilerImports` resolves `.ptkf` sibling/package/mixed Kof↔PortuKof by extension; `PortuKofToolingSurfaceE2ETest` 20/20) | F7.1 tooling parity landed 07/10 (`.ptkf` first-class in LSP analysis + machine-contract parity + formatter anti-transpile + editor manifests; `PortuKofToolingE2ETest` 7/7; gate `check_portukof_tooling.sh` rc=0) | F6 localized diagnostics landed 07/10 (164 codes / 278 variants, PT 100%, placeholder parity locked; `PortuKofDiagnosticsTest` 9/9) | U3 receiver-aware methods landed 07/10 (17 categories; `tamanho`=length/size; `PortuKofMethodsE2ETest` 8/8) | U2 stdlib parity landed 07/10 (308 members / 36 namespaces; `PortuKofStdlibE2ETest` 4/4) | U1 substrate landed 07/10 (`LanguageProfile` + Lexer profile-aware + `.ptkf` autodiscovery; `PortuKofSurfaceE2ETest` 11/11)
location: docs/languages/portukof.md
state: active

intent: portukof-surface-specification

constraint:
* one-compiler
* one-ast
* one-ir
* one-runtime
* one-stdlib
* one-backends
* full-frontend-parity (absolute: no subset, no stub, Q7)
* strings-never-translated
* user-identifiers-never-translated
* comments-never-translated
* no-accent-keywords

---

## 1. What PortuKof is

PortuKof is a **Brazilian Portuguese human surface** for the Kof programming language (`D-PORTUKOF`). It is **NOT** a compiler fork, a second compiler, a second AST/IR/runtime/stdlib, a parallel transpiler, an independent parser, or a subset of Kof.

```text
PortuKof source (.ptkf)
        ↓
 LanguageProfile (forFileName = extension gate)
        ↓
  lexer / parser profile-aware  →  canonical AST (TokenType canonicalized)
        ↓
  Kof semantics (single pipeline)  →  Kof IR → Kof runtime → Kof backends
```

**Golden rule:** PortuKof changes the *human surface*, never the *semantics*. Everything below the parse boundary believes it is compiling canonical Kof.

---

## 2. Autodiscovery

`foo.ptkf` is identified as PortuKof by its **extension** (`LanguageProfile.forFileName`) — the extension is the primary and only authority, never file content. No flag is required:

```bash
kof run programa.ptkf
kof build programa.ptkf
kof check programa.ptkf
kof fmt programa.ptkf
kof lsp                     # .ptkf buffers are analysed as PortuKof
```

Extensions: `.ptkf` → PortuKof (`pt-BR`); `.kf`, `.kof` → canonical Kof (`en`).

---

## 3. Keywords (64 pairs, bijective, ASCII only)

The full table is generated and gate-checked for bijection + no-accents by `scripts/check_portukof_parity.sh`. Representative subset (source: `lang/PortuKofVocabulary.PAIRS`):

| PortuKof | Kof | PortuKof | Kof |
|---|---|---|---|
| `classe` | `class` | `registro` | `record` |
| `interface` | `interface` | `enumeracao` | `enum` |
| `entidade` | `entity` | `externo` | `extern` |
| `pacote` | `package` | `importa` | `import` |
| `estende` | `extends` | `implementa` | `implements` |
| `publico` | `public` | `privado` | `private` |
| `protegido` | `protected` | `estatico` | `static` |
| `abstrato` | `abstract` | `final` | `final` |
| `vazio` | `void` | `novo` | `new` |
| `este` | `this` | `super` | `super` |
| `retorna` | `return` | `lanca` | `throw` |
| `se` | `if` | `senao` | `else` |
| `para` | `for` | `enquanto` | `while` |
| `faca` | `do` | `escolha` | `switch` |
| `caso` | `case` | `padrao` | `default` |
| `sair` | `break` | `segue` | `continue` |
| `tenta` | `try` | `pegar` | `catch` |
| `porFim` | `finally` | `afirme` | `assert` |
| `gera` | `spawn` | `aguarda` | `await` |
| `instanciaDe` | `instanceof` | `como` | `as` |
| `var` | `var` | `val` | `val` |
| `logico` | `Bool` | `inteiro` | `Int` |
| `texto` | `String` | `duplo` | `Double` |
| `verdadeiro` | `true` | `falso` | `false` |
| `nulo` | `null` | `sobrescreve` | `override` |

`funcao` / `fn` / `func` are lexed to non-Kof token types and then **refused** by the parser (Kof declares functions by type+name, not by a function keyword). Entry point `principal() { … }` is normalized to canonical `main() { … }` in the AST (`lang/PortuKofParity.canonName`).

---

## 4. Builtins & Standard Library Aliases

Builtins (source: `lang/PortuKofVocabulary.BUILTINS`):

| PortuKof | Kof | PortuKof | Kof |
|---|---|---|---|
| `escreva` | `print` | `escrevaln` | `println` |
| `leia` | `readLine` | `leiarquivo` | `readFile` |
| `escrevaarquivo` | `writeFile` | `listaDe` | `listOf` |
| `mapaDe` | `mapOf` | `conjuntoDe` | `setOf` |
| `tamanho` | `len` | `agora` | `now` |
| `durma` | `sleep` | `canal` | `channel` |
| `gera` | `spawn` | `aguarda` | `await` |

Speech sugar (`lang/PortuKofVocabulary.SUGAR`, `D-PORTUKOF-SUGAR`, maintainer 08/10): child-facing aliases that **extend the accepted spellings** without replacing the primary table — same symbol, same implementation:

| PortuKof sugar | Kof | primary PortuKof |
|---|---|---|
| `diga` | `println` | `escrevaln` |
| `diz` | `print` | `escreva` |

The sugar only widens the surface→canonical closed domain (parser normalization, completion, hover, rename guards). Canonical→surface rendering always returns the **primary** spelling (`escrevaln`/`escreva`); `kof fmt` keeps `diga`/`diz` verbatim. Proof: `PortuKofSurfaceE2ETest` (normalize identity + script parity), `PortuKofFormatterProfileE2ETest` (verbatim idempotency), `PortuKofToolingSurfaceE2ETest` (completion/hover); gate `scripts/check_portukof_parity.sh` §3b.

Standard library namespaces (source: `lang/PortuKofStdlibMembers.namespaces()`, 36 namespaces; generated by `scripts/gen_portukof_aliases.py` from a live `StdCatalog` dump):

| PortuKof | Kof | PortuKof | Kof |
|---|---|---|---|
| `matematica` | `math` | `textos` | `strings` |
| `banco` | `db` | `tempo` | `time` |
| `aleatorio` | `random` | `codificacao` | `encoding` |
| `rede` | `net` | `processo` | `process` |
| `validacao` | `validation` | `autenticar` | `auth` |

Sample members (canonical → surface): `math.sqrt`→`matematica.raizQuadrada`, `db.connect`→`banco.conectar`, `db.query`→`banco.consultar`, `http.get`→`http.obter`, `time.sleep`→`tempo.dormir`, `time.now`→`tempo.agora`, `strings.count`→`textos.contar`.

A bare call like `max(x)` (a user function name) is **never** rewritten — only qualified member calls over known namespaces (and the receiver-aware typed calls of §5) are resolved.

---

## 5. Receiver-Aware Method & Field Aliases (17 categories)

Method aliases depend on the **receiver type** (`PortuKofMethodCategories`, source: `lang/PortuKofMethodAliases` generated from the real dispatchers), because the native backend only exposes target-specific primitives:

```text
tamanho  on String / Array  → length     (kof_string_length exists on native)
tamanho  on List / Map / Set → size       (no kof_string_size)
```

So a pure AST text replacement would be dishonest across targets. Resolution instead runs the *same* semantic analyzers as Kof:

1. an **entry hook** in `SemMethodCallTyper` / `MemberCallTyper` / `SemFieldAccessTyper` (receiver type already resolved) canonicalizes the dispatch name and records it (so `SEM025`/`SEM102` rejections see the real method);
2. `PortuKofMethodSplicer` rewrites the AST to canonical names post-analysis and re-indexes the identity caches node-by-node.

Sample aliases: `contem`→`contains`, `estaVazio`→`isEmpty`, `comprimento`/`tamanho`→`length`, `mapear`→`map`, field `nome`→`name`, `caminho`→`path`. A user class returns a `null` category — a user method even if named `tamanho` or `contem` is **never** rewritten (`PortuKofMethodsE2ETest` proves this).

---

## 6. Localized Diagnostics (F6)

* **164 codes / 278 variants** covering 100% of the user-facing `LEX`/`PARSE`/`SEM` domain (derived from the real emission sites by `scripts/gen_portukof_diags.py`, including modelable dynamic messages).
* **Single diagnostic system** — the canonical `code` is the only key.
* `Diagnostic.message()` is **always canonical English** (test/LSP/`--json` intact). `format()`/`localizedMessage()` produce the PT form **only** for `.ptkf` (by extension, never content).
* `PortuKofDiagnostics.localize(code, file, message, args)` renders PT **only** when the EN template, rendered with the *same* args, reproduces the canonical message byte-for-byte; otherwise English (never an approximate translation). Placeholder parity EN↔PT is gate-locked.
* Identifiers, literals and code snippets inside diagnostics are preserved verbatim (`'João'` is never translated).

---

## 7. Tooling & LSP Surface (F7)

`.ptkf` is a **first-class citizen** of the existing tooling — the difference between Kof and PortuKof is only the linguistic surface. There is **no second engine**: tooling parity comes from the `LanguageProfile` boundary + the single canonical pipeline (golden rule §4).

* **Extension authority (§6):** `LspProject.fileNameOf` preserves the real `.ptkf` basename, so `LspServer.analyze` drives the canonical pipeline with the PortuKof profile. Project-mirror and sibling scans include `.ptkf`.
* **Machine contract (§16/§17):** LSP `publishDiagnostics` and `--json` use `Diagnostic.message()` (canonical EN); `code`, `severity`, `range` and structured `args` are identical between an equivalent Kof and PortuKof program (proven by `PortuKofToolingE2ETest`). Localization never touches machine output.
* **Source ranges (§29/§30):** positions come from source-authoritative tokens (line/column/offset/length of the *real* `.ptkf` text) — a longer PT word never shifts a range.
* **Profile-aware AST formatter (§18–22/§39, F7.3):** the AST printer parses `.ptkf` **directly** with the profile lexer — structural slots arrive canonical (`se`→IF "if", `texto`→`string`), name slots arrive **verbatim** (user identifiers, `escrevaln`, `tamanho`, imports) — and re-emits every structural slot through the single `lang/SurfaceNames` bridge. Portuguese comes out of the AST: no transpile, no regex, no textual replace, no second vocabulary table in the printer. Parse errors return `null` and the CLI/LSP fall back to the explicit token serializer (byte-for-byte surface, F7.1 behavior). Comments reuse the existing text-source sew (`KofFormatterComments`), so comment content is never translated. Round-trip is AST-equivalent (`PortuKofParity.normalize`) and idempotent (`F(F(x)) == F(x)`); `.kf` output is byte-identical to pre-F7.3 behavior. Proof: `PortuKofFormatterProfileE2ETest` 18/18; CLI/LSP wiring `PortuKofToolingE2ETest` 8/8.
* **Editor manifests (§6):** VS Code (`VscodeExtensionContent`) and IntelliJ (`KofEditorContent`) register `.ptkf` under the same `Kof` language.
* **Surface rendering (§33, F7.2):** completion/hover/signature/symbols render the PT surface through a SINGLE bridge, `lang/SurfaceNames` (`canonical → surface`), which delegates to the already-gated catalogs (`PortuKofVocabulary`, `PortuKofStdlibMembers`, `PortuKofMethodAliases`). No regex, no textual `replace`, no per-tool list: `SurfaceNames` returns identity when the profile is KOF, so Kof is byte-identical. A word typed in a `.ptkf` buffer is canonicalized *by the profile* to the same symbol Kof uses, then re-rendered in surface spelling; user identifiers never pass through the bridge.
* **Imports (§21–§24, F7.2):** the single `CompilerImports` resolver recognizes `.ptkf` by extension (same `LanguageProfile.forFileName` authority), parses the imported file with its own profile, and normalizes surface imports (`importa arquivo.csv` → `file.csv` via `PortuKofParity.canonImport`). Kof↔PortuKof modules coexist: a `.ptkf` may import a `.kf` and vice-versa, with no parallel resolver.

---

## 8. Absolute Rules

1. Strings are never translated (`"principal escreva tamanho"` stays byte-for-byte).
2. User identifiers are never translated (`minhaFuncao`, `tamanhoMeu`, `principalidade`, `max`).
3. Comments are never translated (`// principal chama escreva`).
4. No second engine: parity comes from `LanguageProfile` + the canonical pipeline, never a parallel `PortuKof*` tooling class.
5. No AI translation: every alias is derived from the real runtime catalogs (`StdCatalog`, dispatchers).
6. Case sensitivity and accent rules follow the existing lexer exactly — PortuKof keywords are ASCII, no `função`/`variável`/`senão` variants.

---

## 9. Parity Matrix (a cell is ✓ only with an executed test)

| Feature | Kof (`.kf`) | PortuKof (`.ptkf`) | Mechanism / Proof |
|---|---|---|---|
| CLI discovery (run/build/check/fmt) | ✓ | ✓ | `KofCliSupport.isKofSource`; `PortuKofSurfaceE2ETest` 11/11 |
| Parse + canonical AST | ✓ | ✓ | `Lexer`/`Parser` 4-arg + `PortuKofParity.normalize` |
| Keywords (64, bijective, ASCII) | ✓ | ✓ | `PortuKofVocabulary` (gate rc=0) |
| Stdlib members/namespaces (308/36) | ✓ | ✓ | `PortuKofStdlibMembers`; `PortuKofStdlibE2ETest` 4/4 |
| Receiver-aware methods (17) | ✓ | ✓ | `PortuKofMethodSplicer`; `PortuKofMethodsE2ETest` 8/8 |
| Human diagnostics (CLI `format`) | ✓ | ✓ (PT) | `PortuKofDiagnostics` 164 codes; `PortuKofDiagnosticsTest` 9/9 |
| Machine diagnostics (LSP/`--json`) | ✓ (EN) | ✓ (EN) | `d.message()`; `PortuKofToolingE2ETest` 6/6 |
| LSP single-file + project-mirror analysis | ✓ | ✓ | `LspProject.fileNameOf`+mirror; `PortuKofToolingE2ETest` |
| Formatter (profile-aware AST) | ✓ (AST) | ✓ (AST) | `KofFormatter`+`SurfaceNames`; `PortuKofFormatterProfileE2ETest` 18/18; token fallback only on parse error |
| Editor manifests (VS Code, IntelliJ) | ✓ | ✓ | `EditorIntegrationTest` 22/22 |
| Document / workspace symbols (surface) | ✓ | ✓ | `LspSymbols` profile-aware; `PortuKofToolingSurfaceE2ETest` 20/20 |
| Completion surface (kw/builtin/stdlib/method/user) | ✓ | ✓ | `LanguageProfile`+`SurfaceNames`; `PortuKofToolingSurfaceE2ETest` |
| Hover surface (builtin/stdlib/method/user) | ✓ | ✓ | receiver-aware category (§11); `PortuKofToolingSurfaceE2ETest` |
| Signature help (surface label, canonical params) | ✓ | ✓ | `LspSignatureHelp` profile-aware |
| Import `.ptkf` (sibling/package/mixed) | ✓ | ✓ | `CompilerImports` extension-authority + `PortuKofParity.canonImport` |
| Definition / references / rename (semantic identity) | core-textual | core-textual | Kof LSP is textual by design (no typed index): user symbols resolve; cross-receiver alias disambiguation = core gap, declared (not faked) |
| Rename guards (surface alias/keyword) | ✓ | ✓ | `LspRename` profile-aware refusal |
| Semantic tokens | ✗ | ✗ | absent in Kof core — honest absence (§15) |

**Core limitation, declared not faked (§18/§15):** Kof's LSP definition/references/rename are
*textual* (the server does not run a full parser/typed index per request — see `LspSymbols`).
User symbols rename/resolve correctly on either surface. Disambiguating a bare alias word
(`tamanho` = `length` on String vs `size` on List) across receivers, strings and comments needs a
typed symbol index that the core does not have; PortuKof does **not** fake it with textual rules.
The *compiler* resolves aliases by receiver type (U3 `PortuKofMethodSplicer`), so the language
contract is complete; only the editor-side *semantic* navigation inherits the core limitation.

---

## 10. Examples

Official `.ptkf` examples live under `examples/portukof/` and are compiled (zero diagnostics) by `PortuKofToolingE2ETest`. Only catalog vocabulary from §3–§5 is used.

```ptkf
// exemplos/portukof/ola_mundo.ptkf
principal() {
    escrevaln("Olá, mundo!")
}
```

```ptkf
// exemplos/portukof/colecoes.ptkf
principal() {
    val xs = listaDe(3, 1, 2)
    val nome = "Kof"
    escrevaln(nome.tamanho())     // String → length
    escrevaln(xs.tamanho())        // List   → size
    se (xs.contem(2)) {
        escrevaln("tem dois")
    }
}
```

---

## 11. Verification Gates

```bash
bash scripts/check_portukof_parity.sh    # lexical + stdlib + methods + F6 diags 100%
bash scripts/check_portukof_tooling.sh   # F7 tooling first-class + AST formatter + machine contract
mvn -o -pl kof-compiler,kof-cli -am test -Dtest='PortuKof*'   # executed proof
```
