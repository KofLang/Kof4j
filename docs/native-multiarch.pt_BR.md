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
- **Bare-metal / bootável** (microcontrolador, BIOS legado, UEFI) — diretiva da
  mantenedora 15/09 ("todo código nativo deve se comunicar direto com barebones").
  O runtime riscv64/aarch64 em asm puro (sem libc) é a base natural, mas os
  emitters estão fixados a `ecall`/`syscall` Linux e a uma ABI `_start`, e não há
  perfil de link freestanding. EM DESENVOLVIMENTO desde 22/09:
  `docs/PLAN-BAREMETAL-BOOT.md` (faces B-0…B-6).
