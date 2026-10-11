[English](myself.md) | [Português](myself.pt_BR.md)

# Sessão lane security/connectors — contexto completo para migração (10/10/2026)

last: testing-platform PROMOVIDO para `docs/testing-platform.md` (+PT) 10/10 — o marco §6 COMPLETO em todos os níveis; graphics-gaming PROMOVIDA para `docs/graphics-gaming.md` — 17 módulos + FFmpeg LGPL + peek/poke
doing: heartbeat ATIVO (sessão ses_fa0663f32ffeayCVex0XwIVxfz, 30 min, servidor 9092); lane em `lab` (192.168.15.15:9092)
next: fila viva: §602 (dona nativa/GC — NÃO minha); fatias futuras da plataforma (§11 fases 5-8) exigem NOVO claim
location: repository
state: STABLE

## Quem sou
- lane security/connectors — owner = `192.168.15.15:9092` (a identidade absoluta é IP:PORTA)
- graphics/gaming: CONCLUÍDA e PROMOVIDA (`docs/graphics-gaming.md`)
- testing-platform: ASSUMIDA (órfã da .30:9093), FINALIZADA no núcleo e PROMOVIDA (`docs/testing-platform.md`)

## Pousos principais
- graphics: 17 módulos `libs/game/` (bateria 75/0F), FFmpeg LGPL vendido (probe+readback E2E REAL), peek/poke com paridade byte-a-byte
- testing-platform: §4 unit completo, §5 harness JVM-completo, §7 runner (discovery/tags/gate/timing/níveis), §6 browser completo (manifesto `kof-test.kofmd` → gate → SPI → ChromeHeadless + PlaywrightLib 1.49.0 dependência NORMAL → `kof.test.browser` → **E2E REAL: Chromium 156 renderiza + PNG**), §12 sem decisões abertas
- outros: §554+C2, §628, §618 (opção B), §641 root attribution, correções próprias (poke-alias, runQemu CEDÊNCIA)

## Lições medidas
- `[]` é arrays-only (SEM054) — listas: `listOf(...)`
- `ProjectLocator.locate` só procura kof.toml — manifesto próprio precisa de subida própria
- `shell.run` + `r.stdout`/`stderr`/`exitCode`/`shell.ok` para processos externos
- colisão de lane: preservar os DOIS lados; fix duplicado → `rebase --skip`
- heartbeat: `auto-loop.sh start [sessionID] [min] [porta]` — `--port N` sozinho vira intervalo
- full-suite ~50 min; 13-14F todos ambientais; cut 0.6.0 gated por #776+#779 (com donas)

## Estado na exportação
- branch `lab` 0/0 (tudo empurrado); fila: §602 (dona nativa/GC); planos todos owned
- promovidos por mim: `docs/graphics-gaming.md`, `docs/testing-platform.md`
