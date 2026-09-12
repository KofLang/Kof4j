# Kof Training — Corpus para LLMs

**Version:** 0.3.22-beta (Sep 2026) — 810 tests · targets jvm/native/native.risc/native.arm/js/kofc + KofScript

Este diretório contém conhecimento estruturado sobre a linguagem Kof, otimizado para modelos de linguagem.

## Propósito

O corpus permite que LLMs:
- Entendam a sintaxe e semântica do Kof
- Gerem código válido e idiomático
- Corrijam código Kof
- Expliquem código Kof
- Migrem Java/Spring para Kof
- Gerem APIs, testes e documentação

O objetivo não é ensinar apenas a gramática — é ensinar **como pensar em Kof**:
representar o domínio com as abstrações da linguagem, não traduzir Java.

## Estrutura

```
training/
├── README.md              # Este arquivo
├── language/              # Conceitos da linguagem (estado real)
│   ├── overview.md
│   ├── syntax.md
│   ├── types.md
│   ├── classes.md
│   ├── exceptions.md
│   ├── arrays.md
│   ├── strings.md
│   ├── io.md
│   ├── ui.md
│   └── security.md
├── reference/             # Referência técnica
│   ├── compiler.md
│   └── targets.md
├── idioms/                # FORMA IDIOMÁTICA de cada problema (BAD/GOOD/WHY)
│   ├── collections.md
│   ├── classes.md
│   ├── records.md
│   ├── functions.md
│   ├── control-flow.md
│   ├── strings.md
│   ├── errors.md
│   ├── web.md
│   ├── architecture.md
│   ├── composition.md
│   └── concurrency.md
├── anti-patterns/         # Catálogo de o que NÃO fazer
│   ├── common-mistakes.md
│   ├── java-like-code.md
│   ├── unnecessary-abstraction.md
│   ├── manual-data-structures.md
│   ├── sentinel-values.md
│   ├── duplicate-state.md
│   ├── fake-idioms.md
│   ├── premature-optimization.md
│   └── runtime-workarounds.md
├── datasets/              # Material estruturado para ingestão automatizada
│   └── kof-idioms.json
├── patterns/              # Padrões idiomáticos
│   └── common-patterns.md
├── examples/              # Exemplos executáveis
│   ├── hello.kf
│   ├── classes.kf
│   ├── inheritance.kf
│   ├── web.kf
│   └── security.kf
├── distribution/          # Instalação e distribuição
│   └── install.md
├── tooling/               # CLI, LSP e editor support
│   └── cli.md
├── releases/              # Versionamento e pipeline de release
│   └── versioning.md
└── migration/             # Migração
    └── java-to-kof.md
```

## Regras

1. Todo conteúdo deve refletir o código REAL
2. Não documentar features inexistentes (ver `anti-patterns/fake-idioms.md`)
3. Exemplos devem ser verificáveis (compilar de preferência)
4. Workarounds são marcados `WORKAROUND` — nunca ensinados como idiom
5. Features sensíveis à versão registram `Introduced`/`Status`
6. Código que compila ≠ código idiomático Kof — o corpus ensina a diferença
7. Se houver conflito: implementação → testes → documentação → training