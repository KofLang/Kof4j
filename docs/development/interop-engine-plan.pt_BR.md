# X2 — motor oficial `interop` (Python/R) — plano de implementação

**Status:** `EM DESENVOLVIMENTO` — claimado 26/09 pela lane compiler 9092 (no
mesmo commit desta doc). **Autoridade da decisão:** `D-COMPLETE-FIRST` item 2
(`DECISIONS.md` §`D-COMPLETE-FIRST`, mantenedora 26/09) — o PACOTE COMPLETO é a
decisão; stubs, fachadas e "gaps aceitos" não são opções (a regra 11 se aplica a
toda face que chega na superfície da linguagem).

**Contrato (verbatim da decisão):** marshalling bidirecional tipado
(`Int`/`Double`/`Bool`/`String`/`List`/`Map`/`record` ↔ JSON), gerenciamento real
de processo (spawn, stdin/stdout, timeout, exit, cancel), estado de sessão,
erros nomeados `INTEROP00x`, E2E por alvo, corpus sincronizado. Nasce
`experimental` (R5).

## Design KOF-first (medido 26/09, não memória)

- **Zero namespaces novos.** O namespace `interop` + HOST_IMPORT `kof.interop`
  já existem (X6, `D-INTEROP-REFLECT`; linha 68 do ledger
  `scripts/stdlib_boundary.txt`, layer `interop experimental`) — o motor
  ESTENDE isso. O gate `check_stdlib_boundary.sh` segue verde sem linha nova.
- **Motor escrito em Kof, não em Java** (precedente `D-KOF-AS-CLOUD`/
  `D-BOOTSTRAP` do `interop-host.kf`): o loop RPC, o marshalling e o estado de
  sessão vivem num host injetado pelo compilador
  (`dev/kof/interop-py-host.kf`), composto da plataforma que já existe (regra
  de ferro 2): `kof.process` (JVM `ProcessBuilder`, x86 `RuntimeProcess`
  pipe2/execvp, cross portas `Rt` — medidas vivas em todas as faces
  executáveis) + `kof.json` (encode/decode, mapas tagueados, determinismo de
  chaves ordenadas §106).
- **Codecs são fronteira, nunca fundação** (princípio X6 reusado): o
  marshalling de escalar/List/Map/record passa por
  `json.encode`/`json.decode` — sem parser manual (tabela de anti-padrões).
- **Diagnósticos nomeados (R6):** códigos livres medidos 26/09 —
  `INTEROP001`/`002` (schema X6) e `INTEROP003` (§510 estático externo
  não-JVM) estão tomados; o motor reclama **`INTEROP004`** (interpretador não
  encontrado no spawn — erro de runtime honesto, nunca resultado vazio
  silencioso), **`INTEROP005`** (alvo/face sem backing — recusa em
  compile-time, o padrão de gate do §510), **`INTEROP006`** (falha remota —
  erro do motor / traceback exposto, nomeado). Cada código ganha linha na
  matriz de paridade + pino no `DomainGapCodesTest` quando pousa.
- **Superfície (gate regra 11 antes de pousar):** o usuário escreve
  intenção — `py.call("area", r)` — não mecanismo (sem strings de argv, sem
  JSON manual, sem loops de reader em código do usuário). Os nomes exatos são
  congelados na fatia 1 junto da linha do training; a Lei da Simplicidade
  vale no commit da superfície.

## Fatias (cada uma um corte vertical COMPLETO com prova — nunca fachada)

| # | Fatia | Escopo da entrega COMPLETA | Prova |
|---|---|---|---|
| 1 | **Motor Python no JVM** | host `.kf` + wiring typer/lowerer; sessão = `python3 -u` de longa vida sobre `kof.process`; args tipados → linha JSON no **stdin**, resultado tipado ← linha JSON no stdout (RECON primeiro: medir a superfície de escrita no stdin do `kof.process` — se a API Kof não tiver, a fatia 1 estende o `kof.process` honestamente para TODOS os alvos dele, é trabalho de plataforma, não de interop); `INTEROP004` quando falta o interpretador; `INTEROP006` nomeia a falha remota | E2E com `assumeTrue(python3 presente)` (precedente node/qemu); round-trip por tipo incl. record; idempotência; arestas de erro |
| 2 | **Motor R** | a mesma máquina do host sobre `Rscript` (host não tem → guarda `assumeTrue`; disponibilidade no CI ubuntu medida na fatia) | E2E + binding da fonte via `interop.schema` existente (sinergia X6, zero reflexão nova) |
| 3 | **timeout / cancel / estado de sessão** | `INTEROP00x` nomeado para cada; sem hang silencioso (precedente §418 bounded wait) | E2E de arestas (hang, cancel, reuso) |
| 4 | **Faces Native / JS / Script** | MEDIR por face: porte real onde a plataforma dá backing, senão recusa `INTEROP005` em compile-time (JVM-first é R7, e o §510 provou que a face de recusa honesta é entrega completa do alvo) | goldens por alvo ou pinos de recusa |
| 5 | **Corpus + DoD de promoção** | `training/idioms/interop.md` EN+PT, seção do `learn/`, linhas da matriz de paridade, `ecosystem-coverage`, CHANGELOG | revisão de par do registro docs |

**Sem tocar:** arquivos IN PROGRESS de outras lanes (memory/, media cross);
PR #619 (regra 10).
