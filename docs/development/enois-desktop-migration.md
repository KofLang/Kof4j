[English](enois-desktop-migration.md) | [Português](enois-desktop-migration.pt_BR.md)

# Énois → cliente desktop nativo em Kof (migração 1:1)

last: OPENED 22/11 on the maintainer's order ("seu trabalho é fazer funcionar aqui uma migração de 1:1 do enois, reprodução fiel pra um cliente desktop funcional", target `enois.ia.br`). STATUS: UNDER DEVELOPMENT
doing: Fase 0+1 LANDED — the TOKENS (`libs/enois/Tokens.kf`, 61 colours + radii, generated from the CSS) AND the app SHELL (`libs/enois/Shell.kf`: the 44 px header, the 320 px aside, the `--background` content, the measured `h-11` / `gap-6` / `[1fr_320px]` grid) run on the native and match the site
next: in `enois-kof-desktop` — the SESSION BLOCKER above needs the maintainer's contract call (cookie/bearer for a native client); the recruitment board is IN — meanwhile the remaining catalogues; every NEW screen starts by measuring the live route with `scripts/measure-site.mjs`; Kof4j keeps `libs/game` + the compiler and records the Kof-side bugs (§1196, §1197); §1196 and §1197 stay OPEN
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

**Fase 3 — `comunidades.$communityId.canais.$channelId` (LANDED 22/11).** `ChannelScreen`
in `libs/enois/Channel.kf` paints the channel: the container
`mx-auto w-full max-w-2xl space-y-4 py-6` = **672 px** with 24 px of vertical
padding, the back link "Voltar para a comunidade", the `surface-card p-5`
header with the `h-10 w-10 rounded-full` icon on `--primary-soft`, the empty
state "Nada por aqui ainda. Comece a conversa.", and the composer with
"Escreva para este canal..." — every string the site's own
(`texts.ts` block `channelPage`, which also carries the three access states
`private`/`notMember`/`error`, named here as the next slice's work).
Proven natively: `x=114` (the centred column), `icone=238,232,255`
(`--primary-soft`), `card=255,255,255`, `canal_h=280`.
Battery **18/18** — fourth tick in a row with every sample coordinate computed
from the layout.

**Fase 3 — the channel's three ACCESS states (LANDED 22/11).** `ChannelAccess` in
`libs/enois/Channel.kf` paints the states the site distinguishes with colours
that MEAN something: `private` on `--primary` (**#6746f5**), `error` on
`--destructive` (**#b30018**), and `notMember` with the "Entrar na comunidade"
button on `--primary-soft`. The common container is
`surface-card space-y-3 p-5` = 20 px and the message `text-sm text-text-subtle`
= 14 px. Each state carries its own 4 px accent bar in the role's colour —
the colour is not decoration, it is the distinction between "you lack access"
and "something failed".
Proven natively: `privado_h=54`, `visitante_h=102` (the button adds 12 + 36),
`erro_h=54`, `privado=103,70,245`, `erro=179,0,24`, `botao=238,232,255`.
Battery **19/19** — fifth tick in a row with every sample coordinate computed
from the layout.

**Fase 3 — `blogs` (LANDED 22/11).** `BlogsScreen` in `libs/enois/Blogs.kf` paints the
blog DIRECTORY — "Cantinhos de quem escreve por aqui", not a feed. The header
carries the title "Blogs" and the site's own description (`texts.ts` block
`blog`), word-wrapped by MEASURED glyph advances (`BlogsScreen.wrapText`); each
card is `surface-card p-4 sm:p-5` = 120 px with the eyebrow
`text-xs uppercase tracking-widest text-primary`, the title `text-base
font-semibold` and the description `text-sm`. The empty states
(`emptyDirectory`, `searchEmpty`) and the search placeholder are the site's own
strings.
PROOF `EnosisBlogsE2ETest`: `header_h=62`, `card_h=120`, `card=255,255,255`,
and the eyebrow EXISTING in the `--primary` family.
NAMED DIVERGENCE recorded while writing the proof: a 12 px glyph has
ANTI-ALIASED edges, so the first non-white pixel of the uppercase eyebrow is a
BLEND of `--primary` and the card (`154,132,248`, measured) — the test asserts
the ROLE (blue dominates red) instead of a pixel value a fixed scan could not
guarantee.
Battery **20/20**.

**Fase 3 — `blog.$username` (LANDED 23/11).** `BlogScreen` in `libs/enois/Blog.kf`
paints ONE blog: the cover `h-64 w-full` = **256 px** in a `rounded-2xl` box
(the radius-16 corners cut back to the `--background`, proven by the corner
pixel), the `surface-card` header `p-5 sm:p-6` with the `text-2xl` title and
the meta line, and the "not found" state `surface-card p-10 text-center` with
"Blog não encontrado." and "Ver todos os blogs".
THE SITE'S TEXT FUNCTIONS ARE MIGRATED AS FUNCTIONS: `postsCount(count)` gives
**"1 post"** but **"3 posts"** (the plural is part of the contract), and
`readingTime(min)` gives "N min de leitura estimada".
Proven natively: `c1=1 post`, `c3=3 posts`, `rt=5 min de leitura estimada`,
`capa_h=256`, `capa=116,59,180` (a pixel of the cover IMAGE), `canto=247,244,251`.
DIVERGENCE caught by the test: I rounded the projected source pixel by hand
(110.599 → 117) and the real value is 116 — the value now comes from the
FORMULA, not from my arithmetic.
Battery **21/21**.

**Fase 3 — `grupos.$slug` (LANDED 23/11).** `GroupScreen` in `libs/enois/Groups.kf`
paints ONE group: the `max-w-3xl` = **768 px** column, the `h-40` = **160 px**
banner painted from a REAL image, the `h-12 w-12 rounded-xl` logo — a ROUNDED
SQUARE like the job card, not the circular user avatar — the `text-xl` title
and the `bg-primary-soft text-primary` mode chip.
THE CONTRACT OF THIS SCREEN IS ITS TEXT VARIATION, and the test asserts every
branch: `members(1)` = **"1 membro"** but `members(5)` = **"5 membros"**;
`joinModeLabel` gives **"Entrada aberta"** vs **"Entrada por aprovação"**; and
`actionLabel` gives **"Entrar no grupo"** / **"Pedido pendente"** /
**"Você participa"**. A hard-coded string in any of the three would fail.
Proven natively: `m1=1 membro`, `m5=5 membros`, both modes, all three actions,
`x=66`, `grupo_h=338`, `banner=98,95,180` (a pixel of the image, asserted NOT
to be the `--primary-soft` fallback), `logo=238,232,255`.
Battery **22/22**.

**Fase 3 — `eventos` (LANDED 23/11).** `EventsScreen` in `libs/enois/Events.kf`
paints the community AGENDA: the grid `grid-cols-1 gap-4 md:grid-cols-2
lg:grid-cols-3` — **3 columns on desktop** — with each card
`surface-card space-y-3 p-4` = 16 px carrying the title, the date and the
format. The formats are the site's own (`formats.ONLINE` "Online",
`IN_PERSON` "Presencial", `HYBRID` "Híbrido") and the empty state is
"Nenhum evento por aqui ainda.".
THE PROOF OF THE GRID IS THE GEOMETRY, not a count: the column width comes
from `(904 - 2×16) / 3` = **290**, and the test samples the SECOND and FOURTH
cards at one column + the gap to the right — if the layout collapsed to a
single column, those samples would land on the background instead of the card.
Proven natively: `col_w=290`, `f=Online|Presencial|Híbrido`, `grade_h=296`
(2 rows × 140 + 16), `card1`/`card2`/`card4` = `255,255,255` and
`gap=247,244,251`.
Battery **23/23**.

**Fase 3 — `salvos` (LANDED 23/11).** `SavedScreen` in `libs/enois/Saved.kf` uses the
design system's `PageHeader` numbers (`surface-card mb-4 p-4 sm:p-6` = 24 px
with 16 px below, title `text-2xl` = 24 px, icon `h-5 w-5 text-primary` =
20 px) and the site's own strings.
THE CONTRACT IS A DOUBLE PLURAL, and the test asserts all three cases:
`count(0)` = "0 **posts salvos**", `count(1)` = "1 **post salvo**",
`count(7)` = "7 **posts salvos**" — BOTH suffixes change together, so a
hard-coded one would fail on the other.
Proven natively: `c0`, `c1`, `c7`, `vazio=Nenhum post salvo ainda.`,
`icone=103,70,245` (`--primary`), `card=255,255,255`,
`gap=247,244,251` (the `mb-4` band).
DIVERGENCE caught while writing the proof: I first sampled the `mb-4` gap at
y=140, but the header ends at **146** (`60 + 24×2 + 20 + 14 + 4`) — the sample
was INSIDE the header, not the gap. The band was recomputed before the test
was written.
Battery **24/24**.

**Fase 3 — `selos` (LANDED 23/11).** `SealsScreen` in `libs/enois/Seals.kf` paints the
contribution badges page: the `max-w-3xl` = **768 px** column with `space-y-8`
(32 px between sections) and `sm:py-14` (56 px), the eyebrow
`text-xs font-semibold uppercase tracking-widest text-primary`, the title at
`sm:text-4xl` = **36 px**, the intro at `text-base leading-relaxed`, and the
category blocks for CODE/ISSUES on `--primary-soft`.
THE CONTRACT IS `tierRange(min, max)` WITH THREE FORMS, and the test asserts
all: `tierRange(10, -1)` = **"10+"** (open end), `tierRange(5, 5)` = **"5"**
(single), `tierRange(3, 9)` = **"3–9"** with an **EN DASH (U+2013)** — the test
also asserts the ASCII hyphen is NOT there.
Battery **25/25**.
DIVERGENCE that changed HOW the proof is written: a scan for "not white" stops
immediately, because the PAGE BACKGROUND `247,244,251` is not white either —
the first version found the background at (66,72) and called it the eyebrow.
The scan now looks for the INK of `--primary` (blue high, red low, and blue at
least 60 above red), which NEITHER the background nor white can match.

**Fase 3 — `notificacoes.index` (LANDED 23/11).** `NotificationsScreen` in
`libs/enois/Notifications.kf` paints the notices list: the `max-w-2xl` =
**672 px** column, each item `px-4 py-3.5` with the `h-11 w-11` = **44 px**
circular icon on `--primary-soft`, the name `font-extrabold` and the body
`text-text-subtle`. The day labels are the site's own: **"Hoje"** / **"Ontem"**
(`notificationsPage.today`/`yesterday`).
THE CONTRACT IS THE GROUPING, and it is proven by the HEIGHT, not by counting
calls: a day label appears only when the group CHANGES, so three items in TWO
groups give **274** px (216 + 2×29) and three items in ONE group give **245**
px (216 + 29). A per-item label would give 303 and fail both assertions.
Battery **26/26**.

**Fase 3 — `desafios` (LANDED 23/11).** `ChallengesScreen` in `libs/enois/Challenges.kf`
paints the challenge list: the card `space-y-3 rounded-2xl border border-border
p-4` (radius 16, proven by the corner cut), the title `text-base
font-semibold` and the body `text-sm`.
THE CONTRACT IS THE COMPOSER'S LIMITS, and they were MEASURED in
`desafios-composer.tsx:11-15`: `MAX_TITLE = 120`, `MAX_DESCRIPTION = 3000`,
`MAX_DAYS = 31`, plus the `contentTooLongError` text's **2000**. The site's
REFUSALS interpolate those numbers, so the test compares the COMPLETE message
("O título aceita no máximo 120 caracteres." / "O prazo precisa ser uma data
futura (até 31 dias).") — changing a limit breaks the message. The WEIGHT is
contract too: the subtitle says "Autoria nominal, sem ranking e sem prêmio",
and `hasRanking()` returns `false`.
Battery **27/27**.

**Fase 3 — `mentoria` (LANDED 23/11).** `MentoringScreen` in `libs/enois/Mentoring.kf`
paints the hero with the numbers MEASURED in `mentoria.tsx`: the card
`surface-card p-5 sm:p-8` = **32 px**, the badge on `--primary-soft`, the title
`text-3xl` = **30 px**, the `mt-2` description and the `mt-5` pill CTAs —
"Encontrar mentor" / "Tornar-me mentor".
THE TITLE WRAP IS MEASURED, and that is the finding: the title occupies
**744 px** at 30 px and the card's inner width is `868 − 2×32` = **804**, so it
fits on ONE line by 60 px. The hero height then pins that wrap from the other
side: the REAL site description (2 lines at 16 px) makes the hero **254**,
and a shorter one would make it 228 — which the test proved when I first wrote
it with a trimmed description.
Battery **28/28**.

**Fase 3 — `mentoria` list (LANDED 23/11).** `MentorListScreen` and
`MentorListMetrics` in `libs/enois/MentorList.kf` add the mentor list to the hero
already shipped: the featured block (`text-lg` 28, description at 20, the search at
`mt-4` + 40, the grid `gap-4` in 2 columns), the mentor card `surface-card p-5
space-y-4` (avatar 56, name 24, headline 16) and the 2×2 stats box (`p-3`, 12).
Texts are the real `texts.mentoring` (`featured`, `featuredDescription`,
`searchPlaceholder`, `searchEmpty`, `loadError`/`retry`, `mentorFallback`,
`sparksAccepted`, the four stat labels, `noSlot`). The SPARKS FORMAT is a port of
`src/lib/faisca.ts` (`formatFaisca`): thousands grouped with a dot, only the parts
that exist, singular on ±1, and a debit smaller than one spark carries the minus on
the fagulhas. Proof `EnosisMentorListE2ETest`: every string, `f0=0 faíscas`,
`f830=830 fagulhas`, `f2000=2 faíscas`, `f101830=101 faíscas 830 fagulhas`,
`fNeg=-830 fagulhas`, `fNegBig=-101 faíscas 830 fagulhas`, `group=1.234.567`.
Battery **36/36**.

NAMED PENDING — §1197 still blocks pixel assertions; §1196 shaped `cardHeight`
(split into `cardShell` + `cardHead` + stats).

**Fase 3 — `vagas.minhas` (LANDED 23/11).** `MyJobsScreen` and `MyJobsMetrics` in
`libs/enois/MyJobs.kf` carry "my jobs / my external jobs / my applications" with the
numbers MEASURED in `src/routes/vagas.minhas.tsx`: container `max-w-3xl space-y-6`
(768/24), sections `surface-card p-4 sm:p-6` (24) titled at `text-xl` (28) with a
20 px icon, each job a `rounded-xl border border-border-soft p-3` (12 padding,
16 radius — the only BORDERED block on this route) with the `size="sm"` action row
(`mt-2` + 32). Every string is the real `texts.jobs`: the three section titles and
their empty states, `open`/`closed` "Aberta"/"Encerrada", `applicationsCount`
("1 candidatura recebida" / "N candidaturas recebidas"), `today`/`daysAgo`
("Hoje" / "Há 1 dia" / "Há N dias"), the actions (`applications.link`, `deleteJob`,
`visit`, `external.delete`, `applied`) and `loadError`/`retry`. Proof
`EnosisMyJobsE2ETest`: every string above plus `page=768` and `title_h=28`. Battery
**35/35**.

NAMED PENDING — §1196 bit `jobRowHeight` (the direct six-term sum crashed; it is
now `textStack()` + `actionStack()` + `2 × p-3`). §1197 still blocks pixel
assertions.

**Fase 3 — `projetos.painel` (LANDED 23/11).** `ProjectsPanelScreen` and
`ProjectsPanelMetrics` in `libs/enois/ProjectsPanel.kf` carry the publisher's panel with
the numbers MEASURED in `src/routes/projetos.painel.tsx`: container `max-w-3xl
space-y-6` (**768**/24 — the catalogue's own is 1024 and the route's `pageWidth` now
says so), header at **56** (title `text-2xl` 32 + `space-y-1` 4 + description
`text-sm` 20), the list `space-y-3` (12) of items `surface-card space-y-2 p-4`
(padding 16, gap 8) with the title at 16 and the status at 12 on the same row as
the `Button size="sm"` actions (32). Every string is the real `texts.projects`:
`panel.title` "Meus projetos", `panel.description`, `submit`, `panel.resubmit`
"Reenviar" (REJECTED only, so 3 actions there and 2 otherwise), `editor.editTitle`,
`panel.remove` "Excluir", `panel.reviewNote` "Recusado: …" and the `panel.removed` /
`editor.sent` / `editor.error` toasts. Items measure **148** with a note and two
synopsis lines, **80** bare. Proof `EnosisProjectsPanelE2ETest`: all strings,
`actsRej=3`, `actsPub=2`, `page=768`, `header_h=56`, `item_full=148`,
`item_plain=80`. Battery **34/34**.

NAMED PENDING — `known-bugs §1196` shaped this code too: `itemHeight` is built from
`addItem`/`noteHeight`/`synopsisHeight` because the direct sum crashed. The whole
catalogue family (`projetos` index, author, detail, panel) is now migrated; its
drawing paths still need §1197 resolved before any pixel assertion.

**Fase 3 — `projetos.$username.$projectSlug` (LANDED 23/11).** `ProjectDetailScreen`
and `ProjectDetailMetrics` in `libs/enois/ProjectDetail.kf` carry the project detail
with the numbers MEASURED in the route and the DS: container `max-w-3xl space-y-6`
(768/24), cover `h-56 sm:h-72` = **288** with an image and `h-40` = **160**
without, body `space-y-4 p-5 sm:p-6` (gap 16, padding 24), badge and tags at 12,
title `text-2xl` (24/32), author/synopsis at 14, description `leading-relaxed`
(24/line), action buttons 36 tall with a `pt-1` 4. The texts are `texts.projects`:
`back` "Voltar", `notFound` "Projeto não encontrado.", `submittedBy` "por …",
`openProject`/`openRepo`, and the status labels (shown only when not `PUBLISHED`).
TAGS follow the site (`split(",")` + trim + drop empty). The article body sums to
**328** with every block and **104** with only the title. Proof
`EnosisProjectDetailE2ETest`: all texts, `tags=3`, `coverImg=288`, `coverNone=160`,
`head_cat=84`, `body_min=104`, `body_full=328`. Battery **33/33**.

NAMED PENDING — `known-bugs §1196` bit THIS function: `articleBody` with eight
parameters and five chained call results crashed (deterministic SEGV inside
`m.bodyGap()`), and it only runs when each part is its own function, the gaps are
computed in `blockGaps`, and the sums are sequential; the shipped shape is the one
measured working. `articleHeight` (cover + body) is NOT shipped at nine parameters
— the same crash — and `coverHeight` is pinned on its own. Also pending: the
`GameIcon`-style imagery and no pixel assertion (§1197).

**Fase 3 — `projetos.$username` (LANDED 23/11).** `ProjectAuthorScreen` and
`ProjectAuthorMetrics` in `libs/enois/ProjectAuthor.kf` carry the project-maker page
with the numbers MEASURED in `src/routes/projetos.$username.tsx`: container
`max-w-5xl space-y-6` (**1024**/24), header `space-y-1` = 4 with the name at
`text-2xl` (24, line 32) and the label at `text-sm` (14, line 20) → **56**; grid
`grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3` = 3 columns of (1024 − 2×16)/3 =
**330**, reusing the catalogue's `ProjectsMetrics`; the card `surface-card block
h-full p-4` has NO cover and NO badge — just the title (`line-clamp-2`, 16) and
the synopsis (`mt-1 line-clamp-2`, 14) → **76** for one line of each, **116** for
one title line and two synopsis lines. The synopsis keeps the site's fallback
(`shortDescription || description || " "`), and the empty state is the catalogue's
own `EmptyState` strings. Proof `EnosisProjectAuthorE2ETest`:
`label=Projetos da comunidade`, `empty=Nenhum projeto encontrado.`,
`synShort=curta`, `synLong=longa`, `synNone=[ ]`, `col=330`, `header_h=56`,
`card=116`, `card1=76`. Battery **32/32**.

NAMED PENDING — no pixel assertion here: `known-bugs §1197` still does not close
the readback of a `fillRect`, so this page ships with text and geometry proven and
its colours traced to the tokens.

**Fase 3 — RECRUITMENT BOARD (LANDED 23/11, `enois-kof-desktop` `2cc7fe5`).**
`Recruitment.kf` carries the hiring funnel: the three summary badges with the site's plurals
("1 candidatura" / "N candidaturas", "N em processo", "1 não selecionada" / "N não
selecionadas"), the two non-stage columns ("Sem etapa", "Fora do processo"), the empty
stage, the file and selection labels, the score line, the load states, and the BATCH MOVE
rules taken from the component: nothing moves without a selection, nothing moves while the
batch is pending, advancing needs a stage, and REJECT does not use one. Geometry: batch bar
60, card 70, empty column 76, three-card column 262, three columns 888 — all from the
measured classes. Proof `EnoisRecruitmentE2ETest`: every string, all five move rules, and
those heights. App battery **46/47** (the failure is the §1197 ink test).

**Fase 3 — header parity with the site, and the SESSION BLOCKER measured (LANDED 23/11,
`enois-kof-desktop` `084bdb8`).** `SessionClient.baseHeaders()` now sends what the site's
`sessionHeaders()` sends — `Origin` **plus** `X-CSRF-Protection: 1` (the CSRF header the API
requires on cookie-authenticated calls) — and both session tests assert it.

**NAMED BLOCKER — the desktop cannot hold the site's session yet, and this is MEASURED, not
assumed.** The site's session lives in **HttpOnly cookies** that the browser attaches to
every request (`credentials: "include"`, `enois-frontend/src/lib/api-client.ts:72-77`); no
script can read them. `POST https://api.enois.ia.br/api/auth/login` measured 23/11 with curl
answers `401 {"code":"INVALID_CREDENTIALS"}` for a bad login and — the part that decides the
plan — the response carries **NO `set-cookie` header at all**, and the cookie jar stayed
empty. A desktop client therefore has nothing to store and nothing to replay: `SessionClient
.read` would send `hasSession = true` with an EMPTY `Cookie`, the API would answer 401, and
the app would fall to the guest shell anyway. The current behaviour is honest: the app boots
as `casca=visita` and no code pretends a session exists. What the migration needs is a real
cookie/bearer flow decided by the maintainer (or a backend change that returns a token the
desktop can keep) — that is a contract decision, not a client bug, and it is recorded here
rather than worked around.

**Fase 3 — a sessão decide a casca EM UM PASSO, e o app passa por ela (LANDED 23/11,
`enois-kof-desktop` `8b4fcde`).** `SessionShell` is the path the app runs on open, in the
site's order: read the local session mark (no mark = guest shell, the network is never
touched, exactly the `hasSessionFlag` gate), call `/me`, let the answer pick the shell (200
with the `Profile` = authenticated, 401 = guest, 403/5xx = guest plus the honest code), and
hand the choice to `ShellChoice`. `app/Main.kf` now boots through it and prints
`sessao: casca=visita` instead of the constant `signedIn = false`. Proof
`EnoisSessionShellE2ETest` against a header-driven local server:
`offline=visita`, `anon=visita`, `online=autenticado`,
`line=casca=autenticado usuario=Mel Santos`, and
`falha=casca=visita nota=ORIGIN_REQUIRED (HTTP 403)`. The REAL entry point compiles, runs
and reports `sessao: casca=visita`. App battery **45/46** (the failure is the §1197 ink
test).

NAMED PENDING — the session mark and the cookie come from a `false` and an `""` in the
entry point: the desktop has no session STORE yet (the site keeps it in localStorage). Until
that exists the authenticated shell is reachable through the library and its tests but not
by a real login in the app.

**Fase 3 — a SESSÃO do desktop (`/me`) — LANDED 23/11 (`enois-kof-desktop` `9d53db4`).**
`SessionClient` reproduces the site's `useCurrentUser` with the backend contract MEASURED by
`curl`: without the local session mark nothing is called (the site's `hasSessionFlag`
gate); with it, `GET …/me` sends `Origin` (the backend answers **403 ORIGIN_REQUIRED**
without it and **401 UNAUTHENTICATED** with it — both measured) and, on 200, reads the
`Profile`'s `username`/`name`. `SessionClient.classify` maps 2xx = session, 401 = no
session (guest shell, no failure note), anything else = an honest code. Proof
`EnoisSessionE2ETest` against a local server that answers by HEADERS exactly like
production: `none=false/0/visita`, `noorigin=ORIGIN_REQUIRED/visita/ORIGIN_REQUIRED (HTTP 403)`,
`anon=401/UNAUTHENTICATED/visita/[]`, `logged=200/mel/Mel Santos/autenticado`.

**KOF GAP FOUND AND FIXED — `known-bugs §1198`:** `http.send(...).status()/.body()` had NO
runtime on native x86-64 (the symbols were never emitted; the link failed with
`referência não definida para kof_http_send_response`). `NativeHttpResponse` now emits the
handle as two heap words {status, body}, reading the status from the `.Lhttp_last_status`
the SAME request wrote — calling `kof_http_status(url)` again was a SECOND request and
returned ITS status (measured: body 200 of `/me` with status 403 of the previous call).
Header strings are separated by a single LF (the core converts to CRLF; `\r\n` in the
string lost the second line). PASSING TEST in Kof4j: `HttpResponseNativeE2ETest`
(`status=401`, the `UNAUTHENTICATED` body, `ok=200 {…}`). SCOPE: x86-64; the riscv64 verbs
still lack the response handle (named). The app suite is **44/45** (the failure is the
§1197 ink test).

**Fase 3 — the shell is SESSION-DRIVEN (LANDED 23/11, `enois-kof-desktop` `a57953b`).**
`ShellChoice` picks the container the way `app-container.tsx` does: without a session, the
GUEST shell (no sidebar); with one, the authenticated shell (DS sidebar `w-60` = 240 at the
left). It also resolves where the route starts in each: guest y = 89, x = 104 at 1440 and
x = 24 at 1024 (the `max-w-7xl` centring plus `px-6`), column 1232/976; authenticated x =
368 and column 968 — all measured on the live page. `app/Main.kf` now uses it instead of
the hard-coded 24 px margin and the retired `headerHeight()`, and the REAL entry point
compiles and runs natively (`casca=visita coluna=1232`, plus the four route modules loading
in one binary). Proof `EnoisShellChoiceE2ETest`: both shells' names, tops, lefts and widths
at 1440 and 1024, and that the default is the guest container. App battery **43/44** (the
failure is the §1197 ink test).

NAMED PENDING — the session itself is still a constant (`signedIn = false`) in the entry
point: there is no `/me` client yet, so the authenticated shell is exercised by the library
and its test but never chosen at runtime.

**Fase 3 — SHELL COMPOSITION (LANDED 23/11, `enois-kof-desktop` `422ceea`).** The shell now
has the site's TWO containers, measured on the live page (`enois.ia.br/comunidades`,
logged out, chromium at 1440 and 1024): the GUEST shell is a **65 px** header, content
starting at **y = 89** (65 + `pt-6` 24), **1232** wide in a 1440 window and **976** in a
1024 one, with **no sidebar**; the AUTHENTICATED shell adds the DS sidebar at the LEFT
(`w-60` = **240**, `w-16` = 64 collapsed) and the route column becomes 968 in 1440
(1232 − 240 − 24). `Shell.paint` is the guest container — the one production draws for a
visitor — and `paintAuthenticated` is the other. The old right-hand 320 px column is
retired; that was a layout the site never renders (named divergence, now closed).

BUG FOUND AND FIXED IN THE HARNESS — the Kof4j install still carries an `enois/` copy
(the migration left it there) and the harness copied it OVER the app's, so every test was
compiling against stale screens. It now reinstalls the app's `libs/` after the Kof4j copy,
and the shell test is what caught it: `Layout.contentLeft` existed in the app repo and not
in the installed copy.

NAMED PENDING — one assertion in `EnoisCommunityE2ETest` (the exact source pixel of a
stretched cover) is now pinned only by the gradient's constant channel (B = 180) plus the
ink count, because the card width changed with the shell and the old sample point no
longer maps where it did. Re-deriving that sample needs the Kof4j compiler rebuilt and
reinstalled (the app's suite currently compiles with a jar older than the latest native
changes), so it is left for a tick that can rebuild it.

**MEASURED AGAINST THE LIVE SITE (23/11) — three of my numbers were wrong.** I topped out
the static measurement at the CSS/TSX and never checked production; with chromium
headless (`scripts/measure-site.mjs`, added to the app repo) I measured
`https://enois.ia.br/comunidades`: the Input is **36** (not the 40 my `h-10` comment
claimed), the Pill is **36** (not 32), and the `surface-card` radius is **24**
(`--radius-2xl` = 1rem + 8px — my `radius()` said 20). All three are now corrected and
pinned to the live figures. The location filter (`Filtrar por local`, city 1028 × state
192 in the 1232 container, "Filtrar" 32) was MISSING from the migration and is now in
`Communities.kf`.

**NAMED DIVERGENCE — the shell is authenticated while production shows the guest
layout.** The live page is rendered logged out: a 44 px header, a 1232 px content
column and NO sidebar. `Shell.kf` paints a 320 px right-hand aside for every window, so
a desktop user sees a column the site never draws. The order of the work is now clear:
the shell must render BOTH containers (`GuestAppContainer` and
`AuthenticatedAppContainer`, whose sidebar the DS sets to `w-60` = 240, not 320), and the
session decides which one — which is the shell composition slice, moved ahead of the
remaining catalogues.

**Fase 3 — `comunidades.$communityId` (LANDED 23/11, `enois-kof-desktop` `7804dfa`).**
The community detail: back link, `Criada por`, Edit, the composer placeholder, the feed
title and BOTH feed empties (member vs guest), the member plural and `Membros (N)`, the
admin actions, and the join button in its three states — "…" while the profile loads,
"Entrar" for a visitor, "Participando" for a member. Geometry: cover 192, header block
240 (cover + `p-6` ×2), text block 96 for one description line and 136 for three, head
row 96, composer 120, feed width 720 in a 1024 window. Proof
`EnoisCommunityDetailE2ETest`: every string, the three join states, and all heights.

FIXED while testing — `feedWidth` returned −4 in a 300 px window because I guarded on
the width instead of the remaining space; it now clamps to 0. App battery **42/43**
(the failure is the §1197 ink test).

**Fase 3 — `comunidades` index (LANDED 23/11, `enois-kof-desktop` `20b67d9`).**
The communities showcase: header, search, the topics rail, "Minhas comunidades" and
"Favoritas", the empty states (filtered vs general), the join/leave notices, the member
plural and the join/participating states, and all 43 TOPICS from
`constants/community-topics.ts` with unknown codes passing through (the list mirrors
`ContentTaxonomy.TOPICS` on the backend). Geometry: PageHeader 104, grid column 330
(the same as the catalogue), card cover 128, card body 218 with everything and 150
minimal. Proof `EnoisCommunitiesE2ETest`: every string, both empty-state choices, the
topic map and all heights. App battery **41/42** (the failure is the §1197 ink test).

NAMED DIVERGENCE — the card body's minimum (150) does not equal the hand arithmetic
(154): without an eyebrow the head block takes the name line (24) and not the 40 px
avatar, because the avatar sits beside the taller of the two columns. I pinned the
code's figure and recorded the discrepancy here rather than silently "fixing" a CSS
stacking question I have not measured in a browser.

**Fase 3 — weekly availability editor (LANDED 23/11, `enois-kof-desktop` `5ad7cd2`).**
`Availability.kf`: all `texts.mentoring.availability*` strings, the seven weekdays in
the site's Monday-first order, and the editor's RULES from the component — toggling an
empty day sets "19:00", untoggling clears it, "Adicionar horário" adds "20:00" and
stops at 12 per day (`disabled={draft[day].length >= 12}`), and only days with times
are sent (`toItems`). Geometry: closed day 36, a day with one time 118, with three
202, header 52. Proof `EnoisAvailabilityE2ETest`: every string, each rule, and all
those heights. App battery **40/41** (the failure is the §1197 ink test).

**Fase 3 — `vagas.$jobId.candidaturas` (LANDED 23/11, `enois-kof-desktop` `f63a2fd`).**
The applications page: `PageHeader` texts (`titleFor`, `snapshotHint`), both tabs, the
empty/error/retry states, the candidate card labels, the reputation and the two
`experience` forms, the four status labels, and the TRIAGEM — `actionCount` and
`actionLabel` reproduce `actionsFor` from `src/lib/job-application-status.ts`, whose
transitions are the same ones the backend validates (`canMoveTo`), including
NOT_SELECTED being terminal (0 actions). Geometry: header 104 (`p-6` ×2 + title 32 +
`mt-1` 4 + description 20), table row 36. Proof `EnoisApplicationsE2ETest`: every
string, `acts=2/2/1/0`, and all five transition labels. App battery **39/40** (the
failure is the §1197 ink test).

**Fase 3 — mentor register form (LANDED 23/11, `enois-kof-desktop` `ad244ae`).** `MentorRegister.kf`:
every field text from `texts.mentoring`/`texts.common`, the three room modes with
their hints, and the VALIDATION RULES taken from the route rather than guessed —
sparks price in [50, 10 000] (bounds are `MIN_SPARKS_PRICE`/`MAX_SPARKS_PRICE`), a
fixed room name matching `/^[a-z0-9-]{3,100}$/i`, and an external room only with
`https://`; `canSave` combines them exactly as the disabled predicate does. Geometry:
field 62 (label 16 + 6 + input 40), banner block 162 (label, 96 dashed button, the
hidden file input, the hint, with `space-y-1.5` between), box 72, button 36. Proof
`EnoisMentorRegisterE2ETest`: all strings, both price boundaries (49 999 invalid,
50 000 and 10 000 000 valid, 10 000 001 invalid), `http://` refused / empty allowed,
slug 2 chars and a space refused, and the three `canSave` refusals. App battery
**38/39** (the failure is the §1197 ink test).

**Fase 3 — `vagas.$jobId` (LANDED 23/11, in `enois-kof-desktop` `bd5af2b`).** The job detail:
container `max-w-2xl space-y-4` (672/16), card `surface-card sm:p-6` (24), header with the
48 px icon and `pb-4` (→ 64), author block `p-3` + 40 px avatar (→ 64), pills row
`mt-4` + a 24 px pill (→ 40), owner badge `mt-4` + `p-3` box, and sections titled
`text-base` with `leading-relaxed` bodies (→ 80 for a two-line body). Texts from
`texts.jobs`; the LEVEL and TYPE labels come from `constants/job-options.ts` and
**unknown codes pass through unchanged** (that is what makes external jobs render).
Proof `EnoisJobDetailE2ETest`: every string, `lvl=Júnior/Pleno/Sênior/Liderança`,
`lvlUnknown=EXTERNAL`, `type=CLT/Estágio/Tempo integral/Meio período/Contrato`,
`typeUnknown=OUTRO`, `company=Acme · Remoto`, `page=672`, `head_h=64`, `author_h=64`,
`pills_h=40`, `sec_h=80`. Battery in the app repo **36/37** (the one failure is the
§1197 text-ink test).

**REPO SPLIT — the migration lives in `Rede-ENois/enois-kof-desktop` (maintainer, 23/11).**
"Kof4j you fix Kof; Énois desktop is in enois-kof-desktop." What moved: every
`libs/enois/*.kf`, the text shim, `assets/fonts/Manrope.ttf`, `scripts/gen-enois-text.sh`
and all 36 Énois tests (`Enosis*E2ETest` → `Enois*E2ETest`), plus a new
`app/Main.kf` entry point and a Maven `pom.xml` that consumes `kof-compiler` 0.8.1
from the local Maven repo. What STAYS in Kof4j: `libs/game` (the SDL3 backend,
shared with other apps) — the desktop suite copies it from
`$KOF4J_HOME/kof-install/lib/kof-libs`, `<KOF4J_HOME>/lib/kof-libs` or
`<KOF4J_HOME>/libs` (or `KOF_LIBS`). `libs/enois` has NOT been deleted from Kof4j
yet, because this lane may not delete a library other lanes build on; the removal
is a maintainer call and is named here.
MEASURED in the new repo: `mvn -o test` → **36/37 pass, 1 fails** — `Enois
TextE2ETest` reads its ink inside a nested pixel loop and gets 0, which is the
§1197 readback shape, not a text regression (the same test passed in Kof4j with the
same code, and the glyph advance/line-height assertions pass). Also found and
FIXED while moving: §1192 addendum — `gen-enois-text.sh` was not idempotent.

**§1197 — CORRECTION 23/11 (late):** the "fill painted inside a FUNCTION reads
back" row I recorded earlier was produced by my own probe log — the grader took the
FIRST matching line of an append-only log, so a rerun printing `x=255, y=247` was
still counted as `255`. Re-run clean and with no token field printed:
`baseline=247` — the plain inline `clearFrame` + `fillRect` + `readPixel`, no token
call anywhere, reads the clear. There is NO proven function-painter escape hatch: a
fill followed by a read of that fill is unproven for every shape tried, so no pixel
proof on this backend until the readback is fixed. The three tests on this route
(creator page, catalogue, flow) still pass 3/3 — they assert text and geometry, never
a sampled fill colour.

**Fase 3 — o FLUXO do criador (LANDED 23/11).** `EnosisCreatorFlowE2ETest` compiles ONE
native program that loads `GameCreator`, `Projects` and `Mentoring` together — the
same route family — and runs it on x86-64 with `SDL_VIDEODRIVER=dummy`: the page's
`loading=Carregando…`, `noGames`, `handle=@mel · 2 jogos · 7 seguidores`,
`card=414`, the catalogue's `grid_col=330` and `grid_empty=Nenhum projeto
encontrado.`, and the mentoring title size `30` all come back from a single
binary. Battery **31/31**.

NAMED PENDING — `known-bugs §1197` now carries a 40-program REDUCER MATRIX: a fill
painted inside a FUNCTION reads back (`255`), the same fill written INLINE in
`main` reads the clear (`247`), and no single trigger reproduces in a 30-line
program. Until it is settled, no test above asserts a fill colour painted
inline. NAMED PENDING — `assertTrue(out.contains("X\n"))` on the LAST line of the
captured output does not match (`§1197`, same entry): every assertion here anchors
on the preceding line. NEXT: bisect §1197 at the IR level (inline vs
function-wrapped call order), then return to `projetos.$username`.

**Fase 3 — `games.$username` (LANDED 23/11, texto + geometria).** `GameCreatorScreen`
and `GameCreatorLayout` in `libs/enois/GameCreator.kf` carry the creator page with
the numbers MEASURED in `src/routes/games.$username.tsx` and the design system: the
container `max-w-3xl space-y-5` = **768**/20, the card `surface-card
overflow-hidden`, the cover `h-32 sm:h-40` = **160** (and, without a cover, the
`h-20` = 80 block on `--primary-soft`), the avatar `-mt-10 h-16 w-16 ring-4` = 64
lifting 40, `p-4 sm:p-5` = 20, the title `text-xl` = 20, the `@` line at 14, the
bio `border-t px-4 py-3` = 12 + 20/line, the link pills at 12, the grid
`sm:grid-cols-2 gap-3` = two columns of `(768 − 12)/2` = **378** with the game
card `p-3` + `GameIcon h-12 w-12` = **72 tall**. Texts are the real `texts.games`
block, INCLUDING the plurais (`1 jogo` / `N jogos`, `1 seguidor` / `N seguidores`)
and the owner/follow/following button. Proof `EnosisGameCreatorE2ETest`:
`g1=1 jogo`, `g0=0 jogos`, `f12=12 seguidores`, `handle=@ana · 3 jogos · 12
seguidores`, `owner=Editar minha página`, `following=Acompanhando`,
`notFound=Criador não encontrado.`, `noGames=Nenhum jogo publicado ainda.`,
`bio_h=64`, `card_bio=414`, `card_nobio=186`. Battery **30/30**.

NAMED PENDING — the PIXEL painter is not shipped: `known-bugs §1197` (measured
23/11) shows the canvas readback returning the clear colour after a plain
`fillRect`, so a colour asserted from a sample would be a guess. The page's
colours are therefore traced to the tokens (`--surface-card`, `--primary-soft`,
`--text-strong`, `--text-subtle`, `--muted-foreground`, `--primary`,
`--primary-foreground`) and the pixel proof waits for that defect. Also named:
the `GameIcon` monogram (`Gamepad2` at `h-5 w-5` on `--primary-soft`), the badge
width, and the remote cover/avatar images (network + `faviconOf`).
NEXT: `projetos.$username` (the same maker page for projects), or the §1197
reproducer matrix.

**Fase 3 — `projetos` (LANDED 23/11).** `ProjectsScreen` in `libs/enois/Projects.kf`
paints the community project catalogue with the numbers MEASURED in
`src/routes/projetos.index.tsx`: the container `mx-auto w-full max-w-5xl` =
**1024** (`space-y-6`), the grid `grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3`
= **3 columns of `(1024 − 2×16)/3` = 330** (3×330 + 2×16 = 1022 fits), the card
`surface-card` (NO border — `--surface-card` + `--radius-2xl` + `--shadow-table`)
with the cover `h-36` = **144** and the body `p-4` + `space-y-2`; the badge
`bg-primary-soft text-primary-text` at 11 px, the title `line-clamp-2` at 16,
the synopsis `line-clamp-2` at 14, the `gap-1` links at 12. Texts are the real
`texts.projects` block (`title`, `description`, `orderRecent` "Recentes",
`orderTitle` "A-Z", `submit`, `loadMore`, `empty`, `emptyHint`, `openProject`,
`openRepo`).
THE COVER IS A GRADIENT, not an image: `ProjectCover` falls back to
`linear-gradient(135deg, #6746f5, #4325b8)`, and the native painter repeats it
PER ROW measuring `t = (x + y) / (w + h)`; the test pins both ends of the
diagonal (`grad_tl=103,70,245`, `grad_br=93,61,227` at the 329×143 corner).
THE GRID, THE WRAP AND THE EMPTY STATE ARE MEASURED: the synopsis fits 2 lines
in 328 px, the body is `p-4 ×2` + badge 20 + 2 title/synopsis lines + the link
row + 3 gaps of 8 = **156** and the whole card **300**; the `EmptyState`
(`px-4 py-10`, icon `h-11 w-11` on `--surface-2`, `max-w-sm` description) is
**200** for the real 4-line hint.
NAMED PENDING — `known-bugs §1196`: a painter with FIVE live call results at the
top overran its frame on x86-64 (SEGV inside the line wrap). The card was
restructured so the arithmetic lives in `layoutBody`/`layoutSynLines`/
`cardHeight` and the colours arrive as parameters; the compiler bug stays OPEN
with its measurement recorded. NAMED PENDING — the category badge's colour is
asserted through `paintBadge` painted on its own: a sample INSIDE the full card
read the `--background` (the badge exists, its two lines of arithmetic are
measured, but its sampled pixel did not hold).
Battery **29/29**.

## §4 How to finish

each phase lands with a native run that prints what it painted; the plan records
what is migrated, what is a named gap, and the next step. Promotion to `docs/`
only when the route list is covered or the remaining gaps are named.

## §5 Named gaps (measured, not assumed)

* `math.sin`/`cos` — `MATH001` on every native target (§621 in `KofMath`); the
  tokens avoid it by converting OKLCH at GENERATION time.
* pdf.js, qrcode, Cloudflare Turnstile — no Kof surface; each route that needs
  one gets a named gap entry here.
