package dev.kof.compiler.parser;
import dev.kof.compiler.DiagnosticCollector;
import dev.kof.compiler.SourcePosition;
import dev.kof.compiler.Token;
import dev.kof.compiler.TokenType;
import dev.kof.compiler.lang.LanguageProfile;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Contexto de parsing — estado MUTUÁVEL compartilhado do parser
 * recursivo-descendente (REFACTOR-500, FASE 7). O cursor {@code pos} é o
 * único estado mutável: todo parser extraído recebe este contexto por
 * parâmetro e o avança; nenhum estado é duplicado entre as classes.
 *
 * <p>{@code profile} (D-PORTUKOF 07/10): vocabulário da superfície. É dado
 * SOMENTE às POSIÇÕES de palavras contextuais (IDENTIFIERs que o parser
 * resolve por valor — {@code test}/{@code using}/{@code constructor}/
 * {@code in}/...). A semântica é a mesma: o parser pergunta "esta palavra
 * ocupa a posição canônica X?" — nunca um {@code if (portugues)} espalhado.
 */
public class ParseContext {

    final List<Token> tokens;
    final DiagnosticCollector diagnostics;
    final String file;
    final LanguageProfile profile;
    final Set<String> entityNames = new HashSet<>();
    /** #1095: expressões que vieram de um grupo `( … )` — o AST não tem nó de
     *  parênteses, então a identidade do nó marca "o autor já agrupou". */
    final Set<Object> parenthesized = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    String currentClassName;
    int pos;

    ParseContext(List<Token> tokens, DiagnosticCollector diagnostics, String file) {
        this(tokens, diagnostics, file, LanguageProfile.KOF);
    }

    ParseContext(List<Token> tokens, DiagnosticCollector diagnostics, String file,
                 LanguageProfile profile) {
        this.tokens = tokens;
        this.diagnostics = diagnostics;
        this.file = file;
        this.profile = profile;
    }

    /** A palavra atual (IDENTIFIER) ocupa a posição canônica {@code canonical}? */
    boolean wordIs(String canonical) {
        return check(TokenType.IDENTIFIER) && profile.matchesWord(peek().value(), canonical);
    }

    /** A palavra em {@code pos + off} ocupa a posição canônica {@code canonical}? */
    boolean wordAtIs(int off, String canonical) {
        int i = pos + off;
        if (i < 0 || i >= tokens.size()) return false;
        Token t = tokens.get(i);
        return (t.type() == TokenType.IDENTIFIER || t.type() == TokenType.CLASS)
                && profile.matchesWord(t.value(), canonical);
    }

    /** {@code in}/{@code out} de VARIÂNCIA canônicos; qualquer outra palavra, crua. */
    String canonicalVariance(String word) {
        if (profile.matchesWord(word, "in")) return "in";
        if (profile.matchesWord(word, "out")) return "out";
        return word;
    }

    /** Valor canônico de uma palavra contextual da superfície (PortuKof → inglês). */
    String canonicalValue(String word) {
        return profile.canonicalWord(word);
    }

    public boolean check(TokenType... types) {
        if (atEnd()) return false;
        TokenType cur = peek().type();
        for (TokenType t : types) {
            if (cur == t) return true;
        }
        return false;
    }

    public boolean checkNext(TokenType type) {
        int next = pos + 1;
        return next < tokens.size() && tokens.get(next).type() == type;
    }

    /** Modificadores de declaração (mesma lista de {@code parseModifiers}). */
    private static final Set<TokenType> MODIFIER_TOKENS = Set.of(
            TokenType.PUBLIC, TokenType.PRIVATE, TokenType.PROTECTED, TokenType.STATIC,
            TokenType.FINAL, TokenType.ABSTRACT, TokenType.TRANSIENT, TokenType.VOLATILE,
            TokenType.SYNCHRONIZED, TokenType.NATIVE, TokenType.DEFAULT, TokenType.OVERRIDE);

    /**
     * X5.1 (`D-X5-SURFACE`) — `sealed` é keyword CONTEXTUAL: só é modificador
     * quando precede (direta ou após outros modificadores) um declaration
     * keyword de tipo (`class`/`record`/`interface`). Assim o identificador
     * `sealed` continua válido em qualquer outro lugar (regra 2, retrocompat).
     */
    public boolean sealedModifierAhead() {
        if (!wordIs("sealed")) return false;
        int i = pos + 1;
        while (i < tokens.size() && MODIFIER_TOKENS.contains(tokens.get(i).type())) i++;
        if (i >= tokens.size()) return false;
        TokenType t = tokens.get(i).type();
        return t == TokenType.CLASS || t == TokenType.RECORD || t == TokenType.INTERFACE;
    }

    /**
     * TIER 2.7 (`D-VALUE-RECORD`, 2.7.1) — {@code value} é keyword CONTEXTUAL
     * (IDENTIFIER no lexer), como `sealed`: só é modificador quando precede
     * (direta ou após outros modificadores) um {@code record}. Em qualquer
     * outro lugar `value` segue um identificador comum — o campo `value` das
     * caixas de captura e o `value` de `kof.ui` continuam válidos (R2
     * aditivo/retro-compatível). `D-VALUE-RECORDS-GO` resolveu: `value` aplica
     * SÓ a `record` (nunca `class`).
     */
    /**
     * #1068/#1138 — {@code audited} é keyword CONTEXTUAL (IDENTIFIER no
     * lexer): só é modificador de declaração quando precede um
     * {@code entity}. Sem este despacho o IDENTIFIER {@code audited} caía no
     * ramo de FUNÇÃO do topo (parseFunctionDeclaration o engolia como tipo de
     * retorno e o expectId morria na coluna de {@code entity} — PARSE010).
     * Espelha {@code sealedModifierAhead}/{@code valueModifierAhead}.
     */
    public boolean auditedModifierAhead() {
        if (!wordIs("audited")) return false;
        int i = pos + 1;
        while (i < tokens.size() && MODIFIER_TOKENS.contains(tokens.get(i).type())) i++;
        if (i >= tokens.size()) return false;
        return tokens.get(i).type() == TokenType.ENTITY;
    }

    public boolean valueModifierAhead() {
        if (!wordIs("value")) return false;
        int i = pos + 1;
        while (i < tokens.size() && MODIFIER_TOKENS.contains(tokens.get(i).type())) i++;
        if (i >= tokens.size()) return false;
        return tokens.get(i).type() == TokenType.RECORD;
    }

    /**
     * Connector ecosystem (plan §9.16 slice A): {@code foreign module <name> {}
     * — `foreign`/`module` são keywords CONTEXTUAIS (IDENTIFIER), como `sealed`.
     * Só é um bloco de módulo quando IDENTIFIER("foreign") precede IDENTIFIER e
     * um `{`; em qualquer outro lugar `foreign`/`module` seguem identificadores.
     */
    public boolean foreignModuleAhead() {
        if (!wordIs("foreign")) return false;
        if (pos + 3 >= tokens.size()) return false;
        return tokens.get(pos + 1).type() == TokenType.IDENTIFIER
                && tokens.get(pos + 2).type() == TokenType.IDENTIFIER
                && tokens.get(pos + 3).type() == TokenType.LBRACE;
    }

    /**
     * #981 — um modificador puro (`public`/`private`/`protected`/`static`/...)
     * no nível de TOPO, quando o que vem depois de TODOS eles não é uma keyword
     * de declaração de tipo (`class`/`interface`/`record`/`enum`/`entity`): é
     * uma FUNÇÃO de módulo, não um tipo. {@code ClassMemberParser.parseClassMember}
     * já consome modificadores ANTES de decidir o que segue (mesmo padrão de
     * {@link #sealedModifierAhead()}); o dispatcher de topo ({@code Parser.parse()})
     * decidia pelo token ANTES do modificador, então `private Int f(...)` e
     * `private f(...): Int` caíam sempre no ramo de tipo
     * ({@code TypeDeclarations.parseTypeDeclaration}, que exige um desses
     * keywords) e morriam com `PARSE007` (em cascata, na forma sufixada).
     */
    public boolean modifierPrefixedFunctionAhead() {
        if (!MODIFIER_TOKENS.contains(peek().type())) return false;
        int i = pos;
        while (i < tokens.size() && MODIFIER_TOKENS.contains(tokens.get(i).type())) i++;
        if (i >= tokens.size()) return false;
        TokenType t = tokens.get(i).type();
        if (t == TokenType.CLASS || t == TokenType.INTERFACE || t == TokenType.RECORD
                || t == TokenType.ENUM || t == TokenType.ENTITY) {
            return false;
        }
        return t == TokenType.IDENTIFIER || t == TokenType.VOID || t == TokenType.LPAREN
                || TypeParser.isPrimitiveTypeToken(t);
    }

    /**
     * Kof.expr spike-0 (`D-KOF-EXPR`) — {@code expr} é keyword CONTEXTUAL
     * (IDENTIFIER no lexer), como {@code sealed}: só é o início de uma
     * declaração de padrão quando vem IDENTIFIER(expr) + IDENTIFIER(nome) + `{`.
     * Em qualquer outro lugar `expr` segue um identificador comum, então código
     * existente que nomeia algo `expr` continua compilando (R2 aditivo).
     */
    public boolean exprDeclarationAhead() {
        if (!wordIs("expr")) return false;
        if (pos + 2 >= tokens.size()) return false;
        if (tokens.get(pos + 1).type() == TokenType.IDENTIFIER) {
            return tokens.get(pos + 2).type() == TokenType.LBRACE;
        }
        // O nome do padrão é um NOME, não um slot estrutural: não passa pelo
        // vocabulário do perfil. Uma palavra PT que coincide com keyword da
        // superfície (`digito`, `letra`) segue sendo nome válido — traduzir um
        // identificador é a violação da regra-ouro PARTE 5.
        return isPatternNameToken(pos + 1) && tokens.get(pos + 2).type() == TokenType.LBRACE;
    }

    /** O token em {@code i} pode nomear um padrão: qualquer palavra, inclusive uma keyword léxica (`digito`, `letra` em PT). */
    private boolean isPatternNameToken(int i) {
        if (i < 0 || i >= tokens.size()) return false;
        String old = tokens.get(i).type().name();
        return old.endsWith("_TYPE");
    }

    private boolean isPatternNameToken() {
        return isPatternNameToken(pos);
    }

    boolean atEnd() {
        return pos >= tokens.size() || peek().type() == TokenType.EOF;
    }

    public Token peek() {
        return tokens.get(Math.min(pos, tokens.size() - 1));
    }

    /** O token em {@code pos + off} (EOF ao sair da faixa) — lookahead de leitura. */
    public Token peekOffset(int off) {
        int k = Math.min(pos + off, tokens.size() - 1);
        return tokens.get(Math.max(k, 0));
    }

    public Token advance() {
        if (!atEnd()) pos++;
        return tokens.get(pos - 1);
    }

    public Token expect(TokenType type, String message, String code) {
        if (check(type)) return advance();
        diagnostics.error(file, peek().line(), peek().column(), peek().length(), message, code);
        return peek();
    }

    void expectSemicolon() {
        if (check(TokenType.SEMICOLON)) {
            advance();
        }
    }

    public String expectId(String message, String code) {
        // Nome de padrão (`expr NOME {`): o valor TIPADO da palavra é aceito
        // como nome — a superfície PT usa `digito`/`letra`, que são keywords
        // léxicas daquele perfil. O vocabulário não dita nomes de usuário.
        if (check(TokenType.IDENTIFIER) || isPatternNameToken()) return advance().value();
        // #330: `fn`/`fun`/`func` são RESERVADAS (SG-001) — em posição de nome
        // o diagnóstico certo é PARSE085 com a forma correta, não o genérico
        // do chamador. Lexer + Parser (topo de arquivo) já fazem o mesmo.
        if (check(TokenType.FUN, TokenType.FN, TokenType.FUNC)) {
            Token kw = peek();
            advance();
            diagnostics.error(file, kw.line(), kw.column(), kw.value().length(),
                    "'" + kw.value() + "' is a reserved word (Kof has no function keyword); "
                    + "declare as 'Type name(...) { }' or 'name(...): Type { }'", "PARSE085", kw.value());
            return "error";
        }
        diagnostics.error(file, peek().line(), peek().column(), peek().length(), message, code);
        return "error";
    }

    boolean isTypeKeywordField() {
        return check(TokenType.INT_TYPE, TokenType.LONG_TYPE, TokenType.FLOAT_TYPE,
                TokenType.DOUBLE_TYPE, TokenType.BOOL_TYPE, TokenType.BYTE_TYPE,
                TokenType.SHORT_TYPE, TokenType.CHAR_TYPE, TokenType.STRING_TYPE,
                TokenType.RECORD);
    }

    public boolean isTypeKeywordAtNext() {
        if (pos + 1 >= tokens.size()) return false;
        return switch (tokens.get(pos + 1).type()) {
            case INT_TYPE, LONG_TYPE, FLOAT_TYPE, DOUBLE_TYPE, BOOL_TYPE,
                    BYTE_TYPE, SHORT_TYPE, CHAR_TYPE, STRING_TYPE -> true;
            default -> false;
        };
    }

    public SourcePosition pos() {
        Token t = peek();
        return new SourcePosition(file, t.line(), t.column(), t.offset(), t.length());
    }

    public void error(String message, String code) {
        diagnostics.error(file, peek().line(), peek().column(), peek().length(), message, code);
    }

    /** D-PORTUKOF F6: captura de ARGUMENTOS ESTRUTURADOS (a mensagem EN continua
     *  idêntica; os args alimentam a renderização PT pelo código). */
    public void error(String message, String code, Object... args) {
        diagnostics.error(file, peek().line(), peek().column(), peek().length(),
                message, code, args);
    }

    /** #1095: aviso NÃO-fatal na posição do token corrente (a precedência
     *  congelada não muda; o leitor é avisado do que o parser realmente faz). */
    public void warning(String message, String code) {
        diagnostics.warning(file, peek().line(), peek().column(), peek().length(), message, code);
    }
}
