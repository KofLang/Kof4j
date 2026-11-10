package dev.kof.compiler;

/**
 * The JSON shape a value has, named in a JSN006 diagnostic (§833/#1103/#1128).
 *
 * <p>Extracted from {@code KofInterpreterRuntime} (REFACTOR-500: the class had
 * crossed 600 lines with the two contributing PRs' shape checks — one method is
 * a split, and the wording of "expected X but the payload is Y" is shared by
 * every depth of the decode).
 */
final class KofInterpreterJson {

    private KofInterpreterJson() {}

    /** The payload's JSON shape, named in the diagnostic. */
    static String shapeOf(Object raw) {
        if (raw == null) return "null";
        if (raw instanceof java.util.Map<?, ?>) return "an object";
        if (raw instanceof java.util.List<?>) return "an array";
        if (raw instanceof String) return "a string";
        if (raw instanceof Number) return "a number";
        if (raw instanceof Boolean) return "a boolean";
        return raw.getClass().getSimpleName();
    }
}