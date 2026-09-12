# Kof Common Mistakes

## 1. Using Java-style getters/setters

```kof
// WRONG
class User {
    private String name
    public getName(): String { return name }
}

// RIGHT
class User {
    String name  // accessible directly
}
```

## 2. String.valueOf(Int) achando que dá o caractere

```kof
// WRONG — retorna DÍGITOS: "104"
var s = String.valueOf(104)

// RIGHT — o caractere: "h"
var c = String.valueOf(104 as Char)
```

## 3. Manual memory management

```kof
// WRONG — Kof manages memory automatically
var ptr = alloc(100)
free(ptr)

// RIGHT
var data = new Int[100]
// memory reclaimed automatically
```

## 4. Backend-specific code

```kof
// WRONG — breaks multi-target
if (target == "native") {
    nativeCode()
}

// RIGHT — same code for all targets
var result = compute()
println(result)
```

## 5. Over-engineering

```kof
// WRONG
class ServiceFactory {
    create(): Service {
        return new Service()
    }
}

// RIGHT
var service = new Service()
```

## 6. Ignoring error handling

```kof
// WRONG — unsafe
var data = riskyOperation()

// RIGHT — safe
try {
    var data = riskyOperation()
} catch (String e) {
    println("Error: " + e)
}
```

## 7. Using Object as universal type

```kof
// WRONG
var x: Object = "hello"

// RIGHT
var x: String = "hello"
```

## 8. Unnecessary annotations

Annotations existem no Kof para **interoperação** (frameworks JVM, Android). Para recursos da própria plataforma, use as APIs idiomáticas — annotation+container é vazar mecanismo na intenção.

```kof
// WRONG — HTTP routing é intenção da linguagem, não annotation
@RestController
class UserController {
    // ...
}

// RIGHT
main() {
    var app = web.app()
    app.get("/users") { ... }
}

// RIGHT — annotation como metadado de interop (o framework externo exige)
@Service
class UserService {
    // ...
}
```

## 9. Manual string building

```kof
// WRONG
var result = ""
for (var i = 0; i < items.length; i++) {
    result = result + items[i] + ", "
}

// RIGHT — use concatenation
var result = "Items: " + items.length
```

## 10. Manual List.get handling (fix 27/08 — removido)

```kof
// WRONG (workaround histórico) — bounds check manual antes de get
if (i >= 0 && i < l.size) { var x = l.get(i) }

// RIGHT (0.3.22-beta) — kof_list_get já faz bounds check com mensagem clara
var x = l.get(1)   // ou l[1]
var y = listOf(1,2,3).get(1) // 2
```

## 11. Manual import workarounds (fix 27/08 — removido)

```kof
// WRONG — copiar arquivo C.kf para pasta raiz para evitar import a.b.C falhando
// RIGHT (0.3.22-beta) — CompilerDriver expandKofImports file-specific
import a.b.C
import a.b.*
```

## 12. Ignorar null safety (0.3.22-beta)

```kof
// WRONG — sentinela para ausência
String find(String key) { return "" }

// RIGHT — String? com narrowing
String? find(String key) { if (found) return value; return null }
var r = find("x")
if (r != null) { println(r) }
```

## 13. Loop manual quando higher-order existe (0.3.22-beta)

```kof
// WRONG
var nomes = listOf()
for (var u in users) { nomes.add(u.name) }

// RIGHT
var nomes = users.map((u: User) -> u.name)
```

---

## (02 Sep 2026) Descobertas do koflama — gotchas reais do emit JVM

Descobertas validadas com o forward do TinyLlama 100% Kof
(kof-agent M34). Todas fixadas no compiler; ficam aqui como
lição de causa → efeito.

### 1. `new String[0]` emitia NEWARRAY T_BYTE (VerifyError)

```kof
return KofLmTokVocab(new String[0], new Long[0])   // ✅ agora ANEWARRAY
```

**Causa:** `JvmBackend.arrayTypeForType` só cobria primitivos e
caía no default `T_BYTE` para tipos de referência. O bytecode
passava pelo `check` mas o JVM rejeitava no Verify (frame `[B`
vs `[Ljava/lang/String;`). O erro na tela era enganoso: o
launcher JVM reporta "componentes de runtime do JavaFX não
encontrados" quando o `main` falha no validate — sempre rodar
`java -Xdiag -cp . Default.Main` para ver o VerifyError real.

### 2. `Map<String, Int>` param emitia `Lkof/Map;` (NoClassDefFoundError)

**Causa:** o `kof.jar` embutia a classe `JvmTypeMapper` antiga
(shade não reprocessou depois do `mvn -pl ... -am` parcial).
`classDescriptor` já mapeava `kof.Map → java/util/HashMap`, mas
o jar desatualizado mascarava o fix. **Lição:** rebuild completo
(`mvn clean package` na raiz) antes de culpar o código Kof; o
classpath de `kof run` é só o tempDir — qualquer classe referen-
ciada que não esteja lá vira NoClassDefFoundError *no launcher*
(detalhe: o stack mostra `validateMainMethod`, não o call site).

### 3. `Map.get` com unboxing NPE na atribuição

```kof
var r = idx.get(sub)          // r é Int → unboxing imediato → NPE se ausente
if (r != null) { ... }        // tarde demais
```

**Idiom correto:**

```kof
if (idx.contains(sub)) {
    var r = idx.get(sub)
    if (r != null) { ... }
}
```

O compilador emite `intValue()` logo na atribuição quando a variá-
vel é `Int`; o `!= null` depois não salva. Guarda com `contains`
é a forma estável hoje (0.3.22-beta).

### 4. Soma de Int estoura silenciosamente em acumuladores largos

SPM scores chegam a `-29613` (×1e6 micro = `-3e10`, fora do Int).
O `dp[i] + scores[vi]` em `Int[]` dava wrap-around e o Viterbi
escolhia caminhos absurdos (token "e" com score positivo fantasma).

**Regra:** acumuladores que somam valores micro (×1e6) sempre em
`Long[]`, com sentinelas `Long` (`-2e12`, não `-2e9`).

### 5. UTF-8: `String.valueOf(byte as Char)` é latin-1, não UTF-8

O vocab SPM tem `▁` (U+2581, bytes `E2 96 81`). Ler byte a byte
com `as Char` produzia `â` + garbage e o match do vocab falhava
silenciosamente (`contains` → false). Decode UTF-8 manual (2/3/4
bytes → codepoint) antes de `as Char`. Mesmo princípio vale para
qualquer byte vindo de File I/O que vira texto.
