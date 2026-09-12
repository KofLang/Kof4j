# 24 — Build Tools

> **Status: parcial — Maven/Gradle via `kof build` + `kof test` (0.3.22-beta)**
>
> `kof build`/`kof test` são os build tools nativos (); integração Maven/Gradle como plugin externo ainda é visão planejada, mas coexistência `src/main/java` + `src/main/kof` já funciona para gerar `.class` interoperáveis.

## Maven

### Estrutura de projeto

```
meu-projeto/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/          ← código Java
│   │   └── kof/           ← código Kof
│   └── test/
│       ├── java/
│       └── kof/
```

### pom.xml

```xml
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.exemplo</groupId>
    <artifactId>meu-app</artifactId>
    <version>1.0-SNAPSHOT</version>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
            <version>3.2.0</version>
        </dependency>
    </dependencies>
</project>
```

### Compilando

Não existe plugin Maven/Gradle de Kof ainda (visão planejada). O caminho
nativo é:

```bash
kof build src/main/kof --target jvm --output out/classes
```

O output cai no classpath ao lado dos `.class` do Java — o Maven/Gradle
continuem cuidando do Java, e o `kof build` cuida do Kof. `kof test`
roda a suíte `test "nome" { assert(...) }` nos targets jvm/native/js.

## Gradle

Mesma estratégia que no Maven: o build do Java segue no Gradle; o código
Kof compila com `kof build` para o mesmo classpath. Um plugin Gradle
(`dev.kof.kof`) é visão planejada, não existe hoje.

## Coexistência com Java

Kof e Java podem coexistir no mesmo projeto:

```
src/
├── main/
│   ├── java/
│   │   └── com/exemplo/
│   │       └── legacy/
│   │           └── OldService.java
│   └── kof/
│       └── com/exemplo/
│           └── new/
│               └── NewService.kf
```

O compilador Kof gera `.class` que o Java pode chamar normalmente.

## Próximo passo

[Spring →](25-spring.md)
