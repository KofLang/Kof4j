package dev.kof.compiler.nat.mcu;

import dev.kof.compiler.IRModule;
import dev.kof.compiler.backend.Backend;

import java.io.IOException;
import java.nio.file.Path;

/** The Backend face of the ESP8266 emitter (the dispatch target of
 *  `CompilerPipeline.selectBackend` for `Target.NATIVE_ESP8266`). */
public final class NativeEsp8266Backend implements Backend {

    @Override
    public void emit(IRModule module, Path outputDir) throws IOException {
        NativeEsp8266.emit(module, outputDir);
    }
}
