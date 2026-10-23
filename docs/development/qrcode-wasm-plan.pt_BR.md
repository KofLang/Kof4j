[English](qrcode-wasm-plan.md) | [Português](qrcode-wasm-plan.pt_BR.md)

# Kof — Implementação Incremental de `kofqrcode` + `KofWasm`

**Dono:** `192.168.15.30:9092` (lane compiler/JVM/native — assumido como ÓRFÃO em 22/10 sob `D-PLAN-ORPHAN-ASSUME`; diretriz da mantenedora, exceção a `D-PLAN-ONE-OWNER`).

> **Entregue 12/10 — Fase 1 fatia 2b: correção de erros Reed-Solomon (lane nativa/GC `192.168.15.117:0`).** O `QrDecode.k` ganha o decodificador completo de erros: síndromes (`rsSyndromes`), localizador de Berlekamp-Massey (`rsBerlekampMassey`, retornando o grau + coeficientes low-first), busca de Chien do polinômio recíproco (`rsChien` — as raízes de x^L·Λ(1/x) ficam em x = X_k, que a referência validada mostrou ser a convenção cujas raízes mapeiam para a posição n-1-j no array), e magnitudes de Forney com o fator X sobre GF(256) (`gfPow` para exponenciação GF correta — o `pow2` inteiro estoura depois de 2^30 e era um bug real apanhado contra a referência). O `deinterleaveAndVerify` agora roda por bloco: síndromes não-zero acionam a correção (até floor(ecc/2) erros de byte por bloco); acima da capacidade, a recusa nomeada `QRCODE:` permanece. **Prova (executada, referência-primeiro):** um decodificador de referência em Python (`rsref.py`) validado sobre blocos QR reais danificados — 81 casos (0/1/2/erros de capacidade máxima em blocos das versões 1/3/7/10 mais a recusa de capacidade excedida) **81/81** — depois portado e verificado em Kof: a bateria de danos (flips isolados dentro da região de dados do símbolo) recupera o texto exato para contagens de dano dentro da capacidade, e dano acima da capacidade (um patch 12×12 apagado) é recusado pelo nome; `QrCodeDamageCorrectionTest` **2/2**, `QrCodeReaderE2ETest` **5/5** (o antigo teste de corrupção de 1 pixel agora corretamente ESPERA recuperação e mudou para o teste de danos), `QrCodeWriterE2ETest` **8/8** — 15/15 no total. Bugs apanhados contra a referência durante a porta: a direção da busca de Chien (a avaliação da Λ própria acha raízes em X_k⁻¹, fora do bloco — a convenção recíproca é a que casa com o mapeamento de posição n-1-j), o estouro do `pow2` inteiro, e dois erros de escopo de variável do Kof. O leitor agora tolera símbolos danificados; a próxima fatia é o leitor de câmera (`k.process`/FFI). **A Fase 2 e a Fase 3 permanecem NÃO INICIADAS — a Fase 3 é decisão da mantenedora (rule-6, novo `Target`).**

> **Entregue 12/10 — Fase 1 fatia 2a: o leitor de arquivo (lane nativa/GC `192.168.15.117:0`).** `libs/qrcode/QrDecode.kf`: `decodeQr(caminho) → String` — o decodificador PNG puro-Kof existente (`pngRaster`) fornece as amostras de tons de cinza; o símbolo é localizado pela caixa delimitadora dos pixels escuros (os três finders ficam nos cantos, então a caixa delimitadora escura é o próprio símbolo); as escalas de módulo candidatas são validadas lendo de volta os três padrões finder e o código BCH do format info (ordem de bits verificada contra um QR real gerado pelo python: a string de 15 bits corre MSB-first ao longo da faixa da linha 8 e sobe pela faixa da coluna 8); a máscara é desfeita, os codewords são extraídos na ordem de posicionamento, desintercalados com as tabelas RS do escritor, e as síndromes de Reed-Solomon de cada bloco são verificadas antes de a mensagem ser analisada (modo byte sobre UTF-8, incluindo pares substitutos). **Prova (executada, red-first):** a primeira execução do teste falhou com `PKG006` (import antes de a biblioteca existir) e as correções foram guiadas por falhas executadas — a ordem de bits do format info era lida LSB-first (corrigida para MSB-first, verificada contra um QR gerado pelo python), a caminhada de posicionamento supercontava as células reservadas 9x9 vs as áreas de probe 8x8 do escritor (corrigido), as células de resto da v2-6 estouravam o buffer de bits (corrigido), a análise da mensagem lia bytes de codeword como se fossem bits (corrigido com leituras de bits de fluxo), e o fluxo da mensagem era lido pelas sequências data+ecc por bloco em vez dos codewords de dados contíguos (corrigido — a transição de bloco corrompia o byte 29 de uma mensagem de 100 bytes, pego pela bateria de round-trip). Estado final: round-trip escritor→leitor **20/20** (a bateria completa, versões 1-10, ASCII/URL/Unicode/vazio/capacidade máxima) na JVM; `QrCodeReaderE2ETest` **5/5** (re-medido 22/10 pela lane que assumiu este orfao; a classe tem CINCO @Test, 4 deles condicionados ao ambiente — o antigo "7/7" contava uma mistura de formas, nao a classe) — round-trip na JVM + Script + nativo x86-64 + cross riscv64/aarch64, decodificação de PNGs gerados pelo python-qrcode (implementação independente, escalas de módulo 1-3), recusa de símbolo corrompido por verificação de paridade (`QRCODE:`), recusa de imagem sem QR; `QrCodeWriterE2ETest` 8/8 de não-regressão. **Escopo da fatia (honesto):** imagens digitais verticais cuja caixa delimitadora escura é exatamente o símbolo (símbolos rotacionados ou fotografados, níveis ECC diferentes de M, versões acima de 10 e símbolos danificados são recusados com diagnósticos nomeados; a correção de erros RS completa é a próxima fatia). **Como terminar a Fase 1:** próxima fatia = correção de erros RS (Berlekamp-Massey + Chien + Forney) → leitor de câmera (`kof.process`/FFI). **A Fase 2 (abstrações de frontend) e a Fase 3 (alvo `KofWasm`) seguem NÃO INICIADAS — a Fase 3 é decisão da mantenedora (rule-6, novo `Target`), nunca escopo desta unidade.**
> **Entregue 11/10 — Fase 1 fatia 1: o gerador de QR puro-Kof (lane nativa/GC `192.168.15.117:0`).** `libs/qrcode/Qrcode.kf` (matriz: ISO/IEC 18004 modo byte sobre UTF-8, versões 1-10, nível ECC M, seleção automática de versão e máscara automática pela pontuação de penalidade da especificação) + `libs/qrcode/QrPng.kf` (PNG em tons de cinza com a zona silenciosa de 4 módulos, fluxo zlib stored-deflate, CRC-32 e Adler-32 escritos via `File.writeBytes`). **Prova (executada, red-first):** as matrizes de módulos são byte-idênticas ao módulo Python `qrcode` — uma implementação independente da mesma especificação — numa bateria de 80 casos cobrindo versões 1-10 (ASCII/URL/Unicode/vazio/capacidade máxima; medido 20 + 60 aleatorizados, 0 falhas); todo PNG decodifica de volta ao texto sob o leitor zbar via ctypes (payloads ASCII byte-exatos; os bytes de payload dos não-ASCII são provados pela bateria de paridade, pois o zbar local aplica sua própria suposição de charset); `QrCodeWriterE2ETest` **8/8** — paridade JVM + round-trip zbar + Script + nativo x86-64 + JS + cross riscv64/aarch64 (todas as saídas de matriz byte-idênticas) + recusa de entrada superdimensionada (`QRCODE:`); `FileLibraryE2ETest` 13/13 (o vizinho `File.writeBytes`). Primeiro vermelho medido: o import falhou antes de a biblioteca existir (`PKG006`); três bugs reais foram apanhados e corrigidos contra a referência durante a porta (o loop de intercalação de dados do RS parava um byte antes nas versões de dois grupos v8/v9/v10; os bits de dados eram lidos LSB-first em vez de MSB-first; a penalidade N4 usava uma porcentagem pré-arredondada em vez da exata). **Como terminar a Fase 1:** próximas fatias = o leitor de arquivo (decodificar símbolos QR de uma imagem) → o leitor de câmera (`kof.process`/FFI). **A Fase 2 (abstrações de frontend) e a Fase 3 (alvo `KofWasm`) seguem NÃO INICIADAS — a Fase 3 é decisão da mantenedora (rule-6, novo `Target`), nunca escopo desta unidade.** O texto original do plano segue.

> **Estado (11/10): EM DESENVOLVIMENTO — promovido do `future/` por `D-FUTURE-PROMOTION` (retomada por `D-FUTURE-UNFREEZE`, mantenedora 11/10; lane nativa/GC `192.168.15.117:0`).** Fase 1 fatia 1 = o gerador de QR puro-Kof: `libs/qrcode/Qrcode.kf` (ISO/IEC 18004 modo byte, versões 1–10, ECC M, versão automática, pontuação de penalidade da máscara pela especificação) + saída PNG pelo caminho stored-deflate dos escritores de imagem; provado por um decodificador independente (zbar via ctypes, `libzbar.so.0` medida no host da lane) fazendo round-trip do PNG gerado e por paridade de matriz contra o módulo python `qrcode`. **Como terminar a Fase 1:** próximas fatias = leitor de arquivo (decodificação estilo ZXing em Kof puro ou face de interop somente-JVM honesta) → leitor de câmera (`kof.process`/FFI) → a bateria de testes gerador/leitor da seção "testes do kofqrcode". **A Fase 2 (abstrações de frontend) e a Fase 3 (target `KofWasm`) seguem NÃO INICIADAS — a Fase 3 é decisão da mantenedora (rule-6, novo `Target`), nunca escopo desta unidade.** A redação anterior segue para histórico: Não é fila de
> execução (regra dos três estados + R12). Notas de choque com o estado
> real antes de promover: (a) `kofqrcode` é domínio pesado → **pacote
> oficial** (R1 + gate `scripts/check_stdlib_boundary.sh`), decoder via
> interop (ZXing no JVM — R9), câmera via `kof.process`/FFI; (b)
> `KofWasm` = **novo target** → mexe no enum `Target`, na CLI e no
> dispatcher = decisão de design da mantenedora (regra 6), e o `app {
> route ... }` do exemplo é pseudocódigo (o `kof.ui` atual declara
> intenção, não HTML — regra 9); (c) os exemplos usam `let`/`print`
> (idiomas falsos — `training/anti-patterns/fake-idioms.md`); sintaxe
> real é `var`/`val`/`println`.

Quero evoluir o projeto Kof com **duas funcionalidades independentes**, seguindo rigorosamente a arquitetura existente e sem quebrar absolutamente nada que já funciona:

1. `kofqrcode` — Reader + Writer de QR Code
2. `KofWasm` — novo target de frontend baseado em WebAssembly, coexistindo com `KofJS`

## REGRA ABSOLUTA

### NÃO QUEBRAR A BASE EXISTENTE

A implementação deve ser **incremental, isolada e compatível com o estado atual do projeto**.

Antes de escrever código:

* analise toda a arquitetura relevante;
* entenda o compilador;
* entenda o sistema de targets;
* entenda o KofJS atual;
* entenda Kof4J/KofNative/KofScript;
* entenda o sistema de módulos;
* entenda APIs de frontend;
* entenda testes;
* entenda CLI;
* entenda LSP;
* entenda documentação;
* execute os testes existentes.

Não faça refactor arquitetural amplo sem necessidade.

Não altere APIs existentes sem motivo.

Não remova funcionalidades.

Não substitua implementações existentes simplesmente porque existe uma abordagem "melhor".

Não crie abstrações gigantescas antecipadamente.

**Preserve a base existente e evolua-a somente onde for necessário.**

---

# PARTE 1 — `kofqrcode`

Criar suporte para QR Code no ecossistema Kof.

A funcionalidade deve possuir dois lados:

```text
kofqrcode
├── Reader
│   ├── arquivo/imagem
│   └── câmera em tempo real
│
└── Writer
    └── texto → QR Code
```

## Reader — arquivo

Permitir algo conceitualmente semelhante a:

```kof
import kof.qrcode.*

let result = QRCode.read("qrcode.png")

print(result.text)
```

A API final deve seguir os padrões reais do Kof, portanto **não copie esse exemplo cegamente**.

O Reader deve:

* receber caminho de arquivo;
* carregar imagem;
* detectar QR Code;
* decodificar conteúdo;
* retornar resultado fortemente tipado;
* tratar arquivo inexistente;
* tratar imagem inválida;
* tratar ausência de QR Code;
* tratar QR Code inválido/corrompido;
* fornecer erros claros.

Avaliar uma estrutura semelhante a:

```text
QRCodeResult
├── text
├── format
└── rawBytes
```

Somente adicionar campos realmente necessários.

---

# Reader — câmera em tempo real

Adicionar suporte para leitura através de câmera quando o target/plataforma permitir.

Conceitualmente:

```kof
let reader = QRCode.camera()

reader.start(frame -> {
    if frame.hasCode() {
        print(frame.text)
    }
})
```

Novamente, isso é apenas referência de ergonomia.

A implementação real deve respeitar a arquitetura do Kof.

Considerar:

* abertura da câmera;
* permissões;
* captura de frames;
* processamento dos frames;
* detecção;
* callbacks/eventos;
* start/stop;
* liberação de recursos;
* câmera indisponível;
* concorrência;
* evitar processamento excessivo;
* não bloquear a thread principal desnecessariamente.

Se existir infraestrutura de async/eventos/threads/streams no Kof, reutilizá-la.

**Não implementar um loop infinito ingênuo.**

Se determinado target não possuir suporte adequado à câmera, documentar a limitação em vez de criar uma implementação fake.

---

# Writer

Permitir gerar QR Code a partir de texto.

Conceitualmente:

```kof
QRCode.write(
    "https://koflang.dev",
    "qrcode.png"
)
```

O Writer deve:

* receber texto;
* gerar QR Code;
* permitir salvar como imagem;
* tratar erros de escrita;
* suportar Unicode;
* funcionar de forma consistente com o Reader.

Não criar inicialmente uma API gigantesca de configuração.

Deixar espaço para evolução futura, como:

* tamanho;
* margem;
* formato;
* error correction level.

---

# Dependências do QR Code

Não implementar encoder/decoder de QR Code do zero se existir uma biblioteca madura e adequada.

Avaliar bibliotecas existentes.

Para JVM, considerar bibliotecas consolidadas como ZXing se forem compatíveis com:

* arquitetura;
* licença;
* tamanho;
* performance;
* targets.

Para Native/JS/WASM, avaliar soluções adequadas separadamente.

Não adicionar dependências gigantescas sem necessidade.

Toda dependência nova precisa ter justificativa.

---

# Testes do `kofqrcode`

Criar testes para:

### Writer

* texto simples;
* URL;
* Unicode;
* arquivo de saída;
* erros de escrita.

### Reader

* QR Code válido;
* QR Code gerado pelo Writer;
* arquivo inexistente;
* imagem inválida;
* imagem sem QR Code;
* Unicode;
* URL.

Principal teste de integração:

```text
texto
 ↓
QRCode Writer
 ↓
arquivo
 ↓
QRCode Reader
 ↓
texto original
```

O resultado precisa ser idêntico ao texto original.

---

# PARTE 2 — `KofWasm`

Adicionar um novo target:

```text
wasm
```

como alternativa ao:

```text
js
```

O objetivo é permitir que Kof seja usado no frontend através de **JavaScript ou WebAssembly**, mantendo o mesmo código-fonte da aplicação.

Arquitetura desejada:

```text
                         Kof Source
                             │
                             ▼
                    ┌─────────────────┐
                    │ Kof Frontend API│
                    └────────┬────────┘
                             │
                  ┌──────────┴──────────┐
                  ▼                     ▼
              KofJS                 KofWasm
                  │                     │
                  ▼                     ▼
             JavaScript               WASM
                  │                     │
                  └──────────┬──────────┘
                             ▼
                         Browser
```

## PRINCÍPIO FUNDAMENTAL

### O código da aplicação NÃO deve mudar entre JS e WASM.

Exemplo:

```kof
import kof.web.*

app {
    route("/") {
        page {
            title("Kof")
            text("Hello World")
        }
    }
}
```

O mesmo código deve poder ser compilado com:

```bash
kof build --target js
```

e:

```bash
kof build --target wasm
```

Sem criar:

```kof
if target == js
```

Sem duplicar a aplicação.

Sem criar:

```text
MyAppJS
MyAppWasm
```

A diferença deve estar no backend/runtime/interop, não na lógica da aplicação.

---

# Arquitetura de frontend

Antes de implementar `KofWasm`, estudar cuidadosamente o `KofJS` existente.

Identificar quais APIs são:

* linguagem;
* frontend;
* DOM;
* browser;
* runtime;
* JavaScript interop;
* compilador;
* backend.

Se necessário, extrair somente as abstrações que realmente precisam ser compartilhadas.

O objetivo é chegar progressivamente a algo como:

```text
kof.web
│
├── API comum
│   ├── DOM
│   ├── Events
│   ├── HTTP
│   ├── Storage
│   └── Browser APIs
│
├── KofJS implementation
│
└── KofWasm implementation
```

A API comum deve ser independente do backend.

---

# KofJS não pode ser quebrado

`KofJS` já existe.

Não substituir.

Não reescrever.

Não migrar tudo para WASM.

Não remover suporte JS.

`KofWasm` deve ser adicionado ao lado dele.

O resultado final precisa ser:

```text
Kof Frontend
├── JS
└── WASM
```

---

# Interoperabilidade WASM ↔ JavaScript

WebAssembly não possui acesso mágico ao DOM.

Portanto, projetar corretamente a comunicação entre:

```text
Kof/WASM
      ↕
JavaScript bridge
      ↕
Browser APIs
```

Avaliar cuidadosamente:

* DOM;
* eventos;
* fetch;
* Web APIs;
* console;
* storage;
* timers;
* callbacks;
* strings;
* arrays;
* objetos;
* lifecycle;
* memória;
* comunicação JS ↔ WASM.

Não esconder limitações reais da plataforma.

Se determinada API depender de JavaScript, encapsular isso em uma camada de interop.

---

# Target system

O compilador deve reconhecer:

```bash
kof build --target js
kof build --target wasm
```

E, se fizer sentido dentro da CLI atual:

```bash
kof run --target wasm
```

ou outro mecanismo equivalente.

Não inventar comandos incompatíveis com a CLI existente.

Seguir exatamente o padrão atual de targets.

---

# Compatibilidade

Documentar claramente:

```text
Target     Status
-------------------------
JVM        existente
Native     existente
JS         existente
WASM       novo
Script     existente
```

Para cada API de frontend, indicar se possui:

```text
JS
WASM
JS + WASM
```

Não declarar suporte que não existe.

---

# Mesmo código, targets diferentes

Criar um exemplo real:

```text
examples/frontend/
```

ou seguir a estrutura existente.

Exemplo:

```kof
import kof.web.*

app {
    route("/") {
        page {
            title("Kof WASM")
            text("Hello from Kof")
        }
    }
}
```

Testar o mesmo arquivo com:

```bash
kof build --target js
kof build --target wasm
```

O código-fonte deve permanecer exatamente igual.

Esse requisito é fundamental.

---

# Testes do KofWasm

Adicionar testes para:

* compilação para WASM;
* geração correta do artefato;
* carregamento no browser;
* execução;
* interop com JavaScript;
* DOM;
* eventos;
* HTTP, quando suportado;
* erros de compilação;
* APIs não suportadas;
* build existente de JS.

Garantir que:

```text
KofJS antes
        ↓
implementação KofWasm
        ↓
KofJS depois
```

continue funcionando.

---

# Integração com o compilador

Não criar um compilador paralelo.

O ideal é:

```text
Kof Parser
    ↓
Kof AST / IR
    ↓
    ├── JVM Backend
    ├── Native Backend
    ├── JS Backend
    └── WASM Backend
```

Se a arquitetura atual não estiver exatamente assim, adapte-se à arquitetura existente.

O princípio é evitar duplicação da linguagem.

`KofWasm` deve ser outro backend/target, não outra linguagem.

---

# LSP

Verificar se o novo target exige mudanças no LSP.

O código Kof deve continuar tendo:

* syntax highlighting;
* diagnostics;
* completion;
* hover;
* resolução de símbolos.

Não criar uma linguagem diferente para WASM.

---

# Documentação

Atualizar a documentação existente.

Documentar:

## `kofqrcode`

* Reader;
* Writer;
* arquivo;
* câmera;
* geração;
* tratamento de erros;
* targets;
* limitações.

## `KofWasm`

* o que é;
* por que existe junto com KofJS;
* como selecionar o target;
* compatibilidade;
* JavaScript interop;
* APIs disponíveis;
* limitações;
* exemplos.

Mostrar explicitamente:

```bash
kof build --target js
```

versus:

```bash
kof build --target wasm
```

e explicar que **o código-fonte da aplicação permanece o mesmo**.

---

# Ordem de implementação

Não tente implementar tudo de uma vez.

Faça em fases.

## Fase 0 — análise

* estudar arquitetura;
* rodar testes;
* estudar targets;
* estudar KofJS;
* identificar pontos de extensão.

## Fase 1 — `kofqrcode`

Primeiro:

```text
Writer
 ↓
Reader de arquivo
 ↓
testes
 ↓
integração
```

Depois:

```text
Camera Reader
```

## Fase 2 — abstrações frontend

Antes de WASM:

* identificar APIs compartilháveis;
* reduzir acoplamento do frontend ao JS;
* extrair somente abstrações necessárias.

Não reescrever KofJS inteiro.

## Fase 3 — `KofWasm`

Implementar:

```text
target
 ↓
backend
 ↓
runtime/bridge
 ↓
browser
```

Começar com o menor conjunto funcional possível.

## Fase 4 — frontend comum

Validar que o mesmo código Kof funciona com:

```text
--target js
--target wasm
```

## Fase 5 — estabilização

* testes;
* integração;
* documentação;
* performance;
* erros;
* regressões.

---

# Critérios de aceitação

A implementação só pode ser considerada concluída quando:

### `kofqrcode`

* [ ] Writer funcional
* [ ] Reader de arquivo funcional
* [ ] Reader de câmera funcional onde suportado
* [ ] Writer → Reader funcionando
* [ ] erros tratados
* [ ] testes adicionados
* [ ] documentação atualizada

### `KofWasm`

* [ ] target `wasm` reconhecido
* [ ] backend funcional
* [ ] artefato WASM gerado
* [ ] execução no browser
* [ ] JavaScript interop funcional quando necessário
* [ ] APIs frontend comuns funcionando
* [ ] mesmo código funcionando em JS
* [ ] mesmo código funcionando em WASM
* [ ] KofJS preservado
* [ ] testes adicionados
* [ ] documentação atualizada

### Projeto inteiro

* [ ] build existente continua funcionando
* [ ] testes existentes continuam passando
* [ ] golden tests continuam passando
* [ ] CLI continua funcionando
* [ ] LSP continua funcionando
* [ ] nenhum breaking change desnecessário
* [ ] nenhuma dependência desnecessária
* [ ] nenhuma refatoração fora de escopo
* [ ] nenhuma funcionalidade existente removida

---

# Regra final

Antes de cada alteração importante, pergunte:

> "Isso é necessário para implementar a feature ou estou aproveitando para refatorar o projeto?"

Se não for necessário, **não faça**.

A prioridade é:

```text
estabilidade
    ↓
compatibilidade
    ↓
arquitetura consistente
    ↓
testes
    ↓
funcionalidade
    ↓
otimização
```

Não sacrifique a base existente para acelerar a implementação.

O resultado esperado não é apenas "funciona".

É:

**Kof continua sendo Kof, só que agora possui QR Code e um backend WebAssembly de verdade.**
