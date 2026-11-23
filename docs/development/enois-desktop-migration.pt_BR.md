[English](enois-desktop-migration.md) | [Português](enois-desktop-migration.pt_BR.md)

# Énois → cliente desktop nativo em Kof (migração 1:1)

last: ABERTO 22/11 por ordem da mantenedora ("seu trabalho é fazer funcionar aqui uma migração de 1:1 do enois, reprodução fiel pra um cliente desktop funcional", alvo `enois.ia.br`). STATUS: EM DESENVOLVIMENTO
doing: Fase 0+1 POUSADAS — os TOKENS (`libs/enois/Tokens.kf`, 61 cores + raios, gerados do CSS) E o SHELL do app (`libs/enois/Shell.kf`: header de 44 px, aside de 320 px, conteudo `--background`, o grid medido `h-11` / `gap-6` / `[1fr_320px]`) rodam no nativo e batem com o site
next: a Fase 3 continua — `comunidades.*` (a rota da comunidade, sobre o card + feed + abas)
location: repository
state: active

constraint:

* fidelidade primeiro: toda cor, tamanho e texto é MEDIDO de `enois-design-system/src/styles.css` ou do site ao vivo — nunca inventado
* os tokens são GERADOS (`scripts/gen-enois-tokens.py`), nunca editados à mão
* sem KofJS nesta migração (ordem da mantenedora 22/11): o cliente desktop é NATIVO
* uma tela só é "migrada" com uma execução nativa que imprime os valores que pintou
* as rotas do Énois são o contrato: uma tela Kof espelha uma rota, e rota faltante é gap NOMEADO, nunca stub

---

## §1 O que foi MEDIDO (22/11)

| fato | valor |
|---|---|
| `enois-frontend` | 90 522 linhas de TS/TSX, 1 319 arquivos |
| `enois-design-system` | 96 074 linhas de TS/TSX, 1 569 arquivos |
| rotas (`src/routes/*.tsx`) | **89** |
| tokens do tema (`:root`) | 65 (61 cores, 22 delas OKLCH) |
| raio | `--radius: 1rem` = 16 px |
| marca primária | `#6746f5` → `(103, 70, 245)` |
| fundo | `#f7f4fb` → `(247, 244, 251)` |
| site ao vivo | `https://enois.ia.br` (TanStack Start SSR, CSP, Cloudflare Turnstile) |

O mapa de dependências (o que cada superfície Kof substitui):
React 19 + react-dom → `kof.ui` / o canvas SDL3; TanStack Router (89 rotas file-based)
→ o registro de telas do Kof; TanStack Query → `kof.http` + um cache no app;
Tailwind CSS 4 → a biblioteca de tokens + layout explícito; Radix UI → diálogos/portais do Kof;
zustand → estado em classe Kof; zod → tipos Kof; pdf.js / qrcode / Turnstile → gaps nomeados.

## §2 O inventário de rotas (89, o contrato da migração)

agenda · apoiar · auth.* · blog.* · blogs · boas-vindas · call.$callId · carteira ·
colagem · colecoes.* · comunidades.* · confessionario · conta · contato · denuncias ·
desabafos · desafios · developers · diretrizes · empresa.* · entrar · eventos ·
explorar · feed · freelances · games.* · grupos.* · index · jogos-diarios ·
marketplace · mensagens · mentoria · meus-servicos · moderacao.* · moderador ·
murais.* · notificacoes.* · papo-reto · par-de-estudo · penalidades · perfil.* ·
post.$postId · privacidade · profissional_.* · projetos.* · rede · responsavel ·
roleplay.* · salvos · seguranca · selos · servicos · silenciados · sobre ·
sugestoes · suporte · termometro · termos · vagas.*

## §3 Fases

**Fase 0 — tokens (POUSADA 22/11).** `libs/enois/Tokens.kf` gerado do CSS:
61 cores (hex literal, OKLCH convertido pelo algoritmo do CSS Color 4 em tempo
de geração, então o runtime nativo não precisa de trig — medido: `math.cos` é
`MATH001` em todo alvo nativo), raios em px. Provado por uma execução nativa que
imprime `bg=247,244,251 / primary=103,70,245 / radius=16`.

**Fase 1 — o shell do app (POUSADA 22/11).** `libs/enois/Shell.kf`: uma janela 1280×800
pintada `--background`, o header de 44 px (`h-11`), o aside de 320 px e o grid de
conteudo `[minmax(0,1fr)_320px] gap-6` — o chrome que toda rota compartilha.
Provado nativamente: `top=60` (44+16), `fundo=247,244,251`, `header=255,255,255`,
`aside=255,255,255`. A mesma unidade corrigiu o `Sdl3Backend.fillRect`, que gravava
INTEIROS no `SDL_FRect` (quatro FLOATS) e NAO pintava nada — medido `dentro r=0`
antes, `r=255` depois (a correcao anterior cobria so' o `drawTexture`).

**Fase 2 — o Feed.** `feed.tsx` → uma lista rolável de cards de post.

**Fase 2 — o card de post (POUSADA 22/11).** O `libs/enois/PostCard.kf` pinta um
card de post com os numeros medidos do site: fundo `--surface-card`, borda de
1 px `--border-soft`, `rounded-xl` = 20 px (`calc(1rem + 4px)`, `styles.css:35`),
padding `p-4` = 16 px (`post-card.tsx:100`), o autor em `text-xs` = 12 px e o
corpo em `text-base` = 16 px na Manrope REAL. A quebra e' medida palavra a
palavra (`Font.measure`), entao as linhas seguem os avancos dos glifos, nao um
palpite. Provado nativamente: `card=255,255,255`, `fundo=247,244,251`,
`tinta=6303` — o autor e o corpo pintaram tinta de verdade dentro do card.

**Fase 2 — a tela do feed (POUSADA 22/11).** O `libs/enois/Feed.kf` pinta o
composer e a lista de cards na coluna de conteudo do shell: o composer e'
`h-[3.625rem]` = **58 px** com `--surface-card` e o placeholder
`"Escreva sua publicação"` (`texts.feed.composer.placeholder`) em `text-[15px]`
com `--text-date`; os cards empilham com o `gap-6` = 24 px do `feed.tsx:1011`.
O `Feed.lastCardHeight` expoe a altura MEDIDA do ultimo card, entao quem chama
nunca a crava (um teste que cravou quebrou no primeiro wrap real do card).
Provado nativamente: `composer_h=58`, `pill=255,255,255`, `card=255,255,255`,
`gap=247,244,251` (o `--background` aparecendo pelo gap).

**Fase 3 — as rotas, em ordem de dependência**: `perfil.*` → `post.$postId` →
`comunidades.*` → `mensagens` → o resto.

**Fase 4 — a ponte com o backend.** `kof.http` contra a mesma API que o site chama
(medida em `src/`), para o cliente desktop mostrar conteúdo real.

**Fase 5 — empacotamento.** `scripts/package-desktop.sh` (`.deb` + AppImage) e o
workflow de CI que prova o `dpkg -i` num ubuntu-latest.

**Fase 3 — `perfil.$username` (POUSADA 22/11).** O `libs/enois/Profile.kf` e' a
PRIMEIRA tela de ROTA, construida sobre o shell: o banner e'
`.cos-card__banner` = **120 px** (`styles.css:3503`), o avatar
`.cos-card__avatar` = **100 px** (`styles.css:4229`) meio sobre o banner, o
nome `.cos-card__name` = **25 px / 800** (`styles.css:4297`), a meta em 14 px e
a bio em `.cos-card__bio` 14 px (`styles.css:4323`). Provado nativamente:
`header_h=271`, `banner=238,232,255` (`--primary-soft`, #eee8ff),
`body=255,255,255` (`--surface-card`), `tinta=49`.
GAPS NOMEADOS desta tela: o avatar e' um circulo APROXIMADO (um quadrado com
recuo; o circulo anti-aliased exato precisa de um slice de mascara), a imagem
de capa e' o preenchimento `--primary-soft` (sem carregador de imagem ainda), e
o tema do `look` do perfil (as variaveis CSS `cos-card__*`) nao foi migrado.

**Fase 3 — `post.$postId` (POUSADA 22/11).** O `libs/enois/PostDetail.kf` pinta o
detalhe do post sobre o card: o link de voltar em `text-sm` = 14 px com
`--muted-foreground`, o card `surface-card p-5 sm:p-8` = **32 px** de padding
na largura de desktop, o titulo `text-lg` = **18 px** e o corpo `text-base` com
`leading-relaxed` = linha de **26 px** (`post.$postId.tsx`). Provado
nativamente: `card=255,255,255` (`--surface-card`), `fundo=247,244,251`
(`--background` acima do card), `tinta=174`.
A bateria Énois esta em **6/6** (tokens, shell, texto, card, feed, perfil,
detalhe).

**Fase 3 — a faixa de abas do perfil (POUSADA 22/11).** O `libs/enois/ProfileTabs.kf`:
seis abas na ordem do componente (`ALL, POSTS, REPLIES, REPOSTS, MEDIA,
DIARIO`, `profile-content-tabs.tsx:8`) com os rotulos REAIS de
`src/messages/defaults/profile.ts:345` ("Todos", "Posts", "Respostas",
"Reposts", "Mídia", "Diário"), `px-2 py-3 text-sm font-medium` = **14 px**, o
indicador ativo `inset-x-0 bottom-0 h-1 rounded-full bg-primary` = uma barra de
4 px em `--primary`, e a aba `REPLIES` pintada apagada para quem nao e' o dono
(o proprio `aria-disabled`/`disabled` do componente). Provado nativamente:
`tabs_h=48`, `indicador=103,70,245` (`--primary`), `fundo=247,244,251`,
`tinta=173`. Bateria **7/7**.

**Imagens e cor de verdade (POUSADAS 22/11).** O `Sdl3Backend.loadImageTexture`
decodifica PNG/PNM/BMP/QOI/GIF/WebP pela biblioteca `image` em KOF PURO (sem
codec externo) para uma textura do SDL, e os desenhos 1:1/escalados agora
entregam os pixels da fonte exatamente. Dois bugs independentes do compilador
foram achados e corrigidos no caminho: o formato de textura era o valor de
BIG-ENDIAN `RGBA8888`, entao todo canal voltava trocado (§1193), e o
rasterizador de glifo cravava branco, entao o texto ficava invisivel no card
branco (§1194). O `Font.drawTextColoured` agora leva a cor, e toda chamada do
Énois passa o seu proprio TOKEN. Bateria **8/8**, com um teste de imagem
afirmando um pixel real de PNG contra o oraculo do ImageMagick.
GAP NOMEADO restante: JPEG segue interop-first (`imageio` so' no JVM) — o
decodificador em Kof puro cobre PNG/PNM/BMP/QOI/GIF/WebP.

## §4 Como concluir

cada fase pousa com uma execução nativa que imprime o que pintou; o plano registra
o que está migrado, o que é gap nomeado, e o próximo passo. Promoção para `docs/`
só quando a lista de rotas estiver coberta ou os gaps restantes estiverem nomeados.

## §5 Gaps nomeados (medidos, não supostos)

* `math.sin`/`cos` — `MATH001` em todo alvo nativo (§621 no `KofMath`); os
  tokens evitam isso convertendo OKLCH em tempo de GERAÇÃO.
* pdf.js, qrcode, Cloudflare Turnstile — sem superfície Kof; cada rota que
  precisar de um ganha uma entrada de gap nomeada aqui.
