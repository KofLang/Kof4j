# Licenciamento do Kof

**Última atualização:** 2 de setembro de 2026
**Versão:** 0.2.6-beta (810 testes; 7 targets; free-list + pthread spawn + FP XMM)

---

## 1. Código-fonte do Kof

O código-fonte do projeto Kof — incluindo compilador, ferramentas, backends e demais componentes do repositório — é distribuído sob a **GNU General Public License v3.0** (GPLv3).

Isso significa que qualquer pessoa pode usar, modificar e distribuir o código-fonte do Kof, desde que respeite os termos da GPLv3.

O arquivo `LICENSE` na raiz do repositório contém o texto completo da GPLv3.

---

## 2. JDK embutido na distribuição oficial (0.2.6-beta, JDK 21)

O pacote oficial do Kof embarca um **OpenJDK Eclipse Temurin 21** (binários da
Adoptium, Tooling API Level 21), distribuído sob a **GPLv2 com Classpath Exception**. O JDK
embutido é um componente separado do código-fonte do Kof, empacotado apenas
na distribuição (não no repositório), e mantém sua própria licença.

O Kof não modifica o JDK embutido; o launcher (`bin/kof`/`bin/kof.bat`, Windows SIGPIPE fix 27/08) apenas o localiza
e executa. `scripts/package.sh` gera layout dist + tar.gz/zip + SHA256SUMS, `release.yml` usa 2 jobs (`test-and-bump` exporta o SHA do commit de bump → `package-and-release` checkeia o commit de bump + sanity check de versão) com releases por plataforma (linux-x86_64/macos-arm64/windows-x86_64).

---

## 2.1 Compilador (0.2.6-beta)

O compilador Kof (módulos `kof-compiler` 793 testes + `kof-script` 8 + `kof-c-compiler` 5 = 806, `VERSION` 0.2.6-beta) é GPLv3.

Ele contém:
- Lexer / Parser (`case String s` + `Point(x,y)` + `String?`)
- AST / `Type.java` (`String?` nullable)
- Sistema de tipos / SymbolTable
- Análise semântica (`CompilerDriver.java:243` import `a.b.C` fix)
- IR (backend-agnóstica + `KofDebugInfo`)
- Backends: `JvmBackend` (ASM V21 + web ws/sse + cache + http retry/circuit), `NativeBackend` (x86_64 free-list + pthread spawn + FP XMM; `native.risc`/`native.arm` toolchain + qemu), `JsBackend` (GraalJS + `Java HttpClient` interop), `KofScript` (`let`→`KofScriptGlobals`), `KofCcompiler` (`kof_c`), `KofFormatter` (`kof fmt`)
- Geração de código + `Optimizer` (constant folding etc.)

Usar o compilador Kof para compilar seu código NÃO torna seu código GPLv3.

---

## 3. Runtime

O runtime do Kof possui duas implementações:

### Runtime JVM

O backend JVM delega para as facilities da JVM (java.lang.String, arrays nativos, GC, etc.). O runtime JVM é a própria JVM — não há código Kof específico sendo incorporado ao executável.

### Runtime Nativo

O backend Nativo gera funções de runtime em assembly durante a compilação (0.2.6-beta: free-list `kof_free_head` com reuso `mmap` + `spawn`/`await` via `pthread_create`/`pthread_join` com allocator thread-safe (futex) + FP real em XMM + JSON objetos/arrays). Essas funções são:

- `kof_alloc` / `kof_free_head` — alocação com reuso `mmap` (GC mark-sweep pendente)
- `kof_print`, `kof_println`, `kof_print_int`, `kof_int_to_string` — saída
- `kof_string_*`, `kof_array_*`, `kof_list_*` (`map/filter/reduce`), `kof_map_*` — coleções
- JSON objetos/records + arrays `Int/Long/Bool/String/Double` (31/08)
- trampoline de `pthread_create` + `pthread_join` (spawn/await, 31/08)
- `kof_cache_*` (get/set/ttl/delete/clear, 30/08)
- `kof_db_mysql_scramble` — MySQL auth scramble SHA-1 (wire protocol WIP, 31/08)
- `kof_panic`, `kof_null_error`, `kof_bounds_error` — tratamento de erros

**Importante:** Essas funções são **geradas pelo compilador** durante o processo de compilação. Elas não são distribuídas como uma biblioteca pré-compilada. São incorporadas ao executável final como parte do processo de compilação.

Sob a GPLv3, o output de um compilador NÃO é automaticamente GPLv3. As funções de runtime são geradas pelo compilador e fazem parte do output da compilação. Portanto, elas NÃO são automaticamente GPLv3.

---

## 4. Standard Library

Atualmente não existe uma standard library separada. As operações de string e array são implementadas como funções de runtime geradas pelo compilador.

No futuro, se uma standard library for criada, ela deverá ser licenciada separadamente se for necessário permitir software proprietário.

---

## 5. Código escrito pelos usuários

Programas escritos em Kof pertencem aos seus respectivos autores.

**Escrever um programa em Kof NÃO transforma automaticamente esse programa em código GPLv3.**

O autor do programa Kof mantém o direito de escolher a licença do próprio software.

---

## 6. Software proprietário

Software proprietário escrito em Kof é permitido.

O autor pode:
- Manter o código-fonte fechado
- Distribuir apenas o binário
- Usar qualquer licença compatível com as dependências

Desde que o software não esteja incorporando componentes que imponham outra obrigação incompatível.

---

## 7. Distribuição de binários

### Target JVM

O executável JVM (`.class` ou `.jar`) contém bytecode JVM. O runtime é a própria JVM. Não há código Kof sendo incorporado.

### Target Nativo

O executável nativo contém:
- Código do usuário compilado
- Funções de runtime geradas pelo compilador
- Assembly gerado

As funções de runtime são geradas pelo compilador e são parte do processo de compilação. Elas NÃO são distribuídas como uma biblioteca separada.

---

## 8. Dependências de terceiros

O compilador Kof usa as seguintes dependências:

- **ASM** (org.objectweb.asm) — para geração de bytecode JVM

O ASM é usado apenas pelo backend JVM e não é incorporado ao executável final do target Nativo.

Programas escritos em Kof podem ter suas próprias dependências, que devem ser licenciadas de acordo com os termos de cada dependência.

---

## 9. O que a GPLv3 do Kof cobre

A GPLv3 do Kof cobre:
- Código-fonte do compilador
- Ferramentas de linha de comando
- Backends
- Documentação do projeto
- Testes

---

## 10. O que ela NÃO cobre

A GPLv3 do Kof NÃO cobre:
- Programas escritos em Kof pelos usuários
- Software produzido com o compilador Kof
- Binários gerados para target JVM ou Nativo

---

## 11. Exemplos práticos

### Exemplo 1: Empresa proprietária

```
empresa/
  sistema.kf
  main.kf
```

A empresa pode manter o código proprietário. Usar o compilador Kof não obriga a empresa a abrir seu código-fonte.

### Exemplo 2: Projeto open source

O autor pode escolher GPL, MIT, Apache-2.0, ou qualquer outra licença, respeitando as licenças das dependências que incorporar ao seu software.

### Exemplo 3: Aplicação comercial fechada

Pode ser proprietária, desde que a aplicação não esteja incorporando componentes que imponham outra obrigação incompatível com a licença escolhida.

### Exemplo 4: Biblioteca Kof

Se alguém criar uma biblioteca em Kof e quiser distribuí-la como open source, pode escolher GPL, MIT, Apache-2.0, etc.

Se quiser distribuí-la como proprietária, pode fazê-lo.

---

## 12. Analogia com compiladores tradicionais

Assim como:
- Programas compilados com GCC não são automaticamente GPLv3
- Programas compilados com Clang não são automaticamente Apache 2.0
- Programas compilados com javac não são automaticamente GPL

Programas compilados com Kof NÃO são automaticamente GPLv3.

O compilador é uma ferramenta. O código produzido com a ferramenta pertence ao autor.

---

## 13. Pontos que requerem atenção

1. **Dependências de terceiros**: Se o software Kof incorporar bibliotecas de terceiros com licenças específicas, o autor deve respeitar essas licenças.

2. **Runtime nativo**: As funções de runtime são geradas pelo compilador e incorporadas ao executável. Elas são minimalistas e não constituem uma obra separada significativa.

3. **Futuro**: Se uma standard library for criada e distribuída separadamente, ela poderá ter uma licença diferente. Isso será documentado explicitamente.

---

## 14. Contato

Para dúvidas sobre licenciamento, consulte o repositório do projeto ou abra uma issue.