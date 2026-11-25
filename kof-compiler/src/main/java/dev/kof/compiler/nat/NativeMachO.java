package dev.kof.compiler.nat;

import java.util.ArrayList;
import java.util.List;

/**
 * M-1 (#1135, D-NATIVE-MACOS 17/11): o emissor de diretivas MACH-O.
 *
 * <p>O backend nativo emite assembly no dialeto GNU (ELF): {@code .globl},
 * {@code .type X, @function}, {@code .size X, .-X}, {@code .section .text} /
 * {@code .data} / {@code .bss} / {@code .rodata}. O Mach-O quer outra coisa:
 * os globais levam o prefixo {@code _}, as seções são
 * {@code __TEXT,__text} / {@code __DATA,__data} / {@code __DATA,__bss} /
 * {@code __TEXT,__const}, e as diretivas {@code .type}/{@code .size} NÃO
 * existem (o símbolo é descrito no symtab pelo próprio assembler).
 *
 * <p>A tradução é TEXTUAL e determinística (o mesmo passe do
 * {@code NativeCrossSections}), aplicada ao texto do assembly INTEIRO
 * (programa + runtime): é o único ponto que conhece o formato do objeto, e
 * mantém o resto do backend (o IR, a lowering, a costura {@code kof_plat_*})
 * intacto — o macOS é UM PERFIL, não um backend novo (a decisão
 * {@code D-NATIVE-MACOS}).
 *
 * <p><b>A ORACLE DA TRADUÇÃO</b> (medido em 22/11): o assembler Mach-O de
 * verdade — o clang 18 (provisionado por
 * {@code scripts/provision-macho-toolchain.sh}) — aceita o texto traduzido.
 * As regras foram MEDIDAS uma a uma contra ele, não lembradas:
 * <ul>
 *   <li>{@code .type}/{@code .size} → REJEITADOS ("unknown directive") →
 *       saem;</li>
 *   <li>{@code .weak X} → REJEITADO ("unknown directive") → vira
 *       {@code .weak_definition _X} (aceito);</li>
 *   <li>{@code .section .text} → {@code __TEXT,__text,regular,pure_instructions};
 *       {@code .rodata} → {@code __TEXT,__const,regular,pure_instructions};
 *       {@code .data} → {@code __DATA,__data}; {@code .bss} →
 *       {@code __DATA,__bss}; {@code .debug*} → {@code __DWARF,__debug*}
 *       (a seção ELF crua é rejeitada: "unexpected token in '.section'");</li>
 *   <li>os globais e as suas REFERÊNCIAS ganham o prefixo {@code _} (o símbolo
 *       global do Mach-O é {@code _X}) — mas NUNCA um imediato
 *       ({@code $1}, {@code $0x…}, {@code $-1}), NUNCA um registrador
 *       ({@code %rax}, {@code %rip}), NUNCA um mnemônico, NUNCA um rótulo
 *       local ({@code .L*}) e NUNCA o conteúdo de uma string;</li>
 *   <li>o símbolo do sistema ({@code pthread_create}, {@code pow}) é
 *       resolvido pelo linker da libSystem — o prefixo {@code _} do Mach-O é
 *       aplicado também a esses (o símbolo C é {@code _write});</li>
 *   <li>{@code call X@PLT} → {@code call _X} (o Mach-O não tem o modificador
 *       {@code @PLT}: "unsupported symbol modifier in branch relocation");</li>
 *   <li>a face do TLS ELF local-exec ({@code %fs:X@tpoff}) NÃO tem forma
 *       Mach-O equivalente — a migração é a etapa M-4 (TLV ou pthread key).
 *       Aqui a linha é deixada INTACTA e o perfil recusa o programa com o
 *       diagnóstico nomeado (R6) — nunca um binário que falha em runtime.</li>
 * </ul>
 *
 * <p><b>OS DEFEITOS QUE ESTA FASE CORRIGE</b> (medidos em 22/11 no meu
 * próprio M-1, o commit {@code 59edd37a3}, contra o assembly REAL de 132 KB
 * emitido pelo backend — o M-1 só foi provado por 6 testes de texto
 * pequeno, e o texto real não bate):
 * <ol>
 *   <li><b>TRAVA (loop infinito)</b>: a varredura do operando recomeçava no
 *       {@code %} do REGISTRADOR sem nunca consumi-lo — toda linha com
 *       registrador ({@code pushq %rbx}) travava o compilador: um compile
 *       macOS de verdade NUNCA terminaria;</li>
 *   <li><b>IMEDIATOS CORROMPIDOS</b>: o {@code $1} era lido como um símbolo e
 *       virava {@code _$1} — o assembly resultante não assembla ("invalid
 *       operand for instruction");</li>
 *   <li><b>COMENTÁRIO COM DEZ-PONTOS</b>: o detector de rótulo via
 *       {@code :} olhava a linha INTEIRA, então um comentário
 *       ({@code # nota: valor}) fazia a linha ser tratada como definição de
 *       rótulo e o MNEMÔNICO ser prefixado ({@code _movq});</li>
 *   <li><b>REFERÊNCIA DE DADO</b>: o {@code .quad Pair_toString} (a tabela de
 *       métodos) não recebia o prefixo — o símbolo ficaria indefinido no
 *       Mach-O.</li>
 * </ol>
 */
public final class NativeMachO {

    private NativeMachO() {}

    /**
     * Traduz o assembly GNU (ELF) para o dialeto Mach-O.
     *
     * <p>As transformações, uma a uma (as regras foram MEDIDAS contra o
     * assembler Mach-O de verdade — o clang 18 — e não lembradas):
     * <ul>
     *   <li>{@code .globl X} → {@code .globl _X} (o prefixo do Mach-O);
     *       as REFERÊNCIAS a {@code X} (chamadas, {@code leaq}, {@code .quad})
     *       ganham o mesmo prefixo — no Mach-O o símbolo global é {@code _X};</li>
     *   <li>{@code .type X, @function} e {@code .size X, .-X} → REMOVIDOS
     *       (o Mach-O não tem essas diretivas — o clang as rejeita);</li>
     *   <li>{@code .weak X} → {@code .weak_definition _X} (o clang não tem
     *       {@code .weak}; a forma equivalente do Mach-O é esta);</li>
     *   <li>{@code .section .text} → {@code .section __TEXT,__text,regular,pure_instructions};
     *       {@code .rodata} → {@code __TEXT,__const,regular,pure_instructions};
     *       {@code .data} → {@code __DATA,__data}; {@code .bss} →
     *       {@code __DATA,__bss}; {@code .debug_*} → {@code __DWARF,__debug*};</li>
     *   <li>as labels LOCAIS ({@code .L*}) não ganham prefixo (o Mach-O
     *       também as trata como locais);</li>
     *   <li>os OPERANDOS: um imediato ({@code $1}, {@code $0x…}) NUNCA é
     *       símbolo, um registrador ({@code %rax}, {@code %rip}) NUNCA é
     *       símbolo, o mnemônico NUNCA é símbolo e o texto de uma string
     *       NUNCA é símbolo.</li>
     * </ul>
     */
    public static String translate(String asm) {
        String[] lines = asm.split("\n", -1);
        StringBuilder out = new StringBuilder(asm.length() + 1024);
        // a SECCAO corrente (Mach-O) e a pilha do `.pushsection` (ELF) — o
        // Mach-O nao tem `push/popsection`: a entrada vira `.section` e a
        // saida restaura a secao de antes (medido: o clang recusava
        // ".pushsection .rodata" com "unexpected token").
        java.util.ArrayList<String> sections = new java.util.ArrayList<>();
        String currentSection = null;
        for (String raw : lines) {
            String line = raw;
            String trimmed = line.trim();
            if (trimmed.startsWith(".type ") || trimmed.startsWith(".size ")) {
                // o Mach-O não tem .type/.size — o symtab do assembler carrega
                // o tipo do símbolo (o clang: "unknown directive").
                continue;
            }
            if (trimmed.startsWith(".globl ") || trimmed.startsWith(".global ")) {
                String rest = trimmed.substring(trimmed.indexOf(' ') + 1).trim();
                if (!rest.startsWith("_")) {
                    line = line.replace(rest, "_" + rest);
                }
                out.append(line).append('\n');
                continue;
            }
            if (trimmed.startsWith(".weak ")) {
                // o clang não tem `.weak` — a forma do Mach-O é
                // `.weak_definition _X` (medido: aceita).
                String rest = trimmed.substring(".weak ".length()).trim();
                if (!rest.startsWith("_")) rest = "_" + rest;
                out.append(".weak_definition ").append(rest).append('\n');
                continue;
            }
            if (trimmed.startsWith(".pushsection ")) {
                sections.add(currentSection);
                currentSection = sectionLine(".section "
                        + trimmed.substring(".pushsection ".length()).trim());
                out.append(currentSection).append('\n');
                continue;
            }
            if (trimmed.equals(".popsection")) {
                currentSection = sections.isEmpty()
                        ? null : sections.remove(sections.size() - 1);
                out.append(currentSection == null
                        ? sectionLine(".section .text") : currentSection).append('\n');
                continue;
            }
            String gnudirective = gnudirective(trimmed);
            if (gnudirective != null) {
                // uma DIRETIVA GNU que o assembler do Mach-O nao tem
                // (medido: ".hword" -> "unknown directive"). A linha nova
                // passa pelo MESMO caminho de operandos — os simbolos
                // precisam do prefixo (o ".4byte .Lfe_X - X" sem prefixo
                // deixava o assembler recusar a subtracao).
                String indent = line.substring(0, line.length() - trimmed.length());
                out.append(referenceSymbols(indent + gnudirective)).append('\n');
                continue;
            }
            if (trimmed.startsWith(".section ")) {
                currentSection = sectionLine(trimmed);
                out.append(currentSection).append('\n');
                continue;
            }
            out.append(referenceSymbols(line)).append('\n');
        }
        return out.toString();
    }

    /**
     * As DIRETIVAS GNU que o assembler do Mach-O nao tem (medido: o clang
     * recusava ".hword" com "unknown directive"): a palavra da diretiva vira
     * a equivalente, o resto da linha fica verbatim.
     */
    private static String gnudirective(String trimmed) {
        if (trimmed.startsWith(".hword ")) return ".short" + trimmed.substring(".hword".length());
        if (trimmed.startsWith(".2byte ")) return ".short" + trimmed.substring(".2byte".length());
        if (trimmed.startsWith(".4byte ")) return ".long" + trimmed.substring(".4byte".length());
        if (trimmed.startsWith(".8byte ")) return ".quad" + trimmed.substring(".8byte".length());
        if (trimmed.startsWith(".balign ")) {
            return ".align" + alignOperand(trimmed.substring(".balign".length()));
        }
        if (trimmed.startsWith(".align ")) {
            return ".align" + alignOperand(trimmed.substring(".align".length()));
        }
        return null;
    }

    /** A linha {@code .section} GNU → a seção Mach-O correspondente. */

    /**
     * O operando do {@code .align}: o assembler do Mach-O aceita no máximo
     * 16 (medido: 32 e 64 dão "invalid alignment value") — o alinhamento
     * maior é um aviso de performance, não um contrato de layout.
     */
    private static String alignOperand(String rest) {
        String t = rest.trim();
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isDigit(c)) digits.append(c);
            else break;
        }
        if (digits.length() == 0) return " " + t;
        int value;
        try {
            value = Integer.parseInt(digits.toString());
        } catch (NumberFormatException e) {
            return " " + t;
        }
        if (value <= 16) return " " + t;
        return " 16" + t.substring(digits.length());
    }

    /** A linha {@code .section} GNU → a seção Mach-O correspondente. */
    private static String sectionLine(String trimmed) {
        String rest = trimmed.substring(".section ".length()).trim();
        // a forma GNU traz flags depois do nome: `.section .text,"ax",@progbits`
        int comma = rest.indexOf(',');
        String name = (comma >= 0 ? rest.substring(0, comma) : rest).trim();
        if (name.startsWith(".text")) {
            return ".section __TEXT,__text,regular,pure_instructions";
        }
        if (name.startsWith(".rodata")) {
            return ".section __TEXT,__const,regular,pure_instructions";
        }
        if (name.startsWith(".data")) {
            return ".section __DATA,__data";
        }
        if (name.startsWith(".bss")) {
            return ".section __DATA,__bss";
        }
        if (name.startsWith(".debug")) {
            // a DWARF do Mach-O vive em __DWARF (o clang rejeita a seção ELF
            // crua: "unexpected token in '.section' directive").
            return ".section __DWARF," + name.substring(".debug".length());
        }
        if (name.startsWith(".tbss") || name.startsWith(".tdata")) {
            // O TLS ELF local-exec (%fs:…@tpoff) NÃO existe no Mach-O — a
            // migração é a etapa M-4 (TLV ou pthread_getspecific). A seção
            // vira uma seção normal (o texto fica VÁLIDO) e o PERFIL recusa
            // o programa com o diagnóstico nomeado (R6).
            return name.startsWith(".tbss")
                    ? ".section __DATA,__bss"
                    : ".section __DATA,__data";
        }
        return ".section __TEXT,__text,regular,pure_instructions";
    }

    /**
     * As REFERÊNCIAS a símbolos globais ganham o prefixo {@code _}.
     *
     * <p>A varredura é TOKEN a token (o defeito do meu M-1 era tratar
     * caractere a caractere e confundir imediato/registrador/comentário com
     * símbolo):
     * <ul>
     *   <li>o PRIMEIRO token de uma linha de código é o MNEMÔNICO (ou o RÓTULO
     *       de uma definição) — nunca é prefixado como símbolo;</li>
     *   <li>um token começado por {@code %} é um REGISTRADOR — copiado
     *       verbatim;</li>
     *   <li>um token começado por {@code $} é um IMEDIATO — copiado
     *       verbatim;</li>
     *   <li>um token começado por {@code .} é um rótulo LOCAL ou uma
     *       diretiva — copiado verbatim;</li>
     *   <li>o conteúdo entre aspas é DADO — copiado verbatim;</li>
     *   <li>o resto é um símbolo: ganha o prefixo {@code _} se for global.</li>
     * </ul>
     */
    private static String referenceSymbols(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) return line;
        if (trimmed.startsWith(".") && !trimmed.isEmpty()) {
            // uma DIRETIVA com operando símbolo (`.quad Pair_toString`, a
            // tabela de métodos) — o símbolo precisa do prefixo; o resto da
            // diretiva não é tocado.
            return directiveOperands(line);
        }
        // o COMENTÁRIO é o resto da linha a partir de um `#` FORA de aspas —
        // ele nunca é operativa (e um `#` dentro de uma string não abre
        // comentário: o `.asciz "a#b"` é dado).
        int commentAt = commentStart(line);
        String code = (commentAt >= 0) ? line.substring(0, commentAt) : line;
        String comment = (commentAt >= 0) ? machoComment(line.substring(commentAt)) : "";
        // a DEFINIÇÃO de um símbolo global: `kof_foo:` → `_kof_foo:`
        int colon = code.indexOf(':');
        if (colon > 0 && isGlobalSymbol(code.substring(0, colon).trim())
                && code.substring(0, colon).trim().indexOf(' ') < 0) {
            String def = code.substring(0, colon).trim();
            return code.replace(def, "_" + def) + comment;
        }
        return scanOperands(code) + comment;
    }

    /**
     * Uma linha de DIRETIVA: só o operando que é um símbolo global recebe o
     * prefixo ({@code .quad Pair_toString} — a referência de dado que o
     * meu M-1 deixava sem prefixo).
     */
    private static String directiveOperands(String line) {
        StringBuilder out = new StringBuilder(line.length() + 8);
        int i = 0;
        boolean first = true;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '"') {
                int end = stringEnd(line, i);
                out.append(line, i, end);
                i = end;
                continue;
            }
            if (c == '@') {
                // o modificador ELF (`@PLT`, `@function`) — o Mach-O não o
                // tem ("unsupported symbol modifier in branch relocation").
                int j = i + 1;
                while (j < line.length() && isSymbolPart(line.charAt(j))) j++;
                i = j;
                continue;
            }
            if (Character.isDigit(c)) {
                // um NÚMERO (`0x4f0c…`, `16`) — o defeito do meu M-1 lia o
                // 'x' do hex como início de símbolo e prefixava o literal
                // (`0_x4f0cedc95a718dd4` — "unexpected token").
                int j = i;
                while (j < line.length() && isNumberPart(line.charAt(j))) j++;
                out.append(line, i, j);
                i = j;
                continue;
            }
            if (isSymbolStart(c)) {
                int j = i;
                while (j < line.length() && isSymbolPart(line.charAt(j))) j++;
                String token = line.substring(i, j);
                // o PRIMEIRO token da linha é a diretiva (`.quad`) — verbatim;
                // os operandos símbolo ganham o prefixo.
                if (!first && isGlobalSymbol(token)) {
                    out.append('_');
                }
                out.append(token);
                i = j;
                first = false;
                continue;
            }
            if (c == '#') {
                // o COMENTARIO depois da diretiva — no Mach-O a marca e' `//`
                // (o `#` no inicio da linha e' DIRETIVA de pre-processador).
                out.append(machoComment(line.substring(i)));
                break;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /**
     * O COMENTARIO do Mach-O: o {@code #} no inicio da linha e' uma DIRETIVA
     * de pre-processador para o assembler (medido: o comentario "# if V & 1: …"
     * quebrava a montagem com "unterminated conditional directive"). O
     * CONTRATO do M-1 ("o comentario nunca e' tocado") so vale enquanto o
     * assembler aceita: quando o comentario comeca por uma PALAVRA DE
     * DIRETIVA, a marca vira {@code //} e o resto do texto fica verbatim — o
     * texto do comentario e' o que importa.
     */
    private static String machoComment(String text) {
        if (text == null || text.isEmpty() || text.charAt(0) != '#') return text;
        int i = 1;
        // o pre-processador aceita ESPACO entre a marca e a palavra
        // ("# if V & 1" e' um "#if") — a leitura da palavra pula os brancos.
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
        int wordStart = i;
        while (i < text.length() && (Character.isLetterOrDigit(text.charAt(i))
                || text.charAt(i) == '_')) i++;
        String word = text.substring(wordStart, i);
        if (DIRECTIVE_WORDS.contains(word)) return "//" + text.substring(1);
        return text;
    }

    /** As palavras que o assembler do Mach-O le como DIRETIVA apos um {@code #}. */
    private static final java.util.Set<String> DIRECTIVE_WORDS = java.util.Set.of(
            "if", "ifdef", "ifndef", "elif", "else", "endif",
            "include", "define", "undef", "line", "error", "warning", "pragma");

    /**
     * A varredura dos OPERANDOS de uma linha de código: token a token, com
     * o mnemônico, os registradores, os imediatos e as strings copiados
     * verbatim.
     */
    private static String scanOperands(String code) {
        StringBuilder out = new StringBuilder(code.length() + 8);
        int i = 0;
        boolean inMnemonic = true;
        while (i < code.length()) {
            char c = code.charAt(i);
            if (c == '"') {
                int end = stringEnd(code, i);
                out.append(code, i, end);
                i = end;
                continue;
            }
            if (c == '%') {
                // um REGISTRADOR (`%rax`, `%rip`, `%fs`) — verbatim. O '%' é
                // copiado JUNTO com o nome: o defeito do meu M-1 era recomeçar
                // a varredura no '%' sem consumi-lo (loop infinito).
                int j = i + 1;
                while (j < code.length() && isSymbolPart(code.charAt(j))) j++;
                out.append(code, i, j);
                i = j;
                continue;
            }
            if (c == '$') {
                // um IMEDIATO (`$1`, `$0x…`, `$-1`) — verbatim. O defeito do
                // meu M-1 lia o '$' como início de símbolo e prefixava o
                // valor (`_$1`).
                int j = i + 1;
                if (j < code.length() && code.charAt(j) == '\'') {
                    // M-6 (#1135): um literal de CARACTERE (`$'h'`, `$'\n'`) —
                    // verbatim. Sem isto o `'h'` virava operando (o `h` ganhava
                    // o `_` e o clang dizia "single quote way too long"): o
                    // runtime do kof.http COMPARA chars (`cmpb $'h', …`), então
                    // a face apareceu na família de rede.
                    j++;
                    if (j < code.length() && code.charAt(j) == '\\') j++;
                    if (j < code.length()) j++;
                    if (j < code.length() && code.charAt(j) == '\'') j++;
                    out.append(code, i, j);
                    i = j;
                    continue;
                }
                if (j < code.length() && (code.charAt(j) == '-' || code.charAt(j) == '+')) j++;
                while (j < code.length() && isSymbolPart(code.charAt(j))) j++;
                out.append(code, i, j);
                i = j;
                continue;
            }
            if (c == '@') {
                // o modificador ELF (`@PLT`, `@function`) — o Mach-O não o
                // tem ("unsupported symbol modifier in branch relocation").
                int j = i + 1;
                while (j < code.length() && isSymbolPart(code.charAt(j))) j++;
                i = j;
                continue;
            }
            if (Character.isDigit(c)) {
                // um NÚMERO (`0x4f0c…`, `16`) — o defeito do meu M-1 lia o
                // 'x' do hex como início de símbolo e prefixava o literal.
                int j = i;
                while (j < code.length() && isNumberPart(code.charAt(j))) j++;
                out.append(code, i, j);
                i = j;
                continue;
            }
            if (isSymbolStart(c)) {
                int j = i;
                while (j < code.length() && isSymbolPart(code.charAt(j))) j++;
                String token = code.substring(i, j);
                if (inMnemonic) {
                    // o MNEMÔNICO (ou um PREFIXO dele: `lock`, `rep`) — nunca
                    // um símbolo. O defeito do meu M-1 prefixava o mnemônico
                    // depois de um prefixo (`lock _cmpxchg`).
                    inMnemonic = isInstructionPrefix(token);
                } else if (isGlobalSymbol(token)) {
                    out.append('_');
                }
                out.append(token);
                i = j;
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /**
     * Os PREFIXOS de instrução do x86: eles precedem o mnemônico, que
     * continua mnemônico (nenhum dos dois é símbolo).
     */
    private static boolean isInstructionPrefix(String token) {
        return switch (token) {
            case "lock", "rep", "repe", "repz", "repne", "repnz", "bnd", "notrack" -> true;
            default -> false;
        };
    }

    /**
     * O início do COMENTÁRIO da linha ({@code #} fora de aspas), ou -1.
     *
     * <p>Um {@code #} dentro de uma string ({@code .asciz "a#b"}) NÃO abre
     * comentário — o dado é dado.
     */
    static int commentStart(String line) {
        boolean inString = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inString = !inString;
            } else if (c == '#' && !inString) {
                return i;
            }
        }
        return -1;
    }

    /** O índice logo depois da string que começa em {@code from}. */
    private static int stringEnd(String line, int from) {
        int i = from + 1;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == '"') return i + 1;
            i++;
        }
        return line.length();
    }

    private static boolean isSymbolStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '.';
    }

    private static boolean isSymbolPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '.';
    }

    /**
     * As partes de um NÚMERO no assembly: {@code 0x4f0c…} (hex), {@code 16}
     * (decimal), {@code 0b1010}, {@code 0o17} e o sufixo {@code h} do GNU.
     */
    private static boolean isNumberPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == 'x'
                || c == 'X' || c == 'b' || c == 'o' || c == 'h' || c == '+' || c == '-';
    }

    /**
     * Um símbolo é GLOBAL (e precisa do prefixo {@code _}) quando é um símbolo
     * do programa/runtime Kof. Os rótulos LOCAIS do assembler ({@code .L*}) e
     * os símbolos do sistema (chamados via PLT) não são prefixados — os
     * últimos são resolvidos pelo linker do macOS.
     */
    private static boolean isGlobalSymbol(String sym) {
        if (sym.isEmpty()) return false;
        if (sym.startsWith(".L")) return false;     // rótulo local
        if (sym.startsWith(".")) return false;      // diretiva ou label local
        if (sym.startsWith("_")) return false;      // já prefixado
        // um rótulo NUMÉRICO do GNU as ("1:", "2:") é LOCAL: prefixá-lo
        //("_1:") colidia com o outro rótulo de número 1 do arquivo (medido:
        // "symbol '_1' is already defined").
        if (isNumericLabel(sym)) return false;
        return true;
    }

    /** O rótulo é um número (o GNU as os usa como rótulos locais). */
    private static boolean isNumericLabel(String sym) {
        for (int i = 0; i < sym.length(); i++) {
            if (!Character.isDigit(sym.charAt(i))) return false;
        }
        return !sym.isEmpty();
    }

    /**
     * Os SÍTIOS de TLS ELF local-exec ({@code %fs:X@tpoff}) do texto — a face
     * que o Mach-O não tem (a migração é a etapa M-4, TLV ou pthread key).
     */
    public static List<String> elfTlsSites(String asm) {
        return NativeMachOFaces.elfTlsSites(asm);
    }

    /**
     * Os SÍTIOS de SYSCALL CRU (o ABI Linux) do texto — a face que o macOS
     * troca pela costura {@code kof_plat_*} sobre a libSystem (a etapa M-2).
     */
    public static List<String> elfSyscallSites(String asm) {
        return NativeMachOFaces.elfSyscallSites(asm);
    }

    /**
     * Os DONOS dos sítios de syscall (o rótulo que os contém) — a recusa
     * NOMEIA a família que ainda fala o ABI Linux.
     */
    public static List<String> elfSyscallOwners(String asm) {
        return NativeMachOFaces.elfSyscallOwners(asm);
    }
}
