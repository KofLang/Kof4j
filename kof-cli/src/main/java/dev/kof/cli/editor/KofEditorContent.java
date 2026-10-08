package dev.kof.cli.editor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Conteúdo idiomático de integração por editor (EDI001, degraus 4-10). Cada
 * método devolve os arquivos que o provider escreve no HOME. Regras:
 * - delega SEMPRE ao tooling oficial (kof lsp / kof fmt / kof build) — nenhum
 *   parser/regra semântica dentro do editor (§15);
 * - reconhece *.kf e *.kof (§16);
 * - a grammar TextMate viaja na distribuição (editor/kof.tmLanguage.json);
 *   quando ausente, um mínimo embutido garante o highlight (§14, sem rede).
 *
 * O executável do Kof é injetado via {@code @KOF@} (replace, não format — os
 * textos contêm muitos {@code %} literais de regex/vim).
 */
public final class KofEditorContent {

    private KofEditorContent() {}

    private static String kof(DetectContext ctx, String template) {
        return template.replace("@KOF@", ctx.kofExecutable());
    }

    /** Grammar TextMate: lê da distribuição se existir, senão mínimo embutido. */
    public static String grammar(DetectContext ctx) {
        Path dir = ctx.installDir();
        if (dir != null) {
            Path g = dir.resolve("editor").resolve("kof.tmLanguage.json");
            if (Files.isRegularFile(g)) {
                try { return Files.readString(g); } catch (IOException ignored) { }
            }
        }
        return MINIMAL_GRAMMAR;
    }

    // ---- Neovim ----------------------------------------------------------

    public static List<EditorFile> neovim(DetectContext ctx) {
        return List.of(
            new EditorFile(".config/nvim/ftdetect/kof.lua",
                "vim.filetype.add({ extension = { kf = 'kof', kof = 'kof' } })\n"),
            new EditorFile(".config/nvim/after/ftplugin/kof.lua", kof(ctx, """
                -- Kof: delegates semantics to the official LSP (@KOF@ lsp). No parser here.
                vim.bo.commentstring = '// %s'
                vim.bo.expandtab = true
                vim.bo.shiftwidth = 4
                vim.api.nvim_create_autocmd('FileType', {
                  pattern = 'kof',
                  callback = function()
                    vim.lsp.start({
                      name = 'kof',
                      cmd = { '@KOF@', 'lsp' },
                      root_dir = vim.fs.dirname(vim.fs.find({ 'kof.toml', '.git' },
                        { upward = true })[1]) or vim.fn.getcwd(),
                    })
                  end,
                })
                """)));
    }

    // ---- Vim -------------------------------------------------------------

    public static List<EditorFile> vim(DetectContext ctx) {
        return List.of(
            new EditorFile(".vim/ftdetect/kof.vim",
                "au BufRead,BufNewFile *.kf,*.kof setfiletype kof\n"),
            new EditorFile(".vim/after/syntax/kof.vim", """
                " Kof syntax (keyword-based; semantics come from kof lsp)
                if exists('b:current_syntax') && b:current_syntax ==# 'kof' | finish | endif
                syntax keyword kofKeyword if else for while do switch case break continue return throw try catch finally instanceof as new this super
                syntax keyword kofDecl class interface record extends implements sealed permits package import public private protected static final abstract var val void constructor
                syntax keyword kofConst true false null
                syntax keyword kofType Bool Byte Short Int Long Float Double Char String List Map Set
                syntax match kofComment "//.*$"
                syntax region kofString start=/"/ skip=/\\\\.\\|\\"/ end=/"/
                syntax match kofNumber "\\<[0-9]\\+\\(\\.[0-9]\\+\\)\\?[lLfFdD]\\?\\>"
                hi def link kofKeyword Keyword
                hi def link kofDecl Keyword
                hi def link kofConst Constant
                hi def link kofType Type
                hi def link kofComment Comment
                hi def link kofString String
                hi def link kofNumber Number
                let b:current_syntax = 'kof'
                """),
            new EditorFile(".vim/after/ftplugin/kof.vim",
                "setlocal commentstring=//\\ %s\nsetlocal expandtab shiftwidth=4\n"),
            new EditorFile(".vim/after/compiler/kof.vim", kof(ctx, """
                if exists('current_compiler') | finish | endif
                let current_compiler = 'kof'
                CompilerSet makeprg=@KOF@\\ build\\ %:h
                CompilerSet errorformat=%f:%l:%c:\\ %m
                """)));
    }

    // ---- Nano ------------------------------------------------------------

    public static List<EditorFile> nano(DetectContext ctx) {
        return List.of(
            new EditorFile(".nano/kof.nanorc", """
                ## Kof — syntax highlighting (proportional to nano; semantics: kof lsp)
                syntax "\\.kof$" "\\.kf$"
                color brightyellow "^(class|interface|record|package|import|constructor)\\>"
                color brightcyan "^(public|private|protected|static|final|abstract|sealed|permits|extends|implements)\\>"
                color brightblue "\\b(if|else|for|while|do|switch|case|break|continue|return|throw|try|catch|finally|instanceof|as|new|this|super)\\b"
                color brightgreen "\\b(var|val|void|Bool|Byte|Short|Int|Long|Float|Double|Char|String|List|Map|Set)\\b"
                color magenta "\\b(true|false|null)\\b"
                color brightred ""(\\.|[^"])*"|'(\\.|[^'])*'"
                color green "//.*$"
                """));
    }

    // ---- Emacs -----------------------------------------------------------

    public static List<EditorFile> emacs(DetectContext ctx) {
        return List.of(
            new EditorFile(".emacs.d/lisp/kof-mode.el", kof(ctx, """
                ;;; kof-mode.el --- Kof major mode (semantics via @KOF@ lsp/eglot)  -*- lexical-binding: t; -*-
                ;;; Commentary:
                ;; Keyword-based highlight; diagnostics/completion/rename come from the
                ;; official LSP (`@KOF@ lsp`) via eglot. No parser here.
                ;;; Code:
                (defvar kof-mode-syntax-table
                  (let ((table (make-syntax-table)))
                    (modify-syntax-entry ?/ ". 124b" table)
                    (modify-syntax-entry ?* ". 23" table)
                    (modify-syntax-entry ?\\n "> b" table)
                    (modify-syntax-entry ?\" "\\"" table)
                    table)
                  "Syntax table for `kof-mode'.")

                (defvar kof-font-lock-keywords
                  '(("\\\\b(class|interface|record|package|import|constructor)\\\\b" . font-lock-keyword-face)
                    ("\\\\b(if|else|for|while|do|switch|case|break|continue|return|throw|try|catch|finally|instanceof|as|new|this|super)\\\\b" . font-lock-keyword-face)
                    ("\\\\b(var|val|void|Bool|Byte|Short|Int|Long|Float|Double|Char|String|List|Map|Set)\\\\b" . font-lock-type-face)
                    ("\\\\b(true|false|null)\\\\b" . font-lock-constant-face))
                  "Keyword highlighting for `kof-mode'.")

                (define-derived-mode kof-mode prog-mode "Kof"
                  "Major mode for Kof. Use `eglot' with `@KOF@ lsp' for LSP features."
                  :syntax-table kof-mode-syntax-table
                  (setq-local comment-start "// ")
                  (setq-local comment-end "")
                  (setq-local font-lock-defaults '(kof-font-lock-keywords)))

                ;;;###autoload
                (add-to-list 'auto-mode-alist '("\\\\.\\(?:kf\\|kof\\)\\'" . kof-mode))

                (provide 'kof-mode)
                ;;; kof-mode.el ends here
                """)));
    }

    // ---- Geany -----------------------------------------------------------

    public static List<EditorFile> geany(DetectContext ctx) {
        return List.of(
            new EditorFile(".config/geany/filedefs/filetypes.kof", kof(ctx, """
                # Kof — Geany filetype (build/run delegate to the CLI; semantics: kof lsp)
                [settings]
                comment_open=/*
                comment_close=*/
                comment_line=//
                extension=.kf
                filetype=Kof
                name=Kof

                [build]
                compiler=@KOF@ build
                linker=
                make=
                execute=@KOF@ run

                [error_messages]
                regex=^(.+):([0-9]+):([0-9]+):[[:space:]](error|warning):(.*)$
                """)));
    }

    // ---- VS Code ---------------------------------------------------------

    public static List<EditorFile> vscode(DetectContext ctx) {
        return VscodeExtensionContent.files(ctx, grammar(ctx));
    }

    // ---- IntelliJ IDEA ---------------------------------------------------
    // Degrau 10 (conteúdo honesto, sem plugin): o plugin oficial é um
    // subprojeto Gradle/Platform à parte (issue #1, plano §21). O provider
    // instala o que delega ao tooling oficial SEM plugin: filetype XML
    // (reconhece *.kf/*.kof), External Tools (kof build/run/test/fmt/check
    // com $FilePath$ — §15: delega, nunca reimplementa), template de
    // file-type + README com o passo LSP4IJ manual (docs/editors/intellij.md
    // é a fonte; aqui vai o essencial p/ uso offline).

    public static List<EditorFile> intellij(DetectContext ctx) {
        return List.of(
            new EditorFile(".config/JetBrains/kof/filetypes/Kof.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <!-- Kof: file type (*.kf/*.kof) — delegates semantics to kof lsp -->
                <filetype name="Kof" implementationClass="com.intellij.openapi.fileTypes.impl.SimpleFileType"
                          fieldName="INSTANCE" language="Kof" extensions="kf;kof;ptkf"
                          description="Kof language source file" />
                """),
            new EditorFile(".config/JetBrains/kof/tools/Kof.xml", kof(ctx, """
                <!-- Kof: External Tools — delegate to the official CLI (@KOF@). See docs/editors/intellij.md for LSP4IJ. -->
                <toolSet name="Kof">
                  <tool name="kof build" program="@KOF@" parameters="build $ProjectFileDir$ --target jvm" />
                  <tool name="kof run" program="@KOF@" parameters="run $FilePath$" />
                  <tool name="kof test" program="@KOF@" parameters="test $ProjectFileDir$" />
                  <tool name="kof fmt" program="@KOF@" parameters="fmt $FilePath$" />
                  <tool name="kof check" program="@KOF@" parameters="check $FilePath$" />
                  <tool name="kof lsp" program="@KOF@" parameters="lsp" />
                </toolSet>
                """)),
            new EditorFile(".config/JetBrains/kof/README.txt", kof(ctx, """
                Kof for IntelliJ IDEA (no plugin — issue #1 tracks the official plugin).
                1. TextMate: Settings > Editor > TextMate Bundles > + > editor/kof.tmLanguage.json.
                2. LSP: plugin LSP4IJ (Marketplace) > Server Mapping: command [@KOF@, lsp], extensions kf/kof.
                3. External Tools: Settings > Tools > External Tools > import .config/JetBrains/kof/tools/Kof.xml.
                4. File type: Kof.xml registers *.kf/*.kof (the official plugin will provide real highlighting).
                """)));
    }

    private static final String MINIMAL_GRAMMAR = """
            {
              "name": "Kof",
              "scopeName": "source.kof",
              "fileTypes": ["kf", "kof"],
              "patterns": [
                { "name": "comment.line.double-slash.kof", "match": "//.*$" },
                { "name": "comment.block.kof", "begin": "/\\\\*", "end": "\\\\*/" },
                { "name": "string.quoted.double.kof", "begin": "\\"", "end": "\\"" },
                { "name": "keyword.control.kof", "match": "\\\\b(if|else|for|while|do|switch|case|break|continue|return|throw|try|catch|finally|instanceof|as|new|this|super)\\\\b" },
                { "name": "keyword.declaration.kof", "match": "\\\\b(class|interface|record|extends|implements|sealed|permits|package|import|public|private|protected|static|final|abstract|var|val|void|constructor)\\\\b" },
                { "name": "constant.language.kof", "match": "\\\\b(true|false|null)\\\\b" },
                { "name": "storage.type.kof", "match": "\\\\b(Bool|Byte|Short|Int|Long|Float|Double|Char|String|List|Map|Set)\\\\b" }
              ]
            }
            """;
}
