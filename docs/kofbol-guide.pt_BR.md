[English](kofbol-guide.md) | [Português](kofbol-guide.pt_BR.md)

last: o guia do `kof.legacy` (KofBOL, `D-KOFBOL`) — fatias 1–11 pousadas
doing: fatia 11 (docs + guia + a matriz de conformância) — ESTE documento
next: a decisão de promoção do plano (todas as fatias do §20 pousadas ou delimitadas)
local: docs/kofbol-guide.pt_BR.md
dona: 192.168.100.14:0, flatt CLI, sem servidor opencode neste host; porta n/a
state: under-development

intent: kofbol-guide

---

# Modernizando um sistema COBOL com Kof — o guia do `kof.legacy`

**Escopo:** este guia documenta o que `libs/legacy` FAZ hoje (cada face tem
prova executada — veja `Kofbol*E2ETest`) e o que ela recusa PELO NOME (os
códigos de gap LEG001–LEG019). Não é uma promessa: capacidades futuras moram
em `kofbol-plan.md` §20 com as fatias delas.

## 1. O que é o KofBOL

O KofBOL é a camada de interoperabilidade legada bancária: dados com forma
COBOL (registros fixed-width, EBCDIC, decimal empacotado) entram no Kof como
valores simples, e dados Kof saem byte-exact. A semântica COBOL vive em
bibliotecas Kof puras; a aplicação nunca toca bytes, posições ou números de
código de retorno à mão (a regra anti-corrupção).

```
Copybook COBOL → import Copybook → descritor Layout → decode/encode → seu domínio
```

## 2. O modelo de descritor (sem sintaxe nova)

O KofBOL introduz **nenhuma superfície de linguagem** (regra 6 honrada): um
layout de registro é um valor `Layout` simples construído de descritores
`Field`.

```kof
import legacy.Legacy

main() {
    var lay = Layout(listOf(textOf("name", 10), digitsOf("amount", 5)))
    var rec = encode(lay, listOf("JOHN", "42"))
    println(rec)                    // JOHN      00042
    var vals = decode(lay, rec)     // as fatias CRUAS, nunca trimadas em silêncio
}
```

Construtores: `textOf(name, length)` · `digitsOf(name, length)` ·
`decimalOf(name, digits, scale)` (a forma decimal/assinada). `fieldPic(name,
pic)` aceita as cláusulas simples `X`, `9`, `X(n)`, `9(n)`.

## 3. O import de copybook

```kof
import legacy.Copybook

var cb = parseCopybookFull(copybookText)
cb.layout          // o Layout (itens 01–49 planos com cláusula PIC)
cb.conditions      // os nomes de condição 88-level como valores
cb.redefines       // as visões overlay com os offsets explícitos
renderLayout(cb.layout, "customer")     // o contrato Kof gerado
```

- **OCCURS `<n> [TIMES]`** (itens elementares) expande em nomes com subscrito
  `ITEM(1)..ITEM(n)` — byte-exact.
- **`88 <nome> VALUE 'lit'.`** vira uma constante nomeada que a aplicação
  compara com o campo decodificado — o TESTE da condição fica no lado da
  aplicação.
- **REDEFINES** (elementar) vira uma VISÃO overlay: `decodeFrom(view, rec,
  offset)` lê os mesmos bytes por outro PIC.
- Recusado PELO NOME (nunca descartado em silêncio): OCCURS de grupo,
  DEPENDING ON, REDEFINES de item OCCURS, REDEFINES + OCCURS, múltiplos
  valores 88 / THRU, os atributos COMP/USAGE (LEG014), linhas malformadas
  (LEG015).

O CLI: `kof legacy <copybook> [varName]` imprime o contrato gerado.

## 4. EBCDIC e decimal empacotado

```kof
var bytes = encode37("HELLO")        // 200 197 211 211 214  (CP037)
var text = decode37(bytes)           // HELLO — round-trip verificado
var packed = packComp3("-12.34", 4, 2)   // 01 23 4D
var dec = unpackComp3(packed, 4, 2)      // -12.34
```

- **CP037** (inglês americano) vem com tabelas EXPLÍCITAS geradas de um codec
  real e verificadas por round-trip sobre toda a faixa ASCII imprimível.
  Outras páginas de código são uma decisão ABERTA (a Q5 do plano) — uma
  página pousou, como diz o texto da própria fatia.
- **COMP-3**: dois dígitos por byte, o sinal no último nibble (C/F/A/E
  positivos, D/B negativos), ponto decimal implícito na `scale`, o pad de zero
  à esquerda na CONTAGEM TOTAL de nibbles. Sinais e escala/overflow são
  totalmente provados.

## 5. Transportes, mensageria, batch, transações

```kof
import legacy.Transport
import legacy.Messaging
import legacy.Tx
```

- **A matriz de capacidades** (`capabilityMatrix()`): `file` está CONECTADO
  de ponta a ponta; batch stdio / tcp / http / mq estão DECLARADOS com o que
  precisam e recusam LEG018 até uma fatia conectá-los. Nunca um fallback
  silencioso.
- **O broker** (`publish`/`receive`): FIFO com correlation ids; uma fila
  VAZIA é um **VALOR WARNING** (§10) — o sinal é o STATUS, nunca um null,
  nunca uma exceção.
- **O batch** (`batchDecode`): um registro ruim nunca mata o batch e nunca é
  silencioso — contado com o índice no
  `BatchReport(total, ok, failed, failedIndexes)`.
- **A transação** (`begin`/`stage`/`commit`/`rollback`): all-or-nothing —
  nada é visível até o `commit`; o `rollback` devolve o base inalterado com
  a contagem descartada como valor explícito. Nenhum protocolo distribuído é
  inventado (§13).

## 6. Códigos de retorno e o adapter (a fronteira anti-corrupção)

```kof
import legacy.Adapter

var r = response(8, "bad")      // LegacyResponse(8, "ERROR", "bad")
```

- **Um RC legado não-zero é DADO, nunca exceção** (§10) — e nunca engolido: a
  tabela de faixas é explícita (0 OK · 4 WARNING · 8/12 ERROR · >=16 SEVERE;
  negativo recusado LEG017).
- **`toMap`/`valuesOf`** convertem posições decodificadas em ligações
  NOMEADAS e voltam — a aplicação nunca indexa um campo legado à mão. Uma
  ligação ausente é recusada LEG016.

## 7. Códigos de erro (LEG001–LEG019)

| Código | Significado |
|---|---|
| LEG001 | comprimento do registro divergente / registro curto demais para o offset overlay |
| LEG002 | não-dígito em campo ou valor numérico |
| LEG003 | valor longo demais para o campo |
| LEG004 | contagem de valores != contagem de campos |
| LEG005 | cláusula PIC não suportada |
| LEG006 | cláusula/contagem PIC malformada |
| LEG007 | byte fora da faixa de texto CP037 |
| LEG008 | char fora da faixa imprimível CP037 |
| LEG009 | valor não cabe (overflow COMP-3) |
| LEG010 | decimal malformado |
| LEG011 | nibble de dígito ruim / byte fora de faixa |
| LEG012 | nibble de sinal ruim |
| LEG013 | largura empacotada divergente |
| LEG014 | cláusula ainda não suportada (OCCURS em grupos, DEPENDING ON, variantes de REDEFINES, múltiplos valores 88, COMP/USAGE) |
| LEG015 | linha de copybook / PIC / OCCURS / 88 malformados |
| LEG016 | ligação ausente para um campo |
| LEG017 | código de retorno inválido (negativo) |
| LEG018 | transporte ainda não conectado |
| LEG019 | não consigo ler o alvo do transporte |

## 8. Cross-target e conformância

O núcleo portátil (fixed-width, EBCDIC, COMP-3, o adapter, o parser de
copybook) é **byte-idêntico em JVM, Script, JS e Native x86**
(`KofbolParityE2ETest`, 4/4). A ÚNICA divergência conhecida está catalogada
ABERTA como **§734** (o emitter JS devolve `undefined` para a forma do unpack
COMP-3 — JVM/Script/Native corretos; da lane compiler) e a face JS do COMP-3
está documentada como tal no teste de paridade. O transporte de arquivo é
JVM/Script (a matriz §11 documenta o resto).

## 9. Limitações (explícitas, nunca escondidas)

- Uma página EBCDIC (CP037); mais páginas aguardam a decisão Q5 do plano.
- Sem grupos de copybook/REDEFINES-sobre-OCCURS/DEPENDING ON (LEG014).
- Sem atributos de armazenamento COMP/USAGE no import (use `packComp3`
  diretamente).
- Transportes além de `file` estão declarados, não conectados (LEG018).
- Sem protocolo de transação distribuída (§13).

## 10. Caminho de modernização (§8, a forma curta)

1. Importe os copybooks (`kof legacy`) → os contratos Layout.
2. Decodifique os arquivos/filas legados pelos layouts — byte-exact, provado.
3. Envolva o domínio no adapter (`toMap`/`valuesOf`) — as formas COBOL
   param na fronteira.
4. Mapeie os códigos de retorno por `response`/`statusOf` — explicitamente,
   na aplicação.
5. Modernize incrementalmente: cada campo decodificado que vira um valor Kof
   tipado é uma forma legada a menos no domínio.