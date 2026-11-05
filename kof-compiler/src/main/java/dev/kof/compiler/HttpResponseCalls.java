package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * §832 — {@code http.send(method, url[, body][, headers...])} and the
 * {@code kof.http.Response} it returns (maintainer decision 05/11, G15/G172/G178).
 *
 * <pre>{@code
 * val r = http.send("PUT", url, json.encode(item), "Content-Type: application/json")
 * if (r.status() == 404) { ... }
 * println(r.header("Location"))   // String?, case-insensitive
 * println(r.headers())            // Map<String, String>, lower-case names
 * println(r.body())
 * }</pre>
 *
 * <p>Unlike {@code get}/{@code post}, a 4xx or 5xx is a response, not a throw;
 * a transport failure (refused, timeout, TLS) still throws a catchable String.
 * The response is a runtime value with four accessors, each lowered to a
 * {@code kof_http_response_*} runtime function that takes it as the first
 * argument (the {@code process.spawn} handle pattern).
 */
final class HttpResponseCalls {

    static final Type RESPONSE = new Type.ClassType("kof.http", "Response", List.of());
    static final String SEND = "kof_http_send";

    private static final Type STR = BuiltinTypes.STRING;
    private static final Type MAP_SS = new Type.ClassType("kof", "Map", List.of(STR, STR));

    private HttpResponseCalls() {}

    record Call(String function, Type returnType, List<Type> parameterTypes) {}

    static boolean isResponse(Type t) {
        return RESPONSE.equals(t instanceof Type.NullableType nt ? nt.inner() : t);
    }

    /** `Response` is a declarable name (var/param/field/return), like `Result`. */
    static Type typeByName(String name) {
        return "Response".equals(name) ? RESPONSE : null;
    }

    /** The accessor for {@code r.<name>(args)}, or null (SEM025). */
    static Call method(String name, List<Type> argTypes) {
        return switch (name) {
            case "status" -> argTypes.isEmpty()
                    ? new Call("kof_http_response_status", Type.PrimitiveType.INT, List.of(RESPONSE)) : null;
            case "body" -> argTypes.isEmpty()
                    ? new Call("kof_http_response_body", STR, List.of(RESPONSE)) : null;
            case "headers" -> argTypes.isEmpty()
                    ? new Call("kof_http_response_headers", MAP_SS, List.of(RESPONSE)) : null;
            case "header" -> argTypes.size() == 1 && BuiltinTypes.isString(argTypes.get(0))
                    ? new Call("kof_http_response_header", new Type.NullableType(STR), List.of(RESPONSE, STR))
                    : null;
            default -> null;
        };
    }

    /** Typing of {@code r.m(...)}: the accessor's type, or SEM025 naming the valid methods. */
    static Type infer(SemanticAnalyzer sa, MethodCallExpr mc, List<Type> argTypes) {
        Call c = method(mc.methodName(), argTypes);
        if (c != null) return c.returnType();
        if (sa != null && sa.diagnostics() != null) {
            sa.diagnostics().error(mc, "Cannot resolve method '" + mc.methodName()
                    + "' on type 'Response' (valid: status/body/header/headers)", "SEM025");
        }
        return Type.UnknownType.UNKNOWN;
    }

    /** Lowering of {@code r.m(...)} with the receiver already on the stack. */
    static int lower(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                     String owner, int localIdx, List<IRLocalVariable> locals) {
        List<Type> argTypes = new ArrayList<>();
        for (ExpressionNode a : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, a, locals));
        Call c = method(mc.methodName(), argTypes);
        if (c == null) return localIdx; // SEM025 already reported by the semantic pass
        for (ExpressionNode a : mc.arguments()) {
            localIdx = ExpressionLowerer.emitExpression(driver, a, ops, owner, localIdx, locals);
        }
        ops.add(new KofCall(KofHttp.HTTP, c.function(), c.parameterTypes(), c.returnType(), KofCallKind.FUNCTION));
        return localIdx;
    }
}
