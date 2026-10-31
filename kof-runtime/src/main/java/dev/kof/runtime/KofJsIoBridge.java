package dev.kof.runtime;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * kof.io platform bridge (filesystem primitives) for the KofJS GraalJS host
 * — sibling of KofJsDbBridge/KofJsNetBridge/KofJsProcessBridge/
 * KofJsOrmRuntime (same install(platform) convention), split out of
 * KofJsRunner to stay under the REFACTOR-500 ceiling (#854).
 */
final class KofJsIoBridge {

    private KofJsIoBridge() {}

    static void install(Map<String, Object> platform) {
        platform.put("readFile", (ProxyExecutable) args -> {
            try {
                return Files.readString(Path.of(args[0].asString()));
            } catch (IOException e) {
                return null;
            }
        });
        platform.put("writeFile", (ProxyExecutable) args -> {
            try {
                Files.writeString(Path.of(args[0].asString()), args[1].asString());
                return 0; // global kof.io writeFile = rc INT por contrato (BuiltinCallTyper:220)
            } catch (IOException e) {
                return -1;
            }
        });
        platform.put("fileExists", (ProxyExecutable) args -> Files.exists(Path.of(args[0].asString())));
        platform.put("fileIsFile", (ProxyExecutable) args -> Files.isRegularFile(Path.of(args[0].asString())));
        platform.put("fileIsDir", (ProxyExecutable) args -> Files.isDirectory(Path.of(args[0].asString())));
        platform.put("readText", (ProxyExecutable) args -> readFileText(args));
        platform.put("writeText", (ProxyExecutable) args -> writeFileText(args, false));
        platform.put("appendText", (ProxyExecutable) args -> writeFileText(args, true));
        platform.put("readBytes", (ProxyExecutable) args -> readBytes(args));
        platform.put("writeBytes", (ProxyExecutable) args -> writeBytes(args, false));
        platform.put("appendBytes", (ProxyExecutable) args -> writeBytes(args, true));
        // #854: criacao EXCLUSIVA (equivalente a CREATE_NEW) — Files.createFile
        // falha se o caminho ja existir OU for link (pendente ou nao), fechando
        // a corrida TOCTOU de exists()+write(). Mesmo contrato Bool do JVM/Native.
        platform.put("createNew", (ProxyExecutable) args -> createNew(args));
        platform.put("delete", (ProxyExecutable) args -> {
            try {
                return Files.deleteIfExists(Path.of(args[0].asString())); // §382: booleano REAL
            } catch (IOException e) {
                return false;
            }
        });
        platform.put("fileSize", (ProxyExecutable) args -> {
            try {
                return Files.size(Path.of(args[0].asString()));
            } catch (IOException e) {
                return -1L;
            }
        });
        platform.put("fileName", (ProxyExecutable) args -> {
            Path p = Path.of(args[0].asString());
            Path name = p.getFileName();
            return name == null ? args[0].asString() : name.toString();
        });
        platform.put("pathParent", (ProxyExecutable) args -> {
            Path parent = Path.of(args[0].asString()).getParent();
            return parent == null ? null : parent.toString();
        });
        platform.put("pathFileName", (ProxyExecutable) args -> {
            Path name = Path.of(args[0].asString()).getFileName();
            return name == null ? args[0].asString() : name.toString();
        });
        platform.put("pathExtension", (ProxyExecutable) args -> {
            String name = Path.of(args[0].asString()).getFileName().toString();
            int dot = name.lastIndexOf('.');
            return dot <= 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1);
        });
        platform.put("pathNormalize", (ProxyExecutable) args -> Path.of(args[0].asString()).normalize().toString());
        platform.put("pathResolve", (ProxyExecutable) args -> Path.of(args[0].asString()).resolve(args[1].asString()).toString());
        platform.put("pathIsAbsolute", (ProxyExecutable) args -> Path.of(args[0].asString()).isAbsolute());
        platform.put("pathToAbsolute", (ProxyExecutable) args -> Path.of(args[0].asString()).toAbsolutePath().toString());
        platform.put("dirCreate", (ProxyExecutable) args -> dirCreate(args, false));
        platform.put("dirCreateDirs", (ProxyExecutable) args -> dirCreate(args, true));
        platform.put("dirDelete", (ProxyExecutable) args -> {
            // §618/B (maintainer decision 08/10: recursive everywhere): the
            // JVM (`JvmRuntimeIo.kof_io_dir_delete`) and Native
            // (`RuntimeIo3`, riscv64/aarch64) delete non-empty trees, so the
            // JS bridge walks reverse-order too — same shape, same
            // absent→false contract. `Files.walk` does not follow symlinks.
            try {
                Path root = Path.of(args[0].asString());
                if (!Files.exists(root)) return false;
                try (var walk = Files.walk(root)) {
                    var it = walk.sorted(java.util.Comparator.reverseOrder()).iterator();
                    while (it.hasNext()) {
                        try { Files.deleteIfExists(it.next()); } catch (IOException ignored) {}
                    }
                }
                return true;
            } catch (IOException e) {
                return false;
            }
        });
        platform.put("dirList", (ProxyExecutable) args -> dirList(args));
    }

    // #854: criacao EXCLUSIVA (equivalente a CREATE_NEW) — Files.createFile
    // falha se o caminho ja existir OU for link (pendente ou nao), fechando
    // a corrida TOCTOU de exists()+write(). Mesmo contrato Bool do JVM/Native.
    private static Object createNew(Value[] args) {
        try {
            Files.createFile(Path.of(args[0].asString()));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static Object readFileText(Value[] args) {
        try {
            return Files.readString(Path.of(args[0].asString()));
        } catch (IOException e) {
            return null;
        }
    }

    private static Object writeFileText(Value[] args, boolean append) {
        try {
            if (append) {
                Files.writeString(Path.of(args[0].asString()), args[1].asString(),
                        java.nio.charset.StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND);
            } else {
                Files.writeString(Path.of(args[0].asString()), args[1].asString());
            }
            return true; // §382: face BOOL do host (era 0 = falsy no guest)
        } catch (IOException e) {
            return false;
        }
    }

    private static Object readBytes(Value[] args) {
        try {
            byte[] bytes = Files.readAllBytes(Path.of(args[0].asString()));
            int[] out = new int[bytes.length];
            for (int i = 0; i < bytes.length; i++) out[i] = bytes[i] & 0xFF;
            return out;
        } catch (IOException e) {
            return null;
        }
    }

    private static Object writeBytes(Value[] args, boolean append) {
        try {
            // §420 (familia §258/#773): era o ultimo cast bruto (int) sobre
            // getArraySize() em kof-runtime — guest array > 2^31 truncaria sem
            // diagnostico (R6). Bound check no precedente da mesma casa.
            long n = args[1].getArraySize();
            if (n > Integer.MAX_VALUE) {
                throw new RuntimeException("lista excede o limite da ponte JS (" + n + ")");
            }
            byte[] bytes = new byte[(int) n];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) (args[1].getArrayElement(i).asInt() & 0xFF);
            }
            Path p = Path.of(args[0].asString());
            if (append) {
                Files.write(p, bytes, java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND);
            } else {
                Files.write(p, bytes);
            }
            return true; // §382: face BOOL do host (era 0 = falsy no guest)
        } catch (IOException e) {
            return false;
        }
    }

    private static Object dirCreate(Value[] args, boolean recursive) {
        try {
            if (recursive) {
                Files.createDirectories(Path.of(args[0].asString()));
            } else {
                Files.createDirectory(Path.of(args[0].asString()));
            }
            return true; // §382: face BOOL do host (era 0 = falsy no guest)
        } catch (IOException e) {
            return false;
        }
    }

    private static Object dirList(Value[] args) {
        try (var stream = Files.list(Path.of(args[0].asString()))) {
            List<String> names = new ArrayList<>();
            stream.map(p -> p.getFileName().toString()).sorted().forEach(names::add);
            return names.toArray(new String[0]);
        } catch (IOException e) {
            return null;
        }
    }
}
