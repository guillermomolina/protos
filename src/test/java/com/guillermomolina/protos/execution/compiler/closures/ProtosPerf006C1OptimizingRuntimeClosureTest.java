/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oracle.truffle.api.Truffle;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * PERF006-C1 optimizing-runtime closure, PLAT033 packaging-boundary evidence, and the PLAT045
 * boundary: only the Native profile selects the fallback runtime.
 */
final class ProtosPerf006C1OptimizingRuntimeClosureTest {
    private static final String EXPECTED_RUNTIME =
            "com.oracle.truffle.runtime.hotspot.HotSpotTruffleRuntime";
    private static final String FALLBACK_BUILD_ARG =
            "<buildArg>-Dtruffle.UseFallbackRuntime=true</buildArg>";

    @Test
    void surefireUsesExactOptimizingRuntimeWithoutSuppression() {
        String runtime = Truffle.getRuntime().getClass().getName();
        System.out.println("PERF006_C1_SUREFIRE_RUNTIME=" + runtime);

        assertEquals(EXPECTED_RUNTIME, runtime);
        assertNotEquals("false", System.getProperty("polyglot.engine.WarnInterpreterOnly"));
        assertNotEquals("true", System.getProperty("truffle.UseFallbackRuntime"));

        System.out.println("PERF006_C1_SUREFIRE_OPTIMIZING_RUNTIME=PASS");
        System.out.println("PERF006_C1_WARNING_SUPPRESSION=NO");
    }

    @Test
    void checkoutPackagingKeepsOptimizerOutsideShadedJar() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));
        String launcher = Files.readString(Path.of("bin/protos"));

        assertTrue(pom.contains("<artifactId>truffle-runtime</artifactId>"));
        assertTrue(pom.contains("<scope>runtime</scope>"));
        assertTrue(pom.contains("<exclude>org.graalvm.*:*</exclude>"));
        assertTrue(pom.contains("<id>materialize-canonical-graal-runtime-plane</id>"));
        assertTrue(pom.contains("<graphRoots>"));
        assertTrue(pom.contains("<prependGroupId>true</prependGroupId>"));
        assertTrue(pom.contains("<stripVersion>false</stripVersion>"));
        assertTrue(pom.contains("${project.build.directory}/runtime"));

        assertTrue(launcher.contains("RUNTIME_DIR=$ROOT/target/runtime"));
        assertTrue(launcher.contains("*truffle-runtime-*.jar"));
        assertTrue(launcher.contains("*truffle-compiler-*.jar"));
        assertTrue(launcher.contains("-cp \"$JAR:$RUNTIME_DIR/*\""));
        assertFalse(launcher.contains("-jar \"$JAR\""));

        assertFalse(pom.contains("WarnInterpreterOnly=false"));
        assertEquals(
                pom.indexOf(FALLBACK_BUILD_ARG),
                pom.lastIndexOf(FALLBACK_BUILD_ARG),
                "fallback runtime must be selected exactly once");
        String nativeProfile = nativeProfile(pom);
        assertTrue(nativeProfile.contains(FALLBACK_BUILD_ARG));
        assertTrue(
                nativeProfile.contains(
                        "<classesDirectory>${project.build.outputDirectory}</classesDirectory>"));
        assertFalse(nativeProfile.contains("<protos.shade.skip>true</protos.shade.skip>"));
        assertFalse(withoutNativeProfile(pom).contains("UseFallbackRuntime"));
        assertFalse(launcher.contains("WarnInterpreterOnly=false"));
        assertFalse(launcher.contains("UseFallbackRuntime=true"));

        System.out.println("PERF006_C1_SHADED_OPTIMIZER=NO");
        System.out.println("PERF006_C1_CHECKOUT_RUNTIME_PLANE=PASS");
        System.out.println("PLAT045_NATIVE_PROFILE_FALLBACK_RUNTIME=YES");
        System.out.println("PLAT045_JVM_FALLBACK_RUNTIME=NO");
        System.out.println("BUG014_NATIVE_PROFILE_SHADE_SKIP=NO");
        System.out.println("BUG014_NATIVE_PROFILE_APPLICATION_INPUT=CLASSES_DIRECTORY");
    }

    /** PLAT045 (#772): Native admission is interpreter-only and must never claim guest JIT. */
    @Test
    void nativeAdmissionValidatesInterpreterOnlyFallback() throws Exception {
        String script = Files.readString(Path.of("build/native/test-native.sh"));

        assertTrue(script.contains("NATIVE_GUEST_JIT=UNSUPPORTED_UPSTREAM_ORACLE_GRAAL_14579"));
        assertTrue(script.contains("NATIVE_INTERPRETER_ONLY=FAIL_FALLBACK_RUNTIME_NOT_SELECTED"));
        assertTrue(script.contains("NATIVE_INTERPRETER_ONLY=FAIL_UNEXPECTED_GUEST_COMPILATION"));
        assertTrue(script.contains("NATIVE_INTERPRETER_ONLY=FAIL_OPT_FAILED"));
        assertTrue(script.contains("NATIVE_INTERPRETER_ONLY=FAIL_FRAME_WITHOUT_BOXING"));
        assertTrue(script.contains("NATIVE_INTERPRETER_ONLY=FAIL_COMPILATION"));
        assertTrue(script.contains("NATIVE_TEST_TOOL_SMOKE=PASS"));
        assertTrue(script.contains("NATIVE_DAP_STACKTRACE_REGRESSION=PASS"));
        assertFalse(script.contains("WarnInterpreterOnly"));
        assertFalse(script.contains("NATIVE_FORCED_GUEST_JIT=PASS"));
        assertFalse(script.contains("BYTECODE_ROOT_TIER2=PASS"));

        System.out.println("PLAT045_NATIVE_GUEST_JIT_CLAIM=UNAVAILABLE_NOT_PASS");
    }

    private static String nativeProfile(String pom) {
        int start = pom.indexOf("<id>native</id>");
        assertTrue(start >= 0, "native profile missing");
        int end = pom.indexOf("</profile>", start);
        assertTrue(end > start, "native profile unterminated");
        return pom.substring(start, end);
    }

    private static String withoutNativeProfile(String pom) {
        String profile = nativeProfile(pom);
        return pom.replace(profile, "");
    }
}
