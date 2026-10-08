package dev.kof.compiler.lang;

import dev.kof.compiler.TokenType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * D-PORTUKOF (07/10) — vocabulário oficial do PortuKof, a superfície pt-BR do
 * Kof. Cada linha é um ALIAS: mesma keyword, mesmo token, mesma semântica —
 * NENHUMA implementação paralela.
 *
 * <p>Decisões de vocabulário (documentadas na decisão):
 * <ul>
 *   <li>Sem acentos (portabilidade/teclado — PARTE 28): `nao`, `senao`,
 *       `funcao`... a forma ASCII é a recomendada.</li>
 *   <li>`var`/`val`/`se`/`senao`/`para` permanecem a forma canônica quando a
 *       tradução não agrega nada (PARTE 3). `var` fica — Kof não é
 *       JavaScript (D-KOF-FIRST).</li>
 *   <li>`funcao` NÃO é keyword de função: o Kof declara função por nome com
 *       tipo-antes-do-nome (corpus `training/idioms/functions.md`). A palavra
 *       fica RESERVADA como `fun`/`fn`/`func` (SG-001) e o parser a recusa.</li>
 *   <li>Um alias pode ser a IDENTIDADE (palavra igual): APIs cujo nome já é
 *       português-razoável ou termo técnico sem tradução (`byte`, `asc`,
 *       `desc`, `infra`) mantêm o nome canônico — PARTE 7 "não traduzir
 *       tudo automaticamente". Bijetividade canônico↔português continua
 *       100% e é travada por teste + gate.</li>
 *   <li>Palavras CONTEXTUAIS (IDENTIFIER no lexer, valor no parser: `test`,
 *       `using`, `sealed`, `foreign`, `in`, `out`, `constructor`, DSL de
 *       query…) NÃO entram na tabela de keywords — entrariam em colisão com
 *       identificadores do usuário. São resolvidas no parser via
 *       {@link LanguageProfile#matchesWord} na posição exata em que o Kof as
 *       resolve hoje (determinístico, PARTE 27).</li>
 * </ul>
 */
public final class PortuKofVocabulary {

    private PortuKofVocabulary() {}

    /** (canônico, português, token) — a bijetividade lexical do PortuKof. */
    private static final List<String[]> PAIRS = List.of(
            new String[]{"class", "classe", "CLASS"},
            new String[]{"interface", "interface", "INTERFACE"},
            new String[]{"record", "registro", "RECORD"},
            new String[]{"enum", "enumeracao", "ENUM"},
            new String[]{"entity", "entidade", "ENTITY"},
            new String[]{"extern", "externo", "EXTERN"},
            new String[]{"generated", "gerado", "GENERATED"},
            new String[]{"unique", "unico", "UNIQUE"},
            new String[]{"extends", "estende", "EXTENDS"},
            new String[]{"implements", "implementa", "IMPLEMENTS"},
            new String[]{"fun", "funcao", "FUN"},
            new String[]{"fn", "fn", "FN"},
            new String[]{"func", "func", "FUNC"},
            new String[]{"package", "pacote", "PACKAGE"},
            new String[]{"import", "importa", "IMPORT"},
            new String[]{"public", "publico", "PUBLIC"},
            new String[]{"private", "privado", "PRIVATE"},
            new String[]{"protected", "protegido", "PROTECTED"},
            new String[]{"static", "estatico", "STATIC"},
            new String[]{"final", "final", "FINAL"},
            new String[]{"abstract", "abstrato", "ABSTRACT"},
            new String[]{"transient", "transitorio", "TRANSIENT"},
            new String[]{"volatile", "volatil", "VOLATILE"},
            new String[]{"synchronized", "sincronizado", "SYNCHRONIZED"},
            new String[]{"native", "nativo", "NATIVE"},
            new String[]{"default", "padrao", "DEFAULT"},
            new String[]{"override", "sobrescreve", "OVERRIDE"},
            new String[]{"void", "vazio", "VOID"},
            new String[]{"new", "novo", "NEW"},
            new String[]{"this", "este", "THIS"},
            new String[]{"super", "super", "SUPER"},
            new String[]{"return", "retorna", "RETURN"},
            new String[]{"throw", "lanca", "THROW"},
            new String[]{"if", "se", "IF"},
            new String[]{"else", "senao", "ELSE"},
            new String[]{"for", "para", "FOR"},
            new String[]{"while", "enquanto", "WHILE"},
            new String[]{"do", "faca", "DO"},
            new String[]{"switch", "escolha", "SWITCH"},
            new String[]{"case", "caso", "CASE"},
            new String[]{"break", "sair", "BREAK"},
            new String[]{"continue", "segue", "CONTINUE"},
            new String[]{"try", "tenta", "TRY"},
            new String[]{"catch", "pegar", "CATCH"},
            new String[]{"finally", "porFim", "FINALLY"},
            new String[]{"spawn", "gera", "SPAWN"},
            new String[]{"await", "aguarda", "AWAIT"},
            new String[]{"assert", "afirme", "ASSERT"},
            new String[]{"instanceof", "instanciaDe", "INSTANCEOF"},
            new String[]{"var", "var", "VAR"},
            new String[]{"val", "val", "VAL"},
            new String[]{"as", "como", "AS"},
            new String[]{"bool", "logico", "BOOL_TYPE"},
            new String[]{"byte", "byte", "BYTE_TYPE"},
            new String[]{"short", "curto", "SHORT_TYPE"},
            new String[]{"int", "inteiro", "INT_TYPE"},
            new String[]{"long", "longo", "LONG_TYPE"},
            new String[]{"float", "flutuante", "FLOAT_TYPE"},
            new String[]{"double", "duplo", "DOUBLE_TYPE"},
            new String[]{"char", "caractere", "CHAR_TYPE"},
            new String[]{"string", "texto", "STRING_TYPE"},
            new String[]{"true", "verdadeiro", "BOOLEAN_LITERAL_TRUE"},
            new String[]{"false", "falso", "BOOLEAN_LITERAL_FALSE"},
            new String[]{"null", "nulo", "NULL_LITERAL"});

    /** Palavras contextuais: canônico → português (posição do parser, não lexer). */
    private static final Map<String, String> CONTEXTUAL = map(new String[][]{
            {"test", "teste"},
            {"application", "aplicacao"},
            {"infra", "infra"},
            {"using", "usando"},
            {"sealed", "selado"},
            {"foreign", "modulo"},
            {"constructor", "construtor"},
            {"in", "em"},
            {"out", "fora"},
            {"where", "onde"},
            {"orderBy", "ordenarPor"},
            {"asc", "asc"},
            {"desc", "desc"},
            {"limit", "limite"},
    });

    /**
     * AÇÚCAR de fala (ordem da mantenedora 08/10, `D-PORTUKOF-SUGAR`): a
     * superfície de ensino para crianças — `diga`/`diz` são ALIASES extras dos
     * MESMOS builtins (`println`/`print`). A tabela primária continua a
     * BUILTINS (bijetiva, gateada, é a que o hover/formatter/show retornam);
     * o açúcar só ALARGA o domínio fechado superfície→canônico. Sem nova
     * implementação, sem nova palavra inventada: só as duas medidas aqui.
     */
    private static final Map<String, String> SUGAR = map(new String[][]{
            {"println", "diga"},
            {"print", "diz"},
    });

    /** Builtins de chamada nua: canônico → português (medidos no frontend real). */
    private static final Map<String, String> BUILTINS = map(new String[][]{
            {"print", "escreva"},
            {"println", "escrevaln"},
            {"readLine", "leia"},
            {"readFile", "leiarquivo"},
            {"writeFile", "escrevaarquivo"},
            {"listOf", "listaDe"},
            {"mapOf", "mapaDe"},
            {"setOf", "conjuntoDe"},
            {"len", "tamanho"},
            {"now", "agora"},
            {"sleep", "durma"},
            {"channel", "canal"},
            {"spawn", "gera"},
            {"await", "aguarda"},
    });

    /**
     * Namespaces alcançáveis: catálogo stdlib REAL (tabela gerada a partir do
     * `StdCatalog` em execução — `scripts/gen_portukof_aliases.py`) + as
     * bibliotecas-oficiais por pacote (`libs/`). Canônico → português;
     * colisão mantém o canônico (bijetividade travada no gate).
     */
    public static Map<String, String> namespaces() {
        return Namespaces.MAP;
    }

    private static final class Namespaces {
        static final Map<String, String> MAP = build();

        private static Map<String, String> build() {
            Map<String, String> m = new LinkedHashMap<>();
            // libs/pacotes que NÃO são namespaces do catálogo (import por pacote)
            m.put("file", "arquivo");
            m.put("io", "io");
            m.put("fs", "sistema");
            m.put("csv", "csv");
            m.put("ini", "ini");
            m.put("toml", "toml");
            m.put("yaml", "yaml");
            m.put("xml", "xml");
            m.put("pdf", "pdf");
            m.put("game", "jogo");
            m.put("vision", "visao");
            m.put("kofmd", "kofmd");
            m.put("interop", "interoperabilidade");
            m.put("secrets", "segredos");
            m.put("hash", "hash");
            m.put("sign", "assinar");
            m.put("verify", "verificar");
            m.put("key", "chave");
            m.put("password", "senha");
            // catálogo stdlib — a fonte é a tabela gerada (nunca a mão)
            m.putAll(PortuKofStdlibMembers.namespaces());
            return Collections.unmodifiableMap(m);
        }
    }
;

    /** Tabela de keywords do PortuKof: palavra-fonte → TokenType. */
    public static Map<String, TokenType> keywords() {
        return Keywords.MAP;
    }

    /** Vocabulário canônico do Kof (fonte única compartilhada). */
    public static Map<String, TokenType> baseKeywords() {
        return KofKeywords.MAP;
    }

    /** Palavra-fonte → Token.value canônico (o AST recebe sempre o canônico). */
    public static Map<String, String> lexicalCanon() {
        return Canon.MAP;
    }

    /** Canônico → palavra contextual portuguesa (parser via matchesWord). */
    public static Map<String, String> contextualCanonToPortuguese() {
        return ContextualPt.MAP;
    }

    /** Português → canônico contextual. */
    public static Map<String, String> contextualPortugueseToCanon() {
        return ContextualCanon.MAP;
    }

    /** Símbolos localizáveis (builtin + namespaces) — domínio fechado do walker. */
    public static Map<String, String> symbolAliases() {
        return Symbols.MAP;
    }

    /**
     * Aliases de MEMBROS de um namespace canônico (pt → en). Tabela viva por
     * namespace — o gate de paridade exige cobertura total antes do fecho da
     * superfície (`D-PORTUKOF` regra absoluta).
     */
    public static Map<String, String> memberAliases(String canonicalNamespace) {
        return Members.MAP.getOrDefault(canonicalNamespace, Map.of());
    }

    /** namespaces → (membro-pt → membro-canônico) — fonte do gate. */
    public static Map<String, Map<String, String>> allMemberAliases() {
        return Members.MAP;
    }

    /** Nome canônico → entrada (para o gate de bijetividade). */
    public static List<String[]> pairs() {
        return PAIRS;
    }

    public static Map<String, String> builtins() {
        return BUILTINS;
    }

    /** Açúcar de fala (manutenção 08/10): aliases EXTRAS de builtins de chamada nua. */
    public static Map<String, String> builtinsSugar() {
        return SUGAR;
    }

    private static Map<String, String> map(String[][] entries) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String[] e : entries) m.put(e[0], e[1]);
        return Collections.unmodifiableMap(m);
    }

    private static final class Keywords {
        static final Map<String, TokenType> MAP = build();

        private static Map<String, TokenType> build() {
            Map<String, TokenType> m = new LinkedHashMap<>(KofKeywords.MAP);
            for (String[] p : PAIRS) {
                TokenType t = token(p[2]);
                if (!p[1].equals(p[0])) m.remove(p[0]);
                m.put(p[1], t);
            }
            return Collections.unmodifiableMap(m);
        }

        private static TokenType token(String name) {
            if (name.equals("BOOLEAN_LITERAL_TRUE")) return TokenType.BOOLEAN_LITERAL;
            if (name.equals("BOOLEAN_LITERAL_FALSE")) return TokenType.BOOLEAN_LITERAL;
            return TokenType.valueOf(name);
        }
    }

    private static final class Canon {
        static final Map<String, String> MAP = build();

        private static Map<String, String> build() {
            Map<String, String> m = new LinkedHashMap<>();
            for (String[] p : PAIRS) {
                if (!p[0].equals(p[1])) m.put(p[1], p[0]);
            }
            return Collections.unmodifiableMap(m);
        }
    }

    private static final class ContextualPt {
        static final Map<String, String> MAP = CONTEXTUAL;
    }

    private static final class ContextualCanon {
        static final Map<String, String> MAP = invert(CONTEXTUAL);

        private static Map<String, String> invert(Map<String, String> src) {
            Map<String, String> m = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : src.entrySet()) m.put(e.getValue(), e.getKey());
            return Collections.unmodifiableMap(m);
        }
    }

    private static final class Symbols {
        static final Map<String, String> MAP = build();

        private static Map<String, String> build() {
            Map<String, String> m = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : BUILTINS.entrySet()) m.put(e.getValue(), e.getKey());
            // D-PORTUKOF-SUGAR (08/10): `diga`/`diz` ALARGAM o domínio fechado
            // como grafias extras dos MESMOS builtins — nunca substituem a
            // forma primária (BUILTINS é invertível; SUGAR é tabela à parte).
            for (Map.Entry<String, String> e : SUGAR.entrySet()) m.put(e.getValue(), e.getKey());
            for (Map.Entry<String, String> e : namespaces().entrySet()) m.put(e.getValue(), e.getKey());
            m.put("principal", "main");
            return Collections.unmodifiableMap(m);
        }
    }

    private static final class Members {
        /** alias pt-BR → membro canônico, por namespace (tabela do gerador). */
        static final Map<String, Map<String, String>> MAP = invert(PortuKofStdlibMembers.table());

        private static Map<String, Map<String, String>> invert(Map<String, String[][]> table) {
            Map<String, Map<String, String>> out = new LinkedHashMap<>();
            for (Map.Entry<String, String[][]> e : table.entrySet()) {
                Map<String, String> m = new LinkedHashMap<>();
                for (String[] row : e.getValue()) m.put(row[1], row[0]);
                out.put(e.getKey(), Collections.unmodifiableMap(m));
            }
            return Collections.unmodifiableMap(out);
        }
    }
}
