package dev.kof.compiler;


public final class AccessFlags {
    public static final int PUBLIC     = 0x0001;
    public static final int PRIVATE    = 0x0002;
    public static final int PROTECTED  = 0x0004;
    public static final int STATIC     = 0x0008;
    public static final int FINAL      = 0x0010;
    public static final int SUPER      = 0x0020;
    public static final int ABSTRACT   = 0x0400;
    public static final int INTERFACE  = 0x0200;

    private AccessFlags() {}
}
