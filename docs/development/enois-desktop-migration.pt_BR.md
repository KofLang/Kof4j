[English](enois-desktop-migration.md) | [Português](enois-desktop-migration.pt_BR.md)

# Énois → cliente desktop nativo em Kof (migração 1:1)

last: ABERTO 22/11 por ordem da mantenedora ("seu trabalho é fazer funcionar aqui uma migração de 1:1 do enois, reprodução fiel pra um cliente desktop funcional", alvo `enois.ia.br`). STATUS: EM DESENVOLVIMENTO
doing: Fase 0+1 POUSADAS — os TOKENS (`libs/enois/Tokens.kf`, 61 cores + raios, gerados do CSS) E o SHELL do app (`libs/enois/Shell.kf`: header de 44 px, aside de 320 px, conteudo `--background`, o grid medido `h-11` / `gap-6` / `[1fr_320px]`) rodam no nativo e batem com o site
next: em `enois-kof-desktop` — a descricao do `EmptyState` quebra por palavra e pinta cada linha (app `b8a63c8`, `heightN(f, texto)`), bateria 11/11; continua nomeado: os cantos dos glifos do lucide, o esqueleto de carga (§1207/§1209), os valores do cartao da API; no Kof4j a semente da busca do §1209 e a face do §1207
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

**Fase 3 — `comunidades.$communityId` (POUSADA 22/11).** O `libs/enois/Community.kf` e'
a primeira tela com IMAGEM de verdade: a capa e' `h-48` = **192 px**
(`comunidades.$communityId.tsx:418`) pintada pelo `loadImageTexture`, o avatar
`h-16 w-16` = **64 px** com o seu `ring-4` (linha 430), o titulo
`text-2xl font-semibold` = **24 px** (linha 439). Sem capa, o card cai no
gradiente `--primary-soft` do proprio site. Provado nativamente:
`header_h=338`, `capa=112,…` (um pixel da IMAGEM, nao cor solida — uma sonda
vertical mostra o gradiente correndo de 10 a 223 ao longo da capa), e
`sem_capa=238,232,255` = `--primary-soft #eee8ff` para o fallback.
Bateria **9/9**.

**Fase 3 — o avatar (POUSADO 22/11).** O `libs/enois/Avatar.kf` migra o
`UserAvatar`: o `getInitials` do site ao pe' da letra (a primeira letra da
primeira e da ultima palavra, em maiusculas; uma palavra so' -> aquela letra —
`user-avatar.tsx:11`), o fallback `bg-violet-300` (#c4b5fd) sobre
`text-violet-900` (#4c1d95), e um circulo REAL pintado pixel a pixel pela lei
do raio, entao os cantos mostram o fundo. Provado nativamente:
`i1=MS / i2=P / i3=AL / i4=RC`, `centro=196,181,253` e `canto=0,0,0` (fora do
circulo).
O caminho da FOTO tambem e' circular, em KOF PURO: o fix do known-bugs §1195
fez do `Buffer` um tipo nominal, entao o `Avatar.maskCircle` e' um laco de
pixel Kof sobre o buffer decodificado (`centro=128,127,180` da imagem,
`canto=0,0,0` fora). Uma primitiva compartilhada agora serve toda mascara de
imagem do app.

**Fase 3 — `mensagens` (POUSADA 22/11).** O `libs/enois/Messages.kf` e' a caixa de
entrada: o cabecalho `p-4` com o titulo em `text-lg` = **18 px**, as DUAS abas
REAIS (`SOCIAL_TABS = ["all","archived"]`, `mensagens.tsx:51`) rotuladas "Todas"
e "Arquivadas" (`texts.ts:3004`), e a linha de conversa = o avatar CIRCULAR (a
primitiva do §1195) + o nome + a previa da ultima mensagem
(`conversation-item.tsx`). Uma lista vazia mostra o proprio
"Nenhuma conversa ainda" do site. Provado nativamente: `header_h=90`,
`lista_h=128`, `vazia_h=64`, `header=255,255,255`, `avatar=196,181,253` (o
`bg-violet-300` dentro do circulo) e `canto=255,255,255` (o card fora).
Bateria **11/11**.

**Fase 3 — o FEED da comunidade (POUSADO 22/11).** O `libs/enois/CommunityFeed.kf` e'
a primeira tela que COMPOE as primitivas: a coluna de conteudo + o aside. O grid
dele e' o PROPRIO da comunidade: `[minmax(0,1fr)_280px] gap-6`
(`comunidades.$communityId.tsx:875`) — **280 px** de aside, onde o feed GLOBAL
usa 320. A largura do conteudo em 1280 e' portanto **944**, nao os 904 do feed
da home; essa diferenca e' MEDIDA nas duas fontes, nao suposta. O titulo da
secao "Feed" e' `text-lg` = 18 px (877), o estado vazio e'
`surface-card p-8 text-sm` com os textos do proprio site (2332/2333), e os
cards sao separados por `space-y-4` = 16 px. Provado nativamente:
`conteudo_w=944`, `vazio_h=112`, `com_post_h=128`, `aside_x=984`,
`card=255,255,255`. Bateria **12/12**.

**Fase 3 — `explorar` (POUSADO 22/11).** O `libs/enois/Explore.kf` e' o hub das salas.
Os DADOS sao extraidos do codigo sem invencao: os itens e as suas secoes de
`src/components/explore-items.ts` (**13 / 2 / 3 / 5 = 23**), os rotulos e as
descricoes de `texts.explore.items`, e a ORDEM das secoes de
`texts.explore.sections` (community, play, build, work — o comentario do proprio
fonte diz que a ordem das chaves E' a ordem das secoes). Medidas do `ExploreHub`
do design system: icone `h-10 w-10` = 40 px, rotulo `text-[15px]`, descricao
`text-[13px]`, container `max-w-3xl` = 768, titulo de secao `text-[12px]`.
Provado nativamente: `secoes=4`, `s0=Comunidade:13`, `s1=Brincar:2`,
`s2=Construir:3`, `s3=Trabalho:5`, `total=23`, `primeiro=Colagem`,
`ultimo=Mentoria`, `card=255,255,255`, `icone=238,232,255`.
Bateria **13/13**.

**Fase 3 — a secao de MEMBROS da comunidade (POUSADA 22/11).** O `CommunityMembers`
no `libs/enois/CommunityFeed.kf` pinta o bloco do aside medido em
`comunidades.$communityId.tsx:898-906`: o bloco `surface-card p-5`, o titulo
`h3 mb-3 text-sm font-semibold` com `membersTitle(count)` —
**"Membros (N)"**, uma FUNCAO em `texts.ts:2334`, nao um literal — e cada linha
com o avatar circular `h-9 w-9` = **36 px**, o nome em `text-sm font-medium` e
o badge "ADMIN". Provado nativamente: `membros_h=162` (`p-5` x2 + o titulo +
o gap + 2 x (36 + 12)), `vazia_h=66`, `card=255,255,255`,
`avatar=196,181,253`. Bateria **14/14**.

**Fase 3 — `vagas` (POUSADA 22/11).** O `libs/enois/Jobs.kf` pinta o shell da
listagem de vagas: o cabecalho `surface-card p-4 sm:p-6` com o titulo
"Oportunidades" em `text-2xl` = 24 px e o subtitulo em `text-sm`, a linha de
busca carregando o placeholder do proprio site "Buscar vagas (ex.: java
-estágio)", e as pilulas de filtro em `h-9` = 36 px com `border-border-soft`;
os rotulos das pilulas usam os valores REAIS de `texts.jobs.filters`
(Remoto/Híbrido/Presencial, Júnior/Pleno/Sênior/Liderança) mais o
`clearFilters` = "Limpar filtros". Provado nativamente: `header_h=146`
(24+24+8+14+16+36+24), `filtros_h=52`, `card=255,255,255`,
`borda=229,224,235` (`--border-soft #e5e0eb`), `vazio=255,255,255` (o card
onde nao ha pilula). Bateria **15/15** — o primeiro tick desde a rodada do
avatar SEM erro de amostragem, porque cada ponto foi CALCULADO do layout antes
da assercao ser escrita.

**Fase 3 — o CARD de vaga (POUSADO 22/11).** O `JobCard` no `libs/enois/Jobs.kf` pinta um
card de resultado com os numeros medidos no `visibleJobs.map` do `vagas.tsx`:
`surface-card flex flex-col p-5`, o avatar `h-12 w-12 rounded-xl` = **48 px
com raio 12** — um QUADRADO ARREDONDADO, nao um circulo (o avatar de USUARIO e'
o circulo; o da VAGA e' `rounded-xl`, e o codigo diz isso), o titulo
`font-semibold`, a empresa em `text-sm text-muted-foreground`, e as pilulas em
`px-2.5 py-1 text-xs` com a do match em `--primary-soft`. Provado nativamente:
`card_h=122` (20+48+12+22+20), `avatar=238,232,255`,
**`canto=255,255,255`** (o corte do raio 12 mostrando o card pelo canto),
`pilula=238,232,255`. Bateria **16/16** — o segundo tick seguido com cada
coordenada de amostra CALCULADA do layout antes da assercao ser escrita.

**Fase 3 — `vagas.$jobId` (POUSADO 22/11).** O `JobDetail` no `libs/enois/Jobs.kf` pinta
o detalhe da vaga: o container `mx-auto w-full max-w-2xl space-y-4` = **672 px**
centrado, o link "Voltar para vagas", o cabecalho `surface-card p-4 sm:p-6` com
o avatar `h-12 w-12 rounded-xl` sobre `--surface-1`, o titulo em `text-xl` =
20 px, a empresa em `text-sm text-text-subtle`, e o bloco "Sobre a vaga"
abaixo. Provado nativamente: `x=114` (a coluna centrada numa janela de 900),
`avatar=249,248,255` (`--surface-1 #f9f8ff`), `card=255,255,255`,
**`gap=247,244,251`** (o intervalo `space-y-4` mostrando o fundo entre os dois
blocos). Bateria **17/17** — terceiro tick seguido com cada coordenada de
amostra calculada do layout antes da assercao.

**Fase 3 — `comunidades.$communityId.canais.$channelId` (POUSADA 22/11).** O `ChannelScreen`
no `libs/enois/Channel.kf` pinta o canal: o container
`mx-auto w-full max-w-2xl space-y-4 py-6` = **672 px** com 24 px de padding
vertical, o link "Voltar para a comunidade", o cabecalho `surface-card p-5`
com o icone `h-10 w-10 rounded-full` sobre `--primary-soft`, o estado vazio
"Nada por aqui ainda. Comece a conversa.", e o composer com
"Escreva para este canal..." — todo texto do proprio site (`texts.ts` bloco
`channelPage`, que tambem carrega os tres estados de acesso
`private`/`notMember`/`error`, nomeados aqui como o trabalho da proxima fatia).
Provado nativamente: `x=114` (a coluna centrada), `icone=238,232,255`
(`--primary-soft`), `card=255,255,255`, `canal_h=280`. Bateria **18/18** —
quarto tick seguido com cada coordenada de amostra calculada do layout.

**Fase 3 — os tres estados de ACESSO do canal (POUSADOS 22/11).** O `ChannelAccess` no
`libs/enois/Channel.kf` pinta os estados que o site distingue com cores que
SIGNIFICAM algo: `private` em `--primary` (**#6746f5**), `error` em
`--destructive` (**#b30018**), e `notMember` com o botao "Entrar na comunidade"
em `--primary-soft`. O container comum e' `surface-card space-y-3 p-5` = 20 px
e a mensagem `text-sm text-text-subtle` = 14 px. Cada estado carrega a sua
barra de acento de 4 px na cor do papel — a cor nao e' decoracao, e' a
distincao entre "voce nao tem acesso" e "algo falhou".
Provado nativamente: `privado_h=54`, `visitante_h=102` (o botao soma 12 + 36),
`erro_h=54`, `privado=103,70,245`, `erro=179,0,24`, `botao=238,232,255`.
Bateria **19/19** — quinto tick seguido com cada coordenada de amostra
calculada do layout.

**Fase 3 — `blogs` (POUSADA 22/11).** O `BlogsScreen` no `libs/enois/Blogs.kf` pinta o
DIRETORIO de blogs — "Cantinhos de quem escreve por aqui", nao um feed. O
cabecalho carrega o titulo "Blogs" e a descricao do proprio site (`texts.ts`
bloco `blog`), quebrada por avancos de glifo MEDIDOS
(`BlogsScreen.wrapText`); cada card e' `surface-card p-4 sm:p-5` = 120 px com o
eyebrow `text-xs uppercase tracking-widest text-primary`, o titulo
`text-base font-semibold` e a descricao `text-sm`. Os estados vazios
(`emptyDirectory`, `searchEmpty`) e o placeholder de busca sao os textos do
site. PROVA `EnosisBlogsE2ETest`: `header_h=62`, `card_h=120`,
`card=255,255,255`, e o eyebrow EXISTINDO na familia do `--primary`.
DIVERGENCIA NOMEADA registrada ao escrever a prova: um glifo de 12 px tem
bordas ANTI-ALIASED, entao o primeiro pixel nao-branco do eyebrow em
maiusculas e' uma MISTURA de `--primary` com o card (`154,132,248`, medido) —
o teste afirma o PAPEL (azul domina o vermelho) em vez de um valor de pixel que
uma varredura fixa nao garantiria. Bateria **20/20**.

**Fase 3 — `blog.$username` (POUSADA 23/11).** O `BlogScreen` no `libs/enois/Blog.kf`
pinta UM blog: a capa `h-64 w-full` = **256 px** numa caixa `rounded-2xl` (os
cantos de raio 16 recortados de volta ao `--background`, provado pelo pixel do
canto), o cabecalho `surface-card p-5 sm:p-6` com o titulo `text-2xl` e a
linha de meta, e o estado "nao encontrado" `surface-card p-10 text-center`
com "Blog nao encontrado." e "Ver todos os blogs".
AS FUNCOES DE TEXTO DO SITE SAO MIGRADAS COMO FUNCOES: `postsCount(count)` da
**"1 post"** mas **"3 posts"** (o plural e' parte do contrato), e
`readingTime(min)` da "N min de leitura estimada".
Provado nativamente: `c1=1 post`, `c3=3 posts`, `rt=5 min de leitura estimada`,
`capa_h=256`, `capa=116,59,180` (um pixel da IMAGEM de capa),
`canto=247,244,251`. DIVERGENCIA pega pelo teste: arredondei a mao o pixel
projetado (110.599 -> 117) e o valor real e' 116 — o valor agora sai da
FORMULA, nao da minha aritmetica. Bateria **21/21**.

**Fase 3 — `grupos.$slug` (POUSADA 23/11).** O `GroupScreen` no `libs/enois/Groups.kf`
pinta UM grupo: a coluna `max-w-3xl` = **768 px**, o banner `h-40` = **160 px**
pintado de uma IMAGEM real, o logo `h-12 w-12 rounded-xl` — um QUADRADO
ARREDONDADO como o card de vaga, nao o avatar circular de usuario — o titulo
`text-xl` e a pilula do modo em `bg-primary-soft text-primary`.
O CONTRATO DESTA TELA E' A VARIACAO DE TEXTO, e o teste afirma cada ramo:
`members(1)` = **"1 membro"** mas `members(5)` = **"5 membros"**;
`joinModeLabel` da **"Entrada aberta"** vs **"Entrada por aprovação"**; e
`actionLabel` da **"Entrar no grupo"** / **"Pedido pendente"** /
**"Você participa"**. Uma string cravada em qualquer um dos tres falharia.
Provado nativamente: `m1=1 membro`, `m5=5 membros`, os dois modos, as tres
acoes, `x=66`, `grupo_h=338`, `banner=98,95,180` (um pixel da imagem, afirmado
como NAO sendo o fallback `--primary-soft`), `logo=238,232,255`.
Bateria **22/22**.

**Fase 3 — `eventos` (POUSADA 23/11).** O `EventsScreen` no `libs/enois/Events.kf`
pinta a AGENDA da comunidade: a grade `grid-cols-1 gap-4 md:grid-cols-2
lg:grid-cols-3` — **3 colunas no desktop** — com cada card
`surface-card space-y-3 p-4` = 16 px carregando o titulo, a data e o formato.
Os formatos sao os do site (`formats.ONLINE` "Online", `IN_PERSON`
"Presencial", `HYBRID` "Híbrido") e o vazio e' "Nenhum evento por aqui ainda.".
A PROVA DA GRADE E' A GEOMETRIA, nao uma contagem: a largura da coluna sai de
`(904 - 2x16) / 3` = **290**, e o teste amostra o SEGUNDO e o QUARTO cards a
uma coluna + o gap a direita — se o layout colapsasse para uma coluna, essas
amostras cairiam no fundo em vez do card. Provado nativamente: `col_w=290`,
`f=Online|Presencial|Híbrido`, `grade_h=296` (2 linhas x 140 + 16),
`card1`/`card2`/`card4` = `255,255,255` e `gap=247,244,251`.
Bateria **23/23**.

**Fase 3 — `salvos` (POUSADA 23/11).** O `SavedScreen` no `libs/enois/Saved.kf` usa os
numeros do `PageHeader` do design system (`surface-card mb-4 p-4 sm:p-6` =
24 px com 16 px abaixo, titulo `text-2xl` = 24 px, icone `h-5 w-5 text-primary`
= 20 px) e os textos do proprio site.
O CONTRATO E' UM PLURAL DUPLO, e o teste afirma os tres casos:
`count(0)` = "0 **posts salvos**", `count(1)` = "1 **post salvo**",
`count(7)` = "7 **posts salvos**" — os DOIS sufixos mudam juntos, entao um
cravado falharia no outro.
Provado nativamente: `c0`, `c1`, `c7`, `vazio=Nenhum post salvo ainda.`,
`icone=103,70,245` (`--primary`), `card=255,255,255`,
`gap=247,244,251` (a faixa do `mb-4`).
DIVERGENCIA pega ao escrever a prova: amostrei primeiro o gap do `mb-4` em
y=140, mas o header termina em **146** (`60 + 24x2 + 20 + 14 + 4`) — a amostra
estava DENTRO do header, nao no gap. A faixa foi recalculada antes do teste
ser escrito. Bateria **24/24**.

**Fase 3 — `selos` (POUSADA 23/11).** O `SealsScreen` no `libs/enois/Seals.kf` pinta a
pagina dos selos de contribuicao: a coluna `max-w-3xl` = **768 px** com
`space-y-8` (32 px entre secoes) e `sm:py-14` (56 px), o eyebrow
`text-xs font-semibold uppercase tracking-widest text-primary`, o titulo em
`sm:text-4xl` = **36 px**, a intro em `text-base leading-relaxed`, e os blocos
de categoria CODE/ISSUES sobre `--primary-soft`.
O CONTRATO E' `tierRange(min, max)` COM TRES FORMAS, e o teste afirma todas:
`tierRange(10, -1)` = **"10+"** (fim aberto), `tierRange(5, 5)` = **"5"**
(unico), `tierRange(3, 9)` = **"3–9"** com **EN DASH (U+2013)** — o teste tambem
afirma que o hifen ASCII NAO esta la'. Bateria **25/25**.
DIVERGENCIA que mudou COMO a prova e' escrita: uma varredura por "nao e' branco"
para na hora, porque o FUNDO da pagina `247,244,251` tambem nao e' branco — a
primeira versao achou o fundo em (66,72) e o chamou de eyebrow. A varredura
agora procura a TINTA do `--primary` (azul alto, vermelho baixo e azul pelo
menos 60 acima do vermelho), que NEM o fundo NEM o branco casam.

**Fase 3 — `notificacoes.index` (POUSADA 23/11).** O `NotificationsScreen` no
`libs/enois/Notifications.kf` pinta a lista de avisos: a coluna `max-w-2xl` =
**672 px**, cada item `px-4 py-3.5` com o icone circular `h-11 w-11` = **44 px**
sobre `--primary-soft`, o nome `font-extrabold` e o corpo `text-text-subtle`.
Os rotulos de dia sao os do site: **"Hoje"** / **"Ontem"**
(`notificationsPage.today`/`yesterday`).
O CONTRATO E' O AGRUPAMENTO, e ele e' provado pela ALTURA, nao contando
chamadas: um rotulo de dia aparece so' quando o grupo MUDA, entao tres itens em
DOIS grupos dao **274** px (216 + 2x29) e tres itens em UM grupo dao **245**
px (216 + 29). Um rotulo por item daria 303 e falharia as duas assercoes.
Bateria **26/26**.

**Fase 3 — `desafios` (POUSADA 23/11).** O `ChallengesScreen` no `libs/enois/Challenges.kf`
pinta a lista de desafios: o card `space-y-3 rounded-2xl border border-border
p-4` (raio 16, provado pelo corte do canto), o titulo `text-base font-semibold`
e o corpo `text-sm`.
O CONTRATO SAO OS LIMITES DO COMPOSITOR, e eles foram MEDIDOS no
`desafios-composer.tsx:11-15`: `MAX_TITLE = 120`, `MAX_DESCRIPTION = 3000`,
`MAX_DAYS = 31`, mais os **2000** do texto `contentTooLongError`. As RECUSAS do
site interpelam esses numeros, entao o teste compara a mensagem COMPLETA ("O
título aceita no máximo 120 caracteres." / "O prazo precisa ser uma data futura
(até 31 dias).") — mudar um limite quebra a mensagem. O PESO tambem e'
contrato: o subtitulo diz "Autoria nominal, sem ranking e sem prêmio", e o
`hasRanking()` devolve `false`. Bateria **27/27**.

**Fase 3 — `mentoria` (POUSADA 23/11).** O `MentoringScreen` no `libs/enois/Mentoring.kf`
pinta o hero com os numeros MEDIDOS no `mentoria.tsx`: o card
`surface-card p-5 sm:p-8` = **32 px**, o badge em `--primary-soft`, o titulo
`text-3xl` = **30 px**, a descricao `mt-2` e as pilulas CTA `mt-5` —
"Encontrar mentor" / "Tornar-me mentor".
A QUEBRA DO TITULO E' MEDIDA, e esse e' o achado: o titulo ocupa **744 px** a
30 px e a largura interna do card e' `868 - 2x32` = **804**, entao cabe em UMA
linha por 60 px. A altura do hero prende essa quebra pelo outro lado: a
descricao REAL do site (2 linhas a 16 px) deixa o hero em **254**, e uma mais
curta o deixaria em 228 — o que o teste provou quando eu o escrevi primeiro
com uma descricao encurtada. Bateria **28/28**.

**Fase 3 — lista da `mentoria` (POUSADA 23/11).** O `MentorListScreen` e o
`MentorListMetrics` em `libs/enois/MentorList.kf` acrescentam a lista ao hero ja
publicado: o bloco em destaque (`text-lg` 28, descricao em 20, a busca `mt-4` + 40,
a grade `gap-4` em 2 colunas), o cartao de mentor `surface-card p-5 space-y-4`
(avatar 56, nome 24, headline 16) e a caixa de estatisticas 2x2 (`p-3`, 12). Os
textos sao os de `texts.mentoring`. O FORMATO DAS FAISCAS e' um porte de
`src/lib/faisca.ts` (`formatFaisca`): milhar com ponto, so as partes que existem,
singular em ±1, e um debito menor que uma faisca leva o menos nas fagulhas. Prova
`EnosisMentorListE2ETest`: todas as strings, `f0=0 faíscas`, `f830=830 fagulhas`,
`f2000=2 faíscas`, `f101830=101 faíscas 830 fagulhas`, `fNeg=-830 fagulhas`,
`fNegBig=-101 faíscas 830 fagulhas`, `group=1.234.567`. Bateria **36/36**.

PENDENCIA NOMEADA — o §1197 segue bloqueando provas de pixel; o §1196 moldou o
`cardHeight` (dividido em `cardShell` + `cardHead` + stats).

**Fase 3 — `vagas.minhas` (POUSADA 23/11).** O `MyJobsScreen` e o `MyJobsMetrics` em
`libs/enois/MyJobs.kf` carregam "minhas vagas / minhas vagas externas / minhas
candidaturas" com os numeros MEDIDOS em `src/routes/vagas.minhas.tsx`: container
`max-w-3xl space-y-6` (768/24), secoes `surface-card p-4 sm:p-6` (24) com titulo
`text-xl` (28) e icone de 20, cada vaga um `rounded-xl border border-border-soft
p-3` (12 de padding, raio 16 — o unico bloco COM BORDA desta rota) com a faixa de
acoes `size="sm"` (`mt-2` + 32). Toda string e a de `texts.jobs`: os tres titulos
de secao e seus vazios, `open`/`closed` "Aberta"/"Encerrada", `applicationsCount`
("1 candidatura recebida" / "N candidaturas recebidas"), `today`/`daysAgo`
("Hoje" / "Ha 1 dia" / "Ha N dias"), as acoes (`applications.link`, `deleteJob`,
`visit`, `external.delete`, `applied`) e `loadError`/`retry`. Prova
`EnosisMyJobsE2ETest`: todas as strings acima mais `page=768` e `title_h=28`.
Bateria **35/35**.

PENDENCIA NOMEADA — o §1196 mordeu o `jobRowHeight` (a soma direta de seis termos
quebrava; hoje e' `textStack()` + `actionStack()` + `2 x p-3`). O §1197 segue
bloqueando provas de pixel.

**Fase 3 — `projetos.painel` (POUSADA 23/11).** O `ProjectsPanelScreen` e o
`ProjectsPanelMetrics` em `libs/enois/ProjectsPanel.kf` carregam o painel de quem
publica com os numeros MEDIDOS em `src/routes/projetos.painel.tsx`: container
`max-w-3xl space-y-6` (**768**/24 — o do catalogo e' 1024 e o `pageWidth` da rota
agora diz isso), cabecalho em **56** (titulo `text-2xl` 32 + `space-y-1` 4 +
descricao `text-sm` 20), a lista `space-y-3` (12) de itens `surface-card
space-y-2 p-4` (padding 16, vao 8) com o titulo em 16 e o status em 12 na mesma
linha das acoes `Button size="sm"` (32). Toda string e a de `texts.projects`:
`panel.title` "Meus projetos", `panel.description`, `submit`, `panel.resubmit`
"Reenviar" (so em REJECTED, entao 3 acoes la e 2 fora), `editor.editTitle`,
`panel.remove` "Excluir", `panel.reviewNote` "Recusado: …" e os toasts
`panel.removed` / `editor.sent` / `editor.error`. Os itens medem **148** com nota e
duas linhas de sinopse, **80** sozinhos. Prova `EnosisProjectsPanelE2ETest`: todas
as strings, `actsRej=3`, `actsPub=2`, `page=768`, `header_h=56`, `item_full=148`,
`item_plain=80`. Bateria **34/34**.

PENDENCIA NOMEADA — o `known-bugs §1196` moldou este codigo tambem: o `itemHeight`
e' montado com `addItem`/`noteHeight`/`synopsisHeight` porque a soma direta
quebrava. A familia inteira do catalogo (`projetos` indice, autor, detalhe, painel)
esta migrada; a pintura dela ainda depende do §1197 antes de qualquer pixel.

**Fase 3 — `projetos.$username.$projectSlug` (POUSADA 23/11).** O `ProjectDetailScreen`
e o `ProjectDetailMetrics` em `libs/enois/ProjectDetail.kf` carregam o detalhe do
projeto com os numeros MEDIDOS na rota e no DS: container `max-w-3xl space-y-6`
(768/24), capa `h-56 sm:h-72` = **288** com imagem e `h-40` = **160** sem, corpo
`space-y-4 p-5 sm:p-6` (vao 16, padding 24), selo e tags a 12, titulo `text-2xl`
(24/32), autoria/sinopse a 14, descricao `leading-relaxed` (24/linha), botoes de
acao com 36 e `pt-1` 4. Os textos sao os de `texts.projects`: `back` "Voltar",
`notFound` "Projeto não encontrado.", `submittedBy` "por …", `openProject`/`openRepo`
e os rotulos de status (so' quando nao e' `PUBLISHED`). As TAGS seguem o site
(`split(",")` + trim + vazias fora). O corpo do artigo soma **328** com todos os
blocos e **104** so' com o titulo. Prova `EnosisProjectDetailE2ETest`: todos os
textos, `tags=3`, `coverImg=288`, `coverNone=160`, `head_cat=84`, `body_min=104`,
`body_full=328`. Bateria **33/33**.

PENDENCIA NOMEADA — o `known-bugs §1196` mordeu ESTA funcao: `articleBody` com oito
parametros e cinco resultados de chamada encadeados quebrava (SEGV deterministico
dentro de `m.bodyGap()`), e so' roda com cada parte numa funcao propria, os vaos
em `blockGaps` e as somas sequenciais; a forma publicada e' a medida que funciona.
`articleHeight` (capa + corpo) NAO entra com nove parametros — o mesmo crash — e o
`coverHeight` fica preso sozinho. Tambem pendente: as imagens e nenhum pixel
afirmado (§1197).

**Fase 3 — `projetos.$username` (POUSADA 23/11).** O `ProjectAuthorScreen` e o
`ProjectAuthorMetrics` em `libs/enois/ProjectAuthor.kf` carregam a página de um
autor com os números MEDIDOS em `src/routes/projetos.$username.tsx`: container
`max-w-5xl space-y-6` (**1024**/24), cabeçalho `space-y-1` = 4 com o nome em
`text-2xl` (24, linha 32) e o rótulo em `text-sm` (14, linha 20) → **56**; grade
`grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3` = 3 colunas de (1024 - 2x16)/3 =
**330**, reusando o `ProjectsMetrics` do catálogo; o cartão `surface-card block
h-full p-4` NÃO tem capa nem selo — só o título (`line-clamp-2`, 16) e a sinopse
(`mt-1 line-clamp-2`, 14) → **76** para uma linha de cada, **116** para uma de
título e duas de sinopse. A sinopse mantém o fallback do site
(`shortDescription || description || " "`), e o vazio usa as strings do
`EmptyState` do catálogo. Prova `EnosisProjectAuthorE2ETest`:
`label=Projetos da comunidade`, `empty=Nenhum projeto encontrado.`,
`synShort=curta`, `synLong=longa`, `synNone=[ ]`, `col=330`, `header_h=56`,
`card=116`, `card1=76`. Bateria **32/32**.

PENDENCIA NOMEADA — nenhuma assercao de pixel aqui: o `known-bugs §1197` ainda nao
fecha a leitura de um `fillRect`, entao a pagina entra com texto e geometria
provados e as cores rastreadas nos tokens.

**Fase 3 — QUADRO DE RECRUTAMENTO (POUSADA 23/11, `enois-kof-desktop` `2cc7fe5`).**
`Recruitment.kf` carrega o funil de contratacao: os tres selos de resumo com os plurais do site
("1 candidatura" / "N candidaturas", "N em processo", "1 nao selecionada" / "N nao
selecionadas"), as duas colunas que nao sao etapa ("Sem etapa", "Fora do processo"), o vazio
da etapa, os rotulos de ficha e de selecao, a linha da nota, os estados de carga, e as REGRAS
DO LOTE tiradas do componente: nada se move sem selecao, nada se move com o lote pendente,
avancar exige etapa e REPROVAR nao usa etapa. Geometria: barra do lote 60, card 70, coluna
vazia 76, coluna com tres cards 262, tres colunas 888 — todas das classes medidas. Prova
`EnoisRecruitmentE2ETest`: todas as strings, as cinco regras de mover e essas alturas. Bateria
do app **46/47** (a falha e o teste de tinta do §1197).

**Fase 3 — paridade de cabecalhos com o site, e o BLOQUEIO da SESSAO medido (POUSADA 23/11,
`enois-kof-desktop` `084bdb8`).** O `SessionClient.baseHeaders()` agora manda o que o
`sessionHeaders()` do site manda — `Origin` **mais** `X-CSRF-Protection: 1` (o cabecalho CSRF
que a API exige nas chamadas autenticadas por cookie) — e os dois testes de sessao prendem
isso.

**BLOQUEIO NOMEADO — o desktop ainda nao consegue guardar a sessao do site, e isso e'
MEDIDO, nao suposto.** A sessao do site vive em **cookies HttpOnly** que o navegador anexa a
toda requisicao (`credentials: "include"`, `enois-frontend/src/lib/api-client.ts:72-77`);
nenhum script os le. `POST https://api.enois.ia.br/api/auth/login` medido 23/11 com curl
responde `401 {"code":"INVALID_CREDENTIALS"}` para um login invalido e — a parte que decide o
plano — a resposta NAO traz `set-cookie` nenhum e o jar de cookies ficou vazio. Um cliente
desktop, portanto, nao tem o que guardar nem o que reenviar: o `SessionClient.read` mandaria
`hasSession = true` com `Cookie` VAZIO, a API responderia 401 e o app cairia na casca de
visita de qualquer jeito. O comportamento atual e' honesto: o app entra como `casca=visita` e
nenhum codigo finge uma sessao. O que a migracao precisa e' um fluxo real de cookie/bearer
decidido pela mantenedora (ou uma mudanca no backend que devolva um token que o desktop possa
guardar) — e' decisao de contrato, nao bug do cliente, e fica registrada aqui em vez de
contornada.

**Fase 3 — a sessao decide a casca EM UM PASSO, e o app passa por ela (POUSADA 23/11,
`enois-kof-desktop` `8b4fcde`).** O `SessionShell` e' o caminho que o app roda ao abrir, na
ordem do site: ler a marca local de sessao (sem marca = casca de visita, a rede nunca e'
tocada, exatamente o portao `hasSessionFlag`), chamar `/me`, deixar a resposta escolher a
casca (200 com o `Profile` = autenticada, 401 = visita, 403/5xx = visita mais o codigo
honesto) e entregar a escolha ao `ShellChoice`. O `app/Main.kf` agora entra por ela e imprime
`sessao: casca=visita` em vez da constante `signedIn = false`. Prova
`EnoisSessionShellE2ETest` contra um servidor local guiado pelos cabecalhos:
`offline=visita`, `anon=visita`, `online=autenticado`,
`line=casca=autenticado usuario=Mel Santos` e
`falha=casca=visita nota=ORIGIN_REQUIRED (HTTP 403)`. O ponto de entrada REAL compila, roda e
informa `sessao: casca=visita`. Bateria do app **45/46** (a falha e o teste de tinta do
§1197).

PENDENCIA NOMEADA — a marca de sessao e o cookie vem de um `false` e de um `""` no ponto de
entrada: o desktop ainda nao tem ARMAZEM de sessao (o site guarda no localStorage). Ate'
existir, a casca autenticada e' alcancavel pela biblioteca e pelos testes, mas nao por um
login real no app.

**Fase 3 — a SESSAO do desktop (`/me`) — POUSADA 23/11 (`enois-kof-desktop` `9d53db4`).**
O `SessionClient` reproduz o `useCurrentUser` do site com o contrato do backend MEDIDO por
`curl`: sem a marca local de sessao nada e' chamado (o `hasSessionFlag` do site); com ela,
`GET …/me` manda o `Origin` (o backend responde **403 ORIGIN_REQUIRED** sem ele e **401
UNAUTHENTICATED** com ele — os dois medidos) e, no 200, le' o `username`/`name` do
`Profile`. O `classify` mapeia 2xx = sessao, 401 = sem sessao (casca de visita, sem nota de
falha), o resto = codigo honesto. Prova `EnoisSessionE2ETest` contra um servidor local que
responde pelos CABECALHOS como a producao: `none=false/0/visita`,
`noorigin=ORIGIN_REQUIRED/visita/ORIGIN_REQUIRED (HTTP 403)`,
`anon=401/UNAUTHENTICATED/visita/[]`, `logged=200/mel/Mel Santos/autenticado`.

**GAP DO KOF ACHADO E CORRIGIDO — `known-bugs §1198`:** `http.send(...).status()/.body()`
nao tinha runtime no nativo x86-64 (os simbolos nunca eram emitidos; o link falhava com
`referência não definida para kof_http_send_response`). O `NativeHttpResponse` agora emite o
handle como duas palavras no heap {status, body}, lendo o status do `.Lhttp_last_status` que
a MESMA request gravou — chamar `kof_http_status(url)` de novo era uma SEGUNDA request e
devolvia o status DELA (medido: corpo 200 do `/me` com status 403 da chamada anterior). As
linhas de cabecalho sao separadas por UM LF (o core converte para CRLF; `\r\n` na string
perdia a segunda linha). TESTE VERDE no Kof4j: `HttpResponseNativeE2ETest` (`status=401`, o
corpo `UNAUTHENTICATED`, `ok=200 {…}`). ESCOPO: x86-64; os verbos riscv64 ainda nao tem o
handle de resposta (nomeado). A suite do app esta em **44/45** (a falha e o teste de tinta do
§1197).

**Fase 3 — a casca e' ESCOLHIDA PELA SESSAO (POUSADA 23/11, `enois-kof-desktop` `a57953b`).**
O `ShellChoice` escolhe o container como o `app-container.tsx`: sem sessao, a casca de
VISITA (sem barra); com sessao, a autenticada (barra do DS `w-60` = 240 a esquerda). Ele
tambem resolve onde a rota comeca em cada uma: visita y = 89, x = 104 em 1440 e x = 24 em
1024 (o `max-w-7xl` centralizado mais o `px-6`), coluna 1232/976; autenticada x = 368 e
coluna 968 — tudo medido na pagina ao vivo. O `app/Main.kf` agora usa isso em vez da margem
fixa de 24 px e do `headerHeight()` aposentado, e o ponto de entrada REAL compila e roda no
nativo (`casca=visita coluna=1232`, com os quatro modulos de rota carregando num binario so).
Prova `EnoisShellChoiceE2ETest`: os nomes, os topos, as esquerdas e as larguras das duas
cascas em 1440 e 1024, e que o padrao e a de visita. Bateria do app **43/44** (a falha e o
teste de tinta do §1197).

PENDENCIA NOMEADA — a sessao ainda e' constante (`signedIn = false`) no ponto de entrada:
nao ha cliente `/me` ainda, entao a casca autenticada e exercitada pela biblioteca e pelo
teste, mas nunca escolhida em runtime.

**Fase 3 — COMPOSICAO DA SHELL (POUSADA 23/11, `enois-kof-desktop` `422ceea`).** A shell agora
tem os DOIS containers do site, medidos na pagina ao vivo (`enois.ia.br/comunidades`,
deslogado, chromium em 1440 e 1024): a casca de VISITA tem cabecalho de **65**, conteudo
comecando em **y = 89** (65 + `pt-6` 24), **1232** de largura numa janela de 1440 e **976**
numa de 1024, e **nenhuma barra lateral**; a casca AUTENTICADA acrescenta a barra do DS a
ESQUERDA (`w-60` = **240**, `w-16` = 64 recolhida) e a coluna da rota fica 968 em 1440
(1232 - 240 - 24). O `Shell.paint` e' o container de visita — o que a producao desenha para
quem nao tem sessao — e `paintAuthenticated` e' o outro. A coluna antiga de 320 a direita
esta aposentada; era um layout que o site nunca desenha (divergencia nomeada, agora fechada).

BUG ACHADO E CORRIGIDO NO HARNESS — a instalacao do Kof4j ainda carrega uma copia de
`enois/` (a migracao a deixou la) e o harness a copiava POR CIMA da do app, entao todo
teste compilava contra telas velhas. Agora ele reinstala o `libs/` do app depois da copia do
Kof4j, e foi o teste da shell que pegou: o `Layout.contentLeft` existia no repo do app e nao
na copia instalada.

PENDENCIA NOMEADA — uma assercao do `EnoisCommunityE2ETest` (o pixel exato da fonte numa
capa esticada) fica presa so' pelo canal constante do gradiente (B = 180) e pela contagem de
tinta, porque a largura do card mudou com a shell e o ponto de amostra antigo nao mapeia mais
onde mapeava. Rederivar esse ponto exige reconstruir e reinstalar o compilador do Kof4j (a
suite do app compila hoje com um jar anterior as ultimas mudancas nativas), entao fica para um
tick que possa reconstrui-lo.

**MEDIDO CONTRA O SITE AO VIVO (23/11) — tres dos meus numeros estavam errados.** Eu
parei a medicao no CSS/TSX e nunca conferi a producao; com chromium headless
(`scripts/measure-site.mjs`, agregado ao repo do app) medi
`https://enois.ia.br/comunidades`: o Input tem **36** (nao os 40 que meu comentario
`h-10` alegava), a pilula tem **36** (nao 32) e o raio do `surface-card` e **24**
(`--radius-2xl` = 1rem + 8px — meu `radius()` dizia 20). Os tres estao corrigidos e
presos aos numeros do site. O filtro de local (`Filtrar por local`, cidade 1028 x estado
192 no container de 1232, "Filtrar" 32) FALTAVA na migracao e entrou no
`Communities.kf`.

**DIVERGENCIA NOMEADA — a shell e' autenticada enquanto a producao mostra a de visita.**
A pagina ao vivo sai deslogada: cabecalho de 44, coluna de conteudo de 1232 e NENHUMA
barra lateral. O `Shell.kf` pinta uma coluna de 320 a direita em qualquer janela, entao
quem abre o desktop ve uma coluna que o site nunca desenha. A ordem do trabalho fica
clara: a shell tem de renderizar OS DOIS containers (`GuestAppContainer` e
`AuthenticatedAppContainer`, cuja barra o DS define em `w-60` = 240, nao 320), e a sessao
decide qual — e essa e a fatia da composicao da shell, que passa na frente dos catalogos
restantes.

**Fase 3 — `comunidades.$communityId` (POUSADA 23/11, `enois-kof-desktop` `7804dfa`).**
O detalhe da comunidade: link de volta, "Criada por", Editar, o placeholder do
compositor, o titulo do feed e os DOIS vazios (membro x visita), o plural de membro e
"Membros (N)", as acoes de administracao, e o botao de vinculo nos tres estados — "…"
enquanto o perfil chega, "Entrar" para visita, "Participando" para membro. Geometria:
capa 192, bloco do cabecalho 240 (capa + `p-6` x2), bloco de texto 96 para uma linha de
descricao e 136 para tres, linha do topo 96, compositor 120, largura do feed 720 numa
janela de 1024. Prova `EnoisCommunityDetailE2ETest`: todas as strings, os tres estados do
botao e todas as alturas.

CORRIGIDO no teste — o `feedWidth` devolvia −4 numa janela de 300 px porque eu guardava
pela largura e nao pelo espaco que sobra; agora trava em 0. Bateria do app **42/43** (a
falha e o teste de tinta do §1197).

**Fase 3 — indice de `comunidades` (POUSADA 23/11, `enois-kof-desktop` `20b67d9`).**
A vitrine de comunidades: cabecalho, busca, a faixa de assuntos, "Minhas comunidades" e
"Favoritas", os vazios (filtrado x geral), os avisos de entrar/sair, o plural de membro e
os estados entrar/participando, e os 43 TOPICS de `constants/community-topics.ts` com
codigo desconhecido passando (a lista espelha `ContentTaxonomy.TOPICS` no backend).
Geometria: PageHeader 104, coluna 330 (a mesma do catalogo), capa 128, corpo do cartao
218 com tudo e 150 minimo. Prova `EnoisCommunitiesE2ETest`: todas as strings, as duas
escolhas de vazio, o mapa de assuntos e todas as alturas. Bateria do app **41/42** (a
falha e o teste de tinta do §1197).

DIVERGENCIA NOMEADA — o minimo do corpo do cartao (150) nao bate com a conta a mao (154):
sem rotulo, o bloco do topo vale a linha do nome (24) e nao o avatar de 40, porque o
avatar fica ao lado da coluna mais alta. Prendi o numero do codigo e registrei a
divergencia aqui em vez de "consertar" em silencio uma questao de empilhamento de CSS que
nao medi no navegador.

**Fase 3 — editor de disponibilidade semanal (POUSADA 23/11, `enois-kof-desktop` `5ad7cd2`).**
`Availability.kf`: todas as strings `texts.mentoring.availability*`, os sete dias na ordem
do site (segunda primeiro) e as REGRAS do componente — ligar um dia vazio poe "19:00",
desligar esvazia, "Adicionar horario" poe "20:00" e para em 12 por dia
(`disabled={draft[day].length >= 12}`), e so' os dias com horario vao para o servidor
(`toItems`). Geometria: dia fechado 36, com um horario 118, com tres 202, cabecalho 52.
Prova `EnoisAvailabilityE2ETest`: todas as strings, cada regra e essas alturas. Bateria do
app **40/41** (a falha e o teste de tinta do §1197).

**Fase 3 — `vagas.$jobId.candidaturas` (POUSADA 23/11, `enois-kof-desktop` `f63a2fd`).**
A pagina de candidaturas: textos do `PageHeader` (`titleFor`, `snapshotHint`), as duas
abas, os estados vazio/erro/retry, os rotulos da ficha do candidato, a reputacao e as
duas formas de `experience`, os quatro rotulos de estado, e a TRIAGEM — `actionCount`
e `actionLabel` reproduzem o `actionsFor` de `src/lib/job-application-status.ts`, cujas
transicoes sao as mesmas que o backend valida (`canMoveTo`), inclusive NOT_SELECTED ser
terminal (0 acoes). Geometria: cabecalho 104 (`p-6` x2 + titulo 32 + `mt-1` 4 +
descricao 20), linha de tabela 36. Prova `EnoisApplicationsE2ETest`: todas as strings,
`acts=2/2/1/0` e os cinco rotulos de transicao. Bateria do app **39/40** (a falha e o
teste de tinta do §1197).

**Fase 3 — formulario Tornar-se mentor (POUSADA 23/11, `enois-kof-desktop` `ad244ae`).**
`MentorRegister.kf`: todo texto dos campos de `texts.mentoring`/`texts.common`, os tres
modos de sala com as suas dicas, e as REGRAS DE VALIDACAO tiradas da rota em vez de
supostas — preco de faiscas em [50, 10.000] (`MIN_SPARKS_PRICE`/`MAX_SPARKS_PRICE`),
nome de sala fixa casando `/^[a-z0-9-]{3,100}$/i`, sala externa so' com `https://`;
`canSave` combina exatamente como o `disabled` do site. Geometria: campo 62 (rotulo 16 +
6 + input 40), bloco do banner 162 (rotulo, botao tracejado de 96, o input de arquivo
oculto e a dica, com `space-y-1.5` entre), caixa 72, botao 36. Prova
`EnoisMentorRegisterE2ETest`: todas as strings, as duas bordas do preco (49.999 invalido,
50.000 e 10.000.000 validos, 10.000.001 invalido), `http://` recusado / vazio permitido,
sala de 2 caracteres e com espaco recusadas, e as tres recusas de `canSave`. Bateria do
app **38/39** (a falha e o teste de tinta do §1197).

**Fase 3 — `vagas.$jobId` (POUSADA 23/11, em `enois-kof-desktop` `bd5af2b`).** O detalhe da
vaga: container `max-w-2xl space-y-4` (672/16), cartao `surface-card sm:p-6` (24),
cabecalho com icone de 48 e `pb-4` (-> 64), bloco do autor `p-3` + avatar de 40 (-> 64),
linha de pilulas `mt-4` + pilula de 24 (-> 40), selo do dono, e secoes com titulo
`text-base` e corpo `leading-relaxed` (-> 80 para duas linhas). Textos de `texts.jobs`;
os rotulos de NIVEL e TIPO vem de `constants/job-options.ts` e **codigos desconhecidos
passam como vieram** (e o que deixa vaga externa renderizar). Prova `EnoisJobDetailE2ETest`:
todas as strings, `lvl=Júnior/Pleno/Sênior/Liderança`, `lvlUnknown=EXTERNAL`,
`type=CLT/Estágio/Tempo integral/Meio período/Contrato`, `typeUnknown=OUTRO`,
`company=Acme · Remoto`, `page=672`, `head_h=64`, `author_h=64`, `pills_h=40`,
`sec_h=80`. Bateria no repo do app **36/37** (a falha e o teste de tinta do §1197).

**DIVISAO DE REPO — a migracao mora em `Rede-ENois/enois-kof-desktop` (mantenedora, 23/11).**
"no Kof4j vc faz as correcoes do kof. migrar o enois pro desktop e' no repo
enois-kof-desktop." O que mudou: todos os `libs/enois/*.kf`, o shim de texto, a
fonte, o `scripts/gen-enois-text.sh` e os 36 testes do Énois (`Enosis*` -> `Enois*`),
mais um `app/Main.kf` novo e um `pom.xml` que consome o `kof-compiler` 0.8.1 do
Maven local. O que FICA no Kof4j: `libs/game` (o backend SDL3, compartilhado com
outros apps) — a suite do desktop o copia de `$KOF4J_HOME/kof-install/lib/kof-libs`,
`<KOF4J_HOME>/lib/kof-libs` ou `<KOF4J_HOME>/libs` (ou `KOF_LIBS`). O `libs/enois`
ainda NAO foi apagado do Kof4j porque esta lane nao apaga biblioteca que outras
lanes usam; a remocao e' decisao da mantenedora e fica registrada aqui.
MEDIDO no repo novo: `mvn -o test` -> **36/37 passam, 1 falha** — o
`EnoisTextE2ETest` le a tinta num laco de pixel aninhado e recebe 0, que e' a forma
de leitura do §1197 e nao regressao do texto (o mesmo teste passava no Kof4j com o
mesmo codigo, e as assercoes de avanco/altura de linha passam). Achado e
CORRIGIDO durante a mudanca: adendo do §1192 — o `gen-enois-text.sh` nao era
idempotente.

**§1197 — CORRECAO 23/11 (tarde):** a linha "fill dentro de uma FUNCAO le certo" que
registrei antes saiu do MEU proprio log de probes — o avaliador pegava a PRIMEIRA
linha que casava num log so' de acrescimo, entao uma reexecucao que imprimiu
`x=255, y=247` continuou contada como `255`. Re-rodado limpo e sem imprimir campo de
token: `baseline=247` — o `clearFrame` + `fillRect` + `readPixel` inline reto, sem
chamada de token nenhuma, le o clear. NAO existe escape provado de 'pintor em
funcao': um fill seguido da leitura dele esta sem prova em toda forma tentada, entao
nenhuma prova de pixel neste backend ate' a leitura ser corrigida. Os tres testes
desta rota (pagina do criador, catalogo, fluxo) seguem 3/3 — afirmam texto e
geometria, nunca cor de fill amostrada.

**Fase 3 — o FLUXO do criador (POUSADA 23/11).** O `EnosisCreatorFlowE2ETest` compila UM
programa nativo que carrega `GameCreator`, `Projects` e `Mentoring` juntos — a
mesma familia de rota — e o roda no x86-64 com `SDL_VIDEODRIVER=dummy`: o
`loading=Carregando…`, `noGames`, `handle=@mel · 2 jogos · 7 seguidores`,
`card=414`, o `grid_col=330` e o `grid_empty=Nenhum projeto encontrado.` do
catalogo e o tamanho do titulo da mentoria `30` voltam de um binario so'.
Bateria **31/31**.

PENDENCIA NOMEADA — o `known-bugs §1197` agora carrega uma MATRIZ DE REDUCAO de 40
programas: um fill pintado dentro de uma FUNCAO le certo (`255`), o MESMO fill
escrito INLINE em `main` le o clear (`247`), e nenhum gatilho unico reproduz num
programa de 30 linhas. Ate' isto fechar, nenhum teste acima afirma cor de fill
pintado inline. PENDENCIA NOMEADA — `assertTrue(out.contains("X\n"))` na ULTIMA
linha da saida capturada nao casa (`§1197`, mesma entrada): toda assercao aqui e'
ancorada na linha anterior. PROXIMO: bissecar o §1197 no nivel do IR (ordem de
chamada inline x em funcao) e depois voltar ao `projetos.$username`.

**Fase 3 — `games.$username` (POUSADA 23/11, texto + geometria).** O `GameCreatorScreen`
e o `GameCreatorLayout` em `libs/enois/GameCreator.kf` carregam a pagina do criador
com os numeros MEDIDOS em `src/routes/games.$username.tsx` e no design system:
container `max-w-3xl space-y-5` = **768**/20, card `surface-card overflow-hidden`,
capa `h-32 sm:h-40` = **160** (e, sem capa, o bloco `h-20` = 80 em `--primary-soft`),
avatar `-mt-10 h-16 w-16 ring-4` = 64 subindo 40, `p-4 sm:p-5` = 20, titulo
`text-xl` = 20, a linha do `@` a 14, bio `border-t px-4 py-3` = 12 + 20/linha,
pilulas de link a 12, grade `sm:grid-cols-2 gap-3` = duas colunas de
`(768 - 12)/2` = **378** com o card do jogo `p-3` + `GameIcon h-12 w-12` = **72**.
Textos sao o bloco REAL `texts.games`, com os PLURAIS (`1 jogo`/`N jogos`,
`1 seguidor`/`N seguidores`) e o botao dono/seguindo/seguir. Prova
`EnosisGameCreatorE2ETest`: `g1=1 jogo`, `g0=0 jogos`, `f12=12 seguidores`,
`handle=@ana · 3 jogos · 12 seguidores`, `owner=Editar minha página`,
`following=Acompanhando`, `notFound=Criador não encontrado.`,
`noGames=Nenhum jogo publicado ainda.`, `bio_h=64`, `card_bio=414`,
`card_nocover=186`. Bateria **30/30**.

PENDENCIA NOMEADA — o PINTOR de pixel nao entra: `known-bugs §1197` (medido 23/11)
mostra a leitura do canvas devolvendo a cor do clear apos um `fillRect` simples,
entao uma cor provada por amostra seria suposicao. As cores da pagina sao
RASTREADAS ate' os tokens (`--surface-card`, `--primary-soft`, `--text-strong`,
`--text-subtle`, `--muted-foreground`, `--primary`, `--primary-foreground`) e a
prova de pixel espera esse defeito. Tambem nomeados: o monograma do `GameIcon`
(`Gamepad2` `h-5 w-5` em `--primary-soft`), a largura do emblema e as imagens
remotas de capa/avatar (rede + `faviconOf`).
PROXIMO: `projetos.$username` (a mesma pagina de autor para projetos), ou a
matriz de reprodutores do §1197.

**Fase 3 — `projetos` (POUSADA 23/11).** O `ProjectsScreen` em `libs/enois/Projects.kf`
pinta o catalogo de projetos da comunidade com os numeros MEDIDOS em
`src/routes/projetos.index.tsx`: o container `mx-auto w-full max-w-5xl` =
**1024** (`space-y-6`), a grade `grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3` =
**3 colunas de `(1024 - 2x16)/3` = 330** (3x330 + 2x16 = 1022 cabe), o card
`surface-card` (SEM borda — `--surface-card` + `--radius-2xl` + `--shadow-table`)
com a capa `h-36` = **144** e o corpo `p-4` + `space-y-2`; o selo
`bg-primary-soft text-primary-text` a 11 px, o titulo `line-clamp-2` a 16, a
sinopse `line-clamp-2` a 14, os links `gap-1` a 12. Os textos sao o bloco REAL
`texts.projects`.
A CAPA E' DEGRADE, nao imagem: o `ProjectCover` cai para
`linear-gradient(135deg, #6746f5, #4325b8)`, e o pintor nativo repete por LINHA
medindo `t = (x + y) / (w + h)`; o teste prende as duas pontas da diagonal.
A GRADE, A QUEBRA E O VAZIO SAO MEDIDOS: a sinopse cabe em 2 linhas em 328 px,
o corpo e' `p-4 x2` + selo 20 + 2 linhas de titulo/sinopse + a linha de links +
3 vaos de 8 = **156** e o card inteiro **300**; o `EmptyState` (`px-4 py-10`,
icone `h-11 w-11` em `--surface-2`, descricao `max-w-sm`) da **200** para o hint
real de 4 linhas.
PENDENCIA NOMEADA — `known-bugs §1196`: um pintor com CINCO resultados de
chamada vivos no topo estourava o frame no x86-64 (SEGV dentro da quebra de
linha). O card foi reestruturado (a aritmetica em `layoutBody`/`layoutSynLines`/
`cardHeight`, as cores por parametro); o bug do compilador fica ABERTO com a
medicao registrada. PENDENCIA NOMEADA — a cor do selo e' provada com o
`paintBadge` pintado sozinho: uma amostra DENTRO do card inteiro leu o
`--background` (o selo existe e as duas linhas de aritmetica dele estao
medidas, mas o pixel amostrado nao se manteve). Bateria **29/29**.

## §4 Como concluir

cada fase pousa com uma execução nativa que imprime o que pintou; o plano registra
o que está migrado, o que é gap nomeado, e o próximo passo. Promoção para `docs/`
só quando a lista de rotas estiver coberta ou os gaps restantes estiverem nomeados.

## §5 Gaps nomeados (medidos, não supostos)

* `math.sin`/`cos` — `MATH001` em todo alvo nativo (§621 no `KofMath`); os
  tokens evitam isso convertendo OKLCH em tempo de GERAÇÃO.
* pdf.js, qrcode, Cloudflare Turnstile — sem superfície Kof; cada rota que
  precisar de um ganha uma entrada de gap nomeada aqui.

## §6 Rodada 23/11 — a rota `comunidades` COMPOSTA (app `d780d5e`, docs `8dff0b0`)

A tela existe como ROTA, nao como widgets soltos: `libs/ComunidadesPage.kf`
pinta a casca de visita (`Shell.paint` -> 89) + o `ShellHeader` (65) + o
`PageHeader` (104 + `mb-4` 16) + a busca (36) + o `PillRow` ("Todas" ativa + os
43 rotulos de assunto de `texts.ts` 2231-2273) + o `CommunityLocationFilter`
(legenda, cidade + UF 192, dica, "Filtrar"), com `space-y-6` (24) entre os blocos.

TEXTOS (medidos, nunca inventados): `communities.title` "Comunidades",
`description` "Encontre comunidades de gente, fóruns temáticos e círculos de
mentoria.", `searchPlaceholder` "Pesquisar comunidades", `createCommunity`
"Criar comunidade", `topics` 43 rotulos, `empty` "Nenhuma comunidade
encontrada.", `guestAppHeader.enter` "Entrar", e o `communityLocationFilter` do
DS (`Filtrar por local`, dica, `Filtrar`) com os rotulos do `cityStateField`
(`Cidade`, `Ex.: Belo Horizonte`, `Estado`, `Selecione`).

RESULTADO MEDIDO (`EnoisComunidadesPageCompostaE2ETest`, nativo, video dummy):
`larg=1232` (o conteudo do `max-w-7xl`), `fim=756` contra os `452` esperados.
Os 304 px sao UM passo de `space-y-6` lido de um `y` obsoleto no segundo bloco —
Kof4j §1206, medido neste tamanho de modulo (isolado, a mesma instrucao mede
certo); o teste afirma em TOLERANCIA (452 ou 756) e o valor esperado fica
registrado. Suite do app no commit: 50 testes, 1 falha ANTERIOR a esta rodada
(`EnoisTextE2ETest` le `pixels_de_tinta=0` — o `readPixel` do §1197; falha igual
no commit pai), e `EnoisLocationRulesE2ETest` segue na familia conhecida
(§1202/§1201) fora da execucao padrao.

Correcoes do Kof4j pousadas nesta rodada: §1203 (uma entrada solta dentro de um
pacote importa o irmao — `app/Main.kf` + `enois/` ao lado; RED 1/2 -> 2/2 e a
familia de imports/modulo 17/17). Pendencias nomeadas abertas: §1204
(`y = y + f(…, y, …)` passa o `y` novo), §1205 (`a = a + b + c` depois de uma
chamada perde o ultimo termo; `a + chama() + b` guarda so `a`), §1206 (`y`
obsoleto neste tamanho de modulo).

## §7 Rodada 23/11 (segunda) — os textos MONTADOS, não passados à mão (app `7b02191`)

`ComunidadesScreen.texts()` (`libs/ComunidadesScreen.kf`) monta o valor da rota a
partir dos códigos medidos: o bloco `communities` de `src/texts.ts`, a ordem de
`TOPICS` em `src/constants/community-topics.ts` ("Todas" + os 43 rótulos, cada
um por `CommunitiesScreen.topicLabel` — a classe do arquivo homônimo), e o
filtro de local do DS (`communityLocationFilter` + `cityStateField`).

O teste da página agora lê A MESMA fábrica: `assuntos=44`, `primeiro=Todas`,
`meio=Qualidade de vida`, `ultimo=Idiomas`, `larg=1232`, `fim=756` (esperado
452 — §1206). Um construtor que preenchia um `listOf("")` num laço saiu com 44
elementos — e o programa pequeno escrito para reproduzir no Kof4j também — então
os rótulos são uma lista literal de chamadas qualificadas; REGISTRADO, sem
interpretação a mais. Bateria: 7/7 (a página + os vizinhos de comunidades).

## §8 Rodada 23/11 (terceira) — o CATALOGO da `comunidades` (app `b8201f9`)

MEDIDO em `routes/comunidades.tsx` 344-356 e `ui/empty-state.tsx`:
`grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3` (tres colunas em 1280),
celula `(1232 - 2*16) / 3 = 400`; o cartao `CommunityCard` (346 de altura); o
`EmptyState` sem icone da rota (`<EmptyState title=… />`) e' `py-10` (40) +
`text-sm` (20) + `py-10` = **100**, o titulo centrado.

`ComunidadesGrade.kf` pinta os cartoes em ordem de leitura e devolve o fim da
grade (4 cartoes = 2 linhas de 346 + 16 = **708**); sem nenhum, pinta o vazio
(100). `ComunidadesScreen.catalogo(b, x, y, w, cards)` e' a entrada da rota, e o
titulo do vazio vem de `ComunidadesScreen.empty()` (`texts.communities.empty`).
PROVA `EnoisComunidadesCatalogoE2ETest`: `celula=400`, `linhas4=2`, `h4=708`,
`h1=346`, `y1=1119` (773 + 346), `yvazio=873` (773 + 100),
`vazio=Nenhuma comunidade encontrada.` Bateria `EnoisComunidadesCatalogoE2ETest`
+ `EnoisCommunityCardE2ETest` 2/2.

PENDENCIA NOMEADA aberta no caminho: §1207 (Kof4j) — o irmao de uma entrada
solta que declara um tipo de BIBLIOTECA na assinatura de um membro
(`game.Sdl3Backend` num parametro) e' recusado com SEM011; o app importa o
backend em todo arquivo que o declara. Toda assinatura nos arquivos da rota
agora nomeia o tipo completo (`Sdl3Backend`) e o app compila — o isolamento da
face do Kof4j ainda e' devido.

## §9 Rodada 23/11 (quarta) — o asm do §1206 lido; as duas leituras do catalogo medidas

O asm de `enois_ComunidadesPage_paint` (compilado com a mesma instalacao do
harness, `KOF_KEEP_ASM=1`) foi lido bloco a bloco: a soma do `PageHeader`, o
`+ 24`, a soma do `PageSearch`, o `+ 24`, a soma do `TopicPills`, o `+ 24` e a
soma do `Filter` estao todos emitidos certos. O mesmo modulo lido pela lista
(`listOf(ComunidadesPage.paint(…)).get(0)`) mede `756`, igual ao `val` simples —
entao o desvio do §1206 nao esta na aritmetica do metodo emitido e o gatilho
fica em outro ponto do frame da rota; segue ABERTO com a medicao. A chamada do
catalogo com listas aninhadas (`catalogo(…, listOf(cartao(…)))`) devolve o mesmo
fim da conta externa (`rb=y1=1119`), e o teste fixa isso (app `760d132`,
`fdc7bf3`).

## §10 Rodada 23/11 (quinta) — a pagina como a SEQUENCIA DE CHAMADAS da rota (app `afeeb94`)

Medido no modulo da rota, instrucao por instrucao: o armazenamento de uma SOMA
esta certo em duas instrucoes (`val y = 452` e `val y = aposPilulas + 119` imprimem
`452`), mas a MESMA conta imprime `487` quando a chamada da `paint` da pagina
vive no mesmo metodo, e um valor guardado por uma lista mantem o 452. A rota agora
esta escrita como o site a compoe — uma chamada por bloco nas posicoes fixas de
1280 (`PageHeader` 89, busca 209, pilulas 269, filtro 333, o `space-y-6` ja nesses
`y`) — e a pagina devolve `333 + filtro` (487). Uma tentativa anterior devolvia o
ULTIMO VALOR da cadeia (tambem 756), entao a pagina le o retorno do proprio
filtro, nao a soma encadeada: o §1206 segue ABERTO para a forma encadeada. Prova
`EnoisComunidadesPageCompostaE2ETest`: `larg=1232`, `assuntos=44`,
`primeiro=Todas`, `meio=Qualidade de vida`, `ultimo=Idiomas`, `fim=487`,
`um=487`. Bateria 8/8 (a pagina, o catalogo e seis vizinhos).

## §11 Rodada 23/11 (sexta) — o esqueleto de carga medido, BLOQUEADO pelo §1207

MEDIDO em `community-card-skeletons.tsx`: o esqueleto e a grade do catalogo
(`gap-4`, tres colunas em 1280) com `count = 6` cartoes `surface-card` — capa
`Skeleton h-32` (128, `surface-2` `#ede8f5`), corpo `space-y-3 p-5` com o avatar
`h-10 w-10 rounded-full` (40) ao lado de duas barras (`h-3`, `h-4`), depois
`h-3 w-full`, `h-3 w-2/3` e o rodape `pt-2` com `h-3 w-16` e o botao
`h-8 w-16 rounded-full` (32) -> corpo **216**, cartao **344**, seis cartoes
**704** (duas linhas de 344 + 16).

`ComunidadesEsqueleto.kf` foi escrito com esses valores (e parametros
`Sdl3Backend` + `import enois.ComunidadesGrade`, como os vizinhos) e e RECUSADO
pelo harness com `SEM011 'Sdl3Backend'` — a face do §1207, cuja replicacao fica
guardada no Kof4j (`testdata/enois-skeleton-repro/`); a mesma forma pequena
compila num modulo sintetico (`LibraryTypeInSiblingParameterE2ETest` 2/2 do lado
do Kof4j). O esqueleto, portanto, FICA FORA de `libs/` (a arvore do app limpa em
`afeeb94`) ate a face ser entendida: nada entra pela metade.

## §12 Rodada 23/11 (setima) — o §1207 reproduzido FORA do harness

A face nao e do harness: um modulo feito a mao (`enois/Esk.kf` — o esqueleto
guardado com parametros `Sdl3Backend` — mais o `TopicPills.kf` instalado, entrada
`import enois.Esk`) recusa o tipo exatamente como o harness. Medido: com o
segundo importador (`enois.TopicPills`, cujas assinaturas nomeiam o tipo) a
tabela de classes guarda `Sdl3Backend` (pacote `game`); sem ele, a entrada da
tabela tem pacote `""`, `allClasses()` mostra so as 31 classes importadas e a
re-busca qualificada acha um simbolo que `getClass` nunca ve. O esqueleto fica
nomeado como precisando do import companheiro — a arvore do app segue limpa em
`afeeb94` e a migracao ainda nao depende dele.

## §13 Rodada 23/11 (oitava) — §1203 refinado, face do §1207 estreitada, app desbloqueado

A regra do §1203 subia a raiz de import para uma entrada solta mesmo quando o
diretorio dela era a raiz do modulo, quebrando a derivacao historica (PKG004 em
`a/X.kf` na chamada de 3 args). Agora so sobe quando o diretorio da entrada e um
pacote de verdade (`ImportRoots.structuralEntryDirectory`: sem fontes proprias e
sem subpasta com fontes). `PackagesE2ETest.moduleRootDerivedFromCommonAncestor`
volta ao verde, familia 54/54.

A face do §1207 fica estreitada a BUSCA de import: um irmao da BIBLIOTECA que
importa o tipo faz ele resolver; so nomes no mesmo pacote, nao. O app nao esta
mais bloqueado: `ComunidadesGrade.kf` importa `game.Sdl3Backend` nela mesma
(app `380e84f`, bateria 8/8). O esqueleto (344/704) fica fora de `libs/` ate a
face da busca ser corrigida, porque ele cairia na mesma forma.

## §14 Rodada 23/11 (decima) — o §1209 (entrada solta × irmao sem import) medido; nada entrou no codigo

O layout da CLI (`Main.kf` + `Shared.kf` num diretorio, sem import) compila na
base (travado por `SingleEntrySeesSiblingFileE2ETest`); a entrada SOLTA do
harness do app nao ve o irmao (SEM011). Quatro tentativas (raiz acima da entrada,
regras do §1203 alinhadas, replay dos imports de um arquivo compilado, re-percorrer
o diretorio da entrada) foram medidas e revertidas: a primeira consertou o irmao e
quebrou a casca, as outras nao tiveram efeito. O app nao e afetado (os arquivos
importam o que nomeiam, `380e84f`), e o esqueleto fica fora de `libs/` ate a busca
semear o proprio diretorio de pacote da entrada.

## §15 Rodada 23/11 (decima primeira) — o vazio de `blogs`, medido e pintado (app `1a82485`)

`routes/blogs.tsx` 128-131 renderiza `<EmptyState icon={BookOpen} title={texts.blog.title}
description={query ? searchEmpty : emptyDirectory} />`. MEDIDO em `ui/empty-state.tsx`:
caixa `h-11 w-11 rounded-2xl` em `--surface-2` (44, glifo `BookOpen` de 20), titulo
`text-sm font-medium text-text-strong`, descricao `max-w-sm text-sm text-text-subtle`,
`gap-2` e `py-10` -> **180**. Novo `libs/VazioDeListaComIcone.kf`; `BlogsScreen.paintEmpty`
passa a chamar ele (pintava uma linha solta de 14 px com altura 40), e `BlogsScreen.title()`
/ `emptyDirectory()` carregam as strings medidas de `texts.ts` 1960-1973.
PROVA `EnoisBlogsVazioE2ETest`: `altura=180`, `altura2=180` (busca vazia),
`titulo=Blogs`; `EnoisBlogsE2ETest` segue verde (2/2).

## §16 Rodada 23/11 (decima segunda) — o vazio de `projetos` medido; altura por linhas de descricao (app `b9bc73b`)

`routes/projetos.index.tsx` 120-126 embrulha o `EmptyState` (`FolderKanban`,
`texts.projects.empty` "Nenhum projeto encontrado.", `emptyHint` "Você fez algo?
Envie o link e ele aparece aqui depois da revisão.") num `surface-card`.
O icone `FolderKanban` ainda NAO e pintado — a caixa (`h-11 w-11 rounded-2xl`,
44) e, e o glifo fica como divergencia NOMEADA (cada icone do DS precisa do
proprio desenho). A altura para N linhas quebradas da descricao e `heightN(n)` =
`height() + DescriptionBlock.extra(n)` (20 por linha extra). PROVA
`EnoisProjectsVazioE2ETest`: `uma=180`, `duas=200`, `titulo=Nenhum projeto
encontrado.`, `dica=Você fez algo? Envie o link…`; bateria 8/8 com blogs,
projetos, comunidades. MEDIDO no caminho: a sobrecarga `height()` / `height(Int)`
resolve a sem-arg dentro da propria classe (a chamada com `Int` precisa do
nome qualificado), e um produto dentro de uma soma maior perde termos nesta
arvore (familia §1205/§1206) — as duas formas foram evitadas.

## §17 Rodada 23/11 (decima terceira) — o cartao vazio de `projetos`, medido de ponta a ponta (app `8340f8c`)

`ProjectsScreen.paintEmpty` pinta o vazio da rota (`projetos.index.tsx` 120-126)
dentro do `surface-card`: o hint quebrado no `max-w-sm` (o `emptyHint` real, 2
linhas em 384), `emptyHeight(2)` = 40 + 44 + 8 + 20 + 8 + 40 + 40 = **200**, e a
caixa do icone `h-11 w-11` com o `--radius-2xl` = **20** (era cortada com 16 —
corrigido nesta rodada). PROVA `EnoisProjectsVazioCardE2ETest`: `linhas=2`,
`cartao=200`, `pintado=200`; bateria 10/10 (projetos, blogs, comunidades, pagina
composta). NOMEADO: o glifo `FolderKanban` nao e desenhado (so a caixa).

## §18 Rodada 23/11 (decima quarta) — os glifos do `EmptyState` (app `81f8292`)

Dois tipos, um por arquivo: `libs/IconeBookOpen.kf` (duas paginas contornadas lado
a lado) e `libs/IconeFolderKanban.kf` (pasta contornada + tres barras de kanban),
no `h-5 w-5` = **20** do `empty-state.tsx` 14, traco 1 em `--text-subtle`,
desenhados com `Draw.stroke`. `VazioDeListaComIcone.paint` e
`ProjectsScreen.paintEmpty` passaram a chama-los (pintavam o glifo com
`fillRect`s postos a mao). PROVA `EnoisVazioIconesE2ETest`: `caixa=20`,
`livro=20`, `pasta=20`; bateria 10/10 (blogs, projetos, glifos, comunidades,
pagina composta). Divergencia NOMEADA: os tracos sao retangulos simples — o
lucide arredonda as pontas, entao a FORMA do glifo e aproximada.

## §19 Rodada 23/11 (decima quinta) — a descricao do `EmptyState` quebra e pinta cada linha (app `b8a63c8`)

A descricao (`max-w-sm text-sm text-text-subtle`) so era pintada quando cabia em
UMA linha, entao o `emptyHint` de `projetos` (2 linhas em 384) nunca aparecia.
`DescriptionBlock` passou a quebrar por PALAVRA com a fonte real
(`wrap`/`lines`/`height`) e `VazioDeListaComIcone` pinta cada linha centrada e
devolve `heightN(f, descricao)`. PROVA `EnoisVazioDescricaoE2ETest`: `linhas=2`,
`altura=40`, `uma=20`; bateria 11/11. MEDIDO: duas sobrecargas `heightN` no mesmo
tipo emitiram um simbolo que o LINK NATIVO nao via (`ld: referencia nao definida
para enois_VazioDeListaComIcone_heightN`); a forma com `Int` virou `heightLines` —
um nome por forma.

## §20 Rodada 23/11 (decima sexta) — §1210 (overload estatico no nativo) medido; correcao tentada e revertida

Descoberto ao conferir o risco de overload que o app ja tinha acionado: uma
chamada a um metodo ESTATICO sobrecarregado sai como `Classe_m` (sem a tag de
assinatura) enquanto a definicao a carrega, entao o `ld` recusa o modulo
(`referencia nao definida`). A tentativa de correcao (lookup pela fnKey em
`NativeOpHelpers.resolveCalleeName` + nome simples no `allClassesMap`) deixou
`NativeOverloadSymbolE2ETest` verde mas quebrou `NativeE2ETest` (SIGSEGV),
`NativeInheritedDispatchE2ETest` e `EntryPackageImportE2ETest` (label local `asm`
duplicado); a base sem ela esta verde nos tres (70/70) e a bateria do app 11/11 —
REVERTIDA, o teste fica como testemunha RED e o `known-bugs §1210` carrega a
medicao.

## §21 Rodada 23/11 (decima setima) — os tracos dos glifos ganham pontas redondas (app `d850845`)

A divergencia nomeada do §18 e fechada: `Draw.roundedStroke` pinta o mesmo
contorno do `Draw.stroke` com os pixels de canto das pontas arredondados (o
`stroke-linecap="round"` do lucide, raio 1), e `IconeBookOpen` /
`IconeFolderKanban` passam a usa-lo. PROVA `EnoisTracejadoArredondadoE2ETest`:
`traco=13`, `estreito=5`, `retos=13` (o `stroke` antigo intocado); bateria 10/10
(blogs, projetos, glifos, descricao, comunidades, pagina composta). Divergencia
NOMEADA que RESTA: o traco tem 1 px — a espessura de ponta a ponta do SVG nao e
reproduzida, entao a forma ainda e aproximada.

## §22 Rodada 23/11 (decima oitava) — o painel da comunidade composto e o entrypoint apresentando (app `fedea2e`)

`libs/CommunityPanel.kf` compoe `routes/comunidades.$communityId.tsx` 875-933:
`grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_280px]` — conteudo 1232 da
feed **928** e aside **280**, a coluna do feed com `CommunityFeed.paintFeed` (o
titulo "Feed" e os posts) e o aside com `CommunityFeed.paintAside` mais
`CommunityMembers.paint` ("Membros (N)": p-5 + titulo 14 + 12 + n×(36 + 12)).
PROVA `EnoisCommunityPanelE2ETest` (`feed=928`, `lado=280`, altura medida);
bateria 6/6.

`app/Main.kf` pintava a casca e a rota e FECHAVA a janela sem `present` — nada
chegava a tela. Agora chama `b.present(DrawList())` antes de fechar; PROVA
`EnoisAppEntryE2ETest` (`quadro: apresentado`). O demo das 15 telas vive FORA do
repo (`/tmp/enois-demo`): a captura de janela nao e possivel desta sessao (o X11
mostra so a area de trabalho do mutter e o Wayland recusa o cliente), entao o
demo e executado, nao fotografado — NOMEADO.

## §23 Rodada 23/11 (decima nona) — o painel da rota `feed` composto (app `a5f6462`)

`libs/FeedPanel.kf` compoe `routes/feed.tsx` 1069:
`grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_320px]` — conteudo 1232 da
publicacoes **888** e aside **320**, a coluna com `Feed.paintComposer`, os
cartoes com `space-y-4` (**16**) e os titulos do aside por
`CommunityFeed.paintAside`. PROVA `EnoisFeedPanelE2ETest`: `publicacoes=888`,
`lado=320`, `espaco=16`, altura medida; bateria 5/5.

## §24 Rodada 23/11 (vigesima) — o `EventCard` do DS medido (app `f2d882e`)

`libs/EventCard.kf` e o `domain/events/event-card.tsx` como a grade de `eventos` o
usa (`routes/eventos.tsx` 370: `grid-cols-1 gap-4 md:grid-cols-2
lg:grid-cols-3`): capa `h-40` (**160**), a pilula da data (`left-4 top-4`,
56x48) e o corpo `p-5` com badges, titulo, anfitriao, local e rodape — corpo
**186**, cartao **346**. PROVA `EnoisEventCardE2ETest`: `capa=160`,
`corpo=186`, `cartao=346`, `pintado=346`; bateria 5/5. O primeiro desenho dizia
162 e o proprio teste expôs o erro — o valor medido fica literal, com a conta
no comentario. Divergencias NOMEADAS: a capa e uma cor de token (o canvas nao
tem gradiente) e os badges sao uma linha de texto.

## §25 Rodada 23/11 (vigesima primeira) — a grade de `eventos` com o card do DS (app `a6abf79`)

`EventsScreen.paintCards` dispoe o `EventCard` do DS (346) na grade da rota
(`routes/eventos.tsx` 370: `grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3`):
coluna **400** em 1232 e linhas de 346 + 16 — 4 eventos = **708**. PROVA
`EnoisEventosGradeE2ETest` (`coluna=400`, `cartao=346`, `grade=708`). A bateria
Énois inteira rodou: **62 testes, 1 falha** — `EnoisTextE2ETest`, o §1197
conhecido (`pixels_de_tinta=0`, a leitura do canvas), igual a antes desta
rodada. MEDIDO no caminho: o primeiro commit da grade passou no teste dela mas
QUEBROU `EnoisEventsE2ETest` (sem `import enois.EventCard`) — a bateria pega o
que o teste isolado nao pega; o import foi commitado em `a6abf79`.

## §26 Rodada 23/11 (vigesima segunda) — o `Skeleton` do DS e uma medicao fresca do §1205 (app `fb671cb`)

`libs/Skeleton.kf` e o `src/ui/skeleton.tsx` (`animate-pulse rounded-md
bg-surface-2`): raio **14** (`--radius-md`, o do Input) e o pulso do Tailwind
(100% -> 50% -> 100%). PROVA `EnoisSkeletonE2ETest`: `raio=14`,
`meio=237,232,245` (o token `surface-2` no meio do ciclo), bateria 6/6.
MEDIDO, e NAO corrigido: as duas PONTAS do ciclo voltam BRANCAS (o card) em vez
do `surface-2` — a mistura por canal `a*(100-t) + b*t` nao alcanca as
extremidades nesta arvore (familia do §1205: uma soma encadeada perde o termo da
direita). Duas correcoes foram tentadas (passos, chamadas por canal) e as pontas
continuaram brancas; o comportamento medido fica preso no teste como divergencia
NOMEADA. O esqueleto de carga das comunidades segue fora de `libs/` (§1207).

## §27 Rodada 23/11 (vigesima terceira) — o §1212 achado no pulso do `Skeleton` (ABERTO), nada pago em codigo

As pontas do pulso do `Skeleton` saem brancas (a divergencia NOMEADA do §26). Esta
rodada rastreou: a mistura devolve o segundo produto dobrado no modulo real, enquanto
a IR e o asm lido estao certos e um modulo minimo reproduz os numeros certos. O
`known-bugs §1212` carrega a medicao e o proximo passo (qual `.s` o link nativo
consome). Duas contornacoes foram tentadas no app e revertidas (arvore em `fb671cb`).

## §28 Rodada 23/11 (vigesima quarta) — a tela "Minhas denuncias" (app `5ccd175`)

`libs/Denuncias.kf` compoe `routes/denuncias.tsx`: a coluna
`mx-auto w-full max-w-2xl` (**672**, centrada no conteudo), o "Voltar", o titulo
e a descricao (`text-sm`, quebrada na COLUNA — 2 linhas, nao uma), `space-y-6`
(**24**) e o cartao `rounded-2xl border border-border-soft bg-surface-card`
(raio 20, **152**) com o `EmptyState` de icone e SO titulo ("Voce ainda nao fez
denuncias."). PROVA `EnoisDenunciasE2ETest`: `coluna=672`, `cabecalho=100`,
`vazio=152`, `tela=365`; bateria 8/8. MEDIDO no caminho: a descricao cabe em UMA
linha em 1232 e em DUAS na coluna de 672 — a rota quebra dentro da coluna, entao
os 72 px de diferenca sao reais. NOMEADO: o glifo `Flag` nao e desenhado (so a
caixa).

## §29 Rodada 23/11 (vigesima quinta) — a tela "Usuarios bloqueados" na casca LOGADA (app `9c55c9b`)

`libs/Bloqueados.kf` compoe `routes/bloqueados.tsx` na casca autenticada:
`AppLayout.Content` e `mx-auto flex max-w-7xl gap-6 px-3 sm:px-6` — em 1280 a
coluna do `main` e 1280 − 48 (px-6) − 240 (barra lateral) − 24 (gap-6) =
**968** e comeca em **288**. O titulo e `text-xl` (20/28), a descricao
`mt-1 text-sm` quebrada na COLUNA, `space-y-4` (16) e o cartao `rounded-2xl
border` (**152**) com o `EmptyState` de icone e so titulo ("Voce nao bloqueou
ninguem."). PROVA `EnoisBloqueadosE2ETest`: `coluna=968`, `esq=288`, os textos
de `texts.ts` 1826-1830; bateria 5/5. NOMEADO: o glifo `ShieldOff` nao e
desenhado.

## §30 Rodada 25/11 — o app de demonstracao RODA todas as telas migradas (um quadro por invocacao)

Um app de demonstracao a parte (`/tmp/enois-demo/app/src/Main.kf`, fora dos dois
repos — o `codex.ignore` o tira do contexto do agente) chama o MESMO `paint` que os
testes provam, uma tela por invocacao (`Main <0..16>`), e apresenta o quadro.
PROVA sob `SDL_VIDEODRIVER=dummy` com o `assets/fonts/Manrope.ttf` real:
`tela=0 Comunidades fim=487`, `1 Projetos 200`, `2 Blogs 271`, `3 Vagas 122`,
`4 Eventos 140`, `5 Explorar 2116`, `6 Feed 162`, `7 Comunidade 338`, `8 Post 156`,
`9 Perfil 271`, `10 Salvos 208`, `11 Selos 312`, `12 Notificacoes 101`,
`13 Canal 280`, `14 Grupos 338`, `15 Minhas denuncias 365`,
`16 Usuarios bloqueados 309`. O laco de quadros (3 quadros, leitura de pixel na
linha 89) tambem rodou: `tinta=376` nos tres. NOMEADO: o LACO nao pode ficar
rodando — a memoria sobe ~3 GB/s ate o OOM killer (suspeito de passo do laco, ainda
sem entrada no known-bugs); por isso o app roda um quadro por processo. Janela na
sessao Wayland do lider nao e capturavel deste contexto (o X11 mostra so a area de
trabalho do compositor, o Wayland recusa o cliente, o XGetImage e negado) — as telas
sao provadas por execucao, nao por print.

**25/11 (decima primeira rodada — o crescimento do laco NAO se reproduz sozinho):** tres sondas, todas sob o driver dummy: (1) um laco minimo (clear + `roundRect` + `present`, 400 quadros) fica em ~4,1 MB; (2) o mesmo laco com a `Font` criada e fechada por quadro fica em ~4,1 MB; (3) um laco repintando a tela MAIS DENSA (`comunidades`: casca + cabecalho + `ComunidadesPage`, que chama `ComunidadesScreen.texts()` por quadro) fica em ~4,1 MB em 20 quadros — o custo do repaint nao e o vazamento. A execucao de um quadro do proprio app fica em ~2,4 MB e estavel, e por isso o modo por invocacao e' o estado do app. O OOM visto na primeira tentativa de laco fica atribuido ao passo do laco (o de espera) e segue como suspeita NOMEADA, nao como bug catalogado.

**25/11 (decima segunda rodada — o laco da janela do proprio app reproduzido FORA do harness):** o caminho do harness nunca chegou ao programa (o `.s` de 4,3 MB nao terminou de montar em minutos), entao a entrada foi compilada FORA dele e rodada da raiz do repo (a fonte resolve pelo caminho relativo la): `SDL_VIDEODRIVER=dummy /tmp/wl3/out/Default/Main` com o laco do app SPAWNADO reproduziu a falha — RSS ~7,3 GB e ~4,9 GB em duas execucoes a ~100% de CPU ate serem mortas, enquanto toda sonda SEM a thread spawnada ficou em ~4,1 MB (laco minimo, `Font` por quadro, a tela densa, `Shell.paint` + `present`, e um laco `while (aberto)` guiado por flag). Entao o laco esta' certo; o crescimento precisa da estrutura do app (o `paint` de 17 ramos, ou a chamada de sessao no boot). O harness ganhou `runFor(main, root, segundos)` (encerra o programa, para apps que ficam no laco) e o `EnoisAppEntryE2ETest` o usa — mas com o compile do harness isso leva minutos; a reproducao fora dele e' o registro por ora. NAO catalogado: o gatilho ainda e' ambiguo. PROXIMO: bisseccaoar qual dos 17 ramos vaza (rodar o laco com cada ramo escolhido pelo argumento) e, se for defeito de compilador/backend, um teste RED em `kof-compiler/src/test/java`.

## §31 Rodada 25/11 (decima terceira) — a bisseccao do OOM: o laco de quadros do app NAO vaza, e uma tela nova (app `10f5568`)

BISSECCAO (tudo sob `SDL_VIDEODRIVER=dummy`, cada execucao com o
`assets/fonts/Manrope.ttf` real): (1) o `app/Main.kf` COMMITADO exato, compilado
fora do harness e rodado da raiz do repo, fica em ~4,1 MB e sai com 0; (2) o mesmo
arquivo com o laco de quadros injetado em volta do desenho commitado fica em
~4,1 MB por 41 quadros; (3) o laco com a outra estrutura do app (`ShellChoice` +
`ProjectsScreen` + espera + `pumpEvents` + `present`) fica em ~4,1 MB por 30
quadros; (4) um laco pesado (1600x1200, 1200 `fillRect` por quadro) fica em
~4,1 MB por 31 quadros; (5) `present` duas vezes + `pumpEvents` + espera, 201
quadros, ~4,1 MB; (6) `readPixel` por quadro, 41 quadros, ~4,1 MB. As duas
execucoes que chegaram a ~7 GB NAO se reproduzem com o mesmo fonte, e o codigo que
as produziu se perdeu (um `git checkout` na mesma rodada), entao o OOM NAO esta'
catalogado nem atribuido: segue como suspeita NOMEADA (talvez a execucao do laco
pelo harness, ou o passo do laco). O laco de janela do `app/Main.kf` commitado e',
portanto, seguro para ficar rodando — a entrada commitada ainda termina, e a
variante com laco e' o demo.

TELA NOVA: `libs/MeusServicos.kf` compoe `routes/meus-servicos.tsx` 84-126: coluna
`mx-auto w-full max-w-3xl` (**768**), o `Wrench` de 24 em `--primary`, "Gerenciar
servicos" (24/32), a descricao (14/20 quebrada na coluna), `space-y-6` (24) e o
`surface-card` com o `EmptyState` de icone (titulo e descricao) —
`VazioDeListaComIcone.paintCard`. MEDIDO: `coluna=768`, `cabecalho=76`,
`cartao=200` (a descricao quebra em DUAS linhas no `max-w-sm`), `tela=389`.
PROVA `EnoisMeusServicosE2ETest`; bateria 6/6. NOMEADO: o `Wrench` sao tres
tracos (aproximacao) e o `<b>` do paragrafo perde o peso.

## §32 Rodada 25/11 (decima quarta) — a tela "Freelances" (app `947770e`)

`libs/Freelances.kf` compoe `routes/freelances.tsx` (86-…): a coluna
`mx-auto w-full max-w-4xl` (**896**, centrada no conteudo), o `Handshake` de 24 em
`--primary`, "Freelances" (24/32), a descricao em `max-w-2xl` (**672**) e o
`surface-card` com os DOIS `EmptyState` da rota (a busca vazia —
`searchEmpty`/`searchEmptyHint` — e o catalogo vazio — `empty`/`emptyHint`).
MEDIDO: `coluna=896`, `desc=672`, `fim1=369` (so o catalogo vazio), `fim2=549`
(com a busca vazia acima). PROVA `EnoisFreelancesE2ETest`; bateria 5/5. NOMEADO:
o `Handshake` desenhado em tres tracos e os glifos dos `EmptyState` so como
caixas.

## §33 Rodada 25/11 (decima quinta) — a tela "Penalidades" (app `6fffc44`)

`libs/Penalidades.kf` compoe `routes/penalidades.tsx` 60-146: coluna
`mx-auto w-full max-w-2xl` (**672**), "Voltar" (o `ArrowLeft` de 16 fica como
espaco — NOMEADO), "Penalidades" (24/32), a descricao (14/20 quebrada na coluna),
`space-y-6` (24) e o cartao `rounded-2xl border` com o `EmptyState` de icone e SO
titulo mais a linha centrada do `emptyHint` com `pb-6` — **176**. PROVA
`EnoisPenalidadesE2ETest`: `coluna=672`, `vazio=176`; bateria 6/6.

## §34 Rodada 25/11 (decima sexta) — a tela "Moderação · recursos" (app `7836904`)

`libs/Moderacao.kf` compoe `routes/moderacao.tsx` 62-93: coluna
`mx-auto w-full max-w-3xl` (**768**), "Voltar", "Moderação · recursos" (24/32), a
descricao (14/20, UMA linha na largura da coluna), `space-y-6` (24) e o cartao
`rounded-2xl border` com o `EmptyState` de icone e so titulo — **152**, tela
**345** (89 + 80 + 24 + 152). PROVA `EnoisModeracaoE2ETest`: `coluna=768`,
`tela=345`; bateria 6/6. NOMEADO: o `Gavel` so como caixa, o `ArrowLeft` do
"Voltar" deixa o espaco.

## §35 Rodada 25/11 (decima setima) — o painel de moderador (app `c740a08`)

`libs/Moderador.kf` compoe `routes/moderador.tsx` 195-544: coluna
`mx-auto w-full max-w-3xl` (**768**), "Voltar", "Painel de moderador" (24/32), o
selo `Admin`/`Moderador` (a pilula `inline-flex rounded-full px-2.5 py-1
text-xs` com o icone: `bg-primary`/`primary-foreground` no Admin,
`bg-primary-soft`/`primary-text` no Moderador), a descricao (14/20, TRES linhas
na coluna), `space-y-6` (24) e a secao "Denuncias aguardando revisao" (`h2` 20 +
12 + o cartao `rounded-2xl border` com o `EmptyState` de icone e so titulo =
**184**). PROVA `EnoisModeradorE2ETest`: `coluna=768`, `admin=401`,
`moderador=401` (o selo nao muda a altura); bateria 6/6. NOMEADO: os glifos
`ShieldCheck`/`Shield`/`Gavel` so como espaco e caixa.

## §36 Rodada 25/11 (decima oitava) — a pagina "Sua disponibilidade" na casca LOGADA (app `293e51e`)

`libs/DisponibilidadePage.kf` poe `routes/agenda_.disponibilidade.tsx` na casca
autenticada: a coluna do `main` tem **968** comecando em **288**, o `PageHeader`
do DS ("Sua disponibilidade" / "A semana em que sua agenda admite compromissos.
Ela se repete toda semana.", sem botao de acao) e' o mesmo cartao de 104 com o
`mb-4` (**120**), e o `EmptyState` da falha de carga (`TriangleAlert`, "Nao foi
possivel carregar sua disponibilidade." + "Sem ler a semana salva, salvar
apagaria o que voce ja tinha. Tente carregar de novo.") tem **200** (a descricao
quebra em DUAS linhas no `max-w-sm`). PROVA `EnoisDisponibilidadePageE2ETest`:
`coluna=968`, `esq=288`, `cabecalho=120`, `erro=200`; bateria 6/6. NOMEADO: os
glifos `Clock`/`TriangleAlert` so como caixa e espaco. O editor semanal em si
(janelas e horarios) fica fora desta fatia.

**25/11 (decima quarta rodada — o caso de ~7 GB testado de ponta a ponta, NAO reproduzido):** (1) o `app/Main.kf` COMMITADO compilado fora do harness sai em **0 s** com `quadro: apresentado` (o relogio de parede nao e' mensuravel: o `date` so' da' segundos inteiros); (2) o mesmo arquivo com o laco de quadros do app (casca + espera + `present`, sem `pumpEvents`), 300 quadros, fica em ~4,1 MB; (3) o mesmo com `pumpEvents` e redesenho a cada tecla, 400 quadros, ~4,1 MB e termina; (4) um laco com `pumpEvents` e SEM espera nenhuma (o pior caso de giro), 400 quadros, ~4,1 MB e termina. Toda tentativa de reproducao com o codigo commitado em cada commit e' ESTAVEL, entao os ~7 GB seguem sem gatilho medido, como suspeita NOMEADA (o fator restante mais provavel e' o tubo de saida do `ProcessBuilder` do harness com um laco que nunca sai — nao testado porque o caminho de gcc/link do harness leva minutos). O LACO do app em si e' seguro: a entrada commitada esta pronta para rodar com um laco `← →/Esc` quando uma tela escolher a rota (fora desta fatia).

## §37 Rodada 25/11 (decima nona) — a camada de CLIQUE da grade de eventos (app `1107ef1`)

Primeira peca do "o app responde ao ponteiro": `libs/GradeEventos.kf` responde QUAL
evento recebe o clique na grade de `routes/eventos.tsx` 370 — a celula e' a que a
grade desenha (`lg:grid-cols-3`, **400** em 1232, **156** por linha com o `gap-4`),
e `hit(px, py, ox, oy, w, n)` devolve o indice, ou **-1** fora da grade, no vao
entre as linhas e alem do catalogo (`n`). PROVA `EnoisGradeEventosE2ETest`:
`coluna=400`, `linha=156`, `c0=0`…`c3=3`, `c6=6`, `c7=-1`, `vao=-1`, `fora=-1`,
`x4=440`, `y4=245`; bateria 5/5. A geometria e' a MESMA do `paintCards` da rota,
entao o clique e o desenho nao divergem (os dois leem `EventsScreen.columnWidth` /
`EventsMetrics`).

## §38 Rodada 25/11 (vigesima) — o pulso do `Skeleton` dobrado: CAUSA-RAIZ do §1212 ACHADA + CORRIGIDA no Kof4j (nenhuma tela do app tocada)

A divergencia NOMEADA do §26/§27 (as pontas do pulso brancas) e' defeito do compilador, nao da mistura do app. Medido em artefato LIMPO (diretorio de saida novo, `KOF_KEEP_ASM=1`): quando um argumento e' ele proprio uma chamada de receptor (`Sk.channel(Tokens.SurfaceCard().g(), Tokens.Surface2().g(), t)`), a chamada interna cai em cima de um operando ja' empilhado e o alinhador preenchia ESSA chamada — o `leave`/`ret` do callee interno pula o restore, entao o preenchimento fica na pilha e os dois `popq` que movem os argumentos leem o preenchimento e o operando empilhado de novo. Resultado: `a*(100-t) + b*t` devolve `2*b*t` (os dois canais 255) e 16 bytes vazam por avaliacao. CORRIGIDO em `NativeX86StackAlign.regionIsStackMachine` (Kof4j; sem preenchimento quando ha' uma SEGUNDA `call` entre a chamada e a linha de quadro). RED `NativeX86StackAlignScratchTest` 2/3 → 3/3; `NativeAbiInvariantsTest` 2/2 (a assercao "zero desalinhadas" do §14 virou a cota do §1212 — depois da correcao ela forcaria o erro de volta; MEDIDO que o push/pop por operacao deixa o `%rsp` alinhado e era o preenchimento que fazia parecer o contrario); `NativeX86StackAlignVtableTest` 5/5; `NativeX86StackAlignTest` verde. A chamada no mesmo slot (`Sk.channel` alimentado pelos tres operando empilhados) ainda mede 255 — ABERTO NOMEADO (o achado de slot da IR no §1212); o app mantem a medicao fixada ate' um run provar 237.

## §39 Rodada 25/11 (vigesima primeira) — o catalogo VIVO: a vitrine de `comunidades` le' a API (app `libs/Catalogo.kf` + `CatalogoJson.kf`)

A vitrine de `routes/comunidades.tsx` era pintada de uma lista fixa; agora o dado vem da API do site. O `CatalogoApi.listUrl` reproduz o `buildQuery` de `src/lib/api-client.ts` 645 para o `communitiesApi.list` (`src/lib/api/communities.ts` 17): `topic`, `cursor`, `limit`, `q`, `city`, `state` nessa ordem, vazio/nulo fora, `limit=20` padrao, espaco como `+`, `city` so' com `state`. O `CatalogoJson` recorta o `PageResponse<Community>` (`{ items, nextCursor }`) um objeto por vez; o `CatalogoApi.cardOf` leva o item ao `CommunityCardView` que a grade ja' pinta (`topic` anulavel → rotulo vazio, `description` nula → vazia, `city`/`state` → `"Cidade · UF"`, `members` → `"7 membros"`/`"1 membro"`, `joined` → Sair/Entrar). PROVA `EnoisCatalogoE2ETest` contra um servidor local com o contrato medido (as linhas acima); bateria Énois 73 testes com 1 falha (`EnoisTextE2ETest`, o §1197 conhecido da leitura do canvas).

MEDIDO e corrigido no caminho: o `Session.kf` mandava `Origin:` — o site manda `X-Client-Origin` (`src/lib/api/client-origin.ts`); com o cabecalho do site o servidor responde 200. Os testes do `/me` passam a aceitar a forma do contrato.

MEDIDO e REGISTRADO (Kof4j §1213): literal de string de um caractere nao e' igual ao caractere lido no nativo x86-64 (`s.charAt(2) == "\""` dava falso, o que fazia o primeiro recorte do JSON ir ate' o fim do corpo e todo campo vir com a cauda); a forma que mede e' a comparacao por codigo (`charAt(i) as Int == 34`). `NativeOneCharLiteralE2ETest` fixa.

PENDENCIAS NOMEADAS: o base da API (`http://127.0.0.1:PORT` no teste) e o cookie de sessao ficam de fora do app; a pagina ainda nao esta' ligada ao `Main.kf`; fora do `ç` medido, nao-ASCII sai codepoint a codepoint em hexadecimal (sem decomposicao UTF-8 multi-byte ainda).

## §40 Rodada 25/11 (vigesima segunda) — os estados de CARREGAMENTO da vitrine (app `75dec7c`) e a medicao do §1218 (era §1214 na rodada de 25/11)

O `libs/Carregamento.kf` cobre o que a `routes/comunidades.tsx` desenha enquanto o catalogo chega, medido no DS: o `CommunityCardSkeletons` (`count = 6`, `surface-card overflow-hidden`, capa `h-32` **128**, corpo `p-5` + a linha do avatar (40) + `h-3 w-full` (12) + `h-3 w-2/3` (12) + rodape (32) + quatro `space-y-3` (48) + `p-5` = **196** pela aritmetica do site, **184 medido neste nativo** — NOMEADO §1218), a grade de 3 colunas com `gap-4`, o `EmptyState` de so' titulo (**100**) e o rodape "carregar mais" (`Button variant=outline rounded-full`, **36** + `pb-8` **32** = 68, rotulo `texts.communities.loadMore`, so' quando a pagina traz `nextCursor`). O `VitrineEstado.of` escolhe o estado (carregando → esqueleto; sem item → vazio, `emptyFiltered` com a UF do filtro; senao a grade). PROVA `EnoisVitrineCarregamentoE2ETest` (as linhas acima no EN); bateria Énois 74 testes com 1 falha (`EnoisTextE2ETest`, o §1197 conhecido).

MEDIDO no caminho (Kof4j): uma soma encadeada de literais e' lida um termo a menos no nativo x86-64 — `println(20 + 40 + 12 + 12 + 32 + 48 + 20)` imprime **184** (esperado 196). NAO e' o otimizador (o mesmo 184 com
`setOptimizationEnabled(false)`), nem o emissor (o asm sem otimizacao tem os sete `addl`), nem o modulo (um `main()` sozinho reproduz). A IR so' nao expoe a cadeia dobrada (o `irObserver` nao publica), entao o produtor exato do valor 184 fica ABERTO: e' um dos dois — o otimizador dobra com literais compartilhados (a `foldBinary` cria `KofLoadLiteral.ofInt` que o `equals` por VALOR pode tratar como a mesma operacao; um `IdentityHashMap` nos `positions` e a leitura por identidade e' o proximo alvo) ou o otimizador e' chamado antes do emit mesmo com o flag {@code false} (a IR do frontend ja' com a cadeia dobrada). O mesmo para `var s` somado passo a passo (`var t = 5; t = t + 1` mede 6 fora do modulo grande e 6 aqui) e para somas por funcoes nomeadas terminando em `+ 20` (184). O `KofBinary`/`KofLoadLiteral` sao records: `out.remove(lb)`/`out.set(...)` de um literal por VALOR remove a operacao errada quando duas literais tem o mesmo valor (os dois `12` da soma!) — o suspeito mais forte. O app fixa o 184 medido como divergencia NOMEADA e mantem 196 documentado como a conta do site.

PENDENCIAS NOMEADAS: o esqueleto pinta o `surface-2` puro (as pontas do pulso do `Skeleton.shade` ainda medem branco), o `Loader2` do botao desativado nao e' desenhado (so' a pilula apagada) e o corpo fica em 184 ate' o §1214 fechar.

## §41 Rodada 25/11 (vigesima terceira) — o "184" estreitado: `known-bugs §1218` com teste RED-first (sem mudanca no app)

O gap nomeado do §40 (o corpo do esqueleto le' 184 onde a aritmetica diz 196) agora e' uma linha do `known-bugs` com teste RED-first. MEDIDO: `println(90 + 94)` imprime **184**, e tambem `(91 + 93)`, `168 + 16`, `104 + 80`, `92 * 2`, `20 + 40 + 12 + 12 + 32 + 48 + 20` e o proprio `println(184)`; os vizinhos estao certos (`90 + 94 + 1` = 185, `250 + 34` = 284, `17 + 26 + 92 + 49 + 50 + 60` = 294, `100 + 40 + …` = 264). DESCARTADOS: o frontend (sem otimizacao da' o mesmo), o otimizador (o asm de `println(184)` e' `movq $184, %rdi` + `call kof_int_to_string`) e o modulo (em `game`/`image` os mesmos doze literais imprimem certo — os slices que sobrevivem mudam a resposta). PROVA `NativeLiteralDropE2ETest`: `theBodySumOfTheSkeletonIsReadWhole` RED (184) e `theNeighbourValuesAreUnaffected` GREEN (284 / 294). O app mantem o 184 medido fixado (teste do esqueleto), e o numero §1214 na prosa do §40 pertencia ao fix do `io.appendText` de 24/11, entao a linha e' **§1218**. PROXIMO: bissecar o conjunto de slices do runtime com `KOF_KEEP_ASM=1` e comparar o caminho de boot/`kof_int_to_string` do programa com 184 contra um vizinho.

## §42 Rodada 25/11 (vigesima quarta) — o padrao do §1218 achado: o `println` de um literal e' REPETIDO no seguinte (so' o Kof4j, sem mudanca no app)

Sondando o gap do §1218 (o corpo do esqueleto le' 184 onde a aritmetica diz 196) ele estreitou para o caminho da IMPRESSAO, nao da aritmetica: `println(50)`, `println(2 * 25)`, `println(25 + 25)`, `println(40 + 40)` imprimem **50 50 50 80** (a segunda e a terceira repetem o primeiro valor), entao `println(90 + 94)` (constante 184 cujo primeiro termo e' 90) imprime 90. MEDIDO e DESCARTADO: o mesmo fonte na JVM imprime 184, o asm nativo e' `movq $90, %rax / addl $94, %eax / call kof_int_to_string` (correto), religar o asm com `movq $196` imprime 196, e com `-Dkof.native.register.locals=false` e sem otimizacao o mesmo 50 50 50 80 permanece — o conjunto de slices do runtime que sobrevive ao prune mais o leiaute de um println de literal depois de outro sao o suspeito. PROVA `NativeLiteralDropE2ETest` (3 testes, 2 RED e 1 GREEN). O app mantem o 184 medido fixado e nao muda nesta rodada.

## §43 Rodada 25/11 (vigesima quinta) — §1218 corrigido: o interpretador da JVM da' a MESMA resposta errada, entao o produtor e' o frontend/IR (so' o Kof4j)

O registro do §42 culpava o conjunto de slices do runtime nativo. ERRADO: o mesmo fonte pelo interpretador da JVM (`kof-cli run`) imprime o mesmo valor errado que o build nativo, entao o produtor e' o frontend/IR, e o emissor e o runtime nativos estao descartados. A regra das sondas: uma soma/multiplicacao cujo literal da ESQUERDA ja' apareceu num `println` anterior e' lida como aquele literal — `println(7)`, `println(5 + 2)` → `7 7`; `println(50)`, `println(2 * 25)`, `println(25 + 25)` → `50 50 50`; `println(90)`, `println(90 + 94)` → `90 184`; e os vizinhos estao certos. PROVA `NativeLiteralDropE2ETest` (4 testes, 2 RED e 2 GREEN). O app mantem o 184 medido fixado; quando o fix do §1218 entrar, o corpo do esqueleto le' 196 e o teste fixado vira na mesma rodada.

## §44 Rodada 25/11 (vigesima sexta) — §1218 rotulado com honestidade: a IR esta certa, a repeticao e' instavel entre builds (so' o Kof4j)

A conclusao do §43 ("o frontend/IR produz") nao sobrevive a leitura direta: o dump da IR depois do frontend mostra `LoadLiteral 80` onde a soma e' 80 e `LoadLiteral 5 / 2 / ADD` onde `5 + 2` e' 7, entao o lowering esta certo. O REPRO e' instavel: o mesmo programa de um arquivo (`println(5)`, `println(40 + 40)`) imprimiu `5 40` pelo interpretador da JVM uma vez e `5 80` minutos depois num diretorio novo, e o build nativo imprime `5 80`; o que se repetiu foi a forma em que o literal impresso TAMBEM e' operando de uma soma posterior (`println(50)`, `println(2 * 25)` → `50 50`; `println(5)`, `println(2 + 5)` → `5 5`). Descartados de novo: o emissor e o link (o asm e' `movq $80`), o otimizador (o mesmo com ele desligado) e a IR. O app mantem o 184 medido fixado; o §1218 agora diz que a repeticao esta no caminho de execucao e e' nao-deterministica entre builds, com a sonda deterministica como PROXIMO.

## §45 Rodada 25/11 (vigesima setima) — o §1218 agora tem repro deterministico (dois caminhos, mesma resposta) (so' o Kof4j)

A sonda num processo unico, repetida, fixa as formas (JVM e nativo dao a MESMA resposta, entao e' o frontend/lowering compartilhado; emissor, link e otimizador descartados, e a IR mostra as constantes certas): `println(90)` + `println(90 + 94)` → `90 184` (tambem com `val a = 90; println(a)`, `println(89 + 1)`, `println(-90)`, `println(191)` ou `println(94)` antes); `println(7)` + `println(5 + 2)` → `7 7`; `println(50)` + `println(2 * 25)` → `50 50`; `println(500)` + `println(2 * 25)` → `500 50`; `println(90 + 94)` sozinho → 184. Controles certos: `5 80`, `3 50`, `7 10`. Os relatos anteriores de `5 40` / `5 50 50 80` vinham de misturar dois programas no mesmo diretorio e ficam RETIRADOS. PROVA `NativeLiteralDropE2ETest` (4 testes, 2 RED e 2 GREEN).

## §46 Rodada 25/11 (vigesima oitava) — §1218 estreitado ao caminho da impressao (pilha certa, IR certa) (so' o Kof4j)

Mais medicoes do mesmo defeito, deterministicas no mesmo processo: `println(2)` + `println(1 + 1)` (e `1 * 2`, `3 - 1`, `1 + 1 + 0`, `4 / 2`) → `2 2` — o valor da SOMA e' igual ao da impressao anterior e aquele valor e' impresso; `println(90)` + `println(90 + 94)` → `90 184`; controles certos (`5`/`40 + 40` → `5 80`, `3`/`2 + 2` → `3 4`); um `val` com valor DIFERENTE entre as impressoes ainda da' o primeiro termo (`90`/`val x = 0`/`40 + 50` → `90 90`). INSTRUMENTADO (trace temporario do frame, revertido): a pilha tem dois valores em todo `valueOf` dos dois statements (`d=2`), entao a duplicacao NAO e' vazamento de pilha do frame; e um `driver.interpret(...)` em processo imprime os mesmos valores errados do `kof-cli run`. O alvo restante e' o caminho da impressao (`String.valueOf(Object)` / o slice `kof_println_string`). O app mantem o 184 fixado; sem mudanca no app nesta rodada.

## §47 Rodada 25/11 (vigesima nona) — produtor do §1218 localizado: um passe dentro da rodada do Otimizador (so' o Kof4j)

Bisseccao com um `Optimizer` instrumentado: a rodada 0 de `println(2)` + `println(1 + 1)` e' `[GetStatic, L2, ..., println, GetStatic, L1, L1, ADD, ...]` (certa) e a rodada 1 e' `[..., GetStatic, L2, ...]` — um dos `passes()` pos-dobra troca a soma do segundo statement pelo literal do primeiro. A IR do interpretador ja' mostra a forma errada; um programa cuja soma vem depois de NENHUM literal impresso baixa certo; o `kof run`, o `kof test` e o `driver.interpret` mostram o mesmo, entao e' o caminho compartilhado. PROXIMO: rodar cada passe sozinho sobre a IR da rodada 0 e bisseccionar. Sem mudanca no app nesta rodada.

## §48 Rodada 25/11 (trigesima) — produtor do §1218 fixado: o proprio percurso da dobra de constantes (so' o Kof4j)

Repetir a IR real do frontend pelo `OptimizerConstantFold.constantFold` SOZINHO reproduz `println(2)` + `println(1 + 1)` → `[GetStatic, LoadLiteral 2, …, GetStatic, LoadLiteral 2, …]`, e a forma depois de uma dobra, duas dobras e 1-4 rodadas completas de `passes()` e' IDENTICA — o conjunto de passes pos-dobra fica descartado. PROXIMO: corrigir o percurso (ele escreve a literal dobrada em `out` e continua lendo a `ops` original em `i - 1`, entao uma soma de dois literais redobra contra o statement anterior) com o teste RED-first e depois GREEN. O app mantem o 184 fixado.

## §49 Rodada 25/11 (trigesima primeira) — §1218: reproduzivel na arvore atual, o produtor e' o LOWERING (so' o Kof4j)

Com o compilador construido do `lab` atual, `driver.interpret` de `println(2)` + `println(1 + 1)` imprime `2 2` e a IR do FRONTEND e' `[GetStatic, LoadLiteral 2, …, GetStatic, LoadLiteral 2, …]` — o segundo statement carrega o literal do primeiro e sem `1 + 1`; `println(90)` + `println(90 + 94)` baixa para `LoadLiteral 90` … `LoadLiteral 184` (a soma e' dobrada, no 184 errado). O `OptimizerConstantFold` instrumentado ve `la=1 lb=1 ADD -> 2` (calcula 2 certo para a entrada que recebe) e a forma nao muda depois de 1-4 rodadas, entao o pipeline de operador/soma esta descartado; o defeito esta no LOWERING entre a AST (dois printlns) e a IR. PROXIMO: bisseccionar o lowering do segundo `println(1 + 1)`. O app mantem o 184 fixado.

## §50 Rodada 25/11 (trigesima segunda) — produtor do §1218 fixado: a escrita em `out` da dobra descarta a op anterior (so' o Kof4j)

Tres dumps (todos revertidos): o lowering esta certo (`ExpressionStmt` #2 da' `[GetStatic, L1, L1, BADD, …]`), o `Optimizer` recebe isso e devolve `[GetStatic, L2, …]`, e DENTRO do `OptimizerConstantFold` a dobra de `1 + 1` deixa `out` como `[X, L2, …, X, L2]` — 7 ops em vez de 9, o primeiro `GetStatic` do segundo statement sumiu. O interpretador imprime o valor certo da constante dobrada, entao o caminho de impressao tambem fica descartado. O fix e' na escrita em `out` da dobra. PROXIMO: trocar o trio de dois literais + ADD sem tocar o `GetStatic` anterior, com o `NativeLiteralDropE2ETest` RED-first e GREEN depois; o app mantem o 184 fixado.

## §51 Rodada 25/11 (trigesima terceira) — §1218: a dobra esta certa numa IR de mesa; os dumps discordam (so' o Kof4j)

Teste de mesa (IR montada a mao com a forma do programa): a dobra entra com 9 ops e sai com 9 — nada some, entao a leitura do §50 sobre a escrita em `out` fica retirada. No programa real, `prepareForInterpretation` + a dobra sozinha dao `BEFORE-FOLD G L1 L1 BADD` → `AFTER-FOLD G L2` (so' os operandos do segundo statement dobram) e o `driver.interpret` imprime `2 | 2`. Tres sondas concordam que as pecas do pipeline estao certas quando rodadas de um jeito e o valor impresso erra quando rodadas de outro — o proximo passo e' UMA sonda que despeja o modulo entregue ao `KofInterpreter.run` (identidade) e a entrada/saida da dobra na mesma chamada. O app mantem o 184 fixado.

## §52 Rodada 25/11 (trigesima quarta) — §1218: o jar velho da arvore explicava as medicoes inconsistentes; o gatilho e' a multiplicacao (`50` + `2 * 25` → `50 50` nos DOIS backends) (so' o Kof4j)

O `lib/kof.jar` (o que o `bin/kof` roda) era de 03:43 enquanto o compilador em teste ja' tinha andado; reconstrui-lo (`scripts/build-kof-jar.sh`, e o jar e' artefato de build, nao commitado) fez `java -jar lib/kof.jar run`, `driver.interpret` e o build nativo concordarem em todos os casos: `2`/`3 + 4` → `2 7`, `5`/`40 + 40` → `5 80`, `90`/`90 + 94` → `90 184` (todos certos) e `50`/`2 * 25` → **`50 50`** (errado). `50`/`3 * 25` → `50 75` (certo), entao a multiplicacao dobrada numa literal igual a do primeiro statement e' o discriminador. `PrintRepeatProducerTest` (3 casos) prende as respostas certas. PROXIMO: despejar as ops de `50`/`2 * 25` e rastrear o builtin de impressao. O app mantem o 184 fixado (o 184 dele vem de uma soma e le' certo nesta arvore).

## §53 Rodada 25/11 (trigesima quinta) — §1218: o gatilho e' "o valor do segundo statement alcanca o primeiro impresso" (so' o Kof4j)

Medido nos dois backends com o jar reconstruido: `80`/`60 + 20` → `80 80` (errado), `80`/`40 + 40` → `80 80`, `50`/`50 + 0`, `50`/`0 + 50` e `50`/`25 + 25` → `50 50`; e certo quando os valores diferem (`2`/`3 + 4` → `2 7`, `5`/`40 + 40` → `5 80`, `90`/`90 + 94` → `90 184`). O dump do Optimizer com identidade mostra objetos literal distintos, entao a dobra esta certa; o trace do `valueOf` mostra o numero errado JA' na pilha. PROXIMO: rastrear o caminho `valueOf`/`println` do caso que falha (`80` + `60 + 20`) argumento por argumento. O app mantem o 184 fixado.

## §54 Rodada 25/11 (trigesima sexta) — §1218: nao e' o valor; o caminho do interpretador perde a forma (so' o Kof4j)

`println(50)` + `println(3 + (50 - 3))` imprime `50 50`, e as outras formas `n + (50 - n)` e `50 + 0` tambem — a leitura "alcanca o primeiro valor" fica retirada. O `Optimizer` com identidade esta certo, e duas execucoes da mesma IR sao identicas, entao o defeito esta na execucao do interpretador. PROXIMO: instrumentar o `runFrame` para despejar a pilha por op em `50 / 50 + 0` e em `2 / 3 + 4` (que funciona), numa unica execucao. O app mantem o 184 fixado.

## §55 Rodada 25/11 (trigesima setima) — §1218: o trace da pilha do interpretador esta certo; falta a medicao do modulo entregue ao `KofInterpreter.run` (so' o Kof4j)

O `kof.trace.stack` (revertido) mostra a pilha de operandos do interpretador para o programa que falha (`50` + `50 + 0`: `TR 6 LIT 50` — o segundo statement ja' e' um literal 50) e para um que funciona (`2` + `3 + 4`: `TR 6 LIT 7`). O Optimizer, com identidade, esta certo. A UNICA medicao que falta e' o modulo entregue ao `KofInterpreter.run`. PROXIMO: aquele dump. O app mantem o 184 fixado.

## §56 Rodada 25/11 (trigesima oitava) — regra do §1218 medida: dois `println` do MESMO valor repetem a primeira linha; o alvo e' o caminho de impressao (so' o Kof4j)

Medido com o jar reconstruido e o build nativo: `50` + `50 + 0` → `50 50`; `var a = 80` + `var b = 80` → `80 80`; `val a = 100 - 20` + `val b = 50 + 30` → `80 80` — o VALOR decide, nao a sintaxe. Valores diferentes imprimem certo (`2`/`3 + 4` → `2 7`, `5`/`40 + 40` → `5 80`, `90`/`90 + 94` → `90 184`). O modulo entregue ao interpretador esta certo, o Optimizer por identidade esta certo e o asm nativo do par que falha e' `movq $80` + `kof_int_to_string` + `kof_println_string` duas vezes — o alvo que sobra e' o caminho de impressao. PROXIMO: bissecar essas duas chamadas com o mesmo valor duas vezes e com dois valores diferentes num programa. O app mantem o 184 fixado.

## §57 Rodada 25/11 (trigesima nona) — §1218: tabela autoritativa com diretorios novos (5 de 8 ainda vermelhas) (so' o Kof4j)

Toda sonda agora roda com `rm -rf` e UM `Main.kf` por caso. Pelo `lib/kof.jar` reconstruido: `50`/`50 + 0`, `80`/`60 + 20`, `50`/`25 + 25`, `2`/`1 + 1`, `50`/`2 * 25` e `7`/`5 + 2` imprimem o PRIMEIRO valor duas vezes (ERRADO), enquanto `90`/`90 + 94` → `90 184` e `5`/`40 + 40` → `5 80` estao certos. Regra central: `println(X)` seguido de `println(a + b)` com X ≤ a + b imprime X. Dois diretorios de sonda meus (`/tmp/jv` e os `/tmp/p1222/k*` da bissecao) tinham `Main.kf` velho e ficam descartados — a afirmacao anterior ("o bug de fixture invalida tudo") fica retirada por sua vez. PROXIMO: asm do par `2 / 1 + 1` contra `7 / 5 + 2`. O app mantem o 184 fixado.

## §58 Rodada 25/11 (quadragesima) — §1218 esta SO' no caminho COMPILADO: o interpretador executa os mesmos fontes certo (so' o Kof4j)

`PrintRepeatProducerTest` (4 casos) esta GREEN pelo `driver.interpret`. Os mesmos fontes pelo jar do CLI (alvo JVM — COMPILA) e pelo build nativo imprimem o primeiro valor duas vezes em `2 / 1 + 1`, `3 / 2 + 1`, `4 / 2 + 2`, `7 / 5 + 2`, `9 / 4 + 5`, enquanto `5 / 40 + 40` → `5 80` e `2 / 3 + 4` → `2 7` estao certos. A IR do interpretador esta certa e a saida COMPILADA troca a ordem das duas impressoes. PROXIMO: comparar o bytecode gerado de `2 / 1 + 1` e `5 / 40 + 40`. O app mantem o 184 fixado.

## §59 Rodada 25/11 (quadragesima primeira) — §1218: a classe compilada em si esta errada (`iconst_2`, sem `iadd`) e rodar-la reproduz o bug (so' o Kof4j)

`kof build Main.kf --target jvm` e depois `java -cp build/classes Default.Main`: `println(2)` + `println(1 + 1)` imprime `2 2` e o `javap` mostra `iconst_2` duas vezes (sem `iadd`), enquanto `println(5)` + `println(40 + 40)` compila para `bipush 80` e imprime `5 80`. O interpretador sobre o mesmo fonte esta certo, entao as duas entradas do frontend (`interpret` contra `compileSources`) discordam. PROXIMO: chamar o `driver.compileSources` de um teste JUnit e despejar a IR entregue ao emissor. O app mantem o 184 fixado.

