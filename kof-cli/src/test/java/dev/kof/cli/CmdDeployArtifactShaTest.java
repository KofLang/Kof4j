package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** issue #978 — o SHA impresso na linha do artefato é o do .tar.gz nomeado
 *  (não o do jar interno); o TrustGate verifica o TARBALL contra esse digest. */
class CmdDeployArtifactShaTest extends CmdDeploySupport {

    // issue #978: a linha "artifact → ...tar.gz (sha256 ...)" mostrava o SHA do
    // JAR interno — o rótulo e o valor se contradiziam na mesma linha, e o
    // atestado de publicação (TrustGate) verifica o TARBALL baixado, não o jar.
    // O SHA impresso/atestado agora é o do .tar.gz nomeado na própria linha.
    @Test
    void artifactLineShaMatchesTheNamedTarGz(@TempDir Path dir) throws Exception {
        Path src = writeApp(dir, "main() { println(\"deploy sha\") }\n");
        CliResult r = run(dir, "deploy", src.toString(), "--target", "jvm",
                "--output", "dist", "--name", "mini", "--version", "0.0.1");
        assertEquals(0, r.exit(), "deploy exit, output:\n" + r.out());

        Path tgz = dir.resolve("dist/deploy/mini-0.0.1.tar.gz");
        assertTrue(Files.isRegularFile(tgz), r.out());
        String printed = r.out().lines()
                .filter(l -> l.startsWith("artifact → "))
                .findFirst().orElse("");
        assertFalse(printed.isEmpty(), "linha do artefato ausente:\n" + r.out());
        int i = printed.indexOf("(sha256 ") + "(sha256 ".length();
        String printedSha = printed.substring(i, printed.indexOf("…", i)); // 12 chars + "…"
        assertEquals(CmdDeploy.sha256Hex(tgz).substring(0, 12), printedSha,
                "o SHA impresso é o do .tar.gz nomeado na MESMA linha (#978)");
    }

}
