[English](enois-desktop-migration.md) | [Português](enois-desktop-migration.pt_BR.md)

# Énois → cliente desktop nativo em Kof (migração 1:1)

last: OPENED 22/11 on the maintainer's order ("seu trabalho é fazer funcionar aqui uma migração de 1:1 do enois, reprodução fiel pra um cliente desktop funcional", target `enois.ia.br`). STATUS: UNDER DEVELOPMENT
doing: Fase 0+1 LANDED — the TOKENS (`libs/enois/Tokens.kf`, 61 colours + radii, generated from the CSS) AND the app SHELL (`libs/enois/Shell.kf`: the 44 px header, the 320 px aside, the `--background` content, the measured `h-11` / `gap-6` / `[1fr_320px]` grid) run on the native and match the site
next: Fase 3 continues — `comunidades.*` (the community route, on top of the card + feed + tabs)
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

**Fase 3 — `perfil.$username` (LANDED 22/11).** `libs/enois/Profile.kf` is the
FIRST ROUTE screen, built on the shell: the banner is `.cos-card__banner`
= **120 px** (`styles.css:3503`), the avatar `.cos-card__avatar` = **100 px**
(`styles.css:4229`) half over the banner, the name `.cos-card__name` =
**25 px / 800** (`styles.css:4297`), the meta at 14 px and the bio at
`.cos-card__bio` 14 px (`styles.css:4323`). Proven natively: `header_h=271`,
`banner=238,232,255` (`--primary-soft`, #eee8ff), `body=255,255,255`
(`--surface-card`), `tinta=49`.
NAMED GAPS of this screen: the avatar is an APPROXIMATE circle (a square inset;
the exact anti-aliased circle needs a mask slice), the cover image is the
`--primary-soft` fill (no image loader yet), and the profile `look` theming
(`cos-card__*` CSS variables) is not migrated.

**Fase 3 — `post.$postId` (LANDED 22/11).** `libs/enois/PostDetail.kf` paints the
post detail on top of the card: the back link at `text-sm` = 14 px in
`--muted-foreground`, the card `surface-card p-5 sm:p-8` = **32 px** pad at
desktop width, the title `text-lg` = **18 px** and the body `text-base` with
`leading-relaxed` = **26 px** line (`post.$postId.tsx`). Proven natively:
`card=255,255,255` (`--surface-card`), `fundo=247,244,251` (`--background`
above the card), `tinta=174`.
The Énois battery is **6/6** (tokens, shell, text, card, feed, profile, detail).

**Fase 3 — the profile tabs strip (LANDED 22/11).** `libs/enois/ProfileTabs.kf`:
six tabs in the component's order (`ALL, POSTS, REPLIES, REPOSTS, MEDIA,
DIARIO`, `profile-content-tabs.tsx:8`) with the REAL labels from
`src/messages/defaults/profile.ts:345` ("Todos", "Posts", "Respostas",
"Reposts", "Mídia", "Diário"), `px-2 py-3 text-sm font-medium` = **14 px**, the
active indicator `inset-x-0 bottom-0 h-1 rounded-full bg-primary` = a 4 px bar
in `--primary`, and the `REPLIES` tab painted dimmed for a non-owner (the
component's own `aria-disabled`/`disabled`). Proven natively: `tabs_h=48`,
`indicador=103,70,245` (`--primary`), `fundo=247,244,251`, `tinta=173`.
Battery **7/7**.

**Images and real colour (LANDED 22/11).** `Sdl3Backend.loadImageTexture` decodes
PNG/PNM/BMP/QOI/GIF/WebP through the PURE-Kof `image` library (no external codec)
into an SDL texture, and `1:1`/scaled draws now give the source pixels exactly.
Two compiler-independent bugs were found and fixed on the way: the texture
format was the BIG-ENDIAN `RGBA8888` value, so every channel came back swapped
(§1193), and the glyph rasterizer hard-coded white, so text was invisible on
the white card (§1194). `Font.drawTextColoured` now carries the colour, and
every Énois call passes its own TOKEN. Battery **8/8**, with an image test
asserting a real PNG pixel against the ImageMagick oracle.
NAMED GAP remaining: JPEG stays interop-first (`imageio` on the JVM only) —
the pure-Kof decoder covers PNG/PNM/BMP/QOI/GIF/WebP.

## §4 How to finish

each phase lands with a native run that prints what it painted; the plan records
what is migrated, what is a named gap, and the next step. Promotion to `docs/`
only when the route list is covered or the remaining gaps are named.

## §5 Named gaps (measured, not assumed)

* `math.sin`/`cos` — `MATH001` on every native target (§621 in `KofMath`); the
  tokens avoid it by converting OKLCH at GENERATION time.
* pdf.js, qrcode, Cloudflare Turnstile — no Kof surface; each route that needs
  one gets a named gap entry here.
