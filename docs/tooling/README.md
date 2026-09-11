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
              ├── formatter              (planejado — kof fmt)
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
- versões posteriores do OpenJDK (ex.: 25, para Virtual Threads) podem ser
  usadas internamente quando apropriado, sem tornarem-se requisito;
- o pacote oficial carrega sua própria JVM (Temurin 21).

---

## 3. Componentes

| Componente | Estado | Comando/Arquivo |
|------------|--------|------------------|
| Grammar oficial | ✅ | `editor/kof.tmLanguage.json` (scope `source.kof`) |
| Language Server | ✅ (mínimo) | `kof lsp` (stdio, LSP 3.x) |
| Type-check | ✅ | `kof check <file.kf\|dir>` |
| Test runner | ✅ | `kof test <file.kf\|dir>` (PASS/FAIL por exit code) |
| Diagnóstico do ambiente | ✅ | `kof info [--json]` |
| Formatter | 🔜 planejado | `kof fmt` |


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

Ver [LSP.md](LSP.md).

---

## 6. Formatter (planejado)

`kof fmt` usará a mesma AST do frontend para reescrever o arquivo com a
formatação canônica. Sem implementação própria de parsing — o formatter
consome a saída do parser oficial, garantindo que `kof fmt` nunca altere a
semântica do programa.

---

## 7. Diagnostics

`kof check` executa o pipeline completo (Lexer → Parser → Semantic Analysis)
sem emitir código, reportando todos os erros. O LSP publica o mesmo conjunto
de diagnósticos, com os mesmos códigos, em tempo de edição. Com a flag `--json`,
o `kof check` produz saída estruturada para ferramentas de análise e CI/CD.