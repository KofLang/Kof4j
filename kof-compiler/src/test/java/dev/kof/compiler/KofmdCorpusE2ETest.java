package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-KOFMD Fase 3 fatia 3.8 — the golden corpus under {@code libs/kofmd/corpus/}
 * (spec §19: one idea per file, simultaneously documentation, test, example and
 * AI-evaluation material). Each file is parsed, formatted and validated by the
 * pure-Kof library; the corpus proves the acceptance gates §22 items 1, 2, 3
 * and 4 on real documents: parser green, prose byte-preserved, vocabulary
 * valid, schema validation clean and canonical form idempotent.
 */
class KofmdCorpusE2ETest extends KofmdRunSupport implements LibraryInstallSupport {



    private static final Set<String> EXPECTED = new TreeSet<>(List.of(
            "agent-blocked", "agent-context", "agent-decision", "agent-handoff",
            "agent-instructions", "agent-memory", "agent-result", "agent-task",
            "basic", "mixed-markdown", "schema", "typed-data"));

    @Test
    void goldenCorpusParsesFormatsAndValidates() throws Exception {
        Path corpus = findLibraryRoot().resolve("corpus");
        assertTrue(Files.isDirectory(corpus), () -> "corpus dir missing: " + corpus);

        List<String> names = new ArrayList<>();
        try (var files = Files.list(corpus)) {
            files.filter(p -> p.getFileName().toString().endsWith(".md"))
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .forEach(n -> names.add(n.substring(0, n.length() - 3)));
        }
        assertEquals(EXPECTED, new TreeSet<>(names), "corpus must ship exactly the spec §19 files");

        StringBuilder nameLiterals = new StringBuilder();
        StringBuilder textLiterals = new StringBuilder();
        for (String name : names) {
            if (nameLiterals.length() > 0) {
                nameLiterals.append(", ");
                textLiterals.append(", ");
            }
            nameLiterals.append(kofString(name));
            textLiterals.append(kofString(Files.readString(corpus.resolve(name + ".md"))));
        }

        String program = "import kofmd.Kofmd\n\n"
                + "main() {\n"
                + "    var tool = KofmdTool()\n"
                + "    var names = listOf(" + nameLiterals + ")\n"
                + "    var texts = listOf(" + textLiterals + ")\n"
                + "    var i = 0\n"
                + "    while (i < texts.size) {\n"
                + "        var text = texts.get(i)\n"
                + "        var doc = tool.parse(text)\n"
                + "        if (doc.blocks().size == 0) {\n"
                + "            throw \"empty parse: \" + names.get(i)\n"
                + "        }\n"
                + "        var once = tool.format(doc)\n"
                + "        var twice = tool.format(tool.parse(once))\n"
                + "        if (once != twice) {\n"
                + "            throw \"not idempotent: \" + names.get(i)\n"
                + "        }\n"
                + "        var vocab = tool.validateVocabulary(tool.parse(text))\n"
                + "        if (vocab.size != 0) {\n"
                + "            throw \"vocabulary diag: \" + names.get(i) + \" \" + vocab.get(0)\n"
                + "        }\n"
                + "        if (names.get(i) == \"schema\") {\n"
                + "            var schemas = tool.parseSchemas(text)\n"
                + "            if (schemas.size != 1) {\n"
                + "                throw \"schema count: \" + schemas.size.toString()\n"
                + "            }\n"
                + "            var diags = tool.validateDoc(doc, schemas.get(0))\n"
                + "            if (diags.size != 0) {\n"
                + "                throw \"schema diag: \" + diags.get(0)\n"
                + "            }\n"
                + "        }\n"
                + "        var b = 0\n"
                + "        while (b < doc.blocks().size) {\n"
                + "            var blk = doc.blocks().get(b)\n"
                + "            var p = 0\n"
                + "            while (p < blk.prose().size) {\n"
                + "                var line = blk.prose().get(p).trim()\n"
                + "                if (line.length > 0 && !once.contains(line)) {\n"
                + "                    throw \"prose lost: \" + names.get(i) + \" [\" + line + \"]\"\n"
                + "                }\n"
                + "                p = p + 1\n"
                + "            }\n"
                + "            b = b + 1\n"
                + "        }\n"
                + "        i = i + 1\n"
                + "    }\n"
                + "    println(\"kofmd-3.8-ok\")\n"
                + "}\n";

        runKof(program);
    }

    
    private static String kofString(String text) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> { }
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }




    @Override
    public String libraryName() {
        return "kofmd";
    }

    @Override
    public java.util.List<String> libraryMarkers() {
        return java.util.List.of("Kofmd.kf", "KofmdTypes.kf");
    }

}
