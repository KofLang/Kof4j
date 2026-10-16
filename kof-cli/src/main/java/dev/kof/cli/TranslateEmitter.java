package dev.kof.cli;

import java.util.ArrayList;
import java.util.List;

/**
 * Satélite do REFACTOR-500 (check_500, Translate 730→<600): o Emitter
 * Java→Kof (as fases A-F) vira classe top-level — já era uma static class
 * nomeada com estado próprio (extends TranslateStatements). Zero mudança de
 * comportamento.
 */
final class TranslateEmitter extends TranslateStatements {
    StringBuilder out = new StringBuilder();
    final StringBuilder topFns = new StringBuilder();
    /** Fase F: classes aninhadas hoisted (emitidas após as top-level). */
    final StringBuilder topClasses = new StringBuilder();
    /** Nomes das aninhadas static achadas pela pré-varredura. */
    final java.util.Set<String> nestedTypes = new java.util.HashSet<>();
    /** Campos `static` da classe em parse — p/ qualificar em funções hoisted. */
    final java.util.Set<String> staticFields = new java.util.HashSet<>();
    /**
     * Fase F (fold de `static {}`): atribuições LITERAIS a campos estáticos
     * declarados — cada uma vira o inicializador do campo no fechamento da
     * classe (o campo é emitido ANTES do bloco no fluxo de texto; a dobra
     * reescreve a linha do campo). Uma instrução não-literal recusa o
     * bloco INTEIRO (nada parcial em silêncio).
     */
    final java.util.LinkedHashMap<String, String> pendingStaticInits = new java.util.LinkedHashMap<>();
    /**
     * Fase F (fold de `{}` de instância): o corpo é prefixado em CADA
     * construtor (a ordem textual do Java = bloco antes do construtor).
     * Bloco após o primeiro construtor → recusa nomeada (ordem textual não
     * recriável neste parser de um passe).
     */
    private final List<String> instanceInitBody = new ArrayList<>();
    private boolean sawConstructor = false;

    TranslateEmitter(Parser p) { super(p); }

    String translate() {
        // Skip package / imports.
        while (p.at("package")) {
            while (!p.at(";")) p.next();
            p.next(); // ;
        }
        while (p.at("import")) {
            boolean isStatic = p.peek(1) != null && "static".equals(p.peek(1).text);
            // FQN do import (após `import`/`import static`).
            p.next();
            if (isStatic) { p.next(); }
            String fqn = "";
            while (!p.at(";") && !p.at(T.EOF)) {
                fqn += p.next().text;
            }
            if (!p.at(T.EOF)) { p.next(); } // ;
            // `import static java.lang.Math.max` / `java.lang.Math.*`:
            // o translator ignora imports e Kof não mapeia a stdlib JDK —
            // emitir a chamada nua (`max(3,4)`) gera Kof inválido
            // (SEM011), silenciosamente. Mapear Java→stdlib é decisão de
            // design (regra 6) → gap honesto R6 (Q4 13/09). Imports
            // estáticos de classes do PRÓPRIO programa ficam de fora
            // (o static method vira função top-level Kof e resolve).
            if (isStatic && (fqn.startsWith("java.") || fqn.startsWith("javax."))) {
                throw new TranslateException(
                        "`import static " + fqn + "` is not resolved by the translator "
                        + "(Kof does not map static members of the JDK stdlib; imports are ignored) — "
                        + "manual review");
            }
        }
        // Fase F: pré-varredura dos tipos aninhados static (`Inner` →
        // `Outer_Inner`) — TODA referência de tipo passa por kofType, que
        // aplica o rename; colisões recusam com nome (nada silencioso).
        TranslateTypes.NESTED_RENAMES.clear();
        nestedTypes.clear();
        scanNestedTypes();
        // Parse all top-level type declarations.
        while (!p.at(T.EOF)) {
            parseTypeDeclaration();
        }
        // Static methods were collected as top-level functions; emit them
        // first; the hoisted nested classes come last (Kof resolves por
        // nome, a ordem entre classes não carrega semântica aqui).
        out.insert(0, topFns);
        out.append('\n').append(topClasses);
        // Fase F: o rename por kofType cobre POSIÇÕES DE TIPO; referências
        // em EXPRESSÃO (`T.Inner.X`, `Inner.X` — acesso a membro estático
        // da aninhada) não passam por ele — o pós-passo textual reescreve
        // as formas qualificada e nua-SEGUIDA-DE-PONTO (a nua `Inner.` só
        // matcheia com fronteira de palavra e não precedida de `.` — uma
        // variável local chamada exatamente como o tipo está fora do
        // subconjunto suportado e é a única forma de falso positivo).
        String text = out.toString();
        for (var e : TranslateTypes.NESTED_RENAMES.entrySet()) {
            if (e.getKey().contains(".")) {
                text = text.replace(e.getKey(), e.getValue());
            } else {
                text = text.replaceAll("(?<![\\w.])" + java.util.regex.Pattern.quote(e.getKey()) + "\\.", e.getValue() + ".");
            }
        }
        return text;
    }

    /**
     * Fase F (decisão da mantenedora, 14/10): pré-varredura dos tipos
     * aninhados `static` no nível 1 de cada tipo top-level. Preenche
     * `nestedTypes` + o mapa `Inner`/`Outer.Inner` → `Outer_Inner`.
     * Colisões recusam NOMEADAS: dois outers com o mesmo `Inner`
     * (referência nua ambígua) ou `Outer_Inner` já usado por um top-level.
     */
    private void scanNestedTypes() {
        var toks = p.toks;
        java.util.Set<String> topLevel = new java.util.HashSet<>();
        java.util.List<String[]> pairs = new java.util.ArrayList<>(); // [outer, inner]
        String currentTop = null;
        int depth = 0;
        for (int i = 0; i < toks.size(); i++) {
            String tk = toks.get(i).text;
            if (tk.equals("{")) { depth++; continue; }
            if (tk.equals("}")) { depth--; if (depth == 0) { currentTop = null; } continue; }
            boolean isTypeKw = tk.equals("class") || tk.equals("interface")
                    || tk.equals("record") || tk.equals("enum");
            if (!isTypeKw || i + 1 >= toks.size()) continue;
            String name = toks.get(i + 1).text;
            if (depth == 0) {
                if (currentTop == null) { currentTop = name; topLevel.add(name); }
            } else if (depth == 1 && currentTop != null
                    && i > 0 && toks.get(i - 1).text.equals("static")) {
                pairs.add(new String[]{currentTop, name});
            }
        }
        java.util.Map<String, String> innerOwner = new java.util.HashMap<>();
        for (String[] pr : pairs) {
            String previous = innerOwner.put(pr[1], pr[0]);
            if (previous != null && !previous.equals(pr[0])) {
                throw new TranslateException(
                        "nested static type `" + pr[1] + "` is declared in both `" + previous
                        + "` and `" + pr[0] + "` — a bare reference would be ambiguous after the "
                        + "top-level hoist — manual review");
            }
            String hoisted = pr[0] + "_" + pr[1];
            if (topLevel.contains(hoisted)) {
                throw new TranslateException(
                        "nested static type `" + pr[0] + "." + pr[1] + "` hoists to `" + hoisted
                        + "` but that top-level type already exists — manual review");
            }
            nestedTypes.add(pr[1]);
            TranslateTypes.NESTED_RENAMES.put(pr[1], hoisted);
            TranslateTypes.NESTED_RENAMES.put(pr[0] + "." + pr[1], hoisted);
        }
    }

    private void parseTypeDeclaration() {
        // modifiers + annotations (@Override, @SuppressWarnings(...)) —
        // Kof ignora anotações no translator (não são semântica p/ o
        // subconjunto; a anotação some junto com o `@Nome` e args).
        skipAnnotationsAndModifiers();
        if (p.at("class")) {
            parseClass();
        } else if (p.at("interface")) {
            parseInterface();
        } else if (p.at("record")) {
            parseRecord();
        } else if (p.at("enum")) {
            parseEnum();
        } else {
            throw new TranslateException("expected class/interface/record/enum, found '" + p.peek().text + "'");
        }
    }

    /**
     * Consome modificadores e anotações Java (`@Override`,
     * `@SuppressWarnings("x")`, `@Deprecated(...)`). Anotações não têm
     * semântica no subconjunto traduzido → descartadas (Kof as ignora,
     * verificado 13/09).
     */
    private void skipAnnotationsAndModifiers() {
        while (true) {
            if (TranslateTypes.isModifier(p.peek().text)) { p.next(); continue; }
            if (p.at(T.AT)) {
                p.next();                 // @
                p.next();                 // Nome
                while (p.at(".")) { p.next(); p.next(); }  // @a.b.C
                if (p.at("(")) {          // args
                    int depth = 0;
                    do {
                        if (p.at("(")) depth++;
                        else if (p.at(")")) depth--;
                        p.next();
                    } while (depth > 0 && !p.at(T.EOF));
                }
                continue;
            }
            break;
        }
    }

    private void parseEnum() {
        p.expect("enum");
        String name = p.next().text;
        List<String> constants = new ArrayList<>();
        p.expect("{");
        while (!p.at("}") && !p.at(";")) {
            constants.add(p.next().text);
            if (p.at("(") || p.at("{")) {
                // Argumentos de constante (`A(1)`) ou corpo de constante
                // (`A { ... }`) exigem construtor/override — Kof enum é
                // só o NOME. Antes era pulado em SILÊNCIO (R6/Q7).
                throw new TranslateException(
                        "enum with a constructor/constant body (`" + constants.get(constants.size() - 1)
                        + "(…)` / `{ … }`) has no equivalent in Kof "
                        + "(enum = constants only) — manual review");
            }
            if (p.at(",")) p.next();
        }
        if (p.at(";")) {
            p.next();
            // `enum E { A, B; }` (só o `;` de fechamento) é no-op; com
            // conteúdo, Kof enum tem SÓ constantes, sem corpo — antes era
            // pulado em SILÊNCIO → `E.A.get()`/`E.A.v` sumiam (R6).
            if (!p.at("}")) {
                throw new TranslateException(
                        "enum with a body (fields/methods/constructor) has no equivalent in Kof "
                        + "(enum = constants only; use a `class` with `static` if you need data) — "
                        + "manual review");
            }
        }
        p.expect("}");
        out.append("enum ").append(name).append(" { ")
           .append(String.join(", ", constants)).append(" }\n");
    }

    private void parseRecord() {
        TranslateTypeDecls.parseRecord(this);
    }

    private void parseClass() {
        p.expect("class");
        String name = p.next().text;
        String typeParams = p.at("<") ? parseTypeParams() : "";
        String superCls = null;
        List<String> ifaces = new ArrayList<>();
        if (p.at("extends")) { p.next(); superCls = p.next().text; }
        if (p.at("implements")) {
            p.next();
            while (!p.at("{")) { ifaces.add(p.next().text); if (p.at(",")) p.next(); }
        }
        p.expect("{");
        // Pré-varre os campos `static` (p/ qualificar refs nas funções
        // hoisted — `X` → `Classe.X`; ver TranslateStatics).
        staticFields.clear();
        staticFields.addAll(TranslateStatics.scanFieldNames(p, p.pos));
        pendingStaticInits.clear();
        instanceInitBody.clear();
        sawConstructor = false;
        out.append("class ").append(TranslateTypes.kofType(name)).append(typeParams);
        if (superCls != null) out.append(" extends ").append(TranslateTypes.kofType(superCls));
        if (!ifaces.isEmpty()) {
            out.append(" implements ");
            out.append(ifaces.stream().map(TranslateTypes::kofType).collect(java.util.stream.Collectors.joining(", ")));
        }
        out.append(" {\n");

        while (!p.at("}")) {
            parseMember(name);
        }
        p.expect("}");
        if (!instanceInitBody.isEmpty() && !sawConstructor) {
            emitConstructor(List.of(), List.of());
        }
        applyPendingStaticInits();
        out.append("}\n");
    }

    /**
     * Fase F: reescreve as linhas dos campos que receberam dobra —
     * `    static Int V` → `    static Int V = 42` (ou substitui o
     * inicializador existente: no Java o bloco roda DEPOIS do
     * inicializador do campo, então o valor do bloco vence).
     */
    private void applyPendingStaticInits() {
        for (var e : pendingStaticInits.entrySet()) {
            java.util.regex.Pattern line = java.util.regex.Pattern.compile(
                    "(^|\\n)(    static [^ =\\n]+ " + java.util.regex.Pattern.quote(e.getKey()) + ")( = [^\\n]*)?(\\n)");
            java.util.regex.Matcher m = line.matcher(out);
            if (!m.find()) {
                throw new TranslateException(
                        "static initialization block folds `" + e.getKey()
                        + "` but no matching static field line was emitted — translator bug — manual review");
            }
            String replacement = m.group(1) + m.group(2) + " = " + e.getValue() + m.group(4);
            out.replace(m.start(), m.end(), replacement);
        }
    }

    private void parseInterface() {
        p.expect("interface");
        String name = p.next().text;
        String typeParams = p.at("<") ? parseTypeParams() : "";
        List<String> ext = new ArrayList<>();
        if (p.at("extends")) {
            // Kof interface suporta `extends A, B` (verificado 13/09).
            p.next();
            ext.add(p.next().text);
            while (p.at(",")) { p.next(); ext.add(p.next().text); }
        }
        p.expect("{");
        out.append("interface ").append(name).append(typeParams);
        if (!ext.isEmpty()) {
            out.append(" extends ").append(String.join(", ", ext));
        }
        out.append(" {\n");
        while (!p.at("}")) {
            // method signature ending in ';'
            while (TranslateTypes.isModifier(p.peek().text)) {
                p.next();
            }
            String ret = parseType();
            String mname = p.next().text;
            if (!p.at("(")) {
                // Campo/constante de interface Java (`int X = 1;` é
                // implicitamente `static final`). Kof aceita declarar, mas
                // não resolve o campo (`I.X` → SEM025, verificado 13/09) e
                // não há constante top-level → manual review (R6).
                throw new TranslateException(
                        "interface constant (`Type NAME = ...` in an interface) has no "
                        + "direct equivalent in Kof (an interface field is not resolvable, "
                        + "SEM025) — manual review");
            }
            List<String> params = parseParams();
            if (p.at("{")) {
                // Método de interface COM corpo = `default` (ou `static`)
                // method Java. Kof NÃO tem default method: o corpo é
                // IGNORADO e o implementador falha com SEM043
                // (re-verificado 13/09 — a nota anterior "Kof aceita corpo"
                // era verificação falsa). Emitir o corpo seria um Kof que
                // não compila; dropá-lo silenciosamente muda o
                // comportamento → gap honesto (R6).
                throw new TranslateException(
                        "interface method with a body (`default`/`static` method) has no "
                        + "direct equivalent in Kof (sem default method; o implementador "
                        + "would fail with SEM043) — move the body to the class — manual review");
            }
            p.expect(";");
            out.append("    ").append(ret).append(' ').append(mname).append('(')
               .append(paramList(params)).append("): ").append(ret).append('\n');
        }
        p.expect("}");
        out.append("}\n");
    }

    /**
     * Tipo genérico Java `<T>` / `<K, V>` → Kof `<T>` / `<K, V>`.
     * Bounds (`<T extends X>`) não têm equivalente direto → revisão
     * manual (R6).
     */
    String parseTypeParams() {
        p.expect("<");
        List<String> tps = new ArrayList<>();
        while (!p.at(">") && !p.at(T.EOF)) {
            String tp = p.next().text;
            if (p.at("extends")) {
                throw new TranslateException(
                        "type parameter with a bound (`<T extends X>`) has no equivalent "
                        + "direct in Kof — manual review");
            }
            tps.add(tp);
            if (p.at(",")) p.next();
        }
        p.expect(">");
        return "<" + String.join(", ", tps) + ">";
    }

    private void parseMember(String className) {
        boolean isStatic = false;
        while (true) {
            if (TranslateTypes.isModifier(p.peek().text)) {
                if (p.at("static")) isStatic = true;
                p.next();
                continue;
            }
            if (p.at(T.AT)) {
                p.next(); p.next();
                while (p.at(".")) { p.next(); p.next(); }
                if (p.at("(")) {
                    int depth = 0;
                    do {
                        if (p.at("(")) depth++;
                        else if (p.at(")")) depth--;
                        p.next();
                    } while (depth > 0 && !p.at(T.EOF));
                }
                continue;
            }
            break;
        }
        if (p.at("{")) {
            // Bloco de inicialização. AMBOS têm efeito: o `static {}`
            // inicializa campos estáticos; o de INSTÂNCIA roda antes do
            // construtor. Pulá-los em silêncio deixaria campos com o
            // default errado (R6, Q4 13/09). FATIA FASE F: os dois CASOS
            // SIMPLES dobram — `static {}` com SÓ atribuições literais a
            // campos estáticos declarados vira o inicializador do campo;
            // `{}` de instância é prefixado em cada construtor. Qualquer
            // instrução além disso → recusa NOMEADA (nada parcial).
            if (isStatic) {
                TranslateStaticInit.foldStaticInitBlock(this);
            } else {
                foldInstanceInitBlock();
            }
            return;
        }
        if (p.at("class") || p.at("interface") || p.at("record") || p.at("enum")) {
            // Fase F: `static` nested HOISTA para o topo como
            // `Outer_Inner` (decisão da mantenedora 14/10) — a pré-varredura
            // já registrou o rename; o corpo é emitido no buffer de classes
            // hoisted (um tipo dentro do corpo textual do outer seria Kof
            // inválido). NÃO-static inner captura a instância externa
            // (`Outer.this`) — sem o campo sintético `this$Outer` (Opção B
            // não aprovada) → recusa nomeada.
            String innerName = p.peek(1) != null ? p.peek(1).text : "";
            if (!isStatic) {
                throw new TranslateException(
                        "non-static inner class (`" + innerName + "` inside `" + className
                        + "` captures the enclosing instance via `Outer.this`) — the translator "
                        + "does not emit the synthetic `this$Outer` field; declare it `static` or "
                        + "extract it to the top level — manual review");
            }
            if (!nestedTypes.contains(innerName)) {
                throw new TranslateException(
                        "nested static type `" + innerName + "` was not found by the hoist "
                        + "pre-scan (nested-nested types are not hoisted) — manual review");
            }
            StringBuilder savedOut = out;
            out = topClasses;
            try {
                if (p.at("class")) parseClass();
                else if (p.at("interface")) parseInterface();
                else if (p.at("record")) parseRecord();
                else parseEnum();
            } finally {
                out = savedOut;
            }
            return;
        }
        // Construtor Java: `[mods] ClassName ( params ) { body }` — sem tipo
        // de retorno. Precisa ser detectado ANTES de parseType, senão o nome
        // da classe vira "tipo" e o `(` vira "nome do membro" → o ramo de
        // campo escaneia até o `;` (que não existe) e trava no EOF
        // (bug latente achado 13/09: loop infinito em `kof translate`).
        if (p.peek().text.equals(className) && p.peek(1).text.equals("(")) {
            p.next(); // nome da classe
            List<String> params = parseParams();
            if (p.at("throws")) {
                p.next();
                while (!p.at("{") && !p.at(";") && !p.at(T.EOF)) p.next();
            }
            if (p.at(";")) { p.next(); return; }
            List<String> body = parseBlock();
            emitConstructor(params, body);
            return;
        }
        String typeParams = "";
        if (p.at("<")) {
            typeParams = parseTypeParams();
        }
        String typeName = parseType();
        String memberName = p.next().text;
        if (p.at("(")) {
            // method
            List<String> params = parseParams();
            // `throws E1, E2` — Kof não declara throws (exceções são
            // Strings, sempre propagáveis) → consumir e descartar.
            if (p.at("throws")) {
                p.next();
                while (!p.at("{") && !p.at(";") && !p.at(T.EOF)) p.next();
            }
            if (p.at(";")) {
                // Método sem corpo (`abstract`/`native`) em CLASSE: Kof
                // has no — toda função tem corpo. Antes era dropado em
                // SILÊNCIO → a chamada virava SEM011 (Kof inválido), Q4.
                throw new TranslateException(
                        "method with no body (`abstract`/`native` `" + memberName + "`) in class "
                        + "has no equivalent in Kof (every function has a body) — manual review");
            }
            List<String> body = parseBlock();
            emitMethod(isStatic, typeName, className, memberName, typeParams, params, body);
        } else {
            // field: "Type name [= expr];"
            String init = "";
            if (p.at("=")) {
                p.next();
                if (p.at("{")) {
                    // Array initializer em CAMPO `int[] xs = {1,2,3}` — mesmo
                    // gap honesto do local (TranslateStatements); sem o guard
                    // o output saía TRUNCADO (`Int[] xs = {`) = Kof inválido
                    // (bug latente achado 13/09 no probe Q4).
                    throw new TranslateException(
                            "array initializer `{...}` has no direct equivalent in Kof "
                            + "(use `new Int[n]` + assignments or `listOf(...)`) — manual review");
                }
                init = " = " + parseExpr();
            }
            while (!p.at(";") && !p.at(T.EOF)) p.next();
            if (p.at(";")) p.next();
            // Campo estático Java → `static` em Kof (Kof suporta campo
            // estático de classe — verificado 13/09: `static Int X = 5` +
            // `A.X` compila e roda). Antes era SKIPADO silenciosamente →
            // referência virava `Undefined variable or type: 'X'`
            // (SEM011) = Kof inválido (bug latente Q4).
            out.append("    ").append(isStatic ? "static " : "")
               .append(TranslateTypes.kofType(typeName)).append(' ').append(memberName).append(init).append('\n');
        }
    }

    /**
     * Fase F: literal Java PURO — a única forma que dobra sem mudar o
     * observável (int/long/hex/binário, decimal com sufixo, boolean,
     * null, char, string com escapes simples, e o menos unário).
     */
    private static boolean isPureLiteral(String v) {
        if (v == null || v.isEmpty()) return false;
        String s = v.startsWith("-") ? v.substring(1) : v;
        if (s.equals("true") || s.equals("false") || s.equals("null")) return true;
        if (s.startsWith("\"")) {
            // string: abre, fecha, e nenhum escape complicado no meio
            return s.length() >= 2 && s.endsWith("\"") && s.indexOf('"', 1) == s.length() - 1;
        }
        if (s.startsWith("'")) {
            return s.length() == 3 && s.endsWith("'");
        }
        return s.matches("[0-9_]+") || s.toLowerCase().matches("0x[0-9a-f_]+")
                || s.matches("\\d*\\.?\\d*([eE][-+]?\\d+)?[fFdDlL]?") && s.matches(".*\\d.*");
    }


    /**
     * Fase F: `{ ... }` de instância é prefixado em CADA construtor (o
     * Java roda o bloco antes do corpo do construtor). Bloco textualmente
     * APÓS o primeiro construtor → recusa nomeada (este parser é de um
     * passe; reordenar os corpos mudaria o observável).
     */
    private void foldInstanceInitBlock() {
        if (sawConstructor) {
            throw new TranslateException(
                    "instance initializer block appears after the first constructor — folding "
                    + "into the constructors would reorder execution (Kof has no instance init "
                    + "block) — manual review");
        }
        instanceInitBody.addAll(parseBlock());
    }

    private void emitConstructor(List<String> params, List<String> body) {
        sawConstructor = true;
        out.append("    constructor(").append(paramList(params)).append(") {\n");
        for (String stmt : instanceInitBody) out.append("        ").append(stmt).append('\n');
        for (String stmt : body) out.append("        ").append(stmt).append('\n');
        out.append("    }\n");
    }

    private void emitMethod(boolean isStatic, String retType, String owner,
                            String name, String typeParams, List<String> params, List<String> body) {
        StringBuilder sb = isStatic ? topFns : out;
        // Função hoisted: refs a campo estático ficam fora de escopo →
        // qualifica `X` → `Owner.X` (o Kof aceita `Classe.campo`).
        java.util.Set<String> shadowed = new java.util.HashSet<>();
        for (String prm : params) {
            int sp = prm.lastIndexOf(' ');
            shadowed.add(sp >= 0 ? prm.substring(sp + 1) : prm);
        }
        List<String> emitBody = new ArrayList<>(body.size());
        for (String stmt : body) {
            emitBody.add(isStatic
                    ? TranslateStatics.qualify(stmt, owner, staticFields, shadowed)
                    : stmt);
        }
        if (isStatic && name.equals("main")) {
            // Java `main(String[] args)` → top-level Kof `main(args)`.
            // Bug latente (Q4 13/09): os parâmetros eram DESCARTADOS
            // (`main()`), mas o corpo podia referenciar `args` → Kof
            // inválido (SEM011 silencioso). Kof aceita `main(String[] args)`
            // (verificado no binário: `println(args.length)` roda e dá 0).
            sb.append("main(").append(paramList(params)).append(") {\n");
            for (String stmt : emitBody) sb.append("    ").append(stmt).append('\n');
            sb.append("}\n");
            return;
        }
        sb.append("    ").append(TranslateTypes.kofType(retType)).append(' ').append(name)
          .append(typeParams).append('(').append(paramList(params)).append(')');
        if (emitBody.size() == 1 && emitBody.get(0).startsWith("return ")) {
            String expr = emitBody.get(0).substring("return ".length());
            sb.append(" = ").append(expr).append('\n');
        } else {
            sb.append(" {\n");
            for (String stmt : emitBody) sb.append("        ").append(stmt).append('\n');
            sb.append("    }\n");
        }
    }

}
