package dev.kof.compiler.js;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JsRuntimeSliceRegistryTest {

    private static String legacyCoreRuntime() {
        return new StringBuilder()
                .append(JsRuntimeCore.CORE_RUNTIME)
                .append(JsRuntimeEquality.EQUALITY_RUNTIME)  // #518: kofValEq por conteudo (registro posicao 2)
                .append(JsRuntimeUiComponents.UI_COMPONENT_RUNTIME)
                .append(JsRuntimeUiLinkImageIcon.UI_LINK_IMAGE_ICON_RUNTIME)
                .append(JsRuntimeUiWidgets.UI_WIDGET_RUNTIME)
                .append(JsRuntimeUiForms.UI_FORMS_RUNTIME)
                .append(JsRuntimeUiLayout.UI_LAYOUT_RUNTIME)
                .append(JsRuntimeCollections.COLLECTIONS_RUNTIME)
                .append(JsRuntimeUiJsonMap.JSON_MAP_RUNTIME)
                .append(JsRuntimeUiWeb.UI_WEB_RUNTIME)
                .append(JsRuntimeUiWebSse.uiWebSseRuntime())
                .append(JsRuntimeUiConfig.CONFIG_RUNTIME)
                .append(JsRuntimeUiSupport.UI_SUPPORT_RUNTIME)
                .append(JsRuntimeGpuSupport.GPU_RUNTIME)  // e8d67f104: bloco "gpu" (linha 6 D-FULL-PARITY-050)
                .append(JsRuntimeUiSecurity.UI_SECURITY_RUNTIME)
                .append(JsRuntimeUiCrypto.UI_CRYPTO_RUNTIME)
                .append(JsRuntimeUiChacha.UI_CHACHA_RUNTIME)
                .append(JsRuntimeUiValidation.UI_VALIDATION_RUNTIME)
                .append(JsRuntimeUiStdlib.STDLIB_RUNTIME)
                .append(JsRuntimeUiRandom.RANDOM_RUNTIME)
                .append(JsRuntimeUiRng.RNG_RUNTIME)
                .append(JsRuntimeUiMathDouble.MATH_DOUBLE_RUNTIME)
                .append(JsRuntimeUiNumFmt.NUM_FMT_RUNTIME)
                .append(JsRuntimeBuffer.BUFFER_RUNTIME)
                .append(JsRuntimeUiNet.NET_RUNTIME)
                .append(JsRuntimeUiUuid.UUID_RUNTIME)
                .append(JsRuntimeUiWs.WS_RUNTIME)
                .append(JsRuntimeUiEvents.UI_EVENT_RUNTIME)
                .toString();
    }

    @Test
    void fullSelectionIsByteIdenticalToLegacyConcatenation() {
        JsRuntimeSlices.Selection all = JsRuntimeSlices.select(JsRuntimeSlices.allProvided());
        assertEquals(legacyCoreRuntime(), all.coreText(),
                "seleção com tudo vivo deve reproduzir o kof-runtime.mjs anterior byte a byte");
        assertEquals(JsRuntimeIo.IO_RUNTIME, all.ioText(),
                "idem para o kof-runtime-io.mjs");
    }

    @Test
    void inventoryIsFinerThanBlocks() {
        List<JsRuntimeSlices.Unit> inv = JsRuntimeSlices.inventory();
        assertTrue(inv.size() > 400,
                "a poda precisa de granularidade de função, não de família: " + inv.size());
        assertTrue(JsRuntimeSlices.allProvided().size() > 300,
                "nomes declarados no runtime: " + JsRuntimeSlices.allProvided().size());
    }

    @Test
    void helloClosureDropsUnreachableFamilies() {
        JsRuntimeSlices.Selection hello = JsRuntimeSlices.select(Set.of("kofPrintln"));
        assertTrue(hello.coreText().contains("function kofPrintln"),
                "a semente tem de estar no artefato");
        assertFalse(hello.coreText().contains("kofSecSha256"), "crypto não é alcançável por println");
        assertFalse(hello.coreText().contains("kofUiWindowNew"), "kof.ui não é alcançável por println");
        assertFalse(hello.coreText().contains("kofWsConnect"), "websocket não é alcançável por println");
        assertTrue(hello.coreText().length() < 30_000,
                "hello deve ficar na casa dos KB, não dos 170 KB: " + hello.coreText().length());
    }

    @Test
    void closureIsTransitiveNotJustDirect() {
        JsRuntimeSlices.Selection sel = JsRuntimeSlices.select(Set.of("kofSecSha256"));
        assertTrue(sel.coreText().contains("kofSecSha256"), "semente presente");
        assertTrue(sel.coreText().length() > JsRuntimeSlices.select(Set.of("kofPrintln")).coreText().length(),
                "sha256 arrasta helpers que o println não arrasta");
    }

    @Test
    void selectionIsDeterministicRegardlessOfSeedOrder() {
        List<String> seeds = new ArrayList<>(List.of(
                "kofPrintln", "kofSecSha256", "kofListNew", "kofUiWindowNew", "kofMathAbs"));
        String first = JsRuntimeSlices.select(seeds).coreText();
        for (int i = 0; i < 5; i++) {
            Collections.shuffle(seeds);
            assertEquals(first, JsRuntimeSlices.select(seeds).coreText(),
                    "mesma entrada tem de dar o mesmo artefato, na mesma ordem");
        }
    }

    @Test
    void unknownSeedDoesNotBreakSelection() {
        JsRuntimeSlices.Selection sel = JsRuntimeSlices.select(Set.of("kofPrintln", "kofNaoExiste"));
        assertTrue(sel.coreText().contains("function kofPrintln"));
    }
}
