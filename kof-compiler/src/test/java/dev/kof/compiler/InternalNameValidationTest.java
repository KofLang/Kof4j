package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GitHub #999 — the defensive gate {@link CompilerPipeline#firstInvalidInternalName}
 * and the identifier rule {@link ModuleRoots#isPackageSegment}. The primary fix
 * keeps invalid-directory segments out of {@code derivedPackageOf}; this pins
 * the belt-and-braces check that refuses an invalid JVM internal name before
 * the emit could turn it into an EACCES masked as COMP001 or a silent
 * {@code ClassFormatError}.
 */
class InternalNameValidationTest {

    @Test
    void validInternalNamesPass() {
        assertTrue(CompilerPipeline.isValidInternalName("Main"));
        assertTrue(CompilerPipeline.isValidInternalName("Default/Main"));
        assertTrue(CompilerPipeline.isValidInternalName("foo/bar/Box"));
        assertTrue(CompilerPipeline.isValidInternalName("_x/$y/A1"));
    }

    @Test
    void dashDotAndEmptySegmentsAreInvalid() {
        assertFalse(CompilerPipeline.isValidInternalName("/agent/Box"), "absoluto");
        assertFalse(CompilerPipeline.isValidInternalName("sub//agent/Box"), "barra dupla");
        assertFalse(CompilerPipeline.isValidInternalName("sub/.agent/Box"), "segmento com ponto");
        assertFalse(CompilerPipeline.isValidInternalName("hidden-x/Box"), "hífen");
        assertFalse(CompilerPipeline.isValidInternalName(""), "vazio");
        assertFalse(CompilerPipeline.isValidInternalName("foo/"), "barra final");
        assertFalse(CompilerPipeline.isValidInternalName("1Box"), "começa por dígito");
    }

    @Test
    void packageSegmentFollowsTheLexerIdentifierRule() {
        assertTrue(ModuleRoots.isPackageSegment("agent"));
        assertTrue(ModuleRoots.isPackageSegment("_priv"));
        assertTrue(ModuleRoots.isPackageSegment("$gen"));
        assertTrue(ModuleRoots.isPackageSegment("a1"));
        assertFalse(ModuleRoots.isPackageSegment(".agent"));
        assertFalse(ModuleRoots.isPackageSegment(".hidden-x"));
        assertFalse(ModuleRoots.isPackageSegment("hidden-x"));
        assertFalse(ModuleRoots.isPackageSegment(""));
        assertFalse(ModuleRoots.isPackageSegment("1a"));
    }

    @Test
    void firstInvalidInternalNameFindsTheCulprit() {
        IRModule bad = new IRModule("Default",
                List.of(irClass("Default/Main"), irClass("/agent/Box")),
                List.of(), null, null);
        assertEquals("/agent/Box", CompilerPipeline.firstInvalidInternalName(bad));

        IRModule good = new IRModule("Default",
                List.of(irClass("Default/Main"), irClass("agent/Box")),
                List.of(), null, null);
        assertNull(CompilerPipeline.firstInvalidInternalName(good));
    }

    private static IRClass irClass(String name) {
        return new IRClass(name, "java/lang/Object", List.of(), 0,
                List.of(), List.of(), List.of(), null, 0, List.of());
    }
}