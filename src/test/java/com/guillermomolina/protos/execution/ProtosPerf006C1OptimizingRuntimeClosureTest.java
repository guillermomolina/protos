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

/** PERF006-C1 optimizing-runtime closure and PLAT033 packaging-boundary evidence. */
final class ProtosPerf006C1OptimizingRuntimeClosureTest {
    private static final String EXPECTED_RUNTIME =
            "com.oracle.truffle.runtime.hotspot.HotSpotTruffleRuntime";

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
        assertTrue(pom.contains("<exclude>org.graalvm.truffle:truffle-runtime</exclude>"));
        assertTrue(pom.contains("<exclude>org.graalvm.truffle:truffle-compiler</exclude>"));
        assertTrue(pom.contains("<id>materialize-checkout-optimizing-runtime</id>"));
        assertTrue(pom.contains("${project.build.directory}/runtime"));
        assertTrue(
                pom.contains(
                        "<includeArtifactIds>truffle-runtime,truffle-compiler</includeArtifactIds>"));
        assertTrue(pom.contains("<stripVersion>true</stripVersion>"));

        assertTrue(launcher.contains("RUNTIME_DIR=$ROOT/target/runtime"));
        assertTrue(launcher.contains("$RUNTIME_DIR/truffle-runtime.jar"));
        assertTrue(launcher.contains("$RUNTIME_DIR/truffle-compiler.jar"));
        assertTrue(launcher.contains("-cp \"$JAR:$RUNTIME_DIR/*\""));
        assertFalse(launcher.contains("-jar \"$JAR\""));

        assertFalse(pom.contains("WarnInterpreterOnly=false"));
        assertFalse(pom.contains("UseFallbackRuntime=true"));
        assertFalse(launcher.contains("WarnInterpreterOnly=false"));
        assertFalse(launcher.contains("UseFallbackRuntime=true"));

        System.out.println("PERF006_C1_SHADED_OPTIMIZER=NO");
        System.out.println("PERF006_C1_CHECKOUT_RUNTIME_PLANE=PASS");
    }
}
