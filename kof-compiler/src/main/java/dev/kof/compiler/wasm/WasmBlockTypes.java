package dev.kof.compiler.wasm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CLASS-2 (28/10, #776 — split check_500): o registro dos BLOCK TYPES
 * multi-valor do despacho virtual. O lowering registra a assinatura
 * [receiver i32, args...] -> [res] no momento do if e usa o INDICE como
 * blocktype (s33); o serializador emite os registrados PRIMEIRO na type
 * section — os indices sao estaveis por construcao. Por modulo: o emit
 * cria, o Ctx captura, o modulo carrega para o serializador.
 */
final class WasmBlockTypes {

    final Map<String, WasmBlockType> all = new LinkedHashMap<>();
    private final Map<String, Integer> idxByKey = new LinkedHashMap<>();

    int register(List<Integer> params, List<Integer> results) {
        String key = params + ">" + results;
        Integer idx = idxByKey.get(key);
        if (idx != null) return idx;
        idx = all.size();
        all.put(key, new WasmBlockType(params, results));
        idxByKey.put(key, idx);
        return idx;
    }
}
