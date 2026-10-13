package dev.kof.cli;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

/**
 * `kof md check` (spec docs/spec/kofmd.md sec.15): parse + validar; exit != 0
 * em qualquer MDxxx com file:line (sec.14). Toda decisao (parse, vocabulario
 * fechado, prosa-livre, schemas declarados) e da lib Kof pura `libs/kofmd`;
 * esta classe so gera o main minimo, formata a saida humana e decide o exit
 * code. Schemas `record` do proprio doc validam os campos (spec sec.8).
 */
final class CmdMdCheck {

    private CmdMdCheck() {}

    static String generatedMain(Path doc) {
        String path = CmdMd.literal(doc);
        return "import kofmd.Kofmd\n\n"
                + "main() {\n"
                + "    val tool = KofmdTool()\n"
                + "    val source = File(\"" + path + "\")\n"
                + "    val maybe = source.readText()\n"
                + "    var text = \"\"\n"
                + "    if (maybe != null) {\n"
                + "        text = maybe\n"
                + "    } else {\n"
                + "        throw \"cannot read " + path + "\"\n"
                + "    }\n"
                + "    val doc = tool.parse(text)\n"
                + "    val diags = tool.validateVocabulary(doc)\n"
                + "    val schemas = tool.parseSchemas(text)\n"
                + "    var s = 0\n"
                + "    while (s < schemas.size) {\n"
                + "        val sdiags = tool.validateDoc(doc, schemas.get(s))\n"
                + "        var k = 0\n"
                + "        while (k < sdiags.size) {\n"
                + "            diags.add(sdiags.get(k))\n"
                + "            k = k + 1\n"
                + "        }\n"
                + "        s = s + 1\n"
                + "    }\n"
                + "    var index = 0\n"
                + "    while (index < diags.size) {\n"
                + "        println(\"KOFMD-DIAG \" + diags.get(index))\n"
                + "        index = index + 1\n"
                + "    }\n"
                + "    println(\"KOFMD-CHECK-DONE\")\n"
                + "}\n";
    }
    static int report(Path doc, String output, PrintStream out, PrintStream err) {
        List<String> lines = output.lines().filter(l -> !l.isEmpty()).toList();
        boolean done = lines.stream().anyMatch(l -> l.equals("KOFMD-CHECK-DONE"));
        if (!done) {
            err.println("md: check produced no completion marker (internal)");
            return 1;
        }
        int count = 0;
        for (String line : lines) {
            if (!line.startsWith("KOFMD-DIAG ")) continue;
            count++;
            String diag = line.substring("KOFMD-DIAG ".length());
            String code = beforeColon(diag);
            String rest = afterFirstColon(diag);
            String at = doc + ":" + beforeColon(rest);
            String message = afterFirstColon(rest);
            out.println(at + ": " + code + ": " + human(code, message));
        }
        if (count == 0) {
            out.println(doc + " — no MDxxx");
            return 0;
        }
        out.println(count + " diagnostic(s)");
        return 1;
    }

    static String human(String code, String subject) {
        if (code.equals("MD002") && subject.startsWith("@")) {
            return "intent '" + subject.substring(1) + "' outside reserved vocabulary (spec kofmd App.A)";
        }
        if (code.equals("MD002") && subject.equals("instructions")) {
            return "instructions must be tokens, not free prose (spec kofmd §10)";
        }
        if (code.equals("MD002")) {
            return "value disagrees with schema type or slot shape (spec kofmd §14)";
        }
        return subject;
    }

    static String beforeColon(String text) {
        int index = text.indexOf(':');
        return index < 0 ? text : text.substring(0, index);
    }

    static String afterFirstColon(String text) {
        int index = text.indexOf(':');
        return index < 0 ? "" : text.substring(index + 1);
    }

}
