[English](kof-esp8266-plan.md) | [Português](kof-esp8266-plan.pt_BR.md)

last: none
doing: none-planned
next: gate-0-architecture-and-feasibility-audit
local: docs/development/future
state: planned

intent: kof-esp8266-embedded-target

**Portão da regra 6:** o alvo ESP8266 é um NOVO alvo de compilação (uma nova
superfície de backend + uma integração nova de toolchain/SDK). Este documento é
**somente plano, zero código** — nenhuma mudança de compilador, runtime, CLI ou
dependência é feita por ele. A promoção exige uma decisão da mantenedora
(`DECISIONS.md`, id `D-*` pela mantenedora) e o fluxo de promoção de future
(`docs/development/future/README.pt_BR.md` §"Quando mover", `AGENTS.md` §"Future
promotion"): reescrever com `UNDER DEVELOPMENT`, estado real + como terminar,
enfileirar no `roadmap.md` §23, apontar `docs/status.md` para ele, reivindicar no
`DOING.md`. **Nada aqui está implementado, medido em hardware ou verificado.**

**Fonte:** pedido da mantenedora (14/10/2026) — "Kof Future — ESP8266 Embedded
Target". Relacionados: `docs/PLAN-BAREMETAL-BOOT.md` (a costura HAL + o
precedente MCU), `docs/native-multiarch.md` (nativo multi-arquitetura),
`docs/stdlib/PLAN-TREE-SHAKING.md` §T3 (a rota embedded/MCU), `roadmap.md` §23
item 1.6 (nativo → bare-metal).

---

# `kof.esp8266` — o alvo embarcado ESP8266 (plano, zero código)

## 1. Motivação e casos de uso pretendidos

O ESP8266 (Espressif, Xtensa L106) é um **SoC Wi-Fi de 32 bits, single-core** —
um alvo de **compatibilidade e maker**: extremamente barato, extremamente
restrito (~80 KB de DRAM de usuário, alguns MB de flash, sem MMU), com uma
realidade dura de ciclo de vida (o fabricante moveu o suporte de primeira linha
para a família ESP32). Não é um alvo de sistemas e nunca deve ser apresentado
como um.

Por que importa para o Kof:

- É o **menor endpoint honesto** para a alegação "uma linguagem, muitos alvos":
  se um programa Kof pode ser compilado, linkado e **executado num ESP8266**, a
  alegação sobrevive ao contato com um dispositivo que não comporta nem JVM nem
  Node.js nem SO hospedado.
- É um alvo **maker/educacional** crível: hardware barato, um SoC bem conhecido,
  e um grande corpo de tutoriais existentes contra os quais um fluxo Kof pode ser
  comparado honestamente.
- Ele força a **disciplina de recursos** que a plataforma segue adiando: runtime
  mínimo, diagnósticos explícitos de recursos não suportados, relatório de
  memória/stack, um heap dimensionado pelo linker script.

Casos de uso pretendidos (planejados, não existentes): nós de sensores e
telemetria (GPIO/PWM/UART/I2C/SPI em volta de um uplink Wi-Fi), pequenos
dispositivos de rede (cliente e servidor TCP/UDP), e ensino/experimentação com
Kof em hardware real.

## 2. Abordagem arquitetural

**Reutilizar o pipeline existente.** Código Kof → o frontend/IR existentes → um
backend específico do alvo ou um caminho nativo compatível → a toolchain Xtensa
→ imagem de firmware. A funcionalidade específica do ESP8266 fica **isolada
atrás de interfaces específicas do alvo**; a superfície da linguagem Kof e a IR
são preservadas.

**O precedente MCU já existe no repositório (medido, não planejado).** A frente
bare-metal (`docs/PLAN-BAREMETAL-BOOT.md`) já matriculou dois alvos MCU como
**maquinário programático** (sem superfície CLI ainda):

| Valor do enum `Target` | Face | Contrato hoje |
|---|---|---|
| `Target.NATIVE_RISCV32` | B-4 | bare-metal RV32I, `main` com `print`/`println` de literal pela UART do `-M virt`, poweroff no test device; ops fora do subset → `NATIVE002` |
| `Target.NATIVE_MCU_ARM` | B-4.3 | bare-metal Cortex-M3 Thumb-2 (UART CMSDK do `mps2-an385`), vector table em `0x0`; concorrência → `CONC003` |

Seus testes (`NativeMcuE2ETest`, `NativeMcuArmE2ETest`, `NativeMcuArmGcTest`,
`NativeMcuArmTimeTest`, `NativeMcuGcTest`) são o **padrão de prova** que as faces
do ESP8266 devem seguir. A costura HAL `kof_plat_write` / `kof_plat_exit` /
`kof_plat_alloc` / `kof_plat_time` / `kof_plat_sync` / `kof_plat_thread` (face
B-0 do mesmo plano) é a **fronteira de runtime** que o runtime do ESP8266 deve
implementar — não uma nova.

**Portanto a abordagem preferida é:** matricular o ESP8266 como uma **terceira
face MCU** da mesma arquitetura — um novo valor de `Target` (nomenclatura pela
lane que implementar), um backend/emissor para o subset do Xtensa L106, e uma
**implementação de `kof_plat_*` apoiada no SDK do ESP8266** em vez de um runtime
novo. Um backend dedicado e independente é **apenas fallback**, e só se o Gate 0
estabelecer que o maquinário nativo atual não alcança o alvo.

**Incertezas conhecidas (não verificadas — o Gate 0 precisa medi-las):**

1. **Codegen e escalonamento Xtensa.** O L106 não tem interlocks de pipeline e
   exige tratamento de hazards de load/store (`l32r` literal pools, `memw`,
   register windows). Se o emissor nativo atual pode ser estendido, ou se um
   caminho de emissão em nível de assembler basta, é **desconhecido**.
2. **Alcançabilidade da toolchain.** A toolchain Xtensa (`xtensa-lx106-elf-*`) e
   o SDK do ESP8266 **não** fazem parte do build atual; se podem ser
   provisionados reprodutivelmente (e cross-platform) é um achado do Gate 0, não
   uma suposição.
3. **SDK vs bare-metal.** O ESP8266 nunca é realmente "sem SO" na prática: o SDK
   fornece a stack Wi-Fi e um escalonador mínimo. Qual sabor de SDK é usado, e
   quanto dele é admitido no runtime, decide quanto da alegação de pureza
   bare-metal sobrevive — é uma questão de escopo honesto, registrada aqui, não
   resolvida.
4. **Heap e GC.** O heap de ponteiro-bump mais o GC mark-sweep do runtime nativo
   atual pode não caber. Um pool dimensionado pelo linker script (a abordagem do
   plano bare-metal) é o candidato; se o GC é viável neste dispositivo é um
   achado do Gate 0/Gate 2.
5. **Divisão flash vs RAM.** O firmware é executado a partir da flash; um layout
   de heap/stack que funciona num SO hospedado não mapeia automaticamente aqui.

## 3. Capacidades de hardware planejadas (escopo pretendido — NÃO funcionalidade existente)

Organizadas em estágios incrementais. Nenhum estágio exige todos os periféricos;
cada estágio é entregável e provável independentemente.

| Estágio | Capacidade | Notas |
|---|---|---|
| **H1** | Entrada e saída GPIO | a primeira superfície de hardware provável |
| **H2** | Timers e delays | incl. o clock da plataforma atrás do `kof_plat_time` |
| **H3** | Comunicação serial UART | o primeiro estágio que dá um console utilizável |
| **H4** | PWM (onde suportado pelo SDK selecionado) | "onde suportado" é explícito — nunca alegado incondicionalmente |
| **H5** | I2C e SPI | o estágio dos barramentos de sensores |
| **H6** | Conectividade Wi-Fi station | a capacidade que define o SoC; depende da escolha do SDK |
| **H7** | Rede TCP e UDP | cavalga a superfície existente do `kof.net` onde o runtime admitir |
| **H8** | Geração de firmware, relatório de memória, flashing opcional | o estágio de fluxo de trabalho (`kof build`, inspeção de artefato) |

## 4. Portões de implementação (ordenados)

### Gate 0 — Auditoria de arquitetura e viabilidade

Inspecionar o compilador, a IR, o backend, o runtime, a CLI e a infraestrutura de
testes existentes. Verificar compatibilidade com o Xtensa L106 e identificar
requisitos de toolchain. Determinar quais recursos de linguagem e da stdlib são
viáveis no alvo. Identificar bloqueadores arquiteturais e documentar a abordagem
**selecionada**.

Critério de saída: um desenho de implementação **baseado em evidência** e
consistente com a arquitetura Kof existente (ou uma recusa honesta, se a
auditoria achar o alvo fora de alcance).

### Gate 1 — Infraestrutura do alvo

Registrar o alvo `esp8266` (um novo valor de `Target` + o dispatch, seguindo
`NATIVE_RISCV32`/`NATIVE_MCU_ARM`). Integrar a toolchain e o SDK selecionados.
Estabelecer startup, linking, layout de memória e geração do artefato de
firmware.

Critério de saída: um programa Kof mínimo compila e linka numa **imagem de
firmware ESP8266 válida**.

### Gate 2 — Runtime embarcado mínimo

Estabelecer o ponto de entrada do firmware e a sequência de inicialização.
Remover dependências desnecessárias de runtime hospedado. Definir os recursos de
linguagem suportados e **diagnósticos acionáveis** para construtos não
suportados (o padrão `NATIVE002`/`CONC003` — nunca um fallback silencioso nem uma
emulação silenciosa). Adicionar relatório de uso de memória e stack onde
viável.

Critério de saída: o programa Kof mínimo suportado opera dentro das restrições
de memória e runtime do alvo.

### Gate 3 — Abstração de hardware

Implementar GPIO, UART, timers e PWM onde suportado (estágios H1–H4), seguidos
de I2C, SPI e rede Wi-Fi (H5–H7). A HAL é exposta pelas convenções existentes de
módulo e stdlib do Kof — sem superfície ad-hoc.

Critério de saída: APIs documentadas e testes reproduzíveis para cada capacidade
entregue.

### Gate 4 — Fluxo de trabalho do desenvolvedor

Integrar compilação, inspeção de artefato, flashing opcional e monitor serial às
convenções existentes de CLI (`kof build`/`kof run` e a superfície de flag de
alvo no `KofCliSupport`; note que as faces MCU **não têm flag de CLI hoje** —
adicionar uma faz parte deste portão). A maioria das placas ESP8266 **não tem
JTAG**; o loop realista é flash + monitor serial, e deve ser documentado como
tal.

Critério de saída: um fluxo ponta a ponta documentado que **não torna o hardware
físico obrigatório para builds Kof comuns**.

### Gate 5 — Verificação e prontidão de release

Adicionar testes automatizados de alvo e toolchain. Verificar a geração de
firmware e as restrições de memória. Executar um programa de firmware mínimo em
**hardware físico**. Documentar capacidades verificadas, recursos não suportados
e instruções de setup reproduzíveis. Verificar que os alvos e testes Kof
existentes permanecem inafetados.

Critério de saída: **execução física confirmada** e todos os critérios de
aceitação satisfeitos. Compilação sozinha **nunca** é aceitação.

## 5. Restrições arquiteturais (explícitas)

- Preservar a semântica de linguagem e a IR existentes do Kof.
- Não duplicar o compilador nem criar uma implementação de linguagem
  independente.
- Não introduzir dependências de runtime JVM ou Node.js no firmware.
- Manter a funcionalidade específica do ESP8266 isolada atrás de interfaces
  específicas do alvo.
- Manter testes dependentes de hardware opcionais.
- Não emular silenciosamente recursos de linguagem não suportados.
- **Não alegar suporte de hardware baseando-se apenas em compilação bem-sucedida.**
- Manter o suporte futuro ao ESP32 em mente **sem** expandir o escopo atual para
  implementação do ESP32 (o ESP32 é outro núcleo e outra toolchain).
- Tratar o ESP8266 como alvo de **compatibilidade e maker**, contabilizando seus
  recursos restritos e seu status de ciclo de vida.

## 6. Dependências e pré-requisitos

| Dependência | Estado |
|---|---|
| O pipeline (frontend → IR → backend → toolchain) | EXISTE (medido) |
| O precedente de matrícula MCU (`NATIVE_RISCV32`, `NATIVE_MCU_ARM`) | EXISTE (medido) |
| A costura HAL `kof_plat_*` (face B-0) | EXISTE — reutilizar, não reinventar |
| A toolchain Xtensa + o SDK do ESP8266 | **AUSENTE** — provisionamento é Gate 0/Gate 1 |
| Hardware físico ESP8266 | **AUSENTE do ambiente deste repositório** — Gate 5 |
| Uma decisão da mantenedora para promover este plano | **AUSENTE** — regra 6 |

**Regra transversal R12** (`roadmap.md` §23): nenhum item de plano futuro é uma
ação sobre o estado atual, e frentes novas não abrem antes do estágio SYSTEMS
fechar. A frente bare-metal foi aberta por uma **sobreposição explícita da
mantenedora** ao R12; este plano precisaria do mesmo tipo de ordem explícita para
ser promovido.

## 7. Planos e referências relacionados

- `docs/PLAN-BAREMETAL-BOOT.md` — a costura HAL (`kof_plat_*`), o perfil
  freestanding, e as faces MCU **B-4** (`NATIVE_RISCV32`) / **B-4.3**
  (`NATIVE_MCU_ARM`) que este plano reutiliza como seu padrão arquitetural.
- `docs/native-multiarch.md` — a frente nativa multi-arquitetura
  (x86_64 · riscv64 · aarch64) e seus achados de GC/ABI por arquitetura.
- `docs/stdlib/PLAN-TREE-SHAKING.md` §T3 — a rota embedded/MCU registrada e seus
  três bloqueadores medidos (`-lc` hardcoded, syscalls Linux diretas, o GC
  bump+mark-sweep), mais a fronteira "embedded real = um backend RTOS/bare-metal
  próprio" que este plano precisa pagar.
- `docs/native-multiarch.md` / `Target.java` — o predicado `isNative()` ao qual
  um novo alvo MCU precisa se juntar.
- `roadmap.md` §23 item **1.6** (nativo → bare-metal/bootável) — a frente que
  produziu a costura HAL e o precedente MCU.
- `docs/architecture/IMPLEMENTATION-UNIVERSAL-PLATFORM.md` — a estratégia de
  plataforma (estágio SYSTEMS) cujo fechamento barra frentes novas (R12).

## 8. Não-objetivos explícitos

- **Não** é uma implementação de ESP32 (núcleo diferente, toolchain diferente —
  fora de escopo; "manter em mente" não é "implementar").
- **Não** é um motor Wi-Fi ou de rede escrito em Kof — a stack do SDK é usada por
  interop; sem reimplementação.
- **Não** é um RTOS geral.
- **Não** é uma alegação de pureza bare-metal: usar um SDK de fabricante é um
  compromisso aceito e documentado, não uma violação a esconder.
- **Não** é um programa de compra de hardware nem de CI com hardware (testes de
  hardware são opcionais e locais).
- **Não** é uma reescrita do compilador, da IR ou dos backends nativos existentes.
- **Sem** mudança de superfície de linguagem e **sem** sintaxe nova.

## 9. Critérios de aceitação (o que fecharia a iniciativa)

Funcionalidade planejada e funcionalidade implementada são distinguidas em todo
lugar: este documento é a primeira. A iniciativa só está completa quando:

1. o alvo compila e linka um programa Kof mínimo;
2. uma imagem de firmware válida é gerada;
3. restrições de memória e recursos não suportados são tratados **explicitamente**
   (diagnósticos acionáveis, nunca emulação silenciosa);
4. o fluxo documentado é reproduzível;
5. um **dispositivo ESP8266 físico executa** o firmware gerado;
6. os alvos existentes permanecem funcionais (zero regressão);
7. a documentação reflete **resultados reais de verificação** — nenhuma data de
   conclusão, nenhum status de implementação, nenhuma contagem de testes e
   nenhuma capacidade de hardware deve ser inventada em momento algum.

## 10. Requisitos de verificação

- Testes automatizados de alvo/toolchain seguem o padrão de prova MCU existente.
- Geração de firmware e restrições de memória são verificadas por inspeção do
  artefato.
- **Execução física** em hardware é a única prova da alegação de hardware.
- As suítes existentes devem permanecer verdes em todos os alvos após qualquer
  mudança (zero regressão é uma restrição permanente, não uma checagem só de
  portão).
- Testes dependentes de hardware permanecem **opcionais** e devem ser nomeados
  como tais quando pulados.