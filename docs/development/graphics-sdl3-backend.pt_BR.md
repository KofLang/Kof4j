[English](graphics-sdl3-backend.md) | [Português](graphics-sdl3-backend.pt_BR.md)

# BACKEND de graphics SDL3 (a janela nativa + a face de software do WASI)

last: ABERTO 22/11 por ordem da mantenedora ("1 autorizo, 2 pode focar só no x86-64 por hora, nativo + wasi é o caminho; alem disso depois quem quiser que porte pra arm/risc") — este é o plano novo de backend que o `docs/graphics-gaming.md` exige. STATUS: EM DESENVOLVIMENTO
doing: fatia 1b POUSADA 22/11 — o `present(DrawList)` resolve cada `DrawCmd.image` pelo cache de textura do backend (`registerTexture`/`textureFor`), desenhando a TEXTURA onde uma esta registrada e o retangulo solido caso contrario; o upload da imagem anda pelo `SDL_UpdateTexture` exportado (um `Buffer(U8)` so' e' legal em assinatura `extern`) e o retangulo de destino e' montado por `floatBits(Int)` (um `SDL_FRect` sao quatro floats)
next: um app de exemplo do `kof.game` usando as quatro faces medidas (janela + sprite + teclado + mouse) para a pessoa ter um ponto de partida; depois o porte arm/risc (mantenedora: quem quiser), depois a promoção para `docs/`
location: repository
state: active

constraint:

* Kof-first: o backend é um arquivo Kof sobre `extern`; nenhum primitivo novo no core
* SEM KofJS neste plano (mantenedora, 22/11) — o alvo JS está fora de escopo
* cross-target honesto: toda face ou RODA ou recusa com o seu código (`FFI001` no WASI para `extern`; a face de software a substitui lá)
* a superfície pura (`Window`, `Keys`, `Mouse`, `Draw`, `Sprite`, `Clock`) é CONGELADA — o backend se encaixa, não a redefine
* `D-GRAPHICS-GAMING` continua dono da decisão do stack (SDL3, `G1`)

decision:

* ordem dos alvos: NATIVO x86-64 primeiro; o WASI ganha a face de SOFTWARE; aarch64/riscv64 são um PORTE, não este plano
* o backend é a superfície `kof.game` sobre o SDL3; o app nunca escreve `extern`
* o WASI NÃO tem janela (preview1): a face dele renderiza num PPM que o programa nomeia, e esse é o contrato honesto

---

## §1 O que está MEDIDO hoje (22/11, este host)

| fato | medido por | resultado |
|---|---|---|
| o `extern` liga o SDL3 no nativo x86-64 | um programa real | `janela=true`, `titulo=Enois`, 60 frames, `dt=16ms` |
| o renderer é real | `SDL_RenderReadPixels` + `SDL_ReadSurfacePixel` | os pixels **igualam** a cor escrita: `(10,200,90,255)` |
| o `extern` recusa no WASI | o compilador | `FFI001` — honesto, nunca fallback silencioso |
| o WASI desenha em SOFTWARE | escritor PPM | PPM P6 de 32×16, pixels exatos pelo ImageMagick |
| o loop puro roda em todo alvo | `GameWindowE2ETest` | verde |
| conversões escalares no WASI | `WasmWasiCastE2ETest` | agora verde (§1180) |

---

## §2 Fatia 1 — a face nativa

`libs/game/Sdl3Backend.kf` (`package game`):

```
var b = Sdl3Backend("Enois", 800, 600)   // janela + renderer, lança "SDL3: <err>" na falha
var w = Window("Enois")
w.frame { dt: Int, self: Window ->
    val keys = b.pollKeys()              // o event pump do SDL → List<String> (seguradas, este frame)
    if (b.quitRequested()) { self.stop() }
    b.clear(10, 20, 30)                  // fundo
    b.present(queue)                     // DrawList → o renderer
    queue.clear()
}
b.close()
```

O que a fatia 1 precisa provar:

1. a janela abre e reporta o título;
2. `present(DrawList)` desenha cada `DrawCmd` (retângulo sólido por padrão; um caminho de imagem é a fatia 2);
3. `pollKeys()` reporta as teclas seguradas no frame (o contrato `Keys.beginFrame(backend.heldKeys())`);
4. `quitRequested()` transforma o fechar-da-janela em `self.stop()`;
5. **leitura de pixel** — desenha uma cor conhecida, lê de volta, afirma a igualdade (a mesma medição que provou o FFI).

A prova é `Sdl3BackendE2ETest`, headless (`SDL_VIDEODRIVER=dummy`) no host mais a leitura.

---

## §3 Fatia 2 — a face de software do WASI

`libs/game/SoftwareBackend.kf` (`package game`, usa só `kof.io`):

```
var b = SoftwareBackend(320, 200, "frame.ppm")
b.clear(0, 0, 0)
b.present(queue)          // retângulos pintados num framebuffer Int[]
b.write()                 // PPM P6 no caminho
```

Este é o contrato honesto do WASI: janela não existe, então o frame é um ARQUIVO. Funciona hoje porque `writeText` + `as Char` estão ambos verdes (medidos em §1).

---

## §4 Como concluir

* fatia 1: `Sdl3Backend` + `Sdl3BackendE2ETest` (leitura + input), linha no `docs/graphics-gaming.md`
* fatia 2: `SoftwareBackend` + `SoftwareBackendE2ETest` (header do PPM + pixels), rodada WASI verde
* promover para `docs/` só quando as duas faces estiverem verdes e o DoD do plano cumprido
* o porte arm/risc explicitamente NÃO é deste plano (mantenedora: "depois quem quiser que porte")

## §5 Fora de escopo

* renderização via KofJS (ordem da mantenedora, 22/11)
* áudio (`kof.game.Audio` tem a sua fatia SDL3, já pousada)
* 3D (o caminho de present do `Scene3d`/`Mesh` vem depois do `DrawList` 2D)

---

## §6 A fronteira do WASI, MEDIDA (22/11) — a fatia 2 está bloqueada pelo subset, não por este plano

A mantenedora pediu compatibilidade com o WASI. A medição diz ONDE está o muro, e ele não é o backend:

| forma | WASI | evidência |
|---|---|---|
| um **`_start` puro** que desenha e escreve um PPM | ✅ roda | PPM 8×4, pixels exatos pelo ImageMagick |
| um record + `listOf<Cmd>()` + um `while` sobre ele, TUDO dentro do `main` | ✅ roda | 2 retângulos pintados, recorte correto, PPM exato |
| o mesmo desenho movido para uma **função** (`static`, de instância ou top-level) | ❌ `WASM002` | `chamada 'ppm' nao resolvida no subset escalar da unidade 15.2` |
| um `Int[]` ou `List<T>` como **parâmetro de função** | ❌ `WASM002` | a mesma recusa |
| `new Int[n]` / `listOf<T>()` **dentro de um método** | ❌ `WASM002` | `new Int[n] fora de '_start'` / `kof.listNew` |
| `Sdl3Backend` (a face nativa) importado | ❌ `FFI001` | honesto: `extern` não existe no WASI |

A LEI DE HOJE: **o subset WASI 15.2 aceita o corpo de `_start` e nada mais.** Uma rotina de desenho extraída para qualquer função — que é o que uma face de biblioteca É — é recusada. Então um `SoftwareBackend` que outros programas importam ainda não pode existir no WASI; o que PODE existir é um programa cujo `main` desenha direto, e isso está provado acima.

O QUE ESTE PLANO DEVE, portanto, na fatia 2: ela está **bloqueada nas unidades 15.3+** (a admissão de funções/coleções/IO do subset escalar que o `docs/wasm-wasi-plan.md` já agenda), NÃO no desenho do backend. O requisito WASI da mantenedora está atendido no nível do PROGRAMA hoje (o PPM medido) e será atendido no nível da BIBLIOTECA quando o subset admitir chamadas — o porte é um `SoftwareBackend` sobre a MESMA lei `PaintCmd` medida na segunda linha de §6.
