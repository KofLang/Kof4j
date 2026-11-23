[English](enois-desktop-migration.md) | [Português](enois-desktop-migration.pt_BR.md)

# Énois → cliente desktop nativo em Kof (migração 1:1)

last: OPENED 22/11 on the maintainer's order ("seu trabalho é fazer funcionar aqui uma migração de 1:1 do enois, reprodução fiel pra um cliente desktop funcional", target `enois.ia.br`). STATUS: UNDER DEVELOPMENT
doing: Fase 0+1 LANDED — the TOKENS (`libs/enois/Tokens.kf`, 61 colours + radii, generated from the CSS) AND the app SHELL (`libs/enois/Shell.kf`: the 44 px header, the 320 px aside, the `--background` content, the measured `h-11` / `gap-6` / `[1fr_320px]` grid) run on the native and match the site
next: Fase 3 continues — `blogs` (the publishing route) or `comunidades.$communityId.canais`
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

**Fase 3 — `comunidades.$communityId` (LANDED 22/11).** `libs/enois/Community.kf` is
the first screen with a REAL image: the cover is `h-48` = **192 px**
(`comunidades.$communityId.tsx:418`) painted from `loadImageTexture`, the
avatar `h-16 w-16` = **64 px** with its `ring-4` (line 430), the title
`text-2xl font-semibold` = **24 px** (line 439). With no cover the card falls
back to the site's own `--primary-soft` gradient tint. Proven natively:
`header_h=338`, `capa=112,…` (a pixel of the IMAGE, not a solid colour — a
vertical probe shows the gradient running 10 → 223 down the cover), and
`sem_capa=238,232,255` = `--primary-soft #eee8ff` for the fallback.
Battery **9/9**.

**Fase 3 — the avatar (LANDED 22/11).** `libs/enois/Avatar.kf` migrates
`UserAvatar`: the site's `getInitials` verbatim (first letter of the first and
last word, uppercased; a single word gives that letter — `user-avatar.tsx:11`),
the fallback `bg-violet-300` (#c4b5fd) on `text-violet-900` (#4c1d95), and a
REAL circle painted pixel by pixel with the radius law, so the corners show the
background. Proven natively: `i1=MS / i2=P / i3=AL / i4=RC`, `centro=196,181,253`
and `canto=0,0,0` (outside the circle).
The PHOTO path is circular too, in PURE KOF: the fix to known-bugs §1195 made
`Buffer` a nominal type, so `Avatar.maskCircle` is a Kof pixel loop over the
decoded buffer (`centro=128,127,180` from the image, `canto=0,0,0` outside).
One shared primitive now serves every image mask the app needs.

**Fase 3 — `mensagens` (LANDED 22/11).** `libs/enois/Messages.kf` is the inbox:
the header `p-4` with the title at `text-lg` = **18 px**, the two REAL tabs
(`SOCIAL_TABS = ["all","archived"]`, `mensagens.tsx:51`) labelled "Todas" and
"Arquivadas" (`texts.ts:3004`), and a conversation row = the CIRCULAR avatar
(the §1195 primitive) + the name + the last-message preview
(`conversation-item.tsx`). An empty list shows the site's own
"Nenhuma conversa ainda". Proven natively: `header_h=90`, `lista_h=128`,
`vazia_h=64`, `header=255,255,255`, `avatar=196,181,253` (the `bg-violet-300`
inside the circle) and `canto=255,255,255` (the card outside it).
Battery **11/11**.

**Fase 3 — the community FEED (LANDED 22/11).** `libs/enois/CommunityFeed.kf` is the
first screen that COMPOSES the primitives: the content column + the aside.
Its grid is the community's OWN: `[minmax(0,1fr)_280px] gap-6`
(`comunidades.$communityId.tsx:875`) — **280 px** of aside, where the GLOBAL
feed uses 320. Content width at 1280 is therefore **944**, not the 904 of the
home feed; that difference is measured in both sources, not guessed. The
section title "Feed" is `text-lg` = 18 px (877), the empty state is
`surface-card p-8 text-sm` with the site's own texts (2332/2333), and the cards
are separated by `space-y-4` = 16 px. Proven natively: `conteudo_w=944`,
`vazio_h=112`, `com_post_h=128`, `aside_x=984`, `card=255,255,255`.
Battery **12/12**.

**Fase 3 — `explorar` (LANDED 22/11).** `libs/enois/Explore.kf` is the hub of rooms.
Its DATA is extracted from the code with no invention: the items and their
sections from `src/components/explore-items.ts` (**13 / 2 / 3 / 5 = 23**), the
labels and descriptions from `texts.explore.items`, and the section ORDER from
`texts.explore.sections` (community, play, build, work — the source's own
comment says the key order IS the section order). Measures from the
design system `ExploreHub`: icon `h-10 w-10` = 40 px, label `text-[15px]`,
description `text-[13px]`, container `max-w-3xl` = 768, section title
`text-[12px]`. Proven natively: `secoes=4`, `s0=Comunidade:13`,
`s1=Brincar:2`, `s2=Construir:3`, `s3=Trabalho:5`, `total=23`,
`primeiro=Colagem`, `ultimo=Mentoria`, `card=255,255,255`,
`icone=238,232,255`.
Battery **13/13**.

**Fase 3 — the community MEMBERS section (LANDED 22/11).** `CommunityMembers` in
`libs/enois/CommunityFeed.kf` paints the aside block measured in
`comunidades.$communityId.tsx:898-906`: the `surface-card p-5` block, the
`h3 mb-3 text-sm font-semibold` heading with `membersTitle(count)` —
**"Membros (N)"**, a FUNCTION in `texts.ts:2334`, not a literal — and each row
with the `h-9 w-9` = **36 px** circular avatar, the name at
`text-sm font-medium` and the "ADMIN" badge. Proven natively: `membros_h=162`
(`p-5` ×2 + the heading + the gap + 2 × (36 + 12)), `vazia_h=66`,
`card=255,255,255`, `avatar=196,181,253`.
Battery **14/14**.

**Fase 3 — `vagas` (LANDED 22/11).** `libs/enois/Jobs.kf` paints the jobs listing
shell: the header `surface-card p-4 sm:p-6` with the title "Oportunidades" at
`text-2xl` = 24 px and the subtitle at `text-sm`, the search row carrying the
site's own placeholder "Buscar vagas (ex.: java -estágio)", and the filter
chips at `h-9` = 36 px with `border-border-soft`; the chip labels use the REAL
values from `texts.jobs.filters` (Remoto/Híbrido/Presencial,
Júnior/Pleno/Sênior/Liderança) plus `clearFilters` = "Limpar filtros".
Proven natively: `header_h=146` (24+24+8+14+16+36+24), `filtros_h=52`,
`card=255,255,255`, `borda=229,224,235` (`--border-soft #e5e0eb`),
`vazio=255,255,255` (the card where no chip is).
Battery **15/15** — the first tick since the avatar round with NO sampling
mistake, because every sample point was computed from the layout before the
assertion was written.

**Fase 3 — the job CARD (LANDED 22/11).** `JobCard` in `libs/enois/Jobs.kf` paints a
result card with the numbers measured in the `visibleJobs.map` of `vagas.tsx`:
`surface-card flex flex-col p-5`, the avatar `h-12 w-12 rounded-xl` = **48 px
with radius 12** — a ROUNDED SQUARE, not a circle (the USER avatar is the
circle; the job avatar is `rounded-xl`, and the code says so) — the title
`font-semibold`, the company at `text-sm text-muted-foreground`, and the chips
at `px-2.5 py-1 text-xs` with the match chip on `--primary-soft`.
Proven natively: `card_h=122` (20+48+12+22+20), `avatar=238,232,255`,
**`canto=255,255,255`** (the radius-12 cut showing the card through the corner),
`pilula=238,232,255`.
Battery **16/16** — the second tick in a row with every sample coordinate
computed from the layout before the assertion was written.

**Fase 3 — `vagas.$jobId` (LANDED 22/11).** `JobDetail` in `libs/enois/Jobs.kf` paints
the job detail: the container `mx-auto w-full max-w-2xl space-y-4` = **672 px
centred**, the back link "Voltar para vagas", the `surface-card p-4 sm:p-6`
header with the `h-12 w-12 rounded-xl` avatar on `--surface-1`, the title at
`text-xl` = 20 px, the company at `text-sm text-text-subtle`, and the
"Sobre a vaga" block below. Proven natively: `x=114` (the centred column in a
900 px window), `avatar=249,248,255` (`--surface-1 #f9f8ff`),
`card=255,255,255`, **`gap=247,244,251`** (the `space-y-4` interval showing the
background between the two blocks).
Battery **17/17** — third tick in a row with every sample coordinate computed
from the layout before the assertion.

## §4 How to finish

each phase lands with a native run that prints what it painted; the plan records
what is migrated, what is a named gap, and the next step. Promotion to `docs/`
only when the route list is covered or the remaining gaps are named.

## §5 Named gaps (measured, not assumed)

* `math.sin`/`cos` — `MATH001` on every native target (§621 in `KofMath`); the
  tokens avoid it by converting OKLCH at GENERATION time.
* pdf.js, qrcode, Cloudflare Turnstile — no Kof surface; each route that needs
  one gets a named gap entry here.
