package dev.kof.compiler.wasm;

import java.util.List;

/**
 * Import do modulo (unidade 15.3, D-WASM-06): host WASI preview1 —
 * `wasi_snapshot_preview1.fd_write`/`proc_exit`. O espaco de funcoes do
 * wasm indexa imports ANTES das funcs definidas; `WasmInstr.Call` resolve
 * por nome no mesmo mapa.
 */
record WasmImport(String module, String field, String name,
                  List<Integer> params, List<Integer> results) {

    static WasmImport wasi(String field, List<Integer> params, List<Integer> results) {
        return new WasmImport("wasi_snapshot_preview1", field, field, params, results);
    }
}
