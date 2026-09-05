package dev.kof.compiler;

/**
 * FFI (R3, TIER 2.1.4/2.1.6): downcall JVM-first via FFM
 * ({@code java.lang.foreign}) para o target JVM. Mantido fora de
 * {@code JvmRuntime} para a regra de ≤500 linhas/classe.
 */
final class JvmFfiRuntime {

    private JvmFfiRuntime() {}

    static String source() {
        return """
                public static int kof_ffi_i(String lib, String name, int a) {
                    try {
                        java.lang.foreign.Arena arena = java.lang.foreign.Arena.global();
                        java.lang.foreign.SymbolLookup lookup = lib.isEmpty()
                                ? java.lang.foreign.SymbolLookup.loaderLookup()
                                : java.lang.foreign.SymbolLookup.libraryLookup(lib, arena);
                        java.lang.foreign.Linker linker = java.lang.foreign.Linker.nativeLinker();
                        java.lang.invoke.MethodHandle handle = linker.downcallHandle(
                                lookup.find(name).orElseThrow(),
                                java.lang.foreign.FunctionDescriptor.of(
                                        java.lang.foreign.ValueLayout.JAVA_INT,
                                        java.lang.foreign.ValueLayout.JAVA_INT));
                        return (int) handle.invoke(a);
                    } catch (Throwable t) {
                        throw new RuntimeException("kof_ffi_i: " + lib + "::" + name + " failed: "
                                + t.getMessage(), t);
                    }
                }

                public static int kof_ffi_si(String lib, String name, String a) {
                    try {
                        java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined();
                        java.lang.foreign.SymbolLookup lookup = lib.isEmpty()
                                ? java.lang.foreign.SymbolLookup.loaderLookup()
                                : java.lang.foreign.SymbolLookup.libraryLookup(lib, arena);
                        java.lang.foreign.Linker linker = java.lang.foreign.Linker.nativeLinker();
                        java.lang.invoke.MethodHandle handle = linker.downcallHandle(
                                lookup.find(name).orElseThrow(),
                                java.lang.foreign.FunctionDescriptor.of(
                                        java.lang.foreign.ValueLayout.JAVA_INT,
                                        java.lang.foreign.ValueLayout.ADDRESS));
                        java.lang.foreign.MemorySegment seg = arena.allocateUtf8String(a);
                        return (int) handle.invoke(seg);
                    } catch (Throwable t) {
                        throw new RuntimeException("kof_ffi_si: " + lib + "::" + name + " failed: "
                                + t.getMessage(), t);
                    }
                }

                public static double kof_ffi_dd(String lib, String name, double a) {
                    try {
                        java.lang.foreign.Arena arena = java.lang.foreign.Arena.global();
                        java.lang.foreign.SymbolLookup lookup = lib.isEmpty()
                                ? java.lang.foreign.SymbolLookup.loaderLookup()
                                : java.lang.foreign.SymbolLookup.libraryLookup(lib, arena);
                        java.lang.foreign.Linker linker = java.lang.foreign.Linker.nativeLinker();
                        java.lang.invoke.MethodHandle handle = linker.downcallHandle(
                                lookup.find(name).orElseThrow(),
                                java.lang.foreign.FunctionDescriptor.of(
                                        java.lang.foreign.ValueLayout.JAVA_DOUBLE,
                                        java.lang.foreign.ValueLayout.JAVA_DOUBLE));
                        return (double) handle.invoke(a);
                    } catch (Throwable t) {
                        throw new RuntimeException("kof_ffi_dd: " + lib + "::" + name + " failed: "
                                + t.getMessage(), t);
                    }
                }

    """;
    }
}