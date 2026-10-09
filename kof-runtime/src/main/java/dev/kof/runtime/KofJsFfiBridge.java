package dev.kof.runtime;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * FFI (R3, fatia 3.6-F1): downcall no host do runner GraalJS/node — o runner É uma
 * JVM com {@code java.lang.foreign}, então o mesmo bridge que o target JVM usa pode
 * servir o alvo JS (browser não pode {@code dlopen} → fica {@code FFI002} honesto, R7).
 *
 * <p>Paridade por construção com o runtime JVM gerado por
 * {@code JvmFfiRuntime.kof_ffi}: MESMA codificação de assinatura (chars
 * {@code i/j/f/d/b/S} de parâmetro e {@code v}/{@code i}/{@code j}/{@code f}/{@code d}/
 * {@code b}/{@code S} de retorno), MESMO {@code ValueLayout}, mesmo {@code asSpreader}
 * sobre o {@code Object[]} boxado e mesma leitura de {@code char*} de volta
 * ({@code getString} após {@code reinterpret} com guard de NULL). Uma String
 * retornada é lida do ponteiro nativo; {@code void} devolve {@code null}.
 *
 * <p><b>Estado 21/09:</b> o gate do JS está aberto para escalares + callbacks
 * (3.4-C3) e, desde o bridge de struct (D6-1/3.8b), para {@code record} de campos
 * escalares passado POR VALOR — token {@code @<n><chars>} ({@link #structCharsAt}):
 * o {@code KofJsFfiMarshal} empacota os campos (via {@code __kof_ffi_fields} do
 * record) num {@code MemorySegment} da arena da chamada (D6-5) e aqui o
 * {@code StructLayout} entra no descriptor, idêntico ao caminho reflexivo do
 * {@code JvmFfiRuntime}. Desde 21/09 o array escalar {@code T[]} (token
 * {@code p<elem>}, D6-2) e o out-buffer {@code Buffer(U8)} INOUT (token
 * {@code B}, D6-3) também entram por ponteiro {@code ADDRESS}. Desde 21/09 o
 * struct de RETORNO também binda: o token {@code @<n><chars>} no índice 0 traz o
 * layout, o {@link #call} materializa o struct devolvido por valor na arena da
 * chamada e devolve os campos (ordem de declaração) num {@code Object[]} que o
 * guest reconstrói via {@code __kof_ffi_from} — paridade com o
 * {@code kof_ffi_read_struct} do JVM. O gate cobre JVM+JS; Native fica
 * {@code FFI001} (R6, nunca stub silencioso).
 */
public final class KofJsFfiBridge {

    private KofJsFfiBridge() {
    }

    /**
     * The native library is loaded ONCE per path and its lookup is reused across
     * calls. The old code loaded it into the per-call confined {@link Arena},
     * whose {@code close()} released the library and reset its globals, so a
     * stateful C library lost its state between Kof calls on the JS host — while
     * Native (link-by-use) persists it. The lookup arena is shared and never
     * closed; the per-call arena still owns the argument/return memory.
     */
    private static final java.util.Map<String, SymbolLookup> LOOKUPS =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static SymbolLookup lookupFor(String lib) {
        if (lib == null || lib.isEmpty()) {
            return SymbolLookup.loaderLookup();
        }
        return LOOKUPS.computeIfAbsent(lib, l -> SymbolLookup.libraryLookup(l, Arena.ofShared()));
    }

    public static Object call(String lib, String name, String sig, Object[] args) {
        Arena arena = Arena.ofConfined();
        try {
            SymbolLookup lookup = lookupFor(lib);
            Linker linker = Linker.nativeLinker();
            char ret = sig.charAt(0);
            StructLayout retStruct = null;
            String retChars = null;
            int cur = 1;
            if (ret == '@') {
                // Retorno struct por valor: o sig JS carrega os chars do layout
                // (`@<n><chars>`); o cursor de params começa depois do token.
                int[] ref = { 0 };
                retChars = structCharsAt(sig, ref);
                cur = ref[0];
                retStruct = structLayout(retChars);
            }
            MemoryLayout[] pl = new MemoryLayout[args.length];
            Object[] real = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                char c = sig.charAt(cur);
                if (c == '(') {
                    // callback (R3, 3.4-C3): token "(<ret><params>)" aninhado; o stub
                    // MemorySegment já vem pronto em args[i] (montado pelo KofJsRunner).
                    int j = cur + 1;
                    int depth = 1;
                    while (depth > 0) {
                        char x = sig.charAt(j);
                        if (x == '(') depth++;
                        else if (x == ')') depth--;
                        j++;
                    }
                    cur = j;
                    pl[i] = ValueLayout.ADDRESS;
                    real[i] = args[i];
                } else if (c == 'p') {
                    // D6-2/3.8b fatia 3 (bridge JS): array escalar -> `ptr` C; o
                    // Marshal já copiou os elementos para a arena da chamada.
                    cur += 2;
                    pl[i] = ValueLayout.ADDRESS;
                    real[i] = args[i];
                } else if (c == 'B') {
                    // D6-3/D-R3-BUFFER (bridge JS 21/09): `Buffer(U8)` INOUT -> `ptr`
                    // C; o Marshal copiou os bytes para a arena da chamada e faz o
                    // copy-back depois do downcall (espelho de kof_ffi_buffer_in/out).
                    cur++;
                    pl[i] = ValueLayout.ADDRESS;
                    real[i] = args[i];
                } else if (c == '@') {
                    // D6-1/3.8b (bridge JS 21/09): `record` Kof -> struct C por
                    // valor. O Marshal JÁ empacotou os campos num MemorySegment
                    // da arena da chamada (D6-5); aqui só o layout entra no
                    // descriptor — espelho do `pl[i] = sl` do JvmFfiRuntime.
                    int[] ref = { cur };
                    String chars = structCharsAt(sig, ref);
                    cur = ref[0];
                    pl[i] = structLayout(chars);
                    real[i] = args[i];
                } else {
                    cur++;
                    pl[i] = layout(c);
                    real[i] = (c == 'S') ? arena.allocateFrom((String) args[i]) : args[i];
                }
            }
            FunctionDescriptor fd = (ret == 'v')
                    ? FunctionDescriptor.ofVoid(pl)
                    : (ret == '@')
                            ? FunctionDescriptor.of(retStruct, pl)
                            : FunctionDescriptor.of(layout(ret), pl);
            MethodHandle handle = linker.downcallHandle(
                    lookup.find(name).orElseThrow(), fd);
            if (ret == '@') {
                // Struct devolvido por valor: o Linker materializa na arena da
                // chamada (arg SegmentAllocator à frente) — lido logo abaixo.
                handle = handle.bindTo(arena);
            }
            handle = handle.asSpreader(Object[].class, args.length);
            Object r = handle.invoke(real);
            if (ret == 'v') {
                return null;
            }
            if (ret == '@') {
                return readStruct(retStruct, (MemorySegment) r, retChars);
            }
            if (ret == 'S') {
                MemorySegment seg = (MemorySegment) r;
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
            arena.close();
        }
    }

    public static void callVoid(String lib, String name, String sig, Object[] args) {
        call(lib, name, sig, args);
    }

    /**
     * Lê os campos do struct devolvido por valor (ordem de declaração) num
     * {@code Object[]} — o guest reconstrói o record via {@code __kof_ffi_from}
     * (paridade com o {@code kof_ffi_read_struct} reflexivo do JVM). Os offsets
     * vêm do MESMO {@link #structLayout} que gerou o descriptor.
     */
    private static Object[] readStruct(StructLayout sl, MemorySegment seg, String chars) {
        Object[] out = new Object[chars.length()];
        for (int k = 0; k < chars.length(); k++) {
            long off = sl.byteOffset(MemoryLayout.PathElement.groupElement(k));
            out[k] = switch (chars.charAt(k)) {
                case 'i' -> seg.get(ValueLayout.JAVA_INT, off);
                case 'j' -> seg.get(ValueLayout.JAVA_LONG, off);
                case 'f' -> seg.get(ValueLayout.JAVA_FLOAT, off);
                case 'd' -> seg.get(ValueLayout.JAVA_DOUBLE, off);
                case 'b' -> seg.get(ValueLayout.JAVA_BOOLEAN, off);
                default -> throw new IllegalArgumentException(
                        "ffi: bad struct return field char: " + chars.charAt(k));
            };
        }
        return out;
    }

    static ValueLayout layout(char c) {
        return switch (c) {
            case 'i' -> ValueLayout.JAVA_INT;
            case 'j' -> ValueLayout.JAVA_LONG;
            case 'f' -> ValueLayout.JAVA_FLOAT;
            case 'd' -> ValueLayout.JAVA_DOUBLE;
            case 'b' -> ValueLayout.JAVA_BOOLEAN;
            case 'S' -> ValueLayout.ADDRESS;
            default -> throw new IllegalArgumentException("bad ffi layout char: " + c);
        };
    }

    /**
     * Token de struct por valor no sig do JS: {@code '@'} + TAMANHO decimal +
     * chars do layout (ex. {@code @2ij}) — o prefixo de tamanho elimina a
     * ambiguidade com o escalar seguinte. Avança {@code cur[0]} para depois do
     * token. O retorno {@code @:Nome} nunca chega aqui (o gate do JS mantém
     * struct de retorno em {@code FFI002}).
     */
    static String structCharsAt(String sig, int[] cur) {
        int j = cur[0] + 1;
        int len = 0;
        while (j < sig.length() && Character.isDigit(sig.charAt(j))) {
            len = len * 10 + (sig.charAt(j) - '0');
            j++;
        }
        if (len <= 0 || j + len > sig.length()) {
            throw new IllegalArgumentException("bad ffi struct token at " + cur[0] + ": " + sig);
        }
        cur[0] = j + len;
        return sig.substring(j, j + len);
    }

    /**
     * StructLayout C a partir dos chars dos campos — mesmos ValueLayout do JVM,
     * inclusive o padding de cauda: a ABI C arredonda o struct até o alinhamento
     * do maior membro e o FFM exige esse tamanho exato (ex. {long,double,int} =
     * 24, não 20) — espelho de {@code kof_ffi_struct_layout_of} do JVM.
     */
    static StructLayout structLayout(String chars) {
        MemoryLayout[] members = new MemoryLayout[chars.length()];
        for (int k = 0; k < chars.length(); k++) {
            members[k] = layout(chars.charAt(k));
        }
        StructLayout sl = MemoryLayout.structLayout(members);
        long align = sl.byteAlignment();
        long size = sl.byteSize();
        long padded = (size + align - 1) / align * align;
        if (padded == size) return sl;
        MemoryLayout[] withPad = new MemoryLayout[members.length + 1];
        System.arraycopy(members, 0, withPad, 0, members.length);
        withPad[members.length] = MemoryLayout.paddingLayout(padded - size);
        return MemoryLayout.structLayout(withPad);
    }
}
