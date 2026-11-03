package dev.kof.compiler;

import java.util.List;

/**
 * Compile-time dispatch table for the ACME client namespace ({@code kof.acme},
 * plan {@code docs/development/tls-hardening-plan.md} slice 4).
 *
 * <p>The namespace automates certificate issuance (RFC 8555) for the PEM pair
 * the TLS slices consume: DNS-01 (the multi-tenant edge cannot route HTTP-01
 * pre-verification), ES256 JWS over the operator's account key (key storage is
 * DELEGATED to the operator's secret store — never persisted by Kof), no
 * Kof-run DNS server, no HTTP-01 listener magic (explicit non-goals).
 *
 * <p>JVM-first: the client core lives in {@code JvmAcmeRuntime}; other targets
 * keep the honest gap (no ACME face yet) — the slice records the gap per
 * target rather than shipping a stub.
 */
public final class KofAcme {

    private KofAcme() {}

    static final Type STR = BuiltinTypes.STRING;

    private static boolean isStr(Type t) {
        return STR.equals(t) || "String".equals(t.toString());
    }

    static final Type ACME = new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of());

    static boolean isAcmeNamespace(String name) {
        return "acme".equals(name);
    }

    record AcmeCall(String function, Type returnType, List<Type> parameterTypes) {}

    /** {@code acme.jwsEs256(accountKeyPem, headerJson, payloadJson)} — one JWS. */
    static AcmeCall staticCall(String name, List<Type> argTypes) {
        if (!isAcmeMethod(name)) return null;
        if ("jwsEs256".equals(name) && argTypes.size() == 3
                && isStr(argTypes.get(0)) && isStr(argTypes.get(1)) && isStr(argTypes.get(2))) {
            return new AcmeCall("kof_acme_jws_es256", STR, List.of(STR, STR, STR));
        }
        if ("newOrder".equals(name) && argTypes.size() == 4
                && isStr(argTypes.get(0)) && isStr(argTypes.get(1))
                && isStr(argTypes.get(2)) && isStr(argTypes.get(3))) {
            return new AcmeCall("kof_acme_new_order", STR, List.of(STR, STR, STR, STR));
        }
        if ("dns01Txt".equals(name) && argTypes.size() == 4
                && isStr(argTypes.get(0)) && isStr(argTypes.get(1))
                && isStr(argTypes.get(2)) && isStr(argTypes.get(3))) {
            return new AcmeCall("kof_acme_dns01_txt", STR, List.of(STR, STR, STR, STR));
        }
        if ("challenge".equals(name) && argTypes.size() == 4
                && isStr(argTypes.get(0)) && isStr(argTypes.get(1))
                && isStr(argTypes.get(2)) && isStr(argTypes.get(3))) {
            return new AcmeCall("kof_acme_challenge", STR, List.of(STR, STR, STR, STR));
        }
        // TLS hardening slice 4 increment 2: the ACCOUNT layer — directory +
        // newNonce + newAccount (JWK embedded + RFC 7638 thumbprint); returns
        // the account kid.
        if ("newAccount".equals(name) && argTypes.size() == 3
                && isStr(argTypes.get(0)) && isStr(argTypes.get(1)) && isStr(argTypes.get(2))) {
            return new AcmeCall("kof_acme_new_account", STR, List.of(STR, STR, STR));
        }
        return null;
    }

    static boolean isAcmeMethod(String name) {
        return "jwsEs256".equals(name) || "newAccount".equals(name)
                || "newOrder".equals(name) || "dns01Txt".equals(name)
                || "challenge".equals(name);
    }

    /** Gap code for the not-yet-supported targets (recorded per target). */
    static String gapCode() {
        return "WEB008";
    }
}
