package dev.kof.browser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * §8.3 (testing-platform plan, fase 8): as faces de ACESSIBILIDADE medidas —
 * roles, labels, navegação por teclado, nomes acessíveis, estrutura semântica.
 * O `getByRole` com `name=` É a computação de nome acessível do Playwright
 * (aria), então cada face aqui mede a árvore de acessibilidade REAL, não o
 * texto cru do DOM. Nunca substitui o teste manual de acessibilidade (§8.3):
 * o contraste fica FORA desta fatia (nomeado).
 */
class PlaywrightA11yE2ETest {

    /** Fixture §8.3: formulário com estrutura semântica — labels associados
     *  por `for`, um botão com aria-label explícito, um status com role
     *  explícito, um botão desabilitado e inputs na ordem de tabulação. */
    private static Path fixture(Path dir) throws Exception {
        Path page = dir.resolve("a11y.html");
        Files.writeString(page, """
                <html><head><meta charset="utf-8"></head><body>
                <form>
                  <label for="user">Usuário</label>
                  <input id="user" autocomplete="off">
                  <label for="pass">Senha</label>
                  <input id="pass" type="password">
                  <button id="save" type="button" aria-label="Salvar formulário">💾</button>
                  <button type="button" disabled aria-label="Excluir tudo">🗑</button>
                  <p id="status" role="status">pronto</p>
                </form>
                </body></html>
                """);
        return page;
    }

    private static PlaywrightLibProvider provider() {
        return new PlaywrightLibProvider();
    }

    private static void assumeA11yChromium() {
        String v = new PlaywrightLibProvider().probe("chromium");
        assumeTrue(v != null, "sem o chromium pinado pelo driver do Playwright");
    }

    @Test
    void accessibleNamesResolveThroughGetByRole(@TempDir Path dir)
            throws Exception {
        assumeA11yChromium();
        PlaywrightLibProvider p = provider();
        String url = fixture(dir).toUri().toString();
        // o nome acessível vem do aria-label (não do texto — o texto é um emoji)
        
        assertEquals("true", p.assertOn(url, "role=button[name=\"Salvar formulário\"]", "visible"));
        // o botão com aria-label está habilitado (a face disabled é medida
        // no próprio teste dela, abaixo)
        assertEquals("true", p.assertOn(url, "role=button[name=\"Salvar formulário\"]", "enabled"));
        // o role explícito é medido pela árvore de acessibilidade
        assertEquals("status", p.assertOn(url, "css=#status", "attr=role"));
    }

    @Test
    void labelAssociationReachesTheInput(@TempDir Path dir) throws Exception {
        assumeA11yChromium();
        PlaywrightLibProvider p = provider();
        String url = fixture(dir).toUri().toString();
        // `<label for>` associa o label ao input — o fill por LABEL funciona
        // (UM fluxo: a interação e a medida vivem na mesma vida de página)
        assertEquals("amel", p.flow("chromium", url, java.util.List.of(
                "label=Usuário :: fill=amel",
                "css=#user :: value")));
        // e o locator por label mede o input associado
        assertEquals("", p.assertOn(url, "label=Senha", "value"));
    }

    @Test
    void theDisabledButtonIsNotEnabled(@TempDir Path dir) throws Exception {
        assumeA11yChromium();
        PlaywrightLibProvider p = provider();
        String url = fixture(dir).toUri().toString();
        assertEquals("false", p.assertOn(url, "role=button[name=\"Excluir tudo\"]", "enabled"));
        assertEquals("true", p.assertOn(url, "role=button[name=\"Salvar formulário\"]", "enabled"));
    }

    @Test
    void tabMovesFocusThroughTheSemanticOrder(@TempDir Path dir) throws Exception {
        assumeA11yChromium();
        PlaywrightLibProvider p = provider();
        String url = fixture(dir).toUri().toString();
        // a navegação por teclado: Tab a partir do body → o primeiro input;
        // outro Tab → o segundo; o desfecho de cada Tab é MEDIDO (focus).
        assertEquals("true", p.flow("chromium", url, java.util.List.of(
                "css=body :: press=Tab",
                "css=#user :: focus")));
        assertEquals("true", p.flow("chromium", url, java.util.List.of(
                "css=body :: press=Tab",
                "css=body :: press=Tab",
                "css=#pass :: focus")));
        // o foco NÃO está no segundo input após um único Tab (o negativo mede)
        assertEquals("false", p.flow("chromium", url, java.util.List.of(
                "css=body :: press=Tab",
                "css=#pass :: focus")));
    }
}
