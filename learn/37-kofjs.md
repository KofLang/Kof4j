# 37 — KofJS: o caminho da Web

> **Kof 0.2.6-beta — 810 testes — targets jvm/native/native.risc/native.arm/js/kofc — `intention->Kof->frontend->IR->backend->runtime`**

KofJS é o target `js` da Kof: a mesma linguagem, o mesmo frontend e a
mesma Kof IR gerando **ES Modules (ECMAScript 2022+)** — sem Node.js, sem
runtime externo. Com ele vem a plataforma de UI (`kof.ui`) que renderiza em
webview nativo (WebKitGTK) ou no browser.

## A ideia em uma frase

Uma linguagem, seis alvos: `kof build --target=jvm|native|native.risc|native.arm|js` + `kof c`/`kof script`. O que muda é
o backend; o código Kof é o mesmo (`intention->Kof->frontend->IR->backend->runtime`).

```text
                    Kof Source
                         │
                         ▼
                 ┌──────────────┐
                 │ Kof Frontend │
                 └──────┬───────┘
                        │
                        ▼
                    Kof IR
                        │
          ┌─────────────┼─────────────┐
          │             │             │
          ▼             ▼             ▼
        JVM           Native        KofJS
          │             │             │
       .class          ELF           .mjs
```

## Roteiro

1. **[Fundamentos da linguagem](03-language-basics.md)** — qualquer programa
   Kof compila para JS; comece por aqui.
2. **[Funções](06-functions.md) e [Lambdas](16-lambdas.md)** — lambdas
   compilam para classes sintéticas com `invoke()`; **capturas** (foto
   somente-leitura do valor) funcionam nos três alvos.
3. **[Classes](07-classes-and-objects.md)** — campos estáticos são o
   estado global de uma aplicação KofJS (o padrão dos contadores de UI).
4. **Executar JS**:
   ```bash
   kof build src --target=js            # gera Default.mjs + kof-runtime.mjs
   kof run src --target=js              # executa na engine embarcada (GraalJS)
   ```
   O alvo JS não precisa de Node: o próprio Kof executa o módulo. Programas
   com janela de `kof.ui` abrem o **webview nativo** (`bin/kof-webview`).
5. **[kof.ui — a plataforma de UI](35-kof-ui.md)** — `Window`, `Label`,
   `Button` (com ações), `Input`, `Column`/`Row`, `View`+`Style`.
6. **Deploy** — `kof build --target=js` gera `index.html` + módulos: sirva a
   pasta como uma aplicação web estática (qualquer servidor HTTP).

## O que funciona hoje (estado real)

Backend **alpha**. KofJS gera ES Modules rodados na GraalJS embutida do Kof
— sem Node.js; `kof.http` vem por interop com o `Java HttpClient`.

| Área | Estado |
|------|--------|
| Linguagem completa (classes, herança, generics, exceptions, List, JSON) | ✅ |
| Lambdas com capturas | ✅ (3 alvos) |
| `spawn`/`await`/`channel<T>()` (concorrência) | ✅ real (async/await/Promise, CONC003 fechado 03/09) |
| `kof http` client (get/post/put/delete/patch/options + timeout/retry/circuit) | ✅ (interop Java HttpClient) |
| `kof.ui`: cores, temas, widgets, layout, estilo, eventos | ✅ (JS render) |
| Router (`Router.route/go/replace/back/forward/...`) | ✅ (JS real; 31/08) |
| Webview nativo `bin/kof-webview` (WebKitGTK embutido) | ✅ Linux |
| `kof run --target=js` (GraalJS embarcado) | ✅ |
| `kof build --target=js` + `index.html` (deploy estático) | ✅ |
| `kof.db` | ❌ (DB001) |
| io de arquivos no browser | ~ (io real só no runner embarcado; browser cai em erro claro) |

## Aplicação de exemplo: contador

```kof
class App {
    static Int count = 0
}

main() {
    var w = Window("Contador")
    var label = Label("contagem: 0")
    w.bind(label)
    w.bind(Button("+1", () -> {
        App.count = App.count + 1
        label.text = "contagem: " + App.count
    }))
    w.show()
}
```

```bash
kof run contador.kf --target=js
```

A janela abre com o WebKit de verdade; cada clique atualiza o label ao vivo;
**fechar a janela encerra o programa**.

## Limitações e gaps

- **JVM/Native**: os handles de `kof.ui` são no-ops (renderização é KofJS).
- **Browser**: io de arquivos lança erro claro (`kof_platform` só existe no
  runner embarcado); `print` cai no console do browser.
- **Capturas** são fotos: para estado mutável use campos estáticos.
- **Estáticos no Native**: não são suportados (no-op).

## Referências

- `docs/targets/KOFJS.md` — arquitetura do backend JS
- `docs/status.md` — estado do projeto (seção kof.ui)
- `native/webview/kof-webview.c` — shell WebKitGTK sem headers
- Testes: `UiE2ETest`, `WindowE2ETest`, `KofJsE2ETest`, `BackendParityTest`
