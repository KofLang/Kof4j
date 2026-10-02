[English](image-vision-plan.md) | [Português](image-vision-plan.pt_BR.md)

# Plano Estratégico — Kof Image & Vision

> **Estado (29/09): EM DESENVOLVIMENTO — promovido `future/` → `docs/development/` por `D-FUTURE-PROMOTION` + `D-IMAGE-VISION-GO` (mantenedora 29/09), library-first (`D-KOF-FIRST-IMPL`).**
> **Fatia 1 LANDED 29/09:** `libs/image/` pure-Kof — `Image(path).format()/.width()/.height()` leem o **formato + dimensões em pixels** dos primeiros bytes (`PNG`/`GIF`/`BMP` info+core/`JPEG` SOF/`WEBP` VP8·VP8L·VP8X + `TIFF` + `ICO`/`CUR` + `PNM` P1–P6 + `QOI` + `PSD`/`DDS`/`farbfeld`/`AVIF`-`HEIF`) e o helper `Bool isImage(path)`, sobre um prefixo limitado de 4 KiB do `kof.io.readRange`; sem codec, sem pixels, sem sintaxe nova. Golden medido na JVM + Native x86-64 + riscv64 (qemu) + Script, lacuna JS `IOJS001` — `ImageMetadataE2ETest` 7/7.
> Pesado por R1/R9: `kof.image`/`kof.vision` são **pacotes oficiais** (nascentes `experimental`); codecs e algoritmos vêm de bibliotecas maduras isoladas atrás da API Kof (imageio/turbojpeg/OpenCV/ONNX, avaliação por licença/target). Pixels/filtros e `kof.vision` seguem fatias futuras; achado medido da lane native catalogado como `known-bugs` **§540** (nativos cross falham uma única alocação ≥64 Ki Int).
> **Como terminar:** decode de pixels + dados `Image` → `resize`/`crop`/`rotate` (fatia interop), depois Fase 2 de processamento, Fase 3 `kof.vision`; cada fatia aditiva, com docs + golden em todos os alvos. Regra 6: qualquer operador/semântica nova é decisão da mantenedora; sintaxe real `var`/`val`.
> **Decisão RESOLVIDA 29/09 (`D-IMAGE-SURFACE`, mantenedora):** a superfície de pixels **reusa `Raster`** (sem novos `Image`/`Pixel`/`Color`); os codecs são Kof puro onde viável e usam **imageio** do JVM (via o builtin `kof.image` `image.decode`, um compromisso explícito JVM-only) só onde um decoder Kof puro é inviável, com lacuna honesta `IMG001` nos demais — ver §34. O interop de JPEG pousou 29/09 (`RasterDecodeE2ETest#jpegDecodesOnJvmViaInterop`). (O pedido original segue para histórico.)
> **Fatia 2a LANDED 29/09 (metade Kof-first da fatia de pixels):** `decodeRaster(path)` devolve um `Raster(format, width, height, channels, samples)` provisório para formatos **não comprimidos** — PNM `P5`/`P6` e farbfeld — limitado a ≤16384 amostras (uma leitura, sob o então aberto teto cross-native §540; depois elevado a 262144, §5); formatos comprimidos seguem interop-first atrás da decisão. Prova: `RasterDecodeE2ETest` 7/7 (golden PNM/farbfeld + não-suportado/grande; JVM + Native x86-64/riscv64 + Script; JS `IOJS001`).
> **Fatia 2b LANDED 29/09:** operações de raster pure-Kof sobre o `Raster` provisório — `cropRaster(r,x,y,w,h)` e `resizeNearest(r,w,h)` (vizinho mais próximo), saída limitada pelo mesmo teto; filtragem suave aguarda a fatia de interop. Prova: `RasterDecodeE2ETest` 7/7.
> **Fatia 3g LANDED 29/09 (Kof puro, todos os alvos):** `libs/image/Vp8lTransforms.kf` (novo) + `libs/image/Vp8l.kf` — transforms inversos **predictor** (14 modos, §3.5.1) e **color** (§3.5.2, `ColorTransformDelta = (s8(t)*s8(c))>>5`) do VP8L, aplicados em ordem reversa; o loop de transforms agora lê `size_bits`/grade de subresolução para ambos e a decodificação de entropia foi extraída para `vp8lDecodeImage(r,w,h,metaAllowed)`, de modo que sub-imagens de transform nunca leem o bit meta-prefix (só do ARGB, §3.8.3). Transform color-indexing e grupos meta-Huffman ainda recusados com diagnóstico explícito `IMAGE:`. Prova: `RasterDecodeE2ETest` 19/19 (`webpVp8lDecodesOn*` incl. um stream 8x8 predictor+color gerado pelo libwebp **validado byte a byte contra ele**, JVM + Native x86-64 + riscv64(qemu) + Script); RED medido no decoder pré-fatia (`IMAGE: WebP predictor transform is not supported yet`).
> **Fatia 3h LANDED 30/09 (Kof puro, todos os alvos):** `libs/image/Vp8lTransforms.kf` + `libs/image/Vp8l.kf` — transform **COLOR_INDEXING** do VP8L (§3.5.4): a sub-imagem de paleta (`num_colors = ReadBits(8)+1`, cores delta-codificadas da esquerda para a direita e expandidas para `1 << (8 >> bits)` entradas) é aplicada à imagem de entropia, cujo canal verde empacota `1 << bits` índices de `8 >> bits` bits, do menos significativo para o mais, na largura reduzida `ceil(w / 2^bits)`. Corrigido um bug latente de **`max_symbol`** em `vp8lReadNormal` (`ReadHuffmanCodeLengths` limita o número de *símbolos* de code-length decodificados, não o comprimento do array resultante — RFC 9649 §3.6.2.1); o bug só aparecia quando o flag "use length" reduzia `max_symbol` abaixo do alfabeto e dessincronizava o bitstream. Grupos meta-Huffman ainda recusados com diagnóstico explícito `IMAGE:`. Prova: `RasterDecodeE2ETest` 19/19 (novo stream 8x8 de 8 cores com indexing gerado pelo libwebp, validado byte a byte contra ele; JVM + Native x86-64 + riscv64(qemu) + Script); RED medido no decoder pré-fatia (`IMAGE: WebP color-indexing transform is not supported yet`).
> **Fatia 2 do VP8 lossy LANDED 30/09 (Kof puro, todos os alvos):** `libs/image/Vp8Frame.kf` + `libs/image/Vp8Probs.kf` (novos) — caminhada RIFF/`WEBP` + frame header `VP8 ` completo (RFC 6386 §9/§19): tag de key-frame/start code/dimensões, segmentação, loop filter, número de partições, os seis índices de dequant e a tabela de 1056 probabilidades de coeficiente (defaults + updates). `Vp8Bool` ganhou `signedOrZero`/`bytePosition`. Prova: `Vp8FrameE2ETest` 4/4 vs um oráculo RFC §19.2 independente na JVM + Native x86-64 + riscv64(qemu) + Script. Modelado como classe (não record largo) porque o backend cross corrompia chamadas com ≥9 argumentos (`known-bugs` §546, issue #703 — **CORRIGIDO 30/09**, `NativeCrossWideArgsE2ETest` 3/3).
> **Fatia 3 do VP8 lossy LANDED 30/09 (Kof puro, todos os alvos):** `libs/image/Vp8Frame.kf` agora decodifica os registros de predição por macrobloco de key-frame (RFC 6386 §10/§11): o segment id por macrobloco (quando `update_mb_segmentation_map`), o `mb_skip_coeff` (quando `mb_no_skip_coeff`), o modo luma 16x16, os 16 modos de subbloco luma com contexto quando o modo é `B_PRED` (a `kf_bmode_prob` 10×10×9 indexada pelos modos dos subblocos acima/à esquerda, atravessando fronteiras de macrobloco) e o modo de croma. O novo `libs/image/Vp8ModeProbs.kf` carrega as três tabelas fixas (`kf_ymode_prob`, `kf_uv_mode_prob`, `kf_bmode_prob`). Prova: `Vp8ModeE2ETest` 4/4 vs um oráculo RFC §7.3/§10/§11 independente sobre três arquivos lossy do libwebp (mapa de segmentos 4×4 + `mb_skip_coeff`; 2×2 todo `B_PRED`; modos luma/croma mistos) na JVM + Native x86-64 + riscv64(qemu) + Script — cada segment id, flag de skip e modo idênticos. Próximas fatias: decodificação de coeficientes DCT (§13), predição intra + DCT/WHT inversa (§12/§14), o loop filter (§15).
> **Fatia 4 do VP8 lossy LANDED 30/09 (Kof puro, todos os alvos):** o novo `libs/image/Vp8Coeffs.kf` — o **decodificador de coeficientes DCT/WHT** da partição de token (RFC 6386 §13.2/§13.3): para cada macrobloco não marcado `mb_skip_coeff`, os blocos 4×4 Y2/16 Y/4 U/4 V são lidos da árvore de blocos (end-of-block, zero, 1, 2 e ramos 3–4, os três nós de valor codificados por contexto e os seis tokens de categoria com suas probabilidades fixas de bits extras `Pcat1..Pcat6`) para `mb*400 + block*16 + zig-zagIndex`, com a posição de end-of-block por bloco; os preditores de não-zero acima/à esquerda atravessam fronteiras de macrobloco e são zerados num macrobloco skipado. `vp8TokenPartition` constrói o decoder bool da única partição de token e recusa um frame multi-partição com diagnóstico explícito (o libwebp sempre emite uma) em vez de errar o decode. Prova: `Vp8CoeffE2ETest` **4/4** vs um oráculo RFC §7.3/§13 independente sobre quatro arquivos lossy reais do libwebp (16×16 só-DC, 32×32 todo `B_PRED`, um 64×64 com `mb_no_skip_coeff` + skips e um 64×64 misturando cada categoria de coeficiente), reproduzindo a contagem de blocos não-vazios e as somas de coeficientes com sinal/absolutas de cada macrobloco na JVM + Native x86-64 + riscv64(qemu) + Script. O lookup de banda por posição do oráculo foi conferido contra a referência `tokens.c` da RFC §20.16 (`prob += bands_x[c]`, um mapeamento único). Próximas fatias: predição intra + DCT/WHT inversa (§12/§14), o loop filter (§15).
> **Fatia 5a do VP8 lossy LANDED 30/09 (Kof puro, todos os alvos):** o novo `libs/image/Vp8Residual.kf` — **desquantização + transforms inversas** (RFC 6386 §14). Os coeficientes quantizados de cada macrobloco são desquantizados com seus fatores de frame/segmento (`dc_qlookup`/`ac_qlookup` §14.1; Y2 DC `×2`, Y2 AC `×155/100` mínimo 8, UV DC limitado a 132), o bloco Y2 é invertido com a transformada inversa de Walsh-Hadamard (§14.3) e sua saída 4×4 torna-se o termo DC dos 16 subblocos luma, então cada subbloco luma/croma é invertido com a DCT inversa (§14.4, `20091`/`35468`). As duas tabelas de quant de 128 entradas são construídas **uma vez** num objeto `Vp8QuantTables` (mantendo-as vivas entre macroblocos — reconstruir o `listOf` a cada chamada disparava uma corrida de marca do GC do riscv64 que corrompia o resíduo; a forma de objeto é estável em todos os alvos). Prova: `Vp8ResidualE2ETest` **4/4** vs um oráculo RFC §14 independente sobre quatro arquivos lossy reais do libwebp (um `B_PRED` só-DC, um 2×2 todo `B_PRED`, um frame com segmentos/skip e um misturando cada categoria) — as somas com sinal/absolutas de luma/croma de cada macrobloco idênticas na JVM + Native x86-64 + riscv64(qemu) + Script. Próximo: predição intra (§12) + reconstrução (somar o resíduo aos pixels preditos), depois o loop filter (§15).
> **Fatia 5 do VP8 lossy LANDED 30/09 (Kof puro, todos os alvos):** o novo `libs/image/Vp8Reconstruct.kf` (`vp8Reconstruct`) transforma os coeficientes quantizados nos três planos **pré-loop-filter** de um key frame (RFC 6386 §12/§14) — o resíduo — dequantização por segmento §14.1, WHT inversa do bloco Y2 §14.3 e DCT 4×4 inversa §14.4, tudo em `libs/image/Vp8Residual.kf` (pousado como fatia 5a) — e então predição 16×16 luma (DC/V/H/TM) + os dez modos 4×4 `B_PRED` §12.3 (`libs/image/Vp8Predict4.kf`) + os modos 8×8 de croma §12.2 e a soma saturada predição+resíduo §14.5, em planos com borda. Os pixels acima/direita do `B_PRED` replicam os pixels do canto superior direito do macrobloco ao longo da linha (libwebp `top_right[BPS]=…`), o DC do bloco Y usa o atalho `(dc[0]+3)>>3` quando só o DC do Y2 é não-zero, e U/V preveem de forma independente a partir de seus próprios planos. **Oráculo = a própria libwebp**, decodificada com o loop filter desabilitado (`ffmpeg -skip_loop_filter all`): a fatia 5 para em §14.5, então os planos pré-filtro exatos da libwebp são o golden. Prova: `Vp8ReconstructE2ETest` **4/4** — quatro arquivos libwebp reais (`flat16` 16×16 V_PRED + Y2 só-DC, `diag32` 32×32 todo `B_PRED`, `skip64` 16×16 misto + macroblocos pulados, `cat64` modos mistos + todo resíduo) reproduzem a soma de amostras e um hash rolante por plano para Y, U e V na JVM + Native x86-64 + riscv64(qemu) + Script. Próxima fatia: o loop filter (§15), que completa o decoder VP8 byte-exato.
> **Fatia 6 do VP8 lossy LANDED 30/09 (Kof puro, todos os alvos):** o novo `libs/image/Vp8Filter.kf` (`vp8LoopFilter`) — o **filtro de desblocagem** in-loop (RFC 6386 §15), a última etapa da reconstrução do key frame. Por macrobloco deriva a força do nível do frame e do override de segmento (§15.4: `interior_limit`, `hev_threshold`, o limite de borda inter-macrobloco `+4`) e então filtra as bordas vertical esquerda, vertical interna, horizontal superior e horizontal interna nessa ordem: o ajuste de 4 taps (`DoFilter4`/`common_adjust` sem outer taps) nas bordas inter-subbloco, o de 6 taps (`DoFilter6`/`MBfilter`) nas bordas inter-macrobloco e o ajuste simples de 2 taps em alta variância de borda; o croma não é tocado pelo tipo de filtro simples. As bordas internas são puladas para um macrobloco que não é `B_PRED` nem carrega coeficientes (§15.1). **Oráculo = a própria libwebp** com o filtro padrão ligado (decode `ffmpeg` simples): `Vp8FilterE2ETest` **4/4** — as quatro fixtures da fatia 5 reproduzem a soma de amostras filtrada e o hash rolante de 24 bits da libwebp (`skip64`/`cat64` são as que o filtro muda), na JVM + Native x86-64 + riscv64(qemu) + Script; as duas fixtures pré-filtro ficam inalteradas, batendo com a libwebp. Com esta fatia o decoder VP8 de key frame em Kof puro reproduz a libwebp de ponta a ponta.
> **Fatia 7 do VP8 lossy LANDED 01/10 (Kof puro, todos os alvos):** o novo `libs/image/Vp8Raster.kf` (`vp8Raster`) roteia um WebP lossy para a visão `Raster` — a fronteira honesta é fechada. `decodeRaster(path)` agora despacha um chunk `VP8 ` por toda a cadeia de key frame (frame header → modos → coeficientes → desquantização + predição intra → `vp8LoopFilter`) e converte os planos YUV 4:2:0 filtrados em amostras RGB intercaladas (BT.601 limited range, croma nearest: `clip((298*(Y-16) + 409*(V-128) + 128) >> 8)`, o shift aritmético que casa com o fixed point da libwebp). Os planos são lidos pelos acessores `yAt/uAt/vAt` da própria reconstrução, tratando o stride com padding de macrobloco (`mbCols*16+1`) — o stride ingênuo `width+1` corrompe silenciosamente um frame cuja largura não é múltipla de 16. Prova: `Vp8RasterE2ETest` **4/4** — cinco arquivos libwebp reais (`flat16`, `diag32`, `skip64`, `cat64`, `odd20x28` 20×28) reproduzem o Y/U/V da libwebp (fatia 6) através da matriz limited-range documentada; a fórmula foi validada contra a saída RGB da libwebp em croma sólido; JVM + Native x86-64 + riscv64(qemu) + Script, RED-first (`PKG006 import 'image.Vp8Raster' not found`).
> **Fatia 8 do VP8 lossy LANDED 01/10 (Kof puro, todos os alvos):** `libs/image/Vp8Coeffs.kf` (`vp8TokenPartitions`) decodifica os key frames **multi-partição de token** (RFC 6386 §9.5) em vez de recusá-los — a primeira partição de dados carrega o tamanho de 3 bytes de cada uma das `n-1` primeiras partições e a linha de macrobloco `r` usa a partição `r % n` (2, 4 ou 8 partições). As fixtures são encodes reais do libvpx 1.14 (`VP8E_SET_TOKEN_PARTITIONS`) de um frame 16×128. Prova: `Vp8CoeffE2ETest` **4/4** (oito fixtures: `np2`/`np4`/`np8` reproduzem o golden de coeficientes da partição única), RED-first na JVM + Native x86-64 + riscv64(qemu) + Script.
> **Descritores de região do `kof.vision` LANDADOS 30/09 (Kof puro, todos os alvos):** `libs/vision/Regions.kf` (novo) adiciona `componentBoxes(labels, width)` (bounding box axis-aligned exata por rótulo de componente), `componentAreas(labels)` (contagem de pixels por rótulo) e `labelComponents(labels, width)` (a forma objeto `List<Component>`) — os descritores do §12 "regiões" / §14 "extração de contorno", sobre `componentLabels`. Prova: `VisionAnalysisE2ETest` **4/4** (o PGM 6×4 de dois blobs dá `areas=3,4`, `box1=0,0,1,1`, `box2=3,1,4,2`, `regions=2 r1=1@0,0 a3`) na JVM + Native x86-64 + riscv64(qemu) + Script.
> **Fatia 3i LANDED 30/09 (Kof puro, todos os alvos):** `libs/image/Vp8l.kf` — **grupos meta-Huffman** do VP8L (RFC 9649 §3.7.2.2): `prefix_bits = ReadBits(3)+2`; a imagem de entropia `ceil(w/2^bits) × ceil(h/2^bits)` é decodificada por entropia, os bytes red/green de cada pixel dão seu índice de grupo, lê-se um grupo de códigos de prefixo por valor distinto, e cada pixel seleciona seu grupo por `entropy[(y>>bits)*xw + (x>>bits)]` (a cópia LZ77 não é limitada aos blocos de grupo, espelhando o libwebp). Esta era a última recusa do VP8L — todo o caminho lossless do VP8L agora decodifica. Prova: `RasterDecodeE2ETest` 19/19 (novo stream 8x8 de dois grupos gerado pelo libwebp, validado byte a byte contra ele; JVM + Native x86-64 + riscv64(qemu) + Script); RED medido no decoder pré-fatia (`IMAGE: WebP meta-Huffman groups are not supported yet`).
> **Fatia 3f LANDED 29/09 (Kof puro, todos os alvos):** `libs/image/Vp8l.kf` — **cache de cor** do VP8L (RFC 9649 §3.6.2.3: `color_cache_code_bits` 1..11, slot `(0x1e35a7bd * argb) >> (32 - bits)`, todo pixel literal/copiado inserido na ordem do stream, `S >= 256+24` lê o cache). O alfabeto do código de prefixo verde agora é `256+24+cache_size`. Decodifica streams subtract-green/cache-de-cor/grupo-único. Transforms predictor/color/indexing e meta-Huffman ainda recusados com diagnóstico explícito `IMAGE:`. Prova: `RasterDecodeE2ETest` 19/19 (`webpVp8lDecodesOn*` incl. um stream 8x8 com cache de cor **gerado pelo libwebp e validado byte a byte contra ele**, JVM + Native x86-64 + riscv64(qemu) + Script); RED medido no decoder pré-fatia (`IMAGE: WebP color cache is not supported yet`).
> **Fatia 3e LANDED 29/09 (Kof puro, todos os alvos):** `libs/image/Vp8l.kf` — **Huffman normal (code-length codes)** + **referências LZ77** (bits extras de prefixo de length/distance + o mapa de distância §3.6.2.2.1). Decodifica o subconjunto subtract-green/sem-cache/grupo-único que o libwebp real emite. Predictor/color/indexing, cache de cor e meta-Huffman ainda recusados com diagnóstico explícito `IMAGE:`. Prova: `RasterDecodeE2ETest` 19/19 (`webpVp8lDecodesOn*` incl. um stream normal-Huffman+LZ77 feito à mão, validado contra libwebp, JVM + Native x86-64 + riscv64(qemu) + Script). Re-teste da face VP8L no native após os fixes §541/§543.
> **Fatia 3d LANDED 29/09 (Kof puro, todos os alvos):** `libs/image/Vp8l.kf` — loop de transforms do VP8L + inverso **SUBTRACT_GREEN**; predictor/color/indexing, cache, meta e LZ77 ainda recusados com diagnóstico explícito `IMAGE:`.
> **Fatia 3c LANDED 29/09 (Kof puro, todos os alvos):** `libs/image/Vp8l.kf` — núcleo VP8L (bit reader + Huffman simples + literais); transforms/cache/meta/LZ77 recusados com diagnóstico explícito `IMAGE:`. Prova: `RasterDecodeE2ETest` 19 run/0F (`webpVp8lDecodesOn*` na JVM + Native x86-64/riscv64 + Script).
> **Fatia 3b LANDED 29/09 (Kof puro, todos os alvos):** `libs/image/Gif.kf` decodifica o primeiro quadro GIF (LZW Kof, paleta global/local, entrelaçado) para RGB. Prova: `RasterDecodeE2ETest` 15 run/0F (`gifDecodesOn*` na JVM + Native x86-64/riscv64 + Script).
> **Fatia 2f LANDED 29/09 (Kof puro, paridade — sem gap):** `decodeRaster` decodifica **QOI** (todos os chunks: RGB/RGBA/diff/luma/run/index) em Kof, então um decode de formato comprimido entrega em todos os alvos. Decisão `D-IMAGE-SURFACE` (reusar `Raster`; Kof puro quando viável, imageio JVM só onde inviável) + TODO §34 registrados. Prova: `RasterDecodeE2ETest` 7/7 (golden QOI incl. chunk RUN; JVM + Native x86-64 + riscv64 + Script).
> **Fatia 2c LANDED 29/09:** `flipHorizontal`, `flipVertical` e `rotate90` (horário, dimensões trocam) sobre o `Raster` provisório. Prova: `RasterDecodeE2ETest` 7/7.
> **Fatia 2d LANDED 29/09:** `decodeRaster` também decodifica **BMP** 24/32-bit não comprimido (linhas BGR com padding de 4 bytes, bottom-up ou top-down, alpha descartado). Prova: `RasterDecodeE2ETest` 7/7.
> **Fatia 2e LANDED 29/09 (overlap de processamento):** `grayscale` (BT.601), `threshold(level)` e `boxBlur` (3x3, bordas clampadas) sobre o `Raster`. Prova: `RasterDecodeE2ETest` 7/7.

## Objetivo

Criar suporte nativo do Kof para **manipulação de imagens e visão computacional**, através de APIs próprias e idiomáticas, integradas à arquitetura da linguagem e da stdlib.

O projeto deve ser dividido conceitualmente em:

```text
kof.image
    ↓
manipulação e processamento de imagens

kof.vision
    ↓
visão computacional e análise visual
```

`kof.file` continua responsável por arquivos e formatos de armazenamento.

A responsabilidade de `kof.image` e `kof.vision` começa a partir dos dados de imagem já carregados.

---

# REGRA FUNDAMENTAL — KOF É KOF

Antes de implementar qualquer coisa:

1. Ler a gramática atual do Kof.
2. Ler exemplos reais do projeto.
3. Consultar APIs existentes da stdlib.
4. Consultar o sistema de tipos.
5. Consultar o modelo atual de arrays/buffers.
6. Consultar o modelo de memória.
7. Consultar os targets existentes.
8. Consultar o sistema de módulos.
9. Executar os testes atuais.

Não inventar sintaxe.

Kof utiliza `var`.

Não utilizar:

```text
let
const
variações de JavaScript
sintaxe de Python
sintaxe de Kotlin
```

Não transformar a API em uma DSL inspirada em outra linguagem.

Todos os exemplos deste documento são conceituais e devem ser adaptados à sintaxe real do Kof antes de serem implementados.

---

# 1. Arquitetura

A arquitetura desejada é:

```text
kof.file
    │
    │ bytes / stream / arquivo
    ▼
kof.image
    │
    ├── Image
    ├── Pixel
    ├── Color
    ├── ImageBuffer
    ├── ImageIO
    ├── Transform
    └── Processing
    │
    ▼
kof.vision
    │
    ├── Detection
    ├── Features
    ├── Segmentation
    ├── Tracking
    ├── Geometry
    ├── OCR
    └── ML integration
```

A estrutura final deve seguir a arquitetura existente do Kof.

Não criar módulos apenas para reproduzir essa árvore literalmente.

---

# 2. `kof.image`

`kof.image` deve fornecer uma abstração própria para imagens.

Conceitualmente:

```text
Image
├── width
├── height
├── format
├── channels
├── pixels
└── metadata
```

A representação interna deve ser eficiente e adequada aos targets.

---

# 3. Formatos de imagem

Suportar progressivamente formatos comuns:

```text
PNG
JPEG
WebP
GIF
BMP
TIFF
```

A primeira implementação não precisa suportar todos.

Priorizar os formatos mais utilizados e aqueles com bibliotecas maduras disponíveis.

---

# 4. Leitura e escrita

Integrar com `kof.file`.

Conceitualmente:

```text
arquivo → kof.file → bytes/stream → kof.image → Image
Image → kof.image → encoder → kof.file → arquivo
```

A API de imagem não deve precisar conhecer detalhes de filesystem.

---

# 5. Pixels

Fornecer acesso aos pixels quando necessário.

Suportar representações como:

```text
RGB
RGBA
Grayscale
```

Avaliar posteriormente:

```text
BGR
BGRA
YUV
HSV
Lab
```

Não criar dezenas de formatos de pixel na primeira versão.

---

# 6. Operações básicas

Implementar progressivamente:

* resize; crop; rotate; flip; transpose; scale; padding;
* composição; conversão de formato; conversão de canais; grayscale;
* ajuste de brilho; contraste; saturação; alpha; normalização.

A API deve favorecer operações composáveis.

---

# 7. Processamento de imagem

Adicionar operações clássicas de processamento:

```text
Blur
Gaussian Blur
Median Blur
Sharpen
Threshold
Adaptive Threshold
Edge Detection
Morphology
Convolution
Histogram
Equalization
```

Priorizar algoritmos clássicos e bem definidos.

Não adicionar algoritmos apenas para aumentar a quantidade de funcionalidades.

---

# 8. Geometria

Criar tipos próprios quando necessário:

```text
Point
Size
Rect
Circle
Line
Polygon
Contour
```

Essas estruturas devem ser reutilizáveis por `kof.image` e `kof.vision`.

---

# 9. Máscaras

Suportar máscaras de imagem.

Exemplo conceitual:

```text
Image + Mask → Operation → Image
```

Possibilitar:

* seleção;
* composição;
* recorte;
* operações matemáticas;
* processamento localizado.

---

# 10. Histogramas

Fornecer infraestrutura para histogramas.

Permitir:

* histogramas por canal;
* grayscale;
* distribuição;
* equalização;
* análise estatística.

Isso será útil tanto para processamento quanto para visão computacional.

---

# 11. `kof.vision`

`kof.vision` deve ser responsável por algoritmos de visão computacional.

A API deve trabalhar sobre `Image` e estruturas geométricas de `kof.image`.

---

# 12. Detecção

Suportar progressivamente:

* detecção de bordas;
* linhas;
* círculos;
* contornos;
* regiões;
* objetos;
* features.

A primeira implementação deve priorizar algoritmos clássicos.

---

# 13. Feature detection

Avaliar suporte para:

```text
Corners
Keypoints
Descriptors
Feature Matching
```

Algoritmos possíveis:

```text
Harris
FAST
ORB
SIFT
```

A escolha deve considerar:

* licença;
* performance;
* maturidade;
* necessidade real;
* disponibilidade por target.

Não implementar tudo simultaneamente.

---

# 14. Segmentação

Adicionar progressivamente:

* thresholding;
* binary segmentation;
* connected components;
* region growing;
* contour extraction;
* watershed quando apropriado.

A API deve produzir estruturas que possam ser reutilizadas por outras operações.

---

# 15. Tracking

Avaliar suporte para rastreamento de objetos/regiões em sequências de imagens.

Possíveis componentes:

```text
Tracker
Frame
Region
Object
Trajectory
```

Não implementar tracking antes de existir infraestrutura adequada para frames e processamento incremental.

---

# 16. Câmera

Criar uma abstração para captura de frames quando o target permitir.

Conceitualmente:

```text
Camera → Frame stream → Image → Vision pipeline
```

Deve suportar:

* abertura;
* fechamento;
* resolução;
* FPS;
* captura;
* streaming;
* controle de recursos.

Não bloquear desnecessariamente a thread principal.

Não criar loops infinitos ingênuos.

Target sem suporte adequado: documentar a limitação (gap `XXX00x`, R6) em vez de implementação fake.

---

# 17. Pipelines

Uma das funcionalidades importantes de `kof.vision` deve ser a composição de operações.

Conceitualmente:

```text
Camera → Frame → Resize → Grayscale → Blur → Edge Detection → Contour Detection → Result
```

O modelo deve permitir pipelines eficientes sem criar cópias desnecessárias de imagens.

Avaliar:

* buffers reutilizáveis;
* operações in-place quando seguras;
* lazy processing;
* fusão de operações;
* streaming.

Não implementar otimizações complexas antes de possuir benchmarks.

---

# 18. OCR

Avaliar integração com OCR.

A primeira versão não precisa implementar um OCR próprio.

Pode utilizar engine externa madura, isolada atrás de uma API Kof.

Conceitualmente:

```text
Image → OCR → Text
```

Possibilidades futuras:

* bounding boxes;
* confidence;
* linhas;
* palavras;
* caracteres;
* idioma.

---

# 19. QR Code

`kofqrcode` deve permanecer um módulo específico.

Porém, deve existir integração natural com:

```text
kof.image
```

e futuramente:

```text
kof.vision
```

Arquitetura:

```text
kof.image → Image → kofqrcode → QR Result
```

Não duplicar decoder/encoder de imagem dentro do `kofqrcode`.

---

# 20. Machine Learning

`kof.vision` deve possuir espaço para integração futura com modelos de ML.

Não criar um framework de ML inteiro dentro desse módulo.

A responsabilidade inicial pode ser:

```text
Image → Tensor/Buffer → Model → Inference → Detection/Classification/Segmentation
```

Avaliar posteriormente integração com runtimes como:

* ONNX Runtime;
* TensorFlow Lite;
* outros runtimes adequados.

A API pública deve permanecer independente do runtime utilizado.

---

# 21. Detecção de objetos

Futuramente:

```text
Image → Object Detector → Detection[]
```

Cada detecção pode possuir conceitualmente:

```text
class
confidence
boundingBox
```

O modelo de dados deve ser simples e reutilizável.

---

# 22. Classificação

Suportar futuramente:

```text
Image → Classifier → Classification[]
```

Com:

* classe;
* confiança;
* metadata opcional.

---

# 23. Segmentação semântica

Planejar suporte futuro para:

```text
Image → Segmentation Model → Mask
```

Reutilizando as abstrações de máscara já existentes.

---

# 24. Performance

Visão computacional pode ser extremamente intensiva.

Projetar considerando:

* SIMD;
* buffers reutilizáveis;
* memória contígua;
* operações in-place;
* zero-copy quando possível;
* processamento paralelo;
* GPU quando disponível;
* aceleradores específicos;
* WASM SIMD;
* Native SIMD.

Não sacrificar a API limpa em nome de micro-otimizações.

---

# 25. Targets

Avaliar progressivamente:

```text
JVM
Native
JS
WASM
```

### JVM

Pode utilizar bibliotecas maduras quando necessário.

### Native

Priorizar performance e acesso eficiente à memória.

### JS

Suportar operações compatíveis com browser.

### WASM

Explorar:

* WASM SIMD;
* processamento local;
* pipelines de imagem;
* inferência quando houver runtime adequado.

Não prometer paridade artificial entre targets.

Documentar claramente o suporte de cada API.

---

# 26. Segurança

Considerar:

* imagens malformadas;
* arquivos gigantes;
* decompression bombs;
* overflow de dimensões;
* buffers inválidos;
* formatos corrompidos;
* consumo excessivo de memória;
* modelos não confiáveis;
* entrada de câmera;
* processamento de dados externos.

Não confiar em imagens recebidas de fontes externas.

---

# 27. Dependências

Não implementar codecs ou algoritmos complexos do zero quando houver bibliotecas maduras e adequadas.

Porém:

**a dependência não deve vazar para a API pública do Kof.**

Por exemplo, o usuário não deve precisar conhecer uma classe específica de uma biblioteca externa para trabalhar com `Image`.

A biblioteca externa é detalhe de implementação.

Avaliar:

* licença;
* maturidade;
* segurança;
* manutenção;
* performance;
* tamanho;
* compatibilidade com targets.

---

# 28. Testes

Criar testes para:

## Image

* abrir; salvar; resize; crop; rotate; grayscale; conversão; canais; pixels; metadata.

## Processing

* blur; threshold; edge detection; morphology; histogram.

## Vision

* contours; lines; circles; features; segmentation.

## Camera

* abertura; captura; lifecycle; encerramento.

## OCR

* reconhecimento; bounding boxes; erros.

## QR Code

* integração com `kof.image`; leitura; geração.

---

# 29. Testes de integração

Criar pipelines reais.

Exemplos conceituais:

```text
Image file → kof.file → kof.image → grayscale → threshold → kof.vision → contours → result
Camera → Image → Vision → Detection
Image → QR Code Reader → Text
```

---

# 30. Benchmarks

Adicionar benchmarks para operações críticas:

* decode; encode; resize; grayscale; blur; edge detection; convolution; segmentation; feature detection.

Comparar:

* tamanho da imagem;
* tempo;
* memória;
* throughput.

Não fazer afirmações de performance sem benchmark.

---

# 31. Implementação incremental

Não tentar criar toda a stack de visão computacional de uma vez.

### Fase 1

```text
kof.image
├── Image
├── Pixel
├── Color
├── ImageIO
└── resize/crop/rotate
```

### Fase 2

```text
processing
├── grayscale
├── blur
├── threshold
├── histogram
└── edges
```

### Fase 3

```text
kof.vision
├── contours
├── lines
├── circles
├── geometry
└── segmentation
```

### Fase 4

```text
camera
tracking
features
OCR
```

### Fase 5

```text
ML
object detection
classification
semantic segmentation
GPU acceleration
```

A ordem pode mudar conforme a arquitetura e os targets existentes.

---

# 32. Critérios de arquitetura

Não transformar `kof.image` em:

* um clone de OpenCV;
* um framework de ML;
* uma biblioteca gráfica;
* um editor de imagens;
* um wrapper gigante de bibliotecas externas.

`kof.image` deve cuidar de **imagens**.

`kof.vision` deve cuidar de **visão computacional**.

Runtimes externos devem permanecer detalhes de implementação.

---

---

# 34. TODO — o que falta (plano de implementação, 29/09)

Decisão **`D-IMAGE-SURFACE`** (mantenedora 29/09): a superfície de valor
**reusa `Raster`** (sem novos tipos `Image`/`Pixel`/`Color`); os codecs são
**Kof puro sempre que viável** (paridade total, zero gaps) e só caem para o
interop **imageio** no JVM onde um decoder Kof puro é tecnicamente inviável.
Nenhum gap é adicionado "só por adicionar" — ele existe apenas onde a
capacidade realmente não existe no alvo.

**Feito (Kof puro, todos os alvos):**
- metadados de 17 formatos (`Image.kf`);
- decode cru: PNM `P5`/`P6`, farbfeld, BMP 24/32-bit, **QOI** (todos os chunks);
- ops: `cropRaster`, `resizeNearest`, `flipHorizontal`/`flipVertical`,
  `rotate90`, `grayscale`, `threshold`, `boxBlur`;
- encode: `encodeRaster`/`writeRaster` for PNM/farbfeld/BMP/QOI;
- vision: `histogram`/`normalizedHistogram`/`otsuLevel`/`otsuBinarize`, `equalizationLut`/`equalizeRaster`, `sobelMagnitude`, `componentLabels`/`componentCount`, `erode`/`dilate`/`openRaster`/`closeRaster`.

**Falta — em ordem de custo:**

1. **Decode PNG (Kof puro) — LANDED 29/09 na JVM/riscv64/Script/x86-64 (`known-bugs` §541, corrigido 29/09: o heap x86 devolvia memória reusada suja, agora zerada).**
   - Arquivos: `libs/image/Png.kf` (novo), `libs/image/Raster.kf` (dispatch
     `fmt == "PNG"`).
   - Trabalho: parse do `IHDR` (color type 0/2/3/4/6, bit depth 8), concatenar
     `IDAT`, **inflate zlib** (DEFLATE: stored/fixed/dynamic Huffman) em Kof
     puro, filtros de scanline 0–4 (None/Sub/Up/Average/Paeth), de-paleta
     (`PLTE`) e expansão para os canais do `Raster`.
   - Prova: `RasterDecodeE2ETest#pngDecodesOnJvm` (bytes PNG conhecidos → golden de amostras) na
     JVM + Native x86-64 + riscv64 + Script; JS `IOJS001` (a biblioteca ainda
     usa `readRange`). Sem gap novo: o decoder é independente de alvo.
   - Risco: correção do inflate; mitigar com golden de bloco fixo/dinâmico e a
     checagem Adler-32 do zlib (ignorar o rabo, não pode crashar).
2. **Decode JPEG (inviável em Kof puro → interop imageio no JVM) — LANDED 29/09.**
   - Builtin de plataforma `kof.image` `image.decode(path): Int[]` (layout
     `[w,h,samples…]`) + runtime JVM `JvmImageRuntime` via
     `javax.imageio.ImageIO`; `libs/image/Jpeg.kf` o envolve como
     `decodeJpegRaster(path): Raster` (JPEG, RGB/RGBA); o wrapper liga o
     resultado a um local `Int[]` explícito (`var` na chamada inferia elemento
     `Unknown[]` no emit — medido, inócuo quando tipado). Outros alvos: gap
     honesto **`IMG001`** em compile-time no lowering do namespace
     (`ExpressionMethodCallLowerer`), nunca fallback silencioso. Como o
     builtin só existe no JVM, importar `image.Jpeg` é o compromisso
     explícito JVM-only; os formatos Kof puro sem gap em `Raster.kf` ficam
     intactos. Registrado no ledger stdlib (`platform`, `experimental`) e
     travado na matriz de paridade.
   - Prova: `RasterDecodeE2ETest#jpegDecodesOnJvmViaInterop` (golden JVM ==
     fixture decodificada pelo ImageIO) + `#jpegOnNonJvmIsImg001` (JS recusa
     com `IMG001`) + `DomainGapCodesTest#imageDecodeOnJsIsImg001`.
3. **GIF — LANDED 29/09 (Kof puro, todos os alvos).** `libs/image/Gif.kf` decodifica o primeiro quadro com um LZW Kof (largura variável 2–12, KwKwK), paleta global/local e linhas entrelaçadas, saída RGB.
   **WebP VP8L — fatias A–I LANDED 30/09 (Kof puro, todos os alvos):** `libs/image/Vp8l.kf` + `libs/image/Vp8lTransforms.kf` decodificam todo o caminho lossless do VP8L: **Huffman simples e normal (code-length)**, **referências LZ77**, o **cache de cor**, os **transforms inversos predictor + color** (14 modos de predictor e o delta de cor §3.5.2, aplicados em ordem reversa), o transform **COLOR_INDEXING** (§3.5.4) e os **grupos meta-Huffman** (§3.7.2.2; RFC 9649 §3.5/§3.6.2.1/§3.6.2.2/§3.7). Os fixtures são validados contra libwebp (o stream normal-Huffman+LZ77 feito à mão, um stream 8x8 com cache de cor, um stream 8x8 predictor+color, um stream 8x8 de 8 cores com indexing e um stream 8x8 de dois grupos meta-Huffman, todos gerados pelo libwebp e casados byte a byte pelo PIL); a face VP8L no native foi re-testada verde após os fixes §541/§543. Nenhuma maquinaria do VP8L segue recusada. Próximo: WebP lossy (`VP8 `) e AVIF seguem pendentes (interop/gap).
4. **Encode/write — LANDED 30/09 (Kof puro, todos os alvos).** `libs/image/Encode.kf`
   adiciona `encodeRaster(r, format): Int[]` e `writeRaster(path, r, format): Bool`
   para **PNM `P5`/`P6`**, **farbfeld**, **BMP** 24-bit e **QOI** (encoder
   completo: RUN/INDEX/DIFF/LUMA/RGB/RGBA + marcador final), então um raster
   pode ser escrito de volta em todo alvo. Prova: `RasterEncodeE2ETest` **4/4** —
   um round-trip decode → encode → decode é byte-idêntico na JVM + Native
   x86-64 + riscv64(qemu) + Script, o BMP emitido é lido independentemente pelo
   `javax.imageio`, e o QOI/PNM/farbfeld re-decodificado casa com as amostras
   de origem. Sem gap novo: Kof puro, paridade total (JS herda `IOJS001` via a
   escrita do `kof.io`).
5. **Rasters maiores — LANDED 30/09 (pós-`§540`/`§542`).** O teto que era
   mantido deliberadamente em 16384 amostras até os fixes de alocação native
   pousarem sobe para **262144** (um array `Int` de 1 MiB, que cabe na arena
   cross-native de 16 MiB do `known-bugs` §540 e na arena contígua x86-64 do
   §542). Prova: `RasterDecodeE2ETest#largeRasterAboveOldCapDecodes` (JVM) e
   `#largeRasterAboveOldCapDecodesOnNativeRiscv64` (riscv64/qemu) decodificam um
   P6 200×200 (120 000 amostras, muito além do cap antigo de 16384 e da antiga
   arena cross de 256 KiB) mais o `#oversizedRasterThrows` reajustado (400×400).
   O caminho VP8L agora compartilha o mesmo guard (`libs/image/Vp8l.kf` chama
   `guardRaster(pixels * 4)` em vez do próprio teto de 16384 px), verificado na
   JVM e no Native x86-64 com um WebP lossless 160×120 gerado no libwebp
   (`#largeWebpAboveOldPixelCapDecodesOnJvm`/`...OnNativeX86`); o Native riscv64
   aborta na mesma fixture e fica em quarentena pelo novo `known-bugs` **§544**
   (dona = lane native/GC, issue #700).

6. **Fatia 1 do `kof.vision` — LANDED 30/09 (Kof puro, todos os alvos).** Novo
   pacote `libs/vision/` abre a frente de visão: `histogram(r): Int[256]`
   (bins de luminância BT.601, mesma regra do `image.grayscale`),
   `normalizedHistogram(r): Double[256]` (bins como probabilidades) e
   `otsuLevel(r): Int` + `otsuBinarize(r): Raster` (limiar global ótimo de Otsu
   1979 e seu raster preto/branco, alpha preservado — o primeiro primitivo de
   segmentação do §14). Construído sobre o `image.Raster` compartilhado
   (`D-IMAGE-SURFACE`); determinístico, O(256) após o histograma, sem interop,
   sem ML. Prova: `VisionAnalysisE2ETest` **4/4** — um PGM bimodal construído à
   mão (10×30, 6×220) dá `hist=6,10`, `norm=375`, `otsu=30`, `bw=0,0,255`
   byte-idêntico na JVM + Native x86-64 + riscv64(qemu) + Script. As próximas
   fatias de visão (bordas/contornos, §12) são aditivas.

7. **`kof.vision` fatia 2a — bordas de Sobel — LANDED 30/09 (Kof puro, todos os alvos).**
   `libs/vision/Edges.kf` adiciona `sobelMagnitude(r): Raster` (um raster
   `"SOBEL"` de um canal) e `sobelValues(r): Double[]` — a magnitude clássica do
   gradiente de Sobel sobre a luminância BT.601, bordas 0. A raiz quadrada é uma
   iteração de Newton determinística (sem libm), então o resultado é
   byte-idêntico em todo alvo. O primeiro primitivo de detecção do §12. Prova:
   `VisionAnalysisE2ETest` **4/4** — um PGM 5×5 com um 255 interior e um PGM 5×5
   de "cruz" dão as magnitudes exatas (`edge=98`, centro `0`, bordas `0`) na JVM
   + Native x86-64 + riscv64(qemu) + Script.

8. **`kof.vision` fatia 2b — componentes conectados — LANDED 30/09 (Kof puro,
   todos os alvos).** `libs/vision/Components.kf` adiciona
   `componentLabels(r): Int[]` (rotulagem 4-conectada da luminância não-zero,
   0 = fundo, flood fill iterativo com pilha LIFO — sem recursão) e
   `componentCount(labels): Int` (o "connected components" do §14). Prova:
   `VisionAnalysisE2ETest` **4/4** — um PGM 6×4 com dois blobs disjuntos dá
   `comp=2 a=1 b=2 bg=0` na JVM + Native x86-64 + riscv64(qemu) + Script.

9. **Fatia de processamento do `kof.vision` — morfologia — LANDED 30/09 (Kof
   puro, todos os alvos).** `libs/vision/Morphology.kf` adiciona `erode(r)`/
   `dilate(r)` (elemento quadrado 3×3, mínimo/máximo sobre cada canal, alfa
   preservado, bordas recortadas), mais as composições `openRaster(r)`
   (erode→dilate) e `closeRaster(r)` (dilate→erode) — §Processing do plano.
   Prova: `VisionAnalysisE2ETest` **4/4** — um PGM 5×5 com um 255 isolado dá
   `erode=0 dilate=255,255`, `open=0 close=255` na JVM + Native x86-64 +
   riscv64(qemu) + Script.

10. **Fatia de processamento do `kof.vision` — equalização de histograma —
    LANDED 30/09 (Kof puro, todos os alvos).** `libs/vision/Histogram.kf`
    adiciona `equalizationLut(r): Int[256]` (o remapeamento pela função de
    distribuição acumulada) e `equalizeRaster(r): Raster` (aplica-o a cada canal
    de cor, alfa preservado; um raster uniforme mapeia para tudo-0). Prova:
    `VisionAnalysisE2ETest` **4/4** — um PGM de 16 pixels de baixo contraste
    (60/200) estica para `eqLow=0,255`, `out=0,255`, e uma rampa de 64 pixels
    com seis níveis mapeia para `eqSix=47,94,141,188`, na JVM + Native x86-64 +
    riscv64(qemu) + Script.

11. **Fatia 1 do VP8 lossy — decoder booleano de range — LANDED 30/09 (Kof
    puro, todos os alvos).** `libs/image/Vp8.kf` adiciona `Vp8Bool`, o decoder de
    entropia compartilhado por toda partição VP8 (RFC 6386 §7.3): `bit(prob)`
    (um bool a `prob/256`) e `literal(n)` (um valor de `n` bits a 1/2). Toda a
    aritmética fica em 17 bits, então um `Int` de 32 bits é exato em todo
    backend. Prova: `Vp8BoolE2ETest` **4/4** — um **encoder independente da
    RFC §7.3** (Python offline) escreve 64 bools com seed fixa sobre um padrão de
    8 probabilidades numa partição de 20 bytes, e o decoder Kof reproduz a
    sequência exata (`vp8bool=1101…0010`) byte a byte na JVM + Native x86-64 +
    riscv64(qemu) + Script (sem mudança no compilador). Próximas fatias:
    container RIFF/`VP8 ` + frame header, depois modos/coeficientes por
    macrobloco, predição intra + DCT inversa, e o loop filter.

12. **Fatia 2 do VP8 lossy — container RIFF/`VP8 ` + frame header — LANDED
    30/09 (Kof puro, todos os alvos).** `libs/image/Vp8Frame.kf` (novo) percorre
    o envelope RIFF/`WEBP`, extrai o chunk `VP8 ` e parseia o chunk
    descomprimido (§9.1: frame tag, start code de key-frame, dimensões de 14
    bits) mais todo o frame header (§9.2–§9.11): color space/clamp,
    segmentação, tipo/nível/sharpness do loop filter e grupos de delta por
    macrobloco, número de partições de token, os seis índices de dequant,
    `refresh_entropy`, a tabela completa de probabilidades de coeficiente
    `[4][8][3][11]` (defaults + updates por frame) e
    `mb_no_skip_coeff`/`prob_skip_false`. `libs/image/Vp8Probs.kf` (novo) carrega
    as duas tabelas da RFC (updates §13.4, defaults §13.5); `Vp8Bool` ganhou
    `signedOrZero(n)` (`bool_maybe_get_int` da RFC) e `bytePosition()`. Modelado
    como **classe de construtor de um argumento** em vez de record largo: o
    backend cross riscv64/aarch64 corrompia chamadas com ≥9 argumentos (medido,
    catalogado como `known-bugs` **§546** / issue **#703**; **CORRIGIDO 30/09** —
    o parser foi mantido dentro da aridade verificada na época, e um record largo
    pode ser revisitado agora), então o parser seguiu verde em todos os alvos. Prova:
    `Vp8FrameE2ETest` **4/4** contra um **parser RFC §19.2 independente**
    (Python offline) sobre um arquivo lossy 8×8 real do libwebp — idêntico
    `w=8,h=8,lf=3,qi=9,parts=1,pos=13,sum=174173` (a soma da tabela de 1056
    entradas, incluindo os 3 updates por frame) na JVM + Native x86-64 +
    riscv64(qemu) + Script (sem mudança no compilador). Próximas fatias:
    modos/coeficientes por macrobloco (§11/§13), predição intra + DCT/WHT
    inversa (§12/§14), o loop filter (§15).

13. **Fatia 3 do VP8 lossy — registros de predição por macrobloco de key-frame —
    LANDADA 30/09 (Kof puro, todos os alvos).** `libs/image/Vp8Frame.kf` parseia
    os registros de macrobloco da primeira partição de dados (RFC 6386
    §10/§11): o segment id por macrobloco quando `update_mb_segmentation_map`
    está setado (árvore de 3 probabilidades), o `mb_skip_coeff` quando
    `mb_no_skip_coeff` está setado, o modo luma 16x16 (`kf_ymode_tree`) e, sendo
    `B_PRED`, os 16 modos de subbloco luma usando a `kf_bmode_prob` 10×10×9
    dependente de contexto (contexto dos subblocos acima e à esquerda, incluindo
    os macroblocos vizinhos, com o modo 16x16 mapeado a um modo de subbloco
    constante), e então o modo de croma (`uv_mode_tree`). O novo
    `libs/image/Vp8ModeProbs.kf` carrega `kf_ymode_prob`, `kf_uv_mode_prob` e
    `kf_bmode_prob`; `vp8Tree` percorre qualquer árvore bool da RFC. Prova:
    `Vp8ModeE2ETest` **4/4** contra um **parser RFC §7.3/§10/§11 independente**
    (Python offline) sobre três arquivos lossy reais do libwebp — um frame 4×4
    com mapa de segmentos e `mb_no_skip_coeff=1` (`seg64`), um frame 2×2 todo
    `B_PRED` com os subblocos codificados por contexto (`diag32`) e um frame 4×4
    misturando todos os modos luma/croma (`mix`) — reproduzindo cada segment id,
    flag de skip, modo luma, modo de subbloco e modo de croma na JVM + Native
    x86-64 + riscv64(qemu) + Script (sem mudança no compilador). Próximas fatias:
    predição intra + DCT/WHT inversa (§12/§14), o loop filter (§15).

14. **Fatia 4 do VP8 lossy — decodificador de coeficientes DCT/WHT — LANDADA
    30/09 (Kof puro, todos os alvos).** O novo `libs/image/Vp8Coeffs.kf` percorre
    a(s) partição(ões) de token e decodifica a resíduo quantizado de cada
    macrobloco que não é `mb_skip_coeff` (RFC 6386 §13.2/§13.3). A árvore de
    blocos é lida com os três nós de valor codificados por contexto e os seis
    tokens de categoria, cada categoria com suas probabilidades fixas de bits
    extras (`Pcat1..Pcat6`) e um bit de sinal ao final; o resultado vai para
    `coeffs` em `mb*400 + block*16 + zigzag`, com a posição de end-of-block
    guardada por bloco. Os preditores de não-zero acima/à esquerda são indexados
    por `left_context_index`/`above_context_index` (o preditor Y2 mantém o
    macrobloco mais recente que tem bloco Y2) e são zerados num macrobloco
    skipado. `vp8TokenPartition` constrói o decoder bool da única partição de
    token; um frame que divida o resíduo em mais de uma partição é recusado com
    diagnóstico explícito (os encoders libwebp disponíveis sempre emitem uma) em
    vez de um decode silenciosamente errado. Prova: `Vp8CoeffE2ETest` **4/4**
    contra um **decodificador de coeficientes RFC §7.3/§13 independente** (Python
    offline) sobre quatro arquivos lossy reais do libwebp — um 16×16 só-DC, um
    32×32 todo `B_PRED`, um 64×64 com `mb_no_skip_coeff=1` e muitos macroblocos
    skipados, e um 64×64 misturando todas as categorias de coeficiente —
    reproduzindo a contagem de blocos não-vazios e as somas de coeficientes com
    sinal/absolutas de cada macrobloco na JVM + Native x86-64 + riscv64(qemu) +
    Script (sem mudança no compilador). O lookup de banda por posição do oráculo
    foi conferido contra a referência `tokens.c` da RFC §20.16 (`prob +=
    bands_x[c]`, um mapeamento único; o decoder Kof o aplica uma vez). Próxima
    fatia: predição intra + DCT/WHT inversa (§12/§14), depois o loop filter (§15).

15. **Fatia 5a do VP8 lossy — desquantização + transforms inversas — LANDADA
    30/09 (Kof puro, todos os alvos).** O novo `libs/image/Vp8Residual.kf`
    converte o resíduo quantizado da fatia 4 no resíduo sem predição de cada
    macrobloco (RFC 6386 §14). Para cada macrobloco deriva os seis fatores de
    desquantização do seu quantizador de frame e de segmento (§14.1): as tabelas
    `dc_qlookup`/`ac_qlookup` alimentam Y DC/AC, Y2 DC (`×2`) e AC (`×155/100`,
    mínimo 8), e o croma DC/AC (DC limitado a 132). O bloco Y2 é invertido com a
    transformada inversa de Walsh-Hadamard (§14.3) e sua saída 4×4 torna-se o
    coeficiente DC de cada um dos 16 subblocos luma; cada subbloco luma e croma é
    então invertido com a DCT inversa (§14.4, ponto fixo `20091`/`35468`). Os
    resultados são guardados como `y` (16×16 por macrobloco) e `u`/`v` (8×8),
    com macroblocos `B_PRED` tomando o DC direto do stream de coeficientes (sem
    Y2). As duas tabelas de quant de 128 entradas são construídas **uma vez**
    dentro de um objeto `Vp8QuantTables` compartilhado pelo decode: reconstruir
    o `listOf` a cada chamada de desquantização deixava um temporário
    profundamente vivo que o coletor do riscv64 marcava em corrida e corrompia
    os arrays de trabalho (um achado de GC nativo, contornado estruturalmente em
    Kof puro). Prova: `Vp8ResidualE2ETest` **4/4** contra um **oráculo RFC §14
    independente** (Python offline) sobre quatro arquivos lossy reais do libwebp
    — um frame `B_PRED` só-DC, um 32×32 todo `B_PRED`, um 64×64 com
    segmentos/skip e um 64×64 misturando cada categoria de coeficiente —
    reproduzindo as somas com sinal e absolutas dos planos de resíduo luma e
    croma de cada macrobloco na JVM + Native x86-64 + riscv64(qemu) + Script (sem
    mudança no compilador). Próxima fatia: predição intra (§12) + reconstrução
    (somar o resíduo aos pixels preditos), depois o loop filter (§15).
16. **Fatia 2c do `kof.vision` — regiões de componentes conexos — LANDADA 30/09
    (Kof puro, todos os alvos).** `libs/vision/Regions.kf` adiciona os
    descritores reutilizáveis de região do §12 ("regiões") / §14 ("extração de
    contorno"): `componentBoxes(labels, width): List<ComponentBox>` (uma
    bounding box axis-aligned exata por rótulo de componente, índice 0 =
    fundo), `componentAreas(labels): Int[]` (contagem de pixels por rótulo) e
    `labelComponents(labels, width): List<Component>` (a forma objeto — um
    `Component(label, box, area)` por região, na ordem dos rótulos). Construído
    sobre `componentLabels`; determinístico, sem interop, todos os alvos.
    Prova: `VisionAnalysisE2ETest` **4/4** — o PGM 6×4 de dois blobs dá
    `areas=3,4`, `box1=0,0,1,1`, `box2=3,1,4,2` e `regions=2 r1=1@0,0 a3`
    na JVM + Native x86-64 + riscv64(qemu) + Script (mesma classe estende o
    golden de histograma/Otsu/Sobel/componentes/morfologia).

17. **Fatia 5 do VP8 lossy — predição intra + DCT/WHT inversa — LANDADA 30/09
    (Kof puro, todos os alvos).** `libs/image/Vp8Reconstruct.kf` (`vp8Reconstruct`)
    reconstrói os três planos **pré-loop-filter** de um key frame a partir dos
    coeficientes da fatia 4 (RFC 6386 §12/§14). Dividido por responsabilidade: apoia-se no resíduo da fatia 5a
    `libs/image/Vp8Residual.kf` (dequantização §14.1 + WHT/DCT inversa
    §14.3/§14.4) e acrescenta
    clamp/clip), `libs/image/Vp8Predict4.kf` (os dez modos `B_PRED` 4×4, §12.3) e
    o driver de reconstrução (16×16 DC/V/H/TM §12.3, croma 8×8 §12.2, soma
    predição+resíduo §14.5). As amostras acima/direita do `B_PRED` replicam os
    pixels do canto superior direito do macrobloco ao longo da linha (libwebp
    `top_right[BPS] = top_right[2*BPS] = …`), as células `B_PRED` leem a linha
    acima do subbloco / a coluna à sua esquerda no frame buffer, o DC do bloco Y
    é a saída da WHT inversa (ou o atalho `(dc[0]+3)>>3` quando só o DC do Y2 é
    não-zero), e U/V preveem de forma independente a partir de seus próprios
    planos. **Oráculo = a própria libwebp**, decodificada com o loop filter
    desabilitado (`ffmpeg -skip_loop_filter all`): a fatia 5 para em §14.5, então
    os planos pré-filtro exatos da libwebp são o golden. Prova:
    `Vp8ReconstructE2ETest` **4/4** — quatro arquivos libwebp reais (`flat16`
    16×16 V_PRED + Y2 só-DC, `diag32` 32×32 todo `B_PRED`, `skip64` 16×16 misto
    + macroblocos pulados, `cat64` modos mistos + todo resíduo) casam a soma de
    amostras e um hash rolante de 24 bits por plano para Y, U e V na JVM + Native
    x86-64 + riscv64(qemu) + Script; RED-first (`PKG006 import
    'image.Vp8Reconstruct' not found`) na árvore pré-fatia. Próxima fatia: o loop
    filter (§15), que completa o decoder VP8 byte-exato.

18. **Fatia 6 do VP8 lossy — loop filter — LANDADA 30/09 (Kof puro, todos os
    alvos).** `libs/image/Vp8Filter.kf` (`vp8LoopFilter`) aplica o **filtro de
    desblocagem** in-loop (RFC 6386 §15) aos planos reconstruídos, a etapa final
    do key frame. Por macrobloco deriva a força do `loop_filter_level` do frame
    mais o override de segmento, absoluto ou delta (§15.4, `interior_limit`, a
    escada de `hev_threshold` de key frame e o limite de borda inter-macrobloco
    `+4`), e então filtra as bordas vertical esquerda, vertical interna,
    horizontal superior e horizontal interna nessa ordem: o `DoFilter4` de 4
    taps (ajuste simples sem outer taps, mais os dois pixels internos movidos
    pela metade) nas bordas inter-subbloco, o `DoFilter6` (`MBfilter`) de 6 taps
    nas bordas inter-macrobloco e o `common_adjust` de 2 taps em alta variância
    de borda; o tipo de filtro simples só toca luma, e as bordas internas são
    puladas para um macrobloco que não é `B_PRED` nem carrega coeficientes
    (§15.1). **Oráculo = a própria libwebp** com o filtro padrão ligado (decode
    `ffmpeg` simples). Prova: `Vp8FilterE2ETest` **4/4** — as quatro fixtures da
    fatia 5 reproduzem a soma de amostras filtrada por plano e o hash rolante de
    24 bits na JVM + Native x86-64 + riscv64(qemu) + Script (`flat16`/`diag32`
    ficam inalteradas pelo filtro, batendo com a libwebp; `skip64`/`cat64`
    mudam), RED-first (`PKG006 import 'image.Vp8Filter' not found`). Isto
    completa o decoder VP8 de key frame em Kof puro de ponta a ponta contra a
    libwebp.

19. **Fatia 7 do VP8 lossy — rota `decodeRaster` — LANDADA 01/10 (Kof puro,
    todos os alvos).** O novo `libs/image/Vp8Raster.kf` (`vp8Raster`) fecha a
    fronteira honesta: `decodeRaster(path)` despacha um chunk `VP8 ` por toda a
    cadeia de key frame e devolve um `Raster` limitado (RGB, 3 canais) — frame
    header (`vp8FrameFromWebp`) → `Vp8Coeffs` → `Vp8Reconstruct` →
    `vp8LoopFilter` → YUV 4:2:0 para RGB (BT.601 limited range, croma nearest).
    A conversão lê `yAt/uAt/vAt` da reconstrução (stride com padding de
    macrobloco), então uma largura que não é múltipla de 16 fica correta. Prova:
    `Vp8RasterE2ETest` **4/4** — cinco arquivos libwebp reais incluindo um frame
    parcial 20×28, golden do próprio Y/U/V da libwebp mais a matriz
    limited-range documentada validada contra o RGB da libwebp em croma sólido,
    na JVM + Native x86-64 + riscv64(qemu) + Script.

20. **Fatia 8 do VP8 lossy — decodificação multi-partição de token — LANDADA
    01/10 (Kof puro, todos os alvos).** `libs/image/Vp8Coeffs.kf`
    (`vp8TokenPartitions`) passa a decodificar frames cuja região de coeficientes
    está dividida em 2, 4 ou 8 partições de token (RFC 6386 §9.5), no lugar da
    recusa `IMAGE: VP8 multiple token partitions are not supported yet`. Quando o
    frame header declara mais de uma partição de token, a primeira partição de
    dados carrega os tamanhos das `n-1` primeiras partições como 3 bytes
    little-endian cada (a última pega o restante); a linha de macrobloco `r` é
    lida com a partição `r % n`. O caminho de partição única é inalterado (os
    coeficientes seguem a primeira partição diretamente). O decoder já passava o
    decoder de entropia por macrobloco, então só a seleção de partição e a tabela
    de offsets foram adicionadas. As fixtures são encodes reais do libvpx 1.14
    (`VP8E_SET_TOKEN_PARTITIONS`) de um frame 16×128 (8 linhas de macrobloco) em
    2, 4 e 8 partições — o libvpx é o único encoder disponível que emite mais de
    uma partição (o libwebp e o muxer WebP do ffmpeg sempre emitem uma) — e cada
    uma decodifica exatamente para o golden da partição única. Prova:
    `Vp8CoeffE2ETest` **4/4** (agora oito fixtures: as cinco anteriores mais
    `np2`/`np4`/`np8`), RED-first na árvore pré-fatia (`IMAGE: VP8 multiple token
    partitions are not supported yet`, 4/4 vermelho), na JVM + Native x86-64 +
    riscv64(qemu) + Script.

21. **Interlace Adam7 do PNG — LANDADA 01/10 (Kof puro, todos os alvos).**
    O `libs/image/Png.kf` passa a desinterlaçar os sete passes Adam7
    (especificação PNG §9), no lugar da recusa `IMAGE: interlaced PNG is not
    supported`. Todo o IDAT é inflado uma vez para o comprimento cru somado dos
    passes (`pngAdam7RawLen`); cada passe é uma sub-imagem independente, com seus
    próprios filtros de scanline, revertida pelo `unfilter` existente (agora com
    offset) e espalhada no buffer completo `width x height` (`unfilterAdam7`).
    Passes vazios (largura/altura zero) e passes com exatamente um pixel de
    largura/altura são tratados, seguindo a geometria de passes da especificação.
    Os tipos de cor 0/2/3/4/6 com bit-depth 8 no caminho não-interlaçado ficam
    inalterados. As fixtures são PNGs interlaçados reais do ImageMagick (RGB 20x13
    cobrindo todos os passes, RGBA 13x9, e cinza 17x11 com passes vazios e de um
    pixel), cada um byte-validado de forma independente pelo `ImageIO` do Java e
    pelo PIL. Prova: `PngInterlaceE2ETest` **4/4** (soma das amostras + hash
    rolante de 24 bits contra os pixels do PIL/ImageIO), RED-first (`IMAGE:
    interlaced PNG is not supported` com o decoder antigo), na JVM + Native x86-64
    + riscv64(qemu) + Script; os testes PNG existentes do `RasterDecodeE2ETest`
    seguem 4/4.

22. **Bit depths 1/2/4/16 do PNG — LANDADA 01/10 (Kof puro, todos os alvos).**
    O `libs/image/Png.kf` agora decodifica todos os bit depths permitidos pela
    especificação, no lugar da recusa `IMAGE: unsupported PNG bit depth`. O
    decoder é generalizado para bits-por-pixel: `pngSampleChannels` valida a
    combinação profundidade/tipo de cor, `pngRawLen`/`pngAdam7RawLen` dimensionam
    o stream inflado, e `unfilter`/`unfilterAdam7` movem amostras sub-byte como
    campos de bits e amostras de 8/16 bits como bytes. Depois do unfilter,
    `pngUnpackSub` escala o cinza 1/2/4-bit por `255/maxval` (índices de paleta
    ficam crus) e `pngUnpack16` pega o byte alto das amostras de 16 bits (a regra
    do `farbfeld`). O caminho de 8 bits fica inalterado. As fixtures cobrem
    cada combinação nova — cinza 1/2/4-bit, cinza 16-bit, RGB 16-bit, paleta
    2/4-bit — mais dois arquivos sub-byte interlaçados Adam7 (cinza 4-bit 19x11,
    paleta 4-bit 18x10) que exercitam o scatter sub-byte; os arquivos cinza
    sub-byte e 16-bit são montados à mão (zlib) e todos são lidos de forma
    independente pelo PIL e pelo `ImageIO` do Java. Prova: `PngBitDepthE2ETest`
    **4/4** (soma das amostras + hash rolante de 24 bits) na JVM + Native x86-64
    + riscv64(qemu) + Script, RED-first (`IMAGE: unsupported PNG bit depth 1` com
    o decoder antigo, medido); vizinhos `RasterDecodeE2ETest` PNG 4/4 e
    `PngInterlaceE2ETest` 4/4 inalterados.

**DECIDIDO 30/09 (`D-WEBP-LOSSY-PURE-KOF`, opção C): WebP lossy `VP8 ` + AVIF
como decoder Kof puro em todos os alvos.** O achado medido que forçou a decisão:
a escotilha do JPEG não se estende — o OpenJDK 25 `javax.imageio` **não tem**
leitor de WebP nem de AVIF (`ImageIO.getImageReadersByFormatName("webp"/
"avif")` vazio), então o `image.decode` não lastreia nenhum dos dois formatos
sem um plugin de terceiros (TwelveMonkeys / uma lib AVIF), dependência que a
mantenedora rejeitou. A rota é um decoder VP8 lossy em Kof puro (RFC 6386),
library-first, mesma forma das fatias do VP8L, com a mesma disciplina de fatias
(cada uma unidade completa e testada; sem meio-decode intermediário). Cadeia de
fatias: (1) parser RIFF/`VP8 ` + frame header + decoder booleano de range (§7);
(2) header de modo/segmento por macrobloco + tabelas de probabilidade dos
coeficientes; (3) desquantização + DCT/WHT inversa (§14, fatia 5a LANDADA) e
depois predição intra + reconstrução (§12); (4) filtro de deblocking in-loop; (5)
o caminho adaptativo (não-keyframe). A cadeia de key frame está completa e
roteada (fatia 7 LANDADA 01/10): o `decodeRaster` agora decodifica um WebP lossy
através de `libs/image/Vp8Raster.kf` (nunca decode errado silencioso); AVIF vem
depois do VP8. Os key frames multi-partição de token agora decodificam (fatia 8
LANDADA 01/10, `libs/image/Vp8Coeffs.kf`); o caminho adaptativo (não-keyframe)
permanece uma recusa explícita `IMAGE:`, não um meio-decode.

## EN
[English](image-vision-plan.md)

# 33. Regra final

O objetivo é que Kof possa evoluir de:

```text
arquivo → imagem → processamento → visão computacional → resultado
```

com APIs próprias, consistentes e multiplataforma.

O desenvolvedor Kof não deve precisar abandonar a linguagem para fazer:

* processamento de imagem;
* leitura de câmera;
* detecção;
* OCR;
* QR Code;
* análise visual;
* inferência de modelos.

Tudo deve ser construído incrementalmente, preservando a base existente e seguindo a filosofia do Kof:

**menos complexidade acidental, APIs pequenas, intenção clara e controle sobre a implementação.**
