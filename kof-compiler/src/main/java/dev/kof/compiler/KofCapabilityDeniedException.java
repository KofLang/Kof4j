package dev.kof.compiler;

/**
 * Embedding contract (slice 3): a script reached a stdlib surface whose
 * capability was NOT granted. Thrown BEFORE the call runs, so the host sees a
 * named refusal (rule: no silent fallback, no silent no-op).
 *
 * <p>The message is the diagnostic the host may surface verbatim; the script
 * sees a normal Kof throw (the interpreter converts it like any other error).
 */
public final class KofCapabilityDeniedException extends RuntimeException {

    private final KofCapability capability;
    private final String function;

    KofCapabilityDeniedException(KofCapability capability, String function) {
        super("EMBED001: capability not granted: " + capability.name()
                + " (the script tried to call `" + function
                + "`; grant it on the embedding session to allow this surface)");
        this.capability = capability;
        this.function = function;
    }

    public KofCapability capability() {
        return capability;
    }

    public String function() {
        return function;
    }
}
