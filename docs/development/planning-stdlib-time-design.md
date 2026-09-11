# planning-stdlib-time-design.md — semântica de tempo/calendário na stdlib (PROPOSED)

**Dono:** lane STDLIB · **Status:** PROPOSED (aguarda decisão da mantenedora)
**Criado:** 10/09 · **Regra:** 6 (proposta de design não é edição de código)

## Contexto

`plan-stdlib-expansion.md` §time lista o calendário restante: `addDays/
addMonths/addYears`, `startOf/endOf`, `hoursBetween`, `age`, `formatDate/
parseDate`, `isToday`, `today`. Todos têm decisões de design ABERTAS que a
fila não pode adivinhar (regra 6 + R6 nunca-silencioso). O wedge S7 já
entregue (`isLeapYear/daysInMonth/dayOfWeek/daysBetween` + `isWeekend`
S7-ext) funciona porque a semântica era 100% fixada (calendário civil ISO,
serial Hinnant já portado).

**Machine disponível nos 5 alvos:** `epochDay(y,m,d)` (serial civil, B14) e
`dayOfWeek`. Tudo abaixo é questão de CONTRATO, não de port.

## Decisões necessárias (uma linha = uma escolha)

### D1 — `today()` e `now()` vs fuso (o nó górdio)

`time.now()` hoje retorna epoch millis UTC. "Hoje" depende do fuso: JVM tem
o default do host; Native x86/riscv chama `clock_gettime` (UTC); JS Node usa
o host, browser usa o fuso do browser. **Hoje não há paridade cross-target
de "hoje"** — e é exatamente o tipo de divergência silenciosa que a regra de
paridade proíbe.

Opções:
- **A (recomendada):** stdlib é **UTC-only**: `today()` = data UTC derivada
  de `now()`; quem quiser fuso usa `time.now() + tzOffsetSeconds()` — novo
  getter explícito (JVM host, JS `Date.getTimezoneOffset`, Native
  `TZ`/`/etc/localtime` = gap DIAG). Sem paridade acidental; tudo honesto.
- **B:** `today()` usa fuso do HOST em cada target. ❌ divergência
  silenciosa documentada só em tabela — viola paridade.
- **C:** adiar hoje/isToday/today até D1 resolver.

### D2 — assinaturas de calendário (`addDays`, `age`, `startOf`)

`addDays(y,m,d,n)` precisa retornar **três** inteiros (ano/mês/dia). Retorno
Array/objeto na camada de dispatch = **DD-STDLIB-01** (mesma trava de
`randomBytes`), aguarda decisão. Sem ele:
- **A (recomendada):** adiar addDays/addMonths/addYears/startOf/endOf/age
  para depois de DD-STDLIB-01. Nada de "3 funções separadas"
  (`addDaysYear/Month/Day`) — API quebrada.
- **B:** `addDaysToSerial(serial, n)` + `serialOf(y,m,d)` + `yearOf/monthOf/
  dayOf(serial)` — tudo escalar puro, compõe em Kof. Feio, mas sem retorno
  composto. ❌ vira lição idiomatic anti-pattern.

### D3 — `hoursBetween` e a família de durações

`daysBetween` trava a convenção: **incompleto** (só dias inteiros; data
inválida => 0). `hoursBetween(y1,m1,d1,h1,y2,m2,d2,h2)` tem duas leituras:
- **A (recomendada):** consistente com `daysBetween` => **completo** — só
  conta horas em dias-inteiro-completos + delta de horas (inverso de A:
  floor da diferença real). Dado que `daysBetween` é truncado em direção a
  zero (sinal), horasBetween floor **simétrico**.
- **B:** segundos/dias como número real (float) => FLT001 (sem double nos
  cross). Bloqueado.
- **C:** nomear `hoursBetween(y,m,d,H,M,S,y,m,d,H,M,S)` — assinatura de 12
  args = pesadelo. Não.

### D4 — `formatDate`/`parseDate` — formato é string de pattern?

- **A (recomendada):** NO. stdlib expõe só `formatDateIso(y,m,d)->STR`
  ("YYYY-MM-DD", fixo, 5 alvos, trivial) e `parseDateIso(STR)->y/m/d` só
  depois de DD-STDLIB-01 (ou 3 getters serial, ver D2). Pattern strings
  (`dd/MM/yyyy`) = motor de parsing = complexidade na stdlib base (R1/R12)
  e decisão de design de superfície. `age(birthY,birthM,birthD, nowY,nowM,
  nowD)` escalar (6->INT, aniversário completo) — sem now(): o chamador passa
  `today` quando D1 fechar; entretanto `age` fica adiado.
- **B:** pattern com 3 tokens (`yyyy`, `MM`, `dd`) só — menor, ainda é
  design de mini-DSL. Aguarda.

### D5 — `isToday`

Depende de D1. Com D1-A: `isToday(y,m,d)` = `y/m/d == today-UTC` (3->BOOL,
porta a porta `todayIso` interna). Sem decisão, adia.

## Recomendação consolidada

D1-A (UTC-only, com `tzOffsetSeconds` adiado para nota SEC/NAT), D2-A (adia
compostos p/ DD-STDLIB-01), D3-A, D4-A (só `formatDateIso`), D5 ligado a D1.
Isso libera **exatamente 2 funções** agora: `time.todayIso() -> STR` (zero
arg, `YYYY-MM-DD` UTC) e `time.formatDateIso(y,m,d) -> STR` (validade => ""
— face leniente da stdlib), ambas sem retorno composto, sem fuso, sem float
— implementáveis nos 5 alvos sem tocar em nenhuma decisão em aberto.

**Aguardando a mantenedora.** Ninguém da lane STDLIB implementa D1–D5 sem o
"de acordo" — regra 6. Quando aprovado, adicionar item S7c no plano com a
decisão travada + matrizes novas.

## Precedentes

- DD-STDLIB-01 (`planning-stdlib-array-returns.md`) — retorno composto.
- split random/security (S10) — face leniente inseguro documentada.
- S6a/S6b — predicados sem gate entraram direto (semambiguidade); o critério
  é "sem ambiguidade".
