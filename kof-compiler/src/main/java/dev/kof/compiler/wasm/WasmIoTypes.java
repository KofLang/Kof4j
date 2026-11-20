package dev.kof.compiler.wasm;

import dev.kof.compiler.Type;

/**
 * O `kof.io` no backend WASI: o receiver {@code File}/{@code Path}/{@code
 * Directory} e' um ALIAS do path (uma String) — a MESMA lei do runtime JVM
 * (o receiver e' apagado para o path String em runtime: o {@code File} nao
 * e' um objeto, e' o caminho).
 *
 * <p>Consequencia no backend: o local de tipo {@code kof.io.File} e' um slot
 * i32 = o handle do path (o mesmo slot de uma String) e o rastro do load e'
 * "string" (o path), nunca "File" — o nome da classe caeria no mapa de tipos
 * e recusaria o local honesto (WASM002), medido.
 */
final class WasmIoTypes {

    private WasmIoTypes() {
    }

    /** o tipo e' um dos receivers de {@code kof.io} (File/Path/Directory). */
    static boolean isIoReceiver(Type t) {
        if (!(t instanceof Type.ClassType ct)) return false;
        return "kof.io".equals(ct.packageName())
                && ("File".equals(ct.name()) || "Path".equals(ct.name())
                    || "Directory".equals(ct.name()));
    }
}
