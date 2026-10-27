[English](debugging-js.md) | [Português](debugging-js.pt_BR.md)

# DEBUGGING_JS.md — Debug no target KofJS

**Status:** **gap honesto** — `kof debug --target js` recusa com diagnóstico
(R6/R7). O source map é ✅ emitido mas **por função** (um mapeamento por
declaração de função), não por linha. O target JS roda no **motor GraalJS
embutido** (`KofJsRunner`), não no Node.
**Data:** 20 de setembro de 2026
**Versão:** 0.6.7 (7 targets; free-list + pthread spawn + FP XMM)

---

## 1. Fluxo (hoje)

```text
Kof Source
    ↓
KofJS (ES Modules) + .mjs.map   (mapa Kof → JS por função)
    ↓
Runtime GraalJS EMBUTIDO (in-process; KofJsRunner)
    ↓
kof debug --target js  →  recusa honesta (sem inspector para anexar)
```

## 2. Source Maps (medido 20/09)

O JsBackend gera `.mjs` + um source map V3, mas o emissor registra **um
mapeamento por declaração de função** (`JsIr.JsFunctionLine`: nome, linha
gerada, linha Kof). Ele **não** basta para um breakpoint numa linha Kof
arbitrária — seriam necessários mapeamentos por statement no emissor.

## 3. Execução — gap honesto (R6/R7)

`kof debug --target js app.kf` **não está implementado**; sai com 1 e:

```text
debug js: honest gap — the JS target runs on the EMBEDDED engine
(there is no node/inspector to attach to). Roadmap §19.5 face 7 stays open.
```

Dois bloqueios medidos (20/09):

1. o runtime JS de produção é o **GraalJS embutido** (`KofJsRunner`), então o
   inspector do Node é irrelevante — ligar o inspector do GraalJS é decisão de
   engine (rule 6);
2. mesmo com inspector, o source map por função acima significa que um
   breakpoint por linha Kof ainda não é expressável — o emissor precisa
   registrar mapeamentos por statement (lane compiler).

Um breakpoint de entrada de função disfarçado de linha `L` seria fachada (Q7),
por isso não é entregue.
