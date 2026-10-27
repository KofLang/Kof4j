[English](release-naming.md) | [Português](release-naming.pt_BR.md)

# Release Naming — Kof

**Last updated:** 09/16/2026
**Current version:** 0.6.7

---

## Version format

```text
MAJOR.MINOR.PATCH-<stage>
0.6.0
```

- `MAJOR.MINOR.PATCH` — standard semver (`pom.xml` + `VERSION` file).
- **Stage** — the **phase** (Alpha, Beta, RC, Stable) is a suffix; the release's
  **codename** comes from the list below (future: `1.0.0-chevette`).

## Stages

| Stage | Meaning | Status |
|---------|-------------|--------|
| **Alpha** | Features under construction, breaking changes expected, partial coverage | ✅ completed |
| **Beta** | Feature-complete on the main targets; occasional breaks before 1.0 | ✅ **current** |
| **RC** | Full parity of targets, bug fixes only | future |
| **Stable** | 1.0 — guaranteed compatibility between releases | future | Releases get a codename, in alphabetical order, starting from `Chevette` (e.g.: `1.0.0-chevette`).

---

## Codenames (complete list, in order of use)

Each notable release gets a codename, in alphabetical order — the list is
already defined all at once. When the list runs out, it restarts or extends
(future decision).

| # | Codename | Use |
|---|----------|-----|
| 1 | Alpha | ✅ used (initial phase) |
| 2 | Beta | ✅ used (current phase) |
| 3 | Chevette | first post-Beta release |
| 4 | Diplomata | |
| 5 | Escort | |
| 6 | F-1000 | |
| 7 | Gol | |
| 8 | Hobby | |
| 9 | Idea | |
| 10 | Jeep | |
| 11 | Kadett | |
| 12 | Logus | |
| 13 | Monza | |
| 14 | Niva | |
| 15 | Omega | |
| 16 | Opala | |
| 17 | Parati | |
| 18 | Quantum | |
| 19 | Rekord | |
| 20 | Santana | |
| 21 | Tempra | |
| 22 | Uno | |
| 23 | Verona | |
| 24 | W8 | |
| 25 | XR3 | |
| 26 | Ypsilon | |
| 27 | Zafira | |

---

## Rules

1. **Codename per notable release** — not every patch bump gets a name
   (`0.6.0` → `0.6.0` has no codename; a landmark cut like
   "Phase 6 Router + Phase 9 diffing closed" does).
2. **`Alpha` and `Beta` have already been consumed as a phase**, not as a
   codename for a specific release — the codename list effectively starts at
   `Chevette`.
3. **The order is alphabetical and fixed** — no skipping, no reordering, no
   reserving ("Omega goes to 1.0" does not exist).
4. The codename appears in `VERSION`, in the changelog and in `kof version`
   (e.g.: `kof 1.0.0 (chevette)`).
