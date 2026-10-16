package dev.kof.compiler;

import java.util.HashMap;
import java.util.Map;

/**
 * Registro de tipos do módulo (REFACTOR-500 — higiene de gate check_500):
 * os índices de classes/nomes e o estado sealed/variance vivem AQUI; o
 * {@code SemanticAnalyzer} mantém apenas os delegados package-private
 * (assinaturas intactas — nenhum satélite muda). Os leitores recebem views
 * não-modificáveis; a mutação acontece dentro do dono do estado (CodeQL
 * {@code java/internal-representation-exposure}).
 */
final class ClassTypeRegistry {

    private final Map<String, SymbolTable.ClassSymbol> knownClasses = new HashMap<>();
    /**
     * #639 face 2 (D-DECISION-BATCH-2709B): índice ADITIVO por
     * {@code pkg.Type}. {@code knownClasses} continua por nome simples
     * (last-write-wins — a face 1, ainda aberta); este mapa preserva TODOS os
     * tipos por caminho qualificado, sem alterar nenhuma consulta existente.
     */
    private final Map<String, SymbolTable.ClassSymbol> classesByFqn = new HashMap<>();
    private final java.util.Set<String> interfaceNames = new java.util.HashSet<>();
    /** SG-017 (SEM041): classes declaradas `abstract` — `new A()` vira erro compile-time. */
    private final java.util.Set<String> abstractClasses = new java.util.HashSet<>();
    /** #339 (SEM070): classes declaradas `final` — `class D extends F` vira erro compile-time. */
    private final java.util.Set<String> finalClasses = new java.util.HashSet<>();
    /** X5.1 (D-X5-SURFACE): tipos `sealed` — nome simples → unidade de
     *  compilação (arquivo) que os declara. O conjunto de subtipos é fechado:
     *  um subtipo declarado fora dessa unidade é SEM080. */
    private final Map<String, String> sealedTypes = new HashMap<>();
    /** X5.3 (D-TYPE-VARIANCE): variância declaration-site por tipo genérico —
     *  nome simples → lista de variâncias ("out"/"in"/"") na ordem dos
     *  type-params. Ausente = invariante (compatibilidade total com o que
     *  já existia, §270). */
    private final Map<String, java.util.List<String>> genericVariance = new HashMap<>();
    /** §713: NOMES dos type-params (limpos, sem variância/bound) por tipo
     *  genérico — a inferência de type-args de `new C(args)` precisa deles na
     *  ORDEM declarada (a variância não basta). */
    private final Map<String, java.util.List<String>> genericTypeParams = new HashMap<>();

    /**
     * #639 face 2 (D-DECISION-BATCH-2709B): símbolo do tipo por DECLARAÇÃO
     * (identidade). {@code knownClasses} é por nome simples (colide); aqui cada
     * declaração guarda o SEU ClassSymbol, para que os membros sejam definidos
     * no símbolo certo (sem isto a 2ª package de mesmo nome ficava sem membros).
     */
    private final Map<AstNode, SymbolTable.ClassSymbol> symbolByDecl =
            new java.util.IdentityHashMap<>();
    private final java.util.Set<AstNode> memberDeclsDefined =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    void putClass(String name, SymbolTable.ClassSymbol sym) {
        knownClasses.put(name, sym);
        putClassFqn(name, sym);
    }

    /** #639 face 2: registra o índice por caminho qualificado. Aditivo —
     *  nenhuma consulta por nome simples é afetada. */
    void putClassFqn(String name, SymbolTable.ClassSymbol sym) {
        String pkg = sym.packageName();
        classesByFqn.put((pkg == null || pkg.isEmpty() ? "" : pkg + ".") + name, sym);
    }

    /** #639 face 2: resolve `pkg.Type` pelo caminho completo, preservando
     *  colisões de nome simples. Retorna null quando o caminho não identifica
     *  um tipo do módulo. */
    SymbolTable.ClassSymbol findQualifiedClass(String qualified) {
        return qualified == null ? null : classesByFqn.get(qualified);
    }

    SymbolTable.ClassSymbol getClass(String name) {
        return knownClasses.get(name);
    }

    Map<String, SymbolTable.ClassSymbol> allClasses() {
        return java.util.Collections.unmodifiableMap(knownClasses);
    }

    boolean knowsClass(String name) { return knownClasses.containsKey(name); }

    boolean isInterfaceType(String name) {
        return interfaceNames.contains(name);
    }

    void addInterface(String name) { interfaceNames.add(name); }

    java.util.Set<String> interfaceNames() {
        return java.util.Collections.unmodifiableSet(interfaceNames);
    }

    void addAbstractClass(String name) { abstractClasses.add(name); }

    java.util.Set<String> abstractClasses() {
        return java.util.Collections.unmodifiableSet(abstractClasses);
    }

    void addFinalClass(String name) { finalClasses.add(name); }

    /** #339 (SEM070): nomes simples das classes `final` do programa. */
    java.util.Set<String> finalClasses() {
        return java.util.Collections.unmodifiableSet(finalClasses);
    }

    /** X5.1: registra um tipo `sealed` com a unidade de compilação que o declara. */
    void addSealedType(String name, String file) { sealedTypes.put(name, file); }

    /** X5.1: o tipo (nome simples) foi declarado `sealed`? */
    boolean isSealedType(String name) { return sealedTypes.containsKey(name); }

    /** X5.1: unidade de compilação (arquivo) do tipo `sealed`, ou {@code null}. */
    String sealedTypeUnit(String name) { return sealedTypes.get(name); }

    /** X5.3: registra a variância declaration-site dos type-params de um tipo
     *  genérico (entradas cruas `"out T"`/`"in T"`/`"T"`). */
    void registerTypeParameters(String name, java.util.List<String> typeParameters) {
        if (name == null || typeParameters == null || typeParameters.isEmpty()) return;
        genericVariance.put(name, typeParameters.stream().map(TypeParams::variance).toList());
        genericTypeParams.put(name, TypeParams.names(typeParameters));
    }

    /** §713: nomes dos type-params declarados do tipo (ordem de declaração),
     *  ou lista vazia quando o tipo não é genérico neste módulo. */
    java.util.List<String> typeParamNames(String name) {
        java.util.List<String> n = genericTypeParams.get(name);
        return n == null ? java.util.List.of() : n;
    }

    /** X5.3: variâncias declaradas do tipo (índice = posição do type-param),
     *  ou {@code null} quando o tipo não declara type-params no módulo. */
    java.util.List<String> varianceOf(String name) { return genericVariance.get(name); }

    void registerSymbol(AstNode decl, SymbolTable.ClassSymbol sym) {
        if (decl != null && sym != null) symbolByDecl.put(decl, sym);
    }

    SymbolTable.ClassSymbol symbolForDecl(AstNode decl) {
        return decl == null ? null : symbolByDecl.get(decl);
    }

    boolean membersDefined(AstNode decl) {
        return decl != null && memberDeclsDefined.contains(decl);
    }

    void markMembersDefined(AstNode decl) {
        if (decl != null) memberDeclsDefined.add(decl);
    }
}
