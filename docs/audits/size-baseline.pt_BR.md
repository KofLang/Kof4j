[English](size-baseline.md) | [Português](size-baseline.pt_BR.md)

# D-SIZE-BUDGET — baseline da Fase 1 (só medição)

> Decisão: `DECISIONS.md` `D-SIZE-BUDGET` (mantenedora 01/10/2026, opção A). Este
> documento + `size-baseline-2026-10-01.tsv` são o **entregável da Fase 1**:
> só observabilidade — **nenhuma mudança de comportamento, dependência, packaging
> ou CI**. Ele mede o peso da distribuição para que uma decisão futura (reduções,
> experimentos de packaging, gate de CI) tenha uma baseline para raciocinar.

## Como reproduzir

```
scripts/size/measure-size.sh --out /tmp/size.tsv   # regera a baseline
scripts/size/compare-size.sh OLD.tsv NEW.tsv       # diff de duas baselines
scripts/size/measure-size.sh --selftest            # guarda do guarda
```

O TSV emitido é `kind<TAB>name<TAB>value`:

| kind | significado |
|---|---|
| `meta` | commit, branch, data, `uname`, `java -version` |
| `module` | cada `kof-*/target/*.jar` (módulo compilado) |
| `dist` | o jar CLI shaded, o jar fino `original-` e um `tar.gz` empacotado |
| `dep` | bytes DESCOMPRIMIDOS por **pacote de topo** dentro do jar shaded, top 20 |
| `hello` | bytes do artefato de um hello-world compilado pelo CLI, por alvo |

Um alvo cujo toolchain está ausente é emitido como `unavailable` (nunca um 0 falso).

## Baseline medida — commit `766e1103b`, Linux 6.17 x86_64, OpenJDK 25.0.3 (01/10/2026)

| Artefato | Bytes | ~ |
|---|---:|---:|
| `dist shaded-cli` | 42 887 089 | 40,9 MiB |
| `dist packed-cli.tar.gz` | 39 213 159 | 37,4 MiB |
| `dist thin-cli` (`original-`) | 486 789 | 475 KiB |
| `module kof-compiler` | 2 696 491 | 2,6 MiB |
| `module kof-c-compiler` | 73 360 | 72 KiB |
| `module kof-runtime` | 61 708 | 60 KiB |
| `module kof-script` | 26 314 | 26 KiB |

O CLI shaded tem ≈ **40,9 MiB**, e o jar fino (as classes do próprio CLI) tem só
≈ **475 KiB** — então ≈ **40,4 MiB** são dependências de terceiros empacotadas.
Atribuição direcional dos pacotes de topo (bytes DESCOMPRIMIDOS, então não somam
o tamanho do jar comprimido): `com` ≈ 47,3 MB e `org` ≈ 42,7 MB (GraalJS/Truffle
e afins), `dev` ≈ 8,3 MB (código do próprio KOF), `META-INF` ≈ 0,29 MB.

Bytes do artefato hello-world por alvo (compilado do CLI shaded):

| Alvo | Bytes | ~ |
|---|---:|---:|
| jvm | 2 182 | 2,1 KiB |
| js | 22 186 | 21,7 KiB |
| native (x86-64) | 53 568 | 52,3 KiB |
| native.risc (riscv64) | 138 232 | 135 KiB |
| native.arm (aarch64) | 137 848 | 135 KiB |

## Regras de leitura (evitar conclusões erradas)

- `dep` é **direcional, não exato**: atribui bytes DESCOMPRIMIDOS por pacote de
  topo, então superestima em relação ao jar shaded comprimido e não atribui
  transitivamente. Use-o para ranquear os grandes contribuintes, não para fechar
  contas.
- A diferença entre o jar fino e o shaded são as **dependências empacotadas**; a
  regra consolidadora visada é "capacidade opcional tem custo opcional" (um app
  que não usa PDF paga 0 de PDF) — medida depois, não aqui.
- **Nenhuma redução é autorizada por este documento.** A Fase 1 não muda nada;
  cada ideia de redução/packaging/gate de CI precisa de decisão própria da
  mantenedora.

## Re-auditoria

Regere `scripts/size/measure-size.sh` no tip e rode `compare-size.sh` contra esta
baseline; atualize esta linha no mesmo commit (registro, não opinião congelada).
