package dev.kof.runtime;

import org.graalvm.polyglot.Value;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * R3 (3.6 + 3.4-C3): marshalling do lado do host JS entre o array de args do runner
 * GraalJS e o downcall FFM em {@link KofJsFfiBridge}. Vive separado do {@code KofJsRunner}
 * (que já é grande) — o {@code ProxyExecutable} só delega. A mesma ABI escalar do target
 * JVM ({@code kof_ffi}), com o_acréscimo de callbacks/upcalls: um parâmetro de tipo-função
 * vira um ponteiro de função C real via {@code Linker.upcallStub}.
 *
 * <p>Callback (3.4-C3): um valor de função Kof compilado para JS NÃO é uma arrow nativa —
 * é um objeto {@code Lambda…} com um método {@code invoke}; a ponte do stub chama
 * {@code fn.getMember("invoke").execute(...)}. Contrato síncrono/não-escapante (o stub vive
 * na {@code Arena.ofConfined()} da chamada, fechada assim que o downcall retorna).
 */
final class KofJsFfiMarshal {

    private KofJsFfiMarshal() {
    }

    /** Buffer INOUT: liga o `Uint8Array` do guest ao segmento da chamada. `obj`
     *  é o próprio `KofBufferBox` (dono do estado de borrow — B-03). */
    private record OutBuf(Value obj, Value data, MemorySegment seg, int len) {}

    /** Downcall com retorno: abre a arena confined dos stubs e chama o bridge. */
    static Object ffi(String lib, String name, String sig, Value jsArgs) {
        try (Arena stubArena = Arena.ofConfined()) {
            java.util.List<OutBuf> outs = new java.util.ArrayList<>();
            Object[] real = args(sig, jsArgs, stubArena, outs);
            acquireBorrows(outs);
            try {
                Object r = KofJsFfiBridge.call(lib, name, sig, real);
                copyBack(outs);
                return r;
            } finally {
                releaseBorrows(outs);
            }
        }
    }

    /** Downcall void (statement): idem, descarta o retorno. */
    static void ffiVoid(String lib, String name, String sig, Value jsArgs) {
        try (Arena stubArena = Arena.ofConfined()) {
            java.util.List<OutBuf> outs = new java.util.ArrayList<>();
            Object[] real = args(sig, jsArgs, stubArena, outs);
            acquireBorrows(outs);
            try {
                KofJsFfiBridge.callVoid(lib, name, sig, real);
                copyBack(outs);
            } finally {
                releaseBorrows(outs);
            }
        }
    }

    /**
     * D-MEM030-BORROW-RUNTIME (JS face): o JS é cooperativo (spawn = async/Promise),
     * então dois downcalls nunca se sobrepõem — a corrida negativa é
     * estruturalmente inalcançável. Ainda assim a primitiva é aplicada para
     * uniformidade: um borrow gravável é adquirido para cada `Buffer(U8)` INOUT
     * e liberado depois do downcall (o guest lança `MEM020` se já houver um).
     */
    private static void acquireBorrows(java.util.List<OutBuf> outs) {
        for (int i = 0; i < outs.size(); i++) {
            outs.get(i).obj().invokeMember("__kofBorrowAcquire");
        }
    }

    private static void releaseBorrows(java.util.List<OutBuf> outs) {
        for (int i = outs.size() - 1; i >= 0; i--) {
            outs.get(i).obj().invokeMember("__kofBorrowRelease");
        }
    }

    private static Object[] args(String sig, Value jsArgs, Arena stubArena,
                                 java.util.List<OutBuf> outs) {
        int n = countParams(sig);   // tokens de 1º nível (callback "(..)" conta 1)
        Object[] real = new Object[n];
        int[] curRef = { paramsStart(sig) };
        int cur = paramsStart(sig);
        for (int i = 0; i < n; i++) {
            char c = sig.charAt(cur);
            Value v = (jsArgs != null && jsArgs.hasArrayElements() && i < jsArgs.getArraySize())
                    ? jsArgs.getArrayElement(i) : null;
            if (c == '@') {
                // D6-1/3.8b (bridge JS): struct por valor. O record do guest expõe
                // `__kof_ffi_fields()` (ordem de declaração); empacotamos na arena
                // da chamada com o MESMO StructLayout do JVM (D6-5).
                curRef[0] = cur;
                String chars = KofJsFfiBridge.structCharsAt(sig, curRef);
                cur = curRef[0];
                real[i] = packStruct(chars, v, stubArena);
            } else if (c == 'p') {
                // D6-2/3.8b fatia 3 (bridge JS): `T[]` escalar -> `ptr` C, com
                // copy-in por chamada (o array JS não é pinado nem visto pelo C;
                // espelha `kof_ffi_copy_in` do JVM).
                char elem = sig.charAt(cur + 1);
                cur += 2;
                real[i] = packArray(elem, v, stubArena);
            } else if (c == 'B') {
                // D6-3/D-R3-BUFFER (bridge JS 21/09): `Buffer(U8)` INOUT — copia os
                // bytes do `Uint8Array` do guest para a arena da chamada e registra
                // o par para o copy-back pós-downcall (espelha kof_ffi_buffer_in/out).
                cur++;
                real[i] = packBuffer(v, stubArena, outs);
            } else if (c == '(') {
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
                try {
                    real[i] = jsCallbackStub(v, inner.toString(), stubArena);
                } catch (Throwable t) {
                    throw new RuntimeException("ffi: callback upcallStub failed: " + t, t);
                }
            } else {
                cur++;
                if (v == null || v.isNull()) {
                    real[i] = null;
                } else {
                    real[i] = switch (c) {
                        case 'i' -> v.asInt();
                        case 'j' -> v.asLong();
                        case 'f' -> v.asFloat();
                        case 'd' -> v.asDouble();
                        case 'b' -> v.asBoolean();
                        default -> v.asString();   // 'S'
                    };
                }
            }
        }
        return real;
    }

    /**
     * D6-1/3.8b (bridge JS): empacota um `record` do guest num struct C por valor.
     * O helper sintético {@code __kof_ffi_fields()} devolve os campos na ordem de
     * declaração (o host não reflete `RecordComponent` de um objeto GraalJS), e o
     * {@code StructLayout} vem dos chars do token — mesmo layout/offsets do
     * {@code kof_ffi_write_struct} reflexivo do JVM. A memória é da arena da
     * chamada (D6-5), fechada após o downcall.
     */
    private static MemorySegment packStruct(String chars, Value v, Arena arena) {
        if (v == null || v.isNull()) {
            throw new IllegalArgumentException("ffi: struct argument is null");
        }
        Value fields = v.invokeMember("__kof_ffi_fields");
        if (!fields.hasArrayElements() || fields.getArraySize() < chars.length()) {
            throw new IllegalArgumentException(
                    "ffi: struct field helper mismatch (got "
                            + (fields.hasArrayElements() ? fields.getArraySize() : -1)
                            + ", want " + chars.length() + ")");
        }
        StructLayout sl = KofJsFfiBridge.structLayout(chars);
        MemorySegment seg = arena.allocate(sl);
        for (int k = 0; k < chars.length(); k++) {
            long off = sl.byteOffset(MemoryLayout.PathElement.groupElement(k));
            Value f = fields.getArrayElement(k);
            switch (chars.charAt(k)) {
                case 'i' -> seg.set(ValueLayout.JAVA_INT, off, f.asInt());
                case 'j' -> seg.set(ValueLayout.JAVA_LONG, off, f.asLong());
                case 'f' -> seg.set(ValueLayout.JAVA_FLOAT, off, f.asFloat());
                case 'd' -> seg.set(ValueLayout.JAVA_DOUBLE, off, f.asDouble());
                case 'b' -> seg.set(ValueLayout.JAVA_BOOLEAN, off, f.asBoolean());
                default -> throw new IllegalArgumentException(
                        "ffi: bad struct field char: " + chars.charAt(k));
            }
        }
        return seg;
    }

    /**
     * D6-2/3.8b fatia 3 (bridge JS): copia um array escalar do guest para um
     * segmento nativo da arena da chamada e devolve o ponteiro (o C não vê nem
     * altera o array JS — sem write-back, como no JVM). O elem é i/j/f/d/b.
     */
    private static MemorySegment packArray(char elem, Value v, Arena arena) {
        if (v == null || !v.hasArrayElements()) {
            throw new IllegalArgumentException("ffi: scalar array argument is not a JS array");
        }
        int n = (int) v.getArraySize();
        MemoryLayout ml = KofJsFfiBridge.layout(elem);
        MemorySegment seg = arena.allocate(ml, n);
        for (int k = 0; k < n; k++) {
            Value e = v.getArrayElement(k);
            switch (elem) {
                case 'i' -> seg.setAtIndex(ValueLayout.JAVA_INT, k, e.asInt());
                case 'j' -> seg.setAtIndex(ValueLayout.JAVA_LONG, k, e.asLong());
                case 'f' -> seg.setAtIndex(ValueLayout.JAVA_FLOAT, k, e.asFloat());
                case 'd' -> seg.setAtIndex(ValueLayout.JAVA_DOUBLE, k, e.asDouble());
                case 'b' -> seg.setAtIndex(ValueLayout.JAVA_BOOLEAN, k, e.asBoolean());
                default -> throw new IllegalArgumentException(
                        "ffi: bad scalar array element char: " + elem);
            }
        }
        return seg;
    }

    /**
     * D6-3/D-R3-BUFFER (bridge JS 21/09): empacota o out-buffer `Buffer(U8)` —
     * lê o `Uint8Array` `data` do `KofBufferBox` do guest para a arena da
     * chamada e registra o par para o copy-back. Espelha `kof_ffi_buffer_in` do
     * JVM (a vida é gerenciada pela linguagem; D-R3-HANDLE-LIFETIME).
     */
    private static MemorySegment packBuffer(Value v, Arena arena, java.util.List<OutBuf> outs) {
        if (v == null || v.isNull()) {
            throw new IllegalArgumentException("ffi: buffer argument is null");
        }
        Value data = v.getMember("data");
        if (data == null || !data.hasArrayElements()) {
            throw new IllegalArgumentException("ffi: buffer argument has no byte storage");
        }
        int n = (int) data.getArraySize();
        MemorySegment seg = arena.allocate(ValueLayout.JAVA_BYTE, n);
        for (int k = 0; k < n; k++) {
            seg.setAtIndex(ValueLayout.JAVA_BYTE, k, (byte) data.getArrayElement(k).asInt());
        }
        outs.add(new OutBuf(v, data, seg, n));
        return seg;
    }

    /** Copy-back do out-buffer: o C escreveu no segmento; devolve ao guest. */
    private static void copyBack(java.util.List<OutBuf> outs) {
        for (OutBuf ob : outs) {
            for (int k = 0; k < ob.len(); k++) {
                ob.data().setArrayElement(k,
                        (int) (ob.seg().getAtIndex(ValueLayout.JAVA_BYTE, k) & 0xFF));
            }
        }
    }

    /** Índice do 1º token de parâmetro: 1, ou depois do token de retorno
     *  `@<n><chars>` quando o extern devolve um struct por valor (JS). */
    private static int paramsStart(String sig) {
        if (sig.charAt(0) == '@') {
            int[] ref = { 0 };
            KofJsFfiBridge.structCharsAt(sig, ref);
            return ref[0];
        }
        return 1;
    }

    /** Nº de tokens de parâmetro de 1º nível (um callback `(..)` conta como 1). */
    private static int countParams(String sig) {
        int n = 0, cur = paramsStart(sig);
        while (cur < sig.length()) {
            char c = sig.charAt(cur);
            if (c == ':') {
                // separador do NOME do record de retorno (`@<n><chars>` ... `:Nome`):
                // não é parâmetro — fim da lista.
                break;
            }
            if (c == 'p') {
                // array escalar: `p` + char do elemento (conta 1).
                n++;
                cur += 2;
            } else if (c == '@') {
                // struct por valor: `@` + tamanho decimal + chars (conta 1).
                n++;
                int[] ref = { cur };
                KofJsFfiBridge.structCharsAt(sig, ref);
                cur = ref[0];
            } else if (c == '(') {
                n++;
                int j = cur + 1, depth = 1;
                while (depth > 0) {
                    char x = sig.charAt(j);
                    if (x == '(') {
                        depth++;
                    } else if (x == ')') {
                        depth--;
                    }
                    j++;
                }
                cur = j;
            } else {
                n++;
                cur++;
            }
        }
        return n;
    }

    /**
     * Callback/upcall JS (R3, 3.4-C3): transforma o objeto de função Kof num ponteiro de
     * função C via `Linker.upcallStub`. A ponte chama `fn.getMember("invoke").execute(...)`
     * reentrante na mesma thread (medido na C3.1, `KofJsFfiCallbackBridgeTest`); o stub vive
     * na arena confined da chamada → contrato síncrono/não-escapante. `inner` =
     * "r"+chars dos params do callback (primitivos i/j/f/d/b OU String 'S' como ARGUMENTO
     * — um `char*` que a ponte lê via {@code getString}; o RETORNO continua só primitivo/
     * void, 'S' fica fora). O carrier ADDRESS de um param 'S' chega como {@code
     * MemorySegment} e é convertido p/ String em {@code executeJs*}.
     */
    private static MemorySegment jsCallbackStub(Value fn, String inner, Arena arena)
            throws Throwable {
        char rb = inner.charAt(0);
        int arity = inner.length() - 1;
        MemoryLayout[] il = new MemoryLayout[arity];
        Class<?>[] prim = new Class<?>[arity];
        for (int k = 0; k < arity; k++) {
            char c = inner.charAt(k + 1);
            il[k] = ffiLayout(c);
            // 'S' = char*: o carrier NATIVO do stub é ADDRESS -> MemorySegment; a ponte
            // abaixo o converte em String antes de chamar `invoke` (paridade com o
            // `kof_ffi_cstr` do JVM). Só 'S' diverge; primitivos ficam iguais.
            prim[k] = (c == 'S') ? MemorySegment.class : ffiCarrier(c);
        }
        Class<?> ret = rb == 'v' ? void.class : ffiCarrier(rb);
        // asVarargsCollector (não asSpreader): o alvo varargs `executeJsX(Value, Object...)`
        // recebe os primitivos já com boxing do asType. Mesma forma usada no pin C3.1.
        MethodHandle h = MethodHandles.lookup()
                .findStatic(KofJsFfiMarshal.class, "executeJs" + Character.toUpperCase(rb),
                        MethodType.methodType(ret, Value.class, Object[].class))
                .bindTo(fn)
                .asVarargsCollector(Object[].class)
                .asType(MethodType.methodType(ret, prim));
        FunctionDescriptor cb = (rb == 'v')
                ? FunctionDescriptor.ofVoid(il)
                : FunctionDescriptor.of(ffiLayout(rb), il);
        return Linker.nativeLinker().upcallStub(h, cb, arena);
    }

    // Os args chegam do upcall com os primitivos já boxados; um param 'S' chega como
    // MemorySegment (carrier do ADDRESS). A ponte lê o `char*` -> String UTF-8 (NULL ->
    // null) ANTES de entregar ao `invoke` Kof, espelhando o `kof_ffi_cstr` do JVM. Uma
    // String vira automaticamente um host->JS string no Value.execute. Primitivos: id.
    static int executeJsI(Value fn, Object... a) { return fn.getMember("invoke").execute(jsBridgeArgs(a)).asInt(); }
    static long executeJsJ(Value fn, Object... a) { return fn.getMember("invoke").execute(jsBridgeArgs(a)).asLong(); }
    static float executeJsF(Value fn, Object... a) { return fn.getMember("invoke").execute(jsBridgeArgs(a)).asFloat(); }
    static double executeJsD(Value fn, Object... a) { return fn.getMember("invoke").execute(jsBridgeArgs(a)).asDouble(); }
    static boolean executeJsB(Value fn, Object... a) { return fn.getMember("invoke").execute(jsBridgeArgs(a)).asBoolean(); }
    static void executeJsV(Value fn, Object... a) { fn.getMember("invoke").execute(jsBridgeArgs(a)); }

    private static Object[] jsBridgeArgs(Object[] a) {
        for (int k = 0; k < a.length; k++) {
            if (a[k] instanceof MemorySegment s) {
                a[k] = (s == null || s.address() == 0L)
                        ? null : s.reinterpret(Long.MAX_VALUE).getString(0L);
            }
        }
        return a;
    }

    static Class<?> ffiCarrier(char c) {
        return switch (c) {
            case 'i' -> int.class;
            case 'j' -> long.class;
            case 'f' -> float.class;
            case 'd' -> double.class;
            case 'b' -> boolean.class;
            default -> throw new IllegalArgumentException("bad ffi carrier char: " + c);
        };
    }

    static MemoryLayout ffiLayout(char c) {
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
}
