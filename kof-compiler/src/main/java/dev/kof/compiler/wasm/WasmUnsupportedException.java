package dev.kof.compiler.wasm;

/**
 * Exceção lançada pelo backend WebAssembly quando uma construção está
 * fora do subset atualmente implementado (WASM002).
 */
public class WasmUnsupportedException extends RuntimeException {
    public WasmUnsupportedException(String message) {
        super(message);
    }
}
