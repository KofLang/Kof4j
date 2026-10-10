package dev.kof.compiler;

/**
 * Programas Kof do teste de paridade de backends ({@code BackendParityTest}), hoisted de
 * inline para constantes. Vive fora da classe de teste para mantê-la abaixo do
 * limite de 500 linhas de teste (Fase 3 da arquitetura de testes,
 * {@code D-TEST-ARCHITECTURE-GO}).
 */
abstract class BackendParityPrograms {

    static final String SRC_PARITY_CONTROL_FLOW = """
                main() {
                    var sum = 0
                    for (var i = 0; i < 5; i++) {
                        if (i == 2) {
                            continue
                        }
                        sum = sum + i
                    }
                    println(sum)
                    var i = 0
                    while (i < 3) {
                        i = i + 1
                    }
                    println(i)
                    do {
                        i = i - 1
                    } while (i > 0)
                    println(i)
                }
                """;

    static final String SRC_PARITY_CLASSES_AND_LIST = """
                class User {
                    String name
                    Int age

                    constructor(String name, Int age) {
                        this.name = name
                        this.age = age
                    }
                }

                main() {
                    var users = listOf(User("Mel", 30), User("Kof", 25))
                    for (var i = 0; i < users.size; i++) {
                        println(users.get(i).name)
                        println(users.get(i).age)
                    }
                    println(users.size)
                }
                """;

    static final String SRC_PARITY_COLOR32_BIT = """
                class Color {
                    Int value

                    constructor(Int value) {
                        this.value = value
                    }

                    Int red() { return (this.value >> 16) & 0xFF }
                    Int green() { return (this.value >> 8) & 0xFF }
                    Int blue() { return this.value & 0xFF }
                    Int alpha() { return (this.value >> 24) & 0xFF }
                    String ansi() {
                        return "\\u001b[38;2;" + this.red() + ";" + this.green() + ";" + this.blue() + "m"
                    }
                }

                class Colors {
                    static Int primary = 0xFF6750A4
                    static Int success = 0xFF4CAF50
                }

                main() {
                    var c = Color(Colors.primary)
                    println(c.red())
                    println(c.green())
                    println(c.blue())
                    println(c.alpha())
                    var s = Color(Colors.success)
                    println(s.red())
                    println(s.green())
                    println(s.blue())
                    println(c.ansi() == "\\u001b[38;2;103;80;164m")
                }
                """;

    static final String SRC_PARITY_ARRAY_AND_SWITCH = """
                main() {
                    var arr = new Int[4]
                    arr[0] = 7
                    arr[1] = 3
                    println(arr.length)
                    println(arr[0] + arr[1])
                    var x = 2
                    switch (x) {
                        case 1:
                            println("one")
                        case 2:
                            println("two")
                        default:
                            println("other")
                    }
                }
                """;

    static final String SRC_PARITY_NAN_RELATIONAL_IEEE = """
                Double nan(Double zero) {
                    return zero / zero
                }
                main() {
                    var n = nan(0.0)
                    println(n < 1.0)
                    println(n <= 1.0)
                    println(n > 1.0)
                    println(n >= 1.0)
                    println(n == 1.0)
                    println(n != 1.0)
                    println(n == n)
                    println(n != n)
                    println(1.0 < n)
                    println(1.0 <= n)
                    println(1.0 > n)
                    println(1.0 >= n)
                }
                """;
}
