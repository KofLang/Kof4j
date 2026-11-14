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

    /** §6.5: resolve um locator semântico sobre a página renderizada e mede
     *  UMA asserção (`visible`, `enabled`, `text`, `value`, `attr=<name>`),
     *  devolvendo o valor medido ("true"/"false", o texto, o valor do input,
     *  o atributo — "null" quando o atributo não existe). O default é a
     *  recusa honesta da capacidade — a matriz §6.4 marca locator/assertion
     *  só no Playwright; os outros backends nunca fingem equivalência. */
    default String assertOn(String url, String locator, String assertion)
            throws Exception {
        throw new UnsupportedOperationException(
                "capability unsupported: locator/assert on " + name());
    }

    /** Fase 8: MOCKING DE REDE — registra `pattern` → o stub `fulfill` e mede
     *  `locator`/`assertion` sobre a página renderizada COM a rota (a rede
     *  real nunca é tocada). O default é a recusa honesta da capacidade. */
    default String mockAssert(String kind, String url, String pattern,
                              PlaywrightLibProvider.Stub stub, String locator,
                              String assertion) throws Exception {
        throw new UnsupportedOperationException(
                "capability unsupported: network mock on " + name());
    }

    /** Fase 8: mede `locator`/`assertion` depois de a rede assentar
     *  (NETWORKIDLE) — o portador das faces cujo desfecho é assíncrono
     *  (fetch/promessa). O default é a recusa honesta da capacidade. */
    default String settleAssert(String kind, String url, String locator,
                                String assertion) throws Exception {
        throw new UnsupportedOperationException(
                "capability unsupported: settle-assert on " + name());
    }

    /** Fase 8: UPLOAD — `setInputFiles` em `inputLocator` com `filePath` e a
     *  medida de `locator`/`assertion` sobre o efeito no DOM. O default é a
     *  recusa honesta da capacidade. */
    default String uploadAssert(String kind, String url, String inputLocator,
                                java.nio.file.Path filePath, String locator,
                                String assertion) throws Exception {
        throw new UnsupportedOperationException(
                "capability unsupported: file upload on " + name());
    }

    /** Fase 8: DOWNLOAD — clica `linkLocator`, espera o download e salva em
     *  `targetPath` (devolvendo o path salvo). O default é a recusa honesta. */
    default java.nio.file.Path downloadTo(String kind, String url,
                                          String linkLocator,
                                          java.nio.file.Path targetPath)
            throws Exception {
        throw new UnsupportedOperationException(
                "capability unsupported: file download on " + name());
    }

    /** Fase 8: EMULAÇÃO MOBILE — um contexto com `viewportWidth`×
     *  `viewportHeight` + `hasTouch`, a medida pelo MESMO contexto (o DOM
     *  prova o que o window vê). O default é a recusa honesta. */
    default String deviceAssert(String kind, String url, int viewportWidth,
                                int viewportHeight, boolean hasTouch,
                                String locator, String assertion)
            throws Exception {
        throw new UnsupportedOperationException(
                "capability unsupported: device emulation on " + name());
    }

    /** Fase 8: TRACES — grava a interação (navegar + clicar `locator`) no
     *  archive de trace do Playwright e salva em `tracePath` (o pós-mortem que
     *  o desenvolvedor abre depois de uma falha). O default é a recusa honesta
     *  da capacidade. */
    default void traceRun(String kind, String url, String locator,
                          java.nio.file.Path tracePath) throws Exception {
        throw new UnsupportedOperationException(
                "capability unsupported: trace recording on " + name());
    }

    /** Fase 8: VÍDEO — o contexto grava a sessão (`setRecordVideoDir`), a
     *  navegação + o clique REAL acontecem, o close faz o flush e o vídeo é
     *  salvo em `videoPath`. O default é a recusa honesta da capacidade. */
    default void videoRecord(String kind, String url, String locator,
                             java.nio.file.Path videoPath) throws Exception {
        throw new UnsupportedOperationException(
                "capability unsupported: video recording on " + name());
    }

    /** Fase 8: espera o TEXTO de `locator` DIFERIR de `notValue` (a chegada
     *  assíncrona — WebSocket/fetch/promessa) e o devolve. O default é a
     *  recusa honesta da capacidade. */
    default String assertTextBecomes(String kind, String url, String locator,
                                     String notValue, long timeoutMs) throws Exception {
        throw new UnsupportedOperationException(
                "capability unsupported: text-becomes wait on " + name());
    }

    /** Fase 8: DIAGNÓSTICOS AVANÇADOS — a saída do console e os erros JS não
     *  capturados da página, coletados durante o load (o "por que falhou").
     *  O default é a recusa honesta da capacidade. */
    default dev.kof.browser.PageDiagnostics diagnostics(String kind, String url)
            throws Exception {
        throw new UnsupportedOperationException(
                "capability unsupported: page diagnostics on " + name());
    }

    /** §6.5/§6.2: resolve um locator semântico sobre a página renderizada e
     *  EXECUTA uma interação (`click`, `fill=<texto>`, `press=<tecla>`) — o
     *  retorno vazio é o contrato: a chamada auto-espera o elemento e só
     *  volta quando a ação aconteceu (falha = exceção nomeada). O default é
     *  a recusa honesta da capacidade — a matriz §6.4 marca click/fill só no
     *  Playwright; os outros backends nunca fingem equivalência. */
    default void interact(String url, String locator, String action)
            throws Exception {
        throw new UnsupportedOperationException(
                "capability unsupported: interact on " + name());
    }
}
