# Nomenclatura de Releases — Kof

**Última atualização:** 30/08/2026
**Versão atual:** 0.2.6-beta

---

## Formato da versão

```text
MAJOR.MINOR.PATCH-<estágio>
0.2.6-beta
```

- `MAJOR.MINOR.PATCH` — semver padrão (`pom.xml` + arquivo `VERSION`).
- **Estágio** — a **fase** (Alpha, Beta, RC, Stable) é sufixo; o **codinome** da
  release vem da lista abaixo (futuro: `1.0.0-chevette`).

## Modelo de branches e salvaguardas de release

```text
main        → linha de release ESTÁVEL. Cada push dispara release.yml.
beta-X.Y    → linha de release EM DESENVOLVIMENTO (bump aqui, não na main).
feature/*   → branches de trabalho dos agentes (nunca carregam VERSION).
```

**Regras (não-negociáveis — ver `AGENTS.md` → "Salva-guardas"):**

1. Só a mantenedora (ou o `release.yml`) mexe em `VERSION` e na `main`.
2. Agentes **nunca** mergeiam `main`/`beta-*` para dentro da própria branch
   (isso puxa o `VERSION` e contamina o merge). Sincronizar = `git cherry-pick`
   dos commits de código, nunca um merge de branch de release.
3. `guard-version.yml` falha qualquer PR que mude `VERSION`.

> **Incidente 05/09:** um agente mergeou `beta-0.3.0 → planning-future` e,
> depois, `planning-future → main`. O `VERSION` 0.3.0-beta vazou para `main`
> e o `release.yml` publicou a minor em desenvolvimento antes da hora
> (irreversível). Estas regras existem para impedir a repetição.

## Estágios

| Estágio | Significado | Status |
|---------|-------------|--------|
| **Alpha** | Funcionalidades em construção, breaking changes esperadas, cobertura parcial | ✅ concluída |
| **Beta** | Feature-complete nos alvos principais; quebras pontuais antes de 1.0 | ✅ **atual** |
| **RC** | Paridade total dos targets, só correções de bug | futura |
| **Stable** | 1.0 — compatibilidade garantida entre releases | futura | Releases ganham nome de codinome, em ordem alfabética, a partir do `Chevette` (ex.: `1.0.0-chevette`).

---

## Codinomes (lista completa, em ordem de uso)

Cada release de destaque ganha um codinome, em ordem alfabética — a lista já
está definida de uma vez. Ao esgotar a lista, recomeça ou estende (decisão
futura).

| # | Codinome | Uso |
|---|----------|-----|
| 1 | Alpha | ✅ usada (fase inicial) |
| 2 | Beta | ✅ usada (fase atual) |
| 3 | Chevette | primeira release pós-Beta |
| 4 | Diplomata | |
| 5 | Escort | |
| 6 | F-1000 | |
| 7 | Gol | |
| 8 | Hobby | |
| 9 | Idea | |
| 10 | Jeep | |
| 11 | Kadett | |
| 12 | Logus | |
| 13 | Monza | |
| 14 | Niva | |
| 15 | Omega | |
| 16 | Opala | |
| 17 | Parati | |
| 18 | Quantum | |
| 19 | Rekord | |
| 20 | Santana | |
| 21 | Tempra | |
| 22 | Uno | |
| 23 | Verona | |
| 24 | W8 | |
| 25 | XR3 | |
| 26 | Ypsilon | |
| 27 | Zafira | |

---

## Regras

1. **Codinome por release de destaque** — nem todo bump de patch ganha nome
   (`0.2.6-beta` → `0.2.6-beta` não tem codinome; um corte marcante tipo
   "Fase 6 Router + Fase 9 diffing fechadas" ganha).
2. **`Alpha` e `Beta` já foram consumidos como fase**, não como codinome de
   release específica — a lista de codinomes efetivamente começa no
   `Chevette`.
3. **Ordem é alfabética e fixa** — sem pular, sem reordenar, sem reservar
   ("o Omega vai pra 1.0" não existe).
4. O codinome aparece no `VERSION`, no changelog e no `kof version`
   (ex.: `kof 1.0.0 (chevette)`).
