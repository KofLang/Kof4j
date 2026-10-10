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

    /** Um run completo sobre um único launch: a lambda recebe a Page viva. */
    public <T> T withPage(String url, java.util.function.Function<Page, T> body) {
        try (Playwright pw = Playwright.create()) {
            Browser browser = pw.chromium().launch(
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
        return withPage(url, Page::content);
    }

    @Override
    public String dumpDom(String url) throws Exception {
        return content(url);
    }

    @Override
    public void screenshot(String url, String path) throws Exception {
        withPage(url, page -> {
            page.screenshot(new Page.ScreenshotOptions().setPath(Path.of(path)));
            return null;
        });
    }
}
