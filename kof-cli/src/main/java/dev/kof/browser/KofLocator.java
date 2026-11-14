package dev.kof.browser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * §6.5 Locators and auto-wait (the testing-platform plan, `D-TESTING-PLATFORM`):
 * the grammar of the semantic locator + the measured assertion, parsed pure
 * (no browser on the classpath) so the resolution stays the provider's job.
 *
 * <p>Locator (semantic first, per the plan — "tests must not depend on
 * fragile CSS"): {@code role=button[name="Submit"]}, {@code text=Hello},
 * {@code label=User}, {@code placeholder=Search}, {@code test-id=save},
 * then the escape hatches {@code css=#id} and {@code xpath=//h1}. The value
 * is everything after the FIRST {@code =} (css/xpath selectors contain
 * {@code =} themselves). Only {@code role} carries the {@code [name="..."]}
 * option (double quotes, the exact accessible name).
 *
 * <p>Assertion: {@code visible}, {@code enabled}, {@code text}, {@code value},
 * {@code attr=<name>}.
 *
 * <p>Contract: an unknown scheme/role or a malformed assertion is a named
 * {@link IllegalArgumentException} listing what IS supported — never a silent
 * fallback (Q7).
 */
public final class KofLocator {

    /** Esquemas suportados, na ordem de prioridade do §6.5. */
    public static final String SCHEMES =
            "role, text, label, placeholder, test-id, css, xpath";

    /** Asserções suportadas. */
    public static final String ASSERTIONS =
            "visible, enabled, text, value, attr=<name>, focus";

    private static final Pattern ROLE_NAMED =
            Pattern.compile("^(.+?)\\[name=\"(.*)\"]$", Pattern.DOTALL);

    /** Locator parseado: esquema, alvo e (só no role) o accessible name. */
    public record Parsed(String scheme, String target, String name) {}

    /** Asserção parseada: kind e (só no attr) o nome do atributo. */
    public record Assert(String kind, String attrName) {}

    /** Passo de fluxo (§6.5): locator + o lado direito, que é AÇÃO
     *  (`click`, `fill=<texto>`, `press=<tecla>`) ou ASSERÇÃO
     *  (`visible`, `enabled`, `text`, `value`, `attr=<name>`). */
    public record Step(Parsed locator, String right, boolean assertion) {}

    /** O separador do passo de fluxo. */
    public static final String STEP_SEP = " :: ";

    private static final java.util.List<String> ASSERTION_KINDS =
            java.util.List.of("visible", "enabled", "text", "value", "focus");

    private KofLocator() {}

    public static Parsed parseLocator(String locator) {
        if (locator == null || locator.isBlank()) {
            throw new IllegalArgumentException(
                    "empty locator (schemes: " + SCHEMES + ")");
        }
        int eq = locator.indexOf('=');
        if (eq <= 0) {
            throw new IllegalArgumentException(
                    "locator needs a `scheme=value` form (schemes: " + SCHEMES
                            + "): " + locator);
        }
        String scheme = locator.substring(0, eq).trim();
        String value = locator.substring(eq + 1);
        if (switch (scheme) {
            case "role", "text", "label", "placeholder", "test-id", "css", "xpath" ->
                    true;
            default -> false;
        }) {
            if (value.isBlank()) {
                throw new IllegalArgumentException(
                        "locator `" + scheme + "=</>` has an empty value");
            }
            if ("role".equals(scheme)) {
                String target = value;
                String name = null;
                Matcher m = ROLE_NAMED.matcher(value);
                if (m.matches()) {
                    target = m.group(1).trim();
                    name = m.group(2);
                }
                return new Parsed(scheme, target, name);
            }
            return new Parsed(scheme, value, null);
        }
        throw new IllegalArgumentException(
                "unknown locator scheme '" + scheme + "' in `" + locator
                        + "` (schemes: " + SCHEMES + ")");
    }

    public static Assert parseAssertion(String assertion) {
        if (assertion == null || assertion.isBlank()) {
            throw new IllegalArgumentException(
                    "empty assertion (assertions: " + ASSERTIONS + ")");
        }
        if (assertion.startsWith("attr=")) {
            String attrName = assertion.substring("attr=".length()).trim();
            if (attrName.isEmpty()) {
                throw new IllegalArgumentException(
                        "assertion `attr=</>` needs the attribute name (assertions: "
                                + ASSERTIONS + ")");
            }
            return new Assert("attr", attrName);
        }
        if (switch (assertion) {
            case "visible", "enabled", "text", "value", "focus" -> true;
            default -> false;
        }) {
            return new Assert(assertion, null);
        }
        throw new IllegalArgumentException(
                "unknown assertion '" + assertion + "' (assertions: "
                        + ASSERTIONS + ")");
    }

    /** Um passo `locator :: ação|asserção` (o corte é no PRIMEIRO
     *  separador; o lado direito é classificado — ação ou asserção, com
     *  recusa nomeada quando não é nenhum dos dois). */
    public static Step parseStep(String step) {
        if (step == null || step.isBlank()) {
            throw new IllegalArgumentException("empty flow step");
        }
        int sep = step.indexOf(STEP_SEP);
        if (sep <= 0) {
            throw new IllegalArgumentException(
                    "flow step needs a `locator" + STEP_SEP + "action` form: "
                            + step);
        }
        Parsed locator = parseLocator(step.substring(0, sep).trim());
        String right = step.substring(sep + STEP_SEP.length()).trim();
        boolean isAssertion = ASSERTION_KINDS.contains(right)
                || right.startsWith("attr=");
        boolean isAction = "click".equals(right)
                || (right.startsWith("fill=") && right.length() > "fill=".length())
                || (right.startsWith("press=") && right.length() > "press=".length());
        if (!isAssertion && !isAction) {
            throw new IllegalArgumentException(
                    "unknown flow step action/assertion '" + right
                            + "' (actions: click, fill=<texto>, press=<tecla>"
                            + "; assertions: " + ASSERTIONS + ")");
        }
        return new Step(locator, right, isAssertion);
    }
}
