package dev.kof.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import java.nio.file.Files;
import java.nio.file.Path;

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
}
