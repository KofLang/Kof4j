package dev.kof.compiler.wasm;

import dev.kof.compiler.KofOperation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase D of {@code docs/assembly-optimization-plan.md}: "each
 * backend must explicitly declare which operations it supports."
 *
 * <p>{@code NativeOpSupportTest} guards the native declarations; this guards the
 * WASM one. The set must be a subset of the real {@link KofOperation} types (a
 * typo or a renamed op fails), every declared op must actually be MENTIONED by
 * the lowerer (a declared capability that does not exist is drift), and the
 * declaration must not be empty.
 */
class WasmOpSupportTest {

    private static final Path LOWERING =
            Path.of("src/main/java/dev/kof/compiler/wasm/WasmLowering.java");

    /** Os despachos extraídos (os splits do check_500): um op é "tratado"
     *  quando qualquer uma das classes de despacho o menciona. */
    private static final List<Path> DISPATCHES = List.of(
            LOWERING,
            Path.of("src/main/java/dev/kof/compiler/wasm/WasmCallCode.java"),
            Path.of("src/main/java/dev/kof/compiler/wasm/WasmCollCallCode.java"),
            Path.of("src/main/java/dev/kof/compiler/wasm/WasmArrayOps.java"));

    @Test
    void everyDeclaredOpIsARealOperationType() throws IOException {
        Set<String> operations = allKofOperationClasses();
        for (Class<?> c : WasmOpSupport.SUPPORTED) {
            assertTrue(operations.contains(c.getSimpleName()),
                    "WasmOpSupport declares " + c.getSimpleName()
                            + ", which is not a concrete KofOperation in the compiler");
        }
    }

    @Test
    void everyDeclaredOpIsActuallyHandledByTheLowerer() throws IOException {
        StringBuilder all = new StringBuilder();
        for (Path p : DISPATCHES) {
            all.append(Files.readString(p));
        }
        String source = all.toString();
        for (Class<?> c : WasmOpSupport.SUPPORTED) {
            assertTrue(source.contains("instanceof " + c.getSimpleName())
                            || source.contains(c.getSimpleName() + " "),
                    "Phase D drift: " + c.getSimpleName() + " is declared supported in "
                            + "WasmOpSupport but the lowerer never mentions it");
        }
    }

    @Test
    void theDeclarationIsNotEmptyAndSmallerThanTheWholeLanguage() throws IOException {
        assertNotEquals(0, WasmOpSupport.size(), "the WASM declaration cannot be empty");
        Set<String> all = allKofOperationClasses();
        assertFalse(all.isEmpty(), "expected operation classes in the compiler source");
        assertTrue(WasmOpSupport.size() < all.size(),
                "WASM supports a SUBSET — the declaration covering every op would mean "
                        + "the subset boundary was lost (declared " + WasmOpSupport.size()
                        + " of " + all.size() + ")");
    }

    /** The same enumeration {@code NativeOpSupportTest} uses. */
    private static Set<String> allKofOperationClasses() throws IOException {
        Path dir = Path.of("src/main/java/dev/kof/compiler");
        assertTrue(Files.isDirectory(dir), "compiler source dir must exist");
        Set<String> out = new TreeSet<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.toList()) {
                String name = f.getFileName().toString();
                if (!name.startsWith("Kof") || !name.endsWith(".java")) continue;
                String simple = name.substring(0, name.length() - ".java".length());
                if (Files.readString(f, StandardCharsets.UTF_8).contains("implements KofOperation")) {
                    out.add(simple);
                }
            }
        }
        return out;
    }
}
