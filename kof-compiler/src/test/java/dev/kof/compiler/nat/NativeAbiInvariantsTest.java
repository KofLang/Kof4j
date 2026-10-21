package dev.kof.compiler.nat;

import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Target;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §14 (ABI and optimization) of {@code docs/assembly-optimization-plan.md}:
 * "Optimizations must never incorrectly change … caller-saved; callee-saved;
 * stack alignment; parameter layout".
 *
 * <p>The §14 fixes (stack alignment, callee-saved save/restore) were each proved
 * once by measurement. This test makes them MECHANICAL: it compiles a program
 * exercising early returns, loops, exceptions and 7+ argument calls, then asserts
 * over the emitted x86-64 that
 *
 * <ul>
 *   <li>every {@code call} is 16-byte aligned (SysV), and</li>
 *   <li>every generated method that SAVES a callee-saved register also restores it.</li>
 * </ul>
 *
 * <p>Both are properties a future optimization could break silently (a peephole
 * that drops a push, a pass that inserts a call after the stack was shaped), so
 * they belong in a guard rather than only in a landing note.
 */
class NativeAbiInvariantsTest {

    private static final String SOURCE = """
            Int early(Int a) { if (a > 0) { return a * 3 }; return 0 - a }
            Int loopy(Int n) { var s = 0; for (var i = 0; i < n; i++) { if (i == 3) { continue }; s = s + i * 2 }; return s }
            Int safe(Int a) { try { if (a == 0) { throw "zero" }; return a * 5 } catch (String e) { return 0 - 1 } }
            Int seven(Int a, Int b, Int c, Int d, Int e, Int f, Int g) { return a + b + c + d + e + f + g }
            record P(Int x, Int y)
            Int rec(Int v) { var p = P(v, v + 1); return p.x() + p.y() }
            main() {
                println(early(2)); println(loopy(5)); println(safe(0))
                println(seven(1, 2, 3, 4, 5, 6, 7)); println(rec(4))
            }
            """;

    @Test
    void everyGeneratedCallIsSixteenByteAligned(@TempDir Path dir) throws Exception {
        List<String> lines = compileAndReadAsm(dir);
        for (Map.Entry<String, List<String>> method : generatedMethods(lines).entrySet()) {
            List<Integer> misaligned =
                    NativeX86StackAlign.misalignedCallSites(method.getValue(), 0);
            assertTrue(misaligned.isEmpty(),
                    "§14: every call must be 16-byte aligned; " + method.getKey()
                            + " has deltas " + misaligned);
        }
    }

    @Test
    void everySavedCalleeSavedRegisterIsRestored(@TempDir Path dir) throws Exception {
        List<String> lines = compileAndReadAsm(dir);
        Map<String, Set<String>> saved = new LinkedHashMap<>();
        Map<String, Set<String>> restored = new LinkedHashMap<>();
        String current = null;
        for (String raw : lines) {
            String t = raw.strip();
            if (t.endsWith(":") && !t.startsWith(".")) {
                current = t.startsWith("Default_") ? t : null;
                continue;
            }
            if (current == null) continue;
            if (t.startsWith("movq %r") && t.contains("%rbp)")) {
                String reg = t.substring("movq ".length(), t.indexOf(',')).trim();
                if (isCalleeSaved(reg)) saved.computeIfAbsent(current, k -> new LinkedHashSet<>()).add(reg);
            } else if (t.startsWith("movq -") && t.contains("(%rbp), %r")) {
                String reg = t.substring(t.indexOf(',') + 1).trim();
                if (isCalleeSaved(reg)) restored.computeIfAbsent(current, k -> new LinkedHashSet<>()).add(reg);
            }
        }
        assertFalse(saved.isEmpty(), "expected at least one method to save a callee-saved register");
        for (Map.Entry<String, Set<String>> e : saved.entrySet()) {
            Set<String> back = restored.getOrDefault(e.getKey(), Set.of());
            assertTrue(back.containsAll(e.getValue()),
                    "§14: " + e.getKey() + " saves " + e.getValue() + " but restores " + back);
        }
    }

    private static boolean isCalleeSaved(String reg) {
        return reg.matches("%r(bx|12|13|14|15)");
    }

    private static List<String> compileAndReadAsm(Path dir) throws Exception {
        Path source = dir.resolve("Main.kf");
        Files.writeString(source, SOURCE);
        // The emitter deletes the .s after linking unless asked to keep it. Set
        // the property HERE so the test does not depend on the shell environment
        // (`NativeArchEmitter.keepAsm` honours both env and property).
        String prior = System.getProperty("KOF_KEEP_ASM");
        System.setProperty("KOF_KEEP_ASM", "1");
        try {
            new CompilerDriver().compile(source, dir.resolve("out"), Target.NATIVE);
        } finally {
            if (prior == null) System.clearProperty("KOF_KEEP_ASM");
            else System.setProperty("KOF_KEEP_ASM", prior);
        }
        return Files.readAllLines(dir.resolve("out").resolve("Default").resolve("Main.s"));
    }

    /** Slices the asm into per-method line lists for the generated `Default_*` methods. */
    private static Map<String, List<String>> generatedMethods(List<String> lines) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        String current = null;
        for (String raw : lines) {
            String t = raw.strip();
            if (t.endsWith(":") && !t.startsWith(".")) {
                current = t.startsWith("Default_") ? t.substring(0, t.length() - 1) : null;
                if (current != null) out.put(current, new ArrayList<>());
                continue;
            }
            if (current != null) out.get(current).add(raw);
        }
        assertFalse(out.isEmpty(), "expected generated Default_* methods in the asm");
        return out;
    }
}
