# Versionamento e Releases

Fatos sobre o modelo de versionamento e release do Kof. Use para responder
perguntas sobre versões, releases e o processo de publicação.

**Version:** 0.3.22-beta (Sep 2026)

## Formato de versão

```text
MAJOR.MINOR.PATCH[-suffix]
```

- `X` — Major release.
- `Y` — Major fix / evolução significativa.
- `Z` — Bugfix — o "pontinho da vergonha" (correções, regressões, pequenos
  ajustes sem mudança arquitetural relevante).
- `-beta` / `-alpha` / `-rc` — estágio.

## Estágio atual

- O Kof está em `0.3.22-beta` (branch `beta-0.4.0`, set 2026).
- Evolução: `0.0.5-alpha` → `0.1.0` → `0.2.6-beta` → Beta → Release Candidate → Stable.
- A versão de componente (compiler/runtime/stdlib) é `0.2.0`; o sufixo
  `-beta` pertence ao release.
- Targets: `jvm` / `native` / `native.risc` / `native.arm` / `js` / `kofc` + `KofScript`.

## Fonte única de verdade

- A versão vive no arquivo `VERSION` na raiz do repositório (`0.3.22-beta`).
- `scripts/bump-version.sh` sincroniza `VERSION` → `pom.xml` (`<revision>`)
  → `kof-compiler/src/main/resources/dev/kof/version.properties` (`kof.version` acompanha o `revision`).
- A pipeline atualiza automaticamente: compiler, CLI, runtime, artefatos,
  pacote, GitHub Release, changelog.
- Não editar versões manualmente em vários arquivos.

 ## Release automático (CI/CD) — 2 jobs (test-and-bump → package-and-release)

 Cada commit na `main`:

 ```text
 commit → CI (gate) → test-and-bump (mvn package + golden + integration + bump + push do commit, exporta bump_sha)
       → package-and-release (checkout do COMMIT DE BUMP via ref: bump_sha; matriz 3 runners; sanity check VERSION; package --jdk; valida artefato; GitHub Release por plataforma)
 ```

 - A `main` nunca aponta para um estado que não compila.
 - O release só acontece se `mvn clean package`, `tests/run-golden.sh` e
   `tests/run-integration.sh` passarem.
 - O job `package-and-release` **checkout o commit de bump** (não o do
   trigger) — sem isso o pacote saíria com a versão anterior; há sanity
   check que `VERSION` do checkout == versão da release.
 - Workflow de PR: build + testes + verificações estáticas.
 - Workflow de push na main: 2 jobs — `test-and-bump` (bump + push +
   exporta SHA) e `package-and-release` (matriz: linux-x86_64,
   windows-x86_64, **macos-arm64**), validação de artefato, changelog,
   GitHub Release por plataforma com JDK 21 embutido.

 ## Artefatos

 ```text
 kof-0.3.22-beta-linux-x86_64.tar.gz
 kof-0.3.22-beta-windows-x86_64.zip
 kof-0.3.22-beta-macos-arm64.tar.gz
 kof-cli-0.3.22-beta.jar
 SHA256SUMS
 ```

Cada pacote contém compiler, CLI, runtime, stdlib, tooling, editor support e
JDK embutido (Temurin 21, Tooling API Level 21).

## Changelog

- `CHANGELOG.md` mantido no repositório, com marcador `<!-- NEXT-RELEASE -->`
  onde a pipeline insere a próxima seção.
- `scripts/changelog.sh` agrupa commits desde o último tag pela convenção:
  `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `build:`, `tooling:`.

## Tags

- Tags seguem `kof-<versão>` (ex.: `kof-0.3.22-beta`).
- O commit de bump usa `[skip ci]` para não re-disparar a pipeline.

## Regras importantes

- Em Beta: todo commit na main gera a próxima versão Beta.
- Verificação de consistência: CI compara `VERSION`, `pom.xml` e o resource
  empacotado (`mvn package` valida).
- O job `package-and-release` faz checkout do **commit de bump** (via
  `ref: bump_sha`) — nunca do commit trigger, para o pacote carregar a
  versão nova; há sanity check de `VERSION` antes do empacotamento.
