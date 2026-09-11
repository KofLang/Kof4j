package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class KofValidationTest {
    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void validationJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
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
            """, "ok");
    }

    @Test
    void validationNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
            main() {
                assert(validation.required("hello"))
                assert(!validation.required(""))
                assert(validation.isEmail("a@b.c"))
                assert(!validation.isEmail("a@b"))
                assert(validation.inRange(5, 1, 10))
                println("ok")
            }
            """, "ok");
    }

    @Test
    void validationJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
            main() {
                println(validation.required("hi"))
                println(validation.isUrl("https://kof.dev"))
                println(validation.isEmail("a@b.c"))
                println("done")
            }
            """, "true\ntrue\ntrue\ndone");
    }

    @Test
    void validationBrJvm(@TempDir Path tmp) throws Exception {
        // S5 — CPF/CNPJ/CEP/PIS (vetores derivados em Python, pesos mod-11).
        runJvm(tmp, """
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
            }
            """, "true\ntrue\nfalse\nfalse\nfalse\nfalse\ntrue\nfalse\ntrue\nfalse\ntrue\nfalse");
    }

    @Test
    void validationBrNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
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
                println("ok")
            }
            """, "ok");
    }

    @Test
    void validationBrJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
            main() {
                println(validation.isCpf("529.982.247-25"))
                println(validation.isCpf("111.111.111-11"))
                println(validation.isCnpj("34.546.401/0001-63"))
                println(validation.isCep("01310-100"))
                println(validation.isCep("0131010"))
                println(validation.isPis("123.4567.890-0"))
            }
            """, "true\nfalse\ntrue\ntrue\nfalse\ntrue");
    }

    // S5 cross-arch: é a PRIMEIRA verificação que EXECUTA código runtime
    // riscv/aarch — usa só assert (sem println: bug 59 é no link do
    // System.out), padrão descoberto ao corrigir o bug do .rodata herdado.
    @Test
    void validationBrNativeRiscv(@TempDir Path tmp) throws Exception {
        assumeToolchain("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64");
        runQemu(tmp, Target.NATIVE_RISCV64, "qemu-riscv64", """
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
            }
            """);
    }

    @Test
    void validationBrNativeAarch64(@TempDir Path tmp) throws Exception {
        assumeToolchain("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64");
        runQemu(tmp, Target.NATIVE_AARCH64, "qemu-aarch64", """
            main() {
                assert(validation.isCpf("529.982.247-25"))
                assert(!validation.isCpf("111.111.111-11"))
                assert(validation.isCnpj("11.222.333/0001-81"))
                assert(!validation.isCnpj("11.222.333/0001-82"))
                assert(validation.isCep("01310-100"))
                assert(!validation.isCep("0131010"))
                assert(validation.isPis("123.4567.890-0"))
                assert(!validation.isPis("12345678901"))
            }
            """);
    }

    // S12 formatBr: pontuação BR — 11 dígitos => DDD.DDD.DDD-DD; 8 =>
    // DDDDD-DDDD; null/fora-dos-11-8 => original (no-op, nunca lança).
    // Paridade byte-a-byte nos 5 alvos (dígitos via kof_br_digits já portada).
    @Test
    void formatBrJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
            main() {
                println(validation.formatCpf("52998224725"))
                println(validation.formatCpf("529.982.247-25"))
                println(validation.formatCpf("123") + "|")
                println(validation.formatCpf("529982247254") + "|")
                println(validation.formatCep("01310100"))
                println(validation.formatCep("01310-100"))
                println(validation.formatCep("12") + "|")
                println(validation.formatCep("0131010012") + "|")
            }
            """, "529.982.247-25\n529.982.247-25\n123|\n529982247254|\n01310-100\n01310-100\n12|\n0131010012|");
    }

    @Test
    void formatBrNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
            main() {
                assert(validation.formatCpf("52998224725") == "529.982.247-25")
                assert(validation.formatCpf("529.982.247-25") == "529.982.247-25")
                assert(validation.formatCpf("123") == "123")
                assert(validation.formatCpf("") == "")
                assert(validation.formatCep("01310100") == "01310-100")
                assert(validation.formatCep("01310-100") == "01310-100")
                assert(validation.formatCep("12") == "12")
                println("ok")
            }
            """, "ok");
    }

    @Test
    void formatBrJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
            main() {
                println(validation.formatCpf("52998224725"))
                println(validation.formatCpf("123") + "|")
                println(validation.formatCep("01310100"))
                println(validation.formatCep("12") + "|")
            }
            """, "529.982.247-25\n123|\n01310-100\n12|");
    }

    @Test
    void formatBrNativeRiscv(@TempDir Path tmp) throws Exception {
        assumeToolchain("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64");
        runQemu(tmp, Target.NATIVE_RISCV64, "qemu-riscv64", """
            main() {
                assert(validation.formatCpf("52998224725") == "529.982.247-25")
                assert(validation.formatCpf("123") == "123")
                assert(validation.formatCpf("") == "")
                assert(validation.formatCep("01310100") == "01310-100")
                assert(validation.formatCep("12") == "12")
            }
            """);
    }

    @Test
    void formatBrNativeAarch64(@TempDir Path tmp) throws Exception {
        assumeToolchain("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64");
        runQemu(tmp, Target.NATIVE_AARCH64, "qemu-aarch64", """
            main() {
                assert(validation.formatCpf("52998224725") == "529.982.247-25")
                assert(validation.formatCpf("123") == "123")
                assert(validation.formatCep("01310100") == "01310-100")
                assert(validation.formatCep("12") == "12")
            }
            """);
    }

    // S12b formatCnpj: 14 dígitos => NN.NNN.NNN/NNNN-NN (canônico IBGE);
    // senão original (no-op, nunca lança). Paridade byte-a-byte nos 5 alvos.
    @Test
    void formatCnpjJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
            main() {
                println(validation.formatCnpj("34546401000163"))
                println(validation.formatCnpj("34.546.401/0001-63"))
                println(validation.formatCnpj("11222333000181"))
                println(validation.formatCnpj("123") + "|")
                println(validation.formatCnpj("") + "|")
                println(validation.formatCnpj("3454640100016") + "|")
            }
            """, "34.546.401/0001-63\n34.546.401/0001-63\n11.222.333/0001-81\n123|\n|\n3454640100016|");
    }

    @Test
    void formatCnpjNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
            main() {
                assert(validation.formatCnpj("34546401000163") == "34.546.401/0001-63")
                assert(validation.formatCnpj("11222333000181") == "11.222.333/0001-81")
                assert(validation.formatCnpj("34.546.401/0001-63") == "34.546.401/0001-63")
                assert(validation.formatCnpj("123") == "123")
                assert(validation.formatCnpj("") == "")
                println("ok")
            }
            """, "ok");
    }

    @Test
    void formatCnpjJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
            main() {
                println(validation.formatCnpj("34546401000163"))
                println(validation.formatCnpj("123") + "|")
                println(validation.formatCnpj("") + "|")
            }
            """, "34.546.401/0001-63\n123|\n|");
    }

    @Test
    void formatCnpjNativeRiscv(@TempDir Path tmp) throws Exception {
        assumeToolchain("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64");
        runQemu(tmp, Target.NATIVE_RISCV64, "qemu-riscv64", """
            main() {
                assert(validation.formatCnpj("34546401000163") == "34.546.401/0001-63")
                assert(validation.formatCnpj("123") == "123")
                assert(validation.formatCnpj("") == "")
            }
            """);
    }

    @Test
    void formatCnpjNativeAarch64(@TempDir Path tmp) throws Exception {
        assumeToolchain("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64");
        runQemu(tmp, Target.NATIVE_AARCH64, "qemu-aarch64", """
            main() {
                assert(validation.formatCnpj("34546401000163") == "34.546.401/0001-63")
                assert(validation.formatCnpj("123") == "123")
            }
            """);
    }

    // S6a network: isIpv4/isMac/isPort — byte-scan, sem gate nos 4 targets.
    @Test
    void validationNetJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
            main() {
                println(validation.isIpv4("0.0.0.0"))
                println(validation.isIpv4("255.255.255.255"))
                println(validation.isIpv4("192.168.0.1"))
                println(validation.isIpv4("256.1.1.1"))
                println(validation.isIpv4("01.2.3.4"))
                println(validation.isIpv4("1.2.3."))
                println(validation.isMac("00:1A:2B:3C:4D:5E"))
                println(validation.isMac("00-1a-2b-3c-4d-5e"))
                println(validation.isMac("GG:1A:2B:3C:4D:5E"))
                println(validation.isMac("00:1A:2B-3C:4D:5E"))
                println(validation.isPort(0))
                println(validation.isPort(443))
                println(validation.isPort(65535))
                println(validation.isPort(65536))
            }
            """, "true\ntrue\ntrue\nfalse\nfalse\nfalse\ntrue\ntrue\nfalse\nfalse\nfalse\ntrue\ntrue\nfalse");
    }

    @Test
    void validationNetNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
            main() {
                println(validation.isIpv4("10.0.0.255"))
                println(validation.isIpv4("0.0.0.00"))
                println(validation.isMac("ff:ff:ff:ff:ff:ff"))
                println(validation.isMac("1:2:3:4:5:6"))
                println(validation.isPort(22))
                println(validation.isPort(-1))
            }
            """, "true\nfalse\ntrue\nfalse\ntrue\nfalse");
    }

    @Test
    void validationNetJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
            main() {
                println(validation.isIpv4("192.168.1.1"))
                println(validation.isIpv4("999.1.1.1"))
                println(validation.isMac("00:1A:2B:3C:4D:5E"))
                println(validation.isPort(80))
                println(validation.isPort(0))
            }
            """, "true\nfalse\ntrue\ntrue\nfalse");
    }

    @Test
    void validationNetCrossArch(@TempDir Path tmp) throws Exception {
        String src = """
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
        assumeToolchain("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64");
        runQemu(tmp, Target.NATIVE_RISCV64, "qemu-riscv64", src);
        assumeToolchain("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64");
        runQemu(tmp, Target.NATIVE_AARCH64, "qemu-aarch64", src);
    }

    // S6b.3 IPv6: subconjunto RFC 5952 (sem forma mista/zona). Máquina
    // validada em Python contra ipaddress (30 casos) + x86==JVM==JS==riscv==aarch.
    @Test
    void validationIpv6Jvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
            main() {
                println(validation.isIpv6("::"))
                println(validation.isIpv6("::1"))
                println(validation.isIpv6("2001:0db8:85a3:0000:0000:8a2e:0370:7334"))
                println(validation.isIpv6("fe80::1"))
                println(validation.isIpv6("1:2:3:4:5:6:7:8"))
                println(validation.isIpv6("12345::"))
                println(validation.isIpv6("2001:db8:::1"))
                println(validation.isIpv6("1::2::3"))
                println(validation.isIpv6("::ffff:192.168.0.1"))
                println(validation.isIpv6("1:"))
                println(validation.isIpv6("g::1"))
            }
            """, "true\ntrue\ntrue\ntrue\ntrue\nfalse\nfalse\nfalse\nfalse\nfalse\nfalse");
    }

    @Test
    void validationIpv6Native(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
            main() {
                println(validation.isIpv6("abcd:ef01::"))
                println(validation.isIpv6("2001:db8:0:0:1:0:0:1"))
                println(validation.isIpv6("ABCDEF::10"))
                println(validation.isIpv6(":1"))
            }
            """, "true\ntrue\nfalse\nfalse");
    }

    @Test
    void validationIpv6Js(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
            main() {
                println(validation.isIpv6("fe80::"))
                println(validation.isIpv6("1:2:3:4:5:6:7"))
                println(validation.isIpv6("::"))
            }
            """, "true\nfalse\ntrue");
    }

    @Test
    void validationIpv6CrossArch(@TempDir Path tmp) throws Exception {
        String src = """
            main() {
                assert(validation.isIpv6("::"))
                assert(validation.isIpv6("::1"))
                assert(validation.isIpv6("2001:0db8:85a3:0000:0000:8a2e:0370:7334"))
                assert(validation.isIpv6("fe80::1"))
                assert(validation.isIpv6("abcd:ef01::"))
                assert(validation.isIpv6("a:b:c:d:e:f:1:2"))
                assert(!validation.isIpv6("12345::"))
                assert(!validation.isIpv6("2001:db8:::1"))
                assert(!validation.isIpv6("1::2::3"))
                assert(!validation.isIpv6("::ffff:192.168.0.1"))
                assert(!validation.isIpv6("1:"))
                assert(!validation.isIpv6(":1"))
                assert(!validation.isIpv6("g::1"))
                assert(!validation.isIpv6("ABCDEF::10"))
            }
            """;
        assumeToolchain("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64");
        runQemu(tmp, Target.NATIVE_RISCV64, "qemu-riscv64", src);
        assumeToolchain("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64");
        runQemu(tmp, Target.NATIVE_AARCH64, "qemu-aarch64", src);
    }

    // S6c domínio: subconjunto RFC 1123 declarado (oracle Python 28 casos;
    // x86==JVM==JS==riscv==aarch no diff dos 28).
    @Test
    void validationDomainJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
            main() {
                println(validation.isDomain("example.com"))
                println(validation.isDomain("sub.example.co.uk"))
                println(validation.isDomain("EXAMPLE.COM"))
                println(validation.isDomain("example.c"))
                println(validation.isDomain("example.com."))
                println(validation.isDomain("-example.com"))
                println(validation.isDomain("ex_ample.com"))
                println(validation.isDomain("localhost"))
                println(validation.isDomain("192.168.0.1"))
                println(validation.isDomain("xn--mnchen-3ya.de"))
                println(validation.isDomain("example..com"))
                println(validation.isDomain("a.aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.com"))
            }
            """, "true\ntrue\ntrue\nfalse\nfalse\nfalse\nfalse\nfalse\nfalse\ntrue\nfalse\nfalse");
    }

    @Test
    void validationDomainNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
            main() {
                println(validation.isDomain("k.de"))
                println(validation.isDomain("user@example.com"))
                println(validation.isDomain("ex-ample.com"))
                println(validation.isDomain("888.com"))
                println(validation.isDomain("x.x"))
            }
            """, "true\nfalse\ntrue\ntrue\nfalse");
    }

    @Test
    void validationDomainJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
            main() {
                println(validation.isDomain("a.io"))
                println(validation.isDomain("example.123"))
                println(validation.isDomain("co.uk"))
                println(validation.isDomain(""))
            }
            """, "true\nfalse\ntrue\nfalse");
    }

    @Test
    void validationDomainCrossArch(@TempDir Path tmp) throws Exception {
        String src = """
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
        assumeToolchain("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64");
        runQemu(tmp, Target.NATIVE_RISCV64, "qemu-riscv64", src);
        assumeToolchain("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64");
        runQemu(tmp, Target.NATIVE_AARCH64, "qemu-aarch64", src);
    }

    // S6b Luhn: isCreditCard — dígitos extraídos, 12..19, soma de Luhn %10.
    @Test
    void validationLuhnJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
            main() {
                println(validation.isCreditCard("4111111111111111"))
                println(validation.isCreditCard("4532 0151 1283 0366"))
                println(validation.isCreditCard("378282246310005"))
                println(validation.isCreditCard("6011-0000-0000-0004"))
                println(validation.isCreditCard("4111111111111112"))
                println(validation.isCreditCard("45"))
                println(validation.isCreditCard(""))
                println(validation.isCreditCard("1234567890123456789"))
            }
            """, "true\ntrue\ntrue\ntrue\nfalse\nfalse\nfalse\nfalse");
    }

    @Test
    void validationLuhnNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
            main() {
                println(validation.isCreditCard("4111111111111111"))
                println(validation.isCreditCard("card 4111 1111 1111 1111 ok"))
                println(validation.isCreditCard("4111111111111112"))
                println(validation.isCreditCard("1234567890123456789"))
            }
            """, "true\ntrue\nfalse\nfalse");
    }

    @Test
    void validationLuhnJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
            main() {
                println(validation.isCreditCard("5500 0000 0000 0004"))
                println(validation.isCreditCard("4111111111111112"))
                println(validation.isCreditCard("45"))
            }
            """, "true\nfalse\nfalse");
    }

    @Test
    void validationLuhnCrossArch(@TempDir Path tmp) throws Exception {
        String src = """
            main() {
                assert(validation.isCreditCard("4111111111111111"))
                assert(validation.isCreditCard("4532 0151 1283 0366"))
                assert(validation.isCreditCard("378282246310005"))
                assert(validation.isCreditCard("6011-0000-0000-0004"))
                assert(validation.isCreditCard("card 4111 1111 1111 1111 ok"))
                assert(!validation.isCreditCard("4111111111111112"))
                assert(!validation.isCreditCard("45"))
                assert(!validation.isCreditCard(""))
                assert(!validation.isCreditCard("1234567890123456789"))
            }
            """;
        assumeToolchain("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64");
        runQemu(tmp, Target.NATIVE_RISCV64, "qemu-riscv64", src);
        assumeToolchain("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64");
        runQemu(tmp, Target.NATIVE_AARCH64, "qemu-aarch64", src);
    }

    private static void assumeToolchain(String... bins) {
        for (String b : bins) {
            try {
                Process p = new ProcessBuilder(b, "--version")
                        .redirectOutput(new java.io.File("/dev/null"))
                        .redirectErrorStream(true).start();
                org.junit.jupiter.api.Assumptions.assumeTrue(p.waitFor() == 0,
                        b + " ausente — pulando (NATIVE002)");
            } catch (Exception e) {
                org.junit.jupiter.api.Assumptions.assumeTrue(false, b + " ausente — pulando");
            }
        }
    }

    private void runQemu(Path tempDir, Target target, String qemu, String source) throws Exception {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, target);
        assertTrue(result.success(), target + " compile failed: "
                + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        Process p = new ProcessBuilder(qemu, bin.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        int ec = p.waitFor();
        assertEquals(0, ec, target + " runtime (qemu) exit " + ec + ", out: " + output);
    }

    private String runJvm(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        try {
            Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                    "-cp", outDir.toString(), "Default.Main").redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code, output: " + output);
            if (expected != null) assertEquals(expected, output, "JVM output");
            return output;
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }

    private String runNative(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.NATIVE);
        assertTrue(result.success(), "Native compile failed: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        try {
            Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Native exit code, output: " + output);
            if (expected != null) assertEquals(expected, output, "Native output");
            return output;
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }

    private String runJs(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JS);
        assertTrue(result.success(), "JS compile failed: " + result.diagnostics().getDiagnostics());
        try (java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream()) {
            int ec = dev.kof.runtime.KofJsRunner.run(findJsEntry(outDir), buf,
                    java.io.InputStream.nullInputStream(), new java.io.ByteArrayOutputStream());
            String output = buf.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
            assertEquals(0, ec, "JS exit code, output: " + output);
            if (expected != null) assertEquals(expected, output, "JS output");
            return output;
        }
    }

    private static Path findJsEntry(Path dir) throws java.io.IOException {
        try (var s = Files.walk(dir)) {
            var opt = s.filter(p -> p.getFileName().toString().equals("Default.mjs")).findFirst();
            if (opt.isPresent()) return opt.get();
        }
        try (var s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".mjs"))
                    .findFirst().orElseThrow(() -> new java.io.IOException("no .mjs in " + dir));
        }
    }
}
