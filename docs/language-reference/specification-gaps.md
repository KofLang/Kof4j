// Specification Gaps — Kof 0.3.0-beta

Este documento lista gaps conhecidos na linguagem/Compilador Kof 
(atualizado em 08/09/2026). Gaps abertos são acompanhados na roadmap 
e podem bloquear ou limitar funcionalidades em algum target.

## Gap conventions

- **Códigos**: prefixes curtos como `R6`, `HW001`, `CONC001`, etc.
- **Status**: `aberto`, `fechado`, `parcial`
- **Targets**: `JVM`, `Native`, `JS`
- **Referência**: cada gap deve ter issues/referências nos testes e docs

---

## Gap R6 — putfield de campos `Int`

**Status**: aberto  
**Plataforma**: JVM, Native, JS  
**Desde**: 0.0.4-alpha  
**Última atualização**: 08/09/2026  

**Descrição**: O compilador Kof gera bytecode incorreto ao fazer atribuição 
de valor `Int` a campo de classe (`putfield`). Isso causa `VerifyError` em 
tempo de execução quando o código tenta atribuir um `Int` a um campo de classe 
de um objeto.

**Exemplo problemático**:
```kof
class Foo {
    Int x = 0
    void setX(Int v) { x = v }  // Pode gerar VerifyError em runtime
}
```

**Impacto**: Qualquer código Kof que tente atribuição de `Int` a campos de 
classe em tempo de execução pode falhar com `VerifyError`. A maioria do código 
seguro usa apenas variáveis locais ou `record` (dados imutáveis), que não 
sofrem desse problema.

**Workaround**: Use `record` para dados imutáveis ou variáveis locais em vez 
de campos de classe mutáveis.

**Roadmap**: Correção no backend de codegen do compilador Kof para evitar 
`putfield` de `Int` em campos de classe. Priority: high.

---

## Gap HW001 — Kernel bare-metal

**Status**: documentado como decisão de design  
**Plataforma**: Native  
**Descrição**: O backend Native do Kof gera ELF x86-64 que depende do Linux + 
glibc. O ponto de entrada `_start` usa `SYS_gettid`/`exit_group`, aloca com 
`mmap`, usa `pthread_create`. Não configura GDT/IDT/paging/ring0 e não expõe 
primitivos de hardware (`in/out`, `cli/sti`, `lgdt/lidt`, `int 0x80`, IRQ).

**A IR do Kof tem 30 ops de alto nível; não há assembly inline nem acesso a 
hardware.**

**Decisão (design — não silencioso)**: O KofOS é portado como kernel hosted 
em Kof puro, preservando a arquitetura e funcionalidade do VibeOS (scheduler, 
processos, IPC, syscalls, serviços microkernel, VFS, AppFS, desktop, terminal, 
file manager, editor, task manager, jogos) e o mesmo branding e fluxo de boot. 
A camada de hardware (bootloader BIOS, GDT/IDT real, PIT, PIC, ports de I/O, 
ring0/ring3 real) é abstraída.

Quando o compilador Kof ganhar modo freestanding + primitivos de hardware, 
o kernel pode ser retargetado a x86 real sem reescrever a lógica.

**Impacto**: O KofOS preserva a arquitetura VibeOS (boot → scheduler → 
memória → syscalls → IPC → VFS → userland), mas roda como aplicação hosted 
no runtime Kof, não como kernel bare-metal.

---

## Gap CONC001 — Concorrência no Native

**Status**: fechado (31/08)  
**Plataforma**: Native  
**Desde**: 0.0.5-alpha  
**Fechado**: 31/08  

**Descrição**: Native concurrency com `pthread_create` + trampoline + `await`/`pthread_join` + allocator thread-safe futex + join implícito no fim do `main`.

**Estado**: Corrigido. O backend Native agora suporta concorrência via `spawn`/`await` 
com threads nativas do sistema operacional.

---

## Gap CONC003 — Async no JS

**Status**: parcial  
**Plataforma**: JS  
**Desde**: 0.2.6-beta  

**Descrição**: Execução sequencial — `spawn`/`await` cobrem statement e expression; 
async real de event-loop = CONC003 parcial.

**Estado**: Em desenvolvimento. `spawn` e `await` funcionam para tarefas 
independentes, mas async real de event-loop ainda não está completo.

---

## Gap WEB001 — Web handler no Native/JS

**Status**: parcial (JVM: fechado 30/08)  
**Plataforma**: Native, JS  
**Desde**: 0.2.6-beta  

**Descrição**: Web handler no JVM (`web.app()`) com rotas `get/post/put/delete/patch/options`, 
`status(201, body)`, `headerSet`, WebSocket, SSE, `listenSecure` TLS — 30/08. 
Native/JS: WEB001.

**Estado**: JVM tem implementação completa. Native/JS ainda em desenvolvimento.

---

## Gap MQ001 — Filas produtor/consumidor

**Status**: fechado (01/09)  
**Plataforma**: JVM, Native, JS  

**Descrição**: Filas produtor/consumidor (`kof.mq`) nos 3 targets.

**Estado**: Corrigido. `kof.mq` funciona em todos os targets.

---

## Convenções de gap

- **Códigos**: prefixes curtos como `R6`, `HW001`, `CONC001`, etc.
- **Status**: `aberto`, `fechado`, `parcial`
- **Targets**: `JVM`, `Native`, `JS`
- **Referência**: cada gap deve ter issues/referências nos testes e docs
- **Workaround**: documented em cada gap específico

---

## Roadmap de gaps pendentes

1. **R6** — Corrigir putfield de Int no compilador (Priority: high)
2. **CONC003** — Async real no JS (Priority: medium)
3. **WEB001** — Web handler no Native/JS (Priority: medium)
4. **HW001** — Kernel bare-metal (depende de freestanding no compilador)

---

**Fonte**: Análise de bytecode compilado Kof 0.3.0-beta + verificação de runtime 
`VerifyError` + roadmap da equipe KofLang.

**Mantido por**: Equipe KofLang.  
**Atualizado**: 08/09/2026.

---

Estratégia documentada conforme AGENTS.md — regras de modo autônomo, intenção não mecanismo, 
complexidade pertence à plataforma, represente o domínio, zero cerimônia, null alucinação 
evitada, multi-target honesto.
