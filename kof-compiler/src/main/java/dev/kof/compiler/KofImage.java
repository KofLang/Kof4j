package dev.kof.compiler;

import java.util.List;

/**
 * KofImage — {@code kof.image} platform namespace (D-IMAGE-SURFACE).
 *
 * {@code image.decode(path)} decodes a compressed image (JPEG) into a flat
 * {@code Int[]} layout {@code [width, height, samples…]} (RGB or RGBA,
 * row-major, channels derived from the source's alpha model) using the JVM
 * {@code javax.imageio} runtime. Pure-Kof decoders in {@code libs/image/}
 * cover the formats Kof can express without interop; this namespace is the
 * honest interop escape hatch for the ones it cannot (JPEG today).
 *
 * Other targets have no imageio: the call raises the compile-time gap
 * {@code IMG001} instead of a silent wrong decode (R6, no-silent-fallback).
 */
public final class KofImage {

    private KofImage() {}

    static final List<String> NAMESPACES = List.of("image");

    static boolean isImageNamespace(String name) {
        return NAMESPACES.contains(name);
    }

    record ImageCall(String function, Type returnType, List<Type> parameterTypes) {}

    /** Names accepted by the real dispatch (catalogue for the LSP).
     *  GUARD: StdCatalogTest requires == the switch case-literals below. */
    static List<String> functions() { return List.of("decode"); }

    /**
     * Resolves a call in the image namespace. Returns null when the call is
     * not part of the API (the analyzer reports an unknown method).
     */
    static ImageCall staticMethod(String namespace, String name, int argCount) {
        if ("image".equals(namespace)) {
            return switch (name) {
                case "decode" -> argCount == 1
                        ? new ImageCall("kof_image_decode",
                                new Type.ArrayType(Type.PrimitiveType.INT),
                                List.of(BuiltinTypes.STRING)) : null;
                default -> null;
            };
        }
        return null;
    }

    /** Only the JVM target has {@code javax.imageio} available. */
    static boolean supportedOn(Target target) {
        return target == Target.JVM;
    }

    /** Diagnostic code for target gaps (analogous to EGG001/MEDIA00x). */
    static String gapCode() {
        return "IMG001";
    }
}
