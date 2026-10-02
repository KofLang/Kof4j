package dev.kof.compiler;

import java.util.List;

/**
 * Compile-time dispatch table for the Kof ORM ({@code orm}).
 *
 * <p>The compiler knows every entity schema in compile-time (fields, types,
 * constraints declared with {@code entity}) — no reflection to discover the
 * schema, never annotations. {@code orm} lowers to {@code kof_orm_*} runtime
 * functions that speak SQL via {@code kof.db}.
 *
 * <pre>{@code
 * entity User {
 *     id: Long generated
 *     name: String
 *     email: String unique
 *     age: Int
 * }
 *
 * var db = db.connect("jdbc:h2:mem:test")
 * orm.create<User>(db)                 // DDL a partir do schema
 * orm.save(db, User(0, "Mel", "m@kof.dev", 30))
 * var u = orm.find<User>(db, 1)
 * var all = orm.all<User>(db)
 * orm.delete<User>(db, 1)
 * }</pre>
 *
 * <p>JVM: JDBC (via kof.db). Native x86-64: asm {@code kof_orm_*} real
 * (F1/F2/F3). Cross riscv64/aarch64: slices A/B/C/D/E (22/09) — F1a
 * {@code delete_all}/{@code count} (peça RtB50), F1d {@code create}
 * (peça RtB51), F1c {@code migrate} (peça RtB52), F3a {@code count_where}
 * (peça RtB53) e F2c3 {@code delete} (peça RtB54, com o parser de schema
 * embutido) reais sobre o SQLite do cross; as demais faces reportam
 * {@code ORM001} em compile-time. JS: KofJsOrmBridge.
 */
public final class KofOrm {

    private KofOrm() {
    }

    private static final Type STR = BuiltinTypes.STRING;
    private static final Type INT = Type.PrimitiveType.INT;
    private static final Type BOOL = Type.PrimitiveType.BOOL;
    private static final Type OBJ = Type.UnknownType.UNKNOWN;

    static boolean isOrmNamespace(String name) {
        return "orm".equals(name);
    }

    /** P4 (D-PAGINATION-P4-LOWERING): o tipo `Window<T>` do host virtual
     *  `kof.pagination` (record injetado por-programa). Devolve {@code null}
     *  quando o host nao esta importado — `orm.window` entao nao resolve
     *  (exige `import kof.pagination`, nunca uma janela fantasma). */
    static Type windowType(SemanticAnalyzer sa, Type entityType) {
        SymbolTable.ClassSymbol win = sa == null ? null : sa.getClass("Window");
        if (win == null) return null;
        return new Type.ClassType(win.packageName(), "Window", List.of(entityType));
    }

    /** JVM/ANDROID: JDBC via kof.db (ANDROID fecha 20/09, D-DB-GAPS DB-2 —
     *  mesmo JvmBackend, paridade por construção). JS: KofJsOrmBridge (18/09,
     *  ORM001). Native x86-64: SQL-puro (F1) + row-object completo (F2,
     *  21/09) REAL; cross riscv/aarch64: fatias A-E da DB-3 (22/09) REALs
     *  sobre o SQLite de RtB46/RtB47 (peças RtB50-RtB54; aarch64 herda pelo
     *  tradutor), o resto em compile-time ORM001; MySQL (runtime) segue
     *  pending no backend via .Lorm_conn. */
    static boolean supportedOn(@SuppressWarnings("unused") Target target) {
        return target == Target.JVM || target == Target.ANDROID || target == Target.JS;
    }

    /** DB-3/DB-1 cross (22/09): fatias A-E do ORM no riscv64/aarch64 sobre
     *  os kof_db_* SQLite do cross — slice A: {@code delete_all} + {@code count}
     *  (peça RtB50, port de RuntimeOrm1); slice B: {@code create} (peça RtB51,
     *  port de RuntimeOrm2 — parser do schema + DDL + sqlite3_exec); slice C:
     *  {@code migrate} (peça RtB52, kof_migrations + idempotência); slice D:
     *  {@code count_where} (peça RtB53, bind via box §284; §447: o
     *  literal Bool boxeia kof_box_bool, nunca TEXTO); slice E:
     *  {@code delete} (peça RtB54, parser de schema exposto como o global
     *  {@code kof_orm_parse_schema} — pedra-chave das faces row-object);
     *  E-parte-2a: {@code save} (peça RtB55, F2a de {@code RuntimeOrm4}:
     *  insert/update/upsert + generated key + nova instância; reusa o parser
     *  global); E-parte-2b: {@code saveAll} (peça RtB56, F2c3 de
     *  {@code RuntimeOrm10}: loop {@code kof_list_get}→{@code kof_orm_save});
     *  E-parte-3: {@code find} (peça RtB57 + helpers bind_key/read_field,
     *  F2b de {@code RuntimeOrm5}; resolver {@code kof_orm_ctors} por-programa
     *  em {@code NativeRiscvOrmCtors}); E-parte-4: {@code all} (peça RtB58,
     *  F2c1 de {@code RuntimeOrm6}: SELECT * acumulado em
     *  {@code kof_list_new}/{@code kof_list_add}); E-parte-5: {@code where}/
     *  {@code where_op} (peça RtB59, F2c2 de {@code RuntimeOrm7}: whitelist do
     *  op + bind do value pelo classificador do host); E-parte-6: {@code page}
     *  (peça RtB60, F2c3 de {@code RuntimeOrm8}: LIMIT/OFFSET bindados) —
     *  row-object de leitura COMPLETO no cross; aarch64
     *  herda pelo tradutor. MySQL segue runtime honesto no cross (o
     *  {@code kof_orm_conn} recusa type==2); nenhuma face segue ORM001. */
    private static final java.util.Set<String> CROSS_FACES = java.util.Set.of(
            "kof_orm_delete_all", "kof_orm_count", "kof_orm_create",
            "kof_orm_migrate", "kof_orm_count_where", "kof_orm_delete",
            "kof_orm_save", "kof_orm_save_all", "kof_orm_find", "kof_orm_all",
            "kof_orm_where", "kof_orm_where_op", "kof_orm_page");

    /** D-DB-GAPS DB-1 (20/09): faces SQL-puro do Native x86-64, uma por fatia.
     *  F1a = {@code delete_all}, F1b = {@code count}, F1c = {@code migrate}
     *  (asm em {@code RuntimeOrm1}), F1d = {@code create} (parser de schema +
     *  DDL em {@code RuntimeOrm2}); F3a = {@code count_where} (bind unico via
     *  box de erasure §284, em {@code RuntimeOrm3}); F2a = {@code save}
     *  (row-object: INSERT/UPDATE/upsert em {@code RuntimeOrm4}, schema em
     *  {@code RuntimeOrmSchema}, binds em {@code RuntimeOrmBind}); F2b =
     *  {@code find} (leitura row-object em {@code RuntimeOrm5} + resolver
     *  {@code kof_orm_ctors} do backend); F2c1 = {@code all} (leitura em
     *  {@code List} no runtime — {@code RuntimeOrm6}, mesmo loop de campos
     *  do {@code RuntimeOrm5} com §397); F2c2 = {@code where}/{@code where_op}
     *  ({@code RuntimeOrm7}: whitelist do op idêntica ao host, 7º arg na
     *  stack, loop do Orm6); F2c3 = {@code page} ({@code RuntimeOrm8}: LIMIT/
     *  OFFSET bindados, KofString do coerce = atoi superset honesto),
     *  {@code delete} ({@code RuntimeOrm9}: PK do schema + bind do key, true
     *  em DONE como o {@code execute1 >= 0} do host — miss deleta 0 linhas e
     *  retorna true, medido) e {@code saveAll} ({@code RuntimeOrm10}: loop
     *  {@code kof_list_get} → {@code kof_orm_save}, instância patchada
     *  descartada como no host). O row-object x86-64 está FECHADO; o que
     *  segue {@code ORM001} honesto (R7, R6 — nunca silent): cross
     *  riscv/aarch64 (compile-time) e MySQL (runtime). */
    private static final java.util.Set<String> NATIVE_F1 = java.util.Set.of(
            "kof_orm_delete_all", "kof_orm_count", "kof_orm_migrate",
            "kof_orm_create", "kof_orm_count_where", "kof_orm_save",
            "kof_orm_find", "kof_orm_all",
            "kof_orm_where", "kof_orm_where_op",
            "kof_orm_page", "kof_orm_delete", "kof_orm_save_all");

    static boolean fnSupportedOn(Target target, String fn) {
        if (supportedOn(target)) return true;
        if (target == Target.NATIVE) return NATIVE_F1.contains(fn);
        if (target == Target.NATIVE_RISCV64 || target == Target.NATIVE_AARCH64) return CROSS_FACES.contains(fn);
        return false;
    }

    static String gapCode() {
        return "ORM001";
    }

    record OrmCall(String function, Type returnType, List<Type> parameterTypes,
                   String entityName, boolean typed) {
    }

/** X10 fatia 3: nomes aceitos pelo dispatch real (catálogo p/ LSP).
     *  GUARDA: StdCatalogTest exige == case-literals da fonte abaixo. */
    static List<String> functions() { return List.of("create", "save", "find", "all", "delete", "count", "deleteAll", "where", "saveAll", "page", "migrate"); }

    /** {@code orm.<method>(...) } — resolve o runtime function. */
    static OrmCall staticCall(String name, List<Type> argTypes, boolean typed, String entityName) {
        return switch (name) {
            case "create" -> argTypes.size() == 1
                    ? new OrmCall("kof_orm_create", BOOL, List.of(STR), entityName, false)
                    : null;
            case "save" -> argTypes.size() == 2
                    ? new OrmCall("kof_orm_save", OBJ, List.of(STR, OBJ), entityName, false)
                    : null;
            case "find" -> typed && argTypes.size() == 2
                    ? new OrmCall("kof_orm_find", OBJ, List.of(STR, OBJ), entityName, true)
                    : null;
            case "all" -> typed && argTypes.size() == 1
                    ? new OrmCall("kof_orm_all", new Type.ClassType("kof", "List", List.of(STR)),
                    List.of(STR), entityName, true)
                    : null;
            case "delete" -> typed && argTypes.size() == 2
                    ? new OrmCall("kof_orm_delete", BOOL, List.of(STR, OBJ), entityName, true)
                    : null;
            case "count" -> typed && argTypes.size() == 1
                    ? new OrmCall("kof_orm_count", Type.PrimitiveType.LONG, List.of(STR), entityName, true)
                    : typed && argTypes.size() == 3
                    ? new OrmCall("kof_orm_count_where", Type.PrimitiveType.LONG, List.of(STR, STR, OBJ),
                    entityName, true)
                    : null;
            case "deleteAll" -> typed && argTypes.size() == 1
                    ? new OrmCall("kof_orm_delete_all", BOOL, List.of(STR), entityName, true)
                    : null;
            case "where" -> typed && argTypes.size() == 3
                    ? new OrmCall("kof_orm_where", new Type.ClassType("kof", "List", List.of(STR)),
                    List.of(STR, STR, OBJ), entityName, true)
                    : typed && argTypes.size() == 4
                    ? new OrmCall("kof_orm_where_op", new Type.ClassType("kof", "List", List.of(STR)),
                    List.of(STR, STR, STR, OBJ), entityName, true)
                    : null;
            case "saveAll" -> typed && argTypes.size() == 2
                    ? new OrmCall("kof_orm_save_all", BOOL, List.of(STR, new Type.ClassType("kof", "List", List.of(STR))),
                    entityName, false)
                    : null;
            case "page" -> typed && argTypes.size() == 3
                    ? new OrmCall("kof_orm_page", new Type.ClassType("kof", "List", List.of(STR)),
                    List.of(STR, OBJ, OBJ), entityName, true)
                    : null;
            case "migrate" -> argTypes.size() == 3
                    ? new OrmCall("kof_orm_migrate", BOOL, List.of(STR, STR, STR), entityName, false)
                    : null;
            default -> null;
        };
    }

    /** Serializa o schema da entidade para o runtime:
     *  {@code name:type:constraints,...} (ex.: {@code id:long:generated,
     *  name:string,email:string:unique,age:int}). */
    static String schemaString(List<EntityFieldNode> fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) sb.append(',');
            EntityFieldNode f = fields.get(i);
            sb.append(f.name()).append(':').append(dbType(f.type()));
            if (f.generated()) sb.append(":generated");
            if (f.unique()) sb.append(":unique");
        }
        return sb.toString();
    }

    private static String dbType(String kofType) {
        return switch (kofType) {
            case "Int" -> "int";
            case "Long" -> "long";
            case "String" -> "string";
            case "Bool" -> "bool";
            case "Double" -> "double";
            case "Float" -> "float";
            default -> "string";
        };
    }

    /** Nome da tabela: o nome da entidade em minúsculas (User → user). */
    static String tableName(String entityName) {
        return entityName.toLowerCase();
    }
}