package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regressão P0 (R6 — nunca silencioso): resolução de método/campo que falha
 * em símbolo CONHECIDO (namespace builtin, classe do módulo, superclasse)
 * emite SEM025 antes do fallback UNKNOWN. UNKNOWN só existe para error
 * recovery — nunca declara método implícito.
 *
 * Bugs: #7 (namespaces builtin), #3 (campo em classe conhecida),
 * #6 (super.metodoInexistente), #8 (receiver conhecido não engole método).
 */
class SemanticResolutionTest extends SemanticResolutionSupport {

    // ---- #7: namespace builtin + método inexistente → SEM025 (matriz) ----

    @Test
    void unknownMethodOnBuiltinNamespaces(@TempDir Path tmp) throws IOException {
        String[] namespaces = {"db", "log", "http", "mq", "time", "security",
                "orm", "cache", "gpu", "config", "observability", "validation",
                // Família KofStd (lane STDLIB) — R6: método inexistente em
                // qualquer namespace stdlib dá SEM025, nunca é descartado em
                // silêncio pelo lowerer (fonte única: typer e lowerer usam a
                // mesma tabela KofStd/Kof<Dom>.staticMethod).
                "strings", "random", "uuid", "encoding", "math", "net"};
        for (String ns : namespaces) {
            CompilationResult r = compile(tmp, ns + ".kf",
                    "main() { " + ns + ".metodoRuim() }");
            assertSem025(r, "on namespace '" + ns + "'");
        }
    }

    @Test
    void wrongArityOnStdlibMethod(@TempDir Path tmp) throws IOException {
        // R6 (complemento do anterior): aridade ERRADA em um nome que EXISTE
        // também é SEM025, não "typer passou e lowerer descartou". A tabela
        // de dispatch valida argc; se o nome não casa na aridade, o
        // staticMethod retorna null => SEM025 (prova a fonte única).
        String[][] cases = {
                {"time", "time.isWeekend(2026, 9)"},           // precisa 3
                {"random", "random.randomInt()"},              // precisa 1
                {"strings", "strings.capitalize()"},           // precisa 1
                {"validation", "validation.formatCpf(1, 2)"},  // precisa 1
                {"uuid", "uuid.isUuid()"},                     // precisa 1
                // Famílias de OUTRAS lanes (varredura R6 10/09 — aditivo,
                // prova persistida das sondas manuais db.connect()/http.get()/
                // cache.get()/mq.publish(): nome EXISTE, aridade não casa).
                {"db", "db.connect()"},                        // precisa ≥1
                {"http", "http.get()"},                        // precisa ≥1
                {"cache", "cache.get()"},                      // precisa 1+
                {"mq", "mq.publish()"},                        // precisa 2
                {"security", "security.hash()"},               // precisa 1
                {"orm", "orm.save()"},                         // precisa >=1
                {"config", "config.get()"},                    // precisa 1
                {"cache", "cache.put()"},                      // precisa 2
                {"log", "log.info()"},                         // precisa >=1
        };
        for (String[] c : cases) {
            CompilationResult r = compile(tmp, c[0] + ".kf", "main() { " + c[1] + " }");
            assertSem025(r, "on namespace '" + c[0] + "'");
        }
    }

    @Test
    void mechanismModifierWarnsButStaysGreen(@TempDir Path tmp) throws IOException {
        // #125: `synchronized` (e volatile/transient/native) é aceito pelo
        // parser mas computeAccess o descarta — o programa compilava em
        // silêncio SEM o efeito pedido (falsa sensação de segurança). O memory
        // model ratificado (concurrency-memory-model.md §5) os declara
        // non-goals; a correção é R6 (nunca silencioso): warning SEM091 com a
        // posição do membro, NÃO-fatal (retrocompat — código que compila hoje
        // continua compilando, regra 2 do congelamento).
        CompilationResult r = compile(tmp, "Sync.kf", SRC_MECHANISM_MODIFIER_WARNS_BUT_STAYS_GREEN);
        assertTrue(r.success(), "synchronized deve continuar compilando (warning não-fatal)");
        boolean warned = r.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> d.severity() == Diagnostic.Severity.WARNING
                        && "SEM091".equals(d.code())
                        && d.message().contains("synchronized"));
        assertTrue(warned, "esperava warning SEM091 sobre 'synchronized', foi: "
                + r.diagnostics().getDiagnostics());
        // volatile/transient/native caem na mesma regra
        CompilationResult v = compile(tmp, "Vol.kf", SRC_MECHANISM_MODIFIER_WARNS_BUT_STAYS_GREEN_2);
        assertTrue(v.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> "SEM091".equals(d.code()) && d.message().contains("volatile")),
                "volatile deve avisar: " + v.diagnostics().getDiagnostics());
        // sem modificador de mecanismo → NENHUM SEM091 (não polui código limpo)
        CompilationResult clean = compile(tmp, "Clean.kf", SRC_MECHANISM_MODIFIER_WARNS_BUT_STAYS_GREEN_3);
        assertTrue(clean.success() && clean.diagnostics().getDiagnostics().stream()
                .noneMatch(d -> "SEM091".equals(d.code())),
                "código limpo não deve ter SEM091: " + clean.diagnostics().getDiagnostics());
    }

    @Test
    void unknownMethodOnWebApp(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "W.kf",
                "main() { web.app().metodoRuim() }");
        assertSem025(r, "on namespace 'web.app'");
    }

    // ---- #126: json no caminho semântico — aridade nunca escapa p/ bytecode ----

    @Test
    void wrongArityOnJsonNamespace(@TempDir Path tmp) throws IOException {
        // O namespace `json` só existia no lowering JVM; o check não o via,
        // e aridade errada produzia bytecode inválido (VerifyError). Agora o
        // SemanticAnalyzer valida o contrato fixo: encode(x) 1 arg,
        // decode<T>(s) 1 arg + type-argument.
        assertSem025(compile(tmp, "J1.kf", "record No(String t)\nmain() { println(json.encode(No(\"x\"), 4)) }"),
                "on namespace 'json'");
        assertSem025(compile(tmp, "J2.kf", "main() { println(json.encode()) }"),
                "on namespace 'json'");
        assertSem025(compile(tmp, "J3.kf", "main() { println(json.decode(\"x\")) }"),
                "use json.decode<T>(s)");
        assertSem025(compile(tmp, "J4.kf", "main() { println(json.metodoRuim()) }"),
                "on namespace 'json'");
        // caminho feliz continua verde (regressão zero)
        assertTrue(compile(tmp, "J5.kf", "main() { println(json.encode(42)) }").success(),
                "json.encode(x) deve compilar");
        assertTrue(compile(tmp, "J6.kf", "main() { println(json.decode<String>(\"\\\"a\\\"\")) }").success(),
                "json.decode<T>(s) deve compilar");
    }

    // ---- #6: super.metodoInexistente → SEM025 ----

    @Test
    void unknownMethodOnSuper(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "S.kf", SRC_UNKNOWN_METHOD_ON_SUPER);
        assertSem025(r, "in superclass 'Base'");
    }

    // ---- #3: campo inexistente em classe conhecida → SEM025 ----

    @Test
    void unknownFieldOnKnownClass(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "F.kf", SRC_UNKNOWN_FIELD_ON_KNOWN_CLASS);
        assertSem025(r, "Cannot resolve field 'campoInexistente' on type 'P'");
    }

    // ---- casos válidos NÃO podem diagnosticar (sem falso-positivo) ----

    @Test
    void validCallsStayGreen(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "V.kf", SRC_VALID_CALLS_STAY_GREEN);
        assertTrue(r.success(), "casos válidos devem compilar: "
                + r.diagnostics().getDiagnostics());
    }

    // ---- SG-017 (SEM041): `new` de classe abstrata → erro ----

    @Test
    void abstractClassInstantiationFails(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "A.kf", SRC_ABSTRACT_CLASS_INSTANTIATION_FAILS);
        assertFalse(r.success(), "deve falhar: new de abstract class");
        boolean found = r.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> "SEM041".equals(d.code())
                        && d.message().contains("abstract class 'Shape'"));
        assertTrue(found, "esperava SEM041, foi: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void abstractClassSubclassInstantiationStaysGreen(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "A.kf", SRC_ABSTRACT_CLASS_SUBCLASS_INSTANTIATION_STAYS_GREEN);
        assertTrue(r.success(), "subclass concreta instanciável: "
                + r.diagnostics().getDiagnostics());
    }

    // ---- método inexistente em classe do módulo → SEM025 (já coberto
    //      pelo caminho ClassType; trava regressão do gate isKnownReceiver) ----

    @Test
    void unknownMethodOnKnownClass(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "M.kf", SRC_UNKNOWN_METHOD_ON_KNOWN_CLASS);
        assertSem025(r, "on type 'P'");
    }

    // ---- #99 (R6): campo estático num TIPO PRIMITIVO (Int.MAX_VALUE) — fake
    // idiom, nunca existiu no Kof; antes passava sem diagnóstico e gerava lixo
    // nos 3 targets (JVM NoClassDefFoundError "?", Native SIGSEGV, Script null)
    // — e `var x = Int.MAX_VALUE` CRASHAVA o compilador (ASM visitMaxs). ----


    // §130 (spike OTP #83, 11/09): o laço de 4 passes do corpo de MÉTODO
    // (inference de return-type "bug 26") re-analisava cada corpo no MESMO
    // SymbolTable → do 2º pass em diante, todo `var` colidia (SEM024 falso)
    // quando UM método sem tipo declarado termina em `return <expr>` (ou chama
    // outro da classe que faz isso). Forma do spike: builder de cadeia com
    // overload `child(id, f)` delegando para `child(id, f, politica)`.
    @Test
    void redeclarationFalsePositiveEmMetodoDeClasse(@TempDir Path tmp) throws IOException {
        String src = SRC_REDECLARATION_FALSE_POSITIVE_EM_METODO_DE_CLASSE;
        CompilationResult r = compile(tmp, "S110.kf", src);
        assertTrue(r.success(), "corpos de método re-analisados devem aceitar 'var' "
                + "repetido (escopo por análise, não por classe): "
                + r.diagnostics().getDiagnostics());
        // executa de verdade (o fix nao pode trocar SEM024 por bytecode quebrado)
        java.nio.file.Path out = tmp.resolve("out-run");
        CompilationResult r2 = driver.compile(tmp.resolve("S110.kf"), out, Target.JVM);
        assertTrue(r2.success(), "segunda compilacao p/ run: " + r2.diagnostics().getDiagnostics());
        try {
            Process p = new ProcessBuilder("java", "-cp", out.toString(), "Default.Main")
                    .redirectErrorStream(true).start();
            String os = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0, p.waitFor(), "run deve sair limpo: " + os);
            assertTrue(os.contains("true"), "inference de return-type encadeado deve "
                    + "produzir o valor certo: " + os);
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    // §130 borda: redeclaração GENUÍNA no mesmo corpo continua SEM024
    // (o fix só isola passes, nunca afrouxa o SC5).
    @Test
    void redeclaracaoMesmoCorpoAindaErro(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "S110b.kf", SRC_REDECLARACAO_MESMO_CORPO_AINDA_ERRO);
        assertFalse(r.success(), "redeclaracao no mesmo escopo continua SEM024");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> "SEM024".equals(d.code()) && d.message().contains("'q'")),
                "esperava SEM024 de 'q', foi: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void staticFieldOnPrimitiveTypeRejected(@TempDir Path tmp) throws IOException {
        // as 3 formas: expressão solta, println, e assignment (o último era o
        // que CRASHAVA o compilador — agora é SEM050 limpo, não COMP002).
        String[] types = {"Int", "Long", "Double", "Float", "Char", "Byte", "Short", "Bool"};
        String[] fields = {"MAX_VALUE", "MIN_VALUE", "SIZE", "foo"};
        for (String t : types) {
            for (String f : fields) {
                assertSem050(compile(tmp, "e.kf", "main() { var x = " + t + "." + f + " }"),
                        "'" + t + "' is a primitive type");
            }
        }
    }

    @Test
    void primitiveAsTypeAndLiteralStillCompile(@TempDir Path tmp) throws IOException {
        // o SEM050 não pode quebrar o que LEGITIMAMENTE usa um nome de tipo:
        // anotação (`x: Int`), cast (`as Int`), e acesso a campo em INSTÂNCIA
        // (String.length, "abc".length). Proibido regridir (regra 1).
        CompilationResult r = compile(tmp, "ok.kf", SRC_PRIMITIVE_AS_TYPE_AND_LITERAL_STILL_COMPILE);
        assertTrue(r.success(), "legítimo deve compilar: " + r.diagnostics().getDiagnostics());
    }

    // ---- #100 (R6, paridade absoluta): Char em método de String — o programa
    // era ACEITO e quebrava de um jeito DIFERENTE em cada target (JVM
    // VerifyError/IncompatibleClassChangeError, Native SIGSEGV/saída vazia,
    // Script false/vazio). REJEITAR em compile-time com o mesmo SEM051 em
    // todos os backends (lowering = frontend único dos 5 alvos). ----

    @Test
    void charArgOnStringMethodRejected(@TempDir Path tmp) throws IOException {
        String[] exprs = {
            "\"abc\".indexOf('c')", "\"abc\".lastIndexOf('b')", "\"abc\".contains('b')",
            "\"abc\".startsWith('a')", "\"abc\".endsWith('c')", "\"a,b\".split(',')",
            "\"abc\".concat('x')", "\"abc\".equalsIgnoreCase('a')",
            "\"abc\".compareTo('a')", "\"abc\".compareToIgnoreCase('a')",
            "\"abc\".equalsIgnoreCase(5)", "\"abc\".concat(5)" };
        for (String e : exprs) {
            CompilationResult r = compile(tmp, "e.kf", "main() { println(" + e + ") }");
            assertFalse(r.success(), "deve falhar: " + e);
            boolean found = r.diagnostics().getDiagnostics().stream()
                    .anyMatch(d -> "SEM051".equals(d.code())
                            && d.message().contains("as argument")
                            && !d.message().matches("(?s).*como argumento.*"));
            assertTrue(found, "esperava SEM051 (EN) p/ '" + e + "', foi: "
                    + r.diagnostics().getDiagnostics());
        }
    }

    @Test
    void stringMethodsWithStringOrCharArgsStillCompile(@TempDir Path tmp) throws IOException {
        // não regridir (regra 1): literal String ok; replace(char,char) é o
        // overload LEGAL da registry; charAt/substring recebem numérico
        // (Char é Int em Kof — widening do usuário, não erro do compilador).
        CompilationResult r = compile(tmp, "ok.kf", SRC_STRING_METHODS_WITH_STRING_OR_CHAR_ARGS_STILL_COMPILE);
        assertTrue(r.success(), "legítimo deve compilar: " + r.diagnostics().getDiagnostics());
    }

    // ---- #96 (paridade absoluta JVM=JS=X86=ARM=RISC): funções da stdlib
    // `strings.*` chamadas como MÉTODO de String — o typer aceitava e cada
    // backend quebrava de um jeito (JVM NoSuchMethodError, Native link-fail,
    // JS roda o nativo do JS, Script roda por reflexão). Opção B: REJEITAR em
    // compile-time (SEM052) apontando para o idiom real do corpus. ----

    @Test
    void stringsFunctionsAsInstanceMethodsRejected(@TempDir Path tmp) throws IOException {
        String[] exprs = {
            "\"ab\".repeat(3)", "\"ab\".truncate(3)", "\"7\".padStart(5,\"-\")",
            "\"7\".padEnd(5,\"-\")", "\"7\".padLeft(3,\"0\")", "\"7\".padRight(3,\"0\")",
            "\"ab\".reverse()", "\"ab\".capitalize()", "\"abc\".count(\"a\")",
            "\"a\".isAlpha()", "\"a\".isNumeric()", "\"a_b\".toCamelCase()",
            "\"a\".escapeHtml()", "\"a\".slugify()" };
        for (String e : exprs) {
            CompilationResult r = compile(tmp, "e.kf", "main() { println(" + e + ") }");
            assertFalse(r.success(), "deve falhar: " + e);
            boolean found = r.diagnostics().getDiagnostics().stream()
                    .anyMatch(d -> "SEM052".equals(d.code()) && d.message().contains("strings."));
            assertTrue(found, "esperava SEM052 p/ '" + e + "', foi: "
                    + r.diagnostics().getDiagnostics());
        }
    }

    @Test
    void stringsFunctionsAndRealStringMethodsStillCompile(@TempDir Path tmp) throws IOException {
        // não regridir (regra 1): a forma função da stdlib e os métodos QUE
        // SÃO de String na registry (toUpperCase/trim/split/replace/substring).
        CompilationResult r = compile(tmp, "ok.kf", SRC_STRINGS_FUNCTIONS_AND_REAL_STRING_METHODS_STILL_COMPILE);
        assertTrue(r.success(), "legítimo deve compilar: " + r.diagnostics().getDiagnostics());
    }

    // ---- #98 (paridade absoluta JVM=JS=X86=ARM=RISC): `<`/`<=`/`>`/`>=` em
    // String era aceito e dava LIXO DIFERENTE em cada target (JVM tudo-false
    // via if_acmp, Native comparava PONTEIRO, Script lexicográfico). Opção B:
    // REJEITAR (SEM053) apontando p/ `compareTo` — igual nos 5 alvos. ----

    @Test
    void stringOrderingOperatorsRejected(@TempDir Path tmp) throws IOException {
        String[] exprs = {
            "\"abc\" < \"abd\"", "\"abc\" <= \"abd\"", "\"abc\" > \"abd\"",
            "\"abc\" >= \"abd\"", "\"abd\" < \"abc\"", "\"abc\" < 'b'" };
        for (String e : exprs) {
            CompilationResult r = compile(tmp, "e.kf", "main() { println(" + e + ") }");
            assertFalse(r.success(), "deve falhar: " + e);
            boolean found = r.diagnostics().getDiagnostics().stream()
                    .anyMatch(d -> "SEM053".equals(d.code()) && d.message().contains("compareTo"));
            assertTrue(found, "esperava SEM053 p/ '" + e + "', foi: "
                    + r.diagnostics().getDiagnostics());
        }
    }

    @Test
    void stringEqualityAndNumericOrderingStillCompile(@TempDir Path tmp) throws IOException {
        // não regridir: `==`/`!=` de String (conteúdo, congelado) e toda
        // comparação numérica (o guard é SÓ p/ String).
        CompilationResult r = compile(tmp, "ok.kf", SRC_STRING_EQUALITY_AND_NUMERIC_ORDERING_STILL_COMPILE);
        assertTrue(r.success(), "legítimo deve compilar: " + r.diagnostics().getDiagnostics());
    }

    // ---- subscript `[]`: existe para ARRAY e para a LEITURA de List
    // (`l[i]` → kof_list_get, #149/#152). A ESCRITA `l[i] = v` NÃO foi
    // lowerada (emitia store cru de array: JVM VerifyError aastore, Native
    // SIGSEGV, JS silencioso — §220) e segue rejeitada com SEM054 apontando
    // p/ `l.set(i, v)`. String/Map/Set seguem SEM054 nos 5 alvos
    // (paridade absoluta). ----

    @Test
    void subscriptOnCollectionsRejected(@TempDir Path tmp) throws IOException {
        String[] exprs = {
            "var s = \"abc\"; println(s[0])",
            "var m = mapOf(\"a\", 1); println(m[\"a\"])",
            "var st = setOf(\"a\"); println(st[\"a\"])",
            "var l2 = listOf(1); l2[0] = 9"};
        for (String e : exprs) {
            CompilationResult r = compile(tmp, "e.kf", "main() { " + e + " }");
            assertFalse(r.success(), "deve falhar: " + e);
            boolean found = r.diagnostics().getDiagnostics().stream()
                    .anyMatch(d -> "SEM054".equals(d.code()) && d.message().contains("array"));
            assertTrue(found, "esperava SEM054 p/ '" + e + "', foi: "
                    + r.diagnostics().getDiagnostics());
        }
    }

    @Test
    void listSubscriptReadIsSupported(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "e.kf",
                "main() { var l = listOf(10, 20); println(l[1]) }");
        assertTrue(r.success(), "List[i] leitura (#149/#152) deve compilar: "
                + r.diagnostics().getDiagnostics());
    }

    // ---- §122: índice de List.get/set/remove é Int (learn/12). String/record
    // no índice era ACEITO em silêncio e quebrava feio (JVM VerifyError na
    // carga da classe, Native pointer-as-index → "array index out of bounds"
    // — probes RM3/IX/IX2 11/09). Opção B (família SEM051-054): REJEITAR em
    // compile-time com SEM055 apontando p/ `contains`. Unknown/Int passam. ----

    @Test
    void listIndexNonIntRejected(@TempDir Path tmp) throws IOException {
        String[] exprs = {
            "var l = listOf(\"a\", \"b\"); println(l.remove(\"a\"))",
            "var l2 = listOf(\"a\", \"b\"); println(l2.get(\"x\"))",
            "var l3 = listOf(\"a\", \"b\"); l3.set(\"k\", \"z\")",
            "var l4 = listOf(1, 2); println(l4.remove(new Int[1]))" };
        for (String e : exprs) {
            CompilationResult r = compile(tmp, "e.kf", "main() { " + e + " }");
            assertFalse(r.success(), "deve falhar: " + e);
            boolean found = r.diagnostics().getDiagnostics().stream()
                    .anyMatch(d -> "SEM055".equals(d.code()) && d.message().contains("INDEX"));
            assertTrue(found, "esperava SEM055 p/ '" + e + "', foi: "
                    + r.diagnostics().getDiagnostics());
        }
    }

    @Test
    void listIndexIntAndUnknownStillCompiles(@TempDir Path tmp) throws IOException {
        // remove/get/set com índice Int e recebendo de função Unknown não
        // regredem (regra 1 — SG-008: Unknown pode ser Int em runtime).
        CompilationResult r = compile(tmp, "ok.kf", SRC_LIST_INDEX_INT_AND_UNKNOWN_STILL_COMPILES);
        assertTrue(r.success(), "Int-index não deve regride: " + r.diagnostics().getDiagnostics());
    }

    // ---- §126 (opção ii, decisão da mantenedora 11/09): escrita em
    // container PINADO com tipo ≠ o pinado polui o heap (o scan tag=1 chama
    // kof_string_equals sobre Int cru → SIGSEGV no Native — H3/H4; no JVM,
    // add heterogêneo já VerifyError na carga e set/put-valor já CCE).
    // A linguagem é estática: rejeitar em compile-time com SEM056 (família
    // SEM055/§122). Query-side (get/contains/remove-procura) NÃO é rejeitado
    // — é miss seguro (§126 lado ARG). Unknown (ainda não pinado) e widening
    // numérico (Int→Long) passam. ----

    @Test
    void heterogeneousWriteToPinnedCollectionRejected(@TempDir Path tmp) throws IOException {
        String[] exprs = {
            "var l = listOf(\"a\"); l.add(5)",
            "var l = listOf(1); l.add(\"x\"); println(l.get(0))",
            "var s = setOf(\"a\"); s.add(5)",
            "var m = mapOf(\"a\", 1); m.put(5, \"b\")",
            "var m = mapOf(\"a\", 1); m.put(\"b\", \"x\")",
            "var l = listOf(\"a\", \"b\"); l.set(0, 5)",
            // pinado PELO primeiro add (List<Unknown> → List<Int>); o segundo
            // add polui — mesma família SEM056.
            "var n = listOf(); n.add(1); n.add(\"x\"); println(n.size())" };
        for (String e : exprs) {
            CompilationResult r = compile(tmp, "e.kf", "main() { " + e + "; println(1) }");
            assertFalse(r.success(), "deve falhar: " + e);
            boolean found = r.diagnostics().getDiagnostics().stream()
                    .anyMatch(d -> "SEM056".equals(d.code())
                            && d.message().contains("Kof collections are homogeneous")
                            && !d.message().matches("(?s).*cole\u00e7\u00f5es.*"));
            assertTrue(found, "esperava SEM056 (EN) p/ '" + e + "', foi: "
                    + r.diagnostics().getDiagnostics());
        }
    }

    @Test
    void querySideAndWideningNotRejected(@TempDir Path tmp) throws IOException {
        // get/contains com tipo ≠ (query-side, §126 lado ARG) continua
        // compilando — o Native devolve o miss seguro (tag=0 raw cmpq), o
        // JVM devolve null/false. E add/set/put que só WIDENAM números
        // (Int→Long), são homogêneos, ou PINAM um container Unknown passam.
        CompilationResult r = compile(tmp, "ok2.kf", SRC_QUERY_SIDE_AND_WIDENING_NOT_REJECTED);
        assertTrue(r.success(), "query-side/homogêneo não deve regride: "
                + r.diagnostics().getDiagnostics());
    }

    @Test
    void subscriptOnArraysStillCompiles(@TempDir Path tmp) throws IOException {
        // array de verdade (o único [] do corpus) não regride (regra 1).
        CompilationResult r = compile(tmp, "ok.kf", SRC_SUBSCRIPT_ON_ARRAYS_STILL_COMPILES);
        assertTrue(r.success(), "array deve compilar: " + r.diagnostics().getDiagnostics());
    }

    // ---- bug 145: `for (var c in "abc")` era ACEITO e quebrava de um jeito
    // por target (JVM VerifyError `arraylength` em String — a classe nem
    // carregava; Native SIGSEGV; Script "Argument is not an array"; JS iterava
    // chars em silêncio — divergência cross-target). `for-in` só itera
    // List<T>/array no corpus (statements.md §5.4); SEM058 rejeita o resto em
    // compile-time (família SEM054 do bug 103). Unknown/generic NÃO são
    // flagados (podem ser List/array em runtime, SG-008). ----

    @Test
    void forInNonIterableRejected(@TempDir Path tmp) throws IOException {
        String[] exprs = {
            "var s = \"abc\"; for (var c in s) { println(c) }",
            "var m = mapOf(\"a\", 1); for (var e in m) { println(e) }",
            "var st = setOf(\"a\"); for (var e in st) { println(e) }",
            "var n = 3; for (var x in n) { println(x) }" };
        for (String e : exprs) {
            CompilationResult r = compile(tmp, "e.kf", "main() { " + e + " }");
            assertFalse(r.success(), "deve falhar: " + e);
            boolean found = r.diagnostics().getDiagnostics().stream()
                    .anyMatch(d -> "SEM058".equals(d.code()) && d.message().contains("for-in"));
            assertTrue(found, "esperava SEM058 p/ '" + e + "', foi: "
                    + r.diagnostics().getDiagnostics());
        }
    }

    @Test
    void forInListAndArrayStillCompiles(@TempDir Path tmp) throws IOException {
        // List/array (as duas formas iteráveis do corpus) e List vinda de
        // função (Unknown-element) não regridem (regra 1).
        CompilationResult r = compile(tmp, "ok.kf", SRC_FOR_IN_LIST_AND_ARRAY_STILL_COMPILES);
        assertTrue(r.success(), "List/array não devem regredir: "
                + r.diagnostics().getDiagnostics());
    }
}
