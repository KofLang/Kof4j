package dev.kof.compiler.jvm;

/**
 * Runtime do kof.config (nativo) — gerado no KofRuntime junto com o JvmRuntime.
 * Separado num arquivo próprio porque o constant pool do
 * javac limita cada string a 65535 bytes.
 */
public final class JvmConfigRuntime {

    private JvmConfigRuntime() {}

    static String source() {
        return """
                // ── kof.config — native configuration ────────────────

                public static String kof_config_env(String name) {
                    return System.getenv(name);
                }

                public static String kof_config_get(String key) {
                    return kof_config_lookup(key);
                }

                // P2 (docs/stdlib-config.md §8.2): interpolação ${key} —
                // resolve referências entre chaves do próprio arquivo.
                // Ciclo → valor literal; chave inexistente → literal inalterado.
                // O set de chaves "em resolução" vive no lookup (ThreadLocal),
                // porque um ciclo entra por kof_config_lookup → interpolate →
                // lookup... e não dentro de um único interpolate.
                private static final ThreadLocal<java.util.Set<String>> KOF_CFG_RESOLVING =
                        ThreadLocal.withInitial(java.util.HashSet::new);

                private static String kof_config_interpolate(String value) {
                    if (value == null || !value.contains("${")) return value;
                    String current = value;
                    for (int depth = 0; depth < 16; depth++) {
                        int start = current.indexOf("${");
                        if (start < 0) break;
                        int end = current.indexOf('}', start + 2);
                        if (end < 0) break;
                        String ref = current.substring(start + 2, end);
                        java.util.Set<String> resolving = KOF_CFG_RESOLVING.get();
                        if (resolving.contains(ref)) {
                            // ciclo: mantém o valor literal original
                            return value;
                        }
                        resolving.add(ref);
                        String resolved;
                        try {
                            resolved = kof_config_lookup(ref);
                        } finally {
                            resolving.remove(ref);
                        }
                        if (resolved == null) {
                            // referência desconhecida: mantém literal
                            return value;
                        }
                        current = current.substring(0, start) + resolved
                                + current.substring(end + 1);
                    }
                    return current;
                }

                public static String kof_config_required(String key) {
                    String v = kof_config_lookup(key);
                    if (v == null) {
                        throw new IllegalStateException(
                            "Kof config: missing required key '" + key
                            + "' (searched KOF_CONFIG, env KOF_"
                            + key.toUpperCase().replace('.', '_').replace('-', '_')
                            + ", profile file and kof.config)");
                    }
                    return v;
                }

                public static String kof_config_str(String key, String def) {
                    String v = kof_config_lookup(key);
                    return v != null ? v : def;
                }

                public static int kof_config_int(String key, int def) {
                    String v = kof_config_lookup(key);
                    if (v == null) return def;
                    try {
                        return Integer.parseInt(v.trim());
                    } catch (NumberFormatException e) {
                        return def;
                    }
                }

                public static long kof_config_long(String key, long def) {
                    String v = kof_config_lookup(key);
                    if (v == null) return def;
                    try {
                        return Long.parseLong(v.trim());
                    } catch (NumberFormatException e) {
                        return def;
                    }
                }

                public static int kof_config_bool(String key, int def) {
                    String v = kof_config_lookup(key);
                    if (v == null) return def;
                    String t = v.trim().toLowerCase();
                    if (t.equals("true") || t.equals("1") || t.equals("yes")) return 1;
                    if (t.equals("false") || t.equals("0") || t.equals("no")) return 0;
                    return def;
                }

                public static int kof_config_has(String key) {
                    return kof_config_lookup(key) != null ? 1 : 0;
                }

                private static String kof_config_lookup(String key) {
                    String v = kof_config_lookup_raw(key);
                    return v != null ? kof_config_interpolate(v) : null;
                }
                private static String kof_config_lookup_raw(String key) {
                    String file = System.getenv("KOF_CONFIG");
                    if (file != null && !file.isBlank()) {
                        String v = kof_config_read_key(java.nio.file.Path.of(file), key);
                        if (v != null) return v;
                    }
                    String envName = "KOF_" + key.toUpperCase()
                            .replace('.', '_').replace('-', '_');
                    String env = System.getenv(envName);
                    if (env != null) return env;
                    String profile = System.getenv("KOF_PROFILE");
                    String fileName = (profile != null && !profile.isBlank())
                            ? "kof." + profile + ".config" : "kof.config";
                    return kof_config_read_key(java.nio.file.Path.of(fileName), key);
                }

                private static String kof_config_read_key(java.nio.file.Path file, String key) {
                    if (!java.nio.file.Files.exists(file)) return null;
                    try {
                        for (String line : java.nio.file.Files.readAllLines(
                                file, java.nio.charset.StandardCharsets.UTF_8)) {
                            String t = line.trim();
                            if (t.isEmpty() || t.startsWith("#")) continue;
                            int eq = t.indexOf('=');
                            if (eq <= 0) continue;
                            if (t.substring(0, eq).trim().equals(key)) {
                                return t.substring(eq + 1).trim();
                            }
                        }
                    } catch (java.io.IOException ignored) {
                    }
                    return null;
                }

                // ── kof.log — native logging ─────────────────────────

                private static final int KOF_LOG_LEVEL = kof_log_parse_level(System.getenv("KOF_LOG_LEVEL"));
                private static final boolean KOF_LOG_JSON =
                        "1".equals(System.getenv("KOF_LOG_JSON"))
                                || "true".equalsIgnoreCase(System.getenv("KOF_LOG_JSON"));
                private static final ThreadLocal<String> KOF_LOG_REQUEST_ID = new ThreadLocal<>();

                private static int kof_log_parse_level(String s) {
                    if (s == null || s.isBlank()) return 1;
                    return switch (s.trim().toLowerCase()) {
                        case "debug" -> 0;
                        case "info" -> 1;
                        case "warn", "warning" -> 2;
                        case "error" -> 3;
                        case "off" -> 4;
                        default -> 1;
                    };
                }

                private static String kof_log_timestamp() {
                    return java.time.LocalDateTime.now()
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
                }

                public static void kof_log_debug(String msg) {
                    kof_log(0, "DEBUG", msg);
                }

                public static void kof_log_info(String msg) {
                    kof_log(1, "INFO", msg);
                }

                public static void kof_log_warn(String msg) {
                    kof_log(2, "WARN", msg);
                }

                public static void kof_log_error(String msg) {
                    kof_log(3, "ERROR", msg);
                }

                private static void kof_log(int level, String label, String msg) {
                    if (level < KOF_LOG_LEVEL) return;
                    String line;
                    if (KOF_LOG_JSON) {
                        String rid = KOF_LOG_REQUEST_ID.get();
                        line = "{\\"ts\\":" + kof_json_encode_string(kof_log_timestamp())
                                + ",\\"level\\":" + kof_json_encode_string(label)
                                + ",\\"msg\\":" + kof_json_encode_string(msg == null ? "null" : msg)
                                + (rid != null ? ",\\"requestId\\":" + kof_json_encode_string(rid) : "")
                                + "}";
                    } else {
                        line = kof_log_timestamp() + " " + label + " " + (msg == null ? "null" : msg);
                    }
                    if (level >= 2) {
                        System.err.println(line);
                    } else {
                        System.out.println(line);
                    }
                }

                // ── kof.db — database (JDBC por interoperabilidade JVM) ──

                private static final java.util.concurrent.ConcurrentHashMap<String, java.sql.Connection> KOF_DB_CONNECTIONS =
                        new java.util.concurrent.ConcurrentHashMap<>();
                // issue #60: o id era "db" + (size() + 1) — fechar uma conexão
                // e abrir outra reutilizava o id de uma conexão AINDA ABERTA,
                // sobrescrevia o registro e redirecionava operações p/ o banco
                // errado (silencioso). Contador monotônico: nunca reutiliza
                // handle (mesmo padrão do KOF_MONGO_SEQ acima).
                private static final java.util.concurrent.atomic.AtomicInteger KOF_DB_SEQ =
                        new java.util.concurrent.atomic.AtomicInteger();
                private static volatile String KOF_DB_DEFAULT;
                private static final ThreadLocal<java.sql.Connection> KOF_DB_TX = new ThreadLocal<>();

                private static final java.util.concurrent.ConcurrentHashMap<String, Object> KOF_MONGO =
                        new java.util.concurrent.ConcurrentHashMap<>();
                private static final java.util.concurrent.atomic.AtomicInteger KOF_MONGO_SEQ =
                        new java.util.concurrent.atomic.AtomicInteger();

                public static String kof_db_connect(String url) throws Exception {
                    if (url.startsWith("mongodb://")) {
                        // MongoDB: o driver (mongodb-driver-sync) fica no
                        // classpath do programa — o runtime usa reflexão para
                        // não depender dele em compile-time
                        Class<?> clients = Class.forName("com.mongodb.client.MongoClients");
                        Object client = clients.getMethod("create", String.class).invoke(null, url);
                        String dbName = url;
                        int slash = url.indexOf('/', "mongodb://".length());
                        if (slash > 0) {
                            int q = url.indexOf('?', slash);
                            dbName = url.substring(slash + 1, q > 0 ? q : url.length());
                        }
                        if (dbName.isEmpty()) dbName = "kof";
                        Object database = client.getClass().getMethod("getDatabase", String.class)
                                .invoke(client, dbName);
                        String id = "mongo-" + KOF_MONGO_SEQ.incrementAndGet();
                        KOF_MONGO.put(id, database);
                        return id;
                    }
                    return kof_db_register(java.sql.DriverManager.getConnection(url));
                }

                public static String kof_db_connect2(String url, String user, String pass) throws Exception {
                    return kof_db_register(java.sql.DriverManager.getConnection(url, user, pass));
                }

                private static String kof_db_register(java.sql.Connection c) {
                    String id = "db" + KOF_DB_SEQ.incrementAndGet();
                    KOF_DB_CONNECTIONS.put(id, c);
                    KOF_DB_DEFAULT = id;
                    return id;
                }

                public static void kof_db_close(String id) throws Exception {
                    java.sql.Connection c = KOF_DB_CONNECTIONS.remove(id);
                    if (c != null) c.close();
                }

                private static boolean kof_mongo_id(String id) {
                    return id.startsWith("mongo-");
                }

                private static Object kof_mongo_coll(String id, String table) throws Exception {
                    Object db = KOF_MONGO.get(id);
                    return db.getClass().getMethod("getCollection", String.class).invoke(db, table);
                }

                private static Object kof_mongo_eq(String field, Object value) throws Exception {
                    Class<?> filters = Class.forName("com.mongodb.client.model.Filters");
                    return filters.getMethod("eq", String.class, Object.class).invoke(null, field, value);
                }

                private static Object kof_mongo_op(String field, String op, Object value) throws Exception {
                    // operador → filtro do MongoDB (whitelist — mesmo
                    // conjunto aceito no SQL; LIKE vira regex simples)
                    Class<?> filters = Class.forName("com.mongodb.client.model.Filters");
                    String m = switch (op) {
                        case ">" -> "gt";
                        case "<" -> "lt";
                        case ">=" -> "gte";
                        case "<=" -> "lte";
                        case "!=" -> "ne";
                        case "==" -> "eq";
                        case "LIKE" -> "regex";
                        default -> throw new IllegalArgumentException("ORM operator not allowed: " + op);
                    };
                    if ("LIKE".equals(op)) {
                        String pattern = String.valueOf(value).replace("%", ".*").replace("_", ".");
                        return filters.getMethod(m, String.class, String.class).invoke(null, field, pattern);
                    }
                    return filters.getMethod(m, String.class, Object.class).invoke(null, field, value);
                }

                private static Object kof_mongo_doc(String key, Object value) throws Exception {
                    Class<?> docType = Class.forName("org.bson.Document");
                    java.lang.reflect.Constructor<?> ctor = docType.getConstructor(String.class, Object.class);
                    return ctor.newInstance(key, value);
                }

                private static Object kof_mongo_values(java.lang.reflect.RecordComponent[] comps,
                                                       Object[] values) throws Exception {
                    Class<?> docType = Class.forName("org.bson.Document");
                    Object doc = docType.getConstructor().newInstance();
                    java.lang.reflect.Method put = docType.getMethod("put", String.class, Object.class);
                    for (int i = 0; i < comps.length; i++) {
                        put.invoke(doc, comps[i].getName(), values[i]);
                    }
                    return doc;
                }

                private static java.sql.Connection kof_db_conn(String id) throws Exception {
                    String key = (id == null || id.isEmpty()) ? KOF_DB_DEFAULT : id;
                    java.sql.Connection c = key == null ? null : KOF_DB_CONNECTIONS.get(key);
                    if (c == null) throw new IllegalArgumentException("unknown db connection: " + id);
                    return c;
                }

                public static int kof_db_execute(String id, String sql) throws Exception {
                    return kof_db_execute_n(id, sql, new Object[0]);
                }

                public static int kof_db_execute1(String id, String sql, Object a) throws Exception {
                    return kof_db_execute_n(id, sql, new Object[]{a});
                }

                public static int kof_db_execute2(String id, String sql, Object a, Object b) throws Exception {
                    return kof_db_execute_n(id, sql, new Object[]{a, b});
                }

                public static int kof_db_execute3(String id, String sql, Object a, Object b, Object c) throws Exception {
                    return kof_db_execute_n(id, sql, new Object[]{a, b, c});
                }

                public static int kof_db_execute4(String id, String sql, Object a, Object b, Object c, Object d) throws Exception {
                    return kof_db_execute_n(id, sql, new Object[]{a, b, c, d});
                }

                private static int kof_db_execute_n(String id, String sql, Object[] args) throws Exception {
                    try (java.sql.PreparedStatement ps = kof_db_conn(id).prepareStatement(sql)) {
                        for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
                        return ps.executeUpdate();
                    }
                }

                public static java.util.ArrayList<Object> kof_db_query0(String id, String sql, String className) throws Exception {
                    return kof_db_query_n(id, sql, new Object[0], className);
                }

                public static java.util.ArrayList<Object> kof_db_query1(String id, String sql, Object a, String className) throws Exception {
                    return kof_db_query_n(id, sql, new Object[]{a}, className);
                }

                public static java.util.ArrayList<Object> kof_db_query2(String id, String sql, Object a, Object b, String className) throws Exception {
                    return kof_db_query_n(id, sql, new Object[]{a, b}, className);
                }

                public static java.util.ArrayList<Object> kof_db_query3(String id, String sql, Object a, Object b, Object c, String className) throws Exception {
                    return kof_db_query_n(id, sql, new Object[]{a, b, c}, className);
                }

                public static java.util.ArrayList<Object> kof_db_query4(String id, String sql, Object a, Object b, Object c, Object d, String className) throws Exception {
                    return kof_db_query_n(id, sql, new Object[]{a, b, c, d}, className);
                }

                private static java.util.ArrayList<Object> kof_db_query_n(String id, String sql, Object[] args, String className) throws Exception {
                    java.util.ArrayList<Object> rows = new java.util.ArrayList<>();
                    try (java.sql.PreparedStatement ps = kof_db_conn(id).prepareStatement(sql)) {
                        for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            java.sql.ResultSetMetaData md = rs.getMetaData();
                            int cols = md.getColumnCount();
                            while (rs.next()) {
                                java.util.LinkedHashMap<String, Object> row = new java.util.LinkedHashMap<>();
                                for (int i = 1; i <= cols; i++) {
                                    row.put(md.getColumnLabel(i).toLowerCase(), rs.getObject(i));
                                }
                                if (className == null) {
                                    rows.add(kof_db_row_to_json(row));
                                } else {
                                    rows.add(kof_json_bind(Class.forName(className), row));
                                }
                            }
                        }
                    }
                    return rows;
                }

                private static String kof_db_row_to_json(java.util.Map<String, Object> row) {
                    StringBuilder sb = new StringBuilder("{");
                    boolean first = true;
                    for (var e : row.entrySet()) {
                        if (!first) sb.append(',');
                        first = false;
                        sb.append(kof_json_encode_string(e.getKey())).append(':');
                        Object v = e.getValue();
                        if (v == null) {
                            sb.append("null");
                        } else if (v instanceof String s) {
                            sb.append(kof_json_encode_string(s));
                        } else if (v instanceof Number n) {
                            sb.append(n);
                        } else if (v instanceof Boolean b) {
                            sb.append(b);
                        } else {
                            sb.append(kof_json_encode_string(String.valueOf(v)));
                        }
                    }
                    return sb.append('}').toString();
                }

                public static void kof_db_transaction(Object task) throws Exception {
                    java.sql.Connection c = kof_db_conn(KOF_DB_DEFAULT);
                    // GitHub #65 / bug 77 — aninhamento: um bloco transaction
                    // interno NESTA mesma conexão NÃO comita nem rollbacka —
                    // participa da transação externa (qualquer erro propaga
                    // p/ o bloco externo decidir). Antes o commit interno
                    // confirmava as linhas da transação externa e o rollback
                    // posterior não as desfazia (garantia transacional quebrada
                    // silenciosamente). Bloco em OUTRA conexão continua com
                    // transação própria (comportamento anterior).
                    boolean nested = c.equals(KOF_DB_TX.get());
                    boolean prevAuto = c.getAutoCommit();
                    c.setAutoCommit(false);
                    if (!nested) KOF_DB_TX.set(c);
                    try {
                        task.getClass().getMethod("invoke").invoke(task);
                        if (!nested) c.commit();
                    } catch (Exception e) {
                        if (!nested) {
                            try {
                                c.rollback();
                            } catch (Exception ignored) {
                            }
                        }
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        if (cause instanceof RuntimeException re) throw re;
                        if (cause instanceof Error err) throw err;
                        throw new RuntimeException(cause);
                    } finally {
                        if (!nested) {
                            c.setAutoCommit(prevAuto);
                            KOF_DB_TX.remove();
                        }
                    }
                }
""";
    }
}
