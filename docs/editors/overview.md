# Integração de Editores — Visão Geral

> Kof não é só compilador + runtime. Instalar o Kof deve deixar o seu
> ambiente de desenvolvimento pronto para programar. Esta é a infraestrutura
> oficial de integração de editores (EDI001).

A experiência desejada:

```
instale Kof → kof detecta seu editor → oferece a integração → confirma →
instala → abra um .kof → LSP + autocomplete + diagnostics + formatter →
comece a programar.
```

---

## Camada central: o LSP

Todos os editores compartilham o **mesmo** Language Server oficial (`kof lsp`).
Nenhum editor implementa parser ou regra semântica própria — isso garantiria
divergência (o editor "aceitaria" o que o compilador rejeita). O editor consome
o tooling do Kof:

```
                    kof lsp  (LSP 3.x, stdio)
        ┌───────────────┼───────────────┐
      VS Code        Neovim         IntelliJ
        │              │              │
       Vim           Emacs          outros
```

| Recurso | Fonte |
|---|---|
| Syntax highlighting | grammar TextMate (`editor/kof.tmLanguage.json`) ou sintaxe nativa do editor |
| Diagnostics / completion / hover / rename / references | `kof lsp` |
| Formatação | `kof fmt` |
| Build / Run / Test / Check / Serve | `kof build` / `run` / `test` / `check` / `serve` |
| Debugging | `kof debug` (DAP — **PARTIAL**) |

---

## CLI `kof editor`

```bash
kof editor list       # integrações oficiais disponíveis
kof editor detect     # editores instalados + integrações
kof editor status     # ambiente de edição (versão, path, LSP, instalado)
kof editor setup      # detecta e instala as recomendadas (com consentimento)
kof editor install <editor>     # vscode|vim|neovim|intellij|geany|nano|emacs
kof editor uninstall <editor>
kof editor update     # re-sincroniza integrações instaladas
```

`setup` e o hook pós-`kof install` **nunca** alteram o ambiente sem
consentimento; em ambiente sem console (CI/headless) apenas apontam o comando.
A instalação é **idempotente** e o `uninstall` remove só o que o Kof escreveu.

---

## Reconhecimento de arquivos e workspace

- Extensões: `*.kf` e `*.kof` (fonte Kof).
- Workspace: um projeto é reconhecido pela presença de `kof.toml` na raiz
  (o LSP e as integrações sobem até encontrá-lo).

---

## Por editor

- [VS Code](vscode.md)
- [Neovim](neovim.md)
- [Vim](vim.md)
- [Emacs](emacs.md)
- [Geany](geany.md)
- [Nano](nano.md)
- [IntelliJ IDEA](intellij.md)

---

## Terminal é soberano

As integrações são uma camada de conveniência sobre a CLI. Você sempre pode
rodar `kof build` / `kof test` / `kof run` / `kof serve` manualmente — nada é
escondido atrás do editor.
