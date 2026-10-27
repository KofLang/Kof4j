package dev.kof.compiler.nat;

import dev.kof.compiler.KofOperation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase D of {@code docs/assembly-optimization-plan.md} §735: "each
 * backend must explicitly declare which operations it supports." This guards the
 * {@link NativeOpSupport#X86_64} declaration against the emitter: it must match the
 * switch's cases EXACTLY (no unhandled declaration, no undeclared case), and it
 * must be COMPLETE against every {@link KofOperation} in the compiler — so a NEW IR
 * op fails here (a deliberate decision point) instead of throwing
 * {@code UnsupportedOperationException} in the field.
 */
class NativeOpSupportTest {

    /** The simplest class of each KofOperation in the compiler source dir. */
    private static Set<String> allKofOperationClasses() throws IOException {
        Path dir = Path.of("src/main/java/dev/kof/compiler");
        assertTrue(Files.isDirectory(dir), "compiler source dir must exist");
        Set<String> out = new TreeSet<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.toList()) {
                String name = f.getFileName().toString();
                if (!name.startsWith("Kof") || !name.endsWith(".java")) continue;
                String simple = name.substring(0, name.length() - ".java".length());
                String body = Files.readString(f, StandardCharsets.UTF_8);
                if (body.contains("implements KofOperation")) out.add(simple);
            }
        }
        return out;
    }

    /** The op classes the emitter's switch names as `case KofX ...`. */
    private static Set<String> emitterCases() throws IOException {
        String src = Files.readString(
                Path.of("src/main/java/dev/kof/compiler/nat/NativeMethodEmitter.java"),
                StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("case (Kof[A-Za-z0-9]+)").matcher(src);
        Set<String> out = new TreeSet<>();
        while (m.find()) out.add(m.group(1));
        return out;
    }

    private static Set<String> declaredNames() {
        Set<String> out = new TreeSet<>();
        for (Class<?> c : NativeOpSupport.X86_64) out.add(c.getSimpleName());
        return out;
    }

    @Test
    void theDeclarationMatchesTheEmitterExactly() throws IOException {
        assertEquals(emitterCases(), declaredNames(),
                "NativeOpSupport.X86_64 must equal the emitter's handled ops "
                + "(a mismatch is drift: update the declaration AND the emitter together)");
    }

    @Test
    void theDeclarationIsCompleteAgainstEveryIrOp() throws IOException {
        Set<String> all = allKofOperationClasses();
        Set<String> declared = declaredNames();
        Set<String> missing = new TreeSet<>(all);
        missing.removeAll(declared);
        assertEquals(Set.of(), missing,
                "a NEW KofOperation exists but is not declared in NativeOpSupport.X86_64 "
                + "(add its x86 lowering AND this declaration in the same change)");
    }

    /** The op classes the CROSS emitter's switch names as `case KofX ...`. */
    private static Set<String> crossEmitterCases() throws IOException {
        String src = Files.readString(
                Path.of("src/main/java/dev/kof/compiler/nat/NativeRiscvCrossEmit.java"),
                StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("case (Kof[A-Za-z0-9]+)").matcher(src);
        Set<String> out = new TreeSet<>();
        while (m.find()) out.add(m.group(1));
        return out;
    }

    @Test
    void theCrossDeclarationMatchesTheCrossEmitterExactly() throws IOException {
        Set<String> decl = new TreeSet<>();
        for (Class<?> c : NativeOpSupport.RISCV64_CROSS) decl.add(c.getSimpleName());
        assertEquals(crossEmitterCases(), decl,
                "NativeOpSupport.RISCV64_CROSS must equal the cross emitter's handled ops");
    }
}