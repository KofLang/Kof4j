package dev.kof.compiler;

/**
 * §574: ponte entre um closure Kof interpretado (KofInterpreter.KofObj) e o
 * dispatch web do runtime gerado, que chama handlers por reflexão host
 * ({@code getMethod("invoke")} em JvmRuntimeWebDispatch.kof_web_invoke).
 * O adaptador expõe os dois formatos que o dispatch tenta — invoke() (rota
 * lê param()/body() pelos ThreadLocals do runtime) e invoke(String×5) —
 * delegando ao invokeLambda do interpretador (mesma ponte das coleções).
 */
public final class InterpretedCallable {

    private final KofInterpreter interp;
    private final KofInterpreter.KofObj handler;

    public InterpretedCallable(KofInterpreter interp, KofInterpreter.KofObj handler) {
        this.interp = interp;
        this.handler = handler;
    }

    public Object invoke() throws Throwable {
        return interp.invokeLambda(handler, new Object[0]);
    }

    // A2 (§574 residual): the SSE dispatch reflects getMethod("invoke",
    // SseConnection.class); the connection also arrives through the runtime's
    // KOF_SSE_SENDER ThreadLocal, so the interpreted arm accepts the object
    // and forwards it as a positional arg (0-param handlers ignore it — the
    // sse.send()/event() context functions are the supported form).
    public Object invoke(Object conn) throws Throwable {
        return interp.invokeLambda(handler, new Object[]{conn});
    }

    public Object invoke(String method, String path, String body, String query, String headers)
            throws Throwable {
        return interp.invokeLambda(handler, new Object[]{method, path, body, query, headers});
    }
}
