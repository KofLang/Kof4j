[English](myself.md) | [Português](myself.pt_BR.md)

# Sessão lane security/connectors — contexto completo para migração (10/10/2026)

last: testing-platform PROMOVIDO para `docs/testing-platform.md` (+PT) 10/10 (ordem da mantenedora "pode finalizar o plano então, ja promover e vida que segue") — o marco §6 COMPLETO em todos os níveis (manifesto → gate → SPI → dois providers → API no nível Kof → E2E de browser real verde); todas as refs vivas atualizadas; gates verdes
doing: heartbeat ATIVO (sessão ses_fa0663f32ffeayCVex0XwIVxfz, 30 min, servidor 9092); a lane segue em `lab` (192.168.15.15:9092)
next: o push final do estado da promoção (roadmap §23 não menciona testing-platform — a linha de status já carrega); a fila: §602 (dona nativa/GC); as fatias futuras da plataforma (§11 fases 5-8) exigem NOVO claim por ordem da mantenedora
location: repository
state: STABLE (para a lane)

## O que EU sou
- lane security/connectors — owner = `192.168.15.15:9092` (o IP:PORTA é a identidade; o servidor opencode na porta 9092)
- frente graphics/gaming: CONCLUÍDA e PROMOVIDA (`docs/graphics-gaming.md`, 10/10)
- plano testing-platform: ASSUMIDO 10/10 (órfão da lane issues/tooling .30:9093), FINALIZADO no núcleo e PROMOVIDO (`docs/testing-platform.md`)

## Trabalho pousado nesta sessão (resumo por frente)

### graphics/gaming (COMPLETA, promovida)
- 17 módulos puros em `libs/game/`: Clock/Keys/Mouse/Pad/Window/Sprite/Draw/Trig/Tilemap/Audio/Wav/Video/Camera3d/Mesh/Material/Light3d/Scene3d — bateria 75/0F/0 skips (JVM/Script/Native x86-64/JS/riscv64/aarch64)
- FFmpeg LGPL vendido da fonte (9.0.2, SEM --enable-gpl; `scripts/provision-ffmpeg.sh`); probe FFI + frame readback E2E REAL
- primitivas peek/poke (`buffer.peek8/32/64` + `buffer.poke8/32/64`) — raw+Buffer, byte-parity JVM==Native, bounds trap honesto
- promocões: `docs/graphics-gaming.md` (+PT) 10/10 (todas as refs vivas atualizadas)

### kof-testing-platform (COMPLETA no núcleo, promovida)
- §4 unit API: assertions 1-6, §4.4 tabelas, §4.6 seams clock/random
- §5 harness: temp-dir/readiness, withDb, withServer, process/config faces
- §7 runner: discovery honesta, tags OR, gate de provider, timing/slowest, breakdown por nível com passed/failed reais (`levels:`)
- §6 browser COMPLETO: manifesto `kof-test.kofmd` → gate `kof test --tag browser` → SPI `BrowserProvider` → ChromeHeadlessProvider (zero-dep) + PlaywrightLibProvider (lib 1.49.0, dependência NORMAL no pom do kof-cli, 4º chat poll) → `kof.test.browser` (pacote virtual Kof) → **E2E REAL: Chromium 156 renderiza + PNG real**
- §12: TODAS as decisões resolvidas (4 chat polls: opt-in, manifesto kof-test.kofmd, ordem §11, syntax pousada, lib normal no pom)
- promoção: `docs/testing-platform.md` (+PT) 10/10

### outros pousos da sessão
- §554 + C2: tighten interop (SEM014) + `Result` nomeável
- §628: assertions do launcher aceitam SEM de compile-time
- §618: `Directory.delete()` recursivo no JS (opção B da mantenedora)
- §641: root attribution via C probe (o GC era inocente; a raiz real era under-allocation no hash — corrigido pela lane nativa, minha correção honesta registrada)
- correções próprias: o poke-alias faltante no PortuKofStdlibMembers (o padrão §642), runQemu colisão com a lane ativa (CEDÊNCIA — a lane .30:9092 estava viva)

## Lições medidas (para a próxima lane)
- `[]` em Kof é arrays-only (SEM054) — listas são `listOf(...)`
- o `ProjectLocator.locate` só procura kof.toml — leitores de manifesto próprios precisam de subida própria
- `shell.run` + `r.stdout`/`r.stderr`/`exitCode`/`shell.ok(r)` é o caminho para processos externos em Kof
- colisões de lane: o AGENTS manda preservar os DOIS lados; se a lane alheia pousou o MESMO fix, `rebase --skip` no meu commit duplicado
- o heartbeat: `scripts/auto-loop.sh start [sessionID] [intervalo-min] [porta]` — o `--port N` sozinho é lido como intervalo
- full-suite: ~50 min de parede (safe-suite.sh); os 13-14F são todos ambientais (UEFI 7 + RingPrivilege 4 + InteropTimeout flakes 2)
- gates: 21 `scripts/check_*.sh` rc=0 constantes; o cut 0.6.0 gated por #776 (TIER 15/WASI, dona .101:9092) + #779 (mantenedora)

## Estado do repo no momento da exportação
- branch `lab`, tip local == origin (0/0), trabalho todo empurrado via scripts/sync-push.sh
- fila viva: §602 (dona nativa/GC — NÃO minha)
- planos de docs/development: todos owned (image-vision .21:9092, test-architecture .30:9092, memory-safety, quality-pipeline)
- promovidos por mim e vivendo em `docs/`: graphics-gaming, testing-platform
