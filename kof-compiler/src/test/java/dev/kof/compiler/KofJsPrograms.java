package dev.kof.compiler;

/**
 * Programas Kof do E2E de KofJS ({@code KofJsE2ETest}), hoisted de inline para
 * constantes. Vive fora da classe de teste (Fase 3 da arquitetura de testes,
 * {@code D-TEST-ARCHITECTURE-GO}) e do harness para que ambos fiquem abaixo do
 * limite de 500 linhas; os testes e o nome da classe seguem no {@code KofJsE2ETest}.
 */
abstract class KofJsPrograms {

    static final String SRC_EXEC_VARIABLES = """
            main() {
                Int x = 10
                String name = "Mel"
                Bool active = true
                var y = x * 2
                println(x)
                println(name)
                println(active)
                println(y)
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

    static final String SRC_EXEC_BOOLEAN_CONDITIONS = """
            main() {
                var a = true
                var b = false
                if (a) {
                    println("a")
                }
                if (a && b) {
                    println("both")
                }
                if (a || b) {
                    println("either")
                }
                if (!b) {
                    println("not b")
                }
                println(a == b)
                println(a != b)
            }
            """;

    static final String SRC_EXEC_WHILE_LOOP = """
            main() {
                var i = 0
                var sum = 0
                while (i < 5) {
                    sum = sum + i
                    i = i + 1
                }
                println(sum)
            }
            """;

    static final String SRC_EXEC_BREAK_CONTINUE = """
            main() {
                var i = 0
                while (true) {
                    i = i + 1
                    if (i == 2) {
                        continue
                    }
                    if (i > 4) {
                        break
                    }
                    println(i)
                }
            }
            """;

    static final String SRC_EXEC_FUNCTIONS = """
            Int add(Int a, Int b) {
                return a + b
            }

            String shout(String s) {
                return s + "!"
            }

            main() {
                println(add(2, 3))
                println(shout("hey"))
                println(add(add(1, 2), add(3, 4)))
            }
            """;

    static final String SRC_EXEC_RECURSION = """
            Int factorial(Int n) {
                if (n <= 1) {
                    return 1
                }
                return n * factorial(n - 1)
            }

            main() {
                println(factorial(5))
            }
            """;

    static final String SRC_EXEC_CLASSES = """
            class User {
                String name
                Int age

                constructor(String name, Int age) {
                    this.name = name
                    this.age = age
                }

                String greeting() {
                    return "Hello " + this.name
                }
            }

            main() {
                var u = User("Mel", 30)
                println(u.greeting())
                println(u.age)
            }
            """;

    static final String SRC_EXEC_CLASS_FIELDS = """
            class Counter {
                Int count

                void increment() {
                    this.count = this.count + 1
                }
            }

            main() {
                var c = Counter()
                c.increment()
                c.increment()
                println(c.count)
            }
            """;

    static final String SRC_EXEC_CONSTRUCTORS = """
            class Point {
                Int x
                Int y

                constructor(Int x, Int y) {
                    this.x = x
                    this.y = y
                }
            }

            main() {
                var p = Point(3, 4)
                println(p.x)
                println(p.y)
            }
            """;

    static final String SRC_RECORD_WITH_EXPLICIT_CONSTRUCTOR_RUNS_ON_JS = """
            record Q(Int x) {
                constructor(Int x) {
                    this.x = x
                }
            }
            main() {
                println(Q(1).x())
            }
            """;

    static final String SRC_EXEC_INHERITANCE = """
            class Animal {
                String name

                constructor(String name) {
                    this.name = name
                }

                String speak() {
                    return "..."
                }
            }

            class Dog extends Animal {
                constructor(String name) {
                    super(name)
                }

                String speak() {
                    return "Au au"
                }
            }

            main() {
                var a = Animal("bicho")
                var d = Dog("Rex")
                println(a.speak())
                println(d.speak())
                println(d.name)
            }
            """;

    static final String SRC_EXEC_INTERFACES = """
            interface Greeter {
                String greet()
            }

            class Person implements Greeter {
                String name

                constructor(String name) {
                    this.name = name
                }

                String greet() {
                    return "Hi " + this.name
                }
            }

            main() {
                var g = Person("Mel")
                println(g.greet())
            }
            """;

    static final String SRC_EXEC_GENERICS = """
            List<Int> ints() {
                return listOf(1, 2, 3)
            }

            main() {
                var xs = ints()
                println(xs.size)
                println(xs.get(1))
            }
            """;

    static final String SRC_EXEC_LIST = """
            main() {
                var users = listOf("Mel", "Kof")

                println(users.get(0))
                println(users.size)

                users.add("Kotlin")
                println(users.size)
                println(users.contains("Kof"))
                println(users.contains("Java"))
                println(users.isEmpty())

                users.set(1, "Kof2")
                println(users.get(1))

                users.remove(0)
                println(users.size)

                users.clear()
                println(users.isEmpty())
            }
            """;

    static final String SRC_EXEC_STRING_API = """
            main() {
                var s = "Hello World"

                println(s.length)
                println(s.toUpperCase())
                println(s.toLowerCase())
                println(s.substring(6))
                println(s.substring(0, 5))
                println(s.indexOf("World"))
                println(s.contains("ello"))
                println(s.startsWith("He"))
                println(s.endsWith("ld"))
                println(s.replace('l', 'L'))
                println(s.trim())
                println("a" + "b" + 1)
                println("abc" == "abc")
                println("abc" != "abd")
                println(s.charAt(1))
                var parts = s.split(" ")
                println(parts.length)
                println(parts[1])
            }
            """;

    static final String SRC_EXEC_STRING_API_2 = """
            11
            HELLO WORLD
            hello world
            World
            Hello
            6
            true
            true
            true
            HeLLo WorLd
            Hello World
            ab1
            true
            true
            e
            2
            World""";

    static final String SRC_EXEC_STRING_TO_NUMBER_CONVERSION = """
            main() {
                println("120000".toInt())
                println("7".toLong())
                println("2.5".toDouble())
                println("-12".toInt())
                try {
                    println("abc".toInt())
                } catch (String e) {
                    println("ERR")
                }
            }
            """;

    static final String SRC_EXEC_ARRAYS = """
            main() {
                var arr = new Int[5]
                arr[0] = 10
                arr[1] = 20
                println(arr.length)
                println(arr[0])
                println(arr[1])
                println(arr[4])
            }
            """;

    static final String SRC_EXEC_JSON = """
            main() {
                println(json.encode(42))
                println(json.encode("text"))
                println(json.encode(true))
                println(json.encode(listOf(1, 2, 3)))
                println(json.encode(listOf("a", "b")))

                var n = json.decode<Int>("123")
                println(n + 1)
                var s = json.decode<String>("\\"ok\\"")
                println(s)
                var xs = json.decode<List<Int>>("[10, 20]")
                println(xs.get(0))
            }
            """;

    static final String SRC_EXEC_JSON_OBJECTS = """
            class User(
                String name
            )

            main() {
                var users = listOf(User("Mel"), User("Kof"))
                println(json.encode(users))
                var u = json.decode<User>("{\\"name\\":\\"Mel\\"}")
                println(u.name)
            }
            """;

    static final String SRC_EXEC_TRY_CATCH_FINALLY = """
            main() {
                try {
                    throw "x"
                } catch (String e) {
                    println("caught")
                } finally {
                    println("finally")
                }
                println("end")
            }
            """;

    static final String SRC_LOGICAL_AND_OR_SHORT_CIRCUIT = """
            Int f() {
                println("f-rodou")
                return 1
            }
            main() {
                if (false && f() > 0) {
                    println("x")
                }
                if (true || f() > 0) {
                    println("y")
                }
                var r = false && f() > 0
                println(r)
            }
            """;
}
