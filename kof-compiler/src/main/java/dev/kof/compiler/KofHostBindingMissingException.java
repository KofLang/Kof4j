package dev.kof.compiler;

/**
 * Embedding slice 4: the script called a host binding the session never
 * registered. A NAMED refusal (EMBED003) — never a silent no-op and never a
 * fallback to the global map (the isolation is the point).
 */
public final class KofHostBindingMissingException extends RuntimeException {

    private final String binding;

    KofHostBindingMissingException(String binding) {
        super("EMBED003: no host binding registered for `" + binding
                + "` in this session (register it with the embedding session's"
                + " bind(...) before the script runs)");
        this.binding = binding;
    }

    public String binding() {
        return binding;
    }
}
