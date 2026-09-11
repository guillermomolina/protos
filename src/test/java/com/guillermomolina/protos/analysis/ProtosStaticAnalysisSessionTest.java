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

package com.guillermomolina.protos.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ProtosStaticAnalysisSessionTest {

    @Test
    void partitionsSameDocumentIdentityAcrossIndependentWorkspaces() {
        ProtosStaticAnalysisSession session = new ProtosStaticAnalysisSession();
        assertTrue(session.openWorkspace("workspace-A"));
        assertTrue(session.openWorkspace("workspace-B"));

        session.putDocument(
                "workspace-A",
                new ProtosDocumentSnapshot("shared.protos", 1, "left: 1"));
        session.putDocument(
                "workspace-B",
                new ProtosDocumentSnapshot("shared.protos", 1, "right: 2"));

        ProtosStaticParseResult left =
                session.parseCurrent("workspace-A", "shared.protos").orElseThrow();
        ProtosStaticParseResult right =
                session.parseCurrent("workspace-B", "shared.protos").orElseThrow();

        assertEquals("left: 1", left.snapshot().characters());
        assertEquals("right: 2", right.snapshot().characters());
        assertTrue(session.isCurrent("workspace-A", left));
        assertTrue(session.isCurrent("workspace-B", right));
    }

    @Test
    void replacementKeepsVersionOpaqueAndMarksOldResultStale() {
        ProtosStaticAnalysisSession session = new ProtosStaticAnalysisSession();
        session.openWorkspace("workspace");

        ProtosDocumentSnapshot first =
                new ProtosDocumentSnapshot("doc.protos", 10, "value: 10");
        session.putDocument("workspace", first);
        ProtosStaticParseResult oldResult =
                session.parseCurrent("workspace", "doc.protos").orElseThrow();

        ProtosDocumentSnapshot replacement =
                new ProtosDocumentSnapshot("doc.protos", 4, "value: 4");
        Optional<ProtosDocumentSnapshot> previous =
                session.putDocument("workspace", replacement);

        assertEquals(Optional.of(first), previous);
        assertEquals(Optional.of(replacement),
                session.currentSnapshot("workspace", "doc.protos"));
        assertFalse(session.isCurrent("workspace", oldResult));

        ProtosStaticParseResult newResult =
                session.parseCurrent("workspace", "doc.protos").orElseThrow();
        assertTrue(session.isCurrent("workspace", newResult));
        assertEquals(4, newResult.snapshot().version());
    }

    @Test
    void equivalentReplacementDoesNotInventHostIdentityStaleness() {
        ProtosStaticAnalysisSession session = new ProtosStaticAnalysisSession();
        session.openWorkspace("workspace");

        ProtosDocumentSnapshot original =
                new ProtosDocumentSnapshot("doc.protos", 2, "42");
        session.putDocument("workspace", original);
        ProtosStaticParseResult result =
                session.parseCurrent("workspace", "doc.protos").orElseThrow();

        session.putDocument(
                "workspace",
                new ProtosDocumentSnapshot("doc.protos", 2, "42"));

        assertTrue(session.isCurrent("workspace", result));
    }

    @Test
    void removingDocumentOrWorkspaceInvalidatesCapturedResultsLocally() {
        ProtosStaticAnalysisSession session = new ProtosStaticAnalysisSession();
        session.openWorkspace("kept");
        session.openWorkspace("removed");

        session.putDocument(
                "kept",
                new ProtosDocumentSnapshot("same.protos", 1, "kept: true"));
        session.putDocument(
                "removed",
                new ProtosDocumentSnapshot("same.protos", 1, "removed: true"));

        ProtosStaticParseResult kept =
                session.parseCurrent("kept", "same.protos").orElseThrow();
        ProtosStaticParseResult removed =
                session.parseCurrent("removed", "same.protos").orElseThrow();

        assertTrue(session.closeDocument("removed", "same.protos").isPresent());
        assertFalse(session.isCurrent("removed", removed));
        assertTrue(session.isCurrent("kept", kept));

        session.putDocument(
                "removed",
                new ProtosDocumentSnapshot("same.protos", 2, "removedAgain: true"));
        ProtosStaticParseResult removedAgain =
                session.parseCurrent("removed", "same.protos").orElseThrow();

        assertTrue(session.closeWorkspace("removed"));
        assertFalse(session.isCurrent("removed", removedAgain));
        assertTrue(session.isCurrent("kept", kept));
        assertTrue(session.currentSnapshot("removed", "same.protos").isEmpty());
    }

    @Test
    void documentMutationRequiresAnOpenWorkspaceButQueriesAreEmptyWhenAbsent() {
        ProtosStaticAnalysisSession session = new ProtosStaticAnalysisSession();

        assertThrows(
                IllegalArgumentException.class,
                () -> session.putDocument(
                        "missing",
                        new ProtosDocumentSnapshot("doc.protos", 1, "42")));

        assertTrue(session.currentSnapshot("missing", "doc.protos").isEmpty());
        assertTrue(session.parseCurrent("missing", "doc.protos").isEmpty());
        assertTrue(session.closeDocument("missing", "doc.protos").isEmpty());
        assertFalse(session.closeWorkspace("missing"));
    }

    @Test
    void concurrentIndependentDocumentCustodyDoesNotShareMutableState() throws Exception {
        ProtosStaticAnalysisSession session = new ProtosStaticAnalysisSession();
        session.openWorkspace("workspace");

        int tasks = 24;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ProtosStaticParseResult>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < tasks; i++) {
                final int index = i;
                futures.add(executor.submit(() -> {
                    start.await();
                    String documentId = "doc-" + index + ".protos";
                    ProtosDocumentSnapshot snapshot =
                            new ProtosDocumentSnapshot(
                                    documentId,
                                    index,
                                    "value" + index + ": " + index);
                    session.putDocument("workspace", snapshot);
                    return session.parseCurrent("workspace", documentId).orElseThrow();
                }));
            }

            start.countDown();

            for (int i = 0; i < tasks; i++) {
                ProtosStaticParseResult result = futures.get(i).get(10, TimeUnit.SECONDS);
                assertEquals("doc-" + i + ".protos", result.snapshot().documentId());
                assertEquals(i, result.snapshot().version());
                assertTrue(session.isCurrent("workspace", result));
                assertInstanceOf(ProtosStaticParseResult.Parsed.class, result);
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
