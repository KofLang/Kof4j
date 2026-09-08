# Filosofia do Kof

**Última atualização:** 2 de setembro de 2026
**Versão:** 0.2.6-beta (810 testes; 7 targets; `VERSION` 0.2.6-beta)

---

## Princípio Central

> O programador deve escrever a intenção.
> A linguagem e o runtime cuidam da complexidade.

Kof não existe para ser "mais um Java". Kof existe para resolver problemas que Java não resolve bem — ou que resolve apenas com frameworks complexos.

---

## O "Paradigma" da Intenção

> **Aviso honesto:** não é um paradigma de verdade. Não existe em catálogo de
> paradigmas, não tem definição formal e ninguém publicou um paper sobre ele.
> É a **orientação a objetos levada ao extremo**: o código expressa *o que*
> quer acontecer, e a plataforma (linguagem + compilador + runtime + stdlib)
> decide *como* — por target, por plataforma, por convenção.

### A cadeia da intenção

```text
intenção → Kof → compilador → backend
```

O programador escreve a intenção em Kof. O compilador traduz para a IR única.
O backend (JVM, Native, KofJS) decide os mecanismos. Nada de mecanismo vaza
para cima da linha da intenção.

### O que a intenção parece na prática

| Intenção | Código Kof | O que a plataforma decide |
|----------|-----------|---------------------------|
| "roda isso em paralelo" | `spawn processar()` | JVM: virtual threads; Native: pthread (CONC001 fechado) |
| "responda /users/:id" | `app.get("/users/:id") { ... }` | servidor HTTP próprio, sem servlet container |
| "deserialize isto" | `json.decode<User>(body)` | engine JSON + binding por tipo |
| "mostre uma janela com um botão que soma" | `Window`, `Button("+1", () -> ...)` | KofJS renderiza no webview nativo; JVM/Native são no-ops |
| "esta cor é vermelha" | `Color(255, 0, 0)` | Int de 32 bits; canais por bitwise no compilador |
| "isto é um teste" | `assert(2 + 2 == 4)` | exit code, `kof test`, harness |
| "leia este arquivo" | `File("x.txt")` | IO do backend (JVM/Native/JS) |

Em nenhum desses casos o programador escreve `Thread`, `HttpServer`,
`JsonParser`, `WebView`, `0xAARRGGBB` ou `FileInputStream`.

### Por que é o extremo da OO — não um paradigma novo

A orientação a objetos já diz: objetos respondem a mensagens; o *como* é do
objeto. A intenção radicaliza esse contrato em três saltos:

1. **Do objeto para a linguagem** — não é só o objeto que esconde o como; a
   *linguagem* esconde infraestrutura inteira (concorrência, HTTP, IO, UI).
2. **Do runtime para o compilador** — parte do "como" é decidida em
   compile-time (canais de cor por bitwise, packing de handles, lambdas com
   capturas como campos+construtor sintéticos).
3. **Do código para a plataforma** — o que não é intenção do programa não
   existe no código. Se é essencial para qualquer programa, pertence à
   stdlib; se é essencial para a linguagem, pertence ao compilador.

### A linha entre intenção e mecanismo

A regra prática: **se um programador precisa conhecer o mecanismo para
escrever a intenção, o design falhou.** Exemplos de vazamento que Kof rejeita:

- `new Thread(...).start()` → rejeitado; escreva `spawn`.
- Anotações + container para HTTP → rejeitado; escreva `app.get(...)`.
- WebView/JavaFX no código de UI → rejeitado; escreva `Window(...)`.
- Conversão manual de cores → rejeitada; escreva `Palette.red`.

### Limites honestos da intenção

A intenção é única, mas o backend nem sempre consegue realizá-la — e isso é
**diagnosticado em compile-time, com código de gap**, não silenciosamente:

- ~~`spawn` no Native → `CONC001`~~ — fechado 31/08 (pthread)
- JSON de objetos no Native → `JSN002`
- estaticidade no Native → no-op documentado
- `kof.ui` no JVM/Native → handles no-ops (a renderização é KofJS)

O contrato: a intenção compila em todos os alvos; o alvo que não consegue
executá-la diz isso na hora, com código e documentação.

### Consequências práticas

- **Anti-padrão:** escrever Java dentro de Kof (`training/anti-patterns/
  java-like-code.md`) — é vazar mecanismo na intenção.
- **Idioma:** representar o domínio, não a implementação acidental
  (`training/idioms/architecture.md`).
- **Multi-target:** o mesmo código é a mesma intenção; mudar de target não
  muda o código (apenas a realização).

---

## Princípios Arquiteturais

### 1. Simplicidade por Padrão

O caso comum deve ser o mais simples possível. Se o programador precisa escrever mais de 3 linhas para algo comum, algo está errado.

```kof
// Kof: simples
main() {
    println("Hello")
}

// Java equivalente:
public class Main {
    public static void main(String[] args) {
        System.out.println("Hello");
    }
}
```

### 2. Zero Boilerplate

Se o compilador pode deduzir algo, o programador não deve precisar escrever.

- Construtores: gerados automaticamente quando possível
- Getters/Setters: não necessários (fields são acessíveis diretamente)
- toString: gerado para records
- Igualdade: gerada para records

### 3. Runtime Esconde Complexidade

O programador NÃO deve conhecer:
- malloc/free
- Ponteiros
- GC
- ABI
- Calling conventions
- Layout de memória
- Detalhes da JVM
- Detalhes do Native runtime

```kof
var a = new Int[100]  // aloca, inicializa, gerencia
var s = "Hello"       // aloca KofString
```

### 4. Mesmo Código, Múltiplos Targets

O mesmo programa Kof deve funcionar semanticamente em JVM e Native. O programador não deve precisar alterar seu código para mudar de target.

### 5. Memória é Responsabilidade do Runtime

O programador NÃO deve precisar:
- Liberar memória manualmente
- Gerenciar ownership
- Evitar memory leaks
- Conhecer o ciclo de vida dos objetos

O runtime deve resolver isso automaticamente.

### 6. Compilador Elimina Classes de Problemas

Erros que podem ser detectados em compile-time NÃO devem existir em runtime:
- Tipos incompatíveis
- Métodos inexistentes
- Campos inexistentes
- Quantidade errada de argumentos

### 7. Convenção > Configuração

Se algo pode ser resolvido por convenção, não precisa de configuração.

```kof
// Por convenção, main() é o ponto de entrada
main() {
    // ...
}

// Por convenção, o nome do arquivo define o módulo
```

### 8. Segurança de Tipos em Compile-time

Erros de tipo devem ser capturados antes da execução. O compilador deve ser rigoroso.

### 9. APIs Pequenas

Menos é mais. Uma API com 5 métodos úteis é melhor que uma com 50 métodos dos quais 40 são raramente usados.

### 10. Linguagem Resolve, Framework Não

Se a linguagem pode resolver um problema diretamente, não crie um framework para isso.

| Problema | Solução Framework | Solução Kof |
|----------|------------------|-------------|
| HTTP routing | Spring MVC | ✅ `app.get("/users") { ... }` (implementado — `web.app()` no JVM) |
| Validação | Bean Validation | ✅ `kof.validation` (13 predicados, 3 targets) — sintaxe `name: String required` é **proposta futura** |
| Serialização | Jackson | ✅ `json.encode/decode` (3 targets) |
| Configuração | application.properties | ✅ `config.int("server.port", 8080)` — bloco `config { port = 8080 }` é **proposta futura** |

### 11. Não Copiar o Java

Kof não deve copiar features do Java apenas porque elas existem. Cada feature deve ser questionada:

- "Isso resolve um problema real?"
- "Existe uma forma mais simples?"
- "A complexidade vale a pena?"

### 12. Não Exigir Infraestrutura para Recursos Básicos

Criar um servidor HTTP não deve exigir:
- Spring Boot
- Tomcat
- Servlet container
- XML de configuração
- Annotations

Deveria ser algo como:
```kof
var app = web.app()
app.get("/users") { return users.all() }
```
(implementado no JVM — ver `docs/stdlib-web.md`)

### 13. Performance Sem Sacrificar Ergonomia

A linguagem deve ser ergonômica E performática. Não deve ser necessário escrever código feio para ter performance.

### 14. Native e JVM Compartilham Semântica

A semântica da linguagem é única. Os backends implementam essa semântica de forma diferente, mas o comportamento observável deve ser o mesmo.

---

## O que Kof NÃO é

- Não é Java com outra sintaxe
- Não é Kotlin 2
- Não é um transpiler para Java
- Não é um interpretador (o compilador é real: bytecode/ELF/ESM; o `KofInterpreter` do target KofScript executa a MESMA IR otimizada do frontend — paridade por construção, não um disfarce)
- Não é uma linguagem para scripts (embora possa ser usada para isso)
- Não é uma linguagem para web (embora possa ser usada para isso)

Kof é uma linguagem de programação geral, compilada, com múltiplos backends.

---

## Distribuição

Kof não é "um projeto Java que você monta" — é **uma linguagem que você
instala**. O pacote oficial inclui compilador, CLI, runtime, stdlib,
tooling, editor support e um OpenJDK 21 embutido (Temurin 21, `release.yml`
com 2 jobs — `test-and-bump` → `package-and-release` — por plataforma
linux-x86_64/macos-arm64/windows-x86_64, `scripts/package.sh` PASS). A
instalação não depende de Java externo, `JAVA_HOME` ou SDKMAN. Build
`mvn test` 810 (793+8+5+4), golden 16/16, integration 9/9.

O usuário que instala o Kof recebe tudo o que precisa para desenvolver,
compilar, executar e usar o tooling da linguagem (18 comandos:
`kof build/run/serve/check/test/script/repl/c/fmt/config/bench/profile/inspect/debug/info/lsp/install/version`).

---

## Kof + LLM

> **Human First, LLM Friendly by Consequence.**

Kof não é projetada "para IA". A consistência do design (menos ceremony,
menos arquivos, menos abstrações artificiais, menos configuração, mais
intenção) faz com que humanos e LLMs entendam a mesma linguagem da mesma
forma. O que é explícito para uma pessoa é explícito para um modelo — e
vice-versa.

O diretório `training/` é parte oficial dessa estratégia: um corpus
estruturado para que modelos produzam Kof idiomático.

---

## Visão de Futuro

Kof deve evoluir para ser uma plataforma onde:

1. **Backend APIs** são construídas na linguagem, não em frameworks
2. **Persistência** é parte da linguagem, não de um ORM
3. **Segurança** é parte da linguagem, não de um framework
4. **Observabilidade** é parte da linguagem, não de bibliotecas
5. **Concorrência** é parte da linguagem, não de APIs

O objetivo é que a complexidade que hoje vive em Spring, Hibernate, e dezenas de outras bibliotecas, seja resolvida pelo compilador e runtime do Kof.

E, no limite, **Kof escrito em Kof** — não como demonstração, mas como
evolução arquitetural real.
