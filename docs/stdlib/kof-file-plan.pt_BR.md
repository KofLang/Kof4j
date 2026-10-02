[English](kof-file-plan.md) | [Português](kof-file-plan.pt_BR.md)

# Plano Estratégico — `kof.file`

> **Estado (28/09): CONCLUÍDO — promovido `future/` → `docs/development/` por `D-KOF-FILE-GO` + `D-FUTURE-PROMOTION` (mantenedora), implementado library-first (`D-KOF-FIRST-IMPL`) e movido para `docs/stdlib/` (regra dos 3 estados).** Escopo promovido landou: **Fase 1** já existe como `kof.io`; **Fase 2 streaming** (`FileStream`/`TextStream`/`CsvReader`+`CsvWriter`/`JsonLinesReader`+`JsonLinesWriter`/`XmlReader` incl. namespaces); **Fase 3 configuração** (`Ini`/`Toml`/`Yaml`) — tudo pure-Kof `libs/file/`, goldens na JVM + Native x86-64 + riscv64 (qemu) + Script, JS `IOJS001`; §537/§538 catalogados. **Adiado (NÃO é trabalho atual):** Fase 4 documentos (Markdown/HTML/PDF), Fase 5 binários/containers (imagens/archives) e Fase 6 evolução — codecs pesados são pacotes oficiais (R1/R9); precisam de nova `D-FUTURE-PROMOTION` para abrir.** Re-escopado na promoção: **a Fase 1 (File/Path/Text/Binary) já existe como `kof.io`** (`docs/stdlib/IO.pt_BR.md` — `File`/`Path`/`Directory` + `readText`/`writeText`/`appendText`/`readBytes`/`writeBytes`/`readRange`/`size`/`delete`/`copyTo`/`moveTo`/`modifiedTime`/`isSymlink`); a única lacuna aberta da Fase 1 é **Streaming**. **Fatia 1 LANDED 28/09:** biblioteca pure-Kof `libs/file/` (`FileStream` leitor por chunks sobre `kof.io.readRange` + `copyStream` cópia de memória constante), provada na JVM por `FileLibraryE2ETest` 2/2 — sem sintaxe nova, sem mudança no compilador (library-first, `D-KOF-FIRST-IMPL`). **Fatia 2 LANDED 28/09:** medida na JVM **+ Native x86-64 + riscv64/aarch64 sob qemu + Script** contra o mesmo golden; **`readRange`/`copyTo`/`moveTo`/`modifiedTime`/`isSymlink` no JS são lacuna honesta de compile time `IOJS001`** (o runtime GraalJS não exporta binding — antes o programa morria em runtime com `SyntaxError`) — `FileLibraryE2ETest` 7/7. **Fatia 2.1 LANDED 28/09:** `TextStream` — leitor **streaming de texto/linhas UTF-8** sobre o mesmo modelo de chunks, decodificando sequências multi-byte cortadas entre chunks (uma sequência nunca é emitida como bytes quebrados; uma não terminada no EOF → U+FFFD), aceitando `\n` e `\r\n`, memória limitada a um chunk mais a linha atual; golden medido na JVM + Native x86-64 + riscv64 (qemu) + Script, mais um round-trip astral (não-BMP) na JVM/Script — **não-BMP é divergência medida no Native** (§537, o armazenamento UTF-8 não tem representação WTF-8 de surrogate). **Fatia 2.2 LANDED 28/09:** `CsvReader` — registros **CSV/TSV** streaming (estilo RFC 4180: campos entre quotes com delimitadores/newlines embutidos, escapes `""`, `\n`/`\r\n`, TSV por delimitador tab) como máquina de estados por caractere sobre o `TextStream`, limitado a um chunk mais o registro atual; golden medido na JVM + Native x86-64 + riscv64 (qemu) + Script — `CsvReaderE2ETest` 15/15 (leitor e escritor). `CsvWriter` (fatia 2.2, mesmo commit) completa o par: o primeiro `writeRow` trunca e os demais apendam, citando um campo só quando ele tem o delimitador/quote/newline/CR (quotes duplicadas), e o leitor faz round-trip da saída do escritor. **Fatia 2.3 LANDED 28/09:** `JsonLinesReader`/`JsonLinesWriter` (`libs/file/JsonLines.kf`) — a costura `kof.json` ↔ stream: um documento JSON por linha não-vazia sobre o `TextStream`, com o chamador decodificando num `json.decode<T>` concreto; o leitor fica sem tipo porque `json.decode<T>` sobre um parâmetro de tipo ABERTO é o defeito do compilador **§538** (compila limpo, emite `checkcast T`/`kof_json_decode_T`, falha mascarada como a mensagem do launcher JavaFX). Golden medido na JVM + Native x86-64 + riscv64 (qemu) + Script (records decodificam na JVM; riscv64 decodifica arrays — `kof_json_find_value` ausente do runtime cross), JS `IOJS001` — `JsonLinesE2ETest` 8/8. **Fatia 2.4 LANDED 28/09:** `XmlReader` (`libs/file/Xml.kf`) — leitor XML streaming pull-style, não-validante, sobre o `TextStream` (`next(): XmlEvent?` com kinds `start`/`end`/`empty`/`text`, atributos, entidades predefinidas + numéricas, CDATA, comentários, DOCTYPE com subset interno, pilha de elementos validada); subconjunto documentado (prefixos de namespace preservados verbatim, entidades do DOCTYPE não resolvidas, CDATA não decodificado), entrada malformada lança. Golden medido na JVM + Native x86-64 + riscv64 (qemu) + Script, JS `IOJS001` — `XmlReaderE2ETest` 8/8. **Fatia 2.5 LANDED 28/09:** resolução de namespace — declarações `xmlns`/`xmlns:prefix` com escopo, `localName`/`namespaceUri`/`attributeNamespaces` no `XmlEvent` (`name` segue verbatim, aditivo sobre a 2.4), `xml`/`xmlns` predefinidos impostos, prefixo não ligado lança; golden + dois casos de erro na JVM + Native x86-64/riscv64 + Script — `XmlReaderE2ETest` 14/14. **Fatia 3.1 LANDED 28/09:** `Ini` (`libs/file/Ini.kf`) — leitor de config da Fase 3: seções/chaves globais, separadores `=`/`:`, comentários de linha inteira `;`/`#`, valores entre quotes, duplicatas last-wins, e erros explícitos para entrada malformada; lista ordenada consultada por `get`/`has`/`keysOf`. Golden medido na JVM + Native x86-64 + riscv64 (qemu) + Script, JS `IOJS001` — `IniReaderE2ETest` 7/7. **Fatia 3.2 LANDED 28/09:** `Toml` (`libs/file/Toml.kf`) — leitor de config da Fase 3: comentários, chaves pontuadas/entre quotes, headers `[table]`, strings basic/literal com escapes, inteiro (separador `_`)/float/boolean e arrays de escalares de uma linha; tabelas achatadas numa chave pontuada canônica (sem mapas aninhados) com acessores tipados `getString`/`getInt`/`getDouble`/`getBool`/`getArray`/`kindOf`; linhas duplicadas/malformadas e as formas não suportadas (`[[array of tables]]`, inline tables, arrays aninhados) lançam `String` explícitos. Golden medido na JVM + Native x86-64 + riscv64 (qemu) + Script, JS `IOJS001` — `TomlReaderE2ETest` 11/11. **Fatia 3.3 LANDED 28/09:** `Yaml` (`libs/file/Yaml.kf`) — leitor de config da Fase 3 para block mappings e block sequences de escalares: comentários, aninhamento por indentação de espaços, escalares plain/entre aspas simples/duplas com escapes, boolean/null (`null`/`~`)/inteiro/float, sequências indexadas (`key.0`…), achatadas numa chave pontuada canônica com acessores tipados; linhas duplicadas/com-tab/malformadas e as formas não suportadas (block scalars, flow collections, sequências de mappings, sequências aninhadas, anchors/tags) lançam `String` explícitos. Golden medido na JVM + Native x86-64 + riscv64 (qemu) + Script, JS `IOJS001` — `YamlReaderE2ETest` 11/11. **Configuração da Fase 3 (INI + TOML + YAML) completa.** Gate de boundary R1 — codecs pesados (PDF, imagens, archives) pertencem à camada de **pacotes oficiais**, não à stdlib base (`scripts/check_stdlib_boundary.sh` + `scripts/stdlib_boundary.txt`); interop-first R9 — ZXing/PDFBox/imageio/JCA, nunca reimplementar. Os exemplos do plano usam `let` (idioma falso — ver `training/anti-patterns/fake-idioms.md`); a sintaxe real é `var`/`val`.

## Estado real (28/09)

| Face | Estado | Onde |
|------|--------|------|
| File / Path / Text / Binary | **JÁ EXISTE como `kof.io`** (não `kof.file`) | `docs/stdlib/IO.pt_BR.md`; `KofIo.java`; `JvmRuntimeIo.java` |
| Streaming (leitura por chunks + cópia de memória constante) | **FATIA 1 LANDED** (provada na JVM) | `libs/file/FileStream.kf`; `FileLibraryE2ETest` 2/2 |
| Streaming no Native | **FATIA 2 MEDIDA — golden em x86-64 + riscv64/aarch64** (qemu) | `FileLibraryE2ETest.streamsOnNative*` |
| Streaming no JS | **FATIA 2 — lacuna honesta de compile time `IOJS001`** (sem binding no host JS; antes era `SyntaxError` em runtime) | `ExpressionBuiltinInstanceCalls.lowerIo`; `DomainGapCodesTest.ioReadRangeOnJsIsIojs001` |
| Streaming no Script | **FATIA 2 MEDIDA — golden** (o interpretador tem o `readRange` real) | `FileLibraryE2ETest.streamsOnScript` |
| Texto streaming (linhas UTF-8) | **FATIA 2.1 LANDED** (JVM + Native x86-64/riscv64 + Script; astral = divergência Native não-BMP §537) | `libs/file/TextStream.kf`; `FileLibraryE2ETest.textStream*` |
| CSV / TSV (registros streaming) | **FATIA 2.2 LANDED** (JVM + Native x86-64/riscv64 + Script; JS `IOJS001`) | `libs/file/Csv.kf`; `CsvReaderE2ETest` 15/15 (leitor + escritor) |
| Ponte JSON | **FATIA 2.3 LANDED** como JSON Lines/NDJSON (`libs/file/JsonLines.kf`); `kof.json` `encode`/`decode` reusados, fluxo tipado bloqueado por §538 | `JsonLinesE2ETest` 8/8 |
| XML (pull streaming + namespaces) | **FATIAS 2.4/2.5 LANDED** (JVM + Native x86-64/riscv64 + Script; JS `IOJS001`) | `libs/file/Xml.kf`; `XmlReaderE2ETest` 14/14 |
| INI (config) | **FATIA 3.1 LANDED** (JVM + Native x86-64/riscv64 + Script; JS `IOJS001`) | `libs/file/Ini.kf`; `IniReaderE2ETest` 7/7 |
| TOML (config) | **FATIA 3.2 LANDED** (JVM + Native x86-64/riscv64 + Script; JS `IOJS001`) | `libs/file/Toml.kf`; `TomlReaderE2ETest` 11/11 |
| YAML (config) | **FATIA 3.3 LANDED** (JVM + Native x86-64/riscv64 + Script; JS `IOJS001`) | `libs/file/Yaml.kf`; `YamlReaderE2ETest` 11/11 |
| documentos / archives | **ADIADO** — não promovido; codecs pesados = pacotes oficiais (R1/R9) ou nova `D-FUTURE-PROMOTION` | — |

## Como foi concluído (28/09)

1. Streaming fatia 2 — **LANDED 28/09**: `FileStream` golden na JVM + Native x86-64 + riscv64/aarch64 (qemu) + Script; o JS recusa em compile time com `IOJS001` (o host não tem primitiva de leitura parcial — nunca fallback de arquivo inteiro).
2. Texto streaming fatia 2.1 — **LANDED 28/09**: `TextStream` leitor de linhas UTF-8 sobre `FileStream` (remontagem multi-byte entre chunks, `\n`/`\r\n`, U+FFFD no rabo não terminado).
3. CSV/TSV fatia 2.2 — **LANDED 28/09**: `CsvReader` parser de registros por caractere sobre o `TextStream` (quotes com delimitadores/newlines embutidos, escapes `""`, TSV tab) + `CsvWriter` codificador inverso (o primeiro write trunca, depois apenda; cita só quando necessário) fazendo round-trip pelo leitor. **Fatia 2.3 LANDED 28/09:** `JsonLinesReader`/`JsonLinesWriter` — a costura `kof.json` ↔ stream (um documento por linha não-vazia, tipado pelo chamador); JSON streaming tipado dentro da biblioteca é bloqueado por §538, então a costura é sem tipo por desenho. **Fatia 2.4 LANDED 28/09:** `XmlReader` — pull reader não-validante (subconjunto documentado: elementos/atributos/texto/vazios, entidades predefinidas+numéricas, CDATA, comentários, DOCTYPE, pilha validada; prefixos preservados verbatim), malformado lança. **Fatia 2.5 LANDED 28/09:** resolução de namespace (declarações com escopo, `localName`/`namespaceUri`/`attributeNamespaces`, erro de prefixo não ligado; `name` verbatim, aditivo). **Fatia 3.1 LANDED 28/09:** `Ini` — leitor de config da Fase 3 (seções/global, separadores `=`/`:`, comentários `;`/`#`, valores entre quotes, duplicatas last-wins, erros explícitos de linha malformada), golden em todos os alvos. **Fatia 3.2 LANDED 28/09:** `Toml` — leitor de config da Fase 3 (comentários, chaves pontuadas/entre quotes, headers `[table]`, strings/escapes, inteiro/float/boolean, arrays de escalares de uma linha; tabelas achatadas numa chave pontuada canônica com acessores tipados; diagnósticos explícitos para duplicado/malformado e formas não suportadas), golden em todos os alvos + casos de erro. **Fatia 3.3 LANDED 28/09:** `Yaml` — block mappings + sequências de escalares (indentação por espaços, escalares plain/entre quotes, escapes, boolean/null, inteiro/float, sequências indexadas; chaves pontuadas achatadas com acessores tipados; diagnósticos explícitos). **Configuração da Fase 3 completa (INI + TOML + YAML).** Concluído em 28/09; documentos (Markdown/HTML/PDF) e binários/containers ficam adiados (pacotes oficiais R1/R9 ou nova promoção).
4. Adiado — documentos (Markdown/HTML/PDF), binários/containers e evolução; cada uma precisa da sua própria fatia promovida; codecs pesados como pacotes oficiais (R1, R9).
5. Cada fatia: teste RED-first + docs (`docs/stdlib/IO.pt_BR.md`) + zero regressão da stdlib.


## Objetivo

Criar o módulo nativo `kof.file`, responsável por fornecer ao Kof uma API unificada para **criação, leitura, escrita, transformação, streaming e manipulação de arquivos e formatos de dados/documentos**.

`kof.file` deve evoluir para uma camada fundamental da stdlib do Kof, capaz de trabalhar com arquivos de texto, dados estruturados, documentos, arquivos binários, streams e formatos amplamente utilizados por aplicações reais.

A premissa é:

> Kof deve ser capaz de trabalhar nativamente com dados e arquivos reais sem obrigar o desenvolvedor a montar manualmente uma coleção de bibliotecas externas para cada formato comum.

---

# REGRA FUNDAMENTAL — NÃO INVENTAR SINTAXE

Antes de implementar qualquer coisa:

1. Leia a gramática atual do Kof.
2. Leia exemplos reais existentes no repositório.
3. Identifique a sintaxe atualmente suportada para:

   * variáveis;
   * funções;
   * tipos;
   * imports;
   * loops;
   * lambdas;
   * tratamento de erros;
   * recursos;
   * streams;
   * objetos;
   * coleções.
4. Use exclusivamente construções válidas do Kof.

### IMPORTANTE

Kof utiliza `var`.

Não utilizar:

```text
let
const
```

Não importar sintaxe de JavaScript, Kotlin, TypeScript ou qualquer outra linguagem apenas porque ela é familiar.

Não inventar APIs ou construções sintáticas para ilustrar a implementação.

Todos os exemplos deste plano são **conceituais** e devem ser adaptados à sintaxe real do Kof antes de serem incorporados à documentação ou aos testes.

O objetivo é implementar **Kof**, não transformar Kof em uma variante de JavaScript.

---

# 1. Princípios

## 1.1 API unificada

Formatos diferentes devem seguir conceitos comuns quando existir uma abstração real compartilhável.

Conceitualmente, a API pode permitir operações como:

```kof
var file = File.open("users.json")
var data = file.read()
```

e posteriormente interpretar os dados:

```kof
var json = data.json()
```

A sintaxe acima deve ser validada contra a gramática atual antes de ser usada no código real.

A API final deve seguir os padrões já existentes na stdlib.

---

# 2. Arquivo não é formato

Separar claramente:

```text
filesystem
    ↓
file
    ↓
bytes / text / stream
    ↓
format
```

JSON, XML, CSV, PDF e outros formatos não devem ser conceitualmente acoplados ao filesystem.

O mesmo conteúdo deve poder ser obtido de:

* arquivo;
* memória;
* stdin;
* HTTP;
* socket;
* banco;
* stream;
* outro recurso.

Evitar APIs excessivamente acopladas a arquivos específicos de cada formato quando uma abstração de dados/stream puder ser reutilizada.

---

# 3. Arquitetura

Projetar `kof.file` em camadas.

```text
kof.file
│
├── File
│   ├── open
│   ├── create
│   ├── read
│   ├── write
│   ├── append
│   ├── copy
│   ├── move
│   ├── delete
│   └── metadata
│
├── Path
│
├── Stream
│   ├── input
│   ├── output
│   ├── reader
│   └── writer
│
├── Text
│
├── Binary
│
├── Data
│   ├── JSON
│   ├── XML
│   ├── CSV
│   └── outros formatos estruturados
│
└── Document
    ├── PDF
    ├── HTML
    ├── Markdown
    └── outros
```

A estrutura final deve respeitar a arquitetura real do Kof.

Não criar essa árvore literalmente se o projeto já possuir uma organização melhor.

---

# 4. Operações básicas de filesystem

Fornecer operações fundamentais:

* abrir;
* criar;
* ler;
* escrever;
* append;
* copiar;
* mover;
* excluir;
* verificar existência;
* obter tamanho;
* obter metadata;
* obter timestamps;
* verificar tipo;
* trabalhar com diretórios;
* listar diretórios;
* criar diretórios;
* remover diretórios;
* trabalhar com paths.

Exemplo conceitual, usando a sintaxe correta do Kof:

```kof
var file = File.open("data.txt")
var content = file.read()
```

A implementação real deve seguir as APIs e convenções já existentes.

Quando o modelo de gerenciamento de recursos do Kof permitir, preferir gerenciamento automático de recursos em vez de exigir `close()` manual.

---

# 5. Path

Criar uma abstração adequada para paths.

Deve considerar:

* separadores;
* paths absolutos;
* paths relativos;
* normalização;
* resolução;
* parent;
* filename;
* extension;
* existência;
* diretório;
* arquivo;
* symlink quando suportado.

Não manipular paths críticos usando concatenação manual de strings quando isso puder gerar comportamento incorreto entre plataformas.

---

# 6. Texto

Suporte explícito a arquivos de texto.

Considerar:

* UTF-8;
* outras encodings quando necessárias;
* conversão;
* leitura incremental;
* escrita incremental;
* newline;
* BOM;
* conteúdo inválido.

Não assumir silenciosamente que todo arquivo textual é UTF-8 se isso puder produzir corrupção de dados.

---

# 7. Binário

Fornecer abstrações para dados binários.

Conceitualmente:

```kof
var bytes = File.readBytes("image.bin")
```

e:

```kof
File.writeBytes("output.bin", bytes)
```

A API deve permitir trabalhar com:

* bytes;
* buffers;
* streams;
* offsets;
* ranges;
* leitura parcial;
* escrita parcial.

---

# 8. Streaming

Streaming é requisito arquitetural.

`kof.file` não deve pressupor que qualquer arquivo possa ser carregado integralmente em memória.

Deve ser possível trabalhar incrementalmente com:

* arquivos grandes;
* CSVs grandes;
* JSON streaming quando aplicável;
* XML streaming;
* arquivos binários;
* logs;
* datasets;
* pipelines.

A API final deve utilizar o modelo de iteração e streaming existente no Kof.

Não inventar uma sintaxe nova apenas para representar streaming.

---

# 9. JSON

Adicionar suporte nativo a JSON.

Operações:

* parse;
* serialize;
* leitura;
* escrita;
* objetos;
* arrays;
* strings;
* números;
* booleanos;
* null;
* validação;
* pretty printing;
* streaming quando aplicável.

O acesso deve respeitar o sistema de tipos e coleções existente no Kof.

Não copiar APIs de JavaScript como se JSON fosse um objeto JS.

> **Estado real:** `kof.json` (`json.encode`/`json.decode`) já cobre o
> núcleo desta seção. O trabalho futuro é a costura com o modelo
> File/Stream, não um segundo parser.

---

# 10. XML

Adicionar suporte a XML.

Operações:

* parse;
* serialize;
* elementos;
* atributos;
* texto;
* namespaces;
* leitura;
* escrita;
* consultas;
* streaming.

Avaliar XPath somente se houver justificativa arquitetural.

Não criar uma abstração gigantesca apenas para reproduzir uma biblioteca externa.

---

# 11. CSV / TSV

Adicionar suporte a formatos tabulares.

CSV deve lidar corretamente com:

* delimitadores;
* aspas;
* escapes;
* newline;
* encoding;
* header;
* colunas;
* campos vazios;
* valores contendo delimitadores;
* arquivos grandes;
* streaming.

TSV deve reutilizar a mesma infraestrutura quando possível.

A API deve permitir processamento incremental.

---

# 12. YAML / TOML / INI

Adicionar progressivamente suporte a formatos de configuração e dados:

```text
YAML
TOML
INI
```

Cada formato deve possuir:

* parse;
* serialize quando fizer sentido;
* validação;
* erros claros.

Evitar criar estruturas de dados incompatíveis sem necessidade.

---

# 13. Markdown / HTML

## Markdown

Avaliar suporte para:

* leitura;
* parsing;
* representação estruturada;
* geração.

## HTML

Avaliar suporte para:

* parsing;
* árvore de documentos;
* elementos;
* atributos;
* texto;
* serialização.

HTML deve poder ser tratado como documento estruturado, não apenas como string.

> **Nota de filosofia (regra 9):** tratamento de HTML aqui é como
> **dado/documento** (parse/serialize), nunca como template embutido
> em código de usuário de `kof.ui`.

---

# 14. PDF

Adicionar suporte progressivamente a PDF.

Primeira fase:

* abrir;
* validar;
* metadata;
* número de páginas;
* acessar páginas;
* extrair texto;
* criar PDF simples quando tecnicamente viável.

Posteriormente:

* imagens;
* fontes;
* tabelas;
* posicionamento;
* formulários;
* annotations;
* geração avançada.

Não implementar todo o padrão PDF do zero se uma biblioteca madura puder ser utilizada.

A biblioteca externa deve ficar isolada atrás da API Kof.

---

# 15. Imagens

Avaliar suporte progressivo a:

```text
PNG
JPEG
GIF
WebP
BMP
```

Operações possíveis:

* abrir;
* metadata;
* dimensões;
* leitura;
* escrita;
* conversão;
* pixels quando apropriado.

Não transformar `kof.file` em uma biblioteca gráfica.

A responsabilidade continua sendo manipulação de arquivos e dados.

> **Divisão de responsabilidade:** decodificação de pixels e
> processamento pertencem a `kof.image` (ver [`../development/image-vision-plan.md`](../development/image-vision-plan.md));
> `kof.file` entrega bytes/stream/`Image` carregada.

---

# 16. Arquivos compactados

Avaliar suporte a:

```text
ZIP
GZIP
TAR
```

Operações:

* abrir;
* listar;
* extrair;
* criar;
* adicionar;
* remover;
* streaming quando possível.

Considerar segurança contra path traversal dentro de arquivos compactados.

---

# 17. Formatos binários

A infraestrutura de `kof.file` deve permitir trabalhar com formatos binários arbitrários.

Fornecer primitivas para:

* bytes;
* integers;
* floats;
* endianess;
* offsets;
* buffers;
* streams;
* estruturas binárias.

Isso permite que futuras bibliotecas Kof implementem formatos específicos sem depender de uma biblioteca externa para cada protocolo.

---

# 18. Detecção de formato

Avaliar uma API capaz de identificar formatos quando houver evidência suficiente.

A identificação pode utilizar:

* extensão;
* MIME type;
* magic bytes;
* conteúdo.

Não confiar exclusivamente na extensão.

Quando houver ambiguidade, representar corretamente a incerteza.

---

# 19. MIME types

Adicionar infraestrutura para MIME types.

Exemplos:

```text
application/json
application/pdf
text/csv
application/xml
image/png
```

Permitir integração futura com:

* HTTP;
* upload;
* download;
* filesystem;
* documentos;
* conteúdo binário.

---

# 20. Integração com HTTP

Permitir que dados obtidos via HTTP sejam consumidos pelas mesmas APIs de `kof.file`.

Arquitetura desejada:

```text
HTTP response
      ↓
bytes / stream
      ↓
JSON / XML / CSV / PDF / etc.
```

E:

```text
File
 ↓
stream
 ↓
HTTP upload
```

Não criar dependência circular entre HTTP e `kof.file`.

> **Estado real:** `kof.http` já existe; a integração é camada de
> ponte (`Response → bytes/stream`), não novo módulo.

---

# 21. Integração com banco

A arquitetura deve permitir pipelines como:

```text
Database
    ↓
Data
    ↓
JSON / CSV / XML / etc.
```

e:

```text
File
    ↓
Data
    ↓
Database
```

Isso deve reutilizar as abstrações de dados existentes em vez de criar conversores específicos para cada combinação.

> **Estado real:** `kof.db` já existe; vale o mesmo princípio de ponte.

---

# 22. Segurança

Considerar:

* path traversal;
* symlinks;
* permissões;
* arquivos inexistentes;
* concorrência;
* race conditions;
* arquivos especiais;
* recursos limitados;
* arquivos gigantes;
* conteúdo malformado;
* decompression bombs;
* arquivos compactados maliciosos.

Não assumir que conteúdo recebido pelo programa é confiável.

Erros devem ser explícitos.

---

# 23. Targets

A API pública deve ser compartilhada sempre que houver implementação equivalente.

Prioridade:

```text
JVM
Native
JS/WASM quando aplicável
```

Não fingir suporte multiplataforma.

Quando uma funcionalidade for específica de uma plataforma:

* isolar a implementação;
* documentar a limitação;
* retornar erro apropriado quando indisponível (R6 — gap `XXX00x`, nunca silêncio).

---

# 24. Performance

Considerar:

* streaming;
* buffers reutilizáveis;
* leitura parcial;
* escrita incremental;
* zero-copy quando possível;
* mmap quando apropriado;
* backpressure;
* processamento paralelo quando fizer sentido.

Não otimizar por especulação.

Adicionar benchmarks para medir as decisões relevantes.

---

# 25. Testes

Criar testes por camada.

## Filesystem

* criação;
* leitura;
* escrita;
* append;
* copy;
* move;
* delete;
* directories;
* metadata.

## Text

* UTF-8;
* Unicode;
* encoding;
* newline;
* BOM;
* arquivos vazios;
* conteúdo inválido.

## Binary

* bytes;
* offsets;
* buffers;
* endianess;
* streams.

## JSON

* parse;
* serialize;
* tipos;
* Unicode;
* erros;
* round-trip.

## XML

* parse;
* namespaces;
* atributos;
* serialização;
* XML inválido;
* round-trip.

## CSV

* header;
* quoted fields;
* delimitadores;
* newline;
* Unicode;
* campos vazios;
* arquivos grandes.

## PDF

* abertura;
* metadata;
* páginas;
* texto;
* arquivos inválidos.

## Archives

* criação;
* extração;
* arquivos corrompidos;
* paths maliciosos.

Também criar testes de integração:

```text
File
 ↓
Format parser
 ↓
Data
 ↓
Format serializer
 ↓
File
```

---

# 26. Dependências

Não implementar padrões complexos do zero apenas para evitar dependências.

Também não adicionar uma biblioteca gigantesca para resolver uma operação simples.

Para cada dependência avaliar:

* maturidade;
* licença;
* tamanho;
* segurança;
* manutenção;
* compatibilidade com targets;
* performance;
* possibilidade de substituição futura.

A API pública de Kof deve permanecer independente da biblioteca utilizada internamente.

---

# 27. Implementação incremental

Não implementar todos os formatos simultaneamente.

### Fase 1 — núcleo

```text
File
Path
Text
Binary
Stream
```

### Fase 2 — dados estruturados

```text
JSON
CSV
XML
```

### Fase 3 — configuração

```text
YAML
TOML
INI
```

### Fase 4 — documentos

```text
Markdown
HTML
PDF
```

### Fase 5 — binários e containers

```text
Images
Archives
outros formatos
```

### Fase 6 — evolução

```text
streaming avançado
mmap
performance
integração HTTP
integração DB
```

A ordem pode ser alterada se a arquitetura atual do Kof justificar outra sequência.

---

# 28. Regra arquitetural

`kof.file` não deve virar um depósito de wrappers de bibliotecas externas.

Toda funcionalidade nova precisa responder:

> Qual é a abstração comum que essa funcionalidade introduz ou reutiliza?

Abstrair quando existe semelhança real.

Não criar abstrações artificiais apenas para deixar a arquitetura visualmente bonita.

---

# 29. Regra de compatibilidade com a linguagem

Antes de adicionar qualquer API ou exemplo:

1. Consultar a gramática atual.
2. Consultar exemplos reais do projeto.
3. Consultar APIs existentes da stdlib.
4. Reutilizar convenções existentes.
5. Não inventar sintaxe.
6. Não importar padrões de JavaScript.
7. Não importar padrões de Kotlin.
8. Não importar padrões de Python.
9. Não criar uma "DSL de arquivos" sem necessidade.

`kof.file` deve parecer uma extensão natural do Kof.

---

# 30. Critérios de conclusão

A primeira versão não precisa suportar todos os formatos.

A arquitetura, porém, precisa permitir adicionar novos formatos sem reescrever o núcleo.

Critérios:

* [x] File funcional (`kof.io`)
* [x] Path funcional (`kof.io`)
* [x] Text funcional (`kof.io` + `TextStream`)
* [x] Binary funcional (`kof.io` + `FileStream`)
* [x] Stream funcional (`FileStream`/`TextStream`)
* [x] JSON (`kof.json` + ponte JSON Lines)
* [x] CSV (`CsvReader`/`CsvWriter`)
* [x] XML (`XmlReader`, namespaces)
* [x] config (INI/TOML/YAML)
* [x] testes (E2E por fatia, todos os alvos)
* [ ] benchmarks
* [x] documentação (`docs/stdlib/IO.pt_BR.md`)
* [x] tratamento de erros (diagnósticos explícitos)
* [ ] segurança (path-traversal de archives — não se aplica até haver archives)
* [x] integração com targets (JVM/Native x86-64/riscv64/Script; JS `IOJS001`)
* [x] nenhuma regressão na stdlib existente

---

# Regra final

O objetivo não é criar uma coleção de wrappers.

O objetivo é criar uma **abstração própria do Kof para arquivos, dados e documentos**, utilizando bibliotecas externas somente quando isso for tecnicamente justificável.

O desenvolvedor Kof deve poder pensar:

```text
"preciso trabalhar com esse arquivo"
```

e resolver isso através de `kof.file`, sem precisar conhecer a implementação interna utilizada pelo target.

A complexidade fica atrás da API Kof.

**Kof deve saber trabalhar com arquivos. Kof deve saber trabalhar com dados. Kof deve saber trabalhar com documentos.**

E tudo isso deve ser construído de forma incremental, mantendo a linguagem, a stdlib e a base existente estáveis.
