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
        // 1) type section (dedup por assinatura; os BLOCK TYPES do despacho
        // virtual PRIMEIRO — os indices sao atribuidos no lowering e precisam
        // ser estaveis; imports depois — o espaco de funcoes do wasm indexa
        // imports ANTES das funcs definidas)
        List<byte[]> typeBodies = new ArrayList<>();
        if (m.blockTypes() != null) {
            for (WasmBlockType bt : m.blockTypes().all.values()) {
                List<byte[]> ps = new ArrayList<>();
                for (int p : bt.params()) ps.add(new byte[]{(byte) p});
                List<byte[]> rs = new ArrayList<>();
                for (int r : bt.results()) rs.add(new byte[]{(byte) r});
                typeBodies.add(bytes(new byte[]{0x60}, vec(ps), vec(rs)));
            }
        }
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
        // TRY-1 (01/11, #776 — round 85): o tipo do tag [i32]->[] do wasm EH
        // (a msg de String entra como parâmetro) — dedupe na MESMA assinatura
        // com chave própria (as chaves reais são numéricas — sem colisão); o
        // corpo entra no FIM da type section: os indices das funcs NÃO mudam
        int tagTypeIdx = -1;
        if (m.usesTry()) {
            String tagKey = "tag:i32->";
            Integer tt = typeIdx.get(tagKey);
            if (tt == null) {
                tt = typeBodies.size();
                typeIdx.put(tagKey, tt);
                typeBodies.add(bytes(new byte[]{0x60}, vec(List.of(new byte[]{0x7f})), vec(List.of())));
            }
            tagTypeIdx = tt;
        }
        // 2) exports: todas as funcs do modulo + memory
        List<byte[]> exportItems = new ArrayList<>();
        for (WasmFunc f : m.funcs()) {
            exportItems.add(bytes(str(f.name()), new byte[]{0x00}, uleb(funcIdx.get(f.name()))));
        }
        exportItems.add(bytes(str("memory"), new byte[]{0x02}, uleb(0)));
        // 3) code: locals agrupam runs CONSECUTIVOS do mesmo tipo (a ordem dos
        // indices e a ordem de insercao — o lowering atribui indices por posicao,
        // entao nao se pode reordenar por tipo sem deslocar o corpo); corpo
        // resolve `call` por nome
        List<byte[]> codeItems = new ArrayList<>();
        for (WasmFunc f : m.funcs()) {
            List<byte[]> groups = new ArrayList<>();
            int runType = -1, runCount = 0;
            for (int t : f.locals()) {
                if (t == runType) { runCount++; continue; }
                if (runCount > 0) groups.add(bytes(uleb(runCount), new byte[]{(byte) runType}));
                runType = t; runCount = 1;
            }
            if (runCount > 0) groups.add(bytes(uleb(runCount), new byte[]{(byte) runType}));
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
        sections.add(section(5, vec(List.of(new byte[]{0x00, 0x01})))); // min 1 (sem max: o GC-4 — a dança de staging revertida — o cap exige as strings coletáveis)
        // TRY-1: a seção tag (id 13) — o lugar MEDIDO: entre memory (5) e
        // global (6) (wasm-tools round-trip 01/11); um tag: attr 0x00 +
        // typeidx
        if (m.usesTry()) {
            sections.add(section(13, vec(List.of(bytes(new byte[]{0x00}, uleb(tagTypeIdx))))));
        }
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

/** CLASS-2 (28/10, #776): block type MULTI-VALOR do despacho virtual —
 *  os params = [receiver i32, args...] na pilha no if; os bracos os
 *  consomem (a lei do if: os valores ABAIXO do label sobrevivem ao end —
 *  o braco nao pode consumi-los sem declara-los como params). */
record WasmBlockType(List<Integer> params, List<Integer> results) {}

/** Modelo do modulo (lista ordenada de funcs). */
record WasmModule(List<WasmImport> imports, List<WasmFunc> funcs, List<WasmData> data,
                  List<Integer> globals, WasmBlockTypes blockTypes, boolean usesTry) {

    WasmModule(List<WasmFunc> funcs) {
        this(List.of(), funcs, List.of(), List.of(), null, false);
    }

    WasmModule(List<WasmImport> imports, List<WasmFunc> funcs) {
        this(imports, funcs, List.of(), List.of(), null, false);
    }

    WasmModule(List<WasmImport> imports, List<WasmFunc> funcs, List<WasmData> data) {
        this(imports, funcs, data, List.of(), null, false);
    }

    WasmModule(List<WasmImport> imports, List<WasmFunc> funcs, List<WasmData> data,
               List<Integer> globals) {
        this(imports, funcs, data, globals, null, false);
    }

    byte[] serialize() {
        return WasmBinary.module(this);
    }
}
