# 33 — Versionamento e Releases

> **Kof 0.3.22-beta — set 2026**

## Formato

```text
MAJOR.MINOR.PATCH
```

- `X` — Major release
- `Y` — Major fix / evolução significativa
- `Z` — Bugfix (**o pontinho da vergonha**)

O PATCH é o *pontinho da vergonha*: bugfixes, correções, regressões e
pequenos ajustes sem mudança arquitetural relevante.

## Estágio Beta (0.3.22-beta)

O Kof saiu de `0.0.x` (Alpha) para `0.2.6-beta`. Cada release carrega o sufixo:

```text
0.2.6-beta
```

Nada é chamado de stable. A evolução foi: Alpha → Beta (0.2.6-beta, 02 set 2026; hoje 0.3.22-beta) → Release Candidate → Stable. A cadeia `intention->Kof->frontend->IR->backend->runtime` vale para todos.

Targets oficiais em 0.2.0: `jvm`, `native` (x86-64), `native.risc` (riscv64), `native.arm` (aarch64), `js` (KofJS), `kofc` (KofC C subset nativo-only). Target separation já no `Target` enum.

## Fonte única de verdade

A versão vive em `VERSION` (raiz do repositório). `scripts/bump-version.sh`
sincroniza:

```text
VERSION (0.3.22-beta) → pom.xml (`<revision>`) → dev/kof/version.properties (empacotado)
```

Nunca edite versões espalhadas por arquivos — a pipeline cuida disso.

## Release automático

Cada commit na `main` gera a próxima versão, em dois jobs
(`.github/workflows/release.yml`):

```text
commit → CI ()
       → test-and-bump (Ubuntu)
            ├─ mvn clean package (gate) + golden + integração
            ├─ version bump (scripts/bump-version.sh)
            ├─ seção do changelog → CHANGELOG.md
            └─ commit + push do bump ([skip ci]) — exporta o SHA
       → package-and-release (matriz: 3 runners)
            ├─ checkout do COMMIT DE BUMP (não o do trigger)
            ├─ sanity check: VERSION do checkout == versão da release
            ├─ scripts/package.sh --jdk
            └─ GitHub Release por plataforma
```

- uma release **por plataforma**: `kof-<v>-linux-x86_64`,
  `kof-<v>-macos-arm64` (Apple Silicon), `kof-<v>-windows-x86_64`
  (não existe `macos-x86_64`);
- a `main` nunca aponta para um estado que não compila;
- release quebrada nunca é publicada;
- artefatos: `kof-<versão>-<os>-<arch>.tar.gz`/`.zip` + `SHA256SUMS` +
  `kof-cli-<versão>.jar`;
- changelog gerado por `scripts/changelog.sh` a partir da convenção de
  commits (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `build:`,
  `tooling:`), inserido em `CHANGELOG.md` no marcador `NEXT-RELEASE`.
- Native free-list GC e MySQL via `kof_db` entraram no changelog de 0.2.0 como features de runtime, não de linguagem.

## Referências

- [docs/distribution/VERSIONING.md](../docs/distribution/VERSIONING.md)
- [docs/distribution/RELEASES.md](../docs/distribution/RELEASES.md)
