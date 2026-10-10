package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for the graphics/gaming front slice 3.5a
 * ({@code D-GRAPHICS-GAMING}; the 3D level PROMOTED to the active scope
 * 08/10 by the maintainer): the pure-Kof 3D intent surface —
 * {@code libs/game/Camera3d.kf} + {@code Mesh.kf} + {@code Material.kf} +
 * {@code Light3d.kf}.
 *
 * <p>The plan §9 freezes 3D as intent ({@code camera3d().at().lookAt()},
 * {@code mesh("hero.glb")}) while the view matrix, mesh loading and shading
 * stay backend jobs in later slices. The pure half is the state — camera
 * eye/target/fov/near/far, mesh instance transform, material color/shine/
 * alpha, light position/color/intensity — with the guards throwing on
 * meaningless values. No matrix math (the cross refuses math.sqrt —
 * MATH001), no file IO; honest on every target. Doubles print in
 * milli-units, never raw.
 */
class GameScene3dE2ETest implements LibraryInstallSupport {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    @Override public String libraryName() { return "game"; }

    @Override public java.util.List<String> libraryMarkers() {
        return java.util.List.of("Camera3d.kf", "Mesh.kf", "Material.kf", "Light3d.kf");
    }

    @Test
    void scene3dOnJvm() throws Exception {
        assertEquals(scene3dGolden(), runScene3dJvm(scene3dProbe()));
    }

    @Test
    void scene3dOnScript() throws Exception {
        assertEquals(scene3dGolden(), runScene3dScript(scene3dProbe()));
    }

    @Test
    void scene3dOnNativeX86() throws Exception {
        Assumptions.assumeTrue(NativeToolchainAssumptions.hasTool("as", "ld"),
                "native toolchain absent");
        assertEquals(scene3dGolden(), runScene3dNativeX86(scene3dProbe()));
    }

    @Test
    void scene3dOnJs() throws Exception {
        assertEquals(scene3dGolden(), runScene3dJs(scene3dProbe()));
    }

    @Test
    void scene3dOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(NativeToolchainAssumptions.hasTool(
                        "riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        assertEquals(scene3dGolden(), runScene3dCross("riscv64", Target.NATIVE_RISCV64, scene3dProbe()));
    }

    @Test
    void scene3dOnNativeAarch64() throws Exception {
        Assumptions.assumeTrue(NativeToolchainAssumptions.hasTool(
                        "aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        assertEquals(scene3dGolden(), runScene3dCross("aarch64", Target.NATIVE_AARCH64, scene3dProbe()));
    }

    private static String scene3dProbe() {
        return """
                import game.Camera3d
                import game.Mesh
                import game.Material
                import game.Light3d

                String flag(Bool v) {
                    if (v) { return "true" }
                    return "false"
                }

                main() {
                    var cam = camera3d()
                    cam.at(0.0, 2.0, 5.0).lookAt(0.0, 0.0, 0.0).fov(75.0)
                    println("eye=" + ((cam.eyeX() * 1000.0) as Int) + "," + ((cam.eyeY() * 1000.0) as Int) + "," + ((cam.eyeZ() * 1000.0) as Int))
                    println("tgt=" + ((cam.lookTargetX() * 1000.0) as Int) + "," + ((cam.lookTargetY() * 1000.0) as Int) + "," + ((cam.lookTargetZ() * 1000.0) as Int))
                    println("proj=" + ((cam.fovDegrees() * 1000.0) as Int) + "/" + ((cam.nearPlane() * 1000.0) as Int) + "/" + ((cam.farPlane() * 1000.0) as Int))
                    try {
                        cam.fov(0.0)
                        println("threw=no")
                    } catch (String e) {
                        println("threw=yes")
                    }
                    var hero = mesh("hero.glb", 24, 36)
                    hero.at(1.0, 0.0, 0.0).scale(2.0, 2.0, 2.0).rotate(0.0, 90.0, 0.0)
                    println("mesh=" + hero.vertices() + "v/" + hero.triangles() + "t " + ((hero.posX() * 1000.0) as Int) + "/" + ((hero.scaleOnY() * 1000.0) as Int) + "/" + ((hero.rotationY() * 1000.0) as Int))
                    hero.hide()
                    println("visible=" + flag(hero.isVisible()))
                    var steel = material()
                    steel.color("steel").shininess(64.0).opacity(0.5)
                    println("mat=" + steel.hex() + " " + ((steel.shine() * 1000.0) as Int) + " " + ((steel.alpha() * 1000.0) as Int))
                    var sun = light()
                    sun.at(10.0, 20.0, 30.0).color("warm").intensity(0.8)
                    println("lit=" + ((sun.posY() * 1000.0) as Int) + " " + steel.hex() + " " + sun.hex() + " " + ((sun.power() * 1000.0) as Int))
                    var scene = Scene3d()
                    hero.show()
                    hero.at(2.0, 0.0, 0.0)
                    scene.draw(hero)
                    var ghost = mesh("ghost.glb", 8, 6)
                    ghost.hide()
                    scene.draw(ghost)
                    scene.draw(hero.at(3.0, 0.0, 0.0))
                    println("scene=" + scene.size() + " y0=" + ((scene.commandAt(0).y() * 1000.0) as Int) + " m1=" + scene.commandAt(1).model() + " x1=" + ((scene.commandAt(1).x() * 1000.0) as Int))
                    scene.clear()
                    println("cleared=" + scene.size())
                    try {
                        mesh("bad.glb", 24, 35)
                        println("threw=no")
                    } catch (String e) {
                        println("threw=yes")
                    }
                }
                """;
    }

    private static String scene3dGolden() {
        return """
                eye=0,2000,5000
                tgt=0,0,0
                proj=75000/100/100000
                threw=yes
                mesh=24v/12t 1000/2000/90000
                visible=false
                mat=steel 64000 500
                lit=20000 steel warm 800
                scene=2 y0=0 m1=hero.glb x1=3000
                cleared=0
                threw=yes""";
    }

    private String runScene3dJvm(String code) throws Exception {
        Path root = tmp.resolve("jvm-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Path source = root.resolve("Main.kf");
        Files.writeString(source, code);
        Path out = root.resolve("out");
        CompilationResult result = withScene3dLibrary(root, () -> driver.compile(source, out, Target.JVM));
        assertTrue(result.success(), () -> "compile: " + result.diagnostics().getDiagnostics());
        var stdout = new ByteArrayOutputStream();
        var previousOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(stdout, true, StandardCharsets.UTF_8));
            try (var loader = new URLClassLoader(
                    new java.net.URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
                Class.forName("Default.Main", true, loader)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            }
        } finally {
            System.setOut(previousOut);
        }
        return stdout.toString(StandardCharsets.UTF_8).strip();
    }

    private String runScene3dScript(String code) throws Exception {
        Path root = tmp.resolve("script-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        KofInterpreter.Result result = withScene3dLibrary(root,
                () -> driver.interpret(java.util.List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        return result.stdout().strip();
    }

    private String runScene3dNativeX86(String code) throws Exception {
        Path root = tmp.resolve("native-x86-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        withScene3dLibrary(root, () -> {
            CompilationResult result = driver.compile(root.resolve("Main.kf"), out, Target.NATIVE);
            assertTrue(result.success(),
                    () -> "native compile: " + result.diagnostics().getDiagnostics());
            return null;
        });
        return runScene3dBinary(out.resolve("Default/Main"));
    }

    private String runScene3dJs(String code) throws Exception {
        Path root = tmp.resolve("js-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        CompilationResult result = withScene3dLibrary(root, () -> driver.compile(root.resolve("Main.kf"), out, Target.JS));
        assertTrue(result.success(), () -> "js compile: " + result.diagnostics().getDiagnostics());
        Path jsFile = out.resolve("Default.mjs");
        assertTrue(Files.isRegularFile(jsFile), "generated JS module must exist");
        var stdout = new ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(jsFile, stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, "js output: " + stdout);
        return stdout.toString(StandardCharsets.UTF_8).strip();
    }

    private String runScene3dCross(String arch, Target target, String code) throws Exception {
        Path root = tmp.resolve("cross-" + arch + "-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        withScene3dLibrary(root, () -> {
            CompilationResult result = driver.compile(root.resolve("Main.kf"), out, target);
            assertTrue(result.success(), arch + " compile: " + result.diagnostics().getDiagnostics());
            return null;
        });
        Path binary = out.resolve("Default/Main");
        assertTrue(Files.isRegularFile(binary), arch + " binary must exist");
        ProcessBuilder pb = NativeRiscv64E2ETest.qemu(arch, binary);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, process.waitFor(), arch + " output: " + output);
        return output;
    }

    private String runScene3dBinary(Path binary) throws Exception {
        Process process = new ProcessBuilder(binary.toString())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, process.waitFor(), "native output: " + output);
        return output;
    }

    private <T> T withScene3dLibrary(Path root, Scene3dCheckedSupplier<T> action) throws Exception {
        copyLibrary(root.resolve("kof-install/lib/kof-libs"));
        String previous = System.getProperty("kof.install.dir");
        System.setProperty("kof.install.dir", root.resolve("kof-install").toString());
        try {
            return action.get();
        } finally {
            if (previous == null) System.clearProperty("kof.install.dir");
            else System.setProperty("kof.install.dir", previous);
        }
    }

    @FunctionalInterface
    private interface Scene3dCheckedSupplier<T> {
        T get() throws Exception;
    }
}
