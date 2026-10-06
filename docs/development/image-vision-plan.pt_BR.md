[English](image-vision-plan.md) | [Português](image-vision-plan.pt_BR.md)

# Plano Estratégico — Kof Image & Vision

**Dono:** `192.168.15.21:9092` (lane pipeline/image-vision — UM plano, UM dono por `D-PLAN-ONE-OWNER`). ⚠️ As fatias AVIF 2a–2g (01–02/10) foram escritas por `192.168.15.101:9092`, que é dona de `memory-safety-plan` — violação de `D-PLAN-ONE-OWNER` registrada 02/10; a lane dona DEVE re-verificar esse estado no tip (ordem de releitura, DOING 02/10).
> **AVIF fatia 3j LANDADA 05/10 (Kof puro, PREDIÇÃO INTRA BASE AV1 — `libs/image/Av1Intra.kf`, novo):** o estágio que forma a predição à qual a reconstrução (fatia 3i) soma o resíduo, conforme AV1 §7.11.2 (processo de predição intra). Esta fatia cobre os preditores não-direcionais: `av1IntraDcPred` (§7.11.2.5 — a média `avg = (sum + ((w+h)>>1))/(w+h)` sobre `LeftCol`+`AboveRow` quando `haveLeft && haveAbove`, as formas `leftAvg`/`aboveAvg`, e a constante `1 << (BitDepth-1)` quando nenhuma borda existe), `av1IntraV`/`av1IntraH` (os casos degenerados de ângulo 90/180), `av1IntraPaethPred` (§7.11.2.2 — `base = AboveRow[j] + LeftCol[i] - AboveRow[-1]`, as três distâncias `Abs` e o desempate `pLeft <= pTop && pLeft <= pTopLeft` / `pTop <= pTopLeft`), e `av1IntraSmoothPred` (§7.11.2.6 — SMOOTH `Round2(smWeightsY[i]*AboveRow[j] + (256-smWeightsY[i])*LeftCol[h-1] + smWeightsX[j]*LeftCol[i] + (256-smWeightsX[j])*AboveRow[w-1], 9)`, SMOOTH_V com `smWeightsY` e `Round2(·,8)`, SMOOTH_H com `smWeightsX`), com as cinco tabelas `Sm_Weights_Tx_{4x4,8x8,16x16,32x32,64x64}` citadas verbatim da spec. `av1IntraPred(mode, above, left, w, h, haveAbove, haveLeft, topLeft, bitDepth)` despacha `DC_PRED`(0)/`V_PRED`(1)/`H_PRED`(2)/`SMOOTH_*`(9/10/11)/`PAETH_PRED`(12) e recusa um modo direcional (3..8) com diagnóstico `IMAGE:` explícito (o edge filter/upsample direcional é a fatia 3k), nunca uma predição errada silenciosa. **Oráculo = uma segunda fonte independente:** os kernels REAIS da libaom (`aom_dsp/intrapred.c` compilado da fonte — `aom_{v,h,smooth,smooth_v,smooth_h,paeth,dc,dc_128,dc_top,dc_left}_predictor_WxH_c` e suas variantes `aom_highbd_*`) sobre cada tamanho de transformada (quadrado + retangular), os três bit depths (8/10/12) e as quatro combinações `haveAbove`/`haveLeft` (1.596 blocos, 38.892 linhas) com entradas de borda byte-idênticas (as substituições de borda ausente da spec aplicadas antes da predição); a probe Kof reproduz cada amostra com zero divergências, e o golden é pinado em gzip+base64 (`SHA256 1db200be…`). **Prova RED-first:** a árvore pré-fatia não compila a probe (`PKG006 import 'image.Av1Intra' not found`, medido); pós-fatia `Av1IntraE2ETest` **6/6** em JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu) + JS. Sem mudança de compilador, sem gap novo, sem decisão. `decodeRaster` ainda recusa AVIF até a cadeia fechar.
> **AVIF fatia 3i LANDADA 05/10 (Kof puro, RECONSTRUÇÃO AV1 — `libs/image/Av1Recon.kf`, novo):** o estágio que converte o bloco de transformada dequantizado produzido pela fatia 3f de volta em amostras reconstruídas, conforme AV1 §7.11.4 (processo de reconstrução), passos 2 e 3. O passo 2 é a transformada inversa 2D (`av1InvTx2d`, fatia 3g); este módulo carrega o passo 3, a soma resíduo-predição com os flips FLIPADST e `Clip1`: `av1ReconFlipUd` (1 para `FLIPADST_DCT`=4, `FLIPADST_ADST`=8, `V_FLIPADST`=14, `FLIPADST_FLIPADST`=6), `av1ReconFlipLr` (1 para `DCT_FLIPADST`=5, `ADST_FLIPADST`=7, `H_FLIPADST`=15, `FLIPADST_FLIPADST`=6), `av1ReconClip1` = `Clip3(0, (1<<BitDepth)-1, ·)`, `av1ReconAddPred` (para cada `(i,j)` define `xx = flipLR ? w-j-1 : j`, `yy = flipUD ? h-i-1 : i` e `out[yy][xx] = Clip1(pred[yy][xx] + residual[i][j])` — os flips ficam no índice de destino, conforme a spec) e `av1Reconstruct` compondo `av1InvTx2d` com a soma. O chamador compõe `av1Reconstruct(av1Dequant(quant, txSz, bitDepth, dcQuant, acQuant), pred, txSz, txType, bitDepth, lossless)`. **Oráculo = uma segunda fonte independente:** uma cópia de `inv_txfm2d_add_c` da libaom (`av1/common/av1_inv_txfm2d.c`) com os flips de índice FLIPADST LIGADOS e o resíduo somado a um buffer de predição com `Clip1`, sobre todas as combinações válidas `(txSz,txType,bitDepth)` (as mesmas 579 que a fatia da transformada inversa admite) com uma rampa de predição determinística para cruzar a fronteira do `Clip1`; a probe Kof reproduz cada amostra com zero divergências, e o golden é pinado em gzip+base64 (`SHA256 f7a43da4…`). **Prova RED-first:** a árvore pré-fatia não compila a probe (`PKG006 import 'image.Av1Recon' not found`, medido); pós-fatia `Av1ReconE2ETest` **6/6** em JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu) + JS. Sem mudança de compilador, sem gap novo, sem decisão. `decodeRaster` ainda recusa AVIF até a cadeia fechar.
> **AVIF fatia 3h LANDADA 05/10 (Kof puro, LOOP FILTER AV1 — `libs/image/Av1Lf.kf`, novo):** o estágio de desblocagem que segue a transformada inversa, conforme AV1 §7.14 (processo do loop filter). O módulo carrega a aritmética independente de alvo que a travessia de borda da §7.14.2 dirige: `av1LfMaxLoopFilter` (`MAX_LOOP_FILTER` = 63), `av1LfIndex`/`av1LfSegFeature` (`i = (plane==0) ? pass : plane+1`, `feature = SEG_LVL_ALT_LF_Y_V + i`), `av1LfModeType` (libaom `mode_lf_lut`: intra 0..12 → 0, inter simples 13..16 → 1,1,0,1 com `GLOBALMV`=15 → 0, composto 17..24 → 1,1,1,1,1,1,0,1 com `GLOBAL_GLOBALMV`=23 → 0); `av1LfFilterSize` (§7.14.3, `Min(Tx_Width/Height[prevTxSz], Tx_Width/Height[txSz])` e então `Min(16,·)` luma / `Min(8,·)` croma); `av1LfStrength` (§7.14.4/§7.14.5: `Clip3(0,63,deltaLF+loop_filter_level[i])`, soma do `FeatureData` do segmento, o escalonamento `nShift = lvlSeg>>5` de `ref_deltas`/`mode_deltas` quando `loop_filter_delta_enabled`, depois `shift` de `loop_filter_sharpness`, `limit` = `Clip3(1,9-sharpness,·)` ou `Max(1,·)`, `blimit = 2*(lvl+2)+limit`, `thresh = lvl>>4`); `av1LfMask` (§7.14.6.2 `hevMask`/`filterMask`/`flatMask`/`flatMask2` com `threshBd`/`limitBd`/`blimitBd` = `<<(BitDepth-8)` e `thresholdBd = 1<<(BitDepth-8)`); `av1LfNarrow` (§7.14.6.3, a aritmética do `filter4` sobre amostras deslocadas por `-0x80<<(BitDepth-8)`, o arredondamento `filter1/filter2`, taps externos só quando `hevMask==0`) e `av1LfWide` (§7.14.6.4, `log2Size` 3/4 com `n`=3/6 luma ou 2 croma e `n2`=0/1); `av1LfSample` despacha exatamente como §7.14.6 (sem filtro / narrow / wide-8 / wide-16). **Oráculo = uma segunda fonte independente:** o sample filtering da §7.14.6 roda os kernels REAIS da libaom (`aom_lpf_{horizontal,vertical}_{4,6,8,14}_c` e suas variantes `aom_highbd_*` de `aom_dsp/loopfilter.c`, incluindo os taps reais de `filter4`/`filter6`/`filter8`/`filter14`) sobre 240 casos (8 sementes × bit depths 8/10/12 × luma/croma × tamanhos 4/8/16 × ambas as direções de borda, cada um filtrando quatro fronteiras consecutivas como o laço interno do kernel); o tamanho da §7.14.3 (1444 casos), a força §7.14.4/§7.14.5 (300 casos, incluindo os limites do clamp e do `nShift`) e o `mode_lf_lut` (25 modos) são reproduzidos de `av1/common/av1_loopfilter.c` da libaom (`update_sharpness`, `get_filter_level`) e do seu `mode_lf_lut[]`. A probe Kof reproduz cada linha com zero divergências, e o golden é pinado em gzip+base64 (`SHA256 65ac3261…`). **Prova RED-first:** a árvore pré-fatia não compila a probe (`PKG006 import 'image.Av1Lf' not found`, medido); pós-fatia `Av1LfE2ETest` **6/6** em JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu) + JS. Sem mudança de compilador, sem gap novo, sem decisão. `decodeRaster` ainda recusa AVIF até a reconstrução fechar a cadeia.
> **Decode TIFF LANDADO 04/10 (Kof puro, todos os alvos — `libs/image/Tiff.kf`, novo; `decodeRaster` despacha `fmt == "TIFF"`).** O "progressivamente suportar formatos comuns: … TIFF" do plano tinha só metadados; `decodeRaster` recusava TIFF. Esta fatia adiciona o decode Baseline TIFF 6.0: ambas as ordens de byte (`II`/`MM`), amostras de 8 bits, layout chunky, grayscale (WhiteIsZero/BlackIsZero) / RGB / RGBA / gray-alpha / paleta, uma ou várias strips, Compressão 1 (nenhuma) e 32773 (PackBits). O walk clássico do IFD lê as tags 256/257/258/259/262/273/277/278/279/284/317/320/338; a **regra de valor da TIFF 6.0 §2** — um valor cujo tamanho em bytes cabe no campo de 4 bytes é lido INLINE, só arrays maiores moram no offset (`tiffValues`) — ponto de correção que um primeiro rascunho perdeu (ele sempre lia o offset, então um array `BitsPerSample` inline dessincronizava). `ExtraSamples` é honrado por VALOR (2 = alpha não-associado é admitido; 1 = associado é recusado `IMAGE: TIFF associated alpha not covered`), e `SamplesPerPixel` deve casar com o fotométrico (`IMAGE: TIFF samples per pixel not covered`). Recusas nomeadas: compressão, planar, predictor, bits-por-amostra, fotométrico, amostras extras, amostras-por-pixel, amostras-de-paleta. **Prova RED-first:** pré-fix `tiffDecodesOnJvm` falha `IMAGE: raster decode is not supported for TIFF` (medido revertendo o despacho); pós-fix `TiffDecodeE2ETest` **8/8** em JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu), JS `IOJS001`, mais 9 recusas nomeadas. As fixtures são construídas byte-a-byte conforme a spec e validadas offline contra PIL e Java `ImageIO`; o golden é produzido por um segundo leitor TIFF Java independente (`TiffDecodeFixtures.readFacts`) que concorda fato-a-fato com a biblioteca Kof nos pixels E em cada string de recusa. Sem mudança de compilador, sem gap novo, sem decisão. Próximo: continuar a cadeia de decode AVIF (abaixo).
> **AVIF fatia 3g LANDADA 05/10 (Kof puro, TRANSFORMADA INVERSA AV1 — `libs/image/Av1InvTx.kf`, novo):** o estágio que converte o bloco de coeficientes dequantizados produzido pela fatia 3f de volta a amostras residuais espaciais, conforme AV1 §7.13 (processo de transformada inversa) com as borboletas da §7.13.2.1. O módulo carrega a tabela `Cos128_Lookup[65]` citada verbatim da spec e constrói toda a maquinaria 1D/2D a partir dela: `av1Brev`, `av1InvRound2`/`av1InvRound2L` (`Round2(x,n)=(x+(1<<(n-1)))>>n`), `av1InvClip3`, as primitivas de borboleta `av1InvB(a,b,angle,flip)` (`T[a]=Round2(T[a]*cos128(angle)-T[b]*sin128(angle),12)`, `T[b]=Round2(T[a]*sin128(angle)+T[b]*cos128(angle),12)`, trocados quando `flip==1`) e `av1InvH(a,b,flag)` (`T[a]=Clip(x+y)`, `T[b]=Clip(x-y)`, reordenados quando `flag==1`); a DCT inversa (`av1InvDct`, n=2..6, todos os estágios da §7.13.2.3 com o `av1InvDctPermute` da spec), a ADST inversa (`av1InvAdst4`/`8`/`16` com a permutação de entrada da §7.13.2.4 e a permutação de saída da §7.13.2.10, despachadas por `av1InvAdst`), a WHT inversa (`av1InvWht`, §7.13.2.9) e a transformada identidade (`av1InvIdentity`, n=2..5); então `av1InvTx2d(dequant, txSz, txType, bitDepth, lossless)` aplica o processo 2D da §7.13.3: `rowShift = Lossless ? 0 : Transform_Row_Shift[txSz]`, `colShift = Lossless ? 0 : 4`, os `rowClampRange`/`colClampRange` (`BitDepth+8` / `Max(BitDepth+6,16)`), o pré-escalonamento `T[j]=Round2(T[j]*2896,12)` quando `Abs(log2W-log2H)==1`, o caminho WHT quando lossless, o `Clip3` entre os estágios de linha e coluna, e o `Round2(T[j],rowShift)` / `Round2(T[i],colShift)` finais — consumindo a raster dequantizada com o stride efetivo `Min(32,w) × Min(32,h)` (`dequant[i*tw+j]`, fatia 3f). O mapeamento de tipo de transformada por linha/coluna é derivado da tabela de nomes `<col>_<row>` da `03.symbols.md` da spec: Row DCT = {0,1,4,11}, Row ADST = {2,3,5,6,7,8,13,15}, Col DCT = {0,2,5,10}, Col ADST = {1,3,4,6,7,8,12,14}. Os `flipUD`/`flipLR` do passo 3 da §7.12 NÃO são aplicados aqui — eles pertencem à soma residual-com-predição, então `av1InvTx2d` devolve o residual e o chamador é dono dos flips. **Oráculo = uma segunda fonte independente:** um harness de transformada inversa da libaom (`av1_inv_txfm2d.c` com os locais `av1_inv_txfm1d.c`/`av1_txfm.c`) com os flips do loop filter forçados desligados produz o golden para todas as 579 combinações válidas `(txSz,txType,bitDepth)` dos 19 tamanhos × 16 tipos × 3 depths (os combos inválidos de kernel 1D, ex. DCT64/ADST64/identidade num eixo 64, são excluídos); a probe Kof reproduz cada amostra com zero divergências, e o golden é pinado em gzip+base64 (`SHA256 4edb7b94…`). **Prova RED-first:** a árvore pré-fatia não compila a probe (`PKG006 import 'image.Av1InvTx' not found`, medido); pós-fatia `Av1InvTxE2ETest` **6/6** em JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu) + JS. Sem mudança de compilador, sem gap novo, sem decisão. `decodeRaster` ainda recusa AVIF até o loop filter e a reconstrução fecharem a cadeia.
> **AVIF fatia 3f LANDADA 05/10 (Kof puro, DEQUANTIZAÇÃO AV1 — `libs/image/Av1Quant.kf`, novo):** o estágio que reescala os níveis de coeficientes quantizados produzidos pela fatia 3e de volta a valores do domínio da transformada, conforme AV1 §7.12 (reconstrução e dequantização) / §7.12.2 (funções de dequantização). O módulo carrega `Dc_Qlookup[3][256]` e `Ac_Qlookup[3][256]` citadas verbatim da spec (o eixo `[3]` indexado por `(BitDepth-8)>>1`), `av1DcQ`/`av1AcQ` (`dc_q`/`ac_q`), `av1DqDenom` (a regra `TX_32X32`/`TX_16X32`/`TX_32X16`/`TX_16X64`/`TX_64X16` → 2 e `TX_64X64`/`TX_32X64`/`TX_64X32` → 4) e `av1Dequant`, o laço do passo 1 da §7.12.2 sobre a raster efetiva `Min(32,w) × Min(32,h)` (`q = dcQuant` em `(0,0)` senão `acQuant`; `sign * (Abs(dq) & 0xFFFFFF) / dqDenom`; `Clip3(-(1<<(7+BitDepth)), (1<<(7+BitDepth))-1, ·)`). Este é o caminho sem qmatrix (`using_qmatrix == 0`), que é o que toda imagem still AVIF e o `.avif` do host usam. **Oráculo = uma segunda fonte independente:** todos os 1536 números das tabelas foram conferidos valor-a-valor contra `av1/common/quant_common.c` da libaom (`dc_qlookup_QTX`/`_10_QTX`/`_12_QTX` e `ac_qlookup_QTX`/`_10_QTX`/`_12_QTX`; seis arrays de 256 entradas, zero diferenças); o teste então roda 19 blocos dequantizados (uma entrada determinística de coeficientes quantizados, todos os 19 tamanhos de transformada, os três bit depths) pelos acessores Kof e exige que um segundo leitor Java independente reproduza cada valor. **Prova RED-first:** a árvore pré-fatia não compila a probe (`PKG006 import 'image.Av1Quant' not found`, medido); pós-fatia `Av1QuantE2ETest` **6/6** em JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu) + JS. `decodeRaster` ainda recusa AVIF até a cadeia fechar.
> **AVIF fatia 3d LANDADA 04/10 (Kof puro, SELEÇÃO DE CONTEXTO DE COEFICIENTES AV1 — `libs/image/Av1CoeffCtx.kf`, novo):** as funções de contexto que o walk de coeficientes do tile (`coeffs()`, AV1 §6.4.3/§5.11.39) chama para escolher qual CDF de entropia um símbolo usa — `av1GetCoeffBaseCtx` (`get_coeff_base_ctx`, §9.3.3.3, as variantes `coeff_base` e `coeff_base_eob`), `av1GetCoeffBrCtx` (`get_coeff_br_ctx`, §9.3.3.4), `av1AllZeroCtxY`/`av1AllZeroCtxUV` (o contexto `all_zero`, §9.3.3.1) e `av1DcSignCtx`/`av1DcSignContribution` (`dc_sign`, §9.3.3.2), com as quatro tabelas de deslocamento de posição (`Sig_Ref_Diff_Offset`, `Coeff_Base_Ctx_Offset`, `Coeff_Base_Pos_Ctx_Offset`, `Mag_Ref_Offset_With_Tx_Class`) citadas da spec. As funções só leem os níveis `Quant[]` parcialmente decodificados (`pos = scan[c]`) e recebem `txType`/`txSz` do chamador, então são puras e independentes de alvo. **Oráculo = duas fontes independentes:** a tabela 2D embutida `Coeff_Base_Ctx_Offset` foi conferida posição-a-posição contra `av1_nz_map_ctx_offset` da libaom (`av1/common/txb_common.c`) — 7.440 posições, zero diferenças, e separadamente contra o próprio algoritmo de offset da spec; o teste então roda 2.000 casos semeados por todos os 19 tamanhos de transformada e os 16 tipos (mais as grades `all_zero`/`dc_sign`) pelos acessores Kof e exige que um segundo leitor Java independente reproduza cada valor — 3.130 fatos, zero diferenças. **Prova RED-first:** a árvore pré-fatia não compila a probe (`PKG006 import 'image.Av1CoeffCtx' not found`, medido); pós-fatia `Av1CoeffCtxE2ETest` **6/6** em JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu) + JS. `decodeRaster` ainda recusa AVIF até a cadeia fechar.
> **AVIF fatia 3c LANDADA 04/10 (Kof puro, CDFs DEFAULT DE COEFICIENTES AV1 — `libs/image/Av1CoeffCdf.kf`, novo):** o terceiro estágio da cadeia de decode do AVIF, as tabelas de entropia que o walk de coeficientes do tile consulta. `class Av1CoeffCdf(qctx)` carrega as 13 tabelas de CDF default de coeficientes das tabelas adicionais §10 da spec AV1 — `Default_Txb_Skip_Cdf`, `Default_Eob_Pt_16/32/64/128/256/512/1024_Cdf`, `Default_Eob_Extra_Cdf`, `Default_Dc_Sign_Cdf`, `Default_Coeff_Base_Eob_Cdf`, `Default_Coeff_Base_Cdf`, `Default_Coeff_Br_Cdf` — embutidas verbatim da spec e selecionadas por `COEFF_CDF_Q_CTXS` via `get_q_ctx(base_q_idx)` (`<=20 → 0`, `<=60 → 1`, `<=120 → 2`, senão `3`; libaom `av1/common/entropy.c`). Os acessores (`txbSkipCdf`, `eobPtCdf`, `eobExtraCdf`, `dcSignCdf`, `coeffBaseEobCdf`, `coeffBaseCdf`, `coeffBrCdf`) achatam o layout `[qctx][txSzCtx][ptype][ctx]` da spec e devolvem uma cópia nova para o walk do tile poder adaptá-la in place sem mutar os defaults; `coeffBrCdf` aplica o clamp `Min(txSzCtx, TX_32X32)` da spec. Os valores são parseados de literais de string em pedaços (`split`/`toInt`), então o módulo permanece Kof puro em todos os alvos. **Oráculo = uma segunda fonte independente:** todas as 13 tabelas foram conferidas valor-a-valor contra `av1/common/token_cdfs.h` da libaom (os macros `AOM_CDFn` expandidos para a forma de CDF da spec; o eixo `[2]` colapsado e a duplicata do par 512/1024 normalizados para a forma da spec) — 15.996 números, zero diferenças; o teste então exige que a probe Kof reproduza esse golden derivado da libaom para toda CDF nos quatro qctx. **Prova RED-first:** a árvore pré-fatia não compila a probe (`PKG006 import 'image.Av1CoeffCdf' not found`, medido); pós-fatia `Av1CoeffCdfE2ETest` **6/6** em JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu) + JS. `decodeRaster` ainda recusa AVIF até a cadeia fechar.
> **AVIF fatia 3b LANDADA 04/10 (Kof puro, DESCRITOR DE TRANSFORMADA AV1 — `libs/image/Av1Tx.kf`, novo):** o segundo estágio da cadeia de decode do AVIF, sobre o decoder de entropia da fatia 3a. Fornece tudo que o walk de coeficientes consulta: as tabelas de tamanho de transformada (`Tx_Width`/`Tx_Height`/`Tx_Width_Log2`/`Tx_Height_Log2`/`Tx_Size_Sqr`/`Tx_Size_Sqr_Up`/`Adjusted_Tx_Size`/`txSzCtx`), as **ordens de scan** (`get_scan`/`get_default_scan`/`get_mrow_scan`/`get_mcol_scan`), as classes de transformada (`get_tx_class`), os conjuntos (`get_tx_set`) e a seleção de tipo (`compute_tx_type`/`is_tx_type_in_set`/`Mode_To_Txfm`), cada tabela e regra citada da spec AV1 §3/§5.9.2/§6.4.3 e das tabelas adicionais, lida no host de dev 04/10. Os scans são GERADOS, não embutidos: zig-zag para quadradas, diagonal-de-linha para altas, diagonal-de-coluna para largas, row-major para `mrow`, column-major para `mcol`, com as dimensões efetivas da spec para os tamanhos de 64 (uma transformada 64-quadrada escaneia como 32x32, uma 16x64/64x16 como 16x32/32x16 — a regra "metade dos coeficientes é zero"). O teste pina cada uma das 19x3 ordens contra `av1_scan_orders` da libaom (o markdown da spec escreve a mesma ordem física row-major enquanto a libaom achata column-major; ambos são normalizados e concordam). **Prova RED-first:** a árvore pré-fatia sequer compila a probe (`PKG006 import 'image.Av1Tx' not found`, medido); pós-fatia `Av1TxE2ETest` **7/7** em JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu) + JS (aritmética pura, sem `IOJS001`), com um segundo gerador Java puro e independente (`Av1TxSupport.javaFacts`) concordando fato-a-fato nas tabelas de transformada, em todas as 57 ordens de scan (tamanho + hash rolante), nas classes/conjuntos e na seleção de tipo. `decodeRaster` ainda recusa AVIF até a cadeia fechar.
> **AVIF fatia 3a LANDADA 04/10 (Kof puro, DECODER DE SÍMBOLOS / ENTROPIA AV1 — `libs/image/Av1Symbol.kf`, novo):** o primeiro estágio da cadeia de decode. Implementa o range coder da AV1 §9.2/§9.3 literalmente: `init_symbol(sz)` (semeia `Min(sz*8,15)` bits, `SymbolValue=((1<<15)-1)^padded`, `SymbolRange=1<<15`, `SymbolMaxBits=8*sz-15`), `read_symbol(cdf)` (`cur = ((SymbolRange>>8)*(f>>6))>>1 + 4*(N-symbol-1)`, renorm, então a adaptação da spec `rate = 3 + (cdf[N]>15) + (cdf[N]>31) + Min(FloorLog2(N),2)`, `cdf[i] += (tmp-cdf[i])>>rate`, `cdf[N] += (count<32)`), `read_bool()` = `read_symbol` sobre a cdf fixa equiprovável `[1<<14,1<<15,0]` com adaptação suprimida, `read_literal(n)` MSB-primeiro, `exit_symbol()`, `position()`. A classe também carrega `av1FloorLog2`/`av1Bool`. **Oráculo:** fluxos de bytes produzidos pelo encoder de entropia da libaom (`aom_dsp/entenc.c`) para seis casos (CDF, sequência de símbolos) escolhidos, com round-trip pelo próprio decoder da libaom (`entdec.c`), construído no host de dev 04/10 (não commitado); o golden fixado é a sequência de símbolos (`literal20`/`literal12`/`sym4fixed`/`sym5adapt`/`sym2adapt`/`sym8adapt`). **Prova no arquivo real:** o payload do tile primário do `.avif` do host (fatia 2n `readAvifTilePayloads`, 42132 bytes) decodifica seus primeiros 64 booleanos + 8 literais para `BOOLS 1 0 0 1 0 1 1 1 1 1 0 1 0 0 0 0 0 0 0 1 1 1 1 1 0 1 1 1 1 1 0 0 1 1 1 0 0 1 0 0 1 0 1 0 1 1 0 0 0 1 1 0 1 1 0 0 1 0 1 1 1 1 0 0 LIT 236 22 144 184 174 78 131 116`, batendo com `od_ec_decode_bool_q15` da libaom nos mesmos bytes (nota: os literais continuam o fluxo após os 64 booleanos; uma medição com decoder reiniciado dá uma sequência diferente). O segundo leitor Java independente (`Av1SymbolSupport.javaDecode`, um walk da spec em Java puro) concorda fato-a-fato nas seis fixtures E no tile real. Prova RED-first: `Av1SymbolE2ETest` **8/8** em JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu) + JS (compila E executa — o decoder é aritmética pura sem `kof.io`, então o JS não recusa `IOJS001`; esse é o comportamento correto desta fatia) + os dois testes de concordância. Bug pego: o oráculo do host de dev imprimia o buffer do encoder DEPOIS de `od_ec_enc_clear` liberá-lo (use-after-free corrompia os bytes da primeira fixture) — corrigido com `memcpy` antes do clear; lembrete de que fixtures são re-medidas, nunca lembradas. Sem mudança de compilador, sem gap novo, sem decisão. Próximo: walk de coeficientes do tile → loop filter → quantização → `decodeRaster` AVIF.
> **AVIF fatia 2o LANDADA 03/10 (Kof puro, tile_info NÃO-UNIFORME — `libs/image/AvifFrame.kf` + `AvifSeq.kf`):** a travessia do tile_info (2e) recusava `IMAGE: avif tile size list not covered` quando `uniform_tile_spacing_flag == 0`; esta fatia implementa o ramo não-uniforme da spec (AV1 5.9.15): as listas de tamanho `ns()` por eixo `width_in_sbs_minus_1` (limitada por `Min(sbCols - startSb, maxTileWidthSb)`) e `height_in_sbs_minus_1` (limitada por `Min(sbRows - startSb, maxTileHeightSb)`, onde `maxTileHeightSb` usa a regra de área `widestTileSb` da spec e o ajuste de `minLog2Tiles`), e então deriva `TileCols`/`TileRows` e seus valores `tile_log2` (para que `context_update_tile_id`/`tile_size_bytes` sejam lidos com a largura certa). Novo helper `seqNs(b, bitPos, n)` implementa o descritor não-simétrico da AV1 4.10.6 (`w = FloorLog2(n)+1`, `m = (1<<w)-n`, um bit extra quando `v >= m`). O caminho uniforme é byte-idêntico (o 1x1 do arquivo real segue parseando). **Prova RED-first:** nova fixture `nonuni.avif` (flag uniforme 0, tamanhos de coluna {1,1} + tamanho de linha {2} sobre o `redSeq128` 128x128, tiles 2x1) — pré-fix a travessia recusava `IMAGE: avif tile size list not covered`; pós-fix `nonuni t=0 ... tiles=2x1 hb=3`. O segundo leitor Java (`AvifFrameJavaSupport`) ganhou seu próprio walk `ns()` independente; a antiga fixture de recusa `sizelist` (não-uniforme, não mais uma recusa) foi removida. `AvifFrameE2ETest` **16/16** + bateria AVIF **56/56** em JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu), JS `IOJS001`. Próximo: walk de payload/coeficientes do tile → loop filter → quantização → `decodeRaster` AVIF.
> **AVIF fatia 2n LANDADA 03/10 (Kof puro, PAYLOADS DE TILE — `readAvifTilePayloads` no arquivo real; `libs/image/AvifGroup.kf`):** a travessia do tile group (2f/2m) enumera o tamanho em bytes de cada tile mas descarta sua posição, então a faixa de bytes que o decodificador de coeficientes AV1 consome ainda não era endereçável. Esta fatia registra o offset de início intercalado de cada tile (`AvifTileGroup.tileOffsets`) na posição exata da 6.10.1 (`sz -= tileSize + TileSizeBytes`, então o offset é `p` DEPOIS do campo `le(TileSizeBytes)` e ANTES dos bytes do tile) e adiciona `avifTilePayload(item, group, i)` (cópia `Int[]` com checagem de limites), `avifItemTilePayloads(item)` (todos os tiles na ordem de tile pelos grupos do frame) e `readAvifTilePayloads(path)` (a composição no nível de arquivo sobre `avifItemBytes`). Ainda é EXTRAÇÃO de payload, não decode: o walk de coeficientes, o loop filter e a quantização ficam para as próximas fatias de decode, e o `decodeRaster` segue recusando AVIF. **Prova no arquivo real (executada):** `readAvifTilePayloads` no `.avif` do host → `tile 0 len=42132 first=151 last=136 h=4094`, batendo com a travessia de grupo da 2m (`total=42132`) e com o comprimento do item1 do `iloc`. Fixtures/leitores com bytes DISTINTOS por tile (o filler uniforme `0x07` antigo mascarava um offset errado): `groupBytes` agora escreve uma semente crescente, e tanto a probe Kof quanto o segundo leitor Java fazem hash dos bytes de cada tile (`avifTilePayload` vs `javaTileFacts`) — a concordância é a prova. Prova RED-first: `AvifFrameE2ETest` **16/16** e a bateria AVIF inteira **56/56** (JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu), JS `IOJS001`, concordância do segundo leitor). Próximo: walk de payload/coeficientes do tile → loop filter → quantização → `decodeRaster` AVIF.
> **AVIF fatia 2m LANDADA 03/10 (Kof puro, TAIL DO HEADER + TILE GROUP INLINE DO OBU_FRAME — `readAvifTileGroups` no arquivo real; `libs/image/AvifFrame.kf`, `AvifGroup.kf`, `AvifSeq.kf`):** a fatia 2l deixou o OBU_FRAME real (tipo 6) recusado honestamente porque o tile group inline fica DEPOIS do `uncompressed_header` completo, cujo tail (quantization/segmentation/delta_q/delta_lf/loop-filter/cdef/restoration/tx-mode/reduced_tx_set) não era caminhado. Esta fatia caminha esse tail da 5.9.2 até o `byte_alignment()` e localiza o grupo inline exatamente. `AvifSeq.kf` agora CAPTURA `enable_cdef`/`enable_restoration` no `AvifSeqHeader` (antes lidos e descartados — exatamente os bits que guardam os ramos `cdef_params`/`lr_params`); `AvifFrame.frameTailWalk` implementa `quantization_params` (5.9.9, `read_delta_q` su(1+6) via o novo `seqSu`), `segmentation_params` (5.9.11, `seg_id`/`seg_alt_q` com `CodedLossless` derivado pelo laço `LosslessArray` da spec), `delta_q_params`/`delta_lf_params` (5.9.12/13), `loop_filter_params` (5.9.8), `cdef_params` (5.9.14), `lr_params` (5.9.16), `read_tx_mode` (5.9.17) e `reduced_tx_set`, com `frame_reference_mode`/`skip_mode_params` inferidos para os frames intra-only que esta face admite (`film_grain_params` recusado antes pelo `seqWalk`). `AvifFrameHeader.headerBytes` agora é o tamanho ALINHADO do header RELATIVO ao início do payload do OBU (antes era absoluto — um bug real que a fixture OBU_FRAME expôs), e o record ganha `baseQIdx`/`loopFilterLevel0`/`cdefBits`. `AvifGroup.avifItemTileGroups` lê o grupo inline único do OBU_FRAME em `p + headerBytes` em vez de recusar, e `tileGroupWalk` foi corrigido para o layout intercalado da 6.10.1 (`sz -= tileSize + TileSizeBytes`: o payload de cada tile é pulado antes do PRÓXIMO campo de tamanho) — a forma antiga de tabela de tamanhos contígua só funcionava para ≤2 tiles e decodificava errado um grupo de 4 tiles. **Prova no arquivo real:** `readAvifFrameHeader` → `t=0 480x410 tiles=1x1 hb=11 bq=32 lf=7 cd=0` (base_q_idx 32, loop_filter_level[0] 7, cdef desabilitado — bate com a medição do parser da spec); `readAvifTileGroups` → `group 0..0 n=1 sizes=0 last=42132 total=42132` (o único tile leva o payload restante). Fixtures/leitores spec-fiéis: `frameReduced` agora calcula a grade uniforme a partir dos `sbCols`/`sbRows` reais (o writer antigo escrevia um `context_update_tile_id` fixo de 2 bits, errado para 2x2), nova `q32.avif` não-lossless exercita o ramo cdef e o skip do lf-delta, nova `tg-inline.avif` é o golden positivo de OBU_FRAME, e ambos os segundos leitores (`AvifFrameJavaSupport`, `AvifGroupJavaSupport`) caminham o mesmo tail de forma independente. Prova: `AvifFrameE2ETest` **16/16** e a bateria AVIF inteira **56/56** (JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu), JS `IOJS001`, concordância do segundo leitor). Próximo: walk de payload/coeficientes do tile → loop filter → quantização → `decodeRaster` AVIF.
> **AVIF fatia 2l LANDADA 03/10 (Kof puro, ALINHAMENTO DO RAMO INTRA DO FRAME HEADER À SPEC — `readAvifFrameHeader` no arquivo real; `libs/image/AvifFrame.kf`):** compondo a cadeia do item da fatia 2k até o frame header no `.avif` REAL do host, a leitura recusou com `IMAGE: avif tile size list not covered`, embora o `tile_info()` desse arquivo seja uniforme 1x1. Causa raiz: o caminho intra lia `is_filter_switchable` + `is_motion_mode_switchable` (AV1 5.9.10) embora a spec só os leia no ramo NÃO-intra (5.9.2:766-777); num frame reduced/intra esses dois bits não existem, então o walk consumia dois bits espúrios e desalinhava o `tile_info` (medido por um parser Python spec-fiel: o código iniciava o `tile_info` no bit 6, a spec no bit 3). Os mesmos dois desvios eram compartilhados pelas três fixtures fabricadas e pelos dois segundos leitores — a lição do ponto cego compartilhado outra vez. Corrigido para a spec: os bits de interp/motion não são lidos no caminho intra; `frame_type` usa a numeração da spec (KEY=0 / INTER=1 / INTRA_ONLY=2 / SWIT — o código antigo tratava 0 como INTER e 1 como KEY, então o caminho não-reduced também recusava um frame INTRA_ONLY válido); e a recusa inalcançável `IMAGE: avif frame size-with-refs not covered` é removida (`frame_size_with_refs` é só não-intra, 5.9.2:760). **Prova no arquivo real:** pré-fix `readAvifFrameHeader` → `IMAGE: avif tile size list not covered`; pós-fix → `t=0 480x410 render=480x410 tiles=1x1 tileSizeBytes=0 hb=18`, batendo com os fatos de `ispe`/`av1C`; o tile group inline é então recusado honestamente (`IMAGE: avif frame obu tile group not covered`, a próxima fatia — o frame real é OBU_FRAME tipo 6). Fixtures tornadas spec-fiéis (`AvifFrameSupport.frameReduced`/`frameNr` removem os bits de interp intra, `error_resilient` de KEY agora é inferido; nova `intra.avif` INTRA_ONLY não-reduced; as recusas fabricadas `sizerefs`/`interp` removidas) e os dois segundos leitores (`AvifFrameJavaSupport`, `AvifGroupJavaSupport`) alinhados. Prova: `AvifFrameE2ETest` **16/16** e a bateria AVIF inteira **56/56** (JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu), JS `IOJS001`, concordância do segundo leitor). Próximo: tile group inline do OBU_FRAME → walk de payload/coeficientes do tile → loop filter → quantização → `decodeRaster` AVIF.
> **AVIF fatia 2k LANDADA 03/10 (Kof puro, CADEIA DO ITEM-DATA COM ARQUIVO REAL — `seq_profile` f(3) + `operating_point_idc` f(12) + LEB128 `obu_size`):** rodar a cadeia COMPOSTA do item (`readAvifItemObus`) no arquivo AVIF REAL (arquivo-host da fatia 2i) lançou `IMAGE: truncated avif sequence header`. Causa raiz: o `seqWalk` lia `seq_profile` como **f(2)**, mas AV1 §5.5.1 é **f(3)**; o profile errado selecionava o ramo de `color_config` do profile 0 e consumia dois bits espúrios de `chroma_sample_position`. A mesma varredura da §5.5.1 achou `operating_point_idc` pulado como **16 bits**, mas a spec é **f(12)** — invisível porque as fixtures fabricadas não-reduced e o segundo leitor compartilhavam a mesma largura errada (a lição do ponto cego compartilhado de 2h/2i/2j de novo). Após a correção do profile, o walk bateu em `IMAGE: avif obu truncated`: o `obu_size` era acumulado big-endian (`(size<<7)|…`), mas AV1 §4.10.5 é **LEB128 little-endian** (`value |= (b&0x7f) << (i*7)`), que só diverge para tamanhos ≥128 bytes — exatamente o OBU de frame real (42143 bytes). Corrigido nos seis sítios (`Avif.kf` `seqHeaderReduced`, `AvifSeq.kf` config-OBU + `seqWalk`, `AvifObu.kf`, `AvifFrame.kf`, `AvifGroup.kf`, `AvifMeta.kf` tamanho de OBU **e** `metadata_type`). **Prova com arquivo real:** `readAvifItemObus` agora retorna `total=3 seq=1 frames=1`, sequence header `480x410 depth=8 profile=1` — batendo com `ispe` 480x410, `av1C` profile 1 e `ffprobe yuv444p`; `readAvifMetadata` reporta `480x410 items=2 primary=1 alpha=true profile=1 depth=8`. Fixtures tornadas spec-fiéis (`seq_profile` f(3), `operating_point_idc` f(12)) em `AvifSeqSupport`/`AvifMetadataSupport`/`AvifFrameSupport` e nos três segundos leitores (`AvifSeqSupport.javaSeqCore`, `AvifFrameJavaSupport`, `AvifGroupJavaSupport`); novas fixtures reduced profile-1 `r1.avif` (a forma do arquivo real) e OBU >127 bytes `big.avif` (padding de 200 bytes e então metadata; escritor+leitor LEB128). Prova: `AvifSeqE2ETest` **8/8**, `AvifObuE2ETest` **8/8** (+`big`), `AvifFrameE2ETest` **16/16**, `AvifMetaE2ETest` **8/8**, `AvifItemsE2ETest` **8/8**, `AvifMetadataE2ETest` **8/8** — JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu), JS `IOJS001`, concordância do segundo leitor (bateria AVIF 56/56). Próximo: walk de payload/coeficientes do tile-group → loop filter → quantização → `decodeRaster` AVIF.
> **AVIF fatia 2j LANDADA 03/10 (Kof puro, CONFORMIDADE COM ARQUIVO REAL — seleção de propriedades por `ipma` + layout do record `av1C` + alpha `iref`/`auxl`; `libs/image/Avif.kf`, `AvifSeq.kf`):** dando continuidade à re-revisão `D-PLAN-ONE-OWNER` contra o ARQUIVO AVIF REAL (fatia 2i), rodar a cadeia COMPOSTA sobre ele revelou MAIS TRÊS bugs reais de container que as fixtures fabricadas não pegavam porque as fixtures e os leitores compartilhavam as mesmas suposições: (1) as propriedades do item primário eram lidas como "exatamente um `ispe` e um `av1C` em `ipco`" — o arquivo real tem DOIS `av1C` (cor + alpha) e dois `ispe`, então o leitor inteiro recusava `IMAGE: avif av1C property not covered`; a correção parseia `iprp/ipma` (ISO 14496-12 §8.11.4, `entry_count` **32-bit**, ids de item 16-bit na versão 0, bit essential por associação) e seleciona as propriedades do item PRIMÁRIO, e `AvifSeq.av1cPayload` compartilha a mesma seleção para que toda a cadeia AVIF pare de recusar arquivos com dois `av1C`. (2) O record `av1C` era modelado com um prefixo de 4 bytes `configOBUsLength` que NÃO EXISTE: AV1-ISOBMFF §2.3.3 é `AV1CodecConfigurationRecord` = 4 bytes fixos e então `configOBUs[]` direto (o record real é `81 21 00 00`, 4 bytes, array vazio); o prefixo de comprimento era um ponto cego compartilhado fixture/leitor. (3) O `iref` era modelado como FullBox com `entry_count` de topo e URN HEVC `auxc` inline; o arquivo real usa a lista de boxes-filho do ISO 14496-12 §8.11.12 com uma entrada `auxl` do item alpha para o primário, e o tipo alpha mora na URN da AuxiliaryTypeProperty `auxC` `urn:mpeg:mpegB:cicp:systems:auxiliary:alpha` (AVIF §4.1). As três sintaxes são citadas das fontes de spec lidas no host de dev (AV1-ISOBMFF v1.3.0 §2.3.3; AVIF v1.2.0 §4.1) e conferidas contra o FFmpeg (`mov_read_iprp`) e o arquivo real. Também honesto: `avifSeqHeader` agora recusa `configOBUs` VAZIO com `IMAGE: avif config obu absent` (uma imagem AVIF still legal carrega a sequence header no item data — `readAvifItemObus` é o caminho que a obtém), em vez de ler os bytes do item como se fossem o OBU de config. Prova RED-first: no arquivo real a lib pré-fix recusava em `readAvifMetadata` com `IMAGE: avif av1C property not covered` (medido); pós-fix reporta `avif 480x410 items=2 primary=1 alpha=1 profile=1 level=1 tier=0 mono=0 sub=0/0 depth=8/8`, batendo com o `ffprobe` (`yuv444p`, AV1 profile 1, 480x410). `AvifMetadataE2ETest` **8/8** (a fixture `alpha.avif` reescrita para a forma real dois-`av1C` + `auxl` + `auxC`; o segundo leitor Java agora parseia `ipma`/`auxl`/`auxC` de forma independente) e `AvifSeqE2ETest` **8/8** (nova recusa de `configOBUs` vazio) em JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu), JS `IOJS001`; a bateria AVIF inteira **56/56** inalterada. PRÓXIMO: a cadeia de decode AVIF (walk da sequence header no item data — a header real é reduced profile 0 mas o `seqWalk` diverge nela; walk de payload/coeficientes do tile-group), depois `decodeRaster` AVIF.
> **AVIF fatia 2i LANDADA 03/10 (Kof puro, `iloc` VERSÃO 0 + multi-extent + prova com ARQUIVO REAL — `libs/image/AvifItems.kf`):** re-verificando o estado da fatia 2a (re-revisão `D-PLAN-ONE-OWNER`) contra um arquivo AVIF REAL achado no host de dev (um `.avif` embarcado num bundle de extensão de navegador — a lacuna de fixture-host registrada em 01/10 está fechada para esta fatia) apareceu um DESALINHAMENTO real: o parser `iloc` da fatia 2a lia `item_count` no offset errado (um byte atrasado), pulava o byte index_size/reserved e punha `construction_method` em `(word>>5)&7` em vez do nibble baixo da spec. O leitor E o segundo leitor Java compartilhavam o mesmo off-by-one, então o oráculo da fatia 2a tinha um ponto cego (a lição da fatia 2h). A sintaxe agora é citada da ISO 14496-12 §8.7.4 e conferida contra DUAS implementações independentes lidas no host 03/10 (FFmpeg `mov_read_iloc` e o javadoc do `ItemLocationBox` do mp4parser, que carrega o texto da spec verbatim) E validada byte a byte contra o arquivo real. Mudanças: a VERSÃO 0 do `iloc` (item ids de 16 bits, sem palavra de construction) é decodificada em vez de recusada; a VERSÃO 1 mantém a palavra reserved(12)+construction(4) no offset correto; offset_size/length_size em {4,8} e base_offset_size em {0,4,8} são honrados como tamanhos de campo medidos (o arquivo real é offset=4/length=4/base=0); um item com MAIS DE UM extent é concatenado em ordem (AVIF §2.3); construction 0/1 inalterados, 2 recusado; v2 segue recusado. Prova RED-first: `AvifItemsE2ETest` **8/8** — cinco fatos de item (mdat 2 itens, idat, uma fixture v0/base-0 com a forma do arquivo real, uma concatenação de DOIS extents) em JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu), JS `IOJS001`, 4 recusas nomeadas, e o segundo leitor Java independente (reescrito spec-fiel) concordando fato-a-fato; a biblioteca pré-fix lê o `item_count` do arquivo real como 512 e recusa com `IMAGE: avif iloc item list not covered` (medido), e um parser Python spec-fiel confirma independentemente o item1 real [14723,56883) len=42160 e o item2 [430,14723) len=14293. Docs EN+PT: esta linha + a descrição da fatia 2a abaixo, CHANGELOG, status, README 0f. Sem mudança no compilador, sem novo gap, sem decisão necessária. PRÓXIMO: a cadeia de decode AVIF (travessia de payload/coeficientes do tile-group), depois `decodeRaster` AVIF.
> **AVIF fatia 1 LANDADA 01/10 (Kof puro, fatos de container — cadeia `D-WEBP-LOSSY-PURE-KOF`: 'AVIF segue após VP8'):** `libs/image/Avif.kf` lê o CONTAINER ISOBMFF/AVIF a partir de um prefixo limitado de 4 KiB — marca ftyp (`avif`/`avis`, principal ou compatível), caminho dos filhos de `meta` (full box), id primário `pitm`, contagem de itens `iinf` (v0), exatamente um `ispe` de dimensões + exatamente um registro de configuração `av1C` em `iprp/ipco` (AVIF §3.1.1): seq_profile/level/tier/high_bitdepth/twelve_bit→profundidades de bit (tabelas de índice AV1 §5.1), monochrome, subsampling (consistência monochrome verificada; consistência perfil/profundidade verificada), OBU de configuração = OBU_SEQUENCE_HEADER com forbidden bits zero, com campo de tamanho PRESENTE e o flag de forma REDUZIDA — formas não-reduzidas RECUSADAS explicitamente (`IMAGE: avif sequence header form not covered`; a travessia completa de campos não-reduzida é fatia própria posterior). Associação de alpha `iref`/`auxc` ao primário (`urn:mpeg:hevc:2015:auxid:1`), somente versão 0 (v1 = recusa explícita). Decodificação de pixels NÃO tocada: `decodeRaster` continua recusando AVIF. **Fixtures:** montadas byte-a-byte pela spec (nenhum codificador existe no host de teste: ffmpeg/avifenc/pip medidos AUSENTES 01/10; o .so libheif não tem headers para uma fixagem honesta de ABI — registrado como GAP DE FERRAMENTA da frente; goldens de arquivos reais pegam carona em fatia posterior com host de fixtures). **Oráculo:** um SEGUNDO leitor independente, escrito em Java puro na suíte, concorda byte-a-byte com a biblioteca Kof em cada fato (`secondJavaReaderAgreesWithKofLibrary`). Prova: `AvifMetadataE2ETest` **8/8** — goldens JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu), JS = recusa de compilação `IOJS001` (`readRange`), 5 mensagens de recusa explícitas, acordo do segundo leitor; `AvifMetadataSupport` carrega builders + segundo leitor (split do ratchet test-hygiene). Candidatas da próxima fatia (plano §34): sequence header não-reduzido + fatos completos de localização de item (iloc/idat), depois a cadeia intra de decode AV1. **Superada pela fatia 2j (03/10, acima): a regra de exatamente-um-`av1C`/exatamente-um-`ispe`, o alpha `iref`/`auxc` inline e o record `av1C` com prefixo de comprimento eram bugs de arquivo real — seleção por `ipma`, o layout de record de 4 bytes + `configOBUs[]` e o alpha `iref`/`auxl`+`auxC` são o comportamento fiel à spec agora.**
> **AVIF fatia 2a LANDADA 01/10 (Kof puro, LOCALIZAÇÃO DE ITEM — o mecanismo que toda fatia AVIF posterior precisa):** `libs/image/AvifItems.kf` extrai os bytes de UM item armazenado pela box ISOBMFF `iloc` (ISO 14496-12 §8.7.4): SOMENTE VERSION 1 (v0/v2 = recusas explícitas — o empacotamento de bits da v0 é medido contra arquivos reais na sua própria fatia), offset_size == length_size == 4, base_offset_size 4/8 (palta alta zerada), nibble/bit reservados + data_reference_index + index_size devem ser zero, construction_method 0 (absoluto no arquivo, ex. `mdat`) e 1 (relativo ao payload `idat`); 2 = recusado; o item alvo deve ter EXATAMENTE um extent (multi-extent = recusado, fatia própria); um único `idat` no máximo; o intervalo extraído deve viver dentro do prefixo limitado de 64 KiB (fora = recusa, nunca resposta truncada). `readAvifItemBytes(path, id)`/`avifItemBytes(bytes, id)`. Sem decodificação de pixels. Prova: `AvifItemsE2ETest` **8/8** — fatos de bytes por golden da spec (len/primeiro/último/soma) em JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu), JS = recusa de compilação `IOJS001`, 4 mensagens de recusa explícitas, e um SEGUNDO leitor Java independente (`AvifItemsSupport.readItemJava`) byte-a-byte em cada item; bateria de imagem `AvifMetadataE2ETest` 8/8 + `ImageMetadataE2ETest` 7/7 + `RasterDecodeE2ETest` 26/26 (1 skip de ambiente) inalterados. Próximas candidatas: travessia de campos do `OBU_SEQUENCE_HEADER` não-reduzido, iloc multi-extent/v0 contra arquivos de codificador reais (host de fixtures), depois a cadeia intra de decode AV1. **Superada pela fatia 2i (03/10, acima): os limites v1-only e um-extent eram um bug de offset do parser; v0 e multi-extent agora decodificam, e o parser é fiel à spec.**
> **AVIF fatia 2b LANDADA 02/10 (Kof puro, TRAVESSIA DE CAMPOS DO SEQUENCE HEADER — formas reduzida E não-reduzida):** `libs/image/AvifSeq.kf` percorre o `OBU_SEQUENCE_HEADER` completo — cada largura citada do PDF da spec AV1 lido linha a linha no host de dev (5.5.1/5.5.2, nunca de memória): profile(2)/still(1)/reduced(1); reduzida = só level(5); não-reduzida = ramificação de timing (tick/escala 64 bits, uvlc de igualdade de quadro consumido; decoder model info RECUSADO), atraso inicial de exibição, laço de pontos de operação (idc 16, level 5, tier quando level>7, atraso por ponto 4), bits de largura/altura + tamanho máximo de quadro, números de id de quadro (4+3), bloco de capacidades (128x128/filtro/aresta intra, bloco inter/ordem de hint/conteúdo de tela/MV inteiro), superres/cdef/restoration, color_config: profundidades high/twelve, monocromia, descrição de cor (8+8+8), subamostragem por perfil, posição de amostra de croma, delta-Q UV separado, flag de film grain. Recusas: perfil 3 (`IMAGE: avif sequence profile not covered`), decoder model info, film grain, OBU de configuração truncado. Envelope OBU: forbidden bits 0, tipo 1, campo de tamanho, tamanho LEB128 limitado pelo registro av1C. `readAvifSeqHeader(path)`/`avifSeqHeader(bytes)` -> record `AvifSeqHeader` (perfil, still, reduzida, tamanho máximo de quadro, mono, subamostragem, profundidade de bits, posição de croma, UV separado). Face de metadados de contêiner `Avif.kf` e `decodeRaster` inalterados. Prova RED-first: `AvifSeqE2ETest` **8/8** — goldens de fatos da spec reduzida + não-reduzida (perfil 0 8-bit 8x8; perfil 2 12-bit 32x24 com timing) em JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu), 3 mensagens de recusa, SEGUNDO leitor Java independente (`AvifSeqSupport.javaSeqFacts`) fato-a-fato nas três formas; bateria de imagem `AvifMetadataE2ETest` 8/8 + `AvifItemsE2ETest` 8/8 inalterada (`configObu` da fatia 1 reescrito exatamente pela spec: faltavam os bits de tamanho de quadro/capacidades da forma reduzida). Bugs que o RED-first pegou antes do verde: limite fim do OBU de configuração checado contra o cabeçalho da box em vez do registro; flag de extensão do OBU lida do byte de tamanho; identificador reservado `byte` em Kof; `separate_uv_delta_q` mal posicionado no caminho sRGB da cor. Próximas candidatas: travessia do fluxo OBU do item (tipos delimiter/frame sobre os bytes do item localizado), iloc multi-extent/v0 contra arquivos de codificador reais (host de fixtures), depois a cadeia intra de decode AV1.
> **AVIF fatia 2c LANDADA 02/10 (Kof puro, TRAVESSIA DO FLUXO OBU DO ITEM — enumeração, não decode):** `libs/image/AvifObu.kf` percorre os bytes do item AV1 primário (localizado pelas fatias 1+2a) OBU por OBU com cada regra citada do PDF da spec AV1 lido no host de dev (5.2/5.3.1/5.3.2/6.2): byte de cabeçalho forbidden(1)=0/tipo(4)/extensão(1)/presença-de-tamanho(1)/reservado(1)=0, cabeçalho de extensão 8 bits, `obu_size` LEB128 ("o tamanho em bytes do OBU não incluindo os bytes dentro de obu_header ou do elemento sintático obu_size", §6.2.2); fluxos de baixo overhead — a forma do item AVIF — EXIGEM o campo de tamanho em todo OBU (§5.2: "When using this format, obu_has_size_field must be equal to 1") — tamanho ausente é RECUSADO, nunca adivinhado; o fluxo deve COMEÇAR com um delimitador temporal; o primeiro sequence header é parseado pelo `seqWalk` da fatia 2b (função de intervalo extraída, comportamento idêntico); tipos contados pela tabela §6.2 (delimitador/seq/frame header/redundante/tile group/tile list/metadata/frame/padding/reservado — reservados 0/9..14 pulados por "shall be ignored by AV1 decoder" §6.2). Recusas: `IMAGE: avif obu truncated`, `IMAGE: avif item obu missing size field`, `IMAGE: avif item obu reserved bit set`, `IMAGE: avif item obu forbidden bits`, `IMAGE: avif item does not begin with a temporal delimiter`, `IMAGE: avif item has no sequence header`, `IMAGE: empty avif item`. SOMENTE ENUMERAÇÃO — payloads de frame/tile não são decodificados, `decodeRaster` continua recusando AVIF. `readAvifItemObus(path)` compõe contêiner→pitm→iloc→travessia; `avifItemObus(item)` é a face de bytes puros. Prova RED-first: `AvifObuE2ETest` **8/8** — goldens de fatos da spec de fluxo misto (4 OBUs delimitador+seq+metadata+padding; 8 OBUs cobrindo contagens de frame header/redundante/tile group/tile list/reservado/frame + fatos de seq reduzida e não-reduzida) em JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu), 6 mensagens de recusa, SEGUNDO leitor Java independente (`AvifObuSupport.javaObuFacts` + `AvifSeqSupport.javaSeqCore`) fato-a-fato; bateria de imagem `AvifMetadataE2ETest` 8/8 + `AvifItemsE2ETest` 8/8 + `AvifSeqE2ETest` 8/8 + `ImageMetadataE2ETest` 7/7 + `RasterDecodeE2ETest` 26/26 (1 skip de ambiente) inalterada. Bugs pegos pelo RED-first: um fixture trunc que prometia menos do que o item tinha leu zeros em vez de recusar (fixture corrigido para tamanho além do fim); dedup de `Bits` pelo ratchet. Próximas candidatas: travessias de payload do frame-header/tile-group (a cadeia intra de decode AV1), iloc multi-extent/v0 contra arquivos de codificador reais (host de fixtures).
> **AVIF fatia 2d LANDADA 02/10 (Kof puro, PREFIXO DO FRAME HEADER — parseia-e-parada, não decode):** `libs/image/AvifFrame.kf` parseia `frame_header_obu`/`uncompressed_header` (spec AV1 §5.9.2, citada do PDF lido no host de dev) sobre os bytes do item localizado, com o contexto que o `seqWalk` da fatia 2b agora expõe no `AvifSeqHeader` (larguras de frame-id, bits de order-hint, larguras de bits de tamanho de quadro, marcadores SELECT de screen-content/MV inteiro): show_existing_frame, frame_type, show/showable, error_resilient (forçado a 1 num KEY exibido), disable_cdf_update, allow_screen_content_tools/force_integer_mv (SELECT vs forçado pelo seq), current_frame_id (largura delta+additional+3), frame_size_override_flag, order_hint, refresh_frame_flags + laço ref_order_hint, tamanho codificado (larguras `wBits+1`/`hBits+1` do override), recusa de superres, render_size (o flag de 5.9.6 NÃO tem guarda reduced — medido e corrigido na travessia) e o ponto de parada allow_intrabc. Recusas (nomeadas, nunca silenciosas): `avif frame show-existing not covered`, `avif inter frame not covered`, `avif intra block copy not covered`, `avif frame size-with-refs not covered`, `avif superres not covered`, `truncated avif frame header`, `avif item has no frame header`. `readAvifFrameHeader(path)` compõe contêiner→pitm→iloc→enquadramento OBU→esta travessia; `avifItemFrameHeader(item)` é a face de bytes puros; `AvifFrameHeader` informa tipo de quadro, flags show/error-resilient, override de tamanho e tamanhos codificado/render. SOMENTE METADADOS — tile info, loop filter e quantização após allow_intrabc correm nas fatias de decode; `decodeRaster` continua recusando AVIF. Prova RED-first: `AvifFrameE2ETest` **8/8** — goldens de key reduzido, KEY não-reduzido com override (codificado 5x3 vs máximo 8x8) e render-size em JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu), JS `IOJS001`, 6 mensagens de recusa, SEGUNDO leitor Java independente (parse completo do prefixo 5.5/5.9 em `AvifFrameSupport.javaFrameFacts`) fato-a-fato nos goldens E nas seis recusas; bateria de imagem inalterada; suíte completa do kof-compiler 4361/0F. Bugs pegos pelo RED-first: um rascunho lia os bits de tamanho antes do order-hint/refresh e tratava render_size como pulado no reduzido (a spec não tem essa guarda); a largura do frame-id era 4 fixo em vez de delta+additional+3 do seq; o leitor Java dessincronizava no bit de atraso por ponto de operação. Próximas candidatas: travessia do payload tile-group (a cadeia intra do AV1), iloc multi-extent/v0 contra arquivos reais de codificador (host de fixtures).
> **AVIF fatia 2e LANDADA 02/10 (Kof puro, EXTENSÃO TILE_INFO — contagens de espaçamento uniforme, ainda sem decode):** a travessia do frame da fatia 2d segue agora pelas flags 5.9.10 e pelo caminho uniforme de `tile_info()` (5.9.15): `is_filter_switchable` (seleção de filtro no quadro recusada como `avif frame interp not covered`), `is_motion_mode_switchable`, `disable_frame_end_update_cdf` (lido só quando o prefixo permitiu), o flag uniforme, os laços `increment_tile_cols_log2`/`increment_tile_rows_log2` (o BREAK da spec mantém o log2 atual — uma fixture inventava bits de parada além do teto e o harness pegou), `context_update_tile_id` + `tile_size_bytes_minus_1` (f(2)) quando algum log2 é > 0. Espaçamento não-uniforme é recusado por nome (`avif tile size list not covered`) — as tabelas `ns()` por tile são fatia própria. `AvifFrameHeader` ganha `tileColumns`/`tileRows` (contagens derivadas da grade de superblocos); `AvifSeqHeader` amplia o contexto do frame capturando `use128x128Superblock` e `enableRefFrameMvs` como valores (a travessia consome os mesmos bits de antes — as suítes 2b/2c/2d permaneceram verdes). A parada documentada de `frameWalk` passa a ser o fim do tile_info; filtro de laço/quantização/coeficientes correm nas fatias de decode. Prova RED-first: `AvifFrameE2ETest` **8/8** — os goldens agora trazem contagens exatas de tiles (32x32 uniforme 1x1; 128x128 com dois incrementos de coluna e linha 2x2), JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu), JS `IOJS001`, a lista de recusas cresce para 8 mensagens nomeadas, e o SEGUNDO leitor Java independente concorda fato-a-fato nos goldens E nas oito recusas (ele divergiu três vezes reais durante o laço: um avanço faltante de allow_intrabc, um avanço duplicado do uniforme e bits de parada escritos além do teto — cada uma pega pelo teste de concordância antes do verde). Bugs pegos pelo RED-first aqui: o rascunho lia o bit de motion duas vezes; o tile uniforme 1x1 com sbCols=1 NÃO lê incremento algum (max=0) e a fixture precisou de um sequence header 128x128 para exercitar multi-tile real. 
> **AVIF fatia 2f LANDADA 02/10 (Kof puro, CABEÇALHO DO TILE-GROUP — tamanhos em bytes dos tiles sem decode, `libs/image/AvifGroup.kf`):** o prefixo do cabeçalho tile_group_obu() por 5.11.1 + as regras de conformidade 6.10.1, em arquivo próprio (divisão por responsabilidade): o flag de faixa quando NumTiles>1, tg_start/tg_end f(tileBits), `byte_alignment()` (bits zero até a próxima fronteira de byte, 5.3.5), o le(TileSizeBytes) por tile não-final com o ÚLTIMO tile ficando com os bytes restantes do payload. Recusas nomeadas para cada violação de conformidade: faixa completa com flag, faixa invertida, tg_start fora de ordem (regra `tg_start == TileNum`), último grupo que não termina em NumTiles-1, OBU_TILE_LIST (8), cabeçalho de quadro redundante (7), truncagem em qualquer ponto — e um limite honesto: um OBU_FRAME (6) põe seu grupo embutido DEPOIS do uncompressed_header completo (quantização/segmentação/filtro de laço entre tile_info e o fim dependem de valores da cadeia de decode), então tipo 6 é recusado (`avif frame obu tile group not covered`), nunca com offset chutado. `AvifFrameHeader` ganhou tileBits/tileSizeBytes/headerBytes (o tileWalk agora CONSOME o valor de tile_size_bytes — fiel à spec); `AvifTileGroup(tgStart, tgEnd, tileCount, tileSizes, lastTileSize, totalBytes)`; `avifItemTileGroups(item)` + `readAvifTileGroups(path)`. Prova RED-first: `AvifFrameE2ETest` **16/16** — goldens `one 0..0 n=1 sizes= last=2 total=2` + `split 0..1 n=2 sizes=,5 last=3 total=8` / `split 2..3 n=2 sizes=,4 last=2 total=6` em JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu), JS `IOJS001`, 8 recusas nomeadas, e o segundo leitor Java independente (`AvifGroupJavaSupport`, travessia 5.9/5.11 escrita do zero com captura própria de tileBits) concordando fato-a-fato nos goldens E nas oito strings de recusa. Bugs que o laço de concordância pegou: o writer da fixture emitia só o campo de tamanho SEM os bytes de payload por tile (os dois leitores recusaram trunc identicamente → bug do writer, prova de que as duas implementações estavam vivas), e a fixture trunc com reivindicação de 200 bytes estourava o campo de tamanho OBU de 1 byte (codificada à mão). Nota da suite: os 8 vermelhos `NETN001` de JS-net medidos numa base de rebase MAIS ANTIGA já estavam corrigidos upstream por `6395dbafc` (voto (c) D-NET-JS-V1); §567 fechado no mesmo dia com prova verde na ponta (`KofNetTest` 4/4, `NetJsV1RefusalE2ETest` 3/3, `ConformanceMatrixTest` 14/14). Próximo: iloc multi-extent/v0 contra arquivos reais (host de fixtures), travessia do PAYLOAD/coeficientes do tile-group (cadeia de decode), depois decodeRaster AVIF.
> **AVIF fatia 2g LANDADA 02/10 (Kof puro, OBU DE METADADOS — travessia do payload de OBU_METADATA, `libs/image/AvifMeta.kf`):** metadata_obu() por 5.8.1–5.8.4 + a tabela metadata_type 6.4.1 lida do corpus de spec no host de dev (0 reservado para uso da AOM, 1 hdrCll, 2 hdrMdcv, 3 scalability, 4 itutT35, 5 timecode, 6–31 private não registrado, 32+ reservado para uso da AOM — a tabela é o contrato, memória não; um primeiro pouso tinha trocado 4/5 e rotulado 32+ como user private, corrigido 02/10 na fatia 2h): metadata_type em leb128; T35 = país f(8) + byte de extensão opcional quando país==0xFF + contagem crua de bytes do payload (a regra de conteúdo "último byte não-zero" NÃO aplicada — contagem crua honesta); CLL = max_cll/max_fall f(16) números exatos; MDCV = 8×f(16) de cromaticidades + 2×f(32) de luminâncias como dez valores; os tipos scalability e private/reservado são ENUMERADOS com nome + tamanho de payload, sem travessia da sub-sintaxe (faces de vídeo estruturadas/opacas — fronteira documentada, nunca chutada). Um tipo desconhecido nunca recusa (enumeração é a política); só leituras truncadas lançam `IMAGE:`. `AvifObuMetadata(type,name,payloadBytes,country,extended,t35Bytes,maxCll,maxFall,mdcv)`; `avifItemMetadataObus(item)` + `readAvifMetadataObus(path)` reutilizam o framing OBU das fatias 2a/2c e u8/be16/be32 de `Avif.kf` (o arquivo novo importa os auxiliares do pacote em vez de redefini-los). Prova RED-first: `AvifMetaE2ETest` **8/8** — goldens sobre as fixtures t35/t35x/cll/mdcv/mixed (o stream mixed traz OBUs metadata T35 + scalability + private não registrado e um OBU de padding no meio) em JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu), recusa JS `IOJS001`, 4 recusas honestas de truncamento, e um segundo leitor Java independente (`AvifMetaJavaSupport`) concordando fato-a-fato INCLUINDO as strings de recusa. O laço de concordância pegou uma fixture errada: cromaticidade y=71241 excede f(16) (os dois leitores enrolaram para 5705 identicamente → bug da fixture, corrigido para 60000). Nota da suite: o vermelho `NetJsE2ETest` que reapareceu era uma classe compilada DEFASADA no `target/test-classes` compartilhado depois que o upstream `6395dbafc` renomeou a fonte para `NetJsV1RefusalE2ETest` — condição ambiental, purgada; testes de rede verdes 4/4 + 3/3 na árvore atual. Próximo: fatia 2h = metadata_timecode() (campos de bit 5.8.7 + semântica 6.7.7) em `libs/image/AvifMeta.kf` reusando o bit-reader da 2d; scalability(3) permanece nome+tamanho (scalability_structure é sintaxe profunda de vídeo — fronteira separada); depois iloc multi-extent/v0 contra arquivos reais de codificador (host de fixtures), depois a travessia de payload/coeficientes do tile-group (cadeia de decode), depois decodeRaster AVIF.
> **AVIF fatia 2h LANDADA 02/10 (Kof puro, TIMECODE DE METADADOS — travessia de `metadata_timecode()` + correção da tabela 6.4.1, `libs/image/AvifMeta.kf`):** a tabela metadata_type foi medida errada no pouso da fatia 2g (4/5 trocados, 32+ rotulado como user private); o contrato foi relido da fonte da spec AV1 (`07.bitstream.semantics.md`) E conferido contra o enum `OBU_METADATA_TYPE_*` do `aom/aom_codec.h` (0 reservado para uso da AOM, 1 hdrCll, 2 hdrMdcv, 3 scalability, 4 itutT35, 5 timecode, 6–31 private não registrado, 32+ reservado para uso da AOM) — as duas fontes independentes concordam. O payload timecode (sintaxe 5.8.7 + semântica 6.7.7) agora é percorrido: counting_type f(5), full_timestamp_flag f(1), discontinuity_flag f(1), cnt_dropped_flag f(1), n_frames f(9); quando full_timestamp_flag está setado, seconds f(6)/minutes f(6)/hours f(5); senão os flags de seconds/minutes/hours portão os valores (um valor ausente é reportado como -1 — o “inferido do conjunto anterior” da spec é estado de nível de stream que esta travessia só-metadados não carrega); depois time_offset_length f(5) e, quando > 0, time_offset_value f(time_offset_length), tudo lido MSB-first com o bit-reader `seqRead` da 2d. `AvifObuMetadata` ganha `timecode: List<Int>` ([countingType, fullTimestamp, discontinuity, cntDropped, nFrames, seconds, minutes, hours, timeOffset]). Prova RED-first: `AvifMetaE2ETest` **8/8** — duas fixtures novas (`tc`: timestamp completo, counting 3, n_frames 24, 23:59:45, offset 0; `tcf`: timestamp parcial, todos os flags 0, time_offset_length 4/offset 9) mais as fixtures T35 corrigidas (tipo 4) e o stream mixed (agora t35(4)+scalability(3)+private(7)+reservado(32)) em JVM + Script + Native x86-64 + riscv64(qemu) + aarch64(qemu), JS `IOJS001`, 5 recusas honestas de truncamento (incl. payload timecode de 2 bytes), e o segundo leitor Java independente concordando fato-a-fato nos goldens E nas recusas. A biblioteca pré-fix compila o probe como `SEM025 Cannot resolve field 'timecode'` (medido); um revert só-da-tabela imprimiria `4 timecode` e falharia o golden. Bugs que a correção pegou: a tabela do primeiro pouso estava errada E o laço de concordância tinha um ponto cego compartilhado (os dois leitores carregavam a mesma tabela errada), por isso 56/56 estava verde — as fixtures agora se ancoram na spec, não numa suposição compartilhada.


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
- decode cru: PNM `P5`/`P6`, farbfeld, BMP 24/32-bit, **QOI** (todos os chunks), **TIFF** (ambas as ordens de byte, 8 bits grayscale/RGB/RGBA/gray-alpha/paleta, multi-strip, PackBits);
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
   e o aarch64 agora decodificam a mesma fixture corretamente — §544 CORRIGIDO 03/10
   (dona = lane native/GC `192.168.15.101:9092`, issue #700); os testes cross são
   `#largeWebpOnNativeRiscv64Decodes` / `#largeWebpOnNativeAarch64Decodes`.

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

23. **Transparência `tRNS` do PNG — LANDADA 01/10 (Kof puro, todos os alvos).**
    O `libs/image/Png.kf` agora lê o chunk `tRNS` (antes ignorado). Para cinza
    (tipo de cor 0) a saída passa a cinza+alpha com alpha 0 onde a amostra é
    igual à chave cinza do tRNS (senão 255); para RGB (tipo 2) passa
    a RGBA com alpha 0 no casamento exato da cor-chave (senão 255);
    para paleta (tipo 3) passa a RGBA com o alpha por cor, entradas além do
    comprimento do tRNS ficando opacas. Um `tRNS` em tipo de cor que não carrega
    transparência (4/6) é recusado com diagnóstico `IMAGE:` explícito. O
    componente tRNS é sempre um valor de 16 bits big-endian (spec PNG §11.3.2)
    independente da profundidade de bits, então a chave é mapeada para o mesmo
    espaço de 8 bits que as amostras decodificadas usam (`pngTrnsKey`): o byte
    alto para imagens de 16 bits (a regra do `pngUnpack16`), um escalonamento
    `255/maxval` para cinza sub-byte, e o próprio byte para 8 bits — uma amostra
    de 16 bits que compartilha o byte alto com a chave mas não o baixo permanece
    opaca. As fixtures são um PNG cinza 12x6, um RGB 10x5, um paleta 11x7, um
    cinza 16-bit 4x1, um RGB 16-bit 4x1 e um cinza 4-bit 4x2, cada um lido de
    forma independente pelo PIL e pelo `ImageIO` do Java. Prova:
    `PngTransparencyE2ETest` **4/4** (soma das amostras + hash rolante de 24
    bits) na JVM + Native x86-64 + riscv64(qemu) + Script, RED-first (o decoder
    pré-fatia ignorava o `tRNS`, medido; a chave ciente de profundidade foi
    adicionada após o primeiro pouso medir alpha errado nas fixtures de 16 bits e
    sub-byte); vizinhos `RasterDecodeE2ETest` PNG 4/4,
    `PngInterlaceE2ETest` 4/4 e `PngBitDepthE2ETest` 4/4 inalterados.

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
permanece uma recusa explícita `IMAGE:`, não um meio-decode. A cadeia de decode
AVIF está em andamento: fatia 3a LANDADA 04/10 (`libs/image/Av1Symbol.kf`, o
range decoder de símbolos/entropia AV1 §9.2/§9.3, provado contra o
encoder/decoder da libaom em seis fixtures e no payload real do tile do `.avif`
do host) e fatia 3b LANDADA 04/10 (`libs/image/Av1Tx.kf`, o descritor de
transformada — tabelas de tamanho, as 19x3 ordens de scan geradas e pinadas
contra `av1_scan_orders` da libaom, as classes/conjuntos e `compute_tx_type`) e
fatia 3c LANDADA 04/10 (`libs/image/Av1CoeffCdf.kf`, as 13 tabelas de CDF
default de coeficientes selecionadas por `COEFF_CDF_Q_CTXS`, todos os 15.996
números conferidos contra `token_cdfs.h` da libaom) e fatia 3d LANDADA 04/10
(`libs/image/Av1CoeffCtx.kf`, a seleção de contexto de coeficientes —
`get_coeff_base_ctx`/`get_coeff_br_ctx` e as regras `all_zero`/`dc_sign`, a
tabela 2D de offset conferida contra `av1_nz_map_ctx_offset` da libaom) e fatia
3e LANDADA 05/10 (`libs/image/Av1Coeffs.kf`, o walk de coeficientes do tile
`coeffs()` — `all_zero`, o token EOB `eob_pt_*`/`eob_extra`, o laço de níveis
`coeff_base_eob`/`coeff_base`/`coeff_br`, o sinal e a fuga `read_golomb`, e o
store de CDFs adaptadas por tile `Av1CoeffCdfStore`; as 40 fixtures de blocos
fazem round-trip num encoder/decoder de entropia fiel à libaom e os níveis
batem com um segundo leitor Java independente e com o oráculo Python) e fatia 3f
LANDADA 05/10 (`libs/image/Av1Quant.kf`, o estágio de dequantização AV1 §7.12.2 —
`Dc_Qlookup`/`Ac_Qlookup`, `dc_q`/`ac_q`, a regra `dqDenom` e o laço de
dequantização do passo 1 sobre a raster efetiva `Min(32,w) × Min(32,h)`; todos
os 1536 números das tabelas conferidos contra `quant_common.c` da libaom e 19
blocos dequantizados pinados contra um segundo leitor Java independente) e fatia
3g LANDADA 05/10 (`libs/image/Av1InvTx.kf`, a transformada inversa AV1 §7.13 — a
tabela `Cos128_Lookup[65]`, as borboletas da §7.13.2.1 `av1InvB`/`av1InvH`, DCT
inversa/ADST/WHT/identidade, e a 2D `av1InvTx2d` da §7.13.3 com
`Transform_Row_Shift`, os clamps `BitDepth+8`/`Max(BitDepth+6,16)`, o
pré-escalonamento `Abs(log2W-log2H)==1`, o caminho WHT lossless e o mapeamento de
tipo de transformada linha/coluna; os flips da §7.12 ficam com o chamador; o
golden é um harness de transformada inversa da libaom com os flips desligados
sobre todas as 579 combinações válidas tamanho×tipo×depth, zero divergências) e
fatia 3h LANDADA 05/10 (`libs/image/Av1Lf.kf`, o loop filter AV1 §7.14 — o
processo de tamanho de filtro da §7.14.3, a força de filtro adaptativa da
§7.14.4/§7.14.5 (`MAX_LOOP_FILTER`=63, `SEG_LVL_ALT_LF_*`, o escalonamento
`nShift` de `ref_deltas`/`mode_deltas`) e o sample filtering da §7.14.6
(`hevMask`/`filterMask`/`flatMask`/`flatMask2`, o narrow `filter4` e o wide 8/16
com `n2`; os kernels reais `aom_lpf_*` da libaom são o oráculo sobre 240 casos de
amostra, mais `get_filter_level`/`update_sharpness` da libaom para 300 casos de
força) e fatia 3i LANDADA 05/10 (`libs/image/Av1Recon.kf`, a reconstrução AV1
§7.11.4 — as derivações `flipUD`/`flipLR`, `av1ReconAddPred` com
`xx = flipLR ? w-j-1 : j`, `yy = flipUD ? h-i-1 : i` e `Clip1( pred + residual )`,
e `av1Reconstruct` compondo `av1InvTx2d` com a soma; o oráculo é uma cópia de
`inv_txfm2d_add_c` da libaom com os flips LIGADOS sobre todas as 579 combinações
válidas tamanho×tipo×depth, zero divergências) e
fatia 3j LANDADA 05/10 (`libs/image/Av1Intra.kf`, a predição intra base AV1
§7.11.2 — os preditores DC/V/H/PAETH/SMOOTH/SMOOTH_V/SMOOTH_H com as tabelas
`Sm_Weights_Tx_*`, `av1IntraPred` despachando-os e recusando um modo direcional
3..8 com diagnóstico `IMAGE:` explícito; o oráculo são os kernels REAIS
`aom_dsp/intrapred.c` da libaom sobre cada tamanho de transformada, os três bit
depths e as quatro combinações `haveAbove`/`haveLeft`, zero divergências);
a seguir vem a predição intra direcional §7.11.2.4 com seu edge filter/upsample
(fatia 3k), depois o `decodeRaster` AVIF (ainda recusado até a cadeia fechar). A face nativa cross da
fatia do walk de coeficientes está limitada pela known-bugs §602 (um frame único
grande dispara o GC riscv64/aarch64; a prova quebra o walk num helper por bloco e
uma função por tile).

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
