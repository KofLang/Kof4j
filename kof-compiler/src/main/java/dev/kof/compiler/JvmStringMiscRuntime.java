package dev.kof.compiler;

/**
 * Fragmento do source do KofRuntime gerado (REFACTOR-500 Fase 8).
 * kof.security G9 (rate-limit/sessions/api-keys) + higher-order + enum + tetris - parte 5/5 de JvmStringRuntime. Concatenacao preserva byte-a-byte.
 */
final class JvmStringMiscRuntime {

    private JvmStringMiscRuntime() {}

    static String source() {
        return """

                // ── kof.security G9 (rate limiting / sessions / API keys) ──

                private static final java.util.concurrent.ConcurrentHashMap<String, long[]> KOF_RATE_LIMIT = new java.util.concurrent.ConcurrentHashMap<>();
                private static final java.util.concurrent.ConcurrentHashMap<String, String> KOF_SESSIONS = new java.util.concurrent.ConcurrentHashMap<>();
                private static final java.util.concurrent.ConcurrentHashMap<String, Boolean> KOF_API_KEYS = new java.util.concurrent.ConcurrentHashMap<>();

                public static boolean kof_sec_rate_limit(String key, int limit, int windowSeconds) {
                    if (key == null) key = "";
                    if (limit <= 0 || windowSeconds <= 0) return false;
                    long now = System.currentTimeMillis();
                    long windowMillis = windowSeconds * 1000L;
                    long[] entry = KOF_RATE_LIMIT.computeIfAbsent(key, k -> new long[]{now, 0});
                    synchronized (entry) {
                        if (now - entry[0] >= windowMillis) {
                            entry[0] = now;
                            entry[1] = 1;
                            return true;
                        }
                        if (entry[1] < limit) {
                            entry[1]++;
                            return true;
                        }
                        return false;
                    }
                }

                public static String kof_sec_session_create(String data) {
                    String id = kof_sec_random_hex(16);
                    KOF_SESSIONS.put(id, data == null ? "" : data);
                    return id;
                }

                public static String kof_sec_session_get(String id) {
                    if (id == null) return null;
                    return KOF_SESSIONS.get(id);
                }

                public static boolean kof_sec_session_destroy(String id) {
                    if (id == null) return false;
                    return KOF_SESSIONS.remove(id) != null;
                }

                public static String kof_sec_api_key_generate() {
                    String key = kof_sec_random_hex(32);
                    KOF_API_KEYS.put(key, Boolean.TRUE);
                    return key;
                }

                public static boolean kof_sec_api_key_valid(String key) {
                    if (key == null) return false;
                    return KOF_API_KEYS.containsKey(key);
                }

                // ── higher-order em List (P1-residual) ─────────────────
                // Lambdas sintéticas expõem invoke(...) tipado; reflection
                // genérica localiza pelo arity com boxing automático.

                private static Object kof_ho_invoke(Object lambda, Object[] args) throws Exception {
                    for (var m : lambda.getClass().getMethods()) {
                        if (!m.getName().equals("invoke")) continue;
                        if (m.getParameterCount() != args.length) continue;
                        if (m.isSynthetic()) continue;
                        try { return m.invoke(lambda, args); } catch (IllegalArgumentException ignored) {}
                    }
                    throw new IllegalStateException("lambda invoke não encontrado (" + args.length + " args)");
                }

                public static java.util.ArrayList<Object> kof_list_map(
                        java.util.ArrayList<?> list, Object lambda) throws Exception {
                    var out = new java.util.ArrayList<Object>();
                    for (Object o : list) out.add(kof_ho_invoke(lambda, new Object[]{o}));
                    return out;
                }

                public static java.util.ArrayList<Object> kof_list_filter(
                        java.util.ArrayList<?> list, Object lambda) throws Exception {
                    var out = new java.util.ArrayList<Object>();
                    for (Object o : list) {
                        Object keep = kof_ho_invoke(lambda, new Object[]{o});
                        if (Boolean.TRUE.equals(keep) || Integer.valueOf(1).equals(keep)) out.add(o);
                    }
                    return out;
                }

                public static Object kof_list_reduce(
                        java.util.ArrayList<?> list, Object initial, Object lambda) throws Exception {
                    Object acc = initial;
                    for (Object o : list) acc = kof_ho_invoke(lambda, new Object[]{acc, o});
                    return acc;
                }

                // ── kof.enum (P1) ──────────────────────────────────────

                public static String kof_enum_value_of(java.util.List<?> values, String name) {
                    if (values != null && name != null) {
                        for (Object v : values) {
                            if (name.equals(v)) return (String) v;
                        }
                    }
                    return null;
                }

                // ── kof.tetris — hidden easter egg ────────────────────
                // `tetris.run()` starts a simplified terminal tetris.
                // Keys: a=left d=right s=down w=rotate space=hard drop
                //       q=quit. On POSIX the terminal switches to raw mode
                //       (stty) so single keystrokes work without Enter.

                public static void kof_tetris_run() {
                    final int COLS = 10;
                    final int ROWS = 20;
                    final int[][] board = new int[ROWS][COLS];
                    final int[][][] SHAPES = {
                            {{1, 1, 1, 1}},
                            {{1, 1}, {1, 1}},
                            {{0, 1, 0}, {1, 1, 1}},
                            {{0, 1, 1}, {1, 1, 0}},
                            {{1, 1, 0}, {0, 1, 1}},
                            {{1, 0, 0}, {1, 1, 1}},
                            {{0, 0, 1}, {1, 1, 1}}
                    };
                    final String ESC = "" + (char) 27;
                    final java.util.Random rnd = new java.util.Random(System.nanoTime());
                    final java.io.PrintStream out = System.out;

                    boolean raw = false;
                    try {
                        Process p = new ProcessBuilder("stty", "raw", "-echo")
                                .redirectErrorStream(true).start();
                        raw = p.waitFor() == 0;
                    } catch (Exception ignored) {
                    }
                    if (raw) {
                        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                            try {
                                new ProcessBuilder("stty", "sane").start().waitFor();
                            } catch (Exception ignored) {
                            }
                        }, "kof-tetris-restore"));
                    }

                    int[][] cur = SHAPES[rnd.nextInt(SHAPES.length)];
                    int cx = 4;
                    int cy = 0;
                    int score = 0;
                    int level = 1;
                    long dropAt = System.currentTimeMillis() + 600;
                    boolean over = false;

                    while (!over) {
                        StringBuilder sb = new StringBuilder();
                        sb.append(ESC).append("[2J").append(ESC).append("[H");
                        sb.append("kof.tetris  score=").append(score)
                                .append("  level=").append(level);
                        sb.append((char) 10);
                        sb.append("+----------+").append((char) 10);
                        for (int y = 0; y < ROWS; y++) {
                            sb.append('|');
                            for (int x = 0; x < COLS; x++) {
                                boolean cell = board[y][x] != 0;
                                if (!cell) {
                                    for (int py = 0; py < cur.length; py++) {
                                        for (int px = 0; px < cur[py].length; px++) {
                                            if (cur[py][px] != 0 && cy + py == y && cx + px == x) {
                                                cell = true;
                                            }
                                        }
                                    }
                                }
                                sb.append(cell ? '#' : '.');
                            }
                            sb.append('|').append((char) 10);
                        }
                        sb.append("+----------+").append((char) 10);
                        sb.append("a:left d:right s:down w:rotate space:drop q:quit")
                                .append((char) 10);
                        out.print(sb);

                        long now = System.currentTimeMillis();
                        if (now >= dropAt) {
                            if (kof_tetris_fits(cur, cx, cy + 1, board)) {
                                cy++;
                            } else {
                                for (int y = 0; y < cur.length; y++) {
                                    for (int x = 0; x < cur[y].length; x++) {
                                        if (cur[y][x] != 0) {
                                            board[cy + y][cx + x] = 1;
                                        }
                                    }
                                }
                                int lines = kof_tetris_clear_lines(board);
                                if (lines > 0) {
                                    score += lines * 100 * level;
                                    level = 1 + score / 1000;
                                }
                                cur = SHAPES[rnd.nextInt(SHAPES.length)];
                                cx = 4;
                                cy = 0;
                                if (!kof_tetris_fits(cur, cx, cy, board)) {
                                    over = true;
                                    break;
                                }
                            }
                            dropAt = now + kof_tetris_drop_ms(level);
                        }

                        int key = kof_tetris_key();
                        if (key == 'q' || key == -1) {
                            break;
                        }
                        if (key == 'a' && kof_tetris_fits(cur, cx - 1, cy, board)) {
                            cx--;
                        }
                        if (key == 'd' && kof_tetris_fits(cur, cx + 1, cy, board)) {
                            cx++;
                        }
                        if (key == 's' && kof_tetris_fits(cur, cx, cy + 1, board)) {
                            cy++;
                        }
                        if (key == ' ') {
                            while (kof_tetris_fits(cur, cx, cy + 1, board)) {
                                cy++;
                            }
                        }
                        if (key == 'w') {
                            int[][] rotated = kof_tetris_rotate(cur);
                            int rcx = cx;
                            if (!kof_tetris_fits(rotated, rcx, cy, board)) {
                                rcx = cx - 1;
                            }
                            if (!kof_tetris_fits(rotated, rcx, cy, board)) {
                                rcx = cx + 1;
                            }
                            if (kof_tetris_fits(rotated, rcx, cy, board)) {
                                cur = rotated;
                                cx = rcx;
                            }
                        }

                        try {
                            Thread.sleep(20);
                        } catch (InterruptedException ignored) {
                        }
                    }

                    out.print(ESC + "[2J" + ESC + "[H");
                    if (over) {
                        out.println("GAME OVER  score=" + score + "  level=" + level);
                    } else {
                        out.println("kof.tetris  score=" + score + "  bye");
                    }
                }

                private static boolean kof_tetris_fits(int[][] s, int bx, int by, int[][] board) {
                    for (int y = 0; y < s.length; y++) {
                        for (int x = 0; x < s[y].length; x++) {
                            if (s[y][x] == 0) {
                                continue;
                            }
                            int gx = bx + x;
                            int gy = by + y;
                            if (gx < 0 || gx >= 10 || gy >= 20) {
                                return false;
                            }
                            if (gy >= 0 && board[gy][gx] != 0) {
                                return false;
                            }
                        }
                    }
                    return true;
                }

                private static int[][] kof_tetris_rotate(int[][] s) {
                    int h = s.length;
                    int w = s[0].length;
                    int[][] r = new int[w][h];
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            r[x][h - 1 - y] = s[y][x];
                        }
                    }
                    return r;
                }

                private static int kof_tetris_clear_lines(int[][] board) {
                    int rows = 0;
                    for (int y = 0; y < 20; y++) {
                        boolean full = true;
                        for (int x = 0; x < 10; x++) {
                            if (board[y][x] == 0) {
                                full = false;
                                break;
                            }
                        }
                        if (full) {
                            rows++;
                        }
                    }
                    if (rows == 0) {
                        return 0;
                    }
                    int[][] next = new int[20][10];
                    int dst = 19;
                    for (int y = 19; y >= 0; y--) {
                        boolean full = true;
                        for (int x = 0; x < 10; x++) {
                            if (board[y][x] == 0) {
                                full = false;
                                break;
                            }
                        }
                        if (full) {
                            continue;
                        }
                        for (int x = 0; x < 10; x++) {
                            next[dst][x] = board[y][x];
                        }
                        dst--;
                    }
                    for (int y = 0; y < 20; y++) {
                        for (int x = 0; x < 10; x++) {
                            board[y][x] = next[y][x];
                        }
                    }
                    return rows;
                }

                private static int kof_tetris_drop_ms(int level) {
                    int ms = 600 - (level - 1) * 40;
                    return ms < 100 ? 100 : ms;
                }

                private static int kof_tetris_key() {
                    try {
                        java.io.InputStream in = System.in;
                        if (in.available() > 0) {
                            return in.read();
                        }
                    } catch (java.io.IOException ignored) {
                    }
                    return 0;
                }
""";
    }
}
