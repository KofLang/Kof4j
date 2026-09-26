[English](LSP.md) | [Português](LSP.pt_BR.md)

# Kof Language Server

`kof lsp` é o Language Server oficial do Kof, distribuído com a CLI.

---

## Arquitetura

```text
Editor
   │  (LSP sobre stdio)
   ▼
kof lsp
   │
   ▼
Kof Compiler Frontend
   ├── Lexer
   ├── Parser
   ├── Symbol Table
   ├── Type System
   └── Diagnostics
```

O servidor não possui parser próprio. Cada documento aberto é compilado com
o `CompilerDriver` real (pipeline Lexer → Parser → Análise Semântica) e os
diagnósticos produzidos são publicados ao editor via
`textDocument/publishDiagnostics`, com os mesmos códigos (ex.: `PARSE041`,
`JSN001`) e mensagens que `kof check`/`kof build` reportam.

---

## Protocolo

- Transporte: stdio, framing `Content-Length`.
- Mensagens: JSON-RPC 2.0.
- Sync de documentos: completa (`change: 1`).

### Mensagens suportadas

| Mensagem | Comportamento |
|----------|---------------|
| `initialize` | Capacidades: textDocumentSync (full), serverInfo `kof-lsp` |
| `initialized` | no-op |
| `textDocument/didOpen` | compila e publica diagnostics |
| `textDocument/didChange` | recompila e publica diagnostics |
| `textDocument/hover` | palavras-chave, tipos nativos e var/val do buffer (`LspHover`); **e o DOMÍNIO**: namespace da stdlib mostra seus membros `kof.<ns>` (lista real do `StdCatalog`, a mesma fonte do completion), membro no contexto exato `ns.` nomeia o namespace (8.3, 19/09) e, nos namespaces com tabela (todos os 31 ns do catalogo, exceto `json` (sem tabela por honestidade: o dispatch e por tipo no lowerer, nao travavel por aridade no typer) — LSP-A fatias 1–6; a tabela e artefato de `scripts/gen_signatures.py`), também a(s) assinatura(s) gravada(s), um overload por linha (`StdCatalog.signaturesOf`, travada comportamento-a-dispatcher real); membro sem tabela fica na linha simples — nada de forma inventada (R6); nome solto sem `.` nunca chuta (R6); **fallback = a linha de declaração do símbolo no projeto** — buffer primeiro, depois `.kf` irmãos (X10 fatia 7, `LspProject.declarationLine`) |
| `textDocument/signatureHelp` | dentro de chamada stdlib `ns.member(` com tabela gravada (todos os 31 ns, exceto `json`): uma assinatura por overload gravada com `parameters` parseados e `activeParameter` contado por vírgulas de top-level (ciente de string/escape/parênteses/colchetes; clamp na forma mais larga) — `LspSignatureHelp`, a mesma fonte `StdCatalog.signaturesOf` do hover; sem tabela / ambíguo / fora de chamada = null honesto (R6) |
| `textDocument/definition` | ir-para-definição no buffer **e pelo projeto** — nome desconhecido cai nos `.kf` irmãos (walk ≤6, primeiro hit; mesma convenção `LspSymbols`; X10 fatia 4) |
| `textDocument/completion` | gatilho `.`: **membros de stdlib por domínio** via `StdCatalog` — os 31 namespaces reais do typer (math, strings, rng, json, log, db, http, Image/Audio/Video/Mic, ...), travados contra as fontes do typer (X10 fatias 1–3); prefixo fora da stdlib = zero invenção |
| `textDocument/references` | por palavra, no buffer **e nos `.kf` irmãos do projeto** (somente-leitura; X10 fatia 5) |
| `textDocument/rename` | **rename cross-file (LSP-A ✅ 19/09, `LspRename`)**: edições por palavra no buffer aberto + em todo `.kf` do projeto (a mesma convenção textual dos `references`); palavras-chave e namespaces da stdlib recusam com null (nunca reescreve a linguagem) |
| `textDocument/formatting` | formata pelo formatador `kof fmt` (mesmo motor, sem escritor paralelo; comentários sempre preservados, §509) |
| `textDocument/documentSymbol` | sumário do buffer (tipos + funções, varredura textual) |
| `workspace/symbol` | símbolos do **projeto inteiro**: buffers abertos (fonte da verdade) + `.kf` irmãos não-abertos; filtro substring, ordem prefixo→substring→nome→uri (X10 fatia 6) |
| `shutdown` | responde `null` |
| `exit` | encerra o processo |

### Diagnósticos

Cada `Diagnostic` do compilador é mapeado para o formato LSP:

- `line`/`column` (1-based) → posição LSP (0-based);
- severidade ERROR → 1, demais → 2;
- `source: "kof"`, `code` preservado;
- mensagem igual à do compilador.

---

## Uso

```bash
kof lsp
```

O servidor lê de `stdin` e escreve em `stdout` — integra-se a qualquer
cliente LSP (`cmd: ["kof", "lsp"]`).

---

## Limitações atuais

- A varredura cross-file do projeto (`definition`/`references`/`hover`/`workspace.symbol`) é **convenção textual** (`LspSymbols` — a mesma da navegação de arquivo único), não índice tipado/semântico: nunca mente sobre uma posição que não leu, mas não desambigua nomes iguais entre arquivos (primeiro hit, ordem determinística);
- `rename` é textual, não tipado: como os `references`, renomeia TODA ocorrência por fronteira de palavra no projeto — identificadores homônimos em arquivos sem relação entram na mesma edição (o cliente previewa antes de aplicar; índice tipado não existe por opção). Renomear keyword ou namespace da stdlib devolve null (R6);
- sincronização completa do documento (incremental planejada).
- Uma **request** JSON-RPC cujo `method` não tem handler é respondida com `-32601 MethodNotFound` (o cliente nunca trava numa method não anunciada); uma **notificação** (sem `id`) é ignorada por desenho (§429).

O caminho de evolução é sempre o mesmo: **novas capacidades do LSP
alimentam-se do frontend oficial**, nunca de um parser paralelo.