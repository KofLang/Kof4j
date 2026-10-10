package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

class ConstructorOverloadE2ETest extends JvmJsRunSupport {

    @Test
    void overloadedConstructorsDispatchByArity(@TempDir Path tempDir) throws IOException {
        runBoth("""
                class Log {
                    List<String> lines

                    constructor() {
                        this.lines = listOf<String>()
                    }
                }

                class Guard {
                    Log log
                    List<String> audit

                    constructor(Int a) {
                        this.log = Log()
                        this.audit = this.log.lines
                    }

                    constructor(Int a, Log l) {
                        this.log = l
                        this.audit = l.lines
                    }
                }

                main() {
                    var g = Guard(1)
                    g.audit.add("x")
                    println("one-arg ok: " + g.audit.size())
                    var g2 = Guard(2, Log())
                    println("two-arg ok: " + g2.audit.size())
                }
                """, "one-arg ok: 1\ntwo-arg ok: 0", tempDir, "ctor-arity");
    }

    @Test
    void overloadedConstructorsDispatchBySignature(@TempDir Path tempDir) throws IOException {
        runBoth("""
                class Kind {
                    String label

                    constructor(Int n) {
                        this.label = "int"
                    }

                    constructor(String s) {
                        this.label = s
                    }
                }

                main() {
                    var a = Kind(1)
                    var b = Kind("text")
                    println(a.label)
                    println(b.label)
                }
                """, "int\ntext", tempDir, "ctor-type");
    }
}
