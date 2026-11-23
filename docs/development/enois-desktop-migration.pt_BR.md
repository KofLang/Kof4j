[English](enois-desktop-migration.md) | [Português](enois-desktop-migration.pt_BR.md)

# Énois → cliente desktop nativo em Kof (migração 1:1)

last: ABERTO 22/11 por ordem da mantenedora ("seu trabalho é fazer funcionar aqui uma migração de 1:1 do enois, reprodução fiel pra um cliente desktop funcional", alvo `enois.ia.br`). STATUS: EM DESENVOLVIMENTO
doing: Fase 0+1 POUSADAS — os TOKENS (`libs/enois/Tokens.kf`, 61 cores + raios, gerados do CSS) E o SHELL do app (`libs/enois/Shell.kf`: header de 44 px, aside de 320 px, conteudo `--background`, o grid medido `h-11` / `gap-6` / `[1fr_320px]`) rodam no nativo e batem com o site
next: em `enois-kof-desktop` — o BLOQUEIO DA SESSAO acima depende de uma decisao de contrato da mantenedora (cookie/bearer para um cliente nativo); o quadro de recrutamento ENTROU — enquanto isso, os catalogos restantes; toda tela NOVA comeca medindo a rota ao vivo com `scripts/measure-site.mjs`; o Kof4j mantem o `libs/game` + o compilador e registra os bugs do Kof (§1196, §1197); o §1196 e o §1197 seguem ABERTOS
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
