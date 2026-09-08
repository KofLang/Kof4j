package dev.kof.compiler.backend;
import dev.kof.compiler.IRModule;

import java.io.IOException;
import java.nio.file.Path;

public interface Backend {
    void emit(IRModule module, Path outputDir) throws IOException;

    default void emit(IRModule module, Path outputDir, boolean debugInfo) throws IOException {
        emit(module, outputDir);
    }
}