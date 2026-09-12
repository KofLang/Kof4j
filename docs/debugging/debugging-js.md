# DEBUGGING_JS.md — Debug no target KofJS

**Status:** Planejado — source maps futuros
**Data:** 27 de agosto de 2026
**Versão:** 0.2.6-beta (810 testes; 7 targets; free-list + pthread spawn + FP XMM)

---

## 1. Fluxo

```text
Kof Source
    ↓
KofJS (ES Modules)
    ↓
Source Map (Kof → JS)
    ↓
Node Inspector / Chrome DevTools
    ↓
kof-debug (DAP)
    ↓
Editor
```

## 2. Source Maps

O JsBackend gera `.mjs` + source maps que mapeiam cada linha JS para a
linha Kof. Breakpoint em `main.kf:15` para na instrução JS correspondente.

O usuário nunca procura o `.mjs` manualmente.

## 3. Execução

`kof debug --target js app.kf` lança o runtime com `--inspect` e o
adaptador conversa pelo protocolo do Inspector.