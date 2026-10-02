package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;


class JvmE2ETest extends JvmSupport {


    @Test
    void execHelloWorld(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, "main() { println(\"Hello, JVM!\") }");
        runJvm(source, tempDir.resolve("out"), "Hello, JVM!");
    }

    @Test
    void execArithmetic(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                println(10 + 20 * 3)
                println(100 / 7)
                println(17 % 5)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "70\n14\n2");
    }

    @Test
    void execIfElse(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SRC_EXEC_IF_ELSE);
        runJvm(source, tempDir.resolve("out"), "greater\nsmaller2");
    }

    @Test
    void execIfElseNested(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SRC_EXEC_IF_ELSE_NESTED);
        runJvm(source, tempDir.resolve("out"), "mid");
    }

    @Test
    void execWhileLoop(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var i = 0
                var sum = 0
                while (i < 5) {
                    sum = sum + i
                    i = i + 1
                }
                println(sum)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "10");
    }

    @Test
    void execForLoop(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var sum = 0
                for (var i = 0; i < 10; i++) {
                    sum = sum + i
                }
                println(sum)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "45");
    }

    @Test
    void execDoWhile(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var i = 0
                do {
                    i = i + 1
                } while (i < 3)
                println(i)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "3");
    }

    @Test
    void execBreakContinue(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var sum = 0
                for (var i = 0; i < 10; i++) {
                    if (i == 2) { continue }
                    if (i == 5) { break }
                    sum = sum + i
                }
                println(sum)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "8");
    }

    @Test
    void execSwitch(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var x = 2
                switch (x) {
                    case 1: println("one")
                    case 2: println("two")
                    default: println("many")
                }
            }
            """);
        runJvm(source, tempDir.resolve("out"), "two");
    }

    @Test
    void execStringConcatEquals(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var s = "Hello"
                var t = s + " World"
                println(t)
                println(s == "Hello")
                println(s != "Hello")
                println(s != "World")
                println(s.length)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "Hello World\ntrue\nfalse\ntrue\n5");
    }

    @Test
    void execStringMethods(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var s = "Hello World"
                println(s.charAt(1))
                println(s.substring(6))
                println(s.substring(0, 5))
                println(s.contains("World"))
                println(s.startsWith("Hello"))
                println(s.endsWith("orld"))
                println(s.indexOf("W"))
            }
            """);
        runJvm(source, tempDir.resolve("out"), "e\nWorld\nHello\ntrue\ntrue\ntrue\n6");
    }

    @Test
    void execStringComparison(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = "x"
                var b = "y"
                var ne = a != b
                var ne2 = a != "x"
                if (ne) { println("ne-true") } else { println("ne-false") }
                if (ne2) { println("ne2-true") } else { println("ne2-false") }
            }
            """);
        runJvm(source, tempDir.resolve("out"), "ne-true\nne2-false");
    }

    @Test
    void execListOperations(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SRC_EXEC_LIST_OPERATIONS);
        runJvm(source, tempDir.resolve("out"), "45\n100\n10");
    }

    @Test
    void execListRichApi(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SRC_EXEC_LIST_RICH_API);
        runJvm(source, tempDir.resolve("out"), "4\ntrue\nfalse\nfalse\n2\n3\n3\ntrue\ntrue\n0");
    }

    @Test
    void execListString(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var l = new List<String>()
                l.add("a")
                l.add("b")
                l.add("c")
                println(l.get(2))
                println(l.size)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "c\n3");
    }

    @Test
    void execArrays(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = new Int[3]
                a[0] = 1
                a[1] = 2
                a[2] = 3
                println(a[0] + a[1] + a[2])
                println(a.length)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "6\n3");
    }

    // #132 (issue da mantenedora): acesso a elemento de Bool[]/Byte[]/Short[]/
    // Char[] emitia IALOAD/IASTORE (opcode de int[]) em vez de BALOAD/SALOAD/
    // CALOAD. No Temurin 25 o verificador aceita o bytecode errado e o processo
    // morre no boot com o sintoma JavaFX (regra do JavaFX/AGENTS.md) — o teste
    // falhava com exit!=0 ANTES do fix. Cobertura: os 4 tipos afetados + Int[]
    // como controle, valores negativos (Byte/Short preservam sinal via
    // BALOAD/SALOAD) e round-trip load→store.
    @Test
    void execNarrowPrimitiveArrayAccess(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SRC_EXEC_NARROW_PRIMITIVE_ARRAY_ACCESS);
        runJvm(source, tempDir.resolve("out"), "true\nfalse\nA\nB\n1000\n-5\n7\n-8\n43");
    }

    @Test
    void execFunctions(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fib(Int n): Int {
                if (n < 2) { return n }
                return fib(n - 1) + fib(n - 2)
            }
            main() {
                println(fib(10))
            }
            """);
        runJvm(source, tempDir.resolve("out"), "55");
    }

    @Test
    void execRecords(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            record Point(Int x, Int y)
            main() {
                var p = Point(10, 20)
                println(p.x())
                println(p.y())
            }
            """);
        runJvm(source, tempDir.resolve("out"), "10\n20");
    }

    @Test
    void execClasses(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SRC_EXEC_CLASSES);
        runJvm(source, tempDir.resolve("out"), "3");
    }

    @Test
    void execInheritance(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SRC_EXEC_INHERITANCE);
        runJvm(source, tempDir.resolve("out"), "dog\nRex");
    }

    @Test
    void execVirtualDispatch(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SRC_EXEC_VIRTUAL_DISPATCH);
        runJvm(source, tempDir.resolve("out"), "dog\ncat");
    }

    @Test
    void execInterfaces(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            interface Speaker {
                speak(): String
            }
            class Dog implements Speaker {
                speak(): String = "woof"
            }
            main() {
                var d = new Dog()
                println(d.speak())
            }
            """);
        runJvm(source, tempDir.resolve("out"), "woof");
    }

    @Test
    void execGenericFunction(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            identity<T>(T x): T {
                return x
            }
            main() {
                println(identity<Int>(42))
                println(identity<String>("hi"))
            }
            """);
        runJvm(source, tempDir.resolve("out"), "42\nhi");
    }

    @Test
    void execGenericClass(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SRC_EXEC_GENERIC_CLASS);
        runJvm(source, tempDir.resolve("out"), "7");
    }

    @Test
    void execLongArithmetic(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = 10000000000l
                var b = 5000000000l
                println(a + b)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "15000000000");
    }

    @Test
    void execCastInstanceof(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal { }
            class Dog extends Animal { }
            main() {
                var d = new Dog()
                if (d instanceof Dog) {
                    println("is-dog")
                }
                if (d instanceof Animal) {
                    println("is-animal")
                }
            }
            """);
        runJvm(source, tempDir.resolve("out"), "is-dog\nis-animal");
    }

    @Test
    void execBooleanOperators(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = true
                var b = false
                var c = a && b
                var d = a || b
                println(c)
                println(d)
                println(!c)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "false\ntrue\ntrue");
    }

    @Test
    void execFunctionDeclarationForms(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SRC_EXEC_FUNCTION_DECLARATION_FORMS);
        runJvm(source, tempDir.resolve("out"), "oi\n5\ntrue\nMel");
    }

    @Test
    void execRecordNullablePrimitiveEquals(@TempDir Path tempDir) throws IOException {
        // #127: record com campo Int? gerava equals() com bytecode inválido —
        // dois `int` crus na pilha chamando Objects.equals(Object,Object) →
        // VerifyError "integer not assignable to java/lang/Object" (issue do
        // colaborador, distro oficial 0.3.23-beta). Fix: JvmRecordEmitter erases
        // Nullable(primitivo) p/ primitivo em equals/hashCode/toString. Este
        // teste PROVA a morte: no código velho falha com exit != 0 (VerifyError).
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            record Pair(Int? x, Int? y)
            main() {
                var a = Pair(1, 2)
                var b = Pair(1, 2)
                println(a == b)
                println(a == a)
                println(a.hashCode() == b.hashCode())
                println(a)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "true\ntrue\ntrue\nPair[x=1, y=2]");
    }

    @Test
    void execRecordListFieldDecode(@TempDir Path tempDir) throws IOException {
        // #128: json.decode<Container> com campo List<Item>? ANINHADO. O caso
        // top-level (decode<List<Record>>) já funcionava; o aninhado devolvia
        // LinkedHashMap cru → ClassCastException no acesso. Menor repro da issue.
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SRC_EXEC_RECORD_LIST_FIELD_DECODE);
        runJvm(source, tempDir.resolve("out"), "a\nb\nt");
    }

    // §189: a assinatura genérica de campo RECORD nullable (`List<Item>?`) era
    // omitida (`toGenericSignature` não desembrulhava `NullableType`), então
    // `RecordComponent.getGenericType()` devolvia `ArrayList` cru e o decoder
    // JSON não bindava os elementos → `LinkedHashMap` cru → ClassCastException.
    // O #128 (`76ca3dd4`) subiu com esse teste VERMELHO — a correção é esta
    // unidade. Prova: nullable populado, nullable AUSENTE (null honesto, não
    // crash) e o controle NÃO-nullable (que já funcionava via #34/bug 58).
    @Test
    void execRecordNullableGenericListFieldDecode(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SRC_EXEC_RECORD_NULLABLE_GENERIC_LIST_FIELD_DECODE);
        runJvm(source, tempDir.resolve("out"), "x\ntrue\ny\np");
    }

    @Test
    void execRecordValueMethods(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            record Ponto(Int x, Int y)
            main() {
                var p = Ponto(3, 7)
                println(p)
                var q = Ponto(3, 7)
                println(p == p)
                println(p == q)
                println(p.x() == q.x() && p.y() == q.y())
                println(p.hashCode() == q.hashCode())
            }
            """);
        // p == q é igualdade de CONTEÚDO (record gera equals) — corrigido
        // 03/09 (known-bugs #11); antes era referência (false).
        runJvm(source, tempDir.resolve("out"), "Ponto[x=3, y=7]\ntrue\ntrue\ntrue\ntrue");
    }

    // SG-011 — função aninhada: inner definida primeiro (hoisting para
    // top-level `main__dobro`), outer chama e aguarda o retorno.
    @Test
    void execNestedFunction(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                Int dobro(Int x) {
                    return x * 2
                }
                println(dobro(21))
            }
            """);
        runJvm(source, tempDir.resolve("out"), "42");
    }

    @Test
    void execNestedFunctionWithCondition(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                String classifica(Int n) {
                    if (n >= 10) {
                        return "alto"
                    }
                    return "baixo"
                }
                println(classifica(15))
                println(classifica(3))
            }
            """);
        runJvm(source, tempDir.resolve("out2"), "alto\nbaixo");
    }
}