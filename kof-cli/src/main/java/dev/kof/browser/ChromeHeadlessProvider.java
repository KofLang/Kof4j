package dev.kof.browser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * §6.2 provider (first): Chrome/Chromium headless via the CLI the seed
 * `KofJsBrowserE2ETest` proves (`--headless --dump-dom`). Zero external
 * dependency — the browser binary is the only requirement, probed honestly
 * (the PATH sweep plus the standard install paths, `#110`'s macOS bundle
 * included).
 */
public final class ChromeHeadlessProvider implements BrowserProvider {

    private final String chromePath;

    public ChromeHeadlessProvider(String chromePath) {
        this.chromePath = chromePath;
    }

    @Override
    public String name() {
        return "chrome-headless";
    }

    @Override
    public String probe() {
        if (chromePath == null) return null;
        try {
            ProcessBuilder pb = new ProcessBuilder(chromePath, "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean done = p.waitFor(30, TimeUnit.SECONDS);
            if (!done || p.exitValue() != 0) { p.destroyForcibly(); return null; }
            return new String(p.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String dumpDom(String url) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(chromePath,
                "--headless", "--disable-gpu", "--dump-dom", url);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        boolean done = p.waitFor(120, TimeUnit.SECONDS);
        if (!done) { p.destroyForcibly(); throw new IOException("chrome timed out on " + url); }
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (p.exitValue() != 0) {
            throw new IOException("chrome exit " + p.exitValue() + ": " + out);
        }
        return out;
    }

    /** O Chrome/Chromium disponível (PATH + os caminhos padrão + o bundle macOS), ou null. */
    public static ChromeHeadlessProvider find() {
        String p = findChrome();
        return p == null ? null : new ChromeHeadlessProvider(p);
    }

    static String findChrome() {
        List<String> candidates = List.of(
                "google-chrome", "google-chrome-stable", "chromium", "chromium-browser");
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String name : candidates) {
                for (String dir : pathEnv.split(java.util.regex.Pattern.quote(
                        String.valueOf(java.io.File.pathSeparatorChar)))) {
                    Path p = Path.of(dir, name);
                    if (java.nio.file.Files.isExecutable(p)) return p.toString();
                }
            }
        }
        for (String app : List.of(
                "/usr/bin/google-chrome", "/usr/bin/chromium", "/usr/bin/chromium-browser",
                "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                "/Applications/Chromium.app/Contents/MacOS/Chromium")) {
            Path p = Path.of(app);
            if (java.nio.file.Files.isExecutable(p)) return p.toString();
        }
        return null;
    }
}
