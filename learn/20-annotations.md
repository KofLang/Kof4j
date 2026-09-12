# 20 — Annotations

> **Status: implementado (JVM/KofJS) — 0.3.22-beta**
>
> O parser aceita `@Name` e `@Name(valor | key = valor, ...)` em classes,
> records, interfaces, entities, campos, métodos, construtores, funções,
> componentes de record e parâmetros. O backend JVM emite as annotations
> no bytecode; Native ignora metadados.

## O que são annotations

Annotations são metadados que podem ser adicionados a classes, métodos, campos e parâmetros. No Kof elas existem para **interoperação** — quando um target externo (Android, framework JVM) exige metadata no bytecode. O código idiomático Kof continua preferindo intenção explícita (`app.get(...)`, `entity { ... }`) a annotations+container.

## Annotations em Kof

```kf
@Entity
class User {
    @Id
    UUID id

    @Column("user_name")
    String name
}
```

## Annotations com parâmetros

```kf
@GetMapping("/users/{id}")
User findUser(@PathVariable UUID id) {
    // ...
}
```

Formas aceitas:

| Forma | Exemplo |
|-------|---------|
| simples | `@Override` |
| valor único (vai para `value`) | `@Column("user_name")` |
| pares `key = value` | `@JsonFormat(pattern = "yyyy")` |
| array de literais | `@Roles({"admin", "dev"})` |
| qualificada por pacote | `@androidx.annotation.NonNull` |

Valores precisam ser **constantes em compile-time**: literais `String`, `Int`, `Long`, `Float`, `Double`, `Bool`, `Char`, `null`, ou arrays `{...}` desses literais. Identificadores não constantes viram diagnóstico `ANNOT001` — nunca um valor silenciosamente errado.

## O que o compilador gera no bytecode

- `RuntimeVisibleAnnotations` / `RuntimeInvisibleAnnotations`
- Anotações em parâmetros (`RuntimeVisible/InvisibleParameterAnnotations`)
- Anotações em campos

A retenção é decidida por tabela: `@Override` e `@SuppressWarnings` e os pacotes
de metadata (`androidx.annotation.*`, `javax.annotation.*`,
`org.jetbrains.annotations.*`, `edu.umd.cs.findbugs.annotations.*`) são
emitidos como **invisíveis** (`RuntimeInvisible`); todo o resto — incluindo
`@Deprecated`, `@FunctionalInterface` e `@SafeVarargs` — vai como **visível**
(`RuntimeVisible`). É uma escolha conservadora para frameworks que leem as
annotations em runtime (JUnit, Android).

## Resolução do nome

Nomes qualificados (`androidx.annotation.NonNull`) vão direto para o bytecode. Nomes simples usam os imports do arquivo (`import androidx.annotation.NonNull` torna `@NonNull` resolvível) e os embutidos de `java.lang`.

## Interoperabilidade com frameworks Java

Annotations funcionam normalmente com:
- Spring (`@Service`, `@Autowired`, `@RestController`)
- JPA (`@Entity`, `@Table`, `@Column`)
- Jackson (`@JsonProperty`, `@JsonIgnore`)
- JUnit (`@Test`, `@BeforeEach`)
- Android (`@Override`, `@NonNull`, ciclo de vida via superclasses)

O Spring enxerga `@Service` normalmente porque a annotation está no bytecode.

## Limitações conhecidas

- Valores enum ou `Class<?>` ainda não suportados (`ANNOT001`).
- O Native ignora annotations (metadado não tem semântica executável lá).

## Próximo passo

[Java Interoperability →](21-java-interoperability.md)
