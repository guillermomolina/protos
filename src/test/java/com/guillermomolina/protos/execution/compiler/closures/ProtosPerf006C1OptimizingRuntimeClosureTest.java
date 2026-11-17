/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
 * DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
 * DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
 * OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF
 * THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY
 * OF THE LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING
 * THE CONTENTS OF THIS FILE. IF A COPY OF THE LICENSE DOES NOT ACCOMPANY THIS
 * FILE, A COPY OF THE LICENSE MAY ALSO BE OBTAINED AT THE FOLLOWING WEB SITE:
 * https://github.com/guillermomolina/protos
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
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
 * PERF006-C1 optimizing-runtime closure and PLAT033 packaging-boundary evidence: no profile,
 * including the Native Image profiles, selects the fallback runtime.
 */
final class ProtosPerf006C1OptimizingRuntimeClosureTest {
    private static final String EXPECTED_RUNTIME =
            "com.oracle.truffle.runtime.hotspot.HotSpotTruffleRuntime";
    /** The Native Image profiles: the distributed executable and the I130-C2 JUnit test image. */
    private static final String NATIVE_PROFILE = "native";
    private static final String NATIVE_TLS_TESTS_PROFILE = "native-tls-tests";

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

        // The Native Image profiles no longer select the fallback runtime: no
        // profile, property or launcher pins it or silences its notice.
        String nativeProfile = profile(pom, NATIVE_PROFILE);
        assertFalse(pom.contains("UseFallbackRuntime"));
        assertFalse(pom.contains("protos.native.fallbackRuntime"));
        assertFalse(pom.contains("WarnInterpreterOnly"));
        assertTrue(
                nativeProfile.contains(
                        "<classesDirectory>${project.build.outputDirectory}</classesDirectory>"));
        assertFalse(nativeProfile.contains("<protos.shade.skip>true</protos.shade.skip>"));
        assertFalse(launcher.contains("WarnInterpreterOnly=false"));
        assertFalse(launcher.contains("UseFallbackRuntime=true"));

        System.out.println("PERF006_C1_SHADED_OPTIMIZER=NO");
        System.out.println("PERF006_C1_CHECKOUT_RUNTIME_PLANE=PASS");
        System.out.println("PLAT045_NATIVE_PROFILE_FALLBACK_RUNTIME=NO");
        System.out.println("PLAT045_JVM_FALLBACK_RUNTIME=NO");
        System.out.println("BUG014_NATIVE_PROFILE_SHADE_SKIP=NO");
        System.out.println("BUG014_NATIVE_PROFILE_APPLICATION_INPUT=CLASSES_DIRECTORY");
    }

    /**
     * I130-C3: the Native JUnit profile selects exactly the two TLS classes by itself, so a direct
     * {@code mvn -Pnative-tls-tests test} builds the same image as {@code make test-native}, and a
     * stale Surefire unique-ID selection never reaches that image.
     */
    @Test
    void nativeTlsTestsProfileSelectsExactlyTheTlsClasses() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));
        String nativeTlsTestsProfile = profile(pom, NATIVE_TLS_TESTS_PROFILE);

        int surefire =
                nativeTlsTestsProfile.indexOf("<artifactId>maven-surefire-plugin</artifactId>");
        assertTrue(surefire >= 0, "native-tls-tests must configure Surefire locally");
        int includesStart = nativeTlsTestsProfile.indexOf("<includes>", surefire);
        int includesEnd = nativeTlsTestsProfile.indexOf("</includes>", surefire);
        assertTrue(
                includesStart > surefire
                        && includesEnd > includesStart
                        && includesEnd < nativeTlsTestsProfile.indexOf("</plugin>", surefire),
                "the TLS selection must belong to the profile-local Surefire plugin");
        String includes = nativeTlsTestsProfile.substring(includesStart, includesEnd);
        assertEquals(2, occurrences(includes, "<include>"));
        assertEquals(
                1, occurrences(includes, "<include>**/ProtosTlsClientIntegrationTest.java</include>"));
        assertEquals(1, occurrences(includes, "<include>**/ProtosTlsTrustTest.java</include>"));
        // Only the directory part may be a wildcard; no class-name pattern selects other tests.
        assertFalse(includes.replace("<include>**/", "<include>").contains("*"));
        assertEquals(1, occurrences(nativeTlsTestsProfile, "<includes>"));
        assertFalse(nativeTlsTestsProfile.contains("<test>"));

        int cleanup =
                nativeTlsTestsProfile.indexOf(
                        "<delete dir=\"${project.build.directory}/maven-surefire-plugin-test-ids\""
                                + " quiet=\"true\"/>");
        int prepare = nativeTlsTestsProfile.indexOf("<id>prepare-native-tls-tests-init-args</id>");
        assertTrue(
                prepare >= 0 && cleanup > prepare,
                "the stale Surefire unique IDs must be removed by the profile's antrun execution");
        assertTrue(
                nativeTlsTestsProfile
                        .substring(prepare, cleanup)
                        .contains("<phase>process-test-classes</phase>"),
                "the cleanup must run before Surefire");

        // The selection stays profile-local: the ordinary JVM suite is never restricted.
        String outside = pom.replace(nativeTlsTestsProfile, "");
        assertFalse(outside.contains("ProtosTlsClientIntegrationTest"));
        assertFalse(outside.contains("ProtosTlsTrustTest"));
        assertFalse(outside.contains("maven-surefire-plugin-test-ids"));

        System.out.println("I130_C3_NATIVE_TLS_TESTS_SELECTION=PROFILE_LOCAL_EXACT");
        System.out.println("I130_C3_NATIVE_TLS_TESTS_STALE_IDS=CLEANED");
    }

    /** PLAT045 (#772): Native admission is interpreter-only and must never claim guest JIT. */
    @Test
    void nativeAdmissionValidatesInterpreterOnlyFallback() throws Exception {
        String script = Files.readString(Path.of("scripts/native-test.sh"));

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

    private static String profile(String pom, String id) {
        int start = pom.indexOf("<id>" + id + "</id>");
        assertTrue(start >= 0, id + " profile missing");
        int end = pom.indexOf("</profile>", start);
        assertTrue(end > start, id + " profile unterminated");
        return pom.substring(start, end);
    }

    private static int occurrences(String text, String fragment) {
        int count = 0;
        for (int at = text.indexOf(fragment); at >= 0; at = text.indexOf(fragment, at + 1)) {
            count++;
        }
        return count;
    }
}
