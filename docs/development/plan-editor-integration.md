# PLAN — Editor Integration (EDI001)

> **Status:** `PLANNED` · **Gap:** `EDI001` · **Criado:** 07/09/2026
> **Origem:** briefing "KOF EDITOR INTEGRATION" (infra oficial de integração de
> editores/IDEs). **Escopo desta doc:** especificação + ordem de implementação.
> **A implementação é DEPOIS** — este documento é o contrato.
> **Regra:** nada aqui é *ação* sobre o trabalho atual (R12); não inicia antes
> de ser reivindicado no `DOING.md`. Reutiliza o que já existe; **nunca** um
> segundo LSP/formatter/build (R9 interop-first, R6 nunca silencioso).

---

## 0. Estado real auditado (o que JÁ existe — não recriar)

| Capacidade | Onde hoje | Estado |
|---|---|---|
| Grammar TextMate | `editor/kof.tmLanguage.json` (`source.kof`, `.kf`/`.kof`) | presente |
| Language Server | `kof-cli/.../LspServer.java` (LSP 3.x, stdio) | completion, hover, rename, references, publishDiagnostics |
| Diagnostics | idênticos ao compilador, via LSP ou `kof check` | presente |
| Formatter | `kof-cli/.../Fmt.java` (`kof fmt`) | presente |
| Build/Run/Test | `kof build` / `run` / `test` / `serve` | presente |
| Debugger (DAP) | `KofDebug.java` + `JdwpClient` (`kof debug`, JDWP) | PARTIAL |
| Workspace detection | `ProjectLocator` (sobe até `kof.toml`) | presente |
| Doc de suporte | `docs/tooling/EDITOR_SUPPORT.md` (grammar + LSP por editor) | presente, leve |
| Installer | copia a pasta `editor/` para o prefix | presente (passivo) |
| Comando `kof editor` | **NÃO existe** (sem `CmdEditor`) | ausente — é o miolo deste plano |

**Conclusão da auditoria:** o plano NÃO parte do zero. O que falta é (a) a
**infra de EditorIntegration** (detector/registry/installer), (b) o **comando
`kof editor`**, (c) **integrações por editor** que empacotem grammar+LSP+fmt
de forma idiomática, e (d) **docs**. O LSP já é a camada central (seção 15 do
briefing) — este plano só a **expor**, nunca a duplicar.

---

## 1. Objetivo

Após instalar Kof, `kof editor detect` lista editores instalados + integrações
disponíveis; `kof editor setup` instala as recomendadas (com consentimento);
abrir um `.kof` dá LSP + autocomplete + diagnostics + formatter sem o usuário
pesquisar "como configurar Kof no meu editor".

---

## 2. NÃO acoplar o core a editores

Proibido `if (vscode)`/`if (vim)` espalhado pela CLI. Abstração de
provider:

```
EditorIntegration          (interface — o contrato)
├── id() / displayName()
├── detect(): EditorInfo?     // null = não instalado
├── integrationAvailable()    // existe pacote/config oficial?
├── integrationInstalled()    // já configurado nesta máquina?
├── install(ctx) / uninstall(ctx)
├── configure(ctx)            // LSP/formatter/filetype
└── status(): IntegrationStatus
```

Estrutura proposta (adaptada à arquitetura real — ver §3, o provider vive em
`kof-cli`, não num módulo novo):

```
kof-cli/.../cli/editor/
├── EditorIntegration.java     (interface)
├── EditorInfo.java            (record: id, version, path, available, installed)
├── EditorRegistry.java        (List<EditorIntegration> — registro, sem if-by-id)
├── EditorDetector.java        (PATH + dirs por plataforma; sem /usr/bin hardcoded)
├── EditorConfig.java          (o quê escrever: LSP cmd, root, filetype, formatter)
├── EditorInstaller.java       (copia/configura; idempotente; consent)
├── EditorStatus.java
└── providers/
    ├── VscodeProvider.java
    ├── IntelliJProvider.java
    ├── VimProvider.java
    ├── NeovimProvider.java
    ├── GeanyProvider.java
    ├── NanoProvider.java
    └── EmacsProvider.java
```

`CmdEditor.java` (raiz de `cli/`) faz o dispatch `kof editor <sub>` para
list/detect/status/setup/install/uninstall/update.

---

## 3. Arquitetura de dependência (onde cada coisa vive)

- **Provider logic** → `kof-cli` (ele que conhece PATH, fs, instaladores).
- **Conteúdo de integração** (grammar, config snippets) → pasta `editor/`
  (já viaja na distribuição; §14: sem rede quando possível).
- **LSP/formatter/debug** → **já existem** e são chamados via CLI; o provider
  só aponta o editor para `kof lsp`/`kof fmt`/`kof debug`. **Nada de parser por
  editor.**
- **Testes** → `kof-cli/src/test/.../editor/` (mocks/fakes, §23-24).

---

## 4. Editores prioritários

| Editor | Pacote/config | Escopo mínimo | Notas |
|---|---|---|---|
| VS Code | extensão local | grammar, LSP, diagnostics, completion, hover, go-to-def, references, rename, formatting, code actions, comandos, debug (quando DAP pronto) | consome `kof lsp`; snippets+commands via `package.json` |
| IntelliJ | plugin (Platform) | `.kof` recognition, highlighting, LSP (LSP4IJ), diagnostics, completion, formatting, navigation, run/build | delega a `kof check/build/run/test/fmt/lsp` |
| Vim | `ftplugin`+`syntax` | filetype, syntax, indent, compiler, LSP, formatting | config idiomática |
| Neovim | plugin lua | filetype, syntax, indent, LSP, diagnostics, completion, formatting, code actions, navigation | `vim.lsp.start({cmd={"kof","lsp"}})` |
| Geany | `.conf` | filetype, syntax, indent, build/run, compiler; LSP se suportado | |
| Nano | `syntaxes/kof.nanorc` | syntax, filetype, config oficial | proporcional ao editor — **não** IDE no Nano |
| Emacs | `kof-mode` | `kof-mode`, syntax, indent, LSP (eglot), diagnostics, formatting, commands | reutiliza eglot |

Todos reconhecem `*.kof` (§16) e apontam para o **mesmo** LSP (§15).

---

## 5. Detecção de editores

Multiplataforma (Linux/macOS/Windows). **Sem** `/usr/bin` hardcoded. Fontes:
PATH, executáveis conhecidos, dirs de config conhecidos, mecanismos nativos.
Informe `editor / version / path / integration available / installed`.
**Não inventar versões** (se não souber, `unknown`).

Probes por editor (a consolidar no `EditorDetector`):

| Editor | Probe |
|---|---|
| VS Code | `code --version`; config em `~/.vscode`, `%APPDATA%\Code`, `~/Library/Application Support/Code` |
| Vim | `vim --version` |
| Neovim | `nvim --version` |
| IntelliJ | dirs `~/Library/Application Support/JetBrains`, `~/.config/JetBrains`, `%APPDATA%\JetBrains` |
| Geany | `geany --version` |
| Nano | `nano --version` |
| Emacs | `emacs --version` / `emacsclient --version` |

---

## 6. Comandos `kof editor`

```
kof editor                # = detect (alias)
kof editor list           # integrações disponíveis (independe de detectado)
kof editor detect         # editores + integrações detectados
kof editor status         # detalhe: versão, path, instalado, LSP (ver §12)
kof editor setup          # fluxo principal (ver §7)
kof editor install <id>   # vscode|intellij|vim|neovim|geany|nano|emacs
kof editor uninstall <id>
kof editor update         # re-sincroniza integrações instaladas
```

`setup`/`install`/`uninstall` **nunca** alteram o ambiente sem consentimento
quando a operação é visível ao usuário (§12, §14).

---

## 7. `kof editor setup` — fluxo

```
1. detectar editores
2. detectar versões
3. detectar integrações existentes
4. verificar compatibilidade
5. mostrar recomendações
6. pedir confirmação
7. instalar integrações
8. configurar LSP/formatter
9. validar instalação
10. mostrar resultado
```

Idempotente: rodar de novo não duplica config. Recusou → mostra
`kof editor setup` para depois. **Nunca** bloqueia a instalação do Kof.

---

## 8. Installer oficial

Durante o install do Kof, detectar editores e **oferecer** (com `Y/n`) as
integrações recomendadas. Recusou → `kof editor setup` depois. Não bloqueia a
instalação nem falha se nenhum editor existir.

---

## 9. Sem rede quando possível

Arquivos de integração **viajam na distribuição** (pasta `editor/`). Só usa o
mecanismo oficial do editor quando um marketplace exigir download externo;
**nunca** código arbitrário de URL desconhecida.

---

## 10. LSP como camada central

```
                 Kof LSP (kof lsp — JÁ EXISTE)
        ┌───────────────┼───────────────┐
      VSCode         Neovim          IntelliJ
        │              │              │
       Vim           Emacs          outros
```

Todos compartilham o que o LSP já expõe: diagnostics, hover, completion,
references, rename, definition, formatting, code actions. **Nenhuma** regra
semântica específica por editor (R6/R9).

**LSP — capacidades hoje vs. alvo deste plano** (o LSP é o gargalo de
semântica; o provider só consome):

| Capability | `LspServer.java` hoje | Ação |
|---|---|---|
| `textDocument/completion` | presente | — |
| `textDocument/hover` | presente | — |
| `textDocument/rename` | presente | — |
| `textDocument/references` | presente | — |
| `textDocument/publishDiagnostics` | presente | — |
| `textDocument/definition` | **ausente** | adicionar (gap no LSP, não no provider) |
| `textDocument/documentSymbol` | ausente | adicionar (opcional, P2) |
| `textDocument/codeAction` | ausente | adicionar (opcional, P2) |
| `textDocument/formatting` | ausente (fmt é CLI) | expor via LSP (P2) |
| `workspace/executeCommand` | ausente | adicionar para `Kof: Build/Run/...` (§19) |

> Regra: se uma capability falta, é **gap no LSP** (reivindicar à parte), não
> algo para o provider "resolver" com parser próprio.

---

## 11. File association + workspace

`*.kof` → Kof em todos os editores. Workspace detectado por `kof.toml`
(`ProjectLocator` já sobe até ele); o editor usa source roots/dependencies/
targets/LSP/formatter/compiler/test runner a partir daí.

---

## 12. Status / diagnóstico

`kof editor status` imprime:

```
Kof Editor Environment
Kof:
  version / compiler: OK / LSP: OK / formatter: OK / debugger: PARTIAL
Editors:
  VS Code      integration: installed   LSP: connected
  Neovim       integration: installed   LSP: configured
  IntelliJ     integration: available   not installed
  Vim          integration: installed
```

`debugger` sempre `PARTIAL` até o DAP fechar (R6, nunca esconder).

---

## 13. Target selection + comandos do editor

Arquitetura **permite** (não exige UI complexa agora) escolher target
(`kof build --backend=jvm --frontend=kofjs`). Comandos equivalentes, via
mecanismo idiomático de cada editor:

```
Kof: Build / Run / Test / Check / Format / Serve / Start LSP / Select Target / Open Docs
```

**Terminal é soberano** (§20): a CLI sempre funciona; a integração é conveniência.

---

## 14. Debugger (DAP)

```
Editor → DAP → Kof Debug Adapter → runtime/JVM
```

Nada de debugger por editor. Usa `kof debug`/JDWP existente; **PARTIAL** até o
DAP fechar.

---

## 15. Testes

Cobrir: detection (presente/ausente/multi-version/PATH custom/Linux/macOS/
Windows), installation (install/já-instalado/incompatível/uninstall/update/
falha), configuration (LSP/file association/formatter/project root/exec path),
CLI (list/detect/status/setup/install/uninstall).

**Regra dura (§24):** testes usam fs temporário, mocks e fake editor
installations. **Nunca** instalam plugins reais na máquina da suíte.

---

## 16. Plataformas (arquitetura)

Linux x86_64/ARM64, macOS x86_64/ARM64, Windows x86_64. O detector é a única
ponta que conhece caminhos — isolá-la em `EditorDetector` para os 3 SO.

---

## 17. Extensibilidade

Adicionar editor = adicionar 1 `Provider` + 1 entrada no `EditorRegistry` +
teste. **Sem** tocar no core da CLI. Futuros: Sublime, Helix, Zed, Kate,
Eclipse, Fleet, Android Studio, Cursor, Windsurf.

---

## 18. Documentação

```
docs/editors/
├── overview.md
├── vscode.md  intellij.md  vim.md  neovim.md
├── geany.md   nano.md      emacs.md
```

Cada doc: instalação manual + automática, configuração, LSP, formatter,
debugging, troubleshooting. **E** atualizar `training/` (ensinar agentes/LLMs a
configurar ambiente Kof) + `learn/`.

---

## 19. Release gate (não "fechou" com só VS Code)

**Infra:** detector multiplataforma, registry, CLI `kof editor`, install,
uninstall, status, setup.
**Integrações:** VS Code, IntelliJ, Vim, Neovim, Geany, Nano, Emacs.
**Tooling:** `.kof` recognition, LSP, diagnostics, formatter, build, run, test.
**Qualidade:** instalação idempotente, sem alterar ambiente indevidamente,
testes automatizados, documentação, multiplataforma.

---

## 20. Ordem de implementação (degraus commitáveis)

> Cada degrau = 1 unidade coesa com prova (teste verde). O degrau 0 é
> **pré-condição** (gap no LSP, reivindicável à parte).

| # | Degrau | Entrega | Prova |
|---|---|---|---|
| 0 | LSP `definition` (+ optional: documentSymbol, codeAction, formatting, workspace/executeCommand) | `LspServer.java` | teste de LSP por capability |
| 1 | Abstração `EditorIntegration`/`EditorInfo`/`EditorRegistry`/`EditorDetector`/`EditorConfig` (sem provider concreto ainda) | infra em `cli/editor/` | teste do detector com PATH fake |
| 2 | `CmdEditor` + `list`/`detect`/`status` (read-only) | CLI | teste de saída por mock |
| 3 | `setup`/`install`/`uninstall`/`update` (idempotente + consent) | CLI + `EditorInstaller` | teste em fs temp (sem rede) |
| 4 | Provider VS Code (grammar+LSP+snippets+commands) | `editor/` + provider | teste de config gerado |
| 5 | Provider Neovim (lua LSP) | provider | teste de config gerado |
| 6 | Provider Vim (ftplugin+syntax+indent+compiler) | provider | teste de config gerado |
| 7 | Provider Emacs (`kof-mode`+eglot) | provider | teste de config gerado |
| 8 | Provider Geany (`.conf`) | provider | teste de config gerado |
| 9 | Provider Nano (`.nanorc`) | provider | teste de config gerado |
| 10 | Provider IntelliJ (plugin LSP4IJ + language) | provider | teste de config gerado |
| 11 | Installer hook (oferecer no install, §8) | installer | teste de fluxo |
| 12 | Docs `docs/editors/*` + `training/` + `learn/` | docs | revisão + CI lint |
| 13 | Gate final: suíte completa + release gate §19 | — | `mvn test` verde |

**Dependências:** 0 (LSP) é independente e pode ir primeiro/paralelo. 1→2→3 em
sequência. 4-10 (providers) independentes entre si depois do 3 (paralelizáveis,
um por agente). 11,12,13 no fim.

---

## 21. Limitações / decisões de design (regra 6 — NÃO decidir aqui)

- **IntelliJ plugin** exige build Gradle/IntelliJ Platform — é um subprojeto
  próprio; o "provider" só orquestra/empacota. Decidir escopo (P2?) à parte.
- **DAP** ainda PARTIAL — debug em editor fica `PARTIAL` até fechar.
- **`definition`/`codeAction` no LSP** = mudança de capability (aditiva);
  validar com a suíte de LSP antes.
- **Marketplace** (VS Code/IntelliJ) envolve publicação externa — fora do
  escopo do repo; a distribuição local (`editor/`) é o caminho sem rede.

---

## 22. Respostas exigidas ao final (contrato do briefing)

Ao concluir, informar: (1) arquivos alterados, (2) arquitetura criada,
(3) editores detectados, (4) integrações implementadas, (5) funcionalidades
por editor, (6) testes adicionados, (7) resultado da suíte, (8) limitações
restantes.
