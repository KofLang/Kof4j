package dev.kof.compiler.jvm;

/**
 * FFI (R3, TIER 2.1.4/2.1.6): downcall JVM-first via FFM
 * ({@code java.lang.foreign}) para o target JVM. Mantido fora de
 * {@code JvmRuntime} para a regra de ≤500 linhas/classe.
 */
final class JvmFfiRuntime {

    private JvmFfiRuntime() {}

    static String source() {
        // O source gerado é compilado IN-PROCESS (ToolProvider) pelo MESMO JDK
        // que roda o compilador — e o gate de preview em JvmRuntime usa a mesma
        // condição. JDK 21 (FFM preview): Arena.allocateUtf8String(String);
        // JDK 22+ (FFM final, JEP 454): Arena.allocateFrom(String).
        String alloc = Runtime.version().feature() < 22 ? "allocateUtf8String" : "allocateFrom";
        return FORMATTED.formatted(alloc);
    }

    private static final String FORMATTED = """
                public static int kof_ffi_i(String lib, String name, int a) {
                    java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined();
                    try {
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
                    } finally {
                        arena.close();
                    }
                }

                public static int kof_ffi_si(String lib, String name, String a) {
                    java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined();
                    try {
                        java.lang.foreign.SymbolLookup lookup = lib.isEmpty()
                                ? java.lang.foreign.SymbolLookup.loaderLookup()
                                : java.lang.foreign.SymbolLookup.libraryLookup(lib, arena);
                        java.lang.foreign.Linker linker = java.lang.foreign.Linker.nativeLinker();
                        java.lang.invoke.MethodHandle handle = linker.downcallHandle(
                                lookup.find(name).orElseThrow(),
                                java.lang.foreign.FunctionDescriptor.of(
                                        java.lang.foreign.ValueLayout.JAVA_INT,
                                        java.lang.foreign.ValueLayout.ADDRESS));
                        java.lang.foreign.MemorySegment seg = arena.%1$s(a);
                        return (int) handle.invoke(seg);
                    } catch (Throwable t) {
                        throw new RuntimeException("kof_ffi_si: " + lib + "::" + name + " failed: "
                                + t.getMessage(), t);
                    } finally {
                        arena.close();
                    }
                }

                public static double kof_ffi_dd(String lib, String name, double a) {
                    java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined();
                    try {
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
                    } finally {
                        arena.close();
                    }
                }

                public static Object kof_ffi(String lib, String name, String sig, Object[] args) {
                    java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined();
                    java.util.ArrayList<Buffer> borrowHolds = new java.util.ArrayList<>();
                    try {
                        java.lang.foreign.SymbolLookup lookup = lib.isEmpty()
                                ? java.lang.foreign.SymbolLookup.loaderLookup()
                                : java.lang.foreign.SymbolLookup.libraryLookup(lib, arena);
                        java.lang.foreign.Linker linker = java.lang.foreign.Linker.nativeLinker();
                        char ret = sig.charAt(0);
                        java.lang.foreign.MemoryLayout[] pl =
                                new java.lang.foreign.MemoryLayout[args.length];
                        Object[] real = new Object[args.length];
                        java.util.ArrayList<Object[]> copybacks = new java.util.ArrayList<>();
                        int cur = 1;
                        for (int i = 0; i < args.length; i++) {
                            char c = sig.charAt(cur);
                            if (c == '(') {
                                int j = cur + 1;
                                int depth = 1;
                                StringBuilder inner = new StringBuilder();
                                while (depth > 0) {
                                    char x = sig.charAt(j);
                                    if (x == '(') { depth++; inner.append(x); }
                                    else if (x == ')') { depth--; if (depth > 0) inner.append(x); }
                                    else inner.append(x);
                                    j++;
                                }
                                cur = j;
                                pl[i] = java.lang.foreign.ValueLayout.ADDRESS;
                                real[i] = kof_ffi_upcall(linker, arena, args[i], inner.toString());
                            } else if (c == '@') {
                                // D6-1(A)/3.8b: `record` Kof -> struct C por valor. O
                                // layout e os valores vêm da própria classe do argumento
                                // (RecordComponent); a arena confinada da chamada é a dona
                                // da memória do struct (D6-5).
                                cur++;
                                java.lang.foreign.StructLayout sl = kof_ffi_struct_layout(args[i]);
                                pl[i] = sl;
                                java.lang.foreign.MemorySegment sseg = arena.allocate(sl);
                                kof_ffi_write_struct(sl, sseg, args[i]);
                                real[i] = sseg;
                            } else if (c == 'p') {
                                // D6-2 (3.8b fatia 3): array Kof -> ptr C. Copy-in
                                // para memoria nativa na arena da chamada (o C nao
                                // ve nem altera o array Java — sem pinning).
                                char e = sig.charAt(cur + 1);
                                cur += 2;
                                pl[i] = java.lang.foreign.ValueLayout.ADDRESS;
                                // D-MEM-FFI-CROSS-FULL face 2: `String[]` -> `char**`
                                // (cada elemento vira um cstr NUL-terminado na arena).
                                real[i] = (e == 'S')
                                        ? kof_ffi_copy_in_strings(arena, args[i])
                                        : kof_ffi_copy_in(arena, args[i], e);
                            } else if (c == 'B') {
                                // D6-3 / D-R3-BUFFER: Buffer(U8) INOUT — copy-in
                                // para a arena da chamada; o copy-back acontece
                                // depois do retorno (a vida do buffer e da
                                // linguagem, nunca malloc/free do programador).
                                cur++;
                                pl[i] = java.lang.foreign.ValueLayout.ADDRESS;
                                java.lang.foreign.MemorySegment bseg =
                                        kof_ffi_buffer_in(arena, args[i]);
                                real[i] = bseg;
                                copybacks.add(new Object[] { args[i], bseg });
                                // MEM020 runtime: exclusive writable borrow for
                                // the duration of the FFI call.
                                kof_buffer_borrow_acquire((Buffer) args[i]);
                                borrowHolds.add((Buffer) args[i]);
                            } else {
                                cur++;
                                pl[i] = kof_ffi_layout(c);
                                real[i] = (c == 'S') ? arena.%1$s((String) args[i]) : args[i];
                            }
                        }
                        java.lang.foreign.StructLayout retStruct = null;
                        Class<?> retClass = null;
                        if (ret == '@') {
                            // 3.8b fatia 2: retorno struct por valor — o nome binário
                            // do record veio como sufixo `:` do sig.
                            String cls = sig.substring(cur);
                            if (cls.startsWith(":")) cls = cls.substring(1);
                            retClass = Class.forName(cls);
                            retStruct = kof_ffi_struct_layout_of(retClass);
                        }
                        java.lang.foreign.FunctionDescriptor fd = (ret == 'v')
                                ? java.lang.foreign.FunctionDescriptor.ofVoid(pl)
                                : (ret == '@')
                                        ? java.lang.foreign.FunctionDescriptor.of(retStruct, pl)
                                        : java.lang.foreign.FunctionDescriptor.of(kof_ffi_layout(ret), pl);
                        java.lang.invoke.MethodHandle handle = linker.downcallHandle(
                                lookup.find(name).orElseThrow(), fd);
                        if (ret == '@') {
                            // Retorno struct por valor: o handle do Linker recebe um
                            // SegmentAllocator à frente p/ materializar o struct
                            // devolvido (lido logo abaixo, antes de fechar a arena).
                            handle = handle.bindTo(arena);
                        }
                        handle = handle.asSpreader(Object[].class, args.length);
                        Object r = handle.invoke(real);
                        for (Object[] cb : copybacks) {
                            kof_ffi_buffer_out(cb[0],
                                    (java.lang.foreign.MemorySegment) cb[1]);
                        }
                        if (ret == 'v') return null;
                        if (ret == '@') {
                            return kof_ffi_read_struct(retStruct,
                                    (java.lang.foreign.MemorySegment) r, retClass);
                        }
                        if (ret == 'S') {
                            java.lang.foreign.MemorySegment seg = (java.lang.foreign.MemorySegment) r;
                            if (seg == null || seg.address() == 0L) {
                                return null;
                            }
                            return seg.reinterpret(Long.MAX_VALUE).getString(0L);
                        }
                        return r;
                    } catch (Throwable t) {
                        throw new RuntimeException("kof_ffi: " + lib + "::" + name + " (" + sig + ") failed: "
                                + t.getMessage(), t);
                    } finally {
                        for (Buffer bh : borrowHolds) kof_buffer_borrow_release(bh);
                        arena.close();
                    }
                }

                public static void kof_ffi_void(String lib, String name, String sig, Object[] args) {
                    kof_ffi(lib, name, sig, args);
                }

                static java.lang.foreign.ValueLayout kof_ffi_layout(char c) {
                    return switch (c) {
                        case 'i' -> java.lang.foreign.ValueLayout.JAVA_INT;
                        case 'j' -> java.lang.foreign.ValueLayout.JAVA_LONG;
                        case 'f' -> java.lang.foreign.ValueLayout.JAVA_FLOAT;
                        case 'd' -> java.lang.foreign.ValueLayout.JAVA_DOUBLE;
                        case 'b' -> java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
                        case 'S' -> java.lang.foreign.ValueLayout.ADDRESS;
                        default -> throw new IllegalArgumentException("bad ffi layout char: " + c);
                    };
                }

                // D6-1(A)/3.8b: layout FFM de um `record` Kof com campos escalares
                // (numérico/bool). O struct atravessa POR VALOR (o Linker classifica
                // pela StructLayout) — sem refatorar a ABI escalar.
                static java.lang.foreign.StructLayout kof_ffi_struct_layout(Object rec) {
                    return kof_ffi_struct_layout_of(rec.getClass());
                }

                static java.lang.foreign.StructLayout kof_ffi_struct_layout_of(Class<?> cls) {
                    java.lang.reflect.RecordComponent[] cs = cls.getRecordComponents();
                    java.lang.foreign.MemoryLayout[] ls =
                            new java.lang.foreign.MemoryLayout[cs.length];
                    for (int k = 0; k < cs.length; k++) {
                        ls[k] = kof_ffi_field_layout(cs[k].getType());
                    }
                    java.lang.foreign.StructLayout sl =
                            java.lang.foreign.MemoryLayout.structLayout(ls);
                    // A ABI C paddinga o struct até o alinhamento do maior membro;
                    // o FFM exige que o layout tenha esse tamanho exato (ex.
                    // {double,int} = 16, não 12). Fecha com padding explícito.
                    long align = sl.byteAlignment();
                    long size = sl.byteSize();
                    long padded = (size + align - 1) / align * align;
                    if (padded == size) return sl;
                    java.lang.foreign.MemoryLayout[] lp =
                            new java.lang.foreign.MemoryLayout[ls.length + 1];
                    System.arraycopy(ls, 0, lp, 0, ls.length);
                    lp[ls.length] = java.lang.foreign.MemoryLayout.paddingLayout(padded - size);
                    return java.lang.foreign.MemoryLayout.structLayout(lp);
                }

                // 3.8b fatia 2: lê o struct devolvido POR VALOR e reconstrói o
                // `record` Kof pelo construtor canônico (componentes na ordem do
                // RecordComponent — a mesma ordem do layout). Leitura imediata: o
                // segmento devolvido pelo Linker só é válido logo após o downcall.
                static Object kof_ffi_read_struct(java.lang.foreign.StructLayout sl,
                        java.lang.foreign.MemorySegment seg, Class<?> cls) throws Throwable {
                    java.lang.reflect.RecordComponent[] cs = cls.getRecordComponents();
                    Class<?>[] types = new Class<?>[cs.length];
                    Object[] vals = new Object[cs.length];
                    for (int k = 0; k < cs.length; k++) {
                        types[k] = cs[k].getType();
                        long off = sl.byteOffset(
                                java.lang.foreign.MemoryLayout.PathElement.groupElement(k));
                        vals[k] = kof_ffi_read_field(seg, off, types[k]);
                    }
                    java.lang.reflect.Constructor<?> ctor = cls.getDeclaredConstructor(types);
                    ctor.setAccessible(true);
                    return ctor.newInstance(vals);
                }

                static Object kof_ffi_read_field(java.lang.foreign.MemorySegment seg,
                        long off, Class<?> t) {
                    if (t == int.class || t == Integer.class) {
                        return seg.get(java.lang.foreign.ValueLayout.JAVA_INT, off);
                    }
                    if (t == long.class || t == Long.class) {
                        return seg.get(java.lang.foreign.ValueLayout.JAVA_LONG, off);
                    }
                    if (t == float.class || t == Float.class) {
                        return seg.get(java.lang.foreign.ValueLayout.JAVA_FLOAT, off);
                    }
                    if (t == double.class || t == Double.class) {
                        return seg.get(java.lang.foreign.ValueLayout.JAVA_DOUBLE, off);
                    }
                    if (t == boolean.class || t == Boolean.class) {
                        return seg.get(java.lang.foreign.ValueLayout.JAVA_BOOLEAN, off);
                    }
                    throw new IllegalArgumentException("ffi struct: unsupported field type " + t);
                }

                static java.lang.foreign.MemoryLayout kof_ffi_field_layout(Class<?> t) {
                    if (t == int.class || t == Integer.class) return java.lang.foreign.ValueLayout.JAVA_INT;
                    if (t == long.class || t == Long.class) return java.lang.foreign.ValueLayout.JAVA_LONG;
                    if (t == float.class || t == Float.class) return java.lang.foreign.ValueLayout.JAVA_FLOAT;
                    if (t == double.class || t == Double.class) return java.lang.foreign.ValueLayout.JAVA_DOUBLE;
                    if (t == boolean.class || t == Boolean.class) return java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
                    throw new IllegalArgumentException("ffi struct: unsupported field type " + t);
                }

                static void kof_ffi_write_struct(java.lang.foreign.StructLayout sl,
                        java.lang.foreign.MemorySegment seg, Object rec) throws Throwable {
                    java.lang.reflect.RecordComponent[] cs = rec.getClass().getRecordComponents();
                    for (int k = 0; k < cs.length; k++) {
                        Object v = cs[k].getAccessor().invoke(rec);
                        long off = sl.byteOffset(java.lang.foreign.MemoryLayout.PathElement.groupElement(k));
                        Class<?> t = cs[k].getType();
                        if (t == int.class || t == Integer.class) {
                            seg.set(java.lang.foreign.ValueLayout.JAVA_INT, off, ((Integer) v).intValue());
                        } else if (t == long.class || t == Long.class) {
                            seg.set(java.lang.foreign.ValueLayout.JAVA_LONG, off, ((Long) v).longValue());
                        } else if (t == float.class || t == Float.class) {
                            seg.set(java.lang.foreign.ValueLayout.JAVA_FLOAT, off, ((Float) v).floatValue());
                        } else if (t == double.class || t == Double.class) {
                            seg.set(java.lang.foreign.ValueLayout.JAVA_DOUBLE, off, ((Double) v).doubleValue());
                        } else if (t == boolean.class || t == Boolean.class) {
                            seg.set(java.lang.foreign.ValueLayout.JAVA_BOOLEAN, off, ((Boolean) v).booleanValue());
                        } else {
                            throw new IllegalArgumentException("ffi struct: unsupported field type " + t);
                        }
                    }
                }

                // D6-2 (3.8b fatia 3): copia um array Kof (array Java primitivo)
                // para um segmento nativo da arena da chamada e devolve o ponteiro.
                static java.lang.foreign.MemorySegment kof_ffi_copy_in(
                        java.lang.foreign.Arena arena, Object arr, char e) {
                    int n = java.lang.reflect.Array.getLength(arr);
                    if (e == 'b') {
                        // `boolean[]` não é suportado por MemorySegment.copy (só
                        // byte[]/char[]/short[]/int[]/float[]/long[]/double[]) —
                        // converte para byte[] 0/1 antes de copiar (paridade _Bool).
                        byte[] tmp = new byte[n];
                        boolean[] src = (boolean[]) arr;
                        for (int i = 0; i < n; i++) tmp[i] = (byte) (src[i] ? 1 : 0);
                        java.lang.foreign.MemorySegment seg = arena.allocate(
                                java.lang.foreign.ValueLayout.JAVA_BYTE, n);
                        java.lang.foreign.MemorySegment.copy(tmp, 0, seg,
                                java.lang.foreign.ValueLayout.JAVA_BYTE, 0, n);
                        return seg;
                    }
                    java.lang.foreign.ValueLayout vl = kof_ffi_layout(e);
                    java.lang.foreign.MemorySegment seg = arena.allocate(vl, n);
                    java.lang.foreign.MemorySegment.copy(arr, 0, seg, vl, 0, n);
                    return seg;
                }

                // D-MEM-FFI-CROSS-FULL face 2: `String[]` -> `char**`. Cada String
                // vira um cstr NUL-terminado na arena da chamada (mesma forma do
                // escalar 'S') e um array de ADDRESS recebe os ponteiros; null -> 0.
                static java.lang.foreign.MemorySegment kof_ffi_copy_in_strings(
                        java.lang.foreign.Arena arena, Object arr) {
                    int n = java.lang.reflect.Array.getLength(arr);
                    java.lang.foreign.MemorySegment seg = arena.allocate(
                            java.lang.foreign.ValueLayout.ADDRESS, n == 0 ? 1 : n);
                    for (int i = 0; i < n; i++) {
                        String s = (String) java.lang.reflect.Array.get(arr, i);
                        if (s != null) {
                            seg.setAtIndex(java.lang.foreign.ValueLayout.ADDRESS, i,
                                    arena.%1$s(s));
                        }
                    }
                    return seg;
                }

                // D6-3 / D-R3-BUFFER: Buffer(U8) como param INOUT de um extern.
                // copy-in: os bytes do buffer viram memoria nativa na arena da
                // chamada; copy-back: apos o retorno, a memoria volta para o
                // byte[] do buffer (a vida e gerenciada pela linguagem).
                static java.lang.foreign.MemorySegment kof_ffi_buffer_in(
                        java.lang.foreign.Arena arena, Object buf) {
                    byte[] data = ((Buffer) buf).data;
                    java.lang.foreign.MemorySegment seg = arena.allocate(
                            java.lang.foreign.ValueLayout.JAVA_BYTE, data.length);
                    java.lang.foreign.MemorySegment src =
                            java.lang.foreign.MemorySegment.ofArray(data);
                    java.lang.foreign.MemorySegment.copy(src, 0L, seg, 0L, data.length);
                    return seg;
                }

                static void kof_ffi_buffer_out(
                        Object buf, java.lang.foreign.MemorySegment seg) {
                    byte[] data = ((Buffer) buf).data;
                    java.lang.foreign.MemorySegment dst =
                            java.lang.foreign.MemorySegment.ofArray(data);
                    java.lang.foreign.MemorySegment.copy(seg, 0L, dst, 0L, data.length);
                }

                // Callback/upcall (R3, 3.4): um valor de função Kof (objeto que
                // implementa a interface sintética `invoke(...)`) vira ponteiro de
                // função C. A interface do Kof é ESPECIALIZADA (ex. int invoke(int,int)),
                // então o `unreflect` já dá um MethodHandle de carrier unboxed; o
                // `.asType(mt)` é o bridge (no-op quando o tipo bate). O stub vive na
                // arena da chamada → só callback SÍNCRONO/não-escapante (medido em C1).
                static java.lang.foreign.MemorySegment kof_ffi_upcall(
                        java.lang.foreign.Linker linker, java.lang.foreign.Arena arena,
                        Object closure, String inner) throws Throwable {
                    char rb = inner.charAt(0);
                    int arity = inner.length() - 1;
                    java.lang.foreign.MemoryLayout[] il =
                            new java.lang.foreign.MemoryLayout[arity];
                    // carrier NATIVO do stub (ADDRESS -> MemorySegment) e carrier do
                    // invoke Kof (String -> java.lang.String). Só divergem nos 'S'.
                    Class<?>[] up = new Class<?>[arity];
                    Class<?>[] cb = new Class<?>[arity];
                    int[] strPos = new int[arity];
                    int ns = 0;
                    for (int k = 0; k < arity; k++) {
                        char c = inner.charAt(k + 1);
                        il[k] = kof_ffi_layout(c);
                        if (c == 'S') {
                            up[k] = java.lang.foreign.MemorySegment.class;
                            cb[k] = String.class;
                            strPos[ns++] = k;
                        } else {
                            up[k] = kof_ffi_carrier(c);
                            cb[k] = kof_ffi_carrier(c);
                        }
                    }
                    Class<?> rret = kof_ffi_carrier(rb);
                    java.lang.invoke.MethodType cbMt =
                            java.lang.invoke.MethodType.methodType(rret, cb);
                    java.lang.invoke.MethodType upMt =
                            java.lang.invoke.MethodType.methodType(rret, up);
                    java.lang.reflect.Method m = null;
                    for (java.lang.reflect.Method cand : closure.getClass().getMethods()) {
                        if (cand.getName().equals("invoke")
                                && cand.getParameterCount() == arity) {
                            m = cand;
                            break;
                        }
                    }
                    if (m == null) {
                        throw new RuntimeException("kof_ffi: callback "
                                + closure.getClass().getName()
                                + " has no arity-" + arity + " invoke()");
                    }
                    java.lang.invoke.MethodHandles.Lookup lookup =
                            java.lang.invoke.MethodHandles.lookup();
                    java.lang.invoke.MethodHandle mh =
                            lookup.unreflect(m).bindTo(closure).asType(cbMt);
                    if (ns > 0) {
                        // fronteira ADDRESS->String do upcall: o C entrega um `char*`;
                        // o invoke Kof deve ver um String (reinterpret+getString, como
                        // no downcall). Sem filtro p/ callback só-primitivo (no-op).
                        java.lang.invoke.MethodHandle cstr = lookup.findStatic(
                                lookup.lookupClass(), "kof_ffi_cstr",
                                java.lang.invoke.MethodType.methodType(String.class,
                                        java.lang.foreign.MemorySegment.class));
                        for (int p = 0; p < ns; p++) {
                            mh = java.lang.invoke.MethodHandles.filterArguments(mh, strPos[p], cstr);
                        }
                    }
                    mh = mh.asType(upMt);
                    java.lang.foreign.FunctionDescriptor fd = (rb == 'v')
                            ? java.lang.foreign.FunctionDescriptor.ofVoid(il)
                            : java.lang.foreign.FunctionDescriptor.of(kof_ffi_layout(rb), il);
                    return linker.upcallStub(mh, fd, arena);
                }

                // char* (ADDRESS) entregue por C no callback -> String UTF-8; NULL vira
                // null (nunca um segfault silencioso). Espelha a leitura do downcall.
                public static String kof_ffi_cstr(java.lang.foreign.MemorySegment seg) {
                    if (seg == null || seg.address() == 0L) return null;
                    return seg.reinterpret(java.lang.Long.MAX_VALUE).getString(0L);
                }

                static Class<?> kof_ffi_carrier(char c) {
                    return switch (c) {
                        case 'i' -> int.class;
                        case 'j' -> long.class;
                        case 'f' -> float.class;
                        case 'd' -> double.class;
                        case 'b' -> boolean.class;
                        case 'v' -> void.class;
                        default -> throw new IllegalArgumentException("bad ffi carrier char: " + c);
                    };
                }

    """;
}