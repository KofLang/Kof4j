[English](portukof.md) | [Português](portukof.pt_BR.md)

# PortuKof (`.ptkf`) — Superfície Portuguesa do Kof

last: F7.3 formatter AST dirigido por perfil landado 08/10 (`.ptkf` formata A PARTIR da AST pela ponte única `SurfaceNames` — sem transpile, sem fallback-por-tokens-como-primado, round-trip AST-igual, idempotente, Kof byte-idêntico; `PortuKofFormatterProfileE2ETest` 18/18 + CLI/LSP `PortuKofToolingE2ETest` 8/8) | F7.2 renderização de superfície + imports `.ptkf` landada 07/10 (completion/hover/signature/symbols mostram PT através da ponte única `lang/SurfaceNames` sobre símbolos canônicos — sem regex, sem replace textual, sem segundo engine; `CompilerImports` resolve `.ptkf` irmão/pacote/misto Kof↔PortuKof por extensão; `PortuKofToolingSurfaceE2ETest` 20/20) | F7.1 paridade de tooling landada 07/10 (`.ptkf` cidadão de primeira classe na análise LSP + paridade de contrato de máquina + anti-transpile do formatter + manifestos de editor; `PortuKofToolingE2ETest` 7/7; gate `check_portukof_tooling.sh` rc=0) | F6 diagnósticos localizados landados 07/10 (164 códigos / 278 variantes, PT 100%, paridade de placeholders travada; `PortuKofDiagnosticsTest` 9/9) | U3 métodos receiver-aware landada 07/10 (17 categorias; `tamanho`=length/size; `PortuKofMethodsE2ETest` 8/8) | U2 paridade stdlib landada 07/10 (308 membros / 36 namespaces; `PortuKofStdlibE2ETest` 4/4) | U1 substrato landado 07/10 (`LanguageProfile` + Lexer profile-aware + autodescoberta `.ptkf`; `PortuKofSurfaceE2ETest` 11/11)
location: docs/languages/portukof.pt_BR.md
state: active

intent: portukof-surface-specification

constraint:
* one-compiler
* one-ast
* one-ir
* one-runtime
* one-stdlib
* one-backends
* full-frontend-parity (absoluta: sem subconjunto, sem stub, Q7)
* strings-nunca-traduzidas
* identificadores-de-usuario-nunca-traduzidos
* comentarios-nunca-traduzidos
* keywords-sem-acentos

---

## 1. O que é o PortuKof

PortuKof é uma **superfície humana em português brasileiro** para a linguagem Kof (`D-PORTUKOF`). Ele **NÃO** é: fork do compilador; segundo compilador; segundo AST/IR/runtime/stdlib; transpiler paralelo; parser independente; subconjunto do Kof.

```text
fonte PortuKof (.ptkf)
        ↓
 LanguageProfile (forFileName = gate por extensão)
        ↓
  lexer/parser profile-aware  →  AST canônica (TokenType canonico)
        ↓
  semântica Kof (pipeline única)  →  IR Kof → runtime Kof → backends Kof
```

**Regra de ouro:** PortuKof muda a *superfície humana*, nunca a *semântica*. Tudo abaixo da fronteira de parse acredita estar compilando Kof canônico.

---

## 2. Autodescoberta

`foo.ptkf` é identificado como PortuKof pela **extensão** (`LanguageProfile.forFileName`) — a extensão é a autoridade única e primária, nunca o conteúdo. Nenhuma flag é necessária:

```bash
kof run programa.ptkf
kof build programa.ptkf
kof check programa.ptkf
kof fmt programa.ptkf
kof lsp                     # buffers .ptkf são analisados como PortuKof
```

Extensões: `.ptkf` → PortuKof (`pt-BR`); `.kf`, `.kof` → Kof canônico (`en`).

---

## 3. Keywords (64 pares, bijetivos, só ASCII)

A tabela completa é gerada e travada por bijetividade + sem acentos por `scripts/check_portukof_parity.sh`. Subconjunto representativo (fonte: `lang/PortuKofVocabulary.PAIRS`):

| PortuKof | Kof | PortuKof | Kof |
|---|---|---|---|
| `classe` | `class` | `registro` | `record` |
| `interface` | `interface` | `enumeracao` | `enum` |
| `entidade` | `entity` | `externo` | `extern` |
| `pacote` | `package` | `importa` | `import` |
| `estende` | `extends` | `implementa` | `implements` |
| `publico` | `public` | `privado` | `private` |
| `protegido` | `protected` | `estatico` | `static` |
| `abstrato` | `abstract` | `final` | `final` |
| `vazio` | `void` | `novo` | `new` |
| `este` | `this` | `super` | `super` |
| `retorna` | `return` | `lanca` | `throw` |
| `se` | `if` | `senao` | `else` |
| `para` | `for` | `enquanto` | `while` |
| `faca` | `do` | `escolha` | `switch` |
| `caso` | `case` | `padrao` | `default` |
| `sair` | `break` | `segue` | `continue` |
| `tenta` | `try` | `pegar` | `catch` |
| `porFim` | `finally` | `afirme` | `assert` |
| `gera` | `spawn` | `aguarda` | `await` |
| `instanciaDe` | `instanceof` | `como` | `as` |
| `var` | `var` | `val` | `val` |
| `logico` | `Bool` | `inteiro` | `Int` |
| `texto` | `String` | `duplo` | `Double` |
| `verdadeiro` | `true` | `falso` | `false` |
| `nulo` | `null` | `sobrescreve` | `override` |

`funcao` / `fn` / `func` são lexeados para tipos de token não-Kof e então **recusados** pelo parser (Kof declara função por tipo+nome, não por keyword). A entrada `principal() { … }` é normalizada para o `main() { … }` canônico na AST (`lang/PortuKofParity.canonName`).

---

## 4. Builtins & Aliases da Biblioteca Padrão

Builtins (fonte: `lang/PortuKofVocabulary.BUILTINS`):

| PortuKof | Kof | PortuKof | Kof |
|---|---|---|---|
| `escreva` | `print` | `escrevaln` | `println` |
| `leia` | `readLine` | `leiarquivo` | `readFile` |
| `escrevaarquivo` | `writeFile` | `listaDe` | `listOf` |
| `mapaDe` | `mapOf` | `conjuntoDe` | `setOf` |
| `tamanho` | `len` | `agora` | `now` |
| `durma` | `sleep` | `canal` | `channel` |
| `gera` | `spawn` | `aguarda` | `await` |

Namespaces da stdlib (fonte: `lang/PortuKofStdlibMembers.namespaces()`, 36 namespaces; gerados por `scripts/gen_portukof_aliases.py` a partir de um dump em execução do `StdCatalog` real):

| PortuKof | Kof | PortuKof | Kof |
|---|---|---|---|
| `matematica` | `math` | `textos` | `strings` |
| `banco` | `db` | `tempo` | `time` |
| `aleatorio` | `random` | `codificacao` | `encoding` |
| `rede` | `net` | `processo` | `process` |
| `validacao` | `validation` | `autenticar` | `auth` |

Membros de exemplo (canônico → superfície): `math.sqrt`→`matematica.raizQuadrada`, `db.connect`→`banco.conectar`, `db.query`→`banco.consultar`, `http.get`→`http.obter`, `time.sleep`→`tempo.dormir`, `time.now`→`tempo.agora`, `strings.count`→`textos.contar`.

Uma chamada nua como `max(x)` (nome de função do usuário) **nunca** é reescrita — apenas chamadas de membro qualificadas sobre namespaces conhecidos (e as chamadas tipadas receiver-aware de §5) são resolvidas.

---

## 5. Aliases de Método/Campo Receiver-Aware (17 categorias)

Aliases de método dependem do **tipo do receiver** (`PortuKofMethodCategories`, fonte: `lang/PortuKofMethodAliases` gerada dos dispatchers reais), porque o backend nativo só expõe primitivos específicos do alvo:

```text
tamanho  em String / Array   → length     (kof_string_length existe no native)
tamanho  em List / Map / Set → size       (não existe kof_string_size)
```

Uma substituição textual pura na AST seria desonesta cross-target. A resolução usa os MESMOS analisadores semânticos do Kof:

1. um **hook de entrada** em `SemMethodCallTyper` / `MemberCallTyper` / `SemFieldAccessTyper` (tipo do receiver já resolvido) canoniza o nome de dispatch e o registra (as rejeições `SEM025`/`SEM102` veem o método real);
2. `PortuKofMethodSplicer` reescreve a AST para nomes canônicos pós-análise e re-indexa os caches de identidade nó-a-nó.

Aliases de exemplo: `contem`→`contains`, `estaVazio`→`isEmpty`, `comprimento`/`tamanho`→`length`, `mapear`→`map`, campo `nome`→`name`, `caminho`→`path`. Uma classe de usuário retorna categoria `null` — um método de usuário mesmo chamado `tamanho` ou `contem` **nunca** é reescrito (provado por `PortuKofMethodsE2ETest`).

---

## 6. Diagnósticos Localizados (F6)

* **164 códigos / 278 variantes** cobrindo 100% do domínio user-facing `LEX`/`PARSE`/`SEM` (derivados dos pontos de emissão reais por `scripts/gen_portukof_diags.py`, inclusive mensagens dinâmicas modeláveis).
* **Sistema único** — o `code` canônico é a única chave.
* `Diagnostic.message()` é **sempre inglês canônico** (teste/LSP/`--json` intactos). `format()`/`localizedMessage()` produzem a forma PT **somente** para `.ptkf` (por extensão, nunca conteúdo).
* `PortuKofDiagnostics.localize(code, file, message, args)` renderiza PT **somente** quando o template EN, com os MESMOS args, reproduz a mensagem canônica byte-a-byte; senão mantém inglês (nunca tradução aproximada). Paridade de placeholders EN↔PT travada pelo gate.
* Identificadores, literais e trechos de código dentro dos diagnósticos são preservados verbatim (`'João'` nunca é traduzido).

---

## 7. Tooling & Superfície LSP (F7)

`.ptkf` é **cidadão de primeira classe** do tooling existente — a diferença entre Kof e PortuKof é só a superfície linguística. **Não há segundo engine**: a paridade vem da fronteira `LanguageProfile` + a pipeline canônica única (regra de ouro §4).

* **Autoridade da extensão (§6):** `LspProject.fileNameOf` preserva o basename real `.ptkf`, então `LspServer.analyze` dirige a pipeline canônica com o perfil PortuKof. Espelho de projeto e varredura de irmãos incluem `.ptkf`.
* **Contrato de máquina (§16/§17):** o `publishDiagnostics` do LSP e o `--json` usam `Diagnostic.message()` (EN canônico); `code`, `severity`, `range` e `args` estruturados são idênticos entre um programa Kof e seu equivalente PortuKof (provado por `PortuKofToolingE2ETest`). A localização nunca toca a saída de máquina.
* **Ranges de fonte (§29/§30):** as posições vêm de tokens fonte-autoritativos (linha/coluna/offset/length do texto `.ptkf` real) — uma palavra PT mais longa nunca desloca um range.
* **Formatter AST dirigido por perfil (§18–22/§39, F7.3):** o printer de AST parseia `.ptkf` **direto** pelo lexer de perfil — slots estruturais chegam canônicos (`se`→IF "if", `texto`→`string`), slots de nome chegam **verbatim** (identificadores do usuário, `escrevaln`, `tamanho`, imports) — e reimprime cada slot estrutural pela ponte única `lang/SurfaceNames`. O português sai da AST: sem transpile, sem regex, sem replace textual, sem segunda tabela de vocabulário no printer. Erro de parse devolve `null` e CLI/LSP caem no serializador por tokens explícito (superfície byte-a-byte, comportamento F7.1). Comentários reusam a costura existente no texto-fonte (`KofFormatterComments`), então o conteúdo do comentário nunca é traduzido. Round-trip AST-equivalente (`PortuKofParity.normalize`) e idempotente (`F(F(x)) == F(x)`); a saída `.kf` é byte-idêntica ao comportamento pré-F7.3. Prova: `PortuKofFormatterProfileE2ETest` 18/18; integração CLI/LSP `PortuKofToolingE2ETest` 8/8.
* **Manifestos de editor (§6):** VS Code (`VscodeExtensionContent`) e IntelliJ (`KofEditorContent`) registram `.ptkf` sob a MESMA linguagem `Kof`.
* **Renderização de superfície (§33, F7.2):** completion/hover/signature/symbols mostram a superfície PT através de UMA ponte única, `lang/SurfaceNames` (`canônico → superfície`), que delega aos catálogos já gateados (`PortuKofVocabulary`, `PortuKofStdlibMembers`, `PortuKofMethodAliases`). Sem regex, sem `replace` textual, sem lista por ferramenta: `SurfaceNames` devolve identidade quando o perfil é KOF, então o Kof é byte-idêntico. Uma palavra digitada num buffer `.ptkf` é canonizada *pelo perfil* para o mesmo símbolo que o Kof usa e então re-renderizada na grafia de superfície; identificadores de usuário nunca passam pela ponte.
* **Imports (§21–§24, F7.2):** o único resolver `CompilerImports` reconhece `.ptkf` por extensão (a MESMA autoridade `LanguageProfile.forFileName`), parseia o arquivo importado com o perfil dele e normaliza imports de superfície (`importa arquivo.csv` → `file.csv` via `PortuKofParity.canonImport`). Módulos Kof↔PortuKof coexistem: um `.ptkf` pode importar um `.kf` e vice-versa, sem resolver paralelo.

---

## 8. Regras Absolutas

1. Strings nunca são traduzidas (`"principal escreva tamanho"` fica byte-a-byte).
2. Identificadores de usuário nunca são traduzidos (`minhaFuncao`, `tamanhoMeu`, `principalidade`, `max`).
3. Comentários nunca são traduzidos (`// principal chama escreva`).
4. Nenhum segundo engine: a paridade vem do `LanguageProfile` + a pipeline canônica, nunca de uma classe de tooling `PortuKof*` paralela.
5. Nenhuma tradução por IA: cada alias é derivado dos catálogos de execução reais (`StdCatalog`, dispatchers).
6. Sensibilidade a caixa e acentos seguem exatamente o lexer existente — keywords PortuKof são ASCII, sem variantes `função`/`variável`/`senão`.

---

## 9. Matriz de Paridade (uma célula é ✓ apenas com teste executado)

| Recurso | Kof (`.kf`) | PortuKof (`.ptkf`) | Mecanismo / Prova |
|---|---|---|---|
| Descoberta CLI (run/build/check/fmt) | ✓ | ✓ | `KofCliSupport.isKofSource`; `PortuKofSurfaceE2ETest` 11/11 |
| Parse + AST canônica | ✓ | ✓ | `Lexer`/`Parser` 4-args + `PortuKofParity.normalize` |
| Keywords (64, bijetivas, ASCII) | ✓ | ✓ | `PortuKofVocabulary` (gate rc=0) |
| Membros/namespaces stdlib (308/36) | ✓ | ✓ | `PortuKofStdlibMembers`; `PortuKofStdlibE2ETest` 4/4 |
| Métodos receiver-aware (17) | ✓ | ✓ | `PortuKofMethodSplicer`; `PortuKofMethodsE2ETest` 8/8 |
| Diagnósticos humanos (CLI `format`) | ✓ | ✓ (PT) | `PortuKofDiagnostics` 164 códigos; `PortuKofDiagnosticsTest` 9/9 |
| Diagnósticos de máquina (LSP/`--json`) | ✓ (EN) | ✓ (EN) | `d.message()`; `PortuKofToolingE2ETest` 7/7 |
| Análise LSP arquivo-único + modo projeto | ✓ | ✓ | `LspProject.fileNameOf`+mirror; `PortuKofToolingE2ETest` |
| Formatter (AST dirigida por perfil) | ✓ (AST) | ✓ (AST) | `KofFormatter`+`SurfaceNames`; `PortuKofFormatterProfileE2ETest` 18/18; fallback por tokens só em erro de parse |
| Manifestos de editor (VS Code, IntelliJ) | ✓ | ✓ | `EditorIntegrationTest` 22/22 |
| Symbols de documento/workspace (superfície) | ✓ | ✓ | `LspSymbols` ciente de perfil; `PortuKofToolingSurfaceE2ETest` 20/20 |
| Completion de superfície (kw/builtin/stdlib/método/usuário) | ✓ | ✓ | `LanguageProfile`+`SurfaceNames`; `PortuKofToolingSurfaceE2ETest` |
| Hover de superfície (builtin/stdlib/método/usuário) | ✓ | ✓ | categoria receiver-aware (§11); `PortuKofToolingSurfaceE2ETest` |
| Signature help (rótulo de superfície, params canônicos) | ✓ | ✓ | `LspSignatureHelp` ciente de perfil |
| Import `.ptkf` (irmão/pacote/misto) | ✓ | ✓ | `CompilerImports` autoridade de extensão + `PortuKofParity.canonImport` |
| Definition / references / rename (identidade semântica) | core-textual | core-textual | O LSP do Kof é textual por design (sem índice tipado): símbolos do usuário resolvem; desambiguar alias entre receivers = gap do core, declarado (não forjado) |
| Guardas de rename (alias/keyword de superfície) | ✓ | ✓ | recusa ciente de perfil em `LspRename` |
| Semantic tokens | ✗ | ✗ | ausentes no core Kof — ausência honesta (§15) |

**Limitação do core, declarada e não forjada (§18/§15):** definition/references/rename do LSP do Kof são
*textuais* (o servidor não roda parser/índice tipado completo por request — ver `LspSymbols`). Símbolos do
usuário renomeiam/resolvem corretamente em qualquer superfície. Desambiguar uma palavra-alias solta
(`tamanho` = `length` em String vs `size` em List) entre receivers, strings e comentários exige um índice de
símbolos tipado que o core não tem; o PortuKof **não** forja isso com regras textuais. O *compilador* resolve
os aliases pelo tipo do receiver (U3 `PortuKofMethodSplicer`), então o contrato da linguagem é completo; só a
navegação *semântica* do editor herda a limitação do core.

---

## 10. Exemplos

Exemplos oficiais `.ptkf` vivem em `examples/portukof/` e são compilados (zero diagnóstico) por `PortuKofToolingE2ETest`. Só usam o vocabulário dos §3–§5.

```ptkf
// exemplos/portukof/ola_mundo.ptkf
principal() {
    escrevaln("Olá, mundo!")
}
```

```ptkf
// exemplos/portukof/colecoes.ptkf
principal() {
    val xs = listaDe(3, 1, 2)
    val nome = "Kof"
    escrevaln(nome.tamanho())     // String → length
    escrevaln(xs.tamanho())        // List   → size
    se (xs.contem(2)) {
        escrevaln("tem dois")
    }
}
```

---

## 11. Gatees de Verificação

```bash
bash scripts/check_portukof_parity.sh    # lexical + stdlib + métodos + F6 diags 100%
bash scripts/check_portukof_tooling.sh   # F7 tooling first-class + formatter AST por perfil + contrato de máquina
mvn -o -pl kof-compiler,kof-cli -am test -Dtest='PortuKof*'   # prova executada
```
