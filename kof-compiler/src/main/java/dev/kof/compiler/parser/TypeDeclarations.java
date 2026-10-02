package dev.kof.compiler.parser;

import dev.kof.compiler.AnnotationNode;
import dev.kof.compiler.AstNode;
import dev.kof.compiler.ClassDeclarationNode;
import dev.kof.compiler.EntityDeclarationNode;
import dev.kof.compiler.EntityFieldNode;
import dev.kof.compiler.EnumDeclarationNode;
import dev.kof.compiler.ExpressionNode;
import dev.kof.compiler.InterfaceDeclarationNode;
import dev.kof.compiler.RecordComponentNode;
import dev.kof.compiler.RecordDeclarationNode;
import dev.kof.compiler.SourcePosition;
import dev.kof.compiler.TokenType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Declarações de tipo do parser (REFACTOR-500, Fase 7 — extraído de
 * {@link Parser} no ratchet do §140, regra ≤500): dispatch de type-decl,
 * class/interface/record/enum/entity, modifiers e componentes de record.
 * Compartilha o MESMO {@link ParseContext} (cursor {@code pos} único);
 * nenhuma mudança de comportamento — apenas movimento de código.
 */
final class TypeDeclarations {

    static final Set<String> PRIMITIVE_TYPE_NAMES = Set.of(
            "bool", "byte", "short", "int", "long", "float", "double", "char", "string", "void"
    );

    private TypeDeclarations() {}

    static AstNode parseTypeDeclaration(ParseContext ctx, List<AnnotationNode> annos) {
        List<String> mods = parseModifiers(ctx);
        if (ctx.check(TokenType.CLASS)) return parseClassDeclaration(ctx, mods, annos);
        if (ctx.check(TokenType.INTERFACE)) return parseInterfaceDeclaration(ctx, mods, annos);
        if (ctx.check(TokenType.RECORD)) return parseRecordDeclaration(ctx, mods, annos);
        if (ctx.check(TokenType.ENUM)) return parseEnumDeclaration(ctx, mods, annos);
        if (ctx.check(TokenType.ENTITY)) return parseEntityDeclaration(ctx, mods, annos);
        ctx.error("Expected type declaration", "PARSE007");
        ctx.advance();
        return new ClassDeclarationNode(ctx.pos(), "error", List.of(), null, List.of(), List.of(), List.of(), annos);
    }

    static List<String> parseModifiers(ParseContext ctx) {
        List<String> mods = new ArrayList<>();
        while (true) {
            if (ctx.check(TokenType.PUBLIC, TokenType.PRIVATE, TokenType.PROTECTED, TokenType.STATIC,
                    TokenType.FINAL, TokenType.ABSTRACT, TokenType.TRANSIENT, TokenType.VOLATILE,
                    TokenType.SYNCHRONIZED, TokenType.NATIVE, TokenType.DEFAULT, TokenType.OVERRIDE)) {
                mods.add(ctx.advance().value());
                continue;
            }
            // X5.1 (D-X5-SURFACE): `sealed` contextual antes de class/record/
            // interface — vira modificador; fora desse contexto segue IDENTIFIER.
            if (ctx.sealedModifierAhead()) {
                mods.add(ctx.advance().value());
                continue;
            }
            break;
        }
        return mods;
    }

    /** enum Name { A, B, C } — constantes apenas (MVP P1). */
    static AstNode parseEnumDeclaration(ParseContext ctx, List<String> mods, List<AnnotationNode> annos) {
        ctx.expect(TokenType.ENUM, "Expected 'enum'", "PARSE030");
        String name = ctx.expectId("Expected enum name", "PARSE031");
        java.util.List<String> constants = new ArrayList<>();
        if (!ctx.check(TokenType.LBRACE)) {
            // §687: qualquer token entre o nome e '{' (ex.: `extends X`,
            // `implements Y`) era SILENCIOSAMENTE ignorado — o enum saía com 0
            // constantes e os tokens vazavam para o resto do parse, virando uma
            // cascata de erros enganosos. Agora: UM diagnóstico (PARSE034) +
            // recuperação LIMITADA (para em '{', ';', fim ou início de nova
            // declaração — nunca engole a declaração seguinte, R6).
            ctx.error("Expected '{' after enum name (enums are constants-only)", "PARSE034");
            while (!ctx.atEnd()
                    && !ctx.check(TokenType.LBRACE, TokenType.SEMICOLON, TokenType.CLASS,
                            TokenType.INTERFACE, TokenType.RECORD, TokenType.ENUM,
                            TokenType.ENTITY, TokenType.AT)) {
                ctx.advance();
            }
            if (ctx.check(TokenType.SEMICOLON)) ctx.advance();
        }
        if (ctx.check(TokenType.LBRACE)) {
            ctx.advance();
            while (!ctx.check(TokenType.RBRACE) && !ctx.check(TokenType.EOF)) {
                if (ctx.check(TokenType.IDENTIFIER)) {
                    constants.add(ctx.advance().value());
                } else {
                    ctx.error("Expected enum constant", "PARSE032");
                    ctx.advance();
                }
                if (ctx.check(TokenType.COMMA)) ctx.advance();
            }
            ctx.expect(TokenType.RBRACE, "Expected '}' after enum body", "PARSE033");
        }
        return new EnumDeclarationNode(ctx.pos(), name, mods, constants, annos);
    }

    static AstNode parseClassDeclaration(ParseContext ctx, List<String> mods, List<AnnotationNode> annos) {
        ctx.advance();
        String name = ctx.expectId("Expected class name", "PARSE008");
        ctx.currentClassName = name;
        List<String> typeParams = TypeParser.parseTypeParameters(ctx);

        List<RecordComponentNode> ctorParams = null;
        if (ctx.check(TokenType.LPAREN)) {
            ctorParams = new ArrayList<>();
            ctx.advance();
            if (!ctx.check(TokenType.RPAREN)) {
                ctorParams.add(parseRecordComponent(ctx));
                while (ctx.check(TokenType.COMMA)) {
                    ctx.advance();
                    ctorParams.add(parseRecordComponent(ctx));
                }
            }
            ctx.expect(TokenType.RPAREN, "Expected ')' after constructor parameters", "PARSE013");
        }

        String superClass = null;
        if (ctx.check(TokenType.EXTENDS)) {
            ctx.advance();
            superClass = TypeParser.parseTypeRef(ctx);
        }
        List<String> ifaces = parseImplementedInterfaces(ctx);

        if (ctorParams != null) {
            List<AstNode> members = new ArrayList<>();
            if (ctx.check(TokenType.LBRACE)) {
                ctx.advance();
                while (!ctx.check(TokenType.RBRACE) && !ctx.atEnd()) {
                    members.add(ClassMemberParser.parseClassMember(ctx));
                }
                ctx.expect(TokenType.RBRACE, "Expected '}' after record body", "PARSE014");
            }
            return new RecordDeclarationNode(ctx.pos(), name, mods, superClass, ifaces,
                    typeParams, List.copyOf(ctorParams), List.copyOf(members), annos);
        }
        List<AstNode> members = new ArrayList<>();
        if (ctx.check(TokenType.LBRACE)) {
            ctx.advance();
            while (!ctx.check(TokenType.RBRACE) && !ctx.atEnd()) {
                members.add(ClassMemberParser.parseClassMember(ctx));
            }
            ctx.expect(TokenType.RBRACE, "Expected '}' after class body", "PARSE009");
        }
        return new ClassDeclarationNode(ctx.pos(), name, mods, superClass, ifaces, typeParams,
                List.copyOf(members), annos);
    }

    static InterfaceDeclarationNode parseInterfaceDeclaration(ParseContext ctx, List<String> mods, List<AnnotationNode> annos) {
        ctx.advance();
        String name = ctx.expectId("Expected interface name", "PARSE010");
        // #160: interface genérica — o ramo de classe já parseava a lista de
        // type-params (`class Box<T>`); o de interface não, então `interface
        // Mapper<T>` morria em PARSE007 ("Expected type declaration"). Espelha
        // parseClassDeclaration.
        List<String> typeParams = TypeParser.parseTypeParameters(ctx);
        List<String> ifaces = new ArrayList<>();
        if (ctx.check(TokenType.EXTENDS)) {
            ctx.advance();
            ifaces.add(TypeParser.parseTypeRef(ctx));
            while (ctx.check(TokenType.COMMA)) {
                ctx.advance();
                ifaces.add(TypeParser.parseTypeRef(ctx));
            }
        }
        List<AstNode> members = new ArrayList<>();
        if (ctx.check(TokenType.LBRACE)) {
            ctx.advance();
            while (!ctx.check(TokenType.RBRACE) && !ctx.atEnd()) {
                members.add(ClassMemberParser.parseClassMember(ctx));
            }
            ctx.expect(TokenType.RBRACE, "Expected '}' after interface body", "PARSE011");
        }
        return new InterfaceDeclarationNode(ctx.pos(), name, mods, ifaces, typeParams, List.copyOf(members), annos);
    }

    static EntityDeclarationNode parseEntityDeclaration(ParseContext ctx, List<String> mods, List<AnnotationNode> annos) {
        ctx.advance(); // entity
        String name = ctx.expectId("Expected entity name", "PARSE024");
        ctx.entityNames.add(name);
        List<EntityFieldNode> fields = new ArrayList<>();
        ctx.expect(TokenType.LBRACE, "Expected '{' after entity name", "PARSE024");
        while (!ctx.check(TokenType.RBRACE) && !ctx.atEnd()) {
            SourcePosition fieldPos = ctx.pos();
            // §381: expectId reporta SEM consumir; num nome de campo
            // palavra-reservada o loop fazia progresso-zero e vivia alocando
            // diagnostico + node ate o heap morrer (OOM medido). Recuperacao
            // de panico classica: reporta uma vez e consome o token ofensor
            // — a gramatica nao muda (decisao do maintainer 20/09).
            if (!ctx.check(TokenType.IDENTIFIER)) {
                ctx.expectId("Expected field name in entity", "PARSE024");
                ctx.advance();
                continue;
            }
            String fieldName = ctx.expectId("Expected field name in entity", "PARSE024");
            ctx.expect(TokenType.COLON, "Expected ':' after field name", "PARSE024");
            String fieldType = TypeParser.parseTypeRef(ctx);
            boolean generated = false;
            boolean unique = false;
            while (ctx.check(TokenType.GENERATED, TokenType.UNIQUE)) {
                if (ctx.check(TokenType.GENERATED)) generated = true;
                if (ctx.check(TokenType.UNIQUE)) unique = true;
                ctx.advance();
            }
            fields.add(new EntityFieldNode(fieldPos, fieldType, fieldName, generated, unique));
            if (ctx.pos().offset() == fieldPos.offset()) ctx.advance(); // trava de progresso
        }
        ctx.expect(TokenType.RBRACE, "Expected '}' after entity body", "PARSE024");
        return new EntityDeclarationNode(ctx.pos(), name, mods, fields, annos);
    }

    static RecordDeclarationNode parseRecordDeclaration(ParseContext ctx, List<String> mods, List<AnnotationNode> annos) {
        ctx.advance();
        String name = ctx.expectId("Expected record name", "PARSE012");
        List<String> typeParams = TypeParser.parseTypeParameters(ctx);
        String superClass = null;
        if (ctx.check(TokenType.EXTENDS)) {
            ctx.advance();
            superClass = TypeParser.parseTypeRef(ctx);
        }
        // #325: a forma canonical do Kof e `record Point(Int x, Int y)
        // implements Describable { }` — COMPONENTES antes do `implements`,
        // igual ao `class X(params) implements I` (l. parseTypeDeclaration).
        // O parser antigo so aceitava a ordem java (`implements` antes dos
        // parenteses) e dava PARSE007 em cascata na forma canonical. Ordem
        // java continua aceita (retro-compatibilidade: saida do decompiler,
        // Fase E, e codigo existente na natureza).
        List<String> ifaces = new ArrayList<>();
        List<RecordComponentNode> components;
        if (ctx.check(TokenType.IMPLEMENTS)) {
            ifaces = parseImplementedInterfaces(ctx);
            components = parseRecordComponents(ctx);
        } else {
            components = parseRecordComponents(ctx);
            ifaces = parseImplementedInterfaces(ctx);
        }
        List<AstNode> members = new ArrayList<>();
        if (ctx.check(TokenType.LBRACE)) {
            ctx.advance();
            while (!ctx.check(TokenType.RBRACE) && !ctx.atEnd()) {
                members.add(ClassMemberParser.parseClassMember(ctx));
            }
            ctx.expect(TokenType.RBRACE, "Expected '}' after record body", "PARSE014");
        }
        return new RecordDeclarationNode(ctx.pos(), name, mods, superClass, ifaces,
                typeParams, List.copyOf(components), List.copyOf(members), annos);
    }

    /** Componentes `( ... )` de um record — separados do corpo p/ o
     *  dispatcher conseguir parsear `implements` entre os dois (#325). */
    static List<RecordComponentNode> parseRecordComponents(ParseContext ctx) {
        List<RecordComponentNode> components = new ArrayList<>();
        if (ctx.check(TokenType.LPAREN)) {
            ctx.advance();
            if (!ctx.check(TokenType.RPAREN)) {
                components.add(parseRecordComponent(ctx));
                while (ctx.check(TokenType.COMMA)) {
                    ctx.advance();
                    components.add(parseRecordComponent(ctx));
                }
            }
            ctx.expect(TokenType.RPAREN, "Expected ')' after record components", "PARSE013");
        }
        return components;
    }

    static List<String> parseImplementedInterfaces(ParseContext ctx) {
        List<String> ifaces = new ArrayList<>();
        if (ctx.check(TokenType.IMPLEMENTS)) {
            ctx.advance();
            ifaces.add(TypeParser.parseTypeRef(ctx));
            while (ctx.check(TokenType.COMMA)) {
                ctx.advance();
                ifaces.add(TypeParser.parseTypeRef(ctx));
            }
        }
        return ifaces;
    }

    static RecordComponentNode parseRecordComponent(ParseContext ctx) {
        List<AnnotationNode> annos = AnnotationParser.parseAnnotations(ctx);
        List<String> mods = parseModifiers(ctx);
        String type = TypeParser.parseTypeRef(ctx);
        String name = ctx.expectId("Expected component name", "PARSE015");
        ExpressionNode init = null;
        if (ctx.check(TokenType.EQUAL)) {
            ctx.advance();
            init = ExpressionParser.parseExpression(ctx);
        }
        return new RecordComponentNode(ctx.pos(), mods, type, name, init, annos);
    }
}
