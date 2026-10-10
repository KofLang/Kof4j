package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-KOFMD Fase 3 fatia 3.1 — the pure-Kof block scanner in {@code libs/kofmd}
 * parses {@code TypedField}/{@code @intent}/list/fence/schema shapes into
 * {@code KofmdDoc} records (JVM-first; other targets = honest {@code MD001}).
 */
class KofmdE2ETest extends KofmdRunSupport implements LibraryInstallSupport {



    @Test
    void typedFieldsAndIntentAndListAndFenceParse() throws Exception {
        runKof("""
            import kofmd.Kofmd

            main() {
                var tool = KofmdTool()
                var doc = tool.parse("doing: parser\\nnext: tests\\n")
                if (doc.blocks().size != 1) {
                    throw "blocks: " + doc.blocks().size.toString()
                }
                if (doc.blocks().get(0).fields().size != 2) {
                    throw "fields: " + doc.blocks().get(0).fields().size.toString()
                }
                if (doc.blocks().get(0).fields().get(0).name() != "doing") {
                    throw "name0"
                }
                if (doc.blocks().get(0).fields().get(1).value() != "tests") {
                    throw "value1"
                }

                var intent = tool.parse("@decision\\nAdopt narrowing.\\n")
                if (intent.blocks().get(0).intents().size != 1) {
                    throw "intents"
                }
                if (intent.blocks().get(0).intents().get(0) != "decision") {
                    throw "intent-name"
                }

                var listed = tool.parse("instructions:\\n- inspect\\n- test\\n")
                if (listed.blocks().get(0).fields().get(0).items().size != 2) {
                    throw "items"
                }
                if (listed.blocks().get(0).fields().get(0).items().get(1) != "test") {
                    throw "item1"
                }

                var fenced = tool.parse("```\\nkey: value\\n```\\n")
                if (fenced.blocks().get(0).fields().size != 0) {
                    throw "fence must stay prose"
                }
                if (fenced.blocks().get(0).prose().size == 0) {
                    throw "fence prose lost"
                }

                var prose = tool.parse("# Title\\n\\nplain prose\\n")
                if (prose.blocks().size == 0) {
                    throw "prose blocks lost"
                }
                if (prose.blocks().get(0).fields().size != 0) {
                    throw "prose must carry zero fields"
                }
                println("kofmd-3.1-ok")
            }
            """);
    }

    @Test
    void scalarInferenceAndSchemaMismatchAreMd002() throws Exception {
        runKof("""
            import kofmd.Kofmd

            main() {
                var tool = KofmdTool()
                var probe = tool.parse("stable: false\\nretries: 3\\nratio: 0.5\\nlocation: compiler/parser\\nversion: \\"3\\"\\nbig: 2147483648\\n")
                var fields = probe.blocks().get(0).fields()
                if (tool.inferField(fields.get(0)) != "Bool") {
                    throw "infer stable"
                }
                if (tool.inferField(fields.get(1)) != "Int") {
                    throw "infer retries"
                }
                if (tool.inferField(fields.get(2)) != "Float") {
                    throw "infer ratio"
                }
                if (tool.inferField(fields.get(3)) != "String") {
                    throw "infer location"
                }
                if (tool.inferField(fields.get(4)) != "String") {
                    throw "quoted forces String"
                }
                if (tool.inferField(fields.get(5)) != "String") {
                    throw "Int is 32-bit: overflow stays String"
                }

                var listed = tool.parse("instructions:\\n- inspect\\n")
                if (tool.inferField(listed.blocks().get(0).fields().get(0)) != "List") {
                    throw "list infers List"
                }
                var empty = tool.parse("next:\\n")
                if (tool.inferField(empty.blocks().get(0).fields().get(0)) != "Absent") {
                    throw "empty infers Absent"
                }

                var schemas = tool.parseSchemas("record Probe(Int retries, Float ratio, Bool stable)\\n")
                if (schemas.size != 1) {
                    throw "schemas: " + schemas.size.toString()
                }
                if (schemas.get(0).fields().size != 3) {
                    throw "schema fields"
                }
                var bad = tool.parse("retries: 3\\nratio: x\\nstable: yes\\n")
                var diags = tool.validateDoc(bad, schemas.get(0))
                if (diags.size != 2) {
                    throw "diags: " + diags.size.toString()
                }
                if (diags.get(0) != "MD002:2:ratio") {
                    throw "diag0: " + diags.get(0)
                }
                if (diags.get(1) != "MD002:3:stable") {
                    throw "diag1: " + diags.get(1)
                }

                var good = tool.parse("retries: 3\\nratio: 0.5\\nstable: true\\n")
                if (tool.validateDoc(good, schemas.get(0)).size != 0) {
                    throw "good doc must validate clean"
                }
                var coerced = tool.parse("ratio: 3\\n")
                if (tool.validateDoc(coerced, schemas.get(0)).size != 0) {
                    throw "Int widens to Float"
                }
                var widened = tool.parseSchemas("record Probe(Int retries, String version)\\n")
                var bare = tool.parse("version: 9\\n")
                if (tool.validateDoc(bare, widened.get(0)).size != 0) {
                    throw "any scalar widens to String"
                }
                var unknown = tool.parse("other: 1\\n")
                if (tool.validateDoc(unknown, schemas.get(0)).size != 0) {
                    throw "unknown fields stay inert"
                }
                var listedDoc = tool.parse("retries:\\n- 1\\n")
                var listDiags = tool.validateDoc(listedDoc, schemas.get(0))
                if (listDiags.size != 1) {
                    throw "list for scalar slot is MD002"
                }
                if (listDiags.get(0) != "MD002:1:retries") {
                    throw "list diag: " + listDiags.get(0)
                }
                println("kofmd-3.2-ok")
            }
            """);
    }

    @Test
    void quotedScalarAndIndentedLineStayVerbatim() throws Exception {
        runKof("""
            import kofmd.Kofmd

            main() {
                var tool = KofmdTool()
                var probe = tool.parse("version: \\"0.5.0\\"\\nretries: 3\\n")
                if (probe.blocks().get(0).fields().get(0).value() != "0.5.0") {
                    throw "quoted value"
                }
                if (!probe.blocks().get(0).fields().get(0).quoted()) {
                    throw "quoted flag"
                }
                if (probe.blocks().get(0).fields().get(1).quoted()) {
                    throw "bare must not be quoted"
                }
                var indented = tool.parse("  doing: parser\\n")
                if (indented.blocks().get(0).fields().size != 0) {
                    throw "indented line must stay prose"
                }
                println("kofmd-3.1-edges-ok")
            }
            """);
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
