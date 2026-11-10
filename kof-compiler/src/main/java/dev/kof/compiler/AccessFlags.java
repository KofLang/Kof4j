package dev.kof.compiler;


public final class AccessFlags {
    public static final int PUBLIC     = 0x0001;
    public static final int PRIVATE    = 0x0002;
    public static final int PROTECTED  = 0x0004;
    public static final int STATIC     = 0x0008;
    public static final int FINAL      = 0x0010;
    public static final int SUPER      = 0x0020;
    public static final int BRIDGE     = 0x0040;
    // 0x0040 no espaço de FIELD flags (JVM spec §4.7) — mesmo bit de BRIDGE,
    // que só existe no espaço de METHOD flags (§4.6). (SG-020 regra 5)
    public static final int VOLATILE   = 0x0040;
    // ACC_VARARGS in the METHOD flag space (JVMS §4.6); same bit as
    // TRANSIENT in the FIELD space. Carried on external MethodSymbols so the
    // instance-call lowering packs trailing arguments (VarargsArrayPacker).
    public static final int VARARGS    = 0x0080;
    public static final int ABSTRACT   = 0x0400;
    public static final int INTERFACE  = 0x0200;
    public static final int SYNTHETIC  = 0x1000;
    // D-ENUM207 (#207): marca a constante de enum. Bit 0x4000 no espaço de
    // FIELD flags (§4.5) e de CLASS flags (§4.1) — sem colisão com os acima.
    public static final int ENUM       = 0x4000;

    private AccessFlags() {}
}
