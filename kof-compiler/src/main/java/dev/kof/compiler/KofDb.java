package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;


/**
 * Compile-time dispatch table for the Kof-native database module
 * ({@code kof.db}) — JDBC por interoperabilidade JVM, API idiomática Kof.
 *
 * <pre>{@code
 * var db = db.connect("jdbc:h2:mem:test")
 * db.execute(db, "create table users(id int, name varchar)")
 * db.execute(db, "insert into users values (?, ?)", 1, "Mel")
 * var rows = db.query<User>(db, "select * from users where id = ?", 1)
 * transaction {
 *     db.execute(db, "insert into users values (2, 'Kof')")
 * }
 * }</pre>
 *
 * <p>Internamente cada chamada mapeia para funções {@code kof_db_*} do
 * {@code dev.kof.runtime.KofRuntime} gerado. Aridade dinâmica (varargs de
 * bind) é resolvida por overloads de aridade fixa (0-4 parâmetros).
 * Native e JS reportam {@code DB001} em compile-time.
 */
final class KofDb {

    private KofDb() {}

    static final Type DB = new Type.ClassType("kof.db", "Db", List.of());

    private static final Type STR = BuiltinTypes.STRING;
    private static final Type INT = Type.PrimitiveType.INT;
    private static final Type VOID = Type.PrimitiveType.VOID;
    private static final Type OBJ = Type.UnknownType.UNKNOWN;

    /** Máximo de parâmetros de bind suportados. */
    static final int MAX_BIND = 4;

    static boolean isDbNamespace(String name) {
        return "db".equals(name);
    }

    /** kof.db: JVM via JDBC; NATIVE (x86_64) via link direto de client libs
     *  (sem driver) — SQLite primeiro (libsqlite3.so.0), depois mysql/oracle.
     *  O link dinâmico de libsqlite3 exige libc — os cross estáticos
     *  (riscv64/aarch64, asm puro sem C) reportam DB001 em compile-time (R6:
     *  nunca undefined-reference silencioso no ld). JS reporta DB001. */
    static boolean supportedOn(Target target) {
        return target == Target.JVM || target == Target.NATIVE;
    }

    static String gapCode() {
        return "DB001";
    }

    record DbCall(String function, Type returnType, List<Type> parameterTypes) {}

    static boolean isQuery(String name) {
        return "query".equals(name);
    }

    static boolean isExecute(String name) {
        return "execute".equals(name);
    }

    /** {@code db.<method>(...) } — resolve aridade e o runtime function. */
    static DbCall staticCall(String name, List<Type> argTypes, boolean typed) {
        int bind = argTypes.size() - 2;
        if (isExecute(name) && bind >= 0 && bind <= MAX_BIND) {
            List<Type> params = new ArrayList<>();
            params.add(STR);
            params.add(STR);
            for (int i = 0; i < bind; i++) params.add(OBJ);
            String fn = bind == 0 ? "kof_db_execute" : "kof_db_execute" + bind;
            return new DbCall(fn, INT, params);
        }
        if (isQuery(name) && bind >= 0 && bind <= MAX_BIND) {
            List<Type> params = new ArrayList<>();
            params.add(STR);
            params.add(STR);
            for (int i = 0; i < bind; i++) params.add(OBJ);
            String fn = bind == 0 ? "kof_db_query0" : "kof_db_query" + bind;
            return new DbCall(fn, new Type.ClassType("kof", "List", List.of(STR)), params);
        }
        return switch (name) {
            case "connect" -> argTypes.size() == 1
                    ? new DbCall("kof_db_connect", STR, List.of(STR))
                    : argTypes.size() == 3
                    ? new DbCall("kof_db_connect2", STR, List.of(STR, STR, STR))
                    : null;
            case "close" -> argTypes.size() == 1
                    ? new DbCall("kof_db_close", VOID, List.of(STR))
                    : null;
            case "transaction" -> argTypes.size() == 1
                    ? new DbCall("kof_db_transaction", VOID, List.of(OBJ))
                    : null;
            default -> null;
        };
    }
}