#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""D-PORTUKOF F6 — autora PT-BR do catálogo de diagnósticos.

Preenche o campo `pt` de `scripts/portukof_diag_pt.json` (produzido pelo scan de
`gen_portukof_diags.py`) via:
  1. `OVERRIDES`: tradução exata EN->PT (natural, de ferramenta de programação
     brasileira) para TODO texto canônico — placeholders {i}, tokens entre aspas,
     backticks e exemplos de código Kof ficam VERBATIM;
  2. nenhuma tradução por regex sobre a mensagem final: a chave é o texto EN do
     template; `gen_portukof_diags.py --check` trava paridade de placeholders.
Idempotente: só toca `pt`. Rodar `gen_portukof_diags.py` depois para emitir o Java.
"""
import json, pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
JSON = ROOT / "scripts" / "portukof_diag_pt.json"

OVERRIDES = {
    # ---------------- LEX ----------------
    "Unterminated block comment": "comentário de bloco não terminado",
    "Unterminated character literal": "literal de caractere não terminado",
    "Unterminated string literal": "literal de texto não terminado",
    "Empty character literal": "literal de caractere vazio",
    "Incomplete unicode escape (expected \\uXXXX)": "escape unicode incompleto (esperado \\uXXXX)",
    "Invalid unicode escape: \\u{0}": "escape unicode inválido: \\u{0}",
    "Unexpected character: '{0}'": "caractere inesperado: '{0}'",
    "Kof has no triple-quoted (raw/multiline) string literal: use \"...\" with \\n (no interpolation either — concatenate with +)":
        "Kof não tem literal de texto triplamente aspas (raw/multilinha): use \"...\" com \\n "
        "(também sem interpolação — concatene com +)",
    # ---------------- PARSE (Expected/Unexpected) ----------------
    "Expected package name": "esperado nome de pacote",
    "Expected package name component": "esperado componente do nome de pacote",
    "Expected import name": "esperado nome de import",
    "Expected import path component": "esperado componente do caminho de import",
    "Expected type declaration": "esperada declaração de tipo",
    "Expected class name": "esperado nome de classe",
    "Expected '}' after class body": "esperado '}' depois do corpo da classe",
    "Expected function name": "esperado nome de função",
    "Expected '('": "esperado '('",
    "Expected ')'": "esperado ')'",
    "Expected '{'": "esperado '{'",
    "Expected '}' after record body": "esperado '}' depois do corpo do record",
    "Expected component name": "esperado nome de componente",
    "Unexpected token in class body": "token inesperado no corpo da classe",
    "Expected member name": "esperado nome de membro",
    "Expected ')' after parameters": "esperado ')' depois dos parâmetros",
    "Expected constructor name": "esperado nome de construtor",
    "Expected ')' after constructor parameters": "esperado ')' depois dos parâmetros do construtor",
    "Expected parameter name": "esperado nome de parâmetro",
    "Expected '}'": "esperado '}'",
    "Expected '(' after 'if'": "esperado '(' depois de 'if'",
    "Expected '(' after if": "esperado '(' depois de if",
    "Expected '(' after 'while'": "esperado '(' depois de 'while'",
    "Expected '(' after 'for'": "esperado '(' depois de 'for'",
    "Expected '}' after enum body": "esperado '}' depois do corpo do enum",
    "Expected '{' after enum name (enums are constants-only)":
        "esperado '{' depois do nome do enum (enum é só de constantes)",
    "Expected variable name": "esperado nome de variável",
    "Expected field name": "esperado nome de campo",
    "Expected '->'": "esperado '->'",
    "Expected type": "esperado tipo",
    "Expected ']'": "esperado ']'",
    "Expected onStart/onShutdown block in application": "esperado bloco onStart/onShutdown em application",
    "Expected 'while' after 'do'": "esperado 'while' depois de 'do'",
    "Expected '(' after 'while'": "esperado '(' depois de 'while'",
    "Expected '(' after 'switch'": "esperado '(' depois de 'switch'",
    "Expected '}' after if-expression branch": "esperado '}' depois do ramo da expressão if",
    "expected ':' (switch statement) or '->' (switch expression)":
        "esperado ':' (declaração switch) ou '->' (expressão switch)",
    "Expected '>' after type arguments": "esperado '>' depois dos argumentos de tipo",
    "Expected '->' after 'default'": "esperado '->' depois de 'default'",
    "Expected '<'": "esperado '<'",
    "Expected '>'": "esperado '>'",
    "Expected '>' after type parameters": "esperado '>' depois dos parâmetros de tipo",
    "Expected annotation name": "esperado nome de anotação",
    "Expected ')' after annotation arguments": "esperado ')' depois dos argumentos da anotação",
    "Expected '}' after annotation array": "esperado '}' depois do array da anotação",
    "Invalid numeric literal in annotation": "literal numérico inválido em anotação",
    "numeric literal out of range: {0}": "literal numérico fora da faixa: {0}",
    "invalid float literal: {0}": "literal float inválido: {0}",
    "Expected annotation value": "esperado valor de anotação",
    "'{0}' is a reserved word (Kof has no function keyword); declare as 'Type name(...) { }' or 'name(...): Type { }'":
        "'{0}' é uma palavra reservada (Kof não tem palavra-chave de função); declare como "
        "'Tipo nome(...) { }' ou 'nome(...): Tipo { }'",
    "Wildcard types '? extends/super' are not supported in Kof; use a concrete type or nullable 'T?'":
        "tipos curinga '? extends/super' não são suportados no Kof; use um tipo concreto ou "
        "nullability 'T?'",
    "Expected 'where', 'orderBy' or 'limit' in query block":
        "esperado 'where', 'orderBy' ou 'limit' no bloco de consulta",
    "Expected a library string literal after 'library'": "esperado um literal de texto de biblioteca depois de 'library'",
    "Expected '{' after foreign module name": "esperado '{' depois do nome do módulo estrangeiro",
    "Expected '}' to close foreign module": "esperado '}' para fechar o módulo estrangeiro",
    "Expected foreign module name": "esperado nome de módulo estrangeiro",
    "Expected 'extern', 'library', 'abi', 'ownership' or '}' in foreign module":
        "esperado 'extern', 'library', 'abi', 'ownership' ou '}' no módulo estrangeiro",
    "Expected '}' after interface body": "esperado '}' depois do corpo da interface",
    "Expected '}' after entity body": "esperado '}' depois do corpo da entidade",
    "Expected entity name": "esperado nome de entidade",
    "Expected '{' after entity name": "esperado '{' depois do nome da entidade",
    "Expected enum name": "esperado nome de enum",
    "Expected enum constant": "esperada constante de enum",
    "Expected '{' after application": "esperado '{' depois de application",
    "Expected '}' after application block": "esperado '}' depois do bloco application",
    "Expected '}' after query block": "esperado '}' depois do bloco de consulta",
    "Expected field name in entity": "esperado nome de campo na entidade",
    "Expected interface name": "esperado nome de interface",
    "Expected record name": "esperado nome de record",
    "Expected exception name": "esperado nome de exceção",
    "Expected 'extern'": "esperado 'extern'",
    "Expected extern function name": "esperado nome de função extern",
    "Expected 'enum'": "esperado 'enum'",
    "Expected 'else'": "esperado 'else'",
    "Expected '}' after query block": "esperado '}' depois do bloco de consulta",
    "Expected ':'": "esperado ':'",
    "Expected ':' after field name": "esperado ':' depois do nome do campo",
    "Expected '=' after the resource name in using (name = init, closer)":
        "esperado '=' depois do nome do recurso em using (name = init, closer)",
    "Expected resource name in using (name = init, closer)":
        "esperado nome de recurso em using (name = init, closer)",
    "Expected ')' to close using (name = init, closer)": "esperado ')' para fechar using (name = init, closer)",
    "Expected '(' after 'using'": "esperado '(' depois de 'using'",
    "Expected ')' in Buffer(...)": "esperado ')' em Buffer(...)",
    "Expected '}' after annotation array": "esperado '}' depois do array da anotação",
    "Expected '{' after {0}": "esperado '{' depois de {0}",
    "Expected a value after '{0}'": "esperado um valor depois de '{0}'",
    "Unexpected token in expression: {0}": "token inesperado na expressão: {0}",
    "expected 'case' or 'default' in switch expression": "esperado 'case' ou 'default' na expressão switch",
    "Expected tag string after ',' in test declaration": "esperada string de tag depois de ',' na declaração de teste",
    "Expected test name string": "esperada string de nome de teste",
    "test name must not be empty": "nome de teste não pode ser vazio",
    "test tag must not be empty": "tag de teste não pode ser vazia",
    "infra body accepts only builder calls (resource/prop/requires)":
        "o corpo de infra aceita apenas chamadas de builder (resource/prop/requires)",
    "Expected infra name string": "esperada string de nome de infra",
    "Expected a library string literal after 'library'": "esperado um literal de texto de biblioteca depois de 'library'",
    "foreign module '{0}' must declare a `library \"...\"`": "módulo estrangeiro '{0}' deve declarar um `library \"...\"`",
    "foreign module '{0}': unknown ownership '{1}' (expected one of {2})":
        "módulo estrangeiro '{0}': ownership desconhecido '{1}' (esperado um de {2})",
    "switch expression: each case body is a SINGLE expression (no block scope); use the switch-statement (`case ...:`) for multiple statements":
        "expressão switch: cada corpo de caso é UMA ÚNICA expressão (sem escopo de bloco); use a "
        "declaração switch (`case ...:`) para várias declarações",
    "switch expression requires '->' (the statement form uses ':')":
        "a expressão switch exige '->' (a forma de declaração usa ':')",
    "Expected '}' after class body": "esperado '}' depois do corpo da classe",
    "Expected '}' after record body": "esperado '}' depois do corpo do record",
    "Expected '}' after enum body": "esperado '}' depois do corpo do enum",
    "Expected '}' to close foreign module": "esperado '}' para fechar o módulo estrangeiro",
    "Expected '{' after application": "esperado '{' depois de application",
    "Expected '}' after application block": "esperado '}' depois do bloco application",
    "Expected '}' after interface body": "esperado '}' depois do corpo da interface",
    "Expected '}' after entity body": "esperado '}' depois do corpo da entidade",
    "Expected '}' after query block": "esperado '}' depois do bloco de consulta",
    "Expected '}' after if-expression branch": "esperado '}' depois do ramo da expressão if",
    "Expected '}' after annotation array": "esperado '}' depois do array da anotação",
    "expected ':' (switch statement) or '->' (switch expression)": "esperado ':' (declaração switch) ou '->' (expressão switch)",
    "expected 'case' or 'default' in switch expression": "esperado 'case' ou 'default' na expressão switch",
    "Expected '->' after 'default'": "esperado '->' depois de 'default'",
    "Expected '->'": "esperado '->'",
    "Expected ':' after field name": "esperado ':' depois do nome do campo",
    "Expected ':'": "esperado ':'",
    "Expected '>' after type arguments": "esperado '>' depois dos argumentos de tipo",
    "Expected '>' after type parameters": "esperado '>' depois dos parâmetros de tipo",
    "Expected '>'": "esperado '>'",
    "Expected '<'": "esperado '<'",
    "Expected ']'": "esperado ']'",
    "Expected '{' after foreign module name": "esperado '{' depois do nome do módulo estrangeiro",
    "Expected '{' after entity name": "esperado '{' depois do nome da entidade",
    "Expected '{' after enum name (enums are constants-only)": "esperado '{' depois do nome do enum (enum é só de constantes)",
    "Expected '{' after application": "esperado '{' depois de application",
    "Expected '{'": "esperado '{'",
    "Expected 'while' after 'do'": "esperado 'while' depois de 'do'",
    "Expected 'else'": "esperado 'else'",
    "Expected 'extern', 'library', 'abi', 'ownership' or '}' in foreign module": "esperado 'extern', 'library', 'abi', 'ownership' ou '}' no módulo estrangeiro",
    "Expected 'enum'": "esperado 'enum'",
    "Expected 'extern'": "esperado 'extern'",
    "Expected 'where', 'orderBy' or 'limit' in query block": "esperado 'where', 'orderBy' ou 'limit' no bloco de consulta",
    "Expected '(' after 'using'": "esperado '(' depois de 'using'",
    "Expected '(' after 'switch'": "esperado '(' depois de 'switch'",
    "Expected '(' after 'while'": "esperado '(' depois de 'while'",
    "Expected '(' after 'for'": "esperado '(' depois de 'for'",
    "Expected '(' after 'if'": "esperado '(' depois de 'if'",
    "Expected '(' after if": "esperado '(' depois de if",
    "Expected '('": "esperado '('",
    "Expected ')' after parameters": "esperado ')' depois dos parâmetros",
    "Expected ')' after constructor parameters": "esperado ')' depois dos parâmetros do construtor",
    "Expected ')' after annotation arguments": "esperado ')' depois dos argumentos da anotação",
    "Expected ')' after record components": "esperado ')' depois dos componentes do record",
    "Expected ')' in Buffer(...)": "esperado ')' em Buffer(...)",
    "Expected ')' to close using (name = init, closer)": "esperado ')' para fechar using (name = init, closer)",
    "Expected ')'": "esperado ')'",
    "Expected record name": "esperado nome de record",
    "Expected interface name": "esperado nome de interface",
    "Expected infra name string": "esperada string de nome de infra",
    "Expected import path component": "esperado componente do caminho de import",
    "Expected import name": "esperado nome de import",
    "Expected '}'": "esperado '}'",
    "Expected '}' after class body": "esperado '}' depois do corpo da classe",
    "Expected '}' after record body": "esperado '}' depois do corpo do record",
    "Expected '}' after enum body": "esperado '}' depois do corpo do enum",
    "Expected '}' after interface body": "esperado '}' depois do corpo da interface",
    "Expected '}' after entity body": "esperado '}' depois do corpo da entidade",
    "Expected '}' after application block": "esperado '}' depois do bloco application",
    "Expected '}' after query block": "esperado '}' depois do bloco de consulta",
    "Expected '}' after annotation array": "esperado '}' depois do array da anotação",
    "Expected '}' after if-expression branch": "esperado '}' depois do ramo da expressão if",
    "Expected '}' to close foreign module": "esperado '}' para fechar o módulo estrangeiro",
    "Expected '}'": "esperado '}'",
    "Expected onStart/onShutdown block in application": "esperado bloco onStart/onShutdown em application",
    "Expected tag string after ',' in test declaration": "esperada string de tag depois de ',' na declaração de teste",
    "Expected test name string": "esperada string de nome de teste",
    "Expected annotation value": "esperado valor de anotação",
    "Expected annotation name": "esperado nome de anotação",
    "Expected a value after '{0}'": "esperado um valor depois de '{0}'",
    "Expected a library string literal after 'library'": "esperado um literal de texto de biblioteca depois de 'library'",
    "Expected '}' after entity body": "esperado '}' depois do corpo da entidade",
    "Expected '}' after record body": "esperado '}' depois do corpo do record",
    "Expected '{' after {0}": "esperado '{' depois de {0}",
    "Expected '}' after query block": "esperado '}' depois do bloco de consulta",
    "Expected '{' after entity name": "esperado '{' depois do nome da entidade",
    "Expected entity name": "esperado nome de entidade",
    "Expected enum name": "esperado nome de enum",
    "Expected enum constant": "esperada constante de enum",
    "Expected '}' after if-expression branch": "esperado '}' depois do ramo da expressão if",
    "Expected ':' after field name": "esperado ':' depois do nome do campo",
    "Expected '}'": "esperado '}'",
    "Expected ']'": "esperado ']'",
    "Expected exception name": "esperado nome de exceção",
    "Expected '->' after 'default'": "esperado '->' depois de 'default'",
    "Expected '{' after foreign module name": "esperado '{' depois do nome do módulo estrangeiro",
    "Expected '}' to close foreign module": "esperado '}' para fechar o módulo estrangeiro",
    "Expected foreign module name": "esperado nome de módulo estrangeiro",
    "Expected extern function name": "esperado nome de função extern",
    "Expected '}' after enum body": "esperado '}' depois do corpo do enum",
    "Expected '{' after enum name (enums are constants-only)": "esperado '{' depois do nome do enum (enum é só de constantes)",
    "Expected '}' after application block": "esperado '}' depois do bloco application",
    "Expected '{' after application": "esperado '{' depois de application",
    "Expected '}'": "esperado '}'",
    "Expected '}'": "esperado '}'",

    # ---------------- SEM (representativos) ----------------
    "Undefined variable or type: '{0}'": "variável ou tipo indefinido: '{0}'",
    "undefined variable: '{0}'": "variável indefinida: '{0}'",
    "Undefined variable or type: '{0}' in {1} — declare the type or fix the name (R6: undefined declared types must not compile)":
        "variável ou tipo indefinido: '{0}' em {1} — declare o tipo ou corrija o nome "
        "(R6: tipos declarados indefinidos não podem compilar)",
    "Undefined function: '{0}'": "função indefinida: '{0}'",
    "Undefined superclass or interface: '{0}' in {1} — import the type or fix the name (R6: a raw super fails at load)":
        "superclasse ou interface indefinida: '{0}' em {1} — importe o tipo ou corrija o nome "
        "(R6: um super cru falha no load)",
    "Type mismatch: cannot assign {0} to {1}": "incompatibilidade de tipo: não é possível atribuir {0} a {1}",
    "type mismatch: cannot assign {0} to '{1}: {2}'": "incompatibilidade de tipo: não é possível atribuir {0} a '{1}: {2}'",
    "type mismatch: cannot assign {0} to field '{1}: {2}'": "incompatibilidade de tipo: não é possível atribuir {0} ao campo '{1}: {2}'",
    "Return type mismatch: expected '{0}' but got '{1}'": "incompatibilidade de tipo de retorno: esperado '{0}' mas obtido '{1}'",
    "cannot assign to immutable 'val' variable '{0}'": "não é possível atribuir à variável imutável 'val' '{0}'",
    "cannot assign to '{0}': record is immutable": "não é possível atribuir a '{0}': record é imutável",
    "cannot assign to final field '{0}' (declared in '{1}') from outside its constructor":
        "não é possível atribuir ao campo final '{0}' (declarado em '{1}') de fora do seu construtor",
    "cannot assign to external static field '{0}.{1}' (external class fields are read-only)":
        "não é possível atribuir ao campo estático externo '{0}.{1}' (campos de classe externa são somente leitura)",
    "argument count mismatch for '{0}': expected {1} but got {2}": "número de argumentos incompatível para '{0}': esperado {1} mas obtido {2}",
    "Wrong number of arguments for '{0}': expected {1} but got {2}": "número de argumentos errado para '{0}': esperado {1} mas obtido {2}",
    "Argument {0} of '{1}': expected '{2}' but got '{3}'": "argumento {0} de '{1}': esperado '{2}' mas obtido '{3}'",
    "Argument {0} of '{1}': expected {2} but got {3} (no constructor matches the argument types)":
        "argumento {0} de '{1}': esperado {2} mas obtido {3} (nenhum construtor casa com os tipos dos argumentos)",
    "null cannot be assigned: null safety works by narrowing (if (x != null)), never by direct null literals":
        "null não pode ser atribuído: a segurança contra null funciona por estreitamento (if (x != null)), "
        "nunca por literais null diretos",
    "null cannot be assigned: null safety works by narrowing (if (x != null)), never by direct null literals (variable '{0}')":
        "null não pode ser atribuído: a segurança contra null funciona por estreitamento (if (x != null)), "
        "nunca por literais null diretos (variável '{0}')",
    "null cannot be passed as argument {0} of '{1}()': primitive parameter is non-nullable and null is never fabricable — declare the parameter '{2}?' to accept an absent value, or pass a real value":
        "null não pode ser passado como argumento {0} de '{1}(): parâmetro primitivo não é anulável e null nunca é fabricável — "
        "declare o parâmetro '{2}?' para aceitar um valor ausente, ou passe um valor real",
    "receiver is nullable (T?); narrow first: if (x != null) { x.method() }":
        "o receptor é anulável (T?); estreite antes: if (x != null) { x.method() }",
    "receiver is nullable (T?); narrow first: if (x != null) { x.field }":
        "o receptor é anulável (T?); estreite antes: if (x != null) { x.field }",
    "receiver is nullable (T?); narrow first: if (x != null) { x.field = v }":
        "o receptor é anulável (T?); estreite antes: if (x != null) { x.field = v }",
    "assignment is a statement, not an expression (use '=' on its own line)":
        "atribuição é uma declaração, não uma expressão (use '=' em sua própria linha)",
    "throw requires a String (exceptions are Strings in Kof), got {0}":
        "throw exige um String (exceções são Strings no Kof), obtido {0}",
    "catch type '{0}' is a primitive — primitives are not throwable (Kof exceptions are Strings: use `catch (String e)`)":
        "o tipo do catch '{0}' é um primitivo — primitivos não são lançáveis (exceções do Kof são Strings: use `catch (String e)`)",
    "catch type '{0}' is not a Throwable subclass — Kof exceptions are Strings (use `catch (String e)`) or a JDK/user throwable":
        "o tipo do catch '{0}' não é subclasse de Throwable — exceções do Kof são Strings (use `catch (String e)`) "
        "ou um throwable do JDK/usuário",
    "variable '{0}' is already defined in this scope": "a variável '{0}' já está definida neste escopo",
    "void function cannot return a value - drop the value (bare `return` exits) or declare a return type":
        "função void não pode retornar um valor - descarte o valor (`return` sozinho encerra) ou declare um tipo de retorno",
    "throw clause of {0} references unknown type '{1}'": "a cláusula throw de {0} referencia um tipo desconhecido '{1}'",
    "using requires a closer expression: using (name = init, closer) { body }":
        "using exige uma expressão de fechamento: using (name = init, closer) { corpo }",
    "'Bool' has two values; for true/false/unknown use 'Troolean' (DECISIONS.md D-TROOL)":
        "'Bool' tem dois valores; para true/false/unknown use 'Troolean' (DECISIONS.md D-TROOL)",
    # ---------------- PARSE095 ----------------
    "invalid variable declaration: the ':' annotation is only allowed after 'var'/'val' — the type '{0}' before '{1}' does not match '{2}' and would be silently discarded; write 'var {3}: {4} = ...' or '{5} {6} = ...'":
        "declaração de variável inválida: a anotação ':' só é permitida depois de 'var'/'val' — "
        "o tipo '{0}' antes de '{1}' não casa com '{2}' e seria descartado em silêncio; escreva "
        "'var {3}: {4} = ...' ou '{5} {6} = ...'",
    # ---------------- SEM (restante, fiel) ----------------
    "Cannot apply '{0}' to String and {1}": "não é possível aplicar '{0}' a String e {1}",
    "Cannot apply '{0}' to non-numeric type {1} (declare the parameter type, e.g. (x: Int) -> ...)":
        "não é possível aplicar '{0}' a um tipo não numérico {1} (declare o tipo do parâmetro, "
        "ex.: (x: Int) -> ...)",
    "Cannot apply '{0}' to boolean types. Use == or != for comparison.":
        "não é possível aplicar '{0}' a tipos booleanos. Use == ou != para comparação.",
    "{0} is a top-level function, not a value in argument position — pass the call wrapped in a lambda: () -> {1}()":
        "{0} é uma função de nível de módulo, não um valor em posição de argumento — passe a "
        "chamada envolvida em uma lambda: () -> {1}()",
    "variable '{0}' is not a function and cannot be called{1}": "a variável '{0}' não é uma função e não pode ser chamada{1}",
    "method '{0}' does not exist in superclass '{1}'": "o método '{0}' não existe na superclasse '{1}'",
    "{0}{1}' with {2} argument(s)": "{0}{1}' com {2} argumento(s)",
    "no constructor of '{0}' with {1} argument(s)": "nenhum construtor de '{0}' com {1} argumento(s)",
    "no public constructor of '{0}' with {1} argument(s)": "nenhum construtor público de '{0}' com {1} argumento(s)",
    "no constructor of '{0}' with {1} argument(s) (expected {2})": "nenhum construtor de '{0}' com {1} argumento(s) (esperado {2})",
    "List.zip takes exactly one List argument": "List.zip exige exatamente um argumento List",
    "Set does not support indexing [i]; use contains(x) for membership or keys() to iterate":
        "Set não suporta indexação [i]; use contains(x) para pertinência ou keys() para iterar",
    "Cannot resolve method '{0}' on 'process' (valid: run, spawn, exit)":
        "não é possível resolver o método '{0}' em 'process' (válidos: run, spawn, exit)",
    "Cannot resolve method '{0}' on type 'Channel' (valid: send, receive)":
        "não é possível resolver o método '{0}' no tipo 'Channel' (válidos: send, receive)",
    "Cannot resolve method '{0}' on type 'List' (valid: add/get/set/remove/contains/size/isEmpty/clear/map/filter/reduce/indexOf/lastIndexOf/addAll/subList/take/drop/slice/sort/any/all/none/find/forEach/flatMap/distinct/sorted/groupBy/zip)":
        "não é possível resolver o método '{0}' no tipo 'List' (válidos: add/get/set/remove/contains/"
        "size/isEmpty/clear/map/filter/reduce/indexOf/lastIndexOf/addAll/subList/take/drop/slice/sort/"
        "any/all/none/find/forEach/flatMap/distinct/sorted/groupBy/zip)",
    "Cannot resolve method '{0}' on type 'Map' (valid: put/get/getOrDefault/putIfAbsent/remove/containsKey/contains/containsValue/size/clear/isEmpty/keys/values)":
        "não é possível resolver o método '{0}' no tipo 'Map' (válidos: put/get/getOrDefault/"
        "putIfAbsent/remove/containsKey/contains/containsValue/size/clear/isEmpty/keys/values)",
    "Cannot resolve method '{0}' on type 'Set' (valid: add/contains/remove/size/clear/isEmpty)":
        "não é possível resolver o método '{0}' no tipo 'Set' (válidos: add/contains/remove/size/clear/isEmpty)",
    "Handle<T> has no method '{0}()'; use `await h` to get the value":
        "Handle<T> não tem o método '{0}()'; use `await h` para obter o valor",
    "List.zip takes a List argument; '{0}' is not a List": "List.zip exige um argumento List; '{0}' não é uma List",
    "List.{0} takes exactly one lambda argument": "List.{0} exige exatamente um argumento lambda",
    "array does not have method '{0}'": "o array não tem o método '{0}'",
    "array does not have method '{0}' (use .length for size)": "o array não tem o método '{0}' (use .length para o tamanho)",
    "Cannot resolve field '{0}' on type '{1}'": "não é possível resolver o campo '{0}' no tipo '{1}'",
    "Cannot resolve method '{0}' in superclass '{1}'": "não é possível resolver o método '{0}' na superclasse '{1}'",
    "Cannot resolve method '{0}' on 'shell' (valid: {1})": "não é possível resolver o método '{0}' em 'shell' (válidos: {1})",
    "Cannot resolve method '{0}' on 'ssh' (valid: {1})": "não é possível resolver o método '{0}' em 'ssh' (válidos: {1})",
    "Cannot resolve method '{0}' on namespace 'json' — {1}": "não é possível resolver o método '{0}' no namespace 'json' — {1}",
    "Cannot resolve method '{0}' on namespace '{1}'": "não é possível resolver o método '{0}' no namespace '{1}'",
    "Cannot resolve method '{0}' on type '{1}'": "não é possível resolver o método '{0}' no tipo '{1}'",
    "Cannot resolve static field '{0}' on type '{1}'": "não é possível resolver o campo estático '{0}' no tipo '{1}'",
    "Cannot resolve static method '{0}' on imported class '{1}'": "não é possível resolver o método estático '{0}' na classe importada '{1}'",
    "'{0}' does not have method '{1}' with {2} argument(s)": "'{0}' não tem o método '{1}' com {2} argumento(s)",
    "array has no method '{0}()'{1}": "o array não tem o método '{0}()'{1}",
    "method '{0}' is not supported on collections; use a loop with new T[n] to materialize an array":
        "o método '{0}' não é suportado em coleções; use um laço com new T[n] para materializar um array",
    "enum '{0}' has no constant '{1}'": "o enum '{0}' não tem a constante '{1}'",
    "switch on '{0}' does not cover: {1} (add a default or the missing cases)":
        "o switch sobre '{0}' não cobre: {1} (adicione um default ou os casos ausentes)",
    "assignment to '{0}' received a void value — the call does not return a value":
        "a atribuição a '{0}' recebeu um valor void — a chamada não retorna um valor",
    "{0}(...) received a void value — the call does not return a value (add a 'return' or don't use it as an argument)":
        "{0}(...) recebeu um valor void — a chamada não retorna um valor (adicione um 'return' ou "
        "não a use como argumento)",
    "method '{0}' is not supported on collections (collection return is not materializable); copy the elements with a loop":
        "o método '{0}' não é suportado em coleções (o retorno de coleção não é materializável); copie "
        "os elementos com um laço",
    "case of primitive type is not supported in pattern matching (use a reference type or the value directly)":
        "caso de tipo primitivo não é suportado em pattern matching (use um tipo de referência ou o valor diretamente)",
    "{0} declares return type '{1}' but may finish without return/throw":
        "{0} declara o tipo de retorno '{1}' mas pode terminar sem return/throw",
    "cannot instantiate abstract class '{0}'": "não é possível instanciar a classe abstrata '{0}'",
    "abstract method '{0}' is not allowed in non-abstract class '{1}' (declare the class as 'abstract')":
        "método abstrato '{0}' não é permitido em classe não abstrata '{1}' (declare a classe como 'abstract')",
    "nested type declaration is not supported: declare '{0}' at top level":
        "declaração de tipo aninhado não é suportada: declare '{0}' no nível de módulo",
    "class '{0}' does not implement abstract method '{1}()' from superclass '{2}'":
        "a classe '{0}' não implementa o método abstrato '{1}()' da superclasse '{2}'",
    "class '{0}' does not implement method '{1}' of interface '{2}'{3}":
        "a classe '{0}' não implementa o método '{1}' da interface '{2}'{3}",
    "method '{0}' of interface '{1}' expects {2} parameter(s) but implementation has {3}":
        "o método '{0}' da interface '{1}' espera {2} parâmetro(s) mas a implementação tem {3}",
    "main() must be declared without modifiers: 'main() { ... }' (found {0})":
        "main() deve ser declarado sem modificadores: 'main() { ... }' (encontrado {0})",
    "main() must have no return type: 'main() { ... }' (found '{0} main(...)')":
        "main() não deve ter tipo de retorno: 'main() { ... }' (encontrado '{0} main(...)')",
    "{0} is private (declared in '{1}') and cannot be accessed from '{2}'":
        "{0} é privado (declarado em '{1}') e não pode ser acessado de '{2}'",
    "{0} is protected (declared in '{1}') and cannot be accessed from '{2}'":
        "{0} é protegido (declarado em '{1}') e não pode ser acessado de '{2}'",
    "{0} is {1} (declared in '{2}') and cannot be accessed from top-level code":
        "{0} é {1} (declarado em '{2}') e não pode ser acessado de código de nível de módulo",
    "function '{0}' with parameters ({1}) is already defined at line {2}; duplicate signatures are not allowed — overload requires a DIFFERENT parameter list":
        "a função '{0}' com parâmetros ({1}) já está definida na linha {2}; assinaturas duplicadas "
        "não são permitidas — sobrecarga exige uma LISTA de parâmetros DIFERENTE",
    "'{0}' is a primitive type, it has no static field '{1}' (use the literal, e.g. 2147483647 for Int; there is no Int.MAX_VALUE in Kof)":
        "'{0}' é um tipo primitivo, não tem campo estático '{1}' (use o literal, ex. 2147483647 para Int; "
        "não existe Int.MAX_VALUE no Kof)",
    "String.{0} does not accept {1} as argument {2} (the parameter is String); use a String literal, e.g.: {3}(\"c\")":
        "String.{0} não aceita {1} como argumento {2} (o parâmetro é String); use um literal String, "
        "ex.: {3}(\"c\")",
    "Kof has no \"{0}\" method on String; use the stdlib function: strings.{1}(...":
        "Kof não tem o método \"{0}\" em String; use a função da stdlib: strings.{1}(...",
    "Kof has no \"{0}\" method on String; use the stdlib function: strings.{1}(...)":
        "Kof não tem o método \"{0}\" em String; use a função da stdlib: strings.{1}(...)",
    "Kof has no \"{0}\" method on {1}; use the stdlib function on an Int/Long, e.g.: n.toLong().{2}()":
        "Kof não tem o método \"{0}\" em {1}; use a função da stdlib sobre um Int/Long, ex.: n.toLong().{2}()",
    "Kof has no operator '{0}' for String (lexicographic order is Unspecified — it diverges per target); use: s.compareTo(t) {1}":
        "Kof não tem o operador '{0}' para String (a ordem lexicográfica é Não Especificada — diverge por "
        "alvo); use: s.compareTo(t) {1}",
    "`[]` assignment only works on arrays in Kof; for a List use l.set(i, v)":
        "a atribuição `[]` só funciona em arrays no Kof; para uma List use l.set(i, v)",
    "`[]` only indexes arrays in Kof; for this collection use {0}":
        "`[]` só indexa arrays no Kof; para esta coleção use {0}",
    "List.subList takes Int INDEX bounds; {0} is not an index":
        "List.subList exige limites de ÍNDICE Int; {0} não é um índice",
    "List.{0} takes an Int INDEX; {1} is not an index (to search by value use contains)":
        "List.{0} exige um ÍNDICE Int; {1} não é um índice (para buscar por valor use contains)",
    "Set.add: element {0} does not match the set element type ({1}) — Kof collections are homogeneous":
        "Set.add: o elemento {0} não casa com o tipo de elemento do set ({1}) — coleções do Kof são homogêneas",
    "listOf: element {0} does not match the list element type ({1}) — Kof collections are homogeneous":
        "listOf: o elemento {0} não casa com o tipo de elemento da lista ({1}) — coleções do Kof são homogêneas",
    "mapOf: value {0} does not match the map type ({1}) — Kof collections are homogeneous":
        "mapOf: o valor {0} não casa com o tipo do map ({1}) — coleções do Kof são homogêneas",
    "List.{0}: element {1} does not match the list element type ({2}) — Kof collections are homogeneous":
        "List.{0}: o elemento {1} não casa com o tipo de elemento da lista ({2}) — coleções do Kof são homogêneas",
    "Map.{0}: {1} {2} does not match the map type ({3}) — Kof collections are homogeneous":
        "Map.{0}: {1} {2} não casa com o tipo do map ({3}) — coleções do Kof são homogêneas",
    "call to '{0}' is ambiguous between {1} overloads — add a cast to pick one":
        "a chamada a '{0}' é ambígua entre {1} sobrecargas — adicione um cast para escolher uma",
    "`for-in` only iterates over `List<T>` or arrays in Kof; for String use `s.charAt(i)` in a numeric loop":
        "`for-in` só itera sobre `List<T>` ou arrays no Kof; para String use `s.charAt(i)` em um laço numérico",
    "method '{0}' in class '{1}' overrides '{2}' but return type {3} is not compatible with the overridden return type {4}":
        "o método '{0}' na classe '{1}' sobrepõe '{2}' mas o tipo de retorno {3} não é compatível com o "
        "tipo de retorno sobreposto {4}",
    "cannot call instance method '{0}.{1}()' without a receiver — use 'this.{2}()' inside an instance method of '{3}' or call it on an instance (method() is not static)":
        "não é possível chamar o método de instância '{0}.{1}()' sem um receptor — use 'this.{2}()' dentro "
        "de um método de instância de '{3}' ou o chame sobre uma instância (method() não é estático)",
    "'{0}' is already defined in {1} '{2}' at line {3} — same JVM descriptor; overload requires a DIFFERENT parameter or return type (static and instance do not differ here)":
        "'{0}' já está definido em {1} '{2}' na linha {3} — mesmo descritor JVM; sobrecarga exige um tipo de "
        "parâmetro ou retorno DIFERENTE (estático e instância não diferem aqui)",
    "Cannot compare an enum value to a String: an enum constant is not a String (D-ENUM207). Compare two enum values, or use .name() explicitly to get the name":
        "não é possível comparar um valor de enum a uma String: uma constante de enum não é uma String "
        "(D-ENUM207). Compare dois valores de enum, ou use .name() explicitamente para obter o nome",
    "String has no \"{0}\" method — looks like a collection accessor; the raw row from db.query is a JSON String: use db.query<Record> (typed) or json.decode<Map<String, Object>>(row)":
        "String não tem o método \"{0}\" — parece um acessor de coleção; a linha crua de db.query é uma "
        "String JSON: use db.query<Record> (tipado) ou json.decode<Map<String, Object>>(row)",
    "List.{0} appends exactly one element; there is no positional insert — use set(index, value) to replace at an index":
        "List.{0} acrescenta exatamente um elemento; não há inserção posicional — use set(index, value) "
        "para substituir em um índice",
    "List.reduce takes exactly two arguments: the lambda AND the seed — reduce((a: Int, b: Int) -> a + b, 0) or reduce(0, (a: Int, b: Int) -> a + b)":
        "List.reduce exige exatamente dois argumentos: a lambda E a semente — reduce((a: Int, b: Int) -> a + b, 0) "
        "ou reduce(0, (a: Int, b: Int) -> a + b)",
    "'{0}' has no static method '{1}()'": "'{0}' não tem o método estático '{1}()'",
    "'{0}' is a primitive — it has no method '{1}()' (primitives have toString() and the conversions toInt()/toLong()/toFloat()/toDouble(); comparison is `a == b`, math is top-level functions, e.g. math.abs(x))":
        "'{0}' é um primitivo — não tem o método '{1}()' (primitivos têm toString() e as conversões "
        "toInt()/toLong()/toFloat()/toDouble(); a comparação é `a == b`, a matemática são funções de nível "
        "de módulo, ex. math.abs(x))",
    "static method cannot reference instance field '{0}' (no implicit 'this' in a static context; use an instance, or declare the field 'static')":
        "método estático não pode referenciar o campo de instância '{0}' (não há 'this' implícito em contexto "
        "estático; use uma instância, ou declare o campo como 'static')",
    "'{0}' is already defined in class '{1}' at line {2} — a class cannot declare two fields with the same name (static or not, any type); rename one":
        "'{0}' já está definido na classe '{1}' na linha {2} — uma classe não pode declarar dois campos com o "
        "mesmo nome (estático ou não, qualquer tipo); renomeie um",
    "style: the declaration string must be a literal — the 4-Int Style(background, foreground, padding, radius) form takes a computed Color":
        "style: a string de declaração deve ser um literal — a forma Style(background, foreground, padding, radius) "
        "de 4 Ints aceita um Color computado",
    "'Palette.{0}' is not a palette color (use a named color, e.g. Palette.red)": "'Palette.{0}' não é uma cor da paleta (use uma cor nomeada, ex. Palette.red)",
    "'{0}' has no field '{1}' (UI types expose functions, e.g. Color.rgba(...), Theme.light())":
        "'{0}' não tem o campo '{1}' (tipos de UI expõem funções, ex. Color.rgba(...), Theme.light())",
    "token '{0}' has no methods — it holds constants: {1}": "o token '{0}' não tem métodos — ele guarda constantes: {1}",
    "subtype '{0}' of sealed type '{1}' must be declared in the same compilation unit as '{2}' — a sealed type's subtype set is closed, and subtypes declared elsewhere are not known to the compiler":
        "o subtipo '{0}' do tipo selado '{1}' deve ser declarado na mesma unidade de compilação que '{2}' — o "
        "conjunto de subtipos de um tipo selado é fechado, e subtipos declarados em outro lugar não são conhecidos "
        "pelo compilador",
    "function type with a type-parameter of the owner in '{0}' in {1} — generic function types are not lowered yet (erasure ABI is 1.0-line); the form is rejected instead of compiling to a load crash (SEM085)":
        "tipo de função com um parâmetro de tipo do dono em '{0}' em {1} — tipos de função genéricos ainda não são "
        "rebaixados (a ABI de erasure é da linha 1.0); a forma é recusada em vez de compilar para uma falha de load (SEM085)",
    "'{0}()' may truncate (fractional part discarded; overflow throws) — explicit form: value as {1}":
        "'{0}()' pode truncar (a parte fracionária é descartada; estouro lança exceção) — forma explícita: value as {1}",
    "modificador '{0}' has no effect in Kof (a non-goal of the memory model — concurrency-memory-model.md §5); use a language abstraction: spawn/await/Channel for concurrency{1}":
        "o modificador '{0}' não tem efeito no Kof (não-objetivo do modelo de memória — concurrency-memory-model.md §5); "
        "use uma abstração da linguagem: spawn/await/Channel para concorrência{1}",
    "self-referencing initializer var inside a lambda (var '{0}') is not available on the {1} target yet — captured handle read in the job crashes the worker (§253 face B, native lane); use the shadow-handle idiom (var id=\"\"; job reads id after assignment; id = time.interval(…) after) (SEM092)":
        "a variável de inicialização autorreferente dentro de uma lambda (var '{0}') ainda não está disponível no "
        "alvo {1} — a leitura do handle capturado no job derruba o worker (§253 face B, lane nativa); use o idioma "
        "de handle-sombra (var id=\"\"; o job lê id depois da atribuição; id = time.interval(…) depois) (SEM092)",
    "Buffer element must be U8 (Byte) — got '{0}'": "o elemento de Buffer deve ser U8 (Byte) — obtido '{0}'",
    "{0}() needs an argument — println and print take the value to print (println(x)); for a blank line use println(\"\")":
        "{0}() precisa de um argumento — println e print recebem o valor a imprimir (println(x)); para uma linha "
        "em branco use println(\"\")",
    "cannot store a primitive array (Int[]) into '{0}' of erased reference-array type T[] (erases to Object[] on the JVM — int[] is not a subtype of Object[]). Use List<Int> (the Kof idiom for a growable sequence of primitives) or a reference-typed array slot":
        "não é possível armazenar um array de primitivos (Int[]) em '{0}' de tipo de array de referência apagado "
        "T[] (apaga para Object[] na JVM — int[] não é subtipo de Object[]). Use List<Int> (o idioma do Kof para "
        "uma sequência crescente de primitivos) ou um slot de array de tipo de referência",
    "cannot pass a List to '{0}': the kof.io bytes faces take an Int[] primitive array (training/language/io.md contract) — a List is not an array on any target (VerifyError on the JVM, crash under the interpreter, silent mismatch in KofJS). Fill a primitive array: val a = new Int[n] with a[i] = v, then f.{1}(a)":
        "não é possível passar uma List para '{0}': as faces de bytes do kof.io recebem um array de primitivos "
        "Int[] (contrato training/language/io.md) — uma List não é um array em nenhum alvo (VerifyError na JVM, "
        "falha sob o interpretador, incompatibilidade silenciosa no KofJS). Preencha um array de primitivos: "
        "val a = new Int[n] com a[i] = v, depois f.{1}(a)",
    "an 'as' cast is not a parse: '{0}' does not cast to '{1}' (an implicit String conversion would be a hidden parse) — use the stdlib parsers (math.parseInt / math.parseFloat / math.parseDouble / math.parseChar)":
        "um cast 'as' não é uma conversão: '{0}' não faz cast para '{1}' (uma conversão implícita para String "
        "seria uma análise oculta) — use os analisadores da stdlib (math.parseInt / math.parseFloat / "
        "math.parseDouble / math.parseChar)",
    "class '{0}' inherits conflicting default method '{1}' from unrelated interfaces '{2}' and '{3}' (add an explicit override in '{4}')":
        "a classe '{0}' herda um método default conflitante '{1}' de interfaces não aparentadas '{2}' e '{3}' "
        "(adicione uma sobreposição explícita em '{4}')",
    "'{0}' has no field '{1}' (this builtin exposes methods, not properties)": "'{0}' não tem o campo '{1}' (este builtin expõe métodos, não propriedades)",
    "'{0}' has no field '{1}'{2}": "'{0}' não tem o campo '{1}'{2}",
    "'{0}' has no method '{1}()'": "'{0}' não tem o método '{1}()'",
    "'{0}' has no method '{1}()'{2}": "'{0}' não tem o método '{1}()'{2}",
    "'{0}' is a builtin namespace; it has no field '{1}' (namespaces expose functions, not properties — use {2}.someFunction(...))":
        "'{0}' é um namespace builtin; não tem o campo '{1}' (namespaces expõem funções, não propriedades — "
        "use {2}.algumaFuncao(...))",
    "case of primitive type is not supported in pattern matching (use a reference type or the value directly)":
        "caso de tipo primitivo não é suportado em pattern matching (use um tipo de referência ou o valor diretamente)",
    "no constructor of '{0}' with {1} argument(s)": "nenhum construtor de '{0}' com {1} argumento(s)",
    # ---------------- SEM032/064/069/070/071/076/077/078/079/081/082/083/097 ----------------
    "switch expression on Boolean does not cover all values (true and false)":
        "a expressão switch sobre Boolean não cobre todos os valores (true e false)",
    "switch expression requires 'default' (or enum exhaustiveness)":
        "a expressão switch exige 'default' (ou exaustividade de enum)",
    "switch on Boolean does not cover all values (true and false)":
        "o switch sobre Boolean não cobre todos os valores (true e false)",
    "switch expression on '{0}' does not cover: {1} (add a default or the missing cases)":
        "a expressão switch sobre '{0}' não cobre: {1} (adicione um default ou os casos ausentes)",
    "interface '{0}' cannot extend class '{1}' (interfaces may only extend interfaces)":
        "a interface '{0}' não pode estender a classe '{1}' (interfaces só podem estender interfaces)",
    "class '{0}' cannot be both 'final' and 'abstract' — 'final' forbids subclasses, 'abstract' requires them":
        "a classe '{0}' não pode ser 'final' e 'abstract' ao mesmo tempo — 'final' proíbe subclasses, "
        "'abstract' as exige",
    "cannot extend record '{0}' — records are implicitly final; compose it (hold it in a field) or use a plain class":
        "não é possível estender o record '{0}' — records são implicitamente final; componha-o (guarde-o "
        "em um campo) ou use uma classe comum",
    "cannot inherit from final class '{0}' (declared 'final' — remove 'final' or the inheritance)":
        "não é possível herdar da classe final '{0}' (declarada 'final' — remova o 'final' ou a herança)",
    "cannot instantiate interface '{0}' — declare a class that implements it":
        "não é possível instanciar a interface '{0}' — declare uma classe que a implemente",
    "style: unknown property '{0}' — not in the kof.ui style whitelist":
        "style: propriedade desconhecida '{0}' — não está na whitelist de style do kof.ui",
    "style: the declaration string must be a literal":
        "style: a string de declaração deve ser um literal",
    "style: malformed declaration '{0}' — expected 'property: value'":
        "style: declaração malformada '{0}' — esperado 'propriedade: valor'",
    "style: property '{0}' has an empty value": "style: a propriedade '{0}' tem um valor vazio",
    "style: invalid value for '{0}': {1}": "style: valor inválido para '{0}': {1}",
    "unknown token '{0}.{1}' — {2} has: {3}": "token desconhecido '{0}.{1}' — {2} tem: {3}",
    "switch expression on sealed type '{0}' does not cover: {1} (add a default or the missing cases)":
        "a expressão switch sobre o tipo selado '{0}' não cobre: {1} (adicione um default ou os casos ausentes)",
    "switch on sealed type '{0}' does not cover: {1} (add a default or the missing cases)":
        "o switch sobre o tipo selado '{0}' não cobre: {1} (adicione um default ou os casos ausentes)",
    "type parameter '{0}' is declared 'in' but occurs in an output position ({1}) — an 'in' parameter is contravariant, so it may appear only in input positions (parameters); a readable use would let a contravariant alias expose a value of the wrong type":
        "o parâmetro de tipo '{0}' é declarado 'in' mas ocorre em uma posição de saída ({1}) — um parâmetro "
        "'in' é contravariante, então só pode aparecer em posições de entrada (parâmetros); um uso de leitura "
        "permitiria que um alias contravariante expusesse um valor de tipo errado",
    "type parameter '{0}' is declared 'out' but occurs in an input position ({1}) — an 'out' parameter is covariant, so it may appear only in output positions (return types, read-only components); a writable use would let a covariant alias store a value of the wrong type":
        "o parâmetro de tipo '{0}' é declarado 'out' mas ocorre em uma posição de entrada ({1}) — um parâmetro "
        "'out' é covariante, então só pode aparecer em posições de saída (tipos de retorno, componentes "
        "somente-leitura); um uso gravável permitiria que um alias covariante armazenasse um valor de tipo errado",
    "type parameter '{0}' is declared 'in' but is passed as {1} to supertype '{2}' — a contravariant parameter cannot appear in a supertype position that exposes it (invariant/covariant); the supertype would let a contravariant alias expose a value of the wrong type":
        "o parâmetro de tipo '{0}' é declarado 'in' mas é passado como {1} ao supertipo '{2}' — um parâmetro "
        "contravariante não pode aparecer em uma posição de supertipo que o exponha (invariante/covariante); o "
        "supertipo permitiria que um alias contravariante expusesse um valor de tipo errado",
    "type parameter '{0}' is declared 'out' but is passed as {1} to supertype '{2}' — a covariant parameter cannot appear in a supertype position that consumes it (invariant/contravariant); the supertype would let a covariant alias store a value of the wrong type":
        "o parâmetro de tipo '{0}' é declarado 'out' mas é passado como {1} ao supertipo '{2}' — um parâmetro "
        "covariante não pode aparecer em uma posição de supertipo que o consuma (invariante/contravariante); o "
        "supertipo permitiria que um alias covariante armazenasse um valor de tipo errado",
    "List.sort/sorted needs elements with a natural order (Int/Long/Double/Float/Bool/Char/String); '{0}' has none — use sorted((a, b) -> Int) with an explicit comparator instead":
        "List.sort/sorted exige elementos com ordem natural (Int/Long/Double/Float/Bool/Char/String); '{0}' "
        "não tem — use sorted((a, b) -> Int) com um comparador explícito",
}


def main():
    data = json.loads(JSON.read_text())
    filled = 0
    unfilled = []
    for code, rows in data.items():
        for r in rows:
            en = r["en"]
            if en in OVERRIDES:
                r["pt"] = OVERRIDES[en]
                filled += 1
            elif not r.get("pt"):
                r["pt"] = ""
                unfilled.append((code, en))
    JSON.write_text(json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True) + "\n")
    print(f"preenchidas: {filled} / variantes: {sum(len(v) for v in data.values())}")
    if unfilled:
        print(f"AINDA SEM PT: {len(unfilled)}")
        for c, e in unfilled:
            print(f"  {c}\t{e[:90]}")


if __name__ == "__main__":
    main()
