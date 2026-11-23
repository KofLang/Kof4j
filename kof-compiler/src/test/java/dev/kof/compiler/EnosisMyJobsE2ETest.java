package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Énois → desktop (Fase 3): vagas.minhas — as vagas que a pessoa publicou,
 * cadastrou fora e a que se candidatou.
 *
 * <p>Medido em src/routes/vagas.minhas.tsx e no design system: container
 * max-w-3xl space-y-6 (768/24); seções surface-card p-4 sm:p-6 (24) com o título
 * text-xl (28) e o ícone h-5 w-5; cada vaga rounded-xl border border-border-soft
 * p-3 (12 + 2 de borda) com o título text-sm, a linha text-xs e a faixa de ações
 * (mt-2 + Button size="sm" 32); a lista space-y-2 (8). Os textos são os de
 * texts.jobs.
 */
class EnosisMyJobsE2ETest implements LibraryInstallSupport {

    public String libraryName() { return "game"; }

    public List<String> libraryMarkers() { return List.of("Window.kf", "Draw.kf"); }

    private static boolean hostReady() {
        boolean sdl = new File("/usr/lib64/libSDL3.so.0").exists()
                || new File("/usr/lib/x86_64-linux-gnu/libSDL3.so.0").exists();
        boolean ft = new File("/usr/lib64/libfreetype.so.6").exists()
                || new File("/usr/lib/x86_64-linux-gnu/libfreetype.so.6").exists();
        return sdl && ft;
    }

    @Test
    void theMyJobsPagePinsTheMeasuredSectionsAndTexts(@TempDir Path dir) throws Exception {
        assumeTrue(hostReady(), "SDL3 or FreeType absent");
        assumeTrue(NativeToolchainAssumptions.hasTool("as", "ld"), "native toolchain absent");
        Path repo = findLibsRoot().getParent();
        Path shim = repo.resolve("libs/enois/libkof_text.so");
        assumeTrue(Files.exists(shim), "libkof_text.so absent — run scripts/gen-enois-text.sh");

        Path libs = dir.resolve("kof-install/lib/kof-libs");
        copyLibrary(libs);
        for (String lib : new String[]{"image", "enois"}) {
            Path src = repo.resolve("libs/" + lib);
            try (var files = Files.walk(src)) {
                for (Path f : files.filter(Files::isRegularFile).toList()) {
                    Path dst = libs.resolve(lib).resolve(src.relativize(f));
                    Files.createDirectories(dst.getParent());
                    Files.copy(f, dst);
                }
            }
        }
        Path text = libs.resolve("enois/Text.kf");
        Files.writeString(text, Files.readString(text, StandardCharsets.UTF_8)
                .replace("\"@KOF_TEXT_SHIM@\"", "\"" + shim + "\"")
                .replace("\"kof_text_shim\"", "\"" + shim + "\""), StandardCharsets.UTF_8);

        Files.writeString(dir.resolve("Main.kf"), """
                import enois.MyJobs
                import game.Sdl3Backend

                main() {
                    val b = Sdl3Backend("Énóis", 800, 900)
                    b.clearFrame(247, 244, 251)
                    println("back=" + MyJobsScreen.back())
                    println("s1=" + MyJobsScreen.myJobs() + "/" + MyJobsScreen.myJobsEmpty())
                    println("s2=" + MyJobsScreen.externalTitle() + "/" + MyJobsScreen.externalEmpty())
                    println("s3=" + MyJobsScreen.myApplications() + "/" + MyJobsScreen.myApplicationsEmpty())
                    println("open=" + MyJobsScreen.status("OPEN"))
                    println("closed=" + MyJobsScreen.status("CLOSED"))
                    println("c1=" + MyJobsScreen.applicationsCount(1))
                    println("c3=" + MyJobsScreen.applicationsCount(3))
                    println("age0=" + MyJobsScreen.age(0))
                    println("age1=" + MyJobsScreen.age(1))
                    println("age5=" + MyJobsScreen.age(5))
                    println("acts=" + MyJobsScreen.applicationsLink() + "|" + MyJobsScreen.deleteJob()
                        + "|" + MyJobsScreen.visit() + "|" + MyJobsScreen.applied())
                    println("err=" + MyJobsScreen.loadError() + " " + MyJobsScreen.retry())
                    println("page=" + MyJobsMetrics.pageWidth())
                    println("title_h=" + MyJobsScreen.sectionTitleHeight())
                    println("row_h=" + MyJobsScreen.jobRowHeight())
                    println("sec_empty=" + MyJobsScreen.sectionHeight(0, 0))
                    println("sec_2=" + MyJobsScreen.sectionHeight(2, 0))
                    b.close()
                }
                """,
                StandardCharsets.UTF_8);

        String previous = System.getProperty("kof.install.dir");
        System.setProperty("kof.install.dir", dir.resolve("kof-install").toString());
        CompilationResult r;
        try {
            r = new CompilerDriver().compile(dir.resolve("Main.kf"), dir.resolve("out"), Target.NATIVE);
        } finally {
            if (previous == null) System.clearProperty("kof.install.dir");
            else System.setProperty("kof.install.dir", previous);
        }
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());

        var pb = new ProcessBuilder(dir.resolve("out/Default/Main").toString()).redirectErrorStream(true);
        pb.environment().put("SDL_VIDEODRIVER", "dummy");
        pb.environment().put("LD_LIBRARY_PATH", shim.getParent().toString());
        var p = pb.start();
        String out = new String(p.getInputStream().readAllBytes()).strip();
        assertTrue(p.waitFor(240, TimeUnit.SECONDS), "o programa termina");
        assertEquals(0, p.exitValue(), "saida limpa: " + out);

        assertTrue(out.contains("back=Voltar para vagas\n"), "o link: " + out);
        assertTrue(out.contains("s1=Minhas vagas/Você ainda não publicou vagas.\n"), "secao 1: " + out);
        assertTrue(out.contains("s2=Minhas vagas externas/Você ainda não cadastrou vagas externas.\n"), "secao 2: " + out);
        assertTrue(out.contains("s3=Minhas candidaturas/Você ainda não se candidatou a nenhuma vaga.\n"), "secao 3: " + out);
        assertTrue(out.contains("open=Aberta\n"), "aberta: " + out);
        assertTrue(out.contains("closed=Encerrada\n"), "encerrada: " + out);
        assertTrue(out.contains("c1=1 candidatura recebida\n"), "singular: " + out);
        assertTrue(out.contains("c3=3 candidaturas recebidas\n"), "plural: " + out);
        assertTrue(out.contains("age0=Hoje\n"), "hoje: " + out);
        assertTrue(out.contains("age1=Há 1 dia\n"), "um dia: " + out);
        assertTrue(out.contains("age5=Há 5 dias\n"), "cinco dias: " + out);
        assertTrue(out.contains("page=768\n"), "o max-w-3xl: " + out);
        assertTrue(out.contains("title_h=28\n"), "text-xl: " + out);
    }
}
