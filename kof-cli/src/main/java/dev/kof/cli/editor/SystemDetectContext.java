package dev.kof.cli.editor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** {@link DetectContext} real: PATH, HOME e versões da máquina atual. */
final class SystemDetectContext implements DetectContext {

    @Override
    public boolean whichExists(String exe) {
        return whichPath(exe) != null;
    }

    @Override
    public String whichPath(String exe) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isEmpty()) return null;
        String sep = osName().equals("windows") ? ";" : ":";
        String suffix = osName().equals("windows") ? ".exe" : "";
        for (String dir : pathEnv.split(java.util.regex.Pattern.quote(sep))) {
            if (dir.isEmpty()) continue;
            Path cand = Path.of(dir, exe + suffix);
            if (Files.isRegularFile(cand) && Files.isExecutable(cand)) return cand.toString();
            if (osName().equals("windows")) {
                Path cand2 = Path.of(dir, exe + ".cmd");
                if (Files.isRegularFile(cand2)) return cand2.toString();
            }
        }
        return null;
    }

    @Override
    public String readVersion(String exe) {
        String path = whichPath(exe);
        if (path == null) return "unknown";
        try {
            ProcessBuilder pb = new ProcessBuilder(path, "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            return out;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return "unknown";
        }
    }

    @Override
    public boolean dirExists(Path dir) {
        return dir != null && Files.isDirectory(dir);
    }

    @Override
    public boolean fileExists(Path file) {
        return file != null && Files.isRegularFile(file);
    }

    @Override
    public Path home() {
        String h = System.getProperty("user.home");
        return h != null ? Path.of(h) : null;
    }

    @Override
    public List<String> candidateExes() {
        return List.of();
    }

    @Override
    public String osName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) return "windows";
        if (os.contains("mac") || os.contains("darwin")) return "mac";
        if (os.contains("linux")) return "linux";
        return "other";
    }
}
