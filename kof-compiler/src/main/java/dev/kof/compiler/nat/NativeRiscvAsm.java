package dev.kof.compiler.nat;

/**
 * FASE 3 (REFACTOR-500): runtime assembly riscv64 do NativeBackend.
 * Os 4 blocos originais (RISCV_RUNTIME_ASM/_STRN002_/_B/_MAPSET_) eram
 * ~4700 linhas numa só classe; aqui são fatiados em NativeRiscvAsm*
 * (≤500) e remontados por concatenação — valor byte-idêntico ao original
 * (prova: diff do .s gerado nos 3 targets).
 */
public final class NativeRiscvAsm {

    private NativeRiscvAsm() {}

    // String concatenação literal = variável-constante (JLS 15.28): o javac
    // DOBRA o valor no constant pool de quem referencia. Editar só
    // NativeRiscvAsmRt0 (ex.: G-0 do GC cross) e recompilar incremental deixa
    // o valor antigo embutido em classes-consumidoras não-recompiladas (test
    // de slice via getstatic = bytes velhos; RiscvSlices via reflection =
    // bytes novos) → split-brain FALSO. StringBuilder = mesmo bytes,
    // resolvido no <clinit> a cada JVM (mesma razão do runtimeB() abaixo).
    static final String RISCV_RUNTIME_ASM = runtimeRt();
    private static String runtimeRt() {
        return new StringBuilder()
                .append(NativeRiscvAsmRt0.RISCV_RUNTIME_ASM_0)
                .append(NativeRiscvAsmRt1.RISCV_RUNTIME_ASM_1)
                .toString();
    }
    static final String RISCV_STRN002_ASM = runtimeStrn();
    private static String runtimeStrn() {
        return new StringBuilder()
                .append(NativeRiscvAsmStrn0.RISCV_STRN002_ASM_0)
                .append(NativeRiscvAsmStrn1.RISCV_STRN002_ASM_1)
                .toString();
    }
    // A cadeia B_0..B_9 ultrapassa o limite de 64KB de string-constante do pool
    // quando dobrada em compile-time (javac "constant string too long" no uso).
    // Concatenar via StringBuilder = mesmo bytes, calculado no <clinit>.
    static final String RISCV_RUNTIME_ASM_B = runtimeB();
    private static String runtimeB() {
        return new StringBuilder()
                .append(NativeRiscvAsmRtB0.RISCV_RUNTIME_ASM_B_0)
                .append(NativeRiscvAsmRtB1.RISCV_RUNTIME_ASM_B_1)
                // §272 face (c): observability spans/IDs reais (era stub constante no B1).
                .append(NativeRiscvAsmObs.RISCV_ASM_OBS)
                .append(NativeRiscvAsmRtB2.RISCV_RUNTIME_ASM_B_2)
                .append(NativeRiscvAsmRtB3.RISCV_RUNTIME_ASM_B_3)
                .append(NativeRiscvAsmRtB4.RISCV_RUNTIME_ASM_B_4)
                .append(NativeRiscvAsmRtB5.RISCV_RUNTIME_ASM_B_5)
                .append(NativeRiscvAsmRtB6.RISCV_RUNTIME_ASM_B_6)
                .append(NativeRiscvAsmRtB7.RISCV_RUNTIME_ASM_B_7)
                .append(NativeRiscvAsmRtB8.RISCV_RUNTIME_ASM_B_8)
                .append(NativeRiscvAsmRtB9.RISCV_RUNTIME_ASM_B_9)
                .append(NativeRiscvAsmRtB10.RISCV_RUNTIME_ASM_B_10)
                .append(NativeRiscvAsmRtB11.RISCV_RUNTIME_ASM_B_11)
                .append(NativeRiscvAsmRtB12.RISCV_RUNTIME_ASM_B_12)
                .append(NativeRiscvAsmRtB13.RISCV_RUNTIME_ASM_B_13)
                .append(NativeRiscvAsmRtB14.RISCV_RUNTIME_ASM_B_14)
                .append(NativeRiscvAsmRtB15.RISCV_RUNTIME_ASM_B_15)
                .append(NativeRiscvAsmRtB16.RISCV_RUNTIME_ASM_B_16)
                .append(NativeRiscvAsmRtB17.RISCV_RUNTIME_ASM_B_17)
                .append(NativeRiscvAsmRtB18.RISCV_RUNTIME_ASM_B_18)
                .append(NativeRiscvAsmRtB19.RISCV_RUNTIME_ASM_B_19)
                .append(NativeRiscvAsmRtB20.RISCV_RUNTIME_ASM_B_20)
                .append(NativeRiscvAsmRtB21.RISCV_RUNTIME_ASM_B_21)
                .append(NativeRiscvAsmRtB22.RISCV_RUNTIME_ASM_B_22)
                .append(NativeRiscvAsmRtB23.RISCV_RUNTIME_ASM_B_23)
                .append(NativeRiscvAsmRtB24.RISCV_RUNTIME_ASM_B_24)
                .append(NativeRiscvAsmRtB25.RISCV_RUNTIME_ASM_B_25)
                .append(NativeRiscvAsmRtB25b.RISCV_RUNTIME_ASM_B_25B)
                .append(NativeRiscvAsmRtB26.RISCV_RUNTIME_ASM_B_26)
                .append(NativeRiscvAsmRtB27.RISCV_RUNTIME_ASM_B_27)
                .append(NativeRiscvAsmRtB28.RISCV_RUNTIME_ASM_B_28)
                .append(NativeRiscvAsmRtB29.RISCV_RUNTIME_ASM_B_29)
                .append(NativeRiscvAsmRtB30.RISCV_RUNTIME_ASM_B_30)
                .append(NativeRiscvAsmRtB31.RISCV_RUNTIME_ASM_B_31)
                .append(NativeRiscvAsmRtB32.RISCV_RUNTIME_ASM_B_32)
                .append(NativeRiscvAsmRtB33.RISCV_RUNTIME_ASM_B_33)
                .append(NativeRiscvAsmRtB34.RISCV_RUNTIME_ASM_B_34)
                .append(NativeRiscvAsmRtB35.RISCV_RUNTIME_ASM_B_35)
                .append(NativeRiscvAsmRtB36.RISCV_RUNTIME_ASM_B_36)
                .append(NativeRiscvAsmRtB37.RISCV_RUNTIME_ASM_B_37)
                .append(NativeRiscvAsmRtB38.RISCV_RUNTIME_ASM_B_38)
                .append(NativeRiscvAsmRtB39.RISCV_RUNTIME_ASM_B_39)
                .append(NativeRiscvAsmRtB40.RISCV_RUNTIME_ASM_B_40)
                // S13b (plan-stdlib-expansion): parse com default (§43) —
                // wrapper c/ handler no exc_chain; B34–B39 = outras lanes.
                .append(NativeRiscvAsmRtB41.RISCV_RUNTIME_ASM_B_41)
                // G-1 (NATIVE002 face 1, 15/09): kof_alloc (free-list) +
                // kof_free + kof_memstats — memória riscv64.
                .append(NativeRiscvAsmRtB42.RISCV_RUNTIME_ASM_B_42)
                // G-3 (NATIVE002 face 1, 15/09): mark conservador riscv64
                // (kof_gc_try_mark/mark_transitive/mark) sobre a gc-list do G-2.
                .append(NativeRiscvAsmRtB43.RISCV_RUNTIME_ASM_B_43)
                // G-4 (NATIVE002 face 1, 15/09): sweep + collect riscv64
                // (kof_gc_sweep/collect_now/collect/tick). O kof_alloc do B42
                // chama kof_gc_collect (forward ref resolvido pelo `as`).
                .append(NativeRiscvAsmRtB44.RISCV_RUNTIME_ASM_B_44)
                // FLT001 (NATIVE002, 15/09): Double/Float -> String via libc
                // (snprintf/strtod) — link dinâmico sob demanda. Fecha o gap
                // FLT001 no riscv64/aarch64.
                .append(NativeRiscvAsmRtB45.RISCV_RUNTIME_ASM_B_45)
                // DB001 (15/09): builder JSON + strlen — port RuntimeIo1/RuntimeJsonBuilder.
                .append(NativeRiscvAsmRtB46.RISCV_RUNTIME_ASM_B_46)
                // DB001 (15/09): runtime kof.db SQLite — port Db2/Db4/Db5 (gerado).
                .append(NativeRiscvAsmRtB47.RISCV_RUNTIME_ASM_B_47)
                // S5.4 fatia 2 (24/09): dispatch execute/query do cross —
                // kof_db_execute[N]/query[N] com o ramo mysql (B71 + B72/B70)
                // ao lado do sqlite; extraído da B47 p/ não furar o ≤500.
                .append(NativeRiscvAsmRtB47b.RISCV_RUNTIME_ASM_B_47B)
                // CONC001 (15/09): helpers de concorrência de alta ordem —
                // done/poll/cancel/cancelled/selectAny/awaitTimeout (port
                // RuntimeConcurrency; cancel por TID real via gettid+clone ctid).
                .append(NativeRiscvAsmRtB48.RISCV_RUNTIME_ASM_B_48)
                // §284 (18/09): box de erasure — kof_box_*/unbox/box_to_string
                // (port RuntimeErasureBox x86; aarch64 herda via tradutor).
                .append(NativeRiscvAsmRtB49.RISCV_RUNTIME_ASM_B_49)
                // DB-3/DB-1 cross slice A (22/09): ORM F1a no riscv64 —
                // kof_orm_delete_all + kof_orm_count (port RuntimeOrm1) sobre
                // o kof.db SQLite de RtB46/RtB47; aarch64 herda via tradutor.
                .append(NativeRiscvAsmRtB50.RISCV_RUNTIME_ASM_B_50)
                // DB-3/DB-1 cross slice B (22/09): ORM F1d no riscv64 —
                // kof_orm_create (parser do schema + DDL + sqlite3_exec, port
                // RuntimeOrm2) sobre o SQLite de RtB46/RtB47.
                .append(NativeRiscvAsmRtB51.RISCV_RUNTIME_ASM_B_51)
                // DB-3/DB-1 cross slice C (22/09): ORM F1c no riscv64 —
                // kof_orm_migrate (kof_migrations + idempotência + INSERT
                // bindado, port RuntimeOrm1) sobre o SQLite de RtB46/RtB47.
                .append(NativeRiscvAsmRtB52.RISCV_RUNTIME_ASM_B_52)
                // DB-3/DB-1 cross slice D (22/09): ORM F3a no riscv64 —
                // kof_orm_count_where (bind único via box §284/KofString/null
                // + SQL injection-proof, port RuntimeOrm3) sobre o SQLite.
                .append(NativeRiscvAsmRtB53.RISCV_RUNTIME_ASM_B_53)
                // DB-3/DB-1 cross slice E (22/09): ORM F2c3 no riscv64 —
                // kof_orm_delete (F2c3) + o parser de schema (port de
                // RuntimeOrmSchema, pedra-chave das faces row-object) sobre o
                // SQLite de RtB46/RtB47; aarch64 herda via tradutor.
                .append(NativeRiscvAsmRtB54.RISCV_RUNTIME_ASM_B_54)
                // DB-3/DB-1 cross slice E-parte-2a (22/09): ORM F2a no
                // riscv64 — helpers globais (conexão/builder/bind) + kof_orm_save
                // (insert/update/upsert + generated key + nova instância)
                // sobre o mesmo SQLite; reusa o parser global
                // kof_orm_parse_schema de RtB54; aarch64 via tradutor.
                .append(NativeRiscvAsmRtB55Helpers.RISCV_RUNTIME_ASM_B_55H)
                .append(NativeRiscvAsmRtB55.RISCV_RUNTIME_ASM_B_55)
                // DB-3/DB-1 cross slice E-parte-2b (23/09): saveAll (F2c3 de
                // RuntimeOrm10) — loop kof_list_size/kof_list_get sobre
                // kof_orm_save; reusa o frame/ABI das peças anteriores.
                .append(NativeRiscvAsmRtB56.RISCV_RUNTIME_ASM_B_56)
                // DB-3/DB-1 cross slice E-parte-3 (23/09): row-object de LEITURA
                // — helpers globais bind_key/read_field + kof_orm_find (F2b de
                // RuntimeOrm5). O resolver kof_orm_ctors é emitido por-programa
                // pelo NativeArchEmitter (NativeRiscvOrmCtors).
                .append(NativeRiscvAsmRtB57Helpers.RISCV_RUNTIME_ASM_B_57H)
                .append(NativeRiscvAsmRtB57.RISCV_RUNTIME_ASM_B_57)
                // DB-3/DB-1 cross slice E-parte-4 (23/09): kof_orm_all (F2c1
                // de RuntimeOrm6) — SELECT * acumulado em kof_list_new/add,
                // reusa read_field/ctors/parse_schema.
                .append(NativeRiscvAsmRtB58.RISCV_RUNTIME_ASM_B_58)
                // DB-3/DB-1 cross slice E-parte-5 (23/09): kof_orm_where /
                // kof_orm_where_op (F2c2 de RuntimeOrm7) — whitelist do op +
                // bind do value pelo classificador do host + loop do RtB58.
                .append(NativeRiscvAsmRtB59.RISCV_RUNTIME_ASM_B_59)
                // DB-3/DB-1 cross slice E-parte-6 (23/09): kof_orm_page (F2c3
                // de RuntimeOrm8) — fecha o row-object de leitura no cross.
                .append(NativeRiscvAsmRtB60.RISCV_RUNTIME_ASM_B_60)
                // §423 (TIER 13.4, 23/09): canais no cross — kof_channel_new/
                // send/receive (port RuntimeChannel x86) sobre kof_alloc/free
                // + futex (kof_plat_sync); aarch64 herda via tradutor.
                .append(NativeRiscvAsmRtB61.RISCV_RUNTIME_ASM_B_61)
                // S5.1 db-parity (gaps-db lane, 23/09): SHA1 curto + bswap —
                // primitivas do auth mysql_native_password no cross (port de
                // RuntimeDb1); prova por harness asm (kof.security recusa no
                // cross por SECN000, entao nao ha superficie Kof).
                .append(NativeRiscvAsmRtB62.RISCV_RUNTIME_ASM_B_62)
                // S5.1 db-parity (gaps-db lane, 23/09): auth do wire MySQL —
                // kof_db_mysql_scramble (mysql_native_password) + lenenc, sobre
                // o SHA1 da B62 (port de RuntimeDb1); aarch64 herda via tradutor.
                .append(NativeRiscvAsmRtB63.RISCV_RUNTIME_ASM_B_63)
                // S5.1 db-parity (gaps-db lane, 23/09): parse do greeting do
                // servidor MySQL — extrai os 20 bytes do seed (port de
                // RuntimeDb3); aarch64 herda via tradutor.
                .append(NativeRiscvAsmRtB64.RISCV_RUNTIME_ASM_B_64)
                // S5.1 db-parity (gaps-db lane, 23/09): monta o pacote de auth
                // (handshake response) — capabilities/user/scramble/db/plugin
                // (port de RuntimeDb3); aarch64 herda via tradutor.
                .append(NativeRiscvAsmRtB65.RISCV_RUNTIME_ASM_B_65)
                // S5.1 db-parity (gaps-db lane, 23/09): fecha o handshake/auth —
                // le greeting, scramble, monta/envia a resposta e le o OK (port
                // do fluxo de RuntimeDb3 sobre as pecas B62..B65 + HAL de socket).
                .append(NativeRiscvAsmRtB66.RISCV_RUNTIME_ASM_B_66)
                // S5.2 db-parity (gaps-db lane, 23/09): COM_QUERY texto —
                // framing do request + leitura do 1o pacote de resposta para
                // classificacao (port do framing de RuntimeDb2/Db3); aarch64
                // herda via tradutor.
                .append(NativeRiscvAsmRtB67.RISCV_RUNTIME_ASM_B_67)
                // S5.2 db-parity (gaps-db lane, 23/09): reader de pacotes MySQL
                // (reset/next) — base do parse do resultset (port de RuntimeDb2).
                .append(NativeRiscvAsmRtB68.RISCV_RUNTIME_ASM_B_68)
                // S5.2 db-parity (gaps-db lane, 23/09): cabecalho do resultset
                // texto — COM_QUERY + skip das colunas + 1a linha (ncols/row).
                .append(NativeRiscvAsmRtB69.RISCV_RUNTIME_ASM_B_69)
                // S5.2 db-parity (gaps-db lane, 23/09): query texto completa —
                // COM_QUERY + colunas + todas as linhas como registros JSON
                // numa List<KofString> (port de kof_db_query x86).
                .append(NativeRiscvAsmRtB70.RISCV_RUNTIME_ASM_B_70)
                // S5.3 db-parity (gaps-db lane, 24/09): bind client-side do
                // wire MySQL — kof_db_mysql_render (valor -> literal SQL) +
                // kof_db_mysql_replace_q (troca o 1o `?` pelo literal); o
                // fallback do COM_QUERY (port de RuntimeDb1/Db2 x86).
                .append(NativeRiscvAsmRtB71.RISCV_RUNTIME_ASM_B_71)
                // S5.3 db-parity (gaps-db lane, 24/09): execute COM_QUERY —
                // kof_db_mysql_execute (COM_QUERY via B67 + affected-rows do
                // OK-packet; port da cauda de execute de RuntimeDb4/Db5 x86).
                .append(NativeRiscvAsmRtB72.RISCV_RUNTIME_ASM_B_72)
                // S5.4 db-parity (gaps-db lane, 24/09): connect real MySQL/
                // MariaDB no cross — URL parse + socket/connect (HAL) +
                // handshake B66 + registro do fd como type 2 (kof_db_connect
                // da B47 delega aqui o que não é "sqlite:").
                .append(NativeRiscvAsmRtB73.RISCV_RUNTIME_ASM_B_73)
                // S5.5 db-parity (gaps-db lane, 24/09): scalar do wire mysql no
                // cross (1a linha/1a coluna como Long) — base do orm.count.
                .append(NativeRiscvAsmRtB74.RISCV_RUNTIME_ASM_B_74)
                // S5.5 fatia 3 (gaps-db lane, 24/09): escrita do ORM no wire
                // mysql cross — literal compartilhado (kof_orm_mysql_lit) e
                // delete/deleteAll (exec genérico B72, sem throw, como o x86).
                .append(NativeRiscvAsmRtB75.RISCV_RUNTIME_ASM_B_75)
                // S5.5 fatia 4a (gaps-db lane, 24/09): exec que LANCA no erro
                // do servidor — pre-requisito do orm.save (port do x86
                // RuntimeOrmMysqlExec/.Lorm_sa_exec).
                .append(NativeRiscvAsmRtB76.RISCV_RUNTIME_ASM_B_76)
                // S5.5 fatia 4b (gaps-db lane, 24/09): orm.save no wire mysql
                // cross — 3 saidas do host (INSERT gerado + LAST_INSERT_ID +
                // nova instancia; UPDATE hit; UPDATE miss -> upsert), dialeto
                // backtick e literais por typeCode (port de RuntimeOrmMysqlSave).
                .append(NativeRiscvAsmRtB77.RISCV_RUNTIME_ASM_B_77)
                // S5.5 fatia 5 (gaps-db lane, 24/09): orm.find sobre o wire
                // mysql cross — SELECT por pk com o key como literal, rows
                // casadas por NOME e lidas por typeCode (port de
                // RuntimeOrmMysqlFind).
                .append(NativeRiscvAsmRtB78.RISCV_RUNTIME_ASM_B_78)
                // Helpers de celula (atoi/bool) do orm row-object mysql,
                // extraidos da B78 pelo gate 500.
                .append(NativeRiscvAsmRtB78Helpers.RISCV_RUNTIME_ASM_B_78H)
                // S5.5 fatia 5b: orm.all sobre o wire MySQL (dispatch na B58).
                .append(NativeRiscvAsmRtB79.RISCV_RUNTIME_ASM_B_79)
                // S5.5 fatia 5c: orm.where/where_op sobre o wire MySQL (dispatch na B59).
                .append(NativeRiscvAsmRtB80.RISCV_RUNTIME_ASM_B_80)
                .append(NativeRiscvAsmRtB80Helpers.RISCV_RUNTIME_ASM_B_80H)
                // S5.5 fatia 5d: orm.page sobre o wire MySQL (dispatch na B60).
                .append(NativeRiscvAsmRtB81.RISCV_RUNTIME_ASM_B_81)
                .append(NativeRiscvAsmRtB81Helpers.RISCV_RUNTIME_ASM_B_81H)
                // D-FULL-PARITY-050 row 11 (native cross lane, 24/09):
                // String.toCharArray() no cross — Char[] (code units UTF-16,
                // paridade JVM/x86); fecha a face cross do STR003.
                .append(NativeRiscvAsmStrToCharArray.RISCV_RUNTIME_ASM_STR_TO_CHAR_ARRAY)
                // D-FULL-PARITY-050 row 13 (native cross lane, 24/09): faces de
                // estat de kof.io no cross — exists()/isFile()/isDirectory().
                .append(NativeRiscvAsmIoStat.RISCV_RUNTIME_ASM_IO_STAT)
                // D-FULL-PARITY-050 row 13 slice 2 (native cross lane, 24/09):
                // readText()/writeText()/appendText() no cross.
                .append(NativeRiscvAsmIoText.RISCV_RUNTIME_ASM_IO_TEXT)
                // D-FULL-PARITY-050 row 13 slice 3 (native cross lane, 24/09):
                // delete()/mkdir()/create() no cross.
                .append(NativeRiscvAsmIoFs.RISCV_RUNTIME_ASM_IO_FS)
                // D-FULL-PARITY-050 row 13 slice 4 (native cross lane, 24/09):
                // createDirectories()/mkdirs() (mkdir -p) no cross.
                .append(NativeRiscvAsmIoMkdirs.RISCV_RUNTIME_ASM_IO_MKDIRS)
                // D-FULL-PARITY-050 row 13 slice 5 (native cross lane, 24/09):
                // size() (kof_io_file_size, lanca em miss como o x86).
                .append(NativeRiscvAsmIoSize.RISCV_RUNTIME_ASM_IO_SIZE)
                // D-FULL-PARITY-050 row 13 slice 6 (native cross lane, 24/09):
                // bytes (readBytes/writeBytes/appendBytes).
                .append(NativeRiscvAsmIoBytes.RISCV_RUNTIME_ASM_IO_BYTES)
                // D-FULL-PARITY-050 row 13 slice 7 (native cross lane, 24/09):
                // dir listing (Directory.list()).
                .append(NativeRiscvAsmIoDirList.RISCV_RUNTIME_ASM_IO_DIRLIST)
                // D-FULL-PARITY-050 row 13 slice 8 (native cross lane, 24/09):
                // readRange(offset, len).
                .append(NativeRiscvAsmIoReadRange.RISCV_RUNTIME_ASM_IO_READRANGE)
                // D-FULL-PARITY-050 row 13 slice 9 (native cross lane, 24/09):
                // pure path faces (name/fileName/parent/extension/isAbsolute).
                .append(NativeRiscvAsmIoPath.RISCV_RUNTIME_ASM_IO_PATH)
                // D-FULL-PARITY-050 row 13 slice 10 (native cross lane, 24/09):
                // kof_io_path_resolve (string concat, sem syscall).
                .append(NativeRiscvAsmIoResolve.RISCV_RUNTIME_ASM_IO_RESOLVE)
                // D-FULL-PARITY-050 row 13 slice 11 (native cross lane, 24/09):
                // kof_io_path_normalize (colapsa . / .. / // ; barra final; raiz).
                .append(NativeRiscvAsmIoNormalize.RISCV_RUNTIME_ASM_IO_NORMALIZE)
                // D-FULL-PARITY-050 row 13 slice 12 (native cross lane, 24/09):
                // kof_io_path_to_absolute (getcwd + resolve).
                .append(NativeRiscvAsmIoToAbsolute.RISCV_RUNTIME_ASM_IO_TOABSOLUTE)
                // D-FULL-PARITY-050 row 13 slice 13 (native cross lane, 26/09):
                // kof_io_dir_delete recursivo (getdents64 + unlinkat).
                .append(NativeRiscvAsmIoDirDelete.RISCV_RUNTIME_ASM_IO_DIRDELETE)
                // D-FULL-PARITY-050 row 13 slice 15 (native cross lane, 26/09):
                // metadata (modifiedTime + isSymlink).
                .append(NativeRiscvAsmIoMeta.RISCV_RUNTIME_ASM_IO_META)
                .append(NativeRiscvAsmIoMove.RISCV_RUNTIME_ASM_IO_MOVE)
                .append(NativeRiscvAsmIoCopy.RISCV_RUNTIME_ASM_IO_COPY)
                .append(NativeRiscvAsmProcess.RISCV_RUNTIME_ASM_PROCESS)
                .append(NativeRiscvAsmShell.RISCV_RUNTIME_ASM_SHELL)
                .append(NativeRiscvAsmPipeline.RISCV_RUNTIME_ASM_PIPELINE)
                .append(NativeRiscvAsmSsh.RISCV_RUNTIME_ASM_SSH)
                // D-FULL-PARITY-050 row 1 slice D (native-cross lane, 26/09):
                // process.spawn + handle ops no cross — porta o
                // RuntimeProcessSpawn x86 (clone SIGCHLD, sem fork no riscv).
                .append(NativeRiscvAsmProcessSpawn.RISCV_RUNTIME_ASM_PROCESS_SPAWN)
                // D-FULL-PARITY-050 (row 9, lane parity, 26/09): kof.config real
                // no cross — environ (3a) + lookup por arquivo/env/perfil (3b) +
                // interpolação ${key} e wrappers tipados (3c). Substitui os
                // stubs de B0 (antes CONF001 em KofConfig).
                .append(NativeRiscvAsmConfig1.RISCV_RUNTIME_ASM_CONFIG_1)
                .append(NativeRiscvAsmConfig2.RISCV_RUNTIME_ASM_CONFIG_2)
                .append(NativeRiscvAsmConfig3.RISCV_RUNTIME_ASM_CONFIG_3)
                // D-FULL-PARITY-050 (row 9, lane parity, 26/09): kof.log extraído
                // de RtB0 (que estava ≥600 linhas) para NativeRiscvAsmLog.
                .append(NativeRiscvAsmLog.RISCV_RUNTIME_ASM_LOG)
                // D-FULL-PARITY-050 (row 6, lane parity, 26/09): kof.gpu no cross
                // — fallback honesto (available=false, dispatch=-1/-6), mesmo
                // contrato do JvmVkStubRuntime/x86. Antes não linkava.
                .append(NativeRiscvAsmGpu.RISCV_RUNTIME_ASM_GPU)
                // D-FULL-PARITY-050 linha 4 FATIA 2A (26/09): Video do
                // kof.media no cross — port de RuntimeMedia+RuntimeMediaMp4
                // (aarch64 herda via tradutor); Audio/Image/Mic seguem
                // MEDIA001 declarado ate as fatias seguintes.
                .append(NativeRiscvAsmMedia.RISCV_ASM_MEDIA)
                .append(NativeRiscvAsmMediaMp4.RISCV_ASM_MEDIA_MP4)
                .toString();
    }
    static final String RISCV_MAPSET_ASM = runtimeMapset();
    private static String runtimeMapset() {
        return new StringBuilder()
                .append(NativeRiscvAsmMapset0.RISCV_MAPSET_ASM_0)
                .append(NativeRiscvAsmMapset1.RISCV_MAPSET_ASM_1)
                .append(NativeRiscvAsmMapset2.RISCV_MAPSET_ASM_2)
                .append(NativeRiscvAsmLookups0.RISCV_LOOKUPS_ASM_0)
                .toString();
    }
}
