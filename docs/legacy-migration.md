# LEGACY_MIGRATION.md — Plataforma de Migração de Software Legado

**Status:** EM DESENVOLVIMENTO — Fases A–H implementadas (`kof inspect/decompile/translate/compare/migrate`)
**Escopo:** Fora do Kof 0.0.x
**Data:** 22 de agosto de 2026

---

## 1. Visão

Kof não é apenas uma linguagem para criar software novo.

A visão de longo prazo é uma plataforma capaz de **analisar, recuperar,
traduzir e modernizar sistemas legados para Kof**:

> Preservar a funcionalidade de software legado enquanto modernizamos sua
> implementação para Kof.

A plataforma deve trabalhar tanto com código-fonte disponível quanto com
sistemas onde o código original foi perdido — utilizando bytecode, binários,
metadados, artefatos de build e comportamento observável como fontes de
informação.

**Esta documentação é arquitetura, não promessa de implementação.**
Nenhuma métrica ou capacidade descrita aqui existe no compilador hoje.

---

## 2. Princípio Fundamental

A plataforma não assume `Legacy → Java → Kof`.

Quando possível, utiliza o caminho direto:

```text
Legacy
   ↓
Legacy Semantic IR
   ↓
Kof AST
   ↓
Kof IR
```

Para JVM:

```text
JVM Bytecode
     ↓
Bytecode Analysis
     ↓
Legacy Semantic IR
     ↓
Kof AST
```

Java pode ser uma **origem suportada** (via translator), mas nunca uma
**representação intermediária obrigatória**. Isso evita uma etapa artificial
de geração de Java entre o artefato legado e o Kof.

---

## 3. Componentes Planejados

| Comando | Propósito | Status |
|---------|-----------|--------|
| `kof inspect <input>` | Análise estrutural de `.class`/`.jar`/binários | Planned |
| `kof decompile <input>` | Recuperação de código Kof a partir de artefatos compilados | Planned |
| `kof translate <input>` | Migração de código-fonte (primeiro alvo: Java → Kof) | Planned |
| `kof migrate <input>` | Migração completa com relatório | Planned |
| `kof compare <legacy> <kof>` | Teste diferencial entre sistemas | Planned |

**Nenhum destes comandos existe no CLI atual.** Não documentá-los como
disponíveis até que existam.

### 3.1 `kof inspect` (planejado)

Análise de sistemas existentes. Responsabilidades:
identificar formato, plataforma, versão; analisar dependências; identificar
classes, métodos, interfaces, campos, tipos; identificar chamadas externas,
reflection, carregamento dinâmico, JNI/FFM/native calls; identificar metadata,
debug information, serialization, recursos; estimar recuperabilidade.

Saída conceitual (valores ilustrativos — nenhuma métrica é real sem
implementação e metodologia definidas):

```text
Classes:              1842
Methods:              17391
Reflection:           detected
Native calls:         detected
Debug metadata:       partial

Recoverability:
Types                  HIGH
Control Flow           HIGH
Method Signatures      HIGH
Local Names            LOW
Comments               NONE
```

### 3.2 `kof decompiler` (planejado)

Destinado à recuperação de código Kof a partir de `.class`/`.jar`/`.war`.

Pipeline:

```text
JVM Class File
       ↓
Class File Parser
       ↓
Bytecode IR
       ↓
Control Flow Graph
       ↓
Type Recovery
       ↓
Data Flow Analysis
       ↓
Semantic Recovery
       ↓
Kof AST
       ↓
Kof Source
```

O decompiler prioriza: equivalência semântica, legibilidade, estrutura, tipos,
controle de fluxo, chamadas, herança, interfaces, generics recuperáveis,
exceptions, annotations, metadata.

**Não tenta reconstruir artificialmente o Java original.** O objetivo é
produzir **Kof idiomático equivalente**, não fingir que o fonte original
foi recuperado.

### 3.3 `kof translate` (planejado)

Migração de código-fonte (primeiro alvo: Java → Kof).

```text
Java Source
     ↓
Java Parser
     ↓
Java AST
     ↓
Java Semantic Model
     ↓
Translation IR
     ↓
Kof AST
     ↓
Kof Source
```

O translator **não funciona por substituição textual** (`public → ...`).
Ele compreende a estrutura semântica do programa.

Suporte progressivo planejado: classes, interfaces, inheritance, generics,
overloads, constructors, exceptions, annotations, records, enums, lambdas,
nested classes, anonymous classes, static initialization, access modifiers,
Java standard library, chamadas de bibliotecas externas.

---

## 4. Relação com o Compilador Kof

A plataforma não duplica componentes existentes. Reutiliza:
Kof Lexer, Kof Parser, Kof AST, Kof Type System, Kof Semantic Model,
Kof IR, Kof Backend.

```text
              ┌───────────────┐
              │ Kof Source    │
              └───────┬───────┘
                      ↓
                 Kof Frontend
                      │
Legacy ───────────────┤
                      │
Java ─────────────────┤
                      │
JVM Bytecode ─────────┤
                      ↓
              Legacy Semantic IR
                      ↓
                   Kof AST
                      ↓
                  Kof Compiler
                      ↓
                  Kof IR
                      ↓
               JVM / Native
```

A ferramenta de migração é uma **extensão natural do compilador**,
não um segundo compilador independente.

---

## 5. Informação Irrecuperável

Compilação é, em muitos casos, uma transformação com perda de informação.
Após `Source → Compiler → Bytecode` podem desaparecer:

- comentários;
- nomes locais;
- estrutura sintática original;
- formatação;
- certas informações genéricas;
- intenção do programador;
- abstrações eliminadas pelo compilador;
- informações não presentes no bytecode.

> **Decompilação não é recuperação do fonte original.**
> É recuperação de comportamento e estrutura a partir das informações disponíveis.

A documentação deve estabelecer isto explicitamente, e a plataforma deve
**tornar explícito aquilo que não pode ser recuperado** em vez de inventar.

---

## 6. Segurança e Legalidade

A plataforma é uma **ferramenta de engenharia de software**. O usuário deve
possuir autorização e direitos adequados sobre o software analisado.

A plataforma não promete contornar: DRM, proteção contra cópia, controles de
acesso, mecanismos de segurança, licenciamento.

Foco: preservação, interoperabilidade e modernização autorizada.

---

## 7. Formatos Futuros

A arquitetura prepara adaptadores de formatos além da JVM:

```text
Source / Binary
       ↓
Legacy Adapter
       ↓
Legacy Semantic IR
       ↓
Kof
```

Exemplos potenciais (NÃO implementar no início): COBOL, PL/I, Assembly,
binários legados, bytecode proprietário, VMs customizadas.

O objetivo é impedir que o projeto fique conceitualmente preso à JVM.

---

## 8. Ordem de Implementação

Não começar tentando suportar todos os sistemas legados.

```text
Fase A  JVM Inspection          (.class/.jar + análise estrutural)
Fase B  JVM Bytecode IR         (Class File → Bytecode IR)
Fase C  Control Flow Recovery   (basic blocks, branches, loops, switches, exception regions)
Fase D  Type Recovery           (primitives, references, arrays, generics, inheritance)
Fase E  Kof Decompiler          (gerar Kof source)
Fase F  Java Translator         (Java Source → Kof)
Fase G  Differential Testing    (Legacy vs Kof)
Fase H  Migration Reports       (relatórios completos)
Fase I  Additional Frontends    (COBOL, PL/I, Assembly, formatos proprietários)
```

Antes de implementar: definir a arquitetura, validar com protótipos pequenos,
e só então transformar os protótipos em componentes oficiais.

---

## 9. Critério de Sucesso

A iniciativa é bem-sucedida quando um sistema legado real produz:

```text
Legacy System
      ↓
Analysis
      ↓
Recoverable Semantics
      ↓
Kof Implementation
      ↓
Behavioral Verification
      ↓
Modern Deployment
```

com: rastreabilidade, diagnostics, relatório de limitações, testes
diferenciais, revisão humana, código Kof legível, compilação nativa/JVM,
comportamento compatível dentro do escopo definido.

**Pergunta central:**

> Estamos recuperando comportamento real ou apenas fabricando código que
> parece plausível?

Se a ferramenta não consegue distinguir essas duas coisas, a migração não é
confiável.

---

## 10. Documentação Relacionada

- `DECOMPILER.md` — recuperação de código a partir de artefatos compilados
- `TRANSLATOR.md` — migração de código-fonte Java → Kof
- `LEGACY_IR.md` — representação intermediária de sistemas legados
- `DIFFERENTIAL_TESTING.md` — validação comportamental de migrações