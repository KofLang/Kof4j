# Módulos, Pacotes e Imports

**Status:** Stable (exceto onde etiquetado) · **Evidência:** `Parser.parsePackage`/`parseImports`, `CompilerImports.java`, `MemberResolver.qualifyViaImports`, `CompilerTypes.java:48-138`

---

## 1. Unidade de compilação

A unidade de compilação é **um arquivo `.kf`** (ou `.ks` no KofScript). O
parser produz um `CompilationUnitNode(packageName, imports, declarations)`.

- **`package` é opcional**; ausente → pacote `""` (pacote "default", classe vai
  para `Default/Main`).
- **`import` vem depois de `package`, antes das declarações.**
- **Não há** `module` keyword, nem `namespace`, nem arquivo de módulo separado.
  O "módulo" do Kof é o **diretório raiz** passado ao compilador (module root),
  usado para expandir imports de diretório.

---

## 2. Pacotes

`ebnf
package-declaration = "package" , identifier , { "." , identifier } , [ ";" ]
`

`kof
package com.dev.app
`

- Nome pontuado, semântica de namespace (mapeia para pacote JVM).
- **Não há** diretivas de visibilidade de pacote além de `public`/`private`/
  `protected` por membro.

---

## 3. Imports

`ebnf
import-declaration = "import" , ( "*" | import-path ) , [ ";" ]
import-path = identifier , { "." , identifier } , [ ".*" ]
`

`kof
import com.dev.NodeUI          // classe específica
import com.dev.*               // wildcard de pacote
import kof.json                // módulo stdlib
`

### 3.1 O que um import faz

1. **Qualificação de nome simples**: `qualifyViaImports` resolve um nome simples
   (sem `.`/`<`/`[]`) pelo **primeiro** import não-wildcard que termina em
   `.<nome>` (`MemberResolver.qualifyViaImports`).
   - **Wildcards `import a.b.*` NÃO qualificam nomes simples** (:57) — só
     trazem as declarações para o escopo (item 3.2).
   - Import **ambíguo** (dois imports com o mesmo nome simples) → **não chuta**:
     o tipo é preservado sem qualificação (`simpleNamePackage` retorna `null`,
     `CompilerTypes.java:102-122`). **Stable** (regra anti-chute do bug 32).
2. **Type-arguments são qualificados recursivamente** (`qualifyDeep`,
   `CompilerTypes.java:48-94`): `List<NodeUI>` com `import com.dev.NodeUI`
   resolve para `List<com.dev.NodeUI>` (bug 32). Nome simples do arg resolvido
   por imports → classes do módulo.
3. **Expansão de diretório** (`CompilerImports.expandKofImports`,
   chamada em `CompilerDriver.java`, método `compileSources`): `import a.b` onde `a/b/` é diretório no module
   root **puxa todos os `.kf` daquele diretório** para a unidade (fixpoint ≤256
   rodadas). É como arquivos separados do mesmo pacote se enxergam.

### 3.2 Imports transitivos e colisões

- Importar um pacote que importa outro **re-expõe** as declarações (import
  transitivo não é colisão — PKG005 corrigido).
- Dois `main()` em arquivos do mesmo módulo → **`PKG002`** (*probe*: "module
  has 2 main() functions; expected exactly one").

---

## 4. Resolução de nomes (ordem)

Para um identificador `x` (ver [type-system.md](type-system.md) §6):

`text
escopo local (cadeia de pais)
  → args em main
  → constante de enum não-qualificada
  → membro da classe corrente (resolveInHierarchy BFS)
  → tipos/classes do módulo (knownClasses, fase preDeclareType)
  → imports (qualifyViaImports)
  → namespaces builtin (json, process, KofWeb, …)
  → senão SEM011
`

- **Não há** `import static`, nem renomeação (`import a.b as C`), nem
  `export`/re-export.
- **Não há** resolução por wildcard de pacote para nome simples (item 3.1).

---

## 5. Standard library (`kof.*`)

A stdlib é um conjunto de **namespaces** acessíveis por `import kof.<área>` e
usados via objeto global (`json.encode`, `http.get`, …). Os namespaces
reconhecidos pelo analisador (`SemExpressionTyper`/`MemberResolver`, lista de namespaces builtin):

`text
json  process  KofWeb  KofConfig  KofCache  KofGpu  KofDb  KofOrm
KofLog  KofSecurity  KofValidation  KofObservability  KofHttp  KofMq
KofTime  KofScheduler  KofTetris  KofMedia  KofUi  Theme
`

Cada área tem documento próprio em `docs/stdlib*.md` (não duplicados aqui). A
**linguagem** define que esses nomes existem e como resolvem; a **biblioteca**
define as assinaturas. **Experimental** como superfície (muda entre versões).

---

## 6. Interop com o target

- **JVM**: tipos Java são acessíveis por nome qualificado (`java.util.Date`)
  quando no classpath (`ExternalClasspath.resolveMethod`, `:1535-1549`).
  **Target-specific.**
- **Native/JS**: não há interop com tipos do host da mesma forma. **Unspecified.**
- **Annotations** (`@Name`, `@JsonFormat`) são metadados de interop emitidos no
  bytecode JVM. **Target-specific** (só JVM preserva).

---

## 7. Arquivos e extensão

- **`.kf`** — Kof (compilável para todos os targets).
- **`.ks`** — KofScript (dialeto de script com `let` top-level; **linguagem
  separada**, não parte desta especificação — ver [../…](#) / `kof-script`).
- **Não há** header/source separado, nem `.kfi`, nem pré-processador.
