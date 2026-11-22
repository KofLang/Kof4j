[English](enois-desktop-migration.md) | [Português](enois-desktop-migration.pt_BR.md)

# Énois → cliente desktop nativo em Kof (migração 1:1)

last: OPENED 22/11 on the maintainer's order ("seu trabalho é fazer funcionar aqui uma migração de 1:1 do enois, reprodução fiel pra um cliente desktop funcional", target `enois.ia.br`). STATUS: UNDER DEVELOPMENT
doing: Fase 0+1 LANDED — the TOKENS (`libs/enois/Tokens.kf`, 61 colours + radii, generated from the CSS) AND the app SHELL (`libs/enois/Shell.kf`: the 44 px header, the 320 px aside, the `--background` content, the measured `h-11` / `gap-6` / `[1fr_320px]` grid) run on the native and match the site
next: Fase 3 — the routes: `perfil.$username` (the first route screen on top of the shell + feed primitives)
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

**Fase 1 — the app shell (LANDED 22/11).** `libs/enois/Shell.kf`: a 1280×800 window
painted `--background`, the 44 px header (`h-11`), the 320 px aside and the
`[minmax(0,1fr)_320px] gap-6` content grid — the chrome every route shares.
Proven natively: `top=60` (44+16), `fundo=247,244,251`, `header=255,255,255`,
`aside=255,255,255`. The same unit fixed `Sdl3Backend.fillRect`, which wrote
INTEGERS into the `SDL_FRect` (four FLOATS) and painted NOTHING — measured
`dentro r=0` before, `r=255` after (the earlier fix covered `drawTexture` only).

**Fase 2 — the Feed.** `feed.tsx` → a scrollable list of post cards.

**Fase 2 — the post card (LANDED 22/11).** `libs/enois/PostCard.kf` paints one
post card with the site's measured numbers: `--surface-card` fill, a 1 px
`--border-soft` edge, `rounded-xl` = 20 px (`calc(1rem + 4px)`, `styles.css:35`),
`p-4` = 16 px padding (`post-card.tsx:100`), the author at `text-xs` = 12 px and
the body at `text-base` = 16 px in REAL Manrope. Wrapping is measured per word
(`Font.measure`), so the line breaks follow the glyph advances, not a guess.
Proven natively: `card=255,255,255`, `fundo=247,244,251`, `tinta=6303` — the
author and body painted real ink inside the card.

**Fase 2 — the feed screen (LANDED 22/11).** `libs/enois/Feed.kf` paints the
composer and the card list in the shell's content column: the composer is
`h-[3.625rem]` = **58 px** with `--surface-card` and the placeholder
`"Escreva sua publicação"` (`texts.feed.composer.placeholder`) at `text-[15px]`
in `--text-date`; the cards stack with the `gap-6` = 24 px of `feed.tsx:1011`.
`Feed.lastCardHeight` exposes the MEASURED height of the last card, so callers
never hard-code it (a test that did broke on the first card's real wrap).
Proven natively: `composer_h=58`, `pill=255,255,255`, `card=255,255,255`,
`gap=247,244,251` (the `--background` showing through the gap).

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
