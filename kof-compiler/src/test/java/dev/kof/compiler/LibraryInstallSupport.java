package dev.kof.compiler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Base for the E2E suites that compile a Kof program against an installed
 * library tree ({@code libs/file}, {@code libs/interop}, {@code libs/image},
 * {@code libs/kofmd}, {@code libs/pdf}). The {@code copyLibrary} + library-root
 * discovery pair was byte-identical (modulo the library name and its marker
 * file) across 37 test classes; it now lives once here. Subclasses supply only
 * {@link #libraryName()} and {@link #libraryMarkers()} (and, for the rare
 * multi-library copy, override {@link #libraryNames()}).
 *
 * <p>Phase 5 slice 9 of the test-architecture plan. No test body, target or
 * assertion moves: only the library-install mechanism is shared. {@code copyLibrary}
 * is the sole {@code void} member, so the cross-class method-name audit no longer
 * sees it repeated.
 */
interface LibraryInstallSupport {

    /** Primary library directory name under {@code libs/} (drives the root search). */
    String libraryName();

    /** Marker file(s); a candidate directory qualifies when it holds any of them. */
    List<String> libraryMarkers();

    /** Directory names under {@code libs/} to install; defaults to the primary one. */
    default List<String> libraryNames() {
        return List.of(libraryName());
    }

    /** A directory qualifies when it contains any of {@link #libraryMarkers()}. */
    default boolean isLibraryDir(Path dir) {
        for (String marker : libraryMarkers()) {
            if (Files.isRegularFile(dir.resolve(marker))) return true;
        }
        return false;
    }

    /** Locate the {@code libs/} root from the repository or the module directory. */
    default Path findLibsRoot() {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        for (Path candidate : List.of(workingDirectory.resolve("libs"),
                workingDirectory.resolve("../libs").normalize())) {
            if (isLibraryDir(candidate.resolve(libraryName()))) {
                return candidate;
            }
        }
        throw new IllegalStateException("libs/" + libraryName() + " not found from " + workingDirectory);
    }

    /** Convenience for the single-library suites: the primary library root. */
    default Path findLibraryRoot() {
        return findLibsRoot().resolve(libraryName());
    }

    /** Copy every configured library into {@code destinationRoot/<library>}. */
    default void copyLibrary(Path destinationRoot) throws Exception {
        Path libsRoot = findLibsRoot();
        for (String library : libraryNames()) {
            Path sourceRoot = libsRoot.resolve(library);
            try (var files = Files.walk(sourceRoot)) {
                for (Path source : files.filter(Files::isRegularFile).toList()) {
                    Path destination = destinationRoot.resolve(library)
                            .resolve(sourceRoot.relativize(source));
                    Files.createDirectories(destination.getParent());
                    Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
