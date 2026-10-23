package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KofFormatterLambdaParamTest {

    @Test
    void untypedLambdaParametersStayUntyped() {
        String src = """
                main() {
                    var xs = listOf(1, 2, 3)
                    println(xs.map((x) -> x * 10))
                    println(xs.reduce(0, (acc, x) -> acc + x))
                }
                """;
        String out = KofFormatter.format(src, "Main.kf");
        assertEquals(src, out);
        assertFalse(out.contains("Object"), out);
    }

    @Test
    void typedLambdaParametersKeepTheirType() {
        String src = """
                main() {
                    var xs = listOf(1, 2, 3)
                    println(xs.map((x: Int) -> x * 10))
                }
                """;
        assertEquals(src, KofFormatter.format(src, "Main.kf"));
    }
}
