package dev.kof.compiler;

import dev.kof.compiler.jvm.JvmBackend;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * COMP002 "frame crash" deve produzir diagnóstico útil por padrão (plano
 * estabilização 0.3.1, parte 3): arquivo:linha do último construto Kof,
 * fase do compilador, IR e bytecode ASM na mensagem — sem flags.
 */
class JvmFrameDiagnosticsTest {

    @TempDir
    Path dir;

    @Test
    void frameCrashMessageCarriesSourceLineIrAndAsm() throws IOException {
        // KofPop sem valor na pilha → COMPUTE_FRAMES estoura no visitMaxs.
        SourcePosition pos = new SourcePosition("crash.kf", 7, 3, 42, 5);
        KofPop pop = new KofPop();
        IRMethod method = new IRMethod("m", Type.PrimitiveType.VOID, List.of(),
                org.objectweb.asm.Opcodes.ACC_PUBLIC, List.of(),
                List.of(new IRBasicBlock(0, List.of(pop))),
                List.of(), new KofDebugInfo(Map.of(pop, pos)), List.of(), List.of());
        IRClass clazz = new IRClass("Crash", "java/lang/Object", List.of(),
                org.objectweb.asm.Opcodes.ACC_PUBLIC, List.of(), List.of(method),
                List.of(), null, 0);
        IRModule module = new IRModule("crash", List.of(clazz), List.of(), "crash.kf");

        JvmBackend backend = new JvmBackend();
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> backend.emit(module, dir));

        String msg = rootMessages(e);
        assertTrue(msg.contains("crash.kf:7"), "mensagem deve apontar arquivo:linha Kof: " + msg);
        assertTrue(msg.contains("fase"), "mensagem deve nomear a fase do compilador: " + msg);
        assertTrue(msg.contains("KofPop"), "mensagem deve incluir o IR da op: " + msg);
        assertTrue(msg.contains("ASM") || msg.contains("asm"), "mensagem deve incluir bytecode ASM: " + msg);
    }

    private static String rootMessages(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c.getMessage() != null) sb.append(c.getMessage()).append('\n');
        }
        return sb.toString();
    }
}
