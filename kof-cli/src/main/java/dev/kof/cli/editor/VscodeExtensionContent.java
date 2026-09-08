package dev.kof.cli.editor;

import java.util.List;

/**
 * Conteúdo da extensão VS Code (EDI001 §3/§19). Os comandos da Command
 * Palette delegam à CLI oficial num terminal integrado — o Kof não esconde a
 * CLI (§20) nem reimplementa build/run/format dentro do editor (§15). O LSP é
 * ligado via configuração do cliente (vscode-languageserver-node) apontando
 * para {@code kof lsp}; aqui ficam os comandos + snippets + manifest.
 */
final class VscodeExtensionContent {

    private VscodeExtensionContent() {}

    static List<EditorFile> files(DetectContext ctx, String grammar) {
        return List.of(
            new EditorFile(".vscode/extensions/kof.kof/syntaxes/kof.tmLanguage.json", grammar),
            new EditorFile(".vscode/extensions/kof.kof/language-configuration.json", LANGUAGE_CONFIG),
            new EditorFile(".vscode/extensions/kof.kof/extension.js", kof(ctx, EXTENSION_JS)),
            new EditorFile(".vscode/extensions/kof.kof/snippets/kof.json", SNIPPETS),
            new EditorFile(".vscode/extensions/kof.kof/package.json", PACKAGE_JSON));
    }

    private static String kof(DetectContext ctx, String template) {
        return template.replace("@KOF@", ctx.kofExecutable());
    }

    private static final String LANGUAGE_CONFIG = """
            {
              "comments": { "lineComment": "//", "blockComment": ["/*", "*/"] },
              "brackets": [["{", "}"], ["[", "]"], ["(", ")"]],
              "autoClosingPairs": [
                { "open": "{", "close": "}" },
                { "open": "[", "close": "]" },
                { "open": "(", "close": ")" },
                { "open": "\\"", "close": "\\"", "notIn": ["string"] },
                { "open": "'", "close": "'", "notIn": ["string"] }
              ],
              "surroundingPairs": [["{", "}"], ["[", "]"], ["(", ")"], ["\\"", "\\""], ["'", "'"]]
            }
            """;

    private static final String EXTENSION_JS = """
            // Kof for VS Code — comandos delegam à CLI oficial num terminal
            // integrado. Nenhum build/run/format reimplementado aqui (§15/§20).
            const vscode = require('vscode');

            function kofBin() {
              return vscode.workspace.getConfiguration('kof').get('executable') || '@KOF@';
            }

            function runKof(args) {
              const term = vscode.window.createTerminal({ name: 'Kof' });
              term.show();
              term.sendText([kofBin(), ...args].join(' '), true);
            }

            function activeFile() {
              const ed = vscode.window.activeTextEditor;
              return ed ? ed.document.uri.fsPath : '';
            }

            function activate(context) {
              const cmds = {
                'kof.build': () => runKof(['build', '.']),
                'kof.run': () => runKof(['run', activeFile() || '.']),
                'kof.test': () => runKof(['test', '.']),
                'kof.check': () => runKof(['check', activeFile() || '.']),
                'kof.fmt': () => runKof(['fmt', activeFile() || '.']),
                'kof.serve': () => runKof(['serve', activeFile() || '.']),
                'kof.startLsp': () => runKof(['lsp']),
                'kof.openDocs': () => vscode.env.openExternal(
                  vscode.Uri.parse('https://github.com/KofLang')),
                'kof.selectTarget': async () => {
                  const backend = await vscode.window.showQuickPick(
                    ['jvm', 'native', 'js', 'android'], { placeHolder: 'Kof backend' });
                  if (!backend) return;
                  const cfg = vscode.workspace.getConfiguration('kof');
                  await cfg.update('target', backend, vscode.ConfigurationTarget.Workspace);
                  vscode.window.showInformationMessage('Kof target: ' + backend);
                },
              };
              for (const [id, fn] of Object.entries(cmds)) {
                context.subscriptions.push(vscode.commands.registerCommand(id, fn));
              }
            }

            function deactivate() {}

            module.exports = { activate, deactivate };
            """;

    private static final String SNIPPETS = """
            {
              "main": {
                "prefix": "main",
                "body": ["main() {", "\\t$0", "}"],
                "description": "ponto de entrada"
              },
              "function": {
                "prefix": "fn",
                "body": ["${1:Int} ${2:name}(${3}) {", "\\t$0", "}"],
                "description": "função com tipo de retorno"
              },
              "record": {
                "prefix": "rec",
                "body": ["record ${1:Name}(${2:Int x, Int y})"],
                "description": "dados imutáveis"
              },
              "class": {
                "prefix": "cls",
                "body": ["class ${1:Name} {", "\\t${2:constructor}", "}"],
                "description": "classe com estado mutável"
              },
              "ifelse": {
                "prefix": "ife",
                "body": ["var ${1:x} = if (${2:cond}) ${3:a} else ${4:b}"],
                "description": "if-expression"
              },
              "forin": {
                "prefix": "for",
                "body": ["for (var ${1:item} in ${2:items}) {", "\\t$0", "}"],
                "description": "for-in"
              },
              "switchexpr": {
                "prefix": "sw",
                "body": ["var ${1:desc} = switch (${2:obj}) {", "\\tcase ${3:x} -> ${4:y}", "\\tdefault -> ${5:z}", "}"],
                "description": "switch-expression"
              },
              "spawn": {
                "prefix": "sp",
                "body": ["spawn ${1:work}()"],
                "description": "concorrência (fire-and-forget)"
              },
              "try": {
                "prefix": "try",
                "body": ["try {", "\\t$1", "} catch (String e) {", "\\tprintln(\\"falhou: \\" + e)", "}"],
                "description": "exceção como String"
              }
            }
            """;

    private static final String PACKAGE_JSON = """
            {
              "name": "kof",
              "displayName": "Kof",
              "description": "Kof language support — grammar + comandos CLI + LSP (kof lsp).",
              "version": "0.3.0",
              "publisher": "KofLang",
              "engines": { "vscode": "^1.80.0" },
              "categories": ["Programming Languages", "Snippets"],
              "main": "./extension.js",
              "activationEvents": ["onLanguage:kof"],
              "contributes": {
                "languages": [{
                  "id": "kof",
                  "aliases": ["Kof", "kof"],
                  "extensions": [".kf", ".kof"],
                  "configuration": "./language-configuration.json"
                }],
                "grammars": [{
                  "language": "kof",
                  "scopeName": "source.kof",
                  "path": "./syntaxes/kof.tmLanguage.json"
                }],
                "snippets": [{
                  "language": "kof",
                  "path": "./snippets/kof.json"
                }],
                "configuration": {
                  "title": "Kof",
                  "properties": {
                    "kof.executable": {
                      "type": "string",
                      "default": "kof",
                      "description": "Caminho do executável kof (CLI/LSP)."
                    },
                    "kof.target": {
                      "type": "string",
                      "default": "jvm",
                      "enum": ["jvm", "native", "js", "android"],
                      "description": "Backend alvo dos comandos."
                    }
                  }
                },
                "commands": [
                  { "command": "kof.build", "title": "Kof: Build" },
                  { "command": "kof.run", "title": "Kof: Run" },
                  { "command": "kof.test", "title": "Kof: Test" },
                  { "command": "kof.check", "title": "Kof: Check" },
                  { "command": "kof.fmt", "title": "Kof: Format" },
                  { "command": "kof.serve", "title": "Kof: Serve" },
                  { "command": "kof.startLsp", "title": "Kof: Start LSP" },
                  { "command": "kof.selectTarget", "title": "Kof: Select Target" },
                  { "command": "kof.openDocs", "title": "Kof: Open Documentation" }
                ]
              }
            }
            """;
}
