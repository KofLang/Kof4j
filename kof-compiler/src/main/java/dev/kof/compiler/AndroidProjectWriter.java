package dev.kof.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * AndroidProjectWriter — Fase 1 do target kof-android (ver
 * docs/targets/KOFANDROID.md): transforma a saída do backend JVM num
 * APK de debug pronto para `mvn verify`.
 *
 * Layout gerado em outputDir:
 *
 *   pom.xml                                              (pipeline aapt2/d8/apksigner)
 *   src/main/AndroidManifest.xml
 *   src/main/assets/kof/                                 (saída KofJS p/ WebView)
 *   libs/kof-app.jar                                     (bytecode: programa + host Activity EM KOF)
 *   README.txt
 *
 * FILOSOFIA: o código do usuário é 100% Kof — inclusive a host
 * Activity (dev/kof/android-host.kf, compilada junto pelo mesmo
 * frontend). ZERO Java, ZERO Kotlin, ZERO Gradle no projeto gerado;
 * dependências são resolvidas pelo Kof (ExternalClasspath), nunca por
 * arquivo de build — o pom é só cola dos binários oficiais do SDK.
 */
public final class AndroidProjectWriter {

    static final String APP_PACKAGE = "dev.kof.app";
    static final String APP_LABEL = "Kof App";
    private static final String BUILD_TOOLS = "34.0.0";
    private static final String API_LEVEL = "34";

    /**
     * Escreve o projeto. As classes JVM já estão em outputDir (o JvmBackend
     * rodou antes); a versão KofJS é emitida aqui para os assets do WebView
     * — a MESMA camada de render widgets→DOM usada no desktop.
     */
    void write(Path outputDir, IRModule module) throws IOException {
        Files.createDirectories(outputDir);

        // 0. metadados derivados DO PROGRAMA: label = título da primeira
        //    Window("..."); permissões = @Permissions([...]) em qualquer classe
        String appLabel = detectAppLabel(module);
        List<String> permissions = detectPermissions(module);

        // 1. assets: mesma IR → KofJS (source maps habilitados para o
        //    chrome://inspect depurar o WebView)
        Path assets = outputDir.resolve("src/main/assets/kof");
        Files.createDirectories(assets);
        new JsBackend().emit(module, assets, true);
        writePlatformBridge(assets);
        patchIndexForPlatform(assets);

        // 2. libs/kof-app.jar: bytecode das classes do programa (+ helpers)
        Path libs = outputDir.resolve("libs");
        Files.createDirectories(libs);
        writeJar(outputDir, libs.resolve("kof-app.jar"));

        // 3. pom.xml (cola do pipeline SDK, sem dependências) + manifesto + ícone
        writePom(outputDir);
        writeManifest(outputDir, appLabel, permissions);
        writeLauncherIcon(outputDir);

        // 4. instruções honestas na raiz — sem mágica
        Files.writeString(outputDir.resolve("README.txt"), """
                Projeto Android gerado pelo Kof (target android).

                Pré-requisitos: JDK 21, Maven, Android SDK com
                build-tools;%1$s e platforms;android-%2$s
                (ANDROID_HOME apontando pro SDK).

                    cd %4$s
                    mvn verify

                O APK de debug fica em target/kof-app.apk — instalar:

                    adb install target/kof-app.apk

                - A UI (kof.ui) renderiza via WebView carregando assets/kof/.
                - O código é 100%% Kof: a host Activity (dev/kof/android-host.kf)
                  foi compilada junto pelo próprio compilador — nada de Java.
                - Dependências são geridas pelo Kof (ExternalClasspath);
                  o pom.xml NÃO declara dependências.
                - Label do app: "%5$s" (primeira Window do programa).
                  Permissões: declare @Permissions(["android.permission.X"])
                  numa classe Kof.
                """.formatted(BUILD_TOOLS, API_LEVEL, APP_PACKAGE, APP_PACKAGE, appLabel));
    }

    /** Primeiro literal String passado a kof_ui_window_new vira o label. */
    private String detectAppLabel(IRModule module) {
        for (IRClass clazz : module.classes()) {
            for (IRMethod m : clazz.methods()) {
                for (IRBasicBlock b : m.basicBlocks()) {
                    List<KofOperation> ops = b.operations();
                    for (int i = 1; i < ops.size(); i++) {
                        if (ops.get(i) instanceof KofCall c
                                && "kof_ui_window_new".equals(c.methodName())
                                && ops.get(i - 1) instanceof KofLoadLiteral lit
                                && lit.value() instanceof String s
                                && !s.isBlank()) {
                            return s;
                        }
                    }
                }
            }
        }
        return APP_LABEL;
    }

    /**
     * @Permissions(["android.permission.INTERNET", ...]) em qualquer classe
     * Kof vira <uses-permission> no manifesto — metadado declarado na
     * intenção, consumido pelo target.
     */
    private List<String> detectPermissions(IRModule module) {
        List<String> out = new java.util.ArrayList<>();
        for (IRClass clazz : module.classes()) {
            for (IRAnnotation anno : clazz.annotations()) {
                if (!anno.name().endsWith("Permissions")) continue;
                Object value = anno.values().get("value");
                if (value instanceof List<?> items) {
                    for (Object item : items) {
                        String perm = String.valueOf(item);
                        if (!out.contains(perm)) out.add(perm);
                    }
                }
            }
        }
        return out;
    }

    /**
     * Ponte kof_platform para o WebView: println sai no logcat/console do
     * WebView; operações de FS respondem com erro claro (não suportado no
     * app — usar interop Android para I/O real). Carregado ANTES do módulo.
     */
    private void writePlatformBridge(Path assetsDir) throws IOException {
        Files.writeString(assetsDir.resolve("kof-platform.js"), """
                // Ponte do target android: kof_platform para o WebView.
                // println → console (visível no logcat); FS não suportado no app.
                globalThis.kof_platform = {
                  print(x) { console.log(String(x)); },
                  args() { return []; },
                  readFileSync(p) { throw new Error("kof.io readFile não disponível no Android (use interop)"); },
                  writeFile(p, c) { throw new Error("kof.io writeFile não disponível no Android (use interop)"); }
                };
                """);
    }

    /** index.html gerado pelo JsBackend: garante kof-platform.js antes do módulo. */
    private void patchIndexForPlatform(Path assetsDir) throws IOException {
        Path html = assetsDir.resolve("index.html");
        if (!Files.exists(html)) return;
        String content = Files.readString(html);
        if (content.contains("kof-platform.js")) return;
        content = content.replace("<script type=\"module\"",
                "<script src=\"kof-platform.js\"></script>\n    <script type=\"module\"");
        Files.writeString(html, content);
    }

    /** Empacota todos os .class sob classesDir no jar (d8 consome direto). */
    private void writeJar(Path classesDir, Path jarFile) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jarFile));
             Stream<Path> walk = Files.walk(classesDir)) {
            for (Path p : walk.filter(f -> f.toString().endsWith(".class")).toList()) {
                String entry = classesDir.relativize(p).toString().replace('\\', '/');
                zip.putNextEntry(new ZipEntry(entry));
                zip.write(Files.readAllBytes(p));
            }
        }
    }

    /**
     * Pipeline inteiro em um pom: nenhum plugin de linguagem, nenhuma
     * DSL — só exec dos binários oficiais do SDK nas fases do Maven.
     */
    private void writePom(Path out) throws IOException {
        String bt = "${env.ANDROID_HOME}/build-tools/" + BUILD_TOOLS;
        String platformJar = "${env.ANDROID_HOME}/platforms/android-" + API_LEVEL + "/android.jar";
        Files.writeString(out.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <!--
                  Projeto Android gerado pelo Kof (target android).
                  Nenhum código do usuário vive aqui: as classes Kof estão em
                  libs/kof-app.jar e a UI roda nos assets KofJS do WebView.
                  Pipeline: d8 (dex) → aapt2 (apk) → zipalign → apksigner.
                -->
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>%s</groupId>
                  <artifactId>kof-app</artifactId>
                  <version>0.0.14-alpha</version>
                  <packaging>pom</packaging>

                  <properties>
                    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                    <kof.build-tools>%s</kof.build-tools>
                    <kof.platform.jar>%s</kof.platform.jar>
                    <kof.debug.keystore>${project.build.directory}/debug.keystore</kof.debug.keystore>
                  </properties>

                  <build>
                    <finalName>kof-app</finalName>
                    <plugins>
                      <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-antrun-plugin</artifactId>
                        <version>3.1.0</version>
                        <executions>
                          <!-- host Activity contra o android.jar -->
                          <execution>
                            <id>compile-host</id>
                            <phase>compile</phase>
                            <goals><goal>run</goal></goals>
                            <configuration>
                              <target name="compile-host">
                                <mkdir dir="${project.build.directory}/host-classes"/>
                                <javac release="21" encoding="UTF-8"
                                       srcdir="src/main/java"
                                       destdir="${project.build.directory}/host-classes"
                                       classpath="${kof.platform.jar}"
                                       includeantruntime="false"/>
                                <jar destfile="${project.build.directory}/kof-host.jar"
                                     basedir="${project.build.directory}/host-classes"/>
                              </target>
                            </configuration>
                          </execution>

                          <!-- dex das classes Kof + host -->
                          <execution>
                            <id>dex</id>
                            <phase>package</phase>
                            <goals><goal>run</goal></goals>
                            <configuration>
                              <target name="dex">
                                <mkdir dir="${project.build.directory}/apk"/>
                                <exec executable="${kof.build-tools}/d8" failonerror="true">
                                  <arg value="--release"/>
                                  <arg value="--lib"/><arg value="${kof.platform.jar}"/>
                                  <arg value="--min-api"/><arg value="24"/>
                                  <arg value="--output"/><arg value="${project.build.directory}/apk"/>
                                  <arg value="libs/kof-app.jar"/>
                                  <arg value="${project.build.directory}/kof-host.jar"/>
                                </exec>
                              </target>
                            </configuration>
                          </execution>

                          <!-- apk: manifesto + assets + classes.dex -->
                          <execution>
                            <id>package-apk</id>
                            <phase>package</phase>
                            <goals><goal>run</goal></goals>
                            <configuration>
                              <target name="package-apk">
                                <mkdir dir="${project.build.directory}/apk"/>
                                <exec executable="${kof.build-tools}/aapt2" failonerror="true">
                                  <arg value="compile"/><arg value="--dir"/>
                                  <arg value="src/main/res"/>
                                  <arg value="-o"/><arg value="${project.build.directory}/apk/res.zip"/>
                                </exec>
                                <exec executable="${kof.build-tools}/aapt2" failonerror="true">
                                  <arg value="link"/>
                                  <arg value="-o"/><arg value="${project.build.directory}/apk/base.apk"/>
                                  <arg value="-I"/><arg value="${kof.platform.jar}"/>
                                  <arg value="--manifest"/><arg value="src/main/AndroidManifest.xml"/>
                                  <arg value="-A"/><arg value="src/main/assets"/>
                                  <arg value="-R"/><arg value="${project.build.directory}/apk/res.zip"/>
                                </exec>
                                <zip destfile="${project.build.directory}/apk/base.apk" update="true">
                                  <zipfileset file="${project.build.directory}/apk/classes.dex"
                                              fullpath="classes.dex"/>
                                </zip>
                              </target>
                            </configuration>
                          </execution>

                          <!-- alinhamento + assinatura de debug -->
                          <execution>
                            <id>align-sign</id>
                            <phase>verify</phase>
                            <goals><goal>run</goal></goals>
                            <configuration>
                              <target name="align-sign">
                                <exec executable="${kof.build-tools}/zipalign" failonerror="true">
                                  <arg value="-f"/><arg value="4"/>
                                  <arg value="${project.build.directory}/apk/base.apk"/>
                                  <arg value="${project.build.directory}/apk/aligned.apk"/>
                                </exec>
                                <condition property="keystore.ok">
                                  <available file="${kof.debug.keystore}"/>
                                </condition>
                                <sequential unless:set="keystore.ok">
                                  <exec executable="keytool" failonerror="true">
                                    <arg value="-genkeypair"/>
                                    <arg value="-keystore"/><arg value="${kof.debug.keystore}"/>
                                    <arg value="-alias"/><arg value="androiddebugkey"/>
                                    <arg value="-storepass"/><arg value="android"/>
                                    <arg value="-keypass"/><arg value="android"/>
                                    <arg value="-keyalg"/><arg value="RSA"/>
                                    <arg value="-validity"/><arg value="9999"/>
                                    <arg value="-dname"/><arg value="CN=Kof Debug,O=Kof,C=BR"/>
                                  </exec>
                                </sequential>
                                <exec executable="${kof.build-tools}/apksigner" failonerror="true">
                                  <arg value="sign"/>
                                  <arg value="--ks"/><arg value="${kof.debug.keystore}"/>
                                  <arg value="--ks-pass"/><arg value="pass:android"/>
                                  <arg value="--out"/><arg value="${project.build.directory}/kof-app.apk"/>
                                  <arg value="${project.build.directory}/apk/aligned.apk"/>
                                </exec>
                              </target>
                            </configuration>
                          </execution>

                        </executions>
                      </plugin>
                    </plugins>
                  </build>
                </profile-placeholder>
                """.formatted(APP_PACKAGE, bt, platformJar)
                        .replace("</profile-placeholder>", "</project>"));
    }

    private void writeManifest(Path out, String appLabel, List<String> permissions) throws IOException {
        Path manifest = out.resolve("src/main/AndroidManifest.xml");
        Files.createDirectories(manifest.getParent());
        StringBuilder permLines = new StringBuilder();
        for (String perm : permissions) {
            permLines.append("    <uses-permission android:name=\"").append(perm).append("\" />\n");
        }
        Files.writeString(manifest, """
                <?xml version="1.0" encoding="utf-8"?>
                <!-- Manifesto = dados da plataforma; o CÓDIGO (host Activity)
                     é Kof compilado em libs/kof-app.jar -->
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="%s">

                    %s<application
                        android:label="%s"
                        android:icon="@drawable/ic_launcher_kof"
                        android:theme="@android:style/Theme.Material.Light.NoActionBar">
                        <activity
                            android:name=".MainActivity"
                            android:exported="true"
                            android:configChanges="orientation|screenSize|keyboardHidden">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>

                </manifest>
                """.formatted(APP_PACKAGE, permLines.toString(), appLabel));
    }

    /** Ícone vetorial do Kof — sem binário PNG no projeto gerado. */
    private void writeLauncherIcon(Path out) throws IOException {
        Path icon = out.resolve("src/main/res/drawable/ic_launcher_kof.xml");
        Files.createDirectories(icon.getParent());
        Files.writeString(icon, """
                <vector xmlns:android="http://schemas.android.com/apk/res/android"
                    android:width="108dp"
                    android:height="108dp"
                    android:viewportWidth="108"
                    android:viewportHeight="108">
                    <path android:fillColor="#6F4E37" android:pathData="M0,0h108v108h-108z"/>
                    <path android:fillColor="#FFF8F0"
                          android:pathData="M36,28h11v52h-11z M52,28h13l16,26 -16,26h-13l15,-26z"/>
                </vector>
                """);
    }

}
