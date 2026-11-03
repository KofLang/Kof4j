[English](tls-hardening.md) | [Português](tls-hardening.pt_BR.md)

last: 03/11/2026 — PROMOVIDO para `docs/tls-hardening.pt_BR.md` (+EN) (o modelo de estado dos docs: implemented-validated-decided) — o gate da promoção foi a prova E2E Pebble abaixo
doing: TODAS as camadas definidas pelo plano POUSADAS com prova executada (inventário + SNI + hot-reload + cliente verificador + a cadeia ACME completa + a E2E Pebble: KofAcmeClientTest 4/4 + KofAcmePebbleE2ETest 1/1, KofWebTlsVerifyTest 5/5, KofWebTlsTest 14/14, KofHttpE2ETest 8/8 — família 32/32); o plano está COMPLETO
next: nenhuma — este documento é o CONTRATO da superfície TLS; mudanças seguem `D-SEC.6` (apenas aditivo) e uma decisão da mantenedora
location: docs
state: implemented
dona: 192.168.100.14:0, flatt CLI, sem servidor opencode neste host; porta n/a

intent: kof-tls-hardening-beyond-dev-self-signed

**Gate da regra 6 — SATISFEITO 02/11/2026.** TLS toca uma **superfície de
semântica congelada** (o contrato do `web.app()` + o comportamento de
confiança do `kof.http`, ambos visíveis ao usuário e críticos para
segurança). A mantenedora ordenou a promoção diretamente ("pega tls do
future pra fazer, é urgente"), o que é a decisão da regra 6 para este
plano; o contrato congelado que ele precisa respeitar é o `D-SEC.6`
(apenas aditivo — a superfície `listenSecure` de 1/3 argumentos nunca
muda). O fluxo de promoção foi executado: reescrito `EM DESENVOLVIMENTO`,
enfileirado em `roadmap.md` §23, `docs/status.md` aponta para ele,
reivindicado em `DOING.md`.

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

### Fatia 0 — MEDIDA na promoção (02/11/2026, lane `192.168.100.14:0`)

Re-medido no tip antes de qualquer código (o gate de entrada da promoção):

| Alegação | Medido |
|---|---|
| Contagem de testes do `KofWebTlsTest` | **7** `@Test` (a tabela §2 do plano está certa; o drift 5-vs-7 nos docs é real) |
| Seleção por SNI | **ausente** — 0 arquivos casam `SNIServerName`/`SNIMatcher`/`setServerNames` |
| Hot-reload de certificado | **ausente** — 0 arquivos casam `reloadTls`/`reloadTLS` |
| ACME | **ausente** — o único hit `acme` é `NativeBackend.java:493` (um comentário de mangling JNI, sem relação) |
| Lowering do `listenSecure` | `KofWeb.java:163` (`case "listenSecure"`), gap em `:217` (`WEB002`) |
| Impl do servidor JVM | `JvmRuntimeWebServer.java:43` (self-signed), `:78` (PEM próprio), `:130` PKCS12, `:135` `SunX509`, `:156` keytool |
| Cliente trust-all | `JvmWebHttpRuntime.java:112` (`KOF_HTTP_CLIENT_INSECURE`), `:117` trust manager vazio, `:123` `setEndpointIdentificationAlgorithm("")`, `:137` seleção |
| Drift de docs | `docs/stdlib/http.md:325` e `docs/status.md:783` dizem **5** testes; o arquivo tem **7** |

Os números de linha derivaram um pouco da §2 do plano (ex.: o bloco trust-all é
`:112-137`, não `:112-132`); a substância está inalterada. A fatia 1 pode começar.

### Fatia 1 — POUSADA 02/11/2026 (lane `192.168.100.14:0`, flatt CLI)

Seleção de certificado por SNI na JVM, aditiva sobre o `D-SEC.6`:

- **Superfície Kof:** `web.secureHost(host, certPem, keyPem)` monta um
  descritor apenas-de-compilação; `app.listenSecure(port,
  listOf(secureHost(...), ...))` (o overload de 2 argumentos) serve a lista.
  As superfícies de 1 e 3 argumentos estão inalteradas.
- **Lowering:** o literal de lista é achatado em três `String[]`
  paralelos (hosts/certs/keys) e emitido como
  `kof_web_listen_secure_sni(appId, port, hosts[], certs[], keys[])`; uma
  lista não-literal é recusada com `WEB007` (o conjunto de certificados
  deve ser explícito no ponto da chamada). Native/JS mantêm o `WEB002`
  honesto.
- **Runtime:** um `ServerSocket` PLANO; por conexão o primeiro record TLS
  é lido, o hostname SNI é extraído do ClientHello, e o handshake roda
  num `SSLEngine` sobre o `SSLContext` daquele host (um par PEM cada). SNI
  desconhecido → `HTTP 421` + `WEB007` no stderr, SEM certificado (nunca o
  default). Sem SNI → o PRIMEIRO host configurado (default documentado).
- **Por que SSLEngine:** o caminho `SSLSocket` de servidor do JDK NÃO
  expõe o SNI do ClientHello a um KeyManager (`chooseServerAlias` não
  recebe SNI; só o `chooseEngineServerAlias` do engine recebe), e
  `createSocket(Socket,...)` ignora os streams do Socket passado — o
  handshake tem de ser baseado em engine.
- **Prova (executada):** `KofWebTlsTest` **11/11** (os 7 originais + 4
  novos: seleção por fingerprint por host, recusa alto-falante do
  desconhecido, default sem-SNI = primeiro cert, gap WEB002 no NATIVE);
  `mvn -o -pl kof-compiler -am compile` rc=0; o split do template TLS
  (check_500: `JvmRuntimeWebServer` 744→257 + novo `JvmRuntimeTlsServer`
  506, ambos abaixo de 600).

Batalhas de debug ficam FORA do código mas registradas aqui para a próxima
fatia: no emit, um literal int carregado como String (`KofLoadLiteral.ofInt`
corrige) e `KofNewArray`+`KofArrayStore` (não chamadas `kof_array_*`); no
runtime, um `PushbackInputStream.read(b,off,len)` que drena o pushback e
depois BLOQUEIA esperando o resto — o replay tem de ser
`SequenceInputStream(ByteArrayInputStream, rawStream)` para que o primeiro
`fillNet` não deadlock (o par só envia o próximo vôo depois do nosso
ServerHello).

### Fatia 2 — POUSADA 03/11/2026 (lane `192.168.100.14:0`, flatt CLI)

Hot-reload de certificado sem reinício, aditivo sobre o `D-SEC.6`:

- **Superfície Kof:** `app.reloadTls(certPem, keyPem)` rearma um servidor
  de cert único com um NOVO par PEM; `app.reloadTls(listOf(secureHost(...),
  ...))` substitui o mapa SNI INTEIRO. Sem reload no servidor dev de 1
  argumento (conveniência de dev, não produção).
- **Atomicidade:** o estado TLS por app (mapa de contextos + host default)
  vive num `AtomicReference` (`TLS_HOLDERS`); a troca é uma escrita de
  referência '—' conexões em curso mantêm o engine montado no seu
  handshake, novos handshakes leem o novo estado. O listen PEM de 3
  argumentos agora usa o MESMO caminho engine (ServerSocket plano +
  handshake por conexão), que é o que torna a troca possível.
- **Honestidade da falha:** um reload que não consegue montar o novo
  contexto lança `WEB007` (catável como String Kof '—' `catch (String e)`)
  e o estado ANTIGO segue servindo; nunca um estado meio-carregado.
- **Prova (executada):** `KofWebTlsTest` **14/14** '—' os 11 originais +
  `reloadSwapsTheCertificateAtomically` (conexão em curso aberta ANTES do
  reload completa enquanto conexões NOVAS apresentam o fingerprint de B),
  `reloadFailureKeepsServingAndReports` (a rota captura `WEB007` e o
  fingerprint permanece A), `sniReloadSwapsTheWholeMap` (comportamento do mapa
  pós-fatia-1 travado); `mvn -o -pl kof-compiler -am compile` rc=0; os
  aliases de métodos PortuKof regenerados para o braço novo do dispatcher
  (13 membros WEB_APP).

### Fatia 3 — POUSADA 04/11/2026 (lane `192.168.100.14:0`, flatt CLI)

O cliente HTTP verificador, JVM primeiro:

- **Estrito por default:** as requisições https:// do `kof.http` agora
  validam a cadeia contra o trust store da plataforma COM verificação de
  hostname (`HttpClient.newHttpClient()` faz ambos). O cliente trust-all
  fica, mas apenas atrás do opt-in (os testes existentes o usam
  explicitamente).
- **O opt-in:** `http.insecure()` (0 argumentos, face JVM) devolve o
  https:// ao cliente trust-all e avisa uma vez no stderr
  (`TLS-TRUST-ALL: ... test/dev only`). Em JS/Native o opt-in é uma recusa
  honesta em compile (HTTP002): o stack fetch do JS verifica por default e a
  face https do Native recusa '—' nenhum tem superfície de bypass.
- **Diagnósticos nomeados:** uma rejeição estrita é catável como String
  Kof: `TLS-VERIFY: certificate rejected for <url> '—' <causa> (use
  http.insecure() to opt into trust-all for test/dev)`.
- **Prova (executada):** `KofWebTlsTest` **19/19** '—' os 14 anteriores +
  `strictClientRejectsSelfSigned` (TLS-VERIFY),
  `strictClientAcceptsChainedCaCert` (uma CA de teste REAL via keytool:
  genkeypair(bc) '→' certreq '→' gencert(-rfc) '→' import no keystore do
  servidor como trust store do cliente, `-Djavax.net.ssl.trustStore=...`),
  `strictClientRejectsHostnameMismatch`, `insecureOptInWarnsAndTrustsSelfSigned`,
  `insecureGapOnNative` (HTTP002); `KofHttpE2ETest` 8/8 (a face http://
  intocada); `mvn -o -pl kof-compiler -am compile` rc=0.

Batalhas do keytool (para os docs da próxima fatia): o keytool pede a senha
do keystore quando ela não é passada (um filho PENDURADO, não um erro '—'
passe sempre `-storepass`); `-ext bc=ca` é INVÁLIDO (a grafia válida é
`-ext bc`); `-gencert -outfile` escreve DER sem `-rfc`; o PEM emitido está no
charset do SISTEMA (decodifique com `Charset.defaultCharset()`, nunca
`Files.readString`).

### Fatia 4 — INCREMENTO 1 POUSADO 04/11/2026 (lane `192.168.100.14:0`, flatt CLI)

O núcleo do cliente ACME, primeiro incremento (a primitiva JWS ES256 sobre a
qual o fluxo DNS-01 completo se constrói):

- **Superfície Kof:** `acme.jwsEs256(accountKeyPem, headerJson, payloadJson)`
  assina UM JWS RFC 7515 com a chave EC P-256 do operador:
  `b64url(header).b64url(payload).b64url(ES256(r||s))`. O armazenamento da
  chave permanece DELEGADO ao operador (o PEM chega do secret store dele;
  o Kof nunca o persiste '—' princípio 3.5 do plano). O namespace é
  JVM-primeiro (JVM/Script/Android); JS/Native mantêm o gap honesto
  `WEB008` em compile (sem face ACME lá, nunca um stub).
- **Conversão ECDSA DER'→'JWS** (RFC 7518 §3.4): a assinatura JCA
  `SHA256withECDSA` é DER; o runtime separa r/s para a forma JWS fixa de
  64 bytes, conforme a RFC.
- **Prova (executada):** `KofWebTlsVerifyTest` **6/6** '—' os 5 anteriores +
  `acmeJwsEs256SignsAndVerifiesRoundTrip` (um par EC P-256 gerado pelo
  keytool; o JWS é verificado contra a chave PÚBLICA do operador após
  reconverter r||s para DER); `KofWebTlsTest` **14/14**; `KofHttpE2ETest`
  8/8; `mvn -o -pl kof-compiler -am compile` rc=0.
- **Encanamento do runtime descoberto no caminho:** uma função nova
  `kof_acme_*` precisa do prefixo `kof_acme_` em `JvmRuntime.hasRuntimeFn`
  (senão a call cai fora da whitelist do emitter e NADA é emitido '—' a
  classe helper nunca é gerada e o processo filho morre
  NoClassDefFoundError); um descriptor de call; um descriptor de return; e a
  tabela de descriptors passou de 600 linhas '—' a família web/http/acme
  foi para `JvmRuntimeCallDescriptorsWeb` (SRP: assinaturas de comunicação).
- **Incrementos restantes (registrados honestamente):** o fluxo DNS-01
  (directory '→' newNonce '→' newAccount '→' newOrder '→' authz '→' TXT
  keyauth '→' gatilho do desafio '→' poll '→' finalize(CSR) '→' download do
  cert) e a prova E2E dele contra Pebble. O host NÃO tem Go/Pebble hoje '—'
  a E2E destrava quando o CI providenciar Pebble (condição ambiental, como
  a toolchain riscv64); o fluxo do protocolo pode ser provado primeiro
  contra um stub ACME local-de-teste.

### Fatia 4 '—' INCREMENTO 2 POUSADO 04/11/2026 (lane `192.168.100.14:0`, flatt CLI)

A camada de ACCOUNT do ACME (`acme.newAccount(directoryUrl, accountKeyPem,
contact): String`), provada contra um stub ACME LOCAL-DE-TESTE (Pebble não
está neste host '—' o stub implementa os três endpoints que a camada de
account precisa):

- **Fluxo:** GET directory '→' parse de newNonce/newAccount (lookup JSON
  plano por quote-como-char-code '—' o text block do template NÃO carrega
  NENHUM backslash: os escapes de \" do template chegam QUEBRADOS ao helper
  gerado) '→' newNonce GET captura o `Replay-Nonce` '→' newAccount POST (JWS:
  header protegido `alg`/`jwk`/`nonce`/`url`; payload
  `termsOfServiceAgreed` + `contact`) '→' 201 + `Location` = o kid da conta.
- **A JWK:** a chave pública é DERIVADA da privada '—' o PKCS#8 EC do
  keytool NÃO carrega o ponto embutido (67 bytes, o [1] da RFC 5915
  ausente), então o runtime roda um double-and-add explícito na P-256 (o
  JDK não expõe derivação pública); o `KeyFactory` do JDK então
  VALIDA que o ponto derivado está na curva ao montar o `ECPublicKey`.
- **Prova (executada):** `KofWebTlsVerifyTest` **7/7** '—'
  `acmeNewAccountReturnsTheKid` assegura o kid, a forma do JWS capturado
  (3 partes), a `jwk` embutida, o `stub-nonce-1` capturado no header
  protegido, o acordo de ToS e o contact; `KofWebTlsTest` 14/14;
  `KofHttpE2ETest` 8/8; `mvn -o -pl kof-compiler -am compile` rc=0.
- **Incrementos restantes:** newOrder + authz + o valor TXT do keyauth +
  gatilho do desafio + poll + finalize(CSR) + download do cert (incremento 3),
  depois a E2E Pebble (destrava quando o CI providenciar Pebble).

### Fatia 4 '—' INCREMENTO 3 POUSADO 04/11/2026 (lane `192.168.100.14:0`, flatt CLI)

A camada ORDER do ACME, provada contra o mesmo stub ACME local-de-teste
estendido com /newOrder + /order + /authz + /challenge:

- **Superfície Kof:** `acme.newOrder(kid, accountKeyPem, directoryUrl,
  domain): String` (a URL do order), `acme.dns01Txt(kid, accountKeyPem,
  directoryUrl, domain): String` (o TXT DNS-01:
  base64url(SHA-256(token "." thumbprint))) e `acme.challenge(kid,
  accountKeyPem, directoryUrl, domain): String` (a URL da autorização após
  disparar o desafio). Os três fazem POST como JWS com o KID no header
  protegido (sem JWK '—' a conta existe), cada um buscando um nonce FRESCO
  no endpoint newNonce do directory.
- **Prova (executada):** `KofAcmeClientTest` **3/3** (os testes ACME saíram
  do `KofWebTlsVerifyTest` para os ratchets check_500/hygiene '—' 8/8 + 5/5 +
  3/3 entre as três classes) '—' o teste calcula o TXT ESPERADO de forma
  independente (double-and-add próprio + JSON canónico RFC 7638) e o valor
  do runtime bate byte-a-byte; as URLs de order/authz são servidas pelo stub
  e o POST do desafio é capturado; `KofWebTlsTest` 14/14; `KofHttpE2ETest`
  8/8; `mvn -o -pl kof-compiler -am compile` rc=0.
- **Split do template do runtime:** a camada ACME foi para um template
  próprio `JvmAcmeRuntime` (montado após `JvmRuntimeTlsServer`; o runtime
  http estava em 720 linhas após o incremento 2 '—' o check_500 forçou o
  split).
- **Incremento restante:** finalize(CSR) + download do cert + a E2E Pebble
  (destrava quando o CI providenciar Pebble).

### Fatia 4 '—' INCREMENTO 4 POUSADO 05/11/2026 (lane `192.168.100.14:0`, flatt CLI)

A camada FINALIZE '—' o cliente emite o par de certificado de ponta a ponta
(contra o stub estendido com /order/9/finalize + o flip do poll + /cert):

- **Superfície Kof:** `acme.finalize(kid, accountKeyPem, directoryUrl,
  domain): String` '—' gera uma chave de cert NOVA e efêmera (EC P-256),
  monta o CSR PKCS#10 (RFC 2986: subject CN=domain, o SPKI do par efêmera,
  assinado SHA256withECDSA '—' DER montado pelos escritores DER mínimos do
  próprio runtime), POST finalize `{csr: b64url}`, poll do order até
  `valid` (30 tentativas x 200 ms), baixa o certificado e devolve
  `certPem\nkeyPem` '—' o par que o `listenSecure` da fatia 2 consome.
- **Prova (executada):** `KofAcmeClientTest` **4/4** '—' o par devolvido tem
  o PEM de CERTIFICATE primeiro e o de PRIVATE KEY segundo, e o stub captura
  EXATAMENTE UM POST de finalize carregando o CSR; `KofWebTlsTest` 14/14;
  `KofWebTlsVerifyTest` 5/5; `KofHttpE2ETest` 8/8; `mvn -o -pl kof-compiler
  -am compile` rc=0.
- **O plano TLS agora está COMPLETO exceto a E2E Pebble** (destrava quando o
  CI providenciar Pebble '—' sem Go neste host; ambiental, como a toolchain
  riscv64). Toda camada que o plano define pousou com prova executada:
  inventário + SNI + hot-reload + cliente verificador + a cadeia ACME
  JWS/account/order/finalize.

### Fatia 4 — INCREMENTO 5 POUSADO 03/11/2026 (lane `192.168.100.14:0`, flatt CLI) — a E2E PEBBLE

Emissão real contra o servidor ACME de teste padrão, construído NESTE HOST
no fim das contas (o baseline "sem Go" estava errado '—' o Go 1.23.4
compila Pebble 1.0.1 + pebble-challtestsrv em /tmp): Pebble em
`https://localhost:14000/dir` (o leaf do localhost carrega SAN
DNS:localhost,pebble + IP:127.0.0.1; a âncora de confiança é a minica,
`/tmp/pebble-trust.p12`), challtestsrv em :8053 (DNS, Pebble
`-dnsserver`) + :8055 (gerência).

- **O fluxo (`pebbleIssuesThePairAndItServes`):** face 1 `newAccount`
  contra o Pebble (um kid REAL: `https://localhost:14000/my-account/...`);
  depois as faces 2-6 correm em UM processo '—' a cache de ordens
  (`KOF_ACME_ORDER_BY_DOMAIN`) é por JVM, e processos SEPARADOS criavam
  ordens DIFERENTES cujos tokens nunca casavam com o TXT publicado (o
  diagnóstico da authz INVALID anterior; os 12 valores TXT velhos no log
  do challtestsrv eram a evidência): `newOrder` → `dns01Txt` → o OPERADOR
  publica o TXT via `http.post` plano para
  `http://127.0.0.1:8055/set-txt` (o papel de API do provedor DNS; o
  runtime acme continua sem tocar DNS) → `challenge` → `finalize` → o par
  baixado; face 7: o par emitido serve via `listenSecure` e responde
  "Hello Pebble" sobre HTTPS.
- **Correções medidas contra o servidor REAL (cada uma uma lição de
  contrato real):** o payload do gatilho do desafio é `{}` (RFC 8555
  §7.5.1 '—' o SERVIDOR deriva o keyAuthorization; o Pebble recusa o campo
  legacy no modo strict); o finalize NÃO re-dispara um desafio que não
  está mais pending (o Pebble devolve 400 "Cannot update challenge with
  status processing"); a URL de finalize vem da PRÓPRIA ordem
  (`/finalize-order/<id>` '—' não `<order>/finalize`); o CSR carrega a
  extensão SAN via extensionRequest (o Pebble exige
  `parsedCSR.DNSNames` == os nomes da ordem '—' CN-only é recusado), na
  forma de atributo do Go/OpenSSL `SET{SEQUENCE(Extension)}` (a Extension
  nua no SET quebra o parseCSRExtensions), com `ecdsa-with-SHA256`
  (RFC 5758, sem parâmetros NULL) e comprimentos DER MÍNIMOS (o
  `BigInteger.toByteArray` é SIGNED e emite um zero à esquerda supérfluo
  para comprimentos 128-255 '—' o parser ASN.1 do Go recusa).
- **Prova (executada):** `KofAcmePebbleE2ETest` **1/1** (com `KofAcmeClientTest` 4/4) '—' o log do Pebble
  mostra `authz ... set VALID by completed challenge ...` e `Issued
  certificate serial 0dca413a13143cfa`; `KofWebTlsTest` 14/14;
  `KofWebTlsVerifyTest` 5/5; `KofHttpE2ETest` 8/8 '—' a família é
  **32/32**; `mvn -o -pl kof-compiler -am compile` rc=0. O harness vive em
  /tmp (`/tmp/start-acme-servers.sh` + `/tmp/pebble-config.json` +
  `/tmp/pebble-trust.p12`); o teste pula HONESTAMENTE (assumeTrue) quando
  a trust store não existe '—' um CI que providencie Pebble ganha a E2E de
  graça.

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
