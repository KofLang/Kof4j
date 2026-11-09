package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * BuiltinMemberTypes — a família de acessores de instância sobre tipos
 * BUILTIN (InteropError/HttpResponse), extraída do MethodCallTyper no MESMO
 * commit que o roteamento do Response (#1126; o arquivo cruzou 591→604 no
 * gate ≤500). Devolve o tipo de retorno do acessor, ou null quando o
 * receiver não é dessa família (o caller continua a cadeia).
 */
final class BuiltinMemberTypes {

    private BuiltinMemberTypes() {}

    static Type memberReturnType(Type recvType, MethodCallExpr mc, CompilerDriver driver,
                                 List<IRLocalVariable> locals) {
        if (KofInteropError.isInteropErrorType(recvType)) {
            KofInteropError.InteropCall ioeCall =
                    KofInteropError.instanceMethod(recvType, mc.methodName(), mc.arguments().size());
            if (ioeCall != null) return ioeCall.returnType();
        }
        if (KofHttpResponse.isResponseType(recvType)) {
            KofHttpResponse.ResponseCall rc =
                    KofHttpResponse.instanceMethod(recvType, mc.methodName(), mc.arguments().size());
            if (rc != null) return rc.returnType();
        }
        return null;
    }
}
