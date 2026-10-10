package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Auditoria de paridade (frente de revisão, 21/09) — Fatia 8: o registry de
 * {@code String} é a fonte única de verdade do que o typer aceita; este teste
 * trava, nome a nome, como cada alvo JS o resolve. É a mecanização direcionada
 * da classe do {@code known-bugs.md §424} (métodos aceitos que caíam em
 * {@code default} no JS e geravam {@code receiver.<m>} inexistente;
 * {@code toCharArray}/`compareToIgnoreCase` já foram portados —
 * `D-FULL-PARITY-050` row 11).
 *
 * <p>O golden é MEDIDO da fonte (Q3): o conjunto de métodos vem de
 * {@code StringMethodRegistry.java} e os {@code case} explícitos vêm de
 * {@code JsCallEmitter.java}. Um método novo no registry sem classificação
 * quebra aqui de propósito — a classificação tem de ser feita junto.
 */
class StringMethodTargetCoverageTest {

    /** Métodos cujo membro de {@code String.prototype} casa com a semântica Kof. */
    private static final Set<String> JS_NATIVE_MEMBER = Set.of(
            "substring", "contains", "startsWith", "endsWith", "indexOf",
            "lastIndexOf", "concat", "trim", "toUpperCase", "toLowerCase");

    /** Conversões numéricas: o registry mapeia p/ {@code kof_string_to_*} (caso JS explícito). */
    private static final Set<String> RUNTIME_SUFFIX = Set.of(
            "toInt", "toLong", "toDouble", "toFloat");

    /** §424/`D-STR-UNICODE` — aceitos no typer, SEM lowering no JS (regex trio, deferido a 1.0). */
    // §555/#719: getBytes bindou no JVM (Typer/registry) mas JS/Native ficam
    // no gate STR003 (StringTargetGaps.INCOMPLETE) — gap documentado, nao crescimento.
    private static final Set<String> JS_KNOWN_GAP = Set.of(
            "matches", "replaceAll", "replaceFirst", "getBytes");

    private static Set<String> registryMethods() throws Exception {
        String src = Files.readString(
                Path.of("src/main/java/dev/kof/compiler/StringMethodRegistry.java"));
        int from = src.indexOf("stringMethodSignature(String name, int argCount, List<Type> argTypes)");
        int to = src.indexOf("private static Sig replaceSignature");
        assertTrue(from >= 0 && to > from, "registry: recorte de stringMethodSignature nao localizado");
        return caseNames(src.substring(from, to));
    }

    private static Set<String> jsExplicitCases() throws Exception {
        String src = Files.readString(
                Path.of("src/main/java/dev/kof/compiler/js/JsCallEmitter.java"));
        return caseNames(src);
    }

    /** Nomes de método de String tratados no x86 ({@code "x".equals(kc.methodName())}). */
    private static Set<String> x86Handled() throws Exception {
        String src = Files.readString(Path.of(
                "src/main/java/dev/kof/compiler/nat/NativeX86StringCalls.java"));
        var names = new LinkedHashSet<String>();
        Matcher m = Pattern.compile("\"([A-Za-z]+)\"\\.equals\\(kc\\.methodName\\(\\)\\)").matcher(src);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    /** Nomes de método de String no switch cross ({@code case "x" -> ...}). */
    private static Set<String> riscvHandled() throws Exception {
        String src = Files.readString(Path.of(
                "src/main/java/dev/kof/compiler/nat/NativeRiscvCrossOps.java"));
        var names = new LinkedHashSet<String>();
        Matcher m = Pattern.compile("case \"([A-Za-z]+)\"\\s*[,->]").matcher(src);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    private static Set<String> caseNames(String body) {
        var names = new LinkedHashSet<String>();
        Matcher m = Pattern.compile("case\\s+((?:\"[A-Za-z]+\"\\s*,?\\s*)+)->").matcher(body);
        while (m.find()) {
            Matcher q = Pattern.compile("\"([A-Za-z]+)\"").matcher(m.group(1));
            while (q.find()) {
                names.add(q.group(1));
            }
        }
        return names;
    }

    @Test
    @DisplayName("todo método do registry de String tem destino JS classificado (§424)")
    void everyStringMethodHasAClassifiedJsTarget() throws Exception {
        Set<String> registry = registryMethods();

        Set<String> handled = new TreeSet<>(RUNTIME_SUFFIX);
        for (String n : jsExplicitCases()) {
            if (registry.contains(n)) {
                handled.add(n);
            }
        }

        Set<String> classified = new TreeSet<>(JS_NATIVE_MEMBER);
        classified.addAll(handled);
        classified.addAll(JS_KNOWN_GAP);

        assertEquals(new TreeSet<>(registry), classified, () -> {
            var missing = new TreeSet<>(registry);
            missing.removeAll(classified);
            var extra = new TreeSet<>(classified);
            extra.removeAll(registry);
            return "registry x classificacao JS divergem — sem classificacao: " + missing
                    + " ; classificados a mais: " + extra
                    + " (classifique cada metodo novo; §424 documenta os gaps reais)";
        });
    }

    @Test
    @DisplayName("o gap JS do §424 e exatamente o conjunto documentado — nao cresce nem some")
    void knownJsGapIsPinnedToTheDocumentedSet() throws Exception {
        assertEquals(Set.of("matches", "replaceAll", "replaceFirst", "getBytes"), JS_KNOWN_GAP);
    }

    @Test
    @DisplayName("todo método do registry tem destino NATIVO x86/riscv classificado (§424)")
    void everyStringMethodHasAClassifiedNativeTarget() throws Exception {
        Set<String> registry = registryMethods();

        Set<String> handled = new TreeSet<>(RUNTIME_SUFFIX);
        for (String n : x86Handled()) {
            if (registry.contains(n)) {
                handled.add(n);
            }
        }
        for (String n : riscvHandled()) {
            if (registry.contains(n)) {
                handled.add(n);
            }
        }

        Set<String> classified = new TreeSet<>(handled);
        classified.addAll(JS_KNOWN_GAP);

        assertEquals(new TreeSet<>(registry), classified, () -> {
            var missing = new TreeSet<>(registry);
            missing.removeAll(classified);
            return "registry x cobertura NATIVA divergem — sem lowering x86/riscv: " + missing
                    + " (§424 documenta os gaps reais; o resto tem de ser tratado)";
        });
    }

    @Test
    @DisplayName("todo método com case JS explicito ainda existe no registry (case órfão = bug)")
    void jsExplicitCasesAreRegistered() throws Exception {
        Set<String> registry = registryMethods();
        Set<String> cased = jsExplicitCases();
        var orphan = new LinkedHashSet<String>();
        for (String n : List.of("charAt", "length", "isEmpty", "equals", "equalsIgnoreCase",
                "replace", "compareTo", "split")) {
            assertTrue(cased.contains(n), "JsCallEmitter perdeu o case de " + n);
            if (!registry.contains(n)) {
                orphan.add(n);
            }
        }
        assertTrue(orphan.isEmpty(), "case JS sem metodo no registry: " + orphan);
    }
}
