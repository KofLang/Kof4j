[English](media.md) | [Português](media.pt_BR.md)

# kof.media — Image/Audio/Video/Mic as media I/O

> **Status: JVM ✅ · Native x86-64: `Video`+`Audio` ✅ (26/09, byte-for-byte
> vs JVM) · riscv64/aarch64: `Video` ✅ (26/09 fatia 2A, byte-for-byte under
> qemu; `Audio` = fatia 2B); `Image`/`Mic` and JS = `MEDIA001` (compile-time,
> honest) — parity ledger row 4.**

| Face | Members (measured) |
|------|--------------------|
| `Image` | `open(path) -> ImageData`, `save`, `saveAs` |
| `ImageData` | `pixels`, `width()`, `height()`, `format()`, `size()`, `dataUri()`, `bytes()`, `bytesAs(...)`, `close()` |
| `Audio` | `openWav(path) -> Audio`, `durationMs()`, `sampleRate()`, `pcmBytes()`, `bytes()`, `saveWav(...)`, `close()` |
| `Video` | `open(path) -> Video`, `durationMs()`, `width()`, `height()`, `close()` |
| `Mic` | `record(Int seconds) -> Audio`, `list() -> List<String>` |

```kf
val img = Image.open("photo.png")     // ImageData (pixels + width/height)
println(img.width().toString() + "x" + img.height().toString())
val take = Mic.record(1)              // Audio — 1s from the default device
val inputs = Mic.list()               // available capture devices
```

- `Image` in [kof.ui](../35-kof-ui.md) is a VIEW widget; `Image.open` here is
  MEDIA I/O — same name, different intent.
- Everything the platform decodes stays in the backend; user code never
  touches buffers or codecs.
- The future graphics/gaming/media surface is Kof's OWN engine with full
  4-target parity as acceptance (`D-GRAPHICS-GAMING`) — `MEDIA001` is honest
  for this legacy face, not the model for what gets promoted.

**See also:** [kof.buffer](buffer.md) — the nominal byte buffer.
