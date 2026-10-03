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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** PERF025 structural evidence for proof-preserving Core String construction paths. */
final class ProtosPerf025StringProofPreservationTest {
    private static final Path STRING_PROTOCOL =
            Path.of(
                    "src/main/java/com/guillermomolina/protos/execution/"
                            + "ProtosStandardStringProtocol.java");
    private static final Path ACTOR_TRANSFER =
            Path.of(
                    "src/main/java/com/guillermomolina/protos/runtime/"
                            + "ProtosActorValueTransfer.java");
    private static final Path PARALLEL_RUNTIME =
            Path.of(
                    "src/main/java/com/guillermomolina/protos/execution/"
                            + "ProtosParallelRuntime.java");
    private static final Path DETACHED_EXECUTION =
            Path.of(
                    "src/main/java/com/guillermomolina/protos/execution/"
                            + "ProtosDetachedExecutionValue.java");
    private static final Path DISCOVERY =
            Path.of(
                    "src/main/java/com/guillermomolina/protos/execution/"
                            + "ProtosTestLogicalCaseDiscoveryFacility.java");

    @Test
    void standardStringProductivePathsDoNotForgetEstablishedUnicodeProof() throws Exception {
        String protocol = Files.readString(STRING_PROTOCOL);

        assertFalse(protocol.contains("codePointCount("));
        assertFalse(protocol.contains("new ProtosStringValue(scalar)"));
        assertFalse(
                protocol.contains(
                        "new ProtosStringValue(receiver.value() + right.value())"));
        assertFalse(protocol.contains("new ProtosStringValue(result.toString())"));

        assertTrue(protocol.contains("receiver.scalarCountForRuntime()"));
        assertTrue(protocol.contains("receiver.scalarAtForRuntime(index)"));
        assertTrue(protocol.contains("ProtosStringValue.concatenateForRuntime(receiver, right)"));
        assertTrue(protocol.contains("ProtosStringValue.concatenateAllForRuntime("));
    }

    @Test
    void crossDomainAndDiscoveryCopiesPreserveStringProofMetadata() throws Exception {
        String actor = Files.readString(ACTOR_TRANSFER);
        String parallel = Files.readString(PARALLEL_RUNTIME);
        String detached = Files.readString(DETACHED_EXECUTION);
        String discovery = Files.readString(DISCOVERY);

        assertFalse(actor.contains("new ProtosStringValue(string.value())"));
        assertFalse(parallel.contains("new ProtosStringValue(x.value())"));
        assertFalse(detached.contains("new ProtosStringValue(string.value())"));
        assertFalse(discovery.contains("new ProtosStringValue(corpusId.value())"));
        assertFalse(discovery.contains("new ProtosStringValue(sourcePath.value())"));
        assertFalse(discovery.contains("new ProtosStringValue(value.value())"));

        assertTrue(actor.contains("string.copyForRuntime()"));
        assertTrue(parallel.contains("x.copyForRuntime()"));
        assertTrue(detached.contains("string.copyForRuntime()"));
        assertTrue(discovery.contains("corpusId.copyForRuntime()"));
        assertTrue(discovery.contains("sourcePath.copyForRuntime()"));
        assertTrue(discovery.contains("value.copyForRuntime()"));
    }
}
