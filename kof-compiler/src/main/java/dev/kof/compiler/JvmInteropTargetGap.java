package dev.kof.compiler;

import java.util.Set;

/**
 * #760: JVM interop ({@code import java.X}) is a JVM-backed-target face
 * (JVM/Script/Android). §510 gated only the external-class STATIC FIELD face
 * ({@code Integer.MAX_VALUE}); the constructor ({@code new File(...)}), the
 * instance method ({@code sc.nextLine()}) and the static method
 * ({@code Runtime.getRuntime()}) faces had no target gate, so on JS/Native the
 * backend emitted the real {@code java_*} call and the artifact died only at
 * link ({@code ld: undefined reference to java_io_File_init_1}, COMP001) or run
 * ({@code ReferenceError: java_io_File is not defined}) — the same rule-5/R6
 * silent cross-target divergence §510 fixed for fields.
 *
 * <p>This gate refuses those faces at COMPILE time with the named
 * {@code INTEROP003}, naming the class and the target. JVM/SCRIPT/ANDROID keep
 * the real interop; the JDK wrapper statics ({@code Integer.parseInt},
 * {@code Long.parseLong}, {@code Double.isNaN}, …) have dedicated JS/Native
 * shims ({@code NativeX86WrapperStatics}/{@code NativeRiscvWrapperStatics}) and
 * are therefore exempt by owner.
 */
final class JvmInteropTargetGap {

    private JvmInteropTargetGap() {}

    static final String CODE = "INTEROP003";

    /**
     * External owners with dedicated JS/Native lowering shims — their statics
     * and (String/valueOf) members must NOT be refused by this gate.
     */
    private static final Set<String> SHIMMED_OWNERS = Set.of(
            "java.lang.Integer", "java.lang.Long", "java.lang.Double",
            "java.lang.Float", "java.lang.Boolean", "java.lang.String",
            "java.lang.Object", "java.lang.CharSequence");

    /** Targets without the JVM behind the imported class. */
    static boolean refuses(Target target) {
        return target == Target.JS || target.isNative();
    }

    /** True when the external owner has a JS/Native shim and stays allowed. */
    static boolean isShimmedOwner(String internalName) {
        if (internalName == null) return false;
        return SHIMMED_OWNERS.contains(internalName.replace('/', '.'));
    }

    /**
     * Emits the honest {@code INTEROP003} diagnostic and returns {@code true}
     * when the face must be refused (JVM-backed target absent, owner not
     * shimmed). Mirrors {@link StringTargetGaps#refuse}: the already-emitted
     * ops are intentionally left for the aborted compilation.
     */
    static boolean refuse(CompilerDriver driver, SourcePosition pos, String what) {
        if (!refuses(driver.target)) return false;
        if (driver.currentDiagnostics != null) {
            driver.currentDiagnostics.error(
                    pos != null ? pos.file() : "",
                    pos != null ? pos.line() : 0,
                    pos != null ? pos.column() : 0, 0,
                    what + " requires a JVM-backed target (JVM/Script/Android); "
                            + "not available on " + driver.target + " (" + CODE + ")",
                    CODE);
        }
        return true;
    }
}
