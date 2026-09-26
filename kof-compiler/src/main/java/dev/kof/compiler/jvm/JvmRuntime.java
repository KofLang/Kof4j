package dev.kof.compiler.jvm;
import dev.kof.compiler.IRClass;
import dev.kof.compiler.Target;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public final class JvmRuntime {

    private JvmRuntime() {}

public static boolean hasRuntimeFn(String methodName) {
        return methodName.startsWith("kof_json_")
                || methodName.startsWith("kof_io_")
                || methodName.startsWith("kof_web_")
                || methodName.startsWith("kof_config_")
                || methodName.startsWith("kof_cache_")
                || methodName.startsWith("kof_buffer_")
                || methodName.startsWith("kof_log_")
                || methodName.startsWith("kof_db_")
                || methodName.startsWith("kof_orm_")
                || methodName.startsWith("kof_string_to_")
                || methodName.startsWith("kof_ui_")
                || methodName.startsWith("kof_sec_")
                || methodName.startsWith("kof_validation_")
                || methodName.startsWith("kof_math_")
                || methodName.startsWith("kof_strings_")
                || methodName.startsWith("kof_encoding_")
                || methodName.startsWith("kof_net_")
                || methodName.startsWith("kof_uuid_")
                || methodName.startsWith("kof_random_")
                || methodName.startsWith("kof_rng_")
                || methodName.startsWith("kof_enum_")
                || methodName.equals("kof_spawn_result") || methodName.equals("kof_await")
                || methodName.equals("kof_poll") || methodName.equals("kof_done")
                || methodName.equals("kof_cancel") || methodName.equals("kof_cancelled")
                || methodName.equals("kof_await_timeout")
                || methodName.equals("kof_select_any")
                // §388-B: display de array cru no formato de container.
                || methodName.equals("kof_array_to_string")
                || methodName.equals("kof_list_map") || methodName.equals("kof_list_filter") || methodName.equals("kof_list_reduce")
                || methodName.startsWith("kof_observability_")
                || methodName.startsWith("kof_media_")
                || methodName.startsWith("kof_tetris_")
                || methodName.startsWith("kof_http_")
                || methodName.startsWith("kof_mq_")
                || methodName.startsWith("kof_time_")
                || methodName.startsWith("kof_vk_")
                || methodName.startsWith("kof_mv64_")
                || methodName.startsWith("kof_scheduler_")
                || methodName.equals("kof_ffi_i")
                || methodName.equals("kof_ffi_si")
                || methodName.equals("kof_ffi_dd")
                || methodName.equals("kof_ffi")
                || methodName.equals("kof_ffi_void")
                || methodName.equals("kof_now")
                || methodName.equals("kof_read_line")
                || methodName.equals("kof_read_file")
                || methodName.equals("kof_write_file")
                || methodName.equals("kof_spawn")
                || methodName.startsWith("kof_spawn_")
                || methodName.equals("kof_process_spawn")
                || methodName.equals("kof_process_run")
                || methodName.equals("kof_process_exit")
                || methodName.equals("kof_shell_argv")
                || methodName.equals("kof_shell_pipeline")
                || methodName.equals("kof_shell_runwith")
                || methodName.equals("kof_ssh_argv")
                || methodName.equals("kof_ssh_run")
                || methodName.equals("kof_args_list");
    }

    static public void ensureCompiled(Path outputDir, List<IRClass> classes, boolean usesVk) throws IOException {
        ensureCompiled(outputDir, classes, usesVk, false);
    }

    static public void ensureCompiled(Path outputDir, List<IRClass> classes, boolean usesVk, boolean usesExtern) throws IOException {
        ensureCompiled(outputDir, classes, usesVk, usesExtern, Target.JVM);
    }

    static public void ensureCompiled(Path outputDir, List<IRClass> classes, boolean usesVk, boolean usesExtern,
            Target target) throws IOException {
        Path runtimeDir = outputDir.resolve("dev/kof/runtime");
        if (Files.exists(runtimeDir.resolve("KofRuntime.class"))) return;
        Files.createDirectories(runtimeDir);
        Path sourceFile = outputDir.resolve("KofRuntime.java");
        Files.writeString(sourceFile, source(classes, usesVk, usesExtern, target));
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IOException("JVM runtime requires a full JDK (javac not available)");
        }
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        // O bloco Vulkan usa FFM (java.lang.foreign). No JDK 21 é preview API:
        // exige --release 21 --enable-preview. No JDK 22+ é API FINAL (JEP 454)
        // e NENHUM flag é necessário — o usuário dessa sessão roda JDK 25, e o
        // caminho antigo quebrava com "invalid source release 21 with
        // --enable-preview" (COMP001). Capability/link-por-uso (R2) mantido:
        // o bloco só entra no source quando o programa realmente chama kof.vk.
        List<String> args = new java.util.ArrayList<>(List.of("-d", outputDir.toString()));
        if ((usesVk || usesExtern) && Runtime.version().feature() < 22) {
            args.add("--release");
            args.add("21");
            args.add("--enable-preview");
        }
        args.add("-classpath");
        args.add(outputDir.toString());
        args.add(sourceFile.toString());
        int rc = compiler.run(null, null, err, args.toArray(new String[0]));
        if (rc != 0) {
            String detail = err.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
            throw new IOException("failed to compile KofRuntime helper (javac exit " + rc + "): "
                    + (detail.isEmpty() ? "unknown error" : detail));
        }
        if (System.getenv("KOF_KEEP_SRC") == null) Files.deleteIfExists(sourceFile);
    }


    private static String source(List<IRClass> classes, boolean usesVk, boolean usesExtern) {
        return source(classes, usesVk, usesExtern, Target.JVM);
    }

    private static String source(List<IRClass> classes, boolean usesVk, boolean usesExtern, Target target) {
        StringBuilder decoders = new StringBuilder();
        java.util.Set<String> emitted = new java.util.HashSet<>();
        for (IRClass clazz : classes) {
            String internal = clazz.name();
            if (internal == null || internal.isBlank() || internal.equals("java/lang/Object")) continue;
            if ("Main".equals(internal) || internal.endsWith("/Main")) continue;
            String javaName = internal.replace('/', '.');
            String mangle = javaName.replace('.', '_');
            // #627: multi-file compilation can surface the same packaged
            // class twice under slash/dot spellings of its internal name —
            // both mangle identically, so a second copy was a javac
            // "already defined" compile error of the generated KofRuntime.
            if (!emitted.add(mangle)) continue;
            decoders.append("""
                        public static Object kof_json_decode_%s(String json) throws Exception {
                            return kof_json_decode_object(json, Class.forName("%s"));
                        }

                    """.formatted(mangle, javaName));
        }
                return sourceCore(decoders.toString())
                + JvmWebRuntime.source()
                + JvmMediaRuntime.source()
                + JvmRuntimeWebServer.source()
                + JvmRuntimeWebDispatch.source()
                + JvmConfigRuntime.source()
                + JvmCacheRuntime.source()
                + JvmBufferRuntime.source()
                + JvmSecretRuntime.source()
                + JvmOrmRuntime.source()
                + JvmTimeRuntime.source()
                + JvmStringRuntime.source()
                + (usesExtern ? JvmFfiRuntime.source() : "")
                + (usesVk
                        ? (target == Target.ANDROID ? JvmVkStubRuntime.source() : JvmVkRuntime.source())
                        : "\n            }");
    }

    private static String sourceCore(String decoders) {
        return JvmRuntimeJson.source(decoders)
                + JvmRuntimeJsonMap.source()
                + JvmRuntimeUi.source()
                + JvmRuntimeUiForms.source()
                + JvmRuntimeCore.source()
                + JvmRuntimeIo.source();
    }
}
