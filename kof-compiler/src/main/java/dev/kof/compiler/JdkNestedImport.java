package dev.kof.compiler;

/**
 * §791 (REFACTOR-500): the import of a NESTED Java class —
 * {@code import java.net.http.HttpRequest.BodyPublishers} must name
 * {@code java/net/http/HttpRequest$BodyPublishers}, not the nonexistent
 * {@code java/net/http/HttpRequest/BodyPublishers}. Extracted from
 * {@code JdkReflectionResolver} (594 lines before the fix, 623 after): the
 * resolution is about IMPORT names, while the resolver is about members.
 */
final class JdkNestedImport {

    private JdkNestedImport() {}

    /**
     * {@code import a.b.C.D} names the NESTED class {@code a/b/C$D} when no
     * top-level {@code a/b/C/D} exists (JDK by reflection, jars by
     * {@code external}); the bare split gave a class that does not exist and
     * every member was SEM025. Null when nothing nested matches.
     */
    static Type.ClassType resolve(String pkg, String name, ExternalClasspath external) {
        String[] parts = (pkg + "." + name).split("\\.");
        if (exists(pkg.replace('.', '/') + "/" + name, external)) return null;
        for (int k = parts.length - 2; k >= 1; k--) {
            String p = String.join(".", java.util.Arrays.copyOfRange(parts, 0, k));
            String n = String.join("$", java.util.Arrays.copyOfRange(parts, k, parts.length));
            if (exists(p.replace('.', '/') + "/" + n, external)) {
                return new Type.ClassType(p, n, java.util.List.of());
            }
        }
        return null;
    }

    private static boolean exists(String internal, ExternalClasspath external) {
        if (JdkReflectionResolver.isJdkClass(internal)) {
            try {
                Class.forName(internal.replace('/', '.'), false, JdkNestedImport.class.getClassLoader());
                return true;
            } catch (Throwable t) {
                return false;
            }
        }
        return external != null && external.knows(internal);
    }
}