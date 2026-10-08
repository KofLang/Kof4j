package dev.kof.compiler.wasm;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WasmBinary — construtor do formato binario WebAssembly 1.0 core (D-WASM-01:
 * backend DIRETO, sem toolchain externa na emissao). O modulo do subset 15.2
 * usa as secoes Type(1), Function(3), Export(7), Code(10) e Memory(5) (a
 * memory linear do plano §9 e declarada desde ja: 1 pagina — o runtime das
 * unidades 15.3+ cresce sobre ela sem quebrar modulo existente).
 */
public final class WasmBinary {

    private WasmBinary() {}

    static void writeUleb(ByteArrayOutputStream out, long v) {
        long x = v;
        do {
            int b = (int) (x & 0x7f);
            x >>>= 7;
            if (x != 0) b |= 0x80;
            out.write(b);
        } while (x != 0);
    }

    static void writeSleb(ByteArrayOutputStream out, long v) {
        long x = v;
        while (true) {
            int b = (int) (x & 0x7f);
            x >>= 7;
            boolean signBit = (b & 0x40) != 0;
            if ((x == 0 && !signBit) || (x == -1 && signBit)) {
                out.write(b);
                return;
            }
            out.write(b | 0x80);
        }
    }

    private static byte[] uleb(long v) {
        var out = new ByteArrayOutputStream(5);
        writeUleb(out, v);
        return out.toByteArray();
    }

    private static byte[] bytes(byte[]... parts) {
        var out = new ByteArrayOutputStream();
        for (byte[] p : parts) out.writeBytes(p);
        return out.toByteArray();
    }

    private static byte[] str(String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        return bytes(uleb(b.length), b);
    }

    private static byte[] vec(List<byte[]> items) {
        var parts = new ArrayList<byte[]>(items.size() + 1);
        parts.add(uleb(items.size()));
        parts.addAll(items);
        return bytes(parts.toArray(new byte[0][]));
    }

    private static byte[] section(int id, byte[] content) {
        return bytes(new byte[]{(byte) id}, uleb(content.length), content);
    }

    static byte[] module(WasmModule m) {
        // 1) type section (dedup por assinatura; imports primeiro — o espaco
        // de funcoes do wasm indexa imports ANTES das funcs definidas)
        List<byte[]> typeBodies = new ArrayList<>();
        Map<String, Integer> typeIdx = new LinkedHashMap<>();
        List<byte[]> funcTypeBytes = new ArrayList<>();
        Map<String, Integer> funcIdx = new LinkedHashMap<>();
        List<byte[]> importItems = new ArrayList<>();
        for (WasmImport im : m.imports()) {
            String key = importSigKey(im);
            Integer t = typeIdx.get(key);
            if (t == null) {
                t = typeBodies.size();
                typeIdx.put(key, t);
                List<byte[]> ps = new ArrayList<>();
                for (int p : im.params()) ps.add(new byte[]{(byte) p});
                List<byte[]> rs = new ArrayList<>();
                for (int r : im.results()) rs.add(new byte[]{(byte) r});
                typeBodies.add(bytes(new byte[]{0x60}, vec(ps), vec(rs)));
            }
            importItems.add(bytes(str(im.module()), str(im.field()), new byte[]{0x00}, uleb(t)));
            funcIdx.put(im.name(), funcIdx.size());
        }
        for (WasmFunc f : m.funcs()) {
            String key = sigKey(f);
            Integer t = typeIdx.get(key);
            if (t == null) {
                t = typeBodies.size();
                typeIdx.put(key, t);
                List<byte[]> ps = new ArrayList<>();
                for (int p : f.params()) ps.add(new byte[]{(byte) p});
                List<byte[]> rs = new ArrayList<>();
                for (int r : f.results()) rs.add(new byte[]{(byte) r});
                typeBodies.add(bytes(new byte[]{0x60}, vec(ps), vec(rs)));
            }
            funcTypeBytes.add(uleb(t));
            funcIdx.put(f.name(), funcIdx.size());
        }
        // 2) exports: todas as funcs do modulo + memory
        List<byte[]> exportItems = new ArrayList<>();
        for (WasmFunc f : m.funcs()) {
            exportItems.add(bytes(str(f.name()), new byte[]{0x00}, uleb(funcIdx.get(f.name()))));
        }
        exportItems.add(bytes(str("memory"), new byte[]{0x02}, uleb(0)));
        // 3) code: locals agrupan por tipo; corpo resolve `call` por nome
        List<byte[]> codeItems = new ArrayList<>();
        for (WasmFunc f : m.funcs()) {
            Map<Integer, Integer> counts = new LinkedHashMap<>();
            for (int t : f.locals()) counts.merge(t, 1, Integer::sum);
            List<byte[]> groups = new ArrayList<>();
            for (var e : counts.entrySet()) {
                groups.add(bytes(uleb(e.getValue()), new byte[]{(byte) (int) e.getKey()}));
            }
            var body = new ByteArrayOutputStream();
            body.writeBytes(vec(groups));
            for (WasmInstr in : f.instrs()) in.encode(body, funcIdx);
            body.write(0x0b); // end
            codeItems.add(bytes(uleb(body.size()), body.toByteArray()));
        }
        var sections = new ArrayList<byte[]>();
        sections.add(new byte[]{0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00});
        sections.add(section(1, vec(typeBodies)));
        if (!importItems.isEmpty()) sections.add(section(2, vec(importItems)));
        sections.add(section(3, vec(funcTypeBytes)));
        sections.add(section(5, vec(List.of(new byte[]{0x00, 0x01}))));
        if (m.globals() != null && !m.globals().isEmpty()) {
            List<byte[]> globalItems = new ArrayList<>();
            for (int init : m.globals()) {
                // valtype i32 (0x7f), mutable (0x01), init expr i32.const init; end
                globalItems.add(bytes(new byte[]{0x7f, 0x01, 0x41}, sleb(init), new byte[]{0x0b}));
            }
            sections.add(section(6, vec(globalItems)));
        }
        sections.add(section(7, vec(exportItems)));
        sections.add(section(10, vec(codeItems)));
        if (m.data() != null && !m.data().isEmpty()) {
            List<byte[]> dataItems = new ArrayList<>();
            for (WasmData d : m.data()) {
                dataItems.add(bytes(new byte[]{0x00}, new byte[]{0x41}, sleb(d.addr()),
                        new byte[]{0x0b}, uleb(d.bytes().length), d.bytes()));
            }
            sections.add(section(11, vec(dataItems)));
        }
        return bytes(sections.toArray(new byte[0][]));
    }

    private static byte[] sleb(int v) {
        var out = new ArrayList<Byte>();
        boolean more = true;
        while (more) {
            byte b = (byte) (v & 0x7f);
            v >>= 7;
            boolean signBit = (b & 0x40) != 0;
            if ((v == 0 && !signBit) || (v == -1 && signBit)) more = false;
            else b |= 0x80;
            out.add(b);
        }
        byte[] r = new byte[out.size()];
        for (int i = 0; i < r.length; i++) r[i] = out.get(i);
        return r;
    }

    private static String importSigKey(WasmImport im) {
        StringBuilder sb = new StringBuilder();
        for (int p : im.params()) sb.append(p).append(',');
        sb.append("->");
        for (int r : im.results()) sb.append(r).append(',');
        return sb.toString();
    }

    private static String sigKey(WasmFunc f) {
        StringBuilder sb = new StringBuilder();
        for (int p : f.params()) sb.append(p).append(',');
        sb.append("->");
        for (int r : f.results()) sb.append(r).append(',');
        return sb.toString();
    }
}

/** Funcao do modulo: assinatura + locals + corpo. */
record WasmFunc(String name, List<Integer> params, List<Integer> results,
                List<Integer> locals, List<WasmInstr> instrs) {

    static final int TYPE_I32 = 0x7f, TYPE_I64 = 0x7e, TYPE_F64 = 0x7c;
}

/** Segmento de dados ativo (pagina linear): endereco + bytes (strings 15.3b). */
record WasmData(int addr, byte[] bytes) {}

/** Modelo do modulo (lista ordenada de funcs). */
record WasmModule(List<WasmImport> imports, List<WasmFunc> funcs, List<WasmData> data,
                  List<Integer> globals) {

    WasmModule(List<WasmFunc> funcs) {
        this(List.of(), funcs, List.of(), List.of());
    }

    WasmModule(List<WasmImport> imports, List<WasmFunc> funcs) {
        this(imports, funcs, List.of(), List.of());
    }

    WasmModule(List<WasmImport> imports, List<WasmFunc> funcs, List<WasmData> data) {
        this(imports, funcs, data, List.of());
    }

    byte[] serialize() {
        return WasmBinary.module(this);
    }
}
