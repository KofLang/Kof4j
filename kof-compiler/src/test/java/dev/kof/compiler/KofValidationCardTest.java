package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * STDLIB S12c — validation.creditCardBrand / validation.last4 (cartao de
 * credito): extração de digitos + Luhn NAS MESMAS regras do isCreditCard
 * (S6b). Marca exige Luhn valido e 13..19 digitos; prefixos Visa 4 /
 * Mastercard 51..55 / Amex 34,37 / Discover 6011,65; invalido ou sem marca
 * conhecida => "". last4: ultimos 4 digitos (>=4, sem exigir Luhn);
 * <4 ou >19 => "". JVM/JS/Native-x86/riscv64/aarch64 (tradutor) — paridade
 * por composicao das politicas provadas de S6b.
 */
class KofValidationCardTest extends KofValidationSupport {

    // S12c Luhn-adjacentes: creditCardBrand/last4 — mesma extração e regras do
    // isCreditCard (12..19 digitos; >19 invalido); marca exige Luhn + 13..19;
    // prefixos Visa 4 / MC 51..55 / Amex 34,37 / Discover 6011,65; senão "".
    private static final String BRAND_LAST4_SRC = """
        main() {
            println(validation.creditCardBrand("4111111111111111"))
            println(validation.creditCardBrand("5500 0000 0000 0004"))
            println(validation.creditCardBrand("378282246310005"))
            println(validation.creditCardBrand("6011-0000-0000-0004"))
            println(validation.creditCardBrand("6500000000000002"))
            println(validation.creditCardBrand("4222222222222"))
            println(validation.creditCardBrand("9411111111111117"))
            println(validation.creditCardBrand("4111111111111112"))
            println(validation.creditCardBrand("45"))
            println(validation.creditCardBrand("card 4111 1111 1111 1111 ok"))
            println(validation.last4("4111111111111111"))
            println(validation.last4("378282246310005"))
            println(validation.last4("abcd 5500 0000 0000 0004 xyz"))
            println(validation.last4("123"))
            println(validation.last4("11111111111111111111"))
            println(validation.last4("1234567890123456789"))
        }
        """;

    private static final String BRAND_LAST4_OUT = """
        Visa
        Mastercard
        Amex
        Discover
        Discover
        Visa

        

        Visa
        1111
        0005
        0004
        
        
        6789""";

    @Test
    void validationBrandLast4Jvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, BRAND_LAST4_SRC, BRAND_LAST4_OUT);
    }

    @Test
    void validationBrandLast4Js(@TempDir Path tmp) throws Exception {
        runJs(tmp, BRAND_LAST4_SRC, BRAND_LAST4_OUT);
    }

    @Test
    void validationBrandLast4Native(@TempDir Path tmp) throws Exception {
        runNative(tmp, BRAND_LAST4_SRC, BRAND_LAST4_OUT);
    }

    @Test
    void validationBrandLast4CrossArch(@TempDir Path tmp) throws Exception {
        String src = """
            main() {
                assert(validation.creditCardBrand("4111111111111111") == "Visa")
                assert(validation.creditCardBrand("5500 0000 0000 0004") == "Mastercard")
                assert(validation.creditCardBrand("378282246310005") == "Amex")
                assert(validation.creditCardBrand("6011-0000-0000-0004") == "Discover")
                assert(validation.creditCardBrand("6500000000000002") == "Discover")
                assert(validation.creditCardBrand("4222222222222") == "Visa")
                assert(validation.creditCardBrand("9411111111111117") == "")
                assert(validation.creditCardBrand("4111111111111112") == "")
                assert(validation.creditCardBrand("45") == "")
                assert(validation.creditCardBrand("card 4111 1111 1111 1111 ok") == "Visa")
                assert(validation.last4("4111111111111111") == "1111")
                assert(validation.last4("378282246310005") == "0005")
                assert(validation.last4("abcd 5500 0000 0000 0004 xyz") == "0004")
                assert(validation.last4("123") == "")
                assert(validation.last4("11111111111111111111") == "")
                assert(validation.last4("1234567890123456789") == "6789")
            }
            """;
        assumeToolchain("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64");
        runQemu(tmp, Target.NATIVE_RISCV64, "qemu-riscv64", src);
        assumeToolchain("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64");
        runQemu(tmp, Target.NATIVE_AARCH64, "qemu-aarch64", src);
    }
}
