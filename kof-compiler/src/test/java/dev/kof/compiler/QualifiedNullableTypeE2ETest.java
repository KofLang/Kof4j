package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A nullable type written with its package ({@code profile.ProfileNameStyle?})
 * became a class literally named {@code ProfileNameStyle?}: the record's
 * constructor was declared {@code (String, Lprofile/ProfileNameStyle?;)} while
 * its call sites used {@code Lprofile/ProfileNameStyle;}, so the first
 * construction died with NoSuchMethodError. The {@code ?} is now nullability
 * on the dotted path too.
 */
class QualifiedNullableTypeE2ETest {

    @TempDir Path tmp;

    @Test
    void aQualifiedNullableComponentKeepsItsClassName() throws Exception {
        Path src = tmp.resolve("src");
        Files.createDirectories(src.resolve("profile"));
        Files.createDirectories(src.resolve("network"));
        Files.createDirectories(src.resolve("app"));
        Files.writeString(src.resolve("profile/P.kf"), """
            package profile

            enum ProfileNameStyle { PLAIN, FANCY }
            """);
        Files.writeString(src.resolve("network/N.kf"), """
            package network

            record NetworkUserView(String id, profile.ProfileNameStyle? nameStyle)

            String describe(style: profile.ProfileNameStyle?) {
                if (style == null) { return "none" }
                return "styled"
            }
            """);
        Files.writeString(src.resolve("app/Main.kf"), """
            package app

            import network.*
            import profile.*

            main() {
                val v = NetworkUserView("a", ProfileNameStyle.FANCY)
                println(v.id())
                println(describe(v.nameStyle()))
                println(describe(null))
            }
            """);
        Path out = tmp.resolve("out");
        CompilationResult r = new CompilerDriver().compileSources(List.of(src.resolve("profile/P.kf"),
                src.resolve("network/N.kf"), src.resolve("app/Main.kf")), out, Target.JVM, src);
        assertTrue(r.success(), "must compile: " + r.diagnostics().getDiagnostics());
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true));
        try (URLClassLoader cl = new URLClassLoader(new URL[]{out.toUri().toURL()},
                getClass().getClassLoader())) {
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(old);
        }
        assertEquals("a\nstyled\nnone\n", buf.toString());
    }
}
