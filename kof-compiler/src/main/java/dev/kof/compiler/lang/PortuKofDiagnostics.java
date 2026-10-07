package dev.kof.compiler.lang;

import java.util.List;
import java.util.Map;

/**
 * D-PORTUKOF F6 (07/10) — catálogo de diagnósticos localizados, indexado pelo
 * CÓDIGO canônico. UM único sistema de diagnósticos: o código (LEX/PARSE/SEM…)
 * nunca muda e é a ÚNICA chave; a mensagem em inglês é a forma canônica (teste,
 * LSP, `--json`, tooling) e NUNCA é substituída. Esta classe só produz a forma
 * HUMANA PT-BR, para a superfície `.ptkf`, e NUNCA traduz por regex/prosa:
 *
 *   diagnóstico (código + mensagem EN + argumentos estruturados)
 *        → variante do código cujo TEMPLATE EN, renderizado com os mesmos
 *          argumentos, reproduz EXATAMENTE a mensagem canônica
 *        → renderiza o TEMPLATE PT com esses argumentos.
 *
 * Quando nenhuma variante reproduz a mensagem (código com texto que o catálogo
 * ainda não cobre, ou forma nova), devolve `null` e o chamador mantém o inglês
 * — nunca uma tradução aproximada/errada. Cada template EN do catálogo é
 * DERIVADO da concatenação real do ponto de emissão (gerador); a paridade de
 * placeholders EN↔PT é travada pelo gate.
 */
public final class PortuKofDiagnostics {

    private PortuKofDiagnostics() {}

    /** código canônico -> variantes (template EN, template PT), na ordem real. */
    private record Variant(String en, String pt) {}

    private static final Map<String, List<Variant>> VARIANTS = build();

    /** catálogo para o gate/testes: código -> [(en, pt)]. */
    public static Map<String, List<String[]>> catalog() {
        var m = new java.util.LinkedHashMap<String, List<String[]>>();
        for (var e : VARIANTS.entrySet()) {
            m.put(e.getKey(), e.getValue().stream().map(v -> new String[]{v.en(), v.pt()}).toList());
        }
        return java.util.Collections.unmodifiableMap(m);
    }

    public static Map<String, List<Variant>> variants() {
        return VARIANTS;
    }

    /**
     * Forma humana PT para a superfície `.ptkf`. Devolve `null` quando: não é
     * PortuKof (por EXTENSÃO do arquivo, nunca por conteúdo), o código não tem
     * catálogo, ou nenhuma variante reproduz a mensagem canônica — nesse caso o
     * chamador usa `message` (inglês).
     */
    public static String localize(String code, String file, String message, List<Object> args) {
        if (code == null || code.isEmpty() || message == null) return null;
        if (LanguageProfile.forFileName(file) != LanguageProfile.PORTUKOF) return null;
        List<Variant> vs = VARIANTS.get(code);
        if (vs == null) return null;
        for (Variant v : vs) {
            String built = render(v.en(), args);
            if (built.equals(message)) {
                String pt = render(v.pt(), args);
                // placeholder sem argumento -> não localiza (mantém EN; nunca
                // deixa um '{0}' cru na cara do usuário).
                if (hasOpenPlaceholder(pt, args)) return null;
                return pt;
            }
        }
        return null;
    }

    /** Substituição posicional `{i}` -> args[i]; índice fora/sem args fica
     *  inalterado (usado na detecção de placeholder sem valor). */
    static String render(String template, List<Object> args) {
        if (template == null) return null;
        StringBuilder out = new StringBuilder();
        int i = 0, n = template.length();
        while (i < n) {
            char c = template.charAt(i);
            if (c == '{') {
                int end = template.indexOf('}', i + 1);
                if (end > i) {
                    String idx = template.substring(i + 1, end);
                    if (!idx.isEmpty() && idx.chars().allMatch(Character::isDigit)) {
                        int a = Integer.parseInt(idx);
                        if (args != null && a < args.size()) {
                            out.append(args.get(a));
                            i = end + 1;
                            continue;
                        }
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /** true se sobra um `{dígito}` sem argumento correspondente. */
    private static boolean hasOpenPlaceholder(String rendered, List<Object> args) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{(\\d+)\\}")
                .matcher(rendered);
        while (m.find()) {
            int a = Integer.parseInt(m.group(1));
            if (args == null || a >= args.size()) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // CATÁLOGO — gerado por scripts/gen_portukof_diags.py a partir dos pontos
    // de emissão reais (NÃO editar à mão; `--check` recusa deriva). O texto PT é
    // português natural de ferramenta; placeholders {i} batem com o template EN.
    // ------------------------------------------------------------------
    private static Map<String, List<Variant>> build() {
        var m = new java.util.LinkedHashMap<String, List<Variant>>();
        // @@CATALOG_BEGIN@@
        m.put("LEX001", List.of(new Variant("Unterminated block comment", "comentário de bloco não terminado")));
        m.put("LEX002", List.of(new Variant("Unterminated string literal", "literal de texto não terminado")));
        m.put("LEX003", List.of(new Variant("Empty character literal", "literal de caractere vazio")));
        m.put("LEX004", List.of(new Variant("Unterminated character literal", "literal de caractere não terminado")));
        m.put("LEX005", List.of(new Variant("Unexpected character: '{0}'", "caractere inesperado: '{0}'")));
        m.put("LEX006", List.of(new Variant("Incomplete unicode escape (expected \\uXXXX)", "escape unicode incompleto (esperado \\uXXXX)")));
        m.put("LEX007", List.of(new Variant("Invalid unicode escape: \\u{0}", "escape unicode inválido: \\u{0}")));
        m.put("LEX008", List.of(new Variant("Kof has no triple-quoted (raw/multiline) string literal: use \"...\" with \\n (no interpolation either — concatenate with +)", "Kof não tem literal de texto triplamente aspas (raw/multilinha): use \"...\" com \\n (também sem interpolação — concatene com +)")));
        m.put("PARSE001", List.of(new Variant("Expected package name", "esperado nome de pacote")));
        m.put("PARSE002", List.of(new Variant("Expected package name component", "esperado componente do nome de pacote")));
        m.put("PARSE004", List.of(new Variant("Expected import name", "esperado nome de import")));
        m.put("PARSE005", List.of(new Variant("Expected import path component", "esperado componente do caminho de import")));
        m.put("PARSE007", List.of(new Variant("Expected type declaration", "esperada declaração de tipo")));
        m.put("PARSE008", List.of(new Variant("Expected class name", "esperado nome de classe")));
        m.put("PARSE009", List.of(new Variant("Expected '}' after class body", "esperado '}' depois do corpo da classe")));
        m.put("PARSE010", List.of(new Variant("Expected '(' after 'using'", "esperado '(' depois de 'using'"), new Variant("Expected ')' to close using (name = init, closer)", "esperado ')' para fechar using (name = init, closer)"), new Variant("Expected '=' after the resource name in using (name = init, closer)", "esperado '=' depois do nome do recurso em using (name = init, closer)"), new Variant("Expected function name", "esperado nome de função"), new Variant("Expected infra name string", "esperada string de nome de infra"), new Variant("Expected interface name", "esperado nome de interface"), new Variant("Expected parameter name", "esperado nome de parâmetro"), new Variant("Expected resource name in using (name = init, closer)", "esperado nome de recurso em using (name = init, closer)"), new Variant("Expected tag string after ',' in test declaration", "esperada string de tag depois de ',' na declaração de teste"), new Variant("Expected test name string", "esperada string de nome de teste"), new Variant("test name must not be empty", "nome de teste não pode ser vazio"), new Variant("test tag must not be empty", "tag de teste não pode ser vazia"), new Variant("using requires a closer expression: using (name = init, closer) { body }", "using exige uma expressão de fechamento: using (name = init, closer) { corpo }")));
        m.put("PARSE011", List.of(new Variant("Expected '('", "esperado '('"), new Variant("Expected '}' after interface body", "esperado '}' depois do corpo da interface")));
        m.put("PARSE012", List.of(new Variant("Expected ')'", "esperado ')'"), new Variant("Expected record name", "esperado nome de record")));
        m.put("PARSE013", List.of(new Variant("Expected ')' after constructor parameters", "esperado ')' depois dos parâmetros do construtor"), new Variant("Expected ')' after record components", "esperado ')' depois dos componentes do record"), new Variant("Expected '{'", "esperado '{'")));
        m.put("PARSE014", List.of(new Variant("Expected '}' after record body", "esperado '}' depois do corpo do record")));
        m.put("PARSE015", List.of(new Variant("Expected component name", "esperado nome de componente")));
        m.put("PARSE016", List.of(new Variant("Unexpected token in class body", "token inesperado no corpo da classe")));
        m.put("PARSE018", List.of(new Variant("Expected member name", "esperado nome de membro")));
        m.put("PARSE019", List.of(new Variant("Expected ')' after parameters", "esperado ')' depois dos parâmetros")));
        m.put("PARSE020", List.of(new Variant("Expected constructor name", "esperado nome de construtor")));
        m.put("PARSE021", List.of(new Variant("Expected '('", "esperado '('")));
        m.put("PARSE022", List.of(new Variant("Expected ')'", "esperado ')'")));
        m.put("PARSE023", List.of(new Variant("Expected parameter name", "esperado nome de parâmetro")));
        m.put("PARSE024", List.of(new Variant("Expected ':' after field name", "esperado ':' depois do nome do campo"), new Variant("Expected '{'", "esperado '{'"), new Variant("Expected '{' after entity name", "esperado '{' depois do nome da entidade"), new Variant("Expected '}' after entity body", "esperado '}' depois do corpo da entidade"), new Variant("Expected entity name", "esperado nome de entidade"), new Variant("Expected field name in entity", "esperado nome de campo na entidade")));
        m.put("PARSE025", List.of(new Variant("Expected '}'", "esperado '}'"), new Variant("Expected '}' after query block", "esperado '}' depois do bloco de consulta")));
        m.put("PARSE028", List.of(new Variant("Expected '(' after 'if'", "esperado '(' depois de 'if'")));
        m.put("PARSE029", List.of(new Variant("Expected ')'", "esperado ')'")));
        m.put("PARSE030", List.of(new Variant("Expected '(' after 'while'", "esperado '(' depois de 'while'"), new Variant("Expected 'enum'", "esperado 'enum'")));
        m.put("PARSE031", List.of(new Variant("Expected ')'", "esperado ')'"), new Variant("Expected enum name", "esperado nome de enum")));
        m.put("PARSE032", List.of(new Variant("Expected '(' after 'for'", "esperado '(' depois de 'for'"), new Variant("Expected enum constant", "esperada constante de enum")));
        m.put("PARSE033", List.of(new Variant("Expected '}' after enum body", "esperado '}' depois do corpo do enum")));
        m.put("PARSE034", List.of(new Variant("Expected '{' after enum name (enums are constants-only)", "esperado '{' depois do nome do enum (enum é só de constantes)")));
        m.put("PARSE035", List.of(new Variant("Expected ')'", "esperado ')'")));
        m.put("PARSE037", List.of(new Variant("Expected variable name", "esperado nome de variável")));
        m.put("PARSE039", List.of(new Variant("Expected field name", "esperado nome de campo")));
        m.put("PARSE040", List.of(new Variant("Expected '('", "esperado '('"), new Variant("Expected ')'", "esperado ')'")));
        m.put("PARSE041", List.of(new Variant("Unexpected token in expression: {0}", "token inesperado na expressão: {0}")));
        m.put("PARSE042", List.of(new Variant("Expected '('", "esperado '('"), new Variant("Expected '->'", "esperado '->'")));
        m.put("PARSE043", List.of(new Variant("Expected '(' after if", "esperado '(' depois de if"), new Variant("Expected ')'", "esperado ')'")));
        m.put("PARSE044", List.of(new Variant("Expected 'else'", "esperado 'else'"), new Variant("Expected type", "esperado tipo")));
        m.put("PARSE045", List.of(new Variant("Expected ')' in Buffer(...)", "esperado ')' em Buffer(...)"), new Variant("Expected ']'", "esperado ']'"), new Variant("Expected '}' after if-expression branch", "esperado '}' depois do ramo da expressão if")));
        m.put("PARSE046", List.of(new Variant("Expected ']'", "esperado ']'")));
        m.put("PARSE050", List.of(new Variant("Expected '('", "esperado '('")));
        m.put("PARSE051", List.of(new Variant("Expected '{' after application", "esperado '{' depois de application"), new Variant("Expected '}' after application block", "esperado '}' depois do bloco application"), new Variant("Expected exception name", "esperado nome de exceção"), new Variant("Expected onStart/onShutdown block in application", "esperado bloco onStart/onShutdown em application"), new Variant("infra body accepts only builder calls (resource/prop/requires)", "o corpo de infra aceita apenas chamadas de builder (resource/prop/requires)"), new Variant("Expected '{' after {0}", "esperado '{' depois de {0}")));
        m.put("PARSE052", List.of(new Variant("Expected ')'", "esperado ')'")));
        m.put("PARSE060", List.of(new Variant("Expected 'while' after 'do'", "esperado 'while' depois de 'do'")));
        m.put("PARSE061", List.of(new Variant("Expected '(' after 'while'", "esperado '(' depois de 'while'")));
        m.put("PARSE062", List.of(new Variant("Expected ')'", "esperado ')'")));
        m.put("PARSE070", List.of(new Variant("Expected '(' after 'switch'", "esperado '(' depois de 'switch'")));
        m.put("PARSE071", List.of(new Variant("Expected ')'", "esperado ')'")));
        m.put("PARSE072", List.of(new Variant("Expected '{'", "esperado '{'")));
        m.put("PARSE073", List.of(new Variant("expected ':' (switch statement) or '->' (switch expression)", "esperado ':' (declaração switch) ou '->' (expressão switch)")));
        m.put("PARSE074", List.of(new Variant("Expected ':'", "esperado ':'")));
        m.put("PARSE075", List.of(new Variant("Expected '>' after type parameters", "esperado '>' depois dos parâmetros de tipo"), new Variant("Expected '}'", "esperado '}'")));
        m.put("PARSE076", List.of(new Variant("Expected '>' after type arguments", "esperado '>' depois dos argumentos de tipo"), new Variant("switch expression requires '->' (the statement form uses ':')", "a expressão switch exige '->' (a forma de declaração usa ':')")));
        m.put("PARSE077", List.of(new Variant("Expected '->' after 'default'", "esperado '->' depois de 'default'")));
        m.put("PARSE078", List.of(new Variant("Expected '<'", "esperado '<'"), new Variant("expected 'case' or 'default' in switch expression", "esperado 'case' ou 'default' na expressão switch")));
        m.put("PARSE079", List.of(new Variant("Expected '>'", "esperado '>'")));
        m.put("PARSE080", List.of(new Variant("Expected annotation name", "esperado nome de anotação")));
        m.put("PARSE081", List.of(new Variant("Expected ')' after annotation arguments", "esperado ')' depois dos argumentos da anotação")));
        m.put("PARSE082", List.of(new Variant("Expected '}' after annotation array", "esperado '}' depois do array da anotação")));
        m.put("PARSE083", List.of(new Variant("Invalid numeric literal in annotation", "literal numérico inválido em anotação")));
        m.put("PARSE084", List.of(new Variant("Expected annotation value", "esperado valor de anotação"), new Variant("invalid float literal: {0}", "literal float inválido: {0}"), new Variant("numeric literal out of range: {0}", "literal numérico fora da faixa: {0}")));
        m.put("PARSE085", List.of(new Variant("'{0}' is a reserved word (Kof has no function keyword); declare as 'Type name(...) { }' or 'name(...): Type { }'", "'{0}' é uma palavra reservada (Kof não tem palavra-chave de função); declare como 'Tipo nome(...) { }' ou 'nome(...): Tipo { }'")));
        m.put("PARSE086", List.of(new Variant("Wildcard types '? extends/super' are not supported in Kof; use a concrete type or nullable 'T?'", "tipos curinga '? extends/super' não são suportados no Kof; use um tipo concreto ou nullability 'T?'")));
        m.put("PARSE090", List.of(new Variant("Expected 'extern'", "esperado 'extern'"), new Variant("Expected 'where', 'orderBy' or 'limit' in query block", "esperado 'where', 'orderBy' ou 'limit' no bloco de consulta")));
        m.put("PARSE091", List.of(new Variant("Expected a library string literal after 'library'", "esperado um literal de texto de biblioteca depois de 'library'"), new Variant("Expected extern function name", "esperado nome de função extern"), new Variant("Expected a value after '{0}'", "esperado um valor depois de '{0}'")));
        m.put("PARSE092", List.of(new Variant("Expected '('", "esperado '('"), new Variant("Expected '{' after foreign module name", "esperado '{' depois do nome do módulo estrangeiro")));
        m.put("PARSE093", List.of(new Variant("Expected ')'", "esperado ')'"), new Variant("Expected '}' to close foreign module", "esperado '}' para fechar o módulo estrangeiro")));
        m.put("PARSE094", List.of(new Variant("switch expression: each case body is a SINGLE expression (no block scope); use the switch-statement (`case ...:`) for multiple statements", "expressão switch: cada corpo de caso é UMA ÚNICA expressão (sem escopo de bloco); use a declaração switch (`case ...:`) para várias declarações")));
        m.put("PARSE095", List.of(new Variant("invalid variable declaration: the ':' annotation is only allowed after 'var'/'val' — the type '{0}' before '{1}' does not match '{2}' and would be silently discarded; write 'var {3}: {4} = ...' or '{5} {6} = ...'", "declaração de variável inválida: a anotação ':' só é permitida depois de 'var'/'val' — o tipo '{0}' antes de '{1}' não casa com '{2}' e seria descartado em silêncio; escreva 'var {3}: {4} = ...' ou '{5} {6} = ...'")));
        m.put("PARSE096", List.of(new Variant("Expected foreign module name", "esperado nome de módulo estrangeiro")));
        m.put("PARSE097", List.of(new Variant("foreign module '{0}' must declare a `library \"...\"`", "módulo estrangeiro '{0}' deve declarar um `library \"...\"`")));
        m.put("PARSE098", List.of(new Variant("Expected 'extern', 'library', 'abi', 'ownership' or '}' in foreign module", "esperado 'extern', 'library', 'abi', 'ownership' ou '}' no módulo estrangeiro")));
        m.put("PARSE099", List.of(new Variant("foreign module '{0}': unknown ownership '{1}' (expected one of {2})", "módulo estrangeiro '{0}': ownership desconhecido '{1}' (esperado um de {2})")));
        m.put("SEM001", List.of(new Variant("Cannot apply '{0}' to String and {1}", "não é possível aplicar '{0}' a String e {1}"), new Variant("Cannot apply '{0}' to non-numeric type {1} (declare the parameter type, e.g. (x: Int) -> ...)", "não é possível aplicar '{0}' a um tipo não numérico {1} (declare o tipo do parâmetro, ex.: (x: Int) -> ...)")));
        m.put("SEM002", List.of(new Variant("Cannot apply '{0}' to boolean types. Use == or != for comparison.", "não é possível aplicar '{0}' a tipos booleanos. Use == ou != para comparação.")));
        m.put("SEM010", List.of(new Variant("Return type mismatch: expected '{0}' but got '{1}'", "incompatibilidade de tipo de retorno: esperado '{0}' mas obtido '{1}'")));
        m.put("SEM011", List.of(new Variant("Undefined variable or type: '{0}'", "variável ou tipo indefinido: '{0}'"), new Variant("Undefined variable or type: '{0}' in {1} — declare the type or fix the name (R6: undefined declared types must not compile)", "variável ou tipo indefinido: '{0}' em {1} — declare o tipo ou corrija o nome (R6: tipos declarados indefinidos não podem compilar)"), new Variant("{0} is a top-level function, not a value in argument position — pass the call wrapped in a lambda: () -> {1}()", "{0} é uma função de nível de módulo, não um valor em posição de argumento — passe a chamada envolvida em uma lambda: () -> {1}()")));
        m.put("SEM012", List.of(new Variant("Type mismatch: cannot assign {0} to {1}", "incompatibilidade de tipo: não é possível atribuir {0} a {1}")));
        m.put("SEM013", List.of(new Variant("Wrong number of arguments for '{0}': expected {1} but got {2}", "número de argumentos errado para '{0}': esperado {1} mas obtido {2}")));
        m.put("SEM014", List.of(new Variant("Argument {0} of '{1}': expected '{2}' but got '{3}'", "argumento {0} de '{1}': esperado '{2}' mas obtido '{3}'"), new Variant("Argument {0} of '{1}': expected {2} but got {3} (no constructor matches the argument types)", "argumento {0} de '{1}': esperado {2} mas obtido {3} (nenhum construtor casa com os tipos dos argumentos)")));
        m.put("SEM015", List.of(new Variant("Undefined function: '{0}'", "função indefinida: '{0}'"), new Variant("variable '{0}' is not a function and cannot be called{1}", "a variável '{0}' não é uma função e não pode ser chamada{1}")));
        m.put("SEM016", List.of(new Variant("method '{0}' does not exist in superclass '{1}'", "o método '{0}' não existe na superclasse '{1}'")));
        m.put("SEM017", List.of(new Variant("{0}{1}' with {2} argument(s)", "{0}{1}' com {2} argumento(s)")));
        m.put("SEM020", List.of(new Variant("undefined variable: '{0}'", "variável indefinida: '{0}'")));
        m.put("SEM021", List.of(new Variant("type mismatch: cannot assign {0} to '{1}: {2}'", "incompatibilidade de tipo: não é possível atribuir {0} a '{1}: {2}'"), new Variant("type mismatch: cannot assign {0} to field '{1}: {2}'", "incompatibilidade de tipo: não é possível atribuir {0} ao campo '{1}: {2}'")));
        m.put("SEM023", List.of(new Variant("no constructor of '{0}' with {1} argument(s)", "nenhum construtor de '{0}' com {1} argumento(s)"), new Variant("no public constructor of '{0}' with {1} argument(s)", "nenhum construtor público de '{0}' com {1} argumento(s)"), new Variant("no constructor of '{0}' with {1} argument(s) (expected {2})", "nenhum construtor de '{0}' com {1} argumento(s) (esperado {2})")));
        m.put("SEM024", List.of(new Variant("variable '{0}' is already defined in this scope", "a variável '{0}' já está definida neste escopo")));
        m.put("SEM025", List.of(new Variant("List.zip takes exactly one List argument", "List.zip exige exatamente um argumento List"), new Variant("Set does not support indexing [i]; use contains(x) for membership or keys() to iterate", "Set não suporta indexação [i]; use contains(x) para pertinência ou keys() para iterar"), new Variant("Cannot resolve method '{0}' on 'process' (valid: run, spawn, exit)", "não é possível resolver o método '{0}' em 'process' (válidos: run, spawn, exit)"), new Variant("Cannot resolve method '{0}' on type 'Channel' (valid: send, receive)", "não é possível resolver o método '{0}' no tipo 'Channel' (válidos: send, receive)"), new Variant("Cannot resolve method '{0}' on type 'List' (valid: add/get/set/remove/contains/size/isEmpty/clear/map/filter/reduce/indexOf/lastIndexOf/addAll/subList/take/drop/slice/sort/any/all/none/find/forEach/flatMap/distinct/sorted/groupBy/zip)", "não é possível resolver o método '{0}' no tipo 'List' (válidos: add/get/set/remove/contains/size/isEmpty/clear/map/filter/reduce/indexOf/lastIndexOf/addAll/subList/take/drop/slice/sort/any/all/none/find/forEach/flatMap/distinct/sorted/groupBy/zip)"), new Variant("Cannot resolve method '{0}' on type 'Map' (valid: put/get/getOrDefault/putIfAbsent/remove/containsKey/contains/containsValue/size/clear/isEmpty/keys/values)", "não é possível resolver o método '{0}' no tipo 'Map' (válidos: put/get/getOrDefault/putIfAbsent/remove/containsKey/contains/containsValue/size/clear/isEmpty/keys/values)"), new Variant("Cannot resolve method '{0}' on type 'Set' (valid: add/contains/remove/size/clear/isEmpty)", "não é possível resolver o método '{0}' no tipo 'Set' (válidos: add/contains/remove/size/clear/isEmpty)"), new Variant("Handle<T> has no method '{0}()'; use `await h` to get the value", "Handle<T> não tem o método '{0}()'; use `await h` para obter o valor"), new Variant("List.zip takes a List argument; '{0}' is not a List", "List.zip exige um argumento List; '{0}' não é uma List"), new Variant("List.{0} takes exactly one lambda argument", "List.{0} exige exatamente um argumento lambda"), new Variant("array does not have method '{0}'", "o array não tem o método '{0}'"), new Variant("array does not have method '{0}' (use .length for size)", "o array não tem o método '{0}' (use .length para o tamanho)"), new Variant("Cannot resolve field '{0}' on type '{1}'", "não é possível resolver o campo '{0}' no tipo '{1}'"), new Variant("Cannot resolve method '{0}' in superclass '{1}'", "não é possível resolver o método '{0}' na superclasse '{1}'"), new Variant("Cannot resolve method '{0}' on 'shell' (valid: {1})", "não é possível resolver o método '{0}' em 'shell' (válidos: {1})"), new Variant("Cannot resolve method '{0}' on 'ssh' (valid: {1})", "não é possível resolver o método '{0}' em 'ssh' (válidos: {1})"), new Variant("Cannot resolve method '{0}' on namespace 'json' — {1}", "não é possível resolver o método '{0}' no namespace 'json' — {1}"), new Variant("Cannot resolve method '{0}' on namespace '{1}'", "não é possível resolver o método '{0}' no namespace '{1}'"), new Variant("Cannot resolve method '{0}' on type '{1}'", "não é possível resolver o método '{0}' no tipo '{1}'"), new Variant("Cannot resolve static field '{0}' on type '{1}'", "não é possível resolver o campo estático '{0}' no tipo '{1}'"), new Variant("Cannot resolve static method '{0}' on imported class '{1}'", "não é possível resolver o método estático '{0}' na classe importada '{1}'"), new Variant("cannot assign to external static field '{0}.{1}' (external class fields are read-only)", "não é possível atribuir ao campo estático externo '{0}.{1}' (campos de classe externa são somente leitura)"), new Variant("'{0}' does not have method '{1}' with {2} argument(s)", "'{0}' não tem o método '{1}' com {2} argumento(s)")));
        m.put("SEM026", List.of(new Variant("throw requires a String (exceptions are Strings in Kof), got {0}", "throw exige um String (exceções são Strings no Kof), obtido {0}")));
        m.put("SEM027", List.of(new Variant("assignment is a statement, not an expression (use '=' on its own line)", "atribuição é uma declaração, não uma expressão (use '=' em sua própria linha)")));
        m.put("SEM028", List.of(new Variant("array has no method '{0}()'{1}", "o array não tem o método '{0}()'{1}")));
        m.put("SEM029", List.of(new Variant("method '{0}' is not supported on collections; use a loop with new T[n] to materialize an array", "o método '{0}' não é suportado em coleções; use um laço com new T[n] para materializar um array")));
        m.put("SEM030", List.of(new Variant("enum '{0}' has no constant '{1}'", "o enum '{0}' não tem a constante '{1}'")));
        m.put("SEM031", List.of(new Variant("switch on '{0}' does not cover: {1} (add a default or the missing cases)", "o switch sobre '{0}' não cobre: {1} (adicione um default ou os casos ausentes)")));
        m.put("SEM032", List.of(new Variant("switch expression on Boolean does not cover all values (true and false)", "a expressão switch sobre Boolean não cobre todos os valores (true e false)"), new Variant("switch expression requires 'default' (or enum exhaustiveness)", "a expressão switch exige 'default' (ou exaustividade de enum)"), new Variant("switch on Boolean does not cover all values (true and false)", "o switch sobre Boolean não cobre todos os valores (true e false)"), new Variant("switch expression on '{0}' does not cover: {1} (add a default or the missing cases)", "a expressão switch sobre '{0}' não cobre: {1} (adicione um default ou os casos ausentes)")));
        m.put("SEM033", List.of(new Variant("assignment to '{0}' received a void value — the call does not return a value", "a atribuição a '{0}' recebeu um valor void — a chamada não retorna um valor"), new Variant("{0}(...) received a void value — the call does not return a value (add a 'return' or don't use it as an argument)", "{0}(...) recebeu um valor void — a chamada não retorna um valor (adicione um 'return' ou não a use como argumento)")));
        m.put("SEM034", List.of(new Variant("method '{0}' is not supported on collections (collection return is not materializable); copy the elements with a loop", "o método '{0}' não é suportado em coleções (o retorno de coleção não é materializável); copie os elementos com um laço")));
        m.put("SEM035", List.of(new Variant("case of primitive type is not supported in pattern matching (use a reference type or the value directly)", "caso de tipo primitivo não é suportado em pattern matching (use um tipo de referência ou o valor diretamente)")));
        m.put("SEM036", List.of(new Variant("{0} declares return type '{1}' but may finish without return/throw", "{0} declara o tipo de retorno '{1}' mas pode terminar sem return/throw")));
        m.put("SEM037", List.of(new Variant("cannot assign to immutable 'val' variable '{0}'", "não é possível atribuir à variável imutável 'val' '{0}'")));
        m.put("SEM038", List.of(new Variant("cannot assign to '{0}': record is immutable", "não é possível atribuir a '{0}': record é imutável")));
        m.put("SEM041", List.of(new Variant("cannot instantiate abstract class '{0}'", "não é possível instanciar a classe abstrata '{0}'"), new Variant("abstract method '{0}' is not allowed in non-abstract class '{1}' (declare the class as 'abstract')", "método abstrato '{0}' não é permitido em classe não abstrata '{1}' (declare a classe como 'abstract')")));
        m.put("SEM042", List.of(new Variant("nested type declaration is not supported: declare '{0}' at top level", "declaração de tipo aninhado não é suportada: declare '{0}' no nível de módulo")));
        m.put("SEM043", List.of(new Variant("class '{0}' does not implement abstract method '{1}()' from superclass '{2}'", "a classe '{0}' não implementa o método abstrato '{1}()' da superclasse '{2}'"), new Variant("class '{0}' does not implement method '{1}' of interface '{2}'{3}", "a classe '{0}' não implementa o método '{1}' da interface '{2}'{3}"), new Variant("method '{0}' of interface '{1}' expects {2} parameter(s) but implementation has {3}", "o método '{0}' da interface '{1}' espera {2} parâmetro(s) mas a implementação tem {3}")));
        m.put("SEM044", List.of(new Variant("main() must be declared without modifiers: 'main() { ... }' (found {0})", "main() deve ser declarado sem modificadores: 'main() { ... }' (encontrado {0})"), new Variant("main() must have no return type: 'main() { ... }' (found '{0} main(...)')", "main() não deve ter tipo de retorno: 'main() { ... }' (encontrado '{0} main(...)')")));
        m.put("SEM045", List.of(new Variant("throw clause of {0} references unknown type '{1}'", "a cláusula throw de {0} referencia um tipo desconhecido '{1}'")));
        m.put("SEM046", List.of(new Variant("{0} is private (declared in '{1}') and cannot be accessed from '{2}'", "{0} é privado (declarado em '{1}') e não pode ser acessado de '{2}'"), new Variant("{0} is protected (declared in '{1}') and cannot be accessed from '{2}'", "{0} é protegido (declarado em '{1}') e não pode ser acessado de '{2}'"), new Variant("{0} is {1} (declared in '{2}') and cannot be accessed from top-level code", "{0} é {1} (declarado em '{2}') e não pode ser acessado de código de nível de módulo")));
        m.put("SEM047", List.of(new Variant("function '{0}' with parameters ({1}) is already defined at line {2}; duplicate signatures are not allowed — overload requires a DIFFERENT parameter list", "a função '{0}' com parâmetros ({1}) já está definida na linha {2}; assinaturas duplicadas não são permitidas — sobrecarga exige uma LISTA de parâmetros DIFERENTE")));
        m.put("SEM048", List.of(new Variant("null cannot be assigned: null safety works by narrowing (if (x != null)), never by direct null literals", "null não pode ser atribuído: a segurança contra null funciona por estreitamento (if (x != null)), nunca por literais null diretos"), new Variant("null cannot be assigned: null safety works by narrowing (if (x != null)), never by direct null literals (variable '{0}')", "null não pode ser atribuído: a segurança contra null funciona por estreitamento (if (x != null)), nunca por literais null diretos (variável '{0}')"), new Variant("null cannot be passed as argument {0} of '{1}()': primitive parameter is non-nullable and null is never fabricable — declare the parameter '{2}?' to accept an absent value, or pass a real value", "null não pode ser passado como argumento {0} de '{1}(): parâmetro primitivo não é anulável e null nunca é fabricável — declare o parâmetro '{2}?' para aceitar um valor ausente, ou passe um valor real")));
        m.put("SEM049", List.of(new Variant("receiver is nullable (T?); narrow first: if (x != null) { x.field = v }", "o receptor é anulável (T?); estreite antes: if (x != null) { x.field = v }"), new Variant("receiver is nullable (T?); narrow first: if (x != null) { x.field }", "o receptor é anulável (T?); estreite antes: if (x != null) { x.field }"), new Variant("receiver is nullable (T?); narrow first: if (x != null) { x.method() }", "o receptor é anulável (T?); estreite antes: if (x != null) { x.method() }")));
        m.put("SEM050", List.of(new Variant("'{0}' is a primitive type, it has no static field '{1}' (use the literal, e.g. 2147483647 for Int; there is no Int.MAX_VALUE in Kof)", "'{0}' é um tipo primitivo, não tem campo estático '{1}' (use o literal, ex. 2147483647 para Int; não existe Int.MAX_VALUE no Kof)")));
        m.put("SEM051", List.of(new Variant("String.{0} does not accept {1} as argument {2} (the parameter is String); use a String literal, e.g.: {3}(\"c\")", "String.{0} não aceita {1} como argumento {2} (o parâmetro é String); use um literal String, ex.: {3}(\"c\")")));
        m.put("SEM052", List.of(new Variant("Kof has no \"{0}\" method on String; use the stdlib function: strings.{1}(...", "Kof não tem o método \"{0}\" em String; use a função da stdlib: strings.{1}(..."), new Variant("Kof has no \"{0}\" method on {1}; use the stdlib function on an Int/Long, e.g.: n.toLong().{2}()", "Kof não tem o método \"{0}\" em {1}; use a função da stdlib sobre um Int/Long, ex.: n.toLong().{2}()")));
        m.put("SEM053", List.of(new Variant("Kof has no operator '{0}' for String (lexicographic order is Unspecified — it diverges per target); use: s.compareTo(t) {1}", "Kof não tem o operador '{0}' para String (a ordem lexicográfica é Não Especificada — diverge por alvo); use: s.compareTo(t) {1}")));
        m.put("SEM054", List.of(new Variant("`[]` assignment only works on arrays in Kof; for a List use l.set(i, v)", "a atribuição `[]` só funciona em arrays no Kof; para uma List use l.set(i, v)"), new Variant("`[]` only indexes arrays in Kof; for this collection use {0}", "`[]` só indexa arrays no Kof; para esta coleção use {0}")));
        m.put("SEM055", List.of(new Variant("List.subList takes Int INDEX bounds; {0} is not an index", "List.subList exige limites de ÍNDICE Int; {0} não é um índice"), new Variant("List.{0} takes an Int INDEX; {1} is not an index (to search by value use contains)", "List.{0} exige um ÍNDICE Int; {1} não é um índice (para buscar por valor use contains)")));
        m.put("SEM056", List.of(new Variant("Set.add: element {0} does not match the set element type ({1}) — Kof collections are homogeneous", "Set.add: o elemento {0} não casa com o tipo de elemento do set ({1}) — coleções do Kof são homogêneas"), new Variant("listOf: element {0} does not match the list element type ({1}) — Kof collections are homogeneous", "listOf: o elemento {0} não casa com o tipo de elemento da lista ({1}) — coleções do Kof são homogêneas"), new Variant("mapOf: value {0} does not match the map type ({1}) — Kof collections are homogeneous", "mapOf: o valor {0} não casa com o tipo do map ({1}) — coleções do Kof são homogêneas"), new Variant("List.{0}: element {1} does not match the list element type ({2}) — Kof collections are homogeneous", "List.{0}: o elemento {1} não casa com o tipo de elemento da lista ({2}) — coleções do Kof são homogêneas"), new Variant("Map.{0}: {1} {2} does not match the map type ({3}) — Kof collections are homogeneous", "Map.{0}: {1} {2} não casa com o tipo do map ({3}) — coleções do Kof são homogêneas")));
        m.put("SEM057", List.of(new Variant("call to '{0}' is ambiguous between {1} overloads — add a cast to pick one", "a chamada a '{0}' é ambígua entre {1} sobrecargas — adicione um cast para escolher uma")));
        m.put("SEM058", List.of(new Variant("`for-in` only iterates over `List<T>` or arrays in Kof; for String use `s.charAt(i)` in a numeric loop", "`for-in` só itera sobre `List<T>` ou arrays no Kof; para String use `s.charAt(i)` em um laço numérico")));
        m.put("SEM059", List.of(new Variant("method '{0}' in class '{1}' overrides '{2}' but return type {3} is not compatible with the overridden return type {4}", "o método '{0}' na classe '{1}' sobrepõe '{2}' mas o tipo de retorno {3} não é compatível com o tipo de retorno sobreposto {4}")));
        m.put("SEM060", List.of(new Variant("cannot call instance method '{0}.{1}()' without a receiver — use 'this.{2}()' inside an instance method of '{3}' or call it on an instance (method() is not static)", "não é possível chamar o método de instância '{0}.{1}()' sem um receptor — use 'this.{2}()' dentro de um método de instância de '{3}' ou o chame sobre uma instância (method() não é estático)")));
        m.put("SEM061", List.of(new Variant("'{0}' is already defined in {1} '{2}' at line {3} — same JVM descriptor; overload requires a DIFFERENT parameter or return type (static and instance do not differ here)", "'{0}' já está definido em {1} '{2}' na linha {3} — mesmo descritor JVM; sobrecarga exige um tipo de parâmetro ou retorno DIFERENTE (estático e instância não diferem aqui)")));
        m.put("SEM062", List.of(new Variant("Cannot compare an enum value to a String: an enum constant is not a String (D-ENUM207). Compare two enum values, or use .name() explicitly to get the name", "não é possível comparar um valor de enum a uma String: uma constante de enum não é uma String (D-ENUM207). Compare dois valores de enum, ou use .name() explicitamente para obter o nome")));
        m.put("SEM064", List.of(new Variant("interface '{0}' cannot extend class '{1}' (interfaces may only extend interfaces)", "a interface '{0}' não pode estender a classe '{1}' (interfaces só podem estender interfaces)")));
        m.put("SEM065", List.of(new Variant("cannot assign to final field '{0}' (declared in '{1}') from outside its constructor", "não é possível atribuir ao campo final '{0}' (declarado em '{1}') de fora do seu construtor")));
        m.put("SEM066", List.of(new Variant("String has no \"{0}\" method — looks like a collection accessor; the raw row from db.query is a JSON String: use db.query<Record> (typed) or json.decode<Map<String, Object>>(row)", "String não tem o método \"{0}\" — parece um acessor de coleção; a linha crua de db.query é uma String JSON: use db.query<Record> (tipado) ou json.decode<Map<String, Object>>(row)")));
        m.put("SEM067", List.of(new Variant("catch type '{0}' is a primitive — primitives are not throwable (Kof exceptions are Strings: use `catch (String e)`)", "o tipo do catch '{0}' é um primitivo — primitivos não são lançáveis (exceções do Kof são Strings: use `catch (String e)`)")));
        m.put("SEM068", List.of(new Variant("catch type '{0}' is not a Throwable subclass — Kof exceptions are Strings (use `catch (String e)`) or a JDK/user throwable", "o tipo do catch '{0}' não é subclasse de Throwable — exceções do Kof são Strings (use `catch (String e)`) ou um throwable do JDK/usuário")));
        m.put("SEM069", List.of(new Variant("class '{0}' cannot be both 'final' and 'abstract' — 'final' forbids subclasses, 'abstract' requires them", "a classe '{0}' não pode ser 'final' e 'abstract' ao mesmo tempo — 'final' proíbe subclasses, 'abstract' as exige")));
        m.put("SEM070", List.of(new Variant("cannot extend record '{0}' — records are implicitly final; compose it (hold it in a field) or use a plain class", "não é possível estender o record '{0}' — records são implicitamente final; componha-o (guarde-o em um campo) ou use uma classe comum"), new Variant("cannot inherit from final class '{0}' (declared 'final' — remove 'final' or the inheritance)", "não é possível herdar da classe final '{0}' (declarada 'final' — remova o 'final' ou a herança)")));
        m.put("SEM071", List.of(new Variant("cannot instantiate interface '{0}' — declare a class that implements it", "não é possível instanciar a interface '{0}' — declare uma classe que a implemente")));
        m.put("SEM072", List.of(new Variant("List.{0} appends exactly one element; there is no positional insert — use set(index, value) to replace at an index", "List.{0} acrescenta exatamente um elemento; não há inserção posicional — use set(index, value) para substituir em um índice")));
        m.put("SEM073", List.of(new Variant("List.reduce takes exactly two arguments: the lambda AND the seed — reduce((a: Int, b: Int) -> a + b, 0) or reduce(0, (a: Int, b: Int) -> a + b)", "List.reduce exige exatamente dois argumentos: a lambda E a semente — reduce((a: Int, b: Int) -> a + b, 0) ou reduce(0, (a: Int, b: Int) -> a + b)")));
        m.put("SEM074", List.of(new Variant("'{0}' has no static method '{1}()'", "'{0}' não tem o método estático '{1}()'"), new Variant("'{0}' is a primitive — it has no method '{1}()' (primitives have toString() and the conversions toInt()/toLong()/toFloat()/toDouble(); comparison is `a == b`, math is top-level functions, e.g. math.abs(x))", "'{0}' é um primitivo — não tem o método '{1}()' (primitivos têm toString() e as conversões toInt()/toLong()/toFloat()/toDouble(); a comparação é `a == b`, a matemática são funções de nível de módulo, ex. math.abs(x))")));
        m.put("SEM075", List.of(new Variant("static method cannot reference instance field '{0}' (no implicit 'this' in a static context; use an instance, or declare the field 'static')", "método estático não pode referenciar o campo de instância '{0}' (não há 'this' implícito em contexto estático; use uma instância, ou declare o campo como 'static')")));
        m.put("SEM076", List.of(new Variant("style: unknown property '{0}' — not in the kof.ui style whitelist", "style: propriedade desconhecida '{0}' — não está na whitelist de style do kof.ui"), new Variant("'{0}' is already defined in class '{1}' at line {2} — a class cannot declare two fields with the same name (static or not, any type); rename one", "'{0}' já está definido na classe '{1}' na linha {2} — uma classe não pode declarar dois campos com o mesmo nome (estático ou não, qualquer tipo); renomeie um")));
        m.put("SEM077", List.of(new Variant("style: the declaration string must be a literal", "style: a string de declaração deve ser um literal"), new Variant("style: the declaration string must be a literal — the 4-Int Style(background, foreground, padding, radius) form takes a computed Color", "style: a string de declaração deve ser um literal — a forma Style(background, foreground, padding, radius) de 4 Ints aceita um Color computado"), new Variant("style: malformed declaration '{0}' — expected 'property: value'", "style: declaração malformada '{0}' — esperado 'propriedade: valor'")));
        m.put("SEM078", List.of(new Variant("style: property '{0}' has an empty value", "style: a propriedade '{0}' tem um valor vazio"), new Variant("style: invalid value for '{0}': {1}", "style: valor inválido para '{0}': {1}")));
        m.put("SEM079", List.of(new Variant("'Palette.{0}' is not a palette color (use a named color, e.g. Palette.red)", "'Palette.{0}' não é uma cor da paleta (use uma cor nomeada, ex. Palette.red)"), new Variant("'{0}' has no field '{1}' (UI types expose functions, e.g. Color.rgba(...), Theme.light())", "'{0}' não tem o campo '{1}' (tipos de UI expõem funções, ex. Color.rgba(...), Theme.light())"), new Variant("token '{0}' has no methods — it holds constants: {1}", "o token '{0}' não tem métodos — ele guarda constantes: {1}"), new Variant("unknown token '{0}.{1}' — {2} has: {3}", "token desconhecido '{0}.{1}' — {2} tem: {3}")));
        m.put("SEM080", List.of(new Variant("subtype '{0}' of sealed type '{1}' must be declared in the same compilation unit as '{2}' — a sealed type's subtype set is closed, and subtypes declared elsewhere are not known to the compiler", "o subtipo '{0}' do tipo selado '{1}' deve ser declarado na mesma unidade de compilação que '{2}' — o conjunto de subtipos de um tipo selado é fechado, e subtipos declarados em outro lugar não são conhecidos pelo compilador")));
        m.put("SEM081", List.of(new Variant("switch expression on sealed type '{0}' does not cover: {1} (add a default or the missing cases)", "a expressão switch sobre o tipo selado '{0}' não cobre: {1} (adicione um default ou os casos ausentes)"), new Variant("switch on sealed type '{0}' does not cover: {1} (add a default or the missing cases)", "o switch sobre o tipo selado '{0}' não cobre: {1} (adicione um default ou os casos ausentes)")));
        m.put("SEM082", List.of(new Variant("type parameter '{0}' is declared 'in' but occurs in an output position ({1}) — an 'in' parameter is contravariant, so it may appear only in input positions (parameters); a readable use would let a contravariant alias expose a value of the wrong type", "o parâmetro de tipo '{0}' é declarado 'in' mas ocorre em uma posição de saída ({1}) — um parâmetro 'in' é contravariante, então só pode aparecer em posições de entrada (parâmetros); um uso de leitura permitiria que um alias contravariante expusesse um valor de tipo errado"), new Variant("type parameter '{0}' is declared 'out' but occurs in an input position ({1}) — an 'out' parameter is covariant, so it may appear only in output positions (return types, read-only components); a writable use would let a covariant alias store a value of the wrong type", "o parâmetro de tipo '{0}' é declarado 'out' mas ocorre em uma posição de entrada ({1}) — um parâmetro 'out' é covariante, então só pode aparecer em posições de saída (tipos de retorno, componentes somente-leitura); um uso gravável permitiria que um alias covariante armazenasse um valor de tipo errado")));
        m.put("SEM083", List.of(new Variant("type parameter '{0}' is declared 'in' but is passed as {1} to supertype '{2}' — a contravariant parameter cannot appear in a supertype position that exposes it (invariant/covariant); the supertype would let a contravariant alias expose a value of the wrong type", "o parâmetro de tipo '{0}' é declarado 'in' mas é passado como {1} ao supertipo '{2}' — um parâmetro contravariante não pode aparecer em uma posição de supertipo que o exponha (invariante/covariante); o supertipo permitiria que um alias contravariante expusesse um valor de tipo errado"), new Variant("type parameter '{0}' is declared 'out' but is passed as {1} to supertype '{2}' — a covariant parameter cannot appear in a supertype position that consumes it (invariant/contravariant); the supertype would let a covariant alias store a value of the wrong type", "o parâmetro de tipo '{0}' é declarado 'out' mas é passado como {1} ao supertipo '{2}' — um parâmetro covariante não pode aparecer em uma posição de supertipo que o consuma (invariante/contravariante); o supertipo permitiria que um alias covariante armazenasse um valor de tipo errado")));
        m.put("SEM085", List.of(new Variant("function type with a type-parameter of the owner in '{0}' in {1} — generic function types are not lowered yet (erasure ABI is 1.0-line); the form is rejected instead of compiling to a load crash (SEM085)", "tipo de função com um parâmetro de tipo do dono em '{0}' em {1} — tipos de função genéricos ainda não são rebaixados (a ABI de erasure é da linha 1.0); a forma é recusada em vez de compilar para uma falha de load (SEM085)")));
        m.put("SEM087", List.of(new Variant("Undefined superclass or interface: '{0}' in {1} — import the type or fix the name (R6: a raw super fails at load)", "superclasse ou interface indefinida: '{0}' em {1} — importe o tipo ou corrija o nome (R6: um super cru falha no load)")));
        m.put("SEM090", List.of(new Variant("'{0}()' may truncate (fractional part discarded; overflow throws) — explicit form: value as {1}", "'{0}()' pode truncar (a parte fracionária é descartada; estouro lança exceção) — forma explícita: value as {1}")));
        m.put("SEM091", List.of(new Variant("modificador '{0}' has no effect in Kof (a non-goal of the memory model — concurrency-memory-model.md §5); use a language abstraction: spawn/await/Channel for concurrency{1}", "o modificador '{0}' não tem efeito no Kof (não-objetivo do modelo de memória — concurrency-memory-model.md §5); use uma abstração da linguagem: spawn/await/Channel para concorrência{1}")));
        m.put("SEM092", List.of(new Variant("self-referencing initializer var inside a lambda (var '{0}') is not available on the {1} target yet — captured handle read in the job crashes the worker (§253 face B, native lane); use the shadow-handle idiom (var id=\"\"; job reads id after assignment; id = time.interval(…) after) (SEM092)", "a variável de inicialização autorreferente dentro de uma lambda (var '{0}') ainda não está disponível no alvo {1} — a leitura do handle capturado no job derruba o worker (§253 face B, lane nativa); use o idioma de handle-sombra (var id=\"\"; o job lê id depois da atribuição; id = time.interval(…) depois) (SEM092)")));
        m.put("SEM093", List.of(new Variant("void function cannot return a value - drop the value (bare `return` exits) or declare a return type", "função void não pode retornar um valor - descarte o valor (`return` sozinho encerra) ou declare um tipo de retorno")));
        m.put("SEM095", List.of(new Variant("'Bool' has two values; for true/false/unknown use 'Troolean' (DECISIONS.md D-TROOL)", "'Bool' tem dois valores; para true/false/unknown use 'Troolean' (DECISIONS.md D-TROOL)")));
        m.put("SEM096", List.of(new Variant("Buffer element must be U8 (Byte) — got '{0}'", "o elemento de Buffer deve ser U8 (Byte) — obtido '{0}'"), new Variant("{0}() needs an argument — println and print take the value to print (println(x)); for a blank line use println(\"\")", "{0}() precisa de um argumento — println e print recebem o valor a imprimir (println(x)); para uma linha em branco use println(\"\")")));
        m.put("SEM097", List.of(new Variant("List.sort/sorted needs elements with a natural order (Int/Long/Double/Float/Bool/Char/String); '{0}' has none — use sorted((a, b) -> Int) with an explicit comparator instead", "List.sort/sorted exige elementos com ordem natural (Int/Long/Double/Float/Bool/Char/String); '{0}' não tem — use sorted((a, b) -> Int) com um comparador explícito")));
        m.put("SEM098", List.of(new Variant("cannot store a primitive array (Int[]) into '{0}' of erased reference-array type T[] (erases to Object[] on the JVM — int[] is not a subtype of Object[]). Use List<Int> (the Kof idiom for a growable sequence of primitives) or a reference-typed array slot", "não é possível armazenar um array de primitivos (Int[]) em '{0}' de tipo de array de referência apagado T[] (apaga para Object[] na JVM — int[] não é subtipo de Object[]). Use List<Int> (o idioma do Kof para uma sequência crescente de primitivos) ou um slot de array de tipo de referência")));
        m.put("SEM099", List.of(new Variant("cannot pass a List to '{0}': the kof.io bytes faces take an Int[] primitive array (training/language/io.md contract) — a List is not an array on any target (VerifyError on the JVM, crash under the interpreter, silent mismatch in KofJS). Fill a primitive array: val a = new Int[n] with a[i] = v, then f.{1}(a)", "não é possível passar uma List para '{0}': as faces de bytes do kof.io recebem um array de primitivos Int[] (contrato training/language/io.md) — uma List não é um array em nenhum alvo (VerifyError na JVM, falha sob o interpretador, incompatibilidade silenciosa no KofJS). Preencha um array de primitivos: val a = new Int[n] com a[i] = v, depois f.{1}(a)")));
        m.put("SEM100", List.of(new Variant("an 'as' cast is not a parse: '{0}' does not cast to '{1}' (an implicit String conversion would be a hidden parse) — use the stdlib parsers (math.parseInt / math.parseFloat / math.parseDouble / math.parseChar)", "um cast 'as' não é uma conversão: '{0}' não faz cast para '{1}' (uma conversão implícita para String seria uma análise oculta) — use os analisadores da stdlib (math.parseInt / math.parseFloat / math.parseDouble / math.parseChar)")));
        m.put("SEM101", List.of(new Variant("class '{0}' inherits conflicting default method '{1}' from unrelated interfaces '{2}' and '{3}' (add an explicit override in '{4}')", "a classe '{0}' herda um método default conflitante '{1}' de interfaces não aparentadas '{2}' e '{3}' (adicione uma sobreposição explícita em '{4}')")));
        m.put("SEM102", List.of(new Variant("'{0}' has no field '{1}' (this builtin exposes methods, not properties)", "'{0}' não tem o campo '{1}' (este builtin expõe métodos, não propriedades)"), new Variant("'{0}' has no method '{1}()'", "'{0}' não tem o método '{1}()'"), new Variant("'{0}' has no field '{1}'{2}", "'{0}' não tem o campo '{1}'{2}"), new Variant("'{0}' has no method '{1}()'{2}", "'{0}' não tem o método '{1}()'{2}"), new Variant("'{0}' is a builtin namespace; it has no field '{1}' (namespaces expose functions, not properties — use {2}.someFunction(...))", "'{0}' é um namespace builtin; não tem o campo '{1}' (namespaces expõem funções, não propriedades — use {2}.algumaFuncao(...))")));
        // @@CATALOG_END@@
        return java.util.Collections.unmodifiableMap(m);
    }
}
