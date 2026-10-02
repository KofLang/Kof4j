package dev.kof.c;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Fatia C2 ({@code docs/development/kof-c-cross.md}): parâmetros, retorno,
 * locais e chamadas emitidos nas três ISAs com a ABI C. Oráculo = x86_64 do
 * host; as archs cross concordam com o mesmo golden sob qemu. Onde o texto é
 * construído, o golden vem de medição real (REGRA 5), nunca de memória.
 */
class KofCParamsCompilerTest extends KofCGoldenSupport {

    @Override
    protected List<Prog> programs() {
        return PROGRAMS;
    }

    private static final List<Prog> PROGRAMS = List.of(
            new Prog("two integer parameters", """
                    int add(int a, int b) { return a + b; }
                    void main() {
                      print_arg = add(2, 3);
                      print();
                      print_arg = add(20, 22);
                      print();
                    }
                    """, "5\n42"),
            new Prog("local + loop + early return value", """
                    int sum(int n) {
                      int acc;
                      acc = 0;
                      while(n > 0) { acc = acc + n; n = n - 1; }
                      return acc;
                    }
                    void main() { print_arg = sum(5); print(); }
                    """, "15"),
            new Prog("void function mutating a global", """
                    int g;
                    void setg(int a, int b) { g = a + b; }
                    void main() { setg(4, 5); print_arg = g; print(); }
                    """, "9"),
            new Prog("nested call as an argument", """
                    int add(int a, int b) { return a + b; }
                    void main() { print_arg = add(add(1, 2), add(3, 4)); print(); }
                    """, "10"),
            new Prog("early return short-circuits", """
                    int clamp(int v) {
                      if(v > 10) { return 10; }
                      return v;
                    }
                    void main() {
                      print_arg = clamp(3);
                      print();
                      print_arg = clamp(20);
                      print();
                    }
                    """, "3\n10"),
            new Prog("pointer parameter dereferenced in the callee", """
                    int x;
                    void setp(int p) { *(int*)p = 77; }
                    void main() { x = 1; setp(&x); print_arg = x; print(); }
                    """, "77"),
            new Prog("all six argument registers", """
                    int first(int a, int b, int c, int d, int e, int f) { return a; }
                    int last(int a, int b, int c, int d, int e, int f) { return f; }
                    void main() {
                      print_arg = first(1, 2, 3, 4, 5, 6);
                      print();
                      print_arg = last(1, 2, 3, 4, 5, 6);
                      print();
                    }
                    """, "1\n6"));

    @Test
    void unknownCallIsAHonestDiagnostic(@TempDir Path tmp) throws Exception {
        var res = compile(tmp, "void main() { nope(); }\n");
        assertFalse(res.success(), "chamada desconhecida deve falhar, não emitir binário");
        assertTrue(res.diagnostics().contains("unknown function nope"), res.diagnostics());
    }

    @Test
    void arityMismatchIsAHonestDiagnostic(@TempDir Path tmp) throws Exception {
        var res = compile(tmp, "int f(int a) { return a; }\nvoid main() { print_arg = f(1, 2); print(); }\n");
        assertFalse(res.success(), "aridade errada deve falhar");
        assertTrue(res.diagnostics().contains("expects 1 argument"), res.diagnostics());
    }

    @Test
    void printWithArgumentsIsRejected(@TempDir Path tmp) throws Exception {
        var res = compile(tmp, "void main() { print(1); }\n");
        assertFalse(res.success(), "print(x) não existe no subset");
        assertTrue(res.diagnostics().contains("print() takes no arguments"), res.diagnostics());
    }

    @Test
    void tooManyParametersIsRejected(@TempDir Path tmp) throws Exception {
        String src = "int f(int a, int b, int c, int d, int e, int f, int g) { return a; }\n"
                + "void main() { print_arg = f(1, 2, 3, 4, 5, 6, 7); print(); }\n";
        var res = compile(tmp, src);
        assertFalse(res.success(), "mais de 6 parâmetros/chamada deve falhar");
        assertTrue(res.diagnostics().contains("at most 6"), res.diagnostics());
    }
}
