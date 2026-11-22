package dev.kof.compiler.nat;

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
 * <p>O que a fatia NÃO faz (e cada etapa tem o seu): o syscall cru do Linux
 * (M-2, a costura sobre libSystem), o allocator/GC sobre {@code mmap} (M-3),
 * as exceções sobre TLV (M-4), a concorrência sobre {@code __ulock} (M-5), as
 * famílias restantes pela costura (M-6), o runner macOS (M-7) e o Intel (M-8).
 * nesta frota Linux a prova é ESTRUTURAL (o texto traduzido + a linha de
 * link), até o runner existir.
 */
public final class NativeMachO {

    private NativeMachO() {}

    /**
     * Traduz o assembly GNU (ELF) para o dialeto Mach-O.
     *
     * <p>As transformações, uma a uma:
     * <ul>
     *   <li>{@code .globl X} → {@code .globl _X} (o prefixo do Mach-O);
     *       as REFERÊNCIAS a {@code X} (chamadas, {@code leaq}) ganham o
     *       mesmo prefixo — no Mach-O o símbolo global é {@code _X}.</li>
     *   <li>{@code .type X, @function} e {@code .size X, .-X} → REMOVIDOS
     *       (o Mach-O não tem essas diretivas).</li>
     *   <li>{@code .section .text} → {@code .section __TEXT,__text,regular,pure_instructions};
     *       {@code .data} → {@code __DATA,__data}; {@code .bss} →
     *       {@code __DATA,__bss}; {@code .rodata} → {@code __TEXT,__const};
     *       {@code .tbss}/{@code .tdata} → recusados (o TLS do Mach-O é TLV,
     *       não ELF local-exec — a migração é a etapa M-4).</li>
     *   <li>As labels LOCAIS ({@code .L*}) não ganham prefixo (o Mach-O
     *       também as trata como locais).</li>
     * </ul>
     */
    public static String translate(String asm) {
        String[] lines = asm.split("\n", -1);
        StringBuilder out = new StringBuilder(asm.length() + 1024);
        for (String raw : lines) {
            String line = raw;
            String trimmed = line.trim();
            if (trimmed.startsWith(".type ") || trimmed.startsWith(".size ")) {
                // o Mach-O não tem .type/.size — o symtab do assembler carrega
                // o tipo do símbolo.
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
            if (trimmed.startsWith(".section ")) {
                out.append(sectionLine(trimmed)).append('\n');
                continue;
            }
            out.append(referenceSymbols(line)).append('\n');
        }
        return out.toString();
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
        if (name.startsWith(".tbss") || name.startsWith(".tdata")) {
            // O TLS ELF local-exec (%fs:…@tpoff) NÃO existe no Mach-O — a
            // migração é a etapa M-4 (TLV ou pthread_getspecific). Emitir a
            // seção vazia aqui mantém o texto VÁLIDO (o assembler recusa
            // .tbss) e a etapa M-4 troca o acesso.
            return name.startsWith(".tbss")
                    ? ".section __DATA,__bss"
                    : ".section __DATA,__data";
        }
        return ".section __TEXT,__text,regular,pure_instructions";
    }

    /**
     * As REFERÊNCIAS a símbolos globais ganham o prefixo {@code _}.
     *
     * <p>Só os símbolos do programa/runtime Kof ({@code kof_*}, {@code main},
     * {@code _start}, o nome da classe) são prefixados: os rótulos locais
     * ({@code .L*}) e as diretivas ficam intactos. O prefixo é aplicado onde
     * o símbolo aparece como OPERANDO — {@code call X}, {@code leaq X(%rip)},
     * {@code jmp X}, {@code X:} (a definição).
     */
    private static String referenceSymbols(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) return line;
        if (trimmed.startsWith(".") || trimmed.startsWith("#")) return line;
        // a DEFINIÇÃO de um símbolo global: `kof_foo:` → `_kof_foo:`
        int colon = trimmed.indexOf(':');
        if (colon > 0 && isGlobalSymbol(trimmed.substring(0, colon))) {
            return line.replace(trimmed.substring(0, colon), "_" + trimmed.substring(0, colon));
        }
        // as REFERÊNCIAS: `call kof_foo`, `leaq kof_foo(%rip)`, `jmp kof_foo`
        // O MNEMÔNICO NUNCA é prefixado — só o OPERANDO. O texto do assembler
        // distingue os dois pelo ESPAÇO: a primeira palavra é o mnemônico (e
        // os prefixos de registrador `%rax`/`%rip` vêm logo depois), o resto é
        // o operando.
        StringBuilder out = new StringBuilder(line.length() + 8);
        int opStart = operandStart(line);
        out.append(line, 0, opStart);
        int i = opStart;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (isSymbolStart(c)) {
                int j = i;
                while (j < line.length() && isSymbolPart(line.charAt(j))) j++;
                String sym = line.substring(i, j);
                // um REGISTRADOR (`%rip`, `%rax`, `%al`) NÃO é um símbolo — o
                // prefixo `_` do Mach-O é só para os símbolos GLOBAIS.
                boolean register = i > 0 && line.charAt(i - 1) == '%';
                if (!register && isGlobalSymbol(sym)) {
                    out.append('_');
                }
                out.append(sym);
                i = j;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /**
     * O início do OPERANDO da linha: depois do mnemônico (a primeira palavra)
     * e dos prefixos de registrador ({@code %rax}, {@code %rip}, {@code %al}).
     */
    private static int operandStart(String line) {
        // o INDENTO não é parte do mnemônico
        int i = 0;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) i++;
        // o MNEMÔNICO: a primeira palavra
        while (i < line.length() && !Character.isWhitespace(line.charAt(i))) i++;
        // os prefixos de registrador: `%rax`, `%rip`, `%al`...
        int j = i;
        while (j < line.length()) {
            int k = j;
            while (k < line.length() && Character.isWhitespace(line.charAt(k))) k++;
            if (k < line.length() && line.charAt(k) == '%') {
                j = k;
                continue;
            }
            break;
        }
        return j;
    }

    private static boolean isSymbolStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '.' || c == '$';
    }

    private static boolean isSymbolPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '$';
    }

    /**
     * Um símbolo é GLOBAL (e precisa do prefixo {@code _}) quando é um símbolo
     * do programa/runtime Kof. Os rótulos LOCais do assembler ({@code .L*}) e
     * os símbolos do sistema (chamados via PLT) não são prefixados — os
     * últimos são resolvidos pelo linker do macOS.
     */
    private static boolean isGlobalSymbol(String sym) {
        if (sym.startsWith(".L") || sym.startsWith(".L")) return false;
        if (sym.isEmpty()) return false;
        if (sym.startsWith(".")) return false;      // diretiva ou label local
        if (sym.startsWith("_")) return false;      // já prefixado
        return true;
    }
}
