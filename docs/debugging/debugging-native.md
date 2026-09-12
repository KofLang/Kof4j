# DEBUGGING_NATIVE.md — Debug no target Native

**Status:** Parcial (02/09) — line table DWARF real no ELF x86-64 (Fase 5 parcial): o `NativeBackend`
emite `.file 1 "<fonte.kf>"` + `.loc 1 <linha> 0` quando debug enabled; `objdump
--dwarf=decodedline` mostra o arquivo Kof e a linha de cada instrução
(`NativeDwarfLineInfoTest`). Variáveis locais, DAP no nativo e stepping pendentes.
**Data:** 2 de setembro de 2026
**Versão:** 0.2.6-beta (810 testes; 7 targets)

---

## 1. Fluxo

```text
Kof Debug Info (IR)
    ↓
símbolos + line tables (DWARF futuro)
    ↓
ELF x86-64
    ↓
debug adapter (frame info Kof)
    ↓
DAP
    ↓
Editor
```

## 2. Fase inicial

- símbolos de função (já existem: `ClassName_methodName`);
- source locations por instrução (Fase 1 da IR);
- line tables ✅ (`.file`/`.loc` GAS → `.debug_line`; verificado com `objdump --dwarf=decodedline`);
- locals com tipos Kof;
- scopes;
- stack frames.

## 3. Depois

- DWARF completo;
- localizações otimizadas de variáveis;
- inspeção de memória nativa.

## 4. Regra

Nunca mostrar assembly como experiência primária — o mapeamento
`assembly → linha Kof` é interno.