package dev.kof.compiler.nat;

import java.util.Set;

/**
 * M-1 (#1135, D-NATIVE-MACOS) — as DIRETIVAS e o léxico do dialeto do
 * assemblador: a tradução das diretivas GNU/ELF para as do Mach-O, o escape
 * do comentário que o pré-processador leria como diretiva, e os conjuntos de
 * nomes que precisam de tratamento especial.
 *
 * <p>Separada da TRADUÇÃO porque são duas responsabilidades: uma percorre o
 * texto e reescreve os símbolos, a outra conhece o dialeto do assemblador.
 */
final class NativeMachODirectives {

    private NativeMachODirectives() {}

    /** A linha {@code .section} GNU → a seção Mach-O correspondente. */
    static String sectionLine(String trimmed) {
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
     * As DIRETIVAS GNU que o assembler do Mach-O nao tem (medido: o clang
     * recusava ".hword" com "unknown directive"): a palavra da diretiva vira
     * a equivalente, o resto da linha fica verbatim.
     */
    static String gnudirective(String trimmed) {
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

    /**
     * O operando do {@code .align}: o assembler do Mach-O aceita no máximo
     * 16 (medido: 32 e 64 dão "invalid alignment value") — o alinhamento
     * maior é um aviso de performance, não um contrato de layout.
     */
    static String alignOperand(String rest) {
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

    /** O {@code .lcomm/.comm sym, size[, align]} GNU -> {@code .zerofill} do Mach-O. */
    static String zeroFill(String trimmed) {
        boolean local = trimmed.startsWith(".lcomm ");
        String rest = trimmed.substring(local ? ".lcomm ".length() : ".comm ".length());
        String[] parts = rest.split(",");
        String sym = parts[0].trim();
        String size = parts.length > 1 ? parts[1].trim() : "0";
        String align = parts.length > 2 ? parts[2].trim() : "3";
        return ".zerofill __DATA,__bss,_" + sym + "," + size + "," + align;
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
    static String machoComment(String text) {
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
        static final java.util.Set<String> DIRECTIVE_WORDS = java.util.Set.of(
            "if", "ifdef", "ifndef", "elif", "else", "endif",
            "include", "define", "undef", "line", "error", "warning", "pragma");

    /** Os nomes C que JA comecam com '_' e precisam do prefixo EXTRA no Mach-O. */
        static final java.util.Set<String> C_LEADING_UNDERSCORE =
            java.util.Set.of("__error");
}
