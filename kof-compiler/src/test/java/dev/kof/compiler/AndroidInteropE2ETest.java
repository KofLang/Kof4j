package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E do suporte a interop Android/JVM: super.metodo() via INVOKESPECIAL
 * e annotations emitidas no bytecode (RuntimeVisible/Invisible).
 */
class AndroidInteropE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path source, Path outDir, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running JVM class", e);
        }
    }

    @Test
    void superMethodCallInKofHierarchy(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                constructor() {}
                String describe() {
                    return "animal"
                }
            }

            class Dog extends Animal {
                constructor() {
                    super()
                }
                String describe() {
                    return "dog: " + super.describe()
                }
            }

            main() {
                var d = new Dog()
                println(d.describe())
            }
            """);
        runJvm(source, tempDir.resolve("out"), "dog: animal");
    }

    @Test
    void superMethodUsesOverriddenImplementation(@TempDir Path tempDir) throws IOException {
        // super.falar() deve despachar para a implementação da própria
        // classe (não recursão infinita) — semântica do INVOKESPECIAL
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Base {
                constructor() {}
                String falar() {
                    return "base"
                }
                String viaSuper() {
                    return "via: " + falar()
                }
            }

            class Filha extends Base {
                constructor() {
                    super()
                }
                String falar() {
                    return "filha"
                }
            }

            main() {
                var f = new Filha()
                println(f.viaSuper())
                println(f.falar())
            }
            """);
        runJvm(source, tempDir.resolve("out"), "via: filha\nfilha");
    }

    @Test
    void explicitSuperConstructorOnPlainClass(@TempDir Path tempDir) throws IOException {
        // super() explícito numa classe sem extends: Object.<init> válido
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Ponto {
                Int x
                constructor(Int px) {
                    super()
                    x = px
                }
            }

            main() {
                var p = new Ponto(7)
                println(p.x)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "7");
    }

    @Test
    void annotationsEmittedOnClassAndMethod(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            @Deprecated
            class Servico {
                @Override
                String toString() {
                    return "svc"
                }
            }

            main() {
                var s = new Servico()
                println(s)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "svc");

        byte[] bytes = Files.readAllBytes(tempDir.resolve("out").resolve("Servico.class"));
        List<String> visible = new ArrayList<>();
        List<String> invisible = new ArrayList<>();
        collectAnnotations(bytes, visible, invisible);
        assertTrue(visible.contains("java/lang/Deprecated"),
                "@Deprecated deve ser RuntimeVisible: " + visible);
        assertTrue(invisible.contains("java/lang/Override"),
                "@Override deve ser RuntimeInvisible: " + invisible);
    }

    @Test
    void annotationWithValues(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            @Column("user_name")
            class Repo {
                @Max(42)
                Int limite = 0
            }

            main() {
                println(new Repo().limite)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "0");

        byte[] bytes = Files.readAllBytes(tempDir.resolve("out").resolve("Repo.class"));
        ClassReader reader = new ClassReader(bytes);
        List<String> found = new ArrayList<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public org.objectweb.asm.AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                found.add(desc + (visible ? ":visible" : ":invisible"));
                return null;
            }
        }, 0);
        // retenção desconhecida → conservadoramente RuntimeVisible
        assertTrue(found.contains("LColumn;:visible"), "annotation simples no bytecode: " + found);
        assertEquals("user_name",
                readAnnotationValue(bytes, "LColumn;"),
                "valor da forma curta vai para 'value'");
    }

    @Test
    void parameterAnnotationEmitted(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Api {
                Void call(@NonNull name: String) {
                    println("call:" + name)
                }
            }

            main() {
                var api = new Api()
                api.call("kof")
            }
            """);
        runJvm(source, tempDir.resolve("out"), "call:kof");

        byte[] bytes = Files.readAllBytes(tempDir.resolve("out").resolve("Api.class"));
        List<Integer> paramAnnos = new ArrayList<>();
        ClassReader reader = new ClassReader(bytes);
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String desc,
                                                               String signature, String[] exceptions) {
                if ("call".equals(name)) {
                    return new org.objectweb.asm.MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public org.objectweb.asm.AnnotationVisitor visitParameterAnnotation(
                                int parameter, String descriptor, boolean visible) {
                            paramAnnos.add(parameter);
                            return null;
                        }
                    };
                }
                return null;
            }
        }, 0);
        assertFalse(paramAnnos.isEmpty(), "@NonNull deve ser emitido como annotation de parâmetro");
        // JVMS: índice de RuntimeVisibleParameterAnnotations exclui o receiver
        assertEquals(0, paramAnnos.get(0), "annotation no primeiro parâmetro declarado");
    }

    @Test
    void externalSuperclassSignatureFromClasspath(@TempDir Path tempDir) throws IOException, InterruptedException {
        // jar externo com android/view/View.onCreate(Bundle)V — o descritor
        // do INVOKESPECIAL tem que casar exatamente com o declarado lá
        Path classes = tempDir.resolve("cls");
        Files.createDirectories(classes.resolve("android/view"));
        Files.createDirectories(classes.resolve("android/os"));
        Files.writeString(classes.resolve("android/os/Bundle.java"), """
            package android.os;
            public class Bundle { }
            """);
        Files.writeString(classes.resolve("android/view/View.java"), """
            package android.view;
            public class View {
                public void onCreate(android.os.Bundle s) { }
            }
            """);
        // --release 21: bytecode legível pelo ASM embutido do compilador
        // (o android.jar real usa bytecode antigo; JDKs novos emitiriam
        // major version além do suportado)
        ProcessBuilder pb1 = new ProcessBuilder("javac", "--release", "21", "-d", classes.toString(),
                classes.resolve("android/os/Bundle.java").toString(),
                classes.resolve("android/view/View.java").toString());
        pb1.redirectErrorStream(true);
        Process p1 = pb1.start();
        String javacOut = new String(p1.getInputStream().readAllBytes());
        assertEquals(0, p1.waitFor(), "javac do jar fake falhou: " + javacOut);

        Path jar = tempDir.resolve("fake-android.jar");
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (Path cls : Files.walk(classes).filter(f -> f.toString().endsWith(".class")).toList()) {
                z.putNextEntry(new ZipEntry(classes.relativize(cls).toString().replace('\\', '/')));
                z.write(Files.readAllBytes(cls));
            }
        }

        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            import androidx.annotation.NonNull

            class Tela extends android.view.View {
                constructor() {
                    super()
                }

                Void onCreate(android.os.Bundle s) {
                    super.onCreate(s)
                    println("tela criada")
                    return
                }
            }

            main() {
                var t = new Tela()
                t.onCreate(null)
            }
            """);

        CompilerDriver cpDriver = new CompilerDriver();
        cpDriver.setExternalClasspath(List.of(jar));
        CompilationResult result = cpDriver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());

        ProcessBuilder pb2 = new ProcessBuilder("java", "-cp",
                tempDir.resolve("out").toString() + ":" + jar, "Default.Main");
        pb2.redirectErrorStream(true);
        Process p2 = pb2.start();
        String output = new String(p2.getInputStream().readAllBytes()).trim();
        assertEquals(0, p2.waitFor(), "execução falhou: " + output);
        assertEquals("tela criada", output);

        byte[] bytes = Files.readAllBytes(tempDir.resolve("out").resolve("Tela.class"));
        ClassReader reader = new ClassReader(bytes);
        List<String> callSites = new ArrayList<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String desc,
                                                               String signature, String[] exceptions) {
                return new org.objectweb.asm.MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String mName,
                                                String mDesc, boolean isInterface) {
                        if ("onCreate".equals(mName)) {
                            callSites.add(owner + "." + mName + mDesc);
                        }
                    }
                };
            }
        }, 0);
        assertTrue(callSites.contains("android/view/View.onCreate(Landroid/os/Bundle;)V"),
                "INVOKESPECIAL com assinatura real da classe externa: " + callSites);
    }

    @Test
    void androidProjectGeneration(@TempDir Path tempDir) throws IOException {
        // app UI padrão SEM MainActivity declarada: o host embutido
        // (dev/kof/android-host.kf, escrito EM KOF) é compilado junto
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var w = Window("Contador")
                var label = Label("contagem: 0")
                w.bind(label)
                w.show()
            }
            """);

        Path sdkJar = tempDir.resolve("fake-sdk.jar");
        try (InputStream in = AndroidInteropE2ETest.class.getResourceAsStream("/android/fake-sdk.jar")) {
            assertNotNull(in, "fake-sdk.jar deve estar em src/test/resources/android/");
            Files.copy(in, sdkJar);
        }

        CompilerDriver cpDriver = new CompilerDriver();
        cpDriver.setExternalClasspath(List.of(sdkJar));
        CompilationResult result = cpDriver.compile(source, tempDir.resolve("proj"), Target.ANDROID);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());

        Path proj = tempDir.resolve("proj");
        assertTrue(Files.exists(proj.resolve("pom.xml")), "pom.xml do pipeline");
        assertTrue(Files.exists(proj.resolve("src/main/AndroidManifest.xml")));
        assertTrue(Files.exists(proj.resolve("libs/kof-app.jar")), "jar com bytecode Kof");
        assertTrue(Files.exists(proj.resolve("src/main/assets/kof/Default.mjs")), "KofJS p/ WebView");
        assertTrue(Files.exists(proj.resolve("src/main/assets/kof/index.html")));

        // FILOSOFIA: ZERO Java/Kotlin/Gradle no projeto gerado
        try (var walk = Files.walk(proj)) {
            long offenders = walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.endsWith(".java") || n.endsWith(".kt")
                                || n.endsWith(".kts") || n.endsWith(".gradle");
                    }).count();
            assertEquals(0, offenders, "nenhum arquivo Java/Kotlin/Gradle permitido");
        }

        // o host Activity EM KOF está no jar (compilado pelo mesmo frontend)
        try (var zip = new java.util.zip.ZipFile(proj.resolve("libs/kof-app.jar").toFile())) {
            assertNotNull(zip.getEntry("MainActivity.class"),
                    "MainActivity compilada do android-host.kf deve estar no jar");
            assertNotNull(zip.getEntry("Default/Main.class"), "programa no jar");
        }

        // pom é só cola do SDK: NENHUMA <dependencies>
        String pom = Files.readString(proj.resolve("pom.xml"));
        assertFalse(pom.contains("<dependencies>"), "dependências são geridas pelo Kof, não pelo pom");

        // manifest aponta a Activity sintetizada
        String manifest = Files.readString(proj.resolve("src/main/AndroidManifest.xml"));
        assertTrue(manifest.contains(".MainActivity"));
    }

    @Test
    void androidTargetSupportsSpawnAwaitChannel(@TempDir Path tempDir) throws IOException {
        // AND001 fechado 31/08: ART não tem virtual threads, mas o runtime
        // cai para platform threads (Thread.startVirtualThread detectado em
        // runtime) — spawn/await/cancelled/channel/scheduler compilam no Android.
        // (evita `&&` na condição de while: bug pré-existente JS002 do
        // short-circuit no KofJS do WebView, independente da concorrência)
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            Int tarefa() {
                var i = 0
                while (i < 3) {
                    if (cancelled()) {
                        return i
                    }
                    time.sleep(5)
                    i++
                }
                return i
            }

            main() {
                val r = spawn tarefa()
                var v = await r
                println("v=" + v)
                val c = channel<Int>()
                c.send(1)
                c.send(2)
                println("s=" + (c.receive() + c.receive()))
                var id = scheduler.every(50) { println("tick") }
                scheduler.cancel(id)
                println("ok")
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.ANDROID);
        assertTrue(result.success(),
                "concorrência deve compilar no Android (AND001 fechado): " + result.diagnostics().getDiagnostics());
        assertFalse(result.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> "AND001".equals(d.code())), "sem AND001");

        // o bytecode chama o runtime (spawn_result/await/scheduler) e o
        // canal é um LinkedBlockingQueue (java.util.concurrent, existe no ART):
        // send → put, receive → take, channel() → new LinkedBlockingQueue()
        byte[] bytes = Files.readAllBytes(tempDir.resolve("out").resolve("Default").resolve("Main.class"));
        List<String> runtimeCalls = new ArrayList<>();
        List<String> queueOps = new ArrayList<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String desc,
                                                               String signature, String[] exceptions) {
                return new org.objectweb.asm.MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String mName,
                                                String mDesc, boolean isItf) {
                        if (owner.equals("dev/kof/runtime/KofRuntime")) runtimeCalls.add(mName);
                        if (owner.equals("java/util/concurrent/LinkedBlockingQueue")) queueOps.add(mName);
                    }
                };
            }
        }, 0);
        assertTrue(runtimeCalls.contains("kof_spawn_result"), "spawn via runtime: " + runtimeCalls);
        assertTrue(runtimeCalls.contains("kof_await"), "await via runtime: " + runtimeCalls);
        assertTrue(runtimeCalls.contains("kof_scheduler_every"), "scheduler via runtime: " + runtimeCalls);
        assertTrue(queueOps.contains("put") && queueOps.contains("take"),
                "canal via LinkedBlockingQueue (send/receive): " + queueOps);
    }

    @Test
    void samConversionLambdaToExternalInterface(@TempDir Path tempDir) throws IOException {
        // lambda → interface funcional externa: setOnClickListener((v) -> ...)
        // gera classe sintética que IMPLEMENTA OnClickListener
        Path sdkJar = tempDir.resolve("fake-sdk.jar");
        try (InputStream in = AndroidInteropE2ETest.class.getResourceAsStream("/android/fake-sdk.jar")) {
            assertNotNull(in);
            Files.copy(in, sdkJar);
        }
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            import android.widget.Button

            main() {
                var b = new Button(null)
                var total = 42
                b.setOnClickListener((v) -> println("clicou"))
                b.setOnLongClickListener((v, n) -> println("long " + n))
            }
            """);

        CompilerDriver cpDriver = new CompilerDriver();
        cpDriver.setExternalClasspath(List.of(sdkJar));
        CompilationResult result = cpDriver.compile(source, tempDir.resolve("out"), Target.ANDROID);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());

        Path out = tempDir.resolve("out");
        assertTrue(Files.exists(out.resolve("SamOnClickListener_1.class")),
                "adapter SAM para OnClickListener deve ser gerado");
        assertTrue(Files.exists(out.resolve("SamOnLongClickListener_2.class")),
                "adapter SAM para OnLongClickListener deve ser gerado");

        byte[] bytes = Files.readAllBytes(out.resolve("SamOnClickListener_1.class"));
        ClassReader reader = new ClassReader(bytes);
        String[] iface = new String[1];
        List<String> methods = new ArrayList<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int version, int access, String name, String signature,
                              String superName, String[] interfaces) {
                if (interfaces != null && interfaces.length > 0) iface[0] = interfaces[0];
            }

            @Override
            public org.objectweb.asm.MethodVisitor visitMethod(int access, String name,
                                                               String desc, String signature,
                                                               String[] exceptions) {
                methods.add(name + desc);
                return null;
            }
        }, 0);
        assertEquals("android/view/OnClickListener", iface[0],
                "adapter deve IMPLEMENTAR a interface externa");
        assertTrue(methods.contains("onClick(Landroid/view/View;)V"),
                "método SAM com assinatura real: " + methods);

        // o call site passa o adapter onde a interface é esperada
        byte[] mainBytes = Files.readAllBytes(out.resolve("Default").resolve("Main.class"));
        List<String> calls = new ArrayList<>();
        new ClassReader(mainBytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public org.objectweb.asm.MethodVisitor visitMethod(int access, String name,
                                                               String desc, String signature,
                                                               String[] exceptions) {
                return new org.objectweb.asm.MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String mName,
                                                String mDesc, boolean isItf) {
                        if ("setOnClickListener".equals(mName) || "setOnLongClickListener".equals(mName)) {
                            calls.add(mName + mDesc);
                        }
                    }
                };
            }
        }, 0);
        assertTrue(calls.contains("setOnClickListener(Landroid/view/OnClickListener;)V"),
                "dispatch com descritor exato: " + calls);
        assertTrue(calls.contains("setOnLongClickListener(Landroid/view/OnLongClickListener;)V"),
                "dispatch com descritor exato: " + calls);
    }

    @Test
    void superMethodCallOnJsTarget(@TempDir Path tempDir) throws IOException {
        // super.metodo() também no KofJS — dispatch nativo do `super` JS
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                constructor() {}
                String describe() {
                    return "animal"
                }
            }

            class Dog extends Animal {
                constructor() {
                    super()
                }
                String describe() {
                    return "dog: " + super.describe()
                }
            }

            main() {
                var d = new Dog()
                println(d.describe())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JS);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        Path jsFile = tempDir.resolve("out").resolve("Default.mjs");
        assertTrue(Files.exists(jsFile), "Generated JS module should exist");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exitCode = dev.kof.runtime.KofJsRunner.run(jsFile, out,
                new ByteArrayInputStream(new byte[0]), out);
        assertEquals(0, exitCode, "Exit code should be 0, output: '" + out + "'");
        assertEquals("dog: animal", out.toString().trim());
    }

    // ── helpers de inspeção de bytecode ──

    private static void collectAnnotations(byte[] bytes, List<String> visible, List<String> invisible) {
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public org.objectweb.asm.AnnotationVisitor visitAnnotation(String desc, boolean vis) {
                (vis ? visible : invisible).add(desc.substring(1, desc.length() - 1));
                return null;
            }

            @Override
            public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String desc,
                                                               String signature, String[] exceptions) {
                return new org.objectweb.asm.MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public org.objectweb.asm.AnnotationVisitor visitAnnotation(String d, boolean v) {
                        (v ? visible : invisible).add(d.substring(1, d.length() - 1));
                        return null;
                    }
                };
            }
        }, 0);
    }

    private static String readAnnotationValue(byte[] bytes, String expectedDesc) {
        final String[] value = new String[1];
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public org.objectweb.asm.AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                if (!expectedDesc.equals(desc)) return null;
                return new org.objectweb.asm.AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public void visit(String name, Object val) {
                        value[0] = String.valueOf(val);
                    }
                };
            }
        }, 0);
        return value[0];
    }

    // known-bugs #23 — when a superclass of an external class is NOT on the
    // classpath, inherited member resolution used to fail silently (chain
    // truncated). Now a warning is emitted instead.
    @Test
    void missingSuperclassOnClasspathWarns(@TempDir Path tempDir) throws IOException, InterruptedException {
        Path classes = tempDir.resolve("cls");
        Path baseDir = classes.resolve("com/x");
        Files.createDirectories(baseDir);
        // Base NOT included on the classpath on purpose
        Files.writeString(baseDir.resolve("Base.java"), """
            package com.x;
            public class Base {
                public String inherited() { return "x"; }
            }
            """);
        Files.writeString(baseDir.resolve("Sub.java"), """
            package com.x;
            public class Sub extends Base {
            }
            """);
        ProcessBuilder pb = new ProcessBuilder("javac", "--release", "21", "-d", classes.toString(),
                baseDir.resolve("Base.java").toString(), baseDir.resolve("Sub.java").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes());
        assertEquals(0, p.waitFor(), "javac falhou: " + out);

        // classpath só com Sub (Base excluída)
        Path subOnly = tempDir.resolve("subonly");
        Files.createDirectories(subOnly.resolve("com/x"));
        Files.copy(classes.resolve("com/x/Sub.class"), subOnly.resolve("com/x/Sub.class"));

        ExternalClasspath cp = new ExternalClasspath();
        cp.setEntries(java.util.List.of(subOnly));
        assertEquals(0, cp.loadWarnings().size(), "no warning expected before resolve");

        assertNull(cp.resolveMethod("com/x/Sub", "inherited", 0),
                "inherited member cannot resolve without Base");
        assertTrue(cp.loadWarnings().stream()
                        .anyMatch(w -> w.contains("com/x/Base") && w.contains("inherited")),
                "should warn about the missing superclass, got: " + cp.loadWarnings());
    }
}
