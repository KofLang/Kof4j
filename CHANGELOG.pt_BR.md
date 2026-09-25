[English](CHANGELOG.md) | [Português](CHANGELOG.pt_BR.md)

# Changelog

Todas as mudanças relevantes do Kof são registradas aqui.

O formato segue [Keep a Changelog](https://keepachangelog.com/) com a convenção
de commits do projeto (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`,
`build:`, `tooling:`). A seção de cada release é gerada por
`scripts/changelog.sh` e inserida pela pipeline neste marcador:

## [0.5.0-beta] - unreleased (branch `beta-0.5.0`)

  - **§502 — método desconhecido num `Handle<T>` de `spawn` agora é `SEM025`
    ([FECHADO](docs/bugs-and-gaps/known-bugs.pt_BR.md#502--metodo-desconhecido-num-handlet-de-spawn-compilava-limpo-e-emitia-completablefuturebogus--nosuchmethoderror---corrigido))** (26/09): `val h = spawn { ... }` + `h.bogus()` compilava limpo e
    emitia `invokevirtual .../CompletableFuture.bogus` → `NoSuchMethodError`. Um
    Handle não tem método de instância em Kof; o typer agora recusa com o hint
    `await h`. RED 1/1 → GREEN `BuiltinUnknownMethodGuardTest` 16/16.
  - **`RISCV_RUNTIME_ASM_SHELL`/`RISCV_RUNTIME_ASM_SSH`/`RISCV_RUNTIME_ASM_PIPELINE` deixaram de ser constantes de compile-time (§257 guard)**
    (26/09): o `bf5e3e03` (SHELL/SSH) e a `NativeRiscvAsmPipeline` da fatia B2
    da linha 2 (PIPELINE, `RISCV_RUNTIME_ASM_PIPELINE`) deixaram
    `public static final String` + text block, que o javac inlina nos
    consumidores; `RuntimeConstantInliningGuardTest` estava vermelho. Removido
    o `final` (campo de runtime). 2/2 verde.

  - **`process.spawn` + ops de handle no Native x86-64 (`D-FULL-PARITY-050`
    linha 1 fatia B)** (26/09): nova `RuntimeProcessSpawn` implementa
    `kof_process_spawn` (tabela persistente de 64 slots no `.bss`, handle =
    indice; `pipe2` + `fork` + `execvp` da libc; stdin/stderr do filho
    `/dev/null`; exec falho = `-1`), `kof_spawn_read_line` (linha sem a
    quebra, `""` em EOF), `kof_spawn_exit_code` (`Integer.MIN_VALUE` vivo,
    `-1` morto/killado), `kof_spawn_alive`, `kof_spawn_kill` (SIGKILL + reap +
    esquece) e `kof_spawn_write` (no-op honesto — stdin e `/dev/null` nos dois
    alvos; entrada viva e mudanca de contrato, regra 6).
    `ExpressionProcessCallLowerer` agora emite `spawn` no x86-64 e mantem o
    `PROC001` honesto no cross/MCU. Novo `ProcessSpawnNativeE2ETest` 4/4
    (paridade byte JVM≡x86-64); `DomainGapCodesTest` repontado (x86 compila,
    cross `PROC001`).
  - **`ssh.cmd`/`run`/`ok` no x86-64 (`D-FULL-PARITY-050` linha 3 fatia A)**
    (26/09): nova `RuntimeSsh` emite `kof_ssh_argv` (oraculo JVM exato
    `[ssh,-o,BatchMode=yes,-o,ConnectTimeout=5,host,command]`) e `kof_ssh_run`
    (reusa `kof_process_run`); o `ExpressionSshCallLowerer` agora emite no
    x86-64 e mantem o `PROC001` honesto no cross. Os pins nativos antigos do
    `SshE2ETest` viraram o positivo. `SshE2ETest` 9/9 (argv x86 == JVM
    byte-a-byte, host e comando = 1 elemento cada).

  - **§501 CORRIGIDO — `cache.ttl` no Native riscv64/aarch64 devolvia 0 onde o
    oráculo JVM/x86-64 devolve -1** (26/09, lane parity — linha 9 do ledger):
    `NativeRiscvAsmRtB2.kof_cache_ttl` roteava os casos chave-ausente, sem-TTL e
    expirado para um rótulo compartilhado `.Lct_miss` cujo corpo devolvia `0` —
    um "0 segundos restantes" plausível que escondia o bug de paridade (R6).
    `.Lct_miss` agora devolve `-1`, byte-a-byte com o oráculo JVM/x86-64. Novo
    `KofCacheCrossTest` (riscv64+aarch64 sob qemu) era RED antes (4/4 falhas) e
    GREEN depois; `KofCacheE2ETest` 5/5 inalterado.

  - **`ssh.cmd`/`run`/`ok` no cross riscv64/aarch64 (`D-FULL-PARITY-050` linha 3
    fatia B)** (26/09): nova `NativeRiscvAsmSsh` emite o mesmo argv-oraculo JVM e
    reusa o `kof_process_run` cross; o lowerer agora emite em todo nativo exceto
    MCU/riscv32. `SshE2ETest` 10/10 + novo `SshCrossE2ETest` 2/2 — argv == JVM
    byte-a-byte nos 3 nativos e host inalcancavel = `Result` honesto
    (exitCode != 0), nunca crash.

  - **`shell.runWith` no cross riscv64/aarch64 (`D-FULL-PARITY-050` linha 2
    fatia B1)** (26/09): nova `NativeRiscvAsmShell.kof_shell_runwith` spawna
    argv-first (`argv[0]` = programa, o resto = args) via `kof_process_run`;
    o caso herdado (`cwd=""/null` + env vazio) e byte-paridade com o JVM.
    `cwd`/`env` NAO-VAZIOS nunca sao ignorados (R6): devolvem `Result` honesto
    (exitCode -1, mensagem no stderr). `pipeline` segue `PROC001`.
    `ShellCrossE2ETest` 5/5 + `ShellE2ETest` 19/19.

  - **`shell.pipeline` no cross riscv64/aarch64 (`D-FULL-PARITY-050` linha 2
    fatia B2)** (26/09): nova `NativeRiscvAsmPipeline.kof_shell_pipeline`
    encadeia os estagios com pipes reais do SO (estagio 0 stdin `/dev/null`,
    stdout do i -> stdin do i+1, stderr intermediario descartado, so o ultimo
    capturado) via `clone`+`dup3`+`execvp`, sem threads — byte-paridade com o
    JVM; vazio/sem estagio/>16 = Results honestos. Corrigido o argv do filho
    (`cat` rodava `[cat,cat]`). `ShellCrossE2ETest` 7/7 + `ShellE2ETest` 19/19.

  - **`shell.run`/`cmd`/`ok` no CROSS nativo riscv64/aarch64 (`D-FULL-PARITY-050` linha 2)**
    (26/09): `shell.run` reusa `kof_process_run` (linha 1 fatia C), `shell.ok` e
    IR puro sobre o acesso `exitCode` e `shell.cmd` usa a peca nova
    `NativeRiscvAsmShell` (`kof_shell_argv` = prepend do program — o oraculo JVM
    exato, sem split); `pipeline`/`runWith` seguem `PROC001` honesto (fatia B).
    Corrige tambem um pin STALE do `ShellE2ETest` que ainda exigia `process.run`
    cross = `PROC001` depois da linha 1 fatia C. RED->GREEN: `ShellCrossE2ETest`
    3/3 byte-parity JVM==riscv64==aarch64 (run/cmd/ok), `ShellE2ETest` 18/18.
    Bug medido no caminho: a peca nao trocava p/ `.section .text` e caia no
    `.bss` herdado -> `kof_shell_argv` nao-executavel (SIGSEGV 139); fix =
    `.section .text`.

  - **`process.run` no CROSS nativo riscv64/aarch64 (`D-FULL-PARITY-050` linha 1, fatia C)**
    (26/09): `process.run` agora compila e roda nos 2 alvos cross. Nova fatia
    `NativeRiscvAsmProcess`: `clone(220, SIGCHLD)` (riscv64 nao tem `fork`),
    filho `dup3`->1/2 e stdin `/dev/null`, `List`->`argv` montado na pilha do
    filho, `execvp` da libc para PATH, pai drena os 2 pipes com `ppoll`
    (mascara `0x19`) + `wait4`; exec falho = `("", msg, -1)`. Buffers de dreno
    estaticos de 1 MiB no `.bss` da fatia (a arena GC cross segue 256 KiB);
    `execvp` no `NativeCrossLink.LIBC_SYMBOLS` (link dinamico so quando a fatia
    e alcancavel) e o gate `PROC001` agora recusa so `process.spawn` no cross.
    RED->GREEN: `ProcessRunCrossE2ETest` 6/6, JVM==riscv64==aarch64 byte-a-byte
    (echo/PATH, exit!=0, separacao stdout/stderr, 29 KB de stdout sem deadlock,
    programa inexistente -1), mais drain concorrente dos 2 streams grandes
    (`seq 1 4000` + `seq 1 2000` no stderr, sem deadlock) e pin de argv com
    espaços (`echo "a b" c`). `spawn` cross segue `PROC001` honesto (fatia B).

(0.2.6) preservada — mudanças aqui são aditivas ou com bump deliberado.

  - **§499 — método estático desconhecido em nome de tipo builtin agora é `SEM074`
    ([FECHADO](docs/bugs-and-gaps/known-bugs.pt_BR.md#499--metodo-estatico-desconhecido-em-nome-de-tipo-builtin-stringbogus-intbogus--compilava-limpo-e-emitia-invokestatic-ownerbogus--nosuchmethoderror---corrigido))** (26/09): `String.bogus()`, `Int.bogus()`,
    `Bool`/`Long`/`Double`/`Float`/`Char`/`Byte`/`Short`/`Object.bogus()` compilavam limpo e emitiam
    `invokestatic <Owner>.bogus` (`Char`: owner vazio) → `NoSuchMethodError` em
    runtime. O typer agora confirma o método contra o owner JDK com o novo
    `JdkReflectionResolver.hasJdkMethod` ciente de varargs e recusa um ausente em
    compile time — estáticos reais de interop (`String.valueOf`/`join`/`format`,
    `Long.parseLong`, `Double.isNaN`, …) seguem compilando. RED 3/3 → GREEN
    `BuiltinUnknownMethodGuardTest` 14/14; suíte 4016 0F/0E.
  - **§498 — membro desconhecido em tipos kof.ui / no namespace `web` vira diagnóstico**
    (25/09): `Palette.bogus`, `Color.bogus`, `Theme.bogus` (campos) compilavam
    limpo e emitiam `getfield "?".bogus`; `web.bogus()` compilava limpo e o
    lowering não emitia nada (no-op silencioso; como expressão, `VerifyError`).
    O ramo da paleta agora diagnostica cor desconhecida e uma nova guarda de
    campo em tipo construtor UI emitem `SEM079`; o método no namespace `web`
    agora emite `SEM025`. RED→GREEN: `BuiltinUnknownFieldGuardTest` 23/23 +
    `BuiltinUnknownMethodGuardTest` 10/10; controles Palette.red / Color.rgba /
    Theme.light / web.app compilam. Mesma família do §495/§496.

  - **§496 — campo desconhecido em namespace builtin é um SEM102 limpo**
    (25/09): `math.bogus` (e até `math.PI`, `strings.EMPTY`, `time.EPOCH`,
    `db.bogus`, …) compilava limpo; o receiver não é local nem tipo, então o
    `FieldAccessExpr` inferia UNKNOWN e o emitter usava o nome de classe vazio
    como owner do `getfield` → `getfield "?".bogus` →
    `NoClassDefFoundError: "?"` no load (escondido atrás do launcher JavaFX).
    O novo `SemUndefinedVarGuard.isBuiltinNamespace(name)` centraliza as
    famílias de namespace puras (excluindo paletas/tokens/enum/Theme/interop,
    que mantêm caminho próprio) e o `case FieldAccessExpr` do
    `SemExpressionTyper` agora emite `SEM102`. RED→GREEN:
    `BuiltinUnknownFieldGuardTest` (`unknownMathFieldIsSem102`,
    `plausibleButInvalidNamespaceConstantIsSem102` para `math.PI`,
    `unknownStringsNamespaceFieldIsSem102`,
    `unknownTimeNamespaceFieldIsSem102`) 4/4 falham pré-fix; 19/19 depois
    (controle `namespaceMethodCallsStillCompile`). Face CAMPO do §495/#126/§490.

  - **§495 — método desconhecido no namespace `scheduler` é um SEM025 limpo**
    (25/09): `scheduler.bogus()` passava no `kof check` e abortava no load com
    `VerifyError: Operand stack underflow` (o lowering não emitia nada). O gate
    semântico de namespaces `MemberCallNamespaces.inferStatic` cobria
    db/log/orm/std/... mas não `scheduler`; agora roteia por
    `KofScheduler.staticCall` e emite SEM025 em método desconhecido (mesma
    família de #126/§490). RED→GREEN:
    `BuiltinUnknownMethodGuardTest#unknownSchedulerMethodFailsWithSem025`
    (+ controle `validSchedulerMethodsStillCompile`), 8/8.

  - **`time.collect()` é real no JS — linha 8 do D-FULL-PARITY-050 FECHADA;
    §426 melhorado** (25/09): o antigo gate `TIME004` em compile-time era um
    fallback, não o estado final. A raiz de fato do `ReferenceError` histórico
    era o `JsRuntimeOps.isRuntimeOp` não reconhecer `kof_gc_collect_now`, então
    o emitter produzia uma chamada crua `kof_gc_collect_now(...)` (o mapeamento
    do nome nunca era alcançado). Agora: `isRuntimeOp` reconhece,
    `JsRuntimeTime` EXPORTA `kofGcCollectNow`, e `KofJsRunner` expõe
    `kof_platform.gcCollect` → `System.gc()` — a semântica exata do
    `kof_gc_collect_now` do JVM/SCRIPT (um pedido de GC, não garantia); um
    runtime sem host (browser) lança erro honesto (R7), nunca no-op silencioso.
    O gate é removido do `KofTime` (`supportedOn`/`gapCode`), a linha de gap
    documentado TIME004 do `backend-parity.md` cai, e
    `DomainGapCodesTest.collectOnJsIsTime004` virou
    `collectOnJsHasNoGap`/`collectOnJvmAndX86HasNoGap` (com as arches cross).
    Verificado no tip que a célula cross da linha 8 estava OBSOLETA:
    `addDays`/`diffDays` + as faces novas já eram golden em riscv64/aarch64
    (`KofTimeE2ETest` cross-arch, **44/44, 0 skip**). Prova:
    `KofTimeE2ETest#collectJsRunsOnHost` (JS compila E roda até o fim),
    `KofJsE2ETest` 40/40,
    `JsRuntimePruneWriterTest`/`JsRuntimeSliceRegistryTest` 12/12.

  - **B4 follow-up (a) (baremetal MCU) — espelho Cortex-M3 do coletor e dos
    corpos de tempo (`NativeMcuArmGc`/`NativeMcuArmGcSweep`/`NativeMcuArmTime`)**
    (25/09): o coletor G-4/G-5 do `native-multiarch.md` e os corpos
    `kof_plat_time*` foram portados de RV32I para Thumb-2/AAPCS (mesmo header de
    16 B, payload `block+16`; sem `udiv` — decimal por subtração repetida;
    callee-saved r4-r11; o guard do mark perde o limiar fixo 4096 do RV32I, já
    que no CM3 o heap começa abaixo dele (~0x5e0)). Bug raiz pego pelo teste
    (Q0): a posição bruta do SysTick (`0xFFFFFF-SYST_CVR`) volta a zero no wrap
    de 2^24, quebrando o contrato monotônico — o fix acumula os deltas
    `(last-CVR) mod 2^24` num contador de 32 bits (`boot=0`, só dá a volta em
    2^32). `NativeMcuArmTime` está ligado ao `NativeMcuArm.renderAsm` para
    paridade riscv32/cortex-m. Prova: `NativeMcuArmGcTest` 8/8 (alloc/dump, mark
    transitivo, sweep/ciclo, laço de 10000 allocs reciclando, free-reuse, OOM
    nomeado e a sabotagem que remove o hook do coletor → `out of memory`) +
    `NativeMcuArmTimeTest` 2/2 + `NativeMcuArmE2ETest` 6/6 (agora também
    asserindo `kof_plat_time`/`kof_plat_time_mono` na imagem) sob
    `qemu-system-arm -M mps2-an385`.

  - **B4-TIME (baremetal MCU) — corpos de tempo do MCU: recusa NOMEADA do wall +
    contador monotônico (`NativeMcuTimeRiscv32`)** (25/09): por
    `D-BAREMETAL-MCU-GC` item 2, num MCU sem RTC o `kof_plat_time` (`time.now()`)
    é **recusa NOMEADA** (escreve `NATIVE002: time.now() unavailable on MCU
    (no RTC)` e sai(1) — nunca epoch falsa, R6/R7); `kof_plat_time_mono` lê o
    contador `time` do RISC-V (`boot=0`, `ts[0]=0`, `ts[4]=contador` — um
    CONTADOR, não ns calibrados, como sancionado) e `kof_plat_sleep` faz
    busy-wait nele. O emissor (`NativeMcuRiscv32`) agora emite esses corpos.
    Prova: `NativeMcuTimeTest` 2/2 sob `qemu-system-riscv32 -M virt` (a mensagem
    de recusa do wall é observada; duas leituras de `kof_plat_time_mono` voltam
    não-decrescentes), `NativeMcuE2ETest` 7/7 inalterado.

  - **B4-GC-3/4 (baremetal MCU) — sweep + reuso long-running do coletor do MCU**
    (25/09): o sweep do `native-multiarch.md` G-4 portado para RV32I em
    `NativeMcuGcRiscv32Sweep` (mark==1 → limpa o mark; mark==0 e !free →
    free-list + frees/free_bytes; sem lock — o MCU não tem `spawn`/threads pelo
    §6 do plano). O `kof_alloc` agora, no OOM, roda um `kof_gc_collect_now`
    (mark+sweep) e re-tenta a free-list exatamente UMA vez antes do panic
    nomeado (R6) — o gancho que recicla o heap. Prova: `NativeMcuGcTest` 8/8 sob
    `qemu-system-riscv32 -M virt` — o sweep recupera a C morta (`gc 32 0, gc 32 2,
    gc 32 0, gc 32 0`, `frees: 1`, `live bytes: 96`); um ciclo A<->E termina e os
    dois sobrevivem; um laço de 10000 alocações de 16 B sobre um heap de 64 KB
    completa (`allocs: 10000`) por reciclagem; e a sabotagem (remover o gancho de
    collect no OOM) reproduz o esgotamento (`out of memory`), provando que o
    coletor não é vazio.

  - **B4-GC-2 (baremetal MCU) — mark conservador do coletor do MCU
    (`NativeMcuGcRiscv32`)** (25/09): o mark do `native-multiarch.md` G-3
    portado para RV32I sobre o header de 16 bytes. `kof_gc_try_mark` restringe
    candidatos a `[_kof_heap_start,_kof_heap_end)` + alinhamento de 4 bytes,
    localiza o bloco-dono na gc-list e seta o bit de mark (flags bit0);
    `kof_gc_mark_transitive` varre o payload de um bloco recém-marcado word a
    word e recorre (fecho conservador); `kof_gc_mark` derrama os s0-s11 e varre
    as raízes estáticas `.Lkof_heap_root_start..end` mais o intervalo de pilha
    `[sp,_stack_top)` (sem depender de frame pointer). Prova: `NativeMcuGcTest`
    4/4 sob `qemu-system-riscv32 -M virt` — o grafo alcançável (raiz estática A,
    raiz de pilha B, A.payload[0]→D) volta marcado `gc 32 1, gc 32 0, gc 32 1,
    gc 32 1` (LIFO D,C,B,A) com a C inalcançável em 0, provando as raízes
    estática/pilha e o fecho transitivo. Segue o sweep/reuso (B4-GC-3).

  - **B4-GC-1 (baremetal MCU) — alocador 32-bit do coletor do MCU
    (`NativeMcuGcRiscv32` + `NativeMcuGcTest`)** (25/09): primeira fatia do
    coletor `native-multiarch.md` G-4/G-5 portado para 32-bit, por
    `D-BAREMETAL-MCU-GC`. `kof_alloc(size)` com header de **16 bytes** (size /
    free_next / gc_next / flags), free-list first-fit, bump sobre heap
    **dimensionado pelo linker script** (`_kof_heap_start.._kof_heap_end`,
    escala de KB), inserção na gc-list, `kof_free`, `kof_gc_dump`
    (`gc <size> <flags>`) e `kof_memstats` — **RV32I puro** (sem `div`/`rem`:
    decimal por subtração repetida). OOM → panic nomeado `out of memory`
    (R6/Q7). Prova: `NativeMcuGcTest` 3/3 sob `qemu-system-riscv32 -M virt` com
    harness asm cru (4 allocs → `gc 32 0` ×4, `allocs: 4`, `live bytes: 128`;
    free+alloc reusa → 1 bloco, `allocs: 2`/`frees: 1`/`live bytes: 32`;
    OOM → `out of memory`). Segue mark/sweep (B4-GC-2/3), a prova long-running
    (B4-GC-4), o espelho Cortex-M3 (B4-GC-5) e o tempo SysTick (B4-TIME).

  - **S5.5 fatia 5d (db-parity, lane gaps-db) — `orm.page` sobre o wire MySQL no
    cross (peças `B81` + `B81Helpers`)** (24/09): o `kof_orm_page` desvia em
    `kof_db_type == 2` e faz tail-call para `kof_orm_page_mysql` —
    ``SELECT * FROM \`t\` LIMIT <lim> OFFSET <off>`` com os boxes lim/off
    convertidos como o host (`((Number)x).intValue()`, box Long incluso) por
    `kof_orm_mysql_pv` → `kof_long_to_string`; mesmo walk de pacotes/
    materialização de record, página vazia = lista vazia. **S5.5 COMPLETA** —
    todas as faces de leitura (`find`/`all`/`where`/`where_op`/`page`) e as
    escritas (`save`/`saveAll`/`delete`/`deleteAll`/`count`/`count_where`) agora
    rodam byte-idênticas em JVM + x86-64 + riscv64 + aarch64 sobre o wire MySQL;
    linhas 15 (`orm.*`) e 16 (`db.*`) do `PARITY-GAPS` fechadas. Prova:
    `KofOrmE2ETest#crossNativeMariadbPageMatchesOracles` — JVM + x86-64 +
    riscv64 + aarch64 byte-idênticos; `KofOrmE2ETest` 82/0F/2skip,
    `NativeRiscvDbWireTest` 41/0F, `NativeRiscvRuntimeSliceRegistryTest` 9/9.

  - **S5.5 fatia 5c (db-parity, lane gaps-db) — `orm.where`/`where_op` sobre o
    wire MySQL no cross (peças `B80` + `B80Helpers`)** (24/09):
    `kof_orm_where`/`kof_orm_where_op` desviam em `kof_db_type == 2` e fazem
    tail-call para `kof_orm_where_mysql` — ``SELECT * FROM \`t\` WHERE \`f\` <op> ?``,
    value literal por `kof_orm_mysql_lit` + `kof_db_mysql_replace_q`. A whitelist
    do op é o port mysql do `RuntimeOrmMysqlOp` (`kof_orm_mysql_op`: `==`→`=`,
    `>` `<` `>=` `<=` `!=` `LIKE`, resto throw
    `ORM operator not allowed: <op>`; a face `where` default é `=`). Mesmo walk
    de pacotes/materialização de record; lista vazia se nada casar. Prova:
    `KofOrmE2ETest#crossNativeMariadbWhereMatchesOracles` — JVM + x86-64 +
    riscv64 + aarch64 byte-idênticos (incl. o throw exato e a lista vazia);
    `KofOrmE2ETest` 81/0F/2skip, `NativeRiscvDbWireTest` 41/0F,
    `NativeRiscvRuntimeSliceRegistryTest` 9/9. A última face (`page`) reusa o
    mesmo reader.

  - **S5.5 fatia 5b (db-parity, lane gaps-db) — `orm.all` sobre o wire MySQL no
    cross (peça cross `B79`)** (24/09): o `kof_orm_all` desvia em
    `kof_db_type == 2` e faz tail-call para o novo `kof_orm_all_mysql`, o mesmo
    walk de pacotes do `find` mas com `SELECT * FROM \`t\`` (sem bind),
    resolvendo `kof_orm_ctors` uma vez antes do loop e acumulando um record por
    linha via `kof_list_new`/`kof_list_add` — **lista vazia** (nunca null) quando
    não há linhas; dead/ERR/`no column` → throw (R6). Prova:
    `KofOrmE2ETest#crossNativeMariadbAllMatchesOracles` — JVM + x86-64 +
    riscv64 + aarch64 byte-idênticos (`3\nMel/30\nAna/25\nLeo/40\n2\nMel\nLeo\n0`);
    `KofOrmE2ETest` 80/0F/2skip, `NativeRiscvDbWireTest` 41/0F,
    `NativeRiscvRuntimeSliceRegistryTest` 9/9.

  - **S5.5 fatia 5a (db-parity, lane gaps-db) — `orm.find` sobre o wire MySQL no
    cross (peça cross `B78`)** (24/09): o `kof_orm_find` desvia em
    `kof_db_type == 2` e faz tail-call para o novo `kof_orm_find_mysql`, que
    envia ``SELECT * FROM `t` WHERE `pk` = ?`` (o key vira literal por
    `kof_orm_mysql_lit`/`B75` + `kof_db_mysql_replace_q`/`B71`), percorre o
    resultset com o reader `B68` + lenenc `B63`, casa as colunas por **nome**
    com o schema e converte cada célula pelo **typeCode**
    (`kof_orm_mysql_atoi`, `kof_orm_mysql_bool` §397, `kof_io_make_string`,
    `kof_string_to_double`/`kof_string_to_float`; NULL → 0/null/false). Nenhuma
    primitiva nova de wire foi necessária — a ABI de coluna tipada mora dentro do
    walk. Miss → `null`; ERR do servidor → `mysql: <msg>`; campo do schema sem
    coluna → `mysql: no column <nome>` (R6). Prova:
    `KofOrmE2ETest#crossNativeMariadbFindMatchesOracles` — JVM + x86-64 +
    riscv64 + aarch64 byte-idênticos (`1\nMel\nm@kof.dev\n30\nnull\nAna/25\nAna/25\nMel`);
    `KofOrmE2ETest` 79/0F/2skip, `NativeRiscvDbWireTest` 41/0F,
    `NativeRiscvRuntimeSliceRegistryTest` 9/9. As faces restantes
    (`all`/`where`/`where_op`/`page`) reusam o mesmo reader.

  - **B-4.3 fatia 1 (baremetal MCU) — emissor Cortex-M3/Thumb-2 `NativeMcuArm`
    + `Target.NATIVE_MCU_ARM`** (24/09): o segundo codegen bare-metal 32-bit,
    irmão da fatia RV32I. `main` com `print`/`println` de literal String baixa
    para escritas na UART CMSDK (UART0 `0x40004000`, `CTRL` `+0x08`=3
    TXEN|RXEN, `STATE` `+0x04` bit0 TXFULL, `DATA` `+0x00`) e faz halt; a imagem
    carrega uma **vector table** em `0x0` (`[0]`=`_stack_top` `0x00080000`,
    `[1]`=`Reset_Handler|1`, `[3]`=`HardFault_Handler` → halt honesto). Os
    corpos HAL `kof_plat_write/_exit/_thread_id/_random` são emitidos em Thumb-2
    (random = xorshift32 com semente não-zero). Qualquer op fora do subset →
    `NATIVE002`; concorrência → `CONC003` (single-core, nunca stub). As
    ferramentas resolvem por `KOF_MCU_AS`/`KOF_MCU_LD` ou pelo prefixo sem root
    `~/.local/share/kof-mcu` (`arm-none-eabi-as/ld`). Sem superfície CLI ainda
    (machinery programática, como a fatia RV32I). Prova: `NativeMcuArmE2ETest`
    6/6 sob `qemu-system-arm -M mps2-an385` (hello pela UART, ordem/newlines do
    print, reset path da vector table == o `Reset_Handler` real, símbolos HAL no
    `nm`, `CONC003`/`NATIVE002`), mais `NativeMcuE2ETest` 7/7 +
    `StdParityGapAuditTest` 16/0 (as duas fatias MCU ficam fora da matriz de
    paridade da stdlib).

  - **Ressincronização da contagem viva (docs) — README + release-prep §493**
    (24/09): o `§493` (JVM e Native divergem no caminho de erro do
    `orm.delete`/`deleteAll` MySQL, catalogado pela lane gaps-db) pôs o conjunto
    vivo em 1, mas `README.md`/`README.pt_BR.md` e `release-beta-0.5.0-prep`
    EN/PT ainda declaravam 0. As quatro declarações agora batem com a autoridade
    (`scripts/check_known_bugs_status.sh`); `scripts/check_live_records.sh`
    rc=0.

  - **S5.5 fatia 4b (db-parity, lane gaps-db) — `orm.save` sobre o wire MySQL no
    cross (peça cross `B77`)** (24/09): o `kof_orm_save` desvia em
    `kof_db_type == 2` **antes** do `kof_orm_conn` (que recusa type ≠ 1) e faz
    tail-call para o novo `kof_orm_save_mysql`, espelhando as 3 saídas medidas do
    host: (1) PK nula/0 → INSERT **sem** a coluna PK + `SELECT LAST_INSERT_ID()`
    (`kof_db_mysql_scalar_int`/`B74`) e **nova instância** com a PK patchada
    (`kof_alloc` + `kof_init_object` + `kof_memcpy`); (2) PK != 0 → ``UPDATE `t`
    SET `f` = <lit>,… WHERE `pk` = <lit>`` → mesmo ponteiro quando acha linhas;
    (3) UPDATE 0 linhas → INSERT de todas as colunas (upsert) → mesmo ponteiro. O
    critério de PK espelha o x86 (int/long == 0, double/float truncado == 0 com os
    sentinelas `INT64_MIN`/`MAX`, String null → INSERT, bool nunca). Identificadores
    usam o dialeto backtick (`kof_orm_mysql_bt_str`/`bt_raw`); os valores de campo
    são renderizados pelo `typeCode` do schema (`kof_orm_mysql_field_lit`, port de
    `RuntimeOrmMysqlFieldLit` — int sign-extended, long/bool `kof_long_to_string`,
    double/float `kof_double_to_string` com o float widened, KofString via
    `kof_db_mysql_render`, null → `NULL`, outro tipo → ORM001); o exec é o que
    lança `kof_orm_mysql_exec` (`B76`). Prova:
    `KofOrmE2ETest#crossNativeMariadbSaveMatchesOracles` — JVM + x86-64 +
    riscv64 + aarch64 byte-idênticos (`1\n1\n1\n1\n{"name":"Mel2"}\n7\n2\n8\n3`)
    — e `#crossNativeMariadbSaveErrorMatchesX86Oracle` (tabela inexistente lança
    em x86 == riscv64 == aarch64); `KofOrmE2ETest` 77/0F/2skip,
    `NativeRiscvDbWireTest` 41/0F, `NativeRiscvRuntimeSliceRegistryTest` 9/9.

  - **S5.5 fatia 4c (db-parity, lane gaps-db) — `orm.saveAll` sobre o cross
    MySQL** (24/09): nenhuma peça nova de runtime foi necessária — o
    `kof_orm_save_all` (`B56`) só faz o loop e delega ao `kof_orm_save`, que
    agora desvia `kof_db_type == 2` para a `B77`, então o lote INSERT (PKs
    geradas) e o lote UPDATE por PK funcionam sobre mysql. Prova:
    `KofOrmE2ETest#crossNativeMariadbSaveAllMatchesOracles` — JVM + x86-64 +
    riscv64 + aarch64 byte-idênticos
    (`true\n2\n{"name":"Mel"}\n{"name":"Ana"}\ntrue\n2\n{"name":"Mel2"}\ntrue\n2`).

  - **S5.5 fatia 4a (db-parity, lane gaps-db) — a primitiva exec que lança para
    o `orm.save` no cross MySQL (peça cross `B76`)** (24/09):
    `kof_orm_mysql_exec(fd, sql) -> affected | throw` envia o COM_QUERY (mesmo
    framing B67/B72) e classifica a resposta como o x86
    `RuntimeOrmMysqlExec`/`.Lorm_sa_exec`: OK (1º byte `0x00`) → `affectedRows`
    lenenc (1B / `0xFC`+2LE / `0xFD`+3LE); ERR do servidor (`0xFF`) → lança
    `mysql: <msg>` (mensagem em `payload+9`, teto 400); perda/resposta estranha →
    lança `mysql: connection lost`. É a **única** face ORM que lança (o
    `delete`/`deleteAll` x86 usam o exec genérico sem throw), então é exatamente
    o que o `orm.save` precisa e nada mais. Prova: `NativeRiscvDbWireTest` —
    harness em riscv64 + aarch64 sob qemu contra o MariaDB real
    (`0\n1\n1\n1\n0\n1`) mais o throw do ERR (`mysql: …`, exit 1) e a
    sabotagem do link sem a B76; `NativeRiscvRuntimeSliceRegistryTest` 9/9.

  - **S5.5 fatia 3 (db-parity, lane gaps-db) — `orm.delete`/`deleteAll` sobre o
    wire MySQL no cross (peça cross `B75` + dispatch `B50`/`B54`)** (24/09):
    `kof_orm_delete_mysql(id,key,table,schema)` e
    `kof_orm_delete_all_mysql(id,table,schema)` montam ``DELETE FROM
    `t`[ WHERE `pk` = <lit>]`` (dialeto backtick) e despacham via
    `kof_db_resolve` + `kof_db_mysql_execute` (B72); o `kof_orm_delete_all` e o
    `kof_orm_delete` ramificam em `kof_db_type == 2`. O renderizador de literal
    da fatia 2 foi **promovido a global compartilhado** (`kof_orm_mysql_lit`, na
    `B75`) para uma cópia só servir `count_where` e `delete` (`.L53_lit`
    removido), o bind nulo rende `NULL` e outra forma de box lança ORM001. **A
    semântica espelha o x86 (a referência do contrato, D-DB-GAPS): o
    `delete`/`deleteAll` x86 usa o `kof_db_execute` genérico (sem throw,
    `affected >= 0` → `true` no sucesso *e* no ERR), então o cross reusa a B72**
    — sem exec que lança aqui; a divergência JVM↔Native no caminho de erro é
    pré-existente e está catalogada `§493` (regra 6, decisão de contrato do
    maintainer; não mudada em silêncio). Também corrigiu um off-by-one no ramo
    mysql novo da `B54`: ele lia `key`/`table`/`schema` de slots da pilha
    derramados *antes* dos args serem atribuídos (os regs velhos do chamador) →
    ORM001/segfault; agora lê os `s2`/`s3`/`s4` vivos. Prova:
    `KofOrmE2ETest#crossNativeMariadbDeleteAndDeleteAllMatchesOracles` — JVM +
    x86-64 + riscv64 + aarch64 byte-idênticos em hit / miss / negativo /
    idempotente (`3\ntrue\n2\ntrue\n2\ntrue\n2\ntrue\ntrue\n0`) — e
    `#crossNativeMariadbDeleteErrorMatchesX86Oracle` (Native x86 == riscv64 ==
    aarch64 no ERR de tabela inexistente); regressão verde (`KofOrmE2ETest`
    75/0F, `KofDbE2ETest` 40/0F).

  - **S5.5 fatia 2 (db-parity, lane gaps-db) — `orm.count_where` sobre o wire
    MySQL no cross (peça `B53`) + fix latente do bind bool x86 (`§492`)**
    (24/09): o ramo type-2 do `kof_orm_count_where` monta `SELECT COUNT(*) FROM
    \`t\` WHERE \`f\` = <literal>` (dialeto backtick, sem `?`) com um
    renderizador novo `.L53_lit` — box §284 int/long/**bool**/double/float via
    `kof_*_to_string`, KofString via `kof_db_mysql_render` (aspas+escape),
    null → `NULL`, outra forma → ORM001. A mesma unidade corrigiu um bug
    **x86** latente: o `RuntimeOrmMysqlCountWhere` classificava o tag do box
    como 1 (String, inalcançável) em vez de 3 para Bool, então o
    `orm.count_where` sobre `mysql://` lançava ORM001 em **todo** bind
    booleano, enquanto a face SQLite e a JVM funcionavam (catalogado em
    `known-bugs.md §492`). Prova:
    `KofOrmE2ETest#crossNativeMariadbCountWhereMatchesOracles` — o host JVM
    (JDBC) + Native x86-64 + riscv64 + aarch64 sob qemu, byte-idênticos em
    string / ausente / injeção / int negativo / int positivo / bool
    (`1\n0\n0\n1\n0\n1\n1\n1`); a face SQLite cross e o teste mysql x86 seguem
    verdes (`KofOrmE2ETest` 73/0F, `KofDbE2ETest` 40/0F).

  - **S5.5 fatia 1 (db-parity, lane gaps-db) — `orm.count` sobre o wire MySQL
    no cross (peças cross `B74` + `B50`)** (24/09): um handle type-2 resolvido
    agora chega ao `kof.orm` — o `kof_orm_count` despacha por `kof_db_type`
    (2 → um scalar novo `kof_db_mysql_scalar_int(fd, sql)` sobre
    `COM_QUERY`/B69, `.L50_conn`/sqlite inalterados). O dialeto MySQL exige
    identificador com backtick (`SELECT COUNT(*) FROM \`table\``), **medido**:
    o MariaDB rejeita a citação do sqlite (`SELECT COUNT(*) FROM "t"` → `ERROR
    1064`), então o ramo type-2 monta o próprio SQL. As demais faces
    row-object (`count_where`, `save`/`delete`, `find`/`all`/`page`) seguem
    sqlite-only — gaps interinos declarados (S5.5 fatias 2–4), nunca aceite
    silencioso. Prova:
    `KofOrmE2ETest#crossNativeMariadbCountMatchesX86Oracle` — byte-parity com o
    oráculo x86-64 em riscv64 + aarch64 sob qemu contra o MariaDB real
    (`3` → `2` após um `DELETE` bindado).

  - **S5.4 fatia 2 (db-parity, lane gaps-db) — o dispatch mysql do
    `execute`/`query` no cross + `transaction { }` real (peça cross `B47b`)**
    (24/09): um handle type-2 resolvido agora chega ao wire via
    `db.execute`/`db.query` — os gerados `kof_db_execute[N]`/`kof_db_query[N]`
    despacham por `kof_db_type` (2 → substituição client-side dos binds `B71` +
    `B72`/`B70`, senão o ramo sqlite) e foram para a peça nova `B47b` (o
    dispatch não cabia mais no frame da `B47`; a `B47` fica com resolve/type/
    connect/close/bind/transaction). O `kof_db_connect` zera user2/pass2 (a
    assinatura host-only) para a URL sem userinfo autenticar, e o
    `kof_db_close` fecha type 2 via `kof_plat_close`. O `transaction { }` veio
    de graça: o BEGIN/COMMIT/ROLLBACK da B47 chamam `kof_db_execute`, que agora
    despacha, então os comandos reais rodam sobre `COM_QUERY`. O `-lmariadb`
    link-by-use é moot — o wire cross é auto-contido (sockets crus + SHA1
    próprio), nenhum driver externo é linkado. Prova:
    `KofDbE2ETest#crossNativeMariadbAliasWireProtocol` (espelho riscv64 +
    aarch64 do teste de alias x86, `mariadb://` com userinfo e host-only, saída
    de linha real) + `#crossNativeMariadbTransactionCommits` /
    `#...RollsBackOnFailure` + `NativeRiscvDbWireTest#dispatchExecuteQueryAgainstRealMariaDb*`
    + `withoutDispatchPieceLinkFailsSabotage` sob qemu contra o MariaDB real.

  - **D-FULL-PARITY-050 linha 11 (lane native-cross) — `String.toCharArray()`
    portado para JS + Native x86-64 + Native riscv64/aarch64.** O método saiu do
    gate honesto `STR003`: JS (`JsRuntimeCore.kofToCharArray`), x86-64
    (`RuntimeStringToCharArray` + `NativeX86StringCalls`) e cross
    (`NativeRiscvAsmStrToCharArray` + `NativeRiscvCrossOps`) agora devolvem `Char[]` com os
    code units UTF-16 (char astral = par high/low surrogate), numa passada,
    byte-idêntico ao oráculo JVM. Prova: `KofStringsTest#toCharArrayJvmJsNative`
    (JVM/JS/x86) + `NativeStringToCharArrayCrossTest` (riscv64/aarch64 sob qemu).
    `matches`/`replaceAll`/`replaceFirst` (motor de regex) e `compareToIgnoreCase`
    (folding Unicode) seguem sob `STR003`.

  - **D-FULL-PARITY-050 linha 13 fatia 1 (lane native-cross) — `kof.io`
    `exists()`/`isFile()`/`isDirectory()` no cross riscv64/aarch64.** As faces
    de estat saíram do gate honesto `NAT006`: a peça cross nova
    `NativeRiscvAsmIoStat` implementa `kof_io_stat_mode`/`kof_io_file_exists`/
    `kof_io_file_is_file`/`kof_io_file_is_dir` com o syscall genérico do Linux
    `newfstatat` (`AT_FDCWD`, `st_mode` em +16 no riscv64/aarch64), e o
    `ExpressionBuiltinInstanceCalls.lowerIo` passa a emitir essas três faces no
    cross enquanto as faces de read/write/dir mantêm o `NAT006` honesto. Prova:
    `NativeIoStatCrossTest` (oráculo JVM + riscv64 + aarch64 sob qemu,
    byte-idêntico) + `DomainGapCodesTest#ioStatOnCrossHasNoGap` e o
    `#ioOnCrossIsNat006` repontado (agora `readText`).

  - **D-FULL-PARITY-050 linha 13 fatia 2 (lane native-cross) — `kof.io`
    `readText()`/`writeText()`/`appendText()` no cross riscv64/aarch64.** A peça
    cross nova `NativeRiscvAsmIoText` implementa `kof_io_read_text`
    (`openat`/`fstat`/`read`/`close`, header KofStr, arquivo ausente = null como o
    JVM) e `kof_io_write_text`/`append_text` (`openat`+`write`+`close`,
    `O_TRUNC`/`O_APPEND`, 0644). O `ExpressionBuiltinInstanceCalls.lowerIo` passa
    a emiti-las no cross. Prova: `NativeIoTextCrossTest` (oráculo JVM medido em
    runtime + riscv64 + aarch64 sob qemu, round-trip byte-idêntico).

  - **D-FULL-PARITY-050 linha 13 fatia 3 (lane native-cross) — `kof.io`
    `delete()`/`create()`/`mkdir()` no cross riscv64/aarch64.** A peça cross nova
    `NativeRiscvAsmIoFs` implementa `kof_io_delete` (`unlinkat`, stat primeiro,
    `AT_REMOVEDIR` para diretório, ausente = false como o JVM) e
    `kof_io_dir_create` (`mkdirat`, 0755, `EEXIST` = false). Prova:
    `NativeIoFsCrossTest` (oráculo JVM medido em runtime + riscv64 + aarch64 sob
    qemu, byte-idêntico).

  - **D-FULL-PARITY-050 linha 13 fatia 4 (lane native-cross) — `kof.io`
    `createDirectories()`/`mkdirs()` (mkdir -p) no cross riscv64/aarch64.** A
    peça cross nova `NativeRiscvAsmIoMkdirs` implementa `kof_io_dir_create_dirs`
    (percorre a path, `mkdirat` cada prefixo não-vazio, `EEXIST` tolerado, e no
    fim a path completa). Prova: `NativeIoMkdirsCrossTest` (oráculo JVM medido em
    runtime + riscv64 + aarch64 sob qemu, byte-idêntico).

  - **D-FULL-PARITY-050 linha 13 fatia 5 (lane native-cross) — `kof.io`
    `size()` no cross riscv64/aarch64.** A peça cross nova `NativeRiscvAsmIoSize`
    implementa `kof_io_file_size` (`newfstatat`, `st_size`; em miss LANÇA — exceção
    recuperável, sem sentinela — espelhando a mensagem do x86
    `size: file not found: <path>`; o JVM usa `file not found: <path>`, divergência
    JVM×x86 pré-existente catalogada como §494). Prova: `NativeIoSizeCrossTest`
    (caminho de sucesso JVM==riscv64==aarch64; as duas mensagens de erro pinadas).

  - **D-FULL-PARITY-050 linha 13 fatia 6 (lane native-cross) — `kof.io`
    `readBytes()`/`writeBytes()`/`appendBytes()` no cross riscv64/aarch64.** A
    peça cross nova `NativeRiscvAsmIoBytes` implementa as três faces de bytes
    (openat O_RDONLY/O_TRUNC/O_APPEND, `Int[]` via `kof_array_alloc`, byte
    zero-extendido para bater com o `& 0xFF` do JVM). Prova:
    `NativeIoBytesCrossTest` (reusa o golden JVM `IoE2ETest.fileBytes`:
    `true/4/65/0/255/true/8`, JVM==riscv64==aarch64).

  - **D-FULL-PARITY-050 linha 13 fatia 7 (lane native-cross) — `kof.io`
    `Directory.list()` no cross riscv64/aarch64.** A peça cross nova
    `NativeRiscvAsmIoDirList` implementa `kof_io_dir_list` (`getdents64` num
    buffer de 8 KiB, pula `.`/`..`, monta lista de String e insertion-sort
    bytewise para bater com o `Files.list().sorted()` do JVM). Abre com
    `O_RDONLY`, NÃO `O_DIRECTORY` — este é arch-divergente (riscv64 `0x10000` ×
    arm64 `0x4000`), então a asm cross compartilhada não pode usá-lo (no aarch64
    dava `EINVAL`/SEGV até achar). Prova: `NativeIoDirListCrossTest`
    (`true/true/true/2/a.txt/b.txt`, JVM==riscv64==aarch64).

  - **D-FULL-PARITY-050 linha 13 fatia 8 (lane native-cross) — `kof.io`
    `readRange(offset, len)` no cross riscv64/aarch64.** A peça cross nova
    `NativeRiscvAsmIoReadRange` implementa `kof_io_read_range`/`read_range_path`
    (`openat` + `pread64`, `Int[]` via `kof_array_alloc`, zero-extendido). Prova:
    `NativeIoReadRangeCrossTest` escreve 5 bytes e lê o range (1,3):
    `true/3/20/30/40`, JVM==riscv64==aarch64. A linha 13 NÃO fechou: o gate
    NAT006 ainda cobre as faces restantes (modifiedTime/name/copy/move/
    delete de dir/symlink/path_*).

  - **D-FULL-PARITY-050 linha 13 fatia 9 (lane native-cross) — faces PURAS de
    path do `kof.io` no cross riscv64/aarch64.** A peça cross nova
    `NativeRiscvAsmIoPath` implementa `kof_io_strip_trailing`, `kof_io_file_name`,
    `kof_io_path_file_name`, `kof_io_path_parent`, `kof_io_path_extension` e
    `kof_io_path_is_absolute` (só string, sem syscall). Prova:
    `NativeIoPathCrossTest` (`c.txt/c.txt/txt//a/true/false`,
    JVM==riscv64==aarch64).

  - **D-FULL-PARITY-050 linha 13 fatia 10 (lane native-cross) — `Path.resolve`
    no cross riscv64/aarch64.** A peça cross nova `NativeRiscvAsmIoResolve`
    implementa `kof_io_path_resolve` (child absoluto, base vazia, base terminando
    em `/`, senão `base + "/" + child`). Prova: `NativeIoResolveCrossTest`
    (`/a/b/c/d`, `/a/b/c`, `/x`; JVM==riscv64==aarch64).

  - **D-FULL-PARITY-050 linha 13 fatia 11 (lane native-cross) — `Path.normalize`
    no cross riscv64/aarch64.** A peça cross nova `NativeRiscvAsmIoNormalize` porta
    `kof_io_path_normalize` (colapsa `.`/`..`, separadores repetidos e barra final;
    preserva a raiz `/`; resultado relativo vazio vira `.`). Prova:
    `NativeIoNormalizeCrossTest` (`/a/c`, `a/b`, `c`, `/a/b`, `/`, `.`, `x`;
    JVM==riscv64==aarch64).

  - **D-FULL-PARITY-050 linha 13 fatia 16b (lane native, ÚLTIMA) — `copyTo` em
    todos os alvos nativos, fechando a linha 13.** `RuntimeIoCopy` novo (x86-64)
    e `NativeRiscvAsmIoCopy` (cross): contrato
    `Files.copy(src,dst,COPY_ATTRIBUTES)`, sem sobrescrever via `O_EXCL`, mais
    `fchmod`/`utimensat`; `NAT006` removido do `CROSS_IO_READY`. Prova:
    `NativeIoCopyCrossTest` (JVM==x86-64==riscv64==aarch64). `§497` FECHADO
    (vivos 4→3).

  - **D-FULL-PARITY-050 linha 13 fatia 16a (lane native) — `moveTo` em todos
    os alvos nativos.** `RuntimeIoMove` novo (x86-64, `rename`) e
    `NativeRiscvAsmIoMove` (cross, `renameat2(276, flags=0)` — `renameat(38)`
    e correto no kernel mas o qemu-riscv64 8.2.2 nao o despacha). Contrato
    JVM G-ORG-002 mantido: sem sobrescrever (dest existe -> 0). Prova:
    `NativeIoMoveCrossTest` (JVM==x86-64==riscv64==aarch64).

  - **D-FULL-PARITY-050 linha 13 fatia 15 (lane native) — `modifiedTime` +
    `isSymlink` em todos os alvos nativos.** `RuntimeIoMeta` novo (x86-64) e
    `NativeRiscvAsmIoMeta` (cross) implementam `kof_io_file_modified_time` (millis
    de `stat` `st_mtime`; lança `file not found: ` em miss, como a JVM) e
    `kof_io_file_is_symlink` (`lstat` `AT_SYMLINK_NOFOLLOW`). A sonda NAT006 passou
    para o `copyTo` ainda gateado. Prova: `NativeIoMetadataE2ETest`
    (JVM==x86-64==riscv64==aarch64).

  - **D-FULL-PARITY-050 linha 13 fatia 14 (lane native x86-64) — `Directory.delete()`
    recursivo no x86-64.** O `RuntimeIo3.kof_io_dir_delete` agora faz stat do path,
    percorre a árvore com `getdents64` e recursa em cada filho (`path + "/" + name`,
    montado via `kof_string_concat`), depois `rmdir`; arquivos vão por `unlink`.
    Igual ao contrato recursivo `Files.walk` da JVM. Prova:
    `IoE2ETest.directoryDeleteRecursive` (JVM==x86-64).

  - **D-FULL-PARITY-050 linha 13 fatia 13 (lane native-cross) — `Directory.delete()`
    recursivo no cross riscv64/aarch64.** A peça cross nova `NativeRiscvAsmIoDirDelete`
    implementa `kof_io_dir_delete` com `getdents64` + `unlinkat` (`AT_REMOVEDIR`),
    pulando `.`/`..` e recursando em cada filho (`path + "/" + name`), igual ao
    contrato recursivo `Files.walk` da JVM. Prova: `NativeIoDirDeleteCrossTest`
    (árvore não-vazia: dir + arquivo + subdir/arquivo) `true/false/false`
    JVM==riscv64==aarch64, árvore removida.

  - **§497 catalogado (lane native-cross) — gaps nativos do `kof.io`.** `Directory.delete()`
    é recursivo na JVM mas um `rmdir` cru no x86-64 (não recursivo) e ausente no cross
    riscv64/aarch64; `copyTo`/`moveTo`/`modifiedTime`/`isSymlink` não têm implementação
    nativa em nenhum alvo (só JVM). Catalogado (Q7) em `known-bugs.md §497`; família do
    ledger `NAT006` da linha 13.

  - **D-FULL-PARITY-050 linha 13 fatia 12 (lane native-cross) — `Path.toAbsolute`
    no cross riscv64/aarch64.** A peça cross nova `NativeRiscvAsmIoToAbsolute` porta
    `kof_io_path_to_absolute` (path absoluto devolvido como está, senão `getcwd`
    via `__NR_getcwd`=17 + `kof_io_path_resolve`). Prova: `NativeIoToAbsoluteCrossTest`
    (`/abs/x`, `<cwd>/rel/x`, `<cwd>/a/../b`; JVM==riscv64==aarch64).

  - **S5.4 fatia 1 (db-parity, lane gaps-db) — `connect` MySQL/MariaDB REAL
    no cross (peça cross `B73`)** (24/09): `kof_db_connect` agora aceita
    `mysql://`/`mariadb://` no riscv64/aarch64 — parse da URL
    (`[user[:pass]@]host[:port][/db]`, IPv4 dotted com o mesmo fallback
    127.0.0.1 do x86), `socket`+`connect` pela HAL, o handshake/auth da B66 e
    o registro do fd como type 2 (handle `db<N>`) nas tabelas da B47. Tanto a
    forma `db.connect` com userinfo quanto a host-only via `kof_db_connect2`
    autenticam contra o servidor real; a recusa DB001 passa a listar com
    VERDADE `sqlite:, mysql://, mariadb://` (esquema não portado, como
    `postgres://`, segue lançando no connect). Prova:
    `NativeRiscvDbWireTest#connectMysqlAgainstRealMariaDb*` +
    `withoutConnectPieceLinkFailsSabotage` no riscv64 + aarch64 sob qemu
    contra o **MariaDB real** (type 2, fd resolvido, CREATE/INSERT pelo fd
    resolvido, alias `mariadb://` e forma host-only autenticam)
    + `KofDbE2ETest#crossNativeUnsupportedSchemeNamesTruthfulDb001` (binários
    reais nomeiam DB001 para `postgres://`).

  - **B-4.3 fatia 0 — toolchain Cortex-M3 + recipe de boot de-riscados**
    (24/09, lane baremetal 9092; `D-BAREMETAL-BODIES`). O
    `scripts/provision-mcu-qemu.sh` agora baixa também `binutils-arm-none-eabi` e
    roda um self-test Cortex-M3 (`arm-none-eabi-as -mcpu=cortex-m3 -mthumb` +
    `qemu-system-arm -M mps2-an385`) que exige `KO-CM3 OK`, espelhando o de-risco
    do B-4.1. Recipe medido: vector table em `0x0` (`[0]`=SP, `[1]`=`Reset_Handler|1`),
    UART CMSDK em `0x40004000` (CTRL `0x08`=3 habilita TX/RX — sem isso o DATA é
    descartado; STATE `0x04` bit0 TXFULL; DATA `0x00`), stack em `0x00080000`
    (SSRAM1 de 4 MB em `0x0`). Prova: rodar o script imprime as duas linhas `== OK`.

  - **B-4.2 fatia 2 — corpos `kof_plat_thread_id`/`kof_plat_random` do MCU +
    `CONC003` para concorrência** (24/09, lane baremetal 9092;
    `D-BAREMETAL-BODIES`). O `NativeMcuRiscv32` emite `kof_plat_thread_id`
    (`csrr mhartid`, 0 no virt single-hart) e `kof_plat_random(buf,len)`
    (xorshift32 semeado por `rdcycle`, RV32I puro, sem divisão) como corpos
    `.globl`; concorrência no MCU single-core é recusada com `CONC003`
    (pré-varredura das ops, já que a lowering de `spawn` emite um
    `KofNewObject` antes; novo ramo `CONC003` no `compileSources`, espelhando
    `NATIVE002`/`FLT001`). O `kof_plat_time` no MCU fica deliberadamente fora
    desta fatia (semântica wall-vs-monotônica num MCU sem RTC é decisão regra-6,
    não fix de agente). Prova: `NativeMcuE2ETest` **7/0**
    (`mcuImageDefinesHalSymbols` agora exige `T kof_plat_write/_exit/
    _thread_id/_random`, novo `mcuRejectsConcurrencyWithConc003`) +
    `StdParityGapAuditTest` 16/0.

  - **B-4.4 fatia 1 — vetor de trap do MCU + reset path asserido na imagem**
    (24/09, lane baremetal 9092; `D-BAREMETAL-BODIES`). O `_start` do
    `NativeMcuRiscv32` instala `mtvec` apontando para `.Lmcu_trap` (um halt
    honesto — um trap inesperado para em vez de correr para `mtvec=0`), e o
    reset path (`_start` na base de carga `0x80000000` do `-M virt`) fica
    asserido. Prova: `NativeMcuE2ETest` **6/0** (novo `mcuResetEntryIsAtLoadBase`
    exige `80000000 T _start` via `nm`, mais os cinco casos anteriores).

  - **B-4.2 fatia 1 — o hello MCU passa a sair por uma HAL real
    (`kof_plat_write`/`kof_plat_exit`)** (24/09, lane baremetal 9092;
    `D-BAREMETAL-BODIES`). `NativeMcuRiscv32` emite `kof_plat_write(buf,len)`
    (a UART do virt `0x10000000`, laço por comprimento) e `kof_plat_exit(code)`
    (test device `0x100000` + halt) como funções RV32I `.globl`, e o `_start`
    as chama em vez de inlinar as escritas na UART (os literais viraram
    `.ascii` com comprimento explícito). Prova: `NativeMcuE2ETest` **5/0** (novo
    `mcuImageDefinesHalSymbols` exige `T kof_plat_write`/`T kof_plat_exit` via
    `nm`, mais os quatro casos anteriores).

  - **B-4.1 — primeiro slice MCU RV32I roda bare sob `qemu-system-riscv32`
    (`main(){ println("KO-MCU OK") }` imprime pela UART e desliga pelo test
    device)** (24/09, lane baremetal 9092; `D-BAREMETAL-BODIES`). Novo
    `Target.NATIVE_RISCV32` + `dev.kof.compiler.nat.mcu.NativeMcuRiscv32`: só
    `System.out.print/println(String literal)` no `main` é aceito (mais o op
    no-op `String.valueOf` que o lowering de print emite), rebaixado a escritas
    na UART (`0x10000000`) + poweroff (`0x100000`); qualquer outra op falha com
    diagnóstico limpo `NATIVE002` (R6/Q7 — nunca um artefato que finge rodar).
    Sem semihosting (este build do qemu não reconhece `ebreak`). Toolchain sem
    root: novo `scripts/provision-mcu-qemu.sh`. Prova: `NativeMcuE2ETest`
    **4/0** (hello pela UART, ordem/newlines, ELF32 RISC-V `e_machine` 243,
    recusa `NATIVE002` de `println(42)`).

  - **B-5 `kof_plat_time_mono` — agora REAL também no UEFI (TSC calibrado por
    `BootServices->Stall`)** (24/09, lane baremetal 9092; `D-BAREMETAL-BODIES`).
    Mesma fonte monotônica do BIOS (64 bits, `rdtsc`), mas a frequência é
    calibrada uma vez com um `Stall` de 50 ms (`1e6/50000 = 20`); emite
    `ts[0]=tv_sec`/`ts[1]=tv_nsec`, a ABI que `kof_obs_mono_nanos` consome — fim
    da recusa. Prova: `NativeUefiE2ETest` **8/0** (novo `uefiMonoSpanDuration`:
    um `time.sleep` de 60 ms entre `spanStart`/`spanEnd` dá
    `durationMicros >= 10000` sob OVMF).

  - **B-5 `kof_plat_time_mono` — REAL no BIOS (TSC calibrado pelo PIT) +
    fall-through da recusa NOMEADA corrigido no UEFI** (24/09, lane baremetal
    9092; `D-BAREMETAL-BODIES`). O monotônico do BIOS não pode usar um delta
    único do PIT (o contador de 16 bits dá a volta a cada 54.9 ms), então
    `kof_plat_time_mono` lê o **TSC** (64 bits, `rdtsc`) e calibra a frequência
    **uma vez** contra o PIT (delta do TSC sobre 100000 ticks ≈ 83.8 ms),
    emitindo `ts[0]=tv_sec`/`ts[1]=tv_nsec` — exatamente o que
    `kof_obs_mono_nanos` consome; o init do PIT ganhou guarda de idempotência
    compartilhada com o `sleep`. No UEFI o corpo de recusa é corrigido: emitia
    um array `.word` UTF-16 para `kof_plat_write` (que já converte
    ASCII→UTF-16) e, como `kof_plat_exit_group` *retorna* no UEFI, caía no
    texto seguinte e **crashava o app sob OVMF** — agora a mensagem é ASCII e
    um spin garante o "nunca continua". Prova: `BiosBootE2ETest` **10/0** (novo
    `biosMonoSpanDuration`: um `time.sleep` de 60 ms entre
    `spanStart`/`spanEnd` dá `durationMicros >= 10000`; a recusa de `http.get`
    é alcançável em runtime) + `NativeUefiE2ETest` **7/0** (novo
    `uefiNetRefusalStopsWithDiagnostic`: a recusa nomeada imprime e o app para,
    sem fall-through).

  - **§488 ✅ CORRIGIDO — o caminho de texto MySQL do x86 emitia NULL como string
    VAZIA crua (JSON inválido `{"n":,`) e uma célula de string vazia como número
    cru** (24/09, lane compiler 9092; a mantenedora autorizou o fix no chat — a
    seção era lane da regra 6/mantenedora). `RuntimeDb5 .Ldb_mysql_null` agora
    emite o literal `null` do contrato JVM (4× `kof_json_builder_char`), e o
    detector só-dígitos roteia `len==0` para `kof_json_encode_string`, então uma
    string vazia sai `""` — byte-idêntico à peça cross B70 e a
    `JvmConfigRuntime.kof_db_row_to_json`. Prova:
    `KofDbE2ETest#nativeMysqlNullAndEmptyStringJson` (oráculo JVM medido; x86
    nativo byte a byte) — RED pré-fix `{"id":1,"n":,"s":"ab"}` /
    `{"id":2,"n":7,"s":}`, GREEN pós-fix `{"id":1,"n":null,"s":"ab"}` /
    `{"id":2,"n":7,"s":""}`; vizinhança verde com MariaDB real:
    `KofDbE2ETest` 37 (3 skip) + `NativeRiscvDbWireTest` 25.

  - **B-5 `kof_plat_random` — corpo REAL nas duas faces bare-metal (RDRAND/TSC
    + xorshift64), `random.*` deixa de ser recusa** (24/09, lane baremetal
    9092; `D-BAREMETAL-BODIES`). Novo `RuntimeBareRandom` preenche bytes com o
    contrato de `getrandom` no BIOS e no UEFI: semeia de `RDRAND` quando a CPU
    expõe (`CPUID.01H:ECX[30]`), senão `rdtsc`, e roda xorshift64
    (não-criptográfico — `random.*`, distinto de `security.*`, que segue gap
    bare). Corrige o crash latente do UEFI: como `kof_plat_exit_group`
    *retorna* no UEFI, o corpo de recusa antigo caía no fluxo seguinte e
    crashava o app sob OVMF. Prova: `BiosBootE2ETest` **9/0** (novo
    `biosRandomRunsBare`: duas amostras de 10^9 diferem → `true`) +
    `NativeUefiE2ETest` **6/0** (novo `uefiRandomRunsBareUnderOvmf`) + teste de
    recusa repontado para o ainda-explícito `observability.spanStart` →
    `kof_plat_time_mono` (`biosUnsupportedCapabilityPrintsReadableRefusal`).

  - **B-5 `kof_plat_sleep` no UEFI — `time.sleep()` bloqueia de verdade por
    `BootServices->Stall` (`gBS+248`)** (24/09, lane baremetal 9092;
    `D-BAREMETAL-BODIES`); o timespec vira `us = sec*1e6 + nsec/1000` e é
    entregue ao firmware — fim da recusa na face UEFI.
    `kof_plat_time_mono` segue recusa NOMEADA. Prova: `NativeUefiE2ETest` **5/0**
    (novo `uefiSleepAdvancesWallClock`: após `time.sleep(1100)` o RTC avançou
    ≥1 s → `true` sob OVMF) + `BiosBootE2ETest` **8/0** + bateria nativa **97/0**.

  - **B-5 `kof_plat_sleep` no BIOS — sleep REAL pelo PIT (canal 0, modo 2),
    não recusa** (24/09, lane baremetal 9092; `D-BAREMETAL-BODIES`).
    `time.sleep(ms)` agora espera de verdade no BIOS legado: o PIT é
    reprogramado para rate generator (1.193182 MHz, reload 65536) e o laço
    acumula ticks latchados (ciente de wrap) até `us*1193182/1000000`.
    `kof_plat_time_mono` segue recusa NOMEADA (próxima fatia). Prova:
    `BiosBootE2ETest` **8/0** (novo `biosSleepAdvancesWallClock`: após
    `time.sleep(1100)` o RTC avançou ≥1 s → `true`; RED pré-fix timeout/false) +
    bateria nativa **102/0**.

  - **B-5 UEFI `kof_plat_time` — `time.now()` agora roda bare por
    `RuntimeServices->GetTime`** (24/09, lane baremetal 9092;
    `D-BAREMETAL-BODIES`). A face UEFI ganha o corpo do relógio de parede
    (`RT = ST+88`, `GetTime = RT+24`, um `EFI_TIME` de 16B na pilha, civil→epoch
    COMPARTILHADO com o RTC do BIOS via o novo `RuntimeCivilEpoch`), então um
    `time.now()` Kof devolve um epoch moderno sob OVMF em vez de uma recusa;
    falha do `GetTime` é diagnóstico NOMEADO, nunca epoch silencioso.
    `kof_plat_time_mono`/`kof_plat_sleep` seguem recusas honestas (próxima
    fatia). O corpo RTC do BIOS foi refatorado para reusar o helper
    compartilhado (sem mudança de comportamento). Prova: `NativeUefiE2ETest`
    **4/0** (novo `uefiTimeNowRunsBareUnderOvmf`: serial lê `true`, RED pré-fix
    `false`) + `BiosBootE2ETest` **7/0** (regressão do refactor) + bateria
    nativa **108/0**.

  - **B-5 `kof_plat_time` no BIOS — `time.now()` agora roda bare pelo RTC CMOS;
    capacidades sem corpo no BIOS recusam com diagnóstico ASCII LEGÍVEL**
    (24/09, lane baremetal 9092; `D-BAREMETAL-BODIES`, autorizado pela
    mantenedora). A face BIOS da HAL ganhou seu primeiro corpo de plataforma
    real: `RuntimeBios` lê o RTC (portas `0x70`/`0x71`), aguarda o UIP, converte
    BCD→binário, trata 12h/24h, resolve o século e preenche o timespec
    (`tv_sec` epoch + `tv_nsec=0`) que a ABI já espera — então um `time.now()`
    Kof devolve um epoch moderno sob SeaBIOS em vez de uma recusa. Capacidades
    ainda sem corpo no BIOS (`time.sleep`, `random`, I/O, rede, threads) mantêm
    a **recusa NOMEADA** (R6) mas agora em **ASCII** no COM1 (a forma UTF-16 do
    UEFI saía como lixo com NULs intercalados). Sem mudança de superfície Kof —
    só os corpos `kof_plat_*`. Prova: `BiosBootE2ETest` **7/0** (novo
    `biosTimeNowRunsBare`: serial lê `true` para `time.now() > 2020`, RED
    pré-fix `false`; novo `biosUnsupportedCapabilityPrintsReadableRefusal`:
    `time.sleep` imprime o `KOF BIOS ... kof_plat_sleep` legível e para) +
    bateria nativa **103/0** + `RingPrivilegeE2ETest` 5/0.

  - **§491 ✅ CORRIGIDO — campo desconhecido num builtin `File`/`Path`/`Directory`/
    `Buffer`/`Secret`/`KeyHandle` agora é um `SEM102` limpo** (24/09, lane compiler
    9092). Esses pseudo-tipos não têm forma de propriedade (os acessores são
    métodos), mas um campo desconhecido compilava limpo e emitia um `getfield`
    contra uma classe ausente do runtime — `File("x").bogus` →
    `getfield kof/io/File.bogus`, `buffer.alloc(8).bogus` → `getfield
    kof/Buffer.bogus`, `secrets.of("x").bogus` → `getfield kof/Secret.bogus`
    (até `File("x").path`, cuja API é o método `path()`) — abortando o load com
    `NoClassDefFoundError`, escondido atrás da mensagem do launcher JavaFX, sem
    diagnóstico. O caminho de campo do `SemExpressionTyper` ganha o guard
    `SEM102` (o irmão FIELD do #617/§490 — um gate, os quatro alvos). Prova:
    `BuiltinUnknownFieldGuardTest` 9/9 (RED-first: 7/9 falhando antes); o controle
    `f.path` do `IoUnknownMethodGuardTest` era falso-verde (nunca rodava) e foi
    corrigido para `f.path()`; `kof-compiler` completo **3273, 0F/0E**. A face
    (b), o caminho de ESCRITA (`SemAssignmentAnalyzer`) com o mesmo furo —
    `b.bogus = 1` / `f.bogus += 1` / `s.bogus = secrets.of("y")` emitiam
    `putfield` nas mesmas classes ausentes — é coberta pelo mesmo guard `SEM102`
    (`b.bogus++` já era pego pelo guard de leitura). Residual declarado: campo
    desconhecido em receptores de classe real (`List`/`Map`/`String`) ainda
    falha ALTO em runtime (`NoSuchFieldError`), postura Bug 34 — não é no-op
    silencioso.

  - **§490 ✅ CORRIGIDO — método desconhecido num valor builtin `Buffer`/`Secret`/
    `KeyHandle` agora é um `SEM102` limpo** (24/09, lane compiler 9092). Esses
    tipos têm ramo de typer dedicado, mas nome/aridade fora da tabela ao vivo
    caía no fall-through sem contrato: `secrets.of("x").bogus()` /
    `buffer.alloc(8).bogus()` compilavam limpo e devolviam o PRÓPRIO receptor
    (no-op silencioso), e `secrets.of("x").bogus(1, 2)` /
    `secrets.keyFromHex("00").bogus()` compilavam limpo e abortavam no load com
    `ClassFormatError: Illegal class name ""`. O guard `SEM102` do #617 foi
    generalizado de `kof.io` para esses pseudo-tipos — um gate, os quatro alvos.
    Prova: `BuiltinUnknownMethodGuardTest` 6/6 (RED-first: 5/6 falhando antes),
    cluster verde (`SecretE2ETest` 7/7, `KeyHandleE2ETest` 5/5, `BufferE2ETest`
    4/4, `KofSecurityTest` 42/42). Residual declarado: método desconhecido em
    `String` ainda falha ALTO em runtime (`NoSuchMethodError`, postura histórica
    do Bug 34) — não é no-op silencioso.

  - **§489 (#617) ✅ CORRIGIDO — `File.mkdir()`/`.mkdirs()` são aliases reais de
    `create()`/`createDirectories()`; métodos `kof.io` desconhecidos dão `SEM102`
    limpo** (24/09, lane compiler 9092; ordem da mantenedora "nada de stub,
    implementação real"). Agora criam o diretório de verdade (runtime gateado
    `kof_io_dir_create`/`kof_io_dir_create_dirs`, NAT006 preservado para
    riscv64/aarch64) em vez do antigo no-op silencioso, e qualquer OUTRO método
    desconhecido em File/Path/Directory é rejeitado em compile-time (guard
    `aed5fe7b`), então o `UNKNOWN` não vaza mais nome de classe vazio → sem
    `ClassFormatError` no `.toString()`. O golden JS do §382
    (`IoBoolFacesE2ETest`) agora usa o `createDirectories()` real. Prova:
    `IoUnknownMethodGuardTest` 5/5 + `IoE2ETest` 25/25 (JVM + native x86-64
    `directoryMkdirAliases`).

  - **§104b-ii (face CONTENÇÃO) ✅ FIXED — record/classe dentro de coleção agora
    compara por CONTEÚDO no Native** (24/09, lane compiler/nat 9092):
    `listOf(p1).contains(p2)`, `indexOf`/`lastIndexOf`, `setOf(p1).contains(p2)`,
    dedup do `set.add` e `set.remove` comparavam PONTEIRO (String só por
    conteúdo) → `false` vs JVM `true`. Adicionado tag 2 = objeto Kof em
    `CollectionWrites.stringTag`, o runtime `kof_obj_equals` e a tabela densa
    `kof_equals_table` (irmã da `kof_tostring_table`), despachados pelos helpers
    de coleção x86 e riscv (aarch herdada via tradutor). Um **bug latente irmão**
    encontrado na caça Q4 e corrigido na mesma unidade: o `setOf(...)` nunca
    passava o tag do `kof_set_add`, então o runtime lia registrador sujo (com o
    tag 2 novo, `setOf(1, 2)` após `println(list)` = SIGSEGV riscv/aarch).
    Prova: `NativeRecordCollectionEqualityE2ETest` **3/3** (oráculo JVM,
    byte-idêntico em x86-64 + riscv64 + aarch64).

  - **§104b-ii (face MAP) ✅ FIXED — chave record/classe em `Map` nativo agora
    casa por CONTEÚDO** (24/09, lane compiler/nat 9092): `mapOf(p1, 7).get(p2)` /
    `containsKey` / `remove` usavam comparação por PONTEIRO → `0`/`null` vs JVM
    `7`. Adicionado tag 2 = objeto Kof ao `kof_map_find` (x86 + riscv) com
    despacho para `kof_obj_equals`, e `CollectionWrites.mapKeyTag` escrito pelos
    emitters x86/riscv; o Map nativo é um vetor LINEAR, então não precisa de
    `hashCode` de conteúdo. Prova: `NativeRecordCollectionEqualityE2ETest`
    estendido (get/containsKey/remove com chave record, chave String
    não-internada, chave primitiva) **3/3**.

  - **§114 (face record-aninhado) ✅ FIXED — `equals`/`==` de record com campo
    record/classe aninhado agora compara por CONTEÚDO** (24/09, lane compiler/nat
    9092): `Outer(Inner(1),"z") == Outer(Inner(1),"z")` dava `false` no Native vs
    `true` no oráculo JVM (compara ponteiro). O `equals` sintetizado do Native agora
    emite `kof_obj_equals` (helper null-safe do §104b-ii) para campos `ClassType`;
    primitivos, arrays, type-vars e `Object` mantêm identidade (contrato
    `Objects.equals`). Prova: `NativeRecordCollectionEqualityE2ETest` estendido
    (aninhado, fundo, nullable nulo+não-nulo, classe identidade) **3/3** em
    x86-64+riscv64+aarch64.

  - **§114 (face `hashCode` de conteúdo String) ✅ FIXED — `hashCode()` de record
    com campo `String` agora hasheia o CONTEÚDO, não o ponteiro** (24/09, lane
    compiler/nat 9092): `S("ab").hashCode()` dava `1269465151` no Native vs `3136`
    no JVM. O `hashCode` sintetizado do Native roteia um campo `String` (nullable
    desembrulhado) por `String.hashCode` (`kof_string_hash_code` / `String_hashCode`,
    endurecidos null-safe → 0). O mesmo bug de nullable-unwrap estava latente no
    `equals` (campo `String?` caía em comparação de ponteiro) — corrigido também.
    Prova: `NativeRecordHashCodeE2ETest` novo + caso de equals String nullable
    **3/3** em x86-64+riscv64+aarch64.

  - **§114 (face `hashCode` de Double) ✅ FIXED — `hashCode()` de record com campo
    `Double`** (24/09, lane compiler/nat 9092): `RD(2.5).hashCode()` dava `31` no
    Native vs `1074003999` no JVM (o campo contribuía `0`). Agora o campo passa por
    `kof_double_hash(bits) = (int)(bits ^ (bits>>>32))` (helper novo x86
    `RuntimeMath` / riscv `NativeRiscvAsmRtB5`). **Float já casava cru** (o slot de
    32 bits JÁ É `floatToIntBits`). Prova: `NativeRecordHashCodeE2ETest` +=
    `RFloat`/`RDouble` incl. `-0.0` **3/3** em x86-64+riscv64+aarch64.

  - **§114 (face `hashCode` de record-aninhado) ✅ FIXED — `hashCode()` de record
    com campo record/classe aninhado agora hasheia o CONTEÚDO** (24/09, lane
    compiler/nat 9092): o campo contribuía o ponteiro. Adicionado `kof_obj_hash`
    (despacho null-safe) + a tabela densa `kof_hashcode_table` (espelho da
    `kof_equals_table`, emitida nos 3 sítios de dados). Prova:
    `NativeRecordHashCodeE2ETest` += aninhado/fundo/nullable **3/3** em
    x86-64+riscv64+aarch64. O `hashCode` do §114 está agora completo para todos os
    tipos de conteúdo.

  - **§278 ✅ CORRIGIDA (metade gpu) — `kof.gpu` agora compila e roda em
    `--target android`; nenhum erro claro de paridade fica escondido atrás de
    um gap** (24/09, lane compiler 9092; ordem da mantenedora no chat). O
    Android reusa o `JvmBackend`, então o front/IR não muda e o
    `Default/Main.class` do Android é **byte-a-byte idêntico ao do JVM**
    (`GpuAndroidE2ETest`). Como o ART não tem FFM (`java.lang.foreign`), o
    runtime gpu injetado no Android é o `JvmVkStubRuntime` — zero referências a
    `java.lang.foreign` (afirmado sobre o `KofRuntime.class` emitido),
    `available()=false` e dispatch devolvendo o fallback CPU (não-zero): o
    mesmo contrato honesto dos alvos nativos. `KofGpu.supportedOn` agora inclui
    `ANDROID`; `GPU001` permanece só para JS/SCRIPT. Pins virados em
    `StdParityGapAuditTest` + `DomainGapCodesTest`; `known-bugs.md` §278 FIXED
    (conjunto live **1→0**).

  - **Boot BIOS bare-metal (plano `PLAN-BAREMETAL-BOOT`, B-3b-3) — o payload Kof
    real agora roda bare pelo caminho BIOS legado: o `_start` é ligado na base
    fixa `0x100000`, copiado do staging de modo real (`0xC200`) em protected mode
    e saltado, e os corpos `kof_plat_*` de BIOS (write = COM1 `0x3F8`, exit =
    `cli;hlt`, sync = no-op) sustentam o runtime freestanding; o `main` Kof
    imprime `KO-BIOS PAYLOAD` sob SeaBIOS.** (23/09, lane baremetal 9092,
    retomado do 9093): cinco bugs de medição corrigidos — (1) o `emitAlloc` do
    `RuntimeMemory` mandava o BIOS para o corpo host `mmap` (inválido bare), então
    o 1º `kof_array_alloc` travava; o BIOS agora usa a arena freestanding; (2) um
    `call kof_plat_dbg_tx` de debug no `_start` HOST (definido só no corpo BIOS)
    quebraria o link de todo build nativo; (3) um `subl $512` a mais sub-copiava
    um setor; (4) `kof_plat_writev` usava stride de iovec 8 em vez de 16; (5) o
    caminho de falha nomeada (`kof_bios_load_bad`) era montado sob `.code64`, então
    um `movw $sym,%si` de 64-bit decodificava errado em 16-bit e levantava `#UD` —
    `.code16` restaurado em volta das rotinas de modo real. Prova:
    `BiosBootE2ETest` **5/0F** (positivo imprime os três marcadores; uma magia
    `KOFPAYLD` corrompida imprime `KO-BIOS LOAD BAD`) + bateria nativa **127/0F**;
    `compile` e todos os gates de doc/tamanho verdes.

  - **§205 ✅ CORRIGIDO — valor tipado `Object` agora imprime o próprio
    `toString` no Native (record/classe que chega ao `println` por `as Object`
    ou local `Object`)** (23/09, lane compiler 9092; N2/ABI de caixa com tag do
    `D-NULL-INTENT`): o `kof_box_to_string` ganhou a **face de referência** —
    depois da checagem da MAGIC de caixa de primitivo, ele lê o `type_id` de 4
    bytes no offset 0 (o mesmo discriminador que o `kof_instanceof` usa):
    `type_id == 1` (`String`) passa cru, qualquer outra referência faz tail-call
    em `kof_tostring_table[type_id]`, uma tabela `.quad` densa emitida junto das
    classes do programa que guarda o endereço do próprio `toString` de cada
    classe (o mesmo que a vtable dela já referencia); entrada `0` (sem
    `toString`) mantém o passthrough anterior. Sem segundo ABI. VERMELHO antes
    do fix = **linha vazia** em `println(Point(1,2) as Object)` /
    `var o: Object = Point(3,4); println(o)`. Prova:
    `NativeObjectBoxPrintE2ETest` 3/3 (oráculo JVM no teste, byte-a-byte em
    x86-64 + riscv64 + aarch64; corpus cobre `if` heterogêneo por local,
    `Int`/`Long`/`Double`/`Bool as Object`, `record as Object`, `String as
    Object`). Autorização N2 registrada no `DECISIONS.pt_BR.md` `D-NULL-INTENT`
    (23/09); `RUNTIME_ABI.md` §3.9 documenta a face de referência.

  - **Bridge de retorno covariante no Native (§486): as duas faces
    corrigidas — um bridge covariante cujos parâmetros já batem com o slot
    apagado do pai colidia com o método concreto num ÚNICO símbolo asm do
    Native** (23/09, lane compiler 9092; família da §483/TIER 13.2): o
    `NativeSymbolMangling.sigTag` codifica
    só os TIPOS DE PARÂMETRO, então um par com mesmo nome+mesmos parâmetros (o
    bridge e o método concreto) mangleiava para o mesmo `Classe_nome` → `as:
    symbol 'SBox_get' is already defined` enquanto JVM/Script/JS imprimiam `hi`
    (divergência rule 5). **Face (a) retorno referência:** no Native o bridge é
    pulado quando é um pass-through puro de registrador (`!paramsDiffer` E os
    dois retornos são referências); o slot de vtable aponta direto ao método
    concreto. Prova: `NativeGenericIfaceBridgeE2ETest` 3/3 (golden do oráculo
    JVM no teste `hi\nBox: hi\ndirect\n42\n99` em x86-64 + riscv64 + aarch64;
    RED com o fix revertido = a colisão verbatim), vizinhos 137/0F/0E. A **face
    (b) retorno primitivo** foi corrigida no mesmo dia pelo mangling do bridge
    com sufixo do retorno (`43f2833a`, #613): o bridge e o concreto deixam de
    colidir (`IntBox_get` vs `IntBox_get_B_java_lang_Object`); o teste de
    regressão agora carrega também o repro de retorno primitivo e prova as duas
    faces 3/3.

  - **O receive do canal drenava a fila sem zerar `tail` (§485) — SIGSEGV
    (exit 139) no Native (x86_64 e o cross riscv64/aarch64) no primeiro `send`
    depois de a fila esvaziar** (23/09, lane 9092; apareceu como o flake
    sensível a carga `KofConcurrency2Test#channelWithSpawnCrossArch` sob a
    suíte completa, `si_addr=NULL`). `kof_channel_receive` avançava
    `head = next` e `count--` mas nunca zerava `tail` quando a fila esvaziava
    (`next == 0`), então `tail` seguia apontando para o nó recém-liberado; o
    `send` seguinte via `tail != 0`, anexava na cauda OBSOLETA (liberada) e NÃO
    punha `head` → o canal ficava com `head == 0` e `count == 1`, e o `receive`
    seguinte dereferenciava `head == NULL`. **Fix:** zerar `tail = 0` quando
    `next == 0` em `NativeRiscvAsmRtB61` (aarch64 via o tradutor) e em
    `RuntimeChannel.emitChannel` (x86_64); as faces JVM (`LinkedBlockingQueue`)
    e JS (array push/shift) já estavam corretas. Prova: novo determinístico
    `channelDrainThenSendNative` (x86_64 + riscv64 + aarch64; RED pre-fix =
    rc=139, GREEN = `a=1 b=2`), `KofConcurrency2Test` 50/0F, bateria nativa
    178/0F (2 skips de toolchain opcional).

  - **Diamante de `default` conflitante + overload por aridade em interfaces
    (§487):** uma classe concreta que herda dois `default` de MESMA assinatura
    de interfaces não-relacionadas compilava limpo e estourava no class-LOAD
    com `IncompatibleClassChangeError: Conflicting default methods` (JLS
    9.4.1.3); e um `default` de mesmo nome com aridade diferente numa interface
    irmã fazia `C().greet("mel")` resolver para o método errado
    (`invokeinterface A.greet()` com o argumento deixado na stack →
    `VerifyError`). (23/09, lane compiler 9092, issue #610). **Fix:** novo
    `ImplementationChecker.checkConflictingDefaults` reporta `SEM101` nomeando
    as duas interfaces não-relacionadas e o método (override explícito, ou par
    sub/super-interface com o default mais específico, não é conflito; classes
    abstratas deferem para a subclasse concreta), e o novo
    `MemberResolver.resolveMethodsInHierarchy` deixa o `MemberCallTyper`
    escolher o overload por aridade/args na hierarquia. Prova:
    `ConflictingDefaultMethodsE2ETest` 6/6 (as duas faces RED antes do fix; o
    corpus cobre o diamante, override explícito, sub-interface mais específica,
    aridade diferente, deferral abstrato, paridade Native+JS); suíte completa do
    reactor 3816 testes, 0F/0E.

  - **Ressincronização de `docs/development` — a fila e o allowlist do gate de
    release agora batem com a realidade** (23/09, lane docs/fronteira): a linha
    do `kof-c-cross` (movido para `docs/`, C1–C4 landados) e a do
    `PLAN-BAREMETAL-BOOT` ("zero código" → B-0..B-2 + B-6 landados, B-3b-3
    pausado) estavam stale, `check_500` está rc=0 (`NativeBackend` 547) e não o
    RED que a linha alegava, e o gate de release ainda allowlistava o
    `kof-c-cross.md` movido. Prova: `check-release-050-gate-test.sh` VERDE (7/7)
    + `--selftest` OK + `loose_docs` GREEN.

  - **Gate R1 da fronteira destravado — `kof.ffi`/`kof.interop` registrados no
    ledger da stdlib (`scripts/stdlib_boundary.txt`)** (23/09, lane docs/fronteira):
    os namespaces pousaram (X6.1 `6c9aeb847`, FFI `f670d0551`) sem a linha de
    camada, então `scripts/check_stdlib_boundary.sh` falhava no "Structural
    quality gates" do CI (`VIOLATION: undocumented namespace 'kof.ffi'` /
    `'kof.interop'`). Ambos pertencem à camada mais externa do R1 →
    `interop`/`experimental`. Prova: `--selftest` + gate `rc=0` (38 namespaces
    registrados, `interop: 2`).

  - **`[server] port` do `kof.toml` é honrado — o `kof serve` repassa a porta do
    manifesto (APP002, #598)** (23/09, lane docs/fronteira; Opção 1 ratificada no
    chat): o `kof new --type backend` escrevia `[server] port` mas nada lia o
    valor — mudá-lo era ignorado em silêncio (R6). O `CmdServe` agora lê o
    `kof.toml` (a CLI lê o manifesto, não o runtime) e, quando nenhum
    `KOF_SERVER_PORT` está setado no ambiente, repassa o valor ao app como
    `KOF_SERVER_PORT` (`config.int("server.port", …)` o lê por convenção); uma
    env var explícita ainda prevalece. Prova: `ServeManifestPortE2ETest` 2/2
    (manifesto honrado + env sobre o manifesto), `FullStackE2ETest` 5/5 verde.

  - **Drift do `DECISIONS.md` corrigido — estado do `D-ENUM207` e referência do
    `D-APP` (#599)** (23/09, lane docs/fronteira; ratificado no chat): `D-ENUM207`
    `IN_PROGRESS` → `IMPLEMENTED` (enums são classes reais, identidade `==`,
    `enum == String` é `SEM062`; prova `EnumIdentityE2ETest` 6/6, §211 CLOSED) e a
    referência morta do `D-APP` `docs/architecture/application-model.md` (nunca no
    histórico do git) agora aponta para `docs/backend-parity.md` (`APP001–003`).

  - **O triage do `kof-issues-agent` deixa de rotular por substring (#600)**
    (23/09, lane docs/fronteira): o job `triage` casava `body.includes('rce')`,
    então 33/37 rótulos `security` vinham de "source"/"enforce"/"resource";
    `js` casava "json" e `doc` casava "docker"/"docs/...". O match agora é por
    palavra inteira. A rota de segurança apontava para `aminadojava` (404 — não
    atribuível), então o GitHub dropava e 0/39 issues de segurança tinham
    responsável; agora aponta para `melmonfre` e chama `core.setFailed` se o
    assignee for dropado (R6, nunca silencioso). Prova:
    `scripts/tests/kof-issues-agent-script-test.sh` **12/12** verde, **VERMELHO
    5** contra o workflow pré-fix (`KOF_ISSUES_AGENT_YML`). Escopo: só
    `beta-0.5.0` — o `main` (que roda os eventos de issue) fica intocado, para
    um pouso aprovado pela mantenedora.

  - **O `codeql-gate.sh` julga só alertas/análises do CodeQL e funciona no Git
    Bash (#604/#605)** (23/09, lane CI — `35cfb5493`): desde que o Debt Scout
    passou a subir SARIF, `code-scanning` deixou de significar CodeQL — as
    notas dele deixavam o gate VERMELHO em toda PR contra a `beta-0.5.0`, e a
    análise dele podia amarrar o veredito a um SHA que o CodeQL nunca analisou.
    Um filtro `$TOOL`/`tool_name` foi adicionado às três consultas (lista, união
    por branch, análises), e os endpoints perderam a barra inicial
    (`repos/$REPO/...`) para o MSYS não reescrevê-los como caminho de arquivo no
    Git Bash. Prova: `scripts/tests/codeql-gate-test.sh` **13/13** verde
    (cenários 11–13).

  - **§484 ✅ CORRIGIDO — switch-expressão com id bound por pattern no
    primeiro case não emite mais fallback sintético boxado contra corpos int
    (#601)** (23/09, lane 9093; causa raiz traçada e reportada pelo autor da
    issue): a inferência do tipo do resultado agora corre contra uma projeção
    descartável de `locals` com os bindings dos patterns — o fallback tipa
    `int` e o merge é int×int. Prova: `SwitchExprPatternBindingE2ETest` 4/4
    (verbatim `-5`, todos-os-ids, contrato de box do §57/§70, default
    explícito); RED medido com stash só do fix.

  - **§248 — default methods de interface eram descartados em silêncio no JS e
    no Native (paridade cross-target do §209/#213; sessão 9092, TIER 13.1)**
    (23/09): o repro do §248 (`interface Greeter` com o default `greetLoud` +
    `class SimpleGreeter implements Greeter` + `g.greetLoud("Alice")`) imprimia
    `HELLO ALICE` no JVM mas `null` no Native x86_64 e quebrava no JS
    (`TypeError: g.greetLoud is not a function`). Causa raiz, duas faces:
    (a) **Native** — `NativeClassMeta.collectVirtualMethods` só semeava as
    interfaces da *superclasse* na coleta de slots de vtable, nunca as
    interfaces **próprias** da classe, então um default herdado não ganhava slot
    e a chamada caía fora do índice; fix: semear `clazz.interfaces()` e, no
    `addSlot`, deixar um método `ACC_BRIDGE` sobrescrever o slot da interface que
    ele apaga (o dispatch apagado do §483 segue verde). (b) **JS** —
    `JsLoweringContext.skipClass` descarta toda `IRClass` `INTERFACE` e o
    implementador não herdava o default; fix: `JsBackend.injectInterfaceDefaults`
    materializa cada default herdado no implementador que não o sobrescreve,
    antes dos mapas de método/aridade. Prova: `InterfaceDefaultMethodE2ETest`
    **7/7** (JVM + Native + JS; RED medido: Native `null\nHello Bob`, JS exit 1);
    `GenericInterfaceAssignabilityTest` 10/10; suíte completa 3778 run, 0F/0E.

  - **§483 — dispatch por interface genérica no Native passava um primitivo
    boxed ao método concreto → retorno lixo (sessão 9092, TIER 13.2 / §271)**
    (23/09): o repro da §271 (`interface Converter<A,B>` +
    `class IntToString implements Converter<Int,String>` + chamada pela variável
    `Converter<Int,String>`) imprimia `42`/`99` no JVM, Script e JS mas
    `42`/`-820342752` no Native x86_64; a variante herdada
    (`class Sub extends Base`) falhava no link com `undefined reference to
    'Base_run'`. Causa raiz: o gerador de bridges apagados da §356 estava limitado
    a `driver.target == Target.JVM`, então o slot de vtable Native resolvia para o
    `convert(int)String` concreto enquanto o call site boxa contra o param APAGADO
    da interface. Fix: (a) `CompilerClassLowering` agora roda o gerador da §356
    também para `isNative()` e pré-pende o bridge para o slot apagado resolver
    para ele; (b) `NativeClassMeta.collectVirtualMethods` foi refatorado para as
    passagens superclasse/interfaces/métodos próprios compartilharem um `addSlot`
    com o `fnSymbol` tagueado + critério `sigMangles` (a passagem antiga da
    superclasse usava `Owner_nome` sem tag e pulava o slot concreto quando havia
    bridge); métodos de interface nunca sobrescrevem slot de classe. Prova:
    `GenericInterfaceAssignabilityTest` **10/10** (JVM/Script/JS + Native direto e
    herdado; RED medido com o código sem o fix). Suíte completa 3197/0F/0E.

  - **S5.3 fatia 1 (db-parity, lane gaps-db) — bind client-side do wire MySQL
    no cross (peça cross `B71`)** (24/09): `kof_db_mysql_render(val)` rende um
    bind em literal SQL (Int → decimais via `kof_int_to_string`; KofString →
    `'escaped'` com `'`→`''` e `\`→`\\`) e `kof_db_mysql_replace_q(sql,
    literal)` troca o PRIMEIRO `?` por ele (sem `?` → sql inalterado) — port
    do fallback `.Ldb_exec_subst` do x86 em `RuntimeDb1`/`RuntimeDb2`/
    `RuntimeDb4` (`COM_QUERY` não tem `?`). Divergência honesta (mesma postura
    da janela B47): Int×String pela janela do heap cross em vez do limite
    `0x1000000` do mmap x86; Int negativo cai no ramo int correto aqui (o x86
    cai no ramo string por comparação unsigned). Prova: `NativeRiscvDbWireTest`
    3/3 (Int, aspas, backslash, vazio, zero, negativo, só-1º-`?`, sem-`?` +
    sabotagem da B71) em riscv64 + aarch64 sob qemu.

  - **S5.3 fatia 2 (db-parity, lane gaps-db) — execute COM_QUERY no cross
    (peça cross `B72`)** (24/09): `kof_db_mysql_execute(fd, sql)` envia o
    comando texto via B67 e devolve as affected-rows do OK-packet (1 byte /
    FC+2LE / FD+3LE) — port da cauda de execute do x86 (subst de `RuntimeDb4` +
    done/afc/afd/bad de `RuntimeDb5`); erro de I/O, pacote ERR ou first byte de
    resultset → 0. Query com binds não precisa de peça nova (B71 substitui +
    B70 consulta). Prova: `NativeRiscvDbWireTest#execWithBindsAgainstRealMariaDb*`
    + sabotagem da B72 em riscv64 + aarch64 sob qemu contra o **MariaDB real**
    (CREATE 0, INSERT×2 com binds Int/String incl. escape de quote 1, UPDATE 1,
    DELETE sem-match 0 / com-match 1, SQL ruim 0, SELECT-via-execute 0,
    coerência da query com bind `{"id":7,"name":"n7"}`).

  - **S5.2 PARCIAL (db-parity, lane gaps-db) — query texto MySQL completa no
    cross (peça cross `B70`)** (24/09): `kof_db_mysql_query(fd, sql)` envia o
    `COM_QUERY`, lê as definições de coluna, itera TODAS as linhas e devolve
    `List<KofString>` de registros JSON, seguindo o contrato JVM
    (`kof_db_row_to_json`): NULL → `null` sem aspas, só-dígitos → número cru,
    resto (incl. string vazia) → `json_encode_string`. Prova:
    `NativeRiscvDbWireTest` em riscv64 + aarch64 sob qemu contra o
    **MariaDB real** (`SELECT 1`, 2 colunas, `UNION ALL` 2 linhas, NULL+escape)
    + sabotagem da B70. **§488 catalogada ABERTA:** o caminho mysql do x86
    emite NULL como string vazia crua (JSON inválido); a B70 NÃO copia o bug.

  - **S5.2 PARCIAL (db-parity, lane gaps-db) — reader de pacotes MySQL +
    cabeçalho do resultset texto no cross (peças cross `B68`–`B69`)** (23/09):
    `kof_db_mysql_reset(fd)`/`kof_db_mysql_next()` portam o reader de pacotes
    com buffer do `RuntimeDb2`, e `kof_db_mysql_query_text(fd, sql)` envia o
    `COM_QUERY`, pula as definições de coluna + EOF e devolve o número de
    colunas mais o payload lenenc cru da primeira linha. Prova:
    `NativeRiscvDbWireTest` dirige em riscv64 + aarch64 sob qemu contra o
    **MariaDB real** — `SELECT 1` → 1 coluna / `[0x01,'1']`,
    `SELECT 1,'ab'` → 2 colunas / `[0x01,'1',0x02,'a','b']` — mais testes de
    sabotagem da B68/B69. Falta na próxima fatia: materializar todas as linhas
    como valores Kof.

  - **S5.2 PARCIAL (db-parity, lane gaps-db) — framing do `COM_QUERY` MySQL +
    classificação da 1ª resposta no cross (peça cross `B67`)** (23/09):
    `kof_db_mysql_command(fd, sql, buf, buflen)` envia `[0x03][sql]`
    (comprimento 3 bytes little-endian, seq 0) e devolve o 1º payload de
    resposta para o chamador classificar (`>=1` = column count, `0x00` = OK,
    `0xFF` = ERR). Prova: `NativeRiscvDbWireTest` dirige em riscv64 + aarch64
    sob qemu contra o **MariaDB real** — `SELECT 1` → `1`, `SET @x=1` → `0`,
    SQL ruim → `255` — mais teste de sabotagem da B67. Falta na próxima fatia:
    parsear o result set completo (colunas + linhas) em valores Kof.

  - **S5.1 SATISFEITA (db-parity, lane gaps-db) — handshake/auth MySQL sobre o
    socket cross (peça cross `B66`)** (23/09): `kof_db_mysql_handshake(fd, user,
    pass, db)` lê o greeting, parseia o seed, calcula o scramble
    `mysql_native_password`, monta/envia a resposta e lê OK/ERR, sobre a HAL
    `kof_plat_net_*`. Prova: `NativeRiscvDbWireTest` dirige em riscv64 + aarch64
    sob qemu contra o **MariaDB real** — credenciais corretas devolvem `0`
    (pacote OK) e um banco inexistente devolve `-1` (Err 1049) — mais um teste
    de sabotagem da B66. O `db.connect` cross mantém o `DB001` honesto até o
    S5.4 ligar a superfície Kof.

  - **S5.1 (db-parity, lane gaps-db) — montador do auth response MySQL para o
    wire cross (peça cross `B65`)** (23/09): `kof_db_mysql_build_auth_response`
    escreve o frame do handshake response (len 3 bytes + seq 1) e o payload
    (capabilities `0x0008820B`, max-packet, charset, reservados, user, o auth
    response `<20>+scramble` (ou vazio), database, plugin
    `mysql_native_password`), espelhando o `RuntimeDb3`. Prova:
    `NativeRiscvDbWireTest` monta para `passLen=20` e vazio nas 2 archs contra
    um oráculo fixo, mais um teste de sabotagem da B65. Ligar ao socket
    (enviar + ler OK / `AuthSwitchRequest`) é o último passo do S5.1.

  - **S5.1 (db-parity, lane gaps-db) — parser do greeting MySQL para o wire
    cross (peça cross `B64`)** (23/09): `kof_db_mysql_parse_greeting` percorre o
    pacote de handshake do servidor (protocolo 0x0A, versão, conn-id, as duas
    metades do auth-plugin-data) e extrai os 20 bytes do seed, espelhando o
    `RuntimeDb3`. Prova: `NativeRiscvDbWireTest` parseia um greeting sintético
    do MariaDB + um pacote com protocolo ruim nas 2 archs contra um oráculo
    fixo, mais um teste de sabotagem da B64. Montar/enviar a resposta de
    auth-switch e ler o OK ficam na próxima fatia.

  - **S5.1 (db-parity, lane gaps-db) — helper de auth MySQL para o wire cross
    (peça cross `B63`)** (23/09): `kof_db_mysql_scramble` (a fórmula
    `mysql_native_password` `SHA1(pass) XOR SHA1(seed || SHA1(SHA1(pass)))`,
    sobre o SHA1 da B62) e `kof_db_mysql_lenenc` (inteiro length-encoded) agora
    existem em riscv64/aarch64. Prova: `NativeRiscvDbWireTest` roda os dois nas
    2 archs sob qemu contra um oráculo JVM (`MessageDigest` + a fórmula padrão
    do scramble) mais os 3 casos de lenenc e um teste de sabotagem da B63. Ler o
    greeting + a resposta de auth-switch ficam na próxima fatia.

  - **S5.1 (db-parity, lane gaps-db) — SHA1 + bswap para o wire MySQL cross
    (peça cross `B62`)** (23/09): as primeiras primitivas do caminho de auth
    `mysql_native_password` agora existem em riscv64/aarch64 — `kof_sec_sha1_
    block` + `kof_sec_sha1_internal` (entrada curta, `len < 56`) portados do
    `RuntimeDb1` x86, mais `kof_bswap32`/`kof_bswap64`. Prova:
    `NativeRiscvDbWireTest` roda SHA1 nas 2 archs sob qemu contra o oráculo
    `MessageDigest` do JVM (vazio, `abc`, o pangrama de 43 bytes) e um teste de
    sabotagem (remover `B62` faz o `ld` falhar undefined). Lição cross: `lw` no
    RV64 sign-estende onde o `movl` x86 zera — loads de 32 bits que alimentam
    shifts, e as palavras de trabalho do SHA1, recebem zero-extend explícito.
    Scramble/lenenc/greeting ficam na próxima fatia.

  - **§481 — widening de `listOf()` de records que compartilham uma interface
    escolhia o `Record` nu em vez da interface (sessão 9092, issue #596)** (23/09):
    `record Circle(...) implements Shape` + `record Square(...) implements Shape`
    + `listOf(Circle(1), Square(2))` compilava limpo mas a execução no JVM morria
    com `NoClassDefFoundError: Record` — o `firstCommonAncestor` percorria o
    `extends Record` ESTRUTURAL de um record antes das próprias interfaces, então
    o ancestral comum resolvia para `Record` e o `get()` emitia `checkcast
    Record`. Correção (aditiva): pular os dois ancestrais implícitos do JVM
    (`Object`/`Record`) no BFS; a interface compartilhada vence. Prova:
    `HeterogeneousListInferTest` (6/6) roda o repro verbatim no JVM + compila em
    Native/JS; RED medido com o código sem o fix.

  - **§482 ✅ CORRIGIDO — chamada top-level genérica pelo path de MÓDULO/CLI
    agora recebe a adaptação de retorno do §477 (rota selfMethod) + binding
    de witness explícito** (23/09, lane 9093, achado pelo golden do PR #593):
    `idf<Point>(...)` morria no load do JVM com `VerifyError` no `kof build`
    enquanto o mesmo programa passava nos testes de driver puro — a saída
    antecipada resolvida pelo SA no `ExpressionBareCallLowerer` emitia sem
    adapter nenhum. Os dois ramos selfMethod rodam o adapter compartilhado; o
    witness explícito alimenta `GenericReturnAdapter.bindTypeVariables`/
    `emitBound` (o descritor do KofCall fica apagado). Goldens dos
    #590/#593 adotados em `tests/golden/` e validados via `kof bench`.

  - **§278 — `kof.security` agora roda em `--target android` (lane gaps-db,
    `D-TECHDEBT-23/09` "portar as pilhas")** (23/09): o Android reusa o
    `JvmBackend`, e todo shim `kof_sec_*` do JVM é só JCA/`java.util`
    (MessageDigest, Mac, Cipher, SecureRandom, Base64, KeyStore,
    `java.nio.file`) — todos presentes no Android. `KofSecurity.supportedOn`
    agora trata `ANDROID` como `JVM` (`jvmLike`), sem mais recusa
    `SECN000/001/002/003/004/006/007/008` no Android. Prova:
    `KofSecurityTest.androidSecurityCompilesByteIdenticalToJvm`
    (`Default/Main.class` byte-idêntico ao JVM; RED no gate antigo). `kof.gpu`
    no Android segue `GPU001` — sua pilha JVM exige FFM
    (`java.lang.foreign`), ausente no Android (regra 6).

  - **§479 — exceção lançada dentro de lambda de `List.map`/`filter`/`reduce`
    escapava do `try`/`catch (String e)` como `InvocationTargetException` no JVM
    (sessão 9092, issue #594)** (23/09): todo `map`/`filter`/`reduce` passa pelo
    shim de runtime `kof_ho_invoke`, que alcança o `invoke` sintetizado do lambda
    via `java.lang.reflect.Method.invoke(...)` — isso SEMPRE embrulha em
    `InvocationTargetException` a exceção do alvo, e o shim só capturava
    `IllegalArgumentException`, então o wrapper vazava do `catch (String e)` do
    Kof (lowered para `catch java/lang/RuntimeException`). Correção (aditiva):
    desembrulhar a causa do `InvocationTargetException` e relançá-la, seguindo o
    contrato que `kof_await`/`kof_select_any` já usam (§291). Prova:
    `KofHigherOrderTest` 8/8 — `throw` dentro de lambdas de `map`/`filter`/`reduce`
    capturado por `catch (String e)` no JVM/Native x86/JS; RED medido com o código
    sem o fix (só JVM — Native/JS já propagavam corretamente).
  - **B-6.2b — builtin `ring1(fn)` executa uma função Kof em CPL1 (perfil x86_64
    `uefi-ring`) (lane baremetal 9092)** (23/09): a superfície rule-6 decidida em
    `D-BAREMETAL-RING1-SURFACE` está completa. Novo op de IR `KofFunctionAddress`;
    o `ExpressionStaticCallLowerer` emite a chamada `kof_ring1_run` só em
    `Target.NATIVE` + `UEFI_RING` (senão um `NATIVE003` nomeado — R6/R7); o
    `RuntimeRings` ganha `kof_ring1_target` + `kof_ring1_call` + `kof_ring1_run`.
    **Bug raiz corrigido:** o segundo `kof_rings_init` recarregava o `kof_gdt`
    persistente cujo descritor do TSS ficou **busy** pelo primeiro `ltr`, então o
    novo `ltr` tomava `#GP` no gate default `cli;hlt` (hang silencioso) — o
    descritor é re-habilitado antes de cada `ltr`. Prova: `RingPrivilegeE2ETest`
    4/0F sob OVMF real (positivo imprime `KO-RING1 CPL1 OK` + `41` +
    `KO-RING MAIN`; negativo = `NATIVE003` sob `UEFI`/`HOST`).

  - **B-3a — boot path BIOS legado/MBR (x86_64) (lane baremetal 9092)** (23/09):
    o `NativeProfile.BIOS` emite um **MBR flat de 512 bytes** (magia `0xAA55`
    em `0x1FE`) cujo `_start` roda em **16-bit real mode** (`.code16`,
    `CS:IP=0:0x7C00`), imprime `KO-BIOS OK` pela teletipo do BIOS
    (`int 0x10, ah=0x0E`) e espelha no COM1 (0x3F8), e para. O link usa script
    `-T` de BIOS (ENTRY `_start`, `.text.boot` primeiro em `0x7C00`) + `objcopy
    --output-target=binary`. Sem superfície Kof nova. Prova: `BiosBootE2ETest`
    2/0F — artefato de 512 bytes com a assinatura de boot e, sob
    `qemu-system-x86_64` real (SeaBIOS), imprime `KO-BIOS OK` no serial;
    sabotar a assinatura faz o firmware recusar o disco. B-3b (carregar/rodar o
    payload Kof) resta.

  - **B-3b-1 — o setor de boot BIOS carrega o seu setor de payload do disco
    (lane baremetal 9092)** (23/09): o MBR agora lê o **LBA 1 da sua própria
    imagem** via EDD (`int 0x13, ah=0x42`) para um buffer de staging em `0x8000`
    e valida a magia `KOFPAYLD` emitida pelo linker numa seção `.payload`
    forçada ao LMA `0x7E00` (= setor 1, com `KEEP` contra `--gc-sections`); a
    imagem flat é um disco raw válido de 2 setores. O sucesso imprime
    `KO-BIOS OK`; carry do EDD ou magia errada imprime uma falha **nomeada**
    `KO-BIOS LOAD BAD` — nunca um hang silencioso. Prova: `BiosBootE2ETest`
    3/0F — o marcador de carga sob `qemu-system-x86_64` real (SeaBIOS) mais um
    caso de LBA 1 corrompido que tem de imprimir a falha nomeada e nunca o
    marcador de sucesso. Sem superfície Kof nova. B-3b-2 (A20 + long mode) e
    B-3b-3 (rodar o payload Kof) restam.

  - **B-3b-2 — o setor de boot BIOS entra em long mode x86_64 (lane baremetal
    9092)** (23/09): após a carga do payload, o `_start` habilita A20
    (`int 0x15, ax=0x2401`), carrega uma GDT flat (code32 `0x08` / data `0x10` /
    code64 `0x18`), seta `CR0.PE`, far-jump para um stub 32-bit que seta
    `CR4.PAE`, aponta `CR3` para uma PML4→PDPT→PD de identidade (page de 2 MiB
    cobrindo 0..2 MiB), seta `EFER.LME` e `CR0.PG`, e far-jump para o segmento
    code64, cujo código escreve `KO-BIOS LM64 OK` no COM1 e para — prova viva de
    que a CPU roda em long mode. A emissão do setor de boot saiu para
    `NativeBiosBootEmitter` (gate ≤500: `NativeMethodEmitter` tinha 671 ≥ 600,
    agora 515). Prova: `BiosBootE2ETest` 4/0F — o marcador 64-bit sob
    qemu/SeaBIOS real; vizinhos de entry 84/0F (`NativeUefi`/`FreestandingLink`/
    `RingPrivilege`/`NativeE2E`); bateria nativa 305/0F; `check_500` rc=0. Sem
    superfície Kof nova. B-3b-3 (rodar o payload Kof) resta.

  - **B-6.3 — prova de `#GP` no ring1 + sabotagem do descritor da GDT (perfil
    x86_64 `uefi-ring`) (lane baremetal 9092)** (23/09): uma instrução
    privilegiada (`cli`) executada em CPL1 levanta `#GP`, capturado pelo handler
    ring0 e reportado (`KO-RING1 GP OK`) — nunca um hang silencioso; zerar o
    descritor de código ring1 da GDT faz o próprio `iretq` de CPL1 faultar,
    provando que o nível é **enforced** (`KO-RING1 SABOTAGE OK`). O
    `RuntimeRings` aponta o vetor 13 da IDT para um handler de `#GP` que recupera
    quando uma transição ring1 está ativa (flag limpa pelo `kof_ring1_ret`),
    senão para alto (R6); dois selftests rodam no `_start` antes de
    `kof_rings_restore`. Sem mudança de superfície Kof (B-6.2a/b já a definiu).
    Prova: `RingPrivilegeE2ETest` 5/0F sob OVMF real. Fecha o B-6.

  - **§477 — função de topo genérica que devolve `T` puro perdia o `checkcast`
    no call-site numa instanciação reference (sessão 9092, issue #592)** (23/09):
    `T idf<T>(T x) { return x }` + `idf<Point>(Point(5, 6))` compilava limpo mas a
    execução no JVM lançava `NoSuchMethodError: java.lang.Object.x()` — o
    lowering de chamada nua (`ExpressionBareCallLowerer`) só tratava a metade de
    unbox primitivo de um retorno genérico, nunca o `checkcast` para reference.
    Correção (aditiva, semântica congelada intacta): delegar ao helper
    compartilhado `GenericReturnAdapter.emit(...)` (unbox p/ primitivo,
    `checkcast` p/ referência) — o mesmo já usado pelas chamadas de método de
    instância. Prova: `GenericWitnessConstructionE2ETest` 10/10 (repro verbatim
    no JVM/Native x86/JS + programa de faces mistas record/String/primitivo);
    RED medido guardando só o fix com o teste presente.
  - **§478 ✅ CORRIGIDO — `break` dentro de case de switch agora termina o
    SWITCH, não o loop externo (#587)** (23/09, lane 9093): o switch statement
    se registra como contexto quebrável mais interno (`breakLabels.push` em
    volta de default+corpos nos dois ramos) — a semântica "aceito (e
    redundante)" documentada em statements.md §5.5/§6. Prova:
    `SwitchBreakScopeE2ETest` 8/8 (verbatim `208` em JVM/Native/JS; RED eram
    7 falhas de loop truncado) + vizinhos 79/0F.
  - **§476 ✅ CORRIGIDO — case de valor PRIMITIVO em pattern switch com subject
    reference é recusado com SEM035** (23/09, lane 9093, achado pela sonda
    adversarial do #588): a lista mista emitia `if_acmpeq` (EQ de referência)
    sobre literal INT = `VerifyError` no load do JVM. A recusa reutiliza a
    redação SEM035 congelada; case de valor String segue legal (teste de
    controle). Semântica de igualdade inventada zero.

  - **§474 — construção implícita `ClassName<T>(...)` descartava o type-witness
    (lane frontend/sem, issue #585)** (23/09): os dois sites de construção
    implícita do `BuiltinCallTyper` (`infer`/`inferTail`) devolviam a classe CRUA
    (`typeArguments=[]`), então a substituição de `T` no receptor virava no-op e
    `get(): T` emitia `Methodref java/lang/Object` → `NoSuchMethodError` no JVM
    com argumento reference-type (`Box<Point>(Point(5,6))`); a face primitiva
    (§288) mascarava o furo. Fix (aditivo, sem tocar semântica congelada):
    resolve o witness via `MemberResolver.resolveType` (mesmo idioma de
    `listOf`/`setOf`) + `toType` 3-arg para a face `NewExpr`. Prova:
    `GenericWitnessConstructionE2ETest` 5/5 RED→GREEN (repro verbatim `5,6` em
    JVM/Native x86/JS, cadeia+anotada, controle primitivo `42`); re-verificação
    independente (sessão 9092) somou os casos explícitos `new Box<Point>`/`String`
    (6/6).

  - **§475 — `default:` vazio em switch de padrão/destructuring compilava para
    um `goto` auto-referente (loop infinito)** (23/09, lane compilador, sessão
    9092 — issue #588): em `SwitchStmtLowerer.lowerSwitchStmt` (ramo de padrão)
    a label do default vazio era aliasada à label de fim, emitindo
    `KofLabel(end)` imediatamente seguida de `KofJump(end)` na mesma posição —
    a JVM resolvia o salto para o próprio offset (`25: goto 25`) e o programa
    travava (timeout de 30 s). A label do default vazio agora é própria (nunca
    aliasada a `endLabelPat`) com o salto final mantido incondicional, então
    uma label → uma posição em todo backend. Prova: novo
    `SwitchEmptyDefaultE2ETest` 3/3 (os dois casos JVM travavam antes do fix;
    VERDE depois) + vizinhos 53/0F; `check_500` rc=0.

  - **§480 — exceção não capturada no cross nativo perdia a mensagem
    (lane gaps-db, sessão 9092)** (23/09): `.Lthrow_panic` no
    `NativeRiscvAsmRt0` chamava `kof_panic` com o **KofString** da exceção,
    mas `kof_panic` espera `.asciz` cru (lê até NUL) → o byte 0 do header
    terminava a string, então riscv64/aarch64 imprimia só um newline e saía 1
    (silencioso, R6). O fix espelha o `.Lkof_throw_panic` x86:
    `kof_println_string` (ciente de KofString) + `kof_plat_exit(1)`. Prova:
    novo `KofDbE2ETest.crossUncaughtThrowPrintsMessageAndExitsNonZero`
    (binários reais sob qemu, RED→GREEN) + `crossNativeMysqlRefusalNamesTruthfulDb001`;
    `check_500` rc=0 (`Rt0` fica 598).

  - **Perfil UEFI B-2 (lane baremetal): `--profile uefi` emite PE32+ bootável**
    (23/09, lane 9093): o novo `NativeProfile.UEFI` faz o backend nativo x86-64
    emitir uma imagem PE32+ estática, sem PLT/GOT (subsystem 10), que boota sob
    OVMF/qemu e imprime via `ConOut->OutputString`. As chamadas de plataforma
    passam pelo ramo UEFI da costura `kof_plat_*` (`RuntimeUefi`): write/writev
    (UTF-16LE+CRLF), thread_id, sync no-op e `kof_plat_heap_grow` =
    `gBS->AllocatePool` por baixo do caminho de GC/alloc; capacidades libc
    seguem recusadas com o diagnóstico `NATIVE003` herdado. Prova:
    `NativeUefiE2ETest` 3/0F (shape PE32+, NATIVE003, boot OVMF real imprimindo
    `KO-UEFI OK` no COM1). O cache de `RuntimeSlices` agora é chaveado por
    perfil (o 2º perfil não reusa corpos do 1º); o gate <=500 do `NativeBackend`
    foi pago com a extração de `NativeLinkPolicy` (608->571 linhas).

  - **B-6.2a anéis de privilégio x86_64 — entrada CPL1 via `iretq` + trap-back ao ring0**
    (23/09, lane 9092): sobre o B-6.1, o `RuntimeRings` agora emite uma pilha
    ring1 de 8 KiB (`kof_ring1_stack`, `.bss`), `kof_ring1_entry` (monta o frame
    de `iretq` de 5 qwords `SS=0x20` / `RSP=topo ring1` / `RFLAGS` /
    `CS=0x18|RPL1=0x19` / `RIP=kof_ring1_stub`), `kof_ring1_stub` (roda em CPL1
    e captura `%cs` em `%r15`), `kof_ring1_trapback` (handler ring0 de um gate de
    IDT DPL=3 `0xEE` no vetor `0x81`, troca de volta para a pilha ring0 salva) e
    `kof_ring1_selftest` (confere que o CS observado tem RPL=1, imprime
    `KO-RING1 CPL1 OK`). O `_start` chama
    `kof_rings_init; kof_rings_selftest; kof_ring1_selftest; kof_rings_restore`.
    Sem superfície de linguagem (o lowering de `ring1(fn)` é o B-6.2b). Bug raiz
    corrigido: os descritores GDT de código/dados ring1 estavam codificados
    `0xFA`/`0xF2` = **DPL=3**, não DPL=1, então o `iretq` para CPL1 falhava a
    checagem `DPL==RPL` e tomava `#GP(0x18)` — diagnosticado imprimindo o error
    code do `#GP`; os access bytes corretos com DPL=1 são `0xBA` (código) /
    `0xB2` (dados). Prova: `RingPrivilegeE2ETest` 2/0F sob OVMF real; bateria de
    regressão 117/0F (`NativeE2ETest` 68, `NativeUefiE2ETest` 3,
    `FreestandingLinkE2ETest` 8, `ConformanceMatrixTest` 14,
    `NativeRuntimeSliceRegistryTest` 7, `ArtifactSizeTest` 6,
    `PlatformSeamSabotageTest` 5, `DtoaParityE2ETest` 3, `LinkByUseTest` 3).

  - **B-6.1 anéis de privilégio x86_64 — GDT/TSS/IDT do Kof + prova de CPL0**
    (23/09, lane 9092): o novo `NativeProfile.UEFI_RING` (via
    `NativeProfile.of("uefi-ring")`, programático; a whitelist do CLI continua
    `host|freestanding`) emite, sobre a imagem UEFI do B-2, uma GDT do Kof
    (código/dados ring0 `0x08`/`0x10`, código/dados ring1 `0x18`/`0x20`, TSS
    64-bit `0x28`), um TSS com `rsp0` patcheado em runtime e uma IDT de 256
    gates. O `_start` roda `lgdt` + um `lretq` far (recarrega `CS=0x08`),
    `lidt`, `ltr`; um `int3` controlado precisa cair no handler ring0 de `#BP`
    (contador em memória) antes de o `_start` imprimir `KO-RING IDT OK`, então
    a GDT/IDT do firmware são restauradas (`lgdt`/`lidt`) + `popfq` e o
    controle volta ao caminho UEFI. O ramo UEFI da costura `kof_plat_*` agora
    cobre `UEFI_RING` (`activeIsUefi()`). Sem superfície de linguagem (rule 6
    intocada). Prova: `RingPrivilegeE2ETest` 2/0F (boot OVMF imprime `KO-RING
    IDT OK`+`KO-RING MAIN`; o `UEFI` puro não emite a prova de anel). Bug raiz
    corrigido: o patch de IDT do `#BP` escrevia a entrada 0 em vez do vetor 3
    (faltava o offset `+48`), então o `int3` batia no gate padrão `cli;hlt` e
    travava sob OVMF.

  - **Paridade DB S1 — `mariadb://` é alias do wire `mysql://` no Native x86-64**
    (23/09, lane gaps-db): o `kof_db_connect_inner` (`RuntimeDb2`) agora casa
    `mariadb://` e reusa todo o caminho mysql (`r12 = schemeStart+2`, para o
    `leaq 8(%r12)` compartilhado cair após o scheme de 10 chars); `kof_db_type`
    reporta a família mysql (2), então `execute`/`query` e o ORM pegam o wire. A
    mensagem `DB001` agora lista `mariadb://`. No riscv64/aarch64 tanto `mysql://`
    quanto `mariadb://` seguem recusando com `DB001` (wire mysql cross não
    portado — R7 honesto). Prova:
    `KofDbE2ETest#nativeMariadbAliasWireProtocol` (MariaDB real, nas duas formas
    `user:pass@host` e só-host, byte-idêntico `{"id":7,"name":"Alias"}`);
    `KofDbE2ETest` 28/0F + `NativeDbSchemeRefusalAsmTest` 2/2.

  - **Paridade DB S2 — driver JDBC ausente agora é diagnóstico `DB001` nomeado
    no JVM/JS/Android** (23/09, lane gaps-db): um `db.connect` para URL JDBC cujo
    driver está ausente do classpath vazava o `SQLException: No suitable driver`
    cru. `JvmConfigRuntime.kof_db_connect/connect2` (JVM, e Android pelo mesmo
    runtime) e `KofJsDbBridge.connect/connect2` (delegate JS) agora mapeiam esse
    caso para `DB001: no JDBC driver for this URL (add the driver to the
    classpath): <url>`, enquanto uma falha **real** de conexão (servidor fora /
    credencial ruim) passa intacta — nunca mascarada como `DB001` (R6). A medição
    por-driver (h2/sqlite/mariadb/postgres) já é coberta pelo corpus E2E. Prova:
    `KofDbE2ETest#jvmMissingJdbcDriverNamesGapNotSilent` +
    `#jvmRealConnectionFailureIsNotRelabeledDb001` +
    `#jsMissingJdbcDriverNamesGapNotSilent` +
    `#jsRealConnectionFailureIsNotRelabeledDb001` (a do JVM VERMELHA no código
    antigo); `KofDbE2ETest` 32/0F.

  - **Paridade DB S3/S4 — `mongodb://` e Oracle viram diagnóstico nomeado onde
    não rodam** (23/09, lane gaps-db): `mongodb://` é real no JVM/Android (roundtrip
    ORM completo sobre o `mongodb-driver-sync` real, `KofOrmE2ETest#mongoCrud`) e
    `DB001` **declarado** no JS/Native — `KofJsDbBridge.connect/connect2` agora
    recusa com mensagem ciente do scheme, em vez do "sem driver JDBC" enganoso;
    driver mongo ausente no JVM também é nomeado. Oracle não tem driver/servidor
    aqui e, como o Mongo, nunca será servidor caseiro (R9): `jdbc:oracle:` conecta
    com driver presente, senão dispara o `DB001` do S2; `oracle://` nu é `DB001`
    (não é URL JDBC); o Native recusa via S0. A tabela de estado medido do plano
    também foi corrigida — os schemes nus são aceitos **como escritos só no
    Native**; JVM/Android/JS precisam de URL `jdbc:`. Prova:
    `KofDbE2ETest#jsMongodbSchemeNamesGapNotSilent` +
    `#jvmMongodbMissingDriverNamesGap`; `KofOrmE2ETest#mongoCrud` (JVM real).

  - **fix(db): a mensagem `DB001` do cross (`riscv64`/`aarch64`) não anuncia mais
    um scheme não portado** (23/09, lane gaps-db): o runtime riscv/aarch64 recusava
    `mysql://`/`mariadb://`, mas o texto `DB001: unsupported db scheme` dizia
    `(native: sqlite:, mysql://)` — anunciava um scheme que o código rejeita (gap
    R6 escondido atrás de propaganda falsa). Agora diz a verdade: `sqlite: only
    here; mysql:// / mariadb:// wire is x86-64 only`. Prova:
    `NativeRiscvRuntimeSliceRegistryTest#crossDb001MessageDoesNotAdvertiseUnportedMysql`
    (lê o asm de produção do runtime; 9/9).

  - **§448 ✅ CORRIGIDO — `toString` de `Double`/`Float` no Native agora casa o
    oráculo JVM nos subnormais (x86 e cross)** (23/09, lane baremetal): o dtoa
    nativo escolhia o decimal **mais curto** em vez do **mais próximo entre os
    mais curtos**, então `println(5E-324)` imprimia `5.0E-324` onde o JVM
    imprime `4.9E-324`. Causa-raiz: `RuntimeDtoa`/`NativeRiscvAsmRtB45` iteravam
    `snprintf("%.*e")` + `strtod` (glibc) e ficavam com a primeira precisão que
    fazia round-trip. Corrigido portando o `DoubleToDecimal`/`FloatToDecimal`
    (Schubfach) do JDK para asm libc-free — `RuntimeDtoaSchubfach` no x86 (o
    `RuntimeDtoa` libc foi DELETADO; o runtime x86 agora tem zero refs de
    formatação libc) e `NativeRiscvSchubfach`/`…Format`/`…Float` no cross (o
    aarch64 herda pelo `NativeAarch64Translator`, que ganhou `mulhu`/`sltu`/
    `xori`/shifts por registrador). Prova: `DtoaParityE2ETest` (freestanding,
    sem snprintf/strtod no dynsym) + `FreestandingLinkE2ETest.
    freestandingFloatPrintMatchesJvmOracle` byte-a-byte vs o oráculo JVM medido;
    o corpus do `NativeRiscvDtoaTest` estendido com subnormais de `Double`+
    `Float` é byte-parity em riscv64 **e** aarch64, com um pin de que o runtime
    podado não tem `call snprintf`/`call strtod`. Contagem viva 6→5.

  - **DB-3/DB-1 cross — `orm.page` REAL no riscv64/aarch64 (leitura row-object
    COMPLETA, E-parte-6)** (23/09, lane gaps-db): `SELECT * FROM "t" LIMIT ?
    OFFSET ?` com os 2 params bindados após a coerção `intValue()` do host
    (int direto, long/double/float truncam p/ int32, KofString = atoi superset,
    null = 0); mesmo loop de campos de `all`/`where` (`read_field`, §397). Peça
    nova `NativeRiscvAsmRtB60` (port de `RuntimeOrm8`); `CROSS_FACES` +=
    `kof_orm_page` (13 nomes). Corrigido o mesmo bug latente de leitura de
    `Double` no `RuntimeOrm8` (`movsd %xmm0`, §449). **O row-object do ORM
    agora está COMPLETO no cross** — `find`/`all`/`where`/`where_op`/`page`
    todos reais, nenhuma face `ORM001` compile-time restante. Prova:
    `KofOrmE2ETest#crossNativeF2c3PageMatchesX86Oracle` (golden x86 explícito:
    LIMIT/OFFSET, offset além do fim, limit 0, Long→intValue, Double 2.9→2,
    campo sem coluna→throw); `KofOrmE2ETest` 71/0F/3skip.

  - **DB-3/DB-1 cross — `orm.where`/`orm.where_op` REAIS no riscv64/aarch64
    (E-parte-5)** (23/09, lane gaps-db): `SELECT * FROM "t" WHERE "f" <op> ?`
    com a whitelist do operador idêntica ao host (`==`→`=`, throw
    `ORM operator not allowed: <op>`; `<`/`>`/`<=`/`>=`/`!=`/`LIKE`), o value
    bindado pelo classificador do `RuntimeOrm7` (box §284 / KofString / null) e
    o loop de campos do `all`. Peça nova `NativeRiscvAsmRtB59`; `CROSS_FACES` +=
    `kof_orm_where`, `kof_orm_where_op` (12 nomes). **Dois bugs latentes
    achados e corrigidos ao provar (§449):** `RuntimeOrm7`/`RuntimeOrm8` tinham
    o mesmo bug de leitura de `Double` (`movq %rax` após
    `sqlite3_column_double`), e o `kof_orm_bind_key` da E-parte-3 divergia do
    `RuntimeOrm5` (tag Bool/desconhecida deve lançar `orm.find bind value:
    unsupported type on Native (ORM001)`, não bindar int64/null) — o cross
    agora casa com o x86, com o throw de chave Bool pinado no teste do `find`.
    Prova: `KofOrmE2ETest#crossNativeF2c2WhereMatchesX86Oracle` (=, >=, LIKE,
    !=, vazio→lista vazia, op inválido→throw, campo sem coluna→throw);
    `KofOrmE2ETest` 70/0F/3skip.

  - **§423 ✅ CORRIGIDO — `channel`/`channel<T>` send/receive nos alvos NATIVOS
    riscv64/aarch64 estão PORTADOS** (23/09, lane baremetal; TIER 13.4): o
    runtime era só x86, então qualquer programa com canal falhava no link com o
    críptico `undefined reference to 'kof_channel_new/send/receive'`; desde
    21/09 estava gateado pelo `NAT005` honesto. Nova fatia
    `nat/NativeRiscvAsmRtB61` implementa `kof_channel_new`/`send`/`receive` em
    asm riscv64 cru (aarch64 via `NativeAarch64Translator`): fila ligada FIFO
    (struct 56 B — head@0/tail@8/count@16/lock@20; nó 16 B — value@0/next@8),
    lock `amoswap.w` com `fence rw,rw` acquire/release, bloqueio no futex
    `kof_plat_sync` (PRIVATE 128/129) e `receive` vazio que destrava e faz
    `kof_time_sleep(1 ms)` antes de re-tentar (sem busy-spin). O gate `NAT005`
    do `ChannelWrites` foi removido. Prova: `BareCollectionPrimitiveArgE2ETest`
    (nu → `1`, tipado → `11`, RODAM sob qemu nas duas arches, paridade JVM),
    `KofConcurrency2Test.channelWithSpawnCrossArch` (worker→main FIFO `v=42`,
    nas duas arches), `NativeRiscvRuntimeSliceRegistryTest` 8/8; bateria cross
    177/0F/2skip. Contagem viva 5→4.

  - **Linker script do freestanding B-1 — heap/pilha configuráveis e `_end`
    explícito** (23/09, lane baremetal): o link `FREESTANDING` passa a usar um
    script `-T` gerado (`ENTRY(_start)`, base `0x400000`) que fecha a `.bss`
    REAL com `_end` (topo da varredura de raízes estáticas do GC) e reserva uma
    arena de heap `__kof_heap_start..__kof_heap_end` e uma pilha
    `__kof_stack_bottom..__kof_stack_top` na mesma PT_LOAD NOBITS, com tamanho
    por `KOF_HEAP_SIZE`/`KOF_STACK_SIZE` (ou props `kof.heap.size`/
    `kof.stack.size`; default 8 MiB/1 MiB). O `_start` troca o `%rsp` para a
    pilha do script e o `kof_plat_heap_grow` faz bump na arena em vez de `mmap`
    (`RuntimeFreestanding`) — o freestanding deixa de precisar de SO para
    memória; arena esgotada falha por `kof_panic` ("out of memory"), nunca um
    ponteiro inválido silencioso. **§451 ✅ CORRIGIDO (achado ao provar isto):**
    o `kof_panic` passava a mensagem `.asciz` ao `kof_println_string` (que
    espera KofString e lê um comprimento falso) -> lixo em qualquer OOM nativo
    x86; agora imprime via `kof_print` (C-string). Prova:
    `FreestandingLinkE2ETest` 8/0 (tamanhos de heap/pilha medidos dos símbolos
    do ELF; o OOM de arena mínima é honesto), `DtoaParityE2ETest` 3/0,
    `NativeE2ETest` 68/0, `ArtifactSizeTest` 6/0, bateria 100/0F +
    `CmdBuildProfileTest` 4/0, `CmdRunProfileTest` 2/0.

  - **§450 ✅ CORRIGIDO — baseline de símbolos do hello riscv64/aarch64 no
    `ArtifactSizeTest` ficou stale após o §448** (23/09, lane gaps-db): o dtoa
    Schubfach libc-free é alcançável pelo box printer do hello
    (`kof_box_to_string → kof_double_to_string`), então o conjunto de símbolos
    alcançáveis cresceu 45→55 (+10); os bytes ficaram dentro da tolerância.
    `HELLO_RV_SYMS`/`HELLO_AA_SYMS` 45→55; o vermelho reproduziu byte-a-byte no
    tip base `a148a9557` sem os commits da E-parte-5/6 (atribuição = §448, não
    o trabalho ORM). Prova: `ArtifactSizeTest` 6/6.

  - **DB-3/DB-1 cross — `orm.all` REAL no riscv64/aarch64 (leitura row-object
    do ORM → `List`, E-parte-4)** (23/09, lane gaps-db): `SELECT * FROM "t"` sem
    bind, resolvendo `kof_orm_ctors` uma vez antes do loop de linhas; cada linha
    é um `kof_alloc` + `kof_init_object` novo preenchido pelo
    `kof_orm_read_field` compartilhado (casamento por nome + §397) e acumulado
    com `kof_list_add`. Tabela vazia devolve a **lista vazia** (nunca null), como
    o host. Peça nova `NativeRiscvAsmRtB58` (port de `RuntimeOrm6`);
    `CROSS_FACES` += `kof_orm_all` (10 nomes). Também corrigido o mesmo bug
    latente de leitura de `Double` no `RuntimeOrm6` (ver §449). Prova:
    `KofOrmE2ETest#crossNativeF2c1AllMatchesX86Oracle` (golden x86 explícito:
    lista vazia, ordem rowid independente da inserção, multi-entidade, todos os
    tipos incl. zero/null); `KofOrmE2ETest` 69/0F/3skip, bateria focada
    95/0F/3skip. Naquele corte seguia `ORM001` honesto no cross:
    `where`/`where_op` e `page` (R6/R7) — fechados nas E-parte-5/6.

  - **DB-3/DB-1 cross — `orm.find` REAL no riscv64/aarch64 (leitura
    row-object do ORM, E-parte-3)** (23/09, lane gaps-db): as faces de leitura
    precisam do resolver por-programa `kof_orm_ctors`
    (className→vtable/typeId/size), agora emitido no cross por
    `NativeRiscvOrmCtors` + `NativeBackend.collectOrmCtorClasses`
    target-agnóstico, ligados no `NativeArchEmitter` (fora da região podada
    `[rtStart,rtEnd)`). Peças novas `NativeRiscvAsmRtB57` (`kof_orm_find`, port
    de `RuntimeOrm5`) e `NativeRiscvAsmRtB57Helpers` (`kof_orm_bind_key`,
    `kof_orm_read_field`); `CROSS_FACES` += `kof_orm_find`. **Dois bugs x86
    latentes achados e corrigidos** (ver §449): o `NativeOrmCtors` só funcionava
    com uma entidade (o miss caía no dado inline + `%rsi` sobrescrito) e o
    `RuntimeOrm5` lia `Double` com `movq %rax` após `sqlite3_column_double`
    (retorno em `%xmm0`) → sempre `0.0` (`movsd %xmm0`). Prova:
    `KofOrmE2ETest#crossNativeF2bFindMatchesX86Oracle` (golden x86 explícito,
    byte-parity riscv64/aarch64); `KofOrmE2ETest` 68/0F/3skip.

  - **§446 ✅ FIXED — split do `check_500` dos dois críticos herdados das fatias
    1–2 do §280** (22/09, lane 9093): `TypeChecker` 606→436 e
    `StatementAnalyzer` 600→449 com a extração de `SemBinaryResultTyper`
    (binárias SEM053/SEM062/SEM100/SEM001/SEM002 + aritmética) e
    `SemAssignmentAnalyzer` (`analyzeAssignmentStatement`); behavior-preserving,
    linhas do baseline removidas, `check_500` rc=0. Registrado aqui para fechar o
    gap CHANGELOG×ledger deixado por aquele commit (o ledger já lista §446 como
    FIXED).

  - **FFI 3.7 passo 2 — `Int[]`/`Float[]`/`Bool[]`→`ptr` no Native x86-64 + fix
    `Bool[]` no JVM (D6-2)** (22/09, lane FFI/kof-c): o copy-in de array escalar
    agora cobre toda classe de elemento cuja largura de slot Kof iguala a largura
    C — o mesmo helper copia `len*elemSize` com o tamanho do elemento passado em
    `%rsi` (`Int`/`Float` 4 B, `Bool` 1 B, `Long`/`Double` 8 B); não foi preciso
    loop de estreitamento. `String[]` (array de ponteiros) e arrays no cross
    seguem `FFI001` na linha da declaração (R6). No caminho, corrigiu um **crash
    latente do JVM** (R6): um `extern` que recebia `Bool[]` compilava no JVM mas
    morria em runtime porque `MemorySegment.copy` não suporta `bool[]`;
    `kof_ffi_copy_in` agora converte para `byte[]` 0/1 antes. Prova:
    `FfiNativeArrayE2ETest` 2/2 (shim `.so` do gcc, golden byte-a-byte igual ao
    oráculo JVM incl. bordas vazio/negativo/`Bool[]`) + pin de gate
    `String[]`/cross `FFI001`; três pins antigos de gap migrados de `Int[]` para
    `String[]` (`FfiArrayE2ETest`, `FfiNativeE2ETest`, `FfiE2ETest`);
    `Ffi*+Native*` 403/0F/165skip.

  - **FFI 3.7 passo 1 — array escalar `T[]`→`ptr` no Native x86-64 (copy-in
    por chamada, D6-2)** (22/09, lane FFI/kof-c): `extern` que recebe
    `Long[]`→`long*` ou `Double[]`→`double*` (largura de slot do elemento ==
    largura C, 8 B) agora binda no alvo x86-64. O gate
    (`CompilerPipeline.nativeExternBound`) aceita param array só no x86-64 e só
    para elementos `j`/`d`; o lowering o marca com um tipo sintético
    `kof.ffi`/`array(elem)`; `NativeFfiCall.emitX86` faz um pré-passo de pack que
    copia cada array para um buffer novo via o helper de runtime
    `kof_ffi_pack_array` (atrás de `NativeBackend.ffiUsesArray`) e passa o
    buffer como um registrador INTEGER. A semântica é byte-a-byte igual à do
    JVM: **copy-in por chamada**, a C nunca escreve de volta (o out-buffer do
    D6-3 é o caminho de escrita). `Int[]`/`Float[]`/`Bool[]` (4/1 B, exigem loop
    de estreitamento) e qualquer array no cross seguem `FFI001` na linha da
    declaração (R6). Prova: `FfiNativeArrayE2ETest` 2/2 — shim `.so` compilado
    com gcc (`suml`/`sumd`) ligado ao binário nativo, golden byte-a-byte igual
    ao oráculo JVM do mesmo programa (array vazio e valores negativos
    incluídos), + pin de gate `Int[]`→`FFI001`; bateria FFI 50/0F/12skip.

  - **FFI 3.7 fatia 4 — struct por valor como parâmetro no cross riscv64/aarch64**
    (22/09, lane FFI/kof-c): um `record` Kof de campos INTEGER (≤ 16 B) passado
    por valor a um `extern` agora binda nos alvos cross, fechando o `FFI001`
    honesto para o qual a fatia de fixture C foi construída. O gate
    (`CompilerPipeline.nativeExternBound`) aceita o param struct só pelo
    caminho de registradores INTEGER (`FfiStructLayout.crossIntRegisterOnly`), e
    o caller empacota cada eightbyte do `record` no seu registrador inteiro
    (`a0`/`a1`; AAPCS64 `x0`/`x1` pelo tradutor — um texto, duas archs).
    Float/HFA e > 16 B (BYREF/MEMORY) seguem `FFI001` na linha da declaração
    (R6). Prova: `FfiCrossStructParamE2ETest` 5/5 — um objeto de fixture
    montado à mão (`as` cross, sem cc cruzado no host) chamado pelo binário Kof
    sob qemu, golden `42/2/6` medido nas duas archs (extensão de sinal de campo
    negativo, struct de 3 campos/12 B, struct + arg escalar) mais as duas
    rejeições de gate; bateria FFI 107/0F/2skip. `docs/development/kof-c-cross.md`
    metade de link (C4-x) + metade de ABI agora completas.

  - **`kof.orm` cross riscv64/aarch64 — fatias A–E: `deleteAll`/`count`/`create`/`migrate`/`count_where`/`delete` reais sobre SQLite**
    (22/09, lane gaps-db, DB-3/DB-1): o ORM era só x86-64 no Native (os alvos
    cross recusavam todo `orm.*` com `ORM001` em compile-time). A fatia A
    porta `kof_orm_delete_all` + `kof_orm_count` (F1a de `RuntimeOrm1`) para o
    runtime riscv64 (peça `RtB50`, sobre os `kof_db_*` SQLite de
    `RtB46/RtB47`; aarch64 herda pelo tradutor): semântica idêntica ao x86
    (id ruim → `unknown db connection: <id>`; delete_all → false em SQL error,
    true no sucesso; count → 0 em erro/zero-row, valor exato via
    `sqlite3_column_int64`). `KofOrm.fnSupportedOn` flipa SÓ essas duas faces
    em NATIVE_RISCV64/AARCH64 — o resto mantém o `ORM001` honesto em
    compile-time (R6/R7). Prova:
    `KofOrmE2ETest.crossNativeF1aDeleteAllCountMatchX86Oracle` — oráculo x86-64
    `3/true/0/true/0/false` byte-idêntico em riscv64 e aarch64 sob qemu (incl.
    o edge SQL-error de drop da tabela), + pin do gate de `orm.all` no cross.
    A fatia B adiciona `create` (peça `RtB51`, port de `RuntimeOrm2` —
    parser do schema + DDL + `sqlite3_exec`), também byte-idêntico ao
    oráculo x86-64: DDL lido do `sqlite_master`
    (AUTOINCREMENT/UNIQUE/VARCHAR/tipos), `unique` em campo generated
    suprimido, entidade sem `generated` com o próprio shape de pk, id ruim
    → throw `unknown db connection: <id>` e recuperação; `KofOrmE2ETest` 62/0F.
    A fatia C adiciona `migrate` (peça `RtB52`, port de `RuntimeOrm1`) —
    `kof_migrations` criada sob demanda, idempotente por nome (2ª chamada
    true sem re-rodar o DDL), SQL inválido → false sem registrar (mesmo
    contrato do host), `applied_at` = epoch-ms via `kof_time_now` e o
    INSERT bindado; `KofOrmE2ETest` 63/0F.
    A fatia D adiciona `count_where` (peça `RtB53`, port de `RuntimeOrm3`) —
    `SELECT COUNT(*) FROM "t" WHERE "f" = ?` montado sem concatenação, value
    ligado pelo tag da caixa §284 (Int/Long/Bool/Double/Float, FP pela ABI
    fa0), KofString (`bind_text` transiente) e null (`bind_null`), forma
    estranha → throw `ORM001` honesto; a sonda Q4 achou e o fix fechou o
    **§447**: no Native o box do argumento ORM saía como
    `java.lang.Boolean.valueOf` → o dispatch nativo de `valueOf` (o caminho
    de conversão para String do concat/print) ligava TEXTO `"true"` (o JVM
    liga `Boolean`/1; o caso Int ficava mascarado pelo affinity numérico do
    SQLite), então o `ExpressionOrmCallLowerer` agora emite `kof_box_bool`
    para Bool nos alvos nativos (resíduo não-Bool catalogado no §447).
    Prova: `KofOrmE2ETest.crossNativeF3aCountWhereMatchesX86Oracle` — oráculo
    x86-64 + byte-parity riscv64/aarch64 + perna host JVM na matriz toda de
    valores (bool literal/var/false casam as linhas certas, linha TEXT não
    casada, injeção/negativo/miss/ORM001/id-ruim); `KofOrmE2ETest` 64/0F;
    bateria 315/0F.
    A fatia E adiciona `delete` (peça `RtB54`, port de `RuntimeOrm9` + o
    parser de schema do `RuntimeOrmSchema`) — pk = primeiro campo
    `:generated` (senão 0), `DELETE FROM "t" WHERE "pk" = ?` (tabela com
    aspas pelo `.L54_qq`, pk crua pelo `.L54_qraw`), key pelo mesmo
    classificador do `count_where` (caixa §284 / KofString / null; tag
    estranha → `bind_null`, como o x86), sempre true no SQLITE_DONE (miss
    também — `execute1 >= 0` do host), falha de prepare/step → `sqlite:
    <errmsg>` (R6), id ruim → throw do host; a ABI do parser
    (`.L54_ps(schema*) -> ftab/nbuf/nFields/pkIndex`) é reusada pelas
    próximas fatias row-object. `CROSS_FACES` (ex-`CROSS_F1A`) ganha
    `kof_orm_delete` (6 nomes). Prova:
    `KofOrmE2ETest.crossNativeF2c3DeleteMatchesX86Oracle` byte-parity
    oráculo x86-64 + riscv64/aarch64 + perna host JVM (pk Long > int32, pk
    negativa, miss true, re-delete, id ruim throw + recuperação);
    `KofOrmE2ETest` 65/0F; bateria 91/0F.
  - **F2a `orm.save` no cross do Native (D-DB-GAPS DB-3/DB-1, fatia E-parte-2a)**
    (23/09, lane gaps-db): `orm.save` é REAL no riscv64/aarch64 — peças novas
    `NativeRiscvAsmRtB55` (`kof_orm_save`, port de `RuntimeOrm4`) +
    `NativeRiscvAsmRtB55Helpers` (globais `kof_orm_conn`/`sb_append`/`sb_char`/
    `sb_qraw`/`sb_qstr`/`bind_field`); o parser de schema de RtB54 foi promovido
    ao global `kof_orm_parse_schema` (ABI x86 reusada); `CROSS_FACES` ganha
    `kof_orm_save` (7 nomes). Contrato (3 saídas do host): pk 0/null → INSERT
    sem a PK + chave gerada (`sqlite3_last_insert_rowid`) + NOVA instância com
    a pk patchada (`nFields*8+16`, vtable/typeId do header da FONTE, offset
    `pkIndex*8+16`); pk≠0 → UPDATE (PK bindada por último) → MESMO ponteiro;
    UPDATE 0 linhas → upsert INSERT de todas as colunas; int/long==0 e
    double/float truncado (`fcvt.l.* rtz`) decidem INSERT; falha → throw
    `sqlite: <msg>` (R6). Prova:
    `KofOrmE2ETest.crossNativeF2aSaveMatchesX86Oracle` — oráculo x86-64
    byte-parity riscv64/aarch64 + perna host JVM
    (`true/1/1/1/1/{"name":"Mel2"}/9/2/10/3/unknown db connection: db2/after-throw`);
    `KofOrmE2ETest` 66/0F/3skip + bateria 26/0F. Bug de causa-raiz caçado na
    perna aarch64 (Q4/Q0): o tradutor mapeia `s10`→`x16` (scratch CALLER-SAVED
    da AAPCS64), então manter o contador de loop em `s10` através de chamadas C
    (`sqlite3_bind_*`/`kof_memcpy`) corrompia-o (crash rc=1, saída parcial
    `true|1|1`; riscv64 imune); fix: índice `i` movido p/ slot de pilha
    `48(sp)`. Regra da lane: nunca manter estado vivo em `s10` através de um
    `call` no cross.
  - **F2c3 `orm.saveAll` no cross do Native (D-DB-GAPS DB-3/DB-1, fatia E-parte-2b)**
    (23/09, lane gaps-db): `orm.saveAll` é REAL no riscv64/aarch64 — peça nova
    `NativeRiscvAsmRtB56` (port de `RuntimeOrm10`): loop `kof_list_size`/
    `kof_list_get` → `kof_orm_save` por item, a instância patchada descartada
    (a List de entrada guarda os objetos originais, como o host); retorna true
    quando o loop termina, erro de SQL lança `sqlite: <msg>` pelo próprio save
    (R6); sem `className` (o item carrega vtable/typeId). GC-safe: id/items/
    table/schema em slots de pilha (o GC conservativo varre a stack).
    `CROSS_FACES` ganha `kof_orm_save_all` (8 nomes). Prova:
    `KofOrmE2ETest.crossNativeF2aSaveAllMatchesX86Oracle` — oráculo x86-64
    byte-parity riscv64/aarch64 + perna host JVM (lote de INSERTs, count,
    ordem, lote de UPDATEs por pk, lista vazia true sem tocar no banco, id ruim
    throw + recuperação); `KofOrmE2ETest` 67/0F/3skip + bateria 93/0F/3skip.
  - **check_500 vermelho fechado — `TypeChecker` 606 + `StatementAnalyzer` 600 (known-bugs §446)**
    (22/09, lane typer, sessão 9093): os dois arquivos CRÍTICOS herdados das
    fatias do §280 foram splitados por responsabilidade (`SemBinaryResultTyper`
    +166, `SemAssignmentAnalyzer` +146, movimentos verbatim), `check_500` rc=0;
    bateria 171/171 na superfície tocada. Entrada de paridade adicionada aqui
    para o gate CHANGELOG×ledger refletir o fechamento.

  - **Diagnósticos SEM/PKG agora reportam a posição real da origem (known-bugs §280)**
    (22/09, lane typer, sessão 9093 — a unidade escrita em 21/09 ficou no meio por
    um turno morto e foi terminada pela regra do dono-sumido): 53 dos 91 sites
    hard-coded `error("", 0, 0, 0, …)` agora reportam o file/line/column do nó AST
    (busca `node.position()` dentro dele), então os repros canônicos não imprimem mais o
    `:0:0` fantasma — `Int f() { return "x" }` → SEM010 na linha do return,
    `show(42)` → SEM014 no call-site, escrita em `val` → SEM037 no alvo, `new`
    de classe abstrata → SEM041 no `new`, e os sites do MemberCallTyper
    (SEM072/SEM025) no call. A posição flui por um helper novo
    `DiagnosticCollector.errorAt(node, msg, code)` (1 linha por site);
    `checkArgTypes/checkCtorArgTypes` unificados na assinatura com node
    (os 13 callers passam o nó de chamada) — nada mais mudou de comportamento. Nota do land:
    o split §442 (`fd5119f6`) pousou no meio da unidade e deixou o corpo pré-split
    do `NewExpr` do WIP em conflito; resolvido mantendo a delegação do split e
    re-aplicando os 3 sites do `NewExpr` em `SemNewExprTyper` (preservar os dois
    lados, refazer o meu por cima). Prova: `DiagnosticSourceLocationTest` 5/5
    (RED 4/5 sem o patch — `line=0` no código antigo) + slice de vizinhança
    re-verificado verde no tip atual. 43 sites restam sem nó-fonte no escopo
    (hosts workflow/makealive/supervisor + helpers só-string) — o resíduo
    rastreado.


  - **`kof-c-compiler` ganha os alvos riscv64/aarch64 (fatia C1 do plano cross)**
    (22/09, frente FFI/kof-c): o compilador C do repositório só emitia x86-64
    (`as --64` + `ld`). Agora `KofCTarget` seleciona os binutils cross
    (`riscv64-linux-gnu-*` / `aarch64-linux-gnu-*`) e o `KofCEmitterBase`
    conduz três emissores por ISA (`KofCEmitterX86` / `KofCEmitterRiscv` /
    `KofCEmitterAarch`); `KofCCompiler.compile(path, out, target)` e
    `kof c --target` expõem isso. O subconjunto inteiro (globais, if/while,
    `&`/deref, aritmética/comparação/shift) é byte-a-byte idêntico em riscv64/
    aarch64 sob qemu e igual ao oráculo x86_64 (`KofCCrossCompilerTest` 7/7);
    a saída x86_64 não muda (`KofCCompilerTest` 7/7). Sem toolchain cross →
    skip honesto. O caminho até a fixture de struct-por-valor continua nas
    fatias C2–C4 (`docs/development/kof-c-cross.md`).

  - **`kof-c-compiler` ganha parâmetros, retorno, locais e chamadas
    (fatia C2 do plano cross)** (22/09, frente FFI/kof-c): funções agora
    recebem até seis argumentos `int` em registrador, declaram locais e
    retornam valor — `int f(int a, int b) { int t; t = a + b; return t; }`.
    Cada função tem frame real (par frame/retorno salvo + um slot de 8 bytes
    por parâmetro/local, com base `rbp`/`s0`/`x29`); os argumentos seguem a
    ABI C (SysV `rdi,rsi,rdx,rcx,r8,r9`, LP64 `a0..a5`, AAPCS64 `x0..x5`) e o
    retorno sai no acumulador, que é o registrador de retorno da ABI em todo
    alvo. As chamadas derramam os argumentos na pilha e os desempilham nos
    registradores, então um argumento posterior pode reusar o acumulador sem
    clobberar o anterior. Chamada desconhecida, aridade errada, `print()` com
    argumentos e mais de seis parâmetros/argumentos falham com diagnóstico
    honesto antes de emitir binário. Prova: `KofCParamsCompilerTest` 7/7 em
    x86_64/riscv64/aarch64 (inclusive os seis registradores de argumento e os
    quatro casos de rejeição). Próximo: fatia C3 (struct por valor),
    `docs/development/kof-c-cross.md`.

  - **`kof-c-compiler` ganha tipos `struct` e parâmetros struct por valor
    (fatia C3 do plano cross)** (22/09, frente FFI/kof-c): `struct S { int a;
    int b; };` com acesso a membro (`v.campo`) e `struct S v;` em
    globais/locais/parâmetros nos três alvos. Campos são **`int` C de 4
    bytes** (casando com `AbiLayout.Scalar.INT`); um struct de até 8 bytes é um
    eightbyte e atravessa em UM registrador inteiro — o mesmo caminho do
    `div_t` da libc — então a fixture que os testes FFI cross precisam (um
    parâmetro struct por valor) passa a ser compilável no repositório em
    riscv64/aarch64. Loads/stores de campo são 32 bits com extensão de sinal
    (`movsxd`/`lw`/`ldursw`). Struct desconhecido, campo desconhecido, campo
    em não-struct e struct maior que 8 bytes falham com diagnóstico honesto
    antes de emitir binário. Prova: `KofCStructCompilerTest` 7/7 em
    x86_64/riscv64/aarch64 mais os quatro casos de rejeição. Falta: retorno de
    struct por valor, structs > 8 B e a classificação multi-eightbyte
    completa; o `int` escalar segue 8 bytes internamente (ints que guardam
    ponteiro) — a largura de 32 bits é aplicada onde a ABI C observa layout de
    memória (campos de struct). Próximo: fatia C4 (`.o` + link),
    `docs/development/kof-c-cross.md`.

  - **`kof-c-compiler` ganha saída de objeto reutilizável e link entre objetos
    (fatia C4 do plano cross)** (22/09, frente FFI/kof-c):
    `KofCCompiler.compileObject(cFile, oFile, target)` monta um `.o` avulso sem
    `_start` e **sem exigir `main`**, emitindo toda função definida como
    `.globl`; os helpers de print só saem quando `print()` é de fato chamado,
    então um objeto que não imprime não carrega globais inúteis para colidir no
    link. `compile(cFile, outDir, target, extraObjects)` acrescenta objetos de
    fixture à linha do `ld`, e funções externas se declaram com protótipo C
    simples (`int f(int a);`, resolvido no link e checado por aridade);
    `kof c -c` expõe o modo objeto. Prova: `KofCObjectCompilerTest` 5/5 — uma
    fixture com struct por valor (`int take(struct Pair p)`) construída como
    objeto em x86_64/riscv64/aarch64 e ligada a um driver que só vê o
    protótipo, rodada sob qemu e imprimindo o golden `42`; objeto sem `main`
    compila e executável sem `main` ainda falha. Este é o caminho que a fixture
    cross FFI de struct-param consome; `docs/development/kof-c-cross.md`.

  - **`extern` `library()` aceita uma fixture `.o` pré-montada nos alvos cross
    (C4-x, link FFI)** (22/09, frente FFI/kof-c): `NativeCrossLink.ffiLinkArg`
    guardava só o basename de uma `library()` de caminho, então um objeto de
    fixture montado para a arch (`compileObject` do `kof-c-compiler`) perdia o
    `.o` e virava `-l:` — nunca era ligado. Agora um `.o` entra posicional na
    linha do `ld`, preservando o path — a mesma forma que o x86-64 já usava.
    Prova: `NativeCrossObjectFixtureE2ETest` 3/3 — um objeto cross montado de
    uma fixture e chamado por um programa Kof compilado via
    `extern "<path>.o" add(Int, Int): Int` imprime `42` sob qemu em
    riscv64/aarch64, mais o caso unitário de mapeamento do `ffiLinkArg` em
    `NativeCrossDynamicLinkTest`. É a metade de link da fixture de struct-param
    (a metade de ABI é a próxima fatia).

  - **§302 FECHADO — tipos crus `List/Set/Map` em CAMPO: todas as faces medidas verdes no tip (21/09, lane bugs-and-gaps, CLOSEALL): repros do registro + formas de campo/retorno/parâmetro/estáticos — `3` nos 3 alvos (raiz já fechada pelo §373/#443).

  - **§443 CORRIGIDO — `extern` escalar cross-target (`library()`) voltou a ser re-gateado para `FFI001` no riscv64/aarch64 pelas fatias de struct-by-value**
    (22/09, lane estabilização/docs 9093): `f670d055`/`aeef88d4` reescreveram
    `CompilerPipeline.nativeExternBound` com `if (driver.target != Target.NATIVE) return false;`
    no topo — `Target.NATIVE` é só x86-64, então `NATIVE_RISCV64`/`NATIVE_AARCH64` caíam em
    `FFI001` em TODO `extern` escalar, invisível em host sem qemu. Fix: a guarda x86-only agora
    cobre só os caminhos de STRUCT; chamada puramente escalar binda em todo alvo nativo
    (`!x86 || x86Bindable`). Prova: `FfiNativeCrossE2ETest` 6/6 sob qemu + novo
    `FfiNativeCrossGateTest` 3/3 (pré-codegen, pega sem toolchain).

  - **§445 CORRIGIDO — cross riscv64: `as -mno-relax` + o split S-5 por função
    (`.section`) ligou `j .L*` cross-range a si mesmo (`j .`); `encoding.base64Encode`/
    `base64Decode` travavam para sempre sob qemu** (22/09, lane gaps-db, achado ao montar
    a toolchain cross para o recon do DB-3): a transformação punha o corpo compartilhado
    do B23 na seção do `...UrlEncode` enquanto o `base64Encode` era um stub `j .Lv_b64_enc`;
    o GAS com `-mno-relax` liga esse salto local à frente **sem relocação** (o objeto mostra
    `j 4` sem `.rela`), então o `--gc-sections` órfã o corpo e o stub salta para si mesmo.
    Fix: removido o `-mno-relax` do `as` riscv — o hazard de gp que ele guardava é
    link-time e o `ld --no-relax` já cobre (medido: 0 instruções gp-relative); o passe de
    sectionização foi para `NativeCrossSections` (movimentação pura, byte-idêntica).
    Prova: `NativeCrossSectionsTest` 4/4 + `NativeRiscv64E2ETest` 54/0F/0E (7,1 s, era hang
    de 187 s) + `NativeAarch64E2ETest` 53/0F/0E + `ArtifactSizeTest` cross verde
    (`hello` riscv 136.512 B, S-5 intacta com 134 seções injetadas).

  - **Retorno de struct por valor agora binda nos alvos cross (riscv64/aarch64),
    register path INTEGER ≤ 16 B** (22/09, frente FFI, D6-1/3.7 fatia 3): os
    emissores LP64/AAPCS64 salvam os words de retorno (`a0`/`a1`; `x0`/`x1` no
    AAPCS64), alocam+inicializam o objeto Kof e extraem cada campo do seu word
    pela largura natural. Provado com a `div` da libc (`div_t { int quot; int rem; }`,
    sem fixture C): `FfiNativeCrossE2ETest` 10/10 sob qemu — riscv == aarch == JVM
    (`3\n1`); float/HFA, > 16 B e o caminho de param struct seguem FFI001 honesto (R6).


  - **§268 CORRIGIDO — classe de usuário `extends <classe do JDK>` por nome simples gravava superclasse CRUA (`NoClassDefFoundError` no load)**
    (22/09, lane 9093, voto da mantenedora `D-RULE6-BATCH` opção (A)): `class Worker extends Thread`,
    `extends Object` e `implements Runnable` (sem import) compilavam limpos e a classe morria no
    load (`NoClassDefFoundError: Thread`/`Runnable`) — o super/interfaces crus entravam no class
    file. Fix: probe de `java.lang` com cache (`JavaLangProbe`) qualifica nomes simples de
    `extends`/`implements` sem import; o import explícito/`--classpath` e os tipos do módulo
    mantêm precedência; o que NADA resolve (`IOException` sem import, `Zebra`) vira erro de
    COMPILE **SEM087** agora (`DeclaredTypeChecker`, R6); o lowering JVM passa a rotear a lista
    de interfaces pelo mesmo resolvel (a segunda face do bug). Aliases de wrapper (`Boolean`,
    `Long`, `Double`, …) nunca passam pelo probe — o `Type.of`/builtins decidem primeiro (um
    rascunho que os probara virava `save(): Boolean` em `java.lang.Boolean` e foi pego pela
    bateria de vizinhos). Split regra 7: `HeritageQualifier` + `JavaLangProbe` (`CompilerTypes`/
    `MemberResolver` de volta sob a linha crítica do `check_500`). Prova: `JavaLangHeritageTest`
    **9/9** (RED medido com o fix stashed: 6 falhas — 3 load-crashes + 2 accepts silenciosos +
    1 diagnóstico multi-target) + 96/0F em 16 classes vizinhas.

  - **§288 CORRIGIDA — type-variable DENTRO de assinatura de FUNÇÃO (`mapItems(f: (T) -> T`) era cega ao escopo em duas camadas: SEM014 "expected 'function' but got 'function'" no checker e `Function1_CT_CT`/`LT;` fantasma no emit (crash no load)**
    (22/09, lane 9093, voto da mantenedora `D-RULE6-BATCH` opção (b)): o verbatim do #396
    (`Pipeline<T>.mapItems(transform: (T) -> T)`) compilava com SEM014 auto-contraditório — ou,
    com o checker meia-corrigido, morria no load com referência a classe fantasma. Fix, o
    contrato exato da decisão: (1) `TypeVariable` em parse-time com FONTE ÚNICA de verdade —
    `TypeParams.rewrite` agora recursa em `FunctionType` (params+retorno, aninhado) e
    `WildcardType` (bound), o único ponto por onde passam o checker (`resolveType`) e o
    lowering (`resolveWithTypeParams`), então a interface sintética apaga para
    `Function1_O_O` e o descritor `LT;` some; `isAssignable` ganhou a regra fn×fn por
    componente (erasure); `CompilerLambdaClass` preserva retorno `TypeVariable` no round-trip
    por string. (2) Rejeição interina **SEM085** (R6): medido com o fix de fonte única só, a
    forma do #396 compila limpa e ainda morre no load (`IncompatibleClassChangeError` — a
    lambda do call site é sintetizada contra o próprio `Function1_int_int` concreto enquanto
    o dispatch usa o declarado apagado); a síntese contextual da lambda contra a assinatura
    apagada é a ABI de erasure completa = trabalho da linha 1.0 (§271(B)), então até lá todo
    tipo declarado cuja FUNÇÃO carregue type-param do dono (`(T) -> T`, `(Int) -> T`,
    `List<(T) -> T>`) é rejeitado no compile em todo target. As formas `T` sem função
    (`T value`, `Pipeline<T>`, `List<T>`) seguem legais. Prova: `FnTypeVarSignatureE2ETest`
    **9/9** — verbatim → SEM085 (não SEM014, sem crash) JVM+Native, `useTwice`/função-
    genérica/`(Int) -> T`/`List<(T) -> T>` → SEM085, controles: `T get(): T` puro paridade
    byte-a-byte 4-alvos, `(Int) -> Int` concreto (sem falso-positivo), `List<T>`-sem-função em
    compile (sem falso-positivo), `Zebra` em fn-type → SEM011. Achado lateral catalogado
    (pré-existente, medido em origem limpa): **§444** — classe genérica com constructor de
    ARG T roda em silêncio no Native (roteada para a lane nat).

  - **§418 CORRIGIDO — o harness riscv64 do `kof debug`/E2E podia deixar o qemu vivo após a rodada**
    (21/09, lane nat/native-debug, handoff D-CLOSEALL-BATCH): o `NativeRiscv64E2ETest` chamava
    `waitFor()` sem nenhum `destroy()`/`finally`, então um qemu morto por timeout sobrevivia e o
    temp dir (e a rodada seguinte) herdava o cadáver. Fix: novo helper `runBounded` (wait bounded
    de 180s, `destroyForcibly()` no estouro + reap bounded, kill no interrupt e no `finally`)
    aplicado no `runQemu`, no `runRiscv64` e no sítio de heap-exhaustion. Prova:
    `hangingChildIsKilledByTheBoundedWait` (filho pendurado morre no bound de 1s) +
    `NativeRiscv64E2ETest` 54/0F + as 9 classes vizinhas que chamam `runQemu` 178/0F/0E.

  - **§441 CORRIGIDO — `Map` com chave larga (`Long`/`Double`) gerava bytecode JVM inválido**
    (21/09, lane compilador): `mapOf(1.5, "a")`, `mapOf(1L, "x")` (e `put`/`putIfAbsent`
    com chave larga) passavam limpos no type-check e rodavam em Script/JS, mas a classe
    JVM não carregava com `VerifyError: Bad type on operand stack ... swap` — o emitter
    fazia `SWAP` sobre valor de categoria 2 para boxear a chave. A CLI mascarava como a
    mensagem do JavaFX. Fix: boxear a chave sem `SWAP` para chaves largas (local de
    rascunho por método); emissão de Map extraída para `JvmOpMap` pelo gate ≤500. Prova:
    `KofMapSetTest.mapWithWideKeyJvm` + `mapWithWideKeyParityJvmJs`, classe 16/16.

  - **§188 fixed — `"2026" as Int` agora rejeita no cheque (SEM100) apontando `math.parseInt`**
    (21/09, lane bugs-and-gaps, voto (A) da mantenedora; prova `StringAsParseRejectTest`
    4/4, RED 3/3 no código antigo): `as` = cast, nunca parse implícito.


  - **§400 fixed — função nomeada como valor agora diagnostica a regra real**
    (21/09, lane bugs-and-gaps, voto (A) da mantenedora): `print(probe)` agora
    diz "`probe` is a top-level function, not a value in argument position —
    pass the call wrapped in a lambda: () -> probe()" (SEM011 com posição real;
    `NamedFunctionValueDiagnosticE2ETest` 4/4, RED 2/2 no código antigo).


  - **§334 CLOSED — `kof_box_equals` NaN (batch CLOSEALL da mantenedora 21/09):** probe
    medido nos 2 alvos — `0.0 / 0.0` imprime `NaN` (ARITH001 só pega INT), a face
    box-vs-box imprime `NaN / false / false / false` IDÊNTICO JVM==native; a
    divergência de bits só exigiria dois NaN payloads exóticos (inalcançável do
    fonte hoje) → informativo, guard (b) pronto se um produtor exótico chegar.


  - **§440 CORRIGIDO — `record.x++`/`--` passava no `check` e morria em runtime**
    (21/09, lane compilador): `p.x++` em componente de record passava limpo no
    type-check mas o `kof run` morria com `IllegalAccessError: ... access
    private field P.x`. A escrita direta `p.x = 9` e a composta `p.x += 1` já
    davam `SEM038` ("record is immutable"); só o caminho de incremento/decremento
    (tipado no `SemExpressionTyper`, rebaixado no `CompilerEmission2.emitIncrement`)
    escapava. Fix: `SEM038` para `++`/`--` em componente de record (receiver
    explícito ou `this`, construtor excluído), mesmo contrato da atribuição.
    Prova: `CompilerDriverTest.incrementRecordComponentGivesSem038` +
    `decrementRecordComponentViaThisGivesSem038` + controle positivo de classe
    mutável, classe 262/262.

  - **§439 CORRIGIDO — switch sobre subject largo com case literal `Int` quebrava
    o backend JVM** (21/09, lane compilador): `switch (var x: Long = 3) { case
    1: ... }` passava no type-check mas o backend JVM estourava com
    `NegativeArraySizeException` no `COMPUTE_FRAMES` do ASM (mascarado pelo
    launcher JavaFX). O valor do case era rebaixado com o próprio tipo (um `1`
    nu é `Int`) enquanto o subject é largo, então `KofBinary(EQ, long)`/`DCMP`
    rodava sobre pilha de largura mista. Fix: promover o valor do case ao tipo
    do subject antes da comparação (`emitWideningIfNeeded`) no
    `SwitchStmtLowerer` (ramos numérico e pattern) e no `SwitchExprLowerer` —
    só IR (regra 5), sem mudança de parser/typer/backend. Prova:
    `SwitchLongDoubleSupportE2ETest.intLiteralCasesWidenToLongSubject` +
    `intLiteralCasesWidenCrossTargetParity` (JVM == Script == JS == Native
    x86-64, golden `three\nother`), classe 5/5, RED→GREEN.

  - **§438 CORRIGIDO — `kof debug` não vaza mais a JVM debuggee / dir temporário**
    (21/09, lane .18): o `KofDebugJvmSession` não tinha shutdown hook (ao
    contrário do `KofDebugNativeDap`), então SIGTERM/fechar o editor matava o
    CLI sem matar o debuggee `-agentlib:jdwp=...,suspend=y` lançado nem apagar
    o `/tmp/kof-debug-*`; suítes repetidas acumulavam órfãos até o tmpfs morrer
    (`Cota da disco excedida`). Adicionado `addShutdownHook(this::cleanupOnExit)`
    no `run()` (destrói o `jvmProcess` lançado, nunca um alvo ATTACH, e roda o
    `cleanup()` existente). O teardown dos testes agora passa pelo novo
    `CliProcessTree.terminate(Process)` (SIGTERM primeiro para o hook rodar,
    depois mata à força o CLI + descendentes sobreviventes), substituindo o
    `p.destroy()` cru nos testes de debug. RED-first
    `CliDebugProcessLeakTest.killingTheSessionLeavesNoOrphanDebuggeeNorTempDir`
    (RED medido com o hook desabilitado: o dir `kof-debug-*` vazou).
    Cluster `CliDebugProcessLeakTest`+`KofDebug*`+`ServePortTest` 27/0F.
    Contagem viva 15→14.

  - **§437 CORRIGIDO — `check_500` vermelho em `JvmOpCollections` fechado (604→594)**
    (21/09, lane .18): as ``+20`` linhas vieram do fix §432 **duplicado**
    (`c6a8520d`), cujo bloco extra em `emitMapCall` era código morto
    (`argValueType` nunca atribuído, o ramo `Unknown` sempre-falso,
    `boxValueType` nunca lido). O `a9bbdfc5` já havia removido essas 10 linhas
    (regra 3), deixando o arquivo em 594 (< 600): `check_500` rc=0 e `wc -l`
    = 594, sem split. Comportamento inalterado (o resultado segue vindo do
    `writtenValueType`); prova `MapGetOrDefaultTest` 7/7 +
    `CollectionMethodsStdlibE2ETest` 11/11 + `NativeErasureBoxE2ETest` 6/6.
    Entrada do ledger fechada (o baseline fica em 584; ≥ 600 nunca é
    legitimado). Contagem viva 16→15.

  - **§424 CORRIGIDO — cinco métodos `String` recusam no JS/Native com `STR003`**
    (21/09, lane .18): `matches`/`replaceAll`/`replaceFirst`/`toCharArray`/
    `compareToIgnoreCase` são aceitos pelo typer e implementados no JVM, mas o
    JS emitia uma chamada direta `receiver.<m>(...)` (membro inexistente de
    `String.prototype` -> `TypeError` em runtime; `replaceAll` era literal vs
    a regex do Kof/JVM) e o Native transformava a chamada em
    `java_lang_String_<m>` (falha críptica de `ld`). Novo `StringTargetGaps`
    (fonte única da verdade) é consultado no topo do ramo `String` em
    `ExpressionInstanceCallLowerer`; em `Target.JS`/qualquer `isNative()` ele
    emite o `STR003` honesto e aborta o lowering, de modo que nada errado é
    emitido. JVM intacto. `StringGapMeasuredTest` virado de caracterização
    para correção (JS e Native agora FALHAM em compile com `STR003`). Docs:
    `backend-parity.md` Documented Gaps. Prova:
    `DomainGapCodesTest.stringIncompleteMethodsOnJsAndNativeAreStr003`
    + `stringIncompleteOnJvmHasNoGap` (24/24, incl. o guarda de matriz R6),
    `StdParityGapAuditTest` 16/16.

  - **§427 CORRIGIDO — `kof.io`/web-T1 no riscv64/aarch64 agora recusam com `NAT006`/`NAT007`**
    (21/09, lane .18): o cross baixava File/Path/Directory (`kof_io_file_*`)
    e o servidor T1 (`kof_web_listen`/`route`) cujos símbolos só existem no
    runtime x86_64 -> `ld` undefined-reference alto. `lowerIo` recusa
    File/Path/Directory no cross com `NAT006` e `lowerWeb` recusa o servidor
    T1 com `NAT007` (`nativeWebT1` agora só x86); x86_64/JVM/JS mantêm os
    runtimes reais. Docs: backend-parity.md Documented Gaps + per-arch.
    Prova: DomainGapCodesTest.ioOnCrossIsNat006 + webT1OnCrossIsNat007 +
    ioAndWebT1OnX86AndJsHaveNoGap (22/22, incl. o guarda R6), IoE2ETest
    24/24, KofWebNativeE2ETest 4/4, KofWebE2ETest 25/25.

  - **§426 CORRIGIDO — `time.collect()` no JS recusa com `TIME004`**
    (21/09, lane .18): o backend JS registrava/importava `kofGcCollectNow`
    de `kof-runtime.mjs` mas o runtime nunca o exportava (o alvo JS não tem
    GC manual), então `time.collect()` compilava limpo e falhava no load do
    módulo. `KofTime.supportedOn("collect", JS)` agora é false e
    `gapCode("collect")` devolve `TIME004` -> recusa honesta em
    compile-time; JVM/SCRIPT/x86/riscv64/aarch64 mantêm o mark-sweep real.
    Docs (`backend-parity.md` linha kof.time + Documented Gaps). Prova:
    `DomainGapCodesTest.collectOnJsIsTime004` + `collectOnJvmAndX86HasNoGap`
    (19/19, incl. o guarda R6 da matriz), `KofTimeE2ETest` 42/42.

  - **§425 CORRIGIDO — `kof.config` no riscv64/aarch64 agora recusa com `CONF001`**
    (21/09, lane .18): o asm cross não tinha runtime de lookup `kof_config_*`
    (os stubs ecoavam o argumento default / `0` / `false`) enquanto
    `KofConfig.supportedOn` retornava `true` para todo alvo, deixando o ramo
    `CONF001` morto — valores errados silenciosos no cross. `supportedOn` agora
    é false para `NATIVE_RISCV64`/`NATIVE_AARCH64`, então
    `ExpressionConfigCallLowerer` emite o `CONF001` honesto em compile-time;
    JVM/Native x86_64/JS mantêm a implementação real. Javadoc e a matriz de
    docs (`stdlib-config.md`+PT, `backend-parity.md`) corrigidos. Prova:
    `DomainGapCodesTest.configOnCrossIsConf001` + `configOnJsAndX86HasNoGap`
    (17/17, incl. o guarda R6 da matriz), `NativeConfigE2ETest` 8/8.

  - **§428 CORRIGIDO — requests não implementadas do DAP agora falham honestamente**
    (21/09, lane .18): as sessões DAP JVM e Native respondiam toda request não
    tratada com `success:true` e corpo vazio (fachada silenciosa, Q7). Os dois
    ramos `default` agora usam o helper honesto que todo outro ramo
    não-atendível já usava — `KofDebugJvmSession` `fail2(...)` e
    `KofDebugNativeDap` `fail(...)` com `unsupported request: <command>`.
    `restart` documentado como limite (EN+PT). Prova (Q0 RED→GREEN):
    `KofDebugNativeDapTest.unimplementedRequestsFailHonestlyInsteadOfSilentSuccess`
    e `KofDebugJvmTest.unimplementedRequestFailsHonestlyNotSilentSuccess` falham
    no antigo `success:true,"body":{}`; cluster debug 24/0F.

  - **§436 CORRIGIDO — `StdCatalog` sem `secrets.of`/`secrets.secret`**
    (21/09, sessão 9093): o pouso da D-SECRETS face 1 (`32285136`) adicionou os
    braços `secrets.of`/`secrets.secret` do dispatcher (nominal `Secret`) e a
    lista de membros do namespace, mas não as assinaturas no `StdCatalog` — o
    ratchet `StdCatalogSignaturesTest.fatiaSix…` então falhou no tip
    (`tabela sem secrets.of`). Adicionadas as duas entradas de catálogo para o
    `signatureHelp`/completion do LSP casar com o dispatcher. Prova:
    `StdCatalogSignaturesTest` 12/12, `StdCatalogTest` 11/11,
    `StdlibIdiomsCompileTest` 20/20, `LspServerTest` 38/38.

  - **§432 CORRIGIDO — `VerifyError` do JVM em `Map<_,Object>.getOrDefault(k, <primitivo>)`**
    (21/09, sessão 9093): o `JvmOpCollections.emitMapCall` resolvia o V do owner
    e o sobrescrevia com `parameterTypes[1]` (o arg escrito), então num mapa de
    valor Object o caminho do resultado tratava o valor como `Double`
    (box/checkcast/unbox) enquanto o lowerer o tipava `Object` e o printer
    chamava `String.valueOf(Object)` → `VerifyError: Bad type on operand stack`.
    O emissor agora mantém o **V do owner para o RESULTADO** e um
    **`writtenValueType`** separado para boxar o valor/default escrito, caindo no
    tipo do argumento só quando o V do owner é Unknown (mapas nascidos de
    `mapOf()`). Também conserta o `checkcast` do retorno do `put` (mesma
    família). Prova: `MapGetOrDefaultTest.objectValuedMapGetOrDefaultRunsOnJvm`
    (hit `2.5`, default primitivo `9.5` em miss, exit 0 — RED-first com o
    launcher/VerifyError antes do fix); a classe 6/6,
    `CollectionMethodsStdlibE2ETest` 11/11, `NativeErasureBoxE2ETest` 6/6.

  - **§433 / §434 CORRIGIDOS — os dois vermelhos do tip remoto da FFI/JS**
    (21/09, lane FFI/JS `29198ea8`): §433
    `ArtifactSizeTest.helloJsRuntimeSizeWithinBaseline` passava do orçamento JS
    de 5% — re-baselinado COM causa (precedente §166): `HELLO_JS_BYTES`
    13.007→13.834 KB pelo slice `kof.buffer` + helpers de marshal/bridge JS. §434
    `StdParityGapAuditTest.bufferGatesToJvmWithFfiCodes` esperava o JS na lista
    auditada dos gates de Buffer — `KofBuffer.gapCode` não devolve mais o
    `FFI002` do JS (Buffer binda no JS desde R57/R58) e o teste tira o JS do
    conjunto gateado. Ambos catalogados pela unidade F2d1 da gaps-db em
    `2995d0f8` e corrigidos no mesmo dia.

  - **§430 CORRIGIDO — testes false-green** (21/09, lane docs/plataforma): o
    `RouterE2ETest.debugConc001` sem assert virou teste real, os cinco
    `NativeDebugTest*.java` só-print foram deletados, o `KofWebNativeE2ETest`
    agora envia um `GET /` real e asserta 200, e o
    `BareCollectionPrimitiveArgE2ETest` gateia em `NativeToolchainGate.present()`
    para uma regressão do emissor nativo FALHAR em vez de skipar.

  - **§435 CORRIGIDO + ponto cego do gate de âncoras fechado** (21/09, lane
    `.18`): extraídos os envelopes JSON-RPC do `LspServer` para a classe irmã
    `LspJsonRpc` (regra 7), levando o arquivo 601 → **582** (`check_500` verde)
    depois que o fix §429 o empurrou além do crítico 600; comportamento
    preservado (`LspServerTest` 38/38). Ao fechar, achou-se o
    `scripts/check_ledger_anchors.sh` **cego a hrefs de hífen único** — o regex
    exigia `NNN--`, então um `#435-gate` malformado (o slug real é
    `#435--gate`) nunca era validado; 14 cross-links EN↔PT malformados
    (§424–§428, §431, §435) passavam verdes desde sempre. O regex agora casa
    `NNN-` e o `--selftest` planta um fixture de hífen único; os 14 hrefs foram
    reescritos pelo `gh_slug` exato do heading de destino. Contagem viva 21→20.

  - **Estabilidade — ratchets da FFI JS reconciliados após o fecho da superfície
    JS** (21/09): a suíte completa (3472 testes) revelou 3 ratchets stale
    deixados pelas fatias do bridge D6/JS — ainda afirmavam o estado pré-bridge,
    e as baterias FFI dirigidas sozinhas não pegaram. Corrigido:
    `KofBuffer.gapCode` não devolve mais o `FFI002` específico do JS (Buffer
    binda no JS desde R57/R58), então `StdParityGapAuditTest` tira o JS do
    conjunto gateado; `InteropIdiomsCompileTest.jsShapeExamplesStayHonest` →
    `…BindByValue` (extern record/array/out-buffer agora compilam no JS);
    `ArtifactSizeTest` `HELLO_JS_BYTES` re-medido 13.007→13.834 KB (o slice
    `kof.buffer` + os helpers de marshal/bridge JS). `training/idioms/
    interop.pt_BR.md` (+EN) reconciliado: o JS binda as formas D6, só o Native
    mantém o gap honesto `FFI001`. Prova: 64/64 dirigido (bateria FFI + as 3
    classes) e as 3 falhas da suíte eliminadas.

  - **§431 CORRIGIDO — drift de tooling da auditoria profunda** (21/09, lane
    `.18`): apagados o `serveStatic`+`contentType` mortos em `KofCliSupport` (e
    o ilusório `ServeStaticTest`) — o mecanismo app-level `serveDir` é dono dos
    estáticos full-stack desde o F3-step-2b; removido o ramo inalcançável de
    attach do DAP em `KofDebugNativeDap.launch` (`attachPid` já é tratado UMA vez
    em `run()`, então `launch` é no-op ali); o `Compare` agora retorna 1 em
    QUALQUER opção desconhecida, em vez de imprimir e possivelmente sair com 0
    (prova `CompareTest.unknownOptionIsFatalNotSilentlyRun`); `KofDebug`
    re-triado NÃO é bug — o chain externo já retorna 1 para flags desconhecidas e
    posicionais extras. Também reparados os cross-anchors EN↔PT do §421
    pré-existentes (o fix S0 `b1a5373b` mudou o slug do heading mas não os
    hrefs). Contagem viva 23→22; `check_ledger_anchors` 0 quebradas.

  - **FFI: retorno de `record` por valor no target JS (bridge, D6-1/3.8b fatia
    2)** (21/09): o gate de compilação admite retorno de struct no JS e a
    assinatura carrega o layout (`@<n><chars>` no índice 0); o `KofJsFfiBridge`
    materializa o struct por valor na arena da chamada (o Linker recebe a arena
    como `SegmentAllocator` à frente) e lê os campos num `Object[]`, e o novo
    `__kof_ffi_from` estático do record reconstrói a instância pelo construtor
    canônico (coagindo `Long`→`BigInt`, `bool`→`Boolean`, `Number` no resto) —
    paridade com o `kof_ffi_read_struct` reflexivo. Prova:
    `structReturnByValueJsParity` (`Point`/`Big`/`Mix`/`ParamMix`, caminhos
    registrador e sret, incl. um `Long`) byte-a-byte JVM==JS. `FfiStructE2ETest`
    10/10; bateria FFI 97 run/0F. A superfície de param+retorno do FFI no JS
    agora está completa; o único gap D6 restante é o Native (3.7).

  - **FFI: `Buffer(U8)` INOUT no target JS (bridge, D6-3/3.8b fatia 4)**
    (21/09): `extern` com param `Buffer(U8)` agora binda no JS — o gate de
    compilação admite e o `KofJsFfiMarshal.packBuffer` copia os bytes do
    `Uint8Array` do guest para a arena da chamada (token `B` → `ADDRESS`), com o
    copy-back após o downcall devolvendo o resultado do C ao buffer do guest
    (paridade com `kof_ffi_buffer_in`/`_out`). Prova:
    `bufferInoutCopyInCopyBackJsParity` (`20/[10, 10]/40/[20, 20]`, o +10
    acumulando entre chamadas) byte-a-byte JVM==JS. O
    `FfiE2ETest.jsUnboundAbiEmitsFfi002` agora usa `String[]` como superfície
    ainda honesta de não-bind. Resta no JS: **retorno** de struct (`FFI002`).

  - **`kof.buffer` no target JS (`Buffer(U8)`, D-R3-BUFFER)** (21/09): o
    namespace `kof.buffer` agora binda também no JS — `buffer.alloc(Int)` e
    `Buffer.bytes()`, com o mesmo contrato do JVM (`KofRuntime$Buffer`):
    zero-filled, tamanho negativo clampa para 0, `bytes()` materializa um
    `Byte[]`. Novo slice `JsRuntimeBuffer` (bloco de runtime alcançável pela
    poda) + roteamento `kof_buffer_*` no `JsRuntimeOps`; `KofBuffer.supportedOn`
    agora cobre JVM+JS (Native segue `FFI001`). Prova:
    `BufferE2ETest.allocAndBytesJsParity` byte-a-byte JVM==JS
    (`Buffer[4]`/`[0, 0, 0, 0]`/`Buffer[0]`). O out-buffer INOUT do FFI (token
    `B`) é slice separado e segue `FFI002` no JS.

  - **FFI: array escalar `T[]`→`ptr` no target JS (bridge, D6-2/3.8b fatia 3)**
    (21/09): o runner JS agora binda `extern` com parâmetro de array escalar — o
    gate de compilação admite `T[]` no JS e o `KofJsFfiMarshal.packArray` lê o
    array JS do guest e copia os elementos para a arena da chamada (mesma
    semântica de **copy-in por chamada** do JVM; o C não escreve de volta — isso
    é o `Buffer(U8)` da D6-3). Prova: `arrayParamByValueJsParity` — `sumn=6`,
    `sumd=4.0` e `fill` provando não-aliasamento (`11/11/5`) byte-a-byte
    JVM==JS; `stringArrayStaysFfi001`/`arrayParamNativeStaysFfi001` inalterados;
    os antigos `arrayParamJsStaysFfi002`/`jsUnboundAbiEmitsFfi002` agora usam
    `Buffer`/`String[]` como superfície ainda honesta de não-bind.
    `FfiArrayE2ETest` 5/5, `FfiE2ETest` 17/17. Restam no JS: **retorno** de
    struct + `Buffer` (`FFI002`).

  - **sync da contagem viva 16→24 + reparo da âncora do §423** (21/09, lane
    docs/.18): a passada 5 da frente de revisão catalogou §424–§431, então a
    narrativa/linha da fila no README (contagem viva de `known-bugs.md`) e a
    condição 7 da prep do release agora leem **24 live** (a autoridade segue
    sendo `check_known_bugs_status.sh`); e os cross-links do §423 apontavam para
    slugs de headings pré-reescrita — os dois hrefs (EN→PT/PT→EN) agora batem com
    o `gh_slug` exato dos headings atuais (`check_ledger_anchors` 0 quebradas;
    `check_live_records` OK). Só docs.

  - **FFI: `record` por valor como ARGUMENTO no target JS (bridge, D6-1/3.8b)**
    (21/09, lane docs/.18, frente FFI): o runner JS só compartilhava a ABI
    escalar/callbacks — struct como parâmetro ficava `FFI002`. O host não
    consegue refletir `RecordComponent` de um objeto GraalJS, então o token do
    fio agora carrega o layout dos campos (`@<n><chars>`, com prefixo de tamanho
    para não ambiguar com o escalar seguinte), cada record JS expõe
    `__kof_ffi_fields()` na ordem de declaração, e o `KofJsFfiMarshal` empacota o
    `StructLayout` — mesmos offsets e padding de cauda do
    `kof_ffi_struct_layout_of` do JVM — na arena da chamada (D6-5). Prova:
    `structParamByValueJsParity` com shim C real — `sumpoint(Point(3,4))=7`,
    `scale(Point(2,3),2.0)=10.0`, `parammix(ParamMix(3,2.5,4))=9.5` (layout j/d/i)
    byte-a-byte JVM==JS. Retorno de struct e array/buffer no JS seguem `FFI002`
    (nenhum binding parcial silencioso). Bateria FFI: 118 run, 0F.

  - **portão de release: `decisions` contava um heading combinado `D-A / D-B`
    duas vezes** (21/09, lane docs/.18): o `check_release_050_gate.sh` lia `$2` de
    cada heading `## `, então `## D-TYPE-VARIANCE / D-INTEROP-REFLECT` reportava
    D-TYPE-VARIANCE **2×** e omitia o segundo id — o portão dizia "3 State: OPEN"
    quando são **2** decisões. Agora extrai todo token `D-*` do heading e
    deduplica (rodada real: "2 State: OPEN ... D-INTEROP-REFLECT D-TYPE-VARIANCE").
    Fixture de regressão adicionada ao selftest do portão. Só tooling.

  - **`audit-stubs.sh` v2: método estrutural (ordem da mantenedora)** (21/09,
    lane docs/.18, apoio à frente de revisão 9094 reaberta): a frente pediu
    varredura **estrutural**, não marcadores. A v2 adiciona
    quatro detectores que não dependem de `TODO` algum — (7) métodos concretos
    cujo corpo é só um `return null/false/0/""` trivial (facade), (9) corpos de
    `catch` só com comentários (absorve em silêncio), (10) `throw new
    UnsupportedOperationException` sem código de gap — mais `--section N` para
    rodar uma fatia reprodutível. Primeira medição: **2 facades, 19 catches
    só-comentário, 1 hard-fail sem código** (candidatos, não catalogados — a
    triagem é a fatia da frente). O teste de fixture agora cobre cada detector e
    o isolamento de `--section`. Só docs/tooling.

  - **novo gate `check_doc_refs.sh`: refs `docs/*.md` quebradas + SHAs fantasma
    nos docs da lane** (21/09, lane docs/.18): mecaniza as duas classes de drift
    achadas à mão hoje — um doc movido deixando o path antigo para trás
    (native-multiarch, language/types) e um SHA de prova sem objeto (o repair de
    git de 21/09 reescreveu o histórico, orfanando 11 citações). Varre todo
    `docs/**/*.md` por refs de path (remove URLs) e a lane `docs/development` por
    SHAs hex 8-40 entre crases, exigindo que cada um resolva (`git cat-file -e`).
    Ampliar o scan de path para o corpus inteiro (21/09) revelou 16 refs quebradas
    a mais; as citações de docs movidos foram retargetadas (workflow-plan,
    known-bugs, ecosystem-coverage, CONFORMANCE_MATRIX e 7 modelos
    `future/runtime/*` — EN+PT). Os paths conhecidos + 11 SHAs restantes são
    waivers explícitos e datados em `scripts/doc-refs-waivers.txt`. Selftest
    RED-first + `scripts/tests/doc-refs-test.sh` ligados na suíte de agentes.
    Rodada real hoje: 551 refs de path (docs/) + 247 SHAs conferidos. Só
    docs/tooling.

  - **condições 2/3 da prep de release de-staled + dois caminhos de doc quebrados
    corrigidos** (21/09, lane docs/.18): a condição 2 da prep dizia GREEN, mas a
    condição 2 de `D-RELEASE-0.5.0-GATE` da mantenedora a fixa como NEEDS-REVIEW
    (3 frentes aprovadas `State: OPEN`: X5/X6/secrets) — nunca RED, nunca "nada
    espera"; e a condição 3 ("3 docs com dono") estava stale após as promoções de
    21/09 (agora 6: +`db-parity-plan`, `secrets-plan`,
    `type-system-extensions-plan`). Também corrigidos dois caminhos quebrados:
    `docs/development/native-multiarch.md` -> `docs/native-multiarch.md` (tracker
    1.2.2) e `docs/language/types.md` -> `docs/language-reference/types.md`
    (roadmap 2.6.7 + DECISIONS PT). Só docs.

  - **linhas do tracker sincronizadas com as decisões de 21/09 (superfície X5/X6,
    5.1 Secrets)** (21/09, lane docs/.18): a linha X5 de
    `IMPLEMENTATION-UNIVERSAL-PLATFORM.md` (+PT) não trazia a aprovação nem o
    congelamento `D-X5-SURFACE` (superfície v1: `out`/`in`, `sealed` class/record
    + interface, projeção no sítio de uso na v1, `SEM0xx`); X6 não trazia a
    aprovação; a linha 5.1 (tipo `Secret`) seguia 🔵 sem `D-SECRETS` embora a face
    1 esteja autorizada (a linha 3.6 já a carregava) — agora 🟡 com a decisão.
    Só aditivo; a prosa do mantenedor é preservada. Só docs.

  - **2.2.3 concluído: opção B verificada implementada + assessment movido para
    docs/architecture/** (21/09, lane docs/.18): os quatro desugars de fonte já
    rodam pelo registry `DesugarStep` de AST (`85779f20`: `DesugarSteps.defaults()`
    registra os quatro + `CompilerPipeline:303`; `DesugarStepPipelineTest` 7/7;
    `CompilerDesugar` sem chamadas diretas restantes), mas a linha do roadmap e o
    doc de assessment ainda diziam "DECIDIDO / fatias abertas". Marcados roadmap
    2.2.3 ✅ IMPLEMENTADO, linha §1 do README CONCLUÍDO+MOVIDO, e movido
    `codegen-step-2.2.3-assessment.md` (+PT) para `docs/architecture/` (regra dos
    3 estados) com seção Resolução. Condição 3 do gate de release `loose_docs`
    7 -> 6. Só docs.

  - **plano do sistema de tipos: fatia X5.4 corrigida para v1 (+ reversão da minha
    reescrita do topo)** (21/09, lane docs/.18): o plano já carrega a seção
    "Surface decisions (RESOLVED)" da mantenedora (`b94e2975`); meu commit anterior
    havia reescrito o topo histórico do plano (estado/heading/perguntas),
    duplicando-a e quebrando a referência "cabeçalho acima é histórico" —
    revertido ao topo histórico. Mantida a correção real: a linha da fatia X5.4
    dizia "padrão: adiada", contrário a `D-X5-SURFACE` (c) (projeção no sítio de
    uso é v1); X5.0 e X6.0 agora mostram ✅ e `D-X5-SURFACE` consta em Evidência.
    Só docs.

  - **lane docs registra as decisões de 21/09 da mantenedora dentro dos planos**
    (21/09, lane docs/.18): três planos contradiziam as decisões travadas nas
    próprias linhas de estado. `type-system-extensions-plan.md` (+PT) dizia
    "RASCUNHO para revisão" / "PARA REVISÃO" com quatro perguntas abertas — agora
    registra a aprovação (X5/X6) e o congelamento `D-X5-SURFACE` (`out`/`in`,
    `sealed` class/record + interface, projeção no sítio de uso na v1, `SEM0xx`);
    `ffi-abi-structs.md` (+PT) acrescenta D6-1 **B** aprovado spec-first
    (`D-FFI-STRUCT-B`, regra 11, ainda sem diff de parser/typer); `secrets-plan.md`
    (+PT) registra a promoção + face 1 (`Secret`) autorizada (`D-SECRETS`). Linha
    D6-1 da §1 do README corrigida para A+B (B spec-first). Sem mudança de código
    de produção.

  - **`check_live_records.sh` parte I: a fila oficial (§1) tem de nomear todo loose
    doc + README §1 sincronizado com as decisões da mantenedora de 21/09** (21/09,
    lane docs/.18): um doc promovido para `docs/development/` mas ausente da fila
    §1 é trabalho invisível — a §0 pode listá-lo como pendente enquanto ninguém o
    posiciona. Achado `type-system-extensions-plan.md` (promovido 21/09) na §0 mas
    fora da §1 em EN+PT. Sincronizadas as linhas da fila com as decisões
    (`D-DESUGAR-STEP` DECIDIDA opção B; `D-TYPE-VARIANCE`/`D-INTEROP-REFLECT`
    ABERTO-implementando; `D-SECRETS` promovido; `D-FFI-STRUCT-B` D6-1=B
    spec-first; `D-DB-PARITY-OWNER` dono nomeado) e mecanizada a guarda com
    selftest RED-first (loose fora da §1 PT, §1 ausente). Sem mudança de código de
    produção.

  - **tooling — `scripts/audit-stubs.sh` ganha teste RED-first de fixture +
    wiring na suíte** (21/09, lane docs/.18): a frente de revisão (lane 9094)
    entregou o inventário de stubs sem teste nem entrada na suíte.
    `scripts/tests/audit-stubs-test.sh` planta um `TODO`/`catch` vazio/`@Disabled`
    e exige que sejam achados, fixture limpa dando 0 (anti-falso-positivo — o
    ruído PT `todo`=`todos`), a árvore inalterada (somente leitura) e raiz sem
    `*/src/main` recusando; ligado em `scripts/tests/run-agent-tests.sh` (suíte
    VERDE). Sem mudança de código de produção.

  - **release 0.5.0 condição 1 re-certificada GREEN** (21/09, lane docs/.18): o
    jar da árvore estava velho vs a fonte após os pousos do dia (regra de
    re-envelhecimento da própria condição, condição 6 no corte). Recuperado com
    `scripts/build-kof-jar.sh` (reconstrução + estampa por conteúdo) e re-medido
    com `scripts/target-matrix.sh` sob o env do
    `scripts/setup-cross-toolchain.sh --export` — `PARITY: 100%`
    (jvm/x86_64/riscv64/aarch64/js/script byte-a-byte vs o oráculo JVM;
    kofc=EG-9, android=EG-10 delegados). Condição 1 da prep atualizada EN+PT;
    sem mudança de código.

  - **§258 — o guarda-chuva CodeQL fechado: #775 + #776 corrigidos no próprio
    arquivo** (21/09, lane development/`.18`): `NumericFormatterE2ETest.runJvm()`
    não spawna mais o token relativo `"java"` (#775 `java/relative-path-command`)
    — passa a usar o helper CodeQL-safe do harness `TestJdk.javaBin()`
    (`Path.of(System.getProperty("java.home"), "bin", "java")`, já adotado pelos
    E2E irmãos), 3/3; `KofHttp.supportedOn(Target)` (#776 `java/unused-parameter`)
    removeu o parâmetro morto `@SuppressWarnings("unused")` e lê `target` via
    `switch` exaustivo sobre `Target`, igual ao irmão `KofDb.supportedOn` —
    comportamento inalterado (`true` para todo target que embarca),
    `KofHttpServerTest`+`StdCatalogTest` 19/19. Fila viva 16→15 (a varredura nat
    já levara 18→16 com §192/§358); o waiver do §258
    no ledger do CHANGELOG é removido. Re-run do gate CodeQL = INCONCLUSIVO (API
    em rate limit, como nas rodadas anteriores); o CI `codeql.yml` é a porta real.

  - **§396-cross — a face riscv64/aarch64 do println de record null ganhou o
    guard também** (21/09, lane compilador): a lane gaps-db corrigiu a face x86
    (guard `NativeX86ValueOf` no dispatch do valueOf) e catalogou o resíduo
    cross — "riscv/aarch64 carregam o MESMO dispatch sem guard". Portado: guard
    `beqz a0` no ENTRY do call-site (`NativeVtableToStringGuard.emitRiscv`;
    null -> `kof_string_from_literal("null",4)`, não-null byte-a-byte; aarch64
    herda via tradutor). Pins nos 3 alvos com o golden medido na JVM
    `null\nnull\nPoint[x=1, y=2]` (RED ec=139 em riscv/aarch antes do guard;
    qemu cross 53/53+53/53; x86 verde pela solução da gaps-db já no tip — sem
    duplicação de guard de impressora: os edits x86 desta lane caíram no rebase).
    Registrado também como §422: a evidência da suíte completa achou
    `CompilerDriverTest#externProducesHonestGapNotSilentDrop` VERMELHO no tip
    limpo (rejeição de `extern` não-suportado desapareceu — R6), roteada à lane
    FFI, NÃO relaxada aqui. **Resolvido no mesmo dia (lane FFI): NÃO é bug — o
    teste era stale** (`Int[]` binda por desenho desde o D6-2, `7c6413d4`); a
    rejeição com gap honesto está intacta — reapontado para assinaturas
    genuinamente não-suportadas (`String[]`/`List<Int>` → `FFI001`, `Buffer(Int)`
    → `SEM096`), asserção não relaxada; `CompilerDriverTest` 259/0F/0E.


  - **deps — `kof deps resolve` ganha a POLÍTICA de verificação de proveniência (D-ARTIFACT-TRUST fila (c)), em modo observe**
    (21/09, lane platform-cli/segurança): antes de extrair/instalar um pacote do registry, o tar.gz baixado é conferido contra a
    identidade PEDIDA (owner/repo@versão — nunca o que a release declara): digest, repo-fonte, workflow que assinou, ref e commit
    da tag. Pacote oficial sem evidência válida = bloqueado (`REG005` ausente · `REG006` inválida · `REG007` sem como verificar ·
    `REG008` commit da tag não resolvido), nada instalado; comunitário = aviso visível. O modo padrão é OBSERVE (nunca bloqueia,
    imprime `would block under enforcement`) até o pipeline de release publicar atestações; `KOF_DEPS_TRUST=enforce` aperta, nada
    afrouxa. Pega o que o `SHA256SUMS` embutido não pega: jar E soma trocados de forma consistente dentro do tarball. O verificador
    real (delegado a ferramenta auditada) e o E2E esperam o attest+verify do workflow de release.

  - **tooling — least privilege por job nos workflows fora do release**
    (21/09, lane platform-cli/segurança, hardening de D-ARTIFACT-TRUST §5): 19 grants `write` no nível do
    workflow → 0 (a escrita agora é escopo de job: 11, cada uma justificada em `scripts/workflow-permissions.txt`);
    os 3 workflows que herdavam o default do repo agora declaram `contents: read`. Writes sem uso removidos
    (`kof-bots` só precisa de `actions: write` p/ `createWorkflowDispatch`; `kof-warning-bot` de nenhum;
    `checks`/`issues` saíram dos bots de quality/security). Gate novo `scripts/check_workflow_permissions.py`
    (+ `--selftest`, suíte de agentes / `structural-quality` da CI): `write` de job sem linha no ledger falha;
    linha obsoleta é drift. `release.yml` segue como a isenção declarada.

  - **tooling — toda Action de terceiros dos workflows fora do release agora é pinada por SHA de commit completo**
    (21/09, lane platform-cli/segurança, hardening de D-ARTIFACT-TRUST §5): 51 referências `uses:`
    (`owner/repo@<sha40> # vN`, gitleaks como `@sha256:<digest> # v8.28.0`) — nenhuma versão foi trocada, só
    tornada imutável. Gate novo `scripts/check_workflow_pins.sh` (+ `--selftest`, na suíte de agentes / job
    `structural-quality` da CI) exige o SHA completo **e** o comentário `# vN` (o que o Dependabot usa p/ subir o pin).
    `release.yml` é a única isenção declarada (`scripts/workflow-pins-exempt.txt`) — o pin dele entra com a
    reescrita attest+verify; o gate acusa drift quando a isenção ficar obsoleta.

  - **§353 corrigido — lambda cujo corpo retorna DIRETAMENTE um resultado `io` agora tipa**
    (21/09, lane bugs-and-gaps): `job("e", () -> File("x").exists())` era rejeitado com
    SEM014 ("expected 'function' but got 'function'") porque o typer SEMÂNTICO
    (`SemMethodCallTyper`) não tinha ramo `KofIo` enquanto o do emit tinha — o corpo da
    lambda inferia `Unknown`/Void. O lado SEM agora espelha a tabela do emit (aditivo:
    só programas antes rejeitados passam a compilar). A tipagem correta de io expôs duas
    formas insalubres que o typer cego engolia, ambas na mesma unidade:
    `MakealivePrimitivesE2ETest.ioStateRoundTrip` dereferenciava `readText()` (`String?`)
    sem guarda (SEM049 real — consertado no teste com o idioma `!= null &&`), e
    `MakealiveFsProviderE2ETest.fsRead` usa **early-return narrowing**
    (`if (t == null) { return }` e depois `t.split`) — completado no `StatementAnalyzer`:
    depois de um `if` sem else com saída garantida, o narrowing do lado ELSE vale no
    escopo externo (`Narrowing.thenBranchExits`). Prova:
    `WorkflowE2ETest#lambdaBodyWithIoBoolCompilesAndRuns` (RED com a correção em stash /
    GREEN com ela, JVM==JS em disco real) + `NullSafetyE2ETest` 14/14 (positivo + gêmeo
    negativo — SEM049 continua disparando sem saída garantida). Relacionado: §400
    catalogada (função nomeada como valor -> SEM011; pre-existente, medido idêntico no
    0.4.7; roteado à mantenedora, regra 6).


  - **§396 CORRIGIDO — `println` cru de RECORD NULL era SIGSEGV no Native x86-64 (a JVM imprime "null")** (21/09, lane gaps-db — descobridor fecha): o ramo record do emissor `String.valueOf` do println chamava toString na vtable do objeto SEM guard de null, então o `movq 8(%rax)` desreferenciava zero e o binário morria exit 139 (medido antes do fix). Correção de causa-raiz no emissor: guard `testq %rax,%rax; je` que PULA o dispatch e deixa 0 como resultado — o runtime (`kof_print_string`) já converte 0 em `"null"` (`.Lkof_null_str`), a mesma conversão de `String.valueOf(null)` que o host usa; caminho não-nulo byte-idêntico ao anterior (regra 2 do freeze, puramente aditivo). Achei no oráculo novo `println(miss)` do `orm.find<User>(db, 999)` (o slice já devolvia o null correto — gdb: miss → rax=0). Split mecânico na mesma unidade (gate 500): bloco valueOf movido verbatim para o novo `nat/NativeX86ValueOf.java`; `NativeX86Calls` 606 ≥600 seria CRÍTICO — baseline re-travado. Lição honesta medida no driver: um rascunho de guard chamou `kof_println_string` dentro do ramo e imprimiu `"null"` duas vezes (o consumidor externo já imprimia o resultado do valueOf) — corrigido antes do commit. Prova: novo `RecordNullPrintE2ETest` 2/2 (miss `"null"` + hit `"Point[x=3, y=4]"`, e 2 sítios null + um hit para travar os labels únicos por emissão) byte JVM==Native; Script/JS medidos por CLI = paridade (`String(null)`="null" no JS); resíduo catalogado no §396: os espelhos riscv/aarch64 ainda sem o guard (frente separada, ORM001 já cobre o gate de compile-time). Vizinhos verdes: RecordNullableNullEq 11/0F, RecordImplements 4/0F, ArrayPrintFormat 7/0F; check_500 rc=0.
  - **F2d1 CORRIGIDO — `orm.deleteAll` no Native x86-64 funciona sobre o wire MySQL (D-DB-GAPS DB-3, primeira fatia do DB-3)** (21/09, lane gaps-db): a MariaDB do host (12.3.2, `127.0.0.1:13306`) foi encontrada com a senha de sudo fornecida pela mantenedora e subida (`mariadb-install-db`; root multi-auth `mysql_native_password` 'kofpass'+'kof'; base `test`). O wire nativo foi medido verde nela (`KofDbE2ETest.nativeMysqlWireProtocol`/`nativeMysqlPreparedBinary`) e o CRUD ORM do host também (`mariadbCrud`, porta agora via `KOF_MYSQL_PORT`, default 3306). O `kof_orm_delete_all` ganhou o ramo mysql: dialect backtick (host `kof_orm_q`) + `kof_db_execute` com `>= 0` — o wire devolve affectedRows (lição medida: `sete` devolvia false com 2 linhas apagadas; a expressão do host é `>=0`). Prova: novo `KofOrmE2ETest.deleteAllMysqlNativeMatchesJvm` byte JVM==Native (`true/true/{"c":0}`, idempotente) + `KofOrmE2ETest` 49/0F/2skip + `KofDbE2ETest` 27/0F/2skip + vizinhos (DomainGapCodes 15/0, pin §421 2/0, §396 2/0); check_500 rc=0. Próximo: F2d2 (`count` no mysql, depois save/find/all/where). Nota: a variante de recusa em compile do §421 da múltipla escolha foi DESCARTADA no re-pouso — a lane 9092/S0 já fechou o §421 em runtime com pin que exige compile success para URL literal H2 (zero-regressão, regra 8).
  - **F2d2 CORRIGIDO — `orm.count` no Native x86-64 funciona sobre o wire MySQL + split do gate 500 (D-DB-GAPS DB-3)** (21/09, lane gaps-db): o `kof_orm_count` ganhou o ramo `kof_db_type==2` — `SELECT COUNT(*) FROM \`table\`` (dialect backtick = host `kof_orm_q`) enviado como COM_QUERY; o resultset é lido com os readers do stack (`kof_db_mysql_reset/next/lenenc`): col count, col defs puladas, o EOF pós-colunas (0xFE) e o row, com o parse de dígitos bounded pelo len do valor (nunca além do pacote); ERR/NULL/sem row → 0, o mesmo zero-row honesto do ramo sqlite. Bug medido no 1º run: o reader pegou o pacote EOF pós-colunas como row, então o count era 0 fixo — corrigido e coberto pelo teste novo byte-JVM==Native (`3 -> 2 após delete -> 0 após deleteAll`). A mesma unidade pagou o gate 500: `RuntimeOrm1` tinha crescido para 637 (>=600 CRÍTICO) — o ramo mysql (F2d1+F2d2) foi para o novo `RuntimeOrmMysql` (215 linhas, nome por responsabilidade); `RuntimeOrm1` 508; os helpers novos alinham rsp em 16B. Prova: `KofOrmE2ETest#countMysqlNativeMatchesJvm` + `deleteAllMysqlNativeMatchesJvm` byte JVM==Native; `KofOrmE2ETest` 50/0F/2skip; vizinhos DomainGapCodes 15/0, pin §421 2/0, RecordNullPrint 2/0; check_500 rc=0 para a lane (ver §435: o outro vermelho do run é o `LspServer` 601 da lane cli). Próximo: F2d3 (`save`/`find`/`all`/`where` no mysql).
  - **F2d3a CORRIGIDO — `orm.delete` no Native x86-64 funciona sobre o wire MySQL (D-DB-GAPS DB-3)** (21/09, lane gaps-db): o `kof_orm_delete` agora checa o tipo do id primeiro; no mysql delega ao novo `.Lorm_del_my` (`RuntimeOrmMysql`): `DELETE FROM \`t\` WHERE \`pk\` = ?` com a PK do `kof_orm_parse_schema` (mesmo critério do host e do caminho sqlite) enviado via `kof_db_execute1` (prepared binário, sem formatar literal no cliente); o retorno mapeia `execute1(...) >= 0` — miss é `true`, exatamente o comportamento do host já medido no F2c3. Prova: novo `KofOrmE2ETest#deleteMysqlNativeMatchesJvm` byte JVM==Native (`true / 2 / true / 2`: hit, count, miss, count); `KofOrmE2ETest` 51/0F/2skip (o check de tipo deixa todas as faces sqlite no caminho original). Ledger: §436 (assinaturas de `secrets.of`/`secrets.secret` no `StdCatalog`, drift do D-SECRETS) fechado pela lane `.18` no mesmo intervalo (`14ae7aab`); o vermelho do `check_500` deste re-pouso (`JvmOpCollections` 604 >= 600, lane JVM) foi catalogado como §437 — não tocado aqui; §438 catalogado (testes debug/serve do cli vazam JVMs suspensas -> o tmpfs esgotou no meio da suíte; limpeza do host 6,3G->621M pela lane gaps-db); README 21 vivos. Próximo: F2d3b (`find` no mysql, depois `all`/`where`).

  - **F2d3b CORRIGIDO — `orm.find` (row-object) no Native x86-64 funciona sobre o wire MySQL (D-DB-GAPS DB-3)** (21/09, lane gaps-db): o `kof_orm_find` agora dispatcha pelo tipo do handle — `kof_db_type(id)==2` restaura o frame e tail-chama o novo `.Lorm_find_my`, que envia `SELECT * FROM \`t\` WHERE \`pk\` = ?` (dialect backtick = host `kof_orm_q`) como COM_QUERY com o key renderizado em literal SQL (KofString -> `kof_db_mysql_render` com aspas/escape; box §284 -> numérico cru via `kof_long_to_string`/`kof_double_to_string`; null -> `NULL`; o `?` é trocado por `kof_db_mysql_replace_q`). O resultset é lido pacote a pacote: nomes de coluna casados byte a byte contra o schema, `kof_orm_ctors` constrói o record, valores convertidos pelo typeCode (int/long com sinal, string, bool com a paridade §397, double/float via `strtod` numa cópia NUL-ada); miss -> null (host), ERR do servidor -> throw `"mysql: <mensagem>"`, campo sem coluna -> throw `"mysql: no column <nome>"`; após a primeira linha o resto do resultset é drenado até o EOF para o socket nunca dessincronizar. Gate 500 pago na mesma unidade: a fatia foi dividida em `RuntimeOrmMysqlFind` (451) + `RuntimeOrmMysqlKeyLit` (217, literal do key + helpers de leitura) — ambas abaixo de 500 e sem entrada no baseline. Prova: novo `KofOrmE2ETest#findMysqlNativeMatchesJvm` byte JVM==Native com 8 linhas medidas (hit de key Int com 4 campos, miss -> null, keys Int/Long/String), `KofOrmE2ETest` 52/0F/2skip + `KofDbE2ETest` 27/0F/2skip + `DomainGapCodesTest` 22/0 + `RecordNullPrintE2ETest` 2/0; check_500 rc=0. Ledger: §437 fechado CORRIGIDO 21/09 (lane JVM `a9bbdfc5` removeu o dead code do fix duplicado do §432: `JvmOpCollections` 604->594, abaixo da linha crítica, `check_500` rc=0 — crescimento 584->594 fica na banda tolerada); README 14 vivos. Próximo: F2d4 (`all`/`where`/`page`/`saveAll` no mysql, reusando o reader de rows).
  - **F2d4a CORRIGIDO — `orm.all` (lista de row-objects) no Native x86-64 funciona sobre o wire MySQL (D-DB-GAPS DB-3)** (21/09, lane gaps-db): o `kof_orm_all` agora dispatcha pelo tipo do handle — `kof_db_type(id)==2` restaura o frame e tail-chama o novo `.Lorm_all_my` (`RuntimeOrmMysqlAll`), que envia `SELECT * FROM \`t\`` como COM_QUERY e percorre o resultset pacote a pacote (mesmo walk do F2d3b): nomes de coluna casados byte a byte contra o schema, um record por linha construído por `kof_orm_ctors` + `kof_init_object`, valores convertidos pelo typeCode (int/long com sinal, string, bool com a paridade §397, double/float via `strtod` numa cópia NUL-ada; NULL -> 0/null/false) e acumulados com `kof_list_new`/`kof_list_add`; zero linhas devolve a lista VAZIA (nunca null, como o host), ERR do servidor -> throw `"mysql: <mensagem>"`, campo sem coluna -> throw `"mysql: no column <nome>"`, perda de transporte no meio do walk -> throw `"mysql: connection lost"` (R6 — nunca silêncio). Prova: novo `KofOrmE2ETest#allMysqlNativeMatchesJvm` byte JVM==Native com 8 linhas medidas (3 linhas name/age, uma deletada -> 2 linhas, deleteAll -> 0 = lista vazia); `KofOrmE2ETest` 53/0F/2skip + `KofDbE2ETest` 27/0F/2skip + `DomainGapCodesTest` 24/0; `RuntimeOrmMysqlAll` 423 linhas (<500, sem entrada no baseline), check_500 rc=0. Próximo: F2d4b (`where`/`where_op` no mysql).
  - **F2d4b CORRIGIDO — `orm.where`/`where_op` (lista de row-objects) no Native x86-64 funcionam sobre o wire MySQL (D-DB-GAPS DB-3)** (21/09, lane gaps-db): as duas faces `where` agora dispatcham pelo tipo do handle (`kof_db_type(id)==2` no corpo compartilhado do `RuntimeOrm7` → tail-chama `.Lorm_where_my`, className em `r10`) e enviam `SELECT * FROM \`t\` WHERE \`f\` <op> ?` como COM_QUERY com o value renderizado em literal SQL pelo `.Lorm_key_lit` do F2d3b (o `kof_db_mysql_replace_q` troca o `?`); a whitelist do op foi extraída para `RuntimeOrmMysqlOp` (`.Lorm_my_op`) com a semântica exata medida no host (`>` `<` `>=` `<=` `!=` `LIKE` case-sensitive, `==`→`=`, o resto throw `ORM operator not allowed: <op>`); o walk do resultset é o do F2d4a (colunas por nome, um record por linha, lista VAZIA se nada casar — nunca null, ERR do servidor -> `"mysql: <mensagem>"`, campo sem coluna -> `"mysql: no column <nome>"`, perda de transporte -> `"mysql: connection lost"`). Gate 500 pago na unidade: `RuntimeOrmMysqlWhere` 470 + `RuntimeOrmMysqlOp` 122 (ambas <500, sem entrada no baseline; `RuntimeOrm7` 585 fica na banda tolerada). Prova: novo `KofOrmE2ETest#whereMysqlNativeMatchesJvm` byte JVM==Native (igualdade hit/vazio, `>`, `LIKE`, `==`, `!=`, throw exato `ORM operator not allowed: DROP TABLE user`, re-query após o throw), `KofOrmE2ETest` 54/0F/2skip + `KofDbE2ETest` 27/0F/2skip + `DomainGapCodesTest` 24/0. Ledger: §442 catalogado (vermelho do check_500 já no remoto: `SemExpressionTyper` 547->603 após os fixes §400/§440 — a lane frontend/types é dona do split; a árvore gaps-db só registra). Próximo: F2d4c (`page` no mysql).
  - **F2d4c CORRIGIDO — `orm.page` (lista de row-objects) no Native x86-64 funciona sobre o wire MySQL (D-DB-GAPS DB-3)** (21/09, lane gaps-db): o `kof_orm_page` agora dispatcha pelo tipo do handle (`kof_db_type(id)==2` → tail-chama o novo `.Lorm_page_my`, RuntimeOrmMysqlPage) e envia `SELECT * FROM \`t\` LIMIT <lim> OFFSET <off>` como COM_QUERY, com lim/off convertidos exatamente como o host (`((Number)x).intValue()` via o já medido `.Lorm8_pv` do RuntimeOrm8 — box int/long/double/float, KofString do coerce = atoi, null = 0) e renderizados como literais numéricos por `kof_long_to_string`; o walk do resultset é o do F2d4a (colunas por nome, um record por linha, lista VAZIA quando a página não tem linhas). Prova: novo `KofOrmE2ETest#pageMysqlNativeMatchesJvm` byte JVM==Native (duas páginas cheias, `LIMIT 0` e offset distante = vazio, limite tipado Long, página parcial com `name/age`), `KofOrmE2ETest` 55/0F/2skip + `KofDbE2ETest` 27/0F/2skip + `DomainGapCodesTest` 24/0; `RuntimeOrmMysqlPage` 459 (<500, sem entrada no baseline), `RuntimeOrm8` 504 (banda tolerada, 96 do crítico). Nota de ambiente: o reboot do host derrubou o MariaDB dos E2E mysql; um datadir persistente novo está em `~/.local/share/kof-mariadb` na porta 13306 com `--skip-grant-tables` (o server anterior aceitava tanto `kof` quanto `kofpass`, exigidos pelo `mariadbCrud` e pelos testes F2d* respectivamente). Próximo: F2d4d (`saveAll` no mysql).
  - **F2d4d CORRIGIDO — `orm.saveAll` no Native x86-64 funciona sobre o wire MySQL (D-DB-GAPS DB-3)** (21/09, lane gaps-db): o `kof_orm_save_all` agora dispatcha pelo tipo do handle (`kof_db_type(id)==2` → o novo `.Lorm_saveall_my`, RuntimeOrmMysqlSaveAll) e espelha as três saídas medidas do host por item (`JvmOrmRuntime.kof_orm_save`): pk nula/0 → `INSERT` SEM a coluna PK (id gerada; a instância patchada é descartada exatamente como no saveAll do host); pk != 0 → `UPDATE ... WHERE pk = <lit>`; UPDATE com 0 linhas → INSERT de todas as colunas (upsert-like). Os valores viram literais SQL inline pelo typeCode do schema (int `movslq`, long/bool `kof_long_to_string`, double/float `kof_double_to_string` com `cvtss2sd`, KofString `kof_db_mysql_render`, string null → `NULL`; tipo fora do contrato → ORM001), nomes de coluna com backtick (dialeto do host), COM_QUERY com o reader de pacote (OK → affectedRows; ERR do servidor → throw `mysql: <msg>`; perda → throw `mysql: connection lost`). Dois fixes de raiz medidos ao provar: (1) o connect nativo agora seta **CLIENT_FOUND_ROWS** (`0x00088209` → `0x0008820B`) — o default do MariaDB Connector/J (`useAffectedRows=false`) faz o UPDATE do host devolver FOUND rows, então uma linha sem mudança devolve 1; sem a flag o primeiro teste verde morreu em `Duplicate entry '2' for key 'PRIMARY'` (UPDATE afetadas 0 → INSERT-all de uma pk existente); (2) a fatia mora junto do RuntimeOrm10, FORA do gate `ormCtorClasses`, porque o dispatch no `kof_orm_save_all` é incondicional — emitida dentro do gate quebrava no link programas só-sqlite (`undefined reference to .Lorm_saveall_my`). Prova: novo `KofOrmE2ETest#saveAllMysqlNativeMatchesJvm` byte JVM==Native (pks geradas 1/2, lote upsert com pks 1/2 + pk nova 3, lista vazia = true, deleteAll = 0), `KofOrmE2ETest` 56/0F/2skip + `KofDbE2ETest` 27/0F/2skip + `DomainGapCodesTest` 24/0. Próximo: F2d5 (`orm.save` mysql, patch da id gerada).
  - **F2d5 CORRIGIDO — `orm.save` no Native x86-64 funciona sobre o wire MySQL (D-DB-GAPS DB-3)** (22/09, lane gaps-db): o `kof_orm_save` agora dispatcha pelo tipo do handle (`kof_db_type(id)==2` → o novo `.Lorm_save_my`, RuntimeOrmMysqlSave) e espelha as três saídas medidas do host: pk nula/0 → `INSERT` SEM a coluna PK, depois `SELECT LAST_INSERT_ID()` (RuntimeOrmMysqlLastId: COM_QUERY, walk de colunas + EOF + primeira linha, dígitos parseados; NULL/sem-linha/sem-resultset → 0) e uma NOVA instância do record com a pk patchada (`kof_alloc` + `kof_init_object` com typeId/vtable da fonte + `rep movsq`, exatamente como o caminho sqlite do Orm4); pk != 0 → `UPDATE`, afetadas > 0 (FOUND rows) → mesmo ponteiro; UPDATE 0 linhas → INSERT de todas as colunas → mesmo ponteiro. Bug de raiz medido ao provar: o helper `.Lsm_est` invocado por `call` lia os slots do chamador deslocados 8 (schema onde morava o ftab → SIGBUS BUS_ADRERR, achado com coredumpctl + objdump) — reescrito para receber rdi/rsi/rdx e devolver rax. Prova: novo `KofOrmE2ETest#saveMysqlNativeMatchesJvm` byte JVM==Native (pk gerada patchada = 1, UPDATE hit mantém id 1, segunda insert gerada = 2, upsert de pk explícita = 7, `orm.all` lê as três linhas de volta), `KofOrmE2ETest` 57/0F/2skip + `KofDbE2ETest` 27/0F/2skip + `DomainGapCodesTest` 24/0; RuntimeOrm4 501 (banda tolerada). Próximo: F2d6 (count_where/create/migrate no mysql).
  - **F2d6 CORRIGIDO — `orm.count` com filtro no Native x86-64 funciona sobre o wire MySQL (D-DB-GAPS DB-3)** (22/09, lane gaps-db): o `kof_orm_count_where` dispatcha pelo tipo do handle (`kof_db_type(id)==2` → o novo `.Lorm_cw_my`, RuntimeOrmMysqlCountWhere, emitido fora do gate `ormCtorClasses` porque um programa só de count não registra ctors) e espelha `JvmOrmRuntime.kof_orm_count_where`: `SELECT COUNT(*) FROM `t` WHERE `f` = <literal>` (dialeto backtick do host; o schema nunca entra no SQL, como no host) com o value em literal — box §284 int/long/bool/double/float, KofString via `kof_db_mysql_render`, null → `NULL`, tipo fora → ORM001 (R6). A query roda no executor de 1 coluna já medido do RuntimeOrmMysql (`.Lorm_count_mysql`: erro/NULL/sem linha → 0) e id inválido lança `.Lorm_bad_conn` (mesma string do host). Um bug de raiz pego no caminho: uma edição anterior colou um `.ascii` no label seguinte (assembler `junk at end of line`), corrigido. Prova: novo `KofOrmE2ETest#countWhereMysqlNativeMatchesJvm` byte JVM==Native (string, int, miss, injeção `x' OR 1=1 --`, negativo, hit 41), `KofOrmE2ETest` 58/0F/2skip + `KofDbE2ETest` 27/0F/2skip + `DomainGapCodesTest` 24/0. Próximo: F2d7 (`create`/`migrate` no mysql).
  - **F2d7 CORRIGIDO — `orm.create`/`orm.migrate` no Native x86-64 funcionam sobre o wire MySQL (D-DB-GAPS DB-3); a frente ORM x86-64 está 13/13 faces** (22/09, lane gaps-db): o `kof_orm_create` dispatcha pelo tipo do handle (`kof_db_type(id)==2` → `.Lorm2my_create`, a variante mysql do MESMO parser de schema do RuntimeOrm2, emitida fora do gate `ormCtorClasses`; dialeto do host: backtick, INT/BIGINT/TINYINT(1)/VARCHAR(255), generated → `BIGINT AUTO_INCREMENT PRIMARY KEY`) e o `kof_orm_migrate` dispatcha para `.Lorm_mig_my` (RuntimeOrmMysqlDdl: `kof_migrations` com backtick, checagem de aplicada via `SELECT COUNT(*) ... WHERE name = <literal>` no `.Lorm_count_mysql` já medido, sql do usuário por `.Lorm_ddl_exec`, INSERT do histórico com ms por clock_gettime; ERR do servidor → `mysql: <msg>` como o host que deixa a SQLException propagar; id ruim → `.Lorm_bad_conn`). Prova: novos `KofOrmE2ETest#createMysqlNativeMatchesJvm` byte JVM==Native (create idempotente, pk gerada=1 pelo AUTO_INCREMENT) e `#migrateMysqlNativeMatchesJvm` (aplica uma vez — a 2ª chamada com SQL inválido devolve true sem reexecutar; ALTER provado por insert de 3 colunas; 2 linhas de histórico), `KofOrmE2ETest` 60/0F/2skip + `KofDbE2ETest` 27/0F/2skip + `DomainGapCodesTest` 24/0; check_500 falhando só no §442 pré-existente.
### Em desenvolvimento
  - **F2c3 — `orm.page`/`delete`/`saveAll` REAIS no Native x86-64; row-object
    x86 fechado (lane gaps-db)** (21/09): `RuntimeOrm8` (`kof_orm_page`) = SQL
    do host `SELECT * FROM "t" LIMIT ? OFFSET ?` com os DOIS params bindados e
    a semantica do `((Number) x).intValue()` replicada no parse (box int/long/
    dbl/flt truncam p/ int32; KofString do coerce do call-site = atoi com
    sinal — fora do contrato Number, superset honesto documentado; null/
    desconhecido = 0); frame 184 (mod 16 = 8 como a familia), loop de linhas =
    Orm7 verbatim. `RuntimeOrm9` (`kof_orm_delete`) = `DELETE FROM "t" WHERE
    "pk" = ?` com PK resolvida pelo `parse_schema` (r8=pkIndex, criterio do
    host) e bind do key pelo classificador do Orm7; retorna SEMPRE true no
    DONE — o `execute1 >= 0` do host faz do miss um true (medido no oraculo:
    delete de PK inexistente = true, count intacto). `RuntimeOrm10`
    (`kof_orm_save_all`) = loop `kof_list_size`/`kof_list_get` →
    `kof_orm_save`, instancia remendada DESCARTADA como no host (leitura de
    volta prova), `true` no fim; GC-safe por alcanca-bilidade da List no slot.
    Orm8 emitido no bloco dos ctors (className no r9, 6 args — scan estendido
    p/ `kof_orm_page`); Orm9/Orm10 incondicionais no `usesOrm` (sem
    className). Design-first: oraculos JVM medidos ANTES da asm (corpo de 14
    linhas + edges: batch vazio, offset alem do fim, miss=true). Provas:
    `KofOrmE2ETest#pageDeleteSaveAllNativeEndToEndMatchesJvm` +
    `#pageEdgesDeleteMissSaveAllEmptyNativeMatchesJvm` byte a byte
    JVM==Native — classe 48/0F/3skip; pino ORM001 migrou p/ cross riscv64
    (`#rowObjectFechadoNoX86CrossAindaOrm001` — o que restou: runtime-MySQL e
    espelhos cross, nunca silent). Docs EN+PT: parity, tracker 1.1.9, DOING
    (claim no mesmo commit). O suíte trouxe 1 vermelho de
    outra camada exposto pela abertura do gate: §421 catalogado (native
    `db.connect` aceita scheme fora do contrato — H2 — sem recusa nomeada; raiz
    pre-existente, ORMs ok com sqlite medido). Obs: o `stash@{0}` local (autostash da era F2a,
    nao empurrado por ninguem) foi INSPECIONADO e deixado intacto — todo o
    conteudo ja pousou em forma diversa (`RuntimeOrm4`/`NATIVE_F1`); nao
    pessem-lo as cegas.
  - **F2c2 — `orm.where`/`whereOp` row-object→List REAL no Native x86-64
    (gaps-db lane)** (21/09): `RuntimeOrm7` — UM corpo, dois globls. O `where`
    do Kof é polimórfico por aridade (3 args → `kof_orm_where`; 4 args →
    `kof_orm_where_op` com op do usuário — 7 args no host). O 7º arg
    (`className`) é lido da stack na entry do callee (`8(%rsp)`, ANTES do
    `andq`) — o caller S7f (13/09) já empilha arg7 no topo (SysV,
    caller-cleanup). Whitelist do op idêntica ao host (`> < >= <= != LIKE`;
    `==`→`=`; senão throw `ORM operator not allowed: <op>` — mensagem exata
    medida no oracle JVM). Bind do value pelo mesmo classificador do key do
    Orm5 (box §284 / KofString do coerce do call-site / null →
    bind_text/int64/double/null). Três bugs pegos no caminho: `movl mem,mem`
    (erro de sintaxe do `as`, pego por RED-first no `as`), A/B só em
    registrador durante o alloc interno do `concat` (GC conservativo —
    corrigido: empilhados, protegidos), e o `concat` clobberando `%rdi` via
    `kof_memcpy` (corrigido: `movq %rax,%rdi` após o call). Prova:
    `KofOrmE2ETest#whereNativeEndToEndMatchesJvm` byte-a-byte JVM==Native
    (oracle de 10 linhas com op inválido + catch + re-query) — classe
    46/0F/3skip; pin ORM001 migrou para `page` (F2c3). Docs EN+PT: parity,
    tracker 1.1.9, DOING (claim no mesmo commit).
  - **F2c — `orm.all` row-object→List REAL no Native x86-64 (gaps-db lane)**
    (21/09): `kof_orm_all` em asm (`RuntimeOrm6`) — loop de campos do find
    (casamento por NOME, leitura por typeCode + tipo dinâmico da coluna com o
    §397) + `kof_list_new`/`kof_list_add` por linha; ctor resolvido UMA vez
    (resolver `kof_orm_ctors` da irmã, agora alimentado por find E all —
    set renomeado `ormCtorClasses`); lista VAZIA != null (host devolve
    ArrayList sempre). Design-first: oracle JVM medido ANTES da asm (corpo
    `2/Mel/Ana/0/empty=0`). Prova: `KofOrmE2ETest#allNativeEndToEndMatchesJvm`
    byte-a-byte JVM==Native (2 linhas por nome+size, `for-in`, vazio pós-
    delete, segunda chamada sem leak) — KofOrmE2ETest 45/0F/3skip; pin
    ORM001 migrou p/ `where` (F2c2). Docs EN+PT: parity, tracker 1.1.9
    (+ typo da irmã `F2a find`→F2b sincronizado com a linha do §397), DOING
    (claim da fatia no mesmo commit). Onde a asm é cópia adaptada do find
    (padrão da casa), o loop é o MESMO código já provado no §397.
  - **Checklist da prep de-staled + `check_live_records.sh` parte H** (21/09, lane docs): o item 1 da
    prep ainda dizia "pousar o que está em voo" para §374/#553, §371/#550 e §378/#554, e a lista
    "issues abertas que viajam" ainda nomeava #550/#553/#554 — mas os três § já estavam **✅ FIXED em
    20/09**, então a fila estava atrás do ledger. O item 1 virou `[x]` com as provas
    (`BareCollectionFieldE2ETest` 8/8, `ShippedCliCrossSmokeTest` 2/2 + `RuntimeSourceLoaderTest`
    6/6, cruzamento EN×PT do §378) e só o #555 (guarda-chuva CodeQL) viaja. A classe virou
    mecanizada: a parte H exige que todo `§NNN` citado na seção "issues que viajam" da prep esteja no
    conjunto ABERTO do classificador — prep que manda viajar um § fechado agora falha (red-first: o
    selftest planta um `§999` que não está aberto).
  - **`check_live_records.sh` parte G: contagem da cond.7 da prep == o classificador** (21/09, lane
    docs): a tabela da prep de release (`release-beta-0.5.0-prep.md` + PT) é o registro de aceitação,
    então o número da condição 7 não pode divergir da autoridade — e divergia: a linha dizia `17 live`
    enquanto `check_known_bugs_status.sh` contava **18** (a rodada do teste stale do §422 moveu a
    contagem e só o README foi ressincronizado). Os dois idiomas estão sincronizados e a classe virou
    mecanizada: a parte G exige que todo `N live at the tip` / `N live no tip` da prep bata com a
    contagem do classificador, e a ausência de contagem é falha (anti-neutering), então prosa trocada
    nunca passa em silêncio. Red-first: o selftest planta contagem divergente e prep sem contagem; a
    árvore real agora lê `18` em EN e PT.
  - **O gate agora fecha o ciclo nas duas direções** (21/09, lane docs): além de afirmação de fechamento sem respaldo no ledger, um id FECHADO no ledger a partir da seção 400 sem entrada no CHANGELOG agora derruba o gate. O piso é regra de época, não anistia: medido em 21/09, 25 ids abaixo de 400 não têm entrada enquanto ZERO acima têm — a prática consolidou, a regra começa onde a prática começa. Verificado por mutação: uma seção fechada plantada no ledger sem linha no changelog é nomeada pelo gate; estado real segue verde. (Uma ideia companheira — proibir referências do changelog a ids fora do ledger — foi medida e REJEITADA: os achados são remissões antigas de outro espaço de ids, regra errada para a história, recusada pela lição da rodada 11.)
  - **Passos de "Recuperação" da prep completados para host sem root / sem `gh`** (21/09, lane
    docs): os comandos documentados para limpar as condições auto-medidas assumiam toolchain cross
    instalado e `gh` funcionando. Ambos agora são de primeira classe — `eval
    "$(scripts/setup-cross-toolchain.sh --export)"` para a cond. 1 (R28) e
    `scripts/fetch-open-issues.sh > …tsv` + `R050_OPEN_ISSUES_TSV=…` para a cond. 5 (R29) — mais o
    lembrete de reconstruir o jar da árvore após o último commit do compiler. Medido nesta rodada:
    após `debd39ca`/`6f0a7e8d` da frente FFI o jar acusou `ARTEFATO VELHO` (parity NEEDS-MEASURE);
    reconstruir restaurou `PARITY: 100%`, e o gate lê 3 RED / 1 inconclusiva com as sete linhas
    batendo com a prep.
  - **Estabilidade medida pela primeira vez após o conserto do mecanismo: RED por um teste stale**
    (21/09, lane docs): uma corrida completa do `safe-suite.sh` no `96af9b63` (41 min, estampada
    `SUITE-SHA`/`DIRTY=0`) deu `TOTAL: tests=3413 failures=1 errors=0 skipped=223`. A única falha é
    `CompilerDriverTest.externProducesHonestGapNotSilentDrop`, que ainda exige `FFI001` para um
    extern `Int[]` que o `7c6413d4` (FFI 3.8b D6-2) tornou bindável no JVM de propósito — provado
    pelo `FfiArrayE2ETest` dedicado 5/5 (shim C real; `String[]`/`List<T>` seguem `FFI001`). Logo o
    gate está intacto e o teste é stale, não um drop silencioso. Registrado como hand-off para a
    frente FFI (`jonas`); o teste deve ser reapontado para uma assinatura não-ligada, não apagado.
    Reproduz isolado (258/1/0 em `kof-compiler`). Estabilidade segue RED até isso pousar.
  - **Gate de release `edges`: query de `1.0-blocks` que falha não vira mais "0 blocks"** (21/09,
    lane docs): num host sem `gh`, o ramo de roadmap do `c_edges` rodava
    `check_release_blockers.sh --rc-gate` (rc=3, sem linha de resumo) e então assumia `blocks=0` —
    um falso GREEN latente da condição 6, mascarado só porque EG-8 está aberto. Agora a contagem
    fica `UNKNOWN` quando a enumeração não é parseável (R6/Q5, a mesma regra fail-closed que o
    `bug_issues` já seguia), enquanto EG aberto continua RED; `R050_OPEN_BLOCKS` fornece a contagem
    offline e `R050_EG_ROADMAP`/`R050_BLOCKS_CMD` tornam o ramo testável. O selftest do gate ganha
    os dois casos (query falha -> UNKNOWN; query diz 0 -> GREEN).
  - **`check_live_records.sh` parte F: paridade de numeração/nível de seções em todos os pares
    EN<->PT** (21/09, lane docs): a parte C checava número+nível das seções numeradas só no
    `DECISIONS.md`; a F generaliza para **todo `docs/development/*.md` que tenha gêmeo `.pt_BR.md`**
    — um `## 1.` no EN casado com um `# 1.` no PT é drift da mesma classe que a lane já corrigiu
    uma vez no `DECISIONS.md` (§7). Red-first: o selftest planta um deslize de nível, e um deslize
    plantado no README PT (`## 1.` -> `# 1.`) foi nomeado na árvore real; os 8 pares estão em
    paridade hoje.
  - **`check_live_records.sh` parte E: paridade EN<->PT do estado dos EG do roadmap** (21/09, lane
    docs): o gate de release lê a tabela EG em `docs/development/roadmap.md` (só EN); se o roadmap
    PT divergisse (um EG fechado num idioma e aberto no outro), ninguém veria — e a condição 6 do
    release depende dessa tabela. O gate agora exige as mesmas linhas EG-N e o mesmo estado
    fechado/aberto em EN e PT, pela regra `DONE|FEITO` do próprio gate. Red-first: o selftest planta
    a divergência e uma divergência plantada no EG-8 do PT na árvore real foi nomeada.
  - **`check_live_records.sh` parte D: lista pendente da §0 do README == conjunto loose do gate**
    (21/09, lane docs): o gate já verificava a contagem viva (A), a paridade EN<->PT dos IDs de
    decisão do `DECISIONS.md` (B) e a numeração/nível das seções (C); agora também exige que a lista
    `Pendentes (condição 3)` da §0 do README da lane seja igual ao conjunto loose que o gate de
    release de fato marca (`ls docs/development/*.md` menos o `ALLOWLIST` dele), nos dois idiomas.
    Isso fecha uma divergência silenciosa em que o registro humano lista um doc que a medição não
    marca mais (ou omite um que marca) — registro e medição não podem discordar. Red-first: o
    selftest planta lista divergente, e um loose extra plantado na árvore real foi pego em EN e PT.
  - **Condição 5 (bug_issues) medida num host sem `gh`** (21/09, lane docs): o gate lê
    `UNKNOWN` quando o `gh` não está disponível — fail-closed por desenho, então falha de consulta
    nunca vira "0 bugs". Isso escondia o estado real neste host. Novo
    `scripts/fetch-open-issues.sh` enumera as issues abertas pela API pública do GitHub (`gh`
    autenticado ainda preferido; depois token; depois curl sem auth), no formato exato
    `numero<TAB>labels` que o gate consome via `R050_OPEN_ISSUES_TSV`, e falha alto (sem linhas,
    rc!=0) quando não consegue medir. Medido 21/09: **0 issues de bug abertas** (#580 é
    documentation/enhancement) -> condição 5 GREEN. O contrato fail-closed do gate segue intacto
    (o teste red-first ainda força falha do `gh` -> `UNKNOWN`). Parsing offline coberto por
    `scripts/tests/fetch-open-issues-test.sh`; `scripts/tests/setup-cross-toolchain-test.sh`
    cobre o helper de toolchain da R28 offline também.
  - **Condição 1 (paridade) certificada 100% neste host** (21/09, lane docs): a matriz por alvo
    reportava os alvos cross como RED/NEEDS-MEASURE (`sem riscv64-linux-gnu-as` /
    `sem aarch64-linux-gnu-as`) — lacuna de ambiente, não divergência de código. Reconstruir o jar da
    árvore (`scripts/build-kof-jar.sh`, limpa o bloqueio de artefato velho) mais um helper novo sem
    root `scripts/setup-cross-toolchain.sh` — que extrai os `.deb`s de binutils + qemu-user-static +
    libc-cross num prefixo local (sem apt/sudo) e imprime os exports de `KOF_CROSS_SYSROOT`/PATH —
    leva a matriz a certificar `PARITY: 100%` em jvm/x86-64/riscv64/aarch64/JS/Script byte-a-byte,
    então a condição 1 do release fica GREEN no host. A premissa "paridade só no CI" não resistiu à
    medição (R6: ferramenta faltando não é verde; provê-la não é bypass).
  - **Paridade de seções numeradas adicionada; nível da seção 7 reparado** (21/09, lane docs):
    medir todos os pares EN/PT sob `docs/development/` mostrou só o `DECISIONS.md` fora, e o resíduo
    era um deslize de nível — EN `# 7. Final rule` (H1) vs PT `## 7. Regra final` (H2), o único fora
    do padrão contra os irmãos H1 3–6. Corrigido e guardado: o `check_live_records.sh` agora também
    exige que as seções numeradas (`N.`) tenham o mesmo conjunto de números E o mesmo nível de
    heading nos dois idiomas (language-independent — os números não são traduzidos) — provado por
    mutação (rebaixar o 7 PT para H2 o nomeia; restaurado verde).
  - **Gate de registros vivos ampliado; paridade EN/PT do DECISIONS reparada** (21/09, lane docs):
    auditar o pouso da lane irmã achou três decisões da mantenedora registradas só no EN (`R6-SCOPE`,
    `D-R3-BUFFER`, `D-R3-HANDLE-LIFETIME`) — a lane irmã então as espelhou ao PT em `097ff924`;
    CORREÇÃO desta entrada como escrita primeiro: esta mudança NÃO as traduziu, remove uma
    corrupção remanescente no DECISIONS PT (um heading EN órfão mais uma cópia byte-idêntica do
    adendo de gráficos, ambas deletadas sem perda) e adiciona o gate. O `check_live_records.sh` agora
    também exige paridade EN<->PT dos IDs de decisão (template `D-XXXX` excluído) e zero heading de
    seção repetido — provado por mutação (duplicata replantada e seção PT removida são nomeadas;
    restaurado verde). O fio no agent-verify agora dispara para todo `docs/development/`.
  - **Registros vivos ganharam gate próprio — e o teste de fio ganhou dentes** (21/09, lane docs):
    `scripts/check_live_records.sh` exige que toda declaração `N itens`/`N vivos` nos dois READMEs
    desta lane bata com a contagem viva do classificador — a classe que driftou duas vezes hoje (um
    resync de frase deixou uma linha de tabela velha em `18`; o resync seguinte esqueceu a mesma
    linha, achada pela lane irmã em `5a80625c`). Declaração ausente é falha, não passe livre
    (anti-neutering). Construí-lo expôs dois bugs no ferramental da PRÓPRIA lane na mesma hora:
    (1) o hook novo foi inserido primeiro aninhado dentro de outro `if` — fio morto que o teste de
    wiring nível-regex não via, então o teste ganhou um nível funcional que executa o esqueleto real
    dos blocos; (2) esse nível novo falhou antes por falso-negativo de `pipefail`+SIGPIPE (`grep -q`
    fechando o pipe), corrigido capturando a saída primeiro. Provado por mutação ponta a ponta:
    contagem errada e bloco mal aninhado são ambos nomeados; estado real segue verde.
  - **Os fios agora estão sob teste** (21/09, lane docs): os gates changelog-ledger e ledger-anchors tinham detector testado mas hook NÃO provado dentro do `agent-verify.sh` — erro de digitação num regex `touches` = gate que nunca roda em silêncio (classe falso-verde, lição da rodada 12 aplicada ao wiring). Novo teste da suíte extrai os padrões vivos do dispatcher e confere que cada um dispara exatamente nas suas famílias de arquivos; verificado por mutação (quebrar `^CHANGELOG\.` deixou o teste vermelho nomeando os arquivos; restaurar voltou verde).
  - **Higiene de waiver agora é imposta, não prometida** (21/09, lane docs): uma waiver cujo id FECHOU no ledger enquanto o CHANGELOG ainda o cita passa a derrubar o gate (`STALE`, rc≠0) — fechar um item corretamente exige apagar a linha da waiver, então uma desculpa velha nunca pode abrigar uma mentira futura. Verificado por mutação ponta a ponta: virar a linha de status do item 268 no worktree fez o gate nomeá-la; restaurar voltou verde.
  - **`check_changelog_ledger` reconstruído por mutation-testing — era um falso-verde duas vezes** (21/09, lane docs): mutações realistas no worktree (virar o §420 para OPEN; truncar uma âncora viva) expuseram que (1) a lista de tokens não conhecia a prosa "§NNN closed/closes" da casa e (2) um bug de tipo int-vs-set tornava a checagem INTEIRAMENTE VACUA. O detector agora é um passe python que casa cada token de fechamento ao `§NNN` PRECEDENTE MAIS PRÓXIMO (conserta a atribuição em linhas multi-§: "§248 … §209 JVM ✅" não culpa mais o §248). Colheita honesta na primeira rodada real: 13 achados — 5 menções legítimas de meia-face/retração/citação histórica isentadas com justificativa (incl. o "Closes §268" de 17/09 do próprio CHANGELOG, superado pela reabertura PARTIAL do ledger em 18/09 — CHANGELOG é história, o ledger vence), 142 afirmações EN conferidas, 0 drift. Teste de suíte + selftest re-rodados verdes; a mutação do §420 agora é garantida de pegar.
  - **Gate de âncoras do ledger, `check_ledger_anchors.sh`** (21/09, lane docs): cada href de `pt-switch`/`en-switch` entre línguas tem que ser igual ao slug GitHub do heading de destino (diacríticos dobrados, pontuação removida, `_` preservado) — medidos HOJE 11 hrefs QUEBRADOS (era §395–§419 da irmã: prefixos abreviados à mão, `retracao` contra o `RETRATADO` do heading, deriva de contagem de hífens), todos regenerados mecanicamente até zero. `--selftest` planta um slug truncado; ligado no `agent-verify.sh` (dispara em `known-bugs*`) e na suíte de agentes da CI via `ledger-anchors-test.sh`. O bug exato de âncora do §420 que esta lane pousou uma hora antes é o caso motivador — agora guardado para toda lane. RETRATAÇÃO de uma afirmação falsa da mesma rodada (lição §419 aplicada a mim): a entrada acima declarava `test-kofc-gate-test` e `test-android-gate-test` VERMELHOS no tip limpo como dano pré-existente de outras lanes — remedido com o JDK que o repo exige (25) no PATH: **os dois verdes, todos os cenários OK**, e a suíte completa de agentes fecha 21/21 VERDE. O fantasma veio desta lane rodar a suíte sob o java 21 do sistema antes de exportar o toolchain na mesma linha do shell; o próprio `test-android-gate.sh` se recusou com honestidade no java velho (`SKIP — java 21 no PATH (kof exige 25)`). Não havia dano algum em EG-9/EG-10; o ping à dona na DOING é retratado no mesmo commit. Obs.: a entrada de correção do §420 acima foi escrita antes deste gate existir — corrigiu 2 hrefs à mão; este commit corrige a classe.
  - **Âncoras cross-lingue do §420 corrigidas no mesmo dia em que a retração §419 avisa** (21/09, lane docs): os links de troca EN↔PT foram escritos à mão com letras acentuadas e um `§` cru — o slug do GitHub dobra diacríticos e apaga pontuação, então as âncoras não apontavam para lugar nenhum. Os dois lados foram re-slugados com o algoritmo da casa (precedente `retracao` do §419) e verificados por igualdade de string contra os headings.
  - **Registro vivo ressincronizado 21/09 ~07:2x**: a contagem da fila em `development/README`(+PT) foi de 17 para **18** — a cadeia do dia agora registra os dois sentidos: correções derrubaram a contagem (§380/§381/§394/§353) e a re-publicação honesta da **§418** pela lane nativa (harness de debug riscv64, reprovada após a perda de árvore que a própria retração **§419** documenta) a subiu de volta. Snapshot da prosa atualizado; a autoridade segue sendo o script, por construção.
  - **`check_changelog_ledger` entra na suíte de agentes da CI** (21/09): `scripts/tests/changelog-ledger-test.sh` envolve o gate pelos dois lados — flip falso plantado tem que ser capturado (red-first) e o repo real tem que seguir consistente — registrado no `run-agent-tests.sh`; suíte completa de agentes 21/21 VERDE neste host.
  - **Bug de mecanismo do gate de release achado pela primeira corrida estampada — o `safe-suite.sh` nunca entregava o `TOTAL` ao log** (21/09, lane docs): o `stability-report.sh` certifica um log de suíte pela linha `TOTAL` + `SUITE-SHA`, mas o `safe-suite.sh` imprimia o `TOTAL` só no console — toda corrida estampada em qualquer host era permanentemente `unknown` e a condição 4 do release nunca podia ser medida. Um `tee -a` de uma linha; a primeira corrida completa estampada em `8f459b8e` agora lê **STABILITY: GREEN — 3355 testes / 0F / 0E / 223 pulados** no `check_release_050_gate.sh`, e a linha da condição 4 registra a medição (re-roda obrigatória na hora do corte, pela condição 6). A suíte red-first `check-release-050-gate-test` da irmã segue verde com a mudança.
  - **§420 fechado — morre o último cast bruto `(int)` sobre um `getArraySize()` do guest, com o precedente do §258** (21/09, varredura da lane docs): `KofJsRunner.writeBytes` (`:480`) truncava em silêncio em arrays do guest acima de 2^31 — exatamente a violação R6 que o texto do §258 nomeia. Bound check copiado dos vizinhos já corrigidos do próprio arquivo; `IoE2ETest` 24/24 verde neste host (Graal embutido — a linha da ponte roda em toda rodada). A varredura também re-mediu o próprio batch do §258: honesto (face #775 com a `.22`), e o ✅→REOPENED do §192 é reabertura real, não drift — o gate do ledger segurou.
  - **Gate novo `check_changelog_ledger.sh` — o CHANGELOG não pode mentir sobre o ledger** (21/09, lane docs): cada afirmação `§NNN ✅ FIXED` do `CHANGELOG.md` é conferida contra a fila viva que o `check_known_bugs_status.sh` imprime (mesmo classificador), e o mesmo pro PT. Ele VERMELHA exatamente o acidente que o motivou: o tick `d9384a5b` virou o §388 de `✅→🟡` sem um único conflito de rebase para avisar ninguém. Citação histórica de meia-face fechada só se isenta por linha nomeada em `scripts/changelog-ledger-waivers.txt` (2 hoje: §205-EN gate-red, §278-PT face db/orm). Ligado no `agent-verify.sh` (dispara quando a mudança toca CHANGELOG, known-bugs ou o waivers); `--selftest` planta um flip falso e exige que o gate pegue. Docs EN+PT no `development/README` §2.
  - **Fix-forward do ledger — flip do §388 restaurado** (21/09): o rebase do tick da irmã (tip `d9384a5b`) retrocedeu silenciosamente o status do §388 para `🟡 PARCIAL` nas duas línguas — o mesmo acidente de base velha que apagou a nota da DOING — porque o flip havia pousado um commit antes (`69b49b3a`), fora da zona de conflito que aquele rebase viu. Os dois blocos de status voltam ao texto original `✅ CORRIGIDO 21/09` (Repro A `SEM099`, Repro B `D-ARRAY-PRINT`), gate do ledger verde, contagem de abertos de volta a 17.
  - **Doc-sync pós-§388 (21/09)**: corrigido o `development/README` que ainda listava 3.8 como
    residual "à espera de decisão" (3.8 pousou); condição de entrada **E3 do `PLAN-BOOTSTRAP`
    marcada como cumprida** (§388 fechou as duas metades: `SEM099` + `D-ARRAY-PRINT`).
  - **§388-B fechado — `println(Int[])` é o formato de container §107** (21/09, voto da
    mantenedora `D-ARRAY-PRINT`): imprimir um array primitivo inteiro agora dá `[65, 66]`
    em todo target — JVM/Script pelo novo `kof_array_to_string` (JvmRuntimeCore; o
    interpretador por reflexão), JS roteando `valueOf(ArrayType)` pelo `kofFormat`, e o
    x86 nativo pelo gêmeo em asm (riscv64/aarch64 medidos na CI). A identidade `[I@…` e a
    face JS sem colchetes morreram; o `io.md` declara o formato e o contrato `SEM099` dos
    params de bytes. Fixado pela célula `arrayprint` (ConformanceMatrixTest, os quatro
    targets incl. nativo) + `ArrayPrintFormatE2ETest` 7/7; §388 vira ✅.
  - **Design 3.6 pousado — `docs/development/future/secrets-plan.md` (+EN)** (21/09): a
    resposta do Estágio 5 a "Secrets em logs: SEM PROTEÇÃO" — value type `Secret`
    (reveal-gated, equals em tempo constante, formato de impressão `Secret(*** )` declarado
    logo de saída), redação forçada em três camadas (compile/runtime/corpus) e `KeyHandle`
    (material de chave nunca chega ao guest; rotate() revoga). Zero código: P1/P2/P3 cada um
    espera seu próprio voto rule-6; a superfície crua `secrets.get`/`jwt` segue intacta.
    Linha 3.6 do tracker anotada, linha no `future/README` adicionada EN+PT.
  - **§388-A — `writeBytes`/`appendBytes` com `listOf(...)` compilava VERDE e
    morria em runtime** (20/09): o `lowerIo` agora reporta `SEM099` quando um
    argumento de tipo List encontra um parâmetro ArrayType — o contrato `Int[]`
    declarado no io.md passa a ser cobrado em tempo de compilação em todo target
    (JVM `VerifyError`, Script rc=1 mudo e mismatch JS morrem juntos; o caminho
    `new Int[n]` fica intacto). `IoArrayArgE2ETest` 5/5. A Repro B (imprimir o
    `Int[]` inteiro: JVM/Script `[I@…` vs JS `65,66,67`) segue aberta — decisão
    de contrato rule-6 pendente.
  - **§397 — binder compartilhado int→Bool virava `false` em silencio (JVM)**
    (20/09): descoberto PELO PROBE do F2b antes de escrever qualquer asm —
    `orm.save(Item(0, true))` gravava 1 e `orm.find<Item>(...).active` lia
    `false` (`rs.getObject` = `Integer`; `kof_json_bind` caia em
    `parseBoolean("1")`). Vitimas: `orm.find/all/where/page` JDBC,
    `db.query<T>` e `json.decode<T>` com campo numerico. Fix na raiz: ramo
    boolean aceita `Number -> intValue() != 0` (simetria gravar==ler; String
    segue `parseBoolean`). Prova Q0 medida RED->GREEN: 3/3 RED no binder
    velho, 3/3 GREEN com o fix — 1 teste por vitima
    (`KofDbE2ETest#typedQueryBindsIntColumnToBoolField`,
    `KofOrmE2ETest#findPreservesSavedBoolTrueRegression397`,
    `JsonCompleteE2ETest#jvmDecodeIntFieldIntoBoolRecordBindsTrue`).
    Contrato travado para a asm do F2b: `INTEGER != 0` no slot Bool.
    Follow-up medido 21/09: a asm do `find` (`RuntimeOrm5`, F2b da irma) levara o
    oracle antigo (TEXT "true", INTEGER 1 -> false) e virou divergencia JVM×Native
    com o host consertado — patch por tipo dinamico de coluna (`!=0`) +
    `findPreservesSavedBoolTrueRegression397` estendido p/ byte-paridade
    Native==JVM (RED sem o patch na assert cross, GREEN com; KofOrmE2ETest 44/0F).
    Lacuna de registro consertada no mesmo dia: o gate agora tem parágrafo no `development/README`(+PT) §2 ao lado do gate de língua.

  - **Fortalecimento de teste F2a (lane gaps-db)** (20/09): a unidade F2a
    (`save` row-object, `RuntimeOrm4`+`RuntimeOrmSchema`+`RuntimeOrmBind`)
    pousou por `4316d325`; esta fatia SÓ FORTALECE A PROVA — o
    `saveNativeEndToEndMatchesJvm` agora exercita o caminho UPDATE com VALOR
    ALTERADO (`User(1, "Mel-2", ...)` — record imutável, construtor novo com
    a mesma pk; o golden lê o nome de volta via `db.query`, provando que o
    slot alterado chega ao bind e ao banco — antes o UPDATE era salvo com
    valores idênticos e não provinha writeback). Prova medida: KofOrmE2ETest
    42/0F/3skip, paridade byte JVM==Native (10 linhas).
  - **3.8 entregue — `kof makealive plan|apply|destroy` (D-MAKEALIVE-CLI)** (20/09,
    `.18`): a linha de tooling do Stage 3 fecha com contrato delegado pelo maintainer. O
    verbo segue a decisão Q1 do namespace (`makealive`, não o literal `infra` negado pela
    R1); o arquivo segue Kof puro — `import kof.makealive`, `design(): Infrastructure`,
    `provider(): Provider`, sem main() — e o tool síntetiza um main() sobre as faces do
    host, falando pela linha marcada `@@KOF_MAKEALIVE@@ {json}` (formatação humana/JSON no
    CLI, exit decidido por `allOk` via truthy tolerante; throw = passthrough cru, rc 1, o
    anti-padrão da §255 segue fechado). O estado é um arquivo h2 (`--state PATH`, default
    `<file>.makealive`) salvo em gen = max+1 pela face nova `mkMaxGen`; o E2E do destroy
    achou e corrigiu um BUG REAL no host — um estado vazio salvo gravava ZERO linhas, a
    geração ficava invisível e o plan ainda via o mundo antigo (a MARCA `res ""` agora
    persiste a geração vazia; os goldens `MakealiveDbHost/DbState` intactos). Paridade de
    bytes JVM==JS (KofJsRunner in-process + tee, igual à 2.6 js); recusas honestas
    script/native; contrato do driver JDBC inalterado (o runner traz o driver; h2 é
    dependência de TESTE do kof-cli exatamente como no kof-compiler). Prova:
    `CmdMakealiveTest` 7/7 + `MakealiveMaxGenE2ETest` 4/4 + bateria Makealive 14/14.

  - **known-bugs §381 CORRIGIDO — um campo de `entity` com nome PALAVRA-RESERVADA OOMAVA
    o compilador** (20/09, `.18` via decisão do maintainer — erro limpo, sem mudar a
    gramática): o field loop de `parseEntityDeclaration` chamava `expectId`, que reporta
    SEM consumir, então `entity E { val: String }` fazia progresso-zero alocando um
    diagnóstico + um node de campo por iteração até o heap morrer (medido: -Xmx256m, ~2s).
    Fix: nome de campo não-IDENTIFIER reporta `Expected field name in entity` (PARSE024)
    uma vez e consome o token ofensor (recuperação de pânico clássica) + trava de progresso
    por iteração. Prova: novo `EntityKeywordFieldE2ETest` 3/3 rodando em SUBPROCESSO no
    budget original de 256MB (RED: o filho morre em OOM; GREEN: diagnósticos limitados,
    entities válidas compilam) + bateria parser/makealive 272/0F.
  - **`kof workflow run --target js` suportado (fatia residual da 2.6 / R7)** (20/09,
  - **Ferramentas de identidade de release (sem depender de decisão da mantenedora — gates já ratificados, `EXIT GATE` §32.6/§32.7/§35):**
    `scripts/verify-release-identity.sh` confere uma release publicada/diretório (`SHA256SUMS` × digest que o GitHub
    registra, cobertura do arquivo; `--tested <sha256>` = o "digest testado == digest publicado" ratificado, reportado
    como `NOT_RUN` — nunca PASS — quando não informado); `scripts/release-evidence.sh` é o manifesto de evidência por alvo
    (8 alvos Stable GREEN na mesma SHA candidata; RED/SKIP/NOT_RUN nunca verde; digest do pacote testado obrigatório);
    `scripts/check-reproducible-build.sh` compila o jar do `kof-cli` duas vezes em caminhos limpos diferentes e compara os
    digests. **Mudança de build:** `project.build.outputTimestamp` agora é fixo no `pom.xml` raiz — o jar fica byte-idêntico
    entre builds limpos e caminhos (RED antes: dois digests diferentes; GREEN depois: `30e2bca1…` nos dois). Testes offline
    dos dois primeiros estão na suíte de agentes que o CI já roda. Evidência:
    `docs/audits/supply-chain-trust-boundary-2026-09-20.md` §8.

    `.18`): o CLI agora compila o `pipeline(): KofWfDag` para JS e roda
    **in-process** via `KofJsRunner`, com paridade de BYTES com a JVM
    (`CmdWorkflowTest.runJsTargetFacesJvmBytes`). O stdout do guest alimenta a
    captura do protocolo `@@KOF_WORKFLOW@@`; o stderr vai em tee para o terminal E
    para o buffer (espelha o pipe mesclado da JVM, entao as faces de
    relato/progresso aparecem nos dois targets); a decisao de exit usa `truthy`
    tolerante porque o `allOk()` host do JS e numerico pelo contrato de bool da
    §382 — mesma decisao, mesmos bytes. `--target native`/`script` seguem recusados
    com a mensagem de follow-up. Prova: `CmdWorkflowTest` 12/12 (3 testes js novos,
    RED-first) + bateria 79/0F (`WorkflowE2ETest`, `KofJsE2ETest`,
    `IoBoolFacesE2ETest`, pump).

  - **known-bugs §391 CORRIGIDO — #568: o construtor IMPLÍCITO de classe EXTERNA
    (`Greeter()` sem `new`) via `--classpath` disparava `SEM015` FALSO**
    (20/09, lane compilador `.22`): `kof build ... --classpath producer.jar` com
    `Greeter("producer").greet("consumer")` imprimia `Undefined function: 'Greeter'`
    embora o artefato saísse correto e rodasse. O §134 cobria só a chamada estática
    e o `new Greeter()`; o `Greeter()` puro caía no resolver de funções e nunca
    consultava o `ExternalClasspath`. Fix nos dois lados: o `TopLevelCallTyper`
    resolve o `<init>` externo (sem SEM015 falso) e o `ExpressionBareCallLowerer`
    emite `new` + `<init>` com o descritor REAL do classpath. Prova:
    `ExternalClasspathE2ETest` 9/9 (RED-first; caso do relator + negativo R6) e
    vizinhança 101/0F. Defeito (ii) do #566 (`1.0-blocks`).
  - **known-bugs §387 CORRIGIDO — o pump JS do host declarava quietude com a
    promise do top-level-await do módulo ainda pendurada** (20/09, `.18`): um
    `async main` pendurado num `await` simples (sem sleeper registrado ainda —
    a janela que o makealive-3.3 abriu ao assíncronizar o dispatch do job)
    devolvia rc=0 com stdout VAZIO, a classe silenciosa §255; deterministic
    1/1 no método isolado. Fix em `KofJsAsyncPump`: quietude exige TAMBÉM o
    promise do módulo settled (callback host via `then`; namespaces sem TLA
    seguem settled, rejects do guest continuam estourando no eval — medido);
    30s de starvation sem timer vira falha ALTA, nunca rc=0 vazio. Prova:
    `retryFacesBothOutcomes` GREEN 6,9s, bateria 132/0F/0E (workflow/KofJS/
    Makealive/async/sleep/cron/timer/io).
  - **known-bugs §382 CORRIGIDO — o host JS devolvia as faces BOOL do kof.io
  - **#566 — pacotes publicados com `kof deploy --publish` agora são consumidos como módulos-FONTE**
    (opção (b), decisão da mantenedora 20/09, adendo do `D-RELEASE-0.5.0-GATE`; sem mudança de
    sintaxe nem de semântica): (1) o compilador resolve `import` também nas raízes de fonte das
    dependências instaladas (`CompilerDriver.setDependencySourceRoots`), depois do módulo local e
    das bibliotecas oficiais — uma dependência nunca sombreia a biblioteca padrão — em todo alvo;
    (2) `kof deps resolve` instala as fontes do pacote, cada uma verificada contra o `SHA256SUMS`
    (`REG002` se adulterada; `REG004` se não listada, listada e ausente ou que não seja
    `.kf`/`.kof`; nada é instalado em caso de falha), e `kof run|build --deps` entregam essas
    raízes ao compilador; (3) o `kof deploy` leva as fontes do módulo (`src/…`, sem `tests/`,
    ocultos nem diretórios de saída) e aceita uma **biblioteca** (módulo só com árvore de pacotes e
    nenhuma fonte no topo), que é compilada para validar e publicada só como fontes. **Mudança de
    contrato de propósito:** o tar.gz do deploy deixou de ter 3 entradas — as fontes viajam entre o
    artefato e o `RELEASE.md`. Pacotes publicados antes (só jar) continuam funcionando (jar no
    classpath). Uma dependência com fontes é consumida por elas, então `Classe()` sem `new`
    funciona para suas classes. Prova: `DependencySourceRootE2ETest` 7/7,
    `DepsSourceModuleTest` 8/8, `CmdDeploySourcesTest` 5/5 (inclui o ciclo completo deploy →
    registry → `kof run --deps`).

    como o NUMERO 0/-1** (20/09, `.18`): um `writeText/appendText/writeBytes/
    appendBytes/writeFile` bem-sucedido saía `false` no guest (0 = falsy) com o
    arquivo NO DISCO e rc=0 (a classe silenciosa §255); `delete/dirDelete`
    colapsavam miss/sucesso/IOException num 0/-1 indistinguível. Fix no
    `KofJsRunner` (host = onde os números nasciam): as cinco faces tipadas BOOL
    devolvem o booleano real — a família delete propaga o
    `Files.deleteIfExists` (miss = false como JVM/Script, IOException = false —
    faces de falha MEDIDAS no JVM/Script hoje), a família write `true/false`.
    As faces numéricas ficam números por contrato (`size`, `readText`,
    `exitCode`, format, `db.execute` = INT, e o `writeFile(p,c)` top-level = rc
    INT — as antigas linhas "dbExecute"/"writeFile" da Surface do entry
    corrigidas). Prova: golden `IoBoolFacesE2ETest` 13 linhas JVM==JS
    byte, RED no JS sem o fix; as baterias makealive fs/reconcile intactas (o
    workaround exists() delas é legal e fica); faces de bytes provadas via
    `new Int[n]` — o crash de coerção `listOf` é do §388 (lane JVM/codegen),
    não daq

  - **#564 CORRIGIDO — `kof deps` agora baixa um pacote de uma Release REAL do GitHub** (20/09):
    o `DepsRegistry` cortava cada asset da release na primeira `}` e procurava uma chave
    `download_url` que a API do GitHub não tem (o asset real aninha `uploader{…}` e expõe `url` /
    `browser_download_url`), então todo `owner/repo[@ver]` real falhava com `REG002 … has no
    .tar.gz asset`; a suíte só ficava verde contra um mock plano. O JSON da release agora é lido
    com o `Json.parse` estrutural do CLI; o binário sai do `url` do asset (API) com
    `Accept: application/octet-stream`, `User-Agent: kof-cli` e `X-GitHub-Api-Version` pinada; um
    redirect para o host de armazenamento é seguido sem enviar o token a ele. `SHA256SUMS`
    continua obrigatório e a seleção exato → `-jvm` → primeiro `.tar.gz` não mudou. Prova:
    `DepsRegistryTest` 13/13 no shape real do GitHub (11 estavam RED antes do fix) + round-trip
    real contra uma release pública (sem token, HOME limpo).

  - **#565 CORRIGIDO — fat jars JVM não embutem mais uma cópia truncada de `kof-app.jar`
    dentro de si mesmos** (20/09): o `CmdBuild.buildFatJar` criava `classesDir/kof-app.jar` e
    depois percorria `classesDir`, então o output — ainda em escrita — virava um dos próprios
    inputs (e, num rebuild, o jar anterior também). Afetava `kof build --fat` e todo pacote de
    `kof deploy --target jvm` / `--publish`. O jar agora é montado num arquivo de staging fora
    de `classesDir`, o path final exato é excluído da varredura e só é substituído depois de
    fechado; em falha o staging é apagado e o jar anterior fica intacto. Path público,
    `Main-Class`, precedência app-first e recusa não-JVM inalterados. Prova: regressões
    estruturais em `CmdBuildFatTest` (build 1, rebuild no mesmo `classesDir`, rebuild que
    falha) e no jar distribuído do `CmdDeployTest`.

  - **known-bugs §380 CORRIGIDO — `if` NESTADO cujo then termina em `throw` não
    rouba mais o false-label do `if` ENVOLVENTE no JS** (20/09, `.18`): dump de IR
    confirmou que o if-IR de throw-then é LABEL-ONLY (forma do §147) — o parse do
    else interno encontrava o `falseLabel` ENVOLVENTE como "label final" e o
    consumia, engolindo o epílogo externo; o caminho não-throw devolvia
    `undefined` com node rc=0 (a classe silenciosa do §255). Fix cirúrgico: pilha
    de parsing dos falseLabels ATIVOS (`MethodCtx.enclosingIfFalses`) — label de
    estrutura envolvente é devolvido, nunca consumido; guardas de loop/try
    intactas, então §147/§149/§174 (assert-dentro-de-while, end de try) seguem
    funcionando — era exatamente o que a tentativa revertida (`∪ exits`) quebrava.
    Prova: golden de matriz 4-motores `conformanceNestedIfThrowStealsFalseLabel`
    (RED sem o fix, GREEN = oracle JVM byte a byte), caso de origem
    `MakealiveE2ETest.failedApplyKeepsStateAndNamesTheResource`, baterias de
    preservação 142/0F/0E + 61/0F/0E; suíte completa verde no push.


  - **EXIT GATE 1.0 — EG-3: `scripts/test-package-outside-repo.sh`** (20/09, `.18`):
    a condicao §12/§23-9 virou um comando repetivel — o tar.gz REAL extraido para
    diretorio limpo em `$HOME` (regra 9 do repo), env do repo desexportado; `kof
    version`/`info`/`new` + template `Hello, Kof!` rodando em **jvm, script, js e
    native (ELF x86-64)**, cross = guarda honesta de build-only (o exec pertence a
    matriz final, item 10), e a **lib oficial pura-Kof** (`kof.pdf` de
    `lib/kof-libs`) resolvida FORA do repo — exatamente a classe de bug que o #550
    expoe. PASS medido no kof-0.4.7-beta. Prova RED-first offline na suite de
    agentes (`scripts/tests/test-package-outside-repo-test.sh`, registrada no
    `run-agent-tests.sh`); no dia do RC o re-run no mesmo candidato e um comando.
    Preflights honestos: JDK 25/node/toolchain ausentes falham alto, nunca falso-verde.
  - **Fase 7 do DAP — `pause` + `setExceptionBreakpoints`** (20/09, lane
    tooling/debug): o adaptador da JVM agora pausa um programa em execução (JDWP
    `ThreadReference.Suspend` em toda thread de USUÁRIO — as threads `JDWP*` do
    próprio agente são puladas: suspender a thread de transporte congela o
    protocolo, medido) e arma exception breakpoints (evento Exception kind 4 +
    modificador `ExceptionOnly` 8, `caught`/`uncaught`). O adaptador Native ganhou
    as mesmas duas faces: `pause` = `-exec-interrupt --all`; `setExceptionBreakpoints`
    = breakpoint em `kof_throw_string` (a cadeia de throw do próprio runtime Kof,
    não exceções C++, então o catch-throw do gdb não se aplica) com o refinamento
    caught/uncaught sendo `verified:false` honesto (só do JVM; um pedido de uma
    face só — `caught` OU `uncaught` — é recusado, nunca um filtro que estoura
    silenciosamente a outra face). Prova: `KofDebugJvmExceptionTest` 2/2 +
    `KofDebugNativeDapTest` 5/5 (pause→reason `pause`; recusa de face única; as
    duas faces armam o breakpoint em `kof_throw_string`, provado no log MI do
    stub), cluster de debug 21/21. No caminho, um frame sem debug info (`Thread.sleep` nativo) abortava o
    `stackTrace` inteiro com `NATIVE_METHOD` (511) — o cliente JVM agora reporta
    esse frame como `?`/linha -1 e mantém os frames Kof; uma sonda
    `Method.VariableTable` (6,2) descartada no `methodName` foi removida.

  - **§385 corrigido — o `LocalVariableTable` do JVM agora começa cada local no
    SEU PRIMEIRO STORE** (20/09, `LocalVariableTableScopesTest` 2/2, javap real):
    o table carregava toda entrada com `Start=0`/`Length=<método>`, então um
    local declarado na linha parada era "visível mas sem valor" e o
    `StackFrame.GetValues` do JDWP derrubava o lote inteiro com `INVALID_SLOT` —
    os locais do DAP voltavam vazios. `JvmBackend.emitMethod` agora visita um
    label imediatamente após o primeiro store (`KofStoreLocal`/`KofCatchStart`)
    de cada slot e emite a entrada a partir daquele pc (parâmetros — sem store —
    mantêm `debugStart`; `Long`/2 slots verificado). Atributos só de debug:
    semântica de execução intacta; o retry por slot em `JdwpValues.locals`
    permanece como defesa honesta. Prova red-then-green no `javap -v` e o
    cluster de debug 12/12 com a tabela corrigida.
  - **Paridade do DAP JVM — `next`/`stepIn`/`stepOut` + `evaluate`** (20/09, lane
    tooling/debug): o adaptador de debug da JVM agora faz step (evento JDWP
    `SingleStep` kind 1 + modificador Step kind 10, size LINE, depth over/into/out)
    e avalia, igual ao DAP Native que já tinha os dois (X7-4). O `evaluate` resolve
    o **nome** de um local do frame; o JDWP não tem avaliador de expressão, então
    qualquer outra expressão é `success:false` honesto nomeando a limitação —
    nunca um valor inventado. Prova: `KofDebugJvmStepTest` 3/3 — conversa JDWP real
    (break na linha 7 → `evaluate x` = `1` → `next` para na linha 8 → `stepIn`
    entra em `add` → `stepOut` volta para `main`; um local recém-declarado é
    recusado honestamente). No caminho, a verruga `Start=0` do
    `LocalVariableTable` do backend JVM foi medida e catalogada (§385) e o
    `JdwpValues.locals` ganhou fallback por-slot: um local ilegível é omitido,
    nunca inventado (R6).

  - **makealive 3.3 — `reconcile(design, provider, intervalMs)`** (20/09, `.18`):
    o laço de convergência sobre `scheduler.every` (cada tick roda `apply` dentro
    de um `spawn` — mesma forma CONC003-JS-01 do `schedule` do workflow; parar =
    `scheduler.cancel(jobId)`, sem faces novas). Ticks podem se sobrepor e isso é
    seguro por construção: a idempotência vem do READ do provedor, `job.last` é só
    dica. SEM stub no Native — correção medida do plano (o CRON001 gateia
    `scheduler.at`, nunca o `every`: SCHED001 fechado cross 05/09). Provado por
    `MakealiveReconcileE2ETest` 1/1 (JVM==JS byte: o guard do intervalo recusa
    `<= 0` nomeando `intervalMs`; o laço converge o mundo sozinho; após
    `cancel`+`destroy` o mundo fica morto por 4 intervalos com o contador de
    escritas congelado — sem cancel o tick recriaria) x3 serial + bateria
    Makealive* 21/21. Lição medida no caminho: o pump JS é cooperativo (§132) —
    um golden deve ESPERAR com um `time.sleep` longo, nunca com polling curto
    dentro de `while (a && b)` (competia com o pump, morria silente 2/3).

  - **R2 fatia 1 — `libm` agora é linkado POR USO no Native x86 (capability/link-by-use generalizado)**
    — programas plain carregavam um NEEDED `libm.so.6` por causa do shim `call pow`
    do monolito, mesmo sem usar pow; o shim agora se declara `.weak pow`
    (RuntimeMath), o link fecha sem libm, e `-lm` só entra no ld quando a fonte
    realmente chama `math.pow` (scan `usesPow`, o mesmo padrão de
    `usesDb`/`usesMysql`/`usesConcurrency`; kof_math_pow` é o único caminho ao
    shim). Ternário morto no `NativeAssembler` (sqlite igual nos dois ramos)
    removido junto. Prova no artefato real: `LinkByUseTest` 3/3 com
    `readelf --dynamic` — plain liga SÓ libc (sem libm/libsqlite3/libmariadb/
    libpthread); sqlite/pthread/libm continuam ligados por uso; a saída do
    binário pow bate byte a byte com o oráculo JVM (golden medido). Faces
    cross/JS/JVM já eram by-use (scan `needsSqlite` #431, delegação ao host,
    class-loading lazy) — a linha R2 do tracker vira ✅.
  - **Fatia 3.8a da ABI de struct na FFI — `AbiLayout`, o engine de layout/classificação medido**
    — engine puro (`kof-compiler` `AbiLayout`) que, dados os campos escalares de
    um struct e uma ABI alvo (`SYSV_X86_64`/`AAPCS64`/`RISCV64`), devolve o
    size/align/offsets C e as classes de registrador em que o valor é passado.
    Não binda nada e não decide nada de D6-1..D6-5; é o substrato que 3.8b/3.7
    vão consumir. O golden é medição real (GCC 13.3 em x86-64/aarch64/riscv64,
    14 shapes × 3 ABIs) e é reprovado ao vivo com `_Static_assert` contra os três
    compiladores (`AbiLayoutTest` 3/3). A medição corrigiu a prosa riscv da spec:
    o LP64D faz *flatten* de struct ≤2 campos (FP→`fa0/fa1`, inteiros em
    `a0/a1`), não empacota tudo em doublewords inteiros.

  - **makealive 3.1 MK-1 providers — fs, CLI e REST como corpos de usuario, goldens JVM==JS**
    (20/09, `.18`): tres formas de provedor executaveis sobre as interfaces sistemicas
    genericas. `MakealiveFsProviderE2ETest` — mundo em disco via `File` de `kof.io`
    (apply grava `k=v` reais, idempotencia pelo READ do provedor, destroy apaga; dirs
    por motor; o workaround do §382 = efeito-e-verificacao: `f.writeText(x); return
    f.exists()`). `MakealiveCliProviderE2ETest` — mundo via `run` de `kof.shell`
    (existencia = `test -f`/`test ! -e` pela mesma interface; programa ausente =
    `exitCode -1`, nunca throw). `MakealiveRestProviderE2ETest` — servidor HTTP KV real
    (loopback, mesmo processo): `http.get(url)` devolve o BODY como String, 404 = corpo
    vazio SEM throw (falha de conexao da throw nos dois motores — paridade honesta), a
    existencia passa pela sonda separada `http.status(url)`. CLI/REST rodam JVM primeiro
    e JS SEGUNDO contra O MESMO mundo compartilhado — idempotencia por READ cross-engine
    provada byte a byte, e os programas identicos comparam byte (fs/CLI/REST goldens
    1/1 cada, bateria Makealive 20/20 no tip).
  - .18 - GAPS-DB F1b (20/09): `orm.count<User>(db)` é REAL no Native x86-64 — `kof_orm_count` monta `SELECT COUNT(*) FROM "table"` com o builder privado de KofString e roda via `sqlite3_exec`; o callback converte `argv[0]` NO LUGAR (o sqlite libera as strings do valor quando `exec` volta — ponteiro salvo lá é lixo) e o resultado sobrevive num slot estático. Dois bugs latentes morreram no caminho: (a) o epílogo do `delete_all` da F1a salvava slots sobre o `%rbx` gravado (o teste do throw passava só porque o chamador nunca relia `%rbx`); (b) o `count` novo estourava ao fazer pops na região errada da pilha após a injeção `andq $-16,%rsp` do emitter (SIGSEGV) — epílogo agora espelha `delete_all` exatamente. Prova medida: `KofOrmE2ETest` 37/0F (paridade byte JVM==Native de count, incl. três chamadas em sequência na mesma moldura); suíte 4-módulos 3185/0F/0E no clone isolado.
  - .18 - GAPS-DB F1c (20/09): `orm.migrate(db, name, sql)` é REAL no Native x86-64 — `kof_orm_migrate` espelha o host: DDL da tabela de histórico, `SELECT name = ?` (prepare/bind/step), a sql do usuário pelo executor compartilhado e `INSERT (name, ms)` com `clock_gettime` via syscall cru (mesmo padrão dos spans de observabilidade). A fatia também normalizou o contrato de pilha da stack ORM inteira: todo call para C agora acontece com `rsp % 16 == 0` (a convenção `andq` do emitter reafirmada: entrada `≡0` + `andq` + `subq $56/$88`), `.Lorm_exec` perdeu o `subq $8` acidental e o literal do DDL virou KofString de verdade (`.Lorm_exec` recebe objetos e soma o header de 24 — um `char*` cru fazia o sqlite parsear `ts user (id INTEGER...`). Prova medida: `KofOrmE2ETest` 39/0F (idempotência do migrate + linhas do histórico + paridade do throw de id ruim, JVM==Native byte); suíte 4-módulos 3187/0F/0E no clone isolado.
  - .18 - GAPS-DB F2b (20/09): `orm.find<T>(db, key)` row-object de LEITURA REAL no Native x86-64 — nova fatia `RuntimeOrm5`: `SELECT * FROM "t" WHERE "pk" = ?` com o key bindado (miss → `null` como o host `rows.isEmpty() ? null : rows.get(0)` — NÃO é throw) e o record CONSTRUÍDO dentro do runtime: o resolver novo do lado do programa `kof_orm_ctors` (`NativeOrmCtors`, emitido por entidade usada com find) mapeia o literal className para `(vtable, typeId, totalSize)` — constantes que um slice não conhece — e depois `kof_alloc`+`kof_init_object`+preenchimento campo a campo. Colunas casadas por NOME (comparação byte, paridade com o host) e lidas por typeCode: int via `column_int`+`movslq` (slot 8B), long via `column_int64`, string via `make_string` (SQLITE_NULL → null), double via `column_double`, float via `cvtsd2ss`+slot 4B, bool via `column_text` equalsIgnoreCase "true" — reproduzindo o oráculo MEDIDO JVM+sqlite (`kof_json_bind(boolean)` faz `Boolean.parseBoolean(String.valueOf(v))`: INTEGER 1 → "1" → false; TEXT "true" → true). Dois fatos que a prova gdb pegou: o call-site nativo coage um key primitivo para OBJ via `kof_long_to_string` — o key CHEGA como KofString (bind TEXT + affinity do sqlite casa a pk INTEGER; boxes/outras formas seguem cobertas pelo unwrap §284) — e `println(find-miss)` sobre um ponteiro de record é SEGV no Native (a JVM imprime "null"): o narrowing `if (g == null)` funciona, println cru de record null é o §397 NOVO (arquivo de outra lane, catalogado honesto, sem enfraquecer o teste). Prova: `KofOrmE2ETest` 43/0F/2skip (`findNativeEndToEndMatchesJvm` byte-parity: hit/miss-null/2a-linha/update-lido-de-volta + pin `allWhereFacesRestantesNativeAindaOrm001` migrou para `all`, F2c).
  - .18 - GAPS-DB F2a (20/09): `orm.save(db, obj)` row-object REAL no Native x86-64 — nova fatia `RuntimeOrm4` reproduzindo o contrato do host `JvmOrmRuntime.kof_orm_save` sobre a pilha `kof_db_*`: (1) pk null/numérico-0 → `INSERT` sem a coluna pk + `SELECT last_insert_rowid()` como prepared statement (paridade com getGeneratedKeys do host) → devolve instância **NOVA**: `kof_alloc` + clone de header (typeId/vtable da origem) + `rep movsq` dos slots + rowid patchado no slot do pk; (2) UPDATE com acerto (`sqlite3_changes > 0`) → devolve a MESMA instância; (3) UPDATE sem acerto → INSERT-all (upsert). Os binds passam por `RuntimeOrmSchema.kof_orm_parse_schema` (literal de compile-time → tabela de campos: name/len/typeCode/flags + pkIndex) e pelos helpers do `RuntimeOrmBind` (`.Lorm_bfld` por typeCode; KofString `bind_text` transiente; null → `bind_null`). Dois bugs que a prova gdb pegou: o cursor do parser de schema (`addq %rcx,%r12` sobrescrevia o ponteiro do corpo com o do fim → nFields=1 → DDL vazio; corrigido com `leaq (%r12,%rcx),%r13`) e a instrução do rowid montada como `.ascii` — sem NUL, o `prepare_v2` (nByte=-1) lia `SELECT last_insert_rowid()sqlite: H1...` e falhava, o pk ficava 0 e o segundo save INSERTava (violação UNIQUE); agora `.asciz`. `NATIVE_F1` ganhou `kof_orm_save`; o pin ORM001 da era F1 virou `saveCompilesOnNativeAndJs` e um novo `saveNativeEndToEndMatchesJvm` cobre os três caminhos byte-idênticos à JVM (incl. `User(9,...)` INSERT-all aterrando id=9). O pin de faces restantes migrou para o `find` (leitura row-object, F2b). Prova: `KofOrmE2ETest` 42/0F/2skip; suíte 4-módulos 3262/0F/0E (clone isolado `/tmp/k4jgate/full-f2a.log`); docs EN+PT sincronizados (parity, tracker 1.1.9).
  - .18 - GAPS-DB F3a + fix F1d (20/09): `orm.count<User>(db, campo, valor)` REAL no Native x86-64 — nova fatia `RuntimeOrm3`: `SELECT COUNT(*) FROM "t" WHERE "f" = ?` com o valor bindado por `prepare_v2`/`bind_*` pelo tag do box §284 (int via `movslq` — paridade de sinal com o Integer do JDBC; long/bool/double/float; KofString → `bind_text` transiente; null → `bind_null`; forma estranha → lanço honesto, nunca lixo). Dois bugs que a prova pegou: (1) esta fatia nasceu com `cmpl $101` para `SQLITE_ROW` — a API real é **100=ROW / 101=DONE** (a string de erro "another row available" entregou); (2) `sqlite3_finalize` pisa `%rax` — o count saía certo e a função devolvia 0 (resultado agora estaciona em `%r13`). Retro-fix da F1d que a mesma sessão gdb expôs: o loop de flags do `RuntimeOrm2` caía no scanner de tokens com o cursor já após o `':'`, e o `badtok` comia `:generated`/`:unique` letra a letra — o DDL emitido perdia `INTEGER PRIMARY KEY AUTOINCREMENT` e ` UNIQUE` (e o golden antigo nunca olhava o texto salvo). O golden de create agora relê a linha `sql` do `sqlite_master` e casa byte a byte com o host (incluindo o espaço antes do `(`). Prova: `KofOrmE2ETest` 41/0F (goldens count_where: acerto, numérico, ausente, injeção, negativo; create: texto do DDL); suíte 4-módulos 3261/0F/0E (clone isolado, base pós-rebase 4e71aae2).
  - .18 - GAPS-DB DB-2 (20/09): over-gating `kof.db`/`kof.orm` do Android (§278) FECHADO — `KofDb`/`KofOrm.supportedOn` aceitam `ANDROID` ("Android É JVM": o alvo reusa o `JvmBackend`). A prova é paridade por construção: `KofDbE2ETest.androidDbEmitsTheSameBytecodeAsJvm` compila entity+create+count nos dois alvos e afirma `Main.class` byte-idêntico. O pin R6 virou (`androidCompilesDbLikeJvmAndRefusesCryptoWithTheDocumentedCode`: db limpo, `SECN003` ainda recusado). `SECN00x`/`GPU001` seguem abertos — aquelas pilhas não rodam no Android (regra 6). §278 → PARCIAL; matriz + KOFANDROID + tracker 1.1.10 atualizados no mesmo commit (EN+PT).
  - .18 - GAPS-DB F1d (20/09): `orm.create<User>(db)` é REAL no Native x86-64 — nova fatia `RuntimeOrm2`: `kof_orm_create` faz parse byte a byte do literal de schema compilado (`name:dbType[:generated][:unique]`) e reconstrói o DDL exato do host (`"col"` VARCHAR(255)/INTEGER/BOOLEAN/DOUBLE/REAL + UNIQUE, generated → `INTEGER PRIMARY KEY AUTOINCREMENT`) pelo builder compartilhado de KofString; `CREATE IF NOT EXISTS` duas vezes devolve `true` nos dois motores. O pin de ORM001 de compilação migrou para `save` (a face row-object, F2). Prova medida: `KofOrmE2ETest` 40/0F (paridade byte JVM==Native de create, incl. um `select email from user` real provendo que UNIQUE/varchar chegaram); suíte 4-módulos 3188/0F/0E no clone isolado.
  - .22 - #555 CodeQL (cluster do compilador, 22 alertas): os E2E que disparam `javac`/`java`/
    `node` como subprocesso montavam o comando com concatenação de string ou nome relativo
    (`java/concatenated-command-line`, `java/relative-path-command`). Conserto na fonte com o
    helper `TestJdk` (caminho ABSOLUTO via `Path.of(javaHome, "bin", "java")`, sem `+`;
    `onPath()` resolve `node` pelo PATH e ERRA honesto se não existe — nunca relativo
    silencioso). Zera também #887/#889 (KofTimeE2ETest: local morto + @Override), #876
    (SemanticAnalyzer: caso-`_` no-op removido, `default` já cobre — comportamento idêntico),
    #890/#908 (parâmetro/local mortos removidos) e #907 (overload privado `isTroolean(
    NullableType)` renomeado `isTrooleanNullable` — a overload confusa fazia display/describe
    em :277/:309 resolverem o PRIVADO por tipo estático).
  - .22 - #555 #918/#919/#940 + paridade JVM: `KofJsProcessBridge.kill()` removia os
    readers/writers do mapa SEM fechá-los — o pipe do filho ficava vivo nos fds da JVM até o
    GC (resource-leak REAL, não suprimido: close na face do kill). O espelho JVM
    (`JvmRuntimeCore.kof_spawn_kill`, template) tinha o MESMO vazamento — corrigido junto
    (regra 5, paridade por construção). Local morto `result` removido de `execute()` (#940).
  - .18 - GAPS-DB F1a (20/09): `orm.deleteAll<User>(db)` é REAL no Native x86-64 — `kof_orm_delete_all` em asm sobre o stack `kof_db_*` (sqlite; MySQL lanca ORM001 honesto em runtime; id ruim lanca a string exata do host); gate por-função `fnSupportedOn` (todas as outras faces e o cross mantem `ORM001`); fix de link: programa só-ORM agora puxa `-lsqlite3`. Prova medida: `KofOrmE2ETest` 35/0F paridade byte JVM==Native; suíte 4-módulos 3183/0F/0E no clone isolado.

  - **X9 fatia 6 — `kof deploy` empacota as faces cross (recusa preventiva DEP001 saiu)**
    — `--target native.riscv64|native.aarch64` roda o MESMO pipeline de release do
    native x86: ELF cross (`Default/Main`, 0755) + `RELEASE.md` (run hint `./artefato`,
    não `adb`) + `SHA256SUMS` + `.tar.gz`; multi-target e o manifest de falha parcial
    tratam o cross como qualquer face. Sem toolchain o deploy FALHA honesto nomeando a
    ferramenta (R6: ele tentou de verdade — a recusa `DEP001` antiga nunca invocava o
    emissor). Novo override da casa `KOF_CROSS_PREFIX` (padrão `KOF_GDB`) redireciona
    `as`/`ld` de `riscv64-`/`aarch64-`; provado em qualquer host com ferramentas stub
    (`CmdDeployTest#crossReleasePackagesWithStubToolchain`,
    `crossDeployWithoutToolchainFailsHonestly`, falha parcial determinística com
    `emptybin`) e com binutils/qemu reais no job cross da CI. Tracker 8.4 → ✅, X9 fatia 6.

  - **Dívida CodeQL do cluster debug/tooling fechada na raiz (sem mudança de comportamento observável)**
    — os 12 alertas CodeQL abertos do cluster de debug da CLI foram corrigidos na fonte, não
    suprimidos: `KofGdbMi` usa `add` nas filas ilimitadas (o retorno ignorado de `offer`), remove
    o container `console` só-escrito e rejeita binário nulo com `IllegalArgumentException`
    explícita (sem NPE); `JdwpClient` deixa de nomear o `argWords` não lido (o `argCnt` do
    VariableTable é só framing); o caminho de attach do `KofDebugJvmSession` guarda `jdwp` nulo
    (breakpoint sem cliente vivo fica `verified:false`, nunca NPE) e remove o `frameVariables`
    nunca lido; `KofDebugNativeDap` faz o parse dos campos `line`/`level` do MI por um helper
    com fallback, então saída malformada do gdb nunca aborta a sessão DAP; e `KofDebugNativeTest`
    resolve executáveis pelo `PATH` (`Files.isExecutable`) em vez de spawnar um `sh` relativo;
    `ProfileMethodsTest` resolve o `node` do mesmo jeito. Provado pelo cluster de debug
    **14/14** (`KofDebugNativeTest` 7, `KofDebugNativeDapTest` 3,
    `KofDebugAttachTest` 3, `KofDebugJvmTest` 1), `ProfileMethodsTest` **5/5** e compile
    verde do `kof-cli`.

  - **#545/§362 — chamadas de construtor fantasma agora falham em tempo de compilacao (`57a0d5f0`)**
    — `P(1, 2)` em `record P(Int x)`, `D(1)` em classe sem esse construtor e `C("s")` em
    `constructor(Int)` compilavam "clean" e produziam `NoSuchElementException` de runtime no
    lookup `kof_new` (ou comportamento silenciosamente errado). O caminho de construcao
    implicita agora valida aridade e tipos dos argumentos: **SEM023** (sem construtor com N
    argumentos) / **SEM014** (tipo do argumento), no call-site, em todo alvo (gate de frontend).
    Formas legitimas intactas — medido: `Q(7)`, `E(9)`, `R(1, 2)` seguem compilando e rodando.
    Provado por `ConstructorPhantomE2ETest` 7/7.

  - **`kof profile --methods` — profiler de AMOSTRAGEM method-level interno (resíduo 8.3)**
    — a face JVM de profiling agora e real e auto-contida: o JVM filho grava
    `jdk.ExecutionSample` com o **JFR do proprio JDK** (`jdk.jfr`, sem ferramenta externa),
    e `kof profile --methods app.kf` imprime os metodos quentes com a **linha da fonte Kof**
    (o LineNumberTable do compilador mapeia o bytecode de volta ao `.kf`, entao o usuario ve
    a funcao Kof quente, nunca bytecode cru). O overhead do proprio sampler
    (`jdk.jfr.internal`) e filtrado; gravacao curta demais para uma amostra vira nota
    honesta, nunca lista vazia silenciosa. Native/JS sao recusas honestas nomeando a
    ferramenta deles (perf / V8-DevTools, R6/R7). Provado por `ProfileMethodsTest` 4/4
    (funcao quente real achada por linha + as duas recusas + o controle sem a flag).

  - **`kof profile --methods --target js` — a face JS do profiler de metodo interno**
    — o modulo emitido roda sob o **`--cpu-prof`** do proprio Node (parte do Node, sem
    ferramenta externa) e o **`.mjs.map`** emitido mapeia a linha JavaScript amostrada de
    volta para a **linha da fonte Kof** (o equivalente JS do LineNumberTable do JVM), entao
    o relatorio JS mostra a funcao Kof quente, nunca JS gerado. Internos do Node sao
    filtrados; host sem Node falha honestamente (nunca um profile falso), e o Native segue
    recusa honesta nomeando perf **e o `perf_event_paranoid` medido**. Provado por
    `ProfileMethodsTest` 5/5.

  - **#431 fatia 1 — a ABI escalar do `extern` agora VINCULA no Native x86-64 (`d946e6fa`, §369)**
    — `extern "<lib>" f(Int, Long, Float, Double, Bool, String)` com aridade livre,
    retornos void/String: link direto (a biblioteca entra no `ld`) + marshaling SysV
    por classe; Kof↔Native agora roda o mesmo programa byte-a-byte com a JVM
    (re-verificado pela lane docs com jar limpo reconstruido: `5` / `3.5` / `5` / `10`
    nos dois alvos). o cast explicito (`fmid(4.0 as Float, 9.0 as Float)` → `13.0`) sempre funcionou; `Int`/`Double`
    sem cast num slot Float/Double reinterpretava bits no Native (`3.0E-45`/`0.0`) —
    CORRIGIDO na entrada #549/§370 abaixo.
    Callback/struct/array no Native seguem honestos FFI001/FFI002. Provado por
    `FfiNativeE2ETest` 16/16 (+ `FfiE2ETest` 16/16 regressao JVM, 38/38 total).

  - **#549/§370 — argumentos de `extern` agora seguem a conversão numérica comum do Kof (CORRIGIDO 20/09)**
    — o call-site empilhava o argumento CRU e o marshaling SysV do Native lia os bits
    pela classe do SLOT: `fmid(1, 2)` num slot `Float` imprimia `3.0E-45`, `fmid(1.0, 2.0)`
    imprimia `0.0` (valor errado, sem diagnóstico) e `sqrt(9)` num slot `Double` era o
    mesmo lixo; a JVM lançava erro de cast `Double→Float`. `ExternArgumentCoercion` agora
    converte ao slot declarado com a MESMA regra de widening/`Double→Float` de qualquer
    chamada (`Int→Float`, `Double→Float`, `Int→Double`, `Long→Double`…), antes do
    marshaling/box; `String`/`Bool` em slot numérico e `Double→Int` seguem `SEM014`.
    JVM, Native e host JS imprimem igual (`FfiExternTypeConversionTest` 11/11; a bateria
    FFI de 10 classes é 94/0/0/0 antes E depois).

  - **#278/§361 — escritas de campo nullable-primitivo agora BOXAM no JVM (`e293c4a5`)**
    — `class Box { Int? n }` + `b.n = 42` gravava o inteiro cru no slot boxado
    (`VerifyError` na carga de classe em JVM/Script, erro de cast em execucao no
    Native, enquanto Script/JS imprimiam `42`). O gate do escritor agora usa
    `TypeMetrics.isNullablePrimitive` — o predicado preciso que o fix local do
    §295(b) estabeleceu — com a segunda camada de causa (`isPrimitiveType`
    DESEMBRECA nullables, entao o branch de widening puro precisa exclui-los ou o
    gate vira codigo morto). Provado por `NullablePrimitiveFieldWriterE2ETest` 9/9.
    **Status §361: FECHADO (`5cd078c1`)** — a face de escrita `Char?` nao era um bug
    de escritor proprio: a raiz era o store de campo nunca passar pelo gate de
    atributibilidade (§368). O gate agora rejeita `String → Char/Char?` (e todo
    store de campo nao-atribuivel) com SEM012 em tempo de compilacao, e o idiom
    legitimo — literal de char `y.c = 'x'` — roda verde nos 4 alvos (re-verificado
    20/09 com jar `kof-cli` limpo reconstruido). §365 (campo nullable nunca-escrito
    le `0` no JS vs `null` nos demais) foi corrigido a parte em `dd418419`.
  - **#278/§368 — o store de campo agora passa pelo gate de atributibilidade (`5cd078c1`)**
    — `x.n = "s"` em `Int n`, `y.c = "x"` em `Char`/`Char?`, `x.n = 2.5` em `Int`
    compilavam "clean" e morriam na carga de classe (JVM/Script `VerifyError`, cast
    no Native) ou viravam phantom-store (JS). `StatementAnalyzer.analyzeAssignmentStatement`
    reusa o mesmo gate `TypeChecker.isAssignable` da atribuicao local — SEM012 no
    call-site (R6, sem quebra silenciosa). Provado por
    `FieldAssignabilityPhantomE2ETest` 8/8 (baseline RED pre-gate).

  - **#551/§372 — o gate de escrita de campo do §368 nao engole mais o rio da erasure (`b321fcb1`)**
    — `5cd078c1` disparava o SEM012 novo ANTES do rio de erasure (§355-357) nos stores de elemento de
    array dos goldens `T[]`, virando 5 casos verdes (3F+1E + 1F) e mascarando o SEM098. O colateral da
    pilha makealive-3.1 roteia esses stores corretamente pelo `TypeChecker`; a lane docs reverificou a
    bateria inteira no tip limpo: `GenericFieldArrayEraseE2ETest` 5/5 + `MakealivePrimitivesE2ETest`
    8/8 + `FieldAssignabilityPhantomE2ETest` 8/8.

  - **#548/§367 — `println(result)` de um resultado de process/shell imprime por CONTEUDO, sem vazar identidade Java**
    — `process.run("echo","x")` + `println(r)` vazava a identidade crua do runtime
    (`dev.kof.runtime.KofRuntime$ProcessResult@<hash>`, hash diferente a cada execucao) em JVM e Script. O resultado
    agora imprime `ProcessResult[exitCode=0, stdout=x, stderr=]` em JVM, Script e no host JS (o mesmo formato que a
    ponte JS ja imprimia; Native recusa `process.run` com o gap honesto PROC001, intocado). Nenhum acesso a campos
    mudou (aditivo, freeze 2). Provado por `ProcessResultContentE2ETest` 4/4 — **RED 4/4 no tip limpo antes do fix,
    GREEN depois** — com `ProcessSpawnE2ETest` 4/4 e `ShellE2ETest` 16/16 verdes. O report irmao #547/§366 (Script
    perdendo o stdout do filho) foi medido **nao-reproduzivel** no tip atual (`x`/`0` nos dois alvos); a paridade
    JVM×Script byte-a-byte fica fixada como teste permanente na mesma classe.

  - **#554/§378 — `check_known_bugs_status.sh` agora cruza o CONJUNTO INTEIRO de §NNN EN×PT** —
    o gate comparava só os ABERTOS, então um FIXED existente em UMA única língua passava
    verde (o caso real §376/§377). Todos os headings são comparados com mapeamento de
    família de status + self-test com fixture (FIXED so-uma-língua → exit 1).

  - **#550/§371 — a CLI DISTRIBUIDA agora compila cross (riscv64/aarch64) de QUALQUER
    diretório** — o carregador da ordem de slices lia os fontes `.java` do runtime por
    caminho relativo ao CWD, então `kof build --target native.risc` morria em erro de
    sysroot fora da árvore do repo (todo E2E cross rodava via surefire com CWD=raiz do
    módulo — ponto cego). Fix: carga classpath-first das fontes dos slices
    (`RuntimeSourceLoader`), fallback a arquivo só em árvore de desenvolvimento. Provado
    por `RuntimeSourceLoaderTest` 6/6 + `ShippedCliCrossSmokeTest` 2/2 (CLI como
    subprocesso de um `@TempDir` FORA da árvore); a lane docs re-mediu AMBOS no tip limpo
    `aabd7bff` (0F/0E). O ticket §371 já pinava a raiz com o mesmo repro que o smoke roda.

  - **#443/§373 — `List`/`Set`/`Map` bare em posicao DECLARADA agora resolve para as colecoes builtin (`d969bc3a`)**
    — `class Box { List items }` + `items = listOf(1,2)` compilava "limpo" e morria no class load com descriptor
    fantasma `LList;` (`NoClassDefFoundError: List`): dois resolvedores para o mesmo nome declarado, so o caminho
    IR/`toType` normalizava. A normalizacao mudou para o UNICO ponto de convergencia — passo 2b do `qualifyDeep`
    (mecanismo do §179, guarda de shadow do §243 preservada) mapeando os nomes bare via `BuiltinTypes.declaredCollectionType`;
    classe homonima do usuario mantem o dono (controles provam os dois lados). Contrato congelado #139/#150/#214,
    nao semantica nova. Prova: `BareCollectionFieldE2ETest` 8/8 (RED 6/8 pre-fix); o print verbatim da `2` em JVM,
    Script, Native x86-64 e JS no jar limpo. A caca Q4 deste fix abriu a §374/#553 (arg primitivo em add/set de
    colecao bare nunca boxeia) — CORRIGIDA no mesmo dia (abaixo).

  - **#553/§374 — argumento primitivo em `add`/`set` de colecao BARE nao morre mais no LOAD da classe no JVM**
    — `List xs = listOf(1)` + `xs.add(2)` (local, campo ou `send` de `Channel` nu) boxeava o argumento pelo tipo de
    elemento DECLARADO, que e `Unknown` sem type-args no receiver: o `int` cru chegava em `ArrayList.add(Object)` →
    `VerifyError: Type integer … not assignable to 'java/lang/Object'` (o launcher do CLI mascara como a mensagem do
    JavaFX, §149 — o bug era invisivel, nao ausente). O fix espelha o precedente do bug-35 no MESMO arquivo:
    `emitBoxIfPrimitive` agora boxeia pelo tipo do ARGUMENTO no call-site (`parameterTypes`), guardado por
    `elemType instanceof UnknownType` — a emissao de colecao TIPADA fica byte-identica a antes, e o diagnostico de
    homogeneidade do runtime (regra 5) permanece intocado. Prova: `BareCollectionPrimitiveArgE2ETest` 7/7 (VERMELHO
    4/4 no baseline limpo: add local nu, add de campo nu, set nu, largos Long/Double; controles tipado/Set/Map verdes
    nos dois lados), mais a face Native x86-64 do mesmo programa e vizinhanca `BareCollectionFieldE2ETest` 5/5,
    `CollectionMethodsStdlibE2ETest` 47/47, `KofChannelTest` 14/14.

  - .18 - governança: **regra 11 (Lei da Simplicidade) é ABSOLUTA em AGENTS.md** + `DECISIONS.md` §D-MAKEALIVE/§D-KOF-AS-CLOUD/§D-BOOTSTRAP/§D-DB-GAPS (enquetes da mantenedora 20/09: namespace `kof.makealive`, providers genéricos completos, estado kof.db desde o dia 1, Android=paridade JVM no db, ORM no Native via asm `kof_orm_*`, MySQL no cross, bootstrapper = objetivo final).

  - **`shell.pipeline` REAL no JS (20/09, lane `.18`)** — fecha o último residual
    de pipes vivos da linha 2.2. Cadeia ProcessBuilder + threads de pump em
    `KofJsProcessBridge.processPipeline`, contrato espelhado do `kof_shell_pipeline`
    do JVM (primeira etapa com stdin `/dev/null`, demais PIPE, exit code do último;
    `no stages`/`empty stage`/erro de spawn = `Result(-1)` honesto, nunca exceção do
    host). Gate do lowerer reduzido a só-Native. **Prova:** `ShellE2ETest` 16/16 com
    o pin JS virado golden de paridade byte — cadeia de 2 estágios (`echo|wc -w`→3) e
    de 3 estágios multi-pump (`echo|tr|wc`→2). Linha 2.2 do tracker universal vira ✅;
    Native mantém o `PROC001` herdado (espera o `process.run`/spawn em asm).

  - **Face JS de `process.spawn` landada (19/09, lane `.18`) + §360 corrigido na raiz**
    — as ops de handle (`readLine`/`write`/`exitCode`/`kill`/`alive`) baixavam para um
    `invokevirtual java/lang/Long.readLine` cru: a branch `isHandle` morava atras de um
    dispatcher que nunca roteia receiver `Long`, entao **nenhum alvo as executou jamais**
    (os pins antigos só assertavam compilacao). Conserto de roteamento + binding de host
    `KofJsProcessBridge` (mesmo JDK/ProcessBuilder — paridade por construção: spawn falho
    `-1`, EOF `""`, sentinela vivo `Integer.MIN_VALUE`, kill=esquece); gate do lowerer
    reduzido a só-Native; `DomainGapCodesTest.processSpawnOnJs` virou PROC001→no-gap;
    prova `ProcessSpawnE2ETest` 4/4 paridade byte JVM==JS. Quirk honesto preservado: stdin
    do filho sob `/dev/null` → `write` publico e no-op nos dois alvos (entrada viva =
    mudanca de contrato, regra 6). `JsRuntimeOps` dividido: `JsRuntimeProcessShellOps`
    (gate 500, 577→537).

  - **`Bool` nunca e nulavel — o tipo tres-estado e `Troolean` (D-TROOL,
    19/09, DECISIONS.md §D-TROOL)** — migracao da mesma classe aprovada para
    #401: `Bool?`/`Boolean?` (qualquer posicao: local, campo, parametro,
    retorno) agora falham em compilacao com `SEM095` ("`Bool` tem dois valores;
    para true/false/desconhecido use `Troolean`") — a face antiga compilava mas
    era crash-face em runtime (#462/#486 VerifyError; JS vazando operandos).
    Codigo que quer `true/false/desconhecido` escreve `Troolean`: declaracao
    sem instancia e `null` (unknown), funcoes podem `return null` nele,
    `println` mostra `true`/`false`/`null`, e `!`/`&&`/`||` seguem as tabelas
    de **Kleene** (F domina AND, T domina OR, `NOT U = U`) — logico com lado
    `Troolean` da `Troolean`; consuma como `Bool` com `== true`/`!= null`
    explicito. Programas com operandos puro-`Bool` continuam intactos (face
    #487 preservada). Prova: `TrooleanLawE2ETest` (matrizes Kleene 9+9+3,
    cadeias aninhadas, curto-circuito dos dois lados, `== null`, acucar de
    condicao, `SEM095` nas duas grafias — JVM+Script+JS identicos;
    Native-x86-64 medido nos probes do landing) e as faces §306 migradas.
    Fecha a familia #462/#486 por decisao (regra 8: o substituto do constructo
    estrangeiro agora esta NA lingua).
  - **`kof.shell` 2.2.3 landado (19/09, lane `.18`)** — `shell.runWith(argv, cwd, env)`
    no JVM + host JS: ambiente **aditivo** (as chaves do map sobrescrevem as herdadas,
    nunca uma limpeza silenciosa), `cwd` `""` herda o diretório do processo, e erro de
    spawn / argv vazio devolvem `Result` **honesto** (`stderr` preenchido,
    `exitCode == -1`) — nunca hang, nunca sucesso silencioso (R6). No Native a face
    segue o `PROC001` de compilação herdado de `process.run`. **Prova:**
    `ShellE2ETest` 15/15 — goldens `pwd`/`printenv` com paridade byte JVM==JS, pins de
    falha honesta, pin do gap Native e pin SEM025 de forma errada. Docs
    stdlib/plan/parity/tracker sincronizados EN+PT; residual da linha 2.2 = só os
        pipes vivos do `pipeline` JS (item de plataforma `process.spawn`, à parte).

  - **Catálogo de assinaturas agora é 32/32 — `json` tem hover/signatureHelp**
    (fechamento X10, 19/09): `json.encode`/`json.decode` entram na tabela gerada
    (`encode(value) -> String`, `decode<T>(jsonString) -> T`), travados
    comportamentalmente na aridade real do typer (`MemberCallNamespaces` cobra 1 arg +
    o `<T>` do decode com SEM025 — a regra sempre existiu; faltava só a tabela). O
    dispatch por tipo segue no lowerer (`JsonDispatch`) — nenhuma semântica de
    linguagem mudou.
  - **Bundle 2.1.3 do `kof.workflow` COMPLETO (19/09, lane `.18`)** — retry +
    deadLetter (duas faces) + schedule + checkpoint (3a) + **supervisão (3b)**:
    `runSupervised(dag, nome, maxReinicios)` roda a DAG como workers one_for_one
    DELEGANDO ao `kof.supervisor` (cada job = child `transient`; o laço por filho
    reinicia só o que falhou; dependências = espera cooperativa em flags voláteis;
    limite estourado = drop + skip transitivo). Guardas R6 ALTAS: `maxReinicios < 1`
    recusado (restart ilimitado silencioso = storm de threads — a lição medida
    quando o host caiu 19/09) e `retry()` na mesma dag recusado (uma política de
    reinício por face). O host do supervisor vem injetado flat com dedup pela marca
    (import duplo seguro); a face é REAL nos 4 alvos (sem stub — núcleo OTP desde
    §129). **Prova:** `WorkflowE2ETest` 20/20 (paridade byte JVM==JS, pin Native,
    pin import-duplo) — dono = 192.168.100.18


  - **Igualdade de colecoes JS agora e por conteudo (`#518`)** — uma `List` ou `Set`
    Kof usada como elemento de outro `Set`/`Map`/`List` comparava por identidade no
    alvo JS (`add` dizia `true`, `contains` dizia `false`, `setOf(setOf(1)).size()`
    dava 2), enquanto a JVM compara por conteudo (`AbstractList`/`AbstractSet.equals`).
    O helper compartilhado `kofValEq` agora recorrre: arrays elemento a elemento (ordem
    importa) e sets por membresia — nunca via `Set.has`, cujo SameValueZero por
    referencia e exatamente o bug. Os tres helpers de igualdade (`kofValEq`,
    `kofRecordEq`, `kofFpEq`) sairam para um slice proprio do runtime. Prova:
    `KofSetEqualityTest.collectionsAsElementsCompareByContentOnJs` (antes desabilitado)
    e o gumeo JVM, 21/21 verde.

  - **`kof deps` agora consome o registry (`owner/repo[@versao]`, linha 0.4.0,
    1.5.3-S2 / D-POLL-19)** — uma linha como `acme/hello@1.2.3` (ou `acme/hello`
    puro = *latest*) no `kofdeps` resolve contra os GitHub Releases publicados por
    `kof deploy --publish`: o asset `<repo>-<ver>.tar.gz` é baixado, o `SHA256SUMS`
    embutido é **verificado antes de instalar** (integridade não é opcional) e o jar
    vai para o cache `~/.kof/deps/kof/<owner>/<repo>/<ver>/` — re-resolver é cache hit
    sem rede. O `latest` pinna a versão concreta no `kofdeps` após o primeiro resolve
    (lock-estável). `kof deps classpath` une os jars do registry ao fechamento Maven.
    Diagnósticos honestos (R6): `REG001` release não encontrada, `REG002` soma não
    confere / asset ilegível, `REG003` pacote sem jar, `REG004` pacote sem `SHA256SUMS`
    (recusa instalar). Repositórios privados funcionam com `GH_TOKEN`/`GITHUB_TOKEN`;
    os testes apontam o endpoint via `KOF_REGISTRY_API`.
    Prova: `DepsRegistryTest` (6 casos: caminho feliz + idempotência, pin latest,
    REG001/REG002/REG003/REG004 contra um registry fake).

### Em desenvolvimento

  - **`kof debug --dap --target native` — a ponte DAP<->GDB/MI para o editor (X7-4, roadmap §19.5 fase 7)**
    — o editor fala um unico protocolo com todos os alvos: os pedidos DAP (setBreakpoints,
    continue, stackTrace, variables, evaluate) são traduzidos para GDB/MI contra o ELF
    construído com DWARF; o `source.path` de cada frame e a fonte Kof (o `Main.kf`), nunca
    o asm. Gdb ausente = erro DAP honesto (`success:false` nomeando a ferramenta); evaluate
    de símbolo inexistente = erro do gdb repassado, nunca valor inventado (R6). Arquivos
    proprios pela regra 7: `KofGdbMi` (cliente MI minimo) + `KofDebugNativeDap` (sessao).
    Prova `KofDebugNativeDapTest` 3/3 com stub-MI (a conversa completa do editor, o caminho
    de tool ausente, as recusas honestas); gdb real exercitado na CI.
  - **`kof debug --target native` — gdb sobre o ELF Kof (X7-3, roadmap §19.5 fase 6)**
    — a frente de debug nativa existe sem a linguagem reinventar um debugger: o ELF é
    construído com o DWARF Kof completo (line table + DIEs, X7-1/X7-2) e o gdb é lançado
    com o `directory` da fonte configurado — `break Main.kf:2` vincula na fonte Kof,
    nunca no mangle. `KOF_GDB` resolve o executável (override de teste/ambiente, mesmo
    padrão da casa: `KOF_PUBLISH_API`/`KOF_CROSS_SYSROOT`); gdb ausente é falha honesta,
    `--target js` é recusa honesta (o alvo JS roda no engine embutido — não há
    node/inspector para anexar). `--break <linha>` transforma a frente em sessao BATCH
    scriptavel (para na LINHA Kof + `bt`, amigavel a CI) e `--output <dir>` preserva o ELF
    construido para reuso; ambos sao honestos no alvo JVM (`only apply to --target native`).
    Provado por `KofDebugNativeTest` 7/7 (batch com gdb real parando em `Main.kf:4` +
    backtrace, construcao com stub-gdb + caminhos de falha + estrita de flags R6).
  - **O output de ponto flutuante de `String.format` nao depende mais do locale do host (#466, §339)** —
    `String.format("%.2f", 3.14)` imprimia `3,14` num JVM `pt_BR` (o lowering emitia o overload de
    2 argumentos `String.format(String, Object[])`, locale-sensive por contrato) e a ponte do host
    GraalJS herdava o padrao da maquina tambem — a "paridade byte-a-byte" do §239 dependia
    silenciosamente do locale do SO. O lowering agora SEMPRE emite a forma real de 3 argumentos
    `String.format(Locale.ROOT, fmt, args)` e a ponte JS trava `Locale.ROOT`: output deterministico
    em todo alvo JVM-like (R10). **Prova:** `StringFormatLocaleE2ETest` 3/3 (JVM filho sob
    `-Duser.language=pt -Duser.country=BR`, Script, JS-via-Graal) contra golden de oraculo JDK;
    VERMELHO 3/3 pre-fix. `String.format` no Nativo continua um gap de link honesto preexistente
    (sem formatador JDK; catalogado no §339 para a lane nativa).

  - **`X as T <op> Y` nao descarta mais o operador em silencio (#459, §336)** — o operando de
    tipo de `as`/`instanceof` era parseado pelo climb de precedencia de VALOR e engolia o que
    viesse depois (`a as Double / 2.0` virava um tipo malformado renderizado como `"?"` na
    constant pool, o no de aritmetica sumia, e o programa morria em runtime com
    `NoClassDefFoundError: ?`). O `check` dizia "no errors" — miscompilacao silenciosa (R6).
    O RHS agora passa pelo parser type-ref dedicado (primitivo, pontilhado, genericos, arrays,
    nullable, tipos-funcao — incluido o caso do bug 127) e o controle volta ao loop de
    operadores: o cast liga primeiro, exatamente como `grammar.md` §5.1 ja documentava. As
    consequencias que o parser consertado tornou alcancaveis foram completadas na mesma
    unidade: alvos parametrizados resolvem (`x as List<Int>` agora carrega os args de verdade)
    e casts de array/nullable emitem descriptor valido de `CHECKCAST`/`INSTANCEOF` em vez do
    fallback `"?"`. **Prova:** `AsCastPrecedenceE2ETest` 6/6 em JVM+Script+JS (repro verbatim
    = `0.5`, a matriz `+ - * / % << >> >>>` inteira nos dois lados, `as List<Int>`/`as Int[]`
    ponta-a-ponta, e o `SEM002` honesto quando `instanceof` e legitimamente seguido de `+`
    sobre Bool).

  - **Atribuicao cruzada de tipos genericos e rejeitada em compile time (#401, §270, D-POLL-19)** —
    `List<Int>` atribuido a `List<String>` passava em todo check (a atribuicao comparava so o
    tipo RAW) e morria depois com `ClassCastException` no primeiro `get`. Os type-args agora
    sao INVARIANTES quando os dois lados carregam args concretos no mesmo raw nome — os
    checkpoints SEM012/SEM021 existentes reportam `type mismatch: cannot assign ...`, antes de
    qualquer backend (os 4 alvos compartilham o check semantico). A inferencia continua s6
    permissiva: `listOf()` (args UNKNOWN), alvos raw (`List`), `Object` e atribuicao
    classe→interface generica (#400) seguem intactos. **Migracao:** codigo que compilava e
    quebrava em runtime agora falha em compile — mude o tipo declarado ou mapeie a colecao.
    **Prova:** `GenericArgAssignmentE2ETest` 8/8 (verbatim #401, faces de atribuicao simples e
    aninhada `Map<String, List<Int>>` rejeitadas; controles mesmos-args/inferencia/raw/`Object`/#400
    aceitos).

  - **`kof test` ganha `--timeout <seg>` (linha 0.4.0, X8-A / §G6 "timeouts")** — um programa
    de teste que travava travava o runner inteiro (o harness esperava o filho para sempre;
    CI congelava). Com `--timeout 3` o filho JVM/Native é morto no prazo e reportado como
    `FAIL <arquivo>` honesto (`timeout after 3s — process killed`), com `0 passed, 1 failed`
    e exit 1. A face JS roda in-process, então lá o timeout é best-effort (o CLI diz isso
    em vez de mentir). Sem a flag o comportamento histórico fica intacto (aditivo, zero
    regressão). Prova: `CmdTestTimeoutTest` (3 casos: loop infinito morto em segundos, suíte
    rápida passa sob o limite, valores lixo/zero/sem valor recusados com estriteza R6).

  - **`return <valor>` em `void`/sem-tipo/construtor agora é `SEM093` (linha 0.4.0,
    D-DECL-RETURN, #333)** — uma função top-level que declara `void` — **ou não declara
    tipo algum** — não pode mais `return <valor>`, e construtor também não. Antes, o
    `FunctionLowering` emitia o descritor *inferido* (`()I`) enquanto o symbol e cada
    call-site ficavam em `()V`: o `kof check` passava e o programa morria em runtime com
    `NoSuchMethodError` (o repro da #333). Agora a própria definição é rejeitada em tempo
    de compilação: `void function cannot return a value - drop the value (bare \`return\`
    exits) or declare a return type [SEM093]`. `return` pelado em void continua legal
    (saída antecipada). Métodos de classe ficaram fora da regra por decisão: lá a
    reinferência bug-26 retipa symbol e descritor juntos (§130), então não produzem o
    crash de link. **Migração:** tire o valor (`return`) ou declare o tipo real
    (`Int f() { ... }`). Prova: `VoidReturnValueE2ETest` (7 casos: void/sem-tipo/ctor
    rejeitados; `return` pelado, mismatch `SEM010` não-void e inferência de método preservados).

  - **KofScript virou target de execução direta com interpretador da IR
    (06/09)** — `KofInterpreter` executa a MESMA IR otimizada que o backend
    JVM consome (mesmo frontend: parse → merge → imports → desugar → análise
    → lowering → otimização), sem emitir bytecode e sem fork de JVM.
    Paridade por construção, provada em teste (saída byte-idêntica ao JVM
    compilado: funções, strings, records com `==` de conteúdo + toString,
    coleções com higher-order, classes mutáveis, while/for-in,
    try/catch/finally com throw-as-String, spawn/await). `CompilerDriver`
    ganhou `interpret(...)` (fachada pública) e os passos extraídos
    `parseAndMerge`/`analyzeAndLower` (refactor puro, zero-regressão).
    JS/Native continuam no caminho compilado; `runFileCompiled` mantido como
    fallback e prova de paridade. Docs corrigidas: KofScript NÃO é linguagem
    separada nem JavaScript — é Kof puro no mesmo frontend.
  - **`fn`/`fun`/`func` viraram palavras reservadas no Kof (06/09, SG-001)** —
    a documentação sempre disse que "não existe `fun` nem `func`" (AGENTS.md,
    fake-idioms.md), mas o compilador aceitava `fn` como prefixo e `fun`/`func`
    como tipo de retorno implícito. Agora as três são **palavras reservadas no
    lexer** (tokens `FUN`/`FN`/`FUNC`, mesmo mecanismo de `sealed`/`permits`):
    **não existem** no Kof em nenhuma posição — nem como keyword de declaração,
    nem como nome de função/variável/parâmetro/campo. Em posição de declaração
    dá **`PARSE085`** (diagnóstico claro: "declare como `Tipo nome(...) { }` ou
    `nome(...): Tipo { }`"); em outra posição o `expectId` de cada parser já
    falha (`PARSE037` variável, `PARSE023` parâmetro). Alinhamento
    código↔corpus (regra 4). **KofScript (`.ks`) não é exceção** — é Kof puro
    (ver entrada KofScript abaixo); `fn`/`fun`/`func` lá também dão
    `PARSE085`. Breaking change deliberado e
    documentado: código `.kf` que usava `fun`/`fn`/`func` (mesmo como
    identificador) agora precisa renomear. Prova: `FunctionSyntaxTest` (12
    casos) + KofScriptTest 8/8 + suíte completa 957/0.

  - **KofScript é Kof puro — sugar JavaScript removido (06/09, correção de
    design do maintainer)** — o pipeline carregava açúcar de outra língua:
    `let`→`var`, `const`→`val`, `async fn`→`fn`, e um `fn` "próprio" traduzido
    na fronteira `.ks`→`.kf`. **KofScript não é JavaScript**: é o target onde
    o código Kof roda direto, sem compilação separada. Removido `preprocess`,
    `normalizeVoidFns`, `toKofSyntax` e as 3 cópias da lógica de wrap
    (KofScript/CmdScript/LspServer) — substituídas por um único `wrapPureKof`
    que só faz o **modelo de execução de script**: statements de topo viram
    `main()`, `var`/`val` de topo viram `KofScriptGlobals`. `let`/`const`/
    `async`/`fn` agora dão o diagnóstico normal do parser Kof em `.ks` também
    (R6: nunca silencioso). Breaking change deliberado: `.ks` que usava sugar
    precisa da forma Kof. Prova: `KofScriptTest` 9/9 (inclui `jsSugarIsRejected`
    — `fn`/`let` em `.ks` → `PARSE085`) + suíte completa.

  - **NATIVE002-stdlib residual (05/09)** — auditoria R6 + paridade cross:
    **fcvt riscv64** (os 10 mnemonics de conversão numérica saíam com
    rd/rs invertidos — `as Int`/`as Double` quebravam no `as`),
    **ToolchainMissing** (falha de `as`/`ld` nos cross agora propaga como
    erro de compilação — antes era "success=true sem binário" silencioso),
    **FLT001** (`println(double)`/`valueOf(double)` no cross vira
    diagnóstico em compile-time: runtime asm puro sem libc não tem `%g` —
    antes segfault silencioso; aritmética/conversão FP funciona),
    **time.now()** real (`clock_gettime` 113 — era stub `li a0,0` que
    quebrava o TTL do cache), **cache riscv64/aarch64** (scan loops usavam
    t2/t3 clobberados pelo `kof_string_equals` → segfault; + `sle/sge`
    inexistentes na ISA riscv → `<=`/`>=` quebravam; + `println(null)`→"null"),
    **mq riscv64/aarch64** (port completo: queue por handle, pop via
    `kof_list_remove`, queue_size, unsubscribe por identidade, invoke dos
    handlers via vtable — antes infuncional: gate MQ001). Prova:
    `KofMqE2ETest` 5/5 (incl. cross qemu c/ paridade de output),
    `riscv64/aarch64Cache`, `riscv64/aarch64TimeNow`.
    **tail-call em 8 funções riscv** (`call`+`ret` sem salvar `ra` = loop
    infinito — `observability.health`/ids/`time_interval` hangavam),
    **gates SCHED001/TIME001/SECN000** (scheduler/time.interval/kof_sec_*
    ausentes no runtime cross → diagnóstico limpo em vez de undefined-reference
    no link ou no-op silencioso), **`"42".toInt()`** (deref do VALOR do char
    como endereço → SIGSEGV), **Map/Set + higher-order** no cross
    (`RISCV_MAPSET_ASM` linear-scan; closure ABI do mq), **`kof_panic`**
    imprime C-string (mensagem de bounds-check), **json decoders escalares**
    (int/long/bool/string), **bug 30** (`decode<Bool>("false")`→true no
    x86_64: length em registrador errado + offset ignorado), **metrics()
    `# TYPE`** no cross + **tradutor quote-aware** (`.asciz "# TYPE "` era
    strippado como comentário → string não-terminada no aarch64). Sweeps de
    paridade (KSw/KSw2/KJ/KU/KMR3/KCFG/KVAL): **0 divergências** nos 3
    targets. Bugs registrados fora da lane: #29 (`spawn { lambda }` com
    handle), #31 (`process.<inexistente>` compila como acesso a campo),
    #28 (flake ws). Suíte completa 962/0/3-skip.

  - NATIVE002 paridade avançada riscv64/aarch64: stdlib real no runtime asm —
    **JSON** (`kof_json_quote`/builder, encode/decode record+listas), **HTTP**
    (`get/post/put/patch/delete/options/status` + headers, asm puro: socket+
    connect+write/read/close, syscalls asm-generic) e **spawn/await** (`clone`
    + `futex` — qemu-riscv64 8.2.2 não implementa clone3; heap compartilhado
    entre main e workers → `kof_alloc` virou bump atômico `amoadd.d`) fechados
    (19/19 qemu cada target); aarch64 herda via `translateRiscvToAarch64`.
    Fix de codegen: `--no-relax` no as/ld riscv64 (gp-relaxation faultava com
    gp=0 no binário estático). Fixes do tradutor aarch64: `movz` (não `mov`)
    para imediatos com `lsl #16`; imediatos hex no `li`/`addi`/`andi`;
    `amoadd.d`→`ldadd` + `.arch armv8.1-a`; `fence`→`dmb ish`. **db**: o link
    dinâmico de libsqlite3 exige libc — inviável no asm puro estático; os cross
    agora reportam **DB001 em compile-time** (R6: nunca undefined-reference no
    ld), travado por `crossNativeReportsDb001`. **String methods riscv64/aarch64**
    (`trim`/`toUpperCase`/`toLowerCase`/`replace` char+String/`lastIndexOf`/
    `equalsIgnoreCase`/`split`) implementados em asm puro — antes quebravam no
    link com undefined reference silencioso (R6); `RISCV_RUNTIME_ASM` dividido
    em 3 constantes (limite de 64KB do javac). Prova: `riscv64/aarch64StringTrimCaseReplaceSplit`
    + suíte 913+8+5+8.
  - GC auto-collect (safe-points + mapa de raízes por frame).
  - Package manager MVP (`kof init`/`kofdeps`/registry).

## [0.2.8-beta] - 2026-09-04

### Documentation

  - seção 0.2.7-beta movida para o topo

## [0.3.0-beta] - 2026-09-05

### Features

  - String methods riscv64/aarch64 + 2 fixes de race no spawn
  - spawn/await riscv64+aarch64 (NATIVE002-stdlib)
  - http.get/post/status riscv64+aarch64 (NATIVE002-stdlib)
  - add support for nested lambdas capturing outer variables
  - JSON completo no riscv64/aarch64 (encode+decode de listas)
  - kof_json_quote no riscv64/aarch64 + corrige escape x86_64
  - PR6 hardening limits and observability (upstream rebase)

### Bugfixes

  - time.now() riscv64/aarch64 real (clock_gettime=113, paridade x86)
  - println(char) numérico (72) preservado; String.valueOf(char) → caractere UTF-8 (h)
  - FLT001 — println/valueOf(double) no cross vira diagnóstico, não segfault
  - fcvt riscv64 com direção invertida (FP conversions quebradas)
  - kof_mv64_matvec preserva rc do vk64_submit no readback de y (memcpy clobbera %eax)
  - update stack handling and syscall for string printing
  - readback y <- ymap apos submit em mv1 (rsi clobberado pelo submit)
  - void-as-value SEM033, sublist SEM034, interface dispatch para tipo de função declarado
  - cross riscv64/aarch64 reporta DB001 em compile-time (R6)
  - aritmética sobre param de lambda sem anotação → SEM001, não bytecode quebrado
  - base 1000x1e6 + recomposicao exata por divId
  - vkMakeSet recebia PipelineLayout em vez do DSL
  - unwrap InvocationTargetException in handler error catch
  - map AudioSystem.isLineSupported throw to MEDIA003 message

### Documentation

  - FLT001-cross na matriz de gaps + Bug 27 (println(char) diverge — pré-existente remoto)
  - REFACTOR-500 — divisão confirmada (fixes-for-kofagent faz Fases 4-8)
  - gap 27 — paridade String.valueOf(char) JS vs JVM/Native (R6)
  - reconcile note for planning-future <-> beta-0.3.0 (merge state, pre-existing charAt failure, normalization checklist)
  - update AGENTS.md with autonomous mode guidelines and conditions for stopping
  - add REFACTOR-500 entry for class division guidelines
  - lição aprendida 04/09 — trabalhe sempre em partes pequenas
  - add PLAN-SOLID-500 for class restructuring guidelines
  - known-bugs.md — Bug 19 atualizado (triple-nested resolvido 04/09)
  - NATIVE002-stdlib FEITO (JSON+http+spawn riscv64/aarch64; db→DB001)
  - regra todowrite obrigatório (status visível a cada etapa)
  - break TIER 2 into measurable subtasks (2.1.1–2.5.1)

### Refactoring

  - optimize descriptor set binding logic in assembly

### Tests

  - channelBlocksBeforeSendJvm não pinifica corrida de agendamento

### Build

  - abre linha 0.3.0-beta na branch beta-0.3.0

## [0.3.1-beta] - 2026-09-08

### Features

  - implement extern function support and enhance FFI diagnostics
  - Input/Textarea setName + setReadonly — UI005 (atributos)
  - add JvmFrameDiagnostics for enhanced error reporting on frame crashes
  - add setName and setReadonly methods for Input and Textarea components
  - Table(header, rows) — UI003 data-driven (fecha table/tr/td)
  - Ul/Ol data-driven — UI003 (List<String> vira <ul>/<ol><li>)
  - Canvas.drawImage — fecha UI009 (Image composto no bitmap)
  - Canvas UI009 — save/restore/setGlobalAlpha/fillText/measureText/transform
  - Select(options) — UI003/UI004 (escolha de opção, 9 pontos + 2 provas)
  - Textarea — widget multilinha (UI003, Fase 4)
  - Form.onSubmit + Form.submit() (UI004 headline, Fase 4)
  - setId/setClass/setDisabled (UI005) + fix código morto acceptsFont
  - Form(children) — container <form> (UI004, Fase 4)
  - Input.setChecked/checked (UI004 forms, Fase 4) — checkbox/radio state
  - Input.setType (UI003/4 forms, Fase 4) — text/number/email/password/date
  - Input.setPlaceholder (UI004/5 forms, Fase 4) — KofJS DOM real
  - F3 fechada — APP001 + examples/fullstack + FullStackE2ETest (I2 DoD)
  - F3-degrau-2c — kof run full-stack (env-pass ao processo filho)
  - F3-degrau-2b — kof serve full-stack (I2: app serve o bundle via env)
  - F3-degrau-2a — serveStatic (estáticos p/ full-stack, R6 traversal)
  - F3-degrau-1 — kof build full-stack (backend+frontend+estáticos)
  - F2-parte-4 — CLI --backend/--frontend c/ override do kof.toml
  - add security plan and implementation layers documentation
  - change interfaces to sealed for LiteralKind and Type
  - F2-parte-2 — KofProjectConfig (parser mínimo de kof.toml)
  - runFile aceita Target.SCRIPT (execução direta, fase 2 plataforma)
  - F2-parte-1 — Target.SCRIPT (coringa) + COMP003 honesto + run --target script
  - add KofScript target and module resolution for project roots
  - Fase 1 — module resolution cross-directory via kof.toml + PKG006/PKG007 (plataforma, docs/future/PLATFORM-PLAN.md)
  - add interpreter parity sweep test for edge cases
  - add auto-loop script for autonomous opencode mode with cron integration
  - implement x86_64 method emission in NativeMethodEmitter
  - implement x86_64 call emission in NativeX86Calls class
  - add Kof module interpretation without bytecode emission
  - KofInterpreter — IR stack machine; KofScript roda sem compilar
  - improve imports and add check script for class size limit
  - Canvas 2D widget — desenho 2D via <canvas> (KofJS)
  - scheduler/time.interval riscv64/aarch64 (SCHED001+TIME001 FEITO)
  - time.sleep real riscv64/aarch64 (nanosleep 101)
  - metrics() com # TYPE no riscv64/aarch64 + tradutor quote-aware
  - json.decode<Int> escalar riscv64/aarch64
  - gate SECN000 no cross (kof_sec_* ausente no runtime riscv64)
  - higher-order (map/filter/reduce) riscv64/aarch64
  - Map/Set riscv64/aarch64 + kof_panic imprime C-string (bounds msg)
  - gates SCHED001 + TIME001 no cross (scheduler/time.interval stubs)
  - MQ001 cross FEITO — port completo kof.mq riscv64/aarch64
  - MQ001 no cross — gate honesto (padrao DB001)

### Bugfixes

  - source do runtime FFI casa com o JDK que o compila (CI 21 vs local 25)
  - #35.3 banner reporta a porta REAL (R6, nunca mentir)
  - headers variádicos — 2+ headers como args separados (bug 60, GitHub #32)
  - serveDir com barra final serve index.html (GitHub #35.2) + regressão bug 59
  - record com campo List<Record> decodifica tipado (bug 58, GitHub #34)
  - String.length contava bytes UTF-8 (bug 43)
  - campo estático dava lixo (bug 41)
  - await sobre handle não quebra mais o bytecode (bug 57, GitHub #31)
  - array.get(i)/.size não geram mais ClassFormatError (GitHub #30)
  - lambda return aninhado (bug 53, #28) + CME no spawn (bug 55)
  - handle return null in lambdas to avoid 404 responses (bug 53)
  - finally com return no try perdia o retorno (bug 45)
  - app.delete não colide mais com File.delete (bug 54, GitHub #29)
  - record hashCode() ausente no KofJS (bug 42)
  - try aninhado compila no KofJS (bug 49) + registra bug 52 (re-throw em catch)
  - CompilerDriver reutilizado vazava classes sintéticas (bug 51)
  - Image.setAlt/setWidth/setHeight (UI003/5) + FIX JVM descriptors (6º ponto)
  - try aninhado no KofJS (bug 49)
  - Native — 21 stubs UI ausentes quebravam link (UI001, R6 P0)
  - json.decode<List<Record>> — trata kof_json_decode_object_list (bug 48)
  - corrige RACE no spawn/await (lastReturned) + lote 3 da matriz (concorrência determinística)
  - serveDir('/') serve o bundle completo, não só o index (F3 full-stack)
  - decode<Record> no interpretador + lote 2 da matriz (erros/null/JSON)
  - re-throw em try aninhado — corpo do catch usa sub-escopo (bug 38)
  - REVERTER bug 39 (get nullable) — quebra m.get==1 (retrocompat); registrar design pendente
  - Map.get devolve V? sempre — println(null) não dá NPE (bug 39)
  - chave do cache do eval vira SHA-256 — colisão hashCode+length dava resultado errado (bug 47)
  - case de primitivo em switch → SEM035 (bug 37)
  - compound em campo de instância — KofDup + fieldType real (bug 40)
  - watchdog teto 120→240min (turno ativo legítimo passa de ~2h)
  - importa classes movidas no refactor SOLID (test-compile quebrado)
  - paridade String.valueOf(char) — JS usa String.fromCharCode (bug 27)
  - watchdog mata run pendurado (lock stale >120min)
  - qualifyGlobals substitui replaceAll(\b) — nome de global não corrompe string literal/comentário/membro
  - switches não-exaustivos após sealed->interface — adiciona default
  - SEM025 para resolução falha em símbolo conhecido — namespaces builtin, super e campos (R6, P0 roadmap-audit)
  - update cron job to require --attach for session injection and clarify server health check
  - resolve VerifyError for null comparisons by using reference equality for UnknownType
  - contains boxeia pelo tipo do argumento (bug 35)
  - campo estático por nome simples baixa GETSTATIC/PUTSTATIC
  - clarify autonomous mode cron behavior in AGENTS.md
  - diagnose builtin unknown methods, wildcard and spawn void handle (bugs 29/31/34, SG-007)
  - semear staticFields com initialValue de campos estáticos
  - add error handling for unresolved collection and process method calls
  - time.interval/scheduler com jobs canceláveis (paridade com runtime gerado)
  - pilha aceita null (LinkedList); +3 testes de paridade
  - FASE 3.1–3.7 corrigidas — ciclo StackOverflow, visibilidades, imports + prova honesta (jar fresco)
  - Canvas renderiza no KofJS — shim getContext + attach ao root + snapshot em ops de renderização (CANVAS001 FECHADO)
  - Canvas sem owner "" no JVM — construtores UI tipados no driver-side typer (CANVAS001 metade JVM)
  - contadores WS publicados ANTES do estado observável — fecha bug 28 (flake gate)
  - fun/fn/func viram palavras reservadas — não existem em nenhuma posição (SG-001)
  - member call em receiver nullable inferido (bug 33) — MethodCallTyper unwrap
  - PARSE085 também em membros de classe + âncoras da spec pós-F6/F7
  - rejeita fn/fun/func como keyword de função (PARSE085) — SG-001
  - resolveType do MemberResolver recebe o qualifyDeep do bug-32 (a extração FASE 6 duplicou o método sem o fix)
  - type-argument genérico via import (bug 32) — qualificação recursiva
  - JdwpPacket — codec no corpo da classe (0abb880 deixou aninhado como Packet, quebrando JdwpClient)
  - JdwpPacket — codec no corpo da classe, não em aninhada morta (REFACTOR-500)
  - metrics() x86_64 nao emite mais bytes NUL (comprimentos errados)
  - decoders json escalares cross + decode<Bool> x86_64 invertido
  - "42".toInt() riscv64/aarch64 — deref do VALOR do char = SIGSEGV
  - tail-call em 8 funcoes riscv64 (call+ret sem salvar ra = loop infinito)
  - F1.9 extrai RuntimeJson* — restaura label .Lkof_json_true perdido na divisão
  - println(char) imprime código (paridade JVM) + F1.1 extrai RuntimePrint
  - cache riscv64/aarch64 real + println(null) + sle/sge invalidos

### Documentation

  - fecha lane — issues #28–#35 fechadas no GitHub (0.3.1)
  - fecha metade JS do bug 42 (recordhash) + corrige doc do 44
  - especificação Editor Integration (plano, implementação depois)
  - P0 fallbacks semânticos FECHADO (verificado no código)
  - UI007 proposta de design (regra 6) + DOING atualizado (drawImage/Ul/Ol feitos)
  - bug 56 (GitHub #30 split→ClassFormatError) + linha GITHUB-P0 no DOING
  - auditoria planning-future — R1 marcado FEITO
  - idiom web — contrato de retorno de handler (bug 53/54)
  - auditoria planning-future — agente morto, lote 3 duplicata+flaky
  - corrige causa-raiz da auditoria planning-future
  - auditoria planning-future × docs/development/future
  - bugs 53/54 (GitHub #28/#29) — reproduzidos + causa raiz por IR
  - bug 49 descreve o código mesclado (5d6e68a), não minha versão descartada
  - bugs 48/49 corrigidos (json list interpreter, try aninhado JS); suíte 1037/0
  - F9 lotes 1-3 + 3 fixes da lane interpreter; suíte 1091/0/3-skip
  - FASE 4 KofUI — auditoria de cobertura + matriz de gaps UI00x
  - REFACTOR-500 COMPLETO — F3 NativeBackend 8834→479, check_500 OK, todas ≤500
  - F3 reatribuída ao agente-idiomatic (fixes parou na 3.6; NativeBackend 1269)
  - bugs 27/37/38/40 corrigidos (suíte 1025/0); 39 = design pendente
  - bugs 27/37/40 corrigidos; 39 revertido (design); 38 pendente
  - reivindica F9 — matriz Feature×Target com estado real do sweep cross-target
  - atualiza caminhos de arquivos para os novos subpacotes SOLID (jvm/, nat/)
  - comparação com a main — sem perda de funcionalidade
  - bug 46 — spawn { return … } SIGSEGV no Native (variante do #29) + lane KOFSCRIPT g+h fechada
  - SOLID organização em subpacotes — 7 módulos migrados (backend/js/jvm/nat/parser/runtime/vk)
  - move pendentes para development/ e referencia no AGENTS.md
  - regra de sincronização — verificar conflito antes de cada commit/push
  - runFile SCRIPT feito (51754fd); proximo = paridade cross-target (regra 5)
  - bug 36 CORRIGIDO (3c7641f) + heartbeat corrigido (--attach 9092, testado 1min)
  - Fase 1 plataforma FEITA (6caf84d) — PRÓXIMO PASSO: F2 Target Architecture
  - F0 auditoria real + plano técnico por fases (module system, targets, full-stack, KofUI/JS/Wasm/Android, conformance)
  - roadmap-audit.md — matriz de estado real (12 itens + 12 fallbacks UNKNOWN P0)
  - claim ROADMAP AUDIT lane (fase 1 auditoria em curso)
  - KofScript = execução direta via KofInterpreter (status, backend-parity, bug 37 refinado)
  - varredura FEITA (fix static-field + bug 35), bugs 36-40 registrados, licao do build stale ECJ
  - varredura de paridade FEITA + bugs 35-40 registrados
  - bug 34 registrado — método inexistente em builtin → no-op silencioso (R6)
  - KOFSCRIPT pós-merge — paridade 15/15, bugs null+interval corrigidos, PRÓXIMO PASSO (bug 29/34 + varredura JS/Native)
  - F3 3.1–3.9 completas (fixes-for-kofagent) + protocolo de prova honesta documentado
  - REFACTOR-500 — FASE 9 (varredura) FEITA, só NativeBackend >500 (F3 do outro agente)
  - KofScript = target de execução direta (interpretador da IR)
  - REFACTOR-500 — FASE 2 COMPLETA (CompilerDriver ≤500)
  - FASE 3 reivindicada (NativeBackend) — maintainer pediu, agente-idiomatic não iniciou; aviso de colisão NATIVE002 + plano byte-diff 3 targets
  - PRÓXIMO PASSO — lane 4–8 fechada, CANVAS001 metade JVM corrigida, falta só design JS (lane Canvas)
  - REFACTOR-500 — F2.33-F2.44 + lições (this->driver, campos intercalados)
  - REFACTOR-500 — FASES 4-8 COMPLETAS (fixes-for-kofagent); suíte 955/0
  - SG-001 palavras reservadas (bf84a86) + bug 33 corrigido + bug novo coleção
  - REFACTOR-500 — F2.24-F2.32 + lição do bloco de instância
  - SG-001 inclui membros de classe (7e6f9e3, suíte19)
  - SG-001 resolvido (fn/fun/func → PARSE085, suíte18 verde)
  - âncoras do CompilerDriver por método (não linha) + contagem AST 50 nós
  - LANG-SPEC FEITO — suíte16 969/0/3-skip (zero regressão, docs puros)
  - README + architecture.md — separa linguagem≠compilador≠target, corrige pipeline
  - syntax (formas concretas) + compiler-architecture (implementação)
  - modules + semantics + specification-status + specification-gaps
  - functions + closures + classes
  - expressions + statements — semântica de cada forma
  - types + type-system — catálogo de tipos e regras concretas de validade
  - grammar — gramática EBNF extrativa + AST (39 nós) + precedência exata
  - lexical-structure — gramática léxica completa (tokens, keywords, literais, operadores, erros LEX00x)
  - Language Reference — índice + separação linguagem≠compilador≠target (LANG-SPEC)
  - REFACTOR-500 — lição da divisão do ExpressionMethodCallLowerer (cadeia if/else)
  - REFACTOR-500 — F2.21-F2.23 + suíte 948 verde
  - RFC completa §3-24 + plano I1-I4 — topologia, kof.toml, System, build/deploy, targets, segurança, testes, open questions
  - REFACTOR-500 — F2.12-F2.20 (CompilerDriver 3419)
  - RFC §3-7 — principles, application, manifesto kof.toml, componentes, topologia, monólito
  - RFC APPLICATION_MODEL §1-2 — motivation + auditoria do estado (CLI/stdlib/targets/gaps)
  - bug 28 — nota de recorrência 05/09 (suíte 969 pós bug-32, flake confirmada)
  - REFACTOR-500 — FASE 5+8 FEITAS (fixes-for-kofagent); PRÓXIMO FASE 7 Parser
  - REFACTOR-500 — limpa PRÓXIMO PASSO (F2.12 LoweringContext)
  - REFACTOR-500 — F2.11 + PRÓXIMO PASSO (LoweringContext)
  - REFACTOR-500 — F2.10 CompilerImports
  - Bug 28 — flaky WS/SSE connection counter (JVM) registrado
  - REFACTOR-500 — F2.9 CompilerDesugar
  - time.sleep real no cross (ce81639) — fecha unidade
  - regra 7 — unidade em progresso = turno em progresso
  - REFACTOR-500 — F2.8 ModuleRoots
  - estado real do NATIVE002-stdlib (sweep completo 05/09)
  - REFACTOR-500 — F2.5-F2.7
  - bug 31 (process.<inexistente> segfault) + gap formato log cross
  - linha melissa — sweep R6 completo (SECN000, json decoders, metrics # TYPE, tradutor quote-aware); suíte 962/0
  - REFACTOR-500 — F2.5 BoxClassFactory
  - #29 spawn { lambda } com handle quebra em todos os targets
  - re-dispacho nao e conversa — regra 6 do turno autonomo
  - linha melissa atualizada — Map/Set/higher-order FEITO, sweep 0 divergencias
  - PRÓXIMO PASSO — sweep R6 crypto/process-spawn-edge/json-edge/string-utf8
  - REFACTOR-500 — PRÓXIMO PASSO atualizado (F2.5 BoxClassFactory/CompilerImports)
  - REFACTOR-500 — F2.4 CompilerTypes
  - REFACTOR-500 — F2.3 TypeEmitter + PRÓXIMO PASSO
  - REFACTOR-500 — F2.1/F2.2 CompilerDriver (TypeMetrics, StringMethodRegistry)
  - restaura PRÓXIMO PASSO + linha melissa (merge remoto sobrescreveu)
  - REFACTOR-500 — divisão confirmada (idiomatic F1-3+9, fixes-for-kofagent F4-8) + FASE 1 completa
  - REFACTOR-500 — FASE 1 COMPLETA (NativeRuntime)
  - REFACTOR-500 — F1.15 divide métodos gigantes restantes
  - REFACTOR-500 — F1.14 dedup
  - REFACTOR-500 — F1.13 security/validation/observability
  - REFACTOR-500 — F1.12 printnum/net/vk/misc
  - REFACTOR-500 — F1.11 concurrency
  - REFACTOR-500 — F1.10 RuntimeMemory/RuntimeGc
  - REFACTOR-500 — progresso F1.1-F1.9 (agente-idiomatic) e regressão de paridade do merge
  - Bug 28 (flake ws counter) + PRÓXIMO PASSO (auditoria R6 observability/scheduler)
  - modo autonomo — regra do turno + PRÓXIMO PASSO

### Refactoring

  - dividir lowerer UI + JsRuntimeUiWidgets + JvmRuntimeUi (gate <=500)
  - F3 — extrai NativeClassMeta (vtable/string data, 118 linhas)
  - F3 — extrai NativeOpHelpers (ops de emissão, 178 linhas)
  - F3 — extrai NativeArchEmitter (emitRiscv/emitAarch64, 262 linhas)
  - F3 — extrai NativeMethodEmitter (emitMethod/emitOperation/emitStart, 251 linhas)
  - SOLID — JsExpressionParser 526→383 (JsExpressionStatementParser helper)
  - SOLID — JsControlFlowParser 514→499 (JsLabelParser helper)
  - SOLID — ExpressionParser 519→475 (ExpressionNewParser helper)
  - SOLID — migra parser para dev.kof.compiler.parser (10 classes)
  - SOLID — migra backend/orquestração para dev.kof.compiler.backend (4 classes)
  - SOLID — migra backend JVM para dev.kof.compiler.jvm (35 classes)
  - SOLID — migra backend nativo para dev.kof.compiler.nat (36 classes)
  - SOLID — migra runtime nativo para dev.kof.compiler.runtime (60 classes)
  - SOLID — migra backend JS para dev.kof.compiler.js (30 classes)
  - SOLID — 271 classes public + extrai RecordDeclarationNode/KofOperation
  - SOLID — separa JsMethodCtx.java (5 classes) em arquivos próprios
  - SOLID — separa AstNodes.java (59 decls) em arquivos próprios
  - SOLID — separa IRNodes.java (45 records) em arquivos próprios public
  - SOLID subpackages — grupo vk public (corrige acesso cross-package)
  - divide KofInterpreter/KofInterpreterBuiltins em 8 colaboradores <=500
  - update progress and next steps for REFACTOR-500 phases
  - change resolveCalleeName method visibility to public
  - extract assembly logic to NativeAssembler class
  - F2.53 extrai CompilerUiEmitter (UI instance, packed color, SAM)
  - F2.52 extrai lowerAndEmit para CompilerPipeline
  - F2.51 move setters/local-scope/isAbstract para CompilerDriverState
  - F2.50 move 35 wrappers/setters para CompilerDriverState (herança)
  - F2.49 extrai CompilerEmission2 (super-bridge, args, increment)
  - F2.48 extrai CompilerDriverState (33 campos via herança)
  - F2.47 extrai CompilerEmissionHelpers + CompilerConfigSupport
  - F2.46 extrai CompilerTypeSupport (type helpers)
  - F2.45 extrai CompilerPipeline (orquestração)
  - FASE 3.6 — aritmética x86_64 + predicados de tipo extraídos (NativeBackend 2070→1741, REFACTOR-500)
  - FASE 3.5 — calls String/JSON x86_64 extraídos p/ NativeX86StringCalls (2286→2070, REFACTOR-500)
  - FASE 3.4 — emissores cross riscv64 extraídos (2 classes ≤500, NativeBackend 2850→2286, REFACTOR-500)
  - FASE 3.3 — HTTP/spawn riscv64 extraídos (3 classes ≤500, NativeBackend 3632→2850, REFACTOR-500)
  - FASE 3.2 — tradutor riscv→aarch64 extraído p/ NativeAarch64Translator (4113→3632, REFACTOR-500)
  - FASE 3.1 — constantes asm riscv64 fora do NativeBackend (8834→4113, REFACTOR-500)
  - ExpressionStaticCallLowerer 502→493 — construtor Canvas move p/ ExpressionUiStaticLowerer (REFACTOR-500 varredura)
  - F2.44 extrai CompilerOrmSupport (ORM + super-bridges)
  - F2.43 extrai CompilerFunctionLowering (funções top-level)
  - F2.42 extrai CompilerRecordSupport (métodos sintéticos de record)
  - F2.41 extrai CompilerClassLowering (lowering de classes)
  - F2.40 extrai CompilerComparisons (comparação + retorno)
  - F2.39 extrai CompilerLambdaClass (geração de classes lambda)
  - F2.38 extrai CompilerAnnotations (lowering de anotações)
  - F2.37 extrai CompilerCaptureScanner (análise de capturas)
  - F2.36 extrai CompilerCaptures (coleta de capturas de lambda)
  - F2.35 extrai ExpressionOrmCallLowerer (ORM estático)
  - F2.34 extrai ExpressionJsonCallLowerer (json.encode/decode)
  - F2.33 extrai ExpressionInstanceCallLowerer (dispatch de instância)
  - VkChain64Asm 3568 -> 57 (wrapper source() concatena 15 classes por dominio, REFACTOR-500)
  - VkChain64Dispatch — kof_vk_dispatch64 (REFACTOR-500)
  - VkChain64WSp — wputsp + wrunsp (REFACTOR-500)
  - VkChain64W32 — wput32 + wrun32 (REFACTOR-500)
  - VkChain64W64 — wput + wrun (REFACTOR-500)
  - VkChain64Matvec — load_w + matvec (REFACTOR-500)
  - VkChain64Shape — set_shape + shape_xy (REFACTOR-500)
  - VkChain64Submit — submit + write_desc (REFACTOR-500)
  - VkChain64Helpers — helpers fail/trace (REFACTOR-500)
  - VkChain64Loader — dlopen/dlsym libvulkan (REFACTOR-500)
  - VkChain64Data — .data/.bss/.rodata (REFACTOR-500)
  - VkChain64Alloc — vk64_alloc_buffer (REFACTOR-500)
  - VkChain64Init — init_common parte A (REFACTOR-500)
  - VkChain64InitSpv — init parte B: spv/shader/pipelines (REFACTOR-500)
  - VkChain64Init2 — pipe32+split opcionais (REFACTOR-500)
  - VkChain64InitPools — init pools (pipe32/split/pools de VkChain64Asm, REFACTOR-500)
  - F8.6 JvmBackend sem resíduos (REFACTOR-500)
  - F8.5 extrai JvmOpEmitter de JvmBackend (REFACTOR-500)
  - F8.4 extrai JvmOpCollections de JvmBackend (REFACTOR-500)
  - F8.3 extrai JvmLiteralEmitter de JvmBackend (REFACTOR-500)
  - F8.2 extrai JvmRecordEmitter de JvmBackend (REFACTOR-500)
  - F8.1 extrai JvmAnnotations de JvmBackend (REFACTOR-500)
  - F2.32 extrai ExpressionUiMediaCallLowerer (Ui/Media/Io estáticos)
  - F2.31 extrai 5 lowerers de namespace (http/time/mq/config/cache)
  - F2.30 extrai ExpressionLogCallLowerer (log.*)
  - F2.29 extrai ExpressionSchedulerCallLowerer (scheduler.*)
  - F2.28 extrai ExpressionProcessCallLowerer (namespace process.*)
  - F2.27 extrai ExpressionDbCallLowerer (namespace db.*)
  - F2.26 extrai ExpressionPrintLowerer (print/println)
  - F2.25 extrai ExpressionUiStaticLowerer (Icon/Font/Button/Component/Store)
  - F2.24 extrai ExpressionStaticCallLowerer (branches receiver-null)
  - FASE 6 — renomeia ExpressionTyper/MethodCallTyper → Sem* (colisão de nome com F2.15/F2.16 do CompilerDriver)
  - FASE 4.6 — remove classe placeholder vazia JsMethodCtx (REFACTOR-500)
  - FASE 4.5 — divide parsing/lowering em 8 classes coesas ≤500 (REFACTOR-500)
  - FASE 4.4 — extrai MethodCtx/LoopCtx/NewPending/DupMarker/StatementEnd + helpers estáticos (REFACTOR-500)
  - FASE 4.3 — extrai JsLoweringContext (estado compartilhado do lowering) (REFACTOR-500)
  - FASE 4.2 — extrai JsTypeMapper (helpers puros de nome/tipo) (REFACTOR-500)
  - FASE 4.1 — extrai runtime constants + JsArtifactWriter (REFACTOR-500)
  - F1.16 divide RuntimeJsonDecode em 2 (517→292)
  - F2.21 extrai CollectionCallLowerer (branches List/Channel/Map/Set)
  - REFACTOR-500 F6 (6/6) — remove SSE_CONNECTION_TYPE órfã do SemanticAnalyzer
  - FASE 7 — divide Parser em StatementParser/ExpressionParser/LambdaParser/TypeParser/AnnotationParser/ClassMemberParser (REFACTOR-500)
  - FASE 7 — cria ParseContext (estado compartilhado do parsing, REFACTOR-500)
  - REFACTOR-500 F6 (5/6) — extrai ExpressionTyper + StatementAnalyzer + 3 typer de MethodCallExpr
  - REFACTOR-500 F6 (4/6) — extrai SymbolTableBuilder
  - REFACTOR-500 F6 (3/6) — extrai TypeChecker
  - REFACTOR-500 F6 (2/6) — extrai MemberResolver
  - F2.20 extrai ExpressionAssignmentLowerer + ExpressionBinaryLowerer
  - REFACTOR-500 F6 (1/6) — SemanticAnalyzer expõe estado via accessors
  - F2.19 extrai ExpressionMethodCallLowerer (case MethodCallExpr, 2205 linhas)
  - F2.18 extrai ExpressionLowerer (emitExpression, 3169 linhas)
  - F2.17 extrai CollectionMethodTyper (List/Map/Set/String)
  - F2.16 extrai MethodCallTyper (case MethodCallExpr do inferExprType)
  - F2.15 extrai ExpressionTyper (inferExprType)
  - F2.14 divide StatementLowerer — SwitchStmtLowerer + SwitchExprLowerer
  - F2.13 extrai StatementLowerer (emitStatementInner + switch-expr)
  - F2.12 adiciona resolveWithTypeParams/substituteTypeVariable/defaultValueOp ao CompilerTypes
  - FASE 8 — Main 1229 → 375 (dispatcher + comandos pequenos) (REFACTOR-500)
  - FASE 8 — extrair CmdBuild/CmdRun/CmdTest/CmdScript/CmdServe + KofCliSupport de Main (1229) (REFACTOR-500)
  - F2.11 adiciona type/enum/record helpers ao CompilerTypes
  - F2.10 extrai CompilerImports (expandKofImports + declarationName)
  - FASE 8 — KofJsRunner 568 → 419 + KofJsWebview (162) (REFACTOR-500)
  - F2.9 extrai CompilerDesugar (desugarTests/desugarApplication/buildTestHarnessMain)
  - FASE 8 — Bench 630 → 286 (orquestração só) (REFACTOR-500)
  - FASE 8 — extrair BenchRunners (149) de Bench (630) (REFACTOR-500)
  - FASE 8 — KofScript 608 → 479 + KofScriptExecutor (164) (REFACTOR-500)
  - FASE 8 — extrair BenchBaseline (162) de Bench (630) (REFACTOR-500)
  - F2.8 extrai ModuleRoots (moduleRootFor/commonAncestor/derivedPackageOf)
  - FASE 8 — extrair BenchDiscovery (100) de Bench (630) (REFACTOR-500)
  - F2.7 extrai JsonDispatch (encode/decode/listTag/sanitize)
  - F2.6 extrai HierarchyResolver (5 métodos de hierarquia)
  - F2.5 extrai BoxClassFactory (criação de box mutável)
  - F2.4 extrai CompilerTypes (toType/qualifyViaImports/ownerTypeFromInternal/mainClassType)
  - F2.3 extrai TypeEmitter.boxPrimitive
  - FASE 8 — JvmVkRuntime 995 → wrapper + 3 partes ≤500 (REFACTOR-500)
  - FASE 8 — JvmMediaRuntime 673 → wrapper + 2 partes ≤500 (REFACTOR-500)
  - FASE 8 — JvmWebRuntime 716 → wrapper + 2 partes ≤500 (REFACTOR-500)
  - FASE 8 — JvmStringRuntime 983 → wrapper 15 + 5 partes ≤500 (REFACTOR-500)
  - F2.2 extrai StringMethodRegistry (assinaturas de String/Object)
  - F2.1 extrai TypeMetrics (11 helpers de tipo puros)
  - F1 final — RuntimeSecurityData extrai rodata; todas as Runtime* ≤500
  - F1 COMPLETA — NativeRuntime 17726→141 linhas, orquestrador puro
  - F1.15 divide JsonArrayDecode/Log/Config/Io/Db — NativeRuntime 5564→267 linhas
  - F1.14 dedup — RuntimeJsonUtils/Cache/Time/Ui ligados ao NativeRuntime
  - F1.13 extrai security/validation/observability/map/set/enum
  - F1.12 extrai RuntimePrintNum/RuntimeNet/RuntimeVk/RuntimeMisc
  - F1.11 extrai RuntimeConcurrency/Channel/Scheduler/Mq
  - F1.10 extrai RuntimeMemory + RuntimeGc (alloc/free/gc/exit/panic/errors)
  - F1.8 extrai RuntimeArray (alloc/length/get/set)
  - F1.7 extrai RuntimeList (kof_list_new/grow/add/get/set/...)
  - F1.6 extrai RuntimeStringBase (from_literal/memcpy/length/concat/equals/print_string)
  - F1.5 extrai RuntimeStringOps + RuntimeStringEdit
  - F1.4 extrai RuntimeStringSearch (contains/startsWith/endsWith/indexOf/lastIndexOf)
  - F1.3 extrai RuntimeStringParse (toInt/toLong/toDouble/toFloat)
  - F1.2 extrai RuntimeStringConv (int/char/long/bool/float/double→string)
  - FASE 8 — Optimizer 611 → 240 (REFACTOR-500)
  - FASE 8 — OptimizerConstantFold (388) — passe de constant folding (REFACTOR-500)
  - FASE 8 — NativeWebRuntime 603 → 25 (REFACTOR-500)
  - FASE 8 — NativeWebResponses (122) — helpers de resposta (REFACTOR-500)
  - FASE 8 — NativeWebListen (355) — listen + handle_client (REFACTOR-500)
  - FASE 8 — NativeWebCore (156) — dados + primitivas web (REFACTOR-500)
  - FASE 8 — NativeHttpRuntime 652 → 30 (REFACTOR-500)
  - FASE 8 — NativeHttpCore (392) — request core + wrappers (REFACTOR-500)
  - FASE 8 — NativeHttpParseUrl (172) — parse URL + erro https (REFACTOR-500)
  - FASE 8 — NativeHttpPrimitives (113) — data + buffer helpers (REFACTOR-500)
  - FASE 8 — JdwpClient 503 → 415 + JdwpPacket (100) (REFACTOR-500)
  - FASE 5 — JvmRuntime 2526 → 132 + 7 classes ≤500 (REFACTOR-500)

### Tests

  - fecha bug 52 — re-throw em catch já tinha paridade JS (colateral do 45)
  - kitchen-sink com ORACLE strict-verifier (P0 parte 4)
  - gate de CI da matriz — doc × exclusões do teste (Fase 9)
  - comentário do freshDriver atualizado (bug 51 corrigido em b7afc5a)
  - bug 48 — regressão decode<List<Record>> no interpretador + matriz
  - ConformanceMatrixTest lote 1 — linguagem core nos 4 targets
  - cross-target sweep JS/Native (regra 5) — bugs 41-45 registrados + gate JVM×JS
  - casos dos bugs 35/36/static-field travados em JVM×JS
  - gate de paridade permanente interpretado vs JVM (15 casos)
  - guard Assumptions.assumeTrue (qemu ausente) em time/scheduler cross — convenção NATIVE002
  - E2E riscv64/aarch64 Map/Set (paridade exata com x86_64)

## [0.3.2-beta] - 2026-09-09

### Features

  - add Fieldset, Iframe, Video, Audio, Hr widgets - UI003

### Bugfixes

  - parâmetro após um `Long`/`Double` deixa de sumir da assinatura (GitHub #47)
  - atribuicao parametro let

### Documentation

  - corrigir links internos para docs/development (#49)
  - registra o bug 64 (KofJS descarta parâmetro após Long/Double)
  - reverifica o inventário contra o build 0.3.1-beta e corrige a entrada 45
  - registra bugs 62 e 63 (mutabilidade não validada; let redeclarado em parâmetro no KofJS)
  - bug 6 — remove seção duplicada/desatualizada em known-bugs.md

## [0.3.3-beta] - 2026-09-09

### Bugfixes

  - sintetizar hashCode() para records no backend nativo (#55)

## [0.3.4-beta] - 2026-09-09

### Documentation

  - add GitHub issue form templates

## [0.3.5-beta] - 2026-09-09

### Features

  - S3.1c — strings.escapeJson nos 5 backends (RFC 8259, oracle Python)
  - add escapeJson function for JSON string escaping
  - add support for multidimensional arrays and related operations
  - SECN000 fechado — uuid.v4 riscv/aarch (B25, getrandom ecall 278) + paridade variant x86 (máscara 10xx)
  - S8-C riscv/aarch — net.* portado (B24) — NET001 FECHADO
  - Fase E — arrays (anewarray + acessos) no statements-path
  - S8-B x86 — net.* URI-parse nos nativos x86 (RuntimeUri, NET001 só riscv/aarch)
  - Fase E — new/dup/init em statement-bodies
  - §7 degrau 4 — tipos de assinatura registram import
  - S8-wedge net nos 6 alvos não-nativos — decisão de shape + NET001 gate
  - §7 degrau 3 — imports cross-package com regra de não-ambiguidade
  - ENC002 fechado — base64/base64Url riscv B23 + aarch64 (port)
  - §7 degrau 2 — índice same-package resolve instanceof/cast de domínio
  - S3.1b — strings.unescapeHtml nos 4 targets
  - #58 — issue forms (bug_report + feature_request + config)
  - S3.2 — strings.removeWhitespace/normalizeWhitespace nos 4 targets
  - §7 degrau 1 — kof decompile <dir> com package + fix pop sem operando
  - S3.1 — strings.escapeHtml nos 4 targets, sem gate
  - Fase E — pop/instanceof/checkcast com whitelist R6-segura
  - S6c — validation.isDomain nos 4 targets, sem gate (RFC 1123 v1)
  - blockerSink — medidor da fila Fase E (custo zero) + ROI medido
  - S6b.3 — validation.isIpv6 nos 4 targets, sem gate
  - S6b — validation.isCreditCard (Luhn) nos 4 targets, sem gate
  - S6a — validation.isIpv4/isMac/isPort nos 4 targets, sem gate
  - STRN001 FECHADO — word-converters (joinWords) portados p/ riscv64 B15 + aarch64
  - i2c → as Char (único narrowing fiel); i2b/i2s ficam stub honesto
  - S7.2 — time.dayOfWeek/daysBetween nos 4 targets, sem gate
  - bug 62(b)/(c) — escrita em componente de RECORD é imutável (SEM037)
  - S7-wedge — time.isLeapYear/daysInMonth nos 4 targets, sem gate
  - bug 62 (a) — val é imutável (SEM037) + parser carrega type=val
  - S5 — validation.isCpf/isCnpj/isCep/isPis nos 4 targets, sem gate
  - S3b-wedge — uuid.v4 (RFC 4122) nos 3 targets testáveis + SECN000 gate cross-arch
  - decompile trata ldc_w (maior gap real do corpus) e aconst_null
  - S4.2c — encoding.base64UrlEncode/Decode (RFC 4648 §5) + split RuntimeEncoding ≤500
  - JSON runtime improvements and self-check update
  - S4.2b — encoding.urlEncode/urlDecode (RFC 3986) nos 4 targets, sem gate
  - decompile recupera aritmética/casts long+double com guarda de tipo
  - S4.2a — encoding.base64Encode/Decode (reuso dos internals x86 + gate ENC002 cross-arch)
  - S4-hex — namespace encoding com hexEncode/hexDecode nos 4 targets
  - S2b.4 — word-converters (toCamel/Pascal/Snake/Kebab/slugify) + split ≤500 (RuntimeStrings, JsCrypto)
  - decompile recupera String concat via invokedynamic (J9+ BootstrapMethods)
  - S2b.3 — strings.padLeft/padRight nos 4 targets + fix do limite de 64KB na cadeia riscv
  - S2b.2 — strings.repeat/truncate nos 4 targets
  - S2b-wedge — strings.capitalize/reverse nos 4 targets (1º conversor que alocou String)
  - Fase C/E — ldc2_w (const Long/Double) no kof decompile
  - S2a.3+S2a.4 — strings.count (não-sobrepostas) + isUpperCase/isLowerCase nos 4 targets
  - Fase C/E — lconst/dconst no kof decompile (lesson bug 62 aplicada)
  - S2a.2 — strings.isAlphaNumeric/isAscii nos 4 targets (paridade ASCII travada)
  - S2a — hook KofStd unificado + kof.strings isAlpha/isNumeric nos 4 targets
  - S1 kof.math Int-only nos 4 targets (clamp/abs/sign/min/max/isEven/isOdd/isPositive/isNegative/isZero)
  - Fase C — do-while (bottom-tested loop) no kof decompile
  - UI006 residual FEITO — Event.target()/relatedTarget() com prova browser corrigida
  - add target and relatedTarget event accessors for UI006
  - codeAction source.format (EDI001 §15 — último bullet)
  - UI002 — warning único quando kof.ui roda no interpretador (R6)
  - UI006 — Event key/value/x/y + widget.on() (DOM real em KofJS)
  - documentSymbol (outline) + LspHover extraído (EDI001 §15)
  - UI003 — Fieldset/Iframe/Video/Audio/Hr (DOM real em KofJS)
  - Fase D — Type Recovery (Signature JVM com genéricos)
  - F9(c) — Android/Wasm documentados + --target=wasm honesto (R6)
  - textDocument/formatting — delega ao KofFormatter (EDI001 §15)
  - EDI001 §18 — kof.target flui para build/run/test da extensão
  - EDI001 — extensão VS Code completa (extension.js + snippets, §3/§19)
  - textDocument/definition — go-to-definition same-file (EDI001 §15)
  - EDI001 degrau 11 — hook pós-instalador oferece integrações (§13)
  - EDI001 degraus 3+4-10 — install/uninstall/setup com consentimento + conteúdo idiomático por editor
  - EDI001 degraus 1-2 — infra EditorIntegration + kof editor (read-only)

### Bugfixes

  - bug 71 — new T[a][b] cria TODAS as dims (KofNewMultiArray: MULTIANEWARRAY JVM / Array.newInstance interp / kofMultiArray JS) — restaura gate quebrado no remote (53264c9f era meio-de-grau)
  - #65 transaction aninhado comita o escopo externo — rollback não desfaz
  - #67 kof build ignora .kof (só varre .kf) e responde no .kf files found
  - #66 LNT apontava o statement seguinte (pos pós-ponto-e-vírgula + cópia HashMap colidindo records iguais)
  - #64 += em elemento de array e campo estático qualificado sobrescreve em vez de somar
  - #63 2 labels de debug consecutivos no mesmo pc → LNT inválida (ClassFormatError no load)
  - #62 signature genérica de type-arg primitivo usava descriptor cru (D) → GenericSignatureFormatError
  - resolve SIGSEGV in spawn expressions by correcting type inference for lambda returns
  - #60 — handle de conexão nunca reutilizado (contador monotônico)
  - §70 — join heterogêneo primitivo-vs-primitivo (Int/Long/Double/null) sem crash e sem widening
  - #57 — if/switch heterogêneo primitivo-vs-referência não gera mais VerifyError no JVM
  - sintetizar hashCode() para records no backend nativo (#55)
  - ldc escapa constantes de string — DRIFT 69→5 no corpus (09/09)
  - 3 bugs de parsing/length que travavam bytecode REAL (601 classes)
  - #54 — <init> NÃO é virtual no interpretador (super(v) recursiva)
  - bug #54 — super(v) explícito despacha p/ <init> da SUPERCLASSE (era recursão no ctor → StackOverflowError)
  - #53 metades JS/Native/script — super(Record.<init>) só no JVM
  - bug 66 (#53) — record com ctor explícito canônico não gera <init> duplicado
  - #42(c) — SEM038 em this.x= de MÉTODO de record (exempt só construtor) + testes
  - #42 — SEM038 escrita em componente de record + SEM037 no update do for
  - bug 50 — futex WAIT do canal x86_64 com args corretos (uaddr=&lock, op, val)
  - #52 — reconstruir agrupamento por precedência/associatividade
  - String.toInt/toLong/toDouble/toFloat no runtime (GitHub #51)
  - bug 48 json.decode<List<Record>> gap honesto JSN004 + bug 59 regressão riscv/aarch estáticos
  - B5-B9 emitiam código em .rodata (herdado do B4) — crash em runtime
  - decompile nunca emite owner java/jdk sem mapeamento (R6)
  - slot de xstore_0..3 wide era (op-0x3f) sem %4 (bug da unit anterior)
  - decompile mapeia slots wide (R6 — Long/Double = 2 slots)
  - bug 62 — CP Float/Double como bits crus; ldc recusa float p/ não driftar
  - Fase C — R6: nunca emitir código errado p/ join compartilhado
  - status reflete o marker mesmo com editor ausente do PATH
  - SEM036 — função não-void que pode terminar sem return (bug 26 variante)

### Documentation

  - STDLIB — registra gate quebrado no remote (53264c9f multidim/bug71 WIP: ArrayFiller nunca criado + import KofNewMultiArray ausente em JsExpressionParser) — build limpo falha; escapeJson S3.1c FEITO (d57f8e5c) suíte verde antes do rebase; lane pausa por colisão (bug 71 do autor)
  - #62 docs de Kof (docs de Kof) — docs
  - #61 respondida (design da mantenedora) — sessão issues 62-67
  - switch-Fase-C em espera (stash WIP); #62 assumida; #61 não (design)
  - bugs 46/50 verificados pós-fix órfão (a617d840) — testes verdes, linha bug-fix atualizada
  - junta linha da lane STDLIB rachada por newline (`\\n` literal dentro do replace — contagem de pipes 7 restaurada)
  - #60 fechada c/ evidência; convenção koftmp; próxima fila
  - gotcha LEX004 — lexer pré-processa \\uXXXX antes do token
  - #55/#57/#58 fechadas c/ evidência; próximo = degrau 2 multi-classe ou §§69-70
  - #55 FECHADA (cherry-pick main def86a5a→c57855fd, prova recordhash 4 targets)
  - Fase E — pop/instanceof/cast feitos (3ca20067); próximo degrau = multi-classe §7
  - fila Fase E CORRIGIDA pela prova de drift — pop/instanceof/cast bloqueados por multi-classe, não por eles
  - migração — 601/601 robustez (ff2369f6); fila Fase E = 1812 stubs/3306
  - 39-stdlib — tutorial da standard library universal (S9.2)
  - #54/bug 67 — corrigido registro da causa raiz (o 8968c883 sozinho NÃO fechava) + DOING com evidência
  - SG-006 resolvido — short-circuit && paridade travada por teste (suíte verde)
  - bug 46 — registra teste de isolamento (sem captura)
  - registrar que #54 (bug 67) foi corrigido pelo lane bug-fix — agente migração não deve refazer
  - bug 67 (#54) super(v) interp corrigido + known-bugs + DOING
  - bug 50 — registra teste de validação channelWithSpawnNative
  - #53 FECHADA (203096e4) + #54 aberta (interp super(v), sem dono) — próximo passo do loop
  - SG-010 resolvido — val imutável (SEM037, bug 62a)
  - 'ERRO de runtime: record é imutável' → 'ERRO de compilação SEM038' (training/language/classes + learn/07)
  - learn/08 — p.x=99 é erro de COMPILAÇÃO SEM038 (não runtime)
  - records — documenta imutabilidade (SEM038, bug 62)
  - concurrency — documenta spawn { return ... } lambda literal + Handle (bug 46 Native gap)
  - bug 65 — registra verificação do pipeline JS (descarta codegen; causa = timing runtime no browser)
  - corrigir \\n literal que mesclou as linhas STDLIB e EDI001
  - SG-006 — análise: JS emite &&/|| nativos com short-circuit; recomendar teste de paridade
  - bug 46 — registra teste de regressão + confirmação SIGSEGV + nota sobre causa x86_64
  - remover pipe extra órfão na linha STDLIB (herança do commit S5)
  - bug 66 corrigido; estado atualizado
  - #42 FECHADA (a/b/c, ed0475c8) — linha da sessão atualizada
  - DD-02 → APLICADO (erro direto) + link p/ #53 (record+ctor JVM, aberto); issue #53 criada com repro mínimo
  - bug 62 completo (a val SEM037 + b/c record SEM038); restam 5 exigem ambiente/regra 6
  - bug 62 (mutabilidade) completo — (a) val SEM037, (b)/(c) record SEM038
  - #42 62(b)/(c) FEITO (72e79b9f) — não retocar; mantém bloco 9364b973 da lane bug-fix
  - consertar linha da lane STDLIB — S7-wedge dentro da linha + PRÓXIMO PASSO reordenado
  - bug 50 correção candidata (futex WAIT args) + DOING estado real
  - estado real dos bugs apos a sessao (48,59,62a corrigidos; doc atualizado)
  - marcar bugs 43,44,63,64 como corrigidos (fixes já no código com testes)
  - marcar bugs 7,9,18,21,22,23,30 como corrigidos (doc desatualizado)
  - DD-02 — design dos validadores de mutabilidade (val/record, #42)
  - normas de desenvolvimento colaborativo da pré-beta 0.3.0 no código de conduta
  - training/idioms/stdlib.md — os 4 namespaces STDLIB com BAD/GOOD/WHY
  - S4 completo — linha STDLIB na matriz de módulos + nota do plano
  - contagem da suíte pós-merge (1249/0/64-skip)
  - update PRÓXIMO PASSO and remove legacy heartbeat (auto-loop.sh ativo)
  - add specification-gaps.md with compiler gaps (R6, HW001, CONC001, etc.)
  - registra ldc2_w no DOING + IMPLEMENTATION_PLAN (follow-up d953d92)
  - STDLIB S2a.2 FEITO (257b9b0) — linha de PRÓXIMO PASSO corrigida (fica em S2a.3)
  - PRÓXIMO PASSO — estender emitLinear p/ long/casts c/ guard de tipo (lesson bug 62)
  - sweeps R6 do decompiler (control-flow + numérico) — tudo degrada honesto
  - STDLIB S1+S1a FEITO (d0b829a) — registro da lição do inline de constant JS + PRÓXIMO (S1b math Double, S2 strings)
  - sincroniza tabela de testes (DoD-docs) — 1218 total, UiE2ETest 27, browser 16
  - UI005 readonly/name FEITO na matriz + índice README desatualizado corrigido
  - RETRATA 2 falsas falhas UI (build stale meu pós-rebase; suíte fresca 1091/59bug59 + 126 demais = verde) + EDI001 codeAction FEITO (4329898)
  - UI002 FEITO (warning único no interpretador) — matriz + DOING
  - codeAction feito + registro das 2 falhas UI003/UI006 (lane UI, regra 3)
  - esclarece contagem da suíte (qemu presente vs ausente)
  - suíte completa exige -Dmaven.test.failure.ignore=true (lição 08/09)
  - PRÓXIMO PASSO — fila UI (UI002 warning, UI006 residual, UI007 bloqueado)
  - UI006 FEITO (key/value/x/y + widget.on) — matriz + corpus
  - UI003 FEITO (fieldset/iframe/video/audio/hr) — matriz + corpus
  - Fase D (Type Recovery) marcada completa — provas 367d6c4
  - DD-01 — finally no caminho return (bug 45) vira plano de design + limpeza
  - 38-editors — tutorial kof editor (§27 pede corpus passo-a-passo)
  - PRÓXIMO PASSO — varredura de exclusões obsoletas da matriz de conformância
  - degrau 12 — docs/editors/* + corpus (training/cli, EDITOR_SUPPORT)
  - DOING — degrau 11 feito (hook pós-instalador, e7e3564)

### Refactoring

  - S1a stdlib — JvmRuntimeCallDescriptors 504→354 (callReturnDescriptor → JvmRuntimeReturnDescriptors, gate ≤500 limpo na lane) + docs/development/plan-stdlib-expansion.md (mapeamento da arquitetura real da stdlib: Kof<Domain>.java→typer→3 backends, existente vs lacunas P0-P2, degraus S1-S9) + claim STDLIB no DOING

### Tests

  - reabilita native p/ channel-spawn (bug 50) e adiciona spawnexpr-return (bug 46)
  - json.decode<List<Record>> Native dá JSN004 (gap honesto, não link fail)
  - isolamento spawn { return 42 } sem captura — separa causa captura vs return lambda
  - bug 50 — channel+spawn no Native (valida fix candidata futex WAIT quando build disponível)
  - SG-006 — short-circuit && no JS/JVM (String? null && length > 0 não NPE)
  - spawn-expr lambda literal com return + handle no Native; docs bug 61 gap honesto FFI001
  - Fase C — trava degradação honesta de do-while com corpo ramificado
  - Fase C — trava laço aninhado + corpo não-linear (verificação com probes)
  - staticfield/staticpluseq nos 4 targets (bug 41 já corrigido no Native)

## [0.3.6-beta] - 2026-09-09

### Features

  - introduce kof.random namespace with various random generation functions and runtime support

## [0.3.7-beta] - 2026-09-10

### Bugfixes

  - preservar operandos da pilha em if/switch-expressions (#69)

## [0.3.8-beta] - 2026-09-10

### Features

  - implement kof.random namespace with double, boolean, int, and hex functions; add tests for cross-platform compatibility

### Bugfixes

  - random.double() usava 2^52 como divisor (constante .Lrnd_two53 errada) — corrigir para 2^53 nos runtimes x86 e riscv/aarch; docs: gravar regra de organizacao de documentacao no AGENTS.md + known-bugs SS79 + DOING.md

## [0.3.9-beta] - 2026-09-10

### Documentation

  - consolidar classificacao docs/development pela nova regra - mover planning-switch-expr + planning-mutability (FEITOS) p/ docs/, planning-finally-return (PROPOSED/zero codigo) p/ future/; classificar plan-stdlib-expansion por degrau (S7 unico aberto); atualizar README do indice + DOING.md

## [0.3.10-beta] - 2026-09-10

### Features

  - time.addDays/diffDays em data ISO (JVM/Script via java.time) + gate TIME002 em JS/Native; matriz stdtime2 + KofTimeE2ETest + docs (stdlib/learn/matrix/plan); DOING.md

## [0.3.11-beta] - 2026-09-10

### Features

  - time.addDays/diffDays em JS (algoritmo civil sem Date, paridade byte-idêntica); TIME002 resta só Native; matriz stdtime2 JS DONE + gate KofTimeE2ETest + docs (stdlib/learn/matrix/plan); DOING.md

## [0.3.12-beta] - 2026-09-10

### Bugfixes

  - pad4 de ano em time.addDays JS (parity byte-idêntica com JVM %04d; ano<1000 divergia) + stdtime2 trava 0999/0001/1700-02-28; DOING.md com design fechado do S7c x86

## [0.3.13-beta] - 2026-09-10

### Features

  - time.addDays/diffDays no native x86 (RuntimeTimeIso — parse ISO + inversa civil Hinnant asm; round-trip exaustivo 1..9999 + fuzz C 200k) + gate TIME002 afunilado p/ riscv/aarch (precedente NET001) + matriz stdtime2 roda x86 local + docs; DOING.md

## [0.3.14-beta] - 2026-09-10

### Bugfixes

  - transaction aninhado no native x86 não comita o escopo externo — .Ldb_tx_handle dono + flag-owner no record do try (paridade §77 JVM) + teste E2E sqlite; known-bugs/DOING

### Documentation

  - carry JS bool = false alarm (matriz stdmath prova println true/false no JS; isBoolOperand converte 1/0) — fechar após ler randomShapeJs
  - PRÓXIMO PASSO refina S7c-1 (bloqueio qemu/toolchain documentado) + tarefa (B) paridade JS bool como próxima executável sem qemu

## [0.3.15-beta] - 2026-09-10

### Bugfixes

  - math.is*/random.boolean retornam boolean JS real — boolExpr==true funciona no JS (paridade JVM/Native); randomShapeJs ganha o assert que native já tinha; FALTAM strings/validation/security (guards)

### Documentation

  - §80 math/random feito nesta sessão; PRÓXIMO PASSO = strings/validation/security (guards)
  - plan-stdlib-expansion + stdlib.md + learn/39 atualizados — addDays/diffDays x86 FECHADO (TIME002 residual só riscv/aarch)
  - status PARCIAL — math/random corrigidos; strings/validation/security documentados c/ nota dos guards return 0
  - bug paridade JS Bool — boolExpr==true sempre false (funções stdlib retornam 1/0; print coerce mas == usa === cru; evidência no .mjs gerado) + PRÓXIMO PASSO com fix (opção A, todos os sites incl guards validation)

## [0.3.16-beta] - 2026-09-10

### Bugfixes

  - Bool==true no JS normalizado no chokepoint da comparação — paridade JVM/Native

### Documentation

  - S7c Native x86 addDays/diffDays FEITO (cd622c47) — header estava stale

### Tests

  - stdstrings ganha caso `isAlpha("Hello") == true` — 4 targets
  - matriz stdmath ganha os casos `==true`/`==false` — 4 targets travam a comparação

## [0.3.17-beta] - 2026-09-10

### Tests

  - stdvalidation ganha `isCpf==true`/`isCpf==false` — fecha a família flagged como FALTAM

## [0.3.18-beta] - 2026-09-10

### Documentation

  - §80 fechado + endurecido (matrizes stdmath/stdstrings/stdvalidation); PRÓXIMO PASSO = S1b math Double (ou uuid.isUuid p/ risco menor)

## [0.3.19-beta] - 2026-09-10

### Features

  - uuid.isUuid(STR)->Bool — 4 targets, gate UUID001 honesto riscv/aarch

## [0.3.20-beta] - 2026-09-10

### Refactoring

  - JvmStringMathRuntime 504→465 + JvmUuidRuntime novo (58) — split do próprio 79d2668a

## [0.3.21-beta] - 2026-09-11

### Features

  - implementar uuid.v7() ordenado no tempo conforme RFC 9562

## [0.3.22-beta] - 2026-09-11

### Features

  - math.lerp/percentage/isInteger/isDecimal (Double) em JVM/Script/JS/x86
  - add lerp, percentage, isInteger, and isDecimal functions for pure Double operations
  - add sqrt function for Double type with IEEE NaN handling; update conformance matrix and documentation
  - SG-009 — subtipagem nominal em isAssignable (SEM021)
  - SG-005/SEM049 — deref de T? sem narrowing é erro compile-time
  - SG-008/bug 87 — null safety: ban de literal null (SEM048) + Map.get()->V? sempre + T?==null sem NPE
  - S12b — validation.formatCnpj nos 5 alvos (pontuação BR)
  - S3b-ext — uuid.isUuid nos 5 alvos (shape RFC 4122)
  - S7-ext — time.isWeekend nos 5 alvos (wrapper dayOfWeek>=6)
  - S12 — validation.formatCpf/formatCep nos 5 alvos (pontuação BR)
  - S11 — strings.uncapitalize nos 5 alvos (espelho byte-a-byte do capitalize)
  - S10b — randomString(n, alphabet) nos 5 alvos + fix paridade B27
  - SEM047 — sobrecarga top-level homonima da erro (SG-011B)
  - SG-002 — remove tokens mortos (decisao do maintainer: se nao tem uso, remove)
  - SG-020 — modelo de memoria concorrente SC + fix bug 79 (POP de long)
  - S10a — kof.random (randomInt/randomBoolean) nos 5 alvos
  - SG-014 — guardas em pattern matching (case T v if cond)
  - SG-011 — funcao aninhada com hoisting (inner primeiro, outer chama e aguarda)
  - SG-012 — inferencia contextual de lambda em map/filter/reduce
  - SEM046 — private/protected checados em compile-time (SG-013)
  - SEM045 — clausula throw validada, nao mais decorativa (SG-019)
  - SEM044 — Int main() rejeitado, entry point e so main() (SG-018)
  - SEM043 — implements sem cobrir metodos da interface da erro (SG-015)
  - SEM042 — tipo aninhado (class dentro de class) vira erro (SG-016)
  - SEM041 — new de classe abstrata vira erro de compilacao (SG-017)

### Bugfixes

  - bug 99 — String method com formal String recebia Int/Char → 4 backends divergiam (JVM VerifyError / x86 SIGSEGV / JS -1 silencioso / interp CCE)
  - bug 97 face JS — String.compareTo/hashCode (JsCallEmitter roteava p/ String.prototype, que não tem — TypeError)
  - bug 97 (cont.) — String.equals roteado p/ kof_string_equals (x86_64)
  - bug 97 — String.compareTo/hashCode emite nos nativos (x86_64)
  - bug 95 — 2+ split no mesmo programa quebrou o assembler (x86_64)
  - bug 43 faces indexOf/lastIndexOf — code units UTF-16 (x86_64)
  - bug 43 face substring — code units UTF-16 (x86_64)
  - bug 78 — transaction aninhada participa da tx externa (x86_64)
  - bug 43 residual — charAt UTF-16 code units (x86_64) + bug 44 reconciliado
  - bug 82 (face cross) — toDouble/toFloat riscv64/aarch64 (parser FP novo + tradutor aarch FP)
  - bug 88 — riscv/aarch valueOf(T?) despacha pelo INNER (regressão SG-008 cross)
  - bug 44 — println(double)/toString(double) com o contrato do JDK
  - bug 82 (x86) — toDouble/toFloat no contrato do JDK (parser reescrito)
  - bug 80 — println(Long.MIN) riscv/aarch correto (magnitude negativa, técnica JDK)
  - bug 79 (x86) — toInt/toLong alinhados ao contrato JVM (trim+dígito+overflow+throw)

### Documentation

  - merges beta-0.4.0 + main(uuid.v7) resolvidos — PRÓXIMO PASSO lane Native JS §97
  - NAT-STR01 estendido p/ toUpperCase/toLowerCase de instância (varredura String parte 2)
  - §97/§98 — String.compareTo/hashCode link-fail + `<`/`>` Unspecified (varredura String parte 2)
  - merge main→beta pushado 082784cb — PRÓXIMO PASSO atualizado (fila da lane; nunca pushar main sem pedido)
  - §79 fechado — header stale; 3 faces corrigidas verificadas na menor repro
  - §89 registrado — conversão numérica de PRIMITIVO (.toDouble()/.toInt()) quebra link nos 3 nativos
  - bugs 82-face-cross + 88 fechados; PRÓXIMO PASSO atualizado (gap boxing toDouble / FLT001 print / DD-*)
  - varredura final spec-gaps — §39 (bug 87 corrigiu), SG-E1/E2/E3 fechados, resumo atualizado
  - validação doc↔código do modelo de memória concorrente
  - §82 registrado — toDouble/toFloat nativos fora do contrato (matriz medida)
  - bug 81 — JS toLong = Number/double (overflow ±2^53 não lança); DOING atualizado
  - §79 corrigido — riscv/aarch ' -42 '->42 (sinal perdido), não -42
  - STDLIB — bug 79 reivindicado; PRÓXIMO PASSO = U2 fix x86 (RuntimeStringParse)
  - bug 79 — toInt/toLong nativos divergem do contrato JVM em entrada inválida (R6)
  - STDLIB — corpus fechado; PRÓXIMO PASSO = checar resposta da mantenedora às notas de design
  - corpus em dia — idioms/stdlib cobre as entregas da sessão
  - DD-STDLIB-02 — semântica de tempo restante (fuso/assinaturas/format) é decisão, não edição
  - S3b-ext feito — PRÓXIMO PASSO exato = formatCnpj (formatPis ambíguo -> nota)
  - DD-STDLIB-01 — retorno Array na camada de dispatch (S10c) é decisão, não edição
  - lane spec-gaps COMPLETA — 11 decisões do maintainer aplicadas
  - SG-003/SG-011 a SG-019 — registra decisões do maintainer aplicadas
  - STDLIB — gate restaurado pelo autor (dd8a91fd fecha 53264c9f); lição 3ª vez do inlining (rm -rf target pós-pull de constante embutida); lane retoma p/ random S10

### Tests

  - STDLIB — paridade cross de validation/net/time/escape em qemu (auditoria R6 parte 2)
  - STDLIB — paridade cross (riscv/aarch) do core stdlib em qemu
  - varredura R6 completa — +5 namespaces (security/orm/config/cache/log)
  - varredura R6 estendida a db/http/cache/mq (4 famílias de outras lanes)
  - varredura R6 — 8 namespaces stdlib nunca são silenciosos
  - paridade kof-script × JVM compilado da stdlib nova (S10–S12b)

## [0.3.23-beta] - 2026-09-13

### Features

  - adicionar suporte à flag --json no comando kof check

## [0.4.0-beta] - 2026-09-14

### Features

  - S1b.3 math.roundTo(value, decimals) — meio-para-longe-do-zero por escala decimal determinística, sem libm (5 alvos; DECISIONS §3)
  - implement saturating casts for Double/Float to Int/Long (JLS 5.1.3) across all targets
  - add ui-config block and kof.config functions
  - time.tzOffsetSeconds — fuso do host (D1), 3 alvos + gap honesto TIME003
  - time.parseDateIso -> serial daysFromEpoch nos 5 alvos (D4)
  - time.hoursBetween (D3 floor simétrico) nos 5 alvos + fix emit x86 7+ args
  - time.todayIso/formatDateIso/isToday nos 5 alvos (D-STDLIB ratificado 13/09)
  - Fase C degrau 2b — narrowing ifnull/ifnonnull (0xc6/0xc7) recupera e EXECUTA
  - Fase C degrau 2a (if-else com sequela) + fix raiz do blockCondition (aridade exata; destrava código errado latente)
  - S13b math.parse{Int,Long,Double}OrDefault — briefing §43 (falha DEVOLVE default, 5 alvos) + fixes de causa raiz KofStd/rota JS
  - S13a math.parseInt/parseLong/parseDouble — fachada sobre kof_string_to_* (4 targets, zero runtime novo)
  - Fase C degrau 1 — join de if-then PURO sem else recupera e EXECUTA (oracle 107/101)
  - add support for compound bitwise and shift assignments in the parser and lowerer
  - web server no target JS (GraalJS hostless) + KofJsWebQueue event-loop + teste E2E
  - var local + interface default body + gap constante interface (R6) + testes - dono 192.168.100.22
  - descarta annotations Java (@Override/@Deprecated/@SuppressWarnings) + teste - dono 192.168.100.22
  - assert Java -> assert() Kof + gap honesto for-virgula (R6) + testes - dono 192.168.100.22
  - interface extends Java -> Kof + gaps honestos labeled/anon (R6) + teste - dono 192.168.100.22
  - estágio-3 — record implements interface INTERNA do mesmo pacote (−93 stubs)
  - corpo de enum ignorado + multi-declaracao local Java -> Kof + teste - dono 192.168.100.22
  - gaps honestos try-with-resources e tipo qualificado (R6) + teste - dono 192.168.100.22
  - generics + construtores com corpo + fix loop infinito no parseMember - dono 192.168.100.22
  - gaps honestos varargs e tipo aninhado (R6) + teste + plano sync - dono 192.168.100.22
  - descarta clausula throws Java (Kof nao declara) + teste + plano sync - dono 192.168.100.22
  - cast (T)expr -> expr as T + instanceof Java -> Kof + teste + plano sync - dono 192.168.100.22
  - arrays + declaracoes []/generics Java -> Kof + fix new Int[n] + gap honesto p/ initializer - dono 192.168.100.22
  - try/catch/finally + throw + bare-call Java -> Kof + teste + plano sync - dono 192.168.100.22
  - switch-statement Java -> Kof + teste + plano sync - dono 192.168.100.22
  - do-while Java -> Kof + teste + plano sync + DOING (11 orfaos marcados) - dono 192.168.100.22
  - enhance record handling with interface resolution and add tests
  - record genérico emite type-params EXATOS (record Gen<T>)
  - Fase E — Java record → record Kof (~195 stubs, maior fonte única)
  - IntelliJ degrau-10 honesto (filetype+tools+README, 17/17) + DOING - dono 192.168.100.22
  - implement pow function across multiple runtimes and update documentation
  - random.randomBytesHex(n)->String (decisão 6a 13/09)
  - add OBJECT type and enhance Map.put handling for two-slot values
  - add comprehensive plans for Kof Spring Starter and stdlib time design
  - supervisor.startAll() — laço selectAny único para N filhos com wrapper de identidade (id/motivo), opção 1a ratificada; gates S2 JVM+interpretador; sincroniza célula stdsqrt pós-fix bug 94; KofSupervisorE2ETest 8/8, gate 1620/0
  - add JsRuntimeUiJsonMap and JvmRuntimeJsonMap for JSON map decoding
  - implement decode<Map<String,T>> for JSON parsing and binding
  - runtime JS por alcançabilidade ligado no writer
  - inventário do runtime JS por unidade de topo
  - add 'isEmpty' method support for strings and fix related issues
  - G-0 bloco-header riscv + guard OOM honesto (face 1 do GC cross)
  - PODA riscv64+aarch64 ligada — port riscv da S-3 (hello riscv 258→103 syms)
  - RiscvSlices — mapa de 48 peças do runtime riscv por reflexão (espelho riscv da S-2, zero mudança de emissão)
  - add gh-as-agent and issue-watcher scripts for GitHub issue management
  - add RiscvSlices class and corresponding tests for runtime assembly validation
  - poda x86 do runtime por alcançabilidade — hello 627→37 syms (138.928B→32.520B)
  - precursor .L-aware do S-3 — 119 arestas de rotulos locais cross-slice medida e codada no mapa
  - mapa de fatias do runtime x86 por reflexao derivada do fonte de producao
  - harness de tamanho (ArtifactSize ELF64 puro-Java) + gate anti-inchaço + kof build --print-sizes
  - println(<coleção>) riscv64+aarch64 — B39 + FLT001 p/ coleção FP (paridade com x86/JVM)
  - println(<coleção>) com toString real em asm + fix colateral §138 (text-block \n → .asciz quebrado)
  - implement top-level function overloading with distinct signatures
  - implement top-level function overloading resolution and diagnostics for concurrency helpers
  - #91 — CONC001 em compile-time para auxiliares de concorrência ausentes em riscv64/aarch64
  - kof_multi_alloc riscv64 + aarch64 (fatia B37) — faces cross fechadas
  - implement recovery for switch statements and add related utilities
  - §133 — fetch assíncrono real no Node/browser (spawn http.* + await)
  - implementar strings.indent e strings.dedent nos 5 targets
  - OTP núcleo (issue #83) 1ª fatia — pacote virtual kof.supervisor, JVM+Script, gates OTP001/OTP002
  - MATH001 FECHADO — sqrt/lerp/percentage/isInteger/isDecimal riscv64/aarch64 (S1b.2)
  - split remove vazios TRAILING no riscv64/aarch64
  - TIME002 FECHADO — addDays/diffDays em data ISO riscv64/aarch64 (S7c-1)
  - String equals/compareTo/hashCode + substring -1 no riscv64/aarch64 (B36)
  - busca UTF-16 riscv64/aarch64 (B35) — indexOf/lastIndexOf + from
  - add UTF-16 support for string operations in riscv64 (B34)
  - bug 43 estágio 2 riscv64/aarch64 — substring/indexOf/lastIndexOf em code units UTF-16
  - bug 43 faces riscv64/aarch64 — length/charAt em code units UTF-16
  - addDays/diffDays ISO em riscv64/aarch64 (fatia B33)
  - addDays/diffDays ISO em riscv/aarch (B33)
  - bug 97 faces riscv64+aarch64 — compareTo/hashCode/equals cross em code units UTF-16
  - primitivas visuais por widget + fix bug 102 (col/row dropados) — issue #78
  - math Double (sqrt/lerp/percentage/isInteger/isDecimal) em riscv/aarch
  - add support for UUID v7 (RFC 9562) in stdlib

### Bugfixes

  - §129 `throw` dentro de worker `spawn` no Native x86_64 não faz mais longjmp no handler da thread main — `kof_exc_chain` agora é TLS por thread (`.tbss`+`%fs:@tpoff`) e o trampolim do spawn instala handler próprio do worker que publica a causa no handle; `await`/`awaitTimeout`/`selectAny` a relançam (riscv/aarch seguem OTP001)
  - §180 `println(double/float)` no Native x86_64 agora é JDK `Double.toString`/`Float.toString` (RuntimeDtoa: `%.*e`+`strtod` shortest round-trip + reformatação Java; Float com forma própria) — `doubleprint` com Native incluído
  - §181 doc-sync + baseline HELLO_JS 8.297→13.007 (#132) + UIW050 event-handle em estágio
  - §181 regressão do cast saturante — bits de double no x86 + labels únicos riscv/aarch
  - Bool[] unificado como boolean[] (json.decode<Bool[]> era o outlier int[]) + golden do bloco ui-config no registry JS — #132
  - correct array access opcodes for Bool[], Byte[], Short[], and Char[] types
  - parse ISO de kof.time ESTRITO nos 4 targets + testes time blindados ao relógio
  - synchronized block -> gap honesto R6 + encerra lane - dono 192.168.100.22
  - enforce array bounds safety (KOF-SBD-001)
  - escapes unicode/char + lambda 1-param + gaps enum/record/init/abstract - dono 192.168.100.22
  - ~x/classe-local/; vazio/main(args) + split TranslateNew - dono 192.168.100.22
  - switch-expr + method-ref/text-block/instanceof-pattern/import-static/Math. viram traducao/gap honesto - dono 192.168.100.22
  - §176 — compound em array no JS + lambda com local/return (VOID) + handle kof.ui no invoke
  - this(...)/wildcard/lambda-bloco viram gap/emissao honesta + split TranslateTypes - dono 192.168.100.22
  - enforce array bounds safety (KOF-SBD-001)
  - "".toDouble() LANÇA nos 5 alvos (era 0.0 silencioso no x86/riscv) — paridade JDK
  - default de interface vira gap honesto (corrige 3ab4c99e) + campos static qualificados nas funcoes hoisted - dono 192.168.100.22
  - §174 — return/throw dentro de if dentro do try compilava p/ JS (COMP002)
  - escapes string/char re-escapados + final local/param descartado - dono 192.168.100.22
  - literais numericos Java (10L/1.5e3/0x1F/1_000 etc) deixam de quebrar o parse - dono 192.168.100.22
  - §173 — ++/--/compound em Long/Double/Float + elemento de array (4 targets)
  - bloco de init de instancia dropado silenciosamente vira gap R6 + teste - dono 192.168.100.22
  - array-initializer em CAMPO vira gap honesto R6 (era output truncado invalido) + teste - dono 192.168.100.22
  - bare 'kof editor' = alias de detect (§6) + teste update - dono 192.168.100.22
  - shift composto (<<=,>>=,>>>=) grava o RHS (L2I na contagem) - §172 - dono 192.168.100.22
  - preserva parenteses + bitwise/shift/qualificados (3 bugs de correcao Q4) + testes - dono 192.168.100.22
  - json ganha handler no caminho semântico — aridade errada vira SEM025 no check
  - re-mede baseline do runtime JS hello (7.700 -> 8.297) — shim DOM #121
  - bitwise/shift com Long misturado — JVM VerifyError + JS TypeError/máscara/overflow
  - interpretador lia 2º parâmetro largo (Long/Double) como null
  - shim DOM (kofMakeEl) ganha dataset/disabled/classList
  - overload de MESMA aridade e tipos diferentes no Native → SIGSEGV
  - SEM049 reporta posição real do deref (não mais 0:0)
  - lambda que chama outra função-variável capturada sem receiver
  - Long = BigInt no backend JS (decisão 5b, paridade 64-bit real)
  - json.encode(Map) no JS devolvia {} — JSON.stringify(new Map()) não enumera; helper kofJsonEncodeMap (chaves SORTED) + ramo no JsRuntimeOps
  - sobrecarga de método por assinatura nos 4 backends (decisão 10a)
  - tabela de cancel por TID real + probe linear (decisão 8a) — fim da colisão de hash entre workers
  - conversão numérica em primitivo = alias do  + warning SEM090 (decisão 3a)
  - chaves sorted nos 4 backends; restaura kof_heap_root_start/end
  - raízes do GC cobrem os estáticos do programa + gate ≤500→600
  - finally roda no caminho return — FinallyFrame na IR (store #retVal + jump return-finally, encadeia try aninhado), lambda/método salvam pilha de frames; JS: try/finally nativo + epílogo return-only, #retVal pre-declarado; gates finallyReturnJvm/Js; suíte 1627/0
  - shim passa a atribuicao globalThis — const no core quebrava poda e registry
  - shim passa a atribuicao globalThis — const no core quebrava poda e registry
  - WEB001 no compile das funcoes de contexto Native + Map.put de Long nao crasha mais
  - §155 tipo-função em type-args (List<(Int)->Int>) preserva espaços; §156 registrado (List heterogêneo de lambdas, CCE JVM)
  - §127 cast para tipo-função (as ()->T) — parser type-ref + checkcast na interface SAM
  - §153 switch-expressão rejeita corpo de case em bloco (PARSE094, R6)
  - query()/header() agora sao String? no kof.web — narrowing obrigatorio
  - shim kof_platform no core JS — ReferenceError vira erro claro fora do GraalJS
  - §94 EQ/NE de Double/Float no interpretador agora IEEE (NaN==NaN false, +0.0==-0.0 true)
  - browser real no macOS — bundle Chrome + fallback Safari
  - json.decode<Map<String,T>> — decoder real JVM/Script/JS + JSN004 no Native
  - §149 JS if-throw/loop + §150 switch-expr enum primitivo + §151 contains enum Native
  - app.listen(String) -> SEM025 no kof check (aceita só Int)
  - sleep(ms) sem receiver resolve como time.sleep(ms)
  - fecha sintaxe da fatia B40 — parêntese órfão em """);" travava o build do repo INTEIRO
  - correct string termination in NativeRiscvAsmRtB40
  - add widening for compound assignments and field access in ExpressionAssignmentLowerer
  - kof_double_mod riscv64 (B40) + dispatcher MOD float/double; known-bugs §145/§146/§147 ✅; DOING bugfix-101 FEITO
  - constante de enum como EXPRESSÃO tipava UNKNOWN — SEM032 falso em switch-expr exaustivo sobre enum
  - update 'PRÓXIMO PASSO' section to correct false premise regarding qemu/toolchain cross availability
  - remove JLS-15.28 constant-fold do NativeRiscvAsm (causa raiz do falso split-brain do G-0)
  - POP2 nativo descarta 1 qword (não 2) — SIGSEGV em descarte de Long/Double
  - widening abençoado converte, narrowing rejeita — escrita de coleção pinada (IR compartilhado) + literais cobertos
  - Native concat "a" + <Int?-null> → lixo de ponteiro; guard único box+valueOf (IR compartilhado, 4 targets)
  - ramo null de if/switch em retorno/slot primitivo-nullable → default (mesma opção A; 4 targets)
  - gate ≤500 era decorativo — vira RATCHET com baseline de dívida e entra no CI
  - tick resolve a porta do servidor que REALMENTE hospeda a sessão (probe /session/<id> em 9091-9095, fallback 9093) — porta fixa 9092 é OUTRA sessão TUI e injetar nela dava 'Session not found' a cada tick desde 12:35
  - println(f()) com T? f() retornando null → default do primitivo (decisão A) + JS fold f()==null no parser
  - elimina flake no contador SSE_EVENTS_SENT sob carga concorrente
  - §137 — round-trip do statement-switch (hoist var que escapa do case + static preservado + main() forwarder) — DecompileTest 45/45
  - §134 residual — import wildcard externo (ext.*) qualifica pelo ExternalClasspath
  - §128 — selectAny de Handle<Int> com uso primitivo unboxa (era VerifyError)
  - switch decompilado compilável+executável — hoist de locals + static na assinatura
  - §127 — map get/remove de MISS com valor primitivo dá default (0/false), não null
  - §126 — SEM056 rejeita escrita heterogênea em container PINADO (decisão mantenedora: opção ii)
  - §134 — external classpath (0.3.1→0.4.x) — import de dependência real (--classpath/--deps) apanhava PKG006; estática externa apanhava SEM011
  - §129 — Set.remove apagava o elemento no índice==TAG (silent corruption); passa o índice achado
  - §126 lado ARG — tag de String é CONJUNÇÃO elem×arg; tipos errados viram miss seguro como o JVM
  - §124 — println(String? null) NPEava no interpretador (JVM/Native imprimem null)
  - §123 — Map<Int,*> funcionava só por acaso; chave Int SIGSEGVava (tag de chave no header)
  - §122 — índice não-Int em List.get/set/remove vira SEM055 (opção B)
  - §114 face String — equals de record compara campo String por CONTEÚDO
  - §121 FECHADO — Int→slot Long[] converte no IR (bloco era if{} que só comentava)
  - §113 FECHADO x86 — kof_multi_alloc aloca o multi-dim (op IR não cai mais no default)
  - §110 corpo de método re-analisado no MESMO escopo -> SEM024 falso (impeditivo do host OTP puro-Kof)
  - bug 104b-ii face char — println(char-em-coleção) paridade 4/4
  - interpretador boxia Bool em colecoes -> [true, false] == JVM (merge com §112)
  - bug 112-JS — prev ausente de put/remove imprimia null (4/4 targets)
  - bug 112 — prev null de put/remove (VerifyError/NPE/SIGSEGV) + set.add sempre-true no interp
  - bug 111 — split sem trim de trailing (Java) + sentinela substring 0->-1
  - bug 110 — literal -0.0 virava +0.0 (DCONST_0 colapsava o sinal)
  - bug 104c — membership de record por conteúdo no JS (kofValEq)
  - bug 109 — map.get(<primitivo>) CRASHAVA no JVM (NoSuchMethodError Boolean.intValue)
  - bug 103 — tradutor riscv→aarch lw virava ldr w (zero-extend) onde riscv é sign-extend
  - bug 107 — println(coleção) com formato do contêiner JVM (kofFormat) [renumerado 106→107]
  - bug 106-JS — println(coleção) com formato do contêiner JVM (kofFormat)
  - bug 104b-i — equals herdado de classe não-record (LINK_FAIL → identidade)
  - random.int(bound) riscv/aarch LOOP INFINITO — rejection sampling estourava 2^64
  - bug 104a — KofObj sobrescreve equals/hashCode/toString (records em coleção = conteúdo)
  - bug 103 — subscript `x[i]` em String/List/Map/Set → SEM054 (opção B)
  - bug 102 — indexOf/lastIndexOf/startsWith(s,from) respeitam o índice inicial (paridade absoluta)
  - bug 100 — SEM051 generalizado (qualquer não-String em formal String) + equals(não-String) fold no Native
  - bug 98 — < <= > >= em String → SEM053 (rejeitar, paridade absoluta)
  - runtime JS ao vivo sem CSS p/ layout+form (issues #75/#76) + meta viewport webview (#77)
  - bug 96 — funções strings.* como método de String → SEM052 (paridade absoluta)
  - bug 100 — hijack x86: método de usuário com nome de String-op era sequestrado pelo intrínseco (p.trim() → lixo silencioso)
  - bug 100 — Char em método String (indexOf/contains/split/...) → SEM051 (opção B, rejeitar)
  - bug 44 residual — spelling inf/-inf/nan → JDK Infinity/-Infinity/NaN (x86_64)
  - bug 99 (R6) — Int.MAX_VALUE/<primitivo>.<campo> agora é SEM050, não lixo/crash
  - bug 97 (cont.) — String.equals roteado p/ kof_string_equals (x86_64)
  - bug 97 — String.compareTo/hashCode emite nos nativos (x86_64)
  - bug 95 — 2+ split no mesmo programa quebrou o assembler (x86_64)

### Documentation

  - §186 — borda medida (literal direto × qualquer expressão) + Script também falha
  - §187 novo — Char[] fora de faixa não estreita no Native (e JS); célula charnarrow
  - §185 causa raiz corrigida (coerceFor, não o arrayStore morto) + face Bool[]
  - §184/§185 — arrays de tipo estreito (Q4 no #132); célula narrowarr/chararr prova
  - §181 seq do header sync + matriz sem linha duplicada de castrange
  - fila time EXECUTADA — DECISIONS + README marcados (S7e-S7h, 6/6)
  - link known-bugs.md #101/#102 to their upstream issues
  - §182 — parse ISO de kof.time com campo de sinal diverge (JVM/Script vs Native/JS)
  - §181 fix JS — alerta de dupla-avaliação do operand (helper/IIFE obrigatório)
  - sincroniza registro da sessão (§180d + suíte 1509/35/5/207)
  - §180 face (d) — -nan (NaN negativo de libm) não é normalizado no Native
  - registra caça Q4 da sessão (§180c/§181/§181d/bug82) + suíte verde
  - fila ABERTA 10→12 itens — registra §180/§181 na linha de varredura
  - bug 82 — registra face do sufixo `d/D/f/F` (Native/JS rejeitam; JVM/Script aceitam)
  - §181 face (d) — toInt()/toLong() (§89) sofrem o mesmo desvio
  - §181 — cast FP→Int/Long fora de faixa/NaN/Inf divergente no Native e JS
  - PRÓXIMO PASSO degrau 3 — expr-fallback MORTO por 2 medicoes (gate !isLoopHeader insuficiente, cond aninhada com join-back-edge); so pos-dominador + goldens por shape em sessao dedicada
  - degrau 3a negativa REFORCADA — gate !isLoopHeader provado INSUFICIENTE (2a quebra do diamond)
  - degrau 3a TENTADO-REJEITADO registrado — fallback de expr na blockCondition reabre o trap 3 via header de loop (medição, nao opiniao)
  - §180 face (c) — println(Float) no Native imprime a expansão double
  - §180 — overclaim do bug 44: double→string no Native x86 não é JDK Double.toString
  - alinha numeração pós-rebase (§177/§178 corrigidos, §179 catalogado) — dono 192.168.100.15
  - §176 WEB001 colidiu com lane JS/web no remoto — renumerada a minha seção p/ §177 - dono 192.168.100.22
  - gate pos-rebase medido (1497/33/5/194) - dono 192.168.100.22
  - desambiga colisão §174 — minha seção KofWebJs → §176 (a §174 legítima da lane bugs-and-gaps/ é anterior e mantém o número)
  - degrau 2b registrado (plano + DOING PRÓXIMO PASSO) — narrowing 308 candidatos, −10 medidos, anti-fachada Q7 (contCond revertido)
  - sincroniza §168 como CORRIGIDO na fila do known-bugs.md
  - §168 CORRIGIDO (efeito 3ab4c99e) + EDI001 degrau-13 gate verde - dono 192.168.100.22
  - §168 CORRIGIDO (SEM025 json, efeito de 3ab4c99e, re-verificado no kof check) + EDI001 degrau-13 gate verde - dono 192.168.100.22
  - Fase C degrau 1 FEITO (e17ac9e1) + PRÓXIMO PASSO (joins aninhados = pós-dominador real) + §168
  - plano único roadmap §23 + cluster migração fundido no umbrella
  - SG-021 — pedido de json.encode indentado registrado (regra 6, sem decisão)
  - §168/§169 no known-bugs (causa raiz + menor repro + prova) + DOING da lane 9094
  - Fase C — diagnóstico EXATO do sub-caso if-sem-else pronto p/ executar (fixture J.g reproduzido: oracle g(6)=107/g(1)=101 medido com java real; traço do CFG do stub: linha 280 anda p/ o join + 275 re-entra → 206 recusa; regra: then.succ==[exitStart] && preds⊆{b,then}; mecanismo: parâmetro stop no walker — NÃO é 3 linhas, sessão dedicada; golden de execução OBRIGATÓRIO no mesmo commit, padrão DecompileTest:747-791). DOING PRÓXIMO PASSO aponta p/ o diagnóstico. dono = 192.168.100.17
  - Fase C — gargalo medido (StoreCat: struct() linha 206 recusa join = 2452 stubs silenciosos; instanceof/STORE/branch são SINTOMAS do mesmo join) + plano de sub-caso estreito (if-then sem else) + pointer da infra de golden de EXECUÇÃO que JÁ existe (DecompileTest:747-791, java -cp + assertEquals stdout). R6: join errado = compilável+semântica errada = pior bug; nunca relaxar struct sem golden. dono = 192.168.100.17
  - PRÓXIMO PASSO com fila StoreCat medida + fase C gated por golden (junta de if sem else = sub-caso estreito). dono = 192.168.100.17
  - registra experimente NEGATIVO — aceitar $ no TreeScope.resolve = −9 stubs só + risco anewarray-interna (PARSE041). Probes ✓ (instanceof/as de interna compilam) mas o drop real (BuiltinTypes.isString) é astore_1+ifeq multi-stmt = Fase C; regex atual protege anewarray de interna. REVERTIDO, working tree limpo em 35fc24b8. Medição via Med+DriftCheck no mesmo corpus 688. dono = 192.168.100.17
  - registros da verdade medida — DECOMPILER estágio-2 com números da árvore limpa (1856→1660→1475, zero drift 4=4, 89 skeleton); §165 re-verificado COM node v22 = não reproduz em build limpo (trap do inlining static-final); §166 registrado: ArtifactSizeTest hello JS estoura 8297B>8085B por shim DOM #121 no CORE (bisect b05b3906→cd8ad70b) — lane JS/gate; DOING PRÓXIMO PASSO = TreeScope no statements-path. dono = 192.168.100.17
  - update ownership details and status for TRANSLATOR.md
  - DOING — lane desta sessão = bugs-and-gaps; registra sync/auditoria dos 5 registros
  - README development — suíte 1653→1662 e testes de migração 70→73 (medidos no HEAD)
  - sync contagem da suíte ao HEAD medido (1611→1662: 1479+31+5+147)
  - RECUSA de re-disparo ~09:55Z - nada sem dono na lane development (.22), fila 8 de outras lanes - dono 192.168.100.22
  - sync stdlib S1a/S1/S2/S3 FEITO + PRÓXIMO PASSO - dono 192.168.100.22
  - plano S1a/S1/S2/S3 marcados FEITOs (medição real 13/09) + DOING - dono 192.168.100.22
  - move PLAN-SOLID-500 FEITO por outra instancia preservado (regra 8) - dono 192.168.100.22
  - update PRÓXIMO PASSO with current status and reclassification of PLAN-SOLID-500
  - update PLAN-SOLID-500 path to docs/architecture and clarify status
  - add PLAN-SOLID-500 for class refactoring strategy and guidelines
  - README development — nº da suíte é a execução no host, não a linha (apodrece a cada commit)
  - §3 do README — DD-OTP S2-JVM implementado (não "implementar S2"); registra docs sync no DOING
  - sincroniza README development + PLAN-SOLID-500 com a realidade (F3 fechada 498/12 dívidas; fila bugs 8; §81/§163/§165)
  - cabeçalho do PLAN-SOLID-500 sincronizado (F3 fechada, ratchet 12)
  - §165 re-verificado (não reproduz em clean build) + sumário da lane gate/qualidade
  - re-verificação — NÃO reproduz em build limpo; era constante INLINED (JsRuntimeUiJsonMap)
  - §163 pushed + lane gate/qualidade sem trabalho novo — re-disparo recusado (fila 8, gate verde)
  - §165 JS ui-jsonmap seed-sem-export (menor repro; célula jsonenc-map só roda com node)
  - registra RECUSA de re-disparo da lane gate/docs — nada novo (gate verde, docs/development sem doc pendente, fila 8)
  - fecha varredura de overclaims — 6 células de matriz, gate final 1645/0/13-node/157
  - fecha auditoria da lane gate/docs — §131 sincronizado, fila 12→8, docs/development sem doc concluído pendente; PRÓXIMO PASSO = §81 (lane .18, não tocar)
  - corpus sincronizado — sobrecarga de método existe nos 4 backends (AGENTS.md, type-system, functions, training, status); §136 alcance atualizado
  - §131 fechado (18a64d45, 4 backends — harness 4/4 re-medido) — fila 9→8; gate pós-§131 verde 1645/0/13-node/157
  - §117 fechado (3734f2aa) — fila 10→9; gate pós-§117 verde 1644/0/13-node/157; parity KofConcurrency2Test 34
  - gate pós-§89 verde (1643/0/13-node/157) + suíte do README 1611→1643; PRÓXIMO PASSO aponta lane .18
  - §89 fechado (4 alvos re-medidos + warning SEM090) — fila 11→10; README/plan-stdlib/DOING alinhados
  - repara header corrompido do known-bugs (§11 truncado) + §106 fechado (fila 12→11) + §89 evidência corrigida (quebra nos 4 alvos, não só link nativo) + regra 9 cluster
  - RECUSA de re-disparo ~07:55Z — fila estável PROVADA (issues/PRs abertos VAZIOS, comentários terceiros = nenhum, CI unidade success, discussão #113 no caminho do interp roda e passa). Cron 9094 parado, 9093 intocado.
  - retifica causa raiz pós-rebase — lane nat 53b089fd já movia root_start p/ .data do programa (kof_gc_mark usa _end); restante real = fallback de poda por CWD; DOING sincronizado
  - recusa por lane (nada sem dono na development) — pow fechado
  - corrige extrapolação roundTo (regra 6) + sincroniza fila §1 item 6
  - sync pow FEITO + roundTo aprovado-7a (README §3) + DOING próximo passo = roundTo
  - varredura completa da fila — issues abertas = VAZIAS; discussions #25 (switch-expr já implementada) e #36 (7 bugs = #28–#35 todos corrigidos) respondidas + fechadas
  - §156 FEITO confirmado no HEAD remoto (fix+testes preservados, re-validado 340/0) - dono 192.168.100.22
  - lane 9094 zera fila de issues — #113 corrigida (root_start cobre estáticos, prova nm), #97 fechada (medido 32520B/37), #25 é feature existente
  - nota do autostash stale (superseded por HEAD; mantido, regra 8) - dono 192.168.100.15
  - recusa honesta pos-IntelliJ (nada sem dono) - dono 192.168.100.22
  - §17 editor support cita IntelliJ degrau-10 + DOING - dono 192.168.100.22
  - learn/38 IntelliJ install real (degrau-10) + DOING - dono 192.168.100.22
  - regra de identidade por IPv4 local — esta sessão = dono 192.168.100.18 (conflito com bloco .md-soltos resolvido preservando ambos)
  - sync .md soltos 13/09 (native pow S1b.2, editor degrau 12 provado, stdlib S1b.2 outro agente) - dono 192.168.100.22
  - regra 9 - agentes se identificam pelo IPv4 local (dono = IP; esta sessao 192.168.100.22)
  - cluster legado + OTP sincronizados 13/09 (contradicao LEGACY, contagem 70 testes, S2-JVM 020be966)
  - update README.md to reflect 70 tests in migration platform and clarify documentation status
  - update implementation status in LEGACY_MIGRATION.md and IMPLEMENTATION_PLAN.md to reflect 70 passing tests
  - update LEGACY_MIGRATION.md to clarify documentation status and implementation state
  - add guidelines for `check_500` gate thresholds and handling code size limits
  - regra 8 - nunca descartar trabalho de outro agente (diretriz da mantenedora 13/09, agentes em conjunto)
  - enhance comments in check_500.sh and check_500-baseline.txt for clarity and consistency
  - bump 0.4.0-beta FEITO (e8a8aeea do outro agente + meus complementos) - sem colisao, tree limpo
  - improve comments and messages in check_500.sh for clarity and consistency
  - update version references from 0.3.22-beta to 0.4.0-beta in README, check_500.sh, and versioning.md
  - update version references from 0.3.22-beta to 0.4.0-beta across multiple documentation files
  - standardize version formatting across multiple documentation files
  - roadmap.md sincronizado 13/09 (header 0.2.6->0.3.22-beta, OTP S2-JVM, VERSION) + auditoria 9 docs (nada a mover)
  - PRÓXIMO PASSO registra DD-01 FEITO 2ef6ce69 + lane sem colisão (fila 106/89/117 é do outro agente)
  - DD-01 planning-finally-return movido p/ docs/decisions/ (bug 45 FECHADO 063ed956)
  - §4.1 planning-finally-return → FECHADO (DD-01 4-targets 063ed956)
  - registra sync §45/S10c nos registros centrais (3e8167d8)
  - sincroniza §45 (finally-return) e S10c (randomBytesHex) nos registros — fila 14→13 abertos, parity/README
  - DD-STDLIB-01 movido p/ docs/stdlib/ + refs S10c sincronizadas
  - corrige cross-refs a arquivos movidos (roadmap→development/roadmap, native-multiarch, DATABASE_VISION→stdlib, PLAN-UNIVERSAL/scoped-resources, specification-gaps→bugs-and-gaps)
  - evidência CI da beta (runs failure) reforça o alerta de gate do #116
  - README §1/§4.1/§4.2 corrige localização 3-estados (6 docs → decision-pending, 4 registros → bugs-and-gaps) + OTP S2-JVM
  - lane 9094 issues — registro dos commits da fila + alerta de gate da beta
  - PRÓXIMO PASSO registra fixes de path + condição de recusa por estabilidade
  - corrige paths pós-refactor (complexity-audit docs/audits; actual-state → bugs-and-gaps/known-bugs)
  - roadmap-audit header nota 13/09 + OTP §127/§131 corrigidos/decididos no bloco de atualização
  - architecture.md — diagramas ASCII viram Mermaid (render nativo no GitHub)
  - varredura de claims stale pós-ratificações 13/09 (roadmap-audit P4, matrix S10c, finally-return, stdlib-expansion, OTP 8x, README S2/ratchet)
  - fila §1 itens 4/6 sincronizados — DD-OTP (S2 JVM) e pow/-lm/S10c ratificados 13/09, não mais 'na mesa'
  - README §2/§3/§4.3 sincronizado com a fila viva (14 abertos; §65 NÃO REPRODUZ; NAT-STR01→§161; DD-STDLIB-01 fora de future/)
  - architecture.md — diagramas ASCII viram Mermaid (render nativo no GitHub)
  - dispatcher 05:30 — sessão de organização (bugs-and-gaps/decision-pending/audits) + auditoria native-multiarch/DD-STDLIB-01; próximo tick = manter movimento de docs ao decidir/fechar + células matriz×teste, sem código de lane alheia
  - DD-STDLIB-01 sai de future/ — decisão 6a ratificada 13/09 (a própria doc se contradizia: topo ✅DECIDIDO × status 'PROPOSED aguarda decisão' — corrigido p/ RATIFICADO/Implementação pendente, lane STDLIB executora); futuro/README e README development atualizados
  - conclusão honesta agrupa §104b-ii como lane bugfixer (não regra-6) — 7+3+3+1=14 coerente com a fila
  - ecosystem-coverage baseline ~1620→1611 testes (alinhado a docs/status.md 13/09)
  - conclusão honesta alinhada ao PR #115 (§65→NÃO REPRODUZ): 14 itens, não 13; §65 sai das lanes alheias
  - specification-gaps version 0.3.0-beta→0.3.22-beta (pom revision atual)
  - fila de renumeração da série OTP §127-129 corrigida p/ §162-164 (merge ocupou §157-160)
  - §2.2 tabela corrigida contra medição (auditoria doc-vs-código, lane development) — CI cross ❌→✅ (job cross-native existe e verde, run 34732932745), 13/13→42/42 (surefire medido), 'consequência prática: stub sai 0' marcada SUPERADA (hoje executa lógica sob qemu; re-auditoria 12/09 no topo já dizia — a tabela §2.2 é de 01/09 e contradizia o próprio topo do doc)
  - reordena §155/§156 antes de §157-160 (merge deixou fora de ordem) + conclusão honesta reflete §157-160 corrigidos e §156 code-pure
  - NAT-STR01 renumerado §157→§161 (colisão com o merge dos §157-160 da issue-lane)
  - registra NAT-STR01 (case-fold ASCII-only no Native) como §157 — lacuna: README apontava p/ known-bugs mas só existia na matriz
  - refactor de clareza (pedido da mantenedora) — auditorias saem de development/architecture/history e passam a morar em docs/audits/
  - sincroniza registros com o estado real — fila 14 itens (era 12 com §149 fechado por engano), desambigua colisão §127/128/129, corrige versão e contagens
  - sincronizar plano — S2-JVM implementado (commit anterior), ratificação registrada
  - P0 — WIP §103.1 (69fdab59) quebrou o javac do KofRuntime na beta-0.4.0; resolvido pelo fix do dono (§103: 751a83f2/3fd3c1b3)
  - ratificação da mantenedora 13/09 — 12 decisões (OTP S2 1a, json Map sorted 2b, §89 alias+warning 3a, DD-01 4a bump 0.3.1, Long=BigInt 5b, 6a hex, 7a -lm, 8a slots, 9a cast, 10a sobrecarga, 11b fila, 12 abrir TLS+NAT-STR01)
  - registrar descarte do commit duplicado de §149 (reversão conflitava com fix 29923a5b da lane dona) — estado = remoto, gate 1611/0
  - retificação da MINHA análise pós-fix 29923a5b — eu afirmei 'fix não pode morar em JsIfThrowElse' e 'IR byte-idêntico'; o fix real foi no parser (isLoopStart lookahead) e o dump byte-idêntico eu NUNCA rodei (alegação de memória — não asserir o não-rodado) + PRÓXIMO PASSO re-sincronizado (gate 1611/0, 13 abertos todos em decisão/lane alheia)
  - retificação interna — índice dizia '14 abertos' enquanto o próprio §2 (triagem 13/09) diz '13 seções sem ✅'; contagem conferida seção a seção neste HEAD (16 seções sem ✅ no cabeçalho − 3 com 'CORRIGIDO' sem glifo: §28, §32, §93) = 13
  - #110 FEITO (21e7495b, respondida 5650161070)
  - tabela-resumo corrigida contra o código — 9/18/21/22/23 'não reverificados' viram REVERIFICADOS com prova (16/16 testes neste HEAD) e a ressalva 'host arm64/macOS sem toolchain' marcada como superada (host atual x86_64 + qemu riscv/aarch; Native reverificado 11/09–13/09)
  - §149 JS corrigido — suíte 1611/0 (13 err node) em status/README/backend-parity/AGENTS/DOING
  - dispatcher 22:50 — gate vermelho = §149 (causa raiz completa em known-bugs); próximo tick exige DECISÃO da mesa sobre a porta do fix (Optimizer vs pilha de end-labels do §147)
  - mecanismo exato da poda — Optimizer (unreachable+jump-to-next, linhas 44-45/180) apaga o Jump/Label(end) pós-throw; NÃO é o lowering nem só o parser
  - #102.2 FEITO (9e823af9); WIP-103 em /tmp/RESGATE-* p/ o dono
  - análise da lane dev — metade matriz FECHADA (0c107eb9), metade JS com causa raiz corrigida e locus do fix provado
  - #108 FEITO+fechada; WIP-103 alheio stasheado intacto (stash@{0})
  - 1602/2/5 re-medido no e1962735 + matriz doc sincronizada às células do teste
  - watcher all = varredura completa a cada tick (triar+corrigir+fechar)
  - watcher processado — resposta 5649828936 (B40 4459ff57 compile OK); plano + DOING sincronizados
  - baseline ≤500 17→9 sincronizado (split-7 e45140d6 tirou ExpressionMethodCallLowerer)
  - §9 triado ✅ (nativeLambdaMutableCapture 1/1) — fila 14→13
  - fila 17→14 — §145/146/147 ✅ 440730c8; §149 aberto (lane bugfix-101); §9 p/ triagem
  - S-7 FEITO eabf814b + PRÓXIMO PASSO (fila §1 item 1 concluído)
  - tree-shaking consolidado em docs/ — plano CONCLUÍDO
  - releases.md → release-naming.md — colisão de caixa com RELEASES.md quebrava checkout em macOS/Windows
  - sem trailer Co-authored-by (polui o log) + identidade única consolidada
  - add JavaFX error handling guidelines to prevent regressions
  - higiene de identidade do git — agente vive SÓ no --local (temmcode); --global é da mantenedora e nunca é tocado; scripts/CI nunca setam identidade (release.yml usa kof-release-bot)
  - suíte 1585 = 1413+31+5+136 re-medida no HEAD 54da1325 (gate1585.log)
  - retifica premissa falsa do dispatcher 20:10 ('host sem qemu/toolchain cross')
  - 'free sem caller = código morto' era FALSO — riscv faz 57 allocs e 0 frees
  - sincroniza impeditivos da fatia S2 ao medido — §128 ✅ 12/09; buraco de design do selectAny documentado
  - 3 bugs de backend reportados por PublioSantos — REPRODUZIDOS + causa raiz localizada neste HEAD
  - agente PODE postar via conta da mantenedora (override 12/09) — MAS todo comentário DEVE abrir com bloco de citação marcando autoria real como "Kof-agent-worker" (apelido entre aspas, nunca como se fosse conta real). A regra antiga (NUNCA comentar como melmonfre) gerou rascunho pendente eternamente na #97; a nova troca o risco de identidade pelo risco de parecer palavra da mantenedora — e o elimina com a marcação. Prova de uso: #97 issuecomment-5649162397 (marcação presente).
  - recusa de re-disparo 20:15 — re-varredura pós-F2, TODO vazio honesto
  - F2 (CompilerDriver) FECHADA com prova medida — 487 ≤500, ratchet sem violação
  - recusa de re-disparo 20:00 — re-varredura fila §1 + mesa bugs, TODO vazio honesto
  - recusa de re-disparo na lane nat — varredura de estabilidade 12/09 (nada sem dono)
  - varredura doc-vs-realidade — claims de 31/08 sincronizados ao medido
  - varredura doc-vs-realidade — 7 células sincronizadas ao medido no HEAD
  - suíte 1580 re-medida no HEAD 9cda2b3d + AGENTS.md sem o 'bug 59 aberto/59 falhas'
  - decompõe a face (1) GC mark-sweep cross em 5 degraus executáveis (G-1..G-5)
  - sincroniza a fila ao medido HOJE — S-5 cross ✅ (103→18 syms), suíte 1579→1580, ratchet 13→12
  - add Kof philosophy manifesto to guide development principles
  - contagens da suíte sincronizadas ao gate medido — total 1576/1560→1579 + 32 linhas da tabela regeneradas dos surefire-reports
  - célula WEB002-Native sincronizada ao medido — 'sem kof_web_* no asm' era FALSO
  - PRÓXIMO PASSO = auditoria fila §1 completa (tudo owned/decisão); re-disparo = doc-vs-realidade nas matrizes vivas OU resgate do S-5/GC se 9092 largar
  - ratchet ≤500 aponta p/ nº autoritativo (wc -l do baseline) em vez de número cru que apodrece
  - sincroniza a FILA ao estado medido — suíte 1577→1579, ratchet 17→14, donos S-5/S-6 da #97
  - varredura doc-vs-realidade da coluna ABERTO — 4 linhas corrigidas p/ CORRIGIDO + 1 p/ gap honesto JSN004
  - README vira FILA — base 0.3.22-beta, estado real medido hoje + ordem de execução dos planos
  - CONCLUI a reclassificação de docs/development/ + future/ — unidade meia-executada resgatada (working tree de sessão encerrada no meio do turno)
  - unidades 12/09 — heartbeat porta-dinâmica (9092=outra sessão, ticks mortos 1h), NUL no known-bugs (grep pulava o backlog), triagem §139 (doc-sync de correção já commitada) + watchers #97 processados (S-6 autorizado ao ViniAguiar1, fora da lane dev); PRÓXIMO PASSO = S-5 só se a lane 9092 largar
  - sincroniza doc-vs-realidade — §139 foi CORRIGIDO em 39da8416 (parser JS consome KofPop/KofPop2 no meio de fragmento; célula nullableprint sem exclusão trava os 2 repros nos 4 targets) mas o header dizia ABERTO. ConformanceMatrixTest 11/11 medido. Mesmo padrãoo da lição SG-002 (doc descreve estado pré-fix).
  - remove byte NUL literal (default de Char escrito como \0 bruto na linha 3556) — o arquivo era tratado como BINÁRIO pelo grep ('binary file matches'), o que fazia TODA varredura (triagem de bugs abertos, auditoria de segredos, grep -r nos agentes) PULAR o backlog inteiro silenciosamente
  - planning-mutability + planning-switch-expr voltam p/ docs/decisions/
  - known-bugs/ecosystem-coverage apontam o caminho real em docs/development/ (comentários Java + learn/19)
  - atalhos mortos de docs/development/ resolvidos no estado-corrente
  - pós-movimento — células da matriz ecosystem-coverage resolvidas sob docs/, banners 0.3.22-beta no corpus, contagem da suíte real (1576/0/126-skip), docs/future/ → docs/development/future/ em comentários Java, learn 00→39 na tabela de corpus do AGENTS/README
  - mover arquivos soltos de docs/ para subdiretórios de domínio (architecture/debugging/stdlib/history/distribution/comparison/decisions/ui)
  - auditoria doc-vs-realidade — objmethods/native (LINK_FAIL 104b-i virou bloqueio REAL 104b-ii) + linha da unidade CI-cross (f2fee2f8) com o PRÓXIMO PASSO devido
  - heartbeat na porta certa (9093 = sessão viva; conferir ss antes de assumir) + watcher de issue #97 a cada 2h documentado como parte do loop
  - identidade de agente DESVENDADA — atribuição por e-mail de autor (temmcode 268 commits = aminadojava@gmail.com verificado), não App/PAT; setup git config por clone; proibido comentar issue como melmonfre (corpo vai p/ .issue<N>-reply-pending.md gitignored + pendência no DOING) + watcher de issue #97 c/ scripts/issue-watcher.sh (cron 2h, attach obrigatório)
  - célula jsondec-recordlist Native — PARTIAL com a verdade do §48 (era 'não compila' de 08/09; hoje é recusa JSN004 em compilação, R6; ConformanceMatrixDocTest verde)
  - reconcila o PRÓXIMO PASSO apos a S-2.5 (a35053f9 saiu sem atualizar a linha — auto-denuncia + despacho corrigido)
  - auditoria dev/ — CANVAS001 reprovado verde + doc consolidado (movido future/->docs/); AGENTS.md perde flag obsoleta !canvasCreation
  - 12/09 — §107 3-alvos + #97 S-1 + re-auditorias doc-vs-realidade; contagem 1249->1560/0/5-skip medida neste HEAD (5 skips = BD externos MySQL/Mongo/Postgres, cross EXECUTA)
  - corrige a tabela do topo — varredura 08/09 estava APÓCRIFA (39/62/63/64/46/48/50/59/61 estão ✅ nos próprios cabeçalhos); fila real 12/09 = 16 abertas, cada uma com bloqueio mensurável (decisão/lane/congelado), ecoando a mesa do bugfixer 4d51defe
  - reconcilia a linha PRÓXIMO PASSO apócrifa do rodapé — §135 resolvido, suíte verde, #97 aceite via issue e S-1 FEITA a3996600; fila real = S-2 (provides/needs por fatia)
  - re-auditoria medida do estado cross riscv/aarch — doc contradiz o código (regra AGENTS 'audit vs reality', estado-4)
  - reconcilia as duas retificações da toolchain cross — host-dependente (evidência de ambos os hosts)
  - mesa bugfixer 12/09 = 0 itens desbloqueados (17 abertos auditados 1-1: cada um com bloqueio mensurável — contrato/mantenedora (45,81,89,94,101,106,117,125,129,§127-cast,§132,§68a/§70), lane alheia (65 web/UI, 90, 107 CEDIDO dev 12/09, 131 semântica), cross-sem-toolchain-aqui (114/§104b-ii, provas §123/§126/§127-JS)); FECHADOS da sessão: §127-JS f85ffadd + §128-JVM 6e68cb36 + §134-wildcard 6e147824 + retificações bda06e81/bc45aaf9; gate medido no HEAD exato (matrix 11/11, doc 1/1, classpath 6/6, concurrency 33/0, decompile 45/0); NÃO é condição de estabilidade (development/ e future/ seguem com trabalho) — cron continua; re-disparo da minha lane só assume se: resposta §125 chegar, regressão na suíte, ou dev liberar §107/infra storage-box
  - retifica 92b01b2b — toolchain cross NÃO está neste host (medido: command -v ausente, find vazio, NativeRiscv64E2ETest 36/36 SKIPPED pelo guard assumeTrue); faces cross §123/§126-tag seguem sem prova possível aqui; afirmação possivelmente válida no host com qemu (B37/melissa), não neste — regra de hoje: auditar doc contra o código/ambiente, não contra a memória
  - §137 cedida ao remoto 982f53f0 (fix paralelo descartado na fusao); DOING com a licao de colisao; gate medido 1547/0/5-skip
  - linha da unidade SG-011B+§137 (paragrafo duplicado do replace anterior removido; refs renumerados §136/ratificacao §135)
  - sincroniza o gap com o estado REAL do lexer (tokens já removidos; teste verde) — fila avança p/ cross§123/§126 + §107
  - retifica ACHADO COLATERAL #88 — 'runtime sem export kofStringsIndent' era artefato do MEU build incremental (inlining de WS_RUNTIME static final em JsArtifactWriter.class); causa real do indentDedentJs vermelho = só node ausente no host (mesma classe do node-trio); lição: mvn -o clean na sonda antes de registrar causa-raiz de runtime gerado
  - PRÓXIMO PASSO com §135 — plano tree-shaking enviado (eae16c46); S-1 pré-bloqueado pela decisão de contrato da suíte vermelha
  - §135 — conflito de contrato SEM047(09/09) vs SG-011B(11/09) + DecompileTest switch — suíte vermelha no base, PRÉ-EXISTENTE a esta sessão
  - PLAN-TREE-SHAKING — stdlib por alcançabilidade (frente designada pela mantenedora)
  - linha #91 (gate CONC001) + PRÓXIMO PASSO com fila real (#96 merge, #87/#88 conflito B37, frente tree-shaking)
  - update development focus and stability criteria in DOING.md
  - update version and last update details in AGENTS.md
  - add stability evaluation criteria for autonomous mode
  - §129 FEITO + §125 com causa-raiz pinada (descritor, não print) + fila real (decisões de contrato em 2 itens, §104b-ii grande, prova cross p/ sessão com qemu)
  - §125 no PRÓXIMO PASSO com o oracle medido (0 via precedente PN3)
  - oracle resolvido pelo precedente medido — Nullable(INT)→0 no println; guard falta no boxPrimitive do print-lowering
  - §113 pushed (d2a4dc0a+edb86c34) — gate no HEAD exato em execução
  - faces cross 5/5 no header do known-bugs + suíte do HEAD no PRÓXIMO PASSO
  - §122/§123/§124 FEITOs em um só commit + fila real corrigida (regra do MESMO commit, restaurada)
  - §126 — chave do tipo errado em Map/Set/contains → Native SIGSEGV (design opção-B fechado)
  - SG-020 — a regra 5 (SC para statics/campos compartilhados) ainda não tem prova
  - emendas verificadas ao plano de supervisão + fechamento das 8 DDs independentes
  - corrigir tabela de gaps desatualizada + travá-la com ConcurrencyGapsDocTest
  - linha CONC — pos-merge + renumeracao 127-132 registrada
  - move 0.3.0→0.4.0 FECHADO + fila real pós-merge (§113→§114x86→§107; toolchain cross = aspiracional neste host)
  - spike #83 encerrado — supervisor viável em Kof puro (forma interface-factory) + 3 achados registrados (§107/§108/§109)
  - resync pós-rebase — §111 cross fechado pelo agente B36/B37 (qemu naquela sessão; ausente NESTA); PRÓXIMO PASSO agora §112-JS com a tentativa revertida documentada + 104b-ii
  - split residual fechado nos 5 targets + linha STDLIB atualizada
  - residual riscv/aarch atualizado + linha STDLIB no DOING (B35+B36 juntos, split-trailing riscv em aberto)
  - resíduos fechados + registro no DOING — teste ampliada p/ 22 vetores (bordas starts_with2 provadas sob qemu)
  - varredura cross 11/09 sem bug novo + sonda §101 (cancel-colisão) travada em decisão
  - §106 — Native println(coleção) imprime ponteiro como lixo (causa raiz: vtable -1 silencioso em kof.List/Map/Set)
  - println(<coleção>) → lixo de ponteiro no nativo (sem kof_{list,set,map}_to_string; valueOf dispatch só trata vtable toString = records) — ABERTO, backend-only lane Native
  - tabela known-bugs na varredura 11/09 — fila viva = §45/§104b/§104c + congelados explicitados
  - json.encode(Map) quebra em 3/5 alvos (JVM crash reflection; x86/riscv link error) — registrado com repro medido, ABERTO (superfície JSON = decisão regra 6)
  - renumera meu random-loop 102→105 (102 tomado pelo §102 indexOf no remoto 11/09) + ref no código/DOING
  - snapshots históricos 02/09 (language-state/actual-state) movidos p/ docs/
  - reclassificação development/ — SG-020 p/ docs/, planning PROPOSED p/ future/
  - §102 FEITO (x86+JS) + PRÓXIMO=varredura collections/higher-order
  - PRÓXIMO PASSO §102 (indexOf/lastIndexOf from-arg no Native, backend-only)
  - renumera meu indexOf(String,from) p/ §102 (colidiu com §101 relacional-NaN no remoto) + linha PRÓXIMO
  - renumera meu bug relacional-NaN p/ 101 (100 tomado pelo bug Char no remoto) + DOING MATH001 FEITO / PRÓXIMO=TIME002
  - planning-otp-supervision.md — issue #83 (DD-OTP-01..13) + bug 101 registrado
  - lane STDLIB S3b.2 uuid.v7 FEITO (5 alvos) + sync pós-rebase com S1b.1 lerp (remoto)
  - restaurar linha S1b.1 (lane STDLIB) + sincronizar PRÓXIMO PASSO pós-rebase
  - NAT-STR01 estendido p/ toUpperCase/toLowerCase de instância (varredura String parte 2)
  - §97/§98 — String.compareTo/hashCode link-fail + `<`/`>` Unspecified (varredura String parte 2)

### Refactoring

  - extrai maquina de expressao do linearReturn em machineRun (Fase C degrau 3, unidade 1/n)
  - split Translate.java 526->255 + TranslateStatements 287 (gate <=500) - dono 192.168.100.22
  - F3 FECHADA — NativeBackend 505→498 ≤500; PLAN-SOLID-500 completo
  - NativeBackend 579→505 — NativeStaticData extraído (bug 41)
  - split NativeBackend 671->579 (check_500 verde) — NativeSymbolMangling + array emitters
  - split-7/8 — dedupe gap-diagnostics in ExpressionMethodCallLowerer (515→468) and extract builtin namespace inference from MemberCallTyper (550→379) into MemberCallNamespaces
  - simplify error handling in ExpressionMethodCallLowerer and extract argument emission to a dedicated method
  - extract static method type inference logic into MethodCallNamespaces class
  - ExpressionInstanceCallLowerer 554->481 — famílias builtin por receiver em ExpressionBuiltinInstanceCalls (baseline 12->11)
  - JsControlFlowParser 530->492 — split por RESPONSABILIDADE real (baseline 13->12)
  - BytecodeDecoder 521->359 via BytecodeCp.java (baseline 14->13)
  - SemanticAnalyzer 535->473 removendo visitor no-op (base do ratchet: 17->14)
  - Parser 513->320 via TypeDeclarations.java + baseline do ratchet encolhe 17->16
  - hook de injecao kof.supervisor em classe propria (regra <=500)

### Tests

  - torna gate de asm riscv/aarch OPCIONAL ate dev nativo completo (D-ASM-GATE)
  - stress array bounds safety (KOF-SBD-001-STRESS)
  - corrente de 2 if-sem-else executa os 4 caminhos (Q3: borda não vaza entre irmãos)
  - EDI001 degraus 6-9 - prova de instalacao vim/emacs/geany/nano (21/21) + plano sync - dono 192.168.100.22
  - jsondec-map estreita o narrow exigido pelo congelado §87 (regressão visível do #126; A/B medido: pai 5a116284 verde, 61495f69 vermelho SEM049)
  - célula de matriz castfn (§127-JVM) — prova 4 targets em CI
  - células de matriz p/ §155/§156/§157 — overclaims de alvo-múltiplo (4 targets em CI)
  - prova NATIVA automatizada de §89/§131 — células de matriz numconv/methodoverload (4 targets em CI)
  - anota KofScriptTest.evalNativeTarget como regressão do GC x86 + DOING
  - pow — trava a recusa MATH001 cross (KofMathTest.powCrossArchRefused)
  - §103.2 Int→Long em campo prova o I2L do ExpressionAssignmentLowerer (repro do reporter, VerifyError antes)
  - KofJsBrowserE2ETest acha o Chrome do macOS em vez de pular em silêncio
  - sonda JS declara os imports do runtime podado; plano e DOING com o gate
  - E2E dedicados por target p/ descarte Long/Double (delta sobre 360401a4)
  - hardening da poda riscv (2 testes não-vácuos sobre o port 2f1dba45) + sync de doc do design S-6 (unidade de topo, aprovada na #97)
  - prova qemu real das faces riscv64+aarch64 da tag de chave de Map
  - faces UTF-16 riscv/aarch provadas (B34) — NativeStringUtf16CrossTest
  - bug 104 — célula objmethods na matriz de paridade (record em coleção) + §104b/§104c registrados

### Build

  - nomenclatura 0.4.0-beta (pedido da mantenedora - proxima versao quando estavel)
  - **D-BASELINE (14/09): o baseline da toolchain do repo sobe 21 → 25**
    (decisao da mantenedora — `pom.xml` `release=25`, CI/CodeQL/release/
    benchmark/android JDK 25, `package.sh --jdk` embute Temurin 25). Destrava
    o codemod preservador de comportamento do CodeQL
    `local-variable-is-never-read` (unnamed patterns `_`, JEP 443 — final no
    22, recusado pelo `javac --release 21`). **O contrato da linguagem nao
    muda:** o `JvmBackend` continua emitindo bytecode `V21` e o template
    Android continua alvo `release 21` — um programa Kof continua rodando em
    JVM 21+ (`KofVersion.TOOLING_API=21` intacto). Camadas: compilar o repo /
    rodar o CLI = JDK 25; seu `.kf` compilado = JVM 21+.

<!-- NEXT-RELEASE -->

## [0.2.7-beta] - 2026-09-04

### Features

  - log níveis + cache real + mq push/pop real — suíte 842/0
  - WEB002 T1 — accept loop HTTP/1.1 no Native (kof_web_listen+handle_client; respondendo 200/hello fixo a qualquer request — valida listen→accept→read→write→close; routing+dispatch é T2/T3). Módulo novo NativeWebRuntime.java (≤500 linhas); CompilerDriver libera web.app/listen para NATIVE_*. Suíte 840/0
  - prepared statements com QUERY binário — parse de binary-rows (COM_STMT_EXECUTE)
  - GC mark-sweep real (sweep funcional, auto-collect desligado)
  - close HTTP002 — kof.http no Native (asm HTTP/1.1)
  - COM_STMT_PREPARE/EXECUTE binário — kof_db_mysql_prepare + kof_db_mysql_exec (NativeDbPrepared.java, novo módulo ≤500 linhas)
  - validation 13/13 + observability real (counter/gauge/histogram/metrics) em asm puro
  - stubs NATIVE002.1 — kof.log/config/time/observability/cache/mq em asm puro
  - add roadmap gap report for NATIVE002 core completion
  - CORE COMPLETO em asm puro via tradução riscv→aarch64 (NATIVE002) — 13/13 E2E qemu
  - close TIME001 — time.interval/cancel no KofJS via fila cooperativa
  - CORE COMPLETO em asm puro (NATIVE002 parcial) — 13/13 E2E qemu
  - package manager MVP — kof deps (kofdeps, Maven Central, --deps)
  - application { onStart/onShutdown } — construcao de intencao
  - spans W3C com timing (spanStart/spanEnd) nos 3 targets
  - close LOG001 (kof.log on JS) + runtime fixes
  - add platform invariants and gap conventions to AGENTS.md and backend-parity.md; introduce ACTION_PLAN.md for future implementation roadmap
  - riscv64 real (NATIVE002 parcial) — kof_main em asm + runtime C via gcc cruzado + qemu (NativeRiscv64E2ETest 4/4)
  - captura mutável de lambda — mutação fora da lambda refletida
  - DWARF line table real no ELF x86-64 (.file/.loc GAS — Fase 5 parcial do debugger)
  - source map V3 real (mappings VLQ em nível de linha — função gerada → linha Kof)
  - Enhance array creation handling in JvmBackend for reference types
  - Query DSL tipada nível 3 — User.query(db){ where; orderBy; limit } (ORM001)
  - MQ001 — kof.mq no Native (pub/sub + filas in-process em asm, paridade JVM/JS)
  - readLine → String? (null no EOF) + docs exemplos desatualizados
  - transaction {} — commit/rollback real (BEGIN/COMMIT/ROLLBACK + EH)
  - TIME001 — time.interval/time.cancel no Native (reusa o scheduler)
  - OBS002 — histogram/metrics no Native (store asm + export Prometheus)
  - add primitive widening and narrowing for array store operations to prevent verifier errors
  - enhance AES-GCM support for JS target and add cross-target parity tests
  - add support for Channel type in various components and tests
  - add maven-surefire-plugin configuration to include specific test files
  - add tracing for 'add' method calls to enhance debugging
  - add tracing for return value of 'add' method calls to aid debugging
  - add tracing for resolved owner class in KofPop to aid debugging
  - add L2I unary operation support and enhance type casting for primitives
  - implement local HTTP server for serving appDir and open in system browser
  - add support for I2C conversion in constant folding
  - add support for I2C unary operation and enhance type casting for primitives
  - enhance handling of built-in types as static receivers to prevent frame crashes
  - enhance method call handling for built-in types to prevent ClassFormatError
  - update kof_io_read_range and kof_io_read_range_path to use long for length parameter; enhance file reading with offset support
  - File.readRange(offset, len) e File.readRangePath(path, offset, len) — leitura com offset p/ arquivos grandes (GGUF de LLM) sem carregar o inteiro; JVM via RandomAccessFile (kof_io_read_range/_path)
  - add traceId and spanId functions; enhance LSP server capabilities
  - add built-in health check endpoint and update related tests
  - implement implicit join for main function tasks to prevent orphaned threads
  - add video handling support with metadata extraction and streaming capabilities
  - build.sh da libvkchain (compila + instala)
  - M32.3 — dispatch Vulkan compute REAL nos 2 backends
  - implement non-blocking done/poll methods and cooperative cancellation in native backend
  - String.lastIndexOf — kof_string_last_index_of (varredura reversa do fim p/ inicio, needle vazia retorna length, nao-achado -1) + handler INSTANCE lastIndexOf no emitCall; fecha N11 (repro regressions/N11 rc=0 no kof-agent)
  - CONC001 fechado — spawn/await no Native via pthread
  - JSN001 fechado — Float/Double no Native (encode, decode, arrays)
  - Fase 7 Router — go/replace com param, unmount de rotas não-registradas
  - kof config gen — template de deploy a partir do código (P3)
  - FFI Vulkan compute (FFM) — JvmVkRuntime com cadeia instance→device→pipeline validada (RADV+lvp rc=0), stage inline no ComputePipelineCreateInfo, structs validados (DeviceCreate 72B, WriteDesc 64B, SubmitInfo 72B, MemoryAlloc 32B); degradação silenciosa p/ CPU (bug RADV/lvp 25.2.8 no dispatch — reproduzido em C puro dlsym)
  - interpolação ${key} no kof.config — P2 nos 3 targets
  - FFI Vulkan compute (FFM) — JvmVkRuntime com cadeia instance→device→pipeline validada (RADV+lvp rc=0), stage inline no ComputePipelineCreateInfo, structs validados (DeviceCreate 72B, WriteDesc 64B, SubmitInfo 72B, MemoryAlloc 32B); degradação silenciosa p/ CPU (bug RADV/lvp 25.2.8 no dispatch — reproduzido em C puro dlsym)
  - add configuration interpolation and HTTP circuit breaker functionality

### Bugfixes

  - 500 desempacota InvocationTargetException do handler lambda
  - envolve TODO o mic record no gap MEDIA003
  - mic captura qualquer exceção de hardware ausente como MEDIA003
  - dedup por arquivo de origem — re-import transitivo não é colisão
  - JvmVkRuntime.java — restaura ';' e remove '}' extra (build quebrado no merge fixes-for-kofagent)
  - PKG005 permite nomes iguais em pacotes diferentes (como em Java)
  - bug 11 native — record ==/equals/!/toString/concat (já testado), concat valueOf fix, digest valueOf Object->toString
  - remove duplicate WS/SSE runtime definitions
  - bug 15 — primitivo → Object (auto-boxing) + default em var sem init
  - bug 9 — captura mutável no Native (prologue de lambda)
  - bug 8 — tipo de função (Int) -> Int parseia como tipo
  - bugs 19/20 (lambda em coleção/retornando lambda) + validação símbolos
  - --enable-preview só no JDK 21 — FFM é final no 22+ (JDK 25 quebrava COMP001)
  - bug 23 — warning quando superclasse externa está fora do classpath
  - bug 11 — == em records por conteúdo (JVM+JS)
  - bug 16 — List.toArray() rejeitado com SEM029
  - bug 12 — assignment como valor rejeitado (SEM027)
  - bug 18 — kof-ui widget id monotônico (sem reuso após remove)
  - bug 17 — array .get()/.set() rejeitados com SEM028
  - bug 13 — cast em aritmética crasha o compilador
  - bug 4 — switch de String no JVM
  - bug 22 — Native: construtor de classe importada (undefined reference)
  - bug 7 — listOf<String?>() agora parseia
  - bug 1 — throw não-String vira SEM026; try/catch agora é analisado
  - bug 14 — Map/Set .size como propriedade
  - bug 6 — sufixos numéricos maiúsculos (42L/1.5F/2.0D)
  - bugs 5, 24, 25 — conversões numéricas + literal fora de faixa
  - bugs 2, 3, 10 — compound assignment + NOT lógico
  - concat 'str' + double/float descartava o operando FP
  - Set<T>/Map<K,V> como campo/retorno de classe (mapper HashSet/HashMap + parse de método c/ retorno genérico)
  - idiomatic-philosophy — kof.Set JVM, Map.get V?, readText String?, size() sem sentinela
  - alinhar stack no call pthread_create (println antes de spawn desalinhava → segfault glibc)
  - surefire include pega NativeDebugTest2-5; docs/status atualiza 01/09 + regressão dc849f6
  - clarify test summary in project status documentation
  - update last updated date in project status
  - prevent stack underflow by avoiding unnecessary KofPop for collection methods
  - 'fn' keyword de declaração — o parser tratava 'fn main()' como retorno 'fn' e o JvmBackend emitia main([String;)Lfn; → JVM rejeita e tenta o launcher JavaFX ('componentes de runtime do JavaFX não encontrados'). kof run --target jvm volta a funcionar (validado: pure.kf + date + llama smoke)
  - remove unsupported JS spawn error handling; add sequential execution test
  - dedupe helloRoute in KofWebE2ETest; docs for app.health + observability (760 tests)
  - N12 — ordem dos stack args >=6 invertida nos call sites (SysV: arg6 deve ficar no topo → 16(%rbp)); INSTANCE/INTERFACE vtable salvavam stack args em r10 único (quebra com >1 stack arg) — agora slots de frame; repro N12 (9 campos, x.i=9) verde, J4/N10 re-validados
  - N23 — constructor com >=6 args: cleanup dos stack args apos call (callee caller-clean); pop do consumidor volta a desempilhar o push duplicado do receptor (rip=0x1 via vtable corrompida); repro R3-R7 + N23 verdes, 16/16 suites
  - COMP002 travava lambda WS com if/String — descritores ws faltavam
  - fechamento da classe KofRuntime no runtime concatenado (COMP001)
  - fechamento da classe KofRuntime após concatenação do JvmVkRuntime (COMP001 compact source file)
  - cache.delete statement sem Pop extra — KofIo.instanceMethod(Unknown,'delete') interceptava hasReturnValue antes do caso cache (frame merge NegativeArraySize) — fecha KofCacheE2ETest (661 testes verdes); trace IR via -Dkof.trace.ir
  - inferência de aritmética promove int→long, corpo vazio em classe concreta emite return (ClassFormatError), tipo de retorno de função top-level registrado (NoSuchMethodError em receiver); fix(native/io): kof_io_dir_delete recursivo no JVM + retorna 0 (não -1) em falha — fecha suítes ws do kof-agent (16/16) e IoE2ETest.directoryDelete
  - fechamento da classe KofRuntime no runtime concatenado (COMP001)
  - fechamento da classe KofRuntime após concatenação do JvmVkRuntime (COMP001 compact source file)
  - update future release codename in documentation; modify test to accept closed URL as argument
  - correct spelling of "Diplomat" to "Diplomata" in release notes; add end-to-end tests for HTTP resilience and circuit breaker functionality
  - restaura descritores JVM + no-ops UI/Store perdidos no rebase
  - cache.delete statement sem Pop extra — KofIo.instanceMethod(Unknown,'delete') interceptava hasReturnValue antes do caso cache (frame merge NegativeArraySize) — fecha KofCacheE2ETest (661 testes verdes); trace IR via -Dkof.trace.ir
  - remove unnecessary stack adjustment in method call
  - inferência de aritmética promove int→long, corpo vazio em classe concreta emite return (ClassFormatError), tipo de retorno de função top-level registrado (NoSuchMethodError em receiver); fix(native/io): kof_io_dir_delete recursivo no JVM + retorna 0 (não -1) em falha — fecha suítes ws do kof-agent (16/16) e IoE2ETest.directoryDelete

### Documentation

  - WEB001/WEB002/HTTP002 atualizados ao estado real (03/09)
  - SYN001 23/23 (enum exaustivo) + suíte 910
  - bronca formal — 3 incidentes de processo (03/09)
  - corpus do switch-expressão + status 906 testes
  - atualiza known-bugs.md e DOING.md com status 03/09
  - NATIVE002 paridade stubs→real FEITO — log config cache mq interval scheduler com cli via kof_time; aquitetura confirmada
  - CONC003 fechado - async real no JS documentado em todo o repo
  - trilha universal — Tier 0 fechado; Tier 1 (SYSTEMS) pendências mapeadas; WEB002 reivindicado por agente-planning
  - MySQL prepared binário FEITO (02b9ddb) — status/parity/DOING atualizados
  - limpa duplicatas; umico Em curso + Abertos
  - WEB002 devolvido a ABERTO (escopo muito grande pra sessao; proximo passo: server bloqueante com accept+request-line+match de rota literal) — NAO pega outro enquanto GC fechado
  - marca GC sweep+flag FEITO (dono agente-planning); reivindico WEB002 (NativeWebRuntime.java novo; handler com trampolim)
  - ajustes de escopo apos HTTP002/GC — WEB002 e maior (closure trampolines); GC sweep fechado, auto-collect pendente safe-points
  - status 854 + CHANGELOG noite 03/09 — 14 bugs corrigidos
  - DOING.md — 13/25 bugs corrigidos no known-bugs (03/09)
  - DOING.md — MySQL prepared FEITO (4ce1f25), NATIVE002 valid/observability FEITO (b20aa49), aberto: query binaria + GC
  - update AGENTS.md and DOING.md with behavior freezing guidelines; revise test counts in status and stdlib-logging documentation
  - DOING.md — coordenacao multi-agente (dono por gap, estado, arquivos)
  - rodada 3 (usuários) — 6 bugs novos (total 23)
  - bateria pós-merge — bugs 16-17 + contagens reais 819 (merge riscv64)
  - update known bugs and status with new test results and bug descriptions
  - resolve conflitos do merge de main (riscv64 13/13, counts reais)
  - rodada agressiva — 6 bugs novos em known-bugs.md (total 15)
  - bug-hunt 02/09 — 9 bugs documentados em known-bugs.md p/ próximo agente
  - esclarece class X(...) = record em TODOS os md; reduce padronizado
  - learn/19 esclarece java.util.* (interop, não idiomático para coleções)
  - auditoria final — switch break opcional, learn/04 arrays, learn/12 notas, contagens 810
  - merge backend-parity deltas (LOG001 JS + Vulkan conditional + main fixes)
  - regra de arquitetura — máximo 500 linhas por classe (refactor futuro)
  - captura mutável, concat FP, riscv64 — registros 02/09
  - riscv64 real (02/09) no corpus — targets.md, overview, roadmap, actual-state
  - learn/21 honesto — interop Java parcial verificada; contagem 810
  - riscv64 runtime em asm puro (sem C) — status/parity alinhados
  - honestidade verificada — exceptions String-only, class X(...) = record
  - gotchas do koflama — ANEWARRAY ref types, Map descriptor jar, unboxing NPE, Int overflow em acumuladores micro, UTF-8 vs latin-1
  - recalibra contagem de testes para 805 (788+8+5+4) pós-merge
  - disclaimer da marca no README + NATIVE002 — toolchain cruzada + runtime C via gcc validado (02/09)
  - consistência geral — versão 0.2.6-beta, contagem de testes 788, datas 02/09
  - SECN002 fechado (AES-256-GCM no KofJS) + contagem 780 (763+8+5+4)
  - deltas 01/09 (spawn captura, short-circuit JS, Channel param, pthread_create alinhamento, KofJS browser) + contagem 778 + MySQL wire protocol
  - contagem 778 + bug #2 spawn→await→spawn resolvido (mesmo fix de alinhamento pthread_create)
  - chained-OR membership caveat — Set<T> declarado quebra no JVM; só setOf local nos 3 targets
  - casts primitivos as Char/as Int, Long[], String.valueOf builtin + fixes 01/09 (frame List.add, I2C, L2I)
  - future/ fica só com planos; risc/arm (em desenvolvimento) -> docs/native-multiarch.md com estado real + como finalizar
  - §4.8.1 Kof Security — evolução estratégica (auditoria + PQC híbrido ML-KEM/ML-DSA + SecureChannel + threat model + roadmap por maturidade)
  - status 769 tests (752 kof-compiler +8 script +5 c-compiler +4 cli); integrate upstream io/fn-parser fixes
  - status 768 tests; LSP references/rename, W3C traceId/spanId, P1-4 LCA moduleRoot, P3-10 ORM003 typed column
  - update project status and test counts; add multimedia handling details for Kof
  - alinha contagem de testes (736 = 723+8+5) e pipeline de release
  - sweep profundo — todos os MDs sincronizados com o estado 0.2.6-beta
  - guias de instalação por SO (sem versão hardcoded) + sweep 0.2.6-beta
  - gap COMP002 do config fechado — causa era descritor ws faltando no JVM
  - CONC001 fechado — spawn/await nos 3 targets (parity de concorrência)
  - sync
  - CONC003 no JS já cobre spawn stmt + spawn-expr — gap restante é async real
  - JSN001/FLT001 fechados — parity JSON Float/Double no Native; CONC003 parcial
  - Fase 7 Router marcada como implementada com detalhes

### Refactoring

  - remove observability metrics implementation
  - runtime em assembly PURO (sem C) — Kof é Kof
  - streamline return value handling and remove debug tracing for 'add' method
  - update variable declaration examples for clarity and consistency
  - simplify argument handling in KofIo method calls to prevent frame bugs

### Tests

  - mic gap aceita as duas formulações do MEDIA003

## [0.2.6-beta] - 2026-09-02

### Feature — switch como expressão (SYN001)

- **`case ... ->` produzindo valor** (`feat`): `var r = switch (x) { case 1 ->
  "um"; default -> "outro" }` — pattern matching via expressão, no espírito do
  switch expression do Java 14. Cada caso é uma única expressão (sem `break`,
  sem escopo de bloco, sem fallthrough); `default` obrigatório ou exaustividade
  de enum (senão `SEM032`). Funciona nos 3 targets + riscv64/aarch64 (JS
  renderiza como ternários aninhados). **Aditivo**: a forma statement
  (`case X:`) está intocada. Prova: `KofSwitchExprE2ETest` 19/19 +
  `NativeRiscv64E2ETest`/`NativeAarch64E2ETest` 14/14. Plano em
  `docs/planning-switch-expr.md`.
- **PKG005: re-import transitivo não é colisão** (`fix`): `compileSources` com
  fonte explícita + `import` da mesma declaração disparava falso-positivo de
  "duplicate type name"; agora só colide quando os **arquivos** diferem
  (`PackagesE2ETest` 7/7).

### Fix — filosofia idiomática (revisão do corpus)

- **`Set<T>` como tipo declarado no JVM** (`feat`): descriptor `kof.Set`
  materializado como `java/util/HashSet` (`JvmTypeMapper`). `Set<T>` em campo,
  retorno e parâmetro agora funciona nos 3 targets — antes `NoClassDefFoundError:
  kof/Set` no JVM (`KofMapSetTest`).
- **Parser: membros de classe com retorno genérico** (`fix`): `Set<Int> foo()`,
  `List<String> bar()` em classe não parseavam (lookahead de 1 token).
  Refatorado para parse-then-decide (`Parser.parseClassMember`).
- **Null-safety narrowing no JVM corrigido** (`fix`): `if (s != null) {
  s.length }` emitia `getfield "?".length` e `s.substring(...)` emitia
  `"".substring` (bytecode inválido → erro de launcher/`ClassFormatError`).
  Agora desempacota `NullableType` no dispatch de field-access e method-call
  (`NullSafetyE2ETest`). `if (x != null)` usa `if_acmp*` (era `if_icmp*`).
- **`mapOf(k1, v1, ...)` infere o tipo do primeiro par** (`fix`): antes
  `Map<Unknown,Unknown>` vazava para `var m = mapOf(...)` e `get()` devolvia
  Unknown (SemanticAnalyzer + CompilerDriver).
- **Parser: forma prefixada nullable** (`feat`): `String? s = null` e retorno
  `String? f()` agora parseiam em statements, funções e classes — simétrica a
  `String s`; a forma anotada `var s: String? = null` também é válida.

### Fix — stdlib exemplifica os idioms que ensina

- **`File.readText()`/`readFile()` → `String?`**: ausência = `null` (JVM e
  Native — o Native antes encerrava o programa).
- **`File.size()` sem sentinela `-1`**: lança exceção recuperável
  (`catch (String e)`) quando o arquivo não existe (JVM + Native asm via
  `kof_throw_string`).
- **`Map.get` devolve `V?`** para valores de referência (ausência = `null`,
  narrowing via `if (x != null)`); primitivos seguem `V` (modelo atual não
  representa ausência).
- **`readLine()` → `String?`**: `null` no EOF em JVM e Native (o Native antes
  devolvia `""`).
- **Captura mutável de lambda — mutação fora da lambda** (`fix`): a detecção
  só marcava mutações DENTRO da lambda (`inLambda`); `var f = (x) -> x +
  offset; offset = 20` capturava por valor (15 em vez de 25). Agora
  `collectMutatedCaptures` computa as capturas REAIS (via `collectCaptures`)
  e boxa qualquer variável capturada + mutada em qualquer lugar (JVM
  verificado; `LambdaE2ETest`). Native: a direção "lambda escreve" funciona;
  "lê boxed após mutação externa" é bug conhecido.
- **Concat `"str" + double/float` descartava o operando FP** (`fix`): o guard
  de concat FP fazia `yield` incondicional (ignorava `fpSupportedOnNative`,
  que é true desde o FLT001) → `"a=" + 1.5` compilava só como `"a="` (saída
  vazia silenciosa). Agora só pula quando o target não suporta FP
  (`BackendParityTest.parityStringDoubleConcat`).
- **riscv64 codegen real (merge da main, 02/09)**: stack machine riscv64 +
  runtime em asm puro (sem C), `NativeRiscv64E2ETest 4/4` via qemu
  (`NATIVE002` parcial); aarch64 segue placeholder.

- **Bug-hunt 02/09 — 9 bugs documentados para o próximo agente** em
  `docs/known-bugs.md` (reprodução + causa provável + arquivos): compound
  assignment `-=`/`/=`/`%=` (resultado errado, JVM+Native), `s += "x"` em loop
  (crash do compilador), `switch` de String (bytecode inválido), cast FP→Int,
  sufixo numérico maiúsculo `42L`/`1.5F`, `listOf<String?>()` (não parseia),
  tipo de função em generic (`listOf<(Int) -> Int>()`), `throw` não-String,
  captura mutável Native.

- **Noite 03/09 — 14 bugs corrigidos** (todos com teste de regressão que
  falhava antes/passa depois; `known-bugs.md` atualizado a cada fix):
  1. `throw <não-String>` → SEM026 (try/catch agora passa por análise
     semântica — antes corpos de try eram ignorados)
  2. compound `-=`/`/=`/`%=` (ordem dos operandos invertida)
  3. `s += "x"` em loop (RHS empurrado duas vezes → crash de frame)
  4. `switch` de String no JVM (usava SUB em vez de igualdade de conteúdo)
  5. cast FP→Int/Long (novos ops D2I/F2I/D2L/F2L nos 3 backends)
  6. sufixos numéricos maiúsculos `42L`/`1.5F` (lexer)
  7. `listOf<String?>()` não parseia (lookahead de call genérico)
  10. `!` NOT como valor (fold usava `~i` bitwise)
  13. `(x as Int) + 1` crashava (flattening de cadeia incluía `as`)
  14. `Map.size`/`Set.size` propriedade (NoSuchFieldError)
  17. array `.get()/.set()` → SEM028 (API é `arr[i]`)
  22. Native: construtor de classe importada (mangle com package)
  24. `Float f = 3.4` (D2F no widening)
  25. literal Long fora do range (PARSE084 em vez de crash)
  Suíte subiu de 840 → **854**; zero regressão (Congelamento de comportamento).

### Corpus / docs

- `training/datasets/kof-idioms.json` atualizado para 0.2.6-beta (17 → 20
  entradas; `;` estilo Java removido; kof-004 separa ausência vs erro).
- `AGENTS.md` corrigido: forma nullable padrão `String? s = null`; `spawn`
  fire-and-forget sozinho é válido.
- `docs/philosophy.md`: propostas futuras (`config {}`, `name: required`)
  marcadas como tal; `route GET` substituído pela API implementada.
- `docs/backend-parity.md`: gap `STR001` (length UTF-8 vs UTF-16) e
  `STR002` (io) documentados; `docs/stdlib/IO.md` e `training/language/io.md`
  refletem o novo contrato.

## [0.1.0] - 2026-08-25

Primeira release estável da plataforma base — P0 (ecossistema) e P1
(linguagem) fechados.

### Features

#### P0 — ecossistema
- **G5 `kof.observability`**: `health/readiness/liveness`,
  `counter/increment/gauge`, `requestId/correlationId` — JVM/Native/JS
  (`KofObservabilityTest` 3/3; asm com contadores em .bss no Native)
- **G9 web security**: `security.rateLimit(key, limit, window)`,
  `sessionCreate/sessionGet/sessionDestroy`, `apiKeyGenerate/apiKeyValid`
  — JVM/Native/JS (`KofSecurityG9Test` 3/3)
- **G12 TLS/HTTPS**: `web.listenSecure(port)` (SSLServerSocket + keytool,
  SAN localhost) + `kof.http` HTTPS (`KofWebTlsTest` 5/5); Native/JS
  reportam WEB002

#### P1 — linguagem
- **Enums**: declaração `enum Color { Red }`; `values()/valueOf()/name()`;
  `==` por conteúdo; constante inválida → SEM030; **switch exaustivo**
  com SEM031 listando casos faltantes; mapeado a String nos descritores
  JVM (`KofEnumTest` + `KofEnumSwitchTest`)
- **Map<K,V> / Set<T>**: `mapOf/setOf` + API completa — JVM (HashMap/
  HashSet), Native (**asm próprio**, keys+vals com crescimento 2x, tag de
  tipo p/ equals) e JS (Map/Set nativos) (`KofMapSetTest` 3/3)
- **spawn/await**: `val r = spawn f()` devolve `Handle<T>` tipado;
  `await r` bloqueia em virtual thread com unboxing de primitivos;
  gaps CONC001 (Native) / CONC003 (JS) / AND001 explícitos
  (`KofAwaitTest` 4/4)
- **kof.validation** (G4): 13 predicados nos 3 targets (`SEM` VAL001)

### Fixes

- decode<List<Int>> no Native caía no ramo JSN002 → link quebrado
  (`List_vtable`) — List/Map excluídos do ramo de objeto composto
- spawn statement no JS falhava em runtime silenciosamente → CONC003
- lambda não-void de expressão única emitia POP antes do areturn
  (VerifyError em todo spawn/await com retorno)
- unbox pós-await restrito ao await (descritor default Object
  englobava kof_ui_* → VerifyError mascarado de "JavaFX" pelo launcher)
- `kof test` volta a ser per-file (PKG002 com 2 main() no mesmo diretório)
- boxing de Map.put/get/remove/contains e Set.* via parameterTypes do
  call-site (mapOf nasce Unknown; pinning no primeiro put)

### Docs
- docs/observability.md novo; ecosystem-coverage G5/G9/G12 DONE;
  security.md atualizada; learn/12-collections reescrito (Map/Set);
  learn/18-concurrency reescrito (spawn/await); enum em learn/04 e
  training/language/{types,syntax}; overview do corpus para 0.1.0

## [0.1.0-beta] - 2026-08-25

### Features

- kof.security no Native (asm x86-64, sem libc): PBKDF2-HMAC-SHA256 600k
  (hash/verify/needsRehash), SHA-512 (FIPS 180-4), JWT HS256
  (create/verify + iat/exp/iss/aud + exceções via try/catch) — fecham
  SECN001/SECN003/SECN004 do G10
- lambdas com captura mutável (box sintético) — kof.time.interval real
- kof.http client + kof.mq + kof.time (scheduler) + kof.config nativo
- ORM completo (where com operadores, saveAll, page, count, deleteAll,
  MariaDB/PostgreSQL reais, MongoDB)
- auditoria + matriz de cobertura + plano kof.security (docs)
- split do JvmRuntime em runtimes separados (fix constant pool 65535)

### Fixes

- success=false do compile (gaps de target falhavam o build)
- kof_json_find_value reescrito (ponteiro/offset + limite do scan)
- hmac_internal com data >64 (opad sobreposto)
- .Ljf_mkstr (kof_alloc clobbered len)
- JDT autobuild do VS Code desativado (corrompia o target/ com ECJ)

## [0.0.5-alpha] - 2026-08-22

### Features

  - KofJS backend (alpha) — same Kof IR lowered to ECMAScript 2022+ ESM modules
  - embedded JS engine (GraalJS) — `kof run --target=js` executes without Node.js
  - KofJS runtime layers — kof-runtime.mjs (core) + kof-runtime-io.mjs (platform via kof_platform)
  - KofJS classes, records, inheritance, interfaces (type-level), generics erasure
  - KofJS List, String API, arrays, JSON (encode/decode with class binding)
  - KofJS exceptions (try/catch/finally), lambdas, if-expressions, source maps
  - record-style class syntax — `class User(String name)` same semantics as record
  - generic return types in function declarations (e.g. `List<Int> ints()`)
  - KofJsE2ETest suite — .kf → .mjs → embedded engine → stdout/exit code
  - CLI platform commands — info, check, lsp, install
  - JVM backend correctness — records, Object methods, concat, comparisons
  - remove fun keyword — functions declared by name
  - JSON parity JVM+Native — object/record encode-decode, long, arrays, field inference
  - List rich API — contains, isEmpty, remove, clear, listOf (JVM + Native parity)
  - native string API parity — indexOf, trim, toUpperCase/toLowerCase, replace, equalsIgnoreCase, split
  - enhance parsing and execution for generic calls and string operations in JVM backend
  - enhance JSON encoding/decoding with improved parameter handling and type inference
  - add JSON support with encoding and decoding functions
  - List<T> builtin collection (native + JVM)
  - implement Kof list operations in JVM backend and native runtime
  - add Kof List type support and associated runtime functions
  - generics with erasure (classes, functions, type args)
  - add support for type parameters in symbol table
  - add support for type parameters in function and class declarations
  - strengthen compile-time type checking
  - constructors in native backend, skip implicit Object super() call
  - add break/continue, fix if/while/for control flow, comparison expressions
  - add .balign directive for method table alignment in NativeBackend and NativeRuntime
  - enhance Kof language type system with type IDs and instanceof support
  - implement switch statement and case handling in Kof language
  - enhance Kof language documentation with comprehensive references, examples, and common patterns
  - add support for do-while statements and enhance type system
  - Complete Phase F implementation with runtime, object model, exceptions, and memory management
  - Add logging for assembly generation and error handling in NativeBackend
  - Phase C+D+E - complete compiler with native backend

### Bugfixes

  - JVM backend execution parity — if/else, strings, generics erasure boxing, records, interfaces, access flags, bitwise ops, long arithmetic
  - switch case fall-through, SUB operand order, function call typing
  - resolve native SIGSEGV and complete string/object ABI

### Documentation

  - align learning and training corpus with 0.0.4-alpha
  - distribution, packaging, versioning and state aligned with 0.0.4-alpha
  - atualizar status, architecture, actual-state, README

### Build

  - centralized versioning, official launchers and packaging

### Tooling

  - official TextMate grammar and editor/LSP documentation

## [0.0.6-alpha] - 2026-08-22

### Features

  - kof.time and kof.io stdlib primitives with JVM+Native parity
  - add support for string length and charAt methods in NativeBackend
  - implement standard library functions for time and I/O operations
  - add JvmJsonRuntime for JSON handling in JVM backend
  - native exception unwinding — real try/catch/finally on x86-64
  - lambda expressions and if-expressions with real lowering
  - real native memory management — allocator header, functional kof_free, live memstats
  - CLI platform commands — info, check, lsp, install
  - JVM backend correctness — records, Object methods, concat, comparisons
  - remove fun keyword — functions declared by name
  - JSON parity JVM+Native — object/record encode-decode, long, arrays, field inference
  - List rich API — contains, isEmpty, remove, clear, listOf (JVM + Native parity)
  - native string API parity — indexOf, trim, toUpperCase/toLowerCase, replace, equalsIgnoreCase, split
  - enhance parsing and execution for generic calls and string operations in JVM backend
  - enhance JSON encoding/decoding with improved parameter handling and type inference
  - add JSON support with encoding and decoding functions
  - List<T> builtin collection (native + JVM)
  - implement Kof list operations in JVM backend and native runtime
  - add Kof List type support and associated runtime functions
  - generics with erasure (classes, functions, type args)
  - add support for type parameters in symbol table
  - add support for type parameters in function and class declarations
  - strengthen compile-time type checking
  - constructors in native backend, skip implicit Object super() call
  - add break/continue, fix if/while/for control flow, comparison expressions
  - add .balign directive for method table alignment in NativeBackend and NativeRuntime
  - enhance Kof language type system with type IDs and instanceof support
  - implement switch statement and case handling in Kof language
  - enhance Kof language documentation with comprehensive references, examples, and common patterns
  - add support for do-while statements and enhance type system
  - Complete Phase F implementation with runtime, object model, exceptions, and memory management
  - Add logging for assembly generation and error handling in NativeBackend
  - Phase C+D+E - complete compiler with native backend

### Bugfixes

  - centralize primitive names, reject lambdas with a clear diagnostic
  - native JSON long parity + array element stride
  - JVM backend execution parity — if/else, strings, generics erasure boxing, records, interfaces, access flags, bitwise ops, long arithmetic
  - switch case fall-through, SUB operand order, function call typing
  - resolve native SIGSEGV and complete string/object ABI

### Documentation

  - align learning and training corpus with 0.0.4-alpha
  - distribution, packaging, versioning and state aligned with 0.0.4-alpha
  - atualizar status, architecture, actual-state, README

### Build

  - bump version to 0.0.5-alpha [skip ci]
  - centralized versioning, official launchers and packaging

### Tooling

  - official TextMate grammar and editor/LSP documentation

## [0.0.7-alpha] - 2026-08-23

### Features

  - kof.time and kof.io stdlib primitives with JVM+Native parity
  - add support for string length and charAt methods in NativeBackend
  - implement standard library functions for time and I/O operations
  - add JvmJsonRuntime for JSON handling in JVM backend
  - native exception unwinding — real try/catch/finally on x86-64
  - lambda expressions and if-expressions with real lowering
  - real native memory management — allocator header, functional kof_free, live memstats
  - CLI platform commands — info, check, lsp, install
  - JVM backend correctness — records, Object methods, concat, comparisons
  - remove fun keyword — functions declared by name
  - JSON parity JVM+Native — object/record encode-decode, long, arrays, field inference
  - List rich API — contains, isEmpty, remove, clear, listOf (JVM + Native parity)
  - native string API parity — indexOf, trim, toUpperCase/toLowerCase, replace, equalsIgnoreCase, split
  - enhance parsing and execution for generic calls and string operations in JVM backend
  - enhance JSON encoding/decoding with improved parameter handling and type inference
  - add JSON support with encoding and decoding functions
  - List<T> builtin collection (native + JVM)
  - implement Kof list operations in JVM backend and native runtime
  - add Kof List type support and associated runtime functions
  - generics with erasure (classes, functions, type args)
  - add support for type parameters in symbol table
  - add support for type parameters in function and class declarations
  - strengthen compile-time type checking
  - constructors in native backend, skip implicit Object super() call
  - add break/continue, fix if/while/for control flow, comparison expressions
  - add .balign directive for method table alignment in NativeBackend and NativeRuntime
  - enhance Kof language type system with type IDs and instanceof support
  - implement switch statement and case handling in Kof language
  - enhance Kof language documentation with comprehensive references, examples, and common patterns
  - add support for do-while statements and enhance type system
  - Complete Phase F implementation with runtime, object model, exceptions, and memory management
  - Add logging for assembly generation and error handling in NativeBackend
  - Phase C+D+E - complete compiler with native backend

### Bugfixes

  - centralize primitive names, reject lambdas with a clear diagnostic
  - native JSON long parity + array element stride
  - JVM backend execution parity — if/else, strings, generics erasure boxing, records, interfaces, access flags, bitwise ops, long arithmetic
  - switch case fall-through, SUB operand order, function call typing
  - resolve native SIGSEGV and complete string/object ABI

### Documentation

  - update local build instructions with lib/kof.jar workaround
  - align learning and training corpus with 0.0.4-alpha
  - distribution, packaging, versioning and state aligned with 0.0.4-alpha
  - atualizar status, architecture, actual-state, README

### Build

  - bump version to 0.0.6-alpha [skip ci]
  - bump version to 0.0.5-alpha [skip ci]
  - centralized versioning, official launchers and packaging

### Tooling

  - official TextMate grammar and editor/LSP documentation

## [0.0.8-alpha] - 2026-08-23

### Features

  - JSON completo (Float/Double, arrays), logging estruturado, kof.db (JDBC + transactions)
  - enhance process output handling with virtual threads
  - enhance kof.ui documentation and add KofJS details
  - add kof.ui section to documentation with UI rendering details and widget descriptions
  - update documentation for UI components, add window and widget examples
  - multiple windows, window size and close-to-exit
  - add window size adjustment functionality in KofUi
  - add support for font size, bold, and color properties in KofUi labels
  - add label styling and window theme support in KofUi and related backends
  - Introduce new UI components and bindings for Input, Column, Row, View, and Style
  - enhance KofJsRunner to support program arguments and update related components
  - update documentation and fix issues in Kof Spring Starter phases, enhance runtime functions
  - implement native configuration and logging modules, update documentation
  - update documentation and enhance semantic analysis for config and logging namespaces
  - enhance KofJsRunner output handling and add webview settings for file access
  - implement native web stack with routing, middleware, and JSON support
  - enhance Kof compiler and runtime with new features and bug fixes
  - native webview shell — kof-webview (WebKitGTK embedded)
  - kof.ui webview — DOM shim, HTML serialization, system webview
  - enhance KofJsRunner to support window rendering and HTML capture
  - introduce kof.security module for password hashing, JWT, and cryptography
  - kof-debug MVP completo — breakpoints por linha Kof + stack trace
  - kof.ui Window and Label — webview container with binding
  - kof.ui foundation — Color, Palette, Theme + main(args)
  - add kof.ui foundation with Color, Palette, and Theme support
  - enhance benchmarking with JS target and add CPU time tracking
  - debugger Fase 2 — LocalVariableTable no JVM
  - Kof debugger — Fase 1 (DebugInfo na IR) + docs + JVM line metadata
  - add debug information support with source file and line number mapping in JVM backend
  - enhance IRModule and backend to support source name and debugging information
  - implement Kof debugging support with source mapping and debug metadata
  - add initializer support for record components and enhance semantic analysis
  - idiomatic core — field initializers applied, \uXXXX escapes, typed listOf<T>()
  - implement increment operations with correct semantics and add tests for idiomatic behavior
  - implement generics in Kof with examples for lists and sets
  - enhance method symbol to allow dynamic return type updates and improve semantic analysis
  - refactor semantic analysis by defining constructor and method symbols, and analyzing their bodies
  - add Color class with ARGB semantics and enhance color handling in the compiler
  - enhance literal parsing and add hexadecimal support in lexer
  - Fase L — release gate hardened + package revalidated
  - Fase K — assert primitive + expanded golden + kof test integration
  - implement assertion handling with AssertE2ETest and add various test cases for control flow, functions, and records
  - add AssertStmt for assertion handling and update lexer and token types
  - Fase J — LSP textDocument/didClose clears diagnostics
  - add KofJS backend and runtime support, including parity tests for JVM and JS
  - Enhance parsing and runtime capabilities with new if-expression handling and runtime options
  - kof test — run programs and report PASS/FAIL by exit code
  - Fase I — spawn: concurrent tasks on the JVM (virtual threads)
  - Introduce kof.io filesystem API for file and directory operations
  - kof.io documentation, multiplatform CI and platform guard
  - Fase J — LSP URI fix + editor grammar builtins
  - Fase I+L — concurrency semantics design + distribution validation
  - Fase K — real golden and integration test infrastructure
  - Enhance KofJS backend with improved function handling and module support
  - Implement Kof HTTP server and I/O library
  - idioms corpus, anti-pattern catalog, datasets, corrections
  - kof.time and kof.io stdlib primitives with JVM+Native parity
  - add support for string length and charAt methods in NativeBackend
  - implement standard library functions for time and I/O operations
  - add JvmJsonRuntime for JSON handling in JVM backend
  - native exception unwinding — real try/catch/finally on x86-64
  - lambda expressions and if-expressions with real lowering
  - real native memory management — allocator header, functional kof_free, live memstats
  - CLI platform commands — info, check, lsp, install
  - JVM backend correctness — records, Object methods, concat, comparisons
  - remove fun keyword — functions declared by name
  - JSON parity JVM+Native — object/record encode-decode, long, arrays, field inference
  - List rich API — contains, isEmpty, remove, clear, listOf (JVM + Native parity)
  - native string API parity — indexOf, trim, toUpperCase/toLowerCase, replace, equalsIgnoreCase, split
  - enhance parsing and execution for generic calls and string operations in JVM backend
  - enhance JSON encoding/decoding with improved parameter handling and type inference
  - add JSON support with encoding and decoding functions
  - List<T> builtin collection (native + JVM)
  - implement Kof list operations in JVM backend and native runtime
  - add Kof List type support and associated runtime functions
  - generics with erasure (classes, functions, type args)
  - add support for type parameters in symbol table
  - add support for type parameters in function and class declarations
  - strengthen compile-time type checking
  - constructors in native backend, skip implicit Object super() call
  - add break/continue, fix if/while/for control flow, comparison expressions
  - add .balign directive for method table alignment in NativeBackend and NativeRuntime
  - enhance Kof language type system with type IDs and instanceof support
  - implement switch statement and case handling in Kof language
  - enhance Kof language documentation with comprehensive references, examples, and common patterns
  - add support for do-while statements and enhance type system
  - Complete Phase F implementation with runtime, object model, exceptions, and memory management
  - Add logging for assembly generation and error handling in NativeBackend
  - Phase C+D+E - complete compiler with native backend

### Bugfixes

  - update expected output for label style binding in JS target
  - guard kofUiButtonRemove against missing action registry
  - update ClassPrepare event kind and improve event logging in JdwpClient
  - sound IR optimizer, JS switch routing and list construction
  - field initializers, record defaults and increment semantics
  - idiomatic core — name resolution by symbol, return inference, this-free fields
  - bool semantics parity — 0/1 results, true/false formatting, Multi-Release shade
  - restore kof_io_ dispatch in JVM runtime helper
  - JVM constructor super detection, List<ref> checkcast, kof.List descriptor
  - centralize primitive names, reject lambdas with a clear diagnostic
  - native JSON long parity + array element stride
  - JVM backend execution parity — if/else, strings, generics erasure boxing, records, interfaces, access flags, bitwise ops, long arithmetic
  - switch case fall-through, SUB operand order, function call typing
  - resolve native SIGSEGV and complete string/object ABI

### Documentation

  - README e status finais (513/513, kof.db, JSON completo)
  - document the intent-oriented paradigm with honest framing
  - update local build instructions with lib/kof.jar workaround
  - document the kof.ui platform (widgets, events, webview)
  - auditoria do ecossistema da stdlib — matriz de cobertura (G1-G12)
  - debugger — Fases 1-3 implementadas (kof-debug MVP validado)
  - status — debugger Fases 1-2 (DebugInfo na IR, JVM metadata)
  - status — 394 testes, guidelines idiomáticas e estado real
  - fake-idioms — primary constructor is implemented (record-style since 0.0.5)
  - sync all .md with real 0.0.5 state
  - reorganize — move completed docs out of future/
  - status — 375/375, KofJS 100% (GraalJS embutido)
  - status — kof.io filesystem API, kof test, current test state
  - status — Fases H/J/K/L concluídas, I design pronto
  - Legacy Migration Platform architecture
  - align learning and training corpus with 0.0.4-alpha
  - distribution, packaging, versioning and state aligned with 0.0.4-alpha
  - atualizar status, architecture, actual-state, README

### Build

  - bump version to 0.0.7-alpha [skip ci]
  - rebuild kof-webview with file:// module CORS fix
  - bump version to 0.0.6-alpha [skip ci]
  - bump version to 0.0.5-alpha [skip ci]
  - centralized versioning, official launchers and packaging

### Tooling

  - official TextMate grammar and editor/LSP documentation

## [0.0.9-alpha] - 2026-08-24

### Features

  - implement MySQL authentication scramble using SHA-1
  - MySQL/MariaDB via wire protocol sobre sockets nativos (WIP)
  - add hidden easter egg registry and corresponding tests
  - native kof.db with SQLite via direct .so linking (no JDBC driver)
  - enhance string replacement functionality and content type handling in HTTP server
  - enhance string replace functionality and constructor handling in backends
  - JSON completo (Float/Double, arrays), logging estruturado, kof.db (JDBC + transactions)
  - enhance process output handling with virtual threads
  - enhance kof.ui documentation and add KofJS details
  - add kof.ui section to documentation with UI rendering details and widget descriptions
  - update documentation for UI components, add window and widget examples
  - multiple windows, window size and close-to-exit
  - add window size adjustment functionality in KofUi
  - add support for font size, bold, and color properties in KofUi labels
  - add label styling and window theme support in KofUi and related backends
  - Introduce new UI components and bindings for Input, Column, Row, View, and Style
  - enhance KofJsRunner to support program arguments and update related components
  - update documentation and fix issues in Kof Spring Starter phases, enhance runtime functions
  - implement native configuration and logging modules, update documentation
  - update documentation and enhance semantic analysis for config and logging namespaces
  - enhance KofJsRunner output handling and add webview settings for file access
  - implement native web stack with routing, middleware, and JSON support
  - enhance Kof compiler and runtime with new features and bug fixes
  - native webview shell — kof-webview (WebKitGTK embedded)
  - kof.ui webview — DOM shim, HTML serialization, system webview
  - enhance KofJsRunner to support window rendering and HTML capture
  - introduce kof.security module for password hashing, JWT, and cryptography
  - kof-debug MVP completo — breakpoints por linha Kof + stack trace
  - kof.ui Window and Label — webview container with binding
  - kof.ui foundation — Color, Palette, Theme + main(args)
  - add kof.ui foundation with Color, Palette, and Theme support
  - enhance benchmarking with JS target and add CPU time tracking
  - debugger Fase 2 — LocalVariableTable no JVM
  - Kof debugger — Fase 1 (DebugInfo na IR) + docs + JVM line metadata
  - add debug information support with source file and line number mapping in JVM backend
  - enhance IRModule and backend to support source name and debugging information
  - implement Kof debugging support with source mapping and debug metadata
  - add initializer support for record components and enhance semantic analysis
  - idiomatic core — field initializers applied, \uXXXX escapes, typed listOf<T>()
  - implement increment operations with correct semantics and add tests for idiomatic behavior
  - implement generics in Kof with examples for lists and sets
  - enhance method symbol to allow dynamic return type updates and improve semantic analysis
  - refactor semantic analysis by defining constructor and method symbols, and analyzing their bodies
  - add Color class with ARGB semantics and enhance color handling in the compiler
  - enhance literal parsing and add hexadecimal support in lexer
  - Fase L — release gate hardened + package revalidated
  - Fase K — assert primitive + expanded golden + kof test integration
  - implement assertion handling with AssertE2ETest and add various test cases for control flow, functions, and records
  - add AssertStmt for assertion handling and update lexer and token types
  - Fase J — LSP textDocument/didClose clears diagnostics
  - add KofJS backend and runtime support, including parity tests for JVM and JS
  - Enhance parsing and runtime capabilities with new if-expression handling and runtime options
  - kof test — run programs and report PASS/FAIL by exit code
  - Fase I — spawn: concurrent tasks on the JVM (virtual threads)
  - Introduce kof.io filesystem API for file and directory operations
  - kof.io documentation, multiplatform CI and platform guard
  - Fase J — LSP URI fix + editor grammar builtins
  - Fase I+L — concurrency semantics design + distribution validation
  - Fase K — real golden and integration test infrastructure
  - Enhance KofJS backend with improved function handling and module support
  - Implement Kof HTTP server and I/O library
  - idioms corpus, anti-pattern catalog, datasets, corrections
  - kof.time and kof.io stdlib primitives with JVM+Native parity
  - add support for string length and charAt methods in NativeBackend
  - implement standard library functions for time and I/O operations
  - add JvmJsonRuntime for JSON handling in JVM backend
  - native exception unwinding — real try/catch/finally on x86-64
  - lambda expressions and if-expressions with real lowering
  - real native memory management — allocator header, functional kof_free, live memstats
  - CLI platform commands — info, check, lsp, install
  - JVM backend correctness — records, Object methods, concat, comparisons
  - remove fun keyword — functions declared by name
  - JSON parity JVM+Native — object/record encode-decode, long, arrays, field inference
  - List rich API — contains, isEmpty, remove, clear, listOf (JVM + Native parity)
  - native string API parity — indexOf, trim, toUpperCase/toLowerCase, replace, equalsIgnoreCase, split
  - enhance parsing and execution for generic calls and string operations in JVM backend
  - enhance JSON encoding/decoding with improved parameter handling and type inference
  - add JSON support with encoding and decoding functions
  - List<T> builtin collection (native + JVM)
  - implement Kof list operations in JVM backend and native runtime
  - add Kof List type support and associated runtime functions
  - generics with erasure (classes, functions, type args)
  - add support for type parameters in symbol table
  - add support for type parameters in function and class declarations
  - strengthen compile-time type checking
  - constructors in native backend, skip implicit Object super() call
  - add break/continue, fix if/while/for control flow, comparison expressions
  - add .balign directive for method table alignment in NativeBackend and NativeRuntime
  - enhance Kof language type system with type IDs and instanceof support
  - implement switch statement and case handling in Kof language
  - enhance Kof language documentation with comprehensive references, examples, and common patterns
  - add support for do-while statements and enhance type system
  - Complete Phase F implementation with runtime, object model, exceptions, and memory management
  - Add logging for assembly generation and error handling in NativeBackend
  - Phase C+D+E - complete compiler with native backend

### Bugfixes

  - enhance try-finally parsing logic to correctly handle labels and control flow
  - enhance MySQL connection detection and linker command for conditional library inclusion
  - add --as-needed flag to linker command for improved dependency handling
  - update output handling in various E2E tests for consistent UTF-8 encoding and line endings
  - update file path handling for cross-platform compatibility and enhance test process encoding
  - golden tests need the CLI jar; launcher must not break JDK 21
  - update expected output for label style binding in JS target
  - guard kofUiButtonRemove against missing action registry
  - update ClassPrepare event kind and improve event logging in JdwpClient
  - sound IR optimizer, JS switch routing and list construction
  - field initializers, record defaults and increment semantics
  - idiomatic core — name resolution by symbol, return inference, this-free fields
  - bool semantics parity — 0/1 results, true/false formatting, Multi-Release shade
  - restore kof_io_ dispatch in JVM runtime helper
  - JVM constructor super detection, List<ref> checkcast, kof.List descriptor
  - centralize primitive names, reject lambdas with a clear diagnostic
  - native JSON long parity + array element stride
  - JVM backend execution parity — if/else, strings, generics erasure boxing, records, interfaces, access flags, bitwise ops, long arithmetic
  - switch case fall-through, SUB operand order, function call typing
  - resolve native SIGSEGV and complete string/object ABI

### Documentation

  - README e status finais (513/513, kof.db, JSON completo)
  - document the intent-oriented paradigm with honest framing
  - update local build instructions with lib/kof.jar workaround
  - document the kof.ui platform (widgets, events, webview)
  - auditoria do ecossistema da stdlib — matriz de cobertura (G1-G12)
  - debugger — Fases 1-3 implementadas (kof-debug MVP validado)
  - status — debugger Fases 1-2 (DebugInfo na IR, JVM metadata)
  - status — 394 testes, guidelines idiomáticas e estado real
  - fake-idioms — primary constructor is implemented (record-style since 0.0.5)
  - sync all .md with real 0.0.5 state
  - reorganize — move completed docs out of future/
  - status — 375/375, KofJS 100% (GraalJS embutido)
  - status — kof.io filesystem API, kof test, current test state
  - status — Fases H/J/K/L concluídas, I design pronto
  - Legacy Migration Platform architecture
  - align learning and training corpus with 0.0.4-alpha
  - distribution, packaging, versioning and state aligned with 0.0.4-alpha
  - atualizar status, architecture, actual-state, README

### Build

  - bump version to 0.0.8-alpha [skip ci]
  - bump version to 0.0.7-alpha [skip ci]
  - rebuild kof-webview with file:// module CORS fix
  - bump version to 0.0.6-alpha [skip ci]
  - bump version to 0.0.5-alpha [skip ci]
  - centralized versioning, official launchers and packaging

### Tooling

  - official TextMate grammar and editor/LSP documentation

## [0.0.10-alpha] - 2026-08-24

### Features

  - implement MySQL authentication scramble using SHA-1
  - MySQL/MariaDB via wire protocol sobre sockets nativos (WIP)
  - add hidden easter egg registry and corresponding tests
  - native kof.db with SQLite via direct .so linking (no JDBC driver)
  - enhance string replacement functionality and content type handling in HTTP server
  - enhance string replace functionality and constructor handling in backends
  - JSON completo (Float/Double, arrays), logging estruturado, kof.db (JDBC + transactions)
  - enhance process output handling with virtual threads
  - enhance kof.ui documentation and add KofJS details
  - add kof.ui section to documentation with UI rendering details and widget descriptions
  - update documentation for UI components, add window and widget examples
  - multiple windows, window size and close-to-exit
  - add window size adjustment functionality in KofUi
  - add support for font size, bold, and color properties in KofUi labels
  - add label styling and window theme support in KofUi and related backends
  - Introduce new UI components and bindings for Input, Column, Row, View, and Style
  - enhance KofJsRunner to support program arguments and update related components
  - update documentation and fix issues in Kof Spring Starter phases, enhance runtime functions
  - implement native configuration and logging modules, update documentation
  - update documentation and enhance semantic analysis for config and logging namespaces
  - enhance KofJsRunner output handling and add webview settings for file access
  - implement native web stack with routing, middleware, and JSON support
  - enhance Kof compiler and runtime with new features and bug fixes
  - native webview shell — kof-webview (WebKitGTK embedded)
  - kof.ui webview — DOM shim, HTML serialization, system webview
  - enhance KofJsRunner to support window rendering and HTML capture
  - introduce kof.security module for password hashing, JWT, and cryptography
  - kof-debug MVP completo — breakpoints por linha Kof + stack trace
  - kof.ui Window and Label — webview container with binding
  - kof.ui foundation — Color, Palette, Theme + main(args)
  - add kof.ui foundation with Color, Palette, and Theme support
  - enhance benchmarking with JS target and add CPU time tracking
  - debugger Fase 2 — LocalVariableTable no JVM
  - Kof debugger — Fase 1 (DebugInfo na IR) + docs + JVM line metadata
  - add debug information support with source file and line number mapping in JVM backend
  - enhance IRModule and backend to support source name and debugging information
  - implement Kof debugging support with source mapping and debug metadata
  - add initializer support for record components and enhance semantic analysis
  - idiomatic core — field initializers applied, \uXXXX escapes, typed listOf<T>()
  - implement increment operations with correct semantics and add tests for idiomatic behavior
  - implement generics in Kof with examples for lists and sets
  - enhance method symbol to allow dynamic return type updates and improve semantic analysis
  - refactor semantic analysis by defining constructor and method symbols, and analyzing their bodies
  - add Color class with ARGB semantics and enhance color handling in the compiler
  - enhance literal parsing and add hexadecimal support in lexer
  - Fase L — release gate hardened + package revalidated
  - Fase K — assert primitive + expanded golden + kof test integration
  - implement assertion handling with AssertE2ETest and add various test cases for control flow, functions, and records
  - add AssertStmt for assertion handling and update lexer and token types
  - Fase J — LSP textDocument/didClose clears diagnostics
  - add KofJS backend and runtime support, including parity tests for JVM and JS
  - Enhance parsing and runtime capabilities with new if-expression handling and runtime options
  - kof test — run programs and report PASS/FAIL by exit code
  - Fase I — spawn: concurrent tasks on the JVM (virtual threads)
  - Introduce kof.io filesystem API for file and directory operations
  - kof.io documentation, multiplatform CI and platform guard
  - Fase J — LSP URI fix + editor grammar builtins
  - Fase I+L — concurrency semantics design + distribution validation
  - Fase K — real golden and integration test infrastructure
  - Enhance KofJS backend with improved function handling and module support
  - Implement Kof HTTP server and I/O library
  - idioms corpus, anti-pattern catalog, datasets, corrections
  - kof.time and kof.io stdlib primitives with JVM+Native parity
  - add support for string length and charAt methods in NativeBackend
  - implement standard library functions for time and I/O operations
  - add JvmJsonRuntime for JSON handling in JVM backend
  - native exception unwinding — real try/catch/finally on x86-64
  - lambda expressions and if-expressions with real lowering
  - real native memory management — allocator header, functional kof_free, live memstats
  - CLI platform commands — info, check, lsp, install
  - JVM backend correctness — records, Object methods, concat, comparisons
  - remove fun keyword — functions declared by name
  - JSON parity JVM+Native — object/record encode-decode, long, arrays, field inference
  - List rich API — contains, isEmpty, remove, clear, listOf (JVM + Native parity)
  - native string API parity — indexOf, trim, toUpperCase/toLowerCase, replace, equalsIgnoreCase, split
  - enhance parsing and execution for generic calls and string operations in JVM backend
  - enhance JSON encoding/decoding with improved parameter handling and type inference
  - add JSON support with encoding and decoding functions
  - List<T> builtin collection (native + JVM)
  - implement Kof list operations in JVM backend and native runtime
  - add Kof List type support and associated runtime functions
  - generics with erasure (classes, functions, type args)
  - add support for type parameters in symbol table
  - add support for type parameters in function and class declarations
  - strengthen compile-time type checking
  - constructors in native backend, skip implicit Object super() call
  - add break/continue, fix if/while/for control flow, comparison expressions
  - add .balign directive for method table alignment in NativeBackend and NativeRuntime
  - enhance Kof language type system with type IDs and instanceof support
  - implement switch statement and case handling in Kof language
  - enhance Kof language documentation with comprehensive references, examples, and common patterns
  - add support for do-while statements and enhance type system
  - Complete Phase F implementation with runtime, object model, exceptions, and memory management
  - Add logging for assembly generation and error handling in NativeBackend
  - Phase C+D+E - complete compiler with native backend

### Bugfixes

  - CI multiplataforma + kof.db link seletivo + JS try/finally + package Adoptium
  - enhance try-finally parsing logic to correctly handle labels and control flow
  - enhance MySQL connection detection and linker command for conditional library inclusion
  - add --as-needed flag to linker command for improved dependency handling
  - update output handling in various E2E tests for consistent UTF-8 encoding and line endings
  - update file path handling for cross-platform compatibility and enhance test process encoding
  - golden tests need the CLI jar; launcher must not break JDK 21
  - update expected output for label style binding in JS target
  - guard kofUiButtonRemove against missing action registry
  - update ClassPrepare event kind and improve event logging in JdwpClient
  - sound IR optimizer, JS switch routing and list construction
  - field initializers, record defaults and increment semantics
  - idiomatic core — name resolution by symbol, return inference, this-free fields
  - bool semantics parity — 0/1 results, true/false formatting, Multi-Release shade
  - restore kof_io_ dispatch in JVM runtime helper
  - JVM constructor super detection, List<ref> checkcast, kof.List descriptor
  - centralize primitive names, reject lambdas with a clear diagnostic
  - native JSON long parity + array element stride
  - JVM backend execution parity — if/else, strings, generics erasure boxing, records, interfaces, access flags, bitwise ops, long arithmetic
  - switch case fall-through, SUB operand order, function call typing
  - resolve native SIGSEGV and complete string/object ABI

### Documentation

  - README e status finais (513/513, kof.db, JSON completo)
  - document the intent-oriented paradigm with honest framing
  - update local build instructions with lib/kof.jar workaround
  - document the kof.ui platform (widgets, events, webview)
  - auditoria do ecossistema da stdlib — matriz de cobertura (G1-G12)
  - debugger — Fases 1-3 implementadas (kof-debug MVP validado)
  - status — debugger Fases 1-2 (DebugInfo na IR, JVM metadata)
  - status — 394 testes, guidelines idiomáticas e estado real
  - fake-idioms — primary constructor is implemented (record-style since 0.0.5)
  - sync all .md with real 0.0.5 state
  - reorganize — move completed docs out of future/
  - status — 375/375, KofJS 100% (GraalJS embutido)
  - status — kof.io filesystem API, kof test, current test state
  - status — Fases H/J/K/L concluídas, I design pronto
  - Legacy Migration Platform architecture
  - align learning and training corpus with 0.0.4-alpha
  - distribution, packaging, versioning and state aligned with 0.0.4-alpha
  - atualizar status, architecture, actual-state, README

### Build

  - bump version to 0.0.9-alpha [skip ci]
  - bump version to 0.0.8-alpha [skip ci]
  - bump version to 0.0.7-alpha [skip ci]
  - rebuild kof-webview with file:// module CORS fix
  - bump version to 0.0.6-alpha [skip ci]
  - bump version to 0.0.5-alpha [skip ci]
  - centralized versioning, official launchers and packaging

### Tooling

  - official TextMate grammar and editor/LSP documentation

## [0.0.11-alpha] - 2026-08-24

### Features

  - implement MySQL authentication scramble using SHA-1
  - MySQL/MariaDB via wire protocol sobre sockets nativos (WIP)
  - add hidden easter egg registry and corresponding tests
  - native kof.db with SQLite via direct .so linking (no JDBC driver)
  - enhance string replacement functionality and content type handling in HTTP server
  - enhance string replace functionality and constructor handling in backends
  - JSON completo (Float/Double, arrays), logging estruturado, kof.db (JDBC + transactions)
  - enhance process output handling with virtual threads
  - enhance kof.ui documentation and add KofJS details
  - add kof.ui section to documentation with UI rendering details and widget descriptions
  - update documentation for UI components, add window and widget examples
  - multiple windows, window size and close-to-exit
  - add window size adjustment functionality in KofUi
  - add support for font size, bold, and color properties in KofUi labels
  - add label styling and window theme support in KofUi and related backends
  - Introduce new UI components and bindings for Input, Column, Row, View, and Style
  - enhance KofJsRunner to support program arguments and update related components
  - update documentation and fix issues in Kof Spring Starter phases, enhance runtime functions
  - implement native configuration and logging modules, update documentation
  - update documentation and enhance semantic analysis for config and logging namespaces
  - enhance KofJsRunner output handling and add webview settings for file access
  - implement native web stack with routing, middleware, and JSON support
  - enhance Kof compiler and runtime with new features and bug fixes
  - native webview shell — kof-webview (WebKitGTK embedded)
  - kof.ui webview — DOM shim, HTML serialization, system webview
  - enhance KofJsRunner to support window rendering and HTML capture
  - introduce kof.security module for password hashing, JWT, and cryptography
  - kof-debug MVP completo — breakpoints por linha Kof + stack trace
  - kof.ui Window and Label — webview container with binding
  - kof.ui foundation — Color, Palette, Theme + main(args)
  - add kof.ui foundation with Color, Palette, and Theme support
  - enhance benchmarking with JS target and add CPU time tracking
  - debugger Fase 2 — LocalVariableTable no JVM
  - Kof debugger — Fase 1 (DebugInfo na IR) + docs + JVM line metadata
  - add debug information support with source file and line number mapping in JVM backend
  - enhance IRModule and backend to support source name and debugging information
  - implement Kof debugging support with source mapping and debug metadata
  - add initializer support for record components and enhance semantic analysis
  - idiomatic core — field initializers applied, \uXXXX escapes, typed listOf<T>()
  - implement increment operations with correct semantics and add tests for idiomatic behavior
  - implement generics in Kof with examples for lists and sets
  - enhance method symbol to allow dynamic return type updates and improve semantic analysis
  - refactor semantic analysis by defining constructor and method symbols, and analyzing their bodies
  - add Color class with ARGB semantics and enhance color handling in the compiler
  - enhance literal parsing and add hexadecimal support in lexer
  - Fase L — release gate hardened + package revalidated
  - Fase K — assert primitive + expanded golden + kof test integration
  - implement assertion handling with AssertE2ETest and add various test cases for control flow, functions, and records
  - add AssertStmt for assertion handling and update lexer and token types
  - Fase J — LSP textDocument/didClose clears diagnostics
  - add KofJS backend and runtime support, including parity tests for JVM and JS
  - Enhance parsing and runtime capabilities with new if-expression handling and runtime options
  - kof test — run programs and report PASS/FAIL by exit code
  - Fase I — spawn: concurrent tasks on the JVM (virtual threads)
  - Introduce kof.io filesystem API for file and directory operations
  - kof.io documentation, multiplatform CI and platform guard
  - Fase J — LSP URI fix + editor grammar builtins
  - Fase I+L — concurrency semantics design + distribution validation
  - Fase K — real golden and integration test infrastructure
  - Enhance KofJS backend with improved function handling and module support
  - Implement Kof HTTP server and I/O library
  - idioms corpus, anti-pattern catalog, datasets, corrections
  - kof.time and kof.io stdlib primitives with JVM+Native parity
  - add support for string length and charAt methods in NativeBackend
  - implement standard library functions for time and I/O operations
  - add JvmJsonRuntime for JSON handling in JVM backend
  - native exception unwinding — real try/catch/finally on x86-64
  - lambda expressions and if-expressions with real lowering
  - real native memory management — allocator header, functional kof_free, live memstats
  - CLI platform commands — info, check, lsp, install
  - JVM backend correctness — records, Object methods, concat, comparisons
  - remove fun keyword — functions declared by name
  - JSON parity JVM+Native — object/record encode-decode, long, arrays, field inference
  - List rich API — contains, isEmpty, remove, clear, listOf (JVM + Native parity)
  - native string API parity — indexOf, trim, toUpperCase/toLowerCase, replace, equalsIgnoreCase, split
  - enhance parsing and execution for generic calls and string operations in JVM backend
  - enhance JSON encoding/decoding with improved parameter handling and type inference
  - add JSON support with encoding and decoding functions
  - List<T> builtin collection (native + JVM)
  - implement Kof list operations in JVM backend and native runtime
  - add Kof List type support and associated runtime functions
  - generics with erasure (classes, functions, type args)
  - add support for type parameters in symbol table
  - add support for type parameters in function and class declarations
  - strengthen compile-time type checking
  - constructors in native backend, skip implicit Object super() call
  - add break/continue, fix if/while/for control flow, comparison expressions
  - add .balign directive for method table alignment in NativeBackend and NativeRuntime
  - enhance Kof language type system with type IDs and instanceof support
  - implement switch statement and case handling in Kof language
  - enhance Kof language documentation with comprehensive references, examples, and common patterns
  - add support for do-while statements and enhance type system
  - Complete Phase F implementation with runtime, object model, exceptions, and memory management
  - Add logging for assembly generation and error handling in NativeBackend
  - Phase C+D+E - complete compiler with native backend

### Bugfixes

  - launcher e validate usam o JDK embarcado em todas as plataformas
  - CI multiplataforma + kof.db link seletivo + JS try/finally + package Adoptium
  - enhance try-finally parsing logic to correctly handle labels and control flow
  - enhance MySQL connection detection and linker command for conditional library inclusion
  - add --as-needed flag to linker command for improved dependency handling
  - update output handling in various E2E tests for consistent UTF-8 encoding and line endings
  - update file path handling for cross-platform compatibility and enhance test process encoding
  - golden tests need the CLI jar; launcher must not break JDK 21
  - update expected output for label style binding in JS target
  - guard kofUiButtonRemove against missing action registry
  - update ClassPrepare event kind and improve event logging in JdwpClient
  - sound IR optimizer, JS switch routing and list construction
  - field initializers, record defaults and increment semantics
  - idiomatic core — name resolution by symbol, return inference, this-free fields
  - bool semantics parity — 0/1 results, true/false formatting, Multi-Release shade
  - restore kof_io_ dispatch in JVM runtime helper
  - JVM constructor super detection, List<ref> checkcast, kof.List descriptor
  - centralize primitive names, reject lambdas with a clear diagnostic
  - native JSON long parity + array element stride
  - JVM backend execution parity — if/else, strings, generics erasure boxing, records, interfaces, access flags, bitwise ops, long arithmetic
  - switch case fall-through, SUB operand order, function call typing
  - resolve native SIGSEGV and complete string/object ABI

### Documentation

  - DATABASE_VISION — nível 0 do kof.db implementado (JDBC idiomático JVM, SQLite nativo, MySQL WIP); níveis 1-4 seguem a visão
  - README e status finais (513/513, kof.db, JSON completo)
  - document the intent-oriented paradigm with honest framing
  - update local build instructions with lib/kof.jar workaround
  - document the kof.ui platform (widgets, events, webview)
  - auditoria do ecossistema da stdlib — matriz de cobertura (G1-G12)
  - debugger — Fases 1-3 implementadas (kof-debug MVP validado)
  - status — debugger Fases 1-2 (DebugInfo na IR, JVM metadata)
  - status — 394 testes, guidelines idiomáticas e estado real
  - fake-idioms — primary constructor is implemented (record-style since 0.0.5)
  - sync all .md with real 0.0.5 state
  - reorganize — move completed docs out of future/
  - status — 375/375, KofJS 100% (GraalJS embutido)
  - status — kof.io filesystem API, kof test, current test state
  - status — Fases H/J/K/L concluídas, I design pronto
  - Legacy Migration Platform architecture
  - align learning and training corpus with 0.0.4-alpha
  - distribution, packaging, versioning and state aligned with 0.0.4-alpha
  - atualizar status, architecture, actual-state, README

### Build

  - bump version to 0.0.10-alpha [skip ci]
  - bump version to 0.0.9-alpha [skip ci]
  - bump version to 0.0.8-alpha [skip ci]
  - bump version to 0.0.7-alpha [skip ci]
  - rebuild kof-webview with file:// module CORS fix
  - bump version to 0.0.6-alpha [skip ci]
  - bump version to 0.0.5-alpha [skip ci]
  - centralized versioning, official launchers and packaging

### Tooling

  - official TextMate grammar and editor/LSP documentation

## [0.0.12-alpha] - 2026-08-24

### Features

  - implement MySQL authentication scramble using SHA-1
  - MySQL/MariaDB via wire protocol sobre sockets nativos (WIP)
  - add hidden easter egg registry and corresponding tests
  - native kof.db with SQLite via direct .so linking (no JDBC driver)
  - enhance string replacement functionality and content type handling in HTTP server
  - enhance string replace functionality and constructor handling in backends
  - JSON completo (Float/Double, arrays), logging estruturado, kof.db (JDBC + transactions)
  - enhance process output handling with virtual threads
  - enhance kof.ui documentation and add KofJS details
  - add kof.ui section to documentation with UI rendering details and widget descriptions
  - update documentation for UI components, add window and widget examples
  - multiple windows, window size and close-to-exit
  - add window size adjustment functionality in KofUi
  - add support for font size, bold, and color properties in KofUi labels
  - add label styling and window theme support in KofUi and related backends
  - Introduce new UI components and bindings for Input, Column, Row, View, and Style
  - enhance KofJsRunner to support program arguments and update related components
  - update documentation and fix issues in Kof Spring Starter phases, enhance runtime functions
  - implement native configuration and logging modules, update documentation
  - update documentation and enhance semantic analysis for config and logging namespaces
  - enhance KofJsRunner output handling and add webview settings for file access
  - implement native web stack with routing, middleware, and JSON support
  - enhance Kof compiler and runtime with new features and bug fixes
  - native webview shell — kof-webview (WebKitGTK embedded)
  - kof.ui webview — DOM shim, HTML serialization, system webview
  - enhance KofJsRunner to support window rendering and HTML capture
  - introduce kof.security module for password hashing, JWT, and cryptography
  - kof-debug MVP completo — breakpoints por linha Kof + stack trace
  - kof.ui Window and Label — webview container with binding
  - kof.ui foundation — Color, Palette, Theme + main(args)
  - add kof.ui foundation with Color, Palette, and Theme support
  - enhance benchmarking with JS target and add CPU time tracking
  - debugger Fase 2 — LocalVariableTable no JVM
  - Kof debugger — Fase 1 (DebugInfo na IR) + docs + JVM line metadata
  - add debug information support with source file and line number mapping in JVM backend
  - enhance IRModule and backend to support source name and debugging information
  - implement Kof debugging support with source mapping and debug metadata
  - add initializer support for record components and enhance semantic analysis
  - idiomatic core — field initializers applied, \uXXXX escapes, typed listOf<T>()
  - implement increment operations with correct semantics and add tests for idiomatic behavior
  - implement generics in Kof with examples for lists and sets
  - enhance method symbol to allow dynamic return type updates and improve semantic analysis
  - refactor semantic analysis by defining constructor and method symbols, and analyzing their bodies
  - add Color class with ARGB semantics and enhance color handling in the compiler
  - enhance literal parsing and add hexadecimal support in lexer
  - Fase L — release gate hardened + package revalidated
  - Fase K — assert primitive + expanded golden + kof test integration
  - implement assertion handling with AssertE2ETest and add various test cases for control flow, functions, and records
  - add AssertStmt for assertion handling and update lexer and token types
  - Fase J — LSP textDocument/didClose clears diagnostics
  - add KofJS backend and runtime support, including parity tests for JVM and JS
  - Enhance parsing and runtime capabilities with new if-expression handling and runtime options
  - kof test — run programs and report PASS/FAIL by exit code
  - Fase I — spawn: concurrent tasks on the JVM (virtual threads)
  - Introduce kof.io filesystem API for file and directory operations
  - kof.io documentation, multiplatform CI and platform guard
  - Fase J — LSP URI fix + editor grammar builtins
  - Fase I+L — concurrency semantics design + distribution validation
  - Fase K — real golden and integration test infrastructure
  - Enhance KofJS backend with improved function handling and module support
  - Implement Kof HTTP server and I/O library
  - idioms corpus, anti-pattern catalog, datasets, corrections
  - kof.time and kof.io stdlib primitives with JVM+Native parity
  - add support for string length and charAt methods in NativeBackend
  - implement standard library functions for time and I/O operations
  - add JvmJsonRuntime for JSON handling in JVM backend
  - native exception unwinding — real try/catch/finally on x86-64
  - lambda expressions and if-expressions with real lowering
  - real native memory management — allocator header, functional kof_free, live memstats
  - CLI platform commands — info, check, lsp, install
  - JVM backend correctness — records, Object methods, concat, comparisons
  - remove fun keyword — functions declared by name
  - JSON parity JVM+Native — object/record encode-decode, long, arrays, field inference
  - List rich API — contains, isEmpty, remove, clear, listOf (JVM + Native parity)
  - native string API parity — indexOf, trim, toUpperCase/toLowerCase, replace, equalsIgnoreCase, split
  - enhance parsing and execution for generic calls and string operations in JVM backend
  - enhance JSON encoding/decoding with improved parameter handling and type inference
  - add JSON support with encoding and decoding functions
  - List<T> builtin collection (native + JVM)
  - implement Kof list operations in JVM backend and native runtime
  - add Kof List type support and associated runtime functions
  - generics with erasure (classes, functions, type args)
  - add support for type parameters in symbol table
  - add support for type parameters in function and class declarations
  - strengthen compile-time type checking
  - constructors in native backend, skip implicit Object super() call
  - add break/continue, fix if/while/for control flow, comparison expressions
  - add .balign directive for method table alignment in NativeBackend and NativeRuntime
  - enhance Kof language type system with type IDs and instanceof support
  - implement switch statement and case handling in Kof language
  - enhance Kof language documentation with comprehensive references, examples, and common patterns
  - add support for do-while statements and enhance type system
  - Complete Phase F implementation with runtime, object model, exceptions, and memory management
  - Add logging for assembly generation and error handling in NativeBackend
  - Phase C+D+E - complete compiler with native backend

### Bugfixes

  - Windows — o zip do JDK não preserva o bit de execução; aceitar java.exe por existência (-f) no launcher e no validate
  - launcher e validate usam o JDK embarcado em todas as plataformas
  - CI multiplataforma + kof.db link seletivo + JS try/finally + package Adoptium
  - enhance try-finally parsing logic to correctly handle labels and control flow
  - enhance MySQL connection detection and linker command for conditional library inclusion
  - add --as-needed flag to linker command for improved dependency handling
  - update output handling in various E2E tests for consistent UTF-8 encoding and line endings
  - update file path handling for cross-platform compatibility and enhance test process encoding
  - golden tests need the CLI jar; launcher must not break JDK 21
  - update expected output for label style binding in JS target
  - guard kofUiButtonRemove against missing action registry
  - update ClassPrepare event kind and improve event logging in JdwpClient
  - sound IR optimizer, JS switch routing and list construction
  - field initializers, record defaults and increment semantics
  - idiomatic core — name resolution by symbol, return inference, this-free fields
  - bool semantics parity — 0/1 results, true/false formatting, Multi-Release shade
  - restore kof_io_ dispatch in JVM runtime helper
  - JVM constructor super detection, List<ref> checkcast, kof.List descriptor
  - centralize primitive names, reject lambdas with a clear diagnostic
  - native JSON long parity + array element stride
  - JVM backend execution parity — if/else, strings, generics erasure boxing, records, interfaces, access flags, bitwise ops, long arithmetic
  - switch case fall-through, SUB operand order, function call typing
  - resolve native SIGSEGV and complete string/object ABI

### Documentation

  - DATABASE_VISION — nível 0 do kof.db implementado (JDBC idiomático JVM, SQLite nativo, MySQL WIP); níveis 1-4 seguem a visão
  - README e status finais (513/513, kof.db, JSON completo)
  - document the intent-oriented paradigm with honest framing
  - update local build instructions with lib/kof.jar workaround
  - document the kof.ui platform (widgets, events, webview)
  - auditoria do ecossistema da stdlib — matriz de cobertura (G1-G12)
  - debugger — Fases 1-3 implementadas (kof-debug MVP validado)
  - status — debugger Fases 1-2 (DebugInfo na IR, JVM metadata)
  - status — 394 testes, guidelines idiomáticas e estado real
  - fake-idioms — primary constructor is implemented (record-style since 0.0.5)
  - sync all .md with real 0.0.5 state
  - reorganize — move completed docs out of future/
  - status — 375/375, KofJS 100% (GraalJS embutido)
  - status — kof.io filesystem API, kof test, current test state
  - status — Fases H/J/K/L concluídas, I design pronto
  - Legacy Migration Platform architecture
  - align learning and training corpus with 0.0.4-alpha
  - distribution, packaging, versioning and state aligned with 0.0.4-alpha
  - atualizar status, architecture, actual-state, README

### Build

  - bump version to 0.0.11-alpha [skip ci]
  - bump version to 0.0.10-alpha [skip ci]
  - bump version to 0.0.9-alpha [skip ci]
  - bump version to 0.0.8-alpha [skip ci]
  - bump version to 0.0.7-alpha [skip ci]
  - rebuild kof-webview with file:// module CORS fix
  - bump version to 0.0.6-alpha [skip ci]
  - bump version to 0.0.5-alpha [skip ci]
  - centralized versioning, official launchers and packaging

### Tooling

  - official TextMate grammar and editor/LSP documentation

## [0.0.13-alpha] - 2026-08-24

### Features

  - implement MySQL authentication scramble using SHA-1
  - MySQL/MariaDB via wire protocol sobre sockets nativos (WIP)
  - add hidden easter egg registry and corresponding tests
  - native kof.db with SQLite via direct .so linking (no JDBC driver)
  - enhance string replacement functionality and content type handling in HTTP server
  - enhance string replace functionality and constructor handling in backends
  - JSON completo (Float/Double, arrays), logging estruturado, kof.db (JDBC + transactions)
  - enhance process output handling with virtual threads
  - enhance kof.ui documentation and add KofJS details
  - add kof.ui section to documentation with UI rendering details and widget descriptions
  - update documentation for UI components, add window and widget examples
  - multiple windows, window size and close-to-exit
  - add window size adjustment functionality in KofUi
  - add support for font size, bold, and color properties in KofUi labels
  - add label styling and window theme support in KofUi and related backends
  - Introduce new UI components and bindings for Input, Column, Row, View, and Style
  - enhance KofJsRunner to support program arguments and update related components
  - update documentation and fix issues in Kof Spring Starter phases, enhance runtime functions
  - implement native configuration and logging modules, update documentation
  - update documentation and enhance semantic analysis for config and logging namespaces
  - enhance KofJsRunner output handling and add webview settings for file access
  - implement native web stack with routing, middleware, and JSON support
  - enhance Kof compiler and runtime with new features and bug fixes
  - native webview shell — kof-webview (WebKitGTK embedded)
  - kof.ui webview — DOM shim, HTML serialization, system webview
  - enhance KofJsRunner to support window rendering and HTML capture
  - introduce kof.security module for password hashing, JWT, and cryptography
  - kof-debug MVP completo — breakpoints por linha Kof + stack trace
  - kof.ui Window and Label — webview container with binding
  - kof.ui foundation — Color, Palette, Theme + main(args)
  - add kof.ui foundation with Color, Palette, and Theme support
  - enhance benchmarking with JS target and add CPU time tracking
  - debugger Fase 2 — LocalVariableTable no JVM
  - Kof debugger — Fase 1 (DebugInfo na IR) + docs + JVM line metadata
  - add debug information support with source file and line number mapping in JVM backend
  - enhance IRModule and backend to support source name and debugging information
  - implement Kof debugging support with source mapping and debug metadata
  - add initializer support for record components and enhance semantic analysis
  - idiomatic core — field initializers applied, \uXXXX escapes, typed listOf<T>()
  - implement increment operations with correct semantics and add tests for idiomatic behavior
  - implement generics in Kof with examples for lists and sets
  - enhance method symbol to allow dynamic return type updates and improve semantic analysis
  - refactor semantic analysis by defining constructor and method symbols, and analyzing their bodies
  - add Color class with ARGB semantics and enhance color handling in the compiler
  - enhance literal parsing and add hexadecimal support in lexer
  - Fase L — release gate hardened + package revalidated
  - Fase K — assert primitive + expanded golden + kof test integration
  - implement assertion handling with AssertE2ETest and add various test cases for control flow, functions, and records
  - add AssertStmt for assertion handling and update lexer and token types
  - Fase J — LSP textDocument/didClose clears diagnostics
  - add KofJS backend and runtime support, including parity tests for JVM and JS
  - Enhance parsing and runtime capabilities with new if-expression handling and runtime options
  - kof test — run programs and report PASS/FAIL by exit code
  - Fase I — spawn: concurrent tasks on the JVM (virtual threads)
  - Introduce kof.io filesystem API for file and directory operations
  - kof.io documentation, multiplatform CI and platform guard
  - Fase J — LSP URI fix + editor grammar builtins
  - Fase I+L — concurrency semantics design + distribution validation
  - Fase K — real golden and integration test infrastructure
  - Enhance KofJS backend with improved function handling and module support
  - Implement Kof HTTP server and I/O library
  - idioms corpus, anti-pattern catalog, datasets, corrections
  - kof.time and kof.io stdlib primitives with JVM+Native parity
  - add support for string length and charAt methods in NativeBackend
  - implement standard library functions for time and I/O operations
  - add JvmJsonRuntime for JSON handling in JVM backend
  - native exception unwinding — real try/catch/finally on x86-64
  - lambda expressions and if-expressions with real lowering
  - real native memory management — allocator header, functional kof_free, live memstats
  - CLI platform commands — info, check, lsp, install
  - JVM backend correctness — records, Object methods, concat, comparisons
  - remove fun keyword — functions declared by name
  - JSON parity JVM+Native — object/record encode-decode, long, arrays, field inference
  - List rich API — contains, isEmpty, remove, clear, listOf (JVM + Native parity)
  - native string API parity — indexOf, trim, toUpperCase/toLowerCase, replace, equalsIgnoreCase, split
  - enhance parsing and execution for generic calls and string operations in JVM backend
  - enhance JSON encoding/decoding with improved parameter handling and type inference
  - add JSON support with encoding and decoding functions
  - List<T> builtin collection (native + JVM)
  - implement Kof list operations in JVM backend and native runtime
  - add Kof List type support and associated runtime functions
  - generics with erasure (classes, functions, type args)
  - add support for type parameters in symbol table
  - add support for type parameters in function and class declarations
  - strengthen compile-time type checking
  - constructors in native backend, skip implicit Object super() call
  - add break/continue, fix if/while/for control flow, comparison expressions
  - add .balign directive for method table alignment in NativeBackend and NativeRuntime
  - enhance Kof language type system with type IDs and instanceof support
  - implement switch statement and case handling in Kof language
  - enhance Kof language documentation with comprehensive references, examples, and common patterns
  - add support for do-while statements and enhance type system
  - Complete Phase F implementation with runtime, object model, exceptions, and memory management
  - Add logging for assembly generation and error handling in NativeBackend
  - Phase C+D+E - complete compiler with native backend

### Bugfixes

  - extração do zip do JDK (Windows) — mover o subdiretório jdk-* com verificação, sem engolir falha
  - verificação explícita do JDK embarcado após a extração (Windows)
  - Windows — o zip do JDK não preserva o bit de execução; aceitar java.exe por existência (-f) no launcher e no validate
  - launcher e validate usam o JDK embarcado em todas as plataformas
  - CI multiplataforma + kof.db link seletivo + JS try/finally + package Adoptium
  - enhance try-finally parsing logic to correctly handle labels and control flow
  - enhance MySQL connection detection and linker command for conditional library inclusion
  - add --as-needed flag to linker command for improved dependency handling
  - update output handling in various E2E tests for consistent UTF-8 encoding and line endings
  - update file path handling for cross-platform compatibility and enhance test process encoding
  - golden tests need the CLI jar; launcher must not break JDK 21
  - update expected output for label style binding in JS target
  - guard kofUiButtonRemove against missing action registry
  - update ClassPrepare event kind and improve event logging in JdwpClient
  - sound IR optimizer, JS switch routing and list construction
  - field initializers, record defaults and increment semantics
  - idiomatic core — name resolution by symbol, return inference, this-free fields
  - bool semantics parity — 0/1 results, true/false formatting, Multi-Release shade
  - restore kof_io_ dispatch in JVM runtime helper
  - JVM constructor super detection, List<ref> checkcast, kof.List descriptor
  - centralize primitive names, reject lambdas with a clear diagnostic
  - native JSON long parity + array element stride
  - JVM backend execution parity — if/else, strings, generics erasure boxing, records, interfaces, access flags, bitwise ops, long arithmetic
  - switch case fall-through, SUB operand order, function call typing
  - resolve native SIGSEGV and complete string/object ABI

### Documentation

  - DATABASE_VISION — nível 0 do kof.db implementado (JDBC idiomático JVM, SQLite nativo, MySQL WIP); níveis 1-4 seguem a visão
  - README e status finais (513/513, kof.db, JSON completo)
  - document the intent-oriented paradigm with honest framing
  - update local build instructions with lib/kof.jar workaround
  - document the kof.ui platform (widgets, events, webview)
  - auditoria do ecossistema da stdlib — matriz de cobertura (G1-G12)
  - debugger — Fases 1-3 implementadas (kof-debug MVP validado)
  - status — debugger Fases 1-2 (DebugInfo na IR, JVM metadata)
  - status — 394 testes, guidelines idiomáticas e estado real
  - fake-idioms — primary constructor is implemented (record-style since 0.0.5)
  - sync all .md with real 0.0.5 state
  - reorganize — move completed docs out of future/
  - status — 375/375, KofJS 100% (GraalJS embutido)
  - status — kof.io filesystem API, kof test, current test state
  - status — Fases H/J/K/L concluídas, I design pronto
  - Legacy Migration Platform architecture
  - align learning and training corpus with 0.0.4-alpha
  - distribution, packaging, versioning and state aligned with 0.0.4-alpha
  - atualizar status, architecture, actual-state, README

### Build

  - bump version to 0.0.12-alpha [skip ci]
  - bump version to 0.0.11-alpha [skip ci]
  - bump version to 0.0.10-alpha [skip ci]
  - bump version to 0.0.9-alpha [skip ci]
  - bump version to 0.0.8-alpha [skip ci]
  - bump version to 0.0.7-alpha [skip ci]
  - rebuild kof-webview with file:// module CORS fix
  - bump version to 0.0.6-alpha [skip ci]
  - bump version to 0.0.5-alpha [skip ci]
  - centralized versioning, official launchers and packaging

### Tooling

  - official TextMate grammar and editor/LSP documentation

## [0.0.14-alpha] - 2026-08-24

### Features

  - implement MySQL authentication scramble using SHA-1
  - MySQL/MariaDB via wire protocol sobre sockets nativos (WIP)
  - add hidden easter egg registry and corresponding tests
  - native kof.db with SQLite via direct .so linking (no JDBC driver)
  - enhance string replacement functionality and content type handling in HTTP server
  - enhance string replace functionality and constructor handling in backends
  - JSON completo (Float/Double, arrays), logging estruturado, kof.db (JDBC + transactions)
  - enhance process output handling with virtual threads
  - enhance kof.ui documentation and add KofJS details
  - add kof.ui section to documentation with UI rendering details and widget descriptions
  - update documentation for UI components, add window and widget examples
  - multiple windows, window size and close-to-exit
  - add window size adjustment functionality in KofUi
  - add support for font size, bold, and color properties in KofUi labels
  - add label styling and window theme support in KofUi and related backends
  - Introduce new UI components and bindings for Input, Column, Row, View, and Style
  - enhance KofJsRunner to support program arguments and update related components
  - update documentation and fix issues in Kof Spring Starter phases, enhance runtime functions
  - implement native configuration and logging modules, update documentation
  - update documentation and enhance semantic analysis for config and logging namespaces
  - enhance KofJsRunner output handling and add webview settings for file access
  - implement native web stack with routing, middleware, and JSON support
  - enhance Kof compiler and runtime with new features and bug fixes
  - native webview shell — kof-webview (WebKitGTK embedded)
  - kof.ui webview — DOM shim, HTML serialization, system webview
  - enhance KofJsRunner to support window rendering and HTML capture
  - introduce kof.security module for password hashing, JWT, and cryptography
  - kof-debug MVP completo — breakpoints por linha Kof + stack trace
  - kof.ui Window and Label — webview container with binding
  - kof.ui foundation — Color, Palette, Theme + main(args)
  - add kof.ui foundation with Color, Palette, and Theme support
  - enhance benchmarking with JS target and add CPU time tracking
  - debugger Fase 2 — LocalVariableTable no JVM
  - Kof debugger — Fase 1 (DebugInfo na IR) + docs + JVM line metadata
  - add debug information support with source file and line number mapping in JVM backend
  - enhance IRModule and backend to support source name and debugging information
  - implement Kof debugging support with source mapping and debug metadata
  - add initializer support for record components and enhance semantic analysis
  - idiomatic core — field initializers applied, \uXXXX escapes, typed listOf<T>()
  - implement increment operations with correct semantics and add tests for idiomatic behavior
  - implement generics in Kof with examples for lists and sets
  - enhance method symbol to allow dynamic return type updates and improve semantic analysis
  - refactor semantic analysis by defining constructor and method symbols, and analyzing their bodies
  - add Color class with ARGB semantics and enhance color handling in the compiler
  - enhance literal parsing and add hexadecimal support in lexer
  - Fase L — release gate hardened + package revalidated
  - Fase K — assert primitive + expanded golden + kof test integration
  - implement assertion handling with AssertE2ETest and add various test cases for control flow, functions, and records
  - add AssertStmt for assertion handling and update lexer and token types
  - Fase J — LSP textDocument/didClose clears diagnostics
  - add KofJS backend and runtime support, including parity tests for JVM and JS
  - Enhance parsing and runtime capabilities with new if-expression handling and runtime options
  - kof test — run programs and report PASS/FAIL by exit code
  - Fase I — spawn: concurrent tasks on the JVM (virtual threads)
  - Introduce kof.io filesystem API for file and directory operations
  - kof.io documentation, multiplatform CI and platform guard
  - Fase J — LSP URI fix + editor grammar builtins
  - Fase I+L — concurrency semantics design + distribution validation
  - Fase K — real golden and integration test infrastructure
  - Enhance KofJS backend with improved function handling and module support
  - Implement Kof HTTP server and I/O library
  - idioms corpus, anti-pattern catalog, datasets, corrections
  - kof.time and kof.io stdlib primitives with JVM+Native parity
  - add support for string length and charAt methods in NativeBackend
  - implement standard library functions for time and I/O operations
  - add JvmJsonRuntime for JSON handling in JVM backend
  - native exception unwinding — real try/catch/finally on x86-64
  - lambda expressions and if-expressions with real lowering
  - real native memory management — allocator header, functional kof_free, live memstats
  - CLI platform commands — info, check, lsp, install
  - JVM backend correctness — records, Object methods, concat, comparisons
  - remove fun keyword — functions declared by name
  - JSON parity JVM+Native — object/record encode-decode, long, arrays, field inference
  - List rich API — contains, isEmpty, remove, clear, listOf (JVM + Native parity)
  - native string API parity — indexOf, trim, toUpperCase/toLowerCase, replace, equalsIgnoreCase, split
  - enhance parsing and execution for generic calls and string operations in JVM backend
  - enhance JSON encoding/decoding with improved parameter handling and type inference
  - add JSON support with encoding and decoding functions
  - List<T> builtin collection (native + JVM)
  - implement Kof list operations in JVM backend and native runtime
  - add Kof List type support and associated runtime functions
  - generics with erasure (classes, functions, type args)
  - add support for type parameters in symbol table
  - add support for type parameters in function and class declarations
  - strengthen compile-time type checking
  - constructors in native backend, skip implicit Object super() call
  - add break/continue, fix if/while/for control flow, comparison expressions
  - add .balign directive for method table alignment in NativeBackend and NativeRuntime
  - enhance Kof language type system with type IDs and instanceof support
  - implement switch statement and case handling in Kof language
  - enhance Kof language documentation with comprehensive references, examples, and common patterns
  - add support for do-while statements and enhance type system
  - Complete Phase F implementation with runtime, object model, exceptions, and memory management
  - Add logging for assembly generation and error handling in NativeBackend
  - Phase C+D+E - complete compiler with native backend

### Bugfixes

  - Windows — converter paths MSYS para Windows antes do extractall do Python
  - extração do zip do JDK (Windows) — mover o subdiretório jdk-* com verificação, sem engolir falha
  - verificação explícita do JDK embarcado após a extração (Windows)
  - Windows — o zip do JDK não preserva o bit de execução; aceitar java.exe por existência (-f) no launcher e no validate
  - launcher e validate usam o JDK embarcado em todas as plataformas
  - CI multiplataforma + kof.db link seletivo + JS try/finally + package Adoptium
  - enhance try-finally parsing logic to correctly handle labels and control flow
  - enhance MySQL connection detection and linker command for conditional library inclusion
  - add --as-needed flag to linker command for improved dependency handling
  - update output handling in various E2E tests for consistent UTF-8 encoding and line endings
  - update file path handling for cross-platform compatibility and enhance test process encoding
  - golden tests need the CLI jar; launcher must not break JDK 21
  - update expected output for label style binding in JS target
  - guard kofUiButtonRemove against missing action registry
  - update ClassPrepare event kind and improve event logging in JdwpClient
  - sound IR optimizer, JS switch routing and list construction
  - field initializers, record defaults and increment semantics
  - idiomatic core — name resolution by symbol, return inference, this-free fields
  - bool semantics parity — 0/1 results, true/false formatting, Multi-Release shade
  - restore kof_io_ dispatch in JVM runtime helper
  - JVM constructor super detection, List<ref> checkcast, kof.List descriptor
  - centralize primitive names, reject lambdas with a clear diagnostic
  - native JSON long parity + array element stride
  - JVM backend execution parity — if/else, strings, generics erasure boxing, records, interfaces, access flags, bitwise ops, long arithmetic
  - switch case fall-through, SUB operand order, function call typing
  - resolve native SIGSEGV and complete string/object ABI

### Documentation

  - DATABASE_VISION — nível 0 do kof.db implementado (JDBC idiomático JVM, SQLite nativo, MySQL WIP); níveis 1-4 seguem a visão
  - README e status finais (513/513, kof.db, JSON completo)
  - document the intent-oriented paradigm with honest framing
  - update local build instructions with lib/kof.jar workaround
  - document the kof.ui platform (widgets, events, webview)
  - auditoria do ecossistema da stdlib — matriz de cobertura (G1-G12)
  - debugger — Fases 1-3 implementadas (kof-debug MVP validado)
  - status — debugger Fases 1-2 (DebugInfo na IR, JVM metadata)
  - status — 394 testes, guidelines idiomáticas e estado real
  - fake-idioms — primary constructor is implemented (record-style since 0.0.5)
  - sync all .md with real 0.0.5 state
  - reorganize — move completed docs out of future/
  - status — 375/375, KofJS 100% (GraalJS embutido)
  - status — kof.io filesystem API, kof test, current test state
  - status — Fases H/J/K/L concluídas, I design pronto
  - Legacy Migration Platform architecture
  - align learning and training corpus with 0.0.4-alpha
  - distribution, packaging, versioning and state aligned with 0.0.4-alpha
  - atualizar status, architecture, actual-state, README

### Build

  - bump version to 0.0.13-alpha [skip ci]
  - bump version to 0.0.12-alpha [skip ci]
  - bump version to 0.0.11-alpha [skip ci]
  - bump version to 0.0.10-alpha [skip ci]
  - bump version to 0.0.9-alpha [skip ci]
  - bump version to 0.0.8-alpha [skip ci]
  - bump version to 0.0.7-alpha [skip ci]
  - rebuild kof-webview with file:// module CORS fix
  - bump version to 0.0.6-alpha [skip ci]
  - bump version to 0.0.5-alpha [skip ci]
  - centralized versioning, official launchers and packaging

### Tooling

  - official TextMate grammar and editor/LSP documentation

## [0.1.1-alpha] - 2026-08-26

### Features

  - add fake SDK jar for AndroidInterop testing
  - release version 0.1.0
  - switch exaustivo sobre enum — SEM031 + comparação por conteúdo
  - Map/Set nativo em asm — fecha COL001
  - add support for spawn and await expressions with error handling
  - enum P1 — declaração, values/valueOf/name, == por conteúdo (3 targets)
  - implement TLS/HTTPS G12 — web.listenSecure + kof.http HTTPS
  - complete G9 Native + docs/test — rate limiting/sessions/API keys
  - implement rate limiting, session management, and API key handling
  - implement kof.observability G5 — health/metrics/request IDs on JVM/Native/JS
  - implement kof.validation functions and integrate with compiler
  - AES-GCM nativo em asm — fecha SECN002 (G10 completo)
  - JWT HS256 nativo em asm — fecha SECN004 (G10)
  - PBKDF2 + SHA-512 nativos em asm — fecha SECN001/SECN003 (G10 parcial)
  - add in-memory messaging system with publish/subscribe and queues
  - extend KofUnaryOp with D2F and update backends
  - add support for Link, Image, Icon, and Font UI components
  - add CI workflow for Android target with APK assembly
  - decode de arrays no Native — fecha o gap JSN003
  - Implement constructor overloading and add JvmConfigRuntime and JvmStringRuntime
  - kof.config no target Native — fecha o gap CONF001
  - kof.http client + kof.mq (messageria em memória) — G2/G3 fechados
  - enhance Android target support with embedded host Activity and external classpath resolution
  - add Android target support with project generation and configuration
  - add KofAndroid target with initial design and objectives
  - implement qualified type resolution and enhance inheritance support
  - kof.log no target Native — fecha o gap LOG001
  - kof.orm validado em bancos reais — MariaDB 11 e PostgreSQL 16 + fixes do WIP das annotations
  - add support for native target execution in Main class
  - update CLI documentation and add structured test example
  - kof.orm — count com filtro e deleteAll completam o CRUD
  - kof.orm completo — operadores no where, saveAll (batch) e page (paginação)
  - add test declaration support and compile-time test harness
  - implement string to numeric conversions and enhance MongoDB method handling
  - add MongoDB and SQLite support to kof.orm with new runtime methods
  - orm.where (query por campo) + orm.migrate (migrations versionadas)
  - kof.orm — o ORM da própria linguagem (entity + orm.*)

### Bugfixes

  - unbox pós-kof_await restrito ao await — UI voltou a verificar
  - enhance spawn expression handling for primitive return types
  - List/Map fora do ramo JSN002 (ld List_vtable) + spawn stmt JS CONC003
  - lambda não-void single-expr vira return + gaps CONC003 p/ spawn-expr/await no JS
  - add debug logging for MemoryLayer entries field type resolution
  - Map/Set boxing e construção — corrige VerifyError JVM e stack underflow JS
  - fwd-ref multi-file, Int[] negativos nativo, Frame.merge Map
  - SEM025 não reportar Object methods (hashCode/equals/toString) — corrige JvmE2ETest.execRecordValueMethods
  - N3 args vazio + N9 box String += com concat
  - exclude String/Int/Long/Bool from SEM025 — avoid false-positive for JDK methods (contains/split)
  - dedupe kof.validation block — single copy, fix Native ld duplicate symbols
  - enhance JSON value retrieval in emitJsonFindValue function
  - add string conversion functions and update NativeRuntime with new assembly generation
  - alinha serve/check/test com o modelo de módulo multi-arquivo
  - simplify JSON string handling in NativeRuntime and CompilerDriver
  - kof_sec_secret_get nativo reescrito — bug #13 resolvido
  - db.close quebrado pelo WIP do isLocalVarName + surefire -Xshare:off
  - update AndroidInteropE2ETest to use a temporary SDK JAR for external classpath
  - update Android project instructions to reflect Maven usage
  - update comments for clarity in NativeRuntime and modify AndroidProjectWriter to use Maven
  - FLT001 — operações de ponto flutuante viram diagnóstico em compile-time
  - feedback do kof-calculator-lab — calculator interativo destravado + bugs reais
  - remove debug logging for MongoDB method accessibility
  - enhance integer arithmetic checks and improve MongoDB query handling
  - feedback real do kof-calculator-lab (OBS-004 a OBS-010)

### Documentation

  - stdlib — await/join de spawn (P1), CONC003 no JS
  - stdlib P1 — Map/Set (JVM/JS, COL001 Native) e enum (3 targets)
  - sync 0.1.0-beta 25/08 — generics Box<T> + SEM025 Object fix + test counts
  - bugs #13/#14 resolvidos, plano P0 atualizado
  - JSN003 encerrado na documentacao
  - bug #13 (secret_get nativo) encerrado
  - CONFIG001 nativo concluido (8/8 testes E2E)
  - estado do CONFIG001 nativo (WIP ~90%) e contagem de testes
  - bugs 13-14 na lista (secret_get nativo segfault; FP sem SSE no Native) + progresso do plano
  - kof.log nativo na documentação (LOG001 só no JS)
  - kof.orm completo na documentação (saveAll, page, operadores no where, deleteAll, count filtrado, MariaDB/PostgreSQL)
  - package.sh no Windows — Git Bash + descoberta do Python (OBS-005/006)
  - kof.orm na tabela de features (status.md + README)

### Tests

  - update passwordsNative test to validate successful hash on Native target
  - prova de Turing-completude — Ackermann + loop de 1M nos 3 targets

## [0.1.2-beta] - 2026-08-26

### Features

  - LSP hover/completion · kof init/fmt/script · collect não-recursivo
  - spawn/await no JS (fecha CONC003) + kof script; versão 0.1.1-beta

## [0.1.3-beta] - 2026-08-26

### Features

  - poll/done + exceção limpa no await — itens 'alta' da fila

## [0.1.4-beta] - 2026-08-27

### Features

  - enhance native target support for RISC-V and ARM architectures
  - add native C subset compiler
  - JIT in-memory + top-level let + kof test isolado + LSP .ks (3 gaps restantes)
  - CLI kof script --target + repl + classpath jar + diagnostics
  - MVP KofScript direct execution (Fase 6)
  - switch case String s + instanceof + checkcast em JVM/Native/JS
  - cache in-process, WebSocket/SSE, scheduler every/at + pattern matching record destructuring + nullability String? + kof.time/config/mq para Native/JS
  - kof.time now/sleep, kof.config/mq for JS, fix native rbx clobber
  - implement higher-order functions for List — map, filter, reduce
  - cancel cooperativo + selectAny — itens 'média' da fila

### Bugfixes

  - imports file-specific + native free-list GC + docs 27/08

### Documentation

  - move DATABASE_VISION e KOF_VS_SPRING de future para docs; future fica só com planejados + kof-native risc/arm

## [0.1.5-beta] - 2026-08-27

### Features

  - automatic GC on alloc + kof_gc_collect coalesce

### Bugfixes

  - lib/kof.jar inside tar.gz + always upload artifacts

## [0.1.6-beta] - 2026-08-27

### Features

  - automatic GC on alloc + kof_gc_collect coalesce

### Bugfixes

  - windows SIGPIPE 141 head pipefail
  - lib/kof.jar inside tar.gz + always upload artifacts

### Build

  - bump version to 0.1.5-beta [skip ci]

## [0.1.7-beta] - 2026-08-27

### Bugfixes

  - duplicate if-no-files-found + pipefail head
  - windows pipefail + lib/kof.jar check + jar upload

## [0.1.8-beta] - 2026-08-27

### Bugfixes

  - remove duplicate if-no-files-found
  - ensure kof-cli jars + lib/kof.jar in dist for 0.1.7

## [0.1.9-beta] - 2026-08-27

### Bugfixes

  - build kof-cli+dist in same job as release (no artifact loss)
  - ensure kof-cli jar in same-step as release (artifact fallback)
  - remove duplicate if-no-files-found again
  - re-add kof-cli jars to upload for 0.1.8

## [0.1.10-beta] - 2026-08-27

### Bugfixes

  - package+release uma coisa só + JDK 21 no release job
  - build kof-cli+dist in same job as release (no artifact loss)
  - ensure kof-cli jar in same-step as release (artifact fallback)
  - remove duplicate if-no-files-found again
  - re-add kof-cli jars to upload for 0.1.8

### Build

  - bump version to 0.1.9-beta [skip ci]

## [0.1.11-beta] - 2026-08-27

### Bugfixes

  - package+release uma coisa só (single job, no artifact loss)

## [0.2.1-beta] - 2026-08-28

### Features

  - GC mark-sweep + MySQL handshake + RISC-V placeholder (code it all)

### Documentation

  - update all md to 0.2.0-beta 27 Aug 2026 (658 tests, KofC, KofScript, kof.http JS, imports fix)
  - update all md to 0.2.0-beta 27 Aug 2026 (658 tests, KofC, KofScript, kof.http JS, imports fix)

### Build

  - bump version to 0.2.0-beta [skip ci]

## [0.2.2-beta] - 2026-08-28

### Bugfixes

  - shell bash for changelog + remove duplicate release-artifacts step (windows pwsh fix)

## [0.2.3-beta] - 2026-08-29

### Features

  - resposta rica status/header + scheduler every/at (JVM+JS)

### Bugfixes

  - string concat null -> anull, if null, List and subclass parity (4 bugs)

## [0.2.4-beta] - 2026-08-30

### Features

  - F10 spawn com stdin vivo + fix(native): forward reference

### Bugfixes

  - PROC001 explícito para process.spawn no Native
  - marcação transitiva + sweep no-op conservador + cdq em idivl Int — fecha N22 e SIGFPE do scheduler
  - String API no SemanticAnalyzer/inferExprType + lastIndexOf + null-compare no KofBinary
  - aritmetica Int trunca 32 bits — fecha N21/N10-family
  - comparisonOperandType refinado — null-literal decide ref; Unknown volta a ser int
  - R2 mapOf(k,v) pares + R3 null-safety narrowing + R4 Box<T> genérico na JVM

## [0.2.5-beta] - 2026-08-30

### Features

  - kof fmt via parser real (KofFormatter) + idempotente

### Bugfixes

  - kof.cache ttl/get clobber de registradores + println(null) segfault; feat: KofCacheE2ETest (5 casos x3 targets)
  - String.length via toString type inference + remove auto-GC hang (alloc path)

### Documentation

  - status 0.2.3-beta + kof.cache fechado (fix nativo + E2E)

## [0.2.6-beta] - 2026-08-30

### Features

  - RFC 6455 WebSocket frame codec
  - RFC 6455 WebSocket handshake
  - JVM native SSE + trailing-block sugar
  - persistent connection + route kinds + diagnostics (WEB003/WEB004)

## Versionamento

O Kof usa `MAJOR.MINOR.PATCH` (ver [docs/distribution/VERSIONING.md](docs/distribution/VERSIONING.md)).

- `0.0.x-alpha` — estágio inicial (Alpha), cada commit na `main` gera a próxima versão.
- O `PATCH` é o *pontinho da vergonha*: bugfixes, correções, regressões e pequenos ajustes.
- Nada é chamado de stable enquanto estiver em Alpha.

## [0.0.4-alpha] - 2026-08-22

### Infraestrutura de distribuição

- Versionamento centralizado: `VERSION` como fonte única, `<revision>` no Maven,
  `kof/version.properties` empacotado, `scripts/bump-version.sh`.
- `kof info` — relatório do ambiente (versão, Tooling API, target, JVM, install).
- `kof check` — type-check sem emissão de código.
- `kof lsp` — Language Server sobre stdio consumindo o frontend real do compilador.
- Launcher `bin/kof` (Unix) e `bin/kof.bat` (Windows) com suporte a JDK embutido.
- `scripts/package.sh` — pacote oficial (`kof-<versão>-<os>-<arch>` + SHA256SUMS),
  com JDK embutido opcional (`--jdk`, Temurin 21).
- GitHub Actions: `ci.yml` (PR) e `release.yml` (push na `main` → teste, bump,
  empacotamento multiplataforma, changelog e GitHub Release).
- Suporte a editores: grammar TextMate oficial em `editor/kof.tmLanguage.json`
  e documentação de consumo em `docs/tooling/`.

### Features

- JSON parity JVM + Native — encode/decode de objetos e records (JVM),
  `long`, arrays e inferência de campos.
- List rich API — `contains`, `isEmpty`, `remove`, `clear`, `listOf` (JVM + Native parity).
- Native string API parity — `indexOf`, `trim`, `toUpperCase`/`toLowerCase`,
  `replace`, `equalsIgnoreCase`, `split`.
- Backend JVM: generics com erasure, boxing, records, interfaces, bitwise,
  aritmética de `long`.

### Tooling

- Build Maven estável sob JDK 25 (reuso de compilador desabilitado no reactor).

## [0.0.3] - 2026-08-21

Estado anterior do projeto — veja `git log` e `docs/status.md` para o histórico completo.

## Formato da convenção de commits

```text
feat:      nova capacidade
fix:       correção de bug
docs:      documentação
refactor:  mudança interna sem mudança de comportamento
test:      testes
build:     build/CI/empacotamento
tooling:   ferramentas e editor support
```

A pipeline gera a seção do changelog a partir desses prefixos
(`scripts/changelog.sh`).

