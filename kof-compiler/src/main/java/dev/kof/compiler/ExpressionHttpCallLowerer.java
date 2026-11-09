package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering do namespace http (http.*) no emitExpression.
 */
public final class ExpressionHttpCallLowerer {

    private ExpressionHttpCallLowerer() {}

    static int lower(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                      String owner, int localIdx, List<IRLocalVariable> locals) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    // TLS hardening slice 4: the ACME client core is a JVM face — the plan's
    // slice-4 scope; other targets keep the honest WEB008 gap (no ACME face
    // there, never a stub).
    if (KofAcme.isAcmeNamespace(((IdentifierExpr) mc.receiver()).name())) {
        KofAcme.AcmeCall acmeCall = KofAcme.staticCall(mc.methodName(), argTypes);
        if (acmeCall != null) {
            if (driver.target != Target.JVM && driver.target != Target.SCRIPT
                    && driver.target != Target.ANDROID) {
                if (driver.currentDiagnostics != null) {
                    driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                            mc.position() != null ? mc.position().line() : 0,
                            mc.position() != null ? mc.position().column() : 0, 0,
                            "acme." + mc.methodName() + ": the ACME client core is a JVM face — the "
                                    + driver.target + " target has no ACME surface yet (WEB008)",
                            "WEB008");
                }
                return localIdx;
            }
            for (ExpressionNode arg : mc.arguments()) {
                localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
            }
            ops.add(new KofCall(KofAcme.ACME, acmeCall.function(), acmeCall.parameterTypes(),
                    acmeCall.returnType(), KofCallKind.FUNCTION));
        } else {
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                        mc.position() != null ? mc.position().line() : 0,
                        mc.position() != null ? mc.position().column() : 0, 0,
                        "Cannot resolve method '" + mc.methodName() + "' on namespace 'acme' (WEB008)",
                        "WEB008");
            }
        }
        return localIdx;
    }
    KofHttp.HttpCall httpCall = KofHttp.staticCall(mc.methodName(), argTypes);
    if (httpCall != null) {
        // TLS hardening slice 3: the trust-all opt-in is a JVM face — the JS
        // fetch stack and the Native http stack have NO bypass surface (the
        // JS runtime verifies by default; Native https refuses). Honest
        // compile-time refusal (HTTP002), never a silent no-op.
        if ("insecure".equals(mc.methodName())
                && driver.target != Target.JVM && driver.target != Target.SCRIPT
                && driver.target != Target.ANDROID) {
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                        mc.position() != null ? mc.position().line() : 0,
                        mc.position() != null ? mc.position().column() : 0, 0,
                        "http.insecure(): the trust-all opt-in is a JVM face — the "
                                + driver.target + " TLS stack verifies (or refuses https) by default; "
                                + "no bypass surface (HTTP002)",
                        "HTTP002");
            }
            return localIdx;
        }
        if (!KofHttp.supportedOn(driver.target)) {
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                        mc.position() != null ? mc.position().line() : 0,
                        mc.position() != null ? mc.position().column() : 0,
                        0,
                        ((IdentifierExpr) mc.receiver()).name() + "." + mc.methodName()
                                + ": not available on the " + driver.target
                                + " driver.target yet (HTTP002)",
                        "HTTP002");
            }
            return localIdx;
        }
        // headers variádicos (GitHub #32): o runtime recebe headers como uma
        // única String `\n`-separada. args fixos = url (+body p/ post/put/patch);
        // os demais são headers, colapsados em um só slot.
        // #1126: o send tem QUATRO args fixos (method, url, body, headers) —
        // sem isto o colapso variádico dos headers o tratava como a família
        // get (fixed=1) e concatava url+body+headers num slot.
        int fixed = ("post".equals(mc.methodName()) || "put".equals(mc.methodName())
                || "patch".equals(mc.methodName())) ? 2
                : ("send".equals(mc.methodName()) ? 4
                : ("insecure".equals(mc.methodName()) ? 0 : 1));
        int n = mc.arguments().size();
        for (int i = 0; i < Math.min(n, fixed); i++) {
            localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(i), ops, owner, localIdx, locals);
        }
        if (n > fixed) {
            localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(fixed), ops, owner, localIdx, locals);
            for (int i = fixed + 1; i < n; i++) {
                // pilha: [..., acc]; queremos acc + "\n" + h_i
                ops.add(new KofLoadLiteral(BuiltinTypes.STRING, "\n"));
                localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(i), ops, owner, localIdx, locals);
                ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                        List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                        BuiltinTypes.STRING, KofCallKind.FUNCTION));
                ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                        List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                        BuiltinTypes.STRING, KofCallKind.FUNCTION));
            }
        }
        ops.add(new KofCall(KofHttp.HTTP, httpCall.function(), httpCall.parameterTypes(),
                httpCall.returnType(), KofCallKind.FUNCTION));
        return localIdx;
    }
    return localIdx;
    }
}