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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.source.SourceSpan;
import org.junit.jupiter.api.Test;

class ProtosStaticAnalysisCoreTest {
    private final ProtosStaticAnalysisCore core = new ProtosStaticAnalysisCore();

    @Test
    void parsesUnexecutedSnapshotThroughTheRealParserAuthority() {
        ProtosDocumentSnapshot snapshot =
                new ProtosDocumentSnapshot(
                        "file:///workspace/example.protos",
                        7,
                        "value: 42\nvalue");

        ProtosStaticParseResult.Parsed parsed =
                assertInstanceOf(
                        ProtosStaticParseResult.Parsed.class,
                        core.parse(snapshot));

        assertSame(snapshot, parsed.snapshot());
        assertEquals(2, parsed.program().expressions().size());
        assertEquals(new SourceSpan(0, snapshot.characters().length()), parsed.program().span());
    }

    @Test
    void projectsUnexpectedTokenFailureWithoutProtocolTranslation() {
        ProtosDocumentSnapshot snapshot =
                new ProtosDocumentSnapshot("untitled:broken", 3, "name\n)");

        ProtosStaticParseResult.Failed failed =
                assertInstanceOf(
                        ProtosStaticParseResult.Failed.class,
                        core.parse(snapshot));

        assertSame(snapshot, failed.snapshot());
        assertEquals(new SourceSpan(5, 6), failed.span());
        assertFalse(failed.unexpectedEndOfSource());
        assertTrue(failed.message().contains("RPAREN"));
    }

    @Test
    void preservesUnexpectedEndOfSourceClassification() {
        ProtosDocumentSnapshot snapshot =
                new ProtosDocumentSnapshot("memory:incomplete", 11, "name:");

        ProtosStaticParseResult.Failed failed =
                assertInstanceOf(
                        ProtosStaticParseResult.Failed.class,
                        core.parse(snapshot));

        assertEquals(new SourceSpan(5, 5), failed.span());
        assertTrue(failed.unexpectedEndOfSource());
        assertTrue(failed.message().contains("EOF"));
    }

    @Test
    void snapshotIdentityMetadataRemainsOpaqueToParserAuthority() {
        ProtosDocumentSnapshot snapshot =
                new ProtosDocumentSnapshot("not-a-uri-and-not-a-path", -4, "42");

        ProtosStaticParseResult.Parsed parsed =
                assertInstanceOf(
                        ProtosStaticParseResult.Parsed.class,
                        core.parse(snapshot));

        assertEquals("not-a-uri-and-not-a-path", parsed.snapshot().documentId());
        assertEquals(-4, parsed.snapshot().version());
        assertEquals("42", parsed.snapshot().characters());
    }
}
