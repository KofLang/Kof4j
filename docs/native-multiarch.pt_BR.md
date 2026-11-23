[English](native-multiarch.md) | [Português](native-multiarch.pt_BR.md)

# Kof Native — Multi-Arch (RISC-V 64 e ARM64/AArch64)

> **✅ PROMOVIDO p/ `docs/` 19/09 (§5 passo-8; ordem da mantenedora "assume
> native-multiarch e termina") — NATIVE002 FECHADO.** As cinco faces de
> implementação estão todas fechadas: (1) cross GC mark-sweep G-0..G-6(a)
> FEITO 15/09 (auto-collect x86 G-6(a) 19/09; a face worker-stack-scan está
> catalogada, contrato main-only); (2) DB001 + CONC001 cross FECHADOS 15/09;
> (3) FP cross (FLT001, fatia `RtB45`) + §107 record/aninhado FECHADO 19/09
> NAS 3 ARCOS (x86 descritor recursivo `.rodata` face (3) + port cross face
> (4): a fatia `B39` interpreta a MESMA gramática de descritor — 8=toString
> na vtable via `jalr`, 9/10=containers aninhados, null→"null";
> `NativeRiscv64E2ETest`/`NativeAarch64E2ETest` 46/46 cada sob qemu, golden =
> o oracle JVM medido; aarch64 herda via tradutor c/ o novo `lhu`→`ldrh`);
> (4) colunas por-arch FECHADAS 19/09 — seção "Native per-arch" em
> `docs/backend-parity.md`(+PT), 16 domínios × 3 arcos; (5) CI cross FECHADO
> 12/09 — job `cross-native` roda as duas suítes E2E sob qemu. **As recusas
> restantes por domínio são códigos de gap HONESTOS, não trabalho pendente
> deste doc:** SECN000 (crypto asm — non-goal R11),
> JSN004 (`json.decode<Record>` em asm puro), RNG001 (rng cross),
> `kof.ui` (sem port cross) — cada uma diagnosticada em compile time, nunca
> silenciosa (R6), rastreadas no `docs/bugs-and-gaps/known-bugs.md` + seção
> por-arch do `docs/backend-parity.md`. O histórico de construção abaixo fica
> verbatim como registro de implementação.

> **🔄 RE-AUDIT 12/09 (measured under REAL qemu on this host — NOT memory):**

> **🔄 RE-AUDITORIA 12/09 (medido sob qemu REAL neste host — NÃO memória):**
> os cabeçalhos 03/09 abaixo estão DESATUALIZADOS e este bloco é a fonte do
> estado REAL (regra AGENTS "auditar doc contra o código/testes, não contra a
> memória"; estado-4 "doc contradiz o código" corrigido). As 13 faces "core"
> de 03/09 são hoje ~30 testes cross (`NativeRiscv64E2ETest` 39/39 +
> `NativeAarch64E2ETest` 39/39, **executados**, 0 skip neste host). **FECHADO
> e EXECUTANDO sob qemu (byte-idêntico ao JVM medido), para além do "core":**
> Map/Set, `println(<coleção>)` (§107 — x86 `f3b3821c` + cross B39 12/09),
> higher-order `map/filter/reduce` (probe 12/09: `[2,4,6]`/`[2,3]` idêntico
> JVM), HTTP client (`riscv64HttpGetPostStatus`), `kof.net`, JSON
> encode/decode int/list/string, spawn/await (`clone`+`futex`), `time` ISO
> (add/diff), `math` Double (MATH001) + **`math.pow` S1b.2 (decisão 7a 13/09:
> x86 via libm `pow@PLT` + `-lm`; riscv/aarch recusam com MATH001 — link
> cross estático sem libc, decisão de arquitetura regra 6; `KofMath.supportedOn`),
> `random`, `uuid`, multi-dim array
> (§113), busca String UTF-16 (§43/§102/§111). **ABERTO — recusa HONESTA em
> compile-time (NUNCA binário mudo; regra R6 — o stub "exit 0 sem efeito" de
> 03/09 já NÃO descreve mais o estado, ops desconhecidos dão código de gap):**
> `kof.db` → **DB001 FECHADO 15/09 no riscv64/aarch64** (link-by-use `libsqlite3` +
> runtime `kof_db_*` nas fatias `RtB46/RtB47`; `KofDbE2ETest.crossNativeSqliteRoundtrip`
> prova o caminho completo sob qemu nas duas arches; JS mantém `DB001`), `kof.security` crypto-heavy → **SECN000**, o
> `supervisor` de concorrência → **FECHADO 19/09** (port do §129: a EH chain é
> por-TID via `kof_exc_slots`, `OTP001` removido; os próprios
> `selectAny`/`done`/`poll`/`cancel`/`cancelled`/`awaitTimeout` **FECHADOS
> 15/09**: fatia `RtB48` + trampolim do spawn registrando o cancel slot, TID
> real via gettid(178) gravado pelo KERNEL no ctid do clone,
> `KofConcurrency2Test.crossNative*` sob qemu nas duas arches), UI (`kof.ui`) **sem port cross
> algum** (nenhum teste riscv/aarch), `json.decode<List<Record>>` → **JSN004**
> (asm puro não tem reflection p/ materializar record). **Consequência honesta
> HOJE:** programa com coleção/HTTP/net/JSON-escalar/spawn/time/math **roda de
> verdade** no riscv/aarch (a frase 03/09 "não executa a lógica — sai 0 sem
> efeito" está SUPERADA); o que ainda não roda (DB/segurança/UI/record-decode)
> é diagnosticado com código de gap em compilação, não silenciosamente.
> **Gap real `NATIVE002` que sobra:** (1) GC mark-sweep cross (riscv é
> bump-pointer sem coletor — vazamento em heap longo, não-crash) —
> **G-0 bloco-header + G-1 free-list/memstats + G-2 gc-list/dump + G-3 mark
> conservative + G-4 sweep/collect FEITOS 15/09** (ver a decomposição abaixo);
> G-5 aarch64 satisfeito pelo G-4 (as 2 arches rodam a prova do sweep);
> o coletor (G-4) é quem de fato recupera; (2) a face cross do DB001
> FECHADA 15/09 (SQLite; JS mantém DB001) e os helpers do CONC001
> (selectAny/done/poll/cancel/cancelled/awaitTimeout) FECHADOS 15/09
> e `channel<T>` send/receive FECHADO 23/09 (fatia `RtB61`, §423) — restam as recusas SECN000/JSN004,
> `kof.io` File/Path/Directory → **NAT006** e web-T1 `listen`/`route` →
> **NAT007** (§427, 21/09; recusa honesta em compile-time — os runtimes x86_64
> nunca foram portados para o cross) (OTP001 removido 19/09); (3) FP-coleção no cross
> (FLT001 FECHADO 15/09 — fatia `RtB45`; §107 record/aninhado **FECHADO 19/09
> NAS 3 ARCOS** — x86 descritor recursivo `.rodata` (face (3) x86) + port
> cross (face (4)): a fatia `B39` interpreta a MESMA gramática de descritor
> (0..7 legados + 8=toString na vtable via `jalr`, 9/10=containers aninhados,
> null→"null"), o call-site emite o nó `.rodata` (`NativePrintDescriptors`,
> compartilhado c/ x86), aarch64 herda via tradutor (novo `lhu`→`ldrh`);
> `NativeRiscv64E2ETest`/`NativeAarch64E2ETest` 46/46 cada sob qemu com o
> MESMO golden do oracle JVM do x86 (incl. `[Point[x=1, y=2]]`,
> `{k=Point[x=7, y=8]}`, `[[[4]]]`, `"rec:" + Point(5,6)` — a face de concat
> exigiu ramo `valueOf(record)` vtable no `NativeRiscvCrossOps`); (4) `backend-parity.md` colunas por-arch
> **FECHADAS 19/09** — seção "Native per-arch (x86_64 · riscv64 · aarch64)"
> em `docs/backend-parity.md`(+PT); (5) CI cross não existe (toolchain host-dependente) —
> **face (5) FECHADA 12/09**: job `cross-native` em `.github/workflows/ci.yml`
> instala `binutils-riscv64/aarch64-linux-gnu` + `qemu-user-static` e roda
> `NativeRiscv64E2ETest,NativeAarch64E2ETest` (executam sob qemu, não skipam —
> o job EXISTE para provar; nomes dos binários batem com `NativeArchEmitter:151
> -282`; local: riscv 39/39 + aarch 39/39 verdes neste host com qemu).
> **➕ Link dinâmico SOB DEMANDA FEITO 15/09** (diretriz "liga dinamicamente"):
> `NativeCrossLink` liga libc (`-dynamic-linker … -lc`) só quando o runtime
> podado chama libc; os 84 binários cross atuais seguem estáticos/portáveis.
> **➕ 1º consumidor de produção FEITO 15/09**: `RuntimeDtoa` portado ao runtime
> cross como fatia `RtB45` (`snprintf`/`strtod`), fechando o **FLT001**. Ver §2.3.
> ~~Este doc continua em `development/` (NATIVE002 não fecha enquanto restam
> (1)–(5)); quando (1)–(5) zerarem → mover para `docs/`.~~ **SUPERSEDED 19/09:
> (1)–(5) todos fechados (bloco de promoção acima) — doc movido p/ `docs/`.**
>
> **🪜 DECOMPOSIÇÃO DA FACE (1) — GC mark-sweep cross (12/09, fila para
> execução por degrau — cada degrau cabe numa sessão e tem prova própria):**
> o riscv é bump puro (`amoadd.d` em `kof_alloc_ptr`, sem flags/mark/free-list);
> o port NÃO é copiar o RuntimeGc x86 — o scan conservative exige stack-walk
> riscv + roots no intervalo de seções. **⚠️ CORRIGIDO 12/09 (lido o código,
> não memória) — a decomposição original estava ERRADA no G-1:** o riscv não
> tem NENHUM header de bloco: `kof_alloc` riscv (`NativeRiscvAsmRt0.java:17-22`)
> retorna o bump cru alinhado a 16 e os usuários escrevem o header do OBJETO
> (typeId @0, vtable @8, …) no OFFSET 0 do ponteiro retornado (ex.:
> `string_from_literal`: `sw t0, 0(s3)`), enquanto no x86 o GC vive num bloco
> de 32B ANTERIOR ao ponteiro retornado (`RuntimeMemory.java:145-150` — size@0,
> free-next@8, gc-next@16, flags@24; `kof_free:205` lê `-32(%rdi)`). Portar a
> free-list sem o bloco-header = o coletor ler o typeId como tamanho →
> corrupção. Por isso entra o **G-0** na frente. Degraus na ordem:
> **G-0 bloco-header riscv (FEITO 12/09, sessão dev):** `kof_alloc` riscv reserva
> 32B ANTES do ponteiro (total = 32+align16, retorno base+32; preenchimento
> size/free_next/gc_next/flags) + guard OOM honesto (`_kof_heap_end`, panic
> `out of memory` exit 1 — R6: o bump NÃO tinha bounds-check e o header
> triplica o consumo/bloco, então o estouro ficou mais provável). Prova:
> suíte riscv 40/40 + aarch 40/40 sob qemu (inclui teste de pressão
> `riscvHeapExhaustionPanicsHonest`/`aarch64HeapExhaustionPanicsHonest`,
> sabotagem-sem-guard = zero output FAIL) + GC x86 3/3 + Artifact 6/6 +
> ratchet ≤500 OK (Rt0 com 500 exatas; prosa de design vive aqui).
> **G-1 free-list riscv (FEITO 15/09, sessão dev):** port da lista de blocos
> livres do x86 SOBRE o layout do G-0 (header 32B:
> size/flags/gc-list/free-next). Fatia nova `NativeRiscvAsmRtB42` com
> `kof_alloc` (SAIU de `Rt0`, que caiu 500→478 — a folga do ratchet),
> `kof_free` e `kof_memstats`; o alloc agora faz first-fit na free list (LIFO,
> re-enfileira no alloc, `flags=0`) e só usa o bump no miss; um spin-lock
> `amoswap.w` protege a free list + contadores (main × workers do spawn).
> `kof_free` é port 1:1 do `RuntimeMemory.emitFree` (flags bit1 = na free
> list). `kof_memstats` imprime `allocs`/`frees`/`live bytes` — a alavanca de
> observação dos degraus seguintes. Prova (qemu riscv64 **e** aarch64,
> toolchain presente, testes não skipam): `NativeRiscvGcFreeListTest` monta o
> runtime de PRODUÇÃO (`RiscvSlices.renderRuntime()`) com um `_start` cru que
> faz alloc(64)→free→alloc(64) e exige p2==p1 (reuso) + `allocs: 2`/`frees: 1`
> — 2/2 verde; `NativeRiscvRuntimeSliceRegistryTest` 8/8 (concat ainda
> byte-idêntica); suítes cross riscv 44 + aarch 44, só o `CastSaturation`
> pré-existente vermelho; `ArtifactSizeTest` 6/6 (o lock/head internos são
> `.L`-locais e NÃO incham o `.symtab` do hello — foi a única regressão real,
> achada e corrigida aqui); `KofGcE2ETest` 3/3 x86 intocado; `check_500` OK.
> ⚠️ **Correção da prova do G-1 (15/09, doc-vs-realidade — a prova do plano
> não tinha caminho alcançável):** a proposta de 12/09 dizia "ligar o free nos
> nós do log (RtB0, espelhando `RuntimeLog2:98`)" e "ciclo alloc/free/alloc
> reusa o slot". Contar no fonte refuta as DUAS metades: (a) o nó de log riscv
> (`NativeRiscvAsmRtB0:242`) escreve label+msg **direto por `write()` e nunca
> aloca**, e a observabilidade usa slots fixos de `.bss` — logo não há nó para
> liberar; (b) no compilador inteiro **nenhuma fatia riscv chama `kof_free`**
> (o x86 tem 4 callers reais: `RuntimeChannel:132`, `RuntimeLog2:98`,
> `RuntimeObservability1:426`/`2:247`) e **não existe API Kof de free/GC**, então
> um E2E Kof não consegue exercitá-lo. Conclusão: a free-list riscv é a
> infraestrutura correta de paridade x86, mas no riscv ela fica **latente até o
> G-4** (o sweep a alimenta) — a prova do G-1 é o harness asm cru, não um
> programa Kof. Ligar o free nos 57 sítios de alloc existentes é **assunto do
> G-4** (eles precisam liberar objetos mortos, que só o coletor identifica),
> NÃO do G-1. Isso também significa que o vazamento do `.bss` de ~260KB é
> fechado pelo G-4, não pelo G-1.
> **G-2 header flags/mark bits + lista GC (FEITO 15/09, sessão dev):** cada
> bloco RESERVADO do bump agora entra na gc-list global
> (`.Lkof_gc_head`, LIFO, `gc_next`@16, flags=0) na mesma fatia
> `NativeRiscvAsmRtB42`; um free+realloc NÃO re-entra (o bloco nunca saiu da
> lista). Novo `kof_gc_dump` imprime uma linha `gc <size> <flags>` por bloco —
> o "dump `KOF_GC_DEBUG`" do plano (asm puro não tem gatilho por env, então a
> alavanca é uma chamada explícita; honesto e testável). Prova (qemu riscv64
> **e** aarch64, nunca skip): `NativeRiscvGcListTest` 4/4 — alloc(16/32/64) ⇒
> dump `gc 96 0`/`gc 64 0`/`gc 48 0` (total = align16+32, LIFO); free+realloc ⇒
> um único `gc 96 0`; sabotagem (remover o link) = 4/4 vermelho com saída
> vazia. Slice-registry 8/8 (concat ainda byte-idêntica), cross riscv 44 +
> aarch 44 (só o `CastSaturation` pré-existente vermelho), `ArtifactSizeTest`
> 6/6, `KofGcE2ETest` 3/3 x86 intocado, `check_500` OK.
> **G-3 mark conservative riscv (FEITO 15/09, sessão dev):** port do
> `kof_gc_mark`/`kof_gc_try_mark`/`kof_gc_mark_transitive` na fatia nova
> `NativeRiscvAsmRtB43` sobre o header de 32B do G-0 e a gc-list do G-2.
> Raízes de pilha = `sp..s11` (fp riscv; fallback 4KB como o x86) com `s0-s11`
> derramados para que ponteiros em registrador apareçam; raízes estáticas = o
> intervalo entre os rótulos LOCAIS riscv-only `.Lkof_heap_root_start`/
> `.Lkof_heap_root_end` emitidos pelo `NativeArchEmitter` em volta do `.data`
> do programa (sentinel `.quad 0` na abertura). **Correção ao plano:** o
> `kof_heap_root_end` x86 (fila bugfix S-5) NÃO foi necessário — o riscv agora
> emite os próprios marcadores `.L`-locais (fora do `.symtab`, a lição do G-1
> no ArtifactSizeTest) e o intervalo EXCLUI de propósito a arena do bump
> (`_kof_heap`, em `.bss`), ao contrário do x86 que varre até `_end` porque lá
> o heap é mmap. Logo o G-3 avançou SEM o pré-requisito compartilhado; os
> rótulos são declarados program-side em `RiscvSlices.programSideLocals()`.
> Prova (qemu riscv64 **e** aarch64, nunca skip): `NativeRiscvGcMarkTest` 2/2 —
> `_start` aloca A(raiz estática)→B(pilha)→C(inalcançável)→D(via campo0 de A),
> chama `kof_gc_mark`, despeja: `gc 96 1`/`gc 96 0`/`gc 96 1`/`gc 96 1` (D/A
> alcançáveis, C não); sabotagem (remover a varredura transitiva de campos) =
> 2/2 vermelho com D=0. Harnesses G-1/G-2 e slice-registry 8/8 ainda verdes;
> `ArtifactSizeTest` 6/6 (rótulos `.L`-locais, sem inchar o symtab);
> `KofGcE2ETest` 3/3 x86 intocado; `check_500` OK.
> **G-4 sweep + collect FEITO 15/09** — a free-list agora recebe os mortos:
> `kof_gc_sweep` (mark==1 → limpa bit0; mark==0 & !free → free-list + bit1 +
> contadores) + `kof_gc_collect_now` (mark+sweep incondicional) +
> `kof_gc_collect` (tick-guarded, `tick & 4095`, chamado na ENTRADA do
> `kof_alloc`) + `kof_gc_tick` (fatia nova `NativeRiscvAsmRtB44`). O caminho de
> OOM do `kof_alloc` (B42) também roda UM `kof_gc_collect_now` e re-busca a
> free-list antes de panicar. **O x86 DESLIGA o collect dentro do alloc de
> propósito** (`RuntimeMemory:122-131`: o ponteiro do bloco livre vive num
> registrador e o mark conservador não o vê → reuso duplo); **o riscv é seguro**
> porque a value-stack É a pilha de máquina (`pushRiscv`: `addi sp,-8; sd`) e o
> `kof_gc_mark` derrama `s0-s11` — todo temporário vivo está na pilha varrida.
> **Prova (qemu riscv64 E aarch64, NÃO skipam):** `NativeRiscvGcSweepTest` 5/5 —
> (1) mark+sweep+dump exige `gc 96 0 / gc 96 2 / gc 96 0 / gc 96 0` +
> `frees: 1` (C recuperada, D/A/B sobrevivem); (2) um laço de 10000 allocs
> (arena 256KB ≈ 2730 blocos de 96B) COMPLETA porque o coletor recicla os mortos
> (frees > 0); (3) **sabotagem** (remover os hooks do coletor) = o mesmo laço
> panica `out of memory` (não-vacuidade). Baseline do `ArtifactSizeTest`
> atualizado 18→24 syms no hello (a cadeia do coletor agora é alcançável a partir
> do `kof_alloc` — o preço de fechar o vazamento); cross 44+44, G-1/G-2/G-3,
> registry 8/8, `KofGcE2ETest` 3/3 x86 intocado; `check_500` OK.
> **G-5 aarch64** — herda tudo via tradutor; **o G-4 JÁ provou a herança do
> sweep+collect** (`NativeRiscvGcSweepTest` roda as 2 arches), então o G-5 está
> efetivamente satisfeito para o coletor também.

> **G-6 x86 (FECHADA 19/09 — frente 2 D-DEV-PRIORITY, §260; gatilho LIGADO — opção (A) do §260, a mantenedora autorizou e ordenou "assume native-multiarch e termina"):** o coletor x86
> existe e está correto (`kof_gc_mark`+`kof_gc_sweep`+`kof_gc_collect_now`),
> mas o **gatilho** de auto-collect dentro do `kof_alloc` foi MEDIDO INSANO
> para a convenção x86: o backend mantém temporários em **registradores
> caller-saved** nos call-sites (provado: `KofStringParseTest` vermelho /
> `KofSupervisorE2ETest` exit 139 SIGSEGV com o gatilho, verdes sem; medições
> completas em `known-bugs.md §260`). O riscv não precisou de stack-map porque
> lá a value-stack É a pilha de máquina (RtB44:15-20); acreditava-se que o x86
> exigia o real "mapa de raízes por frame" do texto da D-DEV-PRIORITY. Duas
> opções honestas (escopo: a lane compiler):
> **Correção 19/09 — a premissa do root-map NÃO sobreviveu à auditoria do
> §260:** os reds eram o bloco ad-hoc do gatilho (clobberava r10-r15 no
> collect+restart) mais um bug latente do parser (vazamento de r8/r9,
> corrigido em `e667791f`) — não a convenção de chamada em si. O landing
> preserva TODOS os registradores de chamada na profundidade do collect, então
> nem spill-per-site nem tabela de stack-map foram necessários. Ver a nota
> LANDED abaixo (matriz de aceite 1-5 toda verde, medida).
> **G-6b (a metade da causa-1) FEITA 16/09, gatilho ainda OFF:** provado a gdb
> que com a varredura restrita ao frame corrente, Strings vivas nos frames
> EXTERNOS (a pilha de main enquanto um helper aloca) ficavam invisíveis →
> o sweep liberava vivo (keep corrompido, supervisor SIGSEGV 139). Corrigido
> sem o gatilho: o `_start` grava `kof_main_stack_bottom` (rsp de entrada) e
> o `kof_gc_mark` varre a pilha INTEIRA da thread até ele (cap 64MB +
> fallback sp..sp+4096 preservado p/ harness asm sem `_start`); guard
> `NativeX86GcMarkScopeTest` 3/3 (Q0: vermelho no mark antigo). keep/supervisor
> ficaram verdes até COM o gatilho ligado; o red restante é exatamente a
> causa (2) (o caminho `toFloat` segura a String-arg viva em registrador
> caller-saved no call-site do `kof_alloc`) → o gatilho fica OFF (paridade FP
> é freeze rule 5) até a (a) fechar.
>
> **LANDED 19/09 — desenho entregue (NÃO é (a) spill-per-live-ref, e (b) nunca
> foi preciso):** a auditoria do §260 provou que a causa-(2) era ARTEFATO do
> bloco ad-hoc de 16/09 (preservação incompleta de registradores em volta do
> `collect_now`+restart da busca) mais um bug latente do parser (r8/r9,
> `e667791f`). O landing faz o mínimo são no lado do COLETOR, sem tocar site a
> site do backend: `.Lkof_alloc_maybe_gc` (RuntimeMemory) chama
> `kof_gc_collect_now` exatamente UMA vez por programa (flag `8(%rsp)`), com
> gate `kof_spawn_count==0`, e REINICIA a busca da free-list com cursor NULL
> (o perigo de "temporario solto" que matou a tentativa de 16/09 desaparece
> estruturalmente); `kof_gc_collect_now` (RuntimeGc) derrama os 15 GPRs antes
> de mark+sweep, então toda referência viva de call-site ESTÁ na pilha que o
> mark conservador varre (inteiros falso-positivos apenas SOBRE-retêm —
> try_mark valida alinhamento + range do heap + pertença à gc-list). O
> `incq kof_spawn_count` foi MOVIDO para a ENTRADA de `kof_spawn_handle_new`
> (ficava pós-`pthread_create`, abrindo janela onde o primeiro alloc do worker
> disparava o trigger com o result-box vivo só na pilha não-varrida do worker
> — medido: `spawnWorkerThrowIsolated*` perdia `s1=42`→`0` antes do hoist).
> O contador é CUMULATIVO (sem decq): depois que qualquer spawn começa, o
> auto-collect fica OFF para sempre e o programa se comporta EXATAMENTE como
> antes (crescimento por mmap) — a varredura da pilha do worker é o próximo
> degrau CATALOGADO, nunca silencioso.
> Matriz de aceite — 5/5 medida verde: (1) `KofGcE2ETest.gcAutoCollectFitsUnderMemoryCap`
> verde (200k×`"s"+i` sob `ulimit -v 256M`: exit 0 — §260(1) agora como GUARD
> no repo, era script one-off); (2) os dois repros §260 verdes
> (`KofStringParseTest` limpo, `KofSupervisorE2ETest` 16/16 — sem 139);
> (3) suíte completa **2348/0F/0E + 183 skips** — CORRIGIDO 19/09: o
> "incl. riscv/aarch sob qemu" registrado era ERRADO (este host não tem
> binutils cross; os ~183 testes cross SKIPAM aqui; CI `cross-native` é o
> árbitro) — o trigger é x86-only, cross intocado de qualquer forma; (4) `ArtifactSizeTest.helloX86` re-baselined
> 44→**84 syms COM causa documentada no comentário da constante** (linkar o
> coletor é o ponto da feature; precedente riscv G-4 18→24) — os BYTES
> ficaram DENTRO do gate +5% (baseline 37.320B segurada; o +19,7% temido não
> se reproduziu na forma entregue); (5) MT = comportamento antigo + face
> catalogada.
> - **(b) stack-map completo / spill-per-live-ref:** NÃO executado — medido
>   desnecessário para o contrato main-only; só voltam a ser exigidos
>   apenas se a face worker-stack-scan (auto-collect com threads vivas) for
>   agendada.
> Cada degrau: commit com suíte cross completa verde + DOING.md na linha.
> G-0/G-1/G-2 adiantam sem root_end; **o G-3 também adiantou** (emite os
> próprios marcadores `.L`-locais riscv — NÃO precisou do `kof_heap_root_end`
> x86 da S-5); G-4 (sweep+collect) é o próximo e não depende dele.
>
> **Além do GC (promovido a desenvolvimento 22/09):** a diretiva da mantenedora de
> 15/09 ("todo código nativo deve se comunicar direto com barebones — código
> bootável para microcontroladores, legado e UEFI com Kof") está decomposta em
> `docs/PLAN-BAREMETAL-BOOT.md` (faces B-0…B-6: costura HAL
> `kof_plat_*` + perfil freestanding + UEFI/BIOS/MCU + anéis x86_64 ring0/ring1)
> — **EM DESENVOLVIMENTO** desde 22/09 (`D-BAREMETAL-BOOT`, ordem da
> mantenedora). A face MCU depende do coletor (G-4/G-5) acima.
>
> **Status:** `EM DESENVOLVIMENTO (parcial)` — **riscv64 + aarch64 com core completo (03/09)**: classes/arrays/List/strings/instanceof/switch/try-catch/FP/recursão em asm puro nos dois; paridade avançada pendente *(ver re-auditoria 12/09 acima — muito do que estava "pendente" já roda sob qemu; o que falta tem código de gap honesto)*.
> **Versão:** 0.2.6-beta · **Data:** 2026-09-03
> **Gap:** `NATIVE002` (riscv64 core ✅ 02/09; aarch64 core ✅ 03/09 via tradução riscv→aarch64; paridade total x86 — JSON/DB/HTTP/concorrência/UI/net — pendente nos dois).
> **Progresso 03/09:** toolchain cruzada + qemu + **codegen riscv64 + aarch64** (stack machine,
> `sp`=operandos/`s11`/`x29`=frame pointer, modelo idêntico ao x86_64) + **runtime asm puro** —
> `NativeRiscv64E2ETest 13/13` (`qemu-riscv64`) + `NativeAarch64E2ETest 13/13` (`qemu-aarch64`): println(String/Int), `var`, `if/else`,
> aritmética/comparações, **classes (virtual dispatch/fields/métodos), arrays, List,
> switch, try/catch/throw, pattern matching (`switch String s`/`instanceof`/`as`),
> String methods, recursão**. Ver §2.3.
> **Decisão (02/09):** runtime por arch **em assembly puro**, no mesmo estilo do x86_64
> (`NativeRuntime.generateRuntimeAssembly`) — **sem C** ("Kof é Kof"; o `kof-c-compiler`
> é outra ferramenta, não um runtime). O C compilado com gcc cruzado que foi usado em
> 02/09 como validação de ABI foi descartado: riscv64/aarch64 passam a emitir runtime asm
> puro (bump allocator + raw syscalls `write`/`exit`, sem PLT/libc) e linkam estático via `ld`, idêntico ao modelo x86_64 (sem dependência de libc).
> **Escopo:** expandir o `NativeBackend` (hoje `x86_64` em asm puro) para
> `riscv64` e `aarch64` Linux, preservando `frontend → Kof IR → backend` e
> paridade `JVM/Native/JS`. Este doc vive em `docs/` (não em `docs/future/`)
> porque **já há código em desenvolvimento** — ele documenta o estado real e
> como finalizar.

## 1. Objetivo

Levar o `NativeBackend` de `x86_64` único para multi-arch Linux sem quebrar
`KofPatternMatchingTest` 10/10 (`switch String s` / `instanceof` / `as` /
`checkcast`) e a suíte `NativeE2ETest`.

Não inclui macOS/Windows, GC avançado ou `kof.web` nativo completo (ver
"fora de escopo").

## 2. Estado Real (auditoria 01/09)

> **Regra desta pasta:** `docs/` documenta o que **está em desenvolvimento**;
> `docs/future/` só o que **é plano futuro** (zero código). Este item já tem
> código, por isso está aqui.

### 2.1 O que JÁ ESTÁ FEITO (plumbing)

| Peça | Estado | Onde |
|------|--------|------|
| Enum `Target.NATIVE_RISCV64` / `NATIVE_AARCH64` | ✅ | `Target.java` (valores distintos de `NATIVE`; `NATIVE` continua = `x86_64`) |
| `Target.isNative()` cobre os 3 nativos | ✅ | `Target.java` |
| `Target.nativeArch()` → `x86_64`/`riscv64`/`aarch64` | ✅ | `Target.java` |
| CLI `native.risc`/`native.riscv64`/`native.riscv` → `NATIVE_RISCV64` | ✅ | `KofCliSupport.java:91` |
| CLI `native.arm`/`native.aarch64`/`native.aarch` → `NATIVE_AARCH64` | ✅ | `KofCliSupport.java:92` |
| `kof build`/`run` aceitam `native.risc`/`native.arm` | ✅ | `status.md:13-14` |
| Dispatch `emit()` → `emitRiscv`/`emitAarch64` | ✅ | `NativeBackend.java:210-215` |
| Cross toolchain invocado (as/ld + dynamic-linker + `-lc`) | ✅ | `NativeBackend.emitRiscv`/`emitAarch64` |
| Fallback gracioso sem toolchain (`keeping asm`) | ✅ | idem (try/catch `IOException`) |

**Consequência prática:** `kof build --target native.risc` **compila e gera um
binário** (um stub que sai com `0`) — o pipeline de toolchain/cross-as/ld já
funciona de ponta a ponta.

### 2.2 O que AINDA NÃO ESTÁ FEITO (codegen — o gap real `NATIVE002`)

| Peça | Estado | Detalhe |
|------|--------|---------|
| **Lowering real riscv64 (core)** | ✅ completo 02/09 | `emitRiscv` emite o IR em asm: stack machine (`sp`=pilha de operandos, `s11`=frame pointer, `ra`/`s11` salvos no frame — modelo idêntico ao x86_64) + `.macro pop`; todos os ops do core: literal/local/field/binary (int+FP+bitwise)/unary/condjump/jump/label/call (println/print/valueOf/String methods/coleções/construtor/vtable virtual/FUNCTION/STATIC)/new_object/dup/pop/checkcast/instanceof/arrays/throw/try/catch/return. `NativeRiscv64E2ETest 13/13` |
| **Lowering real aarch64 (core)** | ✅ completo 03/09 | `emitAarch64` = **tradução linha-a-linha do riscv64** (mesmo modelo/lowering, ISA ARMv8-A: `sp`=pilha/`x29`=frame pointer, `x30`/`x29` salvos, `.macro pop` → `ldr`/`add`, `sp` já 16-alinhado no `_start`, `str sp` via temp `x17`). `translateRiscvToAarch64` cobre int+FP (`slt`/`sle`/`seqz`/`snez`/`sext.w`/`fcvt`/`fmv`/`fadd`/`feq`…), `andi`/`ori` via `movk x17`, `sd sp` via `mov x17,sp`. `NativeAarch64E2ETest 13/13` (`qemu-aarch64`) |
| Ops fora do core riscv64/aarch64 (JSON/DB/HTTP/concorrência/UI/net) | ❌ diagnóstico `NATIVE002` | ops desconhecidos emitem comentário `# NATIVE002: op fora do caminho feliz` (nunca binário mudo) |
| Os 18 métodos `emit*` reais (x86_64) | ✅ | `emitBinary`/`emitOperation`/`emitMethod`/`emitConditionalJump`/vcall… — o caminho completo continua só em x86_64 |
| Extração de `NativeBase` (layout/`kof_alloc`/mangle comum) | ❌ não existe | `NativeBackend` ainda é monolítico x86_64 (riscv/aarch64 reusam o mesmo lowering via tradução) |
| Runtime por arch (asm) | ✅ riscv64 + aarch64 core | `kof_alloc`(bump)/`kof_memcpy`/strings (literal/concat/equals/charAt/substring/contains/startsWith/endsWith/indexOf/toInt/length)/int-long-bool→string/print/objects (`init_object`/`instanceof`/super_table/vtables)/arrays (alloc/get/set/length+bounds)/List (new/add/get/set/size/contains/grow)/exceções (`throw`/exc_chain/`null_error`/`bounds_error`) em **asm puro** riscv64 **e** aarch64 (raw syscalls, sem libc; aarch64 via `translateRiscvToAarch64` — `adrp`+`add :lo12:`, `svc #0`, `and sp` skip, `str sp` via `x17`); `qemu-riscv64`/`qemu-aarch64` (ver §2.3). |
| Testes E2E `qemu` (aarch64/riscv64) | ✅ | `NativeRiscv64E2ETest` 42/42 + `NativeAarch64E2ETest` 42/42 (84 testes cross, medidos por @Test + surefire 13/09) |
| CI com cross toolchains | ✅ existe (13/09) | job `cross-native` em `.github/workflows/ci.yml` (instala binutils-riscv64/aarch64 + qemu-user-static e roda as 2 suites; provado `success` no run 34732932745) |
| `backend-parity.md` colunas por arch | ⚠️ parcial | delta citado, colunas `NATIVE_X86_64/AARCH64/RISCV64` separadas pendentes |

**Consequência prática (SUPERADA — foto de 01/09):** valia p/ o stub
original de plumbing; hoje (re-auditoria 12/09 no topo) riscv/aarch **executam
a lógica** sob qemu — 42+42 testes E2E cross, inclusive programas reais com
`println`/`instanceof`/`switch`/Map/Set/higher-order byte-idênticos ao JVM.
O que restou de honesto nesta tabela: `NativeBase` não extraído, colunas por
arch em `backend-parity.md` não separadas, e as faces de ops fora do core
(JSON/DB/UI por arch específico).

### 2.3 Runtime em assembly puro por arch (decisão 02/09)

**Não há runtime em C no Kof.** O nativo x86_64 é asm puro de ponta a ponta: o
runtime (`kof_alloc`, `kof_string_*`, `kof_instanceof`, …) é emitido em
assembly por `NativeRuntime.generateRuntimeAssembly()` e linkado com
`ld -dynamic-linker /lib64/ld-linux-x86-64.so.2 -lc` — a libc entra via PLT
(`printf`/`snprintf`), sem C compilado. (O módulo `kof-c-compiler` é outra
ferramenta — reimplementação do sectorC — e **não** é um runtime.)

Decisão para riscv64/aarch64: **mesmo caminho** — runtime emitido em asm
puro por arch + `ld -dynamic-linker /lib/ld-linux-<arch>.so.1 -lc`. Um C
compilado com gcc cruzado foi usado brevemente (02/09) apenas para validar a
ABI/estática no qemu; ele foi descartado da arquitetura.

Toolchain instalada (02/09, via `sudo apt`):
`binutils-riscv64-linux-gnu`, `binutils-aarch64-linux-gnu`, `qemu-user`,
`gcc-riscv64-linux-gnu`/`gcc-aarch64-linux-gnu` (debug),
`libc6-riscv64-cross`, `libc6-arm64-cross`.

Pipeline alvo (`emitRiscv`/`emitAarch64`):
```
Main.s  (programa: kof_main + seções .data/.rodata)
      + runtime asm riscv64/aarch64 (emitido pelo NativeBackend)
   └─ <arch>-as → <arch>-ld -dynamic-linker /lib/ld-linux-<arch>.so.1 -lc
   └─ qemu-<arch> → saída esperada (exit 0)
```

**🔗 Link dinâmico SOB DEMANDA (link-by-use) — FEITO 15/09 (diretriz da
mantenedora "liga dinamicamente"):** o link cross *era* estático (asm puro, sem
libc) embora a decisão de 02/09 acima sempre dissesse dinâmico. O
`NativeCrossLink` implementa o realinhamento: o binário continua **estático**
enquanto o runtime *podado* não chamar símbolo de libc; no instante em que uma
capacidade libc-dependente entra (double→string/FLT001 via `snprintf`/`strtod`,
`kof.db` via `.so`, …) o `NativeArchEmitter` troca para `<arch>-ld
--allow-shlib-undefined [--no-relax riscv] --sysroot=<s> -dynamic-linker
/lib/ld-linux-<arch>.so.1 -o <bin> <obj> -lc`. Isso preserva a portabilidade dos
84 binários cross atuais (nada muda sem consumidor libc) e destrava os gaps de
libc sob demanda. O `--allow-shlib-undefined` é obrigatório (a `libc.so.6` do
sysroot referencia símbolos `GLIBC_PRIVATE` do loader; o runtime os resolve).
Resolução do sysroot: env `KOF_CROSS_SYSROOT` → instalação de sistema
(`/usr/<arch>-linux-gnu`, sem `--sysroot`) → `/tmp/opencode/x` (este host) →
nenhum (segue estático + stderr, R6). Provado por `NativeCrossDynamicLinkTest`
(5/5, riscv64 **e** aarch64 sob qemu: `snprintf`+`write` resolvidos em runtime; a
sabotagem de ligar o mesmo harness estático falha com `snprintf` indefinido). O
primeiro consumidor de produção chegou em 15/09: `RuntimeDtoa` portado ao
runtime cross como fatia `RtB45` (`kof_dtoa_format`/`kof_double_to_string`/
`kof_float_to_string`), fechando o **FLT001** — provado por `NativeRiscvDtoaTest`
(tabela oracle JVM no riscv64 **e** aarch64) mais o E2E
`nativeValueOfDoubleFloat` nos 2 arches. O aarch64 herda automaticamente assim
que o tradutor mapeia os apelidos ABI `fa0..fa7` → `d0..d7`.

Detalhes do runtime riscv64/aarch64 (inc-0 02/09 + 03/09):
- alocação: **bump allocator + free-list** em `.bss` (sem `mmap` — evita
  problemas de qemu estático; o x86_64 usa `mmap`+free-list, e riscv64/aarch64
  seguem o modelo). O G-1 (15/09) somou a free-list no estilo x86 +
  `kof_free` + `kof_memstats`; o G-4 (15/09) fechou o laço: `kof_gc_collect`
  roda na entrada do `kof_alloc` (tick 4096) e o `kof_gc_sweep` alimenta a
  free-list com os mortos, então um laço longo recicla em vez de esgotar o `.bss`.
- strings: layout **idêntico ao x86_64** — `[typeId@0 i32][super@4 i32]
  [vtable@8 ptr][len@16 i32][data@24 …]` (`KOF_STRING_TYPE_ID=1`).
- saída: raw syscall `write(1, …)` (`a7=64` riscv / `x8=64` arm) + `exit` (`a7/x8=93`) — binário **estático**, sem libc/PLT.
- aarch64: **tradução mecânica** do runtime riscv64 (`riscv2arm.py` validado + `translateRiscvToAarch64` em `NativeAarch64Translator.java:16`): `la`→`adrp`+`add :lo12:`, `ecall`→`svc #0`, `and sp` skip (sp já 16-alinhado), `str sp` via `mov x17,sp`, `andi -16` via `movk x17`+`and`, `rem`→`sdiv`+`msub`, `slt/sle`→`cmp`+`cset`, FP `fcvt`→`scvtf`/`fmv`→`fmov`/`fadd`→`fadd`/`feq`→`fcmp`+`cset`.
- validação: `NativeRiscv64E2ETest 13/13` via `qemu-riscv64` + `NativeAarch64E2ETest 13/13` via `qemu-aarch64` (core completo).

O que **restou** para os próximos incrementos:
- riscv64 + aarch64: Map/Set, higher-order (map/filter/reduce), JSON/DB/HTTP/concorrência/
  UI/net — paridade total com o x86_64 (mesmo gap nos dois; hoje diagnóstico `NATIVE002`).

## 3. Arquitetura (alvo)

> **Nota:** a implementação real divergiu do esboço original (que propunha
> renomear para `NATIVE_X86_64` + flag `--arch`). A decisão adotada foi **valores
> de enum distintos** (`NATIVE` = x86_64, `NATIVE_RISCV64`, `NATIVE_AARCH64`) +
> **nome de target no CLI** (`native.risc`/`native.arm`) — sem flag `--arch` e
> sem renomear `NATIVE` (mantém compat). Segue a decisão real.

```
Target enum:
  JVM, NATIVE (=x86_64), NATIVE_RISCV64, NATIVE_AARCH64, JS, ANDROID

IRModule → NativeBackend.emit (select por target):
  NATIVE          → lowering x86_64 (completo, 18 emit*)   [FEITO]
  NATIVE_RISCV64  → emitRiscv   (core completo 02/09, 13/13) [FEITO]
  NATIVE_AARCH64  → emitAarch64 (core completo 03/09, 13/13 via tradução) [FEITO]
  → (meta) extrair NativeBase: ClassLayout, kof_alloc, mangle, resolveFieldOffset
```

`kof build --target native.risc|native.arm` (já funciona no dispatch).

## 4. Mapeamento por Arch (referência para o lowering)

| Aspecto | x86_64 (atual) | AArch64 | RISC-V 64 |
|---------|----------------|---------|-----------|
| **Assembler** | `as` GNU | `aarch64-linux-gnu-as` | `riscv64-linux-gnu-as` |
| **Linker** | `ld -dynamic-linker /lib64/ld-linux-x86-64.so.2 -lc` (x86_64 usa PLT/libc) | `aarch64-linux-gnu-ld` **estático** (raw syscalls, sem `-lc`) | `riscv64-linux-gnu-ld` **estático** (raw syscalls, sem `-lc`) |
| **Regs args** | `rdi rsi rdx rcx r8 r9` | `x0 x1 x2 x3 x4 x5` | `a0 a1 a2 a3 a4 a5` |
| **Regs temp** | `rax rcx rbx r10` | `x9 x10 x11 x12` | `t0 t1 t2 t3` |
| **Ret** | `rax` | `x0` | `a0` |
| **Stack** | `pushq %rax` | `str x0,[sp,#-16]!` | `addi sp,-16; sd a0,0(sp)` |
| **Call** | `call sym` | `bl sym` | `call sym`/`jal` |
| **Vcall** | `mov 8(%rax),%rbx; add $idx*8,%rbx; mov (%rbx),%rbx; call *%rbx` | `ldr x9,[x0,#8]; add x9,x9,#idx*8; ldr x9,[x9]; blr x9` | `ld t0,8(a0); addi t0,idx*8; ld t0,0(t0); jalr t0` |
| **Cmp/Jmp** | `cmpq %rax,%rcx; je L; jmp M` | `cmp x1,x0; b.eq L; b M` | `sub t0,a0,a1; beqz t0,L; j M` |
| **String header** | `24B [typeId@0][vtable@8][len@16]` | idem | idem |
| **Syscall exit** | `mov $60,%rax; xor %rdi,%rdi; syscall` | `mov x8,#93; mov x0,#0; svc #0` | `li a7,93; li a0,0; ecall` |

`kof_alloc`/`kof_instanceof`/`kof_string_*` por arch com `KOF_STRING_TYPE_ID=1`
constante.

## 5. Como Finalizar (passo a passo — reflete o plumbing que já existe)

> O encanamento (enum + CLI + dispatch + toolchain) **já está pronto**. O que
> falta é a codegen. Ordem incremental, sem quebrar `x86_64`:

1. **Extrair `NativeBase`** — tirar para uma classe/interface comum:
   `getLayoutForType`/`sanitize`/`mangle`/`resolveFieldOffset`/`collectStrings`
   (hoje em `NativeBackend`). `NativeBackend` (x86_64) herda e continua igual.
   → validar `mvn test -Dtest=CompilerDriverTest` (nenhum `emit` muda).
   *Depende de: nada. Não muda binário x86_64.*

2. **Runtime por arch** — mover `kof_alloc`/`kof_instanceof`/`kof_string_*`
   para asm por arch (hoje inline em `NativeRuntime` x86_64); o `emit` de cada
   target inclui a seção `.s` correta. *Depende de 1.*

3. **`Riscv64Backend` mínimo** — substituir o stub `emitRiscv` por lowering real
   do caminho feliz: `String`/`println`/`instanceof String` + `switch String s`
   + `checkcast` no-op, usando os mapeamentos da tabela §4.
   → `qemu-riscv64` rodando `hello` (teste `assume` se `qemu`/`riscv64-as`
   ausentes, como `NativeE2ETest`). *Depende de 1,2.*

4. **`Aarch64Backend` mínimo** — idem para aarch64 (`qemu-aarch64`). *Depende de 1,2.*

5. **Coleções + classes** — `kof_list_*`/`kof_map_*`/`kof_set_*` e
   `kof_instanceof` para classes de usuário (o `Dummy` usado em
   `KofPatternMatchingTest`). *Depende de 3,4.*

6. **Testes E2E multi-arch** — `NativeRiscv64E2ETest` + `NativeAarch64E2ETest`
   (`@Tag("slow")`, `assume` p/ `qemu`+cross-as ausentes) rodando a mesma fonte
   que `KofPatternMatchingTest` roda em x86_64/JS. *Depende de 5.*

7. **CI** — toolchains `x86_64` sempre; `aarch64`/`riscv64` com
   `if: cross-available` (não quebrar o pipeline quando a toolchain falta).
   *Depende de 6.*

8. **Docs** — `backend-parity.md`: colunas separadas
   `NATIVE_X86_64`/`AARCH64`/`RISCV64`; remover `NATIVE002` quando 6 verde.
   *Depende de 6.*

**Critério de pronto:** `var x:Object="hello"; switch(x){case String s: println(s)}`
compila e roda **idêntico** em `x86_64`, `aarch64 (qemu)`, `riscv64 (qemu)` e
`JS` (`typeof==="string"`); `KofPatternMatchingTest` 10/10 por arch.

## 6. Riscos e Mitigação

- **Stack ABI 16-byte** (ARM/RISC-V exigem `sp` alinhado) → usar pares
  `str/ld` de 16.
- **Reloc RIP vs PC-relative**: x64 `leaq sym(%rip)` → ARM `adrp`+`add` /
  RISC-V `auipc`+`ld`.
- **Cross toolchain ausente** → `assume` skip, não falhar `mvn test`.
- **QEMU lento** → `NativeE2ETest` só x86_64 rápido; aarch64/riscv64 em
  `@Tag("slow")`.
- **Divergência silenciosa**: stub atual "passa" gerando binário → garantir que
  o gap `NATIVE002` seja **diagnóstico claro** (não binário que silencia a
  lógica) até o lowering existir.

## 7. Fora de Escopo (ficam em `docs/future/` / outros docs)

- `GC` mark-sweep avançado, `float/double` no Native (`F2D`), `kof.web`
  `listen` nativo, `macOS` Mach-O / `Windows` PE.

  **O `macOS` Mach-O está EM DESENVOLVIMENTO (issue #1135, `D-NATIVE-MACOS` —
  arm64 + x86-64).** A costura `kof_plat_*` (as 21 funções de plataforma
  nomeadas) é a superfície de migração, e ela agora tem RATCHET: o
  `scripts/check_macos_seam.sh` mede as chamadas de sistema que ainda furam a
  costura (x86-64 `syscall` 180 fora-da-costura de 203; riscv64 `ecall` 168
  fora-da-costura de 184), os sítios de TLS ELF (`%fs:/@tpoff`, 24) e os sítios
  de `futex` (15 — o macOS usa `__ulock`/`os_unfair_lock`), e **falha o build
  se qualquer um desses números CRESCER** (o baseline é
  `scripts/macos-seam-baseline.txt`; diminuir é sempre verde; `--update-baseline`
  o regenera; `--selftest` prova que o gate ainda distingue igual/crescimento/
  melhora). Os estágios M-1..M-8 (emissor Mach-O → costura libSystem →
  allocator/GC → exceções → concorrência → …) consomem estes números como o
  ANTES; o M-7 precisa de um runner macOS.
  **M-1 POUSADA 21/11 (#1135, D-NATIVE-MACOS):** o alvo macOS abre como um
  PERFIL (`NativeProfile.MACOS_ARM64`, `--profile macos`), não como um backend
  novo — o IR, a lowering e a costura `kof_plat_*` ficam intactos. A fase
  entrega as três faces da linha M-1: (1) o **emissor de diretivas Mach-O**
  (`nat/NativeMachO.java` — um passe textual sobre o assembly inteiro: o
  `.type`/`.size` (só ELF) saem, as seções viram
  `__TEXT,__text`/`__DATA,__data`/`__DATA,__bss`/`__TEXT,__const`, e os
  globais E suas referências ganham o prefixo `_` do Mach-O — o mnemônico e
  os registradores nunca são tocados); (2) a **linha de link**
  (`NativeAssembler.macosLinkArgs` — `ld -lSystem -arch arm64` mais
  `-syslibroot <sdk>` quando `KOF_MACOS_SDK_PATH` está setado; sem
  `-dynamic-linker` ELF, sem `-lc` do GNU); (3) a **recusa honesta** num host
  sem o toolchain macOS (`NATIVE004` — esta frota Linux tem o `as`/`ld` do
  GNU, que não falam Mach-O; o runner que prova o link é a fase M-7). A
  prova da fase é ESTRUTURAL nesta frota (o texto traduzido + a linha de
  link), exatamente como o plano registra; a entry `_main` e os corpos da
  costura sobre a libSystem são a linha M-2.
  **M-1 CORRIGIDA 22/11 (#1135 — o emissor do M-1 estava QUEBRADO contra o
  assembly real; medido, não assumido):** a prova do M-1 eram seis testes de
  texto pequeno (~10 linhas cada). O assembly **real** que o backend emite é o
  runtime inteiro — **132 KB** — e contra ESSE texto o emissor do M-1 tinha
  cinco defeitos reais, todos encontrados rodando o **assembler Mach-O de
  verdade** (o clang 18, provisionado por
  `scripts/provision-macho-toolchain.sh` → `~/.local/share/kof-macho`): (1) ele
  **TRAVAVA** — a varredura do operando recomeçava no `%` de um REGISTRADOR
  sem consumi-lo, então toda linha com registrador (`pushq %rbx`) girava para
  sempre: um compile macOS de verdade nunca terminaria; (2) os **imediatos
  eram corrompidos** (`movq $1` → `movq _$1`; o clang: "invalid operand for
  instruction"); (3) os **literais hexadecimais eram corrompidos** (`0x4f0c…`
  → `0_x4f0c…`; "unexpected token"); (4) o **mnemônico depois de um prefixo**
  era prefixado (`lock cmpxchg` → `lock _cmpxchg`; "invalid instruction
  mnemonic"); (5) a **referência de dado** (`.quad Pair_toString`, a tabela de
  métodos) não recebia o prefixo — o símbolo ficaria indefinido. O emissor
  agora varre TOKEN por TOKEN (o mnemônico e seus prefixos, os registradores,
  os imediatos, os números, as strings e os comentários são copiados
  verbatim; só as referências a símbolos globais ganham o prefixo `_`), e a
  prova é **o assembler real aceitando os 132 KB reais** — o runtime inteiro,
  menos as duas faces ELF-only que o macOS ainda não tem. A fase ganhou
  também a **recusa nomeada `NATIVE005`** para essas faces: os syscalls Linux
  crus (a linha M-2 — a costura `kof_plat_*` sobre a libSystem) e o TLS
  local-exec do ELF (`%fs:…@tpoff` — a linha M-4) são contados no texto
  emitido e o perfil recusa com o diagnóstico que nomeia as duas, em vez de
  emitir um binário que falha em runtime (R6).
  **M-2 FATIA 1 POUSADA 22/11 (#1135 — a costura `kof_plat_*` sobre a
  libSystem: a família write/exit; e a linha de link nomeia a arquitetura do
  EMISSOR):** o perfil macOS emitia o texto do HOST, e o texto do host fala o
  ABI Linux de syscall cru — o Darwin não tem isso, então um binário macOS
  nunca teria rodado. MEDIDO no hello real (os mesmos 132 KB que a correção do
  M-1 provou contra o assembler real): os **9** sítios de syscall cru são de
  `kof_plat_write`, `kof_plat_writev`, `kof_plat_exit`, `kof_plat_exit_group`,
  `kof_plat_thread_id`, `kof_plat_sync` e três `mmap` do allocator
  (`.Lgrow_try`). A PRIMEIRA FAMÍLIA MIGRADA é write/exit: no perfil macOS a
  costura chama a **libSystem** (`call write`, `call writev`, `call exit` — os
  mesmos registradores SysV do syscall Linux, `%rdi/%rsi/%rdx`), com a forma
  PROVADA de alinhamento de pilha (`pushq %r15; movq %rsp, %r15; andq $-16,
  %rsp; call …; subq %rsp, %r15; addq %r15, %rsp; popq %r15`) porque a libc
  usa SSE e a ABI exige `%rsp ≡ 0` no site do call. O Darwin não tem
  `exit_group`, então as duas saídas chamam `exit` (o processo termina de
  qualquer forma; a diferença para o `exit` do Linux — que encerra só a
  thread — fica registrada na costura). O RATCHET CAIU de **9 para 5**
  syscalls crus: a família migrada saiu e o que fica é o allocator (M-3) e a
  concorrência (M-5) — e a recusa `NATIVE005` agora **nomeia os donos** em vez
  de contar às cegas. **A ARQUITETURA DA LINHA DE LINK (uma contradição
  medida, corrigida):** a linha do M-1 dizia `-arch arm64` enquanto o objeto
  que o backend emite é **x86-64** (a prova do M-1 montou os 132 KB reais
  como `x86_64-macos-none`) — um link que falha no runner do M-7. A
  arquitetura agora segue o **emissor** (`-arch x86_64`), e a constante do
  perfil perdeu a reivindicação de arquitetura (`NativeProfile.MACOS`, era
  `MACOS_ARM64`): um perfil que nomeia uma arquitetura que ele não emite é
  uma mentira. A **face Apple silicon (arm64) é a próxima linha do #1135** —
  ela precisa do emissor cross aarch64 e da sua própria prova Mach-O; no
  Apple silicon a face x86-64 roda sob o Rosetta 2. **O DETECTOR ERA CEGO EM
  DUAS FACES:** o `NATIVE005` contava só o `syscall` do x86-64 — nas faces
  aarch64 (`svc #0`) e riscv64 (`ecall`) a recusa era muda; agora ele vê as
  três. **PROVA (executada):** `NativeMacOSSeamE2ETest` **8/8** — RED-first
  **6/6 falhas** no código antigo (o syscall cru dentro da costura, o ratchet
  ainda em 9, os símbolos `_write`/`_exit` ausentes, a linha de link com
  `-arch arm64`, a reivindicação de arquitetura no nome do perfil, o detector
  cego no `ecall`) e GREEN depois; o **assembler Mach-O real aceita o texto do
  perfil** com os corpos da libSystem (o objeto carrega `_write`/`_exit` como
  externos indefinidos — o que o link contra a libSystem resolve);
  `NativeMacOSProfileE2ETest` 11/11; a bateria nativa **810 run / 0F / 0E /
  81 skipped** (skips de ambiente nomeados); os gates rc=0.
  **M-2 FATIA 2 POUSADA 22/11 (#1135 — as famílias time/random/io da costura
  sobre a libSystem, e os clock ids são os DO DARWIN):** o segundo lote da
  costura também fala a libSystem agora, e um dos corpos teria ficado
  silenciosamente ERRADO se a constante do Linux tivesse sido copiada.
  MEDIDO num programa real (`time.sleep` + `time.now` + `random.hex(8)` — as
  fatias que essas faces puxam): os syscalls Linux crus caíram de **20 para
  16**, e as quatro famílias que saíram do ABI Linux são `kof_plat_time`,
  `kof_plat_time_mono`, `kof_plat_sleep` e `kof_plat_random` (mais
  `kof_plat_read`/`kof_plat_close` na família io). **OS CLOCK IDS SÃO OS DO
  DARWIN (medidos nos headers do SDK do macOS que o toolchain provisionado
  traz):** `CLOCK_REALTIME = 0` e **`CLOCK_MONOTONIC = 6`** — no Linux é 1, e
  no Darwin o 1 não é o relógio monotônico. O `struct timespec` do Darwin tem
  o MESMO layout do Linux (`tv_sec` em 0, `tv_nsec` em 8), então o runtime que
  lê `0(%rsp)`/`8(%rsp)` continua certo. **A FAMÍLIA RANDOM USA O CSPRNG DO
  DARWIN:** a `getentropy` do Linux NÃO EXISTE no Darwin (medido — ela não
  está nos headers do SDK) e ela tem um teto de 256 bytes por chamada, que o
  `random.hex(n)` estouraria; o corpo usa `arc4random_buf(buf, len)` (10.7+, o
  CSPRNG do próprio Darwin, sem teto de tamanho) e devolve o comprimento
  pedido, porque o contrato da costura é `kof_plat_random(buf, len) -> n` com
  `n >= 0` = bytes escritos. A família io (`read`/`close`) migrou
  mecanicamente — os mesmos registradores SysV. **OS CORPOS FORAM PARA A
  SUA PRÓPRIA CLASSE** (`runtime/RuntimePlatMacos.java` — o mesmo padrão de
  `RuntimeBios`/`RuntimeUefi`/`RuntimeBareRandom`), e o `RuntimePlat` delega
  por família. **O QUE AINDA É LINUX (medido nesse programa):** os 16
  syscalls crus que ficam são `kof_plat_thread_id` e `kof_plat_sync` (a face
  de concorrência — M-5), o `.Lgrow_try` do allocator (M-3), e a io PRÓPRIA
  do runtime (`.Lkof_read_line_loop`, `kof_read_file`, `kof_write_file`) mais
  o `kof_sec_secret_get` — esses PASSAM POR FORA da costura, e trazê-los para
  a costura é a linha M-6. **PROVA (executada):** `NativeMacOSSeamE2ETest`
  **12/12** — RED-first **4 falhas** no código antigo (o syscall cru na
  família time, o clock id do Linux, a família io ainda no `syscall`, os
  símbolos `_clock_gettime`/`_arc4random_buf` ausentes) e GREEN depois; o
  **assembler Mach-O real aceita os corpos de time/random** (o objeto carrega
  `_clock_gettime` e `_arc4random_buf` como externos indefinidos);
  `NativeMacOSProfileE2ETest` 11/11; a bateria nativa **814 run / 0F / 0E /
  81 skipped**; os gates rc=0.
  **M-3 FATIA POUSADA 22/11 (o allocator sobre a libSystem) — o
  `kof_plat_heap_grow` (a descida da arena de 4 GiB → 64 MiB) agora chama
  `mmap`/`munmap` da libSystem, e o mmap do Darwin NÃO É o mmap do Linux em
  TRÊS pontos, todos MEDIDOS nos headers do SDK: (1) a 4ª flag vai em `%rcx`
  (a ABI SysV — o syscall Linux punha em `%r10`); (2) as flags são DIFERENTES
  — `MAP_PRIVATE|MAP_ANON|MAP_NORESERVE` = `0x1042` no Darwin contra o
  `0x4022` do Linux (o `MAP_ANONYMOUS` do Linux é 0x20 e o `MAP_NORESERVE`
  dele é 0x4000); (3) o erro é `MAP_FAILED` = `-1` exato (o syscall Linux
  devolvia um `-errno` em `[-4095,-1]`). A lógica da arena (a descida, o
  bitmap, a devolução do bitmap que falhou) é a do host byte a byte — só a
  syscall virou a chamada da libc, com a forma provada de alinhamento de
  pilha. **O RATCHET:** os syscalls Linux crus do hello real caíram de **5
  para 2** — o que resta da face x86-64 da costura é a concorrência
  (`kof_plat_thread_id`, `kof_plat_sync` — a linha M-5). **PROVA
  (executada):** `NativeMacOSAllocatorE2ETest` **5/5** — RED-first **5
  falhas** no código antigo (o corpo ainda no `syscall`, as flags do Linux em
  `%r10`, a checagem de `-errno` do Linux, o ratchet ainda em 5, os símbolos
  `_mmap`/`_munmap` ausentes) e GREEN depois; o **assembler Mach-O real
  aceita o corpo do allocator** (o objeto carrega `_mmap`/`_munmap` como
  externos indefinidos); `NativeMacOSSeamE2ETest` 12/12;
  `NativeMacOSProfileE2ETest` 11/11; a bateria nativa **819 run / 0F / 0E /
  81 skipped**; os gates rc=0. **A FRONTEIRA (honesta):** o perfil ainda
  RECUSA emitir um binário (`NATIVE005`) — a concorrência (M-5), o TLS (M-4) e
  a io própria do runtime (M-6) ainda falam Linux, e a prova de que um
  binário macOS RODA é o runner do M-7.
  **M-6 FATIA 1 POUSADA 22/11 (a io do runtime pela costura) — o
  `read`/`write`/`close` do runtime Native agora falam a costura em vez do
  syscall Linux cru, então o perfil macOS consegue ler e escrever pela
  libSystem.** A costura é o ÚNICO lugar onde o runtime fala com o SO, e a
  io falava o ABI Linux direto (o gate 2 contava isso como dívida
  fora-da-costura). 16 sítios migraram (`RuntimeIo2`, `RuntimeIo3`,
  `RuntimeTime`: a leitura do stdin, o read/write de arquivo, o append, os
  ranges de bytes). O comportamento no host é byte-idêntico — os corpos
  da costura no host são os MESMOS syscalls com os MESMOS registradores — e
  o perfil macOS agora chega no `read`/`write`/`close` da libc. **O
  `fstat` CONTINUA CRU DE PROPÓSITO (medido):** os números de syscall são
  `0=read`, `1=write`, `3=close`, **`5=fstat`** — e o `struct stat` do
  Darwin tem layout DIFERENTE do Linux, então a família stat é a PRÓXIMA
  linha M-6 (o seu próprio corpo na costura), não um read disfarçado. (A
  primeira passada desta fatia mapeou o `5` para o read e a suíte de io
  pegou — 4 RED — antes de qualquer coisa subir.) **O RATCHET:** a
  contagem x86-64 fora-da-costura caiu de **180 para 164** e o baseline foi
  regenerado deliberadamente depois da melhora (o fluxo documentado do
  gate; o selftest é 3/3). **PROVA (executada):** `NativeIoSeamE2ETest`
  **2/2** — RED-first **2 falhas** no código antigo (a io não passa pela
  costura) e GREEN depois; a família de io **92 run / 0F / 0E / 9 skipped**
  (`IoE2ETest` 26/0); a bateria nativa **829 run / 0F / 0E / 82 skipped**; os
  gates rc=0. **A MEDIÇÃO PARA A PRÓXIMA LINHA (M-4, o TLS):** o acesso a
  thread-local do Mach-O foi lido no compilador REAL (o clang 18
  provisionado): `movq _x@TLVP(%rip), %rdi; callq *(%rdi); movq (%rax),
  %reg` mais o descritor `__DATA,__thread_vars` (`__tlv_bootstrap`, 0, o
  slot de init) — e a cadeia de exceções do runtime é UMA variável
  (`kof_exc_chain`) lida/escrita em 22 sítios (9 loads, 13 stores). A
  auditoria de registradores já pagou um dividendo: nos sítios de store o
  `%rdi`/`%rax` têm valores VIVOS (o argumento String, o resultado), então
  uma chamada de costura ingênua os estragaria — os corpos do M-4 precisam
  preservar os registradores caller-saved (o par get/set da costura) ou os
  sítios precisam ser reescritos com o valor num registrador que possam
  ceder.
  **M-4 FATIA 1 POUSADA 22/11 (o TLS pela costura — os LOADS da cadeia; e a
  face macOS é uma pthread key, API PÚBLICA):** a cadeia de exceções do
  runtime (`kof_exc_chain`) ganhou um par get/set na costura com contrato
  TRANSPARENTE — o get devolve o valor em `%rax` e preserva todos os outros
  registradores; o set recebe `%rdi` e preserva tudo (inclusive o `%rax`,
  porque o `pthread_setspecific` devolve um int que o chamador não espera).
  Os **9 sítios de LOAD** migraram (a família parse-or-default, o GC, o DB,
  a concorrência); os **13 de STORE** são a próxima fatia — a auditoria de
  registradores mediu que o `%rdi`/`%rax` têm valores VIVOS ali (o argumento
  `String`, o resultado), então uma chamada de costura ingênua os
  estragaria. A **face macOS** usa `pthread_key_create`/
  `pthread_getspecific`/`pthread_setspecific` (medidos no pthread.h do SDK),
  com a chave criada no **STARTUP** (`kof_plat_tls_init`, single-thread —
  sem corrida de criação). A alternativa medida (o TLV do Mach-O:
  `movq _x@TLVP(%rip), %rdi; callq *(%rdi)` + o descritor
  `__DATA,__thread_vars`) foi REJEITADA: obrigaria o tradutor a reescrever
  cada acesso e a emitir o descritor (o `$` nos nomes dos símbolos). **UM
  BUG LATENTE CORRIGIDO NO CAMINHO:** o entry deixava o array do
  `main(String[] args)` no `%rdi` e então chamava a costura do thread-id —
  no macOS (chamada de libc) isso clobbera o `%rdi`, então os args
  chegariam lixo; o entry agora re-materializa o `%rdi` a partir do `%rbx`
  (callee-saved) logo antes do main. **O RATCHET:** os sítios de TLS caíram
  de **24 para 17** (o baseline regenerado depois da melhora). **PROVA
  (executada):** `NativeMacOSTlsE2ETest` **3/3** — RED-first **3 falhas**
  (o par da costura ausente; os corpos macOS ausentes; os símbolos
  `_pthread_*` ausentes) e GREEN depois; a bateria nativa + de exceções
  **946 run / 0F / 0E / 80 skipped**; os gates rc=0. **A PRÓXIMA FATIA
  (M-4):** os 13 stores (o valor precisa chegar num registrador que o sítio
  possa ceder) + a máquina de try/catch do EMITTER (2 sítios dentro do
  método compilado) — aí a última face de TLS do hello some e a recusa
  `NATIVE005` fica muda.
  **M-4 FATIA 2 POUSADA 22/11 — A MIGRAÇÃO DO TLS ESTÁ COMPLETA: o perfil
  macOS não tem MAIS nenhuma face ELF-only, e a recusa NATIVE005 fica MUDA
  (a única recusa que resta é a NATIVE004 = o toolchain macOS ausente, o
  runner do M-7).** Os **18 sítios** restantes (14 no runtime + 4 na máquina
  de try/catch do emitter) passam pela costura: o valor chega no `%rdi` (com
  `%rdi`/`%rcx` empilhados/desempilhados no sítio, e os stores de `%rsp`
  usando `leaq 16(%rsp)` — o ponteiro de pilha original depois dos dois
  pushes), e o corpo do set preserva TUDO, então o `movq %rax, %rsp` que
  segue o unlink continua funcionando (a base no `%rax` sobrevive à
  chamada). **O RATCHET:** os sítios de TLS caíram de **17 para 3** (os 3 são
  o corpo Linux da própria costura), e o baseline foi regenerado. **O TESTE
  DO MARCO:** `theMacosProfileHasNoElfTlsLeft` — o texto emitido do perfil
  macOS não tem **nenhum `%fs:` nem `@tpoff`** (RED no código antigo). Os
  dois testes de recusa agora asseram o marco: a NATIVE005 SUMIU e só a
  NATIVE004 nomeia o toolchain. **PROVA (executada):** a família macOS
  **40/0F**; as famílias UEFI/Ring **8/0F** e **5/0F** (as falhas da suíte
  foram contaminação de meio-de-rodada — ver o registro do veredito); a
  família Troolean está **3/0F** (a correção de RAIZ do #1178 da lane irmã
  pousou: o tipo era `Nullable(Nullable(Bool))`, não um design fork); a
  bateria de exceções + nativa **946/0F**; os gates rc=0. **A FRONTEIRA
  (honesta):** o perfil macOS agora compila o hello limpo até o LINK — o link
  e a execução precisam do runner do M-7 (um host macos-14 de verdade), e é
  isso que a NATIVE004 recusa sem. **AS PRÓXIMAS LINHAS:** a família "stat"
  da costura (o `struct stat` do Darwin), o runner M-7, a face arm64 (o
  emissor cross aarch64 + a sua prova Mach-O); depois #1159 keep-alive
  **M-6 FATIA 5 POUSADA 22/11 — O PONTO CEGO DO PRÓPRIO GATE FECHADO: o
  ratchet x86 vigiava só metade da superfície.** O gate da costura conta o
  acesso a sistema que ainda passa por fora da costura — a promessa é "a
  costura não cresce". Mas o lado x86 varria só o pacote `runtime/`: os
  sítios de syscall x86 do pacote `nat/` (o `socket`/`connect`/`fcntl`/
  `poll`/`setsockopt` do core do HTTP, o web listen, o net front) **não
  estavam no ratchet** — **19 instruções `syscall` reais** medidas fora da
  superfície contada. Um syscall novo ali passaria pelo gate em silêncio.
  **A CORREÇÃO (duas precisões):** o inventário agora varre **os dois**
  pacotes (as faces riscv/aarch64/mcu continuam contadas à parte, e o
  tradutor fica de fora — lá o `syscall` é o DETECTOR, não um sítio emitido);
  e a métrica conta a **instrução** (a linha que É o `syscall`), não a
  palavra em prosa — o pacote `nat/` tem comentários e o texto do diagnóstico
  NATIVE005 que dizem "syscall" sem emitir um sítio (a contagem antiga
  incluía 45 linhas assim só no pacote runtime). A métrica do TLS recebeu o
  mesmo tratamento (um mnemônico com o operando, não uma menção): 3 → 2.
  **PROVA (executada):** com um `syscall` cru plantado num ficheiro do
  `nat/`, o gate ANTIGO devolve **rc=0** (cego) e o NOVO devolve **rc=1** — o
  ponto cego fechou, medido no mesmo sandbox e com o mesmo baseline; o
  selftest do gate cresceu para **4/4** (o caso novo é exatamente este: o
  crescimento no pacote `nat/` tem que falhar). O baseline novo:
  `x86_out_seam` 148, `riscv_out_seam` 168, `tls_sites` 2, `futex_sites` 13 (o
  número x86 CAIU porque a métrica parou de contar prosa — a superfície
  coberta CRESCEU em 19 sítios reais). **O QUE ISSO TOMA MENSURÁVEL (a
  próxima fatia):** a família de sockets do HTTP — os seus equivalentes na
  libSystem (`socket`, `connect`, `fcntl`, `poll`, `setsockopt`) mantêm os
  MESMOS registradores SysV, e as constantes de opção são o que falta
  medir (`SO_RCVTIMEO` é 20 no Linux e 0x1002 no Darwin).

  **M-6 FATIA 4 POUSADA 22/11 (o relógio do `kof.http` pela costura) — o
  circuit-breaker pedia ao Darwin o clock id do LINUX.** O `kof_http_now_ms`
  fazia o syscall `clock_gettime=228` CRU com `CLOCK_MONOTONIC=1` — o id do
  Linux. No Darwin o id é **6** (medido no SDK), então o corpo cru teria
  pedido outro relógio (ou falhado). Agora passa por `kof_plat_time_mono`, a
  costura que já carrega o id de cada plataforma (1 no Linux, 6 no Darwin) —
  e a aritmética dos milissegundos fica num lugar só. **PROVA (executada):**
  `NativeMacOSHttpClockE2ETest` **3/3** — RED-first **2 falhas** no código
  antigo (o clock_gettime cru e a costura ausente) e GREEN depois; a bateria
  nativa + http/web **927 run / 1F** (a falha é a
  `NativeCrossMultidistroE2ETest` da lane irmã em voo — um import
  `image.Raster` ausente no front end, intocado aqui); os gates rc=0. **A
  MEDIÇÃO QUE ESTE CICLO ENCONTROU (o ponto cego do próprio gate):** o lado
  x86 do gate da costura só vigia o pacote `runtime/` — os sítios de syscall
  x86 do pacote `nat/` (o `socket`/`connect`/`fcntl`/`poll`/`setsockopt` do
  core do HTTP, o web listen, o net front) **não estavam no ratchet**: 19
  instruções `syscall` reais e 34 menções ficam fora da superfície contada.
  Um syscall novo ali não dispararia o gate. A próxima fatia fecha esse
  buraco (e a família de sockets do HTTP — cujos equivalentes na libSystem têm
  os MESMOS registradores SysV — é a migração que ele torna mensurável).

  **M-6 FATIA 3 POUSADA 22/11 (a família STAT da costura — e o `struct stat` do
  Darwin NÃO É o do Linux).** Os sete sítios do runtime que chamavam o
  `newfstatat` (syscall 262) direto agora passam por `kof_plat_stat`. O layout
  do Darwin foi **MEDIDO** compilando os `offsetof` com o clang real (o
  toolchain provisionado), não adivinhado: `mode@4` (u16), `nlink@6` (u16),
  `mtimespec@48`, `size@96` — contra o `mode@24` (u32), `nlink@16` (u32),
  `mtim@88`, `size@48` do Linux; e as FLAGS também diferem
  (`AT_SYMLINK_NOFOLLOW` = **0x20** no Darwin, 0x100 no Linux; `AT_FDCWD` =
  **-2**, -100 no Linux). Copiar o corpo do Linux teria feito o runtime ler
  lixo — o `st_size@96` do Darwin seria lido como mode. **A NORMALIZAÇÃO (um
  lugar só, não leituras espalhadas):** o corpo do macOS chama o `fstatat` num
  rascunho de 144 bytes na pilha e copia os QUATRO campos que o runtime usa
  para o buffer do chamador nos offsets do HOST — então **nenhum chamador
  muda**: eles continuam lendo `24/48/88/96(%rsp)`. O corpo do host é o mesmo
  syscall `newfstatat`, logo o comportamento do host não muda (o teste
  assere isso). **O RATCHET:** a contagem x86-64 fora-da-costura caiu de
  **164 para 157** (o baseline regenerado; o selftest do gate é 3/3).
  **PROVA (executada):** `NativeMacOSStatE2ETest` **4/4** — RED-first **4
  falhas** no código antigo (sem costura, sem `fstatat`, sem normalização, sem
  o símbolo `_fstatat`) e GREEN depois; as famílias macOS + io **87/0F**;
  `check_500` rc=0 (`RuntimePlatMacosStat` é uma classe nova, só dela); os
  gates rc=0. **A PRÓXIMA LINHA:** o runner **M-7** (o link + a execução num
  host macos-14 de verdade — a primeira prova de que um binário macOS
  RODA), e depois a face arm64; o relógio do `kof.http` (o
  `kof_http_now_ms` com o `clock_gettime` cru) é a próxima família de syscall
  medida neste ciclo.

  **M-6 FATIA 2 POUSADA 22/11 (a família de REDE sobre a libSystem — e um BUG
  REAL DO TRADUTOR que a fatia expôs: os imediatos de CARACTERE).** As seis
  funções de socket da costura (`kof_plat_net_socket`/`connect`/`bind`/
  `listen`/`accept`/`send`) agora chamam a libSystem no perfil macOS; a ABI
  dos sockets BSD é a MESMA da libc Linux, e a ÚNICA diferença é a 4ª flag do
  `sendto` (o syscall Linux a põe em `%r10`, a ABI SysV em `%rcx` — o corpo
  move). Os corpos são TRANSPARENTES (preservam os caller-saved, então os
  chamadores do runtime — escritos contra o syscall — continuam certos) e
  moram na sua própria classe (`RuntimePlatMacosNet`) porque o arquivo da
  costura cruzou o gate de 500 linhas; a família de TLS foi para
  `RuntimePlatMacosTls` pelo mesmo motivo (o gate agora está rc=0 com as três
  abaixo da faixa crítica). **O BUG DO TRADUTOR (achado pelo assembler REAL,
  não por inspeção):** um imediato de caractere (`$'h'`) era varrido como
  operando — o `h` ganhava o `_` do Mach-O (`$'_h'`) e o clang respondia
  "single quote way too long". O runtime do kof.http compara caracteres
  (`cmpb $'h', 0(%rbx)`), então a face apareceu no momento em que a fatia de
  rede entrou no texto; a correção copia `$'X'` (e o escapado `$'\n'`)
  verbatim. **PROVA (executada):** `NativeMacOSNetE2ETest` **3/3** — RED-first
  **3 falhas** no código antigo (os corpos de socket eram os syscalls crus; o
  sendto não movia a flag; o literal de caractere vinha corrompido) e GREEN
  depois; `NativeMacOSProfileE2ETest` **12/12** (o teste novo do literal
  RED no tradutor antigo); a família macOS **36/0F**; a bateria nativa +
  http/web **877 run / 1F** (a falha é a `NativeCrossMultidistroE2ETest` da
  lane irmã em voo — um import `image.Raster` ausente no front end, intocado
  por esta fatia); o gate da costura rc=0 (futex 13, TLS 3); o `check_500`
  rc=0 depois dos splits; o gate do changelog-ledger está vermelho na ponta
  pelas linhas §1193/§1194/§1195 da lane IRMÃ (sem entrada no CHANGELOG ainda
  — escrituração deles, reportada e não tocada).

  HTTP/1.1.



  **M-5 FATIA 1 POUSADA 22/11 (a face da concorrência) — o port expôs um DEFEITO
  REAL no runtime x86-64: os argumentos do futex eram passados nos
  registradores ERRADOS, então o "futex wait" era um no-op com EINVAL (uma
  espera que nunca dorme) e o wake do scheduler acordava o endereço ERRADO.**
  O CONTRATO da costura é a ABI do futex — `kof_plat_sync(uaddr=%rdi,
  op=%rsi, val=%rdx, timeout=%r10)` — declarada do mesmo jeito na face
  riscv (`kof_plat_sync(uaddr=a0, op=a1, val=a2, timeout=a3)`). O repo já
  conhecia essa classe de bug: a nota do "bug 50" no `RuntimeChannel` diz que
  o código antigo chamava o syscall 202 com `rdi=chan` (o uaddr errado) e
  `rsi=&lock` (usado como op) — "comportamento indefinido na contenção
  (spawn)". A correção pousou no `RuntimeChannel` e no `RuntimeLock`, mas NÃO
  no `kof_alloc` nem no scheduler: os sítios de WAIT deixavam o ENDEREÇO em
  `%rsi` (o registrador do op) e um valor velho em `%rdi`, e os sítios de
  WAKE do scheduler acordavam o PONTEIRO DO JOB em vez do lock. A CORREÇÃO
  usa o padrão já estabelecido (`leaq <lock>(%rip), %rdi; xorl %esi, %esi`
  no wait — mais restaurar o endereço para o `cmpxchg` — e `leaq <lock>(%rip),
  %rdi` no wake). **MEDIDO no binário real (strace):** as chamadas de futex
  eram `futex(0x40a780, FUTEX_WAKE, 0)` — e o kernel honra esse val
  acordando UM waiter (o wake devolveu 1 — o comportamento não documentado
  mas medido, então os sítios de wake existentes estavam certos no
  efeito); os sítios de wait nunca apareceram com um op válido, porque a
  seção crítica (pequena) quase sempre é ganha pelo spin. **O ID DA THREAD
  no macOS:** o Darwin não tem `gettid`; a API pública é
  `pthread_threadid_np(NULL, &tid)` (medido no header do SDK, e o
  `_pthread_threadid_np` está na lista de exportação da libSystem) — o corpo
  guarda o slot do id na pilha e alinha a pilha para a chamada da libc. **O
  RATCHET:** os syscalls Linux crus do hello real caíram de **2 para 1** —
  só o futex (`kof_plat_sync`) ficou na face x86-64 da costura. **PROVA
  (executada):** `NativeConcurrencySeamE2ETest` **4/4** — RED-first **4
  falhas** no código antigo (o contrato quebrado no wait do `kof_alloc`; o id
  da thread do macOS ainda `gettid`; o símbolo `_pthread_threadid_np`
  ausente; o ratchet) e GREEN depois; a prova do contrato é ESTRUTURAL e
  DETERMINÍSTICA (o teste percorre o assembly emitido e, em CADA call de
  `kof_plat_sync`, exige o op constante, o uaddr endereço e o val constante —
  sem corrida envolvida); a família de concorrência continua verde
  (`KofConcurrency2Test` 50/0, `KofAwaitTest` 8/0, `SpawnAwaitBlockE2ETest`
  5/0, `MakealiveReconcileE2ETest` 1/0, `WorkflowPrimitivesE2ETest` 6/0); a
  bateria nativa **823 run / 0F / 0E / 81 skipped**; os gates rc=0. **A
  PRÓXIMA FATIA:** o corpo macOS da sincronia (o futex →
  `__ulock`/`os_unfair_lock`) — a API ulock é PRIVADA (ela NÃO está nos
  headers do SDK, mas a libSystem exporta `___ulock_wait`/`___ulock_wake` —
  medido na lista de exportação), então a semântica dela precisa do contrato
  do XNU e de um runner real (M-7) para ser provada.
  **M-5 FATIA 2 POUSADA 22/11 (a sincronia macOS sobre o ulock do Darwin — e
  a face x86-64 da costura agora é TODA libSystem):** o futex virou a API
  **ulock**, e o mapeamento foi MEDIDO na fonte do XNU, não adivinhado —
  `bsd/sys/ulock.h` (`__ulock_wait(uint32_t op, void *addr, uint64_t value,
  uint32_t timeout_us)`, `__ulock_wake(uint32_t op, void *addr, uint64_t
  wake_value)`, `UL_COMPARE_AND_WAIT = 1`, `ULF_WAKE_ALL = 0x100`) e
  `bsd/kern/sys_ulock.c` (o wait faz `copyin_atomic32` e só dorme quando
  `*addr == value` — o futex WAIT; o wake sem `ULF_WAKE_ALL` chama
  `waitq_wakeup64_one`, com ele `waitq_wakeup64_all`; o `wake_value` é
  ignorado no `COMPARE_AND_WAIT`). O CORPO: o comando do futex são os 7 bits
  baixos do op (`andl $0x7f` — a flag `FUTEX_PRIVATE` fica acima e é
  irrelevante no Darwin), o WAIT vira `__ulock_wait(1, uaddr, val, 0)`
  (timeout 0 = nenhum, como o NULL do futex) e o `futex WAKE(INT_MAX)` do Kof
  vira `__ulock_wake(1 | ULF_WAKE_ALL, uaddr, 0)` enquanto uma contagem menor
  acorda um. A FRONTEIRA (honesta): a API ulock é **PRIVADA** — ela NÃO está
  nos headers do SDK (medido), mas a libSystem EXPORTA
  `___ulock_wait`/`___ulock_wake` (a lista de exportação, com os três
  underscores — os nomes que o assembly carrega) — então a fiação é provada
  pelo texto + o assembler real, e a semântica em execução pelo runner do
  M-7. **O RATCHET:** os syscalls Linux crus do hello real caíram de **1 para
  0** — a face x86-64 da costura é TODA libSystem agora, e a recusa
  `NATIVE005` nomeia SÓ a face de TLS (a linha M-4). **O GATE:** a contagem de
  futex do `check_macos_seam.sh` agora EXCLUI a costura (a costura é a
  superfície da migração, não a dívida fora dela) — o número caiu de 15 para
  13 e o baseline foi regenerado deliberadamente depois da melhora (o
  selftest é 3/3). **PROVA (executada):** `NativeConcurrencySeamE2ETest`
  **7/7** — RED-first **4 falhas** (a sincronia macOS ainda no `syscall 202`;
  o ratchet em 1; os símbolos `___ulock_*` ausentes; a recusa ainda nomeando
  a face de syscall) e GREEN depois; o **assembler Mach-O real aceita o corpo
  da sincronia** (o objeto carrega `___ulock_wait`/`___ulock_wake` como
  externos indefinidos); a bateria nativa **826 run / 0F / 0E / 81 skipped**;
  a família de concorrência inalterada (o caminho host não é tocado); os
  gates rc=0 nas superfícies desta lane (`check_release_blockers` está
  vermelho na ponta porque as issues novas #1187–#1189 não têm linha no
  ledger — o arquivo da lane de triagem, reportado e não tocado).
- **Bare-metal / bootável** (microcontrolador, BIOS legado, UEFI) — diretiva da
  mantenedora 15/09 ("todo código nativo deve se comunicar direto com barebones").
  O runtime riscv64/aarch64 em asm puro (sem libc) é a base natural, mas os
  emitters estão fixados a `ecall`/`syscall` Linux e a uma ABI `_start`, e não há
  perfil de link freestanding. EM DESENVOLVIMENTO desde 22/09:
  `docs/PLAN-BAREMETAL-BOOT.md` (faces B-0…B-6).
