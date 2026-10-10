package dev.kof.compiler;

/**
 * Programas Kof do E2E do alvo JVM ({@code JvmE2ETest}), hoisted de inline para
 * constantes. Vive fora da classe de teste (Fase 3 da arquitetura de testes,
 * {@code D-TEST-ARCHITECTURE-GO}) e do harness para que ambos fiquem abaixo do
 * limite de 500 linhas; os testes e o nome da classe seguem no
 * {@code JvmE2ETest}.
 */
abstract class JvmPrograms {

    static final String SRC_EXEC_NARROW_PRIMITIVE_ARRAY_ACCESS = """
            main() {
                var b = new Bool[2]
                b[0] = true
                b[1] = false
                println(b[0])
                println(b[1])
                var c = new Char[2]
                c[0] = 'A'
                c[1] = 'B'
                println(c[0])
                println(c[1])
                var s = new Short[2]
                s[0] = 1000
                s[1] = -5
                println(s[0])
                println(s[1])
                var y = new Byte[2]
                y[0] = 7
                y[1] = -8
                println(y[0])
                println(y[1])
                var i = new Int[2]
                i[0] = 42
                i[1] = i[0] + 1
                println(i[1])
            }
            """;

    static final String SRC_EXEC_FUNCTION_DECLARATION_FORMS = """
            String saudacao() {
                return "oi"
            }
            Int soma(Int a, Int b): Int {
                return a + b
            }
            Bool positivo(Int x) = x > 0
            class Usuario {
                String nome
                public constructor(String nome) { this.nome = nome }
                String buscaNomeDeUsuario() {
                    return nome
                }
            }
            main() {
                println(saudacao())
                println(soma(2, 3))
                println(positivo(5))
                var u = new Usuario("Mel")
                println(u.buscaNomeDeUsuario())
            }
            """;

    static final String SRC_EXEC_INHERITANCE = """
            class Animal {
                String name
                public constructor(String name) {
                    this.name = name
                }
                speak(): String = "animal"
            }
            class Dog extends Animal {
                public constructor(String name) {
                    super(name)
                }
                speak(): String = "dog"
            }
            main() {
                var d = new Dog("Rex")
                println(d.speak())
                println(d.name)
            }
            """;

    static final String SRC_EXEC_LIST_RICH_API = """
            main() {
                var l = listOf(1, 2, 3, 4)
                println(l.size)
                println(l.contains(3))
                println(l.contains(99))
                println(l.isEmpty())
                var removed = l.remove(1)
                println(removed)
                println(l.size)
                println(l.get(1))
                l.clear()
                println(l.isEmpty())
                var s = listOf("a", "b")
                println(s.contains("b"))
                var e = listOf<Int>()
                println(e.size)
            }
            """;

    static final String SRC_EXEC_CLASSES = """
            class Counter {
                Int value = 0
                public inc() {
                    value = value + 1
                }
                public get(): Int {
                    return value
                }
            }
            main() {
                var c = new Counter()
                c.inc()
                c.inc()
                c.inc()
                println(c.get())
            }
            """;

    static final String SRC_EXEC_VIRTUAL_DISPATCH = """
            class Animal {
                speak(): String = "animal"
            }
            class Dog extends Animal {
                speak(): String = "dog"
            }
            class Cat extends Animal {
                speak(): String = "cat"
            }
            main() {
                var a = new Dog()
                println(a.speak())
                var b = new Cat()
                println(b.speak())
            }
            """;

    static final String SRC_EXEC_RECORD_NULLABLE_GENERIC_LIST_FIELD_DECODE = """
            record Item(String? name)
            record NullableBox(String? title, List<Item>? items)
            record PlainBox(String? title, List<Item> items)
            main() {
                var n = json.decode<NullableBox>("{\\"title\\":\\"n\\",\\"items\\":[{\\"name\\":\\"x\\"}]}")
                var ni = n.items()
                if (ni != null) {
                    println(ni.get(0).name())
                }
                var absent = json.decode<NullableBox>("{\\"title\\":\\"z\\"}")
                println(absent.items() == null)
                var p = json.decode<PlainBox>("{\\"title\\":\\"p\\",\\"items\\":[{\\"name\\":\\"y\\"}]}")
                println(p.items().get(0).name())
                println(p.title())
            }
            """;

    static final String SRC_EXEC_IF_ELSE = """
            main() {
                var x = 10
                if (x > 5) {
                    println("greater")
                } else {
                    println("smaller")
                }
                var y = 1
                if (y > 5) {
                    println("greater2")
                } else {
                    println("smaller2")
                }
            }
            """;

    static final String SRC_EXEC_LIST_OPERATIONS = """
            main() {
                var l = new List<Int>()
                for (var i = 0; i < 10; i++) {
                    l.add(i)
                }
                var sum = 0
                for (var i = 0; i < l.size; i++) {
                    sum = sum + l.get(i)
                }
                println(sum)
                l.set(0, 100)
                println(l.get(0))
                println(l.size)
            }
            """;

    static final String SRC_EXEC_GENERIC_CLASS = """
            class Box<T> {
                T value
                public constructor(T value) {
                    this.value = value
                }
                get(): T {
                    return value
                }
            }
            main() {
                var b = new Box<Int>(7)
                println(b.get())
            }
            """;

    static final String SRC_EXEC_IF_ELSE_NESTED = """
            main() {
                var x = 7
                if (x > 5) {
                    if (x > 8) {
                        println("high")
                    } else {
                        println("mid")
                    }
                } else {
                    println("low")
                }
            }
            """;

    static final String SRC_EXEC_RECORD_LIST_FIELD_DECODE = """
            record Item(String? name)
            record Container(String? title, List<Item>? items)
            main() {
                var c = json.decode<Container>("{\\"title\\":\\"t\\",\\"items\\":[{\\"name\\":\\"a\\"},{\\"name\\":\\"b\\"}]}")
                var items = c.items()
                if (items != null) {
                    var first = items.get(0)
                    println(first.name())
                    println(items.get(1).name())
                }
                println(c.title())
            }
            """;
}
