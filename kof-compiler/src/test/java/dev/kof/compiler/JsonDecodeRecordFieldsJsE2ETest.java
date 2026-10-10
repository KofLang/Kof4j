package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #740 slice 2 (owner lane compiler/JVM, issue jonasrochasilva-prog): the JS
 * json decoder bound a record's fields RAW, so a nested record field stayed a
 * plain JS object (`o.inner.z` read `_z` on `{z:4}` -> undefined) and a
 * `List<Record>`/`Map<..,Record>` field kept raw objects — silent divergence
 * from the JVM/Script paths. The helper now converts each field by its
 * declared type. JVM is the oracle in every case.
 */
class JsonDecodeRecordFieldsJsE2ETest extends ShellSupport {

    private void assertRecordFieldsJvmAndJs(String source, String expected) throws Exception {
        Files.writeString(tmp.resolve("S.kf"), source);
        Run jvm = runJvm(tmp.resolve("S.kf"), tmp.resolve("o-jvm"));
        assertTrue(jvm.ok(), () -> "JVM failed: " + jvm.output());
        assertEquals(expected, jvm.output().trim().replace("\r\n", "\n"), "JVM");
        Run js = runJs(tmp.resolve("S.kf"), tmp.resolve("o-js"));
        assertTrue(js.ok(), () -> "JS failed: " + js.output());
        assertEquals(expected, js.output().trim().replace("\r\n", "\n"), "JS");
    }

    @Test
    void nestedRecordFieldBindsToItsClass() throws Exception {
        assertRecordFieldsJvmAndJs("""
            record Inner(Int z)
            record Outer(String name, Inner inner)
            main() {
                var o = json.decode<Outer>("{\\"name\\":\\"x\\",\\"inner\\":{\\"z\\":4}}")
                println(o.inner.z)
            }
            """, "4");
    }

    @Test
    void listOfRecordFieldBindsEachElement() throws Exception {
        assertRecordFieldsJvmAndJs("""
            record Inner(Int z)
            record Outer(List<Inner> items)
            main() {
                var o = json.decode<Outer>("{\\"items\\":[{\\"z\\":5},{\\"z\\":6}]}")
                println(o.items.get(0).z)
                println(o.items.get(1).z)
            }
            """, "5\n6");
    }

    @Test
    void mapOfRecordFieldBindsEachValue() throws Exception {
        assertRecordFieldsJvmAndJs("""
            record Inner(Int z)
            record Outer(Map<String, Inner> m)
            main() {
                var o = json.decode<Outer>("{\\"m\\":{\\"a\\":{\\"z\\":7}}}")
                var a = o.m.get("a")
                if (a != null) { println(a.z) }
            }
            """, "7");
    }

    @Test
    void deeplyNestedRecordFieldBinds() throws Exception {
        // helper emission must be transitive: Inner2 is referenced only through
        // Inner, whose class name sorts BEFORE Outer in the helper set.
        assertRecordFieldsJvmAndJs("""
            record Inner2(Int q)
            record Inner(Inner2 deep)
            record Outer(Inner inner)
            main() {
                var o = json.decode<Outer>("{\\"inner\\":{\\"deep\\":{\\"q\\":9}}}")
                println(o.inner.deep.q)
            }
            """, "9");
    }

    @Test
    void nullableNestedRecordFieldStaysNull() throws Exception {
        assertRecordFieldsJvmAndJs("""
            record Inner(Int z)
            record Outer(String name, Inner? inner)
            main() {
                var o = json.decode<Outer>("{\\"name\\":\\"x\\"}")
                if (o.inner == null) { println("null") } else { println(o.inner.z) }
            }
            """, "null");
    }
}
