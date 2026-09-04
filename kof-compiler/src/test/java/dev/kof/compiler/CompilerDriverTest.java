package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompilerDriverTest {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void compilesRecordToJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Point.kf");
        Files.writeString(source, "record Point(int x, int y)");
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Compilation should succeed");
        assertTrue(Files.exists(tempDir.resolve("out/Point.class")), "Class file should exist");
    }

    @Test
    void compilesRecordToNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Point.kf");
        Files.writeString(source, "record Point(int x, int y)");
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Native compilation should succeed");
    }

    @Test
    void compilesFunctionWithPrintln(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                println("Hello, Kof!")
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Compilation should succeed");
    }

    @Test
    void compilesFunctionWithVariables(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var nome = "Mel"
                var idade = 26
                println(nome)
                println(idade)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Compilation should succeed");
    }

    @Test
    void compilesPackageAndImport(@TempDir Path tempDir) throws IOException {
        // PKG004: o pacote deve corresponder ao diretório do arquivo
        Path pkgDir = tempDir.resolve("com").resolve("example");
        Files.createDirectories(pkgDir);
        Path source = pkgDir.resolve("Main.kf");
        Files.writeString(source, """
            package com.example

            import java.util.ArrayList

            main() {
                println("Package and import work!")
            }
            """);
        // módulo raiz = diretório que contém com/ (PKG004: pacote = diretório)
        CompilationResult result = driver.compileSources(java.util.List.of(source),
                tempDir.resolve("out"), Target.JVM, tempDir);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void compilesWithoutSemicolons(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                println("No semicolons!")
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Compilation should succeed without semicolons");
    }

    @Test
    void failsOnInvalidSyntax(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Bad.kf");
        Files.writeString(source, "class {{{ invalid");
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(result.success(), "Compilation should fail on invalid syntax");
        assertTrue(result.diagnostics().hasErrors(), "Should have error diagnostics");
    }

    // known-bugs #25 — literal Long fora do range dava NumberFormatException
    // crua (crash do compilador); agora é diagnóstico limpo PARSE084
    @Test
    void outOfRangeLongLiteralGivesCleanDiagnostic(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Bad.kf");
        Files.writeString(source, """
            main() {
                var big = 9223372036854775808
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(result.success(), "Out-of-range Long literal should fail to compile");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("PARSE084"), "Should be a clean diagnostic, was: " + diags);
        assertFalse(diags.contains("NumberFormatException"), "Must not crash, was: " + diags);
    }

    // known-bugs #1 — `throw <não-String>` gerava bytecode inválido no JVM.
    // Exceções são Strings em Kof: rejeita em compile-time (SEM026), inclusive
    // dentro de try (que antes nem passava pela análise semântica).
    @Test
    void throwNonStringGivesCleanDiagnostic(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Bad.kf");
        Files.writeString(source, """
            main() {
                try { throw 42 } catch (String e) { println("ok") }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(result.success(), "throw <Int> should fail to compile");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("SEM026"), "Should be a clean diagnostic, was: " + diags);
        assertFalse(diags.contains("NumberFormatException"), "Must not crash, was: " + diags);
    }

    // known-bugs #17 — array has no get()/set() methods (API is arr[i]); the
    // compiler used to accept them and emit broken bytecode (ClassFormatError
    // JVM / undefined reference Native). Now a clean SEM028.
    @Test
    void arrayMethodCallGivesCleanDiagnostic(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Bad.kf");
        Files.writeString(source, """
            main() {
                var arr = new Int[3]
                arr.set(0, 5)
                println(arr.get(0))
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(result.success(), "arr.get()/set() should fail to compile");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("SEM028"), "Should be a clean diagnostic, was: " + diags);
    }

    // known-bugs #12 — `var c = a = b` (assignment as an expression VALUE)
    // produced invalid bytecode. Kof has no assignment-expression: reject with
    // SEM027. Statement `a = b` must keep working.
    @Test
    void chainedAssignmentRejectedAsExpression(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Bad.kf");
        Files.writeString(source, """
            main() {
                var a = 1
                var b = 2
                var c = a = b
                println(c)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(result.success(), "Assignment as expression should fail to compile");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("SEM027"), "Should be a clean diagnostic, was: " + diags);
    }

    // known-bugs #16 — List.toArray() (unsupported/undocumented) produced
    // invalid bytecode on JVM and undefined references on Native. Now a clean
    // SEM029; Java interop methods like stream() must keep working.
    @Test
    void toArrayOnCollectionGivesCleanDiagnostic(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Bad.kf");
        Files.writeString(source, """
            main() {
                var arr = listOf(1, 2, 3).toArray()
                println(arr.length)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(result.success(), "toArray should fail to compile");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("SEM029"), "Should be a clean diagnostic, was: " + diags);
    }

    @Test
    void failsOnTypeMismatchAssignment(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Bad.kf");
        Files.writeString(source, """
            main() {
                var x = 1
                x = "hello"
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(result.success(), "Assigning String to Int should fail");
    }

    @Test
    void failsOnWrongReturnType(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Bad.kf");
        Files.writeString(source, """
            f(): Int {
                return "x"
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(result.success(), "Returning String from Int function should fail");
    }

    @Test
    void failsOnWrongArgCount(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Bad.kf");
        Files.writeString(source, """
            add(Int a, Int b): Int { return a + b }
            main() { add(1) }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(result.success(), "Calling add with 1 arg should fail");
    }

    @Test
    void failsOnWrongArgType(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Bad.kf");
        Files.writeString(source, """
            greet(String s): String { return s }
            main() { greet(42) }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(result.success(), "Passing Int to String param should fail");
    }

    @Test
    void failsOnUndefinedVariable(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Bad.kf");
        Files.writeString(source, "main() { println(undefinedVar) }");
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(result.success(), "Undefined variable should fail");
    }

    @Test
    void failsOnUndefinedFunction(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Bad.kf");
        Files.writeString(source, "main() { nope() }");
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(result.success(), "Undefined function should fail");
    }

    @Test
    void compilesClassWithFieldsAndMethods(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            public class User {
                String name
                public getName(): String {
                    return name
                }
                public constructor(String name) {
                    this.name = name
                }
            }
            main() {
                var user = new User("Mel")
                println(user.getName())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Compilation should succeed");
        assertTrue(Files.exists(tempDir.resolve("out/User.class")), "User class should exist");
        assertTrue(Files.exists(tempDir.resolve("out/Default/Main.class")), "Main class should exist");
    }

    @Test
    void compilesClassWithExpressionBodyMethod(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            public class Calculator {
                public add(Int a, Int b): Int = a + b
            }
            main() {
                var c = new Calculator()
                println(c.add(2, 3))
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Compilation should succeed");
        assertTrue(Files.exists(tempDir.resolve("out/Calculator.class")), "Calculator class should exist");
    }

    @Test
    void compilesRecordInstantiation(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            record Point(Int x, Int y)
            main() {
                var p = Point(10, 20)
                println(p.x())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Compilation should succeed");
        assertTrue(Files.exists(tempDir.resolve("out/Point.class")), "Point class should exist");
    }

    @Test
    void compilesClassWithNestedScopes(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var x = 10
                if (x > 5) {
                    var y = 20
                    println(y)
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Compilation should succeed");
    }

    @Test
    void compilesForLoop(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                for (var i = 0; i < 5; i++) {
                    println(i)
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Compilation should succeed");
    }

    @Test
    void compilesWhileLoop(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var i = 0
                while (i < 5) {
                    println(i)
                    i = i + 1
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Compilation should succeed");
    }

    @Test
    void compilesIfElse(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var x = 10
                if (x > 5) {
                    println("greater")
                } else {
                    println("smaller")
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Compilation should succeed");
    }

    @Test
    void compilesArithmetic(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var x = 10
                var y = 20
                println(x + y)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Compilation should succeed");
    }

    @Test
    void compilesClassWithDefaultConstructor(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            public class Empty {
                public getValue(): Int = 42
            }
            main() {
                var e = new Empty()
                println(e.getValue())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Compilation should succeed");
    }



    @Test
    void irNodesHasNoAsmDependency() throws Exception {

        Path irNodes = Path.of("src/main/java/dev/kof/compiler/IRNodes.java");
        String content = Files.readString(irNodes);
        assertFalse(content.contains("org.objectweb.asm"), "IRNodes must not depend on ASM");
    }

    @Test
    void compilerDriverHasNoAsmDependency() throws Exception {
        Path compilerDriver = Path.of("src/main/java/dev/kof/compiler/CompilerDriver.java");
        String content = Files.readString(compilerDriver);
        assertFalse(content.contains("org.objectweb.asm"), "CompilerDriver must not depend on ASM");
    }

    @Test
    void typeSystemHasNoAsmDependency() throws Exception {
        Path type = Path.of("src/main/java/dev/kof/compiler/Type.java");
        String content = Files.readString(type);
        assertFalse(content.contains("org.objectweb.asm"), "Type must not depend on ASM");
    }

    @Test
    void semanticAnalyzerHasNoAsmDependency() throws Exception {
        Path sa = Path.of("src/main/java/dev/kof/compiler/SemanticAnalyzer.java");
        String content = Files.readString(sa);
        assertFalse(content.contains("org.objectweb.asm"), "SemanticAnalyzer must not depend on ASM");
    }

    @Test
    void symbolTableHasNoAsmDependency() throws Exception {
        Path st = Path.of("src/main/java/dev/kof/compiler/SymbolTable.java");
        String content = Files.readString(st);
        assertFalse(content.contains("org.objectweb.asm"), "SymbolTable must not depend on ASM");
    }

    @Test
    void nativeBackendHasNoJvmTypeMapperDependency() throws Exception {
        Path nb = Path.of("src/main/java/dev/kof/compiler/NativeBackend.java");
        String content = Files.readString(nb);
        assertFalse(content.contains("JvmTypeMapper"), "NativeBackend must not use JvmTypeMapper");
    }



    @Test
    void irFieldUsesTypeNotDescriptor() {
        IRField field = new IRField("x", Type.PrimitiveType.INT, 0, null);
        assertEquals(Type.PrimitiveType.INT, field.type());
        assertEquals("x", field.name());
    }

    @Test
    void irMethodUsesTypeNotDescriptor() {
        IRMethod method = new IRMethod("add", Type.PrimitiveType.INT,
                List.of(Type.PrimitiveType.INT, Type.PrimitiveType.INT), 0, List.of(),
                List.of(), List.of());
        assertEquals(Type.PrimitiveType.INT, method.returnType());
        assertEquals(2, method.parameterTypes().size());
    }

    @Test
    void kofLoadLiteralCreation() {
        KofLoadLiteral intLit = KofLoadLiteral.ofInt(42);
        assertEquals(Type.PrimitiveType.INT, intLit.type());
        assertEquals(42, intLit.value());

        KofLoadLiteral strLit = KofLoadLiteral.ofString("hello");
        assertEquals("hello", strLit.value());

        KofLoadLiteral nullLit = KofLoadLiteral.ofNull();
        assertNull(nullLit.value());
    }

    @Test
    void kofCallSemanticRepresentation() {
        Type ownerType = new Type.ClassType("com.example", "Calculator", List.of());
        KofCall call = new KofCall(ownerType, "add",
                List.of(Type.PrimitiveType.INT, Type.PrimitiveType.INT),
                Type.PrimitiveType.INT, KofCallKind.INSTANCE);
        assertEquals("add", call.methodName());
        assertEquals(KofCallKind.INSTANCE, call.kind());
    }

    @Test
    void labelIdCreation() {
        LabelId.reset();
        LabelId a = LabelId.create();
        LabelId b = LabelId.create();
        assertNotEquals(a.id(), b.id());
    }

    @Test
    void accessFlagsAreSemantic() {
        assertTrue((AccessFlags.PUBLIC & 0x0001) != 0);
        assertTrue((AccessFlags.STATIC & 0x0008) != 0);
        assertTrue((AccessFlags.FINAL & 0x0010) != 0);
    }



    @Test
    void compilesRecordInstantiationWithAccessor(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            record Point(Int x, Int y)
            main() {
                var p = Point(10, 20)
                println(p.x())
                println(p.y())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Compilation should succeed");
    }

    @Test
    void compilesClassWithConstructor(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            public class User {
                String name
                public constructor(String name) { this.name = name }
                public getName(): String { return name }
            }
            main() {
                var user = new User("Mel")
                println(user.getName())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Compilation should succeed");
    }

    @Test
    void compilesNestedControlFlow(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var x = 10
                if (x > 5) {
                    var y = 20
                    if (y > 15) {
                        println("nested")
                    }
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Compilation should succeed");
    }

    @Test
    void compilesClassWithFieldAssignment(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            public class Counter {
                Int value
                public constructor(Int v) { this.value = v }
                public getValue(): Int { return value }
                public increment() { this.value = this.value + 1 }
            }
            main() {
                var c = new Counter(10)
                c.increment()
                println(c.getValue())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Compilation should succeed");
    }



    @Test
    void phaseF_recordNativeConstructorEmitted(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            record Point(Int x, Int y)
            main() {
                var p = Point(10, 20)
                println(p.x())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Record with constructor should compile to native");
    }

    @Test
    void phaseF_multipleRecordTypesNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            record Point(Int x, Int y)
            record Size(Int width, Int height)
            main() {
                var p = Point(1, 2)
                var s = Size(100, 200)
                println(p.x())
                println(s.width())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Multiple record types should compile to native");
    }

    @Test
    void phaseF_classNativeCompilation(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            public class User {
                String name
                public constructor(String name) {
                    this.name = name
                }
                public getName(): String {
                    return name
                }
            }
            main() {
                var u = new User("Mel")
                println(u.getName())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Class should compile to native");
    }

    @Test
    void phaseF_nativeRuntimeFunctionsExist(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                println("Hello")
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed");
        Path asmFile = tempDir.resolve("out/Default/Main.s");
        if (Files.exists(asmFile)) {
            String asm = Files.readString(asmFile);
            assertTrue(asm.contains("kof_alloc"), "Assembly should contain kof_alloc");
            assertTrue(asm.contains("kof_panic"), "Assembly should contain kof_panic");
            assertTrue(asm.contains("kof_null_error"), "Assembly should contain kof_null_error");
            assertTrue(asm.contains("kof_bounds_error"), "Assembly should contain kof_bounds_error");
        }
    }

    @Test
    void phaseF_heapAllocationInAssembly(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            record Point(Int x, Int y)
            main() {
                var p = Point(10, 20)
                println(p.x())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed");
        Path asmFile = tempDir.resolve("out/Default/Main.s");
        if (Files.exists(asmFile)) {
            String asm = Files.readString(asmFile);
            assertTrue(asm.contains("call kof_alloc"), "Assembly should use kof_alloc for object creation");
        }
    }

    @Test
    void phaseF_constructorEmittedInNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            record Point(Int x, Int y)
            main() {
                var p = Point(10, 20)
                println(p.x())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed");
        Path asmFile = tempDir.resolve("out/Default/Main.s");
        if (Files.exists(asmFile)) {
            String asm = Files.readString(asmFile);
            assertTrue(asm.contains("Point_init"), "Assembly should contain Point constructor");
        }
    }

    @Test
    void phaseF_fieldLayoutCorrectOffsets(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            record Point(Int x, Int y)
            main() {
                var p = Point(10, 20)
                println(p.x())
                println(p.y())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed");
        Path asmFile = tempDir.resolve("out/Default/Main.s");
        if (Files.exists(asmFile)) {
            String asm = Files.readString(asmFile);
            assertTrue(asm.contains("16(%rax)"), "Field x should be at offset 16");
            assertTrue(asm.contains("24(%rax)"), "Field y should be at offset 24");
        }
    }

    @Test
    void phaseF_kofDupFunctional(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            record Point(Int x, Int y)
            main() {
                var p = Point(10, 20)
                println(p.x())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed");
        Path asmFile = tempDir.resolve("out/Default/Main.s");
        if (Files.exists(asmFile)) {
            String asm = Files.readString(asmFile);
            assertTrue(asm.contains("movq (%rsp), %rax"), "KofDup should duplicate stack value");
            assertTrue(asm.contains("pushq %rax"), "KofDup should push duplicated value");
        }
    }

    @Test
    void phaseF_classLayoutTotalSize(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            record Point(Int x, Int y)
            main() {
                var p = Point(10, 20)
                println(p.x())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed");
        Path asmFile = tempDir.resolve("out/Default/Main.s");
        if (Files.exists(asmFile)) {
            String asm = Files.readString(asmFile);
            assertTrue(asm.contains("movq $32, %rdi"), "Object size should be 32 bytes (16 header + 2×8 fields)");
        }
    }



    @Test
    void phaseF1_stringLiteralJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                println("Hello, Kof!")
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "String literal should compile to JVM");
    }

    @Test
    void phaseF1_stringLiteralNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                println("Hello, Kof!")
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "String literal should compile to native");
        Path asmFile = tempDir.resolve("out/Default/Main.s");
        if (Files.exists(asmFile)) {
            String asm = Files.readString(asmFile);
            assertTrue(asm.contains("call kof_string_from_literal"), "Native should create KofString from literal");
            assertTrue(asm.contains("call kof_println_string"), "Native should use kof_println_string");
        }
    }

    @Test
    void phaseF1_stringVariableJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = "Hello"
                println(a)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "String variable should compile to JVM");
    }

    @Test
    void phaseF1_stringVariableNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = "Hello"
                println(a)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "String variable should compile to native");
    }

    @Test
    void phaseF1_utf8StringNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                println("Olá, mundo!")
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "UTF-8 string should compile to native");
        Path asmFile = tempDir.resolve("out/Default/Main.s");
        if (Files.exists(asmFile)) {
            String asm = Files.readString(asmFile);
            assertTrue(asm.contains("call kof_string_from_literal"), "Should create KofString");
        }
    }

    @Test
    void phaseF1_stringTypeIsBuiltinString() {
        Type stringType = Type.of("String");
        assertTrue(BuiltinTypes.isString(stringType), "Type.of('String') should be recognized as BuiltinTypes.STRING");
        stringType = Type.of("string");
        assertTrue(BuiltinTypes.isString(stringType), "Type.of('string') should be recognized as BuiltinTypes.STRING");
    }

    @Test
    void phaseF1_stringTypeInIr() {
        Type stringType = BuiltinTypes.STRING;
        assertFalse(Type.isPrimitive(stringType), "String should not be primitive");
        assertFalse(Type.isVoid(stringType), "String should not be void");
        assertFalse(Type.isUnknown(stringType), "String should not be unknown");
        assertTrue(stringType instanceof Type.ClassType, "String should be ClassType");
    }

    @Test
    void phaseF1_multipleStringLiteralsNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                println("Hello")
                println("World")
                println("!")
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Multiple string literals should compile to native");
        Path asmFile = tempDir.resolve("out/Default/Main.s");
        if (Files.exists(asmFile)) {
            String asm = Files.readString(asmFile);
            assertTrue(asm.contains("kof_string_from_literal"), "Should create KofStrings");
            assertTrue(asm.contains("kof_println_string"), "Should use kof_println_string");
        }
    }

    @Test
    void phaseF1_stringWithIntPrintlnNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                println(42)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "println(int) should still work");
        Path asmFile = tempDir.resolve("out/Default/Main.s");
        if (Files.exists(asmFile)) {
            String asm = Files.readString(asmFile);
            assertTrue(asm.contains("kof_int_to_string") || asm.contains("kof_print_int"),
                    "Should convert int to text before printing");
            assertTrue(asm.contains("kof_println_string") || asm.contains("kof_print_int"),
                    "Should emit a string println path for int");
        }
    }

    @Test
    void phaseF1_kofStringLayoutConstants() {
        assertEquals(1, NativeRuntime.KOF_STRING_TYPE_ID, "KofString type_id should be 1");
        assertEquals(24, NativeRuntime.KOF_STRING_HEADER_SIZE, "KofString header should be 24 bytes");
    }



    @Test
    void phaseF2_arrayCreationJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = new Int[5]
                println(a.length)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Array creation should compile to JVM");
    }

    @Test
    void phaseF2_arrayCreationNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = new Int[5]
                println(a.length)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Array creation should compile to native");
        Path asmFile = tempDir.resolve("out/Default/Main.s");
        if (Files.exists(asmFile)) {
            String asm = Files.readString(asmFile);
            assertTrue(asm.contains("call kof_array_alloc"), "Native should call kof_array_alloc");
            assertTrue(asm.contains("call kof_array_length"), "Native should call kof_array_length");
        }
    }

    @Test
    void phaseF2_arrayAccessJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = new Int[5]
                a[0] = 42
                println(a[0])
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Array access should compile to JVM");
    }

    @Test
    void phaseF2_arrayAccessNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = new Int[5]
                a[0] = 42
                println(a[0])
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Array access should compile to native");
        Path asmFile = tempDir.resolve("out/Default/Main.s");
        if (Files.exists(asmFile)) {
            String asm = Files.readString(asmFile);
            assertTrue(asm.contains("call kof_array_get"), "Native should call kof_array_get");
            assertTrue(asm.contains("call kof_array_set"), "Native should call kof_array_set");
        }
    }

    @Test
    void phaseF2_arrayLengthJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = new Int[10]
                println(a.length)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Array length should compile to JVM");
    }

    @Test
    void phaseF2_arrayLengthNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = new Int[10]
                println(a.length)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Array length should compile to native");
        Path asmFile = tempDir.resolve("out/Default/Main.s");
        if (Files.exists(asmFile)) {
            String asm = Files.readString(asmFile);
            assertTrue(asm.contains("call kof_array_length"), "Native should call kof_array_length");
        }
    }

    @Test
    void phaseF2_arrayReadWriteJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = new Int[3]
                a[0] = 10
                a[1] = 20
                a[2] = 30
                println(a[0])
                println(a[1])
                println(a[2])
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Array read/write should compile to JVM");
    }

    @Test
    void phaseF2_arrayReadWriteNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = new Int[3]
                a[0] = 10
                a[1] = 20
                a[2] = 30
                println(a[0])
                println(a[1])
                println(a[2])
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Array read/write should compile to native");
    }

    @Test
    void phaseF2_arrayWithLoopJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = new Int[5]
                for (var i = 0; i < 5; i++) {
                    a[i] = i * 10
                }
                for (var i = 0; i < 5; i++) {
                    println(a[i])
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Array with loop should compile to JVM");
    }

    @Test
    void phaseF2_arrayWithLoopNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = new Int[5]
                for (var i = 0; i < 5; i++) {
                    a[i] = i * 10
                }
                for (var i = 0; i < 5; i++) {
                    println(a[i])
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Array with loop should compile to native");
    }

    @Test
    void phaseF2_arrayAsArgumentJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            sum(Int[] arr): Int {
                var total = 0
                for (var i = 0; i < arr.length; i++) {
                    total = total + arr[i]
                }
                return total
            }
            main() {
                var a = new Int[3]
                a[0] = 1
                a[1] = 2
                a[2] = 3
                println(sum(a))
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Array as argument should compile to JVM");
    }

    @Test
    void phaseF2_arrayAsArgumentNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            sum(Int[] arr): Int {
                var total = 0
                for (var i = 0; i < arr.length; i++) {
                    total = total + arr[i]
                }
                return total
            }
            main() {
                var a = new Int[3]
                a[0] = 1
                a[1] = 2
                a[2] = 3
                println(sum(a))
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Array as argument should compile to native");
    }

    @Test
    void phaseF2_arrayAsReturnJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            createArray(): Int[] {
                var a = new Int[3]
                a[0] = 10
                a[1] = 20
                a[2] = 30
                return a
            }
            main() {
                var a = createArray()
                println(a[0])
                println(a[1])
                println(a[2])
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Array as return should compile to JVM");
    }

    @Test
    void phaseF2_arrayAsReturnNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            createArray(): Int[] {
                var a = new Int[3]
                a[0] = 10
                a[1] = 20
                a[2] = 30
                return a
            }
            main() {
                var a = createArray()
                println(a[0])
                println(a[1])
                println(a[2])
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Array as return should compile to native");
    }

    @Test
    void phaseF2_arrayLongJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = new Long[3]
                a[0] = 100l
                a[1] = 200l
                a[2] = 300l
                println(a[0])
                println(a[1])
                println(a[2])
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Long array should compile to JVM");
    }

    @Test
    void phaseF2_arrayLongNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = new Long[3]
                a[0] = 100l
                a[1] = 200l
                a[2] = 300l
                println(a[0])
                println(a[1])
                println(a[2])
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Long array should compile to native");
    }

    @Test
    void phaseF2_arrayStringJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = new String[3]
                a[0] = "Hello"
                a[1] = "World"
                a[2] = "Kof"
                println(a[0])
                println(a[1])
                println(a[2])
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "String array should compile to JVM");
    }

    @Test
    void phaseF2_arrayStringNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = new String[3]
                a[0] = "Hello"
                a[1] = "World"
                a[2] = "Kof"
                println(a[0])
                println(a[1])
                println(a[2])
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "String array should compile to native");
    }

    @Test
    void phaseF2_emptyArrayJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = new Int[0]
                println(a.length)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Empty array should compile to JVM");
    }

    @Test
    void phaseF2_emptyArrayNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = new Int[0]
                println(a.length)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Empty array should compile to native");
    }

    @Test
    void phaseF2_arrayFirstAndLastIndexJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = new Int[5]
                a[0] = 100
                a[4] = 500
                println(a[0])
                println(a[4])
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "First/last index should compile to JVM");
    }

    @Test
    void phaseF2_arrayFirstAndLastIndexNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = new Int[5]
                a[0] = 100
                a[4] = 500
                println(a[0])
                println(a[4])
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "First/last index should compile to native");
    }

    @Test
    void phaseF2_arrayRuntimeConstants() {
        assertEquals(2, NativeRuntime.KOF_ARRAY_TYPE_ID, "KofArray type_id should be 2");
        assertEquals(24, NativeRuntime.KOF_ARRAY_HEADER_SIZE, "KofArray header should be 24 bytes");
    }

    @Test
    void phaseF2_arrayTypeSystem() {
        Type intArray = Type.of("Int[]");
        assertTrue(Type.isArray(intArray), "Int[] should be array type");
        assertEquals(Type.PrimitiveType.INT, Type.arrayElementType(intArray), "Int[] element type should be Int");

        Type stringArray = Type.of("String[]");
        assertTrue(Type.isArray(stringArray), "String[] should be array type");
        assertTrue(Type.isString(Type.arrayElementType(stringArray)), "String[] element type should be String");

        Type nestedArray = Type.of("Int[][]");
        assertTrue(Type.isArray(nestedArray), "Int[][] should be array type");
        assertTrue(Type.isArray(Type.arrayElementType(nestedArray)), "Int[][] element type should be array");
    }

    @Test
    void phaseF2_arrayAssemblyContainsRuntimeFunctions(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = new Int[5]
                a[0] = 42
                println(a[0])
                println(a.length)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed");
        Path asmFile = tempDir.resolve("out/Default/Main.s");
        if (Files.exists(asmFile)) {
            String asm = Files.readString(asmFile);
            assertTrue(asm.contains("kof_array_alloc"), "Should contain kof_array_alloc");
            assertTrue(asm.contains("kof_array_length"), "Should contain kof_array_length");
            assertTrue(asm.contains("kof_array_get"), "Should contain kof_array_get");
            assertTrue(asm.contains("kof_array_set"), "Should contain kof_array_set");
        }
    }



    @Test
    void phaseF3_simpleSubclassJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                public speak(): String {
                    return "animal"
                }
            }
            class Dog extends Animal {
                public bark(): String {
                    return "woof"
                }
            }
            main() {
                var dog = new Dog()
                println(dog.bark())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Simple subclass should compile to JVM");
    }

    @Test
    void phaseF3_simpleSubclassNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                public speak(): String {
                    return "animal"
                }
            }
            class Dog extends Animal {
                public bark(): String {
                    return "woof"
                }
            }
            main() {
                var dog = new Dog()
                println(dog.bark())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Simple subclass should compile to native");
    }

    @Test
    void phaseF3_superclassFieldJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                String name
                public constructor(String name) {
                    this.name = name
                }
            }
            main() {
                var a = new Animal("Rex")
                println(a.name)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Superclass field should compile to JVM");
    }

    @Test
    void phaseF3_superclassFieldNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                String name
                public constructor(String name) {
                    this.name = name
                }
            }
            main() {
                var a = new Animal("Rex")
                println(a.name)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Superclass field should compile to native");
        Path asmFile = tempDir.resolve("out/Default/Main.s");
        if (Files.exists(asmFile)) {
            String asm = Files.readString(asmFile);
            assertTrue(asm.contains("Animal_init"), "Should contain Animal constructor");
        }
    }

    @Test
    void phaseF3_inheritedFieldAccessJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                String name
                public constructor(String name) {
                    this.name = name
                }
            }
            class Dog extends Animal {
                public constructor(String name) {
                    super(name)
                }
            }
            main() {
                var dog = new Dog("Rex")
                println(dog.name)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Inherited field access should compile to JVM");
    }

    @Test
    void phaseF3_inheritedFieldAccessNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                String name
                public constructor(String name) {
                    this.name = name
                }
            }
            class Dog extends Animal {
                public constructor(String name) {
                    super(name)
                }
            }
            main() {
                var dog = new Dog("Rex")
                println(dog.name)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Inherited field access should compile to native");
        Path asmFile = tempDir.resolve("out/Default/Main.s");
        if (Files.exists(asmFile)) {
            String asm = Files.readString(asmFile);
            assertTrue(asm.contains("Dog_init"), "Should contain Dog constructor");
            assertTrue(asm.contains("Animal_init"), "Should contain Animal constructor");
        }
    }

    @Test
    void phaseF3_inheritedMethodJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                public speak(): String {
                    return "animal"
                }
            }
            class Dog extends Animal {
                public bark(): String {
                    return "woof"
                }
            }
            main() {
                var dog = new Dog()
                println(dog.speak())
                println(dog.bark())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Inherited method should compile to JVM");
    }

    @Test
    void phaseF3_inheritedMethodNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                public speak(): String {
                    return "animal"
                }
            }
            class Dog extends Animal {
                public bark(): String {
                    return "woof"
                }
            }
            main() {
                var dog = new Dog()
                println(dog.speak())
                println(dog.bark())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Inherited method should compile to native");
        Path asmFile = tempDir.resolve("out/Default/Main.s");
        if (Files.exists(asmFile)) {
            String asm = Files.readString(asmFile);
            assertTrue(asm.contains("Animal_speak"), "Should contain Animal.speak method");
            assertTrue(asm.contains("Dog_bark"), "Should contain Dog.bark method");
        }
    }

    @Test
    void phaseF3_constructorChainingJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                String name
                public constructor(String name) {
                    this.name = name
                }
                public speak(): String {
                    return name
                }
            }
            class Dog extends Animal {
                public constructor(String name) {
                    super(name)
                }
                public bark(): String {
                    return "woof"
                }
            }
            main() {
                var dog = new Dog("Rex")
                println(dog.speak())
                println(dog.bark())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Constructor chaining should compile to JVM");
    }

    @Test
    void phaseF3_constructorChainingNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                String name
                public constructor(String name) {
                    this.name = name
                }
                public speak(): String {
                    return name
                }
            }
            class Dog extends Animal {
                public constructor(String name) {
                    super(name)
                }
                public bark(): String {
                    return "woof"
                }
            }
            main() {
                var dog = new Dog("Rex")
                println(dog.speak())
                println(dog.bark())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Constructor chaining should compile to native");
        Path asmFile = tempDir.resolve("out/Default/Main.s");
        if (Files.exists(asmFile)) {
            String asm = Files.readString(asmFile);
            assertTrue(asm.contains("Dog_init"), "Should contain Dog constructor");
            assertTrue(asm.contains("Animal_init"), "Should contain Animal constructor");
        }
    }

    @Test
    void phaseF3_subclassOwnFieldJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                String name
                public constructor(String name) {
                    this.name = name
                }
            }
            class Dog extends Animal {
                Int age
                public constructor(String name, Int age) {
                    super(name)
                    this.age = age
                }
            }
            main() {
                var dog = new Dog("Rex", 5)
                println(dog.name)
                println(dog.age)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Subclass with own field should compile to JVM");
    }

    @Test
    void phaseF3_subclassOwnFieldNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                String name
                public constructor(String name) {
                    this.name = name
                }
            }
            class Dog extends Animal {
                Int age
                public constructor(String name, Int age) {
                    super(name)
                    this.age = age
                }
            }
            main() {
                var dog = new Dog("Rex", 5)
                println(dog.name)
                println(dog.age)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Subclass with own field should compile to native");
        Path asmFile = tempDir.resolve("out/Default/Main.s");
        if (Files.exists(asmFile)) {
            String asm = Files.readString(asmFile);
            assertTrue(asm.contains("Dog_init"), "Should contain Dog constructor");
            assertTrue(asm.contains("Animal_init"), "Should contain Animal constructor");
        }
    }

    @Test
    void phaseF3_fieldLayoutInheritance(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                Int age
                public constructor(Int a) {
                    this.age = a
                }
            }
            class Dog extends Animal {
                Int weight
                public constructor(Int a, Int w) {
                    super(a)
                    this.weight = w
                }
            }
            main() {
                var dog = new Dog(5, 20)
                println(dog.age)
                println(dog.weight)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Field layout with inheritance should compile to native");
        Path asmFile = tempDir.resolve("out/Default/Main.s");
        if (Files.exists(asmFile)) {
            String asm = Files.readString(asmFile);
            assertTrue(asm.contains("16(%rax)") || asm.contains("16(%rcx)"), "Animal.age should be at offset 16");
            assertTrue(asm.contains("24(%rax)") || asm.contains("24(%rcx)"), "Dog.weight should be at offset 24");
        }
    }

    @Test
    void phaseF3_superCallWithArgsJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Base {
                Int value
                public constructor(Int v) {
                    this.value = v
                }
            }
            class Derived extends Base {
                public constructor(Int v) {
                    super(v)
                }
            }
            main() {
                var d = new Derived(42)
                println(d.value)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Super call with args should compile to JVM");
    }

    @Test
    void phaseF3_superCallWithArgsNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Base {
                Int value
                public constructor(Int v) {
                    this.value = v
                }
            }
            class Derived extends Base {
                public constructor(Int v) {
                    super(v)
                }
            }
            main() {
                var d = new Derived(42)
                println(d.value)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Super call with args should compile to native");
    }

    @Test
    void phaseF3_threeLevelInheritanceJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class A {
                Int x
                public constructor(Int x) {
                    this.x = x
                }
            }
            class B extends A {
                Int y
                public constructor(Int x, Int y) {
                    super(x)
                    this.y = y
                }
            }
            class C extends B {
                Int z
                public constructor(Int x, Int y, Int z) {
                    super(x, y)
                    this.z = z
                }
            }
            main() {
                var c = new C(1, 2, 3)
                println(c.x)
                println(c.y)
                println(c.z)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Three-level inheritance should compile to JVM");
    }

    @Test
    void phaseF3_threeLevelInheritanceNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class A {
                Int x
                public constructor(Int x) {
                    this.x = x
                }
            }
            class B extends A {
                Int y
                public constructor(Int x, Int y) {
                    super(x)
                    this.y = y
                }
            }
            class C extends B {
                Int z
                public constructor(Int x, Int y, Int z) {
                    super(x, y)
                    this.z = z
                }
            }
            main() {
                var c = new C(1, 2, 3)
                println(c.x)
                println(c.y)
                println(c.z)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Three-level inheritance should compile to native");
        Path asmFile = tempDir.resolve("out/Default/Main.s");
        if (Files.exists(asmFile)) {
            String asm = Files.readString(asmFile);
            assertTrue(asm.contains("A_init"), "Should contain A constructor");
            assertTrue(asm.contains("B_init"), "Should contain B constructor");
            assertTrue(asm.contains("C_init"), "Should contain C constructor");
        }
    }

    @Test
    void phaseF3_defaultConstructorInheritanceJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                public speak(): String {
                    return "animal"
                }
            }
            class Dog extends Animal {
                public bark(): String {
                    return "woof"
                }
            }
            main() {
                var dog = new Dog()
                println(dog.speak())
                println(dog.bark())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Default constructor with inheritance should compile to JVM");
    }

    @Test
    void phaseF3_defaultConstructorInheritanceNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                public speak(): String {
                    return "animal"
                }
            }
            class Dog extends Animal {
                public bark(): String {
                    return "woof"
                }
            }
            main() {
                var dog = new Dog()
                println(dog.speak())
                println(dog.bark())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Default constructor with inheritance should compile to native");
    }

    @Test
    void phaseF3_objectSizeInheritance() throws IOException {
        java.nio.file.Path tmpDir = java.nio.file.Files.createTempDirectory("kof_test");
        try {
            Path source = tmpDir.resolve("Main.kf");
            Files.writeString(source, """
                class Animal {
                    Int age
                    public constructor(Int a) {
                        this.age = a
                    }
                }
                class Dog extends Animal {
                    Int weight
                    public constructor(Int a, Int w) {
                        super(a)
                        this.weight = w
                    }
                }
                main() {
                    var dog = new Dog(5, 20)
                    println(dog.age)
                }
                """);
            CompilationResult result = driver.compile(source, tmpDir.resolve("out"), Target.NATIVE);
            assertTrue(result.success(), "Compilation should succeed");
            Path asmFile = tmpDir.resolve("out/Default/Main.s");
            if (Files.exists(asmFile)) {
                String asm = Files.readString(asmFile);
                assertTrue(asm.contains("movq $24, %rdi") || asm.contains("movq $32, %rdi"),
                        "Dog object size should include inherited fields (24 or 32 bytes)");
            }
        } finally {
            java.nio.file.Files.walk(tmpDir).sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try { java.nio.file.Files.deleteIfExists(p); } catch (Exception e) {}
            });
        }
    }



    @Test
    void phaseF4_simpleOverrideJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                public speak(): String {
                    return "animal"
                }
            }
            class Dog extends Animal {
                public speak(): String {
                    return "dog"
                }
            }
            main() {
                Animal a = new Dog()
                println(a.speak())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Simple override should compile to JVM");
    }

    @Test
    void phaseF4_simpleOverrideNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                public speak(): String {
                    return "animal"
                }
            }
            class Dog extends Animal {
                public speak(): String {
                    return "dog"
                }
            }
            main() {
                Animal a = new Dog()
                println(a.speak())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Simple override should compile to native");
        Path asmFile = tempDir.resolve("out/Default/Main.s");
        if (Files.exists(asmFile)) {
            String asm = Files.readString(asmFile);
            assertTrue(asm.contains("Animal_vtable"), "Should contain Animal vtable");
            assertTrue(asm.contains("Dog_vtable"), "Should contain Dog vtable");
        }
    }

    @Test
    void phaseF4_polymorphismJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Shape {
                public area(): Int {
                    return 0
                }
            }
            class Circle extends Shape {
                Int radius
                public constructor(Int r) {
                    this.radius = r
                }
                public area(): Int {
                    return radius * radius
                }
            }
            class Square extends Shape {
                Int side
                public constructor(Int s) {
                    this.side = s
                }
                public area(): Int {
                    return side * side
                }
            }
            main() {
                Shape c = new Circle(5)
                Shape s = new Square(4)
                println(c.area())
                println(s.area())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Polymorphism should compile to JVM");
    }

    @Test
    void phaseF4_polymorphismNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Shape {
                public area(): Int {
                    return 0
                }
            }
            class Circle extends Shape {
                Int radius
                public constructor(Int r) {
                    this.radius = r
                }
                public area(): Int {
                    return radius * radius
                }
            }
            class Square extends Shape {
                Int side
                public constructor(Int s) {
                    this.side = s
                }
                public area(): Int {
                    return side * side
                }
            }
            main() {
                Shape c = new Circle(5)
                Shape s = new Square(4)
                println(c.area())
                println(s.area())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Polymorphism should compile to native");
    }

    @Test
    void phaseF4_threeLevelOverrideJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class A {
                public greet(): String {
                    return "A"
                }
            }
            class B extends A {
                public greet(): String {
                    return "B"
                }
            }
            class C extends B {
                public greet(): String {
                    return "C"
                }
            }
            main() {
                A a = new C()
                println(a.greet())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Three-level override should compile to JVM");
    }

    @Test
    void phaseF4_threeLevelOverrideNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class A {
                public greet(): String {
                    return "A"
                }
            }
            class B extends A {
                public greet(): String {
                    return "B"
                }
            }
            class C extends B {
                public greet(): String {
                    return "C"
                }
            }
            main() {
                A a = new C()
                println(a.greet())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Three-level override should compile to native");
    }

    @Test
    void phaseF4_superMethodJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                public speak(): String {
                    return "animal"
                }
            }
            class Dog extends Animal {
                public speak(): String {
                    return "dog"
                }
                public describe(): String {
                    return "I am a dog"
                }
            }
            main() {
                var d = new Dog()
                println(d.speak())
                println(d.describe())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Super method should compile to JVM");
    }

    @Test
    void phaseF4_superMethodNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                public speak(): String {
                    return "animal"
                }
            }
            class Dog extends Animal {
                public speak(): String {
                    return "dog"
                }
                public describe(): String {
                    return "I am a dog"
                }
            }
            main() {
                var d = new Dog()
                println(d.speak())
                println(d.describe())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Super method should compile to native");
    }

    @Test
    void phaseF4_methodNotOverriddenJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                public speak(): String {
                    return "animal"
                }
                public walk(): String {
                    return "walking"
                }
            }
            class Dog extends Animal {
                public speak(): String {
                    return "dog"
                }
            }
            main() {
                var d = new Dog()
                println(d.speak())
                println(d.walk())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Method not overridden should compile to JVM");
    }

    @Test
    void phaseF4_methodNotOverriddenNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                public speak(): String {
                    return "animal"
                }
                public walk(): String {
                    return "walking"
                }
            }
            class Dog extends Animal {
                public speak(): String {
                    return "dog"
                }
            }
            main() {
                var d = new Dog()
                println(d.speak())
                println(d.walk())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Method not overridden should compile to native");
    }

    @Test
    void phaseF4_vtableContainsMethods(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                public speak(): String {
                    return "animal"
                }
            }
            class Dog extends Animal {
                public speak(): String {
                    return "dog"
                }
                public bark(): String {
                    return "woof"
                }
            }
            main() {
                var d = new Dog()
                println(d.speak())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed");
        Path asmFile = tempDir.resolve("out/Default/Main.s");
        if (Files.exists(asmFile)) {
            String asm = Files.readString(asmFile);
            assertTrue(asm.contains("Dog_vtable"), "Should contain Dog vtable");
            assertTrue(asm.contains("Animal_vtable"), "Should contain Animal vtable");
            assertTrue(asm.contains("Dog_speak"), "Should contain Dog.speak method");
            assertTrue(asm.contains("Dog_bark"), "Should contain Dog.bark method");
        }
    }



    @Test
    void phaseF5_simpleInterfaceJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            interface Speaker {
                speak(): String
            }
            class Dog implements Speaker {
                public speak(): String {
                    return "woof"
                }
            }
            main() {
                Dog d = new Dog()
                println(d.speak())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Simple interface should compile to JVM");
    }

    @Test
    void phaseF5_simpleInterfaceNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            interface Speaker {
                speak(): String
            }
            class Dog implements Speaker {
                public speak(): String {
                    return "woof"
                }
            }
            main() {
                Dog d = new Dog()
                println(d.speak())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Simple interface should compile to native");
    }

    @Test
    void phaseF5_interfacePolymorphismJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            interface Speaker {
                speak(): String
            }
            class Dog implements Speaker {
                public speak(): String {
                    return "woof"
                }
            }
            main() {
                Speaker s = new Dog()
                println(s.speak())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Interface polymorphism should compile to JVM");
    }

    @Test
    void phaseF5_interfacePolymorphismNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            interface Speaker {
                speak(): String
            }
            class Dog implements Speaker {
                public speak(): String {
                    return "woof"
                }
            }
            main() {
                Speaker s = new Dog()
                println(s.speak())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Interface polymorphism should compile to native");
    }

    @Test
    void phaseF5_multipleInterfacesJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            interface Speaker {
                speak(): String
            }
            interface Walker {
                walk(): String
            }
            class Dog implements Speaker, Walker {
                public speak(): String {
                    return "woof"
                }
                public walk(): String {
                    return "walking"
                }
            }
            main() {
                Dog d = new Dog()
                println(d.speak())
                println(d.walk())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Multiple interfaces should compile to JVM");
    }

    @Test
    void phaseF5_multipleInterfacesNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            interface Speaker {
                speak(): String
            }
            interface Walker {
                walk(): String
            }
            class Dog implements Speaker, Walker {
                public speak(): String {
                    return "woof"
                }
                public walk(): String {
                    return "walking"
                }
            }
            main() {
                Dog d = new Dog()
                println(d.speak())
                println(d.walk())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Multiple interfaces should compile to native");
    }

    @Test
    void phaseF5_inheritedInterfaceJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            interface Speaker {
                speak(): String
            }
            class Animal implements Speaker {
                public speak(): String {
                    return "animal"
                }
            }
            class Dog extends Animal {
                public speak(): String {
                    return "woof"
                }
            }
            main() {
                Dog d = new Dog()
                println(d.speak())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Inherited interface should compile to JVM");
    }

    @Test
    void phaseF5_inheritedInterfaceNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            interface Speaker {
                speak(): String
            }
            class Animal implements Speaker {
                public speak(): String {
                    return "animal"
                }
            }
            class Dog extends Animal {
                public speak(): String {
                    return "woof"
                }
            }
            main() {
                Dog d = new Dog()
                println(d.speak())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Inherited interface should compile to native");
    }

    @Test
    void phaseF5_interfaceThroughSuperclassJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            interface Speaker {
                speak(): String
            }
            class Animal implements Speaker {
                public speak(): String {
                    return "animal"
                }
            }
            class Dog extends Animal {
            }
            main() {
                Speaker s = new Dog()
                println(s.speak())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Interface through superclass should compile to JVM");
    }

    @Test
    void phaseF5_interfaceThroughSuperclassNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            interface Speaker {
                speak(): String
            }
            class Animal implements Speaker {
                public speak(): String {
                    return "animal"
                }
            }
            class Dog extends Animal {
            }
            main() {
                Speaker s = new Dog()
                println(s.speak())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Interface through superclass should compile to native");
    }

    @Test
    void phaseF5_interfaceWithMethodOverrideJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            interface Speaker {
                speak(): String
            }
            class Animal implements Speaker {
                public speak(): String {
                    return "animal"
                }
            }
            class Dog extends Animal {
                public speak(): String {
                    return "woof"
                }
            }
            main() {
                Speaker s1 = new Animal()
                Speaker s2 = new Dog()
                println(s1.speak())
                println(s2.speak())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Interface with override should compile to JVM");
    }

    @Test
    void phaseF5_interfaceWithMethodOverrideNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            interface Speaker {
                speak(): String
            }
            class Animal implements Speaker {
                public speak(): String {
                    return "animal"
                }
            }
            class Dog extends Animal {
                public speak(): String {
                    return "woof"
                }
            }
            main() {
                Speaker s1 = new Animal()
                Speaker s2 = new Dog()
                println(s1.speak())
                println(s2.speak())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Interface with override should compile to native");
    }

    @Test
    void phaseF5_interfaceMethodInVtable(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            interface Speaker {
                speak(): String
            }
            class Dog implements Speaker {
                public speak(): String {
                    return "woof"
                }
                public bark(): String {
                    return "bark"
                }
            }
            main() {
                Dog d = new Dog()
                println(d.speak())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Interface method in vtable should compile to native");
        Path asmFile = tempDir.resolve("out/Default/Main.s");
        if (Files.exists(asmFile)) {
            String asm = Files.readString(asmFile);
            assertTrue(asm.contains("Dog_speak"), "Should contain Dog.speak in vtable");
            assertTrue(asm.contains("Dog_bark"), "Should contain Dog.bark in vtable");
        }
    }



    @Test
    void phaseF6_throwJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                throw "error"
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Throw should compile to JVM");
    }

    @Test
    void phaseF6_throwNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                throw "error"
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Throw should compile to native");
        Path asmFile = tempDir.resolve("out/Default/Main.s");
        if (Files.exists(asmFile)) {
            String asm = Files.readString(asmFile);
            assertTrue(asm.contains("call kof_panic"), "Should call kof_panic for throw");
        }
    }

    @Test
    void phaseF6_tryCatchJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                try {
                    throw "error"
                } catch (String e) {
                    println(e)
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Try/catch should compile to JVM");
    }

    @Test
    void phaseF6_tryCatchNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                try {
                    throw "error"
                } catch (String e) {
                    println(e)
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Try/catch should compile to native");
    }

    @Test
    void phaseF6_tryFinallyJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                try {
                    throw "error"
                } finally {
                    println("finally")
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Try/finally should compile to JVM");
    }

    @Test
    void phaseF6_tryFinallyNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                try {
                    throw "error"
                } finally {
                    println("finally")
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Try/finally should compile to native");
    }

    @Test
    void phaseF6_tryCatchFinallyJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                try {
                    throw "error"
                } catch (String e) {
                    println(e)
                } finally {
                    println("finally")
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Try/catch/finally should compile to JVM");
    }

    @Test
    void phaseF6_tryCatchFinallyNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                try {
                    throw "error"
                } catch (String e) {
                    println(e)
                } finally {
                    println("finally")
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Try/catch/finally should compile to native");
    }

    @Test
    void phaseF6_nestedTryJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                try {
                    try {
                        throw "inner"
                    } catch (String e) {
                        println(e)
                    }
                } catch (String e) {
                    println(e)
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Nested try should compile to JVM");
    }

    @Test
    void phaseF6_nestedTryNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                try {
                    try {
                        throw "inner"
                    } catch (String e) {
                        println(e)
                    }
                } catch (String e) {
                    println(e)
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Nested try should compile to native");
    }

    @Test
    void phaseF6_multipleCatchJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                try {
                    throw "error"
                } catch (String e) {
                    println(e)
                } catch (Int e) {
                    println(e)
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Multiple catch should compile to JVM");
    }

    @Test
    void phaseF6_multipleCatchNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                try {
                    throw "error"
                } catch (String e) {
                    println(e)
                } catch (Int e) {
                    println(e)
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Multiple catch should compile to native");
    }

    @Test
    void phaseF6_throwExpressionJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            boom(): String {
                throw "boom"
            }
            main() {
                try {
                    boom()
                } catch (String e) {
                    println(e)
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Throw in function should compile to JVM");
    }

    @Test
    void phaseF6_throwExpressionNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            boom(): String {
                throw "boom"
            }
            main() {
                try {
                    boom()
                } catch (String e) {
                    println(e)
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Throw in function should compile to native");
    }



    @Test
    void stringConcatJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = "Hello"
                var b = " World"
                println(a + b)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "String concat should compile to JVM");
    }

    @Test
    void stringConcatNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = "Hello"
                var b = " World"
                println(a + b)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "String concat should compile to native");
        Path asmFile = tempDir.resolve("out/Default/Main.s");
        if (Files.exists(asmFile)) {
            String asm = Files.readString(asmFile);
            assertTrue(asm.contains("call kof_string_concat"), "Should call kof_string_concat");
        }
    }

    @Test
    void stringConcatLiteralJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                println("Hello" + " World")
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "String concat literal should compile to JVM");
    }

    @Test
    void stringConcatLiteralNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                println("Hello" + " World")
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "String concat literal should compile to native");
    }



    @Test
    void typeCheck_stringConcatResultType(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = "Hello"
                var b = " World"
                var c = a + b
                println(c)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "String concat result should be String");
    }

    @Test
    void typeCheck_intArithmetic(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = 10
                var b = 20
                var c = a + b
                println(c)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Int arithmetic should work");
    }

    @Test
    void typeCheck_comparisonReturnsBool(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = 10
                var b = 20
                var c = a < b
                if (c) {
                    println("less")
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Comparison should return Bool");
    }

    @Test
    void parse_genericCallAmbiguity(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            pick<T>(T x, T y): T {
                return x
            }
            main() {
                var a = 10
                var b = 20
                var lt = a < b
                var le = a <= b
                var arr = new Int[3]
                var len = arr.length
                var v = arr[0]
                var n = pick<Int>(1, 2)
                if (lt && le) {
                    println(n + len + v)
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Less-than must not be parsed as generic call");
    }

    @Test
    void parse_genericCallAmbiguityLoop(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var l = new List<Int>()
                for (var i = 0; i < l.size; i++) {
                    l.add(i)
                }
                var sum = 0
                for (var i = 0; i < l.size; i++) {
                    sum = sum + l.get(i)
                }
                println(sum)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "i < l.size must not be parsed as generic call");
    }

    @Test
    void typeCheck_logicalOperators(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = true
                var b = false
                var c = a && b
                var d = a || b
                if (c) {
                    println("both")
                }
                if (d) {
                    println("either")
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Logical operators should work");
    }

    @Test
    void typeCheck_arrayLengthReturnsInt(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = new Int[10]
                var len = a.length
                println(len)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Array length should return Int");
    }

    @Test
    void typeCheck_methodReturnType(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            add(Int a, Int b): Int {
                return a + b
            }
            main() {
                var result = add(2, 3)
                println(result)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Method return type should be correct");
    }

    @Test
    void typeCheck_inheritanceReturnType(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                public speak(): String {
                    return "animal"
                }
            }
            class Dog extends Animal {
                public speak(): String {
                    return "dog"
                }
            }
            main() {
                var d = new Dog()
                var s = d.speak()
                println(s)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Inherited method return type should be correct");
    }

    @Test
    void typeCheck_interfaceReturnType(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            interface Speaker {
                speak(): String
            }
            class Dog implements Speaker {
                public speak(): String {
                    return "woof"
                }
            }
            main() {
                var d = new Dog()
                var s = d.speak()
                println(s)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Interface method return type should be correct");
    }



    @Test
    void integration_fullProgramJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            interface Speaker {
                speak(): String
            }
            class Animal {
                String name
                public constructor(String n) {
                    this.name = n
                }
                public getName(): String {
                    return name
                }
            }
            class Dog extends Animal implements Speaker {
                public constructor(String n) {
                    super(n)
                }
                public speak(): String {
                    return "woof"
                }
                public bark(): String {
                    return "bark!"
                }
            }
            main() {
                var d = new Dog("Rex")
                println(d.getName())
                println(d.speak())
                println(d.bark())
                Speaker s = new Dog("Buddy")
                println(s.speak())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Full program should compile to JVM");
    }

    @Test
    void integration_fullProgramNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            interface Speaker {
                speak(): String
            }
            class Animal {
                String name
                public constructor(String n) {
                    this.name = n
                }
                public getName(): String {
                    return name
                }
            }
            class Dog extends Animal implements Speaker {
                public constructor(String n) {
                    super(n)
                }
                public speak(): String {
                    return "woof"
                }
                public bark(): String {
                    return "bark!"
                }
            }
            main() {
                var d = new Dog("Rex")
                println(d.getName())
                println(d.speak())
                println(d.bark())
                Speaker s = new Dog("Buddy")
                println(s.speak())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Full program should compile to native");
    }

    @Test
    void integration_arraysAndStringsJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = new Int[5]
                for (var i = 0; i < 5; i++) {
                    a[i] = i * 10
                }
                for (var i = 0; i < 5; i++) {
                    println(a[i])
                }
                var s = "Hello" + " World"
                println(s)
                println(s.length)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Arrays and strings should compile to JVM");
    }

    @Test
    void integration_arraysAndStringsNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = new Int[5]
                for (var i = 0; i < 5; i++) {
                    a[i] = i * 10
                }
                for (var i = 0; i < 5; i++) {
                    println(a[i])
                }
                var s = "Hello" + " World"
                println(s)
                println(s.length)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Arrays and strings should compile to native");
    }

    @Test
    void integration_exceptionHandlingJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                try {
                    var a = new Int[3]
                    a[0] = 10
                    println(a[0])
                } catch (String e) {
                    println(e)
                } finally {
                    println("done")
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Exception handling should compile to JVM");
    }

    @Test
    void integration_exceptionHandlingNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                try {
                    var a = new Int[3]
                    a[0] = 10
                    println(a[0])
                } catch (String e) {
                    println(e)
                } finally {
                    println("done")
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Exception handling should compile to native");
    }

    @Test
    void integration_virtualDispatchJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Shape {
                public area(): Int {
                    return 0
                }
            }
            class Circle extends Shape {
                Int radius
                public constructor(Int r) {
                    this.radius = r
                }
                public area(): Int {
                    return radius * radius
                }
            }
            class Square extends Shape {
                Int side
                public constructor(Int s) {
                    this.side = s
                }
                public area(): Int {
                    return side * side
                }
            }
            main() {
                Shape c = new Circle(5)
                Shape s = new Square(4)
                println(c.area())
                println(s.area())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Virtual dispatch should compile to JVM");
    }

    @Test
    void integration_virtualDispatchNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Shape {
                public area(): Int {
                    return 0
                }
            }
            class Circle extends Shape {
                Int radius
                public constructor(Int r) {
                    this.radius = r
                }
                public area(): Int {
                    return radius * radius
                }
            }
            class Square extends Shape {
                Int side
                public constructor(Int s) {
                    this.side = s
                }
                public area(): Int {
                    return side * side
                }
            }
            main() {
                Shape c = new Circle(5)
                Shape s = new Square(4)
                println(c.area())
                println(s.area())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Virtual dispatch should compile to native");
    }

    @Test
    void integration_fieldInitializationJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Config {
                String host = "localhost"
                Int port = 8080
                public constructor() {
                }
            }
            main() {
                var c = new Config()
                println(c.host)
                println(c.port)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Field initialization should compile to JVM");
    }

    @Test
    void integration_fieldInitializationNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Config {
                String host = "localhost"
                Int port = 8080
                public constructor() {
                }
            }
            main() {
                var c = new Config()
                println(c.host)
                println(c.port)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Field initialization should compile to native");
    }

    @Test
    void integration_nestedControlFlowJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var x = 10
                if (x > 5) {
                    var y = 20
                    if (y > 15) {
                        for (var i = 0; i < 3; i++) {
                            println(i)
                        }
                    }
                } else {
                    println("small")
                }
                var i = 0
                while (i < 3) {
                    println(i)
                    i = i + 1
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Nested control flow should compile to JVM");
    }

    @Test
    void integration_nestedControlFlowNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var x = 10
                if (x > 5) {
                    var y = 20
                    if (y > 15) {
                        for (var i = 0; i < 3; i++) {
                            println(i)
                        }
                    }
                } else {
                    println("small")
                }
                var i = 0
                while (i < 3) {
                    println(i)
                    i = i + 1
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Nested control flow should compile to native");
    }

    @Test
    void integration_recursionJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            factorial(Int n): Int {
                if (n <= 1) {
                    return 1
                }
                return n * factorial(n - 1)
            }
            main() {
                println(factorial(5))
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Recursion should compile to JVM");
    }

    @Test
    void integration_recursionNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            factorial(Int n): Int {
                if (n <= 1) {
                    return 1
                }
                return n * factorial(n - 1)
            }
            main() {
                println(factorial(5))
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Recursion should compile to native");
    }

    @Test
    void integration_multipleClassesJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            record Point(Int x, Int y)
            class Rect {
                Point topLeft
                Point bottomRight
                public constructor(Point tl, Point br) {
                    this.topLeft = tl
                    this.bottomRight = br
                }
                public width(): Int {
                    return bottomRight.x() - topLeft.x()
                }
                public height(): Int {
                    return bottomRight.y() - topLeft.y()
                }
            }
            main() {
                var tl = Point(0, 0)
                var br = Point(10, 5)
                var r = new Rect(tl, br)
                println(r.width())
                println(r.height())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Multiple classes should compile to JVM");
    }

    @Test
    void integration_multipleClassesNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            record Point(Int x, Int y)
            class Rect {
                Point topLeft
                Point bottomRight
                public constructor(Point tl, Point br) {
                    this.topLeft = tl
                    this.bottomRight = br
                }
                public width(): Int {
                    return bottomRight.x() - topLeft.x()
                }
                public height(): Int {
                    return bottomRight.y() - topLeft.y()
                }
            }
            main() {
                var tl = Point(0, 0)
                var br = Point(10, 5)
                var r = new Rect(tl, br)
                println(r.width())
                println(r.height())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Multiple classes should compile to native");
    }



    @Test
    void doWhileSimpleJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var i = 0
                do {
                    println(i)
                    i = i + 1
                } while (i < 3)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Simple do-while should compile to JVM");
    }

    @Test
    void doWhileSimpleNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var i = 0
                do {
                    println(i)
                    i = i + 1
                } while (i < 3)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Simple do-while should compile to native");
    }

    @Test
    void doWhileNestedJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var i = 0
                do {
                    var j = 0
                    do {
                        println(j)
                        j = j + 1
                    } while (j < 2)
                    i = i + 1
                } while (i < 2)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Nested do-while should compile to JVM");
    }

    @Test
    void doWhileNestedNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var i = 0
                do {
                    var j = 0
                    do {
                        println(j)
                        j = j + 1
                    } while (j < 2)
                    i = i + 1
                } while (i < 2)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Nested do-while should compile to native");
    }

    @Test
    void doWhileRunsAtLeastOnceJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var i = 10
                do {
                    println(i)
                    i = i + 1
                } while (i < 5)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "do-while runs at least once should compile to JVM");
    }

    @Test
    void doWhileRunsAtLeastOnceNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var i = 10
                do {
                    println(i)
                    i = i + 1
                } while (i < 5)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "do-while runs at least once should compile to native");
    }



    @Test
    void instanceofBasicJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                public speak(): String {
                    return "animal"
                }
            }
            class Dog extends Animal {
                public speak(): String {
                    return "dog"
                }
            }
            main() {
                var a = new Dog()
                if (a instanceof Dog) {
                    println("is Dog")
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "instanceof should compile to JVM");
    }

    @Test
    void instanceofBasicNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                public speak(): String {
                    return "animal"
                }
            }
            class Dog extends Animal {
                public speak(): String {
                    return "dog"
                }
            }
            main() {
                var a = new Dog()
                if (a instanceof Dog) {
                    println("is Dog")
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "instanceof should compile to native");
    }

    @Test
    void instanceofInheritanceJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                public speak(): String {
                    return "animal"
                }
            }
            class Dog extends Animal {
                public speak(): String {
                    return "dog"
                }
            }
            main() {
                var a = new Dog()
                if (a instanceof Animal) {
                    println("is Animal")
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "instanceof with inheritance should compile to JVM");
    }

    @Test
    void instanceofInheritanceNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                public speak(): String {
                    return "animal"
                }
            }
            class Dog extends Animal {
                public speak(): String {
                    return "dog"
                }
            }
            main() {
                var a = new Dog()
                if (a instanceof Animal) {
                    println("is Animal")
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "instanceof with inheritance should compile to native");
    }

    @Test
    void castBasicJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                public speak(): String {
                    return "animal"
                }
            }
            class Dog extends Animal {
                public speak(): String {
                    return "dog"
                }
            }
            main() {
                var a = new Dog()
                var d = a as Dog
                println(d.speak())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "cast should compile to JVM");
    }

    @Test
    void castBasicNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                public speak(): String {
                    return "animal"
                }
            }
            class Dog extends Animal {
                public speak(): String {
                    return "dog"
                }
            }
            main() {
                var a = new Dog()
                var d = a as Dog
                println(d.speak())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "cast should compile to native");
    }

    @Test
    void instanceofWithIfElseJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                public speak(): String {
                    return "animal"
                }
            }
            class Dog extends Animal {
                public speak(): String {
                    return "dog"
                }
            }
            main() {
                var a = new Dog()
                if (a instanceof Dog) {
                    println("Dog")
                } else {
                    println("Animal")
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "instanceof with if/else should compile to JVM");
    }

    @Test
    void instanceofWithIfElseNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                public speak(): String {
                    return "animal"
                }
            }
            class Dog extends Animal {
                public speak(): String {
                    return "dog"
                }
            }
            main() {
                var a = new Dog()
                if (a instanceof Dog) {
                    println("Dog")
                } else {
                    println("Animal")
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "instanceof with if/else should compile to native");
    }



    @Test
    void switchBasicJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var x = 1
                switch (x) {
                    case 1:
                        println("one")
                    case 2:
                        println("two")
                    default:
                        println("other")
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Basic switch should compile to JVM");
    }

    @Test
    void switchBasicNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var x = 1
                switch (x) {
                    case 1:
                        println("one")
                    case 2:
                        println("two")
                    default:
                        println("other")
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Basic switch should compile to native");
    }

    @Test
    void switchStringJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var s = "hello"
                switch (s) {
                    case "hello":
                        println("greeting")
                    case "goodbye":
                        println("farewell")
                    default:
                        println("unknown")
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "String switch should compile to JVM");
    }

    @Test
    void switchStringNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var s = "hello"
                switch (s) {
                    case "hello":
                        println("greeting")
                    case "goodbye":
                        println("farewell")
                    default:
                        println("unknown")
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "String switch should compile to native");
    }

    @Test
    void switchNestedJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var x = 1
                switch (x) {
                    case 1:
                        var y = 10
                        switch (y) {
                            case 10:
                                println("ten")
                            default:
                                println("other")
                        }
                    default:
                        println("other")
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Nested switch should compile to JVM");
    }

    @Test
    void switchNestedNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var x = 1
                switch (x) {
                    case 1:
                        var y = 10
                        switch (y) {
                            case 10:
                                println("ten")
                            default:
                                println("other")
                        }
                    default:
                        println("other")
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Nested switch should compile to native");
    }

    /**
     * Regressão crítica de correção semântica: NENHUM identificador não
     * declarado pode ser aceito porque o compilador "inferiu um tipo" para ele.
     * Não há fallback silencioso para Unknown/Object/Any — todo identificador
     * não resolvido deve emitir SEM011 em QUALQUER posição.
     */
    private void assertUndeclaredRejected(String source, Path tempDir, String name) throws IOException {
        Path src = tempDir.resolve(name + ".kf");
        Files.writeString(src, source);
        CompilationResult result = driver.compile(src, tempDir.resolve("out"), Target.JVM);
        assertFalse(result.success(), "Undeclared identifier must fail to compile: " + name);
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("SEM011"),
                "Must report SEM011 (no silent fallback), got: " + diags);
    }

    @Test
    void undeclaredIdentifiersNeverInferredIntoVariables(@TempDir Path tempDir) throws IOException {
        // declaração + uso: válido
        Path ok = tempDir.resolve("ok.kf");
        Files.writeString(ok, """
            main() {
                val x = 10
                println(x)
            }
            """);
        assertTrue(driver.compile(ok, tempDir.resolve("okout"), Target.JVM).success(),
                "Declared variable must work");

        // uso sem declaração: SEM011 em cada posição
        assertUndeclaredRejected("""
            main() { println(ghost) }
            """, tempDir, "u1");
        assertUndeclaredRejected("""
            main() { foo(ghost) }
            """, tempDir, "u2");
        assertUndeclaredRejected("""
            main() { var r = ghost + 1; println(r) }
            """, tempDir, "u3");
        assertUndeclaredRejected("""
            main() { ghost = 5 }
            """, tempDir, "u4");
        assertUndeclaredRejected("""
            main() { var s = "v:" + ghost; println(s) }
            """, tempDir, "u5");
        assertUndeclaredRejected("""
            Int f() { return ghost }
            main() { println(f()) }
            """, tempDir, "u6");
        assertUndeclaredRejected("""
            main() { val a = ghost; println(a) }
            """, tempDir, "u7");
        assertUndeclaredRejected("""
            class C { Int campo = ghost }
            main() { var c = C(); println("ok") }
            """, tempDir, "u8");
        assertUndeclaredRejected("""
            main() { for (var item in ghost) { println(item) } }
            """, tempDir, "u9");
        // dentro de lambda (body analisado)
        assertUndeclaredRejected("""
            main() { val f = (x: Int) -> x + ghost; println(f(1)) }
            """, tempDir, "u10");
        assertUndeclaredRejected("""
            main() { var r = listOf(1, 2).map((x: Int) -> ghost + x); println(r.size) }
            """, tempDir, "u11");
        // lambda aninhada: corpo do lambda interno também é analisado
        assertUndeclaredRejected("""
            main() { val f = (a: Int) -> ((b: Int) -> ghost + b); println("ok") }
            """, tempDir, "u12");
    }

    @Test
    void lambdaParametersBoundInOwnScope(@TempDir Path tempDir) throws IOException {
        // parâmetros de lambda são registrados no escopo próprio
        Path ok = tempDir.resolve("lambdascope.kf");
        Files.writeString(ok, """
            main() {
                val f = (x: Int) -> x + 1
                println(f(10))
            }
            """);
        assertTrue(driver.compile(ok, tempDir.resolve("out"), Target.JVM).success(),
                "Lambda param in own scope must work");
        // shadowing: param sombreia variável externa
        Path sh = tempDir.resolve("shadow.kf");
        Files.writeString(sh, """
            main() {
                val y = 100
                val f = (y: Int) -> y + 1
                println(f(10))
            }
            """);
        assertTrue(driver.compile(sh, tempDir.resolve("out2"), Target.JVM).success(),
                "Lambda param shadowing must work");
        // identificador desconhecido no corpo do lambda: SEM011
        assertUndeclaredRejected("""
            main() { val f = (x: Int) -> y + 1; println(f(10)) }
            """, tempDir, "u13");
    }

    // known-bugs #8 — function types `(Int) -> Int` now PARSE as type
    // annotations, generic arguments and lambda parameter types. Invoking a
    // value of a DECLARED function type (no synthetic lambda class) requires
    // interface dispatch (not implemented) → clean SEM032, not broken bytecode.
    @Test
    void functionTypeSyntax(@TempDir Path tempDir) throws IOException {
        Path ok = tempDir.resolve("ft.kf");
        Files.writeString(ok, """
            main() {
                var fs = listOf<(Int) -> Int>()
                println(fs.size)
            }
            """);
        assertTrue(driver.compile(ok, tempDir.resolve("out"), Target.JVM).success(),
                "Function type as generic argument should parse");

        Path bad = tempDir.resolve("bad.kf");
        Files.writeString(bad, """
            main() {
                val f = (s: (Int) -> Int) -> s(1)
                println(f((x: Int) -> x * 10))
            }
            """);
        CompilationResult result = driver.compile(bad, tempDir.resolve("out2"), Target.JVM);
        assertFalse(result.success(), "Invoking a declared function type needs interface dispatch");
        assertTrue(result.diagnostics().getDiagnostics().toString().contains("SEM032"),
                "Should be a clean SEM032, got: " + result.diagnostics().getDiagnostics());
    }

    // known-bugs #15 — primitive assigned to Object must box (JVM); String
    // must still reject Int. Also: no-initializer declarations get a default
    // (0 primitive / null reference) — they used to crash the frame.
    @Test
    void primitiveAssignableToObject(@TempDir Path tempDir) throws IOException {
        Path ok = tempDir.resolve("obj.kf");
        Files.writeString(ok, """
            main() {
                Object n = 42
                Object d = 3.14
                Object b = true
                Object o
                o = 7
                println("ok")
                Int x
                println(x)
            }
            """);
        Path outJvm = tempDir.resolve("outjvm");
        Path outNat = tempDir.resolve("outnat");
        assertTrue(driver.compile(ok, outJvm, Target.JVM).success(),
                "primitive → Object should compile on JVM");
        assertTrue(driver.compile(ok, outNat, Target.NATIVE).success(),
                "primitive → Object should compile on Native");

        Path bad = tempDir.resolve("bad.kf");
        Files.writeString(bad, """
            main() {
                String s = 42
            }
            """);
        CompilationResult result = driver.compile(bad, tempDir.resolve("out2"), Target.JVM);
        assertFalse(result.success(), "Int → String must still be rejected");
        assertTrue(result.diagnostics().getDiagnostics().toString().contains("SEM021"),
                "Int → String should be SEM021");
    }

    /**
     * Regressão (SEM-AUDIT): parâmetro de lambda SEM anotação de tipo não pode
     * virar `Object` silencioso e aceitar aritmética — o emit faria IADD sobre
     * referência (bytecode inválido; a JVM rejeita com VerifyError disfarçado de
     * "JavaFX launcher"). A regra: inferência nunca mascara tipo inaplicável;
     * diagnóstico SEM explícito, com dica de como corrigir.
     */
    @Test
    void untypedLambdaParamArithmeticIsDiagnosedNotEmitted(@TempDir Path tempDir) throws IOException {
        // aritmética sobre param sem tipo → SEM001, nunca bytecode quebrado
        Path bad = tempDir.resolve("untyped.kf");
        Files.writeString(bad, """
            main() {
                val f = (x) -> x + 1
                println(f(10))
            }
            """);
        CompilationResult result = driver.compile(bad, tempDir.resolve("out"), Target.JVM);
        assertFalse(result.success(),
                "Object + Int must not compile (would emit IADD over reference)");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("SEM001"), "must be SEM001, got: " + diags);
        assertTrue(diags.contains("non-numeric"), "message must name the problem: " + diags);

        // com anotação: o mesmo corpo é válido (a dica do diagnóstico funciona)
        Path ok = tempDir.resolve("typed.kf");
        Files.writeString(ok, """
            main() {
                val f = (x: Int) -> x + 1
                println(f(10))
            }
            """);
        assertTrue(driver.compile(ok, tempDir.resolve("out2"), Target.JVM).success(),
                "(x: Int) -> x + 1 must compile");

        // comparação (== / !=) sobre Object continua válida — só aritmética é
        // que não tem opcode para referência
        Path cmp = tempDir.resolve("cmp.kf");
        Files.writeString(cmp, """
            main() {
                val f = (x) -> x == null
                println(f("a"))
            }
            """);
        assertTrue(driver.compile(cmp, tempDir.resolve("out3"), Target.JVM).success(),
                "== sobre Object deve continuar válido");
    }
}
