package dev.kof.compiler;

/**
 * IO de arquivo/caminho/diretório do runtime JVM + tetris.
 * Extraído de JvmRuntime.sourceCore (REFACTOR-500 Fase 5) — fragmento do
 * source do KofRuntime gerado; a concatenação preserva conteúdo byte-a-byte.
 */
final class JvmRuntimeIo {

    private JvmRuntimeIo() {}

    static String source() {
        return """
                // ── kof.io — File / Path / Directory ──────────────

                private static java.nio.file.Path p(String path) {
                    return java.nio.file.Path.of(path.replace('\\\\', '/'));
                }

                /** Caminho canônico Kof: separador sempre '/' (multiplataforma). */
                private static String s(java.nio.file.Path path) {
                    return path.toString().replace('\\\\', '/');
                }

                public static int kof_io_file_exists(String path) {
                    return java.nio.file.Files.exists(p(path)) ? 1 : 0;
                }

                public static int kof_io_file_is_file(String path) {
                    return java.nio.file.Files.isRegularFile(p(path)) ? 1 : 0;
                }

                public static int kof_io_file_is_dir(String path) {
                    return java.nio.file.Files.isDirectory(p(path)) ? 1 : 0;
                }

                public static String kof_io_read_text(String path) {
                    try {
                        return java.nio.file.Files.readString(p(path), java.nio.charset.StandardCharsets.UTF_8);
                    } catch (java.io.IOException e) {
                        return null;
                    }
                }

                public static int kof_io_write_text(String path, String content) {
                    try {
                        java.nio.file.Files.writeString(p(path), content, java.nio.charset.StandardCharsets.UTF_8);
                        return 1;
                    } catch (java.io.IOException e) {
                        return 0;
                    }
                }

                public static int kof_io_append_text(String path, String content) {
                    try {
                        java.nio.file.Files.writeString(p(path), content, java.nio.charset.StandardCharsets.UTF_8,
                                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                        return 1;
                    } catch (java.io.IOException e) {
                        return 0;
                    }
                }

                public static int[] kof_io_read_bytes(String path) {
                    try {
                        byte[] b = java.nio.file.Files.readAllBytes(p(path));
                        int[] out = new int[b.length];
                        for (int i = 0; i < b.length; i++) out[i] = b[i] & 0xFF;
                        return out;
                    } catch (java.io.IOException e) {
                        return null;
                    }
                }

                public static int[] kof_io_read_range(String path, long offset, long len) {
                    return kof_io_read_range_path(path, offset, len);
                }

                public static int[] kof_io_read_range_path(String path, long offset, long len) {
                    int ilen = (int) Math.min(len, Integer.MAX_VALUE);
                    try {
                        byte[] b = new byte[ilen];
                        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(p(path).toFile(), "r")) {
                            raf.seek(offset);
                            int read = raf.read(b, 0, ilen);
                            int[] out = new int[read < 0 ? 0 : read];
                            for (int i = 0; i < out.length; i++) out[i] = b[i] & 0xFF;
                            return out;
                        }
                    } catch (java.io.IOException e) {
                        return null;
                    }
                }

                public static int kof_io_write_bytes(String path, int[] bytes) {
                    try {
                        byte[] b = new byte[bytes.length];
                        for (int i = 0; i < bytes.length; i++) b[i] = (byte) (bytes[i] & 0xFF);
                        java.nio.file.Files.write(p(path), b);
                        return 1;
                    } catch (java.io.IOException e) {
                        return 0;
                    }
                }

                public static int kof_io_append_bytes(String path, int[] bytes) {
                    try {
                        byte[] b = new byte[bytes.length];
                        for (int i = 0; i < bytes.length; i++) b[i] = (byte) (bytes[i] & 0xFF);
                        java.nio.file.Files.write(p(path), b,
                                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                        return 1;
                    } catch (java.io.IOException e) {
                        return 0;
                    }
                }

                public static int kof_io_delete(String path) {
                    try {
                        if (!java.nio.file.Files.exists(p(path))) return 0;
                        java.nio.file.Files.deleteIfExists(p(path));
                        return 1;
                    } catch (java.io.IOException e) {
                        return 0;
                    }
                }

                public static long kof_io_file_size(String path) {
                    try {
                        return java.nio.file.Files.size(p(path));
                    } catch (java.io.IOException e) {
                        // sentinela -1 era um anti-pattern do próprio corpus: erro é
                        // exceção recuperável (catch (String e)), nunca valor de dados.
                        throw new RuntimeException("file not found: " + path);
                    }
                }

                public static String kof_io_file_name(String path) {
                    java.nio.file.Path pp = p(path).getFileName();
                    return pp == null ? path.replace('\\\\', '/') : s(pp);
                }

                public static String kof_io_path_resolve(String base, String child) {
                    return s(p(base).resolve(child));
                }

                public static String kof_io_path_parent(String path) {
                    java.nio.file.Path pp = p(path).getParent();
                    return pp == null ? null : s(pp);
                }

                public static String kof_io_path_file_name(String path) {
                    return kof_io_file_name(path);
                }

                public static String kof_io_path_extension(String path) {
                    String name = kof_io_file_name(path);
                    int dot = name.lastIndexOf('.');
                    return dot <= 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1);
                }

                public static String kof_io_path_normalize(String path) {
                    String n = s(p(path).normalize());
                    return n.isEmpty() ? "." : n;
                }

                public static int kof_io_path_is_absolute(String path) {
                    return p(path).isAbsolute() ? 1 : 0;
                }

                public static String kof_io_path_to_absolute(String path) {
                    return s(p(path).toAbsolutePath());
                }

                public static int kof_io_dir_create(String path) {
                    try {
                        java.nio.file.Files.createDirectory(p(path));
                        return 1;
                    } catch (java.io.IOException e) {
                        return 0;
                    }
                }

                public static int kof_io_dir_create_dirs(String path) {
                    try {
                        java.nio.file.Files.createDirectories(p(path));
                        return 1;
                    } catch (java.io.IOException e) {
                        return 0;
                    }
                }

                public static int kof_io_dir_delete(String path) {
                    try {
                        if (!java.nio.file.Files.exists(p(path))) return 0;
                        // recursivo: precisa remover sub-árvore (Files.delete
                        // falha em diretório não-vazio)
                        try (var walk = java.nio.file.Files.walk(p(path))) {
                            walk.sorted(java.util.Comparator.reverseOrder())
                                    .forEach(pp -> {
                                        try { java.nio.file.Files.deleteIfExists(pp); }
                                        catch (java.io.IOException ignored) {}
                                    });
                        }
                        return 1;
                    } catch (java.io.IOException e) {
                        return 0;
                    }
                }

                public static java.util.ArrayList<String> kof_io_dir_list(String path) {
                    try (var stream = java.nio.file.Files.list(p(path))) {
                        return stream.map(java.nio.file.Path::getFileName)
                                .map(java.nio.file.Path::toString).sorted()
                                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
                    } catch (java.io.IOException e) {
                        return null;
                    }
                }

""";
    }
}
