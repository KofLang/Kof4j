package dev.kof.script;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.IRModule;
import dev.kof.compiler.KofCapability;
import dev.kof.compiler.KofCapabilityDeniedException;
import dev.kof.compiler.KofInterpreter;
import dev.kof.compiler.KofStepBudget;
import dev.kof.compiler.Target;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * KofScript embedding contract — the host-facing session (issue #842, plan
 * `docs/development/kofscript-embedding-plan.md`).
 *
 * <p>Three things the batch API does not give a host, and this class does:
 *
 * <ol>
 *   <li><b>Bounded execution</b> — {@link #run(int)} advances the script by at
 *       most {@code opBudget} IR operations and returns, so a script that loops
 *       forever can NEVER block the host thread. The script runs on its own
 *       thread and PARKS at an op boundary when its grant is spent.</li>
 *   <li><b>Capability confinement</b> — built with a grant set; any stdlib
 *       surface behind an ungranted capability refuses with
 *       {@code EMBED001} (named, never silent).</li>
 *   <li><b>Session bindings</b> — {@link #bind(String, Object)} registers a
 *       host value visible to THIS session only (the global
 *       {@link ScriptGlobals} map stays the legacy path, unused here).</li>
 * </ol>
 *
 * <p>Usage: build with {@link #builder()}, then {@code run(n)} until it returns
 * {@link Status#DONE} or {@link Status#FAILED}.
 */
public final class KofEmbedSession implements AutoCloseable {

    /** The advance outcome of one {@link #run(int)} call. */
    public enum Status {
        /** The script finished successfully. */
        DONE,
        /** Still running; the budget was spent before completion. */
        RUNNING,
        /** The script threw (the message is in {@link #failure()}). */
        FAILED
    }

    private final Set<KofCapability> grants;
    private final Map<String, Object> bindings;
    private final Map<String, dev.kof.compiler.Type> apiSignatures = new LinkedHashMap<>();
    private final Map<String, java.util.function.Function<Object[], Object>> apiImpls =
            new LinkedHashMap<>();
    private final long parkMillis;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition granted = lock.newCondition();
    private long quota;
    private boolean grantedSignal;

    private Thread worker;
    private KofInterpreter interpreter;
    private volatile Status status;
    private volatile String stdout = "";
    private volatile String stderr = "";
    private volatile String failure;

    private KofEmbedSession(Set<KofCapability> grants, Map<String, Object> bindings,
                            long parkMillis) {
        this.grants = grants;
        this.bindings = bindings;
        this.parkMillis = parkMillis;
    }

    /** A session that grants NOTHING — the untrusted default. */
    public static Builder builder() {
        return new Builder();
    }

    /** Compiles {@code source} and starts the script on its own thread. */
    public KofEmbedSession start(String source, String... args) throws IOException {
        Path dir = Files.createTempDirectory("kof-embed");
        Path file = dir.resolve("Session.kf");
        Files.writeString(file, source);
        CompilerDriver driver = new CompilerDriver();
        // as assinaturas das APIs do host entram na tipagem da chamada
        // (`hostcall_<nome>`) — por COMPILAÇÃO, não global (a sessão some
        // depois: um embedding nunca vaza assinatura para outro).
        dev.kof.compiler.KofHostBindingTypes.install(apiSignatures);
        CompilationResult compiled;
        try {
            compiled = driver.compileSources(List.of(file), dir, Target.JVM);
        } finally {
            dev.kof.compiler.KofHostBindingTypes.clear();
        }
        if (!compiled.success()) {
            throw new IllegalStateException("EMBED002: the script does not compile: "
                    + compiled.diagnostics().getDiagnostics());
        }
        IRModule ir = driver.prepareRepl(List.of(file), dir);
        ByteArrayOutputStream so = new ByteArrayOutputStream();
        ByteArrayOutputStream se = new ByteArrayOutputStream();
        PrintStream po = new PrintStream(so, true);
        PrintStream pe = new PrintStream(se, true);
        interpreter = new KofInterpreter(ir, po, pe);
        interpreter.embedding().setCapabilityGrants(EnumSet.copyOf(grants));
        interpreter.embedding().setStepBudget(this::tick);
        for (var e : apiImpls.entrySet()) {
            interpreter.embedding().bind(e.getKey(),
                    new dev.kof.compiler.KofInterpreterEmbedding.HostBinding(e.getValue()));
        }
        status = Status.RUNNING;
        worker = new Thread(() -> {
            try {
                interpreter.runEmbedded(args);
                status = Status.DONE;
            } catch (KofCapabilityDeniedException e) {
                failure = e.getMessage();
                status = Status.FAILED;
            } catch (Throwable t) {
                failure = KofInterpreter.describeError(t);
                status = Status.FAILED;
            } finally {
                po.flush();
                pe.flush();
                stdout = so.toString();
                stderr = se.toString();
                signal();
            }
        }, "kof-embed");
        worker.setDaemon(true);
        worker.start();
        // park immediately: the first run(n) releases the granted quota
        return this;
    }

    /** Advances the script by at most {@code opBudget} IR operations. */
    public Status run(int opBudget) {
        if (status != Status.RUNNING) return status;
        grant(opBudget);
        lock.lock();
        try {
            long remaining = parkMillis;
            while (status == Status.RUNNING && remaining > 0 && !grantedSignal) {
                long spent = granted.awaitNanos(MILLISECONDS.toNanos(remaining));
                remaining = spent;
            }
            grantedSignal = false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }
        return status;
    }

    /** Runs to completion — the convenience for trusted scripts. */
    public Status runToCompletion() {
        Status s = status;
        while (s == Status.RUNNING) s = run(1_000_000);
        return s;
    }

    public Status status() {
        return status;
    }

    public String stdout() {
        return stdout;
    }

    public String stderr() {
        return stderr;
    }

    public String failure() {
        return failure;
    }

    /** The capability set this session enforces. */
    public Set<KofCapability> grants() {
        return grants;
    }

    /** Registers a host value visible to THIS session's scripts only. */
    public KofEmbedSession bind(String name, Object value) {
        bindings.put(name, value);
        return this;
    }

    /**
     * Registers a host API the script can CALL: `hostcall_<name>(...)` inside
     * the script reaches this implementation. The signature is the Kof return
     * type (default {@code String}) — the crossing is text, by design.
     */
    public KofEmbedSession bindApi(String name, String returnType,
                                   java.util.function.Function<Object[], Object> fn) {
        apiSignatures.put(name, returnTypeOf(returnType));
        apiImpls.put(name, fn);
        return this;
    }

    /** The host APIs this session exposes (read-only view). */
    public java.util.Set<String> apiNames() {
        return java.util.Set.copyOf(apiImpls.keySet());
    }

    private static dev.kof.compiler.Type returnTypeOf(String kofType) {
        String t = kofType == null || kofType.isBlank() ? "String" : kofType.trim();
        return switch (t) {
            case "Int" -> dev.kof.compiler.Type.PrimitiveType.INT;
            case "Long" -> dev.kof.compiler.Type.PrimitiveType.LONG;
            case "Bool" -> dev.kof.compiler.Type.PrimitiveType.BOOL;
            case "Double" -> dev.kof.compiler.Type.PrimitiveType.DOUBLE;
            default -> new dev.kof.compiler.Type.ClassType("java.lang", "String", java.util.List.of());
        };
    }

    /** The session bindings (read-only view). */
    public Map<String, Object> bindings() {
        return Map.copyOf(bindings);
    }

    @Override
    public void close() {
        if (worker != null && worker.isAlive()) {
            worker.interrupt();
        }
    }

    // ── the cooperative gate ──────────────────────────────────────────

    /** Called by the interpreter at every op boundary. Parks while the granted
     *  quota is exhausted; decrements otherwise. */
    private void tick() {
        lock.lock();
        try {
            while (quota <= 0) {
                signal();
                granted.await();
            }
            quota--;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    private void grant(long n) {
        lock.lock();
        try {
            quota = Math.max(quota, n);
            grantedSignal = false;
            granted.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void signal() {
        lock.lock();
        try {
            grantedSignal = true;
            granted.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /** Builder: grants default to NONE (least privilege). */
    public static final class Builder {
        private final Set<KofCapability> grants = EnumSet.noneOf(KofCapability.class);
        private final Map<String, Object> bindings = new LinkedHashMap<>();
        private final Map<String, dev.kof.compiler.Type> apiSignatures = new LinkedHashMap<>();
        private final Map<String, java.util.function.Function<Object[], Object>> apiImpls =
                new LinkedHashMap<>();
        private long parkMillis = 30_000;

        public Builder grant(KofCapability capability) {
            grants.add(capability);
            return this;
        }

        /** Grants everything — only for trusted, unconfined scripts. */
        public Builder grantAll() {
            grants.addAll(KofCapability.all());
            return this;
        }

        public Builder bind(String name, Object value) {
            bindings.put(name, value);
            return this;
        }

        /** Registers a host API the script can CALL (`hostcall_<nome>(...)`). */
        public Builder bindApi(String name, String returnType,
                               java.util.function.Function<Object[], Object> fn) {
            apiSignatures.put(name, returnTypeOf(returnType));
            apiImpls.put(name, fn);
            return this;
        }

        /** How long run(n) waits for the script to spend its grant (default 30s). */
        public Builder parkFor(long millis) {
            this.parkMillis = millis;
            return this;
        }

        public Set<KofCapability> grants() {
            return grants;
        }

        /** Builds the session WITHOUT starting a script — for hosts that
         *  compile/start separately (and for binding isolation checks). */
        public KofEmbedSession build() {
            KofEmbedSession s = new KofEmbedSession(grants, bindings, parkMillis);
            s.apiSignatures.putAll(apiSignatures);
            s.apiImpls.putAll(apiImpls);
            return s;
        }

        public KofEmbedSession start(String source, String... args) throws IOException {
            return build().start(source, args);
        }
    }
}
