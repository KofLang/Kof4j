[English](PLAN-TLS-HARDENING.md) | [Português](PLAN-TLS-HARDENING.pt_BR.md)

last: none
doing: none — só plano, zero código
next: spike-0-inventário (auditoria SNI/hot-reload/ACME) — só se a mantenedora ordenar promoção por `D-FUTURE-PROMOTION`
location: docs/development/future
state: planned

intent: kof-tls-hardening-beyond-dev-self-signed

**Gate da regra 6:** TLS toca uma **superfície de semântica congelada** (o
contrato do `web.app()` + o comportamento de confiança do `kof.http`,
ambos visíveis ao usuário e críticos para segurança). Este documento é
**só plano, zero código** — nenhuma mudança de compilador, runtime, CLI ou
dependência é feita por ele. A promoção exige decisão da mantenedora
(`D-TLS-HARDEN` ou extensão do `D-SEC.6`) e o fluxo de promoção
(`docs/development/future/README.md` §"Quando mover",
`AGENTS.md` §"Future promotion"): reescrever com `EM DESENVOLVIMENTO`,
estado real + como-finalizar, enfileirar em `roadmap.md` §23, apontar
`docs/status.md` para ele, reivindicar em `DOING.md`.

**Origem:** necessidade externa downstream (o edge do KofCloud serve HTTPS
multi-tenant e hoje não consegue terminar TLS dentro do processo Kof para
sockets crus; `kof-cloud/docs/12-tls.md`, `L2`), mais os gaps medidos na
própria superfície TLS do Kof (abaixo).

---

# Endurecimento de TLS — do self-signed de dev ao TLS de produção (plano, zero código)

## 1. Motivação

O Kof serve HTTPS hoje — em exatamente um alvo, com exatamente um modelo de
certificado, e um cliente que confia em tudo. Esse é o estado honesto:

- **Servidor, só JVM:** `app.listenSecure(port)` gera um certificado
  self-signed descartável (conveniência de dev);
  `app.listenSecure(port, certPem, keyPem)` serve com um par PEM PKCS#8
  fornecido pelo operador (produção). Ambos emitem `WEB002` no Native/JS —
  recusa honesta, nunca downgrade silencioso para texto puro.
- **Cliente, JVM:** o `kof.http` contra URLs `https://` usa um `HttpClient`
  **trust-all** (`KOF_HTTP_CLIENT_INSECURE`): qualquer certificado é aceito,
  verificação de hostname desligada. Correto para servidores self-signed de
  teste; errado como padrão para tráfego de produção.
- **O que não existe em lugar nenhum:** seleção de certificado por SNI (um
  processo servindo vários hostnames em TLS), hot-reload de certificado sem
  restart, e qualquer automação ACME/Let's Encrypt.

O caso de uso não é hipotético: qualquer programa Kof que termina TLS para
mais de um hostname — um reverse proxy, um edge multi-tenant, um roteador
SaaS — precisa de (1) e (2) abaixo. O plano mantém o que existe funcionando
e adiciona as faces de produção que faltam em fatias, cada uma testável
independentemente.

## 2. Baseline medido (o que existe hoje — ancorado em arquivo)

| Superfície | Estado hoje | Onde |
|---|---|---|
| Lowering do `listenSecure` (1-arg dev, 3-arg PEM) | **pousado**: `case "listenSecure"` produz `kof_web_listen_secure` (1 arg `Int`) ou `kof_web_listen_secure_pem` (1 `Int` + 2 `String`); qualquer outra coisa produz `null` (o typer recusa) | `kof-compiler/.../KofWeb.java:163-176` |
| Gap fora da JVM | **honesto**: as duas chamadas TLS mapeiam para `WEB002` em `gapCode()` (`KofWeb.java:217`) | `KofWeb.java:217` |
| Impl do servidor JVM (self-signed) | **pousada**: `kof_web_listen_secure(appId, port)` — `SSLServerSocketFactory`, bind `0.0.0.0`, `setNeedClientAuth(false)`, loop de accept em virtual-thread | `jvm/JvmRuntimeWebServer.java:43-70` |
| Impl do servidor JVM (PEM próprio) | **pousada**: `kof_web_listen_secure_pem(appId, port, certPem, keyPem)` — cert X.509 + chave PKCS#8 (RSA/EC/DSA tentados em ordem), keystore PKCS12 em memória (`"changeit"`), `SunX509` | `jvm/JvmRuntimeWebServer.java:78-106`, contexto em `:108-140` |
| Emissão self-signed | **só dev**: `keytool -genkeypair` (RSA-2048, 365d, `CN=localhost`, `SAN=IP:127.0.0.1,DNS:localhost`), arquivo JKS temporário apagado no `finally` | `jvm/JvmRuntimeWebServer.java:153-175` |
| Confiança do cliente HTTP | **trust-all**: `KOF_HTTP_CLIENT_INSECURE` — `X509TrustManager` de emissores vazios, `setEndpointIdentificationAlgorithm("")`; usado para **toda** requisição `https://` | `jvm/JvmWebHttpRuntime.java:112-132`, seleção em `:136-137` |
| Testes | **7/7 medidos** (`KofWebTlsTest`): rota hello, headers, `kof.http`-sobre-TLS, gap Native, gap JS, cert-próprio serve HTTPS, gap Native do cert-próprio | `KofWebTlsTest.java` (`@Test` × 7) |
| Seleção SNI | **ausente** — zero ocorrências de `SNIServerName`/`SNIMatcher`/`setServerNames` nas fontes do compilador+runtime | (nada a medir) |
| Hot-reload de certificado | **ausente** — o `SSLContext` é construído uma vez por chamada `listenSecure`; nenhum caminho de reload/rotação/watch | (nada a medir) |
| ACME / Let's Encrypt | **ausente** — zero ocorrências fora identificadores não relacionados | (nada a medir) |
| Aviso de produção | `listen`/`listenSecure` sob `KOF_ENV=production` sem `app.security()` avisa no `stderr` | `stdlib-web.md:165-166`, `JvmWebSecurityRuntime.java:268-273` |
| Contrato congelado | `D-SEC.6`: a API de 3 args, requisito de chave PKCS#8, JVM-primeiro, "self-signed é conveniência de dev, não produção" | `docs/development/DECISIONS.md:441-466` |

**Drift de doc conhecido (registrado, não corrigido aqui):**
`docs/stdlib/http.md:325` e `docs/status.md:781` ainda dizem que o
`KofWebTlsTest` tem 5 testes; o arquivo tem 7 métodos `@Test` (as duas faces
de cert-próprio pousaram depois). Corrigir a contagem pertence à fatia de
promoção (atualização de docs, não este plano).

## 3. Princípios de design (congelados para todas as fatias)

1. **Nunca downgrade silencioso.** Uma falha de TLS (PEM ruim, cert
   expirado, hostname SNI desconhecido) recusa alto com diagnóstico nomeado —
   nunca cai para texto puro, nunca serve o certificado errado.
2. **A superfície 1-arg/3-arg existente não muda.** Capacidade nova chega
   como funções/opts novas, nunca reinterpretando o que
   `listenSecure(port)` ou `listenSecure(port, certPem, keyPem)` significam
   (o `D-SEC.6` continua sendo o contrato).
3. **Trust-all continua ferramenta de teste, nunca padrão de produção.**
   Qualquer fatia que introduza verificação real precisa tornar o cliente
   inseguro opt-in e barulhento (gated por env, com aviso), não removê-lo
   (os testes dependem dele).
4. **JVM primeiro, gaps continuam honestos.** Native/JS continuam
   reportando `WEB002` até uma fatia pousar de verdade lá — nenhum stub que
   finja que TLS funciona.
5. **Nunca cripto caseira.** Todo primitivo novo (o JWS do ACME precisa de
   ES256 — já está na árvore para OAuth; qualquer outro) reusa provedores
   JDK/JCA auditados, exatamente como o caminho `SunX509`/PKCS12 atual faz.

## 4. Fatias (ordenadas — cada uma testável independentemente)

**Fatia 0 — spike de inventário (auditoria, zero código).** Re-medir o §2
no momento da promoção (números de linha derivam): confirmar os 7 testes
ainda verdes, confirmar zero SNI/reload/ACME, confirmar o drift de
contagem dos docs, e escrever o estado medido no plano promovido. Este é o
gate de entrada da promoção — nenhum código antes dele.

**Fatia 1 — seleção de certificado por SNI (JVM).** Um processo, vários
hostnames: o servidor guarda um mapa hostname→(`certPem`, `keyPem`)
(construído de argumentos explícitos da API, nunca de varredura de
diretório) e seleciona o certificado a partir do SNI do ClientHello. SNI
desconhecido → recusa do handshake com diagnóstico nomeado (princípio 1),
nunca o cert padrão. Prova RED-first: dois hostnames numa porta servem
seus próprios certs (verificados por fingerprint), um terceiro hostname é
recusado, e o cliente sem SNI recebe o comportamento padrão documentado —
tudo sobre `SSLSocket`s reais no estilo `KofWebTlsTest`.

**Fatia 2 — hot-reload de certificado sem restart (JVM).** Re-ler o par PEM
(mapa SNI ou cert único) sob demanda — chamada explícita
`app.reloadTls()` e/ou file-watch — e trocar o `SSLContext`
atomicamente; conexões em voo mantêm o contexto antigo, novos handshakes
usam o novo. Falha no reload mantém o certificado antigo servindo e
reporta (nunca serve estado meio-carregado). Prova RED-first: serve cert A
→ reload cert B → conexões novas apresentam o fingerprint de B enquanto
uma conexão antiga ainda completa; um arquivo de reposição corrompido não
muda nada e reporta.

**Fatia 3 — cliente HTTP verificador (JVM).** Um caminho `kof.http`
estrito que valida a cadeia contra o trust store da plataforma **com**
verificação de hostname, ao lado do cliente trust-all existente (mantido
para testes). O caminho estrito é o padrão para código novo; o trust-all
exige opt-in explícito que avisa. Prova RED-first: o cliente estrito
aceita um cert de teste com cadeia adequada, rejeita self-signed e rejeita
mismatch de hostname — cada um com diagnóstico nomeado, nenhum silencioso.

**Fatia 4 — cliente ACME DNS-01 (JVM, CA externa).** Automatizar a emissão
para o par PEM que a Fatia 1 consome: fluxo de desafio DNS-01 (o edge é
multi-tenant, HTTP-01 não roteia por hostname pré-verificação), JWS
`ES256` (já na árvore via runtime OAuth — reusar, não adicionar
assinador), armazenamento de chave delegado ao secret store do operador
(nunca persistido em claro pelo Kof). Não-objetivo explícito: nenhum
servidor DNS rodado pelo Kof, nenhuma mágica de listener HTTP-01. Prova
RED-first: emissão contra Pebble (o servidor de teste padrão do ACME) no
CI, depois o par emitido serve via Fatia 1.

**Fatia 5 — paridade Native/JS (só se uma lane assumir).** TLS por alvo
depois do contrato JVM congelado e provado: Native via o TLS de
plataforma de cada arch (nunca reimplementação de cripto empacotada), JS
via a superfície `tls` do host. Cada alvo mantém `WEB002` até sua fatia
pousar — paridade é por fatia (SNI/reload/verify/ACME pousam
independentemente por alvo), nunca flag-day.

## 5. Não-objetivos explícitos

- Nenhuma mudança no contrato 1-arg/3-arg do `D-SEC.6` (só aditivo).
- Sem auth `mTLS`/certificado de cliente neste plano (só TLS de servidor;
  `setNeedClientAuth(false)` continua).
- Sem política TLS-1.3-only, sem DSL de cipher-suite — padrões do JDK,
  documentados.
- Sem monitoramento de certificate transparency, sem gestão de OCSP
  stapling (o JDK trata stapling onde configurado; o plano não adiciona
  knobs).
- Sem ACME HTTP-01/TLS-ALPN-01 (só DNS-01 — pela razão multi-tenant).
- Sem remoção do cliente trust-all de teste (os testes dependem dele; §3.3).
- Sem datas, sem estimativas de esforço (planejar ≠ enfileirar).

## 6. Aceitação (o que fecharia a iniciativa)

Cada fatia fecha na sua prova RED-first (sockets reais, certs reais,
recusas reais — §4). A iniciativa fecha quando as Fatias 0–4 estiverem
verdes na JVM com cobertura E2E estilo `KofWebTlsTest`, o drift de contagem
dos docs corrigido, `backend-parity.md` refletindo o estado por fatia por
alvo, e nenhum caminho novo de downgrade silencioso existir (auditado:
toda falha de TLS no código novo tem diagnóstico nomeado). A Fatia 5 fecha
por alvo, independentemente.

## 7. Referências

- `kof-compiler/src/main/java/dev/kof/compiler/KofWeb.java:163-176`
  (lowering), `:217` (gap WEB002).
- `kof-compiler/src/main/java/dev/kof/compiler/jvm/JvmRuntimeWebServer.java:43-70`
  (self-signed), `:78-140` (PEM próprio), `:153-175` (emissão keytool).
- `kof-compiler/src/main/java/dev/kof/compiler/jvm/JvmWebHttpRuntime.java:112-137`
  (cliente trust-all).
- `kof-compiler/src/test/java/dev/kof/compiler/KofWebTlsTest.java` (7 testes).
- `docs/development/DECISIONS.md:441-466` (D-SEC.6, o contrato congelado).
- `docs/stdlib/stdlib-web.md:165-166,205-217` (aviso de produção, tabela da API).
- `docs/stdlib/http.md:315-325` (seção G12; notar o drift 5-vs-7 na contagem de testes).
- Necessidade downstream: `kof-cloud/docs/12-tls.md`, `L2` (sem TLS em sockets crus).
