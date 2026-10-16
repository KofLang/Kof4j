package dev.kof.compiler;

/**
 * Guarda SEM025 para método inexistente em receptor String (probe QA 15/10,
 * família §145/#101 + #711): a chamada caía no descritor fallback
 * {@code ()Object} — NoSuchMethodError no JVM / link-fail no Native / TypeError
 * no JS. Só dispara no ponto de EMISSÃO, depois de TODAS as fontes de
 * resolução terem falhado (registry, Object, classpath externo, símbolos de
 * usuário) — métodos legais via classpath (e.g. `getBytes`) continuam legais
 * (the examples `isBlank`/`strip` here were STALE — both are REJECTED `SEM025`;
 * verified 15/10). Puro: emite o diagnóstico, não mexe na pilha/ops (REFACTOR-500).
 */
final class StringUnknownMethodGuard {

    private StringUnknownMethodGuard() {}

    static boolean refuses(CompilerDriver driver, MethodCallExpr mc, Type methodReturnType,
                           Type recvType) {
        if (!(methodReturnType instanceof Type.UnknownType) || !BuiltinTypes.isString(recvType)
                || driver.currentDiagnostics == null
                || StringMethodRegistry.stringMethodSignature(mc.methodName(), mc.arguments().size()) != null
                || StringMethodRegistry.objectMethodSignature(mc.methodName(), mc.arguments().size()) != null
                || StringMethodRegistry.stringRuntimeMethod(mc.methodName()) != null) {
            return false;
        }
        driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                mc.position() != null ? mc.position().line() : 0,
                mc.position() != null ? mc.position().column() : 0, 0,
                "Cannot resolve method '" + mc.methodName()
                    + "' on type 'String' (valid: length/isEmpty/charAt/substring/contains/startsWith/"
                    + "endsWith/indexOf/lastIndexOf/equals/equalsIgnoreCase/compareTo/compareToIgnoreCase/"
                    + "toUpperCase/toLowerCase/trim/concat/replace/replaceAll/replaceFirst/matches/"
                    + "split/toCharArray/getBytes/toInt/toLong/toFloat/toDouble)",
                "SEM025");
        return true;
    }
}
