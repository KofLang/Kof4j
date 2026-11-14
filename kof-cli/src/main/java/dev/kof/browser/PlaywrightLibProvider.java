package dev.kof.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.AriaRole;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * §6.2 Playwright provider (the testing-platform plan, `D-TESTING-PLATFORM`):
 * the full-capability backend over the Playwright JAVA lib
 * (`com.microsoft.playwright:playwright` — a NORMAL kof-cli dependency per
 * the maintainer's fourth chat poll, 10/10). Launches the Playwright-managed
 * Chromium, exposes navigate + click/fill/locate/assert, and screenshots.
 *
 * <p>Contract (the plan §6.3): only capabilities with compatible semantics
 * are exposed; a backend-specific capability that is missing yields a clear
 * `capability unsupported` diagnostic — never pretend equivalence. The probe
 * is honest: no Playwright-managed browser installed → `null` (the gate skips
 * with the named reason, Q7).
 */
public final class PlaywrightLibProvider implements BrowserProvider {

    /** §6.11 cross-browser (a fatia da ordem §11 confirmada, fase 7): os
     *  três engines que o backend Playwright suporta. O kind é a escolha
     *  EXPLÍCITA do suíte (`kof test` nunca roda a matriz inteira por
     *  padrão — o plano §6.11); o default dos faces sem kind é chromium. */
    public static final java.util.List<String> KINDS =
            java.util.List.of("chromium", "firefox", "webkit");

    @Override
    public String name() {
        return "playwright";
    }

    @Override
    public String probe() {
        // the lib itself is on the classpath; the gate is the managed browser
        Path chromium = findPlaywrightChromium();
        return chromium == null ? null : "playwright-managed " + chromium;
    }

    /** §6.11: probe honesto POR kind — o caminho que o driver do Playwright
     *  VAI usar (a única fonte verdadeira: o cache pode ter builds que o
     *  driver não pina). null = o binário pinado não existe (o gate pula
     *  com o motivo nomeado, Q7 — nunca falso verde). */
    public String probe(String kind) {
        try (Playwright pw = Playwright.create()) {
            Path exe = driverExecutablePath(pw, kind);
            return Files.isExecutable(exe) ? "playwright-managed " + exe : null;
        } catch (Exception e) {
            return null;
        }
    }

    static Path findPlaywrightChromium() {
        Path cache = Path.of(System.getProperty("user.home"),
                ".cache", "ms-playwright");
        if (!Files.isDirectory(cache)) return null;
        Path best = null;
        try (var s = Files.list(cache)) {
            for (Path dir : s.filter(d -> d.getFileName().toString().startsWith("chromium-"))
                    .sorted().toList()) {
                for (String sub : new String[]{"chrome-linux64", "chrome-linux"}) {
                    Path chrome = dir.resolve(sub).resolve("chrome");
                    if (Files.isExecutable(chrome)) best = chrome;
                }
            }
        } catch (Exception e) {
            return null;
        }
        return best;
    }

    static void requireSupported(String kind) {
        if (!KINDS.contains(kind)) {
            throw new IllegalArgumentException(
                    "unknown browser kind '" + kind + "' (supported: chromium, firefox, webkit)");
        }
    }

    static Path driverExecutablePath(Playwright pw, String kind) {
        return Path.of(browserType(pw, kind).executablePath());
    }

    static BrowserType browserType(Playwright pw, String kind) {
        requireSupported(kind);
        return switch (kind) {
            case "chromium" -> pw.chromium();
            case "firefox" -> pw.firefox();
            case "webkit" -> pw.webkit();
            default -> throw new IllegalStateException("unreachable");
        };
    }

    /** Um run completo sobre um único launch: a lambda recebe a Page viva. */
    public <T> T withPage(String url, java.util.function.Function<Page, T> body) {
        return withPage("chromium", url, body);
    }

    /** §6.11: o mesmo run sobre o engine escolhido (chromium/firefox/webkit). */
    public <T> T withPage(String kind, String url, java.util.function.Function<Page, T> body) {
        requireSupported(kind);
        try (Playwright pw = Playwright.create()) {
            Browser browser = browserType(pw, kind).launch(
                    new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            try {
                page.navigate(url);
                return body.apply(page);
            } finally {
                browser.close();
            }
        }
    }

    /** §6.1: o DOM (o content) — o análogo rico do dumpDom do seed. */
    public String content(String url) {
        return content("chromium", url);
    }

    /** §6.11: o content sobre o engine escolhido. */
    public String content(String kind, String url) {
        return withPage(kind, url, Page::content);
    }

    @Override
    public String dumpDom(String url) throws Exception {
        return content(url);
    }

    @Override
    public void screenshot(String url, String path) throws Exception {
        screenshot("chromium", url, path);
    }

    /** §6.11: o screenshot sobre o engine escolhido. */
    public void screenshot(String kind, String url, String path) throws Exception {
        withPage(kind, url, page -> {
            page.screenshot(new Page.ScreenshotOptions().setPath(Path.of(path)));
            return null;
        });
    }

    /** Timeout das asserções que auto-esperam (text/value/attr/enabled sobre
     *  elemento ausente): 5s — falha de teste não pode custar os 30s default. */
    static final long ASSERT_TIMEOUT_MS = 5_000;

    @Override
    public String assertOn(String url, String locator, String assertion)
            throws Exception {
        return assertOn("chromium", url, locator, assertion);
    }

    /** §6.5: o find + assert sobre o engine escolhido. Semântica MEDIDA:
     *  `visible` é a leitura imediata do PRIMEIRO match (elemento ausente ou
     *  escondido = "false", sem espera); `enabled`/`text`/`value`/`attr`
     *  auto-esperam até {@link #ASSERT_TIMEOUT_MS} e um elemento ausente é
     *  erro nomeado com o locator (nunca falso "false"); `value` só existe
     *  em elementos de formulário (o Playwright recusa os outros);
     *  `attr` devolve "null" quando o atributo não existe no elemento. */
    public String assertOn(String kind, String url, String locator,
                           String assertion) throws Exception {
        KofLocator.Parsed parsed = KofLocator.parseLocator(locator);
        KofLocator.Assert want = KofLocator.parseAssertion(assertion);
        try {
            return withPage(kind, url, page -> {
                page.setDefaultTimeout(ASSERT_TIMEOUT_MS);
                return measure(resolve(page, parsed), want);
            });
        } catch (TimeoutError e) {
            throw new IllegalStateException(
                    "element not found within " + ASSERT_TIMEOUT_MS + "ms: "
                            + locator, e);
        }
    }

    /** Fase 8: EMULAÇÃO MOBILE — o contexto com viewport/touch emulados e a
     *  medida pelo MESMO contexto. O `isMobile` é chromium-only na API Java
     *  do Playwright — honrado onde suportado (o toque é medido pelo
     *  `hasTouch`, que é universal). */
    public String deviceAssert(String kind, String url, int viewportWidth,
                               int viewportHeight, boolean hasTouch,
                               String locator, String assertion) throws Exception {
        KofLocator.Parsed parsed = KofLocator.parseLocator(locator);
        KofLocator.Assert want = KofLocator.parseAssertion(assertion);
        requireSupported(kind);
        try (Playwright pw = Playwright.create()) {
            BrowserType type = browserType(pw, kind);
            Browser.NewContextOptions options = new Browser.NewContextOptions()
                    .setViewportSize(viewportWidth, viewportHeight)
                    .setHasTouch(hasTouch);
            if (kind.equals("chromium")) {
                options.setIsMobile(hasTouch);
            }
            try (Browser browser = type.launch(
                    new BrowserType.LaunchOptions().setHeadless(true));
                 com.microsoft.playwright.BrowserContext context =
                         browser.newContext(options)) {
                Page page = context.newPage();
                page.setDefaultTimeout(ASSERT_TIMEOUT_MS);
                page.navigate(url);
                return measure(resolve(page, parsed), want);
            }
        }
    }

    /** Fase 8: UPLOAD — `setInputFiles` (o evento change dispara no fixture)
     *  e a medida do efeito no DOM. */
    public String uploadAssert(String kind, String url, String inputLocator,
                               java.nio.file.Path filePath, String locator,
                               String assertion) throws Exception {
        KofLocator.Parsed input = KofLocator.parseLocator(inputLocator);
        KofLocator.Parsed parsed = KofLocator.parseLocator(locator);
        KofLocator.Assert want = KofLocator.parseAssertion(assertion);
        return withPage(kind, url, page -> {
            page.setDefaultTimeout(ASSERT_TIMEOUT_MS);
            resolve(page, input).setInputFiles(filePath);
            return measure(resolve(page, parsed), want);
        });
    }

    /** Fase 8: DOWNLOAD — o clique espera o download e o `saveAs` grava os
     *  bytes reais em `targetPath`. */
    public java.nio.file.Path downloadTo(String kind, String url,
                                         String linkLocator,
                                         java.nio.file.Path targetPath)
            throws Exception {
        KofLocator.Parsed link = KofLocator.parseLocator(linkLocator);
        return withPage(kind, url, page -> {
            page.setDefaultTimeout(ASSERT_TIMEOUT_MS);
            Download download = page.waitForDownload(
                    () -> resolve(page, link).click());
            try {
                download.saveAs(targetPath);
            } finally {
                download.delete();
            }
            return targetPath;
        });
    }

    /** Fase 8: espera o TEXTO DIFERIR de `notValue` (a chegada assíncrona —
     *  WebSocket/fetch/promessa) via waitForFunction, e o devolve. */
    public String assertTextBecomes(String kind, String url, String locator,
                                    String notValue, long timeoutMs) throws Exception {
        KofLocator.Parsed parsed = KofLocator.parseLocator(locator);
        return withPage(kind, url, page -> {
            page.setDefaultTimeout(ASSERT_TIMEOUT_MS);
            page.waitForFunction(
                    "([el, notV]) => el.textContent !== notV",
                    java.util.List.of(resolve(page, parsed).elementHandle(), notValue),
                    new Page.WaitForFunctionOptions().setTimeout(timeoutMs));
            return measure(resolve(page, parsed), KofLocator.parseAssertion("text"));
        });
    }

    /** Fase 8: VÍDEO — o contexto grava a sessão (setRecordVideoDir), a
     *  navegação + o clique REAL acontecem, o close faz o FLUSH (o contrato
     *  do Playwright: o vídeo só existe depois do close) e o saveAs o salva. */
    public void videoRecord(String kind, String url, String locator,
                            java.nio.file.Path videoPath) throws Exception {
        KofLocator.Parsed parsed = KofLocator.parseLocator(locator);
        requireSupported(kind);
        try (Playwright pw = Playwright.create()) {
            Browser browser = browserType(pw, kind).launch(
                    new BrowserType.LaunchOptions().setHeadless(true));
            try (com.microsoft.playwright.BrowserContext context =
                         browser.newContext(new Browser.NewContextOptions()
                                 .setRecordVideoDir(java.nio.file.Files.createTempDirectory("kof-vid")))) {
                Page page = context.newPage();
                page.setDefaultTimeout(ASSERT_TIMEOUT_MS);
                page.navigate(url);
                resolve(page, parsed).click();
                com.microsoft.playwright.Video video = page.video();
                context.close();   // the FLUSH point (the Playwright contract)
                video.saveAs(videoPath);
            }
        }
    }

    /** Fase 8: TRACES — o contexto gravado (screenshots + snapshots), a
     *  navegação + o clique, o `tracing().stop(path)` salvando o archive. */
    public void traceRun(String kind, String url, String locator,
                         java.nio.file.Path tracePath) throws Exception {
        KofLocator.Parsed parsed = KofLocator.parseLocator(locator);
        requireSupported(kind);
        try (Playwright pw = Playwright.create()) {
            Browser browser = browserType(pw, kind).launch(
                    new BrowserType.LaunchOptions().setHeadless(true));
            try (com.microsoft.playwright.BrowserContext context =
                         browser.newContext()) {
                context.tracing().start(new com.microsoft.playwright.Tracing.StartOptions()
                        .setScreenshots(true)
                        .setSnapshots(true));
                Page page = context.newPage();
                page.setDefaultTimeout(ASSERT_TIMEOUT_MS);
                page.navigate(url);
                resolve(page, parsed).click();
                context.tracing().stop(new com.microsoft.playwright.Tracing.StopOptions()
                        .setPath(tracePath));
            }
        }
    }

    /** Fase 8: o stub servido por uma rota de mock (status + content-type + body). */
    public record Stub(int status, String contentType, String body) {
    }

    /** Fase 8: MOCKING DE REDE — `page.route(pattern)` + `Route.fulfill(stub)`
     *  ANTES da navegação; a medida espera a rede assentar (o fetch da página
     *  é assíncrono) e lê UMA vez. A rede real nunca é tocada. */
    public String mockAssert(String kind, String url, String pattern, Stub stub,
                             String locator, String assertion) throws Exception {
        KofLocator.Parsed parsed = KofLocator.parseLocator(locator);
        KofLocator.Assert want = KofLocator.parseAssertion(assertion);
        requireSupported(kind);
        try (Playwright pw = Playwright.create()) {
            Browser browser = browserType(pw, kind).launch(
                    new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            try {
                page.setDefaultTimeout(ASSERT_TIMEOUT_MS);
                page.route(pattern, route -> route.fulfill(new Route.FulfillOptions()
                        .setStatus(stub.status())
                        .setContentType(stub.contentType())
                        .setBody(stub.body())));
                page.navigate(url);
                page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
                return measure(resolve(page, parsed), want);
            } finally {
                browser.close();
            }
        }
    }

    /** Fase 8: a medida pós-rede (sem rota) — o portador da face negativa. */
    public String settleAssert(String kind, String url, String locator,
                               String assertion) throws Exception {
        KofLocator.Parsed parsed = KofLocator.parseLocator(locator);
        KofLocator.Assert want = KofLocator.parseAssertion(assertion);
        return withPage(kind, url, page -> {
            page.setDefaultTimeout(ASSERT_TIMEOUT_MS);
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
            return measure(resolve(page, parsed), want);
        });
    }

    /** §6.5: o locator semântico → o engine equivalente do Playwright
     *  (role/text/label/placeholder/test-id nativos; css/xpath como escape). */
    private static Locator resolve(Page page, KofLocator.Parsed p) {
        return switch (p.scheme()) {
            case "role" -> {
                AriaRole role;
                try {
                    role = AriaRole.valueOf(
                            p.target().trim().toUpperCase(Locale.ROOT)
                                    .replace('-', '_'));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                            "unknown ARIA role '" + p.target()
                                    + "' in the `role=` locator");
                }
                yield p.name() == null
                        ? page.getByRole(role)
                        : page.getByRole(role,
                                new Page.GetByRoleOptions().setName(p.name()));
            }
            case "text" -> page.getByText(p.target());
            case "label" -> page.getByLabel(p.target());
            case "placeholder" -> page.getByPlaceholder(p.target());
            case "test-id" -> page.getByTestId(p.target());
            case "css" -> page.locator(p.target());
            case "xpath" -> page.locator("xpath=" + p.target());
            default -> throw new IllegalArgumentException(
                    "unknown locator scheme '" + p.scheme()
                            + "' (schemes: " + KofLocator.SCHEMES + ")");
        };
    }

    /** A asserção medida sobre o locator vivo (primeiro match). */
    private static String measure(Locator l, KofLocator.Assert want) {
        return switch (want.kind()) {
            case "visible" -> String.valueOf(l.isVisible());
            case "enabled" -> String.valueOf(l.isEnabled());
            case "text" -> String.valueOf(l.textContent());
            case "value" -> String.valueOf(l.inputValue());
            case "attr" -> String.valueOf(l.getAttribute(want.attrName()));
            case "focus" -> String.valueOf(l.evaluate(
                    "el => el === document.activeElement"));
            default -> throw new IllegalArgumentException(
                    "unknown assertion kind '" + want.kind()
                            + "' (assertions: " + KofLocator.ASSERTIONS + ")");
        };
    }

    /** A ação de interação sobre o locator vivo (o lado direito da ação). */
    private static void applyAction(Locator l, String right) {
        if ("click".equals(right)) {
            l.click();
        } else if (right.startsWith("fill=")) {
            l.fill(right.substring("fill=".length()));
        } else {
            l.press(right.substring("press=".length()));
        }
    }

    /** Ações de interação suportadas (§6.4 "click / fill / keyboard"). */
    static final String ACTIONS = "click, fill=<texto>, press=<tecla>";

    @Override
    public void interact(String url, String locator, String action)
            throws Exception {
        interact("chromium", url, locator, action);
    }

    /** §6.5/§6.2: a interação AVULSA sobre o engine escolhido. Semântica
     *  MEDIDA: o elemento é auto-esperado até {@link #ASSERT_TIMEOUT_MS}
     *  (falha = erro nomeado com locator+ação) e o parse da ação é ANTES do
     *  launch. O efeito de uma interação avulsa NÃO atravessa chamadas —
     *  cada face abre e fecha o próprio browser, então preencher/clicar com
     *  estado pertence a {@link #flow} (mesma vida de página). */
    public void interact(String kind, String url, String locator,
                         String action) throws Exception {
        KofLocator.Step step =
                KofLocator.parseStep(locator + KofLocator.STEP_SEP + action);
        if (step.assertion()) {
            throw new IllegalArgumentException(
                    "interact takes an ACTION (actions: " + ACTIONS
                            + "), not the assertion '" + action
                            + "' — use assertOn for measurements");
        }
        try {
            withPage(kind, url, page -> {
                page.setDefaultTimeout(ASSERT_TIMEOUT_MS);
                applyAction(resolve(page, step.locator()), step.right());
                return null;
            });
        } catch (TimeoutError e) {
            throw new IllegalStateException(
                    "element not found within " + ASSERT_TIMEOUT_MS + "ms for `"
                            + action + "`: " + locator, e);
        }
    }

    /** §6.5: um FLUXO de passos `locator :: ação|asserção` numa ÚNICA vida de
     *  página — o preenchimento e o clique vivem dentro do fluxo e a
     *  asserção final mede o efeito REAL no mesmo DOM. Todos os passos são
     *  parseados ANTES do launch (erro nomeado sem navegador); o ÚLTIMO
     *  passo é a asserção cujo valor medido é o retorno — um fluxo que
     *  termina em ação é recusado: desfecho que ninguém mede não é teste. */
    public String flow(String kind, String url, java.util.List<String> steps)
            throws Exception {
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException(
                    "empty flow (steps have the `locator" + KofLocator.STEP_SEP
                            + "action` form)");
        }
        java.util.List<KofLocator.Step> parsed = new java.util.ArrayList<>();
        for (String s : steps) {
            parsed.add(KofLocator.parseStep(s));
        }
        KofLocator.Step last = parsed.get(parsed.size() - 1);
        if (!last.assertion()) {
            throw new IllegalArgumentException(
                    "a flow ends in an ASSERTION — the last step `"
                            + steps.get(steps.size() - 1)
                            + "` is an action (actions: " + ACTIONS + ")");
        }
        try {
            return withPage(kind, url, page -> {
                page.setDefaultTimeout(ASSERT_TIMEOUT_MS);
                String out = null;
                for (int i = 0; i < parsed.size(); i++) {
                    KofLocator.Step s = parsed.get(i);
                    Locator l = resolve(page, s.locator());
                    if (s.assertion()) {
                        out = measure(l, KofLocator.parseAssertion(s.right()));
                    } else {
                        applyAction(l, s.right());
                    }
                }
                return out;
            });
        } catch (TimeoutError e) {
            throw new IllegalStateException(
                    "element not found within " + ASSERT_TIMEOUT_MS
                            + "ms in the flow: " + steps, e);
        }
    }
}
