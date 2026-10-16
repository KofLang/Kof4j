[English](kofbol-guide.md) | [Português](kofbol-guide.pt_BR.md)

last: the guide for `kof.legacy` (KofBOL, `D-KOFBOL`) — slices 1–11 landed
doing: slice 11 (docs + guide + the conformance matrix) — THIS document
next: the plan's promotion decision (all §20 slices landed or bounded)
location: docs/kofbol-guide.md
owner: 192.168.100.14:0, flatt CLI, no opencode server on this host; port n/a
state: under-development

intent: kofbol-guide

---

# Modernizing a COBOL system with Kof — the `kof.legacy` guide

**Scope:** this guide documents what `libs/legacy` DOES today (every face has
an executed proof — see `Kofbol*E2ETest`) and what it refuses BY NAME (the
LEG001–LEG019 gap codes). It is not a promise: future capabilities live in
`kofbol-plan.md` §20 with their slices.

## 1. What KofBOL is

KofBOL is the **legacy-banking interoperability layer**: COBOL-shaped data
(fixed-width records, EBCDIC, packed decimal) moves into Kof as plain values,
and Kof data moves back out byte-exact. The COBOL semantics live in pure-Kof
libraries; the application never touches bytes, positions or return-code
numbers by hand (the anti-corruption rule).

```
COBOL copybook → Copybook import → Layout descriptor → decode/encode → your domain
```

## 2. The descriptor model (no new syntax)

KofBOL introduces **no language surface** (rule 6 honored): a record layout is
a plain `Layout` value built from `Field` descriptors.

```kof
import legacy.Legacy

main() {
    var lay = Layout(listOf(textOf("name", 10), digitsOf("amount", 5)))
    var rec = encode(lay, listOf("JOHN", "42"))
    println(rec)                    // JOHN      00042
    var vals = decode(lay, rec)     // the RAW slices, never trimmed silently
}
```

Constructors: `textOf(name, length)` · `digitsOf(name, length)` ·
`decimalOf(name, digits, scale)` (the signed/decimal shape). `fieldPic(name,
pic)` accepts the simple clauses `X`, `9`, `X(n)`, `9(n)`.

## 3. The copybook import

```kof
import legacy.Copybook

var cb = parseCopybookFull(copybookText)
cb.layout          // the Layout (flat 01–49 items with a PIC clause)
cb.conditions      // the 88-level condition names as values
cb.redefines       // the overlay views with their explicit offsets
renderLayout(cb.layout, "customer")     // the generated Kof contract
```

- **OCCURS `<n> [TIMES]`** (elementary items) expands into subscripted names
  `ITEM(1)..ITEM(n)` — byte-exact.
- **`88 <name> VALUE 'lit'.`** becomes a named constant the application
  compares against the decoded field — the condition TEST stays
  application-side.
- **REDEFINES** (elementary) becomes an overlay VIEW: `decodeFrom(view, rec,
  offset)` reads the same bytes through another PIC.
- Refused BY NAME (never silently dropped): group OCCURS, DEPENDING ON,
  REDEFINES of an OCCURS item, REDEFINES + OCCURS, multiple 88 values / THRU,
  the COMP/USAGE attributes (LEG014), malformed lines (LEG015).

The CLI: `kof legacy <copybook> [varName]` prints the generated contract.

## 4. EBCDIC and packed decimal

```kof
var bytes = encode37("HELLO")        // 200 197 211 211 214  (CP037)
var text = decode37(bytes)           // HELLO — round-trip verified
var packed = packComp3("-12.34", 4, 2)   // 01 23 4D
var dec = unpackComp3(packed, 4, 2)      // -12.34
```

- **CP037** (US English) ships with EXPLICIT tables generated from a real
  codec and round-trip-verified over the full printable ASCII range. Other
  code pages are an OPEN decision (the plan's Q5) — one page landed, per the
  slice's own text.
- **COMP-3**: two digits per byte, the sign in the last nibble (C/F/A/E
  positive, D/B negative), an implied decimal point at `scale`, the leading
  zero pad on the TOTAL nibble count. Signs and scale/overflow are fully
  proven.

## 5. Transports, messaging, batch, transactions

```kof
import legacy.Transport
import legacy.Messaging
import legacy.Tx
```

- **The capability matrix** (`capabilityMatrix()`): `file` is WIRED
  end-to-end; batch stdio / tcp / http / mq are DECLARED with what they need
  and refuse LEG018 until a slice wires them. Never a silent fallback.
- **The broker** (`publish`/`receive`): FIFO with correlation ids; an EMPTY
  queue is a **WARNING value** (§10) — the signal is the STATUS, never a
  null, never an exception.
- **The batch** (`batchDecode`): one bad record never kills the batch and is
  never silent — counted with its index in
  `BatchReport(total, ok, failed, failedIndexes)`.
- **The transaction** (`begin`/`stage`/`commit`/`rollback`): all-or-nothing —
  nothing is visible until `commit`; `rollback` returns the base unchanged
  with the discarded count as an explicit value. No distributed protocol is
  invented (§13).

## 6. Return codes and the adapter (the anti-corruption boundary)

```kof
import legacy.Adapter

var r = response(8, "bad")      // LegacyResponse(8, "ERROR", "bad")
```

- **A non-zero legacy RC is DATA, never an exception** (§10) — and never
  swallowed: the band table is explicit (0 OK · 4 WARNING · 8/12 ERROR ·
  >=16 SEVERE; negative refused LEG017).
- **`toMap`/`valuesOf`** convert decoded positions into NAMED bindings and
  back — the application never indexes a legacy field by hand. A missing
  binding is refused LEG016.

## 7. Error codes (LEG001–LEG019)

| Code | Meaning |
|---|---|
| LEG001 | record length mismatch / record too short for the overlay offset |
| LEG002 | non-digit in a numeric field or value |
| LEG003 | value too long for a field |
| LEG004 | value count != field count |
| LEG005 | unsupported PIC clause |
| LEG006 | malformed PIC clause / count |
| LEG007 | byte outside the CP037 text range |
| LEG008 | char outside the CP037 printable range |
| LEG009 | value does not fit (COMP-3 overflow) |
| LEG010 | malformed decimal |
| LEG011 | bad digit nibble / byte out of range |
| LEG012 | bad sign nibble |
| LEG013 | packed width mismatch |
| LEG014 | clause not supported yet (OCCURS on groups, DEPENDING ON, REDEFINES variants, multiple 88 values, COMP/USAGE) |
| LEG015 | malformed copybook line / PIC / OCCURS / 88 |
| LEG016 | missing binding for a field |
| LEG017 | invalid (negative) return code |
| LEG018 | transport not wired yet |
| LEG019 | cannot read the transport target |

## 8. Cross-target and conformance

The portable core (fixed-width, EBCDIC, COMP-3, the adapter, the copybook
parser) is **byte-identical on JVM, Script, JS and Native x86**
(`KofbolParityE2ETest`, 4/4). The ONE known divergence is catalogued OPEN as
**§734** (the JS emitter returns `undefined` for the COMP-3 unpack shape —
JVM/Script/Native correct; owned by the compiler lane) and the JS COMP-3 face
is documented as such in the parity test. The file transport is JVM/Script
(the §11 matrix documents the rest).

## 9. Limitations (explicit, never hidden)

- One EBCDIC code page (CP037); more pages await the plan's Q5 decision.
- No copybook groups/REDEFINES-over-OCCURS/DEPENDING ON (LEG014).
- No COMP/USAGE storage attributes in the import (use `packComp3` directly).
- Transports beyond `file` are declared, not wired (LEG018).
- No distributed transaction protocol (§13).

## 10. Modernization path (§8, the short form)

1. Import the copybooks (`kof legacy`) → the Layout contracts.
2. Decode the legacy files/queues through the layouts — byte-exact, proven.
3. Wrap the domain in the adapter (`toMap`/`valuesOf`) — the COBOL shapes
   stop at the edge.
4. Map the return codes through `response`/`statusOf` — explicitly, in the
   application.
5. Modernize incrementally: each decoded field that becomes a typed Kof value
   is one less legacy shape in the domain.
