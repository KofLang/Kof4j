package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-KOFMD Fase 3 fatia 3.7 — the surfaces the LSP hook consumes: keyLabel
 * (§9 kind per special key, empty for plain TypedFields) and the already
 * line-carrying vocabulary diagnostics (§14) resolved per construct line so
 * publishDiagnostics can place a range without re-scanning the buffer.
 */
class KofmdLspSupportE2ETest extends KofmdRunSupport implements LibraryInstallSupport {



    @Test
    void keyLabelCoversVocabularyKinds() throws Exception {
        runKof("""
            import kofmd.Kofmd

            main() {
                var tool = KofmdTool()
                if (tool.keyLabel("doing") != "continuity" || tool.keyLabel("last") != "continuity") {
                    throw "continuity keys mislabeled"
                }
                if (tool.keyLabel("state") != "status" || tool.keyLabel("instructions") != "action") {
                    throw "status/action mislabeled"
                }
                if (tool.keyLabel("reason") != "modifier" || tool.keyLabel("symbol") != "modifier") {
                    throw "modifiers mislabeled"
                }
                if (tool.keyLabel("question") != "dialogue" || tool.keyLabel("answer") != "dialogue") {
                    throw "dialogue mislabeled"
                }
                if (tool.keyLabel("name") != "" || tool.keyLabel("") != "") {
                    throw "plain keys must have no special label"
                }
                println("kofmd-3.7-label-ok")
            }
            """);
    }

    @Test
    void hoverInferenceAndDiagnosticLinesAlign() throws Exception {
        runKof("""
            import kofmd.Kofmd

            main() {
                var tool = KofmdTool()
                var doc = tool.parse("doing: parser\\n\\nretries: 3\\n@todo\\nflag: \\"42\\"\\n")

                var doing = doc.blocks().get(0).fields().get(0)
                if (doing.line() != 1) {
                    throw "doing line: " + doing.line().toString()
                }
                if (tool.keyLabel(doing.name()) != "continuity") {
                    throw "hover label doing"
                }

                var second = doc.blocks().get(1).fields().get(0)
                if (second.name() != "retries" || second.line() != 3) {
                    throw "retries line: " + second.line().toString()
                }
                if (tool.inferField(second) != "Int") {
                    throw "retries inference"
                }
                if (tool.keyLabel(second.name()) != "") {
                    throw "retries must be plain"
                }

                var flag = doc.blocks().get(1).fields().get(1)
                if (flag.name() != "flag" || flag.line() != 5) {
                    throw "flag line: " + flag.line().toString()
                }
                if (tool.inferField(flag) != "String") {
                    throw "quoted 42 must infer String for hover"
                }

                var diags = tool.validateVocabulary(doc)
                if (diags.size != 1) {
                    throw "diags: " + diags.size.toString()
                }
                if (diags.get(0) != "MD002:4:@todo") {
                    throw "diag line golden: " + diags.get(0)
                }
                if (tool.hoverFor("doing: parser\\n", 1, "doing") != "continuity | String") {
                    throw "hover label+type: " + tool.hoverFor("doing: parser\\n", 1, "doing")
                }
                if (tool.hoverFor("flag: \\"42\\"\\n", 1, "flag") != "TypedField | String") {
                    throw "hover quoted: " + tool.hoverFor("flag: \\"42\\"\\n", 1, "flag")
                }
                if (tool.hoverFor("plain: x\\n", 1, "retry") != "") {
                    throw "hover wrong key must be empty"
                }
                if (tool.hoverFor("instructions:\\n", 1, "instructions") != "action") {
                    throw "list header hovers label only"
                }
                println("kofmd-3.7-hover-ok")
            }
            """);
    }

    


    @Override
    public String libraryName() {
        return "kofmd";
    }

    @Override
    public java.util.List<String> libraryMarkers() {
        return java.util.List.of("Kofmd.kf");
    }

}
