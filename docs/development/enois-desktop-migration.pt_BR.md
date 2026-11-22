[English](enois-desktop-migration.md) | [Português](enois-desktop-migration.pt_BR.md)

# Énois → cliente desktop nativo em Kof (migração 1:1)

last: ABERTO 22/11 por ordem da mantenedora ("seu trabalho é fazer funcionar aqui uma migração de 1:1 do enois, reprodução fiel pra um cliente desktop funcional", alvo `enois.ia.br`). STATUS: EM DESENVOLVIMENTO
doing: Fase 0 POUSADA — os TOKENS do design GERADOS do CSS real (`libs/enois/Tokens.kf`, 61 cores + raios) rodam no nativo e batem com o site
next: Fase 1 — o shell do app (janela 1280×800, a superfície `--background`, a barra lateral com a marca) + a rota Feed
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

**Fase 1 — o shell do app.** Uma janela 1280×800 pintada com `--background`, a marca,
a barra lateral e a barra de topo — o chrome que toda rota compartilha.

**Fase 2 — o Feed.** `feed.tsx` → uma lista rolável de cards de post.

**Fase 3 — as rotas, em ordem de dependência**: `perfil.*` → `post.$postId` →
`comunidades.*` → `mensagens` → o resto.

**Fase 4 — a ponte com o backend.** `kof.http` contra a mesma API que o site chama
(medida em `src/`), para o cliente desktop mostrar conteúdo real.

**Fase 5 — empacotamento.** `scripts/package-desktop.sh` (`.deb` + AppImage) e o
workflow de CI que prova o `dpkg -i` num ubuntu-latest.

## §4 Como concluir

cada fase pousa com uma execução nativa que imprime o que pintou; o plano registra
o que está migrado, o que é gap nomeado, e o próximo passo. Promoção para `docs/`
só quando a lista de rotas estiver coberta ou os gaps restantes estiverem nomeados.

## §5 Gaps nomeados (medidos, não supostos)

* `math.sin`/`cos` — `MATH001` em todo alvo nativo (§621 no `KofMath`); os
  tokens evitam isso convertendo OKLCH em tempo de GERAÇÃO.
* pdf.js, qrcode, Cloudflare Turnstile — sem superfície Kof; cada rota que
  precisar de um ganha uma entrada de gap nomeada aqui.
