package dev.kof.compiler;

import java.util.List;

/**
 * Unified dispatch hook for the {@code kof} standard-library namespaces owned
 * by the STDLIB track (math/strings/uuid/encoding/random/... — plan-stdlib-
 * expansion). One hook replaces a per-domain block in MethodCallTyper /
 * MemberCallTyper / ExpressionMethodCallLowerer, so adding S2..S9 never grows
 * those files past the ≤500 gate (each domain registers below).
 *
 * Each domain keeps its own single-responsibility table (KofMath, KofStrings,
 * ...); this class only routes. Existing validation/security/observability stay
 * on their own hooks (other lanes, untouched).
 */
public final class KofStd {

    private KofStd() {}

    record StdCall(String ownerPackage, String ownerClass, String function,
                   Type returnType, List<Type> parameterTypes) {}

    /** true if the receiver identifier is a std namespace we own. */
    static boolean isStdNamespace(String name) {
        return KofMath.isMathNamespace(name) || KofStrings.isStringsNamespace(name)
                || KofEncoding.isEncodingNamespace(name) || KofUuid.isUuidNamespace(name)
                || KofNet.isNetNamespace(name);
    }

    static StdCall staticMethod(String namespace, String name, List<Type> argTypes) {
        if (KofMath.isMathNamespace(namespace)) {
            KofMath.MathCall c = KofMath.staticMethod(namespace, name, argTypes);
            return c == null ? null
                    : new StdCall("kof.math", "Math", c.function(), c.returnType(), c.parameterTypes());
        }
        if (KofStrings.isStringsNamespace(namespace)) {
            KofStrings.StringsCall c = KofStrings.staticMethod(namespace, name, argTypes);
            return c == null ? null
                    : new StdCall("kof.strings", "Strings", c.function(), c.returnType(), c.parameterTypes());
        }
        if (KofEncoding.isEncodingNamespace(namespace)) {
            KofEncoding.EncodingCall c = KofEncoding.staticMethod(namespace, name, argTypes);
            return c == null ? null
                    : new StdCall("kof.encoding", "Encoding", c.function(), c.returnType(), c.parameterTypes());
        }
        if (KofNet.isNetNamespace(namespace)) {
            KofNet.NetCall c = KofNet.staticMethod(namespace, name, argTypes);
            return c == null ? null
                    : new StdCall("kof.net", "Net", c.function(), c.returnType(), c.parameterTypes());
        }
        if (KofUuid.isUuidNamespace(namespace)) {
            KofUuid.UuidCall c = KofUuid.staticMethod(namespace, name, argTypes);
            return c == null ? null
                    : new StdCall("kof.uuid", "Uuid", c.function(), c.returnType(), c.parameterTypes());
        }
        return null;
    }

    static boolean supportedOn(StdCall call, Target target) {
        if ("kof.math".equals(call.ownerPackage())) return KofMath.supportedOn(call.function(), target);
        if ("kof.strings".equals(call.ownerPackage())) return KofStrings.supportedOn(call.function(), target);
        if ("kof.encoding".equals(call.ownerPackage())) return KofEncoding.supportedOn(call.function(), target);
        if ("kof.uuid".equals(call.ownerPackage())) return KofUuid.supportedOn(call.function(), target);
        if ("kof.net".equals(call.ownerPackage())) return KofNet.supportedOn(call.function(), target);
        return true;
    }

    static String gapCode(StdCall call) {
        if ("kof.math".equals(call.ownerPackage())) return KofMath.gapCode(call.function());
        if ("kof.strings".equals(call.ownerPackage())) return KofStrings.gapCode(call.function());
        if ("kof.encoding".equals(call.ownerPackage())) return KofEncoding.gapCode(call.function());
        if ("kof.uuid".equals(call.ownerPackage())) return KofUuid.gapCode(call.function());
        if ("kof.net".equals(call.ownerPackage())) return KofNet.gapCode(call.function());
        return "STD001";
    }
}
