package dev.kof.compiler;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.abort;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * KofJS em um browser real — o alvo que o KofJS existe para
 * cobrir além do GraalJS embutido. Compila um programa kof.ui para JS, serve o
 * diretório de saída por HTTP local (módulos ESM não carregam via file://) e
 * captura o DOM (Chrome/Chromium via {@code --headless --dump-dom}; Safari no
 * macOS via {@code safaridriver}), afirmando que a janela e os widgets
 * renderizaram de verdade no DOM do browser.
 *
 * Pula (assume) quando nenhum browser suportado está instalado.
 */
class KofJsBrowserE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static Path findChrome() {
        Path pathHit = findInPath();
        if (pathHit != null) return pathHit;
        // #110 (13/09): no macOS o Chrome vive dentro do bundle do app
        // (/Applications/...), nunca no PATH. Sem isso os 22 testes pulam
        // em todo Mac com Chrome instalado. Ordem: usuário antes de sistema.
        // Caminho REAL do bundle (exec) — symlink no PATH aborta o Chrome
        // (exit 134, ver #110), então nunca criar/sugerir symlink.
        Path mac = findMacBundle(
                System.getProperty("os.name", ""), System.getProperty("user.home", ""));
        if (mac != null) return mac;
        return null;
    }

    // #110: varredura do PATH pura (sem tocar no macOS) — o teste injeta
    // osName/home falsos, então o macOS precisa ficar isolado aqui.
    static Path findInPath() {
        List<String> candidates = List.of(
                "google-chrome", "google-chrome-stable", "chromium", "chromium-browser");
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;
        for (String name : candidates) {
            for (String dir : pathEnv.split(java.util.regex.Pattern.quote(String.valueOf(java.io.File.pathSeparatorChar)))) {
                Path p = Path.of(dir, name);
                if (Files.isExecutable(p)) return p;
            }
        }
        for (String app : List.of(
                "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                "/Applications/Chromium.app/Contents/MacOS/Chromium")) {
            Path p = Path.of(app);
            if (Files.isExecutable(p)) return p;
        }
        return null;
    }

    // #110 (pedido 13/09): no macOS, quando não há Chrome/Chromium, tenta o
    // Safari via safaridriver (nativo do macOS). Safari NÃO tem --dump-dom:
    // o driver é W3C WebDriver (HTTP+JSON, porta efêmera) — sessão, navegação
    // e page source via REST; exige "Allow Remote Automation" (menu Develop).
    // Fora do mac, ou sem safaridriver, retorna null (teste pula).
    // Extraído p/ teste unitário (osName injetável — o host do teste é
    // Linux, então o caminho macOS é simulado via fake em @TempDir).
    static Path findSafari(String osName) {
        if (!osName.toLowerCase(java.util.Locale.ROOT).contains("mac")) return null;
        for (String dir : List.of("/usr/bin", "/usr/local/bin")) {
            Path p = Path.of(dir, "safaridriver");
            if (Files.isExecutable(p)) return p;
        }
        return null;
    }

    // #110: resolve o browser disponível — Chrome/Chromium primeiro (dump-dom
    // rápido e determinístico), Safari via safaridriver no macOS como fallback.
    // Retorna null quando não há nenhum (o teste pula via assume).
    static Browser findBrowser() {
        Path chrome = findChrome();
        if (chrome != null) return new Browser(chrome, null);
        Path safari = findSafari(System.getProperty("os.name", ""));
        if (safari != null) return new Browser(null, safari);
        return null;
    }

    // #110: browser real resolvido — um dos dois caminhos, nunca os dois.
    static final class Browser {
        final Path chrome;
        final Path safariDriver;

        Browser(Path chrome, Path safariDriver) {
            this.chrome = chrome;
            this.safariDriver = safariDriver;
        }

        boolean isSafari() {
            return chrome == null && safariDriver != null;
        }

        String dump(String url) throws IOException {
            if (chrome != null) return dumpDom(chrome, url);
            return dumpDomSafari(safariDriver, url);
        }
    }

    // #110: extraído p/ teste unitário (osName/home injetáveis — o host
    // do teste é Linux, então o caminho macOS é simulado em @TempDir).
    static Path findMacBundle(String osName, String home) {
        if (!osName.toLowerCase(java.util.Locale.ROOT).contains("mac")) return null;
        List<Path> bundles = List.of(
                Path.of(home, "Applications", "Google Chrome.app", "Contents", "MacOS", "Google Chrome"),
                Path.of("/Applications", "Google Chrome.app", "Contents", "MacOS", "Google Chrome"),
                Path.of(home, "Applications", "Chromium.app", "Contents", "MacOS", "Chromium"),
                Path.of("/Applications", "Chromium.app", "Contents", "MacOS", "Chromium"));
        for (Path p : bundles) {
            if (Files.isExecutable(p)) return p;
        }
        return null;
    }

    @Test
    void uiWindowRendersInRealBrowserDom(@TempDir Path tempDir) throws IOException {
        Browser browser = findBrowser();
        assumeTrue(browser != null, "nenhum browser real (Chrome/Chromium/Safari) — pulando E2E de browser");

        String program = """
            main() {
                var titulo = Label("browser-ok")
                var btn = Button("clicar")
                var col = Column(listOf(titulo, btn))
                var w = Window("BrowserTest")
                w.bind(col)
                w.show()
            }
            """;
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, program);

        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "compilação JS deve passar: " + result.diagnostics().getDiagnostics());
        assertTrue(Files.exists(outDir.resolve("index.html")), "index.html gerado pelo backend JS");
        assertTrue(Files.exists(outDir.resolve("Default.mjs")), "módulo .mjs gerado pelo backend JS");

        HttpServer server = serve(outDir);
        int port = server.getAddress().getPort();
        try {
            String dom = browser.dump("http://127.0.0.1:" + port + "/index.html");
            assertTrue(dom.contains("kof-window"), "janela kof.ui ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("browser-ok"), "label ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("kof-button"), "button kof.ui ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("clicar"), "texto do button ausente no DOM: " + excerpt(dom));
            assertFalse(dom.contains("Failed to load"), "módulo ESM falhou de carregar: " + excerpt(dom));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void inputPlaceholderRendersInRealBrowserDom(@TempDir Path tempDir) throws IOException {
        Browser browser = findBrowser();
        assumeTrue(browser != null, "nenhum browser real (Chrome/Chromium/Safari) — pulando E2E de browser");

        String program = """
            main() {
                var campo = Input("")
                campo.setPlaceholder("digite aqui")
                var col = Column(listOf(campo))
                var w = Window("InputTest")
                w.bind(col)
                w.show()
            }
            """;
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, program);

        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "compilação JS deve passar: " + result.diagnostics().getDiagnostics());

        HttpServer server = serve(outDir);
        int port = server.getAddress().getPort();
        try {
            String dom = browser.dump("http://127.0.0.1:" + port + "/index.html");
            assertTrue(dom.contains("kof-input"), "input kof.ui ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("placeholder=\"digite aqui\""),
                    "placeholder ausente no DOM: " + excerpt(dom));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void componentAndRawWidgetIdsNeverCollide(@TempDir Path tempDir) throws IOException {
        // §261 (16/09, descoberto pela lib kof-ui-widgets): componentes e nós
        // DOM tinham contadores de handle PROPRIOS a partir de 0, e
        // window.bind(id) resolve componentes PRIMEIRO — então um Component
        // criado antes de um widget cru colidia com o handle do widget e o
        // bind montava o objeto errado (o widget ficava órfão em __kofNodes,
        // nunca aparecia). Medido no Chrome com Slider+Button da lib.
        Browser browser = findBrowser();
        assumeTrue(browser != null, "nenhum browser real (Chrome/Chromium/Safari) — pulando E2E de browser");

        String program = """
            main() {
                var comp = Component(0)
                comp.view((s: Int) -> Label("componente-vivo"))
                var raw = Label("widget-cru")
                var w = Window("IdCollision")
                w.bind(comp)
                w.bind(raw)
                w.show()
            }
            """;
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, program);

        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "compilação JS deve passar: " + result.diagnostics().getDiagnostics());

        HttpServer server = serve(outDir);
        int port = server.getAddress().getPort();
        try {
            String dom = browser.dump("http://127.0.0.1:" + port + "/index.html");
            assertTrue(dom.contains("componente-vivo"), "component ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("widget-cru"),
                    "widget cru órfão (colisão de id component/node — §261): " + excerpt(dom));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void inputTypeRendersInRealBrowserDom(@TempDir Path tempDir) throws IOException {
        Browser browser = findBrowser();
        assumeTrue(browser != null, "nenhum browser real (Chrome/Chromium/Safari) — pulando E2E de browser");

        String program = """
            main() {
                var senha = Input("")
                senha.setType("password")
                var col = Column(listOf(senha))
                var w = Window("InputTypeTest")
                w.bind(col)
                w.show()
            }
            """;
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, program);

        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "compilação JS deve passar: " + result.diagnostics().getDiagnostics());

        HttpServer server = serve(outDir);
        int port = server.getAddress().getPort();
        try {
            String dom = browser.dump("http://127.0.0.1:" + port + "/index.html");
            assertTrue(dom.contains("kof-input"), "input kof.ui ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("type=\"password\""),
                    "type=password ausente no DOM: " + excerpt(dom));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void inputCheckboxCheckedRendersInRealBrowserDom(@TempDir Path tempDir) throws IOException {
        Browser browser = findBrowser();
        assumeTrue(browser != null, "nenhum browser real (Chrome/Chromium/Safari) — pulando E2E de browser");

        String program = """
            main() {
                var aceitar = Input("")
                aceitar.setType("checkbox")
                aceitar.setChecked(true)
                var col = Column(listOf(aceitar))
                var w = Window("CheckboxTest")
                w.bind(col)
                w.show()
            }
            """;
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, program);

        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "compilação JS deve passar: " + result.diagnostics().getDiagnostics());

        HttpServer server = serve(outDir);
        int port = server.getAddress().getPort();
        try {
            String dom = browser.dump("http://127.0.0.1:" + port + "/index.html");
            assertTrue(dom.contains("type=\"checkbox\""),
                    "type=checkbox ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("checked"),
                    "checked ausente no DOM: " + excerpt(dom));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void imageAltSizeRendersInRealBrowserDom(@TempDir Path tempDir) throws IOException {
        Browser browser = findBrowser();
        assumeTrue(browser != null, "nenhum browser real (Chrome/Chromium/Safari) — pulando E2E de browser");

        String program = """
            main() {
                var logo = Image("logo.png")
                logo.setAlt("logotipo")
                logo.setWidth(120)
                logo.setHeight(60)
                var col = Column(listOf(logo))
                var w = Window("ImageTest")
                w.bind(col)
                w.show()
            }
            """;
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, program);

        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "compilação JS deve passar: " + result.diagnostics().getDiagnostics());

        HttpServer server = serve(outDir);
        int port = server.getAddress().getPort();
        try {
            String dom = browser.dump("http://127.0.0.1:" + port + "/index.html");
            assertTrue(dom.contains("kof-image"), "image kof.ui ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("alt=\"logotipo\""),
                    "alt ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("width=\"120\""),
                    "width ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("height=\"60\""),
                    "height ausente no DOM: " + excerpt(dom));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void formContainerRendersInRealBrowserDom(@TempDir Path tempDir) throws IOException {
        Browser browser = findBrowser();
        assumeTrue(browser != null, "nenhum browser real (Chrome/Chromium/Safari) — pulando E2E de browser");

        String program = """
            main() {
                var campo = Input("")
                campo.setPlaceholder("nome")
                var b = Button("enviar")
                var f = Form(listOf(campo, b))
                var w = Window("FormTest")
                w.bind(f)
                w.show()
            }
            """;
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, program);

        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "compilação JS deve passar: " + result.diagnostics().getDiagnostics());

        HttpServer server = serve(outDir);
        int port = server.getAddress().getPort();
        try {
            String dom = browser.dump("http://127.0.0.1:" + port + "/index.html");
            assertTrue(dom.contains("<form"), "elemento <form> ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("kof-form"), "classe kof-form ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("placeholder=\"nome\""),
                    "input do form ausente no DOM: " + excerpt(dom));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void widgetAttributesRenderInRealBrowserDom(@TempDir Path tempDir) throws IOException {
        Browser browser = findBrowser();
        assumeTrue(browser != null, "nenhum browser real (Chrome/Chromium/Safari) — pulando E2E de browser");

        String program = """
            main() {
                var campo = Input("")
                campo.setId("nome")
                campo.setClass("destaque")
                campo.setDisabled(true)
                var col = Column(listOf(campo))
                var w = Window("AttrsTest")
                w.bind(col)
                w.show()
            }
            """;
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, program);

        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "compilação JS deve passar: " + result.diagnostics().getDiagnostics());

        HttpServer server = serve(outDir);
        int port = server.getAddress().getPort();
        try {
            String dom = browser.dump("http://127.0.0.1:" + port + "/index.html");
            assertTrue(dom.contains("id=\"nome\""), "id ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("destaque"), "class ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("disabled"), "disabled ausente no DOM: " + excerpt(dom));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void widgetVisualPrimitivesRenderInRealBrowserDom(@TempDir Path tempDir) throws IOException {
        Browser browser = findBrowser();
        assumeTrue(browser != null, "nenhum browser real (Chrome/Chromium/Safari) — pulando E2E de browser");

        // issue #78: border/shadow/gradient/flex-basis/max-width aplicados de
        // verdade no nó DOM (style inline), não só linkando no JVM/Native.
        String program = """
            main() {
                var btn = Button("card")
                btn.setBorder(Color(255, 0, 0), 2)
                btn.setShadow(Color(0, 0, 0), 4, 12)
                btn.setFlexBasis(300)
                btn.setMaxWidth(600)
                var col = Column(listOf(btn))
                col.setGradient(Color(255, 0, 0), Color(0, 0, 255), 90)
                var w = Window("VisualTest")
                w.bind(col)
                w.show()
            }
            """;
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, program);

        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "compilação JS deve passar: " + result.diagnostics().getDiagnostics());

        HttpServer server = serve(outDir);
        int port = server.getAddress().getPort();
        try {
            String dom = browser.dump("http://127.0.0.1:" + port + "/index.html");
            assertTrue(dom.contains("rgb(255, 0, 0) 2px solid") || dom.contains("2px solid rgb(255, 0, 0)"),
                    "border inline ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("box-shadow"), "box-shadow ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("linear-gradient"), "gradiente ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("max-width: 600px"), "max-width ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("flex: 1 1 300px") || dom.contains("flex-basis: 300px"),
                    "flex-basis ausente no DOM: " + excerpt(dom));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void declarativeStyleRendersInRealBrowserDom(@TempDir Path tempDir) throws IOException {
        Browser browser = findBrowser();
        assumeTrue(browser != null, "nenhum browser real (Chrome/Chromium/Safari) — pulando E2E de browser");

        // D-UI-STYLE (UI007): the compiler-parsed/normalized CSS must reach the
        // real DOM node — background hex, bare Int normalized to px, radius.
        String program = """
            main() {
                var s = Style("background: #ff0000; padding: 8; border-radius: 4")
                var v = View(s)
                var w = Window("StyleTest")
                w.bind(v)
                w.show()
            }
            """;
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, program);

        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "compilação JS deve passar: " + result.diagnostics().getDiagnostics());

        HttpServer server = serve(outDir);
        int port = server.getAddress().getPort();
        try {
            String dom = browser.dump("http://127.0.0.1:" + port + "/index.html");
            assertTrue(dom.contains("rgb(255, 0, 0)") || dom.contains("#ff0000"),
                    "background declarativo ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("padding: 8px"), "Int nu não virou px: " + excerpt(dom));
            assertTrue(dom.contains("border-radius: 4px"), "border-radius ausente no DOM: " + excerpt(dom));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void setStyleRendersOnAnyDomWidgetInRealBrowser(@TempDir Path tempDir) throws IOException {
        Browser browser = findBrowser();
        assumeTrue(browser != null, "nenhum browser real (Chrome/Chromium/Safari) — pulando E2E de browser");

        // D-UI-STYLE (UI007) Q5: setStyle(style) on a Label — not only View.
        String program = """
            main() {
                var l = Label("card")
                l.setStyle(Style("background: #00ff00; padding: 4"))
                var col = Column(listOf(l))
                var w = Window("WidgetStyle")
                w.bind(col)
                w.show()
            }
            """;
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, program);

        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "compilação JS deve passar: " + result.diagnostics().getDiagnostics());

        HttpServer server = serve(outDir);
        int port = server.getAddress().getPort();
        try {
            String dom = browser.dump("http://127.0.0.1:" + port + "/index.html");
            assertTrue(dom.contains("rgb(0, 255, 0)") || dom.contains("#00ff00"),
                    "setStyle no Label não aplicou background: " + excerpt(dom));
            assertTrue(dom.contains("padding: 4px"), "setStyle no Label não aplicou padding: " + excerpt(dom));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void formSubmitHandlerRunsInRealBrowser(@TempDir Path tempDir) throws IOException {
        Browser browser = findBrowser();
        assumeTrue(browser != null, "nenhum browser real (Chrome/Chromium/Safari) — pulando E2E de browser");

        // o handler do onSubmit muta o placeholder do input — se o DOM final
        // traz "depois", o handler RODOU de verdade no browser.
        String program = """
            main() {
                var campo = Input("")
                campo.setPlaceholder("antes")
                var f = Form(listOf(campo))
                f.onSubmit(() -> campo.setPlaceholder("depois"))
                var w = Window("SubmitTest")
                w.bind(f)
                w.show()
                f.submit()
            }
            """;
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, program);

        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "compilação JS deve passar: " + result.diagnostics().getDiagnostics());

        HttpServer server = serve(outDir);
        int port = server.getAddress().getPort();
        try {
            String dom = browser.dump("http://127.0.0.1:" + port + "/index.html");
            assertTrue(dom.contains("placeholder=\"depois\""),
                    "handler do onSubmit NÃO rodou (placeholder ainda 'antes'): " + excerpt(dom));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void textareaRendersInRealBrowserDom(@TempDir Path tempDir) throws IOException {
        Browser browser = findBrowser();
        assumeTrue(browser != null, "nenhum browser real (Chrome/Chromium/Safari) — pulando E2E de browser");

        String program = """
            main() {
                var obs = Textarea("inicial")
                obs.setPlaceholder("descreva")
                var col = Column(listOf(obs))
                var w = Window("TextareaTest")
                w.bind(col)
                w.show()
            }
            """;
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, program);

        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "compilação JS deve passar: " + result.diagnostics().getDiagnostics());

        HttpServer server = serve(outDir);
        int port = server.getAddress().getPort();
        try {
            String dom = browser.dump("http://127.0.0.1:" + port + "/index.html");
            assertTrue(dom.contains("<textarea"), "elemento <textarea> ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("kof-textarea"), "classe kof-textarea ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("inicial"), "texto inicial ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("placeholder=\"descreva\""),
                    "placeholder ausente no DOM: " + excerpt(dom));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void selectRendersInRealBrowserDom(@TempDir Path tempDir) throws IOException {
        Browser browser = findBrowser();
        assumeTrue(browser != null, "nenhum browser real (Chrome/Chromium/Safari) — pulando E2E de browser");

        String program = """
            main() {
                var sel = Select(listOf("uma", "duas", "tres"))
                sel.setSelected(1)
                var col = Column(listOf(sel))
                var w = Window("SelectTest")
                w.bind(col)
                w.show()
            }
            """;
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, program);

        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "compilação JS deve passar: " + result.diagnostics().getDiagnostics());

        HttpServer server = serve(outDir);
        int port = server.getAddress().getPort();
        try {
            String dom = browser.dump("http://127.0.0.1:" + port + "/index.html");
            assertTrue(dom.contains("<select"), "elemento <select> ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("kof-select"), "classe kof-select ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("<option value=\"uma\""), "opção 'uma' ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("<option value=\"duas\""), "opção 'duas' ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("<option value=\"tres\""), "opção 'tres' ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("selected"), "opção selecionada (índice 1) ausente no DOM: " + excerpt(dom));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fieldsetRendersInRealBrowserDom(@TempDir Path tempDir) throws IOException {
        Browser browser = findBrowser();
        assumeTrue(browser != null, "nenhum browser real (Chrome/Chromium/Safari) — pulando E2E de browser");

        String program = """
            main() {
                var campo = Input("nome")
                var fs = Fieldset(listOf(campo))
                fs.setClass("dados")
                var w = Window("FieldsetTest")
                w.bind(fs)
                w.show()
            }
            """;
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, program);

        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "compilação JS deve passar: " + result.diagnostics().getDiagnostics());

        HttpServer server = serve(outDir);
        int port = server.getAddress().getPort();
        try {
            String dom = browser.dump("http://127.0.0.1:" + port + "/index.html");
            assertTrue(dom.contains("<fieldset"), "elemento <fieldset> ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("kof-fieldset"), "classe kof-fieldset ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("dados"), "classe custom ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("<input"), "filho <input> ausente no <fieldset>: " + excerpt(dom));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void iframeRendersInRealBrowserDom(@TempDir Path tempDir) throws IOException {
        Browser browser = findBrowser();
        assumeTrue(browser != null, "nenhum browser real (Chrome/Chromium/Safari) — pulando E2E de browser");

        String program = """
            main() {
                var iframe = Iframe("https://kof.dev")
                var col = Column(listOf(iframe))
                var w = Window("IframeTest")
                w.bind(col)
                w.show()
            }
            """;
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, program);

        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "compilação JS deve passar: " + result.diagnostics().getDiagnostics());

        HttpServer server = serve(outDir);
        int port = server.getAddress().getPort();
        try {
            String dom = browser.dump("http://127.0.0.1:" + port + "/index.html");
            assertTrue(dom.contains("<iframe"), "elemento <iframe> ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("kof-iframe"), "classe kof-iframe ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("src=\"https://kof.dev\""), "src ausente no DOM: " + excerpt(dom));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rawViewRendersInRealBrowserDom(@TempDir Path tempDir) throws IOException {
        Browser browser = findBrowser();
        assumeTrue(browser != null, "nenhum browser real (Chrome/Chromium/Safari) — pulando E2E de browser");

        String program = """
            main() {
                var rv = RawView("section", "meu-raw", "color: red;", "<b>oi</b>")
                var w = Window("RawViewTest")
                w.bind(rv)
                w.show()
            }
            """;
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, program);

        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "compilação JS deve passar: " + result.diagnostics().getDiagnostics());

        HttpServer server = serve(outDir);
        int port = server.getAddress().getPort();
        try {
            String dom = browser.dump("http://127.0.0.1:" + port + "/index.html");
            assertTrue(dom.contains("<section"), "elemento <section> ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("meu-raw"), "classe custom ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("color: red"), "CSS inline ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("<b>oi</b>"), "innerHTML ausente no DOM: " + excerpt(dom));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void videoRendersInRealBrowserDom(@TempDir Path tempDir) throws IOException {
        Browser browser = findBrowser();
        assumeTrue(browser != null, "nenhum browser real (Chrome/Chromium/Safari) — pulando E2E de browser");

        String program = """
            main() {
                var video = Video("movie.mp4")
                video.setControls(true)
                var col = Column(listOf(video))
                var w = Window("VideoTest")
                w.bind(col)
                w.show()
            }
            """;
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, program);

        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "compilação JS deve passar: " + result.diagnostics().getDiagnostics());

        HttpServer server = serve(outDir);
        int port = server.getAddress().getPort();
        try {
            String dom = browser.dump("http://127.0.0.1:" + port + "/index.html");
            assertTrue(dom.contains("<video"), "elemento <video> ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("kof-video"), "classe kof-video ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("src=\"movie.mp4\""), "src ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("controls"), "atributo controls ausente no DOM: " + excerpt(dom));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void audioRendersInRealBrowserDom(@TempDir Path tempDir) throws IOException {
        Browser browser = findBrowser();
        assumeTrue(browser != null, "nenhum browser real (Chrome/Chromium/Safari) — pulando E2E de browser");

        String program = """
            main() {
                var audio = Audio("song.mp3")
                audio.setControls(true)
                var col = Column(listOf(audio))
                var w = Window("AudioTest")
                w.bind(col)
                w.show()
            }
            """;
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, program);

        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "compilação JS deve passar: " + result.diagnostics().getDiagnostics());

        HttpServer server = serve(outDir);
        int port = server.getAddress().getPort();
        try {
            String dom = browser.dump("http://127.0.0.1:" + port + "/index.html");
            assertTrue(dom.contains("<audio"), "elemento <audio> ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("kof-audio"), "classe kof-audio ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("src=\"song.mp3\""), "src ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("controls"), "atributo controls ausente no DOM: " + excerpt(dom));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void hrRendersInRealBrowserDom(@TempDir Path tempDir) throws IOException {
        Browser browser = findBrowser();
        assumeTrue(browser != null, "nenhum browser real (Chrome/Chromium/Safari) — pulando E2E de browser");

        String program = """
            main() {
                var hr = Hr()
                hr.setClass("divisor")
                var col = Column(listOf(hr))
                var w = Window("HrTest")
                w.bind(col)
                w.show()
            }
            """;
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, program);

        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "compilação JS deve passar: " + result.diagnostics().getDiagnostics());

        HttpServer server = serve(outDir);
        int port = server.getAddress().getPort();
        try {
            String dom = browser.dump("http://127.0.0.1:" + port + "/index.html");
            assertTrue(dom.contains("<hr"), "elemento <hr> ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("kof-hr"), "classe kof-hr ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("divisor"), "classe custom ausente no DOM: " + excerpt(dom));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void canvasUi009RunsInRealBrowser(@TempDir Path tempDir) throws IOException {
        Browser browser = findBrowser();
        assumeTrue(browser != null, "nenhum browser real (Chrome/Chromium/Safari) — pulando E2E de browser");

        // UI009: save/restore/setGlobalAlpha/fillText/measureText/transform
        // rodam no contexto 2D real. O desenho é bitmap (não aparece no DOM),
        // então provamos que executaram sem lançar usando o resultado de
        // measureText (>0) num Label observável — se qualquer método falhasse,
        // o script pararia e o Label não renderizaria.
        String program = """
            main() {
                var c = Canvas(400, 300)
                c.save()
                c.setGlobalAlpha(0.5)
                c.transform(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
                c.fillText("oi", 10, 20)
                var w = c.measureText("oi")
                var img = Image("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='10' height='10'%3E%3C/svg%3E")
                c.drawImage(img, 5, 5)
                c.restore()
                var lbl = Label(if (w > 0.0) "measured" else "zero")
                var col = Column(listOf(c, lbl))
                var win = Window("CanvasTest")
                win.bind(col)
                win.show()
            }
            """;
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, program);

        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "compilação JS deve passar: " + result.diagnostics().getDiagnostics());

        HttpServer server = serve(outDir);
        int port = server.getAddress().getPort();
        try {
            String dom = browser.dump("http://127.0.0.1:" + port + "/index.html");
            assertTrue(dom.contains("<canvas"), "elemento <canvas> ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("measured"),
                    "measureText não retornou >0 (métodos UI009 não rodaram): " + excerpt(dom));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void listWidgetsRenderInRealBrowserDom(@TempDir Path tempDir) throws IOException {
        Browser browser = findBrowser();
        assumeTrue(browser != null, "nenhum browser real (Chrome/Chromium/Safari) — pulando E2E de browser");

        String program = """
            main() {
                var u = Ul(listOf("maçã", "uva"))
                var o = Ol(listOf("primeiro", "segundo"))
                var col = Column(listOf(u, o))
                var w = Window("ListTest")
                w.bind(col)
                w.show()
            }
            """;
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, program);

        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "compilação JS deve passar: " + result.diagnostics().getDiagnostics());

        HttpServer server = serve(outDir);
        int port = server.getAddress().getPort();
        try {
            String dom = browser.dump("http://127.0.0.1:" + port + "/index.html");
            assertTrue(dom.contains("<ul"), "elemento <ul> ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("kof-ul"), "classe kof-ul ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("<li>maçã</li>"), "<li> da maçã ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("<ol"), "elemento <ol> ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("<li>primeiro</li>"), "<li> do primeiro ausente no DOM: " + excerpt(dom));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void inputAttrsRenderInRealBrowserDom(@TempDir Path tempDir) throws IOException {
        Browser browser = findBrowser();
        assumeTrue(browser != null, "nenhum browser real (Chrome/Chromium/Safari) — pulando E2E de browser");

        // name/readonly são ATRIBUTOS → serializam no outerHTML do dump-dom.
        String program = """
            main() {
                var i = Input("oi")
                i.setName("usuario")
                i.setReadonly(true)
                var t = Textarea("x")
                t.setName("bio")
                t.setReadonly(true)
                var col = Column(listOf(i, t))
                var w = Window("AttrsTest")
                w.bind(col)
                w.show()
            }
            """;
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, program);

        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "compilação JS deve passar: " + result.diagnostics().getDiagnostics());

        HttpServer server = serve(outDir);
        int port = server.getAddress().getPort();
        try {
            String dom = browser.dump("http://127.0.0.1:" + port + "/index.html");
            assertTrue(dom.contains("name=\"usuario\""), "name=\"usuario\" ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("name=\"bio\""), "name=\"bio\" ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("readonly"), "readonly ausente no DOM: " + excerpt(dom));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void tableRendersInRealBrowserDom(@TempDir Path tempDir) throws IOException {
        Browser browser = findBrowser();
        assumeTrue(browser != null, "nenhum browser real (Chrome/Chromium/Safari) — pulando E2E de browser");

        String program = """
            main() {
                var t = Table(listOf("nome", "idade"),
                              listOf(listOf("mel", "26"), listOf("ana", "30")))
                var col = Column(listOf(t))
                var w = Window("TableTest")
                w.bind(col)
                w.show()
            }
            """;
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, program);

        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "compilação JS deve passar: " + result.diagnostics().getDiagnostics());

        HttpServer server = serve(outDir);
        int port = server.getAddress().getPort();
        try {
            String dom = browser.dump("http://127.0.0.1:" + port + "/index.html");
            assertTrue(dom.contains("<table"), "elemento <table> ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("kof-table"), "classe kof-table ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("<th>nome</th>"), "<th> ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("<td>mel</td>"), "<td> mel ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("<td>30</td>"), "<td> 30 ausente no DOM: " + excerpt(dom));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void ui003RemainingRenderInRealBrowserDom(@TempDir Path tempDir) throws IOException {
        Browser browser = findBrowser();
        assumeTrue(browser != null, "nenhum browser real (Chrome/Chromium/Safari) — pulando E2E de browser");

        String program = """
            main() {
                var fs = Fieldset(listOf(Label("dentro")), "credenciais")
                var fr = Iframe("https://example.org")
                var v = Video("clip.mp4")
                var a = Audio("som.mp3")
                var h = Hr()
                var col = Column(listOf(fs, fr, v, a, h))
                var w = Window("Ui003RestTest")
                w.bind(col)
                w.show()
            }
            """;
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, program);

        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "compilação JS deve passar: " + result.diagnostics().getDiagnostics());

        HttpServer server = serve(outDir);
        int port = server.getAddress().getPort();
        try {
            String dom = browser.dump("http://127.0.0.1:" + port + "/index.html");
            assertTrue(dom.contains("<fieldset"), "<fieldset> ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("<legend>credenciais</legend>"), "<legend> ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("<iframe"), "<iframe> ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("https://example.org"), "src do iframe ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("<video"), "<video> ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("clip.mp4"), "src do video ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("<audio"), "<audio> ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("<hr"), "<hr> ausente no DOM: " + excerpt(dom));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void ui006EventAccessorsRunInRealBrowser(@TempDir Path tempDir) throws IOException {
        Browser browser = findBrowser();
        assumeTrue(browser != null, "nenhum browser real (Chrome/Chromium/Safari) — pulando E2E de browser");

        // O handler lê e.key()/e.value()/e.target()/e.relatedTarget() do
        // evento DOM real e muta class/placeholder — se o DOM final traz
        // "key=xt=campo-main", o handler RODOU com o event do browser
        // (dispatch sintético no load, padrão formSubmit). target() expõe o
        // id do nó que originou o evento (set em campo); relatedTarget() é
        // "" nesses eventos. Tokens de class sem espaço (classList.add).
        String program = """
            main() {
                var campo = Input("")
                campo.setId("campo-main")
                campo.setPlaceholder("limpo")
                campo.on("keydown", (e: Event) -> { campo.setClass("key=" + e.key() + "t=" + e.target()) })
                campo.on("input", (e: Event) -> { campo.setPlaceholder("val=" + e.value() + " rt=" + e.relatedTarget()) })
                var col = Column(listOf(campo))
                var w = Window("Ui006Test")
                w.bind(col)
                w.show()
            }
            """;
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, program);

        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "compilação JS deve passar: " + result.diagnostics().getDiagnostics());

        // injeta o dispatch sintético no index.html: keydown "x" + input "abc"
        Path index = outDir.resolve("index.html");
        String html = Files.readString(index);
        String inject = """
            <script type="module">
            import './Default.mjs';
            setTimeout(function () {
                var el = document.querySelector('.kof-input');
                if (!el) return;
                el.dispatchEvent(new KeyboardEvent('keydown', { key: 'x', bubbles: true }));
                el.value = 'abc';
                el.dispatchEvent(new Event('input', { bubbles: true }));
            }, 2000);
            </script>
            </body>""";
        html = html.replace("</body>", inject);
        Files.writeString(index, html);

        HttpServer server = serve(outDir);
        int port = server.getAddress().getPort();
        try {
            String dom = browser.dump("http://127.0.0.1:" + port + "/index.html");
            assertTrue(dom.contains("key=x"),
                    "e.key() não trouxe a tecla do evento DOM real: " + excerpt(dom));
            assertTrue(dom.contains("t=campo-main"),
                    "e.target() não trouxe o id do nó que originou o evento: " + excerpt(dom));
            assertTrue(dom.contains("val=abc"),
                    "e.value() não trouxe o valor do input real: " + excerpt(dom));
            assertTrue(dom.contains("val=abc rt="),
                    "e.relatedTarget() não respondeu (esperado vazio no input): " + excerpt(dom));
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer serve(Path dir) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/")) path = "/index.html";
            Path file = dir.resolve(path.substring(1)).normalize();
            if (!file.startsWith(dir) || !Files.isRegularFile(file)) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            byte[] body = Files.readAllBytes(file);
            String name = file.getFileName().toString().toLowerCase();
            String mime = name.endsWith(".html") ? "text/html; charset=utf-8"
                    : name.endsWith(".mjs") || name.endsWith(".js") ? "text/javascript; charset=utf-8"
                    : "application/octet-stream";
            exchange.getResponseHeaders().set("Content-Type", mime);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static String dumpDomSafari(Path safariDriver, String url) throws IOException {
        // #110: Safari não tem --dump-dom. Caminho WebDriver W3C: sobe o
        // safaridriver numa porta efêmera, cria sessão, navega e lê o
        // page source. Exige "Allow Remote Automation" (menu Develop do
        // Safari); se o driver recusar, aborta (assume) em vez de falhar.
        int port;
        try (ServerSocket ss = new ServerSocket(0)) {
            port = ss.getLocalPort();
        }
        ProcessBuilder pb = new ProcessBuilder(
                safariDriver.toString(), "-p", String.valueOf(port));
        pb.redirectErrorStream(false);
        Process driver = pb.start();
        HttpClient http;
        try {
            String base = "http://127.0.0.1:" + port;
            http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            waitForDriver(http, base);
            String session = newSession(http, base);
            try {
                navigate(http, base, session, url);
                waitForKofUi(http, base, session);
                return pageSource(http, base, session);
            } finally {
                deleteSession(http, base, session);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrompido no safaridriver", e);
        } finally {
            driver.destroy();
        }
    }

    private static void waitForDriver(HttpClient http, String base) throws IOException, InterruptedException {
        HttpRequest status = HttpRequest.newBuilder(URI.create(base + "/status"))
                .timeout(Duration.ofSeconds(2)).GET().build();
        long deadline = System.currentTimeMillis() + 15_000;
        while (true) {
            try {
                HttpResponse<String> r = http.send(status, HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() == 200 && r.body().contains("\"ready\"")) return;
            } catch (IOException | InterruptedException ignored) {
                if (ignored instanceof InterruptedException) throw (InterruptedException) ignored;
            }
            if (System.currentTimeMillis() > deadline) {
                throw new IOException("safaridriver não ficou pronto em 15s (ative Remote Automation no Safari)");
            }
            Thread.sleep(200);
        }
    }

    private static String post(HttpClient http, String url, String json) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) throw new IOException("safaridriver " + url + " -> " + r.statusCode() + ": " + excerpt(r.body()));
        return r.body();
    }

    private static String get(HttpClient http, String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30)).GET().build();
        HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) throw new IOException("safaridriver " + url + " -> " + r.statusCode() + ": " + excerpt(r.body()));
        return r.body();
    }

    private static String newSession(HttpClient http, String base) throws IOException, InterruptedException {
        String body = post(http, base + "/session", "{\"capabilities\":{\"alwaysMatch\":{\"browserName\":\"safari\"}}}");
        String id = extractJson(body, "sessionId");
        if (id == null) abort("safaridriver recusou a sessão (ative Allow Remote Automation no Safari)");
        return id;
    }

    private static void navigate(HttpClient http, String base, String session, String url) throws IOException, InterruptedException {
        post(http, base + "/session/" + session + "/url", "{\"url\":" + quoteJson(url) + "}");
    }

    private static void waitForKofUi(HttpClient http, String base, String session) throws IOException, InterruptedException {
        // O módulo ESM é deferred: espera até 10s pelo nó kof-window no DOM.
        String script = "return document.documentElement.outerHTML;";
        long deadline = System.currentTimeMillis() + 10_000;
        while (true) {
            String html = execute(http, base, session, script);
            if (html != null && html.contains("kof-window")) return;
            if (System.currentTimeMillis() > deadline) return;
            Thread.sleep(300);
        }
    }

    private static String pageSource(HttpClient http, String base, String session) throws IOException, InterruptedException {
        return execute(http, base, session, "return document.documentElement.outerHTML;");
    }

    private static String execute(HttpClient http, String base, String session, String script) throws IOException, InterruptedException {
        String body = post(http, base + "/session/" + session + "/execute/sync",
                "{\"script\":" + quoteJson(script) + ",\"args\":[]}");
        String value = extractJson(body, "value");
        return value == null ? "" : unescapeJson(value);
    }

    private static void deleteSession(HttpClient http, String base, String session) {
        try {
            http.send(HttpRequest.newBuilder(URI.create(base + "/session/" + session))
                    .timeout(Duration.ofSeconds(5)).DELETE().build(),
                    HttpResponse.BodyHandlers.discarding());
        } catch (IOException | InterruptedException ignored) {
            if (ignored instanceof InterruptedException) Thread.currentThread().interrupt();
        }
    }

    private static String quoteJson(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String extractJson(String json, String key) {
        String needle = "\"" + key + "\":\"";
        int i = json.indexOf(needle);
        if (i < 0) return null;
        int j = i + needle.length();
        StringBuilder sb = new StringBuilder();
        while (j < json.length()) {
            char c = json.charAt(j);
            if (c == '"') break;
            if (c == '\\' && j + 1 < json.length()) {
                sb.append(json.charAt(j + 1));
                j += 2;
                continue;
            }
            sb.append(c);
            j++;
        }
        return sb.toString();
    }

    private static String unescapeJson(String s) {
        return s.replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static String dumpDom(Path chrome, String url) throws IOException {
        // --virtual-time-budget: o módulo ESM é deferred — dá tempo virtual
        // para o script carregar e o kof.ui injetar os nós antes do dump.
        ProcessBuilder pb = new ProcessBuilder(
                chrome.toString(), "--headless=new", "--no-sandbox", "--disable-gpu",
                "--dump-dom", "--virtual-time-budget=8000", url);
        pb.redirectErrorStream(false);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        try {
            int code = p.waitFor();
            assertEquals(0, code, "chrome --dump-dom falhou: " + excerpt(out));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrompido no chrome headless", e);
        }
        return out;
    }

    private static String excerpt(String s) {
        if (s == null) return "<nulo>";
        return s.length() > 600 ? s.substring(0, 600) + "…" : s;
    }

    @Test
    void findMacBundleFindsChromeInsideAppBundle(@TempDir Path tempDir) throws IOException {
        // #110: simula o layout macOS (~/Applications/Google Chrome.app/...)
        // num @TempDir — o host do CI é Linux, então home é injetado.
        Path exe = tempDir.resolve("Applications/Google Chrome.app/Contents/MacOS/Google Chrome");
        Files.createDirectories(exe.getParent());
        Files.writeString(exe, "#!/bin/sh\n");
        assertTrue(exe.toFile().setExecutable(true));
        Path found = findMacBundle("Mac OS X", tempDir.toString());
        assertEquals(exe, found);
    }

    @Test
    void findMacBundleIgnoresNonMac(@TempDir Path tempDir) {
        assertNull(findMacBundle("Linux", tempDir.toString()));
    }

    @Test
    void findSafariOnlyOnMac() {
        assertNull(findSafari("Linux"));
        assertNull(findSafari("Windows 11"));
        // No Linux não há /usr/bin/safaridriver — no mac com driver, retorna o path.
        // (No mac sem Remote Automation o dump aborta via assume, nunca falha.)
    }
}
