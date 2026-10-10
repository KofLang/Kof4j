package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * §583 (03/10, KofShare C2 app.kf): a captured route lambda whose body
 * branches an assignment whose RHS is a LONG expression (`expires = now +
 * req.ttlMs` inside `if (req.ttlMs > 0)`, `expires` typed Long from birth)
 * emitted a `goto` whose stack-map target expected a `long` still on the
 * stack — the JVM died with VerifyError at the FIRST dispatch (class load of
 * the lambda), while compiling clean. Shape measured in the product POST
 * /shares handler; this pin drives a real loopback request.
 */
class LongAssignInRouteLambdaE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path dir;

    @Test
    void longAssignedInsideBranchInCapturedRouteLambdaLoads() throws Exception {
        int port = 18971;
        Path src = dir.resolve("ve.kf");
        Files.writeString(src, """
                import kof.json
                import kof.http
                import kof.crypto
                import kof.keyExchange
                import kof.security
                import kof.time

                record Req(String offerId, Long ttlMs, Int maxUses, Bool readOnly)

                record Grant(String id, String token, String deviceId, String offerId, String permissions, Long createdAt, Long expiresAt, Int maxUses, Int used, String status, String revocation, String sig)

                Byte[] asciiBytes(String s) {
                    var out = new Byte[s.length]
                    var i = 0
                    while (i < s.length) {
                        out[i] = s.charAt(i)
                        i = i + 1
                    }
                    return out
                }

                main() {
                    var grants = new Map<String, Grant>()
                    grants.put("o1", Grant("g", "t", "d", "o1", "read", 1, 0, 0, 0, "active", "", ""))
                    var spath = "/tmp"
                    var controlKey = keyExchange.privateKey("Ed25519")
                    var app = web.app()
                    app.post("/shares") {
                        var req = json.decode<Req>(body())
                        var known = grants.get(req.offerId)
                        if (known == null) {
                            return "REG033: unknown offer " + req.offerId
                        }
                        var now = time.now()
                        var id = "sh" + security.randomHex(8)
                        var token = security.randomHex(32)
                        var perms = "read-write"
                        if (req.readOnly) {
                            perms = "read"
                        }
                        var expires: Long = 0
                        if (req.ttlMs > 0) {
                            expires = now + req.ttlMs
                        }
                        var core = Grant(id, token, "d1", req.offerId, perms, now, expires, req.maxUses, 0, "active", "", "")
                        var signed = Grant(core.id, core.token, core.deviceId, core.offerId, core.permissions, core.createdAt, core.expiresAt, core.maxUses, core.used, core.status, core.revocation, crypto.sign(controlKey, asciiBytes(core.token)))
                        grants.put(id, signed)
                        if (spath.length() > 0) {
                            grants.put(id + "s", signed)
                        }
                        return "SHARED " + id + " " + token
                    }
                    spawn {
                        app.listen(PORT)
                    }
                    var resp = ""
                    var i = 0
                    while (i < 100) {
                        try {
                            resp = http.post("http://127.0.0.1:PORT/shares",
                                "{\\"offerId\\":\\"o1\\",\\"ttlMs\\":5000,\\"maxUses\\":2,\\"readOnly\\":true}")
                            i = 100
                        } catch (String e) {
                            i = i + 1
                        }
                    }
                    println("resp=" + resp)
                    app.close()
                    println("LONGCAP-OK")
                }
                """.replace("PORT", String.valueOf(port)));

        Path out = dir.resolve("out");
        CompilationResult r = driver.compileSources(List.of(src), out, Target.JVM, dir);
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());

        var pb = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", out.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        int rc = p.waitFor();
        assertEquals(0, rc, "lambda load: " + output);
        assertTrue(output.contains("resp=SHARED "), "handler must reach the long-branch path: " + output);
        assertTrue(output.contains("LONGCAP-OK"), output);
    }
}
