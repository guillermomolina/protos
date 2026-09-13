/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** PERF006-C2 portable runtime-plane and PLAT033 single-authority evidence. */
final class ProtosPerf006C2PortableRuntimeReconciliationTest {
    @Test
    void portableDistributionIsDerivedFromCanonicalRootPomRuntimePlane() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));
        String builder = Files.readString(Path.of("dist/build_portable.py"));
        String smoke = Files.readString(Path.of("dist/smoke_optimizing_runtime.sh"));
        String verifier = Files.readString(Path.of("tools/verify_toolchain.py"));

        assertFalse(Files.exists(Path.of("dist/runtime-pom.xml")));

        assertTrue(pom.contains("<version>3.11.0</version>"));
        assertTrue(pom.contains("<id>materialize-canonical-graal-runtime-plane</id>"));
        assertTrue(pom.contains("<graphRoots>"));
        assertTrue(pom.contains("<artifactId>dap</artifactId>"));
        assertTrue(pom.contains("<prependGroupId>true</prependGroupId>"));
        assertTrue(pom.contains("<stripVersion>false</stripVersion>"));
        assertTrue(pom.contains("<exclude>org.graalvm.*:*</exclude>"));

        assertTrue(builder.contains("source_runtime_dir = root / \"target\" / \"runtime\""));
        assertTrue(builder.contains("\"runtime_authority=pom.xml\""));
        assertFalse(builder.contains("runtime-pom.xml"));
        assertTrue(builder.contains("source_manifest != copied_manifest"));

        assertTrue(smoke.contains("*truffle-runtime-$EXPECTED_TRUFFLE_VERSION.jar"));
        assertTrue(smoke.contains("*truffle-compiler-$EXPECTED_TRUFFLE_VERSION.jar"));
        assertTrue(smoke.contains("*dap*-$EXPECTED_TRUFFLE_VERSION.jar"));

        assertTrue(verifier.contains("\"dist.runtime_pom_absent\""));
        assertTrue(verifier.contains("\"pom.runtime_plane.graph_roots\""));
        assertTrue(verifier.contains("\"dist.builder.canonical_runtime_projection\""));

        System.out.println("PERF006_C2_ROOT_POM_AUTHORITY=PASS");
        System.out.println("PERF006_C2_DIST_RUNTIME_POM=ABSENT");
        System.out.println("PERF006_C2_DAP_RUNTIME_PROJECTION=DECLARED");
    }
}
