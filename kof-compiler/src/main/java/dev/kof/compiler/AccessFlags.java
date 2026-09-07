package dev.kof.compiler;


public final class AccessFlags {
    static final int PUBLIC     = 0x0001;
    static final int PRIVATE    = 0x0002;
    static final int PROTECTED  = 0x0004;
    static final int STATIC     = 0x0008;
    static final int FINAL      = 0x0010;
    static final int SUPER      = 0x0020;
    static final int ABSTRACT   = 0x0400;
    static final int INTERFACE  = 0x0200;

    private AccessFlags() {}
}
