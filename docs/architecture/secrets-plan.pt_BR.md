[English](secrets-plan.md) | [Português](secrets-plan.pt_BR.md)

# Secrets — `Secret`, `KeyHandle` e redação forçada (plano de design · Estágio 5 / linha 3.6 do tracker)

**Estado:** **COMPLETO — todas as faces pousadas 21/09 (JVM-primeiro, R7).** P1 `Secret`
(`32285136` + `secrets.fromBytes`/`hashCode` de identidade em `04473bbe`), **P2**
redação forçada (runtime `json.encode(Secret)` → `"Secret(*** )"`; compile-time
lint `SECN009` quando `reveal()` alimenta `log.*`/`json.encode`), **P3 `KeyHandle`**
(`secrets.keyFromHex/keyFromPem/keyFromKeystore`, `rotate()` que revoga o antigo →
uso posterior falha `SECN010`, sobrecargas `KeyHandle` em
`crypto.hmacSha256/aesgcm/chacha20` e `jwt.create/verify`). JS/Native/Script/
Android permanecem gap honesto de compile-time `SECN008` (R6). Provas: `SecretE2ETest`
7/7, `KeyHandleE2ETest` 5/5. `secrets.get` manteve o `String` cru legado (congelado
0.2.6) — o caminho tipado é `secrets.of`/`secrets.secret` (sem quebra).
**SEC1 (05/10, `D-MAINT-BATCH-0510`, issue #758):** `secrets.secret(name)` com
env não definida/em branco agora é erro explícito e catchável (`SECN015`), nunca
um `Secret` vazio silencioso; `SecretE2ETest` 9/9.
**Autorizado em bloco** por `DECISIONS.md` §D-SECRETS (mantenedora 21/09).
**Fonte:** linha 3.6 de `docs/architecture/IMPLEMENTATION-UNIVERSAL-PLATFORM.md` (`Secret`/`KeyHandle`
pendentes, Estágio 5) · análise de gap `docs/stdlib/security.md` (linhas "Secrets (env):
INEXISTENTE", "Secrets em logs: SEM PROTEÇÃO") · §kof.security de `docs/bugs-and-gaps/ecosystem-coverage.md`.

## 1. Objetivo

Hoje segredo é só uma `String`. `secrets.get("API_KEY")` devolve o valor cru e ele passa —
invisível ao compilador — para `println`, interpolação, `kof.json`, logs, mensagens de exceção e
campos de entidade. Um vazamento em um desses cinco caminhos é incidente de produção. O Estágio 5
torna o **padrão em nível de tipo** não exportável: um valor que não pode ser impresso,
serializado nem concatenado sem um ato explícito, greppável e auditável.

## 2. Proposta (sintaxe candidata — cada item é um voto próprio)

**P1 — value type `Secret`.**
- `Secret.of(text)`, `Secret.fromBytes(b: Int[])`; `secrets.get(name)` **passa a devolver
  `Secret`** (breaking — o voto; a alternativa é criar `secrets.secret(name)` e congelar o `get`
  como caminho legacy cru).
- `reveal(): String` — ÚNICA exportação; explícita, uma palavra greppável em toda auditoria.
- `redacted(): String` → `"***"` (nunca o tamanho, nunca prefixo — prefixo é orçamento de vazamento).
  Formato fixo em todo target: `Secret(*** )`.
- `equals` em tempo constante (wrap do `security.constantTimeEquals` que já existe); sem `<`/`>`;
  sem `hashCode` do conteúdo (só identidade) para um map não indexar segredos.
- `println(s)` e `"…\(s)"` imprimem a forma REDIGIDA; jamais chamam `reveal`.

**P2 — redação forçada (três camadas).**
- Compile-time: `Secret` + ops de string resolvem para a face redigida; resultado de `reveal()`
  caindo num caminho de log é lint-alertado (família SECN00x, mesmo estilo honesto-código do SEM099).
- Runtime: `kof.json` e as faces de log detectam `Secret` e emitem `Secret(*** )` — valor que
  chega ao serializador por reflection (interop) é redigido na escrita, não pulado.
- Corpus: as páginas de training de `secrets` ganham a linha da matriz "como um Secret IMPRIME"
  (a lição do §388-B: formato de impressão não declarado É divergência — este plano declara o dele).

**P3 — `KeyHandle`.** Uma chave nomeada que nunca expõe bytes ao guest:
- fontes: entrada de keystore / arquivo PEM / vault env (depois, lane separada);
- ops apenas nos algoritmos cripto que já existem (`hmacSha256`, `sign`, `verify`,
  `encryptAesGcm`, `decryptAesGcm`, `encryptChacha20`, `decryptChacha20` — todos ganham overload
  `key: KeyHandle` ao lado da forma crua);
- `rotate()` devolve handle NOVO e revoga o antigo (handle revogado falha com SECN00x no uso,
  não como exceção na semântica Kof);
- `jwt.create(claims, key)` aceita `KeyHandle` direto — o segredo HS256 para de ser `String` na
  mão do chamador.

## 3. Semântica por target (a matriz de paridade, R6)

| target | backing P1 | redação P2 | handles P3 |
|--------|-----------|------------|------------|
| JVM | `String` imutável (honesto: sem zeroing — ver §4) | hook no serializador + override de `toString()` | keystore/PEM via leitor FFI-leve no kof-runtime |
| Script | idem (o interpretador segura o tipo boxed) | idem | idem |
| JS | string JS no host, FORA dos alcançáveis do guest (`reveal` volta pela bridge, nunca fica guardado) | guarda de `JSON.stringify` no host | cripto do host (SubtleCrypto/bind openssl) |
| Native | buffer que PODE ser zerado — API desenhada como superconjunto: wipe() é no-op nos outros | hook no serializador | adiado: gate SECN até existir keystore |

## 4. Não-objetivos (este plano NÃO é)

garantias de zeroing de memória (impossível em String de JVM/JS — dizer isso é o ponto),
integração cliente KMS/vault, injeção de segredo em infra gerenciada pelo Makealive (isso continua
env/file pelo D-MAKEALIVE-CLI; um `Secret` pode ser o valor lido do env), TPM/HSM, agendador de
rotação automática, criptografia de coluna de entidade do ORM (P3×orm precisa de voto próprio).

## 5. Como encaixa no código existente

`KofSecurity.java` já dono da tabela de namespaces (`"secrets", List.of("get", "redact")`) — P1
adiciona o value type `Secret` no typer/lowering (face `Type.ClassType`, sem gramática nova no
parser a menos que a interpolação ganhe forma `reveal`: esse é o item rule-6), P2 engancha
`kof.json` (`KofJson`) e o emissor de log, P3 adiciona overloads sem remover as assinaturas cruas.
Docs tocados quando pousar: `stdlib/security.md(+pt_BR)` (substitui as duas linhas "sem proteção"),
tabelas de assinatura LSP-A, página de training `secrets`, `ecosystem-coverage`.

## 6. Gate e ordem

P1+P2 fecham antes do EXIT GATE 1.0 (fecham uma linha "SEM PROTEÇÃO" da análise de gap); P3 depois
do 1.0. Cada um de P1/P2/P3 recebe: voto rule-6 em `DECISIONS.md` (EN+PT), bump de versão e a
bateria do corpus golden verde nos quatro targets. Este documento não muda NENHUM comportamento; a
linha 3.6 do tracker da lane de auditoria fica 🟡 até o primeiro voto.
