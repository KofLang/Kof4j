package dev.kof.browser;

/**
 * §6.1 Browser abstraction (the testing-platform plan, `D-TESTING-PLATFORM`).
 *
 * <p>Kof owns the API; browsers and Playwright/Cypress are providers behind
 * this SPI. A provider launches a browser against a URL and exposes the
 * rendered DOM — the minimal capability the first slice needs (the seed
 * `KofJsBrowserE2ETest` proves real Chrome `--headless --dump-dom` renders
 * KofJS output for real). More capabilities (click/fill/locate/assert,
 * network interception, screenshots) land per provider slice, discovered
 * during implementation — never assumed (the plan §6.4).
 *
 * <p>Contract: implementations are honest — a missing binary/tool must be
 * reported through {@link #probe()} returning {@code null} so the gate skips
 * with a named reason (Q7, never a false green, never a silent fallback).
 */
public interface BrowserProvider {

    /** Nome declarado (o manifesto `kof-test.kofmd` casa por este nome). */
    String name();

    /** Proba o binário: versão impressa, ou null quando indisponível. */
    String probe();

    /** Navega para a URL e devolve o DOM renderizado (dump). */
    String dumpDom(String url) throws Exception;

    /** Captura um screenshot da página em {@code path}. O default é a
     *  recusa honesta da capacidade (`capability unsupported`) — o plano §6.3:
     *  um backend que não tem a capacidade nunca finge equivalência. */
    default void screenshot(String url, String path) throws Exception {
        throw new UnsupportedOperationException(
                "capability unsupported: screenshot on " + name());
    }
}
