[English](README.md) | [Português](README.pt_BR.md)

# Kof Tooling

**Tooling API Level: 21**

Kof's tooling is an official part of the distribution. The user does not need
to discover external projects to get syntax highlighting, diagnostics or
formatting — everything ships inside the Kof package.

---

## 1. Architecture

```text
Kof installation
        │
        └── tooling
              ├── syntax definition      (editor/kof.tmLanguage.json)
              ├── language server        (kof lsp)
              ├── formatter              (kof fmt)
              └── diagnostics            (kof check / LSP publishDiagnostics)
```

**Fundamental rule:** there is no parallel parser for the editor. The editor
consumes Kof's tooling, and the tooling consumes the **same frontend** as the
compiler:

```text
Editor
   │
   ▼
Kof Language Server  (kof lsp)
   │
   ▼
Kof Compiler Frontend
   ├── Lexer
   ├── Parser
   ├── Symbol Table
   ├── Type System
   └── Diagnostics
```

This prevents the divergence between "the compiler accepts" and "the editor
thinks it is wrong".

---

## 2. Tooling API Level 21

The Java API baseline for all tooling is **Java 21**:

- tooling APIs are compatible with Java 21;
- Kof does not require Java older than 21;
- the repo toolchain requires JDK 25 (D-BASELINE, 14/09) — see README
  "three JDK layers": the language floor stays JVM 21+ and the tooling API
  level stays 21 (`KofVersion.TOOLING_API`);
- the official package carries its own JVM (Temurin 25).

---

## 3. Components

| Component | Status | Command/File |
|------------|--------|------------------|
| Official grammar | ✅ | `editor/kof.tmLanguage.json` (scope `source.kof`) |
| Language Server | ✅ | `kof lsp` (stdio, LSP 3.x: diagnostics, hover, completion, definition, references, rename) |
| Type-check | ✅ | `kof check <file.kf\|dir>` |
| Test runner | ✅ | `kof test <file.kf\|dir>` (PASS/FAIL by exit code) |
| Environment diagnostics | ✅ | `kof info [--json]` |
| Formatter | ✅ | `kof fmt` |
| Workflow runner | ✅ | `kof workflow <list\|run> <file.kf>` (pipelines as Kof code: `pipeline(): KofWfDag`; `--job`/`--dry-run`/`--json`; JVM-first) |


---

## 4. Consumption by editors

See [EDITOR_SUPPORT.md](EDITOR_SUPPORT.md) for the step-by-step for VS Code,
IntelliJ, Neovim and LSP editors.

---

## 5. LSP

`kof lsp` implements the Language Server Protocol over stdio. Capabilities:

- `initialize` / `shutdown` / `exit`
- `textDocument/didOpen` / `didChange` (full sync)
- `textDocument/publishDiagnostics` with the compiler's real frontend
- `textDocument/hover`, `textDocument/definition`, `textDocument/completion`
- `textDocument/references` + `textDocument/rename` (word-boundary, single file)

See [LSP.md](LSP.md).

---

## 6. Formatter

`kof fmt` uses the same frontend AST to rewrite the file with the
canonical formatting. With no parsing implementation of its own — the
formatter consumes the official parser's output, ensuring that `kof fmt`
never changes the program's semantics.

Comments are preserved ALWAYS (§509/#625): the lexer drops them by
contract, so the AST formatter now re-sews every scanned comment
(string/char-aware — `"http://x"` is not a comment) back into the
output in source order; the old 50%-size heuristic that picked the path
BY ACCIDENT is gone (null now means parse failure only). When parsing
fails the token-based fallback still preserves line and full-line block
comments verbatim. The LSP (`textDocument/formatting`) runs the same
engine and answers "no edit" instead of crashing on unformattable
buffers: editor and CLI never lose a comment.

---

## 7. Diagnostics

`kof check` runs the full pipeline (Lexer → Parser → Semantic Analysis)
without emitting code, reporting all errors. The LSP publishes the same set
of diagnostics, with the same codes, at edit time. With the `--json` flag,
`kof check` produces structured output for analysis and CI/CD tools.
