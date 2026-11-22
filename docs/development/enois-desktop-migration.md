[English](enois-desktop-migration.md) | [Português](enois-desktop-migration.pt_BR.md)

# Énois → cliente desktop nativo em Kof (migração 1:1)

last: OPENED 22/11 on the maintainer's order ("seu trabalho é fazer funcionar aqui uma migração de 1:1 do enois, reprodução fiel pra um cliente desktop funcional", target `enois.ia.br`). STATUS: UNDER DEVELOPMENT
doing: Fase 0 LANDED — the design TOKENS generated from the real CSS (`libs/enois/Tokens.kf`, 61 colours + radii) run on the native and match the site
next: Fase 1 — the app shell (window 1280×800, the `--background` surface, the sidebar with the brand mark) + the Feed route
location: repository
state: active

constraint:

* fidelity first: every colour, size and string is MEASURED from `enois-design-system/src/styles.css` or the live site — never invented
* the tokens are GENERATED (`scripts/gen-enois-tokens.py`), never hand-edited
* no KofJS in this migration (maintainer order 22/11): the desktop client is NATIVE
* a screen is "migrated" only with a native run that prints the values it painted
* the Énois routes are the contract: a Kof screen mirrors a route, and a missing route is a named gap, not a stub

---

## §1 What was MEASURED (22/11)

| fact | value |
|---|---|
| `enois-frontend` | 90 522 lines of TS/TSX, 1 319 files |
| `enois-design-system` | 96 074 lines of TS/TSX, 1 569 files |
| routes (`src/routes/*.tsx`) | **89** |
| theme tokens (`:root`) | 65 (61 colours, 22 of them OKLCH) |
| radius | `--radius: 1rem` = 16 px |
| brand primary | `#6746f5` → `(103, 70, 245)` |
| background | `#f7f4fb` → `(247, 244, 251)` |
| live site | `https://enois.ia.br` (TanStack Start SSR, CSP, Cloudflare Turnstile) |

The dependency map (what each Kof surface must replace):
React 19 + react-dom → `kof.ui` / the SDL3 canvas; TanStack Router (89 file-based routes)
→ the Kof screen registry; TanStack Query → `kof.http` + an app-level cache;
Tailwind CSS 4 → the token library + explicit layout; Radix UI → Kof dialogs/portals;
zustand → Kof class state; zod → Kof types; pdf.js / qrcode / Turnstile → named gaps.

## §2 The route inventory (89, the migration contract)

agenda · apoiar · auth.* · blog.* · blogs · boas-vindas · call.$callId · carteira ·
colagem · colecoes.* · comunidades.* · confessionario · conta · contato · denuncias ·
desabafos · desafios · developers · diretrizes · empresa.* · entrar · eventos ·
explorar · feed · freelances · games.* · grupos.* · index · jogos-diarios ·
marketplace · mensagens · mentoria · meus-servicos · moderacao.* · moderador ·
murais.* · notificacoes.* · papo-reto · par-de-estudo · penalidades · perfil.* ·
post.$postId · privacidade · profissional_.* · projetos.* · rede · responsavel ·
roleplay.* · salvos · seguranca · selos · servicos · silenciados · sobre ·
sugestoes · suporte · termometro · termos · vagas.*

## §3 Phases

**Fase 0 — tokens (LANDED 22/11).** `libs/enois/Tokens.kf` generated from the CSS:
61 colours (hex kept literal, OKLCH converted by the CSS Color 4 algorithm at
generation time, so the native runtime needs no trig — measured: `math.cos` is
`MATH001` on every native target), radii in px. Proven by a native run printing
`bg=247,244,251 / primary=103,70,245 / radius=16`.

**Fase 1 — the app shell.** A 1280×800 window painted `--background`, the brand
mark, the sidebar and the top bar — the chrome every route shares.

**Fase 2 — the Feed.** `feed.tsx` → a scrollable list of post cards.

**Fase 3 — the routes, in dependency order**: `perfil.*` → `post.$postId` →
`comunidades.*` → `mensagens` → the rest.

**Fase 4 — the backend bridge.** `kof.http` against the same API the site calls
(measured from `src/`), so the desktop client shows real content.

**Fase 5 — packaging.** `scripts/package-desktop.sh` (`.deb` + AppImage) and the
CI workflow that proves `dpkg -i` on ubuntu-latest.

## §4 How to finish

each phase lands with a native run that prints what it painted; the plan records
what is migrated, what is a named gap, and the next step. Promotion to `docs/`
only when the route list is covered or the remaining gaps are named.

## §5 Named gaps (measured, not assumed)

* `math.sin`/`cos` — `MATH001` on every native target (§621 in `KofMath`); the
  tokens avoid it by converting OKLCH at GENERATION time.
* pdf.js, qrcode, Cloudflare Turnstile — no Kof surface; each route that needs
  one gets a named gap entry here.
