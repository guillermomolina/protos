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
package com.guillermomolina.protos.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** PERF025-G2 structural evidence for fixed-cost physical RootTask bookkeeping compaction. */
final class ProtosPerf025G2RootTaskBookkeepingTest {
    private static final Path TASK =
            Path.of(
                    "src/main/java/com/guillermomolina/protos/runtime/"
                            + "ProtosTask.java");
    private static final Path DOMAIN =
            Path.of(
                    "src/main/java/com/guillermomolina/protos/runtime/"
                            + "ProtosActorExecutionDomain.java");

    @Test
    void liveTaskRegistryAndStructuredParentAvoidGeneralPurposePerTaskBookkeeping()
            throws Exception {
        String task = Files.readString(TASK);
        String domain = Files.readString(DOMAIN);

        assertTrue(task.contains("private final ProtosTask parent;"));
        assertTrue(task.contains("ProtosTask parentForRuntime()"));
        assertFalse(task.contains("private ProtosTask parent;"));

        assertTrue(domain.contains("Collections.newSetFromMap(new IdentityHashMap<>())"));
        assertFalse(
                domain.contains(
                        "private final Set<ProtosTask> liveTasks = new LinkedHashSet<>()"));

        assertTrue(domain.contains("ProtosTask parent = task.parentForRuntime();"));
        assertFalse(domain.contains("task.parent().ifPresent"));
    }

    @Test
    void freshRootRetainsExistingFirstExecutionCancellationBoundary()
            throws Exception {
        String domain = Files.readString(DOMAIN);

        String rootDispatch =
                section(
                        domain,
                        "public ProtosTask runFreshRootTaskDirectly(",
                        "/** @return whether the owning Actor is already TERMINATING");

        assertTrue(rootDispatch.contains("task.beginDirectDispatch()"));
        assertTrue(rootDispatch.contains("task.runContinuation()"));
        assertFalse(rootDispatch.contains("task.runFreshRootFirstSegmentDirectly()"));
    }

    @Test
    void terminalPublicationTrustsTheStateEstablishedByItsPrivateCallers()
            throws Exception {
        String task = Files.readString(TASK);

        String publication =
                section(
                        task,
                        "private void publishTerminal(State terminal, Object outcome)",
                        "private void terminalizeAssociatedFuture(");

        assertFalse(
                publication.contains(
                        "terminal lifecycle publication requires matching terminal Task state"));
        assertTrue(publication.contains("owner.terminal(this);"));
        assertTrue(publication.contains("terminalizeAssociatedFuture(terminal, outcome);"));
    }

    private static String section(String text, String begin, String end) {
        int start = text.indexOf(begin);
        int finish = text.indexOf(end, start);
        if (start < 0 || finish < 0) {
            throw new AssertionError(
                    "source section not found: " + begin + " -> " + end);
        }
        return text.substring(start, finish);
    }
}
