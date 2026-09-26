[English](README.md) | [Português](README.pt_BR.md)

# Kof Tooling

**Tooling API Level: 21**

O tooling do Kof é parte oficial da distribuição. O usuário não precisa
descobrir projetos externos para obter syntax highlighting, diagnóstico ou
formatação — tudo viaja dentro do pacote do Kof.

---

## 1. Arquitetura

```text
Kof installation
        │
        └── tooling
              ├── syntax definition      (editor/kof.tmLanguage.json)
              ├── language server        (kof lsp)
              ├── formatter              (kof fmt)
              └── diagnostics            (kof check / LSP publishDiagnostics)
```

**Regra fundamental:** não existe parser paralelo para o editor. O editor
consome o tooling do Kof, e o tooling consome o **mesmo frontend** do
compilador:

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

Isso impede a divergência entre "o compilador aceita" e "o editor acha que
está errado".

---

## 2. Tooling API Level 21

O baseline de API Java para todo o tooling é **Java 21**:

- APIs do tooling são compatíveis com Java 21;
- o Kof não exige Java anterior a 21;
- o toolchain do repo exige JDK 25 (D-BASELINE, 14/09) — ver README
  "três camadas de JDK": o piso da linguagem segue JVM 21+ e o API level do
  tooling segue 21 (`KofVersion.TOOLING_API`);
- o pacote oficial carrega sua própria JVM (Temurin 25).

---

## 3. Componentes

| Componente | Estado | Comando/Arquivo |
|------------|--------|------------------|
| Grammar oficial | ✅ | `editor/kof.tmLanguage.json` (scope `source.kof`) |
| Language Server | ✅ | `kof lsp` (stdio, LSP 3.x: diagnostics, hover, completion, definition, references, rename) |
| Type-check | ✅ | `kof check <file.kf\|dir>` |
| Test runner | ✅ | `kof test <file.kf\|dir>` (PASS/FAIL por exit code) |
| Diagnóstico do ambiente | ✅ | `kof info [--json]` |
| Formatter | ✅ | `kof fmt` |
| Workflow runner | ✅ | `kof workflow <list\|run> <file.kf>` (pipelines como código Kof: `pipeline(): KofWfDag`; `--job`/`--dry-run`/`--json`; JVM-first) |


---

## 4. Consumo pelos editores

Ver [EDITOR_SUPPORT.md](EDITOR_SUPPORT.md) para o passo a passo de VS Code,
IntelliJ, Neovim e editores LSP.

---

## 5. LSP

O `kof lsp` implementa o Language Server Protocol sobre stdio. Capacidades:

- `initialize` / `shutdown` / `exit`
- `textDocument/didOpen` / `didChange` (sync completa)
- `textDocument/publishDiagnostics` com o frontend real do compilador
- `textDocument/hover`, `textDocument/definition`, `textDocument/completion`
- `textDocument/references` + `textDocument/rename` (word-boundary, arquivo único)

Ver [LSP.md](LSP.md).

---

## 6. Formatter

`kof fmt` usa a mesma AST do frontend para reescrever o arquivo com a
formatação canônica. Sem implementação própria de parsing — o formatter
consome a saída do parser oficial, garantindo que `kof fmt` nunca altere a
semântica do programa.

Comentários são preservados SEMPRE (§509/#625): o lexer os descarta por
contrato, então o formatter AST costura de volta cada comentário
escaneado (ciente de string/char — `"http://x"` não é comentário) na
saída, em ordem de origem; a heurística de 50% que escolhia o caminho
por ACIDENTE morreu (null agora significa apenas falha de parse).
Quando o parse falha, o fallback token-based ainda preserva comentários
de linha e de bloco de linha inteira verbatim. O LSP
(`textDocument/formatting`) roda o MESMO motor e responde "sem edição"
em vez de crashar num buffer não-formatável: editor e CLI nunca perdem
um comentário.

---

## 7. Diagnostics

`kof check` executa o pipeline completo (Lexer → Parser → Semantic Analysis)
sem emitir código, reportando todos os erros. O LSP publica o mesmo conjunto
de diagnósticos, com os mesmos códigos, em tempo de edição. Com a flag `--json`,
o `kof check` produz saída estruturada para ferramentas de análise e CI/CD.