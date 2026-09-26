[English](media.md) | [Português](media.pt_BR.md)

# kof.media — Image/Audio/Video/Mic como I/O de mídia

> **Status: JVM ✅ · Native x86-64: `Video`+`Audio` ✅ (26/09, byte-for-byte
> vs JVM); `Image`/`Mic` e cross/JS = `MEDIA001` (compile-time, honesto) —
> linha 4 do ledger de paridade.**

| Face | Membros (medidos) |
|------|--------------------|
| `Image` | `open(path) -> ImageData`, `save`, `saveAs` |
| `ImageData` | `pixels`, `width()`, `height()`, `format()`, `size()`, `dataUri()`, `bytes()`, `bytesAs(...)`, `close()` |
| `Audio` | `openWav(path) -> Audio`, `durationMs()`, `sampleRate()`, `pcmBytes()`, `bytes()`, `saveWav(...)`, `close()` |
| `Video` | `open(path) -> Video`, `durationMs()`, `width()`, `height()`, `close()` |
| `Mic` | `record(Int seconds) -> Audio`, `list() -> List<String>` |

```kf
val img = Image.open("foto.png")      // ImageData (pixels + width/height)
println(img.width().toString() + "x" + img.height().toString())
val take = Mic.record(1)              // Audio — 1s do dispositivo padrão
val inputs = Mic.list()               // dispositivos de captura disponíveis
```

- `Image` no [kof.ui](../35-kof-ui.pt_BR.md) é um widget de VISUALIZAÇÃO;
  `Image.open` aqui é I/O de MÍDIA — mesmo nome, intenção diferente.
- Tudo que a plataforma decodifica fica no backend; código de usuário nunca
  toca buffers ou codecs.
- A superfície futura de gráficos/jogos/mídia é a engine PRÓPRIA do Kof com
  paridade total em 4 alvos como aceitação (`D-GRAPHICS-GAMING`) — `MEDIA001`
  é honesto para esta face legada, não é o modelo do que será promovido.

**Veja também:** [kof.buffer](buffer.pt_BR.md) — o buffer nominal de bytes.
