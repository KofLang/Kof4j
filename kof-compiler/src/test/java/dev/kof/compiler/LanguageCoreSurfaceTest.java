package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * 8.6 (IMPLEMENTATION-UNIVERSAL-PLATFORM) — teste final do plano: "o nucleo
 * da linguagem mal cresceu". A plataforma (stdlib, backends, tooling) cresce
 * por design; o NUCLEO (palavras reservadas, operadores, comparacoes, tokens,
 * modelo de tipos) e CONGELADO (freeze regra 6). Este teste trava a
 * superficie exata medida no tip 19/09: qualquer adicao/remocao aqui e
 * DECISAO DA MANTENEDORA (rule 6 — bump + docs + migracao no mesmo commit),
 * nunca efeito colateral de feature de plataforma. Se ele quebrar, ou a
 * mudanca era de linguagem e pedia decisao, ou e um vazamento da plataforma
 * para dentro do nucleo — e nas duas leituras a entrega estava errada.
 *
 * Golden MEDIDO (jshell sobre kof-compiler/target/classes do tip, nunca de
 * memoria — Q3): 64 reservados, 17 binarios, 17 unarios, 6 comparacoes,
 * 119 tokens, 8 variantes seladas de Type.
 */
class LanguageCoreSurfaceTest {

    private static List<String> enumNames(Class<? extends Enum<?>> e) {
        return Arrays.stream(e.getEnumConstants()).map(Enum::name).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private static Set<String> lexerKeywords() throws Exception {
        Class<?> lexer = Class.forName("dev.kof.compiler.parser.Lexer");
        Field f = lexer.getDeclaredField("KEYWORDS");
        f.setAccessible(true);
        return new TreeSet<>(((Map<String, ?>) f.get(null)).keySet());
    }

    @Test
    void reservedKeywordsAreExactlyTheFrozenSet() throws Exception {
        // #1118/#1068 (07/11): unique, generated and audited are contextual,
        // like sealed/value, so they left the reserved table (maintainer decision G375).
        Set<String> golden = new TreeSet<>(List.of(
            "abstract", "as", "assert", "await", "bool", "break", "byte", "case", "catch", "char", "class",
            "continue", "default", "do", "double", "else", "entity", "enum", "extends", "extern", "false",
            "final", "finally", "float", "fn", "for", "fun", "func", "if", "implements",
            "import", "instanceof", "int", "interface", "long", "native", "new", "null", "override",
            "package", "private", "protected", "public", "record", "return", "short", "spawn", "static",
            "string", "super", "switch", "synchronized", "this", "throw", "transient", "true", "try",
            "val", "var", "void", "volatile", "while"));
        Set<String> actual = lexerKeywords();
        Set<String> extra = new TreeSet<>(actual);
        extra.removeAll(golden);
        Set<String> missing = new TreeSet<>(golden);
        missing.removeAll(actual);
        assertTrue(extra.isEmpty(), "novas palavras reservadas (grammar = rule 6, bump+doc+migracao): " + extra);
        assertTrue(missing.isEmpty(), "palavra reservada sumiu da tabela (des-reservar = rule 6): " + missing);
    }

    @Test
    void binaryOperatorsAreFrozen() {
        assertEquals(List.of(
            "ADD", "SUB", "MUL", "DIV", "MOD", "EQ", "NE", "LT", "LE", "GT", "GE", "AND", "OR", "XOR",
            "SHL", "SHR", "USHR"), enumNames(KofBinaryOp.class),
                "KofBinaryOp mudou: operadores sao congelados (freeze regra 6); nova aritmetica/logica e decisao da mantenedora");
    }

    @Test
    void unaryOperatorsAreFrozen() {
        assertEquals(List.of(
            "NEG", "NOT", "I2L", "I2F", "I2D", "I2C", "I2B", "I2S", "L2I", "L2F", "L2D", "F2D", "D2F",
            "D2I", "F2I", "D2L", "F2L"), enumNames(KofUnaryOp.class),
                "KofUnaryOp mudou (ordem incluida): as conversions narrowing #471 estao no pin; mexer = rule 6");
    }

    @Test
    void comparisonsAreFrozen() {
        assertEquals(List.of(
            "EQ", "NE", "LT", "LE", "GT", "GE"), enumNames(KofComparison.class),
                "KofComparison mudou: comparacoes de salto (==/!=/relacionais) = semantics congelada");
    }

    @Test
    void tokenTypesAreFrozen() {
        assertEquals(List.of(
            "EOF", "ERROR", "IDENTIFIER", "INT_LITERAL", "LONG_LITERAL", "FLOAT_LITERAL", "DOUBLE_LITERAL",
            "STRING_LITERAL", "CHAR_LITERAL", "BOOLEAN_LITERAL", "NULL_LITERAL", "CLASS", "INTERFACE",
            "RECORD", "ENUM", "ENTITY", "EXTERN", "GENERATED", "AUDITED", "UNIQUE", "EXTENDS", "IMPLEMENTS", "FUN",
            "FN", "FUNC", "PACKAGE", "IMPORT", "PUBLIC", "PRIVATE", "PROTECTED", "STATIC", "FINAL",
            "ABSTRACT", "TRANSIENT", "VOLATILE", "SYNCHRONIZED", "NATIVE", "DEFAULT", "OVERRIDE", "VOID",
            "NEW", "THIS", "SUPER", "RETURN", "THROW", "IF", "ELSE", "FOR", "WHILE", "DO", "SWITCH",
            "CASE", "BREAK", "CONTINUE", "TRY", "CATCH", "FINALLY", "SPAWN", "AWAIT", "ASSERT",
            "INSTANCEOF", "VAR", "VAL", "AS", "BOOL_TYPE", "BYTE_TYPE", "SHORT_TYPE", "INT_TYPE",
            "LONG_TYPE", "FLOAT_TYPE", "DOUBLE_TYPE", "CHAR_TYPE", "STRING_TYPE", "PLUS", "MINUS", "STAR",
            "SLASH", "PERCENT", "BANG", "EQUAL", "EQUAL_EQUAL", "BANG_EQUAL", "LESS", "LESS_EQUAL",
            "GREATER", "GREATER_EQUAL", "AMP_AMP", "PIPE_PIPE", "AMP", "PIPE", "CARET", "LESS_LESS",
            "GREATER_GREATER", "GREATER_GREATER_GREATER", "PLUS_PLUS", "MINUS_MINUS", "PLUS_EQUAL",
            "MINUS_EQUAL", "STAR_EQUAL", "SLASH_EQUAL", "PERCENT_EQUAL", "AMP_EQUAL", "PIPE_EQUAL",
            "CARET_EQUAL", "LESS_LESS_EQUAL", "GREATER_GREATER_EQUAL", "GREATER_GREATER_GREATER_EQUAL",
            "ARROW", "LPAREN", "RPAREN", "LBRACE", "RBRACE", "LBRACKET", "RBRACKET", "SEMICOLON", "COMMA",
            "DOT", "COLON", "QUESTION", "AT"), enumNames(TokenType.class),
                "TokenType mudou (ordem incluida): token novo = sintaxe nova = rule 6");
    }

    @Test
    void typeModelIsSealedWithExactlyTheFrozenVariants() throws Exception {
        assertTrue(Type.class.isSealed(), "Type parou de ser sealed — o modelo de tipos e congelado (rule 6)");
        List<String> golden = List.of(
            "ArrayType", "ClassType", "FunctionType", "NullableType", "PrimitiveType", "TypeVariable",
            "UnknownType", "WildcardType");
        for (String name : golden) {
            Class<?> c = Class.forName("dev.kof.compiler.Type$" + name);
            assertTrue(Type.class.isAssignableFrom(c), name + " nao implementa mais Type");
        }
        Class<?>[] permitted = Type.class.getPermittedSubclasses();
        if (permitted != null) {
            List<String> actual = Arrays.stream(permitted)
                    .map(c -> c.getSimpleName()).sorted().collect(Collectors.toList());
            assertEquals(golden, actual,
                    "permits de Type divergiu das 8 variantes do modelo congelado");
        }
    }
}
