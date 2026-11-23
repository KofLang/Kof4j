# §1207 replication — the Énois skeleton rejected only through the harness install

`ComunidadesEsqueleto.kf` (copied verbatim from the app round 23/11) has
`import game.Sdl3Backend`, a sibling `import enois.ComunidadesGrade` and three
`Sdl3Backend` parameters. Run through the Énois `DesktopHarness` (solo entry
`src/Main.kf` importing it, `kof-libs` installed from the repo) it is rejected
with `SEM011 Undefined variable or type: 'Sdl3Backend'`; the same file next to
the same libraries in a hand-made install, with the same compiler jar, compiles.

To reproduce: put this file in the app's `libs/`, write
`test/java/dev/enois/desktop/EnoisComunidadesEsqueletoHarnessE2ETest.java` with a
`main` that only imports `enois.ComunidadesEsqueleto`, and run
`mvn -o test -Dtest=EnoisComunidadesEsqueletoHarnessE2ETest`.
