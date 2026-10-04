[English](ssh.md) | [Português](ssh.pt_BR.md)

# `kof.ssh` — exec remoto sobre `kof.process`

**Status:** v1 implementado (21/09, `2cd4257a`, plano universal Estágio 2 linha 2.3) ·
**Fonte:** `KofSsh.java` (despacho) + `ExpressionSshCallLowerer` (gates/lowering) ·
**Testes:** `SshE2ETest` (12) + `SshCrossE2ETest` (2) · **Registro de design:**
`docs/architecture/UNIVERSAL-PLATFORM-VISION.md` §6.1 (opção **B** — "sobre
`kof.process`/FFI"; este é o caminho de processo, `libssh` não é necessário no MVP)

## O que é

Um idioma tipado para rodar um comando em um host remoto — montar o argv, rodar,
condicionar ao exit code — **sem nunca transformar um comando em string para
`sh -c`**. É açúcar sobre `kof.process`: o tipo `Result` É o `Result` do
`kof.process` (uma forma, nunca um fork), e `run` rebaixa para a mesma camada de
processo do `shell.run` (`kof_shell_runwith`). Kof não tem backtick nem `$()` — a
forma-função é a resposta.

O `host` é qualquer alvo ssh (`"user@host"`, `"host"`, um alias). A chamada é
**não-interativa e limitada**: BatchMode + timeout de conexão de 5 s, então não
trava num prompt de senha nem pendura para sempre.

## API real (medida no compilador — 0.5.0-beta)

```kof
var r = ssh.run("user@host", "uname -a")     // kof.process.Result
if (ssh.ok(r)) {
    println(r.stdout)
}

var argv = ssh.cmd("user@host", "df -h")     // ["ssh","-o","BatchMode=yes",
                                             //  "-o","ConnectTimeout=5",host,command]
println(argv.get(6))                         // o comando, UM elemento
```

| Chamada | O que faz |
|---|---|
| `ssh.cmd(host, command)` | monta o argv `List<String>` `["ssh","-o","BatchMode=yes","-o","ConnectTimeout=5", host, command]` — sempre uma **lista**, nunca uma string |
| `ssh.run(host, command)` | roda, retorna `kof.process.Result` (`exitCode`/`stdout`/`stderr`) |
| `ssh.ok(result)` | `exitCode == 0` como `Bool` (IR pura de campo/comparação — o `Result` não carrega métodos) |

Uma falha de conexão ou de spawn **não** é exceção: `run` devolve um `Result`
honesto (`exitCode == 255` do ssh, ou `-1` quando o processo não pode ser
iniciado), nunca um throw e nunca um hang.

## A propriedade de segurança (fixada por golden)

`cmdNeverConcatenatesIntoShellString`: o **host e o comando ficam UM elemento de
argv cada**, então um valor com espaços ou metacaracteres
(`ssh.cmd("h; rm -rf /", "echo a b|c && d")`) chega ao `ssh` literal — a classe
de injeção do `sh -c` é estruturalmente impossível nesta API. O `cmd` nunca
concatena numa string de shell.

## Escopo honesto por alvo (R6 — nunca silencioso)

| Face | JVM | JS | Native |
|---|---|---|---|
| `cmd` / `run` / `ok` | ✅ real (rebaixa para `kof_ssh_argv`/`kof_ssh_run` → `kof_process_run`) | ✅ real (paridade byte com JVM) | ✅ real em x86-64 (`RuntimeSsh`) e riscv64/aarch64 (`NativeRiscvAsmSsh`), 26/09 — paridade byte com JVM; só o MCU/riscv32 freestanding mantém `PROC001` |
| membro inválido (`ssh.foo`) | ✅ `SEM025` | — | — |

O Native é real nos alvos hospedeiros x86-64/riscv64/aarch64 (26/09, `SshE2ETest` 12 +
`SshCrossE2ETest` 2); o `PROC001` agora é só o MCU/riscv32 freestanding — a mesma
célula de `kof.process`/`kof.shell` (R7 escopo honesto), nunca um stub silencioso (R6).

## RUIM → BOM

| ❌ RUIM | ✅ BOM | Por quê |
|---|---|---|
| `shell.run("ssh", listOf(host, "sh -c '" + cmd + "'"))` | `ssh.run(host, cmd)` | argv-como-lista mata a classe de injeção |
| `if (r.exitCode == 0) ...` espalhado | `if (ssh.ok(r)) ...` | intenção, não mecanismo |
| montar o argv manual do `ssh` a cada vez | `ssh.cmd(host, command)` | uma forma, testada |
| uma chamada ssh interativa/pendurada | `ssh.run` (BatchMode + ConnectTimeout) | limitada por construção |

## Veja também

- `docs/stdlib/shell.md` — o idioma irmão sobre `kof.process` (mesmo `Result`)
- `docs/architecture/UNIVERSAL-PLATFORM-VISION.md` §6.1 (classificação do SSH)
- `docs/backend-parity.md` — linhas de `kof.ssh` na tabela de namespaces + gaps
