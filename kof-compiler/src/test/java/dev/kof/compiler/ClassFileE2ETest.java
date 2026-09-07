package dev.kof.compiler;

import dev.kof.compiler.parser.ClassFileParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.kof.compiler.parser.ClassFileParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E do ClassFileParser — parser de bytecode JVM.
 */
class ClassFileE2ETest {

    @Test
    void parseSimpleClass(@TempDir Path tempDir) throws IOException, InterruptedException {
        Path javaFile = tempDir.resolve("Simple.java");
        Files.writeString(javaFile, """
                public class Simple {
                    int x;
                    String name;
                    
                    public Simple() {
                        this.x = 0;
                        this.name = "default";
                    }
                    
                    public int getValue() {
                        return this.x;
                    }
                    
                    public void setValue(int v) {
                        this.x = v;
                    }
                }
                """);

        Path classFile = tempDir.resolve("Simple.class");
        runJavac(javaFile, classFile);

        var ir = ClassFileParser.parse(Files.newInputStream(classFile));

        assertEquals(0xCAFEBABE, ir.magic);
        assertEquals("Simple", ir.thisClass);

        assertTrue(ir.methods.stream().anyMatch(m -> m.name.equals("getValue")));
        assertTrue(ir.methods.stream().anyMatch(m -> m.name.equals("setValue")));
        assertTrue(ir.fields.stream().anyMatch(f -> f.name.equals("x")));
        assertTrue(ir.fields.stream().anyMatch(f -> f.name.equals("name")));
    }

    @Test
    void parseWithMain(@TempDir Path tempDir) throws IOException, InterruptedException {
        Path javaFile = tempDir.resolve("Main.java");
        Files.writeString(javaFile, """
                public class Main {
                    public static void main(String[] args) {
                        System.out.println("Hello");
                    }
                }
                """);

        Path classFile = tempDir.resolve("Main.class");
        runJavac(javaFile, classFile);

        var ir = ClassFileParser.parse(Files.newInputStream(classFile));

        assertEquals("Main", ir.thisClass);

        var mainMethod = ir.methods.stream()
            .filter(m -> m.name.equals("main"))
            .findFirst()
            .orElseThrow();

        assertEquals("([Ljava/lang/String;)V", mainMethod.descriptor);
    }

    private void runJavac(Path javaFile, Path classFile) throws IOException, InterruptedException {
        String javaHome = System.getProperty("java.home");
        Path javac = Path.of(javaHome, "bin", "javac");
        
        ProcessBuilder pb = new ProcessBuilder(javac.toString(), javaFile.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        int rc = p.waitFor();
        if (rc != 0) {
            throw new IOException("javac failed with code " + rc + 
                ": " + new String(p.getInputStream().readAllBytes()));
        }
    }
}
