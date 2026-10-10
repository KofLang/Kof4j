[English](VERSIONING.md) | [Português](VERSIONING.pt_BR.md)

# Versionamento do Kof

## Formato

```text
MAJOR.MINOR.PATCH
```

A hierarquia conceitual:

```text
Major releases
    >
Major fixes
    >
Bugfixes
```

| Componente | Significado |
|-----------|-------------|
| `X` (MAJOR) | Release maior |
| `Y` (MINOR) | Major fix / evolução significativa |
| `Z` (PATCH) | Bugfix — o *pontinho da vergonha* |

O `PATCH` é carinhosamente chamado de **pontinho da vergonha** porque
representa principalmente:

- bugfix;
- correção;
- regressão;
- pequenos ajustes;
- pequenas melhorias sem mudança arquitetural relevante.

## Estágio atual

O Kof está na linha **0.5.0 beta** (branch ativa de desenvolvimento `lab`,
`D-BRANCH-PIPELINE`; a linha 0.5.0 foi ratificada em `beta-0.5.0`/`D-BRANCH-0.5.0`,
`SUPERSEDED` pelo cutover de 28/09).
A versão comprometida é `0.5.0-beta`; a linha publicada anterior foi
`0.4.x-beta`.

A escada de estágios é **Alpha → Beta → Release Candidate → Stable** (ver
`release-naming.md`). A fase atual é **Beta**; nada é stable ainda, e mudanças
de quebra continuam possíveis antes do 1.0 (sempre via decisão MINOR
registrada — ver abaixo).

## Fonte única de verdade

A versão vive em **um único arquivo**: `VERSION` na raiz do repositório.

```text
VERSION ──► scripts/bump-version.sh ──► pom.xml (<revision>)
                                     ──► kof-compiler/src/main/resources/dev/kof/version.properties
```

A pipeline atualiza automaticamente:

- versão do compiler;
- versão da CLI;
- metadata do runtime;
- artefatos (jars);
- pacote de distribuição;
- GitHub Release;
- changelog.

**Nada de versão hardcoded em dezenas de arquivos** — isso é receita para
inconsistência. Se a versão precisa mudar, muda-se o `VERSION` (ou a
pipeline faz isso) e o resto segue.

## Quando a versão muda

Classificação e corte são **decisões distintas**. A política normativa é a
**`D-VERSIONING-RELEASE`** (`docs/development/DECISIONS.md`); em resumo:

- **PATCH pré-1.0:** só quando a mudança não adiciona nem altera **nenhuma
  superfície pública contratada** (bugfix, segurança/regressão, fix de
  paridade, refactor interno, CI/tooling/packaging, docs).
- **MINOR pré-1.0 (obrigatório):** qualquer **superfície pública contratada
  nova ou alterada** (sintaxe/operador/semântica observável nova, API ou
  namespace público, comando/flag público, capacidade pública da stdlib,
  contrato de pacote/registry/interop, alvo promovido a Supported/Stable) —
  com Decision ID registrado.
- **Pós-1.0:** SemVer estrito — PATCH = fix retrocompatível, MINOR =
  funcionalidade pública retrocompatível nova, MAJOR = mudança incompatível de
  contrato.
- **Gatilho ≠ corte:** `LAST_RELEASE..ACTIVE_BRANCH` cruzando **100–150
  commits** (ou um evento de segurança/crítico, ou decisão explícita da
  mantenedora) abre uma **avaliação** de release — nunca publica uma release
  por si só. O gate comum de elegibilidade vive na `D-VERSIONING-RELEASE`.
- O primeiro `1.0.0` só existe quando o EXIT GATE da `D-RELEASE-1.0` estiver
  totalmente GREEN no mesmo candidato e nenhuma aresta da `D-1.0-EDGES`
  estiver aberta.

## Verificação

`kof version` e `kof info` reportam a versão empacotada. O CI verifica que
`VERSION`, `pom.xml` e o resource de versão concordam antes de qualquer
build.