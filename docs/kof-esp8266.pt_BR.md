[English](kof-esp8266.md) | [Português](kof-esp8266.pt_BR.md)

last: 15/11/2026 — GATE 5 POUSADO: O PROGRAMA KOF EXECUTOU NO HARDWARE REAL (a mantenedora plugou a placa: "esp plugado na usb"; veja o bloco GATE 5 EXECUTADO abaixo). OS CINCO GATES POUSADOS — o plano CONCLUI.
GATE 4 POUSADO 14/11 — o WORKFLOW da CLI (o gate do próprio plano: "um workflow ponta a ponta documentado que não torna o hardware físico obrigatório para builds Kof comuns"): `kof build <dir> --target native.esp8266` parseia (o `parseTarget` + a lista de grafias conhecidas + a linha de usage), compila pelo `NativeEsp8266`, linka pelo crt0 do overlay e gera as IMAGENS DE FIRMWARE via esptool (`KOF_ESPTOOL` ou o python do PATH; ausente → um skip NOMEADO — o ELF continua sendo o entregável). MEDIDO ponta a ponta neste host: o `ELF 32-bit LSB executable, Tensilica Xtensa` válido + as duas imagens de flash (`Main.img0x00000.bin` a seção de boot, `Main.img0x00010.bin` a app — o layout REAL de flash). `CliUsageTargetsTest` 7/7 (a face do gate 4: o usage anuncia + o parse aceita). A lição do esptool 5.x registrada: o `--chip` vem ANTES do subcomando.
O RESTANTE: NADA — os cinco gates pousados (o 5 no ESP8266EX real, 15/11). O plano conclui; a promoção para docs/ segue no commit deste ciclo.
local: docs
estado: concluído (os cinco gates pousados; o gate 5 EXECUTADO no ESP8266EX real 15/11; promovido de docs/development/ para docs/ em 15/11)
**Dono:** a lane que pousou 192.168.100.14:0 (flatt CLI) — o gate 5 executado na placa provisionada pela mantenedora (o CH340 em /dev/ttyUSB0, ESP8266EX chip-id 0x00b3ad34).

> **GATE 5 — O SUBSTRATO DE EXECUÇÃO MEDIDO 14/11 (a evidência de provisionamento, exaustiva):** todo caminho qemu obtível foi medido e NENHUM modela o ESP8266: (a) o qemu upstream — as máquinas xtensa são `sim`/`virt`/`xtfpga` APENAS (hw/xtensa/meson.build + xtfpga.c sem referência a esp), e a distro deste host não carrega binários `qemu-system-*`; (b) o fork oficial da Espressif (esp-develop-9.2.2, 16 MB, RODA neste host após o fetch do libslirp0) — a lista de máquinas é `esp32`/`esp32s3` APENAS (hw/xtensa: esp32.c/esp32s3.c; a máquina esp8266 NÃO é modelada, nem no fork); (c) o `sys.qemu.specs` da própria toolchain (o overlay multilib esp8266 traz um libgloss qemu) não ajuda sem um qemu que modele o SoC; (d) os forks esp8266 de terceiros no GitHub não publicam binários. A CONCLUSÃO: o gate 5 ("execução física confirmada") exige uma PLACA ESP8266 REAL numa lane com acesso a hardware + o setup de flash (`esptool write_flash` com as imagens que o emitter produz — `Main.img0x00000.bin` em 0x0 e `Main.img0x00010.bin` em 0x10000) — decisão de provisionamento da mantenedora, nomeada, com a evidência medida de que nenhum caminho de software existe.

> **GATE 5 — EXECUTADO NO HARDWARE REAL 15/11 (o provisionamento da mantenedora: "esp plugado na usb" + o sudo para o grupo dialout; o CH340 em /dev/ttyUSB0, o ESP8266EX chip-id 0x00b3ad34, MAC a4:cf:12:b3:ad:34, flash 4 MB — o chip-id/flash-id do esptool medidos ao vivo).** A CADEIA EXECUTADA, cada passo medido: (1) o emitter compilou o programa do gate 5 (`print("KO ESP8266 GATE5"); print("kof on the esp8266ex"); print(7 * 6)`) — o ELF Xtensa válido + as imagens; (2) `esptool write_flash` em 0x0 — hash verificado; (3) o log de boot do ROM a 74880 baud — os segmentos carregam com checksums OK; (4) O CONSOLE IMPRIMIU OS PRÓPRIOS BYTES DO PROGRAMA: `KO ESP8266 GATE5kof on the esp8266ex42` — byte-exato para `print` (sem newline). TRÊS LEIS DE HARDWARE medidas no caminho (cada uma uma correção nomeada, nunca um ajuste silencioso): **(a) A LEI DO ENTRY** — o entry ELF default caiu no pool de literais do crt0 (Fatal exception 0 na primeira palavra, epc1=0x40200010 num laço de crashes medido) → o .ld carrega `ENTRY(_start)`; **(b) A LEI DO IRAM** — o loader V1 do ROM carrega APENAS os segmentos RAM da imagem e NÃO mapeia a flash em 0x40200000 (nenhum setup de cache): código na região mapeada da flash falta na primeira instrução → o .text inteiro vive na IRAM 0x40100000 (o subset do gate 5 mediu 1 KB — bem dentro da janela de 32 KB); **(c) A LEI DO FIFO UART** — o FIFO está na base UART0 0x60000000, NÃO no endereço qemu do gate 0 0x60000074 (nada imprimiu; o stub mínimo com 0x60000000 imprimiu 'A\n'); o write de tuning do CONF em `kof_plat_exit` caiu (a face de exit é o laço de halt apenas). Mais a correção de DYNAMIC-PRINT que a placa expôs: `print(String.valueOf(Int))` emitia o literal Java `null` (o slot da string convertida era `LIT_STR/null` e o caminho do print caía no ramo literal) — o slot agora é DINÂMICO (ponteiro a2 + comprimento a3 do conversor, o comprimento EXCLUINDO o terminador NUL — o byte terminador medido rastreando '42'), e o `kof_str_of_int` devolve o comprimento em a3. OS TESTES DE PROVA: `NativeEsp8266E2ETest` 6/6 (o novo `theDynamicPrintWritesTheConvertedBytesAndTheImageLawHolds` prende a cadeia inteira: converte → escreve o par, nenhum literal 'null', o -1 do comprimento, a base do FIFO, ENTRY(_start), o .text na IRAM, o entry do ELF na IRAM), `NativeEsp8266GpioE2ETest` 1/1, a bateria MCU/paridade 60/0F, `CliUsageTargetsTest` 7/7. O ORÇAMENTO DO PROGRAMA MÍNIMO (inalterado): dram 20%, text 3%. RESIDUAL (nomeado, fora do escopo do plano): `println` de valor dinâmico emite a face de newline — a face byte-exata `42\n` é uma chamada `kof_println_string` após o write do par (o caminho de código existe; o programa do gate 5 usou `print`); GPIO no hardware real são os registradores H1/H2 do gate 3 (o asm os prende; um blink de LED ao vivo é uma face de follow-up, não um gate).

intent: kof-esp8266-embedded-target

**Portão da regra 6 — SATISFEITO E FECHADO (história):** o alvo ESP8266 era um
NOVO alvo de compilação (uma frente da regra 6), então este documento começou
como **somente plano**. O portão foi satisfeito em ordem: a ordem dos dois
planos da mantenedora (14/11, chat) tomou a decisão; o plano foi re-promovido
para `docs/development/` com sua auditoria de viabilidade medida (gate 0,
14/11); cada fatia pousou com prova executada (`NativeEsp8266E2ETest` e a
família em `kof-compiler/src/test/`); e o gate 5 EXECUTOU o firmware gerado em
**hardware físico** (15/11 — a mantenedora provisionou a placa). **Tudo neste
documento marcado POUSADO abaixo está implementado, medido e verificado** — as
seções seguintes mantêm o texto original do plano com o estado realizado
registrado inline, para que o documento leia-se como o registro do que foi
prometido E do que foi entregue.

**Fonte:** pedido da mantenedora (14/10/2026) — "Kof Future — ESP8266 Embedded
Target". Relacionados: `docs/PLAN-BAREMETAL-BOOT.md` (a costura HAL + o
precedente MCU), `docs/native-multiarch.md` (nativo multi-arquitetura),
`docs/stdlib/PLAN-TREE-SHAKING.md` §T3 (a rota embedded/MCU), `roadmap.md` §23
item 1.6 (nativo → bare-metal).

---

# `kof.esp8266` — o alvo embarcado ESP8266 (CONCLUÍDO: o alvo está implementado, verificado em hardware e embarcando desde 15/11)

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

## 3. Capacidades de hardware (os estágios planejados, com o estado realizado)

**POUSADO (14/11, medido):** H1+H2+H3 (GPIO out/in, o delay por-ms no ccount de
80 MHz, o console UART0 — a `libs/esp/Gpio.kf` pura declara o HAL, o emitter
liga os externs qualificados ao asm a nível de registrador:
GPIO_OUT/W1TS/W1TC/ENABLE/IN em 0x60000300..18) e H8 (geração de firmware via
esptool — o layout REAL de flash, `Main.img0x00000.bin` em 0x0 + a app em
0x10000 — o relato de memória, o laço de flash provado ao vivo na placa).
**NOMEADO FORA (a amplitude H4–H7 foi explicitamente dependente da escolha do
SDK):** PWM (H4), I2C/SPI (H5), Wi-Fi station (H6), TCP/UDP (H7) — rastreados
no registro do gate 3 abaixo; cada um depende da decisão de SDK e nunca é
reivindicado incondicionalmente. Os estágios restantes foram PLANEJADOS como
segue (a tabela original, mantida pelo registro):

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

**TODOS OS SETE ATENDIDOS (15/11):** a aceitação abaixo é a própria definição
de pronto do plano — cada item carrega o ponteiro da prova. Funcionalidade
planejada e implementada foram distinguidas em todo lugar durante a construção;
este documento agora registra o estado entregue. Os critérios:
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