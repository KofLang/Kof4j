package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonDecodeNestedJsE2ETest extends ShellSupport {

    private void assertJvmAndJs(String source, String expected) throws Exception {
        Files.writeString(tmp.resolve("S.kf"), source);
        Run jvm = runJvm(tmp.resolve("S.kf"), tmp.resolve("o-jvm"));
        assertTrue(jvm.ok(), () -> "JVM failed: " + jvm.output());
        assertEquals(expected, jvm.output().trim().replace("\r\n", "\n"), "JVM");
        Run js = runJs(tmp.resolve("S.kf"), tmp.resolve("o-js"));
        assertTrue(js.ok(), () -> "JS failed: " + js.output());
        assertEquals(expected, js.output().trim().replace("\r\n", "\n"), "JS");
    }

    @Test
    void nestedObjectsInMapOfObjectBecomeMaps() throws Exception {
        assertJvmAndJs("""
            main() {
                var s = json.decode<Map<String, Object>>("{\\"caps\\":{\\"a\\":1,\\"b\\":{\\"z\\":7}},\\"arr\\":[{\\"k\\":2}]}")
                var caps = s.get("caps") as Map<String, Object>
                println(caps.size)
                var b = caps.get("b") as Map<String, Object>
                println(b.get("z"))
                var arr = s.get("arr") as List<Map<String, Object>>
                println(arr.get(0).get("k"))
            }
            """, "2\n7\n2");
    }

    @Test
    void mapOfMapKeepsInnerMapOperations() throws Exception {
        assertJvmAndJs("""
            main() {
                var m = json.decode<Map<String, Map<String, Int>>>("{\\"a\\":{\\"x\\":1},\\"b\\":{\\"y\\":2}}")
                println(m.size)
                var a = m.get("a")
                if (a != null) { println(a.size) }
            }
            """, "2\n1");
    }

    @Test
    void listOfObjectMaterializesNestedMapsAndLists() throws Exception {
        assertJvmAndJs("""
            main() {
                var l = json.decode<List<Object>>("[{\\"a\\":5},[{\\"b\\":6}]]")
                var first = l.get(0) as Map<String, Object>
                println(first.get("a"))
                var inner = l.get(1) as List<Map<String, Object>>
                println(inner.get(0).get("b"))
            }
            """, "5\n6");
    }
}
