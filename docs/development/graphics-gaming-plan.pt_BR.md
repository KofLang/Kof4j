[English](graphics-gaming-plan.md) | [Português](graphics-gaming-plan.pt_BR.md)

# Graphics, Games e Media — Superfície de Intenção do Kof

last: fatia-3.4a probe do backend POUSADA 09/10 (decisão F executada: FFmpeg 9.0.2 upstream LGPL-2.1+ vendido `~/.local/share/kof-ffmpeg/usr` da fonte, SEM `--enable-gpl`, `scripts/provision-ffmpeg.sh`; `FfmpegFfiProbeE2ETest` 4/4 — licença do probe `LGPL version 2.1 or later` em JVM + Native x86-64, faces cross pulam com motivo; fix `-rpath-link` do dir de extern no `NativeAssembler` para o fecho DT_NEEDED do vendor resolver antes da pilha ffmpeg conflitante da distro)
last: fatia-3.5b POUSADA 09/10 (a fila de draw da cena 3D: `libs/game/Scene3d.kf` — `DrawCmd3d` snapshot registra EM ORDEM, meshes escondidas não registram nada, `Mesh.draw(queue)` espelha o padrão do Sprite; `GameScene3dE2ETest` 6/6 0 skips — o probe cobre câmera/mesh/material/luz + a fila; bateria game 75/0F; o snippet 3D do corpus com compile verificado com a lib game instalada)
doing: fatia-3.4c (poke — a contraparte de escrita do peek) e então a promoção desta superfície pura do plano para docs/
next: 3.4c = `buffer.poke8/32/64` (formas raw + Buffer, trap de bounds) desbloqueando as chamadas av_*_free no fluxo de video + depois o plano promove para docs/ (a superfície pura completa + validada; as faces de backend — matriz de visão, carregamento de mesh, shading, present, fila de decode — ficam documentadas como a fronteira)
state: UNDER DEVELOPMENT

**Dono:** `192.168.15.15:9092` — lane security/connectors, frente graphics/gaming; reivindicado 05/10 (os claims do spike-3.0 `192.168.15.30:9093` eram runner/tooling, históricos).
**Status:** **EM DESENVOLVIMENTO** — promovido 30/09 de `future/` por `D-GRAPHICS-SPIKE` (spike 3.0 = medição + stack apenas, sem API) sob `D-FUTURE-PROMOTION`.
**Fonte normativa:** `DECISIONS.md` §D-GRAPHICS-GAMING + adendos da mantenedora + §D-GRAPHICS-SPIKE.
**Deps:** R3/FFI-ABI, runtime, matriz de capabilities, fronteira da stdlib, suíte de conformância

> **Regra fundamental:** toda sintaxe aqui é **forma de intenção**; a forma
> definitiva da linguagem é decisão da mantenedora. Nenhuma keyword/namespace
> abre a partir deste doc. A fatia 3.0 é **infra + relatório de medição apenas**
> — **não** adiciona API (a decisão autoriza o spike, nada mais).

## 0.1 Estado real (spike 3.0, medido 30/09)

Medido na `lab` (nunca por familiaridade — `D-GRAPHICS-SPIKE`):

- **JavaFX: 0** — `grep -rins javafx` sobre `kof-*/src/**`, `pom.xml` e `*.kf` dá
  **0**; todos os hits (237) são prosa de documentação/treinamento. Agora imposto
  por `scripts/check_javafx_absent.sh` (self-test RED-first em
  `scripts/tests/check-javafx-absent-test.sh`).
- **Libs candidatas no host (dev box x86-64):** `.so` de runtime presente para SDL2
  (`2.30.0`), OpenAL (`1.23.1`), FFmpeg libavformat/avcodec (`6.1.1`); **delta
  02/10: headers `-dev` AGORA presentes para SDL2 (`libsdl2-dev 2.30.0` — `SDL.h`
  completo/áudio/gamecontroller/haptic/eventos) e FFmpeg (`libavcodec-dev` /
  `libavformat-dev` / `libavutil-dev` / `libavfilter-dev` 6.1.1)**; seguem sem
  `-dev` sdl3/raylib/glfw3/openal/miniaudio (`pkg-config` não acha nenhum;
  sem headers em `/usr/include`). Bônus só-runtime: SDL_ttf `2.0.11`
  (era SDL1.2, sem `-dev`). Licenças lidas dos arquivos `copyright` da distro
  (SDL2 = zlib/libpng + permissiva, OpenAL = LGPL-2+, libavformat = LGPL-2.1+).
  É a medição do spike, não a escolha de stack — a escolha em si é `G1` decidido
  (**SDL3**) em `D-MAINT-BATCH-0510` (05/10); ver a matriz abaixo.
- **Nuance de licença (medida do `.so` linkado, 30/09):** o FFmpeg da distro é
  **buildado com GPL** — `avcodec_license()` = `GPL version 3 or later`,
  `avformat_license()` = `GPL version 2 or later`, e `--enable-gpl` aparece em
  `avcodec_configuration()`. O "LGPL-2.1+" do `copyright` é a base upstream,
  **não** o build entregue → uma escolha de stack GPL, se tomada, é uma decisão
  de licenciamento da mantenedora (o spike só reporta). **Confirmado em 02/10
  em nível C** (gcc + `pkg-config`, sondas em scratch do host, sem commit):
  `avcodec_license()`/`avformat_license()` devolvem as mesmas strings GPL e
  `--enable-gpl` está na configuração.
- **Capacidade headless (sonda ctypes, 30/09):** o SDL2 inicializa sem display —
  `SDL_Init(VIDEO|AUDIO)` rc=0 sob `SDL_VIDEODRIVER=dummy` +
  `SDL_AUDIODRIVER=dummy` (`2.30.0`); o OpenAL-Soft abre device nulo —
  `alcOpenDevice(NULL)` + contexto OK sob `ALSOFT_DRIVERS=null` (`AL_VERSION =
  1.1 ALSOFT 1.23.1`). raylib/GLFW/miniaudio **não estão presentes** no host
  (`pkg-config`/`dpkg`), então ficam não medidos aqui. **Endurecido em 02/10
  com sonda C** (gcc + `sdl2-config`, scratch do host, sem commit):
  `SDL_GetVersion` = 2.30.0 e `SDL_Init(VIDEO|AUDIO)` rc=0 sob os drivers
  dummy — compila+linka+inicializa, mais forte que a sonda ctypes. OpenAL
  inalterado (só runtime, sem headers para compilar).
- **Delta SDL3 (medido 05/10, G1):** a SDL3 não está instalada no host, mas os RPMs `SDL3-devel` + `libSDL3-0` do repo Tumbleweed foram extraídos num prefixo local e medidos: `gcc probe.c -lSDL3` compila+linka, e `SDL_Init(VIDEO)` + `SDL_CreateWindow` + `SDL_GetWindowTitle` + `SDL_DestroyWindow` + `SDL_Quit` rodam sob `SDL_VIDEODRIVER=dummy` (`driver=dummy`, `title=kof`, rc=0). O **binding FFI do Kof para a mesma forma também foi medido** — a primeira vez que a SDL3 é dirigida a partir do Kof — na JVM e no Native x86-64 (headless), imprimindo `init=true / driver=dummy / title=kof`. Isso fecha o `?` da SDL3 na matriz abaixo. Também revelou e corrigiu o bloqueador de paridade `known-bugs` §606 (uma biblioteca C com estado perdia seus globais entre chamadas `extern` na JVM/JS porque a arena do lookup era fechada por chamada); a stack não é utilizável sem essa correção. O cross (riscv64/aarch64) segue `?`: a stack escolhida precisa entregar suas libs+headers no sysroot cross.
- **Cross (riscv64/aarch64): ainda não mensurável.** Nenhum `.so`/header candidato
  está no sysroot cross da distro, e a toolchain cross do projeto
  (`scripts/setup-cross-toolchain.sh`, padrão `/tmp/kof-cross`) não foi montada
  neste ambiente (**segue ausente em 02/10** — `/tmp/kof-cross` não existe).
  **Qualquer stack escolhida precisa entregar suas libs cross
  nesse sysroot** — requisito concreto e testável para a fatia 3.1, não promessa.
- **Substrato R3/FFI presente** (a dependência nomeada no §3): `FfiSignature`,
  `AbiLayout`, `FfiStructLayout`, `CompilerFfiBinding`, `JvmFfiRuntime`,
  `NativeFfiCall`, `ExternalClasspath`, `KofProcess` (ver `docs/ffi-abi-structs.md`).
  Qualquer mecanismo gráfico é extensão de R3 primeiro — sem FFI paralela.
- **`kof.ui`:** handles no-op em JVM/Native, DOM no KofJS (`KOFUI-AUDIT`);
  **`kof.media`:** só bitmap/WAV/metadados/mic; playback/streaming/mixer/vídeo
  ausentes (`MEDIA001`/`MEDIA003`). Ambos seguem lacunas honestas até um backend real.

**Matriz de candidatas (`G1` decidido — SDL3 — `D-MAINT-BATCH-0510`; a matriz agora é a entrada de vendoring/ABI para a fatia 3.1, não uma escolha em aberto; `?` = não medido):**

| Candidata | Domínio | Licença (`?` = confirmar upstream) | Roda no host | Headless | Cross (riscv64/aarch64) | Eixo |
|---|---|---|---|---|---|---|
| SDL3 / SDL2 | janela+input+áudio | zlib/libpng + permissiva (`copyright` da distro) | `.so` de runtime SDL2 `2.30.0` **+ `-dev` (02/10)**; RPMs da SDL3 `3.4.16` extraídos para um prefixo local **05/10**; compila+linka em C para ambas | dummy driver SDL3 + SDL2 **medido OK**; **o FFI do Kof dirige init/janela/título/quit da SDL3 em JVM+Native (05/10)** | `?` | uma lib, muitos alvos |
| raylib | 2D/3D+áudio | zlib (`?`) | ausente | `?` | `?` | 2D batteries-included |
| GLFW + API GL | janela+contexto | zlib (`?`) | ausente | contexto offscreen (`?`) | `?` | fina, exige expertise GL |
| miniaudio | áudio | public-domain/MIT-0 (`?`) | ausente (header-only, drop-in) | mix offline sim (`?`) | `?` | áudio single-header |
| OpenAL-Soft | áudio | **LGPL-2+** (`copyright` da distro) | `.so` de runtime `1.23.1` presente, sem `-dev` | backend nulo **medido OK** | `?` | áudio posicional 3D |
| FFmpeg / Libav | vídeo+codecs | **buildado com GPL** aqui (`avcodec_license()` = GPLv3+; `--enable-gpl`); base upstream LGPL-2.1+ | `.so` de libavcodec/avformat `6.1.1` **+ `-dev` (02/10)**; compila+linka OK em C, GPL confirmado em C | API de codec (`?`) | `?` | conjunto de codecs completo |

**Recomendação (guiada por medição, não por familiaridade):** a regra JVM do plano
(§11: nunca JavaFX/Swing/AWT/`javax.sound`) + o acoplamento R3-first (§3) apontam para
**uma stack portátil multi-alvo para janela+input+áudio** (SDL3 é a candidata natural)
e **FFmpeg/Libav para codecs de vídeo** (nunca caseiros, §10/§14).
**Decidido:** `G1` é **SDL3** (`D-MAINT-BATCH-0510`, 05/10); o spike removeu as
incógnitas e restaurou a guarda — a matriz abaixo agora é a entrada de
vendoring/ABI, não uma escolha em aberto.
**Ressalva da sonda de licença:** a face FFmpeg só é "livre" se um build LGPL for
empacotado — a da distro mediu GPL (acima), então usá-la como está é uma decisão
de licenciamento, não só técnica.

**Como terminar (ordem das fatias, §15):** 3.0 (esta infra+relatório) → **3.1**
window/frame/input em JVM/Script/Native/JS + conformância → 3.2 (2D) → 3.3
(áudio, golden PCM offline) → 3.4 (vídeo, frame readback) → 3.5 (3D, só se a
paridade permitir) → 3.6 (corpus). Cada fatia é una, testada, e usa a **decisão
`G1` (SDL3)** registrada em `D-MAINT-BATCH-0510` antes de qualquer API (a matriz do
relatório do spike é a entrada de vendoring/ABI).

# 0. Objetivo

Superfície de intenção (o backend decide como) para: gráficos 2D/3D, janelas, game loops, input, sprites, tilemaps, áudio, vídeo, media, integração KofUI, jogos. Não é uma linguagem gráfica dentro do Kof.

# 1. Princípios

- **Intenção antes de mecanismo:** `sprite("player.png").at(100, 80).draw()` (intenção) vs `createTexture/bindTexture/beginBatch/drawQuad/swapBuffers` (mecanismo, escondido).
- **Loop é da plataforma:** usuário escreve `frame { dt -> update(dt); draw() }`; clock/vsync/scheduling/polling/submit/present são backend.
- **Nenhuma API estrangeira cruza:** nunca `SDL_*`, `gl*`, `canvas.*`, `MediaPlayer`, `javafx.*` — tecnologia de backend não é linguagem.

# 2. Estado medido (beta-0.5.0)

- **JavaFX:** zero uso (medição 09/21; ocorrências são comentários de sintoma do launcher). Não é implementação anterior; decisão: nunca introduzir. Sem migração.
- **`kof.ui`:** JVM/Native = handle sem render (`kof_ui_window_new`, setters/show vazios); KofJS = superfície DOM/webview funcional. Código de gap até o real: `GFX00x`. No-op nunca lê como compatibilidade.
- **`kof.media`:** tem abrir/salvar bitmaps, metadados de vídeo, sampling WAV, enum/mic. Falta: playback, streaming, mixer, pipeline de vídeo/playback, superfícies Native + JS (`MEDIA001`/`MEDIA003`). Evoluir aditivamente; programas existentes continuam funcionando.

# 3. Dependência R3

Gráficos cruzam Kof IR → backend → ABI → runtime → biblioteca → SO/dispositivo e precisam de handles/pointers/buffers/structs/callbacks/arrays/strings/lifecycle/ownership/error-codes/native-resources. **Sem FFI paralelo** — mecanismo faltante vira extensão R3 primeiro.

# 4. Arquitetura (5 níveis)

```text
Kof App (intenção) → Kof Graphics API → Kof Runtime ABI (handles/buffers/events)
→ backends JVM/Native/JS → plataforma/browser
```

A mesma intenção alcança todo alvo.

# 5. Recursos

Objetos de plataforma (`Window Sprite Texture Tilemap Mesh Material Camera Sound
Music Video InputDevice`) escondendo impl (GL/Vulkan/WebGL/imagem/nativo/GPU).
Lifecycle por recurso deve responder: criado/lazy/carregado/disponível/dono/
release/caching/perda de janela. Sem gerenciamento GPU manual quando o backend resolve.

# 6. Janela / loop / clock

- Janela (do backend, forma DECIDIDA — `D-GRAPHICS-WINDOW-FORM`): `Window("Pong") { frame { dt -> ... } }`
  (a alternativa `Scene("Pong") { dt -> ... }` foi rejeitada); cobre título/tamanho/fullscreen/resize/foco/
  close/DPI/orientação/visibilidade/input; sem APIs de SO.
- Loop: backend detém clock/vsync/scheduling/poll/submit/present; programa recebe
  `dt` (unidade DECIDIDA — `D-GRAPHICS-WINDOW-FORM`: Int milissegundos, primeiro frame `0`).
  As semânticas restantes do loop também estão DECIDIDAS (`D-MAINT-BATCH-0610`, 06/10):
  **long-frame = clamp do `dt`** (o delta real limitado por um teto configurável —
  guarda anti spiral-of-death, nunca um `dt` ilimitado); **limit = só vsync on/off**
  (sem cap de frame-rate); **pause = `pause()`/`resume()` explícitos, `minimized`
  suspende o render, perder foco NÃO pausa**.
- Clock virtual obrigatório (`dt` determinístico) para física/animações/input/
  áudio/playback/goldens.
- Hospedeiro puro POUSADO 07/10 (`libs/game/Window.kf`, fatia 3.1): `Window("Pong")`
  + `clock(fonte)` + `dtClampMillis(n)` (A1) + `vsync(on)` (A2) +
  `pause()`/`resume()`/`minimize()`/`restore()`/`blur()`/`focus()` (A3) +
  `frame { dt: Int, self: Window -> ... }` sobre o `Clock` composto; o corpo de
  2 args (window passada como `self`, nunca capturada) desvia do que era o
  `known-bugs` §620 (lambda com captura + args = primeiro arg lixo no cross),
  ✅ CORRIGIDO 07/10 pela lane native-backend, e é verde em todo alvo
  (`GameWindowE2ETest` 8/8). Construí-lo corrigiu o `known-bugs` §619
  (campo sem inicializador + membro `(` mal-parseado, `ClassMemberParseE2ETest` 4/4).
- Binding do pump POUSADO 07/10 (resto da fatia 3.1, só-teste sobre o stack
  vendado — sem API Kof nova): `Sdl3PumpE2ETest` **5/5** dirige uma janela
  SDL3 headless real (driver `dummy`) com drain (`SDL_PollEvent` num
  `Buffer(U8)` de 128 bytes, o tamanho do `SDL_Event`) + push + pacing
  (`SDL_Delay`/`SDL_GetTicks`) + dois frames de `Clock` virtual com snapshots
  de `Keys`, golden `init=true/push=true/poll=0/paced=true/frames=2/quit=true`
  em JVM + Native x86-64 + riscv64 + aarch64 sob qemu. Fronteiras medidas: o
  SDL descarta evento pushado de tipo zero (`poll=0` pinado); eventos reais do
  backend existem (ex. `0x404 MOUSE_ADDED` na criação) mas as contagens variam
  por ambiente, então o drain conta em silêncio; síntese com scancodes espera
  uma superfície de escrita de bytes no `Buffer` (hoje só alloc+leitura —
  fronteira documentada, não gap silencioso).

# 7. Input

Snapshot por frame: `keys.down("left")`, `keys.pressed("space")`;
estados `down/pressed/released`; `mouse.pos/down/pressed`, `pad.stick/down/pressed`;
backend traduz scancodes/X11/Wayland/KeyboardEvent/WinVK (semântica exata TBD).

# 8. 2D

Primeiro nível. `sprite("player.png").at(120, 80).draw()`; transforms
`at/scale/turn/origin/flip` (API TBD); animação `player.frames("walk")` +
`player.animate()` (plataforma: atlas/batching/upload/seleção).
Tilemaps = intenção de mapa (`tilemap("level.png", 16)`). Render: app declara *o
quê*, backend decide *como* (batching/atlas/command-buffer/ordem/cache/upload ocultos).
- Fatia 3.2a POUSADA 07/10 (intent puro, sem render): `libs/game/Sprite.kf`
  (fábrica `sprite()` + `at/scale/turn/origin/flip/show/hide`, `worldPointX/Y`
  = `pos + R·S·F·(p − origin)`, `frames()/animate(dtMs, frameMs)` sobre delta
  do chamador, `draw(queue)`) + `libs/game/Draw.kf` (record `DrawCmd` +
  `DrawList` ordenada: `draw/clear/size/commandAt`, draw invisível não
  registra nada) + `libs/game/Trig.kf` (`trigSin`/`trigCos` em Kof puro,
  Taylor até x^13 — `math.sin`/`math.cos` não tinham símbolos Native,
  `known-bugs` §621, ✅ CORRIGIDO 07/10 pela lane native-backend com um gate
  honesto `MATH001`, então a lib segue usando zero trig de backend). Refs entre
  arquivos do mesmo pacote exigem `import` explícito (medido: `import
  game.Draw` / `import game.Trig` dentro do `Sprite.kf`, precedente
  `Window.kf` → `game.Clock`). Prova: `GameSpriteE2ETest` **14/14**
  (goldens de transform + animação + draw em JVM + Script + Native x86-64 +
  JS; golden de transform também riscv64 + aarch64 sob qemu; goldens em
  milli-units, nunca `Double` cru). Próxima: intent de tilemap (3.2b).
- Fatia 3.2b POUSADA 07/10 (intent puro, sem render): `libs/game/Tilemap.kf`
  (fábrica `tilemap()` + grade esparsa ilimitada: `tileAt`/`setTile`/
  `clearTile`/`hasTile`/`count`/`clear`, origens em pixel `worldX`/`worldY`;
  id negativo limpa, não-setado lê `-1`, `tileSize <= 0` lança). Construí-la
  confirmou dois fatos da API de `List` que o compilador diz explicitamente
  (atribuição `[]` é só de arrays → `l.set(i, v)` por `SEM054`; remoção é
  `l.remove(i)`, sem `removeAt`) — conhecimento da linguagem, sem bug. Prova:
  `GameTilemapE2ETest` **6/6** (golden todo-inteiro em JVM + Script + Native
  x86-64 + JS + riscv64 + aarch64 sob qemu).

# 9. 3D (depois)

Mínimo: mesh/camera/material/light/transform. `mesh("hero.glb")`,
`camera3d().at().lookAt()`, `draw(scene3d { ... })` — apenas ilustrativo.
Sem parsers próprios (glTF/OBJ via libs maduras; critérios: licença/segurança/
cobertura/manutenção/testabilidade/cross-platform). Shaders escondidos no início
(Kof/SPIR-V/WGSL/GLSL/HLSL/cross-compile adiados, fora da primeira fatia).

# 10. Áudio/vídeo

- Intenções: `sound("boom.ogg").play()`, `music("theme.ogg").loop().play()`;
  backend detém decoder/buffer/mixer/output/device/latência/vozes (app nunca
  cria canais/buffers/callbacks/threads). Faces do mixer (`volume/pause/resume/
  stop/loop/fade/pan`) exigem contrato cross-target cada. Latência medida
  request→submission→audível por alvo (valor após spike 3.0/3.3). Devices:
  capability comum + gaps honestos por alvo (restrições de browser).
- Vídeo: `Window("Trailer") { video("intro.mp4").autoplay() }`; sem demuxer/
  decoder/codec/fila/hardware-decoder no app. Sem codecs próprios (FFmpeg/Libav/
  nativos; critérios: licença/alvo/segurança/manutenção/formatos/headless).
- Fatia 3.4a POUSADA 07/10 (intent puro de playback, sem decoder): `libs/game/
  Video.kf` (fábrica `video()` + `play/pause/stop/seek/volume/loop/mute`,
  `tick(dtMs)` sobre timestamps do chamador, `position/frameIndex/
  finished`; metadados sem sentido lançam na construção, `seek` clampa,
  volume clampa em [0,1], fim-de-stream para (ou dá wrap com loop)).
  Prova: `GameVideoE2ETest` **6/6** (golden todo-inteiro em JVM + Script +
  Native x86-64 + JS + riscv64 + aarch64 sob qemu). Decoder e frame
  readback seguem trabalho de backend (decisão F do FFmpeg LGPL,
  mantenedora).
- `kof.media` hoje (bitmap/WAV/metadados/mic) → playback/streaming/mixing/
  video-playback, aditivamente.
- KofUI ≠ linguagem concorrente (apps UI vs jogos); compartilha janela/input/
  vídeo/imagens/eventos onde equivalente.
- Fatia 3.3a POUSADA 07/10 (mixer offline puro, sem saída audível):
  `libs/game/Audio.kf` (record `Sound` + `Mixer`: `play(sound, startMs)`,
  `render()` para PCM 16-bit `Int[]`, vozes sobrepostas somam e clampam em
  [-32768, 32767], cada loop reinicia a fase, `rate <= 0` lança). Amostras
  sintetizam só de `game.Trig` (zero trig de backend — o runtime Native não
  tinha símbolos `sin`/`cos`, `known-bugs` §621, ✅ CORRIGIDO 07/10).
  Construí-lo bissectou e
  catalogou o `known-bugs` §622 (uma 2ª/aninhada atribuição condicional no
  mesmo local Double se perdia no cross; reprodutor `twoIfLit` de 15 linhas +
  variantes else/while/return/aninhadas), ✅ CORRIGIDO 07/10 pela lane
  native-backend (o salto condicional cross comparava padrões de bits Double
  com ramos inteiros com sinal), e embarca o desvio sem-branch
  (redução via `roundTo` no `Trig.trigNorm`, ainda válido e mais rápido). Prova: `GameAudioE2ETest`
  **6/6** (golden de inteiros exatos em JVM + Script + Native x86-64 + JS +
  riscv64 + aarch64 sob qemu). Faces audíveis de decoder/playback/device
  seguem trabalho de backend.
- Fecho do ledger (medido 07/10, sem mudança de gate): `kof.game` NÃO precisa
  de linha no ledger — a questão R1 do namespace (`kof.game` vs `package
  game`) é aberta da mantenedora, `HARD_DENY game` já codifica
  official-package-only, `scripts/package.sh` embarca todo `libs/`
  genericamente, e imports resolvem pelo filesystem. Inventar uma linha
  `kof.game` afirmaria um namespace indecidido. Próxima: faces de áudio vivo
  no backend + 3.4 (vídeo).
- Fatia 3.3b POUSADA 07/10 (ABI de áudio SDL3, só-teste sobre o stack
  vendado — sem saída audível afirmada, CI não tem alto-falantes):
  `Sdl3AudioE2ETest` **5/5** inicia o subsistema de áudio, abre o device
  default de playback, lê de volta o `SDL_AudioSpec` negociado (S16 stereo
  44100 Hz, 1024 frames de buffer — idêntico nos quatro alvos), pausa/
  retoma, fecha e quita, golden `init/open/fmt/format/channels/freq/
  frames/pause/resume/quit=true` em JVM + Native x86-64 + riscv64 + aarch64
  sob qemu. Fatos Kof/FFI medidos: `SDL_AUDIO_DEVICE_DEFAULT_PLAYBACK` é
  passado como `0 - 1` (Kof não tem literais unsigned; mesmos 32 bits
  baixos); o `spec` anulável não soletra `NULL` (`SEM048`), mas um buffer
  zerado de 12 bytes é aceito e negocia os defaults do dummy aqui. Faces
  audíveis de decoder/playback/streaming seguem trabalho posterior (decisão
  F do FFmpeg LGPL para codecs, mantenedora).
- Fatia 3.3d POUSADA 07/10 (encoder WAV, Kof puro — o PCM do mixer offline
  feito embarcável): `libs/game/Wav.kf` (`encodeWav(samples, rate,
  channels)` escreve imagens WAVE PCM16 (header RIFF de 44 bytes a partir
  de códigos `charAt`, nunca números mágicos; só mono/stereo e `rate <= 0`
  lança; amostras clampam) e as lê de volta campo-exatas
  (`wavSampleCount`/`wavRate`/`wavChannels`/`wavSampleAt`). Destravada ao
  medir que escrita indexada em `Byte[]` mais `as Byte` (wrap mod-256)
  funcionam em todo alvo — a mesma superfície de que uma futura ponte de
  submit para stream precisa. Prova: `GameWavE2ETest` **6/6** (header exato
  + golden de roundtrip, incl. extremos ±32768, em JVM + Script + Native
  x86-64 + JS + riscv64 + aarch64 sob qemu).

# 11. Alvos

- **JS:** capabilities de browser como backend; `video("intro.mp4")` pode baixar
  para elemento HTML (detalhe de lowering, API segue Kof).
- **WASM:** mesma intenção quando o alvo existir; documentado no plano WASM/WASI,
  nunca duplicado aqui.
- **Native:** stack portátil, sem bindings manuais; x86-64/aarch64/riscv64 entram
  na matriz individualmente.
- **JVM:** nunca JavaFX/Swing/AWT/javax.sound; rota Kof→JVM→R3→stack portátil.
- **Script:** mesma semântica de intenção, delega ao ambiente; capability
  ausente → `GFX001`/`SND001`/`VID001` (nunca unknown-method).

# 12. Matriz de capabilities / gaps / paridade

- Matriz por operação (`window/sprite/audio/video/3d` × JVM/Native/JS/Script);
  `✓` só após implementação+testes+golden+paridade+docs.
- Famílias de gap `GFX00x/INP00x/SND00x/VID00x` (ex. `GFX001` indisponível,
  `SND001` sem playback, `VID001` sem playback); códigos finais entram no
  catálogo normativo antes de implementar; nunca esconder.
- Promoção = mesmo contrato observável em JVM+Script+Native+JS-Web
  (compile+execute+esperado+conformância — só-compilar não basta).

# 13. Observabilidade / conformância

- Pixels testáveis: render→readback→buffer→hash (nunca driver/GPU/framebuffer
  screenshots; formato/tolerância definidos na implementação).
- Áudio offline: programa→mixer→PCM→hash/referência (volume/mix/ordem/loop/
  duração/canais, sem caixas de som).
- Caminhos de conformância (`graphics/sprite/basic.kf`, `audio/mix/basic.kf`, …)
  rodam JVM/Script/Native/JS comparando observáveis.
- Goldens: sequências de frame/input/sprite/transform/audio-mix/media-meta/
  video-decode (clock virtual, input determinístico, mixer offline, frame readback).
- Fuzz em transforms/coords/sizes/textures/input/lifecycle/assets/mídia
  malformada/audio/video/release (arquivos não-confiáveis primeiro).
- Segurança: malformados/overflow/corrupção/bombs/decoder/recursos/sandbox —
  nunca codecs próprios por isso.

# 14. Stack / licenças / escala

- Candidatos (spike 3.0 decide; nunca por familiaridade): gráficos SDL3/SDL2/
  raylib/GLFW+API; áudio miniaudio/OpenAL-Soft/SDL-audio; vídeo FFmpeg/Libav/
  nativos. Critérios: licença/cobertura de alvos/manutenção/segurança/headless/
  cross-compile/estabilidade de API/binário/startup/performance.
- Licenças analisadas antes de integrar (GPL/LGPL/zlib/MIT/BSD/Apache ×
  distribuição/runtime/executável/estático/dinâmico/Native/JVM/JS); "open source"
  sozinho não aprova nada.
- Camada fina apenas (`Kof API → abstração fina → backend`); plataforma detém
  complexidade. Benchmarks (startup/janela/scheduling/throughput/upload/draw/
  latência/mix/decode/memória) comparam mesma-semântica apenas; sem afirmações
  sem medição.
- Orçamentos de memória (runtime/texturas/áudio/vídeo/temps; Native/mobile/WASM/
  embedded); sem buffers manuais. Política de cache transparente (+ release sob
  demanda se contratado).
- Assets (`sprites/sounds/music/video/models`): copy/embed/compress/hash/cache/
  paths/packaging definidos depois, fora da primeira fatia.
- Caminho headless obrigatório (CI/testes/fuzzing/servidores/conformância;
  render buffer, sem janela). `kof test` nunca precisa de monitor/GPU/caixas/
  mic/câmera.
- Android: mesma intenção, sem split `KofAndroidGraphics`; iOS precisa de decisão.
- Debugging relaciona fonte Kof→operação→runtime (sem internals de GPU no início).
- Diagnósticos nomeiam código Kof (`Kof graphics error GFX002`, recurso, razão,
  `game.kof:42` — nunca `SIGSEGV in libSDL` cru). Taxonomia de erros de asset:
  missing/formato-nao-suportado/falha-decode/sem-permissão/exausto (nunca um
  "not found" genérico).
- Modelo de threading explícito (main/render/audio/loading) sobre spawn/async/
  channels/scheduler do Kof. Determinismo: runtime pode variar, teste não
  (sem não-determinismo no harness de conformância).

# 15. Fases / promoção / aberto

- **Fatia 3.1 — iniciada 05/10 (lane security/connectors `192.168.15.15:9092`):** a metade pura e independente de backend do contrato §6/§7 pousou primeiro — `libs/game/Clock.kf` (namespace `kof.game`) é dono do livro-caixa de frames e do `dt` sobre timestamps monotônicos fornecidos pelo chamador (o "relógio virtual" que o plano exige para goldens determinísticos), então não chama API de janela/áudio/vídeo e é honesto em todo alvo hoje. Semântica congelada por `GameClockE2ETest` **4/4** em JVM + Script + Native x86-64 + JS (frame 0 `dt=0`; frames seguintes difam o timestamp anterior; `stop()` encerra `hasNext()`). `libs/game/Keys.kf` acrescenta o snapshot de input por frame do §7: `beginFrame(held)` difa os conjuntos de teclas atual e anterior e deriva `down`/`pressed`/`released`, então o backend só traduz eventos e as transições são determinísticas em todo alvo (`GameInputE2ETest` **4/4**, JVM + Script + Native x86-64 + JS, golden byte-idêntico). `libs/game/Mouse.kf` completa a metade do ponteiro do mesmo snapshot do §7: `beginFrame(x, y, buttons)` guarda a posição do ponteiro e os conjuntos de botões anterior/atual e deriva `x`/`y`, o movimento por frame `dx`/`dy` (0 no primeiro frame, espelhando o `dt=0` do primeiro frame do relógio) e `down`/`pressed`/`released`, então o backend só traduz eventos de ponteiro (`GameMouseE2ETest` **4/4**, JVM + Script + Native x86-64 + JS, golden byte-idêntico). `libs/game/Pad.kf` fecha a superfície de input do §7 com o snapshot de gamepad: `beginFrame(leftX, leftY, rightX, rightY, buttons)` registra os eixos do analógico crus (normalizar a faixa de um dispositivo é política do backend) e difa o conjunto de botões para `down`/`pressed`/`released` (`GamePadE2ETest` **4/4**, JVM + Script + Native x86-64 + JS, golden byte-idêntico). Construir o relógio revelou e corrigiu um defeito do frontend (`known-bugs` §603: o descritor do `invoke` da SAM sintética usava os tipos inferidos dos argumentos). A medição do G1 SDL3 então fechou a incógnita da stack: os RPMs `SDL3-devel`/`libSDL3-0` `3.4.16` foram extraídos para um prefixo local, uma sonda C compilou+linkou+rodou headless (`SDL_VIDEODRIVER=dummy`), e a mesma forma foi dirigida a partir do **Kof** via `extern` em JVM + Native x86-64 (`SDL_Init`/`SDL_CreateWindow`/`SDL_GetWindowTitle`/`SDL_DestroyWindow`/`SDL_Quit` → `init=true / driver=dummy / title=kof`). Esse primeiro uso pelo lado Kof revelou um bloqueador de paridade cross-target — uma biblioteca C com estado perdia seus globais entre chamadas `extern` na JVM/JS porque o lookup da biblioteca era carregado na arena por chamada e fechado — agora corrigido (`known-bugs` §606, `FfiLibraryStateE2ETest` 3/3 JVM+JS+Native, RED-first 2/3 pré-fix). A superfície pura foi então verificada também nos backends cross: `GameCrossE2ETest` **3/3** dirige um frame virtual pelos quatro módulos juntos — frame 0 `dt=0`, `left` pressionado, ponteiro (100, 50) sem movimento, pad neutro; frame 1 `dt=16`, `space` pressionado com `left` mantido, ponteiro (140, 70) movido +40/+20 com `left` pressionado, analógico esquerdo (0.5, −0.25) com `a` pressionado — e afirma o mesmo golden no oráculo JVM e em **riscv64 + aarch64** sob qemu (as suítes por módulo cobrem x86-64 + JS + Script; eixos em mili-unidades). A forma da janela então é DECIDIDA (`D-GRAPHICS-WINDOW-FORM`): `Window("…") { frame { dt -> … } }` com `dt` um `Int` de milissegundos (primeiro frame `0`) — prepará-la mediu e corrigiu um gap de parser (`known-bugs` §611: uma trailing lambda com parâmetro explícito só parseava depois de um receptor `.`; `f { dt: Int -> … }` e `f(1) { dt: Int -> … }` agora também parseiam, `TrailingLambdaParamsE2ETest` 6/6), então a única necessidade restante da forma aninhada literal é um receptor implícito (o Kof não tem: SEM015/SEM025), escrita hoje como `{ w: Window -> w.frame { dt: Int -> … } }`. A stack então foi entregue cross-target: novo `scripts/provision-cross-sdl3.sh` venda a SDL3 `3.4.16` + seu fecho de runtime medido (áudio/X11/wayland/drm/…) + GLIBC 2.44 dos RPMs do openSUSE Ports para `$KOF_CROSS_SYSROOT/usr/<arch>-linux-gnu/{include,lib}` (sem root; a SDL3 aarch64 exige `GLIBC_2.43`, a libc do sysroot era 2.41 — a glibc forward-compatible do Ports fecha isso), e a ABI crua da SDL3 agora é provada ponta-a-ponta headless (`SDL_VIDEODRIVER=dummy`) nos **quatro alvos** — `Sdl3FfiCrossE2ETest` **5/5**: JVM + Native x86-64 + riscv64 + aarch64 sob qemu, golden idêntico `init=true / driver=dummy / title=kof` (`SDL_Init`/`SDL_CreateWindow`/`SDL_GetWindowTitle`/`SDL_DestroyWindow`/`SDL_Quit`), com o par cross pinado para concordar (regra 5). Próximo: desenhar a janela de backend (`Window("…") { frame { dt -> … } }`) sobre o binding medido — a semântica do loop (long-frame/limit/pause/minimized/focus) segue sendo chamada da mantenedora (`D-GRAPHICS-WINDOW-FORM`).
- **3.0** spike+infra (stack/R3/FFI/licenças/headless/cross/guard-JavaFX;
  relatório, sem API) → **3.1** janela/frame/input (JVM/Script/Native/JS +
  conformância) → **3.2** 2D (sprite/texture/transform/tilemap/draw; golden/
  headless/cross/assets/lifecycle) → **3.3** áudio (sound/music/play/pause/
  stop/loop/volume + decoder/mixer/device; golden PCM offline) → **3.4** vídeo
  (`video/play/pause/seek/volume` com contrato definido; frame readback) →
  **3.5** 3D só se stack/alvos/R3/runtime/conformância permitirem (senão `GFX00x`
  segue válido) → **3.6** corpus (training/learn/docs/conformância/paridade).
- Fatia 3.6a POUSADA 07/10 (corpus, só-docs — sem mudança de
  compilador/biblioteca): `training/idioms/game.md` (+PT: formas canônicas
  para os 12 módulos com as restrições medidas — tempo virtual, corpo de
  frame de 2 args, goldens em milli-units, imports por arquivo, sem trig de
  backend) + `learn/42-games.md` (+PT: tutorial "seu primeiro game loop"
  compondo Clock/Keys/Sprite/Draw/Window). Prova: todos os 20 snippets
  extraídos e compilados limpos na JVM (9 EN + 9 PT de idiomas + 2
  tutoriais), ambos os programas de tutorial rodam (`drawn=3`); executá-los
  pegou e corrigiu 2 bugs de doc pré-commit (uma lambda `Int` para fonte de
  clock `() -> Long` — `IncompatibleClassChangeError` em runtime — e um
  print de `Double` cru no exemplo de sprite). Próxima: 3.6b+ (faces de
  decoder, docs de backend) conforme backends pousarem.
- Checklist de promoção: implementação/runtime/alvos/conformância/golden/
  headless/docs/gaps-catalogados/perf/segurança/licenças/corpus (sem "funciona
  na minha máquina").
- Aberto (mantenedora decide): R1 (namespace `kof.game`?), forma scene (call
  vence sintaxe nova), 3D-após-paridade, contratos de hash golden, pick de
  stack, input snapshot-vs-eventos, entrada auto do WASM, media atual
  manter-vs-rebasar.
- Não-objetivos: renderer/mixer/codec/demuxer próprios; expor SDL/GL/WebGL/DOM/
  HTML/CSS/JavaFX/Swing/AWT; APIs por alvo; linguagem de shader precoce;
  fachadas-que-embrulham; paridade parcial como feature; gaps escondidos;
  implementação sem promoção.
- Regra de ouro: o desenvolvedor pensa em **intenção**, nunca na plataforma
  (`sound("shot.ogg").play()` não pode exigir saber áudio de Linux/browser/
  Android). Forma resultante: Kof App sobre UI/Graphics/Media → Contrato de
  Runtime Kof → backend por alvo → plataforma.
