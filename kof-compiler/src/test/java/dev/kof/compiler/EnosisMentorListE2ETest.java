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
 * Énois → desktop (Fase 3): mentoria — a lista de mentores (o hero já veio em
 * EnosisMentoringE2ETest).
 *
 * <p>Medido em src/routes/mentoria.tsx e no design system: título de seção
 * text-lg (28), descrição text-sm (20), busca (mt-4 16 + 40), grade gap-4 (16) em
 * md:grid-cols-2, cartão surface-card p-5 (20) space-y-4 (16) com avatar h-14
 * (56) e as estatísticas 2x2 (p-3 12). Os textos são os de texts.mentoring, e o
 * formatFaisca é o de src/lib/faisca.ts.
 */
class EnosisMentorListE2ETest implements LibraryInstallSupport {

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
    void theMentorListPinsTheMeasuredCardsAndTheSparksFormat(@TempDir Path dir) throws Exception {
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
                import enois.MentorList
                import game.Sdl3Backend

                main() {
                    val b = Sdl3Backend("Énóis", 1060, 900)
                    b.clearFrame(247, 244, 251)
                    println("feat=" + MentorListScreen.featured())
                    println("featDesc=" + MentorListScreen.featuredDescription())
                    println("search=" + MentorListScreen.searchPlaceholder())
                    println("sEmpty=" + MentorListScreen.searchEmpty())
                    println("err=" + MentorListScreen.loadError() + " / " + MentorListScreen.retry())
                    println("fallback=" + MentorListScreen.mentorFallback())
                    println("sparks=" + MentorListScreen.sparksAccepted("12 faíscas"))
                    println("labels=" + MentorListScreen.rating() + "|" + MentorListScreen.sessions()
                        + "|" + MentorListScreen.investment() + "|" + MentorListScreen.nextSlot())
                    println("noSlot=" + MentorListScreen.noSlot())
                    println("f0=" + MentorListScreen.formatFaisca(0))
                    println("f830=" + MentorListScreen.formatFaisca(830))
                    println("f2000=" + MentorListScreen.formatFaisca(2000))
                    println("f101830=" + MentorListScreen.formatFaisca(101830))
                    println("fNeg=" + MentorListScreen.formatFaisca(-830))
                    println("fNegBig=" + MentorListScreen.formatFaisca(-101830))
                    println("group=" + MentorListScreen.groupThousands(1234567))
                    println("list0=" + MentorListScreen.listBlockHeight(0, 0))
                    println("list2=" + MentorListScreen.listBlockHeight(2, 300))
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

        assertTrue(out.contains("feat=Mentores em destaque\n"), "a secao: " + out);
        assertTrue(out.contains("featDesc=Avaliados pela comunidade nas últimas 8 semanas.\n"), "a descricao: " + out);
        assertTrue(out.contains("search=Buscar mentores por nome, usuário ou especialidade...\n"), "a busca: " + out);
        assertTrue(out.contains("sEmpty=Nenhum mentor encontrado para esta busca.\n"), "o vazio da busca: " + out);
        assertTrue(out.contains("fallback=Mentor\n"), "o nome de reserva: " + out);
        assertTrue(out.contains("sparks=aceita faíscas · 12 faíscas\n"), "a faisca aceita: " + out);
        assertTrue(out.contains("labels=Avaliação|Sessões|Investimento|Próxima vaga\n"), "os rotulos: " + out);
        assertTrue(out.contains("noSlot=Sem horário publicado\n"), "o sem horario: " + out);
        // formatFaisca: o do site
        assertTrue(out.contains("f0=0 faíscas\n"), "zero: " + out);
        assertTrue(out.contains("f830=830 fagulhas\n"), "so fagulhas: " + out);
        assertTrue(out.contains("f2000=2 faíscas\n"), "so faiscas: " + out);
        assertTrue(out.contains("f101830=101 faíscas 830 fagulhas\n"), "as duas partes: " + out);
        assertTrue(out.contains("fNeg=-830 fagulhas\n"), "debito pequeno leva o sinal: " + out);
        assertTrue(out.contains("fNegBig=-101 faíscas 830 fagulhas\n"), "debito grande: " + out);
        assertTrue(out.contains("group=1.234.567\n"), "o milhar com ponto: " + out);
    }
}
