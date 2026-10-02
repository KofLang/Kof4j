package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X10 fatia 1 — trava do StdCatalog contra a FONTE REAL dos typers (padrão
 * RuntimeSlices): (a) para cada um dos 7 namespaces, a lista publicada é
 * EXATAMENTE o conjunto de case-literals do `switch (name)` do staticMethod;
 * (b) o dispatch do KofStd cobre exatamente as chaves do catálogo;
 * (c) cada membro do catálogo resolve para um StdCall não-nulo em alguma
 * forma de aridade/tipo plausível (o catálogo não inventa nomes).
 */
class StdCatalogTest {

    private static String methodBody(String src, String signature) {
        int i = src.indexOf(signature);
        assertTrue(i >= 0, "assinatura ausente: " + signature);
        int open = src.indexOf('{', i);
        int depth = 0, k = open;
        while (true) {
            char c = src.charAt(k);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) break;
            }
            k++;
        }
        return src.substring(open, k + 1);
    }

    private static Set<String> caseLiterals(String body) {
        int sw = body.indexOf("switch (name)");
        assertTrue(sw >= 0, "sem switch (name): " + body.substring(0, Math.min(80, body.length())));
        String blk = methodBody(body.substring(sw), "");
        Set<String> out = new LinkedHashSet<>();
        Matcher m = Pattern.compile("case \"(\\w+)\"").matcher(blk);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    private static String source(String cls) throws IOException {
        return Files.readString(Path.of("src/main/java/dev/kof/compiler/" + cls + ".java"));
    }

    @Test
    void catalogMatchesTyperCaseLiterals() throws Exception {
        record Entry(String ns, String cls, List<String> fns) {}
        List<Entry> entries = List.of(
                new Entry("math", "KofMath", KofMath.functions()),
                new Entry("strings", "KofStrings", KofStrings.functions()),
                new Entry("encoding", "KofEncoding", KofEncoding.functions()),
                new Entry("net", "KofNet", KofNet.functions()),
                new Entry("uuid", "KofUuid", KofUuid.functions()),
                new Entry("random", "KofRandom", KofRandom.functions()),
                new Entry("rng", "KofRng", KofRng.functions()));
        for (Entry e : entries) {
            List<String> inSource = topCaseNames(switchBlock(
                    methodBody(source(e.cls()), "staticMethod(String namespace"),
                    "switch (name)"));
            // 19/09 fatia 3: o lock antigo (regex `case "x"`) via so o 1o literal
            // de um case de familia — foi exatamente como net/encoding esconderam
            // membros. topCaseNames segue as virgulas (a garantia agora e maior).
            assertEquals(new LinkedHashSet<>(inSource), new LinkedHashSet<>(e.fns()),
                    e.ns() + ": catalog != case-literals do " + e.cls());
            assertEquals(e.fns(), StdCatalog.membersOf(e.ns()), e.ns() + ": membros");
        }
    }

    @Test
    void kofStdDispatchCoversExactlyTheCatalog() throws Exception {
        String std = source("KofStd");
        Set<String> dispatched = new LinkedHashSet<>();
        Matcher m = Pattern.compile("Kof(\\w+)\\.staticMethod\\(").matcher(std);
        while (m.find()) dispatched.add("Kof" + m.group(1));
        Set<String> catalogClasses = new LinkedHashSet<>(List.of(
                "KofMath", "KofStrings", "KofEncoding", "KofNet",
                "KofUuid", "KofRandom", "KofRng", "KofBuffer"));
        assertEquals(dispatched, catalogClasses,
                "dispatch do KofStd mudou sem atualizar o catálogo");
        assertEquals(35, StdCatalog.namespaces().size(), StdCatalog.namespaces().toString());
        for (String ns : List.of("math", "strings", "encoding", "net", "uuid", "random",
                "rng", "time", "http", "db", "cache", "buffer", "process", "shell", "ssh", "passwords", "crypto",
                "jwt", "secrets", "security", "auth", "json", "log", "orm", "config",
                "gpu", "mq", "validation", "observability", "tetris", "image", "Image", "Audio",
                "Mic", "Video")) {
            assertTrue(StdCatalog.isNamespace(ns), ns);
        }
    }

    @Test
    void everyCatalogMemberResolvesInRealTyper() {
        List<Type> prim = List.of();
        List<List<Type>> shapes = new ArrayList<>();
        Type[] ts = {Type.PrimitiveType.INT, BuiltinTypes.STRING, Type.PrimitiveType.BOOL,
                Type.PrimitiveType.DOUBLE, Type.PrimitiveType.LONG, Type.PrimitiveType.CHAR};
        shapes.add(prim);
        for (Type a : ts) {
            shapes.add(List.of(a));
            for (Type b : ts) {
                shapes.add(List.of(a, b));
                for (Type c : ts) {
                    shapes.add(List.of(a, b, c));
                }
            }
        }
                for (String ns : List.of("math", "strings", "encoding", "net",
                "uuid", "random", "rng")) {
            for (String fn : StdCatalog.membersOf(ns)) {
                boolean ok = false;
                for (List<Type> sh : shapes) {
                    if (KofStd.staticMethod(ns, fn, sh) != null) { ok = true; break; }
                }
                assertTrue(ok, ns + "." + fn + " não resolve no KofStd \u2014 catálogo inventado?");
            }
        }
    }
    // ── X10 fatia 2: dispatch próprio (time/http/db/cache/process/security) ──

    /** switch-block do anchor dado dentro de um corpo de método (brace-match). */
    private static String switchBlock(String body, String anchor) {
        int i = body.indexOf(anchor);
        assertTrue(i >= 0, "sem " + anchor);
        int j = body.indexOf('{', i);
        int depth = 0, k = j;
        while (true) {
            char c = body.charAt(k);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) break;
            }
            k++;
        }
        return body.substring(j, k + 1);
    }

    /**
     * Nomes de label de `case` NO TOPO do switch (profundidade 1): varre
     * `case "a", "b",` multi-linha (remove comentários) sem colher `case`
     * de switches internos (ex.: checagens de tipo aninhadas no db/seg).
     */
    private static List<String> topCaseNames(String blk) {
        blk = blk.replaceAll("//[^\n]*", "");
        List<String> out = new ArrayList<>();
        int d = 0, i = 0, n = blk.length();
        while (i < n) {
            char c = blk.charAt(i);
            if (c == '{') d++;
            else if (c == '}') d--;
            else if (d == 1 && blk.startsWith("case ", i)) {
                int j = i + 5;
                while (true) {
                    java.util.regex.Matcher m =
                            Pattern.compile("\\s*\"(\\w+)\"").matcher(blk.substring(j));
                    if (!m.find()) break;
                    out.add(m.group(1));
                    j += m.end();
                    java.util.regex.Matcher c2 = Pattern.compile("\\s*,").matcher(blk.substring(j));
                    if (c2.find() && c2.start() == 0) { j += c2.end(); continue; }
                    break;
                }
                int arrow = blk.indexOf("->", j);
                i = arrow >= 0 ? arrow + 2 : j;
                continue;
            }
            i++;
        }
        return new ArrayList<>(new LinkedHashSet<>(out));
    }

    @Test
    void slice2ListsMatchTyperSources() throws Exception {
        List<String> timeExpected = new ArrayList<>(topCaseNames(switchBlock(
                methodBody(source("KofTime"), "isTimeMethod(String name)"), "switch (name)")));
        assertEquals(timeExpected.stream().sorted().toList(),
                KofTime.functions().stream().sorted().toList(), "time");
        assertEquals(topCaseNames(switchBlock(
                methodBody(source("KofHttp"), "isHttpMethod(String name)"), "switch (name)")),
                KofHttp.functions(), "http");
        assertEquals(topCaseNames(switchBlock(
                methodBody(source("KofCache"), "isCacheMethod(String name)"), "switch (name)")),
                KofCache.functions(), "cache");
        List<String> dbExpected = new ArrayList<>(topCaseNames(switchBlock(
                methodBody(source("KofDb"),
                        "staticCall(String name, List<Type> argTypes, boolean typed)"),
                "switch (name)")));
        for (String fam : List.of("isQuery", "isExecute")) {
            java.util.regex.Matcher fm = Pattern.compile("\"(\\w+)\"\\.equals\\(name\\)")
                    .matcher(methodBody(source("KofDb"), fam + "(String name)"));
            while (fm.find()) dbExpected.add(fm.group(1));
        }
        List<String> dbGot = new ArrayList<>(KofDb.functions());
        assertEquals(dbExpected.stream().sorted().toList(), dbGot.stream().sorted().toList(), "db");
        assertEquals(List.of("run", "spawn", "exit"), KofProcess.functions(), "process");
        // 19/09 LSP-A fatia 2: spawn/exit vivem em entryCall/exitCall roteados por
        // ExpressionProcessCallLowerer (case-literals la) — a lista acima tem que
        // bater com os nomes aceitos pelo dispatcher real (behavioural, abaixo).
        assertNotNull(KofProcess.entryCall("spawn", List.of(BuiltinTypes.STRING)), "spawn binda");
        assertNull(KofProcess.entryCall("run", List.of()), "run sem programa NAO binda");
        assertNotNull(KofProcess.exitCall(List.of(Type.PrimitiveType.INT)), "exit(Int)");
    }

    @Test
    void shellCatalogMatchesDispatchAndSignatures() {
        assertEquals(List.of("cmd", "run", "runWith", "pipeline", "ok"), KofShell.functions(), "shell");
        assertNotNull(KofShell.staticCall("cmd",
                List.of(BuiltinTypes.STRING, KofShell.STRING_LIST)), "shell.cmd");
        assertNotNull(KofShell.staticCall("run", List.of(BuiltinTypes.STRING)), "shell.run/1");
        assertNotNull(KofShell.staticCall("run",
                List.of(BuiltinTypes.STRING, KofShell.STRING_LIST)), "shell.run/2");
        assertNotNull(KofShell.staticCall("runWith", List.of(KofShell.STRING_LIST,
                BuiltinTypes.STRING, BuiltinTypes.MAP)), "shell.runWith");
        assertNotNull(KofShell.staticCall("runWith", List.of(KofProcess.STRING_LIST,
                BuiltinTypes.STRING, BuiltinTypes.MAP)), "shell.runWith (empty argv inferred List<Object>)");
        assertNull(KofShell.staticCall("runWith",
                List.of(BuiltinTypes.STRING, BuiltinTypes.STRING, BuiltinTypes.MAP)), "shell.runWith(String argv)");
        assertNotNull(KofShell.staticCall("pipeline",
                List.of(KofShell.STRING_LIST_LIST)), "shell.pipeline");
        assertNotNull(KofShell.staticCall("ok", List.of(KofProcess.RESULT)), "shell.ok");
        assertNull(KofShell.staticCall("ok", List.of(BuiltinTypes.STRING)), "shell.ok(String)");
        assertNull(KofShell.staticCall("cmd", List.of(BuiltinTypes.STRING)), "shell.cmd/1");
        assertNull(KofShell.staticCall("nope", List.of()), "shell.?");
        assertEquals(KofShell.functions(), StdCatalog.membersOf("shell"), "catálogo shell");
    }

    @Test
    void securityCatalogMatchesNestedSwitches() throws Exception {
        String body = methodBody(source("KofSecurity"),
                "staticMethod(String namespace, String name, List<Type> argTypes)");
        Matcher outer = Pattern.compile("case \"(\\w+)\" -> switch \\(name\\)")
                .matcher(body);
        Set<String> seen = new LinkedHashSet<>();
        while (outer.find()) {
            String ns = outer.group(1);
            seen.add(ns);
            int at = body.indexOf("case \"" + ns + "\" -> switch (name)");
            List<String> inSource = topCaseNames(switchBlock(body.substring(at), "switch (name)"));
            assertEquals(inSource, KofSecurity.functions().get(ns),
                    "security ns " + ns);
        }
        // o catálogo de segurança e o NAMESPACES do typer cobrem o mesmo conjunto
        assertEquals(new LinkedHashSet<>(KofSecurity.NAMESPACES), seen,
                "NAMESPACES != chaves dos switches");
        for (String ns : seen) {
            assertTrue(StdCatalog.isNamespace(ns), "no catálogo: " + ns);
            assertEquals(KofSecurity.functions().get(ns), StdCatalog.membersOf(ns));
        }
    }

    @Test
    void slice2MembersResolveInRealDispatch() {
        List<Type> ts = List.of(Type.PrimitiveType.INT, BuiltinTypes.STRING,
                Type.PrimitiveType.BOOL, Type.PrimitiveType.DOUBLE,
                Type.PrimitiveType.LONG, Type.PrimitiveType.CHAR,
                Type.UnknownType.UNKNOWN);
        List<List<Type>> shapes = new ArrayList<>();
        shapes.add(List.of());
        for (Type a : ts) {
            shapes.add(List.of(a));
            for (Type b : ts) {
                shapes.add(List.of(a, b));
                for (Type c : ts) {
                    shapes.add(List.of(a, b, c));
                    for (Type e : ts) shapes.add(List.of(a, b, c, e));
                }
            }
        }
        // aridades 5/6 alvo (time.daysBetween = INTx6; calendário/hex shapes):
        for (int n = 5; n <= 8; n++) {
            for (Type fill : ts) {
                List<Type> sh = new ArrayList<>();
                for (int k = 0; k < n; k++) sh.add(fill);
                shapes.add(sh);
            }
        }
        // misturas Int[]/Int do gpu.dispatch*/mv* (ArrayType no cabeçalho):
        Type arr = new Type.ArrayType(Type.PrimitiveType.INT);
        for (int n = 1; n <= 6; n++) {
            for (int k = 0; k <= n; k++) {
                List<Type> sh = new ArrayList<>();
                for (int i2 = 0; i2 < k; i2++) sh.add(arr);
                for (int i2 = k; i2 < n; i2++) sh.add(Type.PrimitiveType.INT);
                shapes.add(sh);
            }
        }
        for (String fn : KofTime.functions()) {
            assertTrue(shapes.stream().anyMatch(sh -> KofTime.staticCall(fn, sh) != null),
                    "time." + fn);
        }
        for (String fn : KofHttp.functions()) {
            assertTrue(shapes.stream().anyMatch(sh -> KofHttp.staticCall(fn, sh) != null),
                    "http." + fn);
        }
        for (String fn : KofCache.functions()) {
            assertTrue(shapes.stream().anyMatch(sh -> KofCache.staticCall(fn, sh) != null),
                    "cache." + fn);
        }
        for (String fn : KofDb.functions()) {
            assertTrue(shapes.stream().anyMatch(sh ->
                            KofDb.staticCall(fn, sh, true) != null
                                    || KofDb.staticCall(fn, sh, false) != null),
                    "db." + fn);
        }
        assertTrue(shapes.stream().anyMatch(sh -> KofProcess.runCall(sh) != null),
                "process.run");
        for (String ns : KofSecurity.functions().keySet()) {
            for (String fn : KofSecurity.functions().get(ns)) {
                assertTrue(shapes.stream().anyMatch(sh ->
                                KofSecurity.staticMethod(ns, fn, sh) != null),
                        ns + "." + fn);
            }
        }
    }
    // ── X10 fatia 3: receiver-typed (MemberCallNamespaces) ──

    @Test
    void slice3ListsMatchTyperSources() throws Exception {
        assertEquals(topCaseNames(switchBlock(
                methodBody(source("KofLog"), "isLogMethod(String name)"), "switch (name)")),
                KofLog.functions(), "log");
        assertEquals(topCaseNames(switchBlock(
                methodBody(source("KofOrm"),
                        "staticCall(String name, List<Type> argTypes, boolean typed"),
                "switch (name)")),
                KofOrm.functions(), "orm");
        assertEquals(topCaseNames(switchBlock(
                methodBody(source("KofConfig"), "staticCall(String name, List<Type> argTypes)"),
                "switch (name)")),
                KofConfig.functions(), "config");
        assertEquals(topCaseNames(switchBlock(
                methodBody(source("KofGpu"), "staticCall(String name, List<Type> argTypes)"),
                "switch (name)")),
                KofGpu.functions(), "gpu");
        assertEquals(topCaseNames(switchBlock(
                methodBody(source("KofMq"), "staticCall(String name, List<Type> argTypes)"),
                "switch (name)")),
                KofMq.functions(), "mq");
        assertEquals(topCaseNames(switchBlock(
                methodBody(source("KofValidation"),
                        "staticMethod(String namespace, String name, List<Type> argTypes)"),
                "switch (name)")),
                KofValidation.functions(), "validation");
        assertEquals(topCaseNames(switchBlock(
                methodBody(source("KofObservability"),
                        "staticMethod(String namespace, String name, List<Type> argTypes)"),
                "switch (name)")),
                KofObservability.functions(), "observability");
        assertEquals(topCaseNames(switchBlock(
                methodBody(source("KofTetris"),
                        "staticMethod(String namespace, String name, int argCount)"),
                "switch (name)")),
                KofTetris.functions(), "tetris");
    }

    /** Image/Audio/Video/Mic: os maps aninhados do KofMedia são a fonte. */
    @Test
    void mediaNestedMatchesSource() throws Exception {
        String body = methodBody(source("KofMedia"),
                "staticCall(String namespace, String name, int argCount)");
        Matcher outer = Pattern.compile("case \"(\\w+)\" -> switch \\(name\\)").matcher(body);
        int seen = 0;
        while (outer.find()) {
            String ns = outer.group(1);
            seen++;
            int at = body.indexOf("case \"" + ns + "\" -> switch (name)");
            List<String> inSource = topCaseNames(switchBlock(body.substring(at), "switch (name)"));
            assertEquals(inSource, StdCatalog.membersOf(ns), "media ns " + ns);
        }
        assertEquals(4, seen, "esperava Image/Audio/Video/Mic");
    }

    /** json não tem dispatcher nomeado: a fonte é o bloco inline do MemberCallNamespaces. */
    @Test
    void jsonMembersPinnedToValidatorSource() throws Exception {
        String src = Files.readString(
                Path.of("src/main/java/dev/kof/compiler/MemberCallNamespaces.java"));
        int i = src.indexOf("\"json\".equals(rid.name())");
        assertTrue(i >= 0);
        String block = src.substring(i, src.indexOf("\n        }", i));
        List<String> names = new ArrayList<>();
        Matcher m = Pattern.compile("\"(\\w+)\"\\.equals\\(mc\\.methodName\\(\\)\\)").matcher(block);
        while (m.find()) names.add(m.group(1));
        assertEquals(new LinkedHashSet<>(names), new LinkedHashSet<>(StdCatalog.membersOf("json")),
                "json drift");
    }

    @Test
    void slice3MembersResolveInRealDispatch() {
        List<Type> ts = List.of(Type.PrimitiveType.INT, BuiltinTypes.STRING,
                Type.PrimitiveType.BOOL, Type.PrimitiveType.DOUBLE,
                Type.PrimitiveType.LONG, Type.PrimitiveType.CHAR,
                Type.UnknownType.UNKNOWN);
        List<List<Type>> shapes = new ArrayList<>();
        shapes.add(List.of());
        for (Type a : ts) {
            shapes.add(List.of(a));
            for (Type b : ts) {
                shapes.add(List.of(a, b));
                for (Type c : ts) {
                    shapes.add(List.of(a, b, c));
                    for (Type e : ts) shapes.add(List.of(a, b, c, e));
                }
            }
        }
        for (int n = 5; n <= 8; n++) {
            for (Type fill : ts) {
                List<Type> sh = new ArrayList<>();
                for (int k = 0; k < n; k++) sh.add(fill);
                shapes.add(sh);
            }
        }
        Type arr = new Type.ArrayType(Type.PrimitiveType.INT);
        for (int n = 1; n <= 6; n++) {
            for (int k = 0; k <= n; k++) {
                List<Type> sh = new ArrayList<>();
                for (int i2 = 0; i2 < k; i2++) sh.add(arr);
                for (int i2 = k; i2 < n; i2++) sh.add(Type.PrimitiveType.INT);
                shapes.add(sh);
            }
        }
        for (String fn : KofLog.functions()) {
            assertTrue(shapes.stream().anyMatch(sh -> KofLog.staticCall(fn, sh) != null),
                    "log." + fn);
        }
        for (String fn : KofOrm.functions()) {
            assertTrue(shapes.stream().anyMatch(sh ->
                            KofOrm.staticCall(fn, sh, false, null) != null
                                    || KofOrm.staticCall(fn, sh, true, "User") != null),
                    "orm." + fn);
        }
        for (String fn : KofConfig.functions()) {
            assertTrue(shapes.stream().anyMatch(sh -> KofConfig.staticCall(fn, sh) != null),
                    "config." + fn);
        }
        for (String fn : KofGpu.functions()) {
            assertTrue(shapes.stream().anyMatch(sh -> KofGpu.staticCall(fn, sh) != null),
                    "gpu." + fn);
        }
        for (String fn : KofMq.functions()) {
            assertTrue(shapes.stream().anyMatch(sh -> KofMq.staticCall(fn, sh) != null),
                    "mq." + fn);
        }
        for (String fn : KofValidation.functions()) {
            assertTrue(shapes.stream().anyMatch(sh ->
                            KofValidation.staticMethod("validation", fn, sh) != null),
                    "validation." + fn);
        }
        for (String fn : KofObservability.functions()) {
            assertTrue(shapes.stream().anyMatch(sh ->
                            KofObservability.staticMethod("observability", fn, sh) != null),
                    "observability." + fn);
        }
        for (String fn : KofTetris.functions()) {
            assertTrue(java.util.stream.IntStream.rangeClosed(0, 8)
                            .anyMatch(n -> KofTetris.staticMethod("tetris", fn, n) != null),
                    "tetris." + fn);
        }
        for (String ns : List.of("Image", "Audio", "Video", "Mic")) {
            for (String fn : StdCatalog.membersOf(ns)) {
                assertTrue(java.util.stream.IntStream.rangeClosed(0, 8)
                                .anyMatch(n -> KofMedia.staticCall(ns, fn, n) != null),
                        ns + "." + fn);
            }
        }
    }
}
