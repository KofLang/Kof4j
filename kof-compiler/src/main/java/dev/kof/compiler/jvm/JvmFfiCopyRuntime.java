package dev.kof.compiler.jvm;

/** A fatia COPY-OUT/COPY-IN do runtime JVM gerado (o #1156 face; o próprio
 *  split do gate-500): o fragmento de fonte colado NO CORPO da classe
 *  {@code KofRuntime} gerada (os helpers são static members dela — o mesmo
 *  mecanismo do {@link JvmFfiRuntime#source()}). Os helpers:
 *  <ul>
 *   <li>{@code kof_ffi_copy_in}/{@code kof_ffi_copy_in_strings} — o copy-in
 *       do array/String[] para a arena da chamada (D6-2/3.8b fatia 3 + a face
 *       2 do D-MEM-FFI-CROSS-FULL);</li>
 *   <li>{@code kof_ffi_copy_out} — a fatia COPY-OUT de arrays (15/11): o
 *       conteúdo da arena volta para o array Java pós-retorno, element-typed
 *       pelo MESMO ValueLayout do copy_in (o boolean via bytes 0/1) — só para
 *       os módulos {@code ownership borrowed} (o token 'P' do sig);</li>
 *   <li>{@code kof_ffi_buffer_in/out} — o Buffer(U8) INOUT (D6-3/D-R3-BUFFER:
 *       o copy-in + o copy-back pós-retorno).</li>
 *  </ul>
 */
final class JvmFfiCopyRuntime {

    private JvmFfiCopyRuntime() {}

    static final String CHUNK = """
                // A fatia OUT-PARAM (o `pkthdr**` do pcap, #1160): um
                // `Handle[]` emprestado -> `void**`. Cada slot ADDRESS é
                // iniciado com o segmento do Handle (null -> NULL). O
                // MemorySegment.copy genérico rejeita arrays de endereço — o
                // loop por slot é o caminho. O copy-out espelha (abaixo).
                static java.lang.foreign.MemorySegment kof_ffi_copy_in_handles(
                        java.lang.foreign.Arena arena, Object arr) {
                    int n = java.lang.reflect.Array.getLength(arr);
                    java.lang.foreign.MemorySegment seg = arena.allocate(
                            java.lang.foreign.ValueLayout.ADDRESS, n == 0 ? 1 : n);
                    for (int i = 0; i < n; i++) {
                        Object h = java.lang.reflect.Array.get(arr, i);
                        if (h != null) {
                            seg.setAtIndex(java.lang.foreign.ValueLayout.ADDRESS, i,
                                    (java.lang.foreign.MemorySegment) h);
                        }
                    }
                    return seg;
                }

                // D6-2 (3.8b fatia 3): copia um array Kof (array Java primitivo)
                // para um segmento nativo da arena da chamada e devolve o ponteiro.
                // D-R3-3.3 + a fatia OUT-PARAM: `Handle[]` emprestado ->
                // `void**` (o `pkthdr**` do pcap, #1160) — o despacho fica AQUI
                // (o helper vive nesta classe; o gate-500).
                static java.lang.foreign.MemorySegment kof_ffi_copy_in(
                        java.lang.foreign.Arena arena, Object arr, char e) {
                    if (e == 'H') return kof_ffi_copy_in_handles(arena, arr);
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

                // A fatia COPY-OUT (15/11, o #1156 face): apos o retorno, o
                // conteudo do segmento da arena volta para o array Java
                // (element-typed; o espelho exato de kof_ffi_copy_in). `boolean[]`
                // volta por byte 0/1 (o espelho do copy_in; paridade _Bool).
                static void kof_ffi_copy_out(
                        Object arr, java.lang.foreign.MemorySegment seg, char e) {
                    int n = java.lang.reflect.Array.getLength(arr);
                    if (e == 'H') {
                        // A fatia OUT-PARAM (o `pkthdr**` do pcap, #1160): cada
                        // slot ADDRESS volta como um Handle Kof (o espelho do
                        // copy_in_handles e da face de retorno 'H'); NULL -> null.
                        Object[] hs = (Object[]) arr;
                        for (int i = 0; i < n; i++) {
                            java.lang.foreign.MemorySegment s =
                                    seg.getAtIndex(java.lang.foreign.ValueLayout.ADDRESS, i);
                            hs[i] = s.equals(java.lang.foreign.MemorySegment.NULL) ? null : s;
                        }
                        return;
                    }
                    if (e == 'b') {
                        // `boolean[]` não tem MemorySegment.copy direto (o mesmo
                        // corte do copy_in): volta por byte 0/1 (paridade _Bool).
                        boolean[] dst = (boolean[]) arr;
                        for (int i = 0; i < n; i++) {
                            dst[i] = seg.get(java.lang.foreign.ValueLayout.JAVA_BYTE, i) != 0;
                        }
                        return;
                    }
                    // element-typed copy com o MESMO ValueLayout do copy_in
                    // (JAVA_INT p/ 'i', JAVA_LONG p/ 'j' — o mapa do paramChar
                    // NÃO tem 'l'). O ofArray precisa do array TIPADO (o
                    // MemorySegment.ofArray(Object) não existe — o copy_in usa
                    // o overload reflectivo MemorySegment.copy(Object,int,seg,...)
                    // na direção inversa).
                    // O overload JDK 25: copy(srcSeg, srcLayout, srcOffset,
                    // dstArray, dstIndex, count) — o dstOffset é INT e NÃO leva
                    // dstLayout (o layout do array primitivo é implícito).
                    java.lang.foreign.MemorySegment.copy(seg, kof_ffi_layout(e), 0L,
                            arr, 0, n);
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


""";
}
