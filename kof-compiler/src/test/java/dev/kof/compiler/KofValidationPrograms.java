package dev.kof.compiler;

/**
 * Programas Kof do E2E de {@code kof.validation} ({@code KofValidationTest}),
 * hoisted de inline para constantes. Vive fora da classe de teste (Fase 3 da
 * arquitetura de testes, {@code D-TEST-ARCHITECTURE-GO}) e do harness para que
 * ambos fiquem abaixo do limite de 500 linhas; os testes e o nome da classe
 * seguem no {@code KofValidationTest}.
 */
abstract class KofValidationPrograms {

    static final String SRC_VALIDATION_JVM = """
            main() {
                assert(validation.required("hello"))
                assert(!validation.required(""))
                assert(!validation.notBlank("  "))
                assert(validation.notBlank(" a "))
                assert(validation.minLength("abc", 2))
                assert(!validation.minLength("a", 2))
                assert(validation.maxLength("abc", 5))
                assert(!validation.maxLength("abc", 2))
                assert(validation.lengthBetween("abcd", 2, 5))
                assert(!validation.lengthBetween("a", 2, 5))
                assert(validation.isEmail("test@example.com"))
                assert(!validation.isEmail("bad-email"))
                assert(validation.isUrl("https://example.com"))
                assert(!validation.isUrl("ftp://x"))
                assert(validation.matches("hello", "ell"))
                assert(!validation.matches("hello", "xyz"))
                assert(validation.isInt("123"))
                assert(!validation.isInt("12a"))
                assert(validation.inRange(5, 1, 10))
                assert(!validation.inRange(15, 1, 10))
                assert(validation.min(5, 3))
                assert(!validation.min(2, 3))
                assert(validation.max(5, 10))
                assert(!validation.max(15, 10))
                println("ok")
            }
            """;

    static final String SRC_VALIDATION_BR_JVM = """
            main() {
                println(validation.isCpf("529.982.247-25"))
                println(validation.isCpf("52996581504"))
                println(validation.isCpf("111.111.111-11"))
                println(validation.isCpf("529.982.247-24"))
                println(validation.isCpf("00000000000"))
                println(validation.isCpf(""))
                println(validation.isCnpj("34.546.401/0001-63"))
                println(validation.isCnpj("11.222.333/0001-82"))
                println(validation.isCep("01310-100"))
                println(validation.isCep("0131010"))
                println(validation.isPis("123.4567.890-0"))
                println(validation.isPis("12345678901"))
                // S12c: NIS — MESMO checksum mod-11 do PIS (reuso 1:1).
                println(validation.isNis("12056412278"))
                println(validation.isNis("120.5641.227-8"))
                println(validation.isNis("12056412279"))
                println(validation.isNis("12345678901"))
                println(validation.isNis(""))
            }
            """;

    static final String SRC_VALIDATION_BR_NATIVE = """
            main() {
                assert(validation.isCpf("529.982.247-25"))
                assert(validation.isCpf("52996581504"))
                assert(!validation.isCpf("111.111.111-11"))
                assert(!validation.isCpf("529.982.247-24"))
                assert(!validation.isCpf("00000000000"))
                assert(!validation.isCpf(""))
                assert(validation.isCnpj("34.546.401/0001-63"))
                assert(validation.isCnpj("11.222.333/0001-81"))
                assert(!validation.isCnpj("11.222.333/0001-82"))
                assert(validation.isCep("01310-100"))
                assert(!validation.isCep("0131010"))
                assert(validation.isPis("123.4567.890-0"))
                assert(!validation.isPis("12345678901"))
                // S12c: NIS
                assert(validation.isNis("12056412278"))
                assert(!validation.isNis("12056412279"))
                assert(!validation.isNis(""))
                println("ok")
            }
            """;

    static final String SRC_VALIDATION_DOMAIN_CROSS_ARCH = """
            main() {
                assert(validation.isDomain("example.com"))
                assert(validation.isDomain("sub.example.co.uk"))
                assert(validation.isDomain("EXAMPLE.COM"))
                assert(validation.isDomain("xn--mnchen-3ya.de"))
                assert(validation.isDomain("k.de"))
                assert(validation.isDomain("ex-ample.com"))
                assert(validation.isDomain("888.com"))
                assert(!validation.isDomain("example.c"))
                assert(!validation.isDomain("example.com."))
                assert(!validation.isDomain("-example.com"))
                assert(!validation.isDomain("example-.com"))
                assert(!validation.isDomain("ex_ample.com"))
                assert(!validation.isDomain("localhost"))
                assert(!validation.isDomain("192.168.0.1"))
                assert(!validation.isDomain("example..com"))
                assert(!validation.isDomain(".example.com"))
                assert(!validation.isDomain("x.x"))
                assert(!validation.isDomain(""))
            }
            """;

    static final String SRC_VALIDATION_BR_NATIVE_RISCV = """
            main() {
                assert(validation.isCpf("529.982.247-25"))
                assert(validation.isCpf("52996581504"))
                assert(!validation.isCpf("111.111.111-11"))
                assert(!validation.isCpf("529.982.247-24"))
                assert(!validation.isCpf("00000000000"))
                assert(!validation.isCpf(""))
                assert(validation.isCnpj("34.546.401/0001-63"))
                assert(validation.isCnpj("11.222.333/0001-81"))
                assert(!validation.isCnpj("11.222.333/0001-82"))
                assert(validation.isCep("01310-100"))
                assert(!validation.isCep("0131010"))
                assert(validation.isPis("123.4567.890-0"))
                assert(!validation.isPis("12345678901"))
                // S12c: NIS (tail-jmp pro isPis — mesmo mod-11)
                assert(validation.isNis("12056412278"))
                assert(!validation.isNis("12056412279"))
                assert(!validation.isNis(""))
            }
            """;

    static final String SRC_VALIDATION_NET_CROSS_ARCH = """
            main() {
                assert(validation.isIpv4("0.0.0.0"))
                assert(validation.isIpv4("255.255.255.255"))
                assert(validation.isIpv4("10.0.0.255"))
                assert(!validation.isIpv4("256.1.1.1"))
                assert(!validation.isIpv4("01.2.3.4"))
                assert(!validation.isIpv4("1.2.3."))
                assert(!validation.isIpv4(""))
                assert(validation.isMac("00:1A:2B:3C:4D:5E"))
                assert(validation.isMac("00-1a-2b-3c-4d-5e"))
                assert(!validation.isMac("GG:1A:2B:3C:4D:5E"))
                assert(!validation.isMac("00:1A:2B-3C:4D:5E"))
                assert(!validation.isMac("1:2:3:4:5:6"))
                assert(!validation.isPort(0))
                assert(validation.isPort(22))
                assert(validation.isPort(65535))
                assert(!validation.isPort(65536))
                assert(!validation.isPort(-1))
            }
            """;
}
